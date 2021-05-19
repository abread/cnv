package cnv.autoscaler;

import java.io.IOException;

import cnv.autoscaler.loadbalancer.LoadBalancer;

public class Main {
    public static void main(String[] args) throws IOException {
        String address = System.getProperty("lb.address", "0.0.0.0");
        int port = Integer.parseInt(System.getProperty("lb.port", "8000"));

        InstanceRegistry registry = new InstanceRegistry();
        LoadBalancer lb = new LoadBalancer(registry, address, port);

        lb.start();
        System.out.println(lb.getAddress().toString());
    }
}