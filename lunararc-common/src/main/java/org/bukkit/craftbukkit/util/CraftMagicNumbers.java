package org.bukkit.craftbukkit.util;

import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.UnsafeValues;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.potion.PotionType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@SuppressWarnings("deprecation")
public final class CraftMagicNumbers implements UnsafeValues {
    public static final CraftMagicNumbers INSTANCE = new CraftMagicNumbers();
    public static final boolean DISABLE_OLD_API_SUPPORT = Boolean.getBoolean("paper.disableOldApiSupport");

    private CraftMagicNumbers() {
    }

    private static final com.destroystokyo.paper.util.VersionFetcher VERSION_FETCHER =
            new com.destroystokyo.paper.PaperVersionFetcher();

    @Override
    public com.destroystokyo.paper.util.VersionFetcher getVersionFetcher() {
        return VERSION_FETCHER;
    }

    @Override
    public net.kyori.adventure.text.flattener.ComponentFlattener componentFlattener() {
        return net.kyori.adventure.text.flattener.ComponentFlattener.basic();
    }

    @Override
    @Deprecated(forRemoval = true)
    public net.kyori.adventure.text.serializer.plain.PlainComponentSerializer plainComponentSerializer() {
        return net.kyori.adventure.text.serializer.plain.PlainComponentSerializer.plain();
    }

    @Override
    @Deprecated(forRemoval = true)
    public net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plainTextSerializer() {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
    }

    @Override
    @Deprecated(forRemoval = true)
    public net.kyori.adventure.text.serializer.gson.GsonComponentSerializer gsonComponentSerializer() {
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
    }

    @Override
    @Deprecated(forRemoval = true)
    public net.kyori.adventure.text.serializer.gson.GsonComponentSerializer colorDownsamplingGsonComponentSerializer() {
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.colorDownsamplingGson();
    }

