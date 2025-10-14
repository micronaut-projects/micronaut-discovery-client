/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.consul.watch;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.consul.ConsulConfiguration;

/**
 * Configuration for Consul {@link Watcher}.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@ConfigurationProperties(WatchConfiguration.PREFIX)
public class WatchConfiguration implements Toggleable {

    /**
     * The prefix to use for Consul's watcher settings.
     */
    public static final String PREFIX = ConsulConfiguration.PREFIX + ".watch";

    /**
     * The default enable value.
     */
    public static final boolean DEFAULT_ENABLED = false;

    private boolean enabled = DEFAULT_ENABLED;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether Configuration watching is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if it is enabled
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
