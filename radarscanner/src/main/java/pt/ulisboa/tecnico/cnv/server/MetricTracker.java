package pt.ulisboa.tecnico.cnv.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

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

    public static void incrInstrCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.instrCount += incr;
        metrics.bbCount += 1;
    }

    public static void incrAllocCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.allocCount += incr;
    }

    public static void incrLoadCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.loadCount += incr;
    }

    public static void incrLoadFieldCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.loadFieldCount += incr;
    }

    public static void incrStoreCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.storeCount += incr;
    }

    public static void incrStoreFieldCount(int incr) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);
        metrics.storeFieldCount += incr;
    }

    public static void saveBranchOutcome(int branchOutcome) {
        long tid = Thread.currentThread().getId();
        Metrics metrics = localMetricStorage.get(tid);

        if (branchOutcome == 0) {
            metrics.branchNotTakenCount++;
        } else {
            metrics.branchTakenCount++;
        }
    }

    /**
     * TODO
     *
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

    public static class Metrics {
        String[] requestParams;

        // method/instr/basicblock counter
        long methodCount = 0;
        long bbCount = 0;
        long instrCount = 0;

        // allocation counter
        long allocCount = 0;

        // load/store counter
        long loadCount = 0;
        long loadFieldCount = 0;
        long storeCount = 0;
        long storeFieldCount = 0;

        // branch counter
        long branchTakenCount = 0;
        long branchNotTakenCount = 0;

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
            System.err.print(';');
            System.err.print(this.bbCount);
            System.err.print(';');
            System.err.print(this.instrCount);
            System.err.print(';');
            System.err.print(this.allocCount);
            System.err.print(';');
            System.err.print(this.loadCount);
            System.err.print(';');
            System.err.print(this.loadFieldCount);
            System.err.print(';');
            System.err.print(this.storeCount);
            System.err.print(';');
            System.err.print(this.storeFieldCount);
            System.err.print(';');
            System.err.print(this.branchTakenCount);
            System.err.print(';');
            System.err.print(this.branchNotTakenCount);
            System.err.print(';');
            System.err.flush();
        }

    }
}
