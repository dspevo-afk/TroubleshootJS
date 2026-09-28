package com.lushprojects.circuitjs1.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Native contract for the finite-input nonlinear-owned LU zero-row preflight. */
public final class Q30OwnedLuZeroRowContractTest {
    private static Method ownedFactor;
    private static Method originalFactor;
    private static Method originalSolve;
    private static int assertions;

    static {
        try {
            ownedFactor = CirSim.class.getDeclaredMethod("lu_factorNonlinearOwned",
                    double[][].class, int.class, int[].class, CirSim.LuFactorizationWorkspace.class);
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
        return assertions;
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