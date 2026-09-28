package com.lushprojects.circuitjs1.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Contract checks for the isolated pivot-scan lower-row collection prototype. */
public final class Q30LuPivotCollectionContractTest {
    private static Method ownedFactor;
    private static Method originalFactor;
    private static Method originalSolve;
    private static int assertions;

    static {
        try {
            ownedFactor = CirSim.class.getDeclaredMethod("lu_factorNonlinearOwned",
                    double[][].class, int.class, int[].class,
                    CirSim.LuFactorizationWorkspace.class);
            ownedFactor.setAccessible(true);
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
        System.out.println("PASS: Q30 LU pivot collection contracts " + run());
    }

    public static int run() {
        assertions = 0;
        CirSim.LuFactorizationWorkspace workspace = new CirSim.LuFactorizationWorkspace();

        compareFactorsOnly(new double[0][0], workspace, "empty matrix");
        compareFinite(new double[][] { { 4, 2, 1 }, { 1, 5, 2 }, { 2, 1, 6 } },
                new double[] { 7, 8, 9 }, workspace, "dense");
        compareFinite(new double[][] { { 1, 0, 3, 0 }, { 0, 4, 0, 2 },
                { 2, 0, 5, 1 }, { 0, 3, 1, 6 } },
                new double[] { 3, 4, 5, 6 }, workspace, "sparse");

        double[][] tie = { { 1, 2, 3 }, { 5, 7, 11 }, { -5, 13, 17 } };
        compareFinite(tie, new double[] { 3, -2, 5 }, workspace, "equal-magnitude later pivot");
        int[] tiePivots = new int[3];
        check(invokeOriginalFactor(copy(tie), 3, tiePivots), "tie oracle unexpectedly singular");
        check(tiePivots[0] == 2, "pivot tie no longer chooses the later row");

        // A pivot swap whose old row-k entry is zero removes the selected slot
        // and shifts later row references; the next case replaces that slot.
        double[][] swappedZero = { { 0, 1, 2 }, { 3, 4, 5 }, { 1, 6, 7 } };
        compareFinite(swappedZero, new double[] { 1, 2, 3 }, workspace,
                "swap removes zero old-row-k slot");
        assertFirstPivot(swappedZero, 1, "zero old-row-k slot fixture");
        double[][] swappedNonzero = { { 1, 2, 3 }, { 5, 7, 11 }, { 4, 13, 17 } };
        compareFinite(swappedNonzero, new double[] { 1, 2, 3 }, workspace,
                "swap replaces selected slot with nonzero old row k");
        assertFirstPivot(swappedNonzero, 1, "nonzero old-row-k slot fixture");

        // Column one is all zero after the first pivot, while every input row
        // is nonzero. The zero-pivot tie must retain the later-row choice.
        double[][] zeroTrailingColumn = { { 2, 1, 0 }, { 0, 0, 2 }, { 0, 0, 3 } };
        compareFactorsOnly(zeroTrailingColumn, workspace, "all-zero trailing pivot column");
        int[] zeroColumnPivots = new int[3];
        check(invokeOriginalFactor(copy(zeroTrailingColumn), 3, zeroColumnPivots),
                "all-zero trailing column oracle unexpectedly rejected matrix");
        check(zeroColumnPivots[1] == 2,
                "all-zero trailing pivot did not preserve later-row tie behavior");

        compareFinite(new double[][] { { 1.0e308, 1 }, { Double.MIN_VALUE, 2 } },
                new double[] { 1, 2 }, workspace, "scaled lower entry underflows to zero");

        requireNumericalFailure(new double[][] {
                { Double.MIN_VALUE, 1 }, { Double.MIN_VALUE, 2 }
            }, new double[][] {
                { Double.MIN_VALUE, 2 }, { Double.MIN_VALUE, 1 }
            }, workspace, "reciprocal overflow with collected row");
        requireNumericalFailure(new double[][] {
                { 1, Double.MAX_VALUE }, { 1, -Double.MAX_VALUE }
            }, new double[][] {
                { 1, -Double.MAX_VALUE }, { 1, Double.MAX_VALUE }
            }, workspace, "late upper-update overflow after lower-row collection");

        // Reuse the same workspace across descending and ascending dimensions,
        // including after the two exceptional exits above.
        compareFinite(new double[][] { { 3, 1 }, { 2, 5 } },
                new double[] { 4, 7 }, workspace, "workspace reuse size two");
        compareFinite(new double[][] { { 7 } }, new double[] { 14 }, workspace,
                "workspace reuse size one");
        compareFinite(new double[][] { { 2, 1, 0 }, { 1, 3, 1 }, { 0, 1, 4 } },
                new double[] { 1, 2, 3 }, workspace, "workspace reuse size three");
        return assertions;
    }

    private static void compareFinite(double[][] before, double[] rhs,
            CirSim.LuFactorizationWorkspace workspace, String label) {
        compareEntry(before, rhs, workspace, label + " general path", false, true);
        compareEntry(before, rhs, workspace, label + " owned path", true, true);
    }

    private static void compareFactorsOnly(double[][] before,
            CirSim.LuFactorizationWorkspace workspace, String label) {
        compareEntry(before, null, workspace, label + " general path", false, false);
        compareEntry(before, null, workspace, label + " owned path", true, false);
    }

    private static void compareEntry(double[][] before, double[] rhs,
            CirSim.LuFactorizationWorkspace workspace, String label,
            boolean owned, boolean solve) {
        int n = before.length;
        double[][] expected = copy(before);
        double[][] actual = copy(before);
        int[] expectedPivots = new int[n];
        int[] actualPivots = new int[n];
        check(invokeOriginalFactor(expected, n, expectedPivots),
                label + ": independent oracle rejected fixture");
        boolean result = owned
                ? invokeOwnedFactor(actual, n, actualPivots, workspace)
                : CirSim.lu_factor(actual, n, actualPivots, workspace);
        check(result, label + ": candidate factor rejected fixture");
        assertFactorEqual(expected, expectedPivots, actual, actualPivots, label);
        workspaceClear(workspace, label + " success");
        if (solve) {
            double[] expectedRhs = rhs.clone();
            double[] actualRhs = rhs.clone();
            invokeOriginalSolve(expected, n, expectedPivots, expectedRhs);
            CirSim.lu_solve(actual, n, actualPivots, actualRhs);
            for (int i = 0; i < n; i++)
                check(sameBits(expectedRhs[i], actualRhs[i]),
                        label + ": solve mismatch at rhs[" + i + "]");
        }
    }

    private static void assertFirstPivot(double[][] matrix, int expected, String label) {
        int[] pivots = new int[matrix.length];
        check(invokeOriginalFactor(copy(matrix), matrix.length, pivots),
                label + ": fixture unexpectedly singular");
        check(pivots[0] == expected, label + ": first pivot was " + pivots[0]);
    }

    private static void assertFactorEqual(double[][] expected, int[] expectedPivots,
            double[][] actual, int[] actualPivots, String label) {
        for (int i = 0; i < expected.length; i++) {
            check(expectedPivots[i] == actualPivots[i],
                    label + ": pivot mismatch at " + i);
            for (int j = 0; j < expected.length; j++)
                check(sameBits(expected[i][j], actual[i][j]),
                        label + ": factor mismatch at [" + i + "][" + j + "]");
        }
    }

    private static void requireNumericalFailure(double[][] matrix, double[][] expectedAfter,
            CirSim.LuFactorizationWorkspace workspace, String label) {
        requireEntryFailure(matrix, expectedAfter, workspace, label + " general path", false);
        requireEntryFailure(matrix, expectedAfter, workspace, label + " owned path", true);
    }

    private static void requireEntryFailure(double[][] matrix, double[][] expectedAfter,
            CirSim.LuFactorizationWorkspace workspace, String label, boolean owned) {
        double[][] actual = copy(matrix);
        int[] pivots = new int[matrix.length];
        boolean rejected = false;
        try {
            if (owned) invokeOwnedFactor(actual, matrix.length, pivots, workspace);
            else CirSim.lu_factor(actual, matrix.length, pivots, workspace);
        } catch (SolverExecutionBoundary.Failure failure) {
            rejected = failure.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        check(rejected, label + ": expected numerical failure");
        check(pivots[0] == 1, label + ": failed at an unexpected pivot");
        for (int i = 0; i < expectedAfter.length; i++)
            for (int j = 0; j < expectedAfter.length; j++)
                check(sameBits(expectedAfter[i][j], actual[i][j]),
                        label + ": partial matrix mismatch at [" + i + "][" + j + "]");
        workspaceClear(workspace, label + " cleanup");
    }

    private static boolean invokeOwnedFactor(double[][] matrix, int n, int[] pivots,
            CirSim.LuFactorizationWorkspace workspace) {
        try {
            return ((Boolean) ownedFactor.invoke(null, matrix, n, pivots, workspace)).booleanValue();
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
            throw new AssertionError("unreachable");
        } catch (Exception failure) {
            throw new AssertionError("could not invoke private owned factor", failure);
        }
    }

    private static boolean invokeOriginalFactor(double[][] matrix, int n, int[] pivots) {
        try {
            return ((Boolean) originalFactor.invoke(null, matrix, n, pivots)).booleanValue();
        } catch (InvocationTargetException wrapped) {
            throwUnchecked(wrapped.getCause());
            throw new AssertionError("unreachable");
        } catch (Exception failure) {
            throw new AssertionError("could not invoke independent factor oracle", failure);
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
        throw new AssertionError("oracle or candidate threw checked exception", cause);
    }

    private static void workspaceClear(CirSim.LuFactorizationWorkspace workspace, String point) {
        check(workspace.allCountsZeroForChecks(), point + ": workspace retained counters");
        check(workspace.allRowReferencesNullForChecks(), point + ": workspace retained row references");
    }

    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][matrix.length];
        for (int i = 0; i < matrix.length; i++)
            for (int j = 0; j < matrix.length; j++) result[i][j] = matrix[i][j];
        return result;
    }

    private static boolean sameBits(double left, double right) {
        return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private Q30LuPivotCollectionContractTest() { }
}
