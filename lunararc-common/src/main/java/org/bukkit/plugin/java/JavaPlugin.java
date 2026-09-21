package org.bukkit.plugin.java;

import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;


public abstract class JavaPlugin extends PluginBase implements org.bukkit.command.TabExecutor {
    private boolean isEnabled = false;
    protected org.bukkit.plugin.PluginLoader loader;
    protected Server server;
    protected PluginDescriptionFile description;
    protected File dataFolder;
    protected File file;
    protected ClassLoader classLoader;
    protected PluginMeta pluginMeta;
    protected Logger logger;
    private boolean naggable = true;
    private FileConfiguration newConfig;
    private File configFile;
    private io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin> lifecycleManager;
    private boolean allowsLifecycleRegistration = true;

    protected JavaPlugin() {
        if (this.getClass().getClassLoader()
                instanceof io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader configuredPluginClassLoader) {
            configuredPluginClassLoader.init(this);
        } else {
            throw new IllegalStateException("JavaPlugin requires to be created by a valid classloader.");
        }
    }

    /**
     * Returns the plugin jar file. Bukkit/Spigot expose this to subclasses as a
     * protected final method, so the inherited binary contract is preserved here.
     */
    protected final @NotNull File getFile() {
        if (file == null) {
            throw new IllegalStateException("Plugin has not been initialized");
        }
        return file;
    }

    @Override
    public final @NotNull File getDataFolder() {
        return dataFolder;
    }

    @Override
    public final @NotNull PluginDescriptionFile getDescription() {
        if (description == null) {
            throw new IllegalStateException("Plugin has not been initialized");
        }
        return description;
    }

    @Override
    public @NotNull FileConfiguration getConfig() {
        if (newConfig == null) {
            reloadConfig();
        }
        return newConfig;
    }

    @Override
    public void reloadConfig() {
        if (configFile == null) {
            throw new IllegalStateException("Plugin configuration is not available before plugin initialization");
        }
        newConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (Exception ex) {
            logger.severe("Could not save config to " + configFile);
        }
    }

    @Override
    public void saveDefaultConfig() {
        if (configFile == null) {
            throw new IllegalStateException("Plugin configuration is not available before plugin initialization");
        }
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }

    @Override
    public void saveResource(@NotNull String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.equals("")) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        File outFile = new File(dataFolder, resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(dataFolder, resourcePath.substring(0, lastIndex >= 0 ? lastIndex : 0));

        if (!outDir.exists() && !outDir.mkdirs() && !outDir.isDirectory()) {
            throw new IllegalStateException("Could not create plugin data directory " + outDir);
        }

        try (InputStream in = getResource(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found");
            }
            if (!outFile.exists() || replace) {
                try (OutputStream out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            }
        } catch (IOException ex) {
            logger.log(java.util.logging.Level.SEVERE,
                    "Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

    @Override
    public @Nullable InputStream getResource(@NotNull String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    @Override
    public final @NotNull PluginLoader getPluginLoader() {
        return loader;
    }

    @Override
    public final @NotNull Server getServer() {
        return server;
    }

    @Override
    public final boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
    }

    public void setEnabled(boolean enabled) {
        if (isEnabled != enabled) {
            isEnabled = enabled;
            if (isEnabled) {
                logger.fine("Enabling " + getDescription().getFullName());
                try {
                    onEnable();
                } finally {
                    this.allowsLifecycleRegistration = false;
                }
            } else {
                logger.fine("Disabling " + getDescription().getFullName());
                try {
                    onDisable();
                } catch (Throwable t) {


                    logger.log(java.util.logging.Level.SEVERE,
                            "Error in onDisable() for " + getDescription().getFullName(), t);
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        return null;
    }


    /**
     * Bukkit declares this on JavaPlugin and plugins call it - ProtocolLib does so in onLoad, and
     * without it got NoSuchMethodError before it could initialize. It was simply missing here.
     */
    @NotNull
    protected final ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public final void init(@NotNull Server server, @NotNull PluginDescriptionFile description, @NotNull File dataFolder,
            @NotNull File file, @NotNull ClassLoader classLoader, @NotNull PluginMeta pluginMeta,
            @NotNull Logger logger) {
        this.server = server;
        this.description = description;
        this.dataFolder = dataFolder;
        this.file = file;
        this.classLoader = classLoader;
        this.pluginMeta = pluginMeta;
        this.logger = logger;
        this.configFile = new File(dataFolder, "config.yml");

        this.lifecycleManager = io.lunararcdevs.lunararc.common.server.LunarArcLifecycleEventManager.create(
                this, () -> this.allowsLifecycleRegistration);
        if (classLoader instanceof PluginClassLoader pluginClassLoader) {
            this.loader = pluginClassLoader.getPluginLoaderInstance();
        }
    }

    @NotNull
    public static <T extends JavaPlugin> T getPlugin(@NotNull Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        ClassLoader classLoader = clazz.getClassLoader();
        if (!(classLoader instanceof io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader configuredPluginClassLoader)) {
            throw new IllegalArgumentException(clazz + " is not initialized by a " + io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader.class);
        }
        JavaPlugin plugin = configuredPluginClassLoader.getPlugin();
        if (plugin == null) {
            throw new IllegalStateException("Cannot get plugin for " + clazz + " before it is initialized");
        }
        return clazz.cast(plugin);
    }

    @NotNull
    public static JavaPlugin getProvidingPlugin(@NotNull Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        ClassLoader classLoader = clazz.getClassLoader();
        if (!(classLoader instanceof io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader configuredPluginClassLoader)) {
            throw new IllegalArgumentException(clazz + " is not provided by a " + io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader.class);
        }
        JavaPlugin plugin = configuredPluginClassLoader.getPlugin();
        if (plugin == null) {
            throw new IllegalStateException("Cannot get providing plugin for " + clazz + " before it is initialized");
        }
        return plugin;
    }

    @Nullable
    public org.bukkit.command.PluginCommand getCommand(@NotNull String name) {
        String search = name.toLowerCase(java.util.Locale.ENGLISH);
        org.bukkit.command.Command command = server.getCommandMap().getCommand(search);
        if (command instanceof org.bukkit.command.PluginCommand pc && pc.getPlugin() == this) {
            return pc;
        }
        command = server.getCommandMap().getCommand(description.getName().toLowerCase(java.util.Locale.ENGLISH) + ":" + search);
        if (command instanceof org.bukkit.command.PluginCommand pc && pc.getPlugin() == this) {
            return pc;
        }

        if (description.getCommands() != null && description.getCommands().containsKey(search)) {

        }
        return null;
    }

    @Override
    public @NotNull PluginMeta getPluginMeta() {
        return pluginMeta;
    }

    public @Nullable String getAPIVersion() {
        return description.getAPIVersion();
    }

    public @NotNull List<String> getPluginLibraries() {
        List<String> libraries = description.getLibraries();
        return libraries == null || libraries.isEmpty() ? Collections.emptyList() : List.copyOf(libraries);
    }

    public @NotNull Logger getLogger() {
        if (logger == null) {
            return Logger.getLogger(description != null ? description.getName() : "UnknownPlugin");
        }
        return logger;
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return null;
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        return null;
    }

    public @NotNull org.slf4j.Logger getSLF4JLogger() {
        return org.slf4j.LoggerFactory.getLogger(description.getName());
    }

    public @NotNull net.kyori.adventure.text.logger.slf4j.ComponentLogger getComponentLogger() {
        return net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(description.getName());
    }

    public @NotNull io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin> getLifecycleManager() {
        return lifecycleManager;
    }

}
