package cnv.autoscaler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

public class Instance {
    protected Logger logger;
    private String id;
    private String baseUri;

    private boolean isStopping = false;
    private Map<UUID, Long> requestLoadEstimates = new HashMap<>();

    private static final AtomicReference<Estimator> estimator = new AtomicReference<>(new Estimator());
    // TODO: launch background thread that updates the estimator

    public Instance(String id, String baseUri) {
        this.id = id;
        this.baseUri = baseUri;

        logger = Logger.getLogger(Instance.class.getName() + ":" + id);
    }

    public synchronized Optional<UUID> requestStart(String queryString) {
        if (isStopping) {
            return Optional.empty();
        }

        long loadEstimate = estimateFromQueryString(queryString);

        UUID id = UUID.randomUUID();
        requestLoadEstimates.put(id, Math.max(loadEstimate, 1L));
        return Optional.of(id);
    }

    public synchronized void requestEnd(UUID requestId) {
        requestLoadEstimates.remove(requestId);

        if (isStopping && requestLoadEstimates.isEmpty()) {
            this.stop();
        }
    }

    public synchronized void stop() {
        isStopping = true;

        if (!requestLoadEstimates.isEmpty()) {
            return; // do it later (when requests are done)
        }
    }

    public synchronized long currentLoad() {
        return requestLoadEstimates
            .values()
            .stream()
            .reduce(0L, Long::sum);
    }

    public synchronized int currentRequestCount() {
        return requestLoadEstimates.size();
    }

    public double getAvgCpuLoad() {
        if (currentRequestCount() > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    public String getBaseUri() {
        return this.baseUri;
    }

    public String id() {
        return this.id;
    }

    public boolean isHealthy() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String uri = getBaseUri() + "/test";
            logger.info("Checking health of "+uri);
            final HttpGet innerRequest = new HttpGet(uri);
            client.execute(innerRequest).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static class AwsInstance extends Instance {
        public AwsInstance(com.amazonaws.services.ec2.model.Instance awsMetadata) {
            super(awsMetadata.getInstanceId(), "http://" + AwsInstanceManager.getPublicDnsName(awsMetadata.getInstanceId()) + ":8000");
            logger.info(awsMetadata.toString());
        }

        public double getAvgCpuLoad() {
            return AwsInstanceManager.getAvgCpuUsage(this.id())
                // fallback to local approximation when CloudWatch is unavailable
                .orElseGet(() -> super.getAvgCpuLoad());
        }

        public synchronized void stop() {
            super.stop();

            if (this.currentRequestCount() == 0) {
                AwsInstanceManager.terminateInstances(this.id());
            }
        }
    }

    private static long estimateFromQueryString(String queryString) {
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
        viewportArea = Math.max(viewportArea, 0);

        return Math.max(1, estimator.get().estimateMethodCount(algo, viewportArea));
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
