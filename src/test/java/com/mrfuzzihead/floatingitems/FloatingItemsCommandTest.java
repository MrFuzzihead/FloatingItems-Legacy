package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.Collections;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.IChatComponent;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.mrfuzzihead.floatingitems.api.DebugCounters;
import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

public class FloatingItemsCommandTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final FloatingItemsCommand command = new FloatingItemsCommand();

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Before
    @After
    public void reset() {
        FIConfig.blacklist = FIConfig.Blacklist.empty();
        FIConfig.filterMode = ItemFilterMode.BLACKLIST;
        FIConfig.liquidRules = LiquidRules.defaults();
        FIConfig.buoyancy = BuoyancySettings.defaults();
        FIConfig.scopeRules = ScopeRules.defaults();
        FIConfig.debugCounters = false;
        DebugCounters.setEnabled(false);
        DebugCounters.reset();
        FIConfig.config = null;
    }

    @Test
    public void statusReportsTheEffectiveSettings() {
        FIConfig.filterMode = ItemFilterMode.WHITELIST;
        FIConfig.blacklist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.singleton(4),
            Collections.singletonList("ingotCopper"));
        FIConfig.liquidRules = new LiquidRules(
            LiquidPolicy.WATER_LIKE,
            Collections.singletonList("salt_water"),
            Collections.singletonList("oil"));
        FIConfig.buoyancy = new BuoyancySettings(0.2D, 0.5D, 0.3D, 0.1D);
        FIConfig.scopeRules = new ScopeRules(
            ScopeMode.WHITELIST,
            Collections.singletonList(0),
            ScopeMode.BLACKLIST,
            Collections.singletonList("Ocean"),
            ScopeMode.WHITELIST,
            Collections.singletonList("minecraft"));
        ICommandSender sender = mock(ICommandSender.class);

        command.processCommand(sender, new String[] { "status" });

        ArgumentCaptor<IChatComponent> messages = ArgumentCaptor.forClass(IChatComponent.class);
        verify(sender, times(5)).addChatMessage(messages.capture());
        String output = messages.getAllValues()
            .get(0)
            .getUnformattedText() + "\n"
            + messages.getAllValues()
                .get(1)
                .getUnformattedText()
            + "\n"
            + messages.getAllValues()
                .get(2)
                .getUnformattedText()
            + "\n"
            + messages.getAllValues()
                .get(3)
                .getUnformattedText()
            + "\n"
            + messages.getAllValues()
                .get(4)
                .getUnformattedText();

        assertTrue(output.contains("whitelist filter (1 item, 1 registry, 1 ore entries)"));
        assertTrue(output.contains("Liquid: water_like (allow=[salt_water], deny=[oil])"));
        assertTrue(output.contains("Scope: dimension=whitelist[0] biome=blacklist[ocean] mod=whitelist[minecraft]"));
        assertTrue(output.contains("Buoyancy: lift=0.2000 damping=0.500 maxSpeed=0.300 surfaceOffset=0.100"));
        assertTrue(output.contains("Debug counters: disabled"));
    }

    @Test
    public void reloadReadsTheConfigFileAndResolvesEntries() throws Exception {
        File file = new File(temporaryFolder.getRoot(), FIReference.FLOATINGITEMS_CONFIG_FILE);
        FIConfig.init(file);
        FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.ITEM_BLACKLIST_KEY, new String[] {})
            .set(new String[] { "{280}(0)" });
        FIConfig.config.save();
        ICommandSender sender = mock(ICommandSender.class);

        command.processCommand(sender, new String[] { "reload" });

        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .size());
        ArgumentCaptor<IChatComponent> message = ArgumentCaptor.forClass(IChatComponent.class);
        verify(sender).addChatMessage(message.capture());
        assertTrue(
            message.getValue()
                .getUnformattedText()
                .contains("config reloaded"));
    }

    @Test
    public void unknownSubcommandShowsUsage() {
        ICommandSender sender = mock(ICommandSender.class);

        command.processCommand(sender, new String[] { "nope" });

        ArgumentCaptor<IChatComponent> message = ArgumentCaptor.forClass(IChatComponent.class);
        verify(sender).addChatMessage(message.capture());
        assertTrue(
            message.getValue()
                .getUnformattedText()
                .contains("/floatingitems <status|reload>"));
    }

    @Test
    public void commandMetadataIsStable() {
        assertEquals("floatingitems", command.getCommandName());
        assertEquals("/floatingitems <status|reload>", command.getCommandUsage(mock(ICommandSender.class)));
        assertEquals(2, command.getRequiredPermissionLevel());
    }
}
