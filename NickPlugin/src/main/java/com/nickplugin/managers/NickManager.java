package com.nickplugin.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.nickplugin.NickPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class NickManager {

    private final NickPlugin plugin;
    private final Map<UUID, String> nicknames = new HashMap<>();
    private File nickFile;
    private FileConfiguration nickConfig;

    private static final Pattern VALID_NICK_PATTERN = Pattern.compile("^[a-zA-Z0-9_가-힣]+$");
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("(?i)&[0-9A-FK-ORX]|§[0-9A-FK-ORX]");

    public NickManager(NickPlugin plugin) {
        this.plugin = plugin;
        setupNickFile();
    }

    // ==============================
    //   파일 관리
    // ==============================

    private void setupNickFile() {
        nickFile = new File(plugin.getDataFolder(), "nicknames.yml");
        if (!nickFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                nickFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("nicknames.yml 생성 실패: " + e.getMessage());
            }
        }
        nickConfig = YamlConfiguration.loadConfiguration(nickFile);
    }

    public void loadNicknames() {
        nicknames.clear();
        if (nickConfig.getConfigurationSection("nicknames") != null) {
            for (String uuidStr : nickConfig.getConfigurationSection("nicknames").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String nick = nickConfig.getString("nicknames." + uuidStr);
                    if (nick != null && !nick.isEmpty()) nicknames.put(uuid, nick);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("닉네임 " + nicknames.size() + "개 로드 완료.");
    }

    public void saveNicknames() {
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            nickConfig.set("nicknames." + entry.getKey(), entry.getValue());
        }
        if (nickConfig.getConfigurationSection("nicknames") != null) {
            for (String uuidStr : nickConfig.getConfigurationSection("nicknames").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (!nicknames.containsKey(uuid)) nickConfig.set("nicknames." + uuidStr, null);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        try {
            nickConfig.save(nickFile);
        } catch (IOException e) {
            plugin.getLogger().severe("닉네임 저장 실패: " + e.getMessage());
        }
    }

    // ==============================
    //   닉네임 CRUD
    // ==============================

    public void setNick(Player player, String rawNick) {
        String colored = ChatColor.translateAlternateColorCodes('&', rawNick);
        nicknames.put(player.getUniqueId(), colored);
        applyNick(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNicknames);
    }

    public void resetNick(Player player) {
        nicknames.remove(player.getUniqueId());
        applyNick(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNicknames);
    }

    public String getNick(Player player) {
        String nick = nicknames.get(player.getUniqueId());
        return (nick != null) ? nick : player.getName();
    }

    public boolean hasNick(Player player) {
        return nicknames.containsKey(player.getUniqueId());
    }

    public Map<UUID, String> getAllNicknames() {
        return Collections.unmodifiableMap(nicknames);
    }

    // ==============================
    //   닉네임 적용 (ProtocolLib)
    // ==============================

    public void applyNick(Player player) {
        String nick = getNick(player);

        // 탭리스트 이름
        player.setPlayerListName(ChatColor.translateAlternateColorCodes('&', nick));
        // displayName (채팅용)
        player.setDisplayName(ChatColor.translateAlternateColorCodes('&', nick) + ChatColor.RESET);

        // ProtocolLib으로 이름표 패킷 전송
        // 메인 스레드에서 실행 보장
        Bukkit.getScheduler().runTask(plugin, () -> sendNamePackets(player, nick));
    }

    private void sendNamePackets(Player player, String nick) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // 모든 온라인 플레이어에게 전송 (본인 포함)
        for (Player observer : Bukkit.getOnlinePlayers()) {
            try {
                // 1) PLAYER_INFO_REMOVE — 기존 탭리스트 항목 제거
                PacketContainer removePacket = pm.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                removePacket.getUUIDLists().write(0, List.of(player.getUniqueId()));
                pm.sendServerPacket(observer, removePacket);

                // 2) PLAYER_INFO — 새 닉네임으로 탭리스트 항목 추가
                PacketContainer addPacket = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
                addPacket.getPlayerInfoActions().write(0,
                        EnumSet.of(
                                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                                EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
                                EnumWrappers.PlayerInfoAction.UPDATE_LISTED
                        )
                );

                WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);
                WrappedChatComponent displayName = WrappedChatComponent.fromText(
                        ChatColor.translateAlternateColorCodes('&', nick)
                );

                PlayerInfoData infoData = new PlayerInfoData(
                        player.getUniqueId(),
                        player.getPing(),
                        true,
                        EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()),
                        profile,
                        displayName,
                        null
                );
                addPacket.getPlayerInfoDataLists().write(1, List.of(infoData));
                pm.sendServerPacket(observer, addPacket);

                // 3) ENTITY_METADATA — 이름표(네임태그) 변경
                // CustomName 을 설정하고 CustomNameVisible=true
                // Paper API로 직접 처리
            } catch (Exception e) {
                plugin.getLogger().warning("패킷 전송 실패 (" + observer.getName() + "): " + e.getMessage());
            }
        }

        // 이름표는 CustomName 방식으로 처리 (패킷보다 안정적)
        applyNameTag(player, nick);
    }

    private void applyNameTag(Player player, String nick) {
        // Paper API: CustomName으로 네임태그 변경
        // net.kyori.adventure.text 사용
        if (hasNick(player)) {
            net.kyori.adventure.text.Component nameComponent =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand()
                            .deserialize(nick);
            player.customName(nameComponent);
            player.setCustomNameVisible(true);

            // 기본 이름표 숨기기 — 스코어보드 팀으로 원래 이름표 숨김
            hideDefaultNameTag(player);
        } else {
            player.customName(null);
            player.setCustomNameVisible(false);
            showDefaultNameTag(player);
        }
    }

    private void hideDefaultNameTag(Player player) {
        // 스코어보드 팀으로 원래 이름표(위에 뜨는 하얀 이름) 숨기기
        var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getTeamName(player);

        var existing = scoreboard.getTeam(teamName);
        if (existing != null) existing.unregister();

        var team = scoreboard.registerNewTeam(teamName);
        // 이름표 완전 숨김
        team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        team.addEntry(player.getName());
    }

    private void showDefaultNameTag(Player player) {
        var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getTeamName(player);
        var existing = scoreboard.getTeam(teamName);
        if (existing != null) existing.unregister();
    }

    // ==============================
    //   검증
    // ==============================

    public enum NickResult {
        OK, TOO_SHORT, TOO_LONG, INVALID_CHARS, BLOCKED, ALREADY_USED
    }

    public NickResult validateNick(Player player, String rawNick) {
        String stripped = COLOR_STRIP_PATTERN.matcher(rawNick).replaceAll("");
        int minLen = plugin.getConfig().getInt("nick-min-length", 2);
        int maxLen = plugin.getConfig().getInt("nick-max-length", 16);

        if (stripped.length() < minLen) return NickResult.TOO_SHORT;
        if (stripped.length() > maxLen) return NickResult.TOO_LONG;

        boolean allowSpecial = plugin.getConfig().getBoolean("allow-special-chars", false);
        if (!allowSpecial && !VALID_NICK_PATTERN.matcher(stripped).matches())
            return NickResult.INVALID_CHARS;

        List<String> blocked = plugin.getConfig().getStringList("blocked-nicks");
        if (blocked.stream().anyMatch(b -> stripped.equalsIgnoreCase(b)))
            return NickResult.BLOCKED;

        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            if (entry.getKey().equals(player.getUniqueId())) continue;
            if (ChatColor.stripColor(entry.getValue()).equalsIgnoreCase(stripped))
                return NickResult.ALREADY_USED;
        }
        return NickResult.OK;
    }

    // ==============================
    //   채팅 포맷
    // ==============================

    public String formatChatMessage(Player player, String message) {
        String nick = getNick(player);
        String format = plugin.getConfig().getString("chat-format", "&7[&f%nick%&7] &f%message%");
        return ChatColor.translateAlternateColorCodes('&',
                format.replace("%nick%", nick)
                      .replace("%realname%", player.getName())
                      .replace("%message%", message)
                      .replace("%prefix%", "")
                      .replace("%suffix%", "")
        );
    }

    // ==============================
    //   유틸
    // ==============================

    private String getTeamName(Player player) {
        return "np_" + player.getUniqueId().toString().replace("-", "").substring(0, 13);
    }
}
