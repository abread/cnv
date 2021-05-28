package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

import cnv.autoscaler.Instance;

public abstract class LBStrategy implements HttpHandler {
    private Logger logger = Logger.getLogger(LBStrategy.class.getName());
    private static final String X_REQUEST_ID_HEADER = "X-LB-Request-ID";
    private static final String X_METHOD_COUNT_HEADER = "X-Method-Count";
    private static final int MAX_ATTEMPTS = 5;

    public void handle(final HttpExchange t) throws IOException {
        // Get the query.
        final UUID requestId = UUID.randomUUID();
        final String queryString = t.getRequestURI().getQuery();
        logger.info(String.format("Request %s received from %s. Query: %s", requestId, t.getRemoteAddress(), queryString));

        Reply innerResponse = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            innerResponse = tryPerformingRequest(queryString, requestId);

            if (innerResponse != null) {
                break;
            }
        }
        if (innerResponse == null) {
            logger.severe(String.format("Request %s could not be answered after %d attempts", requestId, MAX_ATTEMPTS));

            // Send HTTP error 502 Bad Gateway
            t.sendResponseHeaders(502, 0);
            t.getResponseBody().close();
            return;
        }

        final Headers responseHeaders = t.getResponseHeaders();
        for (Map.Entry<String, String> header : innerResponse.headers.entrySet()) {
            responseHeaders.add(header.getKey(), header.getValue());
        }

        t.sendResponseHeaders(200, innerResponse.body.length);

        final OutputStream os = t.getResponseBody();
        os.write(innerResponse.body);
        os.close();

        logger.info("Request " + requestId + " answered");
    }

    private Reply tryPerformingRequest(String queryString, UUID requestId) {
        Optional<Long> methodCount = Optional.empty();

        Request request = this.startRequest(queryString, requestId);
        try {
            logger.info(String.format("Request %s running on instance %s", request.getId().toString(), request.getInstance().id()));

            final CloseableHttpClient client = HttpClients.createDefault();
            final HttpGet innerRequest = new HttpGet(request.getInstance().getBaseUri() + "/scan?" + queryString);
            innerRequest.addHeader(X_REQUEST_ID_HEADER, request.getId().toString());
            final CloseableHttpResponse innerResp = client.execute(innerRequest);

            if (innerResp.getCode() != 200) {
                throw new Exception("HTTP response not OK");
            }

            Reply reply = new Reply();
            for (Header header : innerResp.getHeaders()) {
                final String headerName = header.getName().toLowerCase();

                if (headerName.equals(X_METHOD_COUNT_HEADER)) {
                    try {
                        reply.methodCount = Optional.of(header.getValue()).map(Long::parseLong);
                        methodCount = reply.methodCount;
                    } catch (NullPointerException | NumberFormatException ignored) {}
                } else if (!headerName.equals("content-length")) {
                    reply.headers.put(header.getName(), header.getValue());
                }
            }

            int bodyLength = Long.valueOf(innerResp.getEntity().getContentLength()).intValue();
            reply.body = readAllBytes(innerResp.getEntity().getContent(), bodyLength);

            if (bodyLength != reply.body.length) {
                throw new Exception("body length does not match Content-Length");
            }

            innerResp.close();
            client.close();

            return reply;
        } catch (Exception e) {
            logger.warning(String.format("Failed attempt at answering request %s: %s", requestId, e.getMessage()));
            suspectInstance(request.getInstance());
            return null;
        } finally {
            request.finished(methodCount);
        }

    }

    private static byte[] readAllBytes(InputStream is, int length) throws IOException {
        byte[] buffer = new byte[length];

        int bufferIdx = 0;
        while (bufferIdx < length) {
            bufferIdx += is.read(buffer, bufferIdx, length - bufferIdx);
        }

        is.close();
        return buffer;
    }

    public abstract Request startRequest(String queryString, UUID requestId);

    protected abstract void suspectInstance(Instance instance);

    private static class Reply {
        public byte[] body;
        public Map<String, String> headers = new HashMap<>();
        public Optional<Long> methodCount = Optional.empty();

        public Reply() {}
    }
}