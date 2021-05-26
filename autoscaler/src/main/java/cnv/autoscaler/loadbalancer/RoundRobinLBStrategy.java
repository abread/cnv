package cnv.autoscaler.loadbalancer;

import java.util.ArrayList;
import java.util.List;
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
        List<Instance> instances;
        Optional<UUID> requestId = Optional.empty();
        int idx;

        do {
            instances = new ArrayList<>(registry.readyInstances());

            int size = instances.size();
            idx = this.idx.updateAndGet(v -> (v + 1) % size);

            requestId = instances.get(idx).requestStart(queryString);
        } while (!requestId.isPresent());

        return new RequestManager(instances.get(idx), requestId.get());
    }
}