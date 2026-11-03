package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.event.LunarArcMobEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;

/** EntityPushedByEntityAttackEvent for a warden's sonic boom. */
@Mixin(SonicBoom.class)
public abstract class ExtraSonicBoomPushEventMixin {
    @WrapMethod(method = "tick", require = 0)
    private void lunararc$context(ServerLevel level, Warden warden, long gameTime, Operation<Void> original) {
        net.minecraft.world.entity.Entity before = LunarArcMobEvents.pusher;
        LunarArcMobEvents.pusher = warden;
        try {
            original.call(level, warden, gameTime);
        } finally {
            LunarArcMobEvents.pusher = before;
        }
    }
}
