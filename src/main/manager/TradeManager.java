package com.lctrades.manager;

import com.lctrades.LCTrades;
import com.lctrades.trade.TradeRequest;
import com.lctrades.trade.TradeSession;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {

    private final LCTrades plugin;
    private final ConfigManager config;
    
    // Pending trade requests: target -> request
    private final Map<UUID, TradeRequest> pendingRequests;
    
    // Active trade sessions: player UUID -> session
    private final Map<UUID, TradeSession> activeSessions;
    
    // Players who have disabled trade requests
    private final Set<UUID> disabledTraders;

    public TradeManager(LCTrades plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        this.disabledTraders = ConcurrentHashMap.newKeySet();
    }

    /**
     * Send a trade request from sender to target
     */
    public void sendRequest(Player sender, Player target) {
        // Check if sender is already trading
        if (isTrading(sender.getUniqueId())) {
            sender.sendMessage(config.getMessage("already-trading"));
            return;
        }
        
        // Check if target is already trading
        if (isTrading(target.getUniqueId())) {
            sender.sendMessage(config.getMessage("target-already-trading")
                    .replace("%player%", target.getName()));
            return;
        }
        
        // Check if target has trading disabled
        if (hasTradingDisabled(target.getUniqueId())) {
            sender.sendMessage(config.getMessage("target-toggled-off")
                    .replace("%player%", target.getName()));
            return;
        }
        
        // Create and store request
        TradeRequest request = new TradeRequest(sender, target, 
                config.getInt("trade.request-timeout", 60));
        pendingRequests.put(target.getUniqueId(), request);
        
        // Send messages
        sender.sendMessage(config.getMessage("request-sent")
                .replace("%player%", target.getName()));
        target.sendMessage(config.getMessage("request-received")
                .replace("%player%", sender.getName()));
        target.sendMessage(config.getMessage("prefix") + "&7Type &e/trade accept &7or &e/trade deny");
        
        // Play sounds
        config.playSound(sender, "request-sent");
        config.playSound(target, "request-received");
        
        // Start expiration timer
        request.startExpirationTimer(this);
    }

    /**
     * Accept a pending trade request
     */
    public void acceptRequest(Player accepter) {
        TradeRequest request = pendingRequests.remove(accepter.getUniqueId());
        
        if (request == null) {
            accepter.sendMessage(config.getMessage("no-pending-request"));
            return;
        }
        
        Player sender = request.getSender();
        
        // Validate sender is still online
        if (!sender.isOnline()) {
            accepter.sendMessage(config.getMessage("player-offline"));
            return;
        }
        
        // Check if either player is now trading
        if (isTrading(sender.getUniqueId()) || isTrading(accepter.getUniqueId())) {
            accepter.sendMessage(config.getMessage("already-trading"));
            sender.sendMessage(config.getMessage("already-trading"));
            return;
        }
        
        // Send acceptance messages
        accepter.sendMessage(config.getMessage("request-accepted"));
        sender.sendMessage(config.getMessage("request-accepted"));
        
        // Play sounds
        config.playSound(sender, "trade-accepted");
        config.playSound(accepter, "trade-accepted");
        
        // Create trade session
        TradeSession session = new TradeSession(plugin, sender, accepter);
        activeSessions.put(sender.getUniqueId(), session);
        activeSessions.put(accepter.getUniqueId(), session);
        
        // Open GUIs
        session.openGUIs();
    }

    /**
     * Deny a pending trade request
     */
    public void denyRequest(Player denier, Player sender) {
        TradeRequest request = pendingRequests.remove(denier.getUniqueId());
        
        if (request == null || (sender != null && !request.getSender().equals(sender))) {
            denier.sendMessage(config.getMessage("no-request-from"));
            return;
        }
        
        Player actualSender = request.getSender();
        
        denier.sendMessage(config.getMessage("request-denied"));
        
        if (actualSender.isOnline()) {
            actualSender.sendMessage(config.getMessage("request-denied-sender")
                    .replace("%player%", denier.getName()));
            config.playSound(actualSender, "trade-denied");
        }
        
        config.playSound(denier, "trade-denied");
    }

    /**
     * Get pending request for a player
     */
    public TradeRequest getPendingRequest(UUID playerId) {
        return pendingRequests.get(playerId);
    }

    /**
     * Remove expired request
     */
    public void removeRequest(UUID targetId) {
        TradeRequest request = pendingRequests.remove(targetId);
        if (request != null && request.getTarget().isOnline()) {
            request.getTarget().sendMessage(config.getMessage("request-expired-target")
                    .replace("%player%", request.getSender().getName()));
        }
        if (request != null && request.getSender().isOnline()) {
            request.getSender().sendMessage(config.getMessage("request-expired")
                    .replace("%player%", request.getTarget().getName()));
        }
    }

    /**
     * Check if a player is currently trading
     */
    public boolean isTrading(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Get active trade session for a player
     */
    public TradeSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    /**
     * Remove a trade session
     */
    public void removeSession(TradeSession session) {
        if (session.getPlayer1() != null) {
            activeSessions.remove(session.getPlayer1().getUniqueId());
        }
        if (session.getPlayer2() != null) {
            activeSessions.remove(session.getPlayer2().getUniqueId());
        }
    }

    /**
     * Cancel all active trades (for shutdown)
     */
    public void cancelAllTrades() {
        for (TradeSession session : new HashSet<>(activeSessions.values())) {
            session.cancelTrade("Plugin disabled");
        }
        activeSessions.clear();
        pendingRequests.clear();
    }

    /**
     * Toggle trading for a player
     */
    public boolean toggleTrading(UUID playerId) {
        if (disabledTraders.contains(playerId)) {
            disabledTraders.remove(playerId);
            return true; // Now enabled
        } else {
            disabledTraders.add(playerId);
            return false; // Now disabled
        }
    }

    /**
     * Check if a player has trading disabled
     */
    public boolean hasTradingDisabled(UUID playerId) {
        return disabledTraders.contains(playerId);
    }

    /**
     * Get the plugin instance
     */
    public LCTrades getPlugin() {
        return plugin;
    }
}
