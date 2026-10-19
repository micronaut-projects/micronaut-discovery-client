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
package io.micronaut.discovery.consul;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsistencyMode;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.ssl.AbstractClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for consul.
 *
 * @author graemerocher
 * @since 1.0
 */
@RequiresConsul
@ConfigurationProperties(ConsulConfiguration.PREFIX)
@BootstrapContextCompatible
@Requires(property = ConsulConfiguration.PREFIX)
public class ConsulConfiguration extends DiscoveryClientConfiguration {

    /**
     * The prefix to use for all Consul settings.
     */
    public static final String PREFIX = "consul.client";

    private static final int CONSULT_DEFAULT_PORT = 8500;
    private final ConsulConnectionPoolConfiguration consulConnectionPoolConfiguration;

    private String aclToken;
    private boolean healthCheck = true;
    private ConsulRegistrationConfiguration registration = new ConsulRegistrationConfiguration();
    private ConsulDiscoveryConfiguration discovery = new ConsulDiscoveryConfiguration();
    private ConsulConfigDiscoveryConfiguration configuration = new ConsulConfigDiscoveryConfiguration();

    /**
     * Default Consult configuration.
     */
    public ConsulConfiguration() {
        setPort(CONSULT_DEFAULT_PORT);
        consulConnectionPoolConfiguration = new ConsulConnectionPoolConfiguration();
    }

    /**
     * @param consulConnectionPoolConfiguration The connection pool configuration
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public ConsulConfiguration(ConsulConnectionPoolConfiguration consulConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        setPort(CONSULT_DEFAULT_PORT);
        this.consulConnectionPoolConfiguration = consulConnectionPoolConfiguration;
    }

    /**
     * @return Whether the Consul server should be considered for health checks.
     * @see io.micronaut.discovery.consul.health.ConsulHealthIndicator
     */
    public boolean isHealthCheck() {
        return healthCheck;
    }

    /**
     * Sets whether the Consul server should be considered for health checks.
     * @see io.micronaut.discovery.consul.health.ConsulHealthIndicator

     * @param healthCheck True if it should
     */
    public void setHealthCheck(boolean healthCheck) {
        this.healthCheck = healthCheck;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return consulConnectionPoolConfiguration;
    }

    /**
     * Applies the SSL configuration bound from {@code consul.client.ssl} to the Consul HTTP client.
     * When no {@code consul.client.ssl} settings are present the default client SSL configuration is retained.
     *
     * @param sslConfiguration The Consul SSL configuration
     * @since 5.3.0
     */
    @Inject
    public void setConsulSslConfiguration(@Nullable ConsulSslConfiguration sslConfiguration) {
        if (sslConfiguration != null) {
            super.setSslConfiguration(sslConfiguration);
        }
    }

    /**
     * @return The configuration discovery configuration
     */
    public ConsulConfigDiscoveryConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @param configuration The {@link ConsulConfigDiscoveryConfiguration}
     */
    @Inject
    public void setConfiguration(ConsulConfigDiscoveryConfiguration configuration) {
        if (configuration != null) {
            this.configuration = configuration;
        }
    }

    /**
     * @return The token to include in all requests as the {@code X-Consul-Token} header
     * @deprecated Use {@link #getAclToken()}.
     */
    @Deprecated(since = "5.0", forRemoval = true)
    public Optional<String> getAslToken() {
        return Optional.ofNullable(aclToken);
    }

    /**
     * @param aslToken The asl token
     * @deprecated Use {@link #setAclToken(String)}.
     */
    @Deprecated(since = "5.0", forRemoval = true)
    public void setAslToken(String aslToken) {
        this.aclToken = aslToken;
    }

    /**
     * @return The token to include in all requests as the {@code X-Consul-Token} header
     */
    public Optional<String> getAclToken() {
        return Optional.ofNullable(aclToken);
    }

    /**
     * @param aclToken The asl token
     */
    public void setAclToken(String aclToken) {
        this.aclToken = aclToken;
    }

    /**
     * @return The registration configuration
     */
    @Override
    public ConsulRegistrationConfiguration getRegistration() {
        return registration;
    }

    /**
     * @param registration The {@link ConsulRegistrationConfiguration}
     */
    @Inject
    public void setRegistration(ConsulRegistrationConfiguration registration) {
        if (registration != null) {
            this.registration = registration;
        }
    }

    /**
     * @return The discovery configuration
     */
    @Override
    public ConsulDiscoveryConfiguration getDiscovery() {
        return discovery;
    }

