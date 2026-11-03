package io.lunararcdevs.lunararc.common.event;

import io.lunararcdevs.lunararc.common.config.LunarArcConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * EXPERIMENTAL entity activation range: a resting mob with no player within range skips most of its ticks. Off by default (experimental.activation-range.enabled
 * in lunararc.conf). A skipped mob still ticks once every {@link #INACTIVE_INTERVAL} ticks so despawning and timers keep moving, and
 * anything moving, fighting, ridden, leashed or in water is never skipped.
 */
public final class LunarArcActivationRange {
    private static final int INACTIVE_INTERVAL = 20;

    private LunarArcActivationRange() {}

    public static boolean shouldSkip(ServerLevel level, Entity entity) {
        if (!LunarArcConfig.isActivationRangeEnabled()) return false;
        if (!(entity instanceof Mob mob)) return false;
        if (mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || mob.getTarget() != null || mob.isInWater() || mob.isInLava()) return false;
        if (!mob.onGround() || mob.isOnFire() || mob.hurtTime > 0) return false;
        // keep the periodic tick spread out across entities instead of all landing on the same game tick
        if ((level.getGameTime() + entity.getId()) % INACTIVE_INTERVAL == 0) return false;

        int range = rangeFor(mob);
        double rangeSquared = (double) range * range;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (player.distanceToSqr(mob) <= rangeSquared) return false;
        }
        return true;
    }

    private static int rangeFor(Mob mob) {
        if (mob instanceof Enemy) return LunarArcConfig.getActivationRangeMonsters();
        if (mob instanceof AbstractVillager) return LunarArcConfig.getActivationRangeVillagers();
        if (mob instanceof net.minecraft.world.entity.animal.Animal) return LunarArcConfig.getActivationRangeAnimals();
        return LunarArcConfig.getActivationRangeMisc();
    }
}
