package io.lunararcdevs.lunararc.common.compat;

import io.lunararcdevs.lunararc.common.LunarArcDebug;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.spigotmc.event.entity.EntityDismountEvent;

public final class LunarArcDismountEvents {

    private LunarArcDismountEvents() {}

    public static void fireEntityDismount(Entity vehicle, Entity passenger) {
        if (LunarArcDebug.DISMOUNT) {
            LunarArcDebug.dismount("fireEntityDismount called: vehicle={} passenger={}", vehicle, passenger);
        }
        EntityDismountEvent event = new EntityDismountEvent(vehicle, passenger);
        Bukkit.getPluginManager().callEvent(event);
        if (LunarArcDebug.DISMOUNT) {
            LunarArcDebug.dismount("Normal callEvent done for class {} (identity={})",
                    event.getClass().getName(), System.identityHashCode(event.getClass()));
        }
        LunarArcDuplicateEventBridge.fireOnEveryOtherCopy("EntityDismountEvent", event.getClass(), vehicle, passenger);
    }
}
