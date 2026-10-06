package com.lushprojects.circuitjs1.client;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/** Compiled, developer-only session reconstruction checks; never player-input evidence. */
final class U06SessionDeveloperVerifier {
    private final CirSim sim;
    private final JSONObject report = new JSONObject();
    private final JSONArray checks = new JSONArray(), generations = new JSONArray(), errors = new JSONArray();
    private PlayerLaunchRequest request;
    private PlayerSessionSave saved;
    private PlayerSessionSave rollbackSaved;
    private String resistorComponent, resistorCatalogEntry;
    private GenerationJob.Stage cancellationStage;
    private GeneratedBoardInstance beforeOwner;
    private Snapshot before;
    private String phase = "entry";
    private long actionsMillis;
    private boolean finished;
    private int inputCount, fuseFixtureCount;

    private U06SessionDeveloperVerifier(CirSim sim) { this.sim = sim; }

    static void verify(CirSim sim, String family, String seed) {
        U06SessionDeveloperVerifier verifier = new U06SessionDeveloperVerifier(sim);
        verifier.publish("RUNNING");
        try {
            verifier.check(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
                "private developer route required");
            PlayerFamilyCatalog.requireNormalPlayerEnabled(family);
            verifier.request = new PlayerLaunchRequest(family, seed,
                PlayerFamilyCatalog.candidateProfile(family).name());
            verifier.report.put("family", new JSONString(family));
            verifier.report.put("replay", new JSONString(verifier.request.replay()));
            verifier.report.put("playerInputEvidence", JSONBoolean.getInstance(false));
            verifier.report.put("damageFixtures", new JSONString("controlled state injection"));
            verifier.codec();
            sim.setSimRunning(false);
            if (sim.generationCoordinator == null) sim.generationCoordinator = new GenerationCoordinator(sim);
            verifier.generate(null, "normal-generation");
        } catch (Throwable failure) { verifier.fail(failure); }
    }

    private void generate(final PlayerSessionSave value, final String label) {
        phase = label;
        publish("RUNNING");
        GenerationCoordinator.Completion completion = new GenerationCoordinator.Completion() {
            public void complete(final GenerationJob job, final GeneratedBoardInstance published) {
                sim.setSimRunning(false);
                JSONObject result = new JSONObject();
                result.put("phase", new JSONString(label));
                result.put("outcome", new JSONString(job.getOutcome().name()));
                result.put("stage", new JSONString(job.getStage().name()));
                result.put("elapsedMillis", new JSONNumber(job.getElapsedMillis()));
                result.put("workUnits", new JSONNumber(job.getStepCount()));
                generations.set(generations.size(), result);
                // Leave the coordinator's completion stack before starting another request.
                new Timer() { public void run() {
                    if (finished) return;
                    long began = System.currentTimeMillis();
                    try {
                        if ("invalid-signature-restore".equals(label)) {
                            rejected(job, published);
                        } else if ("cancel-session-restore".equals(label)) {
                            cancelled(job, published);
                        } else {
                            check(job.getOutcome() == GenerationJob.Outcome.PASS && published != null,
                                label + " published normally");
                            check(sim.getGeneratedBoardInstance() == published &&
                                sim.getGeneratedChallengeController().isReady() && sim.isGeneratedRuntimeSettled(),
                                label + " actionable owner");
                            if (value == null) prepare(); else restored(published);
                        }
                    } catch (Throwable failure) { fail(failure); }
                    finally { actionsMillis += Math.max(0, System.currentTimeMillis() - began); publish(finished ?
                        (errors.size() == 0 ? "PASS" : "FAIL") : "RUNNING"); }
                } }.schedule(1);
            }
        };
        if (value == null) sim.generationCoordinator.start(request.generation(), completion, true);
        else sim.generationCoordinator.startSessionRestore(request.generation(), value, completion);
        if ("cancel-session-restore".equals(label)) awaitPrivateCancellation();
    }

