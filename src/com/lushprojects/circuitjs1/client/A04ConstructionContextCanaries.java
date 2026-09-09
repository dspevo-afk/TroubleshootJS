package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent clients of the real A04 allocation/binding boundary. */
final class A04ConstructionContextCanaries {
    static final class Result {
        final int contractAssertions;
        final int solverAssertions;
        final int negativeCases;
        Result(int contracts, int solver, int negatives) {
            contractAssertions = contracts;
            solverAssertions = solver;
            negativeCases = negatives;
        }
    }

    private final CirSim sim;
    private final Task41SimulationSnapshot original;
    private final GeneratedBoardInstance protectedOwner;
    private final Vector<ElectricalConstructionContext> owned =
        new Vector<ElectricalConstructionContext>();
    private int contracts;
    private int solver;
    private int negatives;

    private A04ConstructionContextCanaries(CirSim sim) {
        this.sim = sim;
        protectedOwner = sim.getGeneratedBoardInstance();
        original = Task41SimulationSnapshot.capture(sim);
    }

    static Result verify(CirSim sim) {
        return new A04ConstructionContextCanaries(sim).run();
    }

    private Result run() {
        Throwable failure = null;
        try {
            sameContextCoordinateAndBridgeProof();
            multiTerminalAllocationOrder();
            sameBoardAbortIsolation();
            equalValuedAttemptBoundary();
            scopedOwnershipNegatives();
            completionNegatives();
            completedReceiptRevocation();
            injectedBoundaries();
        } catch (Throwable problem) {
            failure = problem;
        } finally {
            try { original.restore(sim); }
            catch (Throwable problem) { failure = retain(failure, problem); }
            for (int index = owned.size() - 1; index >= 0; index--) {
                try { close(owned.get(index)); }
                catch (Throwable problem) { failure = retain(failure, problem); }
            }
            try {
                original.assertRestored(sim);
                check(protectedOwner == sim.getGeneratedBoardInstance(),
                    "context tests changed the protected owner");
            } catch (Throwable problem) { failure = retain(failure, problem); }
        }
        if (failure != null) rethrow(failure);
        check(solver >= 5 && negatives >= 10,
            "context corpus did not execute its required proof classes");
        return new Result(contracts, solver, negatives);
    }

    private ElectricalConstructionContext begin(BoundedAssemblyPlan plan,
            ElectricalConstructionContext.FailureProbe probe) {
        ElectricalConstructionContext context = beginOnBoard(plan,
            A04ConstructionDeveloperVerifier.contextBoard(plan.getElectricalRealizationSpec()),
            probe);
        owned.add(context);
        return context;
    }

    private ElectricalConstructionContext beginOnBoard(BoundedAssemblyPlan plan,
            TroubleshootBoard board, ElectricalConstructionContext.FailureProbe probe) {
        return ElectricalConstructionContext.begin(plan.getElectricalRealizationSpec(), board,
            probe);
    }

    private void close(ElectricalConstructionContext context) {
        if (!context.abort(null))
            throw new IllegalStateException("A04 context did not certify candidate cleanup");
    }

