package com.nickplugin.listeners;

import com.nickplugin.NickPlugin;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final NickPlugin plugin;

    public PlayerListener(NickPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        // 접속 후 1틱 뒤 닉네임 적용 (스폰 패킷 이후)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getNickManager().applyNick(event.getPlayer());
        }, 5L);

        String nick = plugin.getNickManager().getNick(event.getPlayer());
        event.setJoinMessage(ChatColor.YELLOW + ChatColor.translateAlternateColorCodes('&', nick)
                + ChatColor.YELLOW + " 님이 접속했습니다.");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        String nick = plugin.getNickManager().getNick(event.getPlayer());
        event.setQuitMessage(ChatColor.YELLOW + ChatColor.translateAlternateColorCodes('&', nick)
                + ChatColor.YELLOW + " 님이 퇴장했습니다.");
    }
}
