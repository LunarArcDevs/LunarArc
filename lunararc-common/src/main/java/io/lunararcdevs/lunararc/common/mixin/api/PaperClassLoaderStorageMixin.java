package io.lunararcdevs.lunararc.common.mixin.api;

import io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Same reasoning as RegistryAccessMixin: PaperClassLoaderStorageAccess resolves its instance via
 * ServiceLoader inside its own static initializer, so a failed lookup there poisons the class
 * permanently rather than just throwing per-call - it must never be touched at all.
 *
 * @author LunarArc
 * @reason Bypasses PaperClassLoaderStorageAccess's ServiceLoader lookup entirely.
 */
@Mixin(value = PaperClassLoaderStorage.class, remap = false)
public interface PaperClassLoaderStorageMixin {

    @Overwrite
    static PaperClassLoaderStorage instance() {
        return io.lunararcdevs.lunararc.common.server.LunarArcPaperClassLoaderStorageHolder.INSTANCE;
    }
}
