package com.mrfuzzihead.floatingitems.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.oredict.OreDictionary;

import com.mrfuzzihead.floatingitems.FIConfig;
import com.mrfuzzihead.floatingitems.FIReference;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The in-game assertions.
 *
 * <p>
 * Each phase runs for {@link #PHASE_TICKS} server ticks. Entity item physics is only observable once the world
 * ticks, so the suite is a small state machine driven by {@code GameTestMod}'s server-tick handler. Every mutation
 * stays on the server thread.
 * </p>
 *
 * <p>
 * Correct behaviour is asserted with {@link #check(String, boolean)}. The bug ids in the messages refer to
 * {@code CODEBASE_ANALYSIS.md}.
 * </p>
 */
final class FloatingItemsGameTests {

    private static final int FLOOR_Y = 100;
    private static final int WATER_TOP_Y = 103;
    private static final double WATER_START_Y = FLOOR_Y + 1.0D;
    private static final double WATER_SURFACE_Y = WATER_TOP_Y + 1.0D;
    private static final int PHASE_TICKS = 120;

    private static final int AIR_X = 8;
    private static final int COUNTING_X = 16;
    private static final int LAVA_X = -8;
    private static final int NEGATIVE_X = -24;
    private static final int NEGATIVE_Z = -24;
    private static final int FLOWING_X = 24;
    private static final int SHALLOW_X = -16;
    private static final int CHUNK_BORDER_X = 15;
    private static final int CHUNK_BORDER_Z = 15;
    private static final int MODDED_LIQUID_X = 0;
    private static final int MODDED_LIQUID_Z = 24;

    private static final String ITEM_BLACKLIST_KEY = "Blacklist of Items that should not Float";

    private static final String PASS = "[FI-GAMETEST] ok   ";
    private static final String FAIL = "[FI-GAMETEST] FAIL ";

    private static final AtomicInteger COUNTING_CALLS = new AtomicInteger();
    private static final Item COUNTING_ITEM = new CountingItem();
    private static final Fluid MODDED_LIQUID_FLUID = new Fluid("floatingitems_test_oil").setDensity(1200)
        .setTemperature(300)
        .setViscosity(1500);
    private static final Block MODDED_LIQUID = new GameTestLiquidBlock();

    private static WorldServer world;
    private static boolean running;
    private static boolean finished;
    private static boolean oreRegistered;
    private static int phase;
    private static int ticks;
    private static int checks;
    private static int failures;

    private static EntityItem waterItem;
    private static EntityItem airItem;
    private static EntityItem lavaItem;
    private static EntityItem negativeItem;
    private static EntityItem countingItem;
    private static EntityItem phaseItem;
    private static EntityItem sourceItem;
    private static EntityItem flowingItem;
    private static EntityItem shallowItem;
    private static EntityItem chunkBorderItem;
    private static EntityItem nbtItem;
    private static EntityItem mergeItemA;
    private static EntityItem mergeItemB;
    private static EntityItem despawnItem;
    private static EntityItem moddedLiquidItem;
    private static ItemStack nbtStack;
    private static double waterStartY;
    private static double negativeStartY;
    private static double phaseStartY;
    private static double sourceStartY;
    private static double flowingStartY;
    private static double shallowStartY;
    private static double chunkBorderStartY;
    private static double nbtStartY;
    private static double moddedLiquidStartY;
    private static ForgeChunkManager.Ticket chunkTicket;

    private FloatingItemsGameTests() {}

    /**
     * Registers the test-only liquid block so world saves can serialize it. Called from {@link GameTestMod#preInit}.
     */
    static void registerTestBlocks() {
        GameRegistry.registerBlock(MODDED_LIQUID, "floatingItemsGameTestLiquid");
    }

    /**
     * Builds the arena and schedules phase 0. Must run on the server thread.
     *
     * @param overworld the overworld
     */
    static void start(WorldServer overworld) {
        if (running || finished) {
            return;
        }
        world = overworld;
        resetItemBlacklistConfig();
        keepArenaLoaded();
        buildArenas();
        clearItems();
        checkConfigEventBus();
        startPhase(0);
        running = true;
    }

    /**
     * Advances the state machine. Called at the start of every server tick.
     */
    static void tick() {
        if (!running || finished) {
            return;
        }
        ticks++;
        if (ticks < PHASE_TICKS) {
            return;
        }

        evaluatePhase(phase);
        ticks = 0;
        phase++;
        startPhase(phase);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Arena
    // ---------------------------------------------------------------------------------------------------------

    /**
     * A dedicated server with no players unloads every chunk, including the spawn chunks, so an entity that is
     * spawned but whose chunk is unloaded never ticks. A Forge chunk ticket keeps the small test area alive.
     */
    private static void keepArenaLoaded() {
        if (GameTestMod.instance == null) {
            check("the game-test mod instance is available for a chunk ticket", false);
            return;
        }
        ForgeChunkManager.setForcedChunkLoadingCallback(GameTestMod.instance, new ForgeChunkManager.LoadingCallback() {

            @Override
            public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, net.minecraft.world.World world) {
                // No saved tickets: this is a throwaway world.
            }
        });
        chunkTicket = ForgeChunkManager.requestTicket(GameTestMod.instance, world, ForgeChunkManager.Type.NORMAL);
        check("a chunk ticket is available for the test arena", chunkTicket != null);
        if (chunkTicket == null) {
            return;
        }
        // The pools live in chunks (-2,-2) through (1,1). Force the whole square.
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                ForgeChunkManager.forceChunk(chunkTicket, new ChunkCoordIntPair(chunkX, chunkZ));
            }
        }
    }

    private static void buildArenas() {
        // Main water pool: 3x3 source blocks at y=101..103, stone floor and walls.
        buildPool(0, 0, 1, Blocks.water);
        // A real lava pool: the mixin must not apply the water lift to it.
        buildPool(LAVA_X, 0, 1, Blocks.lava);
        // One-block water column at x=-24. The item's bounding box is inside block -24, while (int)posX is -23,
        // which is the stone wall. That is exactly the negative-coordinate bug.
        buildPool(NEGATIVE_X, NEGATIVE_Z, 0, Blocks.water);
        // A pool that straddles the x=16/z=16 chunk border. Both chunks are covered by the ticket.
        buildPool(CHUNK_BORDER_X, CHUNK_BORDER_Z, 1, Blocks.water);
        // A downward-flowing column: one source at the top and air below it.
        buildWaterfall(FLOWING_X, 0);
        // A one-block-deep pool for a partially submerged item.
        buildShallowPool(SHALLOW_X, 0);
        // A non-water liquid-like block for the modded-liquid policy check.
        buildModdedLiquidPool(MODDED_LIQUID_X, MODDED_LIQUID_Z);

        // Clear the two air columns (the arena is above the flat-world ground, so nothing else lives there).
        for (int x = AIR_X - 1; x <= AIR_X + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = FLOOR_Y; y <= 112; y++) {
                    world.setBlockToAir(x, y, z);
                }
            }
        }
        for (int x = COUNTING_X - 1; x <= COUNTING_X + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = FLOOR_Y; y <= 112; y++) {
                    world.setBlockToAir(x, y, z);
                }
            }
        }
    }

    private static void buildPool(int centerX, int centerZ, int radius, Block liquid) {
        for (int x = centerX - radius - 1; x <= centerX + radius + 1; x++) {
            for (int z = centerZ - radius - 1; z <= centerZ + radius + 1; z++) {
                world.setBlock(x, FLOOR_Y, z, Blocks.stone);
            }
        }

        for (int y = FLOOR_Y + 1; y <= WATER_TOP_Y; y++) {
            for (int x = centerX - radius - 1; x <= centerX + radius + 1; x++) {
                for (int z = centerZ - radius - 1; z <= centerZ + radius + 1; z++) {
                    boolean wall = Math.abs(x - centerX) > radius || Math.abs(z - centerZ) > radius;
                    world.setBlock(x, y, z, wall ? Blocks.stone : liquid);
                }
            }
        }
    }

    private static void buildWaterfall(int centerX, int centerZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                world.setBlock(x, FLOOR_Y, z, Blocks.stone);
            }
        }

        for (int y = FLOOR_Y + 1; y <= WATER_TOP_Y + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                    boolean wall = x != centerX || z != centerZ;
                    world.setBlock(x, y, z, wall ? Blocks.stone : Blocks.air);
                }
            }
        }
        // The source is above the air column; the blocks below become downward-flowing water.
        world.setBlock(centerX, WATER_TOP_Y + 1, centerZ, Blocks.water);
    }

    private static void buildShallowPool(int centerX, int centerZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                world.setBlock(x, FLOOR_Y, z, Blocks.stone);
                boolean wall = x != centerX || z != centerZ;
                world.setBlock(x, FLOOR_Y + 1, z, wall ? Blocks.stone : Blocks.water);
                world.setBlock(x, FLOOR_Y + 2, z, wall ? Blocks.stone : Blocks.air);
            }
        }
    }

    private static void buildModdedLiquidPool(int centerX, int centerZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                world.setBlock(x, FLOOR_Y, z, Blocks.stone);
                boolean wall = x != centerX || z != centerZ;
                for (int y = FLOOR_Y + 1; y <= WATER_TOP_Y; y++) {
                    world.setBlock(x, y, z, wall ? Blocks.stone : MODDED_LIQUID);
                }
            }
        }
    }

    private static void clearItems() {
        List<?> items = world.getEntitiesWithinAABB(
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(-40.0D, 90.0D, -40.0D, 40.0D, 130.0D, 40.0D));
        for (Object item : items) {
            ((EntityItem) item).setDead();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Phases
    // ---------------------------------------------------------------------------------------------------------

    private static void startPhase(int nextPhase) {
        clearItems();
        phaseItem = null;
        phaseStartY = 0.0D;

        switch (nextPhase) {
            case 0:
                // Defense in depth for B-02: a caller that bypasses the config parser (or mutates the public
                // array) can still put a null item in the list. The mixin must skip it, not NPE every tick.
                FIConfig.blacklist = new FIConfig.Blacklist(
                    Arrays.<FIConfig.FIConfigItem>asList(null, nullItemConfigItem()),
                    Collections.<Integer>emptySet(),
                    Collections.<String>emptyList());
                COUNTING_CALLS.set(0);
                waterItem = spawn(0.5D, WATER_START_Y, 0.5D, new ItemStack(Items.stick), 0.2D);
                waterStartY = waterItem.posY;
                airItem = spawn(AIR_X + 0.5D, 110.0D, 0.5D, new ItemStack(Items.stick), 0.0D);
                lavaItem = spawn(LAVA_X + 0.5D, WATER_START_Y, 0.5D, new ItemStack(Items.stick), 0.0D);
                negativeItem = spawn(
                    NEGATIVE_X + 0.5D,
                    WATER_START_Y,
                    NEGATIVE_Z + 0.5D,
                    new ItemStack(Items.stick),
                    0.2D);
                negativeStartY = negativeItem.posY;
                countingItem = spawn(COUNTING_X + 0.5D, 110.0D, 0.5D, new ItemStack(COUNTING_ITEM), 0.0D);
                break;

            case 1:
                // A per-metadata blacklist entry must suppress the lift.
                FIConfig.blacklist = new FIConfig.Blacklist(
                    Collections.singletonList(configItem("{280}(0)")),
                    Collections.<Integer>emptySet(),
                    Collections.<String>emptyList());
                spawnPhaseItem();
                break;

            case 2:
                // A registry blacklist must suppress the lift too.
                FIConfig.blacklist = new FIConfig.Blacklist(
                    Collections.<FIConfig.FIConfigItem>emptyList(),
                    Collections.singleton(Item.getIdFromItem(Items.stick)),
                    Collections.<String>emptyList());
                spawnPhaseItem();
                break;

            case 3:
                // The per-metadata entry matches the item but not its metadata. The registry blacklist must still
                // win; this is the B-07 regression case.
                FIConfig.blacklist = new FIConfig.Blacklist(
                    Collections.singletonList(configItem("{280}(1)")),
                    Collections.singleton(Item.getIdFromItem(Items.stick)),
                    Collections.<String>emptyList());
                spawnPhaseItem();
                break;

            case 4:
                // The ore-dictionary blacklist compares item metadata as well as the item. A wool entry registered
                // with meta 0 therefore does not blacklist meta 1, even though the entry is meant to cover the
                // whole ore-dictionary name. Known B-09.
                if (!oreRegistered) {
                    OreDictionary.registerOre("floatingItemsGameTestWool", new ItemStack(Blocks.wool, 1, 0));
                    oreRegistered = true;
                }
                FIConfig.blacklist = new FIConfig.Blacklist(
                    Collections.<FIConfig.FIConfigItem>emptyList(),
                    Collections.<Integer>emptySet(),
                    Collections.singletonList("floatingItemsGameTestWool"));
                spawnPhaseItem(new ItemStack(Blocks.wool, 1, 1));
                break;

            case 5:
                // Exercise a real config reload, not a direct FIConfig.blacklist assignment.
                setItemBlacklistConfig("{280}(0)");
                spawnPhaseItem();
                break;

            case 6:
                // The control is a still source pool. The second item sits in a downward-flowing column.
                resetItemBlacklistConfig();
                sourceItem = spawn(0.5D, WATER_START_Y, 0.5D, new ItemStack(Items.stick), 0.2D);
                sourceStartY = sourceItem.posY;
                flowingItem = spawn(FLOWING_X + 0.5D, 102.5D, 0.5D, new ItemStack(Items.stick), 0.0D);
                flowingStartY = flowingItem.posY;
                break;

            case 7:
                // The item starts with only its lower half inside the one-block-deep water.
                shallowItem = spawn(SHALLOW_X + 0.5D, FLOOR_Y + 1.0D, 0.5D, new ItemStack(Items.stick), 0.0D);
                shallowStartY = shallowItem.posY;
                break;

            case 8:
                // posX/posZ are just below 16, so the bounding box crosses both chunk borders.
                chunkBorderItem = spawn(
                    CHUNK_BORDER_X + 0.9D,
                    WATER_START_Y,
                    CHUNK_BORDER_Z + 0.9D,
                    new ItemStack(Items.stick),
                    0.2D);
                chunkBorderStartY = chunkBorderItem.posY;
                break;

            case 9:
                nbtStack = new ItemStack(Items.stick);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("floatingItemsTest", "kept");
                nbtStack.setTagCompound(tag);
                nbtStack.setStackDisplayName("FloatingItems NBT");
                check("item NBT survives an entity save/load round trip", nbtRoundTrip(nbtStack));
                nbtItem = spawn(0.5D, WATER_START_Y, 0.5D, nbtStack, 0.2D);
                nbtStartY = nbtItem.posY;
                break;

            case 10:
                mergeItemA = spawn(0.5D, WATER_START_Y, 0.5D, new ItemStack(Items.stick), 0.0D);
                mergeItemB = spawn(0.5D, WATER_START_Y, 0.5D, new ItemStack(Items.stick), 0.0D);
                break;

            case 11:
                despawnItem = spawn(AIR_X + 0.5D, 110.0D, 0.5D, new ItemStack(Items.stick), 0.0D);
                despawnItem.lifespan = 5;
                break;

            case 12:
                moddedLiquidItem = spawn(
                    MODDED_LIQUID_X + 0.5D,
                    FLOOR_Y + 1.0D,
                    MODDED_LIQUID_Z + 0.5D,
                    new ItemStack(Items.stick),
                    0.0D);
                moddedLiquidStartY = moddedLiquidItem.posY;
                break;

            default:
                finish();
                break;
        }
    }

    private static void spawnPhaseItem() {
        spawnPhaseItem(new ItemStack(Items.stick));
    }

    private static void spawnPhaseItem(ItemStack stack) {
        phaseItem = spawn(0.5D, WATER_START_Y, 0.5D, stack, 0.0D);
        phaseStartY = phaseItem.posY;
    }

    private static EntityItem spawn(double x, double y, double z, ItemStack stack, double motionY) {
        EntityItem item = new EntityItem(world, x, y, z, stack);
        item.motionX = 0.0D;
        item.motionY = motionY;
        item.motionZ = 0.0D;
        item.delayBeforeCanPickup = 32767;
        world.spawnEntityInWorld(item);
        return item;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Assertions
    // ---------------------------------------------------------------------------------------------------------

    private static void evaluatePhase(int evaluatedPhase) {
        switch (evaluatedPhase) {
            case 0:
                System.out.println(
                    "[FI-GAMETEST] info phase 0: waterY=" + waterItem.posY
                        + " airY="
                        + airItem.posY
                        + " lavaY="
                        + lavaItem.posY
                        + " negativeY="
                        + negativeItem.posY
                        + " onEntityItemUpdateCalls="
                        + COUNTING_CALLS.get());
                check(
                    "an item in still water rises",
                    waterItem.isEntityAlive() && waterItem.posY > waterStartY + 0.5D
                        && waterItem.posY < WATER_SURFACE_Y + 1.0D);
                check("an item in air falls", airItem.posY < 109.0D);
                check(
                    "an item in lava does not receive water buoyancy",
                    lavaItem.isDead || lavaItem.posY < WATER_START_Y + 1.0D);
                check("an item at negative coordinates floats (B-08)", negativeItem.posY > negativeStartY + 0.2D);
                int calls = COUNTING_CALLS.get();
                check(
                    "onEntityItemUpdate is called once per tick (B-06)",
                    calls >= PHASE_TICKS - 1 && calls <= PHASE_TICKS);
                break;

            case 1:
                check("a per-metadata blacklist suppresses the lift", phaseItem.posY <= phaseStartY + 0.5D);
                break;

            case 2:
                check("a registry blacklist suppresses the lift", phaseItem.posY <= phaseStartY + 0.5D);
                break;

            case 3:
                check(
                    "a registry blacklist is not shadowed by a different metadata entry (B-07)",
                    phaseItem.posY <= phaseStartY + 0.5D);
                break;

            case 4:
                check(
                    "an ore dictionary blacklist covers every metadata of the entry (B-09)",
                    phaseItem.posY <= phaseStartY + 0.5D);
                break;

            case 5:
                check("a config reload blacklist suppresses the lift", phaseItem.posY <= phaseStartY + 0.5D);
                break;

            case 6:
                System.out.println(
                    "[FI-GAMETEST] info phase 6: sourceY=" + sourceItem.posY + " flowingY=" + flowingItem.posY);
                check(
                    "an item in still source water rises (control)",
                    sourceItem.isEntityAlive() && sourceItem.posY > sourceStartY + 0.5D);
                check(
                    "an item in downward-flowing water does not rise",
                    flowingItem.isEntityAlive() && flowingItem.posY <= flowingStartY + 0.05D);
                break;

            case 7:
                check(
                    "a partially submerged item rises",
                    shallowItem.isEntityAlive() && shallowItem.posY > shallowStartY + 0.2D);
                break;

            case 8:
                check(
                    "the chunk-border item actually straddles a chunk border",
                    chunkBorderItem.boundingBox.maxX > 16.0D && chunkBorderItem.boundingBox.maxZ > 16.0D);
                check(
                    "an item on a chunk border floats",
                    chunkBorderItem.isEntityAlive() && chunkBorderItem.posY > chunkBorderStartY + 0.2D);
                break;

            case 9:
                ItemStack observed = nbtItem.getEntityItem();
                check(
                    "item NBT is preserved while floating",
                    nbtItem.isEntityAlive() && observed != null
                        && observed.hasTagCompound()
                        && "kept".equals(
                            observed.getTagCompound()
                                .getString("floatingItemsTest"))
                        && "FloatingItems NBT".equals(observed.getDisplayName())
                        && nbtItem.posY > nbtStartY + 0.2D);
                break;

            case 10:
                int alive = (mergeItemA.isEntityAlive() ? 1 : 0) + (mergeItemB.isEntityAlive() ? 1 : 0);
                EntityItem survivor = mergeItemA.isEntityAlive() ? mergeItemA : mergeItemB;
                check(
                    "two nearby items merge into one stack",
                    alive == 1 && survivor.getEntityItem() != null && survivor.getEntityItem().stackSize == 2);
                break;

            case 11:
                check("an item with a short lifespan despawns", despawnItem.isDead);
                break;

            case 12:
                check(
                    "the modded-liquid test block is a Forge fluid with water material",
                    world.getBlock(MODDED_LIQUID_X, FLOOR_Y + 1, MODDED_LIQUID_Z) instanceof IFluidBlock
                        && world.getBlock(MODDED_LIQUID_X, FLOOR_Y + 1, MODDED_LIQUID_Z)
                            .getMaterial() == Material.water);
                check(
                    "an item in an oil-like Forge fluid does not receive water buoyancy",
                    moddedLiquidItem.isEntityAlive() && moddedLiquidItem.posY <= moddedLiquidStartY + 0.2D);
                break;

            default:
                break;
        }
    }

    private static void checkConfigEventBus() {
        try {
            Configuration config = config();
            String key = "Default Server language";
            config.get("util", key, "en_us")
                .set("de_de");

            FMLCommonHandler.instance()
                .bus()
                .post(new ConfigChangedEvent.OnConfigChangedEvent("floatingitems", null, false, false));
            check("a ConfigChangedEvent on the FML bus re-syncs FIConfig (B-10)", "de_de".equals(FIConfig.serverLang));

            config.get("util", key, "en_us")
                .set("en_us");
            FIConfig.syncConfig(false);
        } catch (Throwable t) {
            check("the config event test could run: " + t, false);
        }
    }

    private static void resetItemBlacklistConfig() {
        setItemBlacklistConfig(new String[0]);
    }

    private static void setItemBlacklistConfig(String... entries) {
        Configuration config = config();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST_KEY, new String[0])
            .set(entries);
        FMLCommonHandler.instance()
            .bus()
            .post(new ConfigChangedEvent.OnConfigChangedEvent(FIReference.MOD_ID, null, false, false));
    }

    private static Configuration config() {
        try {
            Field configField = FIConfig.class.getDeclaredField("config");
            configField.setAccessible(true);
            return (Configuration) configField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not access FIConfig.config", e);
        }
    }

    private static boolean nbtRoundTrip(ItemStack stack) {
        EntityItem original = new EntityItem(world, 0.5D, WATER_START_Y, 0.5D, stack);
        NBTTagCompound entityTag = new NBTTagCompound();
        original.writeEntityToNBT(entityTag);

        EntityItem loaded = new EntityItem(world);
        loaded.readEntityFromNBT(entityTag);
        ItemStack loadedStack = loaded.getEntityItem();
        return loadedStack != null && loadedStack.hasTagCompound()
            && "kept".equals(
                loadedStack.getTagCompound()
                    .getString("floatingItemsTest"))
            && "FloatingItems NBT".equals(loadedStack.getDisplayName());
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (condition) {
            System.out.println(PASS + description);
        } else {
            failures++;
            System.out.println(FAIL + description);
        }
    }

    private static void finish() {
        if (finished) {
            return;
        }
        finished = true;
        running = false;
        clearItems();
        try {
            resetItemBlacklistConfig();
        } catch (Throwable t) {
            failures++;
            checks++;
            System.out.println(FAIL + "the game-test config was reset: " + t);
        }
        if (chunkTicket != null) {
            ForgeChunkManager.releaseTicket(chunkTicket);
            chunkTicket = null;
        }

        int passed = checks - failures;
        System.out.println("[FI-GAMETEST] " + passed + "/" + checks + " checks passed");
        if (failures > 0) {
            System.out.println(GameTestMod.FAIL_MARKER + ": " + failures + " assertion(s) failed, see above");
            GameTestMod.exit(1);
        } else {
            System.out.println(GameTestMod.OK_MARKER);
            GameTestMod.exit(0);
        }
    }

    /**
     * Creates a {@link FIConfig.FIConfigItem} from its on-disk string form. The constructor is package-private in
     * production; the game test lives in a subpackage and goes through reflection rather than widening the API.
     */
    private static FIConfig.FIConfigItem nullItemConfigItem() {
        try {
            Constructor<FIConfig.FIConfigItem> constructor = FIConfig.FIConfigItem.class
                .getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance("{99999}(0)");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not create a null-item FIConfigItem", e);
        }
    }

    private static FIConfig.FIConfigItem configItem(String entry) {
        try {
            Constructor<FIConfig.FIConfigItem> constructor = FIConfig.FIConfigItem.class
                .getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(entry);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not create FIConfigItem(" + entry + ")", e);
        }
    }

    /** A Forge fluid with water material and an oil-like name, used to prove the fluid policy wins. */
    private static final class GameTestLiquidBlock extends Block implements IFluidBlock {

        GameTestLiquidBlock() {
            super(Material.water);
            setBlockName("floatingItemsGameTestLiquid");
        }

        @Override
        public Fluid getFluid() {
            return MODDED_LIQUID_FLUID;
        }

        @Override
        public FluidStack drain(World world, int x, int y, int z, boolean doDrain) {
            return null;
        }

        @Override
        public boolean canDrain(World world, int x, int y, int z) {
            return false;
        }

        @Override
        public float getFilledPercentage(World world, int x, int y, int z) {
            return 1.0F;
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World worldIn, int x, int y, int z) {
            return null;
        }

        @Override
        public boolean isOpaqueCube() {
            return false;
        }

        @Override
        public boolean renderAsNormalBlock() {
            return false;
        }
    }

    /** Counts vanilla's {@code onEntityItemUpdate} calls; the mixin currently calls it a second time. */
    private static final class CountingItem extends Item {

        @Override
        public boolean onEntityItemUpdate(EntityItem entityItem) {
            COUNTING_CALLS.incrementAndGet();
            return false;
        }
    }
}
