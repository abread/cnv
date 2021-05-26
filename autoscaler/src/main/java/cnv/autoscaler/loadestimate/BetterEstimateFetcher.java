package cnv.autoscaler.loadestimate;

import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import cnv.autoscaler.aws.AwsMetricDownloader;
import cnv.autoscaler.loadbalancer.Request;

public class BetterEstimateFetcher {
    private ConcurrentLinkedQueue<Request> queue = new ConcurrentLinkedQueue<>();
    private Semaphore queueNotEmpty = new Semaphore(1);

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
        queueNotEmpty.release();
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            Request req;
            while (true) {
                while ((req = queue.poll()) != null) {
                    OptionalDouble methodCount = AwsMetricDownloader.getEstimatedMethodCountForRequest(req.params());

                    if (methodCount.isPresent()) {
                        req.getInstance().updateRequestEstimate(req, Math.round(methodCount.getAsDouble()));
                    }
                }


                try {
                    queueNotEmpty.acquire();
                } catch (InterruptedException ignored) {}
            }
        }
    }
}