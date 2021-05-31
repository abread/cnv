package cnv.autoscaler.loadestimate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cnv.autoscaler.loadbalancer.RequestParams;

import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Implements a fast load estimator. Two fast methods are available, an estimate based on a linear regression model and
 * an estimate based on previous requests and a cache.
 * The fields {grid,prog,greedy}{A, B} correspond to the linear regression coefficients, taking the form
 * A*(viewport_area) + B, for each of the available strategies, grid-scan, progressive-scan and greedy-scan.
 */
public class FastEstimator {
    private final double gridA;
    private final double gridB;
    private final double progA;
    private final double progB;
    private final double greedyA;
    private final double greedyB;

    private static final int CACHE_SIZE = 512;
    private final Cache<RequestParams, Long> estimateCache;

    /**
     * Creates a FastEstimator with a fixed set of coefficients. The coefficients were chosen through experimental
     * analysis and fitting a linear regression model to a large number of previous requests.
     */
    public FastEstimator() {
        // values obtained experimentally for random workload
        // an ok starting point
        this(239.39541076087767, -4025509.6311474517, 2.464686058657854, 99524.01211201242, 2.568891967002501, -49490.54988982703);
    }

    /**
     * Initializes the FastEstimator with given model coefficients and creates a LRU cache which keeps up to
     * CACHE_SIZE entries. This cache is thread-safe, using a ConcurrentHashmap underneath.
     * @see <a href="https://github.com/ben-manes/caffeine/issues/392">Caffeinne thread-safety statement</a>
     */
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

    /**
     * Estimates the method count for a given viewportArea using the linear regression model.
     * @param algo the search algorithm used: "GRID_SCAN", "PROGRESSIVE_SCAN" or "GREEDY_RANGE_SCAN"
     * @param viewportArea the size of the viewport area
     * @return the number of estimate method counts for the given parameters
     */
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

    /**
     * Gets an estimate for a given RequestParams using the cache. It first checks whether an exactly equal request
     * is present in the cache, returning its value if it is, making it the most recently used item. If there is not,
     * similar requests are searched for and their average is used to compute an estimate, the position of those
     * RequestParams on the cache remain in the same place to avoid filling the cache with similar requests. If no
     * similar requests are found, an empty optional is returned indicating that no estimate could be found.
     * @param requestParams the corresponding RequestParams
     * @return the load estimate, if obtainable. Empty if no similar requests are in the cache
     */
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

    /**
     * Puts a request in the cache, making it the most recently used item. Should be used to store only accurate
     * estimates.
     * @param requestParams the RequestParams to put in the cache
     * @param loadEstimate the corresponding loadEstimate
     */
    public void putInCache(RequestParams requestParams, long loadEstimate) {
        estimateCache.put(requestParams, loadEstimate);
    }
}
