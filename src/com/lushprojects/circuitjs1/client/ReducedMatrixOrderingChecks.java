package com.lushprojects.circuitjs1.client;

/** Independent pure checks for reduced-matrix ordering and RowInfo remapping. */
final class ReducedMatrixOrderingChecks {
    private ReducedMatrixOrderingChecks() { }

    static int run() {
        Tally tally = new Tally();
        checkGraphOrders(tally);
        checkInverseAndPermutation(tally);
        checkCroutSolveAndResidual(tally);
        checkAtomicFailures(tally);
        checkAutomaticEligibility(tally);
        checkZeroBoundary(tally);
        return tally.count;
    }

    private static void checkGraphOrders(Tally t) {
        double[][] star = new double[5][5];
        for (int leaf = 1; leaf < 5; leaf++) star[0][leaf] = leaf;
        expectOrder(t, star, new int[] {1, 2, 3, 0, 4},
            "one-sided star uses undirected pattern and original-index tie");

        double[][] lowerStar = new double[5][5];
        for (int leaf = 1; leaf < 5; leaf++) lowerStar[leaf][0] = leaf;
        expectOrder(t, lowerStar, new int[] {1, 2, 3, 0, 4},
            "lower-triangle-only star contributes the same undirected pattern");

        double[][] chain = new double[4][4];
        chain[0][1] = chain[1][2] = chain[2][3] = 1;
        expectOrder(t, chain, new int[] {0, 1, 2, 3}, "chain minimum degree with fill and stable ties");

        double[][] disconnected = new double[5][5];
        disconnected[0][1] = 1;
        disconnected[2][3] = -1;
        expectOrder(t, disconnected, new int[] {4, 0, 1, 2, 3},
            "disconnected graph eliminates the isolated vertex first");

        double[][] dense = new double[5][5];
        for (int row = 0; row < dense.length; row++)
            for (int column = 0; column < dense.length; column++)
                dense[row][column] = row == column ? 7 : 1;
        expectOrder(t, dense, new int[] {0, 1, 2, 3, 4}, "dense graph ties by original index");

        double[][] zero = new double[5][5];
        for (int i = 0; i < zero.length; i++) zero[i][i] = i + 1;
        expectOrder(t, zero, new int[] {0, 1, 2, 3, 4}, "diagonal-only graph retains original order");

        double[][] maxStar = starMatrix(128, 0, 1);
        int[] expected = new int[128];
        for (int i = 0; i < 126; i++) expected[i] = i + 1;
        expected[126] = 0;
        expected[127] = 127;
        expectOrder(t, maxStar, expected, "maximum supported ordering boundary");

        final double[][] sharedRows = new double[2][];
        sharedRows[0] = new double[] {2, 1};
        sharedRows[1] = sharedRows[0];
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.minimumDegree(sharedRows); }
        }, "minimumDegree rejects aliased rows");
        final double[][] nonfinite = new double[][] {{2, Double.NaN}, {1, 3}};
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.minimumDegree(nonfinite); }
        }, "minimumDegree rejects nonfinite entries");
        final double[][] tooLarge = new double[129][129];
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.minimumDegree(tooLarge); }
        }, "minimumDegree rejects matrices above its hard boundary");
        t.check(ReducedMatrixOrdering.minimumDegree(new double[0][0]).length == 0,
            "minimumDegree supports the empty boundary");
    }

    private static void checkInverseAndPermutation(Tally t) {
        int[] order = new int[] {2, 0, 3, 1};
        int[] inverse = ReducedMatrixOrdering.inverse(order);
        requireArray(t, inverse, new int[] {1, 3, 0, 2}, "inverse permutation");

        final int[] duplicate = new int[] {0, 0, 2};
        final int[] negative = new int[] {-1, 0, 1};
        final int[] outside = new int[] {0, 1, 3};
        final int[] oversized = new int[129];
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.inverse(duplicate); }
        }, "inverse rejects duplicate");
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.inverse(negative); }
        }, "inverse rejects negative index");
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.inverse(outside); }
        }, "inverse rejects out-of-range index");
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.inverse(oversized); }
        }, "inverse rejects oversized permutation");

        double[][] matrix = new double[][] {
            {4.0, -0.0, 0.5, 0.0},
            {0.25, 3.0, 0.0, -0.75},
            {-0.0, 0.125, 2.5, 0.25},
            {0.5, -0.0, 0.25, 5.0}
        };
        double[] rhs = new double[] {1.0, -0.0, -2.0, 3.5};
        RowInfo[] infos = remapFixtureInfos();
        State before = new State(matrix, rhs, infos);
        RowInfoState[] metadata = new RowInfoState[infos.length];
        for (int i = 0; i < infos.length; i++) metadata[i] = new RowInfoState(infos[i]);

        ReducedMatrixOrdering.Result result =
            ReducedMatrixOrdering.apply(matrix, rhs, infos, order);
        t.check(result.changed, "nonidentity apply reports changed");
        t.check(result.matrix != matrix && result.rightSide != rhs,
            "nonidentity apply returns detached ordered arrays");
        before.checkNumericArraysUnchanged(t, "apply leaves caller A/RHS untouched");
        double[][] expectedMatrix = permute(matrix, order);
        double[] expectedRhs = permute(rhs, order);
        requireMatrixBits(t, result.matrix, expectedMatrix, "exact symmetric matrix permutation");
        requireVectorBits(t, result.rightSide, expectedRhs, "exact RHS permutation including signed zero");

        for (int i = 0; i < infos.length; i++) {
            RowInfo info = infos[i];
            RowInfoState original = metadata[i];
            t.check(info.mapRow == (original.mapRow < 0 ? -1 : inverse[original.mapRow]),
                "mapRow uses inverse permutation at RowInfo " + i);
            t.check(info.mapCol == (original.mapCol < 0 ? -1 : inverse[original.mapCol]),
                "mapCol uses inverse permutation at RowInfo " + i);
            original.checkMetadataUnchanged(t, info, "RowInfo metadata preserved at " + i);
        }

        double[] identityRhs = new double[] {1, -0.0, 3, 4};
        double[][] identityMatrix = copyMatrix(matrix);
        RowInfo[] identityInfos = remapFixtureInfos();
        State identityBefore = new State(identityMatrix, identityRhs, identityInfos);
        RowInfoState[] identityInfoBefore = snapshotInfo(identityInfos);
        ReducedMatrixOrdering.Result identity =
            ReducedMatrixOrdering.apply(identityMatrix, identityRhs, identityInfos,
                new int[] {0, 1, 2, 3});
        t.check(!identity.changed, "identity apply reports unchanged");
        t.check(identity.matrix == identityMatrix && identity.rightSide == identityRhs,
            "identity apply preserves input array identity");
        identityBefore.checkAllUnchanged(t, "identity apply leaves arrays and maps unchanged");
        checkInfoMetadataAndMaps(t, identityInfos, identityInfoBefore, "identity map");
    }

    private static void checkCroutSolveAndResidual(Tally t) {
        double[][] matrix = new double[][] {
            {10, 1, 1, 1},
            {1, 5, 0, 0},
            {1, 0, 6, 0},
            {1, 0, 0, 7}
        };
        double[] expected = new double[] {1, -2, 0.5, 3};
        double[] rhs = new double[] {11.5, -9, 4, 22};
        int[] order = ReducedMatrixOrdering.minimumDegree(matrix);
        requireArray(t, order, new int[] {1, 2, 0, 3}, "literal star solve order");

        double[][] originalFactors = copyMatrix(matrix);
        int[] originalPivots = new int[matrix.length];
        t.check(originalCroutFactor(originalFactors, originalPivots),
            "independent original-order Crout fixture factors");
        double[] originalSolution = copyVector(rhs);
        originalCroutSolve(originalFactors, originalPivots, originalSolution);
        requireVectorNear(t, originalSolution, expected, 1e-12,
            "independent Crout literal expected solution");
        t.check(scaledResidual(matrix, originalSolution, rhs) <= 1e-12,
            "independent Crout original-order residual");

        RowInfo[] infos = identityInfos(matrix.length);
        ReducedMatrixOrdering.Result ordered =
            ReducedMatrixOrdering.apply(copyMatrix(matrix), copyVector(rhs), infos, order);
        double[][] orderedFactors = copyMatrix(ordered.matrix);
        int[] orderedPivots = new int[matrix.length];
        t.check(originalCroutFactor(orderedFactors, orderedPivots),
            "independent Crout permuted fixture factors");
        double[] orderedSolution = copyVector(ordered.rightSide);
        originalCroutSolve(orderedFactors, orderedPivots, orderedSolution);
        double[] mappedBack = new double[matrix.length];
        for (int i = 0; i < order.length; i++) mappedBack[order[i]] = orderedSolution[i];
        requireVectorNear(t, mappedBack, expected, 1e-12,
            "independent Crout mapped-back literal expected solution");
        t.check(scaledResidual(matrix, mappedBack, rhs) <= 1e-12,
            "independent Crout mapped-back residual against original equations");
    }

    private static void checkAtomicFailures(Tally t) {
        final double[][] matrix = new double[][] {{4, 1, 0}, {1, 5, 1}, {0, 1, 6}};
        final double[] rhs = new double[] {1, 2, 3};
        final int[] badOrder = new int[] {0, 0, 2};
        final RowInfo[] infos = identityInfos(3);
        expectApplyRejectedAtomically(t, matrix, rhs, infos, badOrder,
            "invalid order is rejected atomically");

        final RowInfo[] duplicateMaps = identityInfos(3);
        duplicateMaps[1].mapRow = duplicateMaps[0].mapRow;
        expectApplyRejectedAtomically(t, matrix, rhs, duplicateMaps, new int[] {1, 2, 0},
            "invalid duplicate maps are rejected atomically");

        final RowInfo[] outOfRangeMap = identityInfos(3);
        outOfRangeMap[0].mapCol = 3;
        expectApplyRejectedAtomically(t, matrix, rhs, outOfRangeMap, new int[] {1, 2, 0},
            "out-of-range map is rejected atomically");

        final double[][] nonsquare = new double[][] {{2, 1}, {3}};
        expectApplyRejectedAtomically(t, nonsquare, new double[] {1, 2}, identityInfos(2),
            new int[] {1, 0}, "nonsquare matrix is rejected atomically");

        expectApplyRejectedAtomically(t, matrix, new double[] {1, 2}, identityInfos(3),
            new int[] {1, 2, 0}, "RHS shape mismatch is rejected atomically");

        final double[][] nonfiniteMatrix = copyMatrix(matrix);
        nonfiniteMatrix[1][2] = Double.NaN;
        expectApplyRejectedAtomically(t, nonfiniteMatrix, rhs, identityInfos(3),
            new int[] {1, 2, 0}, "nonfinite matrix is rejected atomically");

        final double[] nonfiniteRhs = copyVector(rhs);
        nonfiniteRhs[1] = Double.POSITIVE_INFINITY;
        expectApplyRejectedAtomically(t, matrix, nonfiniteRhs, identityInfos(3),
            new int[] {1, 2, 0}, "nonfinite RHS is rejected atomically");

        final double[] shared = new double[] {3, 1};
        final double[][] aliasedRows = new double[][] {shared, shared};
        expectApplyRejectedAtomically(t, aliasedRows, new double[] {1, 2}, identityInfos(2),
            new int[] {1, 0}, "aliased matrix rows are rejected atomically");

        final RowInfo[] badType = identityInfos(3);
        badType[2].type = 17;
        expectApplyRejectedAtomically(t, matrix, rhs, badType, new int[] {1, 2, 0},
            "unknown RowInfo type is rejected atomically");
    }

    private static void checkAutomaticEligibility(Tally t) {
        double[][] below = starMatrix(15, 0, 1);
        double[] belowRhs = new double[15];
        RowInfo[] belowInfos = identityInfos(15);
        requireReorderUnchanged(t, below, belowRhs, belowInfos,
            "automatic ordering skips size below 16");

        double[][] above = starMatrix(129, 0, 1);
        double[] aboveRhs = new double[129];
        RowInfo[] aboveInfos = identityInfos(129);
        requireReorderUnchanged(t, above, aboveRhs, aboveInfos,
            "automatic ordering skips size above 128");

        checkReorderedBoundary(t, 16, 1, "minimum eligible size");
        checkReorderedBoundary(t, 128, 1, "maximum eligible size");
        checkReorderedBoundary(t, 16, 1e100, "finite entry bound is inclusive");

        double[][] overLimit = starMatrix(16, 0, 2e100);
        requireReorderUnchanged(t, overLimit, new double[16], identityInfos(16),
            "automatic ordering skips entries above 1e100");

        double[][] nonfinite = starMatrix(16, 0, 1);
        nonfinite[0][1] = Double.NaN;
        requireReorderUnchanged(t, nonfinite, new double[16], identityInfos(16),
            "automatic ordering skips nonfinite matrix");

        double[] nonfiniteRhs = new double[16];
        nonfiniteRhs[7] = Double.NEGATIVE_INFINITY;
        requireReorderUnchanged(t, starMatrix(16, 0, 1), nonfiniteRhs, identityInfos(16),
            "automatic ordering skips nonfinite RHS");

        double[] tooLargeRhs = new double[16];
        tooLargeRhs[3] = -2e100;
        requireReorderUnchanged(t, starMatrix(16, 0, 1), tooLargeRhs, identityInfos(16),
            "automatic ordering skips RHS beyond 1e100");

        double[][] zero = new double[16][16];
        for (int i = 0; i < zero.length; i++) zero[i][i] = i + 1;
        requireReorderUnchanged(t, zero, new double[16], identityInfos(16),
            "automatic ordering leaves identity order unchanged");

        double[][] dense = new double[16][16];
        for (int row = 0; row < dense.length; row++)
            for (int column = 0; column < dense.length; column++)
                dense[row][column] = row == column ? 20 : 1;
        requireReorderUnchanged(t, dense, new double[16], identityInfos(16),
            "automatic ordering leaves tied dense identity unchanged");

        double[][] badShape = new double[16][];
        for (int i = 0; i < badShape.length; i++) badShape[i] = new double[i == 7 ? 15 : 16];
        requireReorderUnchanged(t, badShape, new double[16], identityInfos(16),
            "automatic ordering skips nonsquare input");

        RowInfo[] badInfos = identityInfos(16);
        badInfos[2].mapRow = badInfos[1].mapRow;
        requireReorderUnchanged(t, starMatrix(16, 0, 1), new double[16], badInfos,
            "automatic ordering skips invalid RowInfo maps");
    }

    private static void checkReorderedBoundary(Tally t, int n, double edgeValue, String label) {
        double[][] matrix = starMatrix(n, 0, edgeValue);
        double[] rhs = new double[n];
        RowInfo[] infos = identityInfos(n);
        int[] order = ReducedMatrixOrdering.minimumDegree(matrix);
        int[] expectedOrder = new int[n];
        for (int index = 0; index < n - 2; index++) expectedOrder[index] = index + 1;
        expectedOrder[n - 2] = 0;
        expectedOrder[n - 1] = n - 1;
        requireArray(t, order, expectedOrder, label + " literal minimum-degree tie order");
        int[] inverse = ReducedMatrixOrdering.inverse(order);
        State before = new State(matrix, rhs, infos);
        RowInfoState[] beforeInfos = snapshotInfo(infos);
        ReducedMatrixOrdering.Result result = ReducedMatrixOrdering.reorder(matrix, rhs, infos);
        t.check(result.changed, label + " reorders nonidentity star");
        t.check(result.matrix != matrix && result.rightSide != rhs,
            label + " returns detached reordered arrays");
        requireMatrixBits(t, result.matrix, permute(matrix, order), label + " matrix result");
        requireVectorBits(t, result.rightSide, permute(rhs, order), label + " RHS result");
        before.checkNumericArraysUnchanged(t, label + " leaves input arrays untouched");
        for (int i = 0; i < infos.length; i++) {
            t.check(infos[i].mapRow == inverse[i] && infos[i].mapCol == inverse[i],
                label + " maps original index through the inverse permutation");
            beforeInfos[i].checkMetadataUnchanged(t, infos[i], label + " preserves RowInfo fields");
        }
    }

    private static void checkZeroBoundary(Tally t) {
        double[][] matrix = new double[0][0];
        double[] rhs = new double[0];
        RowInfo[] infos = new RowInfo[0];
        ReducedMatrixOrdering.Result result =
            ReducedMatrixOrdering.apply(matrix, rhs, infos, new int[0]);
        t.check(!result.changed && result.matrix == matrix && result.rightSide == rhs,
            "apply supports zero-size unchanged boundary");
        t.check(ReducedMatrixOrdering.inverse(new int[0]).length == 0,
            "inverse supports zero-size boundary");
    }

    private static void expectApplyRejectedAtomically(Tally t, final double[][] matrix,
            final double[] rhs, final RowInfo[] infos, final int[] order, String label) {
        final State before = new State(matrix, rhs, infos);
        expectIllegalArgument(t, new BadCall() {
            public void run() { ReducedMatrixOrdering.apply(matrix, rhs, infos, order); }
        }, label);
        before.checkAllUnchanged(t, label + " preserves caller state");
    }

    private static void requireReorderUnchanged(Tally t, double[][] matrix, double[] rhs,
            RowInfo[] infos, String label) {
        State before = new State(matrix, rhs, infos);
        ReducedMatrixOrdering.Result result = ReducedMatrixOrdering.reorder(matrix, rhs, infos);
        t.check(!result.changed, label + " reports unchanged");
        t.check(result.matrix == matrix && result.rightSide == rhs,
            label + " preserves caller array identity");
        before.checkAllUnchanged(t, label + " preserves caller contents and maps");
    }

    private static void expectOrder(Tally t, double[][] matrix, int[] expected, String label) {
        requireArray(t, ReducedMatrixOrdering.minimumDegree(matrix), expected, label);
    }

    private static void requireArray(Tally t, int[] actual, int[] expected, String label) {
        t.check(actual != null && actual.length == expected.length, label + " length");
        if (actual == null || actual.length != expected.length) return;
        for (int i = 0; i < expected.length; i++)
            t.check(actual[i] == expected[i], label + " at " + i);
    }

    private static void requireMatrixBits(Tally t, double[][] actual, double[][] expected,
            String label) {
        t.check(actual != null && actual.length == expected.length, label + " rows");
        if (actual == null || actual.length != expected.length) return;
        for (int row = 0; row < expected.length; row++) {
            t.check(actual[row] != null && actual[row].length == expected[row].length,
                label + " row length " + row);
            if (actual[row] == null || actual[row].length != expected[row].length) continue;
            for (int column = 0; column < expected[row].length; column++)
                t.check(sameBits(actual[row][column], expected[row][column]),
                    label + " cell " + row + "," + column);
        }
    }

    private static void requireVectorBits(Tally t, double[] actual, double[] expected,
            String label) {
        t.check(actual != null && actual.length == expected.length, label + " length");
        if (actual == null || actual.length != expected.length) return;
        for (int i = 0; i < expected.length; i++)
            t.check(sameBits(actual[i], expected[i]), label + " at " + i);
    }

    private static void requireVectorNear(Tally t, double[] actual, double[] expected,
            double tolerance, String label) {
        t.check(actual != null && actual.length == expected.length, label + " length");
        if (actual == null || actual.length != expected.length) return;
        for (int i = 0; i < expected.length; i++)
            t.check(finite(actual[i]) && Math.abs(actual[i] - expected[i]) <= tolerance,
                label + " at " + i);
    }

    private static void expectIllegalArgument(Tally t, BadCall call, String label) {
        boolean rejected = false;
        try {
            call.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        t.check(rejected, label);
    }

    private static RowInfo[] identityInfos(int n) {
        RowInfo[] infos = new RowInfo[n];
        for (int i = 0; i < n; i++) {
            infos[i] = new RowInfo();
            infos[i].mapRow = i;
            infos[i].mapCol = i;
        }
        return infos;
    }

    private static RowInfo[] remapFixtureInfos() {
        RowInfo[] infos = new RowInfo[6];
        infos[0] = info(RowInfo.ROW_NORMAL, 2, 0, 1.25, true, false, false);
        infos[1] = info(RowInfo.ROW_NORMAL, -1, 3, -2.5, false, true, true);
        infos[2] = info(RowInfo.ROW_CONST, 0, -1, 9.25, true, true, false);
        infos[3] = info(RowInfo.ROW_NORMAL, -1, 2, -17.5, true, false, true);
        infos[4] = info(RowInfo.ROW_NORMAL, 3, 1, 0.125, false, false, false);
        infos[5] = info(RowInfo.ROW_CONST, 1, -1, -0.0, false, true, false);
        return infos;
    }

    private static RowInfo info(int type, int mapRow, int mapCol, double value,
            boolean rsChanges, boolean lsChanges, boolean dropRow) {
        RowInfo info = new RowInfo();
        info.type = type;
        info.mapRow = mapRow;
        info.mapCol = mapCol;
        info.value = value;
        info.rsChanges = rsChanges;
        info.lsChanges = lsChanges;
        info.dropRow = dropRow;
        return info;
    }

    private static RowInfoState[] snapshotInfo(RowInfo[] infos) {
        RowInfoState[] result = new RowInfoState[infos.length];
        for (int i = 0; i < infos.length; i++) result[i] = new RowInfoState(infos[i]);
        return result;
    }

    private static void checkInfoMetadataAndMaps(Tally t, RowInfo[] infos,
            RowInfoState[] before, String label) {
        for (int i = 0; i < infos.length; i++) {
            t.check(infos[i].mapRow == before[i].mapRow && infos[i].mapCol == before[i].mapCol,
                label + " maps at " + i);
            before[i].checkMetadataUnchanged(t, infos[i], label + " fields at " + i);
        }
    }

    private static double[][] starMatrix(int n, int center, double edgeValue) {
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) matrix[i][i] = 4 + i;
        for (int i = 0; i < n; i++) {
            if (i == center) continue;
            matrix[center][i] = edgeValue;
            matrix[i][center] = edgeValue;
        }
        return matrix;
    }

    private static double[][] permute(double[][] matrix, int[] order) {
        double[][] result = new double[order.length][order.length];
        for (int row = 0; row < order.length; row++)
            for (int column = 0; column < order.length; column++)
                result[row][column] = matrix[order[row]][order[column]];
        return result;
    }

    private static double[] permute(double[] rhs, int[] order) {
        double[] result = new double[order.length];
        for (int i = 0; i < order.length; i++) result[i] = rhs[order[i]];
        return result;
    }

    private static double[][] copyMatrix(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            result[row] = new double[matrix[row].length];
            for (int column = 0; column < matrix[row].length; column++)
                result[row][column] = matrix[row][column];
        }
        return result;
    }

    private static double[] copyVector(double[] vector) {
        double[] result = new double[vector.length];
        for (int i = 0; i < vector.length; i++) result[i] = vector[i];
        return result;
    }

    private static boolean originalCroutFactor(double[][] a, int[] pivots) {
        int n = a.length;
        for (int i = 0; i < n; i++) {
            boolean zero = true;
            for (int j = 0; j < n; j++) if (a[i][j] != 0) { zero = false; break; }
            if (zero) return false;
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < j; i++) {
                double value = a[i][j];
                for (int k = 0; k < i; k++) value -= a[i][k] * a[k][j];
                a[i][j] = value;
            }
            double largest = 0;
            int largestRow = -1;
            for (int i = j; i < n; i++) {
                double value = a[i][j];
                for (int k = 0; k < j; k++) value -= a[i][k] * a[k][j];
                a[i][j] = value;
                double magnitude = Math.abs(value);
                if (magnitude >= largest) {
                    largest = magnitude;
                    largestRow = i;
                }
            }
            if (j != largestRow) {
                for (int k = 0; k < n; k++) {
                    double swap = a[largestRow][k];
                    a[largestRow][k] = a[j][k];
                    a[j][k] = swap;
                }
            }
            pivots[j] = largestRow;
            if (a[j][j] == 0) a[j][j] = 1e-18;
            if (j != n - 1) {
                double multiplier = 1.0 / a[j][j];
                for (int i = j + 1; i < n; i++) a[i][j] *= multiplier;
            }
        }
        return true;
    }

    private static void originalCroutSolve(double[][] a, int[] pivots, double[] b) {
        int n = a.length;
        int i;
        for (i = 0; i != n; i++) {
            int row = pivots[i];
            double swap = b[row];
            b[row] = b[i];
            b[i] = swap;
            if (swap != 0) break;
        }
        int first = i++;
        for (; i < n; i++) {
            int row = pivots[i];
            double total = b[row];
            b[row] = b[i];
            for (int j = first; j < i; j++) total -= a[i][j] * b[j];
            b[i] = total;
        }
        for (i = n - 1; i >= 0; i--) {
            double total = b[i];
            for (int j = i + 1; j != n; j++) total -= a[i][j] * b[j];
            b[i] = total / a[i][i];
        }
    }

    private static double scaledResidual(double[][] matrix, double[] x, double[] rhs) {
        double maxResidual = 0;
        double normA = 0;
        double normX = 0;
        double normB = 0;
        for (int i = 0; i < x.length; i++) {
            normX = Math.max(normX, Math.abs(x[i]));
            normB = Math.max(normB, Math.abs(rhs[i]));
        }
        for (int row = 0; row < matrix.length; row++) {
            double ax = 0;
            double rowNorm = 0;
            for (int column = 0; column < matrix.length; column++) {
                ax += matrix[row][column] * x[column];
                rowNorm += Math.abs(matrix[row][column]);
            }
            normA = Math.max(normA, rowNorm);
            maxResidual = Math.max(maxResidual, Math.abs(ax - rhs[row]));
        }
        return maxResidual / (normA * normX + normB);
    }

    private static boolean finite(double value) {
        return SolverExecutionBoundary.finite(value);
    }

    private static boolean sameBits(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    private interface BadCall {
        void run();
    }

    private static final class Tally {
        int count;
        void check(boolean condition, String message) {
            count++;
            if (!condition) throw new AssertionError("ReducedMatrixOrderingChecks: " + message);
        }
    }

    private static final class RowInfoState {
        final int type, mapRow, mapCol;
        final long valueBits;
        final boolean rsChanges, lsChanges, dropRow;

        RowInfoState(RowInfo info) {
            type = info.type;
            mapRow = info.mapRow;
            mapCol = info.mapCol;
            valueBits = Double.doubleToLongBits(info.value);
            rsChanges = info.rsChanges;
            lsChanges = info.lsChanges;
            dropRow = info.dropRow;
        }

        void checkMetadataUnchanged(Tally t, RowInfo info, String label) {
            t.check(type == info.type, label + " type");
            t.check(valueBits == Double.doubleToLongBits(info.value), label + " value");
            t.check(rsChanges == info.rsChanges, label + " rsChanges");
            t.check(lsChanges == info.lsChanges, label + " lsChanges");
            t.check(dropRow == info.dropRow, label + " dropRow");
        }
    }

    private static final class State {
        final double[][] matrix;
        final double[][] rowReferences;
        final double[][] values;
        final double[] rhs;
        final double[] rhsValues;
        final RowInfo[] infos;
        final RowInfo[] infoReferences;
        final RowInfoState[] infoValues;

        State(double[][] matrix, double[] rhs, RowInfo[] infos) {
            this.matrix = matrix;
            this.rowReferences = matrix == null ? null : new double[matrix.length][];
            this.values = matrix == null ? null : new double[matrix.length][];
            if (matrix != null) {
                for (int row = 0; row < matrix.length; row++) {
                    rowReferences[row] = matrix[row];
                    if (matrix[row] != null) values[row] = copyVector(matrix[row]);
                }
            }
            this.rhs = rhs;
            this.rhsValues = rhs == null ? null : copyVector(rhs);
            this.infos = infos;
            this.infoReferences = infos == null ? null : new RowInfo[infos.length];
            this.infoValues = infos == null ? null : new RowInfoState[infos.length];
            if (infos != null) {
                for (int i = 0; i < infos.length; i++) {
                    infoReferences[i] = infos[i];
                    if (infos[i] != null) infoValues[i] = new RowInfoState(infos[i]);
                }
            }
        }

        void checkNumericArraysUnchanged(Tally t, String label) {
            if (matrix != null) {
                t.check(matrix.length == rowReferences.length, label + " matrix row count");
                if (matrix.length == rowReferences.length) {
                    for (int row = 0; row < matrix.length; row++) {
                        t.check(matrix[row] == rowReferences[row], label + " row reference " + row);
                        if (matrix[row] != null && values[row] != null)
                            requireVectorBits(t, matrix[row], values[row], label + " row " + row);
                    }
                }
            }
            t.check(rhs == this.rhs, label + " RHS reference");
            if (rhs != null && rhsValues != null)
                requireVectorBits(t, rhs, rhsValues, label + " RHS values");
        }

        void checkAllUnchanged(Tally t, String label) {
            checkNumericArraysUnchanged(t, label);
            t.check(infos == this.infos, label + " RowInfo array reference");
            if (infos != null && infos.length == infoReferences.length) {
                for (int i = 0; i < infos.length; i++) {
                    t.check(infos[i] == infoReferences[i], label + " RowInfo reference " + i);
                    if (infos[i] != null && infoValues[i] != null) {
                        RowInfoState before = infoValues[i];
                        t.check(before.mapRow == infos[i].mapRow, label + " mapRow " + i);
                        t.check(before.mapCol == infos[i].mapCol, label + " mapCol " + i);
                        before.checkMetadataUnchanged(t, infos[i], label + " metadata " + i);
                    }
                }
            }
        }
    }
}
