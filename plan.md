Here is the complete `PLAN.md` content — copy everything between the triple-backtick fences:

````markdown
# Ex5 — Full MIPS Code Generation: Implementation Plan

> Compiler course, TAU 0368-3133 · Due 15/3/2026  
> Read this entire document before touching any code.  
> The codebase passes all ex1–ex4 tests. This plan describes only what needs to change/be added.

---

## How to Use This Plan

The work is split into four tracks. Tracks A, B, and C can largely be done in parallel once A3 is done. Track D wires everything together and must come last.

| Track | Topic | Depends on |
|-------|-------|------------|
| A | Fix IR generation (full L language support) | nothing — start immediately |
| B | Liveness analysis + interference graph + register allocator | A3 done |
| C | Full MipsGenerator rewrite + `mipsMe` on every IR command | A3, A4 done |
| D | Wire everything in `Main.java` + Makefile + testing | A, B, C all done |

---

## Background: What Already Works

The project compiles and passes all ex1–ex4 tests. The pipeline in `Main.java` is:

1. Lex → Parse → `SemantMe()` → `irMe()` → CFG → dataflow analysis → write output

For ex5, we **replace steps 4–end** with: `irMe()` → per-function liveness → register allocation → MIPS code generation.

The existing IR commands (`IrCommandLoad`, `IrCommandStore`, `IrCommandBinopAddIntegers`, etc.) are all in `src/ir/`. Each already implements `getReadTemps()` and `getWriteTemps()` which the liveness analysis will use. We will add a `mipsMe()` method to each.

---

## Critical Facts About the Target Architecture

Memorize these before writing any code:

### Stack Frame Layout (mandatory — from tutorial)

```
High address
  arg[n-1]     fp + 8 + (n-1)*4     ← last arg pushed first
  ...
  arg[0]       fp + 8
  saved $ra    fp + 4
  saved $fp    fp + 0                ← $fp points here after prologue
  saved $t0    fp - 4
  saved $t1    fp - 8
  ...
  saved $t9    fp - 40
  local[0]     fp - 44              ← first local variable
  local[1]     fp - 48
  ...
Low address    ← $sp during function body
```

### Object Layout (mandatory — from tutorial)

```
word 0:        vtable pointer
word 1:        first field (inherited fields come first, in declaration order)
word 2:        second field
...
```

### Vtable Layout (mandatory — from tutorial)

- Inherited methods come first, in the parent's vtable order.
- If the child overrides a method, its label replaces the parent's entry at the same index.
- New methods added by the child are appended at the end.

Example:
```
class A: methods m1, m2         → vt_A: [A_m1, A_m2]
class B extends A: overrides m2, adds h → vt_B: [A_m1, B_m2, B_h]
```

### Array Layout (mandatory — from tutorial)

```
word 0:        length (number of elements)
word 1:        element[0]
word 2:        element[1]
...
```

### Key Rules Summary

| Topic | Rule |
|-------|------|
| Method label | `ClassName_methodName` (e.g. `Person_getAge`) |
| Static call | `jal funcLabel` |
| Virtual call | load vtable ptr from obj[0]; load method ptr from vtable[idx*4]; `jalr $s1` |
| Self arg in virtual call | pushed last (sits at fp+8 in callee) |
| Explicit args | pushed right-to-left before self |
| Integer saturation | after every `+`, `-`, `*`, `/`: clamp result to [-32768, 32767] |
| `main` function | L's `main` compiles to label `user_main`; MIPS `main:` stub calls `jal user_main` |
| Reg alloc failure | print `Register Allocation Failed` to output file, `System.exit(1)` |
| Scratch registers | `$s0–$s9` are free to use inside `mipsMe` implementations |
| `$t0–$t9` | the only registers to allocate (K=10) |

---

## Track A — Fix IR Generation

### A1. Fix `TypeClass` field ordering

**File:** `src/types/TypeClass.java`

Change both maps to `LinkedHashMap` to preserve declaration order (critical for deterministic object layout and vtable):

```java
import java.util.LinkedHashMap;

public LinkedHashMap<String, Type> fields = new LinkedHashMap<>();
public LinkedHashMap<String, TypeFunction> methods = new LinkedHashMap<>();
```

Add these helper methods:

```java
/**
 * Returns the 0-based field index in the full object layout.
 * Inherited fields come first (father.totalFieldCount() slots), then own fields.
 * NOTE: index i means the field lives at byte offset (i+1)*4 in the object
 * because word 0 is the vtable pointer.
 */
public int getFieldIndex(String fieldName) {
    if (father != null) {
        int fatherResult = father.getFieldIndex(fieldName);
        if (fatherResult >= 0) return fatherResult;
    }
    int base = (father != null) ? father.totalFieldCount() : 0;
    int i = 0;
    for (String key : fields.keySet()) {
        if (key.equals(fieldName)) return base + i;
        i++;
    }
    return -1;
}

/** Total number of data fields (own + inherited), NOT counting the vtable pointer. */
public int totalFieldCount() {
    int parentCount = (father != null) ? father.totalFieldCount() : 0;
    return parentCount + fields.size();
}
```

### A2. Extend `Ir` singleton with three registries

**File:** `src/ir/Ir.java`

Add these fields and methods (add imports as needed):

```java
// --- Registry 1: function parameters ---
// Maps scoped IR name (e.g. "x_1") to its 0-based argument slot index in the caller frame.
// Slot 0 is "self" for methods; slot 0 is the first param for regular functions.
private Map<String, Integer> paramIndex = new HashMap<>();

public void registerParam(String irName, int index) { paramIndex.put(irName, index); }
public boolean isParam(String irName) { return paramIndex.containsKey(irName); }
public int getParamIndex(String irName) { return paramIndex.get(irName); }

// --- Registry 2: global variables ---
// Set of scoped IR names that are global (allocated in .data, not on stack).
private Set<String> globals = new HashSet<>();

public void registerGlobal(String irName) { globals.add(irName); }
public boolean isGlobal(String irName) { return globals.contains(irName); }

// --- Registry 3: class type information ---
// Needed by MipsGenerator to compute vtable indices and field offsets at code gen time.
private Map<String, types.TypeClass> classRegistry = new HashMap<>();

public void registerClass(String name, types.TypeClass tc) { classRegistry.put(name, tc); }
public types.TypeClass lookupClass(String name) { return classRegistry.get(name); }
```

### A3. Add `isFunctionEntry` flag to `IrCommandLabel`

**File:** `src/ir/IrCommandLabel.java`

Add a field and a second constructor:

```java
public boolean isFunctionEntry = false;

// New constructor — used by AstFuncDec.irMe() to mark function entry points
public IrCommandLabel(String labelName, boolean isFunctionEntry) {
    this.labelName = labelName;
    this.isFunctionEntry = isFunctionEntry;
}
```

Keep the existing one-arg constructor untouched (it leaves `isFunctionEntry = false`).

### A4. Create new IR command classes

Each of these is a new file in `src/ir/`. Model them on the existing IR command files (same package, same `getReadTemps()`/`getWriteTemps()` pattern).

#### A4a. `IrCommandVirtualCall.java` (most important new command)

