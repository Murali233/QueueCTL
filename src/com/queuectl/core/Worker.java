package com.queuectl.core;

import com.queuectl.models.Job;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

public class Worker implements Runnable {
    private String workerId;
    private JobQueue jobQueue;
    private ConfigManager configManager;
    private volatile boolean running;

    public Worker() {
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.jobQueue = new JobQueue();
        this.configManager = new ConfigManager();
        this.running = true;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        System.out.println("[" + workerId + "] Worker started");
        
        while (running) {
            try {
                Job job = jobQueue.dequeueJob(workerId);
                
                if (job == null) {
                    Thread.sleep(2000);
                    continue;
                }
                
                System.out.println("[" + workerId + "] Processing job: " + job.getId());
                processJob(job);
                
            } catch (InterruptedException e) {
                System.out.println("[" + workerId + "] Worker interrupted");
                break;
            } catch (Exception e) {
                System.err.println("[" + workerId + "] Error: " + e.getMessage());
            }
        }
        
        System.out.println("[" + workerId + "] Worker stopped");
    }

    private void processJob(Job job) {
        try {
            // Execute the command
            boolean success = executeCommand(job.getCommand());
            
            if (success) {
                jobQueue.markJobCompleted(job.getId());
                System.out.println("[" + workerId + "] Job " + job.getId() + " completed successfully");
            } else {
                handleFailure(job, "Command execution failed");
            }
            
        } catch (Exception e) {
            try {
                handleFailure(job, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[" + workerId + "] Error handling failure: " + ex.getMessage());
            }
        }
    }

    private boolean executeCommand(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            
            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("sh", "-c", command);
            }
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + workerId + "] " + line);
            }
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            System.err.println("[" + workerId + "] Command execution error: " + e.getMessage());
            return false;
        }
    }

    private void handleFailure(Job job, String errorMessage) throws Exception {
        int currentAttempts = job.getAttempts() + 1;
        int maxRetries = configManager.getConfigInt("max-retries", job.getMaxRetries());
        
        if (currentAttempts >= maxRetries) {
            // Move to DLQ
            jobQueue.markJobDead(job.getId(), errorMessage);
            System.out.println("[" + workerId + "] Job " + job.getId() + " moved to DLQ after " + currentAttempts + " attempts");
        } else {
            int backoffBase = configManager.getConfigInt("backoff-base", 2);
            long delay = (long) Math.pow(backoffBase, currentAttempts);
            
            jobQueue.markJobFailed(job.getId(), errorMessage, delay);
            System.out.println("[" + workerId + "] Job " + job.getId() + " failed (attempt " + currentAttempts + "/" + maxRetries + "). Retry in " + delay + " seconds");
        }
    }
}
