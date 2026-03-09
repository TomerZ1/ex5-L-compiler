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

/**
 * Virtual method call: dispatches through the vtable of the receiver object.
 *
 * Calling convention:
 *   - explicit args are pushed right-to-left by the caller
 *   - receiver (self) is pushed last, sitting at fp+8 in the callee
 */
public class IrCommandVirtualCall extends IrCommand
{
	public Temp dst;          // result temp (null for void return)
	public Temp receiver;     // the object whose vtable is used
	public String className;  // static type of receiver (for vtable index lookup)
	public String methodName;
	public TempList args;     // explicit args (does NOT include receiver/self)

	public IrCommandVirtualCall(Temp dst, Temp receiver, String className,
	                             String methodName, TempList args)
	{
		this.dst = dst;
		this.receiver = receiver;
		this.className = className;
		this.methodName = methodName;
		this.args = args;
	}

	@Override
	public String toString() {
		return (dst != null ? "Temp_" + dst.getSerialNumber() + " := " : "")
			+ "virtual_call Temp_" + receiver.getSerialNumber()
			+ "." + methodName + "(...)";
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		result.add("Temp_" + receiver.getSerialNumber());
		TempList cur = args;
		while (cur != null) {
			result.add("Temp_" + cur.head.getSerialNumber());
			cur = cur.tail;
		}
		return result;
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		if (dst != null) result.add("Temp_" + dst.getSerialNumber());
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		// Build explicit args list
		java.util.List<temp.Temp> argList = new java.util.ArrayList<>();
		TempList cur = args;
		while (cur != null) { argList.add(cur.head); cur = cur.tail; }

		// Push explicit args right-to-left
		for (int i = argList.size() - 1; i >= 0; i--) {
			mg.emit("subu $sp, $sp, 4\n");
			mg.emit("sw " + r(argList.get(i), regMap) + ", 0($sp)\n");
		}
		// Push self (receiver) LAST — sits at fp+8 in callee
		String selfReg = r(receiver, regMap);
		mg.emit("subu $sp, $sp, 4\n");
		mg.emit("sw " + selfReg + ", 0($sp)\n");

		// Virtual dispatch: vtable ptr at obj[0], method ptr at vtable[idx*4]
		mg.emit("lw $s0, 0(" + selfReg + ")\n");
		int vtIdx = mips.MipsGenerator.getVtableIndex(className, methodName);
		mg.emit("lw $s1, " + (vtIdx * 4) + "($s0)\n");
		mg.emit("jalr $s1\n");

		// Pop self + explicit args
		int totalArgs = argList.size() + 1;
		mg.emit("addu $sp, $sp, " + (totalArgs * 4) + "\n");

		if (dst != null)
			mg.emit("move " + r(dst, regMap) + ", $v0\n");
	}
}
