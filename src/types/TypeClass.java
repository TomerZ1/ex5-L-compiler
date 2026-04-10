package types;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class TypeClass extends Type
{
	/*********************************************************************/
	/* If this class does not extend a father class this should be null  */
	/*********************************************************************/
	public TypeClass father;

	/**************************************************/
	/* Gather up all data members in one place        */
	/* Note that data members coming from the AST are */
	/* packed together with the class methods         */
	/**************************************************/
	public TypeList dataMembers;

	// LinkedHashMap preserves declaration order — critical for deterministic
	// vtable indices and object field offsets.
	public LinkedHashMap<String, Type> fields = new LinkedHashMap<>();
	public LinkedHashMap<String, TypeFunction> methods = new LinkedHashMap<>();
	// Integer default values for fields that have initializers (e.g. int age := 10)
	public LinkedHashMap<String, Integer> fieldDefaults = new LinkedHashMap<>();
	// String default values for fields that have initializers (raw literal text).
	public LinkedHashMap<String, String> stringFieldDefaults = new LinkedHashMap<>();
	public TypeClass(TypeClass father, String name, TypeList dataMembers)
	{
		this.name = name;
		this.father = father;
		this.dataMembers = dataMembers;
	}

	@Override
	public boolean isClass(){ return true;}

	public Type lookupField(String fieldName)
	{
		// Look in current class
		if (fields.containsKey(fieldName)) {
			return fields.get(fieldName);
		}
		// Check father recursively
		if (father != null) {
			return father.lookupField(fieldName);
		}
		// Not found
		return null;
	}

	public TypeFunction lookupMethod(String methodName)
	{
		// Look in current class
		if (methods.containsKey(methodName)) {
			return methods.get(methodName);
		}
		// Check father recursively
		if (father != null) {
			return father.lookupMethod(methodName);
		}
		// Not found
		return null;
	}

	/**
	 * Returns the 0-based field index in the full object layout.
	 * Inherited fields come first, then own fields.
	 * Object byte offset for field at index i = (i+1)*4  (word 0 is vtable ptr).
	 */
	public int getFieldIndex(String fieldName) {
		if (father != null) {
			int fatherResult = father.getFieldIndex(fieldName);
			if (fatherResult >= 0) return fatherResult;
		}
		int base = (father != null) ? father.totalFieldCount() : 0;
		int i = 0;
		for (String key : fields.keySet()) {
			if (key.equals(fieldName)) return base + i;
			i++;
		}
		return -1;
	}

	/** Total data-field count (own + inherited), NOT counting the vtable pointer. */
	public int totalFieldCount() {
		int parentCount = (father != null) ? father.totalFieldCount() : 0;
		return parentCount + fields.size();
	}

	public void splitMembers() {
    	TypeList it = dataMembers;

		while (it != null && it.head != null) {
			Type t = it.head;

			if (t instanceof TypeClassVarDec) {
				TypeClassVarDec varDec = (TypeClassVarDec) t;
				// Check if a method with this name already exists
				if (methods.containsKey(varDec.name)) {
					System.out.format(">> ERROR: Field '%s' has same name as a method in the same class\n", varDec.name);
					ast.Helpers.HelperFunctions.printErrorAndExit(varDec.lineNumber);
				}
				fields.put(varDec.name, varDec.t);
			}
			else if (t instanceof TypeFunction) {
				TypeFunction func = (TypeFunction) t;
				// Check if a field with this name already exists
				if (fields.containsKey(func.name)) {
					System.out.format(">> ERROR: Method '%s' has same name as a field in the same class\n", func.name);
					ast.Helpers.HelperFunctions.printErrorAndExit(func.lineNumber);
				}
				methods.put(func.name, func);
			}
			it = it.tail;
		}
	}
}
