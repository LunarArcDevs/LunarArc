package org.bukkit.craftbukkit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.papermc.paper.world.MoonPhase;
import io.lunararcdevs.lunararc.common.bridge.access.PrimaryLevelDataAccessBridge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Consumer;
import org.bukkit.block.BlockState;

public class CraftWorld implements World {
    private final java.util.concurrent.ConcurrentMap<Long, java.util.Set<org.bukkit.plugin.Plugin>> lunararcPluginChunkTickets = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Long> lunararcHeldChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> lunararcSpawnHeldChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private CraftChunk lunararcChunk(int x, int z) {
        // CraftChunk is a view over the loader-owned LevelChunk. Do not retain every
        // wrapper ever requested; high-range plugins such as RTP can touch tens of
        // thousands of unique chunks during one session.
        return new CraftChunk(this, x, z);
    }

    /** Plugin chunk ticket: keeps the chunk loaded until explicitly removed. Flags are distinct from the vanilla ticket types. */
    private static final net.minecraft.server.level.TicketType LUNARARC_PLUGIN_TICKET = new net.minecraft.server.level.TicketType(
            net.minecraft.server.level.TicketType.NO_TIMEOUT,
            net.minecraft.server.level.TicketType.FLAG_LOADING | net.minecraft.server.level.TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED);

