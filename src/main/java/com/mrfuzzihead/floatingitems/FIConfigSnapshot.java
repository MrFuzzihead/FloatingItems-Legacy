package com.mrfuzzihead.floatingitems;

import java.util.Collection;
import java.util.Collections;

import com.mrfuzzihead.floatingitems.api.BlacklistSnapshot;

/**
 * The active config snapshot. This lives outside {@link FIConfig} so the holder only owns lifecycle/state and the
 * immutable snapshot owns the blacklist data and decision cache.
 */
public class FIConfigSnapshot extends BlacklistSnapshot<FIConfig.FIConfigItem> {

    private static final FIConfigSnapshot EMPTY = new FIConfigSnapshot(
        Collections.<FIConfig.FIConfigItem>emptyList(),
        Collections.<Integer>emptySet(),
        Collections.<String>emptyList());

    public FIConfigSnapshot(Collection<FIConfig.FIConfigItem> items, Collection<Integer> registryItems,
        Collection<String> oreDict) {
        super(items, registryItems, oreDict);
    }

    public static FIConfigSnapshot empty() {
        return EMPTY;
    }
}
