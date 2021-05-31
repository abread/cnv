package cnv.autoscaler.loadbalancer;

import java.util.Optional;
import java.util.UUID;

import cnv.autoscaler.Instance;

/**
 * Class representing a receiving Request
 * It has the parameters sent with the request, and the instance where the request was forwarded to
 */
public class Request {
    private UUID id;
    private Instance instance;
    private RequestParams params;

    public Request(UUID id, Instance instance, RequestParams params) {
        this.id = id;
        this.instance = instance;
        this.params = params;
    }

    public Instance getInstance() {
        return this.instance;
    }

    public RequestParams params() {
        return this.params;
    }

    public UUID getId() {
        return this.id;
    }

    public void finished(Optional<Long> methodCount) {
        this.instance.requestEnd(this, methodCount);
    }
}
