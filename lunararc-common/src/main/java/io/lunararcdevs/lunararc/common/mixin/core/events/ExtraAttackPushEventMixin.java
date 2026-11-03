package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.event.LunarArcMobEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * EntityPushedByEntityAttackEvent for the mace smash and the sonic boom of a warden. Their attack methods record the attacker in
 * LunarArcMobEvents.pusher; the push itself runs inside a lambda, so it is caught here, on the one method every push goes through.
 */
@Mixin(Entity.class)
public abstract class ExtraAttackPushEventMixin {
    @WrapMethod(method = "push(DDD)V", require = 0)
    private void lunararc$attackPush(double x, double y, double z, Operation<Void> original) {
        Entity attacker = LunarArcMobEvents.pusher;
        if (attacker == null || attacker == (Object) this) {
            original.call(x, y, z);
            return;
        }
        Vec3 push = LunarArcMobEvents.pushedByAttack((Entity) (Object) this, attacker, x, y, z);
        if (push == null) return;
        original.call(push.x, push.y, push.z);
    }
}
