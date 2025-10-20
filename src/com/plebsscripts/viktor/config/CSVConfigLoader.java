package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Expects header:
 * timestamp,item_id,item_name,prob_up,est_buy_price,est_sell_price,expected_net_profit,expected_net_margin,current_mid,liquidity_recent_sum,horizon_minutes,auc_test,acc_test
 */
public class CSVConfigLoader {

    public static List<ItemConfig> load(String path) {
        List<ItemConfig> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String header = br.readLine();
            if (header == null) {
                Logs.warn("CSV is empty: " + path);
                return out;
            }

            // IMPROVEMENT 1: Validate header format
            if (!header.toLowerCase().contains("item_name") || !header.toLowerCase().contains("est_buy_price")) {
                Logs.warn("CSV header invalid - expected item_name, est_buy_price columns");
            }

            String line;
            int lineNum = 1; // Start at 1 (header is line 0)

            while ((line = br.readLine()) != null) {
                lineNum++;

                // IMPROVEMENT 2: Skip empty lines
                if (line.trim().isEmpty()) continue;

                String[] p = safeSplit(line);
                if (p.length < 13) {
                    Logs.warn("Line " + lineNum + " has only " + p.length + " columns, skipping");
                    continue;
                }

                try {
                    Integer itemId = tryInt(p[1]);
                    String itemName = p[2].trim();

                    // IMPROVEMENT 3: Validate required fields
                    if (itemName.isEmpty()) {
                        Logs.warn("Line " + lineNum + " has empty item name, skipping");
                        continue;
                    }

                    double probUp = tryDbl(p[3], 0.5);
                    int estBuy = (int)Math.round(tryDbl(p[4], 0));
                    int estSell = (int)Math.round(tryDbl(p[5], 0));

                    // IMPROVEMENT 4: Sanity check prices
                    if (estBuy <= 0 || estSell <= 0) {
                        Logs.warn("Line " + lineNum + " (" + itemName + ") has invalid prices: buy=" + estBuy + " sell=" + estSell);
                        continue;
                    }

                    if (estSell <= estBuy) {
                        Logs.warn("Line " + lineNum + " (" + itemName + ") has negative margin: buy=" + estBuy + " sell=" + estSell);
                        // Still add it - maybe probe will find better prices
                    }

                    double expectedNetProfit = tryDbl(p[6], Math.max(1, estSell - estBuy));
                    double liq = tryDbl(p[9], 0);
                    int horizon = (int)Math.round(tryDbl(p[10], 60));

                    // Derived guardrails
                    int maxBuy = (int)Math.ceil(estBuy * 1.01);
                    int minSell = (int)Math.floor(estSell * 0.99);
                    int maxQty = Math.max(100, Math.min(10_000, (int)Math.round(liq * 0.2)));
                    int probeQty = estBuy > 5000 ? 1 : estBuy > 1000 ? 2 : estBuy > 200 ? 5 : 10;
                    int minMarginGp = Math.max(2, (int)Math.round(expectedNetProfit * 0.5));

                    out.add(new ItemConfig(itemName, itemId, estBuy, estSell, probUp, liq, horizon,
                            maxBuy, minSell, maxQty, probeQty, minMarginGp,
                            null, null, null));

                } catch (Exception e) {
                    Logs.warn("Line " + lineNum + " parse error: " + e.getMessage());
                }
            }

            Logs.info("Loaded " + out.size() + " items from " + path);

            if (out.isEmpty()) {
                Logs.error("CSV loaded but no valid items found!");
                return out;
            }
            // Check for suspicious data
            for (ItemConfig ic : out) {
                if (ic.estBuy > 1_000_000_000 || ic.estSell > 1_000_000_000) {
                    Logs.warn("Suspicious price for " + ic.itemName + " - over 1B gp!");
                }
            }
            // IMPROVEMENT 5: Sort by expected profit (best first)
            out.sort((a, b) -> {
                int profitA = a.estSell - a.estBuy;
                int profitB = b.estSell - b.estBuy;
                return Integer.compare(profitB, profitA); // Descending
            });

        } catch (IOException e) {
            Logs.warn("CSV load failed (" + path + "): " + e.getMessage());
        }
        return out;
    }

    // Naive CSV split that respects simple quoted fields
    private static String[] safeSplit(String line) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQ = !inQ;
                continue;
            }
            if (c == ',' && !inQ) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static double tryDbl(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