```java
package ir;
import java.util.*;
import temp.*;

public class IrCommandVirtualCall extends IrCommand {
    public Temp dst;          // result temp (null for void)
    public Temp receiver;     // the object whose vtable is used
    public String className;  // static type of receiver (for vtable index lookup)
    public String methodName;
    public TempList args;     // explicit args (does NOT include receiver)

    public IrCommandVirtualCall(Temp dst, Temp receiver, String className,
                                 String methodName, TempList args) {
        this.dst = dst;
        this.receiver = receiver;
        this.className = className;
        this.methodName = methodName;
        this.args = args;
    }

    @Override
    public String toString() {
        return (dst != null ? "Temp_" + dst.getSerialNumber() + " := " : "")
            + "virtual_call Temp_" + receiver.getSerialNumber()
            + "." + methodName + "(...)";
    }

    public Set<String> getReadTemps() {
        Set<String> r = new HashSet<>();
        r.add("Temp_" + receiver.getSerialNumber());
        TempList cur = args;
        while (cur != null) { r.add("Temp_" + cur.head.getSerialNumber()); cur = cur.tail; }
        return r;
    }

    public Set<String> getWriteTemps() {
        Set<String> r = new HashSet<>();
        if (dst != null) r.add("Temp_" + dst.getSerialNumber());
        return r;
    }
}
```

#### A4b. `IrCommandPrintString.java`

Copy the structure of `IrCommandPrintInt.java` exactly. Change the class name and the `toString()` to say `PrintString`.

#### A4c. `IrCommandBinopConcatStrings.java`

Copy the structure of `IrCommandBinopAddIntegers.java`. Change class name and `toString()`.

#### A4d. `IrCommandBinopEqStrings.java`

Copy the structure of `IrCommandBinopEqIntegers.java`. Change class name and `toString()`.

#### A4e. `IrCommandBinopEqPointers.java`

Copy the structure of `IrCommandBinopEqIntegers.java`. Change class name and `toString()`. (Used for class/array/nil pointer equality — semantics are identical at the MIPS level: `seq`.)

### A5. Fix `AstFuncDec.irMe()`

**File:** `src/ast/Dec/AstFuncDec.java`

**Problem:** the current `irMe()` never emits a function-entry label and never registers parameters.

Add a field to `AstFuncDec` to store the scoped IR names of parameters (populated during `SemantMe()`):

```java
// Populated during SemantMe() so irMe() can look up scoped names
private List<String> paramIrNames = new ArrayList<>(); // e.g. ["x_1", "y_1"]
```

In `SemantMe()`, after `tbl.enter(it.name, paramType)`, append the scoped name:
```java
paramIrNames.add(it.name + "_" + tbl.getScopeIndex());
```

Then replace `irMe()` with:

```java
public Temp irMe() {
    SymbolTable tbl = SymbolTable.getInstance();
    Ir ir = Ir.getInstance();

    // Determine the label: "ClassName_methodName" for methods, raw name for top-level functions
    String funcLabel = (tbl.currentClass != null)
        ? tbl.currentClass.name + "_" + name
        : name;

    // Emit the function-entry label (isFunctionEntry = true so Main.java can split IR by function)
    ir.AddIrCommand(new IrCommandLabel(funcLabel, true));

    // Register parameters so MipsGenerator knows they come from the caller's stack frame.
    // For methods: self is slot 0, first explicit param is slot 1.
    // For functions: first param is slot 0.
    int startSlot = (tbl.currentClass != null) ? 1 : 0;
    for (int i = 0; i < paramIrNames.size(); i++) {
        ir.registerParam(paramIrNames.get(i), startSlot + i);
    }

    // Emit function body IR
    if (stmtList != null) stmtList.irMe();

    return null;
}
```

### A6. Fix `AstClassDec.irMe()`

**File:** `src/ast/Dec/AstClassDec.java`

**Problem:** the current `irMe()` returns null without emitting any IR for methods.

Also, in `SemantMe()`, after `myClassType` is fully constructed, register it:
```java
ir.Ir.getInstance().registerClass(name, myClassType);
```

Replace `irMe()` with:

```java
public temp.Temp irMe() {
    SymbolTable tbl = SymbolTable.getInstance();
    TypeClass myClass = (TypeClass) tbl.find(name);

    TypeClass previousClass = tbl.currentClass;
    tbl.currentClass = myClass;

    if (dataMemberList != null) {
        AstCFieldList it = dataMemberList;
        while (it != null) {
            if (it.head instanceof AstFuncDec) {
                ((AstFuncDec) it.head).irMe();
            }
            it = it.tail;
        }
    }

    tbl.currentClass = previousClass;
    return null;
}
```

### A7. Fix `AstDecList.irMeFuncDecs()`

**File:** `src/ast/Dec/AstDecList.java`

Change the existing method so class declarations are also visited:

```java
public void irMeFuncDecs() {
    if (head instanceof AstFuncDec || head instanceof AstClassDec) head.irMe();
    if (tail != null) tail.irMeFuncDecs();
}
```

### A8. Fix `AstVarDec.irMe()` to register globals

**File:** `src/ast/Dec/AstVarDec.java`

After `IrCommandAllocate` is emitted, check if this is a global variable (scope index 0 = outermost scope). If so, register it:

```java
// After emitting IrCommandAllocate(irVarName):
if (SymbolTable.getInstance().getScopeIndex() == 0) {
    Ir.getInstance().registerGlobal(irVarName);
}
```

### A9. Fix `AstCallExp.irMe()` and `SemantMe()`

**File:** `src/ast/AstCallExp.java`

Add a field to store the resolved class name (set during semantic analysis):

```java
private String resolvedClassName = null;
```

In `SemantMe()`, when `receiver != null`, just before `return funcType.returnType`, add:
```java
this.resolvedClassName = tc.name;
```

Replace `irMe()` with:

```java
public temp.Temp irMe() {
    // Evaluate explicit args left-to-right
    ir.TempList argList = null;
    if (args != null) argList = args.irMe();

    // Built-in: PrintInt
    if ("PrintInt".equals(methodName)) {
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandPrintInt(argList.head));
        return null;
    }
    // Built-in: PrintString
    if ("PrintString".equals(methodName)) {
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandPrintString(argList.head));
        return null;
    }

    temp.Temp result = temp.TempFactory.getInstance().getFreshTemp();

    if (receiver != null) {
        // Virtual method call — evaluate receiver, use IrCommandVirtualCall
        temp.Temp selfTemp = receiver.irMe();
        ir.Ir.getInstance().AddIrCommand(
            new ir.IrCommandVirtualCall(result, selfTemp, resolvedClassName, methodName, argList));
    } else {
        // Static function call
        ir.Ir.getInstance().AddIrCommand(
            new ir.IrCommandCall(result, methodName, argList));
    }
    return result;
}
```

### A10. Fix `AstExpBinop.irMe()` for string types

**File:** `src/ast/Exp/AstExpBinop.java`

Add a field set during `SemantMe()`:
```java
private types.Type leftType = null; // set in SemantMe() from the type of the left child
```

In `SemantMe()`, after computing the type of the left child expression, store it:
```java
this.leftType = leftChildType;
```

