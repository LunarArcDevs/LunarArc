package io.lunararcdevs.lunararc.launcher;

import java.lang.reflect.InvocationTargetException;

public final class LauncherEntry {

    private static final int MIN_JAVA = 21;
    private static final int MIN_CLASS_VERSION = 65;
    private static final int MAX_TESTED_JAVA = 25;
    private static final int MAX_TESTED_CLASS_VERSION = 69;

    private LauncherEntry() {
    }

    public static void main(String[] args) throws Throwable {
        int classVersion = (int) Float.parseFloat(System.getProperty("java.class.version"));
        if (classVersion < MIN_CLASS_VERSION) {
            System.err.println("LunarArc requires Java " + MIN_JAVA + " or newer.");
            System.err.println("Current: " + System.getProperty("java.version"));
            System.exit(1);
            return;
        }
        if (classVersion > MAX_TESTED_CLASS_VERSION) {
            System.err.println("Warning: LunarArc is tested up to Java " + MAX_TESTED_JAVA + " and may not run on newer versions.");
            System.err.println("Current: " + System.getProperty("java.version"));
            System.err.flush();
            Thread.sleep(3000L);
        }
        try {
            Class.forName("io.lunararcdevs.lunararc.launcher.Launcher")
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause() != null ? failure.getCause() : failure;
        }
    }
}
