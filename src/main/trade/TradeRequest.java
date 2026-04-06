package com.lctrades.trade;

import com.lctrades.manager.TradeManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TradeRequest {

    private final Player sender;
    private final Player target;
    private final long expirationTime;
    private BukkitRunnable expirationTask;

    public TradeRequest(Player sender, Player target, int timeoutSeconds) {
        this.sender = sender;
        this.target = target;
        this.expirationTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
    }

    public Player getSender() {
        return sender;
    }

    public Player getTarget() {
        return target;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    /**
     * Start the expiration timer
     */
    public void startExpirationTimer(TradeManager manager) {
        long delay = (expirationTime - System.currentTimeMillis()) / 50; // Convert to ticks
        
        if (delay <= 0) {
            manager.removeRequest(target.getUniqueId());
            return;
        }
        
        expirationTask = new BukkitRunnable() {
            @Override
            public void run() {
                manager.removeRequest(target.getUniqueId());
            }
        };
        expirationTask.runTaskLater(manager.getPlugin(), delay);
    }

    /**
     * Cancel the expiration timer
     */
    public void cancelTimer() {
        if (expirationTask != null) {
            expirationTask.cancel();
        }
    }
}
