/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.serde.annotation.Serdeable;

import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Represents a Consul check.
 * <a href="https://developer.hashicorp.com/consul/api-docs/agent/check#json-request-body-schema">Check definition</a>
 */
@Serdeable
public class ConsulCheck {
    /**
     * Specifies the name of the check.
     */
    @JsonProperty("Name")
    private String name;

    /**
     * Specifies a unique ID for this check on the node. This defaults to the "Name" parameter, but it may be necessary to provide an ID for uniqueness. This value will return in the response as "CheckId".
     */
    @Nullable
    @JsonProperty("CheckID")
    private String id;

    /**
     * Specifies the frequency at which to run this check. This is required for HTTP, TCP, and UDP checks.
     */
    @Nullable
    @JsonProperty("Interval")
    private String interval;

    /**
     * Specifies arbitrary information for humans. This is not used by Consul internally.
     */
    @Nullable
    @JsonProperty("Notes")
    private String notes;

    /**
     * Specifies that checks associated with a service should deregister after this time. This is specified as a time duration with suffix like "10m". If a check is in the critical state for more than this configured value, then its associated service (and all of its associated checks) will automatically be deregistered. The minimum timeout is 1 minute, and the process that reaps critical services runs every 30 seconds, so it may take slightly longer than the configured timeout to trigger the deregistration. This should generally be configured with a timeout that's much, much longer than any expected recoverable outage for the given service.
     */
    @Nullable
    @JsonProperty("DeregisterCriticalServiceAfter")
    private String deregisterCriticalServiceAfter;

    /**
     * Specifies an HTTP check to perform a GET request against the value of HTTP (expected to be a URL) every Interval. If the response is any 2xx code, the check is passing. If the response is 429 Too Many Requests, the check is warning. Otherwise, the check is critical. HTTP checks also support SSL. By default, a valid SSL certificate is expected. Certificate verification can be controlled using the TLSSkipVerify.
     */
    @Nullable
    @JsonProperty("HTTP")
    private URL http;

    /**
     * Specifies a different HTTP method to be used for an HTTP check. When no value is specified, GET is used.
     */
    @Nullable @JsonProperty("Method")
    private HttpMethod method;

    /**
     * Specifies a set of headers that should be set for HTTP checks. Each header can have multiple values.
     */
    @Nullable @JsonProperty("Header")
    private Map<CharSequence, List<String>> header;

    /**
     * Specifies if the certificate for an HTTPS check should not be verified.
     */
    @Nullable @JsonProperty("TLSSkipVerify")
    private Boolean tlsSkipVerify;

    /**
     * Specifies this is a TTL check, and the TTL endpoint must be used periodically to update the state of the check. If the check is not set to passing within the specified duration, then the check will be set to the failed state.
     */
    @Nullable @JsonProperty("TTL")
    private String ttl;

    /**
     * Specifies the initial status of the health check.
     */
    @Nullable @JsonProperty("Status")
    private String status;

    /**
     *
     * @return Specifies the name of the check.
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @param name Specifies the name of the check.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return Specifies a unique ID for this check on the node.
     */
    public String getId() {
        return id;
    }

    /**
     *
     * @param id Specifies a unique ID for this check on the node.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     *
     * @return Specifies the frequency at which to run this check. This is required for HTTP, TCP, and UDP checks.
     */
    public String getInterval() {
        return interval;
    }

    /**
     *
     * @param interval Specifies the frequency at which to run this check. This is required for HTTP, TCP, and UDP checks.
     */
    public void setInterval(String interval) {
        this.interval = interval;
    }

    /**
     *
     * @return Specifies arbitrary information for humans. This is not used by Consul internally.
     */
    public String getNotes() {
        return notes;
    }

    /**
     *
     * @param notes Specifies arbitrary information for humans. This is not used by Consul internally.
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     *
     * @return Specifies that checks associated with a service should deregister after this time.
     */
    public String getDeregisterCriticalServiceAfter() {
        return deregisterCriticalServiceAfter;
    }

    /**
     *
     * @param deregisterCriticalServiceAfter Specifies that checks associated with a service should deregister after this time.
     */
    public void setDeregisterCriticalServiceAfter(String deregisterCriticalServiceAfter) {
        this.deregisterCriticalServiceAfter = deregisterCriticalServiceAfter;
    }

    /**
     *
     * @return Specifies an HTTP check to perform a GET request against the value of HTTP (expected to be a URL) every Interval.
     */
    public URL getHttp() {
        return http;
    }

    /**
     *
     * @param http Specifies an HTTP check to perform a GET request against the value of HTTP (expected to be a URL) every Interval.
     */
    public void setHttp(URL http) {
        this.http = http;
    }

    /**
     *
     * @return Specifies a different HTTP method to be used for an HTTP check. When no value is specified, GET is used.
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     *
     * @param method Specifies a different HTTP method to be used for an HTTP check. When no value is specified, GET is used.
     */
    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    /**
     *
     * @return Specifies a set of headers that should be set for HTTP checks. Each header can have multiple values.
     */
    public Map<CharSequence, List<String>> getHeader() {
        return header;
    }

    /**
     *
     * @param header Specifies a set of headers that should be set for HTTP checks. Each header can have multiple values.
     */
    public void setHeader(Map<CharSequence, List<String>> header) {
        this.header = header;
    }

    /**
     *
     * @return Specifies if the certificate for an HTTPS check should not be verified.
     */
    public Boolean getTlsSkipVerify() {
        return tlsSkipVerify;
    }

    /**
     *
     * @param tlsSkipVerify Specifies if the certificate for an HTTPS check should not be verified.
     */
    public void setTlsSkipVerify(Boolean tlsSkipVerify) {
        this.tlsSkipVerify = tlsSkipVerify;
    }

    /**
     *
     * @return Specifies this is a TTL check, and the TTL endpoint must be used periodically to update the state of the check. If the check is not set to passing within the specified duration, then the check will be set to the failed state.
     */
    public String getTtl() {
        return ttl;
    }

    /**
     *
     * @param ttl Specifies this is a TTL check, and the TTL endpoint must be used periodically to update the state of the check. If the check is not set to passing within the specified duration, then the check will be set to the failed state.
     */
    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    /**
     *
     * @return Specifies the initial status of the health check.
     */
    public String getStatus() {
        return status;
    }

    /**
     *
     * @param status Specifies the initial status of the health check.
     */
    public void setStatus(String status) {
        this.status = status;
    }

}
