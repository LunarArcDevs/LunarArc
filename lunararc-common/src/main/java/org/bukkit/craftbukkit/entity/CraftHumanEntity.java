
package org.bukkit.craftbukkit.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.attribute.CraftAttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.bukkit.permissions.PermissibleBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public abstract class CraftHumanEntity extends CraftLivingEntity {


    protected PermissibleBase perm = new PermissibleBase(this);

    private static final Map<Attribute, Holder<net.minecraft.world.entity.ai.attributes.Attribute>> ATTRIBUTE_MAP =
            new EnumMap<>(Attribute.class);
    static {
        ATTRIBUTE_MAP.put(Attribute.GENERIC_MAX_HEALTH,         Attributes.MAX_HEALTH);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_FOLLOW_RANGE,       Attributes.FOLLOW_RANGE);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_KNOCKBACK_RESISTANCE, Attributes.KNOCKBACK_RESISTANCE);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_MOVEMENT_SPEED,     Attributes.MOVEMENT_SPEED);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_FLYING_SPEED,       Attributes.FLYING_SPEED);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_ATTACK_DAMAGE,      Attributes.ATTACK_DAMAGE);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_ATTACK_KNOCKBACK,   Attributes.ATTACK_KNOCKBACK);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_ATTACK_SPEED,       Attributes.ATTACK_SPEED);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_ARMOR,              Attributes.ARMOR);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_ARMOR_TOUGHNESS,    Attributes.ARMOR_TOUGHNESS);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_LUCK,               Attributes.LUCK);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_MAX_ABSORPTION,     Attributes.MAX_ABSORPTION);
        ATTRIBUTE_MAP.put(Attribute.GENERIC_JUMP_STRENGTH,      Attributes.JUMP_STRENGTH);
        ATTRIBUTE_MAP.put(Attribute.PLAYER_BLOCK_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE);
        ATTRIBUTE_MAP.put(Attribute.PLAYER_ENTITY_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE);
        ATTRIBUTE_MAP.put(Attribute.PLAYER_BLOCK_BREAK_SPEED,   Attributes.BLOCK_BREAK_SPEED);
    }

    public static @Nullable Holder<net.minecraft.world.entity.ai.attributes.Attribute> toNms(@Nullable Attribute attribute) {
        if (attribute == null) return null;
        return ATTRIBUTE_MAP.get(attribute);
    }

    public CraftHumanEntity(CraftServer server, Player entity) {
        super(server, entity);
    }

    @Override public boolean isPermissionSet(@NotNull String name) { return perm.isPermissionSet(name); }
    @Override public boolean isPermissionSet(@NotNull Permission permission) { return perm.isPermissionSet(permission); }
    @Override public boolean hasPermission(@NotNull String name) { return perm.hasPermission(name); }
    @Override public boolean hasPermission(@NotNull Permission permission) { return perm.hasPermission(permission); }
    @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) { return perm.addAttachment(plugin, name, value); }
    @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) { return perm.addAttachment(plugin); }
    @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) { return perm.addAttachment(plugin, name, value, ticks); }
    @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) { return perm.addAttachment(plugin, ticks); }
    @Override public void removeAttachment(@NotNull PermissionAttachment attachment) { perm.removeAttachment(attachment); }
    @Override public void recalculatePermissions() { perm.recalculatePermissions(); }
    @Override public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() { return perm.getEffectivePermissions(); }
    @Override
    public boolean isOp() {
        Player handle = getHandle();
        if (handle == null) {
            return false;
        }
        try {
            net.minecraft.server.players.PlayerList playerList = server.getServer().getPlayerList();
            if (playerList != null) {
                return playerList.isOp(handle.getGameProfile());
            }
        } catch (Throwable ignored) {
        }
        return handle.getAbilities().instabuild;
    }

    @Override
    public void setOp(boolean value) {
        if (value == isOp()) return;
        try {
            Object playerList = server.getServer().getPlayerList();
            if (value) {
                int opLevel = server.getServer().getOperatorUserPermissionLevel();
                boolean bypass = (boolean) playerList.getClass().getMethod("canBypassPlayerLimit", com.mojang.authlib.GameProfile.class).invoke(playerList, getHandle().getGameProfile());
                Object entry = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.server.players.ServerOpListEntry")
                        .getConstructor(com.mojang.authlib.GameProfile.class, int.class, boolean.class)
                        .newInstance(getHandle().getGameProfile(), opLevel, bypass);
                Object ops = playerList.getClass().getMethod("getOps").invoke(playerList);
                ops.getClass().getMethod("add", io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.server.players.StoredUserEntry")).invoke(ops, entry);
            } else {
                Object ops = playerList.getClass().getMethod("getOps").invoke(playerList);
                ops.getClass().getMethod("remove", Object.class).invoke(ops, getHandle().getGameProfile());
            }
        } catch (Exception ignored) {
        }
        perm.recalculatePermissions();
    }

    public @Nullable org.bukkit.attribute.AttributeInstance getAttribute(@NotNull Attribute attribute) {
        Holder<net.minecraft.world.entity.ai.attributes.Attribute> nmsAttr = toNms(attribute);
        if (nmsAttr == null) return null;
        net.minecraft.world.entity.LivingEntity living = (net.minecraft.world.entity.LivingEntity) entity;
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = living.getAttribute(nmsAttr);
        return instance == null ? null : new CraftAttributeInstance(instance, attribute);
    }

    @Override
    public void registerAttribute(@NotNull Attribute attribute) { super.registerAttribute(attribute); }

    @SuppressWarnings({"deprecation", "removal"})
    public void openSign(@NotNull org.bukkit.block.Sign sign) {
        this.openSign(sign, org.bukkit.block.sign.Side.FRONT);
    }

    public void openSign(@NotNull org.bukkit.block.Sign sign, @NotNull org.bukkit.block.sign.Side side) {
        java.util.Objects.requireNonNull(sign, "sign");
        java.util.Objects.requireNonNull(side, "side");
        if (!sign.isPlaced()) {
            throw new IllegalArgumentException("Sign must be placed");
        }
        if (!(this instanceof CraftPlayer craftPlayer)) {
            throw new IllegalStateException("Only players can open sign editors");
        }
        if (sign.getWorld() != craftPlayer.getWorld()) {
            throw new IllegalArgumentException("Sign must be in same world as Player");
        }

        io.papermc.paper.event.player.PlayerOpenSignEvent paperEvent =
                new io.papermc.paper.event.player.PlayerOpenSignEvent(
                        craftPlayer, sign, side,
                        io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.PLUGIN);
        server.getPluginManager().callEvent(paperEvent);
        if (paperEvent.isCancelled()) {
            return;
        }

        if (org.bukkit.event.player.PlayerSignOpenEvent.getHandlerList().getRegisteredListeners().length > 0) {
            @SuppressWarnings({"deprecation", "removal"})
            org.bukkit.event.player.PlayerSignOpenEvent legacyEvent =
                    new org.bukkit.event.player.PlayerSignOpenEvent(
                            craftPlayer, sign, side,
                            org.bukkit.event.player.PlayerSignOpenEvent.Cause.PLUGIN);
            server.getPluginManager().callEvent(legacyEvent);
            if (legacyEvent.isCancelled()) {
                return;
            }
        }

        org.bukkit.Location location = sign.getLocation();
        net.minecraft.core.BlockPos position = new net.minecraft.core.BlockPos(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        net.minecraft.world.level.block.entity.BlockEntity blockEntity =
                craftPlayer.getHandle().serverLevel().getBlockEntity(position);
        if (!(blockEntity instanceof net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity)) {
            throw new IllegalArgumentException("Sign does not reference a live sign block entity");
        }

        signBlockEntity.setAllowedPlayerEditor(craftPlayer.getUniqueId());
        craftPlayer.getHandle().openTextEdit(signBlockEntity, side == org.bukkit.block.sign.Side.FRONT);
    }

    public net.minecraft.world.entity.player.Player getHandle() {
        return (net.minecraft.world.entity.player.Player) entity;
    }
}
