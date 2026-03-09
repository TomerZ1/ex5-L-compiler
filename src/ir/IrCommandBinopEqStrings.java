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

public class IrCommandBinopEqStrings extends IrCommand
{
	public Temp t1;
	public Temp t2;
	public Temp dst;

	public IrCommandBinopEqStrings(Temp dst, Temp t1, Temp t2)
	{
		this.dst = dst;
		this.t1 = t1;
		this.t2 = t2;
	}

	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := str_eq(Temp_" + t1.getSerialNumber() + ", Temp_" + t2.getSerialNumber() + ")";
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + t1.getSerialNumber());
		result.add("Temp_" + t2.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + dst.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String rd = r(dst, regMap), r1 = r(t1, regMap), r2 = r(t2, regMap);
		String loop = mg.freshLabel("str_eq_loop");
		String neq  = mg.freshLabel("str_neq");
		String end  = mg.freshLabel("str_eq_end");
		mg.emit("li " + rd + ", 1\n");
		mg.emit("move $s0, " + r1 + "\n");
		mg.emit("move $s1, " + r2 + "\n");
		mg.emit(loop + ":\n");
		mg.emit("lb $s2, 0($s0)\n");
		mg.emit("lb $s3, 0($s1)\n");
		mg.emit("bne $s2, $s3, " + neq + "\n");
		mg.emit("beq $s2, $zero, " + end + "\n");
		mg.emit("addu $s0, $s0, 1\n");
		mg.emit("addu $s1, $s1, 1\n");
		mg.emit("j " + loop + "\n");
		mg.emit(neq + ":\n");
		mg.emit("li " + rd + ", 0\n");
		mg.emit(end + ":\n");
	}
}
