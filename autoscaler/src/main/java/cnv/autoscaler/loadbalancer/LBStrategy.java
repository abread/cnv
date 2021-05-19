package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

public abstract class LBStrategy implements HttpHandler {
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

        long timeEstimateMilliseconds = 1000; // TODO: actually estimate load

        Instance instance = this.selectInstance();
        Optional<UUID> requestUuid = instance.requestStart(timeEstimateMilliseconds);

        while (requestUuid.isEmpty()) {
            instance = this.selectInstance();
            requestUuid = instance.requestStart(timeEstimateMilliseconds);
        }

        final CloseableHttpClient client = HttpClients.createDefault();
        final HttpGet innerRequest = new HttpGet(instance.getBaseUri() + "/scan" + t.getRequestURI().getQuery());
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

        instance.requestEnd(requestUuid.get());

        System.out.println("> Sent response to " + t.getRemoteAddress().toString());
    }

    public abstract Instance selectInstance();
}