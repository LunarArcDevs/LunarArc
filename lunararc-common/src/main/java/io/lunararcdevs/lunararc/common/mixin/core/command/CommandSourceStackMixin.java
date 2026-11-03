package io.lunararcdevs.lunararc.common.mixin.core.command;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import io.lunararcdevs.lunararc.common.bridge.CommandSourceBridge;
import io.lunararcdevs.lunararc.common.bridge.CommandSourceStackBridge;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.commands.CommandSourceStack.class)
public abstract class CommandSourceStackMixin
        implements CommandSourceStackBridge,
                   io.papermc.paper.command.brigadier.CommandSourceStack {

    @Shadow @Final public CommandSource source;
    @Shadow public abstract net.minecraft.world.entity.Entity getEntity();
    @Shadow public abstract Vec3 getPosition();
    @Shadow public abstract Vec2 getRotation();
    @Shadow public abstract ServerLevel getLevel();

    @Unique
    private boolean lunararc$bypassSelectorPermissions;

    @Override
    public boolean lunararc$bypassSelectorPermissions() {
        return this.lunararc$bypassSelectorPermissions;
    }

    @Override
    public void lunararc$setBypassSelectorPermissions(boolean bypass) {
        this.lunararc$bypassSelectorPermissions = bypass;
    }

    @Override
    public CommandSender getSender() {
        if (this.source instanceof CommandSourceBridge bridge) {
            return bridge.lunararc$getBukkitSender((net.minecraft.commands.CommandSourceStack) (Object) this);
        }

        net.minecraft.world.entity.Entity entity = this.getEntity();
        if (entity instanceof EntityBridge bridge) {
            org.bukkit.entity.Entity bukkit = bridge.lunararc$getBukkitEntity();
            if (bukkit instanceof CommandSender sender) {
                return sender;
            }
        }

        // Vanilla also builds command requirements against a source with no sender (CommandSource.NULL) while it sends a player the
        // command tree; treat that as the console instead of failing every command check once per registered command.
        if (this.source == net.minecraft.commands.CommandSource.NULL) {
            return LunarArcServerAccess.getCraftServer().getConsoleSender();
        }

        throw new IllegalStateException("Unsupported command source: " + this.source.getClass().getName());
    }

    @Override
    public org.bukkit.entity.Entity getExecutor() {
        net.minecraft.world.entity.Entity entity = this.getEntity();
        return entity instanceof EntityBridge bridge ? bridge.lunararc$getBukkitEntity() : null;
    }

    @Override
    public Location getLocation() {
        Vec3 pos = this.getPosition();
        Vec2 rot = this.getRotation();
        ServerLevel level = this.getLevel();
        return new Location(
                LunarArcServerAccess.getCraftServer(level.getServer()).getCraftWorld(level),
                pos.x, pos.y, pos.z, rot.y, rot.x);
    }

    @Override
    public CommandSender lunararc$getBukkitSender() {
        return this.getSender();
    }

    public CommandSender getBukkitSender() {
        return this.getSender();
    }
}
