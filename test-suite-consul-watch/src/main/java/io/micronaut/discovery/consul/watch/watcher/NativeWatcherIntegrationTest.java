package io.micronaut.discovery.consul.watch.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@Property(name = "consul.client.config.format", value = "native")
@MicronautTest(contextBuilder = NativeWatcherIntegrationTest.CustomContextBuilder.class)
class NativeWatcherIntegrationTest extends BaseWatcherIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(NativeWatcherIntegrationTest.class);

    public static class CustomContextBuilder extends DefaultApplicationContextBuilder {

        public CustomContextBuilder() throws Exception {
            LOG.info("Initializing consul...");
            doUpdateConsul("foo", "bar");
        }
    }

    private static final String APPLICATION_PROPERTY_FOO = "my.key.to_be_updated";
    private static final String APPLICATION_PROPERTY_BAR = "an.other.property";

    @Override
    protected void updateConsul(final String foo, final String bar) throws Exception {
        LOG.info("Updating consul...");
        doUpdateConsul(foo, bar);
    }

    private static void doUpdateConsul(final String foo, final String bar) throws Exception {
        consulKvPut(ROOT + "application/" + APPLICATION_PROPERTY_FOO, foo);
        consulKvPut(ROOT + "application/" + APPLICATION_PROPERTY_BAR, bar);
        consulKvPut(ROOT + "application,test/something.else", "test");
    }

}


