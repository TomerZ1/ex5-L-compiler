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

public class IrCommandCall extends IrCommand
{
	public Temp dst;           // where result goes (null for void functions)
	public String funcName;
	public TempList args;      // argument temporaries
	
	public IrCommandCall(Temp dst, String funcName, TempList args)
	{
		this.dst = dst;
		this.funcName = funcName;
		this.args = args;
	}
	
	@Override
	public String toString() {
		String argsStr = "";
		if (args != null) {
			argsStr = args.toString();
		}
		if (dst == null) {
			return "call " + funcName + "(" + argsStr + ")";
		}
		return "Temp_" + dst.getSerialNumber() + " := call " + funcName + "(" + argsStr + ")";
	}

	public Set<String> getReadTemps() {
		Set<String> result = new HashSet<>();
		TempList current = args;
		while (current != null) {
			result.add("Temp_" + current.head.getSerialNumber());
			current = current.tail;
		}
		return result;
	}

	public Set<String> getWriteTemps() {
		Set<String> result = new HashSet<>();
		if (dst != null) {
			result.add("Temp_" + dst.getSerialNumber());
		}
		return result;
	}

	@Override
	public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap) {
		// Build args list
		java.util.List<temp.Temp> argList = new java.util.ArrayList<>();
		TempList cur = args;
		while (cur != null) { argList.add(cur.head); cur = cur.tail; }
		// Push right-to-left
		for (int i = argList.size() - 1; i >= 0; i--) {
			mg.emit("subu $sp, $sp, 4\n");
			mg.emit("sw " + r(argList.get(i), regMap) + ", 0($sp)\n");
		}
		String callee = funcName.equals("main") ? "user_main" : funcName;
		mg.emit("jal " + callee + "\n");
		mg.emit("addu $sp, $sp, " + (argList.size() * 4) + "\n");
		if (dst != null)
			mg.emit("move " + r(dst, regMap) + ", $v0\n");
	}
}
