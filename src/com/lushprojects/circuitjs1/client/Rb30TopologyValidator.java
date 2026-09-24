package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Structural guard for the one-graph Q30 construction pilot. */
final class Rb30TopologyValidator {
    private Rb30TopologyValidator() { }

    static void require(Rb30Generator.Candidate candidate) {
        if (candidate == null || candidate.plan == null)
            throw invalid("missing candidate");
        TroubleshootBoard board = candidate.board();
        board.validate();
        int count = board.getComponentIds().size();
        if (count < 20 || count > 40 ||
                candidate.backing.size() != count)
            throw invalid("20-40 package construction/ownership census");
        for (String id : board.getComponentIds()) {
            CircuitElm backing = candidate.backing.get(id);
            if (backing == null || !candidate.elements().contains(backing))
                throw invalid("missing solver backing: " + id);
            BoardComponent component = board.getComponent(id);
            for (String pad : component.getPadIds()) {
                if (board.getSimulationBindings().getEndpoint(pad) == null)
                    throw invalid("unmapped terminal: " + pad);
                if (!component.getPhysicalPackage().isConnector() &&
                        candidate.assembly.connections.get(id, pad) == null)
                    throw invalid("missing detachable lead: " + pad);
            }
        }
        requireNet(board, "J1.1", "RAW12");
        requireNet(board, "F1.1", "RAW12");
        requireNet(board, "F1.2", "FUSED12");
        requireNet(board, "DREV.A", "FUSED12");
        requireNet(board, "DREV.K", "RAIL12");
        requireNet(board, "U1.INPUT", "RAIL12");
        requireNet(board, "U1.OUTPUT", "RAIL5");
        requireNet(board, "U1.RETURN", "CTRL_RETURN");
        requireNet(board, "U1.ENABLE", "EN5");
        requireNet(board, "REN.1", "RAIL12");
        requireNet(board, "REN.2", "EN5");
        if (candidate.backing.get("U1") != candidate.regulator ||
                candidate.regulator.getContract().getNominalOutputVolts() != 5 ||
                !(candidate.backing.get("F1") instanceof ProtectionFuseElm) ||
                !(candidate.backing.get("DREV") instanceof DiodeElm))
            throw invalid("noncausal 12 V to E02 5 V rail");
        if (candidate.backing.get("U2A") != candidate.decisionA ||
                candidate.backing.get("U2B") != candidate.decisionB ||
                candidate.decisionA == candidate.decisionB)
            throw invalid("missing two independent E04 decisions");
        for (String channel : new String[] { "A", "B" }) {
            requireNet(board, "U2" + channel + ".SENSOR", channel + "_SENSE");
            requireNet(board, "U2" + channel + ".RAIL", "RAIL5");
            requireNet(board, "U2" + channel + ".OUTPUT", channel + "_CMD");
            requireNet(board, "RD" + channel + ".1", channel + "_CMD");
            requireNet(board, "Q" + channel + "." +
                board.getComponent("Q" + channel).getPhysicalPackage()
                    .getTerminalIds().get(1), channel + "_COIL_LOW");
            requireNet(board, "K" + channel + ".A1", "RAIL5");
            requireNet(board, "K" + channel + ".A2", channel + "_COIL_LOW");
            requireNet(board, "K" + channel + ".COM", "LOAD12");
            requireNet(board, "K" + channel + ".NO", "OUT_" + channel);
            requireNet(board, "JO" + channel + ".1", "OUT_" + channel);
            requireNet(board, "JO" + channel + ".2", "LOAD_RETURN");
            if (!(candidate.backing.get("K" + channel) instanceof ServiceRelayElm) ||
                    !(candidate.backing.get("JO" + channel) instanceof
                        BoundedExternalLoadElm))
                throw invalid("missing loaded E03 output " + channel);
        }
        String reference = candidate.plan.sharedHystereticReference ?
            "REF_SHARED" : null;
        for (String channel : new String[] { "A", "B" })
            requireNet(board, "U2" + channel + ".REFERENCE",
                reference == null ? channel + "_REF" : reference);
        if (reference != null) {
            if (board.getComponent("RFB_A") == null ||
                    board.getComponent("RFB_B") == null ||
                    board.getComponent("RREF_H") == null ||
                    board.getComponent("RREF_L") == null)
                throw invalid("incomplete shared hysteretic arrangement");
        } else for (String channel : new String[] { "A", "B" })
            if (board.getComponent("RREF_H" + channel) == null ||
                    board.getComponent("RREF_L" + channel) == null)
                throw invalid("incomplete separate direct arrangement");
        int decisionCount = 0, regulatorCount = 0, relayCount = 0;
        for (CircuitElm element : candidate.elements()) {
            if (element instanceof E04SensorControlModel.DecisionElement) decisionCount++;
            if (element instanceof AbstractRailRegulatorElm) regulatorCount++;
            if (element instanceof ServiceRelayElm) relayCount++;
        }
        if (decisionCount != 2 || regulatorCount != 1 || relayCount != 2)
            throw invalid("duplicate or missing live functional role");
        requireElementCensus(candidate);
    }

