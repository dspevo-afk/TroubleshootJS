package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Provider-local Q30 electrical construction pilot.  This is deliberately not
 * registered for player admission: physical service, diagnostic hypotheses and
 * the enlarged production envelope still need qualification.
 */
final class Rb30Generator {
    static final class Candidate {
        final Rb30Plan plan;
        final RelayOutputGenerator.Assembly assembly;
        final PhysicalBoardRuntime runtime;
        final LinearRegulatorElm regulator;
        final E04SensorControlModel.DecisionElement decisionA, decisionB;
        final ServiceRelayElm relayA, relayB;
        final Rb30RelayService relayServiceA, relayServiceB;
        final Rb30DecisionService serviceA, serviceB;
        final Vector<GeneratedFaultCandidate> faultCandidates;
        final GeneratedFaultBinding selectedFault;
        final Vector<CircuitElm> internalSupport;
        final Vector<WireElm> netInterconnect;
        final Map<String, CircuitElm> backing;

        Candidate(Rb30Plan plan, RelayOutputGenerator.Assembly assembly,
                PhysicalBoardRuntime runtime, LinearRegulatorElm regulator,
                E04SensorControlModel.DecisionElement decisionA,
                E04SensorControlModel.DecisionElement decisionB,
                ServiceRelayElm relayA, ServiceRelayElm relayB,
                Rb30RelayService relayServiceA,
                Rb30RelayService relayServiceB,
                Rb30DecisionService serviceA, Rb30DecisionService serviceB,
                Vector<GeneratedFaultCandidate> faultCandidates,
                GeneratedFaultBinding selectedFault,
                Vector<CircuitElm> internalSupport,
                Vector<WireElm> netInterconnect,
                Map<String, CircuitElm> backing) {
            this.plan = plan;
            this.assembly = assembly;
            this.runtime = runtime;
            this.regulator = regulator;
            this.decisionA = decisionA;
            this.decisionB = decisionB;
            this.relayA = relayA;
            this.relayB = relayB;
            this.relayServiceA = relayServiceA;
            this.relayServiceB = relayServiceB;
            this.serviceA = serviceA;
            this.serviceB = serviceB;
            this.faultCandidates = faultCandidates;
            this.selectedFault = selectedFault;
            this.internalSupport = internalSupport;
            this.netInterconnect = netInterconnect;
            this.backing = backing;
        }

        TroubleshootBoard board() { return assembly.board; }
        Vector<CircuitElm> elements() { return assembly.elements; }
    }

    private RelayOutputGenerator.Assembly a;
    private final TreeMap<String, CircuitElm> backing =
        new TreeMap<String, CircuitElm>();
    private final Vector<CircuitElm> internalSupport = new Vector<CircuitElm>();
    private final TreeMap<String, ResistorSecondaryOpenPath> resistorOpenPaths =
        new TreeMap<String, ResistorSecondaryOpenPath>();
    private SwitchElm reverseOpen;
    private int serial;

