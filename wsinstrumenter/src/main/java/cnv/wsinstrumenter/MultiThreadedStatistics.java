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
import java.io.File;
import java.util.Enumeration;

public class MultiThreadedStatistics {
    private static final String CLASS_METRIC_TRACKER = "pt/ulisboa/tecnico/cnv/server/MetricTracker";
    private static final String METHOD_INCR_METHOD_COUNT = "incrMethodCount";
    private static final String METHOD_INCR_INSTR_COUNT = "incrInstrCount";

    public static void printUsage() {
        System.err.println("Syntax: java MultiThreadedStatistics in_path [out_path]");
        System.err.println("        in_path:  directory from which the class files are read");
        System.err.println("        out_path: directory to which the class files are written");
        System.err.println("        Both in_path and out_path are required unless stat_type is static");
        System.err.println("        in which case only in_path is required");
        System.exit(-1);
    }

    public static void doDynamic(File in_dir, File out_dir) {
        String[] fileList = in_dir.list();
        assert fileList != null;
        for (String filename : fileList) {
            if (filename.endsWith(".class")) {
                String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                ClassInfo ci = new ClassInfo(in_filename);

                for (Enumeration<?> e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
                    routine.addBefore(CLASS_METRIC_TRACKER, METHOD_INCR_METHOD_COUNT, 0 /* could be anything, we don't care */);

                    for (Enumeration<?> b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore(CLASS_METRIC_TRACKER, METHOD_INCR_INSTR_COUNT, bb.size());
                    }
                }

                ci.write(out_filename);
            }
        }
    }

	public static void main(String[] argv) {
        if (argv.length != 2) {
            printUsage();
        }

        File in_dir;
        File out_dir;
        try {
            in_dir = new File(argv[0]);
            out_dir = new File(argv[1]);
        } catch (NullPointerException e) {
            System.err.println("in_dir or out_dir do not exist");
            printUsage();
            return; // printUsage will exit with -1, but this makes the compiler happy
        }

        if (!in_dir.isDirectory()) {
            System.err.println("in_dir is not a directory");
            printUsage();
        } else if (!out_dir.isDirectory()) {
            System.err.println("out_dir is not a directory");
            printUsage();
        } else {
            doDynamic(in_dir, out_dir);
        }
    }
}
