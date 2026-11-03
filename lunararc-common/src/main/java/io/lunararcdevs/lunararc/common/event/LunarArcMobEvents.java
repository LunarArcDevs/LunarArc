package io.lunararcdevs.lunararc.common.event;

import io.lunararcdevs.lunararc.common.LunarArcServerAccess;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Mob and entity-related Paper/Bukkit events that are not part of the main event classes. */
public final class LunarArcMobEvents {
    private LunarArcMobEvents() {}

    private static boolean listened(HandlerList handlers) {
        return handlers.getRegisteredListeners().length != 0;
    }

    private static boolean fire(Event event) {
        LunarArcServerAccess.getCraftServer().getPluginManager().callEvent(event);
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    private static Object bukkit(Entity entity) {
        return entity == null ? null : ((EntityBridge) entity).lunararc$getBukkitEntity();
    }

    private static boolean ready(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel && org.bukkit.Bukkit.isPrimaryThread();
    }

    // ---- ender dragon ----

    /** The dragon breathes flame onto the ground; returns false if a plugin cancelled it. */
    public static boolean dragonFlame(AreaEffectCloud flame) {
        if (!ready(flame) || !listened(com.destroystokyo.paper.event.entity.EnderDragonFlameEvent.getHandlerList())) return true;
        if (!(bukkit(flame.getOwner()) instanceof org.bukkit.entity.EnderDragon dragon) || !(bukkit(flame) instanceof org.bukkit.entity.AreaEffectCloud cloud)) return true;
        return !fire(new com.destroystokyo.paper.event.entity.EnderDragonFlameEvent(dragon, cloud));
    }

    /** The dragon shoots a fireball; returns false if a plugin cancelled it. */
    public static boolean dragonShoot(DragonFireball fireball) {
        if (!ready(fireball) || !listened(com.destroystokyo.paper.event.entity.EnderDragonShootFireballEvent.getHandlerList())) return true;
        if (!(bukkit(fireball.getOwner()) instanceof org.bukkit.entity.EnderDragon dragon) || !(bukkit(fireball) instanceof org.bukkit.entity.DragonFireball bukkitBall)) return true;
        return !fire(new com.destroystokyo.paper.event.entity.EnderDragonShootFireballEvent(dragon, bukkitBall));
    }

    /** A dragon fireball landed and made a cloud; returns false if a plugin cancelled the cloud. */
    public static boolean dragonFireballHit(DragonFireball fireball, List<LivingEntity> hit, AreaEffectCloud cloud) {
        if (!ready(fireball) || !listened(com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent.getHandlerList())) return true;
        if (!(bukkit(fireball) instanceof org.bukkit.entity.DragonFireball bukkitBall) || !(bukkit(cloud) instanceof org.bukkit.entity.AreaEffectCloud bukkitCloud)) return true;
        List<org.bukkit.entity.LivingEntity> affected = new ArrayList<>();
        for (LivingEntity entity : hit) if (bukkit(entity) instanceof org.bukkit.entity.LivingEntity living) affected.add(living);
        return !fire(new com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent(bukkitBall, affected, bukkitCloud));
    }

    // ---- pathfinding ----

    public static boolean pathfindListened() {
        return listened(com.destroystokyo.paper.event.entity.EntityPathfindEvent.getHandlerList());
    }

    /** A mob wants to path to a position; returns false if a plugin cancelled that target. */
    public static boolean pathfind(Mob mob, BlockPos target, Entity targetEntity) {
        if (!ready(mob) || !pathfindListened()) return true;
        if (!(bukkit(mob) instanceof org.bukkit.entity.Entity bukkitMob)) return true;
        org.bukkit.Location location = new org.bukkit.Location(
                LunarArcServerAccess.getCraftWorld((ServerLevel) mob.level()), target.getX(), target.getY(), target.getZ());
        org.bukkit.entity.Entity bukkitTarget = targetEntity == null ? null : (org.bukkit.entity.Entity) bukkit(targetEntity);
        return !fire(new com.destroystokyo.paper.event.entity.EntityPathfindEvent(bukkitMob, location, bukkitTarget));
    }

    // ---- spawners, loot, spells ----

    /** A trial spawner spawned a mob; returns false if a plugin cancelled it. */
    public static boolean trialSpawn(Entity entity, BlockPos spawnerPos) {
        if (!ready(entity) || !listened(org.bukkit.event.entity.TrialSpawnerSpawnEvent.getHandlerList())) return true;
        if (!(bukkit(entity) instanceof org.bukkit.entity.Entity bukkitEntity)) return true;
        org.bukkit.block.BlockState state = CraftBlock.at((ServerLevel) entity.level(), spawnerPos).getState();
        org.bukkit.block.TrialSpawner spawner = state instanceof org.bukkit.block.TrialSpawner found ? found : null;
        return !fire(new org.bukkit.event.entity.TrialSpawnerSpawnEvent(bukkitEntity, spawner));
    }

    /**
     * A vault or trial spawner is about to hand out loot. Returns the loot to give (a plugin may change it), or
     * null if a plugin cancelled it.
     */
    public static List<ItemStack> dispenseLoot(ServerLevel level, BlockPos pos, Player who, List<ItemStack> loot, ResourceKey<LootTable> table) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(org.bukkit.event.block.BlockDispenseLootEvent.getHandlerList())) return loot;
        List<org.bukkit.inventory.ItemStack> items = loot.stream().map(CraftItemStack::asBukkitCopy).collect(Collectors.toList());
        org.bukkit.entity.Player player = who instanceof ServerPlayer && bukkit(who) instanceof org.bukkit.entity.Player found ? found : null;
        org.bukkit.event.block.BlockDispenseLootEvent event = new org.bukkit.event.block.BlockDispenseLootEvent(
                player, CraftBlock.at(level, pos), items, org.bukkit.craftbukkit.CraftLootTable.minecraftToBukkit(table.identifier()));
        if (fire(event)) return null;
        return event.getDispensedLoot().stream().map(CraftItemStack::asNMSCopy).collect(Collectors.toList());
    }

    /** An illager is about to cast a spell; returns false if a plugin cancelled it. */
    public static boolean spellCast(SpellcasterIllager caster, SpellcasterIllager.IllagerSpell spell) {
        if (!ready(caster) || !listened(org.bukkit.event.entity.EntitySpellCastEvent.getHandlerList())) return true;
        if (!(bukkit(caster) instanceof org.bukkit.entity.Spellcaster bukkitCaster)) return true;
        return !fire(new org.bukkit.event.entity.EntitySpellCastEvent(bukkitCaster, org.bukkit.craftbukkit.entity.CraftSpellcaster.toBukkitSpell(spell)));
    }
    // ---- projectiles, combustion, spawning ----

    /** Paper's legacy ProjectileCollideEvent; returns true if the collision was cancelled. */
    @SuppressWarnings("deprecation")
    public static boolean projectileCollide(org.bukkit.entity.Projectile projectile, org.bukkit.entity.Entity collided) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(com.destroystokyo.paper.event.entity.ProjectileCollideEvent.getHandlerList())) return false;
        com.destroystokyo.paper.event.entity.ProjectileCollideEvent event =
                new com.destroystokyo.paper.event.entity.ProjectileCollideEvent(projectile, collided);
        if (projectile.getShooter() instanceof org.bukkit.entity.Player shooter && collided instanceof org.bukkit.entity.Player target
                && !shooter.canSee(target)) {
            return true;
        }
        return fire(event);
    }

    /** True while the by-block combust event is the one in charge, so the generic combust hook stays quiet. */
    public static boolean combustSuppressed;

    /**
     * An entity is being set on fire by a block. Returns the burn time in seconds to use, or a negative number
     * if a plugin cancelled it.
     */
    public static float combustByBlock(Entity entity, float seconds) {
        if (!ready(entity) || !listened(org.bukkit.event.entity.EntityCombustEvent.getHandlerList())) return seconds;
        if (!(bukkit(entity) instanceof org.bukkit.entity.Entity bukkitEntity)) return seconds;
        org.bukkit.event.entity.EntityCombustByBlockEvent event = new org.bukkit.event.entity.EntityCombustByBlockEvent(null, bukkitEntity, seconds);
        if (fire(event)) return -1.0F;
        return event.getDuration();
    }

    /** The Bukkit type for a vanilla entity type, or null for modded types that have none. */
    private static org.bukkit.entity.EntityType bukkitEntityType(net.minecraft.world.entity.EntityType<?> type) {
        try {
            return org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(type);
        } catch (IllegalArgumentException modded) {
            return null;
        }
    }

    /** A mob is about to be spawned at a position; returns false if a plugin cancelled it. */
    public static boolean preCreatureSpawn(ServerLevel level, BlockPos pos, net.minecraft.world.entity.EntityType<?> type,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent.getHandlerList())) return true;
        org.bukkit.entity.EntityType bukkitType = bukkitEntityType(type);
        if (bukkitType == null) return true;
        org.bukkit.Location location = new org.bukkit.Location(LunarArcServerAccess.getCraftWorld(level), pos.getX(), pos.getY(), pos.getZ());
        return !fire(new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(location, bukkitType, reason));
    }
    /** Maps vanilla's spawn reason to Bukkit's, honouring a reason a plugin or hook already set. */
    public static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason bukkitReason(net.minecraft.world.entity.EntitySpawnReason type) {
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason override = LunarArcSpawnReasons.get();
        if (override != null) return override;
        return switch (type) {
            case NATURAL -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL;
            case CHUNK_GENERATION, STRUCTURE -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN;
            case SPAWNER -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
            case BREEDING -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING;
            case JOCKEY -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.JOCKEY;
            case REINFORCEMENT -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS;
            case SPAWN_ITEM_USE -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG;
            case COMMAND -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND;
            case DISPENSER -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DISPENSE_EGG;
            case PATROL -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL;
            case TRIAL_SPAWNER -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER;
            default -> org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
        };
    }

    /** A spawner is about to spawn a mob; returns false if a plugin cancelled it. */
    public static boolean preSpawnerSpawn(ServerLevel level, net.minecraft.world.phys.Vec3 spawnAt, net.minecraft.world.entity.EntityType<?> type, BlockPos spawner) {
        if (!org.bukkit.Bukkit.isPrimaryThread() || !listened(com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent.getHandlerList())) return true;
        org.bukkit.entity.EntityType bukkitType = bukkitEntityType(type);
        if (bukkitType == null) return true;
        org.bukkit.World world = LunarArcServerAccess.getCraftWorld(level);
        return !fire(new com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent(
                new org.bukkit.Location(world, spawnAt.x, spawnAt.y, spawnAt.z), bukkitType,
                new org.bukkit.Location(world, spawner.getX(), spawner.getY(), spawner.getZ())));
    }
    // ---- knockback pushes, portals, item transporters ----

    /** The entity whose attack is currently pushing others, set by the attack hooks around their push calls. */
    public static Entity pusher;

    public static net.minecraft.world.phys.Vec3 pushedByAttack(Entity target, Entity attacker, double x, double y, double z) {
        net.minecraft.world.phys.Vec3 same = new net.minecraft.world.phys.Vec3(x, y, z);
        if (attacker == null || !ready(target) || !listened(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent.getHandlerList())) return same;
        if (!(bukkit(target) instanceof org.bukkit.entity.Entity bukkitTarget) || !(bukkit(attacker) instanceof org.bukkit.entity.Entity bukkitAttacker)) return same;
        io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event = new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(
                bukkitTarget, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.PUSH, bukkitAttacker, new org.bukkit.util.Vector(x, y, z));
        if (fire(event)) return null;
        org.bukkit.util.Vector changed = event.getAcceleration();
        return new net.minecraft.world.phys.Vec3(changed.getX(), changed.getY(), changed.getZ());
    }

    public static net.minecraft.world.level.portal.TeleportTransition endGateway(ServerLevel level, Entity entity, BlockPos pos,
            net.minecraft.world.level.portal.TeleportTransition transition) {
        boolean player = entity instanceof net.minecraft.server.level.ServerPlayer;
        if (transition == null || !ready(entity)
                || !listened(player ? com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent.getHandlerList()
                        : com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent.getHandlerList())) return transition;
        if (!(bukkit(entity) instanceof org.bukkit.entity.Entity bukkitEntity)) return transition;
        org.bukkit.World toWorld = LunarArcServerAccess.getCraftWorld(transition.newLevel());
        if (toWorld == null || !(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos).getState() instanceof org.bukkit.block.EndGateway gateway)) {
            return transition;
        }
        org.bukkit.Location from = bukkitEntity.getLocation();
        org.bukkit.Location to = new org.bukkit.Location(toWorld, transition.position().x, transition.position().y, transition.position().z,
                transition.yRot(), transition.xRot());
        org.bukkit.Location changed;
        if (player) {
            com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent event =
                    new com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent((org.bukkit.entity.Player) bukkitEntity, from, to, gateway);
            if (fire(event)) return null;
            changed = event.getTo();
        } else {
            com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent event =
                    new com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent(bukkitEntity, from, to, gateway);
            if (fire(event)) return null;
            changed = event.getTo();
        }
        if (changed == null) return null;
        if (changed.getX() == to.getX() && changed.getY() == to.getY() && changed.getZ() == to.getZ()
                && changed.getYaw() == to.getYaw() && changed.getPitch() == to.getPitch()) return transition;
        return transition.withPosition(new net.minecraft.world.phys.Vec3(changed.getX(), changed.getY(), changed.getZ()))
                .withRotation(changed.getYaw(), changed.getPitch());
    }

    /** An entity is about to go through a Nether portal; returns false if a plugin cancelled it. */
    public static boolean portalReady(Entity entity, ServerLevel destination) {
        if (!ready(entity) || !listened(io.papermc.paper.event.entity.EntityPortalReadyEvent.getHandlerList())) return true;
        if (!(bukkit(entity) instanceof org.bukkit.entity.Entity bukkitEntity)) return true;
        org.bukkit.World world = destination == null ? null : LunarArcServerAccess.getCraftWorld(destination);
        return !fire(new io.papermc.paper.event.entity.EntityPortalReadyEvent(bukkitEntity, world, org.bukkit.PortalType.NETHER));
    }

    /** A vehicle hit a block sideways; {@code requested} is the movement it asked for, {@code collided} what actually happened. */
    public static void vehicleBlockCollision(Entity vehicle, net.minecraft.world.phys.Vec3 intended, net.minecraft.world.phys.Vec3 requested,
            net.minecraft.world.phys.Vec3 collided) {
        if (!ready(vehicle) || !listened(org.bukkit.event.vehicle.VehicleBlockCollisionEvent.getHandlerList())) return;
        if (!(bukkit(vehicle) instanceof org.bukkit.entity.Vehicle bukkitVehicle)) return;
        org.bukkit.World world = LunarArcServerAccess.getCraftWorld((ServerLevel) vehicle.level());
        org.bukkit.block.Block block = world.getBlockAt(net.minecraft.util.Mth.floor(vehicle.getX()), net.minecraft.util.Mth.floor(vehicle.getY()),
                net.minecraft.util.Mth.floor(vehicle.getZ()));
        if (requested.x > collided.x) block = block.getRelative(org.bukkit.block.BlockFace.EAST);
        else if (requested.x < collided.x) block = block.getRelative(org.bukkit.block.BlockFace.WEST);
        else if (requested.z > collided.z) block = block.getRelative(org.bukkit.block.BlockFace.SOUTH);
        else if (requested.z < collided.z) block = block.getRelative(org.bukkit.block.BlockFace.NORTH);
        if (block.getType().isAir()) return;
        fire(new org.bukkit.event.vehicle.VehicleBlockCollisionEvent(bukkitVehicle, block,
                new org.bukkit.util.Vector(intended.x, intended.y, intended.z)));
    }

    /** An item-carrying mob (an allay or similar) is checking a container as a target; returns whether it is allowed. */
    public static boolean transportTarget(Mob mob, net.minecraft.world.level.Level level, BlockPos target) {
        if (!ready(mob) || !listened(io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent.getHandlerList())) return true;
        if (!(bukkit(mob) instanceof org.bukkit.entity.Mob bukkitMob) || !(level instanceof ServerLevel server)) return true;
        io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent event =
                new io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent(bukkitMob, CraftBlock.at(server, target));
        fire(event);
        return event.isAllowed();
    }
}
