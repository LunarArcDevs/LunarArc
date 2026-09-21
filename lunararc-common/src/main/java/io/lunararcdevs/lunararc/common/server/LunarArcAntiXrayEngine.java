package io.lunararcdevs.lunararc.common.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LunarArcAntiXrayEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(LunarArcAntiXrayEngine.class);
    private static final java.util.Map<ServerLevel, LunarArcAntiXrayEngine> ENGINES = new ConcurrentHashMap<>();
    private static final Set<Block> SOLID_EXEMPT = Set.of(
            Blocks.SPAWNER, Blocks.BARRIER, Blocks.SHULKER_BOX, Blocks.SLIME_BLOCK, Blocks.MANGROVE_ROOTS);

    private final boolean enabled;
    private final int maxBlockHeight;
    private final int updateRadius;
    private final boolean lavaObscures;
    private final Set<BlockState> hiddenStates;

    private LunarArcAntiXrayEngine(ServerLevel level) {
        var config = ((io.lunararcdevs.lunararc.common.bridge.LevelBridge) level)
                .lunararc$getPaperConfiguration().anticheat.antiXray;
        boolean configuredEnabled = config.enabled;
        int engineMode = config.engineMode;
        if (configuredEnabled && engineMode != 1) {
            LOGGER.warn("anticheat.anti-xray.engine-mode {} is not implemented yet (only 1/HIDE is) "
                    + "- anti-xray is disabled for {}", engineMode, level.dimension().location());
            configuredEnabled = false;
        }

        this.enabled = configuredEnabled;
        this.maxBlockHeight = config.maxBlockHeight;
        this.updateRadius = config.updateRadius;
        this.lavaObscures = config.lavaObscures;

        Set<BlockState> states = new HashSet<>();
        if (this.enabled) {
            for (String id : config.hiddenBlocks) {
                ResourceLocation location = ResourceLocation.tryParse(id);
                Block block = location == null ? null : BuiltInRegistries.BLOCK.get(location);
                if (block == null || block.defaultBlockState().isAir()) continue;
                states.addAll(block.getStateDefinition().getPossibleStates());
            }
            if (states.isEmpty()) {
                LOGGER.warn("anticheat.anti-xray.hidden-blocks resolved to no real blocks - "
                        + "anti-xray is enabled but has nothing to hide for {}", level.dimension().location());
            }
        }
        this.hiddenStates = states;

        if (this.enabled) {
            LOGGER.info("Anti-xray HIDE engine active for {}: {} hidden block state(s), "
                            + "max-block-height={}, update-radius={}",
                    level.dimension().location(), states.size(), maxBlockHeight, updateRadius);
        }
    }

    public static LunarArcAntiXrayEngine forLevel(ServerLevel level) {
        return ENGINES.computeIfAbsent(level, LunarArcAntiXrayEngine::new);
    }

    /** Clears the cached engine for a level so a config edit takes effect on its next (re)load. */
    public static void invalidate(ServerLevel level) {
        ENGINES.remove(level);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isHidden(BlockState state) {
        return hiddenStates.contains(state);
    }

    private boolean isSolidForReveal(BlockState state) {
        if (state.isAir()) return false;
        if (lavaObscures && state == Blocks.LAVA.defaultBlockState()) return true;
        if (SOLID_EXEMPT.contains(state.getBlock())) return false;
        return state.blocksMotion();
    }

    public LevelChunkSection[] obfuscateForSend(LevelChunk chunk) {
        if (!enabled || hiddenStates.isEmpty()) return chunk.getSections();

        LevelChunkSection[] real = chunk.getSections();
        int minBuildHeight = chunk.getMinBuildHeight();
        LevelChunkSection[] result = null;
        for (int i = 0; i < real.length; i++) {
            int sectionMinY = minBuildHeight + (i << 4);
            if (sectionMinY > maxBlockHeight) continue;
            LevelChunkSection obfuscated = obfuscateSection(chunk, real, i, sectionMinY);
            if (obfuscated == null) continue;
            if (result == null) result = real.clone();
            result[i] = obfuscated;
        }
        return result == null ? real : result;
    }

    private LevelChunkSection obfuscateSection(LevelChunk chunk, LevelChunkSection[] real, int index, int sectionMinY) {
        LevelChunkSection section = real[index];
        if (section.hasOnlyAir()) return null;

        BlockState decoy = findDecoyState(section);
        if (decoy == null) return null;

        LevelChunkSection copy = null;
        for (int localY = 0; localY < 16; localY++) {
            int worldY = sectionMinY + localY;
            if (worldY > maxBlockHeight) break;
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    BlockState state = section.getBlockState(localX, localY, localZ);
                    if (!isHidden(state)) continue;
                    int worldX = (chunk.getPos().x << 4) + localX;
                    int worldZ = (chunk.getPos().z << 4) + localZ;
                    if (isExposed(chunk, real, index, localX, localY, localZ, worldX, worldY, worldZ)) continue;
                    if (copy == null) {
                        copy = new LevelChunkSection(section.getStates().copy(), section.getBiomes());
                    }
                    copy.setBlockState(localX, localY, localZ, decoy, false);
                }
            }
        }
        return copy;
    }

    private BlockState findDecoyState(LevelChunkSection section) {
        for (int localY = 0; localY < 16; localY++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    BlockState state = section.getBlockState(localX, localY, localZ);
                    if (!isHidden(state) && isSolidForReveal(state)) return state;
                }
            }
        }
        return null;
    }

    private boolean isExposed(LevelChunk chunk, LevelChunkSection[] real, int index,
            int localX, int localY, int localZ, int worldX, int worldY, int worldZ) {
        // Vertical neighbours: same chunk column, adjacent section when crossing a boundary.
        if (!isNeighborSolid(real, index, localX, localY - 1, localZ, chunk, worldX, worldY - 1, worldZ)) return true;
        if (!isNeighborSolid(real, index, localX, localY + 1, localZ, chunk, worldX, worldY + 1, worldZ)) return true;
        // Horizontal neighbours: same section unless at the chunk edge.
        if (!isNeighborSolid(real, index, localX - 1, localY, localZ, chunk, worldX - 1, worldY, worldZ)) return true;
        if (!isNeighborSolid(real, index, localX + 1, localY, localZ, chunk, worldX + 1, worldY, worldZ)) return true;
        if (!isNeighborSolid(real, index, localX, localY, localZ - 1, chunk, worldX, worldY, worldZ - 1)) return true;
        if (!isNeighborSolid(real, index, localX, localY, localZ + 1, chunk, worldX, worldY, worldZ + 1)) return true;
        return false;
    }

    private boolean isNeighborSolid(LevelChunkSection[] real, int index, int localX, int localY, int localZ,
            LevelChunk chunk, int worldX, int worldY, int worldZ) {
        if (localX >= 0 && localX <= 15 && localZ >= 0 && localZ <= 15) {
            if (localY >= 0 && localY <= 15) {
                return isSolidForReveal(real[index].getBlockState(localX, localY, localZ));
            }
            int neighborIndex = localY < 0 ? index - 1 : index + 1;
            if (neighborIndex < 0 || neighborIndex >= real.length) return true; // world floor/ceiling
            LevelChunkSection neighborSection = real[neighborIndex];
            int neighborLocalY = localY < 0 ? 15 : 0;
            if (neighborSection.hasOnlyAir()) return false;
            return isSolidForReveal(neighborSection.getBlockState(localX, neighborLocalY, localZ));
        }
        LevelChunk neighborChunk = chunk.getLevel().getChunkSource().getChunkNow(worldX >> 4, worldZ >> 4);
        if (neighborChunk == null) return true;
        return isSolidForReveal(neighborChunk.getBlockState(new BlockPos(worldX, worldY, worldZ)));
    }

    public void onBlockChange(ServerLevel level, BlockPos pos, BlockState newState, BlockState oldState) {
        if (!enabled || oldState == null) return;
        if (!(isSolidForReveal(oldState) && !isSolidForReveal(newState))) return;
        if (pos.getY() > maxBlockHeight + updateRadius - 1) return;
        revealNear(level, pos);
    }

    public void onBlockInteractStart(ServerLevel level, BlockPos pos) {
        if (!enabled) return;
        if (pos.getY() > maxBlockHeight + updateRadius - 1) return;
        revealNear(level, pos);
    }

    private void revealNear(ServerLevel level, BlockPos pos) {
        for (BlockPos neighbor : neighborsWithinTwo(pos)) {
            net.minecraft.world.level.chunk.LevelChunk chunk =
                    level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
            BlockState state = chunk == null ? null : chunk.getBlockState(neighbor);
            if (state != null && isHidden(state)) {
                level.getChunkSource().blockChanged(neighbor);
            }
        }
    }

    private List<BlockPos> neighborsWithinTwo(BlockPos pos) {
        if (updateRadius <= 0) return List.of();
        if (updateRadius == 1) {
            return List.of(pos.west(), pos.east(), pos.below(), pos.above(), pos.north(), pos.south());
        }
        // updateRadius >= 2: real Paper's fixed "up to two orthogonal steps" neighbour set.
        BlockPos west = pos.west();
        BlockPos east = pos.east();
        BlockPos below = pos.below();
        BlockPos above = pos.above();
        return List.of(
                west, west.west(), west.below(), west.above(), west.north(), west.south(),
                east, east.east(), east.below(), east.above(), east.north(), east.south(),
                below, below.below(), below.north(), below.south(),
                above, above.above(), above.north(), above.south(),
                pos.north(), pos.north().north(),
                pos.south(), pos.south().south());
    }
}
