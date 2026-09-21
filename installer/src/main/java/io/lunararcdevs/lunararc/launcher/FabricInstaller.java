package io.lunararcdevs.lunararc.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FabricInstaller {
    public static void install(Path workingDir, java.util.Properties versions, Path selfPath) throws Exception {
        Path fabricServerJar = workingDir.resolve("fabric-server-launch.jar");
        Path minecraftServerJar = workingDir.resolve("server.jar");
        Path versionSentinel = workingDir.resolve(".lunararc-fabric-version");

        String mcVersion = LauncherUtils.requireVersion(versions, "minecraft");
        String fabricVersion = LauncherUtils.requireVersion(versions, "fabric");
        String installerVersion = LauncherUtils.requireVersion(versions, "fabricInstaller");

        Path installerJar = Paths.get("fabric-" + mcVersion + "-" + fabricVersion + "-installer.jar");

        String installerUrl = String.format(
                "https://maven.fabricmc.net/net/fabricmc/fabric-installer/%s/fabric-installer-%s.jar", installerVersion,
                installerVersion);

        String combinedVersion = fabricVersion + ":" + installerVersion;
        boolean needsInstall = !Files.exists(fabricServerJar) || !Files.exists(minecraftServerJar);

        if (!needsInstall && Files.exists(versionSentinel)) {
            String installedVersion = Files.readString(versionSentinel).trim();
            if (!installedVersion.equals(combinedVersion)) {
                needsInstall = true;
            }
        }

        if (!needsInstall) {
            String missingJar = LauncherUtils.missingManifestClassPathJar(fabricServerJar);
            if (missingJar != null) {
                ConsoleUI.printStep("install.fabric.reinstall_missing_jar", fabricVersion, missingJar);
                needsInstall = true;
            }
        }

        if (needsInstall) {
            ConsoleUI.printStep("install.downloading_libraries");
            if (!Files.exists(installerJar)) {
                Downloader.download(installerUrl, installerJar);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    LauncherUtils.getJavaExecutable(), "-jar", installerJar.toAbsolutePath().toString(), "server",
                    "-mcversion", mcVersion, "-loader", fabricVersion, "-downloadMinecraft");
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                ConsoleUI.printError("install.fabric.failed_exit_code", exitCode);
                return;
            }

            if (!Files.exists(fabricServerJar)) {
                ConsoleUI.printError("install.fabric.missing_output_jar", fabricServerJar.getFileName());
                return;
            }

            if (!Files.exists(minecraftServerJar)) {
                Path altJar = workingDir.resolve("minecraft_server." + mcVersion + ".jar");
                if (Files.exists(altJar)) {
                    Files.move(altJar, minecraftServerJar);
                }
            }

            Files.writeString(versionSentinel, combinedVersion);
        }

        FabricLauncher.launch(workingDir, selfPath);
    }
}
