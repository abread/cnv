package cnv.autoscaler.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class RoundRobinLBStrategy extends LBStrategy {
    private InstanceRegistry registry;
    private AtomicInteger idx = new AtomicInteger(0);

    public RoundRobinLBStrategy(InstanceRegistry registry) {
        this.registry = registry;
    }

    public Instance selectInstance() {
        Instance[] instances = (Instance[]) registry.readyInstances().toArray();
        int idx = this.idx.updateAndGet(v -> (v + 1) % instances.length);

        return instances[idx];
    }
}