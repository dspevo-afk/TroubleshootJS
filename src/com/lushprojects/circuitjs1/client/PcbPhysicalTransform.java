package com.lushprojects.circuitjs1.client;

/** Physical board face. Electrical terminal identity never depends on this enum. */
enum PcbBoardSide {
    TOP,
    BOTTOM;

    PcbBoardSide opposite() { return this == TOP ? BOTTOM : TOP; }
}

/** Package-declared cardinal board rotation. Continuous pose is deliberately unsupported. */
enum PcbRotation {
    DEG_0,
    DEG_90,
    DEG_180,
    DEG_270;

    int placedWidth(int width, int height) {
        PcbCoordinateSystem.requireLocalDimensions(width, height);
        return this == DEG_90 || this == DEG_270 ? height : width;
    }

    int placedHeight(int width, int height) {
        PcbCoordinateSystem.requireLocalDimensions(width, height);
        return this == DEG_90 || this == DEG_270 ? width : height;
    }

    Point rotatePoint(Point value, int width, int height) {
        PcbCoordinateSystem.requireLocalDimensions(width, height);
        if (value == null)
            throw new IllegalArgumentException("Invalid PCB rotation input");
        if (this == DEG_90)
            return new Point(PcbCoordinateSystem.checkedInt((long) height - value.y), value.x);
        if (this == DEG_180)
            return new Point(PcbCoordinateSystem.checkedInt((long) width - value.x),
                PcbCoordinateSystem.checkedInt((long) height - value.y));
        if (this == DEG_270)
            return new Point(value.y, PcbCoordinateSystem.checkedInt((long) width - value.x));
        return new Point(value);
    }

    Rectangle rotateRectangle(Rectangle value, int width, int height) {
        PcbCoordinateSystem.requireLocalDimensions(width, height);
        if (value == null || value.width <= 0 || value.height <= 0)
            throw new IllegalArgumentException("Invalid PCB rotation rectangle");
        if (this == DEG_90)
            return new Rectangle(PcbCoordinateSystem.checkedInt((long) height - value.y - value.height),
                value.x, value.height, value.width);
        if (this == DEG_180)
            return new Rectangle(PcbCoordinateSystem.checkedInt((long) width - value.x - value.width),
                PcbCoordinateSystem.checkedInt((long) height - value.y - value.height),
                value.width, value.height);
        if (this == DEG_270)
            return new Rectangle(value.y,
                PcbCoordinateSystem.checkedInt((long) width - value.x - value.width),
                value.height, value.width);
        return new Rectangle(value);
    }

    Point rotateDirection(int dx, int dy) {
        PcbCoordinateSystem.requireCardinalDirection(dx, dy);
        if (this == DEG_90) return new Point(-dy, dx);
        if (this == DEG_180) return new Point(-dx, -dy);
        if (this == DEG_270) return new Point(dy, -dx);
        return new Point(dx, dy);
    }

    Point inversePoint(Point value, int width, int height) {
        PcbCoordinateSystem.requireLocalDimensions(width, height);
        if (value == null)
            throw new IllegalArgumentException("Invalid PCB inverse rotation input");
        if (this == DEG_90)
            return new Point(value.y, PcbCoordinateSystem.checkedInt((long) height - value.x));
        if (this == DEG_180)
            return new Point(PcbCoordinateSystem.checkedInt((long) width - value.x),
                PcbCoordinateSystem.checkedInt((long) height - value.y));
        if (this == DEG_270)
            return new Point(PcbCoordinateSystem.checkedInt((long) width - value.y), value.x);
        return new Point(value);
    }
}

/**
 * Board-space coordinates are deterministic signed integer logical units.
 * They are not millimetres and must never be inferred from screen pixels.
 */
final class PcbCoordinateSystem {
    static final int MAX_ABS_BOARD_COORDINATE = 100000000;
    static final String UNIT_DESCRIPTION = "signed integer logical board unit";

    private PcbCoordinateSystem() { }

