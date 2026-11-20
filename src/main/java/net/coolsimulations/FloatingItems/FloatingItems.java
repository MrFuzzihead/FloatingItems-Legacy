package net.coolsimulations.FloatingItems;

import java.io.File;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

@Mod(
    modid = FIReference.MOD_ID,
    name = FIReference.MOD_NAME,
    version = FIReference.VERSION,
    acceptedMinecraftVersions = FIReference.ACCEPTED_VERSIONS,
    dependencies = FIReference.DEPENDENCIES,
    acceptableRemoteVersions = "*",
    guiFactory = "net.coolsimulations.FloatingItems.FIConfigGUI")
public class FloatingItems {

    @Mod.Instance(FIReference.MOD_ID)
    public static FloatingItems instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println("Pre Init");
        FIConfig.init(new File(event.getModConfigurationDirectory(), FIReference.FLOATINGITEMS_CONFIG_FILE));
        FIUpdateHandler.init();
        // PlayerLoggedInEvent and ConfigChangedEvent are posted on FML's bus in 1.7.10, not the Forge bus.
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // All mods have registered their items by now; drop entries whose ids still do not resolve.
        FIConfig.resolveItems();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent event) {
        if (FIReference.MOD_ID.equals(event.modID)) {
            FIConfig.syncConfig(false);
            FIConfig.resolveItems();
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        if (FIConfig.disableUpdateCheck || !FIUpdateHandler.isOld) {
            return;
        }
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // The update check runs asynchronously. Either message can still be null if its HTTP request failed, so
        // never hand a null component to the player connection.
        if (FIUpdateHandler.updateInfo != null) {
            player.addChatMessage(FIUpdateHandler.updateInfo);
        }
        if (FIUpdateHandler.updateVersionInfo != null) {
            player.addChatMessage(FIUpdateHandler.updateVersionInfo);
        }
    }
}
