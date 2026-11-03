package io.lunararcdevs.lunararc.common.mixin.core.world;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Paper world options environment.portal-search-radius and environment.portal-search-vanilla-dimension-scaling. Vanilla searches 128
 * blocks for an overworld portal and 16 for a nether one (a scale of 8); the scaling option keeps that ratio for a custom radius.
 */
@Mixin(PortalForcer.class)
public abstract class PortalSearchRadiusOptionMixin {
    @Shadow @Final private ServerLevel level;

    @ModifyConstant(method = "findClosestPortalPosition", constant = @Constant(intValue = 128, ordinal = 0), require = 0)
    private int lunararc$overworldRadius(int vanilla) {
        return WorldConfiguration.forLevel(this.level).tuning.portalSearchRadius;
    }

    @ModifyConstant(method = "findClosestPortalPosition", constant = @Constant(intValue = 16, ordinal = 0), require = 0)
    private int lunararc$netherRadius(int vanilla) {
        WorldConfiguration.Tuning tuning = WorldConfiguration.forLevel(this.level).tuning;
        return tuning.portalSearchVanillaDimensionScaling ? tuning.portalSearchRadius / 8 : tuning.portalSearchRadius;
    }
}
