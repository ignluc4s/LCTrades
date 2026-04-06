package com.lctrades.trade;

import com.lctrades.LCTrades;
import com.lctrades.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeSession {

    private final LCTrades plugin;
    private final ConfigManager config;
    private final Player player1;
    private final Player player2;
    
    private Inventory gui1;
    private Inventory gui2;
    
    private boolean player1Ready = false;
    private boolean player2Ready = false;
    
    private boolean tradeCompleted = false;
    private boolean countdownActive = false;
    
    private BukkitTask countdownTask;
    private BukkitTask proximityTask;

    // Item storage: slot -> item for each player
    private final Map<Integer, ItemStack> player1Items;
    private final Map<Integer, ItemStack> player2Items;

    public TradeSession(LCTrades plugin, Player player1, Player player2) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.player1 = player1;
        this.player2 = player2;
        this.player1Items = new HashMap<>();
        this.player2Items = new HashMap<>();
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public boolean isPlayer1(UUID uuid) {
        return player1.getUniqueId().equals(uuid);
    }

    public boolean isPlayer2(UUID uuid) {
        return player2.getUniqueId().equals(uuid);
    }

    /**
     * Open GUIs for both players
     */
    public void openGUIs() {
        String title1 = config.translateColors(config.getGUITitle("active-trade"))
                .replace("%player%", player2.getName());
        String title2 = config.translateColors(config.getGUITitle("active-trade"))
                .replace("%player%", player1.getName());
        
        int size = config.getGUISize();
        
        gui1 = Bukkit.createInventory(null, size, title1);
        gui2 = Bukkit.createInventory(null, size, title2);
        
        setupGUI(gui1, true);
        setupGUI(gui2, false);
        
        player1.openInventory(gui1);
        player2.openInventory(gui2);
        
        // Start proximity checker
        startProximityChecker();
    }

    /**
     * Setup the GUI with dividers and confirm buttons
     */
    private void setupGUI(Inventory gui, boolean isPlayer1) {
        ItemStack divider = createDivider();
        
        // Set up divider column (column 4)
        for (int row = 0; row < gui.getSize() / 9; row++) {
            int slot = row * 9 + 4;
            gui.setItem(slot, divider);
        }
        
        // Set up confirm button
        updateConfirmButton(gui, false);
        
        // Set up status item
        updateStatusItem(gui);
    }

    private ItemStack createDivider() {
        Material mat = Material.getMaterial(config.getString("gui.divider.material", "BLACK_STAINED_GLASS_PANE"));
        if (mat == null) mat = Material.BLACK_STAINED_GLASS_PANE;
        
        ItemStack item = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.translateColors(config.getString("gui.divider.name", " ")));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Update the confirm button based on ready state
     */
    private void updateConfirmButton(Inventory gui, boolean ready) {
        String path = ready ? "gui.confirm-ready" : "gui.confirm-not-ready";
        Material mat = Material.getMaterial(config.getString(path + ".material", ready ? "LIME_WOOL" : "RED_WOOL"));
        if (mat == null) mat = ready ? Material.LIME_WOOL : Material.RED_WOOL;
        
        ItemStack button = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.translateColors(config.getString(path + ".name", ready ? "&aReady" : "&cNot Ready")));
            // Add lore
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(config.translateColors(ready ? config.getString("messages.click-to-unconfirm", "&cClick to unconfirm") : config.getString("messages.click-to-confirm", "&aClick to confirm")));
            meta.setLore(lore);
            button.setItemMeta(meta);
        }
        
        gui.setItem(49, button); // Bottom middle slot
    }

    /**
     * Update the status item
     */
    private void updateStatusItem(Inventory gui) {
        Material mat = Material.getMaterial(config.getString("gui.status-item.material", "CLOCK"));
        if (mat == null) mat = Material.CLOCK;
        
        String p1Status = player1Ready ? "&aReady" : "&cNot Ready";
        String p2Status = player2Ready ? "&aReady" : "&cNot Ready";
        
        ItemStack status = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = status.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.translateColors(config.getString("gui.status-item.name", "&eTrade Status")));
            java.util.List<String> lore = new java.util.ArrayList<>();
            java.util.List<String> defaultLore = java.util.Arrays.asList("&7You: %your_status%", "&7Them: %their_status%");
            java.util.List<String> configLore = config.getConfig().getStringList("gui.status-item.lore");
            if (configLore.isEmpty()) {
                configLore = defaultLore;
            }
            for (String line : configLore) {
                lore.add(config.translateColors(line
                        .replace("%your_status%", gui == gui1 ? p1Status : p2Status)
                        .replace("%their_status%", gui == gui1 ? p2Status : p1Status)));
            }
            meta.setLore(lore);
            status.setItemMeta(meta);
        }
        
        gui.setItem(40, status); // Middle slot
    }

    /**
     * Handle inventory click
     */
    public void handleClick(Player player, int slot, Inventory clickedInventory) {
        if (tradeCompleted || countdownActive) return;
        
        boolean isP1 = player.equals(player1);
        Inventory playerGui = isP1 ? gui1 : gui2;
        
        // Ensure player is clicking their own GUI
        if (!clickedInventory.equals(playerGui)) return;
        
        // Check if clicking confirm button
        if (slot == 49) {
            toggleReady(player);
            return;
        }
        
        // Block clicks on divider and status items
        if (slot == 40 || slot % 9 == 4) {
            return;
        }
        
        // Check if player is ready (can't modify items while ready)
        boolean playerReady = isP1 ? player1Ready : player2Ready;
        if (playerReady) {
            player.sendMessage(config.getMessage("cannot-modify"));
            return;
        }
        
        // Check if clicking on other player's side
        int column = slot % 9;
        if (column > 4) {
            player.sendMessage(config.getMessage("not-your-slot"));
        }
    }

    /**
     * Toggle ready state for a player
     */
    private void toggleReady(Player player) {
        boolean isP1 = player.equals(player1);
        
        if (isP1) {
            player1Ready = !player1Ready;
        } else {
            player2Ready = !player2Ready;
        }
        
        // Update both GUIs
        updateConfirmButton(gui1, player1Ready);
        updateConfirmButton(gui2, player2Ready);
        updateStatusItem(gui1);
        updateStatusItem(gui2);
        
        player.sendMessage(config.getMessage("player-readied"));
        config.playSound(player, "ready-up");
        
        // Check if both ready
        if (player1Ready && player2Ready) {
            startCountdown();
        }
    }

    /**
     * Start the trade countdown
     */
    private void startCountdown() {
        if (countdownActive) return;
        countdownActive = true;
        
        int countdown = config.getInt("trade.countdown", 3);
        
        player1.sendMessage(config.getMessage("both-readied").replace("%seconds%", String.valueOf(countdown)));
        player2.sendMessage(config.getMessage("both-readied").replace("%seconds%", String.valueOf(countdown)));
        
        countdownTask = new BukkitRunnable() {
            int seconds = countdown;
            
            @Override
            public void run() {
                if (seconds > 0) {
                    config.playSound(player1, "countdown");
                    config.playSound(player2, "countdown");
                    seconds--;
                } else {
                    completeTrade();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Complete the trade
     */
    private void completeTrade() {
        if (tradeCompleted) return;
        tradeCompleted = true;
        
        // Capture items before closing
        captureItems();
        
        // Check inventories have space
        if (!hasSpace(player2, player1Items.values())) {
            cancelTrade(config.getMessage("target-inventory-full").replace("%player%", player2.getName()));
            return;
        }
        if (!hasSpace(player1, player2Items.values())) {
            cancelTrade(config.getMessage("inventory-full"));
            return;
        }
        
        // Close inventories
        player1.closeInventory();
        player2.closeInventory();
        
        // Give items
        giveItems(player1, player2Items);
        giveItems(player2, player1Items);
        
        // Send completion messages
        player1.sendMessage(config.getMessage("trade-complete"));
        player2.sendMessage(config.getMessage("trade-complete"));
        config.playSound(player1, "trade-complete");
        config.playSound(player2, "trade-complete");
        
        // Clean up
        cleanup();
    }

    /**
     * Capture items from both GUIs
     */
    private void captureItems() {
        player1Items.clear();
        player2Items.clear();
        
        // Player 1's items (left side of gui1)
        for (int row = 0; row < gui1.getSize() / 9; row++) {
            for (int col = 0; col < 4; col++) {
                int slot = row * 9 + col;
                ItemStack item = gui1.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    player1Items.put(slot, item.clone());
                }
            }
        }
        
        // Player 2's items (left side of gui2, which represents their items)
        for (int row = 0; row < gui2.getSize() / 9; row++) {
            for (int col = 0; col < 4; col++) {
                int slot = row * 9 + col;
                ItemStack item = gui2.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    player2Items.put(slot, item.clone());
                }
            }
        }
    }

    /**
     * Check if player has enough space for items
     */
    private boolean hasSpace(Player player, java.util.Collection<ItemStack> items) {
        Inventory temp = Bukkit.createInventory(null, 36);
        temp.setContents(player.getInventory().getStorageContents());
        
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                java.util.HashMap<Integer, ItemStack> leftover = temp.addItem(item);
                if (!leftover.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Give items to a player
     */
    private void giveItems(Player player, Map<Integer, ItemStack> items) {
        for (ItemStack item : items.values()) {
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }
    }

    /**
     * Cancel the trade and return items
     */
    public void cancelTrade(String reason) {
        if (tradeCompleted) return;
        tradeCompleted = true;
        
        // Cancel tasks
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (proximityTask != null) {
            proximityTask.cancel();
        }
        
        // Return items to players
        returnItemsToPlayer(player1, gui1);
        returnItemsToPlayer(player2, gui2);
        
        // Close inventories
        if (player1.isOnline()) {
            player1.closeInventory();
            if (reason != null) player1.sendMessage(reason);
            config.playSound(player1, "trade-cancelled");
        }
        if (player2.isOnline()) {
            player2.closeInventory();
            if (reason != null) player2.sendMessage(reason);
            config.playSound(player2, "trade-cancelled");
        }
        
        // Clean up
        cleanup();
    }

    /**
     * Return items from GUI to player's inventory
     */
    private void returnItemsToPlayer(Player player, Inventory gui) {
        if (!player.isOnline()) return;
        
        for (int row = 0; row < gui.getSize() / 9; row++) {
            for (int col = 0; col < 4; col++) {
                int slot = row * 9 + col;
                ItemStack item = gui.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item);
                }
            }
        }
    }

    /**
     * Start proximity checker
     */
    private void startProximityChecker() {
        int maxDistance = config.getInt("trade.max-distance", 10);
        boolean crossWorld = config.getBoolean("trade.cross-world", false);
        
        if (maxDistance < 0 && crossWorld) return; // No restrictions
        
        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player1.isOnline() || !player2.isOnline()) {
                    cancelTrade(config.getMessage("trade-cancelled"));
                    return;
                }
                
                if (!crossWorld && !player1.getWorld().equals(player2.getWorld())) {
                    cancelTrade(config.getMessage("trade-cancelled-world"));
                    return;
                }
                
                if (maxDistance >= 0) {
                    double distance = player1.getLocation().distance(player2.getLocation());
                    if (distance > maxDistance) {
                        cancelTrade(config.getMessage("trade-cancelled-distance"));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Handle player closing inventory
     */
    public void handleClose(Player player) {
        if (tradeCompleted) return;
        cancelTrade(config.getMessage("trade-cancelled"));
    }

    /**
     * Clean up the session
     */
    private void cleanup() {
        if (countdownTask != null) countdownTask.cancel();
        if (proximityTask != null) proximityTask.cancel();
        plugin.getTradeManager().removeSession(this);
    }

    public LCTrades getPlugin() {
        return plugin;
    }
}
