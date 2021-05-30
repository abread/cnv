package cnv.autoscaler.loadbalancer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class RoundRobinLBStrategy extends LBStrategy {
    private AtomicInteger idx = new AtomicInteger(0);

    public RoundRobinLBStrategy(InstanceRegistry registry) {
        super(registry);
    }

    public Request startRequest(String queryString, UUID requestId, HashSet<Instance> suspectedBadInstances) {
        List<Instance> instances;
        Optional<Request> request = Optional.empty();
        int idx;

        do {
            instances = new ArrayList<>(registry.readyInstances());

            int size = instances.size();
            idx = this.idx.updateAndGet(v -> (v + 1) % size);

            Instance instance = instances.get(idx);
            if (suspectedBadInstances.contains(instance)) {
                continue;
            }

            request = instance.requestStart(queryString, requestId);
        } while (!request.isPresent());

        return request.get();
    }
}