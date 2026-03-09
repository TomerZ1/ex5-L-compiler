/***********/
/* PACKAGE */
/***********/
package ir;

/*******************/
/* GENERAL IMPORTS */
/*******************/
import java.util.*;

/*******************/
/* PROJECT IMPORTS */
/*******************/
import temp.*;

public class IrCommandNewClass extends IrCommand
{
	public Temp dst;        // destination temp
	public String className;
	
	public IrCommandNewClass(Temp dst, String className)
	{
		this.dst = dst;
		this.className = className;
	}
	
	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := new " + className + "()";
	}

	public Set<String> getReadTemps() {
		return new HashSet<>();
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + dst.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
		int totalFields = tc.totalFieldCount();
		int objSize = (totalFields + 1) * 4;  // +1 for vtable pointer at word 0

		mg.emit("li $v0, 9\n");
		mg.emit("li $a0, " + objSize + "\n");
		mg.emit("syscall\n");
		mg.emit("move " + r(dst, regMap) + ", $v0\n");

		// Store vtable pointer at word 0
		mg.emit("la $s0, vt_" + className + "\n");
		mg.emit("sw $s0, 0($v0)\n");

		// Zero-initialize all fields (sbrk does not guarantee zeroed memory)
		for (int i = 0; i < totalFields; i++) {
			mg.emit("sw $zero, " + ((i + 1) * 4) + "($v0)\n");
		}
	}
}