In `irMe()`, change the emission for **op 0 (`+`)** and **op 6 (`=`)**:

```java
// Op 0: addition or string concatenation
if (op == 0) {
    if (leftType instanceof types.TypeString)
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandBinopConcatStrings(dst, t1, t2));
    else
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandBinopAddIntegers(dst, t1, t2));
}

// Op 6: equality
if (op == 6) {
    if (leftType instanceof types.TypeString)
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandBinopEqStrings(dst, t1, t2));
    else if (leftType instanceof types.TypeInt)
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandBinopEqIntegers(dst, t1, t2));
    else
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandBinopEqPointers(dst, t1, t2));
}
```

### A11. Fix `IrCommandFieldLoad` and `IrCommandFieldStore` to carry class name

**Files:** `src/ir/IrCommandFieldLoad.java` and `src/ir/IrCommandFieldStore.java`

The MIPS generator needs to know the class name of the object to compute the field's byte offset. Add a `className` field to each:

In `IrCommandFieldLoad.java`:
```java
public String className; // static class type of the object temp
```
Update constructor: `public IrCommandFieldLoad(Temp dst, Temp object, String fieldName, String className)`

In `IrCommandFieldStore.java`:
```java
public String className;
```
Update constructor similarly.

Also update the callsites in `AstVarField.irMe()` and `AstStmtAssign.irMe()` to pass the class name (which is known from the semantic pass — it's the `TypeClass` of the receiver expression).

### A12. Implement `AstNewExp.irMe()`

**File:** `src/ast/Exp/AstNewExp.java`

This was not needed for ex4. Implement it now:

```java
public temp.Temp irMe() {
    temp.Temp dst = temp.TempFactory.getInstance().getFreshTemp();

    if (/* this is a "new ClassName" expression */) {
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandNewClass(dst, className));
    } else {
        // "new type[sizeExp]"
        temp.Temp sizeTemp = sizeExp.irMe();
        ir.Ir.getInstance().AddIrCommand(new ir.IrCommandNewArray(dst, sizeTemp, elementTypeName));
    }
    return dst;
}
```

Check what fields `AstNewExp` currently has and adjust the variable names accordingly.

---

## Track B — Liveness Analysis & Register Allocation

### B1. Create `LivenessAnalysis.java`

**New file:** `src/cfg/LivenessAnalysis.java`

Performs backward dataflow on **temporaries only** (`Temp_N` strings). Named variables are loaded/stored from memory and do not need registers.

```java
package cfg;

import ir.IrCommand;
import ir.IrCommandLabel;
import java.util.*;

public class LivenessAnalysis {
    private final List<IrCommand> commands;
    private List<Set<String>> liveOutPerInstruction; // indexed by command position

    public LivenessAnalysis(List<IrCommand> commands) {
        this.commands = commands;
    }

    public void compute() {
        // Step 1: Build basic blocks (same algorithm as Fullcfggraph.partitionIntoBlocks)
        // Step 2: For each block compute use[B] and def[B] using only Temp_* names
        //         use[B] = temps read before being written within B
        //         def[B] = temps written in B
        // Step 3: Backward worklist:
        //         live_in[B]  = use[B] ∪ (live_out[B] − def[B])
        //         live_out[B] = ∪ { live_in[S] | S is successor of B }
        //         Iterate until no change.
        // Step 4: Reconstruct per-instruction live_out by backward simulation within each block.
        //         Start from live_out[B] and work backwards through each instruction.
    }

    /** Returns the set of temps live AFTER instruction at index i. Call after compute(). */
    public Set<String> getLiveOutAt(int i) {
        return liveOutPerInstruction.get(i);
    }

    public List<IrCommand> getCommands() { return commands; }
}
```

**Notes:**
- Use `IrCommand.getReadTemps()` and `IrCommand.getWriteTemps()` for use/def.
- Filter: only include strings that start with `"Temp_"`.
- The CFG construction is identical to `Fullcfggraph` — reuse or copy `partitionIntoBlocks()` and `buildControlFlowEdges()`.

### B2. Create `InterferenceGraph.java`

**New file:** `src/cfg/InterferenceGraph.java`

```java
package cfg;

import ir.IrCommand;
import java.util.*;

public class InterferenceGraph {
    private final Map<String, Set<String>> adj = new HashMap<>();

    public void addNode(String temp) {
        adj.putIfAbsent(temp, new HashSet<>());
    }

    public void addEdge(String a, String b) {
        if (a.equals(b)) return;
        adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    public Set<String> neighbors(String node) {
        return adj.getOrDefault(node, Collections.emptySet());
    }

    public int degree(String node) { return neighbors(node).size(); }
    public Set<String> nodes() { return Collections.unmodifiableSet(adj.keySet()); }

    /**
     * Build from liveness analysis results.
     * Rule: for every instruction i that WRITES temp t,
     *       add edge (t, u) for every u in live_out[i] where u != t.
     */
    public static InterferenceGraph build(LivenessAnalysis la) {
        InterferenceGraph g = new InterferenceGraph();
        List<IrCommand> cmds = la.getCommands();
        for (int i = 0; i < cmds.size(); i++) {
            for (String def : cmds.get(i).getWriteTemps()) {
                g.addNode(def);
                for (String live : la.getLiveOutAt(i)) {
                    g.addEdge(def, live);
                }
            }
            for (String use : cmds.get(i).getReadTemps()) {
                g.addNode(use);
            }
        }
        return g;
    }
}
```

### B3. Create `RegisterAllocator.java`

**New file:** `src/regalloc/RegisterAllocator.java`

Simplification-based coloring (Chaitin-style), no spilling. K=10.

```java
package regalloc;

import cfg.InterferenceGraph;
import java.util.*;

public class RegisterAllocator {
    private static final int K = 10;
    private static final String[] REGS = {
        "$t0","$t1","$t2","$t3","$t4","$t5","$t6","$t7","$t8","$t9"
    };
    private final InterferenceGraph graph;

    public RegisterAllocator(InterferenceGraph graph) {
        this.graph = graph;
    }

    /**
     * Returns a map from "Temp_N" to "$tK".
     * If coloring is impossible, prints "Register Allocation Failed",
     * writes it to the fileWriter if available, and calls System.exit(1).
     */
    public Map<String, String> allocate() {
        if (graph.nodes().isEmpty()) return new HashMap<>();

        // --- Simplification phase ---
        Deque<String> stack = new ArrayDeque<>();
        Set<String> removed = new HashSet<>();
        Set<String> remaining = new HashSet<>(graph.nodes());

        while (!remaining.isEmpty()) {
            String candidate = null;
            for (String n : remaining) {
                // Count active (non-removed) neighbors
                long activeDeg = graph.neighbors(n).stream()
                    .filter(nb -> !removed.contains(nb)).count();
                if (activeDeg < K) { candidate = n; break; }
            }
            if (candidate == null) {
                System.out.println("Register Allocation Failed");
                System.exit(1);
            }
            stack.push(candidate);
            removed.add(candidate);
            remaining.remove(candidate);
        }

        // --- Reconstruction phase ---
        Map<String, String> coloring = new HashMap<>();
        while (!stack.isEmpty()) {
            String n = stack.pop();
            Set<String> usedColors = new HashSet<>();
            for (String nb : graph.neighbors(n)) {
                if (coloring.containsKey(nb)) usedColors.add(coloring.get(nb));
            }
            for (String reg : REGS) {
                if (!usedColors.contains(reg)) {
                    coloring.put(n, reg);
                    break;
                }
            }
            if (!coloring.containsKey(n)) {
                // Should be unreachable if simplification succeeded
                System.out.println("Register Allocation Failed");
                System.exit(1);
            }
        }
        return coloring;
    }
}
```

**Note on the "Register Allocation Failed" test case:** The exercise provides one specific test where allocation fails because a function call has more arguments than available registers (>10 live temps simultaneously). Your allocator must handle this by exiting, not throwing an exception.

---

## Track C — MIPS Generator

### C1. Rewrite `MipsGenerator.java`

**File:** `src/mips/MipsGenerator.java` — complete replacement.

#### C1a. Key design decisions

- Buffer `.data` and `.text` sections separately into `StringBuilder`s; write them to file only in `finalizeFile()`.
- Track per-function local variable slots in a `Map<String, Integer>` reset at each function start.
- Use a placeholder string for the local-area reservation size in the prologue; replace it retroactively when `endFunction()` is called and the final slot count is known.
- `$s0–$s9` are scratch registers, free to use inside any `mipsMe` without saving.
- `mg.emit(text)` appends to the text section.

#### C1b. Fields

```java
private PrintWriter out;
private StringBuilder dataSec = new StringBuilder();
private StringBuilder textSec = new StringBuilder();
public boolean inFunction = false;       // used by IrCommandAllocate.mipsMe
public String currentFunc = null;
private Map<String, Integer> localSlots = new HashMap<>();
private int nextSlot = -44;
private int localSizePlaceholderPos = -1; // char position in textSec for retroactive fill
private static int labelCounter = 0;
private static MipsGenerator instance = null;
```

#### C1c. Initialization

```java
public static void init(String outputPath) throws Exception {
    instance = new MipsGenerator();
    instance.out = new PrintWriter(outputPath);
    // Error message strings always go to .data
    instance.dataSec.append("string_access_violation: .asciiz \"Access Violation\"\n");
    instance.dataSec.append("string_illegal_div_by_0: .asciiz \"Illegal Division By Zero\"\n");
    instance.dataSec.append("string_invalid_ptr_dref: .asciiz \"Invalid Pointer Dereference\"\n");
}

public static MipsGenerator getInstance() { return instance; }
```

Remove the old no-arg `getInstance()` that opened a hardcoded file path.

#### C1d. `startFunction(String name)`

```java
public void startFunction(String name) {
    inFunction = true;
    currentFunc = name;
    localSlots = new HashMap<>();
    nextSlot = -44;

    String mipsName = name.equals("main") ? "user_main" : name;
    emit(mipsName + ":\n");
    emit("# prologue\n");
    emit("subu $sp, $sp, 4\n");
    emit("sw $ra 0($sp)\n");
    emit("subu $sp, $sp, 4\n");
    emit("sw $fp 0($sp)\n");
    emit("move $fp, $sp\n");
    for (int i = 0; i <= 9; i++) {
        emit("subu $sp, $sp, 4\n");
        emit("sw $t" + i + ", 0($sp)\n");
    }
    // Placeholder — will be replaced in endFunction() with actual local size
    emit("subu $sp, $sp, LOCALSIZE_PLACEHOLDER_" + name + "\n");
}
```

#### C1e. `endFunction(String name)`

```java
public void endFunction(String name) {
    String mipsName = name.equals("main") ? "user_main" : name;

    // Replace placeholder with actual local slot count * 4
    int localSize = (-nextSlot - 44); // number of bytes for locals
    // Simplest: use a unique placeholder string and do a replace
    String placeholder = "LOCALSIZE_PLACEHOLDER_" + name;
    int idx = textSec.indexOf(placeholder);
    if (idx >= 0) {
        textSec.replace(idx, idx + placeholder.length(), String.valueOf(localSize));
    }

    emit(mipsName + "_epilogue:\n");
    emit("move $sp, $fp\n");
    for (int i = 0; i <= 9; i++) {
        emit("lw $t" + i + ", " + (-(i+1)*4) + "($sp)\n");
    }
    emit("lw $fp, 0($sp)\n");
    emit("lw $ra, 4($sp)\n");
    emit("addu $sp, $sp, 8\n");
    emit("jr $ra\n");

    inFunction = false;
    currentFunc = null;
}
```

#### C1f. `allocLocal(String irName)` and `getLocal(String irName)`

```java
public int allocLocal(String irName) {
    if (!localSlots.containsKey(irName)) {
        localSlots.put(irName, nextSlot);
        nextSlot -= 4;
    }
    return localSlots.get(irName);
}

public int getLocal(String irName) {
    if (!localSlots.containsKey(irName)) {
        // Allocate on demand (handles cases where allocate and first use are separate)
        return allocLocal(irName);
    }
    return localSlots.get(irName);
}
```

#### C1g. Data section helpers

```java
public void addDataEntry(String text) { dataSec.append(text); }
public void emit(String text) { textSec.append(text); }
public String freshLabel(String prefix) { return prefix + "_" + (labelCounter++); }
```

#### C1h. `emitVtable(String className, List<String> methodLabels)`

```java
public void emitVtable(String className, List<String> methodLabels) {
    addDataEntry("vt_" + className + ":\n");
    for (String lbl : methodLabels) {
        addDataEntry(".word " + lbl + "\n");
    }
}
```

#### C1i. Runtime check helpers

```java
public void emitNilCheck(String reg) {
    emit("beq " + reg + ", $zero, __nil_handler\n");
}

public void emitDivByZeroCheck(String reg) {
    emit("beq " + reg + ", $zero, __div_by_zero_handler\n");
}

// From tutorial: check index negative, then check index >= array_length
public void emitBoundsCheck(String arrReg, String idxReg) {
    emit("bltz " + idxReg + ", __bounds_handler\n");
    emit("lw $s0, 0(" + arrReg + ")\n");   // load stored length from word 0
    emit("bge " + idxReg + ", $s0, __bounds_handler\n");
}
```

#### C1j. `emitSaturate(String reg)` (from tutorial)

```java
public void emitSaturate(String reg) {
    String skipHigh = freshLabel("sat_hi");
    String skipLow  = freshLabel("sat_lo");
    emit("li $s0, 32767\n");
    emit("ble " + reg + ", $s0, " + skipHigh + "\n");
    emit("move " + reg + ", $s0\n");
    emit(skipHigh + ":\n");
    emit("li $s0, -32768\n");
    emit("bge " + reg + ", $s0, " + skipLow + "\n");
    emit("move " + reg + ", $s0\n");
    emit(skipLow + ":\n");
}
```

#### C1k. `emitMipsMain()`

```java
public void emitMipsMain() {
    emit("main:\n");
    emit("jal user_main\n");
    emit("li $v0, 10\n");
    emit("syscall\n");
}
```

#### C1l. `emitRuntimeHandlers()`

```java
public void emitRuntimeHandlers() {
    // Nil dereference
    emit("__nil_handler:\n");
    emit("la $a0, string_invalid_ptr_dref\n");
    emit("li $v0, 4\n");
    emit("syscall\n");
    emit("li $v0, 10\n");
    emit("syscall\n");

    // Division by zero
    emit("__div_by_zero_handler:\n");
    emit("la $a0, string_illegal_div_by_0\n");
    emit("li $v0, 4\n");
    emit("syscall\n");
    emit("li $v0, 10\n");
    emit("syscall\n");

    // Array bounds
    emit("__bounds_handler:\n");
    emit("la $a0, string_access_violation\n");
    emit("li $v0, 4\n");
    emit("syscall\n");
    emit("li $v0, 10\n");
    emit("syscall\n");

    // String concatenation helper
    emitStrConcat();
}
```

#### C1m. `emitStrConcat()` — string concatenation runtime helper

```java
private void emitStrConcat() {
    // Input: $a0 = ptr to string 1, $a1 = ptr to string 2
    // Output: $v0 = ptr to new concatenated string (heap-allocated)
    // Uses: $s0, $s1, $s2, $s3, $s4, $s5
    emit("__str_concat:\n");
    emit("move $s4, $a0\n");       // save str1 ptr
    emit("move $s5, $a1\n");       // save str2 ptr
    emit("li $s2, 0\n");           // total length counter
    // Measure length of str1
    emit("move $s0, $s4\n");
    emit("__sc_l1:\n");
    emit("lb $s3, 0($s0)\n");
    emit("beq $s3, $zero, __sc_l1_done\n");
    emit("addu $s0, $s0, 1\n");
    emit("addu $s2, $s2, 1\n");
    emit("j __sc_l1\n");
    emit("__sc_l1_done:\n");
    // Measure length of str2
    emit("move $s1, $s5\n");
    emit("__sc_l2:\n");
    emit("lb $s3, 0($s1)\n");
    emit("beq $s3, $zero, __sc_l2_done\n");
    emit("addu $s1, $s1, 1\n");
    emit("addu $s2, $s2, 1\n");
    emit("j __sc_l2\n");
    emit("__sc_l2_done:\n");
    // Allocate len+1 bytes
    emit("addu $a0, $s2, 1\n");
    emit("li $v0, 9\n");
    emit("syscall\n");
    // $v0 = allocated buffer
    emit("move $s3, $v0\n");       // save result ptr
    // Copy str1
    emit("move $s0, $s4\n");
    emit("move $s1, $s3\n");
    emit("__sc_cp1:\n");
    emit("lb $s2, 0($s0)\n");
    emit("beq $s2, $zero, __sc_cp1_done\n");
    emit("sb $s2, 0($s1)\n");
    emit("addu $s0, $s0, 1\n");
    emit("addu $s1, $s1, 1\n");
    emit("j __sc_cp1\n");
    emit("__sc_cp1_done:\n");
    // Copy str2
    emit("move $s0, $s5\n");
    emit("__sc_cp2:\n");
    emit("lb $s2, 0($s0)\n");
    emit("sb $s2, 0($s1)\n");
    emit("beq $s2, $zero, __sc_cp2_done\n");
    emit("addu $s0, $s0, 1\n");
    emit("addu $s1, $s1, 1\n");
    emit("j __sc_cp2\n");
    emit("__sc_cp2_done:\n");
    // Return pointer
    emit("move $v0, $s3\n");
    emit("jr $ra\n");
}
```

#### C1n. `finalizeFile()`

```java
public void finalizeFile() {
    out.print(".data\n");
    out.print(dataSec.toString());
    out.print("\n.text\n");
    out.print(textSec.toString());
    out.close();
}
```

### C2. Add `mipsMe` to every IR command

**Signature** (add to every `IrCommand` subclass):
```java
public void mipsMe(mips.MipsGenerator mg, java.util.Map<String,String> regMap)
```

**`regMap`**: maps `"Temp_N"` → `"$tK"` (from register allocator). Is `null` for global-preamble commands.

**Helper** (put as a protected method in `IrCommand` base class):
```java
protected String r(Temp temp, java.util.Map<String,String> regMap) {
    return regMap.get("Temp_" + temp.getSerialNumber());
}
```

---

#### `IrCommandLabel.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    if (isFunctionEntry) {
        mg.startFunction(labelName);
    } else {
        mg.emit(labelName + ":\n");
    }
}
```

#### `IrCommandReturn.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    if (returnValue != null)
        mg.emit("move $v0, " + r(returnValue, regMap) + "\n");
    String fn = mg.currentFunc;
    String epilogue = fn.equals("user_main") ? "user_main_epilogue" : fn + "_epilogue";
    mg.emit("j " + epilogue + "\n");
}
```

#### `IRcommandConstInt.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    mg.emit("li " + r(temp, regMap) + ", " + value + "\n");
}
```

#### `IrCommandConstString.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String strLabel = mg.freshLabel("str_const");
    mg.addDataEntry(strLabel + ": .asciiz \"" + value + "\"\n");
    mg.emit("la " + r(temp, regMap) + ", " + strLabel + "\n");
}
```

#### `IrCommandLoad.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String dstReg = r(dst, regMap);
    ir.Ir ir = ir.Ir.getInstance();
    if (ir.isParam(varName)) {
        int idx = ir.getParamIndex(varName);
        mg.emit("lw " + dstReg + ", " + (8 + idx * 4) + "($fp)\n");
    } else if (ir.isGlobal(varName)) {
        mg.emit("lw " + dstReg + ", " + varName + "\n");
    } else {
        mg.emit("lw " + dstReg + ", " + mg.getLocal(varName) + "($fp)\n");
    }
}
```

#### `IrCommandStore.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String srcReg = r(src, regMap);
    ir.Ir ir = ir.Ir.getInstance();
    if (ir.isGlobal(varName)) {
        mg.emit("sw " + srcReg + ", " + varName + "\n");
    } else {
        mg.emit("sw " + srcReg + ", " + mg.getLocal(varName) + "($fp)\n");
    }
}
```

#### `IrCommandAllocate.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    if (!mg.inFunction) {
        // Global variable: add word to .data
        mg.addDataEntry(varName + ": .word 0\n");
    } else {
        // Local variable: claim a stack slot (no instruction emitted)
        mg.allocLocal(varName);
    }
}
```

**Special case — global string variables:** When `AstVarDec` emits the allocate for a string variable with a string literal initializer at global scope, the `.data` entry should be:
```
varName_str: .asciiz "value"
varName: .word varName_str
```
Handle this by checking in `AstVarDec.irMe()` whether the type is string and the initializer is a string literal. Emit `IrCommandConstString` followed by `IrCommandStore`. The `IrCommandAllocate` for the global string var emits `varName: .word 0`; the subsequent `IrCommandStore` of a string temp will `sw` the address. — Actually the cleanest way is: `IrCommandConstString.mipsMe` emits the `.data` string and `la` the address; then `IrCommandStore.mipsMe` does `sw` to the global label. This means `varName: .word 0` is fine as the `.data` entry for the pointer. The `.asciiz` entry from `IrCommandConstString` goes just above it. This works.

#### `IrCommandBinopAddIntegers.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    mg.emit("add " + r(dst,regMap) + ", " + r(t1,regMap) + ", " + r(t2,regMap) + "\n");
    mg.emitSaturate(r(dst, regMap));
}
```

#### `IrCommandBinopSubIntegers.mipsMe`
```java
mg.emit("sub " + r(dst) + ", " + r(t1) + ", " + r(t2) + "\n");
mg.emitSaturate(r(dst));
```

#### `IrCommandBinopMulIntegers.mipsMe`
```java
mg.emit("mul " + r(dst) + ", " + r(t1) + ", " + r(t2) + "\n");
mg.emitSaturate(r(dst));
```

#### `IrCommandBinopDivIntegers.mipsMe`
```java
mg.emitDivByZeroCheck(r(t2, regMap));
mg.emit("div " + r(t1, regMap) + ", " + r(t2, regMap) + "\n");
mg.emit("mflo " + r(dst, regMap) + "\n");
mg.emitSaturate(r(dst, regMap));
```

#### `IrCommandBinopLtIntegers.mipsMe`
```java
mg.emit("slt " + r(dst) + ", " + r(t1) + ", " + r(t2) + "\n");
```

#### `IrCommandBinopGtIntegers.mipsMe`
```java
mg.emit("sgt " + r(dst) + ", " + r(t1) + ", " + r(t2) + "\n");
```

#### `IrCommandBinopEqIntegers.mipsMe`
```java
mg.emit("seq " + r(dst) + ", " + r(t1) + ", " + r(t2) + "\n");
```

#### `IrCommandBinopEqPointers.mipsMe`
```java
mg.emit("seq " + r(dst) + ", " + r(t1) + ", " + r(t2) + "\n");  // pointer comparison
```

#### `IrCommandBinopEqStrings.mipsMe` (inline, from tutorial)
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String rd = r(dst, regMap), r1 = r(t1, regMap), r2 = r(t2, regMap);
    String loop = mg.freshLabel("str_eq_loop");
    String neq  = mg.freshLabel("str_neq");
    String end  = mg.freshLabel("str_eq_end");
    mg.emit("li " + rd + ", 1\n");
    mg.emit("move $s0, " + r1 + "\n");
    mg.emit("move $s1, " + r2 + "\n");
    mg.emit(loop + ":\n");
    mg.emit("lb $s2, 0($s0)\n");
    mg.emit("lb $s3, 0($s1)\n");
    mg.emit("bne $s2, $s3, " + neq + "\n");
    mg.emit("beq $s2, $zero, " + end + "\n");
    mg.emit("addu $s0, $s0, 1\n");
    mg.emit("addu $s1, $s1, 1\n");
    mg.emit("j " + loop + "\n");
    mg.emit(neq + ":\n");
    mg.emit("li " + rd + ", 0\n");
    mg.emit(end + ":\n");
}
```

