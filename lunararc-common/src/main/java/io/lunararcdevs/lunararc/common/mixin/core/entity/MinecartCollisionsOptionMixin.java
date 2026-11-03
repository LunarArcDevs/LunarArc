package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper world option collisions.allow-vehicle-collisions for minecarts: when off, a minecart is not pushed by other entities. */
@Mixin(AbstractMinecart.class)
public abstract class MinecartCollisionsOptionMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$noMinecartCollisions(Entity other, CallbackInfo ci) {
        if (!WorldConfiguration.forLevel(((Entity) (Object) this).level()).tuning.allowVehicleCollisions) ci.cancel();
    }
}
