package io.lunararcdevs.lunararc.common.bridge.access;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/** Normal runtime bridge implemented by the corresponding Mixin accessor/invoker. */
public interface ShulkerBulletAccessBridge {
    net.minecraft.world.entity.EntityReference<Entity> lunararc$getFinalTarget();
    void lunararc$setFinalTarget(net.minecraft.world.entity.EntityReference<Entity> target);
    Direction lunararc$getCurrentMoveDirection();
    void lunararc$setCurrentMoveDirection(Direction direction);
    int lunararc$getFlightSteps();
    void lunararc$setFlightSteps(int steps);
    double lunararc$getTargetDeltaX();
    void lunararc$setTargetDeltaX(double value);
    double lunararc$getTargetDeltaY();
    void lunararc$setTargetDeltaY(double value);
    double lunararc$getTargetDeltaZ();
    void lunararc$setTargetDeltaZ(double value);
}
