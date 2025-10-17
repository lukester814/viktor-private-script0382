package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.core.StateMachine;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.limits.LimitTracker;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.text.DecimalFormat;

/**
 * Ultra-modern overlay with gradient header, icons, and glassmorphism
 * Optimized for readability and visual appeal
 */
public class OnPaintOverlay {
    private final StateMachine state;
    private final ProfitTracker profit;
    private final LimitTracker limits;

    private final DecimalFormat df0 = new DecimalFormat("#,##0");

    // Layout constants
    private int x = 12, y = 60, w = 380;
    private int line = 20, pad = 12;
    private static final int RADIUS = 16;

    // Modern color palette (Discord-inspired with enhancements)
    private static final Color BG_DARK = new Color(20, 22, 35, 220);
    private static final Color HEADER_START = new Color(88, 101, 242, 230);
    private static final Color HEADER_END = new Color(128, 90, 213, 230);
    private static final Color TEXT_LIGHT = new Color(250, 252, 255);
    private static final Color TEXT_MUTED = new Color(180, 188, 210);
    private static final Color SEPARATOR = new Color(255, 255, 255, 22);
    private static final Color GREEN = new Color(76, 191, 140);
    private static final Color RED = new Color(237, 85, 101);
    private static final Color YELLOW = new Color(255, 188, 66);
    private static final Color BLUE = new Color(103, 158, 255);

    // Fonts (with fallback chain)
    private Font headerFont;
    private Font bodyFont;
    private Font boldFont;

    public OnPaintOverlay(StateMachine state, ProfitTracker profit, LimitTracker limits) {
        this.state = state;
        this.profit = profit;
        this.limits = limits;
        initFonts();
    }

    private void initFonts() {
        // Try Inter → Segoe UI → System default
        String[] fontNames = {"Inter", "Segoe UI", "Arial", "Sans-serif"};
        String availableFont = "Dialog";

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] systemFonts = ge.getAvailableFontFamilyNames();

        for (String fontName : fontNames) {
            for (String systemFont : systemFonts) {
                if (systemFont.equalsIgnoreCase(fontName)) {
                    availableFont = fontName;
                    break;
                }
            }
            if (!availableFont.equals("Dialog")) break;
        }

        headerFont = new Font(availableFont, Font.BOLD, 14);
        bodyFont = new Font(availableFont, Font.PLAIN, 12);
        boldFont = new Font(availableFont, Font.BOLD, 12);
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        // Ultra-smooth rendering
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Get current data
        ItemConfig cur = state.getCurrentItem();
        String phaseVal = formatPhase(state.getPhase());
        String itemVal = (cur == null ? "—" : cur.itemName);

        int buy = cur == null ? 0 : cur.getBuyPrice();
        int sell = cur == null ? 0 : cur.getSellPrice();
        int margin = sell - buy; // Can be negative

        String priceVal = (buy == 0 && sell == 0) ? "—" :
                (df0.format(buy) + " → " + df0.format(sell));
        String marginVal;
        if (margin > 0) {
            marginVal = "+" + df0.format(margin) + " gp";
        } else if (margin < 0) {
            marginVal = df0.format(margin) + " gp"; // Negative sign included
        } else {
            marginVal = "—";
        }

        Instant probeAt = (cur == null ? null : cur.lastProbeAt);
        String probeVal = (probeAt == null) ? "Never" : humanAge(Duration.between(probeAt, Instant.now()));

        String profitVal = formatGp(profit.getRealizedGp());
        String gphVal = formatGp(profit.getGpPerHour()) + "/h";
        String rtVal = profit.prettyRuntime();
        String blockedVal = limits != null ? String.valueOf(limits.getBlockedCount()) : "—";

        // Calculate dynamic height
        int rows = limits != null ? 9 : 8;
        int h = (rows * line) + (pad * 2) + 32;

        // Enhanced shadow with blur effect
        drawEnhancedShadow(g2, x, y, w, h);

        // Draw glassmorphism background
        drawGlassBackground(g2, x, y, w, h);

        // Draw gradient header
        drawGradientHeader(g2, x, y, w);

        // Header text with icon
        g2.setFont(headerFont);
        g2.setColor(Color.WHITE);
        g2.drawString("⚡ Viktor", x + pad, y + 21);

        // Draw content rows
        int bx = x + pad, by = y + 32 + pad, bw = w - pad * 2;
        g2.setFont(bodyFont);

        drawIconRow(g2, bx, by, bw, "🔄", "Phase", phaseVal, getPhaseColor(state.getPhase()));
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        drawIconRow(g2, bx, by, bw, "📦", "Item", itemVal, TEXT_LIGHT);
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        drawIconRow(g2, bx, by, bw, "💰", "Prices", priceVal, TEXT_LIGHT);
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        Color marginColor = margin > 0 ? GREEN : (margin < 0 ? RED : TEXT_LIGHT);
        drawIconRow(g2, bx, by, bw, "📊", "Margin", marginVal, marginColor);
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        drawIconRow(g2, bx, by, bw, "🔍", "Probe", probeVal, TEXT_MUTED);
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        // Optional: Show blocked items
        if (limits != null) {
            Color blockedColor = limits.getBlockedCount() > 0 ? YELLOW : TEXT_LIGHT;
            drawIconRow(g2, bx, by, bw, "🚫", "Blocked", blockedVal, blockedColor);
            by += line;
            drawSeparator(g2, bx, by - 9, bw);
        }

