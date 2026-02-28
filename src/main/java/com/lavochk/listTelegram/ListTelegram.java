package com.lavochk.listTelegram;

import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public final class ListTelegram extends JavaPlugin {

    private static ListTelegram instance;
    private TelegramBot telegramBot;
    private FileLogger fileLogger;
    private WhitelistManager whitelistManager;
    private LinkManager linkManager;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        
        fileLogger = new FileLogger(getDataFolder());
        whitelistManager = new WhitelistManager(getDataFolder());
        linkManager = new LinkManager(getDataFolder());
        playerDataManager = new PlayerDataManager(this);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, whitelistManager, linkManager), this);
        getServer().getPluginManager().registerEvents(new PlayerStatisticListener(playerDataManager), this);
        getServer().getPluginManager().registerEvents(new ChatBridgeListener(this), this);

        this.getCommand("listtelegram").setExecutor(new WhitelistCommand(this));
        this.getCommand("link").setExecutor(new LinkCommand(this));

        if ("YOUR_BOT_TOKEN".equals(getConfig().getString("telegram.token"))) {
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().severe("!!! Telegram bot token is not set in config.yml!     !!!");
            getLogger().severe("!!! The bot will not work until you set a valid token. !!!");
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            return;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBot = new TelegramBot(this);
            botsApi.registerBot(telegramBot);
            getLogger().info("Telegram bot has been enabled successfully.");
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("Error removing old webhook")) {
                getLogger().info("Telegram bot enabled with a non-critical warning: " + e.getMessage());
                getLogger().info("This can usually be ignored if the bot is working correctly.");
            } else {
                getLogger().severe("Failed to enable Telegram bot: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        }
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllOnlinePlayers();
        }
        getLogger().info("ListTelegram has been disabled.");
    }

    public static ListTelegram getInstance() { return instance; }
    public TelegramBot getTelegramBot() { return telegramBot; }
    public FileLogger getFileLogger() { return fileLogger; }
    public WhitelistManager getWhitelistManager() { return whitelistManager; }
    public LinkManager getLinkManager() { return linkManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
}
