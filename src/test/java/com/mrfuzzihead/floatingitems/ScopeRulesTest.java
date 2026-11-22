package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ScopeRulesTest {

    @Test
    public void defaultsAllowEverything() {
        ScopeRules rules = ScopeRules.defaults();

        assertTrue(rules.allows(0, "Plains", "minecraft"));
        assertTrue(rules.allows(-1, "Hell", "thermalexpansion"));
    }

    @Test
    public void dimensionWhitelistAndBlacklistAreApplied() {
        ScopeRules whitelist = new ScopeRules(
            ScopeMode.WHITELIST,
            Collections.singletonList(0),
            ScopeMode.ALL,
            Collections.<String>emptyList(),
            ScopeMode.ALL,
            Collections.<String>emptyList());
        assertTrue(whitelist.allows(0, "Plains", "minecraft"));
        assertFalse(whitelist.allows(1, "Sky", "minecraft"));

        ScopeRules blacklist = new ScopeRules(
            ScopeMode.BLACKLIST,
            Collections.singletonList(1),
            ScopeMode.ALL,
            Collections.<String>emptyList(),
            ScopeMode.ALL,
            Collections.<String>emptyList());
        assertFalse(blacklist.allows(1, "Sky", "minecraft"));
        assertTrue(blacklist.allows(0, "Plains", "minecraft"));
    }

    @Test
    public void biomeAndModIdListsAreNormalizedAndCaseInsensitive() {
        ScopeRules rules = new ScopeRules(
            ScopeMode.ALL,
            Collections.<Integer>emptyList(),
            ScopeMode.WHITELIST,
            Arrays.asList(" Plains ", "OCEAN"),
            ScopeMode.BLACKLIST,
            Collections.singletonList("ThermalExpansion"));

        assertTrue(rules.allows(0, "plains", "minecraft"));
        assertTrue(rules.allows(0, "Ocean", "minecraft"));
        assertFalse(rules.allows(0, "Desert", "minecraft"));
        assertFalse(rules.allows(0, "Plains", "thermalexpansion"));
    }

    @Test
    public void emptyWhitelistDeniesEverythingAndEmptyBlacklistAllowsEverything() {
        ScopeRules whitelist = new ScopeRules(
            ScopeMode.WHITELIST,
            Collections.<Integer>emptyList(),
            ScopeMode.ALL,
            Collections.<String>emptyList(),
            ScopeMode.ALL,
            Collections.<String>emptyList());
        assertFalse(whitelist.allows(0, "Plains", "minecraft"));

        ScopeRules blacklist = new ScopeRules(
            ScopeMode.ALL,
            Collections.<Integer>emptyList(),
            ScopeMode.ALL,
            Collections.<String>emptyList(),
            ScopeMode.BLACKLIST,
            Collections.<String>emptyList());
        assertTrue(blacklist.allows(0, "Plains", "minecraft"));
    }
}
