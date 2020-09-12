# Micronaut Discovery Client

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut/micronaut-discovery-client.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut%22%20AND%20a:%22micronaut-discovery-client%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-discovery-client/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-discovery-client/actions)

This module integrates Micronaut with Service Discovery and Distributed Configuration systems.

The implementation currently includes Service Discovery support for:

* [HashiCorp Consul](https://www.consul.io/)
* [Eureka](https://github.com/Netflix/eureka)

As well as [Distributed Configuration](https://docs.micronaut.io/latest/guide/index.html#distributedConfiguration) support for:

* [HashiCorp Consul](https://www.consul.io/)
* [HashiCorp Vault](https://www.vaultproject.io/)
* [Oracle Cloud Vault](https://docs.cloud.oracle.com/en-us/iaas/Content/KeyManagement/Concepts/keyoverview.htm)
* [Spring Cloud Config Server](https://cloud.spring.io/spring-cloud-config/reference/html/#_spring_cloud_config_server)

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-discovery-client/latest/guide/) for more information. 

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-discovery-client/snapshot/guide/) for the current development docs.

## Snapshots and Releases

Snaphots are automatically published to [JFrog OSS](https://oss.jfrog.org/artifactory/oss-snapshot-local/) using [Github Actions](https://github.com/micronaut-projects/micronaut-discovery-client/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to JCenter and Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-discovery-client/actions).

Releases are completely automated. To perform a release use the following steps:

- [Publish the draft release](https://github.com/micronaut-projects/micronaut-discovery-client/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
- [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-discovery-client/actions?query=workflow%3ARelease) to check it passed successfully.
- Celebrate!
