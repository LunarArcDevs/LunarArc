package io.papermc.paper.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.configuration.type.Duration;
import io.papermc.paper.configuration.type.DurationOrDisabled;

public final class PaperConfigurations {
    private PaperConfigurations() {}

    public static void createDirectoriesSymlinkAware(Path path) throws IOException {
        if (!Files.isDirectory(path)) Files.createDirectories(path);
    }

    @Deprecated
    public static YamlConfiguration loadLegacyConfigFile(File file) throws Exception {
        return read(file.toPath());
    }

    public static WorldConfiguration loadWorldConfiguration(Path defaultsFile, Path worldDirectory) throws IOException {
        createDirectoriesSymlinkAware(defaultsFile.toAbsolutePath().getParent());
        createDirectoriesSymlinkAware(worldDirectory);
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("_version", 31);
        defaults.set("lootables.auto-replenish", false);
        defaults.set("lootables.restrict-player-reloot", true);
        defaults.set("lootables.restrict-player-reloot-time", "disabled");
        defaults.set("lootables.max-refills", -1);
        defaults.set("lootables.refresh-min", "12h");
        defaults.set("lootables.refresh-max", "2d");
        defaults.set("lootables.reset-seed-on-fill", true);
        defaults.set("entities.markers.tick", true);
        createIfMissing(defaultsFile, defaults.saveToString());
        Path worldFile = worldDirectory.resolve("paper-world.yml");
        createIfMissing(worldFile, "_version: 31\n");
        merge(defaults, read(defaultsFile));
        merge(defaults, read(worldFile));
        try {
            return parseWorldConfiguration(defaults);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid Paper world configuration in " + worldFile + " or " + defaultsFile + ": " + error.getMessage(), error);
        }
    }

    private static void createIfMissing(Path path, String contents) throws IOException {
        try {
            Files.writeString(path, contents, StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException existing) {
            if (!Files.isRegularFile(path)) throw existing;
        }
    }

    private static YamlConfiguration read(Path path) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        if (!Files.exists(path)) return config;
        try {
            config.load(path.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException("Invalid YAML in " + path, error);
        }
        return config;
    }

    private static void merge(YamlConfiguration target, YamlConfiguration source) {
        for (var entry : source.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) target.set(entry.getKey(), entry.getValue());
        }
    }

    public static WorldConfiguration parseWorldConfiguration(YamlConfiguration config) {
        for (String section : java.util.List.of("lootables", "entities", "entities.markers", "anticheat", "anticheat.anti-xray")) {
            Object value = config.get(section);
            if (value != null && !(value instanceof ConfigurationSection)) throw new IllegalArgumentException(section + " must be a mapping");
        }
        WorldConfiguration result = new WorldConfiguration();
        result.lootables.autoReplenish = bool(config, "lootables.auto-replenish", false);
        result.lootables.restrictPlayerReloot = bool(config, "lootables.restrict-player-reloot", true);
        result.lootables.resetSeedOnFill = bool(config, "lootables.reset-seed-on-fill", true);
        result.lootables.maxRefills = integer(config, "lootables.max-refills", -1, -1, Integer.MAX_VALUE);
        result.lootables.refreshMin = duration(config, "lootables.refresh-min", "12h");
        result.lootables.refreshMax = duration(config, "lootables.refresh-max", "2d");
        if (result.lootables.refreshMax.seconds() < result.lootables.refreshMin.seconds()) {
            throw new IllegalArgumentException("lootables.refresh-max must be at least refresh-min");
        }
        Object reloot = config.get("lootables.restrict-player-reloot-time", "disabled");
        result.lootables.restrictPlayerRelootTime = new DurationOrDisabled(
                "disabled".equalsIgnoreCase(String.valueOf(reloot)) ? java.util.Optional.empty()
                        : java.util.Optional.of(duration(config, "lootables.restrict-player-reloot-time", "disabled")));
        result.entities.markers.tick = bool(config, "entities.markers.tick", true);
        var antiXray = result.anticheat.antiXray;
        antiXray.enabled = bool(config, "anticheat.anti-xray.enabled", false);
        antiXray.engineMode = integer(config, "anticheat.anti-xray.engine-mode", 1, 1, 3);
        antiXray.maxBlockHeight = integer(config, "anticheat.anti-xray.max-block-height", 64, Integer.MIN_VALUE, Integer.MAX_VALUE);
        antiXray.updateRadius = integer(config, "anticheat.anti-xray.update-radius", 2, 0, 2);
        antiXray.lavaObscures = bool(config, "anticheat.anti-xray.lava-obscures", false);
        Object hidden = config.get("anticheat.anti-xray.hidden-blocks", java.util.List.of());
        if (!(hidden instanceof java.util.List<?> list) || list.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("anticheat.anti-xray.hidden-blocks must be a list of block names");
        }
        antiXray.hiddenBlocks = java.util.List.copyOf(config.getStringList("anticheat.anti-xray.hidden-blocks"));
        parseTuning(config, result.tuning);
        return result;
    }

