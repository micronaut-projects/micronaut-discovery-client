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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.EmbeddedServerInstance;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceIdGenerator;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.*;
import io.micronaut.discovery.exceptions.DiscoveryException;
import io.micronaut.discovery.registration.RegistrationException;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatConfiguration;
import io.micronaut.http.HttpStatus;
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
import java.util.*;

/**
 * Auto registration implementation for consul.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = {ConsulClient.class, ConsulConfiguration.class})
public class ConsulAutoRegistration extends DiscoveryServiceAutoRegistration {

    private final ConsulClient consulClient;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ConsulConfiguration consulConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final Environment environment;

    private static final String DEFAULT_CHECK_STATUS = ConsulCheckStatus.PASSING.toString();

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

            String checkId = "service:" + idGenerator.generateId(environment, instance);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reporting status for Check ID [{}]: {}", checkId, status);
            }

            if (status.equals(HealthStatus.UP)) {
                // send a request to /agent/check/pass/:check_id
                Mono<HttpStatus> passPublisher = Mono.from(consulClient.pass(checkId));
                passPublisher.subscribe(httpStatus -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Successfully reported passing state to Consul");
                    }
                }, throwable -> {

                        // check if the service is still registered with Consul
                        Mono.from(consulClient.getServiceIds()).subscribe(serviceIds -> {
                            String serviceId = idGenerator.generateId(environment, instance);
                            if (!serviceIds.contains(serviceId)) {
                                if (LOG.isInfoEnabled()) {
                                    LOG.info("Instance [{}] no longer registered with Consul. Attempting re-registration.", instance.getId());
                                }
                                register(instance);
                            }
                        });

                        if (LOG.isErrorEnabled()) {
                            LOG.error(getErrorMessage(throwable, "Error reporting passing state to Consul: "), throwable);
                        }
                    });
            } else {
                // send a request to /agent/check/fail/:check_id
                Mono<HttpStatus> failPublisher = Mono.from(consulClient.fail(checkId, status.getDescription().orElse(null)));
                failPublisher.subscribe(httpStatus -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Successfully reported failure state to Consul");
                    }
                }, throwable -> {
                    if (LOG.isErrorEnabled()) {
                        LOG.error(getErrorMessage(throwable, "Error reporting failure state to Consul: "), throwable);
                    }
                });
            }
        }
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
                NewServiceEntry serviceEntry = new NewServiceEntry(applicationName);
                List<String> tags = new ArrayList<>(registration.getTags());
                Map<String, String> meta = new HashMap<>(registration.getMeta());

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

                serviceEntry.address(address)
                    .port(instance.getPort())
                    .tags(tags)
                    .meta(meta);

                String serviceId = idGenerator.generateId(environment, instance);
                serviceEntry.id(serviceId);

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

                    ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig = registration.getCheck();
                    if (checkConfig.isEnabled()) {
                        serviceEntry.check(createCheck(checkConfig, instance, registration, address));
                    }
                }

                customizeServiceEntry(instance, serviceEntry);
                Publisher<HttpStatus> registerFlowable = consulClient.register(serviceEntry);
                performRegistration("Consul", registration, instance, registerFlowable);
            }
        }
    }

    private ConsulCheck createCheck(@NonNull ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig,
                              @NonNull ServiceInstance instance,
                              @NonNull ConsulConfiguration.ConsulRegistrationConfiguration registration,
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
    private String deregisterCriticalServiceAfterCheck(@NonNull ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig) {
        return checkConfig.getDeregisterCriticalServiceAfter().map(d -> d.toMinutes() + "m").orElse(null);
    }

    @Nullable
    private String checkInternal(@NonNull ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig) {
        return checkConfig.getInterval().toSeconds() + "s";
    }

    private Optional<URL> httpCheckUrl(@NonNull ServiceInstance instance,
                                       @NonNull ConsulConfiguration.ConsulRegistrationConfiguration registration,
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

    /**
     * Allows sub classes to override and customize the configuration.
     *
     * @param instance     The instance
     * @param serviceEntry The service entry
     */
    protected void customizeServiceEntry(ServiceInstance instance, NewServiceEntry serviceEntry) {
        // no-op
    }
}
