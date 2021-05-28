package cnv.autoscaler.loadestimate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cnv.autoscaler.loadbalancer.RequestParams;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public class FastEstimator {
    private double gridA;
    private double gridB;
    private double progA;
    private double progB;
    private double greedyA;
    private double greedyB;

    private static final int CACHE_SIZE = 512;
    private Cache<RequestParams, Long> estimateCache;

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
        this.estimateCache = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();
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

    public OptionalLong getFromCache(RequestParams requestParams) {
        Long value = estimateCache.getIfPresent(requestParams);
        if (value != null) {
            return OptionalLong.of(value);
        }

        OptionalDouble estimate = estimateCache.asMap().entrySet().stream()
            .filter(r -> requestParams.similarTo(r.getKey()))
            .mapToDouble(r -> Double.valueOf(r.getValue()))
            .average();

        if (!estimate.isPresent()) {
            return OptionalLong.empty();
        } else {
            return OptionalLong.of(Math.round(estimate.getAsDouble()));
        }
    }

    public void putInCache(RequestParams requestParams, long loadEstimate) {
        estimateCache.put(requestParams, loadEstimate);
    }
}