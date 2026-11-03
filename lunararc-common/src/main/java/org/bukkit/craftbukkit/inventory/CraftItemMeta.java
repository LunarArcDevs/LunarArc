package org.bukkit.craftbukkit.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.craftbukkit.inventory.components.CraftFoodComponent;
import org.bukkit.craftbukkit.inventory.components.CraftToolComponent;
import org.bukkit.craftbukkit.inventory.components.CraftJukeboxComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@org.bukkit.configuration.serialization.DelegateDeserialization(SerializableMeta.class)
public class CraftItemMeta implements ItemMeta, org.bukkit.inventory.meta.Damageable, org.bukkit.inventory.meta.Repairable {

    public boolean hasDestroyableKeys() { return !destroyableKeys.isEmpty(); }
    @Override
    public @NotNull Set<com.destroystokyo.paper.Namespaced> getDestroyableKeys() { return Collections.unmodifiableSet(new LinkedHashSet<>(destroyableKeys)); }
    @Override
    public void setDestroyableKeys(@NotNull Collection<com.destroystokyo.paper.Namespaced> canDestroy) {
        Objects.requireNonNull(canDestroy, "canDestroy");
        this.destroyableKeys = new LinkedHashSet<>(canDestroy);
    }
    public boolean hasPlaceableKeys() { return !placeableKeys.isEmpty(); }
    @Override
    public @NotNull Set<com.destroystokyo.paper.Namespaced> getPlaceableKeys() { return Collections.unmodifiableSet(new LinkedHashSet<>(placeableKeys)); }
    @Override
    public void setPlaceableKeys(@NotNull Collection<com.destroystokyo.paper.Namespaced> canPlaceOn) {
        Objects.requireNonNull(canPlaceOn, "canPlaceOn");
        this.placeableKeys = new LinkedHashSet<>(canPlaceOn);
    }


    private Component displayName = null;
    private Component itemName = null;
    private List<Component> lore = null;
    private Map<Enchantment, Integer> enchantments = new HashMap<>();
    private boolean unbreakable = false;
    private int customModelData = 0;
    private boolean hasCustomModelData = false;
    private Boolean enchantmentGlintOverride = null;
    private com.google.common.collect.Multimap<Attribute, AttributeModifier> attributeModifiers = null;
    private Set<Material> canDestroy = new LinkedHashSet<>();
    private Set<Material> canPlaceOn = new LinkedHashSet<>();
    private Set<com.destroystokyo.paper.Namespaced> destroyableKeys = new LinkedHashSet<>();
    private Set<com.destroystokyo.paper.Namespaced> placeableKeys = new LinkedHashSet<>();
    private EnumSet<org.bukkit.inventory.ItemFlag> itemFlags = EnumSet.noneOf(org.bukkit.inventory.ItemFlag.class);
    private org.bukkit.inventory.ItemRarity rarity = null;
    private Integer maxStackSize = null;
    private boolean hideTooltip = false;
    private boolean fireResistant = false;
    private boolean glider = false;
    private org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent equippable;
    private org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent useCooldown;
    private org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent customModelDataComponent;
    private Integer enchantableValue;
    private NamespacedKey itemModel;
    private net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> damageResistant;
    private org.bukkit.inventory.meta.components.ToolComponent tool;
    private org.bukkit.inventory.meta.components.JukeboxPlayableComponent jukeboxPlayable;
    private org.bukkit.inventory.meta.components.FoodComponent food;
    private org.bukkit.inventory.ItemStack useRemainder;
    private NamespacedKey tooltipStyle;
    private Integer repairCost;
    private Integer damage;
    private Integer maxDamage;
    private Map<String, Object> rawDataComponents = new HashMap<>();


    private Map<net.minecraft.core.component.DataComponentType<?>, Optional<?>> unhandledDataComponents = new LinkedHashMap<>();
    private int version = 0;
    private org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer persistentDataContainer =
            new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer();

    public CraftItemMeta() {}


