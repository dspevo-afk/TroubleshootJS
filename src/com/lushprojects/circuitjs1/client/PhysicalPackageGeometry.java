package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Immutable physical envelope for one package in package-local coordinates.
 *
 * The package origin is the footprint/placement origin. Electrical identity
 * is deliberately absent: this class only describes where a physical feature
 * is, so translating or compacting a board cannot change its node mapping.
 *
 * Staged consumer boundary:
 * 43R-2: board/layout geometry consumers, compaction, containment, physical
 * net-connectivity validation.
 * 43R-3: installed rendering, selection, board-pad/component-side probing.
 * 43R-4: loose-part rigid pose, loose rendering, loose hit testing, loose
 * probing, realization consumer lifecycle.
 */
final class PhysicalPackageGeometry {
    private final int width;
    private final int height;
    private final Vector<Terminal> terminals;
    private final Rectangle bodyBounds;
    private final Rectangle bodyKeepOut;
    private final Rectangle routingCourtyard;
    private final Rectangle selectionEnvelope;
    private final Rectangle dragEnvelope;
    private final PcbGeometryContractVersion geometryContractVersion;
    private final boolean developerGeneric;

    PhysicalPackageGeometry(int width, int height, Vector<Terminal> terminals,
            Rectangle bodyBounds, Rectangle bodyKeepOut, Rectangle routingCourtyard,
            Rectangle selectionEnvelope, Rectangle dragEnvelope) {
        this(width, height, terminals, bodyBounds, bodyKeepOut, routingCourtyard,
            selectionEnvelope, dragEnvelope, PcbGeometryContractVersion.current(), false);
    }

    PhysicalPackageGeometry(int width, int height, Vector<Terminal> terminals,
            Rectangle bodyBounds, Rectangle bodyKeepOut, Rectangle routingCourtyard,
            Rectangle selectionEnvelope, Rectangle dragEnvelope,
            PcbGeometryContractVersion geometryContractVersion) {
        this(width, height, terminals, bodyBounds, bodyKeepOut, routingCourtyard,
            selectionEnvelope, dragEnvelope, geometryContractVersion, false);
    }

    private PhysicalPackageGeometry(int width, int height, Vector<Terminal> terminals,
            Rectangle bodyBounds, Rectangle bodyKeepOut, Rectangle routingCourtyard,
            Rectangle selectionEnvelope, Rectangle dragEnvelope,
            PcbGeometryContractVersion geometryContractVersion, boolean developerGeneric) {
        if (width <= 0 || height <= 0 || terminals == null || terminals.size() == 0 ||
                bodyBounds == null || bodyKeepOut == null || routingCourtyard == null ||
                selectionEnvelope == null || dragEnvelope == null ||
                geometryContractVersion == null)
            throw new IllegalArgumentException("Invalid physical package geometry");
        this.width = width;
        this.height = height;
        this.terminals = new Vector<Terminal>();
        for (Terminal terminal : terminals) {
            if (terminal == null)
                throw new IllegalArgumentException("Invalid package terminal geometry");
            this.terminals.add(terminal.copy());
        }
        this.bodyBounds = copyPositive(bodyBounds, "body");
        this.bodyKeepOut = copyPositive(bodyKeepOut, "body keep-out");
        this.routingCourtyard = copyPositive(routingCourtyard, "routing courtyard");
        this.selectionEnvelope = copyPositive(selectionEnvelope, "selection envelope");
        this.dragEnvelope = copyPositive(dragEnvelope, "drag envelope");
        this.geometryContractVersion = geometryContractVersion;
        this.developerGeneric = developerGeneric;
        validate();
    }

    int getWidth() { return width; }
    int getHeight() { return height; }
    PcbGeometryContractVersion getGeometryContractVersion() {
        return geometryContractVersion;
    }
    int getGeometryContractVersionValue() { return geometryContractVersion.getValue(); }
    boolean isDeveloperGeneric() { return developerGeneric; }

    Vector<Terminal> getTerminals() {
        Vector<Terminal> result = new Vector<Terminal>();
        for (Terminal terminal : terminals)
            result.add(terminal.copy());
        return result;
    }

