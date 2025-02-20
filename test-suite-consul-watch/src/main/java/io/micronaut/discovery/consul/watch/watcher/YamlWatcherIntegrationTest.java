package io.micronaut.discovery.consul.watch.watcher;

import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@Property(name = "consul.client.config.format", value = "yaml")
@MicronautTest(contextBuilder = YamlWatcherIntegrationTest.CustomContextBuilder.class)
class YamlWatcherIntegrationTest extends BaseWatcherIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(YamlWatcherIntegrationTest.class);

    public static class CustomContextBuilder extends DefaultApplicationContextBuilder {

        public CustomContextBuilder() throws Exception {
            LOG.info("Initializing consul...");
            doUpdateConsul("foo", "bar");
        }
    }

    @Language("YAML")
    private static final String APPLICATION_YAML = """
            my:
              key:
                to_be_updated: %s

            an.other.property: %s""";

    @Language("YAML")
    private static final String TEST_YAML = """
            something:
              else: test""";

    @Override
    protected void updateConsul(final String foo, final String bar) throws Exception {
        LOG.info("Updating consul...");
        doUpdateConsul(foo, bar);
    }

    private static void doUpdateConsul(final String foo, final String bar) throws Exception {
        consulKvPut(ROOT + "application", String.format(APPLICATION_YAML, foo, bar));
        consulKvPut(ROOT + "application,test", TEST_YAML);
    }

}


