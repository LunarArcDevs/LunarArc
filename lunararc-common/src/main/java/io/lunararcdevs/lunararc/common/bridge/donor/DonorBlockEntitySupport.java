package io.lunararcdevs.lunararc.common.bridge.donor;

import io.lunararcdevs.lunararc.common.bridge.world.BeaconRangeBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class DonorBlockEntitySupport {
    public static boolean conduitDamage = true;

    public static RecipeType<? extends AbstractCookingRecipe> recipeType(AbstractFurnaceBlockEntity furnace) {
        return ((io.lunararcdevs.lunararc.common.bridge.world.CookSpeedBridge) furnace).lunararc$recipeType();
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) throw new IllegalArgumentException(message);
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private DonorBlockEntitySupport() {}

    public static int getTotalCookTime(Level level, RecipeType<? extends AbstractCookingRecipe> type,
            AbstractFurnaceBlockEntity furnace, double multiplier) {
        return totalCookTime(level, type, furnace, multiplier);
    }

    private static <T extends AbstractCookingRecipe> int totalCookTime(Level level, RecipeType<T> type,
            AbstractFurnaceBlockEntity furnace, double multiplier) {
        int time = 200;
        if (level != null) {
            SingleRecipeInput input = new SingleRecipeInput(furnace.getItem(0));
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                time = serverLevel.recipeAccess().getRecipeFor(type, input, level)
                        .map(holder -> holder.value().cookingTime()).orElse(200);
            }
        }
        return (int) Math.ceil(time / multiplier);
    }

    public static List<Player> getHumansInRange(Level level, BlockPos pos, int levels, BeaconBlockEntity beacon) {
        double range = beacon != null ? ((BeaconRangeBridge) beacon).lunararc$effectRange() : levels * 10 + 10;
        AABB box = new AABB(pos).inflate(range).expandTowards(0.0D, level.getHeight(), 0.0D);
        if (range <= 128.0D) {
            return level.getEntitiesOfClass(Player.class, box);
        }
        List<Player> players = new ArrayList<>();
        for (Player player : level.players()) {
            if (!player.isSpectator() && box.intersects(player.getBoundingBox())) {
                players.add(player);
            }
        }
        return players;
    }

    public static int getRange(List<BlockPos> blocks) {
        return blocks.size() / 7 * 16;
    }
}
