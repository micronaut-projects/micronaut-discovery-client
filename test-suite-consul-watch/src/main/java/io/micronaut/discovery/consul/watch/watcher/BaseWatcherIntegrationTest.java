package io.micronaut.discovery.consul.watch.watcher;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.discovery.consul.watch.Watcher;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.test.support.TestPropertyProvider;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseWatcherIntegrationTest implements TestPropertyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BaseWatcherIntegrationTest.class);

    protected static final String ROOT = "test/";

    @Inject
    private BeanContext beanContext;
    @Inject
    private TestEventListener testEventListener;
    @Inject
    private RefreshableBean refreshableBean;
    @Inject
    private RefreshableProperty refreshableProperty;
    @Inject
    private RefreshableInnerProperty refreshableInnerProperty;

    @Container
    protected static final ConsulContainer CONSUL_CONTAINER = new ConsulContainer("hashicorp/consul:1.18.1");

    @Override
    public @NonNull Map<String, String> getProperties() {
        final var consulHost = CONSUL_CONTAINER.getHost();
        final var consulPort = CONSUL_CONTAINER.getMappedPort(8500);
        return Map.of(
            "micronaut.application.name", "consul-watcher",
            "micronaut.config-client.enabled", "true",
            "consul.client.watch.enabled", "true",
            "consul.client.blocking-queries.max-wait-duration", "5s",
            "consul.client.config.path", "test",
            "consul.client.host", consulHost,
            "consul.client.port", String.valueOf(consulPort)
        );
    }

    protected static void consulKvPut(final String key, final String data) throws Exception {
        CONSUL_CONTAINER.execInContainer("consul", "kv", "put", key, data);
    }

    protected abstract void updateConsul(final String foo, final String bar) throws Exception;

    @AfterEach
    void cleanUp() {
        CONSUL_CONTAINER.withConsulCommand("kv delete " + ROOT);
    }

    @Test
    void should_refresh_only_updated_property() throws Exception {
        // given
        Awaitility.with()
            .await()
            .untilAsserted(() -> assertSoftly(softAssertions -> {
                softAssertions.assertThat(refreshableProperty.getToBeUpdated()).isEqualTo("foo");
                softAssertions.assertThat(refreshableInnerProperty.getKey().getToBeUpdated()).isEqualTo("foo");
                softAssertions.assertThat(refreshableBean.keyToBeUpdated).isEqualTo("foo");
                softAssertions.assertThat(refreshableBean.otherKey).isEqualTo("bar");

                final var watcher = beanContext.getBean(Watcher.class);
                softAssertions.assertThat(watcher.isWatching()).isTrue();
            }));

        // when
        final var randomFoo = RandomStringUtils.secure().nextAlphanumeric(10);
        updateConsul(randomFoo, "bar");

        // then
        Awaitility.with()
            .await()
            .atMost(2, SECONDS)
            .untilAsserted(() -> assertSoftly(softAssertions -> {
                softAssertions.assertThat(testEventListener.isEventReceived).as("isEventReceived").isTrue();
                // refreshableProperty return the new value
                softAssertions.assertThat(refreshableProperty.getToBeUpdated()).as("refreshableProperty").isEqualTo(randomFoo);
                softAssertions.assertThat(refreshableInnerProperty.getKey().getToBeUpdated()).as("refreshableInnerProperty").isEqualTo(randomFoo);
                // while current refreshableBean is not refreshed
                softAssertions.assertThat(refreshableBean.keyToBeUpdated).as("refreshedBean").isEqualTo("foo");
                softAssertions.assertThat(refreshableBean.otherKey).as("refreshedBean").isEqualTo("bar");
                // but if we re-retrieve from the context, it has been recreated
                final var refreshedBean = beanContext.getBean(RefreshableBean.class);
                softAssertions.assertThat(refreshedBean.keyToBeUpdated).as("refreshedBean").isEqualTo(randomFoo);
                softAssertions.assertThat(refreshedBean.otherKey).as("refreshedBean").isEqualTo("bar");
            }));
    }

    @Singleton
    public static class TestEventListener implements ApplicationEventListener<RefreshEvent> {

        private final AtomicBoolean isEventReceived = new AtomicBoolean(false);

        @Override
        public void onApplicationEvent(final RefreshEvent event) {
            LOG.info("Received refresh event: {}", event);
            isEventReceived.set(true);
        }
    }

    @ConfigurationProperties("my.key")
    public interface RefreshableProperty {

        @NotBlank
        String getToBeUpdated();
    }

    @ConfigurationProperties("my")
    public interface RefreshableInnerProperty {

        @NotNull
        InnerRefreshableProperty getKey();

        @ConfigurationProperties("key")
        interface InnerRefreshableProperty {

            @NotBlank
            String getToBeUpdated();
        }
    }

    @Refreshable
    public static class RefreshableBean {

        @Value("${my.key.to_be_updated}")
        public String keyToBeUpdated;

        @Value("${an.other.property}")
        public String otherKey;

    }
}
