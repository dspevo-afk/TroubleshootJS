package com.lushprojects.circuitjs1.client;

/** Independent pre-optimization Crout oracle, exercised in JVM and GWT. */
final class LuFactorizationChecks {
    static int run() {
        int assertions = 0;
        CirSim.LuFactorizationWorkspace workspace = new CirSim.LuFactorizationWorkspace();
        for (int size : new int[] { 0, 1, 2, 3, 8, 24, 31, 32, 33, 63, 64, 65, 81 }) {
            for (int fixture = 0; fixture < 12; fixture++)
                assertions += compareWithOriginal(makeFixture(size, fixture), workspace,
                        "size " + size + " fixture " + fixture);
        }
        int[] alternatingSizes = { 33, 2, 65, 3, 31, 1, 81, 8 };
        int[] alternatingFixtures = { 6, 0, 8, 2, 1, 9, 3, 11 };
        for (int i = 0; i < alternatingSizes.length; i++)
            assertions += compareWithOriginal(makeFixture(alternatingSizes[i], alternatingFixtures[i]),
                    workspace, "alternating workspace reuse " + i);

        // Several late pivots compare the right-looking trailing updates against
        // the independent Crout oracle after row order has changed.
        double[][] latePivot = {
            { 10, 0, 1, 0 },
            { 1, 1, 1, 0 },
            { 2, 2, 1, 0 },
            { 3, 10, 5, 1 }
        };
        assertions += compareWithOriginal(latePivot, workspace, "late cross-column pivots");
        double[][] lateExpected = copy(latePivot);
        int[] latePivots = new int[latePivot.length];
        require(originalFactor(lateExpected, latePivot.length, latePivots), "late-pivot fixture is singular");
        require(latePivots[1] == 3 && latePivots[2] == 3,
                "late-pivot fixture did not exercise the intended row swaps");
        assertions += 2;

        double[][] tiedPivot = {
            { 1, 2, 3 }, { 5, 7, 11 }, { -5, 13, 17 }
        };
        assertions += compareWithOriginal(tiedPivot, workspace, "later-row pivot tie");
        int[] tiedPivots = new int[3];
        require(originalFactor(copy(tiedPivot), 3, tiedPivots), "pivot-tie fixture is singular");
        require(tiedPivots[0] == 2, "pivot tie did not preserve the later-row >= rule");
        assertions += 2;

        // Underflow after a nonzero input factor must be computed and then
        // omitted from the right-looking lower-row update list; signed zero
        // remains numeric-equivalent.
        assertions += compareWithOriginal(new double[][] {
            { 1.0e308, 1 }, { Double.MIN_VALUE, 2 }
        }, workspace, "underflow-created zero");
        double[][] underflow = { { 1.0e308, 1 }, { Double.MIN_VALUE, 2 } };
        int[] underflowPivots = new int[2];
        require(CirSim.lu_factor(underflow, 2, underflowPivots, workspace),
                "underflow fixture unexpectedly singular");
        require(underflow[1][0] == 0.0, "underflow fixture did not create a zero multiplier");
        assertions += 2 + workspaceClearAssertions(workspace);

        // A negative finite multiplier with a negative-zero row entry exercises
        // the zero scaling skip without claiming signed-zero bit identity.
        assertions += compareWithOriginal(new double[][] {
            { -2, 1 }, { -0.0, 1 }
        }, workspace, "negative multiplier and negative zero");

        // Reuse after a singular early return, a nonfinite input rejection, and
        // an overflow that occurs after a row reference has been recorded.
        double[][] singular = { { 1, 2 }, { 0, 0 } };
        int[] singularPivots = new int[2];
        require(!CirSim.lu_factor(singular, 2, singularPivots, workspace),
                "all-zero row was not treated as singular");
        assertions += 1 + workspaceClearAssertions(workspace);
        for (double bad : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            boolean rejectedThreeArgumentApi = false;
            try {
                CirSim.lu_factor(new double[][] { { 1, 0 }, { 0, bad } }, 2, new int[2]);
            } catch (SolverExecutionBoundary.Failure expected) {
                rejectedThreeArgumentApi = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
            }
            require(rejectedThreeArgumentApi,
                    "three-argument LU API did not reject a nonfinite input as a numerical failure");
            assertions++;

            boolean rejected = false;
            try {
                CirSim.lu_factor(new double[][] { { 1, 0 }, { 0, bad } }, 2,
                        new int[2], workspace);
            }
            catch (SolverExecutionBoundary.Failure expected) {
                rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
            }
            require(rejected, "nonfinite LU input was not rejected as a numerical failure");
            assertions += 1 + workspaceClearAssertions(workspace);
        }

        boolean overflowRejected = false;
        try {
            CirSim.lu_factor(new double[][] {
                { 1.0e308, 1.0e308 }, { 1.0e308, -1.0e308 }
            }, 2, new int[2], workspace);
        } catch (SolverExecutionBoundary.Failure expected) {
            overflowRejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        require(overflowRejected, "finite-input LU overflow was not rejected");
        assertions += 1 + workspaceClearAssertions(workspace);
        assertions += compareWithOriginal(new double[][] { { 3, 1 }, { 1, 2 } },
                workspace, "valid factorization after failures");

        assertions += checkInvertMatrix();
        return assertions;
    }

    private static double[][] makeFixture(int size, int fixture) {
        double[][] before = new double[size][size];
        if (fixture == 9) {
            for (int i = 0; i < size; i++) before[i][i] = i == 0 ? 1 : 2;
            if (size > 1) before[1][0] = -1;
            return before;
        }
        if (fixture == 10) {
            for (int i = 0; i < size; i++) {
                before[i][i] = (i & 1) == 0 ? size + 2 : -(size + 2);
                for (int j = 0; j < size; j++)
                    if (i != j && ((i + j) & 1) == 0) before[i][j] = -0.0;
            }
            return before;
        }
        if (fixture == 11) {
            for (int i = 0; i < size; i++) before[i][i] = 1;
            if (size > 1) before[1][0] = Double.MIN_VALUE;
            if (size > 2) before[2][1] = -Double.MIN_VALUE;
            return before;
        }
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) {
            int code = (i * 31 + j * 17 + fixture * 7) % 19 - 9;
            boolean sameIsland = i / 3 == j / 3;
            before[i][j] = fixture == 0 || (fixture < 4 && sameIsland) ? code / 7.0 : 0;
            // Connected sparse systems, not just disconnected islands:
            // a band, an arrowhead and sparsely bridged local blocks.
            if (fixture == 6 && Math.abs(i - j) <= 2)
                before[i][j] = code / 7.0;
            if (fixture == 7 && (i == 0 || j == 0))
                before[i][j] = code / 7.0;
            if (fixture == 8 && (sameIsland || (i * 7 + j * 11) % 31 == 0))
                before[i][j] = code / 7.0;
            if (i == j) before[i][j] += size + 2;
        }
        if ((fixture == 2 || fixture == 8) && size > 1) {
            double[] first = before[0]; before[0] = before[size - 1]; before[size - 1] = first;
        }
        // An all-zero row and dependent rows retain historical singular behavior.
        if (fixture == 4 && size > 0)
            for (int j = 0; j < size; j++) before[size - 1][j] = 0;
        if (fixture == 5 && size > 1)
            for (int j = 0; j < size; j++) before[size - 1][j] = before[0][j];
        return before;
    }

