package io.lunararcdevs.lunararc.common.server;

import io.papermc.paper.plugin.entrypoint.classloader.group.PaperPluginClassLoaderStorage;
import io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage;

/** Backs {@link io.lunararcdevs.lunararc.common.mixin.api.PaperClassLoaderStorageMixin}. */
public final class LunarArcPaperClassLoaderStorageHolder {
    public static final PaperClassLoaderStorage INSTANCE = new PaperPluginClassLoaderStorage();

    private LunarArcPaperClassLoaderStorageHolder() {}
}
