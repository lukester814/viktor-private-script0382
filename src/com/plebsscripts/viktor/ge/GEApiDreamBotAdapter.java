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
    private final Map<String, Integer> itemToOfferId = new ConcurrentHashMap<>();

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
                itemToOfferId.put(itemName, oi.offerId);
                Logs.info("Buy placed: " + qty + "x " + itemName + " @ " + priceEach + " gp");
                return BuyOutcome.PLACED;
            }

            // Check for limit hit (you can enhance this with widget checks)
            // TODO: Check widgets/chat for "You have reached your limit"

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
            int qty = qtyOrZeroForAll;
            if (qty == 0) {
                qty = Inventory.count(itemName);
            }

            if (qty == 0) {
                Logs.warn("No items to sell: " + itemName);
                return SellOutcome.FAILED;
            }

            boolean success = GrandExchange.sellItem(itemName, qty, priceEach);
            if (success) {
                GEApiDreamBot.OfferInfo oi = track.registerOffer(itemName, GEApiDreamBot.Type.SELL, priceEach, qty);
                itemToOfferId.put(itemName + "_sell", oi.offerId); // Different key for sells
                Logs.info("Sell placed: " + qty + "x " + itemName + " @ " + priceEach + " gp");
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

            // Clear tracked offers since they're collected
            track.cancelAllTrackedOffers();
            itemToOfferId.clear();

            Logs.info("Collected all GE offers");
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
        // Similar logic to hasStaleBuys but for sells
        long threshold = Math.max(1, staleMinutes) * 60L * 1000L;
        long now = System.currentTimeMillis();

        for (GEApiDreamBot.OfferInfo oi : track.trackedOffers.values()) {
            if (oi.type == GEApiDreamBot.Type.SELL &&
                    oi.itemName.equals(itemName) &&
                    oi.remainingQty > 0 &&
                    (now - oi.timestampMs) > threshold) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean repriceBuys(String itemName, int newPriceEach) {
        // Cancel existing buy and place new one
        cancelBuys(itemName);

        try {
            // Small delay before repricing
            Thread.sleep(600);

            int qty = 100; // Or get from previous offer
            BuyOutcome result = placeBuy(itemName, newPriceEach, qty);

            if (result == BuyOutcome.PLACED) {
                Logs.info("Repriced buy: " + itemName + " → " + newPriceEach + " gp");
                return true;
            }
        } catch (Exception e) {
            Logs.warn("Reprice failed: " + e.getMessage());
        }

        return false;
    }

    @Override
    public void cancelBuys(String itemName) {
        Integer id = itemToOfferId.remove(itemName);
        if (id != null) {
            track.markOfferCompleted(id);
            Logs.info("Cancelled buy: " + itemName + " (offerId=" + id + ")");
        }

        // Also cancel in GE UI if needed
        try {
            for (int slot = 0; slot < 8; slot++) {
                if (GrandExchange.slotContains(slot, itemName)) {
                    GrandExchange.cancelOffer(slot);
                }
            }
        } catch (Exception e) {
            Logs.warn("GE cancel failed: " + e.getMessage());
        }
    }

    @Override
    public void undercutSells(String itemName, int newSellPrice) {
        // Cancel and relist
        try {
            for (int slot = 0; slot < 8; slot++) {
                if (GrandExchange.slotContains(slot, itemName)) {
                    GrandExchange.cancelOffer(slot);
                    Thread.sleep(600);

                    int qty = Inventory.count(itemName);
                    if (qty > 0) {
                        placeSell(itemName, newSellPrice, qty);
                        Logs.info("Undercut sell: " + itemName + " → " + newSellPrice + " gp");
                    }
                }
            }
        } catch (Exception e) {
            Logs.warn("Undercut failed: " + e.getMessage());
        }
    }

    @Override
    public boolean offersComplete(String itemName) {
        try {
            // Check if any offer for this item is ready to collect
            for (int slot = 0; slot < 8; slot++) {
                if (GrandExchange.slotContains(slot, itemName) &&
                        GrandExchange.isReadyToCollect(slot)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
