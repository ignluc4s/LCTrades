package com.lctrades.manager;

import com.lctrades.LCTrades;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ConfigManager {

    private final LCTrades plugin;
    private FileConfiguration config;

    public ConfigManager(LCTrades plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * Get a string from config with color codes translated
     */
    public String getString(String path, String defaultValue) {
        String value = config.getString(path, defaultValue);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a message from config with prefix and color codes
     */
    public String getMessage(String key) {
        String prefix = getString("messages.prefix", "&8[&aLCTrades&8] &r");
        String message = getString("messages." + key, "&cMessage not found: " + key);
        return translateColors(prefix + message);
    }

    /**
     * Get an integer from config
     */
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * Get a boolean from config
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    /**
     * Translate color codes
     */
    public String translateColors(String text) {
        if (text == null) return "";
        return text.replace("&", "\u00a7");
    }

    /**
     * Play a sound for a player
     */
    public void playSound(Player player, String soundKey) {
        if (player == null || !player.isOnline()) return;
        
        String soundName = getString("sounds." + soundKey, null);
        if (soundName == null) return;
        
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + soundName);
        }
    }

    /**
     * Get GUI setting
     */
    public String getGUITitle(String type) {
        return getString("gui." + type + "-title", "&6Trade");
    }

    public int getGUISize() {
        return getInt("gui.size", 54);
    }

    /**
     * Reload config
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    /**
     * Get the raw FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
