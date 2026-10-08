package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Focused declaration/construction contracts; no powered-board or normal admission claim. */
public final class Rb56ControlTailContractTest {
    private static int assertions;
    public static void main(String[] args) {
        Rb56Plan reference = Rb56Plan.reference(0);
        require(Rb56Plan.SENSOR_LOW_VOLTS == 0 && Rb56Plan.SENSOR_HIGH_VOLTS == 5 &&
            reference.canonical().contains("sensor-commands=Q60-EXTERNAL-LOW:0.0,HIGH:5.0;"),
            "Current Q60 identity declares actual 0/5 V customer sensor commands");
        Rb56Plan low = Rb56Plan.configured(17, 1, Rb56Plan.ReferenceArrangement.SEPARATE_DIRECT, true, false, 1, 0, false);
        Rb56Plan high = Rb56Plan.configured(42, 2, Rb56Plan.ReferenceArrangement.SEPARATE_DIRECT, true, true, 3, 3, true);
        require(reference.physicalPackageCount() == 55 && reference.tailParts().size() == 35, "Explicit reference has 20 power and 35 purposeful tail packages");
        require(low.physicalPackageCount() == 40 && high.physicalPackageCount() == 60, "Retained low/high structural representatives are exact 40/60 packages");
        require(reference.channelCount == 2 && reference.sharedHystereticReference() && reference.hasFiveVoltIndicator &&
            reference.hasTwelveVoltIndicator && reference.sensorInputFilterMask == 3 && reference.outputIndicatorMask == 0 && !reference.hasFiveVoltBleeder,
            "Reference choices match the frozen 55-package device intent");
        verifyPowerInventory(reference);
        verifySeededPopulation();
        verifyIndependentConcerns();
        verifyRejectedShapes();
        verifyTail(reference); verifyTail(low); verifyTail(high);
        System.out.println("PASS: RB56 control-tail contracts assertions=" + assertions +
            " representatives=40,55,60 scope=DECLARATION_AND_CONSTRUCTION_ONLY");
    }

    private static void verifyPowerInventory(Rb56Plan plan) {
        HashSet<String> expected = new HashSet<String>();
        for (String id : new String[] {"JAC", "F1", "D1", "D2", "D3", "D4", "CBULK", "RPRIMARY", "UAC", "RENAC",
                "CBIAS", "DFREE", "L1", "COUT", "ROUT", "UFB", "DZ1", "RSENSE", "RFB", "CFB"}) expected.add(id);
        HashSet<String> actual = new HashSet<String>();
        for (Rb56Plan.Part part : plan.powerParts()) actual.add(part.id);
        require(actual.equals(expected) && actual.size() == 20, "Honest 20-package power census omits internal bias/Rw/ESR/wires");
        TroubleshootBoard board = plan.board();
        net(board, "UAC.IN+", "HV_POS"); net(board, "UAC.IN-", "HV_RETURN"); net(board, "UAC.PRE_L+", "PRE_L");
        net(board, "UAC.OUT-", "CTRL_RETURN"); net(board, "UAC.EN", "ENABLE_AC"); net(board, "UAC.FB", "FB_AC"); net(board, "UAC.BIAS", "BIAS_AC");
        net(board, "UFB.A", "SENSE_LED"); net(board, "UFB.K", "CTRL_RETURN"); net(board, "UFB.C", "FB_AC"); net(board, "UFB.E", "HV_RETURN");
        net(board, "D1.A", "AC_FUSED"); net(board, "D1.K", "HV_POS"); net(board, "D4.A", "HV_RETURN"); net(board, "D4.K", "AC_RETURN");
        net(board, "L1.1", "PRE_L"); net(board, "L1.2", "RAIL12"); net(board, "DZ1.A", "SENSE_LED"); net(board, "DZ1.K", "SENSE_ZENER");
        require(board.getPowerInput("MAINAC") != null && board.getPowerInput("MAIN12") == null,
            "Only the declared AC input supplies the converter domain");
        require(board.getPad("BIAS.1") == null && board.getComponent("RW") == null && board.getComponent("ESR") == null,
            "Internal converter/filter elements do not become filler packages");
    }

