package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.Logs;
import com.plebsscripts.viktor.ge.GEApi;
import com.plebsscripts.viktor.core.ProfitTracker;

import java.time.Instant;

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
     * Buy small @ maxBuy, wait a bit, collect, then sell small @ minSell.
     * Records observed prices/timestamp on the ItemConfig.
     */
    public boolean ensureFreshMargin(ItemConfig ic) {
        if (!ge.ensureOpen()) {
            Logs.warn("GE open failed for probe: " + ic.itemName);
            return false;
        }

        int probeQty = Math.max(1, ic.probeQty);
        int buyPrice = Math.min(ic.maxBuy, ic.estBuy);
        int sellPrice = Math.max(ic.minSell, ic.estSell);

        // BUY probe
        if (ge.placeBuy(ic.itemName, buyPrice, probeQty) != GEApi.BuyOutcome.PLACED) {
            Logs.warn("Probe buy failed for " + ic.itemName);
            return false;
        }
        // Place buy and wait for it to fill
        try {
            Thread.sleep(12000); // Wait 12 seconds for buy to fill
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ge.collectAll();

        // Check if we got the items in inventory
        if (!ge.inventoryHas(ic.itemName)) {
            Logs.warn("Probe buy not filled: " + ic.itemName);
            return false;
        }

        ic.lastProbeBuy = buyPrice;
        ic.lastProbeAt = Instant.now();

        // SELL probe (use minSell guard)
        if (ge.placeSell(ic.itemName, sellPrice, probeQty) != GEApi.SellOutcome.PLACED) {
            Logs.warn("Probe sell failed for " + ic.itemName);
            return false;
        }

        // Wait for sell to complete
        try {
            Thread.sleep(12000); // Wait 12 seconds for sell to fill
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ge.collectAll();

        // Wait for sell to complete
        try {
            Thread.sleep(12000); // Wait 12 seconds for sell to fill
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ge.collectAll();

        // Check if items are gone from inventory (sold successfully)
        if (ge.inventoryHas(ic.itemName)) {
            Logs.warn("Probe sell not filled: " + ic.itemName);
            // Items still in inventory, sell didn't complete
            return false;
        }

        ic.lastProbeSell = sellPrice;
        ic.lastProbeAt = Instant.now();

        int margin = sellPrice - buyPrice;
        Logs.info("Probe OK " + ic.itemName + " buy@" + buyPrice + " sell@" + sellPrice + " Δ" + margin + " gp");
        if (notify != null) notify.probeOk(ic, buyPrice, sellPrice);

        return margin >= ic.minMarginGp;

    }
}
