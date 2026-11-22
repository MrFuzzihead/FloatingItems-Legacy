package com.mrfuzzihead.floatingitems.smoketest;

import com.mrfuzzihead.floatingitems.FIConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * Test-only dedicated-server boot check. It deliberately does not depend on the game-test mod.
 */
@Mod(
    modid = SmokeTestMod.MOD_ID,
    name = "FloatingItems Smoke Test",
    version = "1.0",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:floatingitems")
public class SmokeTestMod {

    public static final String MOD_ID = "floatingitemssmoketest";
    static final String OK_MARKER = "FLOATINGITEMS_SMOKE_OK";
    static final String FAIL_MARKER = "FLOATINGITEMS_SMOKE_FAILED";

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        boolean ok = Loader.isModLoaded("floatingitems") && FIConfig.serverLang != null;
        System.out.println(ok ? OK_MARKER : FAIL_MARKER);
        final int status = ok ? 0 : 1;
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                FMLCommonHandler.instance()
                    .exitJava(status, false);
            }
        }, "FloatingItems-smoke-test-exit");
        worker.setDaemon(true);
        worker.start();
    }
}
