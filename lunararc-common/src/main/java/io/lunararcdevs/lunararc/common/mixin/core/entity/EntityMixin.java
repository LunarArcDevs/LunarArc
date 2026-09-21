package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.lunararcdevs.lunararc.common.bridge.CommandSourceBridge;
import io.lunararcdevs.lunararc.common.bridge.EntityBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.persistence.PersistentDataContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityBridge, CommandSourceBridge {

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "handlePortal",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;canChangeDimensions(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/Level;)Z"),
            require = 0)
    private boolean lunararc$playersMayAlwaysChangeDimension(Entity entity, Level from, Level to,
            Operation<Boolean> original) {
        boolean vanilla = original.call(entity, from, to);
        if (vanilla || !((Object) this instanceof net.minecraft.server.level.ServerPlayer)) return vanilla;
        if (io.lunararcdevs.lunararc.common.LunarArcDebug.ENTITY) {
            io.lunararcdevs.lunararc.common.LunarArcDebug.entity(
                    "handlePortal: canChangeDimensions said no for a player going {} -> {}; allowing it",
                    from.dimension().location(), to.dimension().location());
        }
        return true;
    }

    @Shadow private int portalCooldown;
    @Shadow private int remainingFireTicks;
    @Shadow private boolean hasVisualFire;

    @Override public int lunararc$getPortalCooldown() { return this.portalCooldown; }
    @Override public void lunararc$setPortalCooldown(int cooldown) { this.portalCooldown = cooldown; }
    @Override public int lunararc$getRemainingFireTicks() { return this.remainingFireTicks; }
    @Override public void lunararc$setRemainingFireTicks(int ticks) { this.remainingFireTicks = ticks; }

    private org.bukkit.entity.Entity lunararc$bukkitEntity;
    private CreatureSpawnEvent.SpawnReason lunararc$spawnReason;
    private boolean lunararc$fromMobSpawner;
    private Vec3 lunararc$origin;
    private java.util.UUID lunararc$originWorld;
    private boolean lunararc$inWorld;
    private boolean lunararc$persistent = true;
    @Override public void lunararc$setBukkitEntity(org.bukkit.entity.Entity entity) { this.lunararc$bukkitEntity = entity; }
    @Override public org.bukkit.entity.Entity lunararc$peekBukkitEntity() { return this.lunararc$bukkitEntity; }
    @Override public CreatureSpawnEvent.SpawnReason lunararc$getSpawnReason() { return this.lunararc$spawnReason; }
    @Override public void lunararc$setSpawnReason(CreatureSpawnEvent.SpawnReason reason) { this.lunararc$spawnReason = reason; }
    @Override public boolean lunararc$fromMobSpawner() { return this.lunararc$fromMobSpawner; }
    @Override public void lunararc$setFromMobSpawner(boolean fromMobSpawner) { this.lunararc$fromMobSpawner = fromMobSpawner; }
    @Override public Vec3 lunararc$getOrigin() { return this.lunararc$origin; }
    @Override public java.util.UUID lunararc$getOriginWorld() { return this.lunararc$originWorld; }
    @Override public void lunararc$setOrigin(Vec3 origin, java.util.UUID worldId) {
        this.lunararc$origin = origin;
        this.lunararc$originWorld = worldId;
    }
    @Override public boolean lunararc$isInWorld() { return this.lunararc$inWorld; }
    @Override public void lunararc$setInWorld(boolean inWorld) { this.lunararc$inWorld = inWorld; }
    @Override public boolean lunararc$isVisualFire() { return this.hasVisualFire; }
    @Override public void lunararc$setVisualFire(boolean visualFire) { this.hasVisualFire = visualFire; }
    @Override public boolean lunararc$isPersistent() { return this.lunararc$persistent; }
    @Override public void lunararc$setPersistent(boolean persistent) { this.lunararc$persistent = persistent; }
    @Override public void lunararc$setLevel(Level level) { ((Entity) (Object) this).setLevel(level); }
    @Override public boolean lunararc$saveAsPassenger(CompoundTag tag) { return ((Entity) (Object) this).saveAsPassenger(tag); }

    private CraftPersistentDataContainer lunararc$pdc = new CraftPersistentDataContainer();
    @Override public PersistentDataContainer lunararc$getPersistentDataContainer() { return lunararc$pdc; }
    @Override public org.bukkit.entity.Entity lunararc$getBukkitEntity() {
        if (lunararc$bukkitEntity == null) {
            net.minecraft.server.MinecraftServer minecraftServer = ((Entity) (Object) this).getServer();
            if (minecraftServer == null) {
                throw new IllegalStateException("Cannot create a Bukkit entity wrapper without a server");
            }
            lunararc$bukkitEntity = CraftEntity.getEntity(
                    io.lunararcdevs.lunararc.common.LunarArcServerAccess.getCraftServer(minecraftServer),
                    (Entity) (Object) this);
        }
        return lunararc$bukkitEntity;
    }

    public CraftEntity getBukkitEntity() {
        return (CraftEntity) this.lunararc$getBukkitEntity();
    }


    @Override
    public org.bukkit.command.CommandSender lunararc$getBukkitSender(net.minecraft.commands.CommandSourceStack stack) {
        org.bukkit.entity.Entity entity = this.lunararc$getBukkitEntity();
        if (entity instanceof org.bukkit.command.CommandSender sender) {
            return sender;
        }
        throw new IllegalStateException("Entity command source is not a Bukkit CommandSender: " + entity.getType());
    }

    @Inject(method = "load", at = @At("RETURN"), require = 0)
    private void lunararc$loadEntityData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("BukkitValues")) {
            lunararc$pdc.fromTag(tag.getCompound("BukkitValues"));
        }
        this.lunararc$fromMobSpawner = tag.getBoolean("Paper.FromMobSpawner");
        if (tag.contains("Paper.SpawnReason")) {
            String name = tag.getString("Paper.SpawnReason");
            try {
                this.lunararc$spawnReason = CreatureSpawnEvent.SpawnReason.valueOf(name);
            } catch (IllegalArgumentException exception) {
                this.lunararc$spawnReason = CreatureSpawnEvent.SpawnReason.DEFAULT;
            }
        }
        ListTag origin = tag.getList("Paper.Origin", 6);
        if (!origin.isEmpty() && origin.size() >= 3) {
            this.lunararc$origin = new Vec3(origin.getDouble(0), origin.getDouble(1), origin.getDouble(2));
            this.lunararc$originWorld = tag.hasUUID("Paper.OriginWorld") ? tag.getUUID("Paper.OriginWorld") : null;
        }
        this.lunararc$persistent = !tag.contains("Bukkit.persist") || tag.getBoolean("Bukkit.persist");
        if (this.lunararc$spawnReason == null) {
            Entity entity = (Entity) (Object) this;
            if (this.lunararc$fromMobSpawner) {
                this.lunararc$spawnReason = CreatureSpawnEvent.SpawnReason.SPAWNER;
            } else if (entity instanceof Mob mob
                    && (entity instanceof Animal || entity instanceof AbstractFish)
                    && !mob.removeWhenFarAway(0.0)
                    && !tag.getBoolean("PersistenceRequired")) {
                this.lunararc$spawnReason = CreatureSpawnEvent.SpawnReason.NATURAL;
            } else {
                this.lunararc$spawnReason = CreatureSpawnEvent.SpawnReason.DEFAULT;
            }
        }
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"), require = 0)
    private void lunararc$saveEntityData(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag bukkitValues = lunararc$pdc.toTag();
        if (!bukkitValues.isEmpty()) {
            tag.put("BukkitValues", bukkitValues);
        }
        if (!this.lunararc$persistent) {
            tag.putBoolean("Bukkit.persist", false);
        }
        if (this.lunararc$spawnReason != null) {
            tag.putString("Paper.SpawnReason", this.lunararc$spawnReason.name());
        }
        if (this.lunararc$fromMobSpawner) {
            tag.putBoolean("Paper.FromMobSpawner", true);
        }
        if (this.lunararc$origin != null) {
            if (this.lunararc$originWorld != null) {
                tag.putUUID("Paper.OriginWorld", this.lunararc$originWorld);
            }
            ListTag origin = new ListTag();
            origin.add(net.minecraft.nbt.DoubleTag.valueOf(this.lunararc$origin.x));
            origin.add(net.minecraft.nbt.DoubleTag.valueOf(this.lunararc$origin.y));
            origin.add(net.minecraft.nbt.DoubleTag.valueOf(this.lunararc$origin.z));
            tag.put("Paper.Origin", origin);
        }
    }
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$vehicleEnter(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) return;
        org.bukkit.entity.Entity bukkitVehicle = ((EntityBridge) vehicle).lunararc$getBukkitEntity();
        org.bukkit.entity.Entity bukkitPassenger = this.lunararc$getBukkitEntity();
        if (bukkitVehicle instanceof org.bukkit.entity.Vehicle bukkitVehicleEntity
                && bukkitPassenger instanceof org.bukkit.entity.LivingEntity livingPassenger) {
            org.bukkit.event.vehicle.VehicleEnterEvent event =
                    new org.bukkit.event.vehicle.VehicleEnterEvent(bukkitVehicleEntity, livingPassenger);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "removePassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$vehicleExit(Entity passenger, CallbackInfo ci) {
        // Same reasoning as lunararc$vehicleEnter above.
        if (!org.bukkit.Bukkit.isPrimaryThread()) return;
        org.bukkit.entity.Entity bukkitVehicle = this.lunararc$getBukkitEntity();
        org.bukkit.entity.Entity bukkitPassenger = ((EntityBridge) passenger).lunararc$getBukkitEntity();

        io.lunararcdevs.lunararc.common.compat.LunarArcDismountEvents.fireEntityDismount(bukkitVehicle, bukkitPassenger);

        if (bukkitVehicle instanceof org.bukkit.entity.Vehicle bukkitVehicleEntity
                && bukkitPassenger instanceof org.bukkit.entity.LivingEntity livingPassenger) {
            org.bukkit.event.vehicle.VehicleExitEvent event =
                    new org.bukkit.event.vehicle.VehicleExitEvent(bukkitVehicleEntity, livingPassenger);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Unique private boolean lunararc$dropLeashOnPlayerUnleash;

    @Inject(
            method = "interact",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Leashable;dropLeash(ZZ)V"),
            cancellable = true,
            require = 0)
    private void lunararc$playerUnleash(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        Entity self = (Entity) (Object) this;
        Object bukkit = this.lunararc$getBukkitEntity();
        Object bukkitPlayer = ((EntityBridge) player).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.entity.Entity bukkitEntity)
                || !(bukkitPlayer instanceof org.bukkit.entity.Player human)) {
            return;
        }
        var event = new org.bukkit.event.player.PlayerUnleashEntityEvent(
                bukkitEntity, human, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand),
                !player.hasInfiniteMaterials());
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && self instanceof net.minecraft.world.entity.Leashable leashable) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(
                        self, leashable.getLeashHolder()));
            }
            cir.setReturnValue(net.minecraft.world.InteractionResult.PASS);
            return;
        }
        this.lunararc$dropLeashOnPlayerUnleash = event.isDropLeash();
    }

    @ModifyArg(
            method = "interact",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Leashable;dropLeash(ZZ)V"),
            index = 1,
            require = 0)
    private boolean lunararc$playerUnleashDrop(boolean vanilla) {
        boolean result = this.lunararc$dropLeashOnPlayerUnleash;
        this.lunararc$dropLeashOnPlayerUnleash = false;
        return result;
    }

    @WrapMethod(method = "igniteForSeconds")
    private void lunararc$combust(float seconds, Operation<Void> original) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide || !org.bukkit.Bukkit.isPrimaryThread()) {
            original.call(seconds);
            return;
        }
        var event = new org.bukkit.event.entity.EntityCombustEvent(this.lunararc$getBukkitEntity(), seconds);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            original.call(event.getDuration());
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), require = 0)
    private void lunararc$markRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        this.lunararc$inWorld = false;
    }



}
