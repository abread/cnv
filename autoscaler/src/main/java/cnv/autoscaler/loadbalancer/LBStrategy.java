package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.logging.Logger;

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

public abstract class LBStrategy implements HttpHandler {
    private Logger logger = Logger.getLogger(LBStrategy.class.getName());

    public void handle(final HttpExchange t) throws IOException {
        // Get the query.
        final String queryString = t.getRequestURI().getQuery();

        logger.info("Request received. Query: " + queryString);

        RequestManager requestManager = this.startRequest(queryString);

        final CloseableHttpClient client = HttpClients.createDefault();
        final HttpGet innerRequest = new HttpGet(requestManager.getInstance().getBaseUri() + "/scan?" + queryString);
        final CloseableHttpResponse innerResp = client.execute(innerRequest);

        final Headers outerRespHeaders = t.getResponseHeaders();
        for (Header header : innerResp.getHeaders()) {
            // TODO: see if any more of these needs to be filtered
            if (!header.getName().toLowerCase().equals("content-length")) {
                outerRespHeaders.add(header.getName(), header.getValue());
            }
        }

        final HttpEntity innerRespBody = innerResp.getEntity();

        long contentLength = innerRespBody.getContentLength();
        t.sendResponseHeaders(innerResp.getCode(), contentLength);

        final OutputStream os = t.getResponseBody();
        final InputStream is = innerRespBody.getContent();
        final int BUFFER_SIZE = 2048;
        final byte[] buffer = new byte[BUFFER_SIZE];

        long bytesWritten = 0;
        while (bytesWritten < contentLength) {
            int nToRead = Math.max(Math.min(is.available(), BUFFER_SIZE), BUFFER_SIZE / 2);
            int nRead = is.read(buffer, 0, nToRead);
            if (nRead > 0) {
                os.write(buffer, 0, nRead);
            }
            bytesWritten += nRead;
        }

        os.close();
        is.close();
        innerResp.close();
        client.close();

        requestManager.finished();

        logger.info("> Sent response to " + t.getRemoteAddress().toString());
    }

    public abstract RequestManager startRequest(String queryString);

    public static class RequestManager {
        private Instance instance;
        private UUID id;

        public RequestManager(Instance instance, UUID id) {
            this.instance = instance;
            this.id = id;
        }

        public Instance getInstance() {
            return this.instance;
        }

        public void finished() {
            this.instance.requestEnd(this.id);
        }
    }
}