    private void prepare() {
        phase = "semantic-actions";
        beforeOwner = sim.getGeneratedBoardInstance();
        PhysicalBoardRuntime runtime = beforeOwner.getPhysicalBoardRuntime();
        final Map<String, String> originalFaults = originalFaults(runtime);
        runtime.getSessionHistory().start();
        // The public controller does this after normal generation. Keep debug/query gating,
        // but allow these actual production actions to enter their owner's real journal.
        sim.developerVerifierRunning = false;
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        settle("initial source isolation");
        check(beforeOwner.getExternalPowerBindings().areAllDisconnected(), "mutation sources isolated");
        ReplaceableResistorBoardCapability resistor = null;
        for (PhysicalBoardRuntimeCapability capability : runtime.getCapabilities())
            if (capability instanceof ReplaceableResistorBoardCapability) {
                resistor = (ReplaceableResistorBoardCapability)capability; break;
            }
        check(resistor != null, "normal family has a replaceable resistor");
        String component = resistor.getSlot().getComponentId();
        PhysicalPart<?>[] purchases = exerciseSlot(component, true);
        ResistorStressState partial = resistor.getStressDamageSystem().getState(purchases[0].getId());
        ResistorStressState failed = resistor.getStressDamageSystem().getState(purchases[1].getId());

        ProtectionFuseElm partialFuse = null, blownFuse = null;
        for (String id : runtime.getSlotOrder()) {
            PhysicalPart<?> part = runtime.getInstalledPart(id);
            if (part == null || fuse(part) == null || runtime.getMutationProvider(id) == null) continue;
            PhysicalPart<?>[] fuseParts = exerciseSlot(id, false);
            blownFuse = fuse(fuseParts[0]); partialFuse = fuse(fuseParts[1]);
            check(blownFuse != null && partialFuse != null, "catalog fuses have real electrical backing");
            fuseFixtureCount = 2;
            break;
        }
        Snapshot preReset = new Snapshot(sim);
        int resetCount = runtime.getSessionHistory().operations().size();
        sim.resetAction(); settle("actual reset command");
        Vector<PlayerSessionSave.Operation> resetHistory = runtime.getSessionHistory().operations();
        check(resetHistory.size() == resetCount + 1 && "RESET".equals(resetHistory.lastElement().kind),
            "actual reset records exactly one successful RESET operation");
        Snapshot postReset = new Snapshot(sim);
        for (String key : preReset.values.keySet()) {
            if (key.startsWith("history:") || key.startsWith("stress:") || key.startsWith("fuse:") ||
                    "inputs".equals(key) || "power".equals(key)) continue;
            String prior = preReset.values.get(key), current = postReset.values.get(key);
            check(prior == null ? current == null : prior.equals(current),
                "actual reset preserves physical inventory identities and connections");
        }
        if (beforeOwner.getOperationCatalog() != null)
            for (GeneratedBoardOperation operation : beforeOwner.getOperationCatalog().getAll()) {
                if (GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(operation.getStableId())) continue;
                int count = runtime.getSessionHistory().operations().size();
                check(sim.invokeGeneratedPlayerOperation(operation.getStableId()), "family input command executed");
                settle("family input");
                check(runtime.getSessionHistory().operations().size() == count + 1,
                    "successful input has exactly one history entry");
                inputCount++;
            }
        Vector<String> sources = beforeOwner.getBoard().getPowerInputIds();
        check(!sources.isEmpty(), "source population present");
        for (int i = 0; i < sources.size(); i++) {
            ExternalPowerSimulationBinding binding = beforeOwner.getExternalPowerBindings().getBinding(sources.get(i));
            sim.changeBenchSource(beforeOwner, sources.get(i), Boolean.valueOf(i == 0),
                binding.getLimitedSupply() == null ? null : Double.valueOf(.03125));
            settle("source controls");
            check(binding.isConnected() == (i == 0) && (binding.getLimitedSupply() == null ||
                binding.getLimitedSupply().getLimitAmps() == .03125), "explicit source control expectation");
        }
        setDamage(partial, failed, partialFuse, blownFuse);
        sim.needAnalyze(); sim.requestGeneratedBoardVerification();
        settle("controlled permanent damage backing");
        // Freeze exact scalar fixtures after actual graph settlement. These values are test
        // inputs, not claimed observations of a player overheating a resistor or a fuse.
        setDamage(partial, failed, partialFuse, blownFuse);
        check(originalFaults.equals(originalFaults(runtime)), "private original faults survive physical actions");
        check(!sim.getGeneratedChallengeController().isCompleted(), "fixture has no completion grant");
        before = new Snapshot(sim);
        saved = PlayerSessionState.capture(sim, request, runtime.getSessionHistory().operations(), "u06-compiled");
        check(saved.getHistory().size() > 0 && saved.getStress().size() >= 3, "capture includes repairs and damage");
        String wire = saved.encode();
        check(isDigest(saved.realization) && isDigest(saved.stateSignature),
            "captured physical and semantic fingerprints are opaque SHA256 digests");
        check(wire.indexOf("GENERATED_FAULT") < 0 && wire.indexOf("physical-board@") < 0 &&
            wire.indexOf("player-semantic-state@") < 0 && wire.indexOf("P02-CUTS/1") < 0,
            "captured wire omits raw faults physical realization semantic state and copper");
        PlayerSessionSave parsed = PlayerSessionSave.parse(wire);
        check(saved.encode().equals(parsed.encode()), "captured wire roundtrip canonical");
        check(saved.stateSignature.equals(PlayerSessionState.semanticSignature(sim)), "capture matches independent live signature");
        sim.developerVerifierRunning = true;
        generate(parsed, "valid-session-restore");
    }

