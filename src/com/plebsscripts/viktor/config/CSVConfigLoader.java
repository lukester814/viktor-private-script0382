package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** Expects header:
 * timestamp,item_id,item_name,prob_up,est_buy_price,est_sell_price,expected_net_profit,expected_net_margin,current_mid,liquidity_recent_sum,horizon_minutes,auc_test,acc_test
 */
public class CSVConfigLoader {

    public static List<ItemConfig> load(String path) {
        List<ItemConfig> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String header = br.readLine(); // skip
            if (header == null) return out;
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = safeSplit(line);
                if (p.length < 13) continue;

                Integer itemId = tryInt(p[1]);
                String itemName = p[2].trim();
                double probUp = tryDbl(p[3], 0.5);
                int estBuy = (int)Math.round(tryDbl(p[4], 0));
                int estSell = (int)Math.round(tryDbl(p[5], 0));
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
            }
            Logs.info("Loaded " + out.size() + " items from " + path);
        } catch (Exception e) {
            Logs.warn("CSV load failed (" + path + "): " + e.getMessage());
        }
        return out;
    }

    // naive CSV split that respects simple quoted fields
    private static String[] safeSplit(String line) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQ = !inQ; continue; }
            if (c == ',' && !inQ) { parts.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
    private static Integer tryInt(String s){ try { return Integer.parseInt(s.trim()); } catch(Exception e){ return null; } }
    private static double tryDbl(String s, double def){ try { return Double.parseDouble(s.trim()); } catch(Exception e){ return def; } }
}