    private static final java.util.List<String> MOB_CATEGORIES = java.util.List.of("ambient", "axolotls", "creature", "misc",
            "monster", "underground_water_creature", "water_ambient", "water_creature");

    private static void parseTuning(YamlConfiguration config, WorldConfiguration.Tuning tuning) {
        tuning.altItemDespawnEnabled = lenientBool(config, "entities.spawning.alt-item-despawn-rate.enabled", false);
        ConfigurationSection items = config.getConfigurationSection("entities.spawning.alt-item-despawn-rate.items");
        if (items != null) {
            java.util.Map<String, Integer> map = new java.util.HashMap<>();
            for (String key : items.getKeys(false)) {
                Object value = items.get(key);
                if (value instanceof Number number && number.intValue() > 0) {
                    String id = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
                    map.put(id.toLowerCase(java.util.Locale.ROOT), number.intValue());
                }
            }
            tuning.altItemDespawn = java.util.Map.copyOf(map);
        }
        java.util.Map<String, int[]> ranges = new java.util.HashMap<>();
        java.util.Map<String, Integer> limits = new java.util.HashMap<>();
        for (String category : MOB_CATEGORIES) {
            int soft = lenientInt(config, "entities.spawning.despawn-ranges." + category + ".soft", 0);
            int hard = lenientInt(config, "entities.spawning.despawn-ranges." + category + ".hard", 0);
            if (soft > 0 || hard > 0) ranges.put(category, new int[] {soft, hard});
            int limit = lenientInt(config, "entities.spawning.spawn-limits." + category, -1);
            if (limit >= 0) limits.put(category, limit);
        }
        tuning.despawnRanges = java.util.Map.copyOf(ranges);
        tuning.spawnLimits = java.util.Map.copyOf(limits);
        tuning.nonPlayerArrowDespawnRate = lenientRate(config, "entities.spawning.non-player-arrow-despawn-rate");
        tuning.creativeArrowDespawnRate = lenientRate(config, "entities.spawning.creative-arrow-despawn-rate");
        tuning.hopperDisableMoveEvent = lenientBool(config, "hopper.disable-move-event", false);
        tuning.disableThunder = lenientBool(config, "environment.disable-thunder", false);
        tuning.disableIceAndSnow = lenientBool(config, "environment.disable-ice-and-snow", false);
        tuning.disableChestCatDetection = lenientBool(config, "entities.behavior.disable-chest-cat-detection", false);
        tuning.maxEntityCollisions = lenientInt(config, "collisions.max-entity-collisions", 8);
        tuning.onlyPlayersCollide = lenientBool(config, "collisions.only-players-collide", false);
        tuning.allowPlayerCrammingDamage = lenientBool(config, "collisions.allow-player-cramming-damage", false);
        tuning.fallingBlockHeightNerf = lenientDisabledInt(config, "fixes.falling-block-height-nerf");
        tuning.tntEntityHeightNerf = lenientDisabledInt(config, "fixes.tnt-entity-height-nerf");
        tuning.fixItemsMergingThroughWalls = lenientBool(config, "fixes.fix-items-merging-through-walls", false);
        tuning.disablePlayerCrits = lenientBool(config, "entities.behavior.disable-player-crits", false);
        tuning.disableCreeperLingeringEffect = lenientBool(config, "entities.behavior.disable-creeper-lingering-effect", false);
        tuning.disableSprintInterruptionOnAttack = lenientBool(config, "misc.disable-sprint-interruption-on-attack", false);
        tuning.voidDamageAmount = lenientDouble(config, "environment.void-damage-amount", 4.0D);
        tuning.voidDamageMinBuildHeightOffset = lenientDouble(config, "environment.void-damage-min-build-height-offset", -64.0D);
        tuning.portalSearchRadius = lenientInt(config, "environment.portal-search-radius", 128);
        tuning.portalSearchVanillaDimensionScaling = lenientBool(config, "environment.portal-search-vanilla-dimension-scaling", true);
        tuning.maxGrowthHeightCactus = lenientInt(config, "max-growth-height.cactus", 3);
        tuning.maxGrowthHeightReeds = lenientInt(config, "max-growth-height.reeds", 3);
        tuning.allChunksAreSlimeChunks = lenientBool(config, "entities.spawning.all-chunks-are-slime-chunks", false);
        tuning.allowVehicleCollisions = lenientBool(config, "collisions.allow-vehicle-collisions", true);
        tuning.fishingTimeMin = lenientInt(config, "fishing-time-range.minimum", 100);
        tuning.fishingTimeMax = lenientInt(config, "fishing-time-range.maximum", 600);
        tuning.snifferHatchTime = lenientInt(config, "entities.sniffer.hatch-time", 24000);
        tuning.snifferBoostedHatchTime = lenientInt(config, "entities.sniffer.boosted-hatch-time", 12000);
        tuning.zombiesTargetTurtleEggs = lenientBool(config, "entities.behavior.zombies-target-turtle-eggs", true);
        tuning.armorStandTick = lenientBool(config, "entities.armor-stands.tick", true);
        tuning.piglinsGuardChests = lenientBool(config, "entities.behavior.piglins-guard-chests", true);
        tuning.pillagerPatrolsDisabled = lenientBool(config, "entities.behavior.pillager-patrols.disable", false);
        tuning.pillagerPatrolSpawnChance = lenientDouble(config, "entities.behavior.pillager-patrols.spawn-chance", 0.2);
        tuning.pillagerPatrolSpawnDelay = lenientInt(config, "entities.behavior.pillager-patrols.spawn-delay.ticks", 12000);
        tuning.pillagerPatrolStartDay = lenientInt(config, "entities.behavior.pillager-patrols.start.day", 5);
        tuning.phantomMinSeconds = lenientInt(config, "entities.behavior.phantoms-spawn-attempt-min-seconds", 60);
        tuning.phantomMaxSeconds = lenientInt(config, "entities.behavior.phantoms-spawn-attempt-max-seconds", 119);
        tuning.phantomsSkipCreative = lenientBool(config, "entities.behavior.phantoms-do-not-spawn-on-creative-players", true);
        tuning.phantomsOnlyInsomniacs = lenientBool(config, "entities.behavior.phantoms-only-attack-insomniacs", true);
        tuning.insomniaStartTicks = lenientInt(config, "entities.behavior.player-insomnia-start-ticks", 72000);
        tuning.zombieVillagerInfectionChance = lenientDouble(config, "entities.behavior.zombie-villager-infection-chance", -1);
        tuning.traderMinuteLength = lenientInt(config, "entities.spawning.wandering-trader.spawn-minute-length", 1200);
        tuning.traderDayLength = lenientInt(config, "entities.spawning.wandering-trader.spawn-day-length", 24000);
        tuning.traderChanceMin = lenientInt(config, "entities.spawning.wandering-trader.spawn-chance-min", 25);
        tuning.traderChanceMax = lenientInt(config, "entities.spawning.wandering-trader.spawn-chance-max", 75);
        tuning.traderChanceIncrement = lenientInt(config, "entities.spawning.wandering-trader.spawn-chance-failure-increment", 25);
        tuning.mobSpawnerRate = lenientInt(config, "tick-rates.mob-spawner", 1);
        tuning.grassSpreadRate = lenientInt(config, "tick-rates.grass-spread", 1);
        tuning.dryFarmlandRate = lenientInt(config, "tick-rates.dry-farmland", 1);
        tuning.wetFarmlandRate = lenientInt(config, "tick-rates.wet-farmland", 1);
        tuning.nerfPigmenFromNetherPortals = lenientBool(config, "entities.behavior.nerf-pigmen-from-nether-portals", false);
    }

