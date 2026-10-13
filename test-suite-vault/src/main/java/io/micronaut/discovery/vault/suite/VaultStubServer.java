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
package io.micronaut.discovery.vault.suite;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stub of the Vault HTTP API for the KV secrets engines.
 * <p>
 * It serves the raw JSON Vault sends instead of the Micronaut Vault model so that it checks the wire format. The KV
 * version 2 engine is mounted at {@value #KV_V2_ENGINE} and the KV version 1 engine at {@value #KV_V1_ENGINE}.
 */
@Requires(property = VaultStubServer.ENABLED)
@Controller("/v1")
public class VaultStubServer {

    public static final String ENABLED = "vault.stub.enabled";
    public static final String TOKEN = "suite-token";
    public static final String KV_V2_ENGINE = "secret";
    public static final String KV_V1_ENGINE = "kv";

    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    public VaultStubServer() {
        secrets.put(KV_V2_ENGINE + "/data/myapp", VaultPayloads.KV_V2_SECRET);
        secrets.put(KV_V2_ENGINE + "/data/application", VaultPayloads.kvV2("""
            {"greeting": "hello from application", "shared": "from application"}"""));
        secrets.put(KV_V2_ENGINE + "/data/myapp/vaultenv", VaultPayloads.kvV2("""
            {"greeting": "hello from myapp vaultenv"}"""));
        secrets.put(KV_V2_ENGINE + "/data/config/prefixed", VaultPayloads.kvV2("""
            {"prefixed": "from prefix"}"""));
        secrets.put(KV_V1_ENGINE + "/myapp", VaultPayloads.KV_V1_SECRET);
        secrets.put(KV_V1_ENGINE + "/application", VaultPayloads.kvV1("""
            {"greeting": "hello from v1 application", "shared": "from v1 application"}"""));
    }

    /**
     * @return The paths read, relative to {@code /v1}
     */
    public List<String> getRequests() {
        return requests;
    }

    /**
     * Reads a secret.
     *
     * @param path  The secret path, including the secrets engine mount
     * @param token The Vault token
     * @return The secret
     */
    @Get(uri = "/{path:.*}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<String> read(String path, @Nullable @Header("X-Vault-Token") String token) {
        requests.add(path);
        if (!TOKEN.equals(token)) {
            return HttpResponse.<String>status(HttpStatus.FORBIDDEN).body(VaultPayloads.PERMISSION_DENIED);
        }
        String secret = secrets.get(path);
        if (secret == null) {
            return HttpResponse.<String>notFound().body(VaultPayloads.NOT_FOUND);
        }
        return HttpResponse.ok(secret);
    }
}
