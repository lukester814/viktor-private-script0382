package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.*;
import com.plebsscripts.viktor.coord.CoordinatorClient;
import com.plebsscripts.viktor.coord.SafeCoordinator;
import com.plebsscripts.viktor.ge.*;
import com.plebsscripts.viktor.limits.*;
import com.plebsscripts.viktor.notify.DiscordNotifier;
import com.plebsscripts.viktor.ui.AppGUI;
import com.plebsscripts.viktor.ui.ItemTableModel;
import com.plebsscripts.viktor.util.Logs;
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

    @Override
    public void onStart() {
        Logs.info("Viktor starting...");

        // Setup data directory
        File dataDir = new File("data");
        dataDir.mkdirs();

        // Load settings and CSV
        settings = SettingsStore.loadOrDefault(dataDir);

        // IMPROVED: Load and personalize configs
        List<ItemConfig> rawItems = CSVConfigLoader.load(settings.inputPath);
        List<ItemConfig> items = ConfigPersonalizer.personalizeForAccount(rawItems, settings.getAccountName());
        items = ConfigPersonalizer.filterUnprofitable(items);

        Logs.info("Loaded " + items.size() + " personalized items for " + settings.getAccountName());

        // Load profiles
        Map<String, Settings> profiles = Profiles.loadAll(dataDir);
        Profiles.createDefaultIfNeeded(dataDir);

        // IMPROVED: Use SafeCoordinator instead of network coordinator
        SafeCoordinator coord = new SafeCoordinator(settings.getAccountName());
        Logs.info("Using local coordinator (no network calls)");

        // Get GE API adapter
        GEApiDreamBotAdapter geAdapter = GEApiDreamBotAdapter.instance();

        // Setup GUI
        ItemTableModel tm = new ItemTableModel();
        tm.setItems(items);
        gui = new AppGUI(settings, profiles, tm);
        gui.open();

        Logs.info("Waiting for user to press Start...");
        gui.waitUntilStart();
        gui.captureSettings();

        // Setup hot reloader with improved timing
        hotReloader = new HotReloader(settings.inputPath, new HotReloader.Callback() {
            public void onReload(List<ItemConfig> newItems) {
                Logs.info("CSV reloaded - personalizing " + newItems.size() + " items");

                // Personalize new items
                List<ItemConfig> personalized = ConfigPersonalizer.personalizeForAccount(
                        newItems, settings.getAccountName()
                );
                personalized = ConfigPersonalizer.filterUnprofitable(personalized);

                // Update StateMachine
                if (state != null) {
                    state.updateItems(personalized);
                }

                // Update GUI table
                if (gui != null && tm != null) {
                    tm.setItems(personalized);
                }
            }
        }, 30000);

        hotReloader.start();
        Logs.info("Hot reloader started");

        // Initialize subsystems
        LimitTracker limits = LimitStore.loadForAccount(dataDir, settings.getAccountName());
        DiscordNotifier notify = DiscordNotifier.fromSettings(settings);
        profit = new ProfitTracker();

        GENavigator nav = new GENavigator();
        GEOffers offers = new GEOffers(geAdapter, notify);

        // IMPROVED: Initialize AntiBan with settings
        AntiBan antiBan = new AntiBan(settings);

        // IMPROVED: Initialize HumanBehavior
        HumanBehavior humanBehavior = new HumanBehavior(antiBan);

        // Wire HumanBehavior into GEOffers
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
    }

    @Override
    public int onLoop() {
        // Update GUI with live stats
        if (gui != null && profit != null) {
            gui.setLiveStats(profit.summary());
        }

        // Main state machine tick
        return state.tick();
    }

    @Override
    public void onExit() {
        Logs.info("Viktor stopping...");

        // Stop hot reloader
        if (hotReloader != null) {
            hotReloader.stop();
        }

        // Save state
        File dataDir = new File("data");
        if (state != null) {
            LimitStore.saveForAccount(dataDir, settings.getAccountName(), state.getLimitTracker());
        }
        SettingsStore.save(dataDir, settings);

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
