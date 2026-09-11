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

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Converts between the address strings Consul sends and {@link InetAddress}.
 *
 * <p>Consul addresses may be host names or IP literals. The models keep them as strings, so the
 * JSON library never has to turn them into an {@link InetAddress}.</p>
 *
 * @since 5.2.0
 */
final class ConsulAddresses {

    private ConsulAddresses() {
    }

    /**
     * Returns the host name of the address, or its IP literal when it has no host name. This is
     * the value Jackson writes for an {@link InetAddress}, and it does not trigger a reverse lookup.
     *
     * @param address The address
     * @return The host string, or {@code null} for a {@code null} address
     */
    static @Nullable String toHostString(@Nullable InetAddress address) {
        if (address == null) {
            return null;
        }
        String value = address.toString().trim();
        int slash = value.indexOf('/');
        if (slash > 0) {
            return value.substring(0, slash);
        }
        return address.getHostAddress();
    }

    /**
     * Resolves a Consul address, which may need a DNS lookup when it is a host name.
     *
     * @param hostString The host name or IP literal
     * @return The resolved address, or {@code null} for a {@code null} or empty value
     * @throws UncheckedIOException if the host name cannot be resolved
     */
    static @Nullable InetAddress resolve(@Nullable String hostString) {
        if (hostString == null || hostString.isEmpty()) {
            return null;
        }
        try {
            return InetAddress.getByName(hostString);
        } catch (UnknownHostException e) {
            throw new UncheckedIOException("Failed to resolve Consul address [" + hostString + "]: " + e.getMessage(), e);
        }
    }
}
