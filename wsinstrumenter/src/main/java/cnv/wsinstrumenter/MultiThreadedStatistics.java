//
// StatisticsTool.java
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

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

class Metrics
{
    int dyn_method_count = 0;
    int dyn_bb_count = 0;
    int dyn_instr_count = 0;

    int newcount = 0;
    int newarraycount = 0;
    int anewarraycount = 0;
    int multianewarraycount = 0;

    int loadcount = 0;
    int storecount = 0;
    int fieldloadcount = 0;
    int fieldstorecount = 0;

    StatisticsBranch[] branch_info;
    int branch_number;
    int branch_pc;
    String branch_class_name;
}

public class MultiThreadedStatistics
{
    private static ThreadLocal<Metrics> localMetrics = new ThreadLocal<>();

	public static void printUsage()
		{
			System.out.println("Syntax: java StatisticsTool -stat_type in_path [out_path]");
			System.out.println("        where stat_type can be:");
			System.out.println("        dynamic:    dynamic properties");
			System.out.println("        alloc:      memory allocation instructions");
			System.out.println("        load_store: loads and stores (both field and regular)");
			System.out.println("        branch:     gathers branch outcome statistics");
			System.out.println();
			System.out.println("        in_path:  directory from which the class files are read");
			System.out.println("        out_path: directory to which the class files are written");
			System.out.println("        Both in_path and out_path are required unless stat_type is static");
			System.out.println("        in which case only in_path is required");
			System.exit(-1);
		}

	public static void doDynamic(File in_dir, File out_dir)
		{
			String filelist[] = in_dir.list();
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);
					ci.addBefore("StatisticsTool", "resetMetrics", null);
					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						routine.addBefore("StatisticsTool", "dynMethodCount", 1);
                    
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							bb.addBefore("StatisticsTool", "dynInstrCount", bb.size());
						}
					}
					ci.addAfter("StatisticsTool", "printDynamic", "null");
					ci.write(out_filename);
				}
			}
		}

    public static synchronized void printDynamic(String foo)
		{
		    Metrics local = localMetrics.get();
			System.out.println("Dynamic information summary:");
			System.out.println("Number of methods:      " + local.dyn_method_count);
			System.out.println("Number of basic blocks: " + local.dyn_bb_count);
			System.out.println("Number of instructions: " + local.dyn_instr_count);
		
			if (local.dyn_method_count == 0) {
				return;
			}
		
			float instr_per_bb = (float) local.dyn_instr_count / (float) local.dyn_bb_count;
			float instr_per_method = (float) local.dyn_instr_count / (float) local.dyn_method_count;
			float bb_per_method = (float) local.dyn_bb_count / (float) local.dyn_method_count;
		
			System.out.println("Average number of instructions per basic block: " + instr_per_bb);
			System.out.println("Average number of instructions per method:      " + instr_per_method);
			System.out.println("Average number of basic blocks per method:      " + bb_per_method);
		}

	public static synchronized void resetMetrics(Object obj)
        {
            // FIXME: maybe Metrics.reset() or something instead of creating a new Metrics() object?
            localMetrics.set(new Metrics());
        }

    public static synchronized void dynInstrCount(int incr) 
		{
            Metrics local = localMetrics.get();
			local.dyn_instr_count += incr;
			local.dyn_bb_count += 1;
		}

    public static synchronized void dynMethodCount(int incr) 
		{
			localMetrics.get().dyn_method_count += 1;
		}
