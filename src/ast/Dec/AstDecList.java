package ast.Dec;

import ast.AstGraphviz;
import ast.AstNode;
import ast.AstNodeSerialNumber;
import ast.Helpers.AstList;
import types.*;

public class AstDecList extends AstList
{
    /****************/
    /* DATA MEMBERS */
    /****************/
    public AstDec head;
    public AstDecList tail;

    /******************/
    /* CONSTRUCTOR(S) */
    /******************/
    public AstDecList(AstDec head, AstDecList tail)
    {
        /******************************/
        /* SET A UNIQUE SERIAL NUMBER */
        /******************************/
        serialNumber = AstNodeSerialNumber.getFresh();

        /***************************************/
        /* PRINT CORRESPONDING DERIVATION RULE */
        /***************************************/
        if (tail != null) System.out.print("====================== decList -> dec decList\n");
        if (tail == null) System.out.print("====================== decList -> dec\n");

        /*******************************/
        /* COPY INPUT DATA MEMBERS ... */
        /*******************************/
        this.head = head;
        this.tail = tail;

    }

    /******************************************************/
    /* The printing message for a declaration list AST node */
    /******************************************************/
    public void printMe()
    {
        /**************************************/
        /* AST NODE TYPE = AST DEC LIST */
        /**************************************/
        System.out.print("AST NODE DEC LIST\n");

        /*************************************/
        /* RECURSIVELY PRINT HEAD + TAIL ... */
        /*************************************/
        if (head != null) head.printMe();
        if (tail != null) tail.printMe();

        /**********************************/
        /* PRINT to AST GRAPHVIZ DOT file */
        /**********************************/
        AstGraphviz.getInstance().logNode(
                serialNumber,
                "DEC\nLIST\n");

        /****************************************/
        /* PRINT Edges to AST GRAPHVIZ DOT file */
        /****************************************/
        if (head != null) AstGraphviz.getInstance().logEdge(serialNumber, head.serialNumber);
        if (tail != null) AstGraphviz.getInstance().logEdge(serialNumber, tail.serialNumber);
    }
    
    @Override
    public AstNode getHead() { return head; }

    @Override
    public AstList getTail() { return tail; }
    
    // use semantMe of AstList
    @Override
    public TypeList SemantMe() {
        System.out.println("### SEMANT GOING (DEC LIST) ###");
        System.out.println("DEBUG DecList: head is " + (head == null ? "null" : head.getClass().getName()));
        if (head != null) {
            System.out.println("DEBUG DecList: About to call head.SemantMe()");
            head.SemantMe();   // Perform semantic checks for the head declaration
            System.out.println("DEBUG DecList: Returned from head.SemantMe()");
        }

        if (tail != null) {
            tail.SemantMe();   // Perform semantic checks for the rest of the list
        }

        return null;  // dec lists have no type
    }

    public temp.Temp irMe() {
        // Generate IR for all declarations in order
        if (head != null) head.irMe();
        if (tail != null) tail.irMe();
        return null;
    }

    /** Pass 1: emit IR only for global variable declarations (allocate + init). */
    public void irMeVarDecs() {
        if (head instanceof AstVarDec) head.irMe();
        if (tail != null) tail.irMeVarDecs();
    }

    /** Pass 2: emit IR only for function declaration bodies (and class methods). */
    public void irMeFuncDecs() {
        if (head instanceof AstFuncDec || head instanceof AstClassDec) head.irMe();
        if (tail != null) tail.irMeFuncDecs();
    }
}