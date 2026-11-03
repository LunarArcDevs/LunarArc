package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper world option collisions.allow-vehicle-collisions for boats: when off, a boat is not pushed by other entities. */
@Mixin(AbstractBoat.class)
public abstract class BoatCollisionsOptionMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$noBoatCollisions(Entity other, CallbackInfo ci) {
        if (!WorldConfiguration.forLevel(((Entity) (Object) this).level()).tuning.allowVehicleCollisions) ci.cancel();
    }
}