    private PhysicalPart<?>[] exerciseSlot(String component, boolean leadChecks) {
        PhysicalBoardRuntime runtime = beforeOwner.getPhysicalBoardRuntime();
        PhysicalSlotMutationProvider provider = runtime.getMutationProvider(component);
        WorkbenchPartsProvider shop = runtime.getWorkbenchPartsProvider(component);
        check(provider instanceof CatalogAcquisitionProvider && shop != null && !shop.getCatalogEntries().isEmpty(),
            "registered provider supports loose acquisition");
        PhysicalPart<?> original = runtime.getInstalledPart(component);
        String entry = shop.getCatalogEntries().firstElement().getId();
        if (leadChecks) {
            resistorComponent = component; resistorCatalogEntry = entry;
            check(runtime.getNextPartSerial(component + "_CATALOG_PART") == null,
                "pristine resistor catalog serial is unallocated");
        }
        int count = runtime.getSessionHistory().operations().size(), parts = runtime.getPartOrder().size();
        PhysicalPart<?> first = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(entry);
        settle("first catalog acquisition");
        PhysicalPart<?> second = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(entry);
        settle("second catalog acquisition");
        check(first != null && second != null && first != second && !first.getId().equals(second.getId()) &&
            !first.isOriginal() && !second.isOriginal() && !first.isInstalled() && !second.isInstalled(),
            "catalog creates distinct loose stock identities");
        check(runtime.getPartOrder().size() == parts + 2 && runtime.getSessionHistory().operations().size() == count + 2,
            "two acquisitions append exactly two parts and history entries");
        if (leadChecks)
            check((component + "_CATALOG_PART_0").equals(first.getId()) &&
                (component + "_CATALOG_PART_1").equals(second.getId()) &&
                Integer.valueOf(2).equals(runtime.getNextPartSerial(component + "_CATALOG_PART")),
                "initial stock uses independently expected zero-based identities and next serial");
        check(provider.removeInstalledPart(), "original removed through physical provider");
        settle("remove original");
        int afterRemove = runtime.getSessionHistory().operations().size();
        check(!provider.removeInstalledPart() && runtime.getSessionHistory().operations().size() == afterRemove,
            "failed empty-slot remove absent from history");
        check(provider.install(first.getId()), "catalog stock installed through physical provider");
        settle("install stock");
        check(original != null && runtime.getPart(original.getId()) == original && original.isOriginal() &&
            !original.isInstalled() && runtime.getInstalledPart(component) == first,
            "original retained loose independently of installed new stock");
        if (leadChecks) {
            String pad = runtime.getSlot(component).getPadIds().firstElement();
            BoardModificationController modifications = sim.getBoardModificationController();
            check(modifications.liftLead(component, pad), "lead lifted"); settle("lift lead");
            check(!modifications.isLeadConnected(component, pad), "lift changes real connection");
            check(modifications.reconnectLead(component, pad), "lead reconnected"); settle("reconnect lead");
            check(modifications.isLeadConnected(component, pad), "reconnect restores real connection");
            check(modifications.liftLead(component, pad), "saved final lead lifted"); settle("final lifted lead");
        }
        return new PhysicalPart<?>[] { first, second };
    }

