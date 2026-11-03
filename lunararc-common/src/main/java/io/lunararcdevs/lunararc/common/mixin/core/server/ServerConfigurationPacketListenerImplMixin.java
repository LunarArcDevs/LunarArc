package io.lunararcdevs.lunararc.common.mixin.core.server;

import com.mojang.authlib.GameProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.lunararcdevs.lunararc.common.bridge.ConnectionBridge;
import io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import java.net.SocketAddress;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin implements io.lunararcdevs.lunararc.common.bridge.ConfigurationConnectionBridge {

    /** Paper's configuration-phase connection object, handed to configuration events. */
    public io.papermc.paper.connection.PaperPlayerConfigurationConnection paperConnection;

    @Override
    public io.papermc.paper.connection.PaperPlayerConfigurationConnection lunararc$paperConnection() {
        return this.paperConnection;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lunararc$createPaperConnection(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection,
            net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        this.paperConnection = new io.papermc.paper.connection.PaperPlayerConfigurationConnection((ServerConfigurationPacketListenerImpl) (Object) this);
    }

    /** Paper: PlayerConnectionInitialConfigureEvent as soon as the configuration phase begins. */
    @Inject(method = "startConfiguration", at = @At("HEAD"))
    private void lunararc$initialConfigure(CallbackInfo ci) {
        new io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent(this.paperConnection).callEvent();
    }

    /** Paper: a task that runs AsyncPlayerConnectionConfigureEvent off the main thread before the player joins. */
    @Inject(method = "addOptionalTasks", at = @At("RETURN"))
    private void lunararc$asyncConfigureTask(CallbackInfo ci) {
        ((java.util.Queue<net.minecraft.server.network.ConfigurationTask>) lunararc$tasks())
                .add(new io.papermc.paper.connection.PaperConfigurationTask((ServerConfigurationPacketListenerImpl) (Object) this));
    }

    /** Tell the spawn task which listener it serves, so its spawn-location events can use the configuration connection. */
    @WrapOperation(method = "returnToWorld", require = 1,
            at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/players/NameAndId;)Lnet/minecraft/server/network/config/PrepareSpawnTask;", remap = false))
    private net.minecraft.server.network.config.PrepareSpawnTask lunararc$spawnTaskListener(net.minecraft.server.MinecraftServer server,
            net.minecraft.server.players.NameAndId nameAndId, Operation<net.minecraft.server.network.config.PrepareSpawnTask> original) {
        net.minecraft.server.network.config.PrepareSpawnTask task = original.call(server, nameAndId);
        ((io.lunararcdevs.lunararc.common.bridge.PrepareSpawnTaskBridge) task).lunararc$setListener((ServerConfigurationPacketListenerImpl) (Object) this);
        return task;
    }

    @org.spongepowered.asm.mixin.Shadow
    private java.util.Queue<net.minecraft.server.network.ConfigurationTask> configurationTasks;

    @org.spongepowered.asm.mixin.Unique
    private java.util.Queue<net.minecraft.server.network.ConfigurationTask> lunararc$tasks() {
        return this.configurationTasks;
    }

    @org.spongepowered.asm.mixin.Shadow
    private void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
        throw new AssertionError();
    }

    /**
     * Keep the configuration task chain on the server thread. Paper's AsyncPlayerConnectionConfigureEvent task runs on its own
     * "Configuration Thread" pool and finishes itself from there, and finishCurrentTask starts the next task inline. That next
     * task is the spawn task, which adds chunk tickets and loads spawn chunks, so chunk loading ran off the server thread. Vanilla
     * tolerates that by luck; C2ME does not and throws "Async update chunks" during join. The call is handed back to the server
     * thread, where isSameThread() is true, so vanilla's own finishCurrentTask then runs unchanged.
     */
    @Inject(method = "finishCurrentTask", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$finishOnServerThread(net.minecraft.server.network.ConfigurationTask.Type type, CallbackInfo ci) {
        net.minecraft.server.MinecraftServer server = io.lunararcdevs.lunararc.common.LunarArcServerAccess.getMinecraftServer();
        if (server == null || server.isSameThread()) return;
        server.execute(() -> this.finishCurrentTask(type));
        ci.cancel();
    }


    /** The login player already went through PlayerLoginEvent, so skip vanilla's second login check. */
    @WrapOperation(
            method = "handleConfigurationFinished",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;canPlayerLogin(Ljava/net/SocketAddress;Lnet/minecraft/server/players/NameAndId;)Lnet/minecraft/network/chat/Component;"))
    private Component lunararc$skipSecondLoginCheck(
            PlayerList playerList,
            SocketAddress address,
            net.minecraft.server.players.NameAndId nameAndId,
            Operation<Component> original) {
        ServerPlayer player = ((ServerCommonPacketListenerBridge) (Object) this).lunararc$getPlayer();
        Component result = player == null ? original.call(playerList, address, nameAndId) : null;
        // Paper: plugins may still veto the connection (or pass a kick message) once configuration is complete.
        io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent event =
                new io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent(this.paperConnection,
                        result == null ? null : io.papermc.paper.adventure.PaperAdventure.asAdventure(result));
        event.callEvent();
        return io.papermc.paper.adventure.PaperAdventure.asVanilla(event.getKickMessage());
    }

    @Inject(method = "handleConfigurationFinished", at = @At("RETURN"), require = 0)
    private void lunararc$clearLoginPlayer(CallbackInfo ci) {
        ServerCommonPacketListenerBridge bridge = (ServerCommonPacketListenerBridge) (Object) this;
        ((ConnectionBridge) bridge.lunararc$getConnection()).lunararc$setLoginPlayer(null);
    }
}
