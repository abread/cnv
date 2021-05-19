package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public class LoadBalancer {

    private final HttpServer server;

    public LoadBalancer(InstanceRegistry registry, String address, int port) throws IOException {
        registry.add(new Instance("localhost:8000", "http://localhost:8000"));

        server = HttpServer.create(new InetSocketAddress(address, port), 0);
        server.createContext("/scan", new RoundRobinLBStrategy(registry));

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public InetSocketAddress getAddress() {
        return server.getAddress();
    }
}
