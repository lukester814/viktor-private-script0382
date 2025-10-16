package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.*;
import com.plebsscripts.viktor.coord.CoordinatorClient;
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
        List<ItemConfig> items = CSVConfigLoader.load(settings.inputPath);
        Logs.info("Loaded " + items.size() + " items from CSV");

        // Load profiles
        Map<String, Settings> profiles = Profiles.loadAll(dataDir);
        Profiles.createDefaultIfNeeded(dataDir); // Create default if none exist

        // Setup coordinator (if enabled)
        CoordinatorClient coord = settings.enableCoordinator ?
                new CoordinatorClient(settings.coordinatorUrl) : null;

        if (coord != null) {
            Logs.info("Coordinator enabled: " + settings.coordinatorUrl);
        }

        // Get GE API adapter
        GEApiDreamBotAdapter geAdapter = GEApiDreamBotAdapter.instance();

        // Setup GUI
        ItemTableModel tm = new ItemTableModel();
        tm.setItems(items);
        gui = new AppGUI(settings, profiles, tm);
        gui.open();

        Logs.info("Waiting for user to press Start...");
        gui.waitUntilStart();
        gui.captureSettings(); // Capture any GUI changes

        // Setup hot reloader for CSV changes
        hotReloader = new HotReloader(settings.inputPath, newItems -> {
            Logs.info("CSV reloaded - updating " + newItems.size() + " items");

            // Update StateMachine
            if (state != null) {
                state.updateItems(newItems);
            }

            // Update GUI table
            if (gui != null && tm != null) {
                tm.setItems(newItems);
            }
        }, 30000); // Check every 30 seconds

        hotReloader.start();
        Logs.info("Hot reloader started");

        // Initialize subsystems
        LimitTracker limits = LimitStore.loadForAccount(dataDir, settings.getAccountName());
        DiscordNotifier notify = DiscordNotifier.fromSettings(settings);
        profit = new ProfitTracker();

        GENavigator nav = new GENavigator();
        GEOffers offers = new GEOffers(geAdapter, notify);
        MarginProbe probe = new MarginProbe(settings, geAdapter, notify, profit);
        PriceModel price = new PriceModel();
        InventoryBanking bank = new InventoryBanking();

        // FIX: Pass Settings to AntiBan constructor
        AntiBan antiBan = new AntiBan(settings);
        Timers timers = new Timers();

        // FIX: StateMachine needs ProfitTracker as last parameter
        state = new StateMachine(
                settings, items, coord, limits,
                nav, offers, probe, price, bank,
                antiBan, timers, notify, profit  // ADD profit here!
        );

        // Setup overlay
        overlay = new com.plebsscripts.viktor.ui.OnPaintOverlay(state, profit);

        // Start state machine
        state.start();

        Logs.info("Viktor initialized successfully!");
        Logs.info("═══════════════════════════════════════");
        Logs.info("  Items: " + items.size());
        Logs.info("  Coordinator: " + (coord != null ? "Enabled" : "Disabled"));
        Logs.info("  Discord: " + (notify != null ? "Enabled" : "Disabled"));
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
