package io.lunararcdevs.lunararc.common.server;

import com.destroystokyo.paper.util.VersionFetcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.lunararcdevs.lunararc.i18n.TranslationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class LunarArcVersionFetcher implements VersionFetcher {

    private static final String API_URL = "https://api.github.com/repos/LunarArcDevs/LunarArc/releases";
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 3000;

    public record Release(String version, String downloadUrl) {
    }

    @Override
    public long getCacheTime() {
        return TimeUnit.HOURS.toMillis(1);
    }

    @Override
    public Component getVersionMessage(String serverVersion) {
        try {
            String currentVersion = LunarArcVersionInfo.lunarArcVersion();
            Optional<Release> latest = fetchLatestRelease();
            if (latest.isEmpty()) {
                return latestVersionMessage(currentVersion);
            }

            Release release = latest.get();
            if (isSameVersion(currentVersion, release.version())) {
                return latestVersionMessage(currentVersion);
            }

            return Component.text(TranslationManager.get(
                    "version.update.available", release.version(), currentVersion), NamedTextColor.YELLOW)
                    .append(Component.newline())
                    .append(Component.text(TranslationManager.get(
                            "version.update.download", release.downloadUrl()), NamedTextColor.YELLOW));
        } catch (Exception e) {
            return Component.text(TranslationManager.get("version.check_failed", e.getMessage()), NamedTextColor.RED);
        }
    }

    public static Optional<Release> fetchLatestRelease() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "LunarArc-VersionCommand");
            connection.setRequestProperty("Accept", "application/vnd.github+json");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }

            try (InputStream input = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonArray releases = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement element : releases) {
                    JsonObject release = element.getAsJsonObject();
                    if (release.has("draft") && release.get("draft").getAsBoolean()) continue;

                    String tagName = stringValue(release, "tag_name");
                    String htmlUrl = stringValue(release, "html_url");
                    if (!tagName.isBlank() && !htmlUrl.isBlank()) {
                        return Optional.of(new Release(tagName, htmlUrl));
                    }
                }
            }
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    public static boolean isSameVersion(String serverVersion, String tagName) {
        String current = normalize(serverVersion);
        String tag = normalize(tagName);
        return !current.isBlank() && (current.equals(tag) || tag.endsWith(current) || current.endsWith(tag));
    }

    private static Component latestVersionMessage(String version) {
        Component message = Component.text(TranslationManager.get("version.latest", version), NamedTextColor.GREEN);
        String previous = LunarArcWorldVersionStamp.previousVersion();
        if (!previous.isBlank() && !previous.equals(LunarArcVersionInfo.lunarArcVersion())) {
            message = message.append(Component.newline())
                    .append(Component.text(TranslationManager.get("version.previous", previous), NamedTextColor.GRAY));
        }
        return message;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
