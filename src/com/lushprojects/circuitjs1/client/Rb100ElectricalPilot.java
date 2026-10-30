package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Builds one unpublished, electrically complete RB100 CircuitJS graph. */
final class Rb100ElectricalPilot {
    private Rb100ElectricalPilot() { }

    static final class Candidate {
        final Rb100Plan plan;
        final RelayOutputGenerator.Assembly assembly;
        final Rb56PowerStageAssembly power;
        final Map<String, CircuitElm> backing;
        final Map<String, E04SensorControlModel.DecisionElement> decisions;
        final Map<String, ServiceRelayElm> relays;
        final Map<String, LimitedDcSupplyElm> sensorSources;
        final Map<String, BoundedExternalLoadElm> loads;
        private boolean disposed;

        private Candidate(Rb100Plan plan, RelayOutputGenerator.Assembly assembly,
                Rb56PowerStageAssembly power, Builder builder) {
            this.plan = plan;
            this.assembly = assembly;
            this.power = power;
            backing = immutable(builder.backing);
            decisions = immutable(builder.decisions);
            relays = immutable(builder.relays);
            sensorSources = immutable(builder.sensorSources);
            loads = immutable(builder.loads);
        }

        Vector<CircuitElm> elements() {
            if (disposed) throw new IllegalStateException("RB100 unpublished graph was disposed");
            return new Vector<CircuitElm>(assembly.elements);
        }

        TroubleshootBoard board() { return assembly.board; }

        /**
         * Retires this private graph before installation. An installed graph is
         * owned and retired by its live caller, never by this candidate.
         */
        void dispose() {
            if (disposed) return;
            if (assembly.board.getSimulationBindings().isConstructionComplete())
                throw new IllegalStateException("Installed RB100 graph disposal belongs to its live owner");
            CirSim sim = CircuitElm.sim;
            if (sim != null && sim.elmList != null)
                for (CircuitElm element : assembly.elements)
                    if (sim.elmList.contains(element))
                        throw new IllegalStateException("RB100 graph disposal belongs to its current solver owner");
            disposed = true;
            disposeGraph(assembly, null);
        }
    }

    static Candidate construct(Rb100Plan plan) {
        requirePlan(plan);
        TroubleshootBoard board = plan.board();
        if (!(Rb100Plan.FAMILY_ID + "_BOARD").equals(board.getId()) ||
                board.getComponentIds().size() != Rb100Plan.PACKAGE_COUNT)
            throw new IllegalArgumentException("RB100 plan produced a foreign or incomplete board identity");
        RelayOutputGenerator.Assembly assembly = new RelayOutputGenerator.Assembly(board);
        try {
            for (String net : board.getNetIds())
                assembly.net(net, board.getNet(net).getRoutingRole());

            validatePlanManifest(plan, assembly);

            // The RB56 adapter contributes only the exact, reused 20-package
            // low-level power graph. Board identity and all declaration checks
            // remain owned by this RB100 plan.
            Rb56PowerStageAssembly power = new Rb56PowerStageAssembly(assembly, plan.base);
            Builder builder = new Builder(plan, assembly);
            for (Rb56Plan.Part part : plan.base.powerParts())
                builder.backing.put(part.id, power.primary(part.id));
            for (Rb100Plan.Part part : plan.tailParts()) builder.construct(part);

            assembly.requireCompleteManifest();
            addOnlyPrimaryGround(assembly, power);
            validateActualMapping(plan, assembly, power, builder);
            return new Candidate(plan, assembly, power, builder);
        } catch (RuntimeException failure) {
            disposeGraph(assembly, failure);
            throw failure;
        } catch (Error failure) {
            disposeGraph(assembly, failure);
            throw failure;
        }
    }

