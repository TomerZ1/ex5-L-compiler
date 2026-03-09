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

public class IrCommandReturn extends IrCommand
{
	public Temp returnValue; // null for void return
	
	public IrCommandReturn(Temp returnValue)
	{
		this.returnValue = returnValue;
	}
	
	@Override
	public String toString() {
		if (returnValue == null) {
			return "return";
		}
		return "return Temp_" + returnValue.getSerialNumber();
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		if (returnValue != null) {
			result.add("Temp_" + returnValue.getSerialNumber());
		}
		return result;
	}

	public Set<String> getWriteTemps() {
		return new HashSet<>();
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		if (returnValue != null)
			mg.emit("move $v0, " + r(returnValue, regMap) + "\n");
		String fn = mg.currentFunc;
		String mipsName = (fn != null && fn.equals("main")) ? "user_main" : fn;
		mg.emit("j " + mipsName + "_epilogue\n");
	}
}
