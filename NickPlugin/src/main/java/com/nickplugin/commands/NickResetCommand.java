package com.nickplugin.commands;

import com.nickplugin.NickPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NickResetCommand implements CommandExecutor, TabCompleter {

    private final NickPlugin plugin;

    public NickResetCommand(NickPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage(msg("usage-nickreset")); return true; }
            if (!sender.hasPermission("nickplugin.nickreset")) { sender.sendMessage(msg("no-permission")); return true; }
            plugin.getNickManager().resetNick(player);
            player.sendMessage(msg("nick-reset"));
            return true;
        }
        if (args.length == 1) {
            if (!sender.hasPermission("nickplugin.nickreset.others")) { sender.sendMessage(msg("no-permission")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(msg("player-not-found")); return true; }
            plugin.getNickManager().resetNick(target);
            sender.sendMessage(msg("nick-reset-other").replace("%player%", target.getName()));
            target.sendMessage(msg("nick-reset"));
            return true;
        }
        sender.sendMessage(msg("usage-nickreset"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("nickplugin.nickreset.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages." + key, "&c오류가 발생했습니다."));
    }
}
