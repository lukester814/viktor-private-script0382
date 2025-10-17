package com.plebsscripts.viktor.ge;

public interface GEApi {
    boolean ensureOpen();
    void close();

    int freeSlots();
    int gpInFlight();

    enum BuyOutcome { PLACED, LIMIT_HIT, FAILED }
    enum SellOutcome { PLACED, FAILED }

    BuyOutcome placeBuy(String itemName, int priceEach, int qty);
    SellOutcome placeSell(String itemName, int priceEach, int qtyOrZeroForAll);

    void collectAll();
    void collectIfReady(String itemName);
    boolean inventoryHas(String itemName);

    boolean hasStaleBuys(String itemName, int staleMinutes);
    boolean hasStaleSells(String itemName, int staleMinutes);

    boolean repriceBuys(String itemName, int newPriceEach);
    void cancelBuys(String itemName);
    void undercutSells(String itemName, int newSellPrice);

    // Add these for polling completion
    boolean offersComplete(String itemName);
    int inventoryCount(String itemName);
}
