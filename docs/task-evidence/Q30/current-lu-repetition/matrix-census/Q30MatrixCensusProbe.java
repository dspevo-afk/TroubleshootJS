package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Scratch-only Q30 native construction/matrix census. This is not a proof,
 * normal admission, settling, or performance qualification test.
 */
public final class Q30MatrixCensusProbe {
    private static final long[] SEEDS = { 7L, 10387L, 10014L };
    private static final int[] EXPECTED_PACKAGES = { 37, 20, 40 };

    private Q30MatrixCensusProbe() { }

    public static void main(String[] args) {
        if (args.length != 0)
            throw new IllegalArgumentException("Q30 census probe uses its fixed seed set");
        for (int index = 0; index < SEEDS.length; index++)
            census(SEEDS[index], EXPECTED_PACKAGES[index]);
        System.out.println("PASS: Q30 matrix census probe seeds=3 constructionOnly=true fixedStepsPerSeed=1 qualification=NOT_RUN");
    }

    private static void census(long seed, int expectedPackages) {
        CirSim previousSim = CircuitElm.sim;
        HashMap<String, String> previousLocalizationMap = CirSim.localizationMap;
        CirSim sim = new CirSim();
        Rb30Generator.Candidate candidate = null;
        Vector<CircuitElm> elements = null;
        Throwable primaryFailure = null;
        try {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            String canonical = plan.canonical();
            require(canonical.startsWith("rb30-plan@4;seed=" + Long.toString(seed) + ";"),
                "probe must run against current plan epoch 4 for seed " + seed);
            require(plan.physicalPackageCount() == expectedPackages,
                "plan-4 package count changed for seed " + seed);

            configureHeadlessSimulator(sim);
            CircuitElm.sim = sim;
            candidate = new Rb30Generator().construct(plan);
            elements = candidate.elements();
            TreeMap<String, Integer> classCounts = new TreeMap<String, Integer>();
            int wireCount = 0;
            int internalNodes = 0;
            int declaredVoltageSources = 0;
            int posts = 0;
            for (CircuitElm element : elements) {
                String name = element.getClass().getSimpleName();
                Integer count = classCounts.get(name);
                classCounts.put(name, count == null ? 1 : count + 1);
                if (element instanceof WireElm) wireCount++;
                internalNodes += element.getInternalNodeCount();
                declaredVoltageSources += element.getVoltageSourceCount();
                posts += element.getPostCount();
            }

            sim.elmList = new Vector<CircuitElm>(elements);
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.analyzeCircuit();
            require(sim.stopMessage == null && sim.circuitMatrix != null,
                "CircuitJS analysis failed for seed " + seed + ": " + sim.stopMessage);
            require(sim.circuitNonLinear,
                "post-step matrix interpretation requires the nonlinear Q30 circuit for seed " + seed);
            int expectedFullSize = sim.nodeList.size() - 1 + sim.voltageSourceCount;
            require(sim.circuitMatrixFullSize == expectedFullSize,
                "CircuitJS full MNA dimension disagrees with node/source census for seed " + seed);
            require(sim.voltageSourceCount == declaredVoltageSources,
                "CircuitJS source count disagrees with element census for seed " + seed);

            sim.solverExecutor.advanceSteps(1);
            require(sim.stopMessage == null && sim.circuitMatrix != null &&
                    SolverExecutionBoundary.finite(sim.t) && sim.t > 0.0,
                "CircuitJS did not complete one finite fixed solver step for seed " + seed);
            int fullSize = sim.circuitMatrixFullSize;
            int reducedSize = sim.circuitMatrixSize;
            require(sim.circuitMatrix.length == reducedSize,
                "CircuitJS post-step matrix size changed unexpectedly for seed " + seed);

            // A completed nonlinear step leaves its final converged Jacobian
            // stamp in circuitMatrix; the accepted-step LU is not retained.
            // Snapshot that real matrix and call unchanged CircuitJS LU on a
            // copy to inspect diagnostic factor support without touching live state.
            double[][] postStepJacobian = copyMatrix(sim.circuitMatrix);
            List<MatrixComponent> jacobianComponents = bipartiteComponents(postStepJacobian);
            long refactorStarted = System.nanoTime();
            double[][] luCopy = copyMatrix(postStepJacobian);
            boolean factored = CirSim.lu_factor(luCopy, luCopy.length,
                new int[luCopy.length]);
            long diagnosticLuNanos = System.nanoTime() - refactorStarted;
            require(factored,
                "CircuitJS LU rejected the post-step Jacobian snapshot for seed " + seed);
            List<MatrixComponent> factorComponents = bipartiteComponents(luCopy);

            System.out.println("Q30_MATRIX_CENSUS seed=" + Long.toString(seed) +
                " canonical=" + canonical +
                " packages=" + plan.physicalPackageCount() +
                " channels=" + plan.channelCount +
                " elements=" + elements.size() +
                " wires=" + wireCount +
                " modelElements=" + (elements.size() - wireCount) +
                " posts=" + posts +
                " internalNodes=" + internalNodes +
                " declaredVoltageSources=" + declaredVoltageSources +
                " analyzedVoltageSources=" + sim.voltageSourceCount +
                " circuitNodesIncludingReference=" + sim.nodeList.size() +
                " fullMnaSize=" + fullSize +
                " reducedLuSize=" + reducedSize +
                " postStepSeconds=" + Double.toString(sim.t) +
                " postStepJacobianComponents=" + sizesText(jacobianComponents) +
                " diagnosticLuFactorComponents=" + sizesText(factorComponents) +
                " diagnosticLuNanos=" + diagnosticLuNanos +
                " classes=" + countsText(classCounts));
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } catch (Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable cleanupFailure = cleanup(sim, previousSim, previousLocalizationMap, candidate, elements);
            if (cleanupFailure != null) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                else throw new IllegalStateException("Q30 census cleanup failed for seed " + seed,
                    cleanupFailure);
            }
        }
    }

    private static Throwable cleanup(CirSim sim, CirSim previousSim,
            HashMap<String, String> previousLocalizationMap,
            Rb30Generator.Candidate candidate, Vector<CircuitElm> elements) {
        Throwable failure = null;
        CircuitElm.sim = sim;
        try {
            if (candidate != null) {
                try {
                    candidate.assembly.power.setConnected(false);
                    if (!candidate.assembly.power.areAllDisconnected())
                        failure = append(failure, new IllegalStateException(
                            "Q30 census candidate sources remained connected after teardown"));
                } catch (Throwable cleanup) { failure = append(failure, cleanup); }
            }
            try { sim.boardPowerController.setState(BoardPowerState.UNPOWERED); }
            catch (Throwable cleanup) { failure = append(failure, cleanup); }
            if (elements != null) {
                for (CircuitElm element : elements) {
                    try { element.delete(); }
                    catch (Throwable cleanup) { failure = append(failure, cleanup); }
                    finally { removeByIdentity(sim.elmList, element); }
                }
            }
            try { sim.boardPowerController.detach(); }
            catch (Throwable cleanup) { failure = append(failure, cleanup); }
            if (sim.boardPowerController.getBindingsForDeveloperVerification() != null)
                failure = append(failure, new IllegalStateException(
                    "private Q30 census simulator retained external power bindings"));
            try { sim.solverExecutor.retire(); }
            catch (Throwable cleanup) { failure = append(failure, cleanup); }
            if (sim.elmList != null && !sim.elmList.isEmpty())
                failure = append(failure, new IllegalStateException(
                    "private Q30 census simulator retained non-owned elements after teardown"));
        } finally {
            CircuitElm.sim = previousSim;
            CirSim.localizationMap = previousLocalizationMap;
        }
        return failure;
    }
    private static void removeByIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        if (elements == null) return;
        for (int index = elements.size() - 1; index >= 0; index--) {
            if (elements.get(index) == target) elements.remove(index);
        }
    }

    private static Throwable append(Throwable existing, Throwable next) {
        if (existing == null) return next;
        existing.addSuppressed(next);
        return existing;
    }
    private static void configureHeadlessSimulator(CirSim sim) {
        CirSim.localizationMap = new HashMap<String, String>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.timeStep = 5e-6;
        sim.maxTimeStep = 5e-6;
        sim.minTimeStep = 50e-12;
        sim.adjustTimeStep = true;
        CircuitElm.sim = sim;
    }

    private static double[][] copyMatrix(double[][] source) {
        if (source == null || source.length == 0)
            throw new IllegalStateException("Missing CircuitJS matrix");
        int size = source.length;
        double[][] copy = new double[size][size];
        for (int row = 0; row < size; row++) {
            if (source[row] == null || source[row].length != size)
                throw new IllegalStateException("CircuitJS matrix is not square");
            for (int column = 0; column < size; column++) {
                double value = source[row][column];
                if (!SolverExecutionBoundary.finite(value))
                    throw new IllegalStateException("Nonfinite matrix value in Q30 census");
                copy[row][column] = value;
            }
        }
        return copy;
    }

    private static final class MatrixComponent {
        final int rows;
        final int columns;
        MatrixComponent(int rows, int columns) {
            this.rows = rows;
            this.columns = columns;
        }
    }

    /** Exact bipartite numeric support; rows and columns remain distinct. */
    private static List<MatrixComponent> bipartiteComponents(double[][] matrix) {
        int size = matrix.length;
        int[] parent = new int[size * 2];
        int[] rank = new int[size * 2];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (matrix[row][column] != 0.0)
                    union(parent, rank, row, size + column);
            }
        }
        int[] rowCounts = new int[parent.length];
        int[] columnCounts = new int[parent.length];
        for (int row = 0; row < size; row++) rowCounts[find(parent, row)]++;
        for (int column = 0; column < size; column++)
            columnCounts[find(parent, size + column)]++;
        List<MatrixComponent> result = new ArrayList<MatrixComponent>();
        for (int root = 0; root < parent.length; root++) {
            if (rowCounts[root] > 0 || columnCounts[root] > 0)
                result.add(new MatrixComponent(rowCounts[root], columnCounts[root]));
        }
        Collections.sort(result, new Comparator<MatrixComponent>() {
            public int compare(MatrixComponent left, MatrixComponent right) {
                int leftSize = left.rows + left.columns;
                int rightSize = right.rows + right.columns;
                if (leftSize != rightSize) return rightSize - leftSize;
                if (left.rows != right.rows) return right.rows - left.rows;
                return right.columns - left.columns;
            }
        });
        return result;
    }
    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) root = parent[root];
        while (parent[value] != value) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(int[] parent, int[] rank, int left, int right) {
        int a = find(parent, left);
        int b = find(parent, right);
        if (a == b) return;
        if (rank[a] < rank[b]) parent[a] = b;
        else if (rank[a] > rank[b]) parent[b] = a;
        else { parent[b] = a; rank[a]++; }
    }

    private static String sizesText(List<MatrixComponent> components) {
        StringBuilder text = new StringBuilder("[");
        for (int index = 0; index < components.size(); index++) {
            if (index > 0) text.append(',');
            MatrixComponent component = components.get(index);
            text.append('r').append(component.rows).append('c').append(component.columns);
        }
        return text.append(']').toString();
    }
    private static String countsText(TreeMap<String, Integer> counts) {
        StringBuilder text = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) text.append(',');
            text.append(entry.getKey()).append(':').append(entry.getValue());
            first = false;
        }
        return text.append(']').toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}