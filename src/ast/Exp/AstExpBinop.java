package ast.Exp;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;

import types.Type;
import temp.*;
import ir.*;

public class AstExpBinop extends AstExp
{
	int op;
	public AstExp left;
	public AstExp right;
	/** Type of the left operand — set during SemantMe for string/pointer dispatch in irMe(). */
	private types.Type leftType = null;
	
	/******************/
	/* CONSTRUCTOR(S) */
	/******************/
	public AstExpBinop(AstExp left, AstExp right, int op)
	{
		/******************************/
		/* SET A UNIQUE SERIAL NUMBER */
		/******************************/
		serialNumber = AstNodeSerialNumber.getFresh();

		/***************************************/
		/* PRINT CORRESPONDING DERIVATION RULE */
		/***************************************/
		System.out.print("====================== exp -> exp BINOP exp\n");

		/*******************************/
		/* COPY INPUT DATA MEMBERS ... */
		/*******************************/
		this.left = left;
		this.right = right;
		this.op = op;
	}
	
	/*************************************************/
	/* The printing message for a binop exp AST node */
	/*************************************************/
	public void printMe()
	{
		String sop="";
		
		/*********************************/
		/* CONVERT op to a printable sop */
		/*********************************/
		if (op == 0) {sop = "+";}
		if (op == 1) {sop = "-";}
		if (op == 2) {sop = "*";}
        if (op == 3) {sop = "/";}
		if (op == 4) {sop = "<";}
		if (op == 5) {sop = ">";}
		if (op == 6) {sop = "=";}
		/*************************************/
		/* AST NODE TYPE = AST BINOP EXP */
		/*************************************/
		System.out.print("AST NODE BINOP EXP\n");

		/**************************************/
		/* RECURSIVELY PRINT left + right ... */
		/**************************************/
		if (left != null) left.printMe();
		if (right != null) right.printMe();
		
		/***************************************/
		/* PRINT Node to AST GRAPHVIZ DOT file */
		/***************************************/
		AstGraphviz.getInstance().logNode(
				serialNumber,
			String.format("BINOP(%s)",sop));
		
		/****************************************/
		/* PRINT Edges to AST GRAPHVIZ DOT file */
		/****************************************/
		if (left  != null) AstGraphviz.getInstance().logEdge(serialNumber,left.serialNumber);
		if (right != null) AstGraphviz.getInstance().logEdge(serialNumber,right.serialNumber);
	}
	@Override
    public Type SemantMe() {
        Type t1 = left.SemantMe();
        Type t2 = right.SemantMe();
        this.leftType = t1;  // save for irMe() dispatch

        System.out.println("DEBUG ExpBinop: op=" + op + ", t1=" + (t1!=null?t1.name:"null") + ", t2=" + (t2!=null?t2.name:"null") + ", line=" + this.lineNumber);

        // 1. Equality Check (EQ: 6)
        if (op == 6) {
            // Check if types are compatible (Same type, or Class inheritance, or Nil)
            if (!ast.Helpers.HelperFunctions.canAssign(t1, t2) && 
                !ast.Helpers.HelperFunctions.canAssign(t2, t1)) {
                 System.out.println(">> ERROR: Equality check type mismatch");
                 error();
            }
            return types.TypeInt.getInstance();
        }

        // 2. Addition (PLUS: 0) - Supports Ints and Strings
        if (op == 0) {
            if (t1.isInt() && t2.isInt()) return types.TypeInt.getInstance();
            if (t1.isString() && t2.isString()) return types.TypeString.getInstance();
            System.out.println(">> ERROR: Plus must be between two Ints or two Strings at line " + this.lineNumber);
            error();
        }

        // 3. Other Math/Relational Ops (-, *, /, <, >) - Ints only
        if (!t1.isInt() || !t2.isInt()) {
            System.out.println(">> ERROR: Arithmetic operation on non-int types at line " + this.lineNumber);
            error();
        }
        
        // 4. Division by Zero Check (Constant folding)
        if (op == 3) { // DIVIDE
             if (right instanceof AstExpInt) {
                 if (((AstExpInt)right).value == 0) {
                     System.out.println(">> ERROR: Division by zero");
                     error();
                 }
             }
        }

        return types.TypeInt.getInstance();
    }

    public Temp irMe()
    {
        Temp t1 = null;
        Temp t2 = null;
        Temp dst = TempFactory.getInstance().getFreshTemp();

        if (left  != null) t1 = left.irMe();
        if (right != null) t2 = right.irMe();

        if (op == 0) // PLUS
        {
            if (leftType instanceof types.TypeString)
                Ir.getInstance().AddIrCommand(new IrCommandBinopConcatStrings(dst, t1, t2));
            else
                Ir.getInstance().AddIrCommand(new IrCommandBinopAddIntegers(dst, t1, t2));
        }
        else if (op == 1) // MINUS
        {
            Ir.getInstance().AddIrCommand(new IrCommandBinopSubIntegers(dst, t1, t2));
        }
        else if (op == 2) // TIMES
        {
            Ir.getInstance().AddIrCommand(new IrCommandBinopMulIntegers(dst, t1, t2));
        }
        else if (op == 3) // DIVIDE
        {
            Ir.getInstance().AddIrCommand(new IrCommandBinopDivIntegers(dst, t1, t2));
        }
        else if (op == 4) // LT
        {
            Ir.getInstance().AddIrCommand(new IrCommandBinopLtIntegers(dst, t1, t2));
        }
        else if (op == 5) // GT
        {
            Ir.getInstance().AddIrCommand(new IrCommandBinopGtIntegers(dst, t1, t2));
        }
        else if (op == 6) // EQ
        {
            if (leftType instanceof types.TypeString)
                Ir.getInstance().AddIrCommand(new IrCommandBinopEqStrings(dst, t1, t2));
            else if (leftType instanceof types.TypeInt)
                Ir.getInstance().AddIrCommand(new IrCommandBinopEqIntegers(dst, t1, t2));
            else
                Ir.getInstance().AddIrCommand(new IrCommandBinopEqPointers(dst, t1, t2));
        }

        return dst;
    }
}
