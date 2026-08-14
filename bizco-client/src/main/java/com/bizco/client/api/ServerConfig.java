package com.bizco.client.api;

import java.net.URI;

/**
 * Resolves the Bizco server base URL shared by every API client, so the address is defined
 * once instead of duplicated per client. Resolution order: the {@code bizco.server.url} system
 * property (explicit override, e.g. {@code -Dbizco.server.url=...}), then the
 * {@code BIZCO_SERVER_URL} environment variable, then the local-dev default.
 */
public final class ServerConfig {

    private static final String DEFAULT_SERVER_URL = "http://localhost:9090";

    private ServerConfig() {
    }

    public static URI serverUrl() {
        final String property = System.getProperty("bizco.server.url");
        if (property != null && !property.isBlank()) {
            return URI.create(property);
        }
        final String env = System.getenv("BIZCO_SERVER_URL");
        if (env != null && !env.isBlank()) {
            return URI.create(env);
        }
        return URI.create(DEFAULT_SERVER_URL);
    }
}