    private void lunararc$addPluginRegionTicket(int x, int z) {
        net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x, z);
        world.getChunkSource().addTicketWithRadius(LUNARARC_PLUGIN_TICKET, pos, 0);
    }

    private void lunararc$removePluginRegionTicketIfUnused(int x, int z) {
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        if (lunararcHeldChunks.contains(key) || lunararcSpawnHeldChunks.contains(key)) return;
        java.util.Set<org.bukkit.plugin.Plugin> plugins = lunararcPluginChunkTickets.get(key);
        if (plugins != null && !plugins.isEmpty()) return;
        net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x, z);
        world.getChunkSource().removeTicketWithRadius(LUNARARC_PLUGIN_TICKET, pos, 0);
    }

    private volatile io.papermc.paper.configuration.WorldConfiguration paperConfiguration;
    private final ServerLevel world;
    private final String name;
    private final UUID uid;
    private final org.bukkit.generator.ChunkGenerator lunararcGenerator;
    private final org.bukkit.generator.BiomeProvider lunararcBiomeProvider;
    private final CraftWorldBorder worldBorder;
    private final org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer persistentDataContainer;
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.CopyOnWriteArrayList<org.bukkit.metadata.MetadataValue>> metadata = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Boolean lunararcPvpOverride;
    private volatile Integer lunararcViewDistance;
    private volatile Integer lunararcSimulationDistance;
    private volatile Integer lunararcSendViewDistance;
    private volatile boolean lunararcKeepSpawnInMemory = true;
    private volatile double lunararcVoidDamageMinBuildHeightOffset = 0.0D;
    private volatile float lunararcVoidDamageAmount = 4.0F;
    private volatile boolean lunararcVoidDamageEnabled = true;
    private final java.util.concurrent.ConcurrentMap<org.bukkit.entity.SpawnCategory, Integer> lunararcSpawnLimits = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<org.bukkit.entity.SpawnCategory, Long> lunararcTicksPerSpawn = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<org.bukkit.generator.BlockPopulator> lunararcPopulators = new java.util.concurrent.CopyOnWriteArrayList<>();

    public CraftWorld(ServerLevel world) {
        this(world, defaultWorldName(world), null, null);
    }

    public CraftWorld(ServerLevel world, String name, @Nullable org.bukkit.generator.ChunkGenerator generator,
            @Nullable org.bukkit.generator.BiomeProvider biomeProvider) {
        this.world = java.util.Objects.requireNonNull(world, "world");
        this.name = java.util.Objects.requireNonNull(name, "name");
        this.lunararcGenerator = generator;
        this.lunararcBiomeProvider = biomeProvider;
        this.uid = loadOrCreateWorldUid(world, name);
        this.paperConfiguration = loadPaperConfiguration();
        this.worldBorder = new CraftWorldBorder(this);
        this.persistentDataContainer = org.bukkit.craftbukkit.persistence.CraftWorldPersistentData.get(world).container();
        // CraftBukkit assigns Level.world as the world is created; plugins read that field
        // reflectively, so it must be set before anyone can observe the level.
        ((io.lunararcdevs.lunararc.common.bridge.LevelBridge) world).lunararc$attachBukkitWorld(this);
    }

    public io.papermc.paper.configuration.WorldConfiguration getPaperConfiguration() {
        return paperConfiguration;
    }

    public io.papermc.paper.configuration.WorldConfiguration loadPaperConfiguration() {
        try {
            return io.papermc.paper.configuration.PaperConfigurations.loadWorldConfiguration(
                    java.nio.file.Path.of("config", "paper-world-defaults.yml"), getWorldFolder().toPath());
        } catch (java.io.IOException error) {
            org.slf4j.LoggerFactory.getLogger("LunarArc").warn("Cannot load Paper settings for world {}: {}. "
                    + "Using built-in defaults for this world until it is fixed.", name, error.getMessage());
            return io.papermc.paper.configuration.PaperConfigurations.parseWorldConfiguration(
                    new org.bukkit.configuration.file.YamlConfiguration());
        }
    }

    public void applyPaperConfiguration(io.papermc.paper.configuration.WorldConfiguration configuration) {
        this.paperConfiguration = java.util.Objects.requireNonNull(configuration);
        io.lunararcdevs.lunararc.common.server.LunarArcAntiXrayEngine.invalidate(world);
    }

    private static String defaultWorldName(ServerLevel world) {
        String dim = world.dimension().identifier().toString();
        return switch (dim) {
            case "minecraft:overworld" -> "world";
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> world.dimension().identifier().getPath();
        };
    }

    private static UUID loadOrCreateWorldUid(ServerLevel world, String bukkitName) {
        java.nio.file.Path folder = null;
        try {
            folder = ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) (Object) world)
                    .lunararc$getDimensionFolder();
        } catch (Throwable unavailable) {
            // The mixin that captures it did not apply; the legacy path below still answers.
        }
        return loadOrCreateWorldUid(folder, bukkitName, world.dimension().identifier().toString());
    }

    public static UUID loadOrCreateWorldUid(java.nio.file.Path dimensionFolder, String bukkitName,
                                            String dimensionFallback) {
        java.nio.file.Path preferred = dimensionFolder == null ? null : dimensionFolder.resolve("uid.dat");
        java.nio.file.Path legacy = java.nio.file.Path.of(bukkitName, "uid.dat");
        try {
            UUID current = readWorldUid(preferred);
            if (current != null) return current;

            UUID inherited = readWorldUid(legacy);
            if (inherited != null) {
                // Compared as real locations, not as strings. For the overworld the two are the
                // same file reached two ways - an absolute path from the level directory, and
                // "world/uid.dat" relative to the working directory - and Path.equals calls those
                // different, which would have deleted the file immediately after writing it and
                // handed the overworld a new UUID on the next boot.
                if (preferred != null && !sameUidFile(preferred, legacy)) {
                    writeWorldUid(preferred, inherited);
                    discardStrayUidFolder(legacy);
                }
                return inherited;
            }

            UUID generated = UUID.randomUUID();
            writeWorldUid(preferred != null ? preferred : legacy, generated);
            return generated;
        } catch (Throwable ignored) {
            return UUID.nameUUIDFromBytes(dimensionFallback.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** As above, for callers that do not yet have a level and so know only the world's own folder. */
    public static UUID loadOrCreateWorldUid(String bukkitName, String dimensionFallback) {
        return loadOrCreateWorldUid(null, bukkitName, dimensionFallback);
    }

    private static UUID readWorldUid(java.nio.file.Path uidPath) throws java.io.IOException {
        if (uidPath == null || !java.nio.file.Files.isRegularFile(uidPath)) return null;
        byte[] bytes = java.nio.file.Files.readAllBytes(uidPath);
        if (bytes.length < 16) return null;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    private static void writeWorldUid(java.nio.file.Path uidPath, UUID uid) throws java.io.IOException {
        java.nio.file.Path parent = uidPath.getParent();
        if (parent != null) java.nio.file.Files.createDirectories(parent);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(16);
        buf.putLong(uid.getMostSignificantBits()).putLong(uid.getLeastSignificantBits());
        java.nio.file.Files.write(uidPath, buf.array());
    }

    private static boolean sameUidFile(java.nio.file.Path a, java.nio.file.Path b) {
        try {
            if (java.nio.file.Files.exists(a) && java.nio.file.Files.exists(b)) {
                return java.nio.file.Files.isSameFile(a, b);
            }
        } catch (java.io.IOException ignored) {
            // Fall through to the path comparison.
        }
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private static void discardStrayUidFolder(java.nio.file.Path legacyUid) {
        try {
            java.nio.file.Files.deleteIfExists(legacyUid);
            java.nio.file.Path folder = legacyUid.getParent();
            if (folder == null) return;
            // Only if uid.dat was all it held. Anything else in there is somebody's world.
            try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(folder)) {
                if (entries.findAny().isPresent()) return;
            }
            java.nio.file.Files.deleteIfExists(folder);
        } catch (Throwable ignored) {
            // A leftover directory is untidy, not broken.
        }
    }

    // Derived from an immutable dimension key, so it never changes for a given world. It used to
    // be recomputed - a fresh String, a fresh byte[] and an MD5 digest - on every call, and
    // CraftServer.craftWorld() calls it for each world lookup, including the one every chunk
    // decoration makes on the worldgen threads. Compute it once instead.
    private volatile UUID lunararcLegacyDimensionUid;

    public UUID getLegacyDimensionUID() {
        UUID cached = lunararcLegacyDimensionUid;
        if (cached != null) return cached;
        cached = UUID.nameUUIDFromBytes(
                world.dimension().identifier().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        lunararcLegacyDimensionUid = cached;
        return cached;
    }

    public ServerLevel getHandle() {
        return world;
    }


    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public @NotNull UUID getUID() {
        return uid;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        net.minecraft.resources.Identifier loc = world.dimension().identifier();
        return new NamespacedKey(loc.getNamespace(), loc.getPath());
    }

    @Override
    public @NotNull Block getBlockAt(int x, int y, int z) {
        return CraftBlock.create(world, new net.minecraft.core.BlockPos(x, y, z));
    }

    @Override
    public @NotNull Block getBlockAt(@NotNull Location location) {
        return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    @Override
    public int getHighestBlockYAt(int x, int z, @NotNull HeightMap heightMap) {
        Heightmap.Types nmsType = switch (heightMap) {
            case MOTION_BLOCKING -> Heightmap.Types.MOTION_BLOCKING;
            case MOTION_BLOCKING_NO_LEAVES -> Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
            case OCEAN_FLOOR -> Heightmap.Types.OCEAN_FLOOR;
            case WORLD_SURFACE -> Heightmap.Types.WORLD_SURFACE;
            default -> Heightmap.Types.MOTION_BLOCKING;
        };
        return world.getHeight(nmsType, x, z);
    }

    @Override
    public int getHighestBlockYAt(@NotNull Location location) {
        return getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
    }

    @Override
    public int getHighestBlockYAt(@NotNull Location location, @NotNull HeightMap heightMap) {
        return getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), heightMap);
    }

    @Override
    public @NotNull Block getHighestBlockAt(int x, int z) {
        return getBlockAt(x, getHighestBlockYAt(x, z), z);
    }

    @Override
    public @NotNull Block getHighestBlockAt(int x, int z, @NotNull HeightMap heightMap) {
        return getBlockAt(x, getHighestBlockYAt(x, z, heightMap), z);
    }

    @Override
    public @NotNull Block getHighestBlockAt(@NotNull Location location) {
        return getHighestBlockAt(location.getBlockX(), location.getBlockZ());
    }

    @Override
    public @NotNull Block getHighestBlockAt(@NotNull Location location, @NotNull HeightMap heightMap) {
        return getHighestBlockAt(location.getBlockX(), location.getBlockZ(), heightMap);
    }

    @Override
    public @NotNull Chunk getChunkAt(int x, int z) {
        world.getChunk(x, z);
        return lunararcChunk(x, z);
    }

    @Override
    public @NotNull Chunk getChunkAt(int x, int z, boolean generate) {
        if (!generate && world.getChunkSource().getChunkNow(x, z) == null) {
            return lunararcChunk(x, z);
        }
        world.getChunk(x, z);
        return lunararcChunk(x, z);
    }

    @Override
    public @NotNull Chunk getChunkAt(@NotNull Location location) {
        return getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public @NotNull Chunk getChunkAt(@NotNull Block block) {
        return getChunkAt(block.getX() >> 4, block.getZ() >> 4);
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        return world.getChunkSource().getChunkNow(x, z) != null;
    }

    @Override
    public boolean isChunkLoaded(@NotNull org.bukkit.Chunk chunk) {


        if (chunk == null) return false;
        return chunk.getWorld() == this && isChunkLoaded(chunk.getX(), chunk.getZ());
    }

    @Override
    public boolean refreshChunk(int x, int z) {
        if (!isChunkLoaded(x, z)) return false;
        net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunkSource().getChunkNow(x, z);
        if (chunk == null) return false;
        try {
            net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket packet =
                    new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                            chunk, world.getLightEngine(), null, null);
            for (org.bukkit.entity.Player player : getPlayersSeeingChunk(x, z)) {
                if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft
                        && craft.getHandle().connection != null) {
                    craft.getHandle().connection.send(packet);
                }
            }
        } catch (Throwable ignored) {


        }
        return true;
    }

    @Override
    public void loadChunk(int x, int z) {
        loadChunk(x, z, true);
    }

    @Override
    public void loadChunk(@NotNull org.bukkit.Chunk chunk) {
        if (chunk.getWorld() != this) throw new IllegalArgumentException("Chunk belongs to another world");
        loadChunk(chunk.getX(), chunk.getZ());
    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {
        if (!generate && world.getChunkSource().getChunkNow(x, z) == null && !isChunkGenerated(x, z)) return false;
        // Bukkit loadChunk uses a transient loader ticket, not the persistent
        // /forceload set. This keeps the chunk resident until unloadChunk releases
        // LunarArc's ownership without permanently pinning RTP/pregen destinations.
        world.getChunk(x, z);
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        if (lunararcHeldChunks.add(key)) lunararc$addPluginRegionTicket(x, z);
        return true;
    }

    @Override
    public boolean unloadChunk(int x, int z) {
        return unloadChunk(x, z, true);
    }

    @Override
    public boolean hasStructureAt(@NotNull io.papermc.paper.math.Position position,
            @NotNull org.bukkit.generator.structure.Structure structure) {
        java.util.Objects.requireNonNull(position, "position");
        java.util.Objects.requireNonNull(structure, "structure");
        int chunkX = ((int) Math.floor(position.blockX())) >> 4;
        int chunkZ = ((int) Math.floor(position.blockZ())) >> 4;
        return !getStructures(chunkX, chunkZ, structure).isEmpty();
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save) {
        boolean wasLoaded = isChunkLoaded(x, z);
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        if (lunararcHeldChunks.remove(key)) lunararc$removePluginRegionTicketIfUnused(x, z);
        return wasLoaded;
    }

    @Override
    public boolean unloadChunk(@NotNull org.bukkit.Chunk chunk) {
        java.util.Objects.requireNonNull(chunk, "chunk");
        if (chunk.getWorld() != this) throw new IllegalArgumentException("Chunk belongs to another world");
        return unloadChunk(chunk.getX(), chunk.getZ(), true);
    }

    @Override
    public boolean unloadChunkRequest(int x, int z) {
        return unloadChunk(x, z, true);
    }

    @Override
    public boolean isChunkInUse(int x, int z) {
        if (!isChunkLoaded(x, z)) return false;
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        return !getPlayersSeeingChunk(x, z).isEmpty() || lunararcHeldChunks.contains(key)
                || lunararcSpawnHeldChunks.contains(key) || !getPluginChunkTickets(x, z).isEmpty();
    }

    @Override
    public boolean isChunkGenerated(int x, int z) {
        return world.hasChunk(x, z);
    }

    @Override
    public boolean regenerateChunk(int x, int z) {
        throw new UnsupportedOperationException("Chunk regeneration is not supported in Minecraft 1.21.11");
    }

    @Override
    public @NotNull org.bukkit.Chunk[] getLoadedChunks() {
        // Read the loader-owned visible chunk map instead of using a permanent
        // CraftChunk wrapper cache or reconstructing an approximation from players.
        return ((io.lunararcdevs.lunararc.common.bridge.access.ChunkMapAccessBridge)
                world.getChunkSource().chunkMap).lunararc$getVisibleChunkMap().values().stream()
                .map(net.minecraft.server.level.ChunkHolder::getPos)
                .map(pos -> world.getChunkSource().getChunkNow(pos.x, pos.z))
                .filter(java.util.Objects::nonNull)
                .map(chunk -> (org.bukkit.Chunk) new CraftChunk(chunk, this))
                .toArray(org.bukkit.Chunk[]::new);
    }

    @Override
    public @NotNull List<Entity> getEntities() {
        List<Entity> result = new ArrayList<>();
        for (net.minecraft.world.entity.Entity nmsEntity : world.getAllEntities()) {
            try {
                org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) nmsEntity).lunararc$getBukkitEntity();
                if (bukkitEntity != null) result.add(bukkitEntity);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    @Override
    public boolean generateTree(Location location, TreeType type) {
        return generateTree(location, new Random(), type);
    }

    @Override
    public boolean generateTree(Location location, TreeType type, BlockChangeDelegate delegate) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(delegate, "delegate");
        org.bukkit.craftbukkit.util.BlockStateListPopulator populator =
                new org.bukkit.craftbukkit.util.BlockStateListPopulator(world);
        boolean generated = generateTreeCaptured(location, new Random(), type, populator);
        if (!generated) return false;
        for (var captured : populator.getCapturedStates()) {
            org.bukkit.block.BlockState state = captured.state();
            if (!delegate.setBlockData(state.getX(), state.getY(), state.getZ(), state.getBlockData())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean generateTree(Location location, Random random, TreeType type) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(random, "random");
        java.util.Objects.requireNonNull(type, "type");
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Tree location belongs to another world");
        }

        net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> feature = switch (type) {
            case BIG_TREE -> net.minecraft.data.worldgen.features.TreeFeatures.FANCY_OAK;
            case BIRCH -> net.minecraft.data.worldgen.features.TreeFeatures.BIRCH;
            case REDWOOD -> net.minecraft.data.worldgen.features.TreeFeatures.SPRUCE;
            case TALL_REDWOOD -> net.minecraft.data.worldgen.features.TreeFeatures.PINE;
            case JUNGLE -> net.minecraft.data.worldgen.features.TreeFeatures.MEGA_JUNGLE_TREE;
            case SMALL_JUNGLE -> net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_TREE_NO_VINE;
            case COCOA_TREE -> net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_TREE;
            case JUNGLE_BUSH -> net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_BUSH;
            case RED_MUSHROOM -> net.minecraft.data.worldgen.features.TreeFeatures.HUGE_RED_MUSHROOM;
            case BROWN_MUSHROOM -> net.minecraft.data.worldgen.features.TreeFeatures.HUGE_BROWN_MUSHROOM;
            case SWAMP -> net.minecraft.data.worldgen.features.TreeFeatures.SWAMP_OAK;
            case ACACIA -> net.minecraft.data.worldgen.features.TreeFeatures.ACACIA;
            case DARK_OAK -> net.minecraft.data.worldgen.features.TreeFeatures.DARK_OAK;
            case MEGA_REDWOOD -> net.minecraft.data.worldgen.features.TreeFeatures.MEGA_SPRUCE;
            case MEGA_PINE -> net.minecraft.data.worldgen.features.TreeFeatures.MEGA_PINE;
            case TALL_BIRCH -> net.minecraft.data.worldgen.features.TreeFeatures.SUPER_BIRCH_BEES_0002;
            case CRIMSON_FUNGUS -> net.minecraft.data.worldgen.features.TreeFeatures.CRIMSON_FUNGUS_PLANTED;
            case WARPED_FUNGUS -> net.minecraft.data.worldgen.features.TreeFeatures.WARPED_FUNGUS_PLANTED;
            case AZALEA -> net.minecraft.data.worldgen.features.TreeFeatures.AZALEA_TREE;
            case MANGROVE -> net.minecraft.data.worldgen.features.TreeFeatures.MANGROVE;
            case TALL_MANGROVE -> net.minecraft.data.worldgen.features.TreeFeatures.TALL_MANGROVE;
            case CHERRY -> net.minecraft.data.worldgen.features.TreeFeatures.CHERRY;
            case CHORUS_PLANT -> {
                net.minecraft.world.level.block.ChorusFlowerBlock.generatePlant(
                        world,
                        new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                        new org.bukkit.craftbukkit.util.RandomSourceWrapper(random),
                        8);
                yield null;
            }
            case TREE -> net.minecraft.data.worldgen.features.TreeFeatures.OAK;
            default -> net.minecraft.data.worldgen.features.TreeFeatures.OAK;
        };
        if (feature == null) return true;
        return placeConfiguredTree(feature, world, random, location);
    }

    private boolean generateTreeCaptured(Location location, Random random, TreeType type,
            net.minecraft.world.level.WorldGenLevel access) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> feature = switch (type) {
            case BIG_TREE -> net.minecraft.data.worldgen.features.TreeFeatures.FANCY_OAK;
            case BIRCH -> net.minecraft.data.worldgen.features.TreeFeatures.BIRCH;
            case REDWOOD -> net.minecraft.data.worldgen.features.TreeFeatures.SPRUCE;
            case TALL_REDWOOD -> net.minecraft.data.worldgen.features.TreeFeatures.PINE;
            case JUNGLE -> net.minecraft.data.worldgen.features.TreeFeatures.MEGA_JUNGLE_TREE;
            case SMALL_JUNGLE -> net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_TREE_NO_VINE;
            case COCOA_TREE -> net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_TREE;
            case JUNGLE_BUSH -> net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_BUSH;
            case RED_MUSHROOM -> net.minecraft.data.worldgen.features.TreeFeatures.HUGE_RED_MUSHROOM;
            case BROWN_MUSHROOM -> net.minecraft.data.worldgen.features.TreeFeatures.HUGE_BROWN_MUSHROOM;
            case SWAMP -> net.minecraft.data.worldgen.features.TreeFeatures.SWAMP_OAK;
            case ACACIA -> net.minecraft.data.worldgen.features.TreeFeatures.ACACIA;
            case DARK_OAK -> net.minecraft.data.worldgen.features.TreeFeatures.DARK_OAK;
            case MEGA_REDWOOD -> net.minecraft.data.worldgen.features.TreeFeatures.MEGA_SPRUCE;
            case MEGA_PINE -> net.minecraft.data.worldgen.features.TreeFeatures.MEGA_PINE;
            case TALL_BIRCH -> net.minecraft.data.worldgen.features.TreeFeatures.SUPER_BIRCH_BEES_0002;
            case CRIMSON_FUNGUS -> net.minecraft.data.worldgen.features.TreeFeatures.CRIMSON_FUNGUS_PLANTED;
            case WARPED_FUNGUS -> net.minecraft.data.worldgen.features.TreeFeatures.WARPED_FUNGUS_PLANTED;
            case AZALEA -> net.minecraft.data.worldgen.features.TreeFeatures.AZALEA_TREE;
            case MANGROVE -> net.minecraft.data.worldgen.features.TreeFeatures.MANGROVE;
            case TALL_MANGROVE -> net.minecraft.data.worldgen.features.TreeFeatures.TALL_MANGROVE;
            case CHERRY -> net.minecraft.data.worldgen.features.TreeFeatures.CHERRY;
            case TREE -> net.minecraft.data.worldgen.features.TreeFeatures.OAK;
            case CHORUS_PLANT -> {
                net.minecraft.world.level.block.ChorusFlowerBlock.generatePlant(
                        access, new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                        new org.bukkit.craftbukkit.util.RandomSourceWrapper(random), 8);
                yield null;
            }
            default -> net.minecraft.data.worldgen.features.TreeFeatures.OAK;
        };
        if (feature == null) return true;
        return placeConfiguredTree(feature, access, random, location);
    }

    private boolean placeConfiguredTree(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> feature,
            net.minecraft.world.level.WorldGenLevel access, Random random, Location location) {
        net.minecraft.core.Holder<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> holder =
                world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                        .get(feature).orElse(null);
        if (holder == null) return false;
        return holder.value().place(
                access, world.getChunkSource().getGenerator(),
                new org.bukkit.craftbukkit.util.RandomSourceWrapper(random),
                new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    @Override
    public boolean generateTree(Location location, Random random, TreeType type,
            Predicate<? super BlockState> statePredicate) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(random, "random");
        java.util.Objects.requireNonNull(type, "type");
        org.bukkit.craftbukkit.util.BlockStateListPopulator populator =
                new org.bukkit.craftbukkit.util.BlockStateListPopulator(world);
        boolean generated = generateTreeCaptured(location, random, type, populator);
        populator.placeSomeBlocks(statePredicate, null);
        return generated;
    }

    @Override
    public boolean generateTree(Location location, Random random, TreeType type,
            Consumer<? super BlockState> stateConsumer) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(random, "random");
        java.util.Objects.requireNonNull(type, "type");
        org.bukkit.craftbukkit.util.BlockStateListPopulator populator =
                new org.bukkit.craftbukkit.util.BlockStateListPopulator(world);
        boolean generated = generateTreeCaptured(location, random, type, populator);
        populator.placeSomeBlocks(null, stateConsumer);
        return generated;
    }

    @Override
    public void setType(int x, int y, int z, @NotNull Material type) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.withDefaultNamespace(
                type.name().toLowerCase(java.util.Locale.ROOT));
        net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(key);
        if (block != null) world.setBlock(new net.minecraft.core.BlockPos(x, y, z), block.defaultBlockState(), 3);
    }

    @Override
    public void setType(@NotNull Location location, @NotNull Material type) {
        setType(location.getBlockX(), location.getBlockY(), location.getBlockZ(), type);
    }

    @Override
    public void setBlockData(int x, int y, int z, @NotNull org.bukkit.block.data.BlockData blockData) {
        net.minecraft.world.level.block.state.BlockState nms;
        if (blockData instanceof org.bukkit.craftbukkit.block.data.CraftBlockData craftData) {
            nms = craftData.getState();
        } else {
            nms = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getValue(net.minecraft.resources.Identifier.withDefaultNamespace(
                            blockData.getMaterial().name().toLowerCase(java.util.Locale.ROOT)))
                    .defaultBlockState();
        }
        world.setBlock(new net.minecraft.core.BlockPos(x, y, z), nms, 3);
    }

    @Override
    public void setBlockData(@NotNull Location location, @NotNull org.bukkit.block.data.BlockData blockData) {
        setBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ(), blockData);
    }

    @Override
    public @NotNull Material getType(int x, int y, int z) {
        net.minecraft.world.level.block.state.BlockState state =
                world.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
        net.minecraft.resources.Identifier key =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return Material.AIR;
        try {
            return Material.valueOf(key.getPath().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.AIR;
        }
    }

    @Override
    public @NotNull Material getType(@NotNull Location location) {
        return getType(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public @NotNull org.bukkit.block.data.BlockData getBlockData(int x, int y, int z) {
        return new org.bukkit.craftbukkit.block.data.CraftBlockData(
                world.getBlockState(new net.minecraft.core.BlockPos(x, y, z)));
    }

    @Override
    public @NotNull org.bukkit.block.data.BlockData getBlockData(@NotNull Location location) {
        return getBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public @NotNull io.papermc.paper.block.fluid.FluidData getFluidData(int x, int y, int z) {
        return new org.bukkit.craftbukkit.block.fluid.CraftFluidData(
                world.getFluidState(new net.minecraft.core.BlockPos(x, y, z)));
    }

    @Override
    public @NotNull io.papermc.paper.block.fluid.FluidData getFluidData(@NotNull Location location) {
        return getFluidData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public @NotNull BlockState getBlockState(int x, int y, int z) {
        return CraftBlock.create(world, new net.minecraft.core.BlockPos(x, y, z)).getState();
    }

    @Override
    public @NotNull BlockState getBlockState(@NotNull Location location) {
        return getBlockState(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public void setBiome(int x, int y, int z, @NotNull org.bukkit.block.Biome biome) {
        java.util.Objects.requireNonNull(biome, "biome");
        org.bukkit.NamespacedKey bukkitKey = biome.getKey();
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(bukkitKey.toString());
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME, id);
        net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> registry = world.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.BIOME);
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = registry.get(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown biome " + bukkitKey));

        net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunk(x >> 4, z >> 4);
        int sectionIndex = chunk.getSectionIndex(y);
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            throw new IllegalArgumentException("Y coordinate outside world bounds: " + y);
        }
        net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(sectionIndex);
        net.minecraft.world.level.chunk.PalettedContainerRO<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes = section.getBiomes();
        if (!(biomes instanceof net.minecraft.world.level.chunk.PalettedContainer<?> raw)) {
            throw new IllegalStateException("Biome palette is not mutable");
        }
        @SuppressWarnings("unchecked")
        net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> mutable =
                (net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>) raw;
        mutable.set((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3, holder);
        chunk.markUnsaved();
    }

    @Override
    public @NotNull org.bukkit.block.Biome getBiome(int x, int y, int z) {
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = world.getBiome(new net.minecraft.core.BlockPos(x, y, z));
        net.minecraft.resources.Identifier id = holder.unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
        if (id != null) {
            org.bukkit.block.Biome biome = org.bukkit.Registry.BIOME.get(new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()));
            if (biome != null) return biome;
        }
        org.bukkit.block.Biome plains = org.bukkit.Registry.BIOME.get(org.bukkit.NamespacedKey.minecraft("plains"));
        if (plains == null) throw new IllegalStateException("minecraft:plains biome is unavailable");
        return plains;
    }

    @Override
    public @NotNull org.bukkit.block.Biome getComputedBiome(int x, int y, int z) {
        return getBiome(x, y, z);
    }

    @Override
    public @NotNull org.bukkit.block.Biome getBiome(@NotNull Location location) {
        java.util.Objects.requireNonNull(location, "location");
        return getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public void setBiome(@NotNull Location location, @NotNull org.bukkit.block.Biome biome) {
        java.util.Objects.requireNonNull(location, "location");
        setBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ(), biome);
    }

    @Override
    public <T extends org.bukkit.entity.AbstractArrow> @NotNull T spawnArrow(@NotNull Location location,
            @NotNull Vector direction, float speed, float spread, @NotNull Class<T> clazz) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(direction, "direction");
        java.util.Objects.requireNonNull(clazz, "clazz");
        EntityType type = org.bukkit.entity.SpectralArrow.class.isAssignableFrom(clazz)
                ? EntityType.SPECTRAL_ARROW
                : EntityType.ARROW;
        org.bukkit.entity.Entity spawned = spawnEntity(location, type);
        if (!clazz.isInstance(spawned)) {
            throw new IllegalArgumentException("Cannot spawn arrow class " + clazz.getName());
        }
        Vector velocity = direction.clone();
        if (velocity.lengthSquared() > 0) velocity.normalize().multiply(speed);
        spawned.setVelocity(velocity);
        return clazz.cast(spawned);
    }

    @Override
    public @NotNull org.bukkit.entity.Arrow spawnArrow(@NotNull Location location, @NotNull Vector direction,
            float speed, float spread) {
        return spawnArrow(location, direction, speed, spread, org.bukkit.entity.Arrow.class);
    }

    @Override
    public @NotNull List<Player> getPlayers() {
        org.bukkit.craftbukkit.CraftServer server =
                (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        List<Player> result = new ArrayList<>(world.players().size());
        for (net.minecraft.server.level.ServerPlayer nmsPlayer : world.players()) {
            org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(server, nmsPlayer);
            if (bukkit instanceof Player player) result.add(player);
        }
        return java.util.List.copyOf(result);
    }

    @Override
    public int getPlayerCount() {
        return world.players().size();
    }

    @Override
    public int getChunkCount() {
        return world.getChunkSource().getLoadedChunksCount();
    }

    @Override
    public @NotNull Collection<org.bukkit.Chunk> getForceLoadedChunks() {
        java.util.List<org.bukkit.Chunk> chunks = new java.util.ArrayList<>();
        for (long packed : world.getForceLoadedChunks()) {
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(packed);
            chunks.add(getChunkAt(pos.x, pos.z));
        }
        return java.util.List.copyOf(chunks);
    }

    @Override
    public void setChunkForceLoaded(int x, int z, boolean forced) {
        world.setChunkForced(x, z, forced);
    }

    @Override
    public boolean isChunkForceLoaded(int x, int z) {
        return world.getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x, z));
    }

    @Override
    public @NotNull Collection<org.bukkit.entity.Player> getPlayersSeeingChunk(int x, int z) {
        if (!isChunkLoaded(x, z)) return java.util.List.of();
        net.minecraft.world.level.ChunkPos target = new net.minecraft.world.level.ChunkPos(x, z);
        java.util.List<org.bukkit.entity.Player> result = new java.util.ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer nms : world.players()) {
            if (!nms.getChunkTrackingView().contains(target)) continue;
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(nms.getUUID());
            if (player != null) result.add(player);
        }
        return java.util.List.copyOf(result);
    }

    @Override
    public @NotNull Collection<org.bukkit.entity.Player> getPlayersSeeingChunk(@NotNull org.bukkit.Chunk chunk) {
        java.util.Objects.requireNonNull(chunk, "chunk");
        if (chunk.getWorld() != this) throw new IllegalArgumentException("Chunk belongs to another world");
        return getPlayersSeeingChunk(chunk.getX(), chunk.getZ());
    }

    @Override
    public int getEntityCount() {
        int count = 0;
        for (net.minecraft.world.entity.Entity ignored : world.getAllEntities()) count++;
        return count;
    }

    @Override
    public int getTileEntityCount() {
        int count = 0;
        for (org.bukkit.Chunk chunk : getLoadedChunks()) {
            net.minecraft.world.level.chunk.LevelChunk handle = ((CraftChunk) chunk).getHandle();
            count += handle.getBlockEntities().size();
        }
        return count;
    }

    @Override
    public int getTickableTileEntityCount() {


        return getTileEntityCount();
    }

    @Override
    public void setVoidDamageMinBuildHeightOffset(double offset) {
        this.lunararcVoidDamageMinBuildHeightOffset = offset;
    }

    @Override
    public double getVoidDamageMinBuildHeightOffset() {
        return lunararcVoidDamageMinBuildHeightOffset;
    }

    @Override
    public void setVoidDamageAmount(float amount) {
        if (!Float.isFinite(amount) || amount < 0.0F) throw new IllegalArgumentException("Void damage amount must be finite and non-negative");
        this.lunararcVoidDamageAmount = amount;
    }

    @Override
    public float getVoidDamageAmount() {
        return lunararcVoidDamageAmount;
    }

    @Override
    public void setVoidDamageEnabled(boolean enabled) {
        this.lunararcVoidDamageEnabled = enabled;
    }

    @Override
    public boolean isVoidDamageEnabled() {
        return lunararcVoidDamageEnabled;
    }

    @Override
    public boolean hasCollisionsIn(@NotNull BoundingBox boundingBox) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ(),
                boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ());
        return !world.noCollision(box);
    }

    @Override
    public boolean lineOfSightExists(@NotNull Location from, @NotNull Location to) {
        if (from.getWorld() != this || to.getWorld() != this) {
            throw new IllegalArgumentException("Both locations must be in this world");
        }
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(from.getX(), from.getY(), from.getZ());
        net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(to.getX(), to.getY(), to.getZ());
        net.minecraft.world.phys.BlockHitResult hit = world.clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    @Override
    public @NotNull ChunkSnapshot getEmptyChunkSnapshot(int x, int z, boolean includeBiome,
            boolean includeBiomeTempRain) {
        return CraftChunkSnapshot.empty(this, x, z, includeBiome, includeBiomeTempRain);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count) {
        spawnParticle(particle, location, count, 0, 0, 0, 0, null, false);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {
        try {
            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier
                    .parse(sound.name().toLowerCase());
            net.minecraft.sounds.SoundEvent se = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(rl);
            if (se != null) {
                world.playSound(null, location.getX(), location.getY(), location.getZ(),
                        net.minecraft.core.Holder.direct(se),
                        net.minecraft.sounds.SoundSource.MASTER, volume, pitch);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public long getTime() {
        return Math.floorMod(world.getDayTime(), 24000L);
    }

    @Override
    public void setTime(long time) {
        long current = world.getDayTime();
        long currentDay = Math.floorDiv(current, 24000L);
        long requested = Math.floorMod(time, 24000L);
        long next = currentDay * 24000L + requested;
        if (next < current) next += 24000L;
        world.setDayTime(next);
    }

    @Override
    public long getFullTime() {
        return world.getDayTime();
    }

    @Override
    public void setFullTime(long time) {
        world.setDayTime(time);
    }

    @Override
    public boolean hasStorm() {
        return world.getLevelData().isRaining();
    }

    @Override
    public void setStorm(boolean hasStorm) {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            data.setRaining(hasStorm);
            setWeatherDuration(0);
            setClearWeatherDuration(0);
        }
    }

    @Override
    public int getWeatherDuration() {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            return data.getRainTime();
        }
        return 0;
    }

    @Override
    public void setWeatherDuration(int duration) {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            data.setRainTime(Math.max(0, duration));
        }
    }

    @Override
    public boolean isThundering() {
        return world.getLevelData().isThundering();
    }

    @Override
    public void setThundering(boolean thundering) {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            data.setThundering(thundering);
            setThunderDuration(0);
            setClearWeatherDuration(0);
        }
    }

    @Override
    public @NotNull MoonPhase getMoonPhase() {
        MoonPhase[] phases = MoonPhase.values();
        int index = (int) Math.floorMod(getFullTime() / 24000L, 8L);
        return phases[index % phases.length];
    }

    @Override
    public @NotNull Environment getEnvironment() {
        return io.lunararcdevs.lunararc.common.server.LunarArcDynamicBukkitEnums
                .environment(world.dimension().identifier());
    }

    @Override
    public long getSeed() {
        return world.getSeed();
    }

    @Override
    public boolean getPVP() {
        return lunararcPvpOverride != null ? lunararcPvpOverride : world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.PVP);
    }

    @Override
    public void setPVP(boolean pvp) {
        lunararcPvpOverride = pvp;
    }

    @Override
    public @NotNull Difficulty getDifficulty() {
        net.minecraft.world.Difficulty nmsDiff = world.getDifficulty();
        return switch (nmsDiff) {
            case PEACEFUL -> Difficulty.PEACEFUL;
            case EASY -> Difficulty.EASY;
            case HARD -> Difficulty.HARD;
            default -> Difficulty.NORMAL;
        };
    }

    @Override
    public void setDifficulty(@NotNull Difficulty difficulty) {
        if (difficulty == null) throw new IllegalArgumentException("difficulty cannot be null");
        net.minecraft.world.Difficulty nms = switch (difficulty) {
            case PEACEFUL -> net.minecraft.world.Difficulty.PEACEFUL;
            case EASY -> net.minecraft.world.Difficulty.EASY;
            case HARD -> net.minecraft.world.Difficulty.HARD;
            default -> net.minecraft.world.Difficulty.NORMAL;
        };
        // Difficulty is server-wide in vanilla 1.21.11, so this applies to every world.
        world.getServer().setDifficulty(nms, true);
    }

    @Override
    public @NotNull Location getSpawnLocation() {
        net.minecraft.world.level.storage.LevelData.RespawnData respawn = world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data
                ? data.getRespawnData() : world.getRespawnData();
        net.minecraft.core.BlockPos sp = respawn.pos();
        return new Location(this, sp.getX(), sp.getY(), sp.getZ(), respawn.yaw(), respawn.pitch());
    }

    @Override
    public @NotNull WorldBorder getWorldBorder() {
        return worldBorder;
    }

    @Override
    public @NotNull org.bukkit.entity.Item dropItem(@NotNull Location location, @NotNull ItemStack item) {
        return dropItem(location, item, null);
    }

    @Override
    public @NotNull org.bukkit.entity.Item dropItemNaturally(@NotNull Location location, @NotNull ItemStack item) {
        return dropItemNaturally(location, item, null);
    }

    @Override
    public @NotNull org.bukkit.entity.Item dropItemNaturally(@NotNull Location location, @NotNull ItemStack item,
            @Nullable java.util.function.Consumer<? super org.bukkit.entity.Item> function) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(item, "item");
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Location belongs to another world");
        }


        double x = location.getX() + world.random.nextFloat() * 0.5D + 0.25D;
        double y = location.getY() + world.random.nextFloat() * 0.5D + 0.25D;
        double z = location.getZ() + world.random.nextFloat() * 0.5D + 0.25D;
        return dropItem(new Location(this, x, y, z), item, function);
    }

    @Override
    public @NotNull org.bukkit.entity.Item dropItem(@NotNull Location location, @NotNull ItemStack item,
            @Nullable java.util.function.Consumer<? super org.bukkit.entity.Item> function) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(item, "item");
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Location belongs to another world");
        }
        if (item.getType().isAir() || item.getAmount() <= 0) {
            throw new IllegalArgumentException("Cannot drop an empty ItemStack");
        }
        net.minecraft.world.item.ItemStack nmsStack =
                org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        net.minecraft.world.entity.item.ItemEntity nms = new net.minecraft.world.entity.item.ItemEntity(
                world, location.getX(), location.getY(), location.getZ(), nmsStack);
        org.bukkit.craftbukkit.entity.CraftItem bukkit =
                new org.bukkit.craftbukkit.entity.CraftItem(
                        (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer(), nms);
        if (function != null) function.accept(bukkit);
        if (!world.addFreshEntity(nms)) {
            throw new IllegalStateException("Failed to add dropped item to world");
        }
        return bukkit;
    }

    @Override
    public void setSendViewDistance(int distance) {
        if (distance < 2 || distance > 32) throw new IllegalArgumentException("distance must be between 2 and 32");
        lunararcSendViewDistance = distance;
        net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket packet =
                new net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket(distance);
        for (net.minecraft.server.level.ServerPlayer player : world.players()) {
            if (player.connection != null) player.connection.send(packet);
        }
    }

    @Override
    public int getSendViewDistance() {
        return lunararcSendViewDistance != null ? lunararcSendViewDistance : world.getServer().getPlayerList().getViewDistance();
    }

    @Override
    public void setSimulationDistance(int distance) {
        if (distance < 2 || distance > 32) throw new IllegalArgumentException("distance must be between 2 and 32");
        lunararcSimulationDistance = distance;
        world.getChunkSource().setSimulationDistance(distance);
        world.getServer().getPlayerList().broadcastAll(
                new net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket(distance));
    }

    @Override
    public int getSimulationDistance() {
        if (lunararcSimulationDistance != null) return lunararcSimulationDistance;
        // Vanilla DistanceManager#simulationDistance is private in 1.21.1.
        // Until this CraftWorld has a per-world override, the effective value is
        // the server's configured simulation distance.
        return ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getSimulationDistance();
    }

    @Override
    public void setViewDistance(int distance) {
        if (distance < 2 || distance > 32) throw new IllegalArgumentException("distance must be between 2 and 32");
        lunararcViewDistance = distance;
        world.getChunkSource().chunkMap.setServerViewDistance(distance);
        world.getServer().getPlayerList().broadcastAll(
                new net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket(distance));
    }

    @Override
    public int getViewDistance() {
        // serverViewDistance is private on ChunkMap; read it through the accessor bridge rather
        // than directly, which threw IllegalAccessError on respawn (CraftPlayer#getSendViewDistance).
        return lunararcViewDistance != null ? lunararcViewDistance
                : ((io.lunararcdevs.lunararc.common.bridge.access.ChunkMapAccessBridge)
                        (Object) world.getChunkSource().chunkMap).lunararc$getServerViewDistance();
    }

    @Override
    public @NotNull Set<FeatureFlag> getFeatureFlags() {
        return io.papermc.paper.datapack.PaperDatapack.toBukkitFeatures(world.enabledFeatures());
    }

    @Override
    public @Nullable org.bukkit.boss.DragonBattle getEnderDragonBattle() {
        return world.getDragonFight() == null ? null
                : new org.bukkit.craftbukkit.boss.CraftDragonBattle(world.getDragonFight());
    }

    @Override
    public @NotNull List<org.bukkit.Raid> getRaids() {
        io.lunararcdevs.lunararc.common.bridge.world.raid.RaidsBridge raids =
                (io.lunararcdevs.lunararc.common.bridge.world.raid.RaidsBridge) (Object) world.getRaids();
        return raids.lunararc$raids().values().stream()
                .map(org.bukkit.craftbukkit.CraftRaid::new)
                .map(org.bukkit.Raid.class::cast)
                .toList();
    }

    @Override
    public @Nullable org.bukkit.Raid getRaid(int id) {
        io.lunararcdevs.lunararc.common.bridge.world.raid.RaidsBridge raids =
                (io.lunararcdevs.lunararc.common.bridge.world.raid.RaidsBridge) (Object) world.getRaids();
        net.minecraft.world.entity.raid.Raid raid = raids.lunararc$raids().get(id);
        return raid == null ? null : new org.bukkit.craftbukkit.CraftRaid(raid);
    }

    @Override
    public @Nullable org.bukkit.Raid locateNearestRaid(@NotNull Location location, int radius) {
        java.util.Objects.requireNonNull(location, "location");
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        net.minecraft.world.entity.raid.Raid raid = world.getRaids().getNearbyRaid(
                new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                radius * radius);
        return raid == null ? null : new org.bukkit.craftbukkit.CraftRaid(raid);
    }

    @Override
    public @Nullable org.bukkit.util.BiomeSearchResult locateNearestBiome(@NotNull Location origin, int radius,
            int horizontalInterval, int verticalInterval, @NotNull org.bukkit.block.Biome... biomes) {
        java.util.Objects.requireNonNull(origin, "origin");
        java.util.Objects.requireNonNull(biomes, "biomes");
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        if (horizontalInterval <= 0) throw new IllegalArgumentException("horizontalInterval must be > 0");
        if (verticalInterval <= 0) throw new IllegalArgumentException("verticalInterval must be > 0");
        if (biomes.length == 0) return null;
        java.util.LinkedHashSet<org.bukkit.block.Biome> wanted = new java.util.LinkedHashSet<>(java.util.Arrays.asList(biomes));
        int baseX = origin.getBlockX();
        int baseY = Math.max(getMinHeight(), Math.min(getMaxHeight() - 1, origin.getBlockY()));
        int baseZ = origin.getBlockZ();
        org.bukkit.util.BiomeSearchResult best = null;
        double bestDistance = Double.MAX_VALUE;
        int minY = getMinHeight();
        int maxY = getMaxHeight() - 1;
        for (int dx = -radius; dx <= radius; dx += horizontalInterval) {
            for (int dz = -radius; dz <= radius; dz += horizontalInterval) {
                double horizontalDistanceSq = (double) dx * dx + (double) dz * dz;
                if (horizontalDistanceSq > (double) radius * radius) continue;
                for (int y = minY; y <= maxY; y += verticalInterval) {
                    org.bukkit.block.Biome biome = getBiome(baseX + dx, y, baseZ + dz);
                    if (!wanted.contains(biome)) continue;
                    double distance = horizontalDistanceSq + (double) (y - baseY) * (double) (y - baseY);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        Location found = new Location(this, baseX + dx, y, baseZ + dz);
                        best = lunararcBiomeSearchResult(found, biome);
                    }
                }
            }
        }
        return best;
    }

    @Override
    public @Nullable org.bukkit.util.BiomeSearchResult locateNearestBiome(@NotNull Location origin, int radius,
            @NotNull org.bukkit.block.Biome... biomes) {
        return locateNearestBiome(origin, radius, 16, 16, biomes);
    }

    @Override
    public org.bukkit.World.@NotNull Spigot spigot() {
        return new org.bukkit.World.Spigot();
    }

    @Override
    public void sendGameEvent(@Nullable org.bukkit.entity.Entity source, @NotNull org.bukkit.GameEvent event,
            @NotNull org.bukkit.util.Vector position) {
        java.util.Objects.requireNonNull(event, "event");
        java.util.Objects.requireNonNull(position, "position");
        net.minecraft.world.entity.Entity nmsSource = null;
        if (source != null) {
            if (!(source instanceof org.bukkit.craftbukkit.entity.CraftEntity craftSource)) {
                throw new IllegalArgumentException("Entity is not backed by LunarArc");
            }
            nmsSource = craftSource.getHandle();
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                event.getKey().getNamespace(), event.getKey().getKey());
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.gameevent.GameEvent> eventKey =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.GAME_EVENT, id);
        net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> nmsEvent =
                net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.get(eventKey)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown game event " + event.getKey()));
        world.gameEvent(nmsEvent, new net.minecraft.world.phys.Vec3(position.getX(), position.getY(), position.getZ()),
                net.minecraft.world.level.gameevent.GameEvent.Context.of(nmsSource));
    }

    @Override
    public @NotNull java.util.Collection<org.bukkit.Material> getInfiniburn() {
        java.util.LinkedHashSet<org.bukkit.Material> materials = new java.util.LinkedHashSet<>();
        var tag = world.dimensionType().infiniburn();
        for (var holder : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(holder.value());
            if (id == null) continue;
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
            if (material != null) materials.add(material);
        }
        return java.util.List.copyOf(materials);
    }

    @Override
    public boolean isFixedTime() {
        return world.dimensionType().hasFixedTime();
    }

    @Override
    public double getCoordinateScale() {
        return world.dimensionType().coordinateScale();
    }

    @Override
    public org.bukkit.util.StructureSearchResult locateNearestStructure(Location origin,
            org.bukkit.generator.structure.Structure structure, int radius, boolean findUnexplored) {
        java.util.Objects.requireNonNull(origin, "origin");
        java.util.Objects.requireNonNull(structure, "structure");
        return lunararcLocateNearestStructure(origin, java.util.List.of(structure), radius, findUnexplored);
    }

    @Deprecated
    @Override
    public Location locateNearestStructure(Location origin, org.bukkit.StructureType structureType, int radius,
            boolean findUnexplored) {
        java.util.Objects.requireNonNull(origin, "origin");
        java.util.Objects.requireNonNull(structureType, "structureType");
        org.bukkit.util.StructureSearchResult result = null;
        if (org.bukkit.StructureType.MINESHAFT == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.MINESHAFT, radius, findUnexplored);
        } else if (org.bukkit.StructureType.VILLAGE == structureType) {
            result = lunararcLocateNearestStructure(origin, java.util.List.of(
                    org.bukkit.generator.structure.Structure.VILLAGE_DESERT,
                    org.bukkit.generator.structure.Structure.VILLAGE_PLAINS,
                    org.bukkit.generator.structure.Structure.VILLAGE_SAVANNA,
                    org.bukkit.generator.structure.Structure.VILLAGE_SNOWY,
                    org.bukkit.generator.structure.Structure.VILLAGE_TAIGA), radius, findUnexplored);
        } else if (org.bukkit.StructureType.NETHER_FORTRESS == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.FORTRESS, radius, findUnexplored);
        } else if (org.bukkit.StructureType.STRONGHOLD == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.STRONGHOLD, radius, findUnexplored);
        } else if (org.bukkit.StructureType.JUNGLE_PYRAMID == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.JUNGLE_TEMPLE, radius, findUnexplored);
        } else if (org.bukkit.StructureType.OCEAN_RUIN == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.OCEAN_RUIN, radius, findUnexplored);
        } else if (org.bukkit.StructureType.DESERT_PYRAMID == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.DESERT_PYRAMID, radius, findUnexplored);
        } else if (org.bukkit.StructureType.IGLOO == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.IGLOO, radius, findUnexplored);
        } else if (org.bukkit.StructureType.SWAMP_HUT == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.SWAMP_HUT, radius, findUnexplored);
        } else if (org.bukkit.StructureType.OCEAN_MONUMENT == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.OCEAN_MONUMENT, radius, findUnexplored);
        } else if (org.bukkit.StructureType.END_CITY == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.END_CITY, radius, findUnexplored);
        } else if (org.bukkit.StructureType.WOODLAND_MANSION == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.WOODLAND_MANSION, radius, findUnexplored);
        } else if (org.bukkit.StructureType.BURIED_TREASURE == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.BURIED_TREASURE, radius, findUnexplored);
        } else if (org.bukkit.StructureType.SHIPWRECK == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.SHIPWRECK, radius, findUnexplored);
        } else if (org.bukkit.StructureType.PILLAGER_OUTPOST == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.Structure.PILLAGER_OUTPOST, radius, findUnexplored);
        } else if (org.bukkit.StructureType.NETHER_FOSSIL == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.NETHER_FOSSIL, radius, findUnexplored);
        } else if (org.bukkit.StructureType.RUINED_PORTAL == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.StructureType.RUINED_PORTAL, radius, findUnexplored);
        } else if (org.bukkit.StructureType.BASTION_REMNANT == structureType) {
            result = locateNearestStructure(origin, org.bukkit.generator.structure.Structure.BASTION_REMNANT, radius, findUnexplored);
        }
        return result == null ? null : result.getLocation();
    }

    @Override
    public org.bukkit.util.StructureSearchResult locateNearestStructure(Location origin,
            org.bukkit.generator.structure.StructureType structureType, int radius, boolean findUnexplored) {
        java.util.Objects.requireNonNull(origin, "origin");
        java.util.Objects.requireNonNull(structureType, "structureType");
        java.util.ArrayList<org.bukkit.generator.structure.Structure> structures = new java.util.ArrayList<>();
        for (org.bukkit.generator.structure.Structure structure : org.bukkit.Registry.STRUCTURE) {
            if (structureType.equals(structure.getStructureType())) structures.add(structure);
        }
        return lunararcLocateNearestStructure(origin, structures, radius, findUnexplored);
    }

    private org.bukkit.util.StructureSearchResult lunararcLocateNearestStructure(Location origin,
            java.util.List<org.bukkit.generator.structure.Structure> structures, int radius, boolean findUnexplored) {
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        if (structures.isEmpty()) return null;

        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> registry =
                world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> holders =
                new java.util.ArrayList<>(structures.size());
        for (org.bukkit.generator.structure.Structure structure : structures) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    structure.getKey().getNamespace(), structure.getKey().getKey());
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> key =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, id);
            registry.get(key).ifPresent(holders::add);
        }
        if (holders.isEmpty()) return null;

        net.minecraft.core.BlockPos originPos = net.minecraft.core.BlockPos.containing(
                origin.getX(), origin.getY(), origin.getZ());
        com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos,
                net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> found =
                world.getChunkSource().getGenerator().findNearestMapStructure(
                        world, net.minecraft.core.HolderSet.direct(holders), originPos, radius, findUnexplored);
        if (found == null) return null;

        org.bukkit.generator.structure.Structure bukkit =
                org.bukkit.craftbukkit.generator.structure.CraftStructure.minecraftToBukkit(found.getSecond().value());
        Location location = new Location(this, found.getFirst().getX(), found.getFirst().getY(), found.getFirst().getZ());
        return new LunarArcStructureSearchResult(location, bukkit);
    }

    private static org.bukkit.util.BiomeSearchResult lunararcBiomeSearchResult(Location location, org.bukkit.block.Biome biome) {
        return new LunarArcBiomeSearchResult(location, biome);
    }

    private record LunarArcBiomeSearchResult(Location location, org.bukkit.block.Biome biome)
            implements org.bukkit.util.BiomeSearchResult {
        private LunarArcBiomeSearchResult {
            location = location.clone();
            java.util.Objects.requireNonNull(biome, "biome");
        }

        @Override public @NotNull org.bukkit.block.Biome getBiome() { return biome; }
        @Override public @NotNull Location getLocation() { return location.clone(); }
        @Override public String toString() { return "LunarArcBiomeSearchResult{" + biome + " at " + location + "}"; }
    }

    private record LunarArcStructureSearchResult(Location location, org.bukkit.generator.structure.Structure structure)
            implements org.bukkit.util.StructureSearchResult {
        private LunarArcStructureSearchResult {
            location = location.clone();
            java.util.Objects.requireNonNull(structure, "structure");
        }

        @Override public @NotNull org.bukkit.generator.structure.Structure getStructure() { return structure; }
        @Override public @NotNull Location getLocation() { return location.clone(); }
        @Override public String toString() { return "LunarArcStructureSearchResult{" + structure + " at " + location + "}"; }
    }

    private @NotNull Collection<org.bukkit.generator.structure.GeneratedStructure> lunararcGeneratedStructures(
            int x, int z, @Nullable org.bukkit.generator.structure.Structure filter) {
        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(x, z);
        net.minecraft.world.level.levelgen.structure.Structure filterHandle = filter == null
                ? null
                : org.bukkit.craftbukkit.generator.structure.CraftStructure.bukkitToMinecraft(filter);
        java.util.List<org.bukkit.generator.structure.GeneratedStructure> structures = new java.util.ArrayList<>();
        for (net.minecraft.world.level.levelgen.structure.StructureStart start :
                world.structureManager().startsForStructure(chunkPos,
                        nms -> filterHandle == null || nms == filterHandle)) {
            structures.add(new org.bukkit.craftbukkit.generator.structure.CraftGeneratedStructure(start));
        }
        return java.util.List.copyOf(structures);
    }

    private <T> net.minecraft.core.particles.ParticleOptions lunararcParticle(org.bukkit.Particle particle, @Nullable T data) {
        return CraftParticle.createParticleParam(particle, data);
    }

    private <T> void lunararcSpawnParticles(org.bukkit.Particle particle, @Nullable java.util.Collection<org.bukkit.entity.Player> receivers,
            double x,double y,double z,int count,double offsetX,double offsetY,double offsetZ,double extra,@Nullable T data,boolean force){
        net.minecraft.core.particles.ParticleOptions options=lunararcParticle(particle,data);
        if(receivers==null){ world.sendParticles(options,x,y,z,count,offsetX,offsetY,offsetZ,extra); return; }
        for(org.bukkit.entity.Player receiver:receivers){
            if(!(receiver instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft) || craft.getHandle().level()!=world) continue;
            world.sendParticles(craft.getHandle(),options,force,force,x,y,z,count,offsetX,offsetY,offsetZ,extra);
        }
    }

    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,double x,double y,double z,int count,double offsetX,double offsetY,double offsetZ,double extra,@Nullable T data,boolean force){lunararcSpawnParticles(particle,null,x,y,z,count,offsetX,offsetY,offsetZ,extra,data,force);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull Location location,int count,double offsetX,double offsetY,double offsetZ,double extra,@Nullable T data,boolean force){spawnParticle(particle,location.getX(),location.getY(),location.getZ(),count,offsetX,offsetY,offsetZ,extra,data,force);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull Location location,int count,double offsetX,double offsetY,double offsetZ,double extra,@Nullable T data){spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,extra,data,false);}
    @Override public void spawnParticle(@NotNull org.bukkit.Particle particle,double x,double y,double z,int count,double offsetX,double offsetY,double offsetZ,double extra){spawnParticle(particle,x,y,z,count,offsetX,offsetY,offsetZ,extra,null,false);}
    @Override public void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull Location location,int count,double offsetX,double offsetY,double offsetZ,double extra){spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,extra,null,false);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,double x,double y,double z,int count,double offsetX,double offsetY,double offsetZ,@Nullable T data){spawnParticle(particle,x,y,z,count,offsetX,offsetY,offsetZ,0,data,false);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull Location location,int count,double offsetX,double offsetY,double offsetZ,@Nullable T data){spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,0,data,false);}
    @Override public void spawnParticle(@NotNull org.bukkit.Particle particle,double x,double y,double z,int count,double offsetX,double offsetY,double offsetZ){spawnParticle(particle,x,y,z,count,offsetX,offsetY,offsetZ,0,null,false);}
    @Override public void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull Location location,int count,double offsetX,double offsetY,double offsetZ){spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,0,null,false);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,double x,double y,double z,int count,@Nullable T data){spawnParticle(particle,x,y,z,count,0,0,0,0,data,false);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull Location location,int count,@Nullable T data){spawnParticle(particle,location,count,0,0,0,0,data,false);}
    @Override public void spawnParticle(@NotNull org.bukkit.Particle particle,double x,double y,double z,int count){spawnParticle(particle,x,y,z,count,0,0,0,0,null,false);}
    @Override public <T> void spawnParticle(@NotNull org.bukkit.Particle particle,@NotNull java.util.List<org.bukkit.entity.Player> receivers,@Nullable org.bukkit.entity.Player source,double x,double y,double z,int count,double offsetX,double offsetY,double offsetZ,double extra,@Nullable T data,boolean force){lunararcSpawnParticles(particle,receivers,x,y,z,count,offsetX,offsetY,offsetZ,extra,data,force);}

    @Override
    public @NotNull Collection<org.bukkit.generator.structure.GeneratedStructure> getStructures(int x, int z) {
        return lunararcGeneratedStructures(x, z, null);
    }

    @Override
    public @NotNull Collection<org.bukkit.generator.structure.GeneratedStructure> getStructures(int x, int z,
            @NotNull org.bukkit.generator.structure.Structure structure) {
        java.util.Objects.requireNonNull(structure, "structure");
        return lunararcGeneratedStructures(x, z, structure);
    }

    @Override
    public void setMetadata(@NotNull String metadataKey, @NotNull org.bukkit.metadata.MetadataValue newMetadataValue) {
        metadata.computeIfAbsent(metadataKey, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .removeIf(value -> value.getOwningPlugin() == newMetadataValue.getOwningPlugin());
        metadata.get(metadataKey).add(newMetadataValue);
    }

    @Override
    public @NotNull List<org.bukkit.metadata.MetadataValue> getMetadata(@NotNull String metadataKey) {
        var values = metadata.get(metadataKey);
        return values == null ? Collections.emptyList() : java.util.List.copyOf(values);
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        var values = metadata.get(metadataKey);
        return values != null && !values.isEmpty();
    }

    @Override
    public void removeMetadata(@NotNull String metadataKey, @NotNull org.bukkit.plugin.Plugin owningPlugin) {
        var values = metadata.get(metadataKey);
        if (values == null) return;
        values.removeIf(value -> value.getOwningPlugin() == owningPlugin);
        if (values.isEmpty()) metadata.remove(metadataKey, values);
    }

    @Override
    public @NotNull org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer() {
        return this.persistentDataContainer;
    }

    @Override
    public void sendPluginMessage(@NotNull org.bukkit.plugin.Plugin source, @NotNull String channel, byte[] message) {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(channel, "channel");
        java.util.Objects.requireNonNull(message, "message");
        org.bukkit.plugin.messaging.StandardMessenger.validatePluginMessage(org.bukkit.Bukkit.getMessenger(), source, channel, message);
        for (org.bukkit.entity.Player player : getPlayers()) {
            player.sendPluginMessage(source, channel, message);
        }
    }

    @Override
    public @NotNull java.util.Set<String> getListeningPluginChannels() {
        java.util.LinkedHashSet<String> channels = new java.util.LinkedHashSet<>();
        for (org.bukkit.entity.Player player : getPlayers()) channels.addAll(player.getListeningPluginChannels());
        return java.util.Collections.unmodifiableSet(channels);
    }

    @Override
    public @Nullable String getGameRuleValue(@NotNull String rule) {
        // In method contract for some reason
        if (rule == null) return null;
        org.bukkit.GameRule<?> bukkit = org.bukkit.GameRule.getByName(rule);
        if (bukkit == null) throw new IllegalArgumentException("Unknown gamerule: " + rule);
        return world.getGameRules().getAsString(CraftGameRule.bukkitToMinecraft(bukkit));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public boolean setGameRuleValue(@NotNull String rule, @NotNull String value) {
        // No null values allowed
        if (rule == null || value == null) return false;
        org.bukkit.GameRule<?> bukkit = org.bukkit.GameRule.getByName(rule);
        if (bukkit == null) return false;
        net.minecraft.world.level.gamerules.GameRule nms = CraftGameRule.bukkitToMinecraft(bukkit);
        if (!lunararcHasGameRule(nms)) return false;
        return !org.bukkit.craftbukkit.event.CraftEventFactory.handleGameRuleSet(nms, nms.deserialize(value).getOrThrow(), world, null).cancelled();
    }

    private boolean lunararcHasGameRule(net.minecraft.world.level.gamerules.GameRule<?> rule) {
        return world.getGameRules().availableRules().anyMatch(available -> available == rule);
    }

    @Override
    public @NotNull String[] getGameRules() {
        return world.getGameRules().availableRules()
                .map(net.minecraft.world.level.gamerules.GameRule::id)
                .toArray(String[]::new);
    }

    @Override
    public boolean isGameRule(@NotNull String rule) {
        java.util.Objects.requireNonNull(rule, "String rule cannot be null");
        if (rule.isEmpty()) throw new IllegalArgumentException("String rule cannot be empty");
        org.bukkit.GameRule<?> bukkit = org.bukkit.GameRule.getByName(rule);
        if (bukkit == null) return false;
        return lunararcHasGameRule(CraftGameRule.bukkitToMinecraft(bukkit));
    }

    @SuppressWarnings("unchecked")
    public static <T> T shimLegacyValue(T value, org.bukkit.GameRule<?> gameRule) {
        if (gameRule instanceof CraftGameRule.LegacyGameRuleWrapper<?, ?> legacy) {
            return (T) ((java.util.function.Function<Object, Object>) legacy.getToLegacyFromModern()).apply(value);
        }
        return value;
    }

    @Override
    public <T> @Nullable T getGameRuleValue(@NotNull org.bukkit.GameRule<T> rule) {
        java.util.Objects.requireNonNull(rule, "GameRule cannot be null");
        T value = world.getGameRules().get(CraftGameRule.bukkitToMinecraft(rule));
        return shimLegacyValue(value, rule);
    }

    @Override
    public <T> @Nullable T getGameRuleDefault(@NotNull org.bukkit.GameRule<T> rule) {
        java.util.Objects.requireNonNull(rule, "GameRule cannot be null");
        T value = CraftGameRule.bukkitToMinecraft(rule).defaultValue();
        return shimLegacyValue(value, rule);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> boolean setGameRule(@NotNull org.bukkit.GameRule<T> rule, @NotNull T newValue) {
        java.util.Objects.requireNonNull(rule, "GameRule cannot be null");
        java.util.Objects.requireNonNull(newValue, "GameRule value cannot be null");
        net.minecraft.world.level.gamerules.GameRule<T> nms = CraftGameRule.bukkitToMinecraft(rule);
        if (!lunararcHasGameRule(nms)) return false;

        final T convertedValue;
        if (rule instanceof CraftGameRule.LegacyGameRuleWrapper<?, ?> legacy) {
            convertedValue = (T) ((java.util.function.Function<Object, Object>) legacy.getFromLegacyToModern()).apply(newValue);
        } else {
            nms.valueCodec().encodeStart(com.mojang.serialization.JavaOps.INSTANCE, newValue).ifError(error -> {
                throw CraftGameRule.INVALID_VALUE.apply(newValue.toString(), error);
            });
            convertedValue = newValue;
        }
        return !org.bukkit.craftbukkit.event.CraftEventFactory.handleGameRuleSet(nms, convertedValue, world, null).cancelled();
    }

    private static net.minecraft.sounds.SoundSource lunararcSoundSource(org.bukkit.SoundCategory category) {
        return net.minecraft.sounds.SoundSource.valueOf(category.name());
    }
    private @Nullable net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> lunararcSound(String sound) {
        net.minecraft.resources.Identifier id = sound.indexOf(':') >= 0
                ? net.minecraft.resources.Identifier.parse(sound)
                : net.minecraft.resources.Identifier.withDefaultNamespace(sound.toLowerCase(java.util.Locale.ROOT));
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id).orElse(null);
    }
    private String lunararcSoundName(org.bukkit.Sound sound) { return sound.getKey().toString(); }

    @Override public void playSound(@NotNull org.bukkit.entity.Entity entity,@NotNull String sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch,long seed){ playSound(entity.getLocation(),sound,category,volume,pitch,seed); }
    @Override public void playSound(@NotNull org.bukkit.entity.Entity entity,@NotNull org.bukkit.Sound sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch,long seed){ playSound(entity.getLocation(),sound,category,volume,pitch,seed); }
    @Override public void playSound(@NotNull org.bukkit.entity.Entity entity,@NotNull String sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch){ playSound(entity.getLocation(),sound,category,volume,pitch); }
    @Override public void playSound(@NotNull org.bukkit.entity.Entity entity,@NotNull org.bukkit.Sound sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch){ playSound(entity.getLocation(),sound,category,volume,pitch); }
    @Override public void playSound(@NotNull org.bukkit.entity.Entity entity,@NotNull String sound,float volume,float pitch){ playSound(entity.getLocation(),sound,org.bukkit.SoundCategory.MASTER,volume,pitch); }
    @Override public void playSound(@NotNull org.bukkit.entity.Entity entity,@NotNull org.bukkit.Sound sound,float volume,float pitch){ playSound(entity.getLocation(),sound,org.bukkit.SoundCategory.MASTER,volume,pitch); }
    @Override public void playSound(@NotNull org.bukkit.Location loc,@NotNull String sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch,long seed){
        var holder=lunararcSound(sound); if(holder==null)return;
        world.playSound(null,loc.getX(),loc.getY(),loc.getZ(),holder,lunararcSoundSource(category),volume,pitch);
    }
    @Override public void playSound(@NotNull org.bukkit.Location loc,@NotNull org.bukkit.Sound sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch,long seed){ playSound(loc,lunararcSoundName(sound),category,volume,pitch,seed); }
    @Override public void playSound(@NotNull org.bukkit.Location loc,@NotNull String sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch){ playSound(loc,sound,category,volume,pitch,world.random.nextLong()); }
    @Override public void playSound(@NotNull org.bukkit.Location loc,@NotNull org.bukkit.Sound sound,@NotNull org.bukkit.SoundCategory category,float volume,float pitch){ playSound(loc,lunararcSoundName(sound),category,volume,pitch); }
    @Override public void playSound(@NotNull org.bukkit.Location loc,@NotNull String sound,float volume,float pitch){ playSound(loc,sound,org.bukkit.SoundCategory.MASTER,volume,pitch); }

    @Override
    public void playNote(@NotNull org.bukkit.Location loc, @NotNull org.bukkit.Instrument instrument,
            @NotNull org.bukkit.Note note) {
        java.util.Objects.requireNonNull(loc, "loc");
        java.util.Objects.requireNonNull(instrument, "instrument");
        java.util.Objects.requireNonNull(note, "note");
        playSound(loc, instrument.getSound(), org.bukkit.SoundCategory.RECORDS, 3.0F, note.getPitch());
    }

    @Override
    public void setSpawnLimit(@NotNull org.bukkit.entity.SpawnCategory category, int limit) {
        java.util.Objects.requireNonNull(category, "category");
        if (category == org.bukkit.entity.SpawnCategory.MISC) {
            throw new IllegalArgumentException("SpawnCategory.MISC does not have a natural-spawn cap");
        }
        if (limit < 0) lunararcSpawnLimits.remove(category); else lunararcSpawnLimits.put(category, limit);
    }

    @Override
    public int getSpawnLimit(@NotNull org.bukkit.entity.SpawnCategory category) {
        java.util.Objects.requireNonNull(category, "category");
        if (category == org.bukkit.entity.SpawnCategory.MISC) return -1;
        Integer local = lunararcSpawnLimits.get(category);
        if (local != null) return local;
        org.bukkit.Server server = org.bukkit.Bukkit.getServer();
        return server == null ? -1 : server.getSpawnLimit(category);
    }
    @Override public void setAmbientSpawnLimit(int limit){setSpawnLimit(org.bukkit.entity.SpawnCategory.AMBIENT,limit);}
    @Override public int getAmbientSpawnLimit(){return getSpawnLimit(org.bukkit.entity.SpawnCategory.AMBIENT);}
    @Override public void setWaterAmbientSpawnLimit(int limit){setSpawnLimit(org.bukkit.entity.SpawnCategory.WATER_AMBIENT,limit);}
    @Override public int getWaterAmbientSpawnLimit(){return getSpawnLimit(org.bukkit.entity.SpawnCategory.WATER_AMBIENT);}
    @Override public void setWaterUndergroundCreatureSpawnLimit(int limit){setSpawnLimit(org.bukkit.entity.SpawnCategory.WATER_UNDERGROUND_CREATURE,limit);}
    @Override public int getWaterUndergroundCreatureSpawnLimit(){return getSpawnLimit(org.bukkit.entity.SpawnCategory.WATER_UNDERGROUND_CREATURE);}
    @Override public void setWaterAnimalSpawnLimit(int limit){setSpawnLimit(org.bukkit.entity.SpawnCategory.WATER_ANIMAL,limit);}
    @Override public int getWaterAnimalSpawnLimit(){return getSpawnLimit(org.bukkit.entity.SpawnCategory.WATER_ANIMAL);}
    @Override public void setAnimalSpawnLimit(int limit){setSpawnLimit(org.bukkit.entity.SpawnCategory.ANIMAL,limit);}
    @Override public int getAnimalSpawnLimit(){return getSpawnLimit(org.bukkit.entity.SpawnCategory.ANIMAL);}
    @Override public void setMonsterSpawnLimit(int limit){setSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER,limit);}
    @Override public int getMonsterSpawnLimit(){return getSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER);}
    @Override
    public void setTicksPerSpawns(@NotNull org.bukkit.entity.SpawnCategory category, int ticks) {
        java.util.Objects.requireNonNull(category, "category");
        if (category == org.bukkit.entity.SpawnCategory.MISC) {
            throw new IllegalArgumentException("SpawnCategory.MISC does not have a natural-spawn cadence");
        }
        if (ticks < 0) lunararcTicksPerSpawn.remove(category); else lunararcTicksPerSpawn.put(category, (long) ticks);
    }

    @Override
    public long getTicksPerSpawns(@NotNull org.bukkit.entity.SpawnCategory category) {
        java.util.Objects.requireNonNull(category, "category");
        if (category == org.bukkit.entity.SpawnCategory.MISC) return -1L;
        Long local = lunararcTicksPerSpawn.get(category);
        if (local != null) return local;
        org.bukkit.Server server = org.bukkit.Bukkit.getServer();
        return server == null ? -1L : server.getTicksPerSpawns(category);
    }
    @Override public void setTicksPerAmbientSpawns(int ticks){setTicksPerSpawns(org.bukkit.entity.SpawnCategory.AMBIENT,ticks);}
    @Override public long getTicksPerAmbientSpawns(){return getTicksPerSpawns(org.bukkit.entity.SpawnCategory.AMBIENT);}
    @Override public void setTicksPerWaterUndergroundCreatureSpawns(int ticks){setTicksPerSpawns(org.bukkit.entity.SpawnCategory.WATER_UNDERGROUND_CREATURE,ticks);}
    @Override public long getTicksPerWaterUndergroundCreatureSpawns(){return getTicksPerSpawns(org.bukkit.entity.SpawnCategory.WATER_UNDERGROUND_CREATURE);}
    @Override public void setTicksPerWaterAmbientSpawns(int ticks){setTicksPerSpawns(org.bukkit.entity.SpawnCategory.WATER_AMBIENT,ticks);}
    @Override public long getTicksPerWaterAmbientSpawns(){return getTicksPerSpawns(org.bukkit.entity.SpawnCategory.WATER_AMBIENT);}
    @Override public void setTicksPerWaterSpawns(int ticks){setTicksPerSpawns(org.bukkit.entity.SpawnCategory.WATER_ANIMAL,ticks);}
    @Override public long getTicksPerWaterSpawns(){return getTicksPerSpawns(org.bukkit.entity.SpawnCategory.WATER_ANIMAL);}
    @Override public void setTicksPerMonsterSpawns(int ticks){setTicksPerSpawns(org.bukkit.entity.SpawnCategory.MONSTER,ticks);}
    @Override public long getTicksPerMonsterSpawns(){return getTicksPerSpawns(org.bukkit.entity.SpawnCategory.MONSTER);}
    @Override public void setTicksPerAnimalSpawns(int ticks){setTicksPerSpawns(org.bukkit.entity.SpawnCategory.ANIMAL,ticks);}
    @Override public long getTicksPerAnimalSpawns(){return getTicksPerSpawns(org.bukkit.entity.SpawnCategory.ANIMAL);}

    @Override
    public void setHardcore(boolean hardcore) {
        net.minecraft.world.level.storage.WorldData worldData = world.getServer().getWorldData();
        if (!(worldData instanceof net.minecraft.world.level.storage.PrimaryLevelData primaryLevelData)) {
            throw new IllegalStateException("World data does not expose mutable primary level settings");
        }

        PrimaryLevelDataAccessBridge accessor = (PrimaryLevelDataAccessBridge) (Object) primaryLevelData;
        net.minecraft.world.level.LevelSettings settings = accessor.lunararc$getSettings();
        accessor.lunararc$setSettings(new net.minecraft.world.level.LevelSettings(
                settings.levelName(),
                settings.gameType(),
                hardcore,
                hardcore ? net.minecraft.world.Difficulty.HARD : settings.difficulty(),
                settings.allowCommands(),
                settings.gameRules(),
                settings.getDataConfiguration()));
    }

    @Override public boolean isHardcore() { return world.getServer().getWorldData().isHardcore(); }
    @Override public boolean canGenerateStructures() { return world.getServer().getWorldData().worldGenOptions().generateStructures(); }

    @Override
    public org.bukkit.WorldType getWorldType() {
        return org.bukkit.WorldType.NORMAL;
    }

    @Override
    public @NotNull java.io.File getWorldFolder() {
        java.nio.file.Path folder = ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) world).lunararc$getDimensionFolder();
        if (folder != null) return folder.toFile();
        String dim = world.dimension().identifier().toString();
        return switch (dim) {
            case "minecraft:overworld" -> new java.io.File("world");
            case "minecraft:the_nether" -> new java.io.File("world_nether");
            case "minecraft:the_end" -> new java.io.File("world_the_end");
            default -> new java.io.File("world", "dimensions/" + world.dimension().identifier().getNamespace() + "/" + world.dimension().identifier().getPath());
        };
    }

    @Override
    public void setAutoSave(boolean value) {
        world.noSave = !value;
    }

    @Override
    public boolean isAutoSave() {
        return !world.noSave;
    }

    @Override
    public void setKeepSpawnInMemory(boolean value) {
        this.lunararcKeepSpawnInMemory = value;
        int spawnX = getSpawnLocation().getBlockX() >> 4;
        int spawnZ = getSpawnLocation().getBlockZ() >> 4;
        try {
            world.setChunkForced(spawnX, spawnZ, value);
            long key = net.minecraft.world.level.ChunkPos.asLong(spawnX, spawnZ);
            if (value) {
                if (lunararcSpawnHeldChunks.add(key)) lunararc$addPluginRegionTicket(spawnX, spawnZ);
            } else if (lunararcSpawnHeldChunks.remove(key)) {
                lunararc$removePluginRegionTicketIfUnused(spawnX, spawnZ);
            }
        } catch (Throwable ignored) {

        }
    }

    @Override
    public boolean getKeepSpawnInMemory() {
        return lunararcKeepSpawnInMemory;
    }

    @Override
    public int getSeaLevel() {
        return world.getSeaLevel();
    }

    @Override public boolean isUltraWarm() {
        return world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES)
                && world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.FAST_LAVA)
                && world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.DEFAULT_DRIPSTONE_PARTICLE).equals(net.minecraft.core.particles.ParticleTypes.DRIPPING_DRIPSTONE_LAVA);
    }
    @Override public boolean hasRaids() { return world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.CAN_START_RAID); }
    @Override public boolean isRespawnAnchorWorks() { return world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.RESPAWN_ANCHOR_WORKS); }
    @Override public boolean isPiglinSafe() { return !world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.PIGLINS_ZOMBIFY); }
    @Override public boolean hasCeiling() { return world.dimensionType().hasCeiling(); }
    @Override public boolean hasSkyLight() { return world.dimensionType().hasSkyLight(); }
    @Override public boolean isBedWorks() {
        net.minecraft.world.attribute.BedRule bedRule = world.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE);
        return !bedRule.explodes() && bedRule.canSleep().test(world);
    }
    // the dimension "natural" flag no longer exists in 1.21.11; the overworld type is the closest equivalent
    @Override public boolean isNatural() { return this.getEnvironment() == Environment.NORMAL; }
    @Override public int getLogicalHeight() { return world.dimensionType().logicalHeight(); }

    @Override
    public double getHumidity(int x, int y, int z) {
        return world.getBiome(new net.minecraft.core.BlockPos(x, y, z)).value().climateSettings.downfall();
    }

    @Override
    public double getHumidity(int x, int z) {
        return getHumidity(x, getSeaLevel(), z);
    }

    @Override
    public double getTemperature(int x, int y, int z) {
        return world.getBiome(new net.minecraft.core.BlockPos(x, y, z)).value().getBaseTemperature();
    }

    @Override
    public double getTemperature(int x, int z) {
        return world.getBiome(new net.minecraft.core.BlockPos(x, getSeaLevel(), z)).value().getBaseTemperature();
    }

    @Override
    public void setBiome(int x, int z, @NotNull org.bukkit.block.Biome biome) {
        setBiome(x, getSeaLevel(), z, biome);
    }

    @Override
    public @NotNull org.bukkit.block.Biome getBiome(int x, int z) {
        return getBiome(x, getSeaLevel(), z);
    }

    @Override
    public boolean getAllowMonsters() {
        return world.getChunkSource().spawnEnemies;
    }

    @Override
    public boolean getAllowAnimals() {
        return ((io.lunararcdevs.lunararc.common.bridge.ServerChunkCacheBridge) world.getChunkSource()).lunararc$isSpawnFriendlies();
    }

    @Override
    public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals) {
        world.getChunkSource().setSpawnSettings(allowMonsters);
        ((io.lunararcdevs.lunararc.common.bridge.ServerChunkCacheBridge) world.getChunkSource()).lunararc$setSpawnFriendlies(allowAnimals);
    }

    @Override
    public <T> void playEffect(@NotNull org.bukkit.Location location, @NotNull org.bukkit.Effect effect,
            @Nullable T data, int radius) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(effect, "effect");
        if (location.getWorld() == null) throw new IllegalArgumentException("World of Location cannot be null");
        if (location.getWorld() != this) throw new IllegalArgumentException("Location belongs to a different world");
        if (radius < 0) throw new IllegalArgumentException("Radius cannot be negative");
        if (data != null) {
            if (effect.getData() == null) {
                throw new IllegalArgumentException("Effect." + effect.name() + " does not have valid data");
            }
            if (!effect.isApplicable(data)) {
                throw new IllegalArgumentException(data.getClass().getName() + " data cannot be used for the " + effect + " effect");
            }
        } else if (effect.getData() != null && effect != org.bukkit.Effect.ELECTRIC_SPARK) {
            throw new IllegalArgumentException("Wrong kind of data for the " + effect + " effect");
        }
        int dataValue = org.bukkit.craftbukkit.CraftEffect.getDataValue(effect, data, world.registryAccess());
        playEffect(location, effect, dataValue, radius);
    }

    @Override
    public <T> void playEffect(@NotNull org.bukkit.Location location, @NotNull org.bukkit.Effect effect,
            @Nullable T data) {
        playEffect(location, effect, data, 64);
    }

    @Override
    public void playEffect(@NotNull org.bukkit.Location location, @NotNull org.bukkit.Effect effect, int data, int radius) {
        lunararc$playEffectPacket(location, effect, data, radius);
    }

    @Override
    public void playEffect(@NotNull org.bukkit.Location location, @NotNull org.bukkit.Effect effect, int data) {
        lunararc$playEffectPacket(location, effect, data, 64);
    }

    private void lunararc$playEffectPacket(@NotNull org.bukkit.Location location, @NotNull org.bukkit.Effect effect, int data, int radius) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(effect, "effect");
        if (location.getWorld() == null) throw new IllegalArgumentException("World of Location cannot be null");
        if (location.getWorld() != this) throw new IllegalArgumentException("Location belongs to a different world");
        if (radius < 0) throw new IllegalArgumentException("Radius cannot be negative");

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        net.minecraft.network.protocol.game.ClientboundLevelEventPacket packet =
                new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(effect.getId(), pos, data, false);
        long radiusSquared = (long) radius * radius;
        for (net.minecraft.server.level.ServerPlayer player : world.players()) {
            if (player.connection == null) continue;
            double dx = player.getX() - location.getX();
            double dy = player.getY() - location.getY();
            double dz = player.getZ() - location.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                player.connection.send(packet);
            }
        }
    }

    @Override
    public @NotNull org.bukkit.entity.FallingBlock spawnFallingBlock(@NotNull org.bukkit.Location location,
            @NotNull org.bukkit.Material material, byte data) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(material, "material");
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Location belongs to a different world");
        }
        net.minecraft.world.level.block.state.BlockState state;
        if (material.isLegacy()) {
            state = org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacyData(material, data);
        } else {
            if (!material.isBlock()) throw new IllegalArgumentException("Material " + material + " must be a block");
            state = org.bukkit.craftbukkit.block.data.CraftBlockData.parse(material, null).getState();
        }
        return lunararc$spawnFallingBlock(location, state);
    }

    @Override
    public @NotNull org.bukkit.entity.FallingBlock spawnFallingBlock(@NotNull org.bukkit.Location location,
            @NotNull org.bukkit.block.data.BlockData data) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(data, "data");
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Location belongs to a different world");
        }
        org.bukkit.craftbukkit.block.data.CraftBlockData craftData = data instanceof org.bukkit.craftbukkit.block.data.CraftBlockData craft
                ? craft
                : org.bukkit.craftbukkit.block.data.CraftBlockData.parse(data.getAsString());
        return lunararc$spawnFallingBlock(location, craftData.getState());
    }

    @Override
    public @NotNull org.bukkit.entity.FallingBlock spawnFallingBlock(@NotNull org.bukkit.Location location,
            @NotNull org.bukkit.material.MaterialData data) {
        java.util.Objects.requireNonNull(data, "data");
        return spawnFallingBlock(location, data.getItemType(), data.getData());
    }

    private org.bukkit.entity.FallingBlock lunararc$spawnFallingBlock(
            org.bukkit.Location location, net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.entity.item.FallingBlockEntity entity = new net.minecraft.world.entity.item.FallingBlockEntity(
                world, location.getX(), location.getY(), location.getZ(), state);
        ((io.lunararcdevs.lunararc.common.bridge.FallingBlockBridge) entity).lunararc$setTime(1);
        ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) world).lunararc$addFreshEntity(
                entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(
                (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer(), entity);
        if (!(bukkit instanceof org.bukkit.entity.FallingBlock fallingBlock)) {
            throw new IllegalStateException("FallingBlockEntity did not produce a FallingBlock wrapper");
        }
        return fallingBlock;
    }

    public Iterable<net.minecraft.world.entity.Entity> getNMSEntities() {
        return world.getAllEntities();
    }

    public void addEntityWithPassengers(net.minecraft.world.entity.Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) world).lunararc$addFreshEntityWithPassengers(entity, reason);
    }

    public void setBiome(int x, int y, int z, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder) {
        net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunk(x >> 4, z >> 4);
        int sectionIndex = chunk.getSectionIndex(y);
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            throw new IllegalArgumentException("Y coordinate outside world bounds: " + y);
        }
        net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(sectionIndex);
        if (!(section.getBiomes() instanceof net.minecraft.world.level.chunk.PalettedContainer<?> raw)) {
            throw new IllegalStateException("Biome palette is not mutable");
        }
        @SuppressWarnings("unchecked")
        net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes =
                (net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>) raw;
        biomes.set((x & 15) >> 2, (y & 15) >> 2, (z & 15) >> 2, holder);
        chunk.markUnsaved();
    }

    @Override
    public <T extends Entity> @NotNull T addEntity(@NotNull T entity) {
        java.util.Objects.requireNonNull(entity, "entity");
        if (!(entity instanceof org.bukkit.craftbukkit.entity.CraftEntity craftEntity)) {
            throw new IllegalArgumentException("Entity must be backed by LunarArc CraftEntity");
        }
        net.minecraft.world.entity.Entity handle = craftEntity.getHandle();
        if (handle.level() != world) {
            throw new IllegalArgumentException("Entity was created for a different world");
        }
        ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) world).lunararc$addFreshEntityWithPassengers(
                handle, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> @Nullable T spawnInternal(@NotNull Location location, @NotNull EntityType type,
            org.bukkit.event.entity.CreatureSpawnEvent.@NotNull SpawnReason reason,
            @Nullable java.util.function.Consumer<? super T> function) {
        net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(type.getKey().toString());
        net.minecraft.world.entity.EntityType<?> nmsType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(rl);
        if (nmsType == null) return null;
        net.minecraft.world.entity.Entity nmsEntity = nmsType.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (nmsEntity == null) return null;
        nmsEntity.setPos(location.getX(), location.getY(), location.getZ());
        nmsEntity.setYRot(location.getYaw());
        nmsEntity.setXRot(location.getPitch());
        org.bukkit.craftbukkit.CraftServer cs =
            (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(cs, nmsEntity);
        if (function != null && bukkit != null) {
            function.accept((T) bukkit);
        }
        if (!((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) world).lunararc$addFreshEntity(nmsEntity, reason)) {
            return null;
        }
        return bukkit != null ? (T) bukkit : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> @NotNull T spawn(@NotNull Location location, @NotNull Class<T> clazz) {
        return spawn(location, clazz, true, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> @NotNull T spawn(@NotNull Location location, @NotNull Class<T> clazz,
            boolean randomizeData, @Nullable java.util.function.Consumer<? super T> function) {
        return spawn(location, clazz, function, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> @NotNull T spawn(@NotNull Location location, @NotNull Class<T> clazz,
            @Nullable java.util.function.Consumer<? super T> function,
            org.bukkit.event.entity.CreatureSpawnEvent.@NotNull SpawnReason reason) {
        java.util.Objects.requireNonNull(reason, "reason");
        for (EntityType type : EntityType.values()) {
            if (type.getEntityClass() != null && clazz.isAssignableFrom(type.getEntityClass())) {
                T result = spawnInternal(location, type, reason, function);
                if (result != null) return result;
            }
        }
        throw new IllegalArgumentException("Cannot spawn entity of class " + clazz.getName());
    }

    @Override
    public @NotNull Entity spawnEntity(@NotNull Location location, @NotNull EntityType type) {
        Entity result = spawnInternal(location, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, null);
        if (result == null) throw new IllegalArgumentException("Cannot spawn entity type " + type);
        return result;
    }

    @Override
    public @NotNull Entity spawnEntity(@NotNull Location location, @NotNull EntityType type, boolean randomizeData) {
        return spawnEntity(location, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> @NotNull T createEntity(@NotNull Location location, @NotNull Class<T> clazz) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(clazz, "clazz");
        for (EntityType type : EntityType.values()) {
            if (type.getEntityClass() != null && clazz.isAssignableFrom(type.getEntityClass())) {
                net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(type.getKey().toString());
                net.minecraft.world.entity.EntityType<?> nmsType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(rl);
                if (nmsType == null) continue;
                net.minecraft.world.entity.Entity nmsEntity = nmsType.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (nmsEntity == null) continue;
                nmsEntity.setPos(location.getX(), location.getY(), location.getZ());
                nmsEntity.setYRot(location.getYaw());
                nmsEntity.setXRot(location.getPitch());
                org.bukkit.craftbukkit.CraftServer cs =
                    (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
                org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(cs, nmsEntity);
                if (bukkit != null) return (T) bukkit;
            }
        }
        throw new IllegalArgumentException("Cannot create entity of class " + clazz.getName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends org.bukkit.entity.LivingEntity> @NotNull T spawn(@NotNull org.bukkit.Location location,
            @NotNull Class<T> clazz, org.bukkit.event.entity.CreatureSpawnEvent.@NotNull SpawnReason reason,
            boolean randomizeData, @Nullable java.util.function.Consumer<? super T> function) {
        return spawn(location, clazz, function, reason);
    }

    @Override
    public @NotNull java.util.List<org.bukkit.generator.BlockPopulator> getPopulators() {
        return lunararcPopulators;
    }

    @Override
    public void save() {
        try { world.save(null, true, false); }
        catch (Throwable ex) { throw new IllegalStateException("Unable to save world " + getName(), ex); }
    }

    @Override
    public @Nullable org.bukkit.generator.BiomeProvider getBiomeProvider() {
        return this.lunararcBiomeProvider;
    }

    @Override
    public @NotNull org.bukkit.generator.BiomeProvider vanillaBiomeProvider() {
        net.minecraft.server.level.ServerChunkCache serverCache = world.getChunkSource();
        net.minecraft.world.level.biome.BiomeSource biomeSource = serverCache.getGenerator().getBiomeSource();
        net.minecraft.world.level.biome.Climate.Sampler sampler = serverCache.randomState().sampler();

        java.util.List<org.bukkit.block.Biome> possible = biomeSource.possibleBiomes().stream()
                .map(holder -> holder.unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(id -> org.bukkit.Registry.BIOME.get(new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath())))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        return new org.bukkit.generator.BiomeProvider() {
            @Override
            public @NotNull org.bukkit.block.Biome getBiome(@NotNull org.bukkit.generator.WorldInfo worldInfo,
                    int x, int y, int z) {
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder =
                        biomeSource.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler);
                net.minecraft.resources.Identifier id = holder.unwrapKey()
                        .map(net.minecraft.resources.ResourceKey::identifier)
                        .orElseThrow(() -> new IllegalStateException("Biome is not registered"));
                org.bukkit.block.Biome biome = org.bukkit.Registry.BIOME.get(
                        new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()));
                if (biome == null) throw new IllegalStateException("No Bukkit biome wrapper for " + id);
                return biome;
            }

            @Override
            public @NotNull java.util.List<org.bukkit.block.Biome> getBiomes(@NotNull org.bukkit.generator.WorldInfo worldInfo) {
                return possible;
            }
        };
    }

    @Override
    public @Nullable org.bukkit.generator.ChunkGenerator getGenerator() {
        return this.lunararcGenerator;
    }

    @Override
    public int getMaxHeight() {
        return world.getMaxY();
    }

    @Override
    public int getMinHeight() {
        return world.getMinY();
    }

    private boolean explode0(double x, double y, double z, float yield, boolean isFlaming, boolean breakBlocks,
            @Nullable org.bukkit.entity.Entity source) {
        if (yield < 0.0F) throw new IllegalArgumentException("yield must be >= 0");
        net.minecraft.world.entity.Entity nmsSource = source instanceof org.bukkit.craftbukkit.entity.CraftEntity craft
                ? craft.getHandle() : null;
        net.minecraft.world.level.Level.ExplosionInteraction interaction = breakBlocks
                ? net.minecraft.world.level.Level.ExplosionInteraction.TNT
                : net.minecraft.world.level.Level.ExplosionInteraction.NONE;
        world.explode(nmsSource, x, y, z, yield, isFlaming, interaction);
        return true;
    }

    @Override
    public boolean createExplosion(@NotNull org.bukkit.Location loc, float yield, boolean isFlaming, boolean isSmoking,
            @Nullable org.bukkit.entity.Entity source) {
        java.util.Objects.requireNonNull(loc, "loc");
        return explode0(loc.getX(), loc.getY(), loc.getZ(), yield, isFlaming, isSmoking, source);
    }

    @Override
    public boolean createExplosion(@NotNull org.bukkit.Location loc, float yield, boolean isFlaming, boolean isSmoking) {
        return createExplosion(loc, yield, isFlaming, isSmoking, null);
    }

    @Override
    public boolean createExplosion(@Nullable org.bukkit.entity.Entity source, @NotNull org.bukkit.Location loc,
            float yield, boolean isFlaming, boolean isSmoking, boolean breakBlocks) {
        java.util.Objects.requireNonNull(loc, "loc");
        return explode0(loc.getX(), loc.getY(), loc.getZ(), yield, isFlaming, breakBlocks, source);
    }

    @Override public boolean createExplosion(@NotNull org.bukkit.Location loc, float yield, boolean isFlaming) { return createExplosion(loc, yield, isFlaming, true); }
    @Override public boolean createExplosion(@NotNull org.bukkit.Location loc, float yield) { return createExplosion(loc, yield, false, true); }
    @Override public boolean createExplosion(double x, double y, double z, float yield, boolean isFlaming, boolean isSmoking, @Nullable org.bukkit.entity.Entity source) { return explode0(x,y,z,yield,isFlaming,isSmoking,source); }
    @Override public boolean createExplosion(double x, double y, double z, float yield, boolean isFlaming, boolean isSmoking) { return explode0(x,y,z,yield,isFlaming,isSmoking,null); }
    @Override public boolean createExplosion(double x, double y, double z, float yield, boolean isFlaming) { return explode0(x,y,z,yield,isFlaming,true,null); }
    @Override public boolean createExplosion(double x, double y, double z, float yield) { return explode0(x,y,z,yield,false,true,null); }

    @Override
    public int getClearWeatherDuration() {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            return data.getClearWeatherTime();
        }
        return 0;
    }

    @Override
    public void setClearWeatherDuration(int duration) {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            data.setClearWeatherTime(Math.max(0, duration));
        }
    }

    @Override
    public boolean isClearWeather() {
        return !hasStorm() && !isThundering();
    }

    @Override
    public void setThunderDuration(int duration) {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            data.setThunderTime(Math.max(0, duration));
        }
    }

    @Override
    public int getThunderDuration() {
        if (world.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            return data.getThunderTime();
        }
        return 0;
    }

    @Override
    public long getGameTime() {
        return world.getGameTime();
    }

    @Override
    public boolean isDayTime() {
        return world.isBrightOutside();
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z) {
        return setSpawnLocation(x, y, z, 0.0F);
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float angle) {
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.storage.LevelData levelData = world.getLevelData();
        boolean ownData = levelData instanceof net.minecraft.world.level.storage.PrimaryLevelData
                && levelData != world.getServer().getWorldData().overworldData();
        if (ownData) {
            // A world with its own level data (Multiverse worlds): Paper writes the spawn there. Going through
            // ServerLevel.setRespawnData would overwrite the server-wide default spawn, including its dimension.
            Location previous = getSpawnLocation();
            ((net.minecraft.world.level.storage.ServerLevelData) levelData).setSpawn(
                    net.minecraft.world.level.storage.LevelData.RespawnData.of(world.dimension(), pos, angle, 0.0F));
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.SpawnChangeEvent(this, previous));
            return true;
        }
        // The overworld (or a vanilla Nether/End sharing its data): the spawn must stay in the overworld's dimension.
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension =
                levelData instanceof net.minecraft.world.level.storage.ServerLevelData data
                        ? data.getRespawnData().dimension() : world.dimension();
        world.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(dimension, pos, angle, 0.0F));
        return true;
    }

    @Override
    public boolean setSpawnLocation(@NotNull org.bukkit.Location location) {
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Spawn location must belong to this world");
        }
        return setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getYaw());
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTrace(@NotNull io.papermc.paper.math.Position start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance,
            @NotNull org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter,
            @Nullable java.util.function.Predicate<? super org.bukkit.block.Block> blockFilter) {
        org.bukkit.Location location = new org.bukkit.Location(this, start.x(), start.y(), start.z());
        org.bukkit.util.RayTraceResult block = rayTraceBlocks(location, direction, maxDistance, fluidCollisionMode,
                ignorePassableBlocks);
        if (block != null && blockFilter != null && block.getHitBlock() != null && !blockFilter.test(block.getHitBlock())) {
            block = null;
        }
        org.bukkit.util.RayTraceResult entity = rayTraceEntities(location, direction, maxDistance, raySize, filter);
        if (block == null) return entity;
        if (entity == null) return block;
        double blockDistance = block.getHitPosition().distanceSquared(location.toVector());
        double entityDistance = entity.getHitPosition().distanceSquared(location.toVector());
        return entityDistance < blockDistance ? entity : block;
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTrace(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance,
            @NotNull org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        org.bukkit.util.RayTraceResult block = rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode,
                ignorePassableBlocks);
        org.bukkit.util.RayTraceResult entity = rayTraceEntities(start, direction, maxDistance, raySize, filter);
        if (block == null) return entity;
        if (entity == null) return block;
        double blockDistance = block.getHitPosition().distanceSquared(start.toVector());
        double entityDistance = entity.getHitPosition().distanceSquared(start.toVector());
        return entityDistance < blockDistance ? entity : block;
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceBlocks(@NotNull io.papermc.paper.math.Position start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance,
            @NotNull org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks,
            @Nullable java.util.function.Predicate<? super org.bukkit.block.Block> filter) {
        org.bukkit.util.RayTraceResult result = rayTraceBlocks(
                new org.bukkit.Location(this, start.x(), start.y(), start.z()), direction, maxDistance,
                fluidCollisionMode, ignorePassableBlocks);
        return result != null && filter != null && result.getHitBlock() != null && !filter.test(result.getHitBlock())
                ? null : result;
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceBlocks(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance,
            @NotNull org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks) {
        if (maxDistance < 0.0D || direction.lengthSquared() == 0.0D) return null;
        org.bukkit.util.Vector normalized = direction.clone().normalize();
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(start.getX(), start.getY(), start.getZ());
        net.minecraft.world.phys.Vec3 to = from.add(normalized.getX() * maxDistance,
                normalized.getY() * maxDistance, normalized.getZ() * maxDistance);
        net.minecraft.world.level.ClipContext.Fluid fluid = switch (fluidCollisionMode) {
            case NEVER -> net.minecraft.world.level.ClipContext.Fluid.NONE;
            case SOURCE_ONLY -> net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY;
            case ALWAYS -> net.minecraft.world.level.ClipContext.Fluid.ANY;
        };
        net.minecraft.world.phys.BlockHitResult hit = world.clip(new net.minecraft.world.level.ClipContext(
                from, to,
                ignorePassableBlocks ? net.minecraft.world.level.ClipContext.Block.COLLIDER
                        : net.minecraft.world.level.ClipContext.Block.OUTLINE,
                fluid, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
        org.bukkit.block.Block block = getBlockAt(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
        org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(hit.getDirection().name());
        net.minecraft.world.phys.Vec3 point = hit.getLocation();
        return new org.bukkit.util.RayTraceResult(new org.bukkit.util.Vector(point.x, point.y, point.z), block, face);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceBlocks(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance,
            @NotNull org.bukkit.FluidCollisionMode fluidCollisionMode) {
        return rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode, false);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceBlocks(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance) {
        return rayTraceBlocks(start, direction, maxDistance, org.bukkit.FluidCollisionMode.NEVER, false);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceEntities(@NotNull io.papermc.paper.math.Position start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance, double raySize,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return rayTraceEntities(new org.bukkit.Location(this, start.x(), start.y(), start.z()), direction,
                maxDistance, raySize, filter);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceEntities(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance, double raySize,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        if (maxDistance < 0.0D || direction.lengthSquared() == 0.0D) return null;
        org.bukkit.util.Vector normalized = direction.clone().normalize();
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(start.getX(), start.getY(), start.getZ());
        net.minecraft.world.phys.Vec3 to = from.add(normalized.getX() * maxDistance,
                normalized.getY() * maxDistance, normalized.getZ() * maxDistance);
        net.minecraft.world.phys.AABB search = new net.minecraft.world.phys.AABB(from, to).inflate(Math.max(0.0D, raySize) + 1.0D);
        org.bukkit.craftbukkit.CraftServer craftServer =
                (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        org.bukkit.entity.Entity bestEntity = null;
        net.minecraft.world.phys.Vec3 bestPoint = null;
        double bestDistance = maxDistance * maxDistance;
        for (net.minecraft.world.entity.Entity candidate : world.getEntities((net.minecraft.world.entity.Entity) null, search)) {
            org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(craftServer, candidate);
            if (bukkit == null || (filter != null && !filter.test(bukkit))) continue;
            java.util.Optional<net.minecraft.world.phys.Vec3> hit = candidate.getBoundingBox().inflate(raySize).clip(from, to);
            if (hit.isEmpty()) continue;
            double distance = hit.get().distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestEntity = bukkit;
                bestPoint = hit.get();
            }
        }
        return bestEntity == null ? null : new org.bukkit.util.RayTraceResult(
                new org.bukkit.util.Vector(bestPoint.x, bestPoint.y, bestPoint.z), bestEntity);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceEntities(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return rayTraceEntities(start, direction, maxDistance, 0.0D, filter);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceEntities(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance) {
        return rayTraceEntities(start, direction, maxDistance, 0.0D, null);
    }

    @Override
    public @Nullable org.bukkit.util.RayTraceResult rayTraceEntities(@NotNull org.bukkit.Location start,
            @NotNull org.bukkit.util.Vector direction, double maxDistance, double raySize) {
        return rayTraceEntities(start, direction, maxDistance, raySize, null);
    }

    private java.util.List<org.bukkit.entity.Entity> entitiesInAABB(net.minecraft.world.phys.AABB aabb,
            java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        java.util.List<org.bukkit.entity.Entity> out = new java.util.ArrayList<>();
        org.bukkit.craftbukkit.CraftServer cs =
            (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        for (net.minecraft.world.entity.Entity e : world.getEntities(null, aabb)) {
            org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(cs, e);
            if (bukkit != null && (filter == null || filter.test(bukkit))) out.add(bukkit);
        }
        return out;
    }

    @Override
    public @NotNull java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(
            @NotNull org.bukkit.util.BoundingBox boundingBox,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return entitiesInAABB(new net.minecraft.world.phys.AABB(
            boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ(),
            boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ()), filter);
    }

    @Override
    public @NotNull java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(
            @NotNull org.bukkit.util.BoundingBox boundingBox) {
        return getNearbyEntities(boundingBox, null);
    }

    @Override
    public @NotNull java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(
            @NotNull org.bukkit.Location location, double x, double y, double z,
            @Nullable java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return entitiesInAABB(new net.minecraft.world.phys.AABB(
            location.getX()-x, location.getY()-y, location.getZ()-z,
            location.getX()+x, location.getY()+y, location.getZ()+z), filter);
    }

    @Override
    public @NotNull java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(
            @NotNull org.bukkit.Location location, double x, double y, double z) {
        return getNearbyEntities(location, x, y, z, null);
    }

    @Override
    public @Nullable org.bukkit.entity.Entity getEntity(@NotNull java.util.UUID uuid) {
        java.util.Objects.requireNonNull(uuid, "uuid");
        net.minecraft.world.entity.Entity entity = world.getEntity(uuid);
        return entity == null ? null : org.bukkit.craftbukkit.entity.CraftEntity.getEntity(
                (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer(), entity);
    }

    private static final net.minecraft.server.level.TicketType LUNARARC_ASYNC_CHUNK = new net.minecraft.server.level.TicketType(
            net.minecraft.server.level.TicketType.NO_TIMEOUT,
            net.minecraft.server.level.TicketType.FLAG_LOADING | net.minecraft.server.level.TicketType.FLAG_SIMULATION | net.minecraft.server.level.TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED);

    @Override
    public @NotNull java.util.concurrent.CompletableFuture<org.bukkit.Chunk> getChunkAtAsync(int x, int z, boolean gen,
            boolean urgent) {
        net.minecraft.server.MinecraftServer server = world.getServer();
        net.minecraft.server.level.ServerChunkCache source = world.getChunkSource();

        // Already resident: hand it straight back without touching the chunk system at all.
        if (server.isSameThread() && source.getChunkNow(x, z) != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(lunararcChunk(x, z));
        }

        java.util.concurrent.CompletableFuture<org.bukkit.Chunk> future = new java.util.concurrent.CompletableFuture<>();
        net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x, z);

        Runnable schedule = () -> {
            try {
                if (source.getChunkNow(x, z) != null) {
                    future.complete(lunararcChunk(x, z));
                    return;
                }
                if (!gen && !isChunkGenerated(x, z)) {
                    future.complete(null);
                    return;
                }

                source.addTicketWithRadius(LUNARARC_ASYNC_CHUNK, pos, 0);
                source.getChunkFuture(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true)
                        .whenComplete((result, thrown) -> server.execute(() -> {
                            // Drop the ticket before completing, so the waiting plugin still runs
                            // against a resident chunk - unloading cannot happen until the chunk
                            // system next runs, which is after this task returns.
                            source.removeTicketWithRadius(LUNARARC_ASYNC_CHUNK, pos, 0);
                            if (thrown != null) {
                                future.completeExceptionally(thrown);
                                return;
                            }
                            future.complete(result != null && result.isSuccess() ? lunararcChunk(x, z) : null);
                        }));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };

        if (server.isSameThread()) schedule.run();
        else server.execute(schedule);
        return future;
    }

    @Override
    public @NotNull java.util.concurrent.CompletableFuture<org.bukkit.Chunk> getChunkAtAsync(int x, int z,
            boolean gen) {
        return getChunkAtAsync(x, z, gen, false);
    }

    @Override
    public @NotNull java.util.concurrent.CompletableFuture<org.bukkit.Chunk> getChunkAtAsync(int x, int z) {
        return getChunkAtAsync(x, z, true, false);
    }

    @Override
    public @NotNull java.util.concurrent.CompletableFuture<org.bukkit.Chunk> getChunkAtAsync(
            @NotNull org.bukkit.Location location, boolean gen) {
        if (location == null) throw new IllegalArgumentException("location cannot be null");
        if (location.getWorld() != null && location.getWorld() != this)
            throw new IllegalArgumentException("Location belongs to another world");
        return getChunkAtAsync(location.getBlockX() >> 4, location.getBlockZ() >> 4, gen, false);
    }

    @Override
    public @NotNull java.util.concurrent.CompletableFuture<org.bukkit.Chunk> getChunkAtAsync(
            @NotNull org.bukkit.Location location) {
        return getChunkAtAsync(location, true);
    }

    private java.util.List<org.bukkit.entity.Entity> allBukkitEntities() {
        java.util.List<org.bukkit.entity.Entity> out = new java.util.ArrayList<>();
        org.bukkit.craftbukkit.CraftServer cs =
            (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        world.getAllEntities().forEach(e -> {
            org.bukkit.entity.Entity bukkit = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(cs, e);
            if (bukkit != null) out.add(bukkit);
        });
        return out;
    }

    @Override
    public @NotNull java.util.Collection<org.bukkit.entity.Entity> getEntitiesByClasses(@NotNull Class<?>... classes) {
        java.util.List<org.bukkit.entity.Entity> out = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : allBukkitEntities())
            for (Class<?> cls : classes) if (cls.isInstance(e)) { out.add(e); break; }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T extends org.bukkit.entity.Entity> java.util.Collection<T> getEntitiesByClass(
            @NotNull Class<T> cls) {
        java.util.List<T> out = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : allBukkitEntities())
            if (cls.isInstance(e)) out.add((T) e);
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T extends org.bukkit.entity.Entity> java.util.Collection<T> getEntitiesByClass(
            @NotNull Class<T>... classes) {
        java.util.List<T> out = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : allBukkitEntities())
            for (Class<T> cls : classes) if (cls.isInstance(e)) { out.add((T) e); break; }
        return out;
    }

    @Override
    public @NotNull java.util.List<org.bukkit.entity.LivingEntity> getLivingEntities() {
        java.util.List<org.bukkit.entity.LivingEntity> out = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : allBukkitEntities())
            if (e instanceof org.bukkit.entity.LivingEntity le) out.add(le);
        return out;
    }

    @Override
    public @Nullable org.bukkit.Location findLightningTarget(@NotNull org.bukkit.Location location) {
        java.util.Objects.requireNonNull(location, "location");
        if (location.getWorld() != null && location.getWorld() != this) throw new IllegalArgumentException("Location belongs to another world");
        org.bukkit.Location rod = findLightningRod(location);
        if (rod != null) return rod;
        return new org.bukkit.Location(this, location.getBlockX(), getHighestBlockYAt(location), location.getBlockZ());
    }

    @Override
    public @Nullable org.bukkit.Location findLightningRod(@NotNull org.bukkit.Location location) {
        java.util.Objects.requireNonNull(location, "location");
        if (location.getWorld() != null && location.getWorld() != this) throw new IllegalArgumentException("Location belongs to another world");
        int radius = 128;
        int minY = getMinHeight();
        int maxY = Math.min(getMaxHeight() - 1, location.getBlockY() + radius);
        int cx = location.getBlockX(), cz = location.getBlockZ();
        double best = Double.MAX_VALUE;
        org.bukkit.Location bestLoc = null;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = maxY; y >= minY; y--) {
                    org.bukkit.block.Block block = getBlockAt(x, y, z);
                    if (block.getType() != org.bukkit.Material.LIGHTNING_ROD) continue;
                    double dist = block.getLocation().distanceSquared(location);
                    if (dist < best) { best = dist; bestLoc = block.getLocation(); }
                    break;
                }
            }
        }
        return bestLoc;
    }

    @Override
    public @NotNull org.bukkit.entity.LightningStrike strikeLightning(@NotNull org.bukkit.Location location) {
        return lunararc$strikeLightning(location, false);
    }

    @Override
    public @NotNull org.bukkit.entity.LightningStrike strikeLightningEffect(@NotNull org.bukkit.Location location) {
        return lunararc$strikeLightning(location, true);
    }

    private @NotNull org.bukkit.entity.LightningStrike lunararc$strikeLightning(@NotNull org.bukkit.Location location,
            boolean effect) {
        java.util.Objects.requireNonNull(location, "location");
        if (location.getWorld() != null && location.getWorld() != this) {
            throw new IllegalArgumentException("Location belongs to another world");
        }
        net.minecraft.world.entity.LightningBolt bolt =
                net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (bolt == null) throw new IllegalStateException("Unable to create lightning entity");
        bolt.snapTo(location.getX(), location.getY(), location.getZ());
        ((io.lunararcdevs.lunararc.common.bridge.LightningBoltBridge) bolt).lunararc$setEffect(effect);
        org.bukkit.craftbukkit.entity.CraftLightningStrike bukkit =
                new org.bukkit.craftbukkit.entity.CraftLightningStrike(
                        (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer(), bolt);
        org.bukkit.event.weather.LightningStrikeEvent event = new org.bukkit.event.weather.LightningStrikeEvent(
                this, bukkit, org.bukkit.event.weather.LightningStrikeEvent.Cause.CUSTOM);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled() && !world.addFreshEntity(bolt)) {
            throw new IllegalStateException("Failed to add lightning entity to world");
        }
        return bukkit;
    }


    @Override
    public @NotNull Collection<org.bukkit.Chunk> getIntersectingChunks(@NotNull org.bukkit.util.BoundingBox box) {
        if (box == null) throw new IllegalArgumentException("box cannot be null");
        int minChunkX = ((int) Math.floor(box.getMinX())) >> 4;
        int maxChunkX = ((int) Math.floor(Math.nextDown(box.getMaxX()))) >> 4;
        int minChunkZ = ((int) Math.floor(box.getMinZ())) >> 4;
        int maxChunkZ = ((int) Math.floor(Math.nextDown(box.getMaxZ()))) >> 4;
        java.util.List<org.bukkit.Chunk> chunks = new java.util.ArrayList<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(getChunkAt(x, z));
            }
        }
        return java.util.Collections.unmodifiableList(chunks);
    }

    @Override
    public @NotNull Map<org.bukkit.plugin.Plugin, Collection<org.bukkit.Chunk>> getPluginChunkTickets() {
        java.util.Map<org.bukkit.plugin.Plugin, java.util.Collection<org.bukkit.Chunk>> result = new java.util.LinkedHashMap<>();
        for (var entry : lunararcPluginChunkTickets.entrySet()) {
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(entry.getKey());
            org.bukkit.Chunk chunk = lunararcChunk(pos.x, pos.z);
            for (org.bukkit.plugin.Plugin plugin : entry.getValue()) {
                result.computeIfAbsent(plugin, ignored -> new java.util.ArrayList<>()).add(chunk);
            }
        }
        result.replaceAll((plugin, chunks) -> java.util.List.copyOf(chunks));
        return java.util.Collections.unmodifiableMap(result);
    }

    @Override
    public @NotNull Collection<org.bukkit.plugin.Plugin> getPluginChunkTickets(int x, int z) {
        java.util.Set<org.bukkit.plugin.Plugin> plugins = lunararcPluginChunkTickets.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
        return plugins == null ? java.util.List.of() : java.util.List.copyOf(plugins);
    }

    @Override
    public boolean addPluginChunkTicket(int x, int z, @NotNull org.bukkit.plugin.Plugin plugin) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        if (!plugin.isEnabled()) throw new IllegalStateException("Plugin " + plugin.getName() + " is not enabled");
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        java.util.Set<org.bukkit.plugin.Plugin> set = lunararcPluginChunkTickets.computeIfAbsent(
                key, ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        boolean added = set.add(plugin);
        if (added) {
            // Use the loader's transient PLUGIN ticket rather than setChunkForced().
            // Plugin identity remains in the concrete Bukkit-side set so cleanup can
            // release the shared NMS ticket when the last LunarArc owner disappears.
            lunararc$addPluginRegionTicket(x, z);
            world.getChunk(x, z);
        }
        return added;
    }

    @Override
    public boolean removePluginChunkTicket(int x, int z, @NotNull org.bukkit.plugin.Plugin plugin) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        java.util.Set<org.bukkit.plugin.Plugin> set = lunararcPluginChunkTickets.get(key);
        if (set == null || !set.remove(plugin)) return false;
        if (set.isEmpty()) {
            lunararcPluginChunkTickets.remove(key, set);
            lunararc$removePluginRegionTicketIfUnused(x, z);
        }
        return true;
    }

    @Override
    public void removePluginChunkTickets(@NotNull org.bukkit.plugin.Plugin plugin) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        for (var entry : java.util.List.copyOf(lunararcPluginChunkTickets.entrySet())) {
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(entry.getKey());
            removePluginChunkTicket(pos.x, pos.z, plugin);
        }
    }

    @Override
    public boolean hasBonusChest() {
        return getHandle().getServer().getWorldData().worldGenOptions().generateBonusChest();
    }

    @Override
    public java.nio.file.Path getWorldPath() {
        return getWorldFolder().toPath();
    }

    @Override
    public void save(boolean flush) {
        boolean oldSave = this.world.noSave;
        this.world.noSave = false;
        this.world.save(null, flush, false);
        this.world.noSave = oldSave;
    }

    @Override
    public void getChunkAtAsync(int x, int z, boolean gen, boolean urgent, @NotNull java.util.function.Consumer<? super org.bukkit.Chunk> cb) {
        getChunkAtAsync(x, z, gen, urgent).whenComplete((chunk, thrown) -> cb.accept(thrown == null ? chunk : null));
    }

    @Override
    public void getChunksAtAsync(int minX, int minZ, int maxX, int maxZ, boolean urgent, @NotNull Runnable cb) {
        java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> futures = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                futures.add(getChunkAtAsync(x, z, true, urgent));
            }
        }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ignored, thrown) -> cb.run());
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTrace(java.util.function.Consumer<io.papermc.paper.raytracing.PositionedRayTraceConfigurationBuilder> builderConsumer) {
        io.papermc.paper.raytracing.PositionedRayTraceConfigurationBuilderImpl builder = new io.papermc.paper.raytracing.PositionedRayTraceConfigurationBuilderImpl();

        builderConsumer.accept(builder);
        com.google.common.base.Preconditions.checkArgument(builder.start != null, "Start location cannot be null");
        com.google.common.base.Preconditions.checkArgument(builder.direction != null, "Direction vector cannot be null");
        com.google.common.base.Preconditions.checkArgument(builder.maxDistance.isPresent(), "Max distance must be set");
        com.google.common.base.Preconditions.checkArgument(!builder.targets.isEmpty(), "At least one target");

        final double maxDistance = builder.maxDistance.getAsDouble();
        if (builder.targets.contains(io.papermc.paper.raytracing.RayTraceTarget.ENTITY)) {
            if (builder.targets.contains(io.papermc.paper.raytracing.RayTraceTarget.BLOCK)) {
                return this.rayTrace(builder.start, builder.direction, maxDistance, builder.fluidCollisionMode, builder.blockCollisionMode == io.papermc.paper.raytracing.BlockCollisionMode.COLLIDER, builder.raySize, builder.entityFilter, builder.blockFilter);
            }
            return this.rayTraceEntities(builder.start, builder.direction, maxDistance, builder.raySize, builder.entityFilter);
        }
        return this.rayTraceBlocks(builder.start, builder.direction, maxDistance, builder.fluidCollisionMode, builder.blockCollisionMode == io.papermc.paper.raytracing.BlockCollisionMode.COLLIDER, builder.blockFilter);
    }
}
