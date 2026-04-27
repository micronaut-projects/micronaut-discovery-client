/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.discovery.imports;

import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emits deprecation warnings for legacy distributed configuration client usage.
 */
@Internal
public final class LegacyConfigurationClientDeprecationLogger {

    private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

    private LegacyConfigurationClientDeprecationLogger() {
    }

    public static void warn(Logger logger, String key, String message) {
        if (WARNED_KEYS.add(key)) {
            logger.warn(message);
        }
    }
}
