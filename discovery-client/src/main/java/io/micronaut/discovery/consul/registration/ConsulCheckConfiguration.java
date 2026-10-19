/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.Named;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus;
import io.micronaut.http.HttpMethod;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for an additional Consul check that is registered together with the service, next to the
 * default TTL or HTTP check configured with {@code consul.client.registration.check}.
 *
 * <p>Each check is configured under {@code consul.client.registration.checks.<name>} and must define exactly one of
 * {@link #getHttp() http}, {@link #getTcp() tcp}, {@link #getGrpc() grpc} or {@link #getTtl() ttl}, which
 * determines the type of the check.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 * @see <a href="https://developer.hashicorp.com/consul/api-docs/agent/check#json-request-body-schema">Check definition</a>
 */
@EachProperty(ConsulCheckConfiguration.PREFIX)
public class ConsulCheckConfiguration implements Named, Toggleable {

    /**
     * The prefix for additional check configuration.
     */
    public static final String PREFIX = ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".checks";

    /**
     * The default interval seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_INTERVAL_SECONDS = 15;

    private final String name;
    private boolean enabled = true;
    @Nullable
    private String id;
    @Nullable
    private String notes;
    private Duration interval = Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS);
    @Nullable
    private Duration timeout;
    @Nullable
    private Duration ttl;
    @Nullable
    private String http;
    @Nullable
    private HttpMethod method;
    private Map<String, List<String>> headers = Collections.emptyMap();
    @Nullable
    private String tcp;
    @Nullable
    private String grpc;
    @Nullable
    private Boolean grpcUseTls;
    @Nullable
    private Boolean tlsSkipVerify;
    @Nullable
    private Duration deregisterCriticalServiceAfter;
    @Nullable
    private ConsulCheckStatus status;

    /**
     * @param name The name of the check
     */
    public ConsulCheckConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The name of the check, taken from the configuration key
     */
    @Override
    public @NonNull String getName() {
        return name;
    }

    /**
     * @return Whether the check is registered. Defaults to {@code true}.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Whether the check is registered
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The unique ID of the check on the Consul agent. Defaults to {@code service:<service id>:<name>}.
     */
    public Optional<String> getId() {
        return Optional.ofNullable(id);
    }

    /**
     * @param id The unique ID of the check on the Consul agent
     */
    public void setId(@Nullable String id) {
        this.id = id;
    }

    /**
     * @return Arbitrary information for humans. Not used by Consul.
     */
    public Optional<String> getNotes() {
        return Optional.ofNullable(notes);
    }

    /**
     * @param notes Arbitrary information for humans
     */
    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    /**
     * @return The interval at which Consul runs HTTP, TCP and gRPC checks. Not used by TTL checks.
     */
    public @NonNull Duration getInterval() {
        return interval;
    }

    /**
     * Default value ({@value #DEFAULT_INTERVAL_SECONDS} seconds).
     *
     * @param interval The interval at which Consul runs HTTP, TCP and gRPC checks
     */
    public void setInterval(@NonNull Duration interval) {
        this.interval = interval;
    }

    /**
     * @return The timeout of the outgoing connection of HTTP, TCP and gRPC checks
     */
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    /**
     * @param timeout The timeout of the outgoing connection of HTTP, TCP and gRPC checks
     */
    public void setTimeout(@Nullable Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * @return The time to live of a TTL check. The application is responsible for updating the check
     * through the Consul agent API before it expires.
     */
    public Optional<Duration> getTtl() {
        return Optional.ofNullable(ttl);
    }

    /**
     * @param ttl The time to live of a TTL check
     */
    public void setTtl(@Nullable Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * @return The URL of an HTTP check. A value without a scheme, such as {@code /health/readiness}, is resolved
     * against the URL of the registered server.
     */
    public Optional<String> getHttp() {
        return Optional.ofNullable(http);
    }

    /**
     * @param http The URL of an HTTP check, absolute or relative to the URL of the registered server
     */
    public void setHttp(@Nullable String http) {
        this.http = http;
    }

    /**
     * @return The HTTP method of an HTTP check. Consul uses {@code GET} if not set.
     */
    public Optional<HttpMethod> getMethod() {
        return Optional.ofNullable(method);
    }

    /**
     * @param method The HTTP method of an HTTP check
     */
    public void setMethod(@Nullable HttpMethod method) {
        this.method = method;
    }

    /**
     * @return The headers sent by an HTTP check
     */
    public @NonNull Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * @param headers The headers sent by an HTTP check
     */
    public void setHeaders(@MapFormat(keyFormat = StringConvention.RAW) @Nullable Map<String, List<String>> headers) {
        this.headers = headers != null ? headers : Collections.emptyMap();
    }

    /**
     * @return The host and port, for example {@code localhost:5432}, that a TCP check connects to
     */
    public Optional<String> getTcp() {
        return Optional.ofNullable(tcp);
    }

    /**
     * @param tcp The host and port that a TCP check connects to
     */
    public void setTcp(@Nullable String tcp) {
        this.tcp = tcp;
    }

    /**
     * @return The endpoint of a gRPC check that implements the standard gRPC health checking protocol, for example
     * {@code localhost:9090} or {@code localhost:9090/my.Service}
     */
    public Optional<String> getGrpc() {
        return Optional.ofNullable(grpc);
    }

    /**
     * @param grpc The endpoint of a gRPC check
     */
    public void setGrpc(@Nullable String grpc) {
        this.grpc = grpc;
    }

    /**
     * @return Whether a gRPC check uses TLS
     */
    public Optional<Boolean> getGrpcUseTls() {
        return Optional.ofNullable(grpcUseTls);
    }

    /**
     * @param grpcUseTls Whether a gRPC check uses TLS
     */
    public void setGrpcUseTls(@Nullable Boolean grpcUseTls) {
        this.grpcUseTls = grpcUseTls;
    }

    /**
     * @return Whether the certificate of an HTTPS check or a gRPC check with TLS is not verified
     */
    public Optional<Boolean> getTlsSkipVerify() {
        return Optional.ofNullable(tlsSkipVerify);
    }

    /**
     * @param tlsSkipVerify Whether the certificate of an HTTPS check or a gRPC check with TLS is not verified
     */
    public void setTlsSkipVerify(@Nullable Boolean tlsSkipVerify) {
        this.tlsSkipVerify = tlsSkipVerify;
    }

    /**
     * @return The time after which Consul deregisters the service if this check is critical
     */
    public Optional<Duration> getDeregisterCriticalServiceAfter() {
        return Optional.ofNullable(deregisterCriticalServiceAfter);
    }

    /**
     * @param deregisterCriticalServiceAfter The time after which Consul deregisters the service if this check is critical
     */
    public void setDeregisterCriticalServiceAfter(@Nullable Duration deregisterCriticalServiceAfter) {
        this.deregisterCriticalServiceAfter = deregisterCriticalServiceAfter;
    }

    /**
     * @return The initial status of the check. Consul uses {@code critical} if not set.
     */
    public Optional<ConsulCheckStatus> getStatus() {
        return Optional.ofNullable(status);
    }

    /**
     * @param status The initial status of the check
     */
    public void setStatus(@Nullable ConsulCheckStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ConsulCheckConfiguration{" +
            "name='" + name + '\'' +
            ", enabled=" + enabled +
            ", id='" + id + '\'' +
            ", interval=" + interval +
            ", timeout=" + timeout +
            ", ttl=" + ttl +
            ", http='" + http + '\'' +
            ", method=" + method +
            ", tcp='" + tcp + '\'' +
            ", grpc='" + grpc + '\'' +
            ", grpcUseTls=" + grpcUseTls +
            ", tlsSkipVerify=" + tlsSkipVerify +
            ", deregisterCriticalServiceAfter=" + deregisterCriticalServiceAfter +
            ", status=" + status +
            '}';
    }
}
