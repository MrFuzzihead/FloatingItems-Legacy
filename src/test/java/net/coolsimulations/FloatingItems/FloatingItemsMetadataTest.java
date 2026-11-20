package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Scanner;

import org.junit.Test;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

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
    public void mcmodInfoDeclaresUniMixins() throws Exception {
        String json = readResource("/mcmod.info");
        assertNotNull(json);
        // Match the requiredMods array even if the JSON is reformatted; the dependency must be visible to FML and
        // to any tooling that reads mcmod.info before the @Mod annotation is processed.
        assertTrue(json.matches("(?s).*\"requiredMods\"\\s*:\\s*\\[\\s*\"unimixins\"\\s*\\].*"));
    }

    @Test
    public void guiFactoryIsMarkedClientOnly() {
        SideOnly sideOnly = FIConfigGUI.class.getAnnotation(SideOnly.class);

        assertNotNull(sideOnly);
        assertEquals(Side.CLIENT, sideOnly.value());
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

    private static String readResource(String path) throws Exception {
        try (InputStream stream = FloatingItemsMetadataTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }
}
