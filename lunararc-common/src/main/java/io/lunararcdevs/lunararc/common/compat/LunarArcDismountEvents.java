package io.lunararcdevs.lunararc.common.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spigotmc.event.entity.EntityDismountEvent;

/**
 * Constructing and firing EntityDismountEvent used to happen inline inside EntityMixin itself - a
 * class Mixin's own ASM transform pipeline processes. The leading hypothesis for why some plugins'
 * listeners (SitEverywhere confirmed) never receive this event is that constructing it from inside
 * a Mixin-transformed class is what causes a second, genuinely distinct Class object for it to
 * exist in the first place (Mixin's own frame-computation machinery resolving types separately
 * from normal runtime class loading). Moving construction into this plain, untransformed class
 * removes that trigger entirely rather than just working around its symptom. The reflection-based
 * dual-fire bridge stays as a safety net in case this hypothesis is incomplete.
 *
 * TEMPORARY diagnostic logging (LunarArc/DismountDebug) is in here to find out where the chain
 * actually breaks, since two prior fix attempts based on the class-identity theory both failed to
 * resolve this live. Remove once the real cause is confirmed.
 */
public final class LunarArcDismountEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc/DismountDebug");

    private LunarArcDismountEvents() {}

    public static void fireEntityDismount(Entity vehicle, Entity passenger) {
        LOGGER.info("fireEntityDismount called: vehicle={} passenger={}", vehicle, passenger);
        EntityDismountEvent event = new EntityDismountEvent(vehicle, passenger);
        Bukkit.getPluginManager().callEvent(event);
        LOGGER.info("Normal callEvent done for class {} (identity={})",
                event.getClass().getName(), System.identityHashCode(event.getClass()));
        LunarArcDuplicateEventBridge.fireOnEveryOtherCopy("EntityDismountEvent", event.getClass(), vehicle, passenger);
    }
}
