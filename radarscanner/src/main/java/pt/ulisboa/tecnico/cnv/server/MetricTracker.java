package pt.ulisboa.tecnico.cnv.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricTracker {
    private static Map<Long, Metrics> localMetricStorage = new ConcurrentHashMap<>();

    public static void requestStart(String[] requestParams) {
        long tid = Thread.currentThread().getId();
        localMetricStorage.put(tid, new Metrics(requestParams));
    }

    public static Metrics requestEnd() {
        long tid = Thread.currentThread().getId();
        return localMetricStorage.remove(tid);
    }

    public static void incrInstrCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.instrCount += incr;
        metrics.bbCount += 1;
    }

    /**
     * TODO
     *
     * Thread-safety: localMetrics is a concurrent hashmap which supports concurrent get() calls
     * Only the thread T accesses the Metrics object stored with key T, so no concurrent accesses are performed on
     * Metric objects.
     *
     * @param ignored
     */
    @SuppressWarnings("unused")
    public static void incrMethodCount(int ignored) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.methodCount += 1;
    }

    public static class Metrics {
        String[] requestParams;
        int methodCount = 0;
        int bbCount = 0;
        int instrCount = 0;

        Metrics(String[] requestParams) {
            this.requestParams = requestParams;
        }

        public synchronized void print() {
            System.out.println("For request with arguments: " + requestParams);
            System.out.println("Dynamic information summary:");
            System.out.println("Number of methods:      " + methodCount);
            System.out.println("Number of basic blocks: " + bbCount);
            System.out.println("Number of instructions: " + instrCount);

            if (methodCount == 0) {
                return;
            }

            float instrPerBb = (float) instrCount / (float) bbCount;
            float instrPerMethod = (float) instrCount / (float) methodCount;
            float bbPerMethod = (float) bbCount / (float) methodCount;

            System.out.println("Average number of instructions per basic block: " + instrPerBb);
            System.out.println("Average number of instructions per method:      " + instrPerMethod);
            System.out.println("Average number of basic blocks per method:      " + bbPerMethod);
        }

    }
}
