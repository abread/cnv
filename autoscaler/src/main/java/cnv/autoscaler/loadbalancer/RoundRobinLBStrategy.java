package cnv.autoscaler.loadbalancer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class RoundRobinLBStrategy extends LBStrategy {
    private Logger logger = Logger.getLogger(RoundRobinLBStrategy.class.getName());
    private InstanceRegistry registry;
    private AtomicInteger idx = new AtomicInteger(0);

    public RoundRobinLBStrategy(InstanceRegistry registry) {
        this.registry = registry;
    }

    public RequestManager startRequest(String queryString) {
        Instance[] instances;
        Optional<UUID> requestId = Optional.empty();
        int idx;

        long loadEstimate = 1000; // TODO: compute from querystring

        do {
            instances = (Instance[]) registry.readyInstances().toArray();

            int size = instances.length;
            idx = this.idx.updateAndGet(v -> (v + 1) % size);

            requestId = instances[idx].requestStart(loadEstimate);
        } while (!requestId.isPresent());

        return new RequestManager(instances[idx], requestId.get());
    }
}