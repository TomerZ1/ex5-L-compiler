package ast.Dec;

import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import ast.AstType;
import ast.AstTypeList;
import ast.Helpers.HelperFunctions;
import ast.Stmt.AstStmtList;
import symboltable.SymbolTable;
import types.*;
import temp.*;
import ir.*;
import java.util.ArrayList;
import java.util.List;

public class AstFuncDec extends AstDec {
    private AstType returnType;
    private String name;
    private AstTypeList typeList;
    private AstStmtList stmtList;
    /** Scoped IR names of parameters (e.g. ["x_1","y_1"]) — set during SemantMe. */
    private List<String> paramIrNames = new ArrayList<>();

    public AstFuncDec(AstType returnType, String name, AstTypeList typeList, AstStmtList stmtList)
    {
        serialNumber = AstNodeSerialNumber.getFresh();

        if (typeList != null)
            System.out.print("====================== funcDec -> type ID LPAREN typeList RPAREN LBRACE stmtList RBRACE\n");
        else
            System.out.print("====================== funcDec -> type ID LPAREN RPAREN LBRACE stmtList RBRACE\n");

        this.returnType = returnType;
        this.name = name;
        this.typeList = typeList;
        this.stmtList = stmtList;
    }

    public void printMe()
    {
        /*************************************/
        /* AST NODE TYPE = AST FUNC DEC      */
        /*************************************/
        System.out.print("AST NODE FUNC DEC\n");

        /*****************************/
        /* RECURSIVELY PRINT KIDS   */
        /*****************************/
        if (returnType != null) returnType.printMe();
        if (typeList != null) typeList.printMe();
        if (stmtList != null) stmtList.printMe();

        /*********************************/
        /* Print to AST GRAPHVIZ DOT file */
        /*********************************/
        AstGraphviz.getInstance().logNode(
                serialNumber,
                String.format("FUNC DEC(%s)", name));

        /****************************************/
        /* PRINT Edges to AST GRAPHVIZ DOT file */
        /****************************************/
        if (returnType  != null) AstGraphviz.getInstance().logEdge(serialNumber, returnType.serialNumber);
        if (typeList    != null) AstGraphviz.getInstance().logEdge(serialNumber, typeList.serialNumber);
        if (stmtList    != null) AstGraphviz.getInstance().logEdge(serialNumber, stmtList.serialNumber);
    }
    @Override
    public Type SemantMe() {
        SymbolTable tbl = SymbolTable.getInstance();

        // 1. Resolve Return Type
        Type retType = returnType.SemantMe();
        if (retType == null) error();

        // 2. Check for Shadowing (Function name must be unique)
        if (HelperFunctions.existsInCurrentScope(name)) {
            HelperFunctions.printErrorAndExit(returnType.lineNumber);
        }

        // 3. Validate Parameter Types and Build the signature list
        TypeList params = null;
        if (typeList != null) {
            // First, validate all parameter types before building TypeList
            AstTypeList it = typeList;
            while (it != null) {
                Type paramType = it.type.SemantMe();
                
                // Validate parameter type (cannot be void)
                if (paramType instanceof TypeVoid) {
                    HelperFunctions.printErrorAndExit(it.type.lineNumber);
                }
                
                it = it.tail;
            }
            
            // Now build the TypeList (all params are valid)
            params = (TypeList) typeList.SemantMe();
        }

        // 4. Create the Function Type Wrapper
        TypeFunction funcType = new TypeFunction(retType, name, params);
        funcType.lineNumber = returnType.lineNumber;  // Use return type's line number (same as function declaration)

        // 5. Enter Function into Symbol Table (Before body, allowing recursion)
        tbl.enter(name, funcType);

        // 5.5. If we're inside a class, also add to class's methods HashMap immediately
        // This allows methods to call other methods (including themselves) before all methods are processed
        if (tbl.currentClass != null) {
            tbl.currentClass.methods.put(name, funcType);
        }
        
        // 6. Begin Scope for Function Body
        tbl.beginScope();

        // 7. Register Parameters in the New Scope
        AstTypeList it = typeList;
        while (it != null) {
            Type paramType = it.type.SemantMe();
            
            // Check for duplicate parameter names
            if (HelperFunctions.existsInCurrentScope(it.name)) {
                this.lineNumber = it.type.lineNumber;
                error();
            }

            // Enter param into the local function scope
            tbl.enter(it.name, paramType);
            // Record scoped IR name for the irMe() pass
            paramIrNames.add(it.name + "_" + tbl.getScopeIndex());
            
            it = it.tail;
        }

        // Store the expected return type in the SymbolTable so return statements can check it
        Type prevReturnType = tbl.currentFunctionReturnType;
        tbl.currentFunctionReturnType = retType;

        // 8. Process Function Body
        if (stmtList != null) {
            stmtList.SemantMe();
        }

        // 9. End Scope
        tbl.endScope();

        // Restore previous function return-type context
        tbl.currentFunctionReturnType = prevReturnType;

        // 10. RETURN THE WRAPPER
        return funcType;
    }

    public Temp irMe() {
        SymbolTable tbl = SymbolTable.getInstance();
        Ir ir = Ir.getInstance();

        // Determine the label:
        //   - methods inside a class: "ClassName_methodName"
        //   - top-level functions  : "func_methodName" (except main)
        String funcLabel = (tbl.currentClass != null)
            ? tbl.currentClass.name + "_" + name
            : ("main".equals(name) ? "main" : "func_" + name);

        // Emit function-entry label (isFunctionEntry=true lets Main.java split IR by function)
        ir.AddIrCommand(new IrCommandLabel(funcLabel, true));

        // Tell Ir which function we're building so registerParam() stores under the right key.
        ir.setCurrentFuncLabel(funcLabel);

        // Register parameters so IrCommandLoad.mipsMe() knows they come from the caller's stack.
        // For methods: self occupies slot 0 (fp+8), explicit params start at slot 1.
        // For functions: explicit params start at slot 0.
        int startSlot = (tbl.currentClass != null) ? 1 : 0;
        for (int i = 0; i < paramIrNames.size(); i++) {
            ir.registerParam(paramIrNames.get(i), startSlot + i);
        }
        // For methods: register the implicit "__self" param at slot 0
        if (tbl.currentClass != null) {
            ir.registerParam("__self", 0);
        }

        // Emit function body IR
        if (stmtList != null) stmtList.irMe();

        // Fallthrough return behavior: for non-void functions, return 0 if control reaches end.
        if (!"void".equals(returnType.typeName)) {
            temp.Temp zeroTemp = temp.TempFactory.getInstance().getFreshTemp();
            ir.AddIrCommand(new ir.IRcommandConstInt(zeroTemp, 0));
            ir.AddIrCommand(new ir.IrCommandReturn(zeroTemp));
        }

        return null;
    }
}