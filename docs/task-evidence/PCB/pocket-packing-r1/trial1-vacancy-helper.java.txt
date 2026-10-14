package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Finds a small set of advisory component origins in the largest coarse free
 * envelope pocket. Existing placement and routing checks remain authoritative.
 */
public final class PcbVacancyTargets {
    private static final int CELL_SIZE = 40;
    private static final int MAX_GRID_CELLS = 20000;

    private PcbVacancyTargets() { }

    /**
     * Returns the center and corner origins for the largest conservative free
     * grid rectangle that can contain {@code sourceEnvelope}.
     *
     * <p>The 40-unit grid is anchored at {@code allowed.x/y}, so translating
     * every input by the same amount translates every returned point equally.
     * A partial edge cell and every cell with positive-area obstacle overlap
     * are blocked. The result is advisory; callers still perform exact fits.
     */
    public static Vector<Point> targets(Rectangle allowed,
            Vector<Rectangle> obstacles, Rectangle sourceEnvelope,
            Point sourceOrigin) {
        if (allowed == null || obstacles == null || sourceEnvelope == null ||
                sourceOrigin == null) {
            throw new IllegalArgumentException("vacancy inputs must be non-null");
        }
        for (int i = 0; i < obstacles.size(); i++) {
            if (obstacles.get(i) == null) {
                throw new IllegalArgumentException("vacancy obstacles must be non-null");
            }
        }

        Vector<Point> none = new Vector<Point>();
        if (allowed.width <= 0 || allowed.height <= 0 ||
                sourceEnvelope.width <= 0 || sourceEnvelope.height <= 0) {
            return none;
        }
        for (int i = 0; i < obstacles.size(); i++) {
            Rectangle obstacle = obstacles.get(i);
            if (obstacle.width <= 0 || obstacle.height <= 0) {
                return none;
            }
            long obstacleRight = checkedAdd(obstacle.x, obstacle.width);
            long obstacleBottom = checkedAdd(obstacle.y, obstacle.height);
            if (isOverflow(obstacleRight) || isOverflow(obstacleBottom) ||
                    !fitsInt(obstacleRight) || !fitsInt(obstacleBottom)) {
                return none;
            }
        }

        long allowedRight = checkedAdd(allowed.x, allowed.width);
        long allowedBottom = checkedAdd(allowed.y, allowed.height);
        long sourceRight = checkedAdd(sourceEnvelope.x, sourceEnvelope.width);
        long sourceBottom = checkedAdd(sourceEnvelope.y, sourceEnvelope.height);
        if (isOverflow(allowedRight) || isOverflow(allowedBottom) ||
                isOverflow(sourceRight) || isOverflow(sourceBottom) ||
                !fitsInt(allowedRight) || !fitsInt(allowedBottom) ||
                !fitsInt(sourceRight) || !fitsInt(sourceBottom)) {
            return none;
        }

        long columnsLong = ceilDiv(allowed.width, CELL_SIZE);
        long rowsLong = ceilDiv(allowed.height, CELL_SIZE);
        long cellCount = checkedMultiply(columnsLong, rowsLong);
        if (isOverflow(cellCount) || columnsLong <= 0 || rowsLong <= 0 ||
                columnsLong > Integer.MAX_VALUE || rowsLong > Integer.MAX_VALUE ||
                cellCount > MAX_GRID_CELLS) {
            return none;
        }
        int columns = (int) columnsLong;
        int rows = (int) rowsLong;

        long minimumColumns = ceilDiv(sourceEnvelope.width, CELL_SIZE);
        long minimumRows = ceilDiv(sourceEnvelope.height, CELL_SIZE);
        if (minimumColumns > columns || minimumRows > rows) {
            return none;
        }

        long strideLong = (long) columns + 1L;
        long differenceLength = checkedMultiply((long) rows + 1L, strideLong);
        if (isOverflow(differenceLength) || differenceLength > Integer.MAX_VALUE) {
            return none;
        }
        long[] obstacleCoverage = new long[(int) differenceLength];

        for (int i = 0; i < obstacles.size(); i++) {
            Rectangle obstacle = obstacles.get(i);
            long obstacleRight = checkedAdd(obstacle.x, obstacle.width);
            long obstacleBottom = checkedAdd(obstacle.y, obstacle.height);
            long relativeLeft = checkedSubtract(obstacle.x, allowed.x);
            long relativeRight = checkedSubtract(obstacleRight, allowed.x);
            long relativeTop = checkedSubtract(obstacle.y, allowed.y);
            long relativeBottom = checkedSubtract(obstacleBottom, allowed.y);
            if (isOverflow(obstacleRight) || isOverflow(obstacleBottom) ||
                    isOverflow(relativeLeft) || isOverflow(relativeRight) ||
                    isOverflow(relativeTop) || isOverflow(relativeBottom)) {
                return none;
            }

            int firstColumn = clampGrid(floorDiv(relativeLeft, CELL_SIZE), columns);
            int afterColumn = clampGrid(ceilDiv(relativeRight, CELL_SIZE), columns);
            int firstRow = clampGrid(floorDiv(relativeTop, CELL_SIZE), rows);
            int afterRow = clampGrid(ceilDiv(relativeBottom, CELL_SIZE), rows);
            if (firstColumn >= afterColumn || firstRow >= afterRow) {
                continue;
            }
            addDifference(obstacleCoverage, columns + 1,
                    firstRow, firstColumn, 1L);
            addDifference(obstacleCoverage, columns + 1,
                    firstRow, afterColumn, -1L);
            addDifference(obstacleCoverage, columns + 1,
                    afterRow, firstColumn, -1L);
            addDifference(obstacleCoverage, columns + 1,
                    afterRow, afterColumn, 1L);
        }

        boolean[] blocked = new boolean[(int) cellCount];
        boolean partialRightCell = allowed.width % CELL_SIZE != 0;
        boolean partialBottomCell = allowed.height % CELL_SIZE != 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if ((partialRightCell && column == columns - 1) ||
                        (partialBottomCell && row == rows - 1)) {
                    blocked[index] = true;
                }

                int differenceIndex = row * (columns + 1) + column;
                long coverage = obstacleCoverage[differenceIndex];
                if (row > 0) {
                    coverage += obstacleCoverage[differenceIndex - columns - 1];
                }
                if (column > 0) {
                    coverage += obstacleCoverage[differenceIndex - 1];
                }
                if (row > 0 && column > 0) {
                    coverage -= obstacleCoverage[differenceIndex - columns - 2];
                }
                obstacleCoverage[differenceIndex] = coverage;
                if (coverage > 0) {
                    blocked[index] = true;
                }
            }
        }

        Pocket pocket = largestPocket(blocked, columns, rows,
                (int) minimumColumns, (int) minimumRows);
        if (pocket == null) {
            return none;
        }

        long pocketLeft = checkedAdd(allowed.x,
                checkedMultiply(pocket.left, CELL_SIZE));
        long pocketTop = checkedAdd(allowed.y,
                checkedMultiply(pocket.top, CELL_SIZE));
        long pocketWidth = checkedMultiply(pocket.width, CELL_SIZE);
        long pocketHeight = checkedMultiply(pocket.height, CELL_SIZE);
        long pocketRight = checkedAdd(pocketLeft, pocketWidth);
        long pocketBottom = checkedAdd(pocketTop, pocketHeight);
        if (isOverflow(pocketLeft) || isOverflow(pocketTop) ||
                isOverflow(pocketWidth) || isOverflow(pocketHeight) ||
                isOverflow(pocketRight) || isOverflow(pocketBottom)) {
            return none;
        }

        long rightmostEnvelopeLeft = checkedSubtract(pocketRight, sourceEnvelope.width);
        long bottommostEnvelopeTop = checkedSubtract(pocketBottom, sourceEnvelope.height);
        long centerDeltaX = checkedSubtract(rightmostEnvelopeLeft, pocketLeft);
        long centerDeltaY = checkedSubtract(bottommostEnvelopeTop, pocketTop);
        long centerEnvelopeLeft = checkedAdd(pocketLeft,
                centerDeltaX / 2L);
        long centerEnvelopeTop = checkedAdd(pocketTop,
                centerDeltaY / 2L);
        if (isOverflow(rightmostEnvelopeLeft) || isOverflow(bottommostEnvelopeTop) ||
                isOverflow(centerDeltaX) || isOverflow(centerDeltaY) ||
                isOverflow(centerEnvelopeLeft) || isOverflow(centerEnvelopeTop)) {
            return none;
        }

        long sourceOffsetX = checkedSubtract(sourceEnvelope.x, sourceOrigin.x);
        long sourceOffsetY = checkedSubtract(sourceEnvelope.y, sourceOrigin.y);
        if (isOverflow(sourceOffsetX) || isOverflow(sourceOffsetY)) {
            return none;
        }

        long[] envelopeLefts = new long[] { centerEnvelopeLeft, pocketLeft,
                rightmostEnvelopeLeft, pocketLeft, rightmostEnvelopeLeft };
        long[] envelopeTops = new long[] { centerEnvelopeTop, pocketTop,
                pocketTop, bottommostEnvelopeTop, bottommostEnvelopeTop };
        Vector<Point> result = new Vector<Point>();
        for (int i = 0; i < envelopeLefts.length; i++) {
            long envelopeRight = checkedAdd(envelopeLefts[i], sourceEnvelope.width);
            long envelopeBottom = checkedAdd(envelopeTops[i], sourceEnvelope.height);
            long originX = checkedSubtract(envelopeLefts[i], sourceOffsetX);
            long originY = checkedSubtract(envelopeTops[i], sourceOffsetY);
            if (!fitsInt(envelopeLefts[i]) || !fitsInt(envelopeTops[i]) ||
                    isOverflow(envelopeRight) || isOverflow(envelopeBottom) ||
                    !fitsInt(envelopeRight) || !fitsInt(envelopeBottom) ||
                    isOverflow(originX) || isOverflow(originY) ||
                    !fitsInt(originX) || !fitsInt(originY)) {
                continue;
            }
            Point point = new Point((int) originX, (int) originY);
            if (!result.contains(point)) {
                result.add(point);
            }
        }
        return result;
    }

    private static Pocket largestPocket(boolean[] blocked, int columns, int rows,
            int minimumColumns, int minimumRows) {
        int[] heights = new int[columns];
        int[] stack = new int[columns];
        long bestArea = -1L;
        Pocket best = null;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                heights[column] = blocked[index] ? 0 : heights[column] + 1;
            }

            int stackSize = 0;
            for (int column = 0; column <= columns; column++) {
                int currentHeight = column == columns ? 0 : heights[column];
                while (stackSize > 0 && heights[stack[stackSize - 1]] > currentHeight) {
                    int height = heights[stack[--stackSize]];
                    int left = stackSize == 0 ? 0 : stack[stackSize - 1] + 1;
                    int width = column - left;
                    if (height < minimumRows || width < minimumColumns) {
                        continue;
                    }

                    long area = checkedMultiply(width, height);
                    int top = row - height + 1;
                    if (isOverflow(area)) {
                        continue;
                    }
                    if (best == null || area > bestArea ||
                            (area == bestArea && precedes(top, left, width, best))) {
                        bestArea = area;
                        best = new Pocket(left, top, width, height);
                    }
                }
                if (column < columns) {
                    stack[stackSize++] = column;
                }
            }
        }
        return best;
    }

    /** Equal-area rectangles sort by top, then left, then smaller width. */
    private static boolean precedes(int top, int left, int width, Pocket prior) {
        if (top != prior.top) {
            return top < prior.top;
        }
        if (left != prior.left) {
            return left < prior.left;
        }
        return width < prior.width;
    }

    private static void addDifference(long[] difference, int stride,
            int row, int column, long amount) {
        difference[row * stride + column] += amount;
    }

    private static int clampGrid(long value, int limit) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= limit) {
            return limit;
        }
        return (int) value;
    }

    private static long floorDiv(long value, long divisor) {
        long quotient = value / divisor;
        long remainder = value % divisor;
        return remainder < 0L ? quotient - 1L : quotient;
    }

    private static long ceilDiv(long value, long divisor) {
        long quotient = value / divisor;
        long remainder = value % divisor;
        return remainder > 0L ? quotient + 1L : quotient;
    }

    private static long checkedAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0L) {
            return Long.MIN_VALUE;
        }
        return result;
    }

    private static long checkedSubtract(long left, long right) {
        long result = left - right;
        if (((left ^ right) & (left ^ result)) < 0L) {
            return Long.MIN_VALUE;
        }
        return result;
    }

    private static long checkedMultiply(long left, long right) {
        if (left < 0L || right < 0L) {
            return Long.MIN_VALUE;
        }
        if (left != 0L && right > Long.MAX_VALUE / left) {
            return Long.MIN_VALUE;
        }
        return left * right;
    }

    private static boolean isOverflow(long value) {
        return value == Long.MIN_VALUE;
    }

    private static boolean fitsInt(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static final class Pocket {
        final int left;
        final int top;
        final int width;
        final int height;

        Pocket(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }
}
