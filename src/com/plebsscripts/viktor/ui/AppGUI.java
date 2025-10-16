package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.config.CSVConfigLoader;
import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.config.Profiles;
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

/**
 * Main GUI for Viktor GE Flipper
 * Features: Profile management, CSV loading, Discord webhook testing, live task queue
 */
public class AppGUI {
    private final JFrame frame = new JFrame("Viktor • GE Flipper");

    // Profile management
    private final JComboBox<String> profileSelector = new JComboBox<>();
    private final Map<String, Settings> profiles;
    private final File dataDir;

    // Top controls
    private final JTextField csvPath = new JTextField();
    private final JButton btnBrowse = new JButton("Browse…");
    private final JButton btnLoad = new JButton("Load CSV");
    private final JTextField webhook = new JTextField();
    private final JCheckBox chkTop = new JCheckBox("Always on top");

    // Budget / limits
    private final JSpinner maxGpPerFlip = new JSpinner(new SpinnerNumberModel(250_000, 1_000, 100_000_000, 1_000));
    private final JCheckBox chkRespectLimits = new JCheckBox("Respect GE limits", true);
    private final JSpinner maxGpInFlight = new JSpinner(new SpinnerNumberModel(15_000_000, 100_000, 2_000_000_000, 50_000));

    // Start/Stop + status
    private final JButton btnStart = new JButton("Start");
    private final JButton btnStop = new JButton("Stop");
    private final JLabel lblStatus = new JLabel("Ready.");
    private final JLabel lblStats = new JLabel("Profit: 0 gp • 0 gp/h • 0:00");

    // Tables
    private final JTable itemsTable;
    private final ItemTableModel itemsModel;
    private final JTable tasksTable;
    private final TaskTableModel tasksModel = new TaskTableModel();

    // State
    private volatile boolean startRequested = false;
    private volatile boolean stopRequested = false;
    private File lastCSV = null;

    private Settings settings;

    // Formatting
    private static final DecimalFormat DF = new DecimalFormat("#,###");

    public AppGUI(Settings s, Map<String, Settings> profiles, ItemTableModel model) {
        this.settings = s != null ? s : new Settings();
        this.profiles = profiles != null ? profiles : new java.util.HashMap<>();
        this.dataDir = new File("data");
        this.itemsModel = model != null ? model : new ItemTableModel();
        this.itemsTable = new JTable(this.itemsModel);
        this.tasksTable = new JTable(this.tasksModel);

        initUI();
        preloadFields();
        wireActions();
    }

    private void initUI() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        frame.setContentPane(root);

        // ===== Top: Inputs & limits =====
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Profile selector
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        top.add(new JLabel("Profile:"), c);

        // Populate profile dropdown
        profileSelector.addItem("(Current)");
        for (String name : profiles.keySet()) {
            profileSelector.addItem(name);
        }

        JPanel profilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        profilePanel.add(profileSelector);

        JButton btnSaveProfile = new JButton("Save As…");
        JButton btnDeleteProfile = new JButton("Delete");
        profilePanel.add(btnSaveProfile);
        profilePanel.add(btnDeleteProfile);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1.0;
        top.add(profilePanel, c);

        // Row 1: CSV
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        top.add(new JLabel("CSV:"), c);
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1.0;
        top.add(csvPath, c);
        JPanel csvBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        csvBtns.add(btnBrowse);
        csvBtns.add(btnLoad);
        c.gridx = 2;
        c.gridy = 1;
        c.weightx = 0;
        top.add(csvBtns, c);

        // Row 2: Webhook + AOT + Test button
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        top.add(new JLabel("Discord Webhook:"), c);

