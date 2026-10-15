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
package io.micronaut.discovery.consul.client.v1;

import org.jspecify.annotations.Nullable;

/**
 * The consistency mode of a Consul read. See https://developer.hashicorp.com/consul/api-docs/features/consistency.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public enum ConsistencyMode {

    /**
     * The Consul default: strongly consistent in almost all cases, without the {@code consistent} or {@code stale} query flag.
     */
    DEFAULT,

    /**
     * Strongly consistent reads, sent with the {@code consistent} query flag.
     */
    CONSISTENT,

    /**
     * Reads that any server may answer, possibly with stale values, sent with the {@code stale} query flag.
     */
    STALE;

    /**
     * @param mode The mode, may be null
     * @return {@link Boolean#TRUE} when the {@code consistent} query flag must be sent, otherwise {@code null}
     */
    static @Nullable Boolean consistentFlag(@Nullable ConsistencyMode mode) {
        return mode == CONSISTENT ? Boolean.TRUE : null;
    }

    /**
     * @param mode The mode, may be null
     * @return {@link Boolean#TRUE} when the {@code stale} query flag must be sent, otherwise {@code null}
     */
    static @Nullable Boolean staleFlag(@Nullable ConsistencyMode mode) {
        return mode == STALE ? Boolean.TRUE : null;
    }
}
