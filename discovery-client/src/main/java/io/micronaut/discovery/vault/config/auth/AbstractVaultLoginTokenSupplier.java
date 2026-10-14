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
package io.micronaut.discovery.vault.config.auth;

import io.micronaut.context.exceptions.ConfigurationException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for token suppliers that log in to a Vault auth method with {@code POST /v1/auth/{path}/login}.
 *
 * <p>The client token returned by the first successful login is cached and reused, and concurrent callers share a
 * login in progress. Leases are not renewed: the token is intended for reading configuration at startup.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
public abstract class AbstractVaultLoginTokenSupplier implements VaultTokenSupplier {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVaultLoginTokenSupplier.class);

    private final VaultAuthHttpClient authClient;
    private final AtomicReference<@Nullable Mono<String>> pendingLogin = new AtomicReference<>();
    private volatile @Nullable String token;

    /**
     * @param authClient The client used to call the Vault login endpoint
     */
    protected AbstractVaultLoginTokenSupplier(VaultAuthHttpClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public final Publisher<String> getToken() {
        String cached = token;
        if (cached != null) {
            return Mono.just(cached);
        }
        return Mono.defer(() -> {
            String current = token;
            if (current != null) {
                return Mono.just(current);
            }
            // callers share a login in progress: a second login could consume single-use credentials such as an
            // AppRole secret ID. The pending login is cleared once it ends, so a failed login is retried.
            Mono<String> login = Mono.defer(this::login)
                .doOnNext(t -> token = t)
                .doFinally(signal -> pendingLogin.set(null))
                .cache();
            Mono<String> pending = pendingLogin.compareAndExchange(null, login);
            return pending != null ? pending : login;
        });
    }

    /**
     * @return The name of the auth method, used in log and error messages
     */
    protected abstract String getMethodName();

    /**
     * @return The path the auth method is mounted at, without the {@code auth/} prefix
     */
    protected abstract String getMountPath();

    /**
     * Build the login request body. Called on every login attempt.
     *
     * @return The login request body
     * @throws ConfigurationException If the authentication method is not configured correctly
     */
    protected abstract Map<String, Object> getLoginBody();

    private Mono<String> login() {
        String mountPath = normalizeMountPath(getMountPath());
        String endpoint = "auth/" + mountPath + "/login";
        Map<String, Object> body = getLoginBody();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Logging in to Vault using {} authentication at [{}]", getMethodName(), endpoint);
        }
        return Mono.from(authClient.login(mountPath, body))
            .onErrorMap(t -> new ConfigurationException("Vault " + getMethodName() + " login at [" + endpoint + "] failed: " + t.getMessage(), t))
            .switchIfEmpty(Mono.error(() -> missingToken(endpoint)))
            .map(response -> {
                VaultLoginResponse.Auth auth = response.auth();
                String clientToken = auth != null ? auth.clientToken() : null;
                if (clientToken == null || clientToken.isBlank()) {
                    throw missingToken(endpoint);
                }
                return clientToken;
            });
    }

    private ConfigurationException missingToken(String endpoint) {
        return new ConfigurationException("Vault " + getMethodName() + " login at [" + endpoint + "] did not return a client token");
    }

    private static String normalizeMountPath(String path) {
        String normalized = path.strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("auth/")) {
            normalized = normalized.substring("auth/".length());
        }
        return normalized;
    }
}
