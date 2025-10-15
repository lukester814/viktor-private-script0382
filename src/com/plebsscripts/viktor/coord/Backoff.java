package com.plebsscripts.viktor.coord;

public class Backoff {
    private long delay = 500;

    public void reset() { delay = 500; }
    public void increase() { delay = Math.min(delay * 2, 30_000); }
    public void sleep() {
        try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
    }
}
