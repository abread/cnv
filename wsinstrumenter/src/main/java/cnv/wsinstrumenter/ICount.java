package cnv.wsinstrumenter;

import BIT.highBIT.BasicBlock;
import BIT.highBIT.ClassInfo;
import BIT.highBIT.Routine;

import java.io.File;
import java.util.Enumeration;

public class ICount {
    private static int i_count = 0, b_count = 0, m_count = 0, r_depth = 0, max_depth = 0;

    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */
    public static void main(String[] argv) {
        File file_in = new File(argv[0]);
        String[] inFileNames = file_in.list();

        assert inFileNames != null;
        for (String inFileName : inFileNames) {
            if (inFileName.endsWith(".class")) {
                // create class info object
                ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + inFileName);

                // loop through all the routines
                // see java.util.Enumeration for more information on Enumeration class
                Enumeration<?> e = ci.getRoutines().elements();
                while (e.hasMoreElements()) {
                    Routine routine = (Routine) e.nextElement();
                    routine.addBefore("ICount", "mcount", 1);
                    routine.addAfter("ICount", "mcount", -1);

                    Enumeration<?> b = routine.getBasicBlocks().elements();
                    while (b.hasMoreElements()) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("ICount", "count", bb.size());
                    }
                }
                ci.addAfter("ICount", "printICount", ci.getClassName());
                ci.write(argv[1] + System.getProperty("file.separator") + inFileName);
            }
        }
    }

    public static synchronized void printICount(String foo) {
        System.out.println(i_count + " instructions in " + b_count +
                " basic blocks were executed in " + m_count +
                " methods with max recursion depth " + max_depth);
    }

    public static synchronized void count(int incr) {
        i_count += incr;
        b_count++;
    }

    public static synchronized void mcount(int incr) {
        if (incr == 1) {
            m_count++;
        }
        r_depth += incr;
        if (r_depth > max_depth) {
            max_depth = r_depth;
        }
    }
}
