package io.lunararcdevs.lunararc.forge.network;

import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge;
import io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge;
import io.lunararcdevs.lunararc.common.network.LunarArcRawPayload;
import io.lunararcdevs.lunararc.common.network.LunarArcPluginChannelPolicy;
import io.lunararcdevs.lunararc.forge.bridge.ForgeNetworkRegistryBridge;
import io.netty.buffer.Unpooled;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.NetworkRegistry;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.Plugin;

public final class ForgePluginMessaging {
    private static final ConcurrentMap<ResourceLocation, EventNetworkChannel> CHANNELS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> BLOCKED = ConcurrentHashMap.newKeySet();

    private ForgePluginMessaging() {}

    public static void ensureChannel(String channel) {
        String corrected = LunarArcPluginChannelPolicy.correctedChannel(channel);
        ResourceLocation id = tryParse(corrected);
        if (id == null || CHANNELS.containsKey(id) || BLOCKED.contains(id)) return;

        synchronized (ForgePluginMessaging.class) {
            if (CHANNELS.containsKey(id) || BLOCKED.contains(id)) return;
            if (NetworkRegistry.findTarget(id) != null) {
                BLOCKED.add(id);
                LunarArcPluginChannelPolicy.reportNativeConflict("Forge", id, "channel");
                return;
            }

            boolean wasLocked = ForgeNetworkRegistryBridge.isLocked();
            try {
                ForgeNetworkRegistryBridge.setLocked(false);
                EventNetworkChannel nativeChannel = ChannelBuilder.named(id)
                        .acceptedVersions((status, version) -> true)
                        .optional()
                        .eventNetworkChannel();
                nativeChannel.addListener((CustomPayloadEvent event) -> receive(id, event));
                CHANNELS.put(id, nativeChannel);
            io.lunararcdevs.lunararc.common.network.LunarArcPluginMessageOwnership.markNativeInbound(id);
            } finally {
                ForgeNetworkRegistryBridge.setLocked(wasLocked);
            }
        }
    }

    public static boolean sendIfManaged(CraftPlayer player, Plugin plugin, String channel, byte[] message) {
        String corrected = LunarArcPluginChannelPolicy.correctedChannel(channel);
        ResourceLocation id = tryParse(corrected);
        if (id == null) return false;
        EventNetworkChannel nativeChannel = CHANNELS.get(id);
        if (nativeChannel == null && !BLOCKED.contains(id)) return false;

        LunarArcPluginChannelPolicy.validateManagedOutbound(player, plugin, channel, message);
        Object connection = connectionOf(player.getHandle());
        if (nativeChannel == null || connection == null) return true;

        ServerCommonPacketListenerBridge bridge = (ServerCommonPacketListenerBridge) connection;
        if (!LunarArcPluginChannelPolicy.clientRegistered(player, corrected)) return true;

        nativeChannel.send(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)), bridge.lunararc$getConnection());
        return true;
    }

    private static void receive(ResourceLocation id, CustomPayloadEvent event) {
        FriendlyByteBuf buffer = event.getPayload();
        if (buffer == null) return;

        byte[] data = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), data);
        CustomPayloadEvent.Context context = event.getSource();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            org.bukkit.entity.Entity bukkit = ((EntityBridge) (Object) sender).lunararc$getBukkitEntity();
            if (bukkit instanceof Player player) {
                io.lunararcdevs.lunararc.common.network.LunarArcPluginMessageDispatcher
                        .dispatch(serverOf(sender), player, id, data);
            }
        });
    }

    // Loom compiles Forge against SRG names; these are all real Mojang names that never resolve
    // directly here, so go through the reflection bridge instead.
    private static ResourceLocation tryParse(String value) {
        try {
            return (ResourceLocation) LunarArcReflectionBridge
                    .getMethod(ResourceLocation.class, "tryParse", new Class<?>[]{String.class}).invoke(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to parse resource location " + value, e);
        }
    }

    private static Object connectionOf(ServerPlayer player) {
        try {
            return LunarArcReflectionBridge.getField(player.getClass(), "connection").get(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the player's connection", e);
        }
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        try {
            return (MinecraftServer) LunarArcReflectionBridge.getField(player.getClass(), "server").get(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the player's server", e);
        }
    }
}
