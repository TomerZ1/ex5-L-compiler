/***********/
/* PACKAGE */
/***********/
package ir;

/*******************/
/* GENERAL IMPORTS */
/*******************/

/*******************/
/* PROJECT IMPORTS */
/*******************/

public class IrCommandJumpLabel extends IrCommand
{
	String labelName;
	
	public IrCommandJumpLabel(String labelName)
	{
		this.labelName = labelName;
	}

	@Override
    public boolean isJump() { return true; }

    @Override
    public boolean isUnconditionalJump() { return true; }

    @Override
    public String getJumpLabel() { return labelName; }
	
	@Override
	public String toString() {
		return "goto " + labelName;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		mg.emit("j " + labelName + "\n");
	}
}
