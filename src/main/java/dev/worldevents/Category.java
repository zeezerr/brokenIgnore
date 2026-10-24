package dev.worldevents;

public enum Category {
    PLAYER_EFFECTS("Player Effects"),
    MINING("Mining"),
    FARMING("Farming"),
    WORLD("World Events"),
    ECONOMY("Economy"),
    PVP("PvP"),
    LOOT("Loot"),
    UTILITY("Utility");

    public final String display;

    Category(String display) {
        this.display = display;
    }
}
