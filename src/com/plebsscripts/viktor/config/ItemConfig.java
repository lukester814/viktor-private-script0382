package com.plebsscripts.viktor.config;

import java.time.Instant;

public class ItemConfig {
    public String itemName;
    public Integer itemId;
    public int estBuy;  // This exists, not estBuyPrice
    public int estSell; // This exists, not estSellPrice
    public double probUp;
    public double liquidity;
    public int horizonMinutes;
    public int maxBuy;
    public int minSell;
    public int maxQtyPerCycle;
    public int probeQty;
    public int minMarginGp;
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

    @Override
    public String toString() {
        return itemName + " (" + estBuy + "/" + estSell + ")";
    }
}
