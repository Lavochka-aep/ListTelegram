package com.lavochk.listTelegram;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsoleLogReader {

    private final TelegramBot bot;
    private final long chatId;
    private final int messageId;
    private final File logFile;
    private long lastPosition = 0;
    private final StringBuilder history = new StringBuilder("`");
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int MAX_LENGTH = 3800;

    public ConsoleLogReader(TelegramBot bot, long chatId, int messageId) {
        this.bot = bot;
        this.chatId = chatId;
        this.messageId = messageId;
        this.logFile = new File(Bukkit.getWorldContainer(), "logs/latest.log");
        this.lastPosition = logFile.length(); // Start reading from the end of the file

        scheduler.scheduleAtFixedRate(this::readNewLines, 2, 2, TimeUnit.SECONDS);
    }

    private void readNewLines() {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long fileLength = logFile.length();
            if (fileLength < lastPosition) {
                // Log file was rotated or cleared
                lastPosition = 0;
            }

            if (fileLength > lastPosition) {
                raf.seek(lastPosition);
                String line;
                while ((line = raf.readLine()) != null) {
                    // Append new line to history
                    history.append(ChatColor.stripColor(new String(line.getBytes("ISO-8859-1"), "UTF-8"))).append("\n");
                }
                lastPosition = raf.getFilePointer();

                // Trim history if it's too long
                if (history.length() > MAX_LENGTH) {
                    history.delete(0, history.length() - MAX_LENGTH);
                    int firstNewline = history.indexOf("\n");
                    if (firstNewline != -1) {
                        history.delete(0, firstNewline + 1);
                    }
                }
                
                String textToSend = "🔴 **Просмотр консоли активен...**\n\n" + history.toString() + "`";
                bot.editMessage(chatId, messageId, textToSend, getStopConsoleMarkup());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        scheduler.shutdown();
    }

    private InlineKeyboardMarkup getStopConsoleMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton stopBtn = new InlineKeyboardButton("🟢 Остановить просмотр");
        stopBtn.setCallbackData("stop_console");
        row.add(stopBtn);
        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }
}
