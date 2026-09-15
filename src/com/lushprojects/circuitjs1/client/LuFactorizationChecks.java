package com.lushprojects.circuitjs1.client;

/** Independent pre-optimization Crout oracle, exercised in JVM and GWT. */
final class LuFactorizationChecks {
    static int run() {
        int assertions = 0;
        for (int size : new int[] { 1, 2, 3, 8, 24, 64 }) {
            for (int fixture = 0; fixture < 6; fixture++) {
                double[][] before = new double[size][size];
                for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) {
                    int code = (i * 31 + j * 17 + fixture * 7) % 19 - 9;
                    boolean sameIsland = i / 3 == j / 3;
                    before[i][j] = fixture == 0 || (fixture < 4 && sameIsland) ? code / 7.0 : 0;
                    if (i == j) before[i][j] += size + 2;
                }
                if (fixture == 2 && size > 1) {
                    double[] first = before[0]; before[0] = before[size - 1]; before[size - 1] = first;
                }
                // An all-zero row, dependent rows and pivot ties retain the
                // historical singular/epsilon-pivot behavior.
                if (fixture == 4) for (int j = 0; j < size; j++) before[size - 1][j] = 0;
                if (fixture == 5 && size > 1)
                    for (int j = 0; j < size; j++) before[size - 1][j] = before[0][j];
                double[][] expected = copy(before), actual = copy(before);
                int[] expectedPivots = new int[size], actualPivots = new int[size];
                boolean expectedResult = originalFactor(expected, size, expectedPivots);
                require(CirSim.lu_factor(actual, size, actualPivots) == expectedResult); assertions++;
                if (!expectedResult) continue;
                double[] rhs = new double[size], actualRhs = new double[size];
                for (int i = 0; i < size; i++) {
                    require(expectedPivots[i] == actualPivots[i]); assertions++;
                    rhs[i] = actualRhs[i] = (i * 13 % 17) - 8;
                    for (int j = 0; j < size; j++) {
                        require(expected[i][j] == actual[i][j]); assertions++;
                    }
                }
                originalSolve(expected, size, expectedPivots, rhs);
                CirSim.lu_solve(actual, size, actualPivots, actualRhs);
                for (int i = 0; i < size; i++) {
                    require(SolverExecutionBoundary.finite(rhs[i]) && rhs[i] == actualRhs[i]); assertions++;
                }
            }
        }
        for (double bad : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            boolean rejected = false;
            try { CirSim.lu_factor(new double[][] { { 1, 0 }, { 0, bad } }, 2, new int[2]); }
            catch (SolverExecutionBoundary.Failure expected) {
                rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
            }
            require(rejected); assertions++;
        }
        return assertions;
    }

    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][matrix.length];
        for (int i = 0; i < matrix.length; i++)
            for (int j = 0; j < matrix.length; j++) result[i][j] = matrix[i][j];
        return result;
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Crout factor/pivot/solution differs from original algorithm");
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
