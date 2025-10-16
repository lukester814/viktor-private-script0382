package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.util.Logs;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Tracks profit, trades, and runtime statistics.
 * Provides formatted output for GUI and Discord notifications.
 */
public class ProfitTracker {
    private final long startTime = System.currentTimeMillis();
    private long realizedGp = 0L;
    private long unrealizedGp = 0L; // Items bought but not sold yet
    private int totalBuys = 0;
    private int totalSells = 0;
    private long totalBuyGp = 0L;
    private long totalSellGp = 0L;

    // Per-item tracking
    private final Map<String, ItemStats> itemStats = new HashMap<>();

    // Milestones
    private long lastMilestone = 0L;
    private static final long[] MILESTONES = {100_000, 500_000, 1_000_000, 5_000_000, 10_000_000, 50_000_000, 100_000_000};

    private final DecimalFormat df0 = new DecimalFormat("#,##0");

    // === Core tracking ===

    public void addRealized(long gp) {
        realizedGp += gp;
        checkMilestone();
    }

    public void addUnrealized(long gp) {
        unrealizedGp += gp;
    }

    public void recordBuy(String itemName, int qty, int priceEach) {
        totalBuys += qty;
        totalBuyGp += (long) qty * priceEach;
        unrealizedGp -= (long) qty * priceEach; // Spent GP

        getItemStats(itemName).recordBuy(qty, priceEach);
    }

    public void recordSell(String itemName, int qty, int priceEach) {
        totalSells += qty;
        totalSellGp += (long) qty * priceEach;

        ItemStats stats = getItemStats(itemName);
        stats.recordSell(qty, priceEach);

        // Calculate profit for this sale
        long profit = stats.getProfit();
        addRealized(profit);
    }

    // === Getters ===

    public long getRealizedGp() { return realizedGp; }
    public long getUnrealizedGp() { return unrealizedGp; }
    public long getTotalGp() { return realizedGp + unrealizedGp; }
    public int getTotalBuys() { return totalBuys; }
    public int getTotalSells() { return totalSells; }
    public long getRuntimeMs() { return System.currentTimeMillis() - startTime; }

    public long getGpPerHour() {
        long ms = getRuntimeMs();
        if (ms <= 0) return 0;
        double hours = ms / 3600000.0;
        return (long) Math.floor(realizedGp / hours);
    }

    public double getAvgBuyPrice() {
        return totalBuys > 0 ? (double) totalBuyGp / totalBuys : 0;
    }

    public double getAvgSellPrice() {
        return totalSells > 0 ? (double) totalSellGp / totalSells : 0;
    }

    public double getAvgMarginPercent() {
        if (totalBuyGp == 0) return 0;
        return ((double) (totalSellGp - totalBuyGp) / totalBuyGp) * 100;
    }

    // === Formatting ===

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
                + "B:" + totalBuys + " S:" + totalSells + "  •  " + prettyRuntime();
    }

    public String detailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Profit Summary ═══\n");
        sb.append("Realized:   ").append(df0.format(realizedGp)).append(" gp\n");
        sb.append("Unrealized: ").append(df0.format(unrealizedGp)).append(" gp\n");
        sb.append("Total:      ").append(df0.format(getTotalGp())).append(" gp\n");
        sb.append("GP/Hour:    ").append(df0.format(getGpPerHour())).append(" gp/h\n");
        sb.append("Runtime:    ").append(prettyRuntime()).append("\n");
        sb.append("\n");
        sb.append("Trades:     ").append(totalBuys).append(" buys, ").append(totalSells).append(" sells\n");
        sb.append("Avg Buy:    ").append(df0.format((long) getAvgBuyPrice())).append(" gp\n");
        sb.append("Avg Sell:   ").append(df0.format((long) getAvgSellPrice())).append(" gp\n");
        sb.append("Avg Margin: ").append(String.format("%.2f%%", getAvgMarginPercent())).append("\n");
        return sb.toString();
    }

    // === Per-item stats ===

    private ItemStats getItemStats(String itemName) {
        return itemStats.computeIfAbsent(itemName, k -> new ItemStats());
    }

    public Map<String, ItemStats> getItemStats() {
        return new HashMap<>(itemStats);
    }

    public String getTopItems(int count) {
        List<Map.Entry<String, ItemStats>> sorted = new ArrayList<>(itemStats.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().getProfit(), a.getValue().getProfit()));

        StringBuilder sb = new StringBuilder("Top Items:\n");
        for (int i = 0; i < Math.min(count, sorted.size()); i++) {
            Map.Entry<String, ItemStats> e = sorted.get(i);
            sb.append((i + 1)).append(". ").append(e.getKey())
                    .append(" - ").append(df0.format(e.getValue().getProfit())).append(" gp\n");
        }
        return sb.toString();
    }

    // === Milestones ===

    private void checkMilestone() {
        for (long milestone : MILESTONES) {
            if (realizedGp >= milestone && lastMilestone < milestone) {
                lastMilestone = milestone;
                Logs.info("🎉 Milestone reached: " + df0.format(milestone) + " gp!");
            }
        }
    }

    // === Item-specific stats ===

    public static class ItemStats {
        private int buys = 0;
        private int sells = 0;
        private long totalBuyGp = 0;
        private long totalSellGp = 0;

        public void recordBuy(int qty, int priceEach) {
            buys += qty;
            totalBuyGp += (long) qty * priceEach;
        }

        public void recordSell(int qty, int priceEach) {
            sells += qty;
            totalSellGp += (long) qty * priceEach;
        }

        public long getProfit() {
            return totalSellGp - totalBuyGp;
        }

        public double getAvgBuyPrice() {
            return buys > 0 ? (double) totalBuyGp / buys : 0;
        }

        public double getAvgSellPrice() {
            return sells > 0 ? (double) totalSellGp / sells : 0;
        }

        public int getBuys() { return buys; }
        public int getSells() { return sells; }
        public long getTotalBuyGp() { return totalBuyGp; }
        public long getTotalSellGp() { return totalSellGp; }
    }
}
