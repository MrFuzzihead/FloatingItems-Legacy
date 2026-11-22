package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mrfuzzihead.floatingitems.api.DebugCounters;
import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

public class DebugCountersTest {

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Before
    @After
    public void reset() {
        DebugCounters.setEnabled(false);
        DebugCounters.reset();
    }

    @Test
    public void countersOnlyIncrementWhileEnabled() {
        DebugCounters.itemEvaluated();
        DebugCounters.itemFloated();
        DebugCounters.cacheHit();
        assertEquals(0, DebugCounters.getItemsEvaluated());
        assertEquals(0, DebugCounters.getItemsFloated());
        assertEquals(0, DebugCounters.getCacheHits());

        DebugCounters.setEnabled(true);
        DebugCounters.itemEvaluated();
        DebugCounters.itemFloated();
        DebugCounters.cacheHit();
        DebugCounters.cacheMiss();

        assertTrue(DebugCounters.isEnabled());
        assertEquals(1, DebugCounters.getItemsEvaluated());
        assertEquals(1, DebugCounters.getItemsFloated());
        assertEquals(1, DebugCounters.getCacheHits());
        assertEquals(1, DebugCounters.getCacheMisses());

        DebugCounters.setEnabled(false);
        assertFalse(DebugCounters.isEnabled());
        DebugCounters.itemEvaluated();
        assertEquals(1, DebugCounters.getItemsEvaluated());
    }

    @Test
    public void blacklistSnapshotCacheHitsAreCounted() {
        DebugCounters.setEnabled(true);
        FIConfig.Blacklist snapshot = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());
        ItemStack stack = new ItemStack(MinecraftTestBootstrap.stick(), 1, 0);

        assertTrue(snapshot.isBlacklisted(stack));
        assertTrue(snapshot.isBlacklisted(stack));

        assertEquals(1, DebugCounters.getCacheMisses());
        assertEquals(1, DebugCounters.getCacheHits());
    }

    @Test
    public void resetClearsEveryCounter() {
        DebugCounters.setEnabled(true);
        DebugCounters.itemEvaluated();
        DebugCounters.itemFloated();
        DebugCounters.cacheHit();
        DebugCounters.cacheMiss();

        DebugCounters.reset();

        assertEquals(0, DebugCounters.getItemsEvaluated());
        assertEquals(0, DebugCounters.getItemsFloated());
        assertEquals(0, DebugCounters.getCacheHits());
        assertEquals(0, DebugCounters.getCacheMisses());
    }
}
