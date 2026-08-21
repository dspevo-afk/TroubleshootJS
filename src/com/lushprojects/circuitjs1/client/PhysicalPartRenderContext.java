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
    private final LoosePartPose loosePose;
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
        this.loosePose = loose ? LoosePartPose.forPart(physicalPackage, part,
            renderer.getPartsTrayForProvider(), trayIndex) : null;
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
    PhysicalPackageGeometry getPhysicalGeometry() {
        return loose ? loosePose.getSourceGeometry() : installedPhysicalGeometry();
    }
    int getTrayIndex() { return trayIndex; }
    boolean isLoose() { return loose; }
    LoosePartPose getLoosePose() {
        if (!loose || loosePose == null)
            throw new IllegalStateException("Installed context has no loose pose");
        return loosePose;
    }
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
        return renderer.screenPointForProvider(getLoosePose().getTerminalPoint(terminal));
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

    Rectangle getLooseSelectionBounds(boolean reversed) {
        return renderer.screenRectForProvider(getLoosePose().getSelectionEnvelope());
    }

    Rectangle getLooseBodyBounds(boolean reversed) {
        return renderer.screenRectForProvider(getLoosePose().getBodyBounds());
    }

    Rectangle getLooseDragBounds(boolean reversed) {
        return renderer.screenRectForProvider(getLoosePose().getDragEnvelope());
    }

    Rectangle getLooseProbeBounds(int terminal, boolean reversed) {
        return renderer.screenRectForProvider(getLoosePose().getProbeBounds(terminal));
    }

    Rectangle getLoosePadBounds(int terminal, boolean reversed) {
        return renderer.screenRectForProvider(getLoosePose().getPadBounds(terminal));
    }

    Rectangle getLooseLeadBounds(int terminal, boolean reversed) {
        return renderer.screenRectForProvider(getLoosePose().getLeadBounds(terminal));
    }

    Point getLooseComponentLeadPoint(int terminal) {
        return renderer.screenPointForProvider(getLoosePose().getComponentLeadPoint(terminal));
    }

    Rectangle getLooseComponentLeadProbeBounds(int terminal) {
        return renderer.screenRectForProvider(getLoosePose()
            .getComponentLeadProbeBounds(terminal));
    }

    Point getLooseLeadBodyPoint(int terminal) {
        return renderer.screenPointForProvider(getLoosePose().getLeadBodyPoint(terminal));
    }

    Point getLooseLeadEndPoint(int terminal) {
        return renderer.screenPointForProvider(getLoosePose().getLeadEndPoint(terminal));
    }

    Vector<Rectangle> getLooseProbeSurfaces(int terminal) {
        Vector<Rectangle> result = new Vector<Rectangle>();
        for (Rectangle surface : getLoosePose().getProbeSurfaces(terminal))
            result.add(renderer.screenRectForProvider(surface));
        return result;
    }

    int getLooseLeadStrokeWidth(Rectangle transformedLeadBounds) {
        if (!loose || loosePose == null || transformedLeadBounds == null ||
                transformedLeadBounds.width <= 0 || transformedLeadBounds.height <= 0)
            throw new IllegalArgumentException("Loose lead stroke requires a positive pose");
        int poseWidth = renderer.scaleLengthForProvider(4.0 * loosePose.getScale());
        int containedWidth = Math.min(transformedLeadBounds.width, transformedLeadBounds.height);
        return Math.max(1, Math.min(Math.max(1, poseWidth), containedWidth));
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
