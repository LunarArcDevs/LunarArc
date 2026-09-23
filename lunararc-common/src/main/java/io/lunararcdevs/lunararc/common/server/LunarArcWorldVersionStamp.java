package io.lunararcdevs.lunararc.common.server;

import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

/**
 * Records which LunarArc build last loaded this world, and which build loaded it before that, in the
 * world's own save folder rather than under .lunararc - so the record survives an operator
 * deleting/resetting that runtime cache.
 */
public final class LunarArcWorldVersionStamp {
    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc");
    private static final String FILE_NAME = "lunararc-version.properties";

    private static volatile String previousVersion = "";

    private LunarArcWorldVersionStamp() {}

    public static String previousVersion() {
        return previousVersion;
    }

    public static void stamp(MinecraftServer server) {
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            write(worldRoot, LunarArcVersionInfo.lunarArcVersion(), LunarArcVersionInfo.minecraftVersion(),
                    LunarArcServer.platformName());
        } catch (IOException error) {
            LOGGER.warn("[LunarArc] Could not write world-folder version stamp", error);
        }
    }

    static void write(Path worldRoot, String version, String minecraft, String platform) throws IOException {
        Files.createDirectories(worldRoot);
        Path target = worldRoot.resolve(FILE_NAME);

        String previous = "";
        if (Files.isRegularFile(target)) {
            Properties existing = new Properties();
            try (InputStream in = Files.newInputStream(target)) {
                existing.load(in);
            } catch (IOException | IllegalArgumentException unreadable) {
                existing.clear();
            }
            String recorded = existing.getProperty("version", "").trim();
            String carried = existing.getProperty("previous", "").trim();
            previous = !recorded.isEmpty() && !recorded.equals(version) ? recorded : carried;
        }
        previousVersion = previous;

        Properties props = new Properties();
        props.setProperty("version", version);
        if (!previous.isEmpty()) props.setProperty("previous", previous);
        props.setProperty("minecraft", minecraft);
        props.setProperty("platform", platform);
        props.setProperty("lastBoot", Instant.now().toString());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, "LunarArc version that last loaded this world (and the one before it) - informational only, safe to delete");
        }
        try {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notSupported) {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
