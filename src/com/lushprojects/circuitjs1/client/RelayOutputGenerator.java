package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** One isolated output-channel composition; driver providers own their pin/model differences. */
final class RelayOutputGenerator {
    static final String FAMILY_ID = "RELAY_OUTPUT";
    static final GeneratedFaultType[] FAULTS = { GeneratedFaultType.RELAY_COIL_OPEN,
        GeneratedFaultType.RELAY_CONTACT_OPEN, GeneratedFaultType.BASE_RESISTOR_OPEN };
    GeneratedBoardInstance generate(long seed) { return generateForFaultVerification(seed, null); }

    GeneratedBoardInstance generateForFaultVerification(long seed, GeneratedFaultType forced) {
        return generateResolved(seed, forced, null);
    }

    GeneratedBoardInstance generateResolved(long seed, GeneratedFaultType forced, Rb15Plan plan) {
        if(plan!=null && plan.seed!=seed)throw new IllegalArgumentException("Control-board seed disagrees with resolved plan");
        if(plan!=null && plan.getRoutedLayout()==null)
            plan=plan.withRoutedLayout(new SeededPcbLayoutGenerator().generate(plan.board(),plan.layoutSeed,plan.routingSeed));
        RelayDriverProvider driver = plan == null ? ((seed & 1) == 0 ? new RelayDriverProvider.Bjt() : new RelayDriverProvider.Nmos()) : plan.driver();
        String family = plan == null ? FAMILY_ID : Rb15Plan.FAMILY_ID;
        String loadSupply = plan == null ? "CONTACT_SUPPLY" : "CTRL_SUPPLY";
        String loadReturn = plan == null ? "CONTACT_RETURN" : "CTRL_RETURN";
        Assembly a = new Assembly(plan == null ? new TroubleshootBoard("RELAY_OUTPUT_BOARD") : plan.board());
        a.net("CTRL_SUPPLY", BoardNet.RoutingRole.SUPPLY);
        a.net("CMD", BoardNet.RoutingRole.CONTROL);
        a.net("DRIVE", BoardNet.RoutingRole.CONTROL);
        a.net("COIL_LOW", BoardNet.RoutingRole.HIGH_CURRENT);
        a.net("CTRL_RETURN", BoardNet.RoutingRole.RETURN);
        if (plan == null) a.net("CONTACT_SUPPLY", BoardNet.RoutingRole.SUPPLY);
        a.net("CONTACT_OUT", BoardNet.RoutingRole.HIGH_CURRENT);
        if (plan == null) a.net("CONTACT_RETURN", BoardNet.RoutingRole.RETURN);
        if (plan != null) {
            a.net("RAW_INPUT", BoardNet.RoutingRole.SUPPLY);
            a.net("FUSED_INPUT", BoardNet.RoutingRole.SUPPLY);
            a.net("FILTERED_CMD", BoardNet.RoutingRole.CONTROL);
            a.net("LED_FEED", BoardNet.RoutingRole.SIGNAL);
        }
        a.net("NC", BoardNet.RoutingRole.SIGNAL);
        LimitedDcSupplyElm coilSource = a.source("J1", "COIL_INPUT", plan == null ? 5 : 12, plan == null ? "CTRL_SUPPLY" : "RAW_INPUT", "CTRL_RETURN", false);
        LimitedDcSupplyElm commandSource = a.source("J2", "COMMAND_INPUT", 5, "CMD", "CTRL_RETURN", true);
        LimitedDcSupplyElm loadSource = plan == null ? a.source("J3", "LOAD_INPUT", 12, "CONTACT_SUPPLY", "CONTACT_RETURN", false) : null;
        Rb15Support support = plan == null ? null : new Rb15Support(a, plan);
        // Only the control domain has a true ground. A floating output-domain reference is
        // supplied by CircuitJS's numerical node stabilization, never a second GroundElm.
        Point returnPoint = a.nets.get("CTRL_RETURN");
        GroundElm ground = new GroundElm(returnPoint.x, returnPoint.y); ground.drag(returnPoint.x, returnPoint.y + 32);
        a.elements.add(ground);

        ResistorNameplate driveSpec = new ResistorNameplate("RDRIVE", 1000, 5, .25);
        ResistorElm drive = new ResistorElm(1600, 400); drive.drag(1680, 400); drive.setResistance(1000);
        a.specifications.addPhysicalDefinition("RDRIVE", driveSpec,
            new PhysicalNameplate("RDRIVE", "Resistor markings", "Markings", "Color bands"), PhysicalPackages.AXIAL_RESISTOR);
        WireElm[] driveAttachments = a.part("RDRIVE", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            new String[] { "1", "2" }, new String[] { plan == null ? "CMD" : "FILTERED_CMD", "DRIVE" }, new int[] { 0, 1 }, drive, true);
        ResistorNameplate pullSpec = new ResistorNameplate("RPD", 100000, 5, .25);
        ResistorElm pull = new ResistorElm(2000, 400); pull.drag(2080, 400); pull.setResistance(100000);
        a.specifications.addPhysicalDefinition("RPD", pullSpec,
            new PhysicalNameplate("RPD", "Resistor markings", "Markings", "Color bands"), PhysicalPackages.AXIAL_RESISTOR);
        a.part("RPD", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR, new String[] { "1", "2" },
            new String[] { "DRIVE", "CTRL_RETURN" }, new int[] { 0, 1 }, pull, false);

        CircuitElm q = driver.create(2400, 400);
        a.specifications.addPhysicalDefinition("Q1", driver.getSpecification(),
            new PhysicalNameplate("Q1", "Low-side " + driver.getId() + " driver", "Part", driver.getId()), driver.getPackage());
        a.part("Q1", driver.getId(), driver.getPackage(), driver.getTerminals(),
            new String[] { "DRIVE", "COIL_LOW", "CTRL_RETURN" }, driver.getPosts(), q, false);
        DiodeNameplate diodeSpec = new DiodeNameplate("D1", "1N4148 flyback diode", "1N4148");
        DiodeElm diode = new DiodeElm(2800, 400); diode.drag(2880, 400); diode.modelName = "1N4148"; diode.setup();
        a.specifications.addPhysicalDefinition("D1", diodeSpec,
            new PhysicalNameplate("D1", "1N4148 flyback diode", "Markings", "1N4148"), PhysicalPackages.AXIAL_DIODE);
        a.part("D1", "DIODE", PhysicalPackages.AXIAL_DIODE, new String[] { "A", "K" },
            new String[] { "CTRL_SUPPLY", "COIL_LOW" }, new int[] { 1, 0 }, diode, false);
        RelaySpecification relaySpec = new RelaySpecification(plan == null ? 5 : 12);
        ServiceRelayElm relay = relaySpec.create(3200, 400);
        a.specifications.addPhysicalDefinition("K1", relaySpec,
            new PhysicalNameplate("K1", relaySpec.label(), "Markings", relaySpec.label()), PhysicalPackages.RELAY_SPDT);
        WireElm[] relayAttachments = a.part("K1", "RELAY", PhysicalPackages.RELAY_SPDT,
            RelaySpecification.TERMINALS, new String[] { "CTRL_SUPPLY", "COIL_LOW", loadSupply, "NC", "CONTACT_OUT" },
            RelaySpecification.POSTS, relay, false);

        BoundedExternalLoadElm load = new BoundedExternalLoadElm(3600, 400, 180); load.drag(3680, 400); load.configure(180);
        a.connector("J4", "External load: 180 Ohm / 6 W maximum", "CONTACT_OUT", loadReturn, load, 0, load, 1);
        a.elements.add(load);
        a.wire(a.nets.get("CONTACT_OUT"), load.getPost(0));
        a.wire(a.nets.get(loadReturn), load.getPost(1));

        Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
        for (GeneratedFaultType type : FAULTS) {
            String target = type == GeneratedFaultType.BASE_RESISTOR_OPEN ? "RDRIVE" : "K1";
            GeneratedFaultEffect effect = "RDRIVE".equals(target) ?
                new ResistorIncorrectValueFaultEffect(drive, 1000, 1e9) :
                new RelayFaultEffect(relay, type == GeneratedFaultType.RELAY_COIL_OPEN);
            candidates.add(new GeneratedFaultCandidate(new GeneratedFaultBinding(
                new GeneratedFault(family + "_" + type.name(), type, target, family, seed), effect), true));
        }
        if (forced == null) forced = plan == null ? FAULTS[(int)(((seed / 2) % 3 + 3) % 3)] : plan.fault;
        GeneratedFaultBinding selected = GeneratedFaultEngine.select(forced, candidates).getBinding();
        GeneratedFaultEngine.clearAll(candidates);

        a.requireCompleteManifest();

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(a.board);
        for (String id : (plan == null ? new String[] { "J1", "J2", "J3", "J4" } : new String[] { "J1", "J2", "J4" })) {
            PhysicalBoardSlot slot = runtime.createSlot(id);
            slot.install(PhysicalFoundationPartFactory.fromBoardBindings(id,
                (BasicPhysicalSpecification)a.specifications.getSpecification(id), a.specifications.getNameplate(id),
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, a.board.getSimulationBindings(),
                "J1".equals(id) ? coilSource : "J2".equals(id) ? commandSource : "J3".equals(id) ? loadSource : load,
                new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, id)));
        }
        PhysicalBoardSlot driveSlot = runtime.createSlot("RDRIVE");
        PhysicalResistorPart drivePart = new PhysicalResistorPart("RDRIVE_ORIGINAL", driveSpec, driveSpec,
            new PhysicalNameplate("RDRIVE_ORIGINAL", "Resistor markings", "Markings", "Color bands"), drive,
            "RDRIVE".equals(selected.getFault().getTargetComponentId()) ? selected : null, null, ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "RDRIVE"));
        PhysicalPartInventory<PhysicalResistorPart> resistors = new PhysicalPartInventory<PhysicalResistorPart>(runtime,
            "RDRIVE_REPLACEMENTS", PhysicalResistorPart.class); resistors.add(drivePart);
        ReplaceableComponentSlot driveMutation = new ReplaceableComponentSlot("RDRIVE", driveSpec, drivePart,
            driveAttachments[0], driveAttachments[1], driveSlot);
        runtime.registerCapability(new ReplaceableResistorBoardCapability(driveMutation, resistors, new ResistorReplacementCatalog()));
        runtime.createSlot("RPD").install(new PhysicalResistorPart("RPD_ORIGINAL", pullSpec, pullSpec, pull, null, null,
            ResistorPartLocation.INSTALLED, new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "RPD")));
        PhysicalNameplate qLabel = new PhysicalNameplate("Q1_ORIGINAL", "Low-side " + driver.getId() + " driver", "Part", driver.getId());
        PhysicalBoardSlot qSlot = runtime.createSlot("Q1");
        if (q instanceof NTransistorElm) qSlot.install(new PhysicalNpnPart("Q1_ORIGINAL", (NpnSpecification)driver.getSpecification(),
            qLabel, (NTransistorElm)q, null, NpnPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "Q1")));
        else qSlot.install(new PhysicalNmosPart("Q1_ORIGINAL", (NmosSpecification)driver.getSpecification(), qLabel,
            (NMosfetElm)q, null, NmosPartLocation.INSTALLED, new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "Q1")));
        runtime.createSlot("D1").install(new PhysicalDiodePart("D1_ORIGINAL", diodeSpec, diodeSpec, diode, null, true,
            DiodePartLocation.INSTALLED, new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "D1")));
        PhysicalRelayPart relayPart = new PhysicalRelayPart("K1_ORIGINAL", relaySpec, relay,
            "K1".equals(selected.getFault().getTargetComponentId()) ? selected : null,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "K1"));
        runtime.registerCapability(new ReplaceableRelayCapability(runtime.createSlot("K1"), relayPart, relayAttachments));
        if (support != null) support.install(runtime);
        runtime.registerCapability(new RelayEnergyReadiness(plan == null ? RelayPowerDomains.create() : Rb15Support.powerContract()));
        RelayOutputBehavior behavior = new RelayOutputBehavior(a.command,
            (CircuitPostMeasurementEndpoint)a.board.getSimulationBindings().getEndpoint("J4.1"),
            (CircuitPostMeasurementEndpoint)a.board.getSimulationBindings().getEndpoint("J4.2"), plan, support == null ? null : support.led);
        String topology = plan == null ? "ISOLATED_SPDT_" + driver.getId() : plan.topology();
        GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(family + "_CHALLENGE", family,
            topology, seed, behavior.scenarios(), "Repair verified. The external load switches normally.",
            selected.getFault(), selected, behavior);
        PcbBoardLayout layout = plan == null ? RelayOutputPcbLayoutFactory.create(a.board, seed) : plan.getRoutedLayout().copySealed();
        return new GeneratedBoardInstance(a.board, a.elements, seed, family, topology, plan == null ? "Generated isolated relay output" : "Generated low-voltage control board",
            a.components, a.power, a.connections, behavior, layout, a.specifications, selected,
            new GeneratedComponentOperationalStates(), challenge, behavior, runtime, behavior, false, candidates, null,
            new RelayOutputDiagnosticProvider(seed, plan));
    }

    /** Electrical wiring is distinct from PCB routing; every pad binds its own attachment. */
    static final class Assembly {
        final TroubleshootBoard board;
        private final TreeMap<String,Boolean> constructed = new TreeMap<String,Boolean>();
        private final boolean declared;
        Assembly(TroubleshootBoard board) {
            this.board = board; declared = !board.getComponentIds().isEmpty();
            components = new GeneratedComponentBindings(board);
            connections = new GeneratedComponentConnectionBindings(board);
            power = new GeneratedExternalPowerBindings(board);
        }
        final TreeMap<String,Point> nets = new TreeMap<String,Point>();
        final Vector<CircuitElm> elements = new Vector<CircuitElm>();
        final BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        final GeneratedComponentBindings components;
        final GeneratedComponentConnectionBindings connections;
        final GeneratedExternalPowerBindings power;
        SwitchElm command;
        int padSerial, sourceSerial;
        void net(String id, BoardNet.RoutingRole role) {
            if(declared) {
                if(board.getNet(id)==null || board.getNet(id).getRoutingRole()!=role)
                    throw new IllegalArgumentException("Construction net disagrees with manifest: "+id);
            } else board.addNet(new BoardNet(id, role));
            if(nets.containsKey(id)) throw new IllegalArgumentException("Duplicate construction net");
            nets.put(id, new Point(100, 100 + nets.size() * 100));
        }
        WireElm wire(Point from, Point to) {
            WireElm wire = new WireElm(from.x, from.y); wire.x2=to.x; wire.y2=to.y; wire.setPoints(); elements.add(wire); return wire;
        }
        void connector(String id, String label, String positive, String negative, CircuitElm endpointElement, int p, CircuitElm returnElement, int n) {
            declare(id,"CONNECTOR",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,new String[]{"1","2"},new String[]{positive,negative});
            board.getSimulationBindings().bindPad(id + ".1", new CircuitPostMeasurementEndpoint(endpointElement, p));
            board.getSimulationBindings().bindPad(id + ".2", new CircuitPostMeasurementEndpoint(returnElement, n));
            specifications.addPhysicalDefinition(id, new BasicPhysicalSpecification(id + "_CONNECTOR"),
                new PhysicalNameplate(id, label, "Connection", label), PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        }
        LimitedDcSupplyElm source(String id, String input, double volts, String positive, String negative, boolean withCommand) {
            int x = 400 + sourceSerial++ * 300;
            LimitedDcSupplyElm source = new LimitedDcSupplyElm(x, 1400); source.drag(x, 1200); source.configure(volts, .25);
            SwitchElm isolation = new SwitchElm(source.getPost(1).x, source.getPost(1).y); isolation.drag(x + 64, 1200);
            elements.add(source); elements.add(isolation);
            CircuitElm output = isolation;
            if (withCommand) { command = new SwitchElm(isolation.getPost(1).x, isolation.getPost(1).y); command.drag(x + 128, 1200); elements.add(command); output = command; }
            WireElm positiveLead = wire(output.getPost(1), nets.get(positive));
            WireElm negativeLead = wire(source.getPost(0), nets.get(negative));
            connector(id, volts + " V " + (withCommand ? "command input" : "supply"), positive, negative, positiveLead, 1, negativeLead, 1);
            if(!declared) board.addPowerInput(new ExternalBoardPowerInput(input, id + ".1", id + ".2", positive, negative));
            else {
                ExternalBoardPowerInput expected=board.getPowerInput(input);
                if(expected==null || !expected.getPositivePadId().equals(id+".1") || !expected.getReturnPadId().equals(id+".2"))
                    throw new IllegalArgumentException("Construction source disagrees with manifest");
            }
            specifications.addPowerInputNameplate(new PowerInputNameplate(input, volts));
            Vector<CircuitElm> sources = new Vector<CircuitElm>(); sources.add(source); sources.add(isolation);
            power.bindPowerInput(input, new ExternalPowerSimulationBinding(sources, new SwitchExternalPowerControl(isolation)));
            return source;
        }
        WireElm[] part(String id, String type, PhysicalPackage physicalPackage, String[] terminals,
                String[] netIds, int[] posts, CircuitElm element, boolean reverseSecond) {
            declare(id,type,physicalPackage,terminals,netIds); elements.add(element); components.bindComponent(id, element);
            WireElm[] wires = new WireElm[terminals.length];
            for (int i = 0; i < terminals.length; i++) {
                String pad = id + "." + terminals[i];
                boolean reverse = reverseSecond && i == 1;
                Point padPoint = new Point(4800 + padSerial++ * 64, 128);
                WireElm copper = wire(nets.get(netIds[i]), padPoint);
                WireElm lead = reverse ? wire(element.getPost(posts[i]), padPoint) : wire(padPoint, element.getPost(posts[i]));
                wires[i] = lead;
                CircuitMeasurementEndpoint boardEnd = new CircuitPostMeasurementEndpoint(copper, 1);
                board.getSimulationBindings().bindPad(pad, boardEnd);
                connections.bind(id, pad, boardEnd, new CircuitPostMeasurementEndpoint(element, posts[i]), lead);
            }
            return wires;
        }
        private void declare(String id,String type,PhysicalPackage physical,String[] terminals,String[] netIds) {
            if(constructed.put(id,Boolean.TRUE)!=null) throw new IllegalArgumentException("Duplicate constructed package: "+id);
            if(declared) {
                BoardComponent expected=board.getComponent(id);
                if(expected==null || !expected.getType().equals(type) || !expected.getPhysicalPackage().isEquivalentTo(physical) ||
                        expected.getPadIds().size()!=terminals.length) throw new IllegalArgumentException("Construction package disagrees with manifest: "+id);
                for(int i=0;i<terminals.length;i++) {
                    BoardPad pad=board.getPad(id+"."+terminals[i]);
                    if(pad==null || !pad.getNetId().equals(netIds[i])) throw new IllegalArgumentException("Construction terminal disagrees with manifest: "+id);
                }
            } else {
                board.addComponent(new BoardComponent(id,type,physical));
                for(int i=0;i<terminals.length;i++) board.addPad(new BoardPad(id+"."+terminals[i],id,terminals[i],netIds[i]));
            }
        }
        void requireCompleteManifest() {
            if(constructed.size()!=board.getComponentIds().size() || nets.size()!=board.getNetIds().size())
                throw new IllegalArgumentException("Incomplete construction manifest");
            board.validate();
        }
    }
}
