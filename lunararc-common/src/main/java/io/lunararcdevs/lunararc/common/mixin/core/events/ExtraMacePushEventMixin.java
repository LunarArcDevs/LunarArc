package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.event.LunarArcMobEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * EntityPushedByEntityAttackEvent for a mace smash. The attacker is held in a static while the knockback runs,
 * because the push itself happens in a lambda that is not given the attacker directly.
 */
@Mixin(MaceItem.class)
public abstract class ExtraMacePushEventMixin {
    @WrapMethod(method = "knockback", require = 0)
    private static void lunararc$context(Level level, Entity attacker, Entity target, Operation<Void> original) {
        Entity before = LunarArcMobEvents.pusher;
        LunarArcMobEvents.pusher = attacker;
        try {
            original.call(level, attacker, target);
        } finally {
            LunarArcMobEvents.pusher = before;
        }
    }
}
