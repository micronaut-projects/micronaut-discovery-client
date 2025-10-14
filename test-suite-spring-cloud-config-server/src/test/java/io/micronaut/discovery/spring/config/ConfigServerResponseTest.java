package io.micronaut.discovery.spring.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;

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


    @Test
    void jsonDeserialization(JsonMapper jsonMapper) throws IOException {
        String json = """
    {"name":"micronautguide","profiles":["spain"],"label":null,"version":"b071c1570f225343ac49823e67bdc85cba52ca1d","state":"","propertySources":[{"name":"https://github.com/sdelamo/spring-cloud-config-server-demo.git/micronautguide-spain.properties","source":{"vat.country":"Spain","vat.rate":"21"}},{"name":"https://github.com/sdelamo/spring-cloud-config-server-demo.git/micronautguide.properties","source":{"vat.country":"Switzerland","vat.rate":"7.7"}}]}""";
        ConfigServerResponse configServerResponse = jsonMapper.readValue(json, ConfigServerResponse.class);
        assertEquals(2, configServerResponse.getPropertySources().size());

    }
}
