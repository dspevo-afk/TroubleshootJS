package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Deterministic first transient family: a real RC charge-delay controller. */
final class RcDelayGenerator {
    static final String FAMILY_ID = "RC_DELAY";
    static final String CHARGE_DELAY_VARIANT = "RC_CHARGE_DELAY";

    GeneratedBoardInstance generate(long seed) {
        RcValues values = valuesFor(seed);
        TroubleshootBoard board = createBoard();
        BoardPhysicalSpecifications specs = createSpecifications(values);

        DCVoltageElm supply = new DCVoltageElm(160, 320); supply.drag(160, 160);
        supply.maxVoltage = values.supplyVoltage;
        SwitchElm powerSwitch = new SwitchElm(160, 160); powerSwitch.drag(240, 160);
        // A small real source impedance prevents the healthy C2 decoupler
        // from creating an ideal capacitor/voltage-source loop while leaving
        // the board's VIN rail electrically direct to C2 and R1.
        ResistorElm sourceImpedance = new ResistorElm(240, 160); sourceImpedance.drag(272, 160);
        sourceImpedance.setResistance(1);
        WireElm vinInput = new WireElm(272, 160); vinInput.drag(288, 160);
        WireElm vinToR1 = new WireElm(288, 160); vinToR1.drag(320, 160);
        ResistorElm r1 = new ResistorElm(320, 160); r1.drag(400, 160);
        r1.setResistance(values.r1Ohms);
        WireElm rcOut = new WireElm(400, 160); rcOut.drag(480, 160);
        // R2 is the visible output load and the only intentional C1
        // power-off bleeder.  External power isolation must leave this real
        // path intact so a player can observe stored charge decay.
        ResistorElm r2 = new ResistorElm(480, 160); r2.drag(480, 240);
        r2.setResistance(values.r2Ohms);
        GroundElm ground = new GroundElm(640, 240); ground.drag(640, 272);
        WireElm supplyReturn = new WireElm(160, 320); supplyReturn.drag(640, 320);
        WireElm groundReturn = new WireElm(640, 320); groundReturn.drag(640, 240);
        WireElm r2Ground = new WireElm(480, 240); r2Ground.drag(640, 240);

        // C2 is a genuinely healthy ceramic decoupler directly from VIN to
        // GND.  It is deliberately outside the C1 timing branch.
        CapacitorElm c2 = new CapacitorElm(288, 160); c2.drag(288, 240);
        c2.setCapacitance(values.c2Farads);
        WireElm c2Ground = new WireElm(288, 240); c2Ground.drag(640, 240);

        WireElm c1PositiveAttachment = new WireElm(480, 160);
        // CircuitJS snaps endpoints to its grid.  Keep this junction on that
        // grid so the physical C1 attachment and the open-fault switch share
        // one electrical node.
        c1PositiveAttachment.drag(512, 160);
        SwitchElm c1OpenFaultSwitch = new SwitchElm(512, 160);
        c1OpenFaultSwitch.drag(560, 160);
        CapacitorElm c1 = new CapacitorElm(560, 160); c1.drag(560, 208);
        c1.setCapacitance(values.c1Farads);
        WireElm c1NegativeAttachment = new WireElm(560, 208);
        c1NegativeAttachment.drag(640, 240);
        // The selected C1 short is internal to the original physical part.
        // When that part is lifted or removed, this path stays with its own
        // isolated CapacitorElm rather than bypassing a replacement on the
        // board.
        ResistorElm c1ShortFaultResistor = new ResistorElm(560, 160);
        c1ShortFaultResistor.drag(560, 208);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(supply); elements.add(powerSwitch); elements.add(sourceImpedance); elements.add(vinInput);
        elements.add(vinToR1); elements.add(r1); elements.add(rcOut); elements.add(r2);
        elements.add(ground); elements.add(supplyReturn); elements.add(groundReturn);
        elements.add(r2Ground); elements.add(c2); elements.add(c2Ground);
        elements.add(c1PositiveAttachment);
        elements.add(c1); elements.add(c1OpenFaultSwitch); elements.add(c1NegativeAttachment);
        elements.add(c1ShortFaultResistor);

        GeneratedComponentBindings components = new GeneratedComponentBindings(board);
        components.bindComponent("R1", r1);
        components.bindComponent("R2", r2);
        components.bindComponent("C1", c1);
        components.bindComponent("C2", c2);
        GeneratedComponentOperationalStates operational = new GeneratedComponentOperationalStates();

        Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
        faults.add(GeneratedFaultEngine.capacitorPositiveLeadOpen("RC_C1_OPEN", FAMILY_ID, seed, "C1",
            c1OpenFaultSwitch));
        faults.add(GeneratedFaultEngine.capacitorShuntShort("RC_C1_SHORT", FAMILY_ID, seed,
            "C1", c1ShortFaultResistor, c1OpenFaultSwitch));
        GeneratedFaultEngine.clearAll(faults);
        GeneratedFaultType requiredFault = values.shortFault ? GeneratedFaultType.CAPACITOR_SHORT :
            GeneratedFaultType.CAPACITOR_OPEN;
        GeneratedFaultCandidate selected = GeneratedFaultEngine.select(requiredFault, faults);
        GeneratedFaultBinding faultBinding = selected.getBinding();

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(vinInput, 1));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("J2.1", new CircuitPostMeasurementEndpoint(rcOut, 1));
        bindings.bindPad("J2.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("R1.1", new CircuitPostMeasurementEndpoint(vinToR1, 1));
        bindings.bindPad("R1.2", new CircuitPostMeasurementEndpoint(r1, 1));
        bindings.bindPad("R2.1", new CircuitPostMeasurementEndpoint(rcOut, 1));
        bindings.bindPad("R2.2", new CircuitPostMeasurementEndpoint(r2, 1));
        bindings.bindPad("C1.+", new CircuitPostMeasurementEndpoint(rcOut, 1));
        bindings.bindPad("C1.-", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("C2.1", new CircuitPostMeasurementEndpoint(c2, 0));
        bindings.bindPad("C2.2", new CircuitPostMeasurementEndpoint(c2, 1));

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
        PhysicalBoardSlot j2Slot = runtime.createSlot("J2");
        PhysicalBoardSlot r1Slot = runtime.createSlot("R1");
        PhysicalBoardSlot r2Slot = runtime.createSlot("R2");
        PhysicalBoardSlot c1Slot = runtime.createSlot("C1");
        PhysicalBoardSlot c2Slot = runtime.createSlot("C2");

        CapacitorSpecification c1Specification =
            StandardPhysicalDefinitionProviders.CAPACITOR.require(specs, "C1");
        CapacitorSpecification c2Specification =
            StandardPhysicalDefinitionProviders.CAPACITOR.require(specs, "C2");
        PhysicalCapacitorPart originalC1 = new PhysicalCapacitorPart("C1_ORIGINAL",
            c1Specification, c1Specification.getNameplate().forPhysicalPartId("C1_ORIGINAL"), c1, faultBinding,
            CapacitorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "C1"));
        PhysicalCapacitorPart fixedC2 = new PhysicalCapacitorPart("C2_ORIGINAL",
            c2Specification, c2Specification.getNameplate().forPhysicalPartId("C2_ORIGINAL"), c2, null,
            CapacitorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "C2"));
        PhysicalPartInventory<PhysicalCapacitorPart> c1Inventory =
            new PhysicalPartInventory<PhysicalCapacitorPart>(runtime, "C1_REPLACEMENTS",
                PhysicalCapacitorPart.class);
        c1Inventory.add(originalC1);
        CapacitorComponentSlot c1ComponentSlot = new CapacitorComponentSlot("C1",
            c1Specification, originalC1, c1PositiveAttachment, c1NegativeAttachment, c1Slot);
        ReplaceableCapacitorBoardCapability replaceableCapacitor =
            new ReplaceableCapacitorBoardCapability(c1ComponentSlot, c1Inventory,
                new CapacitorReplacementCatalog());
        runtime.registerCapability(replaceableCapacitor);

