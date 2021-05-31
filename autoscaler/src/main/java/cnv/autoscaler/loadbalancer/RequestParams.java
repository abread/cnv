package cnv.autoscaler.loadbalancer;

import java.util.Objects;

/**
 * Represents the parameters sent with each query request
 * Parses each parameter, containing the algorithm, the path of the image and each position and viewpor
 */
public class RequestParams {
    public final static int POSITION_THRESHOLD = 16; // TODO: tune (future work)
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

    /**
     * True if this request parameters are similar to the others, false otherwise
     * To be similar each coordinate must differ at most by the POSITION_THRESHOLD and the image and algorithm
     * must be the same
     * @param other the RequestParams to compare this instance to
     * @return whether the two RequestParams are similar or not
     */
    public boolean similarTo(RequestParams other) {
        return this.algo.equals(other.algo)
            && this.imagePath.equals(other.imagePath)
            && Math.abs(this.x0 - other.x0) <= POSITION_THRESHOLD
            && Math.abs(this.x1 - other.x1) <= POSITION_THRESHOLD
            && Math.abs(this.y0 - other.y0) <= POSITION_THRESHOLD
            && Math.abs(this.y1 - other.y1) <= POSITION_THRESHOLD;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x0, x1, y0, y1, algo, imagePath);
    }
}
