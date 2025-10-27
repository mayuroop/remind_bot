package com.example.reminderbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Main entry point for the Telegram Reminder Bot.
 */
public class BotMain {
    private static final Logger logger = LoggerFactory.getLogger(BotMain.class);

    public static void main(String[] args) {
        logger.info("Starting Telegram Reminder Bot...");

        // Bot credentials (hardcoded)
        String botToken = "8085889854:AAH-0yp_S91pMRHhxk4-GOf3nGCk8U0vHfI";
        String botUsername = "reminder46bot";

        try {
            // Initialize database
            logger.info("Initializing database...");
            DBHelper.initDatabase();

            // Create reminder service
            ReminderService reminderService = new ReminderService();

            // Create bot instance
            ReminderBot bot = new ReminderBot(botToken, botUsername, reminderService);
            
            // Set bot instance in service (for sending reminders)
            reminderService.setBot(bot);

            // Register bot with Telegram API
            logger.info("Registering bot with Telegram API...");
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            logger.info("Bot registered successfully!");

            // Load and schedule pending reminders from database
            logger.info("Loading pending reminders...");
            reminderService.loadPendingReminders();

            logger.info("✅ Telegram Reminder Bot is now running!");
            logger.info("Bot username: @{}", botUsername);
            logger.info("Press Ctrl+C to stop the bot.");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down bot...");
                reminderService.shutdown();
                logger.info("Bot stopped.");
            }));

            // Keep the application running
            Thread.currentThread().join();

        } catch (TelegramApiException e) {
            logger.error("Error initializing Telegram bot", e);
            System.err.println("Failed to initialize bot: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error("Bot interrupted", e);
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        }
    }
}
