/***********/
/* PACKAGE */
/***********/
package mips;

/*******************/
/* GENERAL IMPORTS */
/*******************/
import java.io.PrintWriter;
import java.util.*;

/*******************/
/* PROJECT IMPORTS */
/*******************/
import temp.*;

public class MipsGenerator
{
	/*****************************/
	/* Output buffers            */
	/*****************************/
	private PrintWriter out;
	private StringBuilder dataSec = new StringBuilder();
	private StringBuilder textSec = new StringBuilder();

	/*****************************/
	/* Per-function state        */
	/*****************************/
	public boolean inFunction = false;
	public String  currentFunc = null;
	private Map<String, Integer> localSlots = new HashMap<>();
	private int nextSlot = -44;  // first local at fp-44

	/*****************************/
	/* Misc counter for labels   */
	/*****************************/
	private static int labelCounter = 0;

	/*****************************/
	/* Singleton                 */
	/*****************************/
	private static MipsGenerator instance = null;
	protected MipsGenerator() {}

	/** Must be called once before getInstance(), passing the output file path. */
	public static void init(String outputPath) throws Exception {
		instance = new MipsGenerator();
		instance.out = new PrintWriter(outputPath);
		instance.dataSec.append("string_access_violation: .asciiz \"Access Violation\"\n");
		instance.dataSec.append("string_illegal_div_by_0: .asciiz \"Illegal Division By Zero\"\n");
		instance.dataSec.append("string_invalid_ptr_dref: .asciiz \"Invalid Pointer Dereference\"\n");
	}

	public static MipsGenerator getInstance() { return instance; }

	/*****************************/
	/* Emit helpers              */
	/*****************************/
	public void addDataEntry(String text) { dataSec.append(text); }
	public void emit(String text)          { textSec.append(text); }
	public String freshLabel(String prefix) { return prefix + "_" + (labelCounter++); }

	/*****************************/
	/* Function prologue/epilogue */
	/*****************************/
	public void startFunction(String name) {
		inFunction  = true;
		currentFunc = name;
		localSlots  = new HashMap<>();
		nextSlot    = -44;

		String mipsName = name.equals("main") ? "user_main" : name;
		emit(mipsName + ":\n");
		emit("# prologue\n");
		emit("subu $sp, $sp, 4\n");
		emit("sw $ra, 0($sp)\n");
		emit("subu $sp, $sp, 4\n");
		emit("sw $fp, 0($sp)\n");
		emit("move $fp, $sp\n");
		for (int i = 0; i <= 9; i++) {
			emit("subu $sp, $sp, 4\n");
			emit("sw $t" + i + ", 0($sp)\n");
		}
		// Placeholder replaced in endFunction() with the real local-area size
		emit("subu $sp, $sp, LOCALSIZE_PLACEHOLDER_" + name + "\n");
	}

	public void endFunction(String name) {
		// Replace the placeholder with actual local-area byte count
		int localSize = Math.max(0, -nextSlot - 44);
		String placeholder = "LOCALSIZE_PLACEHOLDER_" + name;
		int idx = textSec.indexOf(placeholder);
		if (idx >= 0) textSec.replace(idx, idx + placeholder.length(), String.valueOf(localSize));

		String mipsName    = name.equals("main") ? "user_main" : name;
		String epilogueLabel = mipsName + "_epilogue";
		emit(epilogueLabel + ":\n");
		emit("# epilogue\n");
		// Restore $t0..$t9 (saved at $fp-4 .. $fp-40)
		for (int i = 0; i <= 9; i++) emit("lw $t" + i + ", " + (-(i + 1) * 4) + "($fp)\n");
		emit("move $sp, $fp\n");
		emit("lw $fp, 0($sp)\n");
		emit("addu $sp, $sp, 4\n");
		emit("lw $ra, 0($sp)\n");
		emit("addu $sp, $sp, 4\n");
		emit("jr $ra\n");

		inFunction  = false;
		currentFunc = null;
	}

	/*******************************/
	/* Local variable stack slots  */
	/*******************************/
	public int allocLocal(String irName) {
		if (!localSlots.containsKey(irName)) {
			localSlots.put(irName, nextSlot);
			nextSlot -= 4;
		}
		return localSlots.get(irName);
	}

	public int getLocal(String irName) {
		if (!localSlots.containsKey(irName)) return allocLocal(irName);
		return localSlots.get(irName);
	}

	/*****************************/
	/* Data section helpers      */
	/*****************************/
	public void emitVtable(String className, java.util.List<String> methodLabels) {
		addDataEntry("vt_" + className + ":\n");
		for (String lbl : methodLabels) addDataEntry(".word " + lbl + "\n");
	}

	/*****************************/
	/* Runtime check helpers     */
	/*****************************/
	public void emitNilCheck(String reg) {
		emit("beq " + reg + ", $zero, __nil_handler\n");
	}

	public void emitDivByZeroCheck(String reg) {
		emit("beq " + reg + ", $zero, __div_by_zero_handler\n");
	}

	public void emitBoundsCheck(String arrReg, String idxReg) {
		emit("bltz " + idxReg + ", __bounds_handler\n");
		emit("lw $s0, 0(" + arrReg + ")\n");
		emit("bge " + idxReg + ", $s0, __bounds_handler\n");
	}

