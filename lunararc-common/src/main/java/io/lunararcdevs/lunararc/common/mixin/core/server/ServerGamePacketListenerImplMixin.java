package io.lunararcdevs.lunararc.common.mixin.core.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import io.lunararcdevs.lunararc.common.server.LunarArcCommandLogger;
import io.lunararcdevs.lunararc.common.server.LunarArcCommandRouter;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Unique private boolean lunararc$resyncAfterSpecialInventoryClick;

    @Shadow
    public abstract void teleport(double x, double y, double z, float yaw, float pitch);

    // Paper's PlayerMoveEvent state (lastPosX..lastPitch, hasMoved, justTeleported).
    @Unique private double lunararc$lastPosX;
    @Unique private double lunararc$lastPosY;
    @Unique private double lunararc$lastPosZ;
    @Unique private float lunararc$lastYaw;
    @Unique private float lunararc$lastPitch;
    @Unique private boolean lunararc$hasMoved;
    @Unique private boolean lunararc$justTeleported;

    // Paper's internalTeleport: every server-side teleport resets the move baseline.
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("TAIL"))
    private void lunararc$trackTeleport(net.minecraft.world.entity.PositionMoveRotation posMoveRotation,
            Set<net.minecraft.world.entity.Relative> relative, CallbackInfo ci) {
        this.lunararc$justTeleported = true;
        this.lunararc$lastPosX = this.player.getX();
        this.lunararc$lastPosY = this.player.getY();
        this.lunararc$lastPosZ = this.player.getZ();
        this.lunararc$lastYaw = this.player.getYRot();
        this.lunararc$lastPitch = this.player.getXRot();
        this.lunararc$hasMoved = true;
    }

    // Paper fires PlayerMoveEvent right before the accepted-movement absMoveTo (ordinal 1 on
    // vanilla, Forge and NeoForge alike; ordinal 0 is the passenger branch).
    @Inject(method = "handleMovePlayer", cancellable = true,
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V"))
    private void lunararc$firePlayerMove(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet,
            CallbackInfo ci) {
        ServerPlayer player = this.player;
        if (lunararc$playerMoveCancelled(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()),
                packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMoveVehicle", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;move(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void lunararc$fireVehiclePlayerMove(net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (lunararc$playerMoveCancelled(packet.position().x, packet.position().y, packet.position().z, packet.yRot(), packet.xRot())) {
            ci.cancel();
        }
    }

    @Unique
    private boolean lunararc$playerMoveCancelled(double x, double y, double z, float yaw, float pitch) {
        ServerPlayer player = this.player;
        if (!(((EntityBridge) (Object) player).lunararc$getBukkitEntity() instanceof Player bukkit)) return false;

        if (!this.lunararc$hasMoved) {
            this.lunararc$lastPosX = player.getX();
            this.lunararc$lastPosY = player.getY();
            this.lunararc$lastPosZ = player.getZ();
            this.lunararc$lastYaw = player.getYRot();
            this.lunararc$lastPitch = player.getXRot();
            this.lunararc$hasMoved = true;
        }

        org.bukkit.World world = bukkit.getWorld();
        org.bukkit.Location from = new org.bukkit.Location(world, this.lunararc$lastPosX, this.lunararc$lastPosY,
                this.lunararc$lastPosZ, this.lunararc$lastYaw, this.lunararc$lastPitch);
        org.bukkit.Location to = new org.bukkit.Location(world, x, y, z, yaw, pitch);

        double delta = Math.pow(this.lunararc$lastPosX - to.getX(), 2)
                + Math.pow(this.lunararc$lastPosY - to.getY(), 2)
                + Math.pow(this.lunararc$lastPosZ - to.getZ(), 2);
        float deltaAngle = Math.abs(this.lunararc$lastYaw - to.getYaw())
                + Math.abs(this.lunararc$lastPitch - to.getPitch());
        if (!(delta > 1f / 256 || deltaAngle > 10f)
                || ((io.lunararcdevs.lunararc.common.mixin.core.entity.LivingEntityAccessor) player).lunararc$invokeIsImmobile()) {
            return false;
        }

        this.lunararc$lastPosX = to.getX();
        this.lunararc$lastPosY = to.getY();
        this.lunararc$lastPosZ = to.getZ();
        this.lunararc$lastYaw = to.getYaw();
        this.lunararc$lastPitch = to.getPitch();

        org.bukkit.Location oldTo = to.clone();
        org.bukkit.event.player.PlayerMoveEvent event = new org.bukkit.event.player.PlayerMoveEvent(bukkit, from, to);
        bukkit.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            this.teleport(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
            return true;
        }
        if (!oldTo.equals(event.getTo())) {
            bukkit.teleport(event.getTo(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            return true;
        }
        // A plugin teleported the player itself during the event rather than using setTo().
        if (!from.equals(bukkit.getLocation()) && this.lunararc$justTeleported) {
            this.lunararc$justTeleported = false;
            return true;
        }
        return false;
    }

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void lunararc$onChatBroadcast(PlayerChatMessage message, CallbackInfo ci) {
        CraftServer craftServer = LunarArcServerAccess.getCraftServer(this.player.server);
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) {
            throw new IllegalStateException("ServerPlayer bridge did not expose a Bukkit Player");
        }
        boolean async = !this.player.server.isSameThread();
        String originalMessage = message.signedContent();

        Set<Player> recipients = new LinkedHashSet<>(craftServer.getOnlinePlayers());
        AsyncPlayerChatEvent legacyEvent = new AsyncPlayerChatEvent(async, bukkitPlayer, originalMessage, recipients);
        String legacyDefaultFormat = legacyEvent.getFormat();
        craftServer.getPluginManager().callEvent(legacyEvent);
        if (legacyEvent.isCancelled()) {
            ci.cancel();
            return;
        }

        if (org.bukkit.event.player.PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
            org.bukkit.event.player.PlayerChatEvent[] sync = new org.bukkit.event.player.PlayerChatEvent[1];
            Runnable fireSync = () -> {
                org.bukkit.event.player.PlayerChatEvent syncEvent = new org.bukkit.event.player.PlayerChatEvent(bukkitPlayer,
                        legacyEvent.getMessage(), legacyEvent.getFormat(), new LinkedHashSet<>(legacyEvent.getRecipients()));
                craftServer.getPluginManager().callEvent(syncEvent);
                sync[0] = syncEvent;
            };
            if (async) this.player.server.executeBlocking(fireSync);
            else fireSync.run();
            if (sync[0].isCancelled()) {
                ci.cancel();
                return;
            }
            legacyEvent.setMessage(sync[0].getMessage());
            legacyEvent.setFormat(sync[0].getFormat());
            legacyEvent.getRecipients().clear();
            legacyEvent.getRecipients().addAll(sync[0].getRecipients());
        }

        boolean legacyMessageChanged = !originalMessage.equals(legacyEvent.getMessage());
        boolean legacyFormatChanged = !legacyDefaultFormat.equals(legacyEvent.getFormat());

        net.kyori.adventure.text.Component sourceDisplayName = bukkitPlayer.displayName();
        net.kyori.adventure.text.Component originalComponent = net.kyori.adventure.text.Component.text(originalMessage);
        net.kyori.adventure.text.Component modernMessage = legacyMessageChanged
                ? net.kyori.adventure.text.Component.text(legacyEvent.getMessage())
                : originalComponent;

        io.papermc.paper.chat.ChatRenderer renderer;
        if (legacyFormatChanged) {
            String legacyRendered = String.format(legacyEvent.getFormat(), net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(bukkitPlayer.displayName()), legacyEvent.getMessage());
            net.kyori.adventure.text.Component legacyComponent =
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.legacyToAdventure(legacyRendered);
            renderer = io.papermc.paper.chat.ChatRenderer.viewerUnaware((source, displayName, msg) -> legacyComponent);
        } else {
            renderer = io.papermc.paper.chat.ChatRenderer.defaultRenderer();
        }

        java.util.Set<net.kyori.adventure.audience.Audience> viewers =
                new LinkedHashSet<>(legacyEvent.getRecipients());
        LunarArcSignedChatMessage signedMessage = new LunarArcSignedChatMessage(message);
        io.papermc.paper.event.player.AsyncChatEvent modernEvent = new io.papermc.paper.event.player.AsyncChatEvent(
                async, bukkitPlayer, viewers, renderer, modernMessage, originalComponent, signedMessage);
        craftServer.getPluginManager().callEvent(modernEvent);
        if (modernEvent.isCancelled()) {
            ci.cancel();
            return;
        }
        if (io.papermc.paper.event.player.ChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
            io.papermc.paper.event.player.ChatEvent[] sync = new io.papermc.paper.event.player.ChatEvent[1];
            Runnable fireSync = () -> {
                io.papermc.paper.event.player.ChatEvent syncEvent = new io.papermc.paper.event.player.ChatEvent(bukkitPlayer,
                        modernEvent.viewers(), modernEvent.renderer(), modernEvent.message(), originalComponent, signedMessage);
                craftServer.getPluginManager().callEvent(syncEvent);
                sync[0] = syncEvent;
            };
            if (async) this.player.server.executeBlocking(fireSync);
            else fireSync.run();
            if (sync[0].isCancelled()) {
                ci.cancel();
                return;
            }
            modernEvent.message(sync[0].message());
            modernEvent.renderer(sync[0].renderer());
        }

        io.papermc.paper.chat.ChatRenderer finalRenderer = modernEvent.renderer();
        net.kyori.adventure.text.Component finalMessage = modernEvent.message();
        for (net.kyori.adventure.audience.Audience viewer : modernEvent.viewers()) {
            viewer.sendMessage(finalRenderer.render(bukkitPlayer, sourceDisplayName, finalMessage, viewer));
        }
        org.bukkit.command.ConsoleCommandSender console = craftServer.getConsoleSender();
        console.sendMessage(finalRenderer.render(bukkitPlayer, sourceDisplayName, finalMessage, console));
        ci.cancel();
    }

    /**
     * {@link net.kyori.adventure.chat.SignedMessage}, over a {@link PlayerChatMessage} that is
     * already fully available - no NMS patch needed. Real Paper adds this as an inner class of
     * {@code PlayerChatMessage} itself ({@code AdventureView}, in its own Adventure patch);
     * everything it reads off that class - {@code timeStamp()}, {@code salt()}, {@code
     * signature()}, {@code unsignedContent()}, {@code signedContent()}, {@code sender()} - is a
     * plain, unpatched vanilla accessor, so the same view is built here without touching NMS at
     * all.
     */
    @Unique
    private static final class LunarArcSignedChatMessage implements net.kyori.adventure.chat.SignedMessage {
        private final PlayerChatMessage message;

        private LunarArcSignedChatMessage(PlayerChatMessage message) {
            this.message = message;
        }

        @Override
        public java.time.Instant timestamp() {
            return this.message.timeStamp();
        }

        @Override
        public long salt() {
            return this.message.salt();
        }

        @Override
        public Signature signature() {
            net.minecraft.network.chat.MessageSignature signature = this.message.signature();
            return signature == null ? null : signature::bytes;
        }

        @Override
        public net.kyori.adventure.text.Component unsignedContent() {
            net.minecraft.network.chat.Component unsigned = this.message.unsignedContent();
            return unsigned == null ? null : io.papermc.paper.adventure.PaperAdventure.asAdventure(unsigned);
        }

        @Override
        public String message() {
            return this.message.signedContent();
        }

        @Override
        public net.kyori.adventure.identity.Identity identity() {
            return net.kyori.adventure.identity.Identity.identity(this.message.sender());
        }
    }

    @WrapOperation(
            method = {"performUnsignedChatCommand", "performSignedChatCommand"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V"))
    private void lunararc$routeValidatedCommand(net.minecraft.commands.Commands commands,
            com.mojang.brigadier.ParseResults<net.minecraft.commands.CommandSourceStack> parsed,
            String command, Operation<Void> original) {
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) {
            original.call(commands, parsed, command);
            return;
        }
        CraftServer craftServer = LunarArcServerAccess.getCraftServer(this.player.server);
        LunarArcCommandLogger.begin(this.player.getUUID(), this.player.getScoreboardName(), command);
        try {
            if (LunarArcCommandRouter.routePlayerPacket(craftServer, bukkitPlayer, command)
                    == LunarArcCommandRouter.PacketResult.PASS) {
                original.call(commands, parsed, command);
            }
        } finally {
            LunarArcCommandLogger.end();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onSwapHands(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            return;
        }
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) {
            return;
        }
        org.bukkit.inventory.ItemStack oldMain = bukkitPlayer.getInventory().getItemInMainHand();
        org.bukkit.inventory.ItemStack oldOff = bukkitPlayer.getInventory().getItemInOffHand();
        org.bukkit.event.player.PlayerSwapHandItemsEvent event =
                new org.bukkit.event.player.PlayerSwapHandItemsEvent(bukkitPlayer, oldOff.clone(), oldMain.clone());
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }
        if (!java.util.Objects.equals(event.getMainHandItem(), oldOff)
                || !java.util.Objects.equals(event.getOffHandItem(), oldMain)) {
            bukkitPlayer.getInventory().setItemInMainHand(event.getMainHandItem());
            bukkitPlayer.getInventory().setItemInOffHand(event.getOffHandItem());
            ci.cancel();
        }
    }


    @Inject(
            method = "handleInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;dispatch(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket$Handler;)V"),
            cancellable = true,
            require = 0)
    private void lunararc$onInteractEntity(
            net.minecraft.network.protocol.game.ServerboundInteractPacket packet,
            CallbackInfo ci) {
        net.minecraft.world.entity.Entity target = packet.getTarget(this.player.level());
        if (target == null || target == this.player) {
            return;
        }

        Object bukkitPlayerObject = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        Object bukkitTargetObject = ((EntityBridge) target).lunararc$getBukkitEntity();
        if (!(bukkitPlayerObject instanceof Player bukkitPlayer)
                || !(bukkitTargetObject instanceof org.bukkit.entity.Entity bukkitTarget)) {
            return;
        }

        final boolean[] cancelled = {false};
        packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() {
            private org.bukkit.inventory.EquipmentSlot lunararc$slot(net.minecraft.world.InteractionHand hand) {
                return hand == net.minecraft.world.InteractionHand.OFF_HAND
                        ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                        : org.bukkit.inventory.EquipmentSlot.HAND;
            }

            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand) {
                org.bukkit.event.player.PlayerInteractEntityEvent event =
                        new org.bukkit.event.player.PlayerInteractEntityEvent(
                                bukkitPlayer, bukkitTarget, lunararc$slot(hand));
                LunarArcServerAccess.getCraftServer(ServerGamePacketListenerImplMixin.this.player.server)
                        .getPluginManager().callEvent(event);
                cancelled[0] |= event.isCancelled();
            }

            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 position) {
                org.bukkit.event.player.PlayerInteractAtEntityEvent event =
                        new org.bukkit.event.player.PlayerInteractAtEntityEvent(
                                bukkitPlayer,
                                bukkitTarget,
                                new org.bukkit.util.Vector(position.x, position.y, position.z),
                                lunararc$slot(hand));
                LunarArcServerAccess.getCraftServer(ServerGamePacketListenerImplMixin.this.player.server)
                        .getPluginManager().callEvent(event);
                cancelled[0] |= event.isCancelled();
            }

            @Override
            public void onAttack() {
                // Attacks are handled by the existing entity-damage pipeline.
            }
        });

        if (cancelled[0]) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("RETURN"), require = 0)
    private void lunararc$onInteractUnknownEntity(
            net.minecraft.network.protocol.game.ServerboundInteractPacket packet,
            CallbackInfo ci) {
        if (packet.getTarget(this.player.level()) != null) {
            return;
        }
        if (!(((EntityBridge) this.player).lunararc$getBukkitEntity() instanceof Player bukkitPlayer)) {
            return;
        }

        final int entityId = ((io.lunararcdevs.lunararc.common.bridge.access.ServerboundInteractPacketAccessBridge) (Object) packet)
                .lunararc$getEntityId();
        packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() {
            private void lunararc$call(boolean attack, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 position) {
                org.bukkit.inventory.EquipmentSlot slot = hand == net.minecraft.world.InteractionHand.OFF_HAND
                        ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                        : org.bukkit.inventory.EquipmentSlot.HAND;
                org.bukkit.util.Vector clicked = position == null
                        ? null
                        : new org.bukkit.util.Vector(position.x, position.y, position.z);
                LunarArcServerAccess.getCraftServer(ServerGamePacketListenerImplMixin.this.player.server)
                        .getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent(
                                bukkitPlayer, entityId, attack, slot, clicked));
            }

            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand) {
                lunararc$call(false, hand, null);
            }

            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 position) {
                lunararc$call(false, hand, position);
            }

            @Override
            public void onAttack() {
                lunararc$call(true, net.minecraft.world.InteractionHand.MAIN_HAND, null);
            }
        });
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onAnimation(
            net.minecraft.network.protocol.game.ServerboundSwingPacket packet,
            CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) return;

        if (lunararc$isLeftClickAir()) {
            // CraftBukkit/Paper reports attack-air against the selected main-hand item;
            // the swing packet's hand is still preserved separately for PlayerAnimationEvent.
            org.bukkit.event.player.PlayerInteractEvent interact =
                    org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(
                            this.player, org.bukkit.event.block.Action.LEFT_CLICK_AIR, null, null,
                            this.player.getMainHandItem(), org.bukkit.inventory.EquipmentSlot.HAND);
            if (interact != null && interact.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
                ci.cancel();
                return;
            }
        }

        org.bukkit.inventory.EquipmentSlot swingHand =
                packet.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                        ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                        : org.bukkit.inventory.EquipmentSlot.HAND;
        org.bukkit.event.player.PlayerAnimationEvent event =
                new io.papermc.paper.event.player.PlayerArmSwingEvent(bukkitPlayer, swingHand);
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
        if (event.isCancelled()) ci.cancel();
    }

    private boolean lunararc$isLeftClickAir() {
        net.minecraft.world.phys.Vec3 eye = this.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = this.player.getViewVector(1.0F);
        double blockRange = this.player.blockInteractionRange();
        net.minecraft.world.phys.Vec3 blockEnd = eye.add(look.scale(blockRange));
        net.minecraft.world.phys.BlockHitResult blockHit = this.player.level().clip(
                new net.minecraft.world.level.ClipContext(eye, blockEnd,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, this.player));
        if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;

        double entityRange = this.player.entityInteractionRange();
        net.minecraft.world.phys.Vec3 entityEnd = eye.add(look.scale(entityRange));
        net.minecraft.world.phys.AABB box = this.player.getBoundingBox().expandTowards(look.scale(entityRange)).inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                this.player.level(), this.player, eye, entityEnd, box,
                entity -> !entity.isSpectator() && entity.isPickable() && !entity.isPassengerOfSameVehicle(this.player),
                (float) (entityRange * entityRange));
        return entityHit == null;
    }

    /**
     * A right click while aiming at a block within range already fires a
     * {@code RIGHT_CLICK_BLOCK} {@link org.bukkit.event.player.PlayerInteractEvent} from
     * {@code ServerPlayerGameMode#useItemOn} - the client sends {@code ServerboundUseItemOnPacket}
     * first, and this packet ({@code ServerboundUseItemPacket}) right behind it whenever that
     * block use did not consume the interaction. Firing a second, always-{@code RIGHT_CLICK_AIR}
     * event here regardless of what the player is actually looking at, as this used to
     * unconditionally do, misclassifies most real right clicks (aiming at terrain, a wall, or the
     * ground is the common case) and can fire the interact event twice for one physical click.
     * This raytraces the same way {@code ServerPlayerGameMode#useItemOn} does and, when it finds
     * the same block/hand/item {@code useItemOn} already fired for, reuses that result instead -
     * matching real CraftBukkit's {@code firedInteract} dedup - and otherwise fires its own
     * correctly-classified event.
     */
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onRightClickAir(ServerboundUseItemPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        net.minecraft.world.InteractionHand hand = packet.getHand();
        net.minecraft.world.item.ItemStack stack = this.player.getItemInHand(hand);
        io.lunararcdevs.lunararc.common.bridge.ServerPlayerGameModeBridge gameMode =
                (io.lunararcdevs.lunararc.common.bridge.ServerPlayerGameModeBridge) this.player.gameMode;
        boolean firedInteractBefore = gameMode.lunararc$firedInteract();

        if (stack.isEmpty() || !stack.isItemEnabled(this.player.level().enabledFeatures())) {
            return;
        }

        org.bukkit.inventory.EquipmentSlot slot = hand == net.minecraft.world.InteractionHand.OFF_HAND
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                : org.bukkit.inventory.EquipmentSlot.HAND;

        net.minecraft.world.phys.BlockHitResult blockHit = lunararc$clipForUseItem();
        boolean cancelled;
        org.bukkit.event.block.Action action;
        boolean eventCancelled = false;
        org.bukkit.event.Event.Result useItemInHandResult = org.bukkit.event.Event.Result.DEFAULT;

        if (blockHit == null) {
            action = org.bukkit.event.block.Action.RIGHT_CLICK_AIR;
            org.bukkit.event.player.PlayerInteractEvent event =
                    org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(
                            this.player, action, null, null, stack, slot);
            if (event != null) {
                useItemInHandResult = event.useItemInHand();
                cancelled = useItemInHandResult == org.bukkit.event.Event.Result.DENY;
                eventCancelled = cancelled;
            } else {
                cancelled = false;
            }
        } else if (firedInteractBefore
                && blockHit.getBlockPos().equals(gameMode.lunararc$interactPosition())
                && hand == gameMode.lunararc$interactHand()
                && net.minecraft.world.item.ItemStack.isSameItemSameComponents(gameMode.lunararc$interactItemStack(), stack)) {
            action = org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
            cancelled = gameMode.lunararc$interactResult();
            useItemInHandResult = cancelled ? org.bukkit.event.Event.Result.DENY : org.bukkit.event.Event.Result.DEFAULT;
            eventCancelled = cancelled;
        } else {
            action = org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
            org.bukkit.event.player.PlayerInteractEvent event =
                    org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(
                            this.player, action,
                            blockHit.getBlockPos(), blockHit.getDirection(), stack, slot);
            if (event != null) {
                useItemInHandResult = event.useItemInHand();
                cancelled = useItemInHandResult == org.bukkit.event.Event.Result.DENY;
                eventCancelled = cancelled;
            } else {
                cancelled = false;
            }
        }
        gameMode.lunararc$clearFiredInteract();

        boolean continues = !cancelled && !this.player.getItemInHand(hand).isEmpty();


        if (cancelled) {
            // Cancel only the Bukkit-visible use. The original loader-owned
            // packet method remains intact whenever plugins permit the action.
            this.player.getInventory().setChanged();
            ci.cancel();
            return;
        }
        // A plugin listening to the event above may have emptied the stack (e.g. consumed it);
        // re-read it so a use that no longer has an item to act on does not fall through to
        // the loader's own useItem() call with an item that is no longer actually there.
        if (this.player.getItemInHand(hand).isEmpty()) {
            ci.cancel();
        }
    }

    @Unique
    private String lunararc$determineHitResult() {
        net.minecraft.world.phys.Vec3 eye = this.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = this.player.getViewVector(1.0F);
        double blockRange = this.player.blockInteractionRange();
        net.minecraft.world.phys.Vec3 blockEnd = eye.add(look.scale(blockRange));
        net.minecraft.world.phys.BlockHitResult blockHit = this.player.level().clip(
                new net.minecraft.world.level.ClipContext(eye, blockEnd,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, this.player));
        boolean hasBlock = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
        double blockDistSqr = hasBlock ? eye.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

        double entityRange = this.player.entityInteractionRange();
        net.minecraft.world.phys.Vec3 entityEnd = eye.add(look.scale(entityRange));
        net.minecraft.world.phys.AABB box = this.player.getBoundingBox().expandTowards(look.scale(entityRange)).inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                this.player.level(), this.player, eye, entityEnd, box,
                entity -> !entity.isSpectator() && entity.isPickable() && !entity.isPassengerOfSameVehicle(this.player),
                (float) (entityRange * entityRange));
        boolean hasEntity = entityHit != null && entityHit.getEntity() != null;
        double entityDistSqr = hasEntity ? eye.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (hasEntity && entityDistSqr <= blockDistSqr) {
            return "ENTITY";
        } else if (hasBlock) {
            return "BLOCK";
        } else {
            return "MISS";
        }
    }

    @Unique
    private net.minecraft.world.phys.BlockHitResult lunararc$clipForUseItem() {
        net.minecraft.world.phys.Vec3 eye = this.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = this.player.getViewVector(1.0F);
        double range = this.player.blockInteractionRange();
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(range));
        net.minecraft.world.phys.BlockHitResult hit = this.player.level().clip(
                new net.minecraft.world.level.ClipContext(eye, end,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, this.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit : null;
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onHeldItemChange(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);

        int slot = packet.getSlot();
        if (slot < 0 || slot >= Inventory.getSelectionSize() || slot == this.player.getInventory().selected) {
            return;
        }
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) {
            return;
        }
        org.bukkit.event.player.PlayerItemHeldEvent event = new org.bukkit.event.player.PlayerItemHeldEvent(
                bukkitPlayer, this.player.getInventory().selected, slot);
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            ((ServerGamePacketListenerImpl) (Object) this).send(
                    new ClientboundSetHeldSlotPacket(this.player.getInventory().selected));
            this.player.resetLastActionTime();
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.resources.Identifier lunararc$recipeKey(net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket packet) {
        var info = this.player.level().getServer().getRecipeManager().getRecipeFromDisplay(packet.recipe());
        return info == null ? net.minecraft.resources.Identifier.withDefaultNamespace("unknown") : info.parent().id().identifier();
    }

    /** Sneak toggles arrive through the player-input packet now (Paper: PlayerInputEvent + PlayerToggleSneakEvent). */
    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onPlayerInput(net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (!this.player.server.isSameThread()) return; // vanilla re-queues onto the main thread, then we run again
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) return;
        var craftServer = LunarArcServerAccess.getCraftServer(this.player.server);
        net.minecraft.world.entity.player.Input last = this.player.getLastClientInput();
        net.minecraft.world.entity.player.Input now = packet.input();
        if (!now.equals(last)) {
            craftServer.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInputEvent(
                    bukkitPlayer, new org.bukkit.craftbukkit.CraftInput(now)));
        }
        if (last.shift() == now.shift()) return;
        org.bukkit.event.player.PlayerToggleSneakEvent event = new org.bukkit.event.player.PlayerToggleSneakEvent(bukkitPlayer, now.shift());
        craftServer.getPluginManager().callEvent(event);
        boolean loaded = ((ServerGamePacketListenerImpl) (Object) this).hasClientLoaded();
        this.player.setLastClientInput(now);
        if (loaded) {
            this.player.resetLastActionTime();
            if (!event.isCancelled()) this.player.setShiftKeyDown(now.shift());
        }
        ci.cancel();
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onPlayerCommandState(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) {
            return;
        }
        var action = packet.getAction();
        if (action == ServerboundPlayerCommandPacket.Action.START_SPRINTING
                || action == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
            boolean sprinting = action == ServerboundPlayerCommandPacket.Action.START_SPRINTING;
            org.bukkit.event.player.PlayerToggleSprintEvent event =
                    new org.bukkit.event.player.PlayerToggleSprintEvent(bukkitPlayer, sprinting);
            LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onFlightToggle(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        boolean requested = packet.isFlying();
        if (requested == this.player.getAbilities().flying || !this.player.getAbilities().mayfly) {
            return;
        }
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player bukkitPlayer)) {
            return;
        }
        org.bukkit.event.player.PlayerToggleFlightEvent event =
                new org.bukkit.event.player.PlayerToggleFlightEvent(bukkitPlayer, requested);
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.player.onUpdateAbilities();
            ci.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        if (!this.player.gameMode.isCreative()) {
            return;
        }
        int slot = packet.slotNum();
        if (slot < 1 || slot > 45) {
            return;
        }
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer)) {
            return;
        }
        org.bukkit.inventory.InventoryView view = craftPlayer.getOpenInventory();
        org.bukkit.event.inventory.InventoryType.SlotType slotType;
        try {
            slotType = view.getSlotType(slot);
        } catch (RuntimeException ignored) {
            slotType = org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER;
        }
        org.bukkit.inventory.ItemStack proposed =
                org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(packet.itemStack());
        org.bukkit.event.inventory.InventoryCreativeEvent event =
                new org.bukkit.event.inventory.InventoryCreativeEvent(view, slotType, slot, proposed);
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.player.inventoryMenu.broadcastFullState();
            ci.cancel();
            return;
        }
        if (!java.util.Objects.equals(event.getCursor(), proposed)) {
            net.minecraft.world.item.ItemStack replacement =
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getCursor());
            this.player.inventoryMenu.getSlot(slot).setByPlayer(replacement);
            this.player.inventoryMenu.broadcastChanges();
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$ensureContainerClickThread(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
    }

    @Inject(
            method = "handleContainerClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"),
            cancellable = true
    )
    private void lunararc$onContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        // QUICK_CRAFT is a multi-packet drag operation. It is handled against
        // AbstractContainerMenu's authoritative drag state by
        // AbstractContainerMenuMixin and must not be approximated as a click.
        if (packet.clickType() == net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
            return;
        }

        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer)) {
            return;
        }

        org.bukkit.inventory.InventoryView view = craftPlayer.getOpenInventory();
        int rawSlot = packet.slotNum();
        org.bukkit.event.inventory.InventoryType.SlotType slotType = view.getSlotType(rawSlot);
        org.bukkit.event.inventory.ClickType click = lunararc$clickType(packet.clickType(), packet.buttonNum());
        org.bukkit.event.inventory.InventoryAction action = lunararc$inventoryAction(packet, view);
        int hotbarKey = packet.clickType() == net.minecraft.world.inventory.ClickType.SWAP ? packet.buttonNum() : -1;
        org.bukkit.event.inventory.InventoryClickEvent event;
        if (rawSlot == 0 && view.getTopInventory() instanceof org.bukkit.inventory.CraftingInventory crafting
                && crafting.getRecipe() != null) {
            event = hotbarKey >= 0
                    ? new org.bukkit.event.inventory.CraftItemEvent(crafting.getRecipe(), view, slotType, rawSlot, click, action, hotbarKey)
                    : new org.bukkit.event.inventory.CraftItemEvent(crafting.getRecipe(), view, slotType, rawSlot, click, action);
            this.lunararc$resyncAfterSpecialInventoryClick = true;
        } else if (rawSlot == 3 && view.getTopInventory() instanceof org.bukkit.inventory.SmithingInventory smithing
                && smithing.getResult() != null) {
            event = hotbarKey >= 0
                    ? new org.bukkit.event.inventory.SmithItemEvent(view, slotType, rawSlot, click, action, hotbarKey)
                    : new org.bukkit.event.inventory.SmithItemEvent(view, slotType, rawSlot, click, action);
            this.lunararc$resyncAfterSpecialInventoryClick = true;
        } else if (rawSlot == 2 && view.getTopInventory() instanceof org.bukkit.inventory.CartographyInventory cartography
                && cartography.getItem(2) != null) {
            // Paper raises this subclass, instead of a plain click, for the result slot of a cartography table.
            event = hotbarKey >= 0
                    ? new io.papermc.paper.event.player.CartographyItemEvent(view, slotType, rawSlot, click, action, hotbarKey)
                    : new io.papermc.paper.event.player.CartographyItemEvent(view, slotType, rawSlot, click, action);
            this.lunararc$resyncAfterSpecialInventoryClick = true;
        } else {
            event = new org.bukkit.event.inventory.InventoryClickEvent(view, slotType, rawSlot, click, action, hotbarKey);
        }
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            // Vanilla suppresses remote updates immediately before invoking clicked().
            // We cancel at that invocation point, so explicitly restore update flow and
            // send authoritative contents back to the client.
            this.player.containerMenu.resumeRemoteUpdates();
            this.player.containerMenu.broadcastFullState();
            this.lunararc$resyncAfterSpecialInventoryClick = false;
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("RETURN"), require = 0)
    private void lunararc$resyncSpecialInventoryClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!this.lunararc$resyncAfterSpecialInventoryClick) return;
        this.lunararc$resyncAfterSpecialInventoryClick = false;
        this.player.containerMenu.sendAllDataToRemote();
    }

    @org.spongepowered.asm.mixin.Unique
    private org.bukkit.event.inventory.InventoryAction lunararc$inventoryAction(
            ServerboundContainerClickPacket packet, org.bukkit.inventory.InventoryView view) {
        final org.bukkit.event.inventory.InventoryAction nothing =
                org.bukkit.event.inventory.InventoryAction.NOTHING;
        int slotNum = packet.slotNum();
        int button = packet.buttonNum();
        net.minecraft.world.inventory.AbstractContainerMenu menu = this.player.containerMenu;

        switch (packet.clickType()) {
            case PICKUP -> {
                if (button != 0 && button != 1) return org.bukkit.event.inventory.InventoryAction.UNKNOWN;
                if (slotNum == -999) {
                    if (menu.getCarried().isEmpty()) return nothing;
                    return button == 0
                            ? org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR
                            : org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR;
                }
                if (slotNum < 0) return nothing;
                net.minecraft.world.inventory.Slot slot = menu.getSlot(slotNum);
                if (slot == null) return nothing;
                net.minecraft.world.item.ItemStack clicked = slot.getItem();
                net.minecraft.world.item.ItemStack cursor = menu.getCarried();
                if (clicked.isEmpty()) {
                    if (cursor.isEmpty()) return nothing;
                    return button == 0
                            ? org.bukkit.event.inventory.InventoryAction.PLACE_ALL
                            : org.bukkit.event.inventory.InventoryAction.PLACE_ONE;
                }
                if (!slot.mayPickup(this.player)) return nothing;
                if (cursor.isEmpty()) {
                    return button == 0
                            ? org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
                            : org.bukkit.event.inventory.InventoryAction.PICKUP_HALF;
                }
                if (slot.mayPlace(cursor)) {
                    if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(clicked, cursor)) {
                        int toPlace = button == 0 ? cursor.getCount() : 1;
                        toPlace = Math.min(toPlace, clicked.getMaxStackSize() - clicked.getCount());
                        toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clicked.getCount());
                        if (toPlace == 1) return org.bukkit.event.inventory.InventoryAction.PLACE_ONE;
                        if (toPlace == cursor.getCount()) return org.bukkit.event.inventory.InventoryAction.PLACE_ALL;
                        // Negative only with oversized stacks, where the click removes rather than adds.
                        if (toPlace < 0) {
                            return toPlace != -1
                                    ? org.bukkit.event.inventory.InventoryAction.PICKUP_SOME
                                    : org.bukkit.event.inventory.InventoryAction.PICKUP_ONE;
                        }
                        if (toPlace != 0) return org.bukkit.event.inventory.InventoryAction.PLACE_SOME;
                        return nothing;
                    }
                    if (cursor.getCount() <= slot.getMaxStackSize()) {
                        return org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR;
                    }
                    return nothing;
                }
                if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(cursor, clicked)
                        && clicked.getCount() >= 0
                        && clicked.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                    // Result slots only, since 1.5.
                    return org.bukkit.event.inventory.InventoryAction.PICKUP_ALL;
                }
                return nothing;
            }
            case QUICK_MOVE -> {
                if (button != 0 && button != 1) return org.bukkit.event.inventory.InventoryAction.UNKNOWN;
                if (slotNum < 0) return nothing;
                net.minecraft.world.inventory.Slot slot = menu.getSlot(slotNum);
                return slot != null && slot.mayPickup(this.player) && slot.hasItem()
                        ? org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                        : nothing;
            }
            case SWAP -> {
                if (!((button >= 0 && button < 9) || button == 40)) {
                    return org.bukkit.event.inventory.InventoryAction.UNKNOWN;
                }
                net.minecraft.world.inventory.Slot slot = menu.getSlot(slotNum);
                if (slot == null || !slot.mayPickup(this.player)) return nothing;
                net.minecraft.world.item.ItemStack hotbar = this.player.getInventory().getItem(button);
                boolean cleanSwap = hotbar.isEmpty()
                        || (slot.container == this.player.getInventory() && slot.mayPlace(hotbar));
                if (slot.hasItem()) {
                    return cleanSwap
                            ? org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP
                            : org.bukkit.event.inventory.InventoryAction.HOTBAR_MOVE_AND_READD;
                }
                return !hotbar.isEmpty() && slot.mayPlace(hotbar)
                        ? org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP
                        : nothing;
            }
            case CLONE -> {
                if (button != 2) return org.bukkit.event.inventory.InventoryAction.UNKNOWN;
                if (slotNum < 0) return nothing;
                net.minecraft.world.inventory.Slot slot = menu.getSlot(slotNum);
                return slot != null && slot.hasItem()
                        && this.player.getAbilities().instabuild && menu.getCarried().isEmpty()
                        ? org.bukkit.event.inventory.InventoryAction.CLONE_STACK
                        : nothing;
            }
            case THROW -> {
                // A negative slot here is the client holding nothing, not a drop.
                if (slotNum < 0) return nothing;
                net.minecraft.world.inventory.Slot slot = menu.getSlot(slotNum);
                boolean droppable = slot != null && slot.hasItem() && slot.mayPickup(this.player)
                        && !slot.getItem().isEmpty();
                if (!droppable) return nothing;
                if (button == 0) return org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT;
                if (button == 1) return org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT;
                return nothing;
            }
            case PICKUP_ALL -> {
                if (slotNum < 0 || menu.getCarried().isEmpty()) return nothing;
                org.bukkit.Material carried = org.bukkit.craftbukkit.util.CraftMagicNumbers
                        .getMaterial(menu.getCarried().getItem());
                return view.getTopInventory().contains(carried) || view.getBottomInventory().contains(carried)
                        ? org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                        : nothing;
            }
            default -> {
                return org.bukkit.event.inventory.InventoryAction.UNKNOWN;
            }
        }
    }

    private static org.bukkit.event.inventory.ClickType lunararc$clickType(
            net.minecraft.world.inventory.ClickType type, int button) {
        return switch (type) {
            case PICKUP -> button == 1
                    ? org.bukkit.event.inventory.ClickType.RIGHT
                    : org.bukkit.event.inventory.ClickType.LEFT;
            case QUICK_MOVE -> button == 1
                    ? org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
                    : org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
            case SWAP -> org.bukkit.event.inventory.ClickType.NUMBER_KEY;
            case CLONE -> org.bukkit.event.inventory.ClickType.MIDDLE;
            case THROW -> button == 1
                    ? org.bukkit.event.inventory.ClickType.CONTROL_DROP
                    : org.bukkit.event.inventory.ClickType.DROP;
            case PICKUP_ALL -> org.bukkit.event.inventory.ClickType.DOUBLE_CLICK;
            case QUICK_CRAFT -> org.bukkit.event.inventory.ClickType.UNKNOWN;
        };
    }

    @WrapOperation(
            method = {"updateBookContents", "signBook"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"),
            require = 0)
    private void lunararc$bookEdit(net.minecraft.world.entity.player.Inventory inventory, int slot,
                                    net.minecraft.world.item.ItemStack proposed, Operation<Void> original) {
        net.minecraft.world.item.ItemStack old = inventory.getItem(slot).copy();
        net.minecraft.world.item.ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleEditBookEvent(
                this.player, slot, old, proposed);
        original.call(inventory, slot, result);
    }

    @Inject(method = "handleSelectTrade", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$tradeSelect(net.minecraft.network.protocol.game.ServerboundSelectTradePacket packet,
                                      CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        if (!(this.player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)) return;
        Object bukkit = ((EntityBridge) this.player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer)) return;
        org.bukkit.inventory.InventoryView rawView = craftPlayer.getOpenInventory();
        if (!(rawView instanceof org.bukkit.inventory.view.MerchantView merchantView)) return;
        org.bukkit.event.inventory.TradeSelectEvent event =
                new org.bukkit.event.inventory.TradeSelectEvent(merchantView, packet.getItem());
        LunarArcServerAccess.getCraftServer(this.player.server).getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.player.containerMenu.broadcastFullState();
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$onContainerClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
        this.lunararc$runInPacketOrder(packet);
        if (this.player.containerMenu == this.player.inventoryMenu) {
            return;
        }
        if (this.player.containerMenu.containerId != packet.getContainerId()) {
            return;
        }
        ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge) this.player)
                .lunararc$setNextInventoryCloseReason(org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLAYER);
    }

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$recipeClick(net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        if (!this.player.server.isSameThread()) return;
        if (!io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.recipeBookClick(this.player, lunararc$recipeKey(packet), packet.useMaxItems())) ci.cancel();
    }

    @Inject(method = "handleRecipeBookChangeSettingsPacket", at = @At("HEAD"), require = 0)
    private void lunararc$recipeSettings(net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket packet, CallbackInfo ci) {
        if (!this.player.server.isSameThread()) return;
        io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.recipeBookSettings(this.player, packet.getBookType(), packet.isOpen(), packet.isFiltering());
    }

    /**
     * Off the main thread, queues the packet on the connection's own packet processor and throws vanilla's
     * RunningOnDifferentThreadException, exactly like the first line of each vanilla handler. Re-queueing through
     * server.execute instead could run these actions out of order with packets vanilla queues itself
     * (for example a hotbar switch against an attack).
     */
    @org.spongepowered.asm.mixin.Unique
    private <T extends net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ServerGamePacketListener>> void lunararc$runInPacketOrder(T packet) {
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(
                packet, (ServerGamePacketListenerImpl) (Object) this, this.player.level());
    }
}
