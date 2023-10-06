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
    @Nullable @JsonProperty("ID")
    private String id;

    /**
     * Specifies the namespace of the check you register. This field takes precedence over the ns query parameter, one of several other methods to specify the namespace.
     */
    @Nullable @JsonProperty("Namespace")
    private String namespace;

    /**
     * Specifies the frequency at which to run this check. This is required for HTTP, TCP, and UDP checks.
     */
    @Nullable @JsonProperty("Interval")
    private String interval;

    /**
     * Specifies arbitrary information for humans. This is not used by Consul internally.
     */
    @Nullable @JsonProperty("Notes")
    private String notes;

    /**
     * Specifies that checks associated with a service should deregister after this time. This is specified as a time duration with suffix like "10m". If a check is in the critical state for more than this configured value, then its associated service (and all of its associated checks) will automatically be deregistered. The minimum timeout is 1 minute, and the process that reaps critical services runs every 30 seconds, so it may take slightly longer than the configured timeout to trigger the deregistration. This should generally be configured with a timeout that's much, much longer than any expected recoverable outage for the given service.
     */
    @Nullable @JsonProperty("DeregisterCriticalServiceAfter")
    private String deregisterCriticalServiceAfter;

    /**
     * Specifies command arguments to run to update the status of the check. Prior to Consul 1.0, checks used a single Script field to define the command to run, and would always run in a shell. In Consul 1.0, the Args array was added so that checks can be run without a shell. The Script field is deprecated, and you should include the shell in the Args to run under a shell, eg. "args": ["sh", "-c", "..."].
     */
    @Nullable @JsonProperty("Args")
    private List<String> args;

    /**
     * Specifies the ID of the node for an alias check. If no service is specified, the check will alias the health of the node. If a service is specified, the check will alias the specified service on this particular node.
     */
    @Nullable @JsonProperty("AliasNode")
    private String aliasNode;

    /**
     * Specifies the ID of a service for an alias check. If the service is not registered with the same agent, AliasNode must also be specified. Note this is the service ID and not the service name (though they are very often the same).
     */
    @Nullable @JsonProperty("AliasService")
    private String aliasService;

    /**
     * Specifies that the check is a Docker check, and Consul will evaluate the script every Interval in the given container using the specified Shell. Note that Shell is currently only supported for Docker checks.
     */
    @Nullable @JsonProperty("DockerContainerID")
    private String dockerContainerID;

    /**
     * Specifies a gRPC check's endpoint that supports the standard gRPC health checking protocol. The state of the check will be updated at the given Interval by probing the configured endpoint. Add the service identifier after the gRPC check's endpoint in the following format to check for a specific service instead of the whole gRPC server /:service_identifier.
     */
    @Nullable @JsonProperty("GRPC")
    private String grpc;

    /**
     * Specifies whether to use TLS for this gRPC health check. If TLS is enabled, then by default, a valid TLS certificate is expected. Certificate verification can be turned off by setting TLSSkipVerify to true.
     */
    @Nullable @JsonProperty("GRPCUseTLS")
    private Boolean grpcUseTls;

    /**
     * Specifies an address that uses http2 to run a ping check on. At the specified Interval, a connection is made to the address, and a ping is sent. If the ping is successful, the check will be classified as passing, otherwise it will be marked as critical. TLS is used by default. To disable TLS and use h2c, set H2PingUseTLS to false. If TLS is enabled, a valid SSL certificate is required by default, but verification can be removed with TLSSkipVerify.
     */
    @Nullable @JsonProperty("H2PING")
    private String h2ping;

    /**
     * Specifies if TLS should be used for H2PING check. If TLS is enabled, a valid SSL certificate is required by default, but verification can be removed with TLSSkipVerify.
     */
    @Nullable @JsonProperty("H2PingUseTLS")
    private Boolean h2PingUseTLS;

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
     * Specifies a body that should be sent with HTTP checks.
     */
    @Nullable @JsonProperty("Body")
    private String body;

    /**
     * Specifies whether to disable following HTTP redirects when performing an HTTP check.
     */
    @Nullable @JsonProperty("DisableRedirects")
    private Boolean disableRedirects;

    /**
     * Specifies a set of headers that should be set for HTTP checks. Each header can have multiple values.
     */
    @Nullable @JsonProperty("Header")
    private Map<CharSequence, List<String>> header;

    /**
     * Specifies a timeout for outgoing connections in the case of a Script, HTTP, TCP, UDP, or gRPC check. Can be specified in the form of "10s" or "5m" (i.e., 10 seconds or 5 minutes, respectively).
     */
    @Nullable @JsonProperty("Timeout")
    private String timeout;

    /**
     * Allow to put a maximum size of text for the given check. This value must be greater than 0, by default, the value is 4k. The value can be further limited for all checks of a given agent using the check_output_max_size flag in the agent.
     */
    @Nullable @JsonProperty("OutputMaxSize")
    private Integer outputMaxSize;

    /**
     * Specifies an optional string used to set the SNI host when connecting via TLS. For an HTTP check, this value is set automatically if the URL uses a hostname (not an IP address).
     */
    @Nullable @JsonProperty("TLSServerName")
    private String tlsServerName;

    /**
     * Specifies if the certificate for an HTTPS check should not be verified.
     */
    @Nullable @JsonProperty("TLSSkipVerify")
    private Boolean tlsSkipVerify;

    /**
     * Specifies a TCP to connect against the value of TCP (expected to be an IP or hostname plus port combination) every Interval. If the connection attempt is successful, the check is passing. If the connection attempt is unsuccessful, the check is critical. In the case of a hostname that resolves to both IPv4 and IPv6 addresses, an attempt will be made to both addresses, and the first successful connection attempt will result in a successful check.
     */
    @Nullable @JsonProperty("TCP")
    private String tcp;

    /**
     * Specifies whether to use TLS for this TCP health check. If TLS is enabled, then by default, a valid TLS certificate is expected. Certificate verification can be turned off by setting TLSSkipVerify to true.
     */
    @Nullable @JsonProperty("TCPUseTLS")
    private Boolean tcpUseTls;

    /**
     * Specifies a UDP IP address/hostname and port. The check sends datagrams to the value specified at the interval specified in the Interval configuration. If the datagram is sent successfully or a timeout is returned, the check is set to the passing state. The check is logged as critical if the datagram is sent unsuccessfully.
     */
    @Nullable @JsonProperty("UPD")
    private String udp;

    /**
     * Specifies the identifier of an OS-level service to check. You can specify either Windows Services on Windows or SystemD services on Unix
     */
    @Nullable @JsonProperty("OSService")
    private String osService;

    /**
     * Specifies this is a TTL check, and the TTL endpoint must be used periodically to update the state of the check. If the check is not set to passing within the specified duration, then the check will be set to the failed state.
     */
    @Nullable @JsonProperty("TTL")
    private String ttl;

    /**
     * Specifies the ID of a service to associate the registered check with an existing service provided by the agent.
     */
    @Nullable @JsonProperty("ServiceID")
    private String serviceID;

    /**
     * Specifies the initial status of the health check.
     */
    @Nullable @JsonProperty("Status")
    private String status;

    /**
     * Specifies the number of consecutive successful results required before check status transitions to passing. Available for HTTP, TCP, gRPC, Docker & Monitor checks. Added in Consul 1.7.0.
     */
    @Nullable @JsonProperty("SuccessBeforePassing")
    private Integer successBeforePassing;

    /**
     * Specifies the number of consecutive unsuccessful results required before check status transitions to warning. Defaults to the same value as FailuresBeforeCritical. Values higher than FailuresBeforeCritical are invalid. Available for HTTP, TCP, gRPC, Docker & Monitor checks. Added in Consul 1.11.0.
     */
    @Nullable @JsonProperty("FailuresBeforeWarning")
    private Integer failuresBeforeWarning;

    /**
     * Specifies the number of consecutive unsuccessful results required before check status transitions to critical. Available for HTTP, TCP, gRPC, Docker & Monitor checks. Added in Consul 1.7.0.
     */
    @Nullable @JsonProperty("FailuresBeforeCritical")
    private Integer failuresBeforeCritical;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDeregisterCriticalServiceAfter() {
        return deregisterCriticalServiceAfter;
    }

    public void setDeregisterCriticalServiceAfter(String deregisterCriticalServiceAfter) {
        this.deregisterCriticalServiceAfter = deregisterCriticalServiceAfter;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getAliasNode() {
        return aliasNode;
    }

    public void setAliasNode(String aliasNode) {
        this.aliasNode = aliasNode;
    }

    public String getAliasService() {
        return aliasService;
    }

    public void setAliasService(String aliasService) {
        this.aliasService = aliasService;
    }

    public String getDockerContainerID() {
        return dockerContainerID;
    }

    public void setDockerContainerID(String dockerContainerID) {
        this.dockerContainerID = dockerContainerID;
    }

    public String getGrpc() {
        return grpc;
    }

    public void setGrpc(String grpc) {
        this.grpc = grpc;
    }

    public Boolean getGrpcUseTls() {
        return grpcUseTls;
    }

    public void setGrpcUseTls(Boolean grpcUseTls) {
        this.grpcUseTls = grpcUseTls;
    }

    public String getH2ping() {
        return h2ping;
    }

    public void setH2ping(String h2ping) {
        this.h2ping = h2ping;
    }

    public Boolean getH2PingUseTLS() {
        return h2PingUseTLS;
    }

    public void setH2PingUseTLS(Boolean h2PingUseTLS) {
        this.h2PingUseTLS = h2PingUseTLS;
    }

    public URL getHttp() {
        return http;
    }

    public void setHttp(URL http) {
        this.http = http;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Boolean getDisableRedirects() {
        return disableRedirects;
    }

    public void setDisableRedirects(Boolean disableRedirects) {
        this.disableRedirects = disableRedirects;
    }

    public Map<CharSequence, List<String>> getHeader() {
        return header;
    }

    public void setHeader(Map<CharSequence, List<String>> header) {
        this.header = header;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public Integer getOutputMaxSize() {
        return outputMaxSize;
    }

    public void setOutputMaxSize(Integer outputMaxSize) {
        this.outputMaxSize = outputMaxSize;
    }

    public String getTlsServerName() {
        return tlsServerName;
    }

    public void setTlsServerName(String tlsServerName) {
        this.tlsServerName = tlsServerName;
    }

    public Boolean getTlsSkipVerify() {
        return tlsSkipVerify;
    }

    public void setTlsSkipVerify(Boolean tlsSkipVerify) {
        this.tlsSkipVerify = tlsSkipVerify;
    }

    public String getTcp() {
        return tcp;
    }

    public void setTcp(String tcp) {
        this.tcp = tcp;
    }

    public Boolean getTcpUseTls() {
        return tcpUseTls;
    }

    public void setTcpUseTls(Boolean tcpUseTls) {
        this.tcpUseTls = tcpUseTls;
    }

    public String getUdp() {
        return udp;
    }

    public void setUdp(String udp) {
        this.udp = udp;
    }

    public String getOsService() {
        return osService;
    }

    public void setOsService(String osService) {
        this.osService = osService;
    }

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public String getServiceID() {
        return serviceID;
    }

    public void setServiceID(String serviceID) {
        this.serviceID = serviceID;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSuccessBeforePassing() {
        return successBeforePassing;
    }

    public void setSuccessBeforePassing(Integer successBeforePassing) {
        this.successBeforePassing = successBeforePassing;
    }

    public Integer getFailuresBeforeWarning() {
        return failuresBeforeWarning;
    }

    public void setFailuresBeforeWarning(Integer failuresBeforeWarning) {
        this.failuresBeforeWarning = failuresBeforeWarning;
    }

    public Integer getFailuresBeforeCritical() {
        return failuresBeforeCritical;
    }

    public void setFailuresBeforeCritical(Integer failuresBeforeCritical) {
        this.failuresBeforeCritical = failuresBeforeCritical;
    }
}
