package cnv.autoscaler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import cnv.autoscaler.aws.AwsInstanceManager;
import cnv.autoscaler.loadbalancer.Request;
import cnv.autoscaler.loadbalancer.RequestParams;
import cnv.autoscaler.loadestimate.FastEstimator;
import cnv.autoscaler.loadestimate.BetterEstimateFetcher;

public class Instance {
    protected Logger logger;
    private String id;
    private String baseUri;

    private boolean isStopping = false;
    private Map<Request, Long> requestLoadEstimates = new HashMap<>();
    private AtomicLong currentLoad = new AtomicLong(0);

    private static final AtomicReference<FastEstimator> estimator = new AtomicReference<>(new FastEstimator());
    // TODO: launch background thread that updates the estimator

    private static final BetterEstimateFetcher betterEstimateFetcher = new BetterEstimateFetcher();

    public Instance(String id, String baseUri) {
        this.id = id;
        this.baseUri = baseUri;

        logger = Logger.getLogger(Instance.class.getName() + ":" + id);
    }

    public synchronized Optional<Request> requestStart(String queryString) {
        if (isStopping) {
            return Optional.empty();
        }

        Request req = new Request(this, new RequestParams(queryString));

        String algo = req.params().algo;
        long viewportArea = req.params().viewportArea();
        long loadEstimate = estimator.get().estimateMethodCount(algo, viewportArea);

        requestLoadEstimates.put(req, 0L);
        this.updateRequestEstimate(req, loadEstimate);

        // try to get a better estimate in the meantime (out of critical path)
        betterEstimateFetcher.queueEstimationRequest(req);

        return Optional.of(req);
    }

    public synchronized void updateRequestEstimate(Request req, long newEstimate) {
        newEstimate = Math.max(newEstimate, 1L);

        if (requestLoadEstimates.containsKey(req)) {
            logger.info(String.format("Updating estimate for request %s with %d", req.getId().toString(), newEstimate));

            long prevEstimate = requestLoadEstimates.put(req, newEstimate);
            currentLoad.addAndGet(newEstimate - prevEstimate);
        }
    }

    public synchronized void requestEnd(Request req, Optional<Long> methodCount) {
        long estimate = requestLoadEstimates.remove(req);
        currentLoad.addAndGet(-estimate);

        methodCount.ifPresent(c -> {
            logger.info(String.format("Request %s had %d method calls", req.getId(), c));
        });

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

    public long currentLoad() {
        return currentLoad.get();
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
}
