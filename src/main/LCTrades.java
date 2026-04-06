package com.lctrades;

import com.lctrades.commands.TradeCommand;
import com.lctrades.gui.TradeGUIListener;
import com.lctrades.manager.TradeManager;
import com.lctrades.manager.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LCTrades extends JavaPlugin {

    private static LCTrades instance;
    private TradeManager tradeManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.tradeManager = new TradeManager(this);
        
        // Register commands
        TradeCommand tradeCommand = new TradeCommand(this);
        getCommand("trade").setExecutor(tradeCommand);
        getCommand("trade").setTabCompleter(tradeCommand);
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new TradeGUIListener(this), this);
        
        getLogger().info("LCTrades has been enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel all active trades
        if (tradeManager != null) {
            tradeManager.cancelAllTrades();
        }
        
        getLogger().info("LCTrades has been disabled!");
    }

    public static LCTrades getInstance() {
        return instance;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
