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

public class IrCommandFieldLoad extends IrCommand
{
	public Temp dst;           // destination temp
	public Temp object;        // object temp
	public String fieldName;
	public String className;   // static class type of object (for field offset computation)

	public IrCommandFieldLoad(Temp dst, Temp object, String fieldName, String className)
	{
		this.dst = dst;
		this.object = object;
		this.fieldName = fieldName;
		this.className = className;
	}
	
	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := Temp_" + object.getSerialNumber() + "." + fieldName;
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + object.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + dst.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String objReg = r(object, regMap);
		String dstReg = r(dst,    regMap);
		mg.emitNilCheck(objReg);
		types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
		int fieldIdx   = tc.getFieldIndex(fieldName);
		int byteOffset = (fieldIdx + 1) * 4;  // +1 because word 0 is vtable
		mg.emit("lw " + dstReg + ", " + byteOffset + "(" + objReg + ")\n");
	}
}
