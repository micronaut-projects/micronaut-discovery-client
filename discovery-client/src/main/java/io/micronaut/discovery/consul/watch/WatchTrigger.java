/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.consul.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;

/**
 * Start and stop Consul {@link Watcher} on {@link StartupEvent} & {@link ShutdownEvent}.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@Prototype
final class WatchTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(WatchTrigger.class);

    @EventListener
    void onStart(final StartupEvent event) {
        LOG.info("Starting Consul watcher");
        event.getSource().getBean(Watcher.class).start();
    }

    @EventListener
    void onShutdown(final ShutdownEvent event) {
        LOG.info("Shutting down Consul watcher");
        event.getSource().getBean(Watcher.class).stop();
    }
}