    Candidate construct(Rb30Plan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing Q30 plan");
        a = new RelayOutputGenerator.Assembly(plan.board());
        for (String net : a.board.getNetIds())
            a.net(net, a.board.getNet(net).getRoutingRole());

        source("J1", "MAIN12", 12, "RAW12", "CTRL_RETURN");
        source("JSA", "SENSOR_A", 5, "A_RAW", "CTRL_RETURN");
        source("JSB", "SENSOR_B", 5, "B_RAW", "CTRL_RETURN");
        source("JLOAD", "LOAD12", 12, "LOAD12", "LOAD_RETURN");
        Point returned = a.nets.get("CTRL_RETURN");
        GroundElm ground = new GroundElm(returned.x, returned.y);
        ground.drag(returned.x, returned.y + 32);
        a.elements.add(ground);

        ProtectionFuseElm fuse = new ProtectionFuseElm(nextX(), 400);
        fuse.drag(fuse.x + 80, 400);
        fuse.i2t = .1875;
        two("F1", fuse, "RAW12", "FUSED12");
        diode("DREV", "FUSED12", "RAIL12");
        reverseOpen = installReverseOpenPath();
        capacitor("C12", 1e-6, "FUSED12", "CTRL_RETURN");
        capacitor("CIN", 2.2e-5, "RAIL12", "CTRL_RETURN");

        RailRegulationContract rail = RailRegulationContract.linear5V();
        LinearRegulatorElm regulator = new LinearRegulatorElm(nextX(), 400, rail);
        regulator.drag(regulator.x + 96, 400);
        multi("U1", regulator, new int[] { 0, 1, 2, 3 },
            "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
        RegulatorPhysicalMapping.mapComponentTerminals(a.board, "U1", regulator);
        resistor("REN", 10000, "RAIL12", "EN5");
        capacitor("C5", 1e-6, "RAIL5", "CTRL_RETURN");

        E04SensorControlModel.RailContract decisionRail =
            E04SensorControlModel.adaptE02Rail(rail);
        E04SensorControlModel.Variant variant = plan.sharedHystereticReference ?
            E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE :
            E04SensorControlModel.Variant.DIRECT_THRESHOLD;
        E04SensorControlModel.Configuration config =
            E04SensorControlModel.Configuration.defaults(decisionRail);
        E04SensorControlModel.DecisionElement decisionA =
            decision("A", variant, decisionRail, config);
        E04SensorControlModel.DecisionElement decisionB =
            decision("B", variant, decisionRail, config);
        resistor("RSA", 10000, "A_RAW", "A_SENSE");
        resistor("RSB", 10000, "B_RAW", "B_SENSE");
        if (plan.sharedHystereticReference) {
            resistor("RREF_H", 10000, "RAIL5", "REF_SHARED");
            resistor("RREF_L", 10000, "REF_SHARED", "CTRL_RETURN");
            resistor("RFB_A", 22000, "A_CMD", "A_SENSE");
            resistor("RFB_B", 22000, "B_CMD", "B_SENSE");
        } else {
            for (String channel : new String[] { "A", "B" }) {
                resistor("RREF_H" + channel, 10000,
                    "RAIL5", channel + "_REF");
                resistor("RREF_L" + channel, 10000,
                    channel + "_REF", "CTRL_RETURN");
            }
        }

        ServiceRelayElm relayA = output("A", plan.driverA());
        ServiceRelayElm relayB = output("B", plan.driverB());
        resistor("RLED", 3300, "RAIL5", "LED_FEED");
        LEDElm led = new LEDElm(nextX(), 400);
        led.drag(led.x + 80, 400);
        led.modelName = "default-led";
        led.setup();
        two("LED1", led, "LED_FEED", "CTRL_RETURN");

        externalLoad("A");
        externalLoad("B");
        a.requireCompleteManifest();
        Vector<GeneratedFaultCandidate> candidates = faults(plan, relayB);
        GeneratedFaultBinding selected = selected(candidates,
            plan.selectedFault);
        GeneratedFaultEngine.clearAll(candidates);
        if (backing.size() != a.board.getComponentIds().size())
            throw new IllegalStateException("Q30 physical backing census incomplete");
        PhysicalBoardRuntime runtime = installPhysicalOwners(decisionA,
            decisionB, decisionRail, variant, config, selected,
            candidates.get(0).getBinding());
        Rb30DecisionService serviceA = decisionService(runtime, "U2A");
        Rb30DecisionService serviceB = decisionService(runtime, "U2B");
        Rb30RelayService relayServiceA = relayService(runtime, "KA");
        Rb30RelayService relayServiceB = relayService(runtime, "KB");
        Candidate candidate = new Candidate(plan, a, runtime, regulator,
            decisionA, decisionB,
            relayA, relayB, relayServiceA, relayServiceB,
            serviceA, serviceB, candidates, selected,
            new Vector<CircuitElm>(internalSupport), netInterconnect(),
            new TreeMap<String, CircuitElm>(backing));
        Rb30TopologyValidator.require(candidate);
        return candidate;
    }

    private void source(String componentId, String inputId, double volts,
            String positive, String negative) {
        LimitedDcSupplyElm source = a.source(componentId, inputId, volts,
            positive, negative, false);
        backing.put(componentId, source);
    }

    private E04SensorControlModel.DecisionElement decision(String channel,
            E04SensorControlModel.Variant variant,
            E04SensorControlModel.RailContract rail,
            E04SensorControlModel.Configuration config) {
        E04SensorControlModel.DecisionElement element =
            new E04SensorControlModel.DecisionElement(nextX(), 400, rail,
                variant, config);
        multi("U2" + channel, element, new int[] { 0, 1, 2, 3, 4 },
            channel + "_SENSE", planReference(channel), "RAIL5",
            channel + "_CMD", "CTRL_RETURN");
        return element;
    }

    private String planReference(String channel) {
        return a.board.getNet("REF_SHARED") != null ? "REF_SHARED" :
            channel + "_REF";
    }

    private ServiceRelayElm output(String channel, RelayDriverProvider driver) {
        resistor("RD" + channel, 1000,
            channel + "_CMD", channel + "_DRIVE");
        resistor("RPD" + channel, 100000,
            channel + "_DRIVE", "CTRL_RETURN");
        CircuitElm transistor = driver.create(nextX(), 400);
        multi("Q" + channel, transistor, driver.getPosts(),
            channel + "_DRIVE", channel + "_COIL_LOW", "CTRL_RETURN");
        diode("D" + channel, channel + "_COIL_LOW", "RAIL5");
        ServiceRelayElm relay = new RelaySpecification(5).create(nextX(), 400);
        multi("K" + channel, relay, RelaySpecification.POSTS,
            "RAIL5", channel + "_COIL_LOW", "LOAD12",
            "NC_" + channel, "OUT_" + channel);
        return relay;
    }

    private void externalLoad(String channel) {
        BoundedExternalLoadElm load = new BoundedExternalLoadElm(nextX(), 400, 180);
        load.drag(load.x + 80, 400);
        load.configure(180);
        String id = "JO" + channel;
        // The header's fixed backing is the external load, as in E03's J4:
        // both header pads remain distinct and the load lives outside the
        // 33 physical board packages.  Its 180 ohms is CircuitJS stamped.
        multi(id, load, new int[] { 0, 1 },
            "OUT_" + channel, "LOAD_RETURN");
    }

    private void resistor(String id, double ohms, String first, String second) {
        ResistorElm resistor = new ResistorElm(nextX(), 400);
        resistor.drag(resistor.x + 80, 400);
        resistor.setResistance(ohms);
        two(id, resistor, first, second);
        if (isServiceResistor(id)) installResistorOpenPath(id, resistor);
    }

    private void installResistorOpenPath(String id, ResistorElm resistor) {
        ResistorSecondaryOpenPath path = ResistorSecondaryOpenPath.create(
            new CircuitPostMeasurementEndpoint(resistor, 1));
        resistorOpenPaths.put(id, path);
        CircuitElm fault = path.getSimulationElement();
        a.elements.add(fault);
        internalSupport.add(fault);
        WireElm link = a.wire(resistor.getPost(1), fault.getPost(0));
        internalSupport.add(link);
        WireElm lead = lead(id, "2");
        lead.x = path.getPublicTerminal().getElement().getPost(1).x;
        lead.y = path.getPublicTerminal().getElement().getPost(1).y;
        lead.setPoints();
        a.connections.completeConstructionEndpoint(id + ".2",
            path.getPublicTerminal());
        a.components.bindAuxiliaryComponentElement(id, fault);
    }

    private void capacitor(String id, double farads, String first, String second) {
        CapacitorElm capacitor = new CapacitorElm(nextX(), 400);
        capacitor.drag(capacitor.x + 80, 400);
        capacitor.setCapacitance(farads);
        two(id, capacitor, first, second);
    }

    private void diode(String id, String anode, String cathode) {
        DiodeElm diode = new DiodeElm(nextX(), 400);
        diode.drag(diode.x + 80, 400);
        diode.modelName = "1N4148";
        diode.setup();
        two(id, diode, anode, cathode);
    }

    private SwitchElm installReverseOpenPath() {
        DiodeElm diode = (DiodeElm) backing.get("DREV");
        Point cathode = diode.getPost(1);
        SwitchElm fault = new SwitchElm(cathode.x, cathode.y);
        fault.drag(cathode.x + 32, cathode.y);
        a.elements.add(fault);
        internalSupport.add(fault);
        WireElm diodeLink = a.wire(cathode, fault.getPost(0));
        internalSupport.add(diodeLink);
        WireElm cathodeLead = lead("DREV", "K");
        cathodeLead.x = fault.getPost(1).x;
        cathodeLead.y = fault.getPost(1).y;
        cathodeLead.setPoints();
        a.connections.completeConstructionEndpoint("DREV.K",
            new CircuitPostMeasurementEndpoint(fault, 1));
        a.components.bindAuxiliaryComponentElement("DREV", fault);
        return fault;
    }

    private Vector<GeneratedFaultCandidate> faults(Rb30Plan plan,
            ServiceRelayElm relayB) {
        Vector<GeneratedFaultCandidate> candidates =
            new Vector<GeneratedFaultCandidate>();
        candidates.add(GeneratedFaultEngine.diodeOpen("DREV_OPEN",
            Rb30Plan.FAMILY_ID, plan.seed, "DREV", reverseOpen));
        for (String id : new String[] { "REN", "RSA", "RDA" }) {
            GeneratedFault fault = new GeneratedFault(
                id.equals("REN") ? "REN_OPEN" :
                    id.equals("RSA") ? "SENSOR_A_OPEN" : "DRIVE_A_OPEN",
                GeneratedFaultType.RESISTOR_OPEN, id,
                Rb30Plan.FAMILY_ID, plan.seed);
            candidates.add(new GeneratedFaultCandidate(new GeneratedFaultBinding(
                fault, new SwitchOpenFaultEffect((SwitchElm)
                    resistorOpenPaths.get(id).getSimulationElement())), true));
        }
        GeneratedFault relayFault = new GeneratedFault("RELAY_B_COIL_OPEN",
            GeneratedFaultType.RELAY_COIL_OPEN, "KB", Rb30Plan.FAMILY_ID,
            plan.seed);
        candidates.add(new GeneratedFaultCandidate(new GeneratedFaultBinding(
            relayFault, new RelayFaultEffect(relayB, true)), true));
        return candidates;
    }

    private GeneratedFaultBinding selected(
            Vector<GeneratedFaultCandidate> candidates, String id) {
        for (GeneratedFaultCandidate candidate : candidates)
            if (candidate.getFault().getId().equals(id))
                return candidate.getBinding();
        throw new IllegalStateException("Q30 selected fault absent: " + id);
    }

    private Vector<WireElm> netInterconnect() {
        Vector<WireElm> wires = new Vector<WireElm>();
        Vector<CircuitElm> attachments = new Vector<CircuitElm>();
        for (GeneratedComponentConnectionBinding binding : a.connections.getAll())
            attachments.add(binding.getConnectionElement());
        for (CircuitElm element : a.elements) {
            if (!(element instanceof WireElm) ||
                    attachments.contains(element) || internalSupport.contains(element))
                continue;
            Point first = element.getPost(0), second = element.getPost(1);
            if (!a.nets.containsValue(first) && !a.nets.containsValue(second))
                throw new IllegalStateException("Q30 unowned interconnect");
            wires.add((WireElm) element);
        }
        return wires;
    }

    private void two(String id, CircuitElm element,
            String first, String second) {
        multi(id, element, new int[] { 0, 1 }, first, second);
    }

    private void multi(String id, CircuitElm element, int[] posts,
            String... nets) {
        BoardComponent part = a.board.getComponent(id);
        if (part == null) throw new IllegalArgumentException("Unknown Q30 part " + id);
        Vector<String> terminalIds = part.getPhysicalPackage().getTerminalIds();
        if (terminalIds.size() != nets.length || posts.length != nets.length)
            throw new IllegalArgumentException("Q30 terminal mismatch " + id);
        a.part(id, part.getType(), part.getPhysicalPackage(),
            terminalIds.toArray(new String[terminalIds.size()]), nets,
            posts, element, isServiceResistor(id) || id.equals("DREV"));
        backing.put(id, element);
    }

    private PhysicalBoardRuntime installPhysicalOwners(
            E04SensorControlModel.DecisionElement decisionA,
            E04SensorControlModel.DecisionElement decisionB,
            E04SensorControlModel.RailContract rail,
            E04SensorControlModel.Variant variant,
            E04SensorControlModel.Configuration config,
            GeneratedFaultBinding selected,
            GeneratedFaultBinding reverseFault) {
        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(a.board);
        for (String id : a.board.getComponentIds()) {
            BoardComponent component = a.board.getComponent(id);
            PhysicalPackage physical = component.getPhysicalPackage();
            if (isServiceResistor(id)) {
                installResistorService(runtime, id, selected);
                continue;
            }
            if (id.equals("DREV")) {
                installDiodeService(runtime, id, reverseFault);
                continue;
            }
            if (id.equals("KA") || id.equals("KB")) {
                installRelayService(runtime, id, selected);
                continue;
            }
            if (id.equals("CIN")) {
                installInputDecoupler(runtime, id);
                continue;
            }
            PhysicalSpecification spec;
            PhysicalNameplate label;
            if (a.specifications.getSpecification(id) != null) {
                spec = a.specifications.getSpecification(id);
                label = a.specifications.getNameplate(id);
            } else if (isResistorComponent(component, physical)) {
                CircuitElm element = backing.get(id);
                if (!(element instanceof ResistorElm))
                    throw new IllegalStateException("Q30 resistor has no resistor backing: " + id);
                ResistorElm resistor = (ResistorElm) element;
                spec = new ResistorNameplate(id, resistor.getResistance(), 5, .25);
                label = new PhysicalNameplate(id, "Q30 resistor " + id,
                    "Markings", "Color bands");
                a.specifications.addPhysicalDefinition(id, spec, label, physical);
            } else if (isDiodeComponent(component, physical)) {
                spec = new DiodeNameplate(id, "Q30 flyback diode " + id,
                    "1N4148");
                label = new PhysicalNameplate(id, "Flyback diode " + id,
                    "Markings", "1N4148");
                a.specifications.addPhysicalDefinition(id, spec, label, physical);
            } else if (isLedComponent(component, physical)) {
                spec = new LedNameplate(id, "Q30 status LED", "default-led",
                    1, 0, 0);
                label = new PhysicalNameplate(id, "Status LED",
                    "Markings", "default-led");
                a.specifications.addPhysicalDefinition(id, spec, label, physical);
            } else if (isCeramicCapacitorComponent(component, physical)) {
                CircuitElm element = backing.get(id);
                if (!(element instanceof CapacitorElm))
                    throw new IllegalStateException("Q30 ceramic capacitor has no capacitor backing: " + id);
                double farads = ((CapacitorElm) element).getCapacitance();
                CapacitorNameplate markings = new CapacitorNameplate(
                    "Ceramic capacitor", "1 uF / 25 V");
                spec = new CapacitorSpecification(id, farads, 10, 25,
                    PhysicalPackages.RADIAL_CERAMIC_CAPACITOR, markings);
                label = markings.forPhysicalPartId(id);
                a.specifications.addPhysicalDefinition(id, spec, label, physical);
            } else {
                spec = new BasicPhysicalSpecification("RB30_" + id);
                label = new PhysicalNameplate(id,
                    component.getType() + " " + id);
                a.specifications.addPhysicalDefinition(id, spec, label, physical);
            }
            PhysicalBoardSlot slot = runtime.createSlot(id);
            if (id.equals("U2A") || id.equals("U2B")) {
                E04SensorControlModel.DecisionElement decision =
                    id.equals("U2A") ? decisionA : decisionB;
                E04DecisionControlPart original = new E04DecisionControlPart(
                    id + "_ORIGINAL", spec, label, decision, provenance(id));
                WireElm[] attachments = new WireElm[5];
                for (int index = 0; index < 5; index++) {
                    String padId = id + "." + physical.getTerminalIds().get(index);
                    CircuitElm lead = a.connections.get(id, padId)
                        .getConnectionElement();
                    if (!(lead instanceof WireElm))
                        throw new IllegalStateException("Q30 decision lead " + padId);
                    attachments[index] = (WireElm) lead;
                }
                E04DecisionControlSlot decisionSlot =
                    new E04DecisionControlSlot(id, spec, original,
                        attachments, slot);
                PhysicalPartInventory<E04DecisionControlPart> inventory =
                    new PhysicalPartInventory<E04DecisionControlPart>(runtime,
                        id + "_REPLACEMENTS", E04DecisionControlPart.class);
                inventory.add(original);
                runtime.registerCapability(new Rb30DecisionService(
                    decisionSlot, inventory, original, rail, variant, config));
            } else {
                slot.install(PhysicalFoundationPartFactory.fromBoardBindings(id,
                    spec, label, physical, a.board.getSimulationBindings(),
                    backing.get(id), provenance(id)));
            }
        }
        return runtime;
    }

    private boolean isServiceResistor(String id) {
        return id.equals("REN") || id.equals("RSA") || id.equals("RDA");
    }

    private boolean isResistorComponent(BoardComponent component,
            PhysicalPackage physical) {
        return component != null && "RESISTOR".equals(component.getType()) &&
            physical != null && physical.isEquivalentTo(PhysicalPackages.AXIAL_RESISTOR);
    }

    private boolean isDiodeComponent(BoardComponent component,
            PhysicalPackage physical) {
        return component != null && "DIODE".equals(component.getType()) &&
            physical != null && physical.isEquivalentTo(PhysicalPackages.AXIAL_DIODE);
    }

    private boolean isLedComponent(BoardComponent component,
            PhysicalPackage physical) {
        return component != null && "LED".equals(component.getType()) &&
            physical != null && physical.isEquivalentTo(PhysicalPackages.THROUGH_HOLE_LED);
    }

    private boolean isCeramicCapacitorComponent(BoardComponent component,
            PhysicalPackage physical) {
        return component != null && "CAPACITOR".equals(component.getType()) &&
            physical != null && physical.isEquivalentTo(
                PhysicalPackages.RADIAL_CERAMIC_CAPACITOR);
    }

    private WireElm lead(String id, String terminal) {
        CircuitElm element = a.connections.get(id, id + "." + terminal)
            .getConnectionElement();
        if (!(element instanceof WireElm))
            throw new IllegalStateException("Q30 missing detachable lead " + id + "." + terminal);
        return (WireElm) element;
    }

    private void installResistorService(PhysicalBoardRuntime runtime,
            String id, GeneratedFaultBinding selected) {
        ResistorElm element = (ResistorElm) backing.get(id);
        double ohms = element.getResistance();
        ResistorNameplate spec = new ResistorNameplate(id, ohms, 5, .25);
        PhysicalNameplate label = new PhysicalNameplate(id,
            "Service resistor " + id, "Markings", "Color bands");
        a.specifications.addPhysicalDefinition(id, spec, label,
            PhysicalPackages.AXIAL_RESISTOR);
        PhysicalResistorPart original = new PhysicalResistorPart(id + "_ORIGINAL",
            spec, spec, label, element,
            id.equals(selected.getFault().getTargetComponentId()) ? selected : null,
            resistorOpenPaths.get(id),
            ResistorPartLocation.INSTALLED, provenance(id));
        PhysicalPartInventory<PhysicalResistorPart> inventory =
            new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                id + "_REPLACEMENTS", PhysicalResistorPart.class);
        inventory.add(original);
        PhysicalBoardSlot slot = runtime.createSlot(id);
        ReplaceableComponentSlot replacement = new ReplaceableComponentSlot(id,
            spec, original, lead(id, "1"), lead(id, "2"), slot);
        runtime.registerCapability(new ReplaceableResistorBoardCapability(
            "RB30_RESISTOR_SERVICE_" + id, replacement, inventory,
            new ResistorReplacementCatalog(), id + " service resistor"));
    }

