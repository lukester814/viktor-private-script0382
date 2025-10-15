package com.plebsscripts.viktor.limits;

public class LimitHeuristics {
    public boolean shouldSkip(String itemName, int currentCount, int maxLimit) {
        return currentCount >= maxLimit;
    }
}
