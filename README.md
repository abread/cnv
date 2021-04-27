To compile `./gradlew build`.

Tip: Use the `--parallel` flag to speed up the build

Note that despite Gradle requiring Java 8 or above, Java 7-compatible bytecode will be generated.

The output will be in `radarscanner/build/distributions/radarscanner-1.0-SNAPSHOT.zip`

The build process includes the instrumentation of server code. A precompiled (and pre-instrumented) webserver is available at `radarscanner/build/distributions/radarscanner-1.0-SNAPSHOT.zip`

## Subproject overview
- radarscanner - The radarscanner web service
- wsinstrumenter - The radarscanner web service instrumenting code (BIT bytecode manipulation)
- autoscaler - WIP

## Other inclusions
- `bench.sh` - a simple benchmarking script that spins up a webserver and sends 100 requests for experiment data collection
- `benchdata` - obtained experimental data (all experiments ran on LAB11 PCs without concurrent workloads)
