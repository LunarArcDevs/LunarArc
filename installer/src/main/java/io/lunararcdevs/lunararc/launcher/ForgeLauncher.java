package io.lunararcdevs.lunararc.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ForgeLauncher {
    public static void launch(Path workingDir, Path selfPath) throws Exception {
        removeLegacyBridgeCopy();

        Path libDir = Paths.get("libraries");
        Path argsFile = LauncherUtils.findArgsFile(libDir, "net/minecraftforge/forge");
        if (argsFile == null) {
            System.err.println("[LunarArc] Error: Could not find Forge's args file under "
                    + libDir.toAbsolutePath().resolve("net/minecraftforge/forge")
                    + ". The Forge installer has not run, or did not finish.");
            return;
        }

        if (LunarArcAgent.instrumentation == null) {
            System.err.println("[LunarArc] Error: LunarArc's launch agent did not attach; "
                    + "same-JVM launch is required for Forge and no fallback is available.");
            System.exit(1);
            return;
        }

        // LunarArc's shared mixin refmap only carries a Fabric-intermediary mapping table, which
        // does not resolve against Forge's Mojang-named runtime; every mixin's own selector is
        // already a real Mojang name, so skipping the refmap outright is correct here.
        System.setProperty("mixin.env.disableRefMap", "true");

        LoaderSameJvmLaunch.launchFromArgsFile(workingDir, selfPath, argsFile, "Forge");
    }

    private static void removeLegacyBridgeCopy() {
        try {
            Path oldBridge = Paths.get(".lunararc", "mods", "lunararc-bridge.jar");
            if (Files.deleteIfExists(oldBridge)) {
                System.out.println("[LunarArc] Removed legacy .lunararc/mods/lunararc-bridge.jar; "
                        + "LunarArc now boots from the loader bootstrap layer.");
            }
        } catch (Exception e) {
            System.err.println("[LunarArc] Warning: could not remove legacy "
                    + ".lunararc/mods/lunararc-bridge.jar: " + e.getMessage());
        }
    }
}
