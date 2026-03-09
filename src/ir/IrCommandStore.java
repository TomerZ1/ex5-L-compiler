/***********/
/* PACKAGE */
/***********/
package ir;

import java.util.*;

/*******************/
/* GENERAL IMPORTS */
/*******************/

/*******************/
/* PROJECT IMPORTS */
/*******************/
import temp.*;

public class IrCommandStore extends IrCommand
{
	String varName;
	Temp src;
	
	public IrCommandStore(String varName, Temp src)
	{
		this.src      = src;
		this.varName = varName;
	}
	
	@Override
	public String toString() {
		return varName + " := Temp_" + src.getSerialNumber();
	}

    @Override
    public Set<String> getWriteVariables() {
        Set<String> s = new HashSet<>();
        if (varName != null) s.add(varName);
        return s;
    }

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + src.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		return new HashSet<>();
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String srcReg = r(src, regMap);
		Ir irReg = Ir.getInstance();
		if (irReg.isGlobal(varName)) {
			mg.emit("sw " + srcReg + ", " + varName + "\n");
		} else {
			mg.emit("sw " + srcReg + ", " + mg.getLocal(varName) + "($fp)\n");
		}
	}
} 
