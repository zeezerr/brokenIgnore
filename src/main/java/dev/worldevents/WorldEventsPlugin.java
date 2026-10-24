package dev.worldevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public final class WorldEventsPlugin extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private EventManager manager;
    private PlacedBlockTracker tracker;
    private File dataFile;
    private FileConfiguration data;
    private NamespacedKey spawnerKey;
    private EventGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        spawnerKey = new NamespacedKey(this, "no_bonus");
        tracker = new PlacedBlockTracker();

        manager = new EventManager(this);
        manager.registerAll();
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        gui = new EventGui(this);
        getServer().getPluginManager().registerEvents(gui, this);

        PluginCommand cmd = getCommand("worldevents");
        if (cmd != null) {
            WorldEventsCommand executor = new WorldEventsCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        manager.start();
        getLogger().info("Loaded " + manager.getEvents().size() + " events.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public EventManager manager() { return manager; }
    public PlacedBlockTracker tracker() { return tracker; }
    public EventGui gui() { return gui; }
    public FileConfiguration data() { return data; }

    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Could not save data.yml", ex);
        }
    }

    /** Marks a mob as spawner/extra-spawned so it never gives event loot bonuses. */
    public void tagSpawnerMob(Entity e) {
        e.getPersistentDataContainer().set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isSpawnerMob(Entity e) {
        return e.getPersistentDataContainer().has(spawnerKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- messages
    public Component mm(String raw, String... kv) {
        String s = raw.replace("{prefix}", getConfig().getString("messages.prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return MM.deserialize(s);
    }

    public Component msg(String key, String... kv) {
        return mm(getConfig().getString("messages." + key, key), kv);
    }

    public void broadcast(Component c) {
        Bukkit.broadcast(c);
    }
}
