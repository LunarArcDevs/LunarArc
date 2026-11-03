package io.papermc.paper.configuration;

import io.papermc.paper.configuration.type.DurationOrDisabled;

public final class WorldConfiguration {

    public final Lootables lootables = new Lootables();
    public final Entities entities = new Entities();
    public final Anticheat anticheat = new Anticheat();
    public final Tuning tuning = new Tuning();

    public static WorldConfiguration forLevel(net.minecraft.world.level.Level level) {
        return ((io.lunararcdevs.lunararc.common.bridge.LevelBridge) level).lunararc$getPaperConfiguration();
    }

    public static final class Anticheat {
        public final AntiXray antiXray = new AntiXray();

        public static final class AntiXray {
            public boolean enabled;
            public int engineMode = 1;
            public int maxBlockHeight = 64;
            public int updateRadius = 2;
            public boolean lavaObscures;
            public java.util.List<String> hiddenBlocks = java.util.List.of(
                    "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
                    "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
                    "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
                    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
                    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
                    "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
                    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
                    "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
                    "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore", "minecraft:ancient_debris",
                    "minecraft:chest", "minecraft:trapped_chest",
                    "lootr:lootr_chest", "lootr:lootr_barrel");
        }
    }

    public static final class Entities {
        public final Markers markers = new Markers();

        public static final class Markers {
            public boolean tick = true;
        }

        public static final class Spawning {
            public static final class DuplicateUUID {
                public enum DuplicateUUIDMode { SAFE_REGEN, DELETE, NOTHING, WARN }
            }
        }
    }

    public static final class Misc {
        public enum RedstoneImplementation { VANILLA, EIGENCRAFT, ALTERNATE_CURRENT }
    }

    public static final class Lootables {
        public DurationOrDisabled restrictPlayerRelootTime = new DurationOrDisabled(java.util.Optional.empty());
        public boolean restrictPlayerReloot = true;
        public boolean autoReplenish = false;
        public int maxRefills = -1;
        public io.papermc.paper.configuration.type.Duration refreshMin = io.papermc.paper.configuration.type.Duration.of("12h");
        public io.papermc.paper.configuration.type.Duration refreshMax = io.papermc.paper.configuration.type.Duration.of("2d");
        public boolean resetSeedOnFill = true;
    }

    public static final class Tuning {
        public boolean altItemDespawnEnabled;
        public java.util.Map<String, Integer> altItemDespawn = java.util.Map.of();
        public java.util.Map<String, int[]> despawnRanges = java.util.Map.of();
        public java.util.Map<String, Integer> spawnLimits = java.util.Map.of();
        public int nonPlayerArrowDespawnRate = -1;
        public int creativeArrowDespawnRate = -1;
        public boolean hopperDisableMoveEvent;
        public boolean disableThunder;
        public boolean disableIceAndSnow;
        public boolean disableChestCatDetection;
        public int maxEntityCollisions = 8;
        public boolean onlyPlayersCollide;
        public boolean allowPlayerCrammingDamage;
        public int fallingBlockHeightNerf;
        public int tntEntityHeightNerf;
        public boolean fixItemsMergingThroughWalls;
        public boolean disablePlayerCrits;
        public boolean disableCreeperLingeringEffect;
        public boolean disableSprintInterruptionOnAttack;
        public double voidDamageAmount = 4.0D;
        public double voidDamageMinBuildHeightOffset = -64.0D;
        public int portalSearchRadius = 128;
        public boolean portalSearchVanillaDimensionScaling = true;
        public int maxGrowthHeightCactus = 3;
        public int maxGrowthHeightReeds = 3;
        public boolean allChunksAreSlimeChunks;
        public boolean allowVehicleCollisions = true;
        public int fishingTimeMin = 100;
        public int fishingTimeMax = 600;
        public int snifferHatchTime = 24000;
        public int snifferBoostedHatchTime = 12000;
        public boolean zombiesTargetTurtleEggs = true;
        public boolean nerfPigmenFromNetherPortals;
        public boolean armorStandTick = true;
        public boolean piglinsGuardChests = true;
        public boolean pillagerPatrolsDisabled;
        public double pillagerPatrolSpawnChance = 0.2;
        public int pillagerPatrolSpawnDelay = 12000;
        public int pillagerPatrolStartDay = 5;
        public int phantomMinSeconds = 60;
        public int phantomMaxSeconds = 119;
        public boolean phantomsSkipCreative = true;
        public boolean phantomsOnlyInsomniacs = true;
        public int insomniaStartTicks = 72000;
        public double zombieVillagerInfectionChance = -1;
        public int traderMinuteLength = 1200;
        public int traderDayLength = 24000;
        public int traderChanceMin = 25;
        public int traderChanceMax = 75;
        public int traderChanceIncrement = 25;
        public int mobSpawnerRate = 1;
        public int grassSpreadRate = 1;
        public int dryFarmlandRate = 1;
        public int wetFarmlandRate = 1;
    }
}
