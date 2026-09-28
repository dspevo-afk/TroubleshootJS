package com.lushprojects.circuitjs1.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Native-only canary for the real CircuitJS LU and the Q30 support census.
 *
 * <p>The control and observed factors use independent matrix clones.  The
 * collector is wrapped around the observed private owned factor; no alternate
 * LU implementation or second runtime instrumentation path is introduced.</p>
 */
public final class Q30LuSupportCensusActualLuContractTest {
    private static final int ACTIVE_BLOCK_SIZE = 3;
    private static final int FULL_BLOCK_SIZE = 5;
    private static final Method OWNED_FACTOR = ownedFactorMethod();
    private static int assertions;

    public static void main(String[] args) {
        System.out.println("PASS: Q30 actual-LU support-census contracts assertions=" + run());
    }

    public static int run() {
        assertions = 0;
        checkActualBlockAndCollector();
        checkFalseAndThrowOutcomes();
        checkSentinelRisk();
        checkColdWarmIsolation();
        return assertions;
    }

    private static void checkActualBlockAndCollector() {
        double[][] input = finiteInterleavedBlock();
        double[][] original = copy(input);
        double[][] originalBefore = copy(original);

        FactorRun control = factor(copy(input), ACTIVE_BLOCK_SIZE, false);
        ObservedRun observed = factorWithCollector(input, original, ACTIVE_BLOCK_SIZE, true);

        assertOutcomeParity(control.outcome, observed.factor.outcome, "asymmetric block outcome");
        check(control.outcome.returned && control.outcome.value,
            "general control rejected the finite asymmetric block");
        check(observed.factor.outcome.returned && observed.factor.outcome.value,
            "owned LU rejected the finite asymmetric block");
        assertFactorParity(control, observed.factor, "asymmetric block");
        assertRowIdentities(control, ACTIVE_BLOCK_SIZE, "general asymmetric block");
        assertRowIdentities(observed.factor, ACTIVE_BLOCK_SIZE, "owned asymmetric block");
        assertMatrixBits(originalBefore, original, "original matrix changed during census");

        Q30LuSupportCensus.Report report = observed.report;
        check(report.valid && report.frozen && report.scopeComplete && report.completeCapture,
            "successful actual-LU census did not freeze complete evidence");
        check(report.selectedFactorCalls == 1 && report.completedFactorCalls == 1 &&
            report.failedFactorCalls == 0 && report.samples.length == 1,
            "successful actual-LU selection/completion totals changed");
        Q30LuSupportCensus.Sample sample = report.samples[0];
        check(sample.factorSucceeded && sample.opportunity.exact &&
            sample.opportunity.zeroPivotObservationAvailable &&
            sample.opportunity.zeroPivotFallbacks == 0 &&
            !sample.opportunity.postFactorZeroRisk,
            "finite asymmetric actual-LU replay was not exact evidence");
        check(sample.ipvtTraceComplete && sample.ipvtPrefix.length == ACTIVE_BLOCK_SIZE,
            "successful actual-LU sample did not retain a complete pivot trace");
        assertVectorEquals(sample.ipvtPrefix, observed.factor.pivots,
            "successful actual-LU pivot trace");
        int retainedFirstPivot = sample.ipvtPrefix[0];
        sample.ipvtPrefix[0] = -1;
        Q30LuSupportCensus.Report copiedReport = observed.census.freeze();
        check(copiedReport.samples[0].ipvtPrefix[0] == retainedFirstPivot,
            "successful pivot trace aliases collector retained state");
        assertAsymmetricGraph(sample.snapshot.originalGraph,
            "original asymmetric block graph");
        assertAsymmetricGraph(sample.snapshot.currentGraph,
            "current asymmetric block graph");
        check(sample.snapshot.currentCrossOriginalEdges == 0 &&
            sample.snapshot.stableOriginalComponents &&
            sample.snapshot.originalGraph.nonfiniteEntries == 0 &&
            sample.snapshot.currentGraph.nonfiniteEntries == 0,
            "finite block support graph lost its original partition");

        double[] controlRhs = new double[] { 17.0, -4.0, 29.0,
            tailSentinel(8, 0), tailSentinel(8, 1) };
        double[] observedRhs = controlRhs.clone();
        CirSim.lu_solve(control.matrix, ACTIVE_BLOCK_SIZE, control.pivots, controlRhs);
        CirSim.lu_solve(observed.factor.matrix, ACTIVE_BLOCK_SIZE,
            observed.factor.pivots, observedRhs);
        assertVectorBits(controlRhs, observedRhs, "asymmetric block solution");
        check(sameBits(controlRhs[3], tailSentinel(8, 0)) &&
            sameBits(controlRhs[4], tailSentinel(8, 1)) &&
            sameBits(observedRhs[3], tailSentinel(8, 0)) &&
            sameBits(observedRhs[4], tailSentinel(8, 1)),
            "inactive RHS tails changed during active solve");

        ObservedRun noFallbackDeclaration = factorWithCollector(input,
            copy(input), ACTIVE_BLOCK_SIZE, false);
        assertFactorParity(control, noFallbackDeclaration.factor,
            "no-fallback-declaration actual-LU");
        check(noFallbackDeclaration.report.valid && noFallbackDeclaration.report.frozen &&
            !noFallbackDeclaration.report.zeroPivotObservationAvailable &&
            !noFallbackDeclaration.report.samples[0].opportunity.exact,
            "missing zero-pivot fallback declaration was presented as exact evidence");
    }