    static int requireBoardCoordinate(long value) {
        if (value < -MAX_ABS_BOARD_COORDINATE || value > MAX_ABS_BOARD_COORDINATE)
            throw new IllegalArgumentException("PCB board coordinate is out of contract: " + value);
        return (int)value;
    }

    // Absolute coordinates are bounded; a displacement can span the entire domain.
    static int checkedAddBoardCoordinate(int coordinate, int displacement) {
        requireBoardCoordinate(coordinate);
        return requireBoardCoordinate((long)coordinate + displacement);
    }

    static int boardDisplacement(int destination, int origin) {
        requireBoardCoordinate(destination);
        requireBoardCoordinate(origin);
        return checkedInt((long)destination - origin);
    }

    static void requireBoardPoint(Point point) {
        if (point == null) throw new IllegalArgumentException("Missing PCB board point");
        requireBoardCoordinate(point.x);
        requireBoardCoordinate(point.y);
    }

    static void requireBoardRectangle(Rectangle rectangle) {
        if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0)
            throw new IllegalArgumentException("Invalid PCB board rectangle");
        requireBoardCoordinate(rectangle.x);
        requireBoardCoordinate(rectangle.y);
        requireBoardCoordinate((long)rectangle.x + rectangle.width);
        requireBoardCoordinate((long)rectangle.y + rectangle.height);
    }

    // Local extents/deltas are not absolute positions and may span both signs.
    static void requireLocalDimensions(int width, int height) {
        long span = 2L * MAX_ABS_BOARD_COORDINATE;
        if (width <= 0 || height <= 0 || width > span || height > span)
            throw new IllegalArgumentException("Invalid PCB local dimensions");
    }

    static void requireCardinalDirection(int dx, int dy) {
        if (Math.abs((long)dx) + Math.abs((long)dy) > 1)
            throw new IllegalArgumentException("PCB escape direction must be cardinal");
    }

    static int checkedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("PCB coordinate arithmetic overflow: " + value);
        return (int)value;
    }
}

/** Immutable mount: bottom reflects local X BEFORE rotation; view flip is separate. */
final class PcbPackagePose {
    private final int x;
    private final int y;
    private final PcbRotation rotation;
    private final PcbBoardSide mountingSide;

    PcbPackagePose(int x, int y, PcbRotation rotation, PcbBoardSide mountingSide) {
        if (rotation == null || mountingSide == null)
            throw new IllegalArgumentException("Incomplete PCB package pose");
        this.x = PcbCoordinateSystem.requireBoardCoordinate(x);
        this.y = PcbCoordinateSystem.requireBoardCoordinate(y);
        this.rotation = rotation;
        this.mountingSide = mountingSide;
    }

    static PcbPackagePose top(int x, int y) {
        return new PcbPackagePose(x, y, PcbRotation.DEG_0, PcbBoardSide.TOP);
    }

    int getX() { return x; }
    int getY() { return y; }
    PcbRotation getRotation() { return rotation; }
    PcbBoardSide getMountingSide() { return mountingSide; }

    int getPlacedWidth(int localWidth, int localHeight) {
        return rotation.placedWidth(localWidth, localHeight);
    }

    int getPlacedHeight(int localWidth, int localHeight) {
        return rotation.placedHeight(localWidth, localHeight);
    }

    Point toBoardPoint(Point local, int localWidth, int localHeight) {
        Point rotated = rotation.rotatePoint(mountPoint(local, localWidth),
            localWidth, localHeight);
        return new Point(PcbCoordinateSystem.checkedAddBoardCoordinate(x, rotated.x),
            PcbCoordinateSystem.checkedAddBoardCoordinate(y, rotated.y));
    }

