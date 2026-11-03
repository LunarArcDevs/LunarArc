package io.lunararcdevs.lunararc.common.bridge;

import net.minecraft.server.WorldLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.craftbukkit.CraftServer;

public interface MinecraftServerBridge {
    void lunararc$queueTask(Runnable runnable);
    CraftServer lunararc$getCraftServer();
    void lunararc$setCraftServer(CraftServer craftServer);
    boolean lunararc$isTickingWorlds();
    double[] lunararc$getTps();
    void lunararc$setPluginAllowingSleep(String pluginName, boolean value);
    WorldLoader.DataLoadContext lunararc$getDataLoadContext();

    /** Add a Bukkit-created level to the real MinecraftServer level map and notify the active loader. */
    void lunararc$addLevel(ServerLevel level);

    /** Remove a Bukkit-created level from the real MinecraftServer level map and notify the active loader. */
    void lunararc$removeLevel(ServerLevel level);

    /** Initialize a dynamically-created world on the real server before it is exposed to plugins. */
    void lunararc$initializeDynamicLevel(ServerLevel level, PrimaryLevelData data, boolean bonusChest);

    /** Prepare forced/persistent chunks for a dynamically-created world. */
    void lunararc$prepareDynamicLevel(ServerLevel level);

    /** Loader-specific world lifecycle hook implemented by a loader mixin on MinecraftServer. */
    default void lunararc$loaderLevelLoad(ServerLevel level) {}

    /** Loader-specific world lifecycle hook implemented by a loader mixin on MinecraftServer. */
    default void lunararc$loaderLevelUnload(ServerLevel level) {}

    /** Forge/NeoForge hook used when dynamic Bukkit worlds change the loader-owned level set. */
    default void lunararc$loaderMarkLevelsDirty() {}

    /** Forge/NeoForge hook used to restore loader persistent chunk tickets for a created level. */
    default void lunararc$loaderReinstatePersistentChunks(ServerLevel level) {
        net.minecraft.world.level.TicketStorage tickets = level.getDataStorage().get(net.minecraft.world.level.TicketStorage.TYPE);
        if (tickets != null) tickets.activateAllDeactivatedTickets();
    }

    /** True when the active loader has a native cancellable block-break event in destroyBlock. */
    default boolean lunararc$loaderHandlesBlockBreakEvent() { return false; }

    /** True when the active loader has a native cancellable block-place event in BlockItem placement. */
    default boolean lunararc$loaderHandlesBlockPlaceEvent() { return false; }

    /** True when the active loader has a cancellable fresh-entity join event. */
    default boolean lunararc$loaderHandlesEntityJoinEvent() { return false; }

    /** True when the active loader has a native cancellable teleport event for ender pearls, chorus fruit and /tp. */
    default boolean lunararc$loaderHandlesEntityTeleportEvent() { return false; }

    /** Forge registry lifecycle boundary used around dynamic world/plugin phases. */
    default void lunararc$loaderLockRegistries() {}

    /** Forge registry lifecycle boundary used around dynamic world/plugin phases. */
    default void lunararc$loaderUnlockRegistries() {}

    default CraftServer lunararc$requireCraftServer() {
        CraftServer craftServer = lunararc$getCraftServer();
        if (craftServer == null) {
            throw new IllegalStateException("LunarArc CraftServer is not initialized for this MinecraftServer");
        }
        return craftServer;
    }
}