    private void installDiodeService(PhysicalBoardRuntime runtime, String id,
            GeneratedFaultBinding reverseFault) {
        DiodeElm element = (DiodeElm) backing.get(id);
        DiodeNameplate spec = new DiodeNameplate(id,
            "1N4148 reverse-polarity protection", "1N4148");
        PhysicalNameplate label = new PhysicalNameplate(id,
            "Reverse-polarity diode", "Markings", "1N4148");
        a.specifications.addPhysicalDefinition(id, spec, label,
            PhysicalPackages.AXIAL_DIODE);
        PhysicalDiodePart original = new PhysicalDiodePart(id + "_ORIGINAL",
            spec, spec, label, element, reverseFault, false,
            DiodePartLocation.INSTALLED, provenance(id));
        PhysicalPartInventory<PhysicalDiodePart> inventory =
            new PhysicalPartInventory<PhysicalDiodePart>(runtime,
                id + "_REPLACEMENTS", PhysicalDiodePart.class);
        inventory.add(original);
        PhysicalBoardSlot physical = runtime.createSlot(id);
        DiodeComponentSlot slot = new DiodeComponentSlot(id, spec, original,
            lead(id, "A"), lead(id, "K"), physical);
        runtime.registerCapability(new ReplaceableDiodeBoardCapability(slot,
            inventory, new DiodeReplacementCatalog(),
            "RB30_DIODE_SERVICE_" + id));
    }

