package com.lavochk.listTelegram;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class WhitelistCommand implements CommandExecutor {

    private final ListTelegram plugin;

    public WhitelistCommand(ListTelegram plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                if (!sender.hasPermission("listtelegram.help")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                sendHelpMessage(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("listtelegram.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getWhitelistManager().load();
                sender.sendMessage(ChatColor.GREEN + "ListTelegram configuration and whitelist reloaded.");
                return true;

            case "importstats":
                if (!sender.hasPermission("listtelegram.importstats")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                StatImporter importer = new StatImporter(plugin);
                importer.importStats(sender);
                return true;

            case "importvanilla":
                if (!sender.hasPermission("listtelegram.importvanilla")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                importVanillaWhitelist(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /" + label + " help for a list of commands.");
                return false;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ListTelegram Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/lt help" + ChatColor.WHITE + " - Показать это сообщение");
        sender.sendMessage(ChatColor.YELLOW + "/lt reload" + ChatColor.WHITE + " - Перезагрузить конфиг и вайтлист");
        sender.sendMessage(ChatColor.YELLOW + "/lt importstats" + ChatColor.WHITE + " - Импортировать старую статистику игроков");
        sender.sendMessage(ChatColor.YELLOW + "/lt importvanilla" + ChatColor.WHITE + " - Импортировать игроков из whitelist.json");
        sender.sendMessage(ChatColor.YELLOW + "/link <code>" + ChatColor.WHITE + " - Привязать игровой аккаунт к Telegram");
        sender.sendMessage(ChatColor.GOLD + "-------------------------");
    }

    private void importVanillaWhitelist(CommandSender sender) {
        File vanillaWhitelistFile = new File(Bukkit.getServer().getWorldContainer(), "whitelist.json");
        if (!vanillaWhitelistFile.exists()) {
            sender.sendMessage(ChatColor.RED + "Файл whitelist.json не найден.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Начинаю импорт из whitelist.json...");
        try (FileReader reader = new FileReader(vanillaWhitelistFile)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<Map<String, String>>>() {}.getType();
            List<Map<String, String>> players = gson.fromJson(reader, listType);

            if (players == null || players.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "whitelist.json пуст. Нечего импортировать.");
                return;
            }

            int count = 0;
            for (Map<String, String> playerEntry : players) {
                String name = playerEntry.get("name");
                if (name != null) {
                    plugin.getWhitelistManager().addPlayer(name);
                    count++;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Импорт завершен! Добавлено " + count + " игроков в whitelist.txt.");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Произошла ошибка при чтении whitelist.json: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