    private void setDamage(ResistorStressState partial, ResistorStressState failed,
            ProtectionFuseElm partialFuse, ProtectionFuseElm blownFuse) {
        partial.accumulatedDamage = .375; partial.serviceTime = 12.5;
        partial.failed = false; partial.failureServiceTime = Double.NaN;
        partial.getPart().getSecondaryOpenPath().resetForBoardReset();
        failed.accumulatedDamage = 1.25; failed.serviceTime = 20;
        failed.failed = true; failed.failureServiceTime = 15;
        failed.getPart().getSecondaryOpenPath().open();
        if (partialFuse != null) { partialFuse.heat = partialFuse.i2t / 2; partialFuse.blown = false; }
        if (blownFuse != null) { blownFuse.heat = blownFuse.i2t * 1.5; blownFuse.blown = true; }
        check(partial.getAccumulatedDamage() == .375 && partial.getServiceTime() == 12.5 && !partial.isFailed() &&
            !partial.getPart().getSecondaryOpenPath().isOpen(), "partial resistor fixture independently specified");
        check(failed.getAccumulatedDamage() == 1.25 && failed.getServiceTime() == 20 && failed.isFailed() &&
            failed.getFailureServiceTime() == 15 && failed.getPart().getSecondaryOpenPath().isOpen(),
            "failed resistor fixture owns its electrical open path");
    }

    private void restored(GeneratedBoardInstance owner) {
        phase = "roundtrip-comparison";
        check(owner != beforeOwner && owner.getPhysicalBoardRuntime() != beforeOwner.getPhysicalBoardRuntime(),
            "restore publishes fresh graph and inventory owners");
        check(beforeOwner.getExternalPowerBindings().areAllDisconnected(), "retired owner sources disconnected");
        check(!PlayerSessionState.isRestoring(sim) && !FreshGeneratedRuntimeInstallation.isInProgress(sim),
            "private reconstruction scope cleared");
        before.compare(new Snapshot(sim), this);
        check(saved.stateSignature.equals(PlayerSessionState.semanticSignature(sim)), "restored semantic signature exact");
        check(!sim.getGeneratedChallengeController().isCompleted() &&
            sim.getGeneratedChallengeController().getCustomerRetestResult() == null,
            "restore imports neither completion nor customer retest");
        PlayerSessionHistory oldHistory = beforeOwner.getPhysicalBoardRuntime().getSessionHistory();
        PlayerSessionHistory newHistory = owner.getPhysicalBoardRuntime().getSessionHistory();
        check(oldHistory != newHistory, "history belongs to fresh runtime");
        newHistory.start();
        int oldCount = oldHistory.operations().size(), newCount = newHistory.operations().size();
        sim.developerVerifierRunning = false;
        String source = owner.getBoard().getPowerInputIds().firstElement();
        ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(source);
        sim.changeBenchSource(owner, source, Boolean.valueOf(!binding.isConnected()), null);
        settle("successor source command");
        continueAcquisition(owner);
        sim.changeBenchSource(owner, source, Boolean.TRUE, null);
        settle("protected owner source connected");
        check(binding.isConnected(), "late rollback protects a connected source");
        check(newHistory.operations().size() > newCount && oldHistory.operations().size() == oldCount,
            "successor commands cannot append to retired history");
        sim.developerVerifierRunning = true;
        beforeOwner = owner;
        before = new Snapshot(sim);
        PlayerSessionSave current = PlayerSessionState.capture(sim, request, newHistory.operations(), "u06-compiled");
        rollbackSaved = current;
        String corruptDigest = (current.stateSignature.charAt(0) == '0' ? "1" : "0") +
            current.stateSignature.substring(1);
        PlayerSessionSave invalid = new PlayerSessionSave(current.replay, current.build, current.realization,
            corruptDigest, current.getHistory(), current.getSources(),
            current.getStress(), current.getFuses());
        invalid = PlayerSessionSave.parse(invalid.encode());
        check(!invalid.stateSignature.equals(current.stateSignature), "invalid final signature is parser accepted");
        generate(invalid, "invalid-signature-restore");
    }

