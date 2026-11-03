package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Concrete custom merchant backed by a real vanilla Merchant implementation. */
public final class CraftMerchantCustom implements Merchant, org.bukkit.craftbukkit.inventory.CraftMerchant {
    private final MinecraftMerchant merchant;

    public CraftMerchantCustom(@Nullable String title) {
        this(title == null ? net.kyori.adventure.text.Component.text("Merchant") : net.kyori.adventure.text.Component.text(title));
    }

    public CraftMerchantCustom(@NotNull net.kyori.adventure.text.Component title) {
        this.merchant = new MinecraftMerchant(io.papermc.paper.adventure.PaperAdventure.asVanilla(
                java.util.Objects.requireNonNull(title, "title")));
    }

    public net.minecraft.world.item.trading.Merchant getHandle() { return merchant; }
    public Component getTitle() { return merchant.getScoreboardDisplayName(); }
    @Override public net.minecraft.world.item.trading.Merchant getMerchant() { return merchant; }

    @Override public @NotNull List<MerchantRecipe> getRecipes() {
        return merchant.getOffers().stream().map(CraftMerchantRecipe::new).map(MerchantRecipe.class::cast).toList();
    }
    @Override public void setRecipes(@NotNull List<MerchantRecipe> recipes) {
        java.util.Objects.requireNonNull(recipes, "recipes");
        MerchantOffers offers = merchant.getOffers();
        offers.clear();
        for (MerchantRecipe recipe : recipes) offers.add(CraftMerchantRecipe.fromBukkit(recipe).toMinecraft());
    }
    @Override public @NotNull MerchantRecipe getRecipe(int i) { return new CraftMerchantRecipe(merchant.getOffers().get(i)); }
    @Override public void setRecipe(int i, @NotNull MerchantRecipe recipe) { merchant.getOffers().set(i, CraftMerchantRecipe.fromBukkit(recipe).toMinecraft()); }
    @Override public int getRecipeCount() { return merchant.getOffers().size(); }
    @Override public boolean isTrading() { return merchant.getTradingPlayer() != null; }
    @Override public @Nullable HumanEntity getTrader() {
        Player player = merchant.getTradingPlayer();
        return player == null ? null : (HumanEntity) ((io.lunararcdevs.lunararc.common.bridge.EntityBridge) player).lunararc$getBukkitEntity();
    }

    private final class MinecraftMerchant implements net.minecraft.world.item.trading.Merchant,
            io.lunararcdevs.lunararc.common.bridge.MerchantBukkitBridge {
        private final Component title;
        private MerchantOffers offers = new MerchantOffers();
        private Player tradingPlayer;
        private int xp;
        private final org.bukkit.craftbukkit.inventory.CraftMerchant craftMerchant;

        private MinecraftMerchant(Component title) { this.title = title; this.craftMerchant = CraftMerchantCustom.this; }
        public org.bukkit.craftbukkit.inventory.CraftMerchant getCraftMerchant() { return this.craftMerchant; }
        @Override public org.bukkit.inventory.Merchant lunararc$getBukkitMerchant() { return CraftMerchantCustom.this; }
        @Override public void setTradingPlayer(@Nullable Player player) { this.tradingPlayer = player; }
        @Override public @Nullable Player getTradingPlayer() { return this.tradingPlayer; }
        @Override public boolean stillValid(Player player) { return this.tradingPlayer == player; }
        @Override public MerchantOffers getOffers() { return this.offers; }
        @Override public void overrideOffers(MerchantOffers offers) { this.offers = Preconditions.checkNotNull(offers); }
        @Override public void notifyTrade(MerchantOffer offer) { offer.increaseUses(); }
        @Override public void notifyTradeUpdated(net.minecraft.world.item.ItemStack stack) {}
        @Override public int getVillagerXp() { return xp; }
        @Override public void overrideXp(int xp) { this.xp = xp; }
        @Override public boolean showProgressBar() { return false; }
        @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.VILLAGER_YES; }
        @Override public boolean isClientSide() { return false; }
        public Component getScoreboardDisplayName() { return title; }
    }
}
