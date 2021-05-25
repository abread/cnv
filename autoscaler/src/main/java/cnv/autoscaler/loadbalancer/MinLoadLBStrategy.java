package cnv.autoscaler.loadbalancer;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class MinLoadLBStrategy extends LBStrategy {
    private Logger logger = Logger.getLogger(RoundRobinLBStrategy.class.getName());
    private InstanceRegistry registry;
    private AtomicReference<Estimator> estimator = new AtomicReference<>(new Estimator());

    private Map<Instance, Long> currentLoad = new WeakHashMap<>();

    public MinLoadLBStrategy(InstanceRegistry registry) {
        this.registry = registry;

        // TODO: launch background task to update estimator
    }

    public RequestManager startRequest(String queryString) {
        Instance instance;
        Optional<UUID> requestId = Optional.empty();

        long loadEstimate = estimateFromQueryString(queryString);

        do {
            synchronized (currentLoad) {
                instance = registry.readyInstances().stream()
                    .min(Comparator.comparingLong(inst -> currentLoad.getOrDefault(inst, 0L)))
                    .get();
            }

            requestId = instance.requestStart(loadEstimate);
        } while (!requestId.isPresent());

        return new RequestManager(instance, requestId.get());
    }

    private long estimateFromQueryString(String queryString) {
        long x0 = 0, x1 = 0, y0 = 0, y1 = 0;
        String algo = "";

        final String[] params = queryString.split("&");
        for (String param : params) {
            try {
                if (param.startsWith("s=")) {
                    algo = param.substring(2);
                } else if (param.startsWith("x0=")) {
                    x0 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("x1=")) {
                    x1 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("y0=")) {
                    y0 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("y1=")) {
                    y1 = Long.parseLong(param.substring(3));
                }
            } catch (NumberFormatException ignored) {
                // even if it fails, good defaults are provided
            }
        }

        long viewportArea = (x1 - x0) * (y1 - y0);
        viewportArea = Math.min(viewportArea, 0);

        return estimator.get().estimateMethodCount(algo, viewportArea);
    }

    private static class Estimator {
        private double gridA;
        private double gridB;
        private double progA;
        private double progB;
        private double greedyA;
        private double greedyB;

        Estimator() {
            // values obtained experimentally for random workload
            // an ok starting point
            this(239.39541076087767, -4025509.6311474517, 2.464686058657854, 99524.01211201242, 2.568891967002501, -49490.54988982703);
        }

        Estimator (double gridA, double gridB, double progA, double progB, double greedyA, double greedyB) {
            this.gridA = gridA;
            this.gridB = gridB;
            this.progA = progA;
            this.progB = progB;
            this.greedyA = greedyA;
            this.greedyB = greedyB;
        }

        long estimateMethodCount(String algo, long viewportArea) {
            switch (algo) {
                case "GRID_SCAN":
                    return Math.round(gridA * viewportArea + gridB);
                case "PROGRESSIVE_SCAN":
                    return Math.round(progA * viewportArea + progB);
                case "GREEDY_SCAN":
                    return Math.round(greedyA * viewportArea + greedyB);
                default:
                    return 1; // will just error out in the web server
            }
        }
    }
}