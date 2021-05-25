package pt.ulisboa.tecnico.cnv.server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.server.MetricTracker.Metrics;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;

public class WebServer {

    private static ServerArgumentParser sap = null;
    private static SolverFactory solverFactory;
    private static MetricUploader metricUploader;

    private static final int MAX_REQUESTS_PER_CPU = 5;
    private static final int N_THREADS = MAX_REQUESTS_PER_CPU * Runtime.getRuntime().availableProcessors();

    static {
        // just create a dummy metric hold to prevent instrumented code in the main thread from panicking
        MetricTracker.requestStart(new String[0]);

        solverFactory = SolverFactory.getInstance();
    }

    public static void main(final String[] args) throws Exception {
        try {
            // Get user-provided flags.
            WebServer.sap = new ServerArgumentParser(args);

            metricUploader = new MetricUploader();
        } catch (Exception e) {
            System.err.println("Could not initialize server: " + e);
            e.printStackTrace();
            return;
        }

        System.out.println("> Finished parsing Server args.");

        // final HttpServer server = HttpServer.create(new
        // InetSocketAddress("127.0.0.1", 8000), 0);

        final HttpServer server = HttpServer
                .create(new InetSocketAddress(WebServer.sap.getServerAddress(), WebServer.sap.getServerPort()), 0);

        server.createContext("/scan", new MyHandler());
        server.createContext("/test", new TestHandler());

        server.setExecutor(Executors.newFixedThreadPool(N_THREADS));
        server.start();

        System.out.println(server.getAddress().toString());
    }

    static class TestHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);
            t.getResponseBody().close();
        }
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {

            // Get the query.
            final String query = t.getRequestURI().getQuery();

            System.out.println("> Query:\t" + query);

            // Break it down into String[].
            final String[] params = query.split("&");

            // Store as if it was a direct call to SolverMain.
            final ArrayList<String> newArgs = new ArrayList<>();
            for (final String p : params) {
                final String[] splitParam = p.split("=");

                if (splitParam[0].equals("i")) {
                    splitParam[1] = WebServer.sap.getMapsDirectory() + "/" + splitParam[1];
                }

                newArgs.add("-" + splitParam[0]);
                newArgs.add(splitParam[1]);
            }

            if (sap.isDebugging()) {
                newArgs.add("-d");
            }

            // Store from ArrayList into regular String[].
            final String[] args = new String[newArgs.size()];
            int i = 0;
            for (String arg : newArgs) {
                args[i] = arg;
                i++;
            }

            // Create solver instance from factory.
            MetricTracker.requestStart(args);
            final Solver s = solverFactory.makeSolver(args);

            if (s == null) {
                System.out.println("> Problem creating Solver.");
                t.sendResponseHeaders(400, 0);
                t.getResponseBody().close();
                return;
            }

            // Write figure file to disk.
            File responseFile = null;
            try {

                final BufferedImage outputImg = s.solveImage();

                final String outPath = WebServer.sap.getOutputDirectory();

                final String imageName = s.toString();

                final Path imagePathPNG = Paths.get(outPath, imageName);
                ImageIO.write(outputImg, "png", imagePathPNG.toFile());

                responseFile = imagePathPNG.toFile();

            } catch (final Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
                return;
            }

            Metrics results = MetricTracker.requestEnd();
            //results.print();
            try {
                metricUploader.upload(results);
            } catch (Exception e) {
                System.err.println("Could not upload instrumentation data: " + e.getMessage());
            }

            // Send response to browser.
            final Headers hdrs = t.getResponseHeaders();

            hdrs.add("Content-Type", "image/png");

            hdrs.add("Access-Control-Allow-Origin", "*");
            hdrs.add("Access-Control-Allow-Credentials", "true");
            hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
            hdrs.add("Access-Control-Allow-Headers",
                    "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

            t.sendResponseHeaders(200, responseFile.length());

            final OutputStream os = t.getResponseBody();
            Files.copy(responseFile.toPath(), os);

            os.close();

            System.out.println("> Sent response to " + t.getRemoteAddress().toString());
        }
    }

}
