package io.lunararcdevs.lunararc.forge.event;

import io.lunararcdevs.lunararc.common.mod.util.LunarArcBlockBreakCapture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ForgeBlockBreakEvents {
    private ForgeBlockBreakEvents() {}

    public static void register(IEventBus bus) { bus.addListener(ForgeBlockBreakEvents::onBreak); }

    private static void onBreak(BlockEvent.BreakEvent forgeEvent) {
        if (!(forgeEvent.getPlayer() instanceof ServerPlayer player)) return;
        org.bukkit.event.block.BlockBreakEvent bukkit = LunarArcBlockBreakCapture.matching(player, forgeEvent.getPos());
        if (bukkit == null) {
            bukkit = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockBreakEvent(
                    serverLevel(player), forgeEvent.getPos(), player);
        }
        forgeEvent.setCanceled(bukkit.isCancelled());
        forgeEvent.setExpToDrop(bukkit.getExpToDrop());
    }

    // Loom compiles Forge against SRG names; ServerPlayer#serverLevel is a real Mojang name that
    // never resolves directly here, so go through the reflection bridge instead.
    private static net.minecraft.server.level.ServerLevel serverLevel(ServerPlayer player) {
        try {
            return (net.minecraft.server.level.ServerLevel) io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge
                    .getMethod(player.getClass(), "serverLevel", new Class<?>[0]).invoke(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the player's server level", e);
        }
    }
}
