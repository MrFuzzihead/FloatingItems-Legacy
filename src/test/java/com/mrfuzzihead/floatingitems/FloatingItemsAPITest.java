package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mrfuzzihead.floatingitems.api.BlacklistEntry;
import com.mrfuzzihead.floatingitems.api.BlacklistSnapshot;
import com.mrfuzzihead.floatingitems.api.FloatingItemsAPI;
import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

public class FloatingItemsAPITest {

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Before
    @After
    public void reset() {
        FIConfig.blacklist = FIConfig.Blacklist.empty();
    }

    @Test
    public void exposesTheCurrentSnapshotAndBlacklistDecision() {
        FIConfig.Blacklist snapshot = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());
        FIConfig.blacklist = snapshot;

        assertSame(snapshot, FloatingItemsAPI.getBlacklist());
        assertTrue(FloatingItemsAPI.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 0)));
        assertFalse(FloatingItemsAPI.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 1)));
    }

    @Test
    public void exposesRegistryNameParsingAndWildcardSnapshots() {
        BlacklistEntry entry = BlacklistEntry.parse("minecraft:stick:*");
        BlacklistSnapshot<BlacklistEntry> snapshot = new BlacklistSnapshot<BlacklistEntry>(
            Collections.singletonList(entry),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        assertTrue(entry.hasWildcardMeta());
        assertTrue(snapshot.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 7)));
        assertEquals("minecraft:stick:*", entry.toString());
    }

    @Test
    public void apiIdentityMatchesTheMod() {
        assertEquals("floatingitems", FloatingItemsAPI.MOD_ID);
    }
}
