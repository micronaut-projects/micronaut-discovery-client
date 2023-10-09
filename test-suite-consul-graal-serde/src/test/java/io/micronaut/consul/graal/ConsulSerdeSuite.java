package io.micronaut.consul.graal;

import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.consul.graal")
@SuiteDisplayName("Consul TCK Micronaut Serialization")
public class ConsulSerdeSuite {
}
