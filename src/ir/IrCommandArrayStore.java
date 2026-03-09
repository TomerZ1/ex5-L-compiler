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

public class IrCommandArrayStore extends IrCommand
{
	public Temp array;      // array temp
	public Temp index;      // index temp
	public Temp value;      // value to store
	
	public IrCommandArrayStore(Temp array, Temp index, Temp value)
	{
		this.array = array;
		this.index = index;
		this.value = value;
	}
	
	@Override
	public String toString() {
		return "Temp_" + array.getSerialNumber() + "[Temp_" + index.getSerialNumber() + "] := Temp_" + value.getSerialNumber();
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + array.getSerialNumber());
		result.add("Temp_" + index.getSerialNumber());
		result.add("Temp_" + value.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		return new HashSet<>();
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String arrReg = r(array, regMap);
		String idxReg = r(index, regMap);
		String valReg = r(value, regMap);
		mg.emitNilCheck(arrReg);
		mg.emitBoundsCheck(arrReg, idxReg);
		mg.emit("move $s0, " + idxReg + "\n");
		mg.emit("add $s0, $s0, 1\n");
		mg.emit("mul $s0, $s0, 4\n");
		mg.emit("addu $s0, " + arrReg + ", $s0\n");
		mg.emit("sw " + valReg + ", 0($s0)\n");
	}
}
