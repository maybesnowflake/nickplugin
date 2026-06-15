package com.nickplugin.commands;

import com.nickplugin.NickPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class NickListCommand implements CommandExecutor {

    private final NickPlugin plugin;

    public NickListCommand(NickPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nickplugin.nicklist")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        Map<UUID, String> all = plugin.getNickManager().getAllNicknames();
        sender.sendMessage(ChatColor.GOLD + "━━━ 닉네임 목록 (" + all.size() + "명) ━━━");
        if (all.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  설정된 닉네임이 없습니다.");
        } else {
            for (Map.Entry<UUID, String> entry : all.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                String realName = (p != null) ? p.getName() : entry.getKey().toString().substring(0, 8) + "...";
                String status = (p != null) ? ChatColor.GREEN + "●" : ChatColor.GRAY + "●";
                sender.sendMessage(status + ChatColor.GRAY + " " + realName
                        + ChatColor.YELLOW + " → "
                        + ChatColor.translateAlternateColorCodes('&', entry.getValue()));
            }
        }
        sender.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }
}
