package io.papermc.paper.plugin.entrypoint.classloader;

import io.papermc.paper.plugin.configuration.PluginMeta;

public class PaperClassloaderBytecodeModifier implements ClassloaderBytecodeModifier {
    private static final boolean NEEDS_REMAP = isFabricOrQuilt();
    private static final io.lunararcdevs.lunararc.common.mod.LunarArcRemapper REMAPPER =
            new io.lunararcdevs.lunararc.common.mod.LunarArcRemapper(true);

    private static boolean isFabricOrQuilt() {
        String platform = io.lunararcdevs.lunararc.common.mod.server.LunarArcServer.platformName();
        return "Fabric".equalsIgnoreCase(platform) || "Quilt".equalsIgnoreCase(platform);
    }

    @Override
    public byte[] modify(PluginMeta configuration, byte[] bytecode) {
        return NEEDS_REMAP ? REMAPPER.transform(bytecode) : bytecode;
    }
}
