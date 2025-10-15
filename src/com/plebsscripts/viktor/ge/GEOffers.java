package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.ge.GEApi.BuyOutcome;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.Logs;

public class GEOffers {
    private final GEApi ge;
    private final DiscordNotifier notify;

    public GEOffers(GEApi ge, DiscordNotifier notify) {
        this.ge = ge;
        this.notify = notify;
    }

    public Result placeBuys(ItemConfig ic, PriceModel pricing, LimitTracker limits, Settings s) {
        if (limits.isBlocked(ic.itemName)) {
            Logs.info("4h blocked locally: " + ic.itemName);
            return Result.hit4h();
        }

        if (!ge.ensureOpen()) {
            Logs.warn("Cannot open GE for buys");
            return Result.fail();
        }

        int targetQty = clampQty(ic.maxQtyPerCycle);
        int placedQty = 0;

        // Use estBuy (not estBuyPrice)
        int buyPrice = ic.estBuy;

        while (placedQty < targetQty) {
            int freeSlots = ge.freeSlots();
            if (freeSlots <= 0) {
                Logs.warn("No free GE slots");
                break;
            }

            int gpInFlight = ge.gpInFlight();
            long availableGp = s.maxGpInFlight - gpInFlight;
            if (availableGp < buyPrice) {
                Logs.warn("GP exposure limit reached");
                break;
            }

            int batch = Math.min(targetQty - placedQty, (int)(availableGp / buyPrice));
            batch = Math.min(batch, 100);

            if (batch <= 0) break;

            BuyOutcome out = ge.placeBuy(ic.itemName, buyPrice, batch);

            // Use LIMIT_HIT (not HIT_4H_LIMIT)
            if (out == BuyOutcome.LIMIT_HIT) {
                limits.blockFor4h(ic.itemName);
                if (notify != null) {
                    Logs.info("Trade limit hit: " + ic.itemName);
                }
                return Result.hit4h();
            }

            if (out == BuyOutcome.PLACED) {
                placedQty += batch;
                if (notify != null) {
                    notify.tradeBuyPlaced(ic, buyPrice, batch);
                }
                sleep(1500, 3000);
            } else {
                Logs.warn("Buy failed: " + out);
                break;
            }
        }

        ge.close();
        return placedQty > 0 ? Result.ok() : Result.fail();
    }

    public void listSells(ItemConfig ic, PriceModel pricing, Settings s) {
        if (!ge.ensureOpen()) {
            Logs.warn("Cannot open GE for sells");
            return;
        }

        // Check inventory - for now assume we have items from completed buys
        if (!ge.inventoryHas(ic.itemName)) {
            Logs.info("No items to sell: " + ic.itemName);
            ge.close();
            return;
        }

        // Use estSell (not estSellPrice)
        int sellPrice = ic.estSell;

        // Place sell offer - use 0 for "all in inventory"
        GEApi.SellOutcome out = ge.placeSell(ic.itemName, sellPrice, 0);

        if (out == GEApi.SellOutcome.PLACED && notify != null) {
            notify.tradeSellPlaced(ic, sellPrice, 0); // 0 = all inventory
        }

        ge.close();
    }

    private int clampQty(int max) {
        return Math.max(1, Math.min(max, 1000));
    }

    private void sleep(int min, int max) {
        try {
            Thread.sleep(min + (int)(Math.random() * (max - min)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class Result {
        private final boolean ok;
        private final boolean hit4h;

        private Result(boolean ok, boolean hit4h) {
            this.ok = ok;
            this.hit4h = hit4h;
        }

        public boolean isOk() { return ok; }
        public boolean hit4hLimit() { return hit4h; }

        public static Result ok() { return new Result(true, false); }
        public static Result fail() { return new Result(false, false); }
        public static Result hit4h() { return new Result(false, true); }
    }
}
