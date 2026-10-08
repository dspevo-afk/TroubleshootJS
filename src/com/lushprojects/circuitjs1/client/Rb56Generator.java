package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** One private RB56 graph; publication still requires physical and diagnostic admission. */
final class Rb56Generator {
    private static final double HIGH_RESISTANCE_OHMS = 1e9;

    private static final class FaultDescriptor {
        final String id, owner;
        final GeneratedFaultType type;
        final double healthyValue, effectiveValue;
        FaultDescriptor(String id, String owner, GeneratedFaultType type,
                double healthyValue, double effectiveValue) {
            this.id = id; this.owner = owner; this.type = type;
            this.healthyValue = healthyValue; this.effectiveValue = effectiveValue;
        }
        GeneratedFault fault(Rb56Plan plan) {
            return new GeneratedFault(id, type, owner, Rb56Plan.FAMILY_ID, plan.seed,
                healthyValue, effectiveValue);
        }
    }

    private static Vector<FaultDescriptor> faultDescriptors(Rb56Plan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing RB56 fault plan");
        Vector<FaultDescriptor> result = new Vector<FaultDescriptor>();
        String[] owners = {"RENAC", "REN", "RSA", "RDA"};
        String[] ids = {"RENAC_HIGH_RESISTANCE", "REN_HIGH_RESISTANCE",
            "SENSOR_A_HIGH_RESISTANCE", "DRIVE_A_HIGH_RESISTANCE"};
        for (int i = 0; i < owners.length; i++) {
            Rb56Plan.Part part = plan.part(owners[i]);
            if (part == null || !"RESISTOR".equals(part.type))
                throw new IllegalStateException("Missing RB56 resistor fault owner: " + owners[i]);
            result.add(new FaultDescriptor(ids[i], owners[i],
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE, part.value, HIGH_RESISTANCE_OHMS));
        }
        String channel = plan.channelCount == 1 ? "A" : "B";
        result.add(new FaultDescriptor("RELAY_" + channel + "_COIL_OPEN", "K" + channel,
            GeneratedFaultType.RELAY_COIL_OPEN, Double.NaN, Double.NaN));
        return result;
    }

