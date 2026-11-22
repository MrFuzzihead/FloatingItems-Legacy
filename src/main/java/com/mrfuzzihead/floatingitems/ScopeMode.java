package com.mrfuzzihead.floatingitems;

import java.util.Locale;

/**
 * Controls how a scope list (dimensions, biomes or mod IDs) is applied.
 */
public enum ScopeMode {

    /** No restriction. */
    ALL("all"),

    /** Only listed scopes are allowed. */
    WHITELIST("whitelist"),

    /** Listed scopes are denied. */
    BLACKLIST("blacklist");

    private final String id;

    ScopeMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    static ScopeMode parse(String value) {
        if (value == null) {
            return ALL;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_');
        for (ScopeMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return ALL;
    }
}
