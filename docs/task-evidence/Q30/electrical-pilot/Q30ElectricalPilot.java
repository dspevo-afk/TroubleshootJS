package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Bounded Q30 electrical integration pilot.
 *
 * <p>This is intentionally a solver-only composition fixture.  It uses one
 * real E02 12 V to 5 V regulator, two real E04 decision elements, and two
 * real E03 relay/driver channels.  The contact supply and loads are floating
 * infrastructure; they are included to make contact isolation and reference
 * policy observable, not as a second board rail.</p>
 */
public final class Q30ElectricalPilot {
    private static int assertions;

    public static void main(String[] args) {
        assertions = 0;
        Fixture fixture = null;
        try {
            fixture = new Fixture();
            fixture.verifyComposition();
            fixture.verifyReferencePolicy();
            fixture.verifyElectricalIntegration();
            System.out.println("PASS: Q30 electrical pilot " + assertions +
                    " assertions");
        } finally {
            if (fixture != null) fixture.close();
        }
    }

    private static final class Fixture {
        final CirSim sim;
        final RailRegulationContract regulatorContract;
        final E04SensorControlModel directModel;
        final E04SensorControlModel.RailContract sharedRail;
        final E04SensorControlModel.Configuration secondConfiguration;
        final E04SensorControlModel.DecisionElement hystereticDecision;

        final E02FiniteSourceElm secondSensorSource;
        final ResistorElm secondSensorResistance;
        final ResistorElm secondSensorLoad;
        final ResistorElm secondReferenceHigh;
        final ResistorElm secondReferenceLow;
        final ResistorElm hystereticGateResistance;
        final ResistorElm hystereticGatePullDown;
        final CapacitorElm hystereticGateCapacitance;

        final RelayDriverProvider bjtProvider;
        final RelayDriverProvider nmosProvider;
        final CircuitElm bjtDriver;
        final CircuitElm nmosDriver;
        final RelaySpecification relaySpecification;
        final ServiceRelayElm relayA;
        final ServiceRelayElm relayB;
        final SwitchElm sourceIsolationA;
        final SwitchElm sourceIsolationB;
        final ResistorElm bjtBaseResistance;
        final ResistorElm bjtBasePullDown;
        final CapacitorElm bjtBaseCapacitance;

        final E02FiniteSourceElm contactSource;
        final ResistorElm contactLoadA;
        final ResistorElm contactLoadB;
        final ResistorElm contactNcLeakA;
        final ResistorElm contactNcLeakB;
        final ResistorElm contactNumericalAnchor;

        final Vector<CircuitElm> extraElements = new Vector<CircuitElm>();
        final Vector<CircuitElm> allElements;

