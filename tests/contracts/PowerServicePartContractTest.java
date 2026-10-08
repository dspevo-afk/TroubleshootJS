package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Actual post/catalog mappings and small ordinary-solver transaction fixtures; no Q60 admission. */
public final class PowerServicePartContractTest {
    private static int checks;
    public static void main(String[] args) {
        CirSim previous = CircuitElm.sim, previousSingleton = CirSim.theSim;
        Point previousP1 = CircuitElm.ps1, previousP2 = CircuitElm.ps2;
        TestCirSim sim = new TestCirSim();
        Vector<CircuitElm> raw = sim.elmList;
        CircuitElm.sim = sim; CircuitElm.ps1 = new Point(); CircuitElm.ps2 = new Point();
        try {
            recipes(sim);
            detachment(sim);
            for (PhysicalSpecification spec : new PhysicalSpecification[] {
                    InductorSpecification.STANDARD, ZenerSpecification.STANDARD, OptocouplerSpecification.STANDARD })
                transactions(sim, spec);
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            sim.a01MeasurementRunning = false; sim.elmList = raw;
            sim.generatedBoardInstance = null; sim.boardModificationController = null;
            sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); sim.circuitMatrix = null;
            CircuitElm.sim = previous; CirSim.theSim = previousSingleton;
            CircuitElm.ps1 = previousP1; CircuitElm.ps2 = previousP2;
        }
        check(sim.elmList == raw && raw.isEmpty() && CircuitElm.sim == previous && CirSim.theSim == previousSingleton,
            "raw owner and simulator singleton restored");
        System.out.println("PASS: power service-part contracts assertions=" + checks +
            " scope=ORDINARY_SOLVER_POST_CATALOG_TRANSACTION; Q60 admission/session restart NOT RUN");
    }

    private static void recipes(TestCirSim sim) {
        String diode = DiodeElm.lastModelName, transistor = TransistorElm.lastModelName;
        DiodeElm.lastModelName = "1N4148"; TransistorElm.lastModelName = "spice-default";
        try {
            for (final PhysicalSpecification spec : new PhysicalSpecification[] {
                    InductorSpecification.STANDARD, ZenerSpecification.STANDARD, OptocouplerSpecification.STANDARD }) {
                final PhysicalServicePart part = part("P", spec, 1000, 1000);
                try {
                    mapping(part);
                    check("1N4148".equals(DiodeElm.lastModelName) && "spice-default".equals(TransistorElm.lastModelName),
                        "factory preserves the caller's last-model selections");
                    check(!part.isConnector(), "two-element winding never acquires connector semantics");
                    Vector<CircuitElm> copy = part.elements(); copy.clear();
                    check(!part.elements().isEmpty(), "backing observation is defensive");
                    PhysicalPackage pkg = part.getPackage();
                    check(!pkg.getGeometry().isDeveloperGeneric(), "concrete production package geometry");
                    check(StandardPcbFootprintProviders.createRegistry().getProvider(pkg) != null &&
                        StandardPhysicalPartRenderProviders.createRegistry().requireRenderer(pkg, part) != null,
                        "existing registries expose footprint and physical renderer");
                    for (String first : pkg.getTerminalIds()) for (String second : pkg.getTerminalIds())
                        if (!first.equals(second)) check(!pkg.isInternallyConnected(first, second), "no package-internal copper union");
                    reject(new Runnable() { public void run() {
                        PhysicalServicePart.requireBacking(new BasicPhysicalSpecification("FORGED"), part.getPackage(), part.elements());
                    } }, "typed recipe cannot be relabeled as generic service physics");
                    Vector<CircuitElm> occupied = new Vector<CircuitElm>();
                    WireElm blocker = end(new WireElm(48000,48000),48016,48000); occupied.add(blocker);
                    Vector<CircuitElm> allocated = part.createCatalogBacking(occupied);
                    try {
                        check(allocated.get(0).x >= 48256, "allocator rejects occupied actual first post");
                        PhysicalServicePart.requireBacking(spec, pkg, allocated);
                        for (CircuitElm element : allocated) for (int i = 0; i < element.getPostCount(); i++)
                            for (CircuitElm other : occupied) for (int j = 0; j < other.getPostCount(); j++)
                                check(!element.getPost(i).equals(other.getPost(j)), "no allocated post aliases retained graph");
                    } finally { dispose(allocated); blocker.delete(); }
                    if (spec instanceof InductorSpecification) {
                        final InductorElm winding = (InductorElm)part.primary();
                        int flags = winding.flags;
                        winding.ind.setup(.040, 0, flags);
                        check(winding.inductance == .020 && winding.flags == flags,
                            "hidden inductance negative leaves outer declaration unchanged");
                        reject(new Runnable() { public void run() {
                            new PhysicalServicePart("HIDDEN_L", spec, new PhysicalNameplate("HIDDEN_L","Winding"),
                                part.getPackage(),part.elements(),new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,"HIDDEN_L"));
                        } }, "part construction rejects hidden solver inductance before physical publication");
                        winding.ind.setup(.020, 0, flags | Inductor.FLAG_BACK_EULER);
                        check(winding.inductance == .020 && winding.flags == flags,
                            "hidden integration negative leaves outer declaration unchanged");
                        reject(new Runnable() { public void run() {
                            PhysicalServicePart.requireBacking(spec, part.getPackage(), part.elements());
                        } }, "hidden solver integration flags must match the fixed outer declaration");
                        winding.ind.setup(.020, 0, flags);
                        final ResistorElm resistance = (ResistorElm)part.secondary();
                        resistance.resistance = 0;
                        reject(new Runnable() { public void run() {
                            PhysicalServicePart.requireBacking(spec, part.getPackage(), part.elements());
                        } }, "winding resistance cannot be removed from the physical recipe");
                        resistance.resistance = .5;
                        resistance.move(16, 0);
                        reject(new Runnable() { public void run() {
                            PhysicalServicePart.requireBacking(spec, part.getPackage(), part.elements());
                        } }, "internal winding junction must remain joined");
                        resistance.move(-16, 0);
                    }
                    if (spec instanceof ZenerSpecification) {
                        final ZenerElm zener = (ZenerElm)part.primary();
                        zener.diode.setup(DiodeModel.getDefaultModel());
                        check(zener.model.breakdownVoltage == 10.7, "hidden zener setup negative retains the fixed outer model");
                        reject(new Runnable() { public void run() {
                            PhysicalServicePart.requireBacking(spec, part.getPackage(), part.elements());
                        } }, "zener rejects a changed actual embedded junction setup");
                        zener.setup();
                    }
                    if (spec instanceof OptocouplerSpecification) {
                        check(pkg.getGeometry().getIsolationBody() != null, "opto declares its inert isolation body");
                        final OptocouplerElm opto = (OptocouplerElm)part.primary();
                        opto.diode.diode.setup(DiodeModel.getModelWithName("1N4148"));
                        check("default".equals(opto.diode.modelName), "hidden opto setup negative retains its outer model name");
                        reject(new Runnable() { public void run() {
                            PhysicalServicePart.requireBacking(spec, part.getPackage(), part.elements());
                        } }, "opto rejects changed actual embedded diode setup");
                        opto.diode.setup();
                        opto.transistor.vcrit += .01;
                        reject(new Runnable() { public void run() {
                            PhysicalServicePart.requireBacking(spec, part.getPackage(), part.elements());
                        } }, "opto rejects changed actual transistor setup coefficient");
                        opto.transistor.setup();
                        for (int first = 0; first < 2; first++) for (int second = 2; second < 4; second++)
                            check(!opto.getConnection(first, second), "ordinary opto preserves separate electrical sides");
                        check(opto.compElmList.size() == 3 && opto.compElmList.get(0) == opto.diode &&
                            opto.compElmList.get(1) instanceof CCCSElm && opto.compElmList.get(2) == opto.transistor,
                            "single package owns standard actual diode/CCCS/NPN composite");
                    }
                } finally { dispose(part.elements()); }
            }
        } finally { DiodeElm.lastModelName = diode; TransistorElm.lastModelName = transistor; }
    }

    /** Real source energization and a retained RL return path prove the detachment condition. */
    private static void detachment(final TestCirSim sim) {
        final Fixture f = new Fixture(InductorSpecification.STANDARD);
        try {
            sim.generatedBoardInstance = f.instance; sim.elmList = f.instance.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim,f.instance);
            sim.getBoardPowerController().attach(f.instance.getExternalPowerBindings());
            f.runtime.installRegisteredCapabilities(sim,f.instance,sim.boardModificationController,0);
            final ServiceSlotController provider = (ServiceSlotController)f.runtime.getMutationProvider("P");
            renderer(sim,f);
            advance(sim,f); sim.solverExecutor.advanceSteps(1999); f.runtime.observeSimulationTime(sim.t);
            final InductorElm winding = (InductorElm)f.original.primary();
            check(sim.stopMessage == null && sim.solverExecutor.observation(f.instance) != null &&
                winding.current > .001 && winding.ind.current > .001,
                "ordinary bench source actually energizes both winding-current representations");
            double energized = winding.current;
            check(sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED) &&
                f.isolation[0].position == 1 && f.isolation[1].position == 1,
                "both actual source poles open while the ten-ohm RL return remains connected");
            check(!sim.boardModificationController.isDetachmentReady() &&
                sim.solverExecutor.observation(f.instance) == null,
                "OFF control revision requires an accepted new observation before any detach");
            blockedDetachment(sim,f,provider);
            sim.needAnalyze(); advance(sim,f);
            check(sim.solverExecutor.observation(f.instance) != null && winding.current > .001 &&
                winding.ind.current > .001 && winding.current < energized,
                "first accepted OFF step retains and physically decays winding energy instead of resetting it");
            blockedDetachment(sim,f,provider);
            check(provider.acquireFromCatalog(f.capability.catalogId()) != null,
                "catalog acquisition stays available while an installed winding decays");
            advance(sim,f);
            for (int batch = 0; batch < 20 && !sim.boardModificationController.isDetachmentReady(); batch++) {
                sim.solverExecutor.advanceSteps(100); f.runtime.observeSimulationTime(sim.t);
            }
            check(sim.boardModificationController.isDetachmentReady() &&
                Math.abs(winding.current) < InductorSpecification.DETACH_CURRENT_LIMIT_AMPS &&
                Math.abs(winding.ind.current) < InductorSpecification.DETACH_CURRENT_LIMIT_AMPS,
                "ordinary RL solver decay reaches the declared current criterion");
            check(provider.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE,"P"),null),
                "service remove availability follows the same accepted discharge condition");
            check(sim.boardModificationController.liftLead("RDECAY","RDECAY.1"),
                "unscoped lead lift succeeds after the whole-board condition is met");
            advance(sim,f);
            check(sim.boardModificationController.reconnectLead("RDECAY","RDECAY.1"),
                "unscoped reconnect retains its normal behavior"); advance(sim,f);
            check(sim.boardModificationController.removeComponent("RDECAY"),
                "one pre-write condition permits the entire unscoped two-lead disconnect loop"); advance(sim,f);
            check(sim.boardModificationController.restoreComponent("RDECAY"),
                "unscoped restoration retains its normal behavior"); advance(sim,f);
            final int[] cuts = {0}; double before = sim.t;
            PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
                public void afterStage(PhysicalMutationScope.FailureStage stage) {
                    if (stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT) {
                        cuts[0]++;
                        // Retire the old sample after the first write; remaining writes use the checked scope permit.
                        if (cuts[0] == 1) sim.solverExecutor.invalidate();
                    }
                }
            });
            try { check(provider.removeInstalledPart(),"checked scope permit completes removal after its first graph write"); }
            finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
            check(cuts[0] == 2 && sim.t == before && !f.original.isInstalled() &&
                sim.solverExecutor.observation(f.instance) == null,
                "both leads and physical mount detach atomically without claiming a fresh edited-graph sample");
            advance(sim,f);
            check(provider.install(f.original.getId()),"ordinary install is unaffected by the detachment-only guard");
            binding(f,f.original); advance(sim,f);
            GeneratedRuntimeInvariant.verify(f.instance,sim.boardModificationController,sim.elmList);
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); sim.circuitMatrix = null;
            sim.generatedBoardInstance = null; sim.boardModificationController = null; sim.elmList = new Vector<CircuitElm>();
            sim.getBoardPowerController().detach(); dispose(f.instance.getSimulationElements());
        }
    }
    private static void blockedDetachment(final TestCirSim sim, final Fixture f,
            final ServiceSlotController provider) {
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList), canonical = f.instance.getSimulationElements();
        InductorElm winding = (InductorElm)f.original.primary();
        double current = winding.current, embedded = winding.ind.current;
        check(!provider.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE,"P"),null) &&
            !provider.isAvailable(WorkbenchOperation.forComponentLead(WorkbenchOperation.LIFT_LEAD,"P","P.1"),null) &&
            !sim.boardModificationController.isDetachmentReadyFor(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE,"RDECAY")),
            "remove/lift UI conditions cover the winding and another package's current path");
        rejectDetachment(new Runnable() { public void run() { provider.removeInstalledPart(); } },"service whole-package removal rejects");
        rejectDetachment(new Runnable() { public void run() { sim.boardModificationController.removeComponent("P"); } },"direct scoped graph removal rejects");
        rejectDetachment(new Runnable() { public void run() { sim.boardModificationController.liftLead("P","P.1"); } },"direct scoped lead lift rejects");
        rejectDetachment(new Runnable() { public void run() { sim.boardModificationController.removeComponent("RDECAY"); } },"unscoped other-package removal rejects");
        rejectDetachment(new Runnable() { public void run() { sim.boardModificationController.liftLead("RDECAY","RDECAY.1"); } },"unscoped current-path lead lift rejects");
        check(graph.equals(sim.elmList) && canonical.equals(f.instance.getSimulationElements()) &&
            f.original.isInstalled() && winding.current == current && winding.ind.current == embedded &&
            sim.boardModificationController.isLeadConnected("P","P.1") &&
            sim.boardModificationController.isLeadConnected("P","P.2") &&
            sim.boardModificationController.isLeadConnected("RDECAY","RDECAY.1") &&
            sim.boardModificationController.isLeadConnected("RDECAY","RDECAY.2"),
            "rejected detaches leave graph, canonical backing, mount, currents and all attachments unchanged");
        binding(f,f.original);
        check(sim.boardModificationController.isDetachmentReadyFor(WorkbenchOperation.forComponent(WorkbenchOperation.RESTORE,"RDECAY")) &&
            sim.boardModificationController.isDetachmentReadyFor(WorkbenchOperation.forComponentLead(WorkbenchOperation.RECONNECT_LEAD,"RDECAY","RDECAY.1")),
            "detachment policy does not gate restore or reconnect");
    }
    private static void rejectDetachment(Runnable action,String message) {
        boolean rejected = false;
        try { action.run(); } catch (BoardModificationRejectedException expected) { rejected = true; }
        check(rejected,message);
    }

    private static void transactions(TestCirSim sim, PhysicalSpecification spec) {
        Fixture f = new Fixture(spec);
        try {
            sim.generatedBoardInstance = f.instance; sim.elmList = f.instance.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, f.instance);
            sim.getBoardPowerController().attach(f.instance.getExternalPowerBindings());
            check(f.instance.getExternalPowerBindings().hasControlsForAllInputs() &&
                f.instance.getExternalPowerBindings().areAllConnected() &&
                f.isolation[0].position == 0 && f.isolation[1].position == 0 &&
                sim.elmList.contains(f.source), "fixture owns an actual connected controllable bench source");
            check(sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED) &&
                sim.getBoardPowerController().isElectricallyUnpowered() &&
                f.isolation[0].position == 1 && f.isolation[1].position == 1,
                "normal OFF command physically opens both source poles before service");
            f.runtime.installRegisteredCapabilities(sim, f.instance, sim.boardModificationController, 0);
            ServiceSlotController provider = (ServiceSlotController)f.runtime.getMutationProvider("P");
            ServiceComponentSlot slot = (ServiceComponentSlot)provider.getMutationSlot();
            PcbWorkbenchRenderer renderer = renderer(sim, f);
            renderGeometry(f, renderer, false);
            advance(sim, f);
            check(provider.removeInstalledPart() && slot.isEmpty() && !f.original.isInstalled(), "whole package removes through existing service owner");
            for (WireElm lead : f.leads) check(!sim.elmList.contains(lead), "all board attachments leave live graph");
            for (CircuitElm element : f.original.elements()) check(sim.elmList.contains(element), "loose part retains actual complete backing");
            renderGeometry(f, renderer, true);
            advance(sim, f);
            PhysicalProbeProjection projection = new LooseProjection(f.original.getId());
            for (int i = 0; i < f.original.getTerminalCount(); i++) {
                ProbeTarget target = f.original.getRenderMetadata().getLooseProbeProvider().createLooseProbeTarget(
                    sim,f.instance,f.original,i,projection);
                check(target.isValid() && GeneratedComponentConnectionBindings.sameEndpoint(
                    target.getMeasurementEndpoint(),f.original.getTerminal(i).getEndpoint()),
                    "loose service probe resolves the actual external post " + i);
            }
            String catalog = f.capability.catalogId();
            check(catalog.equals(spec.getSpecificationId() + "_STANDARD"), "catalog identity describes the exact fixed recipe");
            for (PhysicalMutationScope.FailureStage stage : new PhysicalMutationScope.FailureStage[] {
                    PhysicalMutationScope.FailureStage.AFTER_INVENTORY_ACQUIRE,
                    PhysicalMutationScope.FailureStage.AFTER_CANONICAL_REGISTER,
                    PhysicalMutationScope.FailureStage.AFTER_GRAPH_APPEND }) {
                Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList), canonical = f.instance.getSimulationElements();
                int inventory = f.inventory.size(); failAt(stage);
                try { provider.acquireFromCatalog(catalog); throw new AssertionError("acquisition failure hook absent"); }
                catch (InjectedFailure expected) { }
                finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
                check(before.equals(sim.elmList) && canonical.equals(f.instance.getSimulationElements()) && f.inventory.size() == inventory,
                    "catalog abort restores graph/canonical/inventory at " + stage);
                check(!f.runtime.isMutationInProgress() && !f.runtime.isMutationOwnerQuarantined("P"), "catalog abort compensates without isolation");
                advance(sim, f);
            }
            PhysicalServicePart candidate = (PhysicalServicePart)provider.acquireFromCatalog(catalog);
            mapping(candidate);
            check(candidate != f.original && candidate.primary() != f.original.primary(), "catalog owns fresh ordinary solver backing");
            advance(sim, f);
            for (PhysicalMutationScope.FailureStage stage : new PhysicalMutationScope.FailureStage[] {
                    PhysicalMutationScope.FailureStage.AFTER_AUXILIARY_BINDING,
                    PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
                    PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
                    PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE }) {
                Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList); failAt(stage);
                try { provider.install(candidate.getId()); throw new AssertionError("install failure hook absent"); }
                catch (InjectedFailure expected) { }
                finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
                check(slot.isEmpty() && !candidate.isInstalled() && before.equals(sim.elmList), "install abort restores loose identity/graph at " + stage);
                binding(f, f.original);
                advance(sim, f);
            }
            check(provider.install(candidate.getId()) && slot.getInstalledPart() == candidate, "catalog part installs through existing transaction");
            binding(f, candidate); advance(sim, f);
            for (WireElm lead : f.leads) check(sim.elmList.contains(lead), "all package attachments reconnect");
            check(provider.removeInstalledPart(), "replacement can return to inventory"); advance(sim, f);
            check(provider.install(f.original.getId()), "original exact bundle can be reinstalled"); binding(f, f.original); advance(sim, f);
            GeneratedRuntimeInvariant.verify(f.instance, sim.boardModificationController, sim.elmList);
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); sim.circuitMatrix = null;
            sim.generatedBoardInstance = null; sim.boardModificationController = null; sim.elmList = new Vector<CircuitElm>();
            sim.getBoardPowerController().detach();
            dispose(f.instance.getSimulationElements());
        }
    }

    private static PcbWorkbenchRenderer renderer(TestCirSim sim, Fixture f) {
        PcbBoardLayout layout = new PcbBoardLayout(1000,1000,
            new Rectangle(0,0,1000,600),new Rectangle(0,650,1000,300));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(f.board.getComponent("P"),100,100);
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        f.runtime.getSlot("P").bindGeometryRealization(footprint.getPlacement());
        return new PcbWorkbenchRenderer(f.instance,sim.boardModificationController,layout);
    }
    private static void renderGeometry(Fixture f, PcbWorkbenchRenderer renderer, boolean loose) {
        PhysicalServicePart part = f.original;
        PhysicalSpecification specification = part.getSpecification();
        PhysicalNameplate nameplate = part.getPlayerVisibleNameplate();
        PcbComponentPlacement placement = loose ? null :
            renderer.getLayoutForProvider().getComponent("P");
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer,placement,
            part,part.getPackage(),loose ? 0 : -1,loose);
        check(loose ? !part.isInstalled() : context.isInstalledPartMounted(),
            "geometry observes actual installed or removed service identity");
        PhysicalPartRenderer provider = StandardPhysicalPartRenderProviders.createRegistry()
            .requireRenderer(part.getPackage(),part);
        PhysicalPartRenderGeometry geometry = loose ? provider.getLooseGeometry(context) :
            provider.getInstalledGeometry(context);
        check(geometry.getTerminals().size() == part.getTerminalCount() &&
            geometry.getBodyBounds().width > 0 && geometry.getBodyBounds().height > 0,
            "actual service renderer produces complete body and terminal geometry");
        for (int i = 0; i < part.getTerminalCount(); i++) {
            PhysicalPartRenderTerminal terminal = geometry.getTerminal(i);
            Point expected = loose ? context.getLooseTerminalPoint(i,false) :
                context.getInstalledBoardPadPoint(i);
            check(part.getTerminal(i).getTerminalName().equals(terminal.getTerminalId()) &&
                terminal.getPoint().equals(expected),
                "geometry preserves the actual normal terminal projection " + i);
        }
        check(part.getSpecification() == specification && part.getPlayerVisibleNameplate() == nameplate &&
            part.getRenderMetadata().getVisualSpecification() == specification,
            "installed and loose rendering preserve typed recipe and immutable nameplate identity");
        if (specification == ZenerSpecification.STANDARD) {
            check("10.7 V zener diode".equals(nameplate.getDisplayName()) &&
                !part.getRenderMetadata().isReversedInstallation() &&
                "A".equals(geometry.getTerminal(0).getTerminalId()) &&
                "K".equals(geometry.getTerminal(1).getTerminalId()),
                "fixed zener retains truthful player markings and normal axial A/K polarity");
        }
    }

    private static void binding(Fixture f, PhysicalServicePart part) {
        check(f.instance.getComponentBindings().getSingleElement("P") == part.primary() &&
            f.instance.getComponentBindings().getAuxiliaryElements("P").equals(part.auxiliaryElements()), "primary and all auxiliary bindings belong to one package");
        for (int i = 0; i < part.getTerminalCount(); i++) {
            GeneratedComponentConnectionBinding binding = f.instance.getConnectionBindings().get("P", "P." + part.getTerminal(i).getTerminalName());
            check(GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), part.getTerminal(i).getEndpoint()),
                "transaction retargets actual external post " + i);
        }
    }
    private static void mapping(PhysicalServicePart part) {
        boolean winding = part.getSpecification() instanceof InductorSpecification;
        String[] names = winding ? new String[] {"1","2"} : part.getSpecification() instanceof ZenerSpecification ?
            new String[] {"A","K"} : new String[] {"A","K","C","E"};
        check(part.getTerminalCount() == names.length, "declared package has exact external post count");
        for (int i = 0; i < names.length; i++) {
            CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)part.getTerminal(i).getEndpoint();
            check(names[i].equals(part.getTerminal(i).getTerminalName()) && endpoint.getElement() ==
                (winding && i == 1 ? part.secondary() : part.primary()) && endpoint.getPostIndex() == i,
                "independent external terminal map " + names[i]);
        }
        check(part.getElectricalBacking().getCircuitElements().equals(part.elements()) && part.elements().size() == (winding ? 2 : 1),
            "one physical package owns its full actual backing");
    }
    private static PhysicalServicePart part(String id, PhysicalSpecification spec, int x, int y) {
        PhysicalPackage pkg = spec instanceof InductorSpecification ? PhysicalPackages.RADIAL_INDUCTOR_2 :
            spec instanceof ZenerSpecification ? PhysicalPackages.AXIAL_DIODE : PhysicalPackages.OPTOCOUPLER_4;
        String label = spec == ZenerSpecification.STANDARD ? "10.7 V zener diode" : "Power service fixture";
        return new PhysicalServicePart(id, spec, new PhysicalNameplate(id, label), pkg,
            PhysicalServicePart.createTypedBacking(spec,x,y), new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,id));
    }
    private static void advance(TestCirSim sim, Fixture f) {
        if (sim.circuitMatrix == null || sim.analyzeFlag) { sim.solverExecutor.analyze(); sim.analyzeFlag = false; }
        sim.solverExecutor.advanceSteps(1); f.runtime.observeSimulationTime(sim.t);
    }
    private static void dispose(Vector<CircuitElm> elements) { for (CircuitElm element : elements) element.delete(); }
    private static void failAt(final PhysicalMutationScope.FailureStage wanted) {
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage stage) { if (stage == wanted) throw new InjectedFailure(); }
        });
    }
    private static final class InjectedFailure extends RuntimeException { }
    private static void reject(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static <T extends CircuitElm> T end(T element, int x, int y) { element.x2=x; element.y2=y; element.setPoints(); return element; }

    private static final class Fixture {
        final TroubleshootBoard board = new TroubleshootBoard("POWER_SERVICE_PART_FIXTURE");
        final PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        final PhysicalServicePart original;
        final PhysicalPartInventory<PhysicalServicePart> inventory;
        final ReplaceableServiceBoardCapability capability;
        final WireElm[] leads;
        final VoltageElm source;
        final SwitchElm[] isolation;
        final GeneratedBoardInstance instance;
        Fixture(PhysicalSpecification spec) {
            original = part("P",spec,32768,32768);
            Vector<CircuitElm> elements = original.elements();
            boolean complete = false;
            try {
                board.addComponent(new BoardComponent("P","POWER_SERVICE",original.getPackage()));
                GeneratedComponentBindings components = new GeneratedComponentBindings(board);
                GeneratedComponentConnectionBindings connections = new GeneratedComponentConnectionBindings(board);
                BoardPhysicalSpecifications definitions = new BoardPhysicalSpecifications();
                leads = new WireElm[original.getTerminalCount()];
                for (int i = 0; i < leads.length; i++) {
                    String terminal = original.getTerminal(i).getTerminalName(), pad = "P."+terminal, net = "N"+i;
                    board.addNet(new BoardNet(net)); board.addPad(new BoardPad(pad,"P",terminal,net));
                    WireElm anchor = end(new WireElm(1000+i*200,1000),1016+i*200,1000); elements.add(anchor);
                    CircuitPostMeasurementEndpoint b = new CircuitPostMeasurementEndpoint(anchor,0);
                    CircuitPostMeasurementEndpoint p = (CircuitPostMeasurementEndpoint)original.getTerminal(i).getEndpoint();
                    Point point = p.getElement().getPost(p.getPostIndex());
                    leads[i] = end(new WireElm(anchor.x,anchor.y),point.x,point.y); elements.add(leads[i]);
                    board.getSimulationBindings().bindPad(pad,b); connections.bind("P",pad,b,p,leads[i]);
                }
                components.bindComponent("P",original.primary());
                if (!original.auxiliaryElements().isEmpty()) components.bindAuxiliaryComponentElements("P",original.auxiliaryElements());
                PhysicalBoardSlot slot = runtime.createSlot("P");
                inventory = new PhysicalPartInventory<PhysicalServicePart>(runtime,"P_REPLACEMENTS",PhysicalServicePart.class);
                inventory.add(original);
                capability = new ReplaceableServiceBoardCapability(new ServiceComponentSlot(slot,original,leads,new WireElm[0],null),inventory,original);
                runtime.registerCapability(capability);
                definitions.addPhysicalDefinition("P",spec,original.getPlayerVisibleNameplate(),original.getPackage());
                if (spec instanceof InductorSpecification) {
                    // This ordinary retained return prevents OFF from producing CircuitJS's no-path L reset.
                    board.addComponent(new BoardComponent("RDECAY","FIXED_DISCHARGE_RESISTOR",PhysicalPackages.AXIAL_RESISTOR));
                    BasicPhysicalSpecification decaySpec = new BasicPhysicalSpecification("FIXED_DISCHARGE_10_OHM");
                    PhysicalNameplate decayLabel = new PhysicalNameplate("RDECAY","10 ohm discharge resistor");
                    ResistorElm decay = end(new ResistorElm(36000,36000),36016,36000); decay.setResistance(10);
                    elements.add(decay); components.bindComponent("RDECAY",decay);
                    Vector<PhysicalPartTerminal> decayTerminals = new Vector<PhysicalPartTerminal>();
                    for (int i = 0; i < 2; i++) {
                        String terminal = String.valueOf(i+1), pad = "RDECAY."+terminal;
                        board.addPad(new BoardPad(pad,"RDECAY",terminal,"N"+i));
                        CircuitPostMeasurementEndpoint copper = (CircuitPostMeasurementEndpoint)board.getSimulationBindings()
                            .getEndpoint("P."+original.getTerminal(i).getTerminalName());
                        CircuitPostMeasurementEndpoint endpoint = new CircuitPostMeasurementEndpoint(decay,i);
                        Point at = copper.getElement().getPost(copper.getPostIndex()), target = decay.getPost(i);
                        WireElm lead = end(new WireElm(at.x,at.y),target.x,target.y); elements.add(lead);
                        board.getSimulationBindings().bindPad(pad,copper); connections.bind("RDECAY",pad,copper,endpoint,lead);
                        decayTerminals.add(new PhysicalPartTerminal("RDECAY",terminal,endpoint));
                    }
                    Vector<CircuitElm> decayBacking = new Vector<CircuitElm>(); decayBacking.add(decay);
                    runtime.createSlot("RDECAY").install(new FixedPhysicalPart<BasicPhysicalSpecification>("RDECAY",decaySpec,
                        decayLabel,PhysicalPackages.AXIAL_RESISTOR,decayTerminals,decayBacking,
                        new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,"RDECAY")));
                    definitions.addPhysicalDefinition("RDECAY",decaySpec,decayLabel,PhysicalPackages.AXIAL_RESISTOR);
                }
                source = end(new VoltageElm(400,1200,VoltageElm.WF_DC),400,1000); source.maxVoltage = 12;
                ResistorElm impedance = end(new ResistorElm(400,1000),600,1000); impedance.setResistance(220);
                isolation = new SwitchElm[] {
                    end(new SwitchElm(600,1000),1000,1000),
                    end(new SwitchElm(400,1200),1200,1000) };
                Vector<CircuitElm> external = new Vector<CircuitElm>();
                external.add(source); external.add(impedance); external.add(isolation[0]); external.add(isolation[1]);
                elements.addAll(external);
                GeneratedExternalPowerBindings power = new GeneratedExternalPowerBindings(board);
                board.addPowerInput(new ExternalBoardPowerInput("BENCH",
                    "P."+original.getTerminal(0).getTerminalName(),"P."+original.getTerminal(1).getTerminalName(),"N0","N1"));
                power.bindPowerInput("BENCH",new ExternalPowerSimulationBinding(external,new SwitchExternalPowerControl(isolation)));
                definitions.addPowerInputNameplate(new PowerInputNameplate("BENCH",12));
                elements.add(end(new GroundElm(1200,1000),1200,1032));
                elements.add(end(new WireElm(48000,48000),48016,48000));
                Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
                instance = new GeneratedBoardInstance(board,elements,0,"POWER_SERVICE_PART_FIXTURE","ORDINARY_POSTS","Power service contract",
                    components,power,connections,new GeneratedChallengeBehaviorContract() {
                        public void verifyHealthy(GeneratedBoardInstance owner,BoardPowerState state) { }
                        public void verifyFaulted(GeneratedBoardInstance owner,BoardModificationController changes,BoardPowerState state) { }
                        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,BoardModificationController changes,BoardPowerState state,boolean overlay) {
                            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
                        }
                        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,BoardModificationController changes,BoardPowerState state,boolean overlay) { return false; }
                    },null,definitions,null,new GeneratedComponentOperationalStates(),null,null,runtime,null,true,faults,
                    GeneratedDiagnosticSolvabilityContract.forDeveloperFixture("POWER_SERVICE_PART_FIXTURE","ORDINARY_POSTS",0,faults));
                complete = true;
            } finally { if (!complete) dispose(elements); }
        }
    }
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
        public boolean isInstalledTargetIdentityCurrent(String component,String pad,Object candidate) { return false; }
        public Object captureLooseProjectionToken() { return token; }
        public boolean isLooseProjectionTokenCurrent(Object candidate) { return candidate == token; }
        public boolean isLoosePartVisibleOnCurrentPage(String id) { return part.equals(id); }
    }
    private static final class TestCirSim extends CirSim {
        TestCirSim() {
            gridSize=16; gridMask=~15; gridRound=7; elmList=new Vector<CircuitElm>(); adjustables=new Vector<Adjustable>();
            undoStack=new Vector<String>(); redoStack=new Vector<String>();
            maxTimeStep=minTimeStep=timeStep=.00005; adjustTimeStep=false; a01MeasurementRunning=true;
        }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag=true; }
        @Override void stop(String message,CircuitElm element) { stopMessage=message; circuitMatrix=null; stopElm=element; setSimRunning(false); analyzeFlag=false; }
        @Override public void setSimRunning(boolean running) { simRunning=running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
