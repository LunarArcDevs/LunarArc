package io.lunararcdevs.lunararc.forge.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/**
 * NeoForge/Fabric/Quilt all bundle MixinExtras and bootstrap it themselves; Forge's own runtime
 * does not, so this has to be wired in manually via the jar's MixinConnector manifest attribute.
 */
public final class LunarArcMixinExtrasConnector_Forge implements IMixinConnector {
    @Override
    public void connect() {
        MixinExtrasBootstrap.init();
    }
}
