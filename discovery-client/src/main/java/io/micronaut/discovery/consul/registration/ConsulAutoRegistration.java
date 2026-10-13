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
package io.micronaut.discovery.consul.registration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.EmbeddedServerInstance;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceIdGenerator;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulCheck;
import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry;
import io.micronaut.discovery.exceptions.DiscoveryException;
import io.micronaut.discovery.registration.RegistrationException;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Auto registration implementation for consul.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = {ConsulClient.class, ConsulConfiguration.class})
public class ConsulAutoRegistration extends DiscoveryServiceAutoRegistration {
    private static final String DEFAULT_CHECK_STATUS = ConsulCheckStatus.PASSING.toString();
    private final ConsulClient consulClient;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ConsulConfiguration consulConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final Environment environment;

    /**
     * @param environment            The environment
     * @param consulClient           The Consul client
     * @param heartbeatConfiguration The heartbeat configuration
     * @param consulConfiguration    The Consul configuration
     * @param idGenerator            The id generator
     */
    protected ConsulAutoRegistration(
        Environment environment,
        ConsulClient consulClient,
        HeartbeatConfiguration heartbeatConfiguration,
        ConsulConfiguration consulConfiguration,
        ServiceInstanceIdGenerator idGenerator) {

        super(consulConfiguration.getRegistration());
        this.environment = environment;
        this.consulClient = consulClient;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.consulConfiguration = consulConfiguration;
        this.idGenerator = idGenerator;
    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null && !registration.getCheck().isHttp() && registration.getCheck().isEnabled() && registered.get()) {

            String serviceId = idGenerator.generateId(environment, instance);
            String checkId = "service:" + serviceId;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reporting status for Check ID [{}]: {}", checkId, status);
            }

