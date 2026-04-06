package com.lctrades.commands;

import com.lctrades.LCTrades;
import com.lctrades.manager.ConfigManager;
import com.lctrades.manager.TradeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final LCTrades plugin;
    private final TradeManager tradeManager;
    private final ConfigManager config;

    public TradeCommand(LCTrades plugin) {
        this.plugin = plugin;
        this.tradeManager = plugin.getTradeManager();
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.translateColors("&cOnly players can use this command!"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("lctrades.use")) {
            player.sendMessage(config.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelp(player);
                break;
            case "accept":
                handleAccept(player, args);
                break;
            case "deny":
                handleDeny(player, args);
                break;
            case "toggle":
            case "t":
                handleToggle(player);
                break;
            case "reload":
                handleReload(player);
                break;
            default:
                // Treat as player name for trade request
                handleTradeRequest(player, subCommand);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(config.translateColors("&8&m          &r &a&lLCTrades &8&m          "));
        player.sendMessage(config.translateColors("&a/trade <player> &7- Send a trade request"));
        player.sendMessage(config.translateColors("&a/trade accept &7- Accept pending trade request"));
        player.sendMessage(config.translateColors("&a/trade deny [player] &7- Deny pending trade request"));
        player.sendMessage(config.translateColors("&a/trade toggle &7- Toggle trade requests on/off"));
        if (player.hasPermission("lctrades.admin")) {
            player.sendMessage(config.translateColors("&a/trade reload &7- Reload configuration"));
        }
        player.sendMessage(config.translateColors("&8&m                              "));
    }

    private void handleTradeRequest(Player sender, String targetName) {
        // Prevent self-trading
        if (targetName.equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(config.getMessage("self-trade"));
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(config.translateColors("&cPlayer '&e" + targetName + "&c' not found!"));
            return;
        }

        tradeManager.sendRequest(sender, target);
    }

    private void handleAccept(Player player, String[] args) {
        tradeManager.acceptRequest(player);
    }

    private void handleDeny(Player player, String[] args) {
        Player sender = null;
        if (args.length > 1) {
            sender = Bukkit.getPlayerExact(args[1]);
        }
        tradeManager.denyRequest(player, sender);
    }

    private void handleToggle(Player player) {
        boolean enabled = tradeManager.toggleTrading(player.getUniqueId());
        player.sendMessage(enabled ? config.getMessage("toggle-enabled") : config.getMessage("toggle-disabled"));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("lctrades.admin")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        config.reload();
        player.sendMessage(config.translateColors("&aLCTrades configuration reloaded!"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            
            // Add subcommands
            List<String> subCommands = Arrays.asList("accept", "deny", "toggle", "help", "reload");
            for (String sub : subCommands) {
                if (sub.startsWith(input) && hasPermissionForSubcommand(player, sub)) {
                    completions.add(sub);
                }
            }
            
            // Add online players
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && online.getName().toLowerCase().startsWith(input)) {
                    if (!tradeManager.hasTradingDisabled(online.getUniqueId())) {
                        completions.add(online.getName());
                    }
                }
            }
            
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("deny")) {
            String input = args[1].toLowerCase();
            
            // Return players who have sent trade requests
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> {
                        if (!p.getName().toLowerCase().startsWith(input)) return false;
                        var request = tradeManager.getPendingRequest(player.getUniqueId());
                        return request != null && request.getSender().equals(p);
                    })
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private boolean hasPermissionForSubcommand(Player player, String subCommand) {
        if (subCommand.equals("reload")) {
            return player.hasPermission("lctrades.admin");
        }
        return player.hasPermission("lctrades.use");
    }
}
