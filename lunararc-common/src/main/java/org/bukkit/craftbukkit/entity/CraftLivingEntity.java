package org.bukkit.craftbukkit.entity;

import io.lunararcdevs.lunararc.common.bridge.ItemStackBridge;
import io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge;
import io.lunararcdevs.lunararc.common.bridge.access.EntityAccessBridge;
import io.lunararcdevs.lunararc.common.bridge.access.LivingEntityAccessBridge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.attribute.CraftAttributeInstance;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.memory.CraftMemoryKey;
import org.bukkit.craftbukkit.entity.memory.CraftMemoryMapper;
import org.bukkit.craftbukkit.inventory.CraftEntityEquipment;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CraftLivingEntity extends CraftEntity implements LivingEntity {
    private final @Nullable CraftEntityEquipment equipment;

    protected CraftLivingEntity(CraftServer server, net.minecraft.world.entity.LivingEntity entity) {
        super(server, entity);
        this.equipment = (entity instanceof net.minecraft.world.entity.Mob || entity instanceof net.minecraft.world.entity.decoration.ArmorStand) ? new CraftEntityEquipment(this) : null;
    }

    @Override
    public net.minecraft.world.entity.LivingEntity getHandle() {
        return (net.minecraft.world.entity.LivingEntity) this.entity;
    }

    private LivingEntityBridge livingBridge() {
        return (LivingEntityBridge) getHandle();
    }

    private LivingEntityAccessBridge livingAccessor() {
        return (LivingEntityAccessBridge) getHandle();
    }

    @Override public double getHealth() { return Math.min(Math.max(0.0D, getHandle().getHealth()), getMaxHealth()); }

    @Override
    public void setHealth(double health) {
        if (!Double.isFinite(health) || health < 0.0D || health > getMaxHealth()) {
            throw new IllegalArgumentException("Health must be finite and between 0 and " + getMaxHealth());
        }
        getHandle().setHealth((float) health);
        if (health == 0.0D) {
            getHandle().die(getHandle().damageSources().generic());
        }
    }

    @Override
    public void heal(double amount, EntityRegainHealthEvent.@NotNull RegainReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!Double.isFinite(amount) || amount < 0.0D) throw new IllegalArgumentException("amount must be finite and >= 0");
        getHandle().heal((float) amount);
    }

    @Override public double getAbsorptionAmount() { return getHandle().getAbsorptionAmount(); }
    @Override public void setAbsorptionAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) throw new IllegalArgumentException("amount must be finite and >= 0");
        getHandle().setAbsorptionAmount((float) amount);
    }
    @Override public double getMaxHealth() { return getHandle().getMaxHealth(); }
    @Override public void setMaxHealth(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) throw new IllegalArgumentException("Max health must be finite and > 0");
        AttributeInstance attribute = getHandle().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attribute == null) throw new IllegalStateException("Living entity has no MAX_HEALTH attribute");
        attribute.setBaseValue(amount);
        if (getHealth() > amount) setHealth(amount);
    }
    @Override public void resetMaxHealth() {
        Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
        setMaxHealth(holder.value().getDefaultValue());
    }

    @Override public double getEyeHeight() { return getHandle().getEyeHeight(); }
    @Override public double getEyeHeight(boolean ignorePose) { return getEyeHeight(); }
    @Override public @NotNull Location getEyeLocation() {
        Location location = getLocation();
        location.setY(location.getY() + getEyeHeight());
        return location;
    }

    @Override
    public @NotNull List<Block> getLineOfSight(@Nullable Set<Material> transparent, int maxDistance) {
        if (maxDistance < 0) throw new IllegalArgumentException("maxDistance must be >= 0");
        if (maxDistance > 120) maxDistance = 120;
        List<Block> blocks = new ArrayList<>();
        BlockIterator iterator = new BlockIterator(this, maxDistance);
        while (iterator.hasNext()) {
            Block block = iterator.next();
            blocks.add(block);
            Material material = block.getType();
            boolean passThrough = transparent == null ? material.isAir() : transparent.contains(material);
            if (!passThrough) break;
        }
        return blocks;
    }

    @Override public @NotNull Block getTargetBlock(@Nullable Set<Material> transparent, int maxDistance) {
        List<Block> blocks = getLineOfSight(transparent, maxDistance);
        return blocks.isEmpty() ? getEyeLocation().getBlock() : blocks.getLast();
    }
    @Override public @NotNull List<Block> getLastTwoTargetBlocks(@Nullable Set<Material> transparent, int maxDistance) {
        List<Block> blocks = getLineOfSight(transparent, maxDistance);
        return new ArrayList<>(blocks.subList(Math.max(0, blocks.size() - 2), blocks.size()));
    }
    @Override public @Nullable Block getTargetBlockExact(int maxDistance) { return getTargetBlockExact(maxDistance, FluidCollisionMode.NEVER); }
    @Override public @Nullable Block getTargetBlockExact(int maxDistance, @NotNull FluidCollisionMode fluidMode) {
        RayTraceResult result = rayTraceBlocks(maxDistance, fluidMode);
        return result == null ? null : result.getHitBlock();
    }
    @Override public @Nullable RayTraceResult rayTraceBlocks(double maxDistance) { return rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER); }
    @Override public @Nullable RayTraceResult rayTraceBlocks(double maxDistance, @NotNull FluidCollisionMode fluidMode) {
        Objects.requireNonNull(fluidMode, "fluidMode");
        return getWorld().rayTraceBlocks(getEyeLocation(), getEyeLocation().getDirection(), maxDistance, fluidMode, false);
    }

    // TargetBlockInfo and its FluidMode are deprecated for removal in the Paper API, but
    // LivingEntity still declares these overloads, so an implementation has to provide them -
    // real Paper's own CraftLivingEntity implements exactly the same four members. Suppressed
    // per-method rather than by disabling the javac `removal` lint for the whole build, so a
    // genuinely accidental use of a deprecated-for-removal API elsewhere still gets flagged.
    @SuppressWarnings("removal")
    @Override public @Nullable Block getTargetBlock(int maxDistance, com.destroystokyo.paper.block.TargetBlockInfo.@NotNull FluidMode fluidMode) {
        return getTargetBlockExact(maxDistance, paperFluidMode(fluidMode));
    }
    @SuppressWarnings("removal")
    @Override public @Nullable org.bukkit.block.BlockFace getTargetBlockFace(int maxDistance, com.destroystokyo.paper.block.TargetBlockInfo.@NotNull FluidMode fluidMode) {
        return getTargetBlockFace(maxDistance, paperFluidMode(fluidMode));
    }
    @Override public @Nullable org.bukkit.block.BlockFace getTargetBlockFace(int maxDistance, @NotNull FluidCollisionMode fluidMode) {
        RayTraceResult result = rayTraceBlocks(maxDistance, fluidMode);
        return result == null ? null : result.getHitBlockFace();
    }
    @SuppressWarnings("removal")
    @Override public @Nullable com.destroystokyo.paper.block.TargetBlockInfo getTargetBlockInfo(int maxDistance, com.destroystokyo.paper.block.TargetBlockInfo.@NotNull FluidMode fluidMode) {
        RayTraceResult result = rayTraceBlocks(maxDistance, paperFluidMode(fluidMode));
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) return null;
        return new com.destroystokyo.paper.block.TargetBlockInfo(result.getHitBlock(), result.getHitBlockFace());
    }

    @Override public @Nullable Entity getTargetEntity(int maxDistance, boolean ignoreBlocks) {
        RayTraceResult result = rayTraceEntities(maxDistance, ignoreBlocks);
        return result == null ? null : result.getHitEntity();
    }
    @Override public @Nullable com.destroystokyo.paper.entity.TargetEntityInfo getTargetEntityInfo(int maxDistance, boolean ignoreBlocks) {
        RayTraceResult result = rayTraceEntities(maxDistance, ignoreBlocks);
        if (result == null || result.getHitEntity() == null || result.getHitPosition() == null) return null;
        return new com.destroystokyo.paper.entity.TargetEntityInfo(result.getHitEntity(), result.getHitPosition());
    }
    @Override public @Nullable RayTraceResult rayTraceEntities(int maxDistance, boolean ignoreBlocks) {
        RayTraceResult entityHit = getWorld().rayTraceEntities(getEyeLocation(), getEyeLocation().getDirection(), maxDistance, 0.0D, candidate -> candidate != this);
        if (entityHit == null || ignoreBlocks) return entityHit;
        RayTraceResult blockHit = rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER);
        if (blockHit == null || blockHit.getHitPosition() == null || entityHit.getHitPosition() == null) return entityHit;
        Vector eye = getEyeLocation().toVector();
        return blockHit.getHitPosition().distanceSquared(eye) < entityHit.getHitPosition().distanceSquared(eye) ? null : entityHit;
    }

    @SuppressWarnings("removal")
    private static FluidCollisionMode paperFluidMode(com.destroystokyo.paper.block.TargetBlockInfo.FluidMode mode) {
        return switch (mode) {
            case ALWAYS -> FluidCollisionMode.ALWAYS;
            case SOURCE_ONLY -> FluidCollisionMode.SOURCE_ONLY;
            case NEVER -> FluidCollisionMode.NEVER;
        };
    }

    @Override public int getRemainingAir() { return getHandle().getAirSupply(); }
    @Override public void setRemainingAir(int ticks) { getHandle().setAirSupply(ticks); }
    @Override public int getMaximumAir() {
        int override = livingBridge().lunararc$getMaximumAirOverride();
        return override >= 0 ? override : getHandle().getMaxAirSupply();
    }
    @Override public void setMaximumAir(int ticks) {
        livingBridge().lunararc$setMaximumAirOverride(ticks);
        if (getRemainingAir() > ticks) setRemainingAir(ticks);
    }

    @Override public @Nullable ItemStack getItemInUse() {
        net.minecraft.world.item.ItemStack item = getHandle().getUseItem();
        return item.isEmpty() ? null : CraftItemStack.asBukkitCopy(item);
    }
    @Override public int getItemInUseTicks() { return getHandle().getUseItemRemainingTicks(); }
    @Override public void setItemInUseTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        livingAccessor().lunararc$setUseItemRemaining(ticks);
    }
    @Override public int getArrowCooldown() { return livingAccessor().lunararc$getRemoveArrowTime(); }
    @Override public void setArrowCooldown(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        livingAccessor().lunararc$setRemoveArrowTime(ticks);
    }
    @Override public int getArrowsInBody() { return getHandle().getArrowCount(); }
    @Override public void setArrowsInBody(int count, boolean fireEvent) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        if (fireEvent) getHandle().setArrowCount(count);
        else getHandle().getEntityData().set(((LivingEntityBridge) (Object) getHandle()).lunararc$getArrowCountDataAccessorBridge(), count);
    }
    @Override public void setNextArrowRemoval(int ticks) { setArrowCooldown(ticks); }
    @Override public int getNextArrowRemoval() { return getArrowCooldown(); }
    @Override public int getBeeStingerCooldown() { return livingAccessor().lunararc$getRemoveStingerTime(); }
    @Override public void setBeeStingerCooldown(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        livingAccessor().lunararc$setRemoveStingerTime(ticks);
    }
    @Override public int getBeeStingersInBody() { return getHandle().getStingerCount(); }
    @Override public void setBeeStingersInBody(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        getHandle().setStingerCount(count);
    }
    @Override public void setNextBeeStingerRemoval(int ticks) { setBeeStingerCooldown(ticks); }
    @Override public int getNextBeeStingerRemoval() { return getBeeStingerCooldown(); }

    @Override public void damage(double amount) { damageNms(amount, getHandle().damageSources().generic()); }
    @Override public void damage(double amount, @Nullable Entity source) {
        net.minecraft.world.damagesource.DamageSource nms = getHandle().damageSources().generic();
        if (source instanceof CraftLivingEntity living) {
            nms = living.getHandle() instanceof net.minecraft.world.entity.player.Player player
                    ? getHandle().damageSources().playerAttack(player)
                    : getHandle().damageSources().mobAttack(living.getHandle());
        }
        damageNms(amount, nms);
    }
    @Override public void damage(double amount, @NotNull org.bukkit.damage.DamageSource damageSource) {
        if (!(damageSource instanceof CraftDamageSource craft)) throw new IllegalArgumentException("DamageSource is not backed by LunarArc");
        damageNms(amount, craft.getHandle());
    }
    private void damageNms(double amount, net.minecraft.world.damagesource.DamageSource source) {
        if (!Double.isFinite(amount) || amount < 0.0D) throw new IllegalArgumentException("amount must be finite and >= 0");
        getHandle().hurt(source, (float) amount);
    }

    @Override public int getMaximumNoDamageTicks() { return livingBridge().lunararc$getInvulnerableDuration(); }
    @Override public void setMaximumNoDamageTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        livingBridge().lunararc$setInvulnerableDuration(ticks);
    }
    @Override public double getLastDamage() { return livingAccessor().lunararc$getLastHurt(); }
    @Override public void setLastDamage(double damage) { livingAccessor().lunararc$setLastHurt((float) damage); }
    @Override public int getNoDamageTicks() { return getHandle().invulnerableTime; }
    @Override public void setNoDamageTicks(int ticks) { getHandle().invulnerableTime = Math.max(0, ticks); }
    @Override public int getNoActionTicks() { return getHandle().getNoActionTime(); }
    @Override public void setNoActionTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        getHandle().setNoActionTime(ticks);
    }

    @Override public @Nullable Player getKiller() {
        net.minecraft.world.entity.player.Player player = net.minecraft.world.entity.EntityReference.getPlayer(livingAccessor().lunararc$getLastHurtByPlayer(), getHandle().level());
        if (player == null) return null;
        Entity bukkit = CraftEntity.getEntity(server, player);
        return bukkit instanceof Player result ? result : null;
    }
    @Override public void setKiller(@Nullable Player killer) {
        if (killer != null && !(killer instanceof CraftPlayer)) throw new IllegalArgumentException("Player is not backed by LunarArc");
        net.minecraft.server.level.ServerPlayer player = killer == null ? null : ((CraftPlayer) killer).getHandle();
        livingAccessor().lunararc$setLastHurtByPlayer(net.minecraft.world.entity.EntityReference.of(player));
        livingAccessor().lunararc$setLastHurtByMob(net.minecraft.world.entity.EntityReference.of(player));
        livingAccessor().lunararc$setLastHurtByPlayerTime(player == null ? 0 : 100);
    }

    @Override public boolean addPotionEffect(@NotNull PotionEffect effect) { return addPotionEffect(effect, false); }
    @Override public boolean addPotionEffect(@NotNull PotionEffect effect, boolean force) {
        Objects.requireNonNull(effect, "effect");
        Holder<net.minecraft.world.effect.MobEffect> holder = effectHolder(effect.getType());
        if (holder == null) return false;
        ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle())
                .lunararc$pushEffectCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.PLUGIN);
        return getHandle().addEffect(
                new MobEffectInstance(holder, effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
    }
    @Override public boolean addPotionEffects(@NotNull Collection<PotionEffect> effects) {
        Objects.requireNonNull(effects, "effects");
        boolean all = true;
        for (PotionEffect effect : effects) all &= addPotionEffect(effect);
        return all;
    }
    @Override public boolean hasPotionEffect(@NotNull PotionEffectType type) {
        Holder<net.minecraft.world.effect.MobEffect> holder = effectHolder(type);
        return holder != null && getHandle().hasEffect(holder);
    }
    @Override public @Nullable PotionEffect getPotionEffect(@NotNull PotionEffectType type) {
        Holder<net.minecraft.world.effect.MobEffect> holder = effectHolder(type);
        if (holder == null) return null;
        MobEffectInstance effect = getHandle().getEffect(holder);
        return effect == null ? null : toBukkitEffect(effect);
    }
    @Override public void removePotionEffect(@NotNull PotionEffectType type) {
        Holder<net.minecraft.world.effect.MobEffect> holder = effectHolder(type);
        if (holder != null) {
            ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle())
                    .lunararc$pushEffectCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.PLUGIN);
            getHandle().removeEffect(holder);
        }
    }
    @Override public @NotNull Collection<PotionEffect> getActivePotionEffects() {
        List<PotionEffect> result = new ArrayList<>();
        for (MobEffectInstance effect : getHandle().getActiveEffects()) result.add(toBukkitEffect(effect));
        return java.util.Collections.unmodifiableList(result);
    }
    @Override public boolean clearActivePotionEffects() {
        return ((io.lunararcdevs.lunararc.common.bridge.LivingEntityBridge) getHandle())
                .lunararc$removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.PLUGIN);
    }

    private static @Nullable Holder<net.minecraft.world.effect.MobEffect> effectHolder(PotionEffectType type) {
        Objects.requireNonNull(type, "type");
        return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(type.getKey().toString())).orElse(null);
    }
    private static PotionEffect toBukkitEffect(MobEffectInstance effect) {
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        if (id == null) throw new IllegalStateException("Unregistered MobEffect " + effect.getEffect());
        PotionEffectType type = PotionEffectType.getByKey(new NamespacedKey(id.getNamespace(), id.getPath()));
        if (type == null) throw new IllegalStateException("No Bukkit PotionEffectType for " + id);
        return new PotionEffect(type, effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    @Override public boolean hasLineOfSight(@NotNull Entity other) {
        return other instanceof CraftEntity craft && getHandle().hasLineOfSight(craft.getHandle());
    }
    @Override public boolean hasLineOfSight(@NotNull Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() != getWorld()) return false;
        Vector start = getEyeLocation().toVector();
        Vector delta = location.toVector().subtract(start);
        double distance = delta.length();
        RayTraceResult hit = rayTraceBlocks(distance, FluidCollisionMode.NEVER);
        return hit == null || hit.getHitPosition() == null || hit.getHitPosition().distanceSquared(start) + 1.0E-6D >= distance * distance;
    }

    @Override public boolean getRemoveWhenFarAway() {
        return getHandle() instanceof net.minecraft.world.entity.Mob mob && !mob.isPersistenceRequired();
    }
    @Override public void setRemoveWhenFarAway(boolean remove) {
        if (getHandle() instanceof net.minecraft.world.entity.Mob mob) {
            if (remove) ((io.lunararcdevs.lunararc.common.bridge.access.MobAccessBridge) mob).lunararc$setPersistenceRequired(false); else mob.setPersistenceRequired();
        }
    }
    @Override public @Nullable EntityEquipment getEquipment() { return equipment; }
    @Override public void setCanPickupItems(boolean pickup) {
        if (getHandle() instanceof net.minecraft.world.entity.Mob mob) mob.setCanPickUpLoot(pickup);
        else livingBridge().lunararc$setBukkitCanPickupItems(pickup);
    }
    @Override public boolean getCanPickupItems() {
        return getHandle() instanceof net.minecraft.world.entity.Mob mob ? mob.canPickUpLoot() : livingBridge().lunararc$getBukkitCanPickupItems();
    }

    @Override public boolean isLeashed() { return false; }
    @Override public @NotNull Entity getLeashHolder() throws IllegalStateException { throw new IllegalStateException("Entity not leashed"); }
    @Override public boolean setLeashHolder(@Nullable Entity holder) { return false; }

    @Override public @NotNull TriState getFrictionState() { return livingBridge().lunararc$getFrictionState(); }
    @Override public void setFrictionState(@NotNull TriState state) { livingBridge().lunararc$setFrictionState(Objects.requireNonNull(state, "state")); }

    @Override public boolean isGliding() { return getHandle().isFallFlying(); }
    @Override public void setGliding(boolean gliding) {
        // Vanilla stores fall-flying in Entity shared flag index 7. Use a narrow
        // accessor on the real NMS entity instead of relying on player-only helpers.
        ((EntityAccessBridge) getHandle()).lunararc$setSharedFlag(7, gliding);
    }
    @Override public boolean isSwimming() { return getHandle().isSwimming(); }
    @Override public void setSwimming(boolean swimming) { getHandle().setSwimming(swimming); }
    @Override public boolean isRiptiding() { return getHandle().isAutoSpinAttack(); }
    @Override public void setRiptiding(boolean riptiding) {
        livingAccessor().lunararc$setLivingEntityFlag(((LivingEntityBridge) (Object) getHandle()).lunararc$getSpinAttackFlagBridge(), riptiding);
    }
    @Override public boolean isSleeping() { return getHandle().isSleeping(); }
    @Override public boolean isClimbing() { return getHandle().onClimbable(); }

    @Override public @Nullable org.bukkit.attribute.AttributeInstance getAttribute(@NotNull Attribute attribute) {
        Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder = attributeHolder(attribute);
        if (holder == null) return null;
        AttributeInstance instance = getHandle().getAttribute(holder);
        return instance == null ? null : new CraftAttributeInstance(instance, attribute);
    }
    @Override public void registerAttribute(@NotNull Attribute attribute) {
        Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder = attributeHolder(attribute);
        if (holder == null) throw new IllegalArgumentException("Unknown attribute " + attribute.getKey());
        if (getHandle().getAttribute(holder) != null) return;
        AttributeInstance instance = new AttributeInstance(holder, AttributeInstance::getAttribute);
        ((io.lunararcdevs.lunararc.common.bridge.access.AttributeMapAccessBridge) getHandle().getAttributes()).lunararc$getAttributes().put(holder, instance);
    }
    private static @Nullable Holder<net.minecraft.world.entity.ai.attributes.Attribute> attributeHolder(Attribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(attribute.getKey().toString())).orElse(null);
    }

    @Override public void setAI(boolean ai) { if (getHandle() instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(!ai); }
    @Override public boolean hasAI() { return !(getHandle() instanceof net.minecraft.world.entity.Mob mob) || !mob.isNoAi(); }
    @Override public void attack(@NotNull Entity target) {
        Objects.requireNonNull(target, "target");
        if (!(target instanceof CraftEntity craft)) throw new IllegalArgumentException("Entity is not backed by LunarArc");
        if (getHandle() instanceof net.minecraft.world.entity.player.Player player) player.attack(craft.getHandle());
        else if (craft.getHandle().level() instanceof ServerLevel targetLevel) getHandle().doHurtTarget(targetLevel, craft.getHandle());
    }
    @Override public void swingMainHand() { getHandle().swing(InteractionHand.MAIN_HAND, true); }
    @Override public void swingOffHand() { getHandle().swing(InteractionHand.OFF_HAND, true); }
    @Override public void playHurtAnimation(float yaw) {
        if (getHandle().level() instanceof ServerLevel level) {
            level.getChunkSource().sendToTrackingPlayersAndSelf(getHandle(), new ClientboundHurtAnimationPacket(getEntityId(), yaw + 90.0F));
        }
    }

    @Override public void setCollidable(boolean collidable) { livingBridge().lunararc$setCollidable(collidable); }
    @Override public boolean isCollidable() { return livingBridge().lunararc$isCollidable(); }
    @Override public @NotNull Set<UUID> getCollidableExemptions() { return livingBridge().lunararc$getCollidableExemptions(); }

    @Override public <T> @Nullable T getMemory(@NotNull MemoryKey<T> memoryKey) {
        Objects.requireNonNull(memoryKey, "memoryKey");
        java.util.Optional<?> memory = getHandle().getBrain().getMemoryInternal(CraftMemoryKey.bukkitToMinecraft(memoryKey));
        return memory == null ? null : (T) memory.map(CraftMemoryMapper::fromNms).orElse(null);
    }
    @Override public <T> void setMemory(@NotNull MemoryKey<T> memoryKey, @Nullable T value) {
        Objects.requireNonNull(memoryKey, "memoryKey");
        getHandle().getBrain().setMemory(CraftMemoryKey.bukkitToMinecraft(memoryKey), CraftMemoryMapper.toNms(value));
    }

    @Override public @Nullable Sound getHurtSound() { return bukkitSound(livingAccessor().lunararc$invokeGetHurtSound(getHandle().damageSources().generic())); }
    @Override public @Nullable Sound getDeathSound() { return bukkitSound(livingAccessor().lunararc$invokeGetDeathSound()); }
    @Override public @NotNull Sound getFallDamageSound(int fallHeight) { return requireSound(livingAccessor().lunararc$invokeGetFallDamageSound(fallHeight)); }
    @Override public @NotNull Sound getFallDamageSoundSmall() { return requireSound(getHandle().getFallSounds().small()); }
    @Override public @NotNull Sound getFallDamageSoundBig() { return requireSound(getHandle().getFallSounds().big()); }
    @Override public @NotNull Sound getDrinkingSound(@NotNull ItemStack itemStack) { return getEatingSound(itemStack); }
    @Override public @NotNull Sound getEatingSound(@NotNull ItemStack itemStack) {
        java.util.Objects.requireNonNull(itemStack, "itemStack");
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(itemStack);
        net.minecraft.world.item.component.Consumable consumable = nms.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
        net.minecraft.sounds.SoundEvent sound = net.minecraft.sounds.SoundEvents.GENERIC_DRINK.value();
        if (consumable != null) {
            if (getHandle() instanceof net.minecraft.world.item.component.Consumable.OverrideConsumeSound override) {
                sound = override.getConsumeSound(nms);
            } else {
                sound = consumable.sound().value();
            }
        }
        return requireSound(sound);
    }
    private static @Nullable Sound bukkitSound(@Nullable SoundEvent event) {
        if (event == null) return null;
        Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(event);
        return id == null ? null : org.bukkit.Registry.SOUNDS.get(new NamespacedKey(id.getNamespace(), id.getPath()));
    }
    private static @NotNull Sound requireSound(SoundEvent event) {
        Sound sound = bukkitSound(event);
        if (sound == null) throw new IllegalStateException("Unregistered sound " + event);
        return sound;
    }

    @Override public boolean canBreatheUnderwater() { return getHandle().canBreatheUnderwater(); }
    @Override @Deprecated public @NotNull EntityCategory getCategory() { throw new UnsupportedOperationException("Entity categories were replaced by tags"); }

    @Override public float getSidewaysMovement() { return livingAccessor().lunararc$getSidewaysMovement(); }
    @Override public float getUpwardsMovement() { return livingAccessor().lunararc$getUpwardsMovement(); }
    @Override public float getForwardsMovement() { return livingAccessor().lunararc$getForwardsMovement(); }
    @Override @Deprecated public int getArrowsStuck() { return getArrowsInBody(); }
    @Override @Deprecated public void setArrowsStuck(int arrows) { setArrowsInBody(arrows, true); }
    @Override public int getShieldBlockingDelay() { return livingBridge().lunararc$getShieldBlockingDelay(); }
    @Override public void setShieldBlockingDelay(int delay) { livingBridge().lunararc$setShieldBlockingDelay(delay); }

    @Override public void startUsingItem(@NotNull EquipmentSlot hand) {
        Objects.requireNonNull(hand, "hand");
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) throw new IllegalArgumentException("hand must be HAND or OFF_HAND");
        getHandle().startUsingItem(hand == EquipmentSlot.HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }
    @Override public void completeUsingActiveItem() { getHandle().completeUsingItem(); }
    @Override public @NotNull ItemStack getActiveItem() { return CraftItemStack.asBukkitCopy(getHandle().getUseItem()); }
    @Override public void clearActiveItem() { getHandle().stopUsingItem(); }
    @Override public int getActiveItemRemainingTime() { return getHandle().getUseItemRemainingTicks(); }
    @Override public void setActiveItemRemainingTime(int ticks) { setItemInUseTicks(ticks); }
    @Override public int getActiveItemUsedTime() {
        if (!getHandle().isUsingItem() || getHandle().getUseItem().isEmpty()) return 0;
        return Math.max(0, getHandle().getUseItem().getUseDuration(getHandle()) - getHandle().getUseItemRemainingTicks());
    }
    @Override public boolean hasActiveItem() { return getHandle().isUsingItem(); }
    @Override public @NotNull EquipmentSlot getActiveItemHand() { return getHandle().getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND; }

    @Override public boolean isJumping() { return livingAccessor().lunararc$isJumping(); }
    @Override public void setJumping(boolean jumping) {
        livingAccessor().lunararc$setJumping(jumping);
        if (jumping && getHandle() instanceof net.minecraft.world.entity.Mob mob) mob.getJumpControl().jump();
    }
    @Override public boolean canUseEquipmentSlot(@NotNull EquipmentSlot slot) { return livingAccessor().lunararc$canUseSlot(CraftEquipmentSlot.getNMS(slot)); }
    @Override public void knockback(double strength, double directionX, double directionZ) {
        if (strength <= 0.0D || !Double.isFinite(strength)) throw new IllegalArgumentException("strength must be finite and > 0");
        getHandle().knockback(strength, directionX, directionZ);
    }

    @Override public void broadcastSlotBreak(@NotNull EquipmentSlot slot) {
        net.minecraft.world.entity.EquipmentSlot nms = CraftEquipmentSlot.getNMS(slot);
        getHandle().level().broadcastEntityEvent(getHandle(), ((LivingEntityBridge) (Object) getHandle()).lunararc$entityEventForEquipmentBreakBridge(nms));
    }
    @Override public void broadcastSlotBreak(@NotNull EquipmentSlot slot, @NotNull Collection<Player> players) {
        Objects.requireNonNull(players, "players");
        if (players.isEmpty()) return;
        net.minecraft.world.entity.EquipmentSlot nms = CraftEquipmentSlot.getNMS(slot);
        ClientboundEntityEventPacket packet = new ClientboundEntityEventPacket(getHandle(), ((LivingEntityBridge) (Object) getHandle()).lunararc$entityEventForEquipmentBreakBridge(nms));
        for (Player player : players) {
            if (!(player instanceof CraftPlayer craft)) throw new IllegalArgumentException("Player is not backed by LunarArc");
            if (craft.getHandle().connection != null) craft.getHandle().connection.send(packet);
        }
    }
    @Override public @NotNull ItemStack damageItemStack(@NotNull ItemStack stack, int amount) {
        Objects.requireNonNull(stack, "stack");
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        ((ItemStackBridge) (Object) nms).lunararc$hurtAndBreak(amount, getHandle(), null, true);
        return CraftItemStack.asBukkitCopy(nms);
    }
    @Override public void damageItemStack(@NotNull EquipmentSlot slot, int amount) {
        net.minecraft.world.entity.EquipmentSlot nmsSlot = CraftEquipmentSlot.getNMS(slot);
        net.minecraft.world.item.ItemStack nms = getHandle().getItemBySlot(nmsSlot);
        ((ItemStackBridge) (Object) nms).lunararc$hurtAndBreak(amount, getHandle(), nmsSlot, true);
    }

    @Override public float getHurtDirection() { return getHandle().getHurtDir(); }
    @Override @Deprecated public void setHurtDirection(float hurtDirection) { throw new UnsupportedOperationException("Cannot set hurt direction on a non-player living entity"); }
    @Override public float getBodyYaw() { return getHandle().getVisualRotationYInDegrees(); }
    @Override public void setBodyYaw(float bodyYaw) { getHandle().setYBodyRot(bodyYaw); }
    @Override public void playPickupItemAnimation(@NotNull Item item, int quantity) {
        Objects.requireNonNull(item, "item");
        if (!(item instanceof CraftItem craft)) throw new IllegalArgumentException("Item is not backed by LunarArc");
        getHandle().take(craft.getHandle(), quantity);
    }

    @Override public <T extends Projectile> @NotNull T launchProjectile(@NotNull Class<? extends T> projectile) {
        return launchProjectile(projectile, null, null);
    }

    @Override
    public <T extends Projectile> @NotNull T launchProjectile(@NotNull Class<? extends T> projectile, @Nullable Vector velocity) {
        return launchProjectile(projectile, velocity, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Projectile> @NotNull T launchProjectile(
            @NotNull Class<? extends T> projectile,
            @Nullable Vector velocity,
            @Nullable Consumer<? super T> function
    ) {
        Objects.requireNonNull(projectile, "projectile");
        if (!(getHandle().level() instanceof ServerLevel world)) {
            throw new IllegalStateException("Living entity is not attached to a server level");
        }

        net.minecraft.world.entity.Entity launch;
        if (org.bukkit.entity.Snowball.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball thrown =
                    new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOWBALL));
            thrown.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F);
            launch = thrown;
        } else if (org.bukkit.entity.Egg.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg thrown =
                    new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG));
            thrown.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F);
            launch = thrown;
        } else if (org.bukkit.entity.EnderPearl.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl thrown =
                    new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
            thrown.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F);
            launch = thrown;
        } else if (org.bukkit.entity.ThrownPotion.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion thrown =
                    org.bukkit.entity.LingeringPotion.class.isAssignableFrom(projectile)
                            ? new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LINGERING_POTION))
                            : new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION));
            thrown.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), -20.0F, 0.5F, 1.0F);
            launch = thrown;
        } else if (org.bukkit.entity.ThrownExpBottle.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle thrown =
                    new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE));
            thrown.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), -20.0F, 0.7F, 1.0F);
            launch = thrown;
        } else if (org.bukkit.entity.FishHook.class.isAssignableFrom(projectile) && getHandle() instanceof net.minecraft.world.entity.player.Player player) {
            launch = new net.minecraft.world.entity.projectile.FishingHook(player, world, 0, 0);
        } else if (org.bukkit.entity.ShulkerBullet.class.isAssignableFrom(projectile)) {
            org.bukkit.Location eye = this.getEyeLocation();
            launch = new net.minecraft.world.entity.projectile.ShulkerBullet(world, getHandle(), null, null);
            launch.snapTo(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch());
        } else if (org.bukkit.entity.LlamaSpit.class.isAssignableFrom(projectile)) {
            org.bukkit.Location eye = this.getEyeLocation();
            org.bukkit.util.Vector direction = eye.getDirection();
            net.minecraft.world.entity.projectile.LlamaSpit spit = net.minecraft.world.entity.EntityType.LLAMA_SPIT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (spit == null) throw new IllegalStateException("Minecraft failed to create LLAMA_SPIT");
            spit.setOwner(getHandle());
            spit.shoot(direction.getX(), direction.getY(), direction.getZ(), 1.5F, 10.0F);
            spit.snapTo(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch());
            launch = spit;
        } else if (org.bukkit.entity.AbstractWindCharge.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge charge;
            if (org.bukkit.entity.BreezeWindCharge.class.isAssignableFrom(projectile)) {
                charge = net.minecraft.world.entity.EntityType.BREEZE_WIND_CHARGE.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            } else {
                charge = net.minecraft.world.entity.EntityType.WIND_CHARGE.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            }
            if (charge == null) throw new IllegalStateException("Minecraft failed to create wind charge");
            charge.setOwner(getHandle());
            charge.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F);
            launch = charge;
        } else if (org.bukkit.entity.Firework.class.isAssignableFrom(projectile)) {
            org.bukkit.Location eye = this.getEyeLocation();
            net.minecraft.world.entity.projectile.FireworkRocketEntity rocket = new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                    world, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET), getHandle(),
                    eye.getX(), eye.getY(), eye.getZ(), true);
            rocket.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 0.5F, 1.0F);
            launch = rocket;
        } else if (org.bukkit.entity.Fireball.class.isAssignableFrom(projectile)) {
            org.bukkit.Location eye = this.getEyeLocation();
            org.bukkit.util.Vector direction = eye.getDirection().multiply(10.0D);
            net.minecraft.world.phys.Vec3 power = new net.minecraft.world.phys.Vec3(direction.getX(), direction.getY(), direction.getZ());
            if (org.bukkit.entity.SmallFireball.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball(world, getHandle(), power);
            } else if (org.bukkit.entity.WitherSkull.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull(world, getHandle(), power);
            } else if (org.bukkit.entity.DragonFireball.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball(world, getHandle(), power);
            } else {
                launch = new net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball(world, getHandle(), power, 1);
            }
            launch.snapTo(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch());
        } else if (org.bukkit.entity.Trident.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.arrow.ThrownTrident arrow =
                    new net.minecraft.world.entity.projectile.arrow.ThrownTrident(
                            world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
            arrow.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F,
                    net.minecraft.world.item.TridentItem.PROJECTILE_SHOOT_POWER, 1.0F);
            launch = arrow;
        } else if (org.bukkit.entity.SpectralArrow.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.arrow.SpectralArrow arrow =
                    new net.minecraft.world.entity.projectile.arrow.SpectralArrow(
                            world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPECTRAL_ARROW), null);
            arrow.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 3.0F, 1.0F);
            launch = arrow;
        } else if (org.bukkit.entity.AbstractArrow.class.isAssignableFrom(projectile)) {
            net.minecraft.world.entity.projectile.arrow.Arrow arrow =
                    new net.minecraft.world.entity.projectile.arrow.Arrow(
                            world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 3.0F, 1.0F);
            launch = arrow;
        } else {
            throw new UnsupportedOperationException(
                    "This entity cannot launch projectiles of type " + projectile.getName()
            );
        }

        T bukkit = (T) CraftEntity.getEntity(server, launch);
        if (velocity != null) bukkit.setVelocity(velocity);
        if (function != null) function.accept(bukkit);
        world.addFreshEntity(launch);
        return bukkit;
    }

    @Override
    public org.bukkit.@org.jetbrains.annotations.Nullable Color getWaypointColor() {
        return getHandle().waypointIcon().color.map(org.bukkit.Color::fromARGB).orElse(null);
    }

    @Override
    public net.kyori.adventure.key.@org.jetbrains.annotations.NotNull Key getWaypointStyle() {
        net.minecraft.resources.Identifier id = getHandle().waypointIcon().style.identifier();
        return net.kyori.adventure.key.Key.key(id.getNamespace(), id.getPath());
    }

    @Override
    public void setWaypointColor(final org.bukkit.@org.jetbrains.annotations.Nullable Color color) {
        final java.util.Optional<Integer> newColor = java.util.Optional.ofNullable(color).map(org.bukkit.Color::asARGB);
        if (java.util.Objects.equals(getHandle().waypointIcon().color, newColor)) return;
        getHandle().waypointIcon().color = newColor;
        updateWaypoint();
    }

    @Override
    public void setWaypointStyle(final net.kyori.adventure.key.@org.jetbrains.annotations.Nullable Key key) {
        final net.minecraft.resources.ResourceKey<net.minecraft.world.waypoints.WaypointStyleAsset> newKey = key == null
                ? net.minecraft.world.waypoints.WaypointStyleAssets.DEFAULT
                : net.minecraft.resources.ResourceKey.create(net.minecraft.world.waypoints.WaypointStyleAssets.ROOT_ID, net.minecraft.resources.Identifier.fromNamespaceAndPath(key.namespace(), key.value()));
        if (java.util.Objects.equals(getHandle().waypointIcon().style, newKey)) return;
        getHandle().waypointIcon().style = newKey;
        updateWaypoint();
    }

    private void updateWaypoint() {
        if (!(getHandle().level() instanceof ServerLevel level)) return;
        net.minecraft.server.waypoints.ServerWaypointManager manager = level.getWaypointManager();
        manager.untrackWaypoint(getHandle());
        manager.trackWaypoint(getHandle());
    }

    @Override
    public io.papermc.paper.world.damagesource.CombatTracker getCombatTracker() {
        return ((io.lunararcdevs.lunararc.common.bridge.CombatTrackerBridge) getHandle().getCombatTracker()).lunararc$getPaperCombatTracker();
    }
}
