package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option entities.behavior.zombies-target-turtle-eggs: when off, zombies do not go after turtle eggs. */
@Mixin(Zombie.class)
public abstract class ZombieTurtleEggsOptionMixin {
    // the turtle egg goal is the first goal addBehaviourGoals registers
    @WrapOperation(method = "addBehaviourGoals", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V", ordinal = 0))
    private void lunararc$turtleEggGoal(GoalSelector selector, int priority, Goal goal, Operation<Void> original) {
        if (!WorldConfiguration.forLevel(((Zombie) (Object) this).level()).tuning.zombiesTargetTurtleEggs) return;
        original.call(selector, priority, goal);
    }
}
