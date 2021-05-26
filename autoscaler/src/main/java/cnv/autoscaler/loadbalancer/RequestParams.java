package cnv.autoscaler.loadbalancer;

public class RequestParams {
    public long x0 = 0, x1 = 0, y0 = 0, y1 = 0;
    public String algo, imagePath;

    public RequestParams(String queryString) {
        final String[] params = queryString.split("&");
        for (String param : params) {
            try {
                if (param.startsWith("s=")) {
                    algo = param.substring(2);
                } else if (param.startsWith("x0=")) {
                    x0 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("x1=")) {
                    x1 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("y0=")) {
                    y0 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("y1=")) {
                    y1 = Long.parseLong(param.substring(3));
                } else if (param.startsWith("i=")) {
                    imagePath = param.substring(2);
                }
            } catch (NumberFormatException ignored) {
                // even if it fails, good defaults are provided
            }
        }
    }

    public long viewportArea() {
        long viewportArea = (x1 - x0) * (y1 - y0);
        viewportArea = Math.max(viewportArea, 0);
        return viewportArea;
    }
}