    public static @NotNull CraftItemMeta copyOf(@NotNull ItemMeta source) {
        Objects.requireNonNull(source, "source");
        if (source instanceof CraftItemMeta craft) return craft.clone();

        CraftItemMeta copy = new CraftItemMeta();
        if (source.hasDisplayName()) copy.displayName(source.displayName());
        if (source.hasItemName()) copy.itemName(source.itemName());
        if (source.hasLore()) copy.lore(source.lore());
        if (source.hasCustomModelData()) copy.setCustomModelData(source.getCustomModelData());
        copy.setUnbreakable(source.isUnbreakable());
        if (source.hasEnchantmentGlintOverride()) copy.setEnchantmentGlintOverride(source.getEnchantmentGlintOverride());
        if (source.hasEnchants()) {
            for (Map.Entry<Enchantment, Integer> entry : source.getEnchants().entrySet()) {
                copy.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        if (source.hasAttributeModifiers()) copy.setAttributeModifiers(source.getAttributeModifiers());
        Set<org.bukkit.inventory.ItemFlag> flags = source.getItemFlags();
        if (!flags.isEmpty()) copy.addItemFlags(flags.toArray(org.bukkit.inventory.ItemFlag[]::new));
        copy.setHideTooltip(source.isHideTooltip());
        copy.setFireResistant(source.isFireResistant());
        if (source.hasMaxStackSize()) copy.setMaxStackSize(source.getMaxStackSize());
        if (source.hasRarity()) copy.setRarity(source.getRarity());
        if (source.hasFood()) copy.setFood(source.getFood());
        if (source.hasTool()) copy.setTool(source.getTool());
        if (source.hasJukeboxPlayable()) copy.setJukeboxPlayable(source.getJukeboxPlayable());
        if (source instanceof org.bukkit.inventory.meta.Damageable damageable) {
            if (damageable.hasMaxDamage()) copy.setMaxDamage(damageable.getMaxDamage());
            if (damageable.hasDamageValue()) copy.setDamage(damageable.getDamage());
        }


        copy.setCanDestroy(source.getCanDestroy());
        copy.setCanPlaceOn(source.getCanPlaceOn());


        if (source.getPersistentDataContainer() instanceof org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer pdc) {
            copy.persistentDataContainer = new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer(pdc);
        }
        return copy;
    }


    public CraftItemMeta(ItemStack nms) {
        if (nms == null || nms.isEmpty()) return;

        net.minecraft.network.chat.Component customName = nms.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            try {
                displayName = io.papermc.paper.adventure.PaperAdventure.asAdventure(customName);
            } catch (Throwable t) {
                displayName = Component.text(customName.getString());
            }
        }

        net.minecraft.network.chat.Component nmsItemName = nms.get(DataComponents.ITEM_NAME);
        if (nmsItemName != null) {
            try {
                itemName = io.papermc.paper.adventure.PaperAdventure.asAdventure(nmsItemName);
            } catch (Throwable t) { itemName = Component.text(nmsItemName.getString()); }
        }

        ItemLore itemLore = nms.get(DataComponents.LORE);
        if (itemLore != null && !itemLore.lines().isEmpty()) {
            lore = new ArrayList<>();
            for (net.minecraft.network.chat.Component line : itemLore.lines()) {
                try {
                    lore.add(io.papermc.paper.adventure.PaperAdventure.asAdventure(line));
                } catch (Throwable t) {
                    lore.add(Component.text(line.getString()));
                }
            }
        }

        net.minecraft.util.Unit u = nms.get(DataComponents.UNBREAKABLE);
        if (u != null) unbreakable = true;

        net.minecraft.world.item.component.CustomModelData cmd = nms.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            hasCustomModelData = true;
            customModelData = cmd.floats().isEmpty() ? 0 : (int) (float) cmd.floats().get(0);
        }

        net.minecraft.world.item.enchantment.ItemEnchantments enc = nms.get(DataComponents.ENCHANTMENTS);
        if (enc != null) {
            enc.entrySet().forEach(e -> {
                try {
                    net.minecraft.resources.Identifier key =
                        e.getKey().unwrapKey().map(k -> k.identifier()).orElse(null);
                    if (key != null) {
                        Enchantment bukkit = Enchantment.getByKey(
                            org.bukkit.NamespacedKey.fromString(key.toString()));
                        if (bukkit != null) enchantments.put(bukkit, e.getIntValue());
                    }
                } catch (Throwable ignored) {}
            });
        }
        try { enchantmentGlintOverride = nms.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE); } catch (Throwable ignored) {}
        try { maxStackSize = nms.get(DataComponents.MAX_STACK_SIZE); } catch (Throwable ignored) {}
        try { repairCost = nms.get(DataComponents.REPAIR_COST); } catch (Throwable ignored) {}
        try { damage = nms.get(DataComponents.DAMAGE); } catch (Throwable ignored) {}
        try { maxDamage = nms.get(DataComponents.MAX_DAMAGE); } catch (Throwable ignored) {}
        readOptionalComponents(nms);
        readNewComponents(nms);
        captureUnhandledComponents(nms);
        loadPersistentData(nms);
    }


    public void applyToNms(ItemStack nms) {
        if (nms == null || nms.isEmpty()) return;
        applyUnhandledComponents(nms);

        if (displayName != null) {
            try {
                nms.set(DataComponents.CUSTOM_NAME, io.papermc.paper.adventure.PaperAdventure.asVanilla(displayName));
            } catch (Throwable t) {
                nms.set(DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)));
            }
        } else {
            nms.remove(DataComponents.CUSTOM_NAME);
        }

        if (itemName != null) {
            try {
                nms.set(DataComponents.ITEM_NAME, io.papermc.paper.adventure.PaperAdventure.asVanilla(itemName));
            } catch (Throwable t) {
                nms.set(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.literal(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(itemName)));
            }
        } else nms.remove(DataComponents.ITEM_NAME);

        if (lore != null && !lore.isEmpty()) {
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
            for (Component line : lore) {
                try {
                    lines.add(io.papermc.paper.adventure.PaperAdventure.asVanilla(line));
                } catch (Throwable t) {
                    lines.add(net.minecraft.network.chat.Component.literal(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line)));
                }
            }
            nms.set(DataComponents.LORE, new ItemLore(lines));
        } else {
            nms.remove(DataComponents.LORE);
        }

        if (unbreakable) {
            nms.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        } else {
            nms.remove(DataComponents.UNBREAKABLE);
        }

        if (hasCustomModelData && customModelDataComponent == null) {
            nms.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(java.util.List.of((float) customModelData), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        } else {
            nms.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
        if (enchantmentGlintOverride != null) nms.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, enchantmentGlintOverride); else nms.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (maxStackSize != null) nms.set(DataComponents.MAX_STACK_SIZE, maxStackSize); else nms.remove(DataComponents.MAX_STACK_SIZE);
        if (repairCost != null) nms.set(DataComponents.REPAIR_COST, repairCost); else nms.remove(DataComponents.REPAIR_COST);
        if (damage != null) nms.set(DataComponents.DAMAGE, damage); else nms.remove(DataComponents.DAMAGE);
        if (maxDamage != null) nms.set(DataComponents.MAX_DAMAGE, maxDamage); else nms.remove(DataComponents.MAX_DAMAGE);
        applyEnchantments(nms);
        applyAttributeModifiers(nms);
        writeOptionalComponents(nms);
        writeNewComponents(nms);
        applyPersistentData(nms);
    }


    /**
     * The components this meta reads and writes itself; everything else is carried through untouched. Built from typed constants, not by
     * reflecting on names: on Fabric/Quilt that lookup found nothing, so every component counted as unhandled and the stale copy was
     * written back over the new one (a plugin's item data vanished). A holder class keeps DataComponents from loading with CraftItemMeta.
     */
    private static final class Handled {
        static final Set<net.minecraft.core.component.DataComponentType<?>> TYPES = build();

        private static Set<net.minecraft.core.component.DataComponentType<?>> build() {
            Set<net.minecraft.core.component.DataComponentType<?>> handled = Collections.newSetFromMap(new IdentityHashMap<>());
            Collections.addAll(handled,
                    DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME, DataComponents.LORE, DataComponents.UNBREAKABLE,
                    DataComponents.CUSTOM_MODEL_DATA, DataComponents.ENCHANTMENTS, DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                    DataComponents.MAX_STACK_SIZE, DataComponents.REPAIR_COST, DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.TOOL,
                    DataComponents.FOOD, DataComponents.JUKEBOX_PLAYABLE, DataComponents.USE_REMAINDER, DataComponents.TOOLTIP_STYLE,
                    DataComponents.GLIDER, DataComponents.RARITY, DataComponents.CUSTOM_DATA, DataComponents.DAMAGE,
                    DataComponents.MAX_DAMAGE, DataComponents.WRITABLE_BOOK_CONTENT, DataComponents.WRITTEN_BOOK_CONTENT,
                    DataComponents.FIREWORKS, DataComponents.FIREWORK_EXPLOSION, DataComponents.PROFILE, DataComponents.NOTE_BLOCK_SOUND,
                    DataComponents.TRIM, DataComponents.BANNER_PATTERNS);
            return handled;
        }
    }

    private void captureUnhandledComponents(ItemStack nms) {
        try {
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry : nms.getComponentsPatch().entrySet()) {
                if (!Handled.TYPES.contains(entry.getKey())) {
                    unhandledDataComponents.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyUnhandledComponents(ItemStack nms) {
        for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry : unhandledDataComponents.entrySet()) {
            try {
                if (entry.getValue().isPresent()) {
                    nms.set((net.minecraft.core.component.DataComponentType) entry.getKey(), entry.getValue().get());
                } else {
                    nms.remove(entry.getKey());
                }
            } catch (Throwable ignored) {
            }
        }
    }


    private void applyEnchantments(ItemStack nms) {
        try {
            net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment> registry =
                    ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer().registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            net.minecraft.world.item.enchantment.ItemEnchantments.Mutable mutable =
                    new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key =
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                                net.minecraft.resources.Identifier.parse(entry.getKey().getKey().toString()));
                registry.get(key).ifPresent(holder -> mutable.set(holder, entry.getValue()));
            }
            nms.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        } catch (Throwable ignored) {
            Object existing = rawDataComponents.get("ENCHANTMENTS");
            if (existing != null) setComponent(nms, "ENCHANTMENTS", existing);
        }
    }

    private void applyAttributeModifiers(ItemStack nms) {
        if (attributeModifiers == null) {
            setComponent(nms, "ATTRIBUTE_MODIFIERS", null);
            return;
        }
        try {
            net.minecraft.core.Registry<net.minecraft.world.entity.ai.attributes.Attribute> registry =
                    ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer().registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.ATTRIBUTE);
            net.minecraft.world.item.component.ItemAttributeModifiers.Builder builder =
                    net.minecraft.world.item.component.ItemAttributeModifiers.builder();
            for (Map.Entry<Attribute, AttributeModifier> entry : attributeModifiers.entries()) {
                net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.attributes.Attribute> key =
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ATTRIBUTE,
                                net.minecraft.resources.Identifier.parse(entry.getKey().getKey().toString()));
                java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute>> holder =
                        registry.get(key);
                if (holder.isEmpty()) continue;
                builder.add(holder.get(), (net.minecraft.world.entity.ai.attributes.AttributeModifier) org.bukkit.craftbukkit.attribute.CraftAttributeInstance.toMinecraft(entry.getValue()),
                        toMinecraftSlotGroup(entry.getValue().getSlotGroup()));
            }
            nms.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    builder.build());
        } catch (Throwable ignored) {
            Object existing = rawDataComponents.get("ATTRIBUTE_MODIFIERS");
            if (existing != null) setComponent(nms, "ATTRIBUTE_MODIFIERS", existing);
        }
    }

    private static net.minecraft.world.entity.EquipmentSlotGroup toMinecraftSlotGroup(EquipmentSlotGroup group) {
        String requested = group == null ? "ANY" : group.toString().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        try {
            return net.minecraft.world.entity.EquipmentSlotGroup.valueOf(requested);
        } catch (IllegalArgumentException unknown) {
            return net.minecraft.world.entity.EquipmentSlotGroup.ANY;
        }
    }

    // Typed constants rather than DataComponents.class.getField(name): reflection by Mojang name finds nothing on Fabric/Quilt, where the
    // game's fields carry intermediary names, so none of these components were ever read or written there. FIRE_RESISTANT and
    // HIDE_TOOLTIP are no longer components in 1.21.11 and stay unmapped.
    private static net.minecraft.core.component.DataComponentType<?> componentType(String field) {
        return switch (field) {
            case "TOOL" -> DataComponents.TOOL;
            case "FOOD" -> DataComponents.FOOD;
            case "JUKEBOX_PLAYABLE" -> DataComponents.JUKEBOX_PLAYABLE;
            case "USE_REMAINDER" -> DataComponents.USE_REMAINDER;
            case "TOOLTIP_STYLE" -> DataComponents.TOOLTIP_STYLE;
            case "GLIDER" -> DataComponents.GLIDER;
            case "RARITY" -> DataComponents.RARITY;
            case "ENCHANTMENTS" -> DataComponents.ENCHANTMENTS;
            case "ATTRIBUTE_MODIFIERS" -> DataComponents.ATTRIBUTE_MODIFIERS;
            default -> null;
        };
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getComponent(ItemStack stack, String field) {
        net.minecraft.core.component.DataComponentType type = componentType(field);
        return type == null ? null : stack.get(type);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(ItemStack stack, String field, Object value) {
        net.minecraft.core.component.DataComponentType type = componentType(field);
        if (type == null) return;
        try {
            if (value == null) stack.remove(type);
            else stack.set(type, value);
        } catch (Throwable ignored) {}
    }
    private void readNewComponents(ItemStack nms) {
        net.minecraft.resources.Identifier model = nms.get(DataComponents.ITEM_MODEL);
        if (model != null) itemModel = NamespacedKey.fromString(model.toString());
        net.minecraft.world.item.enchantment.Enchantable enchantable = nms.get(DataComponents.ENCHANTABLE);
        if (enchantable != null) enchantableValue = enchantable.value();
        net.minecraft.world.item.component.DamageResistant resistant = nms.get(DataComponents.DAMAGE_RESISTANT);
        if (resistant != null) damageResistant = resistant.types();
        net.minecraft.world.item.component.UseCooldown cooldown = nms.get(DataComponents.USE_COOLDOWN);
        if (cooldown != null) useCooldown = new org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent(cooldown);
        net.minecraft.world.item.equipment.Equippable equip = nms.get(DataComponents.EQUIPPABLE);
        if (equip != null) equippable = new org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent(equip);
        net.minecraft.world.item.component.CustomModelData cmd = nms.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) customModelDataComponent = new org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent(cmd);
    }

    private void writeNewComponents(ItemStack nms) {
        if (itemModel != null) nms.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.parse(itemModel.toString()));
        if (enchantableValue != null) nms.set(DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable(enchantableValue));
        if (damageResistant != null) nms.set(DataComponents.DAMAGE_RESISTANT, new net.minecraft.world.item.component.DamageResistant(damageResistant));
        if (useCooldown != null) nms.set(DataComponents.USE_COOLDOWN, useCooldown.getHandle());
        if (equippable != null) nms.set(DataComponents.EQUIPPABLE, equippable.getHandle());
        if (customModelDataComponent != null) nms.set(DataComponents.CUSTOM_MODEL_DATA, customModelDataComponent.getHandle());
    }

    private void readOptionalComponents(ItemStack nms) {
        for (String key : new String[]{"TOOL","FOOD","JUKEBOX_PLAYABLE","USE_REMAINDER","TOOLTIP_STYLE","GLIDER","FIRE_RESISTANT","HIDE_TOOLTIP","RARITY","ENCHANTMENTS","ATTRIBUTE_MODIFIERS"}) {
            Object value = getComponent(nms, key); if (value != null) rawDataComponents.put(key, value);
        }
        glider = rawDataComponents.containsKey("GLIDER");
        fireResistant = rawDataComponents.containsKey("FIRE_RESISTANT");
        hideTooltip = rawDataComponents.containsKey("HIDE_TOOLTIP");
        Object remainder = rawDataComponents.get("USE_REMAINDER");
        if (remainder instanceof net.minecraft.world.item.ItemStack is) useRemainder = CraftItemStack.asBukkitCopy(is);
        Object style = rawDataComponents.get("TOOLTIP_STYLE");
        if (style instanceof net.minecraft.resources.Identifier rl) tooltipStyle = NamespacedKey.fromString(rl.toString());
        Object r = rawDataComponents.get("RARITY");
        if (r instanceof Enum<?> e) try { rarity = org.bukkit.inventory.ItemRarity.valueOf(e.name()); } catch (IllegalArgumentException ignored) {}
        Object rawFood = rawDataComponents.get("FOOD");
        if (rawFood instanceof net.minecraft.world.food.FoodProperties fp) food = new CraftFoodComponent(fp);
        Object rawTool = rawDataComponents.get("TOOL");
        if (rawTool instanceof net.minecraft.world.item.component.Tool t) tool = new CraftToolComponent(t);
        Object rawJukebox = rawDataComponents.get("JUKEBOX_PLAYABLE");
        if (rawJukebox instanceof net.minecraft.world.item.JukeboxPlayable jp) jukeboxPlayable = new CraftJukeboxComponent(jp);
    }
    // FOOD, TOOL, JUKEBOX_PLAYABLE and RARITY are written only when this meta actually holds
    // one - setComponent(nms, key, null) removes the component, so writing null unconditionally
    // would strip a target stack's existing default (e.g. a pickaxe's TOOL) whenever this meta
    // never read one in.
    private void writeOptionalComponents(ItemStack nms) {
        if (food instanceof CraftFoodComponent c) setComponent(nms, "FOOD", c.getHandle());
        if (tool instanceof CraftToolComponent c) setComponent(nms, "TOOL", c.getHandle());
        if (jukeboxPlayable instanceof CraftJukeboxComponent c) setComponent(nms, "JUKEBOX_PLAYABLE", c.getHandle());
        setComponent(nms, "USE_REMAINDER", useRemainder == null ? null : CraftItemStack.asNMSCopy(useRemainder));
        setComponent(nms, "TOOLTIP_STYLE", tooltipStyle == null ? null : net.minecraft.resources.Identifier.parse(tooltipStyle.toString()));
        writeUnitFlag(nms, "GLIDER", glider); writeUnitFlag(nms, "FIRE_RESISTANT", fireResistant); writeUnitFlag(nms, "HIDE_TOOLTIP", hideTooltip);
        if (rarity != null) {
            try { setComponent(nms, "RARITY", net.minecraft.world.item.Rarity.valueOf(rarity.name())); } catch (Throwable ignored) {}
        }
    }
    private void writeUnitFlag(ItemStack nms, String name, boolean enabled) {
        if (!enabled) { setComponent(nms, name, null); return; }
        Object existing = rawDataComponents.get(name); if (existing != null) { setComponent(nms,name,existing); return; }
        setComponent(nms, name, net.minecraft.util.Unit.INSTANCE);
    }

    // Direct calls on purpose: reflecting with Mojang names (copyTag, set, of) finds nothing on Fabric/Quilt, where the game's classes carry
    // intermediary names at runtime, and the swallowed exception silently dropped every plugin's item data there.
    private void loadPersistentData(ItemStack nms) {
        net.minecraft.world.item.component.CustomData customData = nms.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        net.minecraft.nbt.CompoundTag root = customData.copyTag();
        net.minecraft.nbt.Tag values = root.get("PublicBukkitValues");
        if (values instanceof net.minecraft.nbt.CompoundTag compound) {
            persistentDataContainer.fromTag(compound);
        }
    }

    private void applyPersistentData(ItemStack nms) {
        net.minecraft.world.item.component.CustomData current = nms.get(DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = current == null ? new net.minecraft.nbt.CompoundTag() : current.copyTag();

        if (persistentDataContainer.isEmpty()) root.remove("PublicBukkitValues");
        else root.put("PublicBukkitValues", persistentDataContainer.toTag());

        nms.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
    }


    @Override
    public @Nullable Component displayName() {
        return displayName;
    }

    @Override
    public void displayName(@Nullable Component displayName) {
        this.displayName = displayName;
    }

    @Override
    public @NotNull Component itemName() {
        return itemName != null ? itemName : Component.empty();
    }

    @Override
    public void itemName(@Nullable Component name) { this.itemName = name; }

    @Override
    public @Nullable List<Component> lore() {
        return lore;
    }

    @Override
    public void lore(@Nullable List<? extends Component> lore) {
        this.lore = lore == null ? null : new ArrayList<>(lore);
    }


    @Override
    public boolean hasDisplayName() {
        return displayName != null;
    }

    @Override
    public @NotNull String getDisplayName() {
        if (displayName == null) return "";
        return LegacyComponentSerializer.legacySection().serialize(displayName);
    }

    @Override
    public void setDisplayName(@Nullable String name) {
        this.displayName = name == null ? null :
            LegacyComponentSerializer.legacySection().deserialize(name);
    }

    @Override
    public boolean hasItemName() { return itemName != null; }

    @Override
    public @NotNull String getItemName() { return itemName == null ? "" : LegacyComponentSerializer.legacySection().serialize(itemName); }

    @Override
    public void setItemName(@Nullable String name) { itemName = name == null ? null : LegacyComponentSerializer.legacySection().deserialize(name); }

    @Override
    public boolean hasLore() {
        return lore != null && !lore.isEmpty();
    }

    @Override
    public @Nullable List<String> getLore() {
        if (lore == null) return null;
        List<String> out = new ArrayList<>();
        for (Component c : lore) out.add(LegacyComponentSerializer.legacySection().serialize(c));
        return out;
    }

    @Override
    public void setLore(@Nullable List<String> lore) {
        if (lore == null) { this.lore = null; return; }
        this.lore = new ArrayList<>();
        for (String s : lore) this.lore.add(LegacyComponentSerializer.legacySection().deserialize(s));
    }


    @Override
    public boolean hasEnchants() {
        return !enchantments.isEmpty();
    }

    @Override
    public boolean hasEnchant(@NotNull Enchantment ench) {
        return enchantments.containsKey(ench);
    }

    @Override
    public int getEnchantLevel(@NotNull Enchantment ench) {
        return enchantments.getOrDefault(ench, 0);
    }

    @Override
    public @NotNull Map<Enchantment, Integer> getEnchants() {
        return Collections.unmodifiableMap(enchantments);
    }

    @Override
    public boolean addEnchant(@NotNull Enchantment ench, int level, boolean ignoreLevelRestriction) {
        enchantments.put(ench, level);
        return true;
    }

    @Override
    public boolean removeEnchant(@NotNull Enchantment ench) {
        return enchantments.remove(ench) != null;
    }

    @Override
    public void removeEnchantments() {
        enchantments.clear();
    }

    public boolean removeEnchants(@NotNull Enchantment... enchantments) {
        boolean changed = false;
        for (Enchantment e : enchantments) changed |= removeEnchant(e);
        return changed;
    }

    @Override
    public boolean hasConflictingEnchant(@NotNull Enchantment ench) {
        Objects.requireNonNull(ench, "ench");
        for (Enchantment existing : enchantments.keySet()) {
            if (existing.conflictsWith(ench)) return true;
        }
        return false;
    }

    public boolean isEnchantmentGlintOverrideSet() { return enchantmentGlintOverride != null; }
    @Override
    public boolean hasEnchantmentGlintOverride() { return enchantmentGlintOverride != null; }
    @Override
    public @Nullable Boolean getEnchantmentGlintOverride() { return enchantmentGlintOverride; }
    @Override
    public void setEnchantmentGlintOverride(@Nullable Boolean override) { this.enchantmentGlintOverride = override; }


    @Override
    public boolean hasCustomModelData() {
        return hasCustomModelData;
    }

    @Override
    public int getCustomModelData() {
        return customModelData;
    }

    @Override
    public void setCustomModelData(@Nullable Integer data) {
        if (data == null) { hasCustomModelData = false; customModelData = 0; }
        else { hasCustomModelData = true; customModelData = data; }
        customModelDataComponent = null;
    }


    @Override
    public boolean isUnbreakable() {
        return unbreakable;
    }

    @Override
    public void setUnbreakable(boolean unbreakable) {
        this.unbreakable = unbreakable;
    }


    @Override
    public boolean hasAttributeModifiers() { return attributeModifiers != null && !attributeModifiers.isEmpty(); }

    @Override
    public @Nullable com.google.common.collect.Multimap<Attribute, AttributeModifier> getAttributeModifiers() {
        if (attributeModifiers == null) return null;
        return com.google.common.collect.ImmutableMultimap.copyOf(attributeModifiers);
    }

    @Override
    public @NotNull com.google.common.collect.Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            @NotNull EquipmentSlot slot) {
        Objects.requireNonNull(slot, "slot");
        com.google.common.collect.ImmutableMultimap.Builder<Attribute, AttributeModifier> out = com.google.common.collect.ImmutableMultimap.builder();
        if (attributeModifiers != null) {
            for (Map.Entry<Attribute, AttributeModifier> entry : attributeModifiers.entries()) {
                if (entry.getValue().getSlotGroup().test(slot)) out.put(entry.getKey(), entry.getValue());
            }
        }
        return out.build();
    }

    public @NotNull com.google.common.collect.Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            @NotNull EquipmentSlotGroup group) {
        Objects.requireNonNull(group, "group");
        com.google.common.collect.ImmutableMultimap.Builder<Attribute, AttributeModifier> out = com.google.common.collect.ImmutableMultimap.builder();
        if (attributeModifiers != null) {
            for (Map.Entry<Attribute, AttributeModifier> entry : attributeModifiers.entries()) {
                EquipmentSlotGroup modifierGroup = entry.getValue().getSlotGroup();
                boolean matches = false;
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (group.test(slot) && modifierGroup.test(slot)) { matches = true; break; }
                }
                if (matches) out.put(entry.getKey(), entry.getValue());
            }
        }
        return out.build();
    }

    @Override
    public @Nullable Collection<AttributeModifier> getAttributeModifiers(@NotNull Attribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (attributeModifiers == null || !attributeModifiers.containsKey(attribute)) return null;
        return Collections.unmodifiableList(new ArrayList<>(attributeModifiers.get(attribute)));
    }

    @Override
    public boolean addAttributeModifier(@NotNull Attribute attribute, @NotNull AttributeModifier modifier) {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(modifier, "modifier");
        if (attributeModifiers == null) attributeModifiers = com.google.common.collect.LinkedHashMultimap.create();
        for (AttributeModifier existing : attributeModifiers.get(attribute)) {
            if (existing.getKey().equals(modifier.getKey()) || existing.getUniqueId().equals(modifier.getUniqueId())) {
                throw new IllegalArgumentException("Modifier is already applied on this attribute");
            }
        }
        return attributeModifiers.put(attribute, modifier);
    }

    @Override
    public void setAttributeModifiers(@Nullable com.google.common.collect.Multimap<Attribute, AttributeModifier> modifiers) {
        if (modifiers == null) { attributeModifiers = null; return; }
        com.google.common.collect.Multimap<Attribute, AttributeModifier> replacement = com.google.common.collect.LinkedHashMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            for (AttributeModifier existing : replacement.get(entry.getKey())) {
                if (existing.getKey().equals(entry.getValue().getKey()) || existing.getUniqueId().equals(entry.getValue().getUniqueId())) {
                    throw new IllegalArgumentException("Duplicate modifier for attribute " + entry.getKey());
                }
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        attributeModifiers = replacement;
    }

    @Override
    public boolean removeAttributeModifier(@NotNull Attribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (attributeModifiers == null) return false;
        boolean changed = !attributeModifiers.removeAll(attribute).isEmpty();
        if (attributeModifiers.isEmpty()) attributeModifiers = null;
        return changed;
    }

    @Override
    public boolean removeAttributeModifier(@NotNull EquipmentSlot slot) {
        Objects.requireNonNull(slot, "slot");
        if (attributeModifiers == null) return false;
        boolean changed = attributeModifiers.entries().removeIf(e -> e.getValue().getSlotGroup().test(slot));
        if (attributeModifiers.isEmpty()) attributeModifiers = null;
        return changed;
    }

    public boolean removeAttributeModifier(@NotNull EquipmentSlotGroup group) {
        Objects.requireNonNull(group, "group");
        if (attributeModifiers == null) return false;
        boolean changed = attributeModifiers.entries().removeIf(e -> {
            EquipmentSlotGroup modifierGroup = e.getValue().getSlotGroup();
            for (EquipmentSlot slot : EquipmentSlot.values()) if (group.test(slot) && modifierGroup.test(slot)) return true;
            return false;
        });
        if (attributeModifiers.isEmpty()) attributeModifiers = null;
        return changed;
    }

    @Override
    public boolean removeAttributeModifier(@NotNull Attribute attribute, @NotNull AttributeModifier modifier) {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(modifier, "modifier");
        if (attributeModifiers == null) return false;
        boolean changed = attributeModifiers.get(attribute).removeIf(existing ->
                existing.getKey().equals(modifier.getKey()) || existing.getUniqueId().equals(modifier.getUniqueId()));
        if (attributeModifiers.isEmpty()) attributeModifiers = null;
        return changed;
    }


    @Override
    public boolean hasLocalizedName() { return false; }
    @Override
    public @NotNull String getLocalizedName() { return getDisplayName(); }
    @Override
    public void setLocalizedName(@Nullable String name) {}

    @Override
    public @NotNull Set<org.bukkit.Material> getCanDestroy() { return Collections.unmodifiableSet(new LinkedHashSet<>(canDestroy)); }
    @Override
    public void setCanDestroy(@Nullable Set<org.bukkit.Material> materials) { this.canDestroy = materials == null ? new LinkedHashSet<>() : new LinkedHashSet<>(materials); }
    @Override
    public @NotNull Set<org.bukkit.Material> getCanPlaceOn() { return Collections.unmodifiableSet(new LinkedHashSet<>(canPlaceOn)); }
    @Override
    public void setCanPlaceOn(@Nullable Set<org.bukkit.Material> materials) { this.canPlaceOn = materials == null ? new LinkedHashSet<>() : new LinkedHashSet<>(materials); }

    public boolean hasCustomTags() { return !persistentDataContainer.isEmpty(); }
    @Override
    public @NotNull org.bukkit.inventory.meta.tags.CustomItemTagContainer getCustomTagContainer() {
        return new org.bukkit.craftbukkit.inventory.tags.DeprecatedCustomTagContainer(this.persistentDataContainer);
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    @Override
    public @NotNull String getAsString() {
        try {
            ItemStack stack = new ItemStack(net.minecraft.world.item.Items.STONE);
            applyToNms(stack);
            net.minecraft.core.component.DataComponentPatch patch = stack.getComponentsPatch();
            net.minecraft.nbt.Tag nbt = net.minecraft.core.component.DataComponentPatch.CODEC
                    .encodeStart(io.lunararcdevs.lunararc.common.LunarArcServerAccess.getMinecraftServer().registryAccess()
                            .createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), patch)
                    .getOrThrow();
            return nbt.toString();
        } catch (Throwable ignored) {
            return "{}";
        }
    }

    @Override
    public @NotNull String getAsComponentString() {
        try {
            ItemStack stack = new ItemStack(net.minecraft.world.item.Items.STONE);
            applyToNms(stack);
            net.minecraft.core.component.DataComponentPatch patch = stack.getComponentsPatch();
            net.minecraft.core.RegistryAccess registryAccess = io.lunararcdevs.lunararc.common.LunarArcServerAccess.getMinecraftServer().registryAccess();
            com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops = registryAccess.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            net.minecraft.core.Registry<net.minecraft.core.component.DataComponentType<?>> componentRegistry =
                    registryAccess.lookupOrThrow(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE);
            StringJoiner out = new StringJoiner(",", "[", "]");
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                String key = componentRegistry.getResourceKey(entry.getKey()).orElseThrow().identifier().toString();
                if (entry.getValue().isEmpty()) {
                    out.add("!" + key);
                } else {
                    net.minecraft.nbt.Tag encoded = (net.minecraft.nbt.Tag) ((net.minecraft.core.component.DataComponentType) entry.getKey())
                            .codecOrThrow().encodeStart(ops, entry.getValue().get()).getOrThrow();
                    out.add(key + "=" + encoded);
                }
            }
            return out.toString();
        } catch (Throwable ignored) {
            return "[]";
        }
    }

    @Override
    public boolean hasRarity() { return rarity != null; }
    @Override
    public org.bukkit.inventory.ItemRarity getRarity() {
        if (rarity == null) throw new IllegalStateException("We don't have rarity! Check hasRarity first!");
        return rarity;
    }
    @Override
    public void setRarity(@Nullable org.bukkit.inventory.ItemRarity rarity) { this.rarity = rarity; }
    public boolean isGlider() { return glider; }
    public void setGlider(boolean glider) { this.glider = glider; }
    @Override public boolean hasTool() { return tool != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.ToolComponent getTool() {
        if (tool instanceof CraftToolComponent c) return new CraftToolComponent(c);

        return new CraftToolComponent(new net.minecraft.world.item.component.Tool(java.util.List.of(), 1.0f, 0, true));
    }
    @Override public void setTool(@Nullable org.bukkit.inventory.meta.components.ToolComponent value) {
        this.tool = value == null ? null : new CraftToolComponent((CraftToolComponent) value);
        rawDataComponents.remove("TOOL");
    }
    @Override public boolean hasJukeboxPlayable() { return jukeboxPlayable != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.JukeboxPlayableComponent getJukeboxPlayable() {
        if (jukeboxPlayable instanceof CraftJukeboxComponent c) return new CraftJukeboxComponent(c);
        return new CraftJukeboxComponent(new net.minecraft.world.item.JukeboxPlayable(
                new net.minecraft.world.item.EitherHolder<>(net.minecraft.world.item.JukeboxSongs.THIRTEEN)));
    }
    @Override public void setJukeboxPlayable(@Nullable org.bukkit.inventory.meta.components.JukeboxPlayableComponent value) {
        this.jukeboxPlayable = value == null ? null : new CraftJukeboxComponent((CraftJukeboxComponent) value);
        rawDataComponents.remove("JUKEBOX_PLAYABLE");
    }
    @Override public boolean hasFood() { return food != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.FoodComponent getFood() {
        if (food instanceof CraftFoodComponent c) return new CraftFoodComponent(c);
        return new CraftFoodComponent(new net.minecraft.world.food.FoodProperties(0, 0.0f, false));
    }
    @Override public void setFood(@Nullable org.bukkit.inventory.meta.components.FoodComponent value) {
        this.food = value == null ? null : new CraftFoodComponent((CraftFoodComponent) value);
        rawDataComponents.remove("FOOD");
    }
    public boolean hasUseRemainder() { return useRemainder != null; }
    public @Nullable org.bukkit.inventory.ItemStack getUseRemainder() { return useRemainder == null ? null : useRemainder.clone(); }
    public void setUseRemainder(@Nullable org.bukkit.inventory.ItemStack remainder) { useRemainder = remainder == null ? null : remainder.clone(); }
    @Override public boolean hasMaxStackSize() { return maxStackSize != null; }
    @Override public int getMaxStackSize() {
        if (maxStackSize == null) throw new IllegalStateException("We don't have max_stack_size! Check hasMaxStackSize first!");
        return maxStackSize;
    }
    @Override public void setMaxStackSize(@Nullable Integer max) {
        if (max != null && (max < 1 || max > 99)) throw new IllegalArgumentException("max stack size must be between 1 and 99");
        this.maxStackSize = max;
    }
    public boolean hasTooltipStyle() { return tooltipStyle != null; }
    public @Nullable NamespacedKey getTooltipStyle() { return tooltipStyle; }
    public void setTooltipStyle(@Nullable NamespacedKey key) { tooltipStyle = key; }
    @Override public boolean isHideTooltip() { return hideTooltip; }
    @Override public void setHideTooltip(boolean hide) { this.hideTooltip = hide; }
    public boolean hasDyedColor() { return false; }
    public boolean hasCustomName() { return hasDisplayName(); }
    public boolean hasRepairCost() { return repairCost != null && repairCost > 0; }
    public int getRepairCost() { return repairCost == null ? 0 : repairCost; }
    public void setRepairCost(int cost) { if (cost < 0) throw new IllegalArgumentException("repair cost must be >= 0"); repairCost = cost; }
    public boolean isFireResistant() { return damageResistant == net.minecraft.tags.DamageTypeTags.IS_FIRE; }
    public void setFireResistant(boolean fireResistant) {
        this.fireResistant = fireResistant;
        if (fireResistant) this.damageResistant = net.minecraft.tags.DamageTypeTags.IS_FIRE;
        else if (this.damageResistant == net.minecraft.tags.DamageTypeTags.IS_FIRE) this.damageResistant = null;
    }
    public net.md_5.bungee.api.chat.BaseComponent[] getDisplayNameComponent() {
        if (displayName == null) return new net.md_5.bungee.api.chat.BaseComponent[0];
        try {
            return net.md_5.bungee.chat.ComponentSerializer.parse(GsonComponentSerializer.gson().serialize(displayName));
        } catch (Throwable ignored) {
            return new net.md_5.bungee.api.chat.BaseComponent[] { new net.md_5.bungee.api.chat.TextComponent(
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)) };
        }
    }
    public void setDisplayNameComponent(net.md_5.bungee.api.chat.BaseComponent[] component) {
        if (component == null) { displayName = null; return; }
        try {
            displayName = GsonComponentSerializer.gson().deserialize(net.md_5.bungee.chat.ComponentSerializer.toString(component));
        } catch (Throwable ignored) {
            displayName = LegacyComponentSerializer.legacySection().deserialize(net.md_5.bungee.api.chat.BaseComponent.toLegacyText(component));
        }
    }
    @Nullable public java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> getLoreComponents() {
        if (lore == null) return null;
        java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> out = new java.util.ArrayList<>();
        for (Component line : lore) {
            try {
                out.add(net.md_5.bungee.chat.ComponentSerializer.parse(GsonComponentSerializer.gson().serialize(line)));
            } catch (Throwable ignored) {
                out.add(new net.md_5.bungee.api.chat.BaseComponent[] { new net.md_5.bungee.api.chat.TextComponent(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line)) });
            }
        }
        return out;
    }
    public void setLoreComponents(@Nullable java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> lore) {
        if (lore == null) { this.lore = null; return; }
        this.lore = new java.util.ArrayList<>();
        for (var line : lore) {
            try {
                this.lore.add(GsonComponentSerializer.gson().deserialize(net.md_5.bungee.chat.ComponentSerializer.toString(line)));
            } catch (Throwable ignored) {
                this.lore.add(LegacyComponentSerializer.legacySection().deserialize(net.md_5.bungee.api.chat.BaseComponent.toLegacyText(line)));
            }
        }
    }
    @Override
    public void addItemFlags(@NotNull org.bukkit.inventory.ItemFlag... itemFlags) {
        Objects.requireNonNull(itemFlags, "itemFlags");
        Collections.addAll(this.itemFlags, itemFlags);
    }
    @Override
    public void removeItemFlags(@NotNull org.bukkit.inventory.ItemFlag... itemFlags) {
        Objects.requireNonNull(itemFlags, "itemFlags");
        for (org.bukkit.inventory.ItemFlag flag : itemFlags) this.itemFlags.remove(flag);
    }
    @Override
    public @NotNull Set<org.bukkit.inventory.ItemFlag> getItemFlags() { return Collections.unmodifiableSet(EnumSet.copyOf(itemFlags)); }
    @Override
    public boolean hasItemFlag(@NotNull org.bukkit.inventory.ItemFlag flag) { return this.itemFlags.contains(Objects.requireNonNull(flag, "flag")); }
    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        return persistentDataContainer;
    }


    @Override
    public boolean hasDamage() { return damage != null && damage > 0; }

    @Override
    public int getDamage() { return damage != null ? damage : 0; }

    @Override
    public void setDamage(int damage) {
        com.google.common.base.Preconditions.checkArgument(damage >= 0, "Damage cannot be negative");
        com.google.common.base.Preconditions.checkArgument(!hasMaxDamage() || damage <= getMaxDamage(), "Damage cannot exceed max damage");
        this.damage = damage;
    }

    @Override
    public boolean hasDamageValue() { return damage != null; }

    @Override
    public void resetDamage() { damage = null; }

    @Override
    public boolean hasMaxDamage() { return maxDamage != null; }

    @Override
    public int getMaxDamage() {
        com.google.common.base.Preconditions.checkState(hasMaxDamage(), "We don't have max_damage! Check hasMaxDamage first!");
        return maxDamage;
    }

    @Override
    public void setMaxDamage(@Nullable Integer maxDamage) {
        com.google.common.base.Preconditions.checkArgument(maxDamage == null || maxDamage > 0, "Max damage should be positive");
        this.maxDamage = maxDamage;
    }


    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (hasDisplayName()) map.put("display-name", getDisplayName());
        if (hasItemName()) map.put("item-name", getItemName());
        if (hasLore()) map.put("lore", getLore());
        if (hasEnchants()) {
            Map<String, Integer> enchants = new LinkedHashMap<>();
            enchantments.forEach((enchantment, level) -> enchants.put(enchantment.getKey().toString(), level));
            map.put("enchants", enchants);
        }
        if (hasDamage()) map.put("damage", getDamage());
        if (hasMaxDamage()) map.put("max-damage", getMaxDamage());
        return map;
    }

    @Override
    public @NotNull CraftItemMeta clone() {
        try {
            CraftItemMeta clone = (CraftItemMeta) super.clone();
            clone.enchantments = new HashMap<>(enchantments);
            clone.attributeModifiers = attributeModifiers == null ? null : com.google.common.collect.LinkedHashMultimap.create(attributeModifiers);
            clone.canDestroy = new LinkedHashSet<>(canDestroy);
            clone.canPlaceOn = new LinkedHashSet<>(canPlaceOn);
            clone.destroyableKeys = new LinkedHashSet<>(destroyableKeys);
            clone.placeableKeys = new LinkedHashSet<>(placeableKeys);
            clone.itemFlags = itemFlags.clone();
            if (lore != null) clone.lore = new ArrayList<>(lore);
            clone.useRemainder = useRemainder == null ? null : useRemainder.clone();
            clone.rawDataComponents = new HashMap<>(rawDataComponents);
            clone.unhandledDataComponents = new LinkedHashMap<>(unhandledDataComponents);
            clone.tool = tool == null ? null : new CraftToolComponent((CraftToolComponent) tool);
            clone.food = food == null ? null : new CraftFoodComponent((CraftFoodComponent) food);
            clone.jukeboxPlayable = jukeboxPlayable == null ? null : new CraftJukeboxComponent((CraftJukeboxComponent) jukeboxPlayable);
            clone.equippable = equippable == null ? null : new org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent(equippable);
            clone.useCooldown = useCooldown == null ? null : new org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent(useCooldown);
            clone.customModelDataComponent = customModelDataComponent == null ? null : new org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent(customModelDataComponent.getHandle());
            clone.persistentDataContainer =
                    new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer(persistentDataContainer);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CraftItemMeta other)) return false;
        return Objects.equals(displayName, other.displayName)
            && Objects.equals(itemName, other.itemName)
            && Objects.equals(lore, other.lore)
            && Objects.equals(enchantments, other.enchantments)
            && unbreakable == other.unbreakable
            && hasCustomModelData == other.hasCustomModelData
            && customModelData == other.customModelData
            && Objects.equals(enchantmentGlintOverride, other.enchantmentGlintOverride)
            && Objects.equals(attributeModifiers, other.attributeModifiers)
            && Objects.equals(canDestroy, other.canDestroy)
            && Objects.equals(canPlaceOn, other.canPlaceOn)
            && Objects.equals(itemFlags, other.itemFlags)
            && Objects.equals(rarity, other.rarity)
            && Objects.equals(maxStackSize, other.maxStackSize)
            && hideTooltip == other.hideTooltip
            && fireResistant == other.fireResistant
            && glider == other.glider
            && Objects.equals(useRemainder, other.useRemainder)
            && Objects.equals(tooltipStyle, other.tooltipStyle)
            && Objects.equals(repairCost, other.repairCost)
            && Objects.equals(damage, other.damage)
            && Objects.equals(maxDamage, other.maxDamage)
            && Objects.equals(tool, other.tool)
            && Objects.equals(food, other.food)
            && Objects.equals(jukeboxPlayable, other.jukeboxPlayable)
            && Objects.equals(equippable, other.equippable)
            && Objects.equals(useCooldown, other.useCooldown)
            && Objects.equals(customModelDataComponent, other.customModelDataComponent)
            && Objects.equals(enchantableValue, other.enchantableValue)
            && Objects.equals(itemModel, other.itemModel)
            && Objects.equals(damageResistant, other.damageResistant)
            && Objects.equals(unhandledDataComponents, other.unhandledDataComponents)
            && Objects.equals(persistentDataContainer, other.persistentDataContainer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, itemName, lore, enchantments, unbreakable, hasCustomModelData, customModelData,
                enchantmentGlintOverride, attributeModifiers, canDestroy, canPlaceOn, itemFlags, rarity, maxStackSize,
                hideTooltip, fireResistant, glider, useRemainder, tooltipStyle, repairCost, damage, maxDamage, tool, food,
                jukeboxPlayable, equippable, useCooldown, customModelDataComponent, enchantableValue, itemModel, damageResistant,
                unhandledDataComponents, persistentDataContainer);
    }

    @Override public @Nullable Component customName() { return displayName(); }
    @Override public void customName(@Nullable Component customName) { displayName(customName); }

    @Override public boolean hasCustomModelDataComponent() { return customModelDataComponent != null || hasCustomModelData; }
    @Override public @NotNull org.bukkit.inventory.meta.components.CustomModelDataComponent getCustomModelDataComponent() {
        if (customModelDataComponent != null) return new org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent(customModelDataComponent.getHandle());
        net.minecraft.world.item.component.CustomModelData data = hasCustomModelData
                ? new net.minecraft.world.item.component.CustomModelData(java.util.List.of((float) customModelData), java.util.List.of(), java.util.List.of(), java.util.List.of())
                : new net.minecraft.world.item.component.CustomModelData(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        return new org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent(data);
    }
    @Override public void setCustomModelDataComponent(@Nullable org.bukkit.inventory.meta.components.CustomModelDataComponent component) {
        this.customModelDataComponent = component == null ? null : new org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent(((org.bukkit.craftbukkit.inventory.components.CraftCustomModelDataComponent) component).getHandle());
        if (component == null) { hasCustomModelData = false; customModelData = 0; return; }
        java.util.List<Float> floats = component.getFloats();
        hasCustomModelData = !floats.isEmpty();
        customModelData = floats.isEmpty() ? 0 : floats.get(0).intValue();
    }

    @Override public boolean hasEnchantable() { return enchantableValue != null; }
    @Override public int getEnchantable() {
        if (enchantableValue == null) throw new IllegalStateException("We don't have Enchantable! Check hasEnchantable first!");
        return enchantableValue;
    }
    @Override public void setEnchantable(@Nullable Integer data) {
        if (data != null && data <= 0) throw new IllegalArgumentException("Enchantability must be positive");
        this.enchantableValue = data;
    }

    @Override public boolean hasItemModel() { return itemModel != null; }
    @Override public @Nullable NamespacedKey getItemModel() { return itemModel; }
    @Override public void setItemModel(@Nullable NamespacedKey itemModel) { this.itemModel = itemModel; }

    @Override public boolean hasDamageResistant() { return damageResistant != null; }
    @Override public @Nullable org.bukkit.Tag<org.bukkit.damage.DamageType> getDamageResistant() {
        return damageResistant == null ? null : org.bukkit.Bukkit.getTag(org.bukkit.tag.DamageTypeTags.REGISTRY_DAMAGE_TYPES,
                org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(damageResistant.location()), org.bukkit.damage.DamageType.class);
    }
    @Override public void setDamageResistant(@Nullable org.bukkit.Tag<org.bukkit.damage.DamageType> tag) {
        this.damageResistant = tag == null ? null : net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(tag.getKey().getNamespace(), tag.getKey().getKey()));
    }

    @Override public boolean hasUseCooldown() { return useCooldown != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.UseCooldownComponent getUseCooldown() {
        return useCooldown != null ? new org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent(useCooldown)
                : new org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent(new net.minecraft.world.item.component.UseCooldown(1.0F));
    }
    @Override public void setUseCooldown(@Nullable org.bukkit.inventory.meta.components.UseCooldownComponent cooldown) {
        this.useCooldown = cooldown == null ? null : new org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent((org.bukkit.craftbukkit.inventory.components.CraftUseCooldownComponent) cooldown);
    }

    @Override public boolean hasEquippable() { return equippable != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.EquippableComponent getEquippable() {
        return equippable != null ? new org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent(equippable)
                : new org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent(net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD).build());
    }
    @Override public void setEquippable(@Nullable org.bukkit.inventory.meta.components.EquippableComponent component) {
        this.equippable = component == null ? null : new org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent((org.bukkit.craftbukkit.inventory.components.CraftEquippableComponent) component);
    }
}
