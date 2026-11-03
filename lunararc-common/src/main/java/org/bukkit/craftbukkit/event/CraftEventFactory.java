package org.bukkit.craftbukkit.event;

import com.mojang.datafixers.util.Pair;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;

public class CraftEventFactory {
    public static net.minecraft.world.level.block.state.BlockState damagingBlockState;
    public static net.minecraft.core.BlockPos damagingBlockPos;

    @SuppressWarnings("deprecation")
    public static EntityDamageEvent callEntityDamageEvent(LivingEntity entity, DamageSource source, float damage) {
        DamageCause cause = damageCause(source);

        org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity)
                .lunararc$getBukkitEntity();
        org.bukkit.craftbukkit.damage.CraftDamageSource bukkitSource =
                new org.bukkit.craftbukkit.damage.CraftDamageSource(source);

        org.bukkit.entity.Entity damager = bukkitSource.getDirectEntity();
        if (damager == null) damager = bukkitSource.getCausingEntity();

        net.minecraft.world.level.block.state.BlockState blockState = damagingBlockState;
        net.minecraft.core.BlockPos blockPos = damagingBlockPos;
        damagingBlockState = null;
        damagingBlockPos = null;
        EntityDamageEvent event;
        if (damager != null) {
            event = new org.bukkit.event.entity.EntityDamageByEntityEvent(
                    damager, bukkitEntity, cause, bukkitSource, (double) damage);
        } else if (blockPos != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
            org.bukkit.block.Block block = CraftBlock.at(level, blockPos);
            event = new org.bukkit.event.entity.EntityDamageByBlockEvent(block,
                    org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(level, blockPos),
                    bukkitEntity, cause, bukkitSource, (double) damage);
        } else {
            event = new EntityDamageEvent(bukkitEntity, cause, bukkitSource, (double) damage);
        }
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private static DamageCause damageCause(DamageSource source) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) return DamageCause.KILL;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.OUTSIDE_BORDER)) return DamageCause.WORLD_BORDER;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
                || source.is(net.minecraft.world.damagesource.DamageTypes.SWEET_BERRY_BUSH)
                || source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.STING)) return DamageCause.CONTACT;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return DamageCause.ENTITY_ATTACK;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.ARROW)
                || source.is(net.minecraft.world.damagesource.DamageTypes.TRIDENT)
                || source.is(net.minecraft.world.damagesource.DamageTypes.MOB_PROJECTILE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.SPIT)
                || source.is(net.minecraft.world.damagesource.DamageTypes.WIND_CHARGE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FIREWORKS)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FIREBALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.UNATTRIBUTED_FIREBALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.WITHER_SKULL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.THROWN)) return DamageCause.PROJECTILE;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return DamageCause.SUFFOCATION;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) return DamageCause.FALL;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE)) return DamageCause.FIRE;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)) return DamageCause.FIRE_TICK;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.CAMPFIRE)) return DamageCause.CAMPFIRE;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)) return DamageCause.HOT_FLOOR;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)) return DamageCause.LAVA;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) return DamageCause.DROWNING;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)) return source.getDirectEntity() == null ? DamageCause.BLOCK_EXPLOSION : DamageCause.ENTITY_EXPLOSION;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)
                || source.is(net.minecraft.world.damagesource.DamageTypes.BAD_RESPAWN_POINT)) return DamageCause.ENTITY_EXPLOSION;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) return DamageCause.VOID;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)) return DamageCause.LIGHTNING;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)) return DamageCause.STARVATION;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) return DamageCause.MAGIC;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)) return DamageCause.WITHER;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE)) return DamageCause.FALLING_BLOCK;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) return DamageCause.THORNS;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DRAGON_BREATH)) return DamageCause.DRAGON_BREATH;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)) return DamageCause.FLY_INTO_WALL;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) return DamageCause.CRAMMING;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DRY_OUT)) return DamageCause.DRYOUT;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FREEZE)) return DamageCause.FREEZE;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM)) return DamageCause.SONIC_BOOM;
        return DamageCause.CUSTOM;
    }

    public static PlayerInteractEvent callPlayerInteractEvent(net.minecraft.server.level.ServerPlayer player,
            Action action, @Nullable net.minecraft.core.BlockPos pos, @Nullable net.minecraft.core.Direction direction,
            @Nullable net.minecraft.world.item.ItemStack itemstack) {
        return callPlayerInteractEvent(player, action, pos, direction, itemstack, org.bukkit.inventory.EquipmentSlot.HAND);
    }

    public static PlayerInteractEvent callPlayerInteractEvent(net.minecraft.server.level.ServerPlayer player,
            Action action, @Nullable net.minecraft.core.BlockPos pos, @Nullable net.minecraft.core.Direction direction,
            @Nullable net.minecraft.world.item.ItemStack itemstack, @Nullable org.bukkit.inventory.EquipmentSlot hand) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                .lunararc$getBukkitEntity();
        if (bukkitPlayer == null)
            return null;

        ItemStack item = CraftItemStack.asBukkitCopy(itemstack);
        Block block = (pos != null) ? CraftBlock.create(player.level(), pos) : null;
        BlockFace face = (direction != null) ? BlockFace.valueOf(direction.name()) : BlockFace.SELF;

        PlayerInteractEvent event = new PlayerInteractEvent(bukkitPlayer, action, item, block, face, hand);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static io.papermc.paper.block.bed.BedRuleResult asBedRuleResult(net.minecraft.world.attribute.BedRule.Rule rule, boolean value) {
        return switch (rule) {
            case ALWAYS -> io.papermc.paper.block.bed.BedRuleResult.ALLOWED;
            case WHEN_DARK -> value ? io.papermc.paper.block.bed.BedRuleResult.ALLOWED : io.papermc.paper.block.bed.BedRuleResult.TOO_MUCH_LIGHT;
            case NEVER -> io.papermc.paper.block.bed.BedRuleResult.NEVER;
        };
    }

    public static com.mojang.datafixers.util.Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, net.minecraft.util.Unit> callPlayerBedEnterEvent(
            net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos bed,
            com.mojang.datafixers.util.Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, net.minecraft.util.Unit> nmsResult) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                .lunararc$getBukkitEntity();
        if (bukkitPlayer == null) return nmsResult;

        net.minecraft.world.level.Level level = player.level();
        net.minecraft.world.attribute.BedRule rule = level.environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, bed);
        org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult result = nmsResult.map(problem -> {
            if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.TOO_FAR_AWAY) {
                return org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.TOO_FAR_AWAY;
            } else if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_SAFE) {
                return org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.NOT_SAFE;
            } else if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.OBSTRUCTED
                    || problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM) {
                return org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.OTHER_PROBLEM;
            }
            // Remaining problems come from the dimension's bed rule (BedRule#asProblem).
            return switch (rule.canSleep()) {
                case NEVER -> org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE;
                case WHEN_DARK -> org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW;
                case ALWAYS -> org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.OTHER_PROBLEM;
            };
        }, unit -> org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.OK);

        io.papermc.paper.block.bed.BedEnterProblem enterProblem = nmsResult.map(problem -> {
            if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.TOO_FAR_AWAY) return io.papermc.paper.block.bed.BedEnterProblem.TOO_FAR_AWAY;
            if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_SAFE) return io.papermc.paper.block.bed.BedEnterProblem.NOT_SAFE;
            if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.OBSTRUCTED) return io.papermc.paper.block.bed.BedEnterProblem.OBSTRUCTED;
            return rule.explodes() ? io.papermc.paper.block.bed.BedEnterProblem.EXPLOSION : io.papermc.paper.block.bed.BedEnterProblem.OTHER;
        }, unit -> null);
        io.papermc.paper.block.bed.BedEnterAction action = new io.papermc.paper.block.bed.BedEnterActionImpl(
                asBedRuleResult(rule.canSleep(), rule.canSleep().test(level)),
                asBedRuleResult(rule.canSetSpawn(), rule.canSetSpawn().test(level)),
                enterProblem,
                rule.errorMessage().map(io.papermc.paper.adventure.PaperAdventure::asAdventure).orElse(null));

        var event = new org.bukkit.event.player.PlayerBedEnterEvent(
                bukkitPlayer, org.bukkit.craftbukkit.block.CraftBlock.at(player.level(), bed), result, action);
        Bukkit.getPluginManager().callEvent(event);
        return switch (event.useBed()) {
            case ALLOW -> com.mojang.datafixers.util.Either.right(net.minecraft.util.Unit.INSTANCE);
            case DENY -> com.mojang.datafixers.util.Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            case DEFAULT -> nmsResult;
        };
    }

    public static org.bukkit.event.player.PlayerBedLeaveEvent callPlayerBedLeaveEvent(
            net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos bedPos, boolean setSpawnLocation) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                .lunararc$getBukkitEntity();
        if (bukkitPlayer == null) return null;
        org.bukkit.block.Block bed = bedPos != null
                ? org.bukkit.craftbukkit.block.CraftBlock.at(player.level(), bedPos)
                : bukkitPlayer.getLocation().getBlock();
        var event = new org.bukkit.event.player.PlayerBedLeaveEvent(bukkitPlayer, bed, setSpawnLocation);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.entity.EntityToggleGlideEvent callToggleGlideEvent(
            net.minecraft.world.entity.LivingEntity entity, boolean gliding) {
        org.bukkit.entity.LivingEntity bukkit = (org.bukkit.entity.LivingEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity)
                .lunararc$getBukkitEntity();
        org.bukkit.event.entity.EntityToggleGlideEvent event = new org.bukkit.event.entity.EntityToggleGlideEvent(bukkit, gliding);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.block.BlockBreakEvent callBlockBreakEvent(
            net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos pos,
            net.minecraft.server.level.ServerPlayer player) {
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.create(world, pos);
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                .lunararc$getBukkitEntity();

        org.bukkit.event.block.BlockBreakEvent event = new org.bukkit.event.block.BlockBreakEvent(block, bukkitPlayer);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.block.BlockPlaceEvent callBlockPlaceEvent(
            net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos pos,
            net.minecraft.server.level.ServerPlayer player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.level.block.state.BlockState newState) {
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.create(world, pos);

        org.bukkit.entity.Player bukkitPlayer = player != null
                ? (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                        .lunararc$getBukkitEntity()
                : null;
        ItemStack item = player != null ? CraftItemStack.asBukkitCopy(player.getItemInHand(hand)) : null;

        org.bukkit.event.block.BlockPlaceEvent event = newPlaceEvent(world, pos, newState, block, block.getState(), item, bukkitPlayer,
                org.bukkit.inventory.EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    /** The other block a multi-block item (bed, door, tall plant) is about to place, or null for a single block. */
    private static net.minecraft.core.BlockPos otherPlacedHalf(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (state == null) return null;
        net.minecraft.world.level.block.Block placed = state.getBlock();
        if (placed instanceof net.minecraft.world.level.block.BedBlock) {
            return pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
        }
        if (placed instanceof net.minecraft.world.level.block.DoorBlock || placed instanceof net.minecraft.world.level.block.DoublePlantBlock) {
            return pos.above();
        }
        return null;
    }

    /** BlockMultiPlaceEvent (which extends BlockPlaceEvent) for beds, doors and tall plants, a plain BlockPlaceEvent otherwise. */
    private static org.bukkit.event.block.BlockPlaceEvent newPlaceEvent(net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState newState, org.bukkit.block.Block block, org.bukkit.block.BlockState replaced,
            ItemStack item, org.bukkit.entity.Player player, org.bukkit.inventory.EquipmentSlot slot) {
        net.minecraft.core.BlockPos other = otherPlacedHalf(pos, newState);
        if (other != null) {
            // the second half is placed after this event, so its current state is the one being replaced
            java.util.List<org.bukkit.block.BlockState> replacedStates = java.util.List.of(replaced,
                    org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(world, other));
            return new org.bukkit.event.block.BlockMultiPlaceEvent(replacedStates, block, item, player, true, slot);
        }
        return new org.bukkit.event.block.BlockPlaceEvent(block, replaced, block, item, player, true, slot);
    }

    public static org.bukkit.event.block.BlockPlaceEvent callBlockPlaceEvent(
            net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos pos,
            net.minecraft.server.level.ServerPlayer player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.level.block.state.BlockState newState,
            net.minecraft.world.level.block.state.BlockState replacedState,
            @org.jetbrains.annotations.Nullable net.minecraft.nbt.CompoundTag replacedBlockEntityTag) {
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.create(world, pos);

        org.bukkit.entity.Player bukkitPlayer = player != null
                ? (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                        .lunararc$getBukkitEntity()
                : null;
        ItemStack item = player != null ? CraftItemStack.asBukkitCopy(player.getItemInHand(hand)) : null;

        net.minecraft.world.level.block.entity.BlockEntity replacedTileEntity = replacedBlockEntityTag == null
                ? null
                : net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        pos, replacedState, replacedBlockEntityTag, world.registryAccess());
        org.bukkit.block.BlockState bukkitReplacedState =
                org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(
                        block.getWorld(), pos, replacedState, replacedTileEntity);

        org.bukkit.event.block.BlockPlaceEvent event = newPlaceEvent(world, pos, newState, block, bukkitReplacedState, item, bukkitPlayer,
                hand == net.minecraft.world.InteractionHand.OFF_HAND
                        ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                        : org.bukkit.inventory.EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static org.bukkit.event.Cancellable callEntitySpawnEvent(net.minecraft.world.entity.Entity entity) {
        org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        if (bukkitEntity == null) return null;
        org.bukkit.event.Event event;
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity && bukkitEntity instanceof org.bukkit.entity.Item item) {
            event = new org.bukkit.event.entity.ItemSpawnEvent(item);
        } else if (bukkitEntity instanceof org.bukkit.entity.Vehicle vehicle) {
            event = new org.bukkit.event.vehicle.VehicleCreateEvent(vehicle);
        } else {
            event = new org.bukkit.event.entity.EntitySpawnEvent(bukkitEntity);
        }
        Bukkit.getPluginManager().callEvent(event);
        return (org.bukkit.event.Cancellable) event;
    }

    public static org.bukkit.event.entity.CreatureSpawnEvent callCreatureSpawnEvent(net.minecraft.world.entity.LivingEntity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        if (!(bukkitEntity instanceof org.bukkit.entity.LivingEntity living)) return null;
        var event = new org.bukkit.event.entity.CreatureSpawnEvent(living, reason);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static org.bukkit.event.player.PlayerJoinEvent callPlayerJoinEvent(net.minecraft.server.level.ServerPlayer player) {
        org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(player.getUUID());
        if (bukkitPlayer == null) return null;
        var event = new org.bukkit.event.player.PlayerJoinEvent(bukkitPlayer, net.kyori.adventure.text.Component.text(player.getScoreboardName() + " joined the game"));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static org.bukkit.event.player.PlayerQuitEvent callPlayerQuitEvent(net.minecraft.server.level.ServerPlayer player) {
        org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(player.getUUID());
        if (bukkitPlayer == null) return null;
        var event = new org.bukkit.event.player.PlayerQuitEvent(bukkitPlayer, net.kyori.adventure.text.Component.text(player.getScoreboardName() + " left the game"));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static org.bukkit.event.player.PlayerMoveEvent callPlayerMoveEvent(net.minecraft.server.level.ServerPlayer player, Location from, Location to) {
        org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(player.getUUID());
        if (bukkitPlayer == null) return null;
        var event = new org.bukkit.event.player.PlayerMoveEvent(bukkitPlayer, from, to);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static org.bukkit.event.player.AsyncPlayerChatEvent callAsyncChatEvent(
            net.minecraft.server.level.ServerPlayer player, String message) {
        org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(player.getUUID());
        if (bukkitPlayer == null) return null;
        java.util.Set<org.bukkit.entity.Player> recipients = new java.util.HashSet<>(
                (java.util.Collection<? extends org.bukkit.entity.Player>)
                        org.bukkit.Bukkit.getOnlinePlayers());
        @SuppressWarnings("deprecation")
        var event = new org.bukkit.event.player.AsyncPlayerChatEvent(false, bukkitPlayer, message, recipients);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static void callWorldLoadEvent(org.bukkit.World world) {
        var event = new org.bukkit.event.world.WorldLoadEvent(world);
        Bukkit.getPluginManager().callEvent(event);
    }


    public static void callWorldSaveEvent(org.bukkit.World world) {
        var event = new org.bukkit.event.world.WorldSaveEvent(world);
        Bukkit.getPluginManager().callEvent(event);
    }






    public static org.bukkit.event.entity.PlayerLeashEntityEvent callPlayerLeashEntityEvent(
            net.minecraft.world.entity.Entity entity,
            net.minecraft.world.entity.Entity leashHolder,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        var event = new org.bukkit.event.entity.PlayerLeashEntityEvent(
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity(),
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) leashHolder).lunararc$getBukkitEntity(),
                (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player).lunararc$getBukkitEntity(),
                org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand));
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.entity.EntityBreedEvent callEntityBreedEvent(
            net.minecraft.world.entity.LivingEntity child,
            net.minecraft.world.entity.LivingEntity mother,
            net.minecraft.world.entity.LivingEntity father,
            @Nullable net.minecraft.world.entity.LivingEntity breeder,
            @Nullable net.minecraft.world.item.ItemStack bredWith,
            int experience) {
        org.bukkit.entity.LivingEntity breederEntity = breeder == null
                ? null
                : (org.bukkit.entity.LivingEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) breeder).lunararc$getBukkitEntity();
        org.bukkit.inventory.ItemStack bredWithStack = bredWith == null || bredWith.isEmpty()
                ? null
                : org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(bredWith);
        var event = new org.bukkit.event.entity.EntityBreedEvent(
                (org.bukkit.entity.LivingEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) child).lunararc$getBukkitEntity(),
                (org.bukkit.entity.LivingEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) mother).lunararc$getBukkitEntity(),
                (org.bukkit.entity.LivingEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) father).lunararc$getBukkitEntity(),
                breederEntity, bredWithStack, experience);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.entity.EntityPotionEffectEvent callEntityPotionEffectChangeEvent(
            net.minecraft.world.entity.LivingEntity entity,
            @Nullable net.minecraft.world.effect.MobEffectInstance oldEffect,
            @Nullable net.minecraft.world.effect.MobEffectInstance newEffect,
            org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause,
            boolean willOverride) {
        org.bukkit.event.entity.EntityPotionEffectEvent.Action action =
                oldEffect == null
                        ? org.bukkit.event.entity.EntityPotionEffectEvent.Action.ADDED
                        : newEffect == null
                                ? org.bukkit.event.entity.EntityPotionEffectEvent.Action.REMOVED
                                : org.bukkit.event.entity.EntityPotionEffectEvent.Action.CHANGED;
        return callEntityPotionEffectChangeEvent(entity, oldEffect, newEffect, cause, action, willOverride);
    }

    public static org.bukkit.event.entity.EntityPotionEffectEvent callEntityPotionEffectChangeEvent(
            net.minecraft.world.entity.LivingEntity entity,
            @Nullable net.minecraft.world.effect.MobEffectInstance oldEffect,
            @Nullable net.minecraft.world.effect.MobEffectInstance newEffect,
            org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause,
            org.bukkit.event.entity.EntityPotionEffectEvent.Action action,
            boolean willOverride) {
        org.bukkit.potion.PotionEffect bukkitOld = lunararc$toBukkitEffect(oldEffect);
        org.bukkit.potion.PotionEffect bukkitNew = lunararc$toBukkitEffect(newEffect);
        if (bukkitOld == null && bukkitNew == null) {
            throw new IllegalArgumentException("Old and new potion effect are both null");
        }
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.LivingEntity living)) {
            throw new IllegalStateException("NMS LivingEntity is not backed by a Bukkit LivingEntity: " + entity);
        }
        var event = new org.bukkit.event.entity.EntityPotionEffectEvent(
                living, bukkitOld, bukkitNew, cause, action, willOverride);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private static @Nullable org.bukkit.potion.PotionEffect lunararc$toBukkitEffect(
            @Nullable net.minecraft.world.effect.MobEffectInstance effect) {
        if (effect == null) return null;
        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        if (id == null) return null;
        org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByKey(
                new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()));
        if (type == null) return null;
        return new org.bukkit.potion.PotionEffect(
                type, effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    public static org.bukkit.event.entity.EntityDeathEvent callEntityDeathEvent(
            net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
            java.util.List<org.bukkit.inventory.ItemStack> drops, int droppedExp) {
        org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        if (!(bukkitEntity instanceof org.bukkit.entity.LivingEntity living)) return null;

        var event = new org.bukkit.event.entity.EntityDeathEvent(
                living, new org.bukkit.craftbukkit.damage.CraftDamageSource(source), drops, droppedExp);
        double maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? living.getMaxHealth()
                : living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (maxHealth > 0.0D) event.setReviveHealth(maxHealth);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }


    public static AbstractContainerMenu callInventoryOpenEvent(ServerPlayer player,
            AbstractContainerMenu container, InventoryView view) {
        return callInventoryOpenEventWithTitle(player, container, view, false).getSecond();
    }

    public static AbstractContainerMenu callInventoryOpenEvent(ServerPlayer player,
            AbstractContainerMenu container, InventoryView view, boolean cancelled) {
        return callInventoryOpenEventWithTitle(player, container, view, cancelled).getSecond();
    }

    public static Pair<Component, AbstractContainerMenu> callInventoryOpenEventWithTitle(
            ServerPlayer player, AbstractContainerMenu container, InventoryView view, boolean cancelled) {
        InventoryOpenEvent event = new InventoryOpenEvent(view);
        event.setCancelled(cancelled);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Pair.of(null, null);
        }
        return Pair.of(event.titleOverride(), container);
    }


    public static void handleInventoryCloseEvent(ServerPlayer player) {
        handleInventoryCloseEvent(player, InventoryCloseEvent.Reason.UNKNOWN);
    }

    public static void handleInventoryCloseEvent(ServerPlayer player, InventoryCloseEvent.Reason reason) {
        if (player.containerMenu == player.inventoryMenu) return;
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof HumanEntity human)) return;
        InventoryCloseEvent event = new InventoryCloseEvent(human.getOpenInventory(), reason);
        Bukkit.getPluginManager().callEvent(event);
    }

    public static net.minecraft.world.item.ItemStack handleEditBookEvent(
            net.minecraft.server.level.ServerPlayer player, int slot,
            net.minecraft.world.item.ItemStack oldBook,
            net.minecraft.world.item.ItemStack proposedBook) {
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Player bp)) return proposedBook;
        org.bukkit.inventory.ItemStack oldBukkit = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(oldBook);
        org.bukkit.inventory.ItemStack proposedBukkit = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(proposedBook);
        if (!(oldBukkit.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta oldMeta)
                || !(proposedBukkit.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta newMeta)) return proposedBook;
        boolean signing = proposedBook.is(net.minecraft.world.item.Items.WRITTEN_BOOK);
        var event = new org.bukkit.event.player.PlayerEditBookEvent(
                bp, slot >= 0 && slot <= 8 ? slot : -1, oldMeta, newMeta, signing);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return oldBook;
        org.bukkit.inventory.ItemStack result = proposedBukkit.clone();
        result.setType(event.isSigning() ? org.bukkit.Material.WRITTEN_BOOK : org.bukkit.Material.WRITABLE_BOOK);
        result.setItemMeta(event.getNewBookMeta());
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(result);
    }

    public static org.bukkit.event.player.PlayerBucketFillEvent callPlayerBucketFillEvent(
            net.minecraft.server.level.ServerLevel world,
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.core.BlockPos changed,
            net.minecraft.core.BlockPos clicked,
            net.minecraft.core.Direction face,
            net.minecraft.world.item.ItemStack bucketInHand,
            net.minecraft.world.item.ItemStack result,
            net.minecraft.world.InteractionHand hand) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                .lunararc$getBukkitEntity();
        if (bukkitPlayer == null) return null;
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(world, changed);
        org.bukkit.block.Block clickedBlock = org.bukkit.craftbukkit.block.CraftBlock.at(world, clicked);
        org.bukkit.block.BlockFace blockFace = org.bukkit.block.BlockFace.valueOf(face.name());
        org.bukkit.inventory.EquipmentSlot slot = hand == net.minecraft.world.InteractionHand.OFF_HAND
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND;
        org.bukkit.event.player.PlayerBucketFillEvent event = new org.bukkit.event.player.PlayerBucketFillEvent(
                bukkitPlayer, block, clickedBlock, blockFace,
                org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(bucketInHand).getType(),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(result), slot);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.player.PlayerBucketEmptyEvent callPlayerBucketEmptyEvent(
            net.minecraft.server.level.ServerLevel world,
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.core.BlockPos changed,
            net.minecraft.core.BlockPos clicked,
            net.minecraft.core.Direction face,
            net.minecraft.world.item.ItemStack bucketInHand,
            net.minecraft.world.item.ItemStack result,
            net.minecraft.world.InteractionHand hand) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player)
                .lunararc$getBukkitEntity();
        if (bukkitPlayer == null) return null;
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(world, changed);
        org.bukkit.block.Block clickedBlock = org.bukkit.craftbukkit.block.CraftBlock.at(world, clicked);
        org.bukkit.block.BlockFace blockFace = org.bukkit.block.BlockFace.valueOf(face.name());
        org.bukkit.inventory.EquipmentSlot slot = hand == net.minecraft.world.InteractionHand.OFF_HAND
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND;
        org.bukkit.event.player.PlayerBucketEmptyEvent event = new org.bukkit.event.player.PlayerBucketEmptyEvent(
                bukkitPlayer, block, clickedBlock, blockFace,
                org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(bucketInHand).getType(),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(result), slot);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.craftbukkit.inventory.CraftInventoryView createInventoryView(
            ServerPlayer player, AbstractContainerMenu menu, net.kyori.adventure.text.Component title) {
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer)) return null;
        org.bukkit.event.inventory.InventoryType type;
        org.bukkit.craftbukkit.inventory.CraftInventory top = null;
        if (menu instanceof net.minecraft.world.inventory.MerchantMenu merchant) {
            return new org.bukkit.craftbukkit.inventory.CraftMerchantView(craftPlayer, merchant, title);
        }
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu anvil) {
            return new org.bukkit.craftbukkit.inventory.CraftAnvilView(craftPlayer, anvil, title);
        }
        if (menu instanceof net.minecraft.world.inventory.CraftingMenu crafting) {
            type = org.bukkit.event.inventory.InventoryType.WORKBENCH;
            var recipe = player.level().recipeAccess().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                    crafting.craftSlots.asCraftInput(), player.level()).orElse(null);
            org.bukkit.inventory.Inventory craftingTop = new org.bukkit.craftbukkit.inventory.CraftInventoryCrafting(
                    crafting.craftSlots, crafting.resultSlots, craftPlayer, recipe);
            return new org.bukkit.craftbukkit.inventory.CraftInventoryView(
                    craftPlayer, menu, craftingTop, craftPlayer.getInventory(), type,
                    title == null ? net.kyori.adventure.text.Component.text(type.name()) : title);
        }
        else if (menu instanceof net.minecraft.world.inventory.FurnaceMenu) type = org.bukkit.event.inventory.InventoryType.FURNACE;
        else if (menu instanceof net.minecraft.world.inventory.BlastFurnaceMenu) type = org.bukkit.event.inventory.InventoryType.BLAST_FURNACE;
        else if (menu instanceof net.minecraft.world.inventory.SmokerMenu) type = org.bukkit.event.inventory.InventoryType.SMOKER;
        else if (menu instanceof net.minecraft.world.inventory.SmithingMenu smithing) {
            type = org.bukkit.event.inventory.InventoryType.SMITHING;
            top = new org.bukkit.craftbukkit.inventory.CraftSmithingInventory(smithing, craftPlayer);
        } else if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) type = org.bukkit.event.inventory.InventoryType.ENCHANTING;
        else if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu) type = org.bukkit.event.inventory.InventoryType.BREWING;
        else if (menu instanceof net.minecraft.world.inventory.BeaconMenu) type = org.bukkit.event.inventory.InventoryType.BEACON;
        else if (menu instanceof net.minecraft.world.inventory.HopperMenu) type = org.bukkit.event.inventory.InventoryType.HOPPER;
        else if (menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu) type = org.bukkit.event.inventory.InventoryType.SHULKER_BOX;
        else if (menu instanceof net.minecraft.world.inventory.LecternMenu) type = org.bukkit.event.inventory.InventoryType.LECTERN;
        else if (menu instanceof net.minecraft.world.inventory.LoomMenu) type = org.bukkit.event.inventory.InventoryType.LOOM;
        else if (menu instanceof net.minecraft.world.inventory.GrindstoneMenu grindstone) {
            type = org.bukkit.event.inventory.InventoryType.GRINDSTONE;
            top = new org.bukkit.craftbukkit.inventory.CraftGrindstoneInventory(grindstone, craftPlayer);
        }
        else if (menu instanceof net.minecraft.world.inventory.CartographyTableMenu cartography) {
            type = org.bukkit.event.inventory.InventoryType.CARTOGRAPHY;
            top = new org.bukkit.craftbukkit.inventory.CraftCartographyInventory(cartography, craftPlayer);
        }
        else if (menu instanceof net.minecraft.world.inventory.StonecutterMenu) type = org.bukkit.event.inventory.InventoryType.STONECUTTER;
        else if (menu instanceof net.minecraft.world.inventory.ChestMenu) type = org.bukkit.event.inventory.InventoryType.CHEST;
        else type = org.bukkit.event.inventory.InventoryType.CHEST;
        return new org.bukkit.craftbukkit.inventory.CraftInventoryView(
                craftPlayer, menu, top, craftPlayer.getInventory(), type,
                title == null ? net.kyori.adventure.text.Component.text(type.name()) : title);
    }


    public static CraftPortalEvent callPortalEvent(net.minecraft.world.entity.Entity entity,
                                                    org.bukkit.Location exit,
                                                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause,
                                                    int searchRadius,
                                                    int creationRadius) {
        org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        org.bukkit.Location enter = bukkitEntity.getLocation();
        if (bukkitEntity instanceof org.bukkit.entity.Player player) {
            org.bukkit.event.player.PlayerPortalEvent event = new org.bukkit.event.player.PlayerPortalEvent(
                    player, enter, exit, cause, searchRadius, true, creationRadius);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) return null;
            return new CraftPortalEvent(event);
        }
        org.bukkit.event.entity.EntityPortalEvent event = new org.bukkit.event.entity.EntityPortalEvent(
                bukkitEntity, enter, exit, searchRadius, true, creationRadius);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null || !entity.isAlive()) return null;
        return new CraftPortalEvent(event);
    }

    public static org.bukkit.event.entity.EntityExplodeEvent callEntityExplodeEvent(
            net.minecraft.world.entity.Entity entity,
            java.util.List<Block> blocks,
            float yield,
            net.minecraft.world.level.Explosion.BlockInteraction effect) {
        org.bukkit.entity.Entity bukkitEntity =
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity();
        org.bukkit.event.entity.EntityExplodeEvent event = new org.bukkit.event.entity.EntityExplodeEvent(
                bukkitEntity, bukkitEntity.getLocation(), blocks, yield,
                org.bukkit.craftbukkit.CraftExplosionResult.toExplosionResult(effect));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.block.BlockExplodeEvent callBlockExplodeEvent(
            Block block,
            org.bukkit.block.BlockState state,
            java.util.List<Block> blocks,
            float yield,
            net.minecraft.world.level.Explosion.BlockInteraction effect) {
        org.bukkit.event.block.BlockExplodeEvent event = new org.bukkit.event.block.BlockExplodeEvent(
                block, state, blocks, yield,
                org.bukkit.craftbukkit.CraftExplosionResult.toExplosionResult(effect));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static net.minecraft.core.BlockPos sourceBlockOverride = null;

    @FunctionalInterface
    public interface BlockPlacer {
        boolean place(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flag);
    }

    public static boolean handleBlockGrowEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block) {
        return handleBlockGrowEvent(world, pos, block, 3);
    }

    public static boolean handleBlockGrowEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, int flag) {
        return handleBlockGrowEvent(world, pos, block, flag, world::setBlock);
    }

    public static boolean handleBlockGrowEvent(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, int flag, BlockPlacer placer) {
        return fireBlockChange(world, pos, block, flag, placer,
                state -> new org.bukkit.event.block.BlockGrowEvent(state.getBlock(), state));
    }

    public static boolean handleBlockSpreadEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos source,
            net.minecraft.core.BlockPos target, net.minecraft.world.level.block.state.BlockState block) {
        return handleBlockSpreadEvent(world, source, target, block, 2);
    }

    public static boolean handleBlockSpreadEvent(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos source,
            net.minecraft.core.BlockPos target, net.minecraft.world.level.block.state.BlockState block, int flag) {
        return handleBlockSpreadEvent(world, source, target, block, flag, world::setBlock);
    }

    public static boolean handleBlockSpreadEvent(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos source,
            net.minecraft.core.BlockPos target, net.minecraft.world.level.block.state.BlockState block, int flag, BlockPlacer placer) {
        return fireBlockChange(world, target, block, flag, placer, state -> new org.bukkit.event.block.BlockSpreadEvent(state.getBlock(),
                CraftBlock.at((net.minecraft.server.level.ServerLevel) world, sourceBlockOverride != null ? sourceBlockOverride : source), state));
    }

    public static boolean handleBlockFormEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, int flag) {
        return handleBlockFormEvent(world, pos, block, flag, null, world::setBlock);
    }

    public static boolean handleBlockFormEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, int flag, @Nullable net.minecraft.world.entity.Entity entity) {
        return handleBlockFormEvent(world, pos, block, flag, entity, world::setBlock);
    }

    public static boolean handleBlockFormEvent(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, int flag, @Nullable net.minecraft.world.entity.Entity entity,
            BlockPlacer placer) {
        return fireBlockChange(world, pos, block, flag, placer, state -> entity == null
                ? new org.bukkit.event.block.BlockFormEvent(state.getBlock(), state)
                : new org.bukkit.event.block.EntityBlockFormEvent(
                        ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity(), state.getBlock(), state));
    }

    public static boolean handleMoistureChangeEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState newData, int flag) {
        return handleMoistureChangeEvent(world, pos, newData, flag, world::setBlock);
    }

    public static boolean handleMoistureChangeEvent(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState newData, int flag, BlockPlacer placer) {
        return fireBlockChange(world, pos, newData, flag, placer,
                state -> new org.bukkit.event.block.MoistureChangeEvent(state.getBlock(), state));
    }

    private static boolean fireBlockChange(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, int flag, BlockPlacer placer,
            java.util.function.Function<org.bukkit.craftbukkit.block.CraftBlockState, ? extends org.bukkit.event.block.BlockEvent> factory) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel) || !Bukkit.isPrimaryThread()) return placer.place(pos, block, flag);
        org.bukkit.craftbukkit.block.CraftBlockState state = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(world, pos, flag);
        state.setData(block);
        org.bukkit.event.block.BlockEvent event = factory.apply(state);
        Bukkit.getPluginManager().callEvent(event);
        if (((org.bukkit.event.Cancellable) event).isCancelled()) return false;
        return placer.place(pos, state.getHandle(), flag);
    }

    public static boolean callBlockFadeCancelled(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState newState) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel) || !Bukkit.isPrimaryThread()) return false;
        return callBlockFadeEvent(world, pos, newState).isCancelled();
    }

    public static org.bukkit.event.block.BlockFadeEvent callBlockFadeEvent(net.minecraft.world.level.LevelAccessor world,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState newState) {
        org.bukkit.craftbukkit.block.CraftBlockState state = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(world, pos);
        state.setData(newState);
        org.bukkit.event.block.BlockFadeEvent event = new org.bukkit.event.block.BlockFadeEvent(state.getBlock(), state);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.entity.EntityShootBowEvent callEntityShootBowEvent(net.minecraft.world.entity.LivingEntity who,
            net.minecraft.world.item.ItemStack bow, net.minecraft.world.item.ItemStack consumeItem, net.minecraft.world.entity.Entity projectile,
            net.minecraft.world.InteractionHand hand, float force, boolean consume) {
        CraftItemStack arrowItem = CraftItemStack.asCraftMirror(consumeItem);
        org.bukkit.event.entity.EntityShootBowEvent event = new org.bukkit.event.entity.EntityShootBowEvent(
                (org.bukkit.entity.LivingEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) who).lunararc$getBukkitEntity(),
                CraftItemStack.asCraftMirror(bow),
                arrowItem.getType() == org.bukkit.Material.AIR || arrowItem.getAmount() == 0 ? null : arrowItem,
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) projectile).lunararc$getBukkitEntity(),
                hand == net.minecraft.world.InteractionHand.MAIN_HAND ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND,
                force, consume);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static boolean dispenseEventFired = false;

    public static org.bukkit.event.block.BlockDispenseEvent callBlockDispenseEvent(net.minecraft.core.dispenser.BlockSource source,
            net.minecraft.world.item.ItemStack item, net.minecraft.world.phys.Vec3 velocity) {
        org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(CraftBlock.at(source.level(), source.pos()),
                CraftItemStack.asBukkitCopy(item), new org.bukkit.util.Vector(velocity.x, velocity.y, velocity.z));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.player.PlayerHarvestBlockEvent callPlayerHarvestBlockEvent(net.minecraft.world.level.Level world,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player who, net.minecraft.world.InteractionHand hand,
            java.util.List<net.minecraft.world.item.ItemStack> itemsToHarvest) {
        java.util.List<ItemStack> bukkitItems = new ArrayList<>(itemsToHarvest.size());
        for (net.minecraft.world.item.ItemStack item : itemsToHarvest) bukkitItems.add(CraftItemStack.asBukkitCopy(item));
        org.bukkit.event.player.PlayerHarvestBlockEvent event = new org.bukkit.event.player.PlayerHarvestBlockEvent(
                (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) who).lunararc$getBukkitEntity(),
                CraftBlock.at((net.minecraft.server.level.ServerLevel) world, pos),
                hand == net.minecraft.world.InteractionHand.OFF_HAND ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND,
                bukkitItems);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static org.bukkit.event.player.PlayerBucketEntityEvent callPlayerFishBucketEvent(net.minecraft.world.entity.LivingEntity fish,
            net.minecraft.world.entity.player.Player entityHuman, net.minecraft.world.item.ItemStack originalBucket,
            net.minecraft.world.item.ItemStack entityBucket, net.minecraft.world.InteractionHand hand) {
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entityHuman).lunararc$getBukkitEntity();
        org.bukkit.entity.Entity bukkitEntity = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) fish).lunararc$getBukkitEntity();
        org.bukkit.inventory.EquipmentSlot slot = hand == net.minecraft.world.InteractionHand.OFF_HAND
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND;
        org.bukkit.event.player.PlayerBucketEntityEvent event = bukkitEntity instanceof org.bukkit.entity.Fish bukkitFish
                ? new org.bukkit.event.player.PlayerBucketFishEvent(player, bukkitFish, CraftItemStack.asBukkitCopy(originalBucket), CraftItemStack.asBukkitCopy(entityBucket), slot)
                : new org.bukkit.event.player.PlayerBucketEntityEvent(player, bukkitEntity, CraftItemStack.asBukkitCopy(originalBucket), CraftItemStack.asBukkitCopy(entityBucket), slot);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static void callVehicleUpdateAndMove(net.minecraft.world.entity.Entity vehicle, @Nullable Location from) {
        if (vehicle.level().isClientSide
                || !(((io.lunararcdevs.lunararc.common.bridge.EntityBridge) vehicle).lunararc$getBukkitEntity() instanceof org.bukkit.entity.Vehicle bukkitVehicle)) {
            return;
        }
        Location to = bukkitVehicle.getLocation();
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleUpdateEvent(bukkitVehicle));
        if (from != null && !from.equals(to)) {
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleMoveEvent(bukkitVehicle, from, to));
        }
    }

    public static boolean callVehicleEntityCollisionCancelled(net.minecraft.world.entity.Entity vehicle, net.minecraft.world.entity.Entity other) {
        if (vehicle.level().isClientSide
                || !(((io.lunararcdevs.lunararc.common.bridge.EntityBridge) vehicle).lunararc$getBukkitEntity() instanceof org.bukkit.entity.Vehicle bukkitVehicle)) {
            return false;
        }
        var event = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(bukkitVehicle,
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) other).lunararc$getBukkitEntity());
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    public static boolean callPlayerSignOpenEvent(org.bukkit.entity.Player player, org.bukkit.block.Sign sign,
            org.bukkit.block.sign.Side side, org.bukkit.event.player.PlayerSignOpenEvent.Cause cause) {
        org.bukkit.event.player.PlayerSignOpenEvent event = new org.bukkit.event.player.PlayerSignOpenEvent(player, sign, side, cause);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    public static boolean callPlayerShearEntityCancelled(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity sheared,
            net.minecraft.world.item.ItemStack shears, net.minecraft.world.InteractionHand hand) {
        if (sheared.level().isClientSide
                || !(((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player).lunararc$getBukkitEntity() instanceof org.bukkit.entity.Player bukkitPlayer)) {
            return false;
        }
        java.util.List<org.bukkit.inventory.ItemStack> drops = new ArrayList<>();
        for (net.minecraft.world.item.ItemStack drop : io.lunararcdevs.lunararc.common.util.LunarArcShearing.defaultDrops(sheared, shears)) {
            drops.add(CraftItemStack.asBukkitCopy(drop));
        }
        org.bukkit.event.player.PlayerShearEntityEvent event = new org.bukkit.event.player.PlayerShearEntityEvent(bukkitPlayer,
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) sheared).lunararc$getBukkitEntity(), CraftItemStack.asCraftMirror(shears),
                hand == net.minecraft.world.InteractionHand.OFF_HAND ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND,
                drops);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            io.lunararcdevs.lunararc.common.util.LunarArcShearing.clear();
            return true;
        }
        if (io.lunararcdevs.lunararc.common.util.LunarArcShearing.handles(sheared)) {
            java.util.List<net.minecraft.world.item.ItemStack> result = new ArrayList<>();
            for (org.bukkit.inventory.ItemStack drop : event.getDrops()) {
                result.add(CraftItemStack.asNMSCopy(drop));
            }
            io.lunararcdevs.lunararc.common.util.LunarArcShearing.force(sheared, result);
        }
        return false;
    }

    public static org.bukkit.event.entity.ExplosionPrimeEvent callExplosionPrimeEvent(net.minecraft.world.entity.Entity entity, float radius, boolean fire) {
        org.bukkit.event.entity.ExplosionPrimeEvent event = new org.bukkit.event.entity.ExplosionPrimeEvent(
                ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity(), radius, fire);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static boolean callTNTPrimeEvent(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
            org.bukkit.event.block.TNTPrimeEvent.PrimeCause cause, @Nullable net.minecraft.world.entity.Entity causingEntity,
            @Nullable net.minecraft.core.BlockPos causePosition) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel level) || !Bukkit.isPrimaryThread()) return true;
        org.bukkit.entity.Entity bukkitEntity = causingEntity == null ? null
                : ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) causingEntity).lunararc$getBukkitEntity();
        Block causeBlock = causePosition == null ? null : CraftBlock.at(level, causePosition);
        org.bukkit.event.block.TNTPrimeEvent event = new org.bukkit.event.block.TNTPrimeEvent(
                CraftBlock.at(level, pos), cause, bukkitEntity, causeBlock);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason reason = switch (cause) {
            case FIRE -> com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.FIRE;
            case REDSTONE -> com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE;
            case PROJECTILE -> com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.PROJECTILE;
            case EXPLOSION -> com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.EXPLOSION;
            default -> com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.ITEM;
        };
        com.destroystokyo.paper.event.block.TNTPrimeEvent paperEvent =
                new com.destroystokyo.paper.event.block.TNTPrimeEvent(CraftBlock.at(level, pos), reason, bukkitEntity);
        Bukkit.getPluginManager().callEvent(paperEvent);
        return !paperEvent.isCancelled();
    }

    public static org.bukkit.event.block.BlockIgniteEvent callBlockIgniteEvent(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos ignited,
            net.minecraft.core.BlockPos source) {
        org.bukkit.World world =
                ((io.lunararcdevs.lunararc.common.bridge.LevelBridge) level).lunararc$getWorld();
        Block igniter = world.getBlockAt(source.getX(), source.getY(), source.getZ());

        org.bukkit.event.block.BlockIgniteEvent.IgniteCause cause = switch (igniter.getType()) {
            case LAVA -> org.bukkit.event.block.BlockIgniteEvent.IgniteCause.LAVA;
            case DISPENSER -> org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL;
            // Fire, or anything else that is not specifically recognised, counts as spread.
            default -> org.bukkit.event.block.BlockIgniteEvent.IgniteCause.SPREAD;
        };

        org.bukkit.event.block.BlockIgniteEvent event = new org.bukkit.event.block.BlockIgniteEvent(
                world.getBlockAt(ignited.getX(), ignited.getY(), ignited.getZ()), cause, igniter);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static boolean callEntityChangeBlockEvent(
            net.minecraft.world.entity.Entity entity,
            net.minecraft.core.BlockPos position,
            net.minecraft.world.level.block.state.BlockState newState) {
        return callEntityChangeBlockEvent(entity, position, newState, false);
    }

    /** As above, with the event pre-cancelled, which a few CraftBukkit call sites rely on. */
    public static boolean callEntityChangeBlockEvent(
            net.minecraft.world.entity.Entity entity,
            net.minecraft.core.BlockPos position,
            net.minecraft.world.level.block.state.BlockState newState,
            boolean cancelled) {
        Block block = ((io.lunararcdevs.lunararc.common.bridge.LevelBridge) entity.level())
                .lunararc$getWorld()
                .getBlockAt(position.getX(), position.getY(), position.getZ());

        org.bukkit.event.entity.EntityChangeBlockEvent event =
                new org.bukkit.event.entity.EntityChangeBlockEvent(
                        ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) entity).lunararc$getBukkitEntity(),
                        block,
                        org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(newState));
        event.setCancelled(cancelled);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }



    public record GameRuleSetResult<T>(T value, boolean cancelled) {
    }

    /**
     * Fires Paper's world gamerule change event. Returns the (possibly plugin-modified) value, or {@code null} when a
     * plugin cancelled the change.
     */
    public static <T> @org.jetbrains.annotations.Nullable T fireGameRuleChange(net.minecraft.world.level.gamerules.GameRule<T> rule, T value,
            net.minecraft.server.level.ServerLevel level, @org.jetbrains.annotations.Nullable org.bukkit.command.CommandSender sender) {
        String valueStr = rule.serialize(value);
        final var event = new io.papermc.paper.event.world.PaperWorldGameRuleChangeEvent(
                io.lunararcdevs.lunararc.common.LunarArcServerAccess.getCraftWorld(level),
                sender,
                org.bukkit.craftbukkit.CraftGameRule.minecraftToBukkit(rule),
                valueStr);
        if (!event.callEvent()) return null;
        if (!event.getValue().equals(valueStr)) {
            value = rule.deserialize(event.getValue()).getOrThrow();
        }
        return value;
    }

    public static <T> GameRuleSetResult<T> handleGameRuleSet(net.minecraft.world.level.gamerules.GameRule<T> rule, T value,
            net.minecraft.server.level.ServerLevel level, @org.jetbrains.annotations.Nullable org.bukkit.command.CommandSender sender) {
        T accepted = fireGameRuleChange(rule, value, level, sender);
        if (accepted == null) {
            return new GameRuleSetResult<>(level.getGameRules().get(rule), true);
        }
        level.getGameRules().set(rule, accepted, level.getServer());
        return new GameRuleSetResult<>(accepted, false);
    }
}
