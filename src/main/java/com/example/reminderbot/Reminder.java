package com.example.reminderbot;

import java.time.Instant;

/**
 * Model class representing a reminder.
 */
public class Reminder {
    private int id;
    private long chatId;
    private Long userId;
    private String message;
    private Instant remindAt;
    private String timezone;
    private Instant createdAt;
    private boolean sent;

    public Reminder() {
    }

    public Reminder(long chatId, Long userId, String message, Instant remindAt, String timezone) {
        this.chatId = chatId;
        this.userId = userId;
        this.message = message;
        this.remindAt = remindAt;
        this.timezone = timezone;
        this.createdAt = Instant.now();
        this.sent = false;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(Instant remindAt) {
        this.remindAt = remindAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    @Override
    public String toString() {
        return "Reminder{" +
                "id=" + id +
                ", chatId=" + chatId +
                ", userId=" + userId +
                ", message='" + message + '\'' +
                ", remindAt=" + remindAt +
                ", timezone='" + timezone + '\'' +
                ", createdAt=" + createdAt +
                ", sent=" + sent +
                '}';
    }
}
