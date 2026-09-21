package io.lunararcdevs.lunararc.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ForgeInstaller {
    public static void install(Path workingDir, java.util.Properties versions, Path selfPath) throws Exception {
        String mcVersion = LauncherUtils.requireVersion(versions, "minecraft");
        String forgeVersion = LauncherUtils.requireVersion(versions, "forge");
        Path installerJar = Paths.get("forge-" + mcVersion + "-" + forgeVersion + "-installer.jar");
        String url = String.format(
                "https://maven.minecraftforge.net/net/minecraftforge/forge/%s-%s/forge-%s-%s-installer.jar", mcVersion,
                forgeVersion, mcVersion, forgeVersion);

        if (!Files.exists(installerJar)) {
            ConsoleUI.printStep("install.downloading_libraries");
            Downloader.download(url, installerJar);
        }

        Path libDir = Paths.get("libraries");
        Path versionSentinel = libDir.resolve(".lunararc-forge-version");
        boolean needsInstall = true;

        if (Files.exists(versionSentinel)) {
            String installedVersion = Files.readString(versionSentinel).trim();
            if (installedVersion.equals(forgeVersion) && installIntact(libDir, mcVersion, forgeVersion)) {
                needsInstall = false;
            }
        }

        if (needsInstall) {
            ConsoleUI.printStep("install.forge.starting");

            ProcessBuilder pb = new ProcessBuilder(
                    LauncherUtils.getJavaExecutable(), "-jar", installerJar.toAbsolutePath().toString(), "--installServer");
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                ConsoleUI.printError("install.forge.failed_exit_code", exitCode);
                return;
            }
            Files.writeString(versionSentinel, forgeVersion);
        }

        ForgeLauncher.launch(workingDir, selfPath);
    }

    private static boolean installIntact(Path libDir, String mcVersion, String forgeVersion) throws Exception {
        Path argsFile = LauncherUtils.findArgsFile(
                libDir, "net/minecraftforge/forge/" + mcVersion + "-" + forgeVersion, false);
        if (argsFile == null) {
            ConsoleUI.printStep("install.forge.reinstall_missing_args", forgeVersion);
            return false;
        }

        String missingJar = LauncherUtils.missingLaunchJar(LauncherUtils.readArgsFileTokens(argsFile));
        if (missingJar != null) {
            ConsoleUI.printStep("install.forge.reinstall_missing_jar", forgeVersion, missingJar);
            return false;
        }
        return true;
    }
}
