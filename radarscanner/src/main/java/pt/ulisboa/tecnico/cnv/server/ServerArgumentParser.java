package pt.ulisboa.tecnico.cnv.server;

import org.apache.commons.cli.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ServerArgumentParser {
    public enum ServerParameters {
        /**
         * Set debug mode.
         */
        DEBUG_SHORT("d"), DEBUG("debug"), OUTPUT_DIR_SHORT("o"), OUTPUT_DIR("output-directory"), ADDRESS("address"),
        PORT("port"), MAPS_DIR("maps");

        private final String text;

        ServerParameters(final String text) {
            this.text = text;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return this.text;
        }
    }

    protected final Options options = new Options();
    protected final Map<String, Object> argValues = new HashMap<>();

    protected final CommandLineParser parser = new DefaultParser();
    protected final HelpFormatter formatter = new HelpFormatter();
    protected CommandLine cmd = null;

    private void parseValues() {

        // Process output directory argument.
        String outDirPath = System.getProperty("java.io.tmpdir");
        if (this.cmd.hasOption(ServerParameters.OUTPUT_DIR.toString())) {
            outDirPath = this.cmd.getOptionValue(ServerParameters.OUTPUT_DIR.toString()).replace("'", "");
            File outDirHandle = new File(outDirPath).getAbsoluteFile();
            if (!outDirHandle.exists()) {
                boolean result = false;
                try {
                    result = outDirHandle.mkdir();
                } catch (SecurityException se) {
                    System.out.println("Error creating output directory:" + outDirPath);
                    se.printStackTrace();
                    System.exit(1);
                }
                if (result) {
                    System.out.println("Created output directory:\t" + outDirPath);
                } else if (outDirHandle.exists()) {
                    System.out.println("Output directory already existed:\t" + outDirPath);
                } else {
                    System.out.println("Create directory failed:\t " + outDirPath);
                    System.exit(1);
                }
                this.argValues.put(ServerParameters.OUTPUT_DIR.toString(), outDirPath);
            } else if (!outDirHandle.isDirectory()) {
                System.out.println("The given output directory path was a file but should be a directory:");
                System.out.println(outDirPath);
                System.out.println("Exiting.");
                System.exit(1);
            } else {
                this.argValues.put(ServerParameters.OUTPUT_DIR.toString(), outDirPath);
            }
        } else {
            final String tempDir = System.getProperty("java.io.tmpdir");
            this.argValues.put(ServerParameters.OUTPUT_DIR.toString(), tempDir);
        }

        if (this.cmd.hasOption(ServerParameters.ADDRESS.toString())) {
            final String address = this.cmd.getOptionValue(ServerParameters.ADDRESS.toString());
            this.argValues.put(ServerParameters.ADDRESS.toString(), address);
        } else {
            this.argValues.put(ServerParameters.ADDRESS.toString(), "127.0.0.1");
        }

        if (this.cmd.hasOption(ServerParameters.PORT.toString())) {
            final String port = this.cmd.getOptionValue(ServerParameters.PORT.toString());
            this.argValues.put(ServerParameters.PORT.toString(), new Integer(port));
        } else {
            this.argValues.put(ServerParameters.PORT.toString(), 8000);
        }

        if (this.cmd.hasOption(ServerParameters.MAPS_DIR.toString())) {
            String mapsDirectory = this.cmd.getOptionValue(ServerParameters.MAPS_DIR.toString());

            if (mapsDirectory.endsWith("/") || mapsDirectory.endsWith("\\")) {
                mapsDirectory = mapsDirectory.substring(0, mapsDirectory.length() - 1);
            }

            this.argValues.put(ServerParameters.MAPS_DIR.toString(), mapsDirectory);
        } else {
            this.argValues.put(ServerParameters.MAPS_DIR.toString(), "datasets");
        }

        this.argValues.put(ServerParameters.DEBUG.toString(), cmd.hasOption(ServerParameters.DEBUG.toString()));
        if (this.cmd.hasOption(ServerParameters.DEBUG.toString())) {
            for (Map.Entry<String, Object> param : this.argValues.entrySet()) {
                System.out.println(param.getKey() + "\t" + param.getValue().toString());
            }
            System.out.println("\n");
        }

    }

    private void setupCLIOptions() {

        final Option addressOption = new Option(ServerParameters.ADDRESS.toString(), true,
                "server listen address (default: 127.0.0.1).");
        addressOption.setRequired(false);
        this.options.addOption(addressOption);

        final Option portOption = new Option(ServerParameters.PORT.toString(), true,
                "server listen port (default: 8000).");
        portOption.setRequired(false);
        this.options.addOption(portOption);

        final Option mapsDirectoryOption = new Option(ServerParameters.MAPS_DIR.toString(), true,
                "directory with maps on the server (default: WORKING_DIR/datasets).");
        mapsDirectoryOption.setRequired(false);
        this.options.addOption(mapsDirectoryOption);

        final Option outputDirOption = new Option(ServerParameters.OUTPUT_DIR_SHORT.toString(),
                ServerParameters.OUTPUT_DIR.toString(), true,
                "output directory for generated images. By omission it is the system's temp directory.");
        outputDirOption.setRequired(false);
        this.options.addOption(outputDirOption);

        final Option debugOption = new Option(ServerParameters.DEBUG_SHORT.toString(),
                ServerParameters.DEBUG.toString(), false, "set debug mode.");
        debugOption.setRequired(false);
        this.options.addOption(debugOption);
    }

    public ServerArgumentParser(final String[] args) {

        // Prepare program-specific options provided in a subclass of this one.
        this.setupCLIOptions();

        System.out.println("> [ServerArgumentParser]: setupCLIOptions() DONE.");

        // Set values
        try {
            this.cmd = this.parser.parse(this.options, args);
        } catch (ParseException e) {
            System.out.println("> [ServerArgumentParser]: ParseException.");
            System.out.println(e.getMessage());
            this.formatter.printHelp("utility-name", this.options);
            System.exit(1);
        }

        // Parse program-specific arguments provided in a subclass of this one.
        this.parseValues();

    }

    public String getServerAddress() {
        return (String) this.argValues.get(ServerParameters.ADDRESS.toString());
    }

    public Integer getServerPort() {
        return (Integer) this.argValues.get(ServerParameters.PORT.toString());
    }

    public String getMapsDirectory() {
        return (String) this.argValues.get(ServerParameters.MAPS_DIR.toString());
    }

    public Boolean isDebugging() {
        return (Boolean) this.argValues.get(ServerParameters.DEBUG.toString());
    }

    public String getOutputDirectory() {
        return (String) this.argValues.get(ServerParameters.OUTPUT_DIR.toString());
    }

}
