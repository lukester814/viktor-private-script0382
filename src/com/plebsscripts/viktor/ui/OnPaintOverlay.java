package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.core.StateMachine;
import com.plebsscripts.viktor.core.ProfitTracker;
import com.plebsscripts.viktor.config.ItemConfig;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.text.DecimalFormat;

public class OnPaintOverlay {
    private final StateMachine state;
    private final ProfitTracker profit;
    private final DecimalFormat df0 = new DecimalFormat("#,##0");

    // layout
    private int x = 12, y = 60, w = 360;
    private int line = 18, pad = 10, radius = 14;

    public OnPaintOverlay(StateMachine state, ProfitTracker profit) {
        this.state = state;
        this.profit = profit;
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        ItemConfig cur = state.getCurrentItem();
        String phaseVal  = String.valueOf(state.getPhase());
        String itemVal   = (cur == null ? "-" : cur.itemName);

        int buy = cur == null ? 0 : Math.min(cur.maxBuy, cur.estBuy);
        int sell = cur == null ? 0 : Math.max(cur.minSell, cur.estSell);
        int margin = Math.max(0, sell - buy);
        String priceVal = (buy == 0 && sell == 0) ? "-" : ("Buy " + df0.format(buy) + "  |  Sell " + df0.format(sell));
        String marginVal = (margin == 0 ? "-" : (df0.format(margin) + " gp"));

        Instant probeAt = (cur == null ? null : cur.lastProbeAt);
        String probeVal = (probeAt == null) ? "-" : humanAge(Duration.between(probeAt, Instant.now()));

        String profitVal = df0.format(profit.getRealizedGp()) + " gp";
        String gphVal    = df0.format(profit.getGpPerHour()) + " gp/h";
        String rtVal     = profit.prettyRuntime();

        // rows: header + Phase/Item/Prices/Margin/Probe/Profit/GP/h/Runtime = 8 rows
        int rows = 8;
        int h = (rows * line) + (pad * 2) + 26;

        drawShadow(g2, x, y, w, h, radius);
        g2.setColor(new Color(18, 18, 28, 195));
        g2.fillRoundRect(x, y, w, h, radius, radius);

        int headerH = 26;
        g2.setColor(new Color(88, 101, 242, 215));
        g2.fillRoundRect(x, y, w, headerH, radius, radius);
        g2.fillRect(x, y + headerH - radius, w, radius);

        g2.setFont(new Font("Inter", Font.BOLD, 13));
        g2.setColor(Color.WHITE);
        g2.drawString("Viktor • GE Flipper", x + pad, y + 17);

        int bx = x + pad, by = y + headerH + pad, bw = w - pad * 2;
        g2.setFont(new Font("Inter", Font.PLAIN, 12));

        drawRow(g2, bx, by, bw, "Phase", phaseVal); by += line; drawSeparator(g2, bx, by - 8, bw);
        drawRow(g2, bx, by, bw, "Item", itemVal);   by += line; drawSeparator(g2, bx, by - 8, bw);
        drawRow(g2, bx, by, bw, "Prices", priceVal); by += line; drawSeparator(g2, bx, by - 8, bw);
        drawRowColoredValue(g2, bx, by, bw, "Margin", marginVal, (margin >= 0 ? new Color(67,181,129) : new Color(240,71,71)));
        by += line; drawSeparator(g2, bx, by - 8, bw);

        drawRow(g2, bx, by, bw, "Last probe", probeVal); by += line; drawSeparator(g2, bx, by - 8, bw);

        // profit block
        drawRow(g2, bx, by, bw, "Profit", profitVal); by += line; drawSeparator(g2, bx, by - 8, bw);
        drawRow(g2, bx, by, bw, "GP/hr",  gphVal);    by += line; drawSeparator(g2, bx, by - 8, bw);
        drawRow(g2, bx, by, bw, "Runtime", rtVal);    by += line;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private void drawRow(Graphics2D g2, int bx, int by, int bw, String label, String value) {
        g2.setColor(new Color(210, 214, 235));
        g2.drawString(label, bx, by);
        g2.setColor(new Color(245, 247, 255));
        FontMetrics fm = g2.getFontMetrics();
        int vx = bx + bw - fm.stringWidth(value);
        g2.drawString(value, vx, by);
    }

    private void drawRowColoredValue(Graphics2D g2, int bx, int by, int bw, String label, String value, Color valueColor) {
        g2.setColor(new Color(210, 214, 235));
        g2.drawString(label, bx, by);
        g2.setColor(valueColor);
        FontMetrics fm = g2.getFontMetrics();
        int vx = bx + bw - fm.stringWidth(value);
        g2.drawString(value, vx, by);
    }

    private void drawSeparator(Graphics2D g2, int bx, int y, int bw) {
        g2.setColor(new Color(255, 255, 255, 28));
        g2.fillRect(bx, y, bw, 1);
    }

    private void drawShadow(Graphics2D g2, int sx, int sy, int sw, int sh, int r) {
        for (int i = 0; i < 8; i++) {
            int a = 12 - i; if (a < 0) a = 0;
            g2.setColor(new Color(0, 0, 0, a));
            g2.fillRoundRect(sx + 2 + i, sy + 2 + i, sw, sh, r, r);
        }
    }

    private String humanAge(Duration d) {
        long m = Math.max(0, d.toMinutes());
        if (m < 1) return "<1m";
        if (m < 60) return m + "m";
        long h = m / 60, r = m % 60;
        return h + "h " + r + "m";
    }
}
