package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.lunararcdevs.lunararc.common.event.LunarArcBlockEvents;
import io.lunararcdevs.lunararc.common.event.LunarArcMobEvents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vault events: BlockDispenseLootEvent when a key is used, VaultChangeStateEvent when the state changes and
 * VaultDisplayItemEvent when the shown item is picked. A cancelled loot event also stops the player being marked
 * as rewarded.
 */
@Mixin(net.minecraft.world.level.block.entity.vault.VaultBlockEntity.Server.class)
public abstract class ExtraVaultEventsMixin {
    @Unique private static boolean lunararc$lootCancelled;

    @WrapMethod(method = "tryInsertKey", require = 0)
    private static void lunararc$insertKey(ServerLevel level, BlockPos pos, BlockState state, VaultConfig config, VaultServerData serverData,
            VaultSharedData sharedData, Player player, ItemStack stack, Operation<Void> original) {
        lunararc$lootCancelled = false;
        try {
            original.call(level, pos, state, config, serverData, sharedData, player, stack);
        } finally {
            lunararc$lootCancelled = false;
        }
    }

    @WrapOperation(method = "tryInsertKey", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/vault/VaultBlockEntity$Server;unlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/vault/VaultConfig;Lnet/minecraft/world/level/block/entity/vault/VaultServerData;Lnet/minecraft/world/level/block/entity/vault/VaultSharedData;Ljava/util/List;)V"))
    private static void lunararc$unlock(ServerLevel level, BlockState state, BlockPos pos, VaultConfig config, VaultServerData serverData,
            VaultSharedData sharedData, List<ItemStack> items, Operation<Void> original, @Local(argsOnly = true) Player player) {
        List<ItemStack> result = LunarArcMobEvents.dispenseLoot(level, pos, player, items,
                level.getServer().reloadableRegistries().getLootTable(config.lootTable()) == null ? config.lootTable() : config.lootTable());
        if (result == null) {
            lunararc$lootCancelled = true;
            return;
        }
        original.call(level, state, pos, config, serverData, sharedData, result);
    }

    @WrapOperation(method = "tryInsertKey", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/vault/VaultServerData;addToRewardedPlayers(Lnet/minecraft/world/entity/player/Player;)V"))
    private static void lunararc$markRewarded(VaultServerData data, Player player, Operation<Void> original) {
        if (lunararc$lootCancelled) return;
        original.call(data, player);
    }

    @WrapMethod(method = "setVaultState", require = 0)
    private static void lunararc$stateChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState, VaultConfig config,
            VaultSharedData sharedData, Operation<Void> original) {
        if (!LunarArcBlockEvents.vaultStateChange(level, pos, oldState, newState, sharedData)) return;
        original.call(level, pos, oldState, newState, config, sharedData);
    }

    @WrapOperation(method = "cycleDisplayItemFromLootTable", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/vault/VaultSharedData;setDisplayItem(Lnet/minecraft/world/item/ItemStack;)V", ordinal = 1))
    private static void lunararc$displayItem(VaultSharedData data, ItemStack item, Operation<Void> original,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        ItemStack result = LunarArcBlockEvents.vaultDisplayItem(level, pos, item);
        if (result == null) return;
        original.call(data, result);
    }
}
