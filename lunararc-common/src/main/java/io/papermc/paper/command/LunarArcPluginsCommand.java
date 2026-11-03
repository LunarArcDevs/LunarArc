package io.papermc.paper.command;

import com.google.common.collect.Lists;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.entrypoint.Entrypoint;
import io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler;
import io.papermc.paper.plugin.provider.PluginProvider;
import io.papermc.paper.plugin.provider.ProviderStatus;
import io.papermc.paper.plugin.provider.ProviderStatusHolder;
import io.papermc.paper.plugin.provider.type.paper.PaperPluginParent;
import io.papermc.paper.plugin.provider.type.spigot.SpigotPluginProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.List;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

/**
 * Paper's {@code /plugins} listing: Paper plugins and Bukkit (legacy plugin.yml) plugins are listed in
 * separate sections, each plugin coloured by its load state. Ported from Paper's PaperPluginsCommand.
 */
public final class LunarArcPluginsCommand extends BukkitCommand {
    public static final String DESCRIPTION = "Gets a list of plugins running on the server";

    private static final TextColor INFO_COLOR = TextColor.color(52, 159, 218);

    private static final Component SERVER_PLUGIN_INFO = Component.text("ℹ What is a server plugin?", INFO_COLOR)
            .append(asPlainComponents("""
                    Server plugins can add new behavior to your server!
                    You can find new plugins on Paper's plugin repository, Hangar.

                    https://hangar.papermc.io/
                    """));

    private static final Component LEGACY_PLUGIN_INFO = Component.text("ℹ What is a legacy plugin?", INFO_COLOR)
            .append(asPlainComponents("""
                    A legacy plugin is a plugin that was made on
                    very old unsupported versions of the game.

                    It is encouraged that you replace this plugin,
                    as they might not work in the future and may cause
                    performance issues.
                    """));

    private static final Component LEGACY_PLUGIN_STAR = Component.text('*', TextColor.color(255, 212, 42)).hoverEvent(LEGACY_PLUGIN_INFO);
    private static final Component INFO_ICON_START = Component.text("ℹ ", INFO_COLOR);
    private static final Component PLUGIN_TICK = Component.text("- ", NamedTextColor.DARK_GRAY);
    private static final Component PLUGIN_TICK_EMPTY = Component.text(" ");
    private static final Component INFO_ICON_SERVER_PLUGIN = INFO_ICON_START.hoverEvent(SERVER_PLUGIN_INFO)
            .clickEvent(ClickEvent.openUrl("https://docs.papermc.io/paper/adding-plugins"));

    public LunarArcPluginsCommand(final String name) {
        super(name);
        this.description = DESCRIPTION;
        this.usageMessage = "/plugins";
        this.setPermission("bukkit.command.plugins");
        this.setAliases(List.of("pl"));
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String commandLabel, @NotNull final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        final TreeMap<String, PluginProvider<?>> paperPlugins = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final TreeMap<String, PluginProvider<?>> spigotPlugins = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (final PluginProvider<?> provider : LaunchEntryPointHandler.INSTANCE.getStorage().get(Entrypoint.PLUGIN).getRegisteredProviders()) {
            final PluginMeta configuration = provider.getMeta();

            if (provider instanceof SpigotPluginProvider) {
                spigotPlugins.put(configuration.getDisplayName(), provider);
            } else if (provider instanceof PaperPluginParent.PaperServerPluginProvider) {
                paperPlugins.put(configuration.getDisplayName(), provider);
            }
        }

        if (paperPlugins.isEmpty() && spigotPlugins.isEmpty()) {
            // Paper's provider storage is empty (plugins were loaded without it): list what the plugin manager has instead.
            final TreeMap<String, Plugin> fallbackPaper = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final TreeMap<String, Plugin> fallbackBukkit = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                (plugin.getPluginMeta() instanceof PluginDescriptionFile ? fallbackBukkit : fallbackPaper).put(plugin.getName(), plugin);
            }
            final boolean both = !fallbackPaper.isEmpty() && !fallbackBukkit.isEmpty();
            sender.sendMessage(Component.text()
                    .append(INFO_ICON_SERVER_PLUGIN)
                    .append(Component.text("Server Plugins (%s):".formatted(fallbackPaper.size() + fallbackBukkit.size()), NamedTextColor.WHITE))
                    .build());
            if (!fallbackPaper.isEmpty()) sender.sendMessage(header("Paper Plugins", 0x0288D1, fallbackPaper.size(), both));
            for (final Component component : formatPlugins(fallbackPaper)) sender.sendMessage(component);
            if (!fallbackBukkit.isEmpty()) sender.sendMessage(header("Bukkit Plugins", 0xED8106, fallbackBukkit.size(), both));
            for (final Component component : formatPlugins(fallbackBukkit)) sender.sendMessage(component);
            return true;
        }

