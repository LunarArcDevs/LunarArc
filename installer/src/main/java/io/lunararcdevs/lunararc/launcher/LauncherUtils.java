package io.lunararcdevs.lunararc.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class LauncherUtils {
    public static String requireVersion(java.util.Properties versions, String key) {
        String value = versions.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing launcher version property: " + key);
        }
        return value;
    }

    public static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        Path javaPath = Paths.get(javaHome, "bin", isWindows ? "java.exe" : "java");
        if (Files.exists(javaPath)) {
            return javaPath.toAbsolutePath().toString();
        }
        return "java";
    }

    public static java.util.List<String> inheritedJvmArguments(String... ownProperties) {
        java.util.Set<String> owned = new java.util.HashSet<>(java.util.Arrays.asList(ownProperties));
        java.util.List<String> inherited = new java.util.ArrayList<>();
        java.util.List<String> arguments;
        try {
            arguments = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        } catch (Throwable unavailable) {
            // No management bean (a stripped or restricted runtime). Better to launch with the
            // defaults than not at all.
            return inherited;
        }

        for (String argument : arguments) {
            if (argument == null || argument.isBlank()) continue;
            if (argument.startsWith("-javaagent") || argument.startsWith("-agentpath")
                    || argument.startsWith("-agentlib")) {
                continue;
            }
            if (argument.equals("-p") || argument.startsWith("--module-path")
                    || argument.startsWith("--add-opens") || argument.startsWith("--add-exports")
                    || argument.startsWith("--add-modules") || argument.startsWith("--patch-module")) {
                continue;
            }
            if (argument.startsWith("-D")) {
                String body = argument.substring(2);
                int equals = body.indexOf('=');
                String key = equals > 0 ? body.substring(0, equals) : body;
                if (owned.contains(key)) continue;
            } else if (!argument.startsWith("-X")) {
                // Anything else is a launcher-specific or unrecognised flag; passing it on is more
                // likely to stop the server booting than to help.
                continue;
            }
            inherited.add(argument);
        }
        return inherited;
    }

    private static final long LUNARARC_GIB = 1024L * 1024L * 1024L;

    public static java.util.List<String> serverJvmArguments(String... ownProperties) {
        java.util.List<String> arguments = new java.util.ArrayList<>(inheritedJvmArguments(ownProperties));
        for (String argument : arguments) {
            if (argument.startsWith("-Xmx") || argument.startsWith("-XX:MaxHeapSize")
                    || argument.startsWith("-XX:MaxRAMPercentage") || argument.startsWith("-XX:MaxRAMFraction")) {
                return arguments;
            }
        }

        String heap = defaultMaxHeapArgument();
        if (heap == null) {
            System.out.println("[LunarArc] No maximum heap was given and this machine is too small to"
                    + " improve on the JVM's own default. Start LunarArc as"
                    + " java -Xmx<size> -jar <jar> to choose one.");
            return arguments;
        }

        arguments.add(heap);
        System.out.println("[LunarArc] No maximum heap was given, so the server will run with "
                + heap.substring("-Xmx".length()) + ". A modded server that runs out of heap dies with"
                + " \"java.lang.OutOfMemoryError: Java heap space\"; to choose your own, start LunarArc"
                + " as java -Xmx6G -jar <jar>.");
        return arguments;
    }

    static String defaultMaxHeapArgument() {
        long physical = physicalMemoryBytes();
        if (physical < 3 * LUNARARC_GIB) return null;

        long target = (long) (physical * 0.6d);
        long ceiling = physical - LUNARARC_GIB;
        if (target > ceiling) target = ceiling;
        if (target > 8 * LUNARARC_GIB) target = 8 * LUNARARC_GIB;
        if (target < 2 * LUNARARC_GIB) return null;

        long megabytes = (target / (1024L * 1024L) / 256L) * 256L;
        return "-Xmx" + megabytes + "M";
    }

    static long physicalMemoryBytes() {
        try {
            Object bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            Class<?> extended = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (extended.isInstance(bean)) {
                for (String name : new String[] {"getTotalMemorySize", "getTotalPhysicalMemorySize"}) {
                    try {
                        Object value = extended.getMethod(name).invoke(bean);
                        if (value instanceof Number number && number.longValue() > 0L) {
                            return number.longValue();
                        }
                    } catch (ReflectiveOperationException | RuntimeException unavailable) {
                        // getTotalMemorySize is Java 14 and newer; the other is its predecessor.
                    }
                }
            }
        } catch (Throwable unavailable) {
            // No management bean at all, or a runtime without com.sun.management.
        }

        long own = Runtime.getRuntime().maxMemory();
        return own == Long.MAX_VALUE ? 0L : own * 4L;
    }

    static Path findArgsFile(Path librariesDir, String loaderPath) throws IOException {
        return findArgsFile(librariesDir, loaderPath, true);
    }

    static Path findArgsFile(Path librariesDir, String loaderPath, boolean fallBackToWholeTree)
            throws IOException {
        if (!Files.isDirectory(librariesDir)) return null;
        String preferred = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "win_args.txt" : "unix_args.txt";

        Path scoped = librariesDir.resolve(loaderPath);
        if (Files.isDirectory(scoped)) {
            Path found = firstMatch(scoped, preferred);
            if (found == null) found = firstMatch(scoped, null);
            if (found != null) return found;
        }
        if (!fallBackToWholeTree) return null;
        Path found = firstMatch(librariesDir, preferred);
        return found != null ? found : firstMatch(librariesDir, null);
    }

    /**
     * Every token an args file contributes to a launch command, comments and blank lines dropped.
     */
    static java.util.List<String> readArgsFileTokens(Path argsFile) throws IOException {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (String line : Files.readAllLines(argsFile)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            for (String part : trimmed.split(" ")) {
                if (!part.isEmpty()) tokens.add(part);
            }
        }
        return tokens;
    }

    private static Path firstMatch(Path root, String exactName) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> exactName == null
                            ? path.getFileName().toString().endsWith("_args.txt")
                            : path.getFileName().toString().equals(exactName))
                    .findFirst()
                    .orElse(null);
        }
    }

    static String missingLaunchJar(java.util.List<String> command) {
        for (int i = 0; i < command.size() - 1; i++) {
            if (!"-jar".equals(command.get(i))) continue;
            String jar = command.get(i + 1);
            if (!Files.isRegularFile(Path.of(jar))) return jar;
        }
        return null;
    }

    /**
     * The first entry in {@code launchJar}'s manifest Class-Path that's missing on disk, or null if
     * the jar and every entry it references are present - the Fabric/Quilt equivalent of
     * {@link #missingLaunchJar}, since those loaders' launch jars carry their dependency list in the
     * manifest rather than an args file.
     */
    static String missingManifestClassPathJar(Path launchJar) {
        if (!Files.isRegularFile(launchJar)) return launchJar.toString();
        java.util.jar.Manifest manifest;
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(launchJar.toFile())) {
            manifest = jarFile.getManifest();
        } catch (IOException unreadable) {
            return launchJar.toString();
        }
        String classPath = manifest == null ? null
                : manifest.getMainAttributes().getValue(java.util.jar.Attributes.Name.CLASS_PATH);
        if (classPath == null || classPath.isBlank()) return null;
        Path base = launchJar.toAbsolutePath().getParent();
        for (String entry : classPath.trim().split("\\s+")) {
            if (entry.isEmpty()) continue;
            if (!Files.isRegularFile(base.resolve(entry))) return entry;
        }
        return null;
    }
}