#### `IrCommandBinopConcatStrings.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    mg.emit("move $a0, " + r(t1, regMap) + "\n");
    mg.emit("move $a1, " + r(t2, regMap) + "\n");
    mg.emit("jal __str_concat\n");
    mg.emit("move " + r(dst, regMap) + ", $v0\n");
}
```

#### `IrCommandJumpLabel.mipsMe`
```java
mg.emit("j " + labelName + "\n");
```

#### `IrCommandJumpIfEqToZero.mipsMe`
```java
mg.emit("beq " + r(temp, regMap) + ", $zero, " + label + "\n");
```

#### `IrCommandCall.mipsMe` (static function call — from tutorial)
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    // Build args list
    List<Temp> argList = new ArrayList<>();
    TempList cur = args;
    while (cur != null) { argList.add(cur.head); cur = cur.tail; }

    // Push right-to-left (last arg pushed first = deepest in stack)
    for (int i = argList.size() - 1; i >= 0; i--) {
        mg.emit("subu $sp, $sp, 4\n");
        mg.emit("sw " + r(argList.get(i), regMap) + ", 0($sp)\n");
    }
    String callee = funcName.equals("main") ? "user_main" : funcName;
    mg.emit("jal " + callee + "\n");
    mg.emit("addu $sp, $sp, " + (argList.size() * 4) + "\n");
    if (dst != null)
        mg.emit("move " + r(dst, regMap) + ", $v0\n");
}
```

