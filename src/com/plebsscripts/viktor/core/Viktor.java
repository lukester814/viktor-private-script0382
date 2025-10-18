// Viktor.java - Complete revised structure

package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.*;
import com.plebsscripts.viktor.coord.SafeCoordinator;
import com.plebsscripts.viktor.ge.*;
import com.plebsscripts.viktor.limits.*;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.ui.AppGUI;
import com.plebsscripts.viktor.ui.ItemTableModel;
import com.plebsscripts.viktor.util.Logs;
import com.plebsscripts.viktor.util.WorldDetector;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.Category;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;

@ScriptManifest(
        author = "Plebs Scripts",
        name = "Viktor GE Flipper",
        description = "CSV-driven GE flipper with coordinator support",
        version = 1.0,
        category = Category.MONEYMAKING
)
public class Viktor extends AbstractScript {
    private Settings settings;
    private StateMachine state;
    private AppGUI gui;
    private com.plebsscripts.viktor.ui.OnPaintOverlay overlay;
    private ProfitTracker profit;
    private HotReloader hotReloader;
    private File dataDir;

    // Flag to track if we've initialized after Start button
    private boolean initialized = false;

    @Override
    public void onStart() {
        Logs.info("Viktor starting...");

        // Setup data directory
        dataDir = new File("data");
        dataDir.mkdirs();

        // Load settings
        settings = SettingsStore.loadOrDefault(dataDir);

        // Load profiles
        Map<String, Settings> profiles = Profiles.loadAll(dataDir);
        Profiles.createDefaultIfNeeded(dataDir);

        // Create empty table model
        ItemTableModel tm = new ItemTableModel();

        // Create GUI on Swing thread
        final Settings finalSettings = settings;
        final Map<String, Settings> finalProfiles = profiles;

        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new AppGUI(finalSettings, finalProfiles, tm);
            gui.open();
        });

        // Wait for GUI to be created
        Logs.info("Waiting for GUI to initialize...");
        int waitCount = 0;
        while (gui == null && waitCount < 50) {
            try {
                Thread.sleep(100);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logs.error("GUI initialization interrupted");
                return;
            }
        }

        if (gui == null) {
            Logs.error("GUI failed to initialize!");
            return;
        }

        Logs.info("GUI ready! Waiting for user to load CSV and press Start...");
        // DON'T WAIT HERE - let onLoop handle it
    }

    @Override
    public int onLoop() {
        // If not initialized yet, check if user clicked Start
        if (!initialized) {
            if (gui != null && gui.isStartRequested()) {
                Logs.info("Start button pressed! Initializing bot...");
                initializeBot();
                initialized = true;
            }
            return 1000; // Check every second
        }

        // Normal operation
        if (state == null) {
            Logs.warn("State machine failed to initialize!");
            return 1000;
        }

        // Update GUI with live stats
        if (gui != null && profit != null) {
            gui.setLiveStats(profit.summary());
        }

        // Main state machine tick
        return state.tick();
    }

    /**
     * Initialize bot AFTER user clicks Start
     */
    private void initializeBot() {
        try {
            Logs.info("Capturing settings from GUI...");
            gui.captureSettings();

            // Get items from GUI table
            List<ItemConfig> items = gui.getItemsModel().getItems();

            if (items == null || items.isEmpty()) {
                Logs.error("No items loaded! Please load CSV or Pastebin first.");
                return;
            }

            // After initializing bot, check world type
            String worldType = WorldDetector.getWorldType();
            Logs.info("World Type: " + worldType + " (World " + WorldDetector.getCurrentWorldNumber() + ")");

            // Optionally filter items based on world type
            if (WorldDetector.isF2P()) {
                Logs.warn("F2P world detected - some items may not be tradeable!");
                // You could filter out P2P-only items here if needed
            }

            Logs.info("Personalizing " + items.size() + " items...");

            // Personalize items for this account
            items = ConfigPersonalizer.personalizeForAccount(items, settings.getAccountName());
            items = ConfigPersonalizer.filterUnprofitable(items);

            Logs.info("Loaded " + items.size() + " personalized items for " + settings.getAccountName());

            // Update table with personalized items
            gui.getItemsModel().setItems(items);

            // Setup hot reloader (if enabled)
            if (settings.hotReload != null && settings.hotReload.enabled &&
                    settings.hotReload.pastebinUrl != null && !settings.hotReload.pastebinUrl.isEmpty()) {

                HotReloader.PastebinReloader pastebinReloader = new HotReloader.PastebinReloader(
                        settings.hotReload.pastebinUrl,
                        new HotReloader.Callback() {
                            public void onReload(List<ItemConfig> newItems) {
                                Logs.info("Pastebin CSV reloaded - personalizing " + newItems.size() + " items");

                                List<ItemConfig> personalized = ConfigPersonalizer.personalizeForAccount(
                                        newItems, settings.getAccountName()
                                );
                                personalized = ConfigPersonalizer.filterUnprofitable(personalized);

                                if (state != null) {
                                    state.updateItems(personalized);
                                }

                                if (gui != null && gui.getItemsModel() != null) {
                                    gui.getItemsModel().setItems(personalized);
                                }
                            }
                        },
                        settings.hotReload.checkIntervalSeconds * 1000L
                );

                pastebinReloader.start();
                Logs.info("Pastebin hot reloader started");
            }

            // Initialize subsystems
            SafeCoordinator coord = new SafeCoordinator(settings.getAccountName());
            Logs.info("Using local coordinator (no network calls)");

            LimitTracker limits = LimitStore.loadForAccount(dataDir, settings.getAccountName());
            DiscordNotifier notify = DiscordNotifier.fromSettings(settings);
            profit = new ProfitTracker();

            GEApiDreamBotAdapter geAdapter = GEApiDreamBotAdapter.instance();
            GENavigator nav = new GENavigator();
            GEOffers offers = new GEOffers(geAdapter, notify);

            AntiBan antiBan = new AntiBan(settings);
            HumanBehavior humanBehavior = new HumanBehavior(antiBan);

            offers.setHumanBehavior(humanBehavior);
            offers.setAntiBan(antiBan);
            offers.setProfitTracker(profit);

            MarginProbe probe = new MarginProbe(settings, geAdapter, notify, profit);
            PriceModel price = new PriceModel();
            InventoryBanking bank = new InventoryBanking();
            Timers timers = new Timers();

            // Create state machine
            state = new StateMachine(
                    settings, items, coord, limits,
                    nav, offers, probe, price, bank,
                    antiBan, timers, notify, profit
            );

            // Setup overlay
            overlay = new com.plebsscripts.viktor.ui.OnPaintOverlay(state, profit, limits);

            // Start state machine
            state.start();

            Logs.info("Viktor initialized successfully!");
            Logs.info("═══════════════════════════════════════");
            Logs.info("  Items: " + items.size() + " (personalized)");
            Logs.info("  Coordinator: Local (safe mode)");
            Logs.info("  Discord: " + (notify != null ? "Enabled" : "Disabled"));
            Logs.info("  Anti-Ban: Enhanced Gaussian timing");
            Logs.info("  Human Behavior: Mistake simulation enabled");
            Logs.info("═══════════════════════════════════════");

        } catch (Exception e) {
            Logs.error("Initialization failed: " + e.getMessage());
            e.printStackTrace();
        }

        // After "Viktor initialized successfully!" log:

        // Check world type and warn if risky
        String worldInfo = WorldDetector.getWorldInfo();
        Logs.info(worldInfo);

        if (WorldDetector.isHighRiskOrPvp()) {
            Logs.warn("⚠️  HIGH RISK WORLD DETECTED! Consider hopping to safer world.");
            // Optionally auto-hop:
            // WorldDetector.hopToP2P(); // or hopToF2P()
        }

        if (WorldDetector.isF2P()) {
            Logs.warn("F2P world - some items may not be tradeable!");
        }

    }



    @Override
    public void onExit() {
        Logs.info("Viktor stopping...");

        // Stop hot reloader
        if (hotReloader != null) {
            hotReloader.stop();
        }

        // Save state
        if (state != null && dataDir != null) {
            LimitStore.saveForAccount(dataDir, settings.getAccountName(), state.getLimitTracker());
        }

        if (settings != null && dataDir != null) {
            SettingsStore.save(dataDir, settings);
        }

        // Log final stats
        if (profit != null) {
            Logs.info(profit.detailedSummary());
        }

        Logs.info("Viktor stopped. Goodbye!");
    }

    @Override
    public void onPaint(Graphics g) {
        if (overlay != null) {
            overlay.paint(g);
        }
    }
}