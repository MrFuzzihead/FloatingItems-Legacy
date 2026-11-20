package net.coolsimulations.FloatingItems.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.coolsimulations.FloatingItems.FIConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;

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

    private static final String PASS = "[FI-GAMETEST] ok   ";
    private static final String FAIL = "[FI-GAMETEST] FAIL ";

    private static final AtomicInteger COUNTING_CALLS = new AtomicInteger();
    private static final Item COUNTING_ITEM = new CountingItem();

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
    private static double waterStartY;
    private static double negativeStartY;
    private static double phaseStartY;
    private static ForgeChunkManager.Ticket chunkTicket;

    private FloatingItemsGameTests() {}

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

            default:
                break;
        }
    }

    private static void checkConfigEventBus() {
        try {
            Field configField = FIConfig.class.getDeclaredField("config");
            configField.setAccessible(true);
            Configuration config = (Configuration) configField.get(null);
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

    /** Counts vanilla's {@code onEntityItemUpdate} calls; the mixin currently calls it a second time. */
    private static final class CountingItem extends Item {

        @Override
        public boolean onEntityItemUpdate(EntityItem entityItem) {
            COUNTING_CALLS.incrementAndGet();
            return false;
        }
    }
}
