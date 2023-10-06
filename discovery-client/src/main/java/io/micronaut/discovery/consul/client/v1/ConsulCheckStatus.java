package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ConsulCheckStatus {

    PASSING,
    WARNING,
    CRITICAL;

    @JsonValue
    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
