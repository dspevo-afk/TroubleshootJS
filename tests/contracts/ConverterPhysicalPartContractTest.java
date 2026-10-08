package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Actual graph, four-element ownership, readiness and compensation canaries; no family admission. */
public final class ConverterPhysicalPartContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim prior = CircuitElm.sim, priorSingleton = CirSim.theSim;
        TestCirSim sim = new TestCirSim();
        Vector<CircuitElm> originalGraph = sim.elmList;
        CircuitElm.sim = sim;
        Fixture fixture = null;
        Throwable failure = null;
        try {
            fixture = new Fixture();
            sim.generatedBoardInstance = fixture.instance;
            sim.elmList = fixture.instance.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, fixture.instance);
            sim.getBoardPowerController().attach(fixture.instance.getExternalPowerBindings());
            fixture.runtime.installRegisteredCapabilities(sim, fixture.instance, sim.boardModificationController, 0);
            final PhysicalSlotMutationProvider provider = fixture.runtime.getMutationProvider("U1");
            final PhysicalMutationSlot slot = ((PhysicalSlotMutationProvider.Scoped)provider).getMutationSlot();
            mappings(fixture.original);
            geometry(fixture);
            Vector<CircuitElm> copy = fixture.instance.getComponentBindings().getAuxiliaryElements("U1");
            copy.clear();
            check(fixture.instance.getComponentBindings().getAuxiliaryElements("U1").size() == 3,
                "auxiliary observation is a defensive copy");
            advance(sim, fixture, 100);
            check(fixture.bulk.getVoltageDiff() > ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS,
                "real DC source charges the board-owned capacitor above the existing readiness threshold");
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            fixture.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            sim.needAnalyze();
            WorkbenchOperation remove = WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "U1");
            check(!provider.isAvailable(remove, null), "OFF invalidates readiness before any new accepted sample");
            advance(sim, fixture, 1);
            check(fixture.bulk.getVoltageDiff() > .25 && !provider.isAvailable(remove, null),
                "charged OFF board cannot remove the installed converter");
            boolean rejected = false;
            try { provider.removeInstalledPart(); }
            catch (BoardModificationRejectedException expected) { rejected = true; }
            check(rejected, "direct provider entry preserves charged-board refusal");
            advance(sim, fixture, 1200);
            check(Math.abs(fixture.bulk.getVoltageDiff()) <= .25 && provider.isAvailable(remove, null),
                "accepted RC discharge permits module service without a reset or fabricated readiness");
            fixture.runtime.getSessionHistory().start();
            for (PhysicalMutationScope.FailureStage stage : new PhysicalMutationScope.FailureStage[] {
                    PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT,
                    PhysicalMutationScope.FailureStage.AFTER_SLOT_CLEAR,
                    PhysicalMutationScope.FailureStage.AFTER_EMPTY_SLOT_REBIND }) {
                Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList);
                int history = fixture.runtime.getSessionHistory().size();
                failAt(stage);
                try { provider.removeInstalledPart(); throw new AssertionError("Removal failure hook did not run"); }
                catch (InjectedFailure expected) { }
                finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
                check(slot.getInstalledPart() == fixture.original && before.equals(sim.elmList),
                    "partial removal restores exact graph and physical identity at " + stage);
                check(fixture.runtime.getSessionHistory().size() == history, "failed removal does not enter session replay");
                compensated(fixture);
                binding(fixture, fixture.original);
                sim.needAnalyze(); advance(sim, fixture, 1);
            }
            check(provider.removeInstalledPart(), "successful removal detaches the module");
            check(slot.isEmpty() && !fixture.original.isInstalled(), "original remains one loose inventory part");
            for (WireElm lead : fixture.leads) check(!sim.elmList.contains(lead), "all seven board leads leave the live graph");
            for (CircuitElm element : fixture.module.elements()) check(sim.elmList.contains(element), "loose module retains all actual backing");
            advance(sim, fixture, 1);
            PhysicalProbeProjection projection = new LooseProjection(fixture.original.getId());
            for (int i = 0; i < 7; i++) {
                ProbeTarget target = fixture.original.getRenderMetadata().getLooseProbeProvider().createLooseProbeTarget(
                    sim, fixture.instance, fixture.original, i, projection);
                check(target.isValid() && GeneratedComponentConnectionBindings.sameEndpoint(
                    target.getMeasurementEndpoint(), fixture.original.getTerminal(i).getEndpoint()), "loose probe uses actual terminal " + i);
            }
            String catalog = fixture.capability.getCatalogEntries().get(0).getId();
            PhysicalConverterPart candidate = (PhysicalConverterPart)((CatalogAcquisitionProvider)provider).acquireFromCatalog(catalog);
            check(candidate != fixture.original && candidate.getModule().converter != fixture.module.converter,
                "catalog acquisition creates a fresh solver bundle");
            check(candidate.getSpecification().getSpecificationId().equals(fixture.original.getSpecification().getSpecificationId()),
                "fresh contract objects retain the exact static declaration");
            mappings(candidate);
            check(candidate.getModule().converter.x >= 48512, "allocator rejects an actual occupied first candidate post");
            advance(sim, fixture, 1);
            Vector<CircuitElm> auxBefore = fixture.instance.getComponentBindings().getAuxiliaryElements("U1");
            PhysicalMutationScope scope = new PhysicalMutationScope(sim, fixture.instance, sim.boardModificationController,
                PhysicalMutationIntent.prepare(fixture.runtime, fixture.instance, sim.boardModificationController,
                    slot, "install", candidate));
            Throwable foreign = null;
            try {
                scope.replacePrimaryBinding(candidate.getModule().converter);
                Vector<CircuitElm> bad = new Vector<CircuitElm>(); bad.add(candidate.getModule().bias); bad.add(fixture.bulk);
                scope.replaceAuxiliaryBindings(bad);
                throw new AssertionError("Foreign auxiliary element accepted");
            } catch (IllegalArgumentException expected) { foreign = expected; }
            finally { scope.abort(foreign); }
            check(auxBefore.equals(fixture.instance.getComponentBindings().getAuxiliaryElements("U1")),
                "whole vector validates ownership before any auxiliary write");
            compensated(fixture);
            sim.needAnalyze(); advance(sim, fixture, 1);
            for (PhysicalMutationScope.FailureStage stage : new PhysicalMutationScope.FailureStage[] {
                    PhysicalMutationScope.FailureStage.AFTER_AUXILIARY_BINDING,
                    PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
                    PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
                    PhysicalMutationScope.FailureStage.AFTER_SLOT_MOUNT,
                    PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE }) {
                Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList);
                int history = fixture.runtime.getSessionHistory().size();
                failAt(stage);
                try { provider.install(candidate.getId()); throw new AssertionError("Installation failure hook did not run"); }
                catch (InjectedFailure expected) { }
                finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
                check(slot.isEmpty() && !candidate.isInstalled() && before.equals(sim.elmList),
                    "failed install restores graph, loose identity and empty slot at " + stage);
                check(fixture.runtime.getSessionHistory().size() == history, "failed install does not enter session replay");
                compensated(fixture);
                binding(fixture, fixture.original);
                sim.needAnalyze(); advance(sim, fixture, 1);
            }
            check(provider.install(candidate.getId()), "fresh catalog module installs through the existing transaction");
            binding(fixture, candidate);
            advance(sim, fixture, 1);
            // Keep actual bulk charge while the slot is empty; installation must still consult the board pads.
            check(provider.removeInstalledPart(), "catalog module can be removed");
            advance(sim, fixture, 1);
            sim.getBoardPowerController().setState(BoardPowerState.POWERED);
            fixture.runtime.onBoardPowerStateChanged(BoardPowerState.POWERED); sim.needAnalyze();
            advance(sim, fixture, 100);
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            fixture.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED); sim.needAnalyze();
            advance(sim, fixture, 1);
            check(fixture.bulk.getVoltageDiff() > .25 && !provider.isAvailable(
                WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL, candidate, "U1"), null),
                "empty slot installation refuses actual charged board storage");
            advance(sim, fixture, 1200);
            check(provider.install(fixture.original.getId()), "original exact backing can be reinstalled after real discharge");
            binding(fixture, fixture.original);
            advance(sim, fixture, 1);
            GeneratedRuntimeInvariant.verify(fixture.instance, sim.boardModificationController, sim.elmList);
            Vector<PlayerSessionSave.Operation> operations = fixture.runtime.getSessionHistory().operations();
            check(operations.size() == 5 && "REMOVE".equals(operations.get(0).kind) &&
                "ACQUIRE".equals(operations.get(1).kind) && "INSTALL".equals(operations.get(2).kind) &&
                "REMOVE".equals(operations.get(3).kind) && "INSTALL".equals(operations.get(4).kind),
                "successful module actions use the unchanged session operation vocabulary");
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } finally {
            Throwable cleanup = null;
            boolean disposed = true;
            try {
                PhysicalMutationScope.clearFailureHookForDeveloperVerification();
                sim.a01MeasurementRunning = false;
                sim.elmList = originalGraph;
                sim.generatedBoardInstance = null; sim.boardModificationController = null;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException error) { cleanup = error; }
                catch (Error error) { cleanup = error; }
                sim.circuitMatrix = null;
                if (fixture != null) for (CircuitElm element : fixture.instance.getSimulationElements()) {
                    try { element.delete(); }
                    catch (RuntimeException error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                    catch (Error error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                }
            } finally { CircuitElm.sim = prior; CirSim.theSim = priorSingleton; }
            boolean clean = cleanup == null && disposed && sim.elmList == originalGraph && originalGraph.isEmpty() &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == priorSingleton;
            System.out.println("CONVERTER_PHYSICAL_CLEANUP {\"rawGraphRestored\":" + (sim.elmList == originalGraph && originalGraph.isEmpty()) +
                ",\"candidateDisposed\":" + disposed + ",\"singletonRestored\":" + (CircuitElm.sim == prior && CirSim.theSim == priorSingleton) +
                ",\"clean\":" + clean + "}");
            if (failure != null) {
                if (cleanup != null) failure.addSuppressed(cleanup);
                if (!clean) failure.addSuppressed(new AssertionError("Converter physical fixture cleanup failed"));
            } else {
                if (cleanup != null) throw new AssertionError("Converter physical fixture cleanup failed", cleanup);
                check(clean, "exact graph, executor, backing disposal and singleton cleanup");
            }
        }
        System.out.println("PASS: converter physical-part contracts " + assertions +
            " assertions; native DC service/readiness fixture only; Q60 admission and session restart NOT RUN");
    }

    private static void advance(TestCirSim sim, Fixture fixture, int count) {
        int step = -1;
        try {
            if (sim.circuitMatrix == null || sim.analyzeFlag) {
                // Analysis consumes real posts even without drawing; audit newly acquired backing here too.
                for (CircuitElm element : sim.elmList) {
                    check(element != null, "active graph contains a real element");
                    check(element.boundingBox != null, "constructor supplied drawing bounds for " + element.getClass().getSimpleName());
                    for (int post = 0; post < element.getPostCount(); post++)
                        check(element.getPost(post) != null, "initialized actual post " + post + " of " + element.getClass().getSimpleName());
                }
                sim.solverExecutor.analyze();
                // The native fixture consumes the same pending-analysis flag as the production update loop.
                sim.analyzeFlag = false;
            }
            for (step = 0; step < count; step++) sim.solverExecutor.advanceSteps(1);
            fixture.runtime.observeSimulationTime(sim.t);
        } catch (SolverExecutionBoundary.Failure failure) {
            try { reportSolverFailure(sim, fixture, count, step); }
            catch (Exception diagnostic) { failure.addSuppressed(diagnostic); }
            catch (Error diagnostic) { failure.addSuppressed(diagnostic); }
            throw failure;
        }
    }
    /** Native-only read of the exact last stamped bias tangent; never evaluate or mutate model state. */
    private static void reportSolverFailure(TestCirSim sim, Fixture fixture, int count, int step) throws Exception {
        E06PwmControllerElm.BiasElm bias = fixture.module.bias;
        double input = bias.volts[0] - bias.volts[1], output = bias.volts[2] - bias.volts[1];
        double delivered = (E06ConverterContract.clamp(input, 0, E06ConverterContract.BIAS_VOLTS) - output) /
            E06ConverterContract.BIAS_RESISTANCE_OHMS;
        StringBuilder report = new StringBuilder("CONVERTER_PHYSICAL_SOLVER_FAILURE {\"acceptedTime\":");
        report.append(number(sim.t)).append(",\"operationCount\":").append(count).append(",\"stepIndex\":").append(step)
            .append(",\"subIterations\":").append(sim.subIterations).append(",\"moduleInputV\":").append(number(fixture.module.converter.getInputVoltage()))
            .append(",\"moduleOutputV\":").append(number(fixture.module.converter.getOutputVoltage()))
            .append(",\"biasInputV\":").append(number(input)).append(",\"biasOutputV\":").append(number(output))
            .append(",\"physicalBiasDeliveredA\":").append(number(delivered)).append(",\"bulkV\":").append(number(fixture.bulk.getVoltageDiff()))
            .append(",\"powerConnected\":").append(!sim.getBoardPowerController().isElectricallyUnpowered());
        String[] names = {"appliedTargetGain", "appliedTargetOffset", "appliedInputGain", "appliedOutputGain", "appliedInputOffset"};
        double[] coefficients = new double[names.length];
        for (int index = 0; index < names.length; index++) {
            java.lang.reflect.Field field = E06PwmControllerElm.BiasElm.class.getDeclaredField(names[index]);
            field.setAccessible(true);
            coefficients[index] = field.getDouble(bias);
            report.append(",\"").append(names[index]).append("\":").append(number(coefficients[index]));
        }
        report.append(",\"stampedBiasDeliveredA\":").append(number(coefficients[0] * input + coefficients[1] - output /
            E06ConverterContract.BIAS_RESISTANCE_OHMS)).append(",\"stampedBiasInputA\":").append(number(
                coefficients[2] * input + coefficients[3] * output + coefficients[4]));
        System.out.println(report.append("}").toString());
    }
    private static String number(double value) { return Double.isNaN(value) || Double.isInfinite(value) ? "null" : Double.toString(value); }
    private static void mappings(PhysicalConverterPart part) {
        String[] ids = {"IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"};
        for (int i = 0; i < 7; i++) {
            CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)part.getTerminal(i).getEndpoint();
            check(ids[i].equals(part.getTerminal(i).getTerminalName()) && endpoint.getElement() ==
                (i == 6 ? part.getModule().bias : part.getModule().converter) && endpoint.getPostIndex() == (i == 6 ? 2 : i),
                "independent seven-terminal live-post map " + ids[i]);
        }
        Vector<CircuitElm> backing = part.getElectricalBacking().getCircuitElements();
        check(backing.size() == 4 && backing.equals(part.getModule().elements()), "one physical module owns all four backing elements");
    }
    private static void geometry(Fixture fixture) {
        PhysicalPackage pkg = fixture.original.getPackage();
        check(!pkg.getGeometry().isDeveloperGeneric(), "module consumes concrete physical geometry");
        for (String first : pkg.getTerminalIds()) for (String second : pkg.getTerminalIds())
            if (!first.equals(second)) check(!pkg.isInternallyConnected(first,second),
                "package declares no implicit terminal short");
        check(pkg.getGeometry().getIsolationBody() != null && pkg.getTerminalIds().equals(ConverterSpecification.terminalIds()),
            "qualified seven-pad body declares its insulating span");
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            fixture.board.getComponent("U1"), 100, 100, new java.util.Random(0), new Rectangle(0, 0, 1000, 1000));
        check(footprint.getPads().size() == 7, "registered footprint projects every physical terminal");
        fixture.original.getBoardSlot().bindGeometryRealization(footprint.getPlacement());
        check(StandardPhysicalPartRenderProviders.createRegistry().requireRenderer(pkg, fixture.original) != null,
            "registered renderer consumes the concrete module package");
        for (int i = 0; i < 7; i++) check(fixture.board.getPlacementConstraints().domainForPad(fixture.board, "U1." +
            ConverterSpecification.terminalId(i)).equals(i == 2 || i == 3 ? "SECONDARY" : "PRIMARY"), "explicit terminal physical domain " + i);
    }
    private static void binding(Fixture fixture, PhysicalConverterPart part) {
        check(fixture.instance.getComponentBindings().getSingleElement("U1") == part.getModule().converter,
            "exact converter primary binding");
        check(fixture.instance.getComponentBindings().getAuxiliaryElements("U1").equals(part.auxiliaryElements()),
            "exact Bias/internal-wire auxiliary binding vector");
        for (int i = 0; i < 7; i++) {
            String id = "U1." + ConverterSpecification.terminalId(i);
            GeneratedComponentConnectionBinding binding = fixture.instance.getConnectionBindings().get("U1", id);
            check(binding.getBoardEndpoint() == fixture.boardEndpoints[i] &&
                GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), part.getTerminal(i).getEndpoint()),
                "persistent copper and exact live component endpoint " + i);
            Point component = part.getModule().terminalPoint(i);
            check(fixture.leads[i].getPost(0).equals(fixture.points[i]) && fixture.leads[i].getPost(1).equals(component),
                "only detachable lead component end follows replacement " + i);
        }
    }
    private static void compensated(Fixture fixture) {
        check(fixture.runtime.getLastMutationReceipt().isCompensated() && !fixture.runtime.isMutationInProgress() &&
            !fixture.runtime.isMutationQuarantined(), "failed transaction compensates and releases exact owner");
    }
    private static final class InjectedFailure extends RuntimeException { InjectedFailure() { super("expected converter transaction canary"); } }
    private static void failAt(final PhysicalMutationScope.FailureStage requested) {
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage actual) { if (actual == requested) throw new InjectedFailure(); }
        });
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }

    private static final class Fixture {
        final TroubleshootBoard board = new TroubleshootBoard("CONVERTER_PHYSICAL_FIXTURE");
        final PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        final IsolatedConverterModuleModel module = new IsolatedConverterModuleModel(32768, 32768);
        final PhysicalConverterPart original = new PhysicalConverterPart("U1", new ConverterSpecification(), module,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, "U1"));
        final WireElm[] leads = new WireElm[7];
        final CircuitPostMeasurementEndpoint[] boardEndpoints = new CircuitPostMeasurementEndpoint[7];
        final Point[] points = {new Point(500,400),new Point(500,496),new Point(700,400),new Point(700,496),
            new Point(500,300),new Point(550,300),new Point(600,300)};
        final CapacitorElm bulk = end(new CapacitorElm(16000,16000),16096,16000);
        final GeneratedBoardInstance instance;
        final ReplaceableConverterCapability capability;
        Fixture() {
            Vector<CircuitElm> elements = module.elements(); elements.add(bulk);
            boolean complete = false;
            try {
                GeneratedComponentBindings components = new GeneratedComponentBindings(board);
                GeneratedComponentConnectionBindings connections = new GeneratedComponentConnectionBindings(board);
                BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
                board.addComponent(new BoardComponent("U1", "CONVERTER", PhysicalPackages.ISOLATED_CONVERTER_7));
                Vector<PcbPlacementConstraints.TerminalDomain> terminalDomains = new Vector<PcbPlacementConstraints.TerminalDomain>();
                for (int i = 0; i < 7; i++) {
                    String terminal = ConverterSpecification.terminalId(i), pad = "U1." + terminal, net = "N" + i;
                    board.addNet(new BoardNet(net)); board.addPad(new BoardPad(pad,"U1",terminal,net));
                    WireElm anchor = wire(points[i],new Point(points[i].x+16,points[i].y)); elements.add(anchor);
                    leads[i] = wire(points[i],module.terminalPoint(i)); elements.add(leads[i]);
                    boardEndpoints[i] = new CircuitPostMeasurementEndpoint(anchor,0);
                    board.getSimulationBindings().bindPad(pad,boardEndpoints[i]);
                    connections.bind("U1",pad,boardEndpoints[i],module.terminal(i),leads[i]);
                    terminalDomains.add(new PcbPlacementConstraints.TerminalDomain(terminal,i == 2 || i == 3 ? "SECONDARY" : "PRIMARY"));
                }
                components.bindComponent("U1",module.converter); components.bindAuxiliaryComponentElements("U1",original.auxiliaryElements());
                runtime.createSlot("U1").install(original);
                specifications.addPhysicalDefinition("U1",original.getSpecification(),original.getPlayerVisibleNameplate(),original.getPackage());
                bulk.capacitance = .000010; bulk.flags = CapacitorElm.FLAG_BACK_EULER;
                CapacitorSpecification capSpec = new CapacitorSpecification("SERVICE_10UF_25V",.000010,20,25,
                    PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,new CapacitorNameplate("Bulk capacitor","10 uF / 25 V"));
                PhysicalCapacitorPart capPart = new PhysicalCapacitorPart("C1",capSpec,capSpec.getNameplate().forPhysicalPartId("C1"),bulk,
                    null,CapacitorPartLocation.INSTALLED,new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,"C1"));
                board.addComponent(new BoardComponent("C1","CAPACITOR",capSpec.getPhysicalPackage()));
                for (int i = 0; i < 2; i++) {
                    String id = "C1." + capSpec.getPhysicalPackage().getTerminalIds().get(i);
                    board.addPad(new BoardPad(id,"C1",capSpec.getPhysicalPackage().getTerminalIds().get(i),"N"+i));
                    WireElm lead = i == 0 ? wire(points[i],bulk.getPost(i)) : wire(bulk.getPost(i),points[i]); elements.add(lead);
                    CircuitPostMeasurementEndpoint endpoint = boardEndpoints[i];
                    board.getSimulationBindings().bindPad(id,endpoint); connections.bind("C1",id,endpoint,capPart.getTerminal(i).getEndpoint(),lead);
                }
                components.bindComponent("C1",bulk); runtime.createSlot("C1").install(capPart);
                specifications.addPhysicalDefinition("C1",capSpec,capPart.getPlayerVisibleNameplate(),capSpec.getPhysicalPackage());
                Vector<PcbPlacementConstraints.Part> demands = new Vector<PcbPlacementConstraints.Part>();
                demands.add(new PcbPlacementConstraints.Part("U1","PRIMARY","Primary","PRIMARY",PcbPlacementConstraints.Anchor.NONE,16,terminalDomains));
                demands.add(new PcbPlacementConstraints.Part("C1","PRIMARY","Primary","PRIMARY",PcbPlacementConstraints.Anchor.NONE,16));
                Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
                barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY","SECONDARY",100));
                board.setPlacementConstraints(new PcbPlacementConstraints(demands,barriers));
                VoltageElm source = end(new VoltageElm(0,560,VoltageElm.WF_DC),0,400); source.maxVoltage = 12;
                ResistorElm impedance = end(new ResistorElm(0,400),64,400); impedance.setResistance(22);
                SwitchElm line = end(new SwitchElm(64,400),500,400), returned = end(new SwitchElm(0,560),500,496);
                Vector<CircuitElm> input = new Vector<CircuitElm>(); input.add(source); input.add(impedance); input.add(line); input.add(returned);
                elements.addAll(input);
                GeneratedExternalPowerBindings external = new GeneratedExternalPowerBindings(board);
                board.addPowerInput(new ExternalBoardPowerInput("BENCH","U1.IN+","U1.IN-","N0","N1"));
                external.bindPowerInput("BENCH",new ExternalPowerSimulationBinding(input,new SwitchExternalPowerControl(new SwitchElm[]{line,returned})));
                specifications.addPowerInputNameplate(new PowerInputNameplate("BENCH",12));
                ResistorElm bleed = end(new ResistorElm(500,400),500,496); bleed.setResistance(1000); elements.add(bleed);
                elements.add(end(new GroundElm(500,496),500,528));
                elements.add(wire(new Point(48000,48000),new Point(48016,48000)));
                runtime.registerCapability(new StoredEnergyMeasurementReadinessCapability(runtime,board.getSimulationBindings()));
                Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
                instance = new GeneratedBoardInstance(board,elements,0,"CONVERTER_PHYSICAL_FIXTURE","SEVEN_TERMINAL_SERVICE","Native service canary",
                    components,external,connections,new GeneratedChallengeBehaviorContract() {
                        public void verifyHealthy(GeneratedBoardInstance owner,BoardPowerState state) { }
                        public void verifyFaulted(GeneratedBoardInstance owner,BoardModificationController changes,BoardPowerState state) { }
                        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,BoardModificationController changes,BoardPowerState state,boolean overlay) {
                            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
                        }
                        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,BoardModificationController changes,BoardPowerState state,boolean overlay) { return false; }
                    },null,specifications,null,new GeneratedComponentOperationalStates(),null,null,runtime,null,true,faults,
                    GeneratedDiagnosticSolvabilityContract.forDeveloperFixture("CONVERTER_PHYSICAL_FIXTURE","SEVEN_TERMINAL_SERVICE",0,faults));
                capability = (ReplaceableConverterCapability)runtime.getScopedMutationCapability("U1");
                complete = true;
            } finally { if (!complete) for (CircuitElm element : elements) element.delete(); }
        }
    }
    private static <T extends CircuitElm> T end(T element,int x,int y) { element.x2=x; element.y2=y; element.setPoints(); return element; }
    private static WireElm wire(Point a,Point b) { return end(new WireElm(a.x,a.y),b.x,b.y); }
    private static final class LooseProjection implements PhysicalProbeProjection {
        final String part; final Object token = new Object();
        LooseProjection(String part) { this.part = part; }
        public boolean hasPad(String id) { return false; }
        public boolean canProbePad(String id) { return false; }
        public Point getPadPoint(String id) { return null; }
        public Point getComponentLeadPoint(String component,String pad) { return null; }
        public Point getLooseTerminalPoint(String id,int terminal) { return new Point(terminal*40,0); }
        public boolean isBoardPointVisible(Point point) { return false; }
        public Object captureInstalledTargetIdentity(String component,String pad) { return null; }
        public boolean isInstalledTargetIdentityCurrent(String component,String pad,Object token) { return false; }
        public Object captureLooseProjectionToken() { return token; }
        public boolean isLooseProjectionTokenCurrent(Object candidate) { return candidate == token; }
        public boolean isLoosePartVisibleOnCurrentPage(String id) { return part.equals(id); }
    }
    private static final class TestCirSim extends CirSim {
        TestCirSim() {
            gridSize=16; gridMask=~15; gridRound=7; elmList=new Vector<CircuitElm>(); adjustables=new Vector<Adjustable>();
            undoStack=new Vector<String>(); redoStack=new Vector<String>();
            maxTimeStep=minTimeStep=timeStep=.00005; adjustTimeStep=false;
        }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag=true; }
        @Override void stop(String message,CircuitElm element) {
            stopMessage=message; circuitMatrix=null; stopElm=element; setSimRunning(false); analyzeFlag=false;
        }
        @Override public void setSimRunning(boolean running) { simRunning=running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