    private static int compareWithOriginal(double[][] before,
            CirSim.LuFactorizationWorkspace workspace, String description) {
        int assertions = 0;
        int size = before.length;
        double[][] expected = copy(before), actual = copy(before);
        int[] expectedPivots = new int[size], actualPivots = new int[size];
        boolean expectedResult = originalFactor(expected, size, expectedPivots);
        boolean actualResult = CirSim.lu_factor(actual, size, actualPivots, workspace);
        require(actualResult == expectedResult, description + ": singular result differs from oracle");
        assertions++;
        assertions += workspaceClearAssertions(workspace);
        if (!expectedResult) return assertions;
        double[] rhs = new double[size], actualRhs = new double[size];
        for (int i = 0; i < size; i++) {
            require(expectedPivots[i] == actualPivots[i], description + ": pivot differs from oracle");
            rhs[i] = actualRhs[i] = (i * 13 % 17) - 8;
            assertions++;
            for (int j = 0; j < size; j++) {
                require(expected[i][j] == actual[i][j], description + ": factor differs from oracle");
                assertions++;
            }
        }
        originalSolve(expected, size, expectedPivots, rhs);
        CirSim.lu_solve(actual, size, actualPivots, actualRhs);
        for (int i = 0; i < size; i++) {
            require(SolverExecutionBoundary.finite(rhs[i]) && rhs[i] == actualRhs[i],
                    description + ": solution differs from oracle");
            assertions++;
        }
        return assertions;
    }

