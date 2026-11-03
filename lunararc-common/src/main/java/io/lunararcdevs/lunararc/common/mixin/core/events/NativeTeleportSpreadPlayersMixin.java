package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.lunararcdevs.lunararc.common.event.LunarArcNativeEntityEvents;
import java.util.Set;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PlayerTeleportEvent / EntityTeleportEvent for /spreadplayers on loaders without a native teleport event (Fabric, Quilt). */
@Mixin(SpreadPlayersCommand.class)
public abstract class NativeTeleportSpreadPlayersMixin {
    @WrapOperation(method = "setPlayerPositions", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"))
    private static boolean lunararc$spreadTeleport(Entity entity, ServerLevel level, double x, double y, double z, Set<Relative> relatives,
            float yaw, float pitch, boolean resetCamera, Operation<Boolean> original) {
        if (level != entity.level()) return original.call(entity, level, x, y, z, relatives, yaw, pitch, resetCamera);
        LunarArcNativeEntityEvents.TeleportResult result = LunarArcNativeEntityEvents.fireForLoaderWithoutEvent(
                entity, x, y, z, PlayerTeleportEvent.TeleportCause.COMMAND);
        if (result == null) return original.call(entity, level, x, y, z, relatives, yaw, pitch, resetCamera);
        if (result.cancelled()) return false;
        return original.call(entity, level, result.x(), result.y(), result.z(), relatives, yaw, pitch, resetCamera);
    }
}
