package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option entities.behavior.disable-player-crits: a player's attack is never a critical hit. */
@Mixin(Player.class)
public abstract class BehaviorOptionsMixins {
    @ModifyReturnValue(method = "canCriticalAttack", at = @At("RETURN"), require = 0)
    private boolean lunararc$noPlayerCrits(boolean vanilla, Entity target) {
        Player self = (Player) (Object) this;
        return vanilla && !WorldConfiguration.forLevel(self.level()).tuning.disablePlayerCrits;
    }
}
