package org.bukkit.plugin.java;

import org.bukkit.plugin.PluginDescriptionFile;
import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import io.lunararcdevs.lunararc.common.mod.LunarArcRemapper;
import io.lunararcdevs.lunararc.common.mod.PluginMappingNamespace;
import io.lunararcdevs.lunararc.common.server.LunarArcPluginLoader;
import io.lunararcdevs.lunararc.common.server.LunarArcPluginFixManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.HexFormat;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginClassLoader extends URLClassLoader
        implements io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader {

    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
    private final PluginDescriptionFile description;
    private final File dataFolder;
    private final File file;
    private final LunarArcPluginLoader pluginLoader;
    private final ClassLoader libraryLoader;
    private final ClassLoader paperLibraryLoader;
    private final Set<String> joinedPaperDependencies;
    private JavaPlugin plugin;
    private final PluginMappingNamespace mappingNamespace;
    private final boolean remapNms;
    private final boolean paperPlugin;
    private final LunarArcRemapper remapper;
    private final Path transformedCacheRoot;
    private final io.lunararcdevs.lunararc.common.mod.LegacyNmsTranslator legacyNms;
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

    public io.papermc.paper.plugin.provider.entrypoint.DependencyContext dependencyContext;
    private volatile io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup group;

    private static volatile ClassLoader compatibilityLibraryLoader;

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public PluginClassLoader(LunarArcPluginLoader loader, ClassLoader parent, PluginDescriptionFile description,
            File dataFolder, File file) throws MalformedURLException {
        super(new URL[]{file.toURI().toURL()}, parent);
        this.description = description;
        io.lunararcdevs.lunararc.common.server.LunarArcPluginFixManager
                .applyPluginProperties(description == null ? null : description.getName());
        io.lunararcdevs.lunararc.common.server.LunarArcModuleOpener.openMinecraftModuleTo(
                this, description == null ? null : description.getName());
        this.dataFolder = dataFolder;
        this.file = file;
        this.pluginLoader = loader;
        this.mappingNamespace = PluginMappingNamespace.detect(file);
        this.legacyNms = PluginMappingNamespace.legacyTranslatorFor(file,
                description == null ? file.getName() : description.getName(),
                description == null ? null : description.getAPIVersion());
        this.remapNms = this.mappingNamespace.requiresNmsRemap() || alwaysRemapsOnThisPlatform();
        this.paperPlugin = isPaperPlugin(file);
        this.remapper = new LunarArcRemapper(this.remapNms);
        this.transformedCacheRoot = createTransformedCacheRoot(file, this.mappingNamespace,
                this.legacyNms == null ? null : this.legacyNms.fingerprint());
        this.libraryLoader = wrapPluginLibraryLoader(
                io.lunararcdevs.lunararc.common.server.LunarArcLegacyLibraryResolver.create(description, parent),
                "bukkit-libraries");
        this.paperLibraryLoader = wrapPluginLibraryLoader(
                io.lunararcdevs.lunararc.common.server.LunarArcPaperPluginSupport
                        .createLibraryLoader(file, description, dataFolder, parent),
                "paper-libraries");
        this.joinedPaperDependencies = this.paperPlugin
                ? io.lunararcdevs.lunararc.common.server.LunarArcPaperPluginSupport.joinedDependencies(file)
                : java.util.Set.of();
        this.pluginLoader.getClassSpace().register(this, description, this.paperPlugin);
        this.group = io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage.instance()
                .registerSpigotGroup(this);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return findClass(name, true);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (isPlatformClass(name)) {
                    try {
                        loaded = loadPlatformClassWithBackstop(name);
                    } catch (ClassNotFoundException notOnServer) {
                        if (isServerOwnedClass(name)) throw notOnServer;
                        loaded = findClass(name, true);
                    }
                } else {
                    try {
                        loaded = findClass(name, true);
                    } catch (ClassNotFoundException ignored) {
                        loaded = getParent().loadClass(name);
                    }
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    public Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {

        Class<?> result = classes.get(name);
        if (result != null) return result;

        if (isPlatformClass(name)) {
            try { return loadPlatformClassWithBackstop(name); } catch (ClassNotFoundException ignored) {}
        }

        String path = name.replace('.', '/').concat(".class");
        URL url = findResource(path);
        if (url != null) {
            try (InputStream is = url.openStream()) {
                byte[] original = is.readAllBytes();
                byte[] bytecode = loadOrCreateTransformedClass(path, name, original);

                String pkg = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : null;
                if (pkg != null && getDefinedPackage(pkg) == null) {
                    definePackage(pkg, null, null, null, null, null, null, null);
                }

                CodeSource cs = new CodeSource(file.toURI().toURL(), (Certificate[]) null);
                ProtectionDomain pd = new ProtectionDomain(cs, null, this, null);
                result = defineClass(name, bytecode, 0, bytecode.length, pd);
                classes.put(name, result);
                return result;
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        if (checkGlobal) {
            try {
                return lunararc$traceSource(name, "another plugin (dependency class space)",
                        this.pluginLoader.getClassSpace().findDependencyClass(
                                this, name, visibleDependencies(), !this.paperPlugin));
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (paperLibraryLoader != null) {
            try { return lunararc$traceSource(name, "paper library loader", paperLibraryLoader.loadClass(name)); }
            catch (ClassNotFoundException ignored) {}
        }

        if (libraryLoader != null) {
            try { return lunararc$traceSource(name, "plugin library loader", libraryLoader.loadClass(name)); }
            catch (ClassNotFoundException ignored) {}
        }

        ClassLoader compatibilityLoader = compatibilityLibraryLoader();
        if (compatibilityLoader != null) {
            try { return lunararc$traceSource(name, "compatibility library loader", compatibilityLoader.loadClass(name)); }
            catch (ClassNotFoundException ignored) {}
        }

        ClassLoader modCL = LunarArcServer.modClassLoader();
        if (modCL != null && modCL != getParent()) {
            try { return lunararc$traceSource(name, "mod class loader", modCL.loadClass(name)); }
            catch (ClassNotFoundException ignored) {}
        }

        try { return lunararc$traceSource(name, "parent", getParent().loadClass(name)); }
        catch (ClassNotFoundException ignored) {}

        throw new ClassNotFoundException(name);
    }

    private static Class<?> lunararc$traceSource(String name, String source, Class<?> resolved) {
        if (io.lunararcdevs.lunararc.common.LunarArcDebug.CLASSLOAD && resolved != null) {
            ClassLoader definer = resolved.getClassLoader();
            io.lunararcdevs.lunararc.common.LunarArcDebug.classload("{}: answered by {}, defined by {}",
                    name, source, definer == null ? "the bootstrap loader" : definer.getClass().getName()
                            + "@" + Integer.toHexString(System.identityHashCode(definer)));
        }
        return resolved;
    }

    private byte[] loadOrCreateTransformedClass(String resourcePath, String className, byte[] original) throws IOException {

        Path cached = transformedCacheRoot.resolve("classes")
                .resolve(cacheKey(resourcePath) + ".class").normalize();
        if (!cached.startsWith(transformedCacheRoot)) {
            throw new IOException("Unsafe transformed plugin cache path: " + resourcePath);
        }
        if (Files.isRegularFile(cached)) {
            try {
                byte[] bytes = Files.readAllBytes(cached);
                if (bytes.length >= 8) return bytes;
            } catch (IOException ignored) {
            }
        }

        LunarArcRemapper activeRemapper = this.remapper;
        if (!this.remapNms && hasLegacyCraftBukkitReference(original)) {

            activeRemapper = new LunarArcRemapper(true);
        }
        byte[] staged = this.paperPlugin
                ? original
                : org.bukkit.craftbukkit.util.CraftMagicNumbers
                        .applyPaperPluginRewrites(this.description, resourcePath, original);
        if (this.legacyNms != null) {
            staged = this.legacyNms.translate(staged);
        }
        byte[] transformed = activeRemapper.transform(staged, className.replace('.', '/'));
        transformed = LunarArcPluginFixManager.injectPluginFix(className, transformed);
        Files.createDirectories(cached.getParent());
        Path temp = cached.resolveSibling(cached.getFileName() + ".tmp");
        Files.write(temp, transformed);
        try {
            Files.move(temp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, cached, StandardCopyOption.REPLACE_EXISTING);
        }
        return transformed;
    }

    static boolean hasLegacyCraftBukkitReference(byte[] bytecode) {
        byte[] marker = "org/bukkit/craftbukkit/v".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i <= bytecode.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (bytecode[i + j] != marker[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static String cacheKey(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Path createTransformedCacheRoot(File pluginFile, PluginMappingNamespace mappingNamespace,
            String legacyFingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(pluginFile.toPath())) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            digest.update(mappingNamespace.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.minecraftVersion()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(LunarArcServer.platformName().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            digest.update("compat-transform-v30-intermediary-arity-guard".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (legacyFingerprint != null) {
                digest.update(legacyFingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            digest.update(io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.lunarArcVersion()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ClassLoader owner = PluginClassLoader.class.getClassLoader();
            String mappingBase = "mappings/" + io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo.minecraftVersion() + "/";
            for (String resource : new String[]{"paper-reobf.tiny", "plugin-remap.tsv", "intermediary.tiny"}) {
                try (InputStream mapping = owner.getResourceAsStream(mappingBase + resource)) {
                    if (mapping != null) {
                        byte[] buffer = new byte[32 * 1024];
                        int count;
                        while ((count = mapping.read(buffer)) >= 0) {
                            if (count > 0) digest.update(buffer, 0, count);
                        }
                    }
                }
            }
            String key = HexFormat.of().formatHex(digest.digest());
            Path root = io.lunararcdevs.lunararc.common.LunarArcPaths.transformedPlugins()
                    .resolve(key.substring(0, 24));
            Files.createDirectories(root);
            return root;
        } catch (Exception error) {
            Path fallback = io.lunararcdevs.lunararc.common.LunarArcPaths.transformedPlugins()
                    .resolve(normalize(pluginFile.getName()));
            try { Files.createDirectories(fallback); } catch (IOException ignored) {}
            return fallback;
        }
    }

    private Class<?> loadPlatformClass(String name) throws ClassNotFoundException {
        try {
            Class<?> found = getParent().loadClass(name);
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.CLASSLOAD) {
                io.lunararcdevs.lunararc.common.LunarArcDebug.classload("{}: parent resolved as requested", name);
            }
            return found;
        } catch (ClassNotFoundException notUnderRequestedName) {
            if (name.startsWith("org.bukkit.craftbukkit.") || (this.remapNms && name.startsWith("net.minecraft."))) {
                String mapped = remapper.map(name.replace('.', '/')).replace('/', '.');
                if (!mapped.equals(name)) {
                    try {
                        Class<?> found = getParent().loadClass(mapped);
                        if (io.lunararcdevs.lunararc.common.LunarArcDebug.CLASSLOAD) {
                            io.lunararcdevs.lunararc.common.LunarArcDebug.classload(
                                    "{}: absent under that name, parent resolved mapped name {}", name, mapped);
                        }
                        return found;
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.CLASSLOAD) {
                io.lunararcdevs.lunararc.common.LunarArcDebug.classload("{}: not on the parent under any name", name);
            }
            throw notUnderRequestedName;
        }
    }

    /**
     * {@link #loadPlatformClass} plus the mod class loader as a last resort.
     */
    private Class<?> loadPlatformClassWithBackstop(String name) throws ClassNotFoundException {
        if (name.startsWith("com.google.gson.")) {
            return com.google.gson.JsonElement.class.getClassLoader().loadClass(name);
        }
        try {
            return loadPlatformClass(name);
        } catch (ClassNotFoundException notOnParent) {
            ClassLoader modClassLoader = LunarArcServer.modClassLoader();
            if (modClassLoader != null && modClassLoader != getParent()) {
                try {
                    return modClassLoader.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                }
            }
            throw notOnParent;
        }
    }

    private static boolean isServerOwnedClass(String name) {
        return name.startsWith("java.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("org.bukkit.")
                || name.startsWith("net.minecraft.")
                || name.startsWith("io.papermc.paper.")
                || name.startsWith("com.destroystokyo.paper.")
                || name.startsWith("net.kyori.")
                || name.startsWith("com.google.gson.");
    }

    private ClassLoader wrapPluginLibraryLoader(ClassLoader raw, String cacheName) {
        if (raw == null) return raw;
        if (!(raw instanceof URLClassLoader urls)) {
            java.util.logging.Logger.getLogger(description.getName()).fine(
                    "Wrapping non-URL Paper library loader " + raw.getClass().getName()
                            + " with LunarArc's concrete plugin mapping compatibility transformer");
            return new TransformingDelegatePluginLibraryClassLoader(
                    raw, this.remapper, this.transformedCacheRoot.resolve(cacheName));
        }

        URL[] libraryUrls = urls.getURLs();
        ClassLoader libraryParent = urls.getParent();
        try { urls.close(); } catch (IOException ignored) {}
        return new TransformingPluginLibraryClassLoader(
                libraryUrls,
                libraryParent,
                this.remapper,
                this.transformedCacheRoot.resolve(cacheName));
    }

    private static ClassLoader compatibilityLibraryLoader() {
        ClassLoader existing = compatibilityLibraryLoader;
        if (existing != null) return existing;

        synchronized (PluginClassLoader.class) {
            if (compatibilityLibraryLoader != null) return compatibilityLibraryLoader;

            String resourceName = "META-INF/lunararc-libs/commons-lang-2.6.jar";
            ClassLoader owner = PluginClassLoader.class.getClassLoader();
            try (InputStream input = owner.getResourceAsStream(resourceName)) {
                if (input == null) return null;

                Path directory = io.lunararcdevs.lunararc.common.LunarArcPaths.pluginLibraries().resolve("compat");
                Files.createDirectories(directory);
                Path library = directory.resolve("commons-lang-2.6.jar");
                if (!Files.isRegularFile(library) || Files.size(library) == 0L) {
                    Path temp = library.resolveSibling(library.getFileName() + ".tmp");
                    Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.move(temp, library, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temp, library, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                compatibilityLibraryLoader = new URLClassLoader(
                        new URL[]{library.toUri().toURL()},
                        owner
                );
                return compatibilityLibraryLoader;
            } catch (IOException error) {
                java.util.logging.Logger.getLogger("LunarArc").warning(
                        "Could not expose bundled plugin compatibility libraries: " + error.getMessage());
                return null;
            }
        }
    }

    public Class<?> findClassFromOwnSpace(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null && loaded.getClassLoader() == this) return loaded;

            String path = name.replace('.', '/').concat(".class");
            URL own = super.findResource(path);
            if (own != null) {
                try (InputStream is = own.openStream()) {
                    byte[] original = is.readAllBytes();
                    byte[] bytecode = loadOrCreateTransformedClass(path, name, original);
                    String pkg = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : null;
                    if (pkg != null && getDefinedPackage(pkg) == null) {
                        definePackage(pkg, null, null, null, null, null, null, null);
                    }
                    CodeSource cs = new CodeSource(file.toURI().toURL(), (Certificate[]) null);
                    ProtectionDomain pd = new ProtectionDomain(cs, null, this, null);
                    Class<?> result = defineClass(name, bytecode, 0, bytecode.length, pd);
                    classes.put(name, result);
                    return result;
                } catch (IOException error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
            if (paperLibraryLoader instanceof TransformingPluginLibraryClassLoader libraries) {
                try { return libraries.findOwnClass(name); } catch (ClassNotFoundException ignored) {}
            }
            if (libraryLoader instanceof TransformingPluginLibraryClassLoader libraries) {
                try { return libraries.findOwnClass(name); } catch (ClassNotFoundException ignored) {}
            }
            throw new ClassNotFoundException(name);
        }
    }

    public URL findResourceFromOwnSpace(String name) {
        URL resource = super.findResource(name);
        if (resource == null && paperLibraryLoader instanceof TransformingPluginLibraryClassLoader libraries) {
            resource = libraries.findOwnResource(name);
        }
        if (resource == null && libraryLoader instanceof TransformingPluginLibraryClassLoader libraries) {
            resource = libraries.findOwnResource(name);
        }
        return resource;
    }

    public java.util.List<URL> findResourcesFromOwnSpace(String name) throws IOException {
        java.util.LinkedHashSet<URL> resources = new java.util.LinkedHashSet<>();
        addResources(resources, super.findResources(name));
        if (paperLibraryLoader instanceof TransformingPluginLibraryClassLoader libraries) {
            addResources(resources, libraries.findOwnResources(name));
        }
        if (libraryLoader instanceof TransformingPluginLibraryClassLoader libraries) {
            addResources(resources, libraries.findOwnResources(name));
        }
        return java.util.List.copyOf(resources);
    }

    public PluginMappingNamespace getMappingNamespace() {
        return this.mappingNamespace;
    }

    public boolean isNmsRemappingEnabled() {
        return this.remapNms;
    }

    private static boolean isPlatformClass(String name) {
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("org.bukkit.")
                || name.startsWith("com.destroystokyo.paper.")
                || name.startsWith("io.papermc.paper.")
                || name.startsWith("net.kyori.adventure.")
                || name.startsWith("net.kyori.examination.")
                || name.startsWith("com.mojang.")
                || name.startsWith("net.minecraft.")
                || name.startsWith("com.google.gson.");
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url == null) url = getParent().getResource(name);
        if (url == null) {
            url = this.pluginLoader.getClassSpace().findDependencyResource(
                    this, name, visibleDependencies(), !this.paperPlugin);
        }
        if (url == null && paperLibraryLoader != null) url = paperLibraryLoader.getResource(name);
        if (url == null && libraryLoader != null) url = libraryLoader.getResource(name);
        if (url == null) {
            ClassLoader compatibility = compatibilityLibraryLoader();
            if (compatibility != null) url = compatibility.getResource(name);
        }
        return url;
    }

    @Override
    public java.util.Enumeration<URL> getResources(String name) throws IOException {
        java.util.LinkedHashSet<URL> resources = new java.util.LinkedHashSet<>();
        addResources(resources, super.findResources(name));
        addResources(resources, getParent().getResources(name));
        this.pluginLoader.getClassSpace().addDependencyResources(
                resources, this, name, visibleDependencies(), !this.paperPlugin);
        if (paperLibraryLoader != null) addResources(resources, paperLibraryLoader.getResources(name));
        if (libraryLoader != null) addResources(resources, libraryLoader.getResources(name));
        ClassLoader compatibility = compatibilityLibraryLoader();
        if (compatibility != null) addResources(resources, compatibility.getResources(name));
        return java.util.Collections.enumeration(resources);
    }

    private static void addResources(java.util.Set<URL> target, java.util.Enumeration<URL> source) {
        while (source.hasMoreElements()) target.add(source.nextElement());
    }

    private Set<String> visibleDependencies() {
        if (this.paperPlugin) {
            return this.joinedPaperDependencies;
        }
        Set<String> dependencies = new LinkedHashSet<>();
        dependencies.addAll(description.getDepend());
        dependencies.addAll(description.getSoftDepend());
        return dependencies;
    }

    private static boolean alwaysRemapsOnThisPlatform() {
        String platform = LunarArcServer.platformName();
        return "Fabric".equalsIgnoreCase(platform) || "Quilt".equalsIgnoreCase(platform);
    }

    private static boolean isPaperPlugin(File pluginFile) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(pluginFile)) {
            return jar.getJarEntry("paper-plugin.yml") != null;
        } catch (IOException error) {
            throw new IllegalStateException("Could not inspect plugin descriptor for " + pluginFile.getName(), error);
        }
    }

    private static String normalize(String name) {
        return name.replace(' ', '_').toLowerCase(java.util.Locale.ROOT);
    }

    public JavaPlugin getPluginInstance() {
        return plugin;
    }

    @Override
    public io.papermc.paper.plugin.configuration.PluginMeta getConfiguration() {
        return this.description;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve, boolean checkGlobal, boolean checkLibraries)
            throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> found = findClass(name, checkGlobal);
            if (resolve) resolveClass(found);
            return found;
        }
    }

    @Override
    public void init(JavaPlugin plugin) {
        initialize(plugin);
    }

    @Override
    public JavaPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup getGroup() {
        return this.group;
    }

    public void setGroup(io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup group) {
        this.group = group;
    }

    LunarArcPluginLoader getPluginLoaderInstance() {
        return pluginLoader;
    }

    public JavaPlugin createPluginMain() throws ReflectiveOperationException {
        Class<?> mainClass = findClass(description.getMain(), false);
        Class<? extends JavaPlugin> pluginClass = mainClass.asSubclass(JavaPlugin.class);
        java.lang.reflect.Constructor<? extends JavaPlugin> constructor = pluginClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    public synchronized void initialize(JavaPlugin plugin) {
        if (this.plugin != null) throw new IllegalStateException("Plugin already initialized!");

        this.plugin = plugin;
        plugin.init(
                pluginLoader.getServerInstance(),
                description,
                dataFolder,
                file,
                this,
                description,
                io.lunararcdevs.lunararc.common.server.LunarArcLogger.getLogger(description.getName())
        );
    }

    public static void shutdownSharedLoaders() {
        synchronized (PluginClassLoader.class) {
            ClassLoader current = compatibilityLibraryLoader;
            compatibilityLibraryLoader = null;
            if (current instanceof java.io.Closeable closeable) {
                try { closeable.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        IOException failure = null;
        try {
            super.close();
        } catch (IOException error) {
            failure = error;
        } finally {

            this.pluginLoader.getClassSpace().unregister(this);
            if (paperLibraryLoader instanceof java.io.Closeable closeable) {
                try { closeable.close(); } catch (IOException error) { if (failure == null) failure = error; }
            }
            if (libraryLoader instanceof java.io.Closeable closeable) {
                try { closeable.close(); } catch (IOException error) { if (failure == null) failure = error; }
            }
            classes.clear();
            plugin = null;
        }
        if (failure != null) throw failure;
    }
}
