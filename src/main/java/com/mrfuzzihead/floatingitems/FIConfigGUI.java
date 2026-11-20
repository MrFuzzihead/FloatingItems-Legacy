package com.mrfuzzihead.floatingitems;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FIConfigGUI implements IModGuiFactory {

    @SideOnly(Side.CLIENT)
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

    /**
     * @deprecated not part of the 1.7.10 {@link IModGuiFactory} contract; retained for addon compatibility
     */
    @Deprecated
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new FIConfigGUIInner(parent);
    }

    /**
     * @deprecated not part of the 1.7.10 {@link IModGuiFactory} contract; retained for addon compatibility
     */
    @Deprecated
    public boolean hasConfigGui() {
        return true;
    }

}
