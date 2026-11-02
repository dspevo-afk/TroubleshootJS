package com.lushprojects.circuitjs1.client;

/**
 * Generic symbolic ordering for an already reduced nonlinear CircuitJS matrix.
 * The matrix is treated as an undirected structural graph using the union of
 * its off-diagonal A[i][j] and A[j][i] nonzero patterns.
 */
final class ReducedMatrixOrdering {
    private static final int MIN_PRODUCTION_SIZE = 16;
    private static final int MAX_SIZE = 128;
    private static final double PRODUCTION_ENTRY_LIMIT = 1e100;

    static final class Result {
        final double[][] matrix;
        final double[] rightSide;
        final boolean changed;

        private Result(double[][] matrix, double[] rightSide, boolean changed) {
            this.matrix = matrix;
            this.rightSide = rightSide;
            this.changed = changed;
        }
    }

    private ReducedMatrixOrdering() {
    }

    /**
     * Return order[newIndex] = originalIndex using greedy minimum degree,
     * filling each selected vertex's remaining neighbors into a clique.
     * Equal degrees retain the lowest original index.
     */
    static int[] minimumDegree(double[][] matrix) {
        int n = validateMatrix(matrix);
        boolean[][] adjacent = new boolean[n][n];
        for (int row = 0; row < n; row++) {
            for (int column = row + 1; column < n; column++) {
                if (matrix[row][column] != 0 || matrix[column][row] != 0) {
                    adjacent[row][column] = true;
                    adjacent[column][row] = true;
                }
            }
        }

        int[] order = new int[n];
        int[] neighbors = new int[n];
        boolean[] eliminated = new boolean[n];
        for (int position = 0; position < n; position++) {
            int selected = -1;
            int selectedDegree = n + 1;
            for (int vertex = 0; vertex < n; vertex++) {
                if (eliminated[vertex]) continue;
                int degree = 0;
                for (int candidate = 0; candidate < n; candidate++)
                    if (!eliminated[candidate] && adjacent[vertex][candidate]) degree++;
                if (degree < selectedDegree) {
                    selected = vertex;
                    selectedDegree = degree;
                }
            }
            if (selected < 0)
                throw new IllegalArgumentException("incomplete reduced-matrix ordering");

            order[position] = selected;
            int neighborCount = 0;
            for (int candidate = 0; candidate < n; candidate++)
                if (!eliminated[candidate] && candidate != selected &&
                        adjacent[selected][candidate])
                    neighbors[neighborCount++] = candidate;
            for (int left = 0; left < neighborCount; left++) {
                for (int right = left + 1; right < neighborCount; right++) {
                    int first = neighbors[left];
                    int second = neighbors[right];
                    adjacent[first][second] = true;
                    adjacent[second][first] = true;
                }
            }
            eliminated[selected] = true;
        }
        return order;
    }

    /** Return inverse[originalIndex] = newIndex, rejecting non-bijections. */
    static int[] inverse(int[] order) {
        if (order == null || order.length > MAX_SIZE)
            throw new IllegalArgumentException("invalid reduced-matrix permutation size");
        int n = order.length;
        int[] inverse = new int[n];
        boolean[] seen = new boolean[n];
        for (int newIndex = 0; newIndex < n; newIndex++) {
            int originalIndex = order[newIndex];
            if (originalIndex < 0 || originalIndex >= n || seen[originalIndex])
                throw new IllegalArgumentException("reduced-matrix order is not bijective");
            seen[originalIndex] = true;
            inverse[originalIndex] = newIndex;
        }
        return inverse;
    }

    /**
     * Apply order[newIndex] = originalIndex to A and b, and remap all active
     * RowInfo coordinates. All inputs and replacement arrays are validated or
     * built before any RowInfo field is changed.
     */
    static Result apply(double[][] matrix, double[] rightSide, RowInfo[] infos,
            int[] order) {
        int n = validateMatrix(matrix);
        validateRightSide(rightSide, n);
        validateRowInfoMaps(infos, n);
        if (order == null || order.length != n)
            throw new IllegalArgumentException("reduced-matrix order length mismatch");
        int[] inverse = inverse(order);

        boolean identity = true;
        for (int index = 0; index < n; index++)
            if (order[index] != index) {
                identity = false;
                break;
            }
        if (identity) return new Result(matrix, rightSide, false);

        int[] mappedRows = new int[infos.length];
        int[] mappedColumns = new int[infos.length];
        for (int index = 0; index < infos.length; index++) {
            RowInfo info = infos[index];
            mappedRows[index] = info.mapRow < 0 ? -1 : inverse[info.mapRow];
            mappedColumns[index] = info.mapCol < 0 ? -1 : inverse[info.mapCol];
        }

        double[][] orderedMatrix = new double[n][n];
        double[] orderedRightSide = new double[n];
        for (int row = 0; row < n; row++) {
            int originalRow = order[row];
            orderedRightSide[row] = rightSide[originalRow];
            for (int column = 0; column < n; column++)
                orderedMatrix[row][column] = matrix[originalRow][order[column]];
        }

        Result result = new Result(orderedMatrix, orderedRightSide, true);
        for (int index = 0; index < infos.length; index++) {
            infos[index].mapRow = mappedRows[index];
            infos[index].mapCol = mappedColumns[index];
        }
        return result;
    }

