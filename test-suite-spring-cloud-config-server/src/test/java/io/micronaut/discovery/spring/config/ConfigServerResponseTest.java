package io.micronaut.discovery.spring.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Property(name = "micronaut.config-client.enabled", value = StringUtils.TRUE)
@Property(name = "spring.cloud.config.enabled", value = StringUtils.TRUE)
@MicronautTest(startApplication = false)
class ConfigServerResponseTest {
    @Inject
    BeanContext beanContext;

    @Test
    void isAnnotatedWithSerdeable() {
        SerdeIntrospections serdeIntrospections = beanContext.getBean(SerdeIntrospections.class);
        Assertions.assertDoesNotThrow(() -> serdeIntrospections.getDeserializableIntrospection(Argument.of(ConfigServerResponse.class)));
        Assertions.assertDoesNotThrow(() -> serdeIntrospections.getSerializableIntrospection(Argument.of(ConfigServerResponse.class)));
    }

    @Test
    void isAnnotatedWithIntrospected() {
        assertDoesNotThrow(() -> BeanIntrospection.getIntrospection(ConfigServerResponse.class));
    }
}
