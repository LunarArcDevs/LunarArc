package io.lunararcdevs.lunararc.common.compat;

/**
 * Shared Paper compatibility policy for the Minecraft 1.21 family.
 */
public final class PaperCompatibility {
    public static final String RUNTIME_MINECRAFT = "1.21.1";
    public static final String RUNTIME_API = "1.21.1";

    private PaperCompatibility() {
    }
    
    public static boolean isSupportedApiVersion(String apiVersion) {
        if (apiVersion == null || apiVersion.isBlank()) {
            return true; // legacy plugin.yml behaviour
        }
        String normalized = apiVersion.trim();
        return normalized.equals("1.21") || normalized.equals("1.21.1");
    }
}
