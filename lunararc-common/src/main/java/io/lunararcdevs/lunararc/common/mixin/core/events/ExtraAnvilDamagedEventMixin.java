package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.event.LunarArcBlockEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AnvilBlock.class)
public abstract class ExtraAnvilDamagedEventMixin {
    @WrapMethod(method = "damage", require = 0)
    private static @Nullable BlockState lunararc$damage(BlockState state, Operation<BlockState> original) {
        BlockState damaged = original.call(state);
        Player player = LunarArcBlockEvents.ANVIL_TAKER.get();
        return player == null ? damaged : LunarArcBlockEvents.anvilDamaged(player, state, damaged);
    }
}
