import java.io.*;
import java.util.*;
import java_cup.runtime.Symbol;
import ast.*;
import ast.Helpers.HelperFunctions;

public class Main
{
	static public void main(String argv[])
	{
		Lexer l;
		Parser p;
		AstProgram ast;
		FileReader fileReader = null;
		PrintWriter fileWriter = null;
		String inputFileName  = argv[0];
		String outputFileName = argv[1];

		try
		{
			/********************************/
			/* [1] Initialize a file reader */
			/********************************/
			fileReader = new FileReader(inputFileName);

			/********************************/
			/* [2] Initialize a file writer */
			/*** (used for error output)  ***/
			/********************************/
			fileWriter = new PrintWriter(outputFileName);

			/****************************************/
			/* [2.5] Set file writer in HelperFunctions */
			/****************************************/
			HelperFunctions.setFileWriter(fileWriter);

			/******************************/
			/* [3] Initialize a new lexer */
			/******************************/
			l = new Lexer(fileReader);

			/*******************************/
			/* [4] Initialize a new parser */
			/*******************************/
			p = new Parser(l, fileWriter);

			/***********************************/
			/* [5] 3 ... 2 ... 1 ... Parse !!! */
			/***********************************/
			ast = (AstProgram) p.parse().value;

			/**************************/
			/* [6] Semant the AST ... */
			/**************************/
			ast.SemantMe();

			/*********************************/
			/* [7] Generate IR from AST ...  */
			/*********************************/
			ast.irMe();

			/************************************/
			/* [8] Get flat IR command list     */
			/************************************/
			List<ir.IrCommand> irCommands = ir.Ir.getInstance().getCommandList();

			/************************************************************/
			/* [9] Split IR into global preamble + per-function segments */
			/*     Uses isFunctionEntry flag on IrCommandLabel          */
			/************************************************************/
			List<ir.IrCommand> globalPreamble = new ArrayList<>();
			Map<String, List<ir.IrCommand>> funcSegments = new LinkedHashMap<>();
			String curFunc = null;

			for (ir.IrCommand c : irCommands) {
				if (c instanceof ir.IrCommandLabel && ((ir.IrCommandLabel) c).isFunctionEntry) {
					curFunc = ((ir.IrCommandLabel) c).getLabelName();
					funcSegments.put(curFunc, new ArrayList<>());
				}
				if (curFunc == null) globalPreamble.add(c);
				else funcSegments.get(curFunc).add(c);
			}

			/*****************************************************/
			/* [10] Register allocation per function             */
			/*****************************************************/
			Map<String, Map<String,String>> allRegAllocs = new LinkedHashMap<>();

			// Global preamble (initializer code that runs inside user_main)
			{
				cfg.LivenessAnalysis la = new cfg.LivenessAnalysis(globalPreamble);
				la.compute();
				cfg.InterferenceGraph ig = cfg.InterferenceGraph.build(la);
				allRegAllocs.put("__global_preamble", new regalloc.RegisterAllocator(ig, fileWriter).allocate());
			}

			// Per-function
			for (Map.Entry<String, List<ir.IrCommand>> e : funcSegments.entrySet()) {
				cfg.LivenessAnalysis la = new cfg.LivenessAnalysis(e.getValue());
				la.compute();
				cfg.InterferenceGraph ig = cfg.InterferenceGraph.build(la);
				allRegAllocs.put(e.getKey(), new regalloc.RegisterAllocator(ig, fileWriter).allocate());
			}

			/*****************************************************/
			/* [11] MIPS code generation                         */
			/*****************************************************/
			// Close the error-output writer; MipsGenerator opens the real output file
			fileWriter.close();
			fileWriter = null;

			mips.MipsGenerator.init(outputFileName);
			mips.MipsGenerator mg = mips.MipsGenerator.getInstance();

			// Emit vtables for all registered classes (in declaration order)
			ir.Ir irSingleton = ir.Ir.getInstance();
			for (String className : irSingleton.classOrder) {
				types.TypeClass tc = irSingleton.lookupClass(className);
				List<String> vt = mips.MipsGenerator.buildVtable(tc);
				mg.emitVtable(className, vt);
			}

			// Emit global .data entries (IrCommandAllocate only, executed before user_main)
			for (ir.IrCommand c : globalPreamble) {
				if (c instanceof ir.IrCommandAllocate)
					c.mipsMe(mg, null);
			}

			// Emit per-function MIPS code
			Map<String,String> preambleRegMap = allRegAllocs.get("__global_preamble");

			for (Map.Entry<String, List<ir.IrCommand>> e : funcSegments.entrySet()) {
				String fn = e.getKey();
				Map<String,String> regMap = allRegAllocs.get(fn);
				boolean isMain = fn.equals("main");

				for (ir.IrCommand c : e.getValue()) {
					// For user_main's function-entry label: emit prologue then inject global inits
					if (isMain && c instanceof ir.IrCommandLabel
							&& ((ir.IrCommandLabel) c).isFunctionEntry) {
						c.mipsMe(mg, regMap);  // emits "user_main:" + prologue
						// Inject global variable initializer code at the beginning of user_main
						for (ir.IrCommand gc : globalPreamble) {
							if (!(gc instanceof ir.IrCommandAllocate))
								gc.mipsMe(mg, preambleRegMap);
						}
					} else {
						c.mipsMe(mg, regMap);
					}
				}
				mg.endFunction(fn);
			}

			// MIPS main stub and runtime handlers
			mg.emitMipsMain();
			mg.emitRuntimeHandlers();
			mg.finalizeFile();
		}

		catch (Exception e)
		{
			e.printStackTrace();
			if (fileWriter != null) {
				fileWriter.write("ERROR");
			} else {
				// MipsGenerator was already initialized — write error to output file
				try (PrintWriter pw = new PrintWriter(outputFileName)) {
					pw.write("ERROR");
				} catch (Exception ignored) {}
			}
		}

		finally
		{
			if (fileWriter != null) {
				fileWriter.close();
			}
			try {
				if (fileReader != null) fileReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/** Builds ordered list of method labels for className's vtable (static overrides handled). */
	private static List<String> buildVtable(types.TypeClass tc) {
		return mips.MipsGenerator.buildVtable(tc);
	}
}

