package io.lunararcdevs.lunararc.common.mixin.core.network;

import com.mojang.authlib.properties.Property;
import io.lunararcdevs.lunararc.common.bridge.ConnectionBridge;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.SocketAddress;
import java.util.UUID;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionBridge {

    @Shadow public Channel channel;

    @Unique public Channel n;
    @Unique private String lunararc$hostname = "";
    @Unique private SocketAddress lunararc$rawAddress;
    @Unique private SocketAddress lunararc$haProxyAddress;
    @Unique private UUID lunararc$spoofedUuid;
    @Unique private Property[] lunararc$spoofedProfile;
    @Unique private ServerPlayer lunararc$loginPlayer;

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void lunararc$channelActive(ChannelHandlerContext ctx, CallbackInfo ci) {
        this.n = this.channel;
        this.lunararc$rawAddress = this.channel.remoteAddress();
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void lunararc$logExceptionCaught(ChannelHandlerContext ctx, Throwable exception, CallbackInfo ci) {
        if (!io.lunararcdevs.lunararc.common.LunarArcDebug.NETWORK) return;
        java.io.StringWriter trace = new java.io.StringWriter();
        exception.printStackTrace(new java.io.PrintWriter(trace));
        io.lunararcdevs.lunararc.common.LunarArcDebug.network(
                "Connection.exceptionCaught remoteAddress={} exception={}", this.lunararc$rawAddress, trace);
    }

    @Override
    public String lunararc$getHostname() {
        return this.lunararc$hostname;
    }

    @Override
    public void lunararc$setHostname(String hostname) {
        this.lunararc$hostname = java.util.Objects.requireNonNull(hostname, "hostname");
    }

    @Override
    public SocketAddress lunararc$getRawAddress() {
        return this.lunararc$rawAddress;
    }

    @Override
    public SocketAddress lunararc$getHAProxyAddress() {
        return this.lunararc$haProxyAddress;
    }

    @Override
    public void lunararc$setHAProxyAddress(SocketAddress address) {
        this.lunararc$haProxyAddress = address;
    }

    @Override
    public UUID lunararc$getSpoofedUuid() {
        return this.lunararc$spoofedUuid;
    }

    @Override
    public void lunararc$setSpoofedUuid(UUID uuid) {
        this.lunararc$spoofedUuid = uuid;
    }

    @Override
    public Property[] lunararc$getSpoofedProfile() {
        return this.lunararc$spoofedProfile;
    }

    @Override
    public void lunararc$setSpoofedProfile(Property[] profile) {
        this.lunararc$spoofedProfile = profile;
    }

    @Override
    public ServerPlayer lunararc$getLoginPlayer() {
        return this.lunararc$loginPlayer;
    }

    @Override
    public void lunararc$setLoginPlayer(ServerPlayer player) {
        this.lunararc$loginPlayer = player;
    }

    @Override
    public Channel lunararc$getChannel() {
        return this.channel;
    }
}
