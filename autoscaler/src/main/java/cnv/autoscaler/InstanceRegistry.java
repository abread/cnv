package cnv.autoscaler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class InstanceRegistry {
    private Logger logger = Logger.getLogger(InstanceRegistry.class.getName());
    private Map<String, Instance> readyInstances = new ConcurrentHashMap<>();

    public Collection<Instance> readyInstances() {
        return this.readyInstances.values();
    }

    public void stopInstance(String id) {
        Instance instance = readyInstances.remove(id);
        logger.info(String.format("Instance %s stopping", id));
        instance.stop();
    }

    public void add(Instance instance) {
        logger.info(String.format("Instance %s ready", instance.id()));
        readyInstances.put(instance.id(), instance);
    }
}
