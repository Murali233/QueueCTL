package com.queuectl;

import com.queuectl.core.*;
import com.queuectl.models.Job;

import java.util.List;

public class QueueCTL {
    private JobQueue jobQueue;
    private ConfigManager configManager;
    private WorkerManager workerManager;

    public QueueCTL() {
        this.jobQueue = new JobQueue();
        this.configManager = new ConfigManager();
        this.workerManager = WorkerManager.getInstance();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        QueueCTL cli = new QueueCTL();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down gracefully...");
            cli.workerManager.stopWorkers();
        }));

        try {
            cli.execute(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private void execute(String[] args) throws Exception {
        String command = args[0].toLowerCase();

        switch (command) {
            case "enqueue":
                handleEnqueue(args);
                break;
            case "worker":
                handleWorker(args);
                break;
            case "status":
                handleStatus();
                break;
            case "list":
                handleList(args);
                break;
            case "dlq":
                handleDLQ(args);
                break;
            case "config":
                handleConfig(args);
                break;
            case "help":
                printUsage();
                break;
            default:
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
        }
    }

    private void handleEnqueue(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: queuectl enqueue <job-id> <command>");
            System.exit(1);
        }

        String jobId = args[1];
        String command = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";

        if (command.isEmpty()) {
            System.err.println("Error: Command cannot be empty");
            System.exit(1);
        }

        Job job = new Job(jobId, command);
        job.setMaxRetries(configManager.getConfigInt("max-retries", 3));
        
        jobQueue.enqueue(job);
        System.out.println("Job '" + jobId + "' enqueued successfully");
    }

    private void handleWorker(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: queuectl worker [start|stop] [--count N]");
            System.exit(1);
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "start":
                int count = 1;
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equals("--count") && i + 1 < args.length) {
                        count = Integer.parseInt(args[i + 1]);
                        break;
                    }
                }
                workerManager.startWorkers(count);
                
                // Keep the main thread alive
                System.out.println("Workers are running. Press Ctrl+C to stop.");
                Thread.currentThread().join();
                break;

            case "stop":
                workerManager.stopWorkers();
                break;

            default:
                System.err.println("Unknown worker action: " + action);
                System.exit(1);
        }
    }

    private void handleStatus() throws Exception {
        int[] stats = jobQueue.getJobStats();
        int activeWorkers = workerManager.getActiveWorkerCount();

        System.out.println("\n=== Queue Status ===");
        System.out.println("Pending:     " + stats[0]);
        System.out.println("Processing:  " + stats[1]);
        System.out.println("Completed:   " + stats[2]);
        System.out.println("Failed:      " + stats[3]);
        System.out.println("Dead (DLQ):  " + stats[4]);
        System.out.println("\nActive Workers: " + activeWorkers);
        
        if (activeWorkers > 0) {
            List<String> workerIds = workerManager.getWorkerIds();
            System.out.println("Worker IDs:");
            for (String id : workerIds) {
                System.out.println("  - " + id);
            }
        }
        System.out.println();
    }

    private void handleList(String[] args) throws Exception {
        String state = null;
        
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--state") && i + 1 < args.length) {
                state = args[i + 1];
                break;
            }
        }

        List<Job> jobs = jobQueue.listJobs(state);

        if (jobs.isEmpty()) {
            System.out.println("No jobs found" + (state != null ? " with state: " + state : ""));
            return;
        }

        System.out.println("\n=== Jobs" + (state != null ? " (State: " + state + ")" : "") + " ===");
        System.out.printf("%-20s %-15s %-40s %-10s %-10s\n", "ID", "State", "Command", "Attempts", "Max Retries");
        System.out.println("─".repeat(100));
        
        for (Job job : jobs) {
            String cmd = job.getCommand();
            if (cmd.length() > 37) {
                cmd = cmd.substring(0, 37) + "...";
            }
            System.out.printf("%-20s %-15s %-40s %-10d %-10d\n",
                    job.getId(), job.getState(), cmd, job.getAttempts(), job.getMaxRetries());
        }
        System.out.println();
    }

    private void handleDLQ(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: queuectl dlq [list|retry] [job-id]");
            System.exit(1);
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                List<Job> deadJobs = jobQueue.listJobs("dead");
                
                if (deadJobs.isEmpty()) {
                    System.out.println("No jobs in Dead Letter Queue");
                    return;
                }

                System.out.println("\n=== Dead Letter Queue ===");
                System.out.printf("%-20s %-40s %-10s %-30s\n", "ID", "Command", "Attempts", "Error");
                System.out.println("─".repeat(105));
                
                for (Job job : deadJobs) {
                    String cmd = job.getCommand();
                    if (cmd.length() > 37) {
                        cmd = cmd.substring(0, 37) + "...";
                    }
                    String error = job.getErrorMessage();
                    if (error != null && error.length() > 27) {
                        error = error.substring(0, 27) + "...";
                    }
                    System.out.printf("%-20s %-40s %-10d %-30s\n",
                            job.getId(), cmd, job.getAttempts(), error);
                }
                System.out.println();
                break;

            case "retry":
                if (args.length < 3) {
                    System.err.println("Usage: queuectl dlq retry <job-id>");
                    System.exit(1);
                }
                String jobId = args[2];
                jobQueue.retryDeadJob(jobId);
                System.out.println("Job '" + jobId + "' moved from DLQ back to pending queue");
                break;

            default:
                System.err.println("Unknown DLQ action: " + action);
                System.exit(1);
        }
    }

    private void handleConfig(String[] args) throws Exception {
        if (args.length < 2) {
            configManager.listConfig();
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "set":
                if (args.length < 4) {
                    System.err.println("Usage: queuectl config set <key> <value>");
                    System.exit(1);
                }
                String key = args[2];
                String value = args[3];
                configManager.setConfig(key, value);
                System.out.println("Configuration updated: " + key + " = " + value);
                break;

            case "get":
                if (args.length < 3) {
                    System.err.println("Usage: queuectl config get <key>");
                    System.exit(1);
                }
                key = args[2];
                value = configManager.getConfig(key);
                if (value != null) {
                    System.out.println(key + " = " + value);
                } else {
                    System.out.println("Configuration key '" + key + "' not found");
                }
                break;

            case "list":
                configManager.listConfig();
                break;

            default:
                System.err.println("Unknown config action: " + action);
                System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("\nQueueCTL - Background Job Queue System\n");
        System.out.println("USAGE:");
        System.out.println("  queuectl <command> [options]\n");
        System.out.println("COMMANDS:");
        System.out.println("  enqueue <job-id> <command>           Enqueue a new job");
        System.out.println("  worker start [--count N]             Start N worker(s) (default: 1)");
        System.out.println("  worker stop                          Stop all workers");
        System.out.println("  status                               Show queue status and statistics");
        System.out.println("  list [--state <state>]               List jobs (optionally filter by state)");
        System.out.println("  dlq list                             List jobs in Dead Letter Queue");
        System.out.println("  dlq retry <job-id>                   Retry a job from DLQ");
        System.out.println("  config [list]                        List all configuration");
        System.out.println("  config get <key>                     Get configuration value");
        System.out.println("  config set <key> <value>             Set configuration value");
        System.out.println("  help                                 Show this help message\n");
        System.out.println("EXAMPLES:");
        System.out.println("  queuectl enqueue job1 echo \"Hello World\"");
        System.out.println("  queuectl enqueue job2 sleep 5");
        System.out.println("  queuectl worker start --count 3");
        System.out.println("  queuectl status");
        System.out.println("  queuectl list --state pending");
        System.out.println("  queuectl dlq list");
        System.out.println("  queuectl config set max-retries 5");
        System.out.println();
    }
}