    private static void checkFalseAndThrowOutcomes() {
        double[][] zeroRow = { { 2.0, 1.0 }, { 0.0, 0.0 } };
        FactorRun zeroControl = factor(copy(zeroRow), 2, false);
        FactorRun zeroOwned = factor(copy(zeroRow), 2, true);
        assertOutcomeParity(zeroControl.outcome, zeroOwned.outcome, "zero-row outcome");
        check(zeroControl.outcome.returned && !zeroControl.outcome.value,
            "general LU did not return false for a zero row");
        assertMatrixBits(zeroRow, zeroControl.matrix, "general zero-row mutation");
        assertMatrixBits(zeroRow, zeroOwned.matrix, "owned zero-row mutation");
        assertRowIdentities(zeroControl, 2, "general zero-row identity");
        assertRowIdentities(zeroOwned, 2, "owned zero-row identity");

        double[][] overflow = {
            { Double.MIN_VALUE, 1.0 },
            { Double.MIN_VALUE, 2.0 }
        };
        FactorRun throwControl = factor(copy(overflow), 2, false);
        FactorRun throwOwned = factor(copy(overflow), 2, true);
        assertOutcomeParity(throwControl.outcome, throwOwned.outcome,
            "finite overflow outcome");
        check(!throwControl.outcome.returned &&
            throwControl.outcome.failure instanceof SolverExecutionBoundary.Failure &&
            ((SolverExecutionBoundary.Failure)throwControl.outcome.failure).outcome ==
                SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE,
            "general LU did not expose the expected numerical failure");
        assertMatrixBits(throwControl.matrix, throwOwned.matrix,
            "throwing LU partial matrix parity");
        assertVectorEquals(throwControl.pivots, throwOwned.pivots,
            "throwing LU pivot parity");
        assertRowIdentities(throwControl, 2, "general throwing identity");
        assertRowIdentities(throwOwned, 2, "owned throwing identity");

        double[][] throwOriginal = copy(overflow);
        ObservedRun failed = factorWithCollector(overflow, throwOriginal, 2, true, false);
        assertOutcomeParity(throwControl.outcome, failed.factor.outcome,
            "collector-wrapped numerical failure");
        assertMatrixBits(throwControl.matrix, failed.factor.matrix,
            "collector-wrapped partial matrix parity");
        assertVectorEquals(throwControl.pivots, failed.factor.pivots,
            "collector-wrapped pivot parity");
        assertMatrixBits(overflow, throwOriginal,
            "collector-wrapped throw changed original matrix");
        Q30LuSupportCensus.Report report = failed.report;
        check(report.valid && report.frozen && !report.scopeComplete &&
            !report.completeCapture && report.selectedFactorCalls == 1 &&
            report.completedFactorCalls == 0 && report.failedFactorCalls == 1 &&
            report.samples.length == 1,
            "collector did not preserve failed-factor completion flags");
        Q30LuSupportCensus.Sample sample = report.samples[0];
        check(!sample.factorSucceeded && !sample.opportunity.replayComplete &&
            !sample.opportunity.exact && sample.opportunity.initialOrderOnly &&
            !sample.ipvtTraceComplete && sample.ipvtPrefix.length == 0,
            "failed collector sample was presented as complete replay evidence");
    }

