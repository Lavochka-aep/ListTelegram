package com.lavochk.listTelegram;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerJoinListener implements Listener {

    private final ListTelegram plugin;
    private final WhitelistManager whitelistManager;

    public PlayerJoinListener(ListTelegram plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Reload whitelist from file to catch manual edits
        whitelistManager.load();
        
        if (!plugin.getConfig().getBoolean("whitelist.enabled", true)) {
            return;
        }

        // Use the normalized name for the check
        if (!whitelistManager.isWhitelisted(event.getPlayer().getName())) {
            String botUsername = plugin.getConfig().getString("telegram.bot-username", "ваш бот");
            String message = ChatColor.RED + "Вас нет в белом списке этого сервера.\n" +
                             ChatColor.YELLOW + "Чтобы подать заявку, найдите в Telegram бота: @" + botUsername;
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);
        }
    }
}
