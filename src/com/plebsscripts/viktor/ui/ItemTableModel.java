package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.config.ItemConfig;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for displaying loaded items in the GUI
 * Shows all item configuration fields in a sortable table
 */
public class ItemTableModel extends AbstractTableModel {
    private static final DecimalFormat GP_FORMAT = new DecimalFormat("#,###");

    private final String[] cols = {
            "Item", "Est Buy", "Est Sell", "Max Buy", "Min Sell",
            "Probe Qty", "Max Qty", "Min Margin", "Liquidity", "P(Up)"
    };

    private List<ItemConfig> items = new ArrayList<>();

    /**
     * Update table with new item list
     */
    public void setItems(List<ItemConfig> items) {
        this.items = items != null ? items : new ArrayList<>();
        fireTableDataChanged();
    }

    /**
     * Get item at specific row
     */
    public ItemConfig getItemAt(int row) {
        if (row >= 0 && row < items.size()) {
            return items.get(row);
        }
        return null;
    }

    /**
     * Get all items
     */
    public List<ItemConfig> getItems() {
        return new ArrayList<>(items);
    }

    @Override
    public int getRowCount() {
        return items.size();
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
        if (r < 0 || r >= items.size()) return "";

        ItemConfig i = items.get(r);
        switch (c) {
            case 0:  return i.itemName;
            case 1:  return i.estBuy;
            case 2:  return i.estSell;
            case 3:  return i.maxBuy;
            case 4:  return i.minSell;
            case 5:  return i.probeQty;
            case 6:  return i.maxQtyPerCycle;
            case 7:  return i.minMarginGp;
            case 8:  return (int) i.liquidity;
            case 9:  return String.format("%.2f%%", i.probUp * 100); // Show as percentage
            default: return "";
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        // Helps JTable render numbers properly (right-aligned, sortable)
        switch (columnIndex) {
            case 1: case 2: case 3: case 4: case 5: case 6: case 7: case 8:
                return Integer.class;
            case 9:
                return String.class; // P(Up) as formatted string
            default:
                return String.class;
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false; // Read-only table
    }
}
