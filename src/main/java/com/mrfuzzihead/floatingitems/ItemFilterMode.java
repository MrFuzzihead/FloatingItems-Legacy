package com.mrfuzzihead.floatingitems;

import java.util.Locale;

/**
 * Controls how the configured item lists are interpreted.
 */
public enum ItemFilterMode {

    /** Listed items do not float; everything else may float. */
    BLACKLIST("blacklist"),

    /** Only listed items may float; everything else is blocked. */
    WHITELIST("whitelist");

    private final String id;

    ItemFilterMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    static ItemFilterMode parse(String value) {
        if (value == null) {
            return BLACKLIST;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_');
        for (ItemFilterMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return BLACKLIST;
    }
}
