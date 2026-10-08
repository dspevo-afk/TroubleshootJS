package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Vector;

/** One fixed normal-admitted graph's temporal pilot; not five-hypothesis D01 or player qualification. */
public final class Rb56TemporalWorkContractTest {
    private static int assertions;
    private static final int UNITS = 13;
    private static long startedNanos;

    public static void main(String[] args) throws Exception {
        startedNanos = System.nanoTime();
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        String diode = DiodeElm.lastModelName, zener = ZenerElm.lastZenerModelName, transistor = TransistorElm.lastModelName;
        int flags = MosfetElm.globalFlags; double beta = MosfetElm.lastBeta;
        TemporalSim sim = new TemporalSim(); Vector<CircuitElm> rawGraph = sim.elmList;
        GeneratedBoardInstance owner = null;
        Rb56Generator.Candidate candidate = null; Throwable failure = null;
        CircuitElm.sim = sim;
        try {
            phase(sim, "PLAN");
            Rb56Plan plan = Rb56Plan.reference(77L);
            check(plan.physicalPackageCount() == 55 && plan.channelCount == 2, "fixed 55-part, two-channel reference");
            phase(sim, "ROUTE");
            MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator().generateWithPolicyResult(
                plan.board(), plan.layoutSeed, plan.routingSeed);
            check(route != null && route.accepted() && route.getLayout() != null &&
                MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(route.getStatistics().selectedRoutePolicy),
                "one actual bounded P07 route, without seed search");
            phase(sim, "CONSTRUCT");
            candidate = Rb56Generator.construct(plan, "RELAY_B_COIL_OPEN");
            phase(sim, "ADMISSION");
            GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
                candidate.board(), route, plan.layoutSeed, plan.routingSeed);
            phase(sim, "ASSEMBLE");
            owner = Rb56Generator.assemble(candidate, route.getLayout(), admission);
            owner.requireNormalPhysicalAdmission();
            check(!owner.isDeveloperOnlyFaultRoute() && owner.getFaultCandidates().size() == 5,
                "temporal fixture retains normal physical admission and all five unapplied faults");
            phase(sim, "INSTALL");
            install(sim, owner);
            Rb30Behavior behavior = (Rb30Behavior)owner.getFamilyState();
            check(behavior.getInputs() == 0 && behavior.getProfileWorkUnits() == UNITS,
                "fresh RB56 sensor commands are LOW and declare eight startup plus five condition/restoration units");
            completeProfile(sim, owner, behavior, GeneratedTemporalBehavior.Profile.HEALTHY, true);
            phase(sim, "FAULT_APPLY");
            owner.getFaultBinding().setApplied(true);
            completeProfile(sim, owner, behavior, GeneratedTemporalBehavior.Profile.FAULTED, false);
            phase(sim, "FAULT_VERIFY");
            behavior.verifyFaultedProfile(sim, owner, sim.boardModificationController, BoardPowerState.POWERED);
            check(behavior.getObservedBehavior() == GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
                "accepted all-HIGH fault window records the actual selected relay symptom");
            sim.nativeProfilesReady = true;
            PhysicalRelayPart original = (PhysicalRelayPart)owner.getPhysicalBoardRuntime().getInstalledPart("KB");
            repairRelay(sim, owner, behavior, original);
            completeProfile(sim, owner, behavior, GeneratedTemporalBehavior.Profile.REPAIR, true);
            GeneratedBoardOperation operation = owner.getOperationCatalog().find(GeneratedBoardOperationIds.CUSTOMER_RETEST);
            check(operation != null && operation.getWorkUnits(owner) == UNITS, "actual customer retest retains thirteen units");
            phase(sim, "CUSTOMER_RETEST");
            GeneratedWork<GeneratedCustomerRetestResult> retest = operation.begin(sim, owner);
            runUnits(sim, retest, false);
            check(retest.finish().isPassed(), "actual repaired current relay passes every accepted-window customer condition");
            check(owner.getPhysicalBoardRuntime().getInstalledPart("KB") != original && original.isFaulted(),
                "catalog replacement resolves current backing while the original loose fault remains applied");
            phase(sim, "CURRENT_BACKING_NEGATIVES");
            currentBackingNegatives(sim, owner, behavior);
            cancellationAndFailure(sim, owner, behavior);
            missingHarnessNegative(sim, owner, behavior);
            check(subscriptions(sim).isEmpty() && !sim.activeMeasurementOverlay && sim.stopMessage == null,
                "pilot ends without observation handles, stimulus overlay, or unexpected solver stop");
        } catch (Throwable problem) {
            receipt(sim, "TEST_FAILURE", -1, 0, problem.getClass().getSimpleName()); failure = problem;
        } finally {
            phase(sim, "CLEANUP");
            Cleanup cleanup;
            try { cleanup = disposeOwned(sim, rawGraph, owner, candidate); }
            finally {
                CircuitElm.sim = prior; CirSim.theSim = singleton;
                DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener; TransistorElm.lastModelName = transistor;
                MosfetElm.globalFlags = flags; MosfetElm.lastBeta = beta;
            }
            boolean restored = CircuitElm.sim == prior && CirSim.theSim == singleton &&
                same(diode, DiodeElm.lastModelName) && same(zener, ZenerElm.lastZenerModelName) &&
                same(transistor, TransistorElm.lastModelName) && MosfetElm.globalFlags == flags &&
                Double.doubleToLongBits(MosfetElm.lastBeta) == Double.doubleToLongBits(beta);
            boolean clean = cleanup.failure == null && cleanup.deleted == cleanup.attempted &&
                sim.elmList == rawGraph && rawGraph.isEmpty() && sim.generatedBoardInstance == null &&
                sim.generatedChallengeController == null && sim.boardModificationController == null &&
                sim.circuitMatrix == null && cleanup.powerDetached && cleanup.executorRetired &&
                !sim.solverExecutor.isUnavailable() && cleanup.subscriptionsBefore == 0 &&
                cleanup.subscriptionsAfter == 0 && restored;
            System.out.println("RB56_TEMPORAL_CLEANUP {\"elementsAttempted\":" + cleanup.attempted +
                ",\"elementsDeleted\":" + cleanup.deleted + ",\"powerDetached\":" + cleanup.powerDetached +
                ",\"rawGraphRestored\":" + (sim.elmList == rawGraph && rawGraph.isEmpty()) +
                ",\"executorRetired\":" + cleanup.executorRetired +
                ",\"subscriptionsBeforeCleanup\":" + cleanup.subscriptionsBefore +
                ",\"subscriptionsAfterCleanup\":" + cleanup.subscriptionsAfter +
                ",\"emptySubscriptions\":" + (cleanup.subscriptionsAfter == 0) +
                ",\"globalsRestored\":" + restored + ",\"clean\":" + clean + "}");
            failure = merge(failure, cleanup.failure);
            if (!clean) failure = merge(failure, new AssertionError("RB56 temporal owned cleanup failed"));
        }
        if (failure instanceof Error) throw (Error)failure;
        if (failure instanceof Exception) throw (Exception)failure;
        if (failure != null) throw new AssertionError("RB56 temporal pilot failed", failure);
        phase(sim, "COMPLETE");
        System.out.println("PASS: RB56 temporal work contracts " + assertions +
            " assertions seed=77 packages=55 fault=RELAY_B_COIL_OPEN units=13 scope=FIXED_TEMPORAL_PILOT_ONLY");
    }


    /** Three actual normal-admitted owners; metadata capture never advances a solver. */
    static void captureContexts() throws Exception {
        startedNanos = System.nanoTime();
        int initialAssertions = assertions;
        Vector<Throwable> failures = new Vector<Throwable>();
        long[] seeds = {77L, -1067L, -1022L};
        int[] packages = {40, 55, 60};
        for (int index = 0; index < seeds.length; index++) {
            try { captureContext(seeds[index], packages[index]); }
            catch (Exception failure) { failures.add(failure); }
            catch (AssertionError failure) { failures.add(failure); }
        }
        if (!failures.isEmpty()) {
            AssertionError combined = new AssertionError("Every fixed RB56 capture case must pass; failures=" + failures.size());
            for (Throwable failure : failures) combined.addSuppressed(failure);
            throw combined;
        }
        System.out.println("PASS: RB56 dependency capture contracts " + (assertions - initialAssertions) +
            " assertions cases=3 scope=FULL_CONTEXT_CAPTURE_ONLY normalSessionLoad=false");
    }

    private static void captureContext(long seed, int expectedPackages) throws Exception {
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        String diode = DiodeElm.lastModelName, zener = ZenerElm.lastZenerModelName, transistor = TransistorElm.lastModelName;
        int flags = MosfetElm.globalFlags; double beta = MosfetElm.lastBeta;
        TemporalSim sim = new TemporalSim(); Vector<CircuitElm> rawGraph = sim.elmList;
        GeneratedBoardInstance owner = null; Rb56Generator.Candidate candidate = null; Throwable failure = null;
        int packages = -1, elements = -1, contextChars = -1, keyChars = -1, pristineChars = -1, currentChars = -1;
        String digest = null; boolean tailChanged = false; long began = System.nanoTime();
        CircuitElm.sim = sim;
        try {
            Rb56Plan plan = Rb56Plan.resolve(seed);
            check(plan.physicalPackageCount() == expectedPackages, "actual resolved seed has its declared package census");
            GenerationRequest request = GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, seed);
            MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator().generateWithPolicyResult(
                plan.board(), plan.layoutSeed, plan.routingSeed);
            check(route != null && route.accepted() && route.getLayout() != null &&
                MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(route.getStatistics().selectedRoutePolicy),
                "capture owner uses the actual bounded normal P07 route: " + (route == null ? "null" : route.toCanonical()));
            candidate = Rb56Generator.construct(plan, Rb56Generator.selectedFaultId(plan));
            GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
                candidate.board(), route, plan.layoutSeed, plan.routingSeed);
            owner = Rb56Generator.assemble(candidate, route.getLayout(), admission);
            owner.requireNormalPhysicalAdmission(); install(sim, owner);
            // Existing D01 capture fixture adapter supplies scenario metadata only;
            // no healthy/faulted lifecycle or selected-scenario presentation is proved.
            final GeneratedScenario<GeneratedObservedBehavior> selected = owner.getChallengeDefinition()
                .getScenarioCatalog().getCandidates().firstElement();
            sim.generatedChallengeController = new GeneratedChallengeController(sim, owner) {
                @Override GeneratedScenario<GeneratedObservedBehavior> getScenario() { return selected; }
            };
            owner.getFaultBinding().setApplied(true);
            sim.timeStep = sim.maxTimeStep = Rb30Behavior.RB56_MAX_STEP_SECONDS;
            sim.minTimeStep = Rb30Behavior.SOLVER_MIN_STEP_SECONDS; sim.adjustTimeStep = true;
            check(selected != null && sim.generatedChallengeController.getScenario() == selected &&
                sim.timeStep == Rb30Behavior.RB56_MAX_STEP_SECONDS && sim.minTimeStep == 50e-12 && sim.adjustTimeStep,
                "cold metadata fixture exposes its actual scenario and current RB56 adaptive recipe without solving");
            packages = owner.getBoard().getComponentIds().size();
            check(packages == expectedPackages && owner.getPhysicalSpecifications().getPhysicalComponentIds().size() == packages,
                "independent actual logical and physical censuses match the resolved40/55/60 population");
            for (String componentId : owner.getBoard().getComponentIds())
                check(owner.getPhysicalSpecifications().getPhysicalDefinition(componentId) != null &&
                    owner.getPhysicalBoardRuntime().getInstalledPart(componentId) != null,
                    "every declared package has its actual installed physical owner");
            Vector<CircuitElm> actualElements = owner.getSimulationElements();
            Vector<CircuitElm> unique = new Vector<CircuitElm>(); collect(unique, actualElements);
            elements = owner.getSimulationElements().size();
            check(unique.size() == elements && sim.elmList.size() == elements &&
                sim.generatedBoardInstance == owner && !owner.isDeveloperOnlyFaultRoute() && owner.getFaultCandidates().size() == 5,
                "capture uses the whole unique installed normal owner and its five actual hypotheses");
            // Owner accessor returns a defensive vector; compare each actual element identity.
            for (int n = 0; n < elements; n++)
                check(sim.elmList.get(n) == actualElements.get(n), "installed graph retains every owner element in order");
            String requestManifest = request.candidateManifest(0), realization = plan.canonical();
            GenerationDependencyContext context = GenerationDependencyContext.capture(sim, owner, requestManifest, realization);
            GeneratedDiagnosticContextKey key = GeneratedDiagnosticContextKey.from(context);
            String complete = context.canonical(); contextChars = complete.length(); keyChars = key.canonical().length();
            String pristine = owner.getPristineConductorGraph().toCanonical();
            String current = PcbConductorState.encode(owner.getCurrentConductorSnapshot());
            pristineChars = pristine.length(); currentChars = current.length();
            check(complete.contains(captureField("conductor.pristine", pristine)) &&
                complete.contains(captureField("conductor.current", current)) &&
                complete.endsWith(captureField("layout.owner-board", owner.getBoard().getId())),
                "full pristine/current copper and final owner-board field survive capture");
            check(contextChars <= GenerationDependencyContext.MAX_CANONICAL_LENGTH &&
                keyChars <= GeneratedDiagnosticContextKey.MAX_CANONICAL_LENGTH && key.isTrustedCapture(),
                "complete typed context fits its finite capacity and retains capture trust");
            if (expectedPackages > 40) check(contextChars > PlayerSessionFingerprint.MAX_CHARACTERS,
                "actual55/60 context crosses the unchanged ordinary session1MiB boundary");
            digest = PlayerSessionFingerprint.ofDiagnosticContext(key);
            check(digest.equals(captureSha(key.canonical())), "typed full-context digest matches independent JVM UTF8 SHA256");
            GeneratedDiagnosticContextKey repeated = GeneratedDiagnosticContextKey.capture(sim, owner, requestManifest, realization);
            check(key.equals(repeated) && digest.equals(PlayerSessionFingerprint.ofDiagnosticContext(repeated)),
                "unchanged actual owner repeats the same full identity without solver work");
            // Value-key sensitivity only: no admitted gameplay copper mutation is claimed.
            int tail = complete.length() - 2;
            char changed = complete.charAt(tail) == 'X' ? 'Y' : 'X';
            GeneratedDiagnosticContextKey tailKey = new GeneratedDiagnosticContextKey(
                complete.substring(0, tail) + changed + complete.substring(tail + 1));
            tailChanged = !key.equals(tailKey) && !digest.equals(PlayerSessionFingerprint.ofDiagnosticContext(tailKey));
            check(tailChanged && !tailKey.isTrustedCapture(), "last-value-byte mutation changes identity and digest without granting trust");
            if (expectedPackages > 40) check(tail > PlayerSessionFingerprint.MAX_CHARACTERS,
                "large-owner tail sensitivity reaches beyond the ordinary session boundary");
            boolean requestRejected = false, realizationRejected = false;
            try { GenerationDependencyContext.capture(sim, owner, captureRepeated('x', 64 * 1024 + 1), realization); }
            catch (IllegalArgumentException expected) { requestRejected = expected.getMessage().contains("request manifest exceeds"); }
            try { GenerationDependencyContext.capture(sim, owner, requestManifest, captureRepeated('x', 256 * 1024 + 1)); }
            catch (IllegalArgumentException expected) { realizationRejected = expected.getMessage().contains("realization manifest exceeds"); }
            check(requestRejected && realizationRejected, "unchanged request and realization oversize boundaries still fail closed");
            check(sim.t == 0 && sim.a01AcceptedStepCount == 0 && sim.a01IterationCount == 0 && sim.a01SubIterationCount == 0 &&
                sim.a01FactorizationCount == 0 && sim.a01SolveCount == 0 && sim.a01AnalysisCount == 0 && sim.advances == 0 &&
                subscriptions(sim).isEmpty() && !sim.activeMeasurementOverlay,
                "routing/construction/full capture/hash/tail/oversize checks perform exactly zero analysis or solver advancement");
        } catch (Throwable problem) { failure = problem;
        } finally {
            Cleanup cleanup;
            try { cleanup = disposeOwned(sim, rawGraph, owner, candidate); }
            finally {
                CircuitElm.sim = prior; CirSim.theSim = singleton;
                DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener; TransistorElm.lastModelName = transistor;
                MosfetElm.globalFlags = flags; MosfetElm.lastBeta = beta;
            }
            boolean restored = CircuitElm.sim == prior && CirSim.theSim == singleton && same(diode, DiodeElm.lastModelName) &&
                same(zener, ZenerElm.lastZenerModelName) && same(transistor, TransistorElm.lastModelName) &&
                MosfetElm.globalFlags == flags && Double.doubleToLongBits(MosfetElm.lastBeta) == Double.doubleToLongBits(beta);
            boolean clean = cleanup.failure == null && cleanup.deleted == cleanup.attempted && sim.elmList == rawGraph &&
                rawGraph.isEmpty() && sim.generatedBoardInstance == null && sim.generatedChallengeController == null &&
                sim.boardModificationController == null && sim.circuitMatrix == null && cleanup.powerDetached && cleanup.executorRetired &&
                !sim.solverExecutor.isUnavailable() && cleanup.subscriptionsBefore == 0 && cleanup.subscriptionsAfter == 0 && restored;
            failure = merge(failure, cleanup.failure);
            if (!clean) failure = merge(failure, new AssertionError("RB56 capture owned cleanup failed"));
            System.out.println("RB56_DEPENDENCY_CAPTURE {\"status\":\"" + (failure == null ? "COMPLETE" : "FAIL") +
                "\",\"seed\":\"" + Long.toString(seed) + "\",\"packages\":" + packages + ",\"elements\":" + elements +
                ",\"contextCharacters\":" + contextChars + ",\"keyCharacters\":" + keyChars +
                ",\"pristineConductorCharacters\":" + pristineChars + ",\"currentConductorCharacters\":" + currentChars +
                ",\"digest\":" + (digest == null ? "null" : "\"" + digest + "\"") + ",\"tailMutationChangesIdentity\":" + tailChanged +
                ",\"simTime\":" + number(sim.t) + ",\"acceptedSteps\":" + sim.a01AcceptedStepCount +
                ",\"newtonTrials\":" + sim.a01SubIterationCount + ",\"analyses\":" + sim.a01AnalysisCount + ",\"solves\":" + sim.a01SolveCount +
                ",\"elapsedMillis\":" + Double.toString((System.nanoTime() - began) / 1e6) + "}");
            System.out.println("RB56_DEPENDENCY_CAPTURE_CLEANUP {\"seed\":\"" + Long.toString(seed) +
                "\",\"elementsAttempted\":" + cleanup.attempted + ",\"elementsDeleted\":" + cleanup.deleted +
                ",\"powerDetached\":" + cleanup.powerDetached + ",\"rawGraphRestored\":" + (sim.elmList == rawGraph && rawGraph.isEmpty()) +
                ",\"executorRetired\":" + cleanup.executorRetired + ",\"subscriptionsBefore\":" + cleanup.subscriptionsBefore +
                ",\"subscriptionsAfter\":" + cleanup.subscriptionsAfter + ",\"globalsRestored\":" + restored + ",\"clean\":" + clean + "}");
        }
        if (failure instanceof Error) throw (Error)failure;
        if (failure instanceof Exception) throw (Exception)failure;
        if (failure != null) throw new AssertionError("RB56 context capture failed", failure);
    }

    private static String captureField(String key, String value) {
        return "FV" + key.length() + ":" + key + ";V" + value.length() + ":" + value + ";";
    }
    private static String captureRepeated(char value, int count) {
        char[] result = new char[count]; java.util.Arrays.fill(result, value); return new String(result);
    }
    private static String captureSha(String value) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
        String digits = "0123456789abcdef"; StringBuilder out = new StringBuilder();
        for (byte b : digest) { int v = b & 255; out.append(digits.charAt(v >>> 4)).append(digits.charAt(v & 15)); }
        return out.toString();
    }

    /** Exhaust only this private fixture's current/original/acquired graph before restoring globals. */
    private static Cleanup disposeOwned(TemporalSim sim, Vector<CircuitElm> rawGraph,
            GeneratedBoardInstance owner, Rb56Generator.Candidate candidate) {
        Cleanup result = new Cleanup(); Vector<CircuitElm> owned = new Vector<CircuitElm>();
        try { if (owner != null) collect(owned, owner.getSimulationElements()); }
        catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        try { if (candidate != null) collect(owned, candidate.elements()); }
        catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        try { if (sim.elmList != rawGraph) collect(owned, sim.elmList); }
        catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        try {
            GeneratedExternalPowerBindings power = owner != null ? owner.getExternalPowerBindings() :
                candidate == null ? null : candidate.assembly.power;
            if (power != null) power.setConnected(false);
        } catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        try {
            sim.boardPowerController.detach();
            result.powerDetached = sim.boardPowerController.getBindingsForDeveloperVerification() == null;
        } catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        try {
            Vector<SolverTimeObservationService.Subscription> live = subscriptions(sim);
            result.subscriptionsBefore = live.size();
            if (!live.isEmpty()) result.failure = merge(result.failure,
                new AssertionError("Functional observation leaked a subscription before fixture cleanup"));
            for (SolverTimeObservationService.Subscription window :
                    new Vector<SolverTimeObservationService.Subscription>(live)) {
                try { sim.solverTimeObservations.unsubscribe(window); }
                catch (Throwable problem) { result.failure = merge(result.failure, problem); }
            }
        } catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        sim.generatedBoardInstance = null; sim.generatedChallengeController = null;
        sim.boardModificationController = null; sim.a01MeasurementRunning = false;
        sim.elmList = rawGraph; sim.circuitMatrix = null;
        try { sim.solverExecutor.retire(); result.executorRetired = !sim.solverExecutor.isUnavailable(); }
        catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        for (CircuitElm element : owned) {
            result.attempted++;
            try { element.delete(); result.deleted++; }
            catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        }
        try { result.subscriptionsAfter = subscriptions(sim).size(); }
        catch (Throwable problem) { result.failure = merge(result.failure, problem); }
        return result;
    }
    private static void collect(Vector<CircuitElm> owned, Vector<CircuitElm> elements) {
        for (CircuitElm element : elements) if (!owned.contains(element)) owned.add(element);
    }
    private static Throwable merge(Throwable primary, Throwable next) {
        if (next == null) return primary;
        if (primary == null) return next;
        if (primary != next) primary.addSuppressed(next);
        return primary;
    }
    private static boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
    private static final class Cleanup {
        int attempted, deleted, subscriptionsBefore = -1, subscriptionsAfter = -1;
        boolean powerDetached, executorRetired;
        Throwable failure;
    }

    private static void install(final TemporalSim sim, GeneratedBoardInstance owner) {
        sim.generatedBoardInstance = owner; sim.elmList = owner.getSimulationElements();
        sim.boardModificationController = new BoardModificationController(sim, owner);
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim, owner, sim.boardModificationController, 0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        sim.generatedChallengeController = new GeneratedChallengeController(sim, owner) {
            @Override boolean allowsWorkbenchInteraction() { return sim.nativeProfilesReady; }
        };
        owner.getFaultBinding().setApplied(false);
    }

    private static void completeProfile(TemporalSim sim, GeneratedBoardInstance owner, Rb30Behavior behavior,
            GeneratedTemporalBehavior.Profile profile, boolean pass) throws Exception {
        phase(sim, "PROFILE_" + profile.name());
        GeneratedWork<GeneratedRepairStatus> work = behavior.beginProfile(sim, owner, profile);
        int windows = sim.windowAdvances;
        runUnits(sim, work, profile == GeneratedTemporalBehavior.Profile.FAULTED);
        receipt(sim, "PROFILE_FINISH_BEFORE", -1, UNITS, "BEGIN");
        check(work.finish() == (pass ? GeneratedRepairStatus.CORRECTLY_RESTORED :
            GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL), "actual " + profile + " result");
        check(sim.windowAdvances - windows == (profile == GeneratedTemporalBehavior.Profile.FAULTED ? 1 : 4),
            "functional conditions use four real ephemeral accepted windows, faulted snapshot uses one");
        receipt(sim, "PROFILE_FINISH_AFTER", -1, UNITS, "RETURNED");
    }

    private static void runUnits(TemporalSim sim, GeneratedWork<?> work, boolean faulted) throws Exception {
        check(work.getWorkUnits() == UNITS, "thirteen bounded work units");
        for (int unit = 0; unit < UNITS; unit++) {
            int before = sim.advances; double time = sim.t;
            boolean more = stepReceipt(sim, work, unit + 1); int advances = sim.advances - before;
            check(more == (unit < UNITS - 1) && advances <= 1, "one bounded solver advance per unit " + unit);
            if (unit < 8) check(advances == 1 && Math.abs(sim.t - time - .050) <= Rb30Behavior.RB56_MAX_STEP_SECONDS + 1e-12,
                "startup unit advances its actual 50 ms, without widening the solver limit");
            else if (unit < (faulted ? 9 : 12)) check(advances == 1 && sim.lastDuration == .030,
                "condition uses the existing 30 ms solver interval");
            else if (faulted) check(advances == 0, "faulted accounting remainder does not repeat the observation");
            check(subscriptions(sim).isEmpty() && !sim.activeMeasurementOverlay,
                "no subscription or stimulus survives a yielded unit");
        }
        check(sim.maxTimeStep == Rb30Behavior.RB56_MAX_STEP_SECONDS && sim.minTimeStep == 50e-12 && sim.adjustTimeStep,
            "actual RB56 profile retains qualified adaptive 400 us / 50 ps recipe");
    }

    /** Real accepted charged-OFF state; no injected currents or additional solver advance. */
    private static void rejectChargedDetachment(TemporalSim sim, GeneratedBoardInstance owner) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        PhysicalRelayPart relay = (PhysicalRelayPart)runtime.getInstalledPart("KA");
        PhysicalServicePart inductor = (PhysicalServicePart)runtime.getInstalledPart("L1");
        check(owner.getExternalPowerBindings().areAllDisconnected() &&
            Math.abs(relay.getElement().coilCurrent) > RelayOutputBehavior.DISCHARGED_AMPS,
            "actual accepted OFF board retains relay coil energy");
        PhysicalBoardRuntimeCapability guard = runtime.getCapability("RB56_RELAY_KA");
        check(guard instanceof PhysicalPartDetachmentReadiness &&
            !((PhysicalPartDetachmentReadiness)guard).isDetachmentReady(sim, owner),
            "RB56 current energy owner contributes its real guard to all graph detachments");
        boolean windingReady = inductor.isDetachmentReady(sim, owner);
        double time = sim.t;
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        PhysicalPart<?> driver = runtime.getInstalledPart("QA");
        HashMap<String, Boolean> driverLeads = sim.boardModificationController.captureConnectionStates("QA");
        HashMap<String, Boolean> diodeLeads = sim.boardModificationController.captureConnectionStates("DA");
        int history = runtime.getSessionHistory().size();
        check(!sim.boardModificationController.isDetachmentReady(), "charged board rejects global detachment");
        boolean removeRejected = false, liftRejected = false;
        try { runtime.getMutationProvider("QA").removeInstalledPart(); }
        catch (BoardModificationRejectedException expected) { removeRejected = true; }
        try { sim.boardModificationController.liftLead("DA", "DA.K"); }
        catch (BoardModificationRejectedException expected) { liftRejected = true; }
        check(removeRejected && liftRejected && runtime.getInstalledPart("QA") == driver &&
            graph.equals(sim.elmList) && sim.t == time && runtime.getSessionHistory().size() == history &&
            driverLeads.equals(sim.boardModificationController.captureConnectionStates("QA")) &&
            diodeLeads.equals(sim.boardModificationController.captureConnectionStates("DA")) &&
            !runtime.isMutationInProgress() && !runtime.isMutationQuarantined(),
            "charged-OFF driver removal and flyback lead lift reject without graph/history mutation");
        System.out.println("RB56_TEMPORAL_CHARGED_OFF {\"coilAmps\":" + relay.getElement().coilCurrent +
            ",\"inductorReady\":" + windingReady + ",\"driverRemovalRejected\":" + removeRejected +
            ",\"diodeLiftRejected\":" + liftRejected + ",\"isolatedCoilOnlyCausalityClaim\":false}");
    }

    private static void repairRelay(TemporalSim sim, GeneratedBoardInstance owner, Rb30Behavior behavior,
            PhysicalRelayPart original) throws Exception {
        phase(sim, "SERVICE_POWER_OFF");
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        PhysicalSlotMutationProvider provider = runtime.getMutationProvider("KB");
        sim.boardPowerController.setState(BoardPowerState.UNPOWERED); runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
        settle(sim, owner);
        rejectChargedDetachment(sim, owner);
        GeneratedDiagnosticServicePreparation.Policy policy = ((GeneratedDiagnosticServicePreparation.Provider)
            owner.getDiagnosticProvider()).getServicePreparationPolicy();
        check(policy.getWorkUnits() == 30 && policy.getMaximumAdvanceSeconds() == .100,
            "provider declares the unchanged bounded three-second service allowance");
        int units = 0;
        while (!provider.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "KB"), null) && units < 30) {
            receipt(sim, "SERVICE_UNIT_BEFORE", units + 1, 30, "BEGIN");
            boolean returned = false;
            try { sim.advanceGeneratedTemporalProfile(.100); returned = true; }
            finally { receipt(sim, "SERVICE_UNIT_AFTER", units + 1, 30, returned ? "RETURNED" : "THREW"); }
            units++; settleFlags(sim);
        }
        check(provider.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "KB"), null),
            "actual discharged target becomes available within the declared service cap");
        phase(sim, "SERVICE_REMOVE");
        check(provider.removeInstalledPart(), "remove actual faulted relay"); settle(sim, owner);
        double time = sim.t;
        check(runtime.getInstalledPart("KB") == null && !behavior.healthy(owner, 3) && sim.t == time,
            "missing current critical part rejects health without reading the retained loose original");
        String catalog = owner.getDiagnosticProvider().getCorrectCatalogId(owner, "KB");
        phase(sim, "SERVICE_CATALOG_INSTALL");
        check(provider.installNewFromCatalog(catalog), "production catalog installs the declared correct relay"); settle(sim, owner);
        PhysicalPart<?> replacement = runtime.getInstalledPart("KB");
        check(replacement instanceof PhysicalRelayPart && replacement != original &&
            owner.getComponentBindings().getSingleElement("KB") == ((PhysicalRelayPart)replacement).getElement(),
            "repair uses fresh physical identity and current component backing");
        phase(sim, "SERVICE_POWER_ON");
        sim.boardPowerController.setState(BoardPowerState.POWERED); runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        settleFlags(sim);
    }

    private static void currentBackingNegatives(TemporalSim sim, GeneratedBoardInstance owner, Rb30Behavior behavior) {
        CircuitElm current = ((PhysicalRelayPart)owner.getPhysicalBoardRuntime().getInstalledPart("KB")).getElement();
        int index = sim.elmList.indexOf(current); double time = sim.t;
        sim.elmList.remove(index);
        try { check(!behavior.healthy(owner, 3) && sim.t == time, "canonical membership alone cannot bless absent current backing"); }
        finally { sim.elmList.add(index, current); }
        CircuitElm load = owner.getConnectionBindings().getConnectorHarness("JOB")[0].getElement();
        index = sim.elmList.indexOf(load); sim.elmList.remove(index);
        try { check(!behavior.healthy(owner, 3) && sim.t == time, "absent actual customer load rejects health without advancing"); }
        finally { sim.elmList.add(index, load); }
    }

    private static void cancellationAndFailure(TemporalSim sim, GeneratedBoardInstance owner, Rb30Behavior behavior) throws Exception {
        phase(sim, "CANCELLATION");
        check(behavior.getInputs() == 3, "retest restores prior all-HIGH command");
        GeneratedWork<GeneratedRepairStatus> work = behavior.beginProfile(sim, owner, GeneratedTemporalBehavior.Profile.REPAIR);
        for (int i = 0; i < 9; i++) check(stepReceipt(sim, work, i + 1), "cancellation pilot reaches its first completed accepted condition");
        check(behavior.getInputs() == 0 && subscriptions(sim).isEmpty(), "completed LOW window leaves no subscription");
        receipt(sim, "CANCEL_BEFORE", -1, UNITS, "BEGIN"); work.cancel();
        receipt(sim, "CANCEL_AFTER", -1, UNITS, "RETURNED");
        check(behavior.getInputs() == 3 && subscriptions(sim).isEmpty() && sim.boardPowerController.getState() == BoardPowerState.POWERED,
            "current-owner cancellation restores only its prior input and leaks no window");
        phase(sim, "FORCED_FAILURE_AFTER_ACCEPTED_WINDOW");
        work = behavior.beginProfile(sim, owner, GeneratedTemporalBehavior.Profile.REPAIR);
        for (int i = 0; i < 8; i++) check(stepReceipt(sim, work, i + 1), "failure pilot completes a bounded startup unit");
        sim.failAfterAcceptedWindow = true;
        try { stepReceipt(sim, work, 9); throw new AssertionError("Expected forced failure after an actual accepted observation"); }
        catch (InjectedFailure expected) { }
        finally { sim.failAfterAcceptedWindow = false; work.cancel(); }
        check(subscriptions(sim).isEmpty() && behavior.getInputs() == 3 && sim.stopMessage == null,
            "finally closes every subscribed window even after accepted solve followed by forced failure");
    }

    @SuppressWarnings("unchecked")
    private static void missingHarnessNegative(TemporalSim sim, GeneratedBoardInstance owner, Rb30Behavior behavior) throws Exception {
        phase(sim, "MISSING_HARNESS_NEGATIVE");
        Field field = GeneratedComponentConnectionBindings.class.getDeclaredField("connectorHarnesses"); field.setAccessible(true);
        HashMap<String, CircuitPostMeasurementEndpoint[]> harnesses =
            (HashMap<String, CircuitPostMeasurementEndpoint[]>)field.get(owner.getConnectionBindings());
        GeneratedWork<GeneratedRepairStatus> work = behavior.beginProfile(sim, owner, GeneratedTemporalBehavior.Profile.REPAIR);
        for (int i = 0; i < 8; i++) check(stepReceipt(sim, work, i + 1), "missing-harness pilot reaches observation boundary");
        CircuitPostMeasurementEndpoint[] saved = harnesses.put("JOB", null);
        int advances = sim.advances; double time = sim.t;
        try {
            check(!behavior.healthy(owner, 3), "null customer harness conservatively rejects live health");
            check(stepReceipt(sim, work, 9) && sim.advances == advances && sim.t == time && subscriptions(sim).isEmpty(),
                "unavailable current harness neither advances a functional observation nor retains handles");
            harnesses.put("JOB", new CircuitPostMeasurementEndpoint[] {null, null});
            check(!behavior.healthy(owner, 3) && sim.t == time, "malformed null cable terminals cannot become a good reading");
        } finally { harnesses.put("JOB", saved); work.cancel(); }
    }

    private static void settle(TemporalSim sim, GeneratedBoardInstance owner) {
        sim.analyzeCircuit(); sim.solverExecutor.advanceSteps(8);
        // Consume native deferred UI flags only after actual analysis and accepted steps.
        settleFlags(sim);
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        GeneratedRuntimeInvariant.verify(owner, sim.boardModificationController, sim.elmList);
        owner.getConnectionBindings().validateAgainst(owner.getBoard(), owner.getSimulationElements(),
            owner.getComponentBindings(), owner.getExternalPowerBindings(), owner.getFaultBinding());
        settleFlags(sim);
    }
    private static void settleFlags(TemporalSim sim) {
        sim.generatedBoardVerificationPending = false; sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
    }
    @SuppressWarnings("unchecked")
    private static Vector<SolverTimeObservationService.Subscription> subscriptions(TemporalSim sim) throws Exception {
        Field field = SolverTimeObservationService.class.getDeclaredField("subscriptions"); field.setAccessible(true);
        return (Vector<SolverTimeObservationService.Subscription>)field.get(sim.solverTimeObservations);
    }

    /** Test-only boundary receipts; no solver or control command is issued here. */
    private static void phase(TemporalSim sim, String value) {
        sim.phase = value; receipt(sim, "PHASE", -1, 0, "ENTER");
    }
    private static boolean stepReceipt(TemporalSim sim, GeneratedWork<?> work, int unit) {
        receipt(sim, "UNIT_BEFORE", unit, UNITS, "BEGIN");
        boolean returned = false;
        try { boolean more = work.step(); returned = true; return more; }
        finally { receipt(sim, "UNIT_AFTER", unit, UNITS, returned ? "RETURNED" : "THREW"); }
    }
    private static void receipt(TemporalSim sim, String kind, int unit, int total, String outcome) {
        int count = -1;
        try { count = subscriptions(sim).size(); }
        catch (Exception unavailable) { /* Telemetry reports unknown; existing assertions retain their own failure. */ }
        System.out.println("RB56_TEMPORAL_PROGRESS {\"phase\":\"" + sim.phase + "\",\"kind\":\"" + kind +
            "\",\"unit\":" + unit + ",\"totalUnits\":" + total + ",\"outcome\":\"" + outcome +
            "\",\"monotonicElapsedMs\":" + Double.toString((System.nanoTime() - startedNanos) / 1e6) +
            ",\"simTime\":" + number(sim.t) + ",\"timeStep\":" + number(sim.timeStep) +
            ",\"acceptedSteps\":" + sim.a01AcceptedStepCount + ",\"attempts\":" + sim.a01IterationCount +
            ",\"newtonTrials\":" + sim.a01SubIterationCount + ",\"factorizations\":" + sim.a01FactorizationCount +
            ",\"solves\":" + sim.a01SolveCount + ",\"analyses\":" + sim.a01AnalysisCount +
            ",\"lastSubIteration\":" + sim.subIterations + ",\"reducedMatrix\":" + sim.circuitMatrixSize +
            ",\"fullMatrix\":" + sim.circuitMatrixFullSize + ",\"subscriptions\":" + count +
            ",\"advances\":" + sim.advances + ",\"lastAdvanceSeconds\":" + number(sim.lastDuration) + "}");
        System.out.flush();
    }
    private static String number(double value) { return PowerDomainContract.finite(value) ? Double.toString(value) : "null"; }

    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static final class InjectedFailure extends RuntimeException { }
    private static final class TemporalSim extends Q30ServiceFlowContractTest.NativeServiceCirSim {
        int advances, windowAdvances; double lastDuration; boolean failAfterAcceptedWindow;
        String phase = "INITIAL";
        TemporalSim() {
            Q30ServiceFlowContractTest.configureSimulator(this);
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            a01MeasurementRunning = true; // Existing counters observe, and never steer, the production solver.
        }
        @Override void advanceGeneratedTemporalProfile(double seconds) {
            advances++; lastDuration = seconds;
            receipt(this, "ADVANCE_BEFORE", -1, 0, "BEGIN"); boolean returned = false;
            try {
                Vector<SolverTimeObservationService.Subscription> windows = subscriptions(this);
                if (!windows.isEmpty()) check(seconds == .030 && windows.size() == 4,
                    "only actual two rails and two customer harnesses subscribe to a functional interval");
                super.advanceGeneratedTemporalProfile(seconds);
                if (!windows.isEmpty()) {
                    windowAdvances++;
                    for (SolverTimeObservationService.Subscription window : windows) {
                        SolverTimeSample[] samples = window.snapshot();
                        check(samples.length >= 3 && samples[0].getTime() <= t - .010 + 1e-12 &&
                            Math.abs(samples[samples.length - 1].getTime() - t) <= 1e-12,
                            "completed production solve actually commits the required ten-millisecond waveform");
                    }
                    if (failAfterAcceptedWindow) { failAfterAcceptedWindow = false; throw new InjectedFailure(); }
                }
                returned = true;
            } catch (RuntimeException failure) { throw failure;
            } catch (Exception failure) { throw new AssertionError("Unable to inspect native observation ownership", failure);
            } finally { receipt(this, "ADVANCE_AFTER", -1, 0, returned ? "RETURNED" : "THREW"); }
            settleFlags(this);
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message; circuitMatrix = null; stopElm = element; setSimRunning(false); analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
