package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.Vector;

/**
 * E04's isolated native/solver contract.  Every behavior assertion below is
 * made from the actual CircuitJS solved nodes, not from source metadata.
 */
public final class E04SensorControlContractTest {
    private static int assertions;

    private E04SensorControlContractTest() { }

    public static void main(String[] args) {
        verifyRailAndConfigurationRejections();
        verifyTerminalAndDirectThresholdBehavior();
        verifyDecisionDumpRoundTrip();
        verifyEndpointBindingsAndPassiveOwnership();
        verifyElementOwnershipCensusAndGhostRejection();
        verifyLoadingAndReferenceInfluence();
        verifyHystereticAscendingDescendingBehavior();
        verifyBrownoutAndUnsupportedStates();
        verifyE02RegulatorIntegration();
        verifyTimestepStability();
        System.out.println("PASS: E04 sensor-control contracts " + assertions +
                " assertions");
    }

    private static void verifyRailAndConfigurationRejections() {
        reject(new Runnable() { public void run() {
            new E04SensorControlModel.RailDeclaration("R", "RET", 5.0,
                    5.5, 1.0, true);
        }}, "brownout minimum above nominal rail");
        reject(new Runnable() { public void run() {
            new E04SensorControlModel.RailDeclaration("R", "RET", 5.0,
                    3.0, 0.0, true);
        }}, "zero rail source resistance");
        reject(new Runnable() { public void run() {
            new E04SensorControlModel.Configuration(10000.0, 100000.0,
                    10000.0, 10000.0, 0.0, 0.1, 0.1, 1.0, 3.0);
        }}, "equal hysteretic boundaries");
        reject(new Runnable() { public void run() {
            new E04SensorControlModel.Configuration(-1.0, 100000.0,
                    10000.0, 10000.0, 0.0, 0.1, -0.1, 1.0, 3.0);
        }}, "negative finite sensor source impedance");
        reject(new Runnable() { public void run() {
            new E04SensorControlModel(null,
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD);
        }}, "missing rail declaration");
        reject(new Runnable() { public void run() {
            E04SensorControlModel.fromE02Rail(null,
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD);
        }}, "missing E02 rail contract");
        reject(new Runnable() { public void run() {
            E04SensorControlModel.adaptE02Rail(
                    RailRegulationContract.linear5V(), 0.0);
        }}, "zero E02 consumer brownout minimum");
        reject(new Runnable() { public void run() {
            E04SensorControlModel.fromE02Rail(
                    RailRegulationContract.linear5V(),
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                    E04SensorControlModel.Configuration.defaults(
                            E04SensorControlModel.adaptE02Rail(
                                    RailRegulationContract.linear5V())),
                    24.1, true);
        }}, "E02 input above declared maximum");
        E04SensorControlModel.RailContract adapted =
                E04SensorControlModel.adaptE02Rail(
                        RailRegulationContract.linear5V());
        check(Math.abs(adapted.getSourceResistanceOhms() -
                        RailRegulationContract.linear5V()
                                .getOutputResistanceOhms()) <= 1e-12,
                "E02 adapter retains declared regulation impedance without Vnom/Imax droop");
    }

