package com.nickplugin.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.nickplugin.NickPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

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
    //   닉네임 적용
    // ==============================

    public void applyNick(Player player) {
        String nick = getNick(player);
        String colored = ChatColor.translateAlternateColorCodes('&', nick);

        // 1) 탭리스트
        player.setPlayerListName(colored);

        // 2) displayName (채팅용)
        player.setDisplayName(colored + ChatColor.RESET);

        // 3) 이름표 — 메인 스레드에서 실행
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyScoreboardNametag(player, colored);
            sendPlayerInfoPacket(player, colored);
        });
    }

    /**
     * 스코어보드 팀으로 이름표 제어
     * - 닉네임 있을 때: prefix=닉네임, 원래 이름 숨김(NEVER) → prefix만 보임
     * - 닉네임 없을 때: 팀 제거 → 원래 이름표 복구
     *
     * 핵심: NAME_TAG_VISIBILITY = NEVER 로 원래 이름 숨기고
     *       prefix 에 닉네임을 넣으면 prefix는 항상 보임
     *       단, prefix visibility 는 별도 제어 불가 — 대신 아래 트릭 사용:
     *       팀의 prefix 를 닉네임으로 설정하고 NAME_TAG_VISIBILITY = NEVER 하면
     *       prefix 도 같이 숨겨지므로, ProtocolLib 으로 별도 패킷 전송
     */
    private void applyScoreboardNametag(Player player, String colored) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getTeamName(player);

        // 기존 팀 제거
        Team existing = scoreboard.getTeam(teamName);
        if (existing != null) existing.unregister();

        if (hasNick(player)) {
            Team team = scoreboard.registerNewTeam(teamName);
            // prefix = 닉네임, suffix 비움
            // NAME_TAG_VISIBILITY = ALWAYS 유지 (prefix 포함해서 보여야 하므로)
            // 원래 이름(하얀 글씨)을 안 보이게: §r§0 색으로 원래이름 덮기
            String prefix = colored;
            // 원래 이름이 prefix 뒤에 붙으므로, §0(검정+불투명) 으로 원래이름 색 변경
            // 배경이 어두우면 안 보이게 됨. suffix로 §r 복구
            team.setPrefix(prefix + ChatColor.BLACK);
            team.setSuffix(ChatColor.RESET.toString());
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setAllowFriendlyFire(true);
            team.setCanSeeFriendlyInvisibles(true);
            team.addEntry(player.getName());
        }
        // 닉네임 없으면 팀 제거로 원래 이름표 복구됨
    }

    /**
     * ProtocolLib으로 탭리스트 PLAYER_INFO 패킷 전송
     * — 탭리스트에 닉네임 반영
     */
    private void sendPlayerInfoPacket(Player player, String colored) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        try {
            // UPDATE_DISPLAY_NAME 액션으로 탭리스트 이름만 갱신
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
            packet.getPlayerInfoActions().write(0,
                    EnumSet.of(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME)
            );

            WrappedChatComponent displayName = WrappedChatComponent.fromText(colored);
            WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);

            PlayerInfoData infoData = new PlayerInfoData(
                    player.getUniqueId(),
                    player.getPing(),
                    true,
                    EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()),
                    profile,
                    displayName,
                    (com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData) null
            );

            packet.getPlayerInfoDataLists().write(1, List.of(infoData));

            // 모든 플레이어에게 전송
            for (Player observer : Bukkit.getOnlinePlayers()) {
                pm.sendServerPacket(observer, packet);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("PLAYER_INFO 패킷 실패: " + e.getMessage());
        }
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