    /**
     * @param discovery The {@link ConsulDiscoveryConfiguration}
     */
    @Inject
    public void setDiscovery(ConsulDiscoveryConfiguration discovery) {
        if (discovery != null) {
            this.discovery = discovery;
        }
    }

    /**
     * @return The serviceID
     */
    @Override
    protected String getServiceID() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public String toString() {
        return "ConsulConfiguration{" +
            "aclToken='" + aclToken + '\'' +
            ", registration=" + registration +
            ", discovery=" + discovery +
            "} " + super.toString();
    }

    /**
     * Configuration class for Consul client config.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    @Requires(property = ConsulConfiguration.PREFIX)
    public static class ConsulConfigDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        /**
         * The full prefix for this configuration.
         */
        public static final String PREFIX = ConsulConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;

        private String datacenter;
        private ConsistencyMode consistencyMode = ConsistencyMode.DEFAULT;

        /**
         * The consistency mode used to read configuration from the Consul KV store. Defaults to
         * {@link ConsistencyMode#DEFAULT}, which sends neither the {@code consistent} nor the {@code stale} flag.
         *
         * @return The consistency mode
         * @since 5.3.0
         */
        public ConsistencyMode getConsistencyMode() {
            return consistencyMode;
        }

        /**
         * @param consistencyMode The consistency mode used to read configuration from the Consul KV store
         * @since 5.3.0
         */
        public void setConsistencyMode(ConsistencyMode consistencyMode) {
            this.consistencyMode = consistencyMode != null ? consistencyMode : ConsistencyMode.DEFAULT;
        }

        /**
         * The data center to use to read configuration.
         *
         * @return The data center name
         */
        public Optional<String> getDatacenter() {
            return Optional.ofNullable(datacenter);
        }