    /** One independent named draw over the complete, fixed five-member population. */
    static String selectedFaultId(Rb56Plan plan) {
        Vector<FaultDescriptor> descriptors = faultDescriptors(plan);
        NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            plan.seed, Rb56Plan.FAMILY_ID, Rb56Plan.PLAN_VERSION);
        return descriptors.get(streams.openDevice(NamedRandomStreams.Concern.FAULT,
            1, "serviceable-region").nextInt(descriptors.size())).id;
    }

    /** Replays the exact value-bearing semantic hypothesis, without allocating another graph. */
    static String faultIdForHypothesis(Rb56Plan plan, String hypothesisKey) {
        if (hypothesisKey == null || hypothesisKey.length() == 0)
            throw new IllegalArgumentException("Missing RB56 hypothesis key");
        for (FaultDescriptor descriptor : faultDescriptors(plan))
            if (hypothesisKey.equals(descriptor.fault(plan).getHypothesisKey())) return descriptor.id;
        throw new IllegalArgumentException("Unknown RB56 hypothesis key");
    }

    static final class Candidate {
        final Rb56Plan plan;
        final RelayOutputGenerator.Assembly assembly;
        final Rb56PowerStageAssembly power;
        final Rb56ControlTail.Result tail;
        final PhysicalBoardRuntime runtime;
        final PowerDomainContract domains;
        final Vector<GeneratedFaultCandidate> faultCandidates;
        final GeneratedFaultBinding selectedFault;

        private Candidate(Rb56Plan plan, RelayOutputGenerator.Assembly assembly,
                Rb56PowerStageAssembly power, Rb56ControlTail.Result tail,
                PhysicalBoardRuntime runtime, PowerDomainContract domains,
                Vector<GeneratedFaultCandidate> faultCandidates, GeneratedFaultBinding selectedFault) {
            this.plan = plan; this.assembly = assembly; this.power = power; this.tail = tail;
            this.runtime = runtime; this.domains = domains;
            this.faultCandidates = new Vector<GeneratedFaultCandidate>(faultCandidates);
            this.selectedFault = selectedFault;
        }

        TroubleshootBoard board() { return assembly.board; }
        Vector<CircuitElm> elements() { return assembly.elements; }
    }

    private Rb56Generator() { }

    /** Healthy developer construction retains all five candidates with no applied selection. */
    static Candidate construct(Rb56Plan plan) { return construct(plan, null); }

    /** Binds one explicit member; the ordinary challenge lifecycle applies it after healthy proof. */
    static Candidate construct(Rb56Plan plan, String explicitFaultId) {
        if (plan == null) throw new IllegalArgumentException("Missing RB56 construction plan");
        if (explicitFaultId != null) {
            boolean declared = false;
            for (FaultDescriptor descriptor : faultDescriptors(plan))
                if (descriptor.id.equals(explicitFaultId)) declared = true;
            if (!declared) throw new IllegalArgumentException("Unknown RB56 fault ID: " + explicitFaultId);
        }
        RelayOutputGenerator.Assembly a = new RelayOutputGenerator.Assembly(plan.board());
        try {
            for (String net : a.board.getNetIds()) a.net(net, a.board.getNet(net).getRoutingRole());
            Rb56PowerStageAssembly power = new Rb56PowerStageAssembly(a, plan);
            Rb56ControlTail.Result tail = Rb56ControlTail.build(a, plan);
            a.requireCompleteManifest();
            Vector<GeneratedFaultCandidate> faults = faults(plan, a);
            GeneratedFaultBinding selected = selected(faults, explicitFaultId);
            GeneratedFaultEngine.clearAll(faults);
            // Sole numerical reference at the actual mains source return. The
            // secondary and relay-contact domains retain their isolated graph.
            Point sourceReturn = power.stage.input.source.getPost(0);
            GroundElm ground = new GroundElm(sourceReturn.x, sourceReturn.y);
            ground.x2 = sourceReturn.x; ground.y2 = sourceReturn.y + 32; ground.setPoints();
            a.elements.add(ground);
            PhysicalBoardRuntime runtime = Rb56PhysicalAssembly.build(a, plan, power, tail, new RelayGuard(), selected);
            PowerDomainContract domains = Rb56PowerDomains.create(plan, a.board);
            runtime.registerCapability(new PowerDomainRuntimeCapability(domains, a.board, a.power));
            runtime.registerCapability(new StoredEnergyMeasurementReadinessCapability(runtime, a.board.getSimulationBindings()));
            runtime.registerCapability(new Rb56InstrumentAdmissionCapability(runtime));
            return new Candidate(plan, a, power, tail, runtime, domains, faults, selected);
        } catch (RuntimeException failure) {
            disposeUnpublished(a, failure);
            throw failure;
        } catch (Error failure) {
            disposeUnpublished(a, failure);
            throw failure;
        }
    }

    private static Vector<GeneratedFaultCandidate> faults(Rb56Plan plan,
            RelayOutputGenerator.Assembly a) {
        Vector<GeneratedFaultCandidate> result = new Vector<GeneratedFaultCandidate>();
        for (FaultDescriptor descriptor : faultDescriptors(plan)) {
            CircuitElm actual = a.components.getSingleElement(descriptor.owner);
            if (descriptor.type == GeneratedFaultType.RESISTOR_INCORRECT_VALUE) {
                if (!(actual instanceof ResistorElm) ||
                        ((ResistorElm)actual).getResistance() != descriptor.healthyValue)
                    throw new IllegalStateException("RB56 fault resistor differs from its plan: " + descriptor.owner);
                result.add(GeneratedFaultEngine.resistorIncorrectValue(descriptor.id, Rb56Plan.FAMILY_ID,
                    plan.seed, descriptor.owner, (ResistorElm)actual,
                    descriptor.healthyValue, descriptor.effectiveValue));
            } else {
                if (!(actual instanceof ServiceRelayElm))
                    throw new IllegalStateException("RB56 fault relay has foreign backing: " + descriptor.owner);
                result.add(new GeneratedFaultCandidate(new GeneratedFaultBinding(descriptor.fault(plan),
                    new RelayFaultEffect((ServiceRelayElm)actual, true)), true));
            }
        }
        return result;
    }

    private static GeneratedFaultBinding selected(Vector<GeneratedFaultCandidate> candidates, String id) {
        if (id == null) return null;
        for (GeneratedFaultCandidate candidate : candidates)
            if (candidate.getFault().getId().equals(id)) return candidate.getBinding();
        throw new IllegalStateException("RB56 selected fault absent: " + id);
    }

    /** Allocates the live graph only after the unchanged bounded physical route succeeds. */
    static GenerationRequest.Construction constructFromAcceptedRoute(Rb56Plan plan,
            MediumBoardPhysicalPolicy.Result routed) {
        if (plan == null || routed == null || !routed.accepted() || routed.getLayout() == null)
            throw new IllegalArgumentException("RB56 construction requires an accepted physical route");
        if (MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(routed.getStatistics().selectedRoutePolicy))
            throw new GenerationJob.Rejected("RB56 admission requires a selected P07 two-layer route");
        Candidate candidate = construct(plan, selectedFaultId(plan));
        try {
            GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
                candidate.board(), routed, plan.layoutSeed, plan.routingSeed);
            return new GenerationRequest.Construction(assemble(candidate, routed.getLayout(), admission), plan.canonical());
        } catch (RuntimeException failure) {
            disposeUnpublished(candidate.assembly, failure); throw failure;
        } catch (Error failure) {
            disposeUnpublished(candidate.assembly, failure); throw failure;
        }
    }

    /** Exact geometry and all five faults are retained by every diagnostic hypothesis. */
    static GeneratedBoardInstance assemble(Candidate candidate, PcbBoardLayout layout,
            GeneratedPhysicalAdmission admission) {
        if (candidate == null || candidate.selectedFault == null || layout == null || admission == null ||
                !MediumBoardNormalAdmission.RB56_IDENTITY.equals(admission.identity()) ||
                !layout.matchesGenerationSeeds(candidate.plan.layoutSeed, candidate.plan.routingSeed))
            throw new IllegalArgumentException("RB56 assembly requires its exact selected owner and routed admission");
        layout.validateGeometry(candidate.board());
        Rb30Behavior behavior = Rb30Behavior.forRb56(candidate.board(), candidate.assembly.power,
            candidate.plan.channels());
        GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
            "RB56_OUTPUT_NOT_TRACKING", Rb56Plan.FAMILY_ID, candidate.plan.topology(),
            candidate.plan.seed, behavior.scenarios(), candidate.plan.channelCount == 1 ?
                "Repair verified. The sensor-controlled load follows its input." :
                "Repair verified. Both sensor-controlled loads follow their inputs.",
            candidate.selectedFault.getFault(), candidate.selectedFault, behavior);
        Rb56DiagnosticProvider diagnostics = new Rb56DiagnosticProvider(candidate.plan, layout, admission);
        GeneratedBoardInstance instance = new GeneratedBoardInstance(
            candidate.board(), candidate.elements(), candidate.plan.seed,
            Rb56Plan.FAMILY_ID, candidate.plan.topology(),
            "Generated " + candidate.plan.channelCount + "-channel isolated AC control board, seed " +
                Long.toString(candidate.plan.seed),
            candidate.assembly.components, candidate.assembly.power, candidate.assembly.connections,
            behavior, layout, candidate.assembly.specifications, candidate.selectedFault,
            new GeneratedComponentOperationalStates(), challenge, behavior, candidate.runtime,
            behavior, false, candidate.faultCandidates, null, diagnostics, admission);
        GeneratedDiagnosticSolvabilityAdmission.validateStructural(instance);
        return instance;
    }

    /** The caller invokes this only after failed construction, before any runtime publication. */
    static void disposeUnpublished(RelayOutputGenerator.Assembly a, Throwable failure) {
        try { a.power.clearForAbortedConstruction(a.board); }
        catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
        for (CircuitElm element : new Vector<CircuitElm>(a.elements)) {
            try { element.delete(); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
        }
    }

    /** Service uses current installed stores; unrelated loose stores and instrument refusals cannot deadlock it. */
    static final class RelayGuard implements ReplaceableRelayCapability.DischargeGuard {
        public boolean isDischarged(GeneratedBoardInstance owner, String componentId) {
            CirSim sim = CircuitElm.sim;
            if (sim == null || owner == null || sim.getGeneratedBoardInstance() != owner ||
                    sim.getBoardModificationController() == null ||
                    sim.getBoardModificationController().getInstanceForRuntimeValidation() != owner ||
                    !sim.getBoardPowerController().isElectricallyUnpowered() ||
                    !sim.isGeneratedRuntimeSettled() || !sim.solverExecutor.isCurrent(
                        sim.solverExecutor.observation(owner), owner)) return false;
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            PhysicalBoardSlot relaySlot = runtime.getSlot(componentId);
            if (relaySlot == null || !relaySlot.getPhysicalPackage().isEquivalentTo(PhysicalPackages.RELAY_SPDT) ||
                    relaySlot.isOccupied() && !(relaySlot.getInstalledPart() instanceof PhysicalRelayPart)) return false;
            PhysicalBoardRuntimeCapability policy = runtime.getCapability(StoredEnergyMeasurementReadinessCapability.CAPABILITY_ID);
            if (!(policy instanceof StoredEnergyMeasurementReadinessCapability)) return false;
            StoredEnergyMeasurementReadinessCapability capacitors = (StoredEnergyMeasurementReadinessCapability)policy;
            for (PhysicalPart<?> part : runtime.getPhysicalParts()) {
                if (!part.isInstalled()) continue;
                if (part instanceof PhysicalCapacitorPart) {
                    CircuitMeasurementEndpoint first = part.getTerminal(0).getEndpoint(), second = part.getTerminal(1).getEndpoint();
                    if (!(first instanceof CircuitPostMeasurementEndpoint) || !(second instanceof CircuitPostMeasurementEndpoint) ||
                            !capacitors.getActiveMeasurementReadiness((CircuitPostMeasurementEndpoint)first,
                                (CircuitPostMeasurementEndpoint)second, BoardPowerState.UNPOWERED, true).isReady()) return false;
                } else if (part instanceof PhysicalServicePart && part.getSpecification() instanceof InductorSpecification) {
                    PhysicalServicePart winding = (PhysicalServicePart)part;
                    if (!winding.isDetachmentReady(sim, owner)) return false;
                } else if (part instanceof PhysicalRelayPart) {
                    ServiceRelayElm relay = ((PhysicalRelayPart)part).getElement();
                    String id = part.getBoardSlot().getComponentId();
                    if (owner.getComponentBindings().getSingleElement(id) != relay || relay.ind == null ||
                            !discharged(relay.coilCurrent) || !discharged(relay.ind.current)) return false;
                }
            }
            return true;
        }
        private boolean discharged(double current) {
            return PowerDomainContract.finite(current) && Math.abs(current) < RelayOutputBehavior.DISCHARGED_AMPS;
        }
    }
}
