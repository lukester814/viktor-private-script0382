package com.plebsscripts.viktor.core;

import java.text.DecimalFormat;

public class ProfitTracker {
    private final long start = System.currentTimeMillis();
    private long realizedGp = 0L;
    private int buys = 0;
    private int sells = 0;

    private final DecimalFormat df0 = new DecimalFormat("#,##0");

    public void addRealized(long gp) {
        realizedGp += gp;
    }

    public void recordBuy(int qty) { buys += qty; }
    public void recordSell(int qty) { sells += qty; }

    public long getRealizedGp() { return realizedGp; }
    public long getRuntimeMs() { return System.currentTimeMillis() - start; }

    public long getGpPerHour() {
        long ms = getRuntimeMs();
        if (ms <= 0) return 0;
        double h = ms / 3600000.0;
        return (long) Math.floor(realizedGp / h);
    }

    public String prettyRuntime() {
        long ms = getRuntimeMs();
        long s = ms / 1000, m = s / 60, h = m / 60;
        m = m % 60; s = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public String summary() {
        return "P: " + df0.format(realizedGp) + " gp  •  " + df0.format(getGpPerHour()) + " gp/h  •  "
                + "buys " + buys + " / sells " + sells;
    }
}
