package io.lunararcdevs.lunararc.common.bridge.donor;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

public final class DonorContainerSupport {
    private static final MethodHandle NONE = MethodHandles.zero(InventoryHolder.class);

    private static final ClassValue<MethodHandle> DECLARED_OWNER = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                return MethodHandles.publicLookup()
                        .findVirtual(type, "getOwner", MethodType.methodType(InventoryHolder.class))
                        .asType(MethodType.methodType(InventoryHolder.class, Container.class));
            } catch (ReflectiveOperationException | RuntimeException absent) {
                return NONE;
            }
        }
    };

    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDER =
            new DoubleBlockCombiner.Combiner<>() {
                @Override
                public Optional<MenuProvider> acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
                    return Optional.of(new DonorDoubleInventory(first, second));
                }

                @Override
                public Optional<MenuProvider> acceptSingle(ChestBlockEntity single) {
                    return Optional.of(single);
                }

                @Override
                public Optional<MenuProvider> acceptNone() {
                    return Optional.empty();
                }
            };

    private DonorContainerSupport() {
    }

    public static @Nullable InventoryHolder ownerOf(Container container) {
        if (container == null) return null;
        MethodHandle declared = DECLARED_OWNER.get(container.getClass());
        if (declared != NONE) {
            try {
                return (InventoryHolder) declared.invokeExact(container);
            } catch (Throwable failure) {
                if (failure instanceof RuntimeException runtime) throw runtime;
                if (failure instanceof Error error) throw error;
                throw new IllegalStateException(failure);
            }
        }
        if (container instanceof CompoundContainer compound) {
            return doubleChestHolder(compound);
        }
        if (container instanceof BlockEntity blockEntity) return blockHolder(blockEntity);
        return null;
    }

    public static @Nullable MenuProvider chestMenuProvider(
            ChestBlock block, BlockState state, Level level, BlockPos pos, boolean ignoreBlocked) {
        return block.combine(state, level, pos, ignoreBlocked).apply(MENU_PROVIDER).orElse(null);
    }

    private static InventoryHolder doubleChestHolder(CompoundContainer compound) {
        try {
            return new DoubleChest((DoubleChestInventory) DoubleChestInventoryFactory.HANDLE.invoke(compound));
        } catch (Throwable failure) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }
    }

    private static final class DoubleChestInventoryFactory {
        static final MethodHandle HANDLE = lookup();

        private static MethodHandle lookup() {
            try {
                return MethodHandles.publicLookup().findConstructor(
                        CraftInventoryDoubleChest.class, MethodType.methodType(void.class, CompoundContainer.class));
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    private static @Nullable InventoryHolder blockHolder(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null) return null;
        org.bukkit.World world;
        try {
            world = LunarArcServerAccess.getCraftWorld(level);
        } catch (Throwable notReady) {
            return null;
        }
        if (world == null) return null;
        BlockPos pos = blockEntity.getBlockPos();
        return world.getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getState() instanceof InventoryHolder holder
                ? holder
                : null;
    }
}
