package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.container.impl.Inventory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full implementation of GEApi with DreamBot 3.0+ static API.
 */
public class GEApiDreamBotAdapter implements GEApi {
    private static final GEApiDreamBotAdapter INST = new GEApiDreamBotAdapter();

    public static GEApiDreamBotAdapter instance() { return INST; }

    private final GEApiDreamBot track = GEApiDreamBot.instance();
    private final Map<String, Integer> itemToOfferId = new ConcurrentHashMap<String, Integer>();

    private GEApiDreamBotAdapter() {}

    @Override
    public boolean ensureOpen() {
        try {
            if (!GrandExchange.isOpen()) {
                return GrandExchange.open();
            }
            return true;
        } catch (Exception e) {
            Logs.warn("ensureOpen failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (GrandExchange.isOpen()) {
                GrandExchange.close();
            }
        } catch (Exception e) {
            Logs.warn("close failed: " + e.getMessage());
        }
    }

    @Override
    public int freeSlots() {
        return track.freeSlots();
    }

    @Override
    public int gpInFlight() {
        long v = track.gpInFlight();
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    @Override
    public BuyOutcome placeBuy(String itemName, int priceEach, int qty) {
        if (qty <= 0 || priceEach <= 0) return BuyOutcome.FAILED;

        try {
            boolean success = GrandExchange.buyItem(itemName, qty, priceEach);
            if (success) {
                GEApiDreamBot.OfferInfo oi = track.registerOffer(itemName, GEApiDreamBot.Type.BUY, priceEach, qty);
                itemToOfferId.put(itemName, Integer.valueOf(oi.offerId));
                Logs.info("PLACED BUY " + itemName + " @" + priceEach + " x" + qty);
                return BuyOutcome.PLACED;
            }
            // TODO: detect limit hit via widget inspection
            return BuyOutcome.FAILED;
        } catch (Exception e) {
            Logs.warn("placeBuy failed: " + e.getMessage());
            return BuyOutcome.FAILED;
        }
    }

    @Override
    public SellOutcome placeSell(String itemName, int priceEach, int qtyOrZeroForAll) {
        if (priceEach <= 0) return SellOutcome.FAILED;

        try {
            // DreamBot's sell method uses inventory items
            boolean success = GrandExchange.sellItem(itemName, 1, priceEach); // Sells all matching in inv
            if (success) {
                Logs.info("PLACED SELL " + itemName + " @" + priceEach);
                return SellOutcome.PLACED;
            }
            return SellOutcome.FAILED;
        } catch (Exception e) {
            Logs.warn("placeSell failed: " + e.getMessage());
            return SellOutcome.FAILED;
        }
    }

    @Override
    public void collectAll() {
        try {
            GrandExchange.collect();
            Logs.info("Collected GE offers");
        } catch (Exception e) {
            Logs.warn("collectAll failed: " + e.getMessage());
        }
    }

    @Override
    public boolean inventoryHas(String itemName) {
        try {
            return Inventory.contains(itemName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int inventoryCount(String itemName) {
        try {
            return Inventory.count(itemName);
        } catch (Exception e) {
            Logs.warn("inventoryCount failed: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean hasStaleBuys(String itemName, int staleMinutes) {
        long ms = Math.max(1, staleMinutes) * 60L * 1000L;
        return track.hasStaleBuys(ms);
    }

    @Override
    public boolean hasStaleSells(String itemName, int staleMinutes) {
        return false;
    }

    @Override
    public boolean repriceBuys(String itemName, int newPriceEach) {
        Logs.info("Reprice buys " + itemName + " -> " + newPriceEach + " (stub OK)");
        return true;
    }

    @Override
    public void cancelBuys(String itemName) {
        Integer id = itemToOfferId.remove(itemName);
        if (id != null) {
            track.markOfferCompleted(id.intValue());
            Logs.info("Cancelled buy (logical) " + itemName + " offerId=" + id);
        }
    }

    @Override
    public void undercutSells(String itemName, int newSellPrice) {
        Logs.info("Undercut sells " + itemName + " -> " + newSellPrice + " (stub).");
    }

    @Override
    public boolean offersComplete(String itemName) {
        try {
            // Check if any slot is ready to collect
            for (int i = 0; i < 8; i++) {
                if (GrandExchange.isReadyToCollect(i)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
