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

/**
 * Shared metadata keys used to bridge config import state into provider-specific refresh handling.
 */
@Internal
public final class RemoteConfigImportMetadata {

    public static final String CONSUL_IMPORT_METADATA_PREFIX = "micronaut.config.import.consul";
    public static final String CONSUL_WATCH_ENABLED = CONSUL_IMPORT_METADATA_PREFIX + ".watch-enabled";
    public static final String CONSUL_WATCH_PATH = CONSUL_IMPORT_METADATA_PREFIX + ".watch-path";
    public static final String CONSUL_WATCH_FORMAT = CONSUL_IMPORT_METADATA_PREFIX + ".watch-format";
    public static final String CONSUL_WATCH_PROPERTY_SOURCE = CONSUL_IMPORT_METADATA_PREFIX + ".watch-property-source";

    private RemoteConfigImportMetadata() {
    }
}
