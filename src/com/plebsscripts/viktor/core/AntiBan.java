package com.plebsscripts.viktor.core;

public class AntiBan {
    public void idleSleep() {
        try { Thread.sleep(600 + (int)(Math.random() * 400)); } catch (InterruptedException ignored) {}
    }
    public void shortPause() {
        try { Thread.sleep(100 + (int)(Math.random() * 100)); } catch (InterruptedException ignored) {}
    }
}
