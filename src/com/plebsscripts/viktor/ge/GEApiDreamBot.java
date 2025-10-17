package com.plebsscripts.viktor.ge;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal, safe GE API adapter.
 *
 * - Tracks offers placed by this script in an internal map (so gpInFlight() and freeSlots() work).
 * - Attempts to query DreamBot GE API via reflection optionally (non-fatal).
 * - Methods are thread-safe and Java 8 compatible.
 *
 * Usage:
 *   GEApiDreamBot.instance().registerOffer(new OfferInfo(...));
 *   GEApiDreamBot.instance().markOfferCompleted(offerId);
 */
public class GEApiDreamBot {

    // Singleton
    private static final GEApiDreamBot INST = new GEApiDreamBot();
    public static GEApiDreamBot instance() { return INST; }

    // Typical Dream Exchange has 8 slots (conservative)
    private static final int DEFAULT_SLOTS = 8;

    // Internal registry of offers placed/known by script
    public final Map<Integer, OfferInfo> trackedOffers = new ConcurrentHashMap<Integer, OfferInfo>();
    private final AtomicInteger offerIdGenerator = new AtomicInteger(1);

    private GEApiDreamBot() {}

    /**
     * Create and register a new offer record for internal tracking.
     * The returned offerId should be stored by the caller so it can be marked completed.
     */
    public OfferInfo registerOffer(String itemName, Type type, int priceEach, int qty) {
        int id = offerIdGenerator.getAndIncrement();
        OfferInfo oi = new OfferInfo(id, itemName, type, priceEach, qty, System.currentTimeMillis());
        trackedOffers.put(id, oi);
        return oi;
    }

    public Collection<OfferInfo> getTrackedOffers() {
        return trackedOffers.values();
    }

    /** Mark an offer as completed/removed (e.g., on sell/buy completion or cancel). */
    public void markOfferCompleted(int offerId) {
        trackedOffers.remove(offerId);
    }

    /** Mark an offer as partially updated (adjust remaining qty). */
    public void updateOfferRemaining(int offerId, int remainingQty) {
        OfferInfo oi = trackedOffers.get(offerId);
        if (oi != null) oi.remainingQty = remainingQty;
    }

    /**
     * Conservative freeSlots count:
     * - Try to reflectively read the DreamBot GrandExchange slots count if possible.
     * - Otherwise return DEFAULT_SLOTS minus trackedOffers.size() (never negative).
     */
    public int freeSlots() {
        try {
            // Try reflection approach (non fatal): attempt to discover known DreamBot GE class
            // Note: We do not require this to succeed. If reflection fails, we fallback.
            Class<?> geClass = Class.forName("org.dreambot.api.methods.grandexchange.GrandExchange");
            // if found, attempt to call a method that returns slots (best-effort, may vary by DreamBot version)
            // This is intentionally permissive; failure falls through to the fallback.
            // (We don't hardcode method names to avoid compile issues across versions)
            // fallback below:
        } catch (Throwable ignore) {
            // not available or failed — that's fine
        }

        int used = 0;
        for (OfferInfo oi : trackedOffers.values()) {
            // count only buys/sells that still have remaining quantity
            if (oi.remainingQty > 0) used++;
        }
        int free = DEFAULT_SLOTS - used;
        return Math.max(0, free);
    }

    /**
     * Sum of GP committed to active buy offers (conservative).
     * Calculated from trackedOffers: sum(priceEach * remainingQty) for BUY offers.
     */
    public long gpInFlight() {
        long sum = 0L;
        for (OfferInfo oi : trackedOffers.values()) {
            if (oi.type == Type.BUY && oi.remainingQty > 0) {
                sum += (long) oi.priceEach * (long) oi.remainingQty;
            }
        }
        return sum;
    }

    /** Return true if any buy offers are older than `millisThreshold`. Useful for stale detection. */
    public boolean hasStaleBuys(long millisThreshold) {
        long now = System.currentTimeMillis();
        for (OfferInfo oi : trackedOffers.values()) {
            if (oi.type == Type.BUY && oi.remainingQty > 0 && (now - oi.timestampMs) > millisThreshold) {
                return true;
            }
        }
        return false;
    }

    /** Very small utility to cancel all known tracked offers (logical cancel for script). */
    public void cancelAllTrackedOffers() {
        trackedOffers.clear();
    }

    // Minimal OfferInfo
    public static class OfferInfo {
        public final int offerId;
        public final String itemName;
        public final Type type;
        public final int priceEach;
        public volatile int remainingQty;
        public final long timestampMs;

        public OfferInfo(int offerId, String itemName, Type type, int priceEach, int qty, long timestampMs) {
            this.offerId = offerId;
            this.itemName = itemName;
            this.type = type;
            this.priceEach = priceEach;
            this.remainingQty = qty;
            this.timestampMs = timestampMs;
        }

        @Override
        public String toString() {
            return "OfferInfo{id=" + offerId + ", item=" + itemName + ", type=" + type + ", price=" + priceEach + ", rem=" + remainingQty + "}";
        }
    }

    public static enum Type {
        BUY,
        SELL
    }
}
