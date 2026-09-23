package io.lunararcdevs.lunararc.common.bridge.donor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class DonorDoubleInventory implements MenuProvider {
    private final ChestBlockEntity first;
    private final ChestBlockEntity second;
    public final CompoundContainer inventorylargechest;

    public DonorDoubleInventory(@javax.annotation.Nonnull ChestBlockEntity first, @javax.annotation.Nonnull ChestBlockEntity second) {
        this.first = first;
        this.second = second;
        this.inventorylargechest = new CompoundContainer(first, second);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @javax.annotation.Nonnull Inventory inventory, @javax.annotation.Nonnull Player player) {
        if (!first.canOpen(player) || !second.canOpen(player)) return null;
        first.unpackLootTable(inventory.player);
        second.unpackLootTable(inventory.player);
        return ChestMenu.sixRows(containerId, inventory, inventorylargechest);
    }

    @Override
    public Component getDisplayName() {
        if (first.hasCustomName()) return first.getDisplayName();
        if (second.hasCustomName()) return second.getDisplayName();
        return Component.translatable("container.chestDouble");
    }
}
