package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Actual capacitor/ESR service canaries; runtime reset and Q60 durable restore are deferred. */
public final class CapacitorStoragePartContractTest {
    private static final String OUTPUT = "CAP_470UF_25V_ESR_0_1_BE_ZERO";
    private static final String PRIMARY = "CAP_47UF_250V_BE_ZERO";
    private static final double DT = .00005;
    private static int assertions;
    private static final Vector<CircuitElm> scratch = new Vector<CircuitElm>();
    private interface Attempt { void run(); }

    public static void main(String[] args) {
        CirSim prior = CircuitElm.sim;
        CirSim priorSingleton = CirSim.theSim;
        TestCirSim sim = new TestCirSim();
        Vector<CircuitElm> rawGraph = sim.elmList;
        CircuitElm.sim = sim;
        Fixture first = null, reconstructed = null;
        Throwable failure = null;
        try {
            legacy();
            recipes();
            malformedBacking();
            first = new Fixture();
            attach(sim, first);
            Vector<PlayerSessionSave.Operation> actions = service(sim, first);
            sim.solverExecutor.retire();
            reconstructed = new Fixture();
            attach(sim, reconstructed);
            reconstructCatalogActions(sim, reconstructed, actions);
            check(first.original != reconstructed.original &&
                first.original.getElement() != reconstructed.original.getElement() &&
                first.original.getEsrElement() != reconstructed.original.getEsrElement(),
                "fresh fixture never reuses retired physical or solver objects");
        } catch (RuntimeException error) { failure = error; throw error; }
        catch (Error error) { failure = error; throw error;
        } finally {
            Throwable cleanup = null; boolean disposed = true;
            try {
                PhysicalMutationScope.clearFailureHookForDeveloperVerification();
                Vector<CircuitElm> owned = new Vector<CircuitElm>(scratch);
                if (first != null) owned.addAll(first.instance.getSimulationElements());
                if (reconstructed != null) owned.addAll(reconstructed.instance.getSimulationElements());
                sim.generatedBoardInstance = null; sim.boardModificationController = null; sim.elmList = rawGraph;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException error) { cleanup = error; }
                catch (Error error) { cleanup = error; }
                sim.circuitMatrix = null;
                for (CircuitElm element : owned) {
                    try { element.delete(); }
                    catch (RuntimeException error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                    catch (Error error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                }
                scratch.clear();
            } finally { CircuitElm.sim = prior; CirSim.theSim = priorSingleton; }
            boolean clean = cleanup == null && disposed && sim.elmList == rawGraph && rawGraph.isEmpty() &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == priorSingleton;
            System.out.println("CAPACITOR_STORAGE_CLEANUP {\"rawGraphRestored\":" + (sim.elmList == rawGraph && rawGraph.isEmpty()) +
                ",\"backingDisposed\":" + disposed + ",\"singletonRestored\":" + (CircuitElm.sim == prior && CirSim.theSim == priorSingleton) +
                ",\"clean\":" + clean + "}");
            if (failure != null) {
                if (cleanup != null) failure.addSuppressed(cleanup);
                if (!clean) failure.addSuppressed(new AssertionError("Capacitor storage cleanup failed"));
            } else {
                if (cleanup != null) throw new AssertionError("Capacitor storage cleanup failed", cleanup);
                check(clean, "exact graph, executor, backing disposal and singleton cleanup");
            }
        }
        System.out.println("PASS: capacitor storage-part contracts " + assertions +
            " assertions; actual native ESR/service/element restart; runtime reset and full Q60 session restore NOT RUN");
    }

    private static CapacitorSpecification output() {
        return new CapacitorSpecification(OUTPUT, .000470, 20, 25,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Low ESR electrolytic capacitor", "470 uF / 25 V"), .1, true, 0);
    }
    private static CapacitorSpecification primary() {
        return new CapacitorSpecification(PRIMARY, .000047, 20, 250,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Bulk electrolytic capacitor", "47 uF / 250 V"), 0, true, 0);
    }
    private static DynamicCapacitorBackingAllocator.Backing allocate(CapacitorSpecification spec) {
        DynamicCapacitorBackingAllocator.Backing result =
            DynamicCapacitorBackingAllocator.createBacking(scratch, spec);
        scratch.addAll(result.getElements()); return result;
    }
    private static void legacy() {
        CapacitorReplacementCatalog catalog = new CapacitorReplacementCatalog();
        String[] ids = {"C_CATALOG_33UF_16V", "C_CATALOG_1UF_16V", "C_CATALOG_220UF_16V"};
        double[] values = {33e-6, 1e-6, 220e-6};
        check(catalog.getEntries().size() == 3, "legacy electrolytic catalog population remains three");
        for (int i = 0; i < ids.length; i++) {
            CapacitorCatalogEntry row = catalog.getEntries().get(i);
            CapacitorSpecification spec = row.getSpecification();
            check(ids[i].equals(row.getId()) && spec.getCapacitanceFarads() == values[i] &&
                spec.getRatedVoltage() == 16 && spec.getTolerancePercent() == 20 &&
                !spec.hasExplicitModelRecipe() && spec.getEsrOhms() == 0,
                "frozen legacy electrolytic row " + i);
        }
        CapacitorReplacementCatalog ceramic = new CapacitorReplacementCatalog(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR);
        String[] ceramicIds = {"C_CERAMIC_10NF_25V", "C_CERAMIC_100NF_25V", "C_CERAMIC_1UF_25V"};
        double[] ceramicValues = {1e-8, 1e-7, 1e-6};
        check(ceramic.getEntries().size() == 3, "legacy ceramic catalog population remains three");
        for (int i = 0; i < ceramicIds.length; i++) check(ceramicIds[i].equals(ceramic.getEntries().get(i).getId()) &&
            ceramic.getEntries().get(i).getSpecification().getCapacitanceFarads() == ceramicValues[i] &&
            ceramic.getEntries().get(i).getSpecification().getRatedVoltage() == 25, "frozen ceramic row " + i);
        CapacitorSpecification spec = catalog.get(CapacitorReplacementCatalog.CORRECT).getSpecification();
        CapacitorElm element = DynamicCapacitorBackingAllocator.create(new Vector<CircuitElm>(), spec);
        scratch.add(element);
        check(element.x == 1200 && element.y == 2400 && element.x2 == 1216 && element.y2 == 2400 &&
            element.capacitance == 33e-6 && element.flags == 0 && element.initialVoltage == .001 && element.voltdiff == 0,
            "legacy allocator retains exact coordinates, model and unreset charge state");
        PhysicalCapacitorPart part = new PhysicalCapacitorPart("LEGACY", spec,
            spec.getNameplate().forPhysicalPartId("LEGACY"), element, null, CapacitorPartLocation.LOOSE,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, "LEGACY"));
        check(part.getEsrElement() == null && part.getElectricalBacking().getCircuitElements().size() == 1 &&
            part.hasAccessibleStoredEnergyTerminals(), "legacy constructor still owns one direct capacitor");
        check(CapacitorReplacementCatalog.forSpecification(spec).getEntries().get(0).getId().equals(ids[0]),
            "legacy specification factory retains package catalog");
    }
    private static void recipes() {
        final CapacitorSpecification output = output();
        CapacitorReplacementCatalog catalog = CapacitorReplacementCatalog.forSpecification(output);
        check(catalog.getEntries().size() == 1 && catalog.get(OUTPUT).getSpecification() == output,
            "explicit catalog preserves its exact immutable recipe and typed ID");
        Vector<CapacitorCatalogEntry> rows = catalog.getEntries();
        CapacitorReplacementCatalog copied = new CapacitorReplacementCatalog(rows); rows.clear();
        check(copied.getEntries().size() == 1 && copied.get(OUTPUT).getSpecification() == output,
            "typed catalog copies row membership without rebuilding specification");
        final Vector<CapacitorCatalogEntry> duplicates = copied.getEntries(); duplicates.add(duplicates.get(0));
        reject(new Attempt() { public void run() { new CapacitorReplacementCatalog(duplicates); } }, "duplicate typed row IDs reject");
        final CapacitorSpecification bulk = primary();
        DynamicCapacitorBackingAllocator.Backing bulkBacking = allocate(bulk);
        check(bulk.getRatedVoltage() == 250 && bulkBacking.getElements().size() == 1 &&
            bulkBacking.getEsrElement() == null && bulkBacking.getCapacitor().capacitance == .000047 &&
            bulkBacking.getCapacitor().flags == CapacitorElm.FLAG_BACK_EULER &&
            bulkBacking.getCapacitor().initialVoltage == 0 && bulkBacking.getCapacitor().voltdiff == 0,
            "47 uF/250 V acquisition retains the explicit BE/zero-reset model");
        check(CapacitorReplacementCatalog.forSpecification(bulk).get(PRIMARY).getSpecification() == bulk,
            "bulk replacement has its own typed recipe");
        reject(new Attempt() { public void run() { DynamicCapacitorBackingAllocator.create(new Vector<CircuitElm>(), output); } },
            "single-element allocator cannot silently discard explicit ESR");
        for (final double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            reject(new Attempt() { public void run() { recipe(invalid, 0); } }, "invalid ESR rejects");
        for (final double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY})
            reject(new Attempt() { public void run() { recipe(.1, invalid); } }, "invalid reset voltage rejects");
        Vector<CircuitElm> occupied = new Vector<CircuitElm>();
        for (int x : new int[] {1200, 1264, 1328}) {
            WireElm obstruction = wire(new Point(x, 2400), new Point(x, 2416));
            occupied.add(obstruction); scratch.add(obstruction);
        }
        DynamicCapacitorBackingAllocator.Backing allocated = DynamicCapacitorBackingAllocator.createBacking(occupied, output);
        scratch.addAll(allocated.getElements());
        check(allocated.getEsrElement().x == 1344 && allocated.getCapacitor().x == 1360 &&
            allocated.getCapacitor().x2 == 1376, "allocator rejects occupied positive, internal and negative posts");
        Vector<CircuitElm> copy = allocated.getElements(); copy.clear();
        check(allocated.getElements().size() == 2, "allocated backing vector is a defensive observation");
        CirSim sim = CircuitElm.sim;
        int gridSize = sim.gridSize, gridMask = sim.gridMask, gridRound = sim.gridRound;
        try {
            sim.gridSize = 32; sim.gridMask = ~31; sim.gridRound = 15;
            DynamicCapacitorBackingAllocator.Backing gridIndependent =
                DynamicCapacitorBackingAllocator.createBacking(occupied, output);
            scratch.addAll(gridIndependent.getElements());
            check(gridIndependent.getEsrElement().x2 == 1360 && gridIndependent.getCapacitor().x == 1360 &&
                gridIndependent.getCapacitor().x2 == 1376 && part("GRID", output, gridIndependent).hasAccessibleStoredEnergyTerminals(),
                "display grid cannot collapse or relocate the allocator's owned series node");
        } finally { sim.gridSize = gridSize; sim.gridMask = gridMask; sim.gridRound = gridRound; }
    }
    private static CapacitorSpecification recipe(double esr, double initial) {
        return new CapacitorSpecification("INVALID_CANARY", .000470, 20, 25,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Capacitor", "470 uF / 25 V"), esr, true, initial);
    }
    private static PhysicalCapacitorPart part(String id, CapacitorSpecification spec,
            DynamicCapacitorBackingAllocator.Backing backing) {
        return new PhysicalCapacitorPart(id, spec, spec.getNameplate().forPhysicalPartId(id),
            backing.getCapacitor(), backing.getEsrElement(), null, CapacitorPartLocation.LOOSE,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, id));
    }
    private static void malformedBacking() {
        final CapacitorSpecification spec = output();
        final DynamicCapacitorBackingAllocator.Backing backing = allocate(spec);
        final CapacitorElm capacitor = backing.getCapacitor(); final ResistorElm esr = backing.getEsrElement();
        PhysicalCapacitorPart good = part("GOOD", spec, backing);
        check(good.hasAccessibleStoredEnergyTerminals() && good.getTerminalCount() == 2,
            "one physical package exposes two verified series terminals");
        reject(new Attempt() { public void run() {
            new PhysicalCapacitorPart("MISSING", spec, spec.getNameplate().forPhysicalPartId("MISSING"),
                capacitor, null, CapacitorPartLocation.LOOSE,
                new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, "MISSING"));
        } }, "ESR recipe cannot be constructed with the legacy single backing");
        esr.setResistance(.2);
        reject(new Attempt() { public void run() { part("WRONG_R", spec, backing); } }, "mismatched actual ESR rejects");
        esr.setResistance(.1);
        capacitor.flags = 0;
        reject(new Attempt() { public void run() { part("WRONG_METHOD", spec, backing); } }, "mismatched integration recipe rejects");
        capacitor.flags = CapacitorElm.FLAG_BACK_EULER;
        capacitor.initialVoltage = .001;
        reject(new Attempt() { public void run() { part("WRONG_RESET", spec, backing); } }, "mismatched reset recipe rejects");
        capacitor.initialVoltage = 0;
        int priorX = esr.x2; esr.x2 += 16; esr.setPoints();
        reject(new Attempt() { public void run() { part("OPEN_NODE", spec, backing); } }, "disconnected internal series node rejects");
        boolean failedClosed = false;
        try { good.hasAccessibleStoredEnergyTerminals(); }
        catch (IllegalStateException expected) { failedClosed = true; }
        check(failedClosed, "changed series path cannot silently classify charged storage READY");
        esr.x2 = priorX; esr.setPoints();
    }

    private static Vector<PlayerSessionSave.Operation> service(TestCirSim sim, Fixture fixture) {
        advance(sim, fixture, 200);
        double steady = 12 * 1000 / 1022.0;
        double tau = (22 * 1000 / 1022.0 + .1) * .000470;
        double expected = steady * (1 - Math.exp(-sim.t / tau));
        near(fixture.original.getElement().getVoltageDiff(), expected, .03,
            "real ESR/capacitor charging follows independently declared RC circuit");
        double drop = fixture.original.getEsrElement().getVoltageDiff();
        check(drop > .001, "actual ESR produces a nonzero terminal drop while charging");
        near(drop, fixture.original.getElement().getCurrent() * .1, 1e-7, "external/internal voltage separation is actual I times ESR");
        double stored = fixture.original.getElement().voltdiff;
        power(sim, fixture, BoardPowerState.UNPOWERED);
        check(fixture.original.getElement().voltdiff == stored, "source OFF does not clear stored charge");
        check(readiness(sim, fixture.boardEndpoints[0], fixture.boardEndpoints[1]) != ActiveMeasurementReadiness.READY,
            "source isolation invalidates measurement readiness before a new solver sample");
        advance(sim, fixture, 1);
        check(readiness(sim, fixture.boardEndpoints[0], fixture.boardEndpoints[1]) == ActiveMeasurementReadiness.DISCHARGE,
            "installed ESR package remains real charged storage after source isolation");
        advance(sim, fixture, 40000);
        check(readiness(sim, fixture.boardEndpoints[0], fixture.boardEndpoints[1]) == ActiveMeasurementReadiness.READY,
            "real bleeder decay reaches unchanged residual-voltage threshold");
        fixture.runtime.getSessionHistory().start();
        check(fixture.controller.removeInstalledPart(), "ordinary controller removes one capacitor package");
        binding(fixture, fixture.original);
        for (CircuitElm element : fixture.original.getElectricalBacking().getCircuitElements())
            check(sim.elmList.contains(element), "loose original retains every actual backing element");
        advance(sim, fixture, 1);
        final Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        final Vector<CircuitElm> canonical = fixture.instance.getSimulationElements();
        final int parts = fixture.runtime.getPartOrder().size(), history = fixture.runtime.getSessionHistory().size();
        failAt(PhysicalMutationScope.FailureStage.AFTER_GRAPH_APPEND, 2);
        try { fixture.controller.acquireFromCatalog(OUTPUT); throw new AssertionError("Second append failure did not run"); }
        catch (InjectedFailure expectedFailure) { }
        finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
        check(graph.equals(sim.elmList) && canonical.equals(fixture.instance.getSimulationElements()) &&
            fixture.runtime.getPartOrder().size() == parts && fixture.runtime.getNextPartSerial("C1_CATALOG_PART") == null &&
            fixture.runtime.getSessionHistory().size() == history, "second backing append failure restores graph, identity and history");
        compensated(fixture); sim.needAnalyze(); advance(sim, fixture, 1);
        PhysicalCapacitorPart acquired = (PhysicalCapacitorPart)fixture.controller.acquireFromCatalog(OUTPUT);
        check("C1_CATALOG_PART_0".equals(acquired.getId()) && acquired.getSpecification() == fixture.specification &&
            acquired.getElement() != fixture.original.getElement() && acquired.getEsrElement() != fixture.original.getEsrElement(),
            "typed catalog allocates a fresh complete model under one serial");
        advance(sim, fixture, 1);
        failAt(PhysicalMutationScope.FailureStage.AFTER_AUXILIARY_BINDING, 1);
        try { fixture.controller.install(acquired.getId()); throw new AssertionError("Auxiliary failure did not run"); }
        catch (InjectedFailure expectedFailure) { }
        finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
        check(fixture.slot.isEmpty() && !acquired.isInstalled(), "failed ESR binding leaves candidate loose and slot empty");
        binding(fixture, fixture.original); compensated(fixture); sim.needAnalyze(); advance(sim, fixture, 1);
        check(fixture.controller.install(acquired.getId()), "actual C and ESR install atomically");
        binding(fixture, acquired); advance(sim, fixture, 1);
        power(sim, fixture, BoardPowerState.POWERED); advance(sim, fixture, 200);
        power(sim, fixture, BoardPowerState.UNPOWERED); advance(sim, fixture, 1);
        check(fixture.controller.removeInstalledPart(), "charged capacitor enters the existing loose service lifecycle");
        advance(sim, fixture, 1);
        check(readiness(sim, endpoint(acquired, 0), endpoint(acquired, 1)) == ActiveMeasurementReadiness.DISCHARGE &&
            acquired.hasAccessibleStoredEnergyTerminals(), "readiness follows actual charged loose ESR terminals");
        check(fixture.controller.install(fixture.original.getId()), "same original reinstalls with its retained ESR");
        binding(fixture, fixture.original); advance(sim, fixture, 1);
        acquired.getElement().reset(); acquired.getEsrElement().reset();
        fixture.original.getElement().reset(); fixture.original.getEsrElement().reset();
        check(acquired.getElement().voltdiff == 0 && fixture.original.getElement().voltdiff == 0 &&
            acquired.getEsrElement().resistance == .1 && acquired.getElement().flags == CapacitorElm.FLAG_BACK_EULER,
            "native element restart clears installed and loose charge while retaining ESR/model declarations");
        sim.needAnalyze(); advance(sim, fixture, 1);
        check(readiness(sim, endpoint(acquired, 0), endpoint(acquired, 1)) == ActiveMeasurementReadiness.READY,
            "accepted post-restart sample refreshes loose-part readiness");
        GeneratedRuntimeInvariant.verify(fixture.instance, sim.boardModificationController, sim.elmList);
        Vector<PlayerSessionSave.Operation> actions = fixture.runtime.getSessionHistory().operations();
        String[] kinds = {"REMOVE", "ACQUIRE", "INSTALL", "REMOVE", "INSTALL"};
        check(actions.size() == kinds.length, "only committed service actions enter history");
        for (int i = 0; i < kinds.length; i++) check(kinds[i].equals(actions.get(i).kind), "unchanged session vocabulary " + i);
        return actions;
    }

    /** One declared action sequence on a fresh owner, not a substitute for PlayerSessionState.restore. */
    private static void reconstructCatalogActions(TestCirSim sim, Fixture fresh, Vector<PlayerSessionSave.Operation> actions) {
        power(sim, fresh, BoardPowerState.UNPOWERED); advance(sim, fresh, 1);
        fresh.runtime.getSessionHistory().start();
        check(fresh.original.getId().equals(actions.get(0).argument) && fresh.controller.removeInstalledPart(),
            "fresh owner starts with independently expected original identity");
        advance(sim, fresh, 1);
        PhysicalCapacitorPart acquired = (PhysicalCapacitorPart)fresh.controller.acquireFromCatalog(actions.get(1).argument);
        check(acquired.getId().equals(actions.get(1).result) && acquired.getSpecification() == fresh.specification,
            "catalog action reconstruction preserves recorded physical ID and fresh typed recipe");
        advance(sim, fresh, 1);
        check(fresh.controller.install(actions.get(2).argument), "recorded acquired part installs on fresh owner");
        binding(fresh, acquired); advance(sim, fresh, 1);
        check(fresh.controller.removeInstalledPart(), "recorded replacement removal succeeds on fresh owner");
        advance(sim, fresh, 1);
        check(fresh.controller.install(actions.get(4).argument), "recorded original reinstalls on fresh owner");
        advance(sim, fresh, 1);
        Vector<PlayerSessionSave.Operation> rebuilt = fresh.runtime.getSessionHistory().operations();
        check(rebuilt.size() == actions.size(), "fresh sequence records same finite action population");
        for (int i = 0; i < actions.size(); i++) {
            PlayerSessionSave.Operation expected = actions.get(i), actual = rebuilt.get(i);
            check(expected.kind.equals(actual.kind) && expected.owner.equals(actual.owner) &&
                expected.argument.equals(actual.argument) && expected.result.equals(actual.result), "exact action identity reconstructed " + i);
        }
        check(fresh.runtime.getPartOrder().size() == 2 && Integer.valueOf(1).equals(fresh.runtime.getNextPartSerial("C1_CATALOG_PART")) &&
            acquired.getEsrElement().resistance == .1 && acquired.getElement().capacitance == .000470 &&
            acquired.getElement().initialVoltage == 0 && acquired.getElement().flags == CapacitorElm.FLAG_BACK_EULER,
            "reconstruction retains one serial per package, ESR, capacitance and restart recipe");
        GeneratedRuntimeInvariant.verify(fresh.instance, sim.boardModificationController, sim.elmList);
    }

    private static void attach(TestCirSim sim, Fixture fixture) {
        sim.generatedBoardInstance = fixture.instance; sim.elmList = fixture.instance.getSimulationElements();
        sim.boardModificationController = new BoardModificationController(sim, fixture.instance);
        sim.getBoardPowerController().attach(fixture.instance.getExternalPowerBindings());
        fixture.runtime.installRegisteredCapabilities(sim, fixture.instance, sim.boardModificationController, sim.t);
        fixture.controller = fixture.capability.getController(); sim.stopMessage = null; sim.needAnalyze();
    }
    private static void advance(TestCirSim sim, Fixture fixture, int steps) {
        if (sim.circuitMatrix == null || sim.analyzeFlag) {
            // Analysis consumes every actual post, including loose catalog backing, without drawing.
            for (CircuitElm element : sim.elmList) {
                check(element != null, "active graph contains a real element");
                check(element.boundingBox != null, "initialized drawing bounds of " + element.getClass().getSimpleName());
                for (int post = 0; post < element.getPostCount(); post++)
                    check(element.getPost(post) != null, "initialized actual post " + post + " of " + element.getClass().getSimpleName());
            }
            sim.analyzeCircuit();
            // Direct native analysis consumes the pending flag normally handled by the UI loop.
            sim.analyzeFlag = false;
        }
        for (int i = 0; i < steps; i++) sim.solverExecutor.advanceSteps(1);
        fixture.runtime.observeSimulationTime(sim.t);
        check(sim.stopMessage == null && sim.isGeneratedRuntimeSettled(), "actual solver settles each declared fixture transition");
    }
    private static void power(TestCirSim sim, Fixture fixture, BoardPowerState state) {
        sim.getBoardPowerController().setState(state); fixture.runtime.onBoardPowerStateChanged(state); sim.needAnalyze();
    }
    private static ActiveMeasurementReadiness readiness(TestCirSim sim, CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) { return sim.getActiveMeasurementReadiness(red, black); }
    private static CircuitPostMeasurementEndpoint endpoint(PhysicalCapacitorPart part, int index) {
        return (CircuitPostMeasurementEndpoint)part.getTerminal(index).getEndpoint();
    }
    private static void binding(Fixture fixture, PhysicalCapacitorPart expected) {
        check(fixture.instance.getComponentBindings().getSingleElement("C1") == expected.getElement() &&
            fixture.instance.getComponentBindings().getAuxiliaryElements("C1").size() == 1 &&
            fixture.instance.getComponentBindings().getAuxiliaryElements("C1").get(0) == expected.getEsrElement(),
            "slot has exact primary capacitor and owned ESR auxiliary");
        for (int i = 0; i < 2; i++) {
            String pad = i == 0 ? "C1.+" : "C1.-";
            GeneratedComponentConnectionBinding binding = fixture.instance.getConnectionBindings().get("C1", pad);
            check(binding.getBoardEndpoint() == fixture.boardEndpoints[i] &&
                GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), expected.getTerminal(i).getEndpoint()),
                "persistent copper and exact physical package endpoint " + i);
        }
    }
    private static void compensated(Fixture fixture) {
        check(fixture.runtime.getLastMutationReceipt().isCompensated() && !fixture.runtime.isMutationInProgress() &&
            !fixture.runtime.isMutationQuarantined(), "failed storage transaction compensates and releases owner");
    }
    private static final class InjectedFailure extends RuntimeException { }
    private static void failAt(final PhysicalMutationScope.FailureStage requested, final int occurrence) {
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            int seen;
            public void afterStage(PhysicalMutationScope.FailureStage actual) {
                if (actual == requested && ++seen == occurrence) throw new InjectedFailure();
            }
        });
    }
    private static void reject(Attempt attempt, String message) {
        boolean rejected = false; try { attempt.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    private static void near(double actual, double expected, double tolerance, String message) {
        check(!Double.isNaN(actual) && !Double.isInfinite(actual) && Math.abs(actual - expected) <= tolerance,
            message + ": actual=" + actual + "; expected=" + expected);
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static <T extends CircuitElm> T end(T element, int x, int y) {
        element.x2 = x; element.y2 = y; element.setPoints(); return element;
    }
    private static WireElm wire(Point a, Point b) { return end(new WireElm(a.x, a.y), b.x, b.y); }

    private static final class Fixture {
        final TroubleshootBoard board = new TroubleshootBoard("CAPACITOR_ESR_SERVICE_FIXTURE");
        final PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        final CapacitorSpecification specification = output();
        final CircuitPostMeasurementEndpoint[] boardEndpoints = new CircuitPostMeasurementEndpoint[2];
        final PhysicalCapacitorPart original;
        final CapacitorComponentSlot slot;
        final ReplaceableCapacitorBoardCapability capability;
        final GeneratedBoardInstance instance;
        CapacitorSlotController controller;
        Fixture() {
            Vector<CircuitElm> elements = new Vector<CircuitElm>(); boolean complete = false;
            try {
                DynamicCapacitorBackingAllocator.Backing backing = DynamicCapacitorBackingAllocator.createBacking(elements, specification);
                elements.addAll(backing.getElements());
                original = new PhysicalCapacitorPart("C1_ORIGINAL", specification,
                    specification.getNameplate().forPhysicalPartId("C1_ORIGINAL"), backing.getCapacitor(), backing.getEsrElement(), null,
                    CapacitorPartLocation.INSTALLED, new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "C1"));
                board.addComponent(new BoardComponent("C1", "CAPACITOR", specification.getPhysicalPackage()));
                board.addNet(new BoardNet("OUT")); board.addNet(new BoardNet("RETURN"));
                board.addPad(new BoardPad("C1.+", "C1", "+", "OUT"));
                board.addPad(new BoardPad("C1.-", "C1", "-", "RETURN"));
                Point[] anchors = {new Point(160, 0), new Point(160, 96)};
                GeneratedComponentBindings components = new GeneratedComponentBindings(board);
                GeneratedComponentConnectionBindings connections = new GeneratedComponentConnectionBindings(board);
                WireElm[] leads = new WireElm[2];
                for (int i = 0; i < 2; i++) {
                    WireElm anchor = wire(anchors[i], new Point(176, anchors[i].y)); elements.add(anchor);
                    boardEndpoints[i] = new CircuitPostMeasurementEndpoint(anchor, 0);
                    Point partPoint = endpoint(original, i).getElement().getPost(endpoint(original, i).getPostIndex());
                    leads[i] = i == 0 ? wire(anchors[i], partPoint) : wire(partPoint, anchors[i]); elements.add(leads[i]);
                    String pad = i == 0 ? "C1.+" : "C1.-";
                    board.getSimulationBindings().bindPad(pad, boardEndpoints[i]);
                    connections.bind("C1", pad, boardEndpoints[i], original.getTerminal(i).getEndpoint(), leads[i]);
                }
                components.bindComponent("C1", original.getElement());
                components.bindAuxiliaryComponentElement("C1", original.getEsrElement());
                PhysicalPartInventory<PhysicalCapacitorPart> inventory = new PhysicalPartInventory<PhysicalCapacitorPart>(
                    runtime, "C1_REPLACEMENTS", PhysicalCapacitorPart.class); inventory.add(original);
                slot = new CapacitorComponentSlot("C1", specification, original, leads[0], leads[1], runtime.createSlot("C1"));
                capability = new ReplaceableCapacitorBoardCapability(slot, inventory, CapacitorReplacementCatalog.forSpecification(specification));
                runtime.registerCapability(capability);
                slot.getPhysicalSlot().bindGeometryRealization(PcbFootprint.fromPhysicalPackage(board.getComponent("C1"), 100, 100).getPlacement());
                BoardPhysicalSpecifications definitions = new BoardPhysicalSpecifications();
                definitions.addPhysicalDefinition("C1", specification, original.getPlayerVisibleNameplate(), specification.getPhysicalPackage());
                VoltageElm source = end(new VoltageElm(0, 96, VoltageElm.WF_DC), 0, 0); source.maxVoltage = 12;
                ResistorElm impedance = end(new ResistorElm(0, 0), 48, 0); impedance.setResistance(22);
                SwitchElm line = end(new SwitchElm(48, 0), 160, 0), returned = end(new SwitchElm(0, 96), 160, 96);
                Vector<CircuitElm> external = new Vector<CircuitElm>();
                external.add(source); external.add(impedance); external.add(line); external.add(returned); elements.addAll(external);
                GeneratedExternalPowerBindings power = new GeneratedExternalPowerBindings(board);
                board.addPowerInput(new ExternalBoardPowerInput("BENCH", "C1.+", "C1.-", "OUT", "RETURN"));
                power.bindPowerInput("BENCH", new ExternalPowerSimulationBinding(external,
                    new SwitchExternalPowerControl(new SwitchElm[] {line, returned})));
                definitions.addPowerInputNameplate(new PowerInputNameplate("BENCH", 12));
                ResistorElm bleed = end(new ResistorElm(160, 0), 160, 96); bleed.setResistance(1000); elements.add(bleed);
                elements.add(end(new GroundElm(160, 96), 160, 128));
                runtime.registerCapability(new StoredEnergyMeasurementReadinessCapability(runtime, board.getSimulationBindings()));
                Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
                instance = new GeneratedBoardInstance(board, elements, 0, "CAPACITOR_ESR_SERVICE_FIXTURE", "SERIES_ESR",
                    "Native capacitor service canary", components, power, connections, new GeneratedChallengeBehaviorContract() {
                        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { }
                        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state) { }
                        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner, BoardModificationController changes,
                                BoardPowerState state, boolean overlay) { return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL; }
                        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner, BoardModificationController changes,
                                BoardPowerState state, boolean overlay) { return false; }
                    }, null, definitions, null, new GeneratedComponentOperationalStates(), null, null, runtime, null, true, faults,
                    GeneratedDiagnosticSolvabilityContract.forDeveloperFixture("CAPACITOR_ESR_SERVICE_FIXTURE", "SERIES_ESR", 0, faults));
                complete = true;
            } finally { if (!complete) for (CircuitElm element : elements) element.delete(); }
        }
    }
    private static final class TestCirSim extends CirSim {
        TestCirSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>(); undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = DT; adjustTimeStep = false;
        }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message; circuitMatrix = null; stopElm = element; setSimRunning(false); analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
