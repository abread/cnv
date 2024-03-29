//
// MultiThreadedStatistics.java
//
// This program measures and instruments to obtain different statistics
// about Java programs.
//
// Copyright (c) 1998 by Han B. Lee (hanlee@cs.colorado.edu).
// ALL RIGHTS RESERVED.
//
// Permission to use, copy, modify, and distribute this software and its
// documentation for non-commercial purposes is hereby granted provided
// that this copyright notice appears in all copies.
//
// This software is provided "as is".  The licensor makes no warrenties, either
// expressed or implied, about its correctness or performance.  The licensor
// shall not be liable for any damages suffered as a result of using
// and modifying this software.
package cnv.wsinstrumenter;

import BIT.highBIT.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

public class MultiThreadedStatistics {
    private static final String CLASS_METRIC_TRACKER = "pt/ulisboa/tecnico/cnv/server/MetricTracker";

    public static void printUsage() {
        System.err.println("Syntax: java MultiThreadedStatistics in_path [out_path]");
        System.err.println("        in_path:  directory from which the class files are read");
        System.err.println("        out_path: directory to which the class files are written");
        System.err.println("        Both in_path and out_path are required unless stat_type is static");
        System.err.println("        in which case only in_path is required");
        System.exit(-1);
    }

    /**
     * Calls the instrumenter recursively on all .class files in a directory and its subdirectories.
     * @param in_dir the input directory of classes to instrument
     * @param out_dir the output directory
     * @throws IOException if the directories can't be created
     */
    public static void instrumentDirRecurse(Path in_dir, Path out_dir) throws IOException {
        if (!Files.isDirectory(in_dir)) {
            System.err.printf("%s is not a directory%n", in_dir);
            System.exit(-1);
        } else if (!Files.isDirectory(out_dir)) {
            if (Files.exists(out_dir)) {
                System.err.printf("%s is not a directory%n", out_dir);
                System.exit(-1);
            } else {
                Files.createDirectories(out_dir);
            }
        }

        String[] fileList = in_dir.toFile().list();
        assert fileList != null;
        for (String filename : fileList) {
            Path in_path = in_dir.resolve(filename);
            Path out_path = out_dir.resolve(filename);

            if (Files.isDirectory(in_path)) {
                instrumentDirRecurse(in_path, out_path);
            } else if (in_path.toString().endsWith(".class")) {
                instrumentFile(in_path, out_path);
            } else {
                System.err.printf("File not processed: %s%n", in_path);
            }
        }
    }

    /**
     * Runs the MethodCallTracker.instrument on the given .class file.
     * @param in_path the file to instrument
     * @param out_path the corresponding output file
     */
    public static void instrumentFile(Path in_path, Path out_path) {
        ClassInfo ci = new ClassInfo(in_path.toString());

        MethodCallTracker.instrument(ci);

        ci.write(out_path.toString());
    }

    public static void main(String[] argv) throws IOException {
        if (argv.length != 2) {
            printUsage();
        }

        Path in_dir = Paths.get(argv[0]);
        Path out_dir = Paths.get(argv[1]);
        instrumentDirRecurse(in_dir, out_dir);
    }

    /**
     * This class contains the instrumenter logic. It adds a method call to
     * CLASS_METRIC_TRACKER.METHOD_INCR_METHOD_COUNT before each bytecode method.
     */
    private static class MethodCallTracker {
        private static final String METHOD_INCR_METHOD_COUNT = "incrMethodCount";

        public static void instrument(ClassInfo ci) {
            for (Enumeration<?> e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                Routine routine = (Routine) e.nextElement();
                routine.addBefore(CLASS_METRIC_TRACKER, METHOD_INCR_METHOD_COUNT, 0 /* could be anything, we don't care */);
            }
        }
    }

}
