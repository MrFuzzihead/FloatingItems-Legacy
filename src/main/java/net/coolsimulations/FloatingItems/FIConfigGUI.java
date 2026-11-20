package net.coolsimulations.FloatingItems;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;

public class FIConfigGUI implements IModGuiFactory {

    public static class FIConfigGUIInner extends GuiConfig {

        public FIConfigGUIInner(GuiScreen parent) {
            super(
                parent,
                FIConfig.getConfigElements(),
                FIReference.MOD_ID,
                false,
                false,
                FILang.getTranslations("fi.configgui.title"));
        }
    }

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // No client-only initialization is required.
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return FIConfigGUIInner.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        // null means this mod has no runtime option categories.
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        // No runtime options, so no handler exists.
        return null;
    }

}
