package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * E07 timer family: a real CircuitJS 555 (TimerElm) driving an indicator LED.
 * Even seeds build a free-running flasher (astable RA/RB/C1), odd seeds a
 * START-triggered pulse timer (monostable RA/C1, off-board button on J2).
 * Timing comes only from the solved graph; see Timer555TemporalBehavior.
 */
final class Timer555Generator {
    static final String FAMILY_ID = "TIMER_555";
    static final String FREE_RUNNING_VARIANT = "FREE_RUNNING_FLASHER";
    static final String TRIGGERED_VARIANT = "TRIGGERED_PULSE";
    private static final double[] SUPPLY_VOLTAGES = { 5, 9, 12 };
    private static final double[] LED_RESISTORS = { 330, 680, 1000 };
    // Free-running: about 2 Hz healthy; RA at 47 k gives about 0.7 Hz.
    private static final double ASTABLE_RA = 4700, ASTABLE_RA_WRONG = 47000, ASTABLE_RB = 8200;
    // Triggered: about 0.55 s healthy pulse; RA at 47 k gives about 1.7 s.
    private static final double MONO_RA = 15000, MONO_RA_WRONG = 47000, MONO_PULLUP = 10000;
    private static final String[] U1_TERMINALS =
        { "RESET", "VCC", "DIS", "THRES", "TRIG", "GND", "CTRL", "OUT" };
    private static final double C1_FARADS = 33e-6, C2_FARADS = 10e-9;

    GeneratedBoardInstance generate(long seed) { return generate(seed, null); }

    GeneratedBoardInstance generateForFaultVerification(long seed, GeneratedFaultType type) {
        if (type != GeneratedFaultType.RESISTOR_INCORRECT_VALUE &&
                type != GeneratedFaultType.RESISTOR_OPEN &&
                type != GeneratedFaultType.CAPACITOR_SHORT)
            throw new IllegalArgumentException("Unsupported timer diagnostic fault: " + type);
        return generate(seed, type);
    }

    static boolean isFreeRunning(long seed) { return (seed & 1L) == 0; }

    private static int supplyIndex(long seed) {
        long n = (seed >> 1) % SUPPLY_VOLTAGES.length;
        return (int) (n < 0 ? n + SUPPLY_VOLTAGES.length : n);
    }

