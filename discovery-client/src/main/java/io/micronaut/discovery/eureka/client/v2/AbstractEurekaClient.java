/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.eureka.client.v2;

import static tools.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static tools.jackson.databind.DeserializationFeature.UNWRAP_ROOT_VALUE;
import static tools.jackson.databind.SerializationFeature.WRAP_ROOT_VALUE;
import static tools.jackson.databind.SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.eureka.EurekaConfiguration;
import io.micronaut.discovery.eureka.EurekaServiceInstance;
import io.micronaut.discovery.eureka.condition.RequiresEureka;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Compile time implementation of {@link EurekaClient}.
 * <p>
 * The Eureka REST API wraps request and response bodies in a member named after their type, for example
 * {@code {"instance": {...}}}. The wrappers are mapped explicitly, so the model works the same with Jackson Databind
 * and Micronaut Serialization and does not depend on the root value wrapping features of Jackson Databind.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Client(id = EurekaClient.SERVICE_ID, path = EurekaConfiguration.CONTEXT_PATH_PLACEHOLDER, configuration = EurekaConfiguration.class)
@JacksonFeatures(
    disabledSerializationFeatures = {WRAP_ROOT_VALUE, WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED},
    enabledDeserializationFeatures = ACCEPT_SINGLE_VALUE_AS_ARRAY,
    disabledDeserializationFeatures = UNWRAP_ROOT_VALUE
)
@Validated
@RequiresEureka
abstract class AbstractEurekaClient implements EurekaClient {

    static final String EXPR_EUREKA_REGISTRATION_RETRY_DELAY = "${" + EurekaConfiguration.EurekaRegistrationConfiguration.PREFIX + ".retry-delay:3s}";
    static final String EXPR_EUREKA_REGISTRATION_RETRY_COUNT = "${" + EurekaConfiguration.EurekaRegistrationConfiguration.PREFIX + ".retry-count:10}";

    private final EurekaConfiguration.EurekaDiscoveryConfiguration discoveryConfiguration;

    /**
     * Default constructor.
     *
     * @param discoveryConfiguration The discovery configuration.
     */
    protected AbstractEurekaClient(EurekaConfiguration.EurekaDiscoveryConfiguration discoveryConfiguration) {
        this.discoveryConfiguration = discoveryConfiguration;
    }

    @Override
    public @NonNull String getDescription() {
        return EurekaClient.SERVICE_ID;
    }

    @Override
    @Retryable(
        attempts = EXPR_EUREKA_REGISTRATION_RETRY_COUNT,
        delay = EXPR_EUREKA_REGISTRATION_RETRY_DELAY
    )
    public Publisher<HttpStatus> register(@NotBlank String appId, @Valid @NotNull InstanceInfo instance) {
        return registerInternal(appId, new InstanceInfoWrapper(instance));
    }

    @Override
    public Publisher<ApplicationInfo> getApplicationInfo(@NotBlank String appId) {
        return Publishers.map(getApplicationInfoInternal(appId), ApplicationInfoWrapper::application);
    }

    @Override
    public Publisher<InstanceInfo> getInstanceInfo(@NotBlank String appId, @NotBlank String instanceId) {
        return Publishers.map(getInstanceInfoInternal(appId, instanceId), InstanceInfoWrapper::instance);
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        serviceId = NameUtils.hyphenate(serviceId);
        Flux<List<ServiceInstance>> flowable = Flux.from(getApplicationInfo(serviceId)).map(applicationInfo -> Optional.ofNullable(applicationInfo.getInstances())
            .stream()
            .flatMap(Collection::stream)
            .map(ii -> {
                if (!discoveryConfiguration.isUseSecurePort()) {
                    ii.setSecurePort(-1);
                }
                return (ServiceInstance) new EurekaServiceInstance(ii);
            })
            .toList());

        return flowable.onErrorResume(throwable -> {
            // Translate 404 into empty list
            if (throwable instanceof HttpClientResponseException hcre && hcre.getStatus() == HttpStatus.NOT_FOUND) {
                return Flux.just(Collections.emptyList());
            }
            if (throwable instanceof Exception) {
                return Flux.error(throwable);
            } else {
                return Flux.error(new HttpClientException("Internal Client Error: " + throwable.getMessage(), throwable));
            }
        });
    }

    @Override
    public Publisher<List<ApplicationInfo>> getApplicationInfos() {
        return Publishers.map(getApplicationInfosInternal(), ApplicationInfos::applicationList);
    }

    @Override
    public Publisher<List<ApplicationInfo>> getApplicationVips(String vipAddress) {
        return Publishers.map(getApplicationVipsInternal(vipAddress), ApplicationInfos::applicationList);
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        return Publishers.map(getApplicationInfosInternal(), applicationInfos ->
            applicationInfos
                .applicationList()
                .stream()
                .map(ApplicationInfo::getName)
                .toList()
        );
    }

    /**
     * Registers an instance, see {@link #register(String, InstanceInfo)}.
     *
     * @param appId    The application id
     * @param instance The wrapped instance
     * @return A status of {@link HttpStatus#NO_CONTENT} on success
     */
    @Post(uri = "/apps/{appId}", single = true)
    public abstract Publisher<HttpStatus> registerInternal(String appId, @Body InstanceInfoWrapper instance);

    /**
     * @param appId The application id
     * @return The wrapped application info
     */
    @Get(uri = "/apps/{appId}", single = true)
    public abstract Publisher<ApplicationInfoWrapper> getApplicationInfoInternal(String appId);

    /**
     * @param appId      The application id
     * @param instanceId The instance id
     * @return The wrapped instance info
     */
    @Get(uri = "/apps/{appId}/{instanceId}", single = true)
    public abstract Publisher<InstanceInfoWrapper> getInstanceInfoInternal(String appId, String instanceId);

    /**
     * @return A {@link Publisher} with applications info.
     */
    @SuppressWarnings("WeakerAccess")
    @Get("/apps")
    @Produces(single = true)
    public abstract Publisher<ApplicationInfos> getApplicationInfosInternal();

    /**
     * @param vipAddress The vip address
     * @return A {@link Publisher} with applications info
     */
    @SuppressWarnings("WeakerAccess")
    @Get("/vips/{vipAddress}")
    @Produces(single = true)
    public abstract Publisher<ApplicationInfos> getApplicationVipsInternal(String vipAddress);

    /**
     * The body of the Eureka responses with several applications: {@code {"applications": {"application": [...]}}}.
     *
     * @param applications The applications
     */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApplicationInfos(@JsonProperty("applications") Applications applications) {

        /**
         * @param applications The applications
         */
        ApplicationInfos(List<ApplicationInfo> applications) {
            this(new Applications(applications));
        }

        /**
         * @return The applications, never {@code null}
         */
        List<ApplicationInfo> applicationList() {
            if (applications == null || applications.applications() == null) {
                return Collections.emptyList();
            }
            return applications.applications();
        }
    }

    /**
     * The applications of a {@link ApplicationInfos}.
     *
     * @param applications The applications
     */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Applications(
        @JsonProperty("application")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<ApplicationInfo> applications
    ) {
    }

    /**
     * The body of the Eureka responses with an application: {@code {"application": {...}}}.
     *
     * @param application The application
     */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApplicationInfoWrapper(@JsonProperty("application") ApplicationInfo application) {
    }

    /**
     * The body of the Eureka requests and responses with an instance: {@code {"instance": {...}}}.
     *
     * @param instance The instance
     */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InstanceInfoWrapper(@JsonProperty("instance") InstanceInfo instance) {
    }
}