        GeneratedComponentConnectionBindings connections =
            new GeneratedComponentConnectionBindings(board);
        connections.bind("C1", "C1.+", bindings.getEndpoint("C1.+"),
            originalC1.getTerminalForBoardPad("C1.+"), c1PositiveAttachment);
        connections.bind("C1", "C1.-", bindings.getEndpoint("C1.-"),
            originalC1.getTerminalForBoardPad("C1.-"), c1NegativeAttachment);

        FixedPhysicalPart<ResistorNameplate> fixedR1 =
            PhysicalFoundationPartFactory.fromBoardBindings("R1",
                StandardPhysicalDefinitionProviders.RESISTOR.require(specs, "R1"),
                specs.getNameplate("R1"), PhysicalPackages.AXIAL_RESISTOR, bindings, r1,
                new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "R1"));
        FixedPhysicalPart<ResistorNameplate> fixedR2 =
            PhysicalFoundationPartFactory.fromBoardBindings("R2",
                StandardPhysicalDefinitionProviders.RESISTOR.require(specs, "R2"),
                specs.getNameplate("R2"), PhysicalPackages.AXIAL_RESISTOR, bindings, r2,
                new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "R2"));
        FixedPhysicalPart<BasicPhysicalSpecification> fixedJ1 = connectorPart("J1", specs,
            bindings, vinInput);
        FixedPhysicalPart<BasicPhysicalSpecification> fixedJ2 = connectorPart("J2", specs,
            bindings, rcOut);
        j1Slot.install(fixedJ1); j2Slot.install(fixedJ2); r1Slot.install(fixedR1);
        r2Slot.install(fixedR2); c2Slot.install(fixedC2);

        GeneratedExternalPowerBindings power = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
        powerElements.add(supply); powerElements.add(powerSwitch);
        power.bindPowerInput("VIN_INPUT", new ExternalPowerSimulationBinding(powerElements,
            new SwitchExternalPowerControl(powerSwitch)));

        RcDelayTemporalBehavior temporal = new RcDelayTemporalBehavior(
            (CircuitPostMeasurementEndpoint) bindings.getEndpoint("J2.1"),
            (CircuitPostMeasurementEndpoint) bindings.getEndpoint("J2.2"), values.supplyVoltage);
        runtime.registerCapability(temporal);
        runtime.registerCapability(new StoredEnergyMeasurementReadinessCapability(
            replaceableCapacitor, fixedC2, bindings));
        GeneratedChallengeBehaviorContract behavior = new GeneratedChallengeBehaviorAdapter(
            new RcDelayGeneratedBoardValidator(), new RcDelayFaultValidator(),
            new RcDelayRepairValidator());
        GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
            GeneratedScenarioLibrary.rcDelay();
        GeneratedFault fault = selected.getFault();
        return new GeneratedBoardInstance(board, elements, seed, FAMILY_ID, CHARGE_DELAY_VARIANT,
            "Generated RC delay controller, seed " + seed, components, power, connections,
            behavior, RcDelayPcbLayoutFactory.create(board, seed), specs, faultBinding, operational,
            new GeneratedChallengeDefinition("RC_DELAY_STARTUP", FAMILY_ID, CHARGE_DELAY_VARIANT,
                seed, scenarios, "Repair verified. The controller delay is operating normally.",
            fault, faultBinding, behavior), new RcDelayFamilyState(temporal), runtime, temporal,
            false, faults);
    }

    private BoardPhysicalSpecifications createSpecifications(RcValues values) {
        BoardPhysicalSpecifications specs = new BoardPhysicalSpecifications();
        specs.addPhysicalDefinition("J1", new BasicPhysicalSpecification("J1_CONNECTOR"),
            new PhysicalNameplate("J1", "Power input connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specs.addPhysicalDefinition("J2", new BasicPhysicalSpecification("J2_CONNECTOR"),
            new PhysicalNameplate("J2", "Delayed output connector"),
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2);
        specs.addPowerInputNameplate(new PowerInputNameplate("VIN_INPUT", values.supplyVoltage));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specs,
            new ResistorNameplate("R1", values.r1Ohms, 5));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specs,
            new ResistorNameplate("R2", values.r2Ohms, 5));
        StandardPhysicalDefinitionProviders.CAPACITOR.add(specs, new CapacitorSpecification("C1",
            values.c1Farads, 20, 16, PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Electrolytic capacitor", "33 uF 16 V")));
        StandardPhysicalDefinitionProviders.CAPACITOR.add(specs, new CapacitorSpecification("C2",
            values.c2Farads, 10, 50, PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            new CapacitorNameplate("Ceramic capacitor", "104")));
        return specs;
    }

    private FixedPhysicalPart<BasicPhysicalSpecification> connectorPart(String id,
            BoardPhysicalSpecifications specs, BoardSimulationBindings bindings,
            CircuitElm backing) {
        return PhysicalFoundationPartFactory.fromBoardBindings(id,
            (BasicPhysicalSpecification) specs.getSpecification(id), specs.getNameplate(id),
            "J2".equals(id) ? PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2 :
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, bindings, backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, id));
    }

    private TroubleshootBoard createBoard() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID);
        board.addNet(new BoardNet("VIN")); board.addNet(new BoardNet("RC_OUT"));
        board.addNet(new BoardNet("GND"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("J2", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2));
        board.addComponent(new BoardComponent("R1", "RESISTOR"));
        board.addComponent(new BoardComponent("R2", "RESISTOR"));
        board.addComponent(new BoardComponent("C1", "CAPACITOR",
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR));
        board.addComponent(new BoardComponent("C2", "CAPACITOR",
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR));
        board.addPad(new BoardPad("J1.1", "J1", "1", "VIN"));
        board.addPad(new BoardPad("J1.2", "J1", "2", "GND"));
        board.addPad(new BoardPad("J2.1", "J2", "1", "RC_OUT"));
        board.addPad(new BoardPad("J2.2", "J2", "2", "GND"));
        board.addPad(new BoardPad("R1.1", "R1", "1", "VIN"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "RC_OUT"));
        board.addPad(new BoardPad("R2.1", "R2", "1", "RC_OUT"));
        board.addPad(new BoardPad("R2.2", "R2", "2", "GND"));
        board.addPad(new BoardPad("C1.+", "C1", "+", "RC_OUT"));
        board.addPad(new BoardPad("C1.-", "C1", "-", "GND"));
        board.addPad(new BoardPad("C2.1", "C2", "1", "VIN"));
        board.addPad(new BoardPad("C2.2", "C2", "2", "GND"));
        board.addPowerInput(new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2",
            "VIN", "GND"));
        board.validate();
        return board;
    }

    private RcValues valuesFor(long seed) {
        long normalized = seed % 4;
        if (normalized < 0) normalized += 4;
        if (normalized == 2)
            return new RcValues(9, 15000, 10000, true);
        if (normalized == 3)
            return new RcValues(12, 15000, 10000, false);
        return new RcValues(5, 12000, 10000, false);
    }

    private static final class RcValues {
        final double supplyVoltage;
        final double r1Ohms;
        final double r2Ohms;
        final double c1Farads = 33e-6;
        final double c2Farads = 100e-9;
        final boolean shortFault;
        RcValues(double supplyVoltage, double r1Ohms, double r2Ohms, boolean shortFault) {
            this.supplyVoltage = supplyVoltage;
            this.r1Ohms = r1Ohms;
            this.r2Ohms = r2Ohms;
            this.shortFault = shortFault;
        }
    }
}
