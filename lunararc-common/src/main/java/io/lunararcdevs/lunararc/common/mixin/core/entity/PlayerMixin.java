package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import io.lunararcdevs.lunararc.common.bridge.PlayerAffectsSpawningBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Player.class)
public abstract class PlayerMixin implements PlayerAffectsSpawningBridge, io.lunararcdevs.lunararc.common.bridge.PlayerExhaustionBridge, io.lunararcdevs.lunararc.common.bridge.PlayerShieldBridge {
    public org.bukkit.craftbukkit.entity.CraftHumanEntity getBukkitEntity() {
        return (org.bukkit.craftbukkit.entity.CraftHumanEntity) ((EntityBridge) (Object) this).lunararc$getBukkitEntity();
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$preAttackEvent(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        if (io.lunararcdevs.lunararc.common.event.LunarArcPaperEvents.preAttack((Player) (Object) this, target)) ci.cancel();
    }

    @Inject(method = "startAutoSpinAttack", at = @At("HEAD"), require = 0)
    private void lunararc$riptideEvent(int ticks, float damage, ItemStack stack, CallbackInfo ci) {
        io.lunararcdevs.lunararc.common.event.LunarArcPaperEvents.riptide((Player) (Object) this, stack);
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getProjectile", at = @At("RETURN"), require = 0)
    private ItemStack lunararc$readyArrow(ItemStack arrow, ItemStack weapon) {
        return io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.readyArrow((Player) (Object) this, weapon, arrow);
    }

    @org.spongepowered.asm.mixin.Shadow private int sleepCounter;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void lunararc$deepSleep(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (this.sleepCounter == 99 && self.isSleeping() && !self.level().isClientSide
                && !io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.deepSleep(self)) {
            this.sleepCounter = Integer.MIN_VALUE;
        }
    }

    @Unique private net.minecraft.world.entity.LivingEntity lunararc$shieldAttacker;

    @Inject(method = "blockUsingItem", at = @At("HEAD"), require = 1)
    private void lunararc$shieldAttacker(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity attacker, CallbackInfo ci) {
        this.lunararc$shieldAttacker = attacker;
    }
    @Override public net.minecraft.world.entity.LivingEntity lunararc$getShieldAttacker() { return this.lunararc$shieldAttacker; }

    @Inject(method = "blockUsingItem", at = @At("RETURN"), require = 1)
    private void lunararc$clearShieldAttacker(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity attacker, CallbackInfo ci) {
        this.lunararc$shieldAttacker = null;
    }



    @Unique
    private boolean lunararc$affectsSpawning = true;
    @Unique private org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason lunararc$exhaustionReason = org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.UNKNOWN;

    @Override
    public boolean lunararc$getAffectsSpawning() {
        return this.lunararc$affectsSpawning;
    }

    @Override
    public void lunararc$setAffectsSpawning(boolean affectsSpawning) {
        this.lunararc$affectsSpawning = affectsSpawning;
    }

    @Override
    public void lunararc$pushExhaustionReason(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason reason) {
        this.lunararc$exhaustionReason = java.util.Objects.requireNonNull(reason, "reason");
    }



    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "causeFoodExhaustion")
    private void lunararc$exhaustion(float exhaustion, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        Player handle = (Player) (Object) this;
        var reason = this.lunararc$exhaustionReason;
        this.lunararc$exhaustionReason = org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.UNKNOWN;
        Object bukkit = ((EntityBridge) handle).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.HumanEntity human) || handle.level().isClientSide) {
            original.call(exhaustion);
            return;
        }
        var event = new org.bukkit.event.entity.EntityExhaustionEvent(human, reason, exhaustion);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) original.call(event.getExhaustion());
    }






    @Inject(
            method = "actuallyHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),
            require = 0)
    private void lunararc$damageExhaustion(
            net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.lunararc$pushExhaustionReason(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.DAMAGED);
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"), require = 0)
    private void lunararc$attackExhaustion(net.minecraft.world.entity.Entity target, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.lunararc$pushExhaustionReason(org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.ATTACK);
    }

    @Inject(
            method = "turtleHelmetTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 0)
    private void lunararc$turtleHelmetCause(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) this)
                .lunararc$pushEffectCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TURTLE_HELMET);
    }


    @WrapOperation(method = "tryToStartFallFlying", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;startFallFlying()V"))
    private void lunararc$startGlide(Player self, Operation<Void> original) {
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(self, true).isCancelled()) {
            original.call(self);
        }
    }

    @WrapOperation(method = "dropEquipment", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V"))
    private void lunararc$deathDrops(net.minecraft.world.entity.player.Inventory inventory, Operation<Void> original) {
        if ((Object) this instanceof io.lunararcdevs.lunararc.common.bridge.ServerPlayerDeathBridge death
                && death.lunararc$dropDeathItems(() -> original.call(inventory))) return;
        original.call(inventory);
    }
}