    private static void verifyTerminalAndDirectThresholdBehavior() {
        Harness h = new Harness(E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                1e-4);
        try {
            check(h.model.getSimulationElements().size() >= 20,
                    "direct fixture owns real source, passive, decision and wire elements");
            check(h.model.getTerminalMapping().size() == 5 &&
                    h.model.getTerminalMapping().get("SENSOR").getPostIndex() == 0 &&
                    h.model.getTerminalMapping().get("REFERENCE").getPostIndex() == 1 &&
                    h.model.getTerminalMapping().get("RAIL").getPostIndex() == 2,
                    "family-owned terminal mapping retains semantic posts");
            check(!h.model.hasRegenerativeFeedback(),
                    "direct variant has no regenerative feedback branch");

            h.setSensorVoltage(.6);
            double lowNode = h.settle().getSensorNodeVoltage();
            check(finite(lowNode) && lowNode > 0.0 &&
                    h.model.getControlState() == E04SensorControlModel.ControlState.LOW,
                    "actual low sensor node produces a low direct decision");
            check(h.model.getSensorCondition() ==
                    E04SensorControlModel.SensorCondition.SENSOR_LOW,
                    "semantic SENSOR_LOW is derived from the solved sensor node");
            h.model.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_MID);
            h.settle();
            check(h.model.getSensorCondition() ==
                    E04SensorControlModel.SensorCondition.SENSOR_MID,
                    "semantic SENSOR_MID is an actual bounded player sweep");
            h.setSensorVoltage(4.5);
            double highNode = h.settle().getSensorNodeVoltage();
            check(finite(highNode) && highNode > lowNode &&
                    h.model.getControlState() == E04SensorControlModel.ControlState.HIGH,
                    "actual high sensor node crosses direct threshold");
            check(h.model.getOutputVoltage() > h.model.getReferenceNodeVoltage() &&
                    h.model.getOutputCurrent() > 0.0,
                    "high decision drives a finite loaded solver output");
            check(h.model.getSensorCondition() ==
                    E04SensorControlModel.SensorCondition.SENSOR_HIGH,
                    "semantic SENSOR_HIGH is derived from the solved sensor node");
        } finally {
            h.close();
        }
    }

    /** The nonlinear E04 decision must remain safe for CircuitJS undo/export. */
    private static void verifyDecisionDumpRoundTrip() {
        Harness h = new Harness(E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE,
                1e-4);
        try {
            CircuitElm decision = h.model.getSensorControlBindings()
                    .getConditionedSensorEndpoint().getElement();
            check(decision instanceof E04SensorControlModel.DecisionElement &&
                    decision.getDumpType() == 457,
                    "E04 decision has an owned CircuitJS dump type");
            String before = decision.dump();
            h.model.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            h.settle();
            String after = decision.dump();
            check(before.equals(after),
                    "E04 decision dump excludes transient latch and solver state");

            StringTokenizer tokens = new StringTokenizer(after);
            int type = Integer.parseInt(tokens.nextToken());
            int x1 = Integer.parseInt(tokens.nextToken());
            int y1 = Integer.parseInt(tokens.nextToken());
            int x2 = Integer.parseInt(tokens.nextToken());
            int y2 = Integer.parseInt(tokens.nextToken());
            int flags = Integer.parseInt(tokens.nextToken());
            CircuitElm restored = CirSim.createCe(type, x1, y1, x2, y2, flags, tokens);
            check(restored instanceof E04SensorControlModel.DecisionElement &&
                    after.equals(restored.dump()),
                    "E04 decision dump round-trips through the CircuitJS factory");
        } finally {
            h.close();
        }
    }

    private static void verifyEndpointBindingsAndPassiveOwnership() {
        Harness h = new Harness(E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                1e-4);
        try {
            E04SensorControlModel.SensorControlBindings bindings =
                    h.model.getSensorControlBindings();
            check(bindings != null && bindings == h.model.getEndpointBindings(),
                    "E04 exposes one family-owned live endpoint binding view");

            CircuitPostMeasurementEndpoint external =
                    bindings.getExternalSensorSourceEndpoint();
            CircuitPostMeasurementEndpoint conditioned =
                    bindings.getConditionedSensorEndpoint();
            check(external != null && conditioned != null &&
                    external.getElement() instanceof VoltageElm &&
                    external.getPostIndex() == 1 &&
                    external.getElement().getPost(external.getPostIndex()) != null &&
                    external.getElement() != conditioned.getElement(),
                    "external sensor source boundary is a distinct live source post");

            CircuitElm semanticInput = conditioned.getElement();
            check(conditioned.getPostIndex() == 0 &&
                    semanticInput.getPost(conditioned.getPostIndex()) != null &&
                    bindings.getReferenceEndpoint().getElement() == semanticInput &&
                    bindings.getReferenceEndpoint().getPostIndex() == 1 &&
                    bindings.getRailEndpoint().getElement() == semanticInput &&
                    bindings.getRailEndpoint().getPostIndex() == 2 &&
                    bindings.getReturnEndpoint().getElement() == semanticInput &&
                    bindings.getReturnEndpoint().getPostIndex() == 4,
                    "sensor, reference, rail and return target the decision posts");

            CircuitPostMeasurementEndpoint loaded =
                    bindings.getLoadedOutputEndpoint();
            E04SensorControlModel.TerminalDescriptor output =
                    h.model.getTerminalMapping().get("OUTPUT");
            check(loaded.getElement() instanceof ResistorElm &&
                    loaded.getPostIndex() == 0 &&
                    loaded.getElement().getPost(loaded.getPostIndex()) != null &&
                    "R_E04_OUTPUT_LOAD".equals(output.getElementId()) &&
                    output.getPostIndex() == 0,
                    "OUTPUT maps to the actual loaded-output resistor post");

            String[] passiveIds = { "SENSOR_SOURCE_RESISTANCE", "SENSOR_LOAD",
                "RAIL_SOURCE_RESISTANCE", "RAIL_LOAD", "REFERENCE_HIGH",
                "REFERENCE_LOW", "OUTPUT_RESISTANCE", "OUTPUT_LOAD" };
            Map<String, E04SensorControlModel.PassiveBinding> passives =
                    bindings.getFaultablePassives();
            check(passives.size() == passiveIds.length,
                    "binding view exposes every generic faultable passive");
            for (int i = 0; i < passiveIds.length; i++) {
                E04SensorControlModel.PassiveBinding passive =
                        passives.get(passiveIds[i]);
                check(passive != null && passiveIds[i].equals(passive.getId()) &&
                        passive.getElement() == passive.getFirstEndpoint().getElement() &&
                        passive.getElement() == passive.getSecondEndpoint().getElement() &&
                        passive.getFirstEndpoint().getPostIndex() == 0 &&
                        passive.getSecondEndpoint().getPostIndex() == 1 &&
                        passive.getElement().getPost(0) != null &&
                        passive.getElement().getPost(1) != null,
                        "faultable passive has both actual CircuitJS endpoint posts");
                for (int j = 0; j < i; j++) {
                    E04SensorControlModel.PassiveBinding earlier =
                            passives.get(passiveIds[j]);
                    check(earlier.getElement() != passive.getElement(),
                            "faultable passive bindings do not duplicate elements");
                }
            }
        } finally {
            h.close();
        }
    }

    private static void verifyLoadingAndReferenceInfluence() {
        Harness h = new Harness(E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                1e-4);
        try {
            h.setSensorVoltage(2.4);
            h.settle();
            double lightlyLoaded = h.model.getSensorNodeVoltage();
            h.model.setSensorLoadOhms(1000.0);
            h.analyze();
            h.settle();
            double heavilyLoaded = h.model.getSensorNodeVoltage();
            check(heavilyLoaded < lightlyLoaded - .5,
                    "finite sensor source impedance exposes actual load drop");

            h.model.setSensorLoadOhms(100000.0);
            h.model.setReferenceDivider(30000.0, 10000.0);
            h.analyze();
            h.setSensorVoltage(1.6);
            h.settle();
            check(h.model.getReferenceNodeVoltage() < 2.0 &&
                    h.model.getControlState() == E04SensorControlModel.ControlState.HIGH,
                    "actual reference divider shifts direct threshold");

            h.model.setReferenceDivider(10000.0, 10000.0);
            h.analyze();
            h.settle();
            check(h.model.getReferenceNodeVoltage() > 2.0 &&
                    h.model.getControlState() == E04SensorControlModel.ControlState.LOW,
                    "restored reference changes solver-backed threshold behavior");
        } finally {
            h.close();
        }
    }

    /** Every emitted E04 solver element has exactly one physical ownership row. */
    private static void verifyElementOwnershipCensusAndGhostRejection() {
        for (E04SensorControlModel.Variant variant :
                new E04SensorControlModel.Variant[] {
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                    E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE }) {
            final Harness h = new Harness(variant, 1e-4);
            try {
                Vector<CircuitElm> live = h.model.getSimulationElements();
                Vector<E04SensorControlModel.ElementOwnership> rows =
                    h.model.getElementOwnership();
                h.model.validateElementOwnership(live);
                check(!rows.isEmpty() && rows.size() == live.size(),
                    "E04 ownership census has one row per live solver element");
                int mapped = 0;
                int external = 0;
                int internal = 0;
                int interconnect = 0;
                boolean hasDecision = false;
                boolean hasReferenceLow = false;
                boolean hasFeedback = false;
                for (CircuitElm element : live) {
                    E04SensorControlModel.ElementOwnership row =
                        h.model.getElementOwnership(element);
                    check(row != null && row.getElement() == element,
                        "every E04 simulation element has an exact identity row");
                    check(!"E04_INTERNAL".equals(row.getOwnerId()) &&
                            !"E04_BOARD_SUPPORT".equals(row.getOwnerId()),
                        "no unassigned E04 ownership placeholder survives");
                    if (row.getKind() == E04SensorControlModel.ElementOwnershipKind.MAPPED_COMPONENT)
                        mapped++;
                    else if (row.getKind() == E04SensorControlModel.ElementOwnershipKind.EXTERNAL_INFRASTRUCTURE) {
                        external++;
                        check("J1".equals(row.getOwnerId()) ||
                                "J2".equals(row.getOwnerId()) ||
                                "J3".equals(row.getOwnerId()) ||
                                "CONTROL_RETURN".equals(row.getOwnerId()),
                            "external support belongs to an explicit connector/return boundary");
                    } else if (row.getKind() == E04SensorControlModel.ElementOwnershipKind.INTERNAL_SUPPORT) {
                        internal++;
                        check("U2".equals(row.getOwnerId()) && element instanceof ResistorElm,
                            "both internal support loads are owned by physical U2");
                    } else if (row.getKind() == E04SensorControlModel.ElementOwnershipKind.BOARD_INTERCONNECT) {
                        interconnect++;
                        check(element instanceof WireElm && "PCB_COPPER".equals(row.getOwnerId()),
                            "topology wires declare their copper/interconnect owner");
                    } else
                        throw new AssertionError("Unknown E04 ownership category");
                    if ("U2".equals(row.getOwnerId()) &&
                            row.getKind() == E04SensorControlModel.ElementOwnershipKind.MAPPED_COMPONENT)
                        hasDecision = true;
                    if ("RREF_LOW".equals(row.getOwnerId())) hasReferenceLow = true;
                    if ("RFB_HYST".equals(row.getOwnerId())) hasFeedback = true;
                }
                check(mapped > 0 && external > 0 && internal == 2 && interconnect > 0,
                    "E04 census separates mapped components, external infrastructure, U2 loads and interconnect");
                check(hasDecision && hasReferenceLow,
                    "E04 census explicitly maps U2 decision and physical reference-low ownership");
                check(hasFeedback == (variant ==
                        E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE),
                    "E04 census includes hysteresis ownership only for the hysteretic variant");

                final Vector<CircuitElm> ghosted = new Vector<CircuitElm>(live);
                final ResistorElm ghost = new ResistorElm(2048, 2048);
                ghost.drag(2080, 2048);
                ghosted.add(ghost);
                reject(new Runnable() { public void run() {
                    h.model.validateElementOwnership(ghosted);
                }}, "foreign E04 physical ghost element");
                ghost.delete();
            } finally {
                h.close();
            }
        }
    }

    private static void verifyHystereticAscendingDescendingBehavior() {
        Harness h = new Harness(E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE,
                1e-4);
        try {
            check(h.model.hasRegenerativeFeedback(),
                    "hysteretic variant owns a physical feedback resistor");
            h.setSensorVoltage(1.0);
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.LOW,
                    "hysteretic control starts low");
            h.setSensorVoltage(4.5);
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.HIGH,
                    "ascending sensor sweep crosses rising threshold");
            double highOutput = h.model.getOutputVoltage();
            h.setSensorVoltage(2.5);
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
                    h.model.getOutputVoltage() > highOutput * .8,
                    "descending sweep inside hysteresis retains the high state");
            h.setSensorVoltage(.9);
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.LOW,
                    "descending sweep crosses falling threshold");
        } finally {
            h.close();
        }
    }

    private static void verifyBrownoutAndUnsupportedStates() {
        Harness h = new Harness(E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                1e-4);
        try {
            h.setSensorVoltage(4.5);
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.HIGH,
                    "healthy high is established before rail removal");
            double nominalRail = h.model.getRailNodeVoltage();
            double nominalOutput = h.model.getOutputVoltage();
            h.model.setRailVoltage(4.2);
            h.settle();
            check(h.model.getRailNodeVoltage() < nominalRail - .5 &&
                    h.model.getOutputVoltage() < nominalOutput - .5,
                    "valid rail sag changes actual rail and driven output nodes");
            h.model.setRailPowered(true);
            h.settle();
            h.model.setRailPowered(false);
            h.settle();
            check(h.model.getRailNodeVoltage() < .1 &&
                    h.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
                    !h.model.isOutputHigh(),
                    "power removal produces a solver-derived brownout");
            h.model.setRailPowered(true);
            h.settle();
            h.model.setReferenceAvailable(false);
            h.analyze();
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.REFERENCE_LOST,
                    "opened reference supply leg produces an explicit reference-loss state");
            h.model.setReferenceAvailable(true);
            h.analyze();
            h.model.setSensorVoltage(7.0);
            h.settle();
            check(h.model.getControlState() == E04SensorControlModel.ControlState.UNSUPPORTED &&
                    h.model.getSensorCondition() ==
                    E04SensorControlModel.SensorCondition.SENSOR_UNSUPPORTED,
                    "overrange sensor stimulus is explicitly unsupported");
        } finally {
            h.close();
        }
    }

    private static void verifyE02RegulatorIntegration() {
        verifyE02RegulatorVariant(RailRegulationContract.linear5V(),
                LinearRegulatorElm.class);
        verifyE02RegulatorVariant(RailRegulationContract.averagedSwitching5V(),
                AveragedSwitchingRegulatorElm.class);
    }

    private static void verifyE02RegulatorVariant(
            RailRegulationContract contract, Class<?> expectedType) {
        E02Harness h = new E02Harness(contract);
        try {
            check(h.model.hasE02Regulator() &&
                    contract.getVariantId().equals(
                            h.model.getE02RegulatorVariantId()),
                    "E04 retains the selected E02 regulator identity");
            AbstractRailRegulatorElm selected = null;
            for (CircuitElm element : h.model.getSimulationElements()) {
                if (expectedType.isInstance(element)) {
                    selected = (AbstractRailRegulatorElm) element;
                    break;
                }
            }
            check(selected != null,
                    "E04 solver graph contains the selected E02 regulator element");
            E04SensorControlModel.SensorControlBindings bindings =
                h.model.getSensorControlBindings();
            check(bindings.getRawInputEndpoint().getElement() instanceof E02FiniteSourceElm &&
                    bindings.getRawInputEndpoint().getPostIndex() == 1 &&
                    bindings.getRawReturnEndpoint().getElement() instanceof E02FiniteSourceElm &&
                    bindings.getRawReturnEndpoint().getPostIndex() == 0 &&
                    bindings.getRegulatorInputEndpoint().getElement() == selected &&
                    bindings.getRegulatorInputEndpoint().getPostIndex() ==
                        AbstractRailRegulatorElm.INPUT_POST &&
                    bindings.getRegulatorOutputEndpoint().getElement() == selected &&
                    bindings.getRegulatorOutputEndpoint().getPostIndex() ==
                        AbstractRailRegulatorElm.OUTPUT_POST &&
                    bindings.getRegulatorReturnEndpoint().getElement() == selected &&
                    bindings.getRegulatorReturnEndpoint().getPostIndex() ==
                        AbstractRailRegulatorElm.RETURN_POST &&
                    bindings.getRegulatorEnableEndpoint().getElement() == selected &&
                    bindings.getRegulatorEnableEndpoint().getPostIndex() ==
                        AbstractRailRegulatorElm.ENABLE_POST,
                "E04 exposes exact raw-source and selected-regulator posts for physical U1");
            h.model.setSensorVoltage(4.5);
            h.analyzeAndSettle();
            double nominalRail = h.model.getRailNodeVoltage();
            check(nominalRail > contract.getNominalOutputVolts() * .90 &&
                    h.model.getControlState() ==
                    E04SensorControlModel.ControlState.HIGH,
                    "selected E02 regulator drives a healthy E04 rail and control");

            h.model.setRegulatorInputVoltage(
                    contract.getNominalOutputVolts() +
                    -contract.getDropoutVolts() * .75);
            h.analyzeAndSettle();
            check(h.model.getRailNodeVoltage() < nominalRail - .25 &&
                    h.model.isRegulatorInDropout() &&
                    h.model.getControlState() ==
                    E04SensorControlModel.ControlState.BROWNOUT,
                    "actual E02 input headroom loss changes rail and E04 brownout state");

            h.model.setRegulatorInputVoltage(
                    contract.getNominalOutputVolts() + 2.0);
            h.model.setRegulatorEnabled(false);
            h.analyzeAndSettle();
            check(h.model.getRailNodeVoltage() < .1 &&
                    h.model.getControlState() ==
                    E04SensorControlModel.ControlState.BROWNOUT,
                    "actual E02 enable removal collapses the E04 rail");

            h.model.setRegulatorEnabled(true);
            h.analyzeAndSettle();
            check(h.model.getRailNodeVoltage() >
                    contract.getNominalOutputVolts() * .90 &&
                    h.model.getControlState() ==
                    E04SensorControlModel.ControlState.HIGH,
                    "actual E02 enable restoration recovers E04 control");
        } finally {
            h.close();
        }
    }

    private static void verifyTimestepStability() {
        Harness coarse = new Harness(E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE,
                1e-4);
        Harness fine = new Harness(E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE,
                1e-6);
        try {
            for (Harness h : new Harness[] {coarse, fine}) {
                h.setSensorVoltage(1.0); h.settle();
                h.setSensorVoltage(4.5); h.settle();
            }
            check(coarse.model.getControlState() == fine.model.getControlState() &&
                    coarse.model.getControlState() == E04SensorControlModel.ControlState.HIGH,
                    "hysteretic state is timestep-stable");
            check(Math.abs(coarse.model.getSensorNodeVoltage() -
                    fine.model.getSensorNodeVoltage()) < .02 &&
                    Math.abs(coarse.model.getRailNodeVoltage() -
                    fine.model.getRailNodeVoltage()) < .02,
                    "finite sensor and rail nodes remain timestep-stable");
        } finally {
            coarse.close();
            fine.close();
        }
    }

    private static final class Harness {
        final CirSim sim;
        final E04SensorControlModel model;

        Harness(E04SensorControlModel.Variant variant, double maxStep) {
            sim = new CirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = maxStep;
            sim.minTimeStep = Math.min(1e-9, maxStep * .001);
            sim.adjustTimeStep = true;
            sim.elmList = new Vector<CircuitElm>();
            sim.adjustables = new Vector<Adjustable>();
            CircuitElm.sim = sim;
            model = new E04SensorControlModel(
                    E04SensorControlModel.RailDeclaration.fiveVolt(), variant);
            sim.elmList = model.getSimulationElements();
            analyze();
            settle();
        }

        void setSensorVoltage(double voltage) {
            model.setSensorVoltage(voltage);
        }

        void analyze() {
            CircuitElm.sim = sim;
            sim.analyzeCircuit();
        }

        E04SensorControlModel settle() {
            CircuitElm.sim = sim;
            sim.solverExecutor.advanceSteps(8);
            return model;
        }

        void close() {
            CircuitElm.sim = sim;
            sim.solverExecutor.retire();
            model.dispose();
        }
    }

    private static final class E02Harness {
        final CirSim sim;
        final E04SensorControlModel model;

        E02Harness(RailRegulationContract contract) {
            sim = new CirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = 1e-4;
            sim.minTimeStep = 1e-7;
            sim.adjustTimeStep = true;
            sim.elmList = new Vector<CircuitElm>();
            sim.adjustables = new Vector<Adjustable>();
            CircuitElm.sim = sim;
            model = E04SensorControlModel.fromE02Rail(contract,
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD);
            sim.elmList = model.getSimulationElements();
            analyzeAndSettle();
        }

        void analyzeAndSettle() {
            CircuitElm.sim = sim;
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
        }

        void close() {
            CircuitElm.sim = sim;
            sim.solverExecutor.retire();
            model.dispose();
        }
    }

    private static void reject(Runnable action, String label) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Accepted invalid E04 input: " + label);
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
