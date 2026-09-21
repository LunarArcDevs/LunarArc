package io.lunararcdevs.lunararc.common.compat;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class SableCompatibility {
    private static final double MAX_QUERY_SIZE = 10_000.0;
    private static final AtomicBoolean REPORTED_INVALID_QUERY = new AtomicBoolean();

    private static final String TRACKING_INTERFACE =
            "dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension";
    private static volatile boolean trackingSupportChecked;
    private static volatile Class<?> trackingInterface;
    private static volatile Method trackingSetter;

    private SableCompatibility() {}

    public static boolean isSafeEntityQuery(AABB bounds) {
        return bounds != null
                && Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)
                && Double.isFinite(bounds.getSize()) && bounds.getSize() <= MAX_QUERY_SIZE;
    }

    public static boolean allowEntityQuery(AABB bounds) {
        if (isSafeEntityQuery(bounds)) return true;
        if (REPORTED_INVALID_QUERY.compareAndSet(false, true)) {
            org.slf4j.LoggerFactory.getLogger(SableCompatibility.class).warn(
                    "Blocked an invalid or excessively large Sable entity query: {}. Further occurrences will not be logged.", bounds);
        }
        return false;
    }

    public static void safeEntityLookupRemove(Runnable remove) {
        try {
            remove.run();
        } catch (ArrayIndexOutOfBoundsException e) {
            org.slf4j.LoggerFactory.getLogger(SableCompatibility.class).warn(
                    "EntityLookup.remove skipped an out-of-bounds removal caused by Sable sub-level entity tracking corruption: {}", e.toString());
        }
    }

    /**
     * Sable's own entity_sublevel_collision.EntityMixin never clears sable$trackingSubLevel for a
     * ServerPlayer once set (only for non-player entities), and its entities_stick_sublevels
     * player tick hook reasserts that entity's position every tick to follow the tracked sub-level
     * regardless of game mode - so a player who ever stood on a Sable structure keeps getting
     * forced back toward it after switching to spectator, which reads as "can't fly through
     * blocks." Clearing it here on spectator entry, via Sable's own public extension interface, is
     * a compat-side workaround for that upstream gap. No-op via reflection when Sable is absent.
     */
    public static void clearTrackingSubLevelIfPresent(Entity entity) {
        Class<?> iface = trackingInterface();
        if (iface == null || !iface.isInstance(entity)) return;
        try {
            Method setter = trackingSetter(iface);
            if (setter != null) setter.invoke(entity, (Object) null);
        } catch (ReflectiveOperationException ex) {
            org.slf4j.LoggerFactory.getLogger(SableCompatibility.class).warn(
                    "Failed to clear Sable's tracked sub-level on spectator entry for {}", entity, ex);
        }
    }

    private static Class<?> trackingInterface() {
        if (!trackingSupportChecked) {
            synchronized (SableCompatibility.class) {
                if (!trackingSupportChecked) {
                    try {
                        trackingInterface = Class.forName(
                                TRACKING_INTERFACE, false, SableCompatibility.class.getClassLoader());
                    } catch (Throwable notPresent) {
                        trackingInterface = null;
                    }
                    trackingSupportChecked = true;
                }
            }
        }
        return trackingInterface;
    }

    private static Method trackingSetter(Class<?> iface) {
        Method cached = trackingSetter;
        if (cached != null) return cached;
        synchronized (SableCompatibility.class) {
            if (trackingSetter == null) {
                for (Method candidate : iface.getMethods()) {
                    if (candidate.getName().equals("sable$setTrackingSubLevel") && candidate.getParameterCount() == 1) {
                        trackingSetter = candidate;
                        break;
                    }
                }
            }
            return trackingSetter;
        }
    }
}