#### `IrCommandVirtualCall.mipsMe` (virtual dispatch — from tutorial)
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    // Build explicit args list
    List<Temp> argList = new ArrayList<>();
    TempList cur = args;
    while (cur != null) { argList.add(cur.head); cur = cur.tail; }

    // Push explicit args right-to-left
    for (int i = argList.size() - 1; i >= 0; i--) {
        mg.emit("subu $sp, $sp, 4\n");
        mg.emit("sw " + r(argList.get(i), regMap) + ", 0($sp)\n");
    }
    // Push self (receiver) LAST — it will sit at fp+8 in callee
    String selfReg = r(receiver, regMap);
    mg.emit("subu $sp, $sp, 4\n");
    mg.emit("sw " + selfReg + ", 0($sp)\n");

    // Virtual dispatch: load vtable ptr from object[0], load method ptr from vtable[idx*4]
    mg.emit("lw $s0, 0(" + selfReg + ")\n");
    int vtIdx = getVtableIndex(className, methodName); // see C3
    mg.emit("lw $s1, " + (vtIdx * 4) + "($s0)\n");
    mg.emit("jalr $s1\n");

    // Pop self + args
    int totalArgs = argList.size() + 1;
    mg.emit("addu $sp, $sp, " + (totalArgs * 4) + "\n");

    if (dst != null)
        mg.emit("move " + r(dst, regMap) + ", $v0\n");
}
```

#### `IrCommandPrintInt.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    mg.emit("# inline implementation of PrintInt\n");
    mg.emit("move $a0, " + r(temp, regMap) + "\n");
    mg.emit("li $v0, 1\n");
    mg.emit("syscall\n");
    mg.emit("li $a0, 32\n");    // trailing space character
    mg.emit("li $v0, 11\n");
    mg.emit("syscall\n");
}
```

#### `IrCommandPrintString.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    mg.emit("# inline implementation of PrintString\n");
    mg.emit("move $a0, " + r(temp, regMap) + "\n");
    mg.emit("li $v0, 4\n");
    mg.emit("syscall\n");
}
```

#### `IrCommandNewClass.mipsMe` (from tutorial — includes vtable)
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
    int totalFields = tc.totalFieldCount();
    int objSize = (totalFields + 1) * 4;  // +1 for vtable pointer at word 0

    mg.emit("li $v0, 9\n");
    mg.emit("li $a0, " + objSize + "\n");
    mg.emit("syscall\n");
    mg.emit("move " + r(dst, regMap) + ", $v0\n");

    // Store vtable pointer at word 0
    mg.emit("la $s0, vt_" + className + "\n");
    mg.emit("sw $s0, 0($v0)\n");

    // Initialize fields to their declared default values (constant literals only).
    // Iterate over fields in layout order (inherited first, then own).
    // Field at layout index i → byte offset (i+1)*4
    // Zero-initialize ALL fields first (sbrk does not guarantee zeroed memory):
    for (int i = 0; i < totalFields; i++) {
        mg.emit("sw $zero, " + ((i+1)*4) + "($v0)\n");
    }
    // Then overwrite fields that have non-zero/non-nil constant initializers.
    // (Requires TypeClassVarDec to store initializer values — see note below)
    emitFieldInits(mg, tc, "$v0");
}
```

