package io.lunararcdevs.lunararc.common.server;

import net.minecraft.resources.ResourceLocation;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/** Exposes loader-owned items to EssentialsX as both namespace_path and namespace:path. */
public final class LunarArcEssentialsItemBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc");
    private static final String ESSENTIALS_CLASS = "com.earth2me.essentials.Essentials";
    private static final AliasIndex<Material> MODDED_ITEM_ALIASES = new AliasIndex<>(
            LunarArcDynamicBukkitEnums::materialsById, Material::isItem);

    private LunarArcEssentialsItemBridge() {}

    public static void populateModdedItems(CraftServer craftServer) {
        MODDED_ITEM_ALIASES.refresh();
        Plugin essentials = findEssentials(craftServer);
        if (essentials == null) return;

        try {
            File itemsFile = new File(essentials.getDataFolder(), "items.json");
            if (!itemsFile.isFile()) return;

            List<String> lines = Files.readAllLines(itemsFile.toPath(), StandardCharsets.UTF_8);
            List<String> updated = mergeModdedItems(lines);
            if (updated == null) return;

            Files.write(itemsFile.toPath(), updated, StandardCharsets.UTF_8);
            LOGGER.info("[LunarArc] Added modded items to Essentials' items.json"
                    + " - /give, /item and /i accept <namespace>_<path> and <namespace>:<path>.");
            reloadEssentialsItemDb(essentials);
        } catch (Exception e) {
            LOGGER.warn("[LunarArc] Could not add modded items to Essentials' items.json: {}", e.toString());
        } finally {
            prepareItemCommands(essentials);
        }
    }

    private static Object getItemDb(Plugin essentials) throws ReflectiveOperationException {
        return essentials.getClass().getMethod("getItemDb").invoke(essentials);
    }

    private static void prepareItemCommands(Plugin essentials) {
        if (!essentials.isEnabled()) return;
        try {
            Object itemDb = getItemDb(essentials);
            itemDb.getClass().getMethod("get", String.class, boolean.class).invoke(itemDb, "stone", false);
            itemDb.getClass().getMethod("listNames").invoke(itemDb);
            ClassLoader loader = essentials.getClass().getClassLoader();
            Class.forName("com.earth2me.essentials.commands.Commandgive", false, loader);
            Class.forName("com.earth2me.essentials.commands.Commanditem", false, loader);
        } catch (ReflectiveOperationException error) {
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.CLASSLOAD) {
                io.lunararcdevs.lunararc.common.LunarArcDebug.classload("Essentials item command preparation failed: {}", error.toString());
            }
        }
    }

    /** Resolves Essentials' namespace_path form without scanning every registered item. */
    public static Material resolveAlias(String alias) {
        if (alias == null || alias.isBlank()) return null;
        String normalized = alias.trim().toLowerCase(Locale.ROOT);
        return MODDED_ITEM_ALIASES.get(normalized);
    }

    static final class AliasIndex<T> {
        private final java.util.function.Supplier<Map<ResourceLocation, T>> source;
        private final java.util.function.Predicate<T> isItem;
        private volatile Snapshot<T> snapshot = new Snapshot<>(-1, Map.of());

        AliasIndex(java.util.function.Supplier<Map<ResourceLocation, T>> source, java.util.function.Predicate<T> isItem) {
            this.source = source;
            this.isItem = isItem;
        }

        T get(String alias) {
            long start = System.nanoTime();
            try {
                refresh();
                return snapshot.aliases().get(alias);
            } finally {
                if (io.lunararcdevs.lunararc.common.LunarArcDebug.TIMING) {
                    io.lunararcdevs.lunararc.common.LunarArcDebug.timing(
                            "AliasIndex.get({}) took {}ns", alias, System.nanoTime() - start);
                }
            }
        }

        void refresh() {
            long start = System.nanoTime();
            boolean rebuilt = false;
            try {
                Map<ResourceLocation, T> materials = source.get();
                if (snapshot.materialCount() == materials.size()) return;
                synchronized (this) {
                    int count = materials.size();
                    if (snapshot.materialCount() == count) return;
                    rebuilt = true;
                    Map<String, T> aliases = new java.util.HashMap<>();
                    materials.forEach((id, material) -> {
                        if (id != null && material != null && !"minecraft".equals(id.getNamespace()) && isItem.test(material)) {
                            aliases.putIfAbsent(id.getNamespace() + "_" + id.getPath(), material);
                        }
                    });
                    snapshot = new Snapshot<>(count, Map.copyOf(aliases));
                }
            } finally {
                if (io.lunararcdevs.lunararc.common.LunarArcDebug.TIMING) {
                    io.lunararcdevs.lunararc.common.LunarArcDebug.timing(
                            "AliasIndex.refresh() took {}ns (rebuilt={})", System.nanoTime() - start, rebuilt);
                }
            }
        }

        private record Snapshot<T>(int materialCount, Map<String, T> aliases) {}
    }

    private static Plugin findEssentials(CraftServer craftServer) {
        for (Plugin plugin : craftServer.getPluginManager().getPlugins()) {
            if (plugin.isEnabled() && ESSENTIALS_CLASS.equals(plugin.getClass().getName())) {
                return plugin;
            }
        }
        return null;
    }

    private static List<String> mergeModdedItems(List<String> lines) {
        int closingBrace = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (trimmed.equals("}")) closingBrace = i;
            break;
        }
        if (closingBrace < 0) return null;

        Set<String> existingAliases = indexAliases(lines);
        List<String> additions = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Material> entry : LunarArcDynamicBukkitEnums.materialsById().entrySet()) {
            ResourceLocation id = entry.getKey();
            if ("minecraft".equals(id.getNamespace())) continue;
            Material material = entry.getValue();
            if (material == null || !material.isItem()) continue;

            String alias = (id.getNamespace() + "_" + id.getPath()).toLowerCase(Locale.ROOT);
            if (!existingAliases.add(alias)) continue;

            additions.add("  \"" + alias + "\": {");
            additions.add("    \"material\": \"" + material.name() + "\"");
            additions.add("  },");
        }
        if (additions.isEmpty()) return null;

        int lastContentLine = closingBrace - 1;
        while (lastContentLine >= 0 && lines.get(lastContentLine).trim().isEmpty()) lastContentLine--;
        if (lastContentLine >= 0) {
            String content = lines.get(lastContentLine);
            String trimmed = content.trim();
            if (!trimmed.isEmpty() && !trimmed.endsWith(",") && !trimmed.endsWith("{")) {
                lines.set(lastContentLine, content + ",");
            }
        }

        int lastAddition = additions.size() - 1;
        String lastLine = additions.get(lastAddition);
        additions.set(lastAddition, lastLine.substring(0, lastLine.length() - 1));

        List<String> result = new ArrayList<>(lines.size() + additions.size());
        result.addAll(lines.subList(0, closingBrace));
        result.addAll(additions);
        result.addAll(lines.subList(closingBrace, lines.size()));
        return result;
    }

    private static Set<String> indexAliases(List<String> lines) {
        Set<String> aliases = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 3 || trimmed.charAt(0) != '\"') continue;

            int endQuote = trimmed.indexOf('\"', 1);
            if (endQuote <= 1) continue;

            int colon = endQuote + 1;
            while (colon < trimmed.length() && Character.isWhitespace(trimmed.charAt(colon))) colon++;
            if (colon < trimmed.length() && trimmed.charAt(colon) == ':') {
                aliases.add(trimmed.substring(1, endQuote));
            }
        }
        return aliases;
    }

    private static void reloadEssentialsItemDb(Plugin essentials) {
        try {
            Object itemDb = getItemDb(essentials);
            itemDb.getClass().getMethod("reloadConfig").invoke(itemDb);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[LunarArc] Could not refresh Essentials' item database in place;"
                    + " a restart will pick the new items up: {}", e.toString());
        }
    }
}
