package com.plebsscripts.viktor.core;

public class Timers {

    // 👇 add this method
    public void sleepShort() {
        try {
            Thread.sleep(shortWait());
        } catch (InterruptedException ignored) {}
    }

    // Short random sleep (e.g. between state ticks)
    public int shortWait() {
        return 400; // milliseconds
    }

    // Medium-length sleep (for mid-actions)
    public int mediumWait() {
        return 800;
    }

    // Long sleep (for longer tasks or cool-downs)
    public int longWait() {
        return 1200;
    }
}
