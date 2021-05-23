package cnv.autoscaler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class Instance {
    private Logger logger;
    private String id;
    private String baseUri;

    private boolean isStopping = false;
    private Map<UUID, Long> requestLoadEstimates = new HashMap<>();

    public Instance(String id, String baseUri) {
        this.id = id;
        this.baseUri = baseUri;

        logger = Logger.getLogger(Instance.class.getName() + ":" + id);
    }

    public synchronized Optional<UUID> requestStart(long loadEstimate) {
        if (isStopping) {
            return Optional.empty();
        }

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

    public static class AwsInstance extends Instance {
        public AwsInstance(com.amazonaws.services.ec2.model.Instance awsMetadata) {
            super(awsMetadata.getInstanceId(), "http://" + awsMetadata.getPublicDnsName() + ":8000");
        }

        public double getAvgCpuLoad() {
            return AwsInstanceManager.getAvgCpuUsage(this.id())
                // fallback to local approximation when CloudWatch is unavailable
                .orElseGet(() -> super.getAvgCpuLoad());
        }

        public synchronized void stop() {
            super.stop();

            if (this.currentRequestCount() == 0) {
                AwsInstanceManager.terminateInstance(this.id());
            }
        }
    }
}