    private static void verifySeededPopulation() {
        HashSet<String> topologies = new HashSet<String>(); HashSet<Integer> counts = new HashSet<Integer>();
        boolean single = false, dual = false, bjt = false, mos = false;
        for (long seed = 0; seed < 64; seed++) {
            Rb56Plan plan = Rb56Plan.resolve(seed), repeated = Rb56Plan.resolve(seed);
            require(plan.canonical().equals(repeated.canonical()), "Current seed resolves deterministically");
            require(plan.physicalPackageCount() >= 40 && plan.physicalPackageCount() <= 60,
                "Every retained seeded representative is within the declared package population");
            if (plan.channelCount == 1) {
                single = true; require(plan.hasFiveVoltIndicator && plan.sensorInputFilterMask == 1,
                    "Single-channel minimum consists of real filter and rail-status functions");
            } else dual = true;
            bjt |= plan.driverABjt; mos |= !plan.driverABjt;
            topologies.add(plan.topology()); counts.add(plan.physicalPackageCount());
            verifyManifest(plan);
        }
        require(single && dual && bjt && mos && counts.size() >= 6 && topologies.size() >= 16,
            "Seeded inventory changes real channels, drivers and support populations");
        for (long seed : new long[] {-1, 9007199254740993L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            Rb56Plan plan = Rb56Plan.resolve(seed);
            require(plan.seed == seed && plan.canonical().contains(";seed=" + Long.toString(seed) + ";"),
                "Exact signed-long seed identity survives plan serialization");
            verifyManifest(plan);
        }
    }
    private static void verifyManifest(Rb56Plan plan) {
        TroubleshootBoard board = plan.board();
        require(board.getComponentIds().size() == plan.physicalPackageCount(), "One declaration inventory determines physical package count");
        HashSet<String> ids = new HashSet<String>();
        for (Rb56Plan.Part part : plan.parts()) {
            require(ids.add(part.id), "Every declared package has a unique physical identity");
            BoardComponent component = board.getComponent(part.id);
            require(component != null && component.getType().equals(part.type) && component.getPhysicalPackage().isEquivalentTo(part.physicalPackage),
                "Board package/type originates in the same construction declaration");
            String[] terminals = part.terminalIds(), nets = part.netIds();
            require(component.getPadIds().size() == terminals.length, "All package terminals are represented exactly once");
            for (int i = 0; i < terminals.length; i++) net(board, part.id + "." + terminals[i], nets[i]);
            nets[0] = "MUTATED_COPY";
            require(!"MUTATED_COPY".equals(part.netIds()[0]), "Returned declaration arrays cannot mutate the plan");
        }
        Vector<Rb56Plan.Part> copy = plan.parts(); copy.clear();
        require(plan.parts().size() == plan.physicalPackageCount(), "Returned inventory vector is independent");
        require(board.getPowerInputIds().size() == plan.channelCount + 2,
            "Source inventory is MAINAC, LOAD12 and one sensor per active channel");
    }

    private static void verifyIndependentConcerns() {
        Rb56Plan first = Rb56Plan.configured(101, 2, Rb56Plan.ReferenceArrangement.SHARED_DIRECT, false, false, 0, 0, false);
        Rb56Plan changed = Rb56Plan.configured(101, 2, Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC, true, true, 3, 3, true);
        require(first.driverABjt == changed.driverABjt && first.driverBBjt == changed.driverBBjt &&
            first.layoutSeed == changed.layoutSeed && first.routingSeed == changed.routingSeed,
            "Support/reference changes cannot shift independent driver/layout/routing concerns");
        require(!first.canonical().equals(changed.canonical()), "Declared functional changes invalidate device identity");
        NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION, 101, Rb56Plan.FAMILY_ID, Rb56Plan.PLAN_VERSION);
        require(first.layoutSeed == streams.deviceSeed(NamedRandomStreams.Concern.PLACEMENT, 1, "board") &&
            first.routingSeed == streams.deviceSeed(NamedRandomStreams.Concern.ROUTING, 1, "copper"),
            "RB56 uses its own named family/version seed derivation");
        require(!Rb56Plan.FAMILY_ID.equals(Rb30Plan.FAMILY_ID) && Rb56Plan.PLAN_VERSION == 1,
            "RB56 intent does not reuse or modify Q30 family identity");
    }
    private static void verifyRejectedShapes() {
        try { Rb56Plan.configured(0, 1, Rb56Plan.ReferenceArrangement.SEPARATE_DIRECT, false, false, 0, 0, false);
            throw new AssertionError("Below-40 declaration accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { Rb56Plan.configured(0, 2, Rb56Plan.ReferenceArrangement.SHARED_DIRECT, true, true, 4, 0, false);
            throw new AssertionError("Undeclared channel support accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { Rb56Plan.configured(0, 3, Rb56Plan.ReferenceArrangement.SHARED_DIRECT, true, true, 3, 3, true);
            throw new AssertionError("Unbounded channel population accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }

    private static void verifyTail(Rb56Plan plan) {
        CirSim previous = CircuitElm.sim, previousSingleton = CirSim.theSim;
        String previousDiode = DiodeElm.lastModelName, previousBjt = TransistorElm.lastModelName, previousLed = LEDElm.lastLEDModelName;
        RelayOutputGenerator.Assembly assembly = null;
        try {
            CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
            CircuitElm.sim = sim;
            DiodeElm.lastModelName = TransistorElm.lastModelName = "default"; LEDElm.lastLEDModelName = "default-led";
            assembly = new RelayOutputGenerator.Assembly(plan.board());
            for (String id : assembly.board.getNetIds()) assembly.net(id, assembly.board.getNet(id).getRoutingRole());
            Rb56ControlTail.Result result = Rb56ControlTail.build(assembly, plan);
            require(result.plan == plan && result.backing.size() == plan.tailParts().size() &&
                result.decisions.size() == plan.channelCount && result.relays.size() == plan.channelCount,
                "Control-tail result retains the exact plan and actual declared models");
            require(result.regulator == result.backing.get("U1") && result.regulator.getContract().isLinear() &&
                result.regulator.getContract().getNominalOutputVolts() == 5, "Actual E02 linear regulator consumes the adopted RAIL12 node");
            net(assembly.board, "U1.INPUT", "RAIL12"); net(assembly.board, "U1.RETURN", "CTRL_RETURN");
            require(result.loadSource == result.backing.get("JLOAD") && result.loadSource.maxVoltage == 12,
                "One independent actual LOAD12 source feeds relay contacts");
            int limitedSources = 0;
            for (CircuitElm element : assembly.elements) {
                if (element instanceof LimitedDcSupplyElm) limitedSources++;
                require(!(element instanceof GroundElm), "Tail introduces no second ground or primary-secondary reference bond");
            }
            require(limitedSources == plan.channelCount + 1, "Tail allocates only sensor/load supplies, never a MAIN12 source");
            require(assembly.board.getSimulationBindings().getEndpoint("JAC.1") == null &&
                !assembly.power.hasControlsForAllInputs(), "Tail construction does not pretend to complete the absent primary stage");
            for (String channel : plan.channels()) {
                E04SensorControlModel.DecisionElement decision = result.decisions.get(channel);
                ServiceRelayElm relay = result.relays.get(channel);
                require(decision == result.backing.get("U2" + channel) && relay == result.backing.get("K" + channel),
                    "Returned decision/relay objects are the graph's exact declared backing");
                require(decision.declarationIdentity().contains("variant=" + (plan.sharedHystereticReference() ?
                    "HYSTERETIC_REGENERATIVE" : "DIRECT_THRESHOLD")), "Selected reference selects the existing real E04 law");
                net(assembly.board, "U2" + channel + ".REFERENCE", plan.referenceNet(channel));
                net(assembly.board, "K" + channel + ".A1", "RAIL5"); net(assembly.board, "K" + channel + ".COM", "LOAD12");
                net(assembly.board, "K" + channel + ".NO", "OUT_" + channel);
                require(relay.coilR == 125 && relay.r_on == .2 && relay.r_off == 1e9 && relay.inductance == .2,
                    "Real qualified 5 V RL relay model is retained");
                LimitedDcSupplyElm sensor = result.sensorSources.get(channel);
                require(sensor == result.backing.get("JS" + channel) && sensor.maxVoltage == Rb56Plan.SENSOR_LOW_VOLTS &&
                    assembly.power.getBinding("SENSOR_" + channel).getLimitedSupply() == sensor,
                    "Fresh low sensor condition is an actual source and exact external binding");
                require(assembly.specifications.getPowerInputNameplate("SENSOR_" + channel).getNominalVoltage() == 5,
                    "Sensor connector preserves its declared 5 V range nameplate");
                BoundedExternalLoadElm load = result.loads.get(channel);
                require(load == result.backing.get("JO" + channel) && load.getResistance() == 180 &&
                    assembly.connections.getConnectorHarness("JO" + channel)[0].getElement() == load,
                    "Output header retains the actual bounded external load/harness");
                CircuitElm driver = result.backing.get("Q" + channel);
                require(plan.driver(channel).getId().equals("BJT") ? driver instanceof NTransistorElm : driver instanceof NMosfetElm,
                    "Driver provider chooses its exact actual model");
                int[] driverPosts = plan.driver(channel).getPosts(); String[] driverTerminals = plan.driver(channel).getTerminals();
                for (int i = 0; i < driverPosts.length; i++) endpoint(assembly, "Q" + channel, driverTerminals[i], driver, driverPosts[i]);
                for (int i = 0; i < RelaySpecification.POSTS.length; i++)
                    endpoint(assembly, "K" + channel, RelaySpecification.TERMINALS[i], relay, RelaySpecification.POSTS[i]);
                DiodeElm flyback = (DiodeElm) result.backing.get("D" + channel);
                require("1N4148".equals(flyback.modelName), "Actual flyback junction model is retained");
                net(assembly.board, "D" + channel + ".A", channel + "_COIL_LOW"); net(assembly.board, "D" + channel + ".K", "RAIL5");
            }
            for (Rb56Plan.Part part : plan.tailParts()) {
                CircuitElm element = result.backing.get(part.id);
                require(element != null && assembly.elements.contains(element) && assembly.specifications.getSpecification(part.id) != null,
                    "Every tail package has actual owned backing and physical specification");
                if (part.kind == Rb56Plan.Kind.RESISTOR) require(((ResistorElm)element).getResistance() == part.value,
                    "Declared support resistance is stamped by the actual resistor");
                if (part.kind == Rb56Plan.Kind.CAPACITOR) {
                    CapacitorElm capacitor = (CapacitorElm)element;
                    CapacitorSpecification specification = (CapacitorSpecification)assembly.specifications.getSpecification(part.id);
                    require(capacitor.getCapacitance() == part.value && !capacitor.isTrapezoidal() && capacitor.initialVoltage == 0 && capacitor.voltdiff == 0,
                        "Declared support capacitance uses the fresh zero-state Q60 integration recipe");
                    require(specification.hasExplicitModelRecipe() && specification.getCapacitanceFarads() == part.value &&
                        specification.getEsrOhms() == 0 && specification.getIntegrationFlags() == CapacitorElm.FLAG_BACK_EULER &&
                        specification.getInitialVoltage() == 0 && specification.getRatedVoltage() == 25 &&
                        specification.getPhysicalPackage().isEquivalentTo(part.physicalPackage),
                        "Typed replacement recipe exactly matches each actual tail capacitor");
                    String expected = part.value == 1e-6 ? "CAP_Q60_1UF_25V_CERAMIC_BE0" :
                        part.value == 100e-9 ? "CAP_Q60_100NF_25V_CERAMIC_BE0" : "CAP_Q60_22UF_25V_ELECTROLYTIC_BE0";
                    require(expected.equals(specification.getSpecificationId()) && !expected.equals(part.id),
                        "Capacitor catalog identity is stable by value/rating/package independently of designator");
                }
            }
            assembly.connections.validateAgainst(assembly.board, assembly.elements, assembly.components, assembly.power, null);
            assembly.power.validateElementsAreOwnedBy(assembly.elements);
            require(true, "Existing strict connection and external source ownership validators accept the real tail");
        } finally {
            try {
                if (assembly != null) for (CircuitElm element : assembly.elements) element.delete();
            } finally {
                CircuitElm.sim = previous; CirSim.theSim = previousSingleton;
                DiodeElm.lastModelName = previousDiode; TransistorElm.lastModelName = previousBjt; LEDElm.lastLEDModelName = previousLed;
            }
        }
    }
    private static void endpoint(RelayOutputGenerator.Assembly assembly, String id, String terminal, CircuitElm element, int post) {
        GeneratedComponentConnectionBinding binding = assembly.connections.get(id, id + "." + terminal);
        require(GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), new CircuitPostMeasurementEndpoint(element, post)),
            "Provider/relay terminal order reaches the correct live post");
        CircuitPostMeasurementEndpoint board = (CircuitPostMeasurementEndpoint)binding.getBoardEndpoint();
        require(board.getElement() != binding.getConnectionElement() && board.getElement() != element,
            "Persistent copper is independent of each detachable package lead");
    }
    private static void net(TroubleshootBoard board, String pad, String net) {
        require(board.getPad(pad) != null && net.equals(board.getPad(pad).getNetId()), "Exact intended physical net: " + pad);
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
