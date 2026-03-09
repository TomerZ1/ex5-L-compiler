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

public class IrCommandPrintInt extends IrCommand
{
	Temp t;
	
	public IrCommandPrintInt(Temp t)
	{
		this.t = t;
	}
	
	@Override
	public String toString() {
		return "PrintInt(Temp_" + t.getSerialNumber() + ")";
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + t.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		return new HashSet<>();
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		mg.emit("move $a0, " + r(t, regMap) + "\n");
		mg.emit("li $v0, 1\n");
		mg.emit("syscall\n");
		mg.emit("li $a0, 32\n");    // trailing space
		mg.emit("li $v0, 11\n");
		mg.emit("syscall\n");
	}
}
