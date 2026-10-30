package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

/** Native contract for raw copper topology observations and the private physical oracle. */
public final class PhysicalCopperObservationContractTest {
    private static final String VERIFIER =
        "com.lushprojects.circuitjs1.client.GeneratedBoardPhysicalTruthDeveloperVerifier";
    private static Method validateAllTraces;

    private PhysicalCopperObservationContractTest() { }

    public static void main(String[] args) throws Exception {
        validateAllTraces = Class.forName(VERIFIER).getDeclaredMethod("validateAllTraces",
            Class.forName(VERIFIER + "$LogicalBoardSnapshot"),
            Class.forName(VERIFIER + "$LayoutSnapshot"));
        validateAllTraces.setAccessible(true);

        CirSim previousSim = CircuitElm.sim;
        CirSim previousSingleton = CirSim.theSim;
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        Vector<GeneratedBoardInstance> generated = new Vector<GeneratedBoardInstance>();
        int assertions = 0;
        try {
            assertions += runLiteralContracts();

            String[] legacyFamilies = new String[] { "LED_INDICATOR",
                "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR", "RC_DELAY",
                "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH" };
            for (String family : legacyFamilies) {
                GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(family, 0);
                generated.add(instance);
                assertions += requireActualAccepted(family + "/seed-0", instance);
            }

            GeneratedBoardInstance resistive = BoundedGeneratedBoardAssembler.assemble(
                BoundedAssemblyRequest.forCanary(0L)).getInstance();
            generated.add(resistive);
            assertions += requireActualAccepted("COMPOSED_RESISTIVE/seed-0", resistive);

            for (long seed : new long[] { 0, -1 }) {
                String family = "COMPOSED_CONTROLLED_INDICATOR";
                GeneratedBoardInstance instance = new PlayerLaunchRequest(family,
                    Long.toString(seed), PlayerFamilyCatalog.candidateProfile(family).name())
                    .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
                generated.add(instance);
                assertions += requireActualAccepted(family + "/seed-" + seed, instance);
            }
        } finally {
            for (GeneratedBoardInstance instance : generated)
                if (instance != null)
                    for (CircuitElm element : instance.getSimulationElements())
                        if (element != null) element.delete();
            CircuitElm.sim = previousSim;
            CirSim.theSim = previousSingleton;
        }

        System.out.println("PASS: physical copper observation contracts assertions=" + assertions +
            " literalNegatives=16 legacyActual=6 resistiveSeeds=1 controlledSeeds=2");
    }

