package com.lavochk.listTelegram;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class PlayerDataManager {

    private final ListTelegram plugin;
    private final File playerDataFolder;
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();

    public PlayerDataManager(ListTelegram plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    public void onPlayerJoin(Player player) {
        sessionStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
        FileConfiguration playerData = getPlayerData(player.getName());
        if (!playerData.contains("first_join")) {
            playerData.set("first_join", System.currentTimeMillis());
            savePlayerData(player.getName(), playerData);
        }
    }

    public void onPlayerQuit(Player player) {
        if (sessionStartTimes.containsKey(player.getUniqueId())) {
            long sessionStart = sessionStartTimes.remove(player.getUniqueId());
            long sessionDuration = (System.currentTimeMillis() - sessionStart) / 1000; // in seconds

            FileConfiguration playerData = getPlayerData(player.getName());

            // Update total playtime
            long totalPlaytime = playerData.getLong("total_playtime", 0);
            playerData.set("total_playtime", totalPlaytime + sessionDuration);

            // Update daily playtime
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            long dailyPlaytime = playerData.getLong("daily_playtime." + today, 0);
            playerData.set("daily_playtime." + today, dailyPlaytime + sessionDuration);
            
            // Update last seen
            playerData.set("last_join", System.currentTimeMillis());

            savePlayerData(player.getName(), playerData);
        }
    }

    public FileConfiguration getPlayerData(String playerName) {
        String normalizedName = PlayerNameUtil.normalize(playerName);
        File playerFile = new File(playerDataFolder, normalizedName + ".yml");
        if (!playerFile.exists()) {
            try {
                playerFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(playerFile);
    }

    public void savePlayerData(String playerName, FileConfiguration config) {
        String normalizedName = PlayerNameUtil.normalize(playerName);
        try {
            config.save(new File(playerDataFolder, normalizedName + ".yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void saveAllOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            onPlayerQuit(player); // Simulate a quit to save data
            onPlayerJoin(player); // Immediately start a new session
        }
    }
}
