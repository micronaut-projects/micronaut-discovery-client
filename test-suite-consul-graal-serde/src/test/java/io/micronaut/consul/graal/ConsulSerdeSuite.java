package io.micronaut.consul.graal;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@ExcludeClassNamePatterns("io.micronaut.consul.graal.ConsulTest")
@SelectPackages("io.micronaut.consul.graal")
@SuiteDisplayName("Consul TCK Micronaut Serialization")
class ConsulSerdeSuite {
}