**Note on field initializers:** `TypeClassVarDec` only stores name and type. You need to also store the initializer value (int constant or string literal or nil) during semantic analysis. Add to `TypeClassVarDec`:
```java
public int initIntValue = 0;
public String initStringLabel = null; // set if string init; we pre-emit to .data
public boolean hasNonZeroInit = false;
```
Set these values in `AstCField.SemantMe()` or `AstVarDec.SemantMe()` when inside a class context.

#### `IrCommandNewArray.mipsMe` (from tutorial)
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String sizeReg = r(size, regMap);
    String dstReg  = r(dst,  regMap);
    mg.emit("li $v0, 9\n");
    mg.emit("move $a0, " + sizeReg + "\n");
    mg.emit("add $a0, $a0, 1\n");   // one extra cell to store the length
    mg.emit("mul $a0, $a0, 4\n");   // convert to bytes
    mg.emit("syscall\n");
    mg.emit("move " + dstReg + ", $v0\n");
    mg.emit("sw " + sizeReg + ", 0($v0)\n");  // store length at index 0
}
```

#### `IrCommandFieldLoad.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String objReg = r(object, regMap);
    String dstReg = r(dst, regMap);
    mg.emitNilCheck(objReg);
    types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
    int fieldIdx = tc.getFieldIndex(fieldName);
    int byteOffset = (fieldIdx + 1) * 4;  // +1 because word 0 is vtable
    mg.emit("lw " + dstReg + ", " + byteOffset + "(" + objReg + ")\n");
}
```

#### `IrCommandFieldStore.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String objReg = r(object, regMap);
    String valReg = r(value, regMap);
    mg.emitNilCheck(objReg);
    types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
    int fieldIdx = tc.getFieldIndex(fieldName);
    int byteOffset = (fieldIdx + 1) * 4;
    mg.emit("sw " + valReg + ", " + byteOffset + "(" + objReg + ")\n");
}
```

#### `IrCommandArrayLoad.mipsMe` (from tutorial)
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String arrReg = r(array, regMap);
    String idxReg = r(index, regMap);
    String dstReg = r(dst,   regMap);
    mg.emitNilCheck(arrReg);
    mg.emitBoundsCheck(arrReg, idxReg);
    mg.emit("move $s0, " + idxReg + "\n");
    mg.emit("add $s0, $s0, 1\n");       // skip length cell
    mg.emit("mul $s0, $s0, 4\n");
    mg.emit("addu $s0, " + arrReg + ", $s0\n");
    mg.emit("lw " + dstReg + ", 0($s0)\n");
}
```

