package io.lunararcdevs.lunararc.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the locale files in {@code locale/}. A value is a string, or an array of strings that are joined with line breaks so
 * multi-line text (the logo) can be read as lines. Colours and styles are written as tags and become terminal escapes when read:
 * {@code <reset> <bold> <italic> <underline> <red> <green> <yellow> <blue> <magenta> <cyan>}. Raw escapes in old files still work.
 */
public class TranslationManager {
    private static final String DEFAULT_LOCALE = "en_gb";
    private static final Map<String, JsonObject> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile String currentLocale = DEFAULT_LOCALE;

    static {
        currentLocale = detectLocale(Locale.getDefault());
        loadLocale(DEFAULT_LOCALE);
        loadLocale(currentLocale);
    }

    public static void setLocale(String locale) {
        currentLocale = normalizeLocale(locale);
        loadLocale(currentLocale);
    }

    public static String detectLocale(Locale locale) {
        if (locale == null) return DEFAULT_LOCALE;
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        if (language.isBlank()) return DEFAULT_LOCALE;
        if (country.isBlank()) {
            country = switch (language) {
                case "en" -> "us";
                case "pt" -> "br";
                case "zh" -> "cn";
                default -> language;
            };
        }
        return language + "_" + country;
    }

    /** Accepts either Minecraft IDs such as {@code en_gb} or country aliases such as {@code gb}. */
    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;
        String normalized = locale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "gb" -> "en_gb";
            case "us" -> "en_us";
            default -> normalized;
        };
    }

    private static void loadLocale(String locale) {
        if (cache.containsKey(locale))
            return;

        try (InputStream in = openLocale(locale)) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                cache.put(locale, json);
            } else if (!locale.equals(DEFAULT_LOCALE)) {
                loadLocale(DEFAULT_LOCALE);
            }
        } catch (Exception e) {
            if (!locale.equals(DEFAULT_LOCALE)) {
                loadLocale(DEFAULT_LOCALE);
            }
        }
    }

    private static InputStream openLocale(String locale) {
        String resource = "locale/" + normalizeLocale(locale) + ".json";
        ClassLoader own = TranslationManager.class.getClassLoader();
        if (own != null) {
            InputStream in = own.getResourceAsStream(resource);
            if (in != null) return in;
        }

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null && context != own) {
            InputStream in = context.getResourceAsStream(resource);
            if (in != null) return in;
        }

        return ClassLoader.getSystemResourceAsStream(resource);
    }

    public static String get(String key, Object... args) {
        String translation = getRaw(currentLocale, key);
        if (translation == null && !currentLocale.equals(DEFAULT_LOCALE)) {
            translation = getRaw(DEFAULT_LOCALE, key);
        }

        if (translation == null)
            return key;

        for (int i = 0; i < args.length; i++) {
            translation = translation.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return translation;
    }

    private static String getRaw(String locale, String key) {
        JsonObject json = cache.get(locale);
        if (json == null) {
            loadLocale(locale);
            json = cache.get(locale);
        }
        if (json != null && json.has(key)) {
            return render(json.get(key));
        }
        return null;
    }

    private static String render(com.google.gson.JsonElement value) {
        String text;
        if (value.isJsonArray()) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement line : value.getAsJsonArray()) lines.add(line.getAsString());
            text = String.join("\n", lines);
        } else {
            text = value.getAsString();
        }
        return applyStyles(text);
    }

    private static String applyStyles(String text) {
        if (text.indexOf('<') < 0) return text;
        for (String[] style : STYLES) {
            text = text.replace(style[0], style[1]);
        }
        return text;
    }

    private static final String[][] STYLES = {
            {"<reset>", "\u001b[0m"}, {"<bold>", "\u001b[1m"}, {"<italic>", "\u001b[3m"}, {"<underline>", "\u001b[4m"},
            {"<red>", "\u001b[31m"}, {"<green>", "\u001b[32m"}, {"<yellow>", "\u001b[33m"}, {"<blue>", "\u001b[34m"},
            {"<magenta>", "\u001b[35m"}, {"<cyan>", "\u001b[36m"}};
}
