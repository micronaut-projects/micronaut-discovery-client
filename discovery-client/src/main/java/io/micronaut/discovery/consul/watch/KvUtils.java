/*
 * Copyright 2017-2020 original authors
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

import java.util.Comparator;
import java.util.List;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.consul.client.v1.KeyValue;

/**
 * Utils class to compare {@link KeyValue}.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@Internal
final class KvUtils {

    /**
     * Private constructor.
     */
    private KvUtils() {
    }

    /**
     * Compare 2 {@link KeyValue} by key and value.
     *
     * @param left  1st {@link KeyValue} to compare
     * @param right 2d {@link KeyValue} to compare
     * @return {@code true} if they are equals
     */
    static boolean areEqual(final KeyValue left, final KeyValue right) {
        if (left == null && right == null) {
            return true;
        }

        if (left == null || right == null) {
            return false;
        }

        return left.getKey().equals(right.getKey()) && left.getValue().equals(right.getValue());
    }

    /**
     * Compare 2 list of {@link KeyValue}.
     *
     * @param left  1st list of {@link KeyValue} to compare
     * @param right 2d list of {@link KeyValue} to compare
     * @return {@code true} if the list are equals
     * @see #areEqual(KeyValue, KeyValue)
     */
    static boolean areEqual(final List<KeyValue> left, final List<KeyValue> right) {
        if (left == null && right == null) {
            return true;
        }

        if (left == null || right == null) {
            return false;
        }

        if (left.size() != right.size()) {
            return false;
        }

        left.sort(Comparator.comparing(KeyValue::getKey));
        right.sort(Comparator.comparing(KeyValue::getKey));

        for (int i = 0; i < left.size(); i++) {
            final var leftKV = left.get(i);
            final var rightKV = right.get(i);
            if (!areEqual(rightKV, leftKV)) {
                return false;
            }
        }

        return true;
    }
}
