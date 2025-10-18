package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.core.HumanBehavior;
import com.plebsscripts.viktor.core.AntiBan;
import com.plebsscripts.viktor.ge.GEApi.BuyOutcome;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.KellyCalculator;
import com.plebsscripts.viktor.util.Logs;
import java.util.Random;

public class GEOffers {
    private final GEApi ge;
    private final DiscordNotifier notify;
    private ProfitTracker profit;
    private HumanBehavior humanBehavior; // Add this
    private AntiBan antiBan; // Add this
    private Random random = new Random();

    public GEOffers(GEApi ge, DiscordNotifier notify) {
        this.ge = ge;
        this.notify = notify;
    }

    // Add setters
    public void setProfitTracker(ProfitTracker profit) {
        this.profit = profit;
    }

    public void setHumanBehavior(HumanBehavior humanBehavior) {
        this.humanBehavior = humanBehavior;
    }

    public void setAntiBan(AntiBan antiBan) {
        this.antiBan = antiBan;
    }

    /**
     * IMPROVED: Place bulk buys with retry logic and human mistakes
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

        // Maybe check item details first (15% chance)
        if (humanBehavior != null && humanBehavior.shouldCheckItemFirst()) {
            humanBehavior.checkItemDetails(ic);
        }
// KELLY CRITERION: Calculate optimal quantity
        long bankroll = s.maxGpInFlight;
        double winProb = ic.probUp; // From CSV

        int kellyQty = KellyCalculator.calculateOptimalQuantity(
                bankroll,
                winProb,
                ic.getBuyPrice(),
                ic.getSellPrice(),
                ic.maxQtyPerCycle
        );

        double kellyPct = KellyCalculator.calculateKellyPercentage(
                winProb, ic.getBuyPrice(), ic.getSellPrice()
        );

        String riskCategory = KellyCalculator.getRiskCategory(kellyPct);

        Logs.info("Kelly Analysis: " + kellyQty + " items (" +
                String.format("%.1f%%", kellyPct * 100) + " of bankroll) - " + riskCategory + " risk");

        // Use Kelly quantity instead of fixed
        int targetQty = kellyQty;

        // Apply human behavior adjustments
        if (humanBehavior != null) {
            targetQty = humanBehavior.maybeAdjustQuantity(targetQty);
        }
        int placedQty = 0;
        int buyPrice = ic.getBuyPrice(); // Uses probe data if available


        // Maybe enter wrong price first (1-2% chance)
        if (humanBehavior != null) {
            buyPrice = humanBehavior.maybeWrongPrice(buyPrice, "buy");
        }

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
                Logs.warn("GP exposure limit reached");
                break;
            }

            // Calculate batch size
            int affordableQty = (int)(availableGp / buyPrice);
            int batch = Math.min(targetQty - placedQty, affordableQty);
            batch = Math.min(batch, 100); // GE slot limit

            if (s.maxGpPerFlip > 0) {
                int maxQtyPerFlip = (int)(s.maxGpPerFlip / buyPrice);
                batch = Math.min(batch, maxQtyPerFlip);
            }

            if (batch <= 0) {
                Logs.warn("Batch size too small");
                break;
            }

            // Maybe hesitate before confirming
            if (humanBehavior != null) {
                humanBehavior.maybeHesitate();
            }

            // IMPROVED: Retry logic with backoff
            int maxRetries = 3;
            int retryCount = 0;
            BuyOutcome out = BuyOutcome.FAILED;

            while (retryCount < maxRetries) {
                // Maybe misclick (1% chance)
                if (humanBehavior != null && humanBehavior.shouldMisclick()) {
                    humanBehavior.handleMisclick();
                }

                // Place order
                out = ge.placeBuy(ic.itemName, buyPrice, batch);

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

                    if (profit != null) {
                        profit.recordBuy(ic.itemName, batch, buyPrice);
                    }

                    if (notify != null) {
                        notify.info("Buy placed: " + batch + "x " + ic.itemName + " @ " + buyPrice + " gp");
                    }

                    Logs.info("Buy placed: " + batch + "x " + ic.itemName + " @ " + buyPrice + " gp");

                    // Anti-pattern delay with variance
                    sleep(1500, 3000);
                    break; // Success!

                } else {
                    // Failed - retry logic
                    retryCount++;

                    if (retryCount < maxRetries) {
                        Logs.warn("Buy failed (" + out + "), retrying " + retryCount + "/" + maxRetries);

                        // Human-like retry delay (increases with each retry)
                        int retryDelay = 1000 * retryCount + random.nextInt(2000);
                        sleep(retryDelay, retryDelay + 1000);

                        // Maybe adjust price slightly on retry (humans do this)
                        if (retryCount > 1 && random.nextInt(100) < 30) {
                            int adjustment = (int)(buyPrice * 0.01); // +1%
                            buyPrice += adjustment;
                            Logs.info("Adjusting buy price to " + buyPrice + " gp");
                        }
                    } else {
                        Logs.warn("Buy failed after " + maxRetries + " attempts: " + out);
                    }
                }
            }

            // If all retries failed, break
            if (out != BuyOutcome.PLACED) {
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
     * IMPROVED: Sell with retry logic
     */
    public void listSells(ItemConfig ic, PriceModel pricing, Settings s) {
        if (!ge.ensureOpen()) {
            Logs.warn("Cannot open GE for sells");
            return;
        }

        int invQty = ge.inventoryCount(ic.itemName);
        if (invQty <= 0) {
            Logs.info("No items to sell: " + ic.itemName);
            ge.close();
            return;
        }

        int sellPrice = ic.getSellPrice();

        // Maybe wrong price (human mistake)
        if (humanBehavior != null) {
            sellPrice = humanBehavior.maybeWrongPrice(sellPrice, "sell");
        }

        int totalSold = 0;
        int remaining = invQty;

        while (remaining > 0 && ge.freeSlots() > 0) {
            int batch = Math.min(remaining, 100);

            // Maybe hesitate
            if (humanBehavior != null) {
                humanBehavior.maybeHesitate();
            }

            // Retry logic
            int maxRetries = 3;
            int retryCount = 0;
            GEApi.SellOutcome out = GEApi.SellOutcome.FAILED;

            while (retryCount < maxRetries) {
                out = ge.placeSell(ic.itemName, sellPrice, batch);

                if (out == GEApi.SellOutcome.PLACED) {
                    remaining -= batch;
                    totalSold += batch;

                    if (profit != null) {
                        profit.recordSell(ic.itemName, batch, sellPrice);
                    }

                    if (notify != null) {
                        notify.info("Sell placed: " + batch + "x " + ic.itemName + " @ " + sellPrice + " gp");
                    }

                    Logs.info("Sell placed: " + batch + "x " + ic.itemName + " @ " + sellPrice + " gp");
                    sleep(1500, 3000);
                    break;

                } else {
                    retryCount++;

                    if (retryCount < maxRetries) {
                        Logs.warn("Sell failed, retrying " + retryCount + "/" + maxRetries);
                        int retryDelay = 1000 * retryCount + random.nextInt(2000);
                        sleep(retryDelay, retryDelay + 1000);

                        // Adjust price on retry
                        if (retryCount > 1 && random.nextInt(100) < 30) {
                            sellPrice -= (int)(sellPrice * 0.01); // -1% (undercut)
                            Logs.info("Adjusting sell price to " + sellPrice + " gp");
                        }
                    } else {
                        Logs.warn("Sell failed after " + maxRetries + " attempts");
                    }
                }
            }

            if (out != GEApi.SellOutcome.PLACED) {
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
            Thread.sleep(min + random.nextInt(max - min));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Result class unchanged...
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