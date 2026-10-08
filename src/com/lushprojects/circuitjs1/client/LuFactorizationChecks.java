package com.lushprojects.circuitjs1.client;

/** Independent pre-optimization Crout oracle, exercised in JVM and GWT. */
final class LuFactorizationChecks {
    static int run() {
        int assertions = matrixRowCopyChecks() + matrixRowRestoreChecks();
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

        // Nonzero rows with a zero leading column exercise the original
        // later-row zero tie and tiny-pivot behavior, including signed zero.
        assertions += compareWithOriginal(new double[][] {
            { 0, 1, 2 }, { -0.0, 3, 4 }, { 0, 5, 7 }
        }, workspace, "zero leading pivot column");

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
        assertions += compareWithOriginal(new double[][] {
            { 2, 3, 4 }, { 0, 5, 6 }, { 0, 0, 7 }
        }, workspace, "upper triangular with no trailing updates");
        assertions += compareWithOriginal(new double[][] {
            { 8, 0, -0.0 }, { 2, 9, 0 }, { -3, 4, 10 }
        }, workspace, "lower triangular with empty upper rows");

        boolean emptyLowerOverflowRejected = false;
        try {
            CirSim.lu_factor(new double[][] { { Double.MIN_VALUE, 1 }, { 0, 1 } },
                2, new int[2], workspace);
        } catch (SolverExecutionBoundary.Failure expected) {
            emptyLowerOverflowRejected =
                expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        require(emptyLowerOverflowRejected,
            "empty lower column bypassed reciprocal-overflow rejection");
        assertions += 1 + workspaceClearAssertions(workspace);

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

        assertions += bulkTrailingFailurePrefixChecks();
        assertions += checkStickyFiniteSolveCases();
        assertions += checkInvertMatrix();
        assertions += boundedTrailingChecks();
        return assertions;
    }

    // Exercise each failure lane for support widths 1-4 in the thin and bulk loops.
    // The finite prefix is written; the failing value and untouched suffix are
    // retained, and finally must release every recorded scratch row.
    private static int bulkTrailingFailurePrefixChecks() {
        int assertions = 0;
        CirSim.LuFactorizationWorkspace workspace = new CirSim.LuFactorizationWorkspace();
        for (int width = 1; width <= 4; width++) {
            for (int failingColumn = 1; failingColumn <= width; failingColumn++) {
                double[][] matrix = {
                    { 1, 1.0e308, 1.0e308, 1.0e308, 1.0e308 },
                    { .5, 5.0e307, 5.0e307, 5.0e307, 5.0e307 },
                    { 0, 0, 1, 0, 0 },
                    { 0, 0, 0, 1, 0 },
                    { 0, 0, 0, 0, 1 }
                };
                for (int column = width + 1; column <= 4; column++) {
                    matrix[0][column] = 0;
                    matrix[1][column] = 0;
                }
                matrix[1][failingColumn] = -1.75e308;
                boolean rejected = false;
                try {
                    CirSim.lu_factor(matrix, 5, new int[5], workspace);
                } catch (SolverExecutionBoundary.Failure expected) {
                    rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
                }
                require(rejected, "trailing LU overflow was not rejected at width/column " + width + "/" + failingColumn);
                assertions++;
                for (int column = 1; column <= 4; column++) {
                    double expected = column > width ? 0 : column < failingColumn ? 0 :
                        column == failingColumn ? -1.75e308 : 5.0e307;
                    require(matrix[1][column] == expected,
                        "trailing LU failure changed prefix/suffix at " + width + "/" + failingColumn + "/" + column);
                    assertions++;
                }
                assertions += workspaceClearAssertions(workspace);
            }
        }
        return assertions;
    }


    // Independent Crout comparisons at the bounded-path limits, plus failures
    // that must still stop before the failing value is stored.
    private static int boundedTrailingChecks() {
        int assertions = 0;
        CirSim.LuFactorizationWorkspace workspace = new CirSim.LuFactorizationWorkspace();
        assertions += compareWithOriginal(makeFixture(128, 8), workspace, "bounded dimension 128");
        assertions += compareWithOriginal(makeFixture(129, 8), workspace, "checked dimension 129");

        double limit = 1.0e100;
        double aboveLimit = Double.longBitsToDouble(Double.doubleToLongBits(limit) + 1);
        require(aboveLimit > limit && SolverExecutionBoundary.finite(aboveLimit),
                "next representable bound fixture is not finite and above the limit");
        assertions++;
        for (double maximum : new double[] { limit, aboveLimit }) {
            assertions += compareWithOriginal(new double[][] {
                { maximum, 2, 3 }, { -maximum, 7, 11 }, { maximum / 2, 13, 17 }
            }, workspace, "bounded magnitude boundary " + maximum);
        }

        // Exact first-pivot support widths cover thin, leading bulk, remaining
        // bulk and tail updates against the unchanged independent oracle.
        for (int width = 1; width <= 9; width++) {
            double[][] matrix = new double[width + 1][width + 1];
            matrix[0][0] = 16;
            for (int column = 1; column <= width; column++) matrix[0][column] = column + 1;
            for (int row = 1; row <= width; row++) {
                matrix[row][0] = row;
                matrix[row][row] = 16 + row;
            }
            assertions += compareWithOriginal(matrix, workspace, "bounded upper width " + width);
        }

        assertions += compareWithOriginal(new double[][] {
            { 1.0e100, 1, -2 }, { Double.MIN_VALUE, 2, 1 }, { -1.0e99, 3, 7 }
        }, workspace, "bounded underflow-created lower zero");
        assertions += compareWithOriginal(new double[][] {
            { 4, -0.0, 2 }, { 1, 5, -0.0 }, { -Double.MIN_VALUE, 1, 6 }
        }, workspace, "bounded signed-zero and subnormal entries");

        double subnormalReciprocal = 1.0 / 1.0e308;
        require(subnormalReciprocal > 0 && subnormalReciprocal < Double.MIN_NORMAL,
                "checked-path fixture does not have a subnormal reciprocal");
        assertions++;
        assertions += compareWithOriginal(new double[][] {
            { 1.0e308, 1 }, { 1.0e307, 2 }
        }, workspace, "checked finite subnormal reciprocal");
        assertions += boundedReciprocalFailurePrefixChecks(workspace);
        assertions += aliasedBoundedFailurePrefixChecks(workspace);
        return assertions;
    }

    private static int boundedReciprocalFailurePrefixChecks(
            CirSim.LuFactorizationWorkspace workspace) {
        double[] first = { 2, 1, 0 };
        double[] second = { 1, .5, 0 };
        double[] third = { 0, Double.MIN_VALUE, 1 };
        double[][] matrix = { first, second, third };
        int[] pivots = { -7, -7, -7 };
        boolean rejected = false;
        try {
            CirSim.lu_factor(matrix, 3, pivots, workspace);
        } catch (SolverExecutionBoundary.Failure expected) {
            rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        require(rejected, "bounded reciprocal overflow was not rejected");
        require(matrix[0] == first && matrix[1] == third && matrix[2] == second,
                "bounded reciprocal failure changed the pivoted row identities");
        int assertions = 2;
        double[][] expectedPrefix = {
            { 2, 1, 0 }, { 0, Double.MIN_VALUE, 1 }, { .5, 0, 0 }
        };
        for (int row = 0; row < 3; row++)
            assertions += restoredRowValueChecks(expectedPrefix[row], matrix[row], 3);
        require(pivots[0] == 0 && pivots[1] == 2 && pivots[2] == -7,
                "bounded reciprocal failure changed the pivot-vector prefix");
        assertions++;
        assertions += workspaceClearAssertions(workspace);
        assertions += compareWithOriginal(new double[][] { { 3, 1 }, { 1, 2 } },
                workspace, "bounded workspace recovery after reciprocal failure");
        return assertions;
    }

    private static int aliasedBoundedFailurePrefixChecks(
            CirSim.LuFactorizationWorkspace workspace) {
        // Numeric bounds alone are insufficient: repeated aliases scale the
        // shared pivot and then overwrite later reads of that same pivot row.
        double[] shared = { 1.0e-20, 1.0e100, 1.0e100, 1.0e100, 1.0e100, 1.0e100 };
        double expectedScale = 1.0e-20;
        double reciprocal = 1.0 / expectedScale;
        for (int entry = 0; entry < 5; entry++) expectedScale *= reciprocal;
        double firstUpdate = 1.0e100 - expectedScale * 1.0e100;
        double secondUpdate = firstUpdate - expectedScale * firstUpdate;
        double thirdUpdate = secondUpdate - expectedScale * secondUpdate;
        require(SolverExecutionBoundary.finite(expectedScale) &&
                SolverExecutionBoundary.finite(secondUpdate) &&
                !SolverExecutionBoundary.finite(thirdUpdate),
                "aliased fixture does not overflow at the intended third update");
        double[][] matrix = new double[6][];
        int[] pivots = new int[6];
        for (int row = 0; row < 6; row++) { matrix[row] = shared; pivots[row] = -7; }
        boolean rejected = false;
        try {
            CirSim.lu_factor(matrix, 6, pivots, workspace);
        } catch (SolverExecutionBoundary.Failure expected) {
            rejected = expected.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
        }
        require(rejected, "bounded aliased rows bypassed the checked overflow guard");
        require(shared[0] == expectedScale, "aliased scaling prefix changed");
        require(SolverExecutionBoundary.finite(shared[1]) && shared[1] == secondUpdate,
                "aliased trailing failure stored a nonfinite value or changed its exact finite prefix");
        int assertions = 4;
        for (int column = 2; column < 6; column++) {
            require(shared[column] == shared[1], "aliased trailing failure changed the untouched suffix");
            assertions++;
        }
        for (int row = 0; row < 6; row++) {
            require(matrix[row] == shared, "aliased LU replaced a shared caller row");
            require(pivots[row] == (row == 0 ? 5 : -7),
                    "aliased trailing failure changed the pivot-vector prefix");
            assertions += 2;
        }
        assertions += workspaceClearAssertions(workspace);
        assertions += compareWithOriginal(new double[][] { { 3, 1 }, { 1, 2 } },
                workspace, "bounded workspace recovery after aliased failure");
        return assertions;
    }

    private static int matrixRowCopyChecks() {
        int assertions = 0;
        double[] original = { 3.25, -0.0, Double.MIN_VALUE, Double.MAX_VALUE,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
        for (int size = 0; size <= original.length; size++) {
            double[] copy = CirSim.copyReducedMatrixRow(original, size);
            require(copy != original && copy.length == size && copy.getClass() == original.getClass(),
                "matrix row copy must preserve primitive array metadata and exact prefix length");
            assertions++;
            for (int i = 0; i < size; i++) {
                require(copy[i] == original[i] || (Double.isNaN(copy[i]) && Double.isNaN(original[i])),
                    "matrix row copy changed a source value");
                if (original[i] == 0) require(1 / copy[i] == 1 / original[i], "matrix row copy changed signed zero");
                assertions += original[i] == 0 ? 2 : 1;
            }
            if (size > 0) {
                copy[0] = -19;
                require(original[0] == 3.25, "working matrix row aliases the baseline");
                assertions++;
            }
        }
        for (int invalidSize : new int[] { -1, original.length + 1 }) {
            boolean rejected = false;
            try { CirSim.copyReducedMatrixRow(original, invalidSize); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "matrix row copy silently padded or truncated an invalid prefix");
            assertions++;
        }
        boolean nullRejected = false;
        try { CirSim.copyReducedMatrixRow(null, 0); }
        catch (IllegalArgumentException expected) { nullRejected = true; }
        require(nullRejected, "matrix row copy accepted a null baseline");
        assertions++;
        return assertions;
    }

    private static int matrixRowRestoreChecks() {
        int assertions = 0;
        double[] original = { 3.25, +0.0, -0.0, Double.MIN_VALUE, Double.MAX_VALUE,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -91.5 };
        double[] saved = new double[original.length];
        for (int i = 0; i < original.length; i++) saved[i] = original[i];
        for (int size = 0; size <= original.length; size++) {
            double[] working = new double[size];
            double[] result = CirSim.restoreReducedMatrixRow(original, working, size);
            Object untyped = result;
            require(result == working && result.length == size &&
                    result.getClass() == original.getClass() &&
                    untyped instanceof double[] && (double[])untyped == result,
                    "restore changed reduced-row identity, length or primitive metadata");
            assertions++;
            assertions += restoredRowValueChecks(original, result, size);
            assertions += restoredRowValueChecks(saved, original, saved.length);
            if (size > 0) {
                result[0] = -19;
                require(original[0] == 3.25 &&
                        CirSim.restoreReducedMatrixRow(original, working, size) == working,
                        "second restore changed baseline or reduced-row identity");
                assertions++;
                assertions += restoredRowValueChecks(original, working, size);
            }
        }
        for (double[] working : new double[][] { null, original, new double[2], new double[4] }) {
            double[] result = CirSim.restoreReducedMatrixRow(original, working, 3);
            require(result != original && result != working && result.length == 3 &&
                    result.getClass() == original.getClass(),
                    "missing, wrong-size or baseline-alias target lost independent copy fallback");
            assertions++;
            assertions += restoredRowValueChecks(original, result, 3);
            result[0] = -19;
            assertions += restoredRowValueChecks(saved, original, saved.length);
        }
        double[] large = CirSim.restoreReducedMatrixRow(original, new double[7], 7);
        double[] small = CirSim.restoreReducedMatrixRow(original, large, 2);
        double[] grown = CirSim.restoreReducedMatrixRow(original, small, 8);
        require(small != large && small.length == 2 && grown != small && grown.length == 8,
                "dimension change retained a wrong-size working row");
        assertions++;
        assertions += restoredRowValueChecks(original, grown, 8);
        for (int size : new int[] { -1, original.length + 1 }) {
            boolean rejected = false;
            try { CirSim.restoreReducedMatrixRow(original, new double[2], size); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "row restore accepted invalid baseline prefix");
            assertions++;
        }
        boolean nullRejected = false;
        try { CirSim.restoreReducedMatrixRow(null, new double[0], 0); }
        catch (IllegalArgumentException expected) { nullRejected = true; }
        require(nullRejected, "row restore accepted a null baseline");
        assertions++;

        double[][] full = { { 0, 1, 2, 100, -0.0 }, { 1, 1, 3, 101, +0.0 },
            { 2, 4, 9, 102, Double.NaN } };
        double[][] fullSaved = new double[3][5];
        double[][] working = new double[3][3];
        double[][] allocatedRows = new double[3][];
        for (int row = 0; row < 3; row++) {
            allocatedRows[row] = working[row];
            for (int column = 0; column < 5; column++) fullSaved[row][column] = full[row][column];
        }
        CirSim.LuFactorizationWorkspace workspace = new CirSim.LuFactorizationWorkspace();
        for (int pass = 0; pass < 3; pass++) {
            double[][] expected = new double[3][3];
            int[] expectedPivots = new int[3], actualPivots = new int[3];
            for (int row = 0; row < 3; row++) {
                double[] current = working[row];
                require(CirSim.restoreReducedMatrixRow(full[row], current, 3) == current,
                        "restore replaced a row after LU permutation");
                assertions++;
                assertions += restoredRowValueChecks(full[row], current, 3);
                for (int column = 0; column < 3; column++) expected[row][column] = full[row][column];
            }
            require(originalFactor(expected, 3, expectedPivots) &&
                    CirSim.lu_factor(working, 3, actualPivots, workspace),
                    "restored pivot fixture is singular");
            assertions++;
            boolean moved = false;
            for (int row = 0; row < 3; row++) {
                require(expectedPivots[row] == actualPivots[row],
                        "restored pivot differs from independent Crout oracle");
                assertions++;
                assertions += restoredRowValueChecks(expected[row], working[row], 3);
                int membership = 0;
                for (double[] allocated : allocatedRows) if (working[row] == allocated) membership++;
                require(membership == 1, "restored LU row left its original allocation set");
                assertions++;
                for (int other = 0; other < row; other++) {
                    require(working[row] != working[other], "restored working rows alias");
                    assertions++;
                }
                moved |= actualPivots[row] != row;
                assertions += restoredRowValueChecks(fullSaved[row], full[row], 5);
            }
            require(moved, "restore fixture performed no row pivot");
            assertions++;
            double[] expectedRhs = { 1, -2, 4 }, actualRhs = { 1, -2, 4 };
            originalSolve(expected, 3, expectedPivots, expectedRhs);
            CirSim.lu_solve(working, 3, actualPivots, actualRhs);
            assertions += restoredRowValueChecks(expectedRhs, actualRhs, 3);
            assertions += workspaceClearAssertions(workspace);
        }
        return assertions;
    }

    private static int restoredRowValueChecks(double[] expected, double[] actual, int size) {
        for (int i = 0; i < size; i++)
            require(Double.doubleToLongBits(expected[i]) == Double.doubleToLongBits(actual[i]),
                    "row restore changed value or signed zero at " + i);
        return size;
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

    private static int checkStickyFiniteSolveCases() {
        int assertions = 0;

        // Dense and sparse matrices both require real pivoted factors. Exercise
        // multiple all-finite RHS shapes against the unchanged independent oracle.
        double[][] dense = {
            { 4, 1, 2 }, { 1, 5, 1 }, { 2, 1, 6 }
        };
        double[][] sparsePivoted = {
            { 0, 0, 2, 0 }, { 1, 0, 0, 0 },
            { 0, 3, 0, 1 }, { 0, 0, 1, 4 }
        };
        assertions += compareSolveFixtureWithOriginal(dense,
                new double[] { 2, -3, 5 }, "dense finite solve");
        double[][] sparsePivotFactors = copy(sparsePivoted);
        int[] sparsePivotVector = new int[sparsePivoted.length];
        require(originalFactor(sparsePivotFactors, sparsePivoted.length, sparsePivotVector),
                "sparse pivot fixture is singular");
        require(sparsePivotVector[0] == 1 && sparsePivotVector[1] == 2,
                "sparse solve fixture did not exercise its intended row pivots");
        assertions += 2;
        assertions += compareSolveFixtureWithOriginal(sparsePivoted,
                new double[] { 1, 2, -3, 4 }, "sparse pivoted finite solve");
        assertions += compareSolveFixtureWithOriginal(sparsePivoted,
                new double[] { 0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE },
                "pivoted signed-zero and subnormal solve");
        assertions += compareSolveFixtureWithOriginal(sparsePivoted,
                new double[] { Double.NaN, 2, -3, 4 },
                "pivoted sparse initial NaN RHS");

        double[][] identity = { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
        assertions += compareSolveFixtureWithOriginal(identity,
                new double[] { -0.0, 0.0, -0.0 }, "all-zero signed RHS");
        for (double bad : new double[] {
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            double[] rhs = { 1, bad, -2 };
            assertions += compareSolveFixtureWithOriginal(identity, rhs,
                    "initial nonfinite RHS " + bad);
        }

        // The lower multiplier is finite and pivot-valid, but this forward
        // accumulation overflows. The next forward row and backsolve must keep
        // the historical dense 0*Infinity => NaN propagation.
        double[][] forwardOverflow = {
            { 1, 0, 0 }, { 0.9, 1, 0 }, { 0, 0, 1 }
        };
        double[][] forwardOracleFactors = copy(forwardOverflow);
        int[] forwardOraclePivots = new int[forwardOverflow.length];
        require(originalFactor(forwardOracleFactors, forwardOverflow.length, forwardOraclePivots),
                "forward-overflow oracle fixture is singular");
        double[] forwardOracleRhs = { 1.0e308, -1.0e308, 1 };
        originalSolve(forwardOracleFactors, forwardOverflow.length, forwardOraclePivots,
                forwardOracleRhs);
        // Forward substitution first produces [1e308, -Inf, NaN]. During
        // back substitution, row 1 evaluates 0*NaN and promotes -Inf to NaN;
        // row 0 then also becomes NaN. Check the final oracle state, not the
        // intermediate forward value.
        require(Double.isNaN(forwardOracleRhs[0]) &&
                Double.isNaN(forwardOracleRhs[1]) &&
                Double.isNaN(forwardOracleRhs[2]),
                "forward-overflow oracle did not retain final dense NaN propagation");
        assertions += 2;
        assertions += compareSolveFixtureWithOriginal(forwardOverflow,
                new double[] { 1.0e308, -1.0e308, 1 },
                "finite RHS overflows during forward substitution");

        // A successful factorization can retain a subnormal final pivot. The
        // RHS stays finite through forward substitution, then the final divide
        // overflows; an earlier zero upper coefficient must still propagate NaN.
        double[][] tinyFinalPivot = {
            { 1, 0 }, { 0, Double.MIN_VALUE }
        };
        double[][] tinyOracleFactors = copy(tinyFinalPivot);
        int[] tinyOraclePivots = new int[tinyFinalPivot.length];
        require(originalFactor(tinyOracleFactors, tinyFinalPivot.length, tinyOraclePivots) &&
                tinyOracleFactors[0][1] == 0 && tinyOracleFactors[1][1] == Double.MIN_VALUE,
                "tiny-pivot oracle fixture lost its zero upper coefficient or subnormal pivot");
        double[] tinyOracleRhs = { 0, 1 };
        originalSolve(tinyOracleFactors, tinyFinalPivot.length, tinyOraclePivots, tinyOracleRhs);
        require(Double.isNaN(tinyOracleRhs[0]) &&
                tinyOracleRhs[1] == Double.POSITIVE_INFINITY,
                "tiny-pivot oracle did not retain 0*Inf propagation");
        assertions += 2;
        assertions += compareSolveFixtureWithOriginal(tinyFinalPivot,
                new double[] { 0, 1 }, "tiny final pivot second-identity RHS overflow");

        return assertions;
    }

    private static int compareSolveFixtureWithOriginal(double[][] before,
            double[] initialRhs, String description) {
        int size = before.length;
        require(initialRhs.length == size, description + ": RHS dimension mismatch");
        double[][] expectedFactors = copy(before), actualFactors = copy(before);
        int[] expectedPivots = new int[size], actualPivots = new int[size];
        require(originalFactor(expectedFactors, size, expectedPivots),
                description + ": independent fixture factorization is singular");
        require(CirSim.lu_factor(actualFactors, size, actualPivots),
                description + ": CircuitJS fixture factorization is singular");
        int assertions = 2;
        for (int i = 0; i < size; i++) {
            require(expectedPivots[i] == actualPivots[i],
                    description + ": factor pivot differs from independent oracle");
            assertions++;
            for (int j = 0; j < size; j++) {
                require(expectedFactors[i][j] == actualFactors[i][j],
                        description + ": factor differs from independent oracle");
                assertions++;
            }
        }
        double[] expectedRhs = new double[size], actualRhs = new double[size];
        for (int i = 0; i < size; i++)
            expectedRhs[i] = actualRhs[i] = initialRhs[i];
        originalSolve(expectedFactors, size, expectedPivots, expectedRhs);
        CirSim.lu_solve(actualFactors, size, actualPivots, actualRhs);
        for (int i = 0; i < size; i++) {
            require(sameSolveValue(expectedRhs[i], actualRhs[i]),
                    description + ": solve differs from independent oracle at " + i +
                    " (expected " + expectedRhs[i] + ", got " + actualRhs[i] + ")");
            assertions++;
        }
        return assertions;
    }

    private static boolean sameSolveValue(double expected, double actual) {
        if (Double.isNaN(expected)) return Double.isNaN(actual);
        if (Double.isInfinite(expected)) return expected == actual;
        // Signed-zero bit identity is intentionally outside this oracle's contract.
        if (expected == 0 && actual == 0) return true;
        return expected == actual;
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