        final int sizePaperPlugins = paperPlugins.size();
        final int sizeSpigotPlugins = spigotPlugins.size();
        final int sizePlugins = sizePaperPlugins + sizeSpigotPlugins;
        final boolean hasAllPluginTypes = sizePaperPlugins > 0 && sizeSpigotPlugins > 0;

        sender.sendMessage(Component.text()
                .append(INFO_ICON_SERVER_PLUGIN)
                .append(Component.text("Server Plugins (%s):".formatted(sizePlugins), NamedTextColor.WHITE))
                .build());

        if (!paperPlugins.isEmpty()) {
            sender.sendMessage(header("Paper Plugins", 0x0288D1, sizePaperPlugins, hasAllPluginTypes));
        }
        for (final Component component : formatProviders(paperPlugins)) {
            sender.sendMessage(component);
        }

        if (!spigotPlugins.isEmpty()) {
            sender.sendMessage(header("Bukkit Plugins", 0xED8106, sizeSpigotPlugins, hasAllPluginTypes));
        }
        for (final Component component : formatProviders(spigotPlugins)) {
            sender.sendMessage(component);
        }

        return true;
    }

    private static List<Component> formatPlugins(final TreeMap<String, Plugin> plugins) {
        final List<Component> components = new ArrayList<>(plugins.size());
        for (final Plugin plugin : plugins.values()) {
            components.add(Component.text(plugin.getName(), plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/version " + plugin.getName())));
        }
        final List<Component> lines = new ArrayList<>();
        boolean isFirst = true;
        for (final List<Component> sublist : Lists.partition(components, 10)) {
            Component line = isFirst ? Component.space().append(PLUGIN_TICK) : PLUGIN_TICK_EMPTY;
            isFirst = false;
            lines.add(line.append(Component.join(JoinConfiguration.commas(true), sublist)));
        }
        return lines;
    }

    private static List<Component> formatProviders(final TreeMap<String, PluginProvider<?>> plugins) {
        final List<Component> components = new ArrayList<>(plugins.size());
        for (final PluginProvider<?> entry : plugins.values()) {
            components.add(formatProvider(entry));
        }

        boolean isFirst = true;
        final List<Component> formattedSubLists = new ArrayList<>();
        // Split up the plugin list for each 10 plugins to get size down
        for (final List<Component> componentSublist : Lists.partition(components, 10)) {
            Component component = Component.space();
            if (isFirst) {
                component = component.append(PLUGIN_TICK);
                isFirst = false;
            } else {
                component = PLUGIN_TICK_EMPTY;
            }

            formattedSubLists.add(component.append(Component.join(JoinConfiguration.commas(true), componentSublist)));
        }

        return formattedSubLists;
    }

    private static Component formatProvider(final PluginProvider<?> provider) {
        final TextComponent.Builder builder = Component.text();
        if (provider instanceof SpigotPluginProvider && provider.getMeta() instanceof final PluginDescriptionFile description
                && CraftMagicNumbers.isLegacy(description)) {
            builder.append(LEGACY_PLUGIN_STAR);
        }

        final String name = provider.getMeta().getName();
        builder.append(Component.text(name, fromStatus(provider)).clickEvent(ClickEvent.runCommand("/version " + name)));
        return builder.build();
    }

    private static Component header(final String header, final int color, final int count, final boolean showSize) {
        final TextComponent.Builder componentHeader = Component.text().color(TextColor.color(color))
                .append(Component.text(header));

        if (showSize) {
            componentHeader.appendSpace().append(Component.text("(" + count + ")"));
        }

        return componentHeader.append(Component.text(":")).build();
    }

    private static Component asPlainComponents(final String strings) {
        final TextComponent.Builder builder = Component.text();
        for (final String string : strings.split("\n")) {
            builder.append(Component.newline());
            builder.append(Component.text(string, NamedTextColor.WHITE));
        }
        return builder.build();
    }

    private static TextColor fromStatus(final PluginProvider<?> provider) {
        if (provider instanceof final ProviderStatusHolder statusHolder && statusHolder.getLastProvidedStatus() != null) {
            final ProviderStatus status = statusHolder.getLastProvidedStatus();

            // Handle enabled/disabled game plugins
            if (status == ProviderStatus.INITIALIZED) {
                final Plugin plugin = Bukkit.getPluginManager().getPlugin(provider.getMeta().getName());
                // Plugin doesn't exist? Could be due to it being removed.
                if (plugin == null) {
                    return NamedTextColor.RED;
                }

                return plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;
            }

            return NamedTextColor.RED;
        } else if (provider instanceof final PaperPluginParent.PaperServerPluginProvider serverPluginProvider
                && serverPluginProvider.shouldSkipCreation()) {
            // Paper plugins are skipped if their provider was skipped because its initializer failed.
            return NamedTextColor.RED;
        }
        // A provider that failed to load (dependency issues and so on).
        return NamedTextColor.RED;
    }
}