        Fixture() {
            sim = new CirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = 1e-5;
            sim.minTimeStep = 1e-9;
            sim.adjustTimeStep = true;
            sim.elmList = new Vector<CircuitElm>();
            sim.adjustables = new Vector<Adjustable>();
            CircuitElm.sim = sim;

            regulatorContract = RailRegulationContract.linear5V();
            directModel = E04SensorControlModel.fromE02Rail(
                    regulatorContract,
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD,
                    E04SensorControlModel.Configuration.defaults(
                            E04SensorControlModel.adaptE02Rail(regulatorContract)),
                    12.0, true);
            sharedRail = E04SensorControlModel.adaptE02Rail(regulatorContract);
            secondConfiguration = E04SensorControlModel.Configuration.defaults(sharedRail);
            hystereticDecision = add(new E04SensorControlModel.DecisionElement(
                    1408, 288, sharedRail,
                    E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE,
                    secondConfiguration));

            secondSensorSource = add(span(new E02FiniteSourceElm(
                    1280, 640, .55, .10), 1280, 704));
            secondSensorResistance = add(resistor(1408, 640, 1488, 640,
                    secondConfiguration.sensorSourceResistanceOhms));
            secondSensorLoad = add(resistor(1568, 640, 1568, 720,
                    secondConfiguration.sensorLoadOhms));
            secondReferenceHigh = add(resistor(1408, 160, 1488, 160,
                    secondConfiguration.referenceHighOhms));
            secondReferenceLow = add(resistor(1568, 384, 1648, 384,
                    secondConfiguration.referenceLowOhms));
            hystereticGateResistance = add(resistor(1728, 304, 1808, 304, 100.0));
            hystereticGatePullDown = add(resistor(1888, 400, 1888, 480, 100000.0));
            hystereticGateCapacitance = add(capacitor(2000, 400, 2000, 480, 1e-6));

            bjtProvider = new RelayDriverProvider.Bjt();
            nmosProvider = new RelayDriverProvider.Nmos();
            bjtDriver = add(bjtProvider.create(2208, 704));
            nmosDriver = add(nmosProvider.create(2208, 304));
            relaySpecification = new RelaySpecification(5.0);
            relayA = add(relaySpecification.create(2608, 704));
            relayB = add(relaySpecification.create(2608, 304));
            sourceIsolationA = add(span(new SwitchElm(2400, 704), 2480, 704));
            sourceIsolationB = add(span(new SwitchElm(2400, 304), 2480, 304));
            bjtBaseResistance = add(resistor(1728, 800, 1808, 800, 1000.0));
            bjtBasePullDown = add(resistor(1888, 896, 1888, 976, 100000.0));
            bjtBaseCapacitance = add(capacitor(2000, 896, 2000, 976, 1e-6));

            contactSource = add(span(new E02FiniteSourceElm(
                    3200, 96, 12.0, .10), 3200, 160));
            contactLoadA = add(resistor(3600, 640, 3680, 640, 470.0));
            contactLoadB = add(resistor(3600, 800, 3680, 800, 470.0));
            contactNcLeakA = add(resistor(3600, 320, 3680, 320, 1e9));
            contactNcLeakB = add(resistor(3600, 480, 3680, 480, 1e9));
            // CircuitJS itself adds a 100 MOhm numerical anchor to an
            // otherwise floating island during analysis.  Declare that same
            // bounded infrastructure explicitly so the native fixture avoids
            // an extra GWT-only floating-node diagnostic while preserving the
            // contact domain's non-ideal floating behavior.
            contactNumericalAnchor = add(resistor(3840, 96, 3920, 96, 1e8));

            CircuitPostMeasurementEndpoint railEndpoint =
                    directModel.getSensorControlBindings().getRailEndpoint();
            CircuitPostMeasurementEndpoint returnEndpoint =
                    directModel.getSensorControlBindings().getReturnEndpoint();
            Point rail = point(railEndpoint);
            Point controlReturn = point(returnEndpoint);

            add(wire(point(hystereticDecision, 2), rail));
            add(wire(point(hystereticDecision, 4), controlReturn));
            add(wire(point(secondSensorSource, 1), point(secondSensorResistance, 0)));
            add(wire(point(secondSensorResistance, 1), point(hystereticDecision, 0)));
            add(wire(point(hystereticDecision, 0), point(secondSensorLoad, 0)));
            add(wire(point(secondSensorSource, 0), controlReturn));
            add(wire(point(secondSensorLoad, 1), controlReturn));
            add(wire(rail, point(secondReferenceHigh, 0)));
            add(wire(point(secondReferenceHigh, 1), point(hystereticDecision, 1)));
            add(wire(point(hystereticDecision, 1), point(secondReferenceLow, 0)));
            add(wire(point(secondReferenceLow, 1), controlReturn));

            add(wire(point(hystereticDecision, 3), point(hystereticGateResistance, 0)));
            add(wire(point(hystereticGateResistance, 1), point(nmosDriver, 0)));
            add(wire(point(nmosDriver, 0), point(hystereticGatePullDown, 0)));
            add(wire(point(nmosDriver, 0), point(hystereticGateCapacitance, 0)));
            add(wire(point(hystereticGatePullDown, 1), controlReturn));
            add(wire(point(hystereticGateCapacitance, 1), controlReturn));

            add(wire(point(directModel.getDecisionElement(), 3),
                    point(bjtBaseResistance, 0)));
            add(wire(point(bjtBaseResistance, 1), point(bjtDriver, 0)));
            add(wire(point(bjtDriver, 0), point(bjtBasePullDown, 0)));
            add(wire(point(bjtDriver, 0), point(bjtBaseCapacitance, 0)));
            add(wire(point(bjtBasePullDown, 1), controlReturn));
            add(wire(point(bjtBaseCapacitance, 1), controlReturn));

            add(wire(rail, point(sourceIsolationA, 0)));
            add(wire(point(sourceIsolationA, 1), point(relayA, 3)));
            add(wire(point(relayA, 4), point(bjtDriver, 1)));
            add(wire(point(bjtDriver, 2), controlReturn));

            add(wire(rail, point(sourceIsolationB, 0)));
            add(wire(point(sourceIsolationB, 1), point(relayB, 3)));
            add(wire(point(relayB, 4), point(nmosDriver, 2)));
            add(wire(point(nmosDriver, 1), controlReturn));

            // Floating contact-domain loads are powered through the relay NO
            // contacts.  NC has only a bounded 1 GOhm leakage path so an open
            // contact is a real solver node without a hidden ideal short.
            add(wire(point(contactSource, 1), point(relayA, 0)));
            add(wire(point(contactSource, 1), point(relayB, 0)));
            add(wire(point(relayA, 2), point(contactLoadA, 0)));
            add(wire(point(relayB, 2), point(contactLoadB, 0)));
            add(wire(point(contactLoadA, 1), point(contactSource, 0)));
            add(wire(point(contactLoadB, 1), point(contactSource, 0)));
            add(wire(point(relayA, 1), point(contactNcLeakA, 0)));
            add(wire(point(relayB, 1), point(contactNcLeakB, 0)));
            add(wire(point(contactNcLeakA, 1), point(contactSource, 0)));
            add(wire(point(contactNcLeakB, 1), point(contactSource, 0)));
            add(wire(point(contactNumericalAnchor, 0), point(contactSource, 0)));
            add(wire(point(contactNumericalAnchor, 1), controlReturn));

            sourceIsolationA.position = 0;
            sourceIsolationB.position = 0;
            allElements = directModel.getSimulationElements();
            allElements.addAll(extraElements);
            sim.elmList = allElements;
        }

