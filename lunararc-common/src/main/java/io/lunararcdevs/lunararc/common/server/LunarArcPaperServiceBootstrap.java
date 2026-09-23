package io.lunararcdevs.lunararc.common.server;

import io.lunararcdevs.lunararc.api.Unsafe;
import io.papermc.paper.plugin.lifecycle.event.types.LunarArcLifecycleEventTypeProvider;
import io.papermc.paper.registry.RegistryAccess;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;

public final class LunarArcPaperServiceBootstrap {
    private static volatile boolean attempted;

    private LunarArcPaperServiceBootstrap() {}

    public static synchronized void ensureInstalled() {
        if (attempted) return;
        attempted = true;
        seedIfBroken(RegistryAccess::registryAccess,
                "io.papermc.paper.registry.RegistryAccessHolder", "INSTANCE",
                Optional.of(LunarArcRegistryAccess.INSTANCE));

        LunarArcLifecycleEventTypeProvider.ensureInstalled();
    }

    /** Public so package-private donor types (like LifecycleEventTypeProvider) can call this from their own package. */
    public static void seedIfBroken(Callable<?> realAccessor, String holderClass, String field, Object value) {
        try {
            realAccessor.call();
            return;
        } catch (Throwable unresolved) {
            // Falls through to the direct seed below.
        }
        try {
            Class<?> holder = Class.forName(holderClass);
            Field instance = holder.getDeclaredField(field);
            // A plain Field.set() is refused here - static final fields can't be reflectively
            // written even with setAccessible(true) - so this goes around it via Unsafe instead.
            Unsafe.putObject(Unsafe.staticFieldBase(instance), Unsafe.staticFieldOffset(instance), value);
        } catch (Throwable cannotSeed) {
            throw new IllegalStateException("Unable to install LunarArc's " + holderClass + " provider", cannotSeed);
        }
    }
}
