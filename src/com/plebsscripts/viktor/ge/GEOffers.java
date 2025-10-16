package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.ge.GEApi.BuyOutcome;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.Logs;

/**
 * Handles placing buy/sell offers on the GE.
 * Manages GP limits, slot availability, and 4h trade limits.
 */
public class GEOffers {
    private final GEApi ge;
    private final DiscordNotifier notify;
    private ProfitTracker profit; // Optional profit tracking

    public GEOffers(GEApi ge, DiscordNotifier notify) {
        this.ge = ge;
        this.notify = notify;
    }

    // Optional: Set profit tracker for recording trades
    public void setProfitTracker(ProfitTracker profit) {
        this.profit = profit;
    }

    /**
     * Place bulk buy orders for an item.
     * Respects GP limits, slot availability, and 4h limits.
     */
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
        int buyPrice = ic.estBuy;

        while (placedQty < targetQty) {
            // Check slot availability
            int freeSlots = ge.freeSlots();
            if (freeSlots <= 0) {
                Logs.warn("No free GE slots");
                break;
            }

            // Check GP budget
            int gpInFlight = ge.gpInFlight();
            long availableGp = s.maxGpInFlight - gpInFlight;

            if (availableGp < buyPrice) {
                Logs.warn("GP exposure limit reached (" + s.maxGpInFlight + " max)");
                break;
            }

            // Calculate batch size
            int affordableQty = (int)(availableGp / buyPrice);
            int batch = Math.min(targetQty - placedQty, affordableQty);
            batch = Math.min(batch, 100); // GE slot limit

            // Check per-flip limit
            if (s.maxGpPerFlip > 0) {
                int maxQtyPerFlip = (int)(s.maxGpPerFlip / buyPrice);
                batch = Math.min(batch, maxQtyPerFlip);
            }

            if (batch <= 0) {
                Logs.warn("Batch size too small, stopping buys");
                break;
            }

            // Place order
            BuyOutcome out = ge.placeBuy(ic.itemName, buyPrice, batch);

            if (out == BuyOutcome.LIMIT_HIT) {
                limits.blockFor4h(ic.itemName);
                Logs.warn("4h trade limit hit: " + ic.itemName);

                if (notify != null) {
                    long remainingTime = limits.getRemainingBlockTime(ic.itemName);
                    notify.limitHit(ic, remainingTime);
                }

                ge.close();
                return Result.hit4h();
            }

            if (out == BuyOutcome.PLACED) {
                placedQty += batch;

                // Track profit
                if (profit != null) {
                    profit.recordBuy(ic.itemName, batch, buyPrice);
                }

                // Notify Discord
                if (notify != null) {
                    notify.info("Buy placed: " + batch + "x " + ic.itemName + " @ " + buyPrice + " gp");
                }

                Logs.info("Buy placed: " + batch + "x " + ic.itemName + " @ " + buyPrice + " gp");
                sleep(1500, 3000); // Anti-pattern delay

            } else {
                Logs.warn("Buy failed: " + out);
                break;
            }
        }

        ge.close();

        if (placedQty > 0) {
            Logs.info("Total buys placed: " + placedQty + "x " + ic.itemName);
            return Result.ok();
        }

        return Result.fail();
    }

    /**
     * Place sell orders for items in inventory.
     */
    public void listSells(ItemConfig ic, PriceModel pricing, Settings s) {
        if (!ge.ensureOpen()) {
            Logs.warn("Cannot open GE for sells");
            return;
        }

        // Count inventory items
        int invQty = ge.inventoryCount(ic.itemName);
        if (invQty <= 0) {
            Logs.info("No items to sell: " + ic.itemName);
            ge.close();
            return;
        }

        int sellPrice = ic.estSell;
        int totalSold = 0;

        // Sell in batches (100 items per GE slot)
        int remaining = invQty;
        while (remaining > 0 && ge.freeSlots() > 0) {
            int batch = Math.min(remaining, 100);

            GEApi.SellOutcome out = ge.placeSell(ic.itemName, sellPrice, batch);

            if (out == GEApi.SellOutcome.PLACED) {
                remaining -= batch;
                totalSold += batch;

                // Track profit
                if (profit != null) {
                    profit.recordSell(ic.itemName, batch, sellPrice);
                }

                // Notify Discord
                if (notify != null) {
                    notify.info("Sell placed: " + batch + "x " + ic.itemName + " @ " + sellPrice + " gp");
                }

                Logs.info("Sell placed: " + batch + "x " + ic.itemName + " @ " + sellPrice + " gp");
                sleep(1500, 3000); // Anti-pattern delay

            } else {
                Logs.warn("Sell failed: " + out);
                break;
            }
        }

        ge.close();

        if (totalSold > 0) {
            Logs.info("Total sells placed: " + totalSold + "x " + ic.itemName);
        }
    }

    // === Helpers ===

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

    // === Result class ===

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
