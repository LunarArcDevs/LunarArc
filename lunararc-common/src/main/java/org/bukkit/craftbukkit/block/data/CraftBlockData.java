package org.bukkit.craftbukkit.block.data;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class CraftBlockData implements BlockData {
    public static final BlockFace[] ROTATION_CYCLE = {
        BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
        BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
        BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    private BlockState state;
    private Set<String> parsedProperties;

    /**
     * Constructor retained for binary compatibility with CraftBukkit's generated
     * block-data implementations. Runtime instances should normally be created
     * with a concrete NMS state through {@link #createData(BlockState)}.
     */
    protected CraftBlockData() {
        this(Blocks.AIR.defaultBlockState(), null);
    }

    public CraftBlockData(BlockState state) {
        this(state, null);
    }

    protected CraftBlockData(BlockState state, Set<String> parsedProperties) {
        this.state = Objects.requireNonNull(state, "state");
        this.parsedProperties = parsedProperties;
    }

    public BlockState getState() {
        return state;
    }

    private static final Map<Class<? extends Block>, Map<String, Property<?>>> PROPERTY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Block>, Constructor<? extends CraftBlockData>> GENERATED_IMPLEMENTATIONS = new ConcurrentHashMap<>();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Object GENERATED_IMPLEMENTATION_LOCK = new Object();
    private static volatile boolean generatedImplementationsLoaded;

    private static final ClassValue<Enum<?>[]> ENUM_VALUES = new ClassValue<>() {
        @Override
        protected Enum<?>[] computeValue(Class<?> type) {
            return (Enum<?>[]) type.getEnumConstants();
        }
    };

    /** Binary-compatible CraftBukkit property getter used by generated block data. */
    protected <T extends Comparable<T>> T get(Property<T> property) {
        return this.state.getValue(Objects.requireNonNull(property, "property"));
    }

    /** Binary-compatible CraftBukkit property setter used by generated block data. */
    public <T extends Comparable<T>, V extends T> void set(Property<T> property, V value) {
        this.parsedProperties = null;
        this.state = this.state.setValue(Objects.requireNonNull(property, "property"), value);
    }

    @SuppressWarnings("unchecked")
    protected <A extends Enum<A>> A get(EnumProperty<?> property, Class<A> bukkitClass) {
        Enum<?> value = (Enum<?>) this.state.getValue((Property) property);
        return fromVanilla(value, bukkitClass);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <A extends Enum<A>> Set<A> getValues(EnumProperty<?> property, Class<A> bukkitClass) {
        LinkedHashSet<A> result = new LinkedHashSet<>();
        for (Object value : property.getPossibleValues()) {
            result.add(fromVanilla((Enum<?>) value, bukkitClass));
        }
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <A extends Enum<A>> void set(EnumProperty<?> property, A value) {
        this.parsedProperties = null;
        Enum<?> vanilla = toVanilla(value, (Class) property.getValueClass());
        this.state = this.state.setValue((Property) property, (Comparable) vanilla);
    }

    private static <A extends Enum<A>> A fromVanilla(Enum<?> vanilla, Class<A> bukkitClass) {
        if (vanilla instanceof Direction direction && bukkitClass == BlockFace.class) {
            return bukkitClass.cast(switch (direction) {
                case DOWN -> BlockFace.DOWN;
                case UP -> BlockFace.UP;
                case NORTH -> BlockFace.NORTH;
                case SOUTH -> BlockFace.SOUTH;
                case WEST -> BlockFace.WEST;
                case EAST -> BlockFace.EAST;
            });
        }
        Enum<?>[] constants = ENUM_VALUES.get(bukkitClass);
        if (vanilla.ordinal() < 0 || vanilla.ordinal() >= constants.length) {
            throw new IllegalArgumentException("Cannot convert " + vanilla + " to " + bukkitClass.getName());
        }
        return bukkitClass.cast(constants[vanilla.ordinal()]);
    }

    @SuppressWarnings("unchecked")
    public static <N extends Enum<N> & StringRepresentable> N toVanilla(Enum<?> bukkit, Class<N> vanillaClass) {
        if (bukkit instanceof BlockFace face && Direction.class.equals(vanillaClass)) {
            return (N) switch (face) {
                case DOWN -> Direction.DOWN;
                case UP -> Direction.UP;
                case NORTH -> Direction.NORTH;
                case SOUTH -> Direction.SOUTH;
                case WEST -> Direction.WEST;
                case EAST -> Direction.EAST;
                default -> throw new IllegalArgumentException("BlockFace " + face + " is not a vanilla Direction");
            };
        }
        Enum<?>[] constants = ENUM_VALUES.get(vanillaClass);
        if (bukkit.ordinal() < 0 || bukkit.ordinal() >= constants.length) {
            throw new IllegalArgumentException("Cannot convert " + bukkit + " to " + vanillaClass.getName());
        }
        return (N) constants[bukkit.ordinal()];
    }

    /** CraftBukkit 1.21.1 binary/source compatibility alias. */
    public static <N extends Enum<N> & StringRepresentable> N toNMS(Enum<?> bukkit, Class<N> vanillaClass) {
        return toVanilla(bukkit, vanillaClass);
    }

    // CraftBukkit generated-source template signatures. Generated concrete classes use
    // the class-aware overloads below; these no-class forms intentionally remain traps.
    protected static BooleanProperty getBoolean(String name) { throw new AssertionError("Template Method"); }
    protected static BooleanProperty getBoolean(String name, boolean optional) { throw new AssertionError("Template Method"); }
    protected static EnumProperty<?> getEnum(String name) { throw new AssertionError("Template Method"); }
    protected static IntegerProperty getInteger(String name) { throw new AssertionError("Template Method"); }

    protected static BooleanProperty getBoolean(Class<? extends Block> block, String name) {
        return (BooleanProperty) getStateProperty(block, name, false);
    }

    protected static BooleanProperty getBoolean(Class<? extends Block> block, String name, boolean optional) {
        return (BooleanProperty) getStateProperty(block, name, optional);
    }

    protected static EnumProperty<?> getEnum(Class<? extends Block> block, String name) {
        return (EnumProperty<?>) getStateProperty(block, name, false);
    }

    protected static IntegerProperty getInteger(Class<? extends Block> block, String name) {
        return (IntegerProperty) getStateProperty(block, name, false);
    }

    private static Property<?> getStateProperty(Class<? extends Block> blockClass, String name, boolean optional) {
        Objects.requireNonNull(blockClass, "blockClass");
        Objects.requireNonNull(name, "name");
        registerGeneratedImplementation(blockClass);
        Map<String, Property<?>> properties = PROPERTY_CACHE.computeIfAbsent(blockClass, ignored -> {
            Map<String, Property<?>> found = new java.util.LinkedHashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.getClass() != blockClass) continue;
                for (Property<?> property : block.getStateDefinition().getProperties()) {
                    Property<?> previous = found.putIfAbsent(property.getName(), property);
                    if (previous != null && previous != property) {
                        throw new IllegalStateException("State mismatch for " + blockClass.getName() + ":" + property.getName());
                    }
                }
            }
            return Map.copyOf(found);
        });
        Property<?> property = properties.get(name);
        if (!optional && property == null) {
            throw new IllegalStateException("Null state for " + blockClass.getName() + "," + name);
        }
        return property;
    }

    @SuppressWarnings("unchecked")
    private static void registerGeneratedImplementation(Class<? extends Block> blockClass) {
        Class<?> generated = STACK_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type.getName().startsWith("org.bukkit.craftbukkit.block.impl.Craft"))
                .filter(CraftBlockData.class::isAssignableFrom)
                .findFirst()
                .orElse(null));
        if (generated == null || generated == CraftBlockData.class) return;

        try {
            Constructor<? extends CraftBlockData> constructor =
                    ((Class<? extends CraftBlockData>) generated).getConstructor(BlockState.class);
            Constructor<? extends CraftBlockData> previous = GENERATED_IMPLEMENTATIONS.putIfAbsent(blockClass, constructor);
            if (previous != null && previous.getDeclaringClass() != generated) {
                throw new IllegalStateException("Multiple CraftBlockData implementations for " + blockClass.getName()
                        + ": " + previous.getDeclaringClass().getName() + " and " + generated.getName());
            }
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("Generated CraftBlockData implementation has no BlockState constructor: "
                    + generated.getName(), ex);
        }
    }

    /**
     * Paper 1.21.11's generated classes read the block's static properties directly instead of calling getEnum/getBoolean,
     * so nothing registers them. Work out the block class from the fields their static initializer reads (the most derived
     * owner), falling back to the Craft&lt;Name&gt; to &lt;Name&gt;Block naming.
     */
    @SuppressWarnings("unchecked")
    private static void registerByBytecode(Class<?> generated, ClassLoader loader) {
        if (!CraftBlockData.class.isAssignableFrom(generated) || generated == CraftBlockData.class) return;
        Constructor<? extends CraftBlockData> constructor;
        try {
            constructor = ((Class<? extends CraftBlockData>) generated).getConstructor(BlockState.class);
        } catch (NoSuchMethodException ex) {
            return;
        }
        Class<? extends Block> blockClass = null;
        try {
            // Craft<Name> is the implementation of <Name>Block: exact for the subclass blocks (PlayerHeadBlock, WitherSkullBlock) that
            // share their static properties with a parent and would otherwise all resolve to that parent
            Class<?> guess = Class.forName("net.minecraft.world.level.block." + generated.getSimpleName().substring("Craft".length()) + "Block", false, loader);
            if (Block.class.isAssignableFrom(guess)) blockClass = (Class<? extends Block>) guess;
        } catch (ClassNotFoundException | LinkageError ex) {
            // no block of that name: read the block class from the static initializer below
        }
        if (blockClass == null) try (InputStream input = loader.getResourceAsStream(generated.getName().replace('.', '/') + ".class")) {
            if (input != null) {
                java.util.Set<String> owners = new java.util.LinkedHashSet<>();
                new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                        if (!name.equals("<clinit>")) return null;
                        return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                                if (opcode == org.objectweb.asm.Opcodes.GETSTATIC && owner.startsWith("net/minecraft/world/level/block/")) owners.add(owner);
                            }
                        };
                    }
                }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
                for (String owner : owners) {
                    Class<?> candidate;
                    try {
                        candidate = Class.forName(owner.replace('/', '.'), false, loader);
                    } catch (ClassNotFoundException | LinkageError ex) {
                        continue;
                    }
                    if (!Block.class.isAssignableFrom(candidate)) continue;
                    if (blockClass == null || blockClass.isAssignableFrom(candidate)) blockClass = (Class<? extends Block>) candidate;
                }
            }
        } catch (IOException ex) {
            // no readable class file: the block stays unmapped
        }
        if (blockClass != null) GENERATED_IMPLEMENTATIONS.putIfAbsent(blockClass, constructor);
    }

    private static void ensureGeneratedImplementationsLoaded() {
        if (generatedImplementationsLoaded) return;
        synchronized (GENERATED_IMPLEMENTATION_LOCK) {
            if (generatedImplementationsLoaded) return;
            ClassLoader loader = CraftBlockData.class.getClassLoader();
            String resourceName = "META-INF/lunararc/paper-block-impl.list";
            try (InputStream input = loader.getResourceAsStream(resourceName)) {
                if (input == null) {
                    // Development/source-only runs may intentionally omit the exact Paper donor.
                    generatedImplementationsLoaded = true;
                    return;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String className = line.trim();
                        if (className.isEmpty() || className.startsWith("#")) continue;
                        try {
                            Class<?> generated = Class.forName(className, true, loader);
                            registerByBytecode(generated, loader);
                        } catch (ClassNotFoundException ex) {
                            throw new IllegalStateException("Missing generated Paper 1.21.11 CraftBlockData class " + className, ex);
                        } catch (LinkageError error) {
                            throw new IllegalStateException("Could not link generated Paper 1.21.11 CraftBlockData class " + className, error);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read " + resourceName, ex);
            }
            generatedImplementationsLoaded = true;
        }
    }

    private static CraftBlockData createConcreteData(BlockState state) {
        ensureGeneratedImplementationsLoaded();
        // modded blocks usually extend a vanilla class, so walk up to the nearest block class that has an implementation
        Constructor<? extends CraftBlockData> constructor = null;
        for (Class<?> type = state.getBlock().getClass(); type != null && type != Object.class && constructor == null; type = type.getSuperclass()) {
            constructor = GENERATED_IMPLEMENTATIONS.get(type);
        }
        if (constructor == null) return new CraftBlockData(state);
        try {
            return constructor.newInstance(state);
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new IllegalStateException("Could not construct " + constructor.getDeclaringClass().getName(), ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Generated CraftBlockData constructor failed: "
                    + constructor.getDeclaringClass().getName(), cause);
        }
    }

    protected static int getMin(IntegerProperty property) {
        return property.getPossibleValues().stream().min(Integer::compareTo)
                .orElseThrow(() -> new IllegalStateException("Integer property has no values: " + property.getName()));
    }

    protected static int getMax(IntegerProperty property) {
        return property.getPossibleValues().stream().max(Integer::compareTo)
                .orElseThrow(() -> new IllegalStateException("Integer property has no values: " + property.getName()));
    }

    public static CraftBlockData fromState(BlockState state) {
        return createConcreteData(Objects.requireNonNull(state, "state"));
    }

    public static CraftBlockData create(BlockState state) {
        return createConcreteData(Objects.requireNonNull(state, "state"));
    }

    public static CraftBlockData createData(BlockState state) {
        return createConcreteData(Objects.requireNonNull(state, "state"));
    }

    public static CraftBlockData fromData(BlockState state) {
        return createConcreteData(Objects.requireNonNull(state, "state"));
    }

    public static CraftBlockData parse(String input) {
        Objects.requireNonNull(input, "input");
        String value = input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Block data cannot be empty");

        int open = value.indexOf('[');
        String keyText;
        String propertiesText = null;
        if (open >= 0) {
            if (!value.endsWith("]") || value.indexOf('[', open + 1) >= 0) {
                throw new IllegalArgumentException("Invalid block data: " + input);
            }
            keyText = value.substring(0, open).trim();
            propertiesText = value.substring(open + 1, value.length() - 1).trim();
        } else {
            keyText = value;
        }
        if (keyText.isEmpty()) throw new IllegalArgumentException("Missing block name in " + input);

        Identifier key;
        try {
            key = keyText.indexOf(':') >= 0 ? Identifier.parse(keyText) : Identifier.withDefaultNamespace(keyText);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid block name: " + keyText, ex);
        }
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalArgumentException("Unknown block: " + key);
        }

        net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.getValue(key);
        BlockState state = block.defaultBlockState();
        Set<String> parsed = new LinkedHashSet<>();
        if (propertiesText != null && !propertiesText.isEmpty()) {
            for (String token : propertiesText.split(",", -1)) {
                int equals = token.indexOf('=');
                if (equals <= 0 || equals == token.length() - 1 || token.indexOf('=', equals + 1) >= 0) {
                    throw new IllegalArgumentException("Invalid block property '" + token + "' in " + input);
                }
                String name = token.substring(0, equals).trim();
                String rawValue = token.substring(equals + 1).trim();
                if (!parsed.add(name)) throw new IllegalArgumentException("Duplicate block property '" + name + "'");
                Property<?> property = block.getStateDefinition().getProperty(name);
                if (property == null) throw new IllegalArgumentException("Block " + key + " has no property '" + name + "'");
                state = setPropertyValue(state, property, rawValue, key);
            }
        }
        CraftBlockData data = createConcreteData(state);
        data.parsedProperties = parsed;
        return data;
    }

    public static CraftBlockData parse(Material material, String properties) {
        Objects.requireNonNull(material, "material");
        if (material.isLegacy()) {
            material = org.bukkit.craftbukkit.legacy.CraftLegacy.fromLegacy(material);
        }
        if (material == null || !material.isBlock()) throw new IllegalArgumentException("Material is not a block: " + material);
        String key = material.getKey().toString();
        if (properties == null || properties.isBlank()) return parse(key);
        String trimmed = properties.trim();
        if (trimmed.startsWith("[")) return parse(key + trimmed);
        if (trimmed.indexOf(':') >= 0 || trimmed.indexOf('[') > 0) {
            CraftBlockData parsed = parse(trimmed);
            if (parsed.state.getBlock() != blockForMaterial(material)) {
                throw new IllegalArgumentException("Block data does not match material " + material + ": " + properties);
            }
            return parsed;
        }
        return parse(key + "[" + trimmed + "]");
    }

    private static net.minecraft.world.level.block.Block blockForMaterial(Material material) {
        Identifier key = Identifier.fromNamespaceAndPath(material.getKey().getNamespace(), material.getKey().getKey());
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalArgumentException("Material is not a registered block: " + material);
        return BuiltInRegistries.BLOCK.getValue(key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setPropertyValue(BlockState state, Property property, String rawValue, Identifier key) {
        java.util.Optional value = property.getValue(rawValue);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Invalid value '" + rawValue + "' for property '" + property.getName() + "' on " + key);
        }
        return state.setValue(property, (Comparable) value.get());
    }

    @Override
    public @NotNull Material getMaterial() {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(state.getBlock());
    }

    @Override
    public @NotNull String getAsString() {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) throw new IllegalStateException("NMS block is not registered: " + state.getBlock());
        String base = key.toString();
        net.minecraft.world.level.block.state.StateDefinition<net.minecraft.world.level.block.Block,
                net.minecraft.world.level.block.state.BlockState> def = state.getBlock().getStateDefinition();
        if (def.getProperties().isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base).append('[');
        boolean first = true;
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : def.getProperties()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(prop.getName()).append('=').append(getPropertyValueString(state, prop));
        }
        return sb.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueString(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    @Override
    public @NotNull String getAsString(boolean hideUnspecified) {
        if (!hideUnspecified || parsedProperties == null) return getAsString();
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) throw new IllegalStateException("NMS block is not registered: " + state.getBlock());
        if (parsedProperties.isEmpty()) return key.toString();
        StringBuilder out = new StringBuilder(key.toString()).append('[');
        boolean first = true;
        for (String name : parsedProperties) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null) continue;
            if (!first) out.append(',');
            first = false;
            out.append(name).append('=').append(getPropertyValueStringUnchecked(state, property));
        }
        return out.append(']').toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getPropertyValueStringUnchecked(BlockState state, Property property) {
        Comparable value = state.getValue(property);
        return property.getName(value);
    }

    @Override
    public boolean matches(@Nullable BlockData data) {
        if (!(data instanceof CraftBlockData other)) return false;
        if (this.state.getBlock() != other.state.getBlock()) return false;
        if (this.state.equals(other.state)) return true;
        if (other.parsedProperties == null) return false;
        for (String name : other.parsedProperties) {
            Property<?> property = this.state.getBlock().getStateDefinition().getProperty(name);
            if (property == null || !propertyEquals(this.state, other.state, property)) return false;
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean propertyEquals(BlockState first, BlockState second, Property property) {
        return Objects.equals(first.getValue(property), second.getValue(property));
    }

    @Override
    public float getDestroySpeed(@NotNull org.bukkit.inventory.ItemStack itemStack, boolean considerEnchants) {
        java.util.Objects.requireNonNull(itemStack, "itemStack");
        net.minecraft.world.item.ItemStack nms = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack);
        return nms.isEmpty() ? 1.0F : nms.getDestroySpeed(this.state);
    }

    @Override
    public @NotNull org.bukkit.block.BlockState createBlockState() {
        // Paper/CraftBukkit 1.21.1 returns the specialized unplaced block-state
        // implementation for tile/entity-backed block types rather than a generic state.
        return org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(this.state, null);
    }

    @Override
    public void copyTo(@NotNull BlockData blockData) {
        java.util.Objects.requireNonNull(blockData, "blockData");
        if (!(blockData instanceof CraftBlockData target)) {
            throw new IllegalArgumentException("BlockData must be backed by LunarArc CraftBlockData");
        }
        if (this.state.getBlock() != target.state.getBlock()) return;
        BlockState copied = target.state;
        for (net.minecraft.world.level.block.state.properties.Property<?> property : this.state.getProperties()) {
            copied = copyProperty(this.state, copied, property);
        }
        target.state = copied;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperty(BlockState source, BlockState target,
            net.minecraft.world.level.block.state.properties.Property property) {
        if (!target.hasProperty(property)) return target;
        return target.setValue(property, source.getValue(property));
    }

    @Override
    public @NotNull org.bukkit.Material getPlacementMaterial() {
        return getMaterial();
    }

    @Override
    public @NotNull org.bukkit.util.VoxelShape getCollisionShape(@NotNull org.bukkit.Location location) {
        java.util.Objects.requireNonNull(location, "location");
        if (!(location.getWorld() instanceof org.bukkit.craftbukkit.CraftWorld craftWorld)) {
            throw new IllegalArgumentException("Location must belong to a LunarArc CraftWorld");
        }
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        net.minecraft.world.phys.shapes.VoxelShape nmsShape = this.state.getCollisionShape(craftWorld.getHandle(), pos);
        java.util.List<org.bukkit.util.BoundingBox> boxes = new java.util.ArrayList<>();
        for (net.minecraft.world.phys.AABB box : nmsShape.toAabbs()) {
            boxes.add(new org.bukkit.util.BoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        java.util.List<org.bukkit.util.BoundingBox> immutable = java.util.List.copyOf(boxes);
        return new org.bukkit.util.VoxelShape() {
            @Override public @NotNull java.util.Collection<org.bukkit.util.BoundingBox> getBoundingBoxes() { return immutable; }
            @Override public boolean overlaps(@NotNull org.bukkit.util.BoundingBox other) {
                java.util.Objects.requireNonNull(other, "other");
                for (org.bukkit.util.BoundingBox box : immutable) if (box.overlaps(other)) return true;
                return false;
            }
        };
    }

    @Override
    public @NotNull org.bukkit.Color getMapColor() { return org.bukkit.Color.fromRGB(this.state.getMapColor(null, null).col); }

    @Override
    public boolean isFaceSturdy(@NotNull org.bukkit.block.BlockFace face, @NotNull org.bukkit.block.BlockSupport support) {
        java.util.Objects.requireNonNull(face, "face");
        java.util.Objects.requireNonNull(support, "support");
        net.minecraft.core.Direction direction = toDirection(face);
        if (direction == null) return false;
        net.minecraft.world.level.block.SupportType supportType = switch (support) {
            case FULL -> net.minecraft.world.level.block.SupportType.FULL;
            case CENTER -> net.minecraft.world.level.block.SupportType.CENTER;
            case RIGID -> net.minecraft.world.level.block.SupportType.RIGID;
        };
        return this.state.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                net.minecraft.core.BlockPos.ZERO, direction, supportType);
    }

    @Override
    public void mirror(@NotNull org.bukkit.block.structure.Mirror mirror) {
        java.util.Objects.requireNonNull(mirror, "mirror");
        net.minecraft.world.level.block.Mirror nms = switch (mirror) {
            case NONE -> net.minecraft.world.level.block.Mirror.NONE;
            case LEFT_RIGHT -> net.minecraft.world.level.block.Mirror.LEFT_RIGHT;
            case FRONT_BACK -> net.minecraft.world.level.block.Mirror.FRONT_BACK;
        };
        this.state = this.state.mirror(nms);
    }

    @Override
    public void rotate(@NotNull org.bukkit.block.structure.StructureRotation rotation) {
        java.util.Objects.requireNonNull(rotation, "rotation");
        net.minecraft.world.level.block.Rotation nms = switch (rotation) {
            case NONE -> net.minecraft.world.level.block.Rotation.NONE;
            case CLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
        };
        this.state = this.state.rotate(nms);
    }

    private static net.minecraft.core.Direction toDirection(org.bukkit.block.BlockFace face) {
        return switch (face) {
            case DOWN -> net.minecraft.core.Direction.DOWN;
            case UP -> net.minecraft.core.Direction.UP;
            case NORTH -> net.minecraft.core.Direction.NORTH;
            case SOUTH -> net.minecraft.core.Direction.SOUTH;
            case WEST -> net.minecraft.core.Direction.WEST;
            case EAST -> net.minecraft.core.Direction.EAST;
            default -> null;
        };
    }

    @Override
    public boolean isRandomlyTicked() {
        return state.isRandomlyTicking();
    }

    @Override
    public int getLightEmission() { return state.getLightEmission(); }

    @Override
    public @NotNull org.bukkit.block.PistonMoveReaction getPistonMoveReaction() {
        return switch (this.state.getPistonPushReaction()) {
            case NORMAL -> org.bukkit.block.PistonMoveReaction.MOVE;
            case DESTROY -> org.bukkit.block.PistonMoveReaction.BREAK;
            case BLOCK -> org.bukkit.block.PistonMoveReaction.BLOCK;
            case PUSH_ONLY -> org.bukkit.block.PistonMoveReaction.PUSH_ONLY;
            case IGNORE -> org.bukkit.block.PistonMoveReaction.IGNORE;
        };
    }

    @Override
    public @NotNull org.bukkit.SoundGroup getSoundGroup() {
        return new org.bukkit.craftbukkit.CraftSoundGroup(state.getSoundType());
    }

    @Override
    public boolean isOccluding() { return this.state.canOcclude(); }

    @Override
    public boolean isPreferredTool(@NotNull org.bukkit.inventory.ItemStack tool) {
        java.util.Objects.requireNonNull(tool, "tool");
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(tool).isCorrectToolForDrops(this.state);
    }

    @Override
    public boolean isSupported(@NotNull org.bukkit.Location location) {
        java.util.Objects.requireNonNull(location, "location");
        if (!(location.getWorld() instanceof org.bukkit.craftbukkit.CraftWorld craftWorld)) return false;
        return this.state.canSurvive(craftWorld.getHandle(),
                new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    @Override
    public boolean isSupported(@NotNull org.bukkit.block.Block block) {
        java.util.Objects.requireNonNull(block, "block");
        return isSupported(block.getLocation());
    }

    @Override
    public boolean requiresCorrectToolForDrops() { return this.state.requiresCorrectToolForDrops(); }

    @Override
    public @NotNull BlockData merge(@NotNull BlockData data) {
        Objects.requireNonNull(data, "data");
        if (!(data instanceof CraftBlockData other) || this.state.getBlock() != other.state.getBlock()) {
            throw new IllegalArgumentException("Cannot merge BlockData of different block types");
        }
        if (other.parsedProperties == null) throw new IllegalArgumentException("Block data not created via string parsing");
        CraftBlockData merged = this.clone();
        for (String name : other.parsedProperties) {
            Property<?> property = other.state.getBlock().getStateDefinition().getProperty(name);
            if (property != null) merged.state = copyProperty(other.state, merged.state, property);
        }
        merged.parsedProperties = null;
        return merged;
    }

    @Override
    public @NotNull CraftBlockData clone() {
        CraftBlockData copy = createConcreteData(state);
        copy.parsedProperties = parsedProperties == null ? null : new LinkedHashSet<>(parsedProperties);
        return copy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Map<String, String> toStates() {
        java.util.Map<String, String> states = new java.util.HashMap<>();

        for (java.util.Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> entry
                : this.state.getValues().entrySet()) {
            net.minecraft.world.level.block.state.properties.Property property = entry.getKey();
            states.put(property.getName(), property.getName(entry.getValue()));
        }

        return states;
    }

    @Override
    public boolean isReplaceable() {
        return this.state.canBeReplaced();
    }
}