    private BoundedAssemblyPlan resistive() {
        return BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forCanary(1L));
    }

    /** A failed speculative context may revoke only its own shared board pads. */
    private void sameBoardAbortIsolation() {
        final BoundedAssemblyPlan plan = resistive();
        final TroubleshootBoard board = A04ConstructionDeveloperVerifier.contextBoard(
            plan.getElectricalRealizationSpec());
        final ElectricalConstructionContext first = beginOnBoard(plan, board, null);
        final ElectricalConstructionContext second = beginOnBoard(plan, board, null);
        owned.add(first);
        owned.add(second);
        constructResistive(plan, first, false);
        final ConstructionReceipt firstReceipt = first.getReceipt();
        String sourcePad = plan.idFor("source", FunctionalBlockDescriptor.EntityKind.PAD,
            "R1.1");
        CircuitMeasurementEndpoint firstEndpoint = board.getSimulationBindings().getEndpoint(
            sourcePad);
        check(firstEndpoint != null,
            "first context did not install its shared board endpoint");
        check(firstReceipt.getElements().size() == first.getAllocatedElementCount(),
            "first context receipt was not usable before the competing abort");
        String sourceId = plan.idFor("source", FunctionalBlockDescriptor.EntityKind.COMPONENT,
            "R1");
        check(firstReceipt.getComponentBindings().hasComponentBinding(sourceId),
            "first context receipt lost its component binding before the competing abort");
        board.getSimulationBindings().markDeveloperVerificationReady();
        check(board.getSimulationBindings().isDeveloperVerificationReady(),
            "first context did not establish board readiness");
        check(second.abort(null), "second context did not certify its cleanup");
        check(board.getSimulationBindings().getEndpoint(sourcePad) == firstEndpoint,
            "aborting a second context revoked the first context's board endpoint");
        check(firstReceipt.getElements().size() == first.getAllocatedElementCount()
                && firstReceipt.getComponentBindings().hasComponentBinding(sourceId),
            "aborting a second context revoked the first context receipt");
        check(board.getSimulationBindings().isDeveloperVerificationReady(),
            "aborting a second context invalidated first context readiness");
        close(first);
        check(board.getSimulationBindings().getEndpoint(sourcePad) == null,
            "first context cleanup did not revoke its own board endpoint");
        check(!board.getSimulationBindings().isDeveloperVerificationReady(),
            "first context cleanup left stale board readiness");
    }

    /** Equal-valued plans still require exact metadata, runtime, and receipt ownership. */
    private void equalValuedAttemptBoundary() {
        final BoundedAssemblyPlan firstPlan = resistive();
        final BoundedAssemblyPlan secondPlan = resistive();
        check(firstPlan != secondPlan && firstPlan.getSemanticSignature().equals(
            secondPlan.getSemanticSignature()),
            "equal-valued construction attempts were not independently allocated");
        final PhysicalConstructionMetadata firstMetadata =
            PhysicalConstructionMaterializer.describe(firstPlan);
        final PhysicalConstructionMetadata secondMetadata =
            PhysicalConstructionMaterializer.describe(secondPlan);
        final ElectricalConstructionContext first = beginOnBoard(firstPlan,
            firstMetadata.getBoard(), null);
        final ElectricalConstructionContext second = beginOnBoard(secondPlan,
            secondMetadata.getBoard(), null);
        owned.add(first);
        owned.add(second);
        constructResistive(firstPlan, first, false);
        constructResistive(secondPlan, second, false);
        final ConstructionReceipt firstReceipt = first.getReceipt();
        final ConstructionReceipt secondReceipt = second.getReceipt();
        check(first.getReceipt() == firstReceipt && second.getReceipt() == secondReceipt,
            "construction context did not retain its canonical issued receipt");
        check(firstReceipt.belongsToFinishedContext(firstPlan.getElectricalRealizationSpec(),
                firstMetadata.getBoard()), "first receipt lost exact context ownership");
        check(secondReceipt.belongsToFinishedContext(secondPlan.getElectricalRealizationSpec(),
                secondMetadata.getBoard()), "second receipt lost exact context ownership");

        final ConstructionReceipt forgedReceipt = new ConstructionReceipt(
            firstReceipt.getSpec(), secondReceipt.getElements(),
            new java.util.TreeMap<String, ElectricalConstructionContext.ElementHandle>(),
            new java.util.TreeMap<String, ElectricalConstructionContext.SecondaryHandle>(),
            new java.util.TreeMap<String, ContributionConstructionReceipt>(),
            secondReceipt.getComponentBindings(), secondReceipt.getPowerBindings(),
            secondReceipt.getConnectionBindings(), secondReceipt.getDeviceReceipt(),
            secondReceipt.getAllocatedElementCount(), secondReceipt.getBindingCount(),
            secondReceipt.getJoinCount(), secondReceipt.getBridgeCounts(), false, null, first);
        check(!forgedReceipt.belongsToFinishedContext(firstPlan.getElectricalRealizationSpec(),
                firstMetadata.getBoard()), "a copied receipt acquired issued-receipt authority");
        PhysicalBoardRuntime forgedRuntime = new PhysicalBoardRuntime(firstMetadata.getBoard());
        boolean rejectedAtOwnershipBoundary = false;
        try {
            PhysicalConstructionMaterializer.materialize(forgedRuntime, firstMetadata,
                firstPlan, forgedReceipt);
        } catch (IllegalArgumentException expected) {
            rejectedAtOwnershipBoundary = expected.getMessage().contains(
                "receipt belongs to another construction context");
        }
        check(rejectedAtOwnershipBoundary,
            "forged receipt was not rejected at the pre-mutation ownership boundary");
        check(forgedRuntime.getSlots().isEmpty() && forgedRuntime.getPhysicalParts().isEmpty(),
            "forged receipt mutated the physical runtime before rejection");

        final PhysicalBoardRuntime foreignRuntime = new PhysicalBoardRuntime(
            secondMetadata.getBoard());
        reject(new Runnable() { public void run() {
            PhysicalConstructionMaterializer.materialize(foreignRuntime, firstMetadata,
                firstPlan, firstReceipt);
        } }, "runtime from another equal-valued attempt");

        final PhysicalBoardRuntime foreignMetadataRuntime = new PhysicalBoardRuntime(
            firstMetadata.getBoard());
        reject(new Runnable() { public void run() {
            PhysicalConstructionMaterializer.materialize(foreignMetadataRuntime,
                secondMetadata, firstPlan, firstReceipt);
        } }, "metadata from another equal-valued attempt");

        final PhysicalBoardRuntime foreignReceiptRuntime = new PhysicalBoardRuntime(
            secondMetadata.getBoard());
        reject(new Runnable() { public void run() {
            PhysicalConstructionMaterializer.materialize(foreignReceiptRuntime,
                secondMetadata, secondPlan, firstReceipt);
        } }, "receipt from another equal-valued attempt");

        PhysicalBoardRuntime validRuntime = new PhysicalBoardRuntime(firstMetadata.getBoard());
        PhysicalMaterializationReceipt materialized = PhysicalConstructionMaterializer.materialize(
            validRuntime, firstMetadata, firstPlan, firstReceipt);
        check(materialized.getConstructionReceipt() == firstReceipt
                && materialized.getRuntime() == validRuntime,
            "valid physical materialization lost exact attempt identity");
        close(second);
        close(first);
    }

    /** Derived NMOS posts must be inspected only after owned configuration. */
    private void multiTerminalAllocationOrder() {
        final BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
            BoundedAssemblyRequest.forControlledIndicator(2L));
        final ElectricalConstructionContext context = begin(plan, null);
        final ElectricalConstructionContext.Scope driver = context.scope("driver",
            ControlledIndicatorBlockContributions.DRIVER_TYPE_ID, 1);
        final ElectricalConstructionContext.ElementHandle q1 = driver.nmos(
            "Q1", 720, 288, 800, 288, 1.5, 10.0);
        Point gate = driver.point(q1, 0);
        Point source = driver.point(q1, 1);
        Point drain = driver.point(q1, 2);
        check(!gate.equals(source) && !gate.equals(drain) && !source.equals(drain),
            "owned NMOS configuration did not produce three distinct posts");
        check(context.getAllocatedElementCount() == 1,
            "NMOS was not tracked exactly once before configuration");
        close(context);
        final ElectricalConstructionContext raw = begin(plan, null);
        final ElectricalConstructionContext.Scope rawDriver = raw.scope("driver",
            ControlledIndicatorBlockContributions.DRIVER_TYPE_ID, 1);
        final ElectricalConstructionContext.ElementHandle rawQ1 = rawDriver.allocate("Q1");
        reject(new Runnable() { public void run() { rawDriver.point(rawQ1, 1); } },
            "reading an unconfigured multi-terminal post");
        reject(new Runnable() { public void run() { rawDriver.terminal("Q1", "S"); } },
            "binding an unconfigured multi-terminal post");
        close(raw);
        final IllegalStateException injected = new IllegalStateException("A04 NMOS allocation failure");
        final ElectricalConstructionContext interrupted = begin(plan,
            new ElectricalConstructionContext.FailureProbe() {
                public void after(ElectricalConstructionContext.Boundary boundary) {
                    if (boundary == ElectricalConstructionContext.Boundary.ALLOCATE)
                        throw injected;
                }
            });
        final ElectricalConstructionContext.Scope interruptedDriver = interrupted.scope("driver",
            ControlledIndicatorBlockContributions.DRIVER_TYPE_ID, 1);
        Throwable observed = null;
        try { interruptedDriver.nmos("Q1", 720, 288, 800, 288, 1.5, 10.0); }
        catch (Throwable problem) { observed = problem; }
        check(observed == injected && interrupted.getAllocatedElementCount() == 1,
            "NMOS allocation failure escaped ownership or was masked by uninitialized posts");
        close(interrupted);
        original.assertRestored(sim);
    }

    /** Identical local inputs, one context, actual CircuitJS node analysis. */
    private void sameContextCoordinateAndBridgeProof() {
        BoundedAssemblyPlan plan = resistive();
        final ElectricalConstructionContext context = begin(plan, null);
        ElectricalConstructionContext.Scope source = context.scope("source",
            ResistiveBlockContributions.SOURCE_TYPE_ID, 1);
        ElectricalConstructionContext.Scope load = context.scope("load",
            ResistiveBlockContributions.LOAD_TYPE_ID, 1);
        final ElectricalConstructionContext.ElementHandle a = source.resistor(
            "R1", 260, 160, 340, 160, plan.getBlocks().get("source").getResistanceOhms());
        final ElectricalConstructionContext.ElementHandle b = load.resistor(
            "R1", 260, 160, 340, 160, plan.getBlocks().get("load").getResistanceOhms());
        source.secondaryOpenPath("R1_SECONDARY", a, 1);
        final ElectricalConstructionContext.DeviceScope device = context.deviceScope("device");
        final ElectricalConstructionContext.ElementHandle output = device.wire(
            "OUTPUT_TRACE", 420, 160, 500, 160);
        Point localA = source.point(a, 0);
        Point localB = load.point(b, 0);
        check(localA.equals(localB), "same local-coordinate inputs were not exercised");
        final ElectricalConstructionContext.TerminalHandle aIn = source.terminal("R1", "1");
        final ElectricalConstructionContext.TerminalHandle aOut = source.terminal("R1_SECONDARY", "2");
        final ElectricalConstructionContext.TerminalHandle bIn = load.terminal("R1", "1");
        check(!aIn.getPoint().equals(bIn.getPoint()),
            "different owners share a world point for identical local coordinates");
        Point beforeCopy = aIn.getPoint();
        int savedX = beforeCopy.x;
        beforeCopy.x += 64;
        check(aIn.getPoint().x == savedX, "terminal inspection exposed a mutable solver point");
        reject(new Runnable() { public void run() { a.getElement(); } },
            "provider raw-element access before global completion");
        int count = context.getAllocatedElementCount();
        reject(new Runnable() { public void run() {
            device.join("SOURCE_SECOND_ATTACHMENT", aIn, device.terminal(output, "1"));
        } }, "wrong terminal pair with otherwise permitted owners");
        check(context.getAllocatedElementCount() == count,
            "rejected bridge allocated an undeclared wire");
        reject(new Runnable() { public void run() {
            device.wire("SOURCE_SECOND_ATTACHMENT", 100, 100, 200, 100);
        } }, "raw-coordinate bypass of a declared cross-owner bridge");
        analyze(context);
        CircuitElm primaryA = context.getElementForDeveloperVerification("source", "R1");
        CircuitElm primaryB = context.getElementForDeveloperVerification("load", "R1");
        CircuitElm secondary = context.getElementForDeveloperVerification("source", "R1_SECONDARY");
        solved(primaryA.getNode(0) != primaryB.getNode(0),
            "CircuitJS joined equal local coordinates before a device bridge");
        solved(secondary.getNode(1) != primaryB.getNode(0),
            "unconnected declared ports already share a CircuitJS node");
        original.restore(sim);
        original.assertRestored(sim);
        device.join("SOURCE_SECOND_ATTACHMENT", aOut, device.terminal(output, "1"));
        device.join("LOAD_FIRST_ATTACHMENT", device.terminal(output, "2"), bIn);
        analyze(context);
        solved(secondary.getNode(1) == primaryB.getNode(0),
            "permitted bridges did not create the intended CircuitJS connection");
        solved(primaryA.getNode(0) != primaryB.getNode(0),
            "the signal bridge also shorted the unrelated source input");
        original.restore(sim);
        original.assertRestored(sim);
        close(context);
    }

    private void analyze(ElectricalConstructionContext context) {
        // Restore the protected owner before borrowing the single solver again.
        original.restore(sim);
        original.assertRestored(sim);
        sim.elmList = context.getElementsForDeveloperVerification();
        sim.analyzeCircuit();
        solved(sim.stopMessage == null && sim.nodeList != null && !sim.nodeList.isEmpty(),
            "private coordinate fixture did not complete CircuitJS analysis");
    }

    private ElectricalConstructionContext.Scope source(ElectricalConstructionContext context) {
        return context.scope("source", ResistiveBlockContributions.SOURCE_TYPE_ID, 1);
    }

    private void scopedOwnershipNegatives() {
        final BoundedAssemblyPlan plan = resistive();
        final ElectricalConstructionContext context = begin(plan, null);
        reject(new Runnable() { public void run() {
            context.scope("source", "unknown-provider", 1);
        } }, "unknown provider");
        reject(new Runnable() { public void run() {
            context.scope("source", ResistiveBlockContributions.SOURCE_TYPE_ID, 99);
        } }, "unsupported provider version");
        final ElectricalConstructionContext.Scope scope = source(context);
        final ElectricalConstructionContext.ElementHandle handle = scope.resistor(
            "R1", 260, 160, 340, 160, plan.getBlocks().get("source").getResistanceOhms());
        final ElectricalConstructionContext.SecondaryHandle secondary =
            scope.secondaryOpenPath("R1_SECONDARY", handle, 1);
        scope.bindComponent("R1", handle, secondary.getElementHandle());
        reject(new Runnable() { public void run() {
            scope.bindComponent("R1", handle, secondary.getElementHandle());
        } }, "duplicate component binding");
        reject(new Runnable() { public void run() {
            scope.resistor("R1", 260, 160, 340, 160,
                plan.getBlocks().get("source").getResistanceOhms());
        } }, "duplicate allocation");
        reject(new Runnable() { public void run() { scope.terminal("R1", "missing"); } },
            "undeclared terminal");
        reject(new Runnable() { public void run() { scope.allocate("UNDECLARED"); } },
            "undeclared element");
        final ElectricalConstructionContext.Scope load = context.scope("load",
            ResistiveBlockContributions.LOAD_TYPE_ID, 1);
        reject(new Runnable() { public void run() {
            load.bindComponent("R1", handle, null);
        } }, "foreign owner handle");
        final ElectricalConstructionContext other = begin(plan, null);
        final ElectricalConstructionContext.Scope otherSource = source(other);
        reject(new Runnable() { public void run() {
            otherSource.bindComponent("R1", handle, null);
        } }, "same-owner handle from a different construction attempt");
        scope.declareUnit("R1");
        reject(new Runnable() { public void run() { scope.declareUnit("R1"); } },
            "duplicate physical unit");
        scope.finish();
        reject(new Runnable() { public void run() { scope.bindComponent("R1", handle, null); } },
            "binding through a completed local scope");
        close(context);
        reject(new Runnable() { public void run() { scope.allocate("R1"); } },
            "allocation through an aborted scope");
        reject(new Runnable() { public void run() { handle.getElement(); } },
            "raw access through an aborted element handle");
        final ElectricalConstructionContext wrongValue = begin(plan, null);
        final ElectricalConstructionContext.Scope wrongValueScope = source(wrongValue);
        reject(new Runnable() { public void run() {
            wrongValueScope.resistor("R1", 260, 160, 340, 160,
                plan.getBlocks().get("source").getResistanceOhms() * 2.0);
        } }, "construction substituted a different resolved resistance");
    }

    private void completionNegatives() {
        final BoundedAssemblyPlan plan = resistive();
        final int expectedElementCount = plan.getElectricalRealizationSpec()
            .getElementDeclarations().size();
        final ElectricalConstructionContext missingComponent = begin(plan, null);
        final ElectricalConstructionContext.Scope scope = source(missingComponent);
        ElectricalConstructionContext.ElementHandle resistor = scope.resistor(
            "R1", 260, 160, 340, 160, plan.getBlocks().get("source").getResistanceOhms());
        scope.secondaryOpenPath("R1_SECONDARY", resistor, 1);
        scope.declareUnit("R1");
        reject(new Runnable() { public void run() { scope.finish(); } },
            "local completion without its component binding");
        final ElectricalConstructionContext missingPad = begin(plan, null);
        reject(new Runnable() { public void run() {
            constructResistive(plan, missingPad, true);
        } }, "global completion with every element but one missing board pad");
        check(missingPad.getAllocatedElementCount() == expectedElementCount,
            "missing-pad negative did not reach the complete allocation inventory");
    }

    private void completedReceiptRevocation() {
        BoundedAssemblyPlan plan = resistive();
        final int expectedElementCount = plan.getElectricalRealizationSpec()
            .getElementDeclarations().size();
        final int expectedUnitCount = plan.getElectricalRealizationSpec()
            .getPackageMap().getUnitCount();
        final ElectricalConstructionContext context = begin(plan, null);
        constructResistive(plan, context, false);
        final ConstructionReceipt receipt = context.getReceipt();
        final ElectricalConstructionContext.ElementHandle retained =
            receipt.getElement("source", "R1");
        final GeneratedComponentBindings oldComponents = receipt.getComponentBindings();
        final GeneratedExternalPowerBindings oldPower = receipt.getPowerBindings();
        final GeneratedComponentConnectionBindings oldConnections = receipt.getConnectionBindings();
        final TroubleshootBoard oldBoard = oldComponents.getBoardForRuntimeValidation();
        final BoardSimulationBindings oldPads = oldBoard.getSimulationBindings();
        final CircuitElm oldElement = retained.getElement();
        for (GeneratedComponentConnectionBinding binding : oldConnections.getAll())
            check(binding.getBoardEndpoint() == oldPads.getEndpoint(binding.getPadId()),
                "detachable connection copied the canonical persistent board endpoint");
        final String sourceId = plan.idFor("source", FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1");
        final String sourcePad = plan.idFor("source", FunctionalBlockDescriptor.EntityKind.PAD, "R1.1");
        check(context.isFinished() && receipt.getElements().size() == expectedElementCount,
            "completed context lost its exact allocation inventory");
        check(receipt.getContribution("source").getUnits().size() +
            receipt.getContribution("load").getUnits().size() +
            receipt.getDeviceReceipt().getUnits().size() == expectedUnitCount,
            "completed context silently omitted a physical unit");
        reject(new Runnable() { public void run() {
            context.scope("source", ResistiveBlockContributions.SOURCE_TYPE_ID, 1);
        } }, "new scope after global completion");
        close(context);
        check(context.isAborted() && context.getAllocatedElementCount() == expectedElementCount,
            "abort lost its frozen allocation accounting");
        reject(new Runnable() { public void run() { receipt.getElements(); } },
            "old receipt exporting a disposed candidate graph");
        reject(new Runnable() { public void run() { retained.getElement(); } },
            "old completed handle exporting a disposed element");
        reject(new Runnable() { public void run() { receipt.getComponentBindings(); } },
            "old receipt exporting component bindings after abort");
        reject(new Runnable() { public void run() { receipt.getPowerBindings(); } },
            "old receipt exporting external power controls after abort");
        reject(new Runnable() { public void run() { receipt.getConnectionBindings(); } },
            "old receipt exporting detachable bindings after abort");
        check(receipt.isAborted(), "old receipt does not expose its current revocation state");
        check(!oldComponents.hasComponentBinding(sourceId) &&
            !oldPower.hasControlsForAllInputs() && oldConnections.getAll().isEmpty() &&
            oldPads.getEndpoint(sourcePad) == null,
            "previously exported private registries retain disposed live objects");
        reject(new Runnable() { public void run() {
            oldComponents.bindComponent(sourceId, oldElement);
        } }, "repopulation of revoked component bindings");
        reject(new Runnable() { public void run() {
            oldPads.bindPad(sourcePad, new CircuitPostMeasurementEndpoint(oldElement, 0));
        } }, "repopulation of revoked pad bindings");
        reject(new Runnable() { public void run() {
            ElectricalConstructionContext.begin(receipt.getSpec(), oldBoard, null);
        } }, "reuse of an aborted mutable board owner");

        reject(new Runnable() { public void run() { context.getElements(); } },
            "context exporting live state after abort");
    }

    private void injectedBoundaries() {
        final BoundedAssemblyPlan plan = resistive();
        for (final ElectricalConstructionContext.Boundary boundary :
                new ElectricalConstructionContext.Boundary[] {
                    ElectricalConstructionContext.Boundary.ALLOCATE,
                    ElectricalConstructionContext.Boundary.BIND,
                    ElectricalConstructionContext.Boundary.JOIN,
                    ElectricalConstructionContext.Boundary.FINISH }) {
            final IllegalStateException injected = new IllegalStateException(
                "A04 injected construction boundary " + boundary.name());
            final int targetHit = boundary == ElectricalConstructionContext.Boundary.ALLOCATE ? 2 : 1;
            final int[] hits = { 0 };
            final ElectricalConstructionContext context = begin(plan,
                new ElectricalConstructionContext.FailureProbe() {
                    public void after(ElectricalConstructionContext.Boundary actual) {
                        if (actual == boundary && ++hits[0] == targetHit) throw injected;
                    }
                });
            Throwable observed = null;
            try { constructResistive(plan, context, false); }
            catch (Throwable problem) { observed = problem; }
            check(observed == injected && hits[0] == targetHit,
                "failure injection did not reach its actual " + boundary + " boundary");
            close(context);
            check(context.isAborted() && context.getAllocatedElementCount() > 0,
                "failed construction did not retain truthful allocation accounting");
            original.assertRestored(sim);
            final ElectricalConstructionContext fresh = begin(plan, null);
            constructResistive(plan, fresh, false);
            check(fresh.isFinished(), "fresh construction failed after " + boundary);
            close(fresh);
        }
    }

    private ContributionConstructionReceipt contribute(BoundedAssemblyPlan plan,
            ElectricalConstructionContext context, String owner) {
        ElectricalRealizationSpec.ProviderDeclaration declaration =
            plan.getElectricalRealizationSpec().getProviderDeclaration(owner);
        return StandardElectricalConstructionProviders.provider(
            declaration.getProviderId(), declaration.getProviderVersion()).construct(
                declaration, context.scope(owner, declaration.getProviderId(),
                    declaration.getProviderVersion()));
    }

    /** Independent explicit client; omitting one pad must not yield a receipt. */
    private void constructResistive(BoundedAssemblyPlan plan,
            ElectricalConstructionContext context, boolean omitReturnPad) {
        ContributionConstructionReceipt source = contribute(plan, context, "source");
        ContributionConstructionReceipt load = contribute(plan, context, "load");
        ElectricalConstructionContext.DeviceScope d = context.deviceScope("device");
        ElectricalConstructionContext.ElementHandle supply = d.voltageSource(
            "SUPPLY", 100, 320, 100, 160, 5.0);
        ElectricalConstructionContext.ElementHandle isolation = d.switchElement(
            "ISOLATION", 100, 160, 180, 160);
        ElectricalConstructionContext.ElementHandle connector = d.switchElement(
            "CONNECTOR", 180, 160, 220, 160);
        ElectricalConstructionContext.ElementHandle input = d.wire(
            "SUPPLY_TRACE", 220, 160, 230, 160);
        ElectricalConstructionContext.ElementHandle output = d.wire(
            "OUTPUT_TRACE", 420, 160, 500, 160);
        ElectricalConstructionContext.ElementHandle returned = d.wire(
            "RETURN_TRACE", 740, 160, 740, 320);
        ElectricalConstructionContext.ElementHandle ground = d.ground(
            "GROUND", 740, 320, 740, 352);
        d.wire("RETURN_BOTTOM", 100, 320, 740, 320);
        ElectricalConstructionContext.TerminalHandle s1 = d.terminal(source.getElement("R1"), "1");
        ElectricalConstructionContext.TerminalHandle s2 = d.terminal(
            source.getSecondary("R1_SECONDARY").getElementHandle(), "2");
        ElectricalConstructionContext.TerminalHandle l1 = d.terminal(load.getElement("R1"), "1");
        ElectricalConstructionContext.TerminalHandle l2 = d.terminal(
            load.getSecondary("R1_SECONDARY").getElementHandle(), "2");
        ElectricalConstructionContext.ElementHandle sourceFirst = d.join(
            "SOURCE_FIRST_ATTACHMENT", d.terminal(input, "2"), s1);
        ElectricalConstructionContext.ElementHandle sourceSecond = d.join(
            "SOURCE_SECOND_ATTACHMENT", s2, d.terminal(output, "1"));
        ElectricalConstructionContext.ElementHandle loadFirst = d.join(
            "LOAD_FIRST_ATTACHMENT", d.terminal(output, "2"), l1);
        ElectricalConstructionContext.ElementHandle loadSecond = d.join(
            "LOAD_SECOND_ATTACHMENT", l2, d.terminal(returned, "1"));
        d.bindPad("device", "J1.1", d.terminal(connector, "2"));
        if (!omitReturnPad) d.bindPad("device", "J1.2", d.terminal(ground, "1"));
        d.bindPad("source", "R1.1", d.terminal(input, "2"));
        d.bindPad("source", "R1.2", d.terminal(output, "1"));
        d.bindPad("load", "R1.1", d.terminal(output, "2"));
        d.bindPad("load", "R1.2", d.terminal(returned, "1"));
        d.bindComponent("device", "J1", connector, null);
        d.bindPower("VIN_INPUT", supply, isolation);
        rejectWrongComponentTerminals(d, source, input, output, sourceFirst, sourceSecond);
        d.bindComponentConnection("source", "R1", "R1.1",
            d.terminal(input, "2"), s1, sourceFirst);
        d.bindComponentConnection("source", "R1", "R1.2",
            d.terminal(output, "1"), s2, sourceSecond);
        d.bindComponentConnection("load", "R1", "R1.1",
            d.terminal(output, "2"), l1, loadFirst);
        d.bindComponentConnection("load", "R1", "R1.2",
            d.terminal(returned, "1"), l2, loadSecond);
        d.finish();
        context.finish();
    }

    private void rejectWrongComponentTerminals(
            final ElectricalConstructionContext.DeviceScope device,
            final ContributionConstructionReceipt source,
            final ElectricalConstructionContext.ElementHandle input,
            final ElectricalConstructionContext.ElementHandle output,
            final ElectricalConstructionContext.ElementHandle firstConnection,
            final ElectricalConstructionContext.ElementHandle secondConnection) {
        final ElectricalConstructionContext.TerminalHandle primarySecond =
            device.terminal(source.getElement("R1"), "2");
        reject(new Runnable() { public void run() {
            device.bindComponentConnection("source", "R1", "R1.1",
                device.terminal(input, "2"), primarySecond, firstConnection);
        } }, "opposite terminal of the correct component bound to pad one");
        reject(new Runnable() { public void run() {
            device.bindComponentConnection("source", "R1", "R1.2",
                device.terminal(output, "1"), primarySecond, secondConnection);
        } }, "component terminal two bypassing its declared secondary path");
    }

    private void reject(Runnable operation, String description) {
        boolean rejected = false;
        try { operation.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "accepted " + description);
        negatives++;
    }

    private void check(boolean condition, String description) {
        contracts++;
        if (!condition) throw new IllegalStateException("A04 context contract: " + description);
    }

    private void solved(boolean condition, String description) {
        solver++;
        if (!condition) throw new IllegalStateException("A04 context solver: " + description);
    }

    private static Throwable retain(Throwable original, Throwable next) {
        if (original == null) return next;
        if (original != next) original.addSuppressed(next);
        return original;
    }

    private static void rethrow(Throwable problem) {
        if (problem instanceof RuntimeException) throw (RuntimeException) problem;
        if (problem instanceof Error) throw (Error) problem;
        throw new IllegalStateException("A04 context conformance failed", problem);
    }
}
