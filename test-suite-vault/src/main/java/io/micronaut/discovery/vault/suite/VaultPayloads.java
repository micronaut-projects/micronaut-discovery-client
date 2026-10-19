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
package io.micronaut.discovery.vault.suite;

/**
 * JSON responses of the Vault KV secrets engines, in the format the Vault server sends
 * (see https://developer.hashicorp.com/vault/api-docs/secret/kv).
 */
public final class VaultPayloads {

    /**
     * The response of a KV version 1 read: the secrets are the {@code data} object.
     */
    public static final String KV_V1_SECRET = """
        {
          "request_id": "8d6d4bd1-cd4f-31a6-55aa-26b5b1fd3c87",
          "lease_id": "",
          "renewable": false,
          "lease_duration": 2764800,
          "data": {
            "greeting": "hello from kv v1",
            "number": 42,
            "enabled": true,
            "nested": {
              "value": "deep v1"
            }
          },
          "wrap_info": null,
          "warnings": null,
          "auth": null,
          "mount_type": "kv"
        }
        """;

    /**
     * The response of a KV version 2 read: the secrets are the {@code data.data} object.
     */
    public static final String KV_V2_SECRET = """
        {
          "request_id": "f1b6f6c2-5a7e-8d2c-0b8c-6c1e1a3e4f5a",
          "lease_id": "",
          "renewable": false,
          "lease_duration": 0,
          "data": {
            "data": {
              "greeting": "hello from kv v2",
              "number": 7,
              "enabled": true,
              "nested": {
                "value": "deep v2"
              }
            },
            "metadata": {
              "created_time": "2026-03-22T02:24:06.945319214Z",
              "custom_metadata": {
                "owner": "jdoe"
              },
              "deletion_time": "",
              "destroyed": false,
              "version": 2
            }
          },
          "wrap_info": null,
          "warnings": ["a warning"],
          "auth": null,
          "mount_type": "kv"
        }
        """;

    /**
     * The response of Vault when a secret does not exist.
     */
    public static final String NOT_FOUND = """
        {"errors":[]}
        """;

    /**
     * The response of Vault when the token is not valid.
     */
    public static final String PERMISSION_DENIED = """
        {"errors":["permission denied"]}
        """;

    private VaultPayloads() {
    }

    /**
     * Builds a KV version 2 response.
     *
     * @param secretsJson The JSON object of the secrets
     * @return The response
     */
    public static String kvV2(String secretsJson) {
        return """
            {
              "request_id": "0c6d4c0e-2a3b-4c5d-8e9f-0a1b2c3d4e5f",
              "lease_id": "",
              "renewable": false,
              "lease_duration": 0,
              "data": {
                "data": %s,
                "metadata": {
                  "created_time": "2026-03-22T02:24:06.945319214Z",
                  "custom_metadata": null,
                  "deletion_time": "",
                  "destroyed": false,
                  "version": 1
                }
              },
              "wrap_info": null,
              "warnings": null,
              "auth": null,
              "mount_type": "kv"
            }
            """.formatted(secretsJson);
    }

    /**
     * Builds a KV version 1 response.
     *
     * @param secretsJson The JSON object of the secrets
     * @return The response
     */
    public static String kvV1(String secretsJson) {
        return """
            {
              "request_id": "1d7e5d1f-3b4c-5d6e-9f0a-1b2c3d4e5f6a",
              "lease_id": "",
              "renewable": false,
              "lease_duration": 2764800,
              "data": %s,
              "wrap_info": null,
              "warnings": null,
              "auth": null,
              "mount_type": "kv"
            }
            """.formatted(secretsJson);
    }
}
