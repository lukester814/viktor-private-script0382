package com.plebsscripts.viktor.util;

import org.dreambot.api.methods.world.World;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.worldhopper.WorldHopper;

import java.util.List;

/**
 * World utilities: F2P/P2P, PvP flags, and safe hopping.
 * Uses DreamBot APIs:
 *  - Worlds.getCurrentWorld() -> int
 *  - Worlds.getNormalizedWorlds() -> List<World>
 *  - WorldHopper.hopWorld(int or World)
 */
public final class WorldDetector {
    private WorldDetector() {}

    /** @return current world number (e.g., 301) or -1 if unavailable. */
    public static int getCurrentWorldNumber() {
        try {
            return Worlds.getCurrentWorld();
        } catch (Throwable t) {
            Logs.warn("WorldDetector: getCurrentWorldNumber failed: " + t.getMessage());
            return -1;
        }
    }

    /** @return current World object by matching the current world number, or null. */
    private static World getCurrentWorldObject() {
        final int id = getCurrentWorldNumber();
        if (id <= 0) return null;
        try {
            final List<World> all = Worlds.getNormalizedWorlds(); // DB javadocs show this method
            if (all == null) return null;
            for (World w : all) {
                if (w != null && w.getWorld() == id) return w;
            }
        } catch (Throwable t) {
            Logs.warn("WorldDetector: getCurrentWorldObject failed: " + t.getMessage());
        }
        return null;
    }

    /** @return true if the current world is F2P; false if members or unknown. */
    public static boolean isF2P() {
        final World w = getCurrentWorldObject();
        if (w == null) {
            Logs.warn("WorldDetector: current world unknown; assuming P2P.");
            return false;
        }
        // Prefer API flag if present
        try {
            // Many examples use w.isF2P(); if absent in your build, fall back to !isMembers()
            boolean f2p = w.isF2P();
            Logs.info((f2p ? "F2P" : "P2P") + " world: " + w.getWorld());
            return f2p;
        } catch (Throwable ignore) {
            boolean members = false;
            try { members = w.isMembers(); } catch (Throwable ignored) {}
            boolean f2p = !members;
            Logs.info((f2p ? "F2P" : "P2P") + " world: " + w.getWorld());
            return f2p;
        }
    }

    public static boolean isP2P() { return !isF2P(); }

    /** @return "F2P" or "P2P" (defaults to P2P on unknown). */
    public static String getWorldType() { return isF2P() ? "F2P" : "P2P"; }

    /** @return true if current world is PvP-like (PvP or High-Risk flags if exposed). */
    public static boolean isHighRiskOrPvp() {
        final World w = getCurrentWorldObject();
        if (w == null) return false;
        boolean pvp = false, high = false;
        try { pvp  = w.isPVP();      } catch (Throwable ignored) {}
        try { high = w.isHighRisk(); } catch (Throwable ignored) {}
        return pvp || high;
    }

    /** @return human-readable string for logs/HUD. */
    public static String getWorldInfo() {
        final World w = getCurrentWorldObject();
        if (w == null) return "World: unknown";
        StringBuilder sb = new StringBuilder();
        sb.append("World ").append(w.getWorld())
                .append(" - ").append(isF2P() ? "F2P" : "P2P");
        try { if (w.isPVP())      sb.append(" [PvP]");      } catch (Throwable ignored) {}
        try { if (w.isHighRisk()) sb.append(" [High Risk]");} catch (Throwable ignored) {}
        return sb.toString();
    }

    // ----------------- Hopping helpers (use WorldHopper) -----------------

    /** Hop to first non-PvP, non-HighRisk world that matches F2P/P2P. */
    public static boolean hopTo(boolean wantMembers) {
        try {
            final List<World> worlds = Worlds.getNormalizedWorlds();
            if (worlds == null || worlds.isEmpty()) return false;
            for (World w : worlds) {
                if (w == null) continue;
                boolean members = false, pvp = false, high = false;
                try { members = w.isMembers(); } catch (Throwable ignored) {}
                try { pvp     = w.isPVP();     } catch (Throwable ignored) {}
                try { high    = w.isHighRisk();} catch (Throwable ignored) {}
                if (members == wantMembers && !pvp && !high) {
                    Logs.info("Hopping to world " + w.getWorld() + " (" + (members ? "P2P" : "F2P") + ")");
                    // both overloads exist; int is simplest
                    return WorldHopper.hopWorld(w.getWorld());
                }
            }
        } catch (Throwable t) {
            Logs.warn("WorldDetector: hopTo failed: " + t.getMessage());
        }
        return false;
    }

    public static boolean hopToF2P() { return hopTo(false); }
    public static boolean hopToP2P() { return hopTo(true); }
}
