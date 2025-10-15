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

@ScriptManifest(
        author = "Plebs Scripts",
        name = "Plebs Private Script",
        description = "CSV-driven GE flipper",
        version = 0.1,
        category = Category.MONEYMAKING
)
public class Viktor extends AbstractScript {
    private Settings settings;
    private StateMachine state;
    private AppGUI gui;
    private com.plebsscripts.viktor.ui.OnPaintOverlay overlay;
    private ProfitTracker profit;

    @Override
    public void onStart() {
        Logs.info("Viktor Private Script starting...");

        File dataDir = new File("data");
        dataDir.mkdirs();

        settings = SettingsStore.loadOrDefault(dataDir);
        List<ItemConfig> items = CSVConfigLoader.load(settings.inputPath);

        // Get adapter and inject THIS script
        GEApiDreamBotAdapter geAdapter = GEApiDreamBotAdapter.instance();
        // geAdapter.setScript(this);  // <-- CRITICAL LINE

        // Rest of your code stays the same...
        ItemTableModel tm = new ItemTableModel();
        tm.setItems(items);
        gui = new AppGUI(settings, Profiles.loadAll(dataDir), tm);
        gui.open();
        gui.waitUntilStart();
        gui.captureSettings();

        LimitTracker limits = LimitStore.loadForAccount(dataDir, settings.getAccountName());
        CoordinatorClient coord = null;
        DiscordNotifier notify = DiscordNotifier.fromSettings(settings);
        profit = new ProfitTracker();

        GENavigator nav = new GENavigator();
        GEOffers offers = new GEOffers(geAdapter, notify);
        MarginProbe probe = new MarginProbe(settings, geAdapter, notify, profit);
        PriceModel price = new PriceModel();
        InventoryBanking bank = new InventoryBanking();

        state = new StateMachine(
                settings, items, coord, limits,
                nav, offers, probe, price, bank,
                new AntiBan(), new Timers(), notify
        );

        overlay = new com.plebsscripts.viktor.ui.OnPaintOverlay(state, profit);
        state.start();
    }


    @Override
    public int onLoop() {
        return state.tick();
    }

    @Override
    public void onExit() {
        Logs.info("Viktor stopping...");
        File dataDir = new File("data");
        LimitStore.saveForAccount(dataDir, settings.getAccountName(), state.getLimitTracker());
        SettingsStore.save(dataDir, settings);
    }

    public void onPaint(Graphics g) {
        if (overlay != null) overlay.paint(g);
    }
}
