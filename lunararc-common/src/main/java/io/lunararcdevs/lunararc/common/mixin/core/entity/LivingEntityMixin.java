package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.util.TriState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements LivingEntityBridge {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private static net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private static int LIVING_ENTITY_FLAG_SPIN_ATTACK;
    @org.spongepowered.asm.mixin.Shadow private static byte entityEventForEquipmentBreak(net.minecraft.world.entity.EquipmentSlot slot) { throw new AssertionError(); }
    @org.spongepowered.asm.mixin.Shadow protected abstract void completeUsingItem();
    @Unique private TriState lunararc$frictionState = TriState.NOT_SET;
    @Unique private int lunararc$shieldBlockingDelay = 5;
    @Unique private int lunararc$maximumAirOverride = -1;
    @Unique private boolean lunararc$collidable = true;
    @Unique private final Set<UUID> lunararc$collidableExemptions = new HashSet<>();
    @Unique private boolean lunararc$bukkitCanPickupItems;
    @Unique private org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason lunararc$healReason = org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM;
    @Unique private boolean lunararc$fastRegen;
    @Unique private org.bukkit.event.entity.EntityPotionEffectEvent.Cause lunararc$effectCause = org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN;
    @Unique private boolean lunararc$suppressEffectEvent;

    @Override public void lunararc$completeUsingItem() { this.completeUsingItem(); }
    @Override public net.minecraft.network.syncher.EntityDataAccessor<Integer> lunararc$getArrowCountDataAccessorBridge() { return DATA_ARROW_COUNT_ID; }
    @Override public int lunararc$getSpinAttackFlagBridge() { return LIVING_ENTITY_FLAG_SPIN_ATTACK; }
    @Override public byte lunararc$entityEventForEquipmentBreakBridge(net.minecraft.world.entity.EquipmentSlot slot) { return entityEventForEquipmentBreak(slot); }
    @Override public TriState lunararc$getFrictionState() { return lunararc$frictionState; }
    @Override public void lunararc$setFrictionState(TriState state) { lunararc$frictionState = java.util.Objects.requireNonNull(state, "state"); }
    @Override public int lunararc$getShieldBlockingDelay() { return lunararc$shieldBlockingDelay; }
    @Override public void lunararc$setShieldBlockingDelay(int delay) {
        if (delay < 0) throw new IllegalArgumentException("Shield blocking delay must be >= 0");
        lunararc$shieldBlockingDelay = delay;
    }
    @Override public int lunararc$getMaximumAirOverride() { return lunararc$maximumAirOverride; }
    @Override public void lunararc$setMaximumAirOverride(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Maximum air must be >= 0");
        lunararc$maximumAirOverride = ticks;
    }
    @Override public boolean lunararc$isCollidable() { return lunararc$collidable; }
    @Override public void lunararc$setCollidable(boolean collidable) { lunararc$collidable = collidable; }
    @Override public Set<UUID> lunararc$getCollidableExemptions() { return lunararc$collidableExemptions; }
    @Override public boolean lunararc$getBukkitCanPickupItems() { return lunararc$bukkitCanPickupItems; }
    @Override public void lunararc$setBukkitCanPickupItems(boolean pickup) { lunararc$bukkitCanPickupItems = pickup; }
    @Override public void lunararc$pushHealReason(org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason reason, boolean fastRegen) {
        this.lunararc$healReason = java.util.Objects.requireNonNull(reason, "reason");
        this.lunararc$fastRegen = fastRegen;
    }
    @Override public void lunararc$pushEffectCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        this.lunararc$effectCause = java.util.Objects.requireNonNull(cause, "cause");
    }

    @Override
    public boolean lunararc$removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide || entity.getActiveEffects().isEmpty()) {
            return false;
        }
        boolean changed = false;
        java.util.List<net.minecraft.world.effect.MobEffectInstance> effects =
                new java.util.ArrayList<>(entity.getActiveEffects());
        for (net.minecraft.world.effect.MobEffectInstance old : effects) {
            var event = CraftEventFactory.callEntityPotionEffectChangeEvent(
                    entity, old, null, cause,
                    org.bukkit.event.entity.EntityPotionEffectEvent.Action.CLEARED, true);
            if (event.isCancelled()) {
                continue;
            }
            this.lunararc$suppressEffectEvent = true;
            try {
                changed |= entity.removeEffect(old.getEffect());
            } finally {
                this.lunararc$suppressEffectEvent = false;
            }
        }
        return changed;
    }

    @Unique
    private org.bukkit.event.entity.EntityPotionEffectEvent.Cause lunararc$consumeEffectCause() {
        var cause = this.lunararc$effectCause;
        this.lunararc$effectCause = org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN;
        return cause;
    }

    @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean lunararc$effectChange(
            net.minecraft.world.effect.MobEffectInstance incoming,
            net.minecraft.world.entity.Entity source,
            Operation<Boolean> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        // Structure/entity-spawn generation genuinely runs on worker threads even in vanilla
        // (confirmed by a real crash: ChunkGenerationTask -> StructureTemplate.addEntitiesToWorld
        // -> a mod's spawn-time potion-effect application -> this method, all on a
        // ForkJoinWorkerThread). PaperEventManager correctly refuses to fire a synchronous event
        // off the main thread (that's real, correct Paper safety behavior, not a bug) — so this
        // must not even attempt to fire the event in that case. Falling through to the real
        // vanilla/modded behavior unmodified, same as the existing suppressed/client-side cases
        // below, rather than crashing the whole chunk generation task.
        if (this.lunararc$suppressEffectEvent || entity.level().isClientSide || !org.bukkit.Bukkit.isPrimaryThread()) {
            return original.call(incoming, source);
        }

        var cause = this.lunararc$consumeEffectCause();
        net.minecraft.world.effect.MobEffectInstance old = entity.getEffect(incoming.getEffect());
        if (old == null) {
            var event = CraftEventFactory.callEntityPotionEffectChangeEvent(entity, null, incoming, cause, true);
            return !event.isCancelled() && original.call(incoming, source);
        }

        net.minecraft.world.effect.MobEffectInstance probe = new net.minecraft.world.effect.MobEffectInstance(old);
        boolean vanillaOverride = probe.update(incoming);
        var event = CraftEventFactory.callEntityPotionEffectChangeEvent(entity, old, incoming, cause, vanillaOverride);
        if (event.isCancelled() || !event.isOverride()) {
            return false;
        }
        if (vanillaOverride) {
            return original.call(incoming, source);
        }

        this.lunararc$suppressEffectEvent = true;
        try {
            entity.removeEffect(incoming.getEffect());
        } finally {
            this.lunararc$suppressEffectEvent = false;
        }
        return original.call(incoming, source);
    }

    @WrapMethod(method = "removeEffect")
    private boolean lunararc$effectRemove(
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            Operation<Boolean> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        // Same reasoning as lunararc$effectChange above — removeEffect() is a general vanilla
        // method reachable from worldgen/structure-population worker threads too.
        if (this.lunararc$suppressEffectEvent || entity.level().isClientSide || !org.bukkit.Bukkit.isPrimaryThread()) {
            return original.call(effect);
        }
        net.minecraft.world.effect.MobEffectInstance old = entity.getEffect(effect);
        if (old == null) {
            this.lunararc$consumeEffectCause();
            return original.call(effect);
        }
        var cause = this.lunararc$consumeEffectCause();
        var event = CraftEventFactory.callEntityPotionEffectChangeEvent(
                entity, old, null, cause, org.bukkit.event.entity.EntityPotionEffectEvent.Action.REMOVED, true);
        if (event.isCancelled()) {
            return false;
        }
        return original.call(effect);
    }

    @ModifyExpressionValue(
            method = "tickEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectInstance;tick(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z"),
            require = 0)
    private boolean lunararc$effectExpiration(
            boolean stillActive,
            @Local net.minecraft.world.effect.MobEffectInstance effect) {
        if (stillActive) {
            return true;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide) {
            return false;
        }
        var event = CraftEventFactory.callEntityPotionEffectChangeEvent(
                entity, effect, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.EXPIRATION,
                org.bukkit.event.entity.EntityPotionEffectEvent.Action.REMOVED, true);

        return event.isCancelled();
    }

    @WrapOperation(
            method = "checkTotemDeathProtection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"),
            require = 0)
    private boolean lunararc$totemClearCause(LivingEntity entity, Operation<Boolean> original) {
        return ((LivingEntityBridge) entity).lunararc$removeAllEffects(
                org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
    }

    @Inject(
            method = "checkTotemDeathProtection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 0)
    private void lunararc$totemAddCause(
            DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        this.lunararc$pushEffectCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
    }

    @WrapOperation(
            method = "randomTeleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;teleportTo(DDD)V"),
            require = 0)
    private void lunararc$entityRandomTeleport(
            LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer || entity.level().isClientSide) {
            original.call(entity, x, y, z);
            return;
        }
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Entity bukkitEntity)) {
            original.call(entity, x, y, z);
            return;
        }
        org.bukkit.Location from = bukkitEntity.getLocation();
        org.bukkit.Location to = new org.bukkit.Location(from.getWorld(), x, y, z, from.getYaw(), from.getPitch());
        var event = new org.bukkit.event.entity.EntityTeleportEvent(bukkitEntity, from, to);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() != from.getWorld()) {
            return;
        }
        org.bukkit.Location destination = event.getTo();
        original.call(entity, destination.getX(), destination.getY(), destination.getZ());
    }

    @WrapMethod(method = "heal")
    private void lunararc$regainHealth(float amount, Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        var reason = this.lunararc$healReason;
        boolean fast = this.lunararc$fastRegen;
        this.lunararc$healReason = org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM;
        this.lunararc$fastRegen = false;
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.LivingEntity living) || entity.level().isClientSide
                || !org.bukkit.Bukkit.isPrimaryThread()) {
            original.call(amount);
            return;
        }
        org.bukkit.event.entity.EntityRegainHealthEvent event =
                new org.bukkit.event.entity.EntityRegainHealthEvent(living, amount, reason, fast);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) original.call((float) event.getAmount());
    }

    @Inject(method = "shouldDiscardFriction", at = @At("RETURN"), cancellable = true, require = 0)
    private void lunararc$paperFriction(CallbackInfoReturnable<Boolean> cir) {
        if (lunararc$frictionState != TriState.NOT_SET) {
            cir.setReturnValue(lunararc$frictionState == TriState.FALSE);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 0)
    private void lunararc$saveLivingState(CompoundTag tag, CallbackInfo ci) {
        if (lunararc$frictionState != TriState.NOT_SET) {
            tag.putString("Paper.FrictionState", lunararc$frictionState.toString());
        }
        if (lunararc$maximumAirOverride >= 0) {
            tag.putInt("Bukkit.MaxAirSupply", lunararc$maximumAirOverride);
        }
        if (!lunararc$collidable) {
            tag.putBoolean("Bukkit.Collidable", false);
        }
        if (lunararc$bukkitCanPickupItems) {
            tag.putBoolean("Bukkit.PickupLoot", true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 0)
    private void lunararc$loadLivingState(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("Paper.FrictionState")) {
            try {
                lunararc$frictionState = TriState.valueOf(tag.getString("Paper.FrictionState"));
            } catch (IllegalArgumentException ignored) {
                lunararc$frictionState = TriState.NOT_SET;
            }
        }
        lunararc$maximumAirOverride = tag.contains("Bukkit.MaxAirSupply") ? Math.max(0, tag.getInt("Bukkit.MaxAirSupply")) : -1;
        lunararc$collidable = !tag.contains("Bukkit.Collidable") || tag.getBoolean("Bukkit.Collidable");
        lunararc$bukkitCanPickupItems = tag.getBoolean("Bukkit.PickupLoot");
    }

    @WrapMethod(method = "hurt")
    private boolean lunararc$onDamage(DamageSource source, float amount, Operation<Boolean> original) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (entity.level().isClientSide
                || entity.isDeadOrDying()
                || entity.isInvulnerableTo(source)
                || (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                    && entity.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE))) {
            return original.call(source, amount);
        }

        EntityDamageEvent event = CraftEventFactory.callEntityDamageEvent(entity, source, amount);
        if (event.isCancelled()) return false;

        double adjusted = event.getFinalDamage();
        if (!Double.isFinite(adjusted)) adjusted = 0.0D;
        return original.call(source, (float) Math.max(0.0D, adjusted));
    }

    @WrapMethod(method = "die")
    private void lunararc$onDeath(DamageSource source, Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof net.minecraft.server.level.ServerPlayer) {
            original.call(source);
            return;
        }
        org.bukkit.event.entity.EntityDeathEvent event = CraftEventFactory.callEntityDeathEvent(entity, source);
        if (event != null && event.isCancelled()) {
            double revive = event.getReviveHealth();
            if (revive <= 0.0D) revive = entity.getMaxHealth();
            entity.setHealth((float) Math.min(revive, entity.getMaxHealth()));
            return;
        }
        original.call(source);
    }
}