package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Small real-package placements for the private copper-free P07 branch canaries. */
public final class CopperFreeBranchFixtures {
    static final String NET_ID = "A_COIL_LOW";
    static final String START_PAD_ID = "QA.C";
    static final String ROOT_PAD_ID = "DA.A";

    private static final int START_X = 300;
    private static final int START_Y = 300;
    private static final int ROOT_X = 430;
    private static final int ROOT_Y = 300;

    // The open bottom-face route has a clear Manhattan witness:
    // (300,300) -> (300,340) -> (380,340) -> (380,300) -> (430,300).

    /** The fixture intentionally contains a two-terminal partial-route target. */
    public static final class Fixture {
        public final TroubleshootBoard board;
        public final PcbBoardLayout layout;

        private Fixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board;
            this.layout = layout;
        }
    }

    private CopperFreeBranchFixtures() { }

    public static Fixture open() {
        return create(false);
    }

    public static Fixture blocked() {
        return create(true);
    }

    private static Fixture create(boolean surroundStart) {
        TroubleshootBoard board = new TroubleshootBoard(
            surroundStart ? "COPPER_FREE_BLOCKED_BRANCH" : "COPPER_FREE_OPEN_BRANCH");
        PcbBoardLayout layout = new PcbBoardLayout(900, 700,
            new Rectangle(100, 100, 600, 500), new Rectangle(720, 120, 160, 400));

        addNet(board, NET_ID, BoardNet.RoutingRole.HIGH_CURRENT);
        addNet(board, "QA_BASE", BoardNet.RoutingRole.SIGNAL);
        addNet(board, "QA_EMITTER", BoardNet.RoutingRole.SIGNAL);
        addNet(board, "DA_CATHODE", BoardNet.RoutingRole.SIGNAL);

        PhysicalPackage transistor = PhysicalPackages.TO92_NPN;
        addPackage(board, layout, "QA", transistor,
            new String[] {"QA_BASE", NET_ID, "QA_EMITTER"},
            PcbPackagePose.top(START_X - 60, START_Y - 90));

        PhysicalPackage diode = PhysicalPackages.AXIAL_DIODE;
        addPackage(board, layout, "DA", diode,
            new String[] {NET_ID, "DA_CATHODE"},
            PcbPackagePose.top(ROOT_X - 30, ROOT_Y - 30));

        if (surroundStart) {
            addBlocker(board, layout, "BLOCK_E", "BLOCK_E_NET", START_X + 20, START_Y,
                PcbRotation.DEG_180);
            addBlocker(board, layout, "BLOCK_W", "BLOCK_W_NET", START_X - 20, START_Y,
                PcbRotation.DEG_0);
            addBlocker(board, layout, "BLOCK_N", "BLOCK_N_NET", START_X, START_Y - 20,
                PcbRotation.DEG_90);
            addBlocker(board, layout, "BLOCK_S", "BLOCK_S_NET", START_X, START_Y + 20,
                PcbRotation.DEG_270);
        }

        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds)
            parts.add(new PcbPlacementConstraints.Part(componentId, "copper-free",
                "Copper-free branch canary", "board", PcbPlacementConstraints.Anchor.NONE, 16));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, true,
            PcbTraceWidthProfile.ROLE_WIDTHS));

        requireStructuralContract(board, layout);
        return new Fixture(board, layout);
    }

    private static void addNet(TroubleshootBoard board, String netId,
            BoardNet.RoutingRole role) {
        board.addNet(new BoardNet(netId, role));
    }

    private static void addPackage(TroubleshootBoard board, PcbBoardLayout layout,
            String componentId, PhysicalPackage physicalPackage, String[] netIds,
            PcbPackagePose pose) {
        Vector<String> terminals = physicalPackage.getTerminalIds();
        if (netIds == null || netIds.length != terminals.size())
            throw new IllegalArgumentException("Fixture nets do not match package terminals: " +
                componentId);
        BoardComponent component = new BoardComponent(componentId, physicalPackage.getId(),
            physicalPackage, componentId);
        board.addComponent(component);
        for (int index = 0; index < terminals.size(); index++) {
            String terminal = terminals.get(index);
            board.addPad(new BoardPad(componentId + "." + terminal, componentId, terminal,
                netIds[index]));
        }
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
            physicalPackage.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    /**
     * P06's existing one-pad PTH test package provides four physical foreign
     * lands. Their centers are 20 units from QA.C, so the maintained width-13
     * stroke for each cardinal first edge touches a real land on both faces.
     */
    private static void addBlocker(TroubleshootBoard board, PcbBoardLayout layout,
            String componentId, String netId, int padX, int padY, PcbRotation rotation) {
        addNet(board, netId, BoardNet.RoutingRole.SIGNAL);
        PhysicalPackage physicalPackage = P06FactoryLinkFixtures.TEST_POINT;
        BoardComponent component = new BoardComponent(componentId, physicalPackage.getId(),
            physicalPackage, componentId);
        board.addComponent(component);
        board.addPad(new BoardPad(componentId + ".1", componentId, "1", netId));

        PcbPackagePose reference = new PcbPackagePose(0, 0, rotation, PcbBoardSide.BOTTOM);
        Point referencePad = physicalPackage.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(padX - referencePad.x,
            padY - referencePad.y, rotation, PcbBoardSide.BOTTOM);
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
            physicalPackage.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static void requireStructuralContract(TroubleshootBoard board,
            PcbBoardLayout layout) {
        board.validate();
        layout.validateAgainst(board);
        if (layout.getTraces().size() != 0 || layout.getHoles().size() != 0)
            throw new IllegalStateException("Copper-free fixture must start without traces or holes");
        if (board.getPlacementConstraints().getTraceWidthProfile() !=
                PcbTraceWidthProfile.ROLE_WIDTHS ||
                board.getPlacementConstraints().routingLayer != PcbCopperLayer.TOP ||
                !board.getPlacementConstraints().requiresTwoLayerRouting)
            throw new IllegalStateException("Fixture lost its role-width two-layer contract");
        if (board.getNet(NET_ID).getRoutingRole() != BoardNet.RoutingRole.HIGH_CURRENT ||
                PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(
                    board.getNet(NET_ID).getRoutingRole()) != 13)
            throw new IllegalStateException("Target net must use the role-width high-current trace");

        Vector<String> targetPads = board.getNet(NET_ID).getPadIds();
        Collections.sort(targetPads);
        if (targetPads.size() != 2 || !ROOT_PAD_ID.equals(targetPads.get(0)) ||
                !START_PAD_ID.equals(targetPads.get(1)))
            throw new IllegalStateException("Target net must contain only QA.C and DA.A");

        Rectangle outline = layout.getBoardOutline();
        long gridWidth = (outline.width - 20L) / 10L + 1L;
        long gridHeight = (outline.height - 20L) / 10L + 1L;
        if (outline.width > 600 || outline.height > 500 || gridWidth * gridHeight >= 10000)
            throw new IllegalStateException("Fixture exceeds the bounded routing grid");
        for (PcbPadPlacement pad : layout.getPads()) {
            if (pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE)
                throw new IllegalStateException("Fixture contains a non-PTH terminal: " +
                    pad.getPadId());
            if (pad.getX() % 10 != 0 || pad.getY() % 10 != 0)
                throw new IllegalStateException("Fixture pad is not on the 10-unit routing grid: " +
                    pad.getPadId());
        }
    }
}
