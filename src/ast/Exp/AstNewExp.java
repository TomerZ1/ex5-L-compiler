package ast.Exp;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import ast.AstType;
import types.TypeArray;
import types.TypeClass;
import types.TypeVoid;
import types.Type;

public class AstNewExp extends AstExp {

    public AstType type;
    public AstExp sizeExp;   // null if not array allocation

    /******************/
    /* CONSTRUCTOR(S) */
    /******************/
    public AstNewExp(AstType type, AstExp sizeExp)
    {
        /******************************/
        /* SET A UNIQUE SERIAL NUMBER */
        /******************************/
        serialNumber = AstNodeSerialNumber.getFresh();

        /***************************************/
        /* PRINT CORRESPONDING DERIVATION RULE */
        /***************************************/
        if (sizeExp == null)
            System.out.print("====================== newExp -> NEW type\n");
        else
            System.out.print("====================== newExp -> NEW type [exp]\n");

        /*******************************/
        /* COPY INPUT DATA MEMBERS ... */
        /*******************************/
        this.type = type;
        this.sizeExp = sizeExp;
    }

    /**********************************************/
    /* The printing message for a new exp AST node */
    /**********************************************/
    public void printMe()
    {
        /*************************************/
        /* AST NODE TYPE = AST NEW EXP       */
        /*************************************/
        System.out.print("AST NODE NEW EXP\n");

        /*****************************/
        /* RECURSIVELY PRINT KIDS   */
        /*****************************/
        if (type != null) type.printMe();
        if (sizeExp != null) sizeExp.printMe();

        /*********************************/
        /* Print to AST GRAPHVIZ DOT file */
        /*********************************/
        AstGraphviz.getInstance().logNode(
                serialNumber,
                "NEW\nEXP");

        /****************************************/
        /* PRINT Edges to AST GRAPHVIZ DOT file */
        /****************************************/
        if (type != null)
            AstGraphviz.getInstance().logEdge(serialNumber, type.serialNumber);
        if (sizeExp != null)
            AstGraphviz.getInstance().logEdge(serialNumber, sizeExp.serialNumber);
    }
    @Override
    public Type SemantMe() {
        // 1. Check the type being created exists (e.g., "int", "Person")
        Type t = type.SemantMe();
        if (t == null || t instanceof TypeVoid) {
            System.out.println(">> ERROR: Cannot allocate void or undefined type");
            error();
        }

        // CASE 1: Array Allocation (new int[size])
        if (sizeExp != null) {
            // Check size expression is int
            Type sizeType = sizeExp.SemantMe();
            if (!sizeType.isInt()) {
                System.out.println(">> ERROR: Array size must be int");
                error();
            }

            // Optional: If size is a constant literal, check it > 0
            if (sizeExp instanceof AstExpInt) {
                if (((AstExpInt) sizeExp).value <= 0) {
                     System.out.println(">> ERROR: Array size must be positive");
                     error();
                }
            }

            // Return the array type (e.g., if new int[], return type is int[])
            // Note: In L semantics (section 2.3), you usually allocate an array 
            // defined by a specific named type (e.g. IntArray). 
            // However, the syntax `new int[5]` suggests we return an array of ints.
            // If your language requires named array types, you might need to look that up.
            // Based on standard L behavior: assigning `new int[5]` to `array IntArr = int[]` works.
            return new TypeArray(null, t); 
        } 
        
        // CASE 2: Class Allocation (new Person)
        else {
            if (!(t instanceof TypeClass)) {
                System.out.println(">> ERROR: 'new' without size valid only for Classes");
                error();
            }
            return t;
        }
    }

    public temp.Temp irMe()
    {
        temp.Temp dst = temp.TempFactory.getInstance().getFreshTemp();
        
        if (sizeExp != null) {
            // Array allocation: new Type[size]
            temp.Temp sizeTemp = sizeExp.irMe();
            ir.Ir.getInstance().AddIrCommand(new ir.IrCommandNewArray(dst, sizeTemp, type.typeName));
        } else {
            // Object allocation: new ClassName
            ir.Ir.getInstance().AddIrCommand(new ir.IrCommandNewClass(dst, type.typeName));
        }
        
        return dst;
    }
}