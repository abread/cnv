package cnv.autoscaler.loadbalancer;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class MinLoadLBStrategy extends LBStrategy {
    public MinLoadLBStrategy(InstanceRegistry registry) {
        super(registry);
    }

    public Request startRequest(String queryString, UUID requestId, HashSet<Instance> suspectedBadInstances) {
        Instance instance;
        Optional<Request> request = Optional.empty();

        do {
            instance = registry.readyInstances().stream()
                .filter(inst -> !suspectedBadInstances.contains(inst))
                .min(Comparator.comparingLong(inst -> inst.currentLoad()))
                .get();

            request = instance.requestStart(queryString, requestId);
        } while (!request.isPresent());

        return request.get();
    }
}