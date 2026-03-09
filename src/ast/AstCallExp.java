package ast;

import ast.Exp.AstExp;
import ast.Exp.AstExpList;
import ast.Var.AstVar;
import types.Type;

public class AstCallExp extends AstExp {

    public AstVar receiver;       // null if calling f()
    public String methodName;
    public AstExpList args;       // null if no arguments

    /** Set during SemantMe: class name for virtual dispatch (non-null when receiver != null or implicit self). */
    private String resolvedClassName = null;
    /** True when the call is dispatched virtually (through vtable). */
    private boolean isVirtual = false;
    /** True when the function return type is void. */
    private boolean isVoid = false;

    /******************/
    /* CONSTRUCTOR(S) */
    /******************/
    public AstCallExp(AstVar receiver, String methodName, AstExpList args)
    {
        /******************************/
        /* SET A UNIQUE SERIAL NUMBER */
        /******************************/
        serialNumber = AstNodeSerialNumber.getFresh();

        /***************************************/
        /* PRINT CORRESPONDING DERIVATION RULE */
        /***************************************/
        if (receiver == null && args == null)
            System.out.print("====================== callExp -> ID LPAREN RPAREN\n");
        else if (receiver == null)
            System.out.print("====================== callExp -> ID LPAREN expList RPAREN\n");
        else if (args == null)
            System.out.print("====================== callExp -> var DOT ID LPAREN RPAREN\n");
        else
            System.out.print("====================== callExp -> var DOT ID LPAREN expList RPAREN\n");

        /*******************************/
        /* COPY INPUT DATA MEMBERS ... */
        /*******************************/
        this.receiver = receiver;
        this.methodName = methodName;
        this.args = args;
    }

    /*****************************************************/
    /* The printing message for a method-call AST node    */
    /*****************************************************/
    public void printMe()
    {
        /*************************************/
        /* AST NODE TYPE = AST EXP METHOD     */
        /*************************************/
        System.out.format("AST NODE METHOD CALL (%s)\n", methodName);

        /*****************************/
        /* RECURSIVELY PRINT KIDS   */
        /*****************************/
        if (receiver != null) receiver.printMe();
        if (args != null) args.printMe();

        /*********************************/
        /* Print to AST GRAPHVIZ DOT file */
        /*********************************/
        AstGraphviz.getInstance().logNode(
                serialNumber,
                String.format("METHOD\nCALL(%s)", methodName));

        /****************************************/
        /* PRINT Edges to AST GRAPHVIZ DOT file */
        /****************************************/
        if (receiver != null)
            AstGraphviz.getInstance().logEdge(serialNumber, receiver.serialNumber);

        if (args != null)
            AstGraphviz.getInstance().logEdge(serialNumber, args.serialNumber);
    }
    @Override
    public Type SemantMe() {
        types.TypeFunction funcType = null;

        // 1. Find the Function
        if (receiver == null) {
            // Regular function call: foo()
            // First check if we're inside a class and it's a method of the current class
            symboltable.SymbolTable tbl = symboltable.SymbolTable.getInstance();
            if (tbl.currentClass != null) {
                funcType = tbl.currentClass.lookupMethod(methodName);
                if (funcType != null) {
                    // Implicit self call — virtual dispatch
                    this.isVirtual = true;
                    this.resolvedClassName = tbl.currentClass.name;
                }
            }
            
            // If not found in class, look in symbol table for global functions
            if (funcType == null) {
                Type t = tbl.find(methodName);
                if (t == null || !(t instanceof types.TypeFunction)) {
                    System.out.format(">> ERROR: Function %s not found\n", methodName);
                    error();
                }
                funcType = (types.TypeFunction) t;
            }
        } else {
            // Method call: obj.foo()
            Type recvType = receiver.SemantMe();
            if (!(recvType instanceof types.TypeClass)) {
                System.out.println(">> ERROR: Method call on non-class type");
                error();
            }
            types.TypeClass tc = (types.TypeClass) recvType;
            funcType = tc.lookupMethod(methodName);
            if (funcType == null) {
                System.out.format(">> ERROR: Method %s not found in class %s\n", methodName, tc.name);
                error();
            }
            this.isVirtual = true;
            this.resolvedClassName = tc.name;
        }

        // 2. Validate Arguments
        types.TypeList paramList = funcType.params;
        
        types.TypeList argList = null;
        if (args != null) {
            argList = (types.TypeList) args.SemantMe(); 
        }

        types.TypeList pNode = paramList;
        types.TypeList aNode = argList;

        while (pNode != null && aNode != null) {
            if (!ast.Helpers.HelperFunctions.canAssign(pNode.head, aNode.head)) {
                System.out.println(">> ERROR: Argument type mismatch");
                error();
            }
            pNode = pNode.tail;
            aNode = aNode.tail;
        }

        // 3. Check Argument Count
        if (pNode != null || aNode != null) {
            System.out.println(">> ERROR: Argument count mismatch");
            error();
        }

        this.isVoid = (funcType.returnType instanceof types.TypeVoid);
        return funcType.returnType;
    }

    public temp.Temp irMe()
    {
        // Build argument list (evaluated left-to-right per L spec)
        ir.TempList argList = null;
        if (args != null) {
            argList = args.irMe();
        }

        // Built-in: PrintInt
        if ("PrintInt".equals(methodName) && argList != null && argList.head != null) {
            ir.Ir.getInstance().AddIrCommand(new ir.IrCommandPrintInt(argList.head));
            return null;
        }
        // Built-in: PrintString
        if ("PrintString".equals(methodName) && argList != null && argList.head != null) {
            ir.Ir.getInstance().AddIrCommand(new ir.IrCommandPrintString(argList.head));
            return null;
        }

        temp.Temp result = isVoid ? null : temp.TempFactory.getInstance().getFreshTemp();

        if (isVirtual) {
            // Virtual method call: dispatch through vtable
            temp.Temp selfTemp;
            if (receiver != null) {
                // Explicit receiver: obj.method()
                selfTemp = receiver.irMe();
            } else {
                // Implicit self call from inside a method body
                selfTemp = temp.TempFactory.getInstance().getFreshTemp();
                ir.Ir.getInstance().AddIrCommand(new ir.IrCommandLoad(selfTemp, "__self"));
            }
            ir.Ir.getInstance().AddIrCommand(
                new ir.IrCommandVirtualCall(result, selfTemp, resolvedClassName, methodName, argList));
        } else {
            // Static function call
            ir.Ir.getInstance().AddIrCommand(new ir.IrCommandCall(result, methodName, argList));
        }

        return result;
    }
}