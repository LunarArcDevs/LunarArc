package org.bukkit.craftbukkit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.help.HelpMap;
import org.bukkit.inventory.*;
import org.bukkit.loot.LootTable;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.StructureManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import io.lunararcdevs.lunararc.common.server.LunarArcLogger;
import io.papermc.paper.configuration.FeatureFlagConfig;

public class CraftServer implements Server {
    private volatile org.bukkit.craftbukkit.util.CraftIconCache serverIcon;
    private final org.bukkit.potion.PotionBrewer potionBrewer;
    private final MinecraftServer console;
    private final PlayerList playerList;
    private final Logger logger = LunarArcLogger.getLogger("Minecraft");
    private final SimpleCommandMap commandMap = new org.bukkit.craftbukkit.command.CraftCommandMap(this);
    private final PluginManager pluginManager;
    private final SimplePluginManager simplePluginManager;
    private final ServicesManager servicesManager = new SimpleServicesManager();
    private final Map<NamespacedKey, KeyedBossBar> bossBars = new LinkedHashMap<>();

    private final Map<Integer, org.bukkit.craftbukkit.map.CraftMapView> mapViews = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger nextMapId = new java.util.concurrent.atomic.AtomicInteger();
    private final org.bukkit.metadata.MetadataStore<org.bukkit.entity.Entity> entityMetadata =
            new org.bukkit.craftbukkit.metadata.CraftMetadataStore<>();

    public org.bukkit.metadata.MetadataStore<org.bukkit.entity.Entity> getEntityMetadata() {
        return entityMetadata;
    }

    private final YamlConfiguration bukkitConfig = new YamlConfiguration();
    private final YamlConfiguration spigotConfig = new YamlConfiguration();
    private final YamlConfiguration commandsConfig = new YamlConfiguration();
    private final YamlConfiguration paperGlobalConfig = new YamlConfiguration();
    private final YamlConfiguration paperWorldConfig = new YamlConfiguration();

    private final Spigot spigot = new Server.Spigot() {
        @Override
        public YamlConfiguration getConfig() {
            return spigotConfig;
        }

        @Override
        public YamlConfiguration getSpigotConfig() {
            return spigotConfig;
        }

        @Override
        public YamlConfiguration getBukkitConfig() {
            return bukkitConfig;
        }

        @Override
        public YamlConfiguration getPaperConfig() {
            return paperGlobalConfig;
        }
    };
    private final StandardMessenger messenger = new StandardMessenger();
    private final org.bukkit.craftbukkit.scheduler.CraftScheduler scheduler = new org.bukkit.craftbukkit.scheduler.CraftScheduler();
    private final org.bukkit.craftbukkit.scheduler.CraftPaperSchedulers paperSchedulers =
            new org.bukkit.craftbukkit.scheduler.CraftPaperSchedulers(scheduler);
    private final io.papermc.paper.datapack.PaperDatapackManager datapackManager;
    private final CraftServerTickManager serverTickManager;
    private final org.bukkit.craftbukkit.packs.CraftDataPackManager legacyDataPackManager;
    private final org.bukkit.craftbukkit.structure.CraftStructureManager structureManager;
    private final CraftServerLinks serverLinks;
    private final org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager scoreboardManager;
    private final com.destroystokyo.paper.entity.ai.MobGoals mobGoals = new com.destroystokyo.paper.entity.ai.PaperMobGoals();
    private final Map<String, org.bukkit.help.HelpTopic> helpTopics = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Class<?>, org.bukkit.help.HelpTopicFactory<?>> helpTopicFactories = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> ignoredHelpPlugins = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final HelpMap helpMap = new HelpMap() {
        @Override
        public @Nullable org.bukkit.help.HelpTopic getHelpTopic(@NotNull String cmdName) {
            Objects.requireNonNull(cmdName, "cmdName");
            org.bukkit.help.HelpTopic topic = helpTopics.get(cmdName);
            if (topic != null) return topic;
            String normalized = cmdName.startsWith("/") ? cmdName : "/" + cmdName;
            topic = helpTopics.get(normalized);
            if (topic != null) return topic;
            return helpTopics.get(normalized.toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public @NotNull Collection<org.bukkit.help.HelpTopic> getHelpTopics() {
            return Collections.unmodifiableCollection(new java.util.ArrayList<>(helpTopics.values()));
        }

        @Override
        public void addTopic(@NotNull org.bukkit.help.HelpTopic topic) {
            Objects.requireNonNull(topic, "topic");
            String name = topic.getName();
            if (name == null || name.isBlank()) return;
            helpTopics.put(name, topic);
            helpTopics.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), topic);
            if (name.startsWith("/")) helpTopics.putIfAbsent(name.substring(1).toLowerCase(java.util.Locale.ROOT), topic);
        }

        @Override
        public void clear() {
            helpTopics.clear();
            helpTopicFactories.clear();
        }

        @Override
        public void registerHelpTopicFactory(@NotNull Class<?> commandClass,
                @NotNull org.bukkit.help.HelpTopicFactory<?> factory) {
            helpTopicFactories.put(Objects.requireNonNull(commandClass, "commandClass"),
                    Objects.requireNonNull(factory, "factory"));
        }

        @Override
        public @NotNull List<String> getIgnoredPlugins() {
            return Collections.unmodifiableList(new java.util.ArrayList<>(ignoredHelpPlugins));
        }
    };

    private final CraftConsoleCommandSender consoleSender;

    private void loadBukkitSideConfiguration() {
        loadYamlIfPresent(bukkitConfig, new File("bukkit.yml"));
        loadYamlIfPresent(spigotConfig, new File("spigot.yml"));
        loadYamlIfPresent(commandsConfig, new File("commands.yml"));
        loadYamlIfPresent(paperGlobalConfig, new File("config/paper-global.yml"));
        loadYamlIfPresent(paperWorldConfig, new File("config/paper-world-defaults.yml"));
    }

    private void loadYamlIfPresent(YamlConfiguration config, File file) {
        try {
            if (file.isFile()) config.load(file);
        } catch (Exception ex) {
            logger.log(java.util.logging.Level.WARNING, "Unable to load " + file.getPath(), ex);
        }
    }

    public CraftServer(MinecraftServer console, PlayerList playerList) {
        this.console = console;
        this.potionBrewer = new org.bukkit.craftbukkit.potion.CraftPotionBrewer(console);
        this.playerList = playerList;
        loadBukkitSideConfiguration();

        this.simplePluginManager = new SimplePluginManager(this, commandMap);
        this.pluginManager = new io.papermc.paper.plugin.manager.PaperPluginManagerImpl(this, commandMap, simplePluginManager);
        this.simplePluginManager.setInternalManager(this.pluginManager);

        Bukkit.setServer(this);

        this.consoleSender = new CraftConsoleCommandSender(console);

        io.lunararcdevs.lunararc.common.mod.util.log.LunarArcConsole.success(logger, "CraftServer initialized: "
                + io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.projectName() + " version " + getVersion()
                + " (Bukkit: " + getBukkitVersion() + ")");

        this.simplePluginManager.registerInterface(io.lunararcdevs.lunararc.common.server.LunarArcPluginLoader.class);
        this.datapackManager = new io.papermc.paper.datapack.PaperDatapackManager(console);
        this.legacyDataPackManager = new org.bukkit.craftbukkit.packs.CraftDataPackManager(console);
        this.serverTickManager = new CraftServerTickManager(console);
        this.structureManager = new org.bukkit.craftbukkit.structure.CraftStructureManager(console);
        this.serverLinks = console instanceof net.minecraft.server.dedicated.DedicatedServer dedicated
                ? new CraftServerLinks(dedicated)
                : new CraftServerLinks(new net.minecraft.server.ServerLinks(java.util.List.of()));
        this.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(
                console, console.getScoreboard());

        this.loadConfigurations();

        org.bukkit.command.Command existingVersion = commandMap.getCommand("version");
        if (existingVersion != null) {
            existingVersion.unregister(commandMap);
            commandMap.getKnownCommands().entrySet().removeIf(e -> e.getValue() == existingVersion);
        }
        commandMap.register("bukkit", new io.lunararcdevs.lunararc.common.server.LunarArcVersionCommand("version"));

        org.bukkit.command.Command existingPlugins = commandMap.getCommand("plugins");
        if (existingPlugins != null) {
            existingPlugins.unregister(commandMap);
            commandMap.getKnownCommands().entrySet().removeIf(e -> e.getValue() == existingPlugins);
        }
        io.papermc.paper.command.LunarArcPaperBuiltinCommands.register(commandMap);
        commandMap.register("bukkit", new org.bukkit.command.defaults.ReloadCommand("reload"));
    }

    private void loadConfigurations() {
        File bukkitFile = new File("bukkit.yml");
        File spigotFile = new File("spigot.yml");
        File commandsFile = new File("commands.yml");
        File paperDir = new File("config");
        if (!paperDir.exists())
            paperDir.mkdirs();
        File paperGlobalFile = new File(paperDir, "paper-global.yml");
        File paperWorldFile = new File(paperDir, "paper-world-defaults.yml");

        try {
            if (bukkitFile.exists())
                bukkitConfig.load(bukkitFile);
            if (spigotFile.exists())
                spigotConfig.load(spigotFile);
            if (commandsFile.exists())
                commandsConfig.load(commandsFile);
            if (paperGlobalFile.exists())
                paperGlobalConfig.load(paperGlobalFile);
            if (paperWorldFile.exists())
                paperWorldConfig.load(paperWorldFile);

            bukkitConfig.addDefault("settings.allow-end", true);
            bukkitConfig.addDefault("settings.warn-on-overload", true);
            bukkitConfig.addDefault("settings.permissions-file", "permissions.yml");
            bukkitConfig.addDefault("settings.update-checker", true);
            bukkitConfig.addDefault("settings.plugin-profiling", false);
            bukkitConfig.addDefault("settings.connection-throttle", 4000);
            bukkitConfig.addDefault("settings.query-plugins", true);
            bukkitConfig.addDefault("settings.deprecated-verbose", "default");
            bukkitConfig.addDefault("settings.shutdown-message", "Server closed");
            bukkitConfig.addDefault("spawn-limits.monsters", 70);
            bukkitConfig.addDefault("spawn-limits.animals", 10);
            bukkitConfig.addDefault("spawn-limits.water-animals", 5);
            bukkitConfig.addDefault("spawn-limits.water-ambient", 20);
            bukkitConfig.addDefault("spawn-limits.water-underground-creature", 5);
            bukkitConfig.addDefault("spawn-limits.axolotls", 5);
            bukkitConfig.addDefault("spawn-limits.ambient", 15);
            bukkitConfig.addDefault("chunk-gc.period-in-ticks", 600);
            bukkitConfig.addDefault("ticks-per.animal-spawns", 400);
            bukkitConfig.addDefault("ticks-per.monster-spawns", 1);
            bukkitConfig.addDefault("ticks-per.water-spawns", 1);
            bukkitConfig.addDefault("ticks-per.water-ambient-spawns", 1);
            bukkitConfig.addDefault("ticks-per.water-underground-creature-spawns", 1);
            bukkitConfig.addDefault("ticks-per.axolotl-spawns", 1);
            bukkitConfig.addDefault("ticks-per.ambient-spawns", 1);
            bukkitConfig.addDefault("aliases", "noworld");
            bukkitConfig.options().copyDefaults(true);
            bukkitConfig.save(bukkitFile);

            spigotConfig.addDefault("settings.debug", false);
            spigotConfig.addDefault("settings.bungeecord", false);
            spigotConfig.addDefault("settings.sample-count", 12);
            spigotConfig.addDefault("settings.player-shuffle", 0);
            spigotConfig.addDefault("settings.user-cache-size", 1000);
            spigotConfig.addDefault("settings.save-user-cache-on-stop-only", false);
            spigotConfig.addDefault("settings.moved-wrongly-threshold", 0.0625);
            spigotConfig.addDefault("settings.moved-too-quickly-multiplier", 10.0);
            spigotConfig.addDefault("settings.timeout-time", 60);
            spigotConfig.addDefault("settings.restart-on-crash", true);
            spigotConfig.addDefault("settings.restart-script", "./start.sh");
            spigotConfig.addDefault("settings.netty-threads", 4);
            spigotConfig.addDefault("settings.attribute.maxHealth.max", 2048.0);
            spigotConfig.addDefault("settings.attribute.movementSpeed.max", 2048.0);
            spigotConfig.addDefault("settings.attribute.attackDamage.max", 2048.0);
            spigotConfig.addDefault("settings.log-villager-deaths", true);
            spigotConfig.addDefault("settings.log-named-deaths", true);
            spigotConfig.addDefault("messages.whitelist", "You are not whitelisted on this server!");
            spigotConfig.addDefault("messages.unknown-command", "Unknown command. Type \"/help\" for help.");
            spigotConfig.addDefault("messages.server-full", "The server is full!");
            spigotConfig.addDefault("messages.outdated-client", "Outdated client! Please use {0}");
            spigotConfig.addDefault("messages.outdated-server", "Outdated server! I'm still on {0}");
            spigotConfig.addDefault("messages.restart", "Server is restarting");
            spigotConfig.addDefault("advancements.disable-saving", false);
            spigotConfig.addDefault("advancements.disabled", Collections.singletonList("minecraft:story/disabled"));
            spigotConfig.options().copyDefaults(true);
            spigotConfig.save(spigotFile);

            org.spigotmc.SpigotConfig.syncFrom(spigotConfig);

            commandsConfig.addDefault("command-block-overrides", Collections.emptyList());
            commandsConfig.addDefault("aliases.icanhasbukkit", Collections.singletonList("version"));
            commandsConfig.options().copyDefaults(true);
            commandsConfig.save(commandsFile);

            paperGlobalConfig.addDefault("proxies.bungee-cord.enabled", false);
            paperGlobalConfig.addDefault("proxies.velocity.enabled", false);
            paperGlobalConfig.addDefault("proxies.velocity.online-mode", false);
            paperGlobalConfig.addDefault("proxies.velocity.secret", "");
            paperGlobalConfig.addDefault("settings.chunk-loading.min-loadable-tick-rate", 1);
            paperGlobalConfig.addDefault("settings.incoming-packet-spam-threshold", 300);
            paperGlobalConfig.options().copyDefaults(true);
            paperGlobalConfig.save(paperGlobalFile);

            paperWorldConfig.addDefault("anticheat.obfuscation.items.hide-itemmeta", false);
            paperWorldConfig.addDefault("anticheat.obfuscation.items.hide-durability", false);
            paperWorldConfig.addDefault("anticheat.obfuscation.items.hide-itemmeta-with-visual-effects", false);
            paperWorldConfig.addDefault("anticheat.anti-xray.enabled", false);
            paperWorldConfig.addDefault("anticheat.anti-xray.engine-mode", 1);
            paperWorldConfig.addDefault("anticheat.anti-xray.max-block-height", 64);
            paperWorldConfig.addDefault("anticheat.anti-xray.update-radius", 2);
            paperWorldConfig.addDefault("anticheat.anti-xray.lava-obscures", false);
            paperWorldConfig.addDefault("anticheat.anti-xray.use-permission", false);
            paperWorldConfig.addDefault("anticheat.anti-xray.hidden-blocks", new java.util.ArrayList<>(java.util.List.of(
                    "copper_ore", "deepslate_copper_ore", "raw_copper_block",
                    "gold_ore", "deepslate_gold_ore",
                    "iron_ore", "deepslate_iron_ore", "raw_iron_block",
                    "coal_ore", "deepslate_coal_ore",
                    "lapis_ore", "deepslate_lapis_ore",
                    "mossy_cobblestone", "obsidian", "chest",
                    "diamond_ore", "deepslate_diamond_ore",
                    "redstone_ore", "deepslate_redstone_ore",
                    "clay", "emerald_ore", "deepslate_emerald_ore", "ender_chest")));
            paperWorldConfig.addDefault("anticheat.anti-xray.replacement-blocks",
                    new java.util.ArrayList<>(java.util.List.of("stone", "oak_planks", "deepslate")));
            paperWorldConfig.addDefault("entities.spawning.despawn-ranges.ambient.hard", 128);
            paperWorldConfig.addDefault("entities.spawning.despawn-ranges.ambient.soft", 32);
            paperWorldConfig.options().copyDefaults(true);
            paperWorldConfig.save(paperWorldFile);

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to load/generate server configuration files", e);
        }
    }

