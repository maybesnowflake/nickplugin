package com.nickplugin.commands;

import com.nickplugin.NickPlugin;
import com.nickplugin.managers.NickManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NickCommand implements CommandExecutor, TabCompleter {

    private final NickPlugin plugin;

    public NickCommand(NickPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        NickManager nm = plugin.getNickManager();

        if (args.length == 0) {
            sender.sendMessage(msg("usage-nick"));
            return true;
        }

        // /nick <닉네임>
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("콘솔에서는 /nick <닉네임> <플레이어> 로 사용하세요.");
                return true;
            }
            if (!sender.hasPermission("nickplugin.nick")) {
                sender.sendMessage(msg("no-permission")); return true;
            }
            String rawNick = processColors(sender, args[0]);
            if (!validate(sender, player, rawNick, nm)) return true;

            nm.setNick(player, rawNick);
            sender.sendMessage(msg("nick-changed").replace("%nick%",
                    ChatColor.translateAlternateColorCodes('&', rawNick)));
            return true;
        }

        // /nick <닉네임> <플레이어>
        if (args.length == 2) {
            if (!sender.hasPermission("nickplugin.nick.others")) {
                sender.sendMessage(msg("no-permission")); return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(msg("player-not-found")); return true; }

            String rawNick = processColors(sender, args[0]);
            if (!validate(sender, target, rawNick, nm)) return true;

            nm.setNick(target, rawNick);
            sender.sendMessage(msg("nick-changed-other")
                    .replace("%nick%", ChatColor.translateAlternateColorCodes('&', rawNick))
                    .replace("%player%", target.getName()));
            target.sendMessage(msg("nick-changed")
                    .replace("%nick%", ChatColor.translateAlternateColorCodes('&', rawNick)));
            return true;
        }

        sender.sendMessage(msg("usage-nick"));
        return true;
    }

    private String processColors(CommandSender sender, String nick) {
        if (!sender.hasPermission("nickplugin.nick.color")) {
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', nick));
        }
        return nick;
    }

    private boolean validate(CommandSender sender, Player target, String rawNick, NickManager nm) {
        if (sender.hasPermission("nickplugin.bypass.length") &&
            sender.hasPermission("nickplugin.bypass.filter")) return true;

        NickManager.NickResult result = nm.validateNick(target, rawNick);
        int min = plugin.getConfig().getInt("nick-min-length", 2);
        int max = plugin.getConfig().getInt("nick-max-length", 16);

        switch (result) {
            case TOO_SHORT -> { sender.sendMessage(msg("nick-too-short").replace("%min%", String.valueOf(min))); return false; }
            case TOO_LONG  -> { sender.sendMessage(msg("nick-too-long").replace("%max%", String.valueOf(max)));  return false; }
            case INVALID_CHARS -> { sender.sendMessage(msg("nick-invalid-chars")); return false; }
            case BLOCKED       -> { sender.sendMessage(msg("nick-blocked")); return false; }
            case ALREADY_USED  -> { sender.sendMessage(msg("nick-already-used")); return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && sender.hasPermission("nickplugin.nick.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages." + key, "&c오류가 발생했습니다."));
    }
}
