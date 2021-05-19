package cnv.autoscaler;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.concurrent.Executors;

import com.amazonaws.Request;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import pt.ulisboa.tecnico.cnv.server.ServerArgumentParser;

public class LoadBalancer {

    static Random rnd = new Random();
    static int roundRobinIdx = 0;
    static ArrayList<Instance> instances = new ArrayList<>();

    public static void main(final String[] args) throws Exception {
        ServerArgumentParser argParser;
        try {
            // Get user-provided flags.
            argParser = new ServerArgumentParser(args);

        } catch (Exception e) {
            System.out.println(e);
            return;
        }

        System.out.println("> Finished parsing Server args.");

        // final HttpServer server = HttpServer.create(new
        // InetSocketAddress("127.0.0.1", 8000), 0);

        final HttpServer server = HttpServer
                .create(new InetSocketAddress(argParser.getServerAddress(), argParser.getServerPort()), 0);

        server.createContext("/scan", new RandomLBStrategy());

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println(server.getAddress().toString());
    }

    private static selectServer() {
        // FIXME: what if no server is available? (should only happend if no server was launched)
        Instance chosen = instances.get(roundRobinIdx);
        roundRobinIdx = (roundRobinIdx + 1) % instances.size();
        String ip = chosen.getPrivateIpAddress();

        return "http://" + ip + ":8000";
    }

    public static void addInstance(Instance instance) {
        instances.add(instance);
    }

    public static void removeInstance(Instance instance) {
        int index = instances.indexOf(instance);
        if (instance >= 0) {
            if (index < roundRobinIdx) {
                roundRobinIdx--;
            }
            instances.remove(index)
        }
    }

    static interface LBStrategy extends HttpHandler {
        public void handle(final HttpExchange t) throws IOException {

            // Get the query.
            final String query = t.getRequestURI().getQuery();

            System.out.println("> Query:\t" + query);

            // Break it down into String[].
            final String[] params = query.split("&");

            // Store as if it was a direct call to SolverMain.
            final ArrayList<String> newArgs = new ArrayList<>();
            for (final String p : params) {
                final String[] splitParam = p.split("=");

                newArgs.add("-" + splitParam[0]);
                newArgs.add(splitParam[1]);
            }

            // Store from ArrayList into regular String[].
            final String[] args = new String[newArgs.size()];
            int i = 0;
            for (String arg : newArgs) {
                args[i] = arg;
                i++;
            }

            String serverBaseUri = selectServer();

            // TODO: send req to server
            URI u = t.getRequestURI();
            u = new URI(u.getScheme(), ip, u.getPath(), u.getFragment())

            final CloseableHttpClient client = HttpClients.createDefault();
            final HttpGet innerRequest = new HttpGet(serverBaseUri + "/scan" + t.getRequestURI().getQuery());
            final CloseableHttpResponse innerResp = client.execute(innerRequest);

            final Headers outerRespHeaders = t.getResponseHeaders();
            for (Header header : innerResp.getHeaders()) {
                // TODO: see if any more of these needs to be filtered
                if (!header.getName().toLowerCase().equals("content-length")) {
                    outerRespHeaders.add(header.getName(), header.getValue());
                }
            }

            final HttpEntity innerRespBody = innerResp.getEntity();

            t.sendResponseHeaders(innerResp.getCode(), innerRespBody.getContentLength());

            final OutputStream os = t.getResponseBody();
            final InputStream is = innerRespBody.getContent();
            final int BUFFER_SIZE = 2048;
            final byte[] buffer = new byte[BUFFER_SIZE];

            while (is.available() > 0) {
                int nToRead = Math.min(is.available(), BUFFER_SIZE);
                int nRead = is.read(buffer, 0, nToRead);
                if (nRead > 0) {
                    os.write(buffer, 0, nRead);
                }
            }

            os.close();
            is.close();
            innerResp.close();
            client.close();

            System.out.println("> Sent response to " + t.getRemoteAddress().toString());
        }

        public String selectServer();
    }
}
