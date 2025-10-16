package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.Logs;

import java.time.Instant;

/**
 * Tests item margins by buying small qty at max price and selling at min price.
 * Records observed buy/sell prices and validates margin is profitable.
 */
public class MarginProbe {

    private final Settings settings;
    private final GEApi ge;
    private final DiscordNotifier notify;
    private final ProfitTracker profit;

    public MarginProbe(Settings settings, GEApi ge, DiscordNotifier notify, ProfitTracker profit) {
        this.settings = settings;
        this.ge = ge;
        this.notify = notify;
        this.profit = profit;
    }

    /**
     * Buy small qty @ maxBuy, wait, collect, then sell @ minSell.
     * Records observed prices/timestamp on the ItemConfig.
     * Returns true if margin is profitable.
     */
    public boolean ensureFreshMargin(ItemConfig ic) {
        Logs.info("Starting margin probe: " + ic.itemName);

        if (!ge.ensureOpen()) {
            Logs.warn("GE open failed for probe: " + ic.itemName);
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        int probeQty = Math.max(1, ic.probeQty);
        int buyPrice = ic.maxBuy;  // Buy at max to ensure instant fill
        int sellPrice = ic.minSell; // Sell at min to ensure instant fill

        Logs.info("Probe buy: " + probeQty + "x " + ic.itemName + " @ " + buyPrice + " gp");

        // === BUY PHASE ===
        GEApi.BuyOutcome buyResult = ge.placeBuy(ic.itemName, buyPrice, probeQty);

        if (buyResult != GEApi.BuyOutcome.PLACED) {
            Logs.warn("Probe buy failed: " + buyResult);
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        // Wait for buy to fill
        sleep(3000, 5000); // 3-5 seconds initial wait

        // Check if filled, wait longer if needed
        if (!ge.offersComplete(ic.itemName)) {
            Logs.info("Buy not filled yet, waiting longer...");
            sleep(7000, 10000); // Wait up to 10 more seconds
        }

        ge.collectAll();
        sleep(800, 1200);

        // Verify we got the items
        int receivedQty = ge.inventoryCount(ic.itemName);
        if (receivedQty == 0) {
            Logs.warn("Probe buy not filled: " + ic.itemName);
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        Logs.info("Buy filled: " + receivedQty + "x " + ic.itemName);
        ic.lastProbeBuy = buyPrice;
        ic.lastProbeAt = Instant.now();

        // === SELL PHASE ===
        Logs.info("Probe sell: " + receivedQty + "x " + ic.itemName + " @ " + sellPrice + " gp");

        GEApi.SellOutcome sellResult = ge.placeSell(ic.itemName, sellPrice, receivedQty);

        if (sellResult != GEApi.SellOutcome.PLACED) {
            Logs.warn("Probe sell failed: " + sellResult);
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        // Wait for sell to fill
        sleep(3000, 5000);

        if (!ge.offersComplete(ic.itemName)) {
            Logs.info("Sell not filled yet, waiting longer...");
            sleep(7000, 10000);
        }

        ge.collectAll();
        sleep(800, 1200);

        // Verify items are gone (sold successfully)
        int remainingQty = ge.inventoryCount(ic.itemName);
        if (remainingQty > 0) {
            Logs.warn("Probe sell not filled completely: " + remainingQty + " remaining");
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        Logs.info("Sell filled: " + receivedQty + "x " + ic.itemName);
        ic.lastProbeSell = sellPrice;
        ic.lastProbeAt = Instant.now();

        ge.close();

        // === VALIDATE MARGIN ===
        int margin = sellPrice - buyPrice;
        int totalProfit = margin * receivedQty;

        // Record probe in profit tracker (tiny trade)
        if (profit != null) {
            profit.recordBuy(ic.itemName, receivedQty, buyPrice);
            profit.recordSell(ic.itemName, receivedQty, sellPrice);
        }

        boolean profitable = margin >= ic.minMarginGp;

        if (profitable) {
            Logs.info("✓ Probe OK: " + ic.itemName + " | Buy: " + buyPrice + " → Sell: " + sellPrice + " | Margin: " + margin + " gp");
            if (notify != null) {
                notify.probeOk(ic, buyPrice, sellPrice);
            }
        } else {
            Logs.warn("✗ Margin too low: " + ic.itemName + " | Margin: " + margin + " gp < Min: " + ic.minMarginGp + " gp");
            if (notify != null) {
                notify.probeFail(ic);
            }
        }

        return profitable;
    }

    /**
     * Check if probe is stale (needs refresh)
     */
    public boolean isStale(ItemConfig ic) {
        if (ic.lastProbeAt == null) {
            return true; // Never probed
        }

        long ageSeconds = java.time.Duration.between(ic.lastProbeAt, Instant.now()).getSeconds();
        long maxAgeSeconds = settings.probeStaleMinutes * 60;

        return ageSeconds > maxAgeSeconds;
    }

    // Helper sleep method with randomization
    private void sleep(int minMs, int maxMs) {
        try {
            int duration = minMs + (int)(Math.random() * (maxMs - minMs));
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
