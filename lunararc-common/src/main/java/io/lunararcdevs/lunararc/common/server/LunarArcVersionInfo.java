package io.lunararcdevs.lunararc.common.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalInt;
import java.util.Properties;

public final class LunarArcVersionInfo {
    private static final Properties PROPERTIES = new Properties();

    static {
        loadProperties("lunararc-launcher.properties");
        loadProperties("lunararc-build.properties");
    }

    private LunarArcVersionInfo() {
    }

    private static void loadProperties(String resource) {
        try (InputStream in = LunarArcVersionInfo.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                PROPERTIES.load(in);
            }
        } catch (IOException ignored) {
        }
    }

    public static String projectName() {
        return property("projectName", "LunarArc");
    }

    public static String projectVersion() {
        return projectName() + " " + lunarArcVersion() + " (MC: " + minecraftVersion() + ")";
    }


    public static String lunarArcVersion() {
        return property("version", "unknown");
    }

    public static String minecraftVersion() {
        return property("minecraft", "unknown");
    }

    public static String paperApiVersion() {
        return property("paperApi", minecraftVersion() + "-R0.1-SNAPSHOT");
    }

    public static int paperBuild() {
        String value = property("paperBuild", "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String buildChannel() {
        String explicit = PROPERTIES.getProperty("buildChannel");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase(java.util.Locale.ROOT);
        }
        String version = lunarArcVersion().toLowerCase(java.util.Locale.ROOT);
        String base = version.split("\\+", 2)[0];
        if (base.matches(".*-preview(?:[.-].*)?$")) return "preview";
        if (base.matches(".*-(?:pre|prerelease|pre-release|alpha|beta|rc|snapshot)(?:[.-].*)?$")) return "prerelease";
        return "release";
    }

    public static boolean isPreviewBuild() {
        return "preview".equals(buildChannel());
    }

    public static boolean isPreReleaseBuild() {
        return "prerelease".equals(buildChannel()) || "pre-release".equals(buildChannel());
    }

    public static String gitHash() {
        return property("gitHash", "unknown");
    }

    public static String gitBranch() {
        return property("gitBranch", "unknown");
    }

    /** One line identifying exactly which LunarArc this is, for logs and crash reports. */
    public static String brandingLine() {
        return "LunarArc " + lunarArcVersion()
                + " (build " + buildNumber()
                + ", branch " + gitBranch()
                + ", commit " + gitHash()
                + ") for Minecraft " + minecraftVersion();
    }

    public static String buildNumber() {
        return property("buildNumber", "local");
    }

    public static String buildName() {
        return property("buildName", "unknown");
    }

    public static OptionalInt dataVersion() {
        String value = property("dataVersion", "");
        if (value.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static String property(String key, String fallback) {
        return PROPERTIES.getProperty(key, fallback);
    }
}
