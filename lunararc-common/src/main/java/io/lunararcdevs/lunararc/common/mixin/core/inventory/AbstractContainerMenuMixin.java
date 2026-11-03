package io.lunararcdevs.lunararc.common.mixin.core.inventory;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge {

    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow @Final private Set<Slot> quickcraftSlots;
    @Shadow private int quickcraftType;
    @Shadow public abstract ItemStack getCarried();
    @Shadow public abstract void setCarried(ItemStack stack);
    @Shadow public abstract void sendAllDataToRemote();

    @Unique private Map<Integer, ItemStack> lunararc$dragBeforeSlots;
    @Unique private ItemStack lunararc$dragBeforeCursor = ItemStack.EMPTY;
    @Unique private boolean lunararc$capturingDrag;
    @Unique private int lunararc$dragType;
    @Unique private ServerPlayer lunararc$owner;
    @Unique private boolean lunararc$checkReachable = true;
    @Unique private org.bukkit.inventory.InventoryView lunararc$bukkitView;

    @Override public ServerPlayer lunararc$getOwner() { return this.lunararc$owner; }
    @Override public void lunararc$setOwner(ServerPlayer owner) { this.lunararc$owner = owner; }
    @Override public org.bukkit.inventory.InventoryView lunararc$getBukkitView() { return this.lunararc$bukkitView; }
    /** Paper compatibility: Paper's patched AbstractContainerMenu exposes its view under this name. */
    public org.bukkit.inventory.InventoryView getBukkitView() { return this.lunararc$bukkitView; }
    @Override public void lunararc$setBukkitView(org.bukkit.inventory.InventoryView view) { this.lunararc$bukkitView = view; }
    @Override public boolean lunararc$getCheckReachable() { return this.lunararc$checkReachable; }
    @Override public void lunararc$setCheckReachable(boolean checkReachable) { this.lunararc$checkReachable = checkReachable; }

    @Inject(method = "doClick", at = @At("HEAD"), require = 0)
    private void lunararc$captureQuickCraftBefore(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        this.lunararc$clearDragCapture();
        if (clickType != ClickType.QUICK_CRAFT || lunararc$quickcraftHeader(button) != 2) {
            return;
        }
        if (!(player instanceof ServerPlayer) || this.quickcraftSlots == null || this.quickcraftSlots.isEmpty()) {
            return;
        }

        Map<Integer, ItemStack> before = new LinkedHashMap<>();
        for (Slot slot : this.quickcraftSlots) {
            int rawSlot = this.slots.indexOf(slot);
            if (rawSlot >= 0) {
                before.put(rawSlot, slot.getItem().copy());
            }
        }
        if (before.isEmpty()) {
            return;
        }

        this.lunararc$dragBeforeSlots = before;
        this.lunararc$dragBeforeCursor = this.getCarried().copy();
        this.lunararc$dragType = this.quickcraftType;
        this.lunararc$capturingDrag = true;
    }

    @Inject(method = "doClick", at = @At("RETURN"), require = 0)
    private void lunararc$finishQuickCraft(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!this.lunararc$capturingDrag || clickType != ClickType.QUICK_CRAFT || !(player instanceof ServerPlayer serverPlayer)) {
            this.lunararc$clearDragCapture();
            return;
        }

        Map<Integer, ItemStack> before = this.lunararc$dragBeforeSlots;
        ItemStack oldCursor = this.lunararc$dragBeforeCursor.copy();
        int dragType = this.lunararc$dragType;

        try {
            Map<Integer, ItemStack> calculated = new LinkedHashMap<>();
            Map<Integer, org.bukkit.inventory.ItemStack> eventSlots = new LinkedHashMap<>();
            for (Integer rawSlot : before.keySet()) {
                if (rawSlot < 0 || rawSlot >= this.slots.size()) {
                    continue;
                }
                ItemStack result = this.slots.get(rawSlot).getItem().copy();
                calculated.put(rawSlot, result);
                eventSlots.put(rawSlot, CraftItemStack.asBukkitCopy(result));
            }
            ItemStack calculatedCursor = this.getCarried().copy();

            Object bukkit = ((EntityBridge) serverPlayer).lunararc$getBukkitEntity();
            if (!(bukkit instanceof CraftPlayer craftPlayer)) {
                return;
            }

            // Present the event against the pre-drag inventory state, just as
            // CraftBukkit/Paper do before committing the calculated drag.
            lunararc$restoreSlots(before);
            this.setCarried(oldCursor.copy());

            org.bukkit.inventory.ItemStack newCursor = CraftItemStack.asBukkitCopy(calculatedCursor);
            org.bukkit.inventory.ItemStack oldBukkitCursor = CraftItemStack.asBukkitCopy(oldCursor);
            InventoryDragEvent event = new InventoryDragEvent(
                    craftPlayer.getOpenInventory(),
                    newCursor.getType().isAir() ? null : newCursor,
                    oldBukkitCursor,
                    dragType == 1,
                    eventSlots);
            LunarArcServerAccess.getCraftServer(serverPlayer.server).getPluginManager().callEvent(event);

            if (event.getResult() == Event.Result.DENY) {
                this.sendAllDataToRemote();
                return;
            }

            // Commit the result calculated by the real menu implementation.
            // This preserves modded slot validation/limits rather than
            // reimplementing QUICK_CRAFT in LunarArc.
            lunararc$restoreSlots(calculated);
            org.bukkit.inventory.ItemStack eventCursor = event.getCursor();
            this.setCarried(eventCursor == null ? ItemStack.EMPTY : CraftItemStack.asNMSCopy(eventCursor));

            if (event.getResult() != Event.Result.DEFAULT
                    || !lunararc$sameStack(calculatedCursor, this.getCarried())) {
                this.sendAllDataToRemote();
            }
        } finally {
            this.lunararc$clearDragCapture();
        }
    }

    @Unique
    private void lunararc$restoreSlots(Map<Integer, ItemStack> values) {
        for (Map.Entry<Integer, ItemStack> entry : values.entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot >= 0 && rawSlot < this.slots.size()) {
                this.slots.get(rawSlot).setByPlayer(entry.getValue().copy());
            }
        }
    }

    @Unique
    private static int lunararc$quickcraftHeader(int button) {
        return button & 3;
    }

    @Unique
    private static boolean lunararc$sameStack(ItemStack first, ItemStack second) {
        if (first.getCount() != second.getCount()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(first, second);
    }

    @Unique
    private void lunararc$clearDragCapture() {
        this.lunararc$dragBeforeSlots = null;
        this.lunararc$dragBeforeCursor = ItemStack.EMPTY;
        this.lunararc$capturingDrag = false;
        this.lunararc$dragType = 0;
    }
}
