package com.plebsscripts.viktor.config;

import java.time.Instant;

public class ItemConfig {
    // Item identification
    public String itemName;
    public Integer itemId;

    // Price estimates from CSV
    public int estBuy;
    public int estSell;

    // CSV metadata
    public double probUp;
    public double liquidity;
    public int horizonMinutes;

    // Derived guardrails
    public int maxBuy;      // Max price to buy at (estBuy * 1.01)
    public int minSell;     // Min price to sell at (estSell * 0.99)
    public int maxQtyPerCycle;  // Max quantity per flip cycle
    public int probeQty;    // How many to buy/sell for margin test
    public int minMarginGp; // Minimum profit required

    // Runtime probe results
    public Integer lastProbeBuy;
    public Integer lastProbeSell;
    public Instant lastProbeAt;

    public ItemConfig(String itemName, Integer itemId, int estBuy, int estSell,
                      double probUp, double liquidity, int horizonMinutes,
                      int maxBuy, int minSell, int maxQtyPerCycle, int probeQty, int minMarginGp,
                      Integer lastProbeBuy, Integer lastProbeSell, Instant lastProbeAt) {
        this.itemName = itemName;
        this.itemId = itemId;
        this.estBuy = estBuy;
        this.estSell = estSell;
        this.probUp = probUp;
        this.liquidity = liquidity;
        this.horizonMinutes = horizonMinutes;
        this.maxBuy = maxBuy;
        this.minSell = minSell;
        this.maxQtyPerCycle = maxQtyPerCycle;
        this.probeQty = probeQty;
        this.minMarginGp = minMarginGp;
        this.lastProbeBuy = lastProbeBuy;
        this.lastProbeSell = lastProbeSell;
        this.lastProbeAt = lastProbeAt;
    }

    // IMPROVEMENT 1: Helper to check if probe is stale
    public boolean needsProbe(int staleMinutes) {
        if (lastProbeAt == null) return true;
        long ageMinutes = (System.currentTimeMillis() - lastProbeAt.toEpochMilli()) / 60000;
        return ageMinutes >= staleMinutes;
    }

    // IMPROVEMENT 2: Get actual margin from last probe
    public int getProbeMargin() {
        if (lastProbeBuy == null || lastProbeSell == null) {
            return estSell - estBuy; // Fallback to CSV estimate
        }
        return lastProbeSell - lastProbeBuy;
    }

    // IMPROVEMENT 3: Check if margin is good enough
    public boolean hasGoodMargin() {
        return getProbeMargin() >= minMarginGp;
    }

    // IMPROVEMENT 4: Update probe results
    public void updateProbe(int buyPrice, int sellPrice) {
        this.lastProbeBuy = buyPrice;
        this.lastProbeSell = sellPrice;
        this.lastProbeAt = Instant.now();
    }

    // IMPROVEMENT 5: Get best buy price (use probe if available)
    public int getBuyPrice() {
        if (lastProbeBuy != null && lastProbeBuy > 0) {
            return Math.min(lastProbeBuy, maxBuy);
        }
        return estBuy;
    }

    // IMPROVEMENT 6: Get best sell price (use probe if available)
    public int getSellPrice() {
        if (lastProbeSell != null && lastProbeSell > 0) {
            return Math.max(lastProbeSell, minSell);
        }
        return estSell;
    }

    // IMPROVEMENT 7: Calculate expected profit
    public int getExpectedProfit(int quantity) {
        int margin = getProbeMargin();
        return margin * quantity;
    }

    // IMPROVEMENT 8: Better toString with margin
    @Override
    public String toString() {
        int margin = getProbeMargin();
        return String.format("%s (Buy: %dgp, Sell: %dgp, Margin: %dgp)",
                itemName, getBuyPrice(), getSellPrice(), margin);
    }

    // IMPROVEMENT 9: Equals/hashCode for collections
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemConfig that = (ItemConfig) o;
        return itemName != null ? itemName.equals(that.itemName) : that.itemName == null;
    }

    @Override
    public int hashCode() {
        return itemName != null ? itemName.hashCode() : 0;
    }
}
