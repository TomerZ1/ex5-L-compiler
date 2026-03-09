package ast.Var;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import types.Type;

public class AstVarField extends AstVar
{
	public AstVar var;
	public String fieldName;
	/** Class name of the object — set during SemantMe for className in IR commands. */
	public String objClassName = null;
	
	/******************/
	/* CONSTRUCTOR(S) */
	/******************/
	public AstVarField(AstVar var, String fieldName)
	{
		/******************************/
		/* SET A UNIQUE SERIAL NUMBER */
		/******************************/
		serialNumber = AstNodeSerialNumber.getFresh();

		/***************************************/
		/* PRINT CORRESPONDING DERIVATION RULE */
		/***************************************/
		System.out.format("====================== var -> var DOT ID( %s )\n",fieldName);

		/*******************************/
		/* COPY INPUT DATA MEMBERS ... */
		/*******************************/
		this.var = var;
		this.fieldName = fieldName;
	}

	/*************************************************/
	/* The printing message for a field var AST node */
	/*************************************************/
	public void printMe()
	{
		/*********************************/
		/* AST NODE TYPE = AST FIELD VAR */
		/*********************************/
		System.out.print("AST NODE FIELD VAR\n");

		/**********************************************/
		/* RECURSIVELY PRINT VAR, then FIELD NAME ... */
		/**********************************************/
		if (var != null) var.printMe();
		System.out.format("FIELD NAME( %s )\n",fieldName);

		/***************************************/
		/* PRINT Node to AST GRAPHVIZ DOT file */
		/***************************************/
		AstGraphviz.getInstance().logNode(
				serialNumber,
			String.format("FIELD\nVAR\n...->%s",fieldName));
		
		/****************************************/
		/* PRINT Edges to AST GRAPHVIZ DOT file */
		/****************************************/
		if (var != null) AstGraphviz.getInstance().logEdge(serialNumber,var.serialNumber);
	}
	@Override
    public Type SemantMe() {
        // 1. Analyze the variable on the left (e.g., "x" in "x.y")
        Type t = var.SemantMe();
        
        // 2. Ensure it is a Class Type
        if (t == null || !(t instanceof types.TypeClass)) {
            System.out.println(">> ERROR: Accessing field of non-class type");
            error();
        }
        
        // 3. Look up the field inside that class
        types.TypeClass tc = (types.TypeClass) t;
        Type fieldType = tc.lookupField(fieldName);
        
        if (fieldType == null) {
            System.out.format(">> ERROR: Field %s not found in class %s\n", fieldName, tc.name);
            error();
        }
        
        this.objClassName = tc.name;
        return fieldType;
    }

    public temp.Temp irMe()
    {
        // Field access: obj.field - load field value
        temp.Temp objTemp = var.irMe();
        temp.Temp fieldTemp = temp.TempFactory.getInstance().getFreshTemp();
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandFieldLoad(fieldTemp, objTemp, fieldName, objClassName));
        return fieldTemp;
    }
}
