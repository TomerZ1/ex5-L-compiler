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

public class IrCommandJumpIfEqToZero extends IrCommand
{
	Temp t;
	String labelName;
	
	public IrCommandJumpIfEqToZero(Temp t, String labelName)
	{
		this.t          = t;
		this.labelName = labelName;
	}

	@Override
    public boolean isJump() { return true; }

    @Override
    public boolean isConditionalJump() { return true; }

    @Override
    public String getJumpLabel() { return labelName; }
	
	@Override
	public String toString() {
		return "if Temp_" + t.getSerialNumber() + " == 0 goto " + labelName;
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
		mg.emit("beq " + r(t, regMap) + ", $zero, " + labelName + "\n");
	}
}
