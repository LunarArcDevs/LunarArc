package io.lunararcdevs.lunararc.common.mod.server;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Common hybrid-server anchor. The active loader installs only its identity and
 * class loader here; Bukkit/Paper gameplay APIs do not dispatch through it.
 */
public final class LunarArcServer {
    public static final Logger LOGGER = LoggerFactory.getLogger("LunarArc");

    private static volatile MinecraftServer minecraftServer;
    private static volatile ClassLoader modClassLoader = LunarArcServer.class.getClassLoader();
    private static volatile String platformName = "unknown";

    private LunarArcServer() {}

    public static synchronized void installPlatform(String name, ClassLoader loader) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(loader, "loader");
        if (!"unknown".equals(platformName) && !platformName.equals(name)) {
            throw new IllegalStateException(
                    "LunarArc platform already installed as " + platformName + ", cannot replace it with " + name);
        }
        platformName = name;
        modClassLoader = loader;
        // Mod-loading (and the incompatible-mod screening that follows this call in every
        // platform's mod constructor) happens well before MinecraftServer exists, so this is
        // the earliest point lunararc.conf can be read - relying solely on MinecraftServerMixin's
        // later load() would leave incompatible.crash at its default for that screening pass.
        io.lunararcdevs.lunararc.common.config.LunarArcConfig.load();
        io.lunararcdevs.lunararc.common.server.LunarArcPaperServiceBootstrap.ensureInstalled();
        LOGGER.debug("[LunarArc] Platform: {}", name);
    }

    public static String platformName() {
        return platformName;
    }

    public static ClassLoader modClassLoader() {
        return Objects.requireNonNull(modClassLoader, "modClassLoader");
    }

    public static void attach(MinecraftServer server) {
        minecraftServer = Objects.requireNonNull(server, "server");
        io.lunararcdevs.lunararc.common.server.LunarArcDynamicBukkitEnums.registerAll(server);
        installApiFacade();
    }

    private static void installApiFacade() {
        try {
            io.lunararcdevs.lunararc.api.LunarArcVersion.setCurrent(LunarArcServerApiImpl.buildVersion());
        } catch (IllegalStateException alreadySet) {
            // Already set by an earlier attach() — fine on restart-without-full-JVM-reload paths.
        }
        try {
            io.lunararcdevs.lunararc.api.LunarArcServer.setServer(LunarArcServerApiImpl.INSTANCE);
        } catch (IllegalStateException alreadySet) {
            // Same as above.
        }
    }

    public static synchronized void detach(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (minecraftServer == server) {
            minecraftServer = null;
        }
    }

    public static MinecraftServer minecraftServer() {
        return minecraftServer;
    }

    public static MinecraftServer requireMinecraftServer() {
        return Objects.requireNonNull(minecraftServer, "MinecraftServer has not been attached yet");
    }
}
