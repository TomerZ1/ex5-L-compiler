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

public class IrCommandConstString extends IrCommand
{
	public Temp t;
	public String value;
	
	public IrCommandConstString(Temp t, String value)
	{
		this.t = t;
		this.value = value;
	}
	
	@Override
	public String toString() {
		return "Temp_" + t.getSerialNumber() + " := \"" + value + "\"";
	}

	public Set<String> getReadTemps() {
		return new HashSet<>();
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + t.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String strLabel = mg.freshLabel("str_const");
		// Escape special characters in string value
		String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		mg.addDataEntry(strLabel + ": .asciiz \"" + escaped + "\"\n");
		mg.emit("la " + r(t, regMap) + ", " + strLabel + "\n");
	}
}
