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

public class Ir
{
	private IrCommand head=null;
	private IrCommandList tail=null;

	// ----------------------------------------------------------------
	// Registry 1: function parameters
	// Per-function map: funcLabel -> (scopedParamName -> 0-based slot index).
	// This prevents a param name from one function falsely matching a
	// local variable with the same scoped name in another function.
	// ----------------------------------------------------------------
	private java.util.Map<String, java.util.Map<String, Integer>> funcParamIndex = new java.util.HashMap<>();
	private String currentFuncLabel = null;

	/** Must be called before registerParam() whenever a new function's IR is being built. */
	public void setCurrentFuncLabel(String funcLabel) { currentFuncLabel = funcLabel; }

	public void registerParam(String irName, int index) {
		if (currentFuncLabel == null) return;
		funcParamIndex.computeIfAbsent(currentFuncLabel, k -> new java.util.HashMap<>()).put(irName, index);
	}

	public boolean isParam(String funcLabel, String irName) {
		java.util.Map<String, Integer> m = funcParamIndex.get(funcLabel);
		return m != null && m.containsKey(irName);
	}

	/** Legacy single-arg form — queries against the currentFuncLabel context. */
	public boolean isParam(String irName) { return isParam(currentFuncLabel, irName); }

	public int getParamIndex(String funcLabel, String irName) {
		java.util.Map<String, Integer> m = funcParamIndex.get(funcLabel);
		return (m != null) ? m.getOrDefault(irName, -1) : -1;
	}

	public int getParamIndex(String irName) { return getParamIndex(currentFuncLabel, irName); }

	// ----------------------------------------------------------------
	// Registry 2: global variables
	// Set of scoped IR names that are at global (scope-0) level.
	// ----------------------------------------------------------------
	private java.util.Set<String> globals = new java.util.HashSet<>();

	public void registerGlobal(String irName) { globals.add(irName); }
	public boolean isGlobal(String irName) { return globals.contains(irName); }

	// ----------------------------------------------------------------
	// Registry 3: class type information + declaration order
	// Needed by MipsGenerator to compute vtable indices and field offsets.
	// ----------------------------------------------------------------
	private java.util.Map<String, types.TypeClass> classRegistry = new java.util.HashMap<>();
	public  java.util.List<String> classOrder = new java.util.ArrayList<>();

	public void registerClass(String name, types.TypeClass tc) {
		classRegistry.put(name, tc);
		if (!classOrder.contains(name)) classOrder.add(name);
	}
	public types.TypeClass lookupClass(String name) { return classRegistry.get(name); }

	/******************/
	/* Add Ir command */
	/******************/
	public void AddIrCommand(IrCommand cmd)
	{
		if ((head == null) && (tail == null))
		{
			this.head = cmd;
		}
		else if ((head != null) && (tail == null))
		{
			this.tail = new IrCommandList(cmd,null);
		}
		else
		{
			IrCommandList it = tail;
			while ((it != null) && (it.tail != null))
			{
				it = it.tail;
			}
			it.tail = new IrCommandList(cmd,null);
		}
	}

	/**************************************/
	/* USUAL SINGLETON IMPLEMENTATION ... */
	/**************************************/
	private static Ir instance = null;

	/*****************************/
	/* PREVENT INSTANTIATION ... */
	/*****************************/
        private java.util.HashMap<String, String> irNameToOriginalName = new java.util.HashMap<>();

        public void registerIrName(String irName, String originalName) {
                irNameToOriginalName.put(irName, originalName);
        }

        public String getOriginalName(String irName) {
                return irNameToOriginalName.getOrDefault(irName, irName);
        }

	/* GET SINGLETON INSTANCE ... */
	/******************************/
	public static Ir getInstance()
	{
		if (instance == null)
		{
			/*******************************/
			/* [0] The instance itself ... */
			/*******************************/
			instance = new Ir();
		}
		return instance;
	}
	
	/********************************/
	/* GET LIST OF ALL IR COMMANDS  */
	/********************************/
	public java.util.ArrayList<IrCommand> getCommandList()
	{
		java.util.ArrayList<IrCommand> result = new java.util.ArrayList<>();
		
		if (head != null) {
			result.add(head);
		}
		
		IrCommandList current = tail;
		while (current != null) {
			if (current.head != null) {
				result.add(current.head);
			}
			current = current.tail;
		}
		
		return result;
	}
	
	/********************************/
	/* PRINT ALL IR COMMANDS        */
	/********************************/
	public void printIrToFile(String filename) throws java.io.IOException
	{
		java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));
		
		java.util.ArrayList<IrCommand> commands = getCommandList();
		writer.println("========== IR COMMANDS ==========");
		writer.println("Total commands: " + commands.size());
		writer.println("=================================");
		
		for (IrCommand cmd : commands) {
			writer.println(cmd.toString());
		}
		
		writer.close();
	}
}
