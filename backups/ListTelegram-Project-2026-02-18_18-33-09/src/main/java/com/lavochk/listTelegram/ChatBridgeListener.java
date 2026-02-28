package com.lavochk.listTelegram;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Random;

public class ChatBridgeListener implements Listener {

    private final ListTelegram plugin;

    public ChatBridgeListener(ListTelegram plugin) {
        this.plugin = plugin;
    }

    // Проверка активности Chat Bridge
    private boolean isBridgeEnabled() {
        String chatBridgeGroupId = plugin.getConfig().getString("telegram.chat-bridge-group-id");
        return chatBridgeGroupId != null && !chatBridgeGroupId.isEmpty();
    }

    // ==================== ЧАТ ====================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!isBridgeEnabled()) return;
        if (event.isCancelled()) return;
        if (event.getMessage().startsWith("[Telegram]")) return;

        String playerName = event.getPlayer().getName();
        String message = event.getMessage();

        // Форматируем сообщение для Telegram (без эмодзи)
        String formattedMessage = formatChatMessage(playerName, message);
        plugin.getTelegramBot().sendMessageToBridge(formattedMessage);
    }

    private String formatChatMessage(String playerName, String message) {
        // Экранируем HTML-символы в сообщении
        String escapedMessage = escapeHtml(message);
        // Формат: [PlayerName] : сообщение
        return String.format("<b>[%s]</b> : %s", playerName, escapedMessage);
    }

    // ==================== ВХОД ИГРОКА ====================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isBridgeEnabled()) return;

        Player player = event.getPlayer();
        int onlineCount = Bukkit.getOnlinePlayers().size();

        // Сообщение о входе
        String joinMessage = formatJoinMessage(player.getName(), onlineCount);
        plugin.getTelegramBot().sendMessageToBridge(joinMessage);
        
        // Обновляем статус сервера
        plugin.getTelegramBot().updateServerStatus();
    }

    private String formatJoinMessage(String playerName, int onlineCount) {
        return String.format(
            "👋 <b>%s</b> зашёл на сервер\n" +
            "└ Онлайн: <b>%d</b> игрок%s",
            playerName, onlineCount, getPluralForm(onlineCount)
        );
    }

    // ==================== ВЫХОД ИГРОКА ====================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isBridgeEnabled()) return;

        Player player = event.getPlayer();
        // Онлайн после выхода (игрок еще в списке, поэтому -1)
        int onlineCount = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);

        String quitMessage = formatQuitMessage(player.getName(), onlineCount);
        plugin.getTelegramBot().sendMessageToBridge(quitMessage);
        
        // Обновляем статус сервера
        plugin.getTelegramBot().updateServerStatus();
    }

    private String formatQuitMessage(String playerName, int onlineCount) {
        return String.format(
            "💨 <b>%s</b> вышел с сервера\n" +
            "└ Онлайн: <b>%d</b> игрок%s",
            playerName, onlineCount, getPluralForm(onlineCount)
        );
    }

    // ==================== СМЕРТЬ ИГРОКА ====================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isBridgeEnabled()) return;

        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();

        // Форматируем сообщение о смерти
        String formattedDeath = formatDeathMessage(player.getName(), deathMessage);
        plugin.getTelegramBot().sendMessageToBridge(formattedDeath);
    }

    private String formatDeathMessage(String playerName, String originalMessage) {
        // Определяем тип смерти для эмодзи
        String emoji = getDeathEmoji(originalMessage);
        
        // Убираем стандартное "Player was" и делаем красивее
        String cleanMessage = originalMessage
            .replaceFirst(playerName + " ", "")
            .replace("was ", "")
            .replace("by ", "от ");

        return String.format(
            "%s <b>%s</b> %s",
            emoji, playerName, escapeHtml(cleanMessage)
        );
    }

    private String getDeathEmoji(String deathMessage) {
        String msg = deathMessage.toLowerCase();
        
        if (msg.contains("zombie") || msg.contains("skeleton") || msg.contains("husk") || 
            msg.contains("stray") || msg.contains("drowned") || msg.contains("zoglin")) {
            return "🧟";
        }
        if (msg.contains("creeper")) {
            return "💥";
        }
        if (msg.contains("ender") || msg.contains("dragon") || msg.contains("shulker")) {
            return "🐉";
        }
        if (msg.contains("spider") || msg.contains("cave spider")) {
            return "🕷️";
        }
        if (msg.contains("blaze") || msg.contains("ghast") || msg.contains("magma")) {
            return "🔥";
        }
        if (msg.contains("wither") || msg.contains("wither skeleton")) {
            return "💀";
        }
        if (msg.contains("warden")) {
            return "👁️";
        }
        if (msg.contains("piglin") || msg.contains("hoglin")) {
            return "🐷";
        }
        if (msg.contains("iron golem") || msg.contains("snow golem")) {
            return "🤖";
        }
        if (msg.contains("wolf") || msg.contains("dog")) {
            return "🐺";
        }
        if (msg.contains("bee")) {
            return "🐝";
        }
        if (msg.contains("player") || msg.contains("slain")) {
            return "⚔️";
        }
        if (msg.contains("lava")) {
            return "🌋";
        }
        if (msg.contains("drowned") || msg.contains("water")) {
            return "🌊";
        }
        if (msg.contains("fell") || msg.contains("height") || msg.contains("ground")) {
            return "📉";
        }
        if (msg.contains("fire") || msg.contains("burned") || msg.contains("flames")) {
            return "🔥";
        }
        if (msg.contains("explosion") || msg.contains("exploded") || msg.contains("tnt")) {
            return "💥";
        }
        if (msg.contains("suffocated") || msg.contains("wall") || msg.contains("block")) {
            return "🧱";
        }
        if (msg.contains("starved") || msg.contains("hunger")) {
            return "🍖";
        }
        if (msg.contains("magic") || msg.contains("potion") || msg.contains("spell")) {
            return "✨";
        }
        if (msg.contains("thorns") || msg.contains("cactus")) {
            return "🌵";
        }
        if (msg.contains("lightning")) {
            return "⚡";
        }
        if (msg.contains("void")) {
            return "🕳️";
        }
        if (msg.contains("berry") || msg.contains("sweet")) {
            return "🫐";
        }
        if (msg.contains("freeze") || msg.contains("frozen") || msg.contains("powder snow")) {
            return "❄️";
        }
        if (msg.contains("trident")) {
            return "🔱";
        }
        
        // Дефолтный эмодзи
        return "💀";
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&")
            .replace("<", "<")
            .replace(">", ">");
    }

    private String getPluralForm(int count) {
        // Русское склонение: игрок, игрока, игроков
        int mod10 = count % 10;
        int mod100 = count % 100;
        
        if (mod100 >= 11 && mod100 <= 19) {
            return "ов";
        }
        if (mod10 == 1) {
            return "";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "а";
        }
        return "ов";
    }
}