    private static int workspaceClearAssertions(CirSim.LuFactorizationWorkspace workspace) {
        require(workspace.allCountsZeroForChecks(), "LU workspace retained row counts after return");
        require(workspace.allRowReferencesNullForChecks(), "LU workspace retained row references after return");
        return 2;
    }

    private static int checkInvertMatrix() {
        double[][] input = { { 0, 2, 1 }, { 1, 1, 0 }, { 2, 0, 1 } };
        double[][] expectedFactors = copy(input);
        int[] expectedPivots = new int[3];
        require(originalFactor(expectedFactors, 3, expectedPivots), "inverse fixture is singular");
        double[][] expectedInverse = new double[3][3];
        for (int column = 0; column < 3; column++) {
            double[] rhs = new double[] { 0, 0, 0 };
            rhs[column] = 1;
            originalSolve(expectedFactors, 3, expectedPivots, rhs);
            for (int row = 0; row < 3; row++) expectedInverse[row][column] = rhs[row];
        }
        double[][] actual = copy(input);
        double[][] outerArray = actual;
        CirSim.invertMatrix(actual, 3);
        require(actual == outerArray, "invertMatrix replaced the caller's outer matrix array");
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
            require(expectedInverse[i][j] == actual[i][j], "invertMatrix differs from independent inverse oracle");
        return 2 + 9;
    }

    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][matrix.length];
        for (int i = 0; i < matrix.length; i++)
            for (int j = 0; j < matrix.length; j++) result[i][j] = matrix[i][j];
        return result;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // Original CirSim.lu_factor from f41e80f, including tie order and tiny pivot.
    private static boolean originalFactor(double[][] a, int n, int[] ipvt) {
        for (int i = 0; i < n; i++) {
            boolean zero = true;
            for (int j = 0; j < n; j++) if (a[i][j] != 0) { zero = false; break; }
            if (zero) return false;
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < j; i++) {
                double q = a[i][j];
                for (int k = 0; k < i; k++) q -= a[i][k] * a[k][j];
                a[i][j] = q;
            }
            double largest = 0; int largestRow = -1;
            for (int i = j; i < n; i++) {
                double q = a[i][j];
                for (int k = 0; k < j; k++) q -= a[i][k] * a[k][j];
                a[i][j] = q;
                double x = Math.abs(q);
                if (x >= largest) { largest = x; largestRow = i; }
            }
            if (j != largestRow) for (int k = 0; k < n; k++) {
                double x = a[largestRow][k]; a[largestRow][k] = a[j][k]; a[j][k] = x;
            }
            ipvt[j] = largestRow;
            if (a[j][j] == 0) a[j][j] = 1e-18;
            if (j != n - 1) {
                double mult = 1.0 / a[j][j];
                for (int i = j + 1; i < n; i++) a[i][j] *= mult;
            }
        }
        return true;
    }
    private static void originalSolve(double a[][], int n, int ipvt[], double b[]) {
	int i;

	// find first nonzero b element
	for (i = 0; i != n; i++) {
	    int row = ipvt[i];

	    double swap = b[row];
	    b[row] = b[i];
	    b[i] = swap;
	    if (swap != 0)
		break;
	}

	int bi = i++;
	for (; i < n; i++) {
	    int row = ipvt[i];
	    int j;
	    double tot = b[row];

	    b[row] = b[i];
	    // forward substitution using the lower triangular matrix
	    for (j = bi; j < i; j++)
		tot -= a[i][j]*b[j];
	    b[i] = tot;
	}
	for (i = n-1; i >= 0; i--) {
	    double tot = b[i];

	    // back-substitution using the upper triangular matrix
	    int j;
	    for (j = i+1; j != n; j++)
		tot -= a[i][j]*b[j];
	    b[i] = tot/a[i][i];
	}
    }
    private LuFactorizationChecks() { }
}