        void verifyComposition() {
            int regulatorCount = 0;
            int decisionCount = 0;
            int relayCount = 0;
            int bjtCount = 0;
            int nmosCount = 0;
            for (CircuitElm element : allElements) {
                if (element instanceof AbstractRailRegulatorElm) regulatorCount++;
                if (element instanceof E04SensorControlModel.DecisionElement) decisionCount++;
                if (element instanceof ServiceRelayElm) relayCount++;
                if (element instanceof NTransistorElm) bjtCount++;
                if (element instanceof NMosfetElm) nmosCount++;
            }
            check(regulatorCount == 1, "one causal E02 regulator in the composed graph");
            check(decisionCount == 2, "two real E04 DecisionElement instances");
            check(relayCount == 2, "two real E03 ServiceRelayElm instances");
            check(bjtCount == 1 && nmosCount == 1,
                    "provider-owned BJT and NMOS drivers are both present");
            check(regulatorContract.getNominalOutputVolts() == 5.0,
                    "shared E02 rail contract is 5 V");
            check(directModel.getDecisionElement() != hystereticDecision,
                    "direct and hysteretic controls have distinct solver identities");
            check(!directModel.getDecisionElement().dump().equals(
                    hystereticDecision.dump()),
                    "direct and hysteretic declarations remain structurally different");
            check(bjtProvider.getId().equals("BJT") && nmosProvider.getId().equals("NMOS"),
                    "driver provider identities are retained");
            System.out.println("Q30_COMPOSITION elements=" + allElements.size() +
                    " regulator=" + regulatorCount + " decisions=" + decisionCount +
                    " relays=" + relayCount + " bjt=" + bjtCount + " nmos=" + nmosCount);
        }

