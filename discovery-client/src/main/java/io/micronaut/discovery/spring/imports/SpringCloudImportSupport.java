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
package io.micronaut.discovery.spring.imports;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.spring.config.SpringCloudClientConfiguration;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.discovery.spring.config.client.SpringCloudConfigClient;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import reactor.core.publisher.Flux;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
final class SpringCloudImportSupport {

    Map<String, Object> load(ApplicationContext context, String applicationName, String profiles, String label, boolean optional) {
        SpringCloudClientConfiguration configuration = context.getBean(SpringCloudClientConfiguration.class);
        SpringCloudConfigClient client = context.getBean(SpringCloudConfigClient.class);

        try {
            ConfigServerResponse response = Flux.from(read(client, configuration, applicationName, profiles, label)).blockFirst();
            if (response == null || response.getPropertySources() == null || response.getPropertySources().isEmpty()) {
                return Map.of();
            }
            return merge(response.getPropertySources());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (optional && cause instanceof HttpClientResponseException hcre && hcre.getStatus() == HttpStatus.NOT_FOUND) {
                return Map.of();
            }
            throw new ConfigurationException("Error reading distributed configuration from Spring Cloud: " + cause.getMessage(), cause);
        }
    }

    private org.reactivestreams.Publisher<ConfigServerResponse> read(SpringCloudConfigClient client,
                                                                     SpringCloudClientConfiguration configuration,
                                                                     String applicationName,
                                                                     String profiles,
                                                                     @Nullable String label) {
        Optional<String> authorization = getAuthorization(configuration);
        if (authorization.isEmpty()) {
            return label == null ? client.readValues(applicationName, profiles) : client.readValues(applicationName, profiles, label);
        }
        return label == null
            ? client.readValuesAuthorized(applicationName, profiles, authorization.get())
            : client.readValuesAuthorized(applicationName, profiles, label, authorization.get());
    }

    private Map<String, Object> merge(List<ConfigServerPropertySource> propertySources) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (int i = propertySources.size() - 1; i >= 0; i--) {
            merged.putAll(propertySources.get(i).getSource());
        }
        return merged;
    }

    private Optional<String> getAuthorization(SpringCloudClientConfiguration configuration) {
        Optional<String> username = configuration.getUsername();
        Optional<String> password = configuration.getPassword();
        if (username.isPresent() && password.isPresent()) {
            String basicAuth = username.get() + ':' + password.get();
            return Optional.of("Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8)));
        }
        return Optional.empty();
    }
}
