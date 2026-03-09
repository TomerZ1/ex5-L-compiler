package ast.Dec;

import ast.AstCFieldList;
import ast.AstGraphviz;
import ast.AstNodeSerialNumber;
import ast.Helpers.HelperFunctions;
import symboltable.SymbolTable;
import types.*;
import ir.Ir;

public class AstClassDec extends AstDec {
    private String name;
    private String extends_name;
    private AstCFieldList dataMemberList;
    private int classLineNumber;

    public AstClassDec(String name, String extends_name, AstCFieldList cFieldList, int lineNum) 
    {
        serialNumber = AstNodeSerialNumber.getFresh();
        this.classLineNumber = lineNum;  // Store the CLASS keyword line number

        if (extends_name != null)
            System.out.print("====================== classDec -> CLASS ID EXTENDS ID LBRACE cFieldList RBRACE\n");
        else
            System.out.print("====================== classDec -> CLASS ID LBRACE cFieldList RBRACE\n");

        this.name = name;
        this.extends_name = extends_name;
        this.dataMemberList = cFieldList;
    }

    public void printMe()
    {
        if (extends_name != null)
            System.out.format("AST NODE CLASS DEC %s EXTENDS %s\n", name, extends_name);
        else
            System.out.format("AST NODE CLASS DEC %s\n", name);

        if (dataMemberList != null) dataMemberList.printMe();

        AstGraphviz.getInstance().logNode(
                serialNumber,
                String.format("CLASS DEC(%s)", name));

        if (dataMemberList != null)
            AstGraphviz.getInstance().logEdge(serialNumber, dataMemberList.serialNumber);
    }

    @Override
    public Type SemantMe() {
        SymbolTable tbl = SymbolTable.getInstance();

        // 1. Check if class name is already taken
        if (HelperFunctions.existsInCurrentScope(name)) {
            error();
        }

        // 2. Handle Inheritance (Father Class)
        TypeClass fatherType = null;
        if (extends_name != null) {
            Type t = tbl.find(extends_name);
            // Ensure the father exists and is actually a class
            if (t == null || !(t instanceof TypeClass)) {
                HelperFunctions.printErrorAndExit(classLineNumber);
            }
            fatherType = (TypeClass) t;
        }

        // 3. Create Class Shell
        TypeClass myClassType = new TypeClass(fatherType, name, null);

        // 4. Enter Shell into Symbol Table
        tbl.enter(name, myClassType);

        // 5. Begin Scope for Class Members
        tbl.beginScope();

        // 5.5. Set current class context for field lookups in methods
        TypeClass previousClass = tbl.currentClass;
        tbl.currentClass = myClassType;

        // 6. Process Members
        TypeList members = null;
        if (dataMemberList != null) {
            members = (TypeList) dataMemberList.SemantMe();
        }

        // 7. BACKFILL: Update TypeClass
        myClassType.dataMembers = members;

        // 8. Organize Members
        myClassType.splitMembers();

        // Register the class type in the Ir registry (needed by MipsGenerator for vtable/field offsets)
        Ir.getInstance().registerClass(name, myClassType);

        // 9. CHECK INHERITANCE RULES (Shadowing/Overriding)
        if (fatherType != null) {
            checkInheritance(myClassType, fatherType);
        }

        // 9.5. Restore previous class context
        tbl.currentClass = previousClass;

        // 10. End Scope
        tbl.endScope();

        return null;
    }

