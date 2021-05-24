package cnv.autoscaler.autoscaler;

import java.util.logging.Logger;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import cnv.autoscaler.AwsInstanceManager;
import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class AutoScaler {
    private Logger logger = Logger.getLogger(AutoScaler.class.getName());

    private InstanceRegistry instanceRegistry;
    private Timer autoScaleTimer;
    private static final long EXEC_PERIOD = 60 * 1000; // ms
    private TimerTask autoScaleTask = new AutoScaleTask();

    private AtomicInteger pendingInstances = new AtomicInteger(0);

    private static final int MIN_INSTANCES = 1;
    private static final int MAX_INSTANCES = 3;
    private static final long MAX_INSTANCE_LOAD = Long.MAX_VALUE; // TODO: tune

    public AutoScaler(InstanceRegistry instanceRegistry) {
        this.instanceRegistry = instanceRegistry;
    }

    public void start() {
        autoScaleTimer = new Timer(true);
        autoScaleTimer.scheduleAtFixedRate(autoScaleTask, 0, EXEC_PERIOD);
    }

    public void stop() {
        autoScaleTimer.cancel();
        autoScaleTimer = null;
    }

    private Pair<Map<String, Double>, Integer> instanceCpuUsageAndPendingInstances() {
        Collection<Instance> readyInstances;
        int pendingInstances;

        // keep pendingInstances consistent with instaceRegistry.size()
        synchronized (this) {
            readyInstances = instanceRegistry.readyInstances();
            pendingInstances = this.pendingInstances.get();
        }

        return Pair.of(
            readyInstances.stream()
                .collect(Collectors.toMap(Instance::id, Instance::getAvgCpuLoad)),
            pendingInstances
        );
    }

    private static class Pair<F, S> {
        public F first;
        public S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public static<T, U> Pair<T, U> of(T first, U second) {
            return new Pair<T, U>(first, second);
        }
    }

    private class AutoScaleTask extends TimerTask {
        private final ToDoubleFunction<Map.Entry<String, Double>> CPU_USAGE_EXTRACTOR = entry -> entry.getValue();
        private final ToLongFunction<Map.Entry<String, Double>> LOAD_EXTRACTOR = entry -> instanceRegistry
                .get(entry.getKey()).currentLoad();

        public void run() {
            Pair<Map<String, Double>, Integer> p = instanceCpuUsageAndPendingInstances();
            Map<String, Double> cpuUsage = p.first;
            int pendingInstances = p.second;

            double avgCpuUsage = cpuUsage.values().stream().mapToDouble(x -> x).average().orElse(0);
            // account for pending instances
            avgCpuUsage = avgCpuUsage * cpuUsage.size() / (cpuUsage.size() + pendingInstances);

            double avgLoad = cpuUsage.entrySet().stream().mapToLong(LOAD_EXTRACTOR).average().orElse(0);
            // account for pending instances
            avgLoad = avgLoad * cpuUsage.size() / (cpuUsage.size() + pendingInstances);

            // Decide how many instances to add/remove
            int delta = 0;
            if (avgCpuUsage >= 0.7 || avgLoad >= MAX_INSTANCE_LOAD) {
                delta += 1;
            } else if (avgCpuUsage <= 0.3) {
                delta -= 1;
            }

            // current + pending + delta <= maxInstances
            delta = Math.min(delta, MAX_INSTANCES - cpuUsage.size() - pendingInstances);
            // current + pending + delta >= minInstances
            delta = Math.max(delta, MIN_INSTANCES - cpuUsage.size() - pendingInstances);

            if (delta > 0) {
                logger.info(String.format("Starting %d instances", delta));
                // current + pending + n <= maxInstances
                new ScaleUp(delta).run();
            } else if (delta < 0) {
                int n = -delta;
                logger.info(String.format("Stopping %d instances", n));

                // stop the instances with less cpu/predicted load
                cpuUsage.entrySet().stream()
                        .sorted(Comparator.comparingDouble(CPU_USAGE_EXTRACTOR)
                                .thenComparing(Comparator.comparingLong(LOAD_EXTRACTOR)))
                        .limit(n).forEach(entry -> instanceRegistry.stopInstance(entry.getKey()));
            }
        }
    }

    private class ScaleUp implements Runnable {
        private static final long WAIT_TIME = 10 * 1000; // ms
        public int n;

        public ScaleUp(int n) {
            this.n = n;

            // need not be synchronized, because it's only called by the AutoScaleTask
            // after fetching pendingInstances+currentReadyInstances
            pendingInstances.addAndGet(n);
        }

        public void run() {
            List<Instance> startedInstances = AwsInstanceManager.launchInstances(n).stream()
                    .map(awsMetadata -> new Instance.AwsInstance(awsMetadata)).collect(Collectors.toList());

            while (!startedInstances.isEmpty()) {
                for (Instance instance : startedInstances) {
                    if (instance.isHealthy()) {
                        logger.info(String.format("Instance %s now ready to answer requests", instance.id()));
                        startedInstances.remove(instance);

                        // keep pendingInstances consistent with instaceRegistry.size()
                        synchronized (AutoScaler.this) {
                            instanceRegistry.add(instance);
                            pendingInstances.decrementAndGet();
                        }
                    }
                }

                try {
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException ignored) {
                    // i was giving you a chance to work but ok
                }
            }
        }
    }
}
