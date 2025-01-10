package io.micronaut.discovery.spring.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "micronaut.config-client.enabled", value = StringUtils.TRUE)
@Property(name = "spring.cloud.config.enabled", value = StringUtils.TRUE)
@MicronautTest(startApplication = false)
class ConfigServerPropertySourceTest {
    @Inject
    BeanContext beanContext;
    private static final Class<ConfigServerPropertySource> CLAZZ = ConfigServerPropertySource.class;
    private static final Argument<ConfigServerPropertySource> ARGUMENT_CLAZZ = Argument.of(CLAZZ);

    @Test
    void isAnnotatedWithSerdeable() {
        SerdeIntrospections serdeIntrospections = beanContext.getBean(SerdeIntrospections.class);
        Assertions.assertDoesNotThrow(() -> serdeIntrospections.getDeserializableIntrospection(ARGUMENT_CLAZZ));
        Assertions.assertDoesNotThrow(() -> serdeIntrospections.getSerializableIntrospection(ARGUMENT_CLAZZ));
    }

    @Test
    void isAnnotatedWithIntrospected() {
        assertDoesNotThrow(() -> BeanIntrospection.getIntrospection(CLAZZ));
    }
}
