package com.lavochk.listTelegram;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {

    private final ListTelegram plugin;

    public LinkCommand(ListTelegram plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /link <code>");
            return false;
        }

        Player player = (Player) sender;
        String code = args[0];

        if (plugin.getLinkManager().completeLink(code, player.getName())) {
            player.sendMessage(ChatColor.GREEN + "✅ Ваш аккаунт успешно привязан к Telegram!");
        } else {
            player.sendMessage(ChatColor.RED + "❌ Неверный или истекший код привязки.");
        }

        return true;
    }
}
