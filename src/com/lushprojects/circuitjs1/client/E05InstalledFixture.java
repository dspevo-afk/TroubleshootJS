package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;

/** Fixed developer bench using the qualified E05 graph and ordinary installed owners. */
final class E05InstalledFixture {
    static final String FAMILY = "E05_DEVELOPER_BENCH", VARIANT = "LINEAR_TRANSFORMER_BRIDGE";
    static final String INPUT = "AC_INPUT";
    final E05ElectricalFixtures.Fixture electrical = E05ElectricalFixtures.isolatedTransformerBulk();
    final GeneratedBoardInstance instance;
    final PowerDomainRuntimeCapability power;
    private final TroubleshootBoard board = new TroubleshootBoard(FAMILY);
    private final GeneratedComponentBindings components = new GeneratedComponentBindings(board);
    private final BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
    private final PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
    private final PcbBoardLayout layout = new PcbBoardLayout(2630, 1100,
        new Rectangle(30, 30, 2030, 1020), new Rectangle(2130, 80, 450, 950));
    private final Vector<PcbPlacementConstraints.Part> demands = new Vector<PcbPlacementConstraints.Part>();

    E05InstalledFixture() {
        boolean complete = false;
        try {
        // The raw graph's fault switch is not a package on this healthy installed bench.
        SwitchElm privateSwitch = electrical.bridge.openDiode;
        electrical.elements.remove(privateSwitch);
        electrical.elements.add(wire(privateSwitch.getPost(0), privateSwitch.getPost(1)));
        privateSwitch.delete();
        for (String id : new String[] {"PRI_LINE", "PRI_FUSED", "PRI_RETURN", "SEC_A", "SEC_B", "DC_PLUS", "DC_MINUS"})
            board.addNet(new BoardNet(id));
        connector("JAC", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            new String[] {"PRI_LINE", "PRI_RETURN"}, electrical.input.linePole, 1,
            electrical.input.returnPole, 1, 120, 400, "PRIMARY", "120 VAC RMS / 60 Hz input");
        add("F1", PhysicalPackages.AXIAL_FUSE, new String[] {"PRI_LINE", "PRI_FUSED"},
            electrical.fuse, new int[] {0, 1}, 360, 290, "PRIMARY",
            new BasicPhysicalSpecification("E05_SIMULATED_I2T_FUSE"),
            new PhysicalNameplate("F1", "Simulated fuse", "Model", "0.1 ohm / 0.1 A^2 s"));
        add("T1", PhysicalPackages.E05_ISOLATION_TRANSFORMER_4,
            new String[] {"PRI_FUSED", "PRI_RETURN", "SEC_A", "SEC_B"},
            electrical.transformer, new int[] {0, 2, 1, 3}, 700, 350, "PRIMARY",
            new BasicPhysicalSpecification("E05_FIXED_LINEAR_TRANSFORMER"),
            new PhysicalNameplate("T1", "Fixed linear transformer", "Model", "4 H / 0.1 turns ratio / k=0.999"));
        // Planar cycle: outer SEC_A pads join above, outer SEC_B pads below.
        // Inner DC+ (left) and DC- (right) buses enclose the fixed-pose DC parts.
        diode("D1", 0, "SEC_A", "DC_PLUS", 1270, 150);
        diode("D2", 1, "SEC_B", "DC_PLUS", 1270, 840);
        diode("D3", 2, "DC_MINUS", "SEC_A", 1710, 150);
        diode("D4", 3, "DC_MINUS", "SEC_B", 1710, 840);
        CapacitorSpecification capacitor = new CapacitorSpecification("E05_470UF_25V",
            E05ElectricalFixtures.SECONDARY_CAP_FARADS, 20, 25,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Bulk capacitor", "470 uF / 25 V"));
        add("C1", capacitor.getPhysicalPackage(), new String[] {"DC_PLUS", "DC_MINUS"},
            electrical.bulk, new int[] {0, 1}, 1460, 260, "SECONDARY", capacitor,
            capacitor.getNameplate().forPhysicalPartId("C1"));
        resistor("RBLEED", electrical.bleed, E05ElectricalFixtures.SECONDARY_BLEED_OHMS, 1440, 470);
        resistor("RLOAD", electrical.load, E05ElectricalFixtures.SECONDARY_LOAD_OHMS, 1440, 600);
        connector("JOUT", PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            new String[] {"DC_PLUS", "DC_MINUS"}, electrical.bulk, 0, electrical.bulk, 1,
            1470, 690, "SECONDARY", "Rectified DC output");
        board.addPowerInput(new ExternalBoardPowerInput(INPUT, "JAC.1", "JAC.2", "PRI_LINE", "PRI_RETURN"));
        Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 100));
        board.setPlacementConstraints(new PcbPlacementConstraints(demands, barriers, PcbCopperLayer.TOP));
        board.validate();
        try {
            PcbNetRouter.route(layout, board, layout.getBoardOutline(), 5,
                new SeededPcbLayoutGenerator.AttemptObserver() { public void check(int attempt) { } });
        } catch (PcbNetRouter.Rejected failure) {
            throw new IllegalStateException("E05 fixture routing: " + failure.getMessage() +
                "; blockedNet=" + failure.blockedNet + "; " +
                (failure.statistics == null ? "no statistics" : failure.statistics.toCanonical()), failure);
        }
        layout.validateGeometry(board);
        GeneratedExternalPowerBindings external = new GeneratedExternalPowerBindings(board);
        external.bindPowerInput(INPUT, electrical.input.binding);
        specifications.addPowerInputNameplate(PowerInputNameplate.acRms(INPUT,
            E05AcInputModel.RMS_VOLTS, E05AcInputModel.FREQUENCY_HZ));
        power = new PowerDomainRuntimeCapability(domains(), board, external);
        runtime.registerCapability(power);
        runtime.registerCapability(new StoredEnergyMeasurementReadinessCapability(runtime, board.getSimulationBindings()));
        GeneratedChallengeBehaviorContract behavior = new GeneratedChallengeBehaviorContract() {
            public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { verifyLive(owner); }
            public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes,
                    BoardPowerState state) { verifyLive(owner); }
            public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
                    BoardModificationController changes, BoardPowerState state, boolean overlay) {
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            }
            public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
                    BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
        };
        Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
        instance = new GeneratedBoardInstance(board, electrical.elements, 5, FAMILY, VARIANT,
            "E05 developer bench - 120 VAC RMS; fixed linear transformer", components, external,
            new GeneratedComponentConnectionBindings(board), behavior, layout, specifications, null,
            new GeneratedComponentOperationalStates(), null, null, runtime, new BenchLiveBehavior(), true, faults,
            GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(FAMILY, VARIANT, 5, faults));
        complete = true;
        } finally {
            if (!complete) for (CircuitElm element : electrical.elements) element.delete();
        }
    }

    private void diode(String id, int index, String anode, String cathode, int x, int y) {
        DiodeNameplate specification = new DiodeNameplate(id, "Rectifier diode", "default");
        add(id, PhysicalPackages.AXIAL_DIODE, new String[] {anode, cathode}, electrical.bridge.diodes[index],
            new int[] {0, 1}, x, y, "SECONDARY", specification,
            new PhysicalNameplate(id, "Rectifier diode", "Model", "default silicon"));
    }
    private void resistor(String id, ResistorElm element, double ohms, int x, int y) {
        ResistorNameplate specification = new ResistorNameplate(id, ohms, 5, 1);
        add(id, PhysicalPackages.AXIAL_RESISTOR, new String[] {"DC_PLUS", "DC_MINUS"}, element,
            new int[] {0, 1}, x, y, "SECONDARY", specification,
            new PhysicalNameplate(id, specification.getDisplayName()));
    }
    private void connector(String id, PhysicalPackage pkg, String[] nets, CircuitElm first, int firstPost,
            CircuitElm second, int secondPost, int x, int y, String domain, String marking) {
        WireElm a = pin(first.getPost(firstPost)), b = pin(second.getPost(secondPost));
        electrical.elements.add(a); electrical.elements.add(b);
        add(id, pkg, nets, new CircuitElm[] {a, b}, new int[] {0, 0}, x, y, domain,
            new BasicPhysicalSpecification("E05_CONNECTOR"), new PhysicalNameplate(id, marking, "Marking", marking));
    }
    private void add(String id, PhysicalPackage pkg, String[] nets, CircuitElm element, int[] posts,
            int x, int y, String domain, PhysicalSpecification specification, PhysicalNameplate nameplate) {
        CircuitElm[] elements = new CircuitElm[posts.length];
        for (int i = 0; i < posts.length; i++) elements[i] = element;
        add(id, pkg, nets, elements, posts, x, y, domain, specification, nameplate);
    }
    private void add(String id, PhysicalPackage pkg, String[] nets, CircuitElm[] elements, int[] posts,
            int x, int y, String domain, PhysicalSpecification specification, PhysicalNameplate nameplate) {
        BoardComponent component = new BoardComponent(id, pkg.getId(), pkg, id);
        board.addComponent(component);
        Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
        Vector<CircuitElm> backing = new Vector<CircuitElm>();
        Vector<PcbPlacementConstraints.TerminalDomain> terminalDomains = new Vector<PcbPlacementConstraints.TerminalDomain>();
        for (int i = 0; i < pkg.getTerminalCount(); i++) {
            String terminal = pkg.getTerminalIds().get(i), pad = id + "." + terminal;
            board.addPad(new BoardPad(pad, id, terminal, nets[i]));
            CircuitPostMeasurementEndpoint endpoint = new CircuitPostMeasurementEndpoint(elements[i], posts[i]);
            board.getSimulationBindings().bindPad(pad, endpoint);
            terminals.add(new PhysicalPartTerminal(id, terminal, endpoint));
            if (!backing.contains(elements[i])) backing.add(elements[i]);
            if ("T1".equals(id)) terminalDomains.add(new PcbPlacementConstraints.TerminalDomain(terminal,
                i < 2 ? "PRIMARY" : "SECONDARY"));
        }
        components.bindComponent(id, backing.firstElement());
        for (int i = 1; i < backing.size(); i++) components.bindAuxiliaryComponentElement(id, backing.get(i));
        specifications.addPhysicalDefinition(id, specification, nameplate, pkg);
        runtime.createSlot(id).install(new FixedPhysicalPart<PhysicalSpecification>(id, specification,
            nameplate, pkg, terminals, backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, id)));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, x, y);
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        // Reference text occupies each fixed package's clear body margin.
        Rectangle body = footprint.getPlacement().getBodyBounds();
        int labelWidth = id.length() * 7;
        int labelY = "C1".equals(id) ? body.y + body.height + 8 : body.y - 14;
        layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:" + id, id,
            new Rectangle(body.x + (body.width - labelWidth) / 2, labelY, labelWidth, 12),
            10, true, null));
        demands.add(new PcbPlacementConstraints.Part(id, domain, domain, domain,
            PcbPlacementConstraints.Anchor.NONE, 16, terminalDomains));
    }

    private PowerDomainContract domains() {
        Vector<PowerDomainContract.Rail> rails = new Vector<PowerDomainContract.Rail>();
        for (String rail : new String[] {"PRI_LINE", "PRI_FUSED"})
            rails.add(new PowerDomainContract.Rail(rail, "PRI_RETURN", PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED));
        rails.add(new PowerDomainContract.Rail("SEC_A", "SEC_B", PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED));
        rails.add(new PowerDomainContract.Rail("DC_PLUS", "DC_MINUS", PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED));
        return new PowerDomainContract(FAMILY, Arrays.asList(
            new PowerDomainContract.Reference("PRI_RETURN", "PRIMARY", null, false),
            new PowerDomainContract.Reference("SEC_B", "SECONDARY", null, false),
            new PowerDomainContract.Reference("DC_MINUS", "SECONDARY", null, false)), rails,
            Arrays.asList(new PowerDomainContract.Source(INPUT, "PRI_LINE",
                Range.known(-E05AcInputModel.PEAK_VOLTS, E05AcInputModel.PEAK_VOLTS),
                Scalar.unknown(), Scalar.known(E05AcInputModel.SERIES_OHMS),
                Scalar.notApplicable(), Drive.RESISTIVE_SOURCE)),
            Collections.<PowerDomainContract.BackfeedPath>emptyList());
    }

    /** Ordinary live solver pacing for this fixed bench; no customer fault or repair profiles. */
    private final class BenchLiveBehavior implements GeneratedTemporalBehavior, GeneratedLiveTemporalSimulation {
        private static final double LIVE_SECONDS = .005;

        public double getLiveSolverAdvanceSeconds() { return LIVE_SECONDS; }

        public void requireOwnedBy(GeneratedBoardInstance owner) {
            if (owner == null || owner != instance || owner.getBoard() != board ||
                    owner.getPhysicalBoardRuntime() != runtime || owner.getTemporalBehavior() != this ||
                    !owner.getSimulationElements().contains(electrical.transformer) ||
                    !owner.getSimulationElements().contains(electrical.input.source))
                throw new IllegalArgumentException("E05 live solver has a foreign owner");
            String[] windingPads = {"T1.P1", "T1.P2", "T1.S1", "T1.S2"};
            int[] windingPosts = {0, 2, 1, 3};
            for (int i = 0; i < windingPads.length; i++) {
                CircuitPostMeasurementEndpoint endpoint = endpoint(windingPads[i]);
                if (endpoint.getElement() != electrical.transformer || endpoint.getPostIndex() != windingPosts[i])
                    throw new IllegalStateException("E05 live solver winding binding changed");
            }
            for (String pad : new String[] {"JOUT.1", "JOUT.2"}) {
                CircuitPostMeasurementEndpoint endpoint = endpoint(pad);
                if (!owner.getSimulationElements().contains(endpoint.getElement()) || endpoint.getPostIndex() < 0 ||
                        endpoint.getPostIndex() >= endpoint.getElement().getPostCount())
                    throw new IllegalStateException("E05 live solver output binding changed");
            }
        }

        public GeneratedTemporalDependency getDependency(GeneratedBoardInstance owner) {
            requireOwnedBy(owner);
            java.util.Map<String,String> parameters = new java.util.TreeMap<String,String>();
            parameters.put("policy", "DEVELOPER_BENCH_NO_PROFILES");
            parameters.put("source.peak-volts", Double.toString(electrical.input.source.maxVoltage));
            parameters.put("source.frequency-hz", Double.toString(electrical.input.source.frequency));
            parameters.put("source.series-ohms", Double.toString(electrical.input.impedance.resistance));
            parameters.put("transformer.primary-henries", Double.toString(electrical.transformer.inductance));
            parameters.put("transformer.secondary-over-primary-turns", Double.toString(electrical.transformer.ratio));
            parameters.put("transformer.coupling", Double.toString(electrical.transformer.couplingCoef));
            parameters.put("transformer.integration", electrical.transformer.isTrapezoidal() ? "TRAPEZOIDAL" : "BACKWARD_EULER");
            parameters.put("solver.max-step-seconds", Double.toString(E05AcInputModel.MAX_TIME_STEP_SECONDS));
            parameters.put("solver.live-advance-seconds", Double.toString(LIVE_SECONDS));
            return new GeneratedTemporalDependency("E05_FIXED_BENCH_LIVE", 1,
                GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, "JOUT.1", "JOUT.2", parameters);
        }

        public GeneratedObservedBehavior getObservedBehavior() { return null; }
        public int getProfileWorkUnits() { throw noProfiles(); }
        public GeneratedWork<GeneratedRepairStatus> beginProfile(CirSim sim,
                GeneratedBoardInstance owner, Profile profile) { throw noProfiles(); }
        public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance owner) { throw noProfiles(); }
        public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance owner) { throw noProfiles(); }
        public void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance owner,
                BoardModificationController modifications, BoardPowerState powerState) { throw noProfiles(); }
        public GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance owner,
                BoardModificationController modifications, BoardPowerState powerState,
                boolean activeMeasurementOverlay) { throw noProfiles(); }
        private UnsupportedOperationException noProfiles() {
            return new UnsupportedOperationException("E05 fixed developer bench has no customer temporal profiles");
        }
    }

    private void verifyLive(GeneratedBoardInstance owner) {
        if (owner.getBoard() != board || owner.getPhysicalBoardRuntime() != runtime ||
                electrical.input.source.maxVoltage != E05AcInputModel.PEAK_VOLTS ||
                electrical.input.source.frequency != E05AcInputModel.FREQUENCY_HZ ||
                !PowerDomainContract.finite(electrical.bulk.getVoltageDiff()))
            throw new IllegalStateException("E05 installed graph has foreign or nonfinite behavior");
        // These are graph/lifecycle predicates. The independent warmed waveform and
        // discharge expectations belong to the compiled runtime verifier.
    }
    CircuitPostMeasurementEndpoint endpoint(String pad) {
        return (CircuitPostMeasurementEndpoint)instance.getSimulationBindings().getEndpoint(pad);
    }
    private static WireElm pin(Point point) { return wire(point, new Point(point.x + 16, point.y - 16)); }
    private static WireElm wire(Point a, Point b) {
        WireElm element = new WireElm(a.x, a.y); element.x2 = b.x; element.y2 = b.y; element.setPoints(); return element;
    }
}
