# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples of Micronaut Discovery Client under
`test-suite-docs-python/src/test/python/micronaut/discovery/docs` that are disabled, or that carry a workaround because the
direct port of the Java example does not compile or does not behave like the Java example yet (Python compiler gaps). It is
the bug-fixing task list for the Python compiler (`micronaut-inject-python` / `micronaut-context-python`); every row
references a `TODO(python)` comment in the sources.

The Python examples are compiled by every build and their tests run with `./gradlew pythonCheck -Ppython-ci`
(the "Python CI" GitHub workflow).

## Reconciliation

- Last generated active `@Disabled` count: 0.
- Last generated command: `rg -n "@Disabled\(" test-suite-docs-python/src/test/python`.
- Last full-suite command: `./gradlew :test-suite-docs-python:test -Ppython-ci`.
- Last full-suite result: build successful, 2 tests executed (1 test class), 0 skipped.

## Migration Rules

- The snippet classes live in `io.micronaut.discovery.docs` in every language (`project-base="test-suite-docs"` in the
  guide): a Python source package cannot be the imported Java package `micronaut.discovery` itself.
- A declarative client is an `ABC` decorated with `@Client(id="hello-world")` whose methods are `@abstractmethod`s with
  a `...` body; the `@Client(id=...)` qualifier of an injected `HttpClient` is the third member of the
  `Annotated[HttpClient, Inject, Client(id="hello-world")]` attribute annotation.
- The `hello-world` service is resolved without a Consul agent: a Java `@ContextConfigurer`
  (`io.micronaut.discovery.docsupport.HelloWorldServiceConfigurer`, `src/test/java` of the Python suite, outside the
  Python package tree) binds the embedded server to a free port and registers it as
  `micronaut.http.services.hello-world.url`. A configurer is service-loaded before the GraalPy runtime exists, so it
  cannot be a Python class.
- A Python test class is a `@MicronautTest` with `@Test` methods and plain `assert` statements; both tests call the
  `HelloController` of the embedded server through the discovered service, so an unresolved service id fails the test.

## Active `@Disabled` Tests

None.

## Workarounds in the Sources

None.

## `java.type` usages

None.
