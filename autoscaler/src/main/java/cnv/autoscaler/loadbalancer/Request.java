package cnv.autoscaler.loadbalancer;

import cnv.autoscaler.Instance;

public class Request {
    private Instance instance;
    private RequestParams params;

    public Request(Instance instance, RequestParams params) {
        this.instance = instance;
        this.params = params;
    }

    public Instance getInstance() {
        return this.instance;
    }

    public RequestParams params() {
        return this.params;
    }

    public void finished() {
        this.instance.requestEnd(this);
    }
}