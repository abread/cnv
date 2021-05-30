package cnv.autoscaler.autoscaler;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;
import cnv.autoscaler.aws.AwsInstanceManager;

public class AutoScaler {
    private Logger logger = Logger.getLogger(AutoScaler.class.getName());

    private InstanceRegistry instanceRegistry;
    private Timer autoScaleTimer;
    private static final long EXEC_PERIOD = 30 * 1000; // ms
    private TimerTask autoScaleTask = new AutoScaleTask();

    private AtomicLong pendingInstances = new AtomicLong(0);

    private static final int MIN_INSTANCES = 1;
    private static final int MAX_INSTANCES = 3;
    private static final double MAX_INSTANCE_CPU = 1-Double.MIN_VALUE; // these requests use the full cpu
    private static final double MIN_INSTANCE_CPU = 0.7;
    private static final long MAX_INSTANCE_LOAD = 20000000; // TODO: don't use eyeballed value

    private static final long MAX_CHANGE = 2; // maximum number of instances started/stopped

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

    public void runOnce() {
        autoScaleTask.run();
    }

    private Pair<Map<String, Double>, Long> instanceCpuUsageAndPendingInstances() {
        Collection<Instance> readyInstances;
        long pendingInstances;

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
            Pair<Map<String, Double>, Long> p = instanceCpuUsageAndPendingInstances();
            Map<String, Double> cpuUsage = p.first;
            long pendingInstances = p.second;

            long size = cpuUsage.size() + pendingInstances;
            long nonZeroSize = Math.max(1L, size); // for divisions to work

            double avgCpuUsage = cpuUsage.values().stream().mapToDouble(x -> x).average().orElse(0);
            // account for pending instances
            avgCpuUsage = avgCpuUsage * cpuUsage.size() / nonZeroSize;

            double avgLoad = cpuUsage.entrySet().stream().mapToLong(LOAD_EXTRACTOR).average().orElse(0);
            // account for pending instances
            avgLoad = avgLoad * cpuUsage.size() / nonZeroSize;

            // Decide how many instances to add/remove
            long delta = 0L;
            if (avgCpuUsage >= MAX_INSTANCE_CPU || avgLoad >= MAX_INSTANCE_LOAD) {
                // newavg = avg * (size) / (size+delta)
                // newavg < THRESHOLD
                // avg*size < THRESHOLD*size + THRESHOLD*delta
                // delta > (avg - THRESHOLD)*size/THRESHOLD
                logger.info(String.format("Maybe scale up? CPU usage is %f and load is %f", avgCpuUsage, avgLoad));
                long cpuDelta = Math.round(Math.ceil((avgCpuUsage - MAX_INSTANCE_CPU)*size / MAX_INSTANCE_CPU));
                long loadDelta = Math.round(Math.ceil((avgLoad - MAX_INSTANCE_LOAD)*size / MAX_INSTANCE_LOAD));
                delta = Math.max(cpuDelta, loadDelta);
            } else if (avgCpuUsage <= MIN_INSTANCE_CPU) {
                // newavg = avg * (size) / (size+delta)
                // newavg > THRESHOLD
                // avg*size > THRESHOLD*size + THRESHOLD*delta
                // delta < (avg - THRESHOLD)*size/THRESHOLD
                logger.info(String.format("Maybe scale down? CPU usage is %f and load is %f", avgCpuUsage, avgLoad));
                long cpuDelta = Math.round(Math.floor((avgCpuUsage - MIN_INSTANCE_CPU)*size / MIN_INSTANCE_CPU));
                delta = cpuDelta;
            }

            // current + pending + delta <= maxInstances
            delta = Math.min(delta, MAX_INSTANCES - size);
            // current + pending + delta >= minInstances
            delta = Math.max(delta, MIN_INSTANCES - size);

            logger.info("Scaling " + delta + " (pending="+pendingInstances+",current="+cpuUsage.size()+")");
            if (delta > 0) {
                delta = Math.min(delta, MAX_CHANGE);
                logger.info(String.format("Starting %d instances", delta));
                // current + pending + n <= maxInstances
                new Thread(new ScaleUp(delta)).start();
            } else if (delta < 0) {
                long n = Math.min(-delta, MAX_CHANGE);
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
        private static final long WAIT_TIME = 1 * 1000; // ms
        public long n;

        public ScaleUp(long n) {
            this.n = n;

            // need not be synchronized, because it's only called by the AutoScaleTask
            // after fetching pendingInstances+currentReadyInstances
            pendingInstances.addAndGet(n);
        }

        public void run() {
            List<Instance> startedInstances = AwsInstanceManager.launchInstances(Long.valueOf(n).intValue()).stream()
                    .map(awsMetadata -> new Instance.AwsInstance(awsMetadata)).collect(Collectors.toList());

            while (!startedInstances.isEmpty()) {
                // clone list on each iteration to eliminate concurrent modification
                for (Instance instance : new ArrayList<>(startedInstances)) {
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
