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
    private static byte[] velocitySecret = new byte[0];

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
        if (changed) writeProps(props);

        velocityEnabled = Boolean.parseBoolean(props.getProperty("proxy.velocity.enabled", "false"));
        String secret = props.getProperty("proxy.velocity.secret", "");
        velocitySecret = secret.isEmpty() ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

        LOGGER.debug("[LunarArc] Config loaded (velocity={}).", velocityEnabled);
    }

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

    public static boolean isVelocityEnabled() { return velocityEnabled; }
    public static byte[] getVelocitySecret() { return velocitySecret; }
}