        void verifyReferencePolicy() {
            PowerDomainContract domains = RelayPowerDomains.create();
            MeasurementReferencePolicy.Result admitted =
                    MeasurementReferencePolicy.check(domains,
                            MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                            "CONTACT_OUT", "CONTACT_RETURN", null);
            MeasurementReferencePolicy.Result rejected =
                    MeasurementReferencePolicy.check(domains,
                            MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                            "CONTACT_OUT", "CTRL_RETURN", null);
            MeasurementReferencePolicy.Result earthUnproven =
                    MeasurementReferencePolicy.check(domains,
                            MeasurementReferencePolicy.Mode.EARTH_REFERENCED,
                            "CONTACT_OUT", "CONTACT_RETURN", null);
            check(admitted.admitsReading(),
                    "same floating contact-domain differential reference is admitted");
            check(rejected.getDecision() == MeasurementReferencePolicy.Decision.REJECTED,
                    "cross-domain differential reference is rejected");
            check(earthUnproven.getDecision() == MeasurementReferencePolicy.Decision.UNPROVEN,
                    "earth reference without declared earth remains unproven");
            System.out.println("Q30_REFERENCE admitted=" + admitted.getDecision() +
                    " rejected=" + rejected.getDecision() + " reason=" +
                    rejected.getReason() + " earth=" + earthUnproven.getDecision());
        }

        void verifyElectricalIntegration() {
            // Establish a settled all-off Newton point before applying the
            // causal rail.  This keeps the native fixture's first accepted
            // step inside the same bounded startup sequence as the player
            // board, rather than asking every nonlinear device to jump from
            // an uninitialized zero state at once.
            directModel.setRailPowered(false);
            secondSensorSource.setVoltage(.55);
            analyzeAndSettle();
            directModel.setRegulatorInputVoltage(1.0);
            directModel.setRailPowered(true);
            for (double input = 1.0; input <= 12.0; input += 1.0) {
                directModel.setRegulatorInputVoltage(input);
                analyzeAndSettle();
            }
            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_LOW);
            secondSensorSource.setVoltage(.55);
            analyzeAndSettle();
            printReading("unloaded");
            double unloadedRail = directModel.getRailNodeVoltage();
            double unloadedInputCurrent = directModel.getRailSourceCurrent();
            check(directModel.getControlState() ==
                    E04SensorControlModel.ControlState.LOW,
                    "direct E04 control starts low from its solved sensor node");
            check(voltage(hystereticDecision, 3, 4) < .25,
                    "hysteretic E04 output starts low from its solved graph");
            check(Math.abs(relayA.coilCurrent) < 1e-5 &&
                    Math.abs(relayB.coilCurrent) < 1e-5,
                    "unloaded state has no relay coil current");
            check(Math.abs(contactLoadA.getCurrent()) < .005 &&
                    Math.abs(contactLoadB.getCurrent()) < .005,
                    "unloaded state has no floating contact-load current");

            sourceIsolationA.position = 1;
            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            secondSensorSource.setVoltage(.55);
            analyzeAndSettle();
            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_LOW);
            analyzeAndSettle();
            sourceIsolationA.position = 0;
            analyzeAndSettle();
            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            analyzeAndSettle();
            printReading("channel-a-loaded");
            check(directModel.getControlState() ==
                    E04SensorControlModel.ControlState.HIGH,
                    "direct E04 high state is solver-derived");
            check(relayA.coilCurrent > .010 && Math.abs(relayB.coilCurrent) < .001,
                    "BJT channel energizes its relay without energizing NMOS channel");
            check(contactLoadA.getCurrent() > .020 &&
                    Math.abs(contactLoadB.getCurrent()) < .005,
                    "BJT relay switches only its real isolated contact load");