	/** Saturate reg to the range [-32768, 32767] as required by the tutorial. */
	public void emitSaturate(String reg) {
		String skipHigh = freshLabel("sat_hi");
		String skipLow  = freshLabel("sat_lo");
		emit("li $s0, 32767\n");
		emit("ble " + reg + ", $s0, " + skipHigh + "\n");
		emit("move " + reg + ", $s0\n");
		emit(skipHigh + ":\n");
		emit("li $s0, -32768\n");
		emit("bge " + reg + ", $s0, " + skipLow + "\n");
		emit("move " + reg + ", $s0\n");
		emit(skipLow + ":\n");
	}

	/*****************************/
	/* MIPS main stub            */
	/*****************************/
	public void emitMipsMain() {
		emit("main:\n");
		emit("jal user_main\n");
		emit("li $v0, 10\n");
		emit("syscall\n");
	}

	/*****************************/
	/* Runtime handlers          */
	/*****************************/
	public void emitRuntimeHandlers() {
		emit("__nil_handler:\n");
		emit("la $a0, string_invalid_ptr_dref\n");
		emit("li $v0, 4\n");
		emit("syscall\n");
		emit("li $v0, 10\n");
		emit("syscall\n");

		emit("__div_by_zero_handler:\n");
		emit("la $a0, string_illegal_div_by_0\n");
		emit("li $v0, 4\n");
		emit("syscall\n");
		emit("li $v0, 10\n");
		emit("syscall\n");

		emit("__bounds_handler:\n");
		emit("la $a0, string_access_violation\n");
		emit("li $v0, 4\n");
		emit("syscall\n");
		emit("li $v0, 10\n");
		emit("syscall\n");

		emitStrConcat();
	}

	private void emitStrConcat() {
		emit("__str_concat:\n");
		emit("move $s4, $a0\n");
		emit("move $s5, $a1\n");
		emit("li $s2, 0\n");
		emit("move $s0, $s4\n");
		emit("__sc_l1:\n");
		emit("lb $s3, 0($s0)\n");
		emit("beq $s3, $zero, __sc_l1_done\n");
		emit("addu $s0, $s0, 1\n");
		emit("addu $s2, $s2, 1\n");
		emit("j __sc_l1\n");
		emit("__sc_l1_done:\n");
		emit("move $s1, $s5\n");
		emit("__sc_l2:\n");
		emit("lb $s3, 0($s1)\n");
		emit("beq $s3, $zero, __sc_l2_done\n");
		emit("addu $s1, $s1, 1\n");
		emit("addu $s2, $s2, 1\n");
		emit("j __sc_l2\n");
		emit("__sc_l2_done:\n");
		emit("addu $a0, $s2, 1\n");
		emit("li $v0, 9\n");
		emit("syscall\n");
		emit("move $s3, $v0\n");
		emit("move $s0, $s4\n");
		emit("move $s1, $s3\n");
		emit("__sc_cp1:\n");
		emit("lb $s2, 0($s0)\n");
		emit("beq $s2, $zero, __sc_cp1_done\n");
		emit("sb $s2, 0($s1)\n");
		emit("addu $s0, $s0, 1\n");
		emit("addu $s1, $s1, 1\n");
		emit("j __sc_cp1\n");
		emit("__sc_cp1_done:\n");
		emit("move $s0, $s5\n");
		emit("__sc_cp2:\n");
		emit("lb $s2, 0($s0)\n");
		emit("sb $s2, 0($s1)\n");
		emit("beq $s2, $zero, __sc_cp2_done\n");
		emit("addu $s0, $s0, 1\n");
		emit("addu $s1, $s1, 1\n");
		emit("j __sc_cp2\n");
		emit("__sc_cp2_done:\n");
		emit("move $v0, $s3\n");
		emit("jr $ra\n");
	}

	/*****************************/
	/* Write output file         */
	/*****************************/
	public void finalizeFile() {
		out.print(".data\n");
		out.print(dataSec.toString());
		out.print("\n.text\n");
		out.print(textSec.toString());
		out.close();
	}

	/*****************************/
	/* Vtable builder (static)   */
	/*****************************/
	public static java.util.List<String> buildVtable(types.TypeClass tc) {
		java.util.List<String> vtable = (tc.father != null) ? buildVtable(tc.father) : new java.util.ArrayList<>();
		for (Map.Entry<String, types.TypeFunction> m : tc.methods.entrySet()) {
			String methodName = m.getKey();
			String label      = tc.name + "_" + methodName;
			int idx = -1;
			for (int i = 0; i < vtable.size(); i++) {
				if (vtable.get(i).endsWith("_" + methodName)) { idx = i; break; }
			}
			if (idx >= 0) vtable.set(idx, label);
			else          vtable.add(label);
		}
		return vtable;
	}

	/**
	 * Returns the 0-based vtable index of methodName in className's vtable.
	 * Used by IrCommandVirtualCall.mipsMe.
	 */
	public static int getVtableIndex(String className, String methodName) {
		types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
		java.util.List<String> vt = buildVtable(tc);
		for (int i = 0; i < vt.size(); i++) {
			if (vt.get(i).endsWith("_" + methodName)) return i;
		}
		throw new RuntimeException("Method " + methodName + " not found in vtable of " + className);
	}
}
