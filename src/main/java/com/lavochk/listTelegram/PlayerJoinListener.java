package com.lavochk.listTelegram;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerJoinListener implements Listener {

    private final ListTelegram plugin;
    private final WhitelistManager whitelistManager;
    private final LinkManager linkManager;

    public PlayerJoinListener(ListTelegram plugin, WhitelistManager whitelistManager, LinkManager linkManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
        this.linkManager = linkManager;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Reload whitelist from file to catch manual edits
        whitelistManager.load();
        
        // Check Telegram login requirement
        boolean telegramLoginEnabled = plugin.getConfig().getBoolean("telegram-login.enabled", false);
        boolean telegramLoginRequired = plugin.getConfig().getBoolean("telegram-login.required", false);
        
        if (telegramLoginEnabled) {
            String playerName = event.getPlayer().getName();
            boolean isLinked = linkManager.isPlayerLinked(playerName);
            
            if (telegramLoginRequired) {
                // Telegram link is mandatory
                if (!isLinked) {
                    String botUsername = plugin.getConfig().getString("telegram.bot-username", "ваш бот");
                    String message = ChatColor.RED + "Для входа на сервер требуется привязка Telegram аккаунта!\n" +
                                     ChatColor.YELLOW + "Чтобы привязать аккаунт, найдите в Telegram бота: @" + botUsername + "\n" +
                                     ChatColor.GRAY + "Используйте команду /link в игре для получения кода.";
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message);
                    return;
                }
            }
        }
        
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