    private static int runLiteralContracts() throws Exception {
        int checks = 0;
        Fixture tree = treeFixture();
        requireAccepted("literal-single-face-tree", tree, 2);
        checks++;

        Fixture missingSource = treeFixture();
        set(trace(missingSource, 0), "sourceId", null);
        requireRejected("missing-source", missingSource, "task43p-invalid-id:raw copper source");
        checks++;

        Fixture controlSource = treeFixture();
        set(trace(controlSource, 0), "sourceId", new StringBuilder().append((char) 1)
            .append("route").toString());
        requireRejected("control-character-source", controlSource,
            "task43p-invalid-copper-source-character");
        checks++;

        Fixture oversizedSource = treeFixture();
        StringBuilder longSource = new StringBuilder();
        for (int index = 0; index < 4097; index++) longSource.append('x');
        set(trace(oversizedSource, 0), "sourceId", longSource.toString());
        requireRejected("oversized-source", oversizedSource,
            "task43p-invalid-copper-source-length");
        checks++;

        Fixture duplicateSource = treeFixture();
        set(trace(duplicateSource, 1), "sourceId", "tree/main");
        requireRejected("duplicate-source", duplicateSource, "task43p-duplicate-copper-source:");
        checks++;

        Fixture badPadId = treeFixture();
        set(trace(badPadId, 0), "startPadId", "missing.1");
        requireRejected("bad-pad-id", badPadId, "task43p-trace-start-pad-invalid:missing.1");
        checks++;

        Fixture padGap = treeFixture();
        set(trace(padGap, 0), "xPoints", new int[] { 1, 10, 20 });
        requireRejected("pad-gap", padGap, "task43p-raw-copper-start-gap:A.1");
        checks++;

        Fixture wrongNet = twoNetFixture(false);
        set(trace(wrongNet, 0), "netId", "M");
        requireRejected("wrong-net", wrongNet, "task43p-trace-net-board-mismatch:A.1");
        checks++;

        Fixture nullOrphan = treeFixture();
        set(trace(nullOrphan, 1), "yPoints", new int[] { 10, 12 });
        requireRejected("null-orphan", nullOrphan, "task43p-copper-junction-unwitnessed:");
        checks++;

        Fixture removedBranch = treeFixture();
        traces(removedBranch).remove(1);
        requireRejected("removed-branch", removedBranch,
            "task43p-layout-pad-has-no-trace-endpoint:C.1");
        checks++;

        Fixture rawShort = twoNetFixture(true);
        requireRejected("raw-cross-net-short", rawShort, "task43p-copper-cross-net-short:");
        checks++;

        Fixture unsupportedLayer = treeFixture();
        set(trace(unsupportedLayer, 1), "layer", PcbCopperLayer.BOTTOM);
        requireRejected("unsupported-layer", unsupportedLayer,
            "task43p-copper-single-face-layer");
        checks++;

        Fixture platedHole = treeFixture();
        set(layout(platedHole), "platedHoleCount", Integer.valueOf(1));
        requireRejected("plated-hole-scope", platedHole, "task43p-copper-single-face-scope");
        checks++;

        Fixture emptySegment = treeFixture();
        set(trace(emptySegment, 0), "xPoints", new int[] { 0, 0, 20 });
        requireRejected("empty-segment", emptySegment,
            "task43p-raw-copper-non-manhattan-or-empty:");
        checks++;

        Fixture diagonalSegment = treeFixture();
        set(trace(diagonalSegment, 0), "yPoints", new int[] { 0, 5, 0 });
        requireRejected("diagonal-segment", diagonalSegment,
            "task43p-raw-copper-non-manhattan-or-empty:");
        checks++;

        Fixture disconnectedNet = disconnectedSameNetFixture();
        requireRejected("disconnected-logical-net", disconnectedNet,
            "task43p-copper-disconnected-net:N");
        checks++;

        Fixture unanchored = treeFixture();
        addTrace(layout(unanchored), "tree/orphan-a", PcbCopperLayer.TOP, "N", null, null,
            new int[] { 30, 40 }, new int[] { 30, 30 });
        addTrace(layout(unanchored), "tree/orphan-b", PcbCopperLayer.TOP, "N", null, null,
            new int[] { 40, 30 }, new int[] { 30, 30 });
        requireRejected("unanchored-trace-component", unanchored,
            "task43p-copper-unanchored-or-wrong-net:");
        checks++;
        return checks;
    }

    private static int requireActualAccepted(String label, GeneratedBoardInstance instance)
            throws Exception {
        if (instance == null || instance.getBoard() == null || instance.getPcbLayout() == null)
            throw new AssertionError(label + " did not produce a real board and layout");
        Fixture raw = snapshot(instance);
        int traceCount = validate(raw);
        if (traceCount <= 0)
            throw new AssertionError(label + " had no actual copper traces");
        return 1;
    }

    private static Fixture snapshot(GeneratedBoardInstance instance) throws Exception {
        Fixture result = new Fixture(newDto("LogicalBoardSnapshot"), newDto("LayoutSnapshot"));
        TroubleshootBoard board = instance.getBoard();
        PcbBoardLayout pcb = instance.getPcbLayout();
        for (String netId : board.getNetIds())
            addBoardNet(result.logical, netId, board.getNet(netId).getPadIds());
        for (String padId : board.getPadIds()) {
            BoardPad pad = board.getPad(padId);
            addBoardPad(result.logical, pad.getId(), pad.getComponentId(), pad.getTerminalId(),
                pad.getNetId());
        }
        set(result.layout, "routingLayer", board.getPlacementConstraints().routingLayer);
        int platedHoles = 0;
        for (PcbBoardHole hole : pcb.getHoles())
            if (hole.kind != PcbBoardHole.Kind.NON_PLATED) platedHoles++;
        set(result.layout, "platedHoleCount", Integer.valueOf(platedHoles));
        for (PcbPadPlacement pad : pcb.getPads())
            addLayoutPad(result.layout, pad.getPadId(), pad.getX(), pad.getY());
        for (PcbTraceGeometry trace : pcb.getTraces())
            addTrace(result.layout, trace.getSourceId(), trace.getLayer(), trace.getNetId(),
                trace.getStartPadId(), trace.getEndPadId(), trace.getXPoints(),
                trace.getYPoints());
        return result;
    }

