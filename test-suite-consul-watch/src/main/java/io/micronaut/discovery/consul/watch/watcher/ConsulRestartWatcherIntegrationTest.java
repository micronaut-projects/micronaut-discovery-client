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
package io.micronaut.discovery.consul.watch.watcher;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.discovery.consul.watch.Watcher;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;

/**
 * Checks that the watcher recovers when Consul is unavailable for longer than a few retries.
 */
@Property(name = "consul.client.config.format", value = "yaml")
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsulRestartWatcherIntegrationTest implements TestPropertyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulRestartWatcherIntegrationTest.class);
    private static final String KV_PATH = "restart/application";
    private static final String RESTART_PATH = "restart";

    private final int hostPort = findFreePort();
    private ConsulContainer consul;

    @Inject
    Environment environment;

    @Inject
    Watcher watcher;

    @Inject
    RestartEventListener eventListener;

    @Override
    public @NonNull Map<String, String> getProperties() {
        if (consul == null) {
            consul = startConsul(hostPort);
        }
        return Map.of(
            "consul.client.host", consul.getHost(),
            "consul.client.port", String.valueOf(hostPort),
            "consul.client.default-zone", consul.getHost() + ":" + hostPort,
            "micronaut.application.name", "consul-restart",
            "micronaut.config-client.enabled", "true",
            "consul.client.config.path", RESTART_PATH,
            "consul.client.watch.enabled", "true",
            "consul.client.watch.retry-delay-ms", "100",
            "consul.client.watch.max-retry-delay", "500ms",
            "consul.client.blocking-queries.max-wait-duration", "5s"
        );
    }

    @AfterAll
    void stopConsul() {
        if (consul != null) {
            consul.stop();
        }
    }

    @Test
    void should_resume_watching_after_consul_restart() throws Exception {
        Awaitility.await()
            .atMost(10, SECONDS)
            .untilAsserted(() -> {
                assertThat(environment.getProperty("restart.value", String.class)).hasValue("before");
                assertThat(watcher.isWatching()).isTrue();
            });

        // Consul is unavailable for longer than the 4.7.x default of 3 retries
        LOG.info("Stopping Consul");
        consul.getDockerClient().stopContainerCmd(consul.getContainerId()).exec();
        Thread.sleep(Duration.ofSeconds(4));

        LOG.info("Restarting Consul");
        consul.getDockerClient().startContainerCmd(consul.getContainerId()).exec();
        waitForConsul();
        // the dev agent keeps the KV store in memory, so it is empty after the restart
        putValue("after");

        Awaitility.await()
            .atMost(20, SECONDS)
            .untilAsserted(() -> {
                assertThat(eventListener.events.get()).isPositive();
                assertThat(environment.getProperty("restart.value", String.class)).hasValue("after");
            });
    }

    private static ConsulContainer startConsul(final int hostPort) {
        final var container = new ConsulContainer(DockerImageName.parse("consul:1.9.0"));
        // a fixed host port, so that the application reconnects to the same address once Consul is restarted
        container.setPortBindings(List.of(hostPort + ":8500"));
        container.waitingFor(new HttpWaitStrategy()
            .forPort(8500)
            .forStatusCode(200)
            .forPath("/v1/status/leader"));
        container.start();
        putValue(container, "before");
        return container;
    }

    private void waitForConsul() {
        Awaitility.await()
            .atMost(30, SECONDS)
            .ignoreExceptions()
            .until(() -> consul.execInContainer("consul", "kv", "get", "-recurse").getExitCode() == 0);
    }

    private void putValue(final String value) throws Exception {
        final var result = consul.execInContainer("consul", "kv", "put", KV_PATH, "restart.value: " + value);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static void putValue(final ConsulContainer container, final String value) {
        try {
            container.execInContainer("consul", "kv", "put", KV_PATH, "restart.value: " + value);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int findFreePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Singleton
    @Requires(property = "consul.client.config.path", value = RESTART_PATH)
    static class RestartEventListener implements ApplicationEventListener<RefreshEvent> {

        private final AtomicInteger events = new AtomicInteger();

        @Override
        public void onApplicationEvent(final RefreshEvent event) {
            events.incrementAndGet();
        }
    }
}