    private void installRelayService(PhysicalBoardRuntime runtime, String id,
            GeneratedFaultBinding selected) {
        ServiceRelayElm element = (ServiceRelayElm) backing.get(id);
        RelaySpecification spec = new RelaySpecification(5);
        a.specifications.addPhysicalDefinition(id, spec,
            new PhysicalNameplate(id, spec.label(), "Markings", spec.label()),
            PhysicalPackages.RELAY_SPDT);
        PhysicalRelayPart original = new PhysicalRelayPart(id + "_ORIGINAL",
            spec, element,
            id.equals(selected.getFault().getTargetComponentId()) ? selected : null,
            provenance(id));
        WireElm[] attachments = new WireElm[RelaySpecification.TERMINALS.length];
        for (int index = 0; index < attachments.length; index++)
            attachments[index] = lead(id, RelaySpecification.TERMINALS[index]);
        runtime.registerCapability(new Rb30RelayService(
            runtime.createSlot(id), original, attachments));
    }

    private void installInputDecoupler(PhysicalBoardRuntime runtime,
            String id) {
        CapacitorElm element = (CapacitorElm) backing.get(id);
        CapacitorNameplate markings = new CapacitorNameplate(
            "Input decoupling electrolytic", "22 uF / 25 V");
        CapacitorSpecification spec = new CapacitorSpecification(id, 2.2e-5,
            10, 25, PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            markings);
        PhysicalNameplate label = markings.forPhysicalPartId(id);
        a.specifications.addPhysicalDefinition(id, spec, label,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR);
        runtime.createSlot(id).install(new PhysicalCapacitorPart(id,
            spec, label, element, null, CapacitorPartLocation.INSTALLED,
            provenance(id)));
    }

    private Rb30DecisionService decisionService(PhysicalBoardRuntime runtime,
            String id) {
        for (PhysicalBoardRuntimeCapability capability :
                runtime.getCapabilities())
            if (capability instanceof Rb30DecisionService &&
                    id.equals(((Rb30DecisionService) capability).getComponentId()))
                return (Rb30DecisionService) capability;
        throw new IllegalStateException("Missing Q30 decision service " + id);
    }

    private Rb30RelayService relayService(PhysicalBoardRuntime runtime,
            String id) {
        for (PhysicalBoardRuntimeCapability capability :
                runtime.getCapabilities())
            if (capability instanceof Rb30RelayService &&
                    id.equals(((Rb30RelayService) capability).getComponentId()))
                return (Rb30RelayService) capability;
        throw new IllegalStateException("Missing Q30 relay service " + id);
    }

    private PhysicalPartProvenance provenance(String id) {
        return new PhysicalPartProvenance(
            PhysicalPartProvenance.FIXED_GENERATED, id);
    }

    private int nextX() { return 8000 + serial++ * 256; }
}