    @Override
    @Deprecated(forRemoval = true)
    public net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacyComponentSerializer() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
    }

    @Override
    public net.kyori.adventure.text.Component resolveWithContext(
            net.kyori.adventure.text.Component component,
            org.bukkit.command.CommandSender context,
            org.bukkit.entity.Entity scoreboardSubject,
            boolean bypassPermissions) throws IOException {
        Objects.requireNonNull(component, "component");

        net.minecraft.commands.CommandSourceStack source = null;
        if (context instanceof org.bukkit.craftbukkit.entity.CraftPlayer player) {
            source = player.getHandle().createCommandSourceStack();
        } else if (context instanceof org.bukkit.craftbukkit.CraftConsoleCommandSender) {
            source = requireServer().createCommandSourceStack();
        } else if (context instanceof org.bukkit.craftbukkit.entity.CraftEntity entity) {
            source = entity.getHandle().level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                    ? entity.getHandle().createCommandSourceStackForNameResolution(serverLevel) : null;
        } else if (context != null) {
            throw new IllegalArgumentException("Unsupported LunarArc command sender context: " + context.getClass().getName());
        }

        net.minecraft.world.entity.Entity subject = scoreboardSubject == null ? null
                : scoreboardSubject instanceof org.bukkit.craftbukkit.entity.CraftEntity craft
                        ? craft.getHandle()
                        : null;
        if (scoreboardSubject != null && subject == null) {
            throw new IllegalArgumentException("Scoreboard subject is not backed by LunarArc CraftEntity");
        }

        io.lunararcdevs.lunararc.common.bridge.CommandSourceStackBridge bridge = source == null ? null
                : (io.lunararcdevs.lunararc.common.bridge.CommandSourceStackBridge) (Object) source;
        boolean previous = bridge != null && bridge.lunararc$bypassSelectorPermissions();
        if (bridge != null && bypassPermissions) bridge.lunararc$setBypassSelectorPermissions(true);
        try {
            net.minecraft.network.chat.Component resolved = net.minecraft.network.chat.ComponentUtils.updateForEntity(
                    source, io.papermc.paper.adventure.PaperAdventure.asVanilla(component), subject, 0);
            return io.papermc.paper.adventure.PaperAdventure.asAdventure(resolved);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IOException(exception);
        } finally {
            if (bridge != null && bypassPermissions) bridge.lunararc$setBypassSelectorPermissions(previous);
        }
    }

    public static BlockState getBlock(MaterialData material) {
        Objects.requireNonNull(material, "material");
        return getBlock(material.getItemType(), material.getData());
    }

    public static BlockState getBlock(Material material, byte data) {
        Objects.requireNonNull(material, "material");
        Material legacy = material.isLegacy()
                ? material
                : org.bukkit.craftbukkit.legacy.CraftLegacy.toLegacy(material);
        return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacyData(legacy, data);
    }

    public static MaterialData getMaterial(BlockState state) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.toLegacy(state);
    }

    public static Item getItem(Material material, short data) {
        Objects.requireNonNull(material, "material");
        if (material.isLegacy()) {
            return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacyData(material, data);
        }
        return getItem(material);
    }

    public static MaterialData getMaterialData(Item item) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.toLegacyData(getMaterial(item));
    }

    public static Block getBlock(Material material) {
        Objects.requireNonNull(material, "material");
        if (material.isLegacy()) {
            return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacyData(material, (byte) 0).getBlock();
        }
        Identifier key = resourceLocation(material);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalArgumentException("Material is not a block: " + material);
        }
        return BuiltInRegistries.BLOCK.getValue(key);
    }

    public static Item getItem(Material material) {
        Objects.requireNonNull(material, "material");
        if (material.isLegacy()) {
            return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacyData(material, (short) 0);
        }
        Identifier key = resourceLocation(material);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new IllegalArgumentException("Material is not an item: " + material);
        }
        return BuiltInRegistries.ITEM.getValue(key);
    }

    // CraftBukkit resolves a block or item to its Material through a map built once at startup.
    // Resolving it by name instead - registry key, Identifier.toString(), then
    // Material.matchMaterial's own normalisation - allocates several strings per lookup, and
    // this is the hot path behind Block.getType(): a random-teleport search calls it for every
    // candidate position it probes. Memoise it the same way, lazily rather than at class init,
    // because on a hybrid server the modded half of the registries is not populated yet when
    // this class loads and LunarArcDynamicBukkitEnums may still have to mint the Material.
    // A given Block or Item instance always maps to the same Material, so the memo never goes
    // stale, and it is bounded by the size of the registries.
    private static final java.util.Map<Block, Material> BLOCK_MATERIAL = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Item, Material> ITEM_MATERIAL = new java.util.concurrent.ConcurrentHashMap<>();

    public static Material getMaterial(Block block) {
        Objects.requireNonNull(block, "block");
        Material cached = BLOCK_MATERIAL.get(block);
        if (cached != null) return cached;
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        if (key == null) throw new IllegalArgumentException("Unregistered block: " + block);
        Material material = Material.matchMaterial(key.toString());
        if (material == null) {

            material = io.lunararcdevs.lunararc.common.server.LunarArcDynamicBukkitEnums.material(key);
        }
        if (material == null) throw new IllegalArgumentException("No Bukkit Material exists for block " + key);
        BLOCK_MATERIAL.put(block, material);
        return material;
    }

    public static Material getMaterial(Item item) {
        Objects.requireNonNull(item, "item");
        Material cached = ITEM_MATERIAL.get(item);
        if (cached != null) return cached;
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) throw new IllegalArgumentException("Unregistered item: " + item);
        Material material = Material.matchMaterial(key.toString());
        if (material == null) {

            material = io.lunararcdevs.lunararc.common.server.LunarArcDynamicBukkitEnums.material(key);
        }
        if (material == null) throw new IllegalArgumentException("No Bukkit Material exists for item " + key);
        ITEM_MATERIAL.put(item, material);
        return material;
    }

    public static byte toLegacyData(BlockState state) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.toLegacyData(state);
    }

    public static boolean isLegacy(PluginDescriptionFile descriptionFile) {
        return descriptionFile.getAPIVersion() == null;
    }

    private static Identifier resourceLocation(Material material) {
        return Identifier.fromNamespaceAndPath(material.getKey().getNamespace(), material.getKey().getKey());
    }

    @Override
    public Material toLegacy(Material material) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.toLegacy(material);
    }

    @Override
    public Material fromLegacy(Material material) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacy(material);
    }

    @Override
    public Material fromLegacy(MaterialData material) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacy(material);
    }

    @Override
    public Material fromLegacy(MaterialData material, boolean itemPriority) {
        return org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacy(material, itemPriority);
    }

    @Override
    public BlockData fromLegacy(Material material, byte data) {
        return org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(getBlock(material, data));
    }

    @Override
    public Material getMaterial(String material, int version) {
        Objects.requireNonNull(material, "material");
        if (version > getDataVersion()) {
            throw new IllegalArgumentException("Newer version! Server downgrades are not supported");
        }
        if (version == getDataVersion()) return Material.getMaterial(material);

        Dynamic<Tag> name = new Dynamic<>(NbtOps.INSTANCE,
                StringTag.valueOf("minecraft:" + material.toLowerCase(Locale.ROOT)));
        Dynamic<Tag> converted = DataFixers.getDataFixer().update(
                References.ITEM_NAME, name, version, getDataVersion());
        if (name.equals(converted)) {
            converted = DataFixers.getDataFixer().update(
                    References.BLOCK_NAME, name, version, getDataVersion());
        }
        return Material.matchMaterial(converted.asString(""));
    }

    @Override
    public int getDataVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
    }

    @Override
    public ItemStack modifyItemStack(ItemStack stack, String arguments) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(arguments, "arguments");
        net.minecraft.world.item.ItemStack nms =
                org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack);
        try {
            var parsed = new ItemParser(Commands.createValidationContext(io.lunararcdevs.lunararc.common.LunarArcServerAccess.getMinecraftServer().registryAccess()))
                    .parse(new StringReader(arguments));
            nms.applyComponents(parsed.components());
        } catch (CommandSyntaxException exception) {
            Bukkit.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not modify ItemStack with data components: " + arguments, exception);
        }
        stack.setItemMeta(new org.bukkit.craftbukkit.inventory.CraftItemMeta(nms));
        return stack;
    }

    @Override
    public void checkSupported(PluginDescriptionFile pdf) throws InvalidPluginException {
        Objects.requireNonNull(pdf, "pdf");
        String apiVersion = pdf.getAPIVersion();
        if (apiVersion == null || apiVersion.isBlank()) {
            if (!DISABLE_OLD_API_SUPPORT) org.bukkit.craftbukkit.legacy.CraftLegacy.init();
            return;
        }
        if (!isSupportedApiVersion(apiVersion)) {
            throw new InvalidPluginException("Unsupported API version " + apiVersion + " for Minecraft " + io.lunararcdevs.lunararc.common.compat.PaperCompatibility.RUNTIME_MINECRAFT);
        }
        // CraftBukkit keys this off the declared version rather than only off its absence: a
        // plugin that says api-version: 1.12 is pre-flattening just as much as one that says
        // nothing, and it needs the legacy Material/block-id tables initialized before its first
        // MaterialData lookup.
        if (!DISABLE_OLD_API_SUPPORT && isPreFlattening(apiVersion)) {
            org.bukkit.craftbukkit.legacy.CraftLegacy.init();
        }
    }

    // CraftBukkit keeps one Commodore for the life of the server; it caches its reroute table, so
    // building a new one per class would be wasteful. Created lazily because Commodore's own class
    // initialization reads the mapping environment, which is not settled at <clinit> time here.
    private static volatile Commodore commodore;
    private static volatile boolean commodoreUnavailable;

    public static byte[] applyPaperPluginRewrites(PluginDescriptionFile pdf, String path, byte[] bytecode) {
        if (DISABLE_OLD_API_SUPPORT || commodoreUnavailable || pdf == null) return bytecode;

        Thread thread = Thread.currentThread();
        ClassLoader previousContext = thread.getContextClassLoader();
        thread.setContextClassLoader(CraftMagicNumbers.class.getClassLoader());
        try {
            Commodore active = getCommodore();
            if (active == null) return bytecode;

            try {
                // The active compatibility set comes from bukkit.yml (settings.compatibility), as in Paper.
                return active.convert(bytecode, pdf.getName(),
                        ApiVersion.getOrCreateVersion(pdf.getAPIVersion()), org.bukkit.craftbukkit.CraftServer.ACTIVE_COMPATIBILITIES);
            } catch (Throwable ex) {
                Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                        "Fatal error trying to convert " + pdf.getFullName() + ":" + path, ex);
                return bytecode;
            }
        } finally {
            thread.setContextClassLoader(previousContext);
        }
    }

    @Override
    public byte[] processClass(PluginDescriptionFile pdf, String path, byte[] clazz) {
        Objects.requireNonNull(clazz, "clazz");
        String className = path == null ? "<unknown>" : path.replace('\\', '/').replaceAll("\\.class$", "");
        // Paper's rewrite first, on the bytecode as the plugin author compiled it, then LunarArc's
        // hybrid NMS remap - the same order CraftBukkit uses, and the order that keeps LunarArc's
        // remapper helping Paper's transform instead of running ahead of it.
        byte[] rewritten = applyPaperPluginRewrites(pdf, path, clazz);
        return new io.lunararcdevs.lunararc.common.mod.LunarArcRemapper(true).transform(rewritten, className);
    }

    @Override
    public Advancement loadAdvancement(NamespacedKey key, String advancement) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(advancement, "advancement");
        if (Bukkit.getAdvancement(key) != null) {
            throw new IllegalArgumentException("Advancement already exists: " + key);
        }

        MinecraftServer server = io.lunararcdevs.lunararc.common.mod.server.LunarArcServer.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("MinecraftServer is not attached yet");
        }

        Identifier id = Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey());
        ServerAdvancementManager manager = server.getAdvancements();
        try {
            JsonElement json = JsonParser.parseString(advancement);
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            net.minecraft.advancements.Advancement value = net.minecraft.advancements.Advancement.CODEC
                    .parse(ops, json)
                    .getOrThrow(message -> new IllegalArgumentException(
                            "Invalid advancement " + key + ": " + message));
            AdvancementHolder holder = new AdvancementHolder(id, value);

            HashMap<Identifier, AdvancementHolder> advancements = new HashMap<>(manager.advancements);
            advancements.put(id, holder);
            manager.advancements = advancements;
            manager.tree().addAll(List.of(holder));

            AdvancementNode node = manager.tree().get(id);
            if (node != null) {
                AdvancementNode root = node.root();
                if (root.holder().value().display().isPresent()) {
                    TreeNodePosition.run(root);
                }
            }

            Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                    .resolve("bukkit")
                    .resolve("data")
                    .resolve(key.getNamespace())
                    .resolve("advancements")
                    .resolve(key.getKey() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, advancement, StandardCharsets.UTF_8);

            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getAdvancements().reload(manager);
                player.getAdvancements().flushDirty(player, true);
            }

            return Bukkit.getAdvancement(key);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist advancement " + key, exception);
        }
    }

    @Override
    public boolean removeAdvancement(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        MinecraftServer server = io.lunararcdevs.lunararc.common.mod.server.LunarArcServer.minecraftServer();
        if (server == null) return false;
        java.io.File file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                .resolve("bukkit/data/" + key.getNamespace() + "/advancements/" + key.getKey() + ".json")
                .toFile();
        return file.delete();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(Material material, EquipmentSlot slot) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(slot, "slot");
        org.bukkit.inventory.ItemType itemType = material.asItemType();
        if (itemType == null) throw new IllegalArgumentException(material + " is not an item");
        return itemType.getDefaultAttributeModifiers(slot);
    }

    @Override
    public CreativeCategory getCreativeCategory(Material material) {
        return material.getCreativeCategory();
    }

    @Override
    public String getBlockTranslationKey(Material material) {
        return getBlock(material).getDescriptionId();
    }

    @Override
    public String getItemTranslationKey(Material material) {
        return getItem(material).getDescriptionId();
    }

    @Override
    public String getTranslationKey(EntityType entityType) {
        Objects.requireNonNull(entityType, "entityType");
        Identifier key = Identifier.parse(entityType.getKey().toString());
        net.minecraft.world.entity.EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
        return type != null ? type.getDescriptionId() : net.minecraft.util.Util.makeDescriptionId("entity", key);
    }

    @Override
    public String getTranslationKey(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nms =
                org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack);
        return nms.getItem().getDescriptionId();
    }

    @Override
    public String getTranslationKey(Attribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        Identifier key = Identifier.parse(attribute.getKey().toString());
        net.minecraft.world.entity.ai.attributes.Attribute nms = BuiltInRegistries.ATTRIBUTE.getValue(key);
        if (nms == null) throw new IllegalArgumentException("Unknown attribute " + attribute.getKey());
        return nms.getDescriptionId();
    }

    @Override
    public PotionType.InternalPotionData getInternalPotionData(NamespacedKey key) {
        return new org.bukkit.craftbukkit.potion.CraftPotionType(key);
    }

    public DamageEffect getDamageEffect(String key) {
        Objects.requireNonNull(key, "key");
        for (net.minecraft.world.damagesource.DamageEffects effect : net.minecraft.world.damagesource.DamageEffects.values()) {
            if (effect.name().equalsIgnoreCase(key) || effect.toString().equalsIgnoreCase(key)) {
                return new org.bukkit.craftbukkit.damage.CraftDamageEffect(effect);
            }
        }
        return null;
    }

    @Override
    public DamageSource.Builder createDamageSourceBuilder(DamageType damageType) {
        return new org.bukkit.craftbukkit.damage.CraftDamageSourceBuilder(damageType);
    }

    @Override
    public String get(Class<?> aClass, String value) {
        return value;
    }

    @Override
    public <B extends Keyed> B get(io.papermc.paper.registry.RegistryKey<B> registry, NamespacedKey key) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(key, "key");
        return io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(registry).get(key);
    }

    private static boolean isPreFlattening(String apiVersion) {
        try {
            String[] parts = apiVersion.trim().split("\\.");
            return parts.length >= 2 && Integer.parseInt(parts[0]) == 1 && Integer.parseInt(parts[1]) < 13;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Override
    public boolean isSupportedApiVersion(String apiVersion) {
        if (apiVersion == null || apiVersion.isBlank()) return false;
        try {
            String[] parts = apiVersion.trim().split("\\.");
            if (parts.length < 2 || Integer.parseInt(parts[0]) != 1) return false;
            int minor = Integer.parseInt(parts[1]);
            if (minor > 21) return false;
            if (minor < 13) return !DISABLE_OLD_API_SUPPORT;
            if (minor < 21) return true;

            return io.lunararcdevs.lunararc.common.compat.PaperCompatibility.isSupportedApiVersion(apiVersion);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static final org.slf4j.Logger LUNARARC_NBT_LOGGER = org.slf4j.LoggerFactory.getLogger("LunarArc");

    @Override
    public byte[] serializeItem(ItemStack item) {
        Objects.requireNonNull(item, "null cannot be serialized");
        if (item.isEmpty()) throw new IllegalArgumentException("Empty itemstack cannot be serialized");
        MinecraftServer server = requireServer();
        net.minecraft.nbt.CompoundTag compound = (net.minecraft.nbt.CompoundTag) net.minecraft.world.item.ItemStack.CODEC.encodeStart(
                server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item)).getOrThrow();
        return serializeNbt(compound);
    }

    @Override
    public ItemStack deserializeItem(byte[] data) {
        return deserializeItem(deserializeNbt(data));
    }

    private ItemStack deserializeItem(net.minecraft.nbt.CompoundTag compound) {
        final int oldVersion = compound.getIntOr("DataVersion", 0);
        if (oldVersion < getDataVersion()) {
            compound = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(
                    ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.ITEM_STACK,
                    compound, oldVersion, getDataVersion());
        }
        if (compound.getStringOr("id", "minecraft:air").equals("minecraft:air")) {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(net.minecraft.world.item.ItemStack.EMPTY);
        }
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(net.minecraft.world.item.ItemStack.CODEC.parse(
                requireServer().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), compound).getOrThrow());
    }

    @Override
    public @org.jetbrains.annotations.NotNull java.util.Map<String, Object> serializeStack(final ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return java.util.Map.of("id", "minecraft:air", net.minecraft.SharedConstants.DATA_VERSION_TAG, this.getDataVersion(), "schema_version", 1);
        }
        final net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) net.minecraft.world.item.ItemStack.CODEC.encodeStart(
                requireServer().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack)).getOrThrow();
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(tag);

        final java.util.Map<String, Object> ret = new java.util.LinkedHashMap<>();
        tag.asCompound().get().forEach((key, value) -> {
            switch (key) {
                case "id" -> ret.put("id", value.asString().get());
                case "count" -> ret.put("count", value.asInt().get());
                case "components" -> {
                    final java.util.Map<String, Object> components = new java.util.LinkedHashMap<>();
                    value.asCompound().ifPresent(compoundTag -> compoundTag.forEach((componentKey, componentTag) -> components.put(componentKey, componentTag.toString())));
                    ret.put("components", components);
                }
                case net.minecraft.SharedConstants.DATA_VERSION_TAG -> ret.put(net.minecraft.SharedConstants.DATA_VERSION_TAG, value.asInt().get());
                default -> throw new IllegalStateException("Unexpected value: " + key);
            }
        });
        ret.put("schema_version", 1);
        return ret;
    }

    private static final net.minecraft.nbt.TagParser<net.minecraft.nbt.Tag> SNBT_REGISTRY_UNAWARE_PARSER = net.minecraft.nbt.TagParser.create(net.minecraft.nbt.NbtOps.INSTANCE);

    @SuppressWarnings("unchecked")
    @Override
    public @org.jetbrains.annotations.NotNull ItemStack deserializeStack(@org.jetbrains.annotations.NotNull final java.util.Map<String, Object> args) {
        final int version = args.getOrDefault("schema_version", 1) instanceof Number val ? val.intValue() : -1;

        final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        args.forEach((key, value) -> {
            switch (key) {
                case "id" -> tag.putString("id", (String) value);
                case "count" -> tag.putInt("count", ((Number) value).intValue());
                case "components" -> {
                    if (version == 1) {
                        java.util.Map<String, String> componentMap;
                        if (value instanceof java.util.Map) {
                            componentMap = (java.util.Map<String, String>) value;
                        } else if (value instanceof org.bukkit.configuration.MemorySection memory) {
                            componentMap = new java.util.HashMap<>();
                            for (final String memoryKey : memory.getKeys(false)) {
                                componentMap.put(memoryKey, memory.getString(memoryKey));
                            }
                        } else {
                            throw new IllegalArgumentException("components must be a Map");
                        }
                        final net.minecraft.nbt.CompoundTag componentsTag = new net.minecraft.nbt.CompoundTag();
                        componentMap.forEach((componentKey, componentString) -> {
                            final net.minecraft.nbt.Tag componentTag;
                            try {
                                componentTag = SNBT_REGISTRY_UNAWARE_PARSER.parseFully(componentString);
                            } catch (final com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                                throw new RuntimeException("Error parsing item stack data components", e);
                            }
                            componentsTag.put(componentKey, componentTag);
                        });
                        tag.put("components", componentsTag);
                    } else {
                        throw new IllegalStateException("Unexpected version: " + version);
                    }
                }
                case net.minecraft.SharedConstants.DATA_VERSION_TAG -> tag.putInt(net.minecraft.SharedConstants.DATA_VERSION_TAG, ((Number) value).intValue());
                case "==", "schema_version" -> { }
                default -> throw new IllegalStateException("Unexpected value: " + key);
            }
        });

        return deserializeItem(tag);
    }

    @Override
    public JsonObject serializeItemAsJson(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        if (itemStack.getType().isAir()) throw new IllegalArgumentException("air cannot be serialized");
        MinecraftServer server = requireServer();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject object = net.minecraft.world.item.ItemStack.CODEC
                .encodeStart(ops, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack))
                .getOrThrow()
                .getAsJsonObject();
        object.addProperty("DataVersion", getDataVersion());
        return object;
    }

    @Override
    public ItemStack deserializeItemFromJson(JsonObject data) throws IllegalArgumentException {
        Objects.requireNonNull(data, "data");
        MinecraftServer server = requireServer();
        JsonObject payload = data.deepCopy();
        int oldVersion = payload.has("DataVersion") ? payload.get("DataVersion").getAsInt() : getDataVersion();
        if (oldVersion > getDataVersion()) throw new IllegalArgumentException("Cannot load an item from a newer Minecraft data version");
        payload.remove("DataVersion");
        if (oldVersion < getDataVersion()) {
            payload = DataFixers.getDataFixer().update(References.ITEM_STACK,
                    new Dynamic<>(JsonOps.INSTANCE, payload), oldVersion, getDataVersion()).getValue().getAsJsonObject();
        }
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.CODEC
                .parse(ops, payload)
                .getOrThrow(IllegalArgumentException::new);
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(nms);
    }

    @Override
    public byte[] serializeEntity(org.bukkit.entity.Entity entity) {
        return serializeEntity(entity, new io.papermc.paper.entity.EntitySerializationFlag[0]);
    }

    @Override
    public byte[] serializeEntity(org.bukkit.entity.Entity entity, io.papermc.paper.entity.EntitySerializationFlag... serializationFlags) {
        Objects.requireNonNull(entity, "null cannot be serialized");
        if (!(entity instanceof org.bukkit.craftbukkit.entity.CraftEntity craft)) {
            throw new IllegalArgumentException("Only CraftEntities can be serialized");
        }

        java.util.Set<io.papermc.paper.entity.EntitySerializationFlag> flags = java.util.Set.of(serializationFlags);
        final boolean serializePassengers = flags.contains(io.papermc.paper.entity.EntitySerializationFlag.PASSENGERS);
        final boolean forceSerialization = flags.contains(io.papermc.paper.entity.EntitySerializationFlag.FORCE);
        final boolean allowPlayerSerialization = flags.contains(io.papermc.paper.entity.EntitySerializationFlag.PLAYER);
        final boolean allowMiscSerialization = flags.contains(io.papermc.paper.entity.EntitySerializationFlag.MISC);

        net.minecraft.world.entity.Entity nmsEntity = craft.getHandle();
        (serializePassengers ? nmsEntity.getSelfAndPassengers() : java.util.stream.Stream.of(nmsEntity)).forEach(e -> {
            org.bukkit.entity.Entity bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) e).lunararc$getBukkitEntity();
            if (!((bukkit.isValid() && bukkit.isPersistent()) || forceSerialization)) {
                throw new IllegalArgumentException("Cannot serialize invalid or non-persistent entity " + e.getType().toShortString() + "(" + e.getStringUUID() + ") without the FORCE flag");
            }
            if (e instanceof net.minecraft.world.entity.player.Player) {
                if (!allowPlayerSerialization) throw new IllegalArgumentException("Cannot serialize player(" + e.getStringUUID() + ") without the PLAYER flag");
            } else if (!(nmsEntity.getType().canSerialize() || allowMiscSerialization)) {
                throw new IllegalArgumentException("Cannot serialize misc non-saveable entity " + e.getType().toShortString() + "(" + e.getStringUUID() + ") without the MISC flag");
            }
        });

        try (net.minecraft.util.ProblemReporter.ScopedCollector reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                () -> "serialiseEntity@" + entity.getUniqueId(), LUNARARC_NBT_LOGGER)) {
            net.minecraft.world.level.storage.TagValueOutput output =
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, nmsEntity.registryAccess());
            if (serializePassengers) {
                if (!nmsEntity.saveAsPassenger(output)) throw new IllegalArgumentException("Couldn't serialize entity");
            } else {
                java.util.List<net.minecraft.world.entity.Entity> pass = new java.util.ArrayList<>(nmsEntity.getPassengers());
                nmsEntity.passengers = com.google.common.collect.ImmutableList.of();
                boolean serialized;
                try {
                    serialized = nmsEntity.saveAsPassenger(output);
                } finally {
                    nmsEntity.passengers = com.google.common.collect.ImmutableList.copyOf(pass);
                }
                if (!serialized) throw new IllegalArgumentException("Couldn't serialize entity");
            }
            return serializeNbt(output.buildResult());
        }
    }

    @Override
    public org.bukkit.entity.Entity deserializeEntity(byte[] data, org.bukkit.World world, boolean preserveUUID) {
        return deserializeEntity(data, world, preserveUUID, false);
    }

    @Override
    public org.bukkit.entity.Entity deserializeEntity(byte[] data, org.bukkit.World world, boolean preserveUUID, boolean preservePassengers) {
        Objects.requireNonNull(world, "world");
        if (!(world instanceof org.bukkit.craftbukkit.CraftWorld craftWorld)) {
            throw new IllegalArgumentException("World is not backed by LunarArc CraftWorld");
        }
        net.minecraft.nbt.CompoundTag compound = deserializeNbt(data);
        int oldVersion = compound.getIntOr("DataVersion", 0);
        if (oldVersion < getDataVersion()) {
            compound = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(
                    ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.ENTITY,
                    compound, oldVersion, getDataVersion());
        }
        if (!preservePassengers) compound.remove("Passengers");
        net.minecraft.world.entity.Entity nms = deserializeEntity(compound, craftWorld.getHandle(), preserveUUID);
        return ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) nms).lunararc$getBukkitEntity();
    }

    private net.minecraft.world.entity.Entity deserializeEntity(net.minecraft.nbt.CompoundTag compound, net.minecraft.server.level.ServerLevel world, boolean preserveUUID) {
        if (!preserveUUID) {
            // Generate a new UUID, so we don't have to worry about deserializing the same entity twice
            compound.remove("UUID");
        }

        final net.minecraft.world.entity.Entity nmsEntity;
        try (net.minecraft.util.ProblemReporter.ScopedCollector reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                () -> "deserialiseEntity", LUNARARC_NBT_LOGGER)) {
            nmsEntity = net.minecraft.world.entity.EntityType.create(
                    net.minecraft.world.level.storage.TagValueInput.create(reporter, world.registryAccess(), compound),
                    world,
                    net.minecraft.world.entity.EntitySpawnReason.LOAD
            ).orElseThrow(() -> new IllegalArgumentException("An ID was not found for the data. Did you downgrade?"));
        }

        compound.getList("Passengers").ifPresent(passengers -> {
            for (final net.minecraft.nbt.Tag tag : passengers) {
                if (!(tag instanceof final net.minecraft.nbt.CompoundTag serializedPassenger)) continue;
                final net.minecraft.world.entity.Entity passengerEntity = deserializeEntity(serializedPassenger, world, preserveUUID);
                passengerEntity.startRiding(nmsEntity, true, true);
            }
        });
        return nmsEntity;
    }

    @Override
    public ItemStack deserializeItemHover(final net.kyori.adventure.text.event.HoverEvent.ShowItem itemHover) {
        // Round-trip through the vanilla component so the item data components are decoded with the server registries.
        net.minecraft.network.chat.Component vanilla = io.papermc.paper.adventure.PaperAdventure.asVanilla(
                net.kyori.adventure.text.Component.empty().hoverEvent(net.kyori.adventure.text.event.HoverEvent.showItem(itemHover)));
        if (vanilla.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowItem showItem) {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(showItem.item());
        }
        throw new IllegalStateException("Could not decode show_item hover event");
    }

    @Override
    public int nextEntityId() {
        return net.minecraft.world.entity.Entity.ENTITY_COUNTER.getAndIncrement();
    }

    @Override
    public String getMainLevelName() {
        MinecraftServer server = requireServer();
        if (server instanceof net.minecraft.server.dedicated.DedicatedServer dedicated) {
            return dedicated.getProperties().levelName;
        }
        return "world";
    }

    @Override
    public int getProtocolVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().protocolVersion();
    }

    @Override
    public boolean isValidRepairItemStack(ItemStack itemToBeRepaired, ItemStack repairMaterial) {
        Objects.requireNonNull(itemToBeRepaired, "itemToBeRepaired");
        Objects.requireNonNull(repairMaterial, "repairMaterial");
        if (!itemToBeRepaired.getType().isItem() || !repairMaterial.getType().isItem()) return false;
        net.minecraft.world.item.ItemStack item = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemToBeRepaired);
        net.minecraft.world.item.ItemStack repair = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(repairMaterial);
        return item.isValidRepairItem(repair);
    }

    @Override
    public boolean hasDefaultEntityAttributes(NamespacedKey entityKey) {
        Objects.requireNonNull(entityKey, "entityKey");
        net.minecraft.world.entity.EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entityKey.toString()));
        return type != null && net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public org.bukkit.attribute.Attributable getDefaultEntityAttributes(NamespacedKey entityKey) {
        Objects.requireNonNull(entityKey, "entityKey");
        Identifier id = Identifier.tryParse(entityKey.toString());
        net.minecraft.world.entity.EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null || !net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(type)) {
            throw new IllegalArgumentException(entityKey + " doesn't have default attributes");
        }
        net.minecraft.world.entity.ai.attributes.AttributeSupplier supplier =
                net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(
                        (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) type);
        return new io.papermc.paper.attribute.UnmodifiableAttributeMap(supplier);
    }

    @Override
    public NamespacedKey getBiomeKey(org.bukkit.RegionAccessor accessor, int x, int y, int z) {
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder;
        if (accessor instanceof org.bukkit.craftbukkit.CraftWorld world) {
            holder = world.getHandle().getBiome(new net.minecraft.core.BlockPos(x, y, z));
        } else if (accessor instanceof org.bukkit.craftbukkit.generator.CraftLimitedRegion region) {
            holder = region.getHandle().getBiome(new net.minecraft.core.BlockPos(x, y, z));
        } else {
            throw new IllegalArgumentException("Unsupported RegionAccessor implementation: " + accessor.getClass().getName());
        }
        var key = holder.unwrapKey().orElseThrow(() -> new IllegalStateException("Biome has no registry key")).identifier();
        return new NamespacedKey(key.getNamespace(), key.getPath());
    }

    @Override
    public void setBiomeKey(org.bukkit.RegionAccessor accessor, int x, int y, int z, NamespacedKey biomeKey) {
        Objects.requireNonNull(accessor, "accessor");
        Objects.requireNonNull(biomeKey, "biomeKey");
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                biomeKey.getNamespace(), biomeKey.getKey());
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, id);

        if (accessor instanceof org.bukkit.craftbukkit.CraftWorld world) {
            net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> registry =
                    world.getHandle().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = registry.get(key)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown biome " + biomeKey));

            net.minecraft.world.level.chunk.LevelChunk chunk = world.getHandle().getChunk(x >> 4, z >> 4);
            int sectionIndex = chunk.getSectionIndex(y);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                throw new IllegalArgumentException("Y coordinate outside world bounds: " + y);
            }
            net.minecraft.world.level.chunk.PalettedContainerRO<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes =
                    chunk.getSection(sectionIndex).getBiomes();
            if (!(biomes instanceof net.minecraft.world.level.chunk.PalettedContainer<?> raw)) {
                throw new IllegalStateException("Biome palette is not mutable");
            }
            @SuppressWarnings("unchecked")
            net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> mutable =
                    (net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>) raw;
            mutable.set((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3, holder);
            chunk.markUnsaved();
            return;
        }
        if (accessor instanceof org.bukkit.craftbukkit.generator.CraftLimitedRegion region) {
            net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> registry =
                    region.getHandle().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = registry.get(key)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown biome " + biomeKey));
            net.minecraft.world.level.chunk.ChunkAccess chunk =
                    region.getHandle().getChunk(x >> 4, z >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY);
            int sectionIndex = chunk.getSectionIndex(y);
            net.minecraft.world.level.chunk.PalettedContainerRO<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes =
                    chunk.getSection(sectionIndex).getBiomes();
            if (!(biomes instanceof net.minecraft.world.level.chunk.PalettedContainer<?> raw)) {
                throw new IllegalStateException("Biome palette is not mutable");
            }
            @SuppressWarnings("unchecked")
            net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> mutable =
                    (net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>) raw;
            mutable.set((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3, holder);
            chunk.markUnsaved();
            return;
        }
        throw new IllegalArgumentException("Unsupported RegionAccessor implementation: " + accessor.getClass().getName());
    }

    @Override
    public String getStatisticCriteriaKey(org.bukkit.Statistic statistic) {
        Objects.requireNonNull(statistic, "statistic");
        if (statistic.getType() != org.bukkit.Statistic.Type.UNTYPED) {
            return "minecraft.custom:minecraft." + statistic.getKey().getKey();
        }
        Identifier key = Identifier.fromNamespaceAndPath(
                statistic.getKey().getNamespace(), statistic.getKey().getKey());
        return net.minecraft.stats.Stats.CUSTOM.get(key).getName();
    }

    @Override
    public org.bukkit.Color getSpawnEggLayerColor(EntityType entityType, int layer) {
        Objects.requireNonNull(entityType, "entityType");
        net.minecraft.world.entity.EntityType<?> nmsType = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.parse(entityType.getKey().toString()));
        if (nmsType == null) return null;
        net.minecraft.world.item.SpawnEggItem egg = net.minecraft.world.item.SpawnEggItem.byId(nmsType);
        // spawn egg colours moved out of the item into client resources in 1.21.5, so there is no colour to report
        return null;
    }

    @Override
    public io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin> createPluginLifecycleEventManager(
            org.bukkit.plugin.java.JavaPlugin plugin,
            java.util.function.BooleanSupplier registrationCheck) {
        return io.lunararcdevs.lunararc.common.server.LunarArcLifecycleEventManager.create(plugin, registrationCheck);
    }

    @Override
    public java.util.List<net.kyori.adventure.text.Component> computeTooltipLines(
            ItemStack itemStack,
            io.papermc.paper.inventory.tooltip.TooltipContext tooltipContext,
            org.bukkit.entity.Player player) {
        Objects.requireNonNull(itemStack, "itemStack");
        Objects.requireNonNull(tooltipContext, "tooltipContext");
        net.minecraft.world.item.TooltipFlag.Default flag = tooltipContext.isAdvanced()
                ? net.minecraft.world.item.TooltipFlag.ADVANCED
                : net.minecraft.world.item.TooltipFlag.NORMAL;
        if (tooltipContext.isCreative()) flag = flag.asCreative();
        net.minecraft.world.item.Item.TooltipContext context = net.minecraft.world.item.Item.TooltipContext.of(
                player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer
                        ? craftPlayer.getHandle().level().registryAccess()
                        : requireServer().registryAccess());
        net.minecraft.world.entity.player.Player nmsPlayer = player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer
                ? craftPlayer.getHandle() : null;
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack)
                .getTooltipLines(context, nmsPlayer, flag)
                .stream()
                .map(io.papermc.paper.adventure.PaperAdventure::asAdventure)
                .toList();
    }

    public <A extends Keyed, M> io.papermc.paper.registry.tag.Tag<A> getTag(
            io.papermc.paper.registry.tag.TagKey<A> tagKey) {
        Objects.requireNonNull(tagKey, "tagKey");
        net.kyori.adventure.key.Key registryId = tagKey.registryKey().key();
        net.minecraft.resources.Identifier registryLocation = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                registryId.namespace(), registryId.value());
        @SuppressWarnings({"rawtypes", "unchecked"})
        net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<M>> nmsRegistryKey =
                (net.minecraft.resources.ResourceKey) net.minecraft.resources.ResourceKey.createRegistryKey(registryLocation);
        java.util.Optional<net.minecraft.core.Registry<M>> nmsRegistry = requireServer().registryAccess().lookup(nmsRegistryKey);
        if (nmsRegistry.isEmpty()) return null;

        net.kyori.adventure.key.Key tagId = tagKey.key();
        net.minecraft.resources.Identifier tagLocation = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                tagId.namespace(), tagId.value());
        net.minecraft.tags.TagKey<M> nmsTagKey = net.minecraft.tags.TagKey.create(nmsRegistryKey, tagLocation);
        return nmsRegistry.get().get(nmsTagKey)
                .<io.papermc.paper.registry.tag.Tag<A>>map(named ->
                        new io.lunararcdevs.lunararc.common.server.registry.LunarArcNamedRegistryTag<>(tagKey, named))
                .orElse(null);
    }

    @Override
    public ItemStack createEmptyStack() {
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(
                net.minecraft.world.item.ItemStack.EMPTY);
    }

    private static MinecraftServer requireServer() {
        MinecraftServer server = io.lunararcdevs.lunararc.common.mod.server.LunarArcServer.minecraftServer();
        if (server == null) throw new IllegalStateException("MinecraftServer has not been attached to LunarArc yet");
        return server;
    }

    private byte[] serializeNbt(net.minecraft.nbt.CompoundTag compound) {
        compound.putInt("DataVersion", getDataVersion());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(compound, output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not serialize NBT", exception);
        }
    }

    private net.minecraft.nbt.CompoundTag deserializeNbt(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) throw new IllegalArgumentException("cannot deserialize nothing");
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            net.minecraft.nbt.CompoundTag compound = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            if (compound.getIntOr("DataVersion", 0) > getDataVersion()) {
                throw new IllegalArgumentException("Newer version! Server downgrades are not supported");
            }
            return compound;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not deserialize NBT", exception);
        }
    }

    public String getMappingsVersion() {
        return "7092ff1ff9352ad7e2260dc150e6a3ec";
    }

    public static Commodore getCommodore() {
        Commodore active = commodore;
        if (active != null || commodoreUnavailable) {
            return active;
        }
        synchronized (CraftMagicNumbers.class) {
            if (commodore == null && !commodoreUnavailable) {
                try {
                    Commodore created = new Commodore();
                    // Paper's Commodore does not build its reroute tables in the constructor -
                    // they stay null until updateReroute runs, and convert() then dies with a
                    // NullPointerException out of rerouteMethods on the first class that reaches a
                    // reroute lookup. CraftBukkit drives this from the server once its
                    // compatibility set is known; Paper disables that set outright and leaves it
                    // permanently empty, so nothing is enabled here either.
                    created.updateReroute(compatibility -> org.bukkit.craftbukkit.CraftServer.ACTIVE_COMPATIBILITIES.contains(compatibility));
                    commodore = created;
                } catch (Throwable ex) {
                    commodoreUnavailable = true;
                    Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                            "Paper's plugin rewriter is unavailable; plugins built against an older "
                                    + "Bukkit API may fail on renamed API constants", ex);
                }
            }
            return commodore;
        }
    }

    public static net.minecraft.resources.Identifier key(Material mat) {
        return org.bukkit.craftbukkit.util.CraftNamespacedKey.toMinecraft(mat.getKey());
    }
}
