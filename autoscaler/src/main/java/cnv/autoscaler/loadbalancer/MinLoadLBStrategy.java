package cnv.autoscaler.loadbalancer;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class MinLoadLBStrategy extends LBStrategy {
    private InstanceRegistry registry;

    public MinLoadLBStrategy(InstanceRegistry registry) {
        this.registry = registry;
    }

    public Request startRequest(String queryString, UUID requestId) {
        Instance instance;
        Optional<Request> request = Optional.empty();

        do {
            instance = registry.readyInstances().stream()
                .min(Comparator.comparingLong(inst -> inst.currentLoad()))
                .get();

            request = instance.requestStart(queryString, requestId);
        } while (!request.isPresent());

        return request.get();
    }

    protected void suspectInstance(Instance instance) {
        registry.suspectInstanceBad(instance);
    }
}