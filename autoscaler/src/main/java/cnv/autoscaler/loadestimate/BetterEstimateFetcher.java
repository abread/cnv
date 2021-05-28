package cnv.autoscaler.loadestimate;

import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import cnv.autoscaler.aws.AwsMetricDownloader;
import cnv.autoscaler.loadbalancer.Request;

public class BetterEstimateFetcher {
    private Logger logger = Logger.getLogger(BetterEstimateFetcher.class.getName());
    private ConcurrentLinkedQueue<Request> queue = new ConcurrentLinkedQueue<>();
    private Semaphore queueNotEmpty = new Semaphore(0);

    private Thread worker;

    public BetterEstimateFetcher() {
        worker = new Thread(new Worker(), "BetterEstimateFetcher worker");
        worker.start();
    }

    /**
     * never blocks
     * @param req
     */
    public void queueEstimationRequest(Request req) {
        queue.add(req);

        if (queueNotEmpty.availablePermits() <= 0) {
            // try to not issue too many permits (>1 make the worker spin needlessly)
            queueNotEmpty.release();
        }
    }

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