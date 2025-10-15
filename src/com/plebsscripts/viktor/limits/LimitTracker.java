package com.plebsscripts.viktor.limits;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class LimitTracker {
    private final Map<String, Long> blockedUntil = new HashMap<String, Long>(); // epoch seconds

    public boolean isBlocked(String itemName) {
        Long t = blockedUntil.get(itemName.toLowerCase());
        return t != null && Instant.now().getEpochSecond() < t;
    }

    public long blockFor4h(String itemName) {
        long until = Instant.now().getEpochSecond() + 4 * 3600;
        blockedUntil.put(itemName.toLowerCase(), until);
        return until;
    }

    // persistence helpers
    public Map<String, Long> snapshot() { return new HashMap<String, Long>(blockedUntil); }
    public void restore(String itemName, long untilEpoch) { blockedUntil.put(itemName.toLowerCase(), untilEpoch); }
}