#### `IrCommandArrayStore.mipsMe`
```java
public void mipsMe(MipsGenerator mg, Map<String,String> regMap) {
    String arrReg = r(array, regMap);
    String idxReg = r(index, regMap);
    String valReg = r(value, regMap);
    mg.emitNilCheck(arrReg);
    mg.emitBoundsCheck(arrReg, idxReg);
    mg.emit("move $s0, " + idxReg + "\n");
    mg.emit("add $s0, $s0, 1\n");
    mg.emit("mul $s0, $s0, 4\n");
    mg.emit("addu $s0, " + arrReg + ", $s0\n");
    mg.emit("sw " + valReg + ", 0($s0)\n");
}
```

### C3. Vtable builder

Add a static utility method (put it in `Main.java` or a new `VtableBuilder.java`):

```java
/**
 * Returns the ordered list of method labels for className's vtable.
 * Inherited parent methods come first (in parent's order),
 * overridden entries are replaced, new methods are appended.
 */
public static List<String> buildVtable(types.TypeClass tc) {
    List<String> vtable = new ArrayList<>();
    if (tc.father != null) vtable = buildVtable(tc.father);

    for (Map.Entry<String, types.TypeFunction> m : tc.methods.entrySet()) {
        String methodName = m.getKey();
        String label = tc.name + "_" + methodName;
        // Check if this method overrides a parent entry
        int idx = -1;
        for (int i = 0; i < vtable.size(); i++) {
            if (vtable.get(i).endsWith("_" + methodName)) { idx = i; break; }
        }
        if (idx >= 0) vtable.set(idx, label);
        else          vtable.add(label);
    }
    return vtable;
}

/** Returns the vtable index (0-based) of methodName in className's vtable. */
public static int getVtableIndex(String className, String methodName) {
    types.TypeClass tc = ir.Ir.getInstance().lookupClass(className);
    List<String> vt = buildVtable(tc);
    for (int i = 0; i < vt.size(); i++) {
        if (vt.get(i).endsWith("_" + methodName)) return i;
    }
    throw new RuntimeException("Method " + methodName + " not found in vtable of " + className);
}
```

`IrCommandVirtualCall.mipsMe` calls `getVtableIndex(className, methodName)`. Make this method accessible (e.g. store it in a static helper class or pass it through the `Ir` singleton).

---

## Track D — Wiring `Main.java` & Testing

### D1. New `Main.java` pipeline

Replace everything after `ast.irMe()` with:

```java
// [9] Get flat IR command list
List<IrCommand> irCommands = ir.Ir.getInstance().getCommandList();

// [10] Split into global preamble + per-function segments
List<IrCommand> globalPreamble = new ArrayList<>();
Map<String, List<IrCommand>> funcSegments = new LinkedHashMap<>(); // keeps source order
String curFunc = null;
for (IrCommand c : irCommands) {
    if (c instanceof ir.IrCommandLabel && ((ir.IrCommandLabel)c).isFunctionEntry) {
        curFunc = ((ir.IrCommandLabel)c).labelName;
        funcSegments.put(curFunc, new ArrayList<>());
    }
    if (curFunc == null) globalPreamble.add(c);
    else funcSegments.get(curFunc).add(c);
}

// [11] Register allocation per function (and for global preamble)
Map<String, Map<String,String>> allRegAllocs = new LinkedHashMap<>();

// Allocate registers for global preamble (runs inside user_main frame)
cfg.LivenessAnalysis la0 = new cfg.LivenessAnalysis(globalPreamble);
la0.compute();
cfg.InterferenceGraph ig0 = cfg.InterferenceGraph.build(la0);
allRegAllocs.put("__global_preamble", new regalloc.RegisterAllocator(ig0).allocate());

// Allocate registers per function
for (Map.Entry<String, List<IrCommand>> e : funcSegments.entrySet()) {
    cfg.LivenessAnalysis la = new cfg.LivenessAnalysis(e.getValue());
    la.compute();
    cfg.InterferenceGraph ig = cfg.InterferenceGraph.build(la);
    allRegAllocs.put(e.getKey(), new regalloc.RegisterAllocator(ig).allocate());
}

// [12] MIPS generation
mips.MipsGenerator.init(outputFileName);
mips.MipsGenerator mg = mips.MipsGenerator.getInstance();

// Emit vtables for all classes (in declaration order, into .data)
for (types.TypeClass tc : getAllClassesInDeclarationOrder()) {
    List<String> vt = buildVtable(tc);
    mg.emitVtable(tc.name, vt);
}

// Emit global .data entries (IrCommandAllocate commands only)
for (IrCommand c : globalPreamble) {
    if (c instanceof ir.IrCommandAllocate)
        c.mipsMe(mg, null);
}

// Emit per-function MIPS
Map<String,String> preambleRegMap = allRegAllocs.get("__global_preamble");
for (Map.Entry<String, List<IrCommand>> e : funcSegments.entrySet()) {
    String fn = e.getKey();
    Map<String,String> regMap = allRegAllocs.get(fn);
    for (IrCommand c : e.getValue()) {
        // Special: for user_main's entry label, also emit global preamble inits inline
        if (c instanceof ir.IrCommandLabel
                && ((ir.IrCommandLabel)c).isFunctionEntry
                && fn.equals("main")) {
            c.mipsMe(mg, regMap);  // emits "user_main:" + prologue
            // Emit global initializer instructions (non-allocate) inline at start of user_main
            for (IrCommand gc : globalPreamble) {
                if (!(gc instanceof ir.IrCommandAllocate))
                    gc.mipsMe(mg, preambleRegMap);
            }
        } else {
            c.mipsMe(mg, regMap);
        }
    }
    mg.endFunction(fn);
}

// MIPS main stub and runtime handlers
mg.emitMipsMain();
mg.emitRuntimeHandlers();
mg.finalizeFile();
```

