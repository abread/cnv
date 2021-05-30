package cnv.autoscaler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class InstanceRegistry {
    private Logger logger = Logger.getLogger(InstanceRegistry.class.getName());
    private AtomicReference<Optional<UnhealthyInstanceTerminatedCallback>> unhealthyInstanceTerminatedCallback = new AtomicReference<>();
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

    public void setUnhealthyInstanceTerminatedCallback(UnhealthyInstanceTerminatedCallback cb) {
        this.unhealthyInstanceTerminatedCallback.set(Optional.of(cb));
    }

    private class InstanceHealthChecker {
        private ConcurrentLinkedQueue<Instance> suspiciousInstances = new ConcurrentLinkedQueue<>();
        private Semaphore suspiciousInstancesExist = new Semaphore(0);
        private static final int N_WORKERS = 4;
        private Thread[] workerThreads;

        public InstanceHealthChecker() {
            workerThreads = new Thread[N_WORKERS];
            for (int i = 0; i < N_WORKERS; i++) {
                workerThreads[i] = new Thread(new Worker(), "InstanceHealthChecker");
                workerThreads[i].start();
            }
        }

        public void suspectInstance(Instance instance) {
            // Try to keep queue free of duplicates
            if (!suspiciousInstances.contains(instance)) {
                suspiciousInstances.add(instance);

                if (suspiciousInstancesExist.availablePermits() <= N_WORKERS) {
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
                                unhealthyInstanceTerminatedCallback.get().ifPresent(cb -> cb.call());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface UnhealthyInstanceTerminatedCallback {
        public void call();
    }
}
