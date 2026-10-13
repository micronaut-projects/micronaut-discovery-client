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
package io.micronaut.discovery.spring.suite;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stub of the Spring Cloud Config Server environment endpoints.
 * <p>
 * It serves the raw JSON the server sends instead of the Micronaut model so that it checks the wire format. Like
 * the server, it returns the property sources with the highest precedence first, an empty environment for an
 * unknown application and 404 for an unknown label. The application {@value #SECURED_APPLICATION} requires
 * HTTP basic authentication with {@value #USERNAME} and {@value #PASSWORD}.
 */
@Requires(property = SpringCloudConfigStubServer.ENABLED)
@Controller
public class SpringCloudConfigStubServer {

    public static final String ENABLED = "spring-cloud-config.stub.enabled";
    public static final String SECURED_APPLICATION = "secured";
    public static final String USERNAME = "user";
    public static final String PASSWORD = "secret";
    public static final String LABEL = "main";

    private static final String REPOSITORY = "https://example.com/config-repo.git/";

    private static final Map<String, String> SOURCES = Map.of(
        "application", """
            {"greeting": "hello from application", "shared": "from application"}""",
        "myapp", """
            {"greeting": "hello from myapp", "number": 42, "enabled": true, "nested.value": "deep"}""",
        "myapp-first", """
            {"greeting": "hello from first", "first.only": "first"}""",
        "myapp-second", """
            {"greeting": "hello from second", "list[0]": "a", "list[1]": "b"}""",
        "secured", """
            {"secured.value": "from secured"}"""
    );

    private final List<String> requests = new CopyOnWriteArrayList<>();

    /**
     * @return The paths requested
     */
    public List<String> getRequests() {
        return requests;
    }

    /**
     * Reads the environment of an application.
     *
     * @param application   The application name
     * @param profiles      The comma separated profiles
     * @param authorization The authorization header
     * @return The environment
     */
    @Get(uri = "/{application}/{profiles}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<String> environment(String application, String profiles,
                                            @Nullable @Header("Authorization") String authorization) {
        return environment(application, profiles, null, authorization);
    }

    /**
     * Reads the environment of an application for a label.
     *
     * @param application   The application name
     * @param profiles      The comma separated profiles
     * @param label         The label
     * @param authorization The authorization header
     * @return The environment
     */
    @Get(uri = "/{application}/{profiles}/{label}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<String> environment(String application, String profiles, @Nullable String label,
                                            @Nullable @Header("Authorization") String authorization) {
        requests.add(application + "/" + profiles + (label == null ? "" : "/" + label));
        if (SECURED_APPLICATION.equals(application) && !basicAuthorization().equals(authorization)) {
            return HttpResponse.<String>status(HttpStatus.UNAUTHORIZED).body(error(401, "Unauthorized", application, profiles, label));
        }
        if (label != null && !LABEL.equals(label)) {
            return HttpResponse.<String>notFound().body(error(404, "Not Found", application, profiles, label));
        }
        String[] profileNames = profiles.split(",");
        List<String> propertySources = new ArrayList<>();
        for (int i = profileNames.length - 1; i >= 0; i--) {
            addPropertySource(propertySources, application + "-" + profileNames[i], label);
        }
        addPropertySource(propertySources, application, label);
        if (!propertySources.isEmpty()) {
            addPropertySource(propertySources, "application", label);
        }
        return HttpResponse.ok("""
            {"name": "%s", "profiles": [%s], "label": %s, "version": "b071c1570f225343ac49823e67bdc85cba52ca1d", "state": null, "propertySources": [%s]}"""
            .formatted(application, quote(profileNames), label == null ? "null" : '"' + label + '"', String.join(",", propertySources)));
    }

    private static void addPropertySource(List<String> propertySources, String name, @Nullable String label) {
        String source = SOURCES.get(name);
        if (source != null) {
            if (label != null) {
                source = source.substring(0, source.length() - 1) + ", \"config.label\": \"" + label + "\"}";
            }
            propertySources.add("""
                {"name": "%s%s.yml", "source": %s}""".formatted(REPOSITORY, name, source));
        }
    }

    private static String quote(String[] values) {
        List<String> quoted = new ArrayList<>();
        for (String value : values) {
            quoted.add('"' + value + '"');
        }
        return String.join(",", quoted);
    }

    private static String error(int status, String error, String application, String profiles, @Nullable String label) {
        return """
            {"timestamp": "2026-10-13T12:00:00.000+00:00", "status": %d, "error": "%s", "path": "/%s/%s%s"}"""
            .formatted(status, error, application, profiles, label == null ? "" : "/" + label);
    }

    private static String basicAuthorization() {
        return "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }
}
