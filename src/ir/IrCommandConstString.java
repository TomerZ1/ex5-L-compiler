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
		// Strip surrounding quotes that the lexer includes in the value
		String inner = (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length()-1) == '"')
				? value.substring(1, value.length()-1) : value;
		mg.addDataEntry(strLabel + ": .asciiz \"" + inner + "\"\n");
		mg.emit("la " + r(t, regMap) + ", " + strLabel + "\n");
	}
}
