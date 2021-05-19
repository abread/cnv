package cnv.autoscaler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Instance {
    private String id;
    private String baseUri;

    private boolean isStopping = false;
    private long lastUpdate;
    private Map<UUID, Long> requestRemainingTimeEstimates = new HashMap<>();

    public Instance(String id, String baseUri) {
        this.id = id;
        this.baseUri = baseUri;
    }

    public synchronized Optional<UUID> requestStart(long timeEstimateMilliseconds) {
        if (isStopping) {
            return Optional.empty();
        }

        UUID id = UUID.randomUUID();
        updateTimeEstimates(requestRemainingTimeEstimates.size() + 1);
        requestRemainingTimeEstimates.put(id, timeEstimateMilliseconds * requestRemainingTimeEstimates.size());
        return Optional.of(id);
    }

    public synchronized void requestEnd(UUID requestId) {
        updateTimeEstimates(requestRemainingTimeEstimates.size() - 1);
        requestRemainingTimeEstimates.remove(requestId);

        if (isStopping && requestRemainingTimeEstimates.isEmpty()) {
            this.stop();
        }
    }

    private void updateTimeEstimates(int newSize) {
        long now = System.currentTimeMillis();
        long delta = now - lastUpdate;
        int oldSize = requestRemainingTimeEstimates.size();

        requestRemainingTimeEstimates
            .replaceAll((_k, oldEstimate) -> Math.max(1, (oldEstimate - delta) / oldSize * newSize));

        lastUpdate = now;
    }

    public synchronized void stop() {
        isStopping = true;

        if (!requestRemainingTimeEstimates.isEmpty()) {
            return; // do it later (when requests are done)
        }
    }

    public synchronized long currentLoad() {
        return requestRemainingTimeEstimates
            .values()
            .stream()
            .reduce(0L, Long::sum);
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

        public synchronized void stop() {
            super.stop();

            if (this.currentLoad() == 0) {
                AwsInstanceManager.terminateInstance(this.id());
            }
        }
    }
}
