package com.example.reminderbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Database helper class for SQLite operations.
 */
public class DBHelper {
    private static final Logger logger = LoggerFactory.getLogger(DBHelper.class);
    private static final String DB_URL = "jdbc:sqlite:reminders.db";

    /**
     * Initialize the database and create the reminders table if it doesn't exist.
     */
    public static void initDatabase() {
        String createTableSQL = """
                CREATE TABLE IF NOT EXISTS reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id INTEGER NOT NULL,
                    user_id INTEGER,
                    message TEXT NOT NULL,
                    remind_at TEXT NOT NULL,
                    timezone TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    sent INTEGER NOT NULL DEFAULT 0
                )
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Error initializing database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Get a connection to the SQLite database.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Save a new reminder to the database.
     */
    public static int saveReminder(Reminder reminder) {
        String sql = """
                INSERT INTO reminders (chat_id, user_id, message, remind_at, timezone, created_at, sent)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setLong(1, reminder.getChatId());
            pstmt.setObject(2, reminder.getUserId());
            pstmt.setString(3, reminder.getMessage());
            pstmt.setString(4, reminder.getRemindAt().toString());
            pstmt.setString(5, reminder.getTimezone());
            pstmt.setString(6, reminder.getCreatedAt().toString());
            pstmt.setInt(7, reminder.isSent() ? 1 : 0);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating reminder failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    reminder.setId(id);
                    logger.info("Reminder saved with ID: {}", id);
                    return id;
                } else {
                    throw new SQLException("Creating reminder failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving reminder", e);
            throw new RuntimeException("Failed to save reminder", e);
        }
    }

    /**
     * Get all pending (unsent) reminders from the database.
     */
    public static List<Reminder> getPendingReminders() {
        String sql = "SELECT * FROM reminders WHERE sent = 0 ORDER BY remind_at ASC";
        List<Reminder> reminders = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                reminders.add(mapResultSetToReminder(rs));
            }
            logger.info("Loaded {} pending reminders", reminders.size());
            return reminders;
        } catch (SQLException e) {
            logger.error("Error loading pending reminders", e);
            throw new RuntimeException("Failed to load pending reminders", e);
        }
    }

    /**
     * Mark a reminder as sent.
     */
    public static void markAsSent(int reminderId) {
        String sql = "UPDATE reminders SET sent = 1 WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, reminderId);
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Reminder {} marked as sent", reminderId);
            }
        } catch (SQLException e) {
            logger.error("Error marking reminder as sent", e);
        }
    }

    /**
     * Cancel (delete) a reminder by ID.
     */
    public static boolean cancelReminder(int reminderId, long chatId) {
        String sql = "DELETE FROM reminders WHERE id = ? AND chat_id = ? AND sent = 0";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, reminderId);
            pstmt.setLong(2, chatId);
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Reminder {} cancelled", reminderId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Error cancelling reminder", e);
            return false;
        }
    }

    /**
     * Get all reminders for a specific chat.
     */
    public static List<Reminder> getRemindersByChat(long chatId) {
        String sql = "SELECT * FROM reminders WHERE chat_id = ? AND sent = 0 ORDER BY remind_at ASC";
        List<Reminder> reminders = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                reminders.add(mapResultSetToReminder(rs));
            }
            return reminders;
        } catch (SQLException e) {
            logger.error("Error loading reminders for chat", e);
            throw new RuntimeException("Failed to load reminders", e);
        }
    }

    /**
     * Map a ResultSet row to a Reminder object.
     */
    private static Reminder mapResultSetToReminder(ResultSet rs) throws SQLException {
        Reminder reminder = new Reminder();
        reminder.setId(rs.getInt("id"));
        reminder.setChatId(rs.getLong("chat_id"));
        
        long userId = rs.getLong("user_id");
        if (!rs.wasNull()) {
            reminder.setUserId(userId);
        }
        
        reminder.setMessage(rs.getString("message"));
        reminder.setRemindAt(Instant.parse(rs.getString("remind_at")));
        reminder.setTimezone(rs.getString("timezone"));
        reminder.setCreatedAt(Instant.parse(rs.getString("created_at")));
        reminder.setSent(rs.getInt("sent") == 1);
        
        return reminder;
    }
}
