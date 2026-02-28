package com.lavochk.listTelegram;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.FileReader;
import java.util.UUID;

public class StatImporter {

    private final ListTelegram plugin;
    private final PlayerDataManager playerDataManager;
    private final Gson gson = new Gson();

    public StatImporter(ListTelegram plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    public void importStats(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sender.sendMessage(ChatColor.YELLOW + "Начинаю импорт статистики из файлов Minecraft...");
            plugin.getLogger().info("Starting legacy stats import...");

            File worldDir = Bukkit.getServer().getWorlds().get(0).getWorldFolder();
            File statsDir = new File(worldDir, "stats");

            if (!statsDir.exists() || !statsDir.isDirectory()) {
                sender.sendMessage(ChatColor.RED + "Папка 'stats' не найдена. Не могу импортировать.");
                plugin.getLogger().warning("Stats directory not found. Import aborted.");
                return;
            }

            File[] statFiles = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (statFiles == null || statFiles.length == 0) {
                sender.sendMessage(ChatColor.RED + "Файлы статистики не найдены.");
                plugin.getLogger().warning("No stat files found. Import aborted.");
                return;
            }

            int importedCount = 0;
            int updatedCount = 0;

            for (File statFile : statFiles) {
                try {
                    String fileName = statFile.getName();
                    UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 5)); // remove .json
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    String playerName = player.getName();

                    if (playerName == null) {
                        // Player has stats but maybe never joined this server, or name is not resolvable.
                        continue;
                    }

                    try (FileReader reader = new FileReader(statFile)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("stats") && json.getAsJsonObject("stats").has("minecraft:custom")
                                && json.getAsJsonObject("stats").getAsJsonObject("minecraft:custom").has("minecraft:play_time")) {

                            long playTimeTicks = json.getAsJsonObject("stats").getAsJsonObject("minecraft:custom").get("minecraft:play_time").getAsLong();
                            long playTimeSeconds = playTimeTicks / 20;

                            FileConfiguration playerData = playerDataManager.getPlayerData(playerName);
                            long currentPlaytime = playerData.getLong("total_playtime", 0);

                            if (playTimeSeconds > currentPlaytime) {
                                playerData.set("total_playtime", playTimeSeconds);
                                playerDataManager.savePlayerData(playerName, playerData);
                                updatedCount++;
                            }
                            importedCount++;
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to process stat file: " + statFile.getName() + ". Error: " + e.getMessage());
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Импорт завершен!");
            sender.sendMessage(ChatColor.GREEN + "Обработано файлов: " + importedCount);
            sender.sendMessage(ChatColor.GREEN + "Обновлено игроков: " + updatedCount);
            plugin.getLogger().info("Legacy stats import finished. Processed files: " + importedCount + ", Updated players: " + updatedCount);
        });
    }
}
