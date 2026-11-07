package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Real-package copper-free templates for the private failed-first ordering canary. */
public final class FailedFirstFixtures {
    private static final String NET_A = "NET_A";
    private static final String NET_B = "NET_B";

    /** The open witness is direct along each row; blocked() surrounds the A2 endpoint. */
    public static final class Fixture {
        public final TroubleshootBoard board;
        public final PcbBoardLayout layout;

        private Fixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board;
            this.layout = layout;
        }
    }

    private FailedFirstFixtures() { }

    public static Fixture open() {
        return create(false);
    }

    public static Fixture blocked() {
        return create(true);
    }

    private static Fixture create(boolean surroundA2) {
        TroubleshootBoard board = new TroubleshootBoard(
            surroundA2 ? "FAILED_FIRST_BLOCKED" : "FAILED_FIRST_OPEN");
        PcbBoardLayout layout = new PcbBoardLayout(900, 700,
            new Rectangle(100, 100, 600, 500), new Rectangle(720, 120, 160, 400));

        board.addNet(new BoardNet(NET_A, BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet(NET_B, BoardNet.RoutingRole.CONTROL));

        // The package body and directional keep-out face away from each row's clear path.
        addPoint(board, layout, "A1", NET_A, 260, 300, PcbRotation.DEG_180,
            PcbBoardSide.TOP);
        addPoint(board, layout, "A2", NET_A, 500, 300, PcbRotation.DEG_0,
            PcbBoardSide.TOP);
        addPoint(board, layout, "B1", NET_B, 260, 450, PcbRotation.DEG_180,
            PcbBoardSide.TOP);
        addPoint(board, layout, "B2", NET_B, 500, 450, PcbRotation.DEG_0,
            PcbBoardSide.TOP);

        if (surroundA2) {
            // PTH lands 20 units from A2 close every width-13 cardinal first edge
            // on either face. Their bottom-side bodies and directional keep-outs face
            // outward; their full-footprint routing courtyards remain non-overlapping.
            addBlocker(board, layout, "BLOCK_E", "BLOCK_E_NET", 520, 300,
                PcbRotation.DEG_180);
            addBlocker(board, layout, "BLOCK_W", "BLOCK_W_NET", 480, 300,
                PcbRotation.DEG_0);
            addBlocker(board, layout, "BLOCK_N", "BLOCK_N_NET", 500, 280,
                PcbRotation.DEG_90);
            addBlocker(board, layout, "BLOCK_S", "BLOCK_S_NET", 500, 320,
                PcbRotation.DEG_270);
        }

        declareConstraints(board);
        addReferenceLabels(board, layout);
        requireStructuralContract(board, layout, surroundA2);
        return new Fixture(board, layout);
    }

    private static void addBlocker(TroubleshootBoard board, PcbBoardLayout layout,
            String componentId, String netId, int x, int y, PcbRotation rotation) {
        board.addNet(new BoardNet(netId, BoardNet.RoutingRole.SIGNAL));
        addPoint(board, layout, componentId, netId, x, y, rotation, PcbBoardSide.BOTTOM);
    }

    private static void addPoint(TroubleshootBoard board, PcbBoardLayout layout,
            String componentId, String netId, int padX, int padY, PcbRotation rotation,
            PcbBoardSide side) {
        PhysicalPackage physicalPackage = P06FactoryLinkFixtures.TEST_POINT;
        Vector<String> terminals = physicalPackage.getTerminalIds();
        if (terminals.size() != 1)
            throw new IllegalStateException("P06 test-point package must remain single-terminal");
        BoardComponent component = new BoardComponent(componentId, physicalPackage.getId(),
            physicalPackage, componentId);
        board.addComponent(component);
        String terminal = terminals.get(0);
        board.addPad(new BoardPad(componentId + "." + terminal, componentId, terminal, netId));

        PcbPackagePose reference = new PcbPackagePose(0, 0, rotation, side);
        Point referencePad = physicalPackage.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(padX - referencePad.x,
            padY - referencePad.y, rotation, side);
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
            physicalPackage.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static void declareConstraints(TroubleshootBoard board) {
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for (String componentId : componentIds)
            parts.add(new PcbPlacementConstraints.Part(componentId, "failed-first-canary",
                "Failed-first canary", "board", PcbPlacementConstraints.Anchor.NONE, 16));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, true,
            PcbTraceWidthProfile.ROLE_WIDTHS));
    }

    private static void addReferenceLabels(TroubleshootBoard board, PcbBoardLayout layout) {
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (int index = 0; index < componentIds.size(); index++) {
            String componentId = componentIds.get(index);
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:" + componentId,
                componentId, new Rectangle(110 + index * 35, 110, 28, 18), 12, true, null));
        }
    }

    private static void requireStructuralContract(TroubleshootBoard board,
            PcbBoardLayout layout, boolean surroundA2) {
        board.validate();
        layout.validateAgainst(board);
        if (!layout.getTraces().isEmpty() || !layout.getHoles().isEmpty())
            throw new IllegalStateException("Fixture must start with no route copper or vias");
        if (board.getPlacementConstraints().getTraceWidthProfile() !=
                    PcbTraceWidthProfile.ROLE_WIDTHS ||
                board.getPlacementConstraints().routingLayer != PcbCopperLayer.TOP ||
                !board.getPlacementConstraints().requiresTwoLayerRouting)
            throw new IllegalStateException("Fixture lost its role-width two-layer contract");
        if (layout.getComponents().size() != board.getComponentIds().size() ||
                layout.getPads().size() != board.getPadIds().size())
            throw new IllegalStateException("Every logical component and pad needs a footprint");

        requireNet(board, NET_A, BoardNet.RoutingRole.HIGH_CURRENT,
            new String[] {"A1.1", "A2.1"});
        requireNet(board, NET_B, BoardNet.RoutingRole.CONTROL,
            new String[] {"B1.1", "B2.1"});
        if (PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(
                    board.getNet(NET_A).getRoutingRole()) != 13 ||
                PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(
                    board.getNet(NET_B).getRoutingRole()) != 9)
            throw new IllegalStateException("Target nets lost their declared physical widths");

        if (board.getNetIds().size() != (surroundA2 ? 6 : 2))
            throw new IllegalStateException("Fixture net census changed");
        if (surroundA2) {
            for (String netId : new String[] {
                    "BLOCK_E_NET", "BLOCK_W_NET", "BLOCK_N_NET", "BLOCK_S_NET"}) {
                Vector<String> padIds = board.getNet(netId).getPadIds();
                if (padIds.size() != 1)
                    throw new IllegalStateException("Foreign blocker net must be a singleton");
            }
        }

        Rectangle outline = layout.getBoardOutline();
        long gridWidth = (outline.width - 20L) / 10L + 1L;
        long gridHeight = (outline.height - 20L) / 10L + 1L;
        if (outline.x != 100 || outline.y != 100 || outline.width > 600 ||
                outline.height > 500 || gridWidth * gridHeight >= 10000)
            throw new IllegalStateException("Fixture exceeds its board or routing-grid envelope");

        Vector<PcbPadPlacement> pads = layout.getPads();
        for (PcbPadPlacement pad : pads) {
            if (pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    pad.getX() % 10 != 0 || pad.getY() % 10 != 0 ||
                    !containsBounds(outline, pad.getPadBounds()) ||
                    !containsBounds(outline, pad.getProbeBounds()))
                throw new IllegalStateException("Fixture pad must be an in-outline PTH grid point");
        }
        requireNoSameFaceOverlaps(layout);
        requireNoPadLandOverlaps(pads);
        requireOutward(layout, "A1", "A1.1", -1, 0);
        requireOutward(layout, "A2", "A2.1", 1, 0);
        requireOutward(layout, "B1", "B1.1", -1, 0);
        requireOutward(layout, "B2", "B2.1", 1, 0);
        if (surroundA2) {
            requireOutward(layout, "BLOCK_E", "BLOCK_E.1", 1, 0);
            requireOutward(layout, "BLOCK_W", "BLOCK_W.1", -1, 0);
            requireOutward(layout, "BLOCK_N", "BLOCK_N.1", 0, -1);
            requireOutward(layout, "BLOCK_S", "BLOCK_S.1", 0, 1);
        }
        // Full routing geometry validation is deliberately left to the native
        // study after routing: this copper-free template is not all-net connected.
    }

    private static void requireNet(TroubleshootBoard board, String netId,
            BoardNet.RoutingRole role, String[] expectedPads) {
        BoardNet net = board.getNet(netId);
        if (net == null || net.getRoutingRole() != role)
            throw new IllegalStateException("Fixture target net has the wrong role");
        Vector<String> padIds = net.getPadIds();
        Collections.sort(padIds);
        if (padIds.size() != expectedPads.length)
            throw new IllegalStateException("Fixture target net has the wrong pad count");
        for (int index = 0; index < expectedPads.length; index++)
            if (!expectedPads[index].equals(padIds.get(index)))
                throw new IllegalStateException("Fixture target net has different physical pads");
    }

    private static void requireNoSameFaceOverlaps(PcbBoardLayout layout) {
        Vector<PcbComponentPlacement> components = layout.getComponents();
        for (int first = 0; first < components.size(); first++) {
            PcbComponentPlacement a = components.get(first);
            if (!containsBounds(layout.getBoardOutline(), a.getBodyBounds()) ||
                    !containsBounds(layout.getBoardOutline(), a.getRoutingCourtyard()))
                throw new IllegalStateException("Fixture component leaves the board outline");
            for (int second = first + 1; second < components.size(); second++) {
                PcbComponentPlacement b = components.get(second);
                if (a.getMountingSide() != b.getMountingSide())
                    continue;
                if (a.getBodyBounds().intersects(b.getBodyBounds()))
                    throw new IllegalStateException("Fixture has same-face body overlap");
                if (a.getRoutingCourtyard().intersects(b.getRoutingCourtyard()))
                    throw new IllegalStateException("Fixture has same-face courtyard overlap");
            }
        }
    }

    private static void requireNoPadLandOverlaps(Vector<PcbPadPlacement> pads) {
        for (int first = 0; first < pads.size(); first++) {
            PcbPadPlacement a = pads.get(first);
            for (int second = first + 1; second < pads.size(); second++) {
                PcbPadPlacement b = pads.get(second);
                boolean commonFace =
                    PcbCopperAccess.hasCopper(a, PcbCopperLayer.TOP) &&
                        PcbCopperAccess.hasCopper(b, PcbCopperLayer.TOP) ||
                    PcbCopperAccess.hasCopper(a, PcbCopperLayer.BOTTOM) &&
                        PcbCopperAccess.hasCopper(b, PcbCopperLayer.BOTTOM);
                if (commonFace && a.getPadBounds().intersects(b.getPadBounds()))
                    throw new IllegalStateException("Fixture has overlapping physical pad lands");
            }
        }
    }

    private static void requireOutward(PcbBoardLayout layout, String componentId,
            String padId, int dx, int dy) {
        PcbComponentPlacement component = layout.getComponent(componentId);
        PcbPadPlacement pad = layout.getPad(padId);
        Rectangle body = component.getBodyBounds();
        // P06 TEST_POINT declares a full-footprint routing courtyard. Its
        // directional body keep-out, not that symmetric routing envelope, is
        // the package-backed outward component envelope checked here.
        Rectangle bodyKeepOut = component.getKeepOut();
        if (dx < 0 && (body.x + body.width > pad.getX() ||
                bodyKeepOut.x + bodyKeepOut.width > pad.getX()) ||
                dx > 0 && (body.x < pad.getX() || bodyKeepOut.x < pad.getX()) ||
                dy < 0 && (body.y + body.height > pad.getY() ||
                bodyKeepOut.y + bodyKeepOut.height > pad.getY()) ||
                dy > 0 && (body.y < pad.getY() || bodyKeepOut.y < pad.getY()))
            throw new IllegalStateException("Fixture body and package keep-out must face outward");
    }

    private static boolean containsBounds(Rectangle outer, Rectangle inner) {
        if (outer == null || inner == null || outer.width <= 0 || outer.height <= 0 ||
                inner.width <= 0 || inner.height <= 0)
            return false;
        long outerRight = (long) outer.x + outer.width;
        long outerBottom = (long) outer.y + outer.height;
        long innerRight = (long) inner.x + inner.width;
        long innerBottom = (long) inner.y + inner.height;
        return inner.x >= outer.x && inner.y >= outer.y &&
            innerRight <= outerRight && innerBottom <= outerBottom;
    }
}
