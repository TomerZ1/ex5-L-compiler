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

public class IrCommandBinopConcatStrings extends IrCommand
{
	public Temp t1;
	public Temp t2;
	public Temp dst;

	public IrCommandBinopConcatStrings(Temp dst, Temp t1, Temp t2)
	{
		this.dst = dst;
		this.t1 = t1;
		this.t2 = t2;
	}

	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := Temp_" + t1.getSerialNumber() + " ++ Temp_" + t2.getSerialNumber();
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
		mg.emit("move $a0, " + r(t1, regMap) + "\n");
		mg.emit("move $a1, " + r(t2, regMap) + "\n");
		mg.emit("jal __str_concat\n");
		mg.emit("move " + r(dst, regMap) + ", $v0\n");
	}
}
