package cnv.autoscaler.loadestimate;

import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import cnv.autoscaler.aws.AwsMetricDownloader;
import cnv.autoscaler.loadbalancer.Request;

/**
 * Implements a slow-ish estimate fetcher which relies on querying DynamoDB. This runs on a separate thread
 * to allow non-blocking operation.
 */
public class BetterEstimateFetcher {
    private final Logger logger = Logger.getLogger(BetterEstimateFetcher.class.getName());
    private final ConcurrentLinkedQueue<Request> queue = new ConcurrentLinkedQueue<>();
    private final Semaphore queueNotEmpty = new Semaphore(0);

    public BetterEstimateFetcher() {
        Thread worker = new Thread(new Worker(), "BetterEstimateFetcher worker");
        worker.start();
    }

    /**
     * Adds a request for getting the load estimate for a given Request. This method never blocks.
     * @param req the correspondign request
     */
    public void queueEstimationRequest(Request req) {
        queue.add(req);

        if (queueNotEmpty.availablePermits() <= 0) {
            // try to not issue too many permits (>1 make the worker spin needlessly)
            queueNotEmpty.release();
        }
    }

    /**
     * Implements a worker responsible for fetching the estimates from DynamoDB. The worker polls for current
     * estimation requests and updates their load estimate when a response from DynamoDB is received.
     */
    private class Worker implements Runnable {
        @Override
        public void run() {
            Request req;
            while (true) {
                try {
                    queueNotEmpty.acquire();
                } catch (InterruptedException ignored) {}

                while ((req = queue.poll()) != null) {
                    try {
                        logger.info("Fetching a better estimate from DynamoDB for request " + req.getId().toString());
                        OptionalDouble methodCount = AwsMetricDownloader.getEstimatedMethodCountForRequest(req.params());

                        if (methodCount.isPresent()) {
                            req.getInstance().updateRequestEstimate(req, Math.round(methodCount.getAsDouble()));
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