    Vector<String> getTerminalIds() {
        Vector<String> result = new Vector<String>();
        for (Terminal terminal : terminals)
            result.add(terminal.getTerminalId());
        return result;
    }

    Terminal getTerminal(String terminalId) {
        for (Terminal terminal : terminals)
            if (terminal.getTerminalId().equals(terminalId))
                return terminal.copy();
        return null;
    }

    Terminal getTerminal(int index) {
        return index < 0 || index >= terminals.size() ? null : terminals.get(index).copy();
    }

    /** Nominal placement rectangle; declared margins are intentionally excluded. */
    Rectangle getNominalBounds() { return new Rectangle(0, 0, width, height); }
    Rectangle getBodyBounds() { return new Rectangle(bodyBounds); }
    Rectangle getBodyKeepOut() { return new Rectangle(bodyKeepOut); }
    Rectangle getRoutingCourtyard() { return new Rectangle(routingCourtyard); }
    Rectangle getSelectionEnvelope() { return new Rectangle(selectionEnvelope); }
    Rectangle getDragEnvelope() { return new Rectangle(dragEnvelope); }

    Placement placedAt(int x, int y) { return new Placement(this, x, y); }

    /**
     * Returns the package-declared mirrored realization. This does not mutate
     * this geometry; package definitions retain the returned object as a
     * canonical variant.
     */
    PhysicalPackageGeometry mirroredHorizontally() {
        Vector<Terminal> mirrored = new Vector<Terminal>();
        for (Terminal terminal : terminals) {
            mirrored.add(new Terminal(terminal.terminalId,
                mirrorPoint(terminal.padCenter), mirrorRect(terminal.padBounds),
                mirrorPoint(terminal.boardPadProbeCenter),
                mirrorRect(terminal.boardPadProbeBounds),
                mirrorLead(terminal.connectedLead), mirrorLead(terminal.liftedLead),
                -terminal.escapeDx, terminal.escapeDy, terminal.escapeLength));
        }
        return new PhysicalPackageGeometry(width, height, mirrored,
            mirrorRect(bodyBounds), mirrorRect(bodyKeepOut), mirrorRect(routingCourtyard),
            mirrorRect(selectionEnvelope), mirrorRect(dragEnvelope),
            geometryContractVersion, developerGeneric);
    }

    /** Package-local geometry translated into board coordinates. */
    static final class Placement {
        private final PhysicalPackageGeometry source;
        private final int x;
        private final int y;

        private Placement(PhysicalPackageGeometry source, int x, int y) {
            if (source == null)
                throw new IllegalArgumentException("Missing package geometry placement source");
            this.source = source;
            this.x = x;
            this.y = y;
        }

        Rectangle getBodyBounds() { return translated(source.bodyBounds); }
        Rectangle getBodyKeepOut() { return translated(source.bodyKeepOut); }
        Rectangle getRoutingCourtyard() { return translated(source.routingCourtyard); }
        Rectangle getSelectionEnvelope() { return translated(source.selectionEnvelope); }
        Rectangle getDragEnvelope() { return translated(source.dragEnvelope); }

        Point getPadPoint(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getPadCenter());
        }

        Point getLeadEndPoint(int index) {
            return getLeadEndPoint(index, false);
        }

