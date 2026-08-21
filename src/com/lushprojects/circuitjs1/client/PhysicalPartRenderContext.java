package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Narrow rendering context. Providers use it for board identity and transforms;
 * they do not reach into the renderer's canvas orchestration.
 */
final class PhysicalPartRenderContext {
    private final PcbWorkbenchRenderer renderer;
    private final PcbComponentPlacement placement;
    private final PhysicalPart<?> part;
    private final PhysicalPackage physicalPackage;
    private final int trayIndex;
    private final boolean loose;
    private final TroubleshootBoard developerCanaryBoard;
    private final HashMap<String, Point> developerCanaryPadPoints;
    private boolean bodyDrawn;

    PhysicalPartRenderContext(PcbWorkbenchRenderer renderer, PcbComponentPlacement placement,
            PhysicalPart<?> part, PhysicalPackage physicalPackage, int trayIndex, boolean loose) {
        this(renderer, placement, part, physicalPackage, trayIndex, loose, null, null);
    }

    PhysicalPartRenderContext(PcbWorkbenchRenderer renderer, PcbComponentPlacement placement,
            PhysicalPart<?> part, PhysicalPackage physicalPackage, int trayIndex, boolean loose,
            TroubleshootBoard developerCanaryBoard,
            HashMap<String, Point> developerCanaryPadPoints) {
        if (renderer == null || physicalPackage == null)
            throw new IllegalArgumentException("Invalid physical render context");
        if ((developerCanaryBoard == null) != (developerCanaryPadPoints == null))
            throw new IllegalArgumentException("Incomplete developer render canary context");
        this.renderer = renderer;
        this.placement = placement;
        this.part = part;
        this.physicalPackage = physicalPackage;
        this.trayIndex = trayIndex;
        this.loose = loose;
        this.developerCanaryBoard = developerCanaryBoard;
        this.developerCanaryPadPoints = developerCanaryPadPoints == null ? null :
            new HashMap<String, Point>(developerCanaryPadPoints);
        if (placement != null && developerCanaryBoard == null)
            renderer.observeInstalledProjection(getComponentId());
    }

