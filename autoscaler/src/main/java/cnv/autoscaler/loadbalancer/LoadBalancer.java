package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import cnv.autoscaler.InstanceRegistry;

/**
 * Implementation of the load balancer
 * Receives the registry of all instances and the address and port where to listen
 * Provides the /scan endpoint to the clients
 */
public class LoadBalancer {
    private final HttpServer server;

    public LoadBalancer(InstanceRegistry registry, String address, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(address, port), 0);
        server.createContext("/scan", new MinLoadLBStrategy(registry));

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
