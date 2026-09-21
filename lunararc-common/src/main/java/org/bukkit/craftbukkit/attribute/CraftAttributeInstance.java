package org.bukkit.craftbukkit.attribute;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;


public class CraftAttributeInstance implements org.bukkit.attribute.AttributeInstance {

    private final AttributeInstance handle;
    private final Attribute attribute;

    public CraftAttributeInstance(AttributeInstance handle, Attribute attribute) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.attribute = Objects.requireNonNull(attribute, "attribute");
    }

    public AttributeInstance getHandle() {
        return handle;
    }

    @Override
    public @NotNull Attribute getAttribute() {
        return attribute;
    }

    @Override
    public double getBaseValue() {
        return handle.getBaseValue();
    }

    @Override
    public void setBaseValue(double value) {
        handle.setBaseValue(value);
    }

    @Override
    public double getValue() {
        return handle.getValue();
    }

    @Override
    public double getDefaultValue() {
        return handle.getAttribute().value().getDefaultValue();
    }

    @Override
    public @NotNull Collection<AttributeModifier> getModifiers() {
        Collection<AttributeModifier> result = new ArrayList<>();
        for (Object modifier : handle.getModifiers()) {
            AttributeModifier converted = fromNms(modifier);
            if (converted != null) result.add(converted);
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public void addModifier(@NotNull AttributeModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        Object nms = toMinecraft(modifier);
        invokeModifierMutation("addPermanentModifier", nms);
    }

    @Override
    public void removeModifier(@NotNull AttributeModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        removeModifier(modifier.getKey().getNamespace() + ":" + modifier.getKey().getKey());
    }

    @Override
    @SuppressWarnings("deprecation")
    public @Nullable AttributeModifier getModifier(@NotNull UUID id) {
        Objects.requireNonNull(id, "id");
        for (AttributeModifier modifier : getModifiers()) {
            if (id.equals(modifier.getUniqueId())) return modifier;
        }
        return null;
    }

    @Override
    public @Nullable AttributeModifier getModifier(@NotNull net.kyori.adventure.key.Key key) {
        Objects.requireNonNull(key, "key");
        for (AttributeModifier modifier : getModifiers()) {
            NamespacedKey modifierKey = modifier.getKey();
            if (modifierKey.getNamespace().equals(key.namespace()) && modifierKey.getKey().equals(key.value())) return modifier;
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeModifier(@NotNull UUID id) {
        Objects.requireNonNull(id, "id");
        AttributeModifier modifier = getModifier(id);
        if (modifier != null) removeModifier(modifier);
    }

    @Override
    public void addTransientModifier(@NotNull AttributeModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        Object nms = toMinecraft(modifier);
        invokeModifierMutation("addTransientModifier", nms);
    }

    @Override
    public void removeModifier(@NotNull net.kyori.adventure.key.Key key) {
        Objects.requireNonNull(key, "key");
        removeModifier(key.namespace() + ":" + key.value());
    }

    private void removeModifier(String key) {
        try {
            Object resourceLocation = resourceLocation(key);
            Method remove = findMethod(handle.getClass(), "removeModifier", resourceLocation.getClass());
            if (remove == null) throw new IllegalStateException("Paper 1.21.1 AttributeInstance#removeModifier(ResourceLocation) was not found");
            remove.invoke(handle, resourceLocation);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to remove Minecraft attribute modifier " + key, e);
        }
    }

    private void invokeModifierMutation(String name, Object modifier) {
        try {
            Method method = findMethod(handle.getClass(), name, modifier.getClass());
            if (method == null) throw new IllegalStateException("Paper 1.21.1 AttributeInstance#" + name + " was not found");
            method.invoke(handle, modifier);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to mutate Minecraft attribute modifiers", e);
        }
    }

    private static @Nullable Method findMethod(Class<?> owner, String name, Class<?> argument) {
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            if (method.getParameterTypes()[0].isAssignableFrom(argument)) return method;
        }
        return null;
    }

    public static Object toMinecraft(AttributeModifier modifier) {
        try {
            Class<?> nmsClass = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier");
            Class<?> operationClass = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
            Object operation = Enum.valueOf((Class) operationClass, switch (modifier.getOperation()) {
                case ADD_NUMBER -> "ADD_VALUE";
                case ADD_SCALAR -> "ADD_MULTIPLIED_BASE";
                case MULTIPLY_SCALAR_1 -> "ADD_MULTIPLIED_TOTAL";
            });
            Object key = resourceLocation(modifier.getKey().toString());
            for (Constructor<?> constructor : nmsClass.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 3 && params[0].isInstance(key) && params[1] == double.class && params[2].isInstance(operation)) {
                    return constructor.newInstance(key, modifier.getAmount(), operation);
                }
            }
            throw new NoSuchMethodException("AttributeModifier(ResourceLocation,double,Operation)");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to convert Bukkit AttributeModifier to Minecraft 1.21.1", e);
        }
    }

    private static @Nullable AttributeModifier fromNms(Object modifier) {
        try {
            Object id = modifier.getClass().getMethod("id").invoke(modifier);
            double amount = ((Number) modifier.getClass().getMethod("amount").invoke(modifier)).doubleValue();
            Object operation = modifier.getClass().getMethod("operation").invoke(modifier);
            NamespacedKey key = NamespacedKey.fromString(id.toString());
            if (key == null) return null;
            AttributeModifier.Operation bukkitOperation = switch (((Enum<?>) operation).name()) {
                case "ADD_VALUE" -> AttributeModifier.Operation.ADD_NUMBER;
                case "ADD_MULTIPLIED_BASE" -> AttributeModifier.Operation.ADD_SCALAR;
                case "ADD_MULTIPLIED_TOTAL" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                default -> throw new IllegalStateException("Unknown Minecraft attribute operation: " + operation);
            };
            return new AttributeModifier(key, amount, bukkitOperation, EquipmentSlotGroup.ANY);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to convert Minecraft 1.21.1 AttributeModifier", e);
        }
    }

    private static Object resourceLocation(String key) throws ReflectiveOperationException {
        Class<?> resourceLocation = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.forName("net.minecraft.resources.ResourceLocation");
        try {
            return resourceLocation.getMethod("parse", String.class).invoke(null, key);
        } catch (NoSuchMethodException ignored) {
            try {
                return resourceLocation.getMethod("tryParse", String.class).invoke(null, key);
            } catch (NoSuchMethodException ignoredAgain) {
                Constructor<?> constructor = resourceLocation.getDeclaredConstructor(String.class);
                constructor.setAccessible(true);
                return constructor.newInstance(key);
            }
        }
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeModifier convert(
            org.bukkit.attribute.AttributeModifier bukkit) {
        return new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                org.bukkit.craftbukkit.util.CraftNamespacedKey.toMinecraft(bukkit.getKey()), bukkit.getAmount(),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                        .values()[bukkit.getOperation().ordinal()]);
    }

    public static org.bukkit.attribute.AttributeModifier convert(
            net.minecraft.world.entity.ai.attributes.AttributeModifier nms,
            net.minecraft.world.entity.EquipmentSlotGroup slot) {
        return new org.bukkit.attribute.AttributeModifier(
                org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(nms.id()), nms.amount(),
                org.bukkit.attribute.AttributeModifier.Operation.values()[nms.operation().ordinal()],
                org.bukkit.craftbukkit.CraftEquipmentSlot.getSlot(slot));
    }
}
