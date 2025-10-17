package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CRITICAL: Personalizes item configs per account to prevent detection.
 * Each account gets a unique subset of items with varied parameters.
 */
public class ConfigPersonalizer {

    /**
     * Personalize configs for specific account.
     * Each account gets consistent but unique configuration.
     *
     * @param rawConfigs Original configs from CSV
     * @param accountName Account identifier (username)
     * @return Personalized configs for this account
     */
    public static List<ItemConfig> personalizeForAccount(List<ItemConfig> rawConfigs, String accountName) {
        if (rawConfigs == null || rawConfigs.isEmpty()) {
            return new ArrayList<ItemConfig>();
        }

        // Generate consistent seed from account name
        long seed = accountName.hashCode();
        Random rng = new Random(seed);

        List<ItemConfig> personalized = new ArrayList<ItemConfig>();

        // Each account trades 60-85% of items (random selection)
        double selectionRate = 0.60 + (rng.nextDouble() * 0.25);

        int selectedCount = 0;
        int skippedCount = 0;

        for (ItemConfig original : rawConfigs) {
            // Decide if this account trades this item
            if (rng.nextDouble() > selectionRate) {
                skippedCount++;
                continue;
            }

            // Create personalized copy
            ItemConfig personalized_ic = personalizeItem(original, rng);
            personalized.add(personalized_ic);
            selectedCount++;
        }

        Logs.info("ConfigPersonalizer: Selected " + selectedCount + "/" + rawConfigs.size() +
                " items for account (" + (int)(selectionRate * 100) + "% rate)");

        return personalized;
    }

    /**
     * Apply variance to a single item config
     */
    private static ItemConfig personalizeItem(ItemConfig original, Random rng) {
        // Copy all original values
        String itemName = original.itemName;
        Integer itemId = original.itemId;

        // Add ±3-7% variance to prices (different per account)
        double priceVariance = 0.03 + (rng.nextDouble() * 0.04); // 3-7%

        int estBuy = varyPrice(original.estBuy, priceVariance, rng);
        int estSell = varyPrice(original.estSell, priceVariance, rng);

        // Ensure sell > buy
        if (estSell <= estBuy) {
            estSell = estBuy + Math.max(2, (int)(estBuy * 0.02));
        }

        double probUp = original.probUp;
        double liquidity = original.liquidity;
        int horizonMinutes = original.horizonMinutes;

        // Derived guardrails with variance
        int maxBuy = (int) Math.ceil(estBuy * (1.01 + rng.nextDouble() * 0.02)); // 1.01-1.03x
        int minSell = (int) Math.floor(estSell * (0.98 + rng.nextDouble() * 0.02)); // 0.98-1.00x

        // Vary quantity limits ±20%
        int maxQtyBase = Math.max(100, Math.min(10_000, (int) Math.round(liquidity * 0.2)));
        int maxQty = varyQuantity(maxQtyBase, 0.20, rng);

        // Vary probe quantity ±1-2
        int probeQtyBase = estBuy > 5000 ? 1 : estBuy > 1000 ? 2 : estBuy > 200 ? 5 : 10;
        int probeQty = Math.max(1, probeQtyBase + rng.nextInt(3) - 1); // ±1

        // Vary margin requirement ±15%
        double expectedNetProfit = Math.max(1, estSell - estBuy);
        int minMarginGp = Math.max(2, (int) Math.round(expectedNetProfit * (0.45 + rng.nextDouble() * 0.15))); // 45-60%

        return new ItemConfig(
                itemName, itemId, estBuy, estSell, probUp, liquidity, horizonMinutes,
                maxBuy, minSell, maxQty, probeQty, minMarginGp,
                null, null, null
        );
    }

    /**
     * Vary a price by percentage (±variance)
     */
    private static int varyPrice(int price, double variancePercent, Random rng) {
        if (price <= 0) return price;

        double variance = price * variancePercent;
        int adjustment = (int) ((rng.nextDouble() * 2 - 1) * variance); // ±variance

        int newPrice = price + adjustment;
        return Math.max(1, newPrice); // Min 1gp
    }

    /**
     * Vary a quantity by percentage
     */
    private static int varyQuantity(int qty, double variancePercent, Random rng) {
        if (qty <= 0) return qty;

        double variance = qty * variancePercent;
        int adjustment = (int) ((rng.nextDouble() * 2 - 1) * variance);

        int newQty = qty + adjustment;
        return Math.max(1, newQty);
    }

    /**
     * Filter out unprofitable items after GE tax (1%)
     */
    public static List<ItemConfig> filterUnprofitable(List<ItemConfig> configs) {
        List<ItemConfig> profitable = new ArrayList<ItemConfig>();

        for (ItemConfig ic : configs) {
            int grossProfit = ic.estSell - ic.estBuy;
            int geTax = (int) (ic.estSell * 0.01); // 1% tax on sell
            int netProfit = grossProfit - geTax;

            if (netProfit >= ic.minMarginGp) {
                profitable.add(ic);
            } else {
                Logs.debug("Filtered unprofitable: " + ic.itemName + " (net profit: " + netProfit + " < min: " + ic.minMarginGp + ")");
            }
        }

        Logs.info("Filtered: " + profitable.size() + "/" + configs.size() + " items are profitable after tax");
        return profitable;
    }
}