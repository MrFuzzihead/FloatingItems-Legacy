package com.mrfuzzihead.floatingitems;

import java.util.Locale;

/**
 * Controls which liquids an item may float in. The default only accepts water and water-named Forge fluids whose
 * properties are close to water; other modded fluids must be opted into with the configured fluid allowlist.
 */
public enum LiquidPolicy {

    /** Vanilla water, plus any non-Forge block whose material is water. */
    WATER("water"),

    /** Vanilla water, plus Forge fluids with a water-like name and sane water-like properties. */
    WATER_LIKE("water_like"),

    /** Legacy behaviour: any block whose material is water, regardless of its Forge fluid. */
    MATERIAL_WATER("material_water");

    private final String id;

    LiquidPolicy(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    static LiquidPolicy parse(String value) {
        if (value == null) {
            return WATER_LIKE;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_');
        for (LiquidPolicy policy : values()) {
            if (policy.id.equals(normalized)) {
                return policy;
            }
        }
        return WATER_LIKE;
    }
}
