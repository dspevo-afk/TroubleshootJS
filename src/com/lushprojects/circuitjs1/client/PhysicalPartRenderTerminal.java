package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Provider-owned physical lead geometry for one terminal. */
final class PhysicalPartRenderTerminal {
    private final int terminalIndex;
    private final String terminalId;
    private final String boardPadId;
    private final Point point;
    private final Rectangle probeBounds;
    private final Point boardPadPoint;
    private final Rectangle boardPadProbeBounds;
    private final Point componentLeadPoint;
    private final Rectangle componentLeadProbeBounds;
    private final Rectangle padBounds;
    private final Point leadBodyPoint;
    private final Point leadEndPoint;
    private final Rectangle leadBounds;
    private final Vector<Rectangle> looseProbeSurfaces;
    private final boolean looseProbeMode;

    PhysicalPartRenderTerminal(int terminalIndex, String terminalId, String boardPadId,
            Point point, Rectangle probeBounds, Rectangle padBounds, Rectangle leadBounds) {
        this(terminalIndex, terminalId, boardPadId, point, probeBounds,
            boardPadId == null ? null : point, boardPadId == null ? null : probeBounds, padBounds,
            point, probeBounds, point, point, leadBounds, null, false);
    }

    /**
     * Explicit installed projection.  Board-pad and component-lead surfaces
     * remain distinct even when the active probe surface is the board pad.
     */
    PhysicalPartRenderTerminal(int terminalIndex, String terminalId, String boardPadId,
            Point activePoint, Rectangle activeProbeBounds, Point boardPadPoint,
            Rectangle boardPadProbeBounds, Rectangle padBounds, Point componentLeadPoint,
            Rectangle componentLeadProbeBounds, Point leadBodyPoint, Point leadEndPoint,
            Rectangle leadBounds) {
        this(terminalIndex, terminalId, boardPadId, activePoint, activeProbeBounds,
            boardPadPoint, boardPadProbeBounds, padBounds, componentLeadPoint,
            componentLeadProbeBounds, leadBodyPoint, leadEndPoint, leadBounds, null, false);
    }

    /** Explicit loose projection with each declared probe surface retained. */
    PhysicalPartRenderTerminal(int terminalIndex, String terminalId, String boardPadId,
            Point activePoint, Rectangle activeProbeBounds, Point boardPadPoint,
            Rectangle boardPadProbeBounds, Rectangle padBounds, Point componentLeadPoint,
            Rectangle componentLeadProbeBounds, Point leadBodyPoint, Point leadEndPoint,
            Rectangle leadBounds, Vector<Rectangle> looseProbeSurfaces) {
        this(terminalIndex, terminalId, boardPadId, activePoint, activeProbeBounds,
            boardPadPoint, boardPadProbeBounds, padBounds, componentLeadPoint,
            componentLeadProbeBounds, leadBodyPoint, leadEndPoint, leadBounds,
            looseProbeSurfaces, true);
    }

