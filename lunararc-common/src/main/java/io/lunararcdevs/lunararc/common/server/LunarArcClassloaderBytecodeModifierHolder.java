package io.lunararcdevs.lunararc.common.server;

import io.papermc.paper.plugin.entrypoint.classloader.ClassloaderBytecodeModifier;
import io.papermc.paper.plugin.entrypoint.classloader.PaperClassloaderBytecodeModifier;

public final class LunarArcClassloaderBytecodeModifierHolder {
    public static final ClassloaderBytecodeModifier INSTANCE = new PaperClassloaderBytecodeModifier();
    private LunarArcClassloaderBytecodeModifierHolder() {}
}
