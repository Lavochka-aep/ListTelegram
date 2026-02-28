package com.lavochk.listTelegram;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TelegramBot extends TelegramLongPollingBot {

    private final ListTelegram plugin;
    private final String adminChatId;
    private final List<String> moderatorChatIds;
    private final String chatBridgeGroupId;
    private ConsoleLogReader logReader;
    private boolean isBridgeChatValid = true;
    private ServerStatusManager serverStatusManager;

    private enum UserState {
        AWAITING_APP_NICKNAME, AWAITING_APP_AGE, AWAITING_APP_PLANS, AWAITING_APP_LINK,
        AWAITING_REPORT_OWN_NICK, AWAITING_REPORT_TARGET_NICK, AWAITING_REPORT_SITUATION,
        AWAITING_BAN_NICKNAME,
        AWAITING_APP_REPLY, AWAITING_REPORT_REPLY
    }
    private final Map<Long, UserState> userStates = new HashMap<>();
    private final Map<Long, Object> userForms = new HashMap<>();
    private final Map<Long, Long> replyTargets = new HashMap<>(); // chatId -> targetUserId

    public TelegramBot(ListTelegram plugin) {
        super(plugin.getConfig().getString("telegram.token"));
        this.plugin = plugin;
        this.adminChatId = plugin.getConfig().getString("telegram.admin-chat-id");
        this.moderatorChatIds = plugin.getConfig().getStringList("telegram.moderator-chat-ids");
        this.chatBridgeGroupId = plugin.getConfig().getString("telegram.chat-bridge-group-id");
        
        // Запускаем менеджер статуса сервера
        this.serverStatusManager = new ServerStatusManager(this, chatBridgeGroupId);
        this.serverStatusManager.startStatusUpdates();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleTextMessage(Message message) {
        long chatId = message.getChatId();
        String text = message.getText();

        if (String.valueOf(chatId).equals(chatBridgeGroupId) && !text.startsWith("[MC]")) {
            String senderName = message.getFrom().getFirstName();
            String lastName = message.getFrom().getLastName();
            String fullName = lastName != null && !lastName.isEmpty() ? senderName + " " + lastName : senderName;
            String username = message.getFrom().getUserName();
            
            // Красивое форматирование для игры
            String displayName = username != null ? "@" + username : fullName;
            String bridgeMessage = ChatColor.GOLD + "☎ " + 
                                   ChatColor.AQUA + "[Telegram] " + 
                                   ChatColor.YELLOW + displayName + 
                                   ChatColor.GRAY + ": " + 
                                   ChatColor.WHITE + text;
            Bukkit.getServer().broadcastMessage(bridgeMessage);
            return;
        }

        if (userStates.containsKey(chatId)) {
            processFormStep(chatId, text, message.getFrom());
            return;
        }
        
        if (isAdmin(chatId) && text.startsWith("!")) {
            String command = text.substring(1);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            sendMessage(chatId, "✅ Команда `/" + command + "` отправлена на выполнение.");
            return;
        }

        switch (text) {
            case "/start": case "⬅️ Назад в меню": sendMainMenu(chatId); break;
            case "📝 Подать заявку": startApplication(chatId, message.getFrom()); break;
            case "🔗 Привязать аккаунт": startLinking(chatId); break;
            case "👤 Мой профиль": showProfile(chatId); break;
            case "🚨 Подать жалобу": startReport(chatId, message.getFrom()); break;
            case "👑 Админ-меню": if (isAdmin(chatId)) sendAdminMenu(chatId); break;
            case "📋 Показать вайтлист": case "📋 Вайтлист": if (isAdmin(chatId)) showWhitelist(chatId); break;
            case "📊 Статистика": if (isAdmin(chatId)) showServerStats(chatId); break;
            case "🚫 Забанить игрока": if (isAdmin(chatId)) startBan(chatId); break;
            case "🛑 Остановить сервер": if (isAdmin(chatId)) confirmServerStop(chatId); break;
            case "🔄 Рестарт сервера": if (isAdmin(chatId)) confirmServerRestart(chatId); break;
            case "🔴 Консоль": if (isAdmin(chatId)) startConsoleView(chatId); break;
            default: sendMessage(chatId, "Неизвестная команда. Используйте меню или префикс `!` для админ-команд."); break;
        }
    }

    private void handleCallbackQuery(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb) {
        String[] data = cb.getData().split(":", 3);
        String action = data[0];

        switch (action) {
            case "approve": case "deny": handleApplicationCallback(cb, data); break;
            case "reply_app": handleReplyAppCallback(cb, data); break;
            case "reply_report": handleReplyReportCallback(cb, data); break;
            case "show_chart": handleChartCallback(cb, data); break;
            case "app_link_yes": handleAppLinkChoice(cb, true); break;
            case "app_link_no": handleAppLinkChoice(cb, false); break;
            case "unlink_account": handleUnlinkAccount(cb); break;
            case "confirm_stop": if (isAdmin(cb.getMessage().getChatId())) { editMessage(cb.getMessage().getChatId(), cb.getMessage().getMessageId(), "✅ Команда принята. Останавливаю сервер..."); Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop")); } break;
            case "confirm_restart": if (isAdmin(cb.getMessage().getChatId())) { editMessage(cb.getMessage().getChatId(), cb.getMessage().getMessageId(), "✅ Команда принята. Перезапускаю сервер..."); Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart")); } break;
            case "cancel_action": editMessage(cb.getMessage().getChatId(), cb.getMessage().getMessageId(), "❌ Действие отменено."); break;
            case "stop_console": if (isAdmin(cb.getMessage().getChatId())) stopConsoleView(cb); break;
            case "show_stats": if (isAdmin(cb.getMessage().getChatId())) showServerStats(cb.getMessage().getChatId()); break;
        }
        answerCallbackQuery(cb.getId());
    }

    private void startConsoleView(long chatId) {
        if (logReader != null) {
            sendMessage(chatId, "Уже есть активная сессия просмотра консоли.");
            return;
        }
        SendMessage message = new SendMessage(String.valueOf(chatId), "Запускаю просмотр консоли...");
        try {
            Message sentMessage = execute(message);
            logReader = new ConsoleLogReader(this, chatId, sentMessage.getMessageId());
            editMessage(chatId, sentMessage.getMessageId(), "🔴 **Просмотр консоли активен...**\n\n`Начало лога...`", getStopConsoleMarkup());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void stopConsoleView(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb) {
        if (logReader != null) {
            logReader.stop();
            logReader = null;
            editMessage(cb.getMessage().getChatId(), cb.getMessage().getMessageId(), "🟢 **Просмотр консоли остановлен.**");
        }
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

    private void processFormStep(long chatId, String message, User from) { 
        UserState state = userStates.get(chatId); 
        switch (state) { 
            case AWAITING_APP_NICKNAME: 
            case AWAITING_APP_AGE: 
            case AWAITING_APP_PLANS: processApplicationStep(chatId, message); break; 
            case AWAITING_REPORT_OWN_NICK: 
            case AWAITING_REPORT_TARGET_NICK: 
            case AWAITING_REPORT_SITUATION: processReportStep(chatId, message); break; 
            case AWAITING_BAN_NICKNAME: if (isAdmin(chatId)) processBan(chatId, message); break;
            case AWAITING_APP_REPLY: processAppReply(chatId, message, from); break;
            case AWAITING_REPORT_REPLY: processReportReply(chatId, message, from); break;
        } 
    }
    private void sendMainMenu(long chatId) { ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(); kb.setResizeKeyboard(true); List<KeyboardRow> keyboard = new ArrayList<>(); KeyboardRow row1 = new KeyboardRow(); row1.add(new KeyboardButton("📝 Подать заявку")); row1.add(new KeyboardButton("👤 Мой профиль")); keyboard.add(row1); KeyboardRow row2 = new KeyboardRow(); row2.add(new KeyboardButton("🚨 Подать жалобу")); row2.add(new KeyboardButton("🔗 Привязать аккаунт")); keyboard.add(row2); if (isAdmin(chatId)) { KeyboardRow adminRow = new KeyboardRow(); adminRow.add(new KeyboardButton("👑 Админ-меню")); keyboard.add(adminRow); } kb.setKeyboard(keyboard); sendMessage(chatId, "👋 Выберите действие:", kb); }
    private void startApplication(long chatId, User from) { 
        userStates.put(chatId, UserState.AWAITING_APP_NICKNAME); 
        userForms.put(chatId, new WhitelistApplication(from.getUserName(), chatId)); 
        sendMessage(chatId, "📝 Начинаем подачу заявки.\n\n1. Какой ваш ник в Minecraft?"); 
    }
    
    private void processApplicationStep(long chatId, String message) { 
        UserState state = userStates.get(chatId); 
        WhitelistApplication app = (WhitelistApplication) userForms.get(chatId); 
        switch (state) { 
            case AWAITING_APP_NICKNAME: 
                app.setNickname(PlayerNameUtil.normalize(message)); 
                userStates.put(chatId, UserState.AWAITING_APP_AGE); 
                sendMessage(chatId, "2. Сколько вам лет?"); 
                break; 
            case AWAITING_APP_AGE: 
                app.setAge(message); 
                userStates.put(chatId, UserState.AWAITING_APP_PLANS); 
                sendMessage(chatId, "3. Чем планируете заниматься на сервере?"); 
                break; 
            case AWAITING_APP_PLANS: 
                app.setPlans(message); 
                userStates.put(chatId, UserState.AWAITING_APP_LINK);
                // Вопрос о привязке аккаунта
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton yesBtn = new InlineKeyboardButton("✅ Да, привязать");
                yesBtn.setCallbackData("app_link_yes");
                InlineKeyboardButton noBtn = new InlineKeyboardButton("❌ Нет, спасибо");
                noBtn.setCallbackData("app_link_no");
                row.add(yesBtn);
                row.add(noBtn);
                rows.add(row);
                markup.setKeyboard(rows);
                sendMessage(chatId, "4. Хотите автоматически привязать свой аккаунт Minecraft к Telegram?\n\nЭто позволит вам видеть свою статистику и использовать дополнительные функции бота.", markup);
                break;
            case AWAITING_APP_LINK:
                // Этот case обрабатывается через callback
                break;
        } 
    }
    private void startReport(long chatId, User from) { userStates.put(chatId, UserState.AWAITING_REPORT_OWN_NICK); userForms.put(chatId, new Report(from.getUserName())); sendMessage(chatId, "🚨 Подача жалобы.\n\n1. Ваш игровой ник?"); }
    private void processReportStep(long chatId, String message) { 
        UserState state = userStates.get(chatId); 
        Report report = (Report) userForms.get(chatId); 
        switch (state) { 
            case AWAITING_REPORT_OWN_NICK: report.setReporter(PlayerNameUtil.normalize(message)); userStates.put(chatId, UserState.AWAITING_REPORT_TARGET_NICK); sendMessage(chatId, "2. Ник игрока, на которого жалуетесь?"); break; 
            case AWAITING_REPORT_TARGET_NICK: report.setTarget(PlayerNameUtil.normalize(message)); userStates.put(chatId, UserState.AWAITING_REPORT_SITUATION); sendMessage(chatId, "3. Опишите ситуацию подробно."); break; 
            case AWAITING_REPORT_SITUATION: report.setSituation(message); sendReportToStaff(report, chatId); sendMessage(chatId, "✅ Ваша жалоба отправлена администрации."); userStates.remove(chatId); userForms.remove(chatId); break; 
        } 
    }
    private void sendAdminMenu(long chatId) { 
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(); 
        kb.setResizeKeyboard(true); 
        List<KeyboardRow> keyboard = new ArrayList<>(); 
        
        KeyboardRow row1 = new KeyboardRow(); 
        row1.add(new KeyboardButton("🔴 Консоль")); 
        row1.add(new KeyboardButton("📊 Статистика")); 
        keyboard.add(row1); 
        
        KeyboardRow row2 = new KeyboardRow(); 
        row2.add(new KeyboardButton("🚫 Забанить игрока")); 
        row2.add(new KeyboardButton("📋 Вайтлист")); 
        keyboard.add(row2); 
        
        KeyboardRow row3 = new KeyboardRow(); 
        row3.add(new KeyboardButton("🛑 Остановить сервер")); 
        row3.add(new KeyboardButton("🔄 Рестарт сервера")); 
        keyboard.add(row3); 
        
        KeyboardRow row4 = new KeyboardRow(); 
        row4.add(new KeyboardButton("⬅️ Назад в меню")); 
        keyboard.add(row4); 
        
        kb.setKeyboard(keyboard); 
        sendMessage(chatId, "👑 Админ-меню:\n\n*Для выполнения команд с консоли, отправьте сообщение с префиксом `!` (например, `!list`)*", kb); 
    }
    private void startBan(long chatId) { userStates.put(chatId, UserState.AWAITING_BAN_NICKNAME); sendMessage(chatId, "🚫 Введите ник игрока, которого хотите забанить:"); }
    private void processBan(long chatId, String nickname) { String normalizedName = PlayerNameUtil.normalize(nickname); Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + normalizedName + " [Бан через Telegram]")); sendMessage(chatId, "✅ Команда `ban " + normalizedName + "` отправлена на сервер."); userStates.remove(chatId); }
    private void confirmServerStop(long chatId) { InlineKeyboardMarkup markup = new InlineKeyboardMarkup(); List<List<InlineKeyboardButton>> rows = new ArrayList<>(); List<InlineKeyboardButton> row = new ArrayList<>(); InlineKeyboardButton yesBtn = new InlineKeyboardButton("Да, я уверен"); yesBtn.setCallbackData("confirm_stop"); InlineKeyboardButton noBtn = new InlineKeyboardButton("Нет, отмена"); noBtn.setCallbackData("cancel_action"); row.add(yesBtn); row.add(noBtn); rows.add(row); markup.setKeyboard(rows); sendMessage(chatId, "❗️ ВЫ УВЕРЕНЫ, ЧТО ХОТИТЕ ОСТАНОВИТЬ СЕРВЕР? ❗️", markup); }
    private void confirmServerRestart(long chatId) { InlineKeyboardMarkup markup = new InlineKeyboardMarkup(); List<List<InlineKeyboardButton>> rows = new ArrayList<>(); List<InlineKeyboardButton> row = new ArrayList<>(); InlineKeyboardButton yesBtn = new InlineKeyboardButton("Да, я уверен"); yesBtn.setCallbackData("confirm_restart"); InlineKeyboardButton noBtn = new InlineKeyboardButton("Нет, отмена"); noBtn.setCallbackData("cancel_action"); row.add(yesBtn); row.add(noBtn); rows.add(row); markup.setKeyboard(rows); sendMessage(chatId, "❗️ ВЫ УВЕРЕНЫ, ЧТО ХОТИТЕ ПЕРЕЗАПУСТИТЬ СЕРВЕР? ❗️", markup); }
    private void startLinking(long chatId) { if (plugin.getLinkManager().isLinked(chatId)) { sendMessage(chatId, "✅ Ваш аккаунт уже привязан к игроку: " + plugin.getLinkManager().getLinkedPlayerName(chatId)); return; } String code = plugin.getLinkManager().generateLinkCode(chatId); sendMessage(chatId, "🔗 Для привязки аккаунта, зайдите на сервер и введите команду:\n\n`/link " + code + "`"); }
    private void showProfile(long chatId) { 
        String playerName = plugin.getLinkManager().getLinkedPlayerName(chatId); 
        if (playerName == null) { 
            sendMessage(chatId, "ℹ️ Ваш Telegram аккаунт еще не привязан к игровому.\n\nИспользуйте кнопку \"🔗 Привязать аккаунт\" в главном меню."); 
            return; 
        } 
        FileConfiguration data = plugin.getPlayerDataManager().getPlayerData(playerName); 
        boolean isWhitelisted = plugin.getWhitelistManager().isWhitelisted(playerName); 
        long totalPlaytime = data.getLong("total_playtime", 0); 
        String formattedPlaytime = formatDuration(totalPlaytime); 
        long firstJoin = data.getLong("first_join", 0); 
        String firstJoinDate = (firstJoin > 0) ? new SimpleDateFormat("dd.MM.yyyy").format(new Date(firstJoin)) : "неизвестно"; 
        long lastJoin = data.getLong("last_join", 0); 
        String lastJoinDate = (lastJoin > 0) ? new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(lastJoin)) : "неизвестно"; 
        String messageText = "👤 Ваш профиль:\n\n" + "- Ник: `" + playerName + "`\n" + "- Статус: " + (isWhitelisted ? "✅ В белом списке" : "❌ Не в белом списке") + "\n" + "- Общее время в игре: `" + formattedPlaytime + "`\n" + "- Первый вход: `" + firstJoinDate + "`\n" + "- Последний раз был онлайн: `" + lastJoinDate + "`\n\n" + "*Примечание: График активности учитывает только время, записанное этим плагином.*"; 
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(); 
        List<List<InlineKeyboardButton>> rows = new ArrayList<>(); 
        
        // Кнопка графика
        List<InlineKeyboardButton> row1 = new ArrayList<>(); 
        InlineKeyboardButton chartButton = new InlineKeyboardButton(); 
        chartButton.setText("📊 График активности"); 
        chartButton.setCallbackData("show_chart:" + playerName); 
        row1.add(chartButton); 
        rows.add(row1);
        
        // Кнопка отвязки
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton unlinkButton = new InlineKeyboardButton();
        unlinkButton.setText("🔓 Отвязать аккаунт");
        unlinkButton.setCallbackData("unlink_account");
        row2.add(unlinkButton);
        rows.add(row2);
        
        markup.setKeyboard(rows); 
        sendMessage(chatId, messageText, markup); 
    }
    private void showWhitelist(long chatId) { plugin.getWhitelistManager().load(); List<String> players = plugin.getWhitelistManager().getWhitelistedPlayers(); if (players.isEmpty()) { sendMessage(chatId, "📋 Белый список пуст."); return; } StringBuilder sb = new StringBuilder("📋 Игроки в белом списке:\n\n"); for (String player : players) { sb.append("- `").append(player).append("`\n"); } sendMessage(chatId, sb.toString()); }
    private void handleApplicationCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb, String[] data) { 
        String action = data[0]; 
        String nickname = data[1]; 
        // Парсим userChatId, убирая возможный суффикс :link
        String userIdStr = data[2].contains(":") ? data[2].split(":")[0] : data[2];
        long userChatId = Long.parseLong(userIdStr);
        boolean wantsLink = data[2].contains(":link") || (data.length > 3 && data[3].equals("link"));
        
        String staffName = cb.getFrom().getFirstName(); 
        if (!(cb.getMessage() instanceof Message)) return; 
        Message message = (Message) cb.getMessage(); 
        
        if (action.equals("approve")) { 
            plugin.getWhitelistManager().addPlayer(nickname); 
            plugin.getLogger().info("Player '" + nickname + "' was added to the whitelist by " + staffName); 
            plugin.getFileLogger().log("APPROVED: Player '" + nickname + "' by " + staffName + "."); 
            
            // Проверяем, хочет ли пользователь привязать аккаунт
            if (wantsLink) {
                plugin.getLinkManager().forceLink(userChatId, nickname);
                sendMessage(userChatId, "🎉 Поздравляем! Ваша заявка для ника '" + nickname + "' была одобрена.\n\n✅ Ваш аккаунт автоматически привязан к Telegram!");
            } else {
                sendMessage(userChatId, "🎉 Поздравляем! Ваша заявка для ника '" + nickname + "' была одобрена.");
            }
            editMessage(message.getChatId(), message.getMessageId(), message.getText() + "\n\n[✅ Одобрено пользователем " + staffName + "]"); 
        } else if (action.equals("deny")) { 
            plugin.getLogger().info("Player '" + nickname + "' was denied by " + staffName); 
            plugin.getFileLogger().log("DENIED: Player '" + nickname + "' by " + staffName + "."); 
            sendMessage(userChatId, "😔 К сожалению, ваша заявка для ника '" + nickname + "' была отклонена."); 
            editMessage(message.getChatId(), message.getMessageId(), message.getText() + "\n\n[❌ Отклонено пользователем " + staffName + "]"); 
        } 
    }
    private void handleChartCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb, String[] data) { long chatId = cb.getMessage().getChatId(); String playerName = data[1]; FileConfiguration playerData = plugin.getPlayerDataManager().getPlayerData(playerName); String chartUrl = ChartGenerator.generateChartUrl(playerData.getConfigurationSection("daily_playtime")); if (chartUrl != null) { sendPhoto(chatId, chartUrl, "📊 График активности для игрока " + playerName); } else { sendMessage(chatId, "Недостаточно данных для построения графика."); } answerCallbackQuery(cb.getId()); }
    private boolean isAdmin(long chatId) { return String.valueOf(chatId).equals(adminChatId); }
    private void sendToStaff(String subject, String body) { 
        Set<String> recipients = new HashSet<>(moderatorChatIds); 
        if (adminChatId != null && !adminChatId.isEmpty()) { recipients.add(adminChatId); } 
        for (String recipientId : recipients) { if (!recipientId.isEmpty()) { sendMessage(Long.parseLong(recipientId), "<b>" + subject + "</b>\n\n" + body); } } 
    }

    private void sendReportToStaff(Report report, long userChatId) {
        String text = "❗️ Новая жалоба:\n\n" + report.toString();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton replyBtn = new InlineKeyboardButton("💬 Ответить");
        replyBtn.setCallbackData("reply_report:" + userChatId);
        row.add(replyBtn);
        rows.add(row);
        markup.setKeyboard(rows);
        Set<String> recipients = new HashSet<>(moderatorChatIds);
        if (adminChatId != null && !adminChatId.isEmpty()) { recipients.add(adminChatId); }
        for (String recipientId : recipients) { if (!recipientId.isEmpty()) { sendMessage(Long.parseLong(recipientId), text, markup); } }
    }

    // Обработка нажатия кнопки "Ответить" на заявку
    private void handleReplyAppCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb, String[] data) {
        long chatId = cb.getMessage().getChatId();
        long targetUserId = Long.parseLong(data[1]);
        
        userStates.put(chatId, UserState.AWAITING_APP_REPLY);
        replyTargets.put(chatId, targetUserId);
        sendMessage(chatId, "💬 Введите ваш ответ для заявителя:");
    }

    // Обработка нажатия кнопки "Ответить" на жалобу
    private void handleReplyReportCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb, String[] data) {
        long chatId = cb.getMessage().getChatId();
        long targetUserId = Long.parseLong(data[1]);
        
        userStates.put(chatId, UserState.AWAITING_REPORT_REPLY);
        replyTargets.put(chatId, targetUserId);
        sendMessage(chatId, "💬 Введите ваш ответ для жалобщика:");
    }

    // Обработка ответа на заявку
    private void processAppReply(long chatId, String message, User from) {
        Long targetUserId = replyTargets.remove(chatId);
        userStates.remove(chatId);
        
        if (targetUserId != null) {
            String staffName = from.getFirstName();
            String replyText = "📨 <b>Ответ от администрации:</b>\n\n" + message;
            sendMessage(targetUserId, replyText);
            sendMessage(chatId, "✅ Ваш ответ отправлен заявителю.");
        } else {
            sendMessage(chatId, "❌ Ошибка: не найден получатель.");
        }
    }

    // Обработка ответа на жалобу
    private void processReportReply(long chatId, String message, User from) {
        Long targetUserId = replyTargets.remove(chatId);
        userStates.remove(chatId);
        
        if (targetUserId != null) {
            String staffName = from.getFirstName();
            String replyText = "📨 <b>Ответ на вашу жалобу:</b>\n\n" + message;
            sendMessage(targetUserId, replyText);
            sendMessage(chatId, "✅ Ваш ответ отправлен жалобщику.");
        } else {
            sendMessage(chatId, "❌ Ошибка: не найден получатель.");
        }
    }

    // Обработка выбора привязки аккаунта при подаче заявки
    private void handleAppLinkChoice(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb, boolean wantsLink) {
        long chatId = cb.getMessage().getChatId();
        
        if (userForms.containsKey(chatId)) {
            WhitelistApplication app = (WhitelistApplication) userForms.get(chatId);
            app.setWantsLink(wantsLink);
            
            sendApplicationToStaff(app, chatId);
            String linkMessage = wantsLink ? 
                "✅ Ваша заявка отправлена на рассмотрение.\n\nПри одобрении ваш аккаунт будет автоматически привязан к Telegram." :
                "✅ Ваша заявка отправлена на рассмотрение.";
            sendMessage(chatId, linkMessage);
            
            userStates.remove(chatId);
            userForms.remove(chatId);
            editMessage(chatId, cb.getMessage().getMessageId(), wantsLink ? "✅ Вы выбрали привязку аккаунта" : "❌ Вы отказались от привязки");
        }
    }

    // Обработка отвязки аккаунта
    private void handleUnlinkAccount(org.telegram.telegrambots.meta.api.objects.CallbackQuery cb) {
        long chatId = cb.getMessage().getChatId();
        String playerName = plugin.getLinkManager().getLinkedPlayerName(chatId);
        
        if (playerName != null) {
            plugin.getLinkManager().removeLink(chatId);
            editMessage(chatId, cb.getMessage().getMessageId(), "✅ Ваш аккаунт <b>" + playerName + "</b> был отвязан от Telegram.");
        } else {
            editMessage(chatId, cb.getMessage().getMessageId(), "❌ Аккаунт не был привязан.");
        }
    }

    // Показ статистики сервера
    private void showServerStats(long chatId) {
        // Получаем статистику
        int totalPlayers = plugin.getWhitelistManager().getWhitelistedPlayers().size();
        int linkedAccounts = plugin.getLinkManager().getLinkedCount();
        
        // Статистика игроков
        java.io.File playerDataFolder = new java.io.File(plugin.getDataFolder(), "playerdata");
        int totalDataPlayers = 0;
        int newToday = 0;
        int newWeek = 0;
        int activeWeek = 0;
        
        long todayStart = getStartOfDay(System.currentTimeMillis());
        long weekStart = todayStart - 6 * 24 * 60 * 60 * 1000L; // 7 дней включая сегодня
        
        if (playerDataFolder.exists() && playerDataFolder.isDirectory()) {
            java.io.File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                totalDataPlayers = files.length;
                
                for (java.io.File file : files) {
                    org.bukkit.configuration.file.FileConfiguration data = 
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                    
                    long firstJoin = data.getLong("first_join", 0);
                    if (firstJoin >= todayStart) newToday++;
                    if (firstJoin >= weekStart) newWeek++;
                    
                    // Проверяем активность за неделю (10+ часов)
                    long weeklyPlaytime = 0;
                    org.bukkit.configuration.ConfigurationSection daily = data.getConfigurationSection("daily_playtime");
                    if (daily != null) {
                        for (String dateStr : daily.getKeys(false)) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                                java.util.Date date = sdf.parse(dateStr);
                                if (date != null && date.getTime() >= weekStart) {
                                    weeklyPlaytime += daily.getLong(dateStr);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    if (weeklyPlaytime >= 10 * 3600) activeWeek++;
                }
            }
        }
        
        String stats = "📊 <b>Статистика сервера</b>\n\n" +
                      "👥 <b>Игроки:</b>\n" +
                      "├ Всего в вайтлисте: <b>" + totalPlayers + "</b>\n" +
                      "├ Новых за сегодня: <b>" + newToday + "</b>\n" +
                      "├ Новых за неделю: <b>" + newWeek + "</b>\n" +
                      "└ Активных (10ч+/нед): <b>" + activeWeek + "</b>\n\n" +
                      "🔗 <b>Telegram:</b>\n" +
                      "├ Привязанных аккаунтов: <b>" + linkedAccounts + "</b>\n" +
                      "└ Всего с данными: <b>" + totalDataPlayers + "</b>";
        
        sendMessage(chatId, stats);
    }
    
    private long getStartOfDay(long timestamp) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
    
    public void updateServerStatus() {
        if (serverStatusManager != null) {
            serverStatusManager.forceUpdate();
        }
    }
    
    public void sendMessageToBridge(String text) {
        if (!isBridgeChatValid || chatBridgeGroupId == null || chatBridgeGroupId.isEmpty()) {
            return;
        }
        try {
            SendMessage message = new SendMessage(chatBridgeGroupId, text);
            message.setParseMode("HTML");
            execute(message);
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("chat not found")) {
                plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                plugin.getLogger().severe("!!! Chat bridge failed: Chat not found.                !!!");
                plugin.getLogger().severe("!!! Check 'chat-bridge-group-id' in config.yml.      !!!");
                plugin.getLogger().severe("!!! Disabling chat bridge to prevent further errors.   !!!");
                plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                isBridgeChatValid = false;
            } else {
                e.printStackTrace();
            }
        }
    }
    private void sendApplicationToStaff(WhitelistApplication app, long userChatId) { 
        String linkInfo = app.wantsLink() ? "\n🔗 Хочет привязать аккаунт" : "";
        String text = "📬 Новая заявка в белый список:\n\n" + app.toString() + linkInfo; 
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(); 
        List<List<InlineKeyboardButton>> rows = new ArrayList<>(); 
        List<InlineKeyboardButton> row1 = new ArrayList<>(); 
        InlineKeyboardButton approveBtn = new InlineKeyboardButton("✅ Одобрить"); 
        String approveData = app.wantsLink() ? 
            "approve:" + app.getNickname() + ":" + userChatId + ":link" : 
            "approve:" + app.getNickname() + ":" + userChatId;
        approveBtn.setCallbackData(approveData); 
        InlineKeyboardButton denyBtn = new InlineKeyboardButton("❌ Отклонить"); 
        denyBtn.setCallbackData("deny:" + app.getNickname() + ":" + userChatId); 
        row1.add(approveBtn); 
        row1.add(denyBtn); 
        rows.add(row1);
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton replyBtn = new InlineKeyboardButton("💬 Ответить");
        replyBtn.setCallbackData("reply_app:" + userChatId);
        row2.add(replyBtn);
        rows.add(row2);
        markup.setKeyboard(rows); 
        Set<String> recipients = new HashSet<>(moderatorChatIds); 
        if (adminChatId != null && !adminChatId.isEmpty()) { recipients.add(adminChatId); } 
        for (String recipientId : recipients) { if (!recipientId.isEmpty()) { sendMessage(Long.parseLong(recipientId), text, markup); } } 
    }
    private void sendMessage(long chatId, String text) { sendMessage(chatId, text, null); }
    private void sendMessage(long chatId, String text, org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard markup) { SendMessage msg = new SendMessage(String.valueOf(chatId), text); msg.setParseMode("HTML"); if (markup != null) msg.setReplyMarkup(markup); try { execute(msg); } catch (TelegramApiException e) { /* Suppress errors for general messages */ } }
    public void sendPhoto(long chatId, String url, String caption) { SendPhoto photo = new SendPhoto(String.valueOf(chatId), new InputFile(url)); photo.setCaption(caption); try { execute(photo); } catch (TelegramApiException e) { e.printStackTrace(); } }
    
    public void editMessage(long chatId, int msgId, String text) {
        editMessage(chatId, msgId, text, null);
    }

    public void editMessage(long chatId, int msgId, String text, InlineKeyboardMarkup markup) {
        EditMessageText edited = new EditMessageText();
        edited.setChatId(String.valueOf(chatId));
        edited.setMessageId(msgId);
        edited.setText(text);
        edited.setParseMode("HTML");
        if (markup != null) edited.setReplyMarkup(markup);
        try {
            execute(edited);
        } catch (TelegramApiException e) { /* Ignore: message is not modified */
        }
    }

    private void answerCallbackQuery(String id) { try { execute(new AnswerCallbackQuery(id)); } catch (TelegramApiException e) { e.printStackTrace(); } }
    private String formatDuration(long s) { if (s < 60) return s + " сек."; long d = TimeUnit.SECONDS.toDays(s); long h = TimeUnit.SECONDS.toHours(s) % 24; long m = TimeUnit.SECONDS.toMinutes(s) % 60; StringBuilder sb = new StringBuilder(); if (d > 0) sb.append(d).append(" д. "); if (h > 0) sb.append(h).append(" ч. "); if (m > 0) sb.append(m).append(" мин."); return sb.toString().trim(); }
    @Override public String getBotUsername() { return plugin.getConfig().getString("telegram.bot-username", "ListTelegramBot"); }
    private static class WhitelistApplication { 
        private String nickname, age, plans, tgUsername; 
        private long telegramId;
        private boolean wantsLink = false;
        
        public WhitelistApplication(String tgUsername, long telegramId) { 
            this.tgUsername = tgUsername; 
            this.telegramId = telegramId;
        } 
        
        public String getNickname() { return nickname; } 
        public void setNickname(String n) { this.nickname = n; } 
        public void setAge(String a) { this.age = a; } 
        public void setPlans(String p) { this.plans = p; } 
        public long getTelegramId() { return telegramId; }
        public boolean wantsLink() { return wantsLink; }
        public void setWantsLink(boolean wantsLink) { this.wantsLink = wantsLink; }
        
        @Override public String toString() { 
            String user = (tgUsername != null && !tgUsername.isEmpty()) ? " (@" + tgUsername + ")" : ""; 
            return "📝 Ник: " + nickname + user + "\n" + "🎂 Возраст: " + age + "\n" + "🎯 Планы: " + plans; 
        } 
    }
    private static class Report { private String reporter, target, situation, tgUsername; public Report(String tgUsername) { this.tgUsername = tgUsername; } public void setReporter(String r) { this.reporter = r; } public void setTarget(String t) { this.target = t; } public void setSituation(String s) { this.situation = s; } @Override public String toString() { String user = (tgUsername != null && !tgUsername.isEmpty()) ? " (@" + tgUsername + ")" : ""; return "👤 От: " + reporter + user + "\n" + "👥 На кого: " + target + "\n\n" + "📜 Ситуация:\n" + situation; } }
}