        /**
         * @param datacenter The datacenter
         */
        public void setDatacenter(String datacenter) {
            this.datacenter = datacenter;
        }
    }

    /**
     * Configuration class for Consul client discovery.
     */
    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    @Requires(property = ConsulConfiguration.PREFIX)
    public static class ConsulDiscoveryConfiguration extends DiscoveryConfiguration {

        private Map<String, String> tags = Collections.emptyMap();
        private Map<String, List<String>> queryTags = Collections.emptyMap();
        private List<String> defaultQueryTags = Collections.emptyList();
        private Map<String, String> filters = Collections.emptyMap();
        private String defaultFilter;
        private ConsistencyMode consistencyMode = ConsistencyMode.DEFAULT;
        private Map<String, String> schemes = Collections.emptyMap();
        private Map<String, String> datacenters = Collections.emptyMap();
        private boolean passing = false;

        /**
         * Whether services that are not passing health checks should be returned.
         *
         * @return True if only passing services should be returned (defaults to false)
         */
        public boolean isPassing() {
            return passing;
        }

        /**
         * @param passing Whether services that are not passing health checks should be returned
         */
        public void setPassing(boolean passing) {
            this.passing = passing;
        }

        /**
         * A map of service ID to a single tag to use for querying.
         *
         * @return The tags
         * @deprecated Use {@link #getQueryTags()}, which accepts several tags per service
         */
        @Deprecated(since = "5.3.0")
        public Map<String, String> getTags() {
            return tags;
        }

        /**
         * @param tags The tags to use for querying
         * @deprecated Use {@link #setQueryTags(Map)}, which accepts several tags per service
         */
        @Deprecated(since = "5.3.0")
        public void setTags(Map<String, String> tags) {
            if (tags != null) {
                this.tags = tags;
            }
        }

        /**
         * A map of service ID to the tags to use for querying. Only instances carrying every tag are returned. Takes
         * precedence over {@link #getTags()} and {@link #getDefaultQueryTags()}.
         *
         * @return The query tags per service ID
         * @since 5.3.0
         */
        public Map<String, List<String>> getQueryTags() {
            return queryTags;
        }

        /**
         * @param queryTags The tags to use for querying per service ID
         * @since 5.3.0
         */
        public void setQueryTags(Map<String, List<String>> queryTags) {
            if (queryTags != null) {
                this.queryTags = queryTags;
            }
        }

        /**
         * The tags to use for querying services that have no tags configured in {@link #getQueryTags()} or
         * {@link #getTags()}.
         *
         * @return The default query tags
         * @since 5.3.0
         */
        public List<String> getDefaultQueryTags() {
            return defaultQueryTags;
        }

        /**
         * @param defaultQueryTags The tags to use for querying services without specific tags
         * @since 5.3.0
         */
        public void setDefaultQueryTags(List<String> defaultQueryTags) {
            if (defaultQueryTags != null) {
                this.defaultQueryTags = defaultQueryTags;
            }
        }

        /**
         * A map of service ID to a Consul filter expression (see
         * https://developer.hashicorp.com/consul/api-docs/features/filtering) applied when querying the service.
         *
         * @return The filter expressions per service ID
         * @since 5.3.0
         */
        public Map<String, String> getFilters() {
            return filters;
        }

        /**
         * @param filters The filter expressions per service ID
         * @since 5.3.0
         */
        public void setFilters(Map<String, String> filters) {
            if (filters != null) {
                this.filters = filters;
            }
        }

        /**
         * The Consul filter expression applied when querying services that have no filter configured in
         * {@link #getFilters()}.
         *
         * @return The default filter expression
         * @since 5.3.0
         */
        public Optional<String> getDefaultFilter() {
            return Optional.ofNullable(defaultFilter);
        }

        /**
         * @param defaultFilter The filter expression applied to services without a specific filter
         * @since 5.3.0
         */
        public void setDefaultFilter(String defaultFilter) {
            this.defaultFilter = defaultFilter;
        }

        /**
         * The consistency mode used to query service instances. Defaults to {@link ConsistencyMode#DEFAULT}, which
         * sends neither the {@code consistent} nor the {@code stale} flag.
         *
         * @return The consistency mode
         * @since 5.3.0
         */
        public ConsistencyMode getConsistencyMode() {
            return consistencyMode;
        }

        /**
         * @param consistencyMode The consistency mode used to query service instances
         * @since 5.3.0
         */
        public void setConsistencyMode(ConsistencyMode consistencyMode) {
            this.consistencyMode = consistencyMode != null ? consistencyMode : ConsistencyMode.DEFAULT;
        }

        /**
         * Resolves the tags to query a service with: the entry of {@link #getQueryTags()}, otherwise the single tag of
         * {@link #getTags()}, otherwise {@link #getDefaultQueryTags()}.
         *
         * @param serviceId The service ID
         * @return The tags, possibly empty
         * @since 5.3.0
         */
        public List<String> resolveQueryTags(String serviceId) {
            List<String> serviceTags = queryTags.get(serviceId);
            if (serviceTags != null) {
                return serviceTags;
            }
            String tag = tags.get(serviceId);
            if (tag != null) {
                return Collections.singletonList(tag);
            }
            return defaultQueryTags;
        }

        /**
         * Resolves the filter expression to query a service with: the entry of {@link #getFilters()}, otherwise
         * {@link #getDefaultFilter()}.
         *
         * @param serviceId The service ID
         * @return The filter expression, if any
         * @since 5.3.0
         */
        public Optional<String> resolveFilter(String serviceId) {
            String filter = filters.get(serviceId);
            if (filter != null) {
                return Optional.of(filter);
            }
            return getDefaultFilter();
        }

        /**
         * A map of service ID to data centers to query.
         *
         * @return The map to query
         */
        public Map<String, String> getDatacenters() {
            return datacenters;
        }

        /**
         * @param datacenters The data centers to query
         */
        public void setDatacenters(Map<String, String> datacenters) {
            if (datacenters != null) {
                this.datacenters = datacenters;
            }
        }

        /**
         * A map of service ID to protocol scheme (eg. http, https etc.). Default is http.
         *
         * @return A map of schemes
         */
        public Map<String, String> getSchemes() {
            return schemes;
        }

        /**
         * @param schemes The service ID to protocol scheme
         */
        public void setSchemes(Map<String, String> schemes) {
            this.schemes = schemes;
        }

        @Override
        public String toString() {
            return "ConsulDiscoveryConfiguration{" +
                "tags=" + tags +
                ", queryTags=" + queryTags +
                ", defaultQueryTags=" + defaultQueryTags +
                ", filters=" + filters +
                ", defaultFilter=" + defaultFilter +
                ", consistencyMode=" + consistencyMode +
                ", datacenters=" + datacenters +
                ", passing=" + passing +
                '}';
        }
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class ConsulConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

    /**
     * The SSL configuration used by the Consul HTTP client, bound from {@code consul.client.ssl}.
     *
     * @since 5.3.0
     */
    @ConfigurationProperties("ssl")
    @BootstrapContextCompatible
    @Requires(property = ConsulSslConfiguration.PREFIX)
    public static class ConsulSslConfiguration extends AbstractClientSslConfiguration {

        /**
         * The prefix to use for all Consul client SSL settings.
         */
        public static final String PREFIX = ConsulConfiguration.PREFIX + ".ssl";

        /**
         * Creates the Consul client SSL configuration. SSL is enabled by default, like the default HTTP client
         * SSL configuration.
         */
        public ConsulSslConfiguration() {
            setEnabled(true);
        }

        /**
         * Sets the key configuration.
         *
         * @param keyConfiguration The key configuration
         */
        @Inject
        void setKey(@Nullable ConsulKeyConfiguration keyConfiguration) {
            if (keyConfiguration != null) {
                super.setKey(keyConfiguration);
            }
        }

        /**
         * Sets the key store configuration.
         *
         * @param keyStoreConfiguration The key store configuration
         */
        @Inject
        void setKeyStore(@Nullable ConsulKeyStoreConfiguration keyStoreConfiguration) {
            if (keyStoreConfiguration != null) {
                super.setKeyStore(keyStoreConfiguration);
            }
        }

        /**
         * Sets the trust store configuration.
         *
         * @param trustStoreConfiguration The trust store configuration
         */
        @Inject
        void setTrustStore(@Nullable ConsulTrustStoreConfiguration trustStoreConfiguration) {
            if (trustStoreConfiguration != null) {
                super.setTrustStore(trustStoreConfiguration);
            }
        }

        /**
         * The Consul client {@link SslConfiguration.KeyConfiguration}, bound from {@code consul.client.ssl.key}.
         */
        @ConfigurationProperties(SslConfiguration.KeyConfiguration.PREFIX)
        @BootstrapContextCompatible
        @Requires(property = ConsulSslConfiguration.PREFIX + "." + SslConfiguration.KeyConfiguration.PREFIX)
        public static class ConsulKeyConfiguration extends SslConfiguration.KeyConfiguration {
        }

        /**
         * The Consul client {@link SslConfiguration.KeyStoreConfiguration}, bound from {@code consul.client.ssl.key-store}.
         */
        @ConfigurationProperties(SslConfiguration.KeyStoreConfiguration.PREFIX)
        @BootstrapContextCompatible
        @Requires(property = ConsulSslConfiguration.PREFIX + "." + SslConfiguration.KeyStoreConfiguration.PREFIX)
        public static class ConsulKeyStoreConfiguration extends SslConfiguration.KeyStoreConfiguration {
        }

        /**
         * The Consul client {@link SslConfiguration.TrustStoreConfiguration}, bound from {@code consul.client.ssl.trust-store}.
         */
        @ConfigurationProperties(SslConfiguration.TrustStoreConfiguration.PREFIX)
        @BootstrapContextCompatible
        @Requires(property = ConsulSslConfiguration.PREFIX + "." + SslConfiguration.TrustStoreConfiguration.PREFIX)
        public static class ConsulTrustStoreConfiguration extends SslConfiguration.TrustStoreConfiguration {
        }
    }

    /**
     * Configuration class for Consul client registration.
     */
    @ConfigurationProperties(RegistrationConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class ConsulRegistrationConfiguration extends RegistrationConfiguration {

        /**
         * The prefix to use for all Consul client registration settings.
         */
        public static final String PREFIX = ConsulConfiguration.PREFIX + "." + RegistrationConfiguration.PREFIX;

        private List<String> tags = Collections.emptyList();
        private Map<String, String> meta = Collections.emptyMap();
        private CheckConfiguration check = new CheckConfiguration();

        /**
         * @return That tags to use for registering the service
         */
        public List<String> getTags() {
            return tags;
        }

        /**
         * @param tags The tags for registering the service
         */
        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        /**
         * @return That metadata to use for registering the service
         */
        public Map<String, String> getMeta() {
            return meta;
        }

        /**
         * @param meta The metadata for registering the service
         */
        public void setMeta(Map<String, String> meta) {
            this.meta = meta;
        }

        /**
         * @return The Consul client settings for HTTP check
         */
        public CheckConfiguration getCheck() {
            return check;
        }

        /**
         * @param check The Consul client settings for HTTP check
         */
        public void setCheck(CheckConfiguration check) {
            this.check = check;
        }

        @Override
        public String toString() {
            return "ConsulRegistrationConfiguration{" +
                "tags=" + tags +
                ", meta=" + meta +
                ", check=" + check +
                '}';
        }

        /**
         * Configuration for the HTTP check. See https://www.consul.io/api/agent/check.html.
         */
        @ConfigurationProperties("check")
        @BootstrapContextCompatible
        public static class CheckConfiguration implements Toggleable {

            /**
             * The default enable value.
             */
            @SuppressWarnings("WeakerAccess")
            public static final boolean DEFAULT_ENABLED = true;

            /**
             * The default http value.
             */
            @SuppressWarnings("WeakerAccess")
            public static final boolean DEFAULT_HTTP = false;

            /**
             * The default interval seconds.
             */
            @SuppressWarnings("WeakerAccess")
            public static final int DEFAULT_INTERVAL_SECONDS = 15;

            private HttpMethod method = HttpMethod.GET;
            private Duration interval = Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS);
            private Map<CharSequence, List<String>> headers = Collections.emptyMap();
            private Duration deregisterCriticalServiceAfter;
            private String notes;
            private String id;
            private Boolean tlsSkipVerify;
            private boolean enabled = DEFAULT_ENABLED;
            private boolean http = DEFAULT_HTTP;

            /**
             * @return The interval for the checks
             */
            public Duration getInterval() {
                return interval;
            }

            /**
             * Default value ({@value #DEFAULT_INTERVAL_SECONDS}).
             * @param interval The interval for the checks
             */
            public void setInterval(Duration interval) {
                this.interval = interval;
            }

            /**
             * @return Whether to perform an HTTP check
             */
            public boolean isHttp() {
                return http;
            }

            /**
             * Default value ({@value #DEFAULT_HTTP}).
             * @param http Whether to perform an HTTP check
             */
            public void setHttp(boolean http) {
                this.http = http;
            }

            /**
             * @return Whether the check module is enabled
             */
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * Default value ({@value #DEFAULT_ENABLED}).
             * @param enabled Whether the check module is enabled
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * @return Specifies that checks associated with a service should deregister after this time
             */
            public Optional<Duration> getDeregisterCriticalServiceAfter() {
                return Optional.ofNullable(deregisterCriticalServiceAfter);
            }

            /**
             * @param deregisterCriticalServiceAfter Specifies that checks associated with a service should deregister after this time
             */
            public void setDeregisterCriticalServiceAfter(Duration deregisterCriticalServiceAfter) {
                this.deregisterCriticalServiceAfter = deregisterCriticalServiceAfter;
            }

            /**
             * @return Specifies a unique ID for this check on the node
             */
            public Optional<String> getId() {
                return Optional.ofNullable(id);
            }

            /**
             * @param id The unique ID for this check on the node
             */
            public void setId(String id) {
                this.id = id;
            }

            /**
             * @return Arbitrary information for humans. Not used by Consult
             */
            public Optional<String> getNotes() {
                return Optional.ofNullable(notes);
            }

            /**
             * @param notes Arbitrary information for humans
             */
            public void setNotes(String notes) {
                this.notes = notes;
            }

            /**
             * @return Specifies if the certificate for an HTTPS check should not be verified
             */
            public Optional<Boolean> getTlsSkipVerify() {
                return Optional.ofNullable(tlsSkipVerify);
            }

            /**
             * @param tlsSkipVerify Specifies if the certificate for an HTTPS check should not be verified.
             */
            public void setTlsSkipVerify(Boolean tlsSkipVerify) {
                this.tlsSkipVerify = tlsSkipVerify;
            }

            /**
             * @return Specifies a different HTTP method to be used for an HTTP check.
             */
            public HttpMethod getMethod() {
                return method;
            }

            /**
             * @param method The HTTP method to be used for an HTTP check.
             */
            public void setMethod(HttpMethod method) {
                this.method = method;
            }

            /**
             * @return Specifies a set of headers that should be set for HTTP checks
             */
            public Map<CharSequence, List<String>> getHeaders() {
                return headers;
            }

            /**
             * @param headers Headers for the HTTP checks
             */
            public void setHeaders(Map<CharSequence, List<String>> headers) {
                this.headers = headers;
            }

            @Override
            public String toString() {
                return "CheckConfiguration{" +
                    "method=" + method +
                    ", headers=" + headers +
                    ", deregisterCriticalServiceAfter=" + deregisterCriticalServiceAfter +
                    ", notes='" + notes + '\'' +
                    ", id='" + id + '\'' +
                    ", tlsSkipVerify=" + tlsSkipVerify +
                    ", enabled=" + enabled +
                    '}';
            }
        }
    }

}
