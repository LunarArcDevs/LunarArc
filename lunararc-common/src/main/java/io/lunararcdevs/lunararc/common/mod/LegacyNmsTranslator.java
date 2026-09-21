package io.lunararcdevs.lunararc.common.mod;

import io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LegacyNmsTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc");
    private static final String REPOSITORY = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/dev-bundle/";
    private static final String BUNDLE_ENTRY = "data/mojang+yarn-spigot-reobf.tiny";

    private record Source(String minecraftVersion, String snapshot, String file, String sha512) {
    }

    private static final Map<String, Source> SOURCES = Map.of(
            "v1_20_R1", new Source("1.20.1", "1.20.1-R0.1-SNAPSHOT",
                    "dev-bundle-1.20.1-R0.1-20230921.165944-178.zip",
                    "81416d9f2b7c045448b1417e1e03ae85f2a31470ed89b465ec3d1d48bc1565ddd93164388cc6a2838e1ae5c1c0f3e95c0eb4f77746d54750837a91d2438e5ca1"),
            "v1_20_R2", new Source("1.20.2", "1.20.2-R0.1-SNAPSHOT",
                    "dev-bundle-1.20.2-R0.1-20231203.034718-121.zip",
                    "5f43e33ae8fc7d7bf717ebee63fa54b89ae7f75500fff3cab335c04f40259a6f4574e7e0af3693e8f3f7d7ff4cf224e2c519452289cb886d5069b57399387998"),
            "v1_20_R3", new Source("1.20.4", "1.20.4-R0.1-SNAPSHOT",
                    "dev-bundle-1.20.4-R0.1-20241030.192207-176.zip",
                    "57c3babca6798063f386cda20eea649a98160317b428233bde6cc401623628ad463fd3f9890f63bd0c09706190c6261878f43a698747914f25113aca3463eda5"),
            "v1_20_R4", new Source("1.20.6", "1.20.6-R0.1-SNAPSHOT",
                    "dev-bundle-1.20.6-R0.1-20241030.191541-126.zip",
                    "7467df323f91c2bb4d2e4a23af67b982a419cebdd888ce80fd6365556fe6b85935d2387508e1c197d700e8ac05b837be439ddae40f4390e3021fd4fc848a2b03"));

    private static final Map<String, Optional<LegacyNmsTranslator>> LOADED = new ConcurrentHashMap<>();

    private record Member(String mojangOwner, String mojangName, String mojangDesc, boolean method) {
    }

    private static final class Tiny {
        final Map<String, String> classes = new HashMap<>();
        final List<String[]> members = new ArrayList<>();
    }

    private final String fingerprint;
    private final Map<String, String> oldSpigotToMojang = new HashMap<>();
    private final Map<String, Member> oldByOwner = new HashMap<>();
    private final Map<String, List<Member>> oldByNameDesc = new HashMap<>();
    private final Map<String, String> currentMojangToSpigot = new HashMap<>();
    private final Map<String, String> currentSpigotByMojangMember = new HashMap<>();
    private final Map<String, Member> resolved = new ConcurrentHashMap<>();
    private final Map<String, Optional<Class<?>>> runtimeClasses = new ConcurrentHashMap<>();
    private final Remapper remapper = new Translation();

    private LegacyNmsTranslator(String fingerprint, Tiny old, Tiny current) {
        this.fingerprint = fingerprint;
        Map<String, String> oldMojangToSpigot = old.classes;
        for (Map.Entry<String, String> entry : oldMojangToSpigot.entrySet()) {
            oldSpigotToMojang.put(entry.getValue(), entry.getKey());
        }
        for (String[] row : old.members) {
            String mojangOwner = row[0];
            String spigotOwner = oldMojangToSpigot.getOrDefault(mojangOwner, mojangOwner);
            boolean method = row[1].equals("m");
            String spigotDesc = mapDescriptor(row[2], oldMojangToSpigot);
            Member member = new Member(mojangOwner, row[3], row[2], method);
            String kind = method ? "m" : "f";
            oldByOwner.put(spigotOwner + '.' + row[4] + spigotDesc + kind, member);
            oldByNameDesc.computeIfAbsent(row[4] + '\0' + spigotDesc + kind, key -> new ArrayList<>()).add(member);
        }
        currentMojangToSpigot.putAll(current.classes);
        for (String[] row : current.members) {
            currentSpigotByMojangMember.put(row[0] + '.' + row[3] + row[2] + (row[1].equals("m") ? "m" : "f"), row[4]);
        }
    }

    public String fingerprint() {
        return fingerprint;
    }

    public static LegacyNmsTranslator forVersion(String craftBukkitVersion) {
        return LOADED.computeIfAbsent(craftBukkitVersion, LegacyNmsTranslator::load).orElse(null);
    }

    public static boolean supports(String craftBukkitVersion) {
        return SOURCES.containsKey(craftBukkitVersion);
    }

    public byte[] translate(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper), 0);
        return writer.toByteArray();
    }

    private final class Translation extends Remapper {
        @Override
        public String map(String internalName) {
            String mojang = oldSpigotToMojang.get(internalName);
            if (mojang == null) return internalName;
            return currentMojangToSpigot.getOrDefault(mojang, internalName);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            return translateMember(owner, name, descriptor, true);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return translateMember(owner, name, descriptor, false);
        }
    }

    private String translateMember(String owner, String name, String descriptor, boolean method) {
        if (name.startsWith("<") || !oldSpigotToMojang.containsKey(owner)) return name;
        Member member = resolveOld(owner, name, descriptor, method);
        if (member == null) return name;
        String current = currentSpigotByMojangMember.get(
                member.mojangOwner() + '.' + member.mojangName() + member.mojangDesc() + (method ? "m" : "f"));
        return current != null ? current : name;
    }

    private Member resolveOld(String owner, String name, String descriptor, boolean method) {
        String kind = method ? "m" : "f";
        String cacheKey = owner + '.' + name + descriptor + kind;
        Member cached = resolved.get(cacheKey);
        if (cached != null) return cached == NONE ? null : cached;

        Member found = oldByOwner.get(cacheKey);
        if (found == null) {
            List<Member> candidates = oldByNameDesc.get(name + '\0' + descriptor + kind);
            if (candidates != null) found = pickByHierarchy(owner, candidates);
        }
        resolved.put(cacheKey, found == null ? NONE : found);
        return found;
    }

    private static final Member NONE = new Member("", "", "", false);

    private Member pickByHierarchy(String owner, List<Member> candidates) {
        Class<?> ownerClass = runtimeClass(oldSpigotToMojang.get(owner));
        if (candidates.size() == 1) {
            Member only = candidates.get(0);
            Class<?> declaring = runtimeClass(only.mojangOwner());
            if (declaring != null && ownerClass != null && !declaring.isAssignableFrom(ownerClass)) return null;
            return only;
        }
        if (ownerClass == null) return null;
        List<Member> assignable = new ArrayList<>();
        List<Class<?>> classes = new ArrayList<>();
        for (Member candidate : candidates) {
            Class<?> declaring = runtimeClass(candidate.mojangOwner());
            if (declaring != null && declaring.isAssignableFrom(ownerClass)) {
                assignable.add(candidate);
                classes.add(declaring);
            }
        }
        Member best = null;
        Class<?> bestClass = null;
        for (int i = 0; i < assignable.size(); i++) {
            if (bestClass == null || bestClass.isAssignableFrom(classes.get(i))) {
                best = assignable.get(i);
                bestClass = classes.get(i);
            }
        }
        return best;
    }

    private Class<?> runtimeClass(String mojangInternalName) {
        if (mojangInternalName == null) return null;
        return runtimeClasses.computeIfAbsent(mojangInternalName, name -> {
            try {
                String runtime = LunarArcRemapper.currentRuntimeClassName(name).replace('/', '.');
                return Optional.of(Class.forName(runtime, false, LegacyNmsTranslator.class.getClassLoader()));
            } catch (Throwable missing) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static String mapDescriptor(String descriptor, Map<String, String> classMap) {
        StringBuilder out = new StringBuilder(descriptor.length());
        for (int i = 0; i < descriptor.length(); i++) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                String name = descriptor.substring(i + 1, end);
                out.append('L').append(classMap.getOrDefault(name, name)).append(';');
                i = end;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static Optional<LegacyNmsTranslator> load(String craftBukkitVersion) {
        Source source = SOURCES.get(craftBukkitVersion);
        if (source == null) return Optional.empty();
        try {
            Path table = Paths.get(".lunararc", "mappings", source.minecraftVersion(), "mojang-spigot.tiny");
            if (!Files.isRegularFile(table)) download(source, table);
            Tiny current = parseCurrent();
            Tiny old;
            try {
                old = parse(table);
            } catch (IOException | RuntimeException corrupt) {
                Files.deleteIfExists(table);
                download(source, table);
                old = parse(table);
            }
            String fingerprint = craftBukkitVersion + ':' + source.sha512().substring(0, 16) + ':' + Files.size(table);
            return Optional.of(new LegacyNmsTranslator(fingerprint, old, current));
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not prepare legacy NMS mappings for {}: {}", craftBukkitVersion, failure.toString());
            return Optional.empty();
        }
    }

    private static Tiny parseCurrent() throws IOException {
        String resource = "mappings/" + LunarArcVersionInfo.minecraftVersion() + "/paper-reobf.tiny";
        try (InputStream in = LegacyNmsTranslator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing bundled mapping resource " + resource);
            return parse(in);
        }
    }

    private static Tiny parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    private static Tiny parse(InputStream in) throws IOException {
        Tiny tiny = new Tiny();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("tiny\t2\t")) throw new IOException("Not a Tiny v2 mapping file");
            String currentClass = null;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty()) continue;
                if (line.charAt(0) == 'c') {
                    String[] parts = line.split("\t");
                    if (parts.length < 3) continue;
                    currentClass = parts[1];
                    tiny.classes.put(parts[1], parts[2]);
                } else if (line.charAt(0) == '\t' && currentClass != null && line.length() > 2
                        && (line.charAt(1) == 'm' || line.charAt(1) == 'f')) {
                    String[] parts = line.split("\t");
                    if (parts.length < 5) continue;
                    tiny.members.add(new String[]{currentClass, parts[1], parts[2], parts[3], parts[4]});
                }
            }
        }
        return tiny;
    }

    private static void download(Source source, Path target) throws IOException {
        LOGGER.info("Downloading Spigot {} mapping data for legacy plugin support (one-time, about 22 MB)...",
                source.minecraftVersion());
        Path bundle = Files.createTempFile("lunararc-dev-bundle", ".zip");
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(
                    REPOSITORY + source.snapshot() + "/" + source.file()).toURL().openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "LunarArc");
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            try (InputStream in = new DigestInputStream(connection.getInputStream(), digest)) {
                Files.copy(in, bundle, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(source.sha512())) {
                throw new IOException("Checksum mismatch for " + source.file());
            }
            try (ZipFile zip = new ZipFile(bundle.toFile())) {
                ZipEntry entry = zip.getEntry(BUNDLE_ENTRY);
                if (entry == null) throw new IOException(BUNDLE_ENTRY + " missing from " + source.file());
                Files.createDirectories(target.getParent());
                Path temp = target.resolveSibling(target.getFileName() + ".tmp");
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        } finally {
            Files.deleteIfExists(bundle);
        }
    }
}
