package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.coord.JsonCoordinator;
import com.plebsscripts.viktor.coord.SafeCoordinator;
import com.plebsscripts.viktor.ge.*;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.util.Logs;
import com.plebsscripts.viktor.util.WorldDetector;
import com.plebsscripts.viktor.core.SmartRotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class StateMachine {

    // Core runtime phases
    public enum Phase {
        IDLE,
        WALK_TO_GE,
        PROBE,
        BUY_BULK,
        SELL_BULK,
        ROTATE,
        COOLDOWN,
        BANKING
    }

    private Phase phase = Phase.IDLE;
    private final Settings settings;
    private final List<ItemConfig> items;
    private List<ItemConfig> workingQueue;
    private final SafeCoordinator coord;
    private final LimitTracker limits;
    private final GENavigator nav;
    private final GEApi ge;
    private final GEOffers offers;
    private final MarginProbe probe;
    private final PriceModel price;
    private final InventoryBanking bank;
    private final AntiBan antiBan;
    private final Timers timers;
    private final DiscordNotifier notify;
    private final ProfitTracker profit;
    private final HumanBehavior humanBehavior;
    private final SmartRotation smartRotation;
    private ItemConfig current;
    private long lastAction;
    private final Random rng = new Random();

    // Constructor with all dependencies
    public StateMachine(Settings s, List<ItemConfig> it, SafeCoordinator c, LimitTracker lt,
                        GENavigator n, GEOffers o, MarginProbe p, PriceModel pm,
                        InventoryBanking b, AntiBan ab, Timers t, DiscordNotifier dn, ProfitTracker pf, JsonCoordinator jsonCoord) {
        this.settings = s;
        this.items = it;
        this.workingQueue = new ArrayList<ItemConfig>(it);
        this.smartRotation = new SmartRotation(jsonCoord, lt, s.getAccountName());
        this.coord = c;
        this.limits = lt;
        this.nav = n;
        this.ge = GEApiDreamBotAdapter.instance();
        this.offers = o;
        this.probe = p;
        this.price = pm;
        this.bank = b;
        this.antiBan = ab;
        this.timers = t;
        this.notify = dn;
        this.profit = pf;
        this.humanBehavior = new HumanBehavior(ab);
        this.lastAction = System.currentTimeMillis();
    }

    public void start() {
        Logs.info("StateMachine started. Loaded " + items.size() + " items.");
        updateItemQueue();
        if (!workingQueue.isEmpty()) {
            current = workingQueue.get(0);
        }
    }

    public void stop() {
        Logs.info("StateMachine stopped.");

        // ADDED: Reset anti-ban state on stop
        if (antiBan != null) {
            antiBan.reset();
        }
    }

    public void updateItemQueue() {
        if (smartRotation == null) {
            // Fallback: simple filtering
            workingQueue = new ArrayList<ItemConfig>(items);

            if (coord != null) {
                Set<String> localBlocked = coord.getBlockedItems();
                workingQueue.removeIf(ic -> localBlocked.contains(ic.itemName.toLowerCase()));
            }

            Logs.info("Working queue: " + workingQueue.size() + " items available");
            return;
        }

        // Use SmartRotation for intelligent prioritization
        workingQueue = smartRotation.buildPrioritizedQueue(items);

        Logs.info("Smart Queue: " + workingQueue.size() + " items prioritized");
        Logs.info(smartRotation.getTakeoverStats());
    }
    public void updateItems(List<ItemConfig> newItems) {
        synchronized (this.items) {
            this.items.clear();
            this.items.addAll(newItems);
            updateItemQueue();
            Logs.info("StateMachine updated with " + newItems.size() + " items");
        }
    }

    public LimitTracker getLimitTracker() { return limits; }
    public Phase getPhase() { return phase; }
    public ItemConfig getCurrentItem() { return current; }

    /** Main tick loop called from onLoop() */
    public int tick() {
        if (workingQueue == null || workingQueue.isEmpty()) {
            Logs.warn("No items available, idling...");
            updateItemQueue(); // Try to refresh
            timers.sleepShort();
            return 5000;
        }

        switch (phase) {

            case IDLE:
                timers.sleepGaussian(1250, 400);

                // Use SmartRotation for intelligent selection
                if (smartRotation != null) {
                    current = smartRotation.getNextItem(workingQueue);
                } else {
                    // Fallback: random selection
                    if (!workingQueue.isEmpty()) {
                        current = workingQueue.get(rng.nextInt(workingQueue.size()));
                    }
                }

                if (current == null) {
                    Logs.warn("No available items - all blocked or queue empty");
                    updateItemQueue();
                    timers.sleepShort();
                    return 5000;
                }

                Logs.info("Selected item: " + current.itemName);

            case WALK_TO_GE:
                if (nav.walkToGE()) {
                    Logs.info("At GE — checking if probe needed for " + current.itemName);

                    // Check if probe is stale
                    if (WorldDetector.isHighRiskOrPvp()) {
                        Logs.warn("Detected PvP/High Risk world - hopping to safe world...");
                        if (WorldDetector.hopToP2P()) {
                            Logs.info("Successfully hopped to safe world");
                            antiBan.sleep(3000, 5000); // Wait for world change
                        }
                    }
                    if (current.needsProbe(settings.probeStaleMinutes)) {
                        Logs.info("Probe is stale, starting margin check");
                        phase = Phase.PROBE;
                    } else {
                        Logs.info("Probe is fresh, moving to trading");
                        phase = Phase.BUY_BULK;
                    }
                } else {
                    antiBan.idleSleep();
                }
                break;

            case PROBE:
                boolean probeSuccess = probe.ensureFreshMargin(current);

                if (probeSuccess && current.hasGoodMargin()) {
                    Logs.info("✓ Margin verified: " + current.itemName);
                    phase = Phase.BUY_BULK;
                } else {
                    Logs.warn("✗ Margin not profitable: " + current.itemName + ", skipping");
                    phase = Phase.ROTATE;
                }

                lastAction = System.currentTimeMillis();
                break;

            case BUY_BULK:
                Logs.info("Placing buy orders for " + current.itemName);
                GEOffers.Result buyResult = offers.placeBuys(current, price, limits, settings);

                if (buyResult.isOk()) {
                    Logs.info("✓ Buy orders placed, waiting for completion...");

                    // ADD THIS: Wait for offers to complete
                    int maxWaitSeconds = 120; // 2 minutes max
                    int elapsedSeconds = 0;
                    boolean completed = false;

                    while (elapsedSeconds < maxWaitSeconds) {
                        timers.sleepGaussian(5000, 1000); // Check every ~5s

                        if (ge.offersComplete(current.itemName)) {
                            Logs.info("✓ Buy offers completed!");
                            ge.collectIfReady(current.itemName);
                            completed = true;
                            break;
                        }

                        elapsedSeconds += 5;
                        Logs.debug("Waiting for offers... " + elapsedSeconds + "s");
                    }

                    if (!completed) {
                        Logs.warn("Buy offers timed out - collecting what we have");
                        ge.collectIfReady(current.itemName);
                    }

                    // Verify we got items
                    if (!ge.ensureOpen()) {
                        phase = Phase.ROTATE;
                        break;
                    }

                    int itemCount = ge.inventoryCount(current.itemName);
                    ge.close();

                    if (itemCount > 0) {
                        phase = Phase.SELL_BULK;
                    } else {
                        Logs.warn("No items received - skipping sell phase");
                        phase = Phase.ROTATE;
                    }
                }
                break;

            case SELL_BULK:
                Logs.info("Placing sell orders for " + current.itemName);

                // Check if we have items to sell
                if (!ge.ensureOpen()) {
                    Logs.warn("Cannot open GE");
                    phase = Phase.ROTATE;
                    break;
                }

                int itemCount = ge.inventoryCount(current.itemName);
                ge.close();

                if (itemCount > 0) {
                    offers.listSells(current, price, settings);
                    Logs.info("✓ Sell orders placed for " + current.itemName);
                } else {
                    Logs.warn("No items to sell (offers may not have filled)");
                }

                phase = Phase.BANKING;
                lastAction = System.currentTimeMillis();
                break;

            case BANKING:
                // Check if we need to bank collected GP
                if (bank.needsBank()) {
                    Logs.info("Banking items...");

                    if (bank.nearBank()) {
                        bank.bankAll();
                        antiBan.mediumPause();
                    } else {
                        Logs.warn("Not near bank, skipping");
                    }
                }

                phase = Phase.COOLDOWN;
                break;

            case COOLDOWN:
                // IMPROVED: Use Gaussian distribution for cooldown (more human-like)
                timers.sleepGaussian(20_000, 5_000); // Mean 20s, stddev 5s

                // ADDED: 2% chance to simulate distraction (looking away)
                if (humanBehavior != null && rng.nextInt(100) < 2) {
                    humanBehavior.simulateDistraction();
                }

                // Maybe take a break
                if (antiBan.shouldTakeBreak()) {
                    // ADDED: Randomly choose short or medium break
                    if (rng.nextBoolean()) {
                        antiBan.takeShortBreak();
                    } else {
                        antiBan.takeMediumBreak();
                    }
                }

                phase = Phase.ROTATE;
                break;

            case ROTATE:
                // Wait a bit then select new item
                if (System.currentTimeMillis() - lastAction > 5_000) {
                    updateItemQueue(); // Refresh available items
                    phase = Phase.IDLE;
                } else {
                    antiBan.idleSleep();
                }
                break;
        }

        return timers.shortWait();
    }
}