    /** Every published solver element has a declared physical or external owner. */
    private static void requireElementCensus(Rb30Generator.Candidate candidate) {
        Vector<CircuitElm> accounted = new Vector<CircuitElm>();
        for (CircuitElm element : candidate.backing.values())
            addUnique(accounted, element, "duplicate component backing");
        for (String input : candidate.board().getPowerInputIds()) {
            ExternalPowerSimulationBinding binding =
                candidate.assembly.power.getBinding(input);
            if (!binding.hasControl() || binding.getBackingElements().size() != 2 ||
                    !(binding.getLimitedSupply() instanceof LimitedDcSupplyElm))
                throw invalid("source/control ownership: " + input);
            for (CircuitElm element : binding.getBackingElements())
                if (!accounted.contains(element))
                    addUnique(accounted, element, "duplicate external owner");
        }
        for (GeneratedComponentConnectionBinding connection :
                candidate.assembly.connections.getAll()) {
            CircuitElm element = connection.getConnectionElement();
            if (!(element instanceof WireElm) ||
                    !candidate.elements().contains(element))
                throw invalid("missing pad attachment");
            addUnique(accounted, element, "duplicate pad attachment");
        }
        int faultHelpers = 0;
        for (GeneratedFaultCandidate fault : candidate.faultCandidates)
            for (CircuitElm element : fault.getPrivateSimulationElements()) {
                if (!candidate.elements().contains(element) ||
                        !(element instanceof SwitchElm))
                    throw invalid("missing declared fault helper");
                addUnique(accounted, element, "duplicate fault helper");
                faultHelpers++;
            }
        if (faultHelpers != 4 || candidate.selectedFault == null)
            throw invalid("wrong Q30 fault population");
        for (CircuitElm support : candidate.internalSupport)
            if (!accounted.contains(support))
                addUnique(accounted, support, "duplicate component support");
        for (WireElm wire : candidate.netInterconnect) {
            if (!(wire instanceof WireElm) ||
                    !candidate.elements().contains(wire))
                throw invalid("missing declared net interconnect");
            addUnique(accounted, wire, "duplicate net interconnect");
        }
        int groundCount = 0;
        for (CircuitElm element : candidate.elements()) {
            if (accounted.contains(element)) continue;
            if (element instanceof GroundElm) {
                if (++groundCount != 1 ||
                        !element.getPost(0).equals(
                            candidate.assembly.nets.get("CTRL_RETURN")))
                    throw invalid("foreign or duplicate reference ground");
                accounted.add(element);
            } else throw invalid("unexplained CircuitJS element " +
                element.getClass().getName());
        }
        if (groundCount != 1 || accounted.size() != candidate.elements().size())
            throw invalid("incomplete final solver element census");
    }

    private static void addUnique(Vector<CircuitElm> accounted,
            CircuitElm element, String problem) {
        if (element == null || accounted.contains(element)) throw invalid(problem);
        accounted.add(element);
    }

    private static void requireNet(TroubleshootBoard board, String padId,
            String netId) {
        BoardPad pad = board.getPad(padId);
        if (pad == null || !netId.equals(pad.getNetId()))
            throw invalid("wrong Q30 conductor: " + padId);
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException("Invalid RB30 construction: " + detail);
    }
}
