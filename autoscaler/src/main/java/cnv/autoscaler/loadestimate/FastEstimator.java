package cnv.autoscaler.loadestimate;

public class FastEstimator {
    private double gridA;
    private double gridB;
    private double progA;
    private double progB;
    private double greedyA;
    private double greedyB;

    public FastEstimator() {
        // values obtained experimentally for random workload
        // an ok starting point
        this(239.39541076087767, -4025509.6311474517, 2.464686058657854, 99524.01211201242, 2.568891967002501, -49490.54988982703);
    }

    public FastEstimator (double gridA, double gridB, double progA, double progB, double greedyA, double greedyB) {
        this.gridA = gridA;
        this.gridB = gridB;
        this.progA = progA;
        this.progB = progB;
        this.greedyA = greedyA;
        this.greedyB = greedyB;
    }

    public long estimateMethodCount(String algo, long viewportArea) {
        switch (algo) {
            case "GRID_SCAN":
                return Math.round(gridA * viewportArea + gridB);
            case "PROGRESSIVE_SCAN":
                return Math.round(progA * viewportArea + progB);
            case "GREEDY_RANGE_SCAN":
                return Math.round(greedyA * viewportArea + greedyB);
            default:
                return 1; // will just error out in the web server
        }
    }
}