package org.bukkit.craftbukkit.inventory;

import net.minecraft.world.inventory.CartographyTableMenu;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CartographyInventory;

public final class CraftCartographyInventory extends CraftInventoryView.MenuSlotInventory implements CartographyInventory {
    public CraftCartographyInventory(CartographyTableMenu handle, HumanEntity owner) {
        super(handle, 3, InventoryType.CARTOGRAPHY, (org.bukkit.inventory.InventoryHolder) owner);
    }
}
