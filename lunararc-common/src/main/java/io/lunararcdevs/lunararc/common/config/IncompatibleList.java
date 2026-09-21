package io.lunararcdevs.lunararc.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IncompatibleList {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc");
    private static final List<Entry> PLUGINS = load("lunararc/incompatible/plugins.tsv");
    private static final List<Entry> MODS = load("lunararc/incompatible/mods.tsv");

    private IncompatibleList() {
    }

    public static Entry check(String pluginMain, String pluginVersion) {
        return find(PLUGINS, pluginMain, pluginVersion);
    }

    public static Entry checkMod(String modId, String version) {
        return find(MODS, modId, version);
    }

    public static void screenLoadedMods(Map<String, String> loadedMods) {
        if (MODS.isEmpty() || loadedMods == null || loadedMods.isEmpty()) return;
        List<Detected> detected = new ArrayList<>();
        for (Map.Entry<String, String> mod : loadedMods.entrySet()) {
            Entry listed = checkMod(mod.getKey(), mod.getValue());
            if (listed != null) detected.add(new Detected("mod", mod.getKey(), mod.getKey(), mod.getValue(), listed.reason(), listed.crash()));
        }
        if (!detected.isEmpty()) report(detected);
    }

    /** Reports a detected incompatible plugin; blocks startup unless it resolves to warn-only (see {@link #report}). */
    public static void reportPlugin(String pluginMain, String pluginName, String pluginVersion, Entry listed) {
        String displayName = pluginName == null || pluginName.isBlank() ? pluginMain : pluginName;
        report(List.of(new Detected("plugin", pluginMain, displayName, pluginVersion, listed.reason(), listed.crash())));
    }

    private static void report(List<Detected> detected) {
        boolean crash = detected.stream().anyMatch(item -> item.crash() == null || item.crash());
        for (Detected item : detected) {
            log(crash, "[LunarArc/Incompatible] type={} id={} name=\"{}\"{} reason=\"{}\"",
                    item.type(), item.id(), item.displayName().replace("\"", "'"),
                    item.version() == null ? "" : " version=" + item.version(),
                    item.reason().replace("\"", "'"));
        }
        log(crash, "[LunarArc/{}] count={}", crash ? "IncompatibleFatal" : "IncompatibleWarning", detected.size());
        log(crash, "============================================================");
        log(crash, crash ? "                 LUNARARC STARTUP ERROR" : "                 LUNARARC STARTUP WARNING");
        log(crash, "============================================================");
        log(crash, "Incompatible software detected:");
        for (Detected item : detected) {
            log(crash, "  Type:   {}", Character.toUpperCase(item.type().charAt(0)) + item.type().substring(1));
            if ("plugin".equals(item.type())) {
                log(crash, "  Plugin: {}{}", item.displayName(), item.version() == null ? "" : " " + item.version());
                log(crash, "  Main:   {}", item.id());
            } else {
                log(crash, "  Mod:    {}{}", item.displayName(), item.version() == null ? "" : " " + item.version());
            }
            log(crash, "  Reason: {}", item.reason());
        }
        if (crash) {
            LOGGER.error("Server startup has been stopped. Remove the incompatible item(s) and restart.");
            LOGGER.error("============================================================");
            throw new IncompatibleSoftwareException(crashMessage(detected));
        }
        LOGGER.warn("This item is marked warn-only - continuing startup anyway.");
        LOGGER.warn("============================================================");
    }

    private static void log(boolean asError, String message, Object... args) {
        if (asError) LOGGER.error(message, args);
        else LOGGER.warn(message, args);
    }

    private static String crashMessage(List<Detected> detected) {
        StringBuilder message = new StringBuilder();
        if (detected.size() == 1) {
            Detected item = detected.get(0);
            message.append("LunarArc blocked incompatible ")
                    .append(item.type())
                    .append(": ")
                    .append(item.displayName());
            if (item.version() != null) message.append(" ").append(item.version());
            return message.toString();
        }

        message.append("LunarArc blocked ")
                .append(detected.size())
                .append(" incompatible items:");
        for (Detected item : detected) {
            message.append("\n - ")
                    .append(Character.toUpperCase(item.type().charAt(0)))
                    .append(item.type().substring(1))
                    .append(": ")
                    .append(item.displayName());
            if (item.version() != null) message.append(" ").append(item.version());
        }
        return message.toString();
    }

    private static Entry find(List<Entry> entries, String name, String version) {
        if (name == null) return null;
        for (Entry entry : entries) {
            if (!entry.name().equalsIgnoreCase(name)) continue;
            if (entry.version() == null || entry.version().equals(version)) return entry;
        }
        return null;
    }

    private static InputStream openResource(String resource) {
        ClassLoader[] loaders = {
                IncompatibleList.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            InputStream stream = loader.getResourceAsStream(resource);
            if (stream != null) return stream;
        }
        return null;
    }

    private static List<Entry> load(String resource) {
        List<Entry> entries = new ArrayList<>();
        try (InputStream stream = openResource(resource)) {
            if (stream == null) {
                LOGGER.error("Missing built-in LunarArc incompatible resource: {}", resource);
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\t", 4);
                    if (parts.length != 4) throw new IllegalStateException("Malformed built-in incompatible row in " + resource);
                    Boolean crash = parts[3].isEmpty() ? null : Boolean.valueOf(parts[3]);
                    entries.add(new Entry(parts[0], parts[1].isEmpty() ? null : parts[1], parts[2], crash));
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not read built-in LunarArc incompatible resource " + resource, error);
        }
        return List.copyOf(entries);
    }

    public record Entry(String name, String version, String reason, Boolean crash) {
    }

    private record Detected(String type, String id, String displayName, String version, String reason, Boolean crash) {
    }
}
