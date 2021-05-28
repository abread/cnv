package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
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
import org.apache.hc.core5.http.ProtocolException;

public abstract class LBStrategy implements HttpHandler {
    private Logger logger = Logger.getLogger(LBStrategy.class.getName());
    private static final String X_REQUEST_ID_HEADER = "X-LB-Request-ID";
    private static final String X_METHOD_COUNT_HEADER = "X-Method-Count";

    public void handle(final HttpExchange t) throws IOException {
        // Get the query.
        final String queryString = t.getRequestURI().getQuery();

        logger.info("Request received. Query: " + queryString);

        Optional<Long> methodCount = Optional.empty();
        Request request = this.startRequest(queryString);
        try {
            logger.info(String.format("Request %s running on instance %s", request.getId().toString(), request.getInstance().id()));

            final CloseableHttpClient client = HttpClients.createDefault();
            final HttpGet innerRequest = new HttpGet(request.getInstance().getBaseUri() + "/scan?" + queryString);
            innerRequest.addHeader(X_REQUEST_ID_HEADER, request.getId().toString());
            final CloseableHttpResponse innerResp = client.execute(innerRequest);

            final Headers outerRespHeaders = t.getResponseHeaders();
            for (Header header : innerResp.getHeaders()) {
                final String headerName = header.getName().toLowerCase();

                if (!headerName.equals("content-length") || !headerName.equals(X_METHOD_COUNT_HEADER)) {
                    outerRespHeaders.add(header.getName(), header.getValue());
                }
            }

            try {
                methodCount = Optional.of(innerResp.getHeader(X_METHOD_COUNT_HEADER).getValue()).map(Long::parseLong);
            } catch (NullPointerException | ProtocolException ignored) {}

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

            logger.info("> Sent response to " + t.getRemoteAddress().toString());
        } finally {
            request.finished(methodCount);
        }
    }

    public abstract Request startRequest(String queryString);
}