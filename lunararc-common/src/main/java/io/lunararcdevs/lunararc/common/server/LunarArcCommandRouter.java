package io.lunararcdevs.lunararc.common.server;

import net.minecraft.commands.CommandSourceStack;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.logging.Level;


public final class LunarArcCommandRouter {

    @FunctionalInterface
    public interface PlatformCommandHook {
        HookResult apply(CraftServer server, CommandSender sender, String commandLine);
    }

    public record HookResult(boolean cancelled, String commandLine) {
        public static HookResult pass(String commandLine) {
            return new HookResult(false, commandLine);
        }

        public static HookResult cancel() {
            return new HookResult(true, "");
        }
    }

    private static volatile PlatformCommandHook platformCommandHook = (server, sender, line) -> HookResult.pass(line);

    private LunarArcCommandRouter() {
    }

    public static void installPlatformCommandHook(PlatformCommandHook hook) {
        platformCommandHook = hook == null ? (server, sender, line) -> HookResult.pass(line) : hook;
    }

    public enum PacketResult {

        PASS,

        CANCEL
    }

    public static PacketResult routePlayerPacket(Server server, Player player, String packetCommand) {
        if (server == null || player == null || packetCommand == null) {
            return PacketResult.PASS;
        }


        String originalEventMessage = "/" + packetCommand;
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, originalEventMessage);
        server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            LunarArcCommandLogger.markCancelled();
            return PacketResult.CANCEL;
        }

        String eventMessage = event.getMessage();
        if (eventMessage == null) {
            return PacketResult.CANCEL;
        }

        String routedLine = removeEventIntroducer(eventMessage);
        if (routedLine.isEmpty()) {
            return PacketResult.CANCEL;
        }

        if (redirectVanillaWeather(server, player, routedLine)) {
            return PacketResult.CANCEL;
        }
        if (unknownCommand(server, player, routedLine)) {
            return PacketResult.CANCEL;
        }

        boolean modified = !eventMessage.equals(originalEventMessage);
        CommandMap commandMap = server.getCommandMap();
        String label = labelOf(routedLine);
        Command command = commandMap.getCommand(label);

        if (command != null) {


            commandMap.dispatch(player, routedLine);
            return PacketResult.CANCEL;
        }

        if (modified) {


            dispatch(server, player, routedLine);
            return PacketResult.CANCEL;
        }


        return PacketResult.PASS;
    }


    public static boolean dispatch(Server server, CommandSender sender, String commandLine) {
        if (server == null || sender == null || commandLine == null) return false;

        String line = commandLine.trim();
        if (line.isEmpty()) return false;

        boolean startedSession = false;
        if (sender instanceof Player player && !LunarArcCommandLogger.isSessionActive(player.getUniqueId())) {
            LunarArcCommandLogger.begin(player.getUniqueId(), player.getName(), line);
            startedSession = true;
        }

        try {
            if (server instanceof CraftServer craftServer) {
                HookResult hookResult = platformCommandHook.apply(craftServer, sender, line);
                if (hookResult == null || hookResult.cancelled()) return false;
                line = hookResult.commandLine() == null ? "" : hookResult.commandLine().trim();
                if (line.isEmpty()) return false;
            }

            if (redirectVanillaWeather(server, sender, line)) return true;

            CommandMap map = server.getCommandMap();


            String exactLabel = labelOf(line);
            if (map.getCommand(exactLabel) != null) {
                return map.dispatch(sender, line);
            }


            if (line.startsWith("/")) {
                line = line.substring(1).trim();
                if (line.isEmpty()) return false;
            }

            String label = labelOf(line);
            if (map.getCommand(label) != null) {
                return map.dispatch(sender, line);
            }

            return dispatchNative(server, sender, line);
        } finally {
            if (startedSession) {
                LunarArcCommandLogger.end();
            }
        }
    }

    private static boolean redirectVanillaWeather(Server server, CommandSender sender, String line) {
        String bare = line.startsWith("/") ? line.substring(1).trim() : line.trim();
        String[] parts = bare.split("\\s+");
        if (parts.length < 2 || !parts[0].equalsIgnoreCase("weather")) return false;
        String kind = parts[1].toLowerCase(Locale.ROOT);
        if (!kind.equals("clear") && !kind.equals("rain") && !kind.equals("thunder")) return false;
        Command owner = server.getCommandMap().getCommand("weather");
        if (!(owner instanceof org.bukkit.command.PluginIdentifiableCommand) || !owner.testPermissionSilent(sender)) return false;
        dispatchNative(server, sender, bare);
        return true;
    }

    private static boolean unknownCommand(Server server, CommandSender sender, String line) {
        if (!(server instanceof CraftServer craftServer)
                || org.bukkit.event.command.UnknownCommandEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return false;
        }
        String bare = line.startsWith("/") ? line.substring(1) : line;
        String label = rawLabelOf(bare);
        if (server.getCommandMap().getCommand(label) != null
                || craftServer.getServer().getCommands().getDispatcher().getRoot().getChild(label) != null) {
            return false;
        }
        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.translatable(
                "command.unknown.command", net.kyori.adventure.text.format.NamedTextColor.RED);
        org.bukkit.event.command.UnknownCommandEvent event =
                new org.bukkit.event.command.UnknownCommandEvent((io.papermc.paper.command.brigadier.CommandSourceStack) (Object) source(craftServer, sender), bare, message);
        server.getPluginManager().callEvent(event);
        if (event.message() != null) sender.sendMessage(event.message());
        return true;
    }

    public static boolean dispatchNative(Server server, CommandSender sender, String line) {
        if (!(server instanceof CraftServer craftServer)) return false;
        if (unknownCommand(server, sender, line)) return false;
        try {
            net.minecraft.commands.Commands commands = craftServer.getServer().getCommands();
            boolean known = commands.getDispatcher().getRoot().getChild(rawLabelOf(line)) != null;
            commands.performPrefixedCommand(source(craftServer, sender), line);
            return known;
        } catch (Throwable error) {
            server.getLogger().log(Level.SEVERE, "Error dispatching command: " + line, error);
            return false;
        }
    }

    private static CommandSourceStack source(CraftServer server, CommandSender sender) {
        if (sender instanceof io.papermc.paper.commands.FeedbackForwardingSender feedback) {
            return feedback.asVanilla();
        }
        if (sender instanceof CraftPlayer player) {
            return player.getHandle().createCommandSourceStack();
        }
        if (sender == server.getConsoleSender()) {
            return server.getServer().createCommandSourceStack();
        }
        if (sender instanceof org.bukkit.craftbukkit.entity.CraftEntity entity) {
            return entity.getHandle().createCommandSourceStackForNameResolution((net.minecraft.server.level.ServerLevel) entity.getHandle().level());
        }
        throw new IllegalArgumentException("Unsupported native command sender: " + sender.getClass().getName());
    }


    static String removeEventIntroducer(String eventMessage) {
        String line = eventMessage.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        return line.trim();
    }

    /**
     * The label exactly as typed. Brigadier's command tree is case-sensitive, so the lower-cased
     * label {@link #labelOf} produces - right for the Bukkit command map, which is not - would miss
     * every node when asked of the dispatcher.
     */
    static String rawLabelOf(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1).trim();
        if (trimmed.isEmpty()) return "";
        int space = trimmed.indexOf(' ');
        return space >= 0 ? trimmed.substring(0, space) : trimmed;
    }

    static String labelOf(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) return "";
        int space = trimmed.indexOf(' ');
        String label = space >= 0 ? trimmed.substring(0, space) : trimmed;
        return label.toLowerCase(Locale.ROOT);
    }
}
