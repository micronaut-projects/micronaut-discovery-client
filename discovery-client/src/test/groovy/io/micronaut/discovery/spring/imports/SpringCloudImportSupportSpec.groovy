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
package io.micronaut.discovery.spring.imports

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.discovery.spring.config.SpringCloudClientConfiguration
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource
import io.micronaut.discovery.spring.config.client.ConfigServerResponse
import io.micronaut.discovery.spring.config.client.SpringCloudConfigClient
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.micronaut.http.client.exceptions.HttpClientResponseException
import reactor.core.publisher.Flux
import spock.lang.Specification

class SpringCloudImportSupportSpec extends Specification {

    private final SpringCloudImportSupport support = new SpringCloudImportSupport()
    private final ApplicationContext context = Mock()
    private final SpringCloudClientConfiguration configuration = Mock()
    private final SpringCloudConfigClient client = Mock()

    void 'load returns empty map for optional not found'() {
        given:
        context.getBean(SpringCloudClientConfiguration) >> configuration
        context.getBean(SpringCloudConfigClient) >> client
        configuration.getUsername() >> Optional.empty()
        configuration.getPassword() >> Optional.empty()
        client.readValues('myapp', 'default') >> Flux.error(new HttpClientResponseException('missing', new SimpleHttpResponseFactory().status(HttpStatus.NOT_FOUND)))

        expect:
        support.load(context, 'myapp', 'default', null, true).isEmpty()
    }

    void 'load merges property sources from lowest to highest priority'() {
        given:
        context.getBean(SpringCloudClientConfiguration) >> configuration
        context.getBean(SpringCloudConfigClient) >> client
        configuration.getUsername() >> Optional.empty()
        configuration.getPassword() >> Optional.empty()
        client.readValues('myapp', 'default') >> Flux.just(response([
            propertySource([shared: 'base', baseOnly: 'one']),
            propertySource([shared: 'override', highOnly: 'two'])
        ]))

        when:
        Map<String, Object> result = support.load(context, 'myapp', 'default', null, false)

        then:
        result == [shared: 'base', highOnly: 'two', baseOnly: 'one']
    }

    void 'load throws configuration exception for non optional not found'() {
        given:
        context.getBean(SpringCloudClientConfiguration) >> configuration
        context.getBean(SpringCloudConfigClient) >> client
        configuration.getUsername() >> Optional.empty()
        configuration.getPassword() >> Optional.empty()
        client.readValues('myapp', 'default') >> Flux.error(new HttpClientResponseException('missing', new SimpleHttpResponseFactory().status(HttpStatus.NOT_FOUND)))

        when:
        support.load(context, 'myapp', 'default', null, false)

        then:
        ConfigurationException e = thrown()
        e.message.contains('Error reading distributed configuration from Spring Cloud: missing')
    }

    private ConfigServerResponse response(List<ConfigServerPropertySource> propertySources) {
        Stub(ConfigServerResponse) {
            getPropertySources() >> propertySources
        }
    }

    private ConfigServerPropertySource propertySource(Map<String, Object> source) {
        Stub(ConfigServerPropertySource) {
            getSource() >> source
        }
    }
}
