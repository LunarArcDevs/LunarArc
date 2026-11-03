package io.lunararcdevs.lunararc.common.mixin.core.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.lunararcdevs.lunararc.common.event.LunarArcBlockEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AnvilMenu.class)
public abstract class ExtraAnvilTakerContextMixin {
    @WrapMethod(method = "onTake", require = 0)
    private void lunararc$taker(Player player, ItemStack stack, Operation<Void> original) {
        Player before = LunarArcBlockEvents.ANVIL_TAKER.get();
        LunarArcBlockEvents.ANVIL_TAKER.set(player);
        try {
            original.call(player, stack);
        } finally {
            if (before == null) {
                LunarArcBlockEvents.ANVIL_TAKER.remove();
            } else {
                LunarArcBlockEvents.ANVIL_TAKER.set(before);
            }
        }
    }
}
