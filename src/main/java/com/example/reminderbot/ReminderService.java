package com.example.reminderbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Service class for managing and scheduling reminders.
 */
public class ReminderService {
    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);
    
    private final ScheduledExecutorService scheduler;
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks;
    private ReminderBot bot;

    public ReminderService() {
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Set the bot instance for sending messages.
     */
    public void setBot(ReminderBot bot) {
        this.bot = bot;
    }

    /**
     * Create and schedule a new reminder.
     */
    public int createReminder(long chatId, Long userId, String message, Instant remindAt, String timezone) {
        // Validate that reminder time is in the future
        if (remindAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reminder time must be in the future");
        }

        // Create and save reminder
        Reminder reminder = new Reminder(chatId, userId, message, remindAt, timezone);
        int reminderId = DBHelper.saveReminder(reminder);
        
        // Schedule the reminder
        scheduleReminder(reminder);
        
        return reminderId;
    }

    /**
     * Schedule a reminder for execution.
     */
    public void scheduleReminder(Reminder reminder) {
        Instant now = Instant.now();
        Instant remindAt = reminder.getRemindAt();
        
        // Calculate delay
        long delaySeconds = Duration.between(now, remindAt).getSeconds();
        
        if (delaySeconds < 0) {
            logger.warn("Reminder {} is in the past, skipping", reminder.getId());
            return;
        }

        // Schedule the task
        ScheduledFuture<?> future = scheduler.schedule(
            () -> sendReminder(reminder),
            delaySeconds,
            TimeUnit.SECONDS
        );

        scheduledTasks.put(reminder.getId(), future);
        logger.info("Scheduled reminder {} to execute in {} seconds", reminder.getId(), delaySeconds);
    }

    /**
     * Send a reminder to the user.
     */
    private void sendReminder(Reminder reminder) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(reminder.getChatId()));
            message.setText("⏰ Reminder: " + reminder.getMessage());
            
            bot.execute(message);
            logger.info("Sent reminder {}", reminder.getId());
            
            // Mark as sent in database
            DBHelper.markAsSent(reminder.getId());
            
            // Remove from scheduled tasks
            scheduledTasks.remove(reminder.getId());
            
        } catch (TelegramApiException e) {
            logger.error("Error sending reminder {}", reminder.getId(), e);
        }
    }

    /**
     * Cancel a scheduled reminder.
     */
    public boolean cancelReminder(int reminderId, long chatId) {
        // Cancel the scheduled task
        ScheduledFuture<?> future = scheduledTasks.get(reminderId);
        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(reminderId);
        }

        // Delete from database
        boolean deleted = DBHelper.cancelReminder(reminderId, chatId);
        
        if (deleted) {
            logger.info("Cancelled reminder {}", reminderId);
        }
        
        return deleted;
    }

    /**
     * Load all pending reminders from the database and schedule them.
     */
    public void loadPendingReminders() {
        logger.info("Loading pending reminders from database...");
        List<Reminder> pendingReminders = DBHelper.getPendingReminders();
        
        Instant now = Instant.now();
        int scheduled = 0;
        int skipped = 0;

        for (Reminder reminder : pendingReminders) {
            if (reminder.getRemindAt().isAfter(now)) {
                scheduleReminder(reminder);
                scheduled++;
            } else {
                // Mark past reminders as sent
                DBHelper.markAsSent(reminder.getId());
                skipped++;
            }
        }

        logger.info("Loaded {} pending reminders ({} scheduled, {} skipped)", 
                    pendingReminders.size(), scheduled, skipped);
    }

    /**
     * Shutdown the scheduler gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down reminder service...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get all pending reminders for a specific chat.
     */
    public List<Reminder> getPendingRemindersForChat(long chatId) {
        return DBHelper.getRemindersByChat(chatId);
    }
}
