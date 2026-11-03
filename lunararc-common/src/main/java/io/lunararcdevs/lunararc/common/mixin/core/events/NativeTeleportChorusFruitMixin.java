package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.lunararcdevs.lunararc.common.event.LunarArcNativeEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportRandomlyConsumeEffect.class)
public abstract class NativeTeleportChorusFruitMixin {
    @WrapOperation(method = "apply", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
    private boolean lunararc$chorus(LivingEntity entity, double x, double y, double z, boolean particles, Operation<Boolean> original) {
        LunarArcNativeEntityEvents.TeleportResult result = LunarArcNativeEntityEvents.fireForLoaderWithoutEvent(
                entity, x, y, z, PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);
        if (result == null) return original.call(entity, x, y, z, particles);
        if (result.cancelled()) return false;
        return original.call(entity, result.x(), result.y(), result.z(), particles);
    }
}