    /** Apply the production eligibility bounds; unsupported inputs are untouched. */
    static Result reorder(double[][] matrix, double[] rightSide, RowInfo[] infos) {
        if (!eligible(matrix, rightSide, infos))
            return new Result(matrix, rightSide, false);
        int[] order = minimumDegree(matrix);
        return apply(matrix, rightSide, infos, order);
    }

    private static int validateMatrix(double[][] matrix) {
        if (matrix == null || matrix.length > MAX_SIZE)
            throw new IllegalArgumentException("invalid reduced-matrix size");
        int n = matrix.length;
        for (int row = 0; row < n; row++) {
            if (matrix[row] == null || matrix[row].length != n)
                throw new IllegalArgumentException("reduced matrix must be square");
            for (int earlier = 0; earlier < row; earlier++)
                if (matrix[row] == matrix[earlier])
                    throw new IllegalArgumentException("reduced matrix rows must be distinct");
            for (int column = 0; column < n; column++)
                if (!SolverExecutionBoundary.finite(matrix[row][column]))
                    throw new IllegalArgumentException("reduced matrix must be finite");
        }
        return n;
    }

    private static void validateRightSide(double[] rightSide, int n) {
        if (rightSide == null || rightSide.length != n)
            throw new IllegalArgumentException("reduced right side length mismatch");
        for (int index = 0; index < n; index++)
            if (!SolverExecutionBoundary.finite(rightSide[index]))
                throw new IllegalArgumentException("reduced right side must be finite");
    }

    private static void validateRowInfoMaps(RowInfo[] infos, int n) {
        if (!validRowInfoMaps(infos, n))
            throw new IllegalArgumentException("invalid reduced RowInfo maps");
    }

    private static boolean validRowInfoMaps(RowInfo[] infos, int n) {
        if (infos == null || infos.length < n) return false;
        boolean[] seenRows = new boolean[n];
        boolean[] seenColumns = new boolean[n];
        int rowCount = 0;
        int columnCount = 0;
        for (int index = 0; index < infos.length; index++) {
            RowInfo info = infos[index];
            if (info == null || info.mapRow < -1 || info.mapRow >= n ||
                    info.mapCol < -1 || info.mapCol >= n)
                return false;
            if (info.dropRow != (info.mapRow == -1)) return false;
            if (info.mapRow >= 0) {
                if (seenRows[info.mapRow]) return false;
                seenRows[info.mapRow] = true;
                rowCount++;
            }
            if (info.type == RowInfo.ROW_CONST) {
                if (info.mapCol != -1) return false;
            } else if (info.type == RowInfo.ROW_NORMAL) {
                if (info.mapCol < 0 || seenColumns[info.mapCol]) return false;
                seenColumns[info.mapCol] = true;
                columnCount++;
            } else {
                return false;
            }
        }
        return rowCount == n && columnCount == n;
    }

    private static boolean eligible(double[][] matrix, double[] rightSide, RowInfo[] infos) {
        if (matrix == null) return false;
        int n = matrix.length;
        if (n < MIN_PRODUCTION_SIZE || n > MAX_SIZE || rightSide == null ||
                rightSide.length != n || infos == null || infos.length < n)
            return false;
        for (int row = 0; row < n; row++) {
            if (matrix[row] == null || matrix[row].length != n) return false;
            for (int earlier = 0; earlier < row; earlier++)
                if (matrix[row] == matrix[earlier]) return false;
            for (int column = 0; column < n; column++) {
                double value = matrix[row][column];
                if (!SolverExecutionBoundary.finite(value) ||
                        Math.abs(value) > PRODUCTION_ENTRY_LIMIT)
                    return false;
            }
        }
        for (int index = 0; index < n; index++) {
            double value = rightSide[index];
            if (!SolverExecutionBoundary.finite(value) ||
                    Math.abs(value) > PRODUCTION_ENTRY_LIMIT)
                return false;
        }
        return validRowInfoMaps(infos, n);
    }
}
