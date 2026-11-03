package io.lunararcdevs.lunararc.common.mixin.core.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunarArc");

    /**
     * Bukkit's BlockPlaceEvent describes a block that has already been placed (getBlockPlaced is the new block, getBlockReplacedState the
     * old one) and cancelling it puts the old state back. Plugins such as ClickVillagers rely on that: they look at the placed head and
     * cancel it to spawn the mob instead. Forge/NeoForge raise their own placement event at that point; for loaders without one the
     * event is raised here, after the block is set.
     */
    @WrapMethod(method = "placeBlock")
    private boolean lunararc$onPlaceBlock(BlockPlaceContext context, BlockState state, Operation<Boolean> original) {
        if (!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || !(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return original.call(context, state);
        }

        // Forge/NeoForge already expose a native placement event after their BlockSnapshot has captured the replaced state; that event is
        // the single source of truth on those runtimes.
        boolean loaderOwnsCancellation = ((io.lunararcdevs.lunararc.common.bridge.MinecraftServerBridge) (Object) serverPlayer.server)
                .lunararc$loaderHandlesBlockPlaceEvent();
        if (loaderOwnsCancellation) return original.call(context, state);

        net.minecraft.core.BlockPos placePos = context.getClickedPos();
        org.bukkit.block.BlockState replaced = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(serverLevel, placePos);
        if (!original.call(context, state)) return false;

        org.bukkit.event.block.BlockPlaceEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(
                serverLevel, placePos, serverPlayer, context.getHand(), state);
        io.lunararcdevs.lunararc.common.mod.util.LunarArcBlockPlaceCapture.capture(serverPlayer, placePos, event);
        net.minecraft.world.item.ItemStack held = serverPlayer.getItemInHand(context.getHand());
        LOGGER.debug("[place] {} placed {} at {} (held {} x{}, custom data={}, event cancelled={})", serverPlayer.getScoreboardName(), state.getBlock(), placePos,
                held.getItem(), held.getCount(), held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
                event != null && event.isCancelled());

        if (event != null && event.isCancelled()) {
            replaced.update(true, false);
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(serverLevel, placePos));
            return false;
        }
        return true;
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void lunararc$clearPlaceCapture(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        io.lunararcdevs.lunararc.common.mod.util.LunarArcBlockPlaceCapture.clear();
    }
}
