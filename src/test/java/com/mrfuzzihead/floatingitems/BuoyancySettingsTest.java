package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuoyancySettingsTest {

    @Test
    public void defaultsPreserveTheHistoricalProfile() {
        BuoyancySettings defaults = BuoyancySettings.defaults();

        assertEquals(0.03999999910593033D, defaults.getLift(), 0.0D);
        assertEquals(0.95D, defaults.getHorizontalDamping(), 0.0D);
        assertEquals(0.4D, defaults.getMaxUpwardSpeed(), 0.0D);
        assertEquals(0.0D, defaults.getSurfaceOffset(), 0.0D);
    }

    @Test
    public void valuesAreClampedToTheirSupportedRanges() {
        BuoyancySettings low = new BuoyancySettings(-1.0D, -1.0D, -1.0D, -2.0D);
        assertEquals(0.0D, low.getLift(), 0.0D);
        assertEquals(0.0D, low.getHorizontalDamping(), 0.0D);
        assertEquals(0.0D, low.getMaxUpwardSpeed(), 0.0D);
        assertEquals(-0.5D, low.getSurfaceOffset(), 0.0D);

        BuoyancySettings high = new BuoyancySettings(10.0D, 10.0D, 20.0D, 2.0D);
        assertEquals(1.0D, high.getLift(), 0.0D);
        assertEquals(1.0D, high.getHorizontalDamping(), 0.0D);
        assertEquals(10.0D, high.getMaxUpwardSpeed(), 0.0D);
        assertEquals(0.5D, high.getSurfaceOffset(), 0.0D);
    }

    @Test
    public void nonFiniteValuesFallBackToDefaults() {
        BuoyancySettings settings = new BuoyancySettings(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        assertEquals(BuoyancySettings.DEFAULT_LIFT, settings.getLift(), 0.0D);
        assertEquals(BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING, settings.getHorizontalDamping(), 0.0D);
        assertEquals(BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED, settings.getMaxUpwardSpeed(), 0.0D);
        assertEquals(BuoyancySettings.DEFAULT_SURFACE_OFFSET, settings.getSurfaceOffset(), 0.0D);
    }
}
