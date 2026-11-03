package io.papermc.paper.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.command.ServerCommandSender;

public final class FeedbackForwardingSender extends ServerCommandSender {
    private final java.util.function.Consumer<? super Component> feedback;
    private final CraftServer server;

    public FeedbackForwardingSender(java.util.function.Consumer<? super Component> feedback, CraftServer server) {
        super(((ServerCommandSender) server.getConsoleSender()).perm);
        this.feedback = java.util.Objects.requireNonNull(feedback);
        this.server = server;
    }

    @Override public CraftServer getServer() { return server; }
    @Override public String getName() { return "FeedbackForwardingSender"; }
    @Override public boolean isOp() { return true; }
    @Override public void setOp(boolean value) { throw new UnsupportedOperationException("Cannot change console operator status"); }
    @Override public void sendMessage(String message) {
        feedback.accept(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }
    @Override public void sendMessage(String... messages) { for (String message : messages) sendMessage(message); }
    @Override public void sendMessage(Component message) { feedback.accept(message); }
    @Override public void sendMessage(net.kyori.adventure.identity.Identity identity, Component message,
            net.kyori.adventure.audience.MessageType type) { feedback.accept(message); }

    public net.minecraft.commands.CommandSourceStack asVanilla() {
        var minecraft = server.getServer();
        var level = minecraft.overworld();
        return new net.minecraft.commands.CommandSourceStack(new Source(),
                level == null ? net.minecraft.world.phys.Vec3.ZERO : net.minecraft.world.phys.Vec3.atLowerCornerOf(level.getRespawnData().pos()),
                net.minecraft.world.phys.Vec2.ZERO, level, net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS, getName(),
                net.minecraft.network.chat.Component.literal(getName()), minecraft, null);
    }

    private final class Source implements net.minecraft.commands.CommandSource,
            io.lunararcdevs.lunararc.common.bridge.CommandSourceBridge {
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) {
            feedback.accept(io.papermc.paper.adventure.PaperAdventure.asAdventure(message));
        }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
        @Override public org.bukkit.command.CommandSender lunararc$getBukkitSender(net.minecraft.commands.CommandSourceStack stack) {
            return FeedbackForwardingSender.this;
        }
    }
}
