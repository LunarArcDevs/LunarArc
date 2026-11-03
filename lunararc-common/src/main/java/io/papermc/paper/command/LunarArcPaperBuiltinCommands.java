package io.papermc.paper.command;

public final class LunarArcPaperBuiltinCommands {
    private LunarArcPaperBuiltinCommands() {
    }

    public static void register(org.bukkit.command.CommandMap commandMap) {
        commandMap.register("bukkit", new LunarArcPluginsCommand("plugins"));
    }
}
