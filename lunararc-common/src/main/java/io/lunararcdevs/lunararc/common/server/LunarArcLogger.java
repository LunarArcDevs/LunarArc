package io.lunararcdevs.lunararc.common.server;

import java.util.logging.LogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LunarArcLogger extends java.util.logging.Logger {
    private final Logger slf4j;

    public static java.util.logging.Logger getLogger(String name) {
        return new LunarArcLogger(name);
    }

    private LunarArcLogger(String name) {
        super(name, null);
        this.slf4j = LoggerFactory.getLogger(name);
    }

    @Override
    public void log(LogRecord record) {
        String msg = record.getMessage();
        Throwable ex = record.getThrown();
        java.util.logging.Level level = record.getLevel();

        if (level == java.util.logging.Level.SEVERE) {
            if (ex != null) slf4j.error(msg, ex); else slf4j.error(msg);
        } else if (level == java.util.logging.Level.WARNING) {
            if (ex != null) slf4j.warn(msg, ex); else slf4j.warn(msg);
        } else if (level == java.util.logging.Level.INFO) {
            if (ex != null) slf4j.debug(msg, ex); else slf4j.debug(msg);
        } else if (level == java.util.logging.Level.CONFIG || level == java.util.logging.Level.FINE) {
            if (ex != null) slf4j.debug(msg, ex); else slf4j.debug(msg);
        } else {
            if (ex != null) slf4j.trace(msg, ex); else slf4j.trace(msg);
        }
    }
}
