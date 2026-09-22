package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.user.client.Timer;

/** Compiled evidence client of current production owners. Human/player-input trials are separate. */
final class AlphaDeveloperVerifier {
    interface Completion { void finished(String report, Throwable failure); }
    static void start(final CirSim sim, final boolean pilot, final boolean forced, final int caseOnly, final Completion completion) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning) throw new IllegalStateException("Alpha evidence is developer-only");
        new Timer() {
            final Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
            final GenerationCoordinator coordinator = sim.generationCoordinator;
            final Vector<String> families = PlayerFamilyCatalog.families();
            // Independent declared release corpus; every advertised random seed.
            final long[][] releaseSeeds = {{0,2,3,4}, {0,2,3}, {0,2,3}, {0,2,3},
                {0,1,2}, {0,1,2}, {0,1,2,3,4,5},
                {0,1,2},
                {0,1,2,3,17,42,101,-1,9007199254740993L,Long.MIN_VALUE,Long.MAX_VALUE}, {0,3}};
            final StringBuilder cases = new StringBuilder();
            final StringBuilder slowUnits = new StringBuilder();
            final long started = System.currentTimeMillis();
            Vector<PhysicalSlotMutationProvider> previousProviders = new Vector<PhysicalSlotMutationProvider>();
            Vector<String> previousCatalogIds = new Vector<String>();
            final Vector<String> mutationProviders = new Vector<String>();
            final StringBuilder mutationCases = new StringBuilder();
            final StringBuilder shopCases = new StringBuilder();
            final StringBuilder crossTargetCases = new StringBuilder();
            final StringBuilder serviceCases = new StringBuilder();
            int index = caseOnly < 0 ? 0 : caseOnly, phase, assertions, acquisitions, staleCallbacks, failuresChecked, mutationChecks;
            long cancellationMs;
            String operation = "start";
            PlayerLaunchRequest request;
            DifficultyAssessment assessment;
            GeneratedBoardInstance owner;
            int total() { if (pilot) return families.size(); int n=0; for(long[] seeds:releaseSeeds)n+=seeds.length; return n; }
            int familyIndex() { if(pilot)return index; int n=index; for(int f=0;f<releaseSeeds.length;f++) {if(n<releaseSeeds[f].length)return f; n-=releaseSeeds[f].length;} throw new IllegalStateException("Unknown release case"); }
            long seed() { if(pilot)return 0; int n=index; for(int f=0;f<familyIndex();f++)n-=releaseSeeds[f].length; return releaseSeeds[familyIndex()][n]; }
            String family() { return families.get(familyIndex()); }
            public void run() {
                try {
                    if (forced) throw new AssertionError("alpha-explicit-failure-canary");
                    if (caseOnly < -1 || caseOnly >= total()) throw new IllegalArgumentException("Unknown alpha case");
                    if (phase == 0) {
                        operation = "admission";
                        slowUnits.setLength(0);
                        request = new PlayerLaunchRequest(family(), Long.toString(seed()), PlayerFamilyCatalog.candidateProfile(family()).name());
                        GenerationRequest generation = pilot ? (ControlledIndicatorBlockContributions.FAMILY_ID.equals(family()) ?
                            GenerationRequest.controlled(seed()) : GenerationRequest.leaf(family(), seed(), true)) : request.generation();
                        coordinator.startForDeveloperVerification(generation);
                        phase = 1; progress(); schedule(0); return;
                    }
                    if (coordinator.isRunning()) {
                        GenerationJob job = coordinator.getJob();
                        GenerationJob.Stage stage = job.getStage();
                        int stageUnit = job.getStageWorkCount(stage) + 1;
                        long unitStarted = System.currentTimeMillis();
                        coordinator.advanceForDeveloperVerification();
                        long elapsed = System.currentTimeMillis() - unitStarted;
                        if (elapsed >= 250 || (!job.isRunning() && job.getOutcome() != GenerationJob.Outcome.PASS)) {
                            if (slowUnits.length() > 0) slowUnits.append(',');
                            slowUnits.append("{\"stage\":").append(quote(stage.name()))
                                .append(",\"unit\":").append(stageUnit)
                                .append(",\"elapsedMs\":").append(elapsed).append('}');
                        }
                        schedule(0); return;
                    }
                    if (phase == 1) {
                        GenerationJob job = coordinator.getJob();
                        require(job.getOutcome() == GenerationJob.Outcome.PASS, "admission " + family() + "/" + seed() + " " + job.getOutcome() + "/" + job.getStage() + "/" + job.getFailure());
                        require(job.getReceipt() != null && job.getReceipt().getStageCount() == 6, "all six admission stages");
                        owner = sim.getGeneratedBoardInstance();
                        require(owner.getSeed() == request.seed && owner.getCircuitFamilyId().equals(request.familyId), "exact requested owner");
                        assessment = DifficultyAssessment.assess(owner, sim.getGeneratedChallengeController().getDiagnosticProofReceipt());
                        if (!pilot) assessment.require(request.profile);
                        require(PlayerLaunchRequest.parse(request.replay()).seed == owner.getSeed(), "full seed replay");
                        if (!pilot) { operation = "catalog-and-repair"; catalogAndRepair(); }
                        appendCase(job);
                        phase = 2; progress(); schedule(0); return;
                    }
                    if (phase == 2) {
                        if (!pilot && index == 0) { operation = "rejection-and-cancellation"; rejectionAndCancellation(); }
                        previousProviders = new Vector<PhysicalSlotMutationProvider>();
                        previousCatalogIds = new Vector<String>();
                        for (WorkbenchPartsProvider p : owner.getPhysicalBoardRuntime().getWorkbenchPartsProviders()) {
                            previousProviders.add(owner.getPhysicalBoardRuntime().getMutationProvider(p.getComponentId()));
                            previousCatalogIds.add(p.getCatalogEntries().firstElement().getId());
                        }
                        index++; phase = 0;
                        if (caseOnly < 0 && index < total()) { schedule(0); return; }
                        long cleanup = System.currentTimeMillis(); snapshot.restore(sim); snapshot.assertRestored(sim);
                        completion.finished(report("PASS", cleanup-started, System.currentTimeMillis()-cleanup, true, null), null);
                    }
                } catch (Throwable failure) {
                    String state = " owner=" + (sim.getGeneratedBoardInstance()==owner) +
                        " power=" + sim.getBoardPowerController().getState() +
                        " isolated=" + sim.getBoardPowerController().isElectricallyUnpowered() +
                        " settled=" + sim.isGeneratedRuntimeSettled() +
                        " state=" + (sim.getGeneratedChallengeController()==null ? "none" : sim.getGeneratedChallengeController().getState());
                    failure = new IllegalStateException(operation + state + ": " + failure);
                    long cleanup = System.currentTimeMillis(); boolean restored = false;
                    try { coordinator.cancel(); snapshot.restore(sim); snapshot.assertRestored(sim); restored = true; }
                    catch (Throwable cleanupFailure) { failure = new IllegalStateException("Alpha cleanup failed after " + failure + ": " + cleanupFailure); }
                    completion.finished(report("FAIL", cleanup-started, System.currentTimeMillis()-cleanup, restored, failure), failure);
                }
            }
            void staleProviders() {
                int count = owner.getPhysicalBoardRuntime().getPhysicalParts().size();
                for (int i = 0; i < previousProviders.size(); i++) {
                    PhysicalSlotMutationProvider provider = previousProviders.get(i);
                    boolean rejected = false;
                    try { ((CatalogAcquisitionProvider)provider).acquireFromCatalog(previousCatalogIds.get(i)); }
                    catch (RuntimeException expected) { rejected = true; }
                    require(rejected, "retired provider cannot acquire into successor"); staleCallbacks++;
                }
                require(count == owner.getPhysicalBoardRuntime().getPhysicalParts().size(), "stale acquisition leaves successor inventory unchanged");
            }
            void settle() {
                GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "Alpha current owner");
                WireCurrentAdjacencyChecks.verify(sim);
            }
            void power(BoardPowerState state) {
                // Retests may leave ordinary analysis queued. Public power
                // controls intentionally reject a click until that work settles.
                settle();
                sim.setBoardPowerState(state);
                if (owner.getTemporalBehavior() != null) sim.advanceGeneratedTemporalProfile(.025);
                settle();
                require(sim.getBoardPowerController().getState() == state &&
                    (state != BoardPowerState.UNPOWERED || sim.getBoardPowerController().isElectricallyUnpowered()),
                    "public power action reached its requested isolation state");
            }
            void catalogAndRepair() {
                operation = "unrepaired-retest";
                GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
                challenge.beginDeveloperVerificationScope();
                require(!challenge.performCustomerRetest().isPassed(), "unrepaired customer behavior fails");
                power(BoardPowerState.UNPOWERED);
                // A valid catalog ID and actionable successor prevent unrelated
                // catalog/power guards from masking a stale-provider violation.
                staleProviders();
                PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
                PlayerSessionController product = new PlayerSessionController(sim);
                product.session.adopt(owner, request);
                int view = product.openView(); product.closeView(view);
                int count = runtime.getPhysicalParts().size();
                require(product.action(product.session.token(), view, "acquire", "R1", "INVALID", "").contains("closed"), "closed catalog lease rejects callback");
                require(runtime.getPhysicalParts().size() == count, "stale modal cannot create a fake or real part"); staleCallbacks++;
                if (("NPN_LOW_SIDE_SWITCH".equals(family()) && seed() == 0) ||
                        "COMPOSED_CONTROLLED_INDICATOR".equals(family())) verifyPublicShop(product);
                for (WorkbenchPartsProvider catalog : runtime.getWorkbenchPartsProviders()) {
                    operation = "catalog-unknown/" + catalog.getComponentId();
                    PhysicalSlotMutationProvider provider = runtime.getMutationProvider(catalog.getComponentId());
                    require(provider instanceof CatalogAcquisitionProvider, "every displayed current catalog has a real acquisition owner");
                    PhysicalPart<?> installed = runtime.getInstalledPart(catalog.getComponentId());
                    Vector<CircuitElm> bindings = owner.getComponentBindings().getElements(catalog.getComponentId());
                    count = runtime.getPhysicalParts().size();
                    boolean rejected = false;
                    try { ((CatalogAcquisitionProvider)provider).acquireFromCatalog("INVALID_CATALOG"); }
                    catch (IllegalArgumentException expected) { rejected = true; }
                    require(rejected && count == runtime.getPhysicalParts().size(), "unknown catalog entry has no inventory side effect"); failuresChecked++;
                    String id = catalog.getCatalogEntries().firstElement().getId();
                    operation = "catalog-acquire/" + catalog.getComponentId();
                    PhysicalPart<?> part = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(id); settle();
                    require(runtime.getPhysicalParts().size() == count + 1 && runtime.getPart(part.getId()) == part && catalog.ownsPart(part.getId()), "acquired identity is in authoritative inventory");
                    require(!part.isInstalled() && part.getBoardSlot() == null && runtime.getInstalledPart(catalog.getComponentId()) == installed,
                        "acquisition retains installed part and creates a loose part");
                    require(owner.getComponentBindings().getElements(catalog.getComponentId()).equals(bindings), "acquisition never retargets installed electrical bindings");
                    for (CircuitElm e : part.getElectricalBacking().getCircuitElements()) require(sim.elmList.contains(e), "loose part is backed by the active CircuitJS graph");
                    require(part.getGeometryRealization() != null, "loose physical package is inspectable"); acquisitions++;
                    operation = "physical-service/" + catalog.getComponentId();
                    verifyServicePosition(provider, installed, part);
                    String mutationType = U04CatalogMutationVerifier.type(part);
                    if (!mutationProviders.contains(mutationType)) {
                        operation = "catalog-compensation/" + mutationType;
                        U04CatalogMutationVerifier mutations = new U04CatalogMutationVerifier(sim);
                        mutationChecks += mutations.verify(provider, id, part);
                        if (mutationCases.length() > 0) mutationCases.append(',');
                        mutationCases.append(mutations.rows());
                        mutationProviders.add(mutationType);
                    }
                    if (index == 0 && part instanceof PhysicalResistorPart) {
                        PhysicalResistorPart resistor = (PhysicalResistorPart)part;
                        double reading = sim.measureResistance((CircuitPostMeasurementEndpoint)part.getTerminal(0).getEndpoint(),
                            (CircuitPostMeasurementEndpoint)part.getTerminal(1).getEndpoint());
                        require(Math.abs(reading-resistor.getSpecification().getNominalResistanceOhms()) < .02,
                            "new tray resistor measures its real nominal resistance"); settle();
                        PhysicalPart<?> second = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(id); settle();
                        require(second != part && !second.getId().equals(part.getId()), "repeated acquisition gives distinct part identities"); acquisitions++;
                    }
                }
                if ("COMPOSED_CONTROLLED_INDICATOR".equals(family())) {
                    operation = "cross-target-catalog-mutations";
                    U04CatalogMutationVerifier cross = new U04CatalogMutationVerifier(sim);
                    try {
                        cross.verifyCrossTarget();
                    } finally {
                        // Preserve completed checkpoints even if a later move
                        // fails; the outer report remains FAIL in that case.
                        if (crossTargetCases.length() > 0) crossTargetCases.append(',');
                        crossTargetCases.append("{\"family\":").append(quote(family()))
                            .append(",\"seed\":").append(quote(Long.toString(seed())))
                            .append(",\"checks\":").append(cross.checkCount())
                            .append(",\"cases\":[").append(cross.rows()).append("]}");
                    }
                }
                String target = owner.getFaultBinding().getFault().getTargetComponentId();
                PhysicalSlotMutationProvider provider = runtime.getMutationProvider(target);
                String correct = owner.getDiagnosticProvider().getCorrectCatalogId(owner, target);
                operation = "repair-acquire/" + target;
                PhysicalPart<?> replacement = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(correct); settle(); acquisitions++;
                operation = "repair-remove/" + target;
                require(provider.removeInstalledPart(), "separate removal of failed part"); settle();
                operation = "repair-install/" + target;
                require(provider.install(replacement.getId()), "separate installation from actual tray identity"); settle();
                operation = "repaired-retest";
                power(BoardPowerState.POWERED);
                require("".equals(product.action(product.session.token(), view, "retest", "", "", "")),
                    "public session accepts repaired retest");
                settle(); product.refresh();
                require(challenge.getCustomerRetestResult() != null,
                    "public retest produced a result: " + product.session.message());
                require(challenge.getCustomerRetestResult().isPassed(), "catalog repair restores actual customer behavior");
                require(challenge.isCompleted() && product.session.screen() == PlayerSession.Screen.RESULTS,
                    "settled public retest completes its exact session owner");
                require(!sim.activeMeasurementOverlay, "repair and retest leave no temporary meter graph");
                challenge.endDeveloperVerificationScope();
                operation = "catalog-and-repair";
            }
            void verifyServicePosition(PhysicalSlotMutationProvider provider, PhysicalPart<?> original, PhysicalPart<?> spare) {
                String id = provider.getComponentId();
                PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
                PhysicalPartProvenance provenance = original.getProvenance();
                PhysicalPartElectricalBacking backing = original.getElectricalBacking();
                WorkbenchOperation remove = WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original);
                require(provider.supports(remove) && provider.isAvailable(remove, sim.pcbWorkbenchController),
                    "every occupied position exposes physical removal, regardless of fault");
                for (String pad : owner.getBoard().getComponent(id).getPadIds()) {
                    WorkbenchOperation lift = WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, original, id, pad);
                    if (!provider.supports(lift)) continue;
                    operation = "physical-service/" + id + "/lift/" + pad;
                    require(provider.isAvailable(lift, sim.pcbWorkbenchController) && provider.invoke(lift, sim.pcbWorkbenchController), "supported lead lifts"); settle();
                    require(!sim.elmList.contains(owner.getConnectionBindings().get(id, pad).getConnectionElement()), "lift removes actual graph attachment");
                    WorkbenchOperation reconnect = WorkbenchOperation.forPartLead(WorkbenchOperation.RECONNECT_LEAD, original, id, pad);
                    operation = "physical-service/" + id + "/reconnect/" + pad;
                    require(provider.isAvailable(reconnect, sim.pcbWorkbenchController) && provider.invoke(reconnect, sim.pcbWorkbenchController), "same lead reconnects"); settle();
                }
                operation = "physical-service/" + id + "/remove-original";
                require(provider.removeInstalledPart(), "healthy and faulty originals can enter tray"); settle();
                require(runtime.getPart(original.getId()) == original && original.getElectricalBacking() == backing &&
                    original.getProvenance() == provenance && !original.isInstalled() &&
                    runtime.getWorkbenchPartsProviderForPart(original.getId()).getLooseParts().contains(original), "removed original retains real tray identity and provenance");
                for (GeneratedComponentConnectionBinding link : owner.getConnectionBindings().getForComponent(id))
                    require(!sim.elmList.contains(link.getConnectionElement()), "removal disconnects every actual lead");
                operation = "physical-service/" + id + "/install-replacement";
                require(provider.install(spare.getId()), "compatible replacement remains legal at every position"); settle();
                require(runtime.getInstalledPart(id) == spare, "chosen replacement stays installed");
                for (CircuitElm element : owner.getComponentBindings().getElements(id))
                    require(spare.getElectricalBacking().getCircuitElements().contains(element), "replacement owns actual electrical binding");
                operation = "physical-service/" + id + "/remove-replacement";
                require(provider.removeInstalledPart(), "replacement can return to its inventory"); settle();
                operation = "physical-service/" + id + "/reinstall-original";
                require(provider.install(original.getId()), "exact removed original is reinstallable"); settle();
                require(runtime.getInstalledPart(id) == original && original.getElectricalBacking() == backing, "restoration retains original contribution");
                GeneratedRuntimeInvariant.verify(sim, owner, sim.getBoardModificationController(), sim.elmList);
                if (serviceCases.length() > 0) serviceCases.append(',');
                serviceCases.append("{\"family\":").append(quote(family())).append(",\"seed\":").append(quote(Long.toString(seed())))
                    .append(",\"component\":").append(quote(id)).append(",\"type\":").append(quote(U04CatalogMutationVerifier.type(original)))
                    .append(",\"removeReplaceReinstall\":true}");
            }
            void verifyPublicShop(PlayerSessionController product) {
                PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
                PlayerShopCatalog.Category category = new PlayerShopCatalog(owner).category("RESISTOR");
                int lease = product.openView();
                try {
                    for (PlayerShopCatalog.Entry entry : category.entries()) {
                        if (!"R_CATALOG_330".equals(entry.catalogId)) continue;
                        operation = "public-shop-acquire/" + family() + "/" + entry.geometry.getVariantKey();
                        int before = runtime.getPhysicalParts().size();
                        PhysicalPart<?> original = runtime.getInstalledPart(entry.acquisitionComponent);
                        Vector<CircuitElm> bindings = owner.getComponentBindings().getElements(entry.acquisitionComponent);
                        String response = product.action(product.session.token(), lease, "acquire", category.id, entry.id, "");
                        settle();
                        require(response.startsWith("Added to the real Parts Tray."), "public type/specification request acquires a real part");
                        require(runtime.getPhysicalParts().size() == before + 1, "public acquisition adds exactly one physical identity");
                        PhysicalPart<?> added = runtime.getPart(runtime.getPartOrder().lastElement());
                        require(added instanceof PhysicalResistorPart && !added.isInstalled() &&
                            ((PhysicalResistorPart)added).getSpecification().getNominalResistanceOhms() == 330 &&
                            added.getGeometryRealization().isEquivalentTo(entry.geometry), "public choice retains electrical specification and physical fit");
                        require(runtime.getWorkbenchPartsProvider(entry.acquisitionComponent).getPart(added.getId()) == added &&
                            runtime.getInstalledPart(entry.acquisitionComponent) == original &&
                            owner.getComponentBindings().getElements(entry.acquisitionComponent).equals(bindings),
                            "public acquisition uses the declared inventory without replacing the source slot");
                        double measured = sim.measureResistance((CircuitPostMeasurementEndpoint)added.getTerminal(0).getEndpoint(),
                            (CircuitPostMeasurementEndpoint)added.getTerminal(1).getEndpoint());
                        settle();
                        require(Math.abs(measured - 330) < .02, "publicly acquired resistor has its real solver-backed value");
                        acquisitions++;
                        boolean fitNegatives = "NPN_LOW_SIDE_SWITCH".equals(family()) &&
                            "SPAN_260".equals(entry.geometry.getVariantKey());
                        if (fitNegatives) verifyShopFitRejections(product, lease, added);
                        if (shopCases.length() > 0) shopCases.append(',');
                        shopCases.append("{\"family\":").append(quote(family())).append(",\"seed\":").append(quote(Long.toString(seed())))
                            .append(",\"variant\":").append(quote(entry.geometry.getVariantKey()))
                            .append(",\"measuredOhms\":").append(measured).append(",\"sourcePreserved\":true")
                            .append(",\"fitAndTypeRejections\":").append(fitNegatives).append('}');
                    }
                } finally { product.closeView(lease); }
            }
            void verifyShopFitRejections(PlayerSessionController product, int lease, PhysicalPart<?> wideResistor) {
                PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
                PlayerShopCatalog.Category npns = new PlayerShopCatalog(owner).category("NPN_TRANSISTOR");
                PlayerShopCatalog.Entry entry = npns.entries().firstElement();
                PhysicalPart<?> originalTransistor = runtime.getInstalledPart(entry.acquisitionComponent);
                int count = runtime.getPhysicalParts().size();
                require(product.action(product.session.token(), lease, "acquire", npns.id, entry.id, "")
                    .startsWith("Added to the real Parts Tray."), "public Shop provides a real loose wrong-type candidate");
                settle();
                PhysicalPart<?> transistor = runtime.getPart(runtime.getPartOrder().lastElement());
                require(transistor instanceof PhysicalNpnPart && !transistor.isInstalled() &&
                    runtime.getPhysicalParts().size() == count + 1 &&
                    runtime.getWorkbenchPartsProvider(entry.acquisitionComponent).getPart(transistor.getId()) == transistor &&
                    runtime.getInstalledPart(entry.acquisitionComponent) == originalTransistor,
                    "wrong-type negative uses current canonical loose inventory and preserves installed transistor");
                acquisitions++;
                PhysicalSlotMutationProvider target = runtime.getMutationProvider("RLOAD");
                PhysicalPart<?> original = runtime.getInstalledPart("RLOAD");
                require("SPAN_240".equals(original.getGeometryRealization().getVariantKey()) &&
                    "SPAN_260".equals(wideResistor.getGeometryRealization().getVariantKey()),
                    "independent fixture has two different real resistor fits");
                require(target.removeInstalledPart(), "prepare empty target for fit/type negatives"); settle();
                Vector<CircuitElm> active = new Vector<CircuitElm>(sim.elmList);
                Vector<CircuitElm> bindings = owner.getComponentBindings().getElements("RLOAD");
                Vector<String> parts = runtime.getPartOrder();
                require(!runtime.isPartInstallableAt(wideResistor, "RLOAD") && !target.install(wideResistor.getId()),
                    "current loose resistor rejects mismatched target geometry");
                require(!runtime.isPartInstallableAt(transistor, "RLOAD") && !target.install(transistor.getId()),
                    "current loose transistor rejects resistor target type");
                require(runtime.getInstalledPart("RLOAD") == null && !wideResistor.isInstalled() &&
                    !transistor.isInstalled() && parts.equals(runtime.getPartOrder()) && active.equals(sim.elmList) &&
                    bindings.equals(owner.getComponentBindings().getElements("RLOAD")),
                    "rejected physical fits preserve inventory, graph, bindings and empty target");
                require(target.install(original.getId()), "restore exact original after fit/type negatives"); settle();
            }
            void rejectionAndCancellation() {
                Object graph = sim.elmList; GeneratedBoardInstance protectedOwner = sim.getGeneratedBoardInstance();
                // Rejection is AFTER real proof, BEFORE publication. No fallback seed/profile is selected.
                DifficultyProfile opposite = assessment.profile == DifficultyProfile.EASY ? DifficultyProfile.MEDIUM : DifficultyProfile.EASY;
                coordinator.startForDeveloperVerification(new PlayerLaunchRequest(request.familyId, Long.toString(request.seed), opposite.name()).generation());
                while (coordinator.isRunning()) coordinator.advanceForDeveloperVerification();
                require(coordinator.getJob().getOutcome() != GenerationJob.Outcome.PASS && coordinator.getJob().getReceipt() == null,
                    "mismatched profile has no publication receipt"); failuresChecked++;
                require(sim.getGeneratedBoardInstance() == protectedOwner && sim.elmList == graph, "profile rejection restores exact protected graph");
                coordinator.startForDeveloperVerification(new PlayerLaunchRequest(Rb15Plan.FAMILY_ID, "3", PlayerFamilyCatalog.candidateProfile(Rb15Plan.FAMILY_ID).name()).generation());
                coordinator.advanceForDeveloperVerification(); coordinator.advanceForDeveloperVerification();
                require(coordinator.isRunning(), "routing cancellation reaches a resumable unit");
                coordinator.cancel(); cancellationMs = coordinator.getCancellationLatencyMillis();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.CANCELLED && coordinator.getJob().getReceipt() == null,
                    "cancelled launch cannot publish");
                require(sim.getGeneratedBoardInstance() == protectedOwner && sim.elmList == graph && !coordinator.retainsSavedOwnersForDeveloperVerification(),
                    "cancel releases work and restores exact prior owner");
            }
            void appendCase(GenerationJob job) {
                if (cases.length() > 0) cases.append(',');
                cases.append("{\"family\":").append(quote(request.familyId)).append(",\"seed\":").append(quote(Long.toString(request.seed)))
                    .append(",\"requested\":").append(quote(request.profile.name())).append(",\"computed\":").append(quote(assessment.profile.name()))
                    .append(",\"features\":").append(quote(assessment.canonical())).append(",\"admissionMs\":").append(job.getElapsedMillis())
                    .append(",\"maxUnitMs\":").append(coordinator.getMaxAdvanceMillis()).append(",\"workUnits\":").append(job.getStepCount())
                    .append(",\"slowUnits\":[").append(slowUnits).append(']')
                    .append(",\"repairPassed\":").append(!pilot).append('}');
            }
            String report(String status, long elapsed, long cleanup, boolean restored, Throwable failure) {
                return "{\"protocol\":\"TSJ-ALPHA-1\",\"status\":" + quote(status) + ",\"pilot\":" + pilot + ",\"cases\":[" + cases +
                    "],\"assertions\":" + assertions + ",\"acquisitions\":" + acquisitions + ",\"staleCallbacks\":" + staleCallbacks +
                    ",\"mutationChecks\":" + mutationChecks + ",\"mutationProviders\":" + providerJson() +
                    ",\"mutationCases\":[" + mutationCases + "]" +
                    ",\"shopCases\":[" + shopCases + "]" +
                    ",\"crossTargetCases\":[" + crossTargetCases + "]" +
                    ",\"serviceCases\":[" + serviceCases + "]" +
                    ",\"negativeChecks\":" + failuresChecked + ",\"cancellationMs\":" + cancellationMs + ",\"elapsedMs\":" + elapsed +
                    ",\"cleanupMs\":" + cleanup + ",\"ownerRestored\":" + restored + ",\"operation\":" + quote(operation) +
                    ",\"activeSlowUnits\":[" + slowUnits + "]" +
                    ",\"activeCase\":" + index + ",\"failure\":" + (failure == null ? "null" : quote(failure.toString())) + "}";
            }
            String providerJson() {
                StringBuilder result = new StringBuilder("[");
                for (String provider : mutationProviders) {
                    if (result.length() > 1) result.append(',');
                    result.append(quote(provider));
                }
                return result.append(']').toString();
            }
            void progress() { publish(report("RUNNING", System.currentTimeMillis()-started, 0, false, null)); }
            void require(boolean value, String message) { assertions++; if (!value) throw new AssertionError("Alpha: " + message); }
        }.schedule(0);
    }
    private static native String quote(String value) /*-{ return JSON.stringify(value); }-*/;
    static native void publish(String value) /*-{
        $doc.documentElement.setAttribute('data-tsj-alpha-report', value);
    }-*/;
}
