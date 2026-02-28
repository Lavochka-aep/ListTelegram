package com.lavochk.listTelegram;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerStatusManager {

    private final TelegramBot bot;
    private final String chatBridgeGroupId;
    private Integer statusMessageId = null;
    private int updateIntervalSeconds = 60;

    public ServerStatusManager(TelegramBot bot, String chatBridgeGroupId) {
        this.bot = bot;
        this.chatBridgeGroupId = chatBridgeGroupId;
    }

    public void startStatusUpdates() {
        if (chatBridgeGroupId == null || chatBridgeGroupId.isEmpty()) {
            return;
        }

        // Сначала отправляем сообщение
        sendStatusMessage();

        // Запускаем периодическое обновление
        Bukkit.getScheduler().runTaskTimerAsynchronously(ListTelegram.getInstance(), this::updateStatusMessage, 
            20L * updateIntervalSeconds, 20L * updateIntervalSeconds);
    }

    private void sendStatusMessage() {
        try {
            SendMessage message = new SendMessage(chatBridgeGroupId, generateStatusText());
            message.setParseMode("HTML");
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = bot.execute(message);
            
            statusMessageId = sentMessage.getMessageId();
            
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void updateStatusMessage() {
        if (statusMessageId == null) {
            sendStatusMessage();
            return;
        }

        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatBridgeGroupId);
            editMessage.setMessageId(statusMessageId);
            editMessage.setText(generateStatusText());
            editMessage.setParseMode("HTML");
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            // Если сообщение не найдено, отправляем новое
            if (e.getMessage() != null && e.getMessage().contains("message to edit not found")) {
                statusMessageId = null;
                sendStatusMessage();
            } else {
                e.printStackTrace();
            }
        }
    }

    private String generateStatusText() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        
        // TPS
        double tps = getTPS();
        String tpsStatus = getTPSStatus(tps);
        
        // Память
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        int memoryPercent = (int) ((usedMemory * 100) / maxMemory);
        String memoryStatus = getMemoryStatus(memoryPercent);
        
        // Нагрузка CPU
        double cpuLoad = getCPULoad();
        String cpuStatus = getCPUStatus(cpuLoad);
        
        // Время
        String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
        
        // Статус сервера
        String serverStatus = tps > 18 ? "🟢 Отлично" : (tps > 15 ? "🟡 Нормально" : "🔴 Нагружен");
        
        return String.format(
            "📊 <b>Статус сервера</b>\n\n" +
            "🎮 <b>Онлайн:</b> %d / %d игроков\n" +
            "⚡ <b>TPS:</b> %.1f %s\n" +
            "💾 <b>Память:</b> %d / %d МБ (%d%%) %s\n" +
            "🖥️ <b>CPU:</b> %.1f%% %s\n" +
            "📡 <b>Состояние:</b> %s\n\n" +
            "🕐 Обновлено: %s",
            onlinePlayers, maxPlayers,
            tps, tpsStatus,
            usedMemory, maxMemory, memoryPercent, memoryStatus,
            cpuLoad, cpuStatus,
            serverStatus,
            currentTime
        );
    }

    private double getTPS() {
        try {
            // Используем отражение для получения TPS из Paper/Spigot
            Server server = Bukkit.getServer();
            java.lang.reflect.Method tpsMethod = server.getClass().getMethod("getTPS");
            if (tpsMethod != null) {
                double[] tpsArray = (double[]) tpsMethod.invoke(server);
                return tpsArray[0]; // TPS за последнюю минуту
            }
        } catch (Exception e) {
            // Игнорируем, если метод недоступен
        }
        return 20.0; // Дефолтное значение
    }

    private String getTPSStatus(double tps) {
        if (tps >= 19) return "✅";
        if (tps >= 15) return "⚠️";
        return "❌";
    }

    private String getMemoryStatus(int percent) {
        if (percent < 60) return "✅";
        if (percent < 80) return "⚠️";
        return "❌";
    }

    private double getCPULoad() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
            }
            return osBean.getSystemLoadAverage();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getCPUStatus(double load) {
        if (load < 50) return "✅";
        if (load < 80) return "⚠️";
        return "❌";
    }

    public void forceUpdate() {
        updateStatusMessage();
    }
}
