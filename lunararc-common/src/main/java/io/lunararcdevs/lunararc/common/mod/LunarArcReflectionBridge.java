package io.lunararcdevs.lunararc.common.mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.StackWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class LunarArcReflectionBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc/ReflectionBridge");
    private static final LunarArcRemapper REMAPPER = new LunarArcRemapper(true);

    private LunarArcReflectionBridge() {
    }


    public static Class<?> forName(String name) throws ClassNotFoundException {
        String mapped = REMAPPER.mapRuntimeClassName(name);
        ClassLoader callerLoader = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(type -> type != LunarArcReflectionBridge.class)
                        .map(Class::getClassLoader)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(Thread.currentThread().getContextClassLoader()));
        if (callerLoader == null) callerLoader = LunarArcReflectionBridge.class.getClassLoader();
        try {
            Class<?> resolved = Class.forName(mapped, true, callerLoader);
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
                io.lunararcdevs.lunararc.common.LunarArcDebug.reflect("forName {} -> {} resolved via {} (from {})",
                        name, mapped, callerLoader, io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
            }
            return resolved;
        } catch (ClassNotFoundException first) {
            if (!mapped.equals(name)) {
                try {
                    return Class.forName(name, true, callerLoader);
                } catch (ClassNotFoundException second) {
                    if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
                        LOGGER.warn("Class.forName failed for both mapped name '{}' and original name '{}' "
                                        + "using classloader {} (caller {})", mapped, name, callerLoader,
                                io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
                    } else {
                        io.lunararcdevs.lunararc.common.LunarArcDebug.hiddenLookupFailure(LOGGER);
                    }
                    throw second;
                }
            }
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
                LOGGER.warn("Class.forName failed for '{}' (unmapped == input) using classloader {} (caller {})",
                        name, callerLoader, io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
            } else {
                io.lunararcdevs.lunararc.common.LunarArcDebug.hiddenLookupFailure(LOGGER);
            }
            throw first;
        }
    }

    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        String mapped = REMAPPER.mapRuntimeClassName(name);
        ClassLoader effective = loader != null ? loader : LunarArcReflectionBridge.class.getClassLoader();
        try {
            return Class.forName(mapped, initialize, effective);
        } catch (ClassNotFoundException first) {
            if (!mapped.equals(name)) return Class.forName(name, initialize, effective);
            throw first;
        }
    }

    public static Class<?> loadClass(ClassLoader loader, String name) throws ClassNotFoundException {
        String mapped = REMAPPER.mapRuntimeClassName(name);
        try {
            return loader.loadClass(mapped);
        } catch (ClassNotFoundException first) {
            if (!mapped.equals(name)) return loader.loadClass(name);
            throw first;
        }
    }

    /**
     * Redirect target for a plugin's own {@code Field.getName()} call on a Field it obtained via a raw
     * {@code getDeclaredFields()} scan (a legacy NMS-reflection pattern that bypasses every hooked
     * reflective API in this class) - see LunarArcRemapper.mapRuntimeMemberDisplayName.
     */
    public static String fieldName(Field field) {
        return REMAPPER.mapRuntimeMemberDisplayName(field.getDeclaringClass(), field.getName(), false);
    }

    public static String methodName(Method method) {
        return REMAPPER.mapRuntimeMemberDisplayName(method.getDeclaringClass(), method.getName(), true);
    }

    public static Field getField(Class<?> owner, String name) throws NoSuchFieldException {
        String mapped = REMAPPER.mapRuntimeFieldName(owner, name);
        if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
            io.lunararcdevs.lunararc.common.LunarArcDebug.reflect("getField {}#{} -> {} (from {})",
                    owner.getName(), name, mapped,
                    io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
        }
        try {
            return owner.getField(mapped);
        } catch (NoSuchFieldException first) {
            Field field = findDeclaredField(owner, mapped);
            if (field != null) return field;
            if (!mapped.equals(name)) {
                try {
                    return owner.getField(name);
                } catch (NoSuchFieldException ignored) {
                    field = findDeclaredField(owner, name);
                    if (field != null) return field;
                }
            }
            // An enum constant LunarArc added at runtime has no declared field - a loaded class
            // cannot gain one - so getField has nothing to find. Libraries look constants up this
            // way routinely; Gson does it for every constant of every enum it touches, which is
            // what stopped EssentialsX enabling. See LunarArcDynamicEnumFields.
            Field dynamic = LunarArcDynamicEnumFields.find(owner, mapped);
            if (dynamic == null && !mapped.equals(name)) {
                dynamic = LunarArcDynamicEnumFields.find(owner, name);
            }
            if (dynamic != null) return dynamic;
            throw first;
        }
    }

    public static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        String mapped = REMAPPER.mapRuntimeFieldName(owner, name);
        if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
            io.lunararcdevs.lunararc.common.LunarArcDebug.reflect("getDeclaredField {}#{} -> {} (from {})",
                    owner.getName(), name, mapped,
                    io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
        }
        try {
            return owner.getDeclaredField(mapped);
        } catch (NoSuchFieldException first) {
            if (!mapped.equals(name)) {
                try {
                    return owner.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw first;
        }
    }

    public static Method getMethod(Class<?> owner, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        if (name.equals("getDefaultRegistryAccess") && (parameterTypes == null || parameterTypes.length == 0)
                && net.minecraft.server.MinecraftServer.class.isAssignableFrom(owner)) {
            return org.bukkit.craftbukkit.CraftRegistry.class.getMethod("getMinecraftRegistry");
        }
        String mapped = REMAPPER.mapRuntimeMethodName(owner, name, parameterTypes);
        if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
            io.lunararcdevs.lunararc.common.LunarArcDebug.reflect("getMethod {}#{}({} args) -> {} (from {})",
                    owner.getName(), name, parameterTypes == null ? 0 : parameterTypes.length, mapped,
                    io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
        }
        try {
            return owner.getMethod(mapped, parameterTypes);
        } catch (NoSuchMethodException first) {
            Method method = findDeclaredMethod(owner, mapped, parameterTypes);
            if (method != null) return method;
            if (!mapped.equals(name)) {
                try {
                    return owner.getMethod(name, parameterTypes);
                } catch (NoSuchMethodException ignored) {
                    method = findDeclaredMethod(owner, name, parameterTypes);
                    if (method != null) return method;
                }
            }
            throw first;
        }
    }

    public static Method getDeclaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        String mapped = REMAPPER.mapRuntimeMethodName(owner, name, parameterTypes);
        if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
            io.lunararcdevs.lunararc.common.LunarArcDebug.reflect("getDeclaredMethod {}#{}({} args) -> {} (from {})",
                    owner.getName(), name, parameterTypes == null ? 0 : parameterTypes.length, mapped,
                    io.lunararcdevs.lunararc.common.LunarArcDebug.caller());
        }
        try {
            return owner.getDeclaredMethod(mapped, parameterTypes);
        } catch (NoSuchMethodException first) {
            if (!mapped.equals(name)) {
                try {
                    return owner.getDeclaredMethod(name, parameterTypes);
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw first;
        }
    }

    /**
     * Redirect target for a plugin's own {@code MethodHandles.Lookup.findGetter/findSetter} call -
     * these take the field name as a raw string constant, which the ASM class remapper never touches
     * (it only rewrites symbolic FieldInsnNode/MethodInsnNode references), so this is otherwise
     * invisible to the remapper exactly like the raw getDeclaredFields() scan fieldName() covers.
     * ProtocolLib's IdCodecWrapper uses this pattern to grab a private list field by name.
     */
    public static java.lang.invoke.MethodHandle findGetter(java.lang.invoke.MethodHandles.Lookup lookup,
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        String mapped = REMAPPER.mapRuntimeFieldName(owner, name);
        try {
            return lookup.findGetter(owner, mapped, type);
        } catch (NoSuchFieldException first) {
            if (!mapped.equals(name)) {
                try {
                    return lookup.findGetter(owner, name, type);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw first;
        }
    }

    public static java.lang.invoke.MethodHandle findSetter(java.lang.invoke.MethodHandles.Lookup lookup,
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        String mapped = REMAPPER.mapRuntimeFieldName(owner, name);
        try {
            return lookup.findSetter(owner, mapped, type);
        } catch (NoSuchFieldException first) {
            if (!mapped.equals(name)) {
                try {
                    return lookup.findSetter(owner, name, type);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw first;
        }
    }

    public static java.lang.invoke.MethodHandle findStaticGetter(java.lang.invoke.MethodHandles.Lookup lookup,
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        String mapped = REMAPPER.mapRuntimeFieldName(owner, name);
        try {
            return lookup.findStaticGetter(owner, mapped, type);
        } catch (NoSuchFieldException first) {
            if (!mapped.equals(name)) {
                try {
                    return lookup.findStaticGetter(owner, name, type);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw first;
        }
    }

    public static java.lang.invoke.MethodHandle findStaticSetter(java.lang.invoke.MethodHandles.Lookup lookup,
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        String mapped = REMAPPER.mapRuntimeFieldName(owner, name);
        try {
            return lookup.findStaticSetter(owner, mapped, type);
        } catch (NoSuchFieldException first) {
            if (!mapped.equals(name)) {
                try {
                    return lookup.findStaticSetter(owner, name, type);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw first;
        }
    }

    private static Field findDeclaredField(Class<?> owner, String name) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                try { field.setAccessible(true); } catch (RuntimeException ignored) {}
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findDeclaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                try { method.setAccessible(true); } catch (RuntimeException ignored) {}
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
