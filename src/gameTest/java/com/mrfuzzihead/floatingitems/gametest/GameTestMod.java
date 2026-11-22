package com.mrfuzzihead.floatingitems.gametest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Test-only mod: a separate, non-shipped {@code @Mod} that asserts FloatingItems works in a real dedicated server.
 *
 * <p>
 * It lives in the {@code gameTest} source set and is only put on the classpath by {@code ./gradlew gameTestServer}
 * (or {@code -PgameTest}), so it never reaches the published jar. The test arena and the assertions live in
 * {@link FloatingItemsGameTests}; this class only owns the server lifecycle and the exit code.
 * </p>
 *
 * <pre>
 * ./gradlew gameTestServer
 * </pre>
 *
 * <p>
 * The assertions run on the server thread, driven by {@link TickEvent.ServerTickEvent}. Entity physics needs the
 * world to tick, so a single {@code FMLServerStartedEvent} callback cannot do the whole job. Using the tick loop
 * instead of a worker thread also keeps every block/entity mutation on the server thread, which is what Forge
 * expects.
 * </p>
 */
@Mod(
    modid = GameTestMod.MOD_ID,
    name = "FloatingItems Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:floatingitems")
public class GameTestMod {

    public static final String MOD_ID = "floatingitemsgametest";
    static final String OK_MARKER = "FLOATINGITEMS_GAMETEST_OK";
    static final String FAIL_MARKER = "FLOATINGITEMS_GAMETEST_FAILED";

    @Mod.Instance(GameTestMod.MOD_ID)
    public static GameTestMod instance;

    private boolean started;
    private boolean failed;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FloatingItemsGameTests.registerTestBlocks();
        // PlayerEvent and TickEvent live on FML's bus in 1.7.10, not on the Forge bus.
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (started) {
            return;
        }
        started = true;

        WorldServer overworld = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (overworld == null) {
            fail("the overworld is not available");
            return;
        }

        try {
            FloatingItemsGameTests.start(overworld);
        } catch (Throwable t) {
            t.printStackTrace();
            fail("could not start the game tests: " + t);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !started || failed) {
            return;
        }
        try {
            FloatingItemsGameTests.tick();
        } catch (Throwable t) {
            t.printStackTrace();
            fail("the game tests threw: " + t);
        }
    }

    private void fail(String message) {
        failed = true;
        System.out.println(FAIL_MARKER + ": " + message);
        exit(1);
    }

    /**
     * Called by the test suite when it has finished. The exit is deferred to a worker so the server thread can
     * return from the tick callback before {@code Runtime.exit} starts shutdown hooks.
     */
    static void exit(int status) {
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                FMLCommonHandler.instance()
                    .exitJava(status, false);
            }
        }, "FloatingItems-game-test-exit");
        worker.setDaemon(true);
        worker.start();
    }
}
