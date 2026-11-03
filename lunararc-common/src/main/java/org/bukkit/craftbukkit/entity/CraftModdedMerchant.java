package org.bukkit.craftbukkit.entity;

import java.util.List;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.item.trading.MerchantOffers;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.MerchantRecipe;

/** Wrapper for modded AgeableMobs that trade (for example Goblin Traders), so inventory events can find their Bukkit merchant. */
public class CraftModdedMerchant extends CraftAgeable implements CraftMerchant {
    public CraftModdedMerchant(CraftServer server, AgeableMob entity) { super(server, entity); }

    @Override public net.minecraft.world.item.trading.Merchant getMerchant() {
        return (net.minecraft.world.item.trading.Merchant) this.entity;
    }

    @Override public List<MerchantRecipe> getRecipes() {
        return getMerchant().getOffers().stream().map(CraftMerchantRecipe::new).map(MerchantRecipe.class::cast).toList();
    }
    @Override public void setRecipes(List<MerchantRecipe> recipes) {
        java.util.Objects.requireNonNull(recipes, "recipes");
        MerchantOffers offers = new MerchantOffers();
        for (MerchantRecipe recipe : recipes) offers.add(CraftMerchantRecipe.fromBukkit(recipe).toMinecraft());
        getMerchant().overrideOffers(offers);
    }
    @Override public MerchantRecipe getRecipe(int index) { return new CraftMerchantRecipe(getMerchant().getOffers().get(index)); }
    @Override public void setRecipe(int index, MerchantRecipe recipe) { getMerchant().getOffers().set(index, CraftMerchantRecipe.fromBukkit(recipe).toMinecraft()); }
    @Override public int getRecipeCount() { return getMerchant().getOffers().size(); }
    @Override public boolean isTrading() { return getMerchant().getTradingPlayer() != null; }
    @Override public HumanEntity getTrader() {
        net.minecraft.world.entity.player.Player player = getMerchant().getTradingPlayer();
        return player == null ? null : (HumanEntity) CraftEntity.getEntity(server, player);
    }

    @Override public String toString() { return "CraftModdedMerchant"; }
}
