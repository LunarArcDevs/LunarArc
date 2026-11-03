package io.lunararcdevs.lunararc.common.event;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Block-related Paper/Bukkit events that are not part of the main event classes. */
public final class LunarArcBlockEvents {
    private LunarArcBlockEvents() {}

    /** The player taking the result out of an anvil, for the duration of AnvilMenu#onTake. */
    public static final ThreadLocal<net.minecraft.world.entity.player.Player> ANVIL_TAKER = new ThreadLocal<>();

    private static boolean listened(HandlerList handlers) {
        return handlers.getRegisteredListeners().length != 0;
    }

    private static boolean fire(Event event) {
        LunarArcServerAccess.getCraftServer().getPluginManager().callEvent(event);
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    private static org.bukkit.entity.Player player(Player who) {
        return who instanceof ServerPlayer && ((EntityBridge) who).lunararc$getBukkitEntity() instanceof org.bukkit.entity.Player found ? found : null;
    }

    /** Where a flowing fluid last spread from, so the block it destroys can name its source. */
    public static BlockPos fluidSource;

    public static boolean breakBlockListened() {
        return listened(io.papermc.paper.event.block.BlockBreakBlockEvent.getHandlerList());
    }

    /**
     * Drops a block that something else (fluid, piston) broke, through BlockBreakBlockEvent. Returns false when
     * nobody listens so the caller keeps the vanilla drop.
     */
    public static boolean dropAfterBreak(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity, BlockPos source) {
        if (!(level instanceof ServerLevel serverLevel) || !org.bukkit.Bukkit.isPrimaryThread() || !breakBlockListened()) return false;
        List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (ItemStack drop : Block.getDrops(state, serverLevel, pos, blockEntity)) items.add(CraftItemStack.asBukkitCopy(drop));
        io.papermc.paper.event.block.BlockBreakBlockEvent event = new io.papermc.paper.event.block.BlockBreakBlockEvent(
                CraftBlock.at(serverLevel, pos), CraftBlock.at(serverLevel, source == null ? pos : source), items);
        event.callEvent();
        for (org.bukkit.inventory.ItemStack drop : event.getDrops()) Block.popResource(serverLevel, pos, CraftItemStack.asNMSCopy(drop));
        if (event.getExpToDrop() > 0) state.getBlock().popExperience(serverLevel, pos, event.getExpToDrop());
        return true;
    }

    public static boolean shearListened() {
        return listened(io.papermc.paper.event.block.PlayerShearBlockEvent.getHandlerList());
    }

    /** A player shears a block; returns the drops to spawn (a plugin may edit them), or null if cancelled. */
    public static List<ItemStack> shearBlock(Player who, ServerLevel level, BlockPos pos, ItemStack tool, InteractionHand hand, List<ItemStack> drops) {
        org.bukkit.entity.Player player = player(who);
        if (player == null || !org.bukkit.Bukkit.isPrimaryThread() || !shearListened()) return drops;
        List<org.bukkit.inventory.ItemStack> bukkitDrops = new ArrayList<>();
        for (ItemStack drop : drops) bukkitDrops.add(CraftItemStack.asCraftMirror(drop));
        io.papermc.paper.event.block.PlayerShearBlockEvent event = new io.papermc.paper.event.block.PlayerShearBlockEvent(
                player, CraftBlock.at(level, pos), CraftItemStack.asCraftMirror(tool), CraftEquipmentSlot.getHand(hand), bukkitDrops);
        if (fire(event)) return null;
        List<ItemStack> result = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack drop : event.getDrops()) result.add(CraftItemStack.asNMSCopy(drop));
        return result;
    }

    private static io.papermc.paper.event.block.BlockLockCheckEvent lastLockCheck;

