package io.lunararcdevs.lunararc.neoforge;

import io.lunararcdevs.lunararc.common.LunarArcClientSideGuard;
import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import io.lunararcdevs.lunararc.neoforge.command.NeoForgeCommandHook;
import io.lunararcdevs.lunararc.neoforge.server.NeoForgeServerLifecycle;
import io.lunararcdevs.lunararc.neoforge.event.NeoForgeBlockBreakEvents;
import io.lunararcdevs.lunararc.neoforge.event.NeoForgeBlockPlaceEvents;
import io.lunararcdevs.lunararc.neoforge.event.NeoForgeEntityTeleportEvents;
import io.lunararcdevs.lunararc.neoforge.event.NeoForgeEntityJoinEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("lunararc")
public final class LunarArcNeoForge {

    public LunarArcNeoForge(IEventBus modBus) {
        // Before anything is wired up: FML surfaces an exception thrown from a mod constructor
        // on its mod-loading error screen, so this is what a client user actually reads.
        LunarArcClientSideGuard.requireDedicatedServer(FMLEnvironment.dist == Dist.CLIENT);
        LunarArcServer.installPlatform("NeoForge", LunarArcNeoForge.class.getClassLoader());
        io.lunararcdevs.lunararc.common.config.IncompatibleList.screenLoadedMods(
                io.lunararcdevs.lunararc.common.mod.LunarArcModListReflection.loadedMods(
                        net.neoforged.fml.ModList.get().getMods()));
        NeoForgeCommandHook.install();
        NeoForgeServerLifecycle.register();
        NeoForgeBlockBreakEvents.register();
        NeoForgeBlockPlaceEvents.register();
        NeoForgeEntityTeleportEvents.register();
        NeoForgeEntityJoinEvents.register();
    }
}
