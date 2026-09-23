package io.lunararcdevs.lunararc.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge;
import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import io.lunararcdevs.lunararc.common.server.LunarArcCommandRouter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftEntity;

public final class ForgeCommandHook {
    private ForgeCommandHook() {}

    public static void install() {
        LunarArcCommandRouter.installPlatformCommandHook(ForgeCommandHook::apply);
    }

    private static LunarArcCommandRouter.HookResult apply(CraftServer server, CommandSender sender, String commandLine) {
        CommandSourceStack source = source(sender);
        if (source == null) {
            return LunarArcCommandRouter.HookResult.pass(commandLine);
        }

        StringReader reader = new StringReader(commandLine);
        if (reader.canRead() && reader.peek() == '/') reader.skip();
        ParseResults<CommandSourceStack> parse = dispatcher(LunarArcServer.requireMinecraftServer()).parse(reader, source);
        CommandEvent event = new CommandEvent(parse);
        if (MinecraftForge.EVENT_BUS.post(event) || event.getException() != null) {
            return LunarArcCommandRouter.HookResult.cancel();
        }

        String rewritten = event.getParseResults().getReader().getString();
        if (rewritten.startsWith("/")) rewritten = rewritten.substring(1);
        return LunarArcCommandRouter.HookResult.pass(rewritten);
    }

    // Loom compiles Forge against SRG names, but MinecraftServer#getCommands and
    // Commands#getDispatcher are real (unobfuscated-in-source) Mojang names, so a direct call
    // never resolves here - go through the reflection bridge, which maps the name correctly.
    @SuppressWarnings("unchecked")
    private static CommandDispatcher<CommandSourceStack> dispatcher(MinecraftServer minecraftServer) {
        try {
            Object commands = LunarArcReflectionBridge
                    .getMethod(minecraftServer.getClass(), "getCommands", new Class<?>[0])
                    .invoke(minecraftServer);
            Object dispatcher = LunarArcReflectionBridge
                    .getMethod(commands.getClass(), "getDispatcher", new Class<?>[0])
                    .invoke(commands);
            return (CommandDispatcher<CommandSourceStack>) dispatcher;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the command dispatcher", e);
        }
    }

    private static CommandSourceStack source(CommandSender sender) {
        if (sender instanceof CraftEntity craftEntity) {
            return createCommandSourceStack(craftEntity.getHandle());
        }
        if (sender == Bukkit.getConsoleSender()) {
            return createCommandSourceStack(LunarArcServer.requireMinecraftServer());
        }
        return null;
    }

    private static CommandSourceStack createCommandSourceStack(Object commandSource) {
        try {
            return (CommandSourceStack) LunarArcReflectionBridge
                    .getMethod(commandSource.getClass(), "createCommandSourceStack", new Class<?>[0])
                    .invoke(commandSource);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create a command source stack for " + commandSource, e);
        }
    }
}
