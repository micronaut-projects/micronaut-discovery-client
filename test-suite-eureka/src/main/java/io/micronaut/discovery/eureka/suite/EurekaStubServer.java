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
package io.micronaut.discovery.eureka.suite;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stub of the Eureka REST API (see https://github.com/Netflix/eureka/wiki/Eureka-REST-operations).
 * <p>
 * It works with raw JSON instead of the Micronaut Eureka model so that it checks the wire format. Registrations are
 * validated like the Eureka server decodes them, and responses use the JSON the Eureka server sends.
 */
@Requires(property = EurekaStubServer.ENABLED)
@Controller("/eureka")
public class EurekaStubServer {

    public static final String ENABLED = "eureka.stub.enabled";

    public static final String DEFAULT_DATA_CENTER_INFO_CLASS = "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo";
    public static final String AMAZON_INFO_CLASS = "com.netflix.appinfo.AmazonInfo";

    private final JsonMapper jsonMapper;
    private final Map<String, Map<String, Map<String, JsonNode>>> applications = new ConcurrentHashMap<>();
    private final List<String> registrations = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<String> heartbeats = new CopyOnWriteArrayList<>();
    private final AtomicInteger failures = new AtomicInteger();

    public EurekaStubServer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @return The raw JSON bodies of the registrations received
     */
    public List<String> getRegistrations() {
        return registrations;
    }

    /**
     * @return The validation errors of the registrations received
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * @return The heartbeats received, as {@code appId/instanceId}
     */
    public List<String> getHeartbeats() {
        return heartbeats;
    }

    /**
     * @param count The number of next registrations to reject with an internal server error
     */
    public void failNextRegistrations(int count) {
        failures.set(count);
    }

    /**
     * @param appId The application id
     * @return The registered instances of the application, by instance id
     */
    public Map<String, Map<String, JsonNode>> getInstances(String appId) {
        return applications.getOrDefault(appId.toUpperCase(Locale.ROOT), Map.of());
    }

    @Post(uri = "/apps/{appId}", consumes = MediaType.APPLICATION_JSON)
    HttpResponse<String> register(String appId, @Body String body) {
        registrations.add(body);
        if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            return HttpResponse.serverError("Unavailable");
        }
        String error;
        Map<String, JsonNode> instance = null;
        try {
            JsonNode root = jsonMapper.readValue(body, JsonNode.class);
            error = validateRegistration(appId, root);
            if (error == null) {
                instance = toMap(root.get("instance"));
            }
        } catch (IOException e) {
            error = "Unparseable registration: " + e.getMessage();
        }
        if (error != null) {
            errors.add(error);
            return HttpResponse.badRequest(error);
        }
        String app = appId.toUpperCase(Locale.ROOT);
        instance.put("app", JsonNode.createStringNode(app));
        applications.computeIfAbsent(app, k -> new ConcurrentHashMap<>())
            .put(instance.get("instanceId").getStringValue(), instance);
        return HttpResponse.noContent();
    }

    @Delete("/apps/{appId}/{instanceId}")
    HttpResponse<?> deregister(String appId, String instanceId) {
        Map<String, Map<String, JsonNode>> instances = applications.get(appId.toUpperCase(Locale.ROOT));
        if (instances == null || instances.remove(instanceId) == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok();
    }

    @Put("/apps/{appId}/{instanceId}")
    HttpResponse<?> heartbeat(String appId, String instanceId) {
        if (!getInstances(appId).containsKey(instanceId)) {
            return HttpResponse.notFound();
        }
        heartbeats.add(appId + "/" + instanceId);
        return HttpResponse.ok();
    }

    @Put("/apps/{appId}/{instanceId}/status")
    HttpResponse<?> updateStatus(String appId, String instanceId, @QueryValue("value") String status) {
        Map<String, JsonNode> instance = getInstances(appId).get(instanceId);
        if (instance == null) {
            return HttpResponse.notFound();
        }
        instance.put("status", JsonNode.createStringNode(status));
        return HttpResponse.ok();
    }

    @Get(uri = "/apps", produces = MediaType.APPLICATION_JSON)
    String applications() throws IOException {
        return json(Map.of("applications", applicationsNode(new ArrayList<>(applications.keySet()))));
    }

    @Get(uri = "/vips/{vipAddress}", produces = MediaType.APPLICATION_JSON)
    String vips(String vipAddress) throws IOException {
        List<String> apps = new ArrayList<>();
        applications.forEach((app, instances) -> {
            if (instances.values().stream().anyMatch(i -> vipAddress.equals(text(i.get("vipAddress"))))) {
                apps.add(app);
            }
        });
        return json(Map.of("applications", applicationsNode(apps)));
    }

    @Get(uri = "/apps/{appId}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<String> application(String appId) throws IOException {
        String app = appId.toUpperCase(Locale.ROOT);
        if (!applications.containsKey(app)) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(json(Map.of("application", applicationNode(app))));
    }

    @Get(uri = "/apps/{appId}/{instanceId}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<String> instance(String appId, String instanceId) throws IOException {
        Map<String, JsonNode> instance = getInstances(appId).get(instanceId);
        if (instance == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(json(Map.of("instance", instanceNode(instance))));
    }

    private JsonNode applicationsNode(List<String> apps) {
        List<JsonNode> list = new ArrayList<>();
        int up = 0;
        for (String app : apps) {
            list.add(applicationNode(app));
            up += getInstances(app).size();
        }
        Map<String, JsonNode> node = new LinkedHashMap<>();
        node.put("versions__delta", JsonNode.createStringNode("1"));
        node.put("apps__hashcode", JsonNode.createStringNode(up > 0 ? "UP_" + up + "_" : ""));
        node.put("application", JsonNode.createArrayNode(list));
        return JsonNode.createObjectNode(node);
    }

    private JsonNode applicationNode(String app) {
        List<JsonNode> instances = new ArrayList<>();
        for (Map<String, JsonNode> instance : getInstances(app).values()) {
            instances.add(instanceNode(instance));
        }
        Map<String, JsonNode> node = new LinkedHashMap<>();
        node.put("name", JsonNode.createStringNode(app));
        node.put("instance", JsonNode.createArrayNode(instances));
        return JsonNode.createObjectNode(node);
    }

    /**
     * Renders an instance like the Eureka server: ports use string "@enabled" values, lease timestamps are
     * populated and the server-side fields are added.
     */
    private JsonNode instanceNode(Map<String, JsonNode> registered) {
        long now = System.currentTimeMillis();
        Map<String, JsonNode> node = new LinkedHashMap<>(registered);
        node.put("overriddenStatus", JsonNode.createStringNode("UNKNOWN"));
        node.put("port", portNode(registered.get("port")));
        node.put("securePort", portNode(registered.get("securePort")));
        Map<String, JsonNode> leaseInfo = new LinkedHashMap<>();
        JsonNode registeredLease = registered.get("leaseInfo");
        leaseInfo.put("renewalIntervalInSecs", number(registeredLease, "renewalIntervalInSecs", 30));
        leaseInfo.put("durationInSecs", number(registeredLease, "durationInSecs", 90));
        leaseInfo.put("registrationTimestamp", JsonNode.createNumberNode(now));
        leaseInfo.put("lastRenewalTimestamp", JsonNode.createNumberNode(now));
        leaseInfo.put("evictionTimestamp", JsonNode.createNumberNode(0));
        leaseInfo.put("serviceUpTimestamp", JsonNode.createNumberNode(now));
        node.put("leaseInfo", JsonNode.createObjectNode(leaseInfo));
        node.put("isCoordinatingDiscoveryServer", JsonNode.createStringNode("false"));
        node.put("lastUpdatedTimestamp", JsonNode.createStringNode(Long.toString(now)));
        node.put("lastDirtyTimestamp", JsonNode.createStringNode(Long.toString(now)));
        node.put("actionType", JsonNode.createStringNode("ADDED"));
        return JsonNode.createObjectNode(node);
    }

    private static JsonNode portNode(JsonNode port) {
        Map<String, JsonNode> node = new LinkedHashMap<>();
        int value = port == null ? 0 : port.get("$").getIntValue();
        boolean enabled = port != null && Boolean.parseBoolean(port.get("@enabled").coerceStringValue());
        node.put("$", JsonNode.createNumberNode(value));
        node.put("@enabled", JsonNode.createStringNode(Boolean.toString(enabled)));
        return JsonNode.createObjectNode(node);
    }

    private static JsonNode number(JsonNode node, String name, int defaultValue) {
        JsonNode value = node == null ? null : node.get(name);
        return value != null && value.isNumber() ? value : JsonNode.createNumberNode(defaultValue);
    }

    /**
     * Validates a registration the way the Eureka server decodes it.
     *
     * @param appId The application id of the request path
     * @param root  The request body
     * @return The validation error or {@code null} if valid
     */
    static String validateRegistration(String appId, JsonNode root) {
        if (!root.isObject() || root.size() != 1 || root.get("instance") == null || !root.get("instance").isObject()) {
            return "Registration must be a JSON object with a single 'instance' member";
        }
        JsonNode instance = root.get("instance");
        for (String required : List.of("instanceId", "hostName", "app", "ipAddr", "status", "vipAddress")) {
            JsonNode value = instance.get(required);
            if (value == null || !value.isString() || value.getStringValue().isEmpty()) {
                return "Missing instance." + required;
            }
        }
        if (!appId.equalsIgnoreCase(instance.get("app").getStringValue())) {
            return "Mismatched app id: " + appId + " vs " + instance.get("app").getStringValue();
        }
        for (String portName : List.of("port", "securePort")) {
            JsonNode port = instance.get(portName);
            if (port == null) {
                continue;
            }
            if (!port.isObject() || port.get("$") == null || !port.get("$").isNumber() || port.get("@enabled") == null) {
                return "instance." + portName + " must be an object with '$' and '@enabled' members";
            }
        }
        JsonNode dataCenterInfo = instance.get("dataCenterInfo");
        if (dataCenterInfo == null || !dataCenterInfo.isObject()) {
            return "Missing instance.dataCenterInfo";
        }
        JsonNode type = dataCenterInfo.get("@class");
        if (type == null || !type.isString()) {
            return "Could not resolve subtype of DataCenterInfo: missing type id property '@class'";
        }
        if (!DEFAULT_DATA_CENTER_INFO_CLASS.equals(type.getStringValue()) && !AMAZON_INFO_CLASS.equals(type.getStringValue())) {
            return "Could not resolve type id '" + type.getStringValue() + "' as a subtype of DataCenterInfo";
        }
        JsonNode name = dataCenterInfo.get("name");
        if (name == null || !name.isString()) {
            return "Missing instance.dataCenterInfo.name";
        }
        JsonNode leaseInfo = instance.get("leaseInfo");
        if (leaseInfo != null) {
            if (!leaseInfo.isObject() || leaseInfo.get("leaseInfo") != null) {
                return "instance.leaseInfo must be an unwrapped object";
            }
            JsonNode interval = leaseInfo.get("renewalIntervalInSecs");
            if (interval != null && !interval.isNumber()) {
                return "instance.leaseInfo.renewalIntervalInSecs must be a number";
            }
        }
        JsonNode metadata = instance.get("metadata");
        if (metadata != null && !metadata.isObject()) {
            return "instance.metadata must be an object";
        }
        return null;
    }

    private static Map<String, JsonNode> toMap(JsonNode object) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : object.entries()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.getStringValue() : null;
    }

    private String json(Map<String, JsonNode> node) throws IOException {
        return jsonMapper.writeValueAsString(JsonNode.createObjectNode(node));
    }
}
