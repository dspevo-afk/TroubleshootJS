package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Locks seed 4's canvas rejection to the final routed-layout boundary. */
public final class PcbCanvasBoundaryContractTest {
    private static final long SEED = 4L;
    private static final int VERSION = 13;
    private static final String MESSAGE = "PCB board outline leaves workbench canvas";
    private static final String PHASE = "finishRoutedPlan";
    private static int assertions;

    private PcbCanvasBoundaryContractTest() { }

    public static void main(String[] args) {
        Rb56Plan plan = Rb56Plan.resolve(SEED, VERSION);
        check(plan.seed == SEED && plan.placementInterpretationVersion == VERSION,
            "explicit saved13 source identity");
        TroubleshootBoard board = plan.board();
        verifyBoardAgainstPlan(plan, board);
        String sourceBefore = sourcePlanSha256(plan);
        String graphBefore = liveGraphSha256(board);

        IllegalStateException failure = null;
        try {
            new SeededPcbLayoutGenerator().generateWithPolicyResult(
                board, plan.layoutSeed, plan.routingSeed);
        } catch (IllegalStateException expected) {
            failure = expected;
        }

        verifyBoardAgainstPlan(plan, board);
        String sourceAfter = sourcePlanSha256(plan);
        String graphAfter = liveGraphSha256(board);
        StackTraceElement phase = failure == null ? null : firstGeneratorFrame(failure);
        check(failure != null, "seed4 must retain its canvas-boundary rejection");
        check(MESSAGE.equals(failure.getMessage()), "exact canvas-boundary failure message");
        check(phase != null && PHASE.equals(phase.getMethodName()) && phase.getLineNumber() > 0,
            "identified generator frame with source line: " + phase);
        check(sourceBefore.equals(sourceAfter), "RB56 source plan remains unchanged");
        check(graphBefore.equals(graphAfter), "board graph remains unchanged after rejection");
        System.out.println("PASS: PCB canvas boundary contracts version=13 seed=4 identifiedPhase=" +
            phase.toString() + " assertions=" + assertions);
    }

    static void verifyBoardAgainstPlan(Rb56Plan plan, TroubleshootBoard board) {
        check(plan != null && board != null, "source plan and board exist");
        check((Rb56Plan.FAMILY_ID + "_BOARD").equals(board.getId()), "exact RB56 board identity");
        TreeSet<String> componentIds = new TreeSet<String>();
        TreeSet<String> padIds = new TreeSet<String>();
        TreeSet<String> inputIds = new TreeSet<String>();
        TreeMap<String, TreeSet<String>> netPads = new TreeMap<String, TreeSet<String>>();
        for (Rb56Plan.Part part : plan.parts()) {
            check(componentIds.add(part.id), "unique plan component " + part.id);
            BoardComponent component = board.getComponent(part.id);
            check(component != null && part.type.equals(component.getType()) &&
                component.getPhysicalPackage() == part.physicalPackage,
                "source type and exact package retained: " + part.id);
            String[] terminals = part.terminalIds(), nets = part.netIds();
            check(terminals.length == nets.length, "source terminal/net arity: " + part.id);
            TreeSet<String> componentPads = new TreeSet<String>();
            for (int index = 0; index < terminals.length; index++) {
                String padId = part.id + "." + terminals[index];
                check(padIds.add(padId) && componentPads.add(padId), "unique source pad " + padId);
                BoardPad pad = board.getPad(padId);
                check(pad != null && part.id.equals(pad.getComponentId()) &&
                    terminals[index].equals(pad.getTerminalId()) && nets[index].equals(pad.getNetId()),
                    "full source-to-pad mapping: " + padId);
                TreeSet<String> members = netPads.get(nets[index]);
                if (members == null) {
                    members = new TreeSet<String>();
                    netPads.put(nets[index], members);
                }
                members.add(padId);
            }
            check(componentPads.equals(new TreeSet<String>(component.getPadIds())),
                "exact component pad membership: " + part.id);
            if (part.inputId != null) {
                check(inputIds.add(part.inputId) && terminals.length >= 2,
                    "unique source power input: " + part.inputId);
                ExternalBoardPowerInput input = board.getPowerInput(part.inputId);
                check(input != null && (part.id + "." + terminals[0]).equals(input.getPositivePadId()) &&
                    (part.id + "." + terminals[1]).equals(input.getReturnPadId()) &&
                    nets[0].equals(input.getPositiveNetId()) && nets[1].equals(input.getReturnNetId()),
                    "source power binding: " + part.inputId);
            }
        }
        check(componentIds.equals(new TreeSet<String>(board.getComponentIds())),
            "exact source component inventory");
        check(padIds.equals(new TreeSet<String>(board.getPadIds())), "exact source pad inventory");
        check(inputIds.equals(new TreeSet<String>(board.getPowerInputIds())),
            "exact source power-input inventory");
        check(netPads.keySet().equals(new TreeSet<String>(board.getNetIds())), "exact source net inventory");
        for (String id : netPads.keySet()) {
            BoardNet net = board.getNet(id);
            check(net != null && net.getRoutingRole() != null, "typed source net: " + id);
            check(netPads.get(id).equals(new TreeSet<String>(net.getPadIds())),
                "exact source net-pad membership: " + id);
        }
        board.validate();
    }

