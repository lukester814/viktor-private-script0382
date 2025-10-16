package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.methods.map.Tile;

/**
 * Handles navigation to/from the Grand Exchange.
 */
public class GENavigator {

    // Grand Exchange area (Varrock GE)
    private static final Area GE_AREA = new Area(
            new Tile(3161, 3485, 0),
            new Tile(3168, 3489, 0)
    );

    // Slightly larger area for "near GE" check
    private static final Area GE_NEARBY = new Area(
            new Tile(3155, 3480, 0),
            new Tile(3175, 3495, 0)
    );

    /**
     * Check if player is at the GE (inside or very close)
     */
    public boolean atGE() {
        Player local = Players.getLocal();
        if (local == null) {
            return false;
        }

        // Check if player is in GE area or nearby
        return GE_AREA.contains(local) || GE_NEARBY.contains(local);
    }

    /**
     * Walk to the Grand Exchange.
     * Returns true if already there, false if walking/in progress.
     */
    public boolean walkToGE() {
        Player local = Players.getLocal();

        if (local == null) {
            Logs.warn("Player not found, cannot walk to GE");
            return false;
        }

        // Already at GE
        if (atGE()) {
            return true;
        }

        Logs.info("Walking to Grand Exchange...");

        // Use DreamBot's webwalking
        if (Walking.shouldWalk()) {
            Walking.walk(GE_AREA.getCenter());
        }

        // Return false to indicate still walking
        return false;
    }

    /**
     * Get distance from player to GE center
     */
    public int distanceToGE() {
        Player local = Players.getLocal();
        if (local == null) {
            return Integer.MAX_VALUE;
        }

        return (int) local.distance(GE_AREA.getCenter());
    }

    /**
     * Check if player can walk to GE (not in combat, etc.)
     */
    public boolean canWalkToGE() {
        Player local = Players.getLocal();
        if (local == null) {
            return false;
        }

        // Check if player is in combat or busy
        if (local.isInCombat() || local.isAnimating()) {
            return false;
        }

        return true;
    }

    /**
     * Wait until player reaches GE (with timeout)
     */
    public boolean waitUntilAtGE(int timeoutMs) {
        long start = System.currentTimeMillis();

        while (!atGE() && (System.currentTimeMillis() - start) < timeoutMs) {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return atGE();
    }
}
