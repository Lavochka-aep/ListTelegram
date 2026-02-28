package com.lavochk.listTelegram;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class LinkManager {

    private final File linkFile;
    private final FileConfiguration linkConfig;
    private final Map<String, Long> pendingLinks = new ConcurrentHashMap<>(); // Code -> Telegram ID

    public LinkManager(File dataFolder) {
        this.linkFile = new File(dataFolder, "links.yml");
        if (!linkFile.exists()) {
            try {
                linkFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.linkConfig = YamlConfiguration.loadConfiguration(linkFile);
    }

    public String generateLinkCode(long telegramId) {
        String code = UUID.randomUUID().toString().substring(0, 6);
        pendingLinks.put(code, telegramId);
        return code;
    }

    public boolean completeLink(String code, String playerName) {
        Long telegramId = pendingLinks.get(code);
        if (telegramId != null) {
            String normalizedName = PlayerNameUtil.normalize(playerName);
            
            // Remove any old links for this player or telegram id
            removeLink(normalizedName);
            removeLink(telegramId);

            linkConfig.set(normalizedName, telegramId);
            save();
            pendingLinks.remove(code);
            return true;
        }
        return false;
    }

    public String getLinkedPlayerName(long telegramId) {
        for (String playerName : linkConfig.getKeys(false)) {
            if (linkConfig.getLong(playerName) == telegramId) {
                return playerName;
            }
        }
        return null;
    }

    public boolean isLinked(long telegramId) {
        return getLinkedPlayerName(telegramId) != null;
    }
    
    public boolean isPlayerLinked(String playerName) {
        return linkConfig.contains(PlayerNameUtil.normalize(playerName));
    }
    
    public long getPlayerTelegramId(String playerName) {
        return linkConfig.getLong(PlayerNameUtil.normalize(playerName), -1);
    }

    public int getLinkedCount() {
        return linkConfig.getKeys(false).size();
    }

    public void forceLink(long telegramId, String playerName) {
        String normalizedName = PlayerNameUtil.normalize(playerName);
        
        // Remove any old links for this player or telegram id
        removeLink(normalizedName);
        removeLink(telegramId);

        linkConfig.set(normalizedName, telegramId);
        save();
    }

    public void removeLink(String playerName) {
        linkConfig.set(PlayerNameUtil.normalize(playerName), null);
        save();
    }

    public void removeLink(long telegramId) {
        String keyToRemove = null;
        for (String playerName : linkConfig.getKeys(false)) {
            if (linkConfig.getLong(playerName) == telegramId) {
                keyToRemove = playerName;
                break;
            }
        }
        if (keyToRemove != null) {
            linkConfig.set(keyToRemove, null);
            save();
        }
    }

    public void save() {
        try {
            linkConfig.save(linkFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
