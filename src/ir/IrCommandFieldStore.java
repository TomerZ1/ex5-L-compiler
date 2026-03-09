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

public class IrCommandFieldStore extends IrCommand
{
	public Temp object;        // object temp
	public String fieldName;
	public Temp value;         // value to store
	public String className;   // static class type of object (for field offset computation)

	public IrCommandFieldStore(Temp object, String fieldName, Temp value, String className)
	{
		this.object = object;
		this.fieldName = fieldName;
		this.value = value;
		this.className = className;
	}
	
	@Override
	public String toString() {
		return "Temp_" + object.getSerialNumber() + "." + fieldName + " := Temp_" + value.getSerialNumber();
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + object.getSerialNumber());
		result.add("Temp_" + value.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		return new HashSet<>();
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String objReg = r(object, regMap);
		String valReg = r(value,  regMap);
		mg.emitNilCheck(objReg);
		types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
		int fieldIdx   = tc.getFieldIndex(fieldName);
		int byteOffset = (fieldIdx + 1) * 4;
		mg.emit("sw " + valReg + ", " + byteOffset + "(" + objReg + ")\n");
	}
}
