package io.lunararcdevs.lunararc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class LunarArcDebug {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunarArc/Debug");
    private static final Path OUTPUT = Path.of("logs", "lunararc-debug.log");
    private static final Object LOCK = new Object();
    public static final boolean REFLECT;
    public static final boolean REMAP;
    public static final boolean CLASSLOAD;
    public static final boolean ENTITY;
    public static final boolean FLUID;
    public static final boolean COMMAND;
    public static final boolean INTERACT;
    public static final boolean TIMING;
    public static final boolean PLUGIN;
    public static final boolean DISMOUNT;
    public static final boolean NETWORK;

    private static final java.util.concurrent.atomic.AtomicBoolean HIDDEN_LOOKUP_HINT = new java.util.concurrent.atomic.AtomicBoolean();

    public static void hiddenLookupFailure(Logger logger) {
        if (HIDDEN_LOOKUP_HINT.compareAndSet(false, true)) {
            logger.info("Some plugins probe optional or legacy classes and members, so failed reflective lookups are not "
                    + "logged. Start with -Dlunararc.debug=reflect to see each one.");
        }
    }

    private static BufferedWriter writer;
    private static boolean unusable;

    static {
        java.util.Set<String> channels = new java.util.HashSet<>();
        for (String channel : System.getProperty("lunararc.debug", "").split(",")) {
            channels.add(channel.trim().toLowerCase(java.util.Locale.ROOT));
        }
        boolean all = channels.contains("all");
        INTERACT = all || channels.contains("interact");
        TIMING = all || channels.contains("timing");
        REFLECT = all || channels.contains("reflect");
        REMAP = all || channels.contains("remap");
        CLASSLOAD = all || channels.contains("classload");
        ENTITY = all || channels.contains("entity");
        FLUID = all || channels.contains("fluid");
        COMMAND = all || channels.contains("command");
        PLUGIN = all || channels.contains("plugin");
        DISMOUNT = all || channels.contains("dismount");
        NETWORK = all || channels.contains("network");
        if (INTERACT || TIMING || REFLECT || REMAP || CLASSLOAD || ENTITY || FLUID || COMMAND || PLUGIN || DISMOUNT || NETWORK) {
            StringBuilder enabled = new StringBuilder();
            if (INTERACT) enabled.append(" interact");
            if (TIMING) enabled.append(" timing");
            if (REFLECT) enabled.append(" reflect");
            if (REMAP) enabled.append(" remap");
            if (CLASSLOAD) enabled.append(" classload");
            if (ENTITY) enabled.append(" entity");
            if (FLUID) enabled.append(" fluid");
            if (COMMAND) enabled.append(" command");
            if (PLUGIN) enabled.append(" plugin");
            if (DISMOUNT) enabled.append(" dismount");
            if (NETWORK) enabled.append(" network");

            try {
                open();
            } catch (Throwable ignored) {
                // open() reports its own failure once and disables itself.
            }

            String announcement = "[LunarArc/Debug] Debug channels enabled:" + enabled + " - writing to "
                    + OUTPUT.toAbsolutePath() + ". Verbose by design; not meant to be left on for a "
                    + "running server.";
            LOGGER.info(announcement);
            System.out.println(announcement);
        }
    }

    private LunarArcDebug() {
    }

    /** Log on the reflect channel. Call behind {@code if (LunarArcDebug.REFLECT)}. */
    public static void reflect(String format, Object... args) {
        write("reflect", format, args);
    }

    /** Log on the remap channel. Call behind {@code if (LunarArcDebug.REMAP)}. */
    public static void remap(String format, Object... args) {
        write("remap", format, args);
    }

    /** Log on the classload channel. Call behind {@code if (LunarArcDebug.CLASSLOAD)}. */
    public static void classload(String format, Object... args) {
        write("classload", format, args);
    }

    /** Log on the entity channel. Call behind {@code if (LunarArcDebug.ENTITY)}. */
    public static void entity(String format, Object... args) {
        write("entity", format, args);
    }

    /** Log on the interact channel. Call behind {@code if (LunarArcDebug.INTERACT)}. */
    public static void interact(String format, Object... args) {
        write("interact", format, args);
        LOGGER.info("[interact] " + format(format, args));
    }

    /** The enabled channels, or "none", for reporting alongside the build banner. */
    public static String enabledChannels() {
        StringBuilder enabled = new StringBuilder();
        if (INTERACT) enabled.append("interact ");
        if (TIMING) enabled.append("timing ");
        if (REFLECT) enabled.append("reflect ");
        if (REMAP) enabled.append("remap ");
        if (CLASSLOAD) enabled.append("classload ");
        if (ENTITY) enabled.append("entity ");
        if (FLUID) enabled.append("fluid ");
        if (COMMAND) enabled.append("command ");
        if (PLUGIN) enabled.append("plugin ");
        if (DISMOUNT) enabled.append("dismount ");
        if (NETWORK) enabled.append("network ");
        return enabled.isEmpty() ? "none" : enabled.toString().trim().replace(' ', ',');
    }

    /** Log on the dismount channel. Call behind {@code if (LunarArcDebug.DISMOUNT)}. */
    public static void dismount(String format, Object... args) {
        write("dismount", format, args);
    }

    /** Log on the network channel. Call behind {@code if (LunarArcDebug.NETWORK)}. */
    public static void network(String format, Object... args) {
        write("network", format, args);
    }

    /** Log on the command channel. Call behind {@code if (LunarArcDebug.COMMAND)}. */
    public static void command(String format, Object... args) {
        write("command", format, args);
    }

    /** Log on the timing channel. Call behind {@code if (LunarArcDebug.TIMING)}. */
    public static void timing(String format, Object... args) {
        write("timing", format, args);
    }

    /** Log on the fluid channel. Call behind {@code if (LunarArcDebug.FLUID)}. */
    public static void fluid(String format, Object... args) {
        write("fluid", format, args);
    }

    private static void write(String channel, String format, Object... args) {
        boolean enabled = switch (channel) {
            case "interact" -> INTERACT;
            case "timing" -> TIMING;
            case "reflect" -> REFLECT;
            case "remap" -> REMAP;
            case "classload" -> CLASSLOAD;
            case "entity" -> ENTITY;
            case "fluid" -> FLUID;
            case "command" -> COMMAND;
            case "dismount" -> DISMOUNT;
            case "network" -> NETWORK;
            default -> false;
        };
        if (!enabled) return;
        try {
            synchronized (LOCK) {
                BufferedWriter out = open();
                if (out == null) return;
                out.write(Instant.now() + " [" + channel + "] " + format(format, args) + "\n");
                // Flushed per line rather than on a buffer boundary: the failures this exists to
                // trace routinely end in a crash or a hung server, and a half-written buffer is
                // exactly the tail that matters.
                out.flush();
            }
        } catch (Throwable ignored) {
            // Diagnostics must never be the reason a server stops.
        }
    }

    private static BufferedWriter open() throws IOException {
        if (writer != null) return writer;
        if (unusable) return null;
        try {
            Path parent = OUTPUT.getParent();
            if (parent != null) Files.createDirectories(parent);
            // TRUNCATE, matching latest.log: one file per run is what a reproduction wants, and
            // an appending trace at this volume would be unreadable by the third restart.
            writer = Files.newBufferedWriter(OUTPUT, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writer.write("LunarArc debug trace\n");
            writer.write("session-start: " + Instant.now() + "\n");
            writer.write("channels:"
                    + (INTERACT ? " interact" : "")
                    + (TIMING ? " timing" : "")
                    + (REFLECT ? " reflect" : "")
                    + (REMAP ? " remap" : "")
                    + (CLASSLOAD ? " classload" : "")
                    + (ENTITY ? " entity" : "")
                    + (FLUID ? " fluid" : "")
                    + (COMMAND ? " command" : "")
                    + (DISMOUNT ? " dismount" : "")
                    + (NETWORK ? " network" : "") + "\n");
            writer.write("This file is passive tracing only; it does not change plugin behaviour.\n\n");
            writer.flush();
            Runtime.getRuntime().addShutdownHook(new Thread(LunarArcDebug::close, "LunarArc-debug-close"));
            return writer;
        } catch (Throwable failure) {
            // One complaint, then stay quiet: a read-only logs directory should not produce a
            // warning per traced lookup.
            unusable = true;
            LOGGER.warn("Cannot write {} - debug tracing disabled for this run: {}",
                    OUTPUT.toAbsolutePath(), failure.toString());
            return null;
        }
    }

    private static void close() {
        synchronized (LOCK) {
            if (writer == null) return;
            try {
                writer.flush();
                writer.close();
            } catch (Throwable ignored) {
            }
            writer = null;
        }
    }

    /**
     * SLF4J-style {@code {}} substitution, so call sites read the same as the logging around them
     * and cost nothing to convert if a channel ever graduates to a real logger.
     */
    private static String format(String format, Object... args) {
        if (args == null || args.length == 0) return format;
        StringBuilder out = new StringBuilder(format.length() + 32);
        int arg = 0;
        int index = 0;
        while (index < format.length()) {
            int placeholder = format.indexOf("{}", index);
            if (placeholder < 0 || arg >= args.length) {
                out.append(format, index, format.length());
                break;
            }
            out.append(format, index, placeholder).append(args[arg++]);
            index = placeholder + 2;
        }
        return out.toString();
    }

    /** The class that called into the bridge, skipping LunarArc's own frames. Walks the stack, so only ever call it behind an enabled channel. */
    public static String caller() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(type -> !type.getName().startsWith("io.lunararcdevs.lunararc."))
                        .filter(type -> !type.getName().startsWith("java."))
                        .map(Class::getName)
                        .findFirst()
                        .orElse("unknown"));
    }
}
