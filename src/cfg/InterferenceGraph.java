package cfg;

import ir.IrCommand;
import java.util.*;

/**
 * Interference graph for register allocation.
 * An edge (a, b) means temporaries a and b are simultaneously live
 * at some program point, so they cannot share a register.
 */
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
     * Build interference graph from liveness analysis results.
     * For each instruction that writes temp t, add edge (t, u) for every u live after that instruction.
     */
    public static InterferenceGraph build(LivenessAnalysis la) {
        InterferenceGraph g = new InterferenceGraph();
        List<IrCommand> cmds = la.getCommands();
        for (int i = 0; i < cmds.size(); i++) {
            Set<String> liveOut = la.getLiveOutAt(i);
            for (String def : cmds.get(i).getWriteTemps()) {
                if (!def.startsWith("Temp_")) continue;
                g.addNode(def);
                for (String live : liveOut) {
                    if (live.startsWith("Temp_")) g.addEdge(def, live);
                }
            }
            for (String use : cmds.get(i).getReadTemps()) {
                if (use.startsWith("Temp_")) g.addNode(use);
            }
        }
        return g;
    }
}
