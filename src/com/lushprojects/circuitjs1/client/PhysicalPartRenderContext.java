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
    }

    PcbWorkbenchRenderer getRenderer() { return renderer; }
    GeneratedBoardInstance getInstance() { return renderer.getInstanceForProvider(); }
    BoardModificationController getModifications() { return renderer.getModificationsForProvider(); }
    PcbComponentPlacement getPlacement() { return placement; }
    PhysicalPart<?> getPart() { return part; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    int getTrayIndex() { return trayIndex; }
    boolean isLoose() { return loose; }
    boolean isDeveloperCanary() { return developerCanaryBoard != null; }
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
        Point pad = getBoardPadPoint(terminal);
        if (pad == null || getBoardForProvider().getComponent(getComponentId()) == null)
            return getSyntheticTerminalPoint(terminal);
        if (isComponentRemoved()) {
            Rectangle tray = renderer.getPartsTrayForProvider();
            int count = Math.max(1, getTerminalCount());
            int x;
            if (count == 1)
                x = tray.x + tray.width / 2;
            else
                x = tray.x + 18 + (tray.width - 36) * terminal / (count - 1);
            return new Point(renderer.screenXForProvider(x),
                renderer.screenYForProvider(tray.y + 125));
        }
        boolean connected = isLeadConnected(terminal);
        int direction = terminal == 0 ? 1 : -1;
        return new Point(renderer.screenXForProvider(
                getLogicalPadX(terminal) + direction * (connected ? 25 : 20)),
            renderer.screenYForProvider(
                getLogicalPadY(terminal) - (connected ? 20 : 28)));
    }

    Point getMountedLeadEnd(int terminal) {
        Point pad = getBoardPadPoint(terminal);
        return pad == null || !isLeadConnected(terminal) ? getComponentProbePoint(terminal) : pad;
    }

    Point getProviderTerminalPoint(int terminal) {
        Point pad = getBoardPadPoint(terminal);
        return pad == null ? getSyntheticTerminalPoint(terminal) : pad;
    }

    Point getLooseTerminalPoint(int terminal, boolean reversed) {
        Rectangle tray = renderer.getPartsTrayForProvider();
        int count = Math.max(1, getTerminalCount());
        int slot = reversed ? count - terminal - 1 : terminal;
        int x = count == 1 ? tray.x + tray.width / 2 :
            tray.x + 18 + (tray.width - 36) * slot / (count - 1);
        return new Point(renderer.screenXForProvider(x),
            renderer.screenYForProvider(tray.y + 70 + trayIndex * 48));
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
        Point first = getLooseTerminalPoint(0, reversed);
        Point last = getLooseTerminalPoint(Math.max(0, getTerminalCount() - 1), reversed);
        int top = first.y - renderer.scaleIntForProvider(35);
        if (getTerminalCount() == 2)
            return new Rectangle(Math.min(first.x, last.x) - renderer.scaleIntForProvider(8), top,
                renderer.scaleIntForProvider(145), renderer.scaleIntForProvider(70));
        int left = Math.min(first.x, last.x) - renderer.scaleIntForProvider(8);
        int right = Math.max(first.x, last.x) + renderer.scaleIntForProvider(8);
        return new Rectangle(left, top, Math.max(renderer.scaleIntForProvider(145), right - left),
            renderer.scaleIntForProvider(70));
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

    private int getLogicalPadX(int terminal) {
        String padId = getBoardPadId(terminal);
        PcbPadPlacement pad = padId == null ? null : renderer.getLayoutForProvider().getPad(padId);
        return pad == null ? placement.getX() : pad.getX();
    }

    private int getLogicalPadY(int terminal) {
        String padId = getBoardPadId(terminal);
        PcbPadPlacement pad = padId == null ? null : renderer.getLayoutForProvider().getPad(padId);
        return pad == null ? placement.getY() : pad.getY();
    }

    private TroubleshootBoard getBoardForProvider() {
        return developerCanaryBoard == null ? getInstance().getBoard() : developerCanaryBoard;
    }
}