        // Profit section with emphasis
        long totalProfit = profit.getRealizedGp();
        Color profitColor = totalProfit > 0 ? GREEN : (totalProfit < 0 ? RED : TEXT_LIGHT);
        g2.setFont(boldFont);
        drawIconRow(g2, bx, by, bw, "💎", "Profit", profitVal, profitColor);
        g2.setFont(bodyFont);
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        drawIconRow(g2, bx, by, bw, "⚡", "Rate", gphVal, BLUE);
        by += line;
        drawSeparator(g2, bx, by - 9, bw);

        drawIconRow(g2, bx, by, bw, "⏱️", "Runtime", rtVal, TEXT_LIGHT);
    }

    private void drawGlassBackground(Graphics2D g2, int x, int y, int w, int h) {
        // Main background
        g2.setColor(BG_DARK);
        g2.fillRoundRect(x, y, w, h, RADIUS, RADIUS);

        // Subtle highlight (glassmorphism effect)
        int highlightHeight = h / 3;
        GradientPaint highlight = new GradientPaint(
                x, y, new Color(255, 255, 255, 18),
                x, (float)(y + highlightHeight), new Color(255, 255, 255, 0)
        );
        g2.setPaint(highlight);
        g2.fillRoundRect(x, y, w, highlightHeight, RADIUS, RADIUS);
        g2.fillRect(x, y + RADIUS, w, highlightHeight - RADIUS);
    }

    private void drawGradientHeader(Graphics2D g2, int x, int y, int w) {
        int headerH = 32;

        // Gradient background
        GradientPaint gradient = new GradientPaint(
                x, y, HEADER_START,
                x + w, y, HEADER_END
        );
        g2.setPaint(gradient);
        g2.fillRoundRect(x, y, w, headerH, RADIUS, RADIUS);
        g2.fillRect(x, y + headerH - RADIUS, w, RADIUS);

        // Shine effect on top edge
        g2.setColor(new Color(255, 255, 255, 40));
        g2.fillRoundRect(x, y, w, 2, RADIUS, RADIUS);
    }

    private void drawEnhancedShadow(Graphics2D g2, int sx, int sy, int sw, int sh) {
        // Multi-layer shadow for depth
        for (int i = 0; i < 12; i++) {
            int alpha = (int)(25 - (i * 2.0));
            if (alpha < 0) alpha = 0;

            g2.setColor(new Color(0, 0, 0, alpha));
            int offset = i / 2;
            g2.fillRoundRect(sx + offset, sy + offset + 2, sw, sh, RADIUS, RADIUS);
        }
    }

    private void drawIconRow(Graphics2D g2, int bx, int by, int bw, String icon, String label, String value, Color valueColor) {
        // Icon
        g2.setColor(TEXT_MUTED);
        g2.drawString(icon, bx, by);

        // Label
        g2.setColor(TEXT_MUTED);
        g2.drawString(label, bx + 22, by);

        // Value (right-aligned)
        g2.setColor(valueColor);
        FontMetrics fm = g2.getFontMetrics();
        int vx = bx + bw - fm.stringWidth(value);
        g2.drawString(value, vx, by);
    }

    private void drawSeparator(Graphics2D g2, int bx, int y, int bw) {
        g2.setColor(SEPARATOR);
        g2.fillRect(bx, y, bw, 1);
    }

    private String humanAge(Duration d) {
        long m = Math.max(0, d.toMinutes());
        if (m < 1) return "Just now";
        if (m < 60) return m + "m ago";
        long h = m / 60, r = m % 60;
        if (h < 24) return h + "h " + r + "m";
        long days = h / 24;
        return days + "d ago";
    }

    private String formatPhase(Object phase) {
        if (phase == null) return "—";
        String p = phase.toString();

        // Friendly names
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

    private Color getPhaseColor(Object phase) {
        if (phase == null) return TEXT_LIGHT;
        String p = phase.toString();

        switch (p) {
            case "PROBE": return YELLOW;
            case "BUY_BULK": return BLUE;
            case "SELL_BULK": return GREEN;
            case "BANKING": return new Color(148, 103, 189);
            case "COOLDOWN": return TEXT_MUTED;
            default: return TEXT_LIGHT;
        }
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

    // === Optional Customization Methods (can be called from Viktor.java) ===

    /**
     * Set overlay position (top-left corner)
     * @param x X coordinate
     * @param y Y coordinate
     */
    @SuppressWarnings("unused") // Public API for customization
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Set overlay width
     * @param width Width in pixels
     */
    @SuppressWarnings("unused") // Public API for customization
    public void setWidth(int width) {
        this.w = width;
    }

    /**
     * Enable compact mode (smaller spacing)
     * @param compact True for compact mode
     */
    @SuppressWarnings("unused") // Public API for customization
    public void setCompactMode(boolean compact) {
        this.line = compact ? 16 : 20;
        this.pad = compact ? 8 : 12;
    }
}