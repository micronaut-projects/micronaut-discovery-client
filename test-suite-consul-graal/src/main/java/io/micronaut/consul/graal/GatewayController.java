/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.consul.graal;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

@Requires(property = "spec.name", value = "ConsulTest")
@Controller("/api")
public class GatewayController {

    private final HelloClient helloClient;

    public GatewayController(HelloClient helloClient) {
        this.helloClient = helloClient;
    }

    @Get("/hello/{name}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public String sayHi(String name) {
        return helloClient.sayHi(name);
    }
}
