package cfg;

import ir.IrCommand;
import ir.IrCommandLabel;
import java.util.*;

/**
 * Backward liveness analysis for temporaries (Temp_N strings only).
 * Named variables are accessed via load/store from memory and do not need registers.
 */
public class LivenessAnalysis {
    private final List<IrCommand> commands;
    private List<Set<String>> liveOutPerInstruction;

    public LivenessAnalysis(List<IrCommand> commands) {
        this.commands = commands;
    }

    public void compute() {
        int n = commands.size();
        liveOutPerInstruction = new ArrayList<>(Collections.nCopies(n, new HashSet<>()));
        if (n == 0) return;

        // Build label-to-index map
        Map<String, Integer> labelToIdx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            IrCommand cmd = commands.get(i);
            if (cmd instanceof IrCommandLabel) {
                labelToIdx.put(((IrCommandLabel) cmd).getLabelName(), i);
            }
        }

        // Identify block leaders
        Set<Integer> leaderSet = new TreeSet<>();
        leaderSet.add(0);
        for (int i = 0; i < n; i++) {
            IrCommand cmd = commands.get(i);
            if (cmd.isJump()) {
                String target = cmd.getJumpLabel();
                if (target != null && labelToIdx.containsKey(target)) {
                    leaderSet.add(labelToIdx.get(target));
                }
                if (i + 1 < n) leaderSet.add(i + 1);
            }
        }
        List<Integer> leaderList = new ArrayList<>(leaderSet);
        int numBlocks = leaderList.size();

        // Block ranges [start, end)
        List<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < numBlocks; i++) {
            int start = leaderList.get(i);
            int end   = (i + 1 < numBlocks) ? leaderList.get(i + 1) : n;
            ranges.add(new int[]{start, end});
        }

        // Build successor lists
        List<List<Integer>> succ = new ArrayList<>();
        for (int i = 0; i < numBlocks; i++) succ.add(new ArrayList<>());
        for (int i = 0; i < numBlocks; i++) {
            int[] r = ranges.get(i);
            IrCommand last = commands.get(r[1] - 1);
            if (last.isUnconditionalJump()) {
                String target = last.getJumpLabel();
                if (target != null && labelToIdx.containsKey(target)) {
                    int bi = blockOf(labelToIdx.get(target), leaderList);
                    if (bi >= 0) succ.get(i).add(bi);
                }
            } else if (last.isConditionalJump()) {
                String target = last.getJumpLabel();
                if (target != null && labelToIdx.containsKey(target)) {
                    int bi = blockOf(labelToIdx.get(target), leaderList);
                    if (bi >= 0) succ.get(i).add(bi);
                }
                if (i + 1 < numBlocks) succ.get(i).add(i + 1);
            } else {
                if (i + 1 < numBlocks) succ.get(i).add(i + 1);
            }
        }

        // Build predecessor lists
        List<List<Integer>> pred = new ArrayList<>();
        for (int i = 0; i < numBlocks; i++) pred.add(new ArrayList<>());
        for (int i = 0; i < numBlocks; i++) {
            for (int s : succ.get(i)) pred.get(s).add(i);
        }

        // Compute per-block use[] and def[] (Temp_* only)
        List<Set<String>> blockUse = new ArrayList<>();
        List<Set<String>> blockDef = new ArrayList<>();
        for (int b = 0; b < numBlocks; b++) {
            int[] r = ranges.get(b);
            Set<String> use = new HashSet<>(), def = new HashSet<>();
            for (int i = r[0]; i < r[1]; i++) {
                IrCommand cmd = commands.get(i);
                for (String t : cmd.getReadTemps())
                    if (t.startsWith("Temp_") && !def.contains(t)) use.add(t);
                for (String t : cmd.getWriteTemps())
                    if (t.startsWith("Temp_")) def.add(t);
            }
            blockUse.add(use);
            blockDef.add(def);
        }

        // Backward worklist iteration
        List<Set<String>> liveIn  = new ArrayList<>();
        List<Set<String>> liveOut = new ArrayList<>();
        for (int i = 0; i < numBlocks; i++) { liveIn.add(new HashSet<>()); liveOut.add(new HashSet<>()); }

        Queue<Integer> worklist = new LinkedList<>();
        for (int i = numBlocks - 1; i >= 0; i--) worklist.add(i);

        while (!worklist.isEmpty()) {
            int b = worklist.poll();
            Set<String> newOut = new HashSet<>();
            for (int s : succ.get(b)) newOut.addAll(liveIn.get(s));
            Set<String> newIn = new HashSet<>(blockUse.get(b));
            for (String t : newOut) if (!blockDef.get(b).contains(t)) newIn.add(t);
            if (!newOut.equals(liveOut.get(b)) || !newIn.equals(liveIn.get(b))) {
                liveOut.set(b, newOut);
                liveIn.set(b, newIn);
                for (int p : pred.get(b)) if (!worklist.contains(p)) worklist.add(p);
            }
        }

        // Reconstruct per-instruction liveOut by backward simulation within each block
        liveOutPerInstruction = new ArrayList<>(Collections.nCopies(n, null));
        for (int b = 0; b < numBlocks; b++) {
            int[] r = ranges.get(b);
            Set<String> live = new HashSet<>(liveOut.get(b));
            for (int i = r[1] - 1; i >= r[0]; i--) {
                liveOutPerInstruction.set(i, new HashSet<>(live));
                IrCommand cmd = commands.get(i);
                for (String t : cmd.getWriteTemps()) if (t.startsWith("Temp_")) live.remove(t);
                for (String t : cmd.getReadTemps())  if (t.startsWith("Temp_")) live.add(t);
            }
        }
    }

    private int blockOf(int instrIdx, List<Integer> leaderList) {
        for (int i = leaderList.size() - 1; i >= 0; i--)
            if (leaderList.get(i) <= instrIdx) return i;
        return 0;
    }

    /** Returns the set of temps live AFTER instruction at index i. Call after compute(). */
    public Set<String> getLiveOutAt(int i) {
        Set<String> s = liveOutPerInstruction.get(i);
        return (s != null) ? s : Collections.emptySet();
    }

    public List<IrCommand> getCommands() { return commands; }
}
