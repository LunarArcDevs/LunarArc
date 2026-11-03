package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option misc.disable-sprint-interruption-on-attack: hitting something no longer stops the attacker sprinting. */
@Mixin(Player.class)
public abstract class SprintInterruptionOptionMixin {
    @WrapOperation(method = "attack", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private void lunararc$keepSprinting(Player player, boolean sprinting, Operation<Void> original) {
        if (!sprinting && WorldConfiguration.forLevel(player.level()).tuning.disableSprintInterruptionOnAttack) return;
        original.call(player, sprinting);
    }
}
