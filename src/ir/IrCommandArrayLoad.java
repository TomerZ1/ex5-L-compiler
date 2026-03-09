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

public class IrCommandArrayLoad extends IrCommand
{
	public Temp dst;        // destination temp
	public Temp array;      // array temp
	public Temp index;      // index temp
	
	public IrCommandArrayLoad(Temp dst, Temp array, Temp index)
	{
		this.dst = dst;
		this.array = array;
		this.index = index;
	}
	
	@Override
	public String toString() {
		return "Temp_" + dst.getSerialNumber() + " := Temp_" + array.getSerialNumber() + "[Temp_" + index.getSerialNumber() + "]";
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + array.getSerialNumber());
		result.add("Temp_" + index.getSerialNumber());
		return result;
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + dst.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		String arrReg = r(array, regMap);
		String idxReg = r(index, regMap);
		String dstReg = r(dst,   regMap);
		mg.emitNilCheck(arrReg);
		mg.emitBoundsCheck(arrReg, idxReg);
		mg.emit("move $s0, " + idxReg + "\n");
		mg.emit("add $s0, $s0, 1\n");         // skip length cell
		mg.emit("mul $s0, $s0, 4\n");
		mg.emit("addu $s0, " + arrReg + ", $s0\n");
		mg.emit("lw " + dstReg + ", 0($s0)\n");
	}
}