    Rectangle toBoardRectangle(Rectangle local, int localWidth, int localHeight) {
        if (local == null || local.width <= 0 || local.height <= 0)
            throw new IllegalArgumentException("Invalid PCB local rectangle");
        Rectangle mounted = mountingSide == PcbBoardSide.TOP ? new Rectangle(local) :
            new Rectangle(PcbCoordinateSystem.checkedInt((long)localWidth - local.x - local.width),
                local.y, local.width, local.height);
        Rectangle rotated = rotation.rotateRectangle(mounted, localWidth, localHeight);
        int boardX = PcbCoordinateSystem.checkedAddBoardCoordinate(x, rotated.x);
        int boardY = PcbCoordinateSystem.checkedAddBoardCoordinate(y, rotated.y);
        PcbCoordinateSystem.requireBoardCoordinate((long)boardX + rotated.width);
        PcbCoordinateSystem.requireBoardCoordinate((long)boardY + rotated.height);
        return new Rectangle(boardX, boardY, rotated.width, rotated.height);
    }

    Point toLocalPoint(Point board, int localWidth, int localHeight) {
        PcbCoordinateSystem.requireBoardPoint(board);
        Point rotated = new Point(PcbCoordinateSystem.boardDisplacement(board.x, x),
            PcbCoordinateSystem.boardDisplacement(board.y, y));
        return mountPoint(rotation.inversePoint(rotated, localWidth, localHeight), localWidth);
    }

    Point toBoardDirection(int dx, int dy) {
        PcbCoordinateSystem.requireCardinalDirection(dx, dy);
        return rotation.rotateDirection(mountingSide == PcbBoardSide.BOTTOM ? -dx : dx, dy);
    }

    private Point mountPoint(Point local, int localWidth) {
        if (local == null) throw new IllegalArgumentException("Missing PCB local point");
        return mountingSide == PcbBoardSide.TOP ? new Point(local) :
            new Point(PcbCoordinateSystem.checkedInt((long)localWidth - local.x), local.y);
    }

    PcbPackagePose translatedTo(int newX, int newY) {
        return new PcbPackagePose(newX, newY, rotation, mountingSide);
    }

    String fingerprint() {
        return x + "," + y + "|rotation=" + rotation + "|mount=" + mountingSide;
    }
}

/**
 * Read-only board-view transform. A bottom view mirrors board-space X for display only;
 * it never changes package pose, pad IDs, terminal IDs or electrical bindings.
 */
final class PcbBoardViewTransform {
    private final Rectangle boardOutline;
    private final PcbBoardSide viewedSide;

    PcbBoardViewTransform(Rectangle boardOutline, PcbBoardSide viewedSide) {
        if (boardOutline == null || boardOutline.width <= 0 || boardOutline.height <= 0 ||
                viewedSide == null)
            throw new IllegalArgumentException("Invalid PCB board view transform");
        PcbCoordinateSystem.requireBoardRectangle(boardOutline);
        this.boardOutline = new Rectangle(boardOutline);
        this.viewedSide = viewedSide;
    }

    PcbBoardSide getViewedSide() { return viewedSide; }
    Rectangle getBoardOutline() { return new Rectangle(boardOutline); }

    Point boardToView(Point board) {
        if (board == null)
            return null;
        PcbCoordinateSystem.requireBoardPoint(board);
        if (viewedSide == PcbBoardSide.TOP)
            return new Point(board);
        long mirroredX = (long)boardOutline.x * 2L + boardOutline.width - board.x;
        return new Point(PcbCoordinateSystem.requireBoardCoordinate(mirroredX), board.y);
    }

    Point viewToBoard(Point view) { return boardToView(view); }

    Rectangle boardToView(Rectangle board) {
        if (board == null)
            return null;
        PcbCoordinateSystem.requireBoardRectangle(board);
        if (viewedSide == PcbBoardSide.TOP)
            return new Rectangle(board);
        long mirroredX = (long)boardOutline.x * 2L + boardOutline.width -
            ((long)board.x + board.width);
        Rectangle result = new Rectangle(PcbCoordinateSystem.requireBoardCoordinate(mirroredX),
            board.y, board.width, board.height);
        PcbCoordinateSystem.requireBoardRectangle(result);
        return result;
    }

    Rectangle viewToBoard(Rectangle view) { return boardToView(view); }
}
