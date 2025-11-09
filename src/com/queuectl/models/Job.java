package com.queuectl.models;

import java.sql.Timestamp;

public class Job {
    private String id;
    private String command;
    private String state;
    private int attempts;
    private int maxRetries;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp nextRetryAt;
    private String errorMessage;

    public Job() {
        this.state = "pending";
        this.attempts = 0;
        this.maxRetries = 3;
    }

    public Job(String id, String command) {
        this();
        this.id = id;
        this.command = command;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Timestamp getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Timestamp nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return String.format("Job{id='%s', command='%s', state='%s', attempts=%d, maxRetries=%d}",
                id, command, state, attempts, maxRetries);
    }
}
