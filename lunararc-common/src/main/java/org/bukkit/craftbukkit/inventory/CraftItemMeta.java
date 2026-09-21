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
                String json = net.minecraft.network.chat.Component.Serializer.toJson(customName,
                    net.minecraft.core.RegistryAccess.EMPTY);
                displayName = GsonComponentSerializer.gson().deserialize(json);
            } catch (Throwable t) {
                displayName = Component.text(customName.getString());
            }
        }

        net.minecraft.network.chat.Component nmsItemName = nms.get(DataComponents.ITEM_NAME);
        if (nmsItemName != null) {
            try {
                String json = net.minecraft.network.chat.Component.Serializer.toJson(nmsItemName, net.minecraft.core.RegistryAccess.EMPTY);
                itemName = GsonComponentSerializer.gson().deserialize(json);
            } catch (Throwable t) { itemName = Component.text(nmsItemName.getString()); }
        }

        ItemLore itemLore = nms.get(DataComponents.LORE);
        if (itemLore != null && !itemLore.lines().isEmpty()) {
            lore = new ArrayList<>();
            for (net.minecraft.network.chat.Component line : itemLore.lines()) {
                try {
                    String json = net.minecraft.network.chat.Component.Serializer.toJson(line,
                        net.minecraft.core.RegistryAccess.EMPTY);
                    lore.add(GsonComponentSerializer.gson().deserialize(json));
                } catch (Throwable t) {
                    lore.add(Component.text(line.getString()));
                }
            }
        }

        net.minecraft.world.item.component.Unbreakable u = nms.get(DataComponents.UNBREAKABLE);
        if (u != null) unbreakable = true;

        net.minecraft.world.item.component.CustomModelData cmd = nms.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            hasCustomModelData = true;
            customModelData = cmd.value();
        }

        net.minecraft.world.item.enchantment.ItemEnchantments enc = nms.get(DataComponents.ENCHANTMENTS);
        if (enc != null) {
            enc.entrySet().forEach(e -> {
                try {
                    net.minecraft.resources.ResourceLocation key =
                        e.getKey().unwrapKey().map(k -> k.location()).orElse(null);
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
        captureUnhandledComponents(nms);
        loadPersistentData(nms);
    }


    public void applyToNms(ItemStack nms) {
        if (nms == null || nms.isEmpty()) return;
        applyUnhandledComponents(nms);

        if (displayName != null) {
            try {
                String json = GsonComponentSerializer.gson().serialize(displayName);
                net.minecraft.network.chat.Component nmsComp =
                    net.minecraft.network.chat.Component.Serializer.fromJson(json,
                        net.minecraft.core.RegistryAccess.EMPTY);
                nms.set(DataComponents.CUSTOM_NAME, nmsComp);
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
                String json = GsonComponentSerializer.gson().serialize(itemName);
                nms.set(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.Serializer.fromJson(json, net.minecraft.core.RegistryAccess.EMPTY));
            } catch (Throwable t) {
                nms.set(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.literal(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(itemName)));
            }
        } else nms.remove(DataComponents.ITEM_NAME);

        if (lore != null && !lore.isEmpty()) {
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
            for (Component line : lore) {
                try {
                    String json = GsonComponentSerializer.gson().serialize(line);
                    lines.add(net.minecraft.network.chat.Component.Serializer.fromJson(json,
                        net.minecraft.core.RegistryAccess.EMPTY));
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
            nms.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
        } else {
            nms.remove(DataComponents.UNBREAKABLE);
        }

        if (hasCustomModelData) {
            nms.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(customModelData));
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
        applyPersistentData(nms);
    }


    private static final Set<net.minecraft.core.component.DataComponentType<?>> HANDLED_COMPONENT_TYPES = buildHandledComponentTypes();

    private static Set<net.minecraft.core.component.DataComponentType<?>> buildHandledComponentTypes() {
        Set<net.minecraft.core.component.DataComponentType<?>> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String name : new String[]{
                "CUSTOM_NAME", "ITEM_NAME", "LORE", "UNBREAKABLE", "CUSTOM_MODEL_DATA",
                "ENCHANTMENTS", "ENCHANTMENT_GLINT_OVERRIDE", "MAX_STACK_SIZE", "REPAIR_COST",
                "ATTRIBUTE_MODIFIERS", "TOOL", "FOOD", "JUKEBOX_PLAYABLE", "USE_REMAINDER",
                "TOOLTIP_STYLE", "GLIDER", "FIRE_RESISTANT", "HIDE_TOOLTIP", "RARITY", "CUSTOM_DATA",
                "DAMAGE", "MAX_DAMAGE", "WRITABLE_BOOK_CONTENT", "WRITTEN_BOOK_CONTENT", "FIREWORKS", "FIREWORK_EXPLOSION", "PROFILE", "NOTE_BLOCK_SOUND", "TRIM", "BANNER_PATTERNS"
        }) {
            Object type = componentType(name);
            if (type instanceof net.minecraft.core.component.DataComponentType<?> dct) handled.add(dct);
        }
        return handled;
    }

    private void captureUnhandledComponents(ItemStack nms) {
        try {
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry : nms.getComponentsPatch().entrySet()) {
                if (!HANDLED_COMPONENT_TYPES.contains(entry.getKey())) {
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
            Class<?> itemEnchantments = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.item.enchantment.ItemEnchantments");
            Object empty = itemEnchantments.getField("EMPTY").get(null);
            Class<?> mutableClass = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.item.enchantment.ItemEnchantments$Mutable");
            Object mutable = mutableClass.getConstructor(itemEnchantments).newInstance(empty);

            Object minecraftServer = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer();
            Object registryAccess = minecraftServer.getClass().getMethod("registryAccess").invoke(minecraftServer);
            Object registry = registryAccess.getClass().getMethod("registryOrThrow", net.minecraft.resources.ResourceKey.class)
                    .invoke(registryAccess, net.minecraft.core.registries.Registries.ENCHANTMENT);

            java.lang.reflect.Method set = null;
            for (java.lang.reflect.Method method : mutableClass.getMethods()) {
                if (method.getName().equals("set") && method.getParameterCount() == 2 && method.getParameterTypes()[1] == int.class) {
                    set = method;
                    break;
                }
            }
            if (set == null) return;

            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                NamespacedKey key = entry.getKey().getKey();
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(key.toString());
                net.minecraft.resources.ResourceKey<?> resourceKey =
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, id);
                Object optional = registry.getClass().getMethod("getHolder", net.minecraft.resources.ResourceKey.class)
                        .invoke(registry, resourceKey);
                Object holder = optional instanceof java.util.Optional<?> value ? value.orElse(null) : null;
                if (holder != null) set.invoke(mutable, holder, entry.getValue());
            }

            Object immutable = mutableClass.getMethod("toImmutable").invoke(mutable);
            setComponent(nms, "ENCHANTMENTS", immutable);
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
            Class<?> modifiersClass = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.item.component.ItemAttributeModifiers");
            Object builder = modifiersClass.getMethod("builder").invoke(null);

            Object minecraftServer = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer();
            Object registryAccess = minecraftServer.getClass().getMethod("registryAccess").invoke(minecraftServer);
            Object registry = registryAccess.getClass().getMethod("registryOrThrow", net.minecraft.resources.ResourceKey.class)
                    .invoke(registryAccess, net.minecraft.core.registries.Registries.ATTRIBUTE);

            java.lang.reflect.Method add = null;
            for (java.lang.reflect.Method method : builder.getClass().getMethods()) {
                if (method.getName().equals("add") && method.getParameterCount() == 3) {
                    add = method;
                    break;
                }
            }
            if (add == null) return;

            for (Map.Entry<Attribute, AttributeModifier> entry : attributeModifiers.entries()) {
                NamespacedKey key = entry.getKey().getKey();
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(key.toString());
                net.minecraft.resources.ResourceKey<?> resourceKey =
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ATTRIBUTE, id);
                Object optional = registry.getClass().getMethod("getHolder", net.minecraft.resources.ResourceKey.class)
                        .invoke(registry, resourceKey);
                Object holder = optional instanceof java.util.Optional<?> value ? value.orElse(null) : null;
                if (holder == null) continue;

                Object nmsModifier = org.bukkit.craftbukkit.attribute.CraftAttributeInstance.toMinecraft(entry.getValue());
                Object nmsSlotGroup = toMinecraftSlotGroup(entry.getValue().getSlotGroup());
                add.invoke(builder, holder, nmsModifier, nmsSlotGroup);
            }
            Object built = builder.getClass().getMethod("build").invoke(builder);
            try {
                built = built.getClass().getMethod("withTooltip", boolean.class)
                        .invoke(built, !hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES));
            } catch (ReflectiveOperationException ignored) {}
            setComponent(nms, "ATTRIBUTE_MODIFIERS", built);
        } catch (Throwable ignored) {
            Object existing = rawDataComponents.get("ATTRIBUTE_MODIFIERS");
            if (existing != null) setComponent(nms, "ATTRIBUTE_MODIFIERS", existing);
        }
    }

    private static Object toMinecraftSlotGroup(EquipmentSlotGroup group) throws ReflectiveOperationException {
        Class<?> nmsGroup = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.entity.EquipmentSlotGroup");
        String requested = group == null ? "ANY" : group.toString().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        try {
            return nmsGroup.getField(requested).get(null);
        } catch (NoSuchFieldException ignored) {
            return nmsGroup.getField("ANY").get(null);
        }
    }

    private static Object componentType(String field) {
        try { return DataComponents.class.getField(field).get(null); } catch (Throwable ignored) { return null; }
    }
    private static Object getComponent(ItemStack stack, String field) {
        Object type = componentType(field); if (type == null) return null;
        try {
            for (java.lang.reflect.Method m : stack.getClass().getMethods()) {
                if (!m.getName().equals("get") || m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0].isInstance(type)) return m.invoke(stack, type);
            }
        } catch (Throwable ignored) {}
        return null;
    }
    private static void setComponent(ItemStack stack, String field, Object value) {
        Object type = componentType(field); if (type == null) return;
        try {
            if (value == null) {
                for (java.lang.reflect.Method m : stack.getClass().getMethods()) if (m.getName().equals("remove") && m.getParameterCount()==1 && m.getParameterTypes()[0].isInstance(type)) { m.invoke(stack,type); return; }
            } else {
                for (java.lang.reflect.Method m : stack.getClass().getMethods()) if (m.getName().equals("set") && m.getParameterCount()==2 && m.getParameterTypes()[0].isInstance(type)) { m.invoke(stack,type,value); return; }
            }
        } catch (Throwable ignored) {}
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
        if (style instanceof net.minecraft.resources.ResourceLocation rl) tooltipStyle = NamespacedKey.fromString(rl.toString());
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
        setComponent(nms, "TOOLTIP_STYLE", tooltipStyle == null ? null : net.minecraft.resources.ResourceLocation.parse(tooltipStyle.toString()));
        writeUnitFlag(nms, "GLIDER", glider); writeUnitFlag(nms, "FIRE_RESISTANT", fireResistant); writeUnitFlag(nms, "HIDE_TOOLTIP", hideTooltip);
        if (rarity != null) {
            try { setComponent(nms, "RARITY", net.minecraft.world.item.Rarity.valueOf(rarity.name())); } catch (Throwable ignored) {}
        }
    }
    private void writeUnitFlag(ItemStack nms, String name, boolean enabled) {
        if (!enabled) { setComponent(nms, name, null); return; }
        Object existing = rawDataComponents.get(name); if (existing != null) { setComponent(nms,name,existing); return; }
        try {
            Class<?> unit = Class.forName("com.mojang.datafixers.util.Unit");
            Object instance = unit.getField("INSTANCE").get(null); setComponent(nms,name,instance);
        } catch (Throwable ignored) {}
    }

    private void loadPersistentData(ItemStack nms) {
        try {
            Object customData = nms.get(DataComponents.CUSTOM_DATA);
            if (customData == null) return;
            Object copied = customData.getClass().getMethod("copyTag").invoke(customData);
            if (!(copied instanceof net.minecraft.nbt.CompoundTag root)) return;
            net.minecraft.nbt.Tag values = root.get("PublicBukkitValues");
            if (values instanceof net.minecraft.nbt.CompoundTag compound) {
                persistentDataContainer.fromTag(compound);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void applyPersistentData(ItemStack nms) {
        try {
            net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
            Object current = nms.get(DataComponents.CUSTOM_DATA);
            if (current != null) {
                try {
                    Object copied = current.getClass().getMethod("copyTag").invoke(current);
                    if (copied instanceof net.minecraft.nbt.CompoundTag compound) root = compound;
                } catch (ReflectiveOperationException ignored) {
                }
            }

            if (persistentDataContainer.isEmpty()) root.remove("PublicBukkitValues");
            else root.put("PublicBukkitValues", persistentDataContainer.toTag());

            Class<?> customDataClass = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.item.component.CustomData");
            Object replacement = customDataClass.getMethod("of", net.minecraft.nbt.CompoundTag.class).invoke(null, root);

            for (java.lang.reflect.Method method : nms.getClass().getMethods()) {
                if (!method.getName().equals("set") || method.getParameterCount() != 2) continue;
                if (!method.getParameterTypes()[0].isInstance(DataComponents.CUSTOM_DATA)) continue;
                method.invoke(nms, DataComponents.CUSTOM_DATA, replacement);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
        }
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
                    registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE);
            StringJoiner out = new StringJoiner(",", "[", "]");
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                String key = componentRegistry.getResourceKey(entry.getKey()).orElseThrow().location().toString();
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

        return new CraftToolComponent(new net.minecraft.world.item.component.Tool(java.util.List.of(), 1.0f, 0));
    }
    @Override public void setTool(@Nullable org.bukkit.inventory.meta.components.ToolComponent value) {
        this.tool = value == null ? null : new CraftToolComponent(value);
        rawDataComponents.remove("TOOL");
    }
    @Override public boolean hasJukeboxPlayable() { return jukeboxPlayable != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.JukeboxPlayableComponent getJukeboxPlayable() {
        if (jukeboxPlayable instanceof CraftJukeboxComponent c) return new CraftJukeboxComponent(c);
        return new CraftJukeboxComponent(new net.minecraft.world.item.JukeboxPlayable(
                new net.minecraft.world.item.EitherHolder<>(net.minecraft.world.item.JukeboxSongs.THIRTEEN), true));
    }
    @Override public void setJukeboxPlayable(@Nullable org.bukkit.inventory.meta.components.JukeboxPlayableComponent value) {
        this.jukeboxPlayable = value == null ? null : new CraftJukeboxComponent(value);
        rawDataComponents.remove("JUKEBOX_PLAYABLE");
    }
    @Override public boolean hasFood() { return food != null; }
    @Override public @NotNull org.bukkit.inventory.meta.components.FoodComponent getFood() {
        if (food instanceof CraftFoodComponent c) return new CraftFoodComponent(c);
        return new CraftFoodComponent(new net.minecraft.world.food.FoodProperties(0, 0.0f, false, 1.6f, java.util.Optional.empty(), java.util.List.of()));
    }
    @Override public void setFood(@Nullable org.bukkit.inventory.meta.components.FoodComponent value) {
        this.food = value == null ? null : new CraftFoodComponent(value);
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
    public boolean isFireResistant() { return fireResistant; }
    public void setFireResistant(boolean fireResistant) { this.fireResistant = fireResistant; }
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
        if (hasEnchants()) map.put("enchants", new HashMap<>(enchantments));
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
            clone.tool = tool == null ? null : new CraftToolComponent(tool);
            clone.food = food == null ? null : new CraftFoodComponent(food);
            clone.jukeboxPlayable = jukeboxPlayable == null ? null : new CraftJukeboxComponent(jukeboxPlayable);
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
            && Objects.equals(unhandledDataComponents, other.unhandledDataComponents)
            && Objects.equals(persistentDataContainer, other.persistentDataContainer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, itemName, lore, enchantments, unbreakable, hasCustomModelData, customModelData,
                enchantmentGlintOverride, attributeModifiers, canDestroy, canPlaceOn, itemFlags, rarity, maxStackSize,
                hideTooltip, fireResistant, glider, useRemainder, tooltipStyle, repairCost, damage, maxDamage, tool, food,
                jukeboxPlayable, unhandledDataComponents, persistentDataContainer);
    }
}
