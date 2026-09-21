package io.lunararcdevs.lunararc.common.mod;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public enum PluginMappingNamespace {
    MOJANG(false),
    SPIGOT(true);

    public static final String MANIFEST_ATTRIBUTE = "paperweight-mappings-namespace";

    private static final String MOJANG_NAMESPACE = "mojang";
    private static final String MOJANG_PLUS_YARN_NAMESPACE = "mojang+yarn";
    private static final String SPIGOT_NAMESPACE = "spigot";

    private final boolean requiresNmsRemap;

    PluginMappingNamespace(boolean requiresNmsRemap) {
        this.requiresNmsRemap = requiresNmsRemap;
    }

    public boolean requiresNmsRemap() {
        return requiresNmsRemap;
    }

    public static PluginMappingNamespace detect(File pluginFile) {
        try (JarFile jar = new JarFile(pluginFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String declared = manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE);
                if (declared != null && !declared.isBlank()) {
                    return parseDeclared(pluginFile, declared);
                }
            }
            return jar.getJarEntry("paper-plugin.yml") != null ? MOJANG : SPIGOT;
        } catch (IOException error) {
            throw new IllegalStateException("Could not inspect plugin mappings namespace for "
                    + pluginFile.getName(), error);
        }
    }

    private static final String CB_VERSION_MARKER = "org/bukkit/craftbukkit/v";
    private static final String NMS_MARKER = "net/minecraft/";
    private static final java.util.regex.Pattern CB_VERSION = java.util.regex.Pattern.compile("v\\d+_\\d+_R\\d+");

    public static String detectOlderNmsTarget(File pluginFile, String currentCraftBukkitVersion) {
        java.util.Set<String> versions = new java.util.LinkedHashSet<>();
        boolean usesNms = false;
        try (JarFile jar = new JarFile(pluginFile)) {
            for (java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                String text;
                try (java.io.InputStream in = jar.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                usesNms |= text.contains(NMS_MARKER);
                for (int at = text.indexOf(CB_VERSION_MARKER); at >= 0; at = text.indexOf(CB_VERSION_MARKER, at + 1)) {
                    java.util.regex.Matcher matcher = CB_VERSION.matcher(text.substring(at + CB_VERSION_MARKER.length() - 1,
                            Math.min(text.length(), at + CB_VERSION_MARKER.length() + 12)));
                    if (!matcher.lookingAt()) continue;
                    if (matcher.group().equals(currentCraftBukkitVersion)) return null;
                    versions.add(matcher.group());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        if (!usesNms || versions.isEmpty()) return null;
        return versions.stream().max(java.util.Comparator.comparingLong(PluginMappingNamespace::versionKey)).orElse(null);
    }

    private static long versionKey(String version) {
        String[] parts = version.substring(1).split("_R?");
        long key = 0;
        for (String part : parts) key = key * 1000 + Integer.parseInt(part);
        return key;
    }

    public static LegacyNmsTranslator legacyTranslatorFor(File pluginFile, String pluginName, String apiVersion) {
        try {
            if (!isOlderThanServerApi(apiVersion)) return null;
            String current = io.lunararcdevs.lunararc.common.server.LunarArcPluginFixManager.getNMSVersion();
            String target = detectOlderNmsTarget(pluginFile, current);
            if (target == null) return null;
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("LunarArc");
            LegacyNmsTranslator translator = LegacyNmsTranslator.forVersion(target);
            if (translator != null) {
                logger.info("{} was built against CraftBukkit {}; translating its net.minecraft references to {}.",
                        pluginName, target, current);
                return translator;
            }
            logger.warn("{} was built against CraftBukkit {} but this server is {}. Its Bukkit API use is fine, but "
                            + "the net.minecraft internals it references changed between those versions and no "
                            + "mapping set is available for {}, so those calls may fail with NoSuchMethodError or "
                            + "NoClassDefFoundError.",
                    pluginName, target, current, target);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isOlderThanServerApi(String apiVersion) {
        if (apiVersion == null || apiVersion.isBlank()) return true;
        String[] parts = apiVersion.trim().split("\\.");
        if (parts.length < 2) return false;
        try {
            return Integer.parseInt(parts[0]) == 1 && Integer.parseInt(parts[1]) < 21;
        } catch (NumberFormatException notNumeric) {
            return false;
        }
    }

    private static PluginMappingNamespace parseDeclared(File pluginFile, String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case MOJANG_NAMESPACE, MOJANG_PLUS_YARN_NAMESPACE -> MOJANG;
            case SPIGOT_NAMESPACE -> SPIGOT;
            default -> throw new IllegalArgumentException("Unsupported " + MANIFEST_ATTRIBUTE
                    + " '" + value + "' in " + pluginFile.getName()
                    + "; LunarArc 1.21.1 supports '" + MOJANG_NAMESPACE + "', '"
                    + MOJANG_PLUS_YARN_NAMESPACE + "' or '" + SPIGOT_NAMESPACE + "'");
        };
    }
}
