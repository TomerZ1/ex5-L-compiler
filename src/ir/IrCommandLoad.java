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

public class IrCommandLoad extends IrCommand
{
	Temp dst;
	String varName;
	
	public IrCommandLoad(Temp dst, String varName)
	{
		this.dst      = dst;
		this.varName = varName;
	}
	
	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := " + varName;
	}

    @Override
    public Set<String> getReadVariables() {
        Set<String> s = new HashSet<>();
        if (varName != null) s.add(varName);
        return s;
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
		String dstReg = r(dst, regMap);
		Ir irReg = Ir.getInstance();
		// Use per-function param lookup: only treat varName as a param if it is
		// registered as a param of the function currently being emitted.
		if (irReg.isParam(mg.currentFunc, varName)) {
			int idx = irReg.getParamIndex(mg.currentFunc, varName);
			mg.emit("lw " + dstReg + ", " + (8 + idx * 4) + "($fp)\n");
		} else if (irReg.isGlobal(varName)) {
			mg.emit("lw " + dstReg + ", " + varName + "\n");
		} else {
			mg.emit("lw " + dstReg + ", " + mg.getLocal(varName) + "($fp)\n");
		}
	}
} 
