package io.lunararcdevs.lunararc.launcher;

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class LoaderSameJvmLaunch {

    private LoaderSameJvmLaunch() {
    }

    private static void warnIfHeapTooSmall() {
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (maxHeap != Long.MAX_VALUE && maxHeap < 2L * 1024 * 1024 * 1024) {
            System.out.println("[LunarArc] Warning: this JVM's maximum heap is "
                    + (maxHeap / (1024 * 1024)) + "M, which a modded server will exhaust. The heap"
                    + " cannot be changed once the JVM is running - restart LunarArc as"
                    + " java -Xmx6G -jar <jar>.");
        }
    }

    static void launchFromArgsFile(Path workingDir, Path selfPath, Path argsFile, String loaderLabel)
            throws Exception {
        System.out.println("[LunarArc] Launching " + loaderLabel + " in-process...");
        warnIfHeapTooSmall();

        List<String> tokens = LauncherUtils.readArgsFileTokens(argsFile);

        List<String> gameArgs = new ArrayList<>();
        List<String> legacyClassPath = new ArrayList<>();
        String loaderLegacyClassPath = null;
        String mainClass = null;
        Path shimJar = null;
        int i = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i++);

            if (token.equals("-jar")) {
                if (i < tokens.size()) {
                    String jarToken = tokens.get(i++);
                    shimJar = resolveArgsFileJar(workingDir, argsFile, jarToken);
                    if (shimJar == null) {
                        throw new IllegalStateException(loaderLabel + "'s launch arguments reference "
                                + jarToken + ", which is not in " + workingDir.toAbsolutePath()
                                + ". It is produced by the " + loaderLabel
                                + " installer; delete libraries/.lunararc-" + loaderLabel.toLowerCase()
                                + "-version to install again.");
                    }
                    injectManifestClassPath(shimJar, legacyClassPath);
                    injectJar(shimJar, legacyClassPath);
                }
                while (i < tokens.size()) gameArgs.add(tokens.get(i++));
                break;
            } else if (token.startsWith("-D")) {
                String kv = token.substring(2);
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    String key = kv.substring(0, eq);
                    String value = kv.substring(eq + 1);
                    if (key.equals("fml.modsDir") || key.equals("fml.modFolder")) {
                        // skip
                    } else if (key.equals("legacyClassPath")) {
                        loaderLegacyClassPath = value;
                    } else {
                        System.setProperty(key, value);
                    }
                } else {
                    System.setProperty(kv, "");
                }
            } else if (token.equals("-p") || token.equals("--module-path")) {
                if (i < tokens.size()) mergeIntoBootLayer(workingDir, tokens.get(i++));
            } else if (token.startsWith("--module-path=")) {
                mergeIntoBootLayer(workingDir, token.substring("--module-path=".length()));
            } else if (token.equals("-cp") || token.equals("-classpath") || token.equals("--classpath")) {
                if (i < tokens.size()) injectPathList(tokens.get(i++), legacyClassPath);
            } else if (token.startsWith("--classpath=") || token.startsWith("-classpath=")) {
                injectPathList(token.substring(token.indexOf('=') + 1), legacyClassPath);
            } else if (token.equals("--add-opens") || token.equals("--add-exports")) {

                if (i < tokens.size()) applyModuleDirective(token, tokens.get(i++));
            } else if (token.startsWith("--add-opens=")) {
                applyModuleDirective("--add-opens", token.substring("--add-opens=".length()));
            } else if (token.startsWith("--add-exports=")) {
                applyModuleDirective("--add-exports", token.substring("--add-exports=".length()));
            } else if (token.startsWith("-X") || token.startsWith("-ea") || token.startsWith("-da")
                    || token.startsWith("--add-") || token.startsWith("-javaagent")) {

            } else if (token.contains(File.separator) && (token.endsWith(".jar") || token.endsWith(".zip"))) {

                injectJar(Paths.get(token), legacyClassPath);
            } else if (token.matches("[a-zA-Z][\\w.]+\\.[A-Z][\\w]*") && mainClass == null) {
                mainClass = token;
            } else {
                gameArgs.add(token);
            }
        }

        injectJar(selfPath, legacyClassPath);

        if (loaderLegacyClassPath != null) {
            for (String entry : loaderLegacyClassPath.split(File.pathSeparator)) {
                if (entry.isEmpty()) continue;
                Path resolved = Paths.get(entry);
                if (!Files.exists(resolved)) resolved = workingDir.resolve(resolved);
                String abs = resolved.toAbsolutePath().toString();
                if (!legacyClassPath.contains(abs)) legacyClassPath.add(abs);
            }
        }

        System.setProperty("legacyClassPath", String.join(File.pathSeparator, legacyClassPath));
        System.setProperty("ignoreList", "lunararc-no-preloaded-boot-modules");

        if (mainClass == null && shimJar != null) {
            mainClass = readMainClassFromManifest(shimJar);
        }

        if (mainClass == null) {
            throw new IllegalStateException(
                    "Could not determine " + loaderLabel + "'s main class from " + argsFile);
        }

        gameArgs.add("--nogui");
        if ("Forge".equals(loaderLabel)) {
            String selfEntry = selfPath.toAbsolutePath().toString();
            List<String> forgeEntries = new ArrayList<>(legacyClassPath.stream()
                    .filter(entry -> !entry.equals(selfEntry))
                    .toList());
            try {
                Path locatorJar = workingDir.resolve(".lunararc").resolve("mod_file").resolve("forge-locator.jar");
                LunarArcRuntime.extractNestedJarEntry(selfPath, "forge-locator.jar", locatorJar);
                forgeEntries.add(locatorJar.toAbsolutePath().toString());
            } catch (Exception e) {
                System.err.println("[LunarArc] Warning: could not extract the Forge mod locator jar - "
                        + "Forge will not discover LunarArc as a mod: " + e.getMessage());
            }
            System.setProperty("java.class.path", String.join(File.pathSeparator, forgeEntries));
        }
        invokeMain(mainClass, gameArgs, loaderLabel);
    }

    private static Path resolveArgsFileJar(Path workingDir, Path argsFile, String jarToken) {
        Path direct = Paths.get(jarToken);
        if (Files.exists(direct)) return direct.toAbsolutePath();
        Path besideArgsFile = argsFile.toAbsolutePath().getParent().resolve(jarToken);
        if (Files.exists(besideArgsFile)) return besideArgsFile;
        Path inWorkingDir = workingDir.resolve(jarToken);
        if (Files.exists(inWorkingDir)) return inWorkingDir;
        return null;
    }

    private static String readMainClassFromManifest(Path jar) throws Exception {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        }
    }

    static void launchFromManifestClassPath(Path launchJar, String mainClass, Path selfPath, String loaderLabel)
            throws Exception {
        System.out.println("[LunarArc] Launching " + loaderLabel + " in-process...");
        warnIfHeapTooSmall();

        injectManifestClassPath(launchJar, null);
        injectJar(launchJar, null);
        injectJar(selfPath, null);

        List<String> gameArgs = new ArrayList<>();
        gameArgs.add("--nogui");
        invokeMain(mainClass, gameArgs, loaderLabel);
    }

    private static void invokeMain(String mainClass, List<String> gameArgs, String loaderLabel) throws Exception {
        System.out.println("[LunarArc] Invoking " + loaderLabel + " main: " + mainClass);
        Method main = Class.forName(mainClass, true, ClassLoader.getSystemClassLoader())
                .getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) gameArgs.toArray(new String[0]));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(loaderLabel + " same-JVM launch failed: "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        }
    }

    private static void applyModuleDirective(String directive, String spec) {
        try {
            int slash = spec.indexOf('/');
            if (slash < 0) return;
            String moduleName = spec.substring(0, slash);
            String rest = spec.substring(slash + 1);
            int eq = rest.indexOf('=');
            String packageName = eq >= 0 ? rest.substring(0, eq) : rest;
            String targetName = eq >= 0 ? rest.substring(eq + 1) : "ALL-UNNAMED";

            Module module = ModuleLayer.boot().findModule(moduleName).orElse(null);
            if (module == null) return;

            Set<Module> targets = new HashSet<>();
            targets.add(ClassLoader.getSystemClassLoader().getUnnamedModule());
            if (!"ALL-UNNAMED".equals(targetName)) {
                ModuleLayer.boot().findModule(targetName).ifPresent(targets::add);
            }

            boolean isOpens = "--add-opens".equals(directive);
            LunarArcAgent.instrumentation.redefineModule(
                    module,
                    Set.of(),
                    isOpens ? Map.of() : Map.of(packageName, Set.copyOf(targets)),
                    isOpens ? Map.of(packageName, Set.copyOf(targets)) : Map.of(),
                    Set.of(),
                    Map.of()
            );
        } catch (Exception e) {
            System.err.println("[LunarArc] Warning: could not apply " + directive + " " + spec + ": " + e.getMessage());
        }
    }

    private static volatile boolean bootLayerInternalsOpened = false;

    @SuppressWarnings("unchecked")
    private static void mergeIntoBootLayer(Path workingDir, String modulePath) throws Exception {
        openBootLayerInternals();

        List<Path> paths = new ArrayList<>();
        for (String entry : modulePath.split(File.pathSeparator)) {
            if (entry.isEmpty()) continue;
            Path resolved = Paths.get(entry);
            if (!Files.exists(resolved)) resolved = workingDir.resolve(resolved);
            paths.add(resolved);
        }
        ModuleFinder finder = ModuleFinder.of(paths.toArray(new Path[0]));

        Method loadModule = accessibleMethod(Class.forName("jdk.internal.loader.BuiltinClassLoader"),
                "loadModule", ModuleReference.class);
        List<String> targetNames = new ArrayList<>();
        for (ModuleReference mref : finder.findAll()) {
            loadModule.invoke(ClassLoader.getSystemClassLoader(), mref);
            targetNames.add(mref.descriptor().name());
        }

        Configuration bootConfig = ModuleLayer.boot().configuration();
        Configuration config = Configuration.resolveAndBind(finder, List.of(bootConfig), finder, targetNames);

        Field graphField = accessibleField(Configuration.class, "graph");
        Map<ResolvedModule, Set<ResolvedModule>> graphMap =
                new HashMap<>((Map<ResolvedModule, Set<ResolvedModule>>) graphField.get(config));
        Field cfField = accessibleField(ResolvedModule.class, "cf");
        for (Map.Entry<ResolvedModule, Set<ResolvedModule>> entry : graphMap.entrySet()) {
            cfField.set(entry.getKey(), bootConfig);
            for (ResolvedModule m : entry.getValue()) cfField.set(m, bootConfig);
        }
        graphMap.putAll((Map<ResolvedModule, Set<ResolvedModule>>) graphField.get(bootConfig));
        graphField.set(bootConfig, new HashMap<>(graphMap));

        Set<ResolvedModule> oldBootModules = bootConfig.modules();
        Field modulesField = accessibleField(Configuration.class, "modules");
        Set<ResolvedModule> newModules = new HashSet<>(config.modules());
        modulesField.set(bootConfig, new HashSet<>(newModules));

        Field nameToModuleField = accessibleField(Configuration.class, "nameToModule");
        Map<String, ResolvedModule> nameToModuleMap =
                new HashMap<>((Map<String, ResolvedModule>) nameToModuleField.get(bootConfig));
        nameToModuleMap.putAll((Map<String, ResolvedModule>) nameToModuleField.get(config));
        nameToModuleField.set(bootConfig, new HashMap<>(nameToModuleMap));

        Field layerNameToModuleField = accessibleField(ModuleLayer.class, "nameToModule");
        Map<String, Module> bootNameToModule = (Map<String, Module>) layerNameToModuleField.get(ModuleLayer.boot());
        Method defineModules = accessibleMethod(Module.class, "defineModules",
                Configuration.class, Function.class, ModuleLayer.class);
        Map<String, Module> defined = (Map<String, Module>) defineModules.invoke(null, bootConfig,
                (Function<String, ClassLoader>) name -> ClassLoader.getSystemClassLoader(), ModuleLayer.boot());
        bootNameToModule.putAll(defined);

        newModules.addAll(oldBootModules);
        modulesField.set(bootConfig, new HashSet<>(newModules));

        Field layerModulesField = accessibleField(ModuleLayer.class, "modules");
        layerModulesField.set(ModuleLayer.boot(), null);
        Field servicesCatalogField = accessibleField(ModuleLayer.class, "servicesCatalog");
        servicesCatalogField.set(ModuleLayer.boot(), null);

        Method implAddReads = accessibleMethod(Module.class, "implAddReads", Module.class);
        for (ResolvedModule rm : config.modules()) {
            Module m = ModuleLayer.boot().findModule(rm.name()).orElse(null);
            if (m == null) continue;
            for (ResolvedModule brm : oldBootModules) {
                ModuleLayer.boot().findModule(brm.name()).ifPresent(bm -> {
                    try {
                        implAddReads.invoke(m, bm);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static void openBootLayerInternals() {
        if (bootLayerInternalsOpened) return;
        Module javaBase = Object.class.getModule();
        Module unnamed = ClassLoader.getSystemClassLoader().getUnnamedModule();
        LunarArcAgent.instrumentation.redefineModule(
                javaBase,
                Set.of(),
                Map.of(),
                Map.of(
                        "java.lang", Set.of(unnamed),
                        "java.lang.module", Set.of(unnamed),
                        "jdk.internal.loader", Set.of(unnamed)
                ),
                Set.of(),
                Map.of()
        );
        bootLayerInternalsOpened = true;
    }

    private static Field accessibleField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method accessibleMethod(Class<?> owner, String name, Class<?>... params) throws Exception {
        Method method = owner.getDeclaredMethod(name, params);
        method.setAccessible(true);
        return method;
    }

    private static void injectPathList(String pathList, List<String> legacyClassPath) {
        for (String entry : pathList.split(File.pathSeparator)) {
            if (!entry.isEmpty()) injectJar(Paths.get(entry), legacyClassPath);
        }
    }

    private static void injectManifestClassPath(Path jar, List<String> legacyClassPath) {
        Manifest manifest;
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            manifest = jarFile.getManifest();
        } catch (Exception e) {
            System.err.println("[LunarArc] Warning: could not read " + jar.getFileName() + "'s manifest: " + e.getMessage());
            return;
        }
        String classPath = manifest == null ? null
                : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (classPath == null || classPath.isBlank()) return;
        Path base = jar.toAbsolutePath().getParent();
        for (String entry : classPath.trim().split("\\s+")) {
            if (!entry.isEmpty()) injectJar(base.resolve(entry), legacyClassPath);
        }
    }

    private static void injectJar(Path jar, List<String> legacyClassPath) {
        if (jar == null) return;
        String name = jar.toString();
        if (!name.endsWith(".jar") && !name.endsWith(".zip")) return;
        if (!Files.exists(jar)) return;
        try {
            LunarArcAgent.instrumentation.appendToSystemClassLoaderSearch(new JarFile(jar.toFile()));
            if (legacyClassPath != null) legacyClassPath.add(jar.toAbsolutePath().toString());
        } catch (Exception e) {
            System.err.println("[LunarArc] Warning: could not inject " + jar.getFileName() + ": " + e.getMessage());
        }
    }
}
