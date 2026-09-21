package io.lunararcdevs.lunararc.quilt;

import io.lunararcdevs.lunararc.common.LunarArcClientSideGuard;
import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import io.lunararcdevs.lunararc.quilt.event.QuiltBlockBreakEvents;
import io.lunararcdevs.lunararc.quilt.network.QuiltChannelRegistration;
import io.lunararcdevs.lunararc.quilt.permissions.QuiltPermissionsApi;
import io.lunararcdevs.lunararc.quilt.server.QuiltServerLifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class LunarArcQuilt implements ModInitializer {
    @Override
    public void onInitialize() {
        // Quilt runs LunarArc through its Fabric compatibility layer - hence ModInitializer
        // above - so the Fabric loader API answers this here too.
        LunarArcClientSideGuard.requireDedicatedServer(
                FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT);
        LunarArcServer.installPlatform("Quilt", LunarArcQuilt.class.getClassLoader());
        io.lunararcdevs.lunararc.common.config.IncompatibleList.screenLoadedMods(
                FabricLoader.getInstance().getAllMods().stream()
                        .collect(java.util.HashMap::new,
                                (map, mod) -> map.put(mod.getMetadata().getId(),
                                        mod.getMetadata().getVersion().getFriendlyString()),
                                java.util.HashMap::putAll));
        QuiltServerLifecycle.register();
        QuiltChannelRegistration.register();
        QuiltBlockBreakEvents.register();
        QuiltPermissionsApi.register();
    }
}
