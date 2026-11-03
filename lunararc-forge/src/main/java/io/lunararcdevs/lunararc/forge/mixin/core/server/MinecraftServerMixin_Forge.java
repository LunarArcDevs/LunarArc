package io.lunararcdevs.lunararc.forge.mixin.core.server;

import io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import net.minecraftforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin_Forge implements MinecraftServerBridge {
    @Shadow(remap = false)
    public abstract void markWorldsDirty();

    @Override
    public void lunararc$loaderLevelLoad(ServerLevel level) {
        LevelEvent.Load.BUS.post(new LevelEvent.Load(level));
    }

    @Override
    public void lunararc$loaderLevelUnload(ServerLevel level) {
        LevelEvent.Unload.BUS.post(new LevelEvent.Unload(level));
    }

    @Override
    public void lunararc$loaderMarkLevelsDirty() {
        this.markWorldsDirty();
    }

    private static List<IForgeRegistry<?>> lunararc$mutableRegistries() {
        return List.of(
                ForgeRegistries.BLOCKS,
                ForgeRegistries.ITEMS,
                ForgeRegistries.MOB_EFFECTS,
                ForgeRegistries.POTIONS,
                ForgeRegistries.ENTITY_TYPES,
                ForgeRegistries.BLOCK_ENTITY_TYPES,
                ForgeRegistries.BIOMES);
    }

    @Override
    public void lunararc$loaderLockRegistries() {
        for (IForgeRegistry<?> registry : lunararc$mutableRegistries()) {
            if (registry instanceof ForgeRegistry<?> forgeRegistry) {
                forgeRegistry.freeze();
            }
        }
    }

    @Override
    public void lunararc$loaderUnlockRegistries() {
        for (IForgeRegistry<?> registry : lunararc$mutableRegistries()) {
            if (registry instanceof ForgeRegistry<?> forgeRegistry) {
                forgeRegistry.unfreeze();
            }
        }
    }

    @Override
    public boolean lunararc$loaderHandlesBlockBreakEvent() {
        return true;
    }

    @Override
    public boolean lunararc$loaderHandlesBlockPlaceEvent() {
        return true;
    }

    @Override
    public boolean lunararc$loaderHandlesEntityJoinEvent() {
        return true;
    }

    @Override
    public boolean lunararc$loaderHandlesEntityTeleportEvent() {
        return true;
    }
}
