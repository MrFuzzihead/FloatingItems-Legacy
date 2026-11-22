package com.mrfuzzihead.floatingitems.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional process-wide counters for diagnosing floating decisions.
 *
 * <p>
 * Counting is disabled unless {@link #setEnabled(boolean)} is turned on by the config. The methods are safe to call
 * from the item tick even when disabled.
 * </p>
 */
public final class DebugCounters {

    private static volatile boolean enabled;
    private static final AtomicLong EVALUATED = new AtomicLong();
    private static final AtomicLong FLOATED = new AtomicLong();
    private static final AtomicLong CACHE_HITS = new AtomicLong();
    private static final AtomicLong CACHE_MISSES = new AtomicLong();

    private DebugCounters() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void itemEvaluated() {
        if (enabled) {
            EVALUATED.incrementAndGet();
        }
    }

    public static void itemFloated() {
        if (enabled) {
            FLOATED.incrementAndGet();
        }
    }

    public static void cacheHit() {
        if (enabled) {
            CACHE_HITS.incrementAndGet();
        }
    }

    public static void cacheMiss() {
        if (enabled) {
            CACHE_MISSES.incrementAndGet();
        }
    }

    public static long getItemsEvaluated() {
        return EVALUATED.get();
    }

    public static long getItemsFloated() {
        return FLOATED.get();
    }

    public static long getCacheHits() {
        return CACHE_HITS.get();
    }

    public static long getCacheMisses() {
        return CACHE_MISSES.get();
    }

    public static void reset() {
        EVALUATED.set(0);
        FLOATED.set(0);
        CACHE_HITS.set(0);
        CACHE_MISSES.set(0);
    }
}
