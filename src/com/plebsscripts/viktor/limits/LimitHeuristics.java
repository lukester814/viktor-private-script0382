package com.plebsscripts.viktor.limits;

/**
 * Future: Smart heuristics for predicting 4h trade limits.
 * Currently just a simple check.
 */
public class LimitHeuristics {

    public boolean shouldSkip(String itemName, int currentCount, int maxLimit) {
        return currentCount >= maxLimit;
    }
}
