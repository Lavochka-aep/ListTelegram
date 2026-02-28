package com.lavochk.listTelegram;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WhitelistManager {

    private final File whitelistFile;
    private List<String> whitelistedPlayers;

    public WhitelistManager(File dataFolder) {
        this.whitelistFile = new File(dataFolder, "whitelist.txt");
        if (!whitelistFile.exists()) {
            try {
                whitelistFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        load();
    }

    public void load() {
        try {
            this.whitelistedPlayers = Files.readAllLines(whitelistFile.toPath()).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(PlayerNameUtil::normalize) // Normalize names on load
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            this.whitelistedPlayers = Collections.emptyList();
        }
    }

    public boolean isWhitelisted(String playerName) {
        String normalizedName = PlayerNameUtil.normalize(playerName);
        return whitelistedPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(normalizedName));
    }

    public void addPlayer(String playerName) {
        String normalizedName = PlayerNameUtil.normalize(playerName);
        if (!isWhitelisted(normalizedName)) {
            // Read the raw list, add the new name, and write back to preserve comments and formatting
            try {
                List<String> rawLines = Files.readAllLines(whitelistFile.toPath());
                rawLines.add(normalizedName);
                Files.write(whitelistFile.toPath(), rawLines);
                load(); // Reload the normalized list
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void removePlayer(String playerName) {
        String normalizedName = PlayerNameUtil.normalize(playerName);
        if (isWhitelisted(normalizedName)) {
            whitelistedPlayers.removeIf(p -> p.equalsIgnoreCase(normalizedName));
            try {
                Files.write(whitelistFile.toPath(), whitelistedPlayers);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getWhitelistedPlayers() {
        return Collections.unmodifiableList(whitelistedPlayers);
    }
}