        JPanel webhookPanel = new JPanel(new BorderLayout(4, 0));
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
            new Thread(() -> {
                try {
                    com.plebsscripts.viktor.notify.DiscordNotifier test =
                            new com.plebsscripts.viktor.notify.DiscordNotifier(url);
                    test.info("✅ Viktor webhook test successful!");
                    SwingUtilities.invokeLater(() -> {
                        webhookStatus.setText("✓ Webhook OK");
                        webhookStatus.setForeground(new Color(0, 180, 0));
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        webhookStatus.setText("✗ Failed");
                        webhookStatus.setForeground(Color.RED);
                    });
                }
            }).start();
        });

        JPanel webhookRight = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        webhookRight.add(btnTestWebhook);
        webhookRight.add(webhookStatus);

        JPanel webhookContainer = new JPanel(new BorderLayout(6, 0));
        webhookContainer.add(webhookPanel, BorderLayout.CENTER);
        webhookContainer.add(webhookRight, BorderLayout.EAST);

        c.gridx = 1;
        c.gridy = 2;
        c.weightx = 1.0;
        top.add(webhookContainer, c);
        c.gridx = 2;
        c.gridy = 2;
        c.weightx = 0;
        top.add(chkTop, c);

        // Row 3: Limits
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;
        top.add(new JLabel("Max GP per flip:"), c);
        JPanel limits = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ((JSpinner.NumberEditor) maxGpPerFlip.getEditor()).getTextField().setColumns(10);
        limits.add(maxGpPerFlip);
        limits.add(chkRespectLimits);

        ((JSpinner.NumberEditor) maxGpInFlight.getEditor()).getTextField().setColumns(12);
        limits.add(new JLabel("Max GP in flight:"));
        limits.add(maxGpInFlight);

        c.gridx = 1;
        c.gridy = 3;
        c.weightx = 1.0;
        top.add(limits, c);

        root.add(top, BorderLayout.NORTH);

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
        status.add(lblStats, BorderLayout.EAST);

        bottom.add(actions, BorderLayout.WEST);
        bottom.add(status, BorderLayout.CENTER);

        root.add(bottom, BorderLayout.SOUTH);

        // Wire profile actions
        profileSelector.addActionListener(e -> {
            String selected = (String) profileSelector.getSelectedItem();
            if (selected != null && !selected.equals("(Current)") && profiles.containsKey(selected)) {
                loadProfile(profiles.get(selected));
            }
        });

        btnSaveProfile.addActionListener(e -> saveProfileDialog());
        btnDeleteProfile.addActionListener(e -> deleteProfileDialog());
    }

    private void preloadFields() {
        if (settings != null && settings.inputPath != null) {
            csvPath.setText(settings.inputPath);
            File f = new File(settings.inputPath);
            if (f.exists()) lastCSV = f;
        }

        // Load discord webhook
        if (settings.discordWebhookUrl != null) {
            webhook.setText(settings.discordWebhookUrl);
        }

        // Load GP limits
        maxGpPerFlip.setValue(settings.maxGpPerFlip);
        maxGpInFlight.setValue((int) settings.maxGpInFlight);
    }

    private void loadProfile(Settings newSettings) {
        if (newSettings == null) return;

        this.settings = newSettings;

        // Update all GUI fields
        csvPath.setText(newSettings.inputPath != null ? newSettings.inputPath : "");
        webhook.setText(newSettings.discordWebhookUrl != null ? newSettings.discordWebhookUrl : "");
        maxGpPerFlip.setValue(newSettings.maxGpPerFlip);
        maxGpInFlight.setValue((int) newSettings.maxGpInFlight);

        setStatus("Loaded profile: " + profileSelector.getSelectedItem());
        Logs.info("Profile loaded: " + profileSelector.getSelectedItem());
    }

    private void saveProfileDialog() {
        String name = JOptionPane.showInputDialog(frame, "Enter profile name:", "Save Profile", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        name = name.trim();

        // Capture current GUI settings
        captureSettings();

        // Save to file
        if (Profiles.save(dataDir, name, settings)) {
            // Add to dropdown if new
            if (!profiles.containsKey(name)) {
                profiles.put(name, settings);
                profileSelector.addItem(name);
            }
            profileSelector.setSelectedItem(name);
            setStatus("Saved profile: " + name);
        } else {
            JOptionPane.showMessageDialog(frame, "Failed to save profile", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteProfileDialog() {
        String selected = (String) profileSelector.getSelectedItem();
        if (selected == null || selected.equals("(Current)")) {
            JOptionPane.showMessageDialog(frame, "Select a profile to delete", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(frame,
                "Delete profile '" + selected + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (Profiles.delete(dataDir, selected)) {
                profiles.remove(selected);
                profileSelector.removeItem(selected);
                profileSelector.setSelectedIndex(0);
                setStatus("Deleted profile: " + selected);
            }
        }
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
            startRequested = true;
            stopRequested = false;
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            setStatus("Running…");
            captureSettings();
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
            tasksModel.setTasksFromItems(list);
        } catch (Exception ex) {
            setStatus("Load failed: " + ex.getMessage());
            Logs.warn("CSV load failed: " + ex.getMessage());
        }
    }

    // ===== Thread-safe UI updaters =====
    public void setLiveStats(final String text) {
        if (text == null) return;
        SwingUtilities.invokeLater(() -> lblStats.setText(text));
    }

    public void setStatus(final String msg) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(msg));
    }

    public void updateTaskStateByIndex(final int idx, final String state, final String notes) {
        SwingUtilities.invokeLater(() -> tasksModel.updateState(idx, state, notes));
    }

    public void updateTaskStateByItem(final String itemName, final String state, final String notes) {
        SwingUtilities.invokeLater(() -> tasksModel.updateStateByItem(itemName, state, notes));
    }

    // ===== Expose safe getters =====
    public boolean isStartRequested() {
        return startRequested;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public Long getMaxGpPerFlip() {
        try {
            return ((Number) maxGpPerFlip.getValue()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    public boolean isRespectLimitsEnabled() {
        return chkRespectLimits.isSelected();
    }

    public String getWebhook() {
        return webhook.getText().trim();
    }

    public File getSelectedCsv() {
        String p = csvPath.getText().trim();
        if (p.isEmpty()) return lastCSV;
        return new File(p);
    }

    public ItemTableModel getItemsModel() {
        return itemsModel;
    }

    public TaskTableModel getTasksModel() {
        return tasksModel;
    }

    public void open() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public void waitUntilStart() {
        while (!startRequested) {
            if (!frame.isDisplayable()) break;
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void captureSettings() {
        if (settings == null) return;

        settings.inputPath = csvPath.getText().trim();
        settings.discordWebhookUrl = webhook.getText().trim();
        settings.maxGpPerFlip = ((Number) maxGpPerFlip.getValue()).longValue();
        settings.maxGpInFlight = ((Number) maxGpInFlight.getValue()).intValue(); // Changed to intValue()
    }


    // ===== Task model =====
    public static class TaskRow {
        public String itemName;
        public int targetQty;
        public String state;
        public String notes;

        public TaskRow(String itemName, int targetQty, String state, String notes) {
            this.itemName = itemName;
            this.targetQty = targetQty;
            this.state = state;
            this.notes = notes;
        }
    }

    public static class TaskTableModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Qty", "State", "Notes"};
        private final List<TaskRow> rows = new ArrayList<>();

        public void setTasksFromItems(List<ItemConfig> items) {
            rows.clear();
            if (items != null) {
                for (ItemConfig ic : items) {
                    rows.add(new TaskRow(ic.itemName, ic.maxQtyPerCycle, "Queued", ""));
                }
            }
            fireTableDataChanged();
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
                if (itemName.equalsIgnoreCase(rows.get(i).itemName)) {
                    updateState(i, state, notes);
                    break;
                }
            }
        }

        public TaskRow getRow(int idx) {
            return (idx >= 0 && idx < rows.size()) ? rows.get(idx) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            TaskRow row = rows.get(r);
            switch (c) {
                case 0:
                    return row.itemName;
                case 1:
                    return row.targetQty;
                case 2:
                    return row.state;
                case 3:
                    return row.notes;
            }
            return "";
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    }
}
