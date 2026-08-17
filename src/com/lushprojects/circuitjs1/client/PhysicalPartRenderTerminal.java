package com.lushprojects.circuitjs1.client;

/** Provider-owned physical lead geometry for one terminal. */
final class PhysicalPartRenderTerminal {
    private final int terminalIndex;
    private final String terminalId;
    private final String boardPadId;
    private final Point point;

    PhysicalPartRenderTerminal(int terminalIndex, String terminalId, String boardPadId,
            Point point) {
        if (terminalIndex < 0 || terminalId == null || terminalId.length() == 0 ||
                point == null)
            throw new IllegalArgumentException("Invalid physical render terminal");
        this.terminalIndex = terminalIndex;
        this.terminalId = terminalId;
        this.boardPadId = boardPadId;
        this.point = new Point(point.x, point.y);
    }

    int getTerminalIndex() { return terminalIndex; }
    String getTerminalId() { return terminalId; }
    String getBoardPadId() { return boardPadId; }
    Point getPoint() { return new Point(point.x, point.y); }
}
