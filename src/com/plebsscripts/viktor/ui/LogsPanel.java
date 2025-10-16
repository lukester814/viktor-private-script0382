package com.plebsscripts.viktor.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Scrollable log viewer panel with color-coded log levels.
 * Can be integrated into AppGUI as a tabbed panel or separate window.
 *
 * Usage:
 *   LogsPanel logsPanel = new LogsPanel();
 *   logsPanel.info("Bot started");
 *   logsPanel.warn("Low GP warning");
 *   logsPanel.error("Failed to open GE");
 */
public class LogsPanel extends JPanel {
    private final JTextPane textPane;
    private final StyledDocument doc;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    // Max lines before auto-clear (prevents memory issues)
    private static final int MAX_LINES = 1000;
    private int lineCount = 0;

    // Color styles
    private Style infoStyle;
    private Style warnStyle;
    private Style errorStyle;
    private Style timestampStyle;

    public LogsPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Logs"));

        // Create text pane with scroll
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textPane.setBackground(new Color(30, 30, 30));

        doc = textPane.getStyledDocument();
        initStyles();

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scroll, BorderLayout.CENTER);

        // Bottom controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton btnClear = new JButton("Clear");
        JButton btnSave = new JButton("Save to File");

        btnClear.addActionListener(e -> clear());
        btnSave.addActionListener(e -> saveToFile());

        controls.add(btnClear);
        controls.add(btnSave);
        add(controls, BorderLayout.SOUTH);
    }

    private void initStyles() {
        // Timestamp style (gray)
        timestampStyle = doc.addStyle("timestamp", null);
        StyleConstants.setForeground(timestampStyle, new Color(150, 150, 150));

        // Info style (white)
        infoStyle = doc.addStyle("info", null);
        StyleConstants.setForeground(infoStyle, new Color(220, 220, 220));

        // Warn style (yellow)
        warnStyle = doc.addStyle("warn", null);
        StyleConstants.setForeground(warnStyle, new Color(255, 200, 0));
        StyleConstants.setBold(warnStyle, true);

        // Error style (red)
        errorStyle = doc.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, new Color(255, 80, 80));
        StyleConstants.setBold(errorStyle, true);
    }

    /**
     * Append log message (thread-safe)
     */
    private void append(String level, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Check line limit
                if (lineCount >= MAX_LINES) {
                    clear();
                }

                // Timestamp
                String time = timeFormat.format(new Date());
                doc.insertString(doc.getLength(), "[" + time + "] ", timestampStyle);

                // Level + message
                Style style = infoStyle;
                if ("WARN".equals(level)) style = warnStyle;
                else if ("ERROR".equals(level)) style = errorStyle;

                doc.insertString(doc.getLength(), "[" + level + "] " + message + "\n", style);
                lineCount++;

                // Auto-scroll to bottom
                textPane.setCaretPosition(doc.getLength());

            } catch (BadLocationException e) {
                // Ignore
            }
        });
    }

    public void info(String message) {
        append("INFO", message);
    }

    public void warn(String message) {
        append("WARN", message);
    }

    public void error(String message) {
        append("ERROR", message);
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            textPane.setText("");
            lineCount = 0;
        });
    }

    private void saveToFile() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("viktor_logs_" + System.currentTimeMillis() + ".txt"));
        int result = fc.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fc.getSelectedFile();
                java.nio.file.Files.write(file.toPath(), textPane.getText().getBytes());
                JOptionPane.showMessageDialog(this, "Logs saved to: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to save logs: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
