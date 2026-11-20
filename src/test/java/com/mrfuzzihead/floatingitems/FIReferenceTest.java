package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the metadata that FML, the update checker and the config GUI all read from one place.
 */
public class FIReferenceTest {

    @Test
    public void modIdentityIsStable() {
        assertEquals("floatingitems", FIReference.MOD_ID);
        assertEquals("FloatingItems", FIReference.MOD_NAME);
    }

    @Test
    public void versionIsInjectedByTheBuild() {
        assertNotNull(FIReference.VERSION);
        assertFalse(FIReference.VERSION.isEmpty());
        assertFalse("the Gradle token was not replaced", FIReference.VERSION.contains("GRADLETOKEN"));
        assertEquals(FIReference.VERSION, Tags.VERSION);
    }

    @Test
    public void acceptedMinecraftRangeCoversTheTarget() {
        assertTrue(FIReference.ACCEPTED_VERSIONS.contains("1.7.10"));
    }

    @Test
    public void dependenciesDeclareForgeAndUniMixins() {
        assertNotNull(FIReference.DEPENDENCIES);
        assertTrue(FIReference.DEPENDENCIES.contains("required-after:Forge"));
        assertTrue(FIReference.DEPENDENCIES.contains("required-after:unimixins"));
        assertFalse("the dependency string should not end with a separator", FIReference.DEPENDENCIES.endsWith(";"));
    }

    @Test
    public void configNamesMatchTheOnDiskContract() {
        assertEquals("util", FIReference.CONFIG_CATEGORY_UTIL);
        assertEquals("FloatingItems.conf", FIReference.FLOATINGITEMS_CONFIG_FILE);
    }
}
