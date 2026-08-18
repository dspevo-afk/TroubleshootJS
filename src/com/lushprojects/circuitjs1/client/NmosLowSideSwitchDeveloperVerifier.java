package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import java.util.Random;

/** Dedicated Task 38 canary for NMOS solver, physical, privacy, and repair boundaries. */
final class NmosLowSideSwitchDeveloperVerifier {
    private NmosLowSideSwitchDeveloperVerifier() { }

    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady() &&
            NmosLowSideSwitchGenerator.FAMILY_ID.equals(instance.getCircuitFamilyId()),
            "NMOS challenge did not become ready");
        verifyTopologyAndPhysicalIdentity(instance);
        verifyBoardControlPath(sim, instance, challenge);
        verifyProviderFootprint(instance);
        verifyPrivacy(instance, challenge);
        verifyScenarioStatePurity(sim, instance, challenge);
        verifyLiveFault(instance, challenge, sim);
        verifyHealthyAndRepair(sim, instance, challenge);
        verifyDeterministicEnvelope();
        sim.setCircuitTitle("NMOS low-side verification passed");
    }

    private static void verifyTopologyAndPhysicalIdentity(GeneratedBoardInstance instance) {
        require(instance.getBoard().getPad("Q1.G") != null &&
            instance.getBoard().getPad("Q1.D") != null && instance.getBoard().getPad("Q1.S") != null,
            "NMOS board did not expose stable G/D/S pads");
        CircuitElm element = instance.getComponentBindings().getSingleElement("Q1");
        require(element instanceof NMosfetElm && element.getPostCount() == 3,
            "NMOS board is not backed by the real three-post NMosfetElm");
        PhysicalPart<?> physical = instance.getPhysicalBoardRuntime().getInstalledPart("Q1");
        require(physical instanceof PhysicalNmosPart && physical.getTerminalCount() == 3,
            "NMOS installed physical identity is incomplete");
        PhysicalNmosPart part = (PhysicalNmosPart) physical;
        require("G".equals(part.getTerminal(0).getTerminalName()) &&
            "D".equals(part.getTerminal(1).getTerminalName()) &&
            "S".equals(part.getTerminal(2).getTerminalName()),
            "NMOS physical terminal order is not G/D/S");
        require(sameEndpoint(part.getPublicTerminal(0), componentEndpoint(instance, "Q1.G")) &&
            sameEndpoint(part.getPublicTerminal(1), componentEndpoint(instance, "Q1.D")) &&
            sameEndpoint(part.getPublicTerminal(2), componentEndpoint(instance, "Q1.S")),
            "NMOS physical-to-board terminal mapping changed");
        require(part.ownsGeneratedFault(instance.getFaultBinding()),
            "NMOS generated fault is not privately owned by original Q1");
        require(Math.abs(NmosLowSideSwitchGeneratedBoardValidator.gateCurrent(instance)) < 1e-9,
            "NMOS solver gate current is not high impedance");
    }

    private static void verifyBoardControlPath(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        TroubleshootBoard board = instance.getBoard();
        require(board.getNet("GATE_DRIVE") == null && board.getNet("GATE") == null,
            "NMOS board retained a split gate/control net identity");
        require(board.getComponent("TP1") == null && board.getComponent("TP2") == null &&
            board.getPad("TP1.1") == null && board.getPad("TP1.2") == null &&
            board.getPad("TP2.1") == null && board.getPad("TP2.2") == null,
            "NMOS board retained pseudo test headers");
        String[] controlPads = { "J2.1", "RPD.1", "Q1.G" };
        for (String padId : controlPads)
            require("CONTROL_INPUT".equals(board.getPad(padId).getNetId()),
                "NMOS control pad is not on the single board control net: " + padId);
        verifyVisibleControlCopper(instance);

        NmosLowSideSwitchFamilyState state = state(instance);
        boolean priorCommand = state.isCommandedOn();
        BoardPowerState priorPower = sim.getBoardPowerController().getState();
        boolean priorFault = instance.getFaultBinding().isApplied();
        challenge.beginDeveloperVerificationScope();
        try {
            if (!challenge.getFaultController().clearForDeveloperVerification())
                throw new IllegalStateException("NMOS control canary could not clear its fault");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            state.setCommandedOn(sim, true);
            requireControlVoltageAgreement(instance, 4.5, 5.5,
                "NMOS commanded ON control voltage is not a shared +5 V board node");
            state.setCommandedOn(sim, false);
            requireControlVoltageAgreement(instance, -.1, .1,
                "NMOS commanded OFF control voltage is not pulled low");
            require(NmosLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
                "NMOS commanded OFF still drives the load");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle(sim);
            state.setCommandedOn(sim, true);
            requireControlVoltageAgreement(instance, -.1, .1,
                "NMOS board power OFF did not isolate the control input");
            require(NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) < .000001,
                "NMOS board power OFF did not isolate the load input");
        } finally {
            try {
                sim.setBoardPowerState(priorPower);
                settle(sim);
                state.setCommandedOn(sim, priorCommand);
                if (priorFault && !challenge.getFaultController().isApplied())
                    challenge.getFaultController().apply();
            } finally {
                challenge.endDeveloperVerificationScope();
                settle(sim);
            }
        }
    }

    private static void verifyVisibleControlCopper(GeneratedBoardInstance instance) {
        Vector<PcbTraceGeometry> traces = instance.getPcbLayout().getTraces();
        int controlTraceCount = 0;
        boolean reachesPullDown = false;
        boolean reachesGate = false;
        for (PcbTraceGeometry trace : traces) {
            if (!"CONTROL_INPUT".equals(trace.getNetId()))
                continue;
            controlTraceCount++;
            require("J2.1".equals(trace.getStartPadId()),
                "NMOS control copper does not use J2.1 as its stable root");
            reachesPullDown |= "RPD.1".equals(trace.getEndPadId());
            reachesGate |= "Q1.G".equals(trace.getEndPadId());
            require(trace.getXPoints().length >= 2,
                "NMOS control copper has no visible routed segment");
        }
        require(controlTraceCount == 2 && reachesPullDown && reachesGate,
            "NMOS PCB does not visibly join J2.1 to RPD.1 and Q1.G");
    }

    private static void requireControlVoltageAgreement(GeneratedBoardInstance instance,
            double minimum, double maximum, String message) {
        double j2 = NmosLowSideSwitchGeneratedBoardValidator.controlVoltage(instance);
        double rpd = NmosLowSideSwitchGeneratedBoardValidator.voltage(instance, "RPD.1") -
            NmosLowSideSwitchGeneratedBoardValidator.voltage(instance, "RPD.2");
        double gate = NmosLowSideSwitchGeneratedBoardValidator.boardGateVoltage(instance);
        require(j2 >= minimum && j2 <= maximum && rpd >= minimum && rpd <= maximum &&
            gate >= minimum && gate <= maximum && Math.abs(j2 - rpd) < 1e-6 &&
            Math.abs(j2 - gate) < 1e-6,
            message);
    }

    private static void settle(CirSim sim) {
        sim.needAnalyze();
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
    }

    private static void verifyProviderFootprint(GeneratedBoardInstance instance) {
        PcbBoardLayout layout = instance.getPcbLayout();
        PcbComponentPlacement actual = layout.getComponent("Q1");
        require(actual != null, "NMOS layout omitted Q1");
        PcbFootprint expected = StandardPcbFootprintProviders.createRegistry().create(
            instance.getBoard().getComponent("Q1"), actual.getX(), actual.getY(),
            new Random(instance.getSeed()), layout.getBoardOutline());
        require(samePlacement(actual, expected.getPlacement()),
            "NMOS Q1 geometry is not supplied by the registered provider");
        String[] terminals = { "Q1.G", "Q1.D", "Q1.S" };
        for (int index = 0; index < terminals.length; index++)
            require(samePad(layout.getPad(terminals[index]), expected.getPads().get(index)),
                "NMOS Q1 provider pad diverged: " + terminals[index]);
    }

    private static void verifyPrivacy(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        String complaint = challenge.getComplaintText();
        require(complaint.indexOf("NMOS") < 0 && complaint.indexOf("DS_OPEN") < 0 &&
            complaint.indexOf("DS_SHORT") < 0 && complaint.indexOf("GATE_OPEN") < 0 &&
            complaint.indexOf("Q1") < 0 && complaint.indexOf("2N7000") < 0,
            "NMOS normal-player complaint leaked fault or physical metadata");
        require(challenge.getScenario() != null &&
            challenge.getScenario().isCompatible(instance, null, BoardPowerState.POWERED),
            "NMOS selected complaint is not solver-compatible");
    }

    private static void verifyLiveFault(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, CirSim sim) {
        GeneratedFaultType type = challenge.getDefinition().getFault().getType();
        NmosLowSideSwitchFamilyState state = state(instance);
        if (type == GeneratedFaultType.NMOS_DS_SHORT) {
            state.setCommandedOn(sim, false);
            require(NmosLowSideSwitchGeneratedBoardValidator.gateSourceVoltage(instance) < .1 &&
                NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) > .005 &&
                NmosLowSideSwitchGeneratedBoardValidator.drainSourceVoltage(instance) < 1,
                "NMOS D-S short lacks its distinct low-control live symptom");
        } else {
            state.setCommandedOn(sim, true);
            require(NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) < .000001,
                "NMOS open/gate fault did not suppress the load");
            if (type == GeneratedFaultType.NMOS_DS_OPEN)
                require(NmosLowSideSwitchGeneratedBoardValidator.gateSourceVoltage(instance) > 3,
                    "NMOS D-S open does not preserve gate drive");
            else if (type == GeneratedFaultType.NMOS_GATE_OPEN)
                require(NmosLowSideSwitchGeneratedBoardValidator.boardGateVoltage(instance) > 3 &&
                    NmosLowSideSwitchGeneratedBoardValidator.internalGateVoltage(instance) < .1,
                    "NMOS gate-open diagnostic does not distinguish board/internal gate voltage");
            else
                throw new IllegalStateException("Unexpected NMOS normal-player fault: " + type);
        }
    }

    private static void verifyScenarioStatePurity(CirSim sim,
            GeneratedBoardInstance instance, GeneratedChallengeController challenge) {
        Vector<CircuitElm> topology = new Vector<CircuitElm>(sim.elmList);
        String export = sim.dumpCircuit();
        int undo = sim.undoStack.size();
        int redo = sim.redoStack.size();
        boolean unsaved = sim.unsavedChanges;
        boolean overlay = sim.activeMeasurementOverlay;
        BoardPowerState power = sim.getBoardPowerController().getState();
        boolean commandedOn = state(instance).isCommandedOn();
        boolean faultApplied = instance.getFaultBinding().isApplied();
        ComponentPhysicalState qState = sim.getBoardModificationController().getComponentState("Q1");
        require(challenge.getScenario().isCompatible(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED),
            "NMOS scenario compatibility was not live-solver compatible");
        require(commandedOn == state(instance).isCommandedOn() && power ==
                sim.getBoardPowerController().getState() && overlay == sim.activeMeasurementOverlay &&
                faultApplied == instance.getFaultBinding().isApplied() && qState ==
                sim.getBoardModificationController().getComponentState("Q1") &&
                sim.elmList.equals(topology) && export.equals(sim.dumpCircuit()) &&
                undo == sim.undoStack.size() && redo == sim.redoStack.size() &&
                unsaved == sim.unsavedChanges,
            "NMOS scenario compatibility leaked board state");
    }

    private static void verifyHealthyAndRepair(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        NmosLowSideSwitchFamilyState state = state(instance);
        boolean priorCommand = state.isCommandedOn();
        challenge.beginDeveloperVerificationScope();
        boolean faultRestored = false;
        try {
            require(challenge.getFaultController().clearForDeveloperVerification(),
                "NMOS developer verifier could not clear its private fault");
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.runCircuit(true);
            state.setCommandedOn(sim, true);
            require(NmosLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance),
                "Healthy NMOS ON proof failed live CircuitJS conditions");
            state.setCommandedOn(sim, false);
            require(NmosLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
                "Healthy NMOS OFF proof failed live CircuitJS conditions");
            require(NmosLowSideSwitchGeneratedBoardValidator.gateCurrent(instance) < 1e-9,
                "Healthy NMOS gate current exceeded high-impedance tolerance");
            challenge.getFaultController().apply();
            faultRestored = true;
        } finally {
            try {
                if (!faultRestored && !challenge.getFaultController().isApplied())
                    challenge.getFaultController().apply();
                state.setCommandedOn(sim, priorCommand);
            } finally {
                challenge.endDeveloperVerificationScope();
            }
        }

        NmosSlotController slots = sim.getNmosSlotController();
        require(slots != null, "NMOS challenge has no Q1 slot controller");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        PhysicalNmosPart original = (PhysicalNmosPart) instance.getPhysicalBoardRuntime()
            .getInstalledPart("Q1");
        require(slots.removeInstalledPart() && !original.isInstalled() && original.isFaulted(),
            "NMOS original remove did not preserve loose private fault identity");
        requirePrivateFaultGraph(sim, instance, true,
            "original loose part lost its private fault backing");
        for (int terminal = 0; terminal < 3; terminal++)
            require(new PhysicalNmosPartProbeTarget(sim, instance, original.getId(), terminal,
                sim.pcbWorkbenchController.getRenderer()).isValid(),
            "NMOS loose physical lead lacks a probe target: " + terminal);
        require(slots.install(original.getId()),
            "NMOS original reinstall was not accepted");
        requirePrivateFaultGraph(sim, instance, true,
            "original reinstall lost its private fault backing");
        requireOriginalFaultBoardPath(instance, true,
            "original reinstall did not restore its private board path");
        require(slots.removeInstalledPart() && slots.installNewFromCatalog(
            NmosReplacementCatalog.WRONG_HIGH_THRESHOLD),
            "NMOS wrong catalog replacement was not accepted");
        requirePrivateFaultGraph(sim, instance, true,
            "wrong catalog replacement lost declared private fault backing");
        requireOriginalFaultBoardPath(instance, false,
            "wrong catalog replacement retained original private board path");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Wrong NMOS replacement incorrectly passed functional repair");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() && slots.installNewFromCatalog(
            NmosReplacementCatalog.CORRECT),
            "NMOS correct catalog replacement was not accepted");
        requirePrivateFaultGraph(sim, instance, true,
            "correct catalog replacement lost declared private fault backing");
        requireOriginalFaultBoardPath(instance, false,
            "correct catalog replacement retained original private board path");
        require(!((PhysicalNmosPart) instance.getPhysicalBoardRuntime().getInstalledPart("Q1"))
                .ownsGeneratedFault(instance.getFaultBinding()),
            "catalog NMOS replacement inherited the original private fault identity");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correct NMOS replacement did not restore live behavior");
        Vector<CircuitElm> topology = new Vector<CircuitElm>(sim.elmList);
        String export = sim.dumpCircuit();
        int undo = sim.undoStack.size();
        int redo = sim.redoStack.size();
        boolean unsaved = sim.unsavedChanges;
        boolean overlay = sim.activeMeasurementOverlay;
        BoardPowerState power = sim.getBoardPowerController().getState();
        PhysicalNmosPart installed = (PhysicalNmosPart) instance.getPhysicalBoardRuntime()
            .getInstalledPart("Q1");
        require(challenge.finishJob() && challenge.isCompleted(),
            "Correct NMOS replacement did not finish generic challenge");
        require(sim.elmList.equals(topology) && export.equals(sim.dumpCircuit()) &&
                undo == sim.undoStack.size() && redo == sim.redoStack.size() &&
                unsaved == sim.unsavedChanges && overlay == sim.activeMeasurementOverlay &&
                power == sim.getBoardPowerController().getState() && installed ==
                instance.getPhysicalBoardRuntime().getInstalledPart("Q1") && installed.isInstalled(),
            "NMOS Finish Job leaked topology, export, history, or physical state");
    }

    private static void verifyDeterministicEnvelope() {
        GeneratedFaultType[] types = { GeneratedFaultType.NMOS_DS_OPEN,
            GeneratedFaultType.NMOS_DS_SHORT, GeneratedFaultType.NMOS_GATE_OPEN };
        for (int index = 0; index < types.length; index++) {
            GeneratedBoardInstance first = new NmosLowSideSwitchGenerator()
                .generateForFaultVerification(index, types[index]);
            GeneratedBoardInstance second = new NmosLowSideSwitchGenerator()
                .generateForFaultVerification(index, types[index]);
            require(first.getFaultBinding().getFault().getType() == types[index] &&
                first.getPcbLayout().geometryFingerprint().equals(
                    second.getPcbLayout().geometryFingerprint()),
                "NMOS deterministic envelope changed for " + types[index]);
        }
    }

    private static NmosLowSideSwitchFamilyState state(GeneratedBoardInstance instance) {
        return (NmosLowSideSwitchFamilyState) instance.getFamilyState();
    }
    private static CircuitMeasurementEndpoint componentEndpoint(GeneratedBoardInstance instance,
            String padId) {
        String componentId = instance.getBoard().getPad(padId).getComponentId();
        GeneratedComponentConnectionBinding binding = instance.getConnectionBindings()
            .get(componentId, padId);
        return binding.getComponentEndpoint();
    }
    private static boolean sameEndpoint(CircuitMeasurementEndpoint a,
            CircuitMeasurementEndpoint b) {
        if (!(a instanceof CircuitPostMeasurementEndpoint) ||
                !(b instanceof CircuitPostMeasurementEndpoint)) return false;
        CircuitPostMeasurementEndpoint first = (CircuitPostMeasurementEndpoint) a;
        CircuitPostMeasurementEndpoint second = (CircuitPostMeasurementEndpoint) b;
        return first.getElement() == second.getElement() && first.getPostIndex() == second.getPostIndex();
    }
    private static boolean samePlacement(PcbComponentPlacement a, PcbComponentPlacement b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getWidth() == b.getWidth() &&
            a.getHeight() == b.getHeight() && a.getKeepOut().equals(b.getKeepOut()) &&
            a.getRoutingCourtyard().equals(b.getRoutingCourtyard());
    }
    private static boolean samePad(PcbPadPlacement a, PcbPadPlacement b) {
        return a != null && b != null && a.getPadId().equals(b.getPadId()) &&
            a.getX() == b.getX() && a.getY() == b.getY() &&
            a.getEscapeDx() == b.getEscapeDx() && a.getEscapeDy() == b.getEscapeDy() &&
            a.getEscapeLength() == b.getEscapeLength();
    }
    private static void requirePrivateFaultGraph(CirSim sim, GeneratedBoardInstance instance,
            boolean expectedPresent, String message) {
        for (CircuitElm element : instance.getFaultBinding().getPrivateSimulationElements())
            if (sim.elmList.contains(element) != expectedPresent)
                throw new IllegalStateException(message);
    }
    private static void requireOriginalFaultBoardPath(GeneratedBoardInstance instance,
            boolean expectedEnabled, String message) {
        if (instance.getFaultBinding().getEffect() instanceof NmosfetDsShortFaultEffect &&
                ((NmosfetDsShortFaultEffect) instance.getFaultBinding().getEffect())
                    .isBoardPathEnabled() != expectedEnabled)
            throw new IllegalStateException(message);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
