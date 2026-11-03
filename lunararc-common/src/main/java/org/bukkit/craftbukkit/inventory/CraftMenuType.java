package org.bukkit.craftbukkit.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public final class CraftMenuType<V extends InventoryView, B extends org.bukkit.inventory.view.builder.InventoryViewBuilder<V>> implements org.bukkit.inventory.MenuType.Typed<V, B> {
    private final NamespacedKey key;
    private final MenuType<?> handle;
    private final Class<? extends InventoryView> viewClass;

    public CraftMenuType(NamespacedKey key, MenuType<?> handle, Class<? extends InventoryView> viewClass) {
        this.key = key;
        this.handle = handle;
        this.viewClass = viewClass;
    }

    public MenuType<?> getHandle() { return handle; }

    @Override public @NotNull NamespacedKey getKey() { return key; }

    @Override
    public @NotNull Set<FeatureFlag> requiredFeatures() {
        return io.papermc.paper.datapack.PaperDatapack.toBukkitFeatures(this.handle.requiredFeatures());
    }

    @Override public @NotNull Class<? extends InventoryView> getInventoryViewClass() { return viewClass; }

    @Override
    public org.bukkit.inventory.MenuType.@NotNull Typed<InventoryView, org.bukkit.inventory.view.builder.InventoryViewBuilder<InventoryView>> typed() {
        return typed(InventoryView.class);
    }

    @Override
    public <T extends InventoryView, TB extends org.bukkit.inventory.view.builder.InventoryViewBuilder<T>> org.bukkit.inventory.MenuType.@NotNull Typed<T, TB> typed(@NotNull Class<T> viewType) {
        if (viewType.isAssignableFrom(viewClass)) {
            @SuppressWarnings("unchecked")
            org.bukkit.inventory.MenuType.Typed<T, TB> typed = (org.bukkit.inventory.MenuType.Typed<T, TB>) this;
            return typed;
        }
        throw new IllegalArgumentException("The MenuType '" + key + "' cannot be typed as " + viewType.getSimpleName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull B builder() {
        if (handle == MenuType.MERCHANT) return (B) new MerchantBuilder<V>(this, null, false, null);
        return (B) new LocationBuilder<V>(this, null, false, null);
    }

    @Override public @NotNull V create(@NotNull HumanEntity player, @Nullable String title) {
        return create(player, title == null ? null : LegacyComponentSerializer.legacySection().deserialize(title));
    }

    /** Paper-style builder for menus that can be tied to a block position (anvil, crafting table, furnaces and the rest). */
    private record LocationBuilder<V extends InventoryView>(CraftMenuType<V, ?> type, @Nullable Component title, boolean checkReachable,
            @Nullable org.bukkit.Location location) implements org.bukkit.inventory.view.builder.LocationInventoryViewBuilder<V> {
        @Override public org.bukkit.inventory.view.builder.LocationInventoryViewBuilder<V> copy() { return new LocationBuilder<>(type, title, checkReachable, location); }
        @Override public org.bukkit.inventory.view.builder.LocationInventoryViewBuilder<V> title(@Nullable Component title) { return new LocationBuilder<>(type, title, checkReachable, location); }
        @Override public org.bukkit.inventory.view.builder.LocationInventoryViewBuilder<V> checkReachable(boolean checkReachable) { return new LocationBuilder<>(type, title, checkReachable, location); }
        @Override public org.bukkit.inventory.view.builder.LocationInventoryViewBuilder<V> location(org.bukkit.Location location) { return new LocationBuilder<>(type, title, checkReachable, location); }
        @Override public V build(HumanEntity player) { return type.buildView(player, title, location, checkReachable, null); }
    }

    /** Paper-style builder for the merchant menu: the merchant being traded with is part of the menu. */
    private record MerchantBuilder<V extends InventoryView>(CraftMenuType<V, ?> type, @Nullable Component title, boolean checkReachable,
            @Nullable org.bukkit.inventory.Merchant merchant) implements org.bukkit.inventory.view.builder.MerchantInventoryViewBuilder<V> {
        @Override public org.bukkit.inventory.view.builder.MerchantInventoryViewBuilder<V> copy() { return new MerchantBuilder<>(type, title, checkReachable, merchant); }
        @Override public org.bukkit.inventory.view.builder.MerchantInventoryViewBuilder<V> title(@Nullable Component title) { return new MerchantBuilder<>(type, title, checkReachable, merchant); }
        @Override public org.bukkit.inventory.view.builder.MerchantInventoryViewBuilder<V> merchant(org.bukkit.inventory.Merchant merchant) { return new MerchantBuilder<>(type, title, checkReachable, merchant); }
        @Override public org.bukkit.inventory.view.builder.MerchantInventoryViewBuilder<V> checkReachable(boolean checkReachable) { return new MerchantBuilder<>(type, title, checkReachable, merchant); }
        @Override public V build(HumanEntity player) { return type.buildView(player, title, null, checkReachable, merchant); }
    }

    @Override
    public @NotNull V create(@NotNull HumanEntity player, @Nullable Component title) {
        return buildView(player, title, null, false, null);
    }

    @SuppressWarnings("unchecked")
    private V buildView(HumanEntity player, @Nullable Component title, @Nullable org.bukkit.Location location, boolean checkReachable,
            @Nullable org.bukkit.inventory.Merchant merchant) {
        if (title == null) title = Component.empty();
        if (!(player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer)) {
            throw new IllegalArgumentException("The given player must be a CraftPlayer");
        }
        ServerPlayer sp = craftPlayer.getHandle();
        int containerId = ((io.lunararcdevs.lunararc.common.bridge.ServerPlayerInventoryBridge) sp).lunararc$nextContainerCounter();
        AbstractContainerMenu menu;
        if (merchant != null) {
            if (handle != MenuType.MERCHANT) throw new IllegalArgumentException("Only the merchant menu takes a merchant");
            menu = new net.minecraft.world.inventory.MerchantMenu(containerId, sp.getInventory(), nmsMerchant(merchant));
        } else {
            net.minecraft.world.inventory.ContainerLevelAccess access = net.minecraft.world.inventory.ContainerLevelAccess.NULL;
            if (location != null) {
                if (!(location.getWorld() instanceof org.bukkit.craftbukkit.CraftWorld world)) {
                    throw new IllegalArgumentException("The location must be in a loaded world");
                }
                access = net.minecraft.world.inventory.ContainerLevelAccess.create(world.getHandle(),
                        new net.minecraft.core.BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            }
            menu = createMenu(containerId, sp, access);
        }
        ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) menu).lunararc$setCheckReachable(checkReachable);
        InventoryView view = org.bukkit.craftbukkit.event.CraftEventFactory.createInventoryView(sp, menu, title);
        if (view == null) view = new CraftInventoryView(craftPlayer, menu, null, null, inferType(menu), title);
        return (V) view;
    }

    /** Menus that read a block position are built with a level access; the rest come from the menu type's own factory. */
    private AbstractContainerMenu createMenu(int id, ServerPlayer player, net.minecraft.world.inventory.ContainerLevelAccess access) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        if (access != net.minecraft.world.inventory.ContainerLevelAccess.NULL) {
            if (handle == MenuType.ANVIL) return new net.minecraft.world.inventory.AnvilMenu(id, inventory, access);
            if (handle == MenuType.CRAFTING) return new net.minecraft.world.inventory.CraftingMenu(id, inventory, access);
            if (handle == MenuType.ENCHANTMENT) return new net.minecraft.world.inventory.EnchantmentMenu(id, inventory, access);
            if (handle == MenuType.GRINDSTONE) return new net.minecraft.world.inventory.GrindstoneMenu(id, inventory, access);
            if (handle == MenuType.LOOM) return new net.minecraft.world.inventory.LoomMenu(id, inventory, access);
            if (handle == MenuType.CARTOGRAPHY_TABLE) return new net.minecraft.world.inventory.CartographyTableMenu(id, inventory, access);
            if (handle == MenuType.SMITHING) return new net.minecraft.world.inventory.SmithingMenu(id, inventory, access);
            if (handle == MenuType.STONECUTTER) return new net.minecraft.world.inventory.StonecutterMenu(id, inventory, access);
        }
        return handle.create(id, inventory);
    }

    private static net.minecraft.world.item.trading.Merchant nmsMerchant(org.bukkit.inventory.Merchant merchant) {
        if (merchant instanceof CraftMerchant craft) return craft.getMerchant();
        if (merchant instanceof org.bukkit.craftbukkit.entity.CraftAbstractVillager villager) return villager.getMerchant();
        throw new IllegalArgumentException("Unsupported merchant implementation: " + merchant.getClass().getName());
    }

    private static InventoryType inferType(AbstractContainerMenu menu) {
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) return InventoryType.CRAFTING;
        if (menu instanceof net.minecraft.world.inventory.ChestMenu) return InventoryType.CHEST;
        if (menu instanceof net.minecraft.world.inventory.CraftingMenu) return InventoryType.WORKBENCH;
        if (menu instanceof net.minecraft.world.inventory.FurnaceMenu) return InventoryType.FURNACE;
        if (menu instanceof net.minecraft.world.inventory.BlastFurnaceMenu) return InventoryType.BLAST_FURNACE;
        if (menu instanceof net.minecraft.world.inventory.SmokerMenu) return InventoryType.SMOKER;
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) return InventoryType.ANVIL;
        if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) return InventoryType.ENCHANTING;
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu) return InventoryType.BREWING;
        if (menu instanceof net.minecraft.world.inventory.BeaconMenu) return InventoryType.BEACON;
        if (menu instanceof net.minecraft.world.inventory.HopperMenu) return InventoryType.HOPPER;
        if (menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu) return InventoryType.SHULKER_BOX;
        if (menu instanceof net.minecraft.world.inventory.MerchantMenu) return InventoryType.MERCHANT;
        if (menu instanceof net.minecraft.world.inventory.LecternMenu) return InventoryType.LECTERN;
        if (menu instanceof net.minecraft.world.inventory.LoomMenu) return InventoryType.LOOM;
        if (menu instanceof net.minecraft.world.inventory.GrindstoneMenu) return InventoryType.GRINDSTONE;
        if (menu instanceof net.minecraft.world.inventory.CartographyTableMenu) return InventoryType.CARTOGRAPHY;
        if (menu instanceof net.minecraft.world.inventory.StonecutterMenu) return InventoryType.STONECUTTER;
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) return InventoryType.SMITHING;
        if (menu instanceof net.minecraft.world.inventory.DispenserMenu) return InventoryType.DISPENSER;
        if (menu instanceof net.minecraft.world.inventory.CrafterMenu) return InventoryType.CRAFTER;
        return InventoryType.CHEST;
    }

    public static net.minecraft.world.inventory.MenuType<?> bukkitToMinecraft(org.bukkit.inventory.MenuType bukkit) {
        return org.bukkit.craftbukkit.CraftRegistry.bukkitToMinecraft(bukkit);
    }

    public static org.bukkit.inventory.MenuType minecraftToBukkit(net.minecraft.world.inventory.MenuType<?> minecraft) {
        return org.bukkit.craftbukkit.CraftRegistry.minecraftToBukkit(minecraft, net.minecraft.core.registries.Registries.MENU, org.bukkit.Registry.MENU);
    }

    public static org.bukkit.inventory.MenuType minecraftHolderToBukkit(
            net.minecraft.core.Holder<net.minecraft.world.inventory.MenuType<?>> minecraft) {
        return minecraftToBukkit(minecraft.value());
    }
}
