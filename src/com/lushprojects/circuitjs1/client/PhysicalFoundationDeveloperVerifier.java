package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Developer-only canary for the generic physical slot/runtime foundation. */
final class PhysicalFoundationDeveloperVerifier {
    private PhysicalFoundationDeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null)
            throw new IllegalArgumentException("Missing simulator for physical foundation canary");
        Vector<CircuitElm> canaryElements = new Vector<CircuitElm>();
        try {
            for (int terminalCount = 3; terminalCount <= 6; terminalCount++)
                verifyPart(sim, terminalCount, canaryElements);
        } finally {
            for (CircuitElm element : canaryElements)
                sim.elmList.remove(element);
        }
    }

    private static void verifyPart(CirSim sim, int terminalCount,
            Vector<CircuitElm> canaryElements) {
        String componentId = "U_CANARY_" + terminalCount;
        PhysicalPackage physicalPackage = packageFor(terminalCount);
        TroubleshootBoard board = createBoard(componentId, terminalCount, physicalPackage);
        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        runtime.createSlot("PWR_IN");
        PhysicalBoardSlot slot = runtime.createSlot(componentId);
        Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
        Vector<CircuitElm> backingElements = new Vector<CircuitElm>();
        for (int index = 0; index < terminalCount; index++) {
            WireElm wire = new WireElm(32 + index * 48, 32 + terminalCount * 8);
            wire.drag(48 + index * 48, 32 + terminalCount * 8);
            sim.elmList.add(wire);
            canaryElements.add(wire);
            backingElements.add(wire);
            terminals.add(new PhysicalPartTerminal(componentId,
                physicalPackage.getTerminalIds().get(index),
                new CircuitPostMeasurementEndpoint(wire, 0)));
        }
        FixedPhysicalPart<BasicPhysicalSpecification> part =
            new FixedPhysicalPart<BasicPhysicalSpecification>(componentId,
                new BasicPhysicalSpecification(componentId + "_SPEC"),
                new PhysicalNameplate(componentId, "Developer canary part"), physicalPackage,
                terminals, backingElements,
                new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, componentId));
        slot.install(part);
        runtime.validate();
        require(slot.isOccupied() && slot.getInstalledPart() == part &&
                part.getBoardSlot() == slot, "physical canary slot/part association failed");
        require(part.getTerminalCount() == terminalCount &&
                part.getTerminals().size() == terminalCount,
            "physical canary terminal count failed: " + terminalCount);
        require(part.getPackage() == physicalPackage && slot.getPhysicalPackage() == physicalPackage,
            "physical canary package identity failed: " + terminalCount);
        String firstTerminal = part.getTerminal(0).getTerminalName();
        String secondTerminal = part.getTerminal(1).getTerminalName();
        boolean expectedPositiveConnection = terminalCount == 3 || terminalCount == 4;
        require(part.getPackage().isInternallyConnected(firstTerminal, secondTerminal) ==
                expectedPositiveConnection,
            "physical canary declared internal connectivity failed: " + terminalCount);
        String negativeFirst = expectedPositiveConnection ?
            part.getTerminal(1).getTerminalName() : firstTerminal;
        String negativeSecond = expectedPositiveConnection ?
            part.getTerminal(2).getTerminalName() : secondTerminal;
        require(!part.getPackage().isInternallyConnected(negativeFirst, negativeSecond),
            "physical canary undeclared terminal pair became connected: " + terminalCount);
        Vector<String> padIds = slot.getPadIds();
        Vector<String> netIds = slot.getNetIds();
        for (int index = 0; index < terminalCount; index++) {
            require(part.getTerminal(index).getId().equals(componentId + "." +
                    physicalPackage.getTerminalIds().get(index)),
                "physical canary terminal identity/order failed: " + terminalCount);
            require(padIds.get(index).equals(componentId + "." +
                    physicalPackage.getTerminalIds().get(index)) &&
                    netIds.get(index).equals(expectedNetId(terminalCount, index + 1)),
                "physical canary pad/net binding failed: " + terminalCount);
        }
        verifyFootprintAndRouting(part, slot, board, terminalCount);
        new CirSimTroubleshootSimulationFacade(sim).validateBacking(part);
        PhysicalPart<?> detached = slot.remove();
        require(detached == part && !slot.isOccupied() && !part.isInstalled() &&
                part.getBoardSlot() == null, "physical canary detach state failed");
        slot.install(part);
        runtime.validate();
        require(part.isInstalled() && part.getBoardSlot() == slot &&
                slot.getPadIds().equals(padIds) && slot.getNetIds().equals(netIds),
            "physical canary reinstall identity failed: " + terminalCount);
    }

    private static TroubleshootBoard createBoard(String componentId, int terminalCount,
            PhysicalPackage physicalPackage) {
        TroubleshootBoard board = new TroubleshootBoard("PHYSICAL_FOUNDATION_" + terminalCount);
        if (terminalCount <= 4) {
            board.addNet(new BoardNet("CANARY_POSITIVE_" + terminalCount));
            board.addNet(new BoardNet("CANARY_NEGATIVE_" + terminalCount));
            board.addComponent(new BoardComponent("PWR_IN", "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
            board.addPad(new BoardPad("PWR_IN.1", "PWR_IN", "1",
                "CANARY_POSITIVE_" + terminalCount));
            board.addPad(new BoardPad("PWR_IN.2", "PWR_IN", "2",
                "CANARY_NEGATIVE_" + terminalCount));
        } else {
            for (int terminal = 1; terminal <= terminalCount; terminal++)
                board.addNet(new BoardNet(expectedNetId(terminalCount, terminal)));
            board.addComponent(new BoardComponent("PWR_IN", "CONNECTOR",
                PhysicalPackages.developerConnectorForCount(terminalCount)));
            for (int terminal = 1; terminal <= terminalCount; terminal++)
                board.addPad(new BoardPad("PWR_IN." + terminal, "PWR_IN",
                    String.valueOf(terminal), expectedNetId(terminalCount, terminal)));
        }
        board.addComponent(new BoardComponent(componentId, "DEV_CANARY_" + terminalCount,
            physicalPackage));
        for (int terminal = 1; terminal <= terminalCount; terminal++)
            board.addPad(new BoardPad(componentId + "." + terminal, componentId,
                String.valueOf(terminal), expectedNetId(terminalCount, terminal)));
        board.validate();
        return board;
    }

    private static String expectedNetId(int terminalCount, int terminal) {
        if (terminalCount <= 4)
            return terminal <= 2 ? "CANARY_POSITIVE_" + terminalCount :
                "CANARY_NEGATIVE_" + terminalCount;
        return "CANARY_NET_" + terminalCount + "_" + terminal;
    }

    private static void verifyFootprintAndRouting(PhysicalPart<?> part, PhysicalBoardSlot slot,
            TroubleshootBoard board, int terminalCount) {
        String componentId = slot.getComponentId();
        require(board.getComponent(componentId).getPhysicalPackage() == part.getPackage() &&
                slot.getPhysicalPackage() == part.getPackage(),
            "physical canary package identity did not reach the PCB graph: " + terminalCount);
        SeededPcbLayoutGenerator generator = new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry());
        PcbBoardLayout layout = generator.generate(board, 6100 + terminalCount * 13);
        layout.validateGeometry(board);
        HashMap<String, Integer> endpointCounts = new HashMap<String, Integer>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            BoardPad start = board.getPad(trace.getStartPadId());
            BoardPad end = board.getPad(trace.getEndPadId());
            require(start != null && end != null && trace.getNetId().equals(start.getNetId()) &&
                    trace.getNetId().equals(end.getNetId()),
                "physical canary route escaped its logical net: " + terminalCount);
            increment(endpointCounts, trace.getStartPadId());
            increment(endpointCounts, trace.getEndPadId());
        }
        for (int terminal = 0; terminal < part.getTerminalCount(); terminal++) {
            PhysicalPartTerminal physicalTerminal = part.getTerminal(terminal);
            String padId = componentId + "." + physicalTerminal.getTerminalName();
            BoardPad boardPad = board.getPad(padId);
            require(boardPad != null && physicalTerminal.getId().equals(padId) &&
                    physicalTerminal.getTerminalName().equals(boardPad.getTerminalId()) &&
                    layout.getPad(padId) != null,
                "physical terminal did not retain identity through footprint mapping: " + padId);
            if (endpointCounts.get(padId) == null)
                require(terminalCount <= 4 && terminal == 1 &&
                        part.getPackage().isInternallyConnected(
                        part.getTerminal(0).getTerminalName(),
                        physicalTerminal.getTerminalName()),
                    "PCB routing omitted a pad without declared package connectivity: " + padId);
        }
        if (terminalCount <= 4) {
            require(layout.getTraces().size() == terminalCount - 1 &&
                    Integer.valueOf(1).equals(endpointCounts.get(componentId + ".1")) &&
                    endpointCounts.get(componentId + ".2") == null,
                "physical canary declared internal pair was not routed through its package: " +
                    terminalCount);
            for (int terminal = 3; terminal <= terminalCount; terminal++)
                require(Integer.valueOf(1).equals(endpointCounts.get(componentId + "." + terminal)),
                    "physical canary undeclared pair lost copper: " + componentId + "." + terminal);
            TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
            require(!hasLink(topology, componentId, componentId + ".1", componentId + ".2",
                    "CANARY_POSITIVE_" + terminalCount),
                "physical canary placement retained a declared internal link: " + terminalCount);
            if (terminalCount == 4)
                require(hasLink(topology, componentId, componentId + ".3", componentId + ".4",
                        "CANARY_NEGATIVE_" + terminalCount),
                    "physical canary placement omitted an undeclared internal link");
        } else {
            require(layout.getTraces().size() == terminalCount,
                "unconnected physical canary omitted copper: " + terminalCount);
            for (int terminal = 1; terminal <= terminalCount; terminal++)
                require(Integer.valueOf(1).equals(endpointCounts.get(componentId + "." + terminal)),
                    "unconnected physical canary pad lost copper: " + componentId + "." + terminal);
        }
    }

    private static boolean hasLink(TopologyPlacementGraph topology, String componentId,
            String padId, String otherPadId, String netId) {
        for (TopologyPlacementGraph.PadLink link : topology.getLinksFor(componentId))
            if (padId.equals(link.getPadId()) && otherPadId.equals(link.getOtherPadId()) &&
                    netId.equals(link.getNetId()))
                return true;
        return false;
    }

    private static void increment(HashMap<String, Integer> counts, String id) {
        Integer count = counts.get(id);
        counts.put(id, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }

    private static PhysicalPackage packageFor(int terminalCount) {
        if (terminalCount == 3) return PhysicalPackages.DEV_CANARY_3;
        if (terminalCount == 4) return PhysicalPackages.DEV_CANARY_4;
        if (terminalCount == 5) return PhysicalPackages.DEV_CANARY_5;
        if (terminalCount == 6) return PhysicalPackages.DEV_CANARY_6;
        throw new IllegalArgumentException("Unsupported canary terminal count");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
