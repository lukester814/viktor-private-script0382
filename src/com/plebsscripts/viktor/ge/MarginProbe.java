package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.Logs;

import java.time.Instant;
import java.util.Random;

/**
 * IMPROVED: Margin probe with account-specific timing and exponential backoff
 */
public class MarginProbe {

    private final Settings settings;
    private final GEApi ge;
    private final DiscordNotifier notify;
    private final ProfitTracker profit;
    private final Random random;
    private final Random accountRandom;

    // Account-specific timing (consistent per account)
    private final int accountBaseWaitMs;

    public MarginProbe(Settings settings, GEApi ge, DiscordNotifier notify, ProfitTracker profit) {
        this.settings = settings;
        this.ge = ge;
        this.notify = notify;
        this.profit = profit;
        this.random = new Random();

        // Generate account-specific seed
        String accountId = settings != null ? settings.getAccountName() : "default";
        long seed = accountId.hashCode();
        this.accountRandom = new Random(seed);

        // Each account has unique base wait time (2-6 seconds)
        this.accountBaseWaitMs = 2000 + accountRandom.nextInt(4000);

        Logs.info("MarginProbe: Account base wait = " + accountBaseWaitMs + "ms");
    }

    /**
     * IMPROVED: Buy/sell probe with exponential backoff and jitter
     */
    public boolean ensureFreshMargin(ItemConfig ic) {
        Logs.info("Starting margin probe: " + ic.itemName);

        if (!ge.ensureOpen()) {
            Logs.warn("GE open failed for probe: " + ic.itemName);
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        int probeQty = Math.max(1, ic.probeQty);
        int buyPrice = ic.maxBuy;
        int sellPrice = ic.minSell;

        Logs.info("Probe buy: " + probeQty + "x " + ic.itemName + " @ " + buyPrice + " gp");

        // === BUY PHASE ===
        GEApi.BuyOutcome buyResult = ge.placeBuy(ic.itemName, buyPrice, probeQty);

        if (buyResult != GEApi.BuyOutcome.PLACED) {
            Logs.warn("Probe buy failed: " + buyResult);
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        // IMPROVED: Exponential backoff with account-specific timing
        if (!waitForOfferWithBackoff(ic.itemName, "buy")) {
            Logs.warn("Probe buy timeout: " + ic.itemName);
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        ge.collectAll();
        sleepWithJitter(800, 1200);

        // Verify items received
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

        // IMPROVED: Exponential backoff for sell
        if (!waitForOfferWithBackoff(ic.itemName, "sell")) {
            Logs.warn("Probe sell timeout: " + ic.itemName);
            ge.close();
            if (notify != null) notify.probeFail(ic);
            return false;
        }

        ge.collectAll();
        sleepWithJitter(800, 1200);

        // Verify sold
        int remainingQty = ge.inventoryCount(ic.itemName);
        if (remainingQty > 0) {
            Logs.warn("Probe sell not filled: " + remainingQty + " remaining");
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

        // Record in profit tracker
        if (profit != null) {
            profit.recordBuy(ic.itemName, receivedQty, buyPrice);
            profit.recordSell(ic.itemName, receivedQty, sellPrice);
        }

        boolean profitable = margin >= ic.minMarginGp;

        if (profitable) {
            Logs.info("✓ Probe OK: " + ic.itemName + " | Margin: " + margin + " gp");
            if (notify != null) {
                notify.probeOk(ic, buyPrice, sellPrice);
            }
        } else {
            Logs.warn("✗ Margin too low: " + ic.itemName + " | " + margin + " < " + ic.minMarginGp + " gp");
            if (notify != null) {
                notify.probeFail(ic);
            }
        }

        return profitable;
    }

    /**
     * IMPROVED: Wait for offer with exponential backoff and jitter
     * Humans check more frequently at first, then less often
     */
    private boolean waitForOfferWithBackoff(String itemName, String type) {
        int maxChecks = 8;
        int checkCount = 0;
        int waitMs = accountBaseWaitMs; // Start with account-specific base

        while (checkCount < maxChecks) {
            // Add jitter (±25%)
            int jitter = (int) (waitMs * 0.25 * (random.nextDouble() * 2 - 1));
            int actualWait = waitMs + jitter;

            Logs.debug("Waiting " + actualWait + "ms for " + type + " (check " + (checkCount + 1) + "/" + maxChecks + ")");
            sleepExact(actualWait);

            // Check if complete
            if (ge.offersComplete(itemName)) {
                Logs.info(type + " completed after " + (checkCount + 1) + " checks");
                return true;
            }

            checkCount++;

            // Exponential backoff: each check waits 1.5x longer
            // But cap at 15 seconds max per check
            waitMs = Math.min(15000, (int) (waitMs * 1.5));
        }

        Logs.warn(type + " did not complete after " + maxChecks + " checks");
        return false;
    }

    /**
     * Check if probe is stale
     */
    public boolean isStale(ItemConfig ic) {
        if (ic.lastProbeAt == null) {
            return true;
        }

        long ageSeconds = java.time.Duration.between(ic.lastProbeAt, Instant.now()).getSeconds();
        long maxAgeSeconds = settings.probeStaleMinutes * 60;

        return ageSeconds > maxAgeSeconds;
    }

    /**
     * Sleep with jitter
     */
    private void sleepWithJitter(int minMs, int maxMs) {
        int duration = minMs + random.nextInt(maxMs - minMs);
        sleepExact(duration);
    }

    /**
     * Exact sleep
     */
    private void sleepExact(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}