    private void continueAcquisition(GeneratedBoardInstance owner) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        PlayerSessionHistory history = runtime.getSessionHistory();
        String namespace = resistorComponent + "_CATALOG_PART";
        String expectedId = namespace + "_2";
        check(sim.getBoardPowerController().isElectricallyUnpowered() &&
            Integer.valueOf(2).equals(runtime.getNextPartSerial(namespace)) && runtime.getPart(expectedId) == null,
            "restored inventory independently expects next stock serial two");
        int parts = runtime.getPartOrder().size(), operations = history.operations().size();
        PhysicalSlotMutationProvider provider = runtime.getMutationProvider(resistorComponent);
        check(provider instanceof CatalogAcquisitionProvider, "restored provider supports continued acquisition");
        PhysicalPart<?> acquired = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(resistorCatalogEntry);
        settle("post-restore catalog acquisition");
        check(acquired != null && expectedId.equals(acquired.getId()) && !acquired.isInstalled() &&
            !acquired.isOriginal() && runtime.getPart(expectedId) == acquired && provider.ownsPart(expectedId) &&
            runtime.getPartOrder().size() == parts + 1 && expectedId.equals(runtime.getPartOrder().lastElement()) &&
            Integer.valueOf(3).equals(runtime.getNextPartSerial(namespace)),
            "post-restore acquisition uses exact next identity and advances serial once");
        Vector<PlayerSessionSave.Operation> entries = history.operations();
        check(entries.size() == operations + 1, "post-restore acquisition records exactly one operation");
        PlayerSessionSave.Operation recorded = entries.lastElement();
        check("ACQUIRE".equals(recorded.kind) && resistorComponent.equals(recorded.owner) &&
            resistorCatalogEntry.equals(recorded.argument) && expectedId.equals(recorded.result),
            "continued acquisition history names the independently expected new stock");
    }

    private void rejected(GenerationJob job, GeneratedBoardInstance published) {
        phase = "late-failure-rollback";
        check(published == null && job.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE &&
            job.getStage() == GenerationJob.Stage.PUBLISH && hasSignatureFailure(job.getFailure()),
            "private reconstruction rejects final signature at publication");
        check(sim.getGeneratedBoardInstance() == beforeOwner && sim.elmList == before.graph &&
            sim.getGeneratedChallengeController() == before.challenge &&
            beforeOwner.getPhysicalBoardRuntime() == before.runtime,
            "late failure restores exact prior owner graph controller and runtime");
        check(sim.isGeneratedRuntimeSettled() && !PlayerSessionState.isRestoring(sim) &&
            !FreshGeneratedRuntimeInstallation.isInProgress(sim) &&
            !sim.generationCoordinator.retainsSavedOwnersForDeveloperVerification(),
            "late failure cleanup fully settled and released");
        before.compare(new Snapshot(sim), this);
        check(before.history == beforeOwner.getPhysicalBoardRuntime().getSessionHistory(),
            "failed restore preserves exact prior history owner");
        report.put("inputCommands", new JSONNumber(inputCount));
        report.put("resistorDamageFixtures", new JSONNumber(2));
        report.put("fuseDamageFixtures", new JSONNumber(fuseFixtureCount));
        report.put("fuseScope", new JSONString(fuseFixtureCount == 0 ? "NOT APPLICABLE: no replaceable fuse" : "partial and blown"));
        if ("LED_INDICATOR".equals(request.familyId)) generate(rollbackSaved, "cancel-session-restore");
        else finished = true;
    }

    private void awaitPrivateCancellation() {
        final GenerationJob expected = sim.generationCoordinator.getJob();
        new Timer() { public void run() {
            if (finished) return;
            try {
                if (sim.generationCoordinator.getJob() != expected)
                    throw new IllegalStateException("Cancellation observer lost its exact job");
                if (!expected.isRunning()) return; // Its typed completion owns terminal acceptance.
                GenerationJob.Stage stage = expected.getStage();
                if ((stage == GenerationJob.Stage.PHYSICAL || stage == GenerationJob.Stage.HYPOTHESES) &&
                        sim.getGeneratedBoardInstance() != beforeOwner && FreshGeneratedRuntimeInstallation.isInProgress(sim)) {
                    cancellationStage = stage;
                    report.put("cancelPrivateStage", new JSONString(stage.name()));
                    check(true, "restore cancellation reached an actual private candidate stage");
                    sim.generationCoordinator.cancel();
                    return;
                }
                // The ordinary coordinator retains its own unchanged 90-second watchdog.
                schedule(1);
            } catch (Throwable failure) { fail(failure); }
        } }.schedule(1);
    }

    private void cancelled(GenerationJob job, GeneratedBoardInstance published) {
        phase = "restore-cancellation-rollback";
        check(cancellationStage != null && job.getStage() == cancellationStage &&
            job.getOutcome() == GenerationJob.Outcome.CANCELLED && published == null,
            "private restore cancellation completes with typed CANCELLED and no publication");
        check(sim.getGeneratedBoardInstance() == beforeOwner && sim.elmList == before.graph &&
            sim.getGeneratedChallengeController() == before.challenge &&
            beforeOwner.getPhysicalBoardRuntime() == before.runtime &&
            beforeOwner.getPhysicalBoardRuntime().getSessionHistory() == before.history,
            "cancelled restore preserves exact prior graph controller runtime and history owners");
        check(sim.isGeneratedRuntimeSettled() && !PlayerSessionState.isRestoring(sim) &&
            !FreshGeneratedRuntimeInstallation.isInProgress(sim) &&
            !sim.generationCoordinator.retainsSavedOwnersForDeveloperVerification(),
            "cancelled restore fully releases private reconstruction ownership");
        before.compare(new Snapshot(sim), this);
        check(rollbackSaved.stateSignature.equals(PlayerSessionState.semanticSignature(sim)),
            "cancelled restore preserves prior semantic digest and connected sources");
        controllerBoundaries();
        finished = true;
    }

    private void controllerBoundaries() {
        PlayerSessionController controller = new PlayerSessionController(sim);
        int preparing = controller.session.begin(request);
        check(controller.session.prepared(preparing, request, beforeOwner),
            "controller boundary fixture adopts the actual current owner");
        GenerationJob terminalJob = sim.generationCoordinator.getJob();
        int token = controller.session.token();
        int closedView = controller.openView();
        controller.closeView(closedView);
        check("This file selection belongs to a closed view.".equals(controller.action(token, closedView,
            "load-session", rollbackSaved.encode(), "", "")), "production controller rejects a closed file-selection view");
        controllerUnchanged(terminalJob);
        int currentView = controller.openView();
        check("This control belongs to an earlier session.".equals(controller.action(preparing, currentView,
            "load-session", rollbackSaved.encode(), "", "")), "production controller rejects an earlier session token");
        controllerUnchanged(terminalJob);
        for (String invalidLimit : new String[] { "1000", "0.0001" }) {
            // Build intentionally invalid wire directly; using the DTO constructor here
            // would reject before the production controller/parser boundary is exercised.
            String wire = PlayerSessionSave.SCHEMA + "\n" + PlayerSessionSave.MODEL + "\nrestart\n" +
                wireField(rollbackSaved.replay) + wireField(rollbackSaved.build) +
                wireField(rollbackSaved.realization) + wireField(rollbackSaved.stateSignature) +
                "HISTORY\n1\n" + wireField("SOURCE") +
                wireField(beforeOwner.getBoard().getPowerInputIds().firstElement()) +
                wireField("CONNECTED") + wireField(invalidLimit) +
                "SOURCES\n0\nSTRESS\n0\nFUSES\n0\nEND\n";
            check("This session file is incomplete, incompatible or unsupported. The current board is unchanged.".equals(
                controller.action(token, currentView, "load-session", wire, "", "")),
                "production controller rejects out-of-envelope source history before launch");
            controllerUnchanged(terminalJob);
        }
        report.put("controllerBoundaryEvidence", new JSONString("direct production controller calls; no FileReader or player-input evidence"));
    }

    private void controllerUnchanged(GenerationJob terminalJob) {
        check(sim.generationCoordinator.getJob() == terminalJob && !sim.generationCoordinator.isRunning() &&
            sim.getGeneratedBoardInstance() == beforeOwner && sim.elmList == before.graph &&
            sim.getGeneratedChallengeController() == before.challenge &&
            beforeOwner.getPhysicalBoardRuntime() == before.runtime &&
            beforeOwner.getPhysicalBoardRuntime().getSessionHistory() == before.history &&
            before.history.operations().size() == rollbackSaved.getHistory().size() &&
            sim.isGeneratedRuntimeSettled() && rollbackSaved.stateSignature.equals(PlayerSessionState.semanticSignature(sim)),
            "rejected controller load preserves exact current owners history state and idle job");
    }

    private static String wireField(String value) { return value.length() + ":" + value + "\n"; }

    private static boolean hasSignatureFailure(Throwable failure) {
        for (int i = 0; failure != null && i < 12; i++, failure = failure.getCause())
            if ("Session reconstruction does not match the saved semantic state".equals(failure.getMessage())) return true;
        return false;
    }

    private void settle(String label) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, sim.getGeneratedBoardInstance(), "U06 " + label);
        sim.setSimRunning(false);
    }

    private void codec() {
        check("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".equals(
                PlayerSessionFingerprint.of("")), "compiled SHA256 empty standard vector");
        check("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(
                PlayerSessionFingerprint.of("abc")), "compiled SHA256 abc standard vector");
        check("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1".equals(
                PlayerSessionFingerprint.of("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")),
            "compiled SHA256 two-block standard vector");
        // These bytes are deliberately also frozen in the independent native contract test.
        String wire = "tsj-session/1\ntsj-session-model/1\nrestart\n" +
            "47:tsj-alpha/4/EASY/LED_INDICATOR/9007199254740993\n" +
            "13:fixture-build\n19:fixture-realization\n13:fixture-state\n" +
            "HISTORY\n1\n5:RESET\n0:\n0:\n0:\n" +
            "SOURCES\n1\n4:MAIN\n9:CONNECTED\n16:3fd0000000000000\n" +
            "STRESS\n1\n2:R1\n16:3fe0000000000000\n16:4010000000000000\n" +
            "7:HEALTHY\n4:NONE\n" +
            "FUSES\n1\n2:F1\n16:3fc0000000000000\n6:INTACT\nEND\n";
        PlayerSessionSave parsed = PlayerSessionSave.parse(wire);
        check(wire.equals(parsed.encode()), "independent JVM and compiled GWT fixture bytes agree");
        check(PlayerLaunchRequest.parse(parsed.replay).seed == 9007199254740993L &&
            parsed.getSources().firstElement().limitAmps == .25 &&
            parsed.getStress().firstElement().damage == .5 && parsed.getStress().firstElement().serviceTime == 4 &&
            parsed.getFuses().firstElement().heat == .125 && !parsed.getFuses().firstElement().blown,
            "codec exact long and numeric fixture expectations");
        boolean rejected = false;
        try { PlayerSessionSave.parse(wire.substring(0, wire.length() - 1)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "truncated artifact rejected by compiled parser");
    }

    /** Independent values collected directly from owners, never from capture or save DTOs. */
    private static final class Snapshot {
        final Map<String, String> values = new LinkedHashMap<String, String>();
        final Vector<CircuitElm> graph;
        final GeneratedChallengeController challenge;
        final PhysicalBoardRuntime runtime;
        final PlayerSessionHistory history;
        Snapshot(CirSim sim) {
            GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
            graph = sim.elmList; challenge = sim.getGeneratedChallengeController();
            runtime = owner.getPhysicalBoardRuntime(); history = runtime.getSessionHistory();
            put("family", owner.getCircuitFamilyId()); put("seed", Long.toString(owner.getSeed()));
            put("fault", owner.getFaultBinding().getFault().getHypothesisKey());
            put("inputs", owner.getFamilyState() == null ? "NONE" : owner.getFamilyState().getSessionInputSignature());
            put("power", sim.getBoardPowerController().getState().name());
            put("copperCuts", owner.getCurrentConductorSnapshot().getCutEdgeIds().toString());
            put("pristineCopperOwner", Boolean.toString(owner.getCurrentConductorSnapshot().getGraph() ==
                owner.getPristineConductorGraph()));
            put("partOrder", runtime.getPartOrder().toString()); put("slotOrder", runtime.getSlotOrder().toString());
            for (String id : runtime.getSlotOrder()) {
                PhysicalBoardSlot slot = runtime.getSlot(id);
                put("slot:" + id, slot.getId() + "/" + slot.getPhysicalPackage().getId() + "/" +
                    (slot.getInstalledPart() == null ? "EMPTY" : slot.getInstalledPart().getId()));
                put("pads:" + id, slot.getPadIds().toString()); put("nets:" + id, slot.getNetIds().toString());
                put("terminals:" + id, slot.getTerminalIds().toString());
                put("slotGeometry:" + id, geometry(slot.getGeometryRealization()));
            }
            for (PhysicalPart<?> part : runtime.getPhysicalParts()) {
                String id = part.getId();
                put("part:" + id, part.getSpecification().getSpecificationId() + "/" + part.getPackage().getId() + "/" +
                    part.getOrientation().name() + "/" + part.getProvenance().getKind() + "/" + part.getProvenance().getSourceId());
                put("mount:" + id, part.getBoardSlot() == null ? "LOOSE" : part.getBoardSlot().getId());
                put("failure:" + id, part.getFailureState().getKind() + "/" + part.getFailureState().isFailed() + "/" + part.isFaulted());
                put("geometry:" + id, geometry(part.getGeometryRealization()));
                put("inventory:" + id, runtime.getInventoryIdForPart(id));
                for (PhysicalPartTerminal terminal : part.getTerminals())
                    put("terminal:" + terminal.getId(), terminal.getTerminalName());
                ProtectionFuseElm fuse = fuse(part);
                if (fuse != null) put("fuse:" + id, bits(fuse.heat) + "/" + fuse.blown + "/" + bits(fuse.i2t));
            }
            for (String inventory : runtime.getInventoryIds())
                put("inventoryOrder:" + inventory, runtime.getInventoryPartIds(inventory).toString());
            for (String namespace : runtime.getPartSerialNamespaces())
                put("nextSerial:" + namespace, runtime.getNextPartSerial(namespace).toString());
            for (String component : owner.getBoard().getComponentIds())
                for (GeneratedComponentConnectionBinding binding : owner.getConnectionBindings().getForComponentOrEmpty(component)) {
                    put("connection:" + component + ":" + binding.getPadId(),
                        Boolean.toString(sim.getBoardModificationController().isLeadConnected(component, binding.getPadId())));
                    Vector<String> attached = new Vector<String>();
                    for (PhysicalPart<?> part : runtime.getPhysicalParts())
                        for (PhysicalPartTerminal terminal : part.getTerminals())
                            if (GeneratedComponentConnectionBindings.sameEndpoint(terminal.getEndpoint(), binding.getComponentEndpoint()))
                                attached.add(terminal.getId());
                    put("endpointMapping:" + component + ":" + binding.getPadId(), attached.toString());
                }
            for (String id : owner.getBoard().getPowerInputIds()) {
                ExternalPowerSimulationBinding source = owner.getExternalPowerBindings().getBinding(id);
                put("source:" + id, source.isConnected() + "/" +
                    (source.getLimitedSupply() == null ? "NONE" : bits(source.getLimitedSupply().getLimitAmps())));
            }
            for (PhysicalBoardRuntimeCapability capability : runtime.getCapabilities())
                if (capability instanceof ReplaceableResistorBoardCapability)
                    for (ResistorStressState state : ((ReplaceableResistorBoardCapability)capability).getStressDamageSystem().getStates())
                        put("stress:" + state.getPart().getId(), bits(state.getAccumulatedDamage()) + "/" + bits(state.getServiceTime()) +
                            "/" + state.isFailed() + "/" + bits(state.getFailureServiceTime()) + "/" + state.getPart().getSecondaryOpenPath().isOpen());
            int index = 0;
            for (PlayerSessionSave.Operation operation : history.operations())
                put("history:" + index++, operation.kind + "\n" + operation.owner + "\n" + operation.argument + "\n" + operation.result);
        }
        private void put(String key, String value) { values.put(key, value); }
        void compare(Snapshot actual, U06SessionDeveloperVerifier verifier) {
            verifier.check(values.keySet().equals(actual.values.keySet()), "independent state field population exact");
            for (String key : values.keySet()) {
                String value = values.get(key), found = actual.values.get(key);
                // Report the category only: target identities and hidden fault values stay private.
                verifier.check(value == null ? found == null : value.equals(found),
                    "independent " + key.substring(0, key.indexOf(':') < 0 ? key.length() : key.indexOf(':')) + " value exact");
            }
            verifier.report.put("comparedLiveFields", new JSONNumber(values.size()));
        }
    }

    private static Map<String, String> originalFaults(PhysicalBoardRuntime runtime) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (PhysicalPart<?> part : runtime.getPhysicalParts()) if (part.isOriginal())
            result.put(part.getId(), part.getFailureState().getKind() + "/" + part.isFaulted());
        return result;
    }
    private static ProtectionFuseElm fuse(PhysicalPart<?> part) {
        for (CircuitElm element : part.getElectricalBacking().getCircuitElements())
            if (element instanceof ProtectionFuseElm) return (ProtectionFuseElm)element;
        return null;
    }
    private static String geometry(PhysicalGeometryRealization value) { return value == null ? "UNFORMED" : value.fingerprint(); }
    private static String bits(double value) { return Long.toHexString(Double.doubleToLongBits(value)); }
    private static boolean isDigest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
        checks.set(checks.size(), new JSONString(label));
    }
    private void fail(Throwable failure) {
        if (finished) return;
        errors.set(errors.size(), new JSONString(phase + ": " + failure.getClass().getName() + ": " + failure.getMessage()));
        finished = true;
        if (sim != null) { sim.developerVerifierRunning = true; sim.setSimRunning(false); }
        publish("FAIL");
    }
    private void publish(String state) {
        report.put("state", new JSONString(state)); report.put("phase", new JSONString(phase));
        report.put("assertions", new JSONNumber(checks.size())); report.put("checks", checks);
        report.put("generations", generations); report.put("errors", errors);
        report.put("actionsElapsedMillis", new JSONNumber(actionsMillis));
        publishAttributes(state, report.toString());
    }
    private static native void publishAttributes(String state, String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-u06-state", state);
        $doc.documentElement.setAttribute("data-tsj-u06-report", report);
    }-*/;
}
