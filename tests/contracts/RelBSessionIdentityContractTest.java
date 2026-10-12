package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Mathematical private replay identity laws; no generated owner or solver fixture. */
public final class RelBSessionIdentityContractTest {
    private static int assertions;
    private interface Attempt { void run(); }

    public static void main(String[] args) {
        verifyExistingPublicWire();
        verifyPrivateIdentity();
        verifyPreCancelGuards();
        System.out.println("PASS: REL-B session identity contracts assertions=" + assertions +
            " scope=IDENTITY_AND_ADMISSION_ONLY ownerRoundTrip=false");
    }

    private static void verifyExistingPublicWire() {
        String frozen = U06SessionSaveContractTest.crossEnvironmentFixture();
        check(frozen.equals(PlayerSessionSave.parse(frozen).encode()),
            "existing public session wire bytes remain exact");
        PlayerLaunchRequest request = new PlayerLaunchRequest(
            "LED_INDICATOR", "9007199254740993", "EASY");
        check("tsj-alpha/5/EASY/LED_INDICATOR/9007199254740993".equals(request.replay()),
            "normal replay identity remains unchanged");
        check(request.replay().equals(PlayerSessionReplay.parse(request.replay()).canonical()) &&
            !PlayerSessionReplay.parse(request.replay()).isPrivateQualification(),
            "available public replay still uses PlayerLaunchRequest");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/1/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1");
        } }, "public parser refuses the private replay namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/2/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-2/RB56_CONTROL/1");
        } }, "public parser refuses the versioned private replay namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/3/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-3/RB56_CONTROL/1");
        } }, "public parser refuses the private placement-3 replay namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-alpha/5/HARD/LED_INDICATOR/1");
        } }, "public parser continues refusing unavailable HARD");
    }

    private static void verifyPrivateIdentity() {
        for (String seed : new String[] {
                "9007199254741579", "-9007199254741579",
                "-9223372036854775808", "9223372036854775807" }) {
            long value = Long.parseLong(seed);
            GenerationRequest request =
                GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, value);
            PlayerSessionReplay identity = PlayerSessionReplay.forQualification(request);
            String expected = "tsj-private-session/3/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-3/RB56_CONTROL/" + seed;
            check(request.getRb56PlacementInterpretationVersion() ==
                    GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT &&
                    expected.equals(identity.canonical()) && identity.matches(request),
                "exact signed-long fresh private identity selects placement 3: " + seed);
            PlayerSessionSave original = emptySave(identity.canonical());
            PlayerSessionSave parsed = PlayerSessionSave.parse(original.encode());
            check(original.encode().equals(parsed.encode()) &&
                PlayerSessionReplay.parse(parsed.replay).matches(request),
                "placement-3 private identity survives the unchanged bounded session codec: " + seed);

            GenerationRequest compactRequest = request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_COMPACT);
            PlayerSessionReplay compact = PlayerSessionReplay.forQualification(compactRequest);
            String compactExpected = "tsj-private-session/2/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-2/RB56_CONTROL/" + seed;
            check(compactExpected.equals(compact.canonical()) && compact.matches(compactRequest) &&
                !compact.matches(request), "exact saved placement-2 identity remains distinct: " + seed);

            GenerationRequest legacyRequest = request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LEGACY);
            PlayerSessionReplay legacy = PlayerSessionReplay.forQualification(legacyRequest);
            String legacyExpected = "tsj-private-session/1/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/" + seed;
            check(legacyExpected.equals(legacy.canonical()) && legacy.matches(legacyRequest) &&
                !legacy.matches(request) && !legacy.matches(compactRequest),
                "exact historical private identity selects only placement 1: " + seed);
            check(!request.canonical().equals(legacyRequest.canonical()) &&
                !request.canonical().equals(compactRequest.canonical()) &&
                !compactRequest.canonical().equals(legacyRequest.canonical()),
                "placement 1, 2 and 3 have distinct plan-cache/request identities: " + seed);
            check(compactRequest.canonical().contains("placement-interpretation=2;") &&
                request.canonical().contains("placement-interpretation=3;") &&
                legacyRequest.canonical().indexOf("placement-interpretation=") < 0,
                "legacy canonical stays exact while placement 2 and 3 are explicitly keyed: " + seed);
        }
        for (final String invalid : new String[] {
                "tsj-private-session/2/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1",
                "tsj-private-session/2/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-1/RB56_CONTROL/1",
                "tsj-private-session/2/tsj-generation-dependencies-v15/rb56-placement-2/RB56_CONTROL/1",
                "tsj-private-session/2/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-2/RB56_CONTROL/1/extra",
                "tsj-private-session/3/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1",
                "tsj-private-session/3/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-2/RB56_CONTROL/1",
                "tsj-private-session/3/tsj-generation-dependencies-v15/rb56-placement-3/RB56_CONTROL/1",
                "tsj-private-session/3/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-3/RB56_CONTROL/1/extra",
                "tsj-private-session/1/tsj-generation-dependencies-v15/RB56_CONTROL/1",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/LED_INDICATOR/1",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56_control/1",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/+1",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/01",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/-0",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/9223372036854775808",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/-9223372036854775809",
                "tsj-private-session/1/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1/extra" }) {
            reject(new Attempt() { public void run() { PlayerSessionReplay.parse(invalid); } },
                "unknown epoch, family, version or noncanonical seed rejects");
        }
        final GenerationRequest normal = GenerationRequest.player(
            new PlayerLaunchRequest("LED_INDICATOR", "1", "EASY"));
        reject(new Attempt() { public void run() {
            PlayerSessionReplay.forQualification(normal);
        } }, "normal request cannot mint a private qualification identity");
    }

    private static void verifyPreCancelGuards() {
        final CirSim sim = new CirSim();
        sim.troubleshootDebug = true;
        sim.developerVerifierRunning = true;
        final GenerationCoordinator coordinator = new GenerationCoordinator(sim);
        sim.generationCoordinator = coordinator;
        final GenerationRequest savedRequest =
            GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, 41);
        final PlayerSessionSave saved = emptySave(
            PlayerSessionReplay.forQualification(savedRequest).canonical());
        final GenerationRequest wrongSeed =
            GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, 42);
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(wrongSeed, saved);
        } }, "seed mismatch rejects before cancellation");
        check(coordinator.getJob() == null,
            "identity mismatch leaves idle coordinator without a job");

        final GenerationRequest legacySavedRequest = savedRequest.withRb56PlacementInterpretationVersion(
            GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LEGACY);
        final PlayerSessionSave legacySaved = emptySave(
            PlayerSessionReplay.forQualification(legacySavedRequest).canonical());
        final GenerationRequest wrongLegacySeed =
            GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, 42)
                .withRb56PlacementInterpretationVersion(
                    GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LEGACY);
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(wrongLegacySeed, legacySaved);
        } }, "legacy placement identity still rejects a foreign seed before cancellation");
        check(coordinator.getJob() == null,
            "legacy identity mismatch leaves idle coordinator without a job");

        final GenerationRequest compactSameSeed = savedRequest.withRb56PlacementInterpretationVersion(
            GenerationRequest.RB56_PLACEMENT_INTERPRETATION_COMPACT);
        final PlayerSessionSave compactSaved = emptySave(
            PlayerSessionReplay.forQualification(compactSameSeed).canonical());
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(savedRequest, compactSaved);
        } }, "same-seed placement-2 save rejects against placement 3 before cancellation");
        check(coordinator.getJob() == null,
            "cross-placement mismatch leaves idle coordinator without a job");

        sim.troubleshootDebug = false;
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(savedRequest, saved);
        } }, "missing debug scope rejects before cancellation");
        check(coordinator.getJob() == null,
            "scope mismatch leaves idle coordinator without a job");
    }

    private static PlayerSessionSave emptySave(String replay) {
        return new PlayerSessionSave(replay, "contract", "realization", "state",
            new Vector<PlayerSessionSave.Operation>(), new Vector<PlayerSessionSave.Source>(),
            new Vector<PlayerSessionSave.Stress>(), new Vector<PlayerSessionSave.Fuse>());
    }

    private static void reject(Attempt attempt, String message) {
        boolean rejected = false;
        try { attempt.run(); } catch (RuntimeException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