    private static void checkSentinelRisk() {
        double[][] singularNonzero = { { 1.0, 1.0 }, { 1.0, 1.0 } };
        ObservedRun risk = factorWithCollector(singularNonzero,
            copy(singularNonzero), 2, true);
        check(risk.factor.outcome.returned && risk.factor.outcome.value,
            "owned LU did not return successfully after its zero-pivot sentinel");
        check(sameBits(risk.factor.matrix[1][1], 1e-18),
            "owned LU did not expose the actual zero-pivot sentinel");
        assertRowIdentities(risk.factor, 2, "sentinel-risk row identity");
        Q30LuSupportCensus.Report report = risk.report;
        check(report.valid && report.frozen && report.scopeComplete &&
            report.completeCapture && report.samples.length == 1,
            "sentinel-risk collector did not retain a successful sample");
        Q30LuSupportCensus.Sample sample = report.samples[0];
        check(sample.factorSucceeded && !sample.opportunity.exact &&
            sample.opportunity.postFactorZeroRisk &&
            sample.opportunity.postFactorSentinelHits == 1 &&
            sample.opportunity.zeroPivotObservationAvailable &&
            sample.opportunity.zeroPivotFallbacks == 0,
            "real zero-pivot sentinel risk was not retained conservatively");
    }

    private static void checkColdWarmIsolation() {
        double[][] input = finiteInterleavedBlock();
        ObservedRun cold = factorWithCollector(input, copy(input), ACTIVE_BLOCK_SIZE, true);
        Q30LuSupportCensus warm = new Q30LuSupportCensus("warm", 8);
        warm.declareZeroPivotObservationAvailable();
        Q30LuSupportCensus.Report warmReport = warm.freeze();
        check(cold.report.samples.length == 1 &&
            cold.report.selectedFactorCalls == 1,
            "cold actual-LU census lost its selected sample");
        check(warmReport.valid && warmReport.frozen && warmReport.eligibleFactorCalls == 0 &&
            warmReport.selectedFactorCalls == 0 && warmReport.samples.length == 0,
            "warm collector inherited state from the cold actual-LU census");
    }

    private static FactorRun factor(double[][] input, int n, boolean owned) {
        double[][] rowsBefore = input.clone();
        int[] pivots = new int[n];
        CirSim.LuFactorizationWorkspace workspace =
            new CirSim.LuFactorizationWorkspace();
        FactorResult outcome = invokeFactor(input, n, pivots, workspace, owned);
        check(workspace.allCountsZeroForChecks() && workspace.allRowReferencesNullForChecks(),
            (owned ? "owned" : "general") + " LU did not clear its workspace");
        return new FactorRun(input, rowsBefore, pivots, outcome);
    }

    private static ObservedRun factorWithCollector(double[][] input, double[][] original,
            int n, boolean declareZeroObservation) {
        return factorWithCollector(input, original, n, declareZeroObservation, true);
    }

