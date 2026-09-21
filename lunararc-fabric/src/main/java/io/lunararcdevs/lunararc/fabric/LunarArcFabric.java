package io.lunararcdevs.lunararc.fabric;

import io.lunararcdevs.lunararc.common.LunarArcClientSideGuard;
import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import io.lunararcdevs.lunararc.fabric.event.FabricBlockBreakEvents;
import io.lunararcdevs.lunararc.fabric.permissions.FabricPermissionsApi;
import io.lunararcdevs.lunararc.fabric.server.FabricServerLifecycle;
import io.lunararcdevs.lunararc.fabric.network.FabricChannelRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class LunarArcFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        LunarArcClientSideGuard.requireDedicatedServer(
                FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT);
        LunarArcServer.installPlatform("Fabric", LunarArcFabric.class.getClassLoader());
        io.lunararcdevs.lunararc.common.config.IncompatibleList.screenLoadedMods(
                FabricLoader.getInstance().getAllMods().stream()
                        .collect(java.util.HashMap::new,
                                (map, mod) -> map.put(mod.getMetadata().getId(),
                                        mod.getMetadata().getVersion().getFriendlyString()),
                                java.util.HashMap::putAll));
        FabricServerLifecycle.register();
        FabricChannelRegistration.register();
        FabricBlockBreakEvents.register();
        FabricPermissionsApi.register();
    }
}
