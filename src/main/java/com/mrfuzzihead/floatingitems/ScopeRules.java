package com.mrfuzzihead.floatingitems;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Immutable dimension, biome and mod-ID scopes for floating.
 */
public final class ScopeRules {

    private static final ScopeRules DEFAULTS = new ScopeRules(
        ScopeMode.ALL,
        Collections.<Integer>emptyList(),
        ScopeMode.ALL,
        Collections.<String>emptyList(),
        ScopeMode.ALL,
        Collections.<String>emptyList());

    private final ScopeMode dimensionMode;
    private final Set<Integer> dimensions;
    private final ScopeMode biomeMode;
    private final Set<String> biomes;
    private final ScopeMode modMode;
    private final Set<String> modIds;

    public ScopeRules(ScopeMode dimensionMode, Collection<Integer> dimensions, ScopeMode biomeMode,
        Collection<String> biomes, ScopeMode modMode, Collection<String> modIds) {
        this.dimensionMode = dimensionMode == null ? ScopeMode.ALL : dimensionMode;
        this.dimensions = normalizeInts(dimensions);
        this.biomeMode = biomeMode == null ? ScopeMode.ALL : biomeMode;
        this.biomes = normalizeStrings(biomes);
        this.modMode = modMode == null ? ScopeMode.ALL : modMode;
        this.modIds = normalizeStrings(modIds);
    }

    public static ScopeRules defaults() {
        return DEFAULTS;
    }

    public ScopeMode getDimensionMode() {
        return dimensionMode;
    }

    public Set<Integer> getDimensions() {
        return dimensions;
    }

    public ScopeMode getBiomeMode() {
        return biomeMode;
    }

    public Set<String> getBiomes() {
        return biomes;
    }

    public ScopeMode getModMode() {
        return modMode;
    }

    public Set<String> getModIds() {
        return modIds;
    }

    public boolean allows(World world, ItemStack stack, double x, double z) {
        if (dimensionMode == ScopeMode.ALL && biomeMode == ScopeMode.ALL && modMode == ScopeMode.ALL) {
            return true;
        }
        if (world == null || world.provider == null || stack == null || stack.getItem() == null) {
            return false;
        }

        int dimension = world.provider.dimensionId;
        String biome = "";
        BiomeGenBase biomeBase = world.getBiomeGenForCoords(MathHelper.floor_double(x), MathHelper.floor_double(z));
        if (biomeBase != null && biomeBase.biomeName != null) {
            biome = biomeBase.biomeName;
        }

        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        int colon = registryName == null ? -1 : registryName.indexOf(':');
        String modId = colon > 0 ? registryName.substring(0, colon) : "minecraft";

        return allows(dimension, biome, modId);
    }

    boolean allows(int dimension, String biome, String modId) {
        return matches(dimensionMode, dimensions.contains(dimension))
            && matches(biomeMode, biomes.contains(normalize(biome)))
            && matches(modMode, modIds.contains(normalize(modId)));
    }

    private static boolean matches(ScopeMode mode, boolean listed) {
        switch (mode) {
            case WHITELIST:
                return listed;
            case BLACKLIST:
                return !listed;
            default:
                return true;
        }
    }

    private static Set<Integer> normalizeInts(Collection<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Collections.unmodifiableSet(new HashSet<Integer>());
        }
        return Collections.unmodifiableSet(new HashSet<Integer>(values));
    }

    private static Set<String> normalizeStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.unmodifiableSet(new HashSet<String>());
        }
        Set<String> normalized = new HashSet<String>();
        for (String value : values) {
            String name = normalize(value);
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalize(String value) {
        return value == null ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }
}
