package org.bukkit.craftbukkit.enchantments;

import com.google.common.base.Preconditions;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import net.kyori.adventure.key.Key;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.EnchantmentTags;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.enchantments.EnchantmentWrapper;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class CraftEnchantment extends Enchantment implements org.bukkit.craftbukkit.util.Handleable<net.minecraft.world.item.enchantment.Enchantment> {
    private final NamespacedKey key;
    private final Holder<net.minecraft.world.item.enchantment.Enchantment> handle;

    public CraftEnchantment(@NotNull NamespacedKey key,
                            @NotNull Holder<net.minecraft.world.item.enchantment.Enchantment> handle) {
        this.key = Objects.requireNonNull(key, "key");
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public CraftEnchantment(@NotNull NamespacedKey key,
                            @NotNull net.minecraft.world.item.enchantment.Enchantment handle,
                            @NotNull net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment> registry) {
        this(key, registry.wrapAsHolder(handle));
    }

    public @NotNull net.minecraft.world.item.enchantment.Enchantment getHandle() { return this.handle.value(); }
    public @NotNull Holder<net.minecraft.world.item.enchantment.Enchantment> getHandleHolder() { return this.handle; }

    @Override public @NotNull NamespacedKey getKey() { return this.key; }
    @Override public int getMaxLevel() { return this.getHandle().getMaxLevel(); }
    @Override public int getStartLevel() { return this.getHandle().getMinLevel(); }

    @Override @Deprecated
    public @NotNull EnchantmentTarget getItemTarget() {
        throw new UnsupportedOperationException("Enchantments no longer have a single item target in Minecraft 1.21.11; use supported-item registry sets/tags instead");
    }

    @Override public boolean isTreasure() { return this.handle.is(EnchantmentTags.TREASURE); }
    @Override public boolean isCursed() { return this.handle.is(EnchantmentTags.CURSE); }

    @Override
    public boolean conflictsWith(@NotNull Enchantment other) {
        if (other instanceof EnchantmentWrapper wrapper) other = wrapper.getEnchantment();
        if (!(other instanceof CraftEnchantment craft)) return false;
        return !net.minecraft.world.item.enchantment.Enchantment.areCompatible(this.handle, craft.handle);
    }

    @Override
    public boolean canEnchantItem(@NotNull ItemStack item) {
        return this.getHandle().canEnchant(CraftItemStack.asNMSCopy(item));
    }

    @Override @Deprecated
    public @NotNull String getName() {
        if (!NamespacedKey.MINECRAFT.equals(this.key.getNamespace())) return this.key.toString();
        return switch (this.key.getKey().toUpperCase(Locale.ROOT)) {
            case "PROTECTION" -> "PROTECTION_ENVIRONMENTAL";
            case "FIRE_PROTECTION" -> "PROTECTION_FIRE";
            case "FEATHER_FALLING" -> "PROTECTION_FALL";
            case "BLAST_PROTECTION" -> "PROTECTION_EXPLOSIONS";
            case "PROJECTILE_PROTECTION" -> "PROTECTION_PROJECTILE";
            case "RESPIRATION" -> "OXYGEN";
            case "AQUA_AFFINITY" -> "WATER_WORKER";
            case "SHARPNESS" -> "DAMAGE_ALL";
            case "SMITE" -> "DAMAGE_UNDEAD";
            case "BANE_OF_ARTHROPODS" -> "DAMAGE_ARTHROPODS";
            case "LOOTING" -> "LOOT_BONUS_MOBS";
            case "EFFICIENCY" -> "DIG_SPEED";
            case "UNBREAKING" -> "DURABILITY";
            case "FORTUNE" -> "LOOT_BONUS_BLOCKS";
            case "POWER" -> "ARROW_DAMAGE";
            case "PUNCH" -> "ARROW_KNOCKBACK";
            case "FLAME" -> "ARROW_FIRE";
            case "INFINITY" -> "ARROW_INFINITE";
            case "LUCK_OF_THE_SEA" -> "LUCK";
            default -> this.key.getKey().toUpperCase(Locale.ROOT);
        };
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component displayName(int level) {
        return io.papermc.paper.adventure.PaperAdventure.asAdventure(
                net.minecraft.world.item.enchantment.Enchantment.getFullname(this.handle, level));
    }

    @Override public @NotNull String getTranslationKey() { return this.translationKey(); }

    @Override
    public @NotNull String translationKey() {
        if (this.getHandle().description().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
            return translatable.getKey();
        }
        throw new UnsupportedOperationException("Enchantment description is not translatable: " + this.key);
    }

    @Override
    public @NotNull net.kyori.adventure.text.Component description() {
        return io.papermc.paper.adventure.PaperAdventure.asAdventure(this.getHandle().description());
    }

    @Override public @NotNull RegistryKeySet<ItemType> getSupportedItems() {
        return keySet(RegistryKey.ITEM, this.getHandle().getSupportedItems());
    }

    @Override public @Nullable RegistryKeySet<ItemType> getPrimaryItems() {
        return this.getHandle().definition().primaryItems().map(holders -> keySet(RegistryKey.ITEM, holders)).orElse(null);
    }

    @Override public int getWeight() { return this.getHandle().getWeight(); }
    @Override public @NotNull RegistryKeySet<Enchantment> getExclusiveWith() {
        return keySet(RegistryKey.ENCHANTMENT, this.getHandle().exclusiveSet());
    }

    @Override public boolean isTradeable() { return this.handle.is(EnchantmentTags.TRADEABLE); }

    @Override
    public boolean isDiscoverable() {
        return this.handle.is(EnchantmentTags.IN_ENCHANTING_TABLE)
                || this.handle.is(EnchantmentTags.ON_RANDOM_LOOT)
                || this.handle.is(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT)
                || this.handle.is(EnchantmentTags.TRADEABLE)
                || this.handle.is(EnchantmentTags.ON_TRADED_EQUIPMENT);
    }

    @Override public int getMinModifiedCost(int level) { return this.getHandle().definition().minCost().calculate(level); }
    @Override public int getMaxModifiedCost(int level) { return this.getHandle().definition().maxCost().calculate(level); }
    @Override public int getAnvilCost() { return this.getHandle().definition().anvilCost(); }

    @Override @Deprecated
    public @NotNull io.papermc.paper.enchantments.EnchantmentRarity getRarity() {
        // vanilla replaced rarity by an anvil cost; the old rarities map onto the costs they used to have (1, 2, 4 and 8)
        int cost = this.getHandle().definition().anvilCost();
        if (cost >= 8) return io.papermc.paper.enchantments.EnchantmentRarity.VERY_RARE;
        if (cost >= 4) return io.papermc.paper.enchantments.EnchantmentRarity.RARE;
        if (cost >= 2) return io.papermc.paper.enchantments.EnchantmentRarity.UNCOMMON;
        return io.papermc.paper.enchantments.EnchantmentRarity.COMMON;
    }

    @Override @Deprecated
    public float getDamageIncrease(int level, @NotNull org.bukkit.entity.EntityCategory entityCategory) {
        throw new UnsupportedOperationException("Minecraft 1.21.11 enchantment damage is effect-map based and cannot be represented as one fixed increase");
    }

    @Override @Deprecated
    public float getDamageIncrease(int level, @NotNull org.bukkit.entity.EntityType entityType) {
        throw new UnsupportedOperationException("Minecraft 1.21.11 enchantment damage is effect-map based and cannot be represented as one fixed increase");
    }

    @Override
    public @NotNull Set<EquipmentSlotGroup> getActiveSlotGroups() {
        LinkedHashSet<EquipmentSlotGroup> groups = new LinkedHashSet<>();
        for (net.minecraft.world.entity.EquipmentSlotGroup group : this.getHandle().definition().slots()) {
            groups.add(CraftEquipmentSlot.getSlot(group));
        }
        return Collections.unmodifiableSet(groups);
    }

    @Override public int hashCode() { return this.key.hashCode(); }
    @Override public boolean equals(Object other) {
        return this == other || (other instanceof Enchantment enchantment && this.key.equals(enchantment.getKey()));
    }
    @Override public String toString() { return "CraftEnchantment[" + this.key + "]"; }

    private static <B extends org.bukkit.Keyed, M> RegistryKeySet<B> keySet(RegistryKey<B> registryKey, HolderSet<M> holders) {
        List<TypedKey<B>> keys = new ArrayList<>();
        for (Holder<M> holder : holders) {
            holder.unwrapKey().ifPresent(resourceKey -> {
                net.minecraft.resources.Identifier location = resourceKey.identifier();
                keys.add(TypedKey.create(registryKey, Key.key(location.getNamespace(), location.getPath())));
            });
        }
        return new DirectRegistryKeySet<>(registryKey, keys);
    }

    private static final class DirectRegistryKeySet<B extends org.bukkit.Keyed> implements RegistryKeySet<B> {
        private final RegistryKey<B> registryKey;
        private final List<TypedKey<B>> values;
        private final Set<Key> valueKeys;

        private DirectRegistryKeySet(RegistryKey<B> registryKey, Collection<TypedKey<B>> values) {
            this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
            this.values = List.copyOf(values);
            LinkedHashSet<Key> keys = new LinkedHashSet<>();
            for (TypedKey<B> value : this.values) keys.add(value.key());
            this.valueKeys = Set.copyOf(keys);
        }

        @Override public @NotNull Collection<TypedKey<B>> values() { return this.values; }

        @Override
        public @NotNull Collection<B> resolve(@NotNull Registry<B> registry) {
            Preconditions.checkArgument(registry != null, "registry cannot be null");
            List<B> resolved = new ArrayList<>(this.values.size());
            for (TypedKey<B> typed : this.values) {
                Key key = typed.key();
                B value = registry.get(new NamespacedKey(key.namespace(), key.value()));
                if (value != null) resolved.add(value);
            }
            return List.copyOf(resolved);
        }

        @Override
        public boolean contains(@NotNull TypedKey<B> typedKey) {
            return typedKey != null && this.registryKey.equals(typedKey.registryKey()) && this.valueKeys.contains(typedKey.key());
        }

        @Override public @NotNull RegistryKey<B> registryKey() { return this.registryKey; }
    }

    // CraftBukkit's conversion pair for this registry type. Plugins call these directly to cross
    // between the Bukkit handle and the NMS object, so they carry CraftBukkit's names verbatim.
    public static Enchantment minecraftToBukkit(net.minecraft.world.item.enchantment.Enchantment minecraft) {
        return org.bukkit.craftbukkit.CraftRegistry.minecraftToBukkit(minecraft, net.minecraft.core.registries.Registries.ENCHANTMENT, org.bukkit.Registry.ENCHANTMENT);
    }

    public static Enchantment minecraftHolderToBukkit(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> minecraft) {
        return CraftEnchantment.minecraftToBukkit(minecraft.value());
    }

    public static net.minecraft.world.item.enchantment.Enchantment bukkitToMinecraft(Enchantment bukkit) {
        return org.bukkit.craftbukkit.CraftRegistry.bukkitToMinecraft(bukkit);
    }

    public static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> bukkitToMinecraftHolder(Enchantment bukkit) {
        com.google.common.base.Preconditions.checkArgument(bukkit != null);

        net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment> registry = org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry(net.minecraft.core.registries.Registries.ENCHANTMENT);

        if (registry.wrapAsHolder(CraftEnchantment.bukkitToMinecraft(bukkit)) instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.enchantment.Enchantment> holder) {
            return holder;
        }

        throw new IllegalArgumentException("No Reference holder found for " + bukkit
                + ", this can happen if a plugin creates its own enchantment without properly registering it.");
    }

    public static String bukkitToString(Enchantment bukkit) {
        com.google.common.base.Preconditions.checkArgument(bukkit != null);

        return bukkit.getKey().toString();
    }

    public static Enchantment stringToBukkit(String string) {
        com.google.common.base.Preconditions.checkArgument(string != null);

        // Names were serialized before keys were, so replay the rename first, then the key rename.
        string = org.bukkit.craftbukkit.legacy.FieldRename.convertEnchantmentName(org.bukkit.craftbukkit.util.ApiVersion.CURRENT, string);
        string = string.toLowerCase(java.util.Locale.ROOT);
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(string);
        if (key == null) return null;

        return org.bukkit.craftbukkit.CraftRegistry.get(org.bukkit.Registry.ENCHANTMENT, key, org.bukkit.craftbukkit.util.ApiVersion.CURRENT);
    }
}
