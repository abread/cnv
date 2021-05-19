package cnv.autoscaler;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceRegistry {
    private Map<String, Instance> readyInstances = new ConcurrentHashMap<>();

    public Collection<Instance> readyInstances() {
        return this.readyInstances.values();
    }

    public void stopInstance(String id) {
        Instance instance = readyInstances.remove(id);
        instance.stop();
    }

    public void add(Instance instance) {
        readyInstances.put(instance.id(), instance);
    }
}