    private static ObservedRun factorWithCollector(double[][] input, double[][] original,
            int n, boolean declareZeroObservation, boolean scopeComplete) {
        double[][] actual = copy(input);
        double[][] rowsBefore = actual.clone();
        int[] pivots = new int[n];
        CirSim.LuFactorizationWorkspace workspace =
            new CirSim.LuFactorizationWorkspace();
        Q30LuSupportCensus census = new Q30LuSupportCensus("actual-lu", 8);
        if (declareZeroObservation) census.declareZeroPivotObservationAvailable();
        advanceToSelected(census, n);
        Q30LuSupportCensus.SampleToken token = census.beginFactor(n);
        check(token != null, "eighth actual-LU factor was not selected");
        census.recordPreFactor(token, actual, original, n);
        FactorResult outcome = invokeFactor(actual, n, pivots, workspace, true);
        if (outcome.returned && outcome.value)
            census.recordPostFactorZeroRisk(token, actual, n);
        census.finishFactor(token, outcome.returned && outcome.value, pivots);
        Q30LuSupportCensus.Report report = scopeComplete ? census.freeze() : census.freeze(false);
        check(workspace.allCountsZeroForChecks() && workspace.allRowReferencesNullForChecks(),
            "collector-wrapped owned LU did not clear its workspace");
        return new ObservedRun(new FactorRun(actual, rowsBefore, pivots, outcome), report,
            census);
    }

    private static void advanceToSelected(Q30LuSupportCensus census, int n) {
        for (int i = 0; i < 7; i++)
            check(census.beginFactor(n) == null, "actual-LU cadence selected before eighth call");
    }

    private static FactorResult invokeFactor(double[][] matrix, int n, int[] pivots,
            CirSim.LuFactorizationWorkspace workspace, boolean owned) {
        try {
            boolean value;
            if (owned) {
                value = ((Boolean)OWNED_FACTOR.invoke(null, matrix, n, pivots, workspace))
                    .booleanValue();
            } else {
                value = CirSim.lu_factor(matrix, n, pivots, workspace);
            }
            return new FactorResult(true, value, null);
        } catch (InvocationTargetException wrapped) {
            return new FactorResult(false, false, wrapped.getCause());
        } catch (Throwable failure) {
            return new FactorResult(false, false, failure);
        }
    }

