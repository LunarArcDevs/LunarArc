package io.lunararcdevs.lunararc.common.compat;

import io.lunararcdevs.lunararc.common.LunarArcDebug;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class LunarArcDuplicateEventBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LunarArcDuplicateEventBridge.class);

    private LunarArcDuplicateEventBridge() {}

    public static void fireOnEveryOtherCopy(String simpleEventClassName, Class<?> alreadyFired, Object... constructorArgs) {
        Set<Method> invoked = new HashSet<>();
        int handlerListCount = 0;
        int listenerCount = 0;
        int candidateMethodCount = 0;
        for (HandlerList handlerList : HandlerList.getHandlerLists()) {
            handlerListCount++;
            for (RegisteredListener registered : handlerList.getRegisteredListeners()) {
                listenerCount++;
                Listener listener = registered.getListener();
                for (Method method : listener.getClass().getMethods()) {
                    if (!method.isAnnotationPresent(EventHandler.class)) continue;
                    if (method.getParameterCount() != 1) continue;
                    Class<?> paramType = method.getParameterTypes()[0];
                    if (!paramType.getSimpleName().equals(simpleEventClassName)) continue;
                    candidateMethodCount++;
                    if (LunarArcDebug.DISMOUNT) {
                        LunarArcDebug.dismount("candidate: listener={} method={} paramType={} (identity={}) sameAsAlreadyFired={}",
                                listener.getClass().getName(), method.getName(), paramType.getName(),
                                System.identityHashCode(paramType), paramType == alreadyFired);
                    }
                    if (paramType == alreadyFired) continue;
                    if (!invoked.add(method)) continue;
                    invoke(method, listener, paramType, constructorArgs);
                }
            }
        }
        if (LunarArcDebug.DISMOUNT) {
            LunarArcDebug.dismount("scan complete: handlerLists={} listeners={} candidateMethods={} invoked={}",
                    handlerListCount, listenerCount, candidateMethodCount, invoked.size());
        }
    }

    private static void invoke(Method method, Listener listener, Class<?> eventClass, Object[] args) {
        try {
            for (Constructor<?> constructor : eventClass.getConstructors()) {
                if (constructor.getParameterCount() != args.length) continue;
                Object instance = constructor.newInstance(args);
                method.setAccessible(true);
                method.invoke(listener, instance);
                return;
            }
            LOGGER.warn("No matching constructor found on a duplicate copy of {} to bridge a plugin "
                    + "listener registered against it", eventClass.getName());
        } catch (Throwable failure) {
            LOGGER.warn("Failed to bridge an event to a duplicate copy of {}", eventClass.getName(), failure);
        }
    }
}
