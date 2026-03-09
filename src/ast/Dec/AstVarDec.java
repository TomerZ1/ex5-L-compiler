package ast.Dec;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import ast.AstType;
import ast.Exp.AstExp;
import ast.Exp.AstNewExp;
import ast.Helpers.HelperFunctions;
import symboltable.SymbolTable;
import types.Type;
import types.TypeClassVarDec;
import types.TypeVoid;
import temp.*;
import ir.*;

public class AstVarDec extends AstDec {
    private AstType typeNode; // int, string, void, A, B, ... 
    String name; // string name of the var
    AstExp exp; // optional initialization expression
    private AstNewExp newExp; // optional new expression for class types
    private String irVarName = null; // scope-qualified IR name, e.g. "x_0"

    public AstVarDec(AstType typeNode, String name, AstExp exp, AstNewExp newExp)
    {
		/******************************/
		/* SET A UNIQUE SERIAL NUMBER */
		/******************************/
		serialNumber = AstNodeSerialNumber.getFresh();

		/***************************************/
		/* PRINT CORRESPONDING DERIVATION RULE */
		/***************************************/
        if (exp != null)
            System.out.print("====================== varDec -> type ID ASSIGN exp SEMICOLON\n");
        else if (newExp != null)
            System.out.print("====================== varDec -> type ID ASSIGN newExp SEMICOLON\n");
        else
		    System.out.print("====================== varDec -> type ID SEMICOLON\n");

		/*******************************/
		/* COPY INPUT DATA MEMBERS ... */
		/*******************************/
		this.typeNode = typeNode;
        this.name = name;
        this.exp = exp;
        this.newExp = newExp;
	}

    /*************************************************/
    /* The printing message for a var dec AST node   */
    /*************************************************/
    public void printMe()
    {
        /*************************************/
        /* AST NODE TYPE = AST VAR DEC       */
        /*************************************/
        System.out.print("AST NODE VAR DEC\n");

        /*****************************/
        /* RECURSIVELY PRINT KIDS   */
        /*****************************/
        if (typeNode != null) typeNode.printMe();
        if (exp != null) exp.printMe();
        if (newExp != null) newExp.printMe();

        /*********************************/
        /* Print to AST GRAPHVIZ DOT file */
        /*********************************/
        AstGraphviz.getInstance().logNode(
                serialNumber,
                String.format("VAR DEC(%s)", name));

        /****************************************/
        /* PRINT Edges to AST GRAPHVIZ DOT file */
        /****************************************/
        if (typeNode   != null) AstGraphviz.getInstance().logEdge(serialNumber, typeNode.serialNumber);
        if (exp    != null) AstGraphviz.getInstance().logEdge(serialNumber, exp.serialNumber);
        if (newExp != null) AstGraphviz.getInstance().logEdge(serialNumber, newExp.serialNumber);
    }
    
    @Override
    public Type SemantMe() {
        System.out.println("### SEMANT GOING (VAR DEC) ###");
        // 1. Resolve the declared type (e.g., "int", "string", "Person")
        Type varType = typeNode.SemantMe();

        // 2. Validate Type: Must exist and cannot be void
        if (varType == null || varType instanceof TypeVoid) {
            error();
        }

        // 3. Shadowing Check: Name must be unique in the current scope
        if (HelperFunctions.existsInCurrentScope(name)) {
            error();
        }

        // 4. Check Initialization Expression (if exists: int x := 5;)
        if (exp != null) {
            Type expType = exp.SemantMe();
            if (!HelperFunctions.canAssign(varType, expType)) {
                error();
            }
        }

        // 5. Check New Expression (if exists: Person p := new Person;)
        if (newExp != null) {
            Type newType = newExp.SemantMe();
            if (!HelperFunctions.canAssign(varType, newType)) {
                error();
            }
        }

        // 6. Compute unique IR name using current scope index (before entering)
        irVarName = name + "_" + SymbolTable.getInstance().getScopeIndex();

        // 6b. Enter Variable into Symbol Table (So subsequent code can use it)
        SymbolTable.getInstance().enter(name, varType);

        // 7. RETURN THE WRAPPER
        // This allows AstClassDec to know the name of this variable later.
        TypeClassVarDec varDecType = new TypeClassVarDec(varType, name);
        varDecType.lineNumber = this.lineNumber;  // Store the line number for error reporting

                
        // 7.5. If we're inside a class, also add to class's fields HashMap immediately
        // This allows method bodies to access fields before all members are processed
        SymbolTable tbl = SymbolTable.getInstance();
        if (tbl.currentClass != null) {
            tbl.currentClass.fields.put(name, varType);
        }
        
        return varDecType; 
    }

    public Temp irMe()
    {
        // Use scope-qualified IR name to distinguish shadowed variables
        String varIrName = (irVarName != null) ? irVarName : name;

        // Register mapping from IR name back to original source name
        Ir.getInstance().registerIrName(varIrName, name);

        // Allocate space for the variable
        Ir.getInstance().AddIrCommand(new IrCommandAllocate(varIrName));

        // Register as global if it is at scope 0 (outermost / global scope)
        try {
            int scopeOfVar = Integer.parseInt(varIrName.substring(varIrName.lastIndexOf('_') + 1));
            if (scopeOfVar == 0) {
                Ir.getInstance().registerGlobal(varIrName);
            }
        } catch (NumberFormatException ignored) {}

        // If there's an initialization expression, evaluate and store it
        if (exp != null) {
            Temp expTemp = exp.irMe();
            Ir.getInstance().AddIrCommand(new IrCommandStore(varIrName, expTemp));
        }
        // If there's a new expression, evaluate and store it
        else if (newExp != null) {
            Temp newTemp = newExp.irMe();
            Ir.getInstance().AddIrCommand(new IrCommandStore(varIrName, newTemp));
        }

        return null;
    }
}
/*
varDec ::= type ID [ ASSIGN exp ] SEMICOLON
| type ID ASSIGN newExp SEMICOLON
 */