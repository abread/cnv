package cnv.autoscaler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public class InstanceRegistry {
    private Logger logger = Logger.getLogger(InstanceRegistry.class.getName());
    private Map<String, Instance> readyInstances = new ConcurrentHashMap<>();
    private InstanceHealthChecker healthChecker = new InstanceHealthChecker();

    public Collection<Instance> readyInstances() {
        return this.readyInstances.values();
    }

    public int size() {
        return this.readyInstances.size();
    }

    public void stopInstance(String id) {
        Instance instance = readyInstances.remove(id);
        if (instance != null) {
            logger.info(String.format("Instance %s stopping", id));
            instance.stop();
        }
    }

    public void add(Instance instance) {
        logger.info(String.format("Instance %s ready", instance.id()));
        readyInstances.put(instance.id(), instance);
    }

    public Instance get(String id) {
        return readyInstances.get(id);
    }

    public void suspectInstanceBad(Instance instance) {
        healthChecker.suspectInstance(instance);
    }

    private class InstanceHealthChecker {
        private ConcurrentLinkedQueue<Instance> suspiciousInstances = new ConcurrentLinkedQueue<>();
        private Semaphore suspiciousInstancesExist = new Semaphore(0);
        private Thread workerThread;

        public InstanceHealthChecker() {
            workerThread = new Thread(new Worker(), "InstanceHealthChecker");
            workerThread.start();
        }

        public void suspectInstance(Instance instance) {
            // Try to keep queue free of duplicates
            if (!suspiciousInstances.contains(instance)) {
                suspiciousInstances.add(instance);

                if (suspiciousInstancesExist.availablePermits() <= 0) {
                    // try to not issue too many permits (>1 make the worker spin needlessly)
                    suspiciousInstancesExist.release();
                }
            }
        }

        private class Worker implements Runnable {
            public void run() {
                Instance instance;
                while (true) {
                    try {
                        suspiciousInstancesExist.acquire();
                    } catch (InterruptedException ignored) {}

                    while ((instance = suspiciousInstances.poll()) != null) {
                        try {
                            if (!instance.isHealthy()) {
                                InstanceRegistry.this.stopInstance(instance.id());
                                instance.forceStop(); // this instance is no good
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
