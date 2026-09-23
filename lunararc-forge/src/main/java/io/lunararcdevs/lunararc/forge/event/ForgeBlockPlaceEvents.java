package io.lunararcdevs.lunararc.forge.event;

import io.lunararcdevs.lunararc.common.mod.util.LunarArcBlockPlaceCapture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ForgeBlockPlaceEvents {
    private ForgeBlockPlaceEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(ForgeBlockPlaceEvents::onPlace);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent forgeEvent) {
        if (!(forgeEvent.getEntity() instanceof ServerPlayer player)) return;

        org.bukkit.event.block.BlockPlaceEvent bukkit = LunarArcBlockPlaceCapture.matching(player, forgeEvent.getPos());
        if (bukkit == null) {
            net.minecraftforge.common.util.BlockSnapshot snapshot = forgeEvent.getBlockSnapshot();
            bukkit = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(
                    serverLevel(player), forgeEvent.getPos(), player, InteractionHand.MAIN_HAND,
                    forgeEvent.getPlacedBlock(), snapshot.getReplacedBlock(), snapshot.getTag());
        }
        forgeEvent.setCanceled(bukkit != null && bukkit.isCancelled());
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
