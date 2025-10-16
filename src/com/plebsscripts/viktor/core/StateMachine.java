package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.coord.CoordinatorClient;
import com.plebsscripts.viktor.ge.*;
import com.plebsscripts.viktor.ge.GEApiDreamBotAdapter;
import com.plebsscripts.viktor.ge.GEApi;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.core.AntiBan;
import com.plebsscripts.viktor.util.Logs;
import com.plebsscripts.viktor.core.Timers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class StateMachine {

    // Core runtime phases
    public enum Phase { IDLE, WALK_TO_GE, PROBE, ROTATE }
    // In StateMachine.java Phase enum, add:
    public enum Phase {
        IDLE, ROTATE, PROBE, BUY_BULK, SELL_BULK, COOLDOWN  // Add COOLDOWN
    }


    private Phase phase = Phase.IDLE;
    private final Settings settings;
    private final List<ItemConfig> items;
    private List<ItemConfig> workingQueue;  // Add this for filtered items
    private final CoordinatorClient coord;
    private final LimitTracker limits;
    private final GENavigator nav;
    private final GEApi ge;
    private final MarginProbe probe;
    private final PriceModel price;
    private final InventoryBanking bank;
    private final AntiBan antiBan;
    private final Timers timers;
    private final DiscordNotifier notify;

    private ItemConfig current;
    private long lastAction;
    private final Random rng = new Random();

    public StateMachine(Settings s, List<ItemConfig> it, CoordinatorClient c, LimitTracker lt,
                        GENavigator n, GEOffers o, MarginProbe p, PriceModel pm,
                        InventoryBanking b, AntiBan ab, Timers t, DiscordNotifier dn) {
        this.settings = s;
        this.items = it;
        this.coord = c;
        this.limits = lt;
        this.nav = n;
        this.ge = GEApiDreamBotAdapter.instance();
        this.probe = p;
        this.price = pm;
        this.bank = b;
        this.antiBan = ab;
        this.timers = t;
        this.notify = dn;
        this.lastAction = System.currentTimeMillis();
    }

    public void start() {
        Logs.info("StateMachine started. Loaded " + items.size() + " items.");
        if (!items.isEmpty()) current = items.get(0);
    }

    public void stop() {
        Logs.info("StateMachine stopped.");
    }


    // In updateItemQueue(), use workingQueue instead:
    public void updateItemQueue() {
        if (coord == null) {
            workingQueue = new ArrayList<ItemConfig>(items);
            return;
        }

        Set<String> globalBlocked = coord.getBlockedItems();
        workingQueue = new ArrayList<ItemConfig>();

        for (ItemConfig ic : items) {
            if (!globalBlocked.contains(ic.itemName)) {
                workingQueue.add(ic);
            }
        }
    }

    // Add to StateMachine.java
    public void updateItems(List<ItemConfig> newItems) {
        synchronized (this.items) {
            this.items.clear();
            this.items.addAll(newItems);
            Logs.info("StateMachine updated with " + newItems.size() + " items");
        }
    }


    public LimitTracker getLimitTracker() { return limits; }
    public Phase getPhase() { return phase; }
    public ItemConfig getCurrentItem() { return current; }

    /** Main tick loop called from onLoop() */
    public int tick() {
        if (items == null || items.isEmpty()) {
            Logs.warn("No items loaded, idling...");
            timers.sleepShort();
            return 600;
        }

        switch (phase) {

            case IDLE:
                // Pick next item (round robin)
                current = items.get(rng.nextInt(items.size()));
                Logs.info("Selected item: " + current.itemName);
                phase = Phase.WALK_TO_GE;
                break;

            case WALK_TO_GE:
                if (nav.walkToGE()) {
                    Logs.info("At GE — starting probe for " + current.itemName);
                    phase = Phase.PROBE;
                } else {
                    antiBan.idleSleep();
                }
                break;

            case PROBE:
                boolean ok = probe.ensureFreshMargin(current);
                if (ok) {
                    Logs.info("Margin probe success: " + current.itemName +
                            " [" + current.lastProbeBuy + " → " + current.lastProbeSell + "]");
                    if (notify != null) notify.probeOk(current,
                            current.lastProbeBuy, current.lastProbeSell);
                } else {
                    Logs.warn("Margin probe failed: " + current.itemName);
                    if (notify != null) notify.probeFail(current);
                }
                lastAction = System.currentTimeMillis();
                phase = Phase.ROTATE;
                break;

            case ROTATE:
                // Wait a bit before next item
                if (System.currentTimeMillis() - lastAction > 10_000) {
                    phase = Phase.IDLE;
                } else {
                    antiBan.idleSleep();
                }
                break;
        }

        return timers.shortWait();
    }

    private int handleBanking() {
        if (!bank.needsBank()) {
            Logs.info("Nothing to bank");
            phase = Phase.COOLDOWN;
            return 1000;
        }

        if (!bank.nearBank()) {
            Logs.warn("Not near bank");
            // Walk to bank
            return 3000;
        }

        bank.bankAll();
        antiBan.mediumPause();

        phase = Phase.COOLDOWN;
        return 1000;
    }

}
