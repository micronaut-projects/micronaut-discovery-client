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
package io.micronaut.discovery.vault;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stub of the Vault login endpoints and a KV v2 engine that only accepts tokens issued by those endpoints.
 */
@Controller
@Requires(property = MockingVaultAuthController.ENABLED)
public class MockingVaultAuthController {

    public static final String ENABLED = "enable.mock.vault-auth";
    public static final String ENGINE = "authsecret";
    public static final String KUBERNETES_ROLE = "my-role";
    public static final String KUBERNETES_JWT = "k8s-service-account-jwt";
    public static final String KUBERNETES_TOKEN = "k8s-client-token";
    public static final String ROLE_ID = "my-role-id";
    public static final String SECRET_ID = "my-secret-id";
    public static final String APPROLE_TOKEN = "approle-client-token";
    public static final String STATIC_TOKEN = "static-token";
    public static final String CUSTOM_TOKEN = "custom-token";
    public static final String NAMESPACE_HEADER = "X-Vault-Namespace";
    public static final String NO_NAMESPACE = "<none>";

    public final List<String> namespaces = new CopyOnWriteArrayList<>();
    public final AtomicInteger logins = new AtomicInteger();

    @Post("/v1/auth/{+mount}/login")
    public HttpResponse<?> login(String mount, @Body Map<String, Object> body, HttpRequest<?> request) {
        recordNamespace(request);
        logins.incrementAndGet();
        if ((mount.equals("kubernetes") || mount.equals("k8s/cluster-a"))
            && KUBERNETES_ROLE.equals(body.get("role"))
            && KUBERNETES_JWT.equals(body.get("jwt"))) {
            return HttpResponse.ok(loginResponse(KUBERNETES_TOKEN));
        }
        if ((mount.equals("approle") || mount.equals("custom-approle"))
            && ROLE_ID.equals(body.get("role_id"))
            && SECRET_ID.equals(body.get("secret_id"))) {
            return HttpResponse.ok(loginResponse(APPROLE_TOKEN));
        }
        return HttpResponse.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", List.of("invalid credentials")));
    }

    @Get("/v1/" + ENGINE + "/data/{vaultKey:.*}")
    public HttpResponse<?> read(String vaultKey, HttpRequest<?> request) {
        recordNamespace(request);
        String token = request.getHeaders().get("X-Vault-Token");
        if (token == null || !List.of(KUBERNETES_TOKEN, APPROLE_TOKEN, STATIC_TOKEN, CUSTOM_TOKEN).contains(token)) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(Map.of("errors", List.of("permission denied")));
        }
        if (!vaultKey.equals("application") && !vaultKey.equals("myapp")) {
            return HttpResponse.notFound(Map.of("errors", List.of()));
        }
        return HttpResponse.ok(Map.of(
            "data", Map.of(
                "data", Map.of("auth-secret", "loaded-with-" + token, vaultKey + "-secret", "present"),
                "metadata", Map.of()
            ),
            "lease_duration", 0,
            "renewable", false
        ));
    }

    private void recordNamespace(HttpRequest<?> request) {
        String namespace = request.getHeaders().get(NAMESPACE_HEADER);
        namespaces.add(namespace == null ? NO_NAMESPACE : namespace);
    }

    private static Map<String, Object> loginResponse(String token) {
        return Map.of(
            "request_id", "req",
            "lease_id", "",
            "renewable", false,
            "lease_duration", 0,
            "auth", Map.of(
                "client_token", token,
                "accessor", "accessor",
                "policies", List.of("default"),
                "lease_duration", 3600,
                "renewable", true
            )
        );
    }
}
