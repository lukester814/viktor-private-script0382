package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.config.CSVConfigLoader;
import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.util.Logs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppGUI {
    private final JFrame frame = new JFrame("Viktor • GE Flipper");

    // Top controls
    private final JTextField csvPath = new JTextField();
    private final JButton btnBrowse = new JButton("Browse…");
    private final JButton btnLoad   = new JButton("Load CSV");
    private final JTextField webhook = new JTextField();
    private final JCheckBox chkTop   = new JCheckBox("Always on top");

    // Budget / limits
    private final JSpinner maxGpPerFlip = new JSpinner(new SpinnerNumberModel(250_000, 1_000, 100_000_000, 1_000));
    private final JCheckBox chkRespectLimits = new JCheckBox("Respect GE limits", true);
    private final JSpinner maxGpInFlight = new JSpinner(new SpinnerNumberModel(15_000_000, 100_000, 2_000_000_000, 50_000));

    // Start/Stop + status
    private final JButton btnStart = new JButton("Start");
    private final JButton btnStop  = new JButton("Stop");
    private final JLabel  lblStatus = new JLabel("Ready.");
    private final JLabel  lblStats  = new JLabel("Profit: 0 gp • 0 gp/h • 0:00");

    // Tables
    private final JTable itemsTable;
    private final ItemTableModel itemsModel;
    private final JTable tasksTable;
    private final TaskTableModel tasksModel = new TaskTableModel();

    // State
    private volatile boolean startRequested = false;
    private volatile boolean stopRequested  = false;
    private File lastCSV = null;

    private Settings settings;

    // Formatting
    private static final DecimalFormat DF = new DecimalFormat("#,###");

    public AppGUI(Settings s, Map<String, Settings> profiles, ItemTableModel model) {
        this.settings = s != null ? s : new Settings();
        this.itemsModel = model != null ? model : new ItemTableModel();
        this.itemsTable = new JTable(this.itemsModel);
        this.tasksTable = new JTable(this.tasksModel);

        initUI();
        preloadFields();
        wireActions();
    }

    private void initUI() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        frame.setContentPane(root);

        // ===== Top: Inputs & limits =====
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: CSV
        c.gridx=0; c.gridy=0; c.weightx=0; top.add(new JLabel("CSV:"), c);
        c.gridx=1; c.gridy=0; c.weightx=1.0; top.add(csvPath, c);
        JPanel csvBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        csvBtns.add(btnBrowse);
        csvBtns.add(btnLoad);
        c.gridx=2; c.gridy=0; c.weightx=0; top.add(csvBtns, c);

        // Row 1: Webhook + AOT + Test button
        c.gridx=0; c.gridy=1; c.weightx=0; top.add(new JLabel("Discord Webhook:"), c);

        JPanel webhookPanel = new JPanel(new BorderLayout(4,0));
        webhookPanel.add(webhook, BorderLayout.CENTER);

        JButton btnTestWebhook = new JButton("Test");
        JLabel webhookStatus = new JLabel("");
        webhookStatus.setForeground(Color.LIGHT_GRAY);

        btnTestWebhook.addActionListener(e -> {
            final String url = webhook.getText().trim();
            if (url.isEmpty()) {
                webhookStatus.setText("✗ No URL");
                webhookStatus.setForeground(Color.RED);
                return;
            }
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        com.plebsscripts.viktor.notify.DiscordNotifier test =
                                new com.plebsscripts.viktor.notify.DiscordNotifier(url);
                        test.info("✅ Viktor webhook test successful!");
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() {
                                webhookStatus.setText("✓ Webhook OK");
                                webhookStatus.setForeground(new Color(0, 180, 0));
                            }
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() {
                                webhookStatus.setText("✗ Failed");
                                webhookStatus.setForeground(Color.RED);
                            }
                        });
                    }
                }
            }).start();
        });

        JPanel webhookRight = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        webhookRight.add(btnTestWebhook);
        webhookRight.add(webhookStatus);

        JPanel webhookContainer = new JPanel(new BorderLayout(6,0));
        webhookContainer.add(webhookPanel, BorderLayout.CENTER);
        webhookContainer.add(webhookRight, BorderLayout.EAST);

        c.gridx=1; c.gridy=1; c.weightx=1.0; top.add(webhookContainer, c);
        c.gridx=2; c.gridy=1; c.weightx=0;   top.add(chkTop, c);

        c.gridx=1; c.gridy=1; c.weightx=1.0;
        top.add(webhookContainer, c);

        c.gridx=2; c.gridy=1; c.weightx=0;
        top.add(chkTop, c);


        // Row 2: Limits
        c.gridx=0; c.gridy=2; c.weightx=0; top.add(new JLabel("Max GP per flip:"), c);
        JPanel limits = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ((JSpinner.NumberEditor) maxGpPerFlip.getEditor()).getTextField().setColumns(10);
        limits.add(maxGpPerFlip);
        limits.add(chkRespectLimits);
        c.gridx=1; c.gridy=2; c.weightx=1.0; top.add(limits, c);

        root.add(top, BorderLayout.NORTH);

        // Add alongside maxGpPerFlip
        JSpinner maxGpInFlight = new JSpinner(new SpinnerNumberModel(15_000_000, 100_000, 2_000_000_000, 50_000));
        ((JSpinner.NumberEditor) maxGpInFlight.getEditor()).getTextField().setColumns(12);
        limits.add(new JLabel("Max GP in flight:"));
        limits.add(maxGpInFlight);


        // ===== Center: Split - Items (left) / Tasks (right) =====
        itemsTable.setFillsViewportHeight(true);
        itemsTable.setAutoCreateRowSorter(true);

        tasksTable.setFillsViewportHeight(true);
        tasksTable.setAutoCreateRowSorter(true);

        JScrollPane left = new JScrollPane(itemsTable);
        left.setBorder(BorderFactory.createTitledBorder("Loaded Items"));

        JScrollPane right = new JScrollPane(tasksTable);
        right.setBorder(BorderFactory.createTitledBorder("Task Queue"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.55);
        root.add(split, BorderLayout.CENTER);

        // ===== Bottom: Controls & status =====
        JPanel bottom = new JPanel(new BorderLayout(8, 8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(btnStart);
        actions.add(btnStop);
        btnStop.setEnabled(false);

        JPanel status = new JPanel(new BorderLayout());
        lblStatus.setForeground(new Color(220, 220, 240));
        lblStats.setForeground(new Color(180, 200, 240));
        status.add(lblStatus, BorderLayout.WEST);
        status.add(lblStats,  BorderLayout.EAST);

        bottom.add(actions, BorderLayout.WEST);
        bottom.add(status,  BorderLayout.CENTER);

        root.add(bottom, BorderLayout.SOUTH);
    }

    private void preloadFields() {
        if (settings != null && settings.inputPath != null) {
            csvPath.setText(settings.inputPath);
            File f = new File(settings.inputPath);
            if (f.exists()) lastCSV = f;
        }
        // Optional: Settings.discordWebhook via reflection (keeps your Settings clean)
        try {
            java.lang.reflect.Field f = settings.getClass().getDeclaredField("discordWebhook");
            f.setAccessible(true);
            Object v = f.get(settings);
            if (v != null) webhook.setText(String.valueOf(v));
        } catch (Throwable ignored) {}

        // Optional: Settings.maxGpPerFlip if present
        try {
            java.lang.reflect.Field f = settings.getClass().getDeclaredField("maxGpPerFlip");
            f.setAccessible(true);
            Object v = f.get(settings);
            if (v instanceof Number) maxGpPerFlip.setValue(((Number) v).longValue());
        } catch (Throwable ignored) {}
    }

    private void wireActions() {
        chkTop.addActionListener(e -> frame.setAlwaysOnTop(chkTop.isSelected()));

        btnBrowse.addActionListener((ActionEvent e) -> {
            JFileChooser fc = new JFileChooser(lastCSV != null ? lastCSV.getParentFile() : new File("."));
            fc.setDialogTitle("Select flip CSV or TXT");
            int res = fc.showOpenDialog(frame);
            if (res == JFileChooser.APPROVE_OPTION) {
                lastCSV = fc.getSelectedFile();
                csvPath.setText(lastCSV.getAbsolutePath());
            }
        });

        btnLoad.addActionListener((ActionEvent e) -> {
            File f = (lastCSV != null) ? lastCSV : new File(csvPath.getText().trim());
            if (f == null || !f.exists()) {
                setStatus("No file selected.");
                return;
            }
            loadCsv(f);
        });

        btnStart.addActionListener(e -> {
            startRequested = true; stopRequested = false;
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            setStatus("Running…");

            // persist settings
            settings.inputPath = csvPath.getText().trim();
            try {
                java.lang.reflect.Field f = settings.getClass().getDeclaredField("discordWebhook");
                f.setAccessible(true);
                f.set(settings, webhook.getText().trim());
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Field f = settings.getClass().getDeclaredField("maxGpPerFlip");
                f.setAccessible(true);
                f.set(settings, ((Number) maxGpPerFlip.getValue()).longValue());
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Field f = settings.getClass().getDeclaredField("respectLimits");
                f.setAccessible(true);
                f.set(settings, chkRespectLimits.isSelected());
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Field f = settings.getClass().getDeclaredField("maxGpInFlight");
                f.setAccessible(true);
                f.set(settings, ((Number) maxGpInFlight.getValue()).intValue());
            } catch (Throwable ignored) {}

        });

        btnStop.addActionListener(e -> {
            stopRequested = true;
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            setStatus("Stopped.");
        });
    }

    private void loadCsv(File f) {
        try {
            List<ItemConfig> list = CSVConfigLoader.load(f.getAbsolutePath());
            itemsModel.setItems(list);
            setStatus("Loaded " + list.size() + " rows");
            // Seed task queue with a default "Queued" state for each item
            tasksModel.setTasksFromItems(list);
        } catch (Exception ex) {
            setStatus("Load failed: " + ex.getMessage());
            Logs.warn("CSV load failed: " + ex.getMessage());
        }
    }

    // ===== Thread-safe UI updaters =====
    public void setLiveStats(final String text) {
        if (text == null) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { lblStats.setText(text); }
        });
    }

    public void setStatus(final String msg) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { lblStatus.setText(msg); }
        });
    }

    // Update a task row by index or by item name
    public void updateTaskStateByIndex(final int idx, final String state, final String notes) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { tasksModel.updateState(idx, state, notes); }
        });
    }
    public void updateTaskStateByItem(final String itemName, final String state, final String notes) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { tasksModel.updateStateByItem(itemName, state, notes); }
        });
    }

    // ===== Expose safe getters for the script runtime =====
    public boolean isStartRequested() { return startRequested; }
    public boolean isStopRequested()  { return stopRequested;  }

    public Long getMaxGpPerFlip() {
        try { return ((Number) maxGpPerFlip.getValue()).longValue(); }
        catch (Exception e) { return 0L; }
    }

    public boolean isRespectLimitsEnabled() { return chkRespectLimits.isSelected(); }

    public String getWebhook() { return webhook.getText().trim(); }

    public File getSelectedCsv() {
        String p = csvPath.getText().trim();
        if (p.isEmpty()) return lastCSV;
        return new File(p);
    }

    public ItemTableModel getItemsModel() { return itemsModel; }
    public TaskTableModel getTasksModel() { return tasksModel; }

    public void open() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { frame.setVisible(true); }
        });
    }

    // Add to AppGUI.java
    private JTextArea coordinatorStatus;

    private JPanel buildCoordinatorTab() {  // Return JPanel, not void
        JPanel panel = new JPanel(new BorderLayout());

        JTextArea coordinatorStatus = new JTextArea();
        coordinatorStatus.setEditable(false);
        coordinatorStatus.setFont(new Font("Monospaced", Font.PLAIN, 12));
        coordinatorStatus.setText("Coordinator not yet implemented");

        JScrollPane scroll = new JScrollPane(coordinatorStatus);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;  // Must return the panel
    }


    private void refreshCoordinatorStatus() {
        coordinatorStatus.setText("Coordinator not yet implemented - coming soon");
        // Commented out until coordinator is built
        // if (coordinatorClient == null) {
        //     coordinatorStatus.setText("Coordinator not connected");
        //     return;
        // }
    }



    // Blocks until Start is pressed or the window closes
    public void waitUntilStart() {
        while (!startRequested) {
            if (!frame.isDisplayable()) break; // user closed window
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        }
    }

    /** Pull current GUI field values into Settings (used by Viktor on start) */
    public void captureSettings() {
        if (settings == null) return;

        // CSV path
        settings.inputPath = csvPath.getText().trim();

        // Discord webhook (kept reflection-friendly)
        try {
            java.lang.reflect.Field f = settings.getClass().getDeclaredField("discordWebhook");
            f.setAccessible(true);
            f.set(settings, webhook.getText().trim());
        } catch (Throwable ignored) {}

        // Respect limits
        try {
            java.lang.reflect.Field f = settings.getClass().getDeclaredField("respectLimits");
            f.setAccessible(true);
            f.set(settings, chkRespectLimits.isSelected());
        } catch (Throwable ignored) {}

        // Max GP per flip (if your Settings has it as long/int)
        try {
            java.lang.reflect.Field f = settings.getClass().getDeclaredField("maxGpPerFlip");
            f.setAccessible(true);
            f.set(settings, ((Number) maxGpPerFlip.getValue()).longValue());
        } catch (Throwable ignored) {}

        // Max GP in flight (spinner you added earlier)
        try {
            java.lang.reflect.Field f = settings.getClass().getDeclaredField("maxGpInFlight");
            f.setAccessible(true);
            f.set(settings, ((Number) /* your spinner */ maxGpInFlight.getValue()).intValue());
        } catch (Throwable ignored) {}
    }

    // ===== Task model =====
    public static class TaskRow {
        public String itemName;
        public int targetQty;
        public String state; // Queued, Probing, Buying, Selling, Cooldown, Done, Error
        public String notes;

        public TaskRow(String itemName, int targetQty, String state, String notes) {
            this.itemName = itemName;
            this.targetQty = targetQty;
            this.state = state;
            this.notes = notes;
        }
    }

    public static class TaskTableModel extends AbstractTableModel {
        private final String[] cols = new String[] { "Item", "Qty", "State", "Notes" };
        private final List<TaskRow> rows = new ArrayList<TaskRow>();

        public void setTasksFromItems(List<ItemConfig> items) {
            rows.clear();
            if (items != null) {
                for (ItemConfig ic : items) {
                    String itemName = null;
                    int qty = 0;

                    // Try getters first
                    try { itemName = (String) ic.getClass().getMethod("getName").invoke(ic); } catch (Throwable ignored) {}
                    try { qty = ((Number) ic.getClass().getMethod("getQuantity").invoke(ic)).intValue(); } catch (Throwable ignored) {}

                    // Fallback to common field names
                    if (itemName == null) {
                        itemName = tryStringField(ic, "name");
                        if (itemName == null) itemName = tryStringField(ic, "itemName");
                        if (itemName == null) itemName = tryStringField(ic, "idName");   // extra fallback if you use ids→names
                    }
                    if (qty <= 0) {
                        qty = tryIntField(ic, "quantity");
                        if (qty <= 0) qty = tryIntField(ic, "qty");
                        if (qty <= 0) qty = tryIntField(ic, "targetQty");
                    }

                    if (itemName == null) itemName = "Unknown Item";
                    if (qty < 0) qty = 0;

                    rows.add(new TaskRow(itemName, qty, "Queued", ""));
                }
            }
            fireTableDataChanged();
        }

        // --- helpers (place inside TaskTableModel class) ---
        private static String tryStringField(Object obj, String field) {
            try {
                java.lang.reflect.Field f = obj.getClass().getDeclaredField(field);
                f.setAccessible(true);
                Object v = f.get(obj);
                return (v != null) ? String.valueOf(v) : null;
            } catch (Throwable ignore) { return null; }
        }
        private static int tryIntField(Object obj, String field) {
            try {
                java.lang.reflect.Field f = obj.getClass().getDeclaredField(field);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Number) return ((Number) v).intValue();
                if (v != null) return Integer.parseInt(String.valueOf(v));
            } catch (Throwable ignore) {}
            return 0;
        }


        public void updateState(int index, String state, String notes) {
            if (index < 0 || index >= rows.size()) return;
            TaskRow r = rows.get(index);
            if (state != null) r.state = state;
            if (notes != null) r.notes = notes;
            fireTableRowsUpdated(index, index);
        }

        public void updateStateByItem(String itemName, String state, String notes) {
            if (itemName == null) return;
            for (int i = 0; i < rows.size(); i++) {
                TaskRow r = rows.get(i);
                if (itemName.equalsIgnoreCase(r.itemName)) {
                    updateState(i, state, notes);
                    break;
                }
            }
        }

        public TaskRow getRow(int idx) { return (idx >= 0 && idx < rows.size()) ? rows.get(idx) : null; }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            TaskRow row = rows.get(r);
            switch (c) {
                case 0: return row.itemName;
                case 1: return row.targetQty;
                case 2: return row.state;
                case 3: return row.notes;
            }
            return "";
        }

        @Override public boolean isCellEditable(int r, int c) { return false; }
    }
}
