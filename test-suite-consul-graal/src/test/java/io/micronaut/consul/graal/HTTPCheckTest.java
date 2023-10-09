package io.micronaut.consul.graal;

import io.micronaut.discovery.consul.client.v1.HTTPCheck;
import io.micronaut.http.HttpMethod;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class HTTPCheckTest {

    @Inject
    JsonMapper jsonMapper;

    @Test
    void samplePayload() throws IOException {
        //given:
        HTTPCheck check = new HTTPCheck("Memory utilization", new URL("https://example.com"));
        check.id("mem");
        check.notes("Ensure we don't oversubscribe memory");
        check.deregisterCriticalServiceAfter("90m");
        check.setMethod(HttpMethod.POST);
        check.interval("10s");
        check.tlsSkipVerify(true);

        //when:
        String json = jsonMapper.writeValueAsString(check);

        //then:
        assertNotNull(json);
        assertTrue(json.contains("\"CheckID\":\"mem\""));
        assertTrue(json.contains("\"Name\":\"Memory utilization\""));
        assertTrue(json.contains("\"Notes\":\"Ensure we don't oversubscribe memory\""));
        assertTrue(json.contains("\"DeregisterCriticalServiceAfter\":\"90m\""));
        assertTrue(json.contains("\"HTTP\":\"https://example.com\""));
        assertTrue(json.contains("\"Method\":\"POST\""));
        assertTrue(json.contains("\"Interval\":\"10s\""));
        assertTrue(json.contains("\"TLSSkipVerify\":true"));
    }
}