        Point getLeadEndPoint(int index, boolean lifted) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getLead(lifted).getEndPoint());
        }

        Point getConnectedBoardEndPoint(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getConnectedBoardEndPoint());
        }

        Point getLiftedFreeEndPoint(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getLiftedFreeEndPoint());
        }

        Rectangle getPadBounds(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(terminal.getPadBounds());
        }

        Point getBoardPadProbeCenter(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getBoardPadProbeCenter());
        }

        Rectangle getBoardPadProbeBounds(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(terminal.getBoardPadProbeBounds());
        }

        Point getComponentLeadProbeCenter(int index) {
            return getComponentLeadProbeCenter(index, false);
        }

        Point getComponentLeadProbeCenter(int index, boolean lifted) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(
                terminal.getComponentLeadProbeCenter(lifted));
        }

        Rectangle getComponentLeadProbeBounds(int index) {
            return getComponentLeadProbeBounds(index, false);
        }

        Rectangle getComponentLeadProbeBounds(int index, boolean lifted) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(
                terminal.getComponentLeadProbeBounds(lifted));
        }

        Point getLeadBodyPoint(int index) {
            return getLeadBodyPoint(index, false);
        }

        Point getLeadBodyPoint(int index, boolean lifted) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getLead(lifted).getBodyPoint());
        }

        Rectangle getLeadBounds(int index) {
            return getLeadBounds(index, false);
        }

        Rectangle getLeadBounds(int index, boolean lifted) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(terminal.getLead(lifted).getBounds());
        }

        /** Legacy board-side alias retained for existing PCB pad consumers. */
        Point getProbePoint(int index) { return getBoardPadProbeCenter(index); }

        /** Legacy board-side alias retained for existing PCB pad consumers. */
        Rectangle getProbeBounds(int index) { return getBoardPadProbeBounds(index); }

        private Rectangle translated(Rectangle value) {
            return new Rectangle(checkedAdd(value.x, x), checkedAdd(value.y, y),
                value.width, value.height);
        }

        private Point translate(Point value) {
            return new Point(checkedAdd(value.x, x), checkedAdd(value.y, y));
        }
    }

    /** Immutable board-pad and connected/lifted lead contract for one terminal. */
    static final class Terminal {
        private final String terminalId;
        private final Point padCenter;
        private final Rectangle padBounds;
        private final Point boardPadProbeCenter;
        private final Rectangle boardPadProbeBounds;
        private final Lead connectedLead;
        private final Lead liftedLead;
        private final int escapeDx;
        private final int escapeDy;
        private final int escapeLength;

        Terminal(String terminalId, Point padCenter, Rectangle padBounds,
                Point boardPadProbeCenter, Rectangle boardPadProbeBounds,
                Lead connectedLead, Lead liftedLead, int escapeDx, int escapeDy,
                int escapeLength) {
            if (terminalId == null || terminalId.trim().length() == 0 || padCenter == null ||
                    padBounds == null || boardPadProbeCenter == null ||
                    boardPadProbeBounds == null || connectedLead == null || liftedLead == null)
                throw new IllegalArgumentException("Invalid package terminal geometry");
            validateEscape(terminalId, escapeDx, escapeDy, escapeLength);
            this.terminalId = terminalId;
            this.padCenter = new Point(padCenter);
            this.padBounds = copyPositive(padBounds, "pad");
            this.boardPadProbeCenter = new Point(boardPadProbeCenter);
            this.boardPadProbeBounds = copyPositive(boardPadProbeBounds, "board-pad probe");
            this.connectedLead = connectedLead.copy();
            this.liftedLead = liftedLead.copy();
            this.escapeDx = escapeDx;
            this.escapeDy = escapeDy;
            this.escapeLength = escapeLength;
        }

        /** Compatibility constructor for developer-only geometry canaries. */
        Terminal(String terminalId, Point padCenter, Rectangle padBounds,
                Point boardPadProbeCenter, Rectangle boardPadProbeBounds, Lead lead,
                int escapeDx, int escapeDy, int escapeLength) {
            this(terminalId, padCenter, padBounds, boardPadProbeCenter, boardPadProbeBounds,
                lead, lead.copy(), escapeDx, escapeDy, escapeLength);
        }

        private Terminal(Terminal source) {
            this(source.terminalId, source.padCenter, source.padBounds,
                source.boardPadProbeCenter, source.boardPadProbeBounds,
                source.connectedLead, source.liftedLead, source.escapeDx, source.escapeDy,
                source.escapeLength);
        }

        private Terminal copy() { return new Terminal(this); }

        String getTerminalId() { return terminalId; }
        Point getPadCenter() { return new Point(padCenter); }
        Rectangle getPadBounds() { return new Rectangle(padBounds); }
        Point getBoardPadProbeCenter() { return new Point(boardPadProbeCenter); }
        Rectangle getBoardPadProbeBounds() { return new Rectangle(boardPadProbeBounds); }

        Point getConnectedBoardEndPoint() { return connectedLead.getEndPoint(); }
        Point getLiftedFreeEndPoint() { return liftedLead.getEndPoint(); }

        /** Legacy board-side alias; it is never the component-lead surface. */
        Point getProbeCenter() { return getBoardPadProbeCenter(); }

        /** Legacy board-side alias; it is never the component-lead surface. */
        Rectangle getProbeBounds() { return getBoardPadProbeBounds(); }

        Lead getConnectedLead() { return connectedLead.copy(); }
        Lead getLiftedLead() { return liftedLead.copy(); }
        Lead getLead() { return getConnectedLead(); }

        Point getComponentLeadProbeCenter() { return getComponentLeadProbeCenter(false); }
        Point getComponentLeadProbeCenter(boolean lifted) {
            return (lifted ? liftedLead : connectedLead).getComponentProbeCenter();
        }
        Rectangle getComponentLeadProbeBounds() { return getComponentLeadProbeBounds(false); }
        Rectangle getComponentLeadProbeBounds(boolean lifted) {
            return (lifted ? liftedLead : connectedLead).getComponentProbeBounds();
        }

        Lead getLead(boolean lifted) { return lifted ? getLiftedLead() : getConnectedLead(); }
        int getEscapeDx() { return escapeDx; }
        int getEscapeDy() { return escapeDy; }
        int getEscapeLength() { return escapeLength; }
    }

    /** Immutable straight lead pose and its component-side probe surface. */
    static final class Lead {
        private final Point endPoint;
        private final Point bodyPoint;
        private final Rectangle bounds;
        private final Point componentProbeCenter;
        private final Rectangle componentProbeBounds;

        Lead(Point endPoint, Point bodyPoint, Rectangle bounds,
                Point componentProbeCenter, Rectangle componentProbeBounds) {
            if (endPoint == null || bodyPoint == null || bounds == null ||
                    componentProbeCenter == null || componentProbeBounds == null)
                throw new IllegalArgumentException("Invalid package lead geometry");
            this.endPoint = new Point(endPoint);
            this.bodyPoint = new Point(bodyPoint);
            this.bounds = copyPositive(bounds, "lead");
            this.componentProbeCenter = new Point(componentProbeCenter);
            this.componentProbeBounds = copyPositive(componentProbeBounds,
                "component-lead probe");
        }

        /** Compatibility constructor for developer-only geometry canaries. */
        Lead(Point endPoint, Point bodyPoint, Rectangle bounds) {
            this(endPoint, bodyPoint, bounds, bodyPoint, centered(bodyPoint, 8, 8));
        }

        private Lead(Lead source) {
            this(source.endPoint, source.bodyPoint, source.bounds,
                source.componentProbeCenter, source.componentProbeBounds);
        }

        private Lead copy() { return new Lead(this); }

        Point getEndPoint() { return new Point(endPoint); }
        Point getBodyPoint() { return new Point(bodyPoint); }
        Rectangle getBounds() { return new Rectangle(bounds); }
        Point getComponentProbeCenter() { return new Point(componentProbeCenter); }
        Rectangle getComponentProbeBounds() { return new Rectangle(componentProbeBounds); }

        boolean isEquivalentTo(Lead other) {
            return other != null && endPoint.equals(other.endPoint) &&
                bodyPoint.equals(other.bodyPoint) && bounds.equals(other.bounds) &&
                componentProbeCenter.equals(other.componentProbeCenter) &&
                componentProbeBounds.equals(other.componentProbeBounds);
        }
    }

    /** Deterministic fallback used only by marked developer-generic packages. */
    static PhysicalPackageGeometry generic(Vector<String> terminalIds, boolean connector) {
        if (terminalIds == null || terminalIds.size() == 0)
            throw new IllegalArgumentException("Missing package terminals");
        int width = 150;
        int height = checkedAdd(60, checkedMultiply(terminalIds.size() - 1, 40));
        int padX = width - 30;
        Vector<Terminal> terminals = new Vector<Terminal>();
        for (int index = 0; index < terminalIds.size(); index++) {
            int padY = checkedAdd(30, checkedMultiply(index, 40));
            Point pad = new Point(padX, padY);
            Point body = new Point(width - 55, padY);
            Point liftedEnd = new Point(width - 60, padY);
            terminals.add(terminal(terminalIds.get(index), pad, centered(pad, 26, 26),
                centered(pad, 30, 30), body, liftedEnd, -1, 0, 30));
        }
        Rectangle body = new Rectangle(10, 10, width - 50, height - 20);
        Rectangle keepOut = new Rectangle(10, 10, width - 50, height - 20);
        Rectangle courtyard = new Rectangle(10, 10, width - 20, height - 20);
        Rectangle selection = new Rectangle(-4, -4, checkedAdd(width, 8),
            checkedAdd(height, 8));
        Rectangle drag = new Rectangle(-10, -10, checkedAdd(width, 20),
            checkedAdd(height, 20));
        return new PhysicalPackageGeometry(width, height, terminals, body, keepOut, courtyard,
            selection, drag, PcbGeometryContractVersion.current(), true);
    }

    private static Terminal terminal(String id, Point pad, Rectangle padBounds,
            Rectangle boardProbe, Point body, Point liftedEnd, int escapeDx, int escapeDy,
            int escapeLength) {
        Lead connected = lead(pad, body, body);
        Lead lifted = lead(liftedEnd, body, liftedEnd);
        return new Terminal(id, pad, padBounds, pad, boardProbe, connected, lifted,
            escapeDx, escapeDy, escapeLength);
    }

    private static Lead lead(Point endPoint, Point bodyPoint, Point componentProbeCenter) {
        int left = checkedInt(Math.min((long) endPoint.x, bodyPoint.x) - 3L);
        int top = checkedInt(Math.min((long) endPoint.y, bodyPoint.y) - 3L);
        int right = checkedInt(Math.max((long) endPoint.x, bodyPoint.x) + 3L);
        int bottom = checkedInt(Math.max((long) endPoint.y, bodyPoint.y) + 3L);
        return new Lead(endPoint, bodyPoint, rectangleFromEdges(left, top, right, bottom),
            componentProbeCenter, centered(componentProbeCenter, 8, 8));
    }

    private Lead mirrorLead(Lead lead) {
        return new Lead(mirrorPoint(lead.endPoint), mirrorPoint(lead.bodyPoint),
            mirrorRect(lead.bounds), mirrorPoint(lead.componentProbeCenter),
            mirrorRect(lead.componentProbeBounds));
    }

    private void validate() {
        if (!contains(bodyKeepOut, bodyBounds) || !contains(routingCourtyard, bodyKeepOut) ||
                !contains(dragEnvelope, selectionEnvelope))
            throw new IllegalArgumentException("Package envelope containment failed");
        if (!contains(selectionEnvelope, bodyBounds) || !contains(dragEnvelope, bodyKeepOut))
            throw new IllegalArgumentException("Package selection envelope is incomplete");

        Vector<String> ids = new Vector<String>();
        for (Terminal terminal : terminals) {
            if (ids.contains(terminal.terminalId))
                throw new IllegalArgumentException("Duplicate package terminal geometry: " +
                    terminal.terminalId);
            ids.add(terminal.terminalId);
            if (!contains(routingCourtyard, terminal.padBounds) ||
                    !contains(selectionEnvelope, terminal.padBounds) ||
                    !contains(selectionEnvelope, terminal.boardPadProbeBounds) ||
                    !contains(dragEnvelope, terminal.padBounds) ||
                    !contains(dragEnvelope, terminal.boardPadProbeBounds) ||
                    !contains(terminal.padBounds, terminal.padCenter.x, terminal.padCenter.y) ||
                    !contains(terminal.boardPadProbeBounds, terminal.padBounds) ||
                    !contains(terminal.boardPadProbeBounds, terminal.boardPadProbeCenter.x,
                        terminal.boardPadProbeCenter.y))
                throw new IllegalArgumentException("Board-pad probe geometry is inconsistent: " +
                    terminal.terminalId);
            validateLead(terminal, terminal.connectedLead, true);
            validateLead(terminal, terminal.liftedLead, false);
            if (intersects(terminal.boardPadProbeBounds,
                    terminal.connectedLead.componentProbeBounds) ||
                    intersects(terminal.boardPadProbeBounds,
                    terminal.liftedLead.componentProbeBounds))
                throw new IllegalArgumentException("Component probe overlaps board-pad probe: " +
                    terminal.terminalId);
        }

        for (int first = 0; first < terminals.size(); first++) {
            Terminal firstTerminal = terminals.get(first);
            for (int second = first + 1; second < terminals.size(); second++) {
                Terminal secondTerminal = terminals.get(second);
                if (intersects(firstTerminal.boardPadProbeBounds,
                        secondTerminal.boardPadProbeBounds) ||
                        intersects(firstTerminal.boardPadProbeBounds,
                            secondTerminal.connectedLead.componentProbeBounds) ||
                        intersects(firstTerminal.boardPadProbeBounds,
                            secondTerminal.liftedLead.componentProbeBounds) ||
                        intersects(secondTerminal.boardPadProbeBounds,
                            firstTerminal.connectedLead.componentProbeBounds) ||
                        intersects(secondTerminal.boardPadProbeBounds,
                            firstTerminal.liftedLead.componentProbeBounds))
                    throw new IllegalArgumentException("Cross-terminal probe surfaces overlap: " +
                        firstTerminal.terminalId + "/" + secondTerminal.terminalId);
                if (intersects(firstTerminal.connectedLead.componentProbeBounds,
                        secondTerminal.connectedLead.componentProbeBounds) ||
                        intersects(firstTerminal.connectedLead.componentProbeBounds,
                        secondTerminal.liftedLead.componentProbeBounds) ||
                        intersects(firstTerminal.liftedLead.componentProbeBounds,
                        secondTerminal.connectedLead.componentProbeBounds) ||
                        intersects(firstTerminal.liftedLead.componentProbeBounds,
                        secondTerminal.liftedLead.componentProbeBounds))
                    throw new IllegalArgumentException("Peer component probes overlap: " +
                        firstTerminal.terminalId + "/" + secondTerminal.terminalId);
            }
        }
    }

    private void validateLead(Terminal terminal, Lead lead, boolean connected) {
        if (!contains(lead.bounds, lead.endPoint.x, lead.endPoint.y) ||
                !contains(lead.bounds, lead.bodyPoint.x, lead.bodyPoint.y) ||
                !contains(lead.bounds, lead.componentProbeCenter.x,
                    lead.componentProbeCenter.y) ||
                !contains(lead.componentProbeBounds, lead.componentProbeCenter.x,
                    lead.componentProbeCenter.y) ||
                !contains(selectionEnvelope, lead.bounds) ||
                !contains(selectionEnvelope, lead.componentProbeBounds) ||
                !contains(dragEnvelope, lead.bounds) ||
                !contains(dragEnvelope, lead.componentProbeBounds) ||
                (connected && (!lead.endPoint.equals(terminal.padCenter) ||
                    !contains(bodyBounds, lead.bodyPoint.x, lead.bodyPoint.y))) ||
                (!connected && (lead.endPoint.equals(terminal.padCenter) ||
                    contains(terminal.boardPadProbeBounds, lead.endPoint.x,
                        lead.endPoint.y) ||
                    intersects(terminal.boardPadProbeBounds, lead.bounds) ||
                    !lead.bodyPoint.equals(terminal.connectedLead.bodyPoint) ||
                    !contains(bodyBounds, lead.bodyPoint.x, lead.bodyPoint.y))))
            throw new IllegalArgumentException("Package lead geometry is inconsistent: " +
                terminal.terminalId);
    }

    boolean isEquivalentTo(PhysicalPackageGeometry other) {
        if (other == null || width != other.width || height != other.height ||
                !geometryContractVersion.equals(other.geometryContractVersion) ||
                developerGeneric != other.developerGeneric ||
                !bodyBounds.equals(other.bodyBounds) || !bodyKeepOut.equals(other.bodyKeepOut) ||
                !routingCourtyard.equals(other.routingCourtyard) ||
                !selectionEnvelope.equals(other.selectionEnvelope) ||
                !dragEnvelope.equals(other.dragEnvelope) || terminals.size() != other.terminals.size())
            return false;
        for (int index = 0; index < terminals.size(); index++) {
            Terminal first = terminals.get(index);
            Terminal second = other.terminals.get(index);
            if (!first.terminalId.equals(second.terminalId) ||
                    !first.padCenter.equals(second.padCenter) ||
                    !first.padBounds.equals(second.padBounds) ||
                    !first.boardPadProbeCenter.equals(second.boardPadProbeCenter) ||
                    !first.boardPadProbeBounds.equals(second.boardPadProbeBounds) ||
                    !first.connectedLead.isEquivalentTo(second.connectedLead) ||
                    !first.liftedLead.isEquivalentTo(second.liftedLead) ||
                    first.escapeDx != second.escapeDx || first.escapeDy != second.escapeDy ||
                    first.escapeLength != second.escapeLength)
                return false;
        }
        return true;
    }

    boolean hasTerminalIds(Vector<String> expected) {
        return expected != null && getTerminalIds().equals(expected);
    }

    private static void validateEscape(String terminalId, int escapeDx, int escapeDy,
            int escapeLength) {
        long directionMagnitude = Math.abs((long) escapeDx) + Math.abs((long) escapeDy);
        if (directionMagnitude > 1 || escapeLength < 0 ||
                (escapeLength == 0 && directionMagnitude != 0) ||
                (escapeLength > 0 && directionMagnitude == 0))
            throw new IllegalArgumentException("Invalid package terminal escape geometry: " +
                terminalId);
    }

    private static Rectangle copyPositive(Rectangle value, String name) {
        if (value.width <= 0 || value.height <= 0)
            throw new IllegalArgumentException("Invalid package " + name + " bounds");
        return new Rectangle(value);
    }

    private static Rectangle centered(Point center, int width, int height) {
        if (center == null || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid centered package geometry");
        int left = checkedInt((long) center.x - width / 2);
        int top = checkedInt((long) center.y - height / 2);
        return new Rectangle(left, top, width, height);
    }

    private Rectangle mirrorRect(Rectangle value) {
        return new Rectangle(checkedInt((long) width - value.x - value.width), value.y,
            value.width, value.height);
    }

    private Point mirrorPoint(Point value) {
        return new Point(checkedInt((long) width - value.x), value.y);
    }

    private static Rectangle rectangleFromEdges(int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top)
            throw new IllegalArgumentException("Invalid package lead edges");
        return new Rectangle(left, top, checkedInt((long) right - left),
            checkedInt((long) bottom - top));
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        long innerRight = (long) inner.x + inner.width;
        long innerBottom = (long) inner.y + inner.height;
        long outerRight = (long) outer.x + outer.width;
        long outerBottom = (long) outer.y + outer.height;
        return inner.x >= outer.x && inner.y >= outer.y &&
            innerRight <= outerRight && innerBottom <= outerBottom;
    }

    private static boolean contains(Rectangle outer, int x, int y) {
        return x >= outer.x && y >= outer.y &&
            (long) x <= (long) outer.x + outer.width &&
            (long) y <= (long) outer.y + outer.height;
    }

    private static boolean intersects(Rectangle first, Rectangle second) {
        long firstRight = (long) first.x + first.width;
        long firstBottom = (long) first.y + first.height;
        long secondRight = (long) second.x + second.width;
        long secondBottom = (long) second.y + second.height;
        return first.x < secondRight && second.x < firstRight &&
            first.y < secondBottom && second.y < firstBottom;
    }

    private static int checkedAdd(int first, int second) {
        return checkedInt((long) first + second);
    }

    private static int checkedMultiply(int first, int second) {
        return checkedInt((long) first * second);
    }

    private static int checkedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Package geometry integer overflow: " + value);
        return (int) value;
    }
}