//
//	public static void doAlloc(File in_dir, File out_dir)
//		{
//			String filelist[] = in_dir.list();
//
//			for (int i = 0; i < filelist.length; i++) {
//				String filename = filelist[i];
//				if (filename.endsWith(".class")) {
//					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					ClassInfo ci = new ClassInfo(in_filename);
//
//					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
//						Routine routine = (Routine) e.nextElement();
//						InstructionArray instructions = routine.getInstructionArray();
//
//						for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); ) {
//							Instruction instr = (Instruction) instrs.nextElement();
//							int opcode=instr.getOpcode();
//							if ((opcode==InstructionTable.NEW) ||
//								(opcode==InstructionTable.newarray) ||
//								(opcode==InstructionTable.anewarray) ||
//								(opcode==InstructionTable.multianewarray)) {
//								instr.addBefore("StatisticsTool", "allocCount", new Integer(opcode));
//							}
//						}
//					}
//					ci.addAfter("StatisticsTool", "printAlloc", "null");
//					ci.write(out_filename);
//				}
//			}
//		}
//
//	public static synchronized void printAlloc(String s)
//		{
//			System.out.println("Allocations summary:");
//			System.out.println("new:            " + newcount);
//			System.out.println("newarray:       " + newarraycount);
//			System.out.println("anewarray:      " + anewarraycount);
//			System.out.println("multianewarray: " + multianewarraycount);
//		}
//
//	public static synchronized void allocCount(int type)
//		{
//			switch(type) {
//			case InstructionTable.NEW:
//				newcount++;
//				break;
//			case InstructionTable.newarray:
//				newarraycount++;
//				break;
//			case InstructionTable.anewarray:
//				anewarraycount++;
//				break;
//			case InstructionTable.multianewarray:
//				multianewarraycount++;
//				break;
//			}
//		}
//
//	public static void doLoadStore(File in_dir, File out_dir)
//		{
//			String filelist[] = in_dir.list();
//
//			for (int i = 0; i < filelist.length; i++) {
//				String filename = filelist[i];
//				if (filename.endsWith(".class")) {
//					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					ClassInfo ci = new ClassInfo(in_filename);
//
//					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
//						Routine routine = (Routine) e.nextElement();
//
//						for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); ) {
//							Instruction instr = (Instruction) instrs.nextElement();
//							int opcode=instr.getOpcode();
//							if (opcode == InstructionTable.getfield)
//								instr.addBefore("StatisticsTool", "LSFieldCount", new Integer(0));
//							else if (opcode == InstructionTable.putfield)
//								instr.addBefore("StatisticsTool", "LSFieldCount", new Integer(1));
//							else {
//								short instr_type = InstructionTable.InstructionTypeTable[opcode];
//								if (instr_type == InstructionTable.LOAD_INSTRUCTION) {
//									instr.addBefore("StatisticsTool", "LSCount", new Integer(0));
//								}
//								else if (instr_type == InstructionTable.STORE_INSTRUCTION) {
//									instr.addBefore("StatisticsTool", "LSCount", new Integer(1));
//								}
//							}
//						}
//					}
//					ci.addAfter("StatisticsTool", "printLoadStore", "null");
//					ci.write(out_filename);
//				}
//			}
//		}
//
//	public static synchronized void printLoadStore(String s)
//		{
//			System.out.println("Load Store Summary:");
//			System.out.println("Field load:    " + fieldloadcount);
//			System.out.println("Field store:   " + fieldstorecount);
//			System.out.println("Regular load:  " + loadcount);
//			System.out.println("Regular store: " + storecount);
//		}
//
//	public static synchronized void LSFieldCount(int type)
//		{
//			if (type == 0)
//				fieldloadcount++;
//			else
//				fieldstorecount++;
//		}
//
//	public static synchronized void LSCount(int type)
//		{
//			if (type == 0)
//				loadcount++;
//			else
//				storecount++;
//		}
//
//	public static void doBranch(File in_dir, File out_dir)
//		{
//			String filelist[] = in_dir.list();
//			int k = 0;
//			int total = 0;
//
//			for (int i = 0; i < filelist.length; i++) {
//				String filename = filelist[i];
//				if (filename.endsWith(".class")) {
//					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					ClassInfo ci = new ClassInfo(in_filename);
//
//					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
//						Routine routine = (Routine) e.nextElement();
//						InstructionArray instructions = routine.getInstructionArray();
//						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
//							BasicBlock bb = (BasicBlock) b.nextElement();
//							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
//							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
//							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
//								total++;
//							}
//						}
//					}
//				}
//			}
//
//			for (int i = 0; i < filelist.length; i++) {
//				String filename = filelist[i];
//				if (filename.endsWith(".class")) {
//					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
//					ClassInfo ci = new ClassInfo(in_filename);
//
//					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
//						Routine routine = (Routine) e.nextElement();
//						routine.addBefore("StatisticsTool", "setBranchMethodName", routine.getMethodName());
//						InstructionArray instructions = routine.getInstructionArray();
//						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
//							BasicBlock bb = (BasicBlock) b.nextElement();
//							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
//							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
//							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
//								instr.addBefore("StatisticsTool", "setBranchPC", new Integer(instr.getOffset()));
//								instr.addBefore("StatisticsTool", "updateBranchNumber", new Integer(k));
//								instr.addBefore("StatisticsTool", "updateBranchOutcome", "BranchOutcome");
//								k++;
//							}
//						}
//					}
//					ci.addBefore("StatisticsTool", "setBranchClassName", ci.getClassName());
//					ci.addBefore("StatisticsTool", "branchInit", new Integer(total));
//					ci.addAfter("StatisticsTool", "printBranch", "null");
//					ci.write(out_filename);
//				}
//			}
//		}
//
//	public static synchronized void setBranchClassName(String name)
//		{
//			branch_class_name = name;
//		}
//
//	public static synchronized void setBranchMethodName(String name)
//		{
//			branch_method_name = name;
//		}
//
//	public static synchronized void setBranchPC(int pc)
//		{
//			branch_pc = pc;
//		}
//
//	public static synchronized void branchInit(int n)
//		{
//			if (branch_info == null) {
//				branch_info = new StatisticsBranch[n];
//			}
//		}
//
//	public static synchronized void updateBranchNumber(int n)
//		{
//			branch_number = n;
//
//			if (branch_info[branch_number] == null) {
//				branch_info[branch_number] = new StatisticsBranch(branch_class_name, branch_method_name, branch_pc);
//			}
//		}
//
//	public static synchronized void updateBranchOutcome(int br_outcome)
//		{
//			if (br_outcome == 0) {
//				branch_info[branch_number].incrNotTaken();
//			}
//			else {
//				branch_info[branch_number].incrTaken();
//			}
//		}
//
//	public static synchronized void printBranch(String foo)
//		{
//			System.out.println("Branch summary:");
//			System.out.println("CLASS NAME" + '\t' + "METHOD" + '\t' + "PC" + '\t' + "TAKEN" + '\t' + "NOT_TAKEN");
//
//			for (int i = 0; i < branch_info.length; i++) {
//				if (branch_info[i] != null) {
//					branch_info[i].print();
//				}
//			}
//		}
	
			
	public static void main(String argv[]) 
		{
			if (argv.length < 2 || !argv[0].startsWith("-")) {
				printUsage();
			}

			if (argv[0].equals("-dynamic")) {
				if (argv.length != 3) {
					printUsage();
				}
				
				try {
					File in_dir = new File(argv[1]);
					File out_dir = new File(argv[2]);

					if (in_dir.isDirectory() && out_dir.isDirectory()) {
						doDynamic(in_dir, out_dir);
					}
					else {
						printUsage();
					}
				}
				catch (NullPointerException e) {
					printUsage();
				}
			}
//
//			else if (argv[0].equals("-alloc")) {
//				if (argv.length != 3) {
//					printUsage();
//				}
//
//				try {
//					File in_dir = new File(argv[1]);
//					File out_dir = new File(argv[2]);
//
//					if (in_dir.isDirectory() && out_dir.isDirectory()) {
//						doAlloc(in_dir, out_dir);
//					}
//					else {
//						printUsage();
//					}
//				}
//				catch (NullPointerException e) {
//					printUsage();
//				}
//			}
//
//			else if (argv[0].equals("-load_store")) {
//				if (argv.length != 3) {
//					printUsage();
//				}
//
//				try {
//					File in_dir = new File(argv[1]);
//					File out_dir = new File(argv[2]);
//
//					if (in_dir.isDirectory() && out_dir.isDirectory()) {
//						doLoadStore(in_dir, out_dir);
//					}
//					else {
//						printUsage();
//					}
//				}
//				catch (NullPointerException e) {
//					printUsage();
//				}
//			}
//
//			else if (argv[0].equals("-branch")) {
//				if (argv.length != 3) {
//					printUsage();
//				}
//
//				try {
//					File in_dir = new File(argv[1]);
//					File out_dir = new File(argv[2]);
//
//					if (in_dir.isDirectory() && out_dir.isDirectory()) {
//						doBranch(in_dir, out_dir);
//					}
//					else {
//						printUsage();
//					}
//				}
//				catch (NullPointerException e) {
//					printUsage();
//				}
//			}
		}
}
