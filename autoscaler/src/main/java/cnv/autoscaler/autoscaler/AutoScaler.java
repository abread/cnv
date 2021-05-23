package cnv.autoscaler.autoscaler;

import java.util.logging.Logger;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class AutoScaler {
    private Logger logger = Logger.getLogger(AutoScaler.class.getName());

    private InstanceRegistry instanceRegistry;
    private Timer autoScaleTimer;
    private static final long EXEC_PERIOD = 60 * 1000; // ms
    private TimerTask autoScaleTask = new AutoScaleTask();

    private int minInstances = 1;
    private int maxInstances = 3;
    private long maxInstanceLoad = Long.MAX_VALUE; // TODO: tune

    public AutoScaler(InstanceRegistry instanceRegistry) {
        this.instanceRegistry = instanceRegistry;
    }

    public void start() {
        autoScaleTimer = new Timer(true);
        autoScaleTimer.scheduleAtFixedRate(autoScaleTask, 0, EXEC_PERIOD);
    }

    public void stop() {
        autoScaleTimer.cancel();
        autoScaleTimer = null;
    }

    private Map<String, Double> instanceCpuUsage() {
        return instanceRegistry.readyInstances().stream()
            .collect(Collectors.toMap(Instance::id, Instance::getAvgCpuLoad));
    }

    private class AutoScaleTask extends TimerTask {
        public void run() {
            Map<String, Double> cpuUsage = instanceCpuUsage();

            // TODO: check, react by launching instances
            // TODO: also check instance load estimate?
        }
    }
}