    private static Fixture treeFixture() throws Exception {
        Fixture fixture = emptyFixture(PcbCopperLayer.TOP);
        addBoardNet(fixture.logical, "N", new String[] { "A.1", "B.1", "C.1" });
        addBoardPad(fixture.logical, "A.1", "N");
        addBoardPad(fixture.logical, "B.1", "N");
        addBoardPad(fixture.logical, "C.1", "N");
        addLayoutPad(fixture.layout, "A.1", 0, 0);
        addLayoutPad(fixture.layout, "B.1", 20, 0);
        addLayoutPad(fixture.layout, "C.1", 10, 10);
        addTrace(fixture.layout, "tree/main", PcbCopperLayer.TOP, "N", "A.1", "B.1",
            new int[] { 0, 10, 20 }, new int[] { 0, 0, 0 });
        addTrace(fixture.layout, "tree/branch", PcbCopperLayer.TOP, "N", "C.1", null,
            new int[] { 10, 10 }, new int[] { 10, 0 });
        return fixture;
    }

    private static Fixture twoNetFixture(boolean crossing) throws Exception {
        Fixture fixture = emptyFixture(PcbCopperLayer.TOP);
        addBoardNet(fixture.logical, "N", new String[] { "A.1", "B.1" });
        addBoardNet(fixture.logical, "M", new String[] { "C.1", "D.1" });
        addBoardPad(fixture.logical, "A.1", "N");
        addBoardPad(fixture.logical, "B.1", "N");
        addBoardPad(fixture.logical, "C.1", "M");
        addBoardPad(fixture.logical, "D.1", "M");
        addLayoutPad(fixture.layout, "A.1", 0, 10);
        addLayoutPad(fixture.layout, "B.1", 20, 10);
        if (crossing) {
            addLayoutPad(fixture.layout, "C.1", 10, 0);
            addLayoutPad(fixture.layout, "D.1", 10, 20);
            addTrace(fixture.layout, "two/net-n", PcbCopperLayer.TOP, "N", "A.1", "B.1",
                new int[] { 0, 20 }, new int[] { 10, 10 });
            addTrace(fixture.layout, "two/net-m", PcbCopperLayer.TOP, "M", "C.1", "D.1",
                new int[] { 10, 10 }, new int[] { 0, 20 });
        } else {
            addLayoutPad(fixture.layout, "C.1", 0, 30);
            addLayoutPad(fixture.layout, "D.1", 20, 30);
            addTrace(fixture.layout, "two/net-n", PcbCopperLayer.TOP, "N", "A.1", "B.1",
                new int[] { 0, 20 }, new int[] { 10, 10 });
            addTrace(fixture.layout, "two/net-m", PcbCopperLayer.TOP, "M", "C.1", "D.1",
                new int[] { 0, 20 }, new int[] { 30, 30 });
        }
        return fixture;
    }

    private static Fixture disconnectedSameNetFixture() throws Exception {
        Fixture fixture = emptyFixture(PcbCopperLayer.TOP);
        addBoardNet(fixture.logical, "N", new String[] { "A.1", "B.1", "C.1", "D.1" });
        addBoardPad(fixture.logical, "A.1", "N");
        addBoardPad(fixture.logical, "B.1", "N");
        addBoardPad(fixture.logical, "C.1", "N");
        addBoardPad(fixture.logical, "D.1", "N");
        addLayoutPad(fixture.layout, "A.1", 0, 10);
        addLayoutPad(fixture.layout, "B.1", 20, 10);
        addLayoutPad(fixture.layout, "C.1", 0, 30);
        addLayoutPad(fixture.layout, "D.1", 20, 30);
        addTrace(fixture.layout, "disconnected/first", PcbCopperLayer.TOP, "N", "A.1", "B.1",
            new int[] { 0, 20 }, new int[] { 10, 10 });
        addTrace(fixture.layout, "disconnected/second", PcbCopperLayer.TOP, "N", "C.1", "D.1",
            new int[] { 0, 20 }, new int[] { 30, 30 });
        return fixture;
    }

