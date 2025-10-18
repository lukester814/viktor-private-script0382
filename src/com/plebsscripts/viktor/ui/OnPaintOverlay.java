package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.core.StateMachine;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.widget.Widget;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.wrappers.widgets.WidgetChild;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.text.DecimalFormat;

/**
 * Custom Viktor paint anchored to game interface
 * Similar style to Plebs CRABS
 */
public class OnPaintOverlay {
    private final StateMachine state;
    private final ProfitTracker profit;
    private final LimitTracker limits;
    private final long startTime = System.currentTimeMillis();

    private final DecimalFormat df0 = new DecimalFormat("#,##0");

    // Fonts
    private final Font headerFont = new Font("Verdana", Font.BOLD, 27);
    private final Font subHeaderFont = new Font("Verdana", Font.BOLD, 14);
    private final Font dataFont = new Font("Verdana", Font.PLAIN, 12);

    // Colors
    private final Color headerWhite = new Color(255, 255, 255);
    private final Color headerGold = new Color(225, 188, 23);
    private final Color dataColor = Color.BLACK;

    // Background (optional - can load image or draw rectangle)
    private BufferedImage background = null;

    public OnPaintOverlay(StateMachine state, ProfitTracker profit, LimitTracker limits) {
        this.state = state;
        this.profit = profit;
        this.limits = limits;

        // Load custom background image
        loadBackgroundImage();
    }

    /**
     * Load background image from file
     * Tries multiple locations:
     * 1. data/paint_bg.png (recommended)
     * 2. ~/Downloads/paint_bg.png
     * 3. ./paint_bg.png (script folder)
     */
    private void loadBackgroundImage() {
        String[] imagePaths = {
                "data/paint_bg.png",                                    // Recommended location
                System.getProperty("user.home") + "/Downloads/paint_bg.png", // Your Downloads folder
                "paint_bg.png"                                          // Script folder
        };

        for (String path : imagePaths) {
            try {
                java.io.File imgFile = new java.io.File(path);
                if (imgFile.exists()) {
                    background = javax.imageio.ImageIO.read(imgFile);
                    Logs.info("Loaded paint background from: " + path);
                    return;
                }
            } catch (Exception e) {
                // Try next path
            }
        }

        Logs.info("No custom background image found - using solid background");
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Try to anchor to widget (like inventory or chat box)
        int posX = 10; // Default fallback
        int posY = 345; // Default fallback

        try {
            // CORRECT: Use Widgets.getWidget() which returns Widget class
            // Then call getChild() on it to get WidgetChild
            Widget chatWidget = Widgets.getWidget(162);
            if (chatWidget != null) {
                WidgetChild child = chatWidget.getChild(0);
                if (child != null && child.isVisible()) {
                    posX = child.getX();
                    posY = child.getY();
                }
            }
        } catch (Exception e) {
            // Use defaults if widget not found
        }

        // Draw background image (if loaded)
        if (background != null) {
            g2.drawImage(background, posX, posY, null);
        }

        // ALWAYS draw semi-transparent overlay for readability
        g2.setColor(new Color(0, 0, 0, 200)); // Black with 78% opacity
        g2.fillRoundRect(posX, posY, 500, 160, 15, 15);

        // Gold border (Plebs theme)
        g2.setColor(new Color(225, 188, 23, 220));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(posX, posY, 500, 160, 15, 15);

        // Inner shadow for depth
        g2.setColor(new Color(0, 0, 0, 100));
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(posX + 2, posY + 2, 496, 156, 13, 13);

        // === HEADER ===
        g2.setFont(headerFont);
        g2.setColor(headerWhite);
        g2.drawString("Plebs", posX + 5, posY - 5);
        g2.setColor(headerGold);
        g2.drawString("VIKTOR", posX + 80, posY - 5);

        g2.setColor(headerWhite);
        g2.setFont(subHeaderFont);
        g2.drawString("GE Flipper", posX + 260, posY - 4);

        // === DATA SECTION ===
        g2.setFont(dataFont);
        g2.setColor(Color.WHITE); // Changed from BLACK to WHITE for readability

        // Column 1 - General Stats
        int col1X = posX + 15;
        int col1Y = posY + 30;

        g2.drawString("Runtime: " + formatElapsed(System.currentTimeMillis() - startTime), col1X, col1Y);
        g2.drawString("Total Profit: " + formatGp(profit.getRealizedGp()), col1X, col1Y + 25);
        g2.drawString("GP/Hour: " + formatGp(profit.getGpPerHour()), col1X, col1Y + 50);

        // Column 2 - Trade Stats
        int col2X = posX + 200;
        int col2Y = posY + 30;

        g2.drawString("Buys: " + profit.getTotalBuys(), col2X, col2Y);
        g2.drawString("Sells: " + profit.getTotalSells(), col2X, col2Y + 25);
        g2.drawString("Items: " + (state != null ? getItemCount() : "0"), col2X, col2Y + 50);

        // Column 3 - Current Status
        int col3X = posX + 350;
        int col3Y = posY + 30;

        ItemConfig cur = state != null ? state.getCurrentItem() : null;
        String phase = state != null ? formatPhase(state.getPhase()) : "Idle";
        String item = cur != null ? cur.itemName : "—";

        g2.drawString("Phase: " + phase, col3X, col3Y);
        g2.drawString("Item: " + truncate(item, 15), col3X, col3Y + 25);

        if (limits != null && limits.getBlockedCount() > 0) {
            g2.setColor(new Color(255, 150, 0));
            g2.drawString("Blocked: " + limits.getBlockedCount(), col3X, col3Y + 50);
        } else {
            g2.setColor(new Color(0, 200, 0));
            g2.drawString("All clear", col3X, col3Y + 50);
        }

        // Bottom row - Current margin
        g2.setColor(Color.WHITE); // WHITE for readability
        int bottomY = posY + 130;

        if (cur != null) {
            int margin = cur.getSellPrice() - cur.getBuyPrice();
            String marginStr = margin >= 0 ? "+" + df0.format(margin) : df0.format(margin);
            Color marginColor = margin > 0 ? new Color(0, 150, 0) : new Color(200, 0, 0);

            g2.drawString("Current Margin: ", col1X, bottomY);
            g2.setColor(marginColor);
            g2.setFont(new Font("Verdana", Font.BOLD, 13));
            g2.drawString(marginStr + " gp", col1X + 110, bottomY);
        }
    }

    // === Helper Methods ===

    private String formatElapsed(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        minutes %= 60;
        seconds %= 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatGp(long gp) {
        if (gp >= 1_000_000_000) {
            return String.format("%.2fB", gp / 1_000_000_000.0);
        } else if (gp >= 1_000_000) {
            return String.format("%.2fM", gp / 1_000_000.0);
        } else if (gp >= 10_000) {
            return String.format("%.1fK", gp / 1_000.0);
        }
        return df0.format(gp);
    }

    private String formatPhase(Object phase) {
        if (phase == null) return "Idle";
        String p = phase.toString();

        switch (p) {
            case "IDLE": return "Idle";
            case "WALK_TO_GE": return "Walking";
            case "PROBE": return "Probing";
            case "BUY_BULK": return "Buying";
            case "SELL_BULK": return "Selling";
            case "BANKING": return "Banking";
            case "COOLDOWN": return "Cooldown";
            case "ROTATE": return "Rotating";
            default: return p;
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "—";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    private int getItemCount() {
        try {
            return state.getCurrentItem() != null ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}