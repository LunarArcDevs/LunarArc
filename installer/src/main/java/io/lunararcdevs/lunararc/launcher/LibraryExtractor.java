package io.lunararcdevs.lunararc.launcher;

import java.io.File;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LibraryExtractor {

    private static final String PREFIX = "META-INF/libraries/";

    public static void extractLibraries() {
        try {
            File selfJar = new File(LibraryExtractor.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            int extracted = 0;
            try (JarFile jar = new JarFile(selfJar)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(PREFIX)) continue;

                    Path dest = Paths.get(name.substring("META-INF/".length()));
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                        continue;
                    }

                    long expectedSize = entry.getSize();
                    if (Files.isRegularFile(dest) && expectedSize >= 0 && Files.size(dest) == expectedSize) {
                        continue;
                    }

                    Files.createDirectories(dest.getParent());
                    Path temp = dest.resolveSibling(dest.getFileName() + ".tmp");
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ignored) {
                        Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    extracted++;
                }
            }

            if (extracted > 0) {
                ConsoleUI.printSuccess("install.libraries.extracted", extracted);
            }
        } catch (Exception e) {
            ConsoleUI.printError("install.libraries.extract_failed", e.getMessage());
        }
    }
}
