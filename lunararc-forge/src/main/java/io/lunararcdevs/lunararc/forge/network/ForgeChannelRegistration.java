package io.lunararcdevs.lunararc.forge.network;

import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge;
import io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraftforge.event.network.ChannelRegistrationChangeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.bukkit.entity.Player;

public final class ForgeChannelRegistration {
    private ForgeChannelRegistration() {}

    public static void register(IEventBus bus) {
        bus.addListener(ForgeChannelRegistration::onRegistrationChange);
    }

    private static void onRegistrationChange(ChannelRegistrationChangeEvent event) {
        Object listener = packetListener(event.getSource());
        if (!(listener instanceof ServerCommonPacketListenerImpl common)) return;

        ServerCommonPacketListenerBridge bridge = (ServerCommonPacketListenerBridge) (Object) common;
        ServerPlayer serverPlayer = bridge.lunararc$getPlayer();
        if (serverPlayer == null) return;

        Runnable task = () -> {
            org.bukkit.entity.Entity entity = ((EntityBridge) (Object) serverPlayer).lunararc$getBukkitEntity();
            if (!(entity instanceof Player player)) return;

            switch (event.getType()) {
                case REGISTER -> event.getChannels().forEach(channel -> bridge.lunararc$addLoaderPluginChannel(player, channel.toString()));
                case UNREGISTER -> event.getChannels().forEach(channel -> bridge.lunararc$removeLoaderPluginChannel(player, channel.toString()));
            }
        };
        MinecraftServer server = serverOf(serverPlayer);
        if (isSameThread(server)) task.run();
        else execute(server, task);
    }

    // Loom compiles Forge against SRG names, but these are real Mojang names that never resolve
    // directly here, so go through the reflection bridge instead.
    private static Object packetListener(net.minecraft.network.Connection connection) {
        try {
            return LunarArcReflectionBridge
                    .getMethod(connection.getClass(), "getPacketListener", new Class<?>[0]).invoke(connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the connection's packet listener", e);
        }
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        try {
            return (MinecraftServer) LunarArcReflectionBridge.getField(player.getClass(), "server").get(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the player's server", e);
        }
    }

    private static boolean isSameThread(MinecraftServer server) {
        try {
            return (boolean) LunarArcReflectionBridge
                    .getMethod(server.getClass(), "isSameThread", new Class<?>[0]).invoke(server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to check the server thread", e);
        }
    }

    private static void execute(MinecraftServer server, Runnable task) {
        try {
            LunarArcReflectionBridge
                    .getMethod(server.getClass(), "execute", new Class<?>[]{Runnable.class}).invoke(server, task);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to schedule work on the server thread", e);
        }
    }
}
