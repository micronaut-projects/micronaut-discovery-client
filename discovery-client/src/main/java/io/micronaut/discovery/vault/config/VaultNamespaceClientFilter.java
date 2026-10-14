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
package io.micronaut.discovery.vault.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

/**
 * Sends the configured Vault Enterprise namespace as the {@code X-Vault-Namespace} header on requests made by
 * clients annotated with {@link VaultClientFilterMatcher}. It matches every path, because the Vault URI may include a
 * context path, for example behind a reverse proxy.
 *
 * <p>Implemented as an {@link HttpClientFilter} bean rather than an annotated {@code @ClientFilter} so that it is
 * also applied in the importer child context, which does not process executable methods at startup.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@VaultClientFilterMatcher
@Filter(patterns = "/**")
@BootstrapContextCompatible
@Requires(property = VaultClientConfiguration.PREFIX + ".namespace")
@Internal
public final class VaultNamespaceClientFilter implements HttpClientFilter {

    /**
     * The Vault namespace header.
     */
    public static final String NAMESPACE_HEADER = "X-Vault-Namespace";

    private final VaultClientConfiguration configuration;

    /**
     * @param configuration The Vault client configuration
     */
    VaultNamespaceClientFilter(VaultClientConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        String namespace = configuration.getNamespace();
        if (StringUtils.isNotEmpty(namespace)) {
            request.header(NAMESPACE_HEADER, namespace);
        }
        return chain.proceed(request);
    }
}
