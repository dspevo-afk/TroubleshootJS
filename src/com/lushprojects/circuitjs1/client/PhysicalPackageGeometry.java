package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Immutable physical envelope for one package in package-local coordinates.
 *
 * The package origin is the footprint/placement origin.  Electrical identity
 * is deliberately absent: this class only describes where a physical feature
 * is, so translating or compacting a board cannot change its node mapping.
 *
 * Width and height define the nominal Rectangle(0, 0, width, height), not a
 * containment box for every declared feature.  Interaction and routing margins
 * are allowed to extend beyond that nominal rectangle; built-in packages
 * intentionally use that policy and consumers must use the declared envelopes
 * for containment.
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

    PhysicalPackageGeometry(int width, int height, Vector<Terminal> terminals,
            Rectangle bodyBounds, Rectangle bodyKeepOut, Rectangle routingCourtyard,
            Rectangle selectionEnvelope, Rectangle dragEnvelope) {
        if (width <= 0 || height <= 0 || terminals == null || terminals.size() == 0 ||
                bodyBounds == null || bodyKeepOut == null || routingCourtyard == null ||
                selectionEnvelope == null || dragEnvelope == null)
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
        validate();
    }

    int getWidth() { return width; }
    int getHeight() { return height; }
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
     * Returns the package-owned edge variant with its local X axis mirrored.
     * This keeps the package contract immutable while preserving the historical
     * left/right edge placement rule used by connector footprints.
     */
    PhysicalPackageGeometry mirroredHorizontally() {
        Vector<Terminal> mirrored = new Vector<Terminal>();
        for (Terminal terminal : terminals) {
            Point pad = mirrorPoint(terminal.padCenter);
            Point probe = mirrorPoint(terminal.probeCenter);
            Lead lead = terminal.lead;
            mirrored.add(new Terminal(terminal.terminalId, pad,
                mirrorRect(terminal.padBounds), probe, mirrorRect(terminal.probeBounds),
                new Lead(mirrorPoint(lead.padPoint), mirrorPoint(lead.bodyPoint),
                    mirrorRect(lead.bounds)), -terminal.escapeDx, terminal.escapeDy,
                terminal.escapeLength));
        }
        return new PhysicalPackageGeometry(width, height, mirrored,
            mirrorRect(bodyBounds), mirrorRect(bodyKeepOut), mirrorRect(routingCourtyard),
            mirrorRect(selectionEnvelope), mirrorRect(dragEnvelope));
    }

    /** Package-local geometry translated into board coordinates. */
    static final class Placement {
        private final PhysicalPackageGeometry source;
        private final int x;
        private final int y;

        private Placement(PhysicalPackageGeometry source, int x, int y) {
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
        Rectangle getPadBounds(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(terminal.getPadBounds());
        }
        Point getProbePoint(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getProbeCenter());
        }
        Rectangle getProbeBounds(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(terminal.getProbeBounds());
        }
        Point getLeadBodyPoint(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translate(terminal.getLead().getBodyPoint());
        }
        Rectangle getLeadBounds(int index) {
            Terminal terminal = source.getTerminal(index);
            return terminal == null ? null : translated(terminal.getLead().getBounds());
        }
        private Rectangle translated(Rectangle value) {
            return new Rectangle(value.x + x, value.y + y, value.width, value.height);
        }
        private Point translate(Point value) { return new Point(value.x + x, value.y + y); }
    }

    /** Immutable pad, probe, and lead contract owned by one stable terminal ID. */
    static final class Terminal {
        private final String terminalId;
        private final Point padCenter;
        private final Rectangle padBounds;
        private final Point probeCenter;
        private final Rectangle probeBounds;
        private final Lead lead;
        private final int escapeDx;
        private final int escapeDy;
        private final int escapeLength;

        Terminal(String terminalId, Point padCenter, Rectangle padBounds, Point probeCenter,
                Rectangle probeBounds, Lead lead, int escapeDx, int escapeDy,
                int escapeLength) {
            if (terminalId == null || terminalId.trim().length() == 0 || padCenter == null ||
                    padBounds == null || probeCenter == null || probeBounds == null || lead == null)
                throw new IllegalArgumentException("Invalid package terminal geometry");
            if (Math.abs(escapeDx) + Math.abs(escapeDy) > 1 || escapeLength < 0 ||
                    (escapeLength > 0 && escapeDx == 0 && escapeDy == 0))
                throw new IllegalArgumentException("Invalid package terminal escape geometry: " +
                    terminalId);
            this.terminalId = terminalId;
            this.padCenter = new Point(padCenter);
            this.padBounds = copyPositive(padBounds, "pad");
            this.probeCenter = new Point(probeCenter);
            this.probeBounds = copyPositive(probeBounds, "probe");
            this.lead = lead.copy();
            this.escapeDx = escapeDx;
            this.escapeDy = escapeDy;
            this.escapeLength = escapeLength;
        }

        private Terminal(Terminal source) {
            this(source.terminalId, source.padCenter, source.padBounds, source.probeCenter,
                source.probeBounds, source.lead, source.escapeDx, source.escapeDy,
                source.escapeLength);
        }

        private Terminal copy() { return new Terminal(this); }

        String getTerminalId() { return terminalId; }
        Point getPadCenter() { return new Point(padCenter); }
        Rectangle getPadBounds() { return new Rectangle(padBounds); }
        Point getProbeCenter() { return new Point(probeCenter); }
        Rectangle getProbeBounds() { return new Rectangle(probeBounds); }
        Lead getLead() { return lead.copy(); }
        int getEscapeDx() { return escapeDx; }
        int getEscapeDy() { return escapeDy; }
        int getEscapeLength() { return escapeLength; }
    }

    /** Immutable straight lead segment from the pad to the visible body. */
    static final class Lead {
        private final Point padPoint;
        private final Point bodyPoint;
        private final Rectangle bounds;

        Lead(Point padPoint, Point bodyPoint, Rectangle bounds) {
            if (padPoint == null || bodyPoint == null || bounds == null)
                throw new IllegalArgumentException("Invalid package lead geometry");
            this.padPoint = new Point(padPoint);
            this.bodyPoint = new Point(bodyPoint);
            this.bounds = copyPositive(bounds, "lead");
        }

        private Lead(Lead source) {
            this(source.padPoint, source.bodyPoint, source.bounds);
        }

        private Lead copy() { return new Lead(this); }

        Point getPadPoint() { return new Point(padPoint); }
        Point getBodyPoint() { return new Point(bodyPoint); }
        Rectangle getBounds() { return new Rectangle(bounds); }
    }

    /** Deterministic fallback used only by custom developer package definitions. */
    static PhysicalPackageGeometry generic(Vector<String> terminalIds, boolean connector) {
        if (terminalIds == null || terminalIds.size() == 0)
            throw new IllegalArgumentException("Missing package terminals");
        int width = 150;
        int height = 60 + (terminalIds.size() - 1) * 40;
        int padX = width - 30;
        int escapeDx = 1;
        Vector<Terminal> terminals = new Vector<Terminal>();
        for (int index = 0; index < terminalIds.size(); index++) {
            int padY = 30 + index * 40;
            Point pad = new Point(padX, padY);
            Rectangle padBounds = centered(pad, 26, 26);
            Point probe = new Point(padX - 20, padY);
            Rectangle probeBounds = centered(probe, 46, 46).union(padBounds);
            Point body = new Point(width - 40, padY);
            terminals.add(new Terminal(terminalIds.get(index), pad, padBounds, probe,
                probeBounds, new Lead(pad, body, centered(pad, 30, 30)), escapeDx, 0, 30));
        }
        Rectangle body = new Rectangle(10, 10, width - 50, height - 20);
        Rectangle keepOut = new Rectangle(10, 10, width - 50, height - 20);
        Rectangle courtyard = new Rectangle(10, 10, width - 20, height - 20);
        Rectangle selection = new Rectangle(-4, -4, width + 8, height + 8);
        Rectangle drag = new Rectangle(-10, -10, width + 20, height + 20);
        return new PhysicalPackageGeometry(width, height, terminals, body, keepOut, courtyard,
            selection, drag);
    }

    private void validate() {
        // Routing owns body/pad/lead exclusion and selection owns every
        // declared interaction target.  Probe bounds are terminal-owned and
        // deliberately include that terminal's pad, while nominal dimensions
        // remain only placement dimensions (see the class contract above).
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
                    !contains(routingCourtyard, terminal.lead.getBounds()) ||
                    !contains(selectionEnvelope, terminal.padBounds) ||
                    !contains(selectionEnvelope, terminal.probeBounds) ||
                    !contains(selectionEnvelope, terminal.lead.getBounds()) ||
                    !contains(dragEnvelope, terminal.probeBounds) ||
                    !contains(dragEnvelope, terminal.padBounds) ||
                    !contains(dragEnvelope, terminal.lead.getBounds()) ||
                    !contains(terminal.probeBounds, terminal.padBounds) ||
                    !contains(terminal.probeBounds, terminal.probeCenter.x,
                        terminal.probeCenter.y) ||
                    !contains(terminal.padBounds, terminal.padCenter.x, terminal.padCenter.y) ||
                    !contains(terminal.lead.getBounds(), terminal.padCenter.x,
                        terminal.padCenter.y) ||
                    !contains(terminal.lead.getBounds(), terminal.lead.getBodyPoint().x,
                        terminal.lead.getBodyPoint().y) ||
                    !terminal.lead.getPadPoint().equals(terminal.padCenter) ||
                    !contains(bodyBounds, terminal.lead.getBodyPoint().x,
                        terminal.lead.getBodyPoint().y))
                throw new IllegalArgumentException("Package terminal geometry is inconsistent: " +
                    terminal.terminalId);
            int distance = Math.abs(terminal.probeCenter.x - terminal.padCenter.x) +
                Math.abs(terminal.probeCenter.y - terminal.padCenter.y);
            if (distance > 80)
                throw new IllegalArgumentException("Package probe is too far from pad: " +
                    terminal.terminalId);
        }
    }

    boolean isEquivalentTo(PhysicalPackageGeometry other) {
        if (other == null || width != other.width || height != other.height ||
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
                    !first.probeCenter.equals(second.probeCenter) ||
                    !first.probeBounds.equals(second.probeBounds) ||
                    first.escapeDx != second.escapeDx || first.escapeDy != second.escapeDy ||
                    first.escapeLength != second.escapeLength ||
                    !first.lead.padPoint.equals(second.lead.padPoint) ||
                    !first.lead.bodyPoint.equals(second.lead.bodyPoint) ||
                    !first.lead.bounds.equals(second.lead.bounds))
                return false;
        }
        return true;
    }

    boolean hasTerminalIds(Vector<String> expected) {
        return expected != null && getTerminalIds().equals(expected);
    }

    private static Rectangle copyPositive(Rectangle value, String name) {
        if (value.width <= 0 || value.height <= 0)
            throw new IllegalArgumentException("Invalid package " + name + " bounds");
        return new Rectangle(value);
    }

    private static Rectangle centered(Point center, int width, int height) {
        return new Rectangle(center.x - width / 2, center.y - height / 2, width, height);
    }

    private Rectangle mirrorRect(Rectangle value) {
        return new Rectangle(width - (value.x + value.width), value.y, value.width,
            value.height);
    }

    private Point mirrorPoint(Point value) {
        return new Point(width - value.x, value.y);
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
}