    PcbWorkbenchRenderer getRenderer() { return renderer; }
    GeneratedBoardInstance getInstance() { return renderer.getInstanceForProvider(); }
    BoardModificationController getModifications() { return renderer.getModificationsForProvider(); }
    PcbComponentPlacement getPlacement() { return placement; }
    PhysicalPart<?> getPart() { return part; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    PhysicalPackageGeometry getPhysicalGeometry() { return installedPhysicalGeometry(); }
    int getTrayIndex() { return trayIndex; }
    boolean isLoose() { return loose; }
    boolean isDeveloperCanary() { return developerCanaryBoard != null; }
    /** Physical visibility is owned by slot occupancy, not electrical graph state. */
    boolean isInstalledPartMounted() {
        if (developerCanaryBoard != null)
            return true;
        if (placement == null || part == null || !part.isInstalled())
            return false;
        PhysicalBoardSlot slot = getInstance().getPhysicalBoardRuntime()
            .getSlot(getComponentId());
        return slot != null && slot.getInstalledPart() == part && part.getBoardSlot() == slot;
    }
    void markBodyDrawn() { bodyDrawn = true; }
    boolean wasBodyDrawn() { return bodyDrawn; }

    ComponentPhysicalState getComponentState() {
        if (developerCanaryBoard != null || placement == null)
            return ComponentPhysicalState.INSTALLED;
        if (getInstance().getConnectionBindings().getForComponentOrEmpty(getComponentId()).isEmpty())
            return ComponentPhysicalState.INSTALLED;
        return getModifications().getComponentState(getComponentId());
    }

    String getComponentId() {
        return placement == null ? null : placement.getComponentId();
    }

    int getTerminalCount() {
        return part == null ? physicalPackage.getTerminalCount() : part.getTerminalCount();
    }

    String getTerminalName(int terminal) {
        if (part != null)
            return part.getTerminal(terminal).getTerminalName();
        return physicalPackage.getTerminalIds().get(terminal);
    }

    Point getBoardPadPoint(int terminal) {
        String padId = getBoardPadId(terminal);
        if (padId == null)
            return null;
        if (developerCanaryBoard != null)
            return renderer.getProviderCanaryPadPoint(padId, developerCanaryPadPoints);
        if (placement != null)
            return getInstalledBoardPadPoint(terminal);
        return renderer.getPadPoint(padId);
    }

    String getBoardPadId(int terminal) {
        if (placement == null || terminal < 0 || terminal >= getTerminalCount())
            return null;
        BoardComponent component = getBoardForProvider().getComponent(getComponentId());
        if (component == null)
            return null;
        String terminalName = getTerminalName(terminal);
        for (String padId : component.getPadIds()) {
            BoardPad pad = getBoardForProvider().getPad(padId);
            if (pad != null && terminalName.equals(pad.getTerminalId()))
                return padId;
        }
        return null;
    }

    Point getComponentProbePoint(int terminal) {
        if (placement == null || getBoardForProvider().getComponent(getComponentId()) == null)
            return getSyntheticTerminalPoint(terminal);
        // The active installed probe surface is the board pad while connected
        // and the exact detached component lead while lifted.  Electrical
        // removal alone does not move the physically mounted part to the tray.
        if (developerCanaryBoard != null)
            return getBoardPadPoint(terminal);
        return isLeadConnected(terminal) ? getInstalledBoardPadPoint(terminal) :
            getInstalledComponentLeadPoint(terminal);
    }

    Point getMountedLeadEnd(int terminal) {
        if (placement != null)
            return getInstalledLeadEndPoint(terminal);
        return getComponentProbePoint(terminal);
    }

    Point getProviderTerminalPoint(int terminal) {
        if (placement != null && developerCanaryBoard == null) {
            Point point = getInstalledBoardPadPoint(terminal);
            if (point != null)
                return point;
        }
        Point pad = getBoardPadPoint(terminal);
        return pad == null ? getSyntheticTerminalPoint(terminal) : pad;
    }

    Rectangle getInstalledBodyBounds() {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(installedPhysicalGeometry().placedAt(
            placement.getX(), placement.getY()).getBodyBounds());
    }

    Point getInstalledLeadBodyPoint(int terminal) {
        if (placement == null)
            return new Point(0, 0);
        Point point = getInstalledPlacedGeometry().getLeadBodyPoint(terminal,
            !isLeadConnected(terminal));
        return point == null ? getSyntheticTerminalPoint(terminal) :
            new Point(screenX(point.x), screenY(point.y));
    }

    Point getInstalledLeadEndPoint(int terminal) {
        if (placement == null)
            return new Point(0, 0);
        Point point = getInstalledPlacedGeometry().getLeadEndPoint(terminal,
            !isLeadConnected(terminal));
        return point == null ? getSyntheticTerminalPoint(terminal) :
            new Point(screenX(point.x), screenY(point.y));
    }

    Point getInstalledBoardPadPoint(int terminal) {
        if (placement == null)
            return null;
        if (developerCanaryBoard != null)
            return getBoardPadPoint(terminal);
        Point point = getInstalledPlacedGeometry().getBoardPadProbeCenter(terminal);
        return point == null ? null : new Point(screenX(point.x), screenY(point.y));
    }

    Rectangle getInstalledBoardPadProbeBounds(int terminal) {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(getInstalledPlacedGeometry()
            .getBoardPadProbeBounds(terminal));
    }

    Point getInstalledComponentLeadPoint(int terminal) {
        if (placement == null)
            return getSyntheticTerminalPoint(terminal);
        Point point = getInstalledPlacedGeometry().getComponentLeadProbeCenter(terminal,
            !isLeadConnected(terminal));
        return point == null ? getSyntheticTerminalPoint(terminal) :
            new Point(screenX(point.x), screenY(point.y));
    }

    Rectangle getInstalledComponentLeadProbeBounds(int terminal) {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(getInstalledPlacedGeometry()
            .getComponentLeadProbeBounds(terminal, !isLeadConnected(terminal)));
    }

    Rectangle getInstalledSelectionBounds() {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(installedPhysicalGeometry().placedAt(
            placement.getX(), placement.getY()).getSelectionEnvelope());
    }

    Rectangle getInstalledDragBounds() {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(installedPhysicalGeometry().placedAt(
            placement.getX(), placement.getY()).getDragEnvelope());
    }

    Rectangle getInstalledProbeBounds(int terminal) {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return isLeadConnected(terminal) ? getInstalledBoardPadProbeBounds(terminal) :
            getInstalledComponentLeadProbeBounds(terminal);
    }

    Rectangle getInstalledPadBounds(int terminal) {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(getInstalledPlacedGeometry()
            .getPadBounds(terminal));
    }

    Rectangle getInstalledLeadBounds(int terminal) {
        if (placement == null)
            return new Rectangle(0, 0, 1, 1);
        return renderer.screenRectForProvider(getInstalledPlacedGeometry().getLeadBounds(terminal,
            !isLeadConnected(terminal)));
    }

    Point getLooseTerminalPoint(int terminal, boolean reversed) {
        Point point = getLooseTerminalLayoutPoint(terminal, reversed);
        return new Point(renderer.screenXForProvider(point.x),
            renderer.screenYForProvider(point.y));
    }

    Rectangle getComponentBounds() {
        return placement == null ? new Rectangle(0, 0, 1, 1) : renderer.screenRectForProvider(placement);
    }

    Rectangle getRemovedComponentBounds() {
        Point first = getComponentProbePoint(0);
        Point last = getComponentProbePoint(Math.max(0, getTerminalCount() - 1));
        int left = Math.min(first.x, last.x) - renderer.scaleIntForProvider(8);
        int top = first.y - renderer.scaleIntForProvider(35);
        return new Rectangle(left, top, renderer.scaleIntForProvider(145),
            renderer.scaleIntForProvider(70));
    }

    Rectangle getLooseBounds(boolean reversed) {
        return getLooseSelectionBounds(reversed);
    }

    /**
     * Tray layout is deliberately horizontal, while package geometry is
     * package-local.  This adapter anchors the declared package envelope at
     * terminal zero and translates each terminal's own pad/probe/lead
     * rectangles to its tray lead end.  It keeps tray chrome separate without
     * replacing terminal interaction geometry with a generic hit radius.
     */
    Rectangle getLooseSelectionBounds(boolean reversed) {
        PhysicalPackageGeometry geometry = physicalPackage.getGeometry();
        Rectangle result = translateLoosePackageRectangle(geometry.getSelectionEnvelope(),
            reversed);
        result = result.union(getLooseBodyBounds(reversed));
        for (int terminal = 0; terminal < getTerminalCount(); terminal++) {
            result = result.union(getLooseProbeBounds(terminal, reversed));
            result = result.union(getLoosePadBounds(terminal, reversed));
            result = result.union(getLooseLeadBounds(terminal, reversed));
        }
        return result;
    }

    Rectangle getLooseBodyBounds(boolean reversed) {
        return translateLoosePackageRectangle(physicalPackage.getGeometry().getBodyBounds(),
            reversed);
    }

    Rectangle getLooseDragBounds(boolean reversed) {
        Rectangle result = translateLoosePackageRectangle(physicalPackage.getGeometry()
            .getDragEnvelope(), reversed);
        return result.union(getLooseSelectionBoundsWithoutDrag(reversed));
    }

    Rectangle getLooseProbeBounds(int terminal, boolean reversed) {
        PhysicalPackageGeometry.Terminal declared = getDeclaredTerminal(terminal);
        return translateLooseTerminalRectangle(declared.getProbeBounds(), terminal, reversed);
    }

    Rectangle getLoosePadBounds(int terminal, boolean reversed) {
        PhysicalPackageGeometry.Terminal declared = getDeclaredTerminal(terminal);
        return translateLooseTerminalRectangle(declared.getPadBounds(), terminal, reversed);
    }

    Rectangle getLooseLeadBounds(int terminal, boolean reversed) {
        PhysicalPackageGeometry.Terminal declared = getDeclaredTerminal(terminal);
        return translateLooseTerminalRectangle(declared.getLead().getBounds(), terminal, reversed);
    }

    boolean isComponentRemoved() {
        if (placement == null)
            return false;
        if (developerCanaryBoard != null)
            return false;
        Vector<GeneratedComponentConnectionBinding> bindings = getInstance()
            .getConnectionBindings().getForComponentOrEmpty(getComponentId());
        return !bindings.isEmpty() && getModifications().getComponentState(getComponentId()) ==
            ComponentPhysicalState.REMOVED;
    }

    boolean isLeadConnected(int terminal) {
        String padId = getBoardPadId(terminal);
        if (padId == null)
            return false;
        if (developerCanaryBoard != null)
            return true;
        if (getInstance().getConnectionBindings().getForComponentOrEmpty(getComponentId()).isEmpty())
            return true;
        if (isComponentRemoved())
            return false;
        return getModifications().isLeadConnected(getComponentId(), padId);
    }

    boolean isIlluminated() {
        return placement != null && getInstance().getOperationalStates().isIlluminated(getComponentId());
    }

    int screenX(int value) { return renderer.screenXForProvider(value); }
    int screenY(int value) { return renderer.screenYForProvider(value); }
    int scale(int value) { return renderer.scaleIntForProvider(value); }

    private Point getSyntheticTerminalPoint(int terminal) {
        if (placement == null)
            return new Point(0, 0);
        int count = Math.max(1, getTerminalCount());
        int y = placement.getY() + 30 + terminal *
            Math.max(24, placement.getHeight() / count);
        int x = placement.getX() + placement.getWidth() - 30;
        return new Point(screenX(x), screenY(y));
    }

    private Point getPackageProbePoint(int terminal) {
        if (placement == null)
            return getSyntheticTerminalPoint(terminal);
        Point point = installedPhysicalGeometry().placedAt(placement.getX(), placement.getY())
            .getProbePoint(terminal);
        return point == null ? getSyntheticTerminalPoint(terminal) :
            new Point(screenX(point.x), screenY(point.y));
    }

    private Point getLooseTerminalLayoutPoint(int terminal, boolean reversed) {
        Rectangle tray = renderer.getPartsTrayForProvider();
        int count = Math.max(1, getTerminalCount());
        int slot = reversed ? count - terminal - 1 : terminal;
        int x = count == 1 ? tray.x + tray.width / 2 :
            tray.x + 18 + (tray.width - 36) * slot / (count - 1);
        return new Point(x, tray.y + 70 + trayIndex * 48);
    }

    private Rectangle translateLoosePackageRectangle(Rectangle local, boolean reversed) {
        PhysicalPackageGeometry.Terminal anchor = getDeclaredTerminal(0);
        Point target = getLooseTerminalLayoutPoint(0, reversed);
        Point source = anchor.getPadCenter();
        Rectangle translated = new Rectangle(local.x + target.x - source.x,
            local.y + target.y - source.y, local.width, local.height);
        return renderer.screenRectForProvider(translated);
    }

    private Rectangle translateLooseTerminalRectangle(Rectangle local, int terminal,
            boolean reversed) {
        PhysicalPackageGeometry.Terminal declared = getDeclaredTerminal(terminal);
        Point target = getLooseTerminalLayoutPoint(terminal, reversed);
        Point source = declared.getPadCenter();
        Rectangle translated = new Rectangle(local.x + target.x - source.x,
            local.y + target.y - source.y, local.width, local.height);
        return renderer.screenRectForProvider(translated);
    }

    private Rectangle getLooseSelectionBoundsWithoutDrag(boolean reversed) {
        Rectangle result = translateLoosePackageRectangle(physicalPackage.getGeometry()
            .getSelectionEnvelope(), reversed);
        result = result.union(getLooseBodyBounds(reversed));
        for (int terminal = 0; terminal < getTerminalCount(); terminal++) {
            result = result.union(getLooseProbeBounds(terminal, reversed));
            result = result.union(getLoosePadBounds(terminal, reversed));
            result = result.union(getLooseLeadBounds(terminal, reversed));
        }
        return result;
    }

    private PhysicalPackageGeometry.Terminal getDeclaredTerminal(int terminal) {
        PhysicalPackageGeometry.Terminal declared = physicalPackage.getGeometry()
            .getTerminal(terminal);
        if (declared == null)
            throw new IllegalStateException("Package geometry omitted terminal " + terminal +
                " for " + physicalPackage.getId());
        return declared;
    }

    private PhysicalPackageGeometry installedPhysicalGeometry() {
        return placement != null && placement.getPhysicalGeometry() != null ?
            placement.getPhysicalGeometry() : physicalPackage.getGeometry();
    }

    private PhysicalPackageGeometry.Placement getInstalledPlacedGeometry() {
        if (placement == null)
            return physicalPackage.getGeometry().placedAt(0, 0);
        return installedPhysicalGeometry().placedAt(placement.getX(), placement.getY());
    }

    private TroubleshootBoard getBoardForProvider() {
        return developerCanaryBoard == null ? getInstance().getBoard() : developerCanaryBoard;
    }
}
