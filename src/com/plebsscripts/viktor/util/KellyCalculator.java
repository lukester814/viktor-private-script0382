package com.plebsscripts.viktor.util;

/**
 * Kelly Criterion calculator for optimal GP allocation per flip
 * Helps determine how much to invest in each item based on probability and margin
 */
public class KellyCalculator {

    /**
     * Calculate optimal GP to invest using Kelly Criterion
     *
     * @param bankroll Total available GP
     * @param winProbability Probability of successful flip (0.0 to 1.0)
     * @param buyPrice Price to buy item at
     * @param sellPrice Price to sell item at
     * @return Optimal GP to invest
     */
    public static long calculateOptimalInvestment(
            long bankroll,
            double winProbability,
            int buyPrice,
            int sellPrice) {

        if (bankroll <= 0 || buyPrice <= 0 || sellPrice <= buyPrice) {
            return 0;
        }

        // Kelly formula components
        double p = winProbability; // Win probability
        double q = 1 - p; // Loss probability

        // For GE flipping: b = margin / cost
        int margin = sellPrice - buyPrice;
        double b = (double) margin / buyPrice;

        // Kelly percentage: (bp - q) / b
        double kellyFraction = (b * p - q) / b;

        // Apply fractional Kelly (0.25 = quarter Kelly for safety)
        // Full Kelly can be too aggressive and risky
        double fractionalKelly = kelly * kellyFraction * 0.25;

        // Clamp between 0 and 1
        fractionalKelly = Math.max(0, Math.min(fractionalKelly, 1.0));

        // Calculate investment amount
        long investment = (long) (bankroll * fractionalKelly);

        return investment;
    }


    public static boolean isSafeToTrade(double winProbability, int margin, int buyPrice) {
        // Reject if probUp is unrealistic
        if (winProbability < 0.3 || winProbability > 0.95) {
            return false; // Suspicious probability
        }

        // Reject if margin is too thin
        double marginPct = (double) margin / buyPrice;
        if (marginPct < 0.01) { // Less than 1% margin
            return false; // Too risky
        }

        return true;
    }

    /**
     * Calculate quantity to buy based on Kelly Criterion
     *
     * @param bankroll Total available GP
     * @param winProbability Probability of successful flip (0.0 to 1.0)
     * @param buyPrice Price to buy item at
     * @param sellPrice Price to sell item at
     * @param maxQuantity Maximum quantity allowed (GE limit, liquidity, etc.)
     * @return Optimal quantity to buy
     */
    public static int calculateOptimalQuantity(
            long bankroll,
            double winProbability,
            int buyPrice,
            int sellPrice,
            int maxQuantity,
            double kellyFraction) {

        long optimalInvestment = calculateOptimalInvestment(
                bankroll, winProbability, buyPrice, sellPrice
        );

        // Calculate quantity based on investment
        int quantity = (int) (optimalInvestment / buyPrice);

        // Clamp to max quantity
        quantity = Math.min(quantity, maxQuantity);

        // Minimum 1 item
        return Math.max(1, quantity);
    }

    /**
     * Calculate Kelly percentage (for display/logging)
     *
     * @param winProbability Probability of successful flip
     * @param buyPrice Buy price
     * @param sellPrice Sell price
     * @return Kelly percentage as decimal (0.0 to 1.0)
     */
    public static double calculateKellyPercentage(
            double winProbability,
            int buyPrice,
            int sellPrice) {

        if (buyPrice <= 0 || sellPrice <= buyPrice) {
            return 0.0;
        }

        double p = winProbability;
        double q = 1 - p;

        int margin = sellPrice - buyPrice;
        double b = (double) margin / buyPrice;

        double kelly = (b * p - q) / b;

        // Fractional Kelly (25%)
        return Math.max(0, Math.min(kelly * 0.25, 1.0));
    }

    /**
     * Determine if item is worth flipping based on Kelly Criterion
     *
     * @param winProbability Win probability
     * @param buyPrice Buy price
     * @param sellPrice Sell price
     * @return true if Kelly percentage > 0 (worth flipping)
     */
    public static boolean isWorthFlipping(
            double winProbability,
            int buyPrice,
            int sellPrice) {

        double kelly = calculateKellyPercentage(winProbability, buyPrice, sellPrice);
        return kelly > 0.01; // At least 1% Kelly
    }

    /**
     * Get risk category based on Kelly percentage
     *
     * @param kellyPercentage Kelly percentage (0-1)
     * @return Risk category string
     */
    public static String getRiskCategory(double kellyPercentage) {
        if (kellyPercentage >= 0.20) return "HIGH";
        if (kellyPercentage >= 0.10) return "MEDIUM";
        if (kellyPercentage >= 0.05) return "LOW";
        if (kellyPercentage > 0) return "MINIMAL";
        return "SKIP";
    }
}