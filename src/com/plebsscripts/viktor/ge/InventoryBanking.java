package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.wrappers.interactive.GameObject;

/**
 * Handles banking operations for the GE flipper.
 * Manages coin withdrawals, item deposits, and inventory management.
 */
public class InventoryBanking {

    /**
     * Check if we need to bank (inventory has items that should be banked)
     */
    public boolean needsBank() {
        // Check if inventory has items (excluding coins)
        return Inventory.count(item -> item != null && !item.getName().equals("Coins")) > 0;
    }

    /**
     * Bank all items (except coins)
     */
    public void bankAll() {
        try {
            if (!Bank.isOpen()) {
                if (!openBank()) {
                    Logs.warn("Failed to open bank");
                    return;
                }
            }

            // Deposit all items except coins
            Bank.depositAllExcept("Coins");
            Logs.info("Banked all items");

            // Small delay before closing
            sleep(600, 1000);
            Bank.close();

        } catch (Exception e) {
            Logs.warn("bankAll failed: " + e.getMessage());
        }
    }

    /**
     * Bank a specific item
     */
    public boolean bankItem(String itemName) {
        try {
            if (!Bank.isOpen()) {
                if (!openBank()) {
                    return false;
                }
            }

            int count = Inventory.count(itemName);
            if (count > 0) {
                Bank.depositAll(itemName);
                Logs.info("Banked " + count + "x " + itemName);
                sleep(400, 800);
                Bank.close();
                return true;
            }

            Logs.warn("No " + itemName + " to bank");
            return false;

        } catch (Exception e) {
            Logs.warn("bankItem failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Withdraw coins from bank
     */
    public boolean openAndWithdrawCoins(int amount) {
        try {
            if (!Bank.isOpen()) {
                if (!openBank()) {
                    return false;
                }
            }

            if (Bank.contains("Coins")) {
                Bank.withdraw("Coins", amount);
                Logs.info("Withdrew " + amount + " coins");
                sleep(600, 1000);
                Bank.close();
                return true;
            }

            Logs.warn("No coins found in bank!");
            return false;

        } catch (Exception e) {
            Logs.warn("withdrawCoins failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Withdraw all coins from bank
     */
    public boolean withdrawAllCoins() {
        try {
            if (!Bank.isOpen()) {
                if (!openBank()) {
                    return false;
                }
            }

            if (Bank.contains("Coins")) {
                Bank.withdrawAll("Coins");
                Logs.info("Withdrew all coins");
                sleep(600, 1000);
                Bank.close();
                return true;
            }

            Logs.warn("No coins in bank");
            return false;

        } catch (Exception e) {
            Logs.warn("withdrawAllCoins failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open the nearest bank
     */
    private boolean openBank() {
        try {
            // Try to find bank booth/chest
            GameObject bank = GameObjects.closest(obj ->
                    obj != null && (obj.hasAction("Bank") || obj.getName().contains("Bank")));

            if (bank == null) {
                Logs.warn("No bank found nearby");
                return false;
            }

            if (bank.interact("Bank")) {
                return org.dreambot.api.utilities.Sleep.sleepUntil(Bank::isOpen, 3000);
            }

            return false;

        } catch (Exception e) {
            Logs.warn("openBank failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if bank is nearby
     */
    public boolean nearBank() {
        try {
            GameObject bank = GameObjects.closest(obj ->
                    obj != null && obj.hasAction("Bank"));
            return bank != null && bank.distance() < 10;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get coin count in inventory
     */
    public int getCoins() {
        try {
            return Inventory.count("Coins");
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check if inventory is full
     */
    public boolean isFull() {
        return Inventory.isFull();
    }

    /**
     * Check if inventory is empty (except coins)
     */
    public boolean isEmpty() {
        return Inventory.count(item ->
                item != null && !item.getName().equals("Coins")) == 0;
    }

    // Helper sleep method
    private void sleep(int min, int max) {
        try {
            Thread.sleep(min + (int)(Math.random() * (max - min)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
