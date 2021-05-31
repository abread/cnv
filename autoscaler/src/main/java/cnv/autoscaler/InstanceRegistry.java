package cnv.autoscaler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Keeps track of registered instances in a thread-safe manner.
 */
public class InstanceRegistry {
    private final Logger logger = Logger.getLogger(InstanceRegistry.class.getName());
    private final AtomicReference<Optional<UnhealthyInstanceTerminatedCallback>> unhealthyInstanceTerminatedCallback = new AtomicReference<>();
    private final Map<String, Instance> readyInstances = new ConcurrentHashMap<>();
    private final InstanceHealthChecker healthChecker = new InstanceHealthChecker();

    /**
     * @return the collection of instances that are ready
     */
    public Collection<Instance> readyInstances() {
        return this.readyInstances.values();
    }

    /**
     * @return the number of instances that are ready
     */
    public int size() {
        return this.readyInstances.size();
    }

    /**
     * Removes the instance from the current ready instances and orders it to stop execution. Since it is removed,
     * it accepts no further requests.
     * @param id the id of the instance to be stopped
     */
    public void stopInstance(String id) {
        Instance instance = readyInstances.remove(id);
        if (instance != null) {
            logger.info(String.format("Instance %s stopping", id));
            instance.stop();
        }
    }

    /**
     * Adds an instance to the current collection of ready instances.
      * @param instance the instance to be added
     */
    public void add(Instance instance) {
        logger.info(String.format("Instance %s ready", instance.id()));
        readyInstances.put(instance.id(), instance);
    }

    /**
     * @param id the id of the instance
     * @return the corresponding instance associated with given id
     */
    public Instance get(String id) {
        return readyInstances.get(id);
    }

    /**
     * Used to tell the HealthChecker that a certain instance is suspected to be bad. An instance that is suspected
     * to be bad will be checked by the healthchecker which provides the final verdict in relation to the instance's
     * current status.
     * @param instance the instance to mark as bad
     */
    public void suspectInstanceBad(Instance instance) {
        healthChecker.suspectInstance(instance);
    }

    /**
     * Sets a callback to be called when an instance is terminated. This can be used for the autoscaler to
     * react more quickly when an instance is terminated, instead of waiting for the next autoscaler worker tick.
     * @param cb the fun
     */
    public void setUnhealthyInstanceTerminatedCallback(UnhealthyInstanceTerminatedCallback cb) {
        this.unhealthyInstanceTerminatedCallback.set(Optional.of(cb));
    }

    /**
     * Implements an instance health checker running in parallel.
     */
    private class InstanceHealthChecker {
        private final ConcurrentLinkedQueue<Instance> suspiciousInstances = new ConcurrentLinkedQueue<>();
        private final Semaphore suspiciousInstancesExist = new Semaphore(0);
        private static final int N_WORKERS = 4;

        /**
         * Launches N_WORKERS worker threads that check the current instances' health.
         */
        public InstanceHealthChecker() {
            Thread[] workerThreads = new Thread[N_WORKERS];
            for (int i = 0; i < N_WORKERS; i++) {
                workerThreads[i] = new Thread(new Worker(), "InstanceHealthChecker");
                workerThreads[i].start();
            }
        }

        /**
         * Marks an instance as suspected. Instances that are suspected are then evaluated by the Worker thread.
         * @param instance the instance to mark as suspected
         */
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

        /**
         * The healthchecking thread. Polls for current suspicious instances and performs additional health checks
         * to check their current health status. If an instance is not healthy, it is terminated and the corresponding
         * callback is called.
         */
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
        void call();
    }
}
