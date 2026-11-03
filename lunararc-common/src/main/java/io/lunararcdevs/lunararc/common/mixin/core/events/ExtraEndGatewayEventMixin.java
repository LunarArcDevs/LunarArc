package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.event.LunarArcMobEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EndGatewayBlock.class)
public abstract class ExtraEndGatewayEventMixin {
    @WrapMethod(method = "getPortalDestination", require = 0)
    private @Nullable TeleportTransition lunararc$gateway(ServerLevel level, Entity entity, BlockPos pos, Operation<TeleportTransition> original) {
        return LunarArcMobEvents.endGateway(level, entity, pos, original.call(level, entity, pos));
    }
}
