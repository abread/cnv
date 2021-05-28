package cnv.autoscaler.loadbalancer;

import java.util.Optional;
import java.util.UUID;

import cnv.autoscaler.Instance;

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