    private void checkInheritance(TypeClass myClass, TypeClass father) {
        
        // A. Check Fields (Variable Shadowing is ILLEGAL)
        for (String fieldName : myClass.fields.keySet()) {
            // Check if father has a field with this name
            if (father.lookupField(fieldName) != null) {
                System.out.format(">> ERROR: Field '%s' shadows a field in the superclass\n", fieldName);
                // Get the field's type which should be TypeClassVarDec with line number
                Type fieldType = myClass.fields.get(fieldName);
                int errorLine = this.lineNumber;  // default to class line
                
                // Find the actual field declaration in dataMembers to get its line number
                TypeList it = myClass.dataMembers;
                while (it != null) {
                    if (it.head instanceof TypeClassVarDec) {
                        TypeClassVarDec varDec = (TypeClassVarDec) it.head;
                        if (varDec.name.equals(fieldName)) {
                            errorLine = varDec.lineNumber;
                            break;
                        }
                    }
                    it = it.tail;
                }
                HelperFunctions.printErrorAndExit(errorLine);
            }
            // Check if father has a method with this name
            if (father.lookupMethod(fieldName) != null) {
                System.out.format(">> ERROR: Field '%s' shadows a method in the superclass\n", fieldName);
                // Get the field's line number
                int errorLine = this.lineNumber;  // default to class line
                
                // Find the actual field declaration in dataMembers to get its line number
                TypeList it = myClass.dataMembers;
                while (it != null) {
                    if (it.head instanceof TypeClassVarDec) {
                        TypeClassVarDec varDec = (TypeClassVarDec) it.head;
                        if (varDec.name.equals(fieldName)) {
                            errorLine = varDec.lineNumber;
                            break;
                        }
                    }
                    it = it.tail;
                }
                HelperFunctions.printErrorAndExit(errorLine);
            }
        }

        // B. Check Methods (Overloading ILLEGAL, Overriding MUST MATCH SIGNATURE)
        for (String methodName : myClass.methods.keySet()) {
            TypeFunction myMethod = myClass.methods.get(methodName);

            // 1. Check if it shadows a FIELD in father
            if (father.lookupField(methodName) != null) {
                System.out.format(">> ERROR: Method '%s' shadows a field in the superclass\n", methodName);
                HelperFunctions.printErrorAndExit(myMethod.lineNumber);
            }

            // 2. Check Overriding
            TypeFunction fatherMethod = father.lookupMethod(methodName);
            if (fatherMethod != null) {
                // Return type must match
                if (myMethod.returnType != fatherMethod.returnType) {
                    System.out.format(">> ERROR: Method '%s' override has different return type\n", methodName);
                    ast.Helpers.HelperFunctions.printErrorAndExit(myMethod.lineNumber);
                }

                // Arguments must match exactly
                TypeList myParams = myMethod.params;
                TypeList fatherParams = fatherMethod.params;

                while (myParams != null && fatherParams != null) {
                    if (myParams.head != fatherParams.head) {
                        System.out.format(">> ERROR: Method '%s' override has different parameter types\n", methodName);
                        ast.Helpers.HelperFunctions.printErrorAndExit(myMethod.lineNumber);
                    }
                    myParams = myParams.tail;
                    fatherParams = fatherParams.tail;
                }

                // Check argument count mismatch
                if (myParams != null || fatherParams != null) {
                    System.out.format(">> ERROR: Method '%s' override has different number of parameters\n", methodName);
                    ast.Helpers.HelperFunctions.printErrorAndExit(myMethod.lineNumber);
                }
            }
        }
    }

    public temp.Temp irMe()
    {
        SymbolTable tbl = SymbolTable.getInstance();
        TypeClass myClass = (TypeClass) tbl.find(name);

        // Set current class context so AstFuncDec.irMe() emits "ClassName_method" labels
        TypeClass previousClass = tbl.currentClass;
        tbl.currentClass = myClass;

        // Emit IR for all method bodies; collect field defaults for VarDec fields
        if (dataMemberList != null) {
            ast.AstCFieldList it = dataMemberList;
            while (it != null) {
                if (it.head.dec instanceof AstFuncDec) {
                    ((AstFuncDec) it.head.dec).irMe();
                } else if (it.head.dec instanceof AstVarDec) {
                    AstVarDec vd = (AstVarDec) it.head.dec;
                    if (vd.exp instanceof ast.Exp.AstExpInt) {
                        myClass.fieldDefaults.put(vd.name, ((ast.Exp.AstExpInt) vd.exp).value);
                    }
                }
                it = it.tail;
            }
        }

        tbl.currentClass = previousClass;
        return null;
    }
}