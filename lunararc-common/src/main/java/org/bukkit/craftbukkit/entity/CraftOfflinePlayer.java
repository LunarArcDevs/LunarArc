package org.bukkit.craftbukkit.entity;

import org.bukkit.BanEntry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CraftOfflinePlayer implements OfflinePlayer {
    private final UUID uuid;
    private final String name;
    private final org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer persistentDataContainer = new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer();

    public CraftOfflinePlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    private org.bukkit.craftbukkit.ban.CraftProfileBanList lunararc$profileBanList() {
        org.bukkit.craftbukkit.CraftServer server =
                (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        return new org.bukkit.craftbukkit.ban.CraftProfileBanList(
                server.getServer().getPlayerList().getBans());
    }

    @Override public @NotNull UUID getUniqueId() { return uuid; }
    @Override public @Nullable String getName() { return name; }
    @Override public boolean isOnline() {
        return org.bukkit.Bukkit.getPlayer(uuid) != null;
    }
    @Override public boolean isConnected() { return false; }
    @Override public @Nullable Player getPlayer() { return org.bukkit.Bukkit.getPlayer(uuid); }
    @Override public boolean hasPlayedBefore() {
        Player player = getPlayer();
        if (player != null) return player.hasPlayedBefore();
        return getFirstPlayed() > 0L || getLastPlayed() > 0L;
    }
    @Override public boolean isBanned() {
        return lunararc$profileBanList().isBanned(getPlayerProfile());
    }
    @Override public boolean isWhitelisted() {
        Player player = getPlayer();
        if (player != null) return player.isWhitelisted();
        try {
            Object list = org.bukkit.Bukkit.getServer().getClass().getMethod("getHandle").invoke(org.bukkit.Bukkit.getServer());
            Object playerList = list.getClass().getMethod("getPlayerList").invoke(list);
            Object whiteList = playerList.getClass().getMethod("getWhiteList").invoke(playerList);
            Object result = whiteList.getClass().getMethod("isWhiteListed", com.mojang.authlib.GameProfile.class)
                    .invoke(whiteList, gameProfile());
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
    @Override public void setWhitelisted(boolean value) {
        Player player = getPlayer();
        if (player != null) { player.setWhitelisted(value); return; }
        mutateStoredUserList("getWhiteList", "net.minecraft.server.players.UserWhiteListEntry", value);
    }
    @Override public boolean isOp() {
        Player player = getPlayer();
        if (player != null) return player.isOp();
        try {
            Object server = org.bukkit.Bukkit.getServer().getClass().getMethod("getHandle").invoke(org.bukkit.Bukkit.getServer());
            Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
            Object result = playerList.getClass().getMethod("isOp", com.mojang.authlib.GameProfile.class).invoke(playerList, gameProfile());
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
    @Override public void setOp(boolean value) {
        Player player = getPlayer();
        if (player != null) { player.setOp(value); return; }
        try {
            Object server = org.bukkit.Bukkit.getServer().getClass().getMethod("getHandle").invoke(org.bukkit.Bukkit.getServer());
            Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
            if (value) {
                Class<?> entryClass = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.server.players.ServerOpListEntry");
                Object entry = null;
                for (var ctor : entryClass.getConstructors()) {
                    Class<?>[] t = ctor.getParameterTypes();
                    if (t.length == 4 && t[0] == com.mojang.authlib.GameProfile.class) {
                        entry = ctor.newInstance(gameProfile(), 4, false, false);
                        break;
                    }
                }
                if (entry == null) throw new ReflectiveOperationException("No ServerOpListEntry constructor");
                Object ops = playerList.getClass().getMethod("getOps").invoke(playerList);
                ops.getClass().getMethod("add", entryClass).invoke(ops, entry);
            } else {
                Object ops = playerList.getClass().getMethod("getOps").invoke(playerList);
                ops.getClass().getMethod("remove", com.mojang.authlib.GameProfile.class).invoke(ops, gameProfile());
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to update operator state for " + uuid, ex);
        }
    }
    @Override public @Nullable Location getBedSpawnLocation() { return getRespawnLocation(); }
    @Override public @Nullable Location getRespawnLocation() {
        Player player = getPlayer();
        if (player != null) return player.getRespawnLocation();
        return getRespawnLocation(true);
    }

    public @Nullable Location getRespawnLocation(boolean loadLocationAndValidate) {
        Player player = getPlayer();
        if (player != null) return player.getRespawnLocation();
        try {
            java.nio.file.Path data = lunararcPlayerDataPath();
            if (data == null || !java.nio.file.Files.isRegularFile(data)) return null;
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(data, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (tag == null) return null;
            if (!tag.contains("SpawnX") || !tag.contains("SpawnY") || !tag.contains("SpawnZ")) return null;
            String dimension = tag.contains("SpawnDimension") ? tag.getString("SpawnDimension") : "minecraft:overworld";
            org.bukkit.World world = lunararcWorldFromDimension(dimension);
            if (world == null) return null;
            Location location = new Location(world, tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ"));
            if (!loadLocationAndValidate) return location;
            int y = location.getBlockY();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) return null;
            return location;
        } catch (Throwable ignored) {
            return null;
        }
    }
    @Override public long getFirstPlayed() {
        Player p = getPlayer();
        if (p != null) return p.getFirstPlayed();
        net.minecraft.nbt.CompoundTag data = bukkitDataTag();
        if (data != null && data.contains("firstPlayed")) return data.getLong("firstPlayed");
        java.nio.file.Path file = lunararcPlayerDataPath();
        return file != null && java.nio.file.Files.isRegularFile(file) ? file.toFile().lastModified() : 0L;
    }
    @Override public long getLastPlayed() {
        Player p = getPlayer();
        if (p != null) return p.getLastPlayed();
        net.minecraft.nbt.CompoundTag data = bukkitDataTag();
        if (data != null && data.contains("lastPlayed")) return data.getLong("lastPlayed");
        java.nio.file.Path file = lunararcPlayerDataPath();
        return file != null && java.nio.file.Files.isRegularFile(file) ? file.toFile().lastModified() : 0L;
    }
    @Override public long getLastLogin() {
        Player p = getPlayer();
        if (p != null) return p.getLastLogin();
        net.minecraft.nbt.CompoundTag data = paperDataTag();
        if (data != null && data.contains("LastLogin")) return data.getLong("LastLogin");
        java.nio.file.Path file = lunararcPlayerDataPath();
        return file != null && java.nio.file.Files.isRegularFile(file) ? file.toFile().lastModified() : 0L;
    }
    @Override public long getLastSeen() {
        Player p = getPlayer();
        if (p != null) return p.getLastSeen();
        net.minecraft.nbt.CompoundTag data = paperDataTag();
        if (data != null && data.contains("LastSeen")) return data.getLong("LastSeen");
        java.nio.file.Path file = lunararcPlayerDataPath();
        return file != null && java.nio.file.Files.isRegularFile(file) ? file.toFile().lastModified() : 0L;
    }
    @Override public @Nullable Location getLastDeathLocation() {
        Player p = getPlayer();
        if (p != null) return p.getLastDeathLocation();
        net.minecraft.nbt.CompoundTag data = playerDataTag();
        if (data == null || !data.contains("LastDeathLocation", 10)) return null;
        return net.minecraft.core.GlobalPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, data.get("LastDeathLocation"))
                .result()
                .map(globalPos -> {
                    net.minecraft.server.MinecraftServer server = io.lunararcdevs.lunararc.common.LunarArcServerAccess.getMinecraftServer();
                    net.minecraft.server.level.ServerLevel level = server.getLevel(globalPos.dimension());
                    if (level == null) return null;
                    net.minecraft.core.BlockPos pos = globalPos.pos();
                    org.bukkit.craftbukkit.CraftWorld world = io.lunararcdevs.lunararc.common.LunarArcServerAccess.getCraftServer().getCraftWorld(level);
                    return world == null ? null : new Location(world, pos.getX(), pos.getY(), pos.getZ());
                })
                .orElse(null);
    }
    @Override public @Nullable Location getLocation() {
        Player p = getPlayer();
        if (p != null) return p.getLocation();
        net.minecraft.nbt.CompoundTag data = playerDataTag();
        if (data == null || !data.contains("Pos") || !data.contains("Rotation")) return null;
        net.minecraft.nbt.ListTag position = (net.minecraft.nbt.ListTag) data.get("Pos");
        net.minecraft.nbt.ListTag rotation = (net.minecraft.nbt.ListTag) data.get("Rotation");
        if (position == null || rotation == null || position.size() < 3 || rotation.size() < 2) return null;
        org.bukkit.World world = null;
        if (data.contains("WorldUUIDMost") && data.contains("WorldUUIDLeast")) {
            world = org.bukkit.Bukkit.getWorld(new UUID(data.getLong("WorldUUIDMost"), data.getLong("WorldUUIDLeast")));
        }
        if (world == null && data.contains("Dimension")) world = lunararcWorldFromDimension(data.getString("Dimension"));
        if (world == null) return null;
        return new Location(world, position.getDouble(0), position.getDouble(1), position.getDouble(2), rotation.getFloat(0), rotation.getFloat(1));
    }

    @Override public void incrementStatistic(@NotNull Statistic statistic) { incrementStatistic(statistic, 1); }
    @Override public void decrementStatistic(@NotNull Statistic statistic) { decrementStatistic(statistic, 1); }
    @Override public void incrementStatistic(@NotNull Statistic statistic, int amount) { mutateOfflineStatistic("incrementStatistic", statistic, null, amount); }
    @Override public void decrementStatistic(@NotNull Statistic statistic, int amount) { mutateOfflineStatistic("decrementStatistic", statistic, null, amount); }
    @Override public void setStatistic(@NotNull Statistic statistic, int newValue) { mutateOfflineStatistic("setStatistic", statistic, null, newValue); }
    @Override public int getStatistic(@NotNull Statistic statistic) { return getOfflineStatistic(statistic, null); }
    @Override public void incrementStatistic(@NotNull Statistic statistic, @NotNull Material material) { incrementStatistic(statistic, material, 1); }
    @Override public void decrementStatistic(@NotNull Statistic statistic, @NotNull Material material) { decrementStatistic(statistic, material, 1); }
    @Override public int getStatistic(@NotNull Statistic statistic, @NotNull Material material) { return getOfflineStatistic(statistic, Objects.requireNonNull(material, "material")); }
    @Override public void incrementStatistic(@NotNull Statistic statistic, @NotNull Material material, int amount) { mutateOfflineStatistic("incrementStatistic", statistic, Objects.requireNonNull(material, "material"), amount); }
    @Override public void decrementStatistic(@NotNull Statistic statistic, @NotNull Material material, int amount) { mutateOfflineStatistic("decrementStatistic", statistic, Objects.requireNonNull(material, "material"), amount); }
    @Override public void setStatistic(@NotNull Statistic statistic, @NotNull Material material, int newValue) { mutateOfflineStatistic("setStatistic", statistic, Objects.requireNonNull(material, "material"), newValue); }
    @Override public int getStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) { return getOfflineStatistic(statistic, Objects.requireNonNull(entityType, "entityType")); }
    @Override public void incrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) { incrementStatistic(statistic, entityType, 1); }
    @Override public void decrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) { decrementStatistic(statistic, entityType, 1); }
    @Override public void incrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int amount) { mutateOfflineStatistic("incrementStatistic", statistic, Objects.requireNonNull(entityType, "entityType"), amount); }
    @Override public void decrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int amount) { mutateOfflineStatistic("decrementStatistic", statistic, Objects.requireNonNull(entityType, "entityType"), amount); }
    @Override public void setStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int newValue) { mutateOfflineStatistic("setStatistic", statistic, Objects.requireNonNull(entityType, "entityType"), newValue); }
    @Override public @NotNull Map<String, Object> serialize() { return java.util.Collections.singletonMap("UUID", uuid.toString()); }

    @Override
    public @Nullable BanEntry<PlayerProfile> ban(@Nullable String reason, @Nullable Date expires, @Nullable String source) {
        return castBanEntry(lunararc$profileBanList().addBan(getPlayerProfile(), reason, expires, source));
    }

    @Override
    @SuppressWarnings("deprecation")
    public @Nullable BanEntry<PlayerProfile> ban(@Nullable String reason, @Nullable Duration duration, @Nullable String source) {
        return castBanEntry(lunararc$profileBanList().addBan(getPlayerProfile(), reason, duration, source));
    }

    @Override
    @SuppressWarnings("deprecation")
    public @Nullable BanEntry<PlayerProfile> ban(@Nullable String reason, @Nullable Instant expires, @Nullable String source) {
        return castBanEntry(lunararc$profileBanList().addBan(getPlayerProfile(), reason, expires, source));
    }

    @Override
    public @NotNull com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile() {
        return new io.lunararcdevs.lunararc.common.server.LunarArcPlayerProfile(uuid, name);
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        Player player = getPlayer();
        return player == null ? persistentDataContainer : player.getPersistentDataContainer();
    }

    private @Nullable net.minecraft.nbt.CompoundTag bukkitDataTag() {
        net.minecraft.nbt.CompoundTag data = playerDataTag();
        return data != null && data.contains("bukkit", 10) ? data.getCompound("bukkit") : null;
    }

    private @Nullable net.minecraft.nbt.CompoundTag paperDataTag() {
        net.minecraft.nbt.CompoundTag data = playerDataTag();
        return data != null && data.contains("Paper", 10) ? data.getCompound("Paper") : null;
    }

    private net.minecraft.stats.ServerStatsCounter offlineStatsCounter() {
        net.minecraft.server.MinecraftServer server = io.lunararcdevs.lunararc.common.LunarArcServerAccess.getMinecraftServer();
        java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR).resolve(uuid + ".json");
        return new net.minecraft.stats.ServerStatsCounter(server, file.toFile());
    }

    private static boolean statisticArgumentMatches(Class<?> parameter, Object argument) {
        if (argument == null) return !parameter.isPrimitive();
        return parameter.isInstance(argument) || parameter.isAssignableFrom(argument.getClass());
    }

    private static Object invokeCraftStatistic(net.minecraft.stats.ServerStatsCounter counter, String methodName, Object... apiArguments) {
        final Class<?> craftStatistic;
        try {
            craftStatistic = Class.forName("org.bukkit.craftbukkit.CraftStatistic", true, CraftOfflinePlayer.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Paper CraftStatistic is missing from the LunarArc runtime", ex);
        }
        java.lang.reflect.Method selected = null;
        outer:
        for (java.lang.reflect.Method candidate : craftStatistic.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(candidate.getModifiers()) || !candidate.getName().equals(methodName)) continue;
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != apiArguments.length + 1 || !statisticArgumentMatches(parameters[0], counter)) continue;
            for (int i = 0; i < apiArguments.length; i++) {
                if (!statisticArgumentMatches(parameters[i + 1], apiArguments[i])) continue outer;
            }
            candidate.trySetAccessible();
            selected = candidate;
            break;
        }
        if (selected == null) throw new IllegalStateException("Paper CraftStatistic does not expose compatible " + methodName + " overload");
        Object[] invocation = new Object[apiArguments.length + 1];
        invocation[0] = counter;
        System.arraycopy(apiArguments, 0, invocation, 1, apiArguments.length);
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

    private int getOfflineStatistic(Statistic statistic, @Nullable Object parameter) {
        Objects.requireNonNull(statistic, "statistic");
        Player online = getPlayer();
        if (online != null) {
            if (parameter instanceof Material material) return online.getStatistic(statistic, material);
            if (parameter instanceof EntityType type) return online.getStatistic(statistic, type);
            return online.getStatistic(statistic);
        }
        net.minecraft.stats.ServerStatsCounter counter = offlineStatsCounter();
        Object value = parameter == null ? invokeCraftStatistic(counter, "getStatistic", statistic) : invokeCraftStatistic(counter, "getStatistic", statistic, parameter);
        if (!(value instanceof Number number)) throw new IllegalStateException("Paper CraftStatistic.getStatistic returned a non-number");
        return number.intValue();
    }

    private void mutateOfflineStatistic(String operation, Statistic statistic, @Nullable Object parameter, int value) {
        Objects.requireNonNull(statistic, "statistic");
        if (value < 0) throw new IllegalArgumentException("Statistic value/amount cannot be negative");
        Player online = getPlayer();
        if (online != null) {
            if (parameter instanceof Material material) {
                if (operation.equals("incrementStatistic")) online.incrementStatistic(statistic, material, value);
                else if (operation.equals("decrementStatistic")) online.decrementStatistic(statistic, material, value);
                else online.setStatistic(statistic, material, value);
            } else if (parameter instanceof EntityType type) {
                if (operation.equals("incrementStatistic")) online.incrementStatistic(statistic, type, value);
                else if (operation.equals("decrementStatistic")) online.decrementStatistic(statistic, type, value);
                else online.setStatistic(statistic, type, value);
            } else {
                if (operation.equals("incrementStatistic")) online.incrementStatistic(statistic, value);
                else if (operation.equals("decrementStatistic")) online.decrementStatistic(statistic, value);
                else online.setStatistic(statistic, value);
            }
            return;
        }
        net.minecraft.stats.ServerStatsCounter counter = offlineStatsCounter();
        if (parameter == null) invokeCraftStatistic(counter, operation, statistic, value);
        else invokeCraftStatistic(counter, operation, statistic, parameter, value);
        counter.save();
    }

    private net.minecraft.nbt.CompoundTag playerDataTag() {
        try {
            java.nio.file.Path data = lunararcPlayerDataPath();
            if (data == null || !java.nio.file.Files.isRegularFile(data)) return null;
            return net.minecraft.nbt.NbtIo.readCompressed(data, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private java.nio.file.Path lunararcPlayerDataPath() {
        try {
            Object server = org.bukkit.Bukkit.getServer();
            if (server == null) return null;
            Object handle = server.getClass().getMethod("getHandle").invoke(server);
            Object minecraftServer = handle.getClass().getMethod("getServer").invoke(handle);
            for (java.lang.reflect.Method method : minecraftServer.getClass().getMethods()) {
                if (!method.getName().equals("getWorldPath") || method.getParameterCount() != 1) continue;
                Object playerDataDir = net.minecraft.world.level.storage.LevelResource.class.getField("PLAYER_DATA_DIR").get(null);
                Object path = method.invoke(minecraftServer, playerDataDir);
                if (path instanceof java.nio.file.Path p) return p.resolve(uuid + ".dat");
            }
        } catch (Throwable ignored) {}
        for (String worldName : java.util.List.of("world", ".")) {
            java.nio.file.Path path = java.nio.file.Path.of(worldName, "playerdata", uuid + ".dat");
            if (java.nio.file.Files.isRegularFile(path)) return path;
        }
        return null;
    }

    private static org.bukkit.World lunararcWorldFromDimension(String dimension) {
        String name = switch (dimension) {
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> "world";
        };
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(name);
        if (world != null) return world;
        for (org.bukkit.World candidate : org.bukkit.Bukkit.getWorlds()) {
            try {
                if (candidate.getKey().toString().equals(dimension)) return candidate;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private com.mojang.authlib.GameProfile gameProfile() {
        return new com.mojang.authlib.GameProfile(uuid, name == null ? "" : name);
    }

    private void mutateStoredUserList(String accessor, String entryClassName, boolean add) {
        try {
            Object server = org.bukkit.Bukkit.getServer().getClass().getMethod("getHandle").invoke(org.bukkit.Bukkit.getServer());
            Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
            Object list = playerList.getClass().getMethod(accessor).invoke(playerList);
            if (add) {
                Class<?> entryClass = Class.forName(entryClassName);
                Object entry = entryClass.getConstructor(com.mojang.authlib.GameProfile.class).newInstance(gameProfile());
                list.getClass().getMethod("add", entryClass).invoke(list, entry);
            } else {
                list.getClass().getMethod("remove", com.mojang.authlib.GameProfile.class).invoke(list, gameProfile());
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to update stored player list for " + uuid, ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BanEntry<PlayerProfile> castBanEntry(BanEntry entry) {
        return (BanEntry<PlayerProfile>) entry;
    }
}

