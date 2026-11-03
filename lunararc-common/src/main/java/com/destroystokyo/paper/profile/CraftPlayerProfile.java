package com.destroystokyo.paper.profile;

import org.bukkit.craftbukkit.profile.CraftPlayerTextures;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class CraftPlayerProfile implements PlayerProfile, SharedPlayerProfile {
    private UUID uuid;
    private String name;
    private final Set<ProfileProperty> properties = new HashSet<>();
    private CraftPlayerTextures textures;

    public CraftPlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }


    public CraftPlayerProfile(net.minecraft.world.item.component.ResolvableProfile profile) {
        com.mojang.authlib.GameProfile game = profile.partialProfile();
        this.uuid = game.id();
        this.name = game.name();
        for (com.mojang.authlib.properties.Property property : game.properties().values()) {
            this.properties.add(new ProfileProperty(property.name(), property.value(), property.signature()));
        }
    }

    @Override public @Nullable UUID getUniqueId() { return uuid; }


    @Override public @Nullable String getName() { return name; }
    @Override public @Nullable String setName(@Nullable String name) { String old = this.name; this.name = name; return old; }
    @Override public @Nullable UUID getId() { return uuid; }
    @Override public @Nullable UUID setId(@Nullable UUID uuid) { UUID old = this.uuid; this.uuid = uuid; return old; }
    @Override public @NotNull Set<ProfileProperty> getProperties() { syncTextures(); return properties; }
    @Override public void setProperties(@NotNull Collection<ProfileProperty> properties) { this.properties.clear(); this.properties.addAll(properties); }
    @Override public void setProperty(@NotNull ProfileProperty property) { properties.removeIf(p -> p.getName().equals(property.getName())); properties.add(property); }
    @Override public void clearProperties() { properties.clear(); }
    @Override public boolean removeProperty(@NotNull String name) { return properties.removeIf(p -> p.getName().equals(name)); }
    @Override public boolean hasProperty(@NotNull String name) { return properties.stream().anyMatch(p -> p.getName().equals(name)); }

    @Override
    public @NotNull PlayerTextures getTextures() {
        return craftTextures();
    }

    private CraftPlayerTextures craftTextures() {
        return textures != null ? textures : (textures = new CraftPlayerTextures(this));
    }

    @Override
    public void setTextures(@Nullable PlayerTextures textures) {
        if (textures == null) {
            craftTextures().clear();
        } else {
            craftTextures().copyFrom(textures);
        }
    }

    private void syncTextures() {
        if (textures != null) textures.rebuildPropertyIfDirty();
    }

    @Override
    public @Nullable com.mojang.authlib.properties.Property getProperty(String name) {
        syncTextures();
        for (ProfileProperty property : properties) {
            if (property.getName().equals(name)) {
                return new com.mojang.authlib.properties.Property(property.getName(), property.getValue(), property.getSignature());
            }
        }
        return null;
    }

    @Override
    public void setProperty(String name, com.mojang.authlib.properties.Property property) {
        properties.removeIf(existing -> existing.getName().equals(name));
        if (property != null) properties.add(new ProfileProperty(property.name(), property.value(), property.signature()));
    }

    @Override
    public com.mojang.authlib.GameProfile buildGameProfile() {
        syncTextures();
        com.mojang.authlib.GameProfile game = new com.mojang.authlib.GameProfile(
                uuid != null ? uuid : new UUID(0L, 0L), name != null ? name : "");
        for (ProfileProperty property : properties) {
            game.properties().put(property.getName(),
                    new com.mojang.authlib.properties.Property(property.getName(), property.getValue(), property.getSignature()));
        }
        return game;
    }

    @Override
    public net.minecraft.world.item.component.ResolvableProfile buildResolvableProfile() {
        com.mojang.authlib.GameProfile game = buildGameProfile();
        if (properties.isEmpty() && (uuid == null) != (name == null)) {
            return uuid != null ? net.minecraft.world.item.component.ResolvableProfile.createUnresolved(uuid)
                    : net.minecraft.world.item.component.ResolvableProfile.createUnresolved(name);
        }
        return net.minecraft.world.item.component.ResolvableProfile.createResolved(game);
    }

    public static PlayerProfile asBukkitCopy(com.mojang.authlib.GameProfile gameProfile) {
        CraftPlayerProfile copy = new CraftPlayerProfile(gameProfile.id(), gameProfile.name());
        for (com.mojang.authlib.properties.Property property : gameProfile.properties().values()) {
            copy.properties.add(new ProfileProperty(property.name(), property.value(), property.signature()));
        }
        return copy;
    }

    public static net.minecraft.world.item.component.ResolvableProfile asResolvableProfileCopy(PlayerProfile profile) {
        if (profile instanceof SharedPlayerProfile shared) return shared.buildResolvableProfile();
        CraftPlayerProfile copy = new CraftPlayerProfile(profile.getId(), profile.getName());
        copy.properties.addAll(profile.getProperties());
        return copy.buildResolvableProfile();
    }

    @Override
    public @NotNull CompletableFuture<PlayerProfile> update() {
        if (uuid == null) return CompletableFuture.completedFuture(this);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() == 200) {

                    StringBuilder sb = new StringBuilder();
                    try (var reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        char[] buf = new char[2048];
                        int n;
                        while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
                    }
                    String json = sb.toString();

                    int nameStart = json.indexOf("\"name\":\"");
                    if (nameStart >= 0 && this.name == null) {
                        nameStart += 8;
                        int nameEnd = json.indexOf('"', nameStart);
                        if (nameEnd > nameStart) this.name = json.substring(nameStart, nameEnd);
                    }

                    int propsStart = json.indexOf("\"properties\":[");
                    if (propsStart >= 0) {
                        propsStart += 14;
                        int propsEnd = json.lastIndexOf(']');
                        if (propsEnd > propsStart) {
                            String propsJson = json.substring(propsStart, propsEnd);

                            int pos = 0;
                            while (pos < propsJson.length()) {
                                int objStart = propsJson.indexOf('{', pos);
                                if (objStart < 0) break;
                                int objEnd = propsJson.indexOf('}', objStart);
                                if (objEnd < 0) break;
                                String obj = propsJson.substring(objStart + 1, objEnd);
                                String propName = extractJsonString(obj, "name");
                                String propValue = extractJsonString(obj, "value");
                                String propSig = extractJsonString(obj, "signature");
                                if (propName != null && propValue != null) {
                                    properties.add(new ProfileProperty(propName, propValue, propSig));
                                }
                                pos = objEnd + 1;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return this;
        });
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && (end == 0 || json.charAt(end - 1) != '\\')) break;
            end++;
        }
        return end > start ? json.substring(start, end) : null;
    }

    @Override public boolean isComplete() { return uuid != null && name != null; }
    @Override public boolean completeFromCache() { return isComplete(); }
    @Override public boolean completeFromCache(boolean onlineMode) { return isComplete(); }
    @Override public boolean completeFromCache(boolean lookupUuid, boolean onlineMode) { return isComplete(); }
    @Override public boolean complete(boolean textures) { return isComplete(); }
    @Override public boolean complete(boolean textures, boolean onlineMode) { return isComplete(); }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();
        if (uuid != null) result.put("uniqueId", uuid.toString());
        if (name != null) result.put("name", name);
        return result;
    }

    @Override
    public @NotNull CraftPlayerProfile clone() {
        CraftPlayerProfile clone = new CraftPlayerProfile(uuid, name);
        clone.properties.addAll(this.properties);
        if (this.textures != null) clone.craftTextures().copyFrom(this.textures);
        return clone;
    }
}
