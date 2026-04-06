package com.lctrades.gui;

import com.lctrades.LCTrades;
import com.lctrades.manager.TradeManager;
import com.lctrades.trade.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TradeGUIListener implements Listener {

    private final LCTrades plugin;
    private final TradeManager tradeManager;

    public TradeGUIListener(LCTrades plugin) {
        this.plugin = plugin;
        this.tradeManager = plugin.getTradeManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        TradeSession session = tradeManager.getSession(player.getUniqueId());
        
        if (session == null) return;
        
        // Handle the click in the trade session
        session.handleClick(player, event.getSlot(), event.getClickedInventory());
        
        // Cancel the event if it's on the divider column or confirm/status buttons
        int slot = event.getSlot();
        if (slot == 40 || slot == 49 || (slot >= 0 && slot % 9 == 4)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        TradeSession session = tradeManager.getSession(player.getUniqueId());
        
        if (session == null) return;
        
        // Check if any of the dragged slots are in the divider column
        for (int slot : event.getRawSlots()) {
            // Convert raw slot to inventory slot
            int column = slot % 9;
            if (column == 4) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Check if dragging to other player's side
        boolean isP1 = session.isPlayer1(player.getUniqueId());
        for (int slot : event.getRawSlots()) {
            int column = slot % 9;
            // Columns 5-8 are other player's side for player 1, columns 0-3 are other player's side for player 2
            if ((isP1 && column > 4) || (!isP1 && column < 4)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("not-your-slot"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        TradeSession session = tradeManager.getSession(player.getUniqueId());
        
        if (session == null) return;
        
        // Handle the close event
        session.handleClose(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in a trade
        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session != null) {
            session.cancelTrade(null);
        }
        
        // Check if player has pending requests
        var pendingRequest = tradeManager.getPendingRequest(player.getUniqueId());
        if (pendingRequest != null) {
            pendingRequest.cancelTimer();
        }
    }
}
