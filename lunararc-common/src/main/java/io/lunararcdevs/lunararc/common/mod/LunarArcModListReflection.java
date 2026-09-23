package io.lunararcdevs.lunararc.common.mod;

/** Builds a modId -> version map from a mod list. Forge's IModInfo and NeoForge's IModInfo have the
 *  same shape but live in different packages, so this reads both by reflection instead of each
 *  loader duplicating the same getModId/getVersion dance. */
public final class LunarArcModListReflection {
    private LunarArcModListReflection() {}

    public static java.util.Map<String, String> loadedMods(Iterable<?> modInfos) {
        java.util.Map<String, String> mods = new java.util.HashMap<>();
        for (Object modInfo : modInfos) {
            mods.put(invokeString(modInfo, "getModId"), invokeString(modInfo, "getVersion"));
        }
        return mods;
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
