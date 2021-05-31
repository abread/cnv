package pt.ulisboa.tecnico.cnv.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

/**
 * Stores each Metric that is a result of each request, associated to its thread ID
 * Only keeps the metrics while the requests are not ended
 */
public class MetricTracker {
    private static Map<Long, Metrics> localMetricStorage = new ConcurrentHashMap<>();

    public static void requestStart(String[] requestParams) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = new Metrics(requestParams);
        localMetricStorage.put(tid, metrics);
    }

    public static Metrics requestEnd() {
        long tid = Thread.currentThread().getId();
        return localMetricStorage.remove(tid);
    }

    /**
     * Thread-safety: localMetricStorage is a concurrent hashmap which supports concurrent get() calls
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

    /**
     * Stores the parameters of the requests and, at the end, the number of methods that the request invoked.
     */
    public static class Metrics {
        String[] requestParams;

        long methodCount = 0;

        public Metrics(String[] requestParams) {
            this.requestParams = requestParams;
        }

        public synchronized void print() {
            long img_area = -1;
            long vp_area = -1;
            SolverFactory.SolverType strategy = null;
            SolverArgumentParser args = new SolverArgumentParser(this.requestParams);
            img_area = args.getWidth() * args.getHeight();
            vp_area = (args.getY1() - args.getY0()) * (args.getX1() - args.getX0());
            strategy = args.getSolverStrategy();

            System.err.println();
            System.err.print(strategy);
            System.err.print(';');
            System.err.print(img_area);
            System.err.print(';');
            System.err.print(vp_area);
            System.err.print(';');
            System.err.print(this.methodCount);
            System.err.flush();
        }

    }
}
