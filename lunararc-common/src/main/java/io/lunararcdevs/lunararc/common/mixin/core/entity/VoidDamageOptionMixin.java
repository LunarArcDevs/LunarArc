package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Paper world option environment.void-damage-amount: the damage a living entity takes per hit in the void (default 4). */
@Mixin(LivingEntity.class)
public abstract class VoidDamageOptionMixin {
    @ModifyConstant(method = "onBelowWorld", constant = @Constant(floatValue = 4.0F), require = 0)
    private float lunararc$voidDamage(float vanilla) {
        return (float) WorldConfiguration.forLevel(((LivingEntity) (Object) this).level()).tuning.voidDamageAmount;
    }
}
