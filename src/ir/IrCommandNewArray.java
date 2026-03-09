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

public class IrCommandNewArray extends IrCommand
{
	public Temp dst;        // destination temp
	public Temp size;       // size temp
	public String typeName; // element type
	
	public IrCommandNewArray(Temp dst, Temp size, String typeName)
	{
		this.dst = dst;
		this.size = size;
		this.typeName = typeName;
	}
	
	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := new " + typeName + "[Temp_" + size.getSerialNumber() + "]";
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + size.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + dst.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String sizeReg = r(size, regMap);
		String dstReg  = r(dst,  regMap);
		// Save size in $s0 before syscall overwrites $v0 and before move clobbers sizeReg
		mg.emit("move $s0, " + sizeReg + "\n");
		mg.emit("li $v0, 9\n");
		mg.emit("move $a0, $s0\n");
		mg.emit("add $a0, $a0, 1\n");    // one extra cell to store the length
		mg.emit("mul $a0, $a0, 4\n");    // convert to bytes
		mg.emit("syscall\n");
		mg.emit("move " + dstReg + ", $v0\n");
		mg.emit("sw $s0, 0($v0)\n");     // store length at index 0 (use saved size)
	}
}
