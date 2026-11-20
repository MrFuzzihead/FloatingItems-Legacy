package net.coolsimulations.floatingitems.testutil;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.RegistryNamespaced;
import net.minecraft.util.RegistrySimple;

import cpw.mods.fml.relauncher.FMLInjectionData;

/**
 * Installs a single fake item into the vanilla item registry without booting FML.
 *
 * <p>
 * The obvious approach, calling {@link Item#registerItems()}, does not work headless: every registration goes
 * through {@code GameData.addPrefix}, which calls {@code Loader.instance()}, and FML's {@code Loader} casts the
 * application class loader to {@code LaunchClassLoader}. That cast fails outside a Forge launch.
 * </p>
 *
 * <p>
 * The registry itself, however, is a plain {@code RegistryNamespaced}. Adding the two map entries that back
 * {@code getObject(String)} and {@code getObjectById(int)} is enough for every config parser test, and it avoids
 * touching static final fields or starting a game.
 * </p>
 */
public final class MinecraftTestBootstrap {

    /** The registry id used by the config parser tests. */
    public static final int STICK_ID = 280;

    private static boolean installed;
    private static Item stick;

    private MinecraftTestBootstrap() {}

    /**
     * Installs the fake {@code minecraft:stick} entry once per test JVM.
     *
     * @return the fake item, also available from {@link #stick()}
     */
    public static synchronized Item installFakeRegistry() {
        if (installed) {
            return stick;
        }

        stick = new Item();

        // Configuration reads FMLInjectionData.data()[6] to strip the game directory from config paths. FML
        // normally fills that in from the tweaker; a headless test has to provide a directory of its own.
        try {
            Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
            minecraftHome.setAccessible(true);
            minecraftHome.set(null, new java.io.File(System.getProperty("java.io.tmpdir")));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not initialise FMLInjectionData.minecraftHome", e);
        }

        RegistryNamespaced registry = Item.itemRegistry;

        // RegistryNamespaced#addObject calls GameData, so populate the two backing maps directly. The maps are
        // mutable; only the registry field itself is final.
        names(registry).put("minecraft:stick", stick);
        ids(registry).func_148746_a(stick, STICK_ID);

        installed = true;
        return stick;
    }

    /**
     * @return the fake item installed by {@link #installFakeRegistry()}
     */
    public static synchronized Item stick() {
        return installFakeRegistry();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> names(RegistryNamespaced registry) {
        try {
            Field field = RegistrySimple.class.getDeclaredField("registryObjects");
            field.setAccessible(true);
            return (Map<Object, Object>) field.get(registry);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not access the registry name map", e);
        }
    }

    private static ObjectIntIdentityMap ids(RegistryNamespaced registry) {
        try {
            Field field = RegistryNamespaced.class.getDeclaredField("underlyingIntegerMap");
            field.setAccessible(true);
            return (ObjectIntIdentityMap) field.get(registry);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not access the registry id map", e);
        }
    }
}
