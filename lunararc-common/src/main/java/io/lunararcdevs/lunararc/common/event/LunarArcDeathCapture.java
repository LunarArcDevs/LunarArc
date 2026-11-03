package io.lunararcdevs.lunararc.common.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class LunarArcDeathCapture {
    public static final class Capture {
        final LivingEntity entity;
        public final List<ItemStack> items = new ArrayList<>();
        public int exp;
        public boolean cancelled;
        boolean captureItems = true;

        Capture(LivingEntity entity) {
            this.entity = entity;
        }
    }

    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();

    private static final java.util.Map<LivingEntity, Double> EXP_SCALE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private LunarArcDeathCapture() {}

    public static void setExpScale(LivingEntity entity, double scale) {
        EXP_SCALE.put(entity, scale);
    }

    public static double expScale(LivingEntity entity) {
        Double scale = EXP_SCALE.get(entity);
        return scale == null ? 1.0D : scale;
    }

    public static Capture begin(LivingEntity entity) {
        Capture capture = new Capture(entity);
        CURRENT.set(capture);
        return capture;
    }

    /** Captures only the experience an entity drops; its items spawn normally (player deaths, see ServerPlayerMixin). */
    public static Capture beginExperienceOnly(LivingEntity entity) {
        Capture capture = begin(entity);
        capture.captureItems = false;
        return capture;
    }

    public static void end(Capture previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public static Capture current() {
        return CURRENT.get();
    }

    public static boolean captureEntity(Entity added) {
        Capture capture = CURRENT.get();
        if (capture == null || !capture.captureItems) return false;
        if (added instanceof ItemEntity item && item.distanceToSqr(capture.entity) < 64.0D) {
            if (!item.getItem().isEmpty()) capture.items.add(item.getItem().copy());
            return true;
        }
        return false;
    }

    public static boolean captureExperience(int amount) {
        Capture capture = CURRENT.get();
        if (capture == null) return false;
        capture.exp += amount;
        return true;
    }
}
