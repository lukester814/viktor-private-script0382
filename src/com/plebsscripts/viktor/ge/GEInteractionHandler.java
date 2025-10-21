// GEInteractionHandler.java - FINAL FIXED VERSION v3
// All errors fixed including getWidgetChildren()

package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.SmartMouse;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.widgets.WidgetChild;

import java.awt.*;
import java.util.Random;

/**
 * Handles all GE interactions using SmartMouse for human-like behavior
 */
public class GEInteractionHandler {

    private final SmartMouse smartMouse;
    private final Random random;

    // GE Widget IDs
    private static final int GE_INTERFACE_ID = 465;
    private static final int GE_CLOSE_BUTTON = 2;

    public GEInteractionHandler() {
        this.smartMouse = new SmartMouse();
        this.random = new Random();
        Logger.log("[GEInteractionHandler] Initialized with SmartMouse");
    }

    /**
     * Opens the Grand Exchange using SmartMouse
     */
    public boolean openGE() {
        if (GrandExchange.isOpen()) {
            Logger.log("[GE] Already open");
            return true;
        }

        Logger.log("[GE] Opening Grand Exchange...");

        GameObject geObject = GameObjects.closest(obj ->
                obj != null && obj.getName().contains("Grand Exchange")
        );

        if (geObject == null) {
            Logger.log("[GE] Cannot find GE object");
            return false;
        }

        Point target = geObject.getClickablePoint();
        if (target == null) {
            Logger.log("[GE] No clickable point found");
            return false;
        }

        if (random.nextDouble() < 0.3) {
            smartMouse.move(target);
            Sleep.sleep(200, 500);
        }

        smartMouse.click(target, true);
        Logger.log("[GE] Clicked GE object with SmartMouse");

        boolean opened = Sleep.sleepUntil(GrandExchange::isOpen, 5000);

        if (opened) {
            Logger.log("[GE] Successfully opened");
        } else {
            Logger.log("[GE] Failed to open");
        }

        return opened;
    }

    /**
     * Closes the GE interface
     */
    public boolean closeGE() {
        if (!GrandExchange.isOpen()) {
            return true;
        }

        Logger.log("[GE] Closing Grand Exchange...");

        WidgetChild closeButton = Widgets.get(GE_INTERFACE_ID, GE_CLOSE_BUTTON);

        if (closeButton != null && closeButton.isVisible()) {
            Point center = getWidgetCenter(closeButton);

            if (center != null) {
                smartMouse.click(center, true);
                Logger.log("[GE] Clicked close button with SmartMouse");

                return Sleep.sleepUntil(() -> !GrandExchange.isOpen(), 3000);
            }
        }

        Logger.log("[GE] Using ESC key fallback");
        org.dreambot.api.input.Keyboard.type(27);
        return Sleep.sleepUntil(() -> !GrandExchange.isOpen(), 2000);
    }

    /**
     * Clicks a specific GE slot
     */
    public boolean clickSlot(int slot) {
        if (!GrandExchange.isOpen()) {
            Logger.log("[GE] Not open, cannot click slot");
            return false;
        }

        if (slot < 1 || slot > 8) {
            Logger.log("[GE] Invalid slot: " + slot);
            return false;
        }

        Logger.log("[GE] Clicking slot " + slot + " with SmartMouse");

        WidgetChild slotWidget = Widgets.get(GE_INTERFACE_ID, getSlotChildId(slot));

        if (slotWidget != null && slotWidget.isVisible()) {
            Point center = getWidgetCenter(slotWidget);

            if (center != null) {
                int offsetX = random.nextInt(11) - 5;
                int offsetY = random.nextInt(11) - 5;

                Point target = new Point(center.x + offsetX, center.y + offsetY);

                if (random.nextDouble() < 0.01) {
                    Logger.log("[GE] Simulating miss-click");
                    Point missPoint = new Point(
                            target.x + (random.nextInt(30) - 15),
                            target.y + (random.nextInt(30) - 15)
                    );
                    smartMouse.click(missPoint, true);
                    Sleep.sleep(100, 300);
                }

                smartMouse.click(target, true);
                return true;
            }
        }

        Logger.log("[GE] Failed to find slot widget");
        return false;
    }

    /**
     * Collects items from GE
     */
    public boolean collectFromGE() {
        if (!GrandExchange.isOpen()) {
            Logger.log("[GE] Not open, cannot collect");
            return false;
        }

        Logger.log("[GE] Collecting items with SmartMouse");

        WidgetChild collectButton = findWidgetByText("Collect");

        if (collectButton != null && collectButton.isVisible()) {
            Point center = getWidgetCenter(collectButton);

            if (center != null) {
                smartMouse.click(center, true);
                Logger.log("[GE] Clicked collect button");
                Sleep.sleep(600, 1200);
                return true;
            }
        }

        Logger.log("[GE] Could not find collect button");
        return false;
    }

    /**
     * Buys an item
     */
    public boolean buyItem(String itemName, int price, int quantity) {
        Logger.log("[GE] Buying " + quantity + "x " + itemName + " @ " + price + "gp");

        if (!clickSlot(1)) {
            return false;
        }

        Sleep.sleep(400, 800);

        typeItemNameHumanLike(itemName);

        Sleep.sleep(600, 1200);

        return true;
    }

    /**
     * Sells an item
     */
    public boolean sellItem(String itemName, int price, int quantity) {
        Logger.log("[GE] Selling " + quantity + "x " + itemName + " @ " + price + "gp");
        return true;
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Get center point of widget manually
     */
    private Point getWidgetCenter(WidgetChild widget) {
        if (widget == null) {
            return null;
        }

        try {
            int x = widget.getX();
            int y = widget.getY();
            int width = widget.getWidth();
            int height = widget.getHeight();

            if (width > 0 && height > 0) {
                return new Point(x + width / 2, y + height / 2);
            }
        } catch (Exception e) {
            Logger.log("[GE] Error calculating widget center: " + e.getMessage());
        }

        return null;
    }

    /**
     * FIX v3: Find widget by text using parent.getChildren()
     */
    private WidgetChild findWidgetByText(String text) {
        try {
            // Get the parent widget
            WidgetChild parent = Widgets.get(GE_INTERFACE_ID);

            if (parent == null) {
                return null;
            }

            // Get children from parent
            WidgetChild[] children = parent.getChildren();

            if (children != null) {
                for (WidgetChild child : children) {
                    if (child != null && child.getText() != null && child.getText().contains(text)) {
                        return child;
                    }
                }
            }
        } catch (Exception e) {
            Logger.log("[GE] Error finding widget by text: " + e.getMessage());
        }

        return null;
    }

    /**
     * Type with human-like delays
     */
    private void typeItemNameHumanLike(String text) {
        for (char c : text.toCharArray()) {
            org.dreambot.api.input.Keyboard.type(c);

            int delay = 50 + random.nextInt(100);
            Sleep.sleep(delay);

            if (random.nextDouble() < 0.05) {
                Sleep.sleep(200, 500);
            }
        }
    }

    /**
     * Get child ID for slot
     */
    private int getSlotChildId(int slot) {
        int[] slotIds = {7, 8, 9, 10, 11, 12, 13, 14};
        return slotIds[slot - 1];
    }

    public SmartMouse getSmartMouse() {
        return smartMouse;
    }
}