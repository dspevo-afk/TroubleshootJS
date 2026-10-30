package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Bounded A03 identity/replay canary.  This route is deliberately developer
 * only: it exercises the real resolver, assembler and CircuitJS owner seam,
 * then restores the player's owner before disposing every detached candidate.
 */
final class A03IdentityDeveloperVerifier {
    private static final String PROTOCOL = "TSJ-A03-IDENTITY-1";
    private static final long[] SEEDS = { 1L, 2L, -1L };

    private A03IdentityDeveloperVerifier() { }

    static String verify(CirSim sim, boolean forcedFailure) {
        final String[] stage = new String[] { "entry-validation" };
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer route required");
        if (forcedFailure)
            throw new AssertionError("a03-explicit-failure-canary");

        long overallStart = System.currentTimeMillis();
        final String vectors;
        final GeneratedBoardInstance originalOwner;
        final GeneratedChallengeController originalChallenge;
        final Task41SimulationSnapshot original;
        try {
        stage[0] = "vectors-first-run";
        vectors = A03IdentityContractVectors.run();
        stage[0] = "vectors-repeat-run";
        String repeatedVectors = A03IdentityContractVectors.run();
        stage[0] = "assert-vector-determinism";
        require(vectors.equals(repeatedVectors), "A03 vector receipt was not deterministic");
        stage[0] = "assert-vector-receipt-markers";
        require(vectors.startsWith("A03_IDENTITY_VECTORS_BEGIN\n") &&
            vectors.endsWith("A03_IDENTITY_VECTORS_END\n"),
            "A03 vector receipt markers are incomplete");
        stage[0] = "assert-vector-count";
        require(A03IdentityContractVectors.getAssertionCount() > 0,
            "A03 vector corpus made no assertions");

        stage[0] = "capture-original-owner";
        originalOwner = sim.getGeneratedBoardInstance();
        stage[0] = "capture-original-challenge";
        originalChallenge = sim.getGeneratedChallengeController();
        stage[0] = "assert-original-owner-ready";
        require(originalOwner != null && originalChallenge != null &&
            originalChallenge.isReady() && sim.isGeneratedRuntimeSettled(),
            "A03 requires a settled original owner");
        stage[0] = "capture-original-snapshot";
        original = Task41SimulationSnapshot.capture(sim);
        } catch (Throwable failure) {
            throw stagedFailure(stage[0], failure);
        }
        Vector<GeneratedBoardInstance> candidates = new Vector<GeneratedBoardInstance>();
        Vector<GeneratedBoardInstance> disposed = new Vector<GeneratedBoardInstance>();
        Vector<CorpusCase> corpus = new Vector<CorpusCase>();
        Throwable primary = null;
        String report = null;
        try {
            for (int index = 0; index < SEEDS.length; index++) {
                stage[0] = "pure-sample-seed-" + Long.toString(SEEDS[index]);
                CorpusCase sample = verifyPureSample(SEEDS[index], candidates);
                corpus.add(sample);
            }
            stage[0] = "runtime-samples";
            runRuntimeSamples(sim, original, originalOwner, corpus);
            stage[0] = "restore-original-after-runtime-samples";
            original.restore(sim);
            stage[0] = "assert-original-after-runtime-samples";
            original.assertRestored(sim);
            stage[0] = "assert-original-owner-after-runtime-samples";
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                sim.getGeneratedChallengeController() == originalChallenge,
                "A03 changed the original owner");
            stage[0] = "serialize-success-report";
            report = report(vectors, corpus, overallStart);
        } catch (Throwable failure) {
            primary = stagedFailure(stage[0], failure);
        } finally {
            Throwable cleanupFailure = null;
            try {
                stage[0] = "cleanup-restore-original-snapshot";
                original.restore(sim);
            } catch (Throwable cleanup) {
                cleanupFailure = stagedFailure(stage[0], cleanup);
            }
            for (int index = 0; index < candidates.size(); index++) {
                GeneratedBoardInstance candidate = candidates.get(index);
                try {
                    stage[0] = "cleanup-dispose-candidate-" + index;
                    dispose(sim, candidate, originalOwner, disposed, stage);
                } catch (Throwable cleanup) {
                    cleanupFailure = retain(cleanupFailure,
                        stagedFailure(stage[0], cleanup));
                }
            }
            try {
                stage[0] = "cleanup-assert-original-snapshot";
                original.assertRestored(sim);
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure,
                    stagedFailure(stage[0], cleanup));
            }
            if (cleanupFailure != null && primary == null)
                primary = cleanupFailure;
            else if (cleanupFailure != null && primary != null) {
                IllegalStateException combined = new IllegalStateException(
                    "A03 verification failed: " + message(primary) +
                    "; cleanup failed: " + message(cleanupFailure), primary);
                retain(combined, cleanupFailure);
                primary = combined;
            }
        }
        if (primary != null)
            rethrow(primary);
        return report;
    }

    private static CorpusCase verifyPureSample(long seed,
            Vector<GeneratedBoardInstance> candidates) {
        String[] stage = new String[] { "request" };
        try {
        BoundedAssemblyRequest request = requestFor(seed);
        stage[0] = "plan-resolve";
        long planStart = System.currentTimeMillis();
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        long planElapsed = elapsed(planStart);

        stage[0] = "manifest-capture";
        long captureStart = System.currentTimeMillis();
        RealizationManifest pure = A03RealizationReplay.capture(plan);
        stage[0] = "manifest-canonical-encode";
        String canonical = pure.toCanonical();
        long encodeElapsed = elapsed(captureStart);
        require(canonical.length() > 0, "A03 capture produced an empty manifest");
        stage[0] = "manifest-parse";
        RealizationManifest parsed = RealizationManifest.parse(canonical);
        require(canonical.equals(parsed.toCanonical()),
            "A03 manifest canonical round-trip changed bytes");
        require(pure.identityCanonical().equals(parsed.identityCanonical()),
            "A03 manifest identity changed after parse");

        stage[0] = "manifest-resolve";
        long resolveStart = System.currentTimeMillis();
        BoundedAssemblyPlan resolved = A03RealizationReplay.resolve(parsed);
        stage[0] = "manifest-decode-and-resolve";
        BoundedAssemblyPlan decoded = A03RealizationReplay.decodeAndResolve(canonical);
        long resolveElapsed = elapsed(resolveStart);
        require(plan.getSemanticSignature().equals(resolved.getSemanticSignature()),
            "A03 resolved plan changed semantic signature");
        require(plan.getSemanticSignature().equals(decoded.getSemanticSignature()),
            "A03 decoded plan changed semantic signature");

        stage[0] = "direct-assembly";
        long generationStart = System.currentTimeMillis();
        BoundedGeneratedBoardAssembler.Result direct =
            BoundedGeneratedBoardAssembler.assemble(request);
        long generationElapsed = elapsed(generationStart);
        candidates.add(direct.getInstance());
        require(plan.getSemanticSignature().equals(direct.getPlan().getSemanticSignature()),
            "A03 direct generation changed the resolved plan");
        require(canonical.equals(direct.getRealizationManifest().toCanonical()),
            "runtime generation capture differs from pure plan manifest");

        stage[0] = "replay-assembly";
        long replayStart = System.currentTimeMillis();
        BoundedGeneratedBoardAssembler.Result replay =
            A03RealizationReplay.generate(parsed);
        long replayElapsed = elapsed(replayStart);
        candidates.add(replay.getInstance());
        require(plan.getSemanticSignature().equals(replay.getPlan().getSemanticSignature()),
            "A03 replay generation changed the resolved plan");
        require(canonical.equals(replay.getRealizationManifest().toCanonical()),
            "A03 replay result changed manifest bytes");
        assertFresh(direct.getInstance(), replay.getInstance());

        stage[0] = "assembly-owner-parity";
        GeneratedBoardInstance instance = replay.getInstance();
        require(instance.getBoard() != null && instance.getPcbLayout() != null,
            "A03 replay has no board/layout owner");
        stage[0] = "board-validation";
        instance.getBoard().validate();
        stage[0] = "pcb-layout-validation";
        instance.getPcbLayout().validateGeometry(instance.getBoard());
        return new CorpusCase(seed, request, plan, pure, direct, replay,
            planElapsed, encodeElapsed, resolveElapsed, generationElapsed,
            replayElapsed, canonical.length());
        } catch (Throwable failure) {
            throw stagedFailure("pure-seed-" + Long.toString(seed) + "/" +
                stage[0], failure);
        }
    }

    private static void runRuntimeSamples(CirSim sim, Task41SimulationSnapshot original,
            GeneratedBoardInstance originalOwner, Vector<CorpusCase> corpus) {
        String[] stage = new String[] { "entry" };
        try {
        for (int index = 0; index < corpus.size(); index++) {
            CorpusCase sample = corpus.get(index);
            stage[0] = "seed-" + Long.toString(sample.seed) + "/restore-before-install";
            original.restore(sim);
            stage[0] = "seed-" + Long.toString(sample.seed) + "/assert-restored-before-install";
            original.assertRestored(sim);
            stage[0] = "seed-" + Long.toString(sample.seed) + "/install-composition";
            FreshGeneratedRuntimeInstallation.installComposition(sim,
                sample.replay.getInstance(), false);
            stage[0] = "seed-" + Long.toString(sample.seed) + "/settle-through-circuitjs";
            GeneratedRuntimeDeveloperSettlement.settle(sim, sample.replay.getInstance(),
                "a03-replayed-board-" + Long.toString(sample.seed));
            stage[0] = "seed-" + Long.toString(sample.seed) + "/assert-settled-owner";
            require(sim.getGeneratedBoardInstance() == sample.replay.getInstance() &&
                sim.getGeneratedChallengeController() != null &&
                sim.getGeneratedChallengeController().isReady() &&
                sim.isGeneratedRuntimeSettled(),
                "A03 replay owner did not settle through CircuitJS");
            stage[0] = "seed-" + Long.toString(sample.seed) + "/assert-settled-manifest";
            require(sample.canonical.equals(sample.replay.getRealizationManifest().toCanonical()),
                "settling the replay owner changed its durable manifest");
            sample.runtimeSettled = true;
            if (index == 0) {
                stage[0] = "seed-" + Long.toString(sample.seed) + "/solver-coordinate-poison-canary";
                verifySolverAndCoordinatePoison(sim, sample);
            }
        }
        stage[0] = "restore-original-after-runtime-samples";
        original.restore(sim);
        stage[0] = "assert-original-after-runtime-samples";
        original.assertRestored(sim);
        } catch (Throwable failure) {
            throw stagedFailure("runtime-samples/" + stage[0], failure);
        }
    }

    /**
     * Allocate a real temporary CircuitElm/node graph and move a real solver
     * element's drawing coordinates.  The operation is never settled as
     * production physics; the full Task41 snapshot and element fields are
     * restored before asserting the owner.  Durable identity is read only
     * from the manifest, so transient node numbers/points cannot poison it.
     */
    private static void verifySolverAndCoordinatePoison(CirSim sim, CorpusCase sample) {
        String[] stage = new String[] { "capture-poison-snapshot" };
        final Task41SimulationSnapshot poisoned;
        final Vector<ElementState> states;
        final CircuitElm first;
        final GeneratedBoardInstance activeOwner;
        final String manifestBefore;
        final String dumpBefore;
        final int nodesBefore;
        try {
        poisoned = Task41SimulationSnapshot.capture(sim);
        stage[0] = "capture-poison-element-states";
        states = saveElementStates(sim.elmList);
        stage[0] = "inspect-active-graph";
        first = sim.elmList.isEmpty() ? null : sim.elmList.firstElement();
        stage[0] = "inspect-active-owner";
        activeOwner = sim.getGeneratedBoardInstance();
        stage[0] = "assert-poison-graph-present";
        require(first != null, "A03 poison canary has no solver element");
        stage[0] = "assert-poison-owner";
        require(activeOwner == sample.replay.getInstance(),
            "A03 poison canary is not running the replayed board owner");
        stage[0] = "capture-pre-poison-manifest";
        manifestBefore = sample.canonical;
        stage[0] = "capture-pre-poison-element-dump";
        dumpBefore = first.dump();
        stage[0] = "inspect-node-count-before";
        nodesBefore = sim.nodeList == null ? 0 : sim.nodeList.size();
        } catch (Throwable failure) {
            throw stagedFailure("poison-canary-setup/" + stage[0], failure);
        }
        ResistorElm transientElement = null;
        Throwable primary = null;
        try {
            stage[0] = "begin-private-proof";
            poisoned.beginProof(sim);
            stage[0] = "stop-simulation-for-canary";
            sim.setSimRunning(false);
            stage[0] = "poison-first-element-coordinates";
            int shift = 1;
            first.setPosition(first.x + shift, first.y + shift,
                first.x2 + shift, first.y2 + shift);
            require(!dumpBefore.equals(first.dump()),
                "A03 coordinate poison did not change a real CircuitElm");
            stage[0] = "create-transient-element";
            transientElement = new ResistorElm(20000, 20000);
            stage[0] = "position-transient-element";
            transientElement.setPosition(20000, 20000, 20032, 20000);
            stage[0] = "add-transient-element";
            sim.elmList.addElement(transientElement);
            stage[0] = "analyze-circuit-after-transient-add";
            sim.analyzeCircuit();
            stage[0] = "check-transient-node-allocation";
            int nodesAfter = sim.nodeList == null ? 0 : sim.nodeList.size();
            require(nodesAfter > nodesBefore,
                "A03 solver transient CircuitElm did not allocate a new solver node set");

            // Re-capture directly from the live replay owner.  This proves that
            // node assignment, element coordinates, and layout coordinates do
            // not become durable identity through an accidental runtime path.
            stage[0] = "capture-active-physical-choices";
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices activeChoices =
                new BoundedGeneratedBoardAssembler.PlanPhysicalChoices(
                    activeOwner.getPcbLayout(), activeOwner.getPhysicalSpecifications(),
                    activeOwner.getBoard());
            stage[0] = "capture-live-replay-manifest";
            RealizationManifest activeManifest =
                A03RealizationReplay.capture(sample.plan, activeChoices);
            requireManifestParity(sample.pure, activeManifest,
                "A03 live replay-owner recapture");

            // A translated reconstruction exercises real PCB placement/trace
            // coordinates while retaining each package's declared variant and
            // geometry realization.  PlanPhysicalChoices intentionally retains
            // only those durable package/value choices, never the coordinates.
            stage[0] = "translate-pcb-layout";
            PcbBoardLayout translatedLayout = translatedPcbLayout(
                activeOwner.getPcbLayout(), activeOwner.getBoard());
            stage[0] = "capture-translated-physical-choices";
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices translatedChoices =
                new BoundedGeneratedBoardAssembler.PlanPhysicalChoices(
                    translatedLayout, activeOwner.getPhysicalSpecifications(),
                    activeOwner.getBoard());
            require(samePhysicalChoices(activeChoices, translatedChoices),
                "A03 translated PCB coordinates changed physical choices");
            stage[0] = "capture-translated-manifest";
            RealizationManifest translatedManifest =
                A03RealizationReplay.capture(sample.plan, translatedChoices);
            requireManifestParity(sample.pure, translatedManifest,
                "A03 translated PCB-layout recapture");

            // This overload must stay independent of any live solver singleton.
            stage[0] = "pure-plan-recapture-after-poison";
            RealizationManifest pureRecapture = A03RealizationReplay.capture(sample.plan);
            requireManifestParity(sample.pure, pureRecapture,
                "A03 pure-plan recapture after solver poison");
            require(manifestBefore.equals(sample.replay.getRealizationManifest().toCanonical()),
                "solver/node allocation poisoned the replay manifest");
            sample.coordinatePoison = true;
            sample.pcbCoordinatePoison = true;
            sample.solverNodePoison = true;
            sample.nodesBefore = nodesBefore;
            sample.nodesAfter = nodesAfter;
        } catch (Throwable failure) {
            primary = stagedFailure(stage[0], failure);
        } finally {
            Throwable cleanupFailure = null;
            try {
                stage[0] = "cleanup-delete-transient-element";
                if (transientElement != null)
                    transientElement.delete();
            } catch (Throwable cleanup) {
                cleanupFailure = stagedFailure(stage[0], cleanup);
            }
            try {
                // Restore actual CircuitElm fields before the snapshot proof;
                // snapshot restoration restores list/graph ownership around it.
                stage[0] = "cleanup-restore-element-states";
                restoreElementStates(states);
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure,
                    stagedFailure(stage[0], cleanup));
            }
            try {
                stage[0] = "cleanup-restore-poison-snapshot";
                poisoned.restore(sim);
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure,
                    stagedFailure(stage[0], cleanup));
            }
            try {
                stage[0] = "cleanup-assert-poison-snapshot-restored";
                poisoned.assertRestored(sim);
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure,
                    stagedFailure(stage[0], cleanup));
            }
            try {
                stage[0] = "cleanup-assert-active-replay-owner";
                require(sim.getGeneratedBoardInstance() == activeOwner,
                    "A03 poison cleanup lost the active replayed board owner");
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure,
                    stagedFailure(stage[0], cleanup));
            }
            if (cleanupFailure != null && primary == null)
                primary = cleanupFailure;
            else if (cleanupFailure != null && primary != null) {
                IllegalStateException combined = new IllegalStateException(
                    "A03 poison failure: " + message(primary) +
                    "; cleanup failure: " + message(cleanupFailure), primary);
                retain(combined, cleanupFailure);
                primary = combined;
            }
        }
        if (primary != null)
            rethrow(primary);
    }

    private static void requireManifestParity(RealizationManifest expected,
            RealizationManifest actual, String label) {
        require(expected != null && actual != null,
            label + " produced no manifest");
        require(expected.identityCanonical().equals(actual.identityCanonical()),
            label + " changed durable identity vectors");
        require(expected.toCanonical().equals(actual.toCanonical()),
            label + " changed canonical manifest bytes");
    }

    private static boolean samePhysicalChoices(
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices first,
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices second) {
        return first.getLayoutVersion() == second.getLayoutVersion() &&
            first.getPackages().equals(second.getPackages()) &&
            first.getInputVoltages().equals(second.getInputVoltages());
    }

    private static PcbBoardLayout translatedPcbLayout(PcbBoardLayout source,
            TroubleshootBoard board) {
        int[][] offsets = new int[][] {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }
        };
        String originalGeometry = source.geometryFingerprint();
        for (int index = 0; index < offsets.length; index++) {
            int dx = offsets[index][0];
            int dy = offsets[index][1];
            try {
                PcbBoardLayout translated = reconstructPcbLayout(source, dx, dy);
                translated.validateGeometry(board);
                if (!originalGeometry.equals(translated.geometryFingerprint()))
                    return translated;
            } catch (RuntimeException ignored) {
                // Try the next bounded translation that remains on-canvas.
            }
        }
        throw new IllegalStateException(
            "A03 could not construct a valid translated PCB-layout canary");
    }

    private static PcbBoardLayout reconstructPcbLayout(PcbBoardLayout source,
            int dx, int dy) {
        PcbBoardLayout translated = new PcbBoardLayout(source.getWidth(), source.getHeight(),
            translateRectangle(source.getBoardOutline(), dx, dy),
            translateRectangle(source.getPartsTray(), dx, dy),
            source.getLayoutAlgorithmVersion());
        for (PcbBoardHole hole : source.getHoles()) translated.addHole(hole.translatedBy(dx,dy));
        for (PcbComponentPlacement component : source.getComponents())
            translated.addComponent(component.translatedBy(dx, dy));
        for (PcbPadPlacement pad : source.getPads())
            translated.addPad(new PcbPadPlacement(pad.getPadId(),
                translateCoordinate(pad.getX(), dx), translateCoordinate(pad.getY(), dy),
                pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength(),
                translateRectangle(pad.getPadBounds(), dx, dy),
                translateRectangle(pad.getProbeBounds(), dx, dy), pad.getAttachment(),
                pad.getMountingSide(), pad.getExposure()));
        for (PcbTraceGeometry trace : source.getTraces())
            translated.addTrace(trace.withPath(
                translateCoordinates(trace.getXPoints(), dx),
                translateCoordinates(trace.getYPoints(), dy)));
        for (PcbSilkscreenLabel label : source.getSilkscreenLabels())
            translated.addSilkscreenLabel(new PcbSilkscreenLabel(label.getId(),
                label.getText(), translateRectangle(label.getBounds(), dx, dy),
                label.getFontSize(), label.isBold(), label.getTargetPadId()));
        return translated;
    }

    private static Rectangle translateRectangle(Rectangle value, int dx, int dy) {
        return new Rectangle(translateCoordinate(value.x, dx),
            translateCoordinate(value.y, dy), value.width, value.height);
    }

    private static int[] translateCoordinates(int[] values, int delta) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++)
            result[index] = translateCoordinate(values[index], delta);
        return result;
    }

    private static int translateCoordinate(int value, int delta) {
        long result = (long) value + delta;
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
            throw new IllegalStateException("A03 PCB coordinate overflow");
        return (int) result;
    }

    private static Vector<ElementState> saveElementStates(Vector<CircuitElm> elements) {
        Vector<ElementState> result = new Vector<ElementState>();
        for (CircuitElm element : elements)
            result.add(new ElementState(element));
        return result;
    }

    private static void restoreElementStates(Vector<ElementState> states) {
        for (ElementState state : states)
            state.restore();
    }

    private static void assertFresh(GeneratedBoardInstance first,
            GeneratedBoardInstance second) {
        FreshGeneratedRuntimeInstallation.requireDisjoint(first, second);
        require(first != second && first.getBoard() != second.getBoard() &&
            first.getSimulationBindings() != second.getSimulationBindings() &&
            first.getPhysicalBoardRuntime() != second.getPhysicalBoardRuntime(),
            "A03 replay reused a mutable owner");
    }

    private static void dispose(CirSim sim, GeneratedBoardInstance candidate,
            GeneratedBoardInstance protectedOwner, Vector<GeneratedBoardInstance> disposed,
            String[] stage) {
        if (candidate == null || disposed.contains(candidate)) return;
        stage[0] = "cleanup-candidate-protection-check";
        require(candidate != protectedOwner && candidate != sim.getGeneratedBoardInstance(),
            "A03 refused to dispose an active/protected owner");
        stage[0] = "cleanup-candidate-disconnect-power";
        candidate.getExternalPowerBindings().setConnected(false);
        int elementIndex = 0;
        for (CircuitElm element : candidate.getSimulationElements()) {
            stage[0] = "cleanup-candidate-delete-element-" + elementIndex;
            element.delete();
            elementIndex++;
        }
        stage[0] = "cleanup-candidate-record-disposed";
        disposed.add(candidate);
    }

    private static RuntimeException stagedFailure(String stage, Throwable failure) {
        String type = failure == null ? "null" : failure.getClass().getName();
        return new IllegalStateException("A03 stage [" + stage + "] failed (" +
            type + "): " + message(failure), failure);
    }

    private static BoundedAssemblyRequest requestFor(long seed) {
        if (seed == 1L) return BoundedAssemblyRequest.forCanary(seed);
        if (seed == 2L) return BoundedAssemblyRequest.forControlledIndicator(seed);
        if (seed == -1L) return BoundedAssemblyRequest.forControlledIndicator(seed);
        throw new IllegalArgumentException("Unsupported A03 bounded sample seed: " + seed);
    }

    private static String report(String vectors, Vector<CorpusCase> corpus,
            long start) {
        StringBuilder result = new StringBuilder();
        result.append("{\"protocol\":").append(q(PROTOCOL))
            .append(",\"status\":\"PASS\",\"vectors\":").append(q(vectors))
            .append(",\"vectorAssertions\":").append(A03IdentityContractVectors.getAssertionCount())
            .append(",\"deterministicVectors\":true,\"runtimeManifestMatchesPlan\":true")
            .append(",\"solverNodePoisonCanary\":true,\"pcbCoordinatePoisonCanary\":true")
            .append(",\"cases\":[");
        long serializedBytes = 0;
        boolean first = true;
        for (CorpusCase sample : corpus) {
            if (!first) result.append(',');
            first = false;
            serializedBytes += sample.serializedBytes;
            result.append(sample.toJson());
        }
        return result.append("],\"serializedBytes\":").append(serializedBytes)
            .append(",\"elapsedMs\":").append(elapsed(start))
            .append(",\"originalOwnerRestored\":true,\"candidateCleanup\":\"PASS\"}")
            .toString();
    }

    private static long elapsed(long start) {
        long result = System.currentTimeMillis() - start;
        return result < 0 ? 0 : result;
    }

    private static Throwable retain(Throwable original, Throwable extra) {
        if (original == null) return extra;
        if (extra != original) original.addSuppressed(extra);
        return original;
    }

    private static String message(Throwable failure) {
        return failure == null ? "unknown" :
            (failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new IllegalStateException("A03 verification failed", failure);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("A03: " + label);
    }

    private static String q(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '\\') result.append("\\\\");
            else if (c == '"') result.append("\\\"");
            else if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else result.append(c);
        }
        return result.append('"').toString();
    }

    private static final class CorpusCase {
        final long seed;
        final BoundedAssemblyRequest request;
        final BoundedAssemblyPlan plan;
        final RealizationManifest pure;
        final BoundedGeneratedBoardAssembler.Result direct;
        final BoundedGeneratedBoardAssembler.Result replay;
        final long planElapsed;
        final long encodeElapsed;
        final long resolveElapsed;
        final long generationElapsed;
        final long replayElapsed;
        final int serializedBytes;
        final String canonical;
        boolean runtimeSettled;
        boolean solverNodePoison;
        boolean coordinatePoison;
        boolean pcbCoordinatePoison;
        int nodesBefore;
        int nodesAfter;

        CorpusCase(long seed, BoundedAssemblyRequest request, BoundedAssemblyPlan plan,
                RealizationManifest pure, BoundedGeneratedBoardAssembler.Result direct,
                BoundedGeneratedBoardAssembler.Result replay, long planElapsed,
                long encodeElapsed, long resolveElapsed, long generationElapsed,
                long replayElapsed, int serializedBytes) {
            this.seed = seed;
            this.request = request;
            this.plan = plan;
            this.pure = pure;
            this.direct = direct;
            this.replay = replay;
            this.planElapsed = planElapsed;
            this.encodeElapsed = encodeElapsed;
            this.resolveElapsed = resolveElapsed;
            this.generationElapsed = generationElapsed;
            this.replayElapsed = replayElapsed;
            this.serializedBytes = serializedBytes;
            this.canonical = pure.toCanonical();
        }

        String toJson() {
            ChallengeDescriptor descriptor = request.getDescriptor();
            GeneratedBoardInstance instance = replay.getInstance();
            return "{\"seed\":" + q(Long.toString(seed)) +
                ",\"generatorVersion\":" + descriptor.getGenerator().getVersion() +
                ",\"family\":" + q(instance.getCircuitFamilyId()) +
                ",\"layoutVersion\":" + instance.getPcbLayout().getLayoutAlgorithmVersion() +
                ",\"serializedBytes\":" + serializedBytes +
                ",\"planMs\":" + planElapsed +
                ",\"encodeMs\":" + encodeElapsed +
                ",\"resolveMs\":" + resolveElapsed +
                ",\"generationMs\":" + generationElapsed +
                ",\"replayMs\":" + replayElapsed +
                ",\"runtimeSettled\":" + runtimeSettled +
                ",\"runtimeManifestEqualsPlan\":true" +
                ",\"solverNodePoison\":" + solverNodePoison +
                ",\"coordinatePoison\":" + coordinatePoison +
                ",\"pcbCoordinatePoison\":" + pcbCoordinatePoison +
                ",\"nodesBefore\":" + nodesBefore +
                ",\"nodesAfter\":" + nodesAfter +
                ",\"identity\":" + q(pure.identityCanonical()) + "}";
        }
    }

    private static final class ElementState {
        final CircuitElm element;
        final int x;
        final int y;
        final int x2;
        final int y2;
        final int flags;
        final int[] nodes;
        final double[] volts;
        final double current;
        final double curcount;

        ElementState(CircuitElm element) {
            this.element = element;
            x = element.x;
            y = element.y;
            x2 = element.x2;
            y2 = element.y2;
            flags = element.flags;
            nodes = copy(element.nodes);
            volts = copy(element.volts);
            current = element.current;
            curcount = element.curcount;
        }

        void restore() {
            element.flags = flags;
            element.setPosition(x, y, x2, y2);
            element.nodes = copy(nodes);
            element.volts = copy(volts);
            element.current = current;
            element.curcount = curcount;
        }
    }

    private static int[] copy(int[] value) {
        if (value == null) return null;
        int[] result = new int[value.length];
        for (int index = 0; index < value.length; index++) result[index] = value[index];
        return result;
    }

    private static double[] copy(double[] value) {
        if (value == null) return null;
        double[] result = new double[value.length];
        for (int index = 0; index < value.length; index++) result[index] = value[index];
        return result;
    }
}
