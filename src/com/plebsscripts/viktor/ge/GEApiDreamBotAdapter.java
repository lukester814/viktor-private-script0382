package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.grandexchange.GrandExchangeItem;
import org.dreambot.api.methods.container.impl.Inventory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full implementation of GEApi with DreamBot 3.0+ static API.
 */
public class GEApiDreamBotAdapter implements GEApi {
    private static final GEApiDreamBotAdapter INST = new GEApiDreamBotAdapter();

    public static GEApiDreamBotAdapter instance() {
        return INST;
    }

    private final GEApiDreamBot track = GEApiDreamBot.instance();
    private final Map<String, Integer> itemToOfferId = new ConcurrentHashMap<String, Integer>();

    private GEApiDreamBotAdapter() {
    }

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

            // Check for limit hit (enhance with widget checks if needed)
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
                itemToOfferId.put(itemName + "_sell", oi.offerId);
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
        // Use getTrackedOffers() to avoid accessing private field
        long threshold = Math.max(1, staleMinutes) * 60L * 1000L;
        long now = System.currentTimeMillis();

        for (GEApiDreamBot.OfferInfo oi : track.getTrackedOffers()) {
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
        cancelBuys(itemName);

        try {
            Thread.sleep(600);
            int qty = 100;
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

        // Cancel in GE UI - DreamBot uses GrandExchangeItem
        try {
            for (int slot = 0; slot < 8; slot++) {
                GrandExchangeItem geItem = GrandExchange.getItem(slot);
                if (geItem != null && geItem.getItem() != null &&
                        geItem.getItem().getName().equals(itemName)) {
                    GrandExchange.cancelOffer(slot);
                }
            }
        } catch (Exception e) {
            Logs.warn("GE cancel failed: " + e.getMessage());
        }
    }

    @Override
    public void undercutSells(String itemName, int newSellPrice) {
        try {
            for (int slot = 0; slot < 8; slot++) {
                GrandExchangeItem geItem = GrandExchange.getItem(slot);
                if (geItem != null && geItem.getItem() != null &&
                        geItem.getItem().getName().equals(itemName)) {
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

    /// In GEApiDreamBotAdapter.java, replace offersComplete() method:

    @Override
    public boolean offersComplete(String itemName) {
        try {
            boolean anyReady = false;

            for (int slot = 0; slot < 8; slot++) {
                GrandExchangeItem geItem = GrandExchange.getItem(slot);

                if (geItem == null) continue;

                // Check if this slot has our item
                if (geItem.getItem() != null &&
                        geItem.getItem().getName().equals(itemName)) {

                    // Check if ready to collect (green checkmark)
                    if (GrandExchange.isReadyToCollect(slot)) {
                        Logs.info("Offer slot " + slot + " ready to collect: " + itemName);
                        anyReady = true;
                    } else {
                        Logs.debug("Offer slot " + slot + " still pending: " + itemName);
                    }
                }
            }

            return anyReady;

        } catch (Exception e) {
            Logs.warn("offersComplete() error: " + e.getMessage());
            return false;
        }
    }

    // Add this new method to force collection check:
    public void collectIfReady(String itemName) {
        try {
            if (!GrandExchange.isOpen()) {
                GrandExchange.open();
                org.dreambot.api.utilities.Sleep.sleepUntil(() -> GrandExchange.isOpen(), 3000);
            }

            boolean collected = false;

            for (int slot = 0; slot < 8; slot++) {
                if (GrandExchange.isReadyToCollect(slot)) {
                    GrandExchangeItem geItem = GrandExchange.getItem(slot);

                    if (geItem != null && geItem.getItem() != null) {
                        String item = geItem.getItem().getName();

                        if (item.equals(itemName) || itemName == null) {
                            Logs.info("Collecting from slot " + slot + ": " + item);

                            // FIXED: Use collectAll() instead of collect(slot)
                            GrandExchange.collect();
                            org.dreambot.api.utilities.Sleep.sleep(600, 1000);
                            collected = true;
                            break; // Collect one at a time to avoid issues
                        }
                    }
                }
            }

            if (!collected) {
                Logs.debug("No ready offers found for: " + itemName);
            }

        } catch (Exception e) {
            Logs.warn("collectIfReady() error: " + e.getMessage());
        }
    }
}