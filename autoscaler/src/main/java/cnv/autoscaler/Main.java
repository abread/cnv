package cnv.autoscaler;

import java.io.IOException;

import cnv.autoscaler.autoscaler.AutoScaler;
import cnv.autoscaler.loadbalancer.LoadBalancer;

public class Main {
    static {
        if (System.getProperty("java.util.logging.SimpleFormatter.format", "").isEmpty()) {
            System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        }
    }

    public static void main(String[] args) throws IOException {
        String address = System.getProperty("lb.address", "0.0.0.0");
        int port = Integer.parseInt(System.getProperty("lb.port", "8000"));

        InstanceRegistry registry = new InstanceRegistry();

        LoadBalancer lb = new LoadBalancer(registry, address, port);
        AutoScaler as = new AutoScaler(registry);

        lb.start();
        as.start();
        System.out.println(lb.getAddress().toString());
    }
}