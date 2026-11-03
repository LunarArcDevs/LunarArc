package io.lunararcdevs.lunararc.common.mixin.core.server;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import java.util.function.Consumer;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import io.lunararcdevs.lunararc.common.bridge.ServerPlayerClientOptionsBridge;
import io.lunararcdevs.lunararc.common.bridge.ServerPlayerBukkitDataBridge;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ServerPlayerClientOptionsBridge, ServerPlayerBukkitDataBridge,
        io.lunararcdevs.lunararc.common.bridge.ServerPlayerBedBridge,
        io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge,
        io.lunararcdevs.lunararc.common.bridge.ServerPlayerDeathBridge,
        io.lunararcdevs.lunararc.common.bridge.ServerPlayerRespawnBridge,
        io.lunararcdevs.lunararc.common.bridge.ServerPlayerSpawnBridge, io.lunararcdevs.lunararc.common.bridge.ServerPlayerListOrderBridge {
    @Shadow private int containerCounter;
    @Shadow private void nextContainerCounter() {}
    @Shadow private void initMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {}

    @Override public int lunararc$nextContainerCounter() { this.nextContainerCounter(); return this.containerCounter; }
    @Override public void lunararc$initMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) { this.initMenu(menu); }

    public CraftPlayer getBukkitEntity() {
        return (CraftPlayer) ((EntityBridge) (Object) this).lunararc$getBukkitEntity();
    }

    public net.minecraft.network.chat.Component listName;

    /** Public and un-prefixed: Paper classes read {@code ServerPlayer.listOrder} directly. */
    public int listOrder = 0;

    @Override public int lunararc$getListOrder() { return this.listOrder; }
    @Override public void lunararc$setListOrder(int order) { this.listOrder = order; }

    @Inject(method = "getTabListOrder", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$tabListOrder(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.listOrder);
    }

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$listName(CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        if (this.listName != null) cir.setReturnValue(this.listName);
    }


    @Shadow
    private String language;

    @Shadow
    public abstract net.minecraft.world.level.portal.TeleportTransition findRespawnPositionAndUseSpawnBlock(
            boolean keepEverything,
            net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition postTransition);

    @Override
    public net.minecraft.world.level.portal.TeleportTransition lunararc$findRespawnPositionAndUseSpawnBlock(
            boolean keepEverything,
            net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition postTransition) {
        return this.findRespawnPositionAndUseSpawnBlock(keepEverything, postTransition);
    }

    @Unique private ServerLevel lunararc$levelBeforeDimensionChange;

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), require = 1)
    private void lunararc$rememberLevelBeforeDimensionChange(
            net.minecraft.world.level.portal.TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir) {
        this.lunararc$levelBeforeDimensionChange = ((ServerPlayer) (Object) this).level();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("RETURN"), require = 1)
    private void lunararc$playerChangedWorldEvent(
            net.minecraft.world.level.portal.TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir) {
        ServerLevel before = this.lunararc$levelBeforeDimensionChange;
        this.lunararc$levelBeforeDimensionChange = null;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (before == null || cir.getReturnValue() == null) return;
        ServerLevel after = self.level();
        if (before == after || !org.bukkit.Bukkit.isPrimaryThread()) return;

        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) self).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Player player)) return;
        org.bukkit.craftbukkit.CraftWorld from = io.lunararcdevs.lunararc.common.LunarArcServerAccess
                .getCraftServer(self.server).getCraftWorldIfPresent(before);
        if (from == null) return;

        org.bukkit.Bukkit.getPluginManager().callEvent(
                new org.bukkit.event.player.PlayerChangedWorldEvent(player, from));
    }

    @Unique private long lunararc$firstPlayed = System.currentTimeMillis();
    @Unique private long lunararc$lastPlayed;
    @Unique private long lunararc$loginTime;
    @Unique private long lunararc$lastSaveTime;
    @Unique private boolean lunararc$hasPlayedBefore;
    @Unique private Boolean lunararc$nextBedLeaveShouldSetSpawn;
    @Unique private org.bukkit.event.inventory.InventoryCloseEvent.Reason lunararc$nextInventoryCloseReason;
    @Unique private net.kyori.adventure.text.Component lunararc$nextInventoryOpenTitle;
    @Unique private int lunararc$lastKnownExperienceLevel = Integer.MIN_VALUE;
    @Unique private org.bukkit.event.entity.PlayerDeathEvent lunararc$deathEvent;
    @Unique private io.lunararcdevs.lunararc.common.bridge.ServerPlayerDeathBridge.ExperienceState lunararc$deathExperienceState;
    @Unique private java.util.List<net.minecraft.world.item.ItemStack> lunararc$deathInventoryState;
    @Unique private org.bukkit.event.player.PlayerSpawnChangeEvent.Cause lunararc$spawnChangeCause;

    @Override
    public String lunararc$getLanguage() {
        return this.language;
    }

    @Inject(method = "updateOptions", at = @At("HEAD"), require = 0)
    private void lunararc$clientSettingEvents(ClientInformation options, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) self).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Player player)) return;
        io.lunararcdevs.lunararc.common.event.LunarArcPaperEvents.clientOptions(self, options);

        if (self.getMainArm() != options.mainHand()) {
            org.bukkit.inventory.MainHand previous = self.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT
                    ? org.bukkit.inventory.MainHand.LEFT
                    : org.bukkit.inventory.MainHand.RIGHT;
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new org.bukkit.event.player.PlayerChangedMainHandEvent(player, previous));
        }
        if (!java.util.Objects.equals(this.language, options.language())) {
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new org.bukkit.event.player.PlayerLocaleChangeEvent(player, options.language()));
        }
    }

    @Override
    public void lunararc$setNextBedLeaveShouldSetSpawn(Boolean value) {
        this.lunararc$nextBedLeaveShouldSetSpawn = value;
    }

    @Override
    public void lunararc$setNextInventoryCloseReason(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        this.lunararc$nextInventoryCloseReason = reason;
    }

    @Override
    public void lunararc$pushSpawnChangeCause(org.bukkit.event.player.PlayerSpawnChangeEvent.Cause cause) {
        this.lunararc$spawnChangeCause = cause;
    }

    @Override public long lunararc$getFirstPlayed() { return this.lunararc$firstPlayed; }
    @Override public void lunararc$setFirstPlayed(long firstPlayed) { this.lunararc$firstPlayed = firstPlayed; }
    @Override public long lunararc$getLastPlayed() { return this.lunararc$lastPlayed; }
    @Override public void lunararc$setLastPlayed(long lastPlayed) { this.lunararc$lastPlayed = lastPlayed; }
    @Override public long lunararc$getLoginTime() { return this.lunararc$loginTime; }
    @Override public void lunararc$setLoginTime(long loginTime) { this.lunararc$loginTime = loginTime; }
    @Override public long lunararc$getLastSaveTime() { return this.lunararc$lastSaveTime; }
    @Override public boolean lunararc$hasPlayedBefore() { return this.lunararc$hasPlayedBefore; }
    @Override public io.lunararcdevs.lunararc.common.bridge.ServerPlayerDeathBridge.ExperienceState lunararc$getDeathExperienceState() { return this.lunararc$deathExperienceState; }
    @Override public void lunararc$setDeathExperienceState(io.lunararcdevs.lunararc.common.bridge.ServerPlayerDeathBridge.ExperienceState state) { this.lunararc$deathExperienceState = state; }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> lunararc$getDeathInventoryState() { return this.lunararc$deathInventoryState; }
    @Override public void lunararc$setDeathInventoryState(java.util.List<net.minecraft.world.item.ItemStack> state) { this.lunararc$deathInventoryState = state; }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"), require = 0)
    private void lunararc$readBukkitPlayerData(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        this.lunararc$hasPlayedBefore = true;
        input.child("bukkit").ifPresent(bukkit -> {
            this.lunararc$firstPlayed = bukkit.getLongOr("firstPlayed", this.lunararc$firstPlayed);
            this.lunararc$lastPlayed = bukkit.getLongOr("lastPlayed", this.lunararc$lastPlayed);
        });
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"), require = 0)
    private void lunararc$writeBukkitPlayerData(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        this.lunararc$lastSaveTime = now;
        net.minecraft.world.level.storage.ValueOutput bukkit = output.child("bukkit");
        bukkit.putLong("firstPlayed", this.lunararc$firstPlayed);
        bukkit.putLong("lastPlayed", now);
        bukkit.putString("lastKnownName", ((ServerPlayer) (Object) this).getScoreboardName());
        net.minecraft.world.level.storage.ValueOutput paper = output.child("Paper");
        paper.putLong("LastLogin", this.lunararc$loginTime);
        paper.putLong("LastSeen", now);
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "setRespawnPosition", require = 0)
    private void lunararc$playerSpawnChangeEvent(
            ServerPlayer.RespawnConfig config,
            boolean sendMessage,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        org.bukkit.event.player.PlayerSpawnChangeEvent.Cause cause = this.lunararc$spawnChangeCause;
        this.lunararc$spawnChangeCause = null;
        if (cause == null) cause = org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.UNKNOWN;

        ServerPlayer self = (ServerPlayer) (Object) this;
        net.minecraft.world.level.storage.LevelData.RespawnData data = config == null ? null : config.respawnData();
        ServerLevel level = data == null ? null : self.level().getServer().getLevel(data.dimension());
        boolean forced = config != null && config.forced();
        org.bukkit.Location newSpawn = null;
        if (data != null && level != null) {
            org.bukkit.craftbukkit.CraftWorld world =
                    ((io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) self.level().getServer())
                            .lunararc$getCraftServer().getCraftWorld(level);
            if (world != null) {
                BlockPos position = data.pos();
                newSpawn = new org.bukkit.Location(world, position.getX(), position.getY(), position.getZ(), data.yaw(), data.pitch());
            }
        }

        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) self).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Player player)) {
            original.call(config, sendMessage);
            return;
        }

        org.bukkit.event.player.PlayerSpawnChangeEvent event =
                new org.bukkit.event.player.PlayerSpawnChangeEvent(player, newSpawn, forced, cause);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        if (!io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.setSpawn(player, event.getNewSpawn(), event.isForced(), sendMessage,
                String.valueOf(cause).equals("RESET") ? "PLAYER_RESPAWN" : String.valueOf(cause))) return;

        org.bukkit.Location selected = event.getNewSpawn();
        boolean selectedForced = event.isForced();
        if (selected == null) {
            original.call(null, sendMessage);
            return;
        }
        if (!(selected.getWorld() instanceof org.bukkit.craftbukkit.CraftWorld selectedWorld)) {
            return;
        }
        original.call(new ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                        selectedWorld.getHandle().dimension(),
                        BlockPos.containing(selected.getX(), selected.getY(), selected.getZ()),
                        selected.getYaw(), selected.getPitch()),
                selectedForced), sendMessage);
    }

    @Inject(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V"),
            require = 0)
    private void lunararc$bedSpawnChangeCause(BlockPos bedPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        this.lunararc$spawnChangeCause = org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.BED;
    }


    @Redirect(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/datafixers/util/Either;ifRight(Ljava/util/function/Consumer;)Lcom/mojang/datafixers/util/Either;",
                    remap = false),
            require = 0)
    private <L, R> Either<L, R> lunararc$playerBedEnter(
            Either<L, R> result, Consumer<? super R> successConsumer, BlockPos bedPos) {
        @SuppressWarnings("unchecked")
        Either<Player.BedSleepingProblem, Unit> vanillaResult =
                (Either<Player.BedSleepingProblem, Unit>) result;
        Either<Player.BedSleepingProblem, Unit> eventResult =
                org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(
                        (ServerPlayer) (Object) this, bedPos, vanillaResult);
        @SuppressWarnings("unchecked")
        Either<L, R> converted = (Either<L, R>) eventResult;
        return converted.ifRight(successConsumer);
    }

    @Unique
    private static String lunararc$bedProblemName(Player.BedSleepingProblem problem) {
        if (problem == Player.BedSleepingProblem.TOO_FAR_AWAY) return "TOO_FAR_AWAY";
        if (problem == Player.BedSleepingProblem.OBSTRUCTED) return "OBSTRUCTED";
        if (problem == Player.BedSleepingProblem.NOT_SAFE) return "NOT_SAFE";
        return "OTHER_PROBLEM";
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "startSleepInBed", at = @At("RETURN"), require = 0)
    private Either<Player.BedSleepingProblem, Unit> lunararc$bedFailed(
            Either<Player.BedSleepingProblem, Unit> result,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) BlockPos bedPos) {
        result.left().ifPresent(problem -> io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.bedFailed(
                (ServerPlayer) (Object) this, bedPos, lunararc$bedProblemName(problem), problem.message()));
        return result;
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$playerBedLeave(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.isSleeping()) {
            this.lunararc$nextBedLeaveShouldSetSpawn = null;
            return;
        }
        BlockPos bedPos = self.getSleepingPos().orElse(null);
        boolean defaultSetSpawn = this.lunararc$nextBedLeaveShouldSetSpawn == null
                || this.lunararc$nextBedLeaveShouldSetSpawn;
        this.lunararc$nextBedLeaveShouldSetSpawn = null;
        org.bukkit.event.player.PlayerBedLeaveEvent event =
                org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedLeaveEvent(self, bedPos, defaultSetSpawn);
        if (event != null && event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event != null && event.shouldSetSpawnLocation() && bedPos != null) {
            this.lunararc$spawnChangeCause = org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.BED;
            self.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    net.minecraft.world.level.storage.LevelData.RespawnData.of(self.level().dimension(), bedPos, self.getYRot(), self.getXRot()), false), true);
        }
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"), require = 0)
    private void lunararc$inventoryClose(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.containerMenu == self.inventoryMenu) {
            this.lunararc$nextInventoryCloseReason = null;
            return;
        }
        org.bukkit.event.inventory.InventoryCloseEvent.Reason reason = this.lunararc$nextInventoryCloseReason;
        this.lunararc$nextInventoryCloseReason = null;
        if (reason == null) reason = org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNKNOWN;
        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(self, reason);
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("RETURN"), require = 0)
    private void lunararc$finishInventoryOpen(net.minecraft.world.MenuProvider provider,
                                               CallbackInfoReturnable<java.util.OptionalInt> cir) {
        lunararc$applyInventoryOpenTitle(cir);
    }

    @SuppressWarnings("target")
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At("RETURN"), require = 0)
    private void lunararc$finishInventoryOpenWithExtraData(net.minecraft.world.MenuProvider provider,
            java.util.function.Consumer<?> extraDataWriter, CallbackInfoReturnable<java.util.OptionalInt> cir) {
        lunararc$applyInventoryOpenTitle(cir);
    }

    @Unique
    private void lunararc$applyInventoryOpenTitle(CallbackInfoReturnable<java.util.OptionalInt> cir) {
        net.kyori.adventure.text.Component title = this.lunararc$nextInventoryOpenTitle;
        if (title == null) return;
        this.lunararc$nextInventoryOpenTitle = null;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (cir.getReturnValue() == null || cir.getReturnValue().isEmpty()
                || self.containerMenu == self.inventoryMenu || self.connection == null) return;
        self.connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                self.containerMenu.containerId, self.containerMenu.getType(),
                io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(title)));
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "openMenu",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/MenuProvider;createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;"),
            require = 0)
    private net.minecraft.world.inventory.AbstractContainerMenu lunararc$inventoryOpen(
            net.minecraft.world.MenuProvider provider, int containerId,
            net.minecraft.world.entity.player.Inventory inventory, Player player,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.world.inventory.AbstractContainerMenu> original) {
        net.minecraft.world.inventory.AbstractContainerMenu menu = original.call(provider, containerId, inventory, player);
        return lunararc$fireInventoryOpen(provider, menu);
    }

    @Unique
    private net.minecraft.world.inventory.AbstractContainerMenu lunararc$fireInventoryOpen(
            net.minecraft.world.MenuProvider provider, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu == null) return null;
        ServerPlayer self = (ServerPlayer) (Object) this;
        net.kyori.adventure.text.Component title = io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline
                .toAdventure(provider.getDisplayName());
        ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) menu).lunararc$setOwner(self);
        org.bukkit.craftbukkit.inventory.CraftInventoryView view =
                org.bukkit.craftbukkit.event.CraftEventFactory.createInventoryView(self, menu, title);
        if (view == null) return menu;
        com.mojang.datafixers.util.Pair<net.kyori.adventure.text.Component, net.minecraft.world.inventory.AbstractContainerMenu> result =
                org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEventWithTitle(self, menu, view, false);
        if (result.getSecond() == null) {
            this.lunararc$nextInventoryOpenTitle = null;
            return null;
        }
        this.lunararc$nextInventoryOpenTitle = result.getFirst();
        return result.getSecond();
    }



    @Inject(method = "doTick", at = @At("TAIL"), require = 0)
    private void lunararc$levelChange(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        int current = self.experienceLevel;
        if (this.lunararc$lastKnownExperienceLevel == Integer.MIN_VALUE) {
            this.lunararc$lastKnownExperienceLevel = current;
            return;
        }
        if (this.lunararc$lastKnownExperienceLevel != current) {
            Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) self).lunararc$getBukkitEntity();
            if (bukkit instanceof org.bukkit.entity.Player player) {
                org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerLevelChangeEvent(
                        player, this.lunararc$lastKnownExperienceLevel, current));
            }
            this.lunararc$lastKnownExperienceLevel = current;
        }
    }

    @Unique
    private void lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason reason) {
        ((io.lunararcdevs.lunararc.common.bridge.PlayerExhaustionBridge) this).lunararc$pushExhaustionReason(reason);
    }

    @Inject(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 0), require = 0)
    private void lunararc$swimExhaustion(double x, double y, double z, CallbackInfo ci) {
        lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SWIM);
    }

    @Inject(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 1), require = 0)
    private void lunararc$underwaterWalkExhaustion(double x, double y, double z, CallbackInfo ci) {
        lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK_UNDERWATER);
    }

    @Inject(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 2), require = 0)
    private void lunararc$waterSurfaceExhaustion(double x, double y, double z, CallbackInfo ci) {
        lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK_ON_WATER);
    }

    @Inject(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 3), require = 0)
    private void lunararc$sprintExhaustion(double x, double y, double z, CallbackInfo ci) {
        lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SPRINT);
    }

    @Inject(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 4), require = 0)
    private void lunararc$crouchExhaustion(double x, double y, double z, CallbackInfo ci) {
        lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.CROUCH);
    }

    @Inject(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 5), require = 0)
    private void lunararc$walkExhaustion(double x, double y, double z, CallbackInfo ci) {
        lunararc$movementExhaustion(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK);
    }


    @Inject(method = "die", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$playerDeathEvent(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isRemoved() || self.level().isClientSide) return;

        boolean keepInventory = self.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)
                || self.isSpectator();
        java.util.List<org.bukkit.inventory.ItemStack> drops = new java.util.ArrayList<>();
        if (!keepInventory) {
            for (int slot = 0; slot < self.getInventory().getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack item = self.getInventory().getItem(slot);
                if (!item.isEmpty() && !net.minecraft.world.item.enchantment.EnchantmentHelper.has(
                        item, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(item));
                }
            }
        }

        int droppedExp = self.getExperienceReward(self.level(), source.getEntity());
        net.minecraft.network.chat.Component defaultMessage = self.getCombatTracker().getDeathMessage();
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) self).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Player player)) return;

        var event = new org.bukkit.event.entity.PlayerDeathEvent(
                player,
                new org.bukkit.craftbukkit.damage.CraftDamageSource(source),
                drops,
                droppedExp,
                0,
                0,
                0,
                io.papermc.paper.adventure.PaperAdventure.asAdventure(defaultMessage),
                true);
        event.setKeepInventory(keepInventory);
        event.setKeepLevel(keepInventory);
        double revive = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? player.getMaxHealth()
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (revive > 0.0D) event.setReviveHealth(revive);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            this.lunararc$deathEvent = null;
            this.lunararc$deathExperienceState = null;
            this.lunararc$deathInventoryState = null;
            if (self.getHealth() <= 0.0F) {
                self.setHealth((float) Math.min(event.getReviveHealth(), self.getMaxHealth()));
            }
            if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer) {
                craftPlayer.sendHealthUpdate();
            }
            ci.cancel();
            return;
        }

        this.lunararc$deathEvent = event;
        this.lunararc$deathExperienceState = new io.lunararcdevs.lunararc.common.bridge.ServerPlayerDeathBridge.ExperienceState(
                event.getKeepLevel(), self.experienceLevel, self.totalExperience, self.experienceProgress,
                event.getNewExp(), event.getNewTotalExp(), event.getNewLevel());

        if (self.containerMenu != self.inventoryMenu) {
            this.lunararc$nextInventoryCloseReason = org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH;
            self.closeContainer();
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "die", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object lunararc$deathMessageEnabled(net.minecraft.world.level.gamerules.GameRules rules, net.minecraft.world.level.gamerules.GameRule<?> rule,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> original) {
        Object vanilla = original.call(rules, rule);
        if (rule == net.minecraft.world.level.gamerules.GameRules.SHOW_DEATH_MESSAGES && vanilla instanceof Boolean enabled) {
            return enabled && (this.lunararc$deathEvent == null || this.lunararc$deathEvent.deathMessage() != null);
        }
        return vanilla;
    }

    @WrapOperation(
            method = "die",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;"),
            require = 0)
    private net.minecraft.network.chat.Component lunararc$deathMessage(
            net.minecraft.world.damagesource.CombatTracker tracker,
            Operation<net.minecraft.network.chat.Component> original) {
        if (this.lunararc$deathEvent != null && this.lunararc$deathEvent.deathMessage() != null) {
            return io.papermc.paper.adventure.PaperAdventure.asVanilla(this.lunararc$deathEvent.deathMessage());
        }
        return original.call(tracker);
    }

    @Unique private boolean lunararc$deathDropsPending;

    @WrapOperation(
            method = "die",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V"),
            require = 1)
    private void lunararc$playerDeathDrops(ServerPlayer self, ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
            Operation<Void> original) {
        org.bukkit.event.entity.PlayerDeathEvent event = this.lunararc$deathEvent;
        if (event == null) {
            original.call(self, level, source);
            return;
        }
        net.minecraft.world.entity.player.Inventory inventory = self.getInventory();
        int size = inventory.getContainerSize();
        java.util.List<net.minecraft.world.item.ItemStack> before = new java.util.ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) before.add(inventory.getItem(slot).copy());

        java.util.List<org.bukkit.inventory.ItemStack> keep = new java.util.ArrayList<>(event.getItemsToKeep());
        if (!event.getKeepInventory()) {
            for (int slot = 0; slot < size; slot++) {
                net.minecraft.world.item.ItemStack item = inventory.getItem(slot);
                if (item.isEmpty()) continue;
                // PREVENT_EQUIPMENT_DROP (curse of vanishing) items were never in getDrops(); vanilla destroys them
                boolean stays = !net.minecraft.world.item.enchantment.EnchantmentHelper.has(
                        item, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)
                        && keep.remove(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(item));
                if (!stays) inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        // vanilla's own death XP is captured and discarded: the event's amount is awarded below instead
        var previous = io.lunararcdevs.lunararc.common.event.LunarArcDeathCapture.current();
        io.lunararcdevs.lunararc.common.event.LunarArcDeathCapture.beginExperienceOnly(self);
        this.lunararc$deathDropsPending = !event.getKeepInventory();
        try {
            original.call(self, level, source);
        } finally {
            io.lunararcdevs.lunararc.common.event.LunarArcDeathCapture.end(previous);
        }
        if (this.lunararc$deathDropsPending) {
            // vanilla skipped Inventory#dropAll (keepInventory gamerule on, but a plugin turned keepInventory off)
            this.lunararc$deathDropsPending = false;
            for (org.bukkit.inventory.ItemStack drop : event.getDrops()) {
                net.minecraft.world.item.ItemStack nms = lunararc$toDrop(drop);
                if (!nms.isEmpty()) self.spawnAtLocation(level, nms);
            }
        }
        if (event.getKeepInventory()) {
            // vanilla may still have destroyed vanishing items; a kept inventory stays exactly as it was
            for (int slot = 0; slot < size; slot++) inventory.setItem(slot, before.get(slot));
        }

        if (event.shouldDropExperience() && event.getDroppedExp() > 0) {
            net.minecraft.world.entity.ExperienceOrb.award(level, self.position(), event.getDroppedExp());
        }
        if (!event.getKeepInventory() && !keep.isEmpty()) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player)
                    ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) self).lunararc$getBukkitEntity();
            for (org.bukkit.inventory.ItemStack extra : keep) {
                if (extra != null && !extra.getType().isAir() && extra.getAmount() > 0) player.getInventory().addItem(extra);
            }
        }
        this.lunararc$captureDeathInventory(self);
    }

    /**
     * Called from Player#dropEquipment in place of Inventory#dropAll while a PlayerDeathEvent is applied. Drops the event's
     * list through vanilla's dropAll in inventory-sized batches, so the drops scatter like vanilla's and pass through
     * NeoForge's capture into LivingDropsEvent. Returns false to let vanilla's dropAll run untouched.
     */
    @Override
    public boolean lunararc$dropDeathItems(Runnable dropAll) {
        if (this.lunararc$deathEvent == null) return false;
        // a plugin set keepInventory while the gamerule is off: the inventory stays, so vanilla must not drop it
        if (this.lunararc$deathEvent.getKeepInventory()) return true;
        if (!this.lunararc$deathDropsPending) return false;
        this.lunararc$deathDropsPending = false;
        net.minecraft.world.entity.player.Inventory inventory = ((ServerPlayer) (Object) this).getInventory();
        int size = inventory.getContainerSize();
        java.util.List<net.minecraft.world.item.ItemStack> stays = new java.util.ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) stays.add(inventory.getItem(slot));
        inventory.clearContent();
        int slot = 0;
        for (org.bukkit.inventory.ItemStack drop : this.lunararc$deathEvent.getDrops()) {
            net.minecraft.world.item.ItemStack nms = lunararc$toDrop(drop);
            if (nms.isEmpty()) continue;
            inventory.setItem(slot++, nms);
            if (slot == size) {
                dropAll.run();
                slot = 0;
            }
        }
        if (slot > 0) dropAll.run();
        inventory.clearContent();
        for (int i = 0; i < size; i++) inventory.setItem(i, stays.get(i));
        return true;
    }

    @Unique
    private static net.minecraft.world.item.ItemStack lunararc$toDrop(org.bukkit.inventory.ItemStack drop) {
        if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) return net.minecraft.world.item.ItemStack.EMPTY;
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(drop);
    }

    @Unique
    private void lunararc$captureDeathInventory(ServerPlayer self) {
        java.util.List<net.minecraft.world.item.ItemStack> state = new java.util.ArrayList<>(self.getInventory().getContainerSize());
        for (int slot = 0; slot < self.getInventory().getContainerSize(); slot++) {
            state.add(self.getInventory().getItem(slot).copy());
        }
        this.lunararc$deathInventoryState = state;
    }

    @Inject(method = "die", at = @At("RETURN"), require = 0)
    private void lunararc$clearDeathEvent(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        this.lunararc$deathEvent = null;
    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), require = 0)
    private void lunararc$captureCommandSystemMessage(net.minecraft.network.chat.Component component, boolean overlay, CallbackInfo ci) {
        if (!overlay && component != null) {
            io.lunararcdevs.lunararc.common.server.LunarArcCommandLogger.capture(((ServerPlayer) (Object) this).getUUID(), component.getString());
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "setCamera", at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
            cancellable = true, require = 0)
    private void lunararc$spectate(net.minecraft.world.entity.Entity camera,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (io.lunararcdevs.lunararc.common.event.LunarArcPaperEvents.spectate((ServerPlayer) (Object) this, camera)) ci.cancel();
    }
}
