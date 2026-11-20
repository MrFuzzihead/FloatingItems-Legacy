package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import cpw.mods.fml.common.Mod;

/**
 * Reads the {@link Mod} annotation back, so a change to the mod id, version or GUI factory cannot drift away from
 * {@link FIReference}.
 */
public class FloatingItemsMetadataTest {

    @Test
    public void modAnnotationMatchesTheReferenceConstants() {
        Mod mod = FloatingItems.class.getAnnotation(Mod.class);

        assertNotNull("@Mod is missing", mod);
        assertEquals(FIReference.MOD_ID, mod.modid());
        assertEquals(FIReference.MOD_NAME, mod.name());
        assertEquals(FIReference.VERSION, mod.version());
        assertEquals(FIReference.ACCEPTED_VERSIONS, mod.acceptedMinecraftVersions());
        assertEquals(FIReference.DEPENDENCIES, mod.dependencies());
        assertEquals("*", mod.acceptableRemoteVersions());
    }

    @Test
    public void guiFactoryStillPointsAtTheClientClass() {
        Mod mod = FloatingItems.class.getAnnotation(Mod.class);

        assertNotNull(mod);
        assertEquals("net.coolsimulations.FloatingItems.FIConfigGUI", mod.guiFactory());
        // The class exists and is public; FML loads it only on the client, but the name must be resolvable in the
        // main source set so the annotation processor can validate it.
        assertEquals("net.coolsimulations.FloatingItems.FIConfigGUI", FIConfigGUI.class.getName());
    }
}
