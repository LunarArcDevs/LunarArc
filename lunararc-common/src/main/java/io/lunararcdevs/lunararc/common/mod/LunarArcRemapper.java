package io.lunararcdevs.lunararc.common.mod;

import io.lunararcdevs.lunararc.common.mod.server.LunarArcServer;
import io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class LunarArcRemapper extends org.objectweb.asm.commons.Remapper {
    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc/Remapper");

    private static final String CRAFTBUKKIT_PREFIX = "org/bukkit/craftbukkit/";

    private static final Map<String, String> CLASS_MAP = new HashMap<>();

    private static final Map<String, String> MOJANG_TO_SPIGOT_CLASS = new HashMap<>();

    private static final Map<MemberKey, String> FIELD_MAP = new HashMap<>();
    private static final Map<MemberKey, String> METHOD_MAP = new HashMap<>();
    private static final Map<MemberNameKey, String> FIELD_NAME_MAP = new HashMap<>();
    private static final Map<MemberNameKey, String> METHOD_NAME_MAP = new HashMap<>();
    private static final Map<String, String> MOJANG_TO_INTERMEDIARY_CLASS = new HashMap<>();
    private static final Map<String, String> INTERMEDIARY_TO_MOJANG_CLASS = new HashMap<>();
    private static final Map<MemberKey, String> INTERMEDIARY_FIELD_MAP = new HashMap<>();
    private static final Map<MemberKey, String> INTERMEDIARY_METHOD_MAP = new HashMap<>();
    private static final Map<MemberNameKey, String> INTERMEDIARY_FIELD_NAME_MAP = new HashMap<>();
    private static final Map<MemberNameKey, String> INTERMEDIARY_METHOD_NAME_MAP = new HashMap<>();
    private static final Map<MemberNameKey, String> INTERMEDIARY_TO_MOJANG_FIELD_NAME = new HashMap<>();
    private static final Map<MemberNameKey, String> INTERMEDIARY_TO_MOJANG_METHOD_NAME = new HashMap<>();
    private static volatile Boolean needsIntermediaryHopCache;
    private static final Map<MemberNameKey, List<Map.Entry<MemberKey, String>>> METHOD_OVERLOAD_INDEX = new HashMap<>();
    private static final Map<MemberNameKey, List<Map.Entry<MemberKey, String>>> INTERMEDIARY_METHOD_OVERLOAD_INDEX = new HashMap<>();
    private static final Map<String, String> RUNTIME_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> RUNTIME_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> RUNTIME_DISPLAY_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> BYTECODE_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> BYTECODE_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> ALREADY_CORRECT_CACHE = new ConcurrentHashMap<>();
    private static final int DYNAMIC_CACHE_LIMIT = 16_384;

    private final boolean remapNms;

    static {
        loadMappings();
    }

    public LunarArcRemapper() {
        this(true);
    }

    public LunarArcRemapper(boolean remapNms) {
        this.remapNms = remapNms;
    }

    public boolean isNmsRemappingEnabled() {
        return this.remapNms;
    }

    public static void verifyCompatibilityOutput(byte[] bytes, String className) {
        if (bytes == null || bytes.length < 8) return;
        if (containsAscii(bytes, "net/minecraft/server/v1_") || containsAscii(bytes, "org/bukkit/craftbukkit/v1_")) {
            LOGGER.warn("Post-transform compatibility check failed for {}: remapped bytecode still "
                            + "contains a legacy versioned NMS/CraftBukkit symbol — the transform likely "
                            + "did not fully remap this class.", className);
        }
    }

    private static void loadMappings() {
        String base = "mappings/" + LunarArcVersionInfo.minecraftVersion() + "/";
        ClassLoader loader = LunarArcRemapper.class.getClassLoader();

        try {

            try (InputStream stream = loader.getResourceAsStream(base + "paper-reobf.tiny")) {
                if (stream == null) {
                    throw new IllegalStateException("Missing Paper reobf mappings " + base + "paper-reobf.tiny");
                }
                loadPaperMappings(stream);
            }


            try (InputStream stream = loader.getResourceAsStream(base + "plugin-remap.tsv")) {
                if (stream != null) loadOverrides(stream);
            }

            try (InputStream stream = loader.getResourceAsStream(base + "intermediary.tiny")) {
                if (stream != null) loadIntermediaryMappings(stream);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void loadIntermediaryMappings(InputStream stream) throws Exception {
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        if (lines.isEmpty()) return;

        String mojangOwner = null;
        String intermediaryOwner = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] p = line.split("\\t", -1);

            if (p.length >= 3 && "c".equals(p[0])) {
                mojangOwner = p[1];
                intermediaryOwner = p[2];
                if (!mojangOwner.isEmpty() && !intermediaryOwner.isEmpty()) {
                    MOJANG_TO_INTERMEDIARY_CLASS.put(mojangOwner, intermediaryOwner);
                    INTERMEDIARY_TO_MOJANG_CLASS.put(intermediaryOwner, mojangOwner);
                }
                continue;
            }

            if (p.length >= 5 && p[0].isEmpty() && ("f".equals(p[1]) || "m".equals(p[1]))
                    && mojangOwner != null && intermediaryOwner != null) {
                boolean method = "m".equals(p[1]);
                String descriptor = p[2];
                String mojangName = p[3];
                String intermediaryName = p[4];
                if (mojangName.isEmpty() || intermediaryName.isEmpty()) continue;
                MemberKey key = new MemberKey(mojangOwner, mojangName, descriptor);
                MemberNameKey nameKey = new MemberNameKey(mojangOwner, mojangName);
                MemberNameKey reverseKey = new MemberNameKey(mojangOwner, intermediaryName);
                if (method) {
                    INTERMEDIARY_METHOD_MAP.put(key, intermediaryName);
                    addUniqueNameMapping(INTERMEDIARY_METHOD_NAME_MAP, nameKey, intermediaryName);
                    addUniqueNameMapping(INTERMEDIARY_TO_MOJANG_METHOD_NAME, reverseKey, mojangName);
                    INTERMEDIARY_METHOD_OVERLOAD_INDEX
                            .computeIfAbsent(nameKey, ignored -> new ArrayList<>())
                            .add(Map.entry(key, intermediaryName));
                } else {
                    INTERMEDIARY_FIELD_MAP.put(key, intermediaryName);
                    addUniqueNameMapping(INTERMEDIARY_FIELD_NAME_MAP, nameKey, intermediaryName);
                    addUniqueNameMapping(INTERMEDIARY_TO_MOJANG_FIELD_NAME, reverseKey, mojangName);
                }
            }
        }
    }

    private static boolean needsIntermediaryHop() {
        Boolean cached = needsIntermediaryHopCache;
        if (cached != null) return cached;
        String platform = LunarArcServer.platformName();
        boolean needs = "Fabric".equalsIgnoreCase(platform) || "Quilt".equalsIgnoreCase(platform);
        LOGGER.info("Mojang -> Intermediary remap hop {} (platform={}, {} class(es)/{} method(s)/{} "
                        + "field(s) loaded from intermediary.tiny)", needs ? "ENABLED" : "disabled",
                platform, MOJANG_TO_INTERMEDIARY_CLASS.size(), INTERMEDIARY_METHOD_MAP.size(),
                INTERMEDIARY_FIELD_MAP.size());
        needsIntermediaryHopCache = needs;
        return needs;
    }

    private static String toIntermediaryClass(String mojangInternalName) {
        if (mojangInternalName == null) return null;
        String mapped = MOJANG_TO_INTERMEDIARY_CLASS.get(mojangInternalName);
        if (mapped != null) return mapped;
        int nested = mojangInternalName.indexOf('$');
        if (nested > 0) {
            String mappedOwner = MOJANG_TO_INTERMEDIARY_CLASS.get(mojangInternalName.substring(0, nested));
            if (mappedOwner != null) return mappedOwner + mojangInternalName.substring(nested);
        }
        return mojangInternalName;
    }

    private static String toIntermediaryMember(String mojangOwner, String mojangName, String mojangDescriptor,
            boolean method) {
        String exact = walkIntermediaryMember(mojangOwner, mojangName, mojangDescriptor, method, false);
        if (exact != null) return exact;
        String byName = walkIntermediaryMember(mojangOwner, mojangName, mojangDescriptor, method, true);
        return byName != null ? byName : mojangName;
    }

    private static String walkIntermediaryMember(String mojangOwner, String mojangName, String mojangDescriptor,
            boolean method, boolean allowUniqueNameFallback) {
        String found = lookupIntermediaryMember(mojangOwner, mojangName, mojangDescriptor, method, allowUniqueNameFallback);
        if (found != null) return found;
        String intermediaryOwner = MOJANG_TO_INTERMEDIARY_CLASS.get(mojangOwner);
        if (intermediaryOwner == null) return null;
        try {
            ClassLoader loader = LunarArcServer.modClassLoader();
            if (loader == null) loader = LunarArcRemapper.class.getClassLoader();
            Class<?> type = Class.forName(intermediaryOwner.replace('/', '.'), false, loader);
            for (Class<?> current = type.getSuperclass(); current != null; current = current.getSuperclass()) {
                String currentMojang = INTERMEDIARY_TO_MOJANG_CLASS.get(current.getName().replace('.', '/'));
                if (currentMojang != null) {
                    found = lookupIntermediaryMember(currentMojang, mojangName, mojangDescriptor, method, allowUniqueNameFallback);
                    if (found != null) return found;
                }
                if (method) {
                    found = walkIntermediaryInterfaceMember(
                            current.getInterfaces(), mojangName, mojangDescriptor, allowUniqueNameFallback);
                    if (found != null) return found;
                }
            }
            if (method) {
                return walkIntermediaryInterfaceMember(type.getInterfaces(), mojangName, mojangDescriptor, allowUniqueNameFallback);
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
        return null;
    }

    private static String walkIntermediaryInterfaceMember(Class<?>[] interfaces, String mojangName,
            String mojangDescriptor, boolean allowUniqueNameFallback) {
        for (Class<?> iface : interfaces) {
            String ifaceMojang = INTERMEDIARY_TO_MOJANG_CLASS.get(iface.getName().replace('.', '/'));
            if (ifaceMojang != null) {
                String found = lookupIntermediaryMember(ifaceMojang, mojangName, mojangDescriptor, true, allowUniqueNameFallback);
                if (found != null) return found;
            }
            String fromNested = walkIntermediaryInterfaceMember(
                    iface.getInterfaces(), mojangName, mojangDescriptor, allowUniqueNameFallback);
            if (fromNested != null) return fromNested;
        }
        return null;
    }

    private static String lookupIntermediaryMember(String mojangOwner, String mojangName, String mojangDescriptor,
            boolean method, boolean allowUniqueNameFallback) {
        Map<MemberKey, String> table = method ? INTERMEDIARY_METHOD_MAP : INTERMEDIARY_FIELD_MAP;
        String mapped = table.get(new MemberKey(mojangOwner, mojangName, mojangDescriptor));
        if (mapped != null) return mapped;
        if (!allowUniqueNameFallback) return null;
        if (namesRuntimeMember(resolveRuntimeClassForMojang(mojangOwner), mojangName, method)) {
            return null;
        }
        Map<MemberNameKey, String> names = method ? INTERMEDIARY_METHOD_NAME_MAP : INTERMEDIARY_FIELD_NAME_MAP;
        String unique = names.get(new MemberNameKey(mojangOwner, mojangName));
        return unique != null && !AMBIGUOUS.equals(unique) ? unique : null;
    }

    private static void loadPaperMappings(InputStream stream) throws Exception {
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        if (lines.isEmpty()) throw new IllegalStateException("Paper mapping resource is empty");

        String[] header = lines.get(0).split("\\t", -1);
        if (header.length == 0) throw new IllegalStateException("Invalid Paper mapping header");

        List<PendingMember> pending = new ArrayList<>();
        if ("tiny".equals(header[0])) {
            parseTinyV2(lines, header, pending);
        } else if ("v1".equals(header[0])) {
            parseTinyV1(lines, header, pending);
        } else if ("tsrg2".equals(header[0])) {
            parseTsrg2(lines, pending);
        } else {
            throw new IllegalStateException("Unsupported Paper mapping format: " + lines.get(0));
        }


        for (PendingMember member : pending) {
            String spigotDescriptor = mapDescriptorClasses(member.mojangDescriptor, MOJANG_TO_SPIGOT_CLASS);
            MemberKey key = new MemberKey(member.spigotOwner, member.spigotName, spigotDescriptor);
            if (member.method) {
                putMethod(key, member.mojangName);
                addUniqueNameMapping(METHOD_NAME_MAP, new MemberNameKey(member.spigotOwner, member.spigotName), member.mojangName);
            } else {
                FIELD_MAP.put(key, member.mojangName);
                addUniqueNameMapping(FIELD_NAME_MAP, new MemberNameKey(member.spigotOwner, member.spigotName), member.mojangName);
            }
        }
    }

    /** Single write path for METHOD_MAP so METHOD_OVERLOAD_INDEX can never drift out of sync with it. */
    private static void putMethod(MemberKey key, String mojangName) {
        METHOD_MAP.put(key, mojangName);
        METHOD_OVERLOAD_INDEX
                .computeIfAbsent(new MemberNameKey(key.owner(), key.name()), ignored -> new ArrayList<>())
                .add(Map.entry(key, mojangName));
    }

    private static void parseTinyV2(List<String> lines, String[] header, List<PendingMember> pending) {
        if (header.length < 5 || !"2".equals(header[1])) {
            throw new IllegalStateException("Unsupported Tiny header: " + lines.get(0));
        }
        int sourceIndex = 3;
        int targetIndex = findSpigotNamespace(header, sourceIndex + 1);
        if (targetIndex < 0) targetIndex = header.length - 1;

        String mojangOwner = null;
        String spigotOwner = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] p = line.split("\\t", -1);

            if (p.length >= 3 && "c".equals(p[0])) {
                mojangOwner = p[1];
                int mappedColumn = targetIndex - sourceIndex + 1;
                if (mappedColumn >= p.length) continue;
                spigotOwner = p[mappedColumn];
                if (!mojangOwner.isEmpty() && !spigotOwner.isEmpty()) {
                    CLASS_MAP.put(spigotOwner, mojangOwner);
                    MOJANG_TO_SPIGOT_CLASS.put(mojangOwner, spigotOwner);
                }
                continue;
            }


            if (p.length >= 5 && p[0].isEmpty() && ("f".equals(p[1]) || "m".equals(p[1]))
                    && mojangOwner != null && spigotOwner != null) {
                int mappedColumn = targetIndex - sourceIndex + 3;
                if (mappedColumn >= p.length) continue;
                String descriptor = p[2];
                String mojangName = p[3];
                String spigotName = p[mappedColumn];
                if (!mojangName.isEmpty() && !spigotName.isEmpty()) {
                    pending.add(new PendingMember("m".equals(p[1]), spigotOwner, spigotName, descriptor, mojangName));
                }
            }
        }
    }

    private static void parseTinyV1(List<String> lines, String[] header, List<PendingMember> pending) {
        int targetIndex = findSpigotNamespace(header, 1);
        if (targetIndex < 0) targetIndex = header.length - 1;
        int mappedNameOffset = targetIndex;

        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split("\\t", -1);
            if (p.length < 3) continue;
            switch (p[0]) {
                case "CLASS" -> {
                    if (mappedNameOffset >= p.length) continue;
                    String mojang = p[1];
                    String spigot = p[mappedNameOffset];
                    if (!mojang.isEmpty() && !spigot.isEmpty()) {
                        CLASS_MAP.put(spigot, mojang);
                        MOJANG_TO_SPIGOT_CLASS.put(mojang, spigot);
                    }
                }
                case "FIELD", "METHOD" -> {

                    int mappedColumn = targetIndex + 2;
                    if (p.length <= mappedColumn) continue;
                    String mojangOwner = p[1];
                    String spigotOwner = MOJANG_TO_SPIGOT_CLASS.getOrDefault(mojangOwner, mojangOwner);
                    pending.add(new PendingMember("METHOD".equals(p[0]), spigotOwner, p[mappedColumn], p[2], p[3]));
                }
                default -> {
                }
            }
        }
    }


    private static void parseTsrg2(List<String> lines, List<PendingMember> pending) {
        String mojangOwner = null;
        String spigotOwner = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 2) {
                    mojangOwner = p[0];
                    spigotOwner = p[1];
                    CLASS_MAP.put(spigotOwner, mojangOwner);
                    MOJANG_TO_SPIGOT_CLASS.put(mojangOwner, spigotOwner);
                }
                continue;
            }
            if (mojangOwner == null || spigotOwner == null) continue;
            String[] p = line.trim().split("\\s+");
            if (p.length == 2) {

                pending.add(new PendingMember(false, spigotOwner, p[1], "*", p[0]));
            } else if (p.length >= 3 && p[1].startsWith("(")) {
                pending.add(new PendingMember(true, spigotOwner, p[2], p[1], p[0]));
            } else if (p.length >= 3) {
                pending.add(new PendingMember(false, spigotOwner, p[p.length - 1], p[1], p[0]));
            }
        }
    }

    private static int findSpigotNamespace(String[] header, int start) {
        for (int i = start; i < header.length; i++) {
            String value = header[i].toLowerCase(java.util.Locale.ROOT);
            if (value.contains("spigot") || value.contains("reobf")) return i;
        }
        return -1;
    }

    private static void loadOverrides(InputStream stream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\t", -1);
                switch (parts[0]) {
                    case "CLASS" -> {
                        if (parts.length != 3) throw invalid(line);
                        CLASS_MAP.put(parts[1], parts[2]);
                        MOJANG_TO_SPIGOT_CLASS.put(parts[2], parts[1]);
                    }
                    case "FIELD" -> {
                        if (parts.length != 5) throw invalid(line);
                        FIELD_MAP.put(new MemberKey(parts[1], parts[2], parts[3]), parts[4]);
                        addUniqueNameMapping(FIELD_NAME_MAP, new MemberNameKey(parts[1], parts[2]), parts[4]);
                    }
                    case "METHOD" -> {
                        if (parts.length != 5) throw invalid(line);
                        putMethod(new MemberKey(parts[1], parts[2], parts[3]), parts[4]);
                        addUniqueNameMapping(METHOD_NAME_MAP, new MemberNameKey(parts[1], parts[2]), parts[4]);
                    }
                    default -> throw invalid(line);
                }
            }
        }
    }

    private static final String AMBIGUOUS = "\u0000";

    private static void addUniqueNameMapping(Map<MemberNameKey, String> map, MemberNameKey key, String value) {
        String previous = map.putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) map.put(key, AMBIGUOUS);
    }

    private static IllegalStateException invalid(String line) {
        return new IllegalStateException("Invalid plugin mapping entry: " + line);
    }

    @Override
    public String map(String internalName) {
        if (internalName == null) return null;
        if (internalName.startsWith(CRAFTBUKKIT_PREFIX)) {
            String remainder = internalName.substring(CRAFTBUKKIT_PREFIX.length());
            int slash = remainder.indexOf('/');
            if (slash > 0 && remainder.substring(0, slash).matches("v\\d+_\\d+_R\\d+")) {
                return CRAFTBUKKIT_PREFIX + remainder.substring(slash + 1);
            }
            return internalName;
        }

        if (!remapNms) return internalName;
        String mojangName = spigotToMojangClass(internalName);
        if (mojangName == null) {
            int nested = internalName.indexOf('$');
            if (nested > 0) {
                String mappedOwner = spigotToMojangClass(internalName.substring(0, nested));
                if (mappedOwner != null) mojangName = mappedOwner + internalName.substring(nested);
            }
        }
        if (mojangName == null) mojangName = internalName;
        return needsIntermediaryHop() ? toIntermediaryClass(mojangName) : mojangName;
    }

    private static String spigotToMojangClass(String name) {
        return MOJANG_TO_SPIGOT_CLASS.containsKey(name) ? name : CLASS_MAP.get(name);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (!remapNms || owner == null || name == null) return name;

        String spigotOwner = toSpigotOwner(owner);
        String lookupDescriptor = toSpigotDescriptor(descriptor);
        String mapped = FIELD_MAP.get(new MemberKey(spigotOwner, name, lookupDescriptor));
        if (mapped == null && !java.util.Objects.equals(lookupDescriptor, descriptor)) {
            mapped = FIELD_MAP.get(new MemberKey(spigotOwner, name, descriptor));
        }
        if (mapped == null) mapped = FIELD_MAP.get(new MemberKey(spigotOwner, name, "*"));
        if (mapped == null) {
            String unique = FIELD_NAME_MAP.get(new MemberNameKey(spigotOwner, name));
            if (unique != null && !AMBIGUOUS.equals(unique)) mapped = unique;
        }
        if (mapped == null && spigotOwner.startsWith("net/minecraft/")) {
            String key = spigotOwner + '#' + name + '#' + descriptor;
            mapped = boundedComputeIfAbsent(BYTECODE_FIELD_CACHE, key,
                    ignored -> resolveInheritedBytecodeMember(spigotOwner, name, descriptor, false));
            if (name.equals(mapped)) mapped = null;
        }
        String mojangName = mapped != null ? mapped : name;
        if (needsIntermediaryHop()) {
            String mojangOwner = CLASS_MAP.getOrDefault(spigotOwner, spigotOwner);
            String mojangDescriptor = mapDescriptorClasses(descriptor, CLASS_MAP);
            String intermediaryName = toIntermediaryMember(mojangOwner, mojangName, mojangDescriptor, false);
            if (intermediaryName.equals(mojangName) && mojangOwner.startsWith("net/minecraft/")
                    && !isIntermediaryMemberName(mojangName)
                    && !namesRuntimeMember(resolveRuntimeClassForMojang(mojangOwner), mojangName, false)) {
                LOGGER.warn("No Mojang -> Intermediary mapping found for NMS field {}#{} {} — plugin "
                                + "bytecode will keep the Mojang name and is likely to throw "
                                + "NoSuchFieldError at runtime on Fabric/Quilt.",
                        mojangOwner, mojangName, mojangDescriptor);
            }
            return intermediaryName;
        }
        if (mapped == null && spigotOwner.startsWith("net/minecraft/")
                && !namesRuntimeMember(runtimeClassFor(spigotOwner), name, false)) {
            LOGGER.warn("No mapping found for NMS field {}#{} {} — plugin bytecode will keep the "
                            + "unmapped name and is likely to throw NoSuchFieldError at runtime.",
                    spigotOwner, name, descriptor);
        }
        return mojangName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (!remapNms || "<init>".equals(name) || "<clinit>".equals(name)) return name;
        String spigotOwner = toSpigotOwner(owner);
        String lookupDescriptor = toSpigotDescriptor(descriptor);
        String mapped = METHOD_MAP.get(new MemberKey(spigotOwner, name, lookupDescriptor));
        if (mapped == null && !java.util.Objects.equals(lookupDescriptor, descriptor)) {
            mapped = METHOD_MAP.get(new MemberKey(spigotOwner, name, descriptor));
        }
        if (mapped == null) {
            String wildcard = METHOD_MAP.get(new MemberKey(spigotOwner, name, "*"));
            if (wildcard != null && runtimeHasMethod(spigotOwner, wildcard, descriptor)) mapped = wildcard;
        }
        if (mapped == null) {
            String unique = METHOD_NAME_MAP.get(new MemberNameKey(spigotOwner, name));
            if (unique != null && !AMBIGUOUS.equals(unique)
                    && runtimeHasMethod(spigotOwner, unique, descriptor)) {
                mapped = unique;
            }
        }
        if (mapped == null && spigotOwner.startsWith("net/minecraft/")) {
            String key = spigotOwner + '#' + name + '#' + descriptor;
            mapped = boundedComputeIfAbsent(BYTECODE_METHOD_CACHE, key,
                    ignored -> resolveInheritedBytecodeMember(spigotOwner, name, descriptor, true));
            if (name.equals(mapped)) mapped = null;
        }
        String mojangName = mapped != null ? mapped : name;
        if (needsIntermediaryHop()) {
            String mojangOwner = CLASS_MAP.getOrDefault(spigotOwner, spigotOwner);
            String mojangDescriptor = mapDescriptorClasses(descriptor, CLASS_MAP);
            String intermediaryName = toIntermediaryMember(mojangOwner, mojangName, mojangDescriptor, true);
            if (intermediaryName.equals(mojangName) && mojangOwner.startsWith("net/minecraft/")
                    && !isIntermediaryMemberName(mojangName)
                    && !namesRuntimeMember(resolveRuntimeClassForMojang(mojangOwner), mojangName, true)) {
                LOGGER.warn("No Mojang -> Intermediary mapping found for NMS method {}#{} {} — plugin "
                                + "bytecode will keep the Mojang name and is likely to throw "
                                + "NoSuchMethodError at runtime on Fabric/Quilt.",
                        mojangOwner, mojangName, mojangDescriptor);
            }
            return intermediaryName;
        }
        if (mapped == null && spigotOwner.startsWith("net/minecraft/")
                && !namesRuntimeMember(runtimeClassFor(spigotOwner), name, true)) {
            LOGGER.warn("No mapping found for NMS method {}#{} {} — plugin bytecode will keep the "
                            + "unmapped name and is likely to throw NoSuchMethodError at runtime.",
                    spigotOwner, name, descriptor);
        }
        return mojangName;
    }

    private static boolean runtimeHasMethod(String spigotOwner, String mappedName, String descriptor) {
        Class<?> runtimeOwner = runtimeClassFor(spigotOwner);
        if (runtimeOwner == null) return true;
        org.objectweb.asm.Type[] arguments;
        try {
            arguments = org.objectweb.asm.Type.getArgumentTypes(descriptor);
        } catch (RuntimeException malformedDescriptor) {
            return true;
        }
        org.objectweb.asm.Type returnType;
        try {
            returnType = org.objectweb.asm.Type.getReturnType(descriptor);
        } catch (RuntimeException malformedDescriptor) {
            return true;
        }
        String key = runtimeOwner.getName() + '#' + mappedName + '#' + descriptor;
        return "true".equals(boundedComputeIfAbsent(ALREADY_CORRECT_CACHE, key,
                ignored -> Boolean.toString(
                        declaresMatchingMethod(runtimeOwner, mappedName, arguments, returnType))));
    }

    private static boolean typeMatches(Class<?> runtime, org.objectweb.asm.Type declared) {
        boolean declaredIsReference = declared.getSort() == org.objectweb.asm.Type.OBJECT
                || declared.getSort() == org.objectweb.asm.Type.ARRAY;
        if (!declaredIsReference || runtime.isPrimitive()) {
            return org.objectweb.asm.Type.getDescriptor(runtime).equals(declared.getDescriptor());
        }
        String internal = declared.getInternalName();
        String mapped = spigotToMojangClass(internal);
        if (mapped == null) return true;
        return mapped.equals(org.objectweb.asm.Type.getInternalName(runtime));
    }

    private static boolean signatureMatches(java.lang.reflect.Method candidate,
            org.objectweb.asm.Type[] arguments, org.objectweb.asm.Type returnType) {
        Class<?>[] parameters = candidate.getParameterTypes();
        if (parameters.length != arguments.length) return false;
        for (int i = 0; i < parameters.length; i++) {
            if (!typeMatches(parameters[i], arguments[i])) return false;
        }
        return typeMatches(candidate.getReturnType(), returnType);
    }

    private static boolean declaresMatchingMethod(Class<?> runtimeOwner, String name,
            org.objectweb.asm.Type[] arguments, org.objectweb.asm.Type returnType) {
        try {
            for (Class<?> current = runtimeOwner; current != null; current = current.getSuperclass()) {
                for (java.lang.reflect.Method candidate : current.getDeclaredMethods()) {
                    if (candidate.getName().equals(name) && signatureMatches(candidate, arguments, returnType)) {
                        return true;
                    }
                }
                if (interfaceDeclaresMatchingMethod(current.getInterfaces(), name, arguments, returnType)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    private static boolean interfaceDeclaresMatchingMethod(Class<?>[] interfaces, String name,
            org.objectweb.asm.Type[] arguments, org.objectweb.asm.Type returnType) {
        for (Class<?> iface : interfaces) {
            for (java.lang.reflect.Method candidate : iface.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && signatureMatches(candidate, arguments, returnType)) {
                    return true;
                }
            }
            if (interfaceDeclaresMatchingMethod(iface.getInterfaces(), name, arguments, returnType)) return true;
        }
        return false;
    }

    private String resolveInheritedBytecodeMember(String spigotOwner, String name, String descriptor, boolean method) {
        String mojangOwner = CLASS_MAP.get(spigotOwner);
        if (mojangOwner == null) return name;
        Class<?> type = resolveRuntimeClassForMojang(mojangOwner);
        if (type == null) return name;
        String exact = walkInheritedMember(type, name, descriptor, method, false);
        if (exact != null) return exact;
        String byName = walkInheritedMember(type, name, descriptor, method, true);
        return byName != null ? byName : name;
    }

    private String walkInheritedMember(Class<?> type, String name, String descriptor, boolean method,
            boolean allowUniqueNameFallback) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String currentSpigot = MOJANG_TO_SPIGOT_CLASS.getOrDefault(mojangNameOf(current), mojangNameOf(current));
            String found = lookupBytecodeMember(currentSpigot, name, descriptor, method, allowUniqueNameFallback);
            if (found != null) return found;
            if (method) {
                found = walkInheritedInterfaceMember(current.getInterfaces(), name, descriptor, allowUniqueNameFallback);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String walkInheritedInterfaceMember(Class<?>[] interfaces, String name, String descriptor,
            boolean allowUniqueNameFallback) {
        for (Class<?> iface : interfaces) {
            String ifaceSpigot = MOJANG_TO_SPIGOT_CLASS.getOrDefault(mojangNameOf(iface), mojangNameOf(iface));
            String found = lookupBytecodeMember(ifaceSpigot, name, descriptor, true, allowUniqueNameFallback);
            if (found != null) return found;
            found = walkInheritedInterfaceMember(iface.getInterfaces(), name, descriptor, allowUniqueNameFallback);
            if (found != null) return found;
        }
        return null;
    }

    private String lookupBytecodeMember(String spigotOwner, String name, String descriptor, boolean method,
            boolean allowUniqueNameFallback) {
        Map<MemberKey, String> mappings = method ? METHOD_MAP : FIELD_MAP;
        String lookupDescriptor = toSpigotDescriptor(descriptor);
        String mapped = mappings.get(new MemberKey(spigotOwner, name, lookupDescriptor));
        if (mapped == null) mapped = mappings.get(new MemberKey(spigotOwner, name, descriptor));
        if (mapped == null) {
            String wildcard = mappings.get(new MemberKey(spigotOwner, name, "*"));
            if (wildcard != null && (!method || runtimeHasMethod(spigotOwner, wildcard, descriptor))) {
                mapped = wildcard;
            }
        }
        if (mapped == null && allowUniqueNameFallback) {
            Map<MemberNameKey, String> names = method ? METHOD_NAME_MAP : FIELD_NAME_MAP;
            String unique = names.get(new MemberNameKey(spigotOwner, name));
            if (unique != null && !AMBIGUOUS.equals(unique)
                    && (!method || runtimeHasMethod(spigotOwner, unique, descriptor))) {
                mapped = unique;
            }
        }
        return mapped;
    }


    /**
     * The class name a {@link io.lunararcdevs.lunararc.common.mod.util.remapper.patcher.PluginPatcher}
     * must compare a bytecode owner against for {@code mojangInternalName} - patchers run on already
     * fully class-transformed plugin bytecode (see {@code LunarArcRemapper.transformInternal}), so an
     * owner is the real Mojang name on NeoForge/Forge but the Intermediary name on Fabric/Quilt. A
     * patcher that hardcodes the Mojang string only ever matches on NeoForge/Forge.
     */
    public static String currentRuntimeClassName(String mojangInternalName) {
        return needsIntermediaryHop() ? toIntermediaryClass(mojangInternalName) : mojangInternalName;
    }

    /** Same platform-hop as {@link #currentRuntimeClassName}, applied to every class name embedded in
     *  a method/field descriptor - for patchers matching a call's full descriptor, not just its owner. */
    public static String currentRuntimeDescriptor(String mojangDescriptor) {
        if (mojangDescriptor == null || mojangDescriptor.isEmpty()) return mojangDescriptor;
        StringBuilder out = new StringBuilder(mojangDescriptor.length());
        for (int i = 0; i < mojangDescriptor.length(); i++) {
            char c = mojangDescriptor.charAt(i);
            out.append(c);
            if (c == 'L') {
                int end = mojangDescriptor.indexOf(';', i);
                if (end < 0) break;
                out.append(currentRuntimeClassName(mojangDescriptor.substring(i + 1, end)));
                out.append(';');
                i = end;
            }
        }
        return out.toString();
    }

    public String mapRuntimeClassName(String className) {
        if (!remapNms || className == null || className.isEmpty()) return className;
        return mapClassNameString(className);
    }


    public String mapRuntimeFieldName(Class<?> runtimeOwner, String spigotName) {
        if (!remapNms || runtimeOwner == null || spigotName == null) return spigotName;
        String cacheKey = runtimeOwner.getName() + '#' + spigotName;
        return boundedComputeIfAbsent(RUNTIME_FIELD_CACHE, cacheKey,
                ignored -> resolveRuntimeMember(runtimeOwner, spigotName, false));
    }


    public String mapRuntimeMethodName(Class<?> runtimeOwner, String spigotName) {
        return mapRuntimeMethodName(runtimeOwner, spigotName, null);
    }

    public String mapRuntimeMethodName(Class<?> runtimeOwner, String spigotName, Class<?>[] parameterTypes) {
        if (!remapNms || runtimeOwner == null || spigotName == null) return spigotName;
        String parameterKey = parameterTypes == null ? "*" : runtimeParameterDescriptor(parameterTypes);
        String cacheKey = runtimeOwner.getName() + '#' + spigotName + '#' + parameterKey;
        return boundedComputeIfAbsent(RUNTIME_METHOD_CACHE, cacheKey,
                ignored -> resolveRuntimeMethod(runtimeOwner, spigotName, parameterTypes));
    }


    /**
     * The name a plugin's own {@code Field.getName()}/{@code Method.getName()} call should see for a
     * member it obtained via a raw {@code getDeclaredFields()}/{@code getDeclaredMethods()} scan - a
     * pattern several legacy NMS-reflection plugins use instead of any hooked reflective API, and
     * which is otherwise invisible to this class entirely. Only translates Intermediary -> Mojang
     * (never further to a Spigot reobf name): real modern Paper runs Mojang-mapped internals directly,
     * so that is what such a plugin's own version-conditional logic already expects to find.
     */
    public String mapRuntimeMemberDisplayName(Class<?> runtimeOwner, String runtimeName, boolean method) {
        if (!remapNms || runtimeOwner == null || runtimeName == null || !needsIntermediaryHop()) return runtimeName;
        String cacheKey = runtimeOwner.getName() + '#' + runtimeName + '#' + (method ? 'M' : 'F') + "#disp";
        return boundedComputeIfAbsent(RUNTIME_DISPLAY_NAME_CACHE, cacheKey,
                ignored -> resolveRuntimeMemberDisplayName(runtimeOwner, runtimeName, method));
    }

    private static String resolveRuntimeMemberDisplayName(Class<?> runtimeOwner, String runtimeName, boolean method) {
        String mojangOwner = mojangNameOf(runtimeOwner);
        Map<MemberNameKey, String> reverse = method ? INTERMEDIARY_TO_MOJANG_METHOD_NAME : INTERMEDIARY_TO_MOJANG_FIELD_NAME;
        String mojangName = reverse.get(new MemberNameKey(mojangOwner, runtimeName));
        if (mojangName != null && !AMBIGUOUS.equals(mojangName)) return mojangName;
        return runtimeName;
    }

    private static boolean isIntermediaryMemberName(String name) {
        return name != null && (name.startsWith("method_") || name.startsWith("field_"))
                && name.length() > 7 && Character.isDigit(name.charAt(name.indexOf('_') + 1));
    }

    private static boolean namesRuntimeMember(Class<?> runtimeOwner, String name, boolean method) {
        if (runtimeOwner == null || name == null) return false;
        String key = runtimeOwner.getName() + '#' + name + '#' + (method ? 'M' : 'F');
        return "true".equals(boundedComputeIfAbsent(ALREADY_CORRECT_CACHE, key,
                ignored -> Boolean.toString(declaresMember(runtimeOwner, name, method))));
    }

    private static boolean declaresMember(Class<?> runtimeOwner, String name, boolean method) {
        try {
            for (Class<?> current = runtimeOwner; current != null; current = current.getSuperclass()) {
                if (method) {
                    for (java.lang.reflect.Method candidate : current.getDeclaredMethods()) {
                        if (candidate.getName().equals(name)) return true;
                    }
                    if (interfaceDeclaresMethod(current.getInterfaces(), name)) return true;
                } else {
                    for (java.lang.reflect.Field candidate : current.getDeclaredFields()) {
                        if (candidate.getName().equals(name)) return true;
                    }
                }
            }
            if (method) {
                for (java.lang.reflect.Method candidate : Object.class.getDeclaredMethods()) {
                    if (candidate.getName().equals(name)) return true;
                }
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    private static boolean interfaceDeclaresMethod(Class<?>[] interfaces, String name) {
        for (Class<?> iface : interfaces) {
            for (java.lang.reflect.Method candidate : iface.getDeclaredMethods()) {
                if (candidate.getName().equals(name)) return true;
            }
            if (interfaceDeclaresMethod(iface.getInterfaces(), name)) return true;
        }
        return false;
    }

    /** The loaded Minecraft class behind a Spigot owner name, or null if it cannot be resolved. */
    private static Class<?> runtimeClassFor(String spigotOwner) {
        String mojangOwner = CLASS_MAP.getOrDefault(spigotOwner, spigotOwner);
        try {
            ClassLoader loader = LunarArcServer.modClassLoader();
            if (loader == null) loader = LunarArcRemapper.class.getClassLoader();
            return Class.forName(mojangOwner.replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /** Same as {@link #runtimeClassFor} but starting from a Mojang owner name and resolving it in
     *  whichever namespace the runtime actually uses - Mojang on NeoForge/Forge, Intermediary on
     *  Fabric/Quilt - so callers on the intermediary-hop path can check whether a member already
     *  exists under its Mojang name on the real class (enum values()/ordinal(), Mixin-added
     *  CraftBukkit accessors, and inherited Object methods all do) before treating it as missing. */
    private static Class<?> resolveRuntimeClassForMojang(String mojangOwner) {
        String runtimeName = needsIntermediaryHop()
                ? MOJANG_TO_INTERMEDIARY_CLASS.getOrDefault(mojangOwner, mojangOwner)
                : mojangOwner;
        try {
            ClassLoader loader = LunarArcServer.modClassLoader();
            if (loader == null) loader = LunarArcRemapper.class.getClassLoader();
            return Class.forName(runtimeName.replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static String boundedComputeIfAbsent(Map<String, String> cache, String key,
                                                  java.util.function.Function<String, String> mapping) {
        String existing = cache.get(key);
        if (existing != null) return existing;
        if (cache.size() >= DYNAMIC_CACHE_LIMIT) return mapping.apply(key);
        return cache.computeIfAbsent(key, mapping);
    }

    private static boolean isNmsRuntimeClass(Class<?> runtimeOwner) {
        return runtimeOwner != null && runtimeOwner.getName().startsWith("net.minecraft.");
    }

    private String resolveRuntimeMember(Class<?> runtimeOwner, String spigotName, boolean method) {
        String resolved = lookupRuntimeMember(runtimeOwner, spigotName, method);
        if (resolved != null) return resolved;
        if (isNmsRuntimeClass(runtimeOwner) && !namesRuntimeMember(runtimeOwner, spigotName, method)) {
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
                LOGGER.warn("No reflective mapping found for {} {}#{} — a plugin's reflective lookup is "
                                + "likely to throw NoSuchFieldException/NoSuchMethodException.",
                        method ? "method" : "field", runtimeOwner.getName(), spigotName);
            } else {
                io.lunararcdevs.lunararc.common.LunarArcDebug.hiddenLookupFailure(LOGGER);
            }
        }
        return spigotName;
    }

    private String lookupRuntimeMember(Class<?> runtimeOwner, String spigotName, boolean method) {
        for (Class<?> current = runtimeOwner; current != null; current = current.getSuperclass()) {
            String mojangOwner = mojangNameOf(current);
            String spigotOwner = MOJANG_TO_SPIGOT_CLASS.getOrDefault(mojangOwner, mojangOwner);
            Map<MemberNameKey, String> names = method ? METHOD_NAME_MAP : FIELD_NAME_MAP;
            String unique = names.get(new MemberNameKey(spigotOwner, spigotName));
            if (unique != null && !AMBIGUOUS.equals(unique)) return toRuntimeMemberName(mojangOwner, unique, method);
            if (needsIntermediaryHop()) {
                Map<MemberNameKey, String> intermediaryNames = method ? INTERMEDIARY_METHOD_NAME_MAP : INTERMEDIARY_FIELD_NAME_MAP;
                String direct = intermediaryNames.get(new MemberNameKey(mojangOwner, spigotName));
                if (direct != null && !AMBIGUOUS.equals(direct)) return direct;
            }
            if (method) {
                for (Class<?> iface : current.getInterfaces()) {
                    String resolved = lookupRuntimeMember(iface, spigotName, true);
                    if (resolved != null) return resolved;
                }
            }
        }
        return null;
    }

    private static String mojangNameOf(Class<?> runtimeClass) {
        String internal = runtimeClass.getName().replace('.', '/');
        if (needsIntermediaryHop()) {
            String mojang = INTERMEDIARY_TO_MOJANG_CLASS.get(internal);
            if (mojang != null) return mojang;
        }
        return internal;
    }

    private static String toRuntimeMemberName(String mojangOwner, String mojangName, boolean method) {
        if (!needsIntermediaryHop()) return mojangName;
        Map<MemberNameKey, String> names = method ? INTERMEDIARY_METHOD_NAME_MAP : INTERMEDIARY_FIELD_NAME_MAP;
        String unique = names.get(new MemberNameKey(mojangOwner, mojangName));
        return unique != null && !AMBIGUOUS.equals(unique) ? unique : mojangName;
    }

    private String resolveRuntimeMethod(Class<?> runtimeOwner, String spigotName, Class<?>[] parameterTypes) {
        String parameterDescriptor = parameterTypes == null ? null : runtimeParameterDescriptor(parameterTypes);
        String resolved = lookupRuntimeMethod(runtimeOwner, spigotName, parameterTypes, parameterDescriptor);
        if (resolved != null) return resolved;
        if (isNmsRuntimeClass(runtimeOwner) && !namesRuntimeMember(runtimeOwner, spigotName, true)) {
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.REFLECT) {
                LOGGER.warn("No reflective mapping found for method {}#{}{} — a plugin's reflective lookup is "
                                + "likely to throw NoSuchMethodException.",
                        runtimeOwner.getName(), spigotName, parameterDescriptor == null ? "(*)" : parameterDescriptor);
            } else {
                io.lunararcdevs.lunararc.common.LunarArcDebug.hiddenLookupFailure(LOGGER);
            }
        }
        return spigotName;
    }

    private String lookupRuntimeMethod(Class<?> runtimeOwner, String spigotName, Class<?>[] parameterTypes,
                                       String parameterDescriptor) {
        for (Class<?> current = runtimeOwner; current != null; current = current.getSuperclass()) {
            String mojangOwner = mojangNameOf(current);
            String spigotOwner = MOJANG_TO_SPIGOT_CLASS.getOrDefault(mojangOwner, mojangOwner);
            String descriptorMapped = parameterDescriptor == null ? null
                    : findMethodMappingByParameters(spigotOwner, spigotName, parameterDescriptor);
            if (descriptorMapped != null) {
                String runtimeName = toRuntimeMemberName(mojangOwner, descriptorMapped, true);
                if (acceptsRuntimeParameters(runtimeOwner, runtimeName, parameterTypes)) return runtimeName;
            }

            String unique = METHOD_NAME_MAP.get(new MemberNameKey(spigotOwner, spigotName));
            if (unique != null && !AMBIGUOUS.equals(unique)) {
                String runtimeName = toRuntimeMemberName(mojangOwner, unique, true);
                if (acceptsRuntimeParameters(runtimeOwner, runtimeName, parameterTypes)) return runtimeName;
            }

            if (needsIntermediaryHop()) {
                String direct = INTERMEDIARY_METHOD_NAME_MAP.get(new MemberNameKey(mojangOwner, spigotName));
                if (direct != null && !AMBIGUOUS.equals(direct)
                        && acceptsRuntimeParameters(runtimeOwner, direct, parameterTypes)) return direct;
                if (AMBIGUOUS.equals(direct) && parameterTypes != null) {
                    String mojangDescriptor = runtimeParameterDescriptorMojang(parameterTypes);
                    String exact = findIntermediaryMethodMappingByParameters(mojangOwner, spigotName, mojangDescriptor);
                    if (exact != null && acceptsRuntimeParameters(runtimeOwner, exact, parameterTypes)) return exact;
                }
            }
            for (Class<?> iface : current.getInterfaces()) {
                String resolved = lookupRuntimeMethod(iface, spigotName, parameterTypes, parameterDescriptor);
                if (resolved != null) return resolved;
            }
        }
        return null;
    }

    private static boolean acceptsRuntimeParameters(Class<?> owner, String name, Class<?>[] parameters) {
        if (parameters == null) return true;
        try {
            owner.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException ignored) {
            for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
                try {
                    current.getDeclaredMethod(name, parameters);
                    return true;
                } catch (NoSuchMethodException missing) {
                }
            }
            return false;
        }
    }

    private static String findMethodMappingByParameters(String spigotOwner, String spigotName,
                                                         String parameterDescriptor) {
        List<Map.Entry<MemberKey, String>> overloads =
                METHOD_OVERLOAD_INDEX.get(new MemberNameKey(spigotOwner, spigotName));
        if (overloads == null || overloads.isEmpty()) return null;
        String resolved = null;
        for (Map.Entry<MemberKey, String> entry : overloads) {
            String descriptor = entry.getKey().descriptor();
            if ("*".equals(descriptor)) {
                if (resolved == null) resolved = entry.getValue();
                else if (!resolved.equals(entry.getValue())) return null;
                continue;
            }
            int close = descriptor.indexOf(')');
            if (close < 0 || !descriptor.substring(0, close + 1).equals(parameterDescriptor)) continue;
            if (resolved == null) resolved = entry.getValue();
            else if (!resolved.equals(entry.getValue())) return null;
        }
        return resolved;
    }

    /** Same as {@link #findMethodMappingByParameters} but over the Mojang-keyed intermediary overload
     *  index, for disambiguating a plugin's own direct-Mojang-name candidate by parameter descriptor
     *  when the name alone is ambiguous (see the call site in {@link #lookupRuntimeMethod}). */
    private static String findIntermediaryMethodMappingByParameters(String mojangOwner, String mojangName,
                                                                      String parameterDescriptor) {
        List<Map.Entry<MemberKey, String>> overloads =
                INTERMEDIARY_METHOD_OVERLOAD_INDEX.get(new MemberNameKey(mojangOwner, mojangName));
        if (overloads == null || overloads.isEmpty()) return null;
        String resolved = null;
        for (Map.Entry<MemberKey, String> entry : overloads) {
            String descriptor = entry.getKey().descriptor();
            int close = descriptor.indexOf(')');
            if (close < 0 || !descriptor.substring(0, close + 1).equals(parameterDescriptor)) continue;
            if (resolved == null) resolved = entry.getValue();
            else if (!resolved.equals(entry.getValue())) return null;
        }
        return resolved;
    }

    private static String runtimeParameterDescriptor(Class<?>[] parameterTypes) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameterType : parameterTypes) descriptor.append(runtimeTypeDescriptor(parameterType));
        return descriptor.append(')').toString();
    }

    private static String runtimeTypeDescriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (type.isPrimitive()) {
            if (type == void.class) return "V";
            if (type == boolean.class) return "Z";
            if (type == byte.class) return "B";
            if (type == char.class) return "C";
            if (type == short.class) return "S";
            if (type == int.class) return "I";
            if (type == long.class) return "J";
            if (type == float.class) return "F";
            if (type == double.class) return "D";
        }
        String internal = type.getName().replace('.', '/');
        String spigot = MOJANG_TO_SPIGOT_CLASS.getOrDefault(internal, internal);
        return 'L' + spigot + ';';
    }

    /** Same as {@link #runtimeParameterDescriptor} but in Mojang namespace throughout, for matching
     *  against {@code INTERMEDIARY_METHOD_MAP}'s descriptor-exact keys (which are Mojang-mapped). */
    private static String runtimeParameterDescriptorMojang(Class<?>[] parameterTypes) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameterType : parameterTypes) descriptor.append(runtimeTypeDescriptorMojang(parameterType));
        return descriptor.append(')').toString();
    }

    private static String runtimeTypeDescriptorMojang(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (type.isPrimitive()) {
            if (type == void.class) return "V";
            if (type == boolean.class) return "Z";
            if (type == byte.class) return "B";
            if (type == char.class) return "C";
            if (type == short.class) return "S";
            if (type == int.class) return "I";
            if (type == long.class) return "J";
            if (type == float.class) return "F";
            if (type == double.class) return "D";
        }
        return 'L' + mojangNameOf(type) + ';';
    }

    private String toSpigotOwner(String owner) {
        String spigot = MOJANG_TO_SPIGOT_CLASS.get(owner);
        if (spigot != null) return spigot;
        if (CLASS_MAP.containsKey(owner)) return owner;
        return owner;
    }

    private static String toSpigotDescriptor(String descriptor) {
        return mapDescriptorClasses(descriptor, MOJANG_TO_SPIGOT_CLASS);
    }

    public byte[] transform(byte[] bytecode, String className) {
        return transformInternal(bytecode, className == null ? "<unknown>" : className);
    }

    public byte[] transform(byte[] bytecode) {
        return transformInternal(bytecode, "<unknown>");
    }

    private byte[] transformInternal(byte[] bytecode, String className) {
        if (bytecode == null || bytecode.length < 8) return bytecode;
        try {
            ClassReader reader = new ClassReader(bytecode);
            ClassWriter writer = new ClassWriter(0);

            boolean classNeedsNms = remapNms && containsNmsReference(bytecode);
            LunarArcRemapper effective = classNeedsNms || !remapNms
                    ? this
                    : new LunarArcRemapper(false);

            ClassVisitor remapper = new ClassRemapper(writer, effective);
            ClassVisitor visitor = remapNms ? new ReflectionMemberVisitor(remapper) : remapper;
            if (io.lunararcdevs.lunararc.common.LunarArcDebug.REMAP) {
                io.lunararcdevs.lunararc.common.LunarArcDebug.remap(
                        "{}: nmsSymbols={} reflectionBridge={}", className, classNeedsNms, remapNms);
            }
            visitor = effective.compatibilityVisitor(visitor, className);
            reader.accept(visitor, 0);
            byte[] remapped = writer.toByteArray();
            return applyPluginPatchers(remapped, className);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to remap plugin class " + className, e);
        }
    }

    private static final java.util.List<io.lunararcdevs.lunararc.common.mod.util.remapper.patcher.PluginPatcher> PLUGIN_PATCHERS =
            io.lunararcdevs.lunararc.common.mod.util.remapper.patcher.LunarArcPluginPatcherLoader.load();

    private static byte[] applyPluginPatchers(byte[] remapped, String className) {
        if (PLUGIN_PATCHERS.isEmpty()) return remapped;
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(remapped).accept(node, 0);
        boolean matched = false;
        for (io.lunararcdevs.lunararc.common.mod.util.remapper.patcher.PluginPatcher patcher : PLUGIN_PATCHERS) {
            try {
                patcher.handleClass(node, io.lunararcdevs.lunararc.common.mod.util.remapper.patcher.LunarArcGlobalClassRepo.INSTANCE);
                matched = true;
            } catch (RuntimeException e) {
                LOGGER.warn("Plugin patcher {} failed on class {}", patcher.getClass().getName(), className, e);
            }
        }
        if (!matched) return remapped;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean containsNmsReference(byte[] bytecode) {
        return containsAscii(bytecode, "net/minecraft/")
                || containsAscii(bytecode, "net.minecraft.");
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (haystack[i + j] != target[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private ClassVisitor compatibilityVisitor(ClassVisitor delegate, String className) {
        return new ClassVisitor(Opcodes.ASM9, delegate) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/minecraft/server/MinecraftServer".equals(owner)
                                && "getServer".equals(methodName)
                                && "()Lnet/minecraft/server/MinecraftServer;".equals(methodDescriptor)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "io/lunararcdevs/lunararc/common/LunarArcServerAccess",
                                    "getMinecraftServer", methodDescriptor, false);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                    }
                };
            }
        };
    }

    private String mapClassNameString(String value) {
        if (value == null || value.isEmpty()) return value;
        boolean binaryName = value.indexOf('.') >= 0 && value.indexOf('/') < 0;
        String internal = binaryName ? value.replace('.', '/') : value;
        if (!internal.startsWith(CRAFTBUKKIT_PREFIX) && !internal.startsWith("net/minecraft/")) return value;
        String mapped = map(internal);
        return mapped.equals(internal) ? value : (binaryName ? mapped.replace('/', '.') : mapped);
    }

    private final class ReflectionMemberVisitor extends ClassVisitor {
        private ReflectionMemberVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM9, delegate) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    String bridgeOwner = "io/lunararcdevs/lunararc/common/mod/LunarArcReflectionBridge";
                    if (opcode == Opcodes.INVOKESTATIC && "java/lang/Class".equals(owner) && "forName".equals(methodName)) {
                        if ("(Ljava/lang/String;)Ljava/lang/Class;".equals(methodDescriptor)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "forName", methodDescriptor, false);
                            return;
                        }
                        if ("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;".equals(methodDescriptor)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "forName", methodDescriptor, false);
                            return;
                        }
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && "java/lang/ClassLoader".equals(owner)
                            && "loadClass".equals(methodName)
                            && "(Ljava/lang/String;)Ljava/lang/Class;".equals(methodDescriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "loadClass",
                                "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;", false);
                        return;
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && "java/lang/invoke/MethodHandles$Lookup".equals(owner)
                            && ("findGetter".equals(methodName) || "findSetter".equals(methodName)
                                || "findStaticGetter".equals(methodName) || "findStaticSetter".equals(methodName))
                            && "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;".equals(methodDescriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, methodName,
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                false);
                        return;
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && "java/lang/reflect/Field".equals(owner)
                            && "getName".equals(methodName) && "()Ljava/lang/String;".equals(methodDescriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "fieldName",
                                "(Ljava/lang/reflect/Field;)Ljava/lang/String;", false);
                        return;
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && "java/lang/reflect/Method".equals(owner)
                            && "getName".equals(methodName) && "()Ljava/lang/String;".equals(methodDescriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "methodName",
                                "(Ljava/lang/reflect/Method;)Ljava/lang/String;", false);
                        return;
                    }
                    if ("java/lang/Class".equals(owner)) {
                        switch (methodName) {
                            case "getField" -> {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "getField",
                                        "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
                                return;
                            }
                            case "getDeclaredField" -> {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "getDeclaredField",
                                        "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
                                return;
                            }
                            case "getMethod" -> {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "getMethod",
                                        "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                                return;
                            }
                            case "getDeclaredMethod" -> {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, bridgeOwner, "getDeclaredMethod",
                                        "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                                return;
                            }
                            default -> {
                            }
                        }
                    }
                    super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                }
            };
        }
    }

    private static String mapDescriptorClasses(String descriptor, Map<String, String> classes) {
        if (descriptor == null || descriptor.isEmpty() || "*".equals(descriptor)) return descriptor;
        StringBuilder out = new StringBuilder(descriptor.length());
        for (int i = 0; i < descriptor.length(); i++) {
            char c = descriptor.charAt(i);
            out.append(c);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) break;
                String name = descriptor.substring(i + 1, end);
                out.append(classes.getOrDefault(name, name));
                out.append(';');
                i = end;
            }
        }
        return out.toString();
    }

    private record MemberKey(String owner, String name, String descriptor) {
    }

    private record MemberNameKey(String owner, String name) {
    }

    private record PendingMember(boolean method, String spigotOwner, String spigotName,
                                 String mojangDescriptor, String mojangName) {
    }
}
