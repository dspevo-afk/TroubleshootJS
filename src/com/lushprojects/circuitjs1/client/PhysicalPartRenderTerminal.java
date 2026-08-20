package com.lushprojects.circuitjs1.client;

/** Provider-owned physical lead geometry for one terminal. */
final class PhysicalPartRenderTerminal {
    private final int terminalIndex;
    private final String terminalId;
    private final String boardPadId;
    private final Point point;
    private final Rectangle probeBounds;
    private final Rectangle padBounds;
    private final Rectangle leadBounds;

    PhysicalPartRenderTerminal(int terminalIndex, String terminalId, String boardPadId,
            Point point, Rectangle probeBounds, Rectangle padBounds, Rectangle leadBounds) {
        if (terminalIndex < 0 || terminalId == null || terminalId.length() == 0 ||
                point == null || probeBounds == null || padBounds == null || leadBounds == null ||
                probeBounds.width <= 0 || probeBounds.height <= 0 || padBounds.width <= 0 ||
                padBounds.height <= 0 || leadBounds.width <= 0 || leadBounds.height <= 0)
            throw new IllegalArgumentException("Invalid physical render terminal");
        this.terminalIndex = terminalIndex;
        this.terminalId = terminalId;
        this.boardPadId = boardPadId;
        this.point = new Point(point.x, point.y);
        this.probeBounds = new Rectangle(probeBounds);
        this.padBounds = new Rectangle(padBounds);
        this.leadBounds = new Rectangle(leadBounds);
        if (!contains(probeBounds, padBounds))
            throw new IllegalArgumentException("Physical render probe does not contain pad");
    }

    int getTerminalIndex() { return terminalIndex; }
    String getTerminalId() { return terminalId; }
    String getBoardPadId() { return boardPadId; }
    Point getPoint() { return new Point(point.x, point.y); }
    Rectangle getProbeBounds() { return new Rectangle(probeBounds); }
    Rectangle getPadBounds() { return new Rectangle(padBounds); }
    Rectangle getLeadBounds() { return new Rectangle(leadBounds); }
    boolean containsProbe(int x, int y) { return probeBounds.contains(x, y); }
    boolean containsComponentProbe(int x, int y, Rectangle boardPadProbeBounds) {
        return containsProbe(x, y) &&
            (boardPadProbeBounds == null || !boardPadProbeBounds.contains(x, y));
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }
}
