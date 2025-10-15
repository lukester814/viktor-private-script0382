package com.plebsscripts.viktor.ge;

import com.plebsscripts.viktor.util.Logs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.methods.map.Tile;

public class GENavigator {

    private static final Area GE_AREA = new Area(
            new Tile(3161, 3485, 0),
            new Tile(3168, 3489, 0)
    );

    public boolean walkToGE() {
        if (Players.getLocal() != null && GE_AREA.contains(Players.getLocal())) {
            return true;
        }
        Logs.info("Walking to Grand Exchange...");
        Walking.walk(GE_AREA.getRandomTile());
        return false;
    }
}
