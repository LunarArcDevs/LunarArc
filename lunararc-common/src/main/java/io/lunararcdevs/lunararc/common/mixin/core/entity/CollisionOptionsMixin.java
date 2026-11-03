package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paper world options collisions.max-entity-collisions, collisions.only-players-collide and collisions.allow-player-cramming-damage.
 * Cramming still counts every nearby entity; only the pushing is limited.
 */
@Mixin(LivingEntity.class)
public abstract class CollisionOptionsMixin {
    @Unique private int lunararc$pushes;

    @Inject(method = "pushEntities", at = @At("HEAD"), require = 0)
    private void lunararc$resetPushes(CallbackInfo ci) {
        this.lunararc$pushes = 0;
    }

    @WrapOperation(method = "pushEntities", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;doPush(Lnet/minecraft/world/entity/Entity;)V"))
    private void lunararc$limitPushes(LivingEntity self, Entity other, Operation<Void> original) {
        WorldConfiguration.Tuning tuning = WorldConfiguration.forLevel(self.level()).tuning;
        if (tuning.maxEntityCollisions > 0 && ++this.lunararc$pushes > tuning.maxEntityCollisions) return;
        if (tuning.onlyPlayersCollide && !(self instanceof Player) && !(other instanceof Player)) return;
        original.call(self, other);
    }

    @WrapOperation(method = "pushEntities", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean lunararc$crammingDamage(LivingEntity self, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        if (self instanceof ServerPlayer && !WorldConfiguration.forLevel(level).tuning.allowPlayerCrammingDamage) return false;
        return original.call(self, level, source, amount);
    }
}
