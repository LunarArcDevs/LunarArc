package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.lunararcdevs.lunararc.common.event.LunarArcNativeEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownEnderpearl.class)
public abstract class NativeTeleportEnderPearlMixin {
    @WrapOperation(method = "onHit", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer lunararc$pearlPlayer(ServerPlayer player, TeleportTransition transition, Operation<ServerPlayer> original) {
        LunarArcNativeEntityEvents.TeleportResult result = LunarArcNativeEntityEvents.fireForLoaderWithoutEvent(
                player, transition.position().x, transition.position().y, transition.position().z, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        if (result == null) return original.call(player, transition);
        if (result.cancelled()) return player;
        return original.call(player, transition.withPosition(new Vec3(result.x(), result.y(), result.z())));
    }

    @WrapOperation(method = "onHit", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;"))
    private Entity lunararc$pearlEntity(Entity entity, TeleportTransition transition, Operation<Entity> original) {
        LunarArcNativeEntityEvents.TeleportResult result = LunarArcNativeEntityEvents.fireForLoaderWithoutEvent(
                entity, transition.position().x, transition.position().y, transition.position().z, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        if (result == null) return original.call(entity, transition);
        if (result.cancelled()) return entity;
        return original.call(entity, transition.withPosition(new Vec3(result.x(), result.y(), result.z())));
    }
}
