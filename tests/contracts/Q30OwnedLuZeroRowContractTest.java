package com.lushprojects.circuitjs1.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Native contract for the finite-input nonlinear-owned LU zero-row preflight. */
public final class Q30OwnedLuZeroRowContractTest {
    private static Method ownedFactor;
    private static Method boundedOwnedFactor;
    private static Method finiteMatrix;
    private static Method originalFactor;
    private static Method originalSolve;
    private static int assertions;

    static {
        try {
            ownedFactor = CirSim.class.getDeclaredMethod("lu_factorNonlinearOwned",
                    double[][].class, int.class, int[].class, CirSim.LuFactorizationWorkspace.class);
            ownedFactor.setAccessible(true);
            boundedOwnedFactor = CirSim.class.getDeclaredMethod("lu_factorNonlinearOwned",
                    double[][].class, int.class, int[].class, CirSim.LuFactorizationWorkspace.class,
                    boolean.class);
            boundedOwnedFactor.setAccessible(true);
            finiteMatrix = CirSim.class.getDeclaredMethod("requireFiniteMatrix");
            finiteMatrix.setAccessible(true);
            originalFactor = LuFactorizationChecks.class.getDeclaredMethod("originalFactor",
                    double[][].class, int.class, int[].class);
            originalFactor.setAccessible(true);
            originalSolve = LuFactorizationChecks.class.getDeclaredMethod("originalSolve",
                    double[][].class, int.class, int[].class, double[].class);
            originalSolve.setAccessible(true);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public static void main(String[] args) {
        int count = run();
        System.out.println("PASS: Q30 owned LU zero-row contracts assertions=" + count);
    }

    public static int run() {
        assertions = 0;
        CirSim.LuFactorizationWorkspace workspace = new CirSim.LuFactorizationWorkspace();

        compareFinite(new double[0][0], new double[0], workspace, "empty matrix");
        compareFinite(new double[][] { { 4 } }, new double[] { 8 }, workspace, "one by one");
        compareFinite(new double[][] { { Double.MIN_VALUE } },
                new double[] { Double.MIN_VALUE }, workspace, "subnormal diagonal");
        compareFinite(new double[][] { { 0, 1 }, { 1, 0 } },
                new double[] { 1, 1 }, workspace, "off-diagonal-only rows");
        compareFinite(new double[][] {
                { 0, 0, 2 }, { 3, 0, 0 }, { 0, 4, 0 }
            }, new double[] { 2, 3, 4 }, workspace, "sole nonzero in last column");
        compareFinite(new double[][] {
                { 1.0, -0.0, 0.0 },
                { 0.0, 2.0, -0.0 },
                { -0.0, 0.0, 3.0 }
            }, new double[] { 1, 2, 3 }, workspace, "signed-zero entries");

        // The tiny second pivot remains solvable with an RHS scaled to the
        // original matrix; this covers a subnormal off-diagonal-only row.
        compareFinite(new double[][] {
                { 0, Double.MIN_VALUE }, { 1, 0 }
            }, new double[] { Double.MIN_VALUE, 1 }, workspace, "subnormal sole-last-column row");

        double[][] tie = {
            { 1, 2, 3 }, { 5, 7, 11 }, { -5, 13, 17 }
        };
        compareFinite(tie, new double[] { 3, -2, 5 }, workspace, "later-row pivot tie");
        int[] tiePivots = new int[3];
        check(invokeOriginalFactor(copy(tie), 3, tiePivots), "tie fixture unexpectedly singular");
        check(tiePivots[0] == 2, "tie fixture did not choose the later equal-magnitude pivot");

        for (int zeroRow = 0; zeroRow < 3; zeroRow++)
            compareSingularWithZeroRow(zeroRow, workspace);

        double[][] overflow = {
            { 1.0e308, 1.0e308 },
            { 1.0e308, -1.0e308 }
        };
        boolean rejected = false;
        try {
            invokeOwnedFactor(copy(overflow), 2, new int[2], workspace);
        } catch (SolverExecutionBoundary.Failure expected) {
            rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        check(rejected, "arithmetic overflow was not reported as a numerical failure");
        workspaceClear(workspace, "arithmetic failure");

        // Reuse after the exception proves finally cleanup left the scratch safe.
        compareFinite(new double[][] { { 2, 0 }, { 1, 3 } },
                new double[] { 2, 4 }, workspace, "valid solve after overflow failure");

        checkBoundedProducer();
        compareBoundedFinite(new double[][] {
                { 4, 1, 2, 3, 4 }, { 2, 8, 1, 0, 1 }, { 1, 0, 7, 1, 0 },
                { 0, 1, 0, 9, 1 }, { 1, 0, 1, 0, 10 }
            }, new double[] { 3, -2, 5, 7, -1 }, workspace);
        checkLateUnderflowSurvivor(workspace);
        compareCheckedProofFalseOverflow(workspace);
        return assertions;
    }

    /** Independent Crout fixture: a retained factor precedes a zero and a later survivor. */
    private static void checkLateUnderflowSurvivor(CirSim.LuFactorizationWorkspace workspace) {
        double[][] before = {
            { 1e100, 1, 2, 3 }, { 5e99, 8, 1, 2 },
            { Double.MIN_VALUE, 0, 11, 1 }, { 2.5e99, 2, 1, 13 }
        };
        double[] rhs = { 1, 2, 3, 4 };
        double[][] expected = copy(before);
        int[] pivots = new int[4];
        check(invokeOriginalFactor(expected, 4, pivots),
                "late-underflow fixture is singular in the independent oracle");
        for (int i = 0; i < 4; i++)
            check(pivots[i] == i, "late-underflow fixture unexpectedly changed row order at " + i);
        check(expected[1][0] != 0 && expected[2][0] == 0 && expected[3][0] != 0,
                "late-underflow fixture omitted the retained prefix, zero, or later survivor");
        compareFinite(before, rhs, workspace, "late underflow followed by nonzero survivor");
        compareBoundedFinite(before, rhs, workspace);
    }

    /** Exercise the actual producer without installing a UI or changing a live graph. */
    private static void checkBoundedProducer() {
        CirSim previous = CirSim.theSim;
        try {
            CirSim sim = new CirSim();
            check(!sim.luBaselineBounded && !sim.luCurrentBounded,
                    "new simulator published a bounded matrix before scanning");
            sim.circuitMatrixSize = 2;
            sim.circuitMatrix = new double[][] { { 4, 1 }, { 2, 3 } };
            sim.circuitNeedsMap = false;
            invokeFiniteMatrix(sim);
            check(sim.luBaselineBounded && sim.luCurrentBounded,
                    "finite small matrix did not publish both bounded flags");
            sim.stampMatrix(1, 1, 2e100);
            check(sim.luBaselineBounded && !sim.luCurrentBounded,
                    "large accumulated dynamic stamp did not invalidate only current proof");
            sim.stampMatrix(1, 1, -2e100);
            check(sim.luBaselineBounded && !sim.luCurrentBounded,
                    "a later small accumulated value re-enabled the current proof");

            sim.circuitMatrix = new double[][] { { 4, 1 }, { 2, 3 } };
            invokeFiniteMatrix(sim);
            sim.stampMatrix(1, 1, Double.MAX_VALUE);
            boolean rejectedAccumulatedStamp = false;
            try {
                sim.stampMatrix(1, 1, Double.MAX_VALUE);
            } catch (SolverExecutionBoundary.Failure failure) {
                rejectedAccumulatedStamp =
                        failure.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
            }
            check(rejectedAccumulatedStamp, "accumulated stamp overflow was not rejected");
            check(sim.circuitMatrix[0][0] == Double.POSITIVE_INFINITY,
                    "stamp overflow changed the original store-before-validation boundary");
            check(sim.luBaselineBounded && !sim.luCurrentBounded,
                    "stamp overflow changed the baseline or re-enabled the current proof");

            sim.circuitMatrix = new double[][] { { 1e100, 0 }, { 0, 1 } };
            invokeFiniteMatrix(sim);
            check(sim.luBaselineBounded && sim.luCurrentBounded,
                    "inclusive magnitude boundary was rejected");
            sim.circuitMatrix[0][0] = Math.nextAfter(1e100, Double.POSITIVE_INFINITY);
            invokeFiniteMatrix(sim);
            check(!sim.luBaselineBounded && !sim.luCurrentBounded,
                    "entry above the magnitude boundary retained a proof");

            sim.circuitMatrixSize = 128;
            sim.circuitMatrix = new double[128][128];
            invokeFiniteMatrix(sim);
            check(sim.luBaselineBounded && sim.luCurrentBounded,
                    "inclusive dimension boundary was rejected by the producer");
            sim.circuitMatrixSize = 129;
            sim.circuitMatrix = new double[129][129];
            invokeFiniteMatrix(sim);
            check(!sim.luBaselineBounded && !sim.luCurrentBounded,
                    "dimension above the boundary retained a proof");

            double[] nonfinite = { Double.NaN, Double.POSITIVE_INFINITY };
            for (int entry = 0; entry < nonfinite.length; entry++) {
                sim.circuitMatrixSize = 2;
                sim.circuitMatrix = new double[][] { { 4, 1 }, { 2, 3 } };
                invokeFiniteMatrix(sim);
                sim.circuitMatrix[0][0] = nonfinite[entry];
                boolean rejected = false;
                try {
                    invokeFiniteMatrix(sim);
                } catch (SolverExecutionBoundary.Failure expected) {
                    rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
                }
                check(rejected, "nonfinite baseline scan did not report numerical failure");
                check(!sim.luBaselineBounded && !sim.luCurrentBounded,
                        "failed baseline scan retained a bounded proof");
            }
        } finally {
            CirSim.theSim = previous;
        }
    }

    private static void compareBoundedFinite(double[][] before, double[] rhs,
            CirSim.LuFactorizationWorkspace workspace) {
        int n = before.length;
        double[][] expected = copy(before);
        double[][] actual = copy(before);
        int[] expectedPivots = new int[n];
        int[] actualPivots = new int[n];
        boolean expectedResult = invokeOriginalFactor(expected, n, expectedPivots);
        boolean actualResult = invokeBoundedOwnedFactor(actual, n, actualPivots, workspace, true);
        check(expectedResult, "bounded owned fixture is singular in the independent oracle");
        check(actualResult == expectedResult, "bounded owned factor result differs from oracle");
        workspaceClear(workspace, "bounded owned success");
        for (int i = 0; i < n; i++) {
            check(expectedPivots[i] == actualPivots[i], "bounded owned pivot differs at " + i);
            for (int j = 0; j < n; j++)
                check(expected[i][j] == actual[i][j],
                        "bounded owned factor differs at [" + i + "][" + j + "]");
        }
        double[] expectedRhs = rhs.clone();
        double[] actualRhs = rhs.clone();
        invokeOriginalSolve(expected, n, expectedPivots, expectedRhs);
        CirSim.lu_solve(actual, n, actualPivots, actualRhs);
        for (int i = 0; i < n; i++)
            check(SolverExecutionBoundary.finite(expectedRhs[i]) && expectedRhs[i] == actualRhs[i],
                    "bounded owned solve differs from independent oracle at " + i);
    }

    /** False proof must preserve the existing checked entry's exact failure prefix. */
    private static void compareCheckedProofFalseOverflow(CirSim.LuFactorizationWorkspace workspace) {
        double[][] before = { { 1e308, 1e308 }, { 1e308, -1e308 } };
        double[][] expected = copy(before);
        double[][] actual = copy(before);
        int[] expectedPivots = new int[2];
        int[] actualPivots = new int[2];
        boolean expectedRejected = false;
        try {
            invokeOwnedFactor(expected, 2, expectedPivots, workspace);
        } catch (SolverExecutionBoundary.Failure failure) {
            expectedRejected = failure.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        check(expectedRejected, "checked overflow fixture did not report numerical failure");
        workspaceClear(workspace, "checked overflow reference");
        boolean actualRejected = false;
        try {
            invokeBoundedOwnedFactor(actual, 2, actualPivots, workspace, false);
        } catch (SolverExecutionBoundary.Failure failure) {
            actualRejected = failure.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        check(actualRejected, "false-proof owned overflow did not report numerical failure");
        workspaceClear(workspace, "false-proof overflow");
        for (int i = 0; i < 2; i++) {
            check(expectedPivots[i] == actualPivots[i], "false-proof failure pivot differs at " + i);
            for (int j = 0; j < 2; j++)
                check(Double.doubleToLongBits(expected[i][j]) == Double.doubleToLongBits(actual[i][j]),
                        "false-proof failure prefix differs at [" + i + "][" + j + "]");
        }
    }

    private static void invokeFiniteMatrix(CirSim sim) {
        try {
            finiteMatrix.invoke(sim);
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
        } catch (Exception failure) {
            throw new AssertionError("could not invoke actual finite matrix producer", failure);
        }
    }

    private static boolean invokeBoundedOwnedFactor(double[][] matrix, int n, int[] pivots,
            CirSim.LuFactorizationWorkspace workspace, boolean bounded) {
        try {
            return ((Boolean) boundedOwnedFactor.invoke(null, matrix, n, pivots, workspace,
                    Boolean.valueOf(bounded))).booleanValue();
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
            throw new AssertionError("unreachable");
        } catch (Exception failure) {
            throw new AssertionError("could not invoke private bounded owned LU helper", failure);
        }
    }

    private static void compareSingularWithZeroRow(int zeroRow,
            CirSim.LuFactorizationWorkspace workspace) {
        double[][] before = {
            { 2, 0, 0 },
            { 0, 3, 0 },
            { 0, 0, 4 }
        };
        for (int column = 0; column < 3; column++)
            before[zeroRow][column] = (column & 1) == 0 ? -0.0 : 0.0;
        double[][] expected = copy(before);
        double[][] actual = copy(before);
        int[] expectedPivots = new int[3];
        int[] actualPivots = new int[3];
        boolean expectedResult = invokeOriginalFactor(expected, 3, expectedPivots);
        boolean actualResult = invokeOwnedFactor(actual, 3, actualPivots, workspace);
        check(!expectedResult, "zero-row oracle fixture was not singular at row " + zeroRow);
        check(actualResult == expectedResult,
                "owned preflight disagreed on zero row " + zeroRow);
        for (int i = 0; i < 3; i++) {
            check(expectedPivots[i] == actualPivots[i],
                    "singular preflight changed pivot output at row " + i);
            for (int j = 0; j < 3; j++)
                check(expected[i][j] == actual[i][j],
                        "singular preflight changed matrix at [" + i + "][" + j + "]");
        }
        workspaceClear(workspace, "singular zero row " + zeroRow);
    }

    private static void compareFinite(double[][] before, double[] rhs,
            CirSim.LuFactorizationWorkspace workspace, String description) {
        int n = before.length;
        double[][] expected = copy(before);
        double[][] actual = copy(before);
        int[] expectedPivots = new int[n];
        int[] actualPivots = new int[n];
        boolean expectedResult = invokeOriginalFactor(expected, n, expectedPivots);
        boolean actualResult = invokeOwnedFactor(actual, n, actualPivots, workspace);
        check(expectedResult, description + ": independent fixture is singular");
        check(actualResult == expectedResult, description + ": factor success differs from oracle");
        workspaceClear(workspace, description + " success");
        double[] expectedRhs = rhs.clone();
        double[] actualRhs = rhs.clone();
        for (int i = 0; i < n; i++) {
            check(expectedPivots[i] == actualPivots[i], description + ": pivot differs at " + i);
            for (int j = 0; j < n; j++)
                check(expected[i][j] == actual[i][j],
                        description + ": factor differs at [" + i + "][" + j + "]");
        }
        invokeOriginalSolve(expected, n, expectedPivots, expectedRhs);
        CirSim.lu_solve(actual, n, actualPivots, actualRhs);
        for (int i = 0; i < n; i++)
            check(SolverExecutionBoundary.finite(expectedRhs[i]) &&
                    expectedRhs[i] == actualRhs[i],
                    description + ": solve differs from independent oracle at " + i);
    }

    private static boolean invokeOwnedFactor(double[][] matrix, int n, int[] pivots,
            CirSim.LuFactorizationWorkspace workspace) {
        try {
            return ((Boolean) ownedFactor.invoke(null, matrix, n, pivots, workspace)).booleanValue();
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
            throw new AssertionError("unreachable");
        } catch (Exception failure) {
            throw new AssertionError("could not invoke private owned LU helper", failure);
        }
    }

    private static boolean invokeOriginalFactor(double[][] matrix, int n, int[] pivots) {
        try {
            return ((Boolean) originalFactor.invoke(null, matrix, n, pivots)).booleanValue();
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
            throw new AssertionError("unreachable");
        } catch (Exception failure) {
            throw new AssertionError("could not invoke independent LU oracle", failure);
        }
    }

    private static void invokeOriginalSolve(double[][] matrix, int n, int[] pivots, double[] rhs) {
        try {
            originalSolve.invoke(null, matrix, n, pivots, rhs);
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
        } catch (Exception failure) {
            throw new AssertionError("could not invoke independent solve oracle", failure);
        }
    }

    private static void throwUnchecked(Throwable cause) {
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new AssertionError("oracle/owned helper threw an unexpected checked exception", cause);
    }

    private static void workspaceClear(CirSim.LuFactorizationWorkspace workspace, String point) {
        check(workspace.allCountsZeroForChecks(), point + ": workspace retained counts");
        check(workspace.allRowReferencesNullForChecks(), point + ": workspace retained row references");
    }

    private static double[][] copy(double[][] matrix) {
        double[][] copy = new double[matrix.length][matrix.length];
        for (int i = 0; i < matrix.length; i++)
            for (int j = 0; j < matrix.length; j++) copy[i][j] = matrix[i][j];
        return copy;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private Q30OwnedLuZeroRowContractTest() { }
}