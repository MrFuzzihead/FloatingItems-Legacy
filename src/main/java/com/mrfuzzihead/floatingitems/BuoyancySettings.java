package com.mrfuzzihead.floatingitems;

/**
 * Immutable, validated buoyancy tuning values.
 */
public final class BuoyancySettings {

    public static final double DEFAULT_LIFT = 0.03999999910593033D;
    public static final double DEFAULT_HORIZONTAL_DAMPING = 0.95D;
    public static final double DEFAULT_MAX_UPWARD_SPEED = 0.4D;
    public static final double DEFAULT_SURFACE_OFFSET = 0.0D;

    private static final BuoyancySettings DEFAULTS = new BuoyancySettings(
        DEFAULT_LIFT,
        DEFAULT_HORIZONTAL_DAMPING,
        DEFAULT_MAX_UPWARD_SPEED,
        DEFAULT_SURFACE_OFFSET);

    private final double lift;
    private final double horizontalDamping;
    private final double maxUpwardSpeed;
    private final double surfaceOffset;

    public BuoyancySettings(double lift, double horizontalDamping, double maxUpwardSpeed, double surfaceOffset) {
        this.lift = clamp(lift, 0.0D, 1.0D, DEFAULT_LIFT);
        this.horizontalDamping = clamp(horizontalDamping, 0.0D, 1.0D, DEFAULT_HORIZONTAL_DAMPING);
        this.maxUpwardSpeed = clamp(maxUpwardSpeed, 0.0D, 10.0D, DEFAULT_MAX_UPWARD_SPEED);
        this.surfaceOffset = clamp(surfaceOffset, -0.5D, 0.5D, DEFAULT_SURFACE_OFFSET);
    }

    public static BuoyancySettings defaults() {
        return DEFAULTS;
    }

    public double getLift() {
        return lift;
    }

    public double getHorizontalDamping() {
        return horizontalDamping;
    }

    public double getMaxUpwardSpeed() {
        return maxUpwardSpeed;
    }

    public double getSurfaceOffset() {
        return surfaceOffset;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
