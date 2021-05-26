package cnv.autoscaler.loadbalancer;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class MinLoadLBStrategy extends LBStrategy {
    private Logger logger = Logger.getLogger(RoundRobinLBStrategy.class.getName());
    private InstanceRegistry registry;

    private Map<Instance, Long> currentLoad = new WeakHashMap<>();

    public MinLoadLBStrategy(InstanceRegistry registry) {
        this.registry = registry;
    }

    public Request startRequest(String queryString) {
        Instance instance;
        Optional<Request> request = Optional.empty();

        do {
            synchronized (currentLoad) {
                instance = registry.readyInstances().stream()
                    .min(Comparator.comparingLong(inst -> currentLoad.getOrDefault(inst, 0L)))
                    .get();
            }

            request = instance.requestStart(queryString);
        } while (!request.isPresent());

        return request.get();
    }
}