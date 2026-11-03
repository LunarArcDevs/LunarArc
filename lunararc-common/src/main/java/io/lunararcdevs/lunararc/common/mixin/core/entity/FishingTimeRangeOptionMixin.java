package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option fishing-time-range: the vanilla wait for a fish to take the lure (100 to 600 ticks) uses the configured range. */
@Mixin(FishingHook.class)
public abstract class FishingTimeRangeOptionMixin {
    // catchingFish rolls three ranges; the third (ordinal 2) is the wait before a fish approaches
    @WrapOperation(method = "catchingFish", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I", ordinal = 2))
    private int lunararc$lureWait(RandomSource random, int min, int max, Operation<Integer> original) {
        if (min != 100 || max != 600) return original.call(random, min, max);
        WorldConfiguration.Tuning tuning = WorldConfiguration.forLevel(((FishingHook) (Object) this).level()).tuning;
        return original.call(random, tuning.fishingTimeMin, Math.max(tuning.fishingTimeMin, tuning.fishingTimeMax));
    }
}
