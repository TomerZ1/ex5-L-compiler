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

public class IrCommandPrintString extends IrCommand
{
	public Temp t;

	public IrCommandPrintString(Temp t)
	{
		this.t = t;
	}

	@Override
	public String toString() {
		return "PrintString(Temp_" + t.getSerialNumber() + ")";
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
		mg.emit("li $v0, 4\n");
		mg.emit("syscall\n");
	}
}