**`getAllClassesInDeclarationOrder()`**: iterate over the `Ir` class registry, but the registry (a `HashMap`) has no order. Instead, collect class names in order during `AstClassDec.SemantMe()` by appending to a `List<String>` stored in `Ir`. Add `List<String> classOrder = new ArrayList<>()` to `Ir` and append to it in `registerClass()`.

### D2. Output format (error cases)

The existing `catch (Exception e)` block in `Main.java` writes `ERROR`. Verify that `HelperFunctions.printErrorAndExit(lineNum)` writes `ERROR(N)` (with parentheses, not space) and throws an exception. The register allocation failure is handled by `System.exit(1)` inside `RegisterAllocator.allocate()` — before that, write `Register Allocation Failed` to the output file writer. Pass the `fileWriter` to the allocator, or handle it in `Main.java` by catching `RegisterAllocationException`.

| Situation | Output to `argv[1]` |
|-----------|---------------------|
| Lex error | `ERROR` |
| Syntax error at line N | `ERROR(N)` |
| Semantic error at line N | `ERROR(N)` |
| Register allocation fails | `Register Allocation Failed` |
| Success | Valid MIPS assembly |

### D3. Update `Makefile`

- Change JAR output name from `ANALYZER` to `COMPILER`
- Fix `make everything` to: run `java -jar COMPILER input/Input.txt output/MIPS.txt`, then run SPIM
- Do NOT hardcode absolute paths — use relative paths only

### D4. Submission structure

```
<ID>.zip
├── ids.txt        (one student ID per line)
└── ex5/
    ├── Makefile   (builds to ex5/COMPILER)
    ├── src/
    ├── cup/
    ├── jflex/
    └── ...
```

### D5. Testing checklist

```bash
# Build
cd ex5 && make

# Test 1: simple function call (test_1.c: f(1,2)=3, PrintInt(3))
java -jar COMPILER examples/test_1.c /tmp/t1.s && spim -file /tmp/t1.s
# Expected output: 3

# Test 2: global string (test_2.c: PrintString("abcd"))
java -jar COMPILER examples/test_2.c /tmp/t2.s && spim -file /tmp/t2.s
# Expected output: abcd

# Test 3: all main tests TEST_01 through TEST_17
for i in $(seq 1 17); do
  f=$(printf "input/TEST_%02d*.txt" $i)
  java -jar COMPILER $f /tmp/out.s
  spim -file /tmp/out.s > /tmp/actual.txt
  expected=$(printf "expected_output/TEST_%d_OUTPUT.txt" $i)
  diff $expected /tmp/actual.txt && echo "PASS $i" || echo "FAIL $i"
done

# Test 4: register allocation failure
java -jar COMPILER <reg_fail_test> /tmp/out.txt
cat /tmp/out.txt   # must print: Register Allocation Failed

# Test 5: runtime error — access violation
java -jar COMPILER input/TEST_08_Access_Violation.txt /tmp/out.s
spim -file /tmp/out.s     # must print: Access Violation

# Test 6: runtime error — invalid pointer dereference
java -jar COMPILER input/TEST_09_Access_Violation.txt /tmp/out.s
spim -file /tmp/out.s     # must print: Invalid Pointer Dereference
```

---

## Dependency Graph

```
A1 (LinkedHashMap fields/methods)
  └─► getFieldIndex() used by IrCommandFieldLoad/Store.mipsMe
  └─► buildVtable() deterministic because methods is LinkedHashMap

A2 (Ir registries: params, globals, classes)
  └─► A8 (AstVarDec calls registerGlobal)
  └─► A5 (AstFuncDec calls registerParam)
  └─► A6 (AstClassDec calls registerClass in SemantMe)
  └─► C (mipsMe implementations query the registries)

A3 (isFunctionEntry flag) ← START HERE
  └─► D1 (Main.java splits IR list by function)
  └─► B (liveness knows function segment boundaries)

A4 (new IR command classes)
  └─► A9, A10 (AST nodes emit new commands)
  └─► C (mipsMe implementations for new commands)

A5 + A6 + A7 (emit IR for all functions and class methods)
  └─► B (liveness has complete IR per function)

B1 + B2 + B3 (liveness → interference → allocator)
  └─► D1 (regMap passed into mipsMe calls)

C1 + C2 + C3 (MipsGenerator + mipsMe + vtable builder)
  └─► D1 (all code gen calls go here)

D1 (Main.java wiring) ← LAST
```

**Minimum viable starting order:**
1. A3 (one-line change — unblocks B and D planning)
2. A1, A2 (registries and type ordering)
3. A4–A12 (all IR fixes in Track A)
4. B1–B3 and C1–C3 in parallel  
5. D1 last

---

## Common Mistakes to Avoid

1. **Do not** use `HashMap` for `fields` or `methods` in `TypeClass` — field offsets must be deterministic.
2. **Do not** call `jal` for virtual method calls — always use `jalr $s1` after loading from vtable.
3. **Do not** forget to push self (`receiver`) onto the stack before the `jalr` — it should be the last thing pushed, sitting at `fp+8` in the callee.
4. **Do not** emit saturation for comparison operators (`slt`, `sgt`, `seq`) — only for arithmetic (`add`, `sub`, `mul`, `div`).
5. **Do not** forget that the array's byte offset for element `i` is `(i+1)*4` — word 0 stores the length.
6. **Do not** forget that the object's field byte offset is `(fieldIndex+1)*4` — word 0 stores the vtable pointer.
7. **Do not** hardcode `MIPS.txt` as the output path — use `argv[1]` from `Main.java`.
8. **Do not** call `endFunction()` inside `mipsMe` — call it from the outer loop in `Main.java` after all commands of a function segment are processed.
````