    private PhysicalPartRenderTerminal(int terminalIndex, String terminalId, String boardPadId,
            Point activePoint, Rectangle activeProbeBounds, Point boardPadPoint,
            Rectangle boardPadProbeBounds, Rectangle padBounds, Point componentLeadPoint,
            Rectangle componentLeadProbeBounds, Point leadBodyPoint, Point leadEndPoint,
            Rectangle leadBounds, Vector<Rectangle> looseProbeSurfaces, boolean looseProbeMode) {
        if (terminalIndex < 0 || terminalId == null || terminalId.length() == 0 ||
                activePoint == null || activeProbeBounds == null || padBounds == null ||
                activeProbeBounds.width <= 0 || activeProbeBounds.height <= 0 ||
                leadBodyPoint == null || leadEndPoint == null || leadBounds == null ||
                componentLeadProbeBounds == null || componentLeadProbeBounds.width <= 0 ||
                componentLeadProbeBounds.height <= 0 || padBounds.width <= 0 ||
                padBounds.height <= 0 || leadBounds.width <= 0 || leadBounds.height <= 0)
            throw new IllegalArgumentException("Invalid physical render terminal");
        if (!contains(activeProbeBounds, activePoint))
            throw new IllegalArgumentException("Physical render marker is outside its probe surface");
        if (boardPadId != null && (boardPadPoint == null || boardPadProbeBounds == null ||
                boardPadProbeBounds.width <= 0 || boardPadProbeBounds.height <= 0))
            throw new IllegalArgumentException("Installed physical render terminal has no pad surface");
        this.terminalIndex = terminalIndex;
        this.terminalId = terminalId;
        this.boardPadId = boardPadId;
        this.point = new Point(activePoint.x, activePoint.y);
        this.probeBounds = new Rectangle(activeProbeBounds);
        this.boardPadPoint = boardPadPoint == null ? null : new Point(boardPadPoint);
        this.boardPadProbeBounds = boardPadProbeBounds == null ? null :
            new Rectangle(boardPadProbeBounds);
        this.componentLeadPoint = new Point(componentLeadPoint);
        this.componentLeadProbeBounds = new Rectangle(componentLeadProbeBounds);
        this.padBounds = new Rectangle(padBounds);
        this.leadBodyPoint = new Point(leadBodyPoint);
        this.leadEndPoint = new Point(leadEndPoint);
        this.leadBounds = new Rectangle(leadBounds);
        this.looseProbeMode = looseProbeMode;
        this.looseProbeSurfaces = new Vector<Rectangle>();
        if (looseProbeMode) {
            if (looseProbeSurfaces == null || looseProbeSurfaces.size() == 0)
                throw new IllegalArgumentException("Loose physical render terminal has no probe surface");
            for (Rectangle surface : looseProbeSurfaces) {
                if (surface == null || surface.width <= 0 || surface.height <= 0)
                    throw new IllegalArgumentException("Invalid loose physical render probe surface");
                this.looseProbeSurfaces.add(new Rectangle(surface));
            }
        }
        if (boardPadId != null && (!contains(this.boardPadProbeBounds, this.padBounds) ||
                !contains(this.boardPadProbeBounds, this.boardPadPoint)))
            throw new IllegalArgumentException("Physical render probe does not contain pad");
        if (!contains(this.componentLeadProbeBounds, this.componentLeadPoint))
            throw new IllegalArgumentException("Physical render component probe omits its center");
        if (!contains(this.leadBounds, this.leadBodyPoint) ||
                !contains(this.leadBounds, this.leadEndPoint))
            throw new IllegalArgumentException("Physical render lead point escapes lead bounds");
        if (looseProbeMode && !containsAny(this.looseProbeSurfaces, this.point))
            throw new IllegalArgumentException(
                "Physical render marker is outside its declared loose probe surfaces");
    }

    int getTerminalIndex() { return terminalIndex; }
    String getTerminalId() { return terminalId; }
    String getBoardPadId() { return boardPadId; }
    /** Active probe/marker surface: board pad while connected, lead while lifted. */
    Point getPoint() { return new Point(point.x, point.y); }
    /** Active probe surface: board pad while connected, lead while lifted. */
    Rectangle getProbeBounds() { return new Rectangle(probeBounds); }
    Point getBoardPadPoint() {
        return boardPadPoint == null ? null : new Point(boardPadPoint.x, boardPadPoint.y);
    }
    Rectangle getBoardPadProbeBounds() {
        return boardPadProbeBounds == null ? null : new Rectangle(boardPadProbeBounds);
    }
    Point getComponentLeadPoint() {
        return new Point(componentLeadPoint.x, componentLeadPoint.y);
    }
    Rectangle getComponentLeadProbeBounds() { return new Rectangle(componentLeadProbeBounds); }
    Rectangle getPadBounds() { return new Rectangle(padBounds); }
    Rectangle getLeadBounds() { return new Rectangle(leadBounds); }
    Point getLeadBodyPoint() { return new Point(leadBodyPoint.x, leadBodyPoint.y); }
    Point getLeadEndPoint() { return new Point(leadEndPoint.x, leadEndPoint.y); }
    Vector<Rectangle> getProbeSurfaces() {
        Vector<Rectangle> result = new Vector<Rectangle>();
        if (looseProbeMode) {
            for (Rectangle surface : looseProbeSurfaces)
                result.add(new Rectangle(surface));
        } else {
            result.add(new Rectangle(probeBounds));
        }
        return result;
    }
    boolean containsProbe(int x, int y) {
        if (!looseProbeMode)
            return probeBounds.contains(x, y);
        for (Rectangle surface : looseProbeSurfaces)
            if (surface.contains(x, y))
                return true;
        return false;
    }
    boolean containsComponentProbe(int x, int y, Rectangle boardPadProbeBounds) {
        return componentLeadProbeBounds.contains(x, y) &&
            (boardPadProbeBounds == null || !boardPadProbeBounds.contains(x, y));
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x - 1 && inner.y >= outer.y - 1 &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width + 1 &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height + 1;
    }

    private static boolean contains(Rectangle outer, Point point) {
        return point.x >= outer.x - 1 && point.y >= outer.y - 1 &&
            point.x <= outer.x + outer.width && point.y <= outer.y + outer.height;
    }

    private static boolean containsAny(Vector<Rectangle> surfaces, Point point) {
        for (Rectangle surface : surfaces)
            if (contains(surface, point))
                return true;
        return false;
    }
}
