package com.queuectl.core;

import com.queuectl.db.DatabaseManager;
import com.queuectl.models.Job;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JobQueue {
    private DatabaseManager dbManager;

    public JobQueue() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void enqueue(Job job) throws SQLException {
        String sql = "INSERT INTO jobs (id, command, state, attempts, max_retries) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, job.getId());
            stmt.setString(2, job.getCommand());
            stmt.setString(3, job.getState());
            stmt.setInt(4, job.getAttempts());
            stmt.setInt(5, job.getMaxRetries());
            
            stmt.executeUpdate();
        }
    }

    public Job dequeueJob(String workerId) throws SQLException {
        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            
            // Find next pending job or failed job ready for retry
            String sql = "SELECT * FROM jobs WHERE " +
                        "(state = 'pending' OR (state = 'failed' AND (next_retry_at IS NULL OR next_retry_at <= NOW()))) " +
                        "ORDER BY created_at ASC LIMIT 1 FOR UPDATE";
            
            PreparedStatement selectStmt = conn.prepareStatement(sql);
            ResultSet rs = selectStmt.executeQuery();
            
            if (rs.next()) {
                Job job = mapResultSetToJob(rs);
                
                // Update job to processing state
                String updateSql = "UPDATE jobs SET state = 'processing', updated_at = NOW() WHERE id = ?";
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setString(1, job.getId());
                updateStmt.executeUpdate();
                updateStmt.close();
                
                conn.commit();
                rs.close();
                selectStmt.close();
                return job;
            }
            
            conn.commit();
            rs.close();
            selectStmt.close();
            return null;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignore
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }

    public void markJobCompleted(String jobId) throws SQLException {
        String sql = "UPDATE jobs SET state = 'completed', updated_at = NOW() WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.executeUpdate();
        }
    }

    public void markJobFailed(String jobId, String errorMessage, long nextRetryDelay) throws SQLException {
        String sql = "UPDATE jobs SET state = 'failed', error_message = ?, " +
                    "attempts = attempts + 1, next_retry_at = TIMESTAMPADD(SECOND, ?, NOW()), " +
                    "updated_at = NOW() WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, errorMessage);
            stmt.setLong(2, nextRetryDelay);
            stmt.setString(3, jobId);
            stmt.executeUpdate();
        }
    }

    public void markJobDead(String jobId, String errorMessage) throws SQLException {
        String sql = "UPDATE jobs SET state = 'dead', error_message = ?, " +
                    "attempts = attempts + 1, updated_at = NOW() WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, errorMessage);
            stmt.setString(2, jobId);
            stmt.executeUpdate();
        }
    }

    public Job getJob(String jobId) throws SQLException {
        String sql = "SELECT * FROM jobs WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Job job = mapResultSetToJob(rs);
                rs.close();
                return job;
            }
            rs.close();
            return null;
        }
    }

    public List<Job> listJobs(String state) throws SQLException {
        List<Job> jobs = new ArrayList<>();
        String sql = state == null ? "SELECT * FROM jobs ORDER BY created_at DESC" :
                                    "SELECT * FROM jobs WHERE state = ? ORDER BY created_at DESC";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (state != null) {
                stmt.setString(1, state);
            }
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                jobs.add(mapResultSetToJob(rs));
            }
            rs.close();
        }
        
        return jobs;
    }

    public int[] getJobStats() throws SQLException {
        int[] stats = new int[5]; // pending, processing, completed, failed, dead
        String sql = "SELECT state, COUNT(*) as count FROM jobs GROUP BY state";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String state = rs.getString("state");
                int count = rs.getInt("count");
                
                switch (state) {
                    case "pending": stats[0] = count; break;
                    case "processing": stats[1] = count; break;
                    case "completed": stats[2] = count; break;
                    case "failed": stats[3] = count; break;
                    case "dead": stats[4] = count; break;
                }
            }
        }
        
        return stats;
    }

    public void retryDeadJob(String jobId) throws SQLException {
        String sql = "UPDATE jobs SET state = 'pending', attempts = 0, error_message = NULL, " +
                    "next_retry_at = NULL, updated_at = NOW() WHERE id = ? AND state = 'dead'";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Job not found in DLQ or not in dead state");
            }
        }
    }

    private Job mapResultSetToJob(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getString("id"));
        job.setCommand(rs.getString("command"));
        job.setState(rs.getString("state"));
        job.setAttempts(rs.getInt("attempts"));
        job.setMaxRetries(rs.getInt("max_retries"));
        job.setCreatedAt(rs.getTimestamp("created_at"));
        job.setUpdatedAt(rs.getTimestamp("updated_at"));
        job.setNextRetryAt(rs.getTimestamp("next_retry_at"));
        job.setErrorMessage(rs.getString("error_message"));
        return job;
    }
}