    /**
     * A player tries to open a locked container. Returns TRUE to allow, FALSE to deny, or null to fall back to
     * the vanilla key check.
     */
    public static Boolean lockCheck(BlockEntity blockEntity, net.minecraft.network.chat.Component displayName, Player who) {
        if (!(who instanceof ServerPlayer player) || blockEntity == null || blockEntity.getLevel() == null || !org.bukkit.Bukkit.isPrimaryThread()
                || !listened(io.papermc.paper.event.block.BlockLockCheckEvent.getHandlerList())
                || blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) != blockEntity
                || !(blockEntity.getLevel() instanceof ServerLevel serverLevel)) return null;
        org.bukkit.entity.Player bukkitPlayer = player(player);
        if (bukkitPlayer == null) return null;
        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.translatable("container.isLocked",
                io.lunararcdevs.lunararc.common.messaging.LunarArcComponentPipeline.toAdventure(displayName));
        net.kyori.adventure.sound.Sound sound = net.kyori.adventure.sound.Sound.sound(
                org.bukkit.Sound.BLOCK_CHEST_LOCKED, net.kyori.adventure.sound.Sound.Source.BLOCK, 1.0F, 1.0F);
        io.papermc.paper.event.block.BlockLockCheckEvent event = new io.papermc.paper.event.block.BlockLockCheckEvent(
                CraftBlock.at(serverLevel, blockEntity.getBlockPos()), bukkitPlayer, message, sound);
        fire(event);
        if (event.getResult() == Event.Result.ALLOW) return Boolean.TRUE;
        if (event.getResult() == Event.Result.DENY) {
            lastLockCheck = event;
            return Boolean.FALSE;
        }
        return null;
    }

    /** Called when the locked-container notification would play; returns true if a plugin's event supplied its own. */
    public static boolean lockedNotification(net.minecraft.world.phys.Vec3 pos) {
        io.papermc.paper.event.block.BlockLockCheckEvent event = lastLockCheck;
        if (event == null) return false;
        lastLockCheck = null;
        if (event.getLockedMessage() != null) event.getPlayer().sendActionBar(event.getLockedMessage());
        if (event.getLockedSound() != null) event.getPlayer().getWorld().playSound(event.getLockedSound(), pos.x(), pos.y(), pos.z());
        return true;
    }

    // ---- vault, fluid, crafter, building, dispensers, furnaces ----

    /** Context for the furnace experience hook: which block entity is paying out, and who took which item. */
    public static BlockPos furnacePos;
    public static ServerPlayer furnacePlayer;
    public static ItemStack furnaceStack = ItemStack.EMPTY;
    public static int furnaceAmount;

    /** A vault is changing state; returns false if a plugin cancelled it. */
    public static boolean vaultStateChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState,
            net.minecraft.world.level.block.entity.vault.VaultSharedData sharedData) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(io.papermc.paper.event.block.VaultChangeStateEvent.getHandlerList())) return true;
        net.minecraft.world.level.block.entity.vault.VaultState before = oldState.getValue(net.minecraft.world.level.block.VaultBlock.STATE);
        net.minecraft.world.level.block.entity.vault.VaultState after = newState.getValue(net.minecraft.world.level.block.VaultBlock.STATE);
        org.bukkit.entity.Player player = null;
        if (after == net.minecraft.world.level.block.entity.vault.VaultState.ACTIVE) {
            java.util.Set<java.util.UUID> connected = sharedData.getConnectedPlayers();
            if (!connected.isEmpty()) player = LunarArcServerAccess.getCraftServer().getPlayer(connected.iterator().next());
        }
        try {
            io.papermc.paper.event.block.VaultChangeStateEvent event = new io.papermc.paper.event.block.VaultChangeStateEvent(
                    CraftBlock.at(level, pos), player,
                    org.bukkit.block.data.type.Vault.State.valueOf(before.name()),
                    org.bukkit.block.data.type.Vault.State.valueOf(after.name()));
            return !fire(event);
        } catch (IllegalArgumentException unknownState) {
            return true;
        }
    }

    /** The vault picked a display item; returns the item to show, or null if a plugin cancelled it. */
    public static ItemStack vaultDisplayItem(ServerLevel level, BlockPos pos, ItemStack item) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.VaultDisplayItemEvent.getHandlerList())) return item;
        org.bukkit.event.block.VaultDisplayItemEvent event = new org.bukkit.event.block.VaultDisplayItemEvent(
                CraftBlock.at(level, pos), CraftItemStack.asBukkitCopy(item));
        if (fire(event)) return null;
        return CraftItemStack.asNMSCopy(event.getDisplayItem());
    }

    /** A fluid is changing the block at the position; returns the state to place, or null if a plugin cancelled it. */
    public static BlockState fluidLevelChange(ServerLevel level, BlockPos pos, BlockState newState) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.FluidLevelChangeEvent.getHandlerList())) return newState;
        org.bukkit.event.block.FluidLevelChangeEvent event = new org.bukkit.event.block.FluidLevelChangeEvent(
                CraftBlock.at(level, pos), org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(newState));
        if (fire(event)) return null;
        return ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getNewData()).getState();
    }

    /** A crafter made an item; returns the result to use, or null if a plugin cancelled it. */
    public static ItemStack crafterCraft(ServerLevel level, BlockPos pos, ItemStack result,
            net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.CrafterCraftEvent.getHandlerList())) return result;
        if (!(org.bukkit.craftbukkit.inventory.CraftRecipeAdapter.toBukkit(holder) instanceof org.bukkit.inventory.CraftingRecipe recipe)) return result;
        org.bukkit.event.block.CrafterCraftEvent event = new org.bukkit.event.block.CrafterCraftEvent(
                CraftBlock.at(level, pos), recipe, CraftItemStack.asBukkitCopy(result));
        if (fire(event)) return null;
        return CraftItemStack.asNMSCopy(event.getResult());
    }

    /** A block is about to be placed; returns whether it may be built, after letting plugins decide. */
    public static boolean canBuild(net.minecraft.world.item.context.BlockPlaceContext context, BlockState state, boolean canBuild) {
        if (!(context.getLevel() instanceof ServerLevel level) || !org.bukkit.Bukkit.isPrimaryThread()
                || !listened(org.bukkit.event.block.BlockCanBuildEvent.getHandlerList())) return canBuild;
        org.bukkit.entity.Player player = player(context.getPlayer());
        org.bukkit.event.block.BlockCanBuildEvent event = new org.bukkit.event.block.BlockCanBuildEvent(
                CraftBlock.at(level, context.getClickedPos()), player, org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state),
                canBuild, CraftEquipmentSlot.getHand(context.getHand()));
        LunarArcServerAccess.getCraftServer().getPluginManager().callEvent(event);
        return event.isBuildable();
    }

    /**
     * A dispenser puts armor on a mob. Returns the stack to equip (possibly changed by a plugin), or null if a
     * plugin cancelled it.
     */
    public static ItemStack dispenseArmor(ServerLevel level, BlockPos dispenser, ItemStack single, net.minecraft.world.entity.LivingEntity target) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.BlockDispenseArmorEvent.getHandlerList())) return single;
        if (!(((EntityBridge) target).lunararc$getBukkitEntity() instanceof org.bukkit.entity.LivingEntity living)) return single;
        org.bukkit.event.block.BlockDispenseArmorEvent event = new org.bukkit.event.block.BlockDispenseArmorEvent(
                CraftBlock.at(level, dispenser), CraftItemStack.asBukkitCopy(single), living);
        if (fire(event)) return null;
        return CraftItemStack.asNMSCopy(event.getItem());
    }

    /** A dispenser shears an entity; returns false if a plugin cancelled it. */
    public static boolean dispenserShear(ServerLevel level, BlockPos dispenser, net.minecraft.world.entity.Entity entity, ItemStack shears) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.BlockShearEntityEvent.getHandlerList())) return true;
        if (!(((EntityBridge) entity).lunararc$getBukkitEntity() instanceof org.bukkit.entity.Entity bukkit)) return true;
        return !fire(new org.bukkit.event.block.BlockShearEntityEvent(
                CraftBlock.at(level, dispenser), bukkit, CraftItemStack.asCraftMirror(shears), new ArrayList<>()));
    }

    /** Experience from a furnace or grindstone; returns the amount to drop. */
    public static int blockExp(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack extracted, int amount, int exp) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) return exp;
        org.bukkit.event.block.BlockExpEvent event;
        if (player != null && amount != 0 && listened(org.bukkit.event.inventory.FurnaceExtractEvent.getHandlerList())
                && ((EntityBridge) player).lunararc$getBukkitEntity() instanceof org.bukkit.entity.Player bukkitPlayer) {
            event = new org.bukkit.event.inventory.FurnaceExtractEvent(bukkitPlayer, CraftBlock.at(level, pos),
                    CraftItemStack.asBukkitCopy(extracted), amount, exp);
        } else if (listened(org.bukkit.event.block.BlockExpEvent.getHandlerList())) {
            event = new org.bukkit.event.block.BlockExpEvent(CraftBlock.at(level, pos), exp);
        } else {
            return exp;
        }
        fire(event);
        return event.getExpToDrop();
    }
    /**
     * A listening block (sculk sensor and similar) is deciding whether it receives a game event. Returns whether it
     * may, after letting plugins change the vanilla answer.
     */
    public static boolean receiveGameEvent(ServerLevel level, net.minecraft.world.phys.Vec3 listener,
            net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> gameEvent,
            net.minecraft.world.level.gameevent.GameEvent.Context context, boolean vanillaAllows) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.BlockReceiveGameEvent.getHandlerList())) return vanillaAllows;
        org.bukkit.GameEvent bukkitEvent = gameEvent.unwrapKey()
                .map(key -> org.bukkit.Registry.GAME_EVENT.get(org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(key.identifier())))
                .orElse(null);
        if (bukkitEvent == null) return vanillaAllows;
        net.minecraft.world.entity.Entity source = context.sourceEntity();
        org.bukkit.entity.Entity bukkitSource = source == null ? null : (org.bukkit.entity.Entity) ((EntityBridge) source).lunararc$getBukkitEntity();
        org.bukkit.event.block.BlockReceiveGameEvent event = new org.bukkit.event.block.BlockReceiveGameEvent(
                bukkitEvent, CraftBlock.at(level, BlockPos.containing(listener)), bukkitSource);
        event.setCancelled(!vanillaAllows);
        LunarArcServerAccess.getCraftServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /**
     * An anvil is taking damage from use. Returns the block state to leave behind (null breaks the anvil); a
     * plugin may change the damage or cancel it, which leaves the original state in place.
     */
    public static BlockState anvilDamaged(Player who, BlockState before, BlockState damaged) {
        if (!(who instanceof ServerPlayer) || !org.bukkit.Bukkit.isPrimaryThread()
                || !listened(com.destroystokyo.paper.event.block.AnvilDamagedEvent.getHandlerList())) return damaged;
        org.bukkit.inventory.InventoryView view = ((io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge) who.containerMenu).lunararc$getBukkitView();
        if (view == null) return damaged;
        com.destroystokyo.paper.event.block.AnvilDamagedEvent event = new com.destroystokyo.paper.event.block.AnvilDamagedEvent(
                view, damaged == null ? null : org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(damaged));
        if (fire(event)) return before;
        if (event.getDamageState() == com.destroystokyo.paper.event.block.AnvilDamagedEvent.DamageState.BROKEN) return null;
        return ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getDamageState().getMaterial().createBlockData()).getState()
                .setValue(net.minecraft.world.level.block.AnvilBlock.FACING, before.getValue(net.minecraft.world.level.block.AnvilBlock.FACING));
    }
}
