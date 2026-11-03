package io.lunararcdevs.lunararc.common.mixin.core.inventory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.lunararcdevs.lunararc.common.bridge.AbstractContainerMenuBridge;
import io.lunararcdevs.lunararc.common.bridge.AnvilMenuBridge;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("mixin")
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin implements AnvilMenuBridge {
    @Unique private int lunararc$maximumRepairCost = 40;
    @Unique private boolean lunararc$bypassEnchantmentLevelRestriction;

    @Override public int lunararc$getMaximumRepairCost() { return this.lunararc$maximumRepairCost; }
    @Override public void lunararc$setMaximumRepairCost(int value) { this.lunararc$maximumRepairCost = Math.max(0, value); }
    @Override public boolean lunararc$bypassesEnchantmentLevelRestriction() { return this.lunararc$bypassEnchantmentLevelRestriction; }
    @Override public void lunararc$setBypassEnchantmentLevelRestriction(boolean value) { this.lunararc$bypassEnchantmentLevelRestriction = value; }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40), require = 0)
    private int lunararc$maximumRepairCostConstant(int original) {
        return this.lunararc$maximumRepairCost;
    }

    @WrapOperation(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"),
            require = 0)
    private int lunararc$enchantmentMaxLevel(Enchantment enchantment, Operation<Integer> original) {
        return this.lunararc$bypassEnchantmentLevelRestriction ? Integer.MAX_VALUE : original.call(enchantment);
    }

    @Inject(method = "createResult", at = @At("RETURN"), require = 0)
    private void lunararc$prepareAnvil(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        var owner = ((AbstractContainerMenuBridge) menu).lunararc$getOwner();
        if (owner == null) return;
        Object bukkit = ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) owner).lunararc$getBukkitEntity();
        if (!(bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer player)) return;
        var view = new org.bukkit.craftbukkit.inventory.CraftAnvilView(
                player, menu, net.kyori.adventure.text.Component.translatable("container.repair"));
        org.bukkit.inventory.ItemStack current = view.getTopInventory().getItem(2);
        var event = new org.bukkit.event.inventory.PrepareAnvilEvent(view, current);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        view.getTopInventory().setItem(2, event.getResult());
        menu.broadcastChanges();
    }
}
