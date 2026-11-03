package io.lunararcdevs.lunararc.common.mixin.core.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option entities.spawning.all-chunks-are-slime-chunks: every chunk counts as a slime chunk. */
@Mixin(Slime.class)
public abstract class SlimeChunksOptionMixin {
    // the first nextInt is the swamp light roll (8); the second is the slime-chunk roll (10), where 0 means "this is a slime chunk"
    @WrapOperation(method = "checkSlimeSpawnRules", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 1))
    private static int lunararc$slimeChunk(RandomSource random, int bound, Operation<Integer> original,
            @Local(argsOnly = true) LevelAccessor level) {
        if (level instanceof ServerLevelAccessor accessor && WorldConfiguration.forLevel(accessor.getLevel()).tuning.allChunksAreSlimeChunks) {
            original.call(random, bound);
            return 0;
        }
        return original.call(random, bound);
    }
}
