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
package io.micronaut.discovery.eureka.registration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.discovery.EmbeddedServerInstance;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceIdGenerator;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.eureka.EurekaConfiguration;
import io.micronaut.discovery.eureka.client.v2.EurekaClient;
import io.micronaut.discovery.eureka.client.v2.InstanceInfo;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link io.micronaut.discovery.registration.AutoRegistration} that registers with Eureka.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = {EurekaClient.class, EmbeddedServer.class})
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
public class EurekaAutoRegistration extends DiscoveryServiceAutoRegistration {

    private static final String EUREKA_SERVICE_NAME = "Eureka";
    private final Environment environment;
    private final EurekaClient eurekaClient;
    private final EurekaConfiguration eurekaConfiguration;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final AtomicReference<HealthStatus> lastStatus = new AtomicReference<>();

    /**
     * @param environment            The environment
     * @param eurekaClient           The Eureka client
     * @param eurekaConfiguration    The Eureka configuration
     * @param heartbeatConfiguration The Heartbeat configuration
     * @param idGenerator            The id generator
     */
    protected EurekaAutoRegistration(
        Environment environment,
        EurekaClient eurekaClient,
        EurekaConfiguration eurekaConfiguration,
        HeartbeatConfiguration heartbeatConfiguration,
        ServiceInstanceIdGenerator idGenerator) {

        super(eurekaConfiguration.getRegistration());
        this.environment = environment;
        this.eurekaClient = eurekaClient;
        this.eurekaConfiguration = eurekaConfiguration;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.idGenerator = idGenerator;
    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        EurekaConfiguration.EurekaRegistrationConfiguration registration = eurekaConfiguration.getRegistration();
        if (heartbeatConfiguration.isEnabled() && registration != null && registered.get()) {
            InstanceInfo instanceInfo = registration.getInstanceInfo();
            if (status.equals(HealthStatus.UP)) {
                Mono<HttpStatus> heartbeatPublisher = Mono.from(eurekaClient.heartbeat(instanceInfo.getApp(), instanceInfo.getId()));
                heartbeatPublisher.hasElement().subscribe(reported -> {
                    if (Boolean.TRUE.equals(reported)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Successfully reported passing state to Eureka");
                        }
                    } else {
                        // the Micronaut Reactor HTTP client turns the 404 response for an unknown instance into an empty result
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Eureka did not acknowledge the heartbeat of instance [{}]. Attempting re-registration.", instance.getId());
                        }
                        register(instance, InstanceInfo.Status.UP);
                    }
                }, throwable -> {
                    if (throwable instanceof HttpClientResponseException hcre) {
                        HttpStatus httpStatus = hcre.getStatus();
                        if (httpStatus == HttpStatus.NOT_FOUND) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Instance [{}] no longer registered with Eureka. Attempting re-registration.", instance.getId());
                            }
                            register(instance, InstanceInfo.Status.UP);
                            return;
                        }
                    }
                    String errorMessage = getErrorMessage(throwable, "Error reporting passing state to Eureka: ");
                    if (LOG.isErrorEnabled()) {
                        LOG.error(errorMessage, throwable);
                    }
                });
            }

            final HealthStatus lastStatus = this.lastStatus.getAndSet(status);
            if (lastStatus == null || !lastStatus.equals(status)) {

                InstanceInfo.Status s = translateState(status);
                Mono<HttpStatus> statusPublisher = Mono.from(eurekaClient.updateStatus(instanceInfo.getApp(), instanceInfo.getId(), s));
                statusPublisher.hasElement().subscribe(reported -> {
                    if (Boolean.TRUE.equals(reported)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Successfully reported status {} to Eureka", s);
                        }
                    } else {
                        // the Micronaut Reactor HTTP client turns the 404 response for an unknown instance into an empty result
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Eureka did not acknowledge the status {} of instance [{}]. Attempting re-registration.", s, instance.getId());
                        }
                        reRegisterForStatus(instance, status);
                    }
                }, throwable -> {
                    if (throwable instanceof HttpClientResponseException hcre && hcre.getStatus() == HttpStatus.NOT_FOUND) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Instance [{}] no longer registered with Eureka. Attempting re-registration.", instance.getId());
                        }
                        reRegisterForStatus(instance, status);
                        return;
                    }
                    String errorMessage = getErrorMessage(throwable, "Error reporting state to Eureka: ");
                    if (LOG.isErrorEnabled()) {
                        LOG.error(errorMessage, throwable);
                    }
                });
            }

        }
    }

    private void reRegisterForStatus(ServiceInstance instance, HealthStatus status) {
        // register with the status being reported so an unhealthy instance is not advertised as UP,
        // and report the status again with the next heartbeat in case the registration lost it
        this.lastStatus.compareAndSet(status, null);
        register(instance, translateState(status));
    }

    /**
     * Translate a {@link HealthStatus} to a Eureka {@link io.micronaut.discovery.eureka.client.v2.InstanceInfo.Status}.
     *
     * @param status The status
     * @return The {@link io.micronaut.discovery.eureka.client.v2.InstanceInfo.Status} instance
     */
    protected InstanceInfo.Status translateState(HealthStatus status) {
        if (status.equals(HealthStatus.UP)) {
            return InstanceInfo.Status.UP;
        }
        return InstanceInfo.Status.DOWN;
    }

    @Override
    protected void deregister(ServiceInstance instance) {
        EurekaConfiguration.EurekaRegistrationConfiguration registration = eurekaConfiguration.getRegistration();
        if (registration != null) {
            InstanceInfo instanceInfo = registration.getInstanceInfo();

            Publisher<HttpStatus> deregisterPublisher = eurekaClient.deregister(instanceInfo.getApp(), instanceInfo.getId());
            // a failed fail-fast registration stops the server on the event loop, which must not block,
            // so the de-registration is then sent without waiting for it
            boolean block = registration.isFailFast() && !Schedulers.isInNonBlockingThread();
            performDeregistration(EUREKA_SERVICE_NAME, registration, deregisterPublisher, instanceInfo.getApp(), block);
        }
    }

    @Override
    protected void register(ServiceInstance instance) {
        register(instance, null);
    }

    /**
     * Registers the instance, with the given status when it is re-registered after Eureka lost it.
     *
     * @param instance The instance
     * @param status   The status to register the instance with, or {@code null} to keep the configured one
     */
    private void register(ServiceInstance instance, InstanceInfo.@Nullable Status status) {
        EurekaConfiguration.EurekaRegistrationConfiguration registration = eurekaConfiguration.getRegistration();
        if (registration != null) {
            InstanceInfo instanceInfo = registration.getInstanceInfo();
            if (status != null) {
                instanceInfo.setStatus(status);
            }

            if (!registration.isExplicitInstanceId()) {
                instanceInfo.setInstanceId(idGenerator.generateId(environment, instance));
            }

            ConvertibleValues<String> instanceMetadata = instance.getMetadata();
            Properties properties = instanceMetadata.asProperties();
            Map<String, String> targetMetadata = instanceInfo.getMetadata();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                targetMetadata.put(entry.getKey().toString(), entry.getValue().toString());
            }
            if (instance instanceof EmbeddedServerInstance embeddedServerInstance) {
                // the zone metadata is where other instances, and Spring Cloud Netflix, look for the zone
                embeddedServerInstance.getEmbeddedServer().getApplicationConfiguration().getInstance().getZone()
                    .ifPresent(zone -> targetMetadata.put(ServiceInstance.ZONE, zone));
            }
            customizeInstanceInfo(instanceInfo);
            validateApplicationName(instanceInfo.getApp());

            Publisher<HttpStatus> registerPublisher = eurekaClient.register(instanceInfo.getApp(), instanceInfo);
            performRegistration(EUREKA_SERVICE_NAME, registration, instance, registerPublisher);
        }
    }

    /**
     * Subclasses can override to customize the instance info.
     *
     * @param instanceInfo The instance info
     */
    protected void customizeInstanceInfo(InstanceInfo instanceInfo) {
        // no-op
    }
}
