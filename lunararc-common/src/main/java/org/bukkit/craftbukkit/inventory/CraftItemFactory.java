package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CraftItemFactory implements ItemFactory {
    public static final Color DEFAULT_LEATHER_COLOR = Color.fromRGB(0xA06540);
    private static final CraftItemFactory INSTANCE = new CraftItemFactory();
    private static final RandomSource RANDOM = RandomSource.create();

    private CraftItemFactory() {}

    public static CraftItemFactory instance() {
        return INSTANCE;
    }

    private static net.minecraft.world.item.ItemStack prototypeStack(Material material) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.parse(material.getKey().toString()));
        return new net.minecraft.world.item.ItemStack(item);
    }

    @Override
    public @Nullable ItemMeta getItemMeta(@NotNull Material material) {
        Objects.requireNonNull(material, "material");
        if (material == Material.AIR) return null;
        Preconditions.checkArgument(material.isItem(), "%s is not an item", material);
        net.minecraft.world.item.ItemStack prototype = prototypeStack(material);
        if (material == Material.POTION || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW) return new CraftMetaPotion(prototype);
        if (material == Material.WRITABLE_BOOK) return new CraftMetaBook(prototype);
        if (material == Material.WRITTEN_BOOK) return new CraftMetaBookSigned(prototype);
        if (material == Material.FIREWORK_ROCKET) return new CraftMetaFirework(prototype);
        if (material == Material.FIREWORK_STAR) return new CraftMetaCharge(prototype);
        if (material == Material.PLAYER_HEAD) return new CraftMetaSkull(prototype);
        if (isLeather(material)) return new CraftMetaColorableArmor(prototype);
        if (isArmor(material)) return new CraftMetaArmor(prototype);
        if (material.name().endsWith("_BANNER")) return new CraftMetaBanner(prototype);
        CraftItemMeta extra = extraMeta(material, prototype);
        return extra != null ? extra : new CraftMetaItem(prototype);
    }

    /** Metas for materials whose API sub-interface LunarArc implements through a single data component. */
    static @Nullable CraftItemMeta extraMeta(Material material, net.minecraft.world.item.ItemStack nms) {
        if (material == Material.ENCHANTED_BOOK) return new CraftMetaEnchantedBook(nms);
        if (material == Material.CROSSBOW) return new CraftMetaCrossbow(nms);
        if (material == Material.COMPASS || material == Material.RECOVERY_COMPASS) return new CraftMetaCompass(nms);
        if (material == Material.OMINOUS_BOTTLE) return new CraftMetaOminousBottle(nms);
        if (material.name().endsWith("BUNDLE")) return new CraftMetaBundle(nms);
        return null;
    }

    @Override
    public boolean isApplicable(@Nullable ItemMeta meta, @Nullable ItemStack stack) throws IllegalArgumentException {
        return stack != null && this.isApplicable(meta, stack.getType());
    }

    @Override
    public boolean isApplicable(@Nullable ItemMeta meta, @Nullable Material material) throws IllegalArgumentException {
        if (meta == null || material == null || material == Material.AIR || !material.isItem()) return false;
        Preconditions.checkArgument(meta instanceof CraftItemMeta,
                "Meta of %s was not created by LunarArc's CraftItemFactory", meta.getClass().getName());
        if (meta instanceof CraftMetaPotion) return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW;
        if (meta instanceof CraftMetaBookSigned) return material == Material.WRITTEN_BOOK;
        if (meta instanceof CraftMetaBook) return material == Material.WRITABLE_BOOK;
        if (meta instanceof CraftMetaFirework) return material == Material.FIREWORK_ROCKET;
        if (meta instanceof CraftMetaCharge) return material == Material.FIREWORK_STAR;
        if (meta instanceof CraftMetaSkull) return material == Material.PLAYER_HEAD;
        if (meta instanceof CraftMetaColorableArmor) return isLeather(material);
        if (meta instanceof CraftMetaArmor) return isArmor(material);
        if (meta instanceof CraftMetaBanner) return material.name().endsWith("_BANNER");
        if (meta instanceof CraftMetaEnchantedBook) return material == Material.ENCHANTED_BOOK;
        if (meta instanceof CraftMetaCrossbow) return material == Material.CROSSBOW;
        if (meta instanceof CraftMetaCompass) return material == Material.COMPASS || material == Material.RECOVERY_COMPASS;
        if (meta instanceof CraftMetaOminousBottle) return material == Material.OMINOUS_BOTTLE;
        if (meta instanceof CraftMetaBundle) return material.name().endsWith("BUNDLE");
        return true;
    }

    @Override
    public boolean equals(@Nullable ItemMeta meta1, @Nullable ItemMeta meta2) throws IllegalArgumentException {
        if (meta1 == meta2) return true;
        if (meta1 != null) Preconditions.checkArgument(meta1 instanceof CraftItemMeta, "Foreign ItemMeta: %s", meta1.getClass().getName());
        if (meta2 != null) Preconditions.checkArgument(meta2 instanceof CraftItemMeta, "Foreign ItemMeta: %s", meta2.getClass().getName());
        if (meta1 == null || meta2 == null) return false;
        return meta1.equals(meta2);
    }

    @Override
    public @Nullable ItemMeta asMetaFor(@NotNull ItemMeta meta, @NotNull ItemStack stack) throws IllegalArgumentException {
        Objects.requireNonNull(stack, "stack");
        return this.asMetaFor(meta, stack.getType());
    }

    @Override
    public @Nullable ItemMeta asMetaFor(@NotNull ItemMeta meta, @NotNull Material material) throws IllegalArgumentException {
        Objects.requireNonNull(material, "material");
        Preconditions.checkArgument(meta instanceof CraftItemMeta,
                "ItemMeta of %s was not created by LunarArc's CraftItemFactory", meta.getClass().getName());
        if (material == Material.AIR) return null;
        Preconditions.checkArgument(material.isItem(), "%s is not an item", material);
        CraftItemMeta base = CraftItemMeta.copyOf(meta);
        net.minecraft.world.item.ItemStack temp = new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse(material.getKey().toString())));
        base.applyToNms(temp);
        if (material == Material.POTION || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW) return new CraftMetaPotion(temp);
        if (material == Material.WRITABLE_BOOK) return new CraftMetaBook(temp);
        if (material == Material.WRITTEN_BOOK) return new CraftMetaBookSigned(temp);
        if (material == Material.FIREWORK_ROCKET) return new CraftMetaFirework(temp);
        if (material == Material.FIREWORK_STAR) return new CraftMetaCharge(temp);
        if (material == Material.PLAYER_HEAD) return new CraftMetaSkull(temp);
        if (isLeather(material)) return new CraftMetaColorableArmor(temp);
        if (isArmor(material)) return new CraftMetaArmor(temp);
        if (material.name().endsWith("_BANNER")) return new CraftMetaBanner(temp);
        CraftItemMeta extra = extraMeta(material, temp);
        return extra != null ? extra : new CraftItemMeta(temp);
    }

    static boolean isLeather(Material material) {
        return material == Material.LEATHER_HELMET || material == Material.LEATHER_CHESTPLATE
                || material == Material.LEATHER_LEGGINGS || material == Material.LEATHER_BOOTS
                || material == Material.LEATHER_HORSE_ARMOR;
    }

    private static boolean isArmor(Material material) {
        String n = material.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    @Override
    public @NotNull Color getDefaultLeatherColor() {
        return DEFAULT_LEATHER_COLOR;
    }

    @Override
    public @NotNull ItemStack createItemStack(@NotNull String input) throws IllegalArgumentException {
        Objects.requireNonNull(input, "input");
        try {
            RegistryAccess registries = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer().registryAccess();
            ItemParser.ItemResult parsed = new ItemParser(registries).parse(new StringReader(input));
            net.minecraft.world.item.ItemStack nms = new net.minecraft.world.item.ItemStack(parsed.item().value());
            DataComponentPatch patch = parsed.components();
            if (patch != null) nms.applyComponents(patch);
            return CraftItemStack.asCraftMirror(nms);
        } catch (CommandSyntaxException | RuntimeException exception) {
            throw new IllegalArgumentException("Could not parse ItemStack: " + input, exception);
        }
    }

    @Override
    public @Nullable Material getSpawnEgg(@NotNull EntityType type) {
        Objects.requireNonNull(type, "type");
        if (type == EntityType.UNKNOWN) return null;
        net.minecraft.world.entity.EntityType<?> nmsType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.parse(type.getKey().toString()));
        if (nmsType == null) return null;
        Item egg = SpawnEggItem.byId(nmsType);
        if (egg == null) return null;
        Identifier key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(egg);
        return key == null ? null : Material.matchMaterial(key.toString());
    }

    @Override
    public @NotNull ItemStack enchantItem(@NotNull Entity entity, @NotNull ItemStack item, int level, boolean allowTreasures) {
        Objects.requireNonNull(entity, "entity");
        Preconditions.checkArgument(entity instanceof CraftEntity, "Entity is not backed by LunarArc CraftEntity");
        return enchant(((CraftEntity) entity).getHandle().getRandom(), item, level, allowTreasures);
    }

    @Override
    public @NotNull ItemStack enchantItem(@NotNull World world, @NotNull ItemStack item, int level, boolean allowTreasures) {
        Objects.requireNonNull(world, "world");
        Preconditions.checkArgument(world instanceof CraftWorld, "World is not backed by LunarArc CraftWorld");
        return enchant(((CraftWorld) world).getHandle().getRandom(), item, level, allowTreasures);
    }

    @Override
    @Deprecated(since = "1.19.3")
    public @NotNull ItemStack enchantItem(@NotNull ItemStack item, int level, boolean allowTreasures) {
        return enchant(RANDOM, item, level, allowTreasures);
    }

    private static ItemStack enchant(RandomSource random, ItemStack item, int level, boolean allowTreasures) {
        Objects.requireNonNull(item, "item");
        Preconditions.checkArgument(!item.getType().isAir(), "ItemStack must not be air");
        RegistryAccess registryAccess = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer().registryAccess();
        Optional<HolderSet.Named<Enchantment>> allowed = allowTreasures
                ? Optional.empty()
                : registryAccess.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        net.minecraft.world.item.ItemStack enchanted = EnchantmentHelper.enchantItem(
                random, CraftItemStack.asNMSCopy(item), level, registryAccess, allowed);
        return CraftItemStack.asCraftMirror(enchanted);
    }

    @Override
    public @NotNull net.kyori.adventure.text.event.HoverEvent<net.kyori.adventure.text.event.HoverEvent.ShowItem> asHoverEvent(
            @NotNull ItemStack item, @NotNull UnaryOperator<net.kyori.adventure.text.event.HoverEvent.ShowItem> op) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(op, "op");
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);
        net.kyori.adventure.text.event.HoverEvent.ShowItem show = net.kyori.adventure.text.event.HoverEvent.ShowItem.showItem(
                item.getType().getKey(), item.getAmount(),
                io.papermc.paper.adventure.PaperAdventure.asAdventure(nms.getComponentsPatch()));
        return net.kyori.adventure.text.event.HoverEvent.showItem(op.apply(show));
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component displayName(@NotNull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return io.papermc.paper.adventure.PaperAdventure.asAdventure(CraftItemStack.asNMSCopy(itemStack).getDisplayName());
    }

    @Override
    @Deprecated
    public @Nullable String getI18NDisplayName(@Nullable ItemStack item) {
        if (item == null) return null;
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);
        return nms.isEmpty() ? null : net.minecraft.locale.Language.getInstance().getOrDefault(nms.getItem().getDescriptionId());
    }

    @Override
    public @NotNull ItemStack ensureServerConversions(@NotNull ItemStack item) {
        Objects.requireNonNull(item, "item");
        return CraftItemStack.asCraftMirror(CraftItemStack.asNMSCopy(item));
    }

    @Override
    @Deprecated
    public @NotNull net.md_5.bungee.api.chat.hover.content.Content hoverContentOf(@NotNull ItemStack itemStack) {
        throw new UnsupportedOperationException("BungeeCord item hover data cannot faithfully represent Minecraft 1.21.11 data components");
    }

    @Override
    @Deprecated
    public @NotNull net.md_5.bungee.api.chat.hover.content.Content hoverContentOf(@NotNull Entity entity) {
        return hoverContentOf(entity, entity.getCustomName());
    }

    @Override
    @Deprecated
    public @NotNull net.md_5.bungee.api.chat.hover.content.Content hoverContentOf(@NotNull Entity entity, @Nullable String customName) {
        return new net.md_5.bungee.api.chat.hover.content.Entity(
                entity.getType().getKey().toString(), entity.getUniqueId().toString(),
                customName == null ? null : new net.md_5.bungee.api.chat.TextComponent(customName));
    }

    @Override
    @Deprecated
    public @NotNull net.md_5.bungee.api.chat.hover.content.Content hoverContentOf(@NotNull Entity entity, @Nullable net.md_5.bungee.api.chat.BaseComponent customName) {
        return new net.md_5.bungee.api.chat.hover.content.Entity(
                entity.getType().getKey().toString(), entity.getUniqueId().toString(), customName);
    }

    @Override
    @Deprecated
    public @NotNull net.md_5.bungee.api.chat.hover.content.Content hoverContentOf(@NotNull Entity entity, @NotNull net.md_5.bungee.api.chat.BaseComponent[] customName) {
        return new net.md_5.bungee.api.chat.hover.content.Entity(
                entity.getType().getKey().toString(), entity.getUniqueId().toString(), new net.md_5.bungee.api.chat.TextComponent(customName));
    }

    @Override
    public @NotNull ItemStack enchantWithLevels(@NotNull ItemStack itemStack, int levels, boolean allowTreasure, @NotNull java.util.Random random) {
        Objects.requireNonNull(random, "random");
        Preconditions.checkArgument(levels >= 1 && levels <= 30, "levels must be in range [1, 30]");
        return enchant(new org.bukkit.craftbukkit.util.RandomSourceWrapper(random), itemStack, levels, allowTreasure);
    }

    @Override
    public @NotNull ItemStack enchantWithLevels(@NotNull ItemStack itemStack, int levels,
            @NotNull io.papermc.paper.registry.set.RegistryKeySet<org.bukkit.enchantments.Enchantment> keySet,
            @NotNull java.util.Random random) {
        Objects.requireNonNull(itemStack, "itemStack");
        Objects.requireNonNull(keySet, "keySet");
        Objects.requireNonNull(random, "random");
        Preconditions.checkArgument(levels >= 1 && levels <= 30, "levels must be in range [1, 30]");

        RegistryAccess access = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer().registryAccess();
        net.minecraft.core.Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> holders = new ArrayList<>();
        for (io.papermc.paper.registry.TypedKey<org.bukkit.enchantments.Enchantment> typedKey : keySet.values()) {
            Identifier location = Identifier.fromNamespaceAndPath(typedKey.key().namespace(), typedKey.key().value());
            registry.get(ResourceKey.create(Registries.ENCHANTMENT, location)).ifPresent(holders::add);
        }
        HolderSet<Enchantment> allowed = HolderSet.direct(holders);
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(itemStack);
        if (nms.isEnchanted()) nms.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        net.minecraft.world.item.ItemStack enchanted = EnchantmentHelper.enchantItem(
                new org.bukkit.craftbukkit.util.RandomSourceWrapper(random), nms, levels, access, Optional.of(allowed));
        return CraftItemStack.asCraftMirror(enchanted);
    }
}
