package org.bukkit.craftbukkit.entity;

import io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Entity;
import org.bukkit.craftbukkit.CraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import net.md_5.bungee.api.chat.BaseComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.map.MapView;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.Material;
import org.bukkit.GameMode;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import io.lunararcdevs.lunararc.common.bridge.ItemStackBridge;
import io.lunararcdevs.lunararc.common.bridge.ServerPlayerBukkitDataBridge;
import io.lunararcdevs.lunararc.common.bridge.access.LivingEntityAccessBridge;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.block.Block;
import org.bukkit.attribute.AttributeInstance;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import org.bukkit.util.Vector;
import org.bukkit.entity.Projectile;
import org.bukkit.damage.DamageSource;
import net.kyori.adventure.util.TriState;
import org.bukkit.util.RayTraceResult;
import org.bukkit.entity.Item;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.FishHook;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.Merchant;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Firework;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.memory.MemoryKey;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.entity.TargetEntityInfo;

@SuppressWarnings("all")
public class CraftPlayer extends CraftHumanEntity implements Player {


    private final org.bukkit.craftbukkit.inventory.CraftPlayerInventory inventory;
    private final org.bukkit.craftbukkit.inventory.CraftNMSInventory enderChest;

    public CraftPlayer(CraftServer server, ServerPlayer player) {
        super(server, player);


        this.inventory = new org.bukkit.craftbukkit.inventory.CraftPlayerInventory(player.getInventory(), this);
        this.enderChest = new org.bukkit.craftbukkit.inventory.CraftNMSInventory(player.getEnderChestInventory(), this, org.bukkit.event.inventory.InventoryType.ENDER_CHEST);
    }

    private net.kyori.adventure.text.Component tabListHeader = net.kyori.adventure.text.Component.empty();
    private net.kyori.adventure.text.Component tabListFooter = net.kyori.adventure.text.Component.empty();
    private String displayName;
    private Location compassTarget;
    private long playerTimeOffset;
    private boolean playerTimeRelative = true;
    private org.bukkit.WeatherType playerWeather;
    private org.bukkit.WorldBorder playerWorldBorder;
    private TriState flyingFallDamage = TriState.NOT_SET;
    private boolean sleepingIgnored;
    private boolean hasSeenWinScreen;
    private Location respawnLocation;
    private boolean whitelisted;
    private boolean lunararcHealthScaled;
    private double lunararcHealthScale = 20.0D;
    private org.bukkit.event.player.PlayerResourcePackStatusEvent.Status resourcePackStatus;
    private int lunararcViewDistance = -1;
    private int lunararcSimulationDistance = -1;
    private int lunararcSendViewDistance = -1;
    private final org.bukkit.craftbukkit.conversations.ConversationTracker conversationTracker = new org.bukkit.craftbukkit.conversations.ConversationTracker();


    private final Set<UUID> unlistedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> legacyHiddenEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<Plugin>> pluginHiddenEntities = new ConcurrentHashMap<>();

    public ServerPlayer getHandle() {
        return (ServerPlayer) entity;
    }

    @Override
    public org.bukkit.entity.EntityType getType() { return org.bukkit.entity.EntityType.PLAYER; }

    @Override
    public boolean getAffectsSpawning() {
        return ((io.lunararcdevs.lunararc.common.bridge.PlayerAffectsSpawningBridge) (Object) getHandle()).lunararc$getAffectsSpawning();
    }

    @Override
    public void setAffectsSpawning(boolean affects) {
        ((io.lunararcdevs.lunararc.common.bridge.PlayerAffectsSpawningBridge) (Object) getHandle()).lunararc$setAffectsSpawning(affects);
    }


    @Override
    public String getName() {
        return getHandle().getScoreboardName();
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component name() {
        return net.kyori.adventure.text.Component.text(getName());
    }

    private final org.bukkit.entity.Player.Spigot spigot = new org.bukkit.entity.Player.Spigot() {
        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent component) {
            sendBungeeComponents(component == null ? new net.md_5.bungee.api.chat.BaseComponent[0]
                    : new net.md_5.bungee.api.chat.BaseComponent[] { component });
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent... components) {
            sendBungeeComponents(components);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(@Nullable UUID sender, net.md_5.bungee.api.chat.BaseComponent component) {
            sendMessage(component);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(@Nullable UUID sender, net.md_5.bungee.api.chat.BaseComponent... components) {
            sendMessage(components);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(net.md_5.bungee.api.ChatMessageType position,
                net.md_5.bungee.api.chat.BaseComponent component) {
            sendMessage(position, component == null ? new net.md_5.bungee.api.chat.BaseComponent[0]
                    : new net.md_5.bungee.api.chat.BaseComponent[] { component });
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(net.md_5.bungee.api.ChatMessageType position,
                net.md_5.bungee.api.chat.BaseComponent... components) {
            if (position == net.md_5.bungee.api.ChatMessageType.ACTION_BAR) {
                sendBungeeActionBar(components);
            } else {
                sendBungeeComponents(components);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(net.md_5.bungee.api.ChatMessageType position, @Nullable UUID sender,
                net.md_5.bungee.api.chat.BaseComponent component) {
            sendMessage(position, component);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendMessage(net.md_5.bungee.api.ChatMessageType position, @Nullable UUID sender,
                net.md_5.bungee.api.chat.BaseComponent... components) {
            sendMessage(position, components);
        }

        @Override
        public java.net.InetSocketAddress getRawAddress() {
            if (getHandle().connection == null) return null;
            return (java.net.InetSocketAddress) getHandle().connection.getRemoteAddress();
        }

        @Override
        public void respawn() {
            if (getHealth() <= 0 && isOnline()) {
                getHandle().connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            }
        }

        @Override
        public Set<Player> getHiddenPlayers() {
            Set<Player> hidden = new java.util.HashSet<>();
            for (Player other : getServer().getOnlinePlayers()) {
                if (!canSee(other)) hidden.add(other);
            }
            return Collections.unmodifiableSet(hidden);
        }

        @Override
        public int getPing() {
            return CraftPlayer.this.getPing();
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    public org.bukkit.entity.Player.Spigot spigot() {
        return spigot;
    }

    private void sendBungeeComponents(net.md_5.bungee.api.chat.BaseComponent... components) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendSystem(getHandle(), components);
    }

    private void sendBungeeActionBar(net.md_5.bungee.api.chat.BaseComponent... components) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendActionBar(getHandle(), components);
    }

    @Override
    public void setWalkSpeed(float value) {
        validateSpeed(value);
        getHandle().getAbilities().setWalkingSpeed(value / 2.0F);
        net.minecraft.world.entity.ai.attributes.AttributeInstance movementSpeed =
                getHandle().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(getHandle().getAbilities().getWalkingSpeed());
        }
        syncAbilities();
    }

    @Override
    public float getWalkSpeed() {
        return getHandle().getAbilities().getWalkingSpeed() * 2.0F;
    }

    @Override
    public void setFlySpeed(float value) {
        validateSpeed(value);
        getHandle().getAbilities().setFlyingSpeed(value / 2.0F);
        syncAbilities();
    }

    @Override
    public float getFlySpeed() {
        return getHandle().getAbilities().getFlyingSpeed() * 2.0F;
    }

    @Override
    public void setAllowFlight(boolean flight) {
        getHandle().getAbilities().mayfly = flight;
        if (!flight && getHandle().getAbilities().flying) {
            getHandle().getAbilities().flying = false;
        }
        syncAbilities();
    }

    @Override
    public boolean getAllowFlight() {
        return getHandle().getAbilities().mayfly;
    }

    @Override
    public void setFlying(boolean value) {
        if (value && !getAllowFlight()) {
            throw new IllegalArgumentException("Cannot make player fly if getAllowFlight() is false");
        }
        if (getHandle().getAbilities().flying == value) {
            return;
        }
        getHandle().getAbilities().flying = value;
        syncAbilities();
    }

    @Override
    public boolean isFlying() {
        return getHandle().getAbilities().flying;
    }

    @Override
    public void setFlyingFallDamage(@NotNull TriState flyingFallDamage) {
        if (flyingFallDamage == null) throw new IllegalArgumentException("flyingFallDamage cannot be null");
        this.flyingFallDamage = flyingFallDamage;
    }

    @Override
    public @NotNull TriState hasFlyingFallDamage() { return flyingFallDamage; }

    @Override
    public boolean isSprinting() { return getHandle().isSprinting(); }

    @Override
    public void setSprinting(boolean sprinting) { getHandle().setSprinting(sprinting); }

    @Override
    public void setSleepingIgnored(boolean isSleeping) { this.sleepingIgnored = isSleeping; }

    @Override
    public boolean isSleepingIgnored() { return sleepingIgnored; }

    @Override
    public boolean isTransferred() {
        return getHandle().connection != null
                && ((io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) getHandle().connection)
                        .lunararc$isTransferred();
    }

    @Override
    public @Nullable InetSocketAddress getHAProxyAddress() {
        if (getHandle().connection == null) return null;
        io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge bridge =
                (io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) (Object) getHandle().connection;
        java.net.SocketAddress address = ((io.lunararcdevs.lunararc.common.bridge.ConnectionBridge) (Object)
                bridge.lunararc$getConnection()).lunararc$getHAProxyAddress();
        return address instanceof InetSocketAddress inet ? inet : null;
    }

    @Override
    public boolean hasSeenWinScreen() { return hasSeenWinScreen; }

    @Override
    public void setHasSeenWinScreen(boolean hasSeenWinScreen) { this.hasSeenWinScreen = hasSeenWinScreen; }

    @Override
    public void showWinScreen() {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.WIN_GAME, 1.0F));
    }

    @Override
    public void saveData() {
        ((io.lunararcdevs.lunararc.common.bridge.access.PlayerListAccessBridge) (Object) getHandle().server.getPlayerList()).lunararc$invokeSave(getHandle());
    }

    @Override
    public void loadData() {
        getHandle().level().getServer().getPlayerList().loadPlayerData(new net.minecraft.server.players.NameAndId(getHandle().getGameProfile()))
                .map(tag -> net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, getHandle().level().getServer().registryAccess(), tag))
                .ifPresent(getHandle()::load);
    }

    @Override
    public boolean performCommand(@NotNull String command) {
        if (command == null) throw new IllegalArgumentException("command cannot be null");
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        return getServer().dispatchCommand(this, normalized);
    }

    @Override
    public void chat(@NotNull String msg) {
        if (msg == null) throw new IllegalArgumentException("msg cannot be null");
        if (msg.startsWith("/")) performCommand(msg.substring(1)); else sendMessage(msg);
    }

    @Override
    public @NotNull CompletableFuture<byte[]> retrieveCookie(@NotNull NamespacedKey key) {
        if (getHandle().connection == null) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Player has no active connection"));
            return failed;
        }
        return ((io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) getHandle().connection)
                .lunararc$retrieveCookie(key);
    }

    @Override
    public void storeCookie(@NotNull NamespacedKey key, byte @NotNull [] value) {
        if (getHandle().connection == null) throw new IllegalStateException("Player has no active connection");
        ((io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) getHandle().connection)
                .lunararc$storeCookie(key, value);
    }

    @Override
    public void transfer(@NotNull String host, int port) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host cannot be blank");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port out of range");
        java.util.Objects.requireNonNull(getHandle().connection, "Player has no active connection")
                .send(new net.minecraft.network.protocol.common.ClientboundTransferPacket(host, port));
    }

    @Override
    public void kick() { kick(net.kyori.adventure.text.Component.text("Kicked by server")); }

    @Override
    public void kick(@Nullable net.kyori.adventure.text.Component message) {
        kick(message, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
    }

    @Override
    public void kick(@Nullable net.kyori.adventure.text.Component message, org.bukkit.event.player.PlayerKickEvent.@NotNull Cause cause) {
        if (getHandle().connection == null) return;
        net.kyori.adventure.text.Component reason = message == null
                ? net.kyori.adventure.text.Component.text("Kicked by server") : message;
        org.bukkit.event.player.PlayerKickEvent event = new org.bukkit.event.player.PlayerKickEvent(
                this, reason, null, cause);
        getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            getHandle().connection.disconnect(adventureToNms(event.reason()));
        }
    }

    @Override
    public void kickPlayer(@Nullable String message) {
        kick(message == null ? null : net.kyori.adventure.text.Component.text(message));
    }

    @Override
    public boolean isListed(@NotNull Player other) {
        if (other == null) throw new IllegalArgumentException("other player cannot be null");
        return !unlistedPlayers.contains(other.getUniqueId());
    }

    @Override
    public boolean unlistPlayer(@NotNull Player other) {
        if (other == null) throw new IllegalArgumentException("other player cannot be null");
        boolean wasListed = unlistedPlayers.add(other.getUniqueId());
        if (wasListed) {
            sendPlayerListVisibility(other, false);
        }
        return wasListed;
    }

    @Override
    public boolean listPlayer(@NotNull Player other) {
        if (other == null) throw new IllegalArgumentException("other player cannot be null");
        if (!canSee(other)) {
            throw new IllegalStateException("Cannot list a player that is hidden from this player");
        }
        boolean wasUnlisted = unlistedPlayers.remove(other.getUniqueId());
        if (wasUnlisted) {
            sendPlayerListVisibility(other, true);
        }
        return wasUnlisted;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void hidePlayer(@NotNull Player player) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        legacyHiddenEntities.add(player.getUniqueId());
        unlistPlayer(player);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void showPlayer(@NotNull Player player) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        legacyHiddenEntities.remove(player.getUniqueId());
        if (canSee(player)) listPlayer(player);
    }

    @Override
    public void hidePlayer(@NotNull Plugin plugin, @NotNull Player player) {
        hideEntity(plugin, player);
        unlistPlayer(player);
    }

    @Override
    public void showPlayer(@NotNull Plugin plugin, @NotNull Player player) {
        showEntity(plugin, player);
        if (canSee(player)) listPlayer(player);
    }

    @Override
    public boolean canSee(@NotNull Player player) {
        return canSee((Entity) player);
    }

    @Override
    public void hideEntity(@NotNull Plugin plugin, @NotNull Entity target) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (target == null) throw new IllegalArgumentException("entity cannot be null");
        boolean wasVisible = canSee(target);
        pluginHiddenEntities.computeIfAbsent(target.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(plugin);
        if (wasVisible) io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.hideShow(this, target, true);
    }

