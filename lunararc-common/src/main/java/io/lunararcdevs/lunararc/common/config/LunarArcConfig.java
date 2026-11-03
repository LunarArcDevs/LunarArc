package io.lunararcdevs.lunararc.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class LunarArcConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc");
    private static final Path CONFIG_PATH = Paths.get("lunararc.conf");

    private static boolean velocityEnabled = false;
    private static boolean pluginDataReloadOnStartup = false;
    private static byte[] velocitySecret = new byte[0];

    private static boolean activationRangeEnabled = false;
    private static int activationMonsters = 32;
    private static int activationAnimals = 32;
    private static int activationVillagers = 32;
    private static int activationMisc = 16;

    public static void load() {
        Properties props = readProps();

        boolean changed = false;
        if (!props.containsKey("proxy.velocity.enabled")) {
            props.setProperty("proxy.velocity.enabled", "false");
            changed = true;
        }
        if (!props.containsKey("proxy.velocity.secret")) {
            props.setProperty("proxy.velocity.secret", "");
            changed = true;
        }
        if (!props.containsKey("plugin-data.reload-on-startup")) {
            props.setProperty("plugin-data.reload-on-startup", "false");
            changed = true;
        }
        String[][] activation = {
                {"experimental.activation-range.enabled", "false"}, {"experimental.activation-range.monsters", "32"}, {"experimental.activation-range.animals", "32"},
                {"experimental.activation-range.villagers", "32"}, {"experimental.activation-range.misc", "16"}};
        for (String[] entry : activation) {
            if (!props.containsKey(entry[0])) {
                props.setProperty(entry[0], entry[1]);
                changed = true;
            }
        }
        if (changed) writeProps(props);
        activationRangeEnabled = Boolean.parseBoolean(props.getProperty("experimental.activation-range.enabled", "false"));
        activationMonsters = intProperty(props, "experimental.activation-range.monsters", 32);
        activationAnimals = intProperty(props, "experimental.activation-range.animals", 32);
        activationVillagers = intProperty(props, "experimental.activation-range.villagers", 32);
        activationMisc = intProperty(props, "experimental.activation-range.misc", 16);
        if (activationRangeEnabled) {
            LOGGER.warn("[LunarArc] experimental.activation-range is enabled. It is experimental: mobs with no player nearby skip most ticks, which can affect mob farms and redstone-driven mobs.");
        }
        pluginDataReloadOnStartup = Boolean.parseBoolean(props.getProperty("plugin-data.reload-on-startup", "false"));

        velocityEnabled = Boolean.parseBoolean(props.getProperty("proxy.velocity.enabled", "false"));
        String secret = props.getProperty("proxy.velocity.secret", "");
        velocitySecret = secret.isEmpty() ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

        LOGGER.debug("[LunarArc] Config loaded (velocity={}).", velocityEnabled);
    }

    private static int intProperty(Properties props, String key, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim()));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    /** EXPERIMENTAL: whether mobs with no player nearby skip most of their ticking. Off by default (experimental.activation-range.enabled). */
    public static boolean isActivationRangeEnabled() { return activationRangeEnabled; }
    public static int getActivationRangeMonsters() { return activationMonsters; }
    public static int getActivationRangeAnimals() { return activationAnimals; }
    public static int getActivationRangeVillagers() { return activationVillagers; }
    public static int getActivationRangeMisc() { return activationMisc; }

    static Properties readProps() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (Exception e) {
                LOGGER.error("[LunarArc] Failed to read lunararc.conf — using defaults.", e);
            }
        }
        return props;
    }

    static void writeProps(Properties props) {
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "LunarArc Server Configuration");
        } catch (IOException e) {
            LOGGER.error("[LunarArc] Could not write lunararc.conf", e);
        }
    }

    /**
     * Whether plugin-supplied datapacks and tag handlers are applied with a data reload at startup. Off by default: a reload makes some
     * mods (Curios, for one, reads its slot data only once at boot) lose data they loaded earlier. Without it they apply on the next /reload.
     */
    public static boolean isPluginDataReloadOnStartup() {
        return pluginDataReloadOnStartup;
    }

    public static boolean isVelocityEnabled() {
        return velocityEnabled || io.lunararcdevs.lunararc.common.server.LunarArcServerSettings.paperGlobalBoolean("proxies.velocity.enabled", false);
    }
    public static byte[] getVelocitySecret() {
        String paper = io.lunararcdevs.lunararc.common.server.LunarArcServerSettings.paperGlobalString("proxies.velocity.secret", "");
        return paper.isEmpty() ? velocitySecret : paper.getBytes(StandardCharsets.UTF_8);
    }
}