            sourceIsolationB.position = 1;
            secondSensorSource.setVoltage(4.5);
            analyzeAndSettle();
            sourceIsolationB.position = 0;
            analyzeAndSettle();
            printReading("both-channels-loaded");
            double bothLoadedRail = directModel.getRailNodeVoltage();
            double bothLoadedInputCurrent = directModel.getRailSourceCurrent();
            check(voltage(hystereticDecision, 3, 4) > 4.0,
                    "hysteretic E04 high state is solver-derived");
            check(relayA.coilCurrent > .010 && relayB.coilCurrent > .010,
                    "both provider channels draw real 5 V coil current");
            check(contactLoadA.getCurrent() > .020 && contactLoadB.getCurrent() > .020,
                    "both real relay contacts deliver their floating-domain loads");
            check(bothLoadedRail < unloadedRail - .001 && bothLoadedRail > 4.70,
                    "two-channel loading causes bounded shared-rail interaction");
            check(bothLoadedInputCurrent > unloadedInputCurrent + .005,
                    "E02 input current rises with the two real output channels");
            double coilTotal = Math.abs(relayA.coilCurrent) +
                    Math.abs(relayB.coilCurrent);
            double usableCoilEnvelope = regulatorContract.getUsableRegulatedCurrentAmps();
            System.out.println("Q30_ENVELOPE coilTotal=" + coilTotal +
                    " usableRegulated=" + usableCoilEnvelope);
            check(coilTotal < usableCoilEnvelope,
                    "two 5 V relay coils fit the E02 usable current envelope");