    /** Paper's "number or disabled" options: a number is used as is, the word disabled (or anything else) means off, stored as 0. */
    private static int lenientDisabledInt(YamlConfiguration config, String key) {
        Object value = config.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean lenientBool(YamlConfiguration config, String key, boolean fallback) {
        Object value = config.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    private static double lenientDouble(YamlConfiguration config, String key, double fallback) {
        Object value = config.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int lenientInt(YamlConfiguration config, String key, int fallback) {
        Object value = config.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static int lenientRate(YamlConfiguration config, String key) {
        Object value = config.get(key);
        if (value instanceof Number number && number.intValue() > 0) return number.intValue();
        return -1;
    }

    private static boolean bool(YamlConfiguration config, String key, boolean fallback) {
        Object value = config.get(key, fallback);
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(key + " must be true or false");
        return result;
    }

    private static int integer(YamlConfiguration config, String key, int fallback, int min, int max) {
        Object value = config.get(key, fallback);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.longValue() || number.longValue() < min || number.longValue() > max) {
            throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max);
        }
        return number.intValue();
    }

    private static Duration duration(YamlConfiguration config, String key, String fallback) {
        String value = String.valueOf(config.get(key, fallback)).trim();
        if (!value.matches("[0-9]+(?:\\.[0-9]+)?\\s*[dhms]?")) throw new IllegalArgumentException(key + " must be a non-negative duration");
        double amount = Double.parseDouble(value.replaceAll("[dhms\\s]", ""));
        char unit = value.charAt(value.length() - 1);
        double seconds = amount * switch (unit) { case 'd' -> 86400; case 'h' -> 3600; case 'm' -> 60; default -> 1; };
        if (!Double.isFinite(seconds) || seconds > Integer.MAX_VALUE) throw new IllegalArgumentException(key + " is too large");
        return Duration.of(value);
    }
}
