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

public class IrCommandLabel extends IrCommand
{
	String labelName;
	/** True when this label marks the entry point of a function/method. */
	public boolean isFunctionEntry = false;

	/** Standard label (not a function entry). */
	public IrCommandLabel(String labelName)
	{
		this.labelName = labelName;
	}

	/** Function-entry label — used to split the flat IR list into per-function segments. */
	public IrCommandLabel(String labelName, boolean isFunctionEntry)
	{
		this.labelName = labelName;
		this.isFunctionEntry = isFunctionEntry;
	}

	public String getLabelName() {
        return labelName;
    }
	
	@Override
	public String toString() {
		return labelName + ":";
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		if (isFunctionEntry) {
			mg.startFunction(labelName);
		} else {
			mg.emit(labelName + ":\n");
		}
	}
}
