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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.order.OrderUtil;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final List<ConsulCheckConfiguration> checkConfigurations;
    private final List<ConsulCheckCustomizer> checkCustomizers;

    /**
     * @param environment            The environment
     * @param consulClient           The Consul client
     * @param heartbeatConfiguration The heartbeat configuration
     * @param consulConfiguration    The Consul configuration
     * @param idGenerator            The id generator
     * @deprecated Use {@link #ConsulAutoRegistration(Environment, ConsulClient, HeartbeatConfiguration, ConsulConfiguration, ServiceInstanceIdGenerator, List, List)} instead
     */
    @Deprecated(since = "5.3.0")
    protected ConsulAutoRegistration(
        Environment environment,
        ConsulClient consulClient,
        HeartbeatConfiguration heartbeatConfiguration,
        ConsulConfiguration consulConfiguration,
        ServiceInstanceIdGenerator idGenerator) {
        this(environment, consulClient, heartbeatConfiguration, consulConfiguration, idGenerator, List.of(), List.of());
    }

    /**
     * @param environment            The environment
     * @param consulClient           The Consul client
     * @param heartbeatConfiguration The heartbeat configuration
     * @param consulConfiguration    The Consul configuration
     * @param idGenerator            The id generator
     * @param checkConfigurations    The additional checks configured under {@value ConsulCheckConfiguration#PREFIX}
     * @param checkCustomizers       The customizers of the registered checks
     * @since 5.3.0
     */
    @Inject
    protected ConsulAutoRegistration(
        Environment environment,
        ConsulClient consulClient,
        HeartbeatConfiguration heartbeatConfiguration,
        ConsulConfiguration consulConfiguration,
        ServiceInstanceIdGenerator idGenerator,
        List<ConsulCheckConfiguration> checkConfigurations,
        List<ConsulCheckCustomizer> checkCustomizers) {

        super(consulConfiguration.getRegistration());
        this.environment = environment;
        this.consulClient = consulClient;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.consulConfiguration = consulConfiguration;
        this.idGenerator = idGenerator;
        List<ConsulCheckConfiguration> checks = new ArrayList<>(checkConfigurations);
        checks.sort(Comparator.comparing(ConsulCheckConfiguration::getName));
        this.checkConfigurations = List.copyOf(checks);
        List<ConsulCheckCustomizer> customizers = new ArrayList<>(checkCustomizers);
        OrderUtil.sort(customizers);
        this.checkCustomizers = List.copyOf(customizers);
    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null && !registration.getCheck().isHttp() && registration.getCheck().isEnabled() && registered.get()) {

            String serviceId = idGenerator.generateId(environment, instance);
            String checkId = defaultCheckId(registration.getCheck(), serviceId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reporting status for Check ID [{}]: {}", checkId, status);
            }

            boolean passing = status.equals(HealthStatus.UP);
            String state = passing ? "passing" : "failure";
            // a check recreated by re-registration starts in the state this heartbeat reports
            String checkStatus = passing ? DEFAULT_CHECK_STATUS : ConsulCheckStatus.CRITICAL.toString();
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
                    register(instance, checkStatus);
                }
            }, throwable -> onReportError(instance, serviceId, checkId, state, checkStatus, throwable));
        }
    }

    private void onReportError(ServiceInstance instance, String serviceId, String checkId, String state, String checkStatus, Throwable throwable) {
        if (isUnknownCheck(throwable)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Check [{}] is unknown to Consul. Attempting re-registration of instance [{}].", checkId, instance.getId());
            }
            register(instance, checkStatus);
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
                register(instance, checkStatus);
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
            // a failed fail-fast registration stops the server on the event loop, which must not block,
            // so the de-registration is then sent without waiting for it
            boolean block = registration.isFailFast() && !Schedulers.isInNonBlockingThread();
            performDeregistration(discoveryService, registration, deregisterPublisher, applicationName, block);
        }
    }

    @Override
    protected void register(ServiceInstance instance) {
        register(instance, DEFAULT_CHECK_STATUS);
    }

    /**
     * Registers the instance with its check in the given initial status.
     *
     * @param instance    The instance
     * @param checkStatus The initial status of the check
     */
    private void register(ServiceInstance instance, String checkStatus) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null) {
            String applicationName = instance.getId();
            validateApplicationName(applicationName);
            if (StringUtils.isNotEmpty(applicationName)) {
                String address = address(instance, registration);
                Map<String, String> meta = new HashMap<>(registration.getMeta());
                if (instance instanceof EmbeddedServerInstance embeddedServerInstance) {
                    // the zone tag predates the service metadata, which is where Spring Cloud Consul looks for it
                    embeddedServerInstance.getEmbeddedServer().getApplicationConfiguration().getInstance().getZone()
                        .ifPresent(zone -> meta.put(ServiceInstance.ZONE, zone));
                }
                String serviceId = idGenerator.generateId(environment, instance);
                ConsulNewServiceEntry serviceEntry = new ConsulNewServiceEntry(
                    applicationName,
                    address,
                    instance.getPort(),
                    tags(instance, registration),
                    serviceId,
                    meta,
                    createChecks(instance, registration, address, serviceId, checkStatus));
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
                                           String address,
                                           @NonNull String serviceId,
                                           String checkStatus) {
        List<ConsulCheck> checks = new ArrayList<>(checkConfigurations.size() + 1);
        ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig = registration.getCheck();
        if (checkConfig.isEnabled()) {
            ConsulCheck check = createCheck(checkConfig, instance, registration, address, checkStatus);
            // Consul derives the ID of a check without an explicit ID from its position once a service has more
            // than one check, so set it explicitly to keep the ID the TTL heartbeat reports to stable
            check.setId(defaultCheckId(checkConfig, serviceId));
            checks.add(check);
        }
        for (ConsulCheckConfiguration additionalCheck : checkConfigurations) {
            if (additionalCheck.isEnabled()) {
                checks.add(createAdditionalCheck(additionalCheck, instance, registration, address, serviceId));
            }
        }
        for (ConsulCheckCustomizer customizer : checkCustomizers) {
            customizer.customize(instance, serviceId, checks);
        }
        return checks;
    }

    /**
     * The ID of the default check: the configured ID or the ID Consul assigns to the only check of a service.
     *
     * @param checkConfig The check configuration
     * @param serviceId   The service ID
     * @return The check ID
     */
    @NonNull
    private static String defaultCheckId(ConsulConfiguration.ConsulRegistrationConfiguration.@NonNull CheckConfiguration checkConfig,
                                         @NonNull String serviceId) {
        return checkConfig.getId().orElse("service:" + serviceId);
    }

    @NonNull
    private ConsulCheck createAdditionalCheck(@NonNull ConsulCheckConfiguration checkConfig,
                                              @NonNull ServiceInstance instance,
                                              ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration,
                                              @Nullable String address,
                                              @NonNull String serviceId) {
        String name = checkConfig.getName();
        int types = (checkConfig.getHttp().isPresent() ? 1 : 0)
            + (checkConfig.getTcp().isPresent() ? 1 : 0)
            + (checkConfig.getGrpc().isPresent() ? 1 : 0)
            + (checkConfig.getTtl().isPresent() ? 1 : 0);
        if (types != 1) {
            throw new ConfigurationException("Consul check [" + name + "] must define exactly one of http, tcp, grpc or ttl, see "
                + ConsulCheckConfiguration.PREFIX + "." + name);
        }

        ConsulCheck check = new ConsulCheck();
        check.setName(name);
        check.setId(checkConfig.getId().orElse("service:" + serviceId + ":" + name));
        checkConfig.getNotes().ifPresent(check::setNotes);
        checkConfig.getStatus().ifPresent(status -> check.setStatus(status.toString()));
        checkConfig.getDeregisterCriticalServiceAfter().map(ConsulAutoRegistration::goDuration).ifPresent(check::setDeregisterCriticalServiceAfter);
        Optional<Duration> ttl = checkConfig.getTtl();
        if (ttl.isPresent()) {
            check.setTtl(goDuration(ttl.get()));
            return check;
        }

        check.setInterval(goDuration(checkConfig.getInterval()));
        checkConfig.getTimeout().map(ConsulAutoRegistration::goDuration).ifPresent(check::setTimeout);
        checkConfig.getTlsSkipVerify().ifPresent(check::setTlsSkipVerify);
        checkConfig.getHttp().ifPresent(http -> {
            check.setHttp(additionalCheckUrl(name, http, instance, registration, address));
            checkConfig.getMethod().ifPresent(check::setMethod);
            if (!checkConfig.getHeaders().isEmpty()) {
                check.setHeader(new HashMap<>(checkConfig.getHeaders()));
            }
        });
        checkConfig.getTcp().ifPresent(check::setTcp);
        checkConfig.getGrpc().ifPresent(grpc -> {
            check.setGrpc(grpc);
            checkConfig.getGrpcUseTls().ifPresent(check::setGrpcUseTls);
        });
        return check;
    }

    @NonNull
    private URL additionalCheckUrl(@NonNull String name,
                                   @NonNull String http,
                                   @NonNull ServiceInstance instance,
                                   ConsulConfiguration.@NonNull ConsulRegistrationConfiguration registration,
                                   @Nullable String address) {
        try {
            if (http.contains("://")) {
                return new URL(http);
            }
            URL serverUrl = serverUrl(instance, registration, address).orElseGet(() -> {
                try {
                    return instance.getURI().toURL();
                } catch (MalformedURLException | IllegalArgumentException e) {
                    throw new ConfigurationException("Unable to resolve the URL of Consul check [" + name + "]: " + e.getMessage(), e);
                }
            });
            return new URL(serverUrl, http);
        } catch (MalformedURLException e) {
            throw new ConfigurationException("Invalid URL [" + http + "] configured for Consul check [" + name + "]", e);
        }
    }

    /**
     * Formats a duration in the format Consul expects, for example {@code 10s} or {@code 1500ms}.
     *
     * @param duration The duration
     * @return The formatted duration
     */
    @NonNull
    private static String goDuration(@NonNull Duration duration) {
        long millis = duration.toMillis();
        return millis % 1000 == 0 ? (millis / 1000) + "s" : millis + "ms";
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
                                    @Nullable String address,
                                    String checkStatus) {

        ConsulCheck check = new ConsulCheck();
        check.setDeregisterCriticalServiceAfter(deregisterCriticalServiceAfterCheck(checkConfig));
        checkConfig.getId().ifPresent(check::setId);
        check.setStatus(checkStatus);
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
        return serverUrl(instance, registration, address).map(serverURL -> {
            try {
                return new URL(serverURL, registration.getHealthPath().orElse("/health"));
            } catch (MalformedURLException e) {
                throw new DiscoveryException("Invalid health path configured: " + registration.getHealthPath());
            }
        });
    }

    private Optional<URL> serverUrl(@NonNull ServiceInstance instance,
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
            return Optional.of(serverURL);
        }
        return Optional.empty();
    }
}
