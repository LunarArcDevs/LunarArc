package io.lunararcdevs.lunararc.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.stream.Stream;

final class SpigotWorldMigration {

    private static final String MARKER = "lunararc-spigot-migration.properties";
    private static final String TEMP_SUFFIX = ".lunararc-migrating";
    private static final String ARCHIVE_FOLDER = "Migrated";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private enum Result { COPIED, NOTHING, SKIPPED, FAILED }

    private SpigotWorldMigration() {
    }

    static void run(Path serverDir) {
        try {
            Path world = serverDir.resolve(levelName(serverDir)).normalize();
            if (!world.startsWith(serverDir) || !Files.isDirectory(world) || Files.exists(world.resolve(MARKER))) {
                return;
            }
            String level = world.getFileName().toString();
            Path netherFolder = serverDir.resolve(level + "_nether");
            Path endFolder = serverDir.resolve(level + "_the_end");

            Result nether = copyDimension(serverDir, netherFolder.resolve("DIM-1"), world.resolve("DIM-1"));
            String netherOriginal = nether == Result.COPIED ? archive(serverDir, netherFolder) : null;
            Result end = copyDimension(serverDir, endFolder.resolve("DIM1"), world.resolve("DIM1"));
            String endOriginal = end == Result.COPIED ? archive(serverDir, endFolder) : null;

            boolean failed = nether == Result.FAILED || end == Result.FAILED;
            boolean found = nether != Result.NOTHING || end != Result.NOTHING;
            if (found && !failed) writeMarker(world, nether, end, netherOriginal, endOriginal);
        } catch (Throwable unexpected) {
            ConsoleUI.printError("migration.spigot.failed", "world", "world", String.valueOf(unexpected));
        }
    }

    private static String levelName(Path serverDir) {
        Path properties = serverDir.resolve("server.properties");
        if (Files.isRegularFile(properties)) {
            try (InputStream in = Files.newInputStream(properties)) {
                Properties loaded = new Properties();
                loaded.load(in);
                String name = loaded.getProperty("level-name", "world").trim();
                if (!name.isEmpty()) return name;
            } catch (IOException ignored) {
            }
        }
        return "world";
    }

    private static Result copyDimension(Path serverDir, Path source, Path target) {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            if (!Files.isDirectory(source) || isEmpty(source)) return Result.NOTHING;
            if (Files.isDirectory(target) && !isEmpty(target)) {
                ConsoleUI.printStep("migration.spigot.skipped_exists", display(serverDir, target));
                return Result.SKIPPED;
            }

            long[] expected = scan(source);
            long bytes = expected[0];
            if (Files.getFileStore(target.getParent()).getUsableSpace() < bytes + bytes / 20) {
                throw new IOException("not enough free disk space (" + megabytes(bytes) + " MB needed)");
            }
            ConsoleUI.printStep("migration.spigot.found", display(serverDir, source), display(serverDir, target), megabytes(bytes));

            deleteTemp(temp);
            copyTree(source, temp);
            long[] copied = scan(temp);
            if (copied[0] != expected[0] || copied[1] != expected[1]) {
                throw new IOException("copy verification failed (expected " + expected[1] + " files / " + expected[0]
                        + " bytes, got " + copied[1] + " / " + copied[0] + ")");
            }
            Files.deleteIfExists(target);
            move(temp, target);
            ConsoleUI.printSuccess("migration.spigot.copied", copied[1], display(serverDir, target));
            return Result.COPIED;
        } catch (IOException | RuntimeException failure) {
            deleteTemp(temp);
            ConsoleUI.printError("migration.spigot.failed", display(serverDir, source), display(serverDir, target),
                    reason(failure));
            return Result.FAILED;
        }
    }

    private static String archive(Path serverDir, Path folder) {
        try {
            if (!Files.isDirectory(folder)) return null;
            Path archive = serverDir.resolve(ARCHIVE_FOLDER);
            Files.createDirectories(archive);
            Path destination = archive.resolve(folder.getFileName().toString());
            if (Files.exists(destination)) {
                destination = archive.resolve(folder.getFileName() + "-" + STAMP.format(Instant.now()));
            }
            move(folder, destination);
            ConsoleUI.printSuccess("migration.spigot.moved", display(serverDir, folder), display(serverDir, destination));
            return display(serverDir, destination);
        } catch (IOException | RuntimeException failure) {
            ConsoleUI.printError("migration.spigot.move_failed", display(serverDir, folder), ARCHIVE_FOLDER, reason(failure));
            return null;
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long[] scan(Path directory) throws IOException {
        long[] totals = {0, 0};
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                totals[0] += attrs.size();
                totals[1]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return totals;
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        }
    }

    private static void deleteTemp(Path temp) {
        if (!temp.getFileName().toString().endsWith(TEMP_SUFFIX) || !Files.exists(temp)) return;
        try {
            Files.walkFileTree(temp, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void writeMarker(Path world, Result nether, Result end, String netherOriginal, String endOriginal) throws IOException {
        Properties marker = new Properties();
        marker.setProperty("migratedAt", Instant.now().toString());
        marker.setProperty("nether", nether.name());
        marker.setProperty("end", end.name());
        marker.setProperty("netherOriginal", netherOriginal != null ? netherOriginal : "left in place");
        marker.setProperty("endOriginal", endOriginal != null ? endOriginal : "left in place");
        try (OutputStream out = Files.newOutputStream(world.resolve(MARKER))) {
            marker.store(out, "LunarArc one-time Spigot world migration");
        }
    }

    private static String reason(Exception failure) {
        if (failure instanceof java.nio.file.FileSystemException fileFailure) {
            return fileFailure.getReason() != null ? fileFailure.getReason() : fileFailure.getClass().getSimpleName();
        }
        return String.valueOf(failure.getMessage());
    }

    private static String display(Path serverDir, Path path) {
        return serverDir.relativize(path).toString().replace('\\', '/');
    }

    private static long megabytes(long bytes) {
        return bytes / (1024 * 1024);
    }
}
