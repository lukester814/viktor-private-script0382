package com.plebsscripts.viktor.util;

import org.dreambot.api.utilities.Logger;

/**
 * Centralized logging for Viktor bot.
 * Uses DreamBot's Logger for console output with color coding.
 */
public class Logs {

    private static final String PREFIX = "[Viktor] ";

    /**
     * Log info message (default white text)
     */
    public static void info(String msg) {
        Logger.log(PREFIX + msg);
    }

    /**
     * Log warning message (yellow text in DreamBot console)
     */
    public static void warn(String msg) {
        Logger.warn(PREFIX + msg);
    }

    /**
     * Log error message (red text in DreamBot console)
     */
    public static void error(String msg) {
        Logger.error(PREFIX + msg);
    }

    /**
     * Log debug message (only shown when debug mode enabled)
     */
    public static void debug(String msg) {
        if (isDebugEnabled()) {
            Logger.log(PREFIX + "[DEBUG] " + msg);
        }
    }

    /**
     * Log success message (green checkmark)
     */
    public static void success(String msg) {
        Logger.log(PREFIX + "✓ " + msg);
    }

    /**
     * Log trade-specific message (for filtering)
     */
    public static void trade(String msg) {
        Logger.log(PREFIX + "[TRADE] " + msg);
    }

    /**
     * Log with custom prefix
     */
    public static void log(String prefix, String msg) {
        Logger.log(PREFIX + prefix + " " + msg);
    }

    /**
     * Check if debug mode is enabled
     */
    private static boolean isDebugEnabled() {
        // Could read from Settings or system property
        return true; // temp
    }
}
