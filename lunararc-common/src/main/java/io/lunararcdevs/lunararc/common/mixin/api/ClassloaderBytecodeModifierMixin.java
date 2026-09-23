package io.lunararcdevs.lunararc.common.mixin.api;

import io.papermc.paper.plugin.entrypoint.classloader.ClassloaderBytecodeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ClassloaderBytecodeModifier.class, remap = false)
public interface ClassloaderBytecodeModifierMixin {
    @Overwrite
    static ClassloaderBytecodeModifier bytecodeModifier() {
        return io.lunararcdevs.lunararc.common.server.LunarArcClassloaderBytecodeModifierHolder.INSTANCE;
    }
}
