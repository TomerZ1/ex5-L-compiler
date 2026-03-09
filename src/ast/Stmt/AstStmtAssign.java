package ast.Stmt;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import ast.Exp.AstExp;
import ast.Helpers.HelperFunctions;
import ast.Var.AstVar;
import ast.Var.AstVarSimple;
import ast.Var.AstVarField;
import ast.Var.AstVarSubscript;
import types.Type;
import temp.*;
import ir.*;

public class AstStmtAssign extends AstStmt
{
	/***************/
	/*  var := exp */
	/***************/
	public AstVar var;
	public AstExp exp;

	/*******************/
	/*  CONSTRUCTOR(S) */
	/*******************/
	public AstStmtAssign(AstVar var, AstExp exp)
	{
		/******************************/
		/* SET A UNIQUE SERIAL NUMBER */
		/******************************/
		serialNumber = AstNodeSerialNumber.getFresh();

		/***************************************/
		/* PRINT CORRESPONDING DERIVATION RULE */
		/***************************************/
		System.out.print("====================== stmt -> var ASSIGN exp SEMICOLON\n");

		/*******************************/
		/* COPY INPUT DATA MEMBERS ... */
		/*******************************/
		this.var = var;
		this.exp = exp;
	}

	/*********************************************************/
	/* The printing message for an assign statement AST node */
	/*********************************************************/
	public void printMe()
	{
		/********************************************/
		/* AST NODE TYPE = AST ASSIGNMENT STATEMENT */
		/********************************************/
		System.out.print("AST NODE ASSIGN STMT\n");

		/***********************************/
		/* RECURSIVELY PRINT VAR + EXP ... */
		/***********************************/
		if (var != null) var.printMe();
		if (exp != null) exp.printMe();

		/***************************************/
		/* PRINT Node to AST GRAPHVIZ DOT file */
		/***************************************/
		AstGraphviz.getInstance().logNode(
				serialNumber,
			"ASSIGN\nleft := right\n");
		
		/****************************************/
		/* PRINT Edges to AST GRAPHVIZ DOT file */
		/****************************************/
		AstGraphviz.getInstance().logEdge(serialNumber,var.serialNumber);
		AstGraphviz.getInstance().logEdge(serialNumber,exp.serialNumber);
	}
	@Override
    public Type SemantMe() {
        System.out.println("DEBUG StmtAssign: Processing assignment at line " + this.lineNumber);
        System.out.println("DEBUG StmtAssign: var.lineNumber = " + var.lineNumber);
        System.out.println("DEBUG StmtAssign: exp.lineNumber = " + exp.lineNumber);
        Type varType = var.SemantMe();
        System.out.println("DEBUG StmtAssign: varType = " + (varType != null ? varType.name : "null"));
        Type expType = exp.SemantMe();
        System.out.println("DEBUG StmtAssign: expType = " + (expType != null ? expType.name : "null"));

        if (!HelperFunctions.canAssign(varType, expType)) {
             System.out.format(">> ERROR: Cannot assign type %s to %s\n", 
                 expType != null ? expType.name : "null", 
                 varType != null ? varType.name : "null");
             this.lineNumber = var.lineNumber;
             error();
        }
        return null;
    }

    public Temp irMe()
    {
        // Handle different variable types
        if (var instanceof AstVarSimple) {
            // Simple variable: x := exp  (RHS first is fine, no LHS side-effects)
            Temp src = exp.irMe();
            AstVarSimple simpleVar = (AstVarSimple) var;
            if (simpleVar.isClassField) {
                Temp selfTemp = TempFactory.getInstance().getFreshTemp();
                Ir.getInstance().AddIrCommand(new IrCommandLoad(selfTemp, "__self"));
                Ir.getInstance().AddIrCommand(new IrCommandFieldStore(selfTemp, simpleVar.name, src, simpleVar.selfClassName));
            } else {
                String varIrName = (simpleVar.irVarName != null) ? simpleVar.irVarName : simpleVar.name;
                Ir.getInstance().AddIrCommand(new IrCommandStore(varIrName, src));
            }
        } else if (var instanceof AstVarField) {
            // Field access: obj.field := exp — evaluate obj first (LHS), then RHS
            AstVarField fieldVar = (AstVarField) var;
            Temp objTemp = fieldVar.var.irMe();
            Temp src = exp.irMe();
            Ir.getInstance().AddIrCommand(new IrCommandFieldStore(objTemp, fieldVar.fieldName, src, fieldVar.objClassName));
        } else if (var instanceof AstVarSubscript) {
            // Array subscript: arr[index] := exp — evaluate arr and index first, then RHS
            AstVarSubscript subsVar = (AstVarSubscript) var;
            Temp arrTemp = subsVar.var.irMe();
            Temp indexTemp = subsVar.subscript.irMe();
            Temp src = exp.irMe();
            Ir.getInstance().AddIrCommand(new IrCommandArrayStore(arrTemp, indexTemp, src));
        }
        
        return null;
    }
}
