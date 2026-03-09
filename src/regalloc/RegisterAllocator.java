package regalloc;

import cfg.InterferenceGraph;
import java.io.PrintWriter;
import java.util.*;

/**
 * Simplification-based graph-coloring register allocator (Chaitin-style).
 * K = 10 (allocates only $t0–$t9; no spilling — exits on failure).
 */
public class RegisterAllocator {
    private static final int K = 10;
    private static final String[] REGS = {
        "$t0","$t1","$t2","$t3","$t4","$t5","$t6","$t7","$t8","$t9"
    };

    private final InterferenceGraph graph;
    private final PrintWriter fileWriter; // may be null

    public RegisterAllocator(InterferenceGraph graph) {
        this.graph = graph;
        this.fileWriter = null;
    }

    public RegisterAllocator(InterferenceGraph graph, PrintWriter fw) {
        this.graph = graph;
        this.fileWriter = fw;
    }

    /**
     * Returns a map from "Temp_N" to "$tK".
     * On failure: writes "Register Allocation Failed" to the output file, then exits.
     */
    public Map<String, String> allocate() {
        if (graph.nodes().isEmpty()) return new HashMap<>();

        // Simplification phase: repeatedly remove a node with degree < K
        Deque<String> stack = new ArrayDeque<>();
        Set<String> removed  = new HashSet<>();
        Set<String> remaining = new HashSet<>(graph.nodes());

        while (!remaining.isEmpty()) {
            String candidate = null;
            for (String n : remaining) {
                long activeDeg = graph.neighbors(n).stream()
                    .filter(nb -> !removed.contains(nb)).count();
                if (activeDeg < K) { candidate = n; break; }
            }
            if (candidate == null) {
                fail();
            }
            stack.push(candidate);
            removed.add(candidate);
            remaining.remove(candidate);
        }

        // Reconstruction phase: assign colors
        Map<String, String> coloring = new HashMap<>();
        while (!stack.isEmpty()) {
            String n = stack.pop();
            Set<String> usedColors = new HashSet<>();
            for (String nb : graph.neighbors(n))
                if (coloring.containsKey(nb)) usedColors.add(coloring.get(nb));
            boolean assigned = false;
            for (String reg : REGS) {
                if (!usedColors.contains(reg)) {
                    coloring.put(n, reg);
                    assigned = true;
                    break;
                }
            }
            if (!assigned) fail();
        }
        return coloring;
    }

    private void fail() {
        if (fileWriter != null) {
            fileWriter.print("Register Allocation Failed");
            fileWriter.flush();
        }
        System.out.println("Register Allocation Failed");
        System.exit(1);
    }
}
