package com.plebsscripts.viktor.ui;

import com.plebsscripts.viktor.config.ItemConfig;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ItemTableModel extends AbstractTableModel {
    private final String[] cols = {
            "Item","estBuy","estSell","maxBuy","minSell",
            "probeQty","maxQty","minMargin","Liq","p(up)"
    };
    private List<ItemConfig> items = new ArrayList<>();

    public void setItems(List<ItemConfig> list) {
        items = (list != null) ? list : new ArrayList<ItemConfig>();
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return items.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }

    @Override
    public Object getValueAt(int r, int c) {
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
            case 9:  return String.format("%.2f", i.probUp);
            default: return "";
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        // Helps JTable render numbers properly
        switch (columnIndex) {
            case 1: case 2: case 3: case 4: case 5: case 6: case 7: case 8:
                return Integer.class;
            default:
                return String.class;
        }
    }
}