    private static Fixture emptyFixture(PcbCopperLayer routingLayer) throws Exception {
        Fixture fixture = new Fixture(newDto("LogicalBoardSnapshot"), newDto("LayoutSnapshot"));
        set(fixture.layout, "routingLayer", routingLayer);
        set(fixture.layout, "platedHoleCount", Integer.valueOf(0));
        return fixture;
    }

    private static void addBoardPad(Object logical, String padId, String netId)
            throws Exception {
        addBoardPad(logical, padId, "literal-component", padId, netId);
    }

    private static void addBoardPad(Object logical, String padId, String componentId,
            String terminalId, String netId) throws Exception {
        Object observation = newDto("BoardPadObservation");
        set(observation, "padId", padId);
        set(observation, "componentId", componentId);
        set(observation, "terminalId", terminalId);
        set(observation, "netId", netId);
        vector(logical, "pads").add(observation);
    }

    private static void addBoardNet(Object logical, String netId, String[] padIds)
            throws Exception {
        Vector<String> pads = new Vector<String>();
        for (String padId : padIds) pads.add(padId);
        addBoardNet(logical, netId, pads);
    }

    private static void addBoardNet(Object logical, String netId, Vector<String> padIds)
            throws Exception {
        Object observation = newDto("BoardNetObservation");
        set(observation, "netId", netId);
        @SuppressWarnings("unchecked")
        Vector<String> targetPads = (Vector<String>) field(observation, "padIds").get(observation);
        targetPads.addAll(padIds);
        vector(logical, "nets").add(observation);
    }

    private static void addLayoutPad(Object layout, String padId, int x, int y)
            throws Exception {
        Object observation = newDto("LayoutPadObservation");
        set(observation, "padId", padId);
        set(observation, "x", Integer.valueOf(x));
        set(observation, "y", Integer.valueOf(y));
        vector(layout, "pads").add(observation);
    }

    private static void addTrace(Object layout, String sourceId, PcbCopperLayer layer,
            String netId, String startPadId, String endPadId, int[] xPoints, int[] yPoints)
            throws Exception {
        Object observation = newDto("TraceObservation");
        set(observation, "sourceId", sourceId);
        set(observation, "layer", layer);
        set(observation, "netId", netId);
        set(observation, "startPadId", startPadId);
        set(observation, "endPadId", endPadId);
        set(observation, "xPoints", xPoints.clone());
        set(observation, "yPoints", yPoints.clone());
        vector(layout, "traces").add(observation);
    }

    private static void requireAccepted(String label, Fixture fixture, int expectedTraces)
            throws Exception {
        int actual = validate(fixture);
        if (actual != expectedTraces)
            throw new AssertionError(label + " expected trace count=" + expectedTraces +
                " actual=" + actual);
    }

    private static void requireRejected(String label, Fixture fixture, String message)
            throws Exception {
        try {
            validate(fixture);
        } catch (IllegalStateException rejected) {
            if (rejected.getMessage() == null || !rejected.getMessage().contains(message))
                throw new AssertionError(label + " rejected for the wrong reason: " +
                    rejected.getMessage());
            return;
        }
        throw new AssertionError(label + " unexpectedly passed physical copper validation");
    }

    private static int validate(Fixture fixture) throws Exception {
        try {
            return ((Integer) validateAllTraces.invoke(null, fixture.logical, fixture.layout))
                .intValue();
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Physical copper validator invocation failed", cause);
        }
    }

    private static Object trace(Fixture fixture, int index) throws Exception {
        return traces(fixture).get(index);
    }

    @SuppressWarnings("unchecked")
    private static Vector<Object> traces(Fixture fixture) throws Exception {
        return (Vector<Object>) field(fixture.layout, "traces").get(fixture.layout);
    }

    @SuppressWarnings("unchecked")
    private static Vector<Object> vector(Object target, String name) throws Exception {
        return (Vector<Object>) field(target, name).get(target);
    }

    private static Object newDto(String simpleName) throws Exception {
        Class<?> type = Class.forName(VERIFIER + "$" + simpleName);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    private static Object layout(Fixture fixture) {
        return fixture.layout;
    }

    private static final class Fixture {
        final Object logical;
        final Object layout;

        Fixture(Object logical, Object layout) {
            this.logical = logical;
            this.layout = layout;
        }
    }
}
