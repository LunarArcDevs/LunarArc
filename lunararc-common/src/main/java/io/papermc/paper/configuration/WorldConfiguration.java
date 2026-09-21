package io.papermc.paper.configuration;

import io.papermc.paper.configuration.type.DurationOrDisabled;

public final class WorldConfiguration {

    public final Lootables lootables = new Lootables();
    public final Entities entities = new Entities();
    public final Anticheat anticheat = new Anticheat();

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
            // Vanilla ores match real Paper's own default hidden-blocks list. Chests (vanilla and
            // lootr's own, since this pack ships it - confirmed present via a real crash report's
            // block_entities listing) are LunarArc's own addition: an x-ray hack that reveals ANY
            // non-air block through walls finds loot chests exactly the same way it finds ore,
            // and real Paper's own default list only ever covered ores, never storage.
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
}
