package dev.worldevents;

import org.bukkit.block.Block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Remembers recently player-placed ores/logs so event bonuses can't be farmed by re-placing blocks. */
public final class PlacedBlockTracker {
    private static final int LIMIT = 100_000;

    private final Set<String> placed = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > LIMIT;
        }
    });

    private static String key(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public void track(Block b) {
        placed.add(key(b));
    }

    public boolean isPlaced(Block b) {
        return placed.contains(key(b));
    }

    public void forget(Block b) {
        placed.remove(key(b));
    }
}