    private static Method ownedFactorMethod() {
        try {
            Method method = CirSim.class.getDeclaredMethod("lu_factorNonlinearOwned",
                double[][].class, int.class, int[].class,
                CirSim.LuFactorizationWorkspace.class);
            method.setAccessible(true);
            return method;
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static double[][] finiteInterleavedBlock() {
        double[][] result = new double[FULL_BLOCK_SIZE][FULL_BLOCK_SIZE];
        for (int row = 0; row < FULL_BLOCK_SIZE; row++)
            for (int column = 0; column < FULL_BLOCK_SIZE; column++)
                result[row][column] = tailSentinel(row, column);
        result[0][0] = 0.0; result[0][1] = 2.0; result[0][2] = -3.0;
        result[1][0] = 5.0; result[1][1] = 0.0; result[1][2] = 0.0;
        result[2][0] = 0.0; result[2][1] = -7.0; result[2][2] = 11.0;
        return result;
    }

    private static double tailSentinel(int row, int column) {
        return 1000.0 + row * 10.0 + column;
    }

    private static void assertOutcomeParity(FactorResult expected, FactorResult actual,
            String label) {
        check(expected.returned == actual.returned,
            label + ": returned/throw outcome changed");
        if (expected.returned) {
            check(expected.value == actual.value, label + ": false/success result changed");
        } else {
            check(expected.failure != null && actual.failure != null &&
                expected.failure.getClass() == actual.failure.getClass(),
                label + ": exception type changed");
            if (expected.failure instanceof SolverExecutionBoundary.Failure &&
                    actual.failure instanceof SolverExecutionBoundary.Failure)
                check(((SolverExecutionBoundary.Failure)expected.failure).outcome ==
                    ((SolverExecutionBoundary.Failure)actual.failure).outcome,
                    label + ": failure outcome changed");
        }
    }

    private static void assertFactorParity(FactorRun expected, FactorRun actual,
            String label) {
        assertOutcomeParity(expected.outcome, actual.outcome, label);
        assertMatrixBits(expected.matrix, actual.matrix, label + " matrix");
        assertVectorEquals(expected.pivots, actual.pivots, label + " pivots");
    }

    private static void assertAsymmetricGraph(Q30LuSupportCensus.Graph graph, String label) {
        check(graph.matrixSize == ACTIVE_BLOCK_SIZE && graph.components.length == 2,
            label + ": expected two support components");
        check(graph.rowComponent[0] == graph.rowComponent[2] &&
            graph.rowComponent[0] != graph.rowComponent[1] &&
            graph.columnComponent[0] == graph.rowComponent[1] &&
            graph.columnComponent[1] == graph.rowComponent[0] &&
            graph.columnComponent[2] == graph.rowComponent[0],
            label + ": [A,B,A] rows/[B,A,A] columns changed");
    }

    private static void assertRowIdentities(FactorRun result, int n, String label) {
        boolean[] used = new boolean[result.rowsBefore.length];
        for (int row = 0; row < result.matrix.length; row++) {
            int source = rowIdentity(result.rowsBefore, result.matrix[row]);
            check(source >= 0 && !used[source], label + ": row identity was duplicated or lost");
            used[source] = true;
            if (row >= n)
                check(result.matrix[row] == result.rowsBefore[row],
                    label + ": inactive row reference moved");
            for (int column = n; column < result.matrix[row].length; column++)
                check(sameBits(result.matrix[row][column], result.rowsBefore[source][column]),
                    label + ": inactive tail sentinel changed at row " + row +
                    " column " + column);
        }
    }

    private static int rowIdentity(double[][] before, double[] row) {
        for (int i = 0; i < before.length; i++) if (before[i] == row) return i;
        return -1;
    }

    private static void assertMatrixBits(double[][] expected, double[][] actual, String label) {
        check(expected.length == actual.length, label + ": matrix row count changed");
        for (int row = 0; row < expected.length; row++) {
            check(expected[row].length == actual[row].length,
                label + ": row length changed at " + row);
            for (int column = 0; column < expected[row].length; column++)
                check(sameBits(expected[row][column], actual[row][column]),
                    label + ": matrix mismatch at [" + row + "][" + column + "]");
        }
    }

    private static void assertVectorBits(double[] expected, double[] actual, String label) {
        check(expected.length == actual.length, label + ": vector length changed");
        for (int i = 0; i < expected.length; i++)
            check(sameBits(expected[i], actual[i]), label + ": vector mismatch at " + i);
    }

    private static void assertVectorEquals(int[] expected, int[] actual, String label) {
        check(expected.length == actual.length, label + ": pivot length changed");
        for (int i = 0; i < expected.length; i++)
            check(expected[i] == actual[i], label + ": pivot mismatch at " + i);
    }

    private static double[][] copy(double[][] source) {
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }

    private static boolean sameBits(double left, double right) {
        return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static final class FactorResult {
        final boolean returned, value;
        final Throwable failure;
        FactorResult(boolean returned, boolean value, Throwable failure) {
            this.returned = returned; this.value = value; this.failure = failure;
        }
    }

    private static final class FactorRun {
        final double[][] matrix, rowsBefore;
        final int[] pivots;
        final FactorResult outcome;
        FactorRun(double[][] matrix, double[][] rowsBefore, int[] pivots,
                FactorResult outcome) {
            this.matrix = matrix; this.rowsBefore = rowsBefore;
            this.pivots = pivots; this.outcome = outcome;
        }
    }

    private static final class ObservedRun {
        final FactorRun factor;
        final Q30LuSupportCensus.Report report;
        final Q30LuSupportCensus census;
        ObservedRun(FactorRun factor, Q30LuSupportCensus.Report report,
                Q30LuSupportCensus census) {
            this.factor = factor; this.report = report; this.census = census;
        }
    }

    private Q30LuSupportCensusActualLuContractTest() { }
}