    @Override
    public void showEntity(@NotNull Plugin plugin, @NotNull Entity target) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (target == null) throw new IllegalArgumentException("entity cannot be null");
        Set<Plugin> plugins = pluginHiddenEntities.get(target.getUniqueId());
        if (plugins == null) return;
        plugins.remove(plugin);
        if (plugins.isEmpty()) {
            pluginHiddenEntities.remove(target.getUniqueId(), plugins);
            if (canSee(target)) io.lunararcdevs.lunararc.common.event.LunarArcMoreEvents.hideShow(this, target, false);
        }
    }

    @Override
    public boolean canSee(@NotNull Entity target) {
        if (target == null) throw new IllegalArgumentException("entity cannot be null");
        UUID id = target.getUniqueId();
        if (legacyHiddenEntities.contains(id)) return false;
        Set<Plugin> plugins = pluginHiddenEntities.get(id);
        return plugins == null || plugins.isEmpty();
    }

    private void sendPlayerListVisibility(Player other, boolean listed) {
        if (getHandle().connection == null || !(other instanceof CraftPlayer craftOther)) return;
        if (listed) {
            getHandle().connection.send(
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                            java.util.List.of(craftOther.getHandle())));
        } else {
            getHandle().connection.send(
                    new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                            java.util.List.of(other.getUniqueId())));
        }
    }

    private void unregisterEntity(net.minecraft.world.entity.Entity other) {
        net.minecraft.server.level.ChunkMap chunkMap = getHandle().level().getChunkSource().chunkMap;
        net.minecraft.server.level.ChunkMap.TrackedEntity tracked =
                ((io.lunararcdevs.lunararc.common.bridge.access.ChunkMapAccessBridge) (Object) chunkMap)
                        .lunararc$getEntityMap().get(other.getId());
        if (tracked != null) {
            tracked.removePlayer(getHandle());
        }
        if (other instanceof ServerPlayer otherPlayer && getHandle().connection != null) {
            getHandle().connection.send(
                    new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                            java.util.List.of(otherPlayer.getUUID())));
        }
    }

    private void registerEntity(net.minecraft.world.entity.Entity other) {
        if (other instanceof ServerPlayer otherPlayer && getHandle().connection != null) {
            getHandle().connection.send(
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                            java.util.List.of(otherPlayer)));
        }
        net.minecraft.server.level.ChunkMap chunkMap = getHandle().level().getChunkSource().chunkMap;
        net.minecraft.server.level.ChunkMap.TrackedEntity tracked =
                ((io.lunararcdevs.lunararc.common.bridge.access.ChunkMapAccessBridge) (Object) chunkMap)
                        .lunararc$getEntityMap().get(other.getId());
        if (tracked != null) {
            tracked.updatePlayer(getHandle());
        }
    }

    public com.mojang.authlib.GameProfile getProfile() {
        return getHandle().getGameProfile();
    }

    @Override
    public UUID getUniqueId() {
        return getHandle().getUUID();
    }

    @Override
    public InetSocketAddress getAddress() {
        if (getHandle().connection == null) return null;
        return (InetSocketAddress) getHandle().connection.getRemoteAddress();
    }

    @Override
    public void sendMessage(String message) {
        if (!this.conversationTracker.isConversingModaly()) {
            sendRawMessage(null, message);
        }
    }

    private void sendLegacyComponentDirect(String message) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendSystem(getHandle(), message);
    }

    @Override
    public void sendMessage(String... messages) {
        if (messages == null) return;
        for (String msg : messages) sendMessage(msg);
    }

    @Override
    public void sendMessage(net.kyori.adventure.text.Component message) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendSystem(getHandle(), message);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void sendMessage(net.kyori.adventure.identity.Identity identity,
            net.kyori.adventure.text.Component message, net.kyori.adventure.audience.MessageType type) {
        sendMessage(message);
    }

    private void syncAbilities() {
        getHandle().onUpdateAbilities();
    }

    private static void validateSpeed(float value) {
        if (!Float.isFinite(value) || value < -1.0f || value > 1.0f) {
            throw new IllegalArgumentException("Speed must be finite and between -1.0 and 1.0");
        }
    }

    private static net.minecraft.network.chat.Component adventureToNms(net.kyori.adventure.text.Component component) {
        return io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(component);
    }

    private static net.kyori.adventure.text.Component bungeeToAdventure(net.md_5.bungee.api.chat.BaseComponent... components) {
        return io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.bungeeToAdventure(components);
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? getName() : displayName;
    }

    @Override
    public void setDisplayName(String name) { this.displayName = name; }

    @Override
    public @NotNull Component displayName() {
        return io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.legacyToAdventure(getDisplayName());
    }

    @Override
    public void displayName(@Nullable Component displayName) {
        this.displayName = displayName == null ? null
                : net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build()
                        .serialize(displayName);
    }

    private void sendTabListPacket() {
        try {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundTabListPacket(
                    adventureToNms(tabListHeader), adventureToNms(tabListFooter)));
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendPlayerListHeader(@NotNull net.kyori.adventure.text.Component header) {
        tabListHeader = header;
        sendTabListPacket();
    }

    @Override
    public void sendPlayerListFooter(@NotNull net.kyori.adventure.text.Component footer) {
        tabListFooter = footer;
        sendTabListPacket();
    }

    @Override
    public void sendPlayerListHeaderAndFooter(@NotNull net.kyori.adventure.text.Component header, @NotNull net.kyori.adventure.text.Component footer) {
        this.tabListHeader = header;
        this.tabListFooter = footer;
        sendTabListPacket();
    }
    @Override public Component playerListHeader() { return tabListHeader; }
    @Override public Component playerListFooter() { return tabListFooter; }
    @Override public @Nullable String getPlayerListHeader() { return io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.toLegacy(tabListHeader); }
    @Override public @Nullable String getPlayerListFooter() { return io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.toLegacy(tabListFooter); }
    @Override public void setPlayerListHeader(String header) {
        tabListHeader = header == null ? net.kyori.adventure.text.Component.empty() : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.legacyToAdventure(header);
        sendTabListPacket();
    }
    @Override public void setPlayerListFooter(String footer) {
        tabListFooter = footer == null ? net.kyori.adventure.text.Component.empty() : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.legacyToAdventure(footer);
        sendTabListPacket();
    }
    @Override public void setPlayerListHeaderFooter(String header, String footer) {
        tabListHeader = header == null ? net.kyori.adventure.text.Component.empty() : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.legacyToAdventure(header);
        tabListFooter = footer == null ? net.kyori.adventure.text.Component.empty() : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.legacyToAdventure(footer);
        sendTabListPacket();
    }
    @SuppressWarnings("deprecation")
    @Override public void setPlayerListHeaderFooter(@Nullable BaseComponent[] header, @Nullable BaseComponent[] footer) {
        tabListHeader = header == null ? net.kyori.adventure.text.Component.empty() : bungeeToAdventure(header);
        tabListFooter = footer == null ? net.kyori.adventure.text.Component.empty() : bungeeToAdventure(footer);
        sendTabListPacket();
    }
    @SuppressWarnings("deprecation")
    @Override public void setPlayerListHeaderFooter(@Nullable BaseComponent header, @Nullable BaseComponent footer) {
        setPlayerListHeaderFooter(
            header == null ? null : new BaseComponent[] { header },
            footer == null ? null : new BaseComponent[] { footer }
        );
    }
    @Override public String getPlayerListName() {
        net.minecraft.network.chat.Component display = getHandle().getTabListDisplayName();
        return display != null
                ? io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.toLegacy(
                        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.toAdventure(display))
                : getName();
    }
    @Override public void setPlayerListName(String name) {
        try {
            var field = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("tabListDisplayName");
            field.setAccessible(true);
            field.set(getHandle(), name == null ? null
                    : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromLegacy(name));
        } catch (Throwable ignored) {}
    }
    @Override public @NotNull Component playerListName() {
        net.minecraft.network.chat.Component display = getHandle().getTabListDisplayName();
        return display == null ? Component.text(getName())
                : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.toAdventure(display);
    }
    @Override public void playerListName(@Nullable Component name) {
        try {
            var field = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("tabListDisplayName");
            field.setAccessible(true);
            field.set(getHandle(), name == null ? null : adventureToNms(name));
        } catch (Throwable ignored) {}
    }
    @Override public void setCompassTarget(Location loc) {
        if (loc == null) throw new IllegalArgumentException("Compass target cannot be null");
        this.compassTarget = loc.clone();
        if (getHandle().connection != null && loc.getWorld() == getWorld()) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(
                    net.minecraft.world.level.storage.LevelData.RespawnData.of(((org.bukkit.craftbukkit.CraftWorld) loc.getWorld()).getHandle().dimension(),
                            new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), loc.getYaw(), loc.getPitch())));
        }
    }
    @Override public Firework fireworkBoost(ItemStack stack) {
        java.util.Objects.requireNonNull(stack, "stack");
        net.minecraft.world.entity.projectile.FireworkRocketEntity firework = new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                getHandle().level(), CraftItemStack.asNMSCopy(stack), getHandle());
        boolean added = ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) getHandle().level())
                .lunararc$addFreshEntity(firework, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!added) return null;
        return (Firework) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) firework).lunararc$getBukkitEntity();
    }
    @Override public Location getCompassTarget() {
        if (compassTarget != null) return compassTarget.clone();
        net.minecraft.world.level.storage.LevelData.RespawnData respawn = getHandle().level().getRespawnData();
        net.minecraft.core.BlockPos pos = respawn.pos();
        return new Location(getWorld(), pos.getX(), pos.getY(), pos.getZ(), respawn.yaw(), respawn.pitch());
    }
    @Override public Iterable<? extends BossBar> activeBossBars() { return org.bukkit.craftbukkit.boss.CraftBossBar.activeAdventureFor(this); }
    @Override public void sendExperienceChange(float progress) {
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(progress, getHandle().totalExperience, getHandle().experienceLevel));
    }
    @Override public void sendMap(MapView map) {
        java.util.Objects.requireNonNull(map, "map");
        if (getHandle().connection == null) return;
        if (!(map instanceof org.bukkit.craftbukkit.map.CraftMapView craftMap)) {
            throw new IllegalArgumentException("MapView was not created by this server");
        }
        org.bukkit.craftbukkit.map.RenderData rendered = craftMap.render(this);
        java.util.List<net.minecraft.world.level.saveddata.maps.MapDecoration> decorations = new java.util.ArrayList<>();
        for (org.bukkit.map.MapCursor cursor : rendered.cursors) {
            if (!cursor.isVisible()) continue;
            org.bukkit.NamespacedKey key = cursor.getType().getKey();
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    key.getNamespace(), key.getKey());
            net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> type =
                    net.minecraft.core.registries.BuiltInRegistries.MAP_DECORATION_TYPE.get(id)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown map cursor type " + key));
            java.util.Optional<net.minecraft.network.chat.Component> caption = java.util.Optional.ofNullable(cursor.caption())
                    .map(io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline::fromAdventure);
            decorations.add(new net.minecraft.world.level.saveddata.maps.MapDecoration(
                    type, cursor.getX(), cursor.getY(), cursor.getDirection(), caption));
        }
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(
                new net.minecraft.world.level.saveddata.maps.MapId(map.getId()),
                map.getScale().getValue(),
                map.isLocked(),
                decorations,
                new net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch(0, 0, 128, 128, rendered.buffer)));
    }
    @Override public void sendRawMessage(String message) { sendRawMessage(null, message); }
    @Override public void sendBlockChange(Location loc, Material material, byte data) {
        java.util.Objects.requireNonNull(loc, "loc");
        java.util.Objects.requireNonNull(material, "material");
        sendBlockChange(loc, material.createBlockData());
    }
    @Override public void sendBlockChange(Location loc, org.bukkit.block.data.BlockData block) {
        java.util.Objects.requireNonNull(loc, "loc");
        java.util.Objects.requireNonNull(block, "block");
        if (getHandle().connection == null) return;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, toNmsBlockData(block)));
    }

    private static net.minecraft.world.level.block.state.BlockState toNmsBlockData(org.bukkit.block.data.BlockData block) {
        if (block instanceof org.bukkit.craftbukkit.block.data.CraftBlockData craft) return craft.getState();
        org.bukkit.block.data.BlockData parsed = org.bukkit.Bukkit.createBlockData(block.getAsString());
        if (!(parsed instanceof org.bukkit.craftbukkit.block.data.CraftBlockData craft)) {
            throw new IllegalArgumentException("Unsupported BlockData implementation: " + block.getClass().getName());
        }
        return craft.getState();
    }

    @Override public void sendBlockChanges(Collection<org.bukkit.block.BlockState> states) { sendBlockChanges(states, false); }
    @Override public void sendBlockChanges(Collection<org.bukkit.block.BlockState> states, boolean ignoreAir) {
        java.util.Objects.requireNonNull(states, "states");
        if (getHandle().connection == null || states.isEmpty()) return;
        for (org.bukkit.block.BlockState state : states) {
            if (state == null || state.getWorld() != getWorld()) continue;
            net.minecraft.world.level.block.state.BlockState nms = toNmsBlockData(state.getBlockData());
            if (ignoreAir && nms.isAir()) continue;
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(state.getX(), state.getY(), state.getZ());
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, nms));
        }
    }
    @Override public void sendBlockDamage(Location loc, float progress) { sendBlockDamage(loc, progress, getEntityId()); }
    @Override public void sendBlockDamage(Location loc, float progress, Entity entity) {
        java.util.Objects.requireNonNull(entity, "entity");
        sendBlockDamage(loc, progress, entity.getEntityId());
    }
    @Override public void sendBlockDamage(Location loc, float progress, int entityId) {
        java.util.Objects.requireNonNull(loc, "loc");
        if (getHandle().connection == null) return;
        int stage = progress <= 0.0F ? -1 : Math.min(9, Math.max(0, (int) (progress * 10.0F)));
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(entityId, new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), stage));
    }
    @Override public void sendEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
        java.util.Objects.requireNonNull(entity, "entity"); java.util.Objects.requireNonNull(slot, "slot"); java.util.Objects.requireNonNull(stack, "stack");
        sendEquipmentChange(entity, java.util.Map.of(slot, stack));
    }
    @Override public void sendEquipmentChange(LivingEntity entity, Map<EquipmentSlot, ItemStack> equipment) {
        java.util.Objects.requireNonNull(entity, "entity"); java.util.Objects.requireNonNull(equipment, "equipment");
        if (getHandle().connection == null || equipment.isEmpty()) return;
        java.util.List<com.mojang.datafixers.util.Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = new java.util.ArrayList<>();
        for (var entry : equipment.entrySet()) {
            net.minecraft.world.entity.EquipmentSlot nmsSlot = switch (entry.getKey()) {
                case HAND -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
                case OFF_HAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
                case FEET -> net.minecraft.world.entity.EquipmentSlot.FEET;
                case LEGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                case CHEST -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                case BODY -> net.minecraft.world.entity.EquipmentSlot.BODY;
                case SADDLE -> net.minecraft.world.entity.EquipmentSlot.SADDLE;
            };
            slots.add(com.mojang.datafixers.util.Pair.of(nmsSlot, CraftItemStack.asNMSCopy(entry.getValue())));
        }
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(entity.getEntityId(), slots));
    }
    @Override public void sendSignChange(Location loc, String[] lines) { sendSignChange(loc, lines, DyeColor.BLACK, false); }
    @Override public void sendSignChange(Location loc, String[] lines, DyeColor dyeColor) { sendSignChange(loc, lines, dyeColor, false); }
    @Override public void sendSignChange(Location loc, String[] lines, DyeColor dyeColor, boolean hasGlowingText) {
        java.util.Objects.requireNonNull(loc, "loc");
        java.util.Objects.requireNonNull(dyeColor, "dyeColor");
        if (lines == null) lines = new String[4];
        if (lines.length < 4) throw new IllegalArgumentException("Must have at least 4 lines");
        net.minecraft.network.chat.Component[] components = new net.minecraft.network.chat.Component[4];
        for (int i = 0; i < components.length; i++) {
            components[i] = lines[i] == null
                    ? net.minecraft.network.chat.Component.empty()
                    : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromLegacy(lines[i]);
        }
        sendSignChange0(loc, components, dyeColor, hasGlowingText);
    }
    @Override
    public void sendPluginMessage(org.bukkit.plugin.Plugin plugin, String channel, byte[] message) {
        org.bukkit.plugin.messaging.StandardMessenger.validatePluginMessage(getServer().getMessenger(), plugin, channel, message);
        if (getHandle().connection == null) return;

        String corrected = org.bukkit.plugin.messaging.StandardMessenger.validateAndCorrectChannel(channel);
        io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge bridge =
                (io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) getHandle().connection;
        if (!bridge.lunararc$getPluginChannels().contains(corrected)) return;

        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(corrected);
        getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                new io.lunararcdevs.lunararc.common.network.LunarArcRawPayload(id, message)));
    }
    @Override
    public void sendPotionEffectChange(LivingEntity entity, PotionEffect effect) {
        java.util.Objects.requireNonNull(entity, "entity");
        java.util.Objects.requireNonNull(effect, "effect");
        if (getHandle().connection == null) return;

        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> holder = resolveMobEffect(effect.getType());
        net.minecraft.world.effect.MobEffectInstance instance = new net.minecraft.world.effect.MobEffectInstance(
                holder, effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles());
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket(
                entity.getEntityId(), instance, true));
    }

    @Override
    public void sendPotionEffectChangeRemove(LivingEntity entity, PotionEffectType type) {
        java.util.Objects.requireNonNull(entity, "entity");
        java.util.Objects.requireNonNull(type, "type");
        if (getHandle().connection == null) return;

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(
                entity.getEntityId(), resolveMobEffect(type)));
    }

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> resolveMobEffect(PotionEffectType type) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.parse(type.getKey().toString());
        return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown potion effect type: " + type.getKey()));
    }
    @Override public void sendBlockUpdate(Location loc, org.bukkit.block.TileState state) {
        java.util.Objects.requireNonNull(loc, "loc");
        java.util.Objects.requireNonNull(state, "state");
        if (getHandle().connection == null) return;
        if (!(state instanceof org.bukkit.craftbukkit.block.CraftBlockState craftState)) {
            throw new IllegalArgumentException("Unsupported TileState implementation: " + state.getClass().getName());
        }
        org.bukkit.World world = state.getWorld();
        if (!(world instanceof org.bukkit.craftbukkit.CraftWorld craftWorld)) {
            throw new IllegalArgumentException("TileState world is not a CraftWorld");
        }
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = craftWorld.getHandle().getBlockEntity(
                new net.minecraft.core.BlockPos(state.getX(), state.getY(), state.getZ()));
        if (blockEntity == null) {
            throw new IllegalArgumentException("TileState does not reference a block entity");
        }
        getHandle().connection.send(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(blockEntity));
    }
    @Override public void sendSignChange(Location loc, List<? extends net.kyori.adventure.text.Component> lines, DyeColor dyeColor, boolean hasGlowingText) {
        java.util.Objects.requireNonNull(loc, "loc");
        java.util.Objects.requireNonNull(dyeColor, "dyeColor");
        java.util.Objects.requireNonNull(lines, "lines");
        if (lines.size() < 4) throw new IllegalArgumentException("Must have at least 4 lines");
        net.minecraft.network.chat.Component[] components = new net.minecraft.network.chat.Component[4];
        for (int i = 0; i < components.length; i++) {
            net.kyori.adventure.text.Component line = lines.get(i);
            components[i] = line == null
                    ? net.minecraft.network.chat.Component.empty()
                    : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(line);
        }
        sendSignChange0(loc, components, dyeColor, hasGlowingText);
    }

    private void sendSignChange0(Location loc, net.minecraft.network.chat.Component[] components, DyeColor dyeColor, boolean hasGlowingText) {
        if (getHandle().connection == null) return;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        net.minecraft.world.level.block.entity.SignBlockEntity sign = new net.minecraft.world.level.block.entity.SignBlockEntity(
                pos, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState());
        net.minecraft.world.level.block.entity.SignText text = sign.getFrontText()
                .setColor(net.minecraft.world.item.DyeColor.byId(dyeColor.getWoolData()))
                .setHasGlowingText(hasGlowingText);
        for (int i = 0; i < components.length; i++) text = text.setMessage(i, components[i]);
        sign.setText(text, true);
        getHandle().connection.send(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(sign));
    }
    @Override public void sendHealthUpdate(double health, int foodLevel, float saturation) {
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                    (float) health, foodLevel, saturation));
        }
    }
    @Override public void sendHealthUpdate() {
        double health = getHealth();
        if (this.lunararcHealthScaled) {
            double max = getMaxHealth();
            health = max <= 0.0D ? 0.0D : (health / max) * this.lunararcHealthScale;
        }
        sendHealthUpdate(health, getFoodLevel(), getSaturation());
    }
    @Override
    public void sendMultiBlockChange(Map<? extends io.papermc.paper.math.Position, org.bukkit.block.data.BlockData> blocks) {
        java.util.Objects.requireNonNull(blocks, "blocks");
        if (getHandle().connection == null || blocks.isEmpty()) return;

        for (var entry : blocks.entrySet()) {
            io.papermc.paper.math.Position position = java.util.Objects.requireNonNull(entry.getKey(), "block position");
            org.bukkit.block.data.BlockData blockData = java.util.Objects.requireNonNull(entry.getValue(), "block data");
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(position.blockX(), position.blockY(), position.blockZ());
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, toNmsBlockData(blockData)));
        }
    }
    @Override public void hideTitle() {
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(false));
        }
    }
    @Override
    public void sendEntityEffect(org.bukkit.EntityEffect effect, org.bukkit.entity.Entity target) {
        java.util.Objects.requireNonNull(effect, "effect");
        java.util.Objects.requireNonNull(target, "target");
        if (getHandle().connection == null || !effect.isApplicableTo(target)) return;
        if (!(target instanceof CraftEntity craftTarget)) {
            throw new IllegalArgumentException("Entity is not backed by LunarArc");
        }
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(
                craftTarget.getHandle(), effect.getData()));
    }

    @Override public void sendHurtAnimation(float yaw) {
        sendHurtAnimation(yaw, this);
    }

    void sendHurtAnimation(float yaw, CraftEntity target) {
        java.util.Objects.requireNonNull(target, "target");
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(
                    target.getEntityId(), yaw + 90.0F));
        }
    }
    @Override public void sendLinks(org.bukkit.ServerLinks links) {
        java.util.Objects.requireNonNull(links, "links");
        if (getHandle().connection == null) return;
        if (!(links instanceof org.bukkit.craftbukkit.CraftServerLinks craftLinks)) {
            throw new IllegalArgumentException("ServerLinks was not created by this server");
        }
        getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundServerLinksPacket(craftLinks.getHandle().untrust()));
    }
    @Override public void addCustomChatCompletions(Collection<String> completions) { sendChatCompletions(net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action.ADD, completions); }
    @Override public void removeCustomChatCompletions(Collection<String> completions) { sendChatCompletions(net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action.REMOVE, completions); }
    @Override public void setCustomChatCompletions(Collection<String> completions) { sendChatCompletions(net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action.SET, completions); }
    private void sendChatCompletions(net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action action, Collection<String> completions) {
        java.util.Objects.requireNonNull(completions, "completions");
        if (getHandle().connection != null) getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket(action, java.util.List.copyOf(completions)));
    }

    @Override public void updateInventory() {
        getHandle().inventoryMenu.broadcastChanges();
    }
    @Override public GameMode getPreviousGameMode() {
        net.minecraft.world.level.GameType previous = getHandle().gameMode.getPreviousGameModeForPlayer();
        if (previous == null) return null;
        try {
            return GameMode.valueOf(previous.getName().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
    @Override public void setPlayerTime(long time, boolean relative) {
        this.playerTimeRelative = relative;
        this.playerTimeOffset = relative ? time : time - getWorld().getTime();
    }
    @Override public long getPlayerTime() { return getWorld().getTime() + playerTimeOffset; }
    @Override public long getPlayerTimeOffset() { return playerTimeOffset; }
    @Override public boolean isPlayerTimeRelative() { return playerTimeRelative; }
    @Override public void resetPlayerTime() { playerTimeOffset = 0L; playerTimeRelative = true; }
    @Override public void setPlayerWeather(org.bukkit.WeatherType type) { playerWeather = type; }
    @Override public org.bukkit.WeatherType getPlayerWeather() { return playerWeather; }
    @Override public void resetPlayerWeather() { playerWeather = null; }
    @Override public int getExpCooldown() { return getHandle().takeXpDelay; }
    @Override public void setExpCooldown(int ticks) { getHandle().takeXpDelay = ticks; }
    @Override public void giveExp(int amount, boolean applyMending) { getHandle().giveExperiencePoints(amount); }
    @Override public int applyMending(int amount) { return amount; }
    @Override public void giveExpLevels(int levels) { getHandle().giveExperienceLevels(levels); }
    @Override public void setExp(float exp) { getHandle().experienceProgress = exp; getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(exp, getHandle().totalExperience, getHandle().experienceLevel)); }
    @Override public float getExp() { return getHandle().experienceProgress; }
    @Override public void sendExperienceChange(float progress, int level) { getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(progress, getHandle().totalExperience, level)); }
    @Override public void setExperienceLevelAndProgress(int level) { setLevel(level); }
    @Override public void setLevel(int level) { getHandle().experienceLevel = level; getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(getExp(), getHandle().totalExperience, level)); }
    @Override public int getLevel() { return getHandle().experienceLevel; }
    @Override public void setTotalExperience(int exp) { getHandle().totalExperience = exp; }
    @Override public int getTotalExperience() { return getHandle().totalExperience; }
    @Override public int getExperiencePointsNeededForNextLevel() {
        int level = getLevel();
        return level >= 30 ? 9 * level - 158 : level >= 15 ? 5 * level - 38 : 2 * level + 7;
    }
    @Override public int calculateTotalExperiencePoints() {
        int level = getLevel();
        int base;
        if (level <= 16) base = level * level + 6 * level;
        else if (level <= 31) base = (int) Math.floor(2.5 * level * level - 40.5 * level + 360);
        else base = (int) Math.floor(4.5 * level * level - 162.5 * level + 2220);
        return base + Math.round(getExp() * getExperiencePointsNeededForNextLevel());
    }
    @Override public boolean isChunkSent(long chunk) {
        return getHandle().getChunkTrackingView().contains(new net.minecraft.world.level.ChunkPos(chunk));
    }
    @Override public Set<Chunk> getSentChunks() {
        java.util.LinkedHashSet<Chunk> chunks = new java.util.LinkedHashSet<>();
        getHandle().getChunkTrackingView().forEach(pos -> chunks.add(getWorld().getChunkAt(pos.x, pos.z)));
        return java.util.Collections.unmodifiableSet(chunks);
    }
    @Override public Set<Long> getSentChunkKeys() {
        java.util.LinkedHashSet<Long> keys = new java.util.LinkedHashSet<>();
        getHandle().getChunkTrackingView().forEach(pos -> keys.add(pos.toLong()));
        return java.util.Collections.unmodifiableSet(keys);
    }
    @Override public void resetIdleDuration() { getHandle().resetLastActionTime(); }
    @Override public Duration getIdleDuration() {
        long idleMillis = Math.max(0L, net.minecraft.util.Util.getMillis() - getHandle().getLastActionTime());
        return Duration.ofMillis(idleMillis);
    }
    @Override public void setRotation(float yaw, float pitch) {
        getHandle().snapTo(getHandle().getX(), getHandle().getY(), getHandle().getZ(), yaw, pitch);
        getHandle().connection.teleport(getHandle().getX(), getHandle().getY(), getHandle().getZ(), yaw, pitch);
    }
    @Override public void lookAt(double x, double y, double z, io.papermc.paper.entity.LookAnchor anchor) {
        java.util.Objects.requireNonNull(anchor, "anchor");
        net.minecraft.commands.arguments.EntityAnchorArgument.Anchor nmsAnchor = anchor == io.papermc.paper.entity.LookAnchor.EYES
                ? net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES
                : net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
        getHandle().lookAt(nmsAnchor, new net.minecraft.world.phys.Vec3(x, y, z));
    }
    @Override public void lookAt(Entity entity, io.papermc.paper.entity.LookAnchor anchor, io.papermc.paper.entity.LookAnchor anchor2) {
        java.util.Objects.requireNonNull(entity, "entity"); java.util.Objects.requireNonNull(anchor, "anchor"); java.util.Objects.requireNonNull(anchor2, "anchor2");
        if (!(entity instanceof CraftEntity craft)) throw new IllegalArgumentException("Unsupported entity implementation: " + entity.getClass().getName());
        net.minecraft.commands.arguments.EntityAnchorArgument.Anchor from = anchor == io.papermc.paper.entity.LookAnchor.EYES ? net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES : net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
        net.minecraft.commands.arguments.EntityAnchorArgument.Anchor to = anchor2 == io.papermc.paper.entity.LookAnchor.EYES ? net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES : net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
        getHandle().lookAt(from, craft.getHandle(), to);
    }
    @Override public void showElderGuardian(boolean silent) {
        if (getHandle().connection != null) getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, silent ? 0.0F : 1.0F));
    }
    @Override public int getWardenWarningCooldown() {
        return getHandle().getWardenSpawnTracker().map(tracker -> tracker.cooldownTicks).orElse(0);
    }
    @Override public void setWardenWarningCooldown(int cooldown) {
        if (cooldown < 0) throw new IllegalArgumentException("cooldown must be >= 0");
        getHandle().getWardenSpawnTracker().ifPresent(tracker -> tracker.cooldownTicks = cooldown);
    }
    @Override public int getWardenTimeSinceLastWarning() {
        return getHandle().getWardenSpawnTracker().map(tracker -> tracker.ticksSinceLastWarning).orElse(0);
    }
    @Override public void setWardenTimeSinceLastWarning(int time) {
        if (time < 0) throw new IllegalArgumentException("time must be >= 0");
        getHandle().getWardenSpawnTracker().ifPresent(tracker -> tracker.ticksSinceLastWarning = time);
    }
    @Override public int getWardenWarningLevel() {
        return getHandle().getWardenSpawnTracker().map(net.minecraft.world.entity.monster.warden.WardenSpawnTracker::getWarningLevel).orElse(0);
    }
    @Override public void setWardenWarningLevel(int level) {
        getHandle().getWardenSpawnTracker().ifPresent(tracker -> tracker.setWarningLevel(level));
    }
    @Override public void increaseWardenWarningLevel() {
        getHandle().getWardenSpawnTracker().ifPresent(tracker -> {
            tracker.increaseWarningLevel();
        });
    }
    @Override
    public String getClientBrandName() {
        if (getHandle().connection == null) return null;
        return ((io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) getHandle().connection)
                .lunararc$getClientBrand();
    }
    @Override public void sendOpLevel(byte level) {
        if (level < 0 || level > 4) throw new IllegalArgumentException("level must be between 0 and 4");
        if (getHandle().connection != null) getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(getHandle(), (byte) (24 + level)));
    }
    @Override public void addAdditionalChatCompletions(Collection<String> completions) { addCustomChatCompletions(completions); }
    @Override public void removeAdditionalChatCompletions(Collection<String> completions) { removeCustomChatCompletions(completions); }
    @Override public float getCooldownPeriod() { return 1.0f; }
    @Override public float getCooledAttackStrength(float adjustTicks) { return getHandle().getAttackStrengthScale(adjustTicks); }
    @Override public void resetCooldown() { getHandle().resetAttackStrengthTicker(); }
    @Override public <T> T getClientOption(com.destroystokyo.paper.ClientOption<T> option) {
        java.util.Objects.requireNonNull(option, "option");
        if (com.destroystokyo.paper.ClientOption.SKIN_PARTS == option) {
            int raw = getHandle().getEntityData().get(net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION);
            return option.getType().cast(new com.destroystokyo.paper.PaperSkinParts(raw));
        } else if (com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED == option) {
            return option.getType().cast(getHandle().canChatInColor());
        } else if (com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY == option) {
            return option.getType().cast(com.destroystokyo.paper.ClientOption.ChatVisibility.valueOf(getHandle().getChatVisibility().name()));
        } else if (com.destroystokyo.paper.ClientOption.LOCALE == option) {
            return option.getType().cast(getLocale());
        } else if (com.destroystokyo.paper.ClientOption.MAIN_HAND == option) {
            return option.getType().cast(getMainHand());
        } else if (com.destroystokyo.paper.ClientOption.VIEW_DISTANCE == option) {
            return option.getType().cast(getClientViewDistance());
        } else if (com.destroystokyo.paper.ClientOption.TEXT_FILTERING_ENABLED == option) {
            return option.getType().cast(getHandle().isTextFilteringEnabled());
        } else if (com.destroystokyo.paper.ClientOption.ALLOW_SERVER_LISTINGS == option) {
            return option.getType().cast(getHandle().allowsListing());
        }
        throw new IllegalArgumentException("Unknown client option: " + option);
    }
    @Override
    public PlayerProfile getPlayerProfile() {
        com.mojang.authlib.GameProfile handle =
                getHandle().gameProfile;
        com.destroystokyo.paper.profile.CraftPlayerProfile profile =
                new com.destroystokyo.paper.profile.CraftPlayerProfile(handle.id(), handle.name());
        java.util.List<com.destroystokyo.paper.profile.ProfileProperty> properties = new java.util.ArrayList<>();
        for (com.mojang.authlib.properties.Property property : handle.properties().values()) {
            properties.add(new com.destroystokyo.paper.profile.ProfileProperty(
                    property.name(), property.value(), property.signature()));
        }
        profile.setProperties(properties);
        return profile;
    }

    @Override
    public void setPlayerProfile(PlayerProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        UUID profileId = profile.getId();
        String profileName = profile.getName();
        if (profileId == null && (profileName == null || profileName.isBlank())) {
            throw new IllegalArgumentException("Profile must contain a UUID or non-blank name");
        }

        com.mojang.authlib.GameProfile current =
                getHandle().gameProfile;
        com.mojang.authlib.GameProfile replacement = new com.mojang.authlib.GameProfile(
                profileId != null ? profileId : current.id(),
                profileName != null && !profileName.isBlank() ? profileName : current.name());
        for (com.destroystokyo.paper.profile.ProfileProperty property : profile.getProperties()) {
            replacement.properties().put(property.getName(),
                    new com.mojang.authlib.properties.Property(
                            property.getName(), property.getValue(), property.getSignature()));
        }

        java.util.List<ServerPlayer> viewers =
                new java.util.ArrayList<>(getHandle().server.getPlayerList().getPlayers());
        for (ServerPlayer viewer : viewers) {
            org.bukkit.entity.Entity bukkit = ((EntityBridge) viewer).lunararc$getBukkitEntity();
            if (bukkit instanceof CraftPlayer craftViewer && craftViewer.canSee(this)) {
                craftViewer.unregisterEntity(getHandle());
            }
        }

        getHandle().gameProfile = replacement;

        for (ServerPlayer viewer : viewers) {
            org.bukkit.entity.Entity bukkit = ((EntityBridge) viewer).lunararc$getBukkitEntity();
            if (bukkit instanceof CraftPlayer craftViewer && craftViewer.canSee(this)) {
                craftViewer.registerEntity(getHandle());
            }
        }

        refreshPlayerAfterProfileChange();
    }

    private void refreshPlayerAfterProfileChange() {
        ServerPlayer handle = getHandle();
        if (handle.connection == null) return;
        net.minecraft.server.level.ServerLevel level = handle.level();
        handle.connection.send(new net.minecraft.network.protocol.game.ClientboundRespawnPacket(
                handle.createCommonSpawnInfo(level),
                net.minecraft.network.protocol.game.ClientboundRespawnPacket.KEEP_ALL_DATA));
        handle.onUpdateAbilities();
        Location location = getLocation();
        handle.connection.teleport(
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        handle.server.getPlayerList().sendPlayerPermissionLevel(handle);
        handle.server.getPlayerList().sendLevelInfo(handle, level);
        handle.server.getPlayerList().sendAllPlayerInfo(handle);
        handle.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(
                handle.experienceProgress, handle.totalExperience, handle.experienceLevel));
        for (net.minecraft.world.effect.MobEffectInstance effect : handle.getActiveEffects()) {
            handle.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket(
                    handle.getId(), effect, false));
        }
    }

    @Override public boolean isAllowingServerListings() { return getHandle().allowsListing(); }

    @Override
    public void showDemoScreen() {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.DEMO_EVENT,
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.DEMO_PARAM_INTRO));
    }

    @Override
    public void updateCommands() {
        if (getHandle().connection == null) return;
        getHandle().server.getCommands().sendCommands(getHandle());
    }

    @Override public int getClientViewDistance() {
        return getHandle().requestedViewDistance();
    }

    @Override public int getPing() {
        if (getHandle().connection == null) {
            throw new UnsupportedOperationException("Too early to call this method at this stage");
        }
        return getHandle().connection.latency();
    }

    @Override public String getLocale() {
        String language = ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerClientOptionsBridge) getHandle())
                .lunararc$getLanguage();
        return language != null ? language : "en_us";
    }

    @Override public java.util.Locale locale() {
        String language = ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerClientOptionsBridge) getHandle())
                .lunararc$getLanguage();
        if (language == null) return java.util.Locale.US;
        java.util.Locale parsed = java.util.Locale.forLanguageTag(language.replace('_', '-'));
        return parsed.getLanguage().isEmpty() ? java.util.Locale.US : parsed;
    }

    @Override public int getViewDistance() {
        return lunararcViewDistance < 0 ? getWorld().getViewDistance() : lunararcViewDistance;
    }

    @Override public void setViewDistance(int distance) {
        validateViewDistance(distance, "viewDistance");
        this.lunararcViewDistance = distance;
    }

    @Override public int getSimulationDistance() {
        return lunararcSimulationDistance < 0 ? getWorld().getSimulationDistance() : lunararcSimulationDistance;
    }

    @Override public void setSimulationDistance(int distance) {
        validateViewDistance(distance, "simulationDistance");
        this.lunararcSimulationDistance = distance;
    }

    @Override public int getSendViewDistance() {
        return lunararcSendViewDistance < 0 ? getViewDistance() : lunararcSendViewDistance;
    }

    @Override public void setSendViewDistance(int distance) {
        validateViewDistance(distance, "sendViewDistance");
        this.lunararcSendViewDistance = distance;
    }

    private static void validateViewDistance(int distance, String name) {
        if (distance != -1 && (distance < 2 || distance > 32)) {
            throw new IllegalArgumentException(name + " must be between 2 and 32, or -1 to use the world/server default");
        }
    }
    private org.bukkit.craftbukkit.CraftWorld lunararcCraftWorld() { return (org.bukkit.craftbukkit.CraftWorld) getWorld(); }
    @Override public void spawnParticle(Particle particle, Location location, int count) { lunararcCraftWorld().spawnParticle(particle, java.util.List.of(this), this, location.getX(), location.getY(), location.getZ(), count, 0,0,0,0,null,false); }
    @Override public void spawnParticle(Particle particle, double x, double y, double z, int count) { lunararcCraftWorld().spawnParticle(particle, java.util.List.of(this), this, x,y,z,count,0,0,0,0,null,false); }
    @Override public <T> void spawnParticle(Particle particle, Location location, int count, T data) { lunararcCraftWorld().spawnParticle(particle, java.util.List.of(this), this, location.getX(),location.getY(),location.getZ(),count,0,0,0,0,data,false); }
    @Override public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, T data) { lunararcCraftWorld().spawnParticle(particle, java.util.List.of(this), this, x,y,z,count,0,0,0,0,data,false); }
    @Override public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,location.getX(),location.getY(),location.getZ(),count,offsetX,offsetY,offsetZ,0,null,false); }
    @Override public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,x,y,z,count,offsetX,offsetY,offsetZ,0,null,false); }
    @Override public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,location.getX(),location.getY(),location.getZ(),count,offsetX,offsetY,offsetZ,0,data,false); }
    @Override public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,x,y,z,count,offsetX,offsetY,offsetZ,0,data,false); }
    @Override public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,location.getX(),location.getY(),location.getZ(),count,offsetX,offsetY,offsetZ,extra,null,false); }
    @Override public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,x,y,z,count,offsetX,offsetY,offsetZ,extra,null,false); }
    @Override public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,location.getX(),location.getY(),location.getZ(),count,offsetX,offsetY,offsetZ,extra,data,false); }
    @Override public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,x,y,z,count,offsetX,offsetY,offsetZ,extra,data,false); }
    @Override public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,location.getX(),location.getY(),location.getZ(),count,offsetX,offsetY,offsetZ,extra,data,force); }
    @Override public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) { lunararcCraftWorld().spawnParticle(particle,java.util.List.of(this),this,x,y,z,count,offsetX,offsetY,offsetZ,extra,data,force); }
    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, float volume, float pitch) {
        playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, getHandle().getRandom().nextLong());
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        if (location == null || sound == null || category == null || getHandle().connection == null) return;
        sendSound(location, resolveSound(sound), category, volume, pitch, seed);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {
        playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, getHandle().getRandom().nextLong());
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        if (location == null || sound == null || category == null || getHandle().connection == null) return;
        sendSound(location, resolveSound(sound), category, volume, pitch, seed);
    }

    private void sendSound(Location location, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
            SoundCategory category, float volume, float pitch, long seed) {
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                sound, toSoundSource(category), location.getX(), location.getY(), location.getZ(), volume, pitch, seed));
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, float volume, float pitch) {
        playSound(entity, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, getHandle().getRandom().nextLong());
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        if (entity == null || sound == null || category == null || getHandle().connection == null) return;
        sendEntitySound(entity, resolveSound(sound), category, volume, pitch, seed);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, float volume, float pitch) {
        playSound(entity, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, getHandle().getRandom().nextLong());
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        if (entity == null || sound == null || category == null || getHandle().connection == null) return;
        sendEntitySound(entity, resolveSound(sound), category, volume, pitch, seed);
    }

    private void sendEntitySound(Entity entity, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
            SoundCategory category, float volume, float pitch, long seed) {
        if (!(entity instanceof CraftEntity craftEntity)) {
            throw new IllegalArgumentException("Unsupported entity implementation: " + entity.getClass().getName());
        }
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
                sound, toSoundSource(category), craftEntity.getHandle(), volume, pitch, seed));
    }

    private static net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> resolveSound(Sound sound) {
        return resolveSound(sound.getKey().toString());
    }

    private static net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> resolveSound(String sound) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.parse(sound);
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(key)
                .<net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>>map(holder -> holder)
                .orElseGet(() -> net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(key)));
    }

    private static net.minecraft.sounds.SoundSource toSoundSource(SoundCategory category) {
        return net.minecraft.sounds.SoundSource.valueOf(category.name());
    }

    @Override
    public void stopSound(@NotNull Sound sound) {
        if (sound == null) return;
        stopSound(sound.getKey().toString(), null);
    }

    @Override
    public void stopSound(@NotNull String sound) {
        stopSound(sound, null);
    }

    @Override
    public void stopSound(@NotNull Sound sound, @Nullable SoundCategory category) {
        if (sound == null) return;
        stopSound(sound.getKey().toString(), category);
    }

    @Override
    public void stopSound(@NotNull String sound, @Nullable SoundCategory category) {
        if (sound == null || getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                net.minecraft.resources.Identifier.parse(sound),
                category == null ? null : toSoundSource(category)));
    }

    @Override
    public void stopSound(@NotNull SoundCategory category) {
        if (category == null || getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, toSoundSource(category)));
    }

    @Override
    public void playSound(net.kyori.adventure.sound.@NotNull Sound sound) {
        playSound(sound, getHandle().getX(), getHandle().getY(), getHandle().getZ());
    }

    @Override
    public void playSound(net.kyori.adventure.sound.@NotNull Sound sound, double x, double y, double z) {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                resolveSound(sound.name().asString()), io.papermc.paper.adventure.PaperAdventure.asVanilla(sound.source()),
                x, y, z, sound.volume(), sound.pitch(), sound.seed().orElseGet(() -> getHandle().getRandom().nextLong())));
    }

    @Override
    public void playSound(net.kyori.adventure.sound.@NotNull Sound sound, net.kyori.adventure.sound.Sound.@NotNull Emitter emitter) {
        if (getHandle().connection == null) return;
        net.minecraft.world.entity.Entity target;
        if (emitter == net.kyori.adventure.sound.Sound.Emitter.self()) {
            target = getHandle();
        } else if (emitter instanceof CraftEntity craftEntity) {
            target = craftEntity.getHandle();
        } else {
            throw new IllegalArgumentException("Specified emitter '" + emitter + "' is not a valid emitter");
        }
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
                resolveSound(sound.name().asString()), io.papermc.paper.adventure.PaperAdventure.asVanilla(sound.source()),
                target, sound.volume(), sound.pitch(), sound.seed().orElseGet(() -> getHandle().getRandom().nextLong())));
    }

    @Override
    public void stopSound(net.kyori.adventure.sound.@NotNull SoundStop stop) {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                io.papermc.paper.adventure.PaperAdventure.asVanillaNullable(stop.sound()),
                io.papermc.paper.adventure.PaperAdventure.asVanillaNullable(stop.source())));
    }

    @Override
    public void showBossBar(@NotNull BossBar bar) {
        net.kyori.adventure.bossbar.BossBarImplementation.get(bar, io.papermc.paper.adventure.BossBarImplementationImpl.class).playerShow(this);
    }

    @Override
    public void hideBossBar(@NotNull BossBar bar) {
        net.kyori.adventure.bossbar.BossBarImplementation.get(bar, io.papermc.paper.adventure.BossBarImplementationImpl.class).playerHide(this);
    }

    @Override
    public void openBook(net.kyori.adventure.inventory.@NotNull Book book) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
        java.util.List<net.minecraft.server.network.Filterable<net.minecraft.network.chat.Component>> pages = new ArrayList<>();
        for (Component page : book.pages()) {
            pages.add(net.minecraft.server.network.Filterable.passThrough(io.papermc.paper.adventure.PaperAdventure.asVanilla(page)));
        }
        stack.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                new net.minecraft.world.item.component.WrittenBookContent(
                        net.minecraft.server.network.Filterable.passThrough(
                                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(book.title())),
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(book.author()),
                        0, pages, true));
        openBook(CraftItemStack.asBukkitCopy(stack));
    }

    @Override
    public void sendResourcePacks(net.kyori.adventure.resource.@NotNull ResourcePackRequest request) {
        boolean first = true;
        for (net.kyori.adventure.resource.ResourcePackInfo info : request.packs()) {
            byte[] hash = info.hash().isEmpty() ? null : java.util.HexFormat.of().parseHex(info.hash());
            if (request.callback() != net.kyori.adventure.resource.ResourcePackCallback.noOp() && getHandle().connection != null) {
                ((io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) (Object) getHandle().connection).lunararc$getPackCallbacks().put(info.id(), request.callback());
            }
            pushResourcePack(info.id(), info.uri().toString(), hash,
                    request.prompt() == null ? null : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(request.prompt()),
                    request.required(), first && request.replace());
            first = false;
        }
    }

    @Override
    public void removeResourcePacks(@NotNull UUID id, @NotNull UUID @NotNull ... others) {
        removeResourcePack(id);
        for (UUID other : others) removeResourcePack(other);
    }

    @Override
    public void clearResourcePacks() {
        removeResourcePacks();
    }

    @Override
    public void deleteMessage(net.kyori.adventure.chat.SignedMessage.@NotNull Signature signature) {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundDeleteChatPacket(
                new net.minecraft.network.chat.MessageSignature.Packed(
                        new net.minecraft.network.chat.MessageSignature(signature.bytes()))));
    }

    @Override
    public net.kyori.adventure.pointer.@NotNull Pointers pointers() {
        return net.kyori.adventure.pointer.Pointers.builder()
                .withDynamic(net.kyori.adventure.identity.Identity.NAME, this::getName)
                .withDynamic(net.kyori.adventure.identity.Identity.UUID, this::getUniqueId)
                .withDynamic(net.kyori.adventure.identity.Identity.DISPLAY_NAME, this::displayName)
                .withDynamic(net.kyori.adventure.identity.Identity.LOCALE, this::locale)
                .withDynamic(net.kyori.adventure.permission.PermissionChecker.POINTER, () -> permission ->
                        isPermissionSet(permission)
                                ? net.kyori.adventure.util.TriState.byBoolean(hasPermission(permission))
                                : net.kyori.adventure.util.TriState.NOT_SET)
                .build();
    }

    @Override
    public void stopAllSounds() {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
    }

    @Override
    public @Nullable BanEntry<InetAddress> banIp(@Nullable String reason, @Nullable Duration duration, @Nullable String source, boolean kickPlayer) {
        InetAddress address = getAddress() == null ? null : getAddress().getAddress();
        if (address == null) return null;
        BanEntry<InetAddress> entry = new org.bukkit.craftbukkit.ban.CraftIpBanList(this.server.getServer().getPlayerList().getIpBans()).addBan(address, reason, duration, source);
        if (kickPlayer) kick(Component.text(reason == null ? "Banned by an operator." : reason));
        return entry;
    }

    @Override
    public @Nullable BanEntry<InetAddress> banIp(@Nullable String reason, @Nullable Instant expires, @Nullable String source, boolean kickPlayer) {
        InetAddress address = getAddress() == null ? null : getAddress().getAddress();
        if (address == null) return null;
        BanEntry<InetAddress> entry = new org.bukkit.craftbukkit.ban.CraftIpBanList(this.server.getServer().getPlayerList().getIpBans()).addBan(address, reason, expires, source);
        if (kickPlayer) kick(Component.text(reason == null ? "Banned by an operator." : reason));
        return entry;
    }

    @Override
    public @Nullable BanEntry<InetAddress> banIp(@Nullable String reason, @Nullable Date expires, @Nullable String source, boolean kickPlayer) {
        InetAddress address = getAddress() == null ? null : getAddress().getAddress();
        if (address == null) return null;
        BanEntry<InetAddress> entry = new org.bukkit.craftbukkit.ban.CraftIpBanList(this.server.getServer().getPlayerList().getIpBans()).addBan(address, reason, expires, source);
        if (kickPlayer) kick(Component.text(reason == null ? "Banned by an operator." : reason));
        return entry;
    }


    @Override public Player getKiller() {
        net.minecraft.world.entity.LivingEntity killer = getHandle().getKillCredit();
        if (killer instanceof net.minecraft.server.level.ServerPlayer player) {
            org.bukkit.entity.Entity bukkit = CraftEntity.getEntity(server, player);
            return bukkit instanceof Player p ? p : null;
        }
        return null;
    }
    @Override public void setArrowsInBody(int count, boolean fireEvent) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        if (fireEvent) {
            getHandle().setArrowCount(count);
        } else {
            getHandle().getEntityData().set(((LivingEntityBridge) (Object) getHandle()).lunararc$getArrowCountDataAccessorBridge(), count);
        }
    }
    @Override public org.bukkit.Sound getFallDamageSoundBig() { return org.bukkit.Sound.ENTITY_PLAYER_BIG_FALL; }
    @Override public org.bukkit.Sound getFallDamageSoundSmall() { return org.bukkit.Sound.ENTITY_PLAYER_SMALL_FALL; }
    @Override public @Nullable Entity getTargetEntity(int distance, boolean ignoreBlocks) {
        RayTraceResult result = rayTraceEntities(distance, ignoreBlocks);
        return result == null ? null : result.getHitEntity();
    }
    @Override public @Nullable RayTraceResult rayTraceEntities(int distance, boolean ignoreBlocks) {
        RayTraceResult entityHit = getWorld().rayTraceEntities(
                getEyeLocation(), getEyeLocation().getDirection(), distance, 0.0D,
                candidate -> candidate != this);
        if (entityHit == null || ignoreBlocks) return entityHit;

        RayTraceResult blockHit = rayTraceBlocks(distance, FluidCollisionMode.NEVER);
        if (blockHit == null || blockHit.getHitPosition() == null || entityHit.getHitPosition() == null) return entityHit;
        double entityDistance = entityHit.getHitPosition().distanceSquared(getEyeLocation().toVector());
        double blockDistance = blockHit.getHitPosition().distanceSquared(getEyeLocation().toVector());
        return blockDistance < entityDistance ? null : entityHit;
    }
    @Override public void broadcastSlotBreak(EquipmentSlot slot) {
        net.minecraft.world.entity.EquipmentSlot nmsSlot = org.bukkit.craftbukkit.CraftEquipmentSlot.getNMS(slot);
        getHandle().level().broadcastEntityEvent(getHandle(), ((LivingEntityBridge) (Object) getHandle()).lunararc$entityEventForEquipmentBreakBridge(nmsSlot));
    }
    @Override public void broadcastSlotBreak(EquipmentSlot slot, Collection<Player> players) {
        java.util.Objects.requireNonNull(players, "players");
        if (players.isEmpty()) return;
        net.minecraft.world.entity.EquipmentSlot nmsSlot = org.bukkit.craftbukkit.CraftEquipmentSlot.getNMS(slot);
        net.minecraft.network.protocol.game.ClientboundEntityEventPacket packet = new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(
                getHandle(), ((LivingEntityBridge) (Object) getHandle()).lunararc$entityEventForEquipmentBreakBridge(nmsSlot));
        for (Player player : players) {
            if (!(player instanceof CraftPlayer craftPlayer)) {
                throw new IllegalArgumentException("Unsupported Player implementation: " + player.getClass().getName());
            }
            if (craftPlayer.getHandle().connection != null) {
                craftPlayer.getHandle().connection.send(packet);
            }
        }
    }
    @Override public void setGliding(boolean gliding) {
        if (!gliding && getHandle().isFallFlying()) getHandle().stopFallFlying();
        else if (gliding && !getHandle().isFallFlying()) getHandle().startFallFlying();
    }
    @Override public void attack(Entity target) {
        if (target instanceof CraftEntity craftEntity) getHandle().attack(craftEntity.entity);
    }
    @Override public boolean canBreatheUnderwater() { return getHandle().canBreatheUnderwater(); }
    @Override public void setKiller(Player killer) {
        if (killer != null && !(killer instanceof CraftPlayer)) {
            throw new IllegalArgumentException("Unsupported Player implementation: " + killer.getClass().getName());
        }
        net.minecraft.server.level.ServerPlayer nmsKiller = killer == null ? null : ((CraftPlayer) killer).getHandle();
        LivingEntityAccessBridge accessor = (LivingEntityAccessBridge) getHandle();
        accessor.lunararc$setLastHurtByPlayer(net.minecraft.world.entity.EntityReference.of(nmsKiller));
        accessor.lunararc$setLastHurtByMob(net.minecraft.world.entity.EntityReference.of(nmsKiller));
        accessor.lunararc$setLastHurtByPlayerTime(nmsKiller == null ? 0 : 100);
    }
    // Deprecated-for-removal Paper API that LivingEntity still declares - see the note on
    // CraftLivingEntity's matching overloads for why these are suppressed per-method.
    @SuppressWarnings("removal")
    @Override public @Nullable Block getTargetBlock(int distance, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode fluidMode) {
        RayTraceResult result = rayTraceBlocks(distance, fluidMode == com.destroystokyo.paper.block.TargetBlockInfo.FluidMode.ALWAYS ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER);
        return result != null ? result.getHitBlock() : null;
    }
    @Override public @NotNull Block getTargetBlock(@Nullable Set<Material> transparent, int distance) {
        List<Block> blocks = getLineOfSight(transparent, distance);
        return blocks.isEmpty() ? getEyeLocation().getBlock() : blocks.get(blocks.size() - 1);
    }
    @Override public @NotNull List<Block> getLastTwoTargetBlocks(@Nullable Set<Material> transparent, int distance) {
        List<Block> blocks = getLineOfSight(transparent, distance);
        int from = Math.max(0, blocks.size() - 2);
        return new ArrayList<>(blocks.subList(from, blocks.size()));
    }
    @Override public int getNoDamageTicks() { return getHandle().invulnerableTime; }
    @Override public EntityCategory getCategory() { return EntityCategory.NONE; }
    @Override public void setRemainingAir(int air) { getHandle().setAirSupply(air); }
    @Override public double getEyeHeight() { return getHandle().getEyeHeight(); }
    @Override public double getEyeHeight(boolean ignorePose) { return getHandle().getEyeHeight(); }
    @Override public float getBodyYaw() { return getHandle().yBodyRot; }
    @Override public void setBodyYaw(float yaw) { getHandle().yBodyRot = yaw; }
    @Override public org.bukkit.Sound getDrinkingSound(ItemStack itemStack) { return org.bukkit.Sound.ENTITY_GENERIC_DRINK; }
    @Override public boolean hasLineOfSight(Location location) {
        if (location == null || location.getWorld() != getWorld()) return false;
        org.bukkit.util.Vector direction = location.toVector().subtract(getEyeLocation().toVector());
        double distance = direction.length();
        org.bukkit.util.RayTraceResult hit = rayTraceBlocks(distance, FluidCollisionMode.NEVER);
        return hit == null || hit.getHitPosition().distanceSquared(getEyeLocation().toVector()) + 1.0E-6 >= distance * distance;
    }
    @Override public boolean hasLineOfSight(Entity other) {
        return other instanceof CraftEntity craftEntity && getHandle().hasLineOfSight(craftEntity.entity);
    }
    @Override public org.bukkit.Sound getHurtSound() { return org.bukkit.Sound.ENTITY_PLAYER_HURT; }
    @Override public org.bukkit.Sound getDeathSound() { return org.bukkit.Sound.ENTITY_PLAYER_DEATH; }
    @Override public Block getTargetBlockExact(int distance) {
        return getTargetBlockExact(distance, FluidCollisionMode.NEVER);
    }
    @Override public Block getTargetBlockExact(int distance, FluidCollisionMode fluidCollisionMode) {
        RayTraceResult result = rayTraceBlocks(distance, fluidCollisionMode);
        return result == null ? null : result.getHitBlock();
    }
    @Override public float getUpwardsMovement() { return ((LivingEntityAccessBridge) getHandle()).lunararc$getUpwardsMovement(); }
    @Override public float getSidewaysMovement() { return ((LivingEntityAccessBridge) getHandle()).lunararc$getSidewaysMovement(); }
    @Override public float getForwardsMovement() { return ((LivingEntityAccessBridge) getHandle()).lunararc$getForwardsMovement(); }
    @Override public boolean isRiptiding() { return getHandle().isAutoSpinAttack(); }
    @Override public boolean isClimbing() { return getHandle().onClimbable(); }
    @Override
    public Collection<PotionEffect> getActivePotionEffects() {
        java.util.List<PotionEffect> out = new java.util.ArrayList<>();
        for (net.minecraft.world.effect.MobEffectInstance fx : getHandle().getActiveEffects()) {
            PotionEffectType type = PotionEffectType.getByKey(
                org.bukkit.NamespacedKey.fromString(
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(fx.getEffect().value()).toString()));
            if (type != null)
                out.add(new PotionEffect(type, fx.getDuration(), fx.getAmplifier(), fx.isAmbient(), fx.isVisible()));
        }
        return out;
    }
    @Override
    public boolean hasPotionEffect(PotionEffectType type) {
        if (type == null) return false;
        net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(type.getKey().toString());
        var effectOpt = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(rl);
        return effectOpt.isPresent() && getHandle().hasEffect(effectOpt.get());
    }
    @Override public void knockback(double strength, double x, double z) {
        if (strength <= 0) throw new IllegalArgumentException("Knockback strength must be > 0");
        getHandle().knockback(strength, x, z);
    }
    @Override public void setShieldBlockingDelay(int delay) {
        ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle()).lunararc$setShieldBlockingDelay(delay);
    }
    @Override public void setArrowCooldown(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        ((LivingEntityAccessBridge) getHandle()).lunararc$setRemoveArrowTime(ticks);
    }
    @Override public void setNoActionTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        getHandle().setNoActionTime(ticks);
    }
    @Override public void completeUsingActiveItem() { ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle()).lunararc$completeUsingItem(); }
    @Override public boolean canUseEquipmentSlot(EquipmentSlot slot) {
        return ((LivingEntityAccessBridge) getHandle()).lunararc$canUseSlot(
                org.bukkit.craftbukkit.CraftEquipmentSlot.getNMS(slot));
    }
    @Override public <T> void setMemory(MemoryKey<T> key, T value) {
        java.util.Objects.requireNonNull(key, "key");
        getHandle().getBrain().setMemory(
                org.bukkit.craftbukkit.entity.memory.CraftMemoryKey.bukkitToMinecraft(key),
                org.bukkit.craftbukkit.entity.memory.CraftMemoryMapper.toNms(value));
    }
    @Override @SuppressWarnings("unchecked") public <T> T getMemory(MemoryKey<T> key) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Optional<?> memory = getHandle().getBrain().getMemoryInternal(
                org.bukkit.craftbukkit.entity.memory.CraftMemoryKey.bukkitToMinecraft(key));
        return memory == null ? null : (T) memory.map(org.bukkit.craftbukkit.entity.memory.CraftMemoryMapper::fromNms).orElse(null);
    }
    @Override public float getHurtDirection() { return getHandle().hurtDir; }
    @Override public void setHurtDirection(float direction) { getHandle().hurtDir = direction; }
    @Override public void damageItemStack(EquipmentSlot slot, int amount) {
        net.minecraft.world.entity.EquipmentSlot nmsSlot =
                org.bukkit.craftbukkit.CraftEquipmentSlot.getNMS(slot);
        ((ItemStackBridge) (Object) getHandle().getItemBySlot(nmsSlot)).lunararc$hurtAndBreak(
                amount, getHandle(), nmsSlot, true);
    }
    @Override public ItemStack damageItemStack(ItemStack stack, int amount) {
        java.util.Objects.requireNonNull(stack, "stack");
        net.minecraft.world.item.ItemStack nmsStack;
        ItemStack result = stack;
        if (stack instanceof CraftItemStack craft) {
            if (craft.handle == null || craft.handle.isEmpty()) return stack;
            nmsStack = craft.handle;
        } else {
            nmsStack = CraftItemStack.asNMSCopy(stack);
            result = CraftItemStack.asCraftMirror(nmsStack);
        }
        ((ItemStackBridge) (Object) nmsStack).lunararc$hurtAndBreak(amount, getHandle(), null, true);
        return result;
    }
    @Override public void setNoDamageTicks(int ticks) { getHandle().invulnerableTime = Math.max(0, ticks); }
    @Override public boolean isGliding() { return getHandle().isFallFlying(); }
    @Override public Set<UUID> getCollidableExemptions() { return super.getCollidableExemptions(); }
    @Override public org.bukkit.Sound getFallDamageSound(int fallDistance) { return org.bukkit.Sound.ENTITY_GENERIC_SMALL_FALL; }
    @Override public void setNextArrowRemoval(int ticks) { setArrowCooldown(ticks); }
    @Override public Entity getLeashHolder() {
        throw new IllegalStateException("Entity not leashed");
    }
    @SuppressWarnings("removal")
    @Override public @Nullable org.bukkit.block.BlockFace getTargetBlockFace(int distance, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode fluidMode) {
        return getTargetBlockFace(distance,
                fluidMode == com.destroystokyo.paper.block.TargetBlockInfo.FluidMode.ALWAYS
                        ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER);
    }
    @Override public @Nullable org.bukkit.block.BlockFace getTargetBlockFace(int distance, @NotNull FluidCollisionMode fluidMode) {
        RayTraceResult result = rayTraceBlocks(distance, fluidMode);
        return result == null ? null : result.getHitBlockFace();
    }


    @SuppressWarnings("removal")
    @Override public boolean setWindowProperty(InventoryView.Property prop, int value) {
        if (prop == null) return false;
        return getOpenInventory().setProperty(prop, value);
    }
    @Override public void wakeup(boolean setSpawnLocation) {
        if (!getHandle().isSleeping()) throw new IllegalStateException("Cannot wakeup if not sleeping");
        ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerBedBridge) getHandle())
                .lunararc$setNextBedLeaveShouldSetSpawn(setSpawnLocation);
        getHandle().stopSleepInBed(true, true);
    }
    @Override public ItemStack getItemOnCursor() {
        return CraftItemStack.asBukkitCopy(getHandle().containerMenu.getCarried());
    }
    @Override public void setItemOnCursor(ItemStack item) {
        getHandle().containerMenu.setCarried(CraftItemStack.asNMSCopy(item));
        getHandle().containerMenu.broadcastChanges();
    }
    private final Set<NamespacedKey> lunararcDiscoveredRecipes = ConcurrentHashMap.newKeySet();
    private java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> resolveRecipes(Collection<NamespacedKey> recipes) {
        java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> resolved = new java.util.ArrayList<>();
        var manager = getHandle().server.getRecipeManager();
        for (NamespacedKey key : recipes) {
            if (key == null) continue;
            try {
                var id = net.minecraft.resources.Identifier.parse(key.toString());
                manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id)).ifPresent(resolved::add);
            } catch (Throwable ignored) {}
        }
        return resolved;
    }
    @Override public int discoverRecipes(Collection<NamespacedKey> recipes) {
        if (recipes == null) throw new IllegalArgumentException("recipes cannot be null");
        var resolved = resolveRecipes(recipes);
        int changed = getHandle().awardRecipes(resolved);
        for (NamespacedKey key : recipes) if (key != null) lunararcDiscoveredRecipes.add(key);
        return changed;
    }
    @Override public int undiscoverRecipes(Collection<NamespacedKey> recipes) {
        if (recipes == null) throw new IllegalArgumentException("recipes cannot be null");
        var resolved = resolveRecipes(recipes);
        int changed = getHandle().resetRecipes(resolved);
        lunararcDiscoveredRecipes.removeAll(recipes);
        return changed;
    }
    @Override public EntityEquipment getEquipment() { return inventory; }
    @Override public void setStarvationRate(int rate) {
        if (rate < 0) throw new IllegalArgumentException("rate must be >= 0");
        ((io.lunararcdevs.lunararc.common.bridge.FoodDataBridge) getHandle().getFoodData()).lunararc$setStarvationRate(rate);
    }
    @Override public boolean isBlocking() { return getHandle().isBlocking(); }
    @Override public void setSaturatedRegenRate(int rate) {
        if (rate < 0) throw new IllegalArgumentException("rate must be >= 0");
        ((io.lunararcdevs.lunararc.common.bridge.FoodDataBridge) getHandle().getFoodData()).lunararc$setSaturatedRegenRate(rate);
    }
    @Override public float getExhaustion() {
        return getHandle().getFoodData().exhaustionLevel;
    }
    private net.minecraft.world.item.Item resolveItem(Material material) {
        if (material == null) throw new IllegalArgumentException("material cannot be null");
        if (!material.isItem()) throw new IllegalArgumentException(material + " is not an item");
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(material.getKey().toString()));
    }
    @Override public boolean hasCooldown(Material material) { return getHandle().getCooldowns().isOnCooldown(new net.minecraft.world.item.ItemStack(resolveItem(material))); }
    @Override public void setCooldown(Material material, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        getHandle().getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(resolveItem(material)), ticks);
    }
    @Override public GameMode getGameMode() { return fromNMS(getHandle().gameMode.getGameModeForPlayer()); }
    @Override public void setGameMode(GameMode mode) {
        if (mode == null) throw new IllegalArgumentException("GameMode cannot be null");
        if (mode == getGameMode()) return;
        org.bukkit.event.player.PlayerGameModeChangeEvent event =
                new org.bukkit.event.player.PlayerGameModeChangeEvent(this, mode);
        getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        getHandle().setGameMode(toNMS(mode));
        syncAbilities();
    }
    @Override public Entity releaseRightShoulderEntity() { return releaseShoulderEntity(false); }
    @Override public Entity releaseLeftShoulderEntity() { return releaseShoulderEntity(true); }
    private Entity releaseShoulderEntity(boolean left) {
        net.minecraft.nbt.CompoundTag tag = left ? getHandle().getShoulderEntityLeft() : getHandle().getShoulderEntityRight();
        if (tag == null || tag.isEmpty()) return null;
        try {
            var created = lunararc$createFromTag(tag);
            if (created.isEmpty()) return null;
            net.minecraft.world.entity.Entity nms = created.get();
            nms.setPos(getHandle().getX(), getHandle().getY() + 0.7D, getHandle().getZ());
            if (left) getHandle().setShoulderEntityLeft(new net.minecraft.nbt.CompoundTag());
            else getHandle().setShoulderEntityRight(new net.minecraft.nbt.CompoundTag());
            ((io.lunararcdevs.lunararc.common.bridge.ServerLevelBridge) getHandle().level())
                    .lunararc$addFreshEntity(nms, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            return ((EntityBridge) nms).lunararc$getBukkitEntity();
        } catch (Throwable ignored) { return null; }
    }
    private java.util.Optional<net.minecraft.world.entity.Entity> lunararc$createFromTag(net.minecraft.nbt.CompoundTag tag) {
        return net.minecraft.world.entity.EntityType.create(
                net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, getHandle().registryAccess(), tag),
                getHandle().level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
    }
    private Entity shoulderEntity(net.minecraft.nbt.CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        try {
            var created = lunararc$createFromTag(tag);
            if (created.isEmpty()) return null;
            return ((EntityBridge) created.get()).lunararc$getBukkitEntity();
        } catch (Throwable ignored) { return null; }
    }
    @Override public Entity getShoulderEntityLeft() { return shoulderEntity(getHandle().getShoulderEntityLeft()); }
    @Override public Entity getShoulderEntityRight() { return shoulderEntity(getHandle().getShoulderEntityRight()); }
    private net.minecraft.nbt.CompoundTag saveShoulder(Entity entity) {
        if (entity == null) return new net.minecraft.nbt.CompoundTag();
        if (!(entity instanceof CraftEntity craft)) throw new IllegalArgumentException("Entity is not a LunarArc/Craft entity");
        net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, getHandle().registryAccess());
        craft.getHandle().save(output);
        net.minecraft.nbt.CompoundTag tag = output.buildResult();
        entity.remove();
        return tag;
    }
    @Override public void setShoulderEntityLeft(Entity entity) { getHandle().setShoulderEntityLeft(saveShoulder(entity)); }
    @Override public void setShoulderEntityRight(Entity entity) { getHandle().setShoulderEntityRight(saveShoulder(entity)); }
    @Override public boolean hasDiscoveredRecipe(NamespacedKey recipe) { return recipe != null && lunararcDiscoveredRecipes.contains(recipe); }
    @Override public int getUnsaturatedRegenRate() { return ((io.lunararcdevs.lunararc.common.bridge.FoodDataBridge) getHandle().getFoodData()).lunararc$getUnsaturatedRegenRate(); }
    @Override public int getSleepTicks() { return getHandle().getSleepTimer(); }
    @Override public boolean undiscoverRecipe(NamespacedKey recipe) { return undiscoverRecipes(java.util.Collections.singleton(recipe)) != 0; }
    @Override public boolean sleep(Location location, boolean force) {
        if (location == null || location.getWorld() == null) throw new IllegalArgumentException("location/world cannot be null");
        if (!location.getWorld().equals(getWorld())) throw new IllegalArgumentException("Cannot sleep across worlds");
        var pos = new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        var state = getHandle().level().getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return false;
        try { return getHandle().startSleepInBed(pos).left().isEmpty(); }
        catch (Throwable t) { return false; }
    }
    @Override public boolean isDeeplySleeping() { return getHandle().isSleepingLongEnough(); }
    @Override public int getStarvationRate() { return ((io.lunararcdevs.lunararc.common.bridge.FoodDataBridge) getHandle().getFoodData()).lunararc$getStarvationRate(); }
    @Override public Set<NamespacedKey> getDiscoveredRecipes() { return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(lunararcDiscoveredRecipes)); }
    @Override public int getSaturatedRegenRate() { return ((io.lunararcdevs.lunararc.common.bridge.FoodDataBridge) getHandle().getFoodData()).lunararc$getSaturatedRegenRate(); }
    @Override public Location getBedLocation() {
        var pos = getHandle().getSleepingPos().orElseThrow(() -> new IllegalStateException("Not sleeping"));
        return new Location(getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }
    @Override public void setSaturation(float value) {
        getHandle().getFoodData().setSaturation(value);
    }
    @Override public FishHook getFishHook() {
        try { return getHandle().fishing == null ? null : (FishHook) ((EntityBridge)getHandle().fishing).lunararc$getBukkitEntity(); }
        catch (Throwable ignored) { return null; }
    }
    @Override public void startRiptideAttack(int duration, float attackDamage, ItemStack itemStack) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be > 0");
        if (attackDamage < 0) throw new IllegalArgumentException("attackDamage must be >= 0");
        getHandle().startAutoSpinAttack(duration, attackDamage, CraftItemStack.asNMSCopy(itemStack));
    }
    @Override public boolean isHandRaised() { return getHandle().isUsingItem(); }
    @Override public void setLastDeathLocation(Location location) {
        java.util.Optional<net.minecraft.core.GlobalPos> value;
        if (location == null) {
            value = java.util.Optional.empty();
        } else {
            if (!(location.getWorld() instanceof org.bukkit.craftbukkit.CraftWorld world)) {
                throw new IllegalArgumentException("Location must reference a LunarArc CraftWorld");
            }
            value = java.util.Optional.of(net.minecraft.core.GlobalPos.of(
                    world.getHandle().dimension(),
                    new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ())));
        }
        getHandle().setLastDeathLocation(value);
    }
    @Override public int getCooldown(Material material) {
        Object cooldowns = getHandle().getCooldowns();
        Object item = resolveItem(material);
        try {
            java.lang.reflect.Field mapField = cooldowns.getClass().getDeclaredField("cooldowns"); mapField.setAccessible(true);
            Object map = mapField.get(cooldowns);
            Object entry = ((java.util.Map<?,?>) map).get(item);
            if (entry == null) return 0;
            java.lang.reflect.Field end = entry.getClass().getDeclaredField("endTime"); end.setAccessible(true);
            java.lang.reflect.Field tick = cooldowns.getClass().getDeclaredField("tickCount"); tick.setAccessible(true);
            return Math.max(0, end.getInt(entry) - tick.getInt(cooldowns));
        } catch (Throwable ignored) { return hasCooldown(material) ? 1 : 0; }
    }
    @Override public void setUnsaturatedRegenRate(int rate) {
        if (rate < 0) throw new IllegalArgumentException("rate must be >= 0");
        ((io.lunararcdevs.lunararc.common.bridge.FoodDataBridge) getHandle().getFoodData()).lunararc$setUnsaturatedRegenRate(rate);
    }
    @Override public boolean dropItem(boolean dropAll) {
        net.minecraft.world.item.ItemStack before = getHandle().getMainHandItem().copy();
        getHandle().drop(dropAll);
        boolean success = !net.minecraft.world.item.ItemStack.matches(before, getHandle().getMainHandItem());
        if (success) getHandle().containerMenu.broadcastChanges();
        return success;
    }
    @Override public ItemStack getItemInHand() { return CraftItemStack.asBukkitCopy(getHandle().getMainHandItem()); }
    @Override public void setItemInHand(ItemStack item) {
        getHandle().getInventory().setItem(getHandle().getInventory().selected, CraftItemStack.asNMSCopy(item));
        getHandle().inventoryMenu.broadcastChanges();
    }
    @Override public float getSaturation() { return getHandle().getFoodData().getSaturationLevel(); }
    @Override public void setExhaustion(float value) { getHandle().getFoodData().exhaustionLevel = value; }
    @Override public float getAttackCooldown() { return getHandle().getAttackStrengthScale(0.5F); }
    @Override public int getExpToLevel() { return getHandle().getXpNeededForNextLevel(); }
    @Override public Location getPotentialBedLocation() {
        var config = getHandle().getRespawnConfig();
        if (config == null) return null;
        var pos = config.respawnData().pos();
        var level = getHandle().level().getServer().getLevel(config.respawnData().dimension());
        if (level == null) return null;
        org.bukkit.World bw = server.getWorld(level.dimension().identifier().toString());
        if (bw == null) bw = getWorld();
        return new Location(bw, pos.getX(), pos.getY(), pos.getZ());
    }
    @Override
    public void openBook(@NotNull ItemStack book) {
        com.google.common.base.Preconditions.checkArgument(book != null, "ItemStack cannot be null");
        com.google.common.base.Preconditions.checkArgument(
                book.getType() == Material.WRITTEN_BOOK,
                "ItemStack Material (%s) must be Material.WRITTEN_BOOK",
                book.getType());

        ItemStack previousItem = this.getInventory().getItemInMainHand();
        this.getInventory().setItemInMainHand(book);
        try {
            this.getHandle().openItemGui(
                    CraftItemStack.asNMSCopy(book),
                    net.minecraft.world.InteractionHand.MAIN_HAND);
        } finally {
            this.getInventory().setItemInMainHand(previousItem);
        }
    }

    @Override public PlayerInventory getInventory() { return inventory; }
    @Override public Inventory getEnderChest() { return enderChest; }
    @Override public MainHand getMainHand() { return getHandle().getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? MainHand.LEFT : MainHand.RIGHT; }

    private InventoryView makeView(net.minecraft.world.inventory.AbstractContainerMenu menu, Inventory top, org.bukkit.event.inventory.InventoryType type, net.kyori.adventure.text.Component title) {
        return new org.bukkit.craftbukkit.inventory.CraftInventoryView(this, menu, top, inventory, type, title);
    }
    private org.bukkit.event.inventory.InventoryType inferInventoryType(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu == getHandle().inventoryMenu) return org.bukkit.event.inventory.InventoryType.CRAFTING;
        if (menu instanceof net.minecraft.world.inventory.ChestMenu) return org.bukkit.event.inventory.InventoryType.CHEST;
        if (menu instanceof net.minecraft.world.inventory.CraftingMenu) return org.bukkit.event.inventory.InventoryType.WORKBENCH;
        if (menu instanceof net.minecraft.world.inventory.FurnaceMenu) return org.bukkit.event.inventory.InventoryType.FURNACE;
        if (menu instanceof net.minecraft.world.inventory.BlastFurnaceMenu) return org.bukkit.event.inventory.InventoryType.BLAST_FURNACE;
        if (menu instanceof net.minecraft.world.inventory.SmokerMenu) return org.bukkit.event.inventory.InventoryType.SMOKER;
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) return org.bukkit.event.inventory.InventoryType.ANVIL;
        if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) return org.bukkit.event.inventory.InventoryType.ENCHANTING;
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu) return org.bukkit.event.inventory.InventoryType.BREWING;
        if (menu instanceof net.minecraft.world.inventory.BeaconMenu) return org.bukkit.event.inventory.InventoryType.BEACON;
        if (menu instanceof net.minecraft.world.inventory.HopperMenu) return org.bukkit.event.inventory.InventoryType.HOPPER;
        if (menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu) return org.bukkit.event.inventory.InventoryType.SHULKER_BOX;
        if (menu instanceof net.minecraft.world.inventory.HorseInventoryMenu) return org.bukkit.event.inventory.InventoryType.CHEST;
        if (menu instanceof net.minecraft.world.inventory.MerchantMenu) return org.bukkit.event.inventory.InventoryType.MERCHANT;
        if (menu instanceof net.minecraft.world.inventory.LecternMenu) return org.bukkit.event.inventory.InventoryType.LECTERN;
        if (menu instanceof net.minecraft.world.inventory.LoomMenu) return org.bukkit.event.inventory.InventoryType.LOOM;
        if (menu instanceof net.minecraft.world.inventory.GrindstoneMenu) return org.bukkit.event.inventory.InventoryType.GRINDSTONE;
        if (menu instanceof net.minecraft.world.inventory.CartographyTableMenu) return org.bukkit.event.inventory.InventoryType.CARTOGRAPHY;
        if (menu instanceof net.minecraft.world.inventory.StonecutterMenu) return org.bukkit.event.inventory.InventoryType.STONECUTTER;
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) return org.bukkit.event.inventory.InventoryType.SMITHING;
        return org.bukkit.event.inventory.InventoryType.CHEST;
    }
    @Override public InventoryView getOpenInventory() {
        var menu = getHandle().containerMenu;
        // A view a plugin opened is handed back as the same object for the life of the container,
        // the way CraftBukkit's AbstractContainerMenu.bukkitView does. Rebuilding one from the menu
        // instead - which is all this used to do - produced a fresh CraftInventory over the same
        // slots, with the plugin's InventoryHolder gone. A GUI whose click handler asks
        // "is this my inventory?" then said no, declined to cancel, and the player simply took the
        // item out of the menu: the exact symptom of a GUI that opens and does nothing.
        InventoryView opened = ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) menu)
                .lunararc$getBukkitView();
        if (opened != null) return opened;
        org.bukkit.event.inventory.InventoryType type = inferInventoryType(menu);
        if (menu instanceof net.minecraft.world.inventory.MerchantMenu merchantMenu) {
            return new org.bukkit.craftbukkit.inventory.CraftMerchantView(
                    this, merchantMenu, net.kyori.adventure.text.Component.translatable("merchant.trades"));
        }
        if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu enchantmentMenu) {
            return new org.bukkit.craftbukkit.inventory.CraftEnchantmentView(
                    this, enchantmentMenu, net.kyori.adventure.text.Component.translatable("container.enchant"));
        }
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu anvilMenu) {
            return new org.bukkit.craftbukkit.inventory.CraftAnvilView(
                    this, anvilMenu, net.kyori.adventure.text.Component.translatable("container.repair"));
        }
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu smithingMenu) {
            var top = new org.bukkit.craftbukkit.inventory.CraftSmithingInventory(smithingMenu, this);
            return makeView(menu, top, org.bukkit.event.inventory.InventoryType.SMITHING,
                    net.kyori.adventure.text.Component.translatable("container.upgrade"));
        }
        if (menu instanceof net.minecraft.world.inventory.GrindstoneMenu grindstoneMenu) {
            var top = new org.bukkit.craftbukkit.inventory.CraftGrindstoneInventory(grindstoneMenu, this);
            return makeView(menu, top, org.bukkit.event.inventory.InventoryType.GRINDSTONE,
                    net.kyori.adventure.text.Component.translatable("container.grindstone_title"));
        }
        if (menu instanceof net.minecraft.world.inventory.CartographyTableMenu cartographyMenu) {
            var top = new org.bukkit.craftbukkit.inventory.CraftCartographyInventory(cartographyMenu, this);
            return makeView(menu, top, org.bukkit.event.inventory.InventoryType.CARTOGRAPHY,
                    net.kyori.adventure.text.Component.translatable("container.cartography_table"));
        }
        if (menu instanceof net.minecraft.world.inventory.CraftingMenu craftingMenu) {
            var recipe = getHandle().level().recipeAccess().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                    craftingMenu.craftSlots.asCraftInput(), getHandle().level()).orElse(null);
            var top = new org.bukkit.craftbukkit.inventory.CraftInventoryCrafting(
                    craftingMenu.craftSlots, craftingMenu.resultSlots, this, recipe);
            return makeView(menu, top, org.bukkit.event.inventory.InventoryType.WORKBENCH,
                    net.kyori.adventure.text.Component.translatable("container.crafting"));
        }
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu inventoryMenu) {
            var recipe = getHandle().level().recipeAccess().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                    ((io.lunararcdevs.lunararc.common.bridge.access.InventoryMenuAccessBridge) (Object) inventoryMenu).lunararc$getCraftSlots().asCraftInput(), getHandle().level()).orElse(null);
            var top = new org.bukkit.craftbukkit.inventory.CraftInventoryCrafting(
                    ((io.lunararcdevs.lunararc.common.bridge.access.InventoryMenuAccessBridge) (Object) inventoryMenu).lunararc$getCraftSlots(), ((io.lunararcdevs.lunararc.common.bridge.access.InventoryMenuAccessBridge) (Object) inventoryMenu).lunararc$getResultSlots(), this, recipe);
            return makeView(menu, top, org.bukkit.event.inventory.InventoryType.CRAFTING,
                    net.kyori.adventure.text.Component.translatable("container.crafting"));
        }
        return makeView(menu, null, type, net.kyori.adventure.text.Component.text(type.name()));
    }
    @Override public InventoryView openInventory(Inventory opened) {
        if (opened == null) throw new IllegalArgumentException("inventory cannot be null");
        if (getHandle().connection == null) return null;
        int rows = Math.max(1, Math.min(6, (opened.getSize() + 8) / 9));
        if (opened.getSize() != rows * 9) throw new IllegalArgumentException("Unsupported custom inventory size " + opened.getSize() + "; chest inventories must be a multiple of 9 up to 54");


        if (getHandle().containerMenu != getHandle().inventoryMenu) {
            closeHandle(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW, false);
        }
        var adapter = new org.bukkit.craftbukkit.inventory.BukkitInventoryContainer(opened);
        net.kyori.adventure.text.Component adventureTitle = opened instanceof org.bukkit.craftbukkit.inventory.CraftInventory ci ? ci.title() : net.kyori.adventure.text.Component.text(opened.getType().name());
        net.minecraft.world.inventory.MenuType<?> menuType = switch (rows) {
            case 1 -> net.minecraft.world.inventory.MenuType.GENERIC_9x1;
            case 2 -> net.minecraft.world.inventory.MenuType.GENERIC_9x2;
            case 3 -> net.minecraft.world.inventory.MenuType.GENERIC_9x3;
            case 4 -> net.minecraft.world.inventory.MenuType.GENERIC_9x4;
            case 5 -> net.minecraft.world.inventory.MenuType.GENERIC_9x5;
            case 6 -> net.minecraft.world.inventory.MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Unsupported chest rows: " + rows);
        };
        int id = ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge) getHandle()).lunararc$nextContainerCounter();
        net.minecraft.world.inventory.AbstractContainerMenu menu = new net.minecraft.world.inventory.ChestMenu(
                menuType, id, getHandle().getInventory(), adapter, rows);
        InventoryView view = makeView(menu, opened, opened.getType(), adventureTitle);
        com.mojang.datafixers.util.Pair<net.kyori.adventure.text.Component, net.minecraft.world.inventory.AbstractContainerMenu> result =
                org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEventWithTitle(
                        getHandle(), menu, view, false);
        if (result.getSecond() == null) return null;
        getHandle().containerMenu = menu;
        ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) menu).lunararc$setOwner(getHandle());
        ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) menu).lunararc$setBukkitView(view);
        ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) menu).lunararc$setCheckReachable(false);
        net.kyori.adventure.text.Component finalTitle = result.getFirst() != null ? result.getFirst() : adventureTitle;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(id, menuType,
                io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(finalTitle)));
        ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge) getHandle()).lunararc$initMenu(menu);
        menu.broadcastFullState();
        if (opened instanceof org.bukkit.craftbukkit.inventory.CraftInventory ci) ci.onOpen(this);
        return view;
    }
    @Override public void openInventory(InventoryView view) {
        if (view == null) throw new IllegalArgumentException("inventory view cannot be null");
        if (!this.equals(view.getPlayer())) throw new IllegalArgumentException("InventoryView must belong to this player");
        if (view instanceof org.bukkit.craftbukkit.inventory.CraftInventoryView civ) {
            if (civ.getHandle() == getHandle().containerMenu) return;
            // the player's own inventory menu is always open on the client and has no screen packet
            if (civ.getHandle() == getHandle().inventoryMenu) return;
            if (getHandle().connection == null) return;
            if (getHandle().containerMenu != getHandle().inventoryMenu) {
                closeHandle(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW, false);
            }
            com.mojang.datafixers.util.Pair<net.kyori.adventure.text.Component, net.minecraft.world.inventory.AbstractContainerMenu> result =
                    org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEventWithTitle(
                            getHandle(), civ.getHandle(), view, false);
            if (result.getSecond() == null) return;
            getHandle().containerMenu = civ.getHandle();
            ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) civ.getHandle()).lunararc$setOwner(getHandle());
            ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) civ.getHandle()).lunararc$setBukkitView(civ);
            net.minecraft.world.inventory.AbstractContainerMenu menu = civ.getHandle();
            net.kyori.adventure.text.Component title = result.getFirst() != null ? result.getFirst() : view.title();
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(menu.containerId, menu.getType(),
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(title)));
            ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge) getHandle()).lunararc$initMenu(menu);
            if (menu instanceof net.minecraft.world.inventory.MerchantMenu
                    && ((io.lunararcdevs.lunararc.common.bridge.access.MerchantMenuAccessBridge) (Object) menu).lunararc$getTrader() instanceof net.minecraft.world.item.trading.Merchant trader) {
                trader.setTradingPlayer(getHandle());
                int level = trader instanceof net.minecraft.world.entity.npc.villager.Villager villager ? villager.getVillagerData().level() : 1;
                getHandle().sendMerchantOffers(menu.containerId, trader.getOffers(), level, trader.getVillagerXp(),
                        trader.showProgressBar(), trader.canRestock());
            }
        } else {
            openInventory(view.getTopInventory());
        }
    }
    private void closeHandle(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason, boolean notifyClient) {
        if (getHandle().containerMenu == getHandle().inventoryMenu) return;
        net.minecraft.world.inventory.AbstractContainerMenu menu = getHandle().containerMenu;
        ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge) getHandle())
                .lunararc$setNextInventoryCloseReason(reason == null
                        ? org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNKNOWN : reason);
        if (notifyClient) notifyClientClose(menu);
        getHandle().closeContainer();
    }

    private void notifyClientClose(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (getHandle().connection == null || menu == null) return;
        try {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundContainerClosePacket(menu.containerId));
        } catch (Throwable ignored) {}
    }
    private void applyTitleOverride(net.minecraft.world.inventory.AbstractContainerMenu menu, net.kyori.adventure.text.Component override) {
        if (override == null || getHandle().connection == null || menu == null) return;
        try {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                    menu.containerId, menu.getType(),
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(override)));
        } catch (Throwable ignored) {}
    }
    private InventoryView openBlockMenu(Location location, boolean force, Material expected, net.minecraft.world.level.block.Block menuBlock) {
        if (location == null) location = getLocation();
        if (location.getWorld() == null || !location.getWorld().equals(getWorld())) throw new IllegalArgumentException("Location must be in the player's world");
        if (!force && location.getBlock().getType() != expected) return null;
        var pos = new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        var provider = menuBlock.defaultBlockState().getMenuProvider(getHandle().level(), pos);
        if (provider == null) return null;
        if (getHandle().containerMenu != getHandle().inventoryMenu) {
            closeHandle(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW, false);
        }
        var before = getHandle().containerMenu;
        getHandle().openMenu(provider);
        if (getHandle().containerMenu == before) return null;
        ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) getHandle().containerMenu).lunararc$setCheckReachable(!force);
        return getOpenInventory();
    }
    @Override public InventoryView openWorkbench(Location location, boolean force) { return openBlockMenu(location, force, Material.CRAFTING_TABLE, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE); }
    @Override public InventoryView openEnchanting(Location location, boolean force) { return openBlockMenu(location, force, Material.ENCHANTING_TABLE, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE); }
    @Override public InventoryView openAnvil(Location location, boolean force) { return openBlockMenu(location, force, Material.ANVIL, net.minecraft.world.level.block.Blocks.ANVIL); }
    @Override public InventoryView openCartographyTable(Location location, boolean force) { return openBlockMenu(location, force, Material.CARTOGRAPHY_TABLE, net.minecraft.world.level.block.Blocks.CARTOGRAPHY_TABLE); }
    @Override public InventoryView openGrindstone(Location location, boolean force) { return openBlockMenu(location, force, Material.GRINDSTONE, net.minecraft.world.level.block.Blocks.GRINDSTONE); }
    @Override public InventoryView openLoom(Location location, boolean force) { return openBlockMenu(location, force, Material.LOOM, net.minecraft.world.level.block.Blocks.LOOM); }
    @Override public InventoryView openSmithingTable(Location location, boolean force) { return openBlockMenu(location, force, Material.SMITHING_TABLE, net.minecraft.world.level.block.Blocks.SMITHING_TABLE); }
    @Override public InventoryView openStonecutter(Location location, boolean force) { return openBlockMenu(location, force, Material.STONECUTTER, net.minecraft.world.level.block.Blocks.STONECUTTER); }
    @Override public InventoryView openMerchant(org.bukkit.entity.Villager villager, boolean force) { return openMerchant((Merchant) villager, force); }
    @Override public InventoryView openMerchant(Merchant merchant, boolean force) {
        if (merchant == null) throw new IllegalArgumentException("merchant cannot be null");
        if (!force && merchant.isTrading()) return null;
        if (merchant.isTrading() && merchant.getTrader() != null) merchant.getTrader().closeInventory();
        if (merchant instanceof CraftEntity craft && craft.getHandle() instanceof net.minecraft.world.entity.npc.villager.AbstractVillager av) {
            av.setTradingPlayer(getHandle());
            av.openTradingScreen(getHandle(), av.getDisplayName(), av instanceof net.minecraft.world.entity.npc.villager.Villager v ? v.getVillagerData().level() : 1);
            if (getHandle().containerMenu == getHandle().inventoryMenu) return null;
            return getOpenInventory();
        }
        if (merchant instanceof org.bukkit.craftbukkit.inventory.CraftMerchant craftMerchant) {
            net.minecraft.world.item.trading.Merchant nms = craftMerchant.getMerchant();
            nms.setTradingPlayer(getHandle());
            net.minecraft.network.chat.Component title = craftMerchant instanceof org.bukkit.craftbukkit.inventory.CraftMerchantCustom custom
                    ? custom.getTitle() : net.minecraft.network.chat.Component.literal("Merchant");
            nms.openTradingScreen(getHandle(), title, 1);
            if (getHandle().containerMenu == getHandle().inventoryMenu) return null;
            return getOpenInventory();
        }
        throw new IllegalArgumentException("Unsupported merchant implementation: " + merchant.getClass().getName());
    }
    @Override
    public void showDialog(final net.kyori.adventure.dialog.DialogLike dialog) {
        if (this.getHandle().connection == null) return;
        this.getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundShowDialogPacket(
                io.papermc.paper.dialog.PaperDialog.bukkitToMinecraftHolder((io.papermc.paper.dialog.Dialog) dialog)));
    }

    @Override
    public void closeDialog() {
        if (this.getHandle().connection == null) return;
        this.getHandle().connection.send(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);
    }

    @Override public void closeInventory() {
        closeInventory(InventoryCloseEvent.Reason.PLUGIN);
    }
    @Override public void closeInventory(InventoryCloseEvent.Reason reason) {
        if (reason == null) reason = InventoryCloseEvent.Reason.PLUGIN;
        try {
            if (getHandle().containerMenu == getHandle().inventoryMenu) return;
            closeHandle(reason, true);
        } catch (Throwable ignored) {
            try { getHandle().closeContainer(); } catch (Throwable ignoredAgain) {}
        }
    }
    @Override public boolean discoverRecipe(NamespacedKey recipe) { return discoverRecipes(java.util.Collections.singleton(recipe)) != 0; }
    @Override public int getEnchantmentSeed() { return getHandle().enchantmentSeed; }
    @Override public void setEnchantmentSeed(int seed) { getHandle().enchantmentSeed = seed; }
    @Override public void setFoodLevel(int value) {
        int clamped = Math.max(0, Math.min(20, value));
        getHandle().getFoodData().setFoodLevel(clamped);
    }


    @Override public boolean isOnline() { return isConnected(); }
    @Override public boolean isBanned() { return new org.bukkit.craftbukkit.ban.CraftProfileBanList(this.server.getServer().getPlayerList().getBans()).isBanned(getPlayerProfile()); }
    @Override public boolean isWhitelisted() { return whitelisted; }
    @Override public void setWhitelisted(boolean value) { this.whitelisted = value; }
    @Override public long getFirstPlayed() { return ((ServerPlayerBukkitDataBridge) getHandle()).lunararc$getFirstPlayed(); }
    public void setFirstPlayed(long firstPlayed) { ((ServerPlayerBukkitDataBridge) getHandle()).lunararc$setFirstPlayed(firstPlayed); }

    private Object getNmsStatsCounter() {
        return getHandle().getStats();
    }

    private static final java.util.Map<Class<?>, Class<?>> STATISTIC_BOXED_PRIMITIVES = java.util.Map.of(
            int.class, Integer.class, long.class, Long.class, double.class, Double.class,
            float.class, Float.class, boolean.class, Boolean.class, byte.class, Byte.class,
            short.class, Short.class, char.class, Character.class);

    private static boolean statisticArgumentMatches(Class<?> parameter, Object argument) {
        if (argument == null) return !parameter.isPrimitive();
        if (parameter.isPrimitive()) {
            return STATISTIC_BOXED_PRIMITIVES.get(parameter) == argument.getClass();
        }
        return parameter.isInstance(argument);
    }

    private static Object[] statisticArgumentsWith(Object[] apiArguments, Object trailing) {
        Object[] extended = new Object[apiArguments.length + 1];
        System.arraycopy(apiArguments, 0, extended, 0, apiArguments.length);
        extended[apiArguments.length] = trailing;
        return extended;
    }

    private static java.lang.reflect.Method findCraftStatisticMethod(Class<?> craftStatistic, String methodName,
                                                                     Object counter, Object[] arguments) {
        outer:
        for (java.lang.reflect.Method candidate : craftStatistic.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(candidate.getModifiers()) || !candidate.getName().equals(methodName)) continue;
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != arguments.length + 1 || !statisticArgumentMatches(parameters[0], counter)) continue;
            for (int i = 0; i < arguments.length; i++) {
                if (!statisticArgumentMatches(parameters[i + 1], arguments[i])) continue outer;
            }
            candidate.trySetAccessible();
            return candidate;
        }
        return null;
    }

    private Object invokeCraftStatistic(String methodName, Object... apiArguments) {
        final Object counter = getNmsStatsCounter();
        final Class<?> craftStatistic;
        try {


            craftStatistic = Class.forName("org.bukkit.craftbukkit.CraftStatistic", true, getClass().getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Paper CraftStatistic is missing from the LunarArc runtime", ex);
        }

        // Read off CraftBukkit's own source rather than guessed at, after two wrong attempts. The
        // real mutating signatures are
        //   incrementStatistic(ServerStatsCounter, Statistic, Material, int amount, ServerPlayer)
        // so the trailing argument is the *NMS* ServerPlayer, not the Bukkit Player. Appending
        // this CraftPlayer could never match it, which is why the previous fallback did nothing.
        // Both are tried, NMS handle first, so the shape actually present resolves either way.
        Object[] arguments = null;
        java.lang.reflect.Method selected = null;
        for (Object[] candidate : new Object[][] {
                apiArguments,
                statisticArgumentsWith(apiArguments, getHandle()),
                statisticArgumentsWith(apiArguments, this) }) {
            selected = findCraftStatisticMethod(craftStatistic, methodName, counter, candidate);
            if (selected != null) {
                arguments = candidate;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("Paper CraftStatistic does not expose compatible " + methodName
                    + " overload for " + java.util.Arrays.toString(apiArguments)
                    + " (also tried with a trailing ServerPlayer and a trailing Player)");
        }

        Object[] invocation = new Object[arguments.length + 1];
        invocation[0] = counter;
        System.arraycopy(arguments, 0, invocation, 1, arguments.length);
        try {
            return selected.invoke(null, invocation);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Paper CraftStatistic." + methodName + " failed", cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to invoke Paper CraftStatistic." + methodName, ex);
        }
    }

    private int getStatisticFromPaper(Statistic statistic, Object parameter) {
        Object result = parameter == null
            ? invokeCraftStatistic("getStatistic", statistic)
            : invokeCraftStatistic("getStatistic", statistic, parameter);
        if (!(result instanceof Number number)) {
            throw new IllegalStateException("Paper CraftStatistic.getStatistic returned " + (result == null ? "null" : result.getClass().getName()));
        }
        return number.intValue();
    }

    private void mutateStatisticWithPaper(String operation, Statistic statistic, Object parameter, int value) {
        if (value < 0) throw new IllegalArgumentException("Statistic value/amount cannot be negative");
        if (parameter == null) invokeCraftStatistic(operation, statistic, value);
        else invokeCraftStatistic(operation, statistic, parameter, value);
    }

    @Override public int getStatistic(Statistic statistic) { return getStatisticFromPaper(statistic, null); }
    @Override public int getStatistic(Statistic statistic, EntityType entityType) { return getStatisticFromPaper(statistic, java.util.Objects.requireNonNull(entityType, "entityType")); }
    @Override public int getStatistic(Statistic statistic, Material material) { return getStatisticFromPaper(statistic, java.util.Objects.requireNonNull(material, "material")); }
    @Override public void incrementStatistic(Statistic statistic) { incrementStatistic(statistic, 1); }
    @Override public void incrementStatistic(Statistic statistic, int amount) { mutateStatisticWithPaper("incrementStatistic", statistic, null, amount); }
    @Override public void incrementStatistic(Statistic statistic, Material material) { incrementStatistic(statistic, material, 1); }
    @Override public void incrementStatistic(Statistic statistic, Material material, int amount) { mutateStatisticWithPaper("incrementStatistic", statistic, java.util.Objects.requireNonNull(material, "material"), amount); }
    @Override public void incrementStatistic(Statistic statistic, EntityType entityType) { incrementStatistic(statistic, entityType, 1); }
    @Override public void incrementStatistic(Statistic statistic, EntityType entityType, int amount) { mutateStatisticWithPaper("incrementStatistic", statistic, java.util.Objects.requireNonNull(entityType, "entityType"), amount); }
    @Override public void decrementStatistic(Statistic statistic) { decrementStatistic(statistic, 1); }
    @Override public void decrementStatistic(Statistic statistic, int amount) { mutateStatisticWithPaper("decrementStatistic", statistic, null, amount); }
    @Override public void decrementStatistic(Statistic statistic, Material material) { decrementStatistic(statistic, material, 1); }
    @Override public void decrementStatistic(Statistic statistic, Material material, int amount) { mutateStatisticWithPaper("decrementStatistic", statistic, java.util.Objects.requireNonNull(material, "material"), amount); }
    @Override public void decrementStatistic(Statistic statistic, EntityType entityType) { decrementStatistic(statistic, entityType, 1); }
    @Override public void decrementStatistic(Statistic statistic, EntityType entityType, int amount) { mutateStatisticWithPaper("decrementStatistic", statistic, java.util.Objects.requireNonNull(entityType, "entityType"), amount); }
    @Override public void setStatistic(Statistic statistic, int newValue) { mutateStatisticWithPaper("setStatistic", statistic, null, newValue); }
    @Override public void setStatistic(Statistic statistic, Material material, int newValue) { mutateStatisticWithPaper("setStatistic", statistic, java.util.Objects.requireNonNull(material, "material"), newValue); }
    @Override public void setStatistic(Statistic statistic, EntityType entityType, int newValue) { mutateStatisticWithPaper("setStatistic", statistic, java.util.Objects.requireNonNull(entityType, "entityType"), newValue); }
    @Override public Player getPlayer() { return this; }


    @Override public void setAbsorptionAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) throw new IllegalArgumentException("Absorption amount must be finite and >= 0");
        getHandle().setAbsorptionAmount((float) amount);
    }
    @Override public void setMaxHealth(double health) { getHandle().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health); }
    // LunarArc: setHealth, heal and damage are inherited from CraftLivingEntity on purpose. Player-only copies here set
    // health without calling die() (EssentialsX /kill left players at 0 health with no death, grave or drops, and their
    // items were gone on respawn) and dropped the attacker and damage source.
    @Override public double getHealth() { return getHandle().getHealth(); }
    @Override public void resetMaxHealth() { setMaxHealth(20.0); }
    @Override public double getMaxHealth() { return getHandle().getMaxHealth(); }
    @Override public double getAbsorptionAmount() { return getHandle().getAbsorptionAmount(); }


    @Override public boolean isConversing() { return this.conversationTracker.isConversing(); }
    @Override public void acceptConversationInput(String input) { this.conversationTracker.acceptConversationInput(input); }


    @Override public int getProtocolVersion() { return net.minecraft.SharedConstants.getProtocolVersion(); }


    @Override public TriState getFrictionState() { return ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle()).lunararc$getFrictionState(); }
    @Override public void setFrictionState(TriState state) { ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle()).lunararc$setFrictionState(java.util.Objects.requireNonNull(state, "state")); }


    @Override public Map<String, Object> serialize() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("name", getName());
        return result;
    }

    @Override public boolean isSleeping() { return getHandle().isSleeping(); }
    @Override public void setCollidable(boolean collidable) { super.setCollidable(collidable); }
    @Override public boolean isCollidable() { return super.isCollidable(); }
    @Override public void setJumping(boolean jumping) { getHandle().setJumping(jumping); }
    @Override public boolean isJumping() { return ((LivingEntityAccessBridge) getHandle()).lunararc$isJumping(); }
    @Override public void setSwimming(boolean swimming) { getHandle().setSwimming(swimming); }
    @Override public boolean isSwimming() { return getHandle().isSwimming(); }
    @Override public int getActiveItemRemainingTime() { return getHandle().getUseItemRemainingTicks(); }
    @Override public void setNextBeeStingerRemoval(int ticks) { setBeeStingerCooldown(ticks); }
    @Override public int getNextBeeStingerRemoval() { return getBeeStingerCooldown(); }
    @Override public boolean addPotionEffects(Collection<PotionEffect> effects) {
        boolean result = false;
        for (PotionEffect effect : effects) if (addPotionEffect(effect)) result = true;
        return result;
    }
    @Override public @NotNull List<Block> getLineOfSight(@Nullable Set<Material> transparent, int distance) {
        if (distance > 120) throw new IllegalStateException("Max distance cannot exceed 120 blocks");
        if (distance < 0) throw new IllegalArgumentException("distance cannot be negative");
        List<Block> blocks = new ArrayList<>();
        org.bukkit.util.BlockIterator iterator = new org.bukkit.util.BlockIterator(this, distance);
        while (iterator.hasNext()) {
            Block block = iterator.next();
            blocks.add(block);
            Material type = block.getType();
            boolean isTransparent = transparent == null ? type.isAir() : transparent.contains(type);
            if (!isTransparent) break;
        }
        return blocks;
    }
    @Override public boolean hasAI() { return true; }
    @Override public void setAI(boolean ai) {  }
    @Override public void playPickupItemAnimation(Item item, int quantity) {
        if (item == null) throw new IllegalArgumentException("item cannot be null");
        if (item instanceof CraftEntity craft) getHandle().take(craft.getHandle(), quantity);
    }
    @Override public ItemStack getItemInUse() { return CraftItemStack.asBukkitCopy(getHandle().getUseItem()); }
    @Override public ItemStack getActiveItem() { return CraftItemStack.asBukkitCopy(getHandle().getUseItem()); }
    @Override public void clearActiveItem() { getHandle().stopUsingItem(); }
    @Override public void playHurtAnimation(float yaw) { sendHurtAnimation(yaw); }
    @Override public void setRemoveWhenFarAway(boolean remove) {}
    @Override public boolean getRemoveWhenFarAway() { return false; }
    @Override public int getShieldBlockingDelay() {
        return ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle()).lunararc$getShieldBlockingDelay();
    }
    @Override public void setBeeStingerCooldown(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        ((LivingEntityAccessBridge) getHandle()).lunararc$setRemoveStingerTime(ticks);
    }
    @Override public int getBeeStingerCooldown() { return ((LivingEntityAccessBridge) getHandle()).lunararc$getRemoveStingerTime(); }
    @SuppressWarnings("removal")
    @Override public @Nullable com.destroystokyo.paper.block.TargetBlockInfo getTargetBlockInfo(int distance, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode fluidMode) {
        FluidCollisionMode collisionMode = switch (fluidMode) {
            case ALWAYS -> FluidCollisionMode.ALWAYS;
            case SOURCE_ONLY -> FluidCollisionMode.SOURCE_ONLY;
            case NEVER -> FluidCollisionMode.NEVER;
        };
        RayTraceResult result = rayTraceBlocks(distance, collisionMode);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) return null;
        return new com.destroystokyo.paper.block.TargetBlockInfo(result.getHitBlock(), result.getHitBlockFace());
    }
    @SuppressWarnings("removal")
    @Override public @Nullable com.destroystokyo.paper.block.TargetBlockInfo getTargetBlockInfo(int distance) { return getTargetBlockInfo(distance, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode.NEVER); }
    @Override public @Nullable com.destroystokyo.paper.entity.TargetEntityInfo getTargetEntityInfo(int distance, boolean ignoreBlocks) {
        RayTraceResult result = rayTraceEntities(distance, ignoreBlocks);
        if (result == null || result.getHitEntity() == null || result.getHitPosition() == null) return null;
        return new com.destroystokyo.paper.entity.TargetEntityInfo(result.getHitEntity(), result.getHitPosition());
    }
    @Override public int getMaximumNoDamageTicks() { return super.getMaximumNoDamageTicks(); }
    @Override public void setMaximumNoDamageTicks(int ticks) { super.setMaximumNoDamageTicks(ticks); }
    @Override public int getActiveItemUsedTime() {
        if (!getHandle().isUsingItem()) return 0;
        net.minecraft.world.item.ItemStack useItem = getHandle().getUseItem();
        if (useItem == null || useItem.isEmpty()) return 0;
        try {
            return Math.max(0, useItem.getUseDuration(getHandle()) - getHandle().getUseItemRemainingTicks());
        } catch (Throwable ignored) {
            return 0;
        }
    }
    @Override public boolean isLeashed() { return false; }
    @Override public void setRiptiding(boolean riptiding) {
        LivingEntityAccessBridge accessor = (LivingEntityAccessBridge) getHandle();
        accessor.lunararc$setLivingEntityFlag(((LivingEntityBridge) (Object) getHandle()).lunararc$getSpinAttackFlagBridge(), riptiding);
    }
    @Override public RayTraceResult rayTraceBlocks(double distance) {
        return rayTraceBlocks(distance, FluidCollisionMode.NEVER);
    }
    @Override public RayTraceResult rayTraceBlocks(double distance, FluidCollisionMode fluidMode) {
        return getWorld().rayTraceBlocks(getEyeLocation(), getEyeLocation().getDirection(), distance, fluidMode, false);
    }
    @Override public Location getEyeLocation() {
        Location loc = getLocation();
        loc.setY(loc.getY() + getEyeHeight());
        return loc;
    }
    @Override public void swingMainHand() { getHandle().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    @Override public void swingOffHand() { getHandle().swing(net.minecraft.world.InteractionHand.OFF_HAND); }
    @Override public boolean setLeashHolder(Entity holder) { return false; }
    @Override public void setArrowsStuck(int arrows) {
        if (arrows < 0) throw new IllegalArgumentException("arrows must be >= 0");
        getHandle().setArrowCount(arrows);
    }
    @Override public int getArrowsStuck() { return getHandle().getArrowCount(); }
    @Override public boolean clearActivePotionEffects() {
        Collection<PotionEffect> active = getActivePotionEffects();
        if (active.isEmpty()) return false;
        for (PotionEffect e : active) removePotionEffect(e.getType());
        return true;
    }
    @Override public void setActiveItemRemainingTime(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        ((LivingEntityAccessBridge) getHandle()).lunararc$setUseItemRemaining(ticks);
    }
    @Override public void setLastDamage(double damage) { ((LivingEntityAccessBridge) getHandle()).lunararc$setLastHurt((float) damage); }
    @Override public double getLastDamage() { return ((LivingEntityAccessBridge) getHandle()).lunararc$getLastHurt(); }
    @Override
    public void removePotionEffect(PotionEffectType type) {
        if (type == null) return;
        try {
            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(type.getKey().toString());
            var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(rl).orElse(null);
            if (holder != null) getHandle().removeEffect(holder);
        } catch (Throwable ignored) {}
    }
    @Override public boolean hasActiveItem() { return getHandle().isUsingItem(); }
    @Override public int getMaximumAir() { return super.getMaximumAir(); }
    @Override public int getRemainingAir() { return getHandle().getAirSupply(); }
    @Override public void setMaximumAir(int air) { super.setMaximumAir(air); }
    @Override public int getNextArrowRemoval() { return getArrowCooldown(); }
    @Override public void startUsingItem(EquipmentSlot slot) {
        java.util.Objects.requireNonNull(slot, "slot");
        if (slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND) throw new IllegalArgumentException("slot must be HAND or OFF_HAND");
        getHandle().startUsingItem(slot == EquipmentSlot.HAND ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
    }
    @Override public int getArrowCooldown() { return ((LivingEntityAccessBridge) getHandle()).lunararc$getRemoveArrowTime(); }
    @Override public int getItemInUseTicks() { return getHandle().getUseItemRemainingTicks(); }
    @Override public void setItemInUseTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        ((LivingEntityAccessBridge) getHandle()).lunararc$setUseItemRemaining(ticks);
    }
    @Override public void setBeeStingersInBody(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        getHandle().setStingerCount(count);
    }
    @Override public int getBeeStingersInBody() { return getHandle().getStingerCount(); }
    @Override public void setCanPickupItems(boolean pickup) { super.setCanPickupItems(pickup); }
    @Override public boolean getCanPickupItems() { return super.getCanPickupItems(); }
    @Override public org.bukkit.Sound getEatingSound(ItemStack itemStack) { return org.bukkit.Sound.ENTITY_GENERIC_EAT; }
    @Override public EquipmentSlot getActiveItemHand() { return getHandle().getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND; }
    @Override
    public boolean addPotionEffect(PotionEffect effect) {
        return addPotionEffect(effect, false);
    }
    @Override
    public boolean addPotionEffect(PotionEffect effect, boolean force) {
        if (effect == null) return false;
        try {
            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(effect.getType().getKey().toString());
            var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(rl).orElse(null);
            if (holder == null) return false;
            getHandle().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                holder, effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles()),
                (net.minecraft.world.entity.Entity) null);
            return true;
        } catch (Throwable t) { return false; }
    }
    @Override
    public PotionEffect getPotionEffect(PotionEffectType type) {
        if (type == null) return null;
        try {
            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(type.getKey().toString());
            var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(rl).orElse(null);
            if (holder == null) return null;
            net.minecraft.world.effect.MobEffectInstance fx = getHandle().getEffect(holder);
            if (fx == null) return null;
            return new PotionEffect(type, fx.getDuration(), fx.getAmplifier(), fx.isAmbient(), fx.isVisible());
        } catch (Throwable t) { return null; }
    }


    @Override public int getFoodLevel() { return getHandle().getFoodData().getFoodLevel(); }


    @Override public long getLastLogin() { return ((ServerPlayerBukkitDataBridge) getHandle()).lunararc$getLoginTime(); }
    @Override public long getLastSeen() {
        return isOnline() ? System.currentTimeMillis() : ((ServerPlayerBukkitDataBridge) getHandle()).lunararc$getLastSaveTime();
    }
    @Override public boolean isConnected() { return getHandle().connection != null; }
    @Override public long getLastPlayed() { return ((ServerPlayerBukkitDataBridge) getHandle()).lunararc$getLastPlayed(); }
    @Override public boolean hasPlayedBefore() { return ((ServerPlayerBukkitDataBridge) getHandle()).lunararc$hasPlayedBefore(); }
    @Override public Location getLastDeathLocation() {
        return getHandle().getLastDeathLocation().map(globalPos -> {
            net.minecraft.server.level.ServerLevel level = getHandle().server.getLevel(globalPos.dimension());
            if (level == null) return null;
            net.minecraft.core.BlockPos pos = globalPos.pos();
            return new Location(((io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) getHandle().server)
                    .lunararc$getCraftServer().getCraftWorld(level), pos.getX(), pos.getY(), pos.getZ());
        }).orElse(null);
    }
    @Override @SuppressWarnings("unchecked") public <E extends org.bukkit.BanEntry<? super PlayerProfile>> E ban(String reason, Duration duration, String source) {
        return (E) new org.bukkit.craftbukkit.ban.CraftProfileBanList(this.server.getServer().getPlayerList().getBans()).addBan(getPlayerProfile(), reason, duration, source);
    }
    @Override @SuppressWarnings("unchecked") public <E extends org.bukkit.BanEntry<? super PlayerProfile>> E ban(String reason, Date expires, String source) {
        return (E) new org.bukkit.craftbukkit.ban.CraftProfileBanList(this.server.getServer().getPlayerList().getBans()).addBan(getPlayerProfile(), reason, expires, source);
    }
    @Override @SuppressWarnings("unchecked") public <E extends org.bukkit.BanEntry<? super PlayerProfile>> E ban(String reason, Instant expires, String source) {
        return (E) new org.bukkit.craftbukkit.ban.CraftProfileBanList(this.server.getServer().getPlayerList().getBans()).addBan(getPlayerProfile(), reason, expires, source);
    }

    @Override
    public <E extends org.bukkit.BanEntry<? super PlayerProfile>> E ban(String reason, Duration duration, String source, boolean kickPlayer) {
        E entry = ban(reason, duration, source);
        if (kickPlayer) kick(Component.text(reason == null ? "Banned by an operator." : reason));
        return entry;
    }

    @Override
    public <E extends org.bukkit.BanEntry<? super PlayerProfile>> E ban(String reason, Date expires, String source, boolean kickPlayer) {
        E entry = ban(reason, expires, source);
        if (kickPlayer) kick(Component.text(reason == null ? "Banned by an operator." : reason));
        return entry;
    }

    @Override
    public <E extends org.bukkit.BanEntry<? super PlayerProfile>> E ban(String reason, Instant expires, String source, boolean kickPlayer) {
        E entry = ban(reason, expires, source);
        if (kickPlayer) kick(Component.text(reason == null ? "Banned by an operator." : reason));
        return entry;
    }


    @Override
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile) {
        return launchProjectile(projectile, null, null);
    }

    @Override
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity) {
        return launchProjectile(projectile, velocity, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity, Consumer<? super T> function) {
        if (projectile == null) throw new IllegalArgumentException("projectile cannot be null");
        T spawned = (T) getWorld().spawn(getEyeLocation(), projectile);
        if (velocity != null) spawned.setVelocity(velocity);
        if (function != null) function.accept(spawned);
        return spawned;
    }


    @Override
    public Set<String> getListeningPluginChannels() {
        if (getHandle().connection == null) return Collections.emptySet();
        return Collections.unmodifiableSet(
                ((io.lunararcdevs.lunararc.common.bridge.ServerCommonPacketListenerBridge) getHandle().connection)
                        .lunararc$getPluginChannels());
    }


    @Override public void sendRawMessage(UUID sender, String message) {
        java.util.Objects.requireNonNull(message, "message");
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendSystem(getHandle(), message);
    }
    @Override public InetSocketAddress getVirtualHost() { return getAddress(); }
    @Override public boolean beginConversation(Conversation conversation) { return this.conversationTracker.beginConversation(java.util.Objects.requireNonNull(conversation, "conversation")); }
    @Override public void abandonConversation(Conversation conversation) {
        java.util.Objects.requireNonNull(conversation, "conversation");
        this.conversationTracker.abandonConversation(conversation, new ConversationAbandonedEvent(
                conversation, new org.bukkit.conversations.ManuallyAbandonedConversationCanceller()));
    }
    @Override public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
        this.conversationTracker.abandonConversation(
                java.util.Objects.requireNonNull(conversation, "conversation"),
                java.util.Objects.requireNonNull(details, "details"));
    }

    @Override public int getArrowsInBody() { return getHandle().getArrowCount(); }
    @Override public int getNoActionTicks() { return getHandle().getNoActionTime(); }


    @Override
    public @NotNull org.bukkit.advancement.AdvancementProgress getAdvancementProgress(
            @NotNull org.bukkit.advancement.Advancement advancement) {
        java.util.Objects.requireNonNull(advancement, "advancement");
        org.bukkit.NamespacedKey key = advancement.getKey();
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                key.getNamespace(), key.getKey());
        net.minecraft.advancements.AdvancementHolder holder = getHandle().server.getAdvancements().get(id);
        if (holder == null) {
            throw new IllegalArgumentException("Advancement is not registered: " + key);
        }
        net.minecraft.server.PlayerAdvancements playerData = getHandle().getAdvancements();
        net.minecraft.advancements.AdvancementProgress progress = playerData.getOrStartProgress(holder);
        return new org.bukkit.craftbukkit.advancement.CraftAdvancementProgress(
                advancement, holder, playerData, progress);
    }

    @Override
    public boolean breakBlock(@NotNull org.bukkit.block.Block block) {
        java.util.Objects.requireNonNull(block, "block");
        if (block.getWorld() != getWorld()) {
            throw new IllegalArgumentException("Cannot break blocks across worlds");
        }
        return getHandle().gameMode.destroyBlock(new net.minecraft.core.BlockPos(block.getX(), block.getY(), block.getZ()));
    }

    @Override
    public double getHealthScale() {
        return this.lunararcHealthScale;
    }

    @Override
    public boolean isHealthScaled() {
        return this.lunararcHealthScaled;
    }

    @Override
    public void setHealthScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Health scale must be finite and greater than 0");
        }
        this.lunararcHealthScale = scale;
        this.lunararcHealthScaled = true;
        sendScaledHealthUpdate();
    }

    @Override
    public void setHealthScaled(boolean scaled) {
        if (this.lunararcHealthScaled != scaled) {
            this.lunararcHealthScaled = scaled;
            sendScaledHealthUpdate();
        }
    }

    private void sendScaledHealthUpdate() {
        double health = getHealth();
        if (this.lunararcHealthScaled) {
            double max = getMaxHealth();
            health = max <= 0.0D ? 0.0D : (health / max) * this.lunararcHealthScale;
        }
        sendHealthUpdate(health, getFoodLevel(), getSaturation());
    }

    @Override
    public @Nullable Location getBedSpawnLocation() {
        return getRespawnLocation();
    }

    @Override
    public void setBedSpawnLocation(@Nullable Location location) {
        setRespawnLocation(location, false);
    }

    @Override
    public void setBedSpawnLocation(@Nullable Location location, boolean force) {
        setRespawnLocation(location, force);
    }

    @Override
    public @Nullable Location getRespawnLocation() {
        return getRespawnLocation(false);
    }

    public @Nullable Location getRespawnLocation(boolean loadLocationAndValidate) {
        net.minecraft.server.level.ServerPlayer.RespawnConfig config = getHandle().getRespawnConfig();
        if (config == null) return null;
        net.minecraft.world.level.storage.LevelData.RespawnData data = config.respawnData();
        net.minecraft.server.level.ServerLevel level = getHandle().level().getServer().getLevel(data.dimension());
        if (level == null) return null;
        org.bukkit.craftbukkit.CraftWorld world =
                ((io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) getHandle().level().getServer())
                        .lunararc$getCraftServer().getCraftWorld(level);
        if (world == null) return null;
        if (!loadLocationAndValidate) {
            net.minecraft.core.BlockPos pos = data.pos();
            return new Location(world, pos.getX(), pos.getY(), pos.getZ(), data.yaw(), data.pitch());
        }
        return net.minecraft.server.level.ServerPlayer.findRespawnAndUseSpawnBlock(level, config, false)
                .map(resolved -> new Location(world, resolved.position().x, resolved.position().y, resolved.position().z, resolved.yaw(), resolved.pitch()))
                .orElse(null);
    }

    @Override
    public void setRespawnLocation(@Nullable Location location) {
        setRespawnLocation(location, false);
    }

    @Override
    public void setRespawnLocation(@Nullable Location location, boolean force) {
        if (location == null) {
            ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerSpawnBridge) getHandle())
                    .lunararc$pushSpawnChangeCause(org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.PLUGIN);
            getHandle().setRespawnPosition(null, false);
            return;
        }
        if (!(location.getWorld() instanceof org.bukkit.craftbukkit.CraftWorld world)) {
            throw new IllegalArgumentException("Respawn location must reference a LunarArc CraftWorld");
        }
        ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerSpawnBridge) getHandle())
                .lunararc$pushSpawnChangeCause(org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.PLUGIN);
        getHandle().setRespawnPosition(new net.minecraft.server.level.ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                        world.getHandle().dimension(),
                        new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                        location.getYaw(), location.getPitch()),
                force), false);
    }

    @Override
    public @Nullable org.bukkit.entity.Entity getSpectatorTarget() {
        net.minecraft.world.entity.Entity camera = getHandle().getCamera();
        return camera == getHandle() ? null : org.bukkit.craftbukkit.entity.CraftEntity.getEntity(server, camera);
    }

    @Override
    public void setSpectatorTarget(@Nullable org.bukkit.entity.Entity target) {
        if (getGameMode() != GameMode.SPECTATOR) {
            throw new IllegalArgumentException("Player must be in spectator mode");
        }
        getHandle().setCamera(target == null ? null : ((CraftEntity) target).getHandle());
    }

    @Override
    public @NotNull org.bukkit.scoreboard.Scoreboard getScoreboard() {
        org.bukkit.scoreboard.ScoreboardManager manager = server.getScoreboardManager();
        if (!(manager instanceof org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager craftManager)) {
            throw new IllegalStateException("LunarArc CraftScoreboardManager is not installed");
        }
        return craftManager.getPlayerBoard(this);
    }

    @Override
    public void setScoreboard(@NotNull org.bukkit.scoreboard.Scoreboard scoreboard) {
        java.util.Objects.requireNonNull(scoreboard, "scoreboard");
        org.bukkit.scoreboard.ScoreboardManager manager = server.getScoreboardManager();
        if (!(manager instanceof org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager craftManager)) {
            throw new IllegalStateException("LunarArc CraftScoreboardManager is not installed");
        }
        craftManager.setPlayerBoard(this, scoreboard);
    }

    @Override
    public @Nullable org.bukkit.WorldBorder getWorldBorder() {
        return this.playerWorldBorder;
    }

    @Override
    public void setWorldBorder(@Nullable org.bukkit.WorldBorder border) {
        net.minecraft.world.level.border.WorldBorder handle;
        if (border == null) {
            this.playerWorldBorder = null;
            handle = ((org.bukkit.craftbukkit.CraftWorld) getWorld()).getHandle().getWorldBorder();
        } else {
            if (!(border instanceof org.bukkit.craftbukkit.CraftWorldBorder craftBorder)) {
                throw new IllegalArgumentException("WorldBorder must be a LunarArc CraftWorldBorder");
            }
            if (!craftBorder.isVirtual() && craftBorder.getWorld() != getWorld()) {
                throw new UnsupportedOperationException("Cannot set a world-backed border from another world");
            }
            this.playerWorldBorder = border;
            handle = craftBorder.getHandle();
        }
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(handle));
        }
    }

    @Override
    @Deprecated
    public void playEffect(@NotNull Location location, @NotNull Effect effect, int data) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(effect, "effect");
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(
                effect.getId(),
                new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                data,
                false));
    }

    @Override
    public <T> void playEffect(@NotNull Location location, @NotNull Effect effect, @Nullable T data) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(effect, "effect");
        if (data != null) {
            if (effect.getData() == null) {
                throw new IllegalArgumentException("Effect." + effect.name() + " does not have valid data");
            }
            if (!effect.isApplicable(data)) {
                throw new IllegalArgumentException(data.getClass().getName() + " data cannot be used for the " + effect + " effect");
            }
        } else if (effect.getData() != null && effect != Effect.ELECTRIC_SPARK) {
            throw new IllegalArgumentException("Wrong kind of data for the " + effect + " effect");
        }
        int dataValue = org.bukkit.craftbukkit.CraftEffect.getDataValue(effect, data);
        playEffect(location, effect, dataValue);
    }

    @Override
    @Deprecated
    public void playNote(@NotNull Location location, byte instrument, byte note) {
        java.util.Objects.requireNonNull(location, "location");
        Instrument resolved = Instrument.getByType(instrument);
        if (resolved == null) return;
        playNote(location, resolved, new Note(note));
    }

    @Override
    public void playNote(@NotNull Location location, @NotNull Instrument instrument, @NotNull Note note) {
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(instrument, "instrument");
        java.util.Objects.requireNonNull(note, "note");
        org.bukkit.Sound sound = instrument.getSound();
        if (sound == null) return;
        playSound(location, sound, org.bukkit.SoundCategory.RECORDS, 3.0F, note.getPitch());
    }

    @Override
    public void sendActionBar(@NotNull String message) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendActionBar(
                getHandle(), java.util.Objects.requireNonNull(message, "message"));
    }

    @Override
    public void sendActionBar(char alternateChar, @NotNull String message) {
        sendActionBar(org.bukkit.ChatColor.translateAlternateColorCodes(alternateChar,
                java.util.Objects.requireNonNull(message, "message")));
    }

    @Override
    public void sendActionBar(net.md_5.bungee.api.chat.BaseComponent... message) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendActionBar(getHandle(), message);
    }

    /**
     * Adventure's action bar, missing for the same reason the title was: Audience.sendActionBar is
     * an empty default, so a plugin calling it saw no error and no action bar. The String and
     * BaseComponent forms above were implemented; the one modern plugins call was not.
     */
    @Override
    public void sendActionBar(@NotNull net.kyori.adventure.text.Component message) {
        io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.sendActionBar(getHandle(), message);
    }

    @Override
    public void resetTitle() {
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        }
    }

    @Override
    public <T> void sendTitlePart(@NotNull net.kyori.adventure.title.TitlePart<T> part, @NotNull T value) {
        java.util.Objects.requireNonNull(part, "part");
        java.util.Objects.requireNonNull(value, "value");
        if (getHandle().connection == null) return;

        if (part == net.kyori.adventure.title.TitlePart.TITLE) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline
                            .fromAdventure((net.kyori.adventure.text.Component) value)));
        } else if (part == net.kyori.adventure.title.TitlePart.SUBTITLE) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline
                            .fromAdventure((net.kyori.adventure.text.Component) value)));
        } else if (part == net.kyori.adventure.title.TitlePart.TIMES) {
            net.kyori.adventure.title.Title.Times times = (net.kyori.adventure.title.Title.Times) value;
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                    titleTicks(times.fadeIn()), titleTicks(times.stay()), titleTicks(times.fadeOut())));
        } else {
            // Adventure can add parts, and a part we do not know is not something to guess at.
            throw new IllegalArgumentException("Unknown TitlePart: " + part);
        }
    }

    @Override
    public void clearTitle() {
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(false));
        }
    }

    /** A duration in ticks, with vanilla's -1 for "leave this one as it is". */
    private static int titleTicks(java.time.Duration duration) {
        return duration == null ? -1 : (int) (duration.toMillis() / 50L);
    }

    @Override
    public void sendTitle(@Nullable String title, @Nullable String subtitle) {
        sendTitle(title, subtitle, 10, 70, 20);
    }

    @Override
    public void sendTitle(@Nullable String title, @Nullable String subtitle, int fadeIn, int stay, int fadeOut) {
        if (getHandle().connection == null) return;
        setTitleTimes(fadeIn, stay, fadeOut);
        if (title != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromLegacy(title)));
        }
        if (subtitle != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromLegacy(subtitle)));
        }
    }

    @Override
    public void setTitleTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                    fadeInTicks, stayTicks, fadeOutTicks));
        }
    }

    @Override
    public void setSubtitle(net.md_5.bungee.api.chat.BaseComponent subtitle) {
        setSubtitle(subtitle == null ? null : new net.md_5.bungee.api.chat.BaseComponent[] { subtitle });
    }

    @Override
    public void setSubtitle(net.md_5.bungee.api.chat.BaseComponent[] subtitle) {
        if (getHandle().connection == null || subtitle == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromBungee(subtitle)));
    }

    @Override
    public void showTitle(net.md_5.bungee.api.chat.BaseComponent title) {
        showTitle(title == null ? null : new net.md_5.bungee.api.chat.BaseComponent[] { title });
    }

    @Override
    public void showTitle(net.md_5.bungee.api.chat.BaseComponent[] title) {
        if (getHandle().connection == null || title == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromBungee(title)));
    }

    @Override
    public void showTitle(net.md_5.bungee.api.chat.BaseComponent title,
            net.md_5.bungee.api.chat.BaseComponent subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        showTitle(title == null ? null : new net.md_5.bungee.api.chat.BaseComponent[] { title },
                subtitle == null ? null : new net.md_5.bungee.api.chat.BaseComponent[] { subtitle },
                fadeInTicks, stayTicks, fadeOutTicks);
    }

    @Override
    public void showTitle(net.md_5.bungee.api.chat.BaseComponent[] title,
            net.md_5.bungee.api.chat.BaseComponent[] subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        setTitleTimes(fadeInTicks, stayTicks, fadeOutTicks);
        if (title != null) showTitle(title);
        if (subtitle != null) setSubtitle(subtitle);
    }

    @Override
    public void sendTitle(com.destroystokyo.paper.Title title) {
        java.util.Objects.requireNonNull(title, "title");
        showTitle(title.getTitle(), title.getSubtitle(), title.getFadeIn(), title.getStay(), title.getFadeOut());
    }

    @Override
    public void updateTitle(com.destroystokyo.paper.Title title) {
        sendTitle(java.util.Objects.requireNonNull(title, "title"));
    }

    @Override
    public void setTexturePack(@NotNull String url) {
        setResourcePack(url);
    }

    @Override
    public void setResourcePack(@NotNull String url) {
        setResourcePack(url, null, (String) null, false);
    }

    @Override
    public void setResourcePack(@NotNull String url, byte @Nullable [] hash) {
        setResourcePack(url, hash, (String) null, false);
    }

    @Override
    public void setResourcePack(@NotNull String url, byte @Nullable [] hash, boolean force) {
        setResourcePack(url, hash, (String) null, force);
    }

    @Override
    public void setResourcePack(@NotNull String url, byte @Nullable [] hash, @Nullable String prompt) {
        setResourcePack(url, hash, prompt, false);
    }

    @Override
    public void setResourcePack(@NotNull String url, byte @Nullable [] hash, @Nullable String prompt, boolean force) {
        java.util.Objects.requireNonNull(url, "url");
        setResourcePack(UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)), url, hash, prompt, force);
    }

    @Override
    public void setResourcePack(@NotNull UUID id, @NotNull String url, byte @Nullable [] hash,
            @Nullable String prompt, boolean force) {
        pushResourcePack(id, url, hash,
                prompt == null ? null : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromLegacy(prompt),
                force, true);
    }

    @Override
    public void setResourcePack(@NotNull UUID id, @NotNull String url, byte @Nullable [] hash,
            @Nullable net.kyori.adventure.text.Component prompt, boolean force) {
        pushResourcePack(id, url, hash,
                prompt == null ? null : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromAdventure(prompt),
                force, true);
    }

    @Override
    public void addResourcePack(@NotNull UUID id, @NotNull String url, byte @Nullable [] hash,
            @Nullable String prompt, boolean force) {
        pushResourcePack(id, url, hash,
                prompt == null ? null : io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.fromLegacy(prompt),
                force, false);
    }

    private void pushResourcePack(UUID id, String url, byte[] hash,
            net.minecraft.network.chat.Component prompt, boolean force, boolean replace) {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(url, "url");
        if (hash != null && hash.length != 20) {
            throw new IllegalArgumentException("Resource pack hash must be 20 bytes, got " + hash.length);
        }
        if (getHandle().connection == null) return;
        if (replace) removeResourcePacks();
        String hashString = hash == null ? "" : java.util.HexFormat.of().formatHex(hash);
        getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket(
                id, url, hashString, force, java.util.Optional.ofNullable(prompt)));
    }

    @Override
    public void removeResourcePack(@NotNull UUID id) {
        java.util.Objects.requireNonNull(id, "id");
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket(
                    java.util.Optional.of(id)));
        }
    }

    @Override
    public void removeResourcePacks() {
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket(
                    java.util.Optional.empty()));
        }
    }

    @Override
    public org.bukkit.event.player.PlayerResourcePackStatusEvent.@Nullable Status getResourcePackStatus() {
        return this.resourcePackStatus;
    }


    public void lunararc$setResourcePackStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent.Status status) {
        this.resourcePackStatus = status;
    }


    private static net.minecraft.world.level.GameType toNMS(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> net.minecraft.world.level.GameType.SURVIVAL;
            case CREATIVE -> net.minecraft.world.level.GameType.CREATIVE;
            case ADVENTURE -> net.minecraft.world.level.GameType.ADVENTURE;
            case SPECTATOR -> net.minecraft.world.level.GameType.SPECTATOR;
        };
    }

    private static GameMode fromNMS(net.minecraft.world.level.GameType type) {
        return switch (type) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
    }

    // The four look-anchor and relative-teleport-flag converters CraftBukkit exposes as statics.
    // Paper's teleport API takes the Bukkit forms and NMS wants its own enums; plugins driving a
    // teleport through NMS directly convert with these.
    public static net.minecraft.commands.arguments.EntityAnchorArgument.Anchor toNmsAnchor(
            io.papermc.paper.entity.LookAnchor apiAnchor) {
        return switch (apiAnchor) {
            case EYES -> net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES;
            case FEET -> net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
        };
    }

    public static io.papermc.paper.entity.LookAnchor toApiAnchor(
            net.minecraft.commands.arguments.EntityAnchorArgument.Anchor nmsAnchor) {
        return switch (nmsAnchor) {
            case EYES -> io.papermc.paper.entity.LookAnchor.EYES;
            case FEET -> io.papermc.paper.entity.LookAnchor.FEET;
        };
    }

    public static net.minecraft.world.entity.Relative toNmsRelativeFlag(
            io.papermc.paper.entity.TeleportFlag.Relative apiFlag) {
        return switch (apiFlag) {
            case VELOCITY_X -> net.minecraft.world.entity.Relative.DELTA_X;
            case VELOCITY_Y -> net.minecraft.world.entity.Relative.DELTA_Y;
            case VELOCITY_Z -> net.minecraft.world.entity.Relative.DELTA_Z;
            case VELOCITY_ROTATION -> net.minecraft.world.entity.Relative.ROTATE_DELTA;
        };
    }

    public static io.papermc.paper.entity.TeleportFlag.Relative toApiRelativeFlag(
            net.minecraft.world.entity.Relative nmsFlag) {
        return switch (nmsFlag) {
            case DELTA_X -> io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_X;
            case DELTA_Y -> io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Y;
            case DELTA_Z -> io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Z;
            case ROTATE_DELTA -> io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION;
            default -> throw new IllegalArgumentException("No API teleport flag for " + nmsFlag);
        };
    }

    private io.papermc.paper.connection.PaperPlayerGameConnection lunararc$gameConnection;
    private net.minecraft.server.network.ServerGamePacketListenerImpl lunararc$gameConnectionOwner;

    @Override
    public io.papermc.paper.connection.PlayerGameConnection getConnection() {
        net.minecraft.server.network.ServerGamePacketListenerImpl listener = getHandle().connection;
        if (lunararc$gameConnection == null || lunararc$gameConnectionOwner != listener) {
            lunararc$gameConnection = new io.papermc.paper.connection.PaperPlayerGameConnection(listener);
            lunararc$gameConnectionOwner = listener;
        }
        return lunararc$gameConnection;
    }

    @Override
    public void setDeathScreenScore(final int score) {
        getHandle().setScore(score);
    }

    @Override
    public int getDeathScreenScore() {
        return getHandle().getScore();
    }

    @Override
    public io.papermc.paper.entity.@org.jetbrains.annotations.NotNull PlayerGiveResult give(final java.util.@org.jetbrains.annotations.NotNull Collection<org.bukkit.inventory.@org.jetbrains.annotations.NotNull ItemStack> items, final boolean dropIfFull) {
        com.google.common.base.Preconditions.checkArgument(items != null, "items cannot be null");
        if (items.isEmpty()) return io.papermc.paper.entity.PaperPlayerGiveResult.EMPTY;

        for (final org.bukkit.inventory.ItemStack item : items) {
            com.google.common.base.Preconditions.checkArgument(item != null, "ItemStack cannot be null");
            com.google.common.base.Preconditions.checkArgument(!item.isEmpty(), "ItemStack cannot be empty");
            com.google.common.base.Preconditions.checkArgument(item.getAmount() <= item.getMaxStackSize(), "ItemStack amount cannot be greater than its max stack size");
        }

        final net.minecraft.server.level.ServerPlayer handle = this.getHandle();
        final com.google.common.collect.ImmutableList.Builder<org.bukkit.entity.Item> drops = com.google.common.collect.ImmutableList.builder();
        final com.google.common.collect.ImmutableList.Builder<org.bukkit.inventory.ItemStack> leftovers = com.google.common.collect.ImmutableList.builder();
        for (final org.bukkit.inventory.ItemStack item : items) {
            final net.minecraft.world.item.ItemStack nmsStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
            final boolean added = handle.getInventory().add(nmsStack);
            if (added && nmsStack.isEmpty()) continue;

            leftovers.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nmsStack));
            if (!dropIfFull) continue;

            final net.minecraft.world.entity.item.ItemEntity entity = handle.drop(nmsStack, false, true);
            if (entity != null) drops.add((org.bukkit.entity.Item) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity());
        }

        handle.containerMenu.broadcastChanges();
        return new io.papermc.paper.entity.PaperPlayerGiveResult(leftovers.build(), drops.build());
    }

    @Override
    public void openVirtualSign(io.papermc.paper.math.Position block, org.bukkit.block.sign.Side side) {
        if (this.getHandle().connection == null) return;
        this.getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket(
                new net.minecraft.core.BlockPos(block.blockX(), block.blockY(), block.blockZ()), side == org.bukkit.block.sign.Side.FRONT));
    }

    @Override
    public org.bukkit.Input getCurrentInput() {
        return new org.bukkit.craftbukkit.CraftInput(this.getHandle().getLastClientInput());
    }

    @Override
    public java.util.Collection<org.bukkit.entity.EnderPearl> getEnderPearls() {
        return getHandle().getEnderPearls().stream()
                .map(pearl -> (org.bukkit.entity.EnderPearl) ((EntityBridge) pearl).lunararc$getBukkitEntity())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public int getPlayerListOrder() {
        return ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerListOrderBridge) getHandle()).lunararc$getListOrder();
    }

    @Override
    public void setPlayerListOrder(int order) {
        com.google.common.base.Preconditions.checkArgument(order >= 0, "order cannot be negative");
        ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerListOrderBridge) getHandle()).lunararc$setListOrder(order);
        if (getHandle().connection == null) return; // updates are possible before the player has fully joined
        for (net.minecraft.server.level.ServerPlayer other : getHandle().level().getServer().getPlayerList().getPlayers()) {
            if (((org.bukkit.entity.Player) ((EntityBridge) other).lunararc$getBukkitEntity()).canSee(this)) {
                other.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER, getHandle()));
            }
        }
    }

    @Override
    public @Nullable Location getPotentialRespawnLocation() {
        return getRespawnLocation(false);
    }

    @Override
    public org.bukkit.entity.@Nullable Item dropItem(final int slot, final int amount, final boolean throwRandomly, final java.util.function.@Nullable Consumer<org.bukkit.entity.Item> entityOperation) {
        com.google.common.base.Preconditions.checkArgument(slot >= 0 && slot < getInventory().getSize(), "Slot %s is not a valid inventory slot.", slot);
        return lunararc$dropFromInventory(getInventory().getItem(slot), amount, throwRandomly, entityOperation);
    }

    @Override
    public org.bukkit.entity.@Nullable Item dropItem(final org.bukkit.inventory.@NotNull EquipmentSlot slot, final int amount, final boolean throwRandomly, final java.util.function.@Nullable Consumer<org.bukkit.entity.Item> entityOperation) {
        return lunararc$dropFromInventory(getInventory().getItem(slot), amount, throwRandomly, entityOperation);
    }

    @Override
    public org.bukkit.entity.@Nullable Item dropItem(final ItemStack itemStack, final boolean throwRandomly, final java.util.function.@Nullable Consumer<org.bukkit.entity.Item> entityOperation) {
        com.google.common.base.Preconditions.checkArgument(itemStack != null, "Cannot drop a null itemstack");
        if (itemStack.isEmpty()) return null;
        net.minecraft.world.entity.item.ItemEntity dropped = getHandle().drop(CraftItemStack.asNMSCopy(itemStack), throwRandomly, true);
        return lunararc$finishDrop(dropped, entityOperation);
    }

    private org.bukkit.entity.@Nullable Item lunararc$dropFromInventory(final ItemStack original, final int amount, final boolean throwRandomly, final java.util.function.@Nullable Consumer<org.bukkit.entity.Item> entityOperation) {
        if (original == null || original.isEmpty() || amount <= 0) return null;
        net.minecraft.world.item.ItemStack nms = CraftItemStack.unwrap(original);
        net.minecraft.world.item.ItemStack dropContent = nms.split(amount);
        net.minecraft.world.entity.item.ItemEntity dropped = getHandle().drop(dropContent, throwRandomly, true);
        if (dropped == null) {
            // return the stack to the inventory slot if the drop was refused
            nms.grow(dropContent.getCount());
            return null;
        }
        return lunararc$finishDrop(dropped, entityOperation);
    }

    private org.bukkit.entity.@Nullable Item lunararc$finishDrop(@Nullable net.minecraft.world.entity.item.ItemEntity dropped, java.util.function.@Nullable Consumer<org.bukkit.entity.Item> entityOperation) {
        if (dropped == null) return null;
        org.bukkit.entity.Item item = (org.bukkit.entity.Item) ((EntityBridge) dropped).lunararc$getBukkitEntity();
        if (entityOperation != null) entityOperation.accept(item);
        return item;
    }

    @Override
    public boolean hasCooldown(ItemStack item) {
        com.google.common.base.Preconditions.checkArgument(item != null, "Item cannot be null");
        return getHandle().getCooldowns().isOnCooldown(CraftItemStack.asNMSCopy(item));
    }

    @Override
    public int getCooldown(ItemStack item) {
        com.google.common.base.Preconditions.checkArgument(item != null, "Item cannot be null");
        net.minecraft.resources.Identifier group = getHandle().getCooldowns().getCooldownGroup(CraftItemStack.asNMSCopy(item));
        if (group == null) return 0;
        net.minecraft.world.item.ItemCooldowns.CooldownInstance cooldown = getHandle().getCooldowns().cooldowns.get(group);
        return cooldown == null ? 0 : Math.max(0, cooldown.endTime() - getHandle().getCooldowns().tickCount);
    }

    @Override
    public void setCooldown(ItemStack item, int ticks) {
        com.google.common.base.Preconditions.checkArgument(item != null, "Item cannot be null");
        com.google.common.base.Preconditions.checkArgument(ticks >= 0, "Cannot have negative cooldown");
        getHandle().getCooldowns().addCooldown(CraftItemStack.asNMSCopy(item), ticks);
    }

    @Override
    public int getCooldown(net.kyori.adventure.key.Key key) {
        com.google.common.base.Preconditions.checkArgument(key != null, "Key cannot be null");
        net.minecraft.world.item.ItemCooldowns.CooldownInstance cooldown = getHandle().getCooldowns().cooldowns.get(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(key.namespace(), key.value()));
        return cooldown == null ? 0 : Math.max(0, cooldown.endTime() - getHandle().getCooldowns().tickCount);
    }

    @Override
    public void setCooldown(net.kyori.adventure.key.Key key, int ticks) {
        com.google.common.base.Preconditions.checkArgument(key != null, "Key cannot be null");
        com.google.common.base.Preconditions.checkArgument(ticks >= 0, "Cannot have negative cooldown");
        getHandle().getCooldowns().addCooldown(net.minecraft.resources.Identifier.fromNamespaceAndPath(key.namespace(), key.value()), ticks);
    }
}
