package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.container.impl.bank.Bank;

public class InventoryBanking {

    public boolean openAndWithdrawCoins(int amount) {
        if (!Bank.isOpen()) Bank.open();
        if (Bank.contains("Coins")) {
            Bank.withdraw("Coins", amount);
            Bank.close();
            Logs.info("Withdrew " + amount + " coins.");
            return true;
        }
        Logs.warn("No coins found in bank!");
        return false;
    }
}
