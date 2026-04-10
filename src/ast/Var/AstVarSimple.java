package ast.Var;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import types.Type;
import temp.*;
import ir.*;

public class AstVarSimple extends AstVar
{
	/************************/
	/* simple variable name */
	/************************/
	public String name;
	public String irVarName = null; // scope-qualified IR name, e.g. "x_0"
	/** True when this name refers to a field of the enclosing class (implicit self access). */
	public boolean isClassField = false;
	/** Class name when isClassField is true — needed for IrCommandFieldLoad/Store. */
	public String selfClassName = null;
	public AstVarSimple(String name)
	{
		/******************************/
		/* SET A UNIQUE SERIAL NUMBER */
		/******************************/
		serialNumber = AstNodeSerialNumber.getFresh();
	
		/***************************************/
		/* PRINT CORRESPONDING DERIVATION RULE */
		/***************************************/
		System.out.format("====================== var -> ID( %s )\n",name);

		/*******************************/
		/* COPY INPUT DATA MEMBERS ... */
		/*******************************/
		this.name = name;
	}

	/**************************************************/
	/* The printing message for a simple var AST node */
	/**************************************************/
	public void printMe()
	{
		/**********************************/
		/* AST NODE TYPE = AST SIMPLE VAR */
		/**********************************/
		System.out.format("AST NODE SIMPLE VAR( %s )\n",name);

		/*********************************/
		/* Print to AST GRAPHVIZ DOT file */
		/*********************************/
		AstGraphviz.getInstance().logNode(
				serialNumber,
			String.format("SIMPLE\nVAR\n(%s)",name));
	}
	@Override
    public Type SemantMe() {
        symboltable.SymbolTable tbl = symboltable.SymbolTable.getInstance();

		// 1. Check if we're inside a class and the name is a field
		if (tbl.currentClass != null) {
			Type fieldType = tbl.currentClass.lookupField(name);
			if (fieldType != null) {
				System.out.println("DEBUG VarSimple: Looking up variable '" + name + "', found in class field: " + fieldType.name);
				this.isClassField = true;
				this.selfClassName = tbl.currentClass.name;
				return fieldType;
			}
		}

		// 2. Look for the name in the Symbol Table (local and global scopes)
		Type t = tbl.find(name);

		System.out.println("DEBUG VarSimple: Looking up variable '" + name + "', found: " + (t != null ? t.name : "NULL"));

		// 3. If not found, it's an error
		if (t == null) {
            System.out.format(">> ERROR: Variable %s not found at line %d\n", name, this.lineNumber);
            error();
        }

		// 4. Record which scope this variable resolves to, for IR generation
		int scopeIdx = tbl.getScopeIndexOf(name);
		irVarName = name + "_" + scopeIdx;

        return t;
    }

    public Temp irMe()
    {
        if (isClassField) {
            // Implicit self.field access: load self from param slot 0, then load field
            Temp selfTemp = TempFactory.getInstance().getFreshTemp();
            Ir.getInstance().AddIrCommand(new IrCommandLoad(selfTemp, "__self"));
            Temp fieldTemp = TempFactory.getInstance().getFreshTemp();
            Ir.getInstance().AddIrCommand(new IrCommandFieldLoad(fieldTemp, selfTemp, name, selfClassName));
            return fieldTemp;
        }
        Temp t = TempFactory.getInstance().getFreshTemp();
        String varIrName = (irVarName != null) ? irVarName : name;
        Ir.getInstance().AddIrCommand(new IrCommandLoad(t, varIrName));
        return t;
    }
}
