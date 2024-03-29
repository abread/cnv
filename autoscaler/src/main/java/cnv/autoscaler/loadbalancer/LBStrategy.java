package cnv.autoscaler.loadbalancer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import cnv.autoscaler.Instance;
import cnv.autoscaler.InstanceRegistry;

/**
 * Abstract the strategy of load balancing, while handling the received requests and trying to send the requests
 * to an instance
 */
public abstract class LBStrategy implements HttpHandler {
    private Logger logger = Logger.getLogger(LBStrategy.class.getName());
    protected InstanceRegistry registry;

    private static final String X_REQUEST_ID_HEADER = "X-LB-Request-ID";
    private static final String X_METHOD_COUNT_HEADER = "X-Method-Count";
    private static final int MAX_ATTEMPTS = 5;

    protected LBStrategy(InstanceRegistry registry) {
        this.registry = registry;
    }

    /**
     * Receives a request, parses it and (tries to) redirects it to an instance
     * @param t
     * @throws IOException
     */
    public void handle(final HttpExchange t) throws IOException {
        // Get the query.
        final UUID requestId = UUID.randomUUID();
        final String queryString = t.getRequestURI().getQuery();
        logger.info(String.format("Request %s received from %s. Query: %s", requestId, t.getRemoteAddress(), queryString));

        Reply innerResponse = null;
        HashSet<Instance> suspectedBadInstances = new HashSet<>();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            innerResponse = tryPerformingRequest(queryString, requestId, suspectedBadInstances);

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

        t.sendResponseHeaders(innerResponse.statusCode, innerResponse.body.length);

        final OutputStream os = t.getResponseBody();
        os.write(innerResponse.body);
        os.close();

        logger.info("Request " + requestId + " answered");
    }

    /**
     * Tries to send this request to an healthy instance, and parses its results on success, or marks that instance
     * as a suspected unhealthy
     * When the response is an success, gets the method count from the headers and stores it.
     * @param queryString
     * @param requestId
     * @param suspectedBadInstances
     * @return
     */
    private Reply tryPerformingRequest(String queryString, UUID requestId, HashSet<Instance> suspectedBadInstances) {
        final int WAIT_TIME = 10 * 1000;// ms
        Optional<Long> methodCount = Optional.empty();

        if (registry.size() == suspectedBadInstances.size() && suspectedBadInstances.containsAll(registry.readyInstances())) {
            // try to give the system some time to have healthy instances again
            try {
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException ignored) {}

            // we suspect everyone, so just start fresh to be able to make progress
            // note: this does not affect health checking
            suspectedBadInstances.clear();
        }

        Request request = this.startRequest(queryString, requestId, suspectedBadInstances);
        try {
            logger.info(String.format("Request %s running on instance %s", request.getId().toString(), request.getInstance().id()));

            final CloseableHttpClient client = HttpClients.createDefault();
            final RequestConfig innerRequestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
                .setConnectionKeepAlive(TimeValue.NEG_ONE_SECOND)
                .setConnectionRequestTimeout(Timeout.DISABLED)
                .setResponseTimeout(Timeout.DISABLED)
                .build();
            final HttpGet innerRequest = new HttpGet(request.getInstance().getBaseUri() + "/scan?" + queryString);
            innerRequest.addHeader(X_REQUEST_ID_HEADER, request.getId().toString());
            innerRequest.setConfig(innerRequestConfig);
            final CloseableHttpResponse innerResp = client.execute(innerRequest);

            if (innerResp.getCode() >= 500) {
                throw new Exception("Error in server that handled the request (statusCode >= 500)");
            }

            Reply reply = new Reply();
            reply.statusCode = innerResp.getCode();

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
            e.printStackTrace();
            suspectedBadInstances.add(request.getInstance());
            registry.suspectInstanceBad(request.getInstance());
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

    /**
     * Mark the request as started in some instance of the registry.
     * Should ignore instances present in the suspectedBadInstances set
     *
     * @param queryString request query string
     * @param requestId requestId
     * @param suspectedBadInstances instances that are suspected to be unhealthy
     * @return request representation
     */
    public abstract Request startRequest(String queryString, UUID requestId, HashSet<Instance> suspectedBadInstances);

    private static class Reply {
        public int statusCode;
        public byte[] body;
        public Map<String, String> headers = new HashMap<>();
        public Optional<Long> methodCount = Optional.empty();

        public Reply() {}
    }
}