    static String sourcePlanSha256(Rb56Plan plan) {
        return PlayerSessionFingerprint.of(plan.canonical());
    }

    static String liveGraphSha256(TroubleshootBoard board) {
        return PlayerSessionFingerprint.of(boardGraph(board));
    }

    private static StackTraceElement firstGeneratorFrame(IllegalStateException failure) {
        for (StackTraceElement frame : failure.getStackTrace())
            if (SeededPcbLayoutGenerator.class.getName().equals(frame.getClassName())) return frame;
        return null;
    }

    private static String boardGraph(TroubleshootBoard board) {
        StringBuilder out = new StringBuilder();
        append(out, board.getId());
        TreeSet<String> componentIds = new TreeSet<String>(board.getComponentIds());
        out.append("components:").append(componentIds.size()).append(';');
        for (String id : componentIds) {
            BoardComponent component = board.getComponent(id);
            check(component != null, "component exists while fingerprinting: " + id);
            append(out, component.getId()); append(out, component.getType());
            PhysicalPackage physicalPackage = component.getPhysicalPackage();
            append(out, physicalPackage.getId());
            appendSequence(out, physicalPackage.getTerminalIds());
            appendSet(out, component.getPadIds());
        }

        TreeSet<String> padIds = new TreeSet<String>(board.getPadIds());
        out.append("pads:").append(padIds.size()).append(';');
        for (String id : padIds) {
            BoardPad pad = board.getPad(id);
            check(pad != null, "pad exists while fingerprinting: " + id);
            append(out, pad.getId()); append(out, pad.getComponentId());
            append(out, pad.getTerminalId()); append(out, pad.getNetId());
        }

        TreeSet<String> netIds = new TreeSet<String>(board.getNetIds());
        out.append("nets:").append(netIds.size()).append(';');
        for (String id : netIds) {
            BoardNet net = board.getNet(id);
            check(net != null, "net exists while fingerprinting: " + id);
            append(out, net.getId()); append(out, net.getRoutingRole().name());
            appendSet(out, net.getPadIds());
        }

        TreeSet<String> inputIds = new TreeSet<String>(board.getPowerInputIds());
        out.append("powerInputs:").append(inputIds.size()).append(';');
        for (String id : inputIds) {
            ExternalBoardPowerInput input = board.getPowerInput(id);
            check(input != null, "power input exists while fingerprinting: " + id);
            append(out, input.getId()); append(out, input.getPositivePadId());
            append(out, input.getReturnPadId()); append(out, input.getPositiveNetId());
            append(out, input.getReturnNetId());
        }
        return out.toString();
    }

    private static void appendSet(StringBuilder out, Vector<String> values) {
        TreeSet<String> sorted = new TreeSet<String>(values);
        out.append(sorted.size()).append('[');
        for (String value : sorted) append(out, value);
        out.append(']');
    }

    private static void appendSequence(StringBuilder out, Vector<String> values) {
        out.append(values.size()).append('[');
        for (String value : values) append(out, value);
        out.append(']');
    }

    private static void append(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value).append(';');
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
