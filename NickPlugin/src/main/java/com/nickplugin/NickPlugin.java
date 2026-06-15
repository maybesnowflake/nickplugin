package com.nickplugin;

import com.nickplugin.commands.NickCommand;
import com.nickplugin.commands.NickListCommand;
import com.nickplugin.commands.NickResetCommand;
import com.nickplugin.listeners.ChatListener;
import com.nickplugin.listeners.PlayerListener;
import com.nickplugin.managers.NickManager;
import org.bukkit.plugin.java.JavaPlugin;

public class NickPlugin extends JavaPlugin {

    private static NickPlugin instance;
    private NickManager nickManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        nickManager = new NickManager(this);
        nickManager.loadNicknames();

        getCommand("nick").setExecutor(new NickCommand(this));
        getCommand("nickreset").setExecutor(new NickResetCommand(this));
        getCommand("nicklist").setExecutor(new NickListCommand(this));

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // 이미 온라인인 플레이어 적용 (리로드 대비)
        getServer().getOnlinePlayers().forEach(p -> nickManager.applyNick(p));

        getLogger().info("NickPlugin 활성화 완료!");
    }

    @Override
    public void onDisable() {
        if (nickManager != null) nickManager.saveNicknames();
        getLogger().info("NickPlugin 비활성화.");
    }

    public static NickPlugin getInstance() { return instance; }
    public NickManager getNickManager() { return nickManager; }
}
