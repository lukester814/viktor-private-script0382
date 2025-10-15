package com.plebsscripts.viktor.util;

import org.dreambot.api.utilities.Logger;

public class Logs {
    public static void info(String msg)  { Logger.log("[Viktor] " + msg); }
    public static void warn(String msg)  { Logger.log("[Viktor:WARN] " + msg); }
    public static void error(String msg) { Logger.log("[Viktor:ERROR] " + msg); }
}
