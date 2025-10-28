package com.example.reminderbot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotMain extends TelegramLongPollingBot {
    
    // Reminder class - stores chat ID, message, and time
    static class Reminder {
        long chatId;
        String message;
        Instant time;
        
        Reminder(long chatId, String message, Instant time) {
            this.chatId = chatId;
            this.message = message;
            this.time = time;
        }
    }
    
    // Priority queue sorts reminders by time
    private final PriorityQueue<Reminder> reminders = new PriorityQueue<>(Comparator.comparing(r -> r.time));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private int reminderCount = 0;
    
    public static void main(String[] args) throws Exception {
        new TelegramBotsApi(DefaultBotSession.class).registerBot(new BotMain());
        Thread.currentThread().join();
    }
    
    @Override
    public String getBotUsername() {
        return "reminder46bot";
    }
    
    @Override
    public String getBotToken() {
        return "8085889854:AAH-0yp_S91pMRHhxk4-GOf3nGCk8U0vHfI";
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;
        
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        
        try {
            if (text.startsWith("/start") || text.startsWith("/help")) {
                handleHelp(chatId);
            } else if (text.startsWith("/list")) {
                handleList(chatId);
            } else if (text.startsWith("/cancel")) {
                handleCancel(chatId, text);
            } else if (text.startsWith("/remind")) {
                handleRemind(chatId, text);
            }
        } catch (Exception e) {
            sendMessage(chatId, "❌ " + e.getMessage());
        }
    }
    
    private void handleHelp(long chatId) {
        String help = "📅 /remind 2025-10-28 14:00 msg\n" +
                      "⏱️ /remind in 10m msg\n" +
                      "📋 /list\n" +
                      "❌ /cancel <id>";
        sendMessage(chatId, help);
    }
    
    private void handleList(long chatId) {
        if (reminders.isEmpty()) {
            sendMessage(chatId, "📭 No reminders");
            return;
        }
        
        StringBuilder list = new StringBuilder("📋 Reminders:\n");
        int index = 0;
        for (Reminder r : reminders) {
            list.append(++index).append(". ").append(r.message)
                .append(" @ ").append(formatTime(r.time)).append("\n");
        }
        sendMessage(chatId, list.toString());
    }
    
    private void handleCancel(long chatId, String text) {
        try {
            int id = Integer.parseInt(text.split(" ")[1]);
            List<Reminder> list = new ArrayList<>(reminders);
            reminders.remove(list.get(id - 1));
            sendMessage(chatId, "✅ Cancelled #" + id);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Invalid ID");
        }
    }
    
    private void handleRemind(long chatId, String text) {
        // Try relative time format: /remind in 10m msg
        Matcher relative = Pattern.compile("^/remind\\s+in\\s+((?:\\d+[smhd])+)\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(text);
        if (relative.matches()) {
            long seconds = parseRelativeTime(relative.group(1));
            addReminder(chatId, relative.group(2), Instant.now().plusSeconds(seconds));
            return;
        }
        
        // Try absolute time format: /remind 2025-10-28 14:00 msg
        Matcher absolute = Pattern.compile("^/remind\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(text);
        if (absolute.matches()) {
            String dateTime = absolute.group(1) + " " + absolute.group(2);
            Instant time = LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                        .atZone(ZoneId.of("Asia/Kolkata"))
                                        .toInstant();
            addReminder(chatId, absolute.group(3), time);
            return;
        }
        
        sendMessage(chatId, "❌ Use: /remind in 10m msg OR /remind 2025-10-28 14:00 msg");
    }
    
    private void addReminder(long chatId, String message, Instant time) {
        if (time.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Time must be in the future");
        }
        
        Reminder reminder = new Reminder(chatId, message, time);
        reminders.add(reminder);
        
        long delay = Duration.between(Instant.now(), time).getSeconds();
        scheduler.schedule(() -> sendMessage(reminder.chatId, "⏰ " + reminder.message), delay, TimeUnit.SECONDS);
        
        sendMessage(chatId, "✅ Reminder #" + (++reminderCount) + " @ " + formatTime(time));
    }
    
    private long parseRelativeTime(String duration) {
        Matcher matcher = Pattern.compile("(\\d+)([smhd])").matcher(duration.toLowerCase());
        long seconds = 0;
        
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            
            seconds += switch (unit) {
                case "s" -> value;
                case "m" -> value * 60;
                case "h" -> value * 3600;
                case "d" -> value * 86400;
                default -> 0;
            };
        }
        return seconds;
    }
    
    private String formatTime(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                               .withZone(ZoneId.of("Asia/Kolkata"))
                               .format(instant);
    }
    
    private void sendMessage(long chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception e) {
            // Silent fail
        }
    }
}