            boolean passing = status.equals(HealthStatus.UP);
            String state = passing ? "passing" : "failure";
            // send a request to /agent/check/pass/:check_id or /agent/check/fail/:check_id
            Publisher<HttpStatus> reportPublisher = passing
                ? consulClient.pass(checkId)
                : consulClient.fail(checkId, status.getDescription().orElse(null));
            Mono.from(reportPublisher).hasElement().subscribe(reported -> {
                if (reported) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Successfully reported {} state to Consul", state);
                    }
                } else {
                    // the Micronaut Reactor HTTP client turns the 404 response for an unknown check into an empty result
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Consul did not acknowledge the {} state of check [{}]. Attempting re-registration of instance [{}].", state, checkId, instance.getId());
                    }
                    register(instance);
                }
            }, throwable -> onReportError(instance, serviceId, checkId, state, throwable));
        }
    }

    private void onReportError(ServiceInstance instance, String serviceId, String checkId, String state, Throwable throwable) {
        if (isUnknownCheck(throwable)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Check [{}] is unknown to Consul. Attempting re-registration of instance [{}].", checkId, instance.getId());
            }
            register(instance);
            return;
        }

        if (LOG.isErrorEnabled()) {
            LOG.error(getErrorMessage(throwable, "Error reporting " + state + " state to Consul: "), throwable);
        }

        // check if the service is still registered with the Consul agent
        Mono.from(consulClient.findServices()).subscribe(services -> {
            if (!services.containsKey(serviceId)) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Instance [{}] no longer registered with Consul. Attempting re-registration.", instance.getId());
                }
                register(instance);
            }
        }, lookupError -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to verify the registration of instance [{}] with Consul: {}", instance.getId(), lookupError.getMessage(), lookupError);
            }
        });
    }

    /**
     * Whether Consul rejected a TTL check update because it does not know the check. Recent Consul
     * versions respond with a 404, older ones with a 500 that names the check.
     *
     * @param throwable The error
     * @return True if the check is unknown to Consul
     */
    private static boolean isUnknownCheck(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof HttpClientResponseException e) {
                HttpStatus httpStatus = e.getStatus();
                if (httpStatus == HttpStatus.NOT_FOUND) {
                    return true;
                }
                if (httpStatus == HttpStatus.INTERNAL_SERVER_ERROR) {
                    String body = e.getResponse().getBody(String.class).orElse(null);
                    return isUnknownCheckMessage(body) || isUnknownCheckMessage(e.getMessage());
                }
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isUnknownCheckMessage(@Nullable String message) {
        if (message == null) {
            return false;
        }
        String lowerCase = message.toLowerCase(Locale.ENGLISH);
        return lowerCase.contains("unknown check") || lowerCase.contains("does not have associated ttl");
    }

    @Override
    protected void deregister(ServiceInstance instance) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null) {
            String applicationName = instance.getId();
            String serviceId = idGenerator.generateId(environment, instance);
            Publisher<HttpStatus> deregisterPublisher = consulClient.deregister(serviceId);
            final String discoveryService = "Consul";
            performDeregistration(discoveryService, registration, deregisterPublisher, applicationName);
        }
    }

    @Override
    protected void register(ServiceInstance instance) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null) {
            String applicationName = instance.getId();
            validateApplicationName(applicationName);
            if (StringUtils.isNotEmpty(applicationName)) {
                String address = address(instance, registration);
                Map<String, String> meta = new HashMap<>(registration.getMeta());
                String serviceId = idGenerator.generateId(environment, instance);
                ConsulNewServiceEntry serviceEntry = new ConsulNewServiceEntry(
                    applicationName,
                    address,
                    instance.getPort(),
                    tags(instance, registration),
                    serviceId,
                    meta,
                    createChecks(instance, registration, address));
                Publisher<HttpStatus> registerFlowable = consulClient.register(serviceEntry);
                performRegistration("Consul", registration, instance, registerFlowable);
            }
        }
    }

    @NonNull
    private List<String> tags(@NonNull ServiceInstance instance,
                              ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration) {
        List<String> tags = new ArrayList<>(registration.getTags());
        if (instance instanceof EmbeddedServerInstance embeddedServerInstance) {
            ApplicationConfiguration applicationConfiguration = embeddedServerInstance.getEmbeddedServer().getApplicationConfiguration();
            ApplicationConfiguration.InstanceConfiguration instanceConfiguration = applicationConfiguration.getInstance();
            instanceConfiguration.getGroup().ifPresent(g -> {
                    validateName(g, "Instance Group");
                    tags.add(ServiceInstance.GROUP + "=" + g);
                }

            );
            instanceConfiguration.getZone().ifPresent(z -> {
                    validateName(z, "Instance Zone");
                    tags.add(ServiceInstance.ZONE + "=" + z);
                }
            );

            // include metadata as tags
            ConvertibleValues<String> metadata = embeddedServerInstance.getMetadata();
            for (Map.Entry<String, String> entry : metadata) {
                tags.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return tags;
    }

    @NonNull
    private List<ConsulCheck> createChecks(@NonNull ServiceInstance instance,
                                           ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration,
                                           String address) {
        ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig = registration.getCheck();
        if (checkConfig.isEnabled()) {
            return Collections.singletonList(createCheck(checkConfig, instance, registration, address));
        }
        return Collections.emptyList();
    }

    private String address(@NonNull ServiceInstance instance,
                           ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration) {
        String address = null;
        if (registration.isPreferIpAddress()) {
            address = registration.getIpAddr().orElseGet(() -> {
                final String host = instance.getHost();
                try {
                    final InetAddress inetAddress = InetAddress.getByName(host);
                    return inetAddress.getHostAddress();
                } catch (UnknownHostException e) {
                    throw new RegistrationException("Failed to lookup IP address for host [" + host + "]: " + e.getMessage(), e);
                }
            });
        }
        if (StringUtils.isEmpty(address)) {
            address = instance.getHost();
        }
        return address;
    }

    private ConsulCheck createCheck(ConsulConfiguration.ConsulRegistrationConfiguration.@NonNull CheckConfiguration checkConfig,
                                    @NonNull ServiceInstance instance,
                                    ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration,
                                    @Nullable String address) {

        ConsulCheck check = new ConsulCheck();
        check.setDeregisterCriticalServiceAfter(deregisterCriticalServiceAfterCheck(checkConfig));
        checkConfig.getId().ifPresent(check::setId);
        check.setStatus(DEFAULT_CHECK_STATUS);
        checkConfig.getNotes().ifPresent(check::setNotes);
        if (heartbeatConfiguration.isEnabled() && !checkConfig.isHttp()) {
            check.setTtl(heartbeatConfiguration.getInterval().plus(Duration.ofSeconds(10)).toSeconds() + "s");
        } else {
            check.setInterval(checkInternal(checkConfig));
            httpCheckUrl(instance, registration, address).ifPresent(check::setHttp);
            check.setMethod(checkConfig.getMethod());
            checkConfig.getTlsSkipVerify().ifPresent(check::setTlsSkipVerify);
            check.setHeader(checkConfig.getHeaders());
        }
        return check;
    }

    @Nullable
    private String deregisterCriticalServiceAfterCheck(ConsulConfiguration.ConsulRegistrationConfiguration.@NonNull CheckConfiguration checkConfig) {
        return checkConfig.getDeregisterCriticalServiceAfter().map(d -> d.toMinutes() + "m").orElse(null);
    }

    @Nullable
    private String checkInternal(ConsulConfiguration.ConsulRegistrationConfiguration.@NonNull CheckConfiguration checkConfig) {
        return checkConfig.getInterval().toSeconds() + "s";
    }

    private Optional<URL> httpCheckUrl(@NonNull ServiceInstance instance,
                                       ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration,
                                       @Nullable String address) {
        if (instance instanceof EmbeddedServerInstance embeddedServerInstance) {
            EmbeddedServer embeddedServer = embeddedServerInstance.getEmbeddedServer();
            URL serverURL = embeddedServer.getURL();
            if (registration.isPreferIpAddress() && address != null) {
                try {
                    serverURL = new URL(embeddedServer.getURL().getProtocol(), address, embeddedServer.getPort(), embeddedServer.getURL().getPath());
                } catch (MalformedURLException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("invalid url for health check: {}:{}/{}", embeddedServer.getURL().getProtocol() + address, embeddedServer.getPort(), embeddedServer.getURL().getPath());
                    }
                    throw new DiscoveryException("Invalid health path configured: " + registration.getHealthPath());
                }
            }
            try {
                return Optional.of(new URL(serverURL, registration.getHealthPath().orElse("/health")));
            } catch (MalformedURLException e) {
                throw new DiscoveryException("Invalid health path configured: " + registration.getHealthPath());
            }
        }
        return Optional.empty();
    }
}
