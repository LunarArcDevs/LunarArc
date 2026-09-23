package io.lunararcdevs.lunararc.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.lunararcdevs.lunararc.i18n.TranslationManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class UpdateChecker {
    private static final String REPO = "LunarArcDevs/LunarArc";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases";
    private static final String NO_UPDATE_MESSAGE = "No new updates available, You're up to date";
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 3000;

    private UpdateChecker() {
    }

    public static String LATEST_VERSION = null;
    public static String UPDATE_URL = null;
    private static final Object LOCK = new Object();
    private static final java.util.List<String> MESSAGES = new java.util.ArrayList<>();

    private static void say(String message) {
        synchronized (LOCK) {
            MESSAGES.add(message);
        }
    }

    public static Handle begin(String currentVersion, String buildName) {
        if (!updatesEnabled()) return new Handle(null, null);
        if (isLocalBuild(currentVersion)) {
            System.out.println(TranslationManager.get("update.local_build", currentVersion));
            return new Handle(null, null);
        }
        Thread thread = new Thread(() -> probe(currentVersion, buildName), "LunarArc-update-check");
        thread.setDaemon(true);
        thread.start();
        return new Handle(thread, currentVersion);
    }

    private static boolean isLocalBuild(String currentVersion) {
        return currentVersion != null && currentVersion.endsWith("+local");
    }

    /** A running check, and the means to report whatever it found. */
    public static final class Handle {
        private final Thread thread;
        private final String currentVersion;

        private Handle(Thread thread, String currentVersion) {
            this.thread = thread;
            this.currentVersion = currentVersion;
        }

        public void finish() {
            if (thread == null) return;
            try {
                thread.join(1000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (thread.isAlive()) return;

            synchronized (LOCK) {
                for (String message : MESSAGES) {
                    System.out.println(message);
                }
                MESSAGES.clear();
                if (LATEST_VERSION != null && UPDATE_URL != null) {
                    saveUpdateInfo(currentVersion, LATEST_VERSION, UPDATE_URL);
                }
            }
        }
    }

    /** Reads, and if absent creates, the enable_updates setting. Callers' thread, never the probe's. */
    private static boolean updatesEnabled() {
        Path configPath = Paths.get("lunararc.conf");
        Properties props = new Properties();
        boolean enableUpdates = true;

        try {
            if (Files.exists(configPath)) {
                try (java.io.InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                    if (!props.containsKey("enable_updates")) {
                        props.setProperty("enable_updates", "true");
                        try (java.io.OutputStream out = Files.newOutputStream(configPath)) {
                            props.store(out, "LunarArc Server Configuration");
                        }
                    }
                    enableUpdates = Boolean.parseBoolean(props.getProperty("enable_updates", "true"));
                }
            } else {
                props.setProperty("enable_updates", "true");
                try (java.io.OutputStream out = Files.newOutputStream(configPath)) {
                    props.store(out, "LunarArc Server Configuration");
                }
            }
        } catch (Exception ignored) {
        }

        return enableUpdates;
    }

    private static void probe(String currentVersion, String buildName) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "LunarArc-Launcher");
            connection.setRequestProperty("Accept", "application/vnd.github+json");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return;
            }

            JsonArray releases;
            try (InputStream input = connection.getInputStream();
                    InputStreamReader reader = new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
                releases = JsonParser.parseReader(reader).getAsJsonArray();
            }

            boolean foundMatch = false;
            for (JsonElement element : releases) {
                JsonObject release = element.getAsJsonObject();
                if (release.has("draft") && release.get("draft").getAsBoolean()) {
                    continue;
                }

                String tagName = stringValue(release, "tag_name");
                String name = stringValue(release, "name");
                String targetCommitish = stringValue(release, "target_commitish");
                String htmlUrl = stringValue(release, "html_url");
                if (tagName.isBlank() || htmlUrl.isBlank()) {
                    continue;
                }

                String normalizedBuildName = normalize(buildName);
                String normalizedTagName = normalize(tagName);
                String normalizedReleaseName = normalize(name);
                String normalizedTarget = normalize(targetCommitish);

                boolean isMatch = normalizedBuildName.isBlank()
                        || normalizedTagName.contains(normalizedBuildName)
                        || normalizedReleaseName.contains(normalizedBuildName)
                        || normalizedTarget.contains(normalizedBuildName);

                if (!isMatch) {
                    continue;
                }

                foundMatch = true;
                if (!sameVersion(currentVersion, tagName, name, buildName)) {
                    LATEST_VERSION = tagName;
                    UPDATE_URL = htmlUrl;
                    if (!alreadyReported(currentVersion, tagName)) {
                        say(TranslationManager.get("update.available", buildName, tagName, currentVersion));
                        say(TranslationManager.get("update.download", htmlUrl));
                    }
                } else {
                    LATEST_VERSION = null;
                    UPDATE_URL = null;
                    say(NO_UPDATE_MESSAGE);
                }
                break;
            }

            if (!foundMatch) {
                say(NO_UPDATE_MESSAGE);
            }
        } catch (Exception ignored) {

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static boolean sameVersion(String currentVersion, String tagName, String releaseName, String buildName) {
        String current = normalize(currentVersion);
        if (current.isBlank()) {
            return false;
        }

        String tag = stripBuildName(normalize(tagName), buildName);
        String name = stripBuildName(normalize(releaseName), buildName);
        return current.equals(tag) || current.equals(name) || tag.endsWith(current) || name.endsWith(current);
    }

    private static String stripBuildName(String value, String buildName) {
        String build = normalize(buildName);
        return build.isBlank() ? value : value.replace(build, "");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    
    private static boolean alreadyReported(String currentVersion, String tagName) {
        try {
            Path configPath = Paths.get("lunararc.conf");
            if (!Files.exists(configPath)) return false;
            Properties props = new Properties();
            try (java.io.InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            }
            return currentVersion.equals(props.getProperty("update.current"))
                    && tagName.equals(props.getProperty("update.latest"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void saveUpdateInfo(String current, String latest, String url) {
        try {
            Path configPath = Paths.get("lunararc.conf");
            Properties props = new Properties();

            if (Files.exists(configPath)) {
                try (java.io.InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
            }

            props.setProperty("update.current", current);
            props.setProperty("update.latest", latest);
            props.setProperty("update.url", url);

            try (java.io.OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "LunarArc Server Configuration");
            }
        } catch (Exception ignored) {
        }
    }
}