    private static void requirePlan(Rb100Plan plan) {
        if (plan == null || plan.variant == null || plan.base == null)
            throw new IllegalArgumentException("Missing RB100 construction plan");
        Rb56Plan.ReferenceArrangement expected = plan.variant == Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR ?
            Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC :
            Rb56Plan.ReferenceArrangement.SEPARATE_DIRECT;
        NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            plan.seed, Rb100Plan.FAMILY_ID, Rb100Plan.PLAN_VERSION);
        long expectedBaseSeed = streams.deviceSeed(NamedRandomStreams.Concern.TOPOLOGY,
            1, "base-control");
        Rb56Plan base = plan.base;
        if (base.seed != expectedBaseSeed || base.channelCount != 2 ||
                base.referenceArrangement != expected || base.hasFiveVoltIndicator ||
                base.hasTwelveVoltIndicator || base.sensorInputFilterMask != 3 ||
                base.outputIndicatorMask != 0 || base.hasFiveVoltBleeder)
            throw new IllegalArgumentException("RB100 plan does not contain its declared 51-package RB56 core");
        if (plan.parts().size() != Rb100Plan.PACKAGE_COUNT ||
                plan.physicalPackageCount() != Rb100Plan.PACKAGE_COUNT)
            throw new IllegalArgumentException("RB100 electrical pilot requires its exact 100-package plan");
    }

    private static void validatePlanManifest(Rb100Plan plan,
            RelayOutputGenerator.Assembly assembly) {
        TreeMap<String, Rb100Plan.Part> declarations = new TreeMap<String, Rb100Plan.Part>();
        int powerCount = 0;
        for (Rb100Plan.Part part : plan.parts()) {
            if (declarations.put(part.id, part) != null)
                throw new IllegalArgumentException("Duplicate RB100 package declaration: " + part.id);
            BoardComponent component = assembly.board.getComponent(part.id);
            if (component == null || !part.type.equals(component.getType()) ||
                    !part.physicalPackage.isEquivalentTo(component.getPhysicalPackage()))
                throw new IllegalArgumentException("RB100 board package disagrees with plan: " + part.id);
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            if (terminals.length != nets.length || terminals.length != part.physicalPackage.getTerminalCount() ||
                    component.getPadIds().size() != terminals.length)
                throw new IllegalArgumentException("RB100 terminal census disagrees with plan: " + part.id);
            for (int index = 0; index < terminals.length; index++) {
                BoardPad pad = assembly.board.getPad(part.id + "." + terminals[index]);
                if (pad == null || !part.id.equals(pad.getComponentId()) ||
                        !terminals[index].equals(pad.getTerminalId()) ||
                        !nets[index].equals(pad.getNetId()))
                    throw new IllegalArgumentException("RB100 board terminal disagrees with plan: " +
                        part.id + "." + terminals[index]);
            }
            if (part.kind == Rb100Plan.Kind.POWER) powerCount++;
            if (part.inputId != null) {
                ExternalBoardPowerInput input = assembly.board.getPowerInput(part.inputId);
                if (input == null || !input.getPositivePadId().equals(part.id + "." + terminals[0]) ||
                        !input.getReturnPadId().equals(part.id + "." + terminals[1]) ||
                        !input.getPositiveNetId().equals(nets[0]) || !input.getReturnNetId().equals(nets[1]))
                    throw new IllegalArgumentException("RB100 power-input declaration disagrees with plan: " + part.id);
            }
        }
        if (powerCount != Rb56Plan.POWER_PACKAGES ||
                plan.base.powerParts().size() != Rb56Plan.POWER_PACKAGES)
            throw new IllegalArgumentException("RB100 must reuse exactly the 20 declared RB56 power packages");

        for (Rb56Plan.Part basePart : plan.base.parts()) {
            Rb100Plan.Part actual = declarations.get(basePart.id);
            if (actual == null || actual.kind != Rb100Plan.Kind.valueOf(basePart.kind.name()) ||
                    !actual.type.equals(basePart.type) ||
                    !actual.physicalPackage.isEquivalentTo(basePart.physicalPackage) ||
                    Double.doubleToLongBits(actual.value) != Double.doubleToLongBits(basePart.value) ||
                    !equal(actual.channel, basePart.channel) || !equal(actual.inputId, basePart.inputId) ||
                    !same(actual.terminalIds(), basePart.terminalIds()) ||
                    !sameQ100BaseNets(basePart, actual))
                throw new IllegalArgumentException("RB100 changed a declared RB56 core package: " + basePart.id);
        }
        for (Rb56Plan.Part powerPart : plan.base.powerParts()) {
            Rb100Plan.Part actual = declarations.get(powerPart.id);
            if (actual == null || actual.kind != Rb100Plan.Kind.POWER ||
                    !actual.type.equals(powerPart.type) ||
                    !actual.physicalPackage.isEquivalentTo(powerPart.physicalPackage) ||
                    Double.doubleToLongBits(actual.value) != Double.doubleToLongBits(powerPart.value) ||
                    !equal(actual.channel, powerPart.channel) || !equal(actual.inputId, powerPart.inputId) ||
                    !same(actual.terminalIds(), powerPart.terminalIds()) ||
                    !same(actual.netIds(), powerPart.netIds()))
                throw new IllegalArgumentException("RB100 power declaration differs from its RB56 adapter: " +
                    powerPart.id);
        }

        TreeSet<String> inputIds = new TreeSet<String>(assembly.board.getPowerInputIds());
        TreeSet<String> expectedInputs = new TreeSet<String>();
        expectedInputs.add("MAINAC");
        expectedInputs.add("LOAD12");
        for (String channel : new String[] {"A", "B", "C", "D"})
            expectedInputs.add("SENSOR_" + channel);
        if (!inputIds.equals(expectedInputs))
            throw new IllegalArgumentException("RB100 must declare MAINAC, LOAD12 and sensor inputs A-D");
    }

    /** Q100 model 4 shares only these three inherited load returns with CTRL_RETURN. */
    private static boolean sameQ100BaseNets(Rb56Plan.Part basePart, Rb100Plan.Part actual) {
        String[] expected = basePart.netIds();
        if ("JLOAD".equals(basePart.id) || "JOA".equals(basePart.id) ||
                "JOB".equals(basePart.id)) {
            if (Rb100Plan.MODEL_VERSION != 4 || expected.length != 2 ||
                    !"LOAD_RETURN".equals(expected[1]))
                return false;
            expected[1] = "CTRL_RETURN";
        }
        return same(actual.netIds(), expected);
    }

    private static void addOnlyPrimaryGround(RelayOutputGenerator.Assembly assembly,
            Rb56PowerStageAssembly power) {
        Point sourceReturn = power.stage.input.source.getPost(0);
        GroundElm ground = new GroundElm(sourceReturn.x, sourceReturn.y);
        ground.x2 = sourceReturn.x;
        ground.y2 = sourceReturn.y + 32;
        ground.setPoints();
        assembly.elements.add(ground);
        int groundCount = 0;
        for (CircuitElm element : assembly.elements) if (element instanceof GroundElm) {
            groundCount++;
            if (element != ground || !element.getPost(0).equals(sourceReturn))
                throw new IllegalStateException("RB100 must have only the mains-source ground reference");
        }
        if (groundCount != 1)
            throw new IllegalStateException("RB100 primary source ground was not uniquely constructed");
    }

    private static void validateActualMapping(Rb100Plan plan,
            RelayOutputGenerator.Assembly assembly, Rb56PowerStageAssembly power,
            Builder builder) {
        if (!Rb100Plan.FAMILY_ID.equals(assembly.board.getId().substring(0,
                assembly.board.getId().length() - "_BOARD".length())) ||
                assembly.elements.isEmpty() || assembly.nets.size() != assembly.board.getNetIds().size() ||
                builder.backing.size() != Rb100Plan.PACKAGE_COUNT)
            throw new IllegalStateException("RB100 candidate is not a complete private electrical graph");

        TreeSet<String> ids = new TreeSet<String>();
        for (Rb100Plan.Part part : plan.parts()) {
            ids.add(part.id);
            CircuitElm primary = builder.backing.get(part.id);
            if (primary == null || !assembly.elements.contains(primary))
                throw new IllegalStateException("RB100 package has no owned CircuitJS backing: " + part.id);
            if (part.kind == Rb100Plan.Kind.SOURCE || "JAC".equals(part.id)) {
                if (assembly.components.hasComponentBinding(part.id))
                    throw new IllegalStateException("External RB100 input/header was misbound as an installed component: " + part.id);
            } else if (!assembly.components.hasComponentBinding(part.id)) {
                throw new IllegalStateException("RB100 package has no component-to-solver binding: " + part.id);
            }
            for (String terminal : part.terminalIds()) {
                String padId = part.id + "." + terminal;
                CircuitMeasurementEndpoint endpoint = assembly.board.getSimulationBindings().getEndpoint(padId);
                if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                    throw new IllegalStateException("RB100 terminal lacks an actual CircuitJS endpoint: " + padId);
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
                if (!assembly.elements.contains(post.getElement()) || post.getPostIndex() < 0 ||
                        post.getPostIndex() >= post.getElement().getPostCount() ||
                        post.getElement().getPost(post.getPostIndex()) == null)
                    throw new IllegalStateException("RB100 terminal endpoint is outside the candidate graph: " + padId);
            }
        }
        if (!ids.equals(new TreeSet<String>(builder.backing.keySet())))
            throw new IllegalStateException("RB100 backing map does not exactly match its 100-package manifest");
        verifyDeclaredBackingValues(plan, builder);
        verifyCIN33Esr(plan, assembly, builder);

        // This checks every detachable pad's solver endpoint, component owner,
        // and actual one-wire lead against the same board-owned component map.
        assembly.connections.validateAgainst(assembly.board, assembly.elements,
            assembly.components, assembly.power, null);
        assembly.power.validateElementsAreOwnedBy(assembly.elements);
        if (!assembly.power.hasControlsForAllInputs() ||
                assembly.board.getPowerInputIds().size() != 6)
            throw new IllegalStateException("RB100 external power control map is incomplete");

        if (builder.sensorSources.size() != 4 || builder.loads.size() != 6 ||
                builder.decisions.size() != 5 || builder.relays.size() != 4)
            throw new IllegalStateException("RB100 source, load, decision, or relay population is incomplete");
        for (String channel : new String[] {"A", "B", "C", "D"}) {
            LimitedDcSupplyElm source = builder.sensorSources.get(channel);
            if (source == null || source.getLimitAmps() != Rb100Plan.SENSOR_MAX_AMPS ||
                    assembly.power.getBinding("SENSOR_" + channel).getLimitedSupply() != source)
                throw new IllegalStateException("RB100 fresh LOW sensor source is not the declared live input: " + channel);
            ServiceRelayElm relay = builder.relays.get(channel);
            if (relay == null || builder.backing.get("K" + channel) != relay)
                throw new IllegalStateException("RB100 relay map does not identify its actual CircuitJS relay: " + channel);
        }
        for (String channel : new String[] {"A", "B", "C", "D", "E", "F"}) {
            BoundedExternalLoadElm load = builder.loads.get(channel);
            if (load == null || load.getResistance() != Rb100Plan.EXTERNAL_LOAD_OHMS || load.hasFailed())
                throw new IllegalStateException("RB100 external load does not match its bounded actual model: " + channel);
        }
        for (String channel : new String[] {"A", "B", "C", "D"})
            if (!(builder.decisions.get(channel) instanceof E04SensorControlModel.DecisionElement))
                throw new IllegalStateException("RB100 channel decision is not the actual E04 element: " + channel);
        if (!(builder.decisions.get("MON") instanceof E04SensorControlModel.DecisionElement))
            throw new IllegalStateException("RB100 current monitor does not map to its actual E04 decision element");

        ExternalPowerSimulationBinding loadInput = assembly.power.getBinding("LOAD12");
        if (loadInput.getLimitedSupply() != builder.backing.get("JLOAD") ||
                loadInput.getLimitedSupply().getLimitAmps() != Rb100Plan.LOAD12_MAX_AMPS)
            throw new IllegalStateException("RB100 LOAD12 source does not use its frozen 500 mA bound");

        verifyLoadPowerDomain(plan, assembly, builder);

        verifyDcHarness(plan, assembly, builder, "E", "JOE");
        verifyDcHarness(plan, assembly, builder, "F", "JOF");
    }

    private static void verifyLoadPowerDomain(Rb100Plan plan,
            RelayOutputGenerator.Assembly assembly, Builder builder) {
        if (assembly.board.getNet("LOAD_RETURN") != null ||
                assembly.nets.containsKey("LOAD_RETURN"))
            throw new IllegalStateException("RB100 model 4 retained the superseded LOAD_RETURN net");
        Rb100Plan.Part declaration = plan.part("JLOAD");
        String[] nets = declaration.netIds();
        CircuitElm backing = builder.backing.get("JLOAD");
        Point controlReturn = assembly.nets.get("CTRL_RETURN");
        if (declaration.kind != Rb100Plan.Kind.SOURCE || !"LOAD12".equals(declaration.inputId) ||
                nets.length != 2 || !"LOAD12".equals(nets[0]) ||
                !"CTRL_RETURN".equals(nets[1]) || !(backing instanceof LimitedDcSupplyElm) ||
                controlReturn == null)
            throw new IllegalStateException("RB100 JLOAD source declaration is not LOAD12/CTRL_RETURN");
        LimitedDcSupplyElm source = (LimitedDcSupplyElm) backing;
        String[] terminals = declaration.terminalIds();
        CircuitPostMeasurementEndpoint[] harness = assembly.connections.getConnectorHarness("JLOAD");
        CircuitMeasurementEndpoint mappedReturn = terminals.length == 2 ?
            assembly.board.getSimulationBindings().getEndpoint("JLOAD." + terminals[1]) : null;
        if (source.getPost(0) == null || harness == null || harness.length != 2 ||
                !(harness[1].getElement() instanceof WireElm) || harness[1].getPostIndex() != 1 ||
                !(mappedReturn instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("RB100 JLOAD return lead is not mapped to an actual wire endpoint");
        WireElm negativeLead = (WireElm) harness[1].getElement();
        CircuitPostMeasurementEndpoint mappedReturnPost =
            (CircuitPostMeasurementEndpoint) mappedReturn;
        if (!assembly.elements.contains(negativeLead) ||
                mappedReturnPost.getElement() != negativeLead || mappedReturnPost.getPostIndex() != 1 ||
                negativeLead.getPost(0) == null || negativeLead.getPost(1) == null ||
                !negativeLead.getPost(0).equals(source.getPost(0)) ||
                !controlReturn.equals(negativeLead.getPost(1)))
            throw new IllegalStateException("RB100 JLOAD source return is not the actual CTRL_RETURN node");
    }

    private static void verifyDcHarness(Rb100Plan plan,
            RelayOutputGenerator.Assembly assembly, Builder builder,
            String channel, String id) {
        Rb100Plan.Part part = plan.part(id);
        String[] nets = part.netIds();
        String[] terminals = part.terminalIds();
        GeneratedComponentConnectionBinding firstBinding = assembly.connections.get(
            id, id + "." + terminals[0]);
        GeneratedComponentConnectionBinding secondBinding = assembly.connections.get(
            id, id + "." + terminals[1]);
        CircuitPostMeasurementEndpoint firstBoard = asPost(firstBinding.getBoardEndpoint(), id);
        CircuitPostMeasurementEndpoint secondBoard = asPost(secondBinding.getBoardEndpoint(), id);
        CircuitPostMeasurementEndpoint firstComponent = asPost(firstBinding.getComponentEndpoint(), id);
        CircuitPostMeasurementEndpoint secondComponent = asPost(secondBinding.getComponentEndpoint(), id);
        BoundedExternalLoadElm load = builder.loads.get(channel);
        CircuitElm inductor = builder.dcHarnessInductors.get(channel);
        if (load == null || !(inductor instanceof InductorElm) ||
                ((InductorElm) inductor).getInductance() != Rb100Plan.EXTERNAL_LOAD_HENRIES ||
                firstComponent.getElement() != load || firstComponent.getPostIndex() != 0 ||
                secondComponent.getElement() != inductor || secondComponent.getPostIndex() != 1 ||
                !"LOAD12".equals(nets[0]) || !("OUT_" + channel).equals(nets[1]))
            throw new IllegalStateException("RB100 external RL harness does not map the declared header nets: " + id);
        CircuitPostMeasurementEndpoint[] harness = assembly.connections.getConnectorHarness(id);
        if (harness == null || harness[0].getElement() != load || harness[0].getPostIndex() != 0 ||
                harness[1].getElement() != inductor || harness[1].getPostIndex() != 1 ||
                !boardEndpointAtNet(firstBoard, assembly.nets.get(nets[0])) ||
                !boardEndpointAtNet(secondBoard, assembly.nets.get(nets[1])))
            throw new IllegalStateException("RB100 output header terminal does not land on its declared net: " + id);
        WireElm series = null;
        for (CircuitElm element : assembly.elements) if (element instanceof WireElm) {
            WireElm wire = (WireElm) element;
            if (wire.getPost(0).equals(load.getPost(1)) &&
                    wire.getPost(1).equals(inductor.getPost(0)) ||
                    wire.getPost(1).equals(load.getPost(1)) &&
                    wire.getPost(0).equals(inductor.getPost(0))) {
                series = wire;
                break;
            }
        }
        if (series == null)
            throw new IllegalStateException("RB100 external RL harness is not electrically series connected: " + id);
    }

    private static void verifyDeclaredBackingValues(Rb100Plan plan, Builder builder) {
        for (Rb100Plan.Part part : plan.tailParts()) {
            CircuitElm element = builder.backing.get(part.id);
            switch (part.kind) {
            case SOURCE:
                if (!(element instanceof LimitedDcSupplyElm))
                    throw new IllegalStateException("RB100 source is not a bounded CircuitJS supply: " + part.id);
                LimitedDcSupplyElm source = (LimitedDcSupplyElm) element;
                double expectedVolts = part.channel == null ? Rb100Plan.LOAD12_VOLTS :
                    Rb100Plan.SENSOR_LOW_VOLTS;
                double expectedLimit = part.channel == null ? Rb100Plan.LOAD12_MAX_AMPS :
                    Rb100Plan.SENSOR_MAX_AMPS;
                if (source.maxVoltage != expectedVolts || source.getLimitAmps() != expectedLimit ||
                        source.waveform != VoltageElm.WF_DC || source.bias != 0)
                    throw new IllegalStateException("RB100 source differs from its explicit bound: " + part.id);
                break;
            case REGULATOR:
                if (!(element instanceof AbstractRailRegulatorElm))
                    throw new IllegalStateException("RB100 rail is not an actual E02 element: " + part.id);
                RailRegulationContract expected = "U1".equals(part.id) ?
                    RailRegulationContract.linear5V() : builder.rail3v3;
                if (((AbstractRailRegulatorElm) element).getContract().getVariantId()
                        .equals(expected.getVariantId()) == false ||
                        ((AbstractRailRegulatorElm) element).getContract().getNominalOutputVolts() != part.value)
                    throw new IllegalStateException("RB100 E02 contract disagrees with its plan: " + part.id);
                break;
            case DECISION:
                if (!(element instanceof E04SensorControlModel.DecisionElement) ||
                        !element.equals(builder.decisions.get(part.channel)))
                    throw new IllegalStateException("RB100 decision package is not its actual E04 element: " + part.id);
                break;
            case DRIVER:
                RelayDriverProvider driver = plan.driver(part.channel);
                if (!driver.getId().equals(part.type) ||
                        !driver.getPackage().isEquivalentTo(part.physicalPackage) ||
                        ("BJT".equals(part.type) && !(element instanceof NTransistorElm)) ||
                        ("NMOS".equals(part.type) && !(element instanceof NMosfetElm)))
                    throw new IllegalStateException("RB100 transistor backing differs from its plan: " + part.id);
                break;
            case RELAY:
                if (!(element instanceof ServiceRelayElm))
                    throw new IllegalStateException("RB100 relay is not the actual CircuitJS relay: " + part.id);
                break;
            case RESISTOR:
                if (!(element instanceof ResistorElm) ||
                        ((ResistorElm) element).getResistance() != part.value)
                    throw new IllegalStateException("RB100 resistor differs from its declared value: " + part.id);
                break;
            case CAPACITOR:
                if (!(element instanceof CapacitorElm) ||
                        ((CapacitorElm) element).getCapacitance() != part.value ||
                        ((CapacitorElm) element).initialVoltage != 0 ||
                        ((((CapacitorElm) element).flags & CapacitorElm.FLAG_BACK_EULER) == 0))
                    throw new IllegalStateException("RB100 capacitor differs from its zero-energy model: " + part.id);
                PhysicalSpecification capacitorDefinition = builder.assembly.specifications
                    .getSpecification(part.id);
                double expectedEsr = "CIN33".equals(part.id) ? Rb100Plan.CIN33_ESR_OHMS : 0;
                if (!(capacitorDefinition instanceof CapacitorSpecification))
                    throw new IllegalStateException("RB100 capacitor has no typed model declaration: " + part.id);
                CapacitorSpecification capacitorSpec = (CapacitorSpecification) capacitorDefinition;
                if (!capacitorSpec.hasExplicitModelRecipe() ||
                        capacitorSpec.getEsrOhms() != expectedEsr ||
                        capacitorSpec.getIntegrationFlags() != CapacitorElm.FLAG_BACK_EULER ||
                        capacitorSpec.getInitialVoltage() != 0 ||
                        capacitorSpec.getCapacitanceFarads() != part.value)
                    throw new IllegalStateException("RB100 capacitor recipe disagrees with actual backing: " + part.id);
                break;
            case DIODE:
                if (!(element instanceof DiodeElm) || !"1N4148".equals(((DiodeElm) element).modelName))
                    throw new IllegalStateException("RB100 diode differs from its declared model: " + part.id);
                break;
            case OUTPUT_HEADER:
                if (builder.loads.get(part.channel) == null ||
                        builder.loads.get(part.channel).getResistance() != part.value)
                    throw new IllegalStateException("RB100 output header lacks its declared actual load: " + part.id);
                break;
            default:
                throw new IllegalStateException("Unsupported RB100 actual backing kind: " + part.id);
            }
        }
    }

    /** Checks the internal CIN33 series ESR and the package's two public terminals. */
    private static void verifyCIN33Esr(Rb100Plan plan,
            RelayOutputGenerator.Assembly assembly, Builder builder) {
        Rb100Plan.Part part = plan.part("CIN33");
        if (part == null || part.kind != Rb100Plan.Kind.CAPACITOR || part.value != 22e-6 ||
                !part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR))
            throw new IllegalStateException("RB100 CIN33 plan declaration is not the input capacitor");
        String[] terminals = part.terminalIds();
        String[] nets = part.netIds();
        if (terminals.length != 2 || nets.length != 2 ||
                !"RAIL12".equals(nets[0]) || !"CTRL_RETURN".equals(nets[1]))
            throw new IllegalStateException("RB100 CIN33 polarity or net order changed in the manifest");
        CircuitElm primary = assembly.components.getSingleElement("CIN33");
        Vector<CircuitElm> auxiliary = assembly.components.getAuxiliaryElements("CIN33");
        if (!(primary instanceof CapacitorElm) || auxiliary.size() != 1 ||
                !(auxiliary.firstElement() instanceof ResistorElm) ||
                builder.backing.get("CIN33") != primary ||
                !assembly.elements.contains(primary) || !assembly.elements.contains(auxiliary.firstElement()))
            throw new IllegalStateException("RB100 CIN33 ESR is not one package-owned auxiliary resistor");
        CapacitorElm capacitor = (CapacitorElm) primary;
        ResistorElm esr = (ResistorElm) auxiliary.firstElement();
        Point positiveTerminal = esr.getPost(0);
        Point negativeTerminal = capacitor.getPost(1);
        if (esr.resistance != Rb100Plan.CIN33_ESR_OHMS ||
                positiveTerminal == null || negativeTerminal == null ||
                esr.getPost(1) == null || capacitor.getPost(0) == null ||
                !esr.getPost(1).equals(capacitor.getPost(0)))
            throw new IllegalStateException("RB100 CIN33 ESR does not touch the actual capacitor-side junction");

        Point junction = capacitor.getPost(0);
        Point positiveNet = assembly.nets.get(nets[0]);
        Point negativeNet = assembly.nets.get(nets[1]);
        if (positiveNet == null || negativeNet == null || junction.equals(positiveNet) ||
                junction.equals(negativeNet) || junction.equals(positiveTerminal) ||
                junction.equals(negativeTerminal) || positiveTerminal.equals(negativeTerminal) ||
                positiveNet.equals(negativeNet))
            throw new IllegalStateException("RB100 CIN33 private ESR junction collapsed onto a logical net");

        GeneratedComponentConnectionBinding positive = assembly.connections.get(
            "CIN33", "CIN33." + terminals[0]);
        GeneratedComponentConnectionBinding negative = assembly.connections.get(
            "CIN33", "CIN33." + terminals[1]);
        CircuitPostMeasurementEndpoint positiveBoard = asPost(positive.getBoardEndpoint(), "CIN33");
        CircuitPostMeasurementEndpoint negativeBoard = asPost(negative.getBoardEndpoint(), "CIN33");
        CircuitPostMeasurementEndpoint positiveComponent = asPost(positive.getComponentEndpoint(), "CIN33");
        CircuitPostMeasurementEndpoint negativeComponent = asPost(negative.getComponentEndpoint(), "CIN33");
        if (positiveComponent.getElement() != esr || positiveComponent.getPostIndex() != 0 ||
                negativeComponent.getElement() != capacitor || negativeComponent.getPostIndex() != 1 ||
                !boardEndpointAtNet(positiveBoard, positiveNet) ||
                !boardEndpointAtNet(negativeBoard, negativeNet) ||
                !assembly.components.isElementBoundToComponent("CIN33", esr))
            throw new IllegalStateException("RB100 CIN33 public terminals do not map across its private ESR");
        for (String componentId : assembly.board.getComponentIds())
            if (!"CIN33".equals(componentId) &&
                    assembly.components.isElementBoundToComponent(componentId, esr))
                throw new IllegalStateException("RB100 CIN33 ESR is shared with another package: " + componentId);

        PhysicalSpecification declaration = assembly.specifications.getSpecification("CIN33");
        if (!(declaration instanceof CapacitorSpecification))
            throw new IllegalStateException("RB100 CIN33 lacks a capacitor model declaration");
        CapacitorSpecification spec = (CapacitorSpecification) declaration;
        if (!"CAP_RB100_CIN33_22UF_25V_ELECTROLYTIC_BE0_ESR1".equals(spec.getSpecificationId()) ||
                !spec.hasExplicitModelRecipe() || spec.getEsrOhms() != Rb100Plan.CIN33_ESR_OHMS ||
                spec.getIntegrationFlags() != CapacitorElm.FLAG_BACK_EULER ||
                spec.getInitialVoltage() != 0 || spec.getCapacitanceFarads() != part.value ||
                spec.getRatedVoltage() != 25 || spec.getTolerancePercent() != 10 ||
                !spec.getPhysicalPackage().isEquivalentTo(part.physicalPackage))
            throw new IllegalStateException("RB100 CIN33 package model does not declare its actual series ESR");
    }

    private static CircuitPostMeasurementEndpoint asPost(CircuitMeasurementEndpoint endpoint,
            String id) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("RB100 header endpoint is not a CircuitJS post: " + id);
        return (CircuitPostMeasurementEndpoint) endpoint;
    }

    private static boolean boardEndpointAtNet(CircuitPostMeasurementEndpoint endpoint, Point net) {
        if (!(endpoint.getElement() instanceof WireElm) || net == null) return false;
        WireElm copper = (WireElm) endpoint.getElement();
        int other = endpoint.getPostIndex() == 0 ? 1 : 0;
        return endpoint.getPostIndex() >= 0 && endpoint.getPostIndex() <= 1 &&
            net.equals(copper.getPost(other));
    }

    private static void disposeGraph(RelayOutputGenerator.Assembly assembly, Throwable original) {
        if (assembly == null) return;
        Throwable firstCleanupFailure = null;
        try { assembly.power.clearForAbortedConstruction(assembly.board); }
        catch (Throwable cleanup) { firstCleanupFailure = recordCleanup(original, firstCleanupFailure, cleanup); }
        try { assembly.connections.clearForAbortedConstruction(assembly.board); }
        catch (Throwable cleanup) { firstCleanupFailure = recordCleanup(original, firstCleanupFailure, cleanup); }
        try { assembly.components.clearForAbortedConstruction(assembly.board); }
        catch (Throwable cleanup) { firstCleanupFailure = recordCleanup(original, firstCleanupFailure, cleanup); }
        try { assembly.board.getSimulationBindings().clearForAbortedConstruction(assembly.board); }
        catch (Throwable cleanup) { firstCleanupFailure = recordCleanup(original, firstCleanupFailure, cleanup); }
        for (CircuitElm element : new Vector<CircuitElm>(assembly.elements)) {
            try { element.delete(); }
            catch (Throwable cleanup) { firstCleanupFailure = recordCleanup(original, firstCleanupFailure, cleanup); }
        }
        assembly.elements.clear();
        if (original == null && firstCleanupFailure != null)
            throw new IllegalStateException("RB100 unpublished graph cleanup failed", firstCleanupFailure);
    }

    private static Throwable recordCleanup(Throwable original, Throwable first,
            Throwable cleanup) {
        if (original != null) original.addSuppressed(cleanup);
        return first == null ? cleanup : first;
    }

    private static boolean same(String[] first, String[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        for (int index = 0; index < first.length; index++)
            if (!equal(first[index], second[index])) return false;
        return true;
    }

    private static boolean equal(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static <T> Map<String, T> immutable(Map<String, T> source) {
        return Collections.unmodifiableMap(new TreeMap<String, T>(source));
    }

    private static final class Builder {
        final Rb100Plan plan;
        final RelayOutputGenerator.Assembly assembly;
        final RailRegulationContract rail5 = RailRegulationContract.linear5V();
        final RailRegulationContract rail3v3;
        final E04SensorControlModel.RailContract decisionRail5 =
            E04SensorControlModel.adaptE02Rail(rail5);
        final E04SensorControlModel.RailContract decisionRail3v3;
        final E04SensorControlModel.Variant baseDecisionVariant;
        final E04SensorControlModel.Configuration baseConfiguration;
        final E04SensorControlModel.Configuration directSensorConfiguration;
        final E04SensorControlModel.Configuration monitorConfiguration;
        final TreeMap<String, CircuitElm> backing = new TreeMap<String, CircuitElm>();
        final TreeMap<String, E04SensorControlModel.DecisionElement> decisions =
            new TreeMap<String, E04SensorControlModel.DecisionElement>();
        final TreeMap<String, ServiceRelayElm> relays = new TreeMap<String, ServiceRelayElm>();
        final TreeMap<String, LimitedDcSupplyElm> sensorSources = new TreeMap<String, LimitedDcSupplyElm>();
        final TreeMap<String, BoundedExternalLoadElm> loads = new TreeMap<String, BoundedExternalLoadElm>();
        final TreeMap<String, InductorElm> dcHarnessInductors = new TreeMap<String, InductorElm>();
        int serial;

        Builder(Rb100Plan plan, RelayOutputGenerator.Assembly assembly) {
            this.plan = plan;
            this.assembly = assembly;
            rail3v3 = plan.variant == Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING ?
                RailRegulationContract.averagedSwitching3V3() : RailRegulationContract.linear3V3();
            decisionRail3v3 = E04SensorControlModel.adaptE02Rail(rail3v3);
            baseDecisionVariant = plan.base.sharedHystereticReference() ?
                E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE :
                E04SensorControlModel.Variant.DIRECT_THRESHOLD;
            baseConfiguration = E04SensorControlModel.Configuration.defaults(decisionRail5);
            directSensorConfiguration = new E04SensorControlModel.Configuration(
                10000, 100000, 10000, 10000, 0, .03, -.03,
                Rb100Plan.SENSOR_LOW_VOLTS, Rb100Plan.SENSOR_HIGH_VOLTS);
            monitorConfiguration = new E04SensorControlModel.Configuration(
                10000, 100000, Rb100Plan.MONITOR_REFERENCE_HIGH_OHMS,
                Rb100Plan.MONITOR_REFERENCE_LOW_OHMS, 0, .03, -.03, 0, 3.3);
        }

        int nextX() { return 12000 + serial++ * 512; }

        void construct(Rb100Plan.Part part) {
            if (part.kind == Rb100Plan.Kind.POWER)
                throw new IllegalArgumentException("RB100 power declarations belong to the RB56 low-level adapter");
            if (part.kind == Rb100Plan.Kind.SOURCE) {
                constructSource(part);
                return;
            }
            if (part.kind == Rb100Plan.Kind.OUTPUT_HEADER) {
                constructOutput(part);
                return;
            }

            CircuitElm element;
            int[] posts;
            PhysicalSpecification specification;
            String label = part.type + " " + part.id;
            switch (part.kind) {
            case REGULATOR:
                RailRegulationContract contract = "U1".equals(part.id) ? rail5 : rail3v3;
                AbstractRailRegulatorElm regulator = contract.isAveragedSwitching() ?
                    new AveragedSwitchingRegulatorElm(nextX(), 400, contract) :
                    new LinearRegulatorElm(nextX(), 400, contract);
                regulator.drag(regulator.x + 96, 400);
                element = regulator;
                posts = new int[] {0, 1, 2, 3};
                specification = new BasicPhysicalSpecification(regulatorSpecificationId(contract));
                break;
            case DECISION:
                E04SensorControlModel.DecisionElement decision = createDecision(part);
                decisions.put(part.channel, decision);
                element = decision;
                posts = new int[] {0, 1, 2, 3, 4};
                specification = new BasicPhysicalSpecification(decision.declarationIdentity());
                break;
            case DRIVER:
                RelayDriverProvider driver = plan.driver(part.channel);
                if (!driver.getId().equals(part.type) ||
                        !driver.getPackage().isEquivalentTo(part.physicalPackage))
                    throw new IllegalArgumentException("RB100 driver disagrees with plan: " + part.id);
                element = driver.create(nextX(), 400);
                posts = driver.getPosts();
                specification = driver.getSpecification(part.id);
                label = "Low-side " + driver.getId() + " driver";
                break;
            case RELAY:
                RelaySpecification relaySpec = new RelaySpecification(part.value);
                ServiceRelayElm relay = relaySpec.create(nextX(), 400);
                relays.put(part.channel, relay);
                element = relay;
                posts = RelaySpecification.POSTS;
                specification = relaySpec;
                label = relaySpec.label();
                break;
            case RESISTOR:
                ResistorElm resistor = end(new ResistorElm(nextX(), 400), 80);
                resistor.setResistance(part.value);
                element = resistor;
                posts = new int[] {0, 1};
                specification = new ResistorNameplate(part.id, part.value, 5, .25);
                break;
            case CAPACITOR:
                CapacitorElm capacitor = end(new CapacitorElm(nextX(), 400), 80);
                capacitor.setCapacitance(part.value);
                capacitor.flags |= CapacitorElm.FLAG_BACK_EULER;
                capacitor.initialVoltage = 0;
                capacitor.reset();
                CapacitorSpecification capacitorSpec = capacitorSpecification(part);
                if (capacitorSpec.getEsrOhms() > 0) {
                    addCapacitorWithEsr(part, capacitor, capacitorSpec);
                    return;
                }
                element = capacitor;
                posts = new int[] {0, 1};
                specification = capacitorSpec;
                break;
            case DIODE:
                DiodeElm diode = end(new DiodeElm(nextX(), 400), 80);
                diode.modelName = "1N4148";
                diode.setup();
                element = diode;
                posts = new int[] {0, 1};
                specification = new DiodeNameplate(part.id, "1N4148 diode", "1N4148");
                break;
            default:
                throw new IllegalArgumentException("Unsupported RB100 package kind: " + part.id);
            }
            addPart(part, element, posts, specification, label);
        }

        private E04SensorControlModel.DecisionElement createDecision(Rb100Plan.Part part) {
            if ("UMON".equals(part.id))
                return new E04SensorControlModel.DecisionElement(nextX(), 400, decisionRail3v3,
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD, monitorConfiguration);
            if ("U2A".equals(part.id) || "U2B".equals(part.id))
                return new E04SensorControlModel.DecisionElement(nextX(), 400, decisionRail5,
                    baseDecisionVariant, baseConfiguration);
            if ("U2C".equals(part.id) || "U2D".equals(part.id))
                return new E04SensorControlModel.DecisionElement(nextX(), 400, decisionRail5,
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD, directSensorConfiguration);
            throw new IllegalArgumentException("Unknown RB100 decision element: " + part.id);
        }

        private void constructSource(Rb100Plan.Part part) {
            if (part.inputId == null)
                throw new IllegalArgumentException("RB100 external source has no declared input: " + part.id);
            String[] nets = part.netIds();
            LimitedDcSupplyElm source = assembly.source(part.id, part.inputId,
                part.value, nets[0], nets[1], false);
            if (part.channel == null) {
                if (!"JLOAD".equals(part.id) || !"LOAD12".equals(part.inputId))
                    throw new IllegalArgumentException("Unknown RB100 non-sensor source: " + part.id);
                source.configure(Rb100Plan.LOAD12_VOLTS, Rb100Plan.LOAD12_MAX_AMPS);
            } else {
                if (!("A".equals(part.channel) || "B".equals(part.channel) ||
                        "C".equals(part.channel) || "D".equals(part.channel)) ||
                        !("SENSOR_" + part.channel).equals(part.inputId))
                    throw new IllegalArgumentException("Unknown RB100 sensor source: " + part.id);
                source.configure(Rb100Plan.SENSOR_LOW_VOLTS, Rb100Plan.SENSOR_MAX_AMPS);
                sensorSources.put(part.channel, source);
            }
            backing.put(part.id, source);
        }

        private void constructOutput(Rb100Plan.Part part) {
            if (part.channel == null || part.value != Rb100Plan.EXTERNAL_LOAD_OHMS)
                throw new IllegalArgumentException("RB100 output header has an invalid external load: " + part.id);
            if ("E".equals(part.channel) || "F".equals(part.channel)) {
                constructDcHarness(part);
                return;
            }
            if (!("A".equals(part.channel) || "B".equals(part.channel) ||
                    "C".equals(part.channel) || "D".equals(part.channel)))
                throw new IllegalArgumentException("Unknown RB100 output channel: " + part.channel);
            int x = nextX();
            BoundedExternalLoadElm load = end(new BoundedExternalLoadElm(x, 400, part.value), 80);
            loads.put(part.channel, load);
            addPart(part, load, new int[] {0, 1},
                new BasicPhysicalSpecification("RB100_EXTERNAL_LOAD_180OHM@1"),
                "External load / 180 Ohm");
            assembly.connections.declareConnectorHarness(part.id, load, 0, load, 1);
        }

        private void constructDcHarness(Rb100Plan.Part part) {
            String[] nets = part.netIds();
            String[] terminals = part.terminalIds();
            int x = nextX();
            BoundedExternalLoadElm load = end(
                new BoundedExternalLoadElm(x, 400, part.value), 80);
            InductorElm inductor = end(new InductorElm(x + 256, 400), 80);
            inductor.setInductance(Rb100Plan.EXTERNAL_LOAD_HENRIES);
            loads.put(part.channel, load);
            dcHarnessInductors.put(part.channel, inductor);
            assembly.elements.add(load);
            assembly.elements.add(inductor);
            // This uses Assembly.registerExistingPart for the manifest header,
            // with board copper and detachable leads at the two chain ends;
            // declareConnectorHarness records those same external endpoints.
            // The extra harness elements are not inventory rows. CircuitJS sees
            // a real LOAD12 -> load -> 10 mH -> OUT_E/F series path. LOAD12 is
            // the shared positive input; the MOSFET switches the low side back
            // through MON_SENSE/RMON to CTRL_RETURN and JLOAD's common return.
            // The bounded resistor fails above 24 V or 6 W; InductorElm is an ideal 10 mH
            // element without winding resistance, so this is a limited DC
            // stimulus model, not a production harness or thermal rating.
            assembly.wire(load.getPost(1), inductor.getPost(0));

            Vector<CircuitElm> auxiliary = new Vector<CircuitElm>();
            auxiliary.add(inductor);
            CircuitPostMeasurementEndpoint[] boardEnds = new CircuitPostMeasurementEndpoint[2];
            CircuitPostMeasurementEndpoint[] componentEnds = new CircuitPostMeasurementEndpoint[] {
                new CircuitPostMeasurementEndpoint(load, 0),
                new CircuitPostMeasurementEndpoint(inductor, 1)
            };
            WireElm[] leads = new WireElm[2];
            for (int index = 0; index < 2; index++) {
                Point net = assembly.nets.get(nets[index]);
                if (net == null) throw new IllegalArgumentException("RB100 output has no net: " + nets[index]);
                Point pad = new Point(4800 + assembly.padSerial++ * 64, 128);
                WireElm copper = assembly.wire(net, pad);
                leads[index] = assembly.wire(pad,
                    componentEnds[index].getElement().getPost(componentEnds[index].getPostIndex()));
                boardEnds[index] = new CircuitPostMeasurementEndpoint(copper, 1);
            }
            assembly.registerExistingPart(part.id, part.type, part.physicalPackage,
                terminals, nets, load, auxiliary, boardEnds, componentEnds, leads);
            backing.put(part.id, load);
            assembly.specifications.addPhysicalDefinition(part.id,
                new BasicPhysicalSpecification(
                    "RB100_OUTPUT_HEADER_2PIN@1+LOAD12_LOW_SIDE_SHARED_RETURN@4+EXTERNAL_RL_HARNESS_180OHM_10MH@1"),
                new PhysicalNameplate(part.id,
                    "12 V DC low-side output / shared LOAD12 positive",
                    "External harness", "180 Ohm resistor and 10 mH series inductor"),
                part.physicalPackage);
            assembly.connections.declareConnectorHarness(part.id, load, 0, inductor, 1);
        }

        private void addPart(Rb100Plan.Part part, CircuitElm element, int[] posts,
                PhysicalSpecification specification, String label) {
            assembly.part(part.id, part.type, part.physicalPackage,
                part.terminalIds(), part.netIds(), posts, element,
                part.physicalPackage.getTerminalCount() == 2);
            backing.put(part.id, element);
            assembly.specifications.addPhysicalDefinition(part.id, specification,
                new PhysicalNameplate(part.id, label), part.physicalPackage);
        }

        private void addCapacitorWithEsr(Rb100Plan.Part part, CapacitorElm capacitor,
                CapacitorSpecification specification) {
            if (!"CIN33".equals(part.id) ||
                    specification.getEsrOhms() != Rb100Plan.CIN33_ESR_OHMS ||
                    !specification.hasExplicitModelRecipe())
                throw new IllegalArgumentException("Only the declared CIN33 recipe owns a series ESR");

            // ESR post 1 meets the capacitor's post 0 at a private solver point.
            // The package's positive terminal remains ESR post 0; its negative
            // terminal remains capacitor post 1. Neither logical net owns the
            // internal junction.
            ResistorElm esr = end(new ResistorElm(capacitor.x - 80, capacitor.y), 80);
            esr.setResistance(specification.getEsrOhms());
            assembly.elements.add(capacitor);
            assembly.elements.add(esr);

            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            CircuitPostMeasurementEndpoint[] boardEnds = new CircuitPostMeasurementEndpoint[2];
            CircuitPostMeasurementEndpoint[] componentEnds = new CircuitPostMeasurementEndpoint[] {
                new CircuitPostMeasurementEndpoint(esr, 0),
                new CircuitPostMeasurementEndpoint(capacitor, 1)
            };
            WireElm[] leads = new WireElm[2];
            for (int index = 0; index < 2; index++) {
                Point net = assembly.nets.get(nets[index]);
                if (net == null)
                    throw new IllegalArgumentException("RB100 CIN33 has no declared net: " + nets[index]);
                Point pad = new Point(4800 + assembly.padSerial++ * 64, 128);
                WireElm copper = assembly.wire(net, pad);
                leads[index] = assembly.wire(pad,
                    componentEnds[index].getElement().getPost(componentEnds[index].getPostIndex()));
                boardEnds[index] = new CircuitPostMeasurementEndpoint(copper, 1);
            }

            Vector<CircuitElm> auxiliary = new Vector<CircuitElm>();
            auxiliary.add(esr);
            assembly.registerExistingPart(part.id, part.type, part.physicalPackage,
                terminals, nets, capacitor, auxiliary, boardEnds, componentEnds, leads);
            backing.put(part.id, capacitor);
            assembly.specifications.addPhysicalDefinition(part.id, specification,
                new PhysicalNameplate(part.id, "22 uF / 25 V electrolytic capacitor"),
                part.physicalPackage);
        }
    }

    private static CapacitorSpecification capacitorSpecification(Rb100Plan.Part part) {
        String recipe;
        String marking;
        double esr = 0;
        if (part.value == 1e-6 &&
                part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR)) {
            recipe = "CAP_RB100_1UF_25V_CERAMIC_BE0";
            marking = "1 uF / 25 V ceramic capacitor";
        } else if (part.value == 100e-9 &&
                part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR)) {
            recipe = "CAP_RB100_100NF_25V_CERAMIC_BE0";
            marking = "100 nF / 25 V ceramic capacitor";
        } else if ("CIN33".equals(part.id) && part.value == 22e-6 &&
                part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR)) {
            recipe = "CAP_RB100_CIN33_22UF_25V_ELECTROLYTIC_BE0_ESR1";
            marking = "22 uF / 25 V electrolytic capacitor";
            esr = Rb100Plan.CIN33_ESR_OHMS;
        } else if (part.value == 22e-6 &&
                part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR)) {
            recipe = "CAP_RB100_22UF_25V_ELECTROLYTIC_BE0";
            marking = "22 uF / 25 V electrolytic capacitor";
        } else {
            throw new IllegalArgumentException("Unsupported RB100 capacitor recipe: " + part.id);
        }
        return new CapacitorSpecification(recipe, part.value, 10, 25,
            part.physicalPackage, new CapacitorNameplate("Capacitor", marking),
            esr, true, 0);
    }

    private static String regulatorSpecificationId(RailRegulationContract contract) {
        if (contract.isAveragedSwitching()) return "RB100_E02_AVERAGED_SWITCHING_3V3@3";
        return contract.getNominalOutputVolts() == 5.0 ?
            "RB100_E02_LINEAR_5V@3" : "RB100_E02_LINEAR_3V3@3";
    }

    private static <T extends CircuitElm> T end(T element, int width) {
        element.x2 = element.x + width;
        element.y2 = element.y;
        element.setPoints();
        return element;
    }
}