    // CraftBukkit declares this as DedicatedServer, and plugins are compiled against that
    // descriptor. Java resolves a method by name *and* descriptor, so returning the supertype here
    // is not a widening - it is a different method that plugin bytecode cannot find: WorldEdit's
    // PaperweightAdapter died on
    // NoSuchMethodError: CraftServer.getServer()Lnet/minecraft/server/dedicated/DedicatedServer;
    // even though a getServer() was right here. console is always the dedicated subclass on this
    // server-only runtime, which is what CraftBukkit assumes.
    public net.minecraft.server.dedicated.DedicatedServer getServer() {
        return (net.minecraft.server.dedicated.DedicatedServer) console;
    }

    public net.minecraft.server.dedicated.DedicatedPlayerList getHandle() {
        return (net.minecraft.server.dedicated.DedicatedPlayerList) playerList;
    }

    @Override
    public @NotNull String getName() {
        // Must agree with ServerBuildInfo's brand - EssentialsX checks this string directly and
        // warned about an unsupported server on a live boot when it didn't match.
        return "Paper";
    }

    @Override
    public @NotNull String getVersion() {

        return io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.projectVersion();
    }

    @Override
    public @NotNull String getMinecraftVersion() {
        return io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.minecraftVersion();
    }

    @Override
    public @NotNull String getBukkitVersion() {
        return io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.paperApiVersion();
    }

    @Override
    public @NotNull Logger getLogger() {
        return logger;
    }

    @Override
    public @NotNull PluginManager getPluginManager() {
        return simplePluginManager;
    }

    public Plugin[] getPlugins() {
        return pluginManager.getPlugins();
    }

    @Override
    public @NotNull ServicesManager getServicesManager() {
        return servicesManager;
    }

    private final Map<String, World> worlds = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, World> worldCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, CraftWorld> worldByDimension = new java.util.concurrent.ConcurrentHashMap<>();
    // Tracks which dimensions have already had WorldInitEvent/WorldLoadEvent fired, so the
    // boot-time firing loop (MinecraftServerMixin.lunararc$afterWorldLoad) and the
    // plugin-initiated createWorld() path below never double-fire for the same dimension
    // regardless of which one reaches it first. Public: accessed from MinecraftServerMixin,
    // a different package.
    public final java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> worldLoadEventFired =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private CraftWorld craftWorld(net.minecraft.server.level.ServerLevel level) {
        // Fast path: an already-registered world needs no index maintenance. This is not a
        // micro-optimisation for its own sake - every chunk decoration looks its world up here,
        // on the worldgen threads, and the registration block below touches four maps and used
        // to hash a UUID out of the dimension name each time. Bulk terrain generation, such as a
        // random-teleport search walking far chunks, ran all of that per chunk.
        CraftWorld existing = worldByDimension.get(level.dimension());
        if (existing != null) return existing;

        CraftWorld craft = worldByDimension.computeIfAbsent(level.dimension(), ignored -> new CraftWorld(level));
        worldCache.putIfAbsent(craft.getUID(), craft);

        worlds.putIfAbsent(craft.getName(), craft);

        worldCache.putIfAbsent(craft.getLegacyDimensionUID(), craft);
        return craft;
    }

    public @NotNull CraftWorld getCraftWorld(@NotNull net.minecraft.server.level.ServerLevel level) {
        return craftWorld(java.util.Objects.requireNonNull(level, "level"));
    }

    public @Nullable CraftWorld getCraftWorldIfPresent(@NotNull net.minecraft.server.level.ServerLevel level) {
        java.util.Objects.requireNonNull(level, "level");
        CraftWorld craft = worldByDimension.get(level.dimension());
        return craft != null && craft.getHandle() == level ? craft : null;
    }

    private CraftWorld registerDynamicWorld(net.minecraft.server.level.ServerLevel level, String name,
            @Nullable org.bukkit.generator.ChunkGenerator generator,
            @Nullable org.bukkit.generator.BiomeProvider biomeProvider) {
        CraftWorld craft = new CraftWorld(level, name, generator, biomeProvider);
        CraftWorld previous = worldByDimension.putIfAbsent(level.dimension(), craft);
        if (previous != null && previous != craft) {
            throw new IllegalStateException("A Bukkit world is already registered for " + level.dimension().location());
        }
        worlds.put(name, craft);
        worldCache.put(craft.getUID(), craft);
        worldCache.put(craft.getLegacyDimensionUID(), craft);
        return craft;
    }

