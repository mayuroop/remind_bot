package com.example.reminderbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram bot implementation for handling reminder commands.
 */
public class ReminderBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ReminderBot.class);
    
    private final String botToken;
    private final String botUsername;
    private final ReminderService reminderService;

    // Regex patterns for parsing commands
    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile(
        "^/remind\\s+in\\s+((?:\\d+[smhd])+)\\s+(.+)$", 
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABSOLUTE_TIME_PATTERN = Pattern.compile(
        "^/remind\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CANCEL_PATTERN = Pattern.compile(
        "^/cancel\\s+(\\d+)$",
        Pattern.CASE_INSENSITIVE
    );

    public ReminderBot(String botToken, String botUsername, ReminderService reminderService) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.reminderService = reminderService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        String text = message.getText();
        long chatId = message.getChatId();
        Long userId = message.getFrom().getId();

        logger.info("Received message from chat {}: {}", chatId, text);

        try {
            if (text.startsWith("/start")) {
                handleStartCommand(chatId);
            } else if (text.startsWith("/help")) {
                handleStartCommand(chatId);
            } else if (text.startsWith("/remind")) {
                handleRemindCommand(chatId, userId, text);
            } else if (text.startsWith("/list")) {
                handleListCommand(chatId);
            } else if (text.startsWith("/cancel")) {
                handleCancelCommand(chatId, text);
            } else {
                sendMessage(chatId, "Unknown command. Send /help for usage instructions.");
            }
        } catch (Exception e) {
            logger.error("Error processing message", e);
            sendMessage(chatId, "❌ Error: " + e.getMessage());
        }
    }

    /**
     * Handle /start and /help commands.
     */
    private void handleStartCommand(long chatId) {
        String helpText = """
                🤖 *Reminder Bot - Help*
                
                I can help you schedule reminders!
                
                *Commands:*
                
                📅 *Absolute time:*
                `/remind YYYY-MM-DD HH:MM message`
                (Use 24-hour format: 00:00 to 23:59)
                Example: `/remind 2025-10-28 14:00 Submit project`
                Example: `/remind 2025-10-27 17:30 Evening meeting` (5:30 PM)
                
                ⏱️ *Relative time:*
                `/remind in <duration> message`
                Duration format: 1s, 2m, 3h, 4d (or combinations like 2h30m)
                Example: `/remind in 10m Drink water`
                Example: `/remind in 2h30m Take a break`
                
                📋 *List reminders:*
                `/list` - Show all your pending reminders
                
                ❌ *Cancel reminder:*
                `/cancel <id>` - Cancel a specific reminder
                Example: `/cancel 5`
                
                All times are in Indian Standard Time (IST).
                """;
        
        sendMessage(chatId, helpText);
    }

    /**
     * Handle /remind command.
     */
    private void handleRemindCommand(long chatId, Long userId, String text) {
        // Try relative time format first
        Matcher relativeMatcher = RELATIVE_TIME_PATTERN.matcher(text);
        if (relativeMatcher.matches()) {
            String duration = relativeMatcher.group(1);
            String message = relativeMatcher.group(2);
            handleRelativeReminder(chatId, userId, duration, message);
            return;
        }

        // Try absolute time format
        Matcher absoluteMatcher = ABSOLUTE_TIME_PATTERN.matcher(text);
        if (absoluteMatcher.matches()) {
            String date = absoluteMatcher.group(1);
            String time = absoluteMatcher.group(2);
            String message = absoluteMatcher.group(3);
            handleAbsoluteReminder(chatId, userId, date, time, message);
            return;
        }

        // Invalid format
        sendMessage(chatId, """
                ❌ Invalid format!
                
                Use one of these formats:
                • `/remind in 10m message`
                • `/remind 2025-10-28 14:00 message`
                
                Send /help for more examples.
                """);
    }

    /**
     * Handle relative time reminder (e.g., "in 10m").
     */
    private void handleRelativeReminder(long chatId, Long userId, String durationStr, String message) {
        try {
            long seconds = parseDuration(durationStr);
            Instant remindAt = Instant.now().plusSeconds(seconds);
            
            int reminderId = reminderService.createReminder(chatId, userId, message, remindAt, "Asia/Kolkata");
            
            String confirmText = String.format(
                "✅ Reminder #%d created!\n\n" +
                "📝 Message: %s\n" +
                "⏰ Will remind you at: %s IST",
                reminderId, message, formatInstant(remindAt)
            );
            
            sendMessage(chatId, confirmText);
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "❌ " + e.getMessage());
        }
    }

    /**
     * Handle absolute time reminder (e.g., "2025-10-28 14:00").
     */
    private void handleAbsoluteReminder(long chatId, Long userId, String date, String time, String message) {
        try {
            String dateTimeStr = date + " " + time;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr, formatter);
            
            // Parse as Indian Standard Time
            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of("Asia/Kolkata"));
            Instant remindAt = zonedDateTime.toInstant();
            
            int reminderId = reminderService.createReminder(chatId, userId, message, remindAt, "Asia/Kolkata");
            
            String confirmText = String.format(
                "✅ Reminder #%d created!\n\n" +
                "📝 Message: %s\n" +
                "⏰ Will remind you at: %s IST",
                reminderId, message, formatInstant(remindAt)
            );
            
            sendMessage(chatId, confirmText);
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "❌ Invalid date/time format. Use: YYYY-MM-DD HH:MM (24-hour format)");
        } catch (IllegalArgumentException e) {
            // Show current time to help user understand the error
            String currentTime = formatInstant(Instant.now());
            sendMessage(chatId, String.format("❌ %s\n\n⏰ Current time (IST): %s\n\nNote: Use 24-hour format (e.g., 17:00 for 5 PM)", 
                e.getMessage(), currentTime));
        }
    }

    /**
     * Handle /list command.
     */
    private void handleListCommand(long chatId) {
        List<Reminder> reminders = reminderService.getPendingRemindersForChat(chatId);
        
        if (reminders.isEmpty()) {
            sendMessage(chatId, "📭 You have no pending reminders.");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 *Your Pending Reminders:*\n\n");
        
        for (Reminder reminder : reminders) {
            sb.append(String.format(
                "🔔 *#%d*\n" +
                "📝 %s\n" +
                "⏰ %s IST\n\n",
                reminder.getId(),
                reminder.getMessage(),
                formatInstant(reminder.getRemindAt())
            ));
        }
        
        sb.append("Use `/cancel <id>` to cancel a reminder.");
        
        sendMessage(chatId, sb.toString());
    }

    /**
     * Handle /cancel command.
     */
    private void handleCancelCommand(long chatId, String text) {
        Matcher matcher = CANCEL_PATTERN.matcher(text);
        
        if (!matcher.matches()) {
            sendMessage(chatId, "❌ Invalid format. Use: `/cancel <id>`\nExample: `/cancel 5`");
            return;
        }

        try {
            int reminderId = Integer.parseInt(matcher.group(1));
            boolean cancelled = reminderService.cancelReminder(reminderId, chatId);
            
            if (cancelled) {
                sendMessage(chatId, "✅ Reminder #" + reminderId + " has been cancelled.");
            } else {
                sendMessage(chatId, "❌ Reminder #" + reminderId + " not found or already sent.");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Invalid reminder ID.");
        }
    }

    /**
     * Parse duration string like "10m", "2h30m", "1d" into seconds.
     */
    private long parseDuration(String durationStr) {
        Pattern pattern = Pattern.compile("(\\d+)([smhd])");
        Matcher matcher = pattern.matcher(durationStr.toLowerCase());
        
        long totalSeconds = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            
            totalSeconds += switch (unit) {
                case "s" -> value;
                case "m" -> value * 60L;
                case "h" -> value * 3600L;
                case "d" -> value * 86400L;
                default -> throw new IllegalArgumentException("Invalid time unit: " + unit);
            };
        }

        if (!found || totalSeconds == 0) {
            throw new IllegalArgumentException("Invalid duration format: " + durationStr);
        }

        return totalSeconds;
    }

    /**
     * Format an Instant for display.
     */
    private String formatInstant(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Kolkata"));
        return formatter.format(instant);
    }

    /**
     * Send a text message to a chat.
     */
    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableMarkdown(true);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chat {}", chatId, e);
        }
    }
}
