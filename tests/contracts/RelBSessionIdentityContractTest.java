package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Mathematical private replay identity laws; no generated owner or solver fixture. */
public final class RelBSessionIdentityContractTest {
    private static int assertions;
    private interface Attempt { void run(); }

    public static void main(String[] args) {
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/12/" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-12/RB56_CONTROL/1");
        } }, "public parser refuses the private placement12 namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/13/" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-13/RB56_CONTROL/1");
        } }, "public parser refuses the private placement13 namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/14/" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-14/RB56_CONTROL/1");
        } }, "public parser refuses the private placement14 namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/15/" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-15/RB56_CONTROL/1");
        } }, "public parser refuses the private placement15 namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/11/" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-11/RB56_CONTROL/1");
        } }, "public parser still refuses saved private placement11");
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
        check("tsj-alpha/6/EASY/LED_INDICATOR/9007199254740993".equals(request.replay()),
            "normal replay identity uses the current epoch");
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
            PlayerLaunchRequest.parse("tsj-private-session/4/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-4/RB56_CONTROL/1");
        } }, "public parser refuses the private placement4 namespace");
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/7/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-7/RB56_CONTROL/1");
        } }, "public parser refuses the private placement7 namespace");
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
            String expected = "tsj-private-session/15/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-15/RB56_CONTROL/" + seed;
            check(request.getRb56PlacementInterpretationVersion() ==
                    GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT &&
                    expected.equals(identity.canonical()) && identity.matches(request),
                "exact signed-long fresh private identity selects placement 15: " + seed);
            PlayerSessionSave original = emptySave(identity.canonical());
            PlayerSessionSave parsed = PlayerSessionSave.parse(original.encode());
            check(original.encode().equals(parsed.encode()) &&
                PlayerSessionReplay.parse(parsed.replay).matches(request),
                "placement-15 private identity survives the unchanged bounded session codec: " + seed);
            GenerationRequest canvasRequest = request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CANVAS);
            String canvasExpected = "tsj-private-session/14/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-14/RB56_CONTROL/" + seed;
            PlayerSessionReplay canvasIdentity = PlayerSessionReplay.forQualification(canvasRequest);
            check(canvasRequest.getRb56PlacementInterpretationVersion() ==
                    GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CANVAS &&
                    canvasExpected.equals(canvasIdentity.canonical()) && canvasIdentity.matches(canvasRequest),
                "explicit saved14 retains its exact canvas-owner replay identity: " + seed);
            PlayerSessionSave canvasSave = emptySave(canvasExpected);
            PlayerSessionSave canvasParsed = PlayerSessionSave.parse(canvasSave.encode());
            check(canvasSave.encode().equals(canvasParsed.encode()) &&
                    PlayerSessionReplay.parse(canvasParsed.replay).matches(canvasRequest),
                "saved14 identity survives unchanged session framing: " + seed);
            check(!identity.matches(canvasRequest) && !canvasIdentity.matches(request),
                "saved14 and current15 identities reject each other in both directions: " + seed);
            GenerationRequest nearestVacancyRequest = request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_NEAREST_VACANCY);
            String nearestVacancyExpected = "tsj-private-session/13/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-13/RB56_CONTROL/" + seed;
            PlayerSessionReplay nearestVacancyIdentity =
                PlayerSessionReplay.forQualification(nearestVacancyRequest);
            check(nearestVacancyRequest.getRb56PlacementInterpretationVersion() ==
                    GenerationRequest.RB56_PLACEMENT_INTERPRETATION_NEAREST_VACANCY &&
                    nearestVacancyExpected.equals(nearestVacancyIdentity.canonical()) &&
                    nearestVacancyIdentity.matches(nearestVacancyRequest),
                "explicit saved13 retains the placement13 replay identity: " + seed);
            PlayerSessionSave nearestVacancySave = emptySave(nearestVacancyExpected);
            PlayerSessionSave nearestVacancyParsed =
                PlayerSessionSave.parse(nearestVacancySave.encode());
            check(nearestVacancySave.encode().equals(nearestVacancyParsed.encode()) &&
                    PlayerSessionReplay.parse(nearestVacancyParsed.replay)
                        .matches(nearestVacancyRequest),
                "saved13 identity survives unchanged session framing: " + seed);
            check(!identity.matches(nearestVacancyRequest) &&
                    !nearestVacancyIdentity.matches(request),
                "saved13 and current15 identities reject each other in both directions: " + seed);
            GenerationRequest roleWidthsRequest = request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_ROLE_WIDTHS);
            String roleWidthsExpected = "tsj-private-session/12/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-12/RB56_CONTROL/" + seed;
            PlayerSessionReplay roleWidthsIdentity = PlayerSessionReplay.forQualification(roleWidthsRequest);
            check(roleWidthsExpected.equals(roleWidthsIdentity.canonical()) &&
                    roleWidthsIdentity.matches(roleWidthsRequest) &&
                    roleWidthsRequest.getRb56PlacementInterpretationVersion() ==
                        GenerationRequest.RB56_PLACEMENT_INTERPRETATION_ROLE_WIDTHS,
                "explicit saved12 keeps its exact private identity and placement interpretation: " + seed);
            check(!identity.matches(roleWidthsRequest) && !roleWidthsIdentity.matches(request),
                "saved12 and current15 remain distinct in both replay directions: " + seed);
            GenerationRequest vacancyRequest=request.withRb56PlacementInterpretationVersion(GenerationRequest.RB56_PLACEMENT_INTERPRETATION_VACANCY);
            String vacancyExpected="tsj-private-session/11/"+GenerationDependencyContext.INTERPRETATION_EPOCH+"/rb56-placement-11/RB56_CONTROL/"+seed;
            check(vacancyExpected.equals(PlayerSessionReplay.forQualification(vacancyRequest).canonical()) &&
                PlayerSessionReplay.parse(vacancyExpected).matches(vacancyRequest) &&
                !PlayerSessionReplay.parse(vacancyExpected).matches(request),"saved11 stays distinct from current15: "+seed);
            check(emptySave(vacancyExpected).encode().equals(PlayerSessionSave.parse(emptySave(vacancyExpected).encode()).encode()),
                "saved11 bounded wire remains exact: "+seed);
            GenerationRequest localRequest=request.withRb56PlacementInterpretationVersion(GenerationRequest.RB56_PLACEMENT_INTERPRETATION_PIN_LOCAL);
            String localExpected="tsj-private-session/10/"+GenerationDependencyContext.INTERPRETATION_EPOCH+"/rb56-placement-10/RB56_CONTROL/"+seed;
            check(localExpected.equals(PlayerSessionReplay.forQualification(localRequest).canonical()) &&
                PlayerSessionReplay.parse(localExpected).matches(localRequest) &&
                !PlayerSessionReplay.parse(localExpected).matches(request),"saved10 stays distinct from current15: "+seed);
            GenerationRequest connectedRequest=request.withRb56PlacementInterpretationVersion(GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CONNECTED);
            String connectedExpected="tsj-private-session/9/"+GenerationDependencyContext.INTERPRETATION_EPOCH+"/rb56-placement-9/RB56_CONTROL/"+seed;
            check(connectedExpected.equals(PlayerSessionReplay.forQualification(connectedRequest).canonical()) && PlayerSessionReplay.parse(connectedExpected).matches(connectedRequest) && !PlayerSessionReplay.parse(connectedExpected).matches(request),"saved9 stays distinct from current15: "+seed);
            GenerationRequest narrowRequest=request.withRb56PlacementInterpretationVersion(GenerationRequest.RB56_PLACEMENT_INTERPRETATION_NARROW);
            String narrowExpected="tsj-private-session/8/"+GenerationDependencyContext.INTERPRETATION_EPOCH+"/rb56-placement-8/RB56_CONTROL/"+seed;
            check(narrowExpected.equals(PlayerSessionReplay.forQualification(narrowRequest).canonical()) && PlayerSessionReplay.parse(narrowExpected).matches(narrowRequest) && !PlayerSessionReplay.parse(narrowExpected).matches(request),"saved8 stays distinct from current15: "+seed);
            GenerationRequest guardedRequest=request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_GUARDED);
            String guardedExpected="tsj-private-session/7/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-7/RB56_CONTROL/"+seed;
            check(guardedExpected.equals(PlayerSessionReplay.forQualification(guardedRequest).canonical()) &&
                PlayerSessionReplay.parse(guardedExpected).matches(guardedRequest) &&
                !PlayerSessionReplay.parse(guardedExpected).matches(request),"saved7 stays distinct from current15: "+seed);

            GenerationRequest realismRequest = request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_REALISM);
            PlayerSessionReplay realism = PlayerSessionReplay.forQualification(realismRequest);
            String realismExpected = "tsj-private-session/3/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-3/RB56_CONTROL/" + seed;
            check(realismExpected.equals(realism.canonical()) && realism.matches(realismRequest) &&
                !realism.matches(request), "saved placement3 identity stays distinct from current15: " + seed);
            PlayerSessionSave realismSave = emptySave(realismExpected);
            check(realismSave.encode().equals(PlayerSessionSave.parse(realismSave.encode()).encode()),
                "saved3 bounded session wire remains exact: " + seed);

            GenerationRequest singleRequest=request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_SINGLE_GROUP);
            String singleExpected="tsj-private-session/6/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-6/RB56_CONTROL/"+seed;
            PlayerSessionReplay single=PlayerSessionReplay.forQualification(singleRequest);
            check(singleExpected.equals(single.canonical()) && single.matches(singleRequest) && !single.matches(request),
                "saved6 stays distinct from current15: "+seed);
            check(emptySave(singleExpected).encode().equals(PlayerSessionSave.parse(emptySave(singleExpected).encode()).encode()),
                "saved6 wire remains exact: "+seed);
            GenerationRequest largeRequest=request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LARGE);
            String largeExpected="tsj-private-session/5/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-5/RB56_CONTROL/"+seed;
            PlayerSessionReplay large=PlayerSessionReplay.forQualification(largeRequest);
            check(largeExpected.equals(large.canonical()) && large.matches(largeRequest) && !large.matches(request),
                "published saved5 stays distinct from current15: "+seed);
            check(emptySave(largeExpected).encode().equals(PlayerSessionSave.parse(emptySave(largeExpected).encode()).encode()),
                "saved5 wire remains exact: "+seed);
            GenerationRequest denseRequest=request.withRb56PlacementInterpretationVersion(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_DENSE);
            String denseExpected="tsj-private-session/4/"+
                GenerationDependencyContext.INTERPRETATION_EPOCH+"/rb56-placement-4/RB56_CONTROL/"+seed;
            PlayerSessionReplay dense=PlayerSessionReplay.forQualification(denseRequest);
            check(denseExpected.equals(dense.canonical()) && dense.matches(denseRequest) &&
                !dense.matches(request),"published saved4 stays distinct from current15: "+seed);
            PlayerSessionSave denseSave=emptySave(denseExpected);
            check(denseSave.encode().equals(PlayerSessionSave.parse(denseSave.encode()).encode()),
                "saved4 wire remains exact: "+seed);

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
                !compactRequest.canonical().equals(legacyRequest.canonical()) &&
                !request.canonical().equals(roleWidthsRequest.canonical()) &&
                !request.canonical().equals(nearestVacancyRequest.canonical()) &&
                !request.canonical().equals(canvasRequest.canonical()) &&
                !canvasRequest.canonical().equals(nearestVacancyRequest.canonical()) &&
                !nearestVacancyRequest.canonical().equals(roleWidthsRequest.canonical()),
                "placement 1, 2, 12, 13, 14 and 15 have distinct plan-cache/request identities: " + seed);
            check(compactRequest.canonical().contains("placement-interpretation=2;") &&
                roleWidthsRequest.canonical().contains("placement-interpretation=12;") &&
                nearestVacancyRequest.canonical().contains("placement-interpretation=13;") &&
                canvasRequest.canonical().contains("placement-interpretation=14;") &&
                request.canonical().contains("placement-interpretation=15;") &&
                legacyRequest.canonical().indexOf("placement-interpretation=") < 0,
                "legacy canonical stays exact while saved12, saved13, saved14 and current15 are explicitly keyed: " + seed);
        }
        reject(new Attempt() { public void run() {
            PlayerLaunchRequest.parse("tsj-private-session/5/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-5/RB56_CONTROL/1");
        } }, "public parser still refuses saved private5");
        for (final String invalid : new String[] {
                "tsj-private-session/12/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1",
                "tsj-private-session/12/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-11/RB56_CONTROL/1",
                "tsj-private-session/11/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-12/RB56_CONTROL/1",
                "tsj-private-session/12/tsj-generation-dependencies-v15/rb56-placement-12/RB56_CONTROL/1",
                "tsj-private-session/12/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-12/RB56_CONTROL/1/extra",
                "tsj-private-session/12/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-13/RB56_CONTROL/1",
                "tsj-private-session/13/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-12/RB56_CONTROL/1",
                "tsj-private-session/13/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-14/RB56_CONTROL/1",
                "tsj-private-session/13/tsj-generation-dependencies-v15/rb56-placement-13/RB56_CONTROL/1",
                "tsj-private-session/13/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-13/RB56_CONTROL/1/extra",
                "tsj-private-session/14/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-13/RB56_CONTROL/1",
                "tsj-private-session/14/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1",
                "tsj-private-session/14/tsj-generation-dependencies-v15/rb56-placement-14/RB56_CONTROL/1",
                "tsj-private-session/14/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-14/RB56_CONTROL/1/extra",
                "tsj-private-session/15/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-14/RB56_CONTROL/1",
                "tsj-private-session/14/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-15/RB56_CONTROL/1",
                "tsj-private-session/15/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-15/RB56_CONTROL/1/extra",
                "tsj-private-session/16/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-16/RB56_CONTROL/1",
                "tsj-private-session/7/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1",
                "tsj-private-session/7/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-4/RB56_CONTROL/1",
                "tsj-private-session/7/tsj-generation-dependencies-v15/rb56-placement-7/RB56_CONTROL/1",
                "tsj-private-session/7/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-7/RB56_CONTROL/1/extra",
                "tsj-private-session/4/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/RB56_CONTROL/1",
                "tsj-private-session/4/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-3/RB56_CONTROL/1",
                "tsj-private-session/4/tsj-generation-dependencies-v15/rb56-placement-4/RB56_CONTROL/1",
                "tsj-private-session/4/" + GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-4/RB56_CONTROL/1/extra",
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
        } }, "same-seed placement-2 save rejects against current placement 15 before cancellation");
        check(coordinator.getJob() == null,
            "cross-placement mismatch leaves idle coordinator without a job");

        final GenerationRequest saved13Request = savedRequest.withRb56PlacementInterpretationVersion(
            GenerationRequest.RB56_PLACEMENT_INTERPRETATION_NEAREST_VACANCY);
        final PlayerSessionSave saved13 = emptySave(
            PlayerSessionReplay.forQualification(saved13Request).canonical());
        final GenerationRequest saved14Request = savedRequest.withRb56PlacementInterpretationVersion(
            GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CANVAS);
        final PlayerSessionSave saved14 = emptySave(
            PlayerSessionReplay.forQualification(saved14Request).canonical());
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(savedRequest, saved13);
        } }, "saved13 replay rejects against current15 request before coordinator mutation");
        check(coordinator.getJob() == null,
            "saved13/current15 override mismatch leaves coordinator idle");
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(saved13Request, saved);
        } }, "current15 replay rejects against explicit saved13 request before coordinator mutation");
        check(coordinator.getJob() == null,
            "current15/saved13 override mismatch leaves coordinator idle");
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(saved13Request, saved14);
        } }, "saved14 replay rejects against explicit saved13 request before coordinator mutation");
        check(coordinator.getJob() == null,
            "saved13/saved14 override mismatch leaves coordinator idle");
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(saved14Request, saved13);
        } }, "saved13 replay rejects against explicit saved14 request before coordinator mutation");
        check(coordinator.getJob() == null,
            "saved14/saved13 override mismatch leaves coordinator idle");

        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(savedRequest, saved14);
        } }, "saved14 replay rejects against current15 request before coordinator mutation");
        check(coordinator.getJob() == null,
            "saved14/current15 override mismatch leaves coordinator idle");
        reject(new Attempt() { public void run() {
            coordinator.startSessionRestoreForQualification(saved14Request, saved);
        } }, "current15 replay rejects against explicit saved14 request before coordinator mutation");
        check(coordinator.getJob() == null,
            "current15/saved14 override mismatch leaves coordinator idle");

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
