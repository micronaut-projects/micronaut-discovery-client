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

/**
 * JSON responses of the Spring Cloud Config Server, in the format the server sends
 * (see https://docs.spring.io/spring-cloud-config/reference/server/environment-repository.html).
 */
public final class SpringCloudConfigPayloads {

    /**
     * The environment of an application with two profiles. The property sources with the highest precedence are first.
     */
    public static final String ENVIRONMENT = """
        {
          "name": "myapp",
          "profiles": ["first", "second"],
          "label": "main",
          "version": "b071c1570f225343ac49823e67bdc85cba52ca1d",
          "state": null,
          "propertySources": [
            {
              "name": "https://example.com/config-repo.git/myapp-second.yml",
              "source": {
                "greeting": "hello from second",
                "list[0]": "a",
                "list[1]": "b"
              }
            },
            {
              "name": "https://example.com/config-repo.git/myapp-first.yml",
              "source": {
                "greeting": "hello from first",
                "number": 42,
                "enabled": true,
                "nested.value": "deep"
              }
            },
            {
              "name": "https://example.com/config-repo.git/application.yml",
              "source": {
                "greeting": "hello from application",
                "shared": "from application"
              }
            }
          ]
        }
        """;

    private SpringCloudConfigPayloads() {
    }
}