    @Override
    public @NotNull List<CraftPlayer> getOnlinePlayers() {
        // List<CraftPlayer>, as CraftBukkit declares it. The erasure is part of the descriptor, so
        // returning Collection here means donated code compiled against ()Ljava/util/List; cannot
        // bind - PaperServerListPingEvent, Title and the mobcaps command all call it.
        List<CraftPlayer> players = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : playerList.getPlayers()) {
            if (getPlayer(player.getUUID()) instanceof CraftPlayer craftPlayer) {
                players.add(craftPlayer);
            }
        }
        return players;
    }

    @Override
    public @NotNull List<World> getWorlds() {

        for (net.minecraft.server.level.ServerLevel level : console.getAllLevels()) {
            craftWorld(level);
        }
        return java.util.List.copyOf(worlds.values());
    }

    @Override
    public @NotNull ConsoleCommandSender getConsoleSender() {
        return consoleSender;
    }

    @Override
    public @NotNull SimpleCommandMap getCommandMap() {
        return commandMap;
    }

    public void loadPlugins() {
        // Reverted from Paper's modern PluginInitializerManager/LaunchEntryPointHandler system
        // back to the classic folder-scan approach — matching real Arclight's own proven,
        // working pattern (confirmed directly from a real Arclight crash trace:
        // DedicatedServer.arclight$loadPlugins -> CraftServer.loadPlugins -> classic
        // SimplePluginManager.loadPlugins(File[])). Arclight is Spigot-based and never used
        // Paper's modern entrypoint system at all. That modern system depends on reliably
        // hooking PluginInitializerManager.load() at the right point in boot — confirmed
        // unreliable across three different injection points this session (Main.main() at the
        // real Bootstrap.bootStrap() call site, Main.main() at HEAD, and
        // DedicatedServer.initServer() at HEAD). This simpler, classic path has no such
        // dependency: simplePluginManager.registerInterface(LunarArcPluginLoader.class) is
        // already called in the constructor, so the loader is ready to use directly here.
        File pluginsFolder = getPluginsFolder();
        if (!pluginsFolder.exists()) {
            pluginsFolder.mkdirs();
        }
        File[] files = pluginsFolder.listFiles(f -> f.getName().endsWith(".jar"));
        if (files != null) {
            io.lunararcdevs.lunararc.common.mod.util.log.LunarArcConsole.info(logger,
                    "Found " + files.length + " potential plugins. Loading...");
            // Boot-time load, not the runtime PluginManager API: the runtime path refuses
            // paper-plugin.yml plugins (correctly, for real Paper's runtime) and was silently
            // dropping every modern plugin here.
            simplePluginManager.getInternalManager().loadPluginsAtBoot(files);
        }
    }

    public void enablePlugins(org.bukkit.plugin.PluginLoadOrder type) {
        io.lunararcdevs.lunararc.common.mod.util.log.LunarArcConsole.info(logger, "Enabling Bukkit plugins (Order: " + type + ")...");

        if (type == org.bukkit.plugin.PluginLoadOrder.STARTUP) {
            // Where CraftBukkit registers Spigot's own commands, before any plugin can claim the
            // labels. Without this LunarArc had no /restart at all.
            for (java.util.Map.Entry<String, org.bukkit.command.Command> entry
                    : org.spigotmc.SpigotConfig.commands.entrySet()) {
                commandMap.register(entry.getKey(), "spigot", entry.getValue());
            }
        }

        simplePluginManager.enablePlugins(type);
    }

    public void disablePlugins() {
        simplePluginManager.disablePlugins();
    }

    public void shutdownSchedulers() {
        paperSchedulers.beginShutdown();
        scheduler.beginShutdown();
        paperSchedulers.awaitShutdown();
        scheduler.awaitShutdown();
    }

    public void clearPluginsForShutdown() {
        simplePluginManager.clearPlugins();
        io.lunararcdevs.lunararc.common.server.LunarArcCommandMap.setDispatcher(null);
        io.lunararcdevs.lunararc.common.server.LunarArcContext.clearServerReferences();
    }

    public void syncCommands() {
        com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher = console.getCommands().getDispatcher();
        if (commandMap instanceof io.lunararcdevs.lunararc.common.server.LunarArcCommandMap lunarArcMap) {
            lunarArcMap.syncToBrigadier(dispatcher);
        }
        for (net.minecraft.server.level.ServerPlayer player : console.getPlayerList().getPlayers()) {
            console.getCommands().sendCommands(player);
        }
    }

    @Override
    public int getMaxPlayers() {
        return playerList.getMaxPlayers();
    }

    @Override
    public void setMaxPlayers(int maxPlayers) {
        if (maxPlayers < 0) throw new IllegalArgumentException("maxPlayers must be >= 0");
        playerList.maxPlayers = maxPlayers;
    }

    @Override
    public int getPort() {
        return console.getPort();
    }

    private net.minecraft.server.dedicated.DedicatedServer dedicatedServer() {
        if (console instanceof net.minecraft.server.dedicated.DedicatedServer dedicated) return dedicated;
        throw new IllegalStateException("LunarArc CraftServer requires a DedicatedServer runtime");
    }

    private net.minecraft.server.dedicated.DedicatedServerProperties dedicatedProperties() {
        return dedicatedServer().getProperties();
    }

    private String readLevelType() {
        try {
            java.lang.reflect.Field propertiesField = net.minecraft.server.dedicated.Settings.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
            Object raw = propertiesField.get(dedicatedProperties());
            if (raw instanceof java.util.Properties props) return props.getProperty("level-type", "minecraft:normal");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to read the 1.21.1 level-type server property", ex);
        }
        return "minecraft:normal";
    }

    @Override
    public long getConnectionThrottle() {
        return bukkitConfig.getLong("settings.connection-throttle", 4000L);
    }

    @Override
    public int getViewDistance() {
        return dedicatedProperties().viewDistance;
    }

    @Override
    public int getSimulationDistance() {
        return dedicatedProperties().simulationDistance;
    }

    @Override
    public @NotNull String getIp() {
        return console.getLocalIp();
    }

    @Override
    public @NotNull String getWorldType() {
        return readLevelType();
    }

    @Override
    public boolean getGenerateStructures() {
        return console.getWorldData().worldGenOptions().generateStructures();
    }

    @Override
    public int getSpawnRadius() {
        return console.getSpawnProtectionRadius();
    }

    public int getBukkitSpawnRadius() {
        return bukkitConfig.getInt("settings.spawn-radius", -1);
    }

    @Override
    public void setSpawnRadius(int value) {
        bukkitConfig.set("settings.spawn-radius", value);
        try {
            bukkitConfig.save(new File("bukkit.yml"));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to persist Bukkit spawn radius", ex);
        }
    }

    @Override
    public boolean isHardcore() {
        return console.getWorldData().isHardcore();
    }

    @Override
    public boolean getAllowFlight() {
        return console.isFlightAllowed();
    }

    @Override
    public boolean getOnlineMode() {
        return console.usesAuthentication();
    }

    @Override
    public boolean getHideOnlinePlayers() {
        return dedicatedProperties().hideOnlinePlayers;
    }

    @Override
    public boolean getAllowNether() {
        return dedicatedProperties().allowNether;
    }

    @Override
    public boolean getAllowEnd() {
        return bukkitConfig.getBoolean("settings.allow-end", true);
    }

    @Override
    public boolean hasWhitelist() {
        return playerList.isUsingWhitelist();
    }

    @Override
    public void setWhitelist(boolean value) {
        playerList.setUsingWhiteList(value);
    }

    @Override
    public boolean isWhitelistEnforced() {
        return console.isEnforceWhitelist();
    }

    @Override
    public void setWhitelistEnforced(boolean value) {
        console.setEnforceWhitelist(value);
    }

    @Override
    public @NotNull Set<OfflinePlayer> getWhitelistedPlayers() {
        Set<OfflinePlayer> result = new LinkedHashSet<>();
        try {
            Object whiteList = playerList.getWhiteList();
            Object entries = whiteList.getClass().getMethod("getEntries").invoke(whiteList);
            if (entries instanceof String[] ids) {
                for (String raw : ids) {
                    try { result.add(getOfflinePlayer(UUID.fromString(raw))); } catch (IllegalArgumentException ignored) {}
                }
            } else if (entries instanceof Collection<?> collection) {
                for (Object raw : collection) {
                    if (raw instanceof String id) {
                        try { result.add(getOfflinePlayer(UUID.fromString(id))); } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {
            for (OfflinePlayer player : getOfflinePlayers()) {
                if (player.isWhitelisted()) result.add(player);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public void reloadWhitelist() {
        playerList.reloadWhiteList();
    }

    @Override
    public int broadcastMessage(@NotNull String message) {
        int count = 0;
        for (Player p : getOnlinePlayers()) { p.sendMessage(message); count++; }
        getConsoleSender().sendMessage(message);
        return count;
    }

    @Override
    public int broadcast(@NotNull String message, @NotNull String permission) {
        int count = 0;
        for (Player p : getOnlinePlayers()) {
            if (p.hasPermission(permission)) { p.sendMessage(message); count++; }
        }
        return count;
    }

    @Override
    public int broadcast(@NotNull net.kyori.adventure.text.Component message, @NotNull String permission) {
        int count = 0;
        for (Player p : getOnlinePlayers()) {
            if (p.hasPermission(permission)) { p.sendMessage(message); count++; }
        }
        return count;
    }

    @Override
    public int broadcast(@NotNull net.kyori.adventure.text.Component message) {
        int count = 0;
        for (Player p : getOnlinePlayers()) { p.sendMessage(message); count++; }
        getConsoleSender().sendMessage(message);
        return count;
    }

    @Override
    public @Nullable Player getPlayer(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        Player exact = getPlayerExact(name);
        if (exact != null) return exact;

        String lower = name.toLowerCase(Locale.ROOT);
        Player best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (Player player : getOnlinePlayers()) {
            String candidate = player.getName();
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                int delta = Math.abs(candidate.length() - name.length());
                if (delta < bestDelta) {
                    best = player;
                    bestDelta = delta;
                    if (delta == 0) break;
                }
            }
        }
        return best;
    }

    @Override
    public @Nullable Player getPlayerExact(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        net.minecraft.server.level.ServerPlayer player = playerList.getPlayerByName(name);
        return player != null ? getPlayer(player.getUUID()) : null;
    }

    @Override
    public @NotNull List<Player> matchPlayer(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        List<Player> matches = new ArrayList<>();
        String lower = name.toLowerCase(Locale.ROOT);
        for (Player player : getOnlinePlayers()) {
            String candidate = player.getName();
            if (candidate.equalsIgnoreCase(name)) return Collections.singletonList(player);
            if (candidate.toLowerCase(Locale.ROOT).contains(lower)) matches.add(player);
        }
        return matches;
    }

    @Override
    public @Nullable Player getPlayer(@NotNull UUID id) {
        Objects.requireNonNull(id, "id");
        net.minecraft.server.level.ServerPlayer player = playerList.getPlayer(id);
        if (player == null) return null;
        org.bukkit.entity.Entity bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) (Object) player)
                .lunararc$getBukkitEntity();
        if (!(bukkit instanceof Player result)) {
            throw new IllegalStateException("ServerPlayer did not resolve to a Bukkit Player: " + bukkit);
        }
        return result;
    }

    @Override
    public @Nullable org.bukkit.entity.Entity getEntity(@NotNull UUID id) {
        Objects.requireNonNull(id, "id");
        for (net.minecraft.server.level.ServerLevel level : console.getAllLevels()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            if (entity != null) return org.bukkit.craftbukkit.entity.CraftEntity.getEntity(this, entity);
        }
        return null;
    }

    @Override
    public @Nullable World getWorld(@NotNull String name) {
        if (name == null || name.isBlank()) return null;

        for (net.minecraft.server.level.ServerLevel level : console.getAllLevels()) {
            CraftWorld craft = craftWorld(level);
            if (craft.getName().equalsIgnoreCase(name)
                    || level.dimension().location().toString().equalsIgnoreCase(name)
                    || level.dimension().location().getPath().equalsIgnoreCase(name)) {
                return craft;
            }
        }

        String qualified = switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "world" -> "minecraft:overworld";
            case "world_nether" -> "minecraft:the_nether";
            case "world_the_end" -> "minecraft:the_end";
            default -> name.contains(":") ? name : "minecraft:" + name;
        };
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(qualified);
        if (rl == null) return null;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key = net.minecraft.resources.ResourceKey
                .create(net.minecraft.core.registries.Registries.DIMENSION, rl);
        net.minecraft.server.level.ServerLevel level = console.getLevel(key);
        return level == null ? null : craftWorld(level);
    }

    @Override
    public @Nullable World getWorld(@NotNull UUID uid) {
        World cached = worldCache.get(uid);
        if (cached != null) return cached;
        for (net.minecraft.server.level.ServerLevel level : console.getAllLevels()) {
            CraftWorld world = craftWorld(level);
            if (world.getUID().equals(uid) || world.getLegacyDimensionUID().equals(uid)) return world;
        }
        return null;
    }

    @Override
    public @Nullable World getWorld(@NotNull net.kyori.adventure.key.Key key) {
        return getWorld(key.namespace() + ":" + key.value());
    }

    @Override
    public @NotNull org.bukkit.craftbukkit.map.CraftMapView createMap(@NotNull World world) {
        Objects.requireNonNull(world, "world");
        int id = this.nextMapId.getAndIncrement();
        org.bukkit.craftbukkit.map.CraftMapView view = new org.bukkit.craftbukkit.map.CraftMapView(id, world);
        this.mapViews.put(id, view);
        return view;
    }

    @Override
    public @Nullable org.bukkit.craftbukkit.map.CraftMapView getMap(int id) {
        return mapViews.get(id);
    }

    public void reloadPaperWorldConfigurations() {
        if (!isPrimaryThread()) throw new IllegalStateException("Paper configuration reload must run on the server thread");
        java.util.Map<CraftWorld, io.papermc.paper.configuration.WorldConfiguration> loaded = new java.util.LinkedHashMap<>();
        for (CraftWorld world : worldByDimension.values()) loaded.put(world, world.loadPaperConfiguration());
        loaded.forEach(CraftWorld::applyPaperConfiguration);
    }

    @Override
    public void reload() {
        if (!isPrimaryThread()) {
            throw new IllegalStateException("Bukkit reload must run on the primary server thread");
        }

        io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge bridge =
                (io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) (Object) console;

        reloadPaperWorldConfigurations();
        disablePlugins();
        simplePluginManager.clearPlugins();
        commandMap.clearCommands();
        helpMap.clear();
        loadBukkitSideConfiguration();

        bridge.lunararc$loaderUnlockRegistries();
        try {
            reloadData();
        } finally {
            bridge.lunararc$loaderLockRegistries();
        }

        if (!(this.console instanceof net.minecraft.server.dedicated.DedicatedServer dedicated)) {
            throw new IllegalStateException("PluginInitializerManager.reload() is only supported on a dedicated server");
        }
        io.papermc.paper.plugin.PluginInitializerManager.reload(dedicated);
        loadPlugins();
        enablePlugins(org.bukkit.plugin.PluginLoadOrder.STARTUP);
        enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);

        com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher =
                console.getCommands().getDispatcher();
        io.lunararcdevs.lunararc.common.server.LunarArcPaperCommands registrar =
                new io.lunararcdevs.lunararc.common.server.LunarArcPaperCommands(dispatcher);
        io.lunararcdevs.lunararc.common.server.LunarArcReloadableRegistrarEvent<io.papermc.paper.command.brigadier.Commands> lifecycle =
                new io.lunararcdevs.lunararc.common.server.LunarArcReloadableRegistrarEvent<>(
                        registrar,
                        io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD);
        io.lunararcdevs.lunararc.common.server.LunarArcLifecycleEventRunner.fire(
                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, lifecycle);

        syncCommands();
        pluginManager.callEvent(new org.bukkit.event.server.ServerLoadEvent(
                org.bukkit.event.server.ServerLoadEvent.LoadType.RELOAD));
    }

    @Override
    public void reloadData() {
        console.reloadResources(console.getPackRepository().getSelectedIds()).join();
        Runnable fire = () -> getPluginManager().callEvent(
                new io.papermc.paper.event.server.ServerResourcesReloadedEvent(
                        io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN));
        if (console.isSameThread()) fire.run(); else console.execute(fire);
    }

    @Override
    public void savePlayers() {
        playerList.saveAll();
    }

    @Override
    public void shutdown() {
        console.halt(false);
    }

    @Override
    public boolean isPrimaryThread() {
        return console.isSameThread();
    }

    @Override
    public @NotNull String getMotd() {
        return console.getMotd();
    }

    @Override
    public @Nullable String getShutdownMessage() {
        return "Server closed";
    }

    @Override
    public @NotNull Spigot spigot() {
        return spigot;
    }

    @Override
    public @Nullable org.bukkit.craftbukkit.scheduler.CraftScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public @NotNull Messenger getMessenger() {
        return messenger;
    }

    @Override
    public void sendPluginMessage(@NotNull org.bukkit.plugin.Plugin source, @NotNull String channel, byte[] message) {
        StandardMessenger.validatePluginMessage(messenger, source, channel, message);
        for (Player player : getOnlinePlayers()) {
            player.sendPluginMessage(source, channel, message);
        }
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
        Set<String> channels = new HashSet<>();
        for (Player player : getOnlinePlayers()) {
            channels.addAll(player.getListeningPluginChannels());
        }
        return Collections.unmodifiableSet(channels);
    }

    @Override
    public @NotNull HelpMap getHelpMap() {
        return helpMap;
    }

    @Override
    public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, @NotNull InventoryType type) {
        return new org.bukkit.craftbukkit.inventory.CraftInventory(owner, type);
    }

    @Override
    public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, @NotNull InventoryType type,
            @NotNull String title) {
        return new org.bukkit.craftbukkit.inventory.CraftInventory(
                owner, type.getDefaultSize(), type, net.kyori.adventure.text.Component.text(title));
    }

    @Override
    public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, @NotNull InventoryType type,
            @NotNull net.kyori.adventure.text.Component title) {
        return new org.bukkit.craftbukkit.inventory.CraftInventory(
                owner, type.getDefaultSize(), type, title);
    }

    @Override
    public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, int size)
            throws IllegalArgumentException {
        if (size <= 0 || size % 9 != 0) throw new IllegalArgumentException("size must be positive multiple of 9");
        return new org.bukkit.craftbukkit.inventory.CraftInventory(
                owner, size, net.kyori.adventure.text.Component.text("Chest"));
    }

    @Override
    public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, int size, @NotNull String title)
            throws IllegalArgumentException {
        if (size <= 0 || size % 9 != 0) throw new IllegalArgumentException("size must be positive multiple of 9");
        return new org.bukkit.craftbukkit.inventory.CraftInventory(
                owner, size, net.kyori.adventure.text.Component.text(title));
    }

    @Override
    public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, int size,
            @NotNull net.kyori.adventure.text.Component title) throws IllegalArgumentException {
        if (size <= 0 || size % 9 != 0) throw new IllegalArgumentException("size must be positive multiple of 9");
        return new org.bukkit.craftbukkit.inventory.CraftInventory(owner, size, title);
    }

    @Override
    public @NotNull Merchant createMerchant(@Nullable String title) {
        return new org.bukkit.craftbukkit.inventory.CraftMerchantCustom(title);
    }

    @Override
    public @NotNull Merchant createMerchant(@NotNull net.kyori.adventure.text.Component title) {
        return new org.bukkit.craftbukkit.inventory.CraftMerchantCustom(Objects.requireNonNull(title, "title"));
    }

    @Override
    public int getMonsterSpawnLimit() {
        return getSpawnLimit(SpawnCategory.MONSTER);
    }

    @Override
    public int getAnimalSpawnLimit() {
        return getSpawnLimit(SpawnCategory.ANIMAL);
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        return getSpawnLimit(SpawnCategory.WATER_ANIMAL);
    }

    @Override
    public int getWaterAmbientSpawnLimit() {
        return getSpawnLimit(SpawnCategory.WATER_AMBIENT);
    }

    @Override
    public int getWaterUndergroundCreatureSpawnLimit() {
        return getSpawnLimit(SpawnCategory.WATER_UNDERGROUND_CREATURE);
    }

    @Override
    public int getAmbientSpawnLimit() {
        return getSpawnLimit(SpawnCategory.AMBIENT);
    }

    @Override
    public @NotNull File getWorldContainer() {
        return new File(".");
    }

    @Override
    public @NotNull OfflinePlayer[] getOfflinePlayers() {
        Map<UUID, OfflinePlayer> players = new LinkedHashMap<>();
        for (Player online : getOnlinePlayers()) players.put(online.getUniqueId(), online);

        Set<File> dataDirs = new LinkedHashSet<>();
        for (World world : getWorlds()) {
            File folder = world.getWorldFolder();
            if (folder != null) dataDirs.add(new File(folder, "playerdata"));
        }
        dataDirs.add(new File(getWorldContainer(), "world/playerdata"));

        for (File dir : dataDirs) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".dat") && name.length() > 4);
            if (files == null) continue;
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4);
                try {
                    UUID uuid = UUID.fromString(id);
                    players.putIfAbsent(uuid, getOfflinePlayer(uuid));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return players.values().toArray(new OfflinePlayer[0]);
    }

    @Override
    public @NotNull OfflinePlayer getOfflinePlayer(@NotNull String name) {
        Player online = getPlayer(name);
        if (online != null) return online;

        try {
            var profile = console.getProfileCache().get(name);
            if (profile.isPresent()) {
                return new org.bukkit.craftbukkit.entity.CraftOfflinePlayer(
                        profile.get().getId(), name);
            }
        } catch (Throwable ignored) {}
        return new org.bukkit.craftbukkit.entity.CraftOfflinePlayer(
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name);
    }

    @Override
    public @NotNull OfflinePlayer getOfflinePlayer(@NotNull UUID id) {
        Player online = getPlayer(id);
        if (online != null) return online;

        try {
            var profile = console.getProfileCache().get(id);
            if (profile.isPresent()) {
                return new org.bukkit.craftbukkit.entity.CraftOfflinePlayer(
                        id, profile.get().getName());
            }
        } catch (Throwable ignored) {}
        return new org.bukkit.craftbukkit.entity.CraftOfflinePlayer(id, null);
    }

    @Override
    public @Nullable OfflinePlayer getOfflinePlayerIfCached(@NotNull String name) {
        Player online = getPlayer(name);
        if (online != null) return online;
        try {
            var profile = console.getProfileCache().get(name);
            if (profile.isPresent()) {
                return new org.bukkit.craftbukkit.entity.CraftOfflinePlayer(
                        profile.get().getId(), name);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private org.bukkit.craftbukkit.ban.CraftIpBanList lunararc$ipBanList() {
        return new org.bukkit.craftbukkit.ban.CraftIpBanList(this.playerList.getIpBans());
    }

    private org.bukkit.craftbukkit.ban.CraftProfileBanList lunararc$profileBanList() {
        return new org.bukkit.craftbukkit.ban.CraftProfileBanList(this.playerList.getBans());
    }

    @Override
    public @NotNull Set<String> getIPBans() {
        Set<String> result = new LinkedHashSet<>();
        for (Object raw : lunararc$ipBanList().getBanEntries()) {
            if (raw instanceof BanEntry<?> entry) result.add(entry.getTarget());
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public void banIP(@NotNull String address) {
        Objects.requireNonNull(address, "address");
        try {
            lunararc$ipBanList().addBan(java.net.InetAddress.getByName(address), null, (java.util.Date) null, null);
        } catch (java.net.UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid IP address: " + address, ex);
        }
    }

    @Override
    public void unbanIP(@NotNull String address) {
        Objects.requireNonNull(address, "address");
        try {
            lunararc$ipBanList().pardon(java.net.InetAddress.getByName(address));
        } catch (java.net.UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid IP address: " + address, ex);
        }
    }

    @Override
    public void banIP(@NotNull java.net.InetAddress address) {
        lunararc$ipBanList().addBan(Objects.requireNonNull(address, "address"), null, (java.util.Date) null, null);
    }

    @Override
    public void unbanIP(@NotNull java.net.InetAddress address) {
        lunararc$ipBanList().pardon(Objects.requireNonNull(address, "address"));
    }

    @Override
    public @NotNull Set<OfflinePlayer> getBannedPlayers() {
        Set<OfflinePlayer> result = new LinkedHashSet<>();
        for (OfflinePlayer player : getOfflinePlayers()) {
            if (player.isBanned()) result.add(player);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull BanList getBanList(BanList.@NotNull Type type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case IP -> lunararc$ipBanList();
            case NAME, PROFILE -> lunararc$profileBanList();
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <B extends BanList<E>, E> B getBanList(@NotNull io.papermc.paper.ban.BanListType<B> type) {
        Objects.requireNonNull(type, "type");
        if (type == io.papermc.paper.ban.BanListType.IP) return (B) lunararc$ipBanList();
        if (type == io.papermc.paper.ban.BanListType.PROFILE) return (B) lunararc$profileBanList();
        throw new IllegalArgumentException("Unknown BanListType: " + type);
    }

    @Override
    public @NotNull Set<OfflinePlayer> getOperators() {
        Set<OfflinePlayer> result = new LinkedHashSet<>();
        for (OfflinePlayer player : getOfflinePlayers()) {
            if (player.isOp()) result.add(player);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Warning.@NotNull WarningState getWarningState() {
        return Warning.WarningState.DEFAULT;
    }

    @Override
    public @Nullable org.bukkit.craftbukkit.util.CraftIconCache getServerIcon() {
        org.bukkit.craftbukkit.util.CraftIconCache cached = this.serverIcon;
        if (cached != null) return cached;
        File file = new File("server-icon.png");
        if (!file.isFile()) return null;
        try {
            cached = loadServerIcon(file);
            this.serverIcon = cached;
            return cached;
        } catch (Exception exception) {
            logger.log(java.util.logging.Level.WARNING, "Could not load server-icon.png", exception);
            return null;
        }
    }

    @Override
    public @NotNull org.bukkit.craftbukkit.util.CraftIconCache loadServerIcon(@NotNull File file) throws Exception {
        Objects.requireNonNull(file, "file");
        if (!file.isFile()) throw new IllegalArgumentException("File is not a valid file: " + file);
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IllegalArgumentException("File is not a readable image: " + file);
        return loadServerIcon(image);
    }

    @Override
    public @NotNull org.bukkit.craftbukkit.util.CraftIconCache loadServerIcon(@NotNull BufferedImage image) throws Exception {
        Objects.requireNonNull(image, "image");
        if (image.getWidth() != 64 || image.getHeight() != 64) {
            throw new IllegalArgumentException("Server icon must be exactly 64x64 pixels");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", output)) {
            throw new IllegalArgumentException("Could not encode server icon as PNG");
        }
        return new org.bukkit.craftbukkit.util.CraftIconCache(output.toByteArray());
    }

    @Override
    public void setIdleTimeout(int threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Idle timeout cannot be negative");
        this.console.setPlayerIdleTimeout(threshold);
    }

    @Override
    public int getIdleTimeout() {
        return this.console.getPlayerIdleTimeout();
    }

    @Override
    public @NotNull UnsafeValues getUnsafe() {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.INSTANCE;
    }

    @Override
    public @NotNull StructureManager getStructureManager() {
        return structureManager;
    }

    @Override
    public @NotNull io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler() {
        return paperSchedulers.async();
    }

    @Override
    public @NotNull io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler() {
        return paperSchedulers.global();
    }

    @Override
    public @NotNull io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler() {
        return paperSchedulers.region();
    }

    public @NotNull io.papermc.paper.threadedregions.scheduler.EntityScheduler getEntityScheduler(
            @NotNull net.minecraft.world.entity.Entity entity) {
        return paperSchedulers.entity(entity);
    }

    @Override
    public @NotNull org.bukkit.entity.EntityFactory getEntityFactory() {
        return org.bukkit.craftbukkit.entity.CraftEntityFactory.instance();
    }

    @Override
    public @NotNull org.bukkit.inventory.ItemFactory getItemFactory() {
        return org.bukkit.craftbukkit.inventory.CraftItemFactory.instance();
    }

    @Override
    public @NotNull org.bukkit.potion.PotionBrewer getPotionBrewer() {
        return this.potionBrewer;
    }

    @Override
    public @NotNull com.destroystokyo.paper.entity.ai.MobGoals getMobGoals() {
        return this.mobGoals;
    }

    @Override
    public @NotNull org.bukkit.ServerLinks getServerLinks() {
        return this.serverLinks;
    }

    @Override
    public @NotNull org.bukkit.packs.DataPackManager getDataPackManager() {
        return legacyDataPackManager;
    }

    @Override
    public @NotNull io.papermc.paper.datapack.DatapackManager getDatapackManager() {
        return datapackManager;
    }

    @Override
    public @NotNull List<String> getInitialDisabledPacks() {
        return Collections.unmodifiableList(dedicatedProperties().initialDataPackConfiguration.getDisabled());
    }

    @Override
    public @NotNull List<String> getInitialEnabledPacks() {
        return Collections.unmodifiableList(dedicatedProperties().initialDataPackConfiguration.getEnabled());
    }

    @Override
    public @NotNull org.bukkit.ServerTickManager getServerTickManager() {
        return serverTickManager;
    }

    @Override
    public boolean isStopping() {
        return console.isStopped();
    }

    @Override
    public boolean isLoggingIPs() {
        return dedicatedProperties().logIPs;
    }

    @Override
    public boolean isTickingWorlds() {
        return ((io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) (Object) console)
                .lunararc$isTickingWorlds();
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Override
    public boolean isResourcePackRequired() {
        return this.getServer().isResourcePackRequired();
    }

    @Override
    public @NotNull String getResourcePackHash() {
        return this.getServer().getServerResourcePack()
                .map(MinecraftServer.ServerResourcePackInfo::hash)
                .orElse("")
                .toUpperCase(Locale.ROOT);
    }

    @Override
    public @NotNull String getResourcePack() {
        return this.getServer().getServerResourcePack()
                .map(MinecraftServer.ServerResourcePackInfo::url)
                .orElse("");
    }

    @Override
    public @NotNull String getResourcePackPrompt() {
        return this.getServer().getServerResourcePack()
                .map(MinecraftServer.ServerResourcePackInfo::prompt)
                .map(org.bukkit.craftbukkit.util.CraftChatMessage::fromComponent)
                .orElse("");
    }

    @Override
    public boolean isAcceptingTransfers() {
        return this.getServer().acceptsTransfers();
    }

    @Override
    public boolean isEnforcingSecureProfiles() {
        return this.getServer().enforceSecureProfile();
    }

    @Override
    public boolean shouldSendChatPreviews() {
        return false;
    }

    @Override
    public double[] getTPS() {
        return ((io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) (Object) console).lunararc$getTps();
    }

    @Override
    public long[] getTickTimes() {
        return ((io.lunararcdevs.lunararc.common.bridge.access.MinecraftServerAccessBridge) (Object) console).lunararc$getTickTimesNanos().clone();
    }

    @Override
    public double getAverageTickTime() {
        long[] times = getTickTimes();
        long total = 0L;
        int populated = 0;
        for (long time : times) {
            if (time <= 0L) continue;
            total += time;
            populated++;
        }
        return populated == 0 ? 0.0D : ((double) total / (double) populated) / 1_000_000.0D;
    }

    @Override
    public int getCurrentTick() {
        return ((io.lunararcdevs.lunararc.common.bridge.access.MinecraftServerAccessBridge) (Object) console).lunararc$getTickCount();
    }

    @Override
    public boolean reloadCommandAliases() {
        java.util.Map<String, String[]> previous = new java.util.LinkedHashMap<>(this.getCommandAliases());
        java.io.File file = new java.io.File("commands.yml");
        try {
            this.commandsConfig.load(file);
        } catch (java.io.FileNotFoundException ex) {
            return false;
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            this.logger.log(java.util.logging.Level.WARNING, "Unable to reload commands.yml", ex);
            return false;
        }
        if (this.commandMap instanceof io.lunararcdevs.lunararc.common.server.LunarArcCommandMap lunarArcCommandMap) {
            return lunarArcCommandMap.reloadServerAliases(previous, this.getCommandAliases());
        }
        return false;
    }

    private void loadCustomPermissions() {
        String configured = this.bukkitConfig.getString("settings.permissions-file", "permissions.yml");
        java.io.File file = new java.io.File(configured == null || configured.isBlank() ? "permissions.yml" : configured);
        if (!file.isFile()) return;

        org.bukkit.configuration.file.YamlConfiguration permissions = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            permissions.load(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            this.logger.log(java.util.logging.Level.WARNING, "Unable to load " + file.getPath(), ex);
            return;
        }

        java.util.Map<String, Object> values = permissions.getValues(false);
        java.util.List<org.bukkit.permissions.Permission> loaded = org.bukkit.permissions.Permission.loadPermissions(
                values,
                "Permission node '%s' in " + file + " is invalid",
                org.bukkit.permissions.Permission.DEFAULT_PERMISSION);
        for (org.bukkit.permissions.Permission permission : loaded) {
            try {
                this.pluginManager.addPermission(permission);
            } catch (IllegalArgumentException ex) {
                this.logger.log(java.util.logging.Level.WARNING,
                        "Permission '" + permission.getName() + "' from " + file + " is already registered", ex);
            }
        }
    }

    @Override
    public void reloadPermissions() {
        this.pluginManager.clearPermissions();
        for (org.bukkit.plugin.Plugin plugin : this.pluginManager.getPlugins()) {
            for (org.bukkit.permissions.Permission permission : plugin.getDescription().getPermissions()) {
                try {
                    this.pluginManager.addPermission(permission);
                } catch (IllegalArgumentException ex) {
                    this.logger.log(java.util.logging.Level.WARNING,
                            "Plugin " + plugin.getDescription().getFullName()
                                    + " tried to register duplicate permission '" + permission.getName() + "'", ex);
                }
            }
        }
        this.loadCustomPermissions();
        org.bukkit.util.permissions.DefaultPermissions.registerCorePermissions();
    }

    @Override
    public @NotNull <T extends Keyed> Iterable<Tag<T>> getTags(@NotNull String registry, @NotNull Class<T> clazz) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(clazz, "clazz");
        List<Tag<T>> tags = new ArrayList<>();
        java.util.stream.Stream<? extends net.minecraft.tags.TagKey<?>> keys;
        if (org.bukkit.Tag.REGISTRY_ITEMS.equals(registry)) {
            if (clazz != Material.class) throw new IllegalArgumentException("Item tags require Material values");
            keys = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags().map(pair -> pair.getFirst());
        } else if (org.bukkit.Tag.REGISTRY_BLOCKS.equals(registry)) {
            if (clazz != Material.class) throw new IllegalArgumentException("Block tags require Material values");
            keys = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTags().map(pair -> pair.getFirst());
        } else if (org.bukkit.Tag.REGISTRY_FLUIDS.equals(registry)) {
            if (clazz != org.bukkit.Fluid.class) throw new IllegalArgumentException("Fluid tags require Fluid values");
            keys = net.minecraft.core.registries.BuiltInRegistries.FLUID.getTags().map(pair -> pair.getFirst());
        } else if (org.bukkit.Tag.REGISTRY_ENTITY_TYPES.equals(registry)) {
            if (clazz != org.bukkit.entity.EntityType.class) throw new IllegalArgumentException("Entity tags require EntityType values");
            keys = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getTags().map(pair -> pair.getFirst());
        } else if (org.bukkit.Tag.REGISTRY_GAME_EVENTS.equals(registry)) {
            if (clazz != org.bukkit.GameEvent.class) throw new IllegalArgumentException("Game-event tags require GameEvent values");
            keys = net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.getTags().map(pair -> pair.getFirst());
        } else {
            throw new IllegalArgumentException("Unsupported Bukkit tag registry: " + registry);
        }
        keys.forEach(key -> {
            net.minecraft.resources.ResourceLocation id = key.location();
            Tag<T> value = getTag(registry, new NamespacedKey(id.getNamespace(), id.getPath()), clazz);
            if (value != null) tags.add(value);
        });
        return Collections.unmodifiableList(tags);
    }

    @Override
    public @Nullable org.bukkit.scoreboard.Criteria getScoreboardCriteria(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        return org.bukkit.craftbukkit.scoreboard.CraftCriteria.getFromBukkit(name);
    }

    @Override
    public @NotNull com.destroystokyo.paper.profile.PlayerProfile createProfile(@NotNull UUID uuid) {
        return createProfile(uuid, null);
    }

    @Override
    public @NotNull com.destroystokyo.paper.profile.PlayerProfile createProfile(@NotNull String name) {
        return createProfile(null, name);
    }

    @Override
    public @NotNull com.destroystokyo.paper.profile.PlayerProfile createProfile(@Nullable UUID uuid,
            @Nullable String name) {
        return new io.lunararcdevs.lunararc.common.server.LunarArcPlayerProfile(uuid, name);
    }

    @Override
    public @NotNull com.destroystokyo.paper.profile.PlayerProfile createProfileExact(@Nullable UUID uuid,
            @Nullable String name) {
        return new io.lunararcdevs.lunararc.common.server.LunarArcPlayerProfile(uuid, name);
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component motd() {
        return net.kyori.adventure.text.Component.text(getMotd());
    }

    @Override
    public void motd(@NotNull net.kyori.adventure.text.Component motd) {
        Objects.requireNonNull(motd, "motd");
        setMotd(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(motd));
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component shutdownMessage() {
        return net.kyori.adventure.text.Component.text(getShutdownMessage());
    }

    @Override
    public @NotNull org.bukkit.GameMode getDefaultGameMode() {
        org.bukkit.GameMode mode = org.bukkit.GameMode.getByValue(console.getDefaultGameType().getId());
        if (mode == null) throw new IllegalStateException("Unknown Minecraft default game mode " + console.getDefaultGameType());
        return mode;
    }

    @Override
    public void setDefaultGameMode(@NotNull org.bukkit.GameMode mode) {
        Objects.requireNonNull(mode, "mode");
        try {
            console.setDefaultGameType(net.minecraft.world.level.GameType.byId(mode.getValue()));
        } catch (Throwable ex) {
            throw new IllegalStateException("Unable to set the default game mode", ex);
        }
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return dedicatedProperties().maxChainedNeighborUpdates;
    }

    @Override
    public void setMotd(@NotNull String motd) {
        console.setMotd(Objects.requireNonNull(motd, "motd"));
    }

    @Override
    public boolean suggestPlayerNamesWhenNullTabCompletions() {
        return true;
    }

    @Override
    public @Nullable String getPermissionMessage() {
        return "";
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component permissionMessage() {
        return net.kyori.adventure.text.Component.empty();
    }

    @Override
    public @NotNull Iterable<? extends net.kyori.adventure.audience.Audience> audiences() {
        java.util.ArrayList<net.kyori.adventure.audience.Audience> audiences = new java.util.ArrayList<>();
        audiences.add(getConsoleSender());
        for (Player player : getOnlinePlayers()) audiences.add(player);
        return java.util.Collections.unmodifiableList(audiences);
    }

    @Override
    public @NotNull CommandSender createCommandSender(
            @NotNull Consumer<? super net.kyori.adventure.text.Component> feedback) {
        return new org.bukkit.craftbukkit.command.FeedbackForwardingSender(feedback, this);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return this.console.isSameThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, int x, int z) {
        Objects.requireNonNull(world, "world");
        return this.console.isSameThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location world");
        return this.console.isSameThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, int x, int y, int z) {
        Objects.requireNonNull(world, "world");
        return this.console.isSameThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location, int radius) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location world");
        if (radius < 0) throw new IllegalArgumentException("radius must be non-negative");
        return this.console.isSameThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, @NotNull io.papermc.paper.math.Position position,
            int radius) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        if (radius < 0) throw new IllegalArgumentException("radius must be non-negative");
        return this.console.isSameThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, @NotNull io.papermc.paper.math.Position position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        return this.console.isSameThread();
    }

    @Override
    public @NotNull String getUpdateFolder() {
        return "update";
    }

    @Override
    public @NotNull File getPluginsFolder() {
        return new File("plugins");
    }

    @Override
    public @NotNull File getUpdateFolderFile() {
        return new File(getPluginsFolder(), getUpdateFolder());
    }

    @Override
    public @Nullable org.bukkit.command.PluginCommand getPluginCommand(@NotNull String name) {
        org.bukkit.command.Command command = commandMap.getCommand(name);
        if (command instanceof org.bukkit.command.PluginCommand) {
            return (org.bukkit.command.PluginCommand) command;
        }
        return null;
    }

    @Override
    public boolean dispatchCommand(@NotNull CommandSender sender, @NotNull String commandLine) {
        return io.lunararcdevs.lunararc.common.server.LunarArcCommandRouter.dispatch(
                this, sender, commandLine);
    }

    @Override
    public @NotNull List<Entity> selectEntities(@NotNull CommandSender sender, @NotNull String selector) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(selector, "selector");
        final net.minecraft.commands.CommandSourceStack source;
        if (sender instanceof CraftPlayer craftPlayer) {
            source = craftPlayer.getHandle().createCommandSourceStack();
        } else if (sender instanceof org.bukkit.craftbukkit.entity.CraftEntity craftEntity) {
            source = craftEntity.getHandle().createCommandSourceStack();
        } else if (sender instanceof org.bukkit.craftbukkit.CraftConsoleCommandSender) {
            source = console.createCommandSourceStack();
        } else {
            throw new IllegalArgumentException("Unsupported command sender for entity selector: " + sender.getClass().getName());
        }

        try {
            net.minecraft.commands.arguments.selector.EntitySelector parsed =
                    new net.minecraft.commands.arguments.selector.EntitySelectorParser(
                            new com.mojang.brigadier.StringReader(selector), true).parse();
            List<Entity> result = new ArrayList<>();
            for (net.minecraft.world.entity.Entity entity : parsed.findEntities(source)) {
                result.add(((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity());
            }
            return Collections.unmodifiableList(result);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Invalid entity selector: " + selector, ex);
        }
    }

    @Override
    public @Nullable <T extends Keyed> Registry<T> getRegistry(@NotNull Class<T> type) {
        if (type == null) return null;
        return io.lunararcdevs.lunararc.common.server.LunarArcRegistryAccess.INSTANCE.getRegistry(type);
    }

    @Override
    public @Nullable <T extends Keyed> Tag<T> getTag(@NotNull String registry, @NotNull NamespacedKey tag,
            @NotNull Class<T> clazz) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(clazz, "clazz");
        net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                tag.getNamespace(), tag.getKey());

        if (org.bukkit.Tag.REGISTRY_ITEMS.equals(registry)) {
            if (clazz != Material.class) throw new IllegalArgumentException("Item tags require Material values");
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, location);
            var holders = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(key);
            if (holders.isEmpty()) return null;
            java.util.LinkedHashSet<T> values = new java.util.LinkedHashSet<>();
            for (var holder : holders.get()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value());
                Material material = Material.matchMaterial(id.toString());
                if (material != null) values.add(clazz.cast(material));
            }
            return lunararcTag(tag, values);
        }
        if (org.bukkit.Tag.REGISTRY_BLOCKS.equals(registry)) {
            if (clazz != Material.class) throw new IllegalArgumentException("Block tags require Material values");
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, location);
            var holders = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTag(key);
            if (holders.isEmpty()) return null;
            java.util.LinkedHashSet<T> values = new java.util.LinkedHashSet<>();
            for (var holder : holders.get()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(holder.value());
                Material material = Material.matchMaterial(id.toString());
                if (material != null) values.add(clazz.cast(material));
            }
            return lunararcTag(tag, values);
        }
        if (org.bukkit.Tag.REGISTRY_FLUIDS.equals(registry)) {
            if (clazz != org.bukkit.Fluid.class) throw new IllegalArgumentException("Fluid tags require Fluid values");
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, location);
            var holders = net.minecraft.core.registries.BuiltInRegistries.FLUID.getTag(key);
            if (holders.isEmpty()) return null;
            java.util.LinkedHashSet<T> values = new java.util.LinkedHashSet<>();
            for (var holder : holders.get()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(holder.value());
                try {
                    values.add(clazz.cast(org.bukkit.Fluid.valueOf(id.getPath().toUpperCase(java.util.Locale.ROOT))));
                } catch (IllegalArgumentException ignored) {

                }
            }
            return lunararcTag(tag, values);
        }
        if (org.bukkit.Tag.REGISTRY_ENTITY_TYPES.equals(registry)) {
            if (clazz != org.bukkit.entity.EntityType.class) throw new IllegalArgumentException("Entity tags require EntityType values");
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, location);
            var holders = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getTag(key);
            if (holders.isEmpty()) return null;
            java.util.LinkedHashSet<T> values = new java.util.LinkedHashSet<>();
            for (var holder : holders.get()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(holder.value());
                org.bukkit.entity.EntityType type = org.bukkit.Registry.ENTITY_TYPE.get(
                        new NamespacedKey(id.getNamespace(), id.getPath()));
                if (type != null) values.add(clazz.cast(type));
            }
            return lunararcTag(tag, values);
        }
        if (org.bukkit.Tag.REGISTRY_GAME_EVENTS.equals(registry)) {
            if (clazz != org.bukkit.GameEvent.class) throw new IllegalArgumentException("Game-event tags require GameEvent values");
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.GAME_EVENT, location);
            var holders = net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.getTag(key);
            if (holders.isEmpty()) return null;
            java.util.LinkedHashSet<T> values = new java.util.LinkedHashSet<>();
            Registry<org.bukkit.GameEvent> gameEvents = io.lunararcdevs.lunararc.common.server.LunarArcRegistryAccess.INSTANCE
                    .getRegistry(org.bukkit.GameEvent.class);
            for (var holder : holders.get()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.getKey(holder.value());
                org.bukkit.GameEvent event = gameEvents.get(new NamespacedKey(id.getNamespace(), id.getPath()));
                if (event != null) values.add(clazz.cast(event));
            }
            return lunararcTag(tag, values);
        }
        throw new IllegalArgumentException("Unsupported Bukkit tag registry: " + registry);
    }

    private static <T extends Keyed> Tag<T> lunararcTag(NamespacedKey key, java.util.Set<T> values) {
        java.util.Set<T> immutable = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(values));
        return new Tag<>() {
            @Override public @NotNull NamespacedKey getKey() { return key; }
            @Override public boolean isTagged(@NotNull T item) { return immutable.contains(item); }
            @Override public @NotNull java.util.Set<T> getValues() { return immutable; }
        };
    }

    @Override
    public @Nullable LootTable getLootTable(@NotNull NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        net.minecraft.server.ReloadableServerRegistries.Holder registries = this.getServer().reloadableRegistries();
        return registries.lookup().lookup(net.minecraft.core.registries.Registries.LOOT_TABLE)
                .flatMap(lookup -> lookup.get(org.bukkit.craftbukkit.CraftLootTable.bukkitKeyToMinecraft(key)))
                .map(holder -> (LootTable) new org.bukkit.craftbukkit.CraftLootTable(key, holder.value()))
                .orElse(null);
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull Material material) {
        return createBlockData(material, (Consumer<? super BlockData>) null);
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull Material material,
            @Nullable Consumer<? super BlockData> consumer) {
        Objects.requireNonNull(material, "material");
        BlockData blockData;
        if (material.isLegacy()) {
            blockData = org.bukkit.craftbukkit.block.data.CraftBlockData.create(
                    org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacyData(material, (byte) 0));
        } else {
            blockData = org.bukkit.craftbukkit.block.data.CraftBlockData.parse(material, null);
        }
        if (consumer != null) consumer.accept(blockData);
        return blockData;
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull String data) throws IllegalArgumentException {
        return org.bukkit.craftbukkit.block.data.CraftBlockData.parse(data);
    }

    @Override
    public @NotNull BlockData createBlockData(@Nullable Material material, @Nullable String data)
            throws IllegalArgumentException {
        if (material == null && data == null) throw new IllegalArgumentException("Material and data cannot both be null");
        if (material == null) return createBlockData(Objects.requireNonNull(data, "data"));
        return org.bukkit.craftbukkit.block.data.CraftBlockData.parse(material, data);
    }

    @Override
    public int getMaxWorldSize() {
        return dedicatedProperties().maxWorldSize;
    }

    private Advancement wrapAdvancement(net.minecraft.advancements.AdvancementHolder holder) {
        return holder == null ? null : new org.bukkit.craftbukkit.advancement.CraftAdvancement(holder);
    }

    @Override
    public @Nullable Advancement getAdvancement(@NotNull NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey());
        return wrapAdvancement(console.getAdvancements().get(id));
    }

    @Override
    public @NotNull Iterator<Advancement> advancementIterator() {
        List<Advancement> advancements = new ArrayList<>();
        for (net.minecraft.advancements.AdvancementHolder holder : console.getAdvancements().getAllAdvancements()) {
            advancements.add(wrapAdvancement(holder));
        }
        return Collections.unmodifiableList(advancements).iterator();
    }

    @Override
    public boolean removeBossBar(@NotNull NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        KeyedBossBar removed = bossBars.remove(key);
        if (removed != null) removed.removeAll();
        return removed != null;
    }

    @Override
    public @Nullable KeyedBossBar getBossBar(@NotNull NamespacedKey key) {
        return bossBars.get(Objects.requireNonNull(key, "key"));
    }

    @Override
    public @NotNull Iterator<KeyedBossBar> getBossBars() {
        return Collections.unmodifiableCollection(new ArrayList<>(bossBars.values())).iterator();
    }

    @Override
    public @NotNull KeyedBossBar createBossBar(@NotNull NamespacedKey key, @Nullable String title,
            @NotNull BarColor color, @NotNull BarStyle style, @NotNull BarFlag... flags) {
        Objects.requireNonNull(key, "key");
        if (bossBars.containsKey(key)) throw new IllegalArgumentException("Boss bar already exists: " + key);
        KeyedBossBar bar = io.lunararcdevs.lunararc.common.server.LunarArcBossBar.createKeyed(key, title, color, style, flags);
        bossBars.put(key, bar);
        return bar;
    }

    @Override
    public @NotNull BossBar createBossBar(@Nullable String title, @NotNull BarColor color, @NotNull BarStyle style,
            @NotNull BarFlag... flags) {
        return io.lunararcdevs.lunararc.common.server.LunarArcBossBar.create(title, color, style, flags);
    }

    @Override
    public int getSpawnLimit(@NotNull SpawnCategory category) {
        Objects.requireNonNull(category, "category");
        return switch (category) {
            case MONSTER -> bukkitConfig.getInt("spawn-limits.monsters", 70);
            case ANIMAL -> bukkitConfig.getInt("spawn-limits.animals", 10);
            case WATER_ANIMAL -> bukkitConfig.getInt("spawn-limits.water-animals", 5);
            case WATER_AMBIENT -> bukkitConfig.getInt("spawn-limits.water-ambient", 20);
            case WATER_UNDERGROUND_CREATURE -> bukkitConfig.getInt("spawn-limits.water-underground-creature", 5);
            case AXOLOTL -> bukkitConfig.getInt("spawn-limits.axolotls", 5);
            case AMBIENT -> bukkitConfig.getInt("spawn-limits.ambient", 15);
            case MISC -> -1;
        };
    }

    @Override
    public int getTicksPerSpawns(@NotNull SpawnCategory category) {
        Objects.requireNonNull(category, "category");
        return switch (category) {
            case MONSTER -> getTicksPerMonsterSpawns();
            case ANIMAL -> getTicksPerAnimalSpawns();
            case WATER_ANIMAL -> getTicksPerWaterSpawns();
            case WATER_AMBIENT -> getTicksPerWaterAmbientSpawns();
            case WATER_UNDERGROUND_CREATURE -> getTicksPerWaterUndergroundCreatureSpawns();
            case AXOLOTL -> bukkitConfig.getInt("ticks-per.axolotl-spawns", 1);
            case AMBIENT -> getTicksPerAmbientSpawns();
            case MISC -> -1;
        };
    }

    @Override
    public int getTicksPerAnimalSpawns() {
        return bukkitConfig.getInt("ticks-per.animal-spawns", 400);
    }

    @Override
    public int getTicksPerMonsterSpawns() {
        return bukkitConfig.getInt("ticks-per.monster-spawns", 1);
    }

    @Override
    public int getTicksPerWaterSpawns() {
        return bukkitConfig.getInt("ticks-per.water-spawns", 1);
    }

    @Override
    public int getTicksPerWaterAmbientSpawns() {
        return bukkitConfig.getInt("ticks-per.water-ambient-spawns", 1);
    }

    @Override
    public int getTicksPerWaterUndergroundCreatureSpawns() {
        return bukkitConfig.getInt("ticks-per.water-underground-creature-spawns", 1);
    }

    @Override
    public int getTicksPerAmbientSpawns() {
        return bukkitConfig.getInt("ticks-per.ambient-spawns", 1);
    }

    @Override
    public @NotNull PlayerProfile createPlayerProfile(@Nullable UUID uniqueId, @Nullable String name) {
        return new io.lunararcdevs.lunararc.common.server.LunarArcPlayerProfile(uniqueId, name);
    }

    @Override
    public @NotNull PlayerProfile createPlayerProfile(@NotNull UUID uniqueId) {
        String name = null;
        try {
            var profile = console.getProfileCache().get(uniqueId);
            if (profile.isPresent()) name = profile.get().getName();
        } catch (Throwable ignored) {}
        return new io.lunararcdevs.lunararc.common.server.LunarArcPlayerProfile(uniqueId, name);
    }

    @Override
    public @NotNull PlayerProfile createPlayerProfile(@NotNull String name) {
        UUID id = getPlayerUniqueId(name);
        return new io.lunararcdevs.lunararc.common.server.LunarArcPlayerProfile(id, name);
    }

    @Override
    public @Nullable UUID getPlayerUniqueId(@NotNull String name) {

        Player online = getPlayer(name);
        if (online != null) return online.getUniqueId();

        try {
            var profile = console.getProfileCache().get(name);
            if (profile.isPresent()) return profile.get().getId();
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public ChunkGenerator.@NotNull ChunkData createChunkData(@NotNull World world) {
        Objects.requireNonNull(world, "world");
        return new org.bukkit.craftbukkit.generator.CraftChunkData(
                world.getMinHeight(), world.getMaxHeight());
    }

    private static long chunkDataKey(int x, int y, int z) {
        return ((long) (x & 15) << 40) | ((long) (z & 15) << 32) | (y & 0xffffffffL);
    }

    private static org.bukkit.block.data.BlockData chunkDataBlockData(Object value) {
        if (value instanceof org.bukkit.block.data.BlockData data) return data;
        if (value instanceof Material material) return Bukkit.createBlockData(material);
        if (value instanceof String text) return Bukkit.createBlockData(text);
        return null;
    }

    @Override
    public @NotNull Map<String, String[]> getCommandAliases() {
        Map<String, String[]> aliases = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection section = commandsConfig.getConfigurationSection("aliases");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof java.util.List<?> list) {
                    aliases.put(key, list.stream().map(String::valueOf).toArray(String[]::new));
                } else if (value instanceof String string) {
                    aliases.put(key, new String[] { string });
                }
            }
        }
        return Collections.unmodifiableMap(aliases);
    }

    private io.lunararcdevs.lunararc.common.bridge.recipe.RecipeManagerBridge recipeManagerBridge() {
        Object manager = console.getRecipeManager();
        if (!(manager instanceof io.lunararcdevs.lunararc.common.bridge.recipe.RecipeManagerBridge bridge)) {
            throw new IllegalStateException("RecipeManagerMixin is not active on the loader-owned RecipeManager");
        }
        return bridge;
    }

    @Override
    public boolean addRecipe(@Nullable Recipe recipe) {
        return addRecipe(recipe, true);
    }

    @Override
    public boolean addRecipe(@Nullable Recipe recipe, boolean update) {
        if (recipe == null) return false;
        net.minecraft.world.item.crafting.RecipeHolder<?> nms =
                org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toMinecraft(recipe);
        boolean added = recipeManagerBridge().lunararc$addRecipe(nms);
        if (added && update) updateRecipes();
        return added;
    }

    @Override
    public boolean removeRecipe(@NotNull NamespacedKey key) {
        return removeRecipe(key, true);
    }

    @Override
    public boolean removeRecipe(@NotNull NamespacedKey key, boolean update) {
        Objects.requireNonNull(key, "key");
        boolean removed = recipeManagerBridge().lunararc$removeRecipe(
                org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toMinecraft(key));
        if (removed && update) updateRecipes();
        return removed;
    }

    @Override
    public @Nullable Recipe getRecipe(@NotNull NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        net.minecraft.world.item.crafting.RecipeHolder<?> holder = recipeManagerBridge().lunararc$recipe(
                org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toMinecraft(key));
        return holder == null ? null : org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toBukkit(holder);
    }

    @Override
    public @NotNull List<Recipe> getRecipesFor(@NotNull ItemStack result) {
        Objects.requireNonNull(result, "result");
        List<Recipe> matches = new ArrayList<>();
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipeManagerBridge().lunararc$recipes()) {
            Recipe recipe = org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toBukkit(holder);
            ItemStack recipeResult = recipe.getResult();
            if (recipeResult != null && recipeResult.isSimilar(result)) matches.add(recipe);
        }
        return Collections.unmodifiableList(matches);
    }

    @Override
    public @NotNull Iterator<Recipe> recipeIterator() {
        final List<net.minecraft.world.item.crafting.RecipeHolder<?>> snapshot =
                new ArrayList<>(recipeManagerBridge().lunararc$recipes());
        final Iterator<net.minecraft.world.item.crafting.RecipeHolder<?>> iterator = snapshot.iterator();
        return new Iterator<>() {
            private net.minecraft.world.item.crafting.RecipeHolder<?> current;

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Recipe next() {
                current = iterator.next();
                return org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toBukkit(current);
            }

            @Override
            public void remove() {
                if (current == null) throw new IllegalStateException("next() has not been called");
                if (recipeManagerBridge().lunararc$removeRecipe(current.id())) updateRecipes();
                current = null;
            }
        };
    }

    @Override
    public void clearRecipes() {
        recipeManagerBridge().lunararc$clearRecipes();
        updateRecipes();
    }

    @Override
    public void resetRecipes() {

        reloadData();
    }

    @Override
    public void updateResources() {

        reloadData();
    }

    @Override
    public void updateRecipes() {

        if (playerList instanceof io.lunararcdevs.lunararc.common.bridge.PlayerListBridge bridge) {
            bridge.lunararc$reloadRecipeData();
            return;
        }
        throw new IllegalStateException("PlayerList recipe sync bridge is not active");
    }

    private record LunarArcCraftingContext(
            CraftWorld world,
            net.minecraft.world.inventory.CraftingContainer container,
            java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> recipe) {}

    private LunarArcCraftingContext craftingContext(ItemStack[] items, World world) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(world, "world");
        if (items.length != 9) throw new IllegalArgumentException("Crafting matrix must contain exactly 9 items");
        if (!(world instanceof CraftWorld craftWorld)) {
            throw new IllegalArgumentException("World is not owned by LunarArc: " + world.getClass().getName());
        }

        net.minecraft.world.inventory.AbstractContainerMenu menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return false; }
            @Override public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
        };
        net.minecraft.world.inventory.CraftingContainer crafting =
                new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < items.length; i++) {
            crafting.setItem(i, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(items[i]));
        }
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> match =
                console.getRecipeManager().getRecipeFor(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                        crafting.asCraftInput(), craftWorld.getHandle());
        return new LunarArcCraftingContext(craftWorld, crafting, match);
    }

    @Override
    public @NotNull org.bukkit.inventory.ItemCraftResult craftItemResult(@NotNull ItemStack[] items,
            @NotNull World world) {
        return craftItemResult(items, world, null);
    }

    @Override
    public @NotNull org.bukkit.inventory.ItemCraftResult craftItemResult(@NotNull ItemStack[] items,
            @NotNull World world, @Nullable Player player) {
        if (player == null) {
            LunarArcCraftingContext context = craftingContext(items, world);
            net.minecraft.world.item.ItemStack result = context.recipe()
                    .map(holder -> holder.value().assemble(context.container().asCraftInput(),
                            context.world().getHandle().registryAccess()))
                    .orElse(net.minecraft.world.item.ItemStack.EMPTY);
            return createItemCraftResult(result, context.container(), context.world().getHandle());
        }

        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(world, "world");
        if (items.length != 9) throw new IllegalArgumentException("Crafting matrix must contain exactly 9 items");
        if (!(world instanceof CraftWorld craftWorld)) {
            throw new IllegalArgumentException("World is not owned by LunarArc: " + world.getClass().getName());
        }
        if (!(player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer)) {
            throw new IllegalArgumentException("Player is not owned by LunarArc: " + player.getClass().getName());
        }

        net.minecraft.world.inventory.CraftingMenu menu = new net.minecraft.world.inventory.CraftingMenu(
                -1, craftPlayer.getHandle().getInventory());
        net.minecraft.world.inventory.CraftingContainer crafting = menu.craftSlots;
        net.minecraft.world.inventory.ResultContainer resultContainer = menu.resultSlots;
        for (int i = 0; i < items.length; i++) {
            crafting.setItem(i, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(items[i]));
        }

        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> recipe =
                console.getRecipeManager().getRecipeFor(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                        crafting.asCraftInput(), craftWorld.getHandle());
        net.minecraft.world.item.ItemStack initial = recipe
                .map(holder -> holder.value().assemble(crafting.asCraftInput(), craftWorld.getHandle().registryAccess()))
                .orElse(net.minecraft.world.item.ItemStack.EMPTY);
        resultContainer.setItem(0, initial.copy());

        org.bukkit.craftbukkit.inventory.CraftInventoryCrafting bukkitCrafting =
                new org.bukkit.craftbukkit.inventory.CraftInventoryCrafting(
                        crafting, resultContainer, craftPlayer, recipe.orElse(null));
        org.bukkit.craftbukkit.inventory.CraftInventoryView view =
                new org.bukkit.craftbukkit.inventory.CraftInventoryView(
                        craftPlayer, menu, bukkitCrafting, craftPlayer.getInventory(),
                        org.bukkit.event.inventory.InventoryType.WORKBENCH,
                        net.kyori.adventure.text.Component.translatable("container.crafting"));
        boolean repair = recipe.map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .filter(net.minecraft.world.item.crafting.RepairItemRecipe.class::isInstance).isPresent();
        org.bukkit.event.inventory.PrepareItemCraftEvent event =
                new org.bukkit.event.inventory.PrepareItemCraftEvent(bukkitCrafting, view, repair);
        this.pluginManager.callEvent(event);

        net.minecraft.world.item.ItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack
                .asNMSCopy(bukkitCrafting.getResult());
        return createItemCraftResult(result, crafting, craftWorld.getHandle());
    }

    private org.bukkit.craftbukkit.inventory.CraftItemCraftResult createItemCraftResult(
            net.minecraft.world.item.ItemStack result,
            net.minecraft.world.inventory.CraftingContainer crafting,
            net.minecraft.server.level.ServerLevel world) {
        org.bukkit.craftbukkit.inventory.CraftItemCraftResult craftResult =
                new org.bukkit.craftbukkit.inventory.CraftItemCraftResult(
                        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(result));
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> remaining =
                console.getRecipeManager().getRemainingItemsFor(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                        crafting.asCraftInput(), world);
        for (int i = 0; i < remaining.size(); i++) {
            net.minecraft.world.item.ItemStack original = crafting.getItem(i).copy();
            net.minecraft.world.item.ItemStack remainder = remaining.get(i);
            if (!original.isEmpty()) original.shrink(1);
            if (!remainder.isEmpty()) {
                if (original.isEmpty()) {
                    original = remainder.copy();
                } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(original, remainder)) {
                    original.grow(remainder.getCount());
                } else {
                    craftResult.getOverflowItems().add(
                            org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(remainder));
                }
            }
            craftResult.setResultMatrix(i,
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(original));
        }
        return craftResult;
    }

    @Override
    public @Nullable ItemStack craftItem(@NotNull ItemStack[] items, @NotNull World world) {
        return craftItemResult(items, world).getResult();
    }

    @Override
    public @Nullable ItemStack craftItem(@NotNull ItemStack[] items, @NotNull World world, @Nullable Player player) {
        return craftItemResult(items, world, player).getResult();
    }

    @Override
    public @Nullable Recipe getCraftingRecipe(@NotNull ItemStack[] items, @NotNull World world) {
        LunarArcCraftingContext context = craftingContext(items, world);
        return context.recipe().<Recipe>map(
                org.bukkit.craftbukkit.inventory.CraftRecipeAdapter::toBukkit).orElse(null);
    }

    @Override
    public @NotNull World createWorld(@NotNull WorldCreator creator) {
        Objects.requireNonNull(creator, "creator");
        if (!isPrimaryThread()) {
            throw new IllegalStateException("World creation must occur on the primary server thread");
        }
        World existing = getWorld(creator.name());
        if (existing != null) return existing;
        if (!console.getAllLevels().iterator().hasNext()) {
            throw new IllegalStateException("Cannot create additional worlds before the primary world is initialized");
        }
        final String name = creator.name();
        final java.io.File folder = new java.io.File(getWorldContainer(), name);
        if (folder.exists() && !folder.isDirectory()) {
            throw new IllegalArgumentException("File (" + folder + ") exists and isn't a folder");
        }

        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.dimension.LevelStem> stemKey =
                io.lunararcdevs.lunararc.common.server.LunarArcDynamicBukkitEnums.levelStem(creator.environment());
        if (stemKey == null) {
            throw new IllegalArgumentException("No loader-owned level stem is registered for Bukkit environment " + creator.environment());
        }

        final io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge serverBridge =
                (io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) (Object) console;
        final net.minecraft.server.WorldLoader.DataLoadContext context = serverBridge.lunararc$getDataLoadContext();
        final net.minecraft.core.RegistryAccess.Frozen dimensions =
                console.registries().getLayer(net.minecraft.server.RegistryLayer.DIMENSIONS);
        final net.minecraft.core.Registry<net.minecraft.world.level.dimension.LevelStem> stems =
                dimensions.registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
        final net.minecraft.world.level.dimension.LevelStem stem = stems.get(stemKey);
        if (stem == null) {
            throw new IllegalArgumentException("The running loader has no level stem for " + stemKey.location());
        }

        final net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess session;
        try {
            net.minecraft.world.level.storage.LevelStorageSource storage =
                    net.minecraft.world.level.storage.LevelStorageSource.createDefault(getWorldContainer().toPath());
            session = ((io.lunararcdevs.lunararc.common.bridge.storage.LevelStorageSourceBridge) (Object) storage)
                    .lunararc$validateAndCreateAccess(name, stemKey);
        } catch (java.io.IOException | net.minecraft.world.level.validation.ContentValidationException ex) {
            throw new IllegalStateException("Unable to open world storage for " + name, ex);
        }

        net.minecraft.world.level.storage.PrimaryLevelData levelData;
        net.minecraft.server.level.ServerLevel createdLevel = null;
        CraftWorld createdWorld = null;
        try {
            if (session.hasWorldData()) {
                com.mojang.serialization.Dynamic<?> dynamic;
                net.minecraft.world.level.storage.LevelSummary summary;
                try {
                    dynamic = session.getDataTag();
                    summary = session.getSummary(dynamic);
                } catch (net.minecraft.nbt.NbtException | net.minecraft.nbt.ReportedNbtException | java.io.IOException first) {
                    try {
                        dynamic = session.getDataTagFallback();
                        summary = session.getSummary(dynamic);
                        session.restoreLevelDataFromOld();
                    } catch (java.io.IOException fallbackFailure) {
                        fallbackFailure.addSuppressed(first);
                        throw new IllegalStateException("Unable to read primary or fallback world data for " + name, fallbackFailure);
                    }
                }
                if (summary.requiresManualConversion()) {
                    throw new IllegalStateException("World " + name + " requires manual conversion by an older Minecraft version");
                }
                if (!summary.isCompatible()) {
                    throw new IllegalStateException("World " + name + " was created by an incompatible Minecraft version");
                }
                net.minecraft.world.level.storage.LevelDataAndDimensions loaded =
                        net.minecraft.world.level.storage.LevelStorageSource.getLevelDataAndDimensions(
                                dynamic, context.dataConfiguration(),
                                context.datapackDimensions().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM),
                                context.datapackWorldgen());
                levelData = (net.minecraft.world.level.storage.PrimaryLevelData) loaded.worldData();
            } else {
                net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                        name, net.minecraft.world.level.GameType.byId(getDefaultGameMode().getValue()), creator.hardcore(),
                        net.minecraft.world.Difficulty.EASY, false, new net.minecraft.world.level.GameRules(), context.dataConfiguration());
                net.minecraft.world.level.levelgen.WorldOptions options = new net.minecraft.world.level.levelgen.WorldOptions(
                        creator.seed(), creator.generateStructures(), false);
                net.minecraft.world.level.storage.WorldData template = console.getWorldData();
                net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty property = template.isDebugWorld()
                        ? net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty.DEBUG
                        : template.isFlatWorld()
                                ? net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty.FLAT
                                : net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty.NONE;
                levelData = new net.minecraft.world.level.storage.PrimaryLevelData(
                        settings, options, property, template.worldGenSettingsLifecycle());
            }

            long seed = levelData.worldGenOptions().seed();
            long biomeSeed = net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(seed);
            java.util.List<net.minecraft.world.level.CustomSpawner> spawners = creator.environment() == World.Environment.NORMAL
                    ? java.util.List.of(
                            new net.minecraft.world.level.levelgen.PhantomSpawner(),
                            new net.minecraft.world.level.levelgen.PatrolSpawner(),
                            new net.minecraft.world.entity.npc.CatSpawner(),
                            new net.minecraft.world.entity.ai.village.VillageSiege(),
                            new net.minecraft.world.entity.npc.WanderingTraderSpawner(levelData))
                    : java.util.List.of();

            String mainLevelName = dedicatedProperties().levelName;
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> worldKey;
            if (name.equals(mainLevelName + "_nether")) {
                worldKey = net.minecraft.world.level.Level.NETHER;
            } else if (name.equals(mainLevelName + "_the_end")) {
                worldKey = net.minecraft.world.level.Level.END;
            } else {
                worldKey = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                creator.key().getNamespace(), creator.key().getKey()));
            }
            if (((io.lunararcdevs.lunararc.common.bridge.access.MinecraftServerAccessBridge) (Object) console).lunararc$getLevels().containsKey(worldKey)) {
                throw new IllegalArgumentException("A world is already registered with key " + worldKey.location());
            }

            if (creator.keepSpawnLoaded() == net.kyori.adventure.util.TriState.FALSE) {
                levelData.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
            }

            net.minecraft.server.level.progress.ChunkProgressListener listener =
                    console.progressListenerFactory
                            .create(levelData.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_SPAWN_CHUNK_RADIUS));

            org.bukkit.generator.ChunkGenerator bukkitGenerator = creator.generator();
            org.bukkit.generator.BiomeProvider bukkitBiomeProvider = creator.biomeProvider();
            net.minecraft.world.level.dimension.LevelStem effectiveStem = stem;
            org.bukkit.craftbukkit.generator.CustomChunkGenerator customGenerator = null;
            if (bukkitGenerator != null || bukkitBiomeProvider != null) {
                // Same folder CraftWorld will use once the level exists, so the generator is told
                // the identity the world actually ends up with rather than one from a folder named
                // after it.
                java.util.UUID generationUid = org.bukkit.craftbukkit.CraftWorld.loadOrCreateWorldUid(
                        session.getDimensionPath(worldKey), name, worldKey.location().toString());
                net.minecraft.world.level.dimension.DimensionType dimensionType = stem.type().value();
                org.bukkit.craftbukkit.generator.CraftWorldInfo worldInfo =
                        new org.bukkit.craftbukkit.generator.CraftWorldInfo(
                                name, generationUid, creator.environment(), seed,
                                dimensionType.minY(), dimensionType.minY() + dimensionType.height(),
                                stem.generator(), console.registryAccess(), levelData.enabledFeatures());
                if (bukkitBiomeProvider == null && bukkitGenerator != null) {
                    bukkitBiomeProvider = bukkitGenerator.getDefaultBiomeProvider(worldInfo);
                }
                net.minecraft.world.level.biome.BiomeSource effectiveBiomeSource = stem.generator().getBiomeSource();
                if (bukkitBiomeProvider != null) {
                    effectiveBiomeSource = new org.bukkit.craftbukkit.generator.CustomWorldChunkManager(
                            worldInfo, bukkitBiomeProvider,
                            console.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME),
                            stem.generator().getBiomeSource());
                }
                customGenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(
                        stem.generator(), bukkitGenerator, worldInfo, effectiveBiomeSource);
                effectiveStem = new net.minecraft.world.level.dimension.LevelStem(stem.type(), customGenerator);
            }

            createdLevel = new net.minecraft.server.level.ServerLevel(
                    console, console, session, levelData, worldKey, effectiveStem, listener,
                    levelData.isDebugWorld(), biomeSeed, spawners, true, console.overworld().getRandomSequences());
            if (customGenerator != null) customGenerator.attachWorld(createdLevel);

            serverBridge.lunararc$addLevel(createdLevel);
            createdWorld = registerDynamicWorld(createdLevel, name, bukkitGenerator, bukkitBiomeProvider);
            serverBridge.lunararc$initializeDynamicLevel(createdLevel, levelData, false);
            serverBridge.lunararc$prepareDynamicLevel(createdLevel, listener);
            worldLoadEventFired.add(createdLevel.dimension());
            simplePluginManager.callEvent(new org.bukkit.event.world.WorldLoadEvent(createdWorld));
            return createdWorld;
        } catch (RuntimeException ex) {
            if (createdLevel != null) {
                try { serverBridge.lunararc$removeLevel(createdLevel); } catch (Throwable ignored) {}
                worldByDimension.remove(createdLevel.dimension());
            }
            if (createdWorld != null) {
                worlds.remove(createdWorld.getName(), createdWorld);
                worldCache.remove(createdWorld.getUID(), createdWorld);
                worldCache.remove(createdWorld.getLegacyDimensionUID(), createdWorld);
            }
            try { session.close(); } catch (Throwable ignored) {}
            throw ex;
        }
    }

    @Override
    public boolean unloadWorld(@NotNull String name, boolean save) {
        World world = getWorld(Objects.requireNonNull(name, "name"));
        return world != null && unloadWorld(world, save);
    }

    @Override
    public boolean unloadWorld(@NotNull World world, boolean save) {
        Objects.requireNonNull(world, "world");
        net.minecraft.server.level.ServerLevel level = null;
        for (net.minecraft.server.level.ServerLevel candidate : console.getAllLevels()) {
            if (craftWorld(candidate).getUID().equals(world.getUID())) { level = candidate; break; }
        }
        if (level == null || level.dimension() == net.minecraft.world.level.Level.OVERWORLD) return false;
        if (!world.getPlayers().isEmpty()) return false;

        org.bukkit.event.world.WorldUnloadEvent unloadEvent = new org.bukkit.event.world.WorldUnloadEvent(world);
        this.simplePluginManager.callEvent(unloadEvent);
        if (unloadEvent.isCancelled()) return false;

        if (save) {
            try { level.save(null, true, false); }
            catch (Throwable ex) { logger.log(java.util.logging.Level.WARNING, "Failed to save world " + world.getName(), ex); return false; }
        }
        io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge serverBridge =
                (io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) (Object) console;
        serverBridge.lunararc$removeLevel(level);
        if (((io.lunararcdevs.lunararc.common.bridge.access.MinecraftServerAccessBridge) (Object) console).lunararc$getLevels().containsKey(level.dimension())) {
            return false;
        }
        worldCache.remove(world.getUID());
        if (world instanceof CraftWorld craft) {
            worldCache.remove(craft.getLegacyDimensionUID());
        }
        worlds.remove(world.getName(), world);
        worldByDimension.remove(level.dimension());
        return true;
    }

    @Override
    public @NotNull WorldBorder createWorldBorder() {
        return new CraftWorldBorder(new net.minecraft.world.level.border.WorldBorder());
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public @NotNull ItemStack createExplorerMap(@NotNull World world, @NotNull Location location,
            @NotNull org.bukkit.StructureType structureType) {
        return createExplorerMap(world, location, structureType, 100, true);
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public @NotNull ItemStack createExplorerMap(@NotNull World world, @NotNull Location location,
            @NotNull org.bukkit.StructureType structureType, int radius, boolean findUnexplored) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(structureType, "structureType");
        ItemStack stack = new ItemStack(org.bukkit.Material.FILLED_MAP);
        try {
            MapView view = createMap(world);
            org.bukkit.inventory.meta.MapMeta meta = (org.bukkit.inventory.meta.MapMeta) stack.getItemMeta();
            if (meta != null) {
                meta.setMapView(view);
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {}
        return stack;
    }

    @Override
    public @NotNull ItemStack createExplorerMap(@NotNull World world, @NotNull Location location,
            @NotNull org.bukkit.generator.structure.StructureType structureType,
            org.bukkit.map.MapCursor.@NotNull Type mapCursorType, int radius, boolean findUnexplored) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(structureType, "structureType");
        Objects.requireNonNull(mapCursorType, "mapCursorType");
        ItemStack stack = new ItemStack(org.bukkit.Material.FILLED_MAP);
        try {
            MapView view = createMap(world);
            org.bukkit.inventory.meta.MapMeta meta = (org.bukkit.inventory.meta.MapMeta) stack.getItemMeta();
            if (meta != null) {
                meta.setMapView(view);
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {

        }
        return stack;
    }

    @Override
    // CraftScoreboardManager, not the ScoreboardManager interface: the donated CraftStatistic
    // calls getScoreboardManager().forAllObjectives(...) to keep scoreboard objectives in step
    // with a statistic, and forAllObjectives exists only on the Craft class. Declaring the
    // interface here made every incrementStatistic with a scoreboard-visible criterion throw
    // NoSuchMethodError - which is what VeinMiner hit on every block it mined.
    public @NotNull org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager getScoreboardManager() {
        return this.scoreboardManager;
    }

    @Override
    public @Nullable org.bukkit.packs.ResourcePack getServerResourcePack() {
        return this.getServer().getServerResourcePack()
                .map(org.bukkit.craftbukkit.packs.CraftResourcePack::new)
                .orElse(null);
    }

    public @NotNull FeatureFlagConfig getFeatureFlagConfig() {
        final java.util.Set<Object> enabled = java.util.Collections.unmodifiableSet(lunararcEnabledFeatureFlagIds());
        return new FeatureFlagConfig() {
            @Override public java.util.Set<Object> getFeatureFlags() { return enabled; }
            @Override public boolean isFeatureFlagEnabled(Object flag) {
                String id = lunararcFeatureFlagId(flag);
                if (id == null) return false;
                for (Object value : enabled) {
                    if (id.equals(lunararcFeatureFlagId(value))) return true;
                }
                return false;
            }
        };
    }

    private Set<Object> lunararcEnabledFeatureFlagIds() {
        java.util.LinkedHashSet<Object> flags = new java.util.LinkedHashSet<>();
        for (World world : getWorlds()) {
            if (!(world instanceof CraftWorld craftWorld)) continue;
            flags.addAll(io.papermc.paper.datapack.PaperDatapack.toBukkitFeatures(
                    craftWorld.getHandle().enabledFeatures()));
        }
        return flags;
    }

    private String lunararcFeatureFlagId(Object flag) {
        if (flag instanceof org.bukkit.Keyed keyed) return keyed.getKey().toString();
        return null;
    }
}
