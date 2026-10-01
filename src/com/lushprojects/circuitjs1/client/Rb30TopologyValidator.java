package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Structural guard for the one-graph procedural Q30 family. */
final class Rb30TopologyValidator {
    private Rb30TopologyValidator() { }

    static void require(Rb30Generator.Candidate candidate) {
        if (candidate == null || candidate.plan == null)
            throw invalid("missing candidate");
        TroubleshootBoard board = candidate.board();
        board.validate();
        int count = board.getComponentIds().size();
        if (count != candidate.plan.physicalPackageCount() || count < 20 || count > 40 ||
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
        if (candidate.plan.channelCount != candidate.decisions.size())
            throw invalid("decision population does not match active channels");
        if (candidate.plan.hasEntryCapacitor) {
            CircuitElm entryCap = candidate.backing.get("C12");
            if (!(entryCap instanceof CapacitorElm) ||
                    ((CapacitorElm) entryCap).capacitance != 1e-6)
                throw invalid("missing requested fused-entry capacitor");
            requireNet(board, "C12.1", "FUSED12");
            requireNet(board, "C12.2", "CTRL_RETURN");
        } else if (candidate.backing.get("C12") != null ||
                board.getComponent("C12") != null) {
            throw invalid("omitted entry capacitor remains in live graph");
        }
        if (candidate.plan.hasFiveVoltBleeder) {
            CircuitElm bleeder = candidate.backing.get("RBLEED5");
            if (!(bleeder instanceof ResistorElm) ||
                    ((ResistorElm) bleeder).getResistance() != 100000.0)
                throw invalid("missing requested 5 V stored-energy bleed path");
            requireNet(board, "RBLEED5.1", "RAIL5");
            requireNet(board, "RBLEED5.2", "CTRL_RETURN");
        }
        requireIndicator(candidate, "RLED", "LED1",
            candidate.plan.hasFiveVoltIndicator, "RAIL5", "CTRL_RETURN", 3300.0);
        requireIndicator(candidate, "RLED12", "LED12",
            candidate.plan.hasTwelveVoltIndicator, "RAIL12", "CTRL_RETURN", 10000.0);
        for (String channel : candidate.plan.channels()) {
            if (candidate.backing.get("U2" + channel) != candidate.decision(channel))
                throw invalid("missing independent E04 decision " + channel);
            CircuitElm rawPullDown = candidate.backing.get("RPIN_" + channel);
            if (!(rawPullDown instanceof ResistorElm) ||
                    ((ResistorElm) rawPullDown).getResistance() != 1000.0)
                throw invalid("missing 1 kOhm raw sensor pull-down " + channel);
            requireNet(board, "RPIN_" + channel + ".1", channel + "_RAW");
            requireNet(board, "RPIN_" + channel + ".2", "CTRL_RETURN");
            if (candidate.plan.hasSensorInputFilter(channel)) {
                CircuitElm filter = candidate.backing.get("CFLT_" + channel);
                if (!(filter instanceof CapacitorElm) ||
                        ((CapacitorElm) filter).capacitance != 100e-9)
                    throw invalid("missing physical sensor input filter " + channel);
                requireNet(board, "CFLT_" + channel + ".1", channel + "_SENSE");
                requireNet(board, "CFLT_" + channel + ".2", "CTRL_RETURN");
            }
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
            requireIndicator(candidate, "RLEDOUT_" + channel,
                "LEDOUT_" + channel, candidate.plan.hasOutputIndicator(channel),
                "OUT_" + channel, "LOAD_RETURN", 10000.0);
        }
        String reference = candidate.plan.sharedReference() ?
            "REF_SHARED" : null;
        for (String channel : candidate.plan.channels())
            requireNet(board, "U2" + channel + ".REFERENCE",
                reference == null ? channel + "_REF" : reference);
        if (candidate.plan.sharedHystereticReference()) {
            if (board.getComponent("RREF_H") == null ||
                    board.getComponent("RREF_L") == null)
                throw invalid("incomplete shared hysteretic arrangement");
            for (String channel : candidate.plan.channels())
                if (board.getComponent("RFB_" + channel) == null)
                    throw invalid("missing channel hysteresis feedback " + channel);
        } else if (candidate.plan.referenceArrangement ==
                Rb30Plan.ReferenceArrangement.SHARED_DIRECT) {
            if (board.getComponent("RREF_H") == null ||
                    board.getComponent("RREF_L") == null)
                throw invalid("incomplete shared direct arrangement");
            for (String channel : candidate.plan.channels())
                if (board.getComponent("RFB_" + channel) != null)
                    throw invalid("shared direct threshold gained feedback");
        } else for (String channel : candidate.plan.channels())
            if (board.getComponent("RREF_H" + channel) == null ||
                    board.getComponent("RREF_L" + channel) == null)
                throw invalid("incomplete separate direct arrangement");
        int decisionCount = 0, regulatorCount = 0, relayCount = 0;
        for (CircuitElm element : candidate.elements()) {
            if (element instanceof E04SensorControlModel.DecisionElement) decisionCount++;
            if (element instanceof AbstractRailRegulatorElm) regulatorCount++;
            if (element instanceof ServiceRelayElm) relayCount++;
        }
        if (decisionCount != candidate.plan.channelCount || regulatorCount != 1 ||
                relayCount != candidate.plan.channelCount)
            throw invalid("duplicate or missing live functional role");
        requireElementCensus(candidate);
        requireFaultOwners(candidate);
    }

    private static void requireIndicator(Rb30Generator.Candidate candidate,
            String resistorId, String ledId, boolean expected,
            String firstNet, String returnNet, double ohms) {
        TroubleshootBoard board = candidate.board();
        CircuitElm resistor = candidate.backing.get(resistorId);
        CircuitElm led = candidate.backing.get(ledId);
        if (!expected) {
            if (resistor != null || led != null ||
                    board.getComponent(resistorId) != null ||
                    board.getComponent(ledId) != null)
                throw invalid("unrequested indicator remains in topology " + resistorId);
            return;
        }
        if (!(resistor instanceof ResistorElm) ||
                ((ResistorElm) resistor).getResistance() != ohms ||
                !(led instanceof LEDElm))
            throw invalid("missing causal indicator pair " + resistorId);
        String feed = "LED_FEED";
        if ("RLED12".equals(resistorId)) feed = "LED12_FEED";
        else if (resistorId.startsWith("RLEDOUT_"))
            feed = "LED_OUT_" + resistorId.substring("RLEDOUT_".length()) + "_FEED";
        requireNet(board, resistorId + ".1", firstNet);
        requireNet(board, resistorId + ".2", feed);
        requireNet(board, ledId + ".A", feed);
        requireNet(board, ledId + ".K", returnNet);
    }

    /** Verify all five answer-blind options have distinct executable physical owners. */
    private static void requireFaultOwners(Rb30Generator.Candidate candidate) {
        String relayChannel = candidate.plan.channelCount == 1 ? "A" : "B";
        String[] ids = { "DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN",
            "DRIVE_A_OPEN", "RELAY_" + relayChannel + "_COIL_OPEN" };
        String[] owners = { "DREV", "REN", "RSA", "RDA", "K" + relayChannel };
        if (candidate.faultCandidates == null || candidate.faultCandidates.size() != ids.length)
            throw invalid("Q30 must retain exactly five fault candidates");
        boolean selectedFound = false;
        for (int index = 0; index < ids.length; index++) {
            GeneratedFaultCandidate fault = candidate.faultCandidates.get(index);
            if (fault == null || !ids[index].equals(fault.getFault().getId()) ||
                    !owners[index].equals(fault.getFault().getTargetComponentId()) ||
                    !fault.isAdmitted())
                throw invalid("noncanonical or unserviceable fault candidate: " + ids[index]);
            PhysicalBoardSlot slot = candidate.runtime.getSlot(owners[index]);
            PhysicalBoardInstallationProvider.Scoped declaration =
                candidate.runtime.getScopedMutationCapability(owners[index]);
            WorkbenchPartsProvider parts =
                candidate.runtime.getWorkbenchPartsProvider(owners[index]);
            if (slot == null || slot.getInstalledPart() == null ||
                    declaration == null || declaration.getMutationSlot() == null ||
                    declaration.getMutationSlot().getPhysicalSlot() != slot ||
                    parts == null || parts.getCatalogEntries().isEmpty())
                throw invalid("fault owner has no installed scoped catalog service: " +
                    owners[index]);
            PhysicalMutationSlot scope = declaration.getMutationSlot();
            if (scope == null || scope.getPhysicalSlot() != slot ||
                    !owners[index].equals(scope.getComponentId()))
                throw invalid("fault owner has stale mutation slot: " + owners[index]);
            for (CircuitElm helper : fault.getPrivateSimulationElements()) {
                for (String componentId : candidate.board().getComponentIds())
                    if (candidate.assembly.components.isElementBoundToComponent(
                            componentId, helper))
                        throw invalid("private fault helper is also component-bound: " +
                            componentId);
            }
            if (fault.getBinding() == candidate.selectedFault) {
                selectedFound = true;
                PhysicalPart<?> installed = slot.getInstalledPart();
                if (!(installed instanceof GeneratedFaultOwningPart) ||
                        !((GeneratedFaultOwningPart) installed).ownsGeneratedFault(
                            candidate.selectedFault))
                    throw invalid("selected fault has a stale physical owner: " + owners[index]);
            } else if (slot.getInstalledPart() instanceof GeneratedFaultOwningPart &&
                    ((GeneratedFaultOwningPart) slot.getInstalledPart()).ownsGeneratedFault(
                        candidate.selectedFault)) {
                throw invalid("selected fault has multiple physical owners");
            }
            GeneratedFaultServiceabilityAdmission.validateCandidate(fault);
        }
        if (!selectedFound)
            throw invalid("selected fault is not an owned member of the population");
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
