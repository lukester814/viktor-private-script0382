package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;

public class PriceModel {
    public PriceModel() {}

    public int buyPrice(ItemConfig ic) {
        return Math.min(ic.maxBuy, ic.estBuy);
    }

    public int sellPrice(ItemConfig ic) {
        return Math.max(ic.minSell, ic.estSell);
    }

    public boolean meetsMinMargin(ItemConfig ic) {
        return sellPrice(ic) - buyPrice(ic) >= ic.minMarginGp;
    }
}
