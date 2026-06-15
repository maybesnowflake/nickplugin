package com.nickplugin.listeners;

import com.nickplugin.NickPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final NickPlugin plugin;

    public ChatListener(NickPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String formatted = plugin.getNickManager()
                .formatChatMessage(event.getPlayer(), event.getMessage());

        event.setCancelled(true);

        for (Player recipient : event.getRecipients()) {
            recipient.sendMessage(formatted);
        }
        plugin.getServer().getConsoleSender().sendMessage(formatted);
    }
}