    private GeneratedBoardInstance generate(long seed, GeneratedFaultType forcedType) {
        boolean astable = isFreeRunning(seed);
        String variant = astable ? FREE_RUNNING_VARIANT : TRIGGERED_VARIANT;
        int index = supplyIndex(seed);
        double supplyVoltage = SUPPLY_VOLTAGES[index];
        double ledResistor = LED_RESISTORS[index];
        // RA (from VCC) is the serviceable timing resistor on both variants. An
        // open RA leaves C1 with no charging path, which is a deterministic symptom.
        String target = "RA";
        double targetOhms = astable ? ASTABLE_RA : MONO_RA;
        double targetWrongOhms = astable ? ASTABLE_RA_WRONG : MONO_RA_WRONG;
        String fixedTimingId = astable ? "RB" : "R4";
        double fixedTimingOhms = astable ? ASTABLE_RB : MONO_PULLUP;

        TroubleshootBoard board = createBoard(astable);
        BoardPhysicalSpecifications specs = createSpecifications(astable, supplyVoltage,
            ledResistor, targetOhms, fixedTimingOhms);
        LedNameplate ledNameplate = StandardPhysicalDefinitionProviders.LED.require(specs, "LED1");

        // Net anchor points, kept clear of every chip post.
        Point vcc = new Point(288, 160), gnd = new Point(1024, 640);
        Point timing = new Point(640, 96), discharge = new Point(480, 96);
        Point trigger = new Point(560, 240), ctrl = new Point(800, 96);
        Point out = new Point(912, 96);

        DCVoltageElm supply = new LimitedDcSupplyElm(160, 320); supply.drag(160, 160);
        supply.maxVoltage = supplyVoltage;
        SwitchElm powerSwitch = new SwitchElm(160, 160); powerSwitch.drag(240, 160);
        ResistorElm sourceImpedance = new ResistorElm(240, 160); sourceImpedance.drag(272, 160);
        sourceImpedance.setResistance(1);
        WireElm vinInput = wire(272, 160, vcc.x, vcc.y);
        GroundElm ground = new GroundElm(gnd.x, gnd.y); ground.drag(gnd.x, gnd.y + 32);
        WireElm supplyReturn = wire(160, 320, gnd.x, gnd.y);

        // U1 is a serviceable chip: each package pin has stationary copper from its
        // net to a pad point and a detachable lead from that pad to the chip post.
        TimerElm timer = Timer555Specification.createTimer(608, 400);
        Point[] u1Nodes = { vcc, vcc, astable ? discharge : timing, timing,
            astable ? timing : trigger, gnd, ctrl, out };
        WireElm[] u1Copper = new WireElm[U1_TERMINALS.length], u1Leads = new WireElm[U1_TERMINALS.length];
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        add(elements, supply, powerSwitch, sourceImpedance, vinInput, ground, supplyReturn, timer);
        for (int i = 0; i < U1_TERMINALS.length; i++) {
            Point post = timer.getPost(Timer555Specification.postForTerminal(i));
            Point pad = new Point(post.x + 3, post.y + 5);
            u1Copper[i] = wire(u1Nodes[i].x, u1Nodes[i].y, pad.x, pad.y);
            u1Leads[i] = wire(pad.x, pad.y, post.x, post.y);
            add(elements, u1Copper[i], u1Leads[i]);
        }
        WireElm disCopper = u1Copper[2], thresCopper = u1Copper[3], outCopper = u1Copper[7];

        // Fixed resistor: RB (astable, DIS to TIMING) or R4 (trigger pull-up from VCC).
        Point fixedFrom = astable ? discharge : vcc, fixedEnd = astable ? timing : trigger;
        ResistorElm fixedTiming = new ResistorElm(320, astable ? 32 : 240);
        fixedTiming.drag(400, astable ? 32 : 240);
        fixedTiming.setResistance(fixedTimingOhms);
        WireElm fixedFeed = wire(fixedFrom.x, fixedFrom.y, 320, astable ? 32 : 240);
        WireElm fixedOut = wire(400, astable ? 32 : 240, fixedEnd.x, fixedEnd.y);
        add(elements, fixedTiming, fixedFeed, fixedOut);

        // Replaceable RA: VCC to DIS (astable) or VCC to TIMING (triggered).
        Point targetTo = astable ? discharge : timing;
        ResistorElm targetResistor = new ResistorElm(512, 16); targetResistor.drag(592, 16);
        targetResistor.setResistance(targetOhms);
        WireElm targetLead1 = wire(vcc.x, vcc.y, 512, 16);
        SwitchElm targetOpenSwitch = new SwitchElm(592, 16); targetOpenSwitch.drag(624, 16);
        WireElm targetLead2 = wire(624, 16, targetTo.x, targetTo.y);
        add(elements, targetResistor, targetLead1, targetOpenSwitch, targetLead2);

        // Replaceable C1 with private open/short fault elements, as on the RC board.
        WireElm c1PositiveAttachment = wire(timing.x, timing.y, 672, 160);
        SwitchElm c1OpenFaultSwitch = new SwitchElm(672, 160); c1OpenFaultSwitch.drag(720, 160);
        CapacitorElm c1 = new CapacitorElm(720, 160); c1.drag(720, 224);
        c1.setCapacitance(C1_FARADS);
        WireElm c1NegativeAttachment = wire(720, 224, gnd.x, gnd.y);
        ResistorElm c1ShortFaultResistor = new ResistorElm(720, 160); c1ShortFaultResistor.drag(720, 224);
        add(elements, c1PositiveAttachment, c1OpenFaultSwitch, c1, c1NegativeAttachment,
            c1ShortFaultResistor);

        // Fixed control-voltage capacitor.
        CapacitorElm c2 = new CapacitorElm(ctrl.x, ctrl.y); c2.drag(ctrl.x, ctrl.y + 80);
        c2.setCapacitance(C2_FARADS);
        WireElm c2Ground = wire(ctrl.x, ctrl.y + 80, gnd.x, gnd.y);
        add(elements, c2, c2Ground);

        // Output: fixed R3 then the replaceable indicator LED.
        ResistorElm r3 = new ResistorElm(out.x, out.y); r3.drag(out.x, out.y + 80);
        r3.setResistance(ledResistor);
        WireElm ledNodeTrace = wire(out.x, out.y + 80, 944, 176);
        WireElm ledAnodeLink = wire(944, 176, 960, 224);
        SwitchElm ledFaultIsolation = new SwitchElm(960, 224); ledFaultIsolation.drag(992, 224);
        LEDElm led = new LEDElm(992, 224); led.drag(1056, 224);
        led.modelName = ledNameplate.getModelName(); led.setup();
        led.colorR = ledNameplate.getRed(); led.colorG = ledNameplate.getGreen();
        led.colorB = ledNameplate.getBlue();
        WireElm ledCathodeLink = wire(1056, 224, gnd.x, gnd.y);
        add(elements, r3, ledNodeTrace, ledAnodeLink, ledFaultIsolation, led, ledCathodeLink);

        // Triggered variant: J2 carries the trigger to an off-board momentary START button.
        WireElm j2Trace = null, startReturn = null;
        SwitchElm startButton = null;
        if (!astable) {
            j2Trace = wire(trigger.x, trigger.y, 560, 304);
            startButton = new SwitchElm(560, 304); startButton.drag(560, 368);
            startButton.toggle(); // released (open)
            startReturn = wire(560, 368, gnd.x, gnd.y);
            add(elements, j2Trace, startButton, startReturn);
        }

        GeneratedComponentBindings components = new GeneratedComponentBindings(board);
        components.bindComponent("U1", timer);
        components.bindComponent(fixedTimingId, fixedTiming);
        components.bindComponent(target, targetResistor);
        components.bindComponent("C1", c1);
        components.bindComponent("C2", c2);
        components.bindComponent("R3", r3);
        components.bindComponent("LED1", led);
        GeneratedComponentOperationalStates operational = new GeneratedComponentOperationalStates();
        operational.bindLed("LED1", led);

        Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
        String prefix = astable ? "TIMER_ASTABLE_" : "TIMER_MONO_";
        faults.add(GeneratedFaultEngine.resistorIncorrectValue(prefix + target + "_INCORRECT_VALUE",
            FAMILY_ID, seed, target, targetResistor, targetOhms, targetWrongOhms));
        faults.add(GeneratedFaultEngine.capacitorShuntShort(prefix + "C1_SHORT", FAMILY_ID, seed,
            "C1", c1ShortFaultResistor, c1OpenFaultSwitch));
        faults.add(GeneratedFaultEngine.resistorOpen(prefix + target + "_OPEN", FAMILY_ID, seed,
            target, targetOpenSwitch));
        GeneratedFaultEngine.clearAll(faults);
        GeneratedFaultCandidate selected = forcedType != null ?
            GeneratedFaultEngine.select(forcedType, faults) :
            GeneratedFaultEngine.select(seed >> 1, faults);
        for (GeneratedFaultCandidate candidate : faults)
            for (CircuitElm privateElement : candidate.getPrivateSimulationElements())
                if (!elements.contains(privateElement)) elements.add(privateElement);
        GeneratedFault fault = selected.getFault();
        GeneratedFaultBinding faultBinding = selected.getBinding();

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(vinInput, 1));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
        for (int i = 0; i < U1_TERMINALS.length; i++)
            bindings.bindPad("U1." + U1_TERMINALS[i], new CircuitPostMeasurementEndpoint(u1Copper[i], 1));
        bindings.bindPad(fixedTimingId + ".1", new CircuitPostMeasurementEndpoint(fixedTiming, 0));
        bindings.bindPad(fixedTimingId + ".2", new CircuitPostMeasurementEndpoint(fixedTiming, 1));
        bindings.bindPad(target + ".1", new CircuitPostMeasurementEndpoint(vinInput, 1));
        bindings.bindPad(target + ".2", astable ? new CircuitPostMeasurementEndpoint(disCopper, 0) :
            new CircuitPostMeasurementEndpoint(thresCopper, 0));
        bindings.bindPad("C1.+", new CircuitPostMeasurementEndpoint(thresCopper, 0));
        bindings.bindPad("C1.-", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("C2.1", new CircuitPostMeasurementEndpoint(c2, 0));
        bindings.bindPad("C2.2", new CircuitPostMeasurementEndpoint(c2, 1));
        bindings.bindPad("R3.1", new CircuitPostMeasurementEndpoint(r3, 0));
        bindings.bindPad("R3.2", new CircuitPostMeasurementEndpoint(r3, 1));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(ledNodeTrace, 1));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(ground, 0));
        if (!astable) {
            bindings.bindPad("J2.1", new CircuitPostMeasurementEndpoint(j2Trace, 0));
            bindings.bindPad("J2.2", new CircuitPostMeasurementEndpoint(ground, 0));
        }

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
        PhysicalBoardSlot j2Slot = astable ? null : runtime.createSlot("J2");
        PhysicalBoardSlot u1Slot = runtime.createSlot("U1");
        PhysicalBoardSlot fixedTimingSlot = runtime.createSlot(fixedTimingId);
        PhysicalBoardSlot targetSlot = runtime.createSlot(target);
        PhysicalBoardSlot c1Slot = runtime.createSlot("C1");
        PhysicalBoardSlot c2Slot = runtime.createSlot("C2");
        PhysicalBoardSlot r3Slot = runtime.createSlot("R3");
        PhysicalBoardSlot ledSlot = runtime.createSlot("LED1");

        // Target resistor: replaceable, carries its own fault binding and open path.
        GeneratedFaultBinding targetFaultBinding = target.equals(fault.getTargetComponentId()) ?
            faultBinding : null;
        CircuitPostMeasurementEndpoint openPathUpstream = null;
        if (targetFaultBinding != null && fault.getType() == GeneratedFaultType.RESISTOR_OPEN)
            openPathUpstream = (CircuitPostMeasurementEndpoint)
                targetFaultBinding.getPublicTerminal(targetResistor, 1);
        if (openPathUpstream == null)
            openPathUpstream = new CircuitPostMeasurementEndpoint(targetOpenSwitch, 1);
        ResistorSecondaryOpenPath openPath = ResistorSecondaryOpenPath.create(openPathUpstream);
        elements.add(openPath.getSimulationElement());
        ResistorNameplate targetSpec = StandardPhysicalDefinitionProviders.RESISTOR.require(specs, target);
        PhysicalResistorPart originalTarget = new PhysicalResistorPart(target + "_ORIGINAL",
            targetSpec, new ResistorNameplate(target + "_ORIGINAL", targetOhms, 5),
            specs.getNameplate(target), targetResistor, targetFaultBinding, openPath,
            ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, target));
        components.bindAuxiliaryComponentElement(target, openPath.getSimulationElement());
        PhysicalPartInventory<PhysicalResistorPart> targetInventory =
            new PhysicalPartInventory<PhysicalResistorPart>(runtime, target + "_REPLACEMENTS",
                PhysicalResistorPart.class);
        targetInventory.add(originalTarget);
        runtime.registerCapability(new ReplaceableResistorBoardCapability(
            new ReplaceableComponentSlot(target, targetSpec, originalTarget, targetLead1,
                targetLead2, targetSlot), targetInventory, new ResistorReplacementCatalog()));

        // C1: replaceable electrolytic.
        CapacitorSpecification c1Spec = StandardPhysicalDefinitionProviders.CAPACITOR.require(specs, "C1");
        GeneratedFaultBinding c1FaultBinding = "C1".equals(fault.getTargetComponentId()) ?
            faultBinding : null;
        PhysicalCapacitorPart originalC1 = new PhysicalCapacitorPart("C1_ORIGINAL", c1Spec,
            c1Spec.getNameplate().forPhysicalPartId("C1_ORIGINAL"), c1, c1FaultBinding,
            CapacitorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "C1"));
        PhysicalPartInventory<PhysicalCapacitorPart> c1Inventory =
            new PhysicalPartInventory<PhysicalCapacitorPart>(runtime, "C1_REPLACEMENTS",
                PhysicalCapacitorPart.class);
        c1Inventory.add(originalC1);
        runtime.registerCapability(new ReplaceableCapacitorBoardCapability(
            new CapacitorComponentSlot("C1", c1Spec, originalC1, c1PositiveAttachment,
                c1NegativeAttachment, c1Slot), c1Inventory, new CapacitorReplacementCatalog()));

        // LED1: replaceable indicator.
        PhysicalLedPart originalLed = new PhysicalLedPart("LED1_ORIGINAL", ledNameplate,
            ledNameplate, specs.getNameplate("LED1"), led, false, LedPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "LED1"), null);
        PhysicalPartInventory<PhysicalLedPart> ledInventory =
            new PhysicalPartInventory<PhysicalLedPart>(runtime, "LED1_REPLACEMENTS",
                PhysicalLedPart.class);
        ledInventory.add(originalLed);
        runtime.registerCapability(new ReplaceableLedBoardCapability(
            new LedComponentSlot("LED1", ledNameplate, originalLed, ledAnodeLink, ledCathodeLink,
                ledSlot), ledInventory, new LedReplacementCatalog()));

        GeneratedComponentConnectionBindings connections = new GeneratedComponentConnectionBindings(board);
        connections.bind(target, target + ".1", bindings.getEndpoint(target + ".1"),
            new CircuitPostMeasurementEndpoint(targetResistor, 0), targetLead1);
        connections.bind(target, target + ".2", bindings.getEndpoint(target + ".2"),
            originalTarget.getPublicTerminal(1), targetLead2);
        connections.bind("C1", "C1.+", bindings.getEndpoint("C1.+"),
            originalC1.getTerminalForBoardPad("C1.+"), c1PositiveAttachment);
        connections.bind("C1", "C1.-", bindings.getEndpoint("C1.-"),
            originalC1.getTerminalForBoardPad("C1.-"), c1NegativeAttachment);
        connections.bind("LED1", "LED1.A", bindings.getEndpoint("LED1.A"),
            originalLed.getTerminalForBoardPad("LED1.A"), ledAnodeLink);
        connections.bind("LED1", "LED1.K", bindings.getEndpoint("LED1.K"),
            originalLed.getTerminalForBoardPad("LED1.K"), ledCathodeLink);
        for (int i = 0; i < U1_TERMINALS.length; i++)
            connections.bind("U1", "U1." + U1_TERMINALS[i], bindings.getEndpoint("U1." + U1_TERMINALS[i]),
                new CircuitPostMeasurementEndpoint(timer, Timer555Specification.postForTerminal(i)), u1Leads[i]);

        // Fixed parts.
        j1Slot.install(fixed("J1", specs, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, bindings, vinInput));
        if (j2Slot != null)
            j2Slot.install(fixed("J2", specs, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, bindings, j2Trace));
        Vector<CircuitElm> u1Backing = new Vector<CircuitElm>(); u1Backing.add(timer);
        u1Slot.install(new PhysicalServicePart("U1_ORIGINAL", Timer555Specification.STANDARD,
            specs.getNameplate("U1"), PhysicalPackages.TIMER_SIP_8, u1Backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "U1")));
        fixedTimingSlot.install(PhysicalFoundationPartFactory.fromBoardBindings(fixedTimingId,
            StandardPhysicalDefinitionProviders.RESISTOR.require(specs, fixedTimingId),
            specs.getNameplate(fixedTimingId), PhysicalPackages.AXIAL_RESISTOR, bindings, fixedTiming,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, fixedTimingId)));
        r3Slot.install(PhysicalFoundationPartFactory.fromBoardBindings("R3",
            StandardPhysicalDefinitionProviders.RESISTOR.require(specs, "R3"),
            specs.getNameplate("R3"), PhysicalPackages.AXIAL_RESISTOR, bindings, r3,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "R3")));
        CapacitorSpecification c2Spec = StandardPhysicalDefinitionProviders.CAPACITOR.require(specs, "C2");
        c2Slot.install(new PhysicalCapacitorPart("C2_ORIGINAL", c2Spec,
            c2Spec.getNameplate().forPhysicalPartId("C2_ORIGINAL"), c2, null,
            CapacitorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "C2")));

        GeneratedExternalPowerBindings power = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
        powerElements.add(supply); powerElements.add(powerSwitch);
        power.bindPowerInput("VIN_INPUT", new ExternalPowerSimulationBinding(powerElements,
            new SwitchExternalPowerControl(powerSwitch)));

        Timer555TemporalBehavior temporal = new Timer555TemporalBehavior(
            astable ? Timer555TemporalBehavior.Mode.FREE_RUNNING :
                Timer555TemporalBehavior.Mode.TRIGGERED,
            new CircuitPostMeasurementEndpoint(outCopper, 1), new CircuitPostMeasurementEndpoint(ground, 0),
            "U1.OUT", "J1.2", supplyVoltage, startButton);
        runtime.registerCapability(temporal);
        runtime.registerCapability(new StoredEnergyMeasurementReadinessCapability(runtime, bindings));
        GeneratedChallengeBehaviorContract behavior = new GeneratedChallengeBehaviorAdapter(
            new Timer555GeneratedBoardValidator(), new Timer555FaultValidator(),
            new Timer555RepairValidator());
        GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios = astable ?
            GeneratedScenarioLibrary.timerFlasher() : GeneratedScenarioLibrary.timerPulse();
        connections.declareConnectorHarness("J1", vinInput, 1, supply, 0);
        // Harness: J2.1 meets the board trigger node; the button's return post meets J2.2.
        if (!astable) connections.declareConnectorHarness("J2", j2Trace, 0, startButton, 1);
        return new GeneratedBoardInstance(board, elements, seed, FAMILY_ID, variant,
            (astable ? "Generated 555 flasher" : "Generated 555 pulse timer") + ", seed " + seed +
                ", " + supplyVoltage + " V",
            components, power, connections, behavior,
            ProceduralPcbLayout.generate(board, seed, FAMILY_ID), specs, faultBinding, operational,
            new GeneratedChallengeDefinition(astable ? "TIMER_FLASHER" : "TIMER_PULSE", FAMILY_ID,
                variant, seed, scenarios,
                astable ? "Repair verified. The indicator flashes at its normal rate." :
                    "Repair verified. START gives a normal-length pulse.",
                fault, faultBinding, behavior),
            new Timer555FamilyState(temporal), runtime, temporal, false, faults, null,
            new Timer555DiagnosticProvider(seed));
    }

    private static FixedPhysicalPart<BasicPhysicalSpecification> fixed(String id,
            BoardPhysicalSpecifications specs, PhysicalPackage physicalPackage,
            BoardSimulationBindings bindings, CircuitElm backing) {
        return PhysicalFoundationPartFactory.fromBoardBindings(id,
            (BasicPhysicalSpecification) specs.getSpecification(id), specs.getNameplate(id),
            physicalPackage, bindings, backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, id));
    }

    private static WireElm wire(int x1, int y1, int x2, int y2) {
        // Exact endpoints: CircuitJS joins posts by coordinate, so no grid snapping here.
        WireElm wire = new WireElm(x1, y1);
        wire.x2 = x2; wire.y2 = y2;
        wire.setPoints();
        return wire;
    }

    private static void add(Vector<CircuitElm> elements, CircuitElm... values) {
        for (CircuitElm value : values) elements.add(value);
    }

    private TroubleshootBoard createBoard(boolean astable) {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID);
        board.setSilkscreenTitle(astable ? "TSJ 555 FLASHER" : "TSJ 555 PULSE");
        for (String net : astable ?
                new String[] { "VCC", "GND", "DISCHARGE", "TIMING", "CTRL", "OUT", "LED_A" } :
                new String[] { "VCC", "GND", "TIMING", "TRIGGER", "CTRL", "OUT", "LED_A" })
            board.addNet(new BoardNet(net));
        board.addComponent(new BoardComponent("J1", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        if (!astable)
            board.addComponent(new BoardComponent("J2", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "J2 START")); // silkscreen names the off-board button input
        board.addComponent(new BoardComponent("U1", "TIMER", PhysicalPackages.TIMER_SIP_8));
        board.addComponent(new BoardComponent("RA", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent(astable ? "RB" : "R4", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("C1", "CAPACITOR", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR));
        board.addComponent(new BoardComponent("C2", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR));
        board.addComponent(new BoardComponent("R3", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED));
        pad(board, "J1.1", "VCC"); pad(board, "J1.2", "GND");
        if (!astable) { pad(board, "J2.1", "TRIGGER"); pad(board, "J2.2", "GND"); }
        pad(board, "U1.RESET", "VCC"); pad(board, "U1.VCC", "VCC");
        pad(board, "U1.DIS", astable ? "DISCHARGE" : "TIMING");
        pad(board, "U1.THRES", "TIMING");
        pad(board, "U1.TRIG", astable ? "TIMING" : "TRIGGER");
        pad(board, "U1.GND", "GND"); pad(board, "U1.CTRL", "CTRL"); pad(board, "U1.OUT", "OUT");
        pad(board, "RA.1", "VCC"); pad(board, "RA.2", astable ? "DISCHARGE" : "TIMING");
        if (astable) { pad(board, "RB.1", "DISCHARGE"); pad(board, "RB.2", "TIMING"); }
        else { pad(board, "R4.1", "VCC"); pad(board, "R4.2", "TRIGGER"); }
        pad(board, "C1.+", "TIMING"); pad(board, "C1.-", "GND");
        pad(board, "C2.1", "CTRL"); pad(board, "C2.2", "GND");
        pad(board, "R3.1", "OUT"); pad(board, "R3.2", "LED_A");
        pad(board, "LED1.A", "LED_A"); pad(board, "LED1.K", "GND");
        board.addPowerInput(new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2", "VCC", "GND"));
        board.validate();
        return board;
    }

    private static void pad(TroubleshootBoard board, String id, String net) {
        int dot = id.indexOf('.');
        board.addPad(new BoardPad(id, id.substring(0, dot), id.substring(dot + 1), net));
    }

    private BoardPhysicalSpecifications createSpecifications(boolean astable, double supplyVoltage,
            double ledResistor, double targetOhms, double fixedTimingOhms) {
        BoardPhysicalSpecifications specs = new BoardPhysicalSpecifications();
        specs.addPhysicalDefinition("J1", new BasicPhysicalSpecification("J1_CONNECTOR"),
            new PhysicalNameplate("J1", "Power input connector"), PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        if (!astable)
            specs.addPhysicalDefinition("J2", new BasicPhysicalSpecification("J2_CONNECTOR"),
                new PhysicalNameplate("J2", "START button connector"),
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specs.addPhysicalDefinition("U1", Timer555Specification.STANDARD,
            new PhysicalNameplate("U1", "555 timer IC"), PhysicalPackages.TIMER_SIP_8);
        specs.addPowerInputNameplate(new PowerInputNameplate("VIN_INPUT", supplyVoltage));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specs,
            new ResistorNameplate(astable ? "RB" : "R4", fixedTimingOhms, 5));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specs,
            new ResistorNameplate("RA", targetOhms, 5));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specs, new ResistorNameplate("R3", ledResistor, 5));
        StandardPhysicalDefinitionProviders.CAPACITOR.add(specs, new CapacitorSpecification("C1",
            C1_FARADS, 20, 16, PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Electrolytic capacitor", "33 uF 16 V")));
        StandardPhysicalDefinitionProviders.CAPACITOR.add(specs, new CapacitorSpecification("C2",
            C2_FARADS, 10, 50, PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            new CapacitorNameplate("Ceramic capacitor", "103")));
        StandardPhysicalDefinitionProviders.LED.add(specs,
            new LedNameplate("LED1", "Generic red LED", "default-led", 1, 0, 0));
        return specs;
    }
}