            // The same solved reference is deliberately placed inside the
            // hysteresis window.  Direct control must fall while hysteretic
            // control retains its high state until the falling threshold.
            directModel.setSensorVoltage(2.30);
            secondSensorSource.setVoltage(2.60);
            analyzeAndSettle();
            printReading("hysteresis-window");
            check(directModel.getControlState() ==
                    E04SensorControlModel.ControlState.LOW,
                    "direct threshold responds to the same in-window sensor value");
            check(voltage(hystereticDecision, 3, 4) > 4.0,
                    "hysteretic decision retains high output inside its falling window");
            check(relayA.coilCurrent < .001 && relayB.coilCurrent > .010,
                    "solver control differences propagate to the two driver channels");

            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            secondSensorSource.setVoltage(4.5);
            analyzeAndSettle();
            sourceIsolationA.position = 1;
            analyzeAndSettle();
            printReading("partial-source-isolation");
            double isolatedRail = directModel.getRailNodeVoltage();
            check(Math.abs(relayA.coilCurrent) < 1e-5,
                    "isolating channel A source removes its real coil current");
            check(relayB.coilCurrent > .010 && contactLoadB.getCurrent() > .020,
                    "channel B remains powered during partial source isolation");
            check(Math.abs(contactLoadA.getCurrent()) < .005,
                    "isolated channel A contact load is not backfed");
            check(isolatedRail > 4.70 && isolatedRail > bothLoadedRail,
                    "partial source isolation raises the shared rail without collapse");
            check(directModel.getRailSourceCurrent() >= -1e-6,
                    "isolated branch does not drive current back into the E02 source");

            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_LOW);
            analyzeAndSettle();
            sourceIsolationA.position = 0;
            analyzeAndSettle();
            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            analyzeAndSettle();
            check(relayA.coilCurrent > .010 && relayB.coilCurrent > .010,
                    "restoring channel A source restores both real coil loads");

            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_LOW);
            secondSensorSource.setVoltage(.55);
            directModel.setRegulatorInputVoltage(4.0);
            analyzeAndSettle();
            printReading("partial-input-power");
            check(directModel.getRailNodeVoltage() > .0 &&
                    directModel.getRailNodeVoltage() < 4.30,
                    "lowered causal E02 input produces a real partial-power rail");
            check(Math.abs(relayA.coilCurrent) < 1e-5 &&
                    Math.abs(relayB.coilCurrent) < 1e-5,
                    "partial-power brownout leaves both real relay coils off");
            check(directModel.getRailSourceCurrent() >= -1e-6,
                    "partial-power regulator does not accept contact-domain backfeed");

            directModel.setRegulatorInputVoltage(12.0);
            directModel.setSensorCondition(E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            secondSensorSource.setVoltage(4.5);
            analyzeAndSettle();
            printReading("restored");
            check(directModel.getRegulatorInputVoltage() > 11.0 &&
                    directModel.getRailNodeVoltage() > 4.70,
                    "causal 12 V E02 input restores the regulated 5 V rail");
            check(relayA.coilCurrent > .010 && relayB.coilCurrent > .010,
                    "restored rail re-energizes both provider relay channels");
        }

        void analyzeAndSettle() {
            CircuitElm.sim = sim;
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            sim.solverExecutor.advanceFor(.025);
        }

        void printReading(String label) {
            System.out.println("Q30_READING label=" + label +
                    " rail=" + directModel.getRailNodeVoltage() +
                    " inputV=" + directModel.getRegulatorInputVoltage() +
                    " sourceI=" + directModel.getRailSourceCurrent() +
                    " direct=" + directModel.getControlState() +
                    " hystereticSensor=" + voltage(hystereticDecision, 0, 4) +
                    " hystereticRef=" + voltage(hystereticDecision, 1, 4) +
                    " hystereticOut=" + voltage(hystereticDecision, 3, 4) +
                    " coilA=" + relayA.coilCurrent +
                    " coilB=" + relayB.coilCurrent +
                    " contactA=" + contactLoadA.getCurrent() +
                    " contactB=" + contactLoadB.getCurrent() +
                    " contactSourceI=" + contactSource.getCurrent() +
                    " isoA=" + sourceIsolationA.position +
                    " isoB=" + sourceIsolationB.position);
        }

        void close() {
            CircuitElm.sim = sim;
            sim.solverExecutor.retire();
            directModel.dispose();
            for (CircuitElm element : extraElements) element.delete();
        }

        private <T extends CircuitElm> T add(T element) {
            if (element == null) throw new IllegalArgumentException("Missing fixture element");
            extraElements.add(element);
            return element;
        }
    }

    private static ResistorElm resistor(int x, int y, int x2, int y2,
            double resistance) {
        ResistorElm result = new ResistorElm(x, y);
        result.setPosition(x, y, x2, y2);
        result.setResistance(resistance);
        return result;
    }

    private static CapacitorElm capacitor(int x, int y, int x2, int y2,
            double capacitance) {
        CapacitorElm result = new CapacitorElm(x, y);
        result.setPosition(x, y, x2, y2);
        result.setCapacitance(capacitance);
        result.flags |= CapacitorElm.FLAG_BACK_EULER;
        result.initialVoltage = 0.0;
        result.reset();
        return result;
    }

    private static <T extends CircuitElm> T span(T element, int x2, int y2) {
        element.drag(x2, y2);
        return element;
    }

    private static WireElm wire(Point from, Point to) {
        WireElm result = new WireElm(from.x, from.y);
        result.setPosition(from.x, from.y, to.x, to.y);
        return result;
    }

    private static Point point(CircuitPostMeasurementEndpoint endpoint) {
        return endpoint.getElement().getPost(endpoint.getPostIndex());
    }

    private static Point point(CircuitElm element, int post) {
        return element.getPost(post);
    }

    private static double voltage(CircuitElm element, int positive, int negative) {
        return element.getPostVoltage(positive) - element.getPostVoltage(negative);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError("Q30 electrical pilot: " + message);
    }
}
