package com.queuectl.core;

import java.util.ArrayList;
import java.util.List;

public class WorkerManager {
    private static WorkerManager instance;
    private List<Worker> workers;
    private List<Thread> workerThreads;

    private WorkerManager() {
        this.workers = new ArrayList<>();
        this.workerThreads = new ArrayList<>();
    }

    public static synchronized WorkerManager getInstance() {
        if (instance == null) {
            instance = new WorkerManager();
        }
        return instance;
    }

    public void startWorkers(int count) {
        for (int i = 0; i < count; i++) {
            Worker worker = new Worker();
            Thread thread = new Thread(worker);
            
            workers.add(worker);
            workerThreads.add(thread);
            thread.start();
        }
        
        System.out.println("Started " + count + " worker(s)");
    }

    public void stopWorkers() {
        if (workers.isEmpty()) {
            System.out.println("No workers are currently running");
            return;
        }
        
        System.out.println("Stopping " + workers.size() + " worker(s)...");
        
        for (Worker worker : workers) {
            worker.stop();
        }
        
        for (Thread thread : workerThreads) {
            try {
                thread.join(5000); 
            } catch (InterruptedException e) {
                System.err.println("Error waiting for worker to stop: " + e.getMessage());
            }
        }
        
        workers.clear();
        workerThreads.clear();
        System.out.println("All workers stopped");
    }

    public int getActiveWorkerCount() {
        return workers.size();
    }

    public List<String> getWorkerIds() {
        List<String> ids = new ArrayList<>();
        for (Worker worker : workers) {
            ids.add(worker.getWorkerId());
        }
        return ids;
    }
}
