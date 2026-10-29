package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;

/** Product orchestration only. Public JSON contains no graph, proof, fault or original-part metadata. */
final class PlayerSessionController {
    private final CirSim sim;
    final PlayerSession session = new PlayerSession();
    private final QuickPlayIdentityAllocator newBoardIdentities = new QuickPlayIdentityAllocator();
    private String notice = "";
    private boolean bridgeReady, released, qualificationPersistence;
    private JavaScriptObject bridge;
    private int viewToken;
    private PendingRetest pendingRetest;

    private final class PendingRetest {
        final int token;
        final Object owner;
        final GeneratedChallengeController challenge;
        final GeneratedCustomerRetestResult result;
        final Timer watchdog = new Timer() {
            public void run() {
                if (pendingRetest != PendingRetest.this) return;
                finishRetest(true); refresh();
            }
        };
        PendingRetest(int token, Object owner, GeneratedChallengeController challenge,
                GeneratedCustomerRetestResult result) {
            this.token = token; this.owner = owner; this.challenge = challenge; this.result = result;
        }
    }

    PlayerSessionController(CirSim sim) { this.sim = sim; }

    void openInitial(QueryParameters query) {
        bridge = installBridge(); bridgeReady = true;
        try {
            String replay = query.getValue("replay");
            String route = query.getValue("tsjChallenge");
            if (replay != null) launch(PlayerLaunchRequest.parse(replay));
            else if (route != null) {
                String family = PlayerFamilyCatalog.fromRoute(route);
                String seed = query.getValue("seed");
                String profile = query.getValue("difficulty");
                launch(new PlayerLaunchRequest(family, seed == null ? "0" : seed,
                    profile == null ? PlayerFamilyCatalog.candidateProfile(family).name() : profile));
            } else if (query.getBooleanValue("tsjQuickPlay", false)) {
                QuickPlaySelection selected = new QuickPlaySelector().select();
                launch(PlayerLaunchRequest.random(selected.getFamilyId(), Long.toString(selected.getSeed()),
                    PlayerFamilyCatalog.candidateProfile(selected.getFamilyId()).name()));
            } else if (query.getValue("tsjFixture") != null) notice = "Developer fixtures require the separate developer mode.";
        } catch (RuntimeException failure) { notice = "This replay is unsupported. Check its epoch, family, profile and exact seed."; }
        refresh();
    }

    /** A private request keeps its own authority when its family also becomes public. */
    void openQualifiedBoard(GenerationRequest qualificationRequest,
            GeneratedBoardInstance owner, boolean persistence) {
        GenerationJob job = sim.generationCoordinator == null ? null : sim.generationCoordinator.getJob();
        requireQualificationPublication(qualificationRequest, owner, job);
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        if (released || sim.playerSessionController != this || owner == null ||
                sim.getGeneratedBoardInstance() != owner || challenge == null || !challenge.isReady() ||
                owner.isDeveloperOnlyFaultRoute() || sim.generationCoordinator.isRunning() || !bridgeIsEmpty())
            throw new IllegalStateException("Qualified session requires its admitted private owner");
        GeneratedDiagnosticProofReceipt proof = challenge.getDiagnosticProofReceipt();
        if (proof == null || proof.isWarmReuseForDeveloperVerification() || proof.getContextKey() == null ||
                !proof.getContextKey().isTrustedCapture())
            throw new IllegalStateException("Qualified session requires its cold production proof");
        proof.requireAssessmentOwner(owner);
        session.adoptQualification(owner);
        owner.getPhysicalBoardRuntime().getSessionHistory().start();
        qualificationPersistence = persistence;
        notice = persistence ? "Private session files can be restored only in this qualification view." :
            "This qualification board cannot be saved, shared or replaced.";
        bridge = installBridge(); bridgeReady = true;
        refresh();
    }

    static void requireQualificationPublication(GenerationRequest qualificationRequest,
            GeneratedBoardInstance owner, GenerationJob job) {
        if (qualificationRequest == null || !qualificationRequest.isPrivateDiagnosticQualification() ||
                qualificationRequest.isCandidateSearch() ||
                !PlayerSessionReplay.forQualification(qualificationRequest).matches(owner))
            throw new IllegalStateException("Qualified session requires its exact private request");
        GenerationReceipt receipt = job == null ? null : job.getReceipt();
        if (receipt == null || !qualificationRequest.canonical().equals(receipt.getManifest()))
            throw new IllegalStateException("Qualified session requires its current private publication receipt");
        job.validateReceipt(receipt);
    }

    boolean isBridgeAvailable() {
        return !released && sim.playerSessionController == this &&
            (!session.isQualification() || sim.getGeneratedBoardInstance() == session.owner() ||
                (qualificationPersistence && Rb56QualificationVerifier.ownsPersistenceLease(sim, this)));
    }

    /** Keep the lease reachable for retry if exact UI/bridge teardown fails. */
    void releaseQualification(Object expectedOwner) {
        if (expectedOwner == null || sim.playerSessionController != this ||
                sim.getGeneratedBoardInstance() != expectedOwner)
            throw new IllegalStateException("Refusing session release across a successor owner");
        // A failed precheck has installed no session or bridge. Do not touch an existing bridge.
        if (session.owner() == null && !session.isQualification() && bridge == null) {
            released = true; bridgeReady = false; viewToken++; clearRetest();
            sim.playerSessionController = null;
            return;
        }
        if (!session.isQualification() || session.owner() != expectedOwner)
            throw new IllegalStateException("Qualification lease belongs to another session");
        released = true; bridgeReady = false; viewToken++; clearRetest();
        if (bridge != null && !removeBridge(bridge))
            throw new IllegalStateException("Qualified session UI or bridge is still retained");
        bridge = null;
        session.revoke(expectedOwner);
        sim.playerSessionController = null;
    }

    boolean isQualificationReleased() {
        return released && bridge == null && session.owner() == null && sim.playerSessionController != this;
    }

    private void launch(PlayerLaunchRequest selected) { launch(selected, null); }

    private void launch(PlayerLaunchRequest selected, final PlayerSessionSave saved) {
        if (released || session.isQualification() || !sim.isGeneratedRuntimeSettled() || sim.generationCoordinator.isRunning())
            throw new IllegalStateException("The current board is busy");
        final PlayerLaunchRequest request = newBoardIdentities.allocate(selected);
        final int token = session.begin(request);
        notice = ""; refresh();
        try {
            GenerationCoordinator.Completion completion = new GenerationCoordinator.Completion() {
                public void complete(GenerationJob job, GeneratedBoardInstance published) {
                    if (job.getOutcome() == GenerationJob.Outcome.PASS) {
                        if (!request.familyId.equals(published.getCircuitFamilyId()))
                            throw new IllegalStateException("Published family differs from the launch");
                        published.getPhysicalBoardRuntime().getSessionHistory().start();
                        session.prepared(token, request, request.accepted(published.getSeed()), published);
                        if (saved != null) {
                            session.enter(session.token(), PlayerSession.Screen.WORKBENCH);
                            notice = "Session restored. Transient behavior restarted; retest the board.";
                        }
                    } else session.failed(token, job.getOutcome() == GenerationJob.Outcome.CANCELLED,
                        job.getOutcome() == GenerationJob.Outcome.CANCELLED ? "Board preparation cancelled." :
                        request.candidateSearch ?
                        "No unique valid board passed within this launch's limits. The previous board is retained. " +
                        "Family: " + request.familyId + "; difficulty: " + request.profile + "; launch seed: " + request.seed :
                        "This exact board could not be prepared for this difficulty. Try another seed or board. Replay: " + request.replay());
                    sim.refreshChallengeInteractionState(); refresh(); sim.repaint();
                }
            };
            if (saved == null) sim.generationCoordinator.start(request.generation(), completion, true);
            else sim.generationCoordinator.startSessionRestore(request.generation(), saved, completion);
        } catch (RuntimeException failure) {
            session.failed(token, false, "Board preparation could not start. The previous board is retained.");
            refresh();
        }
    }

    int beginQualificationRestore(Object expectedOwner) {
        if (!qualificationPersistence || !isBridgeAvailable())
            throw new IllegalStateException("Private persistence is unavailable");
        clearRetest();
        int token = session.beginQualificationRestore(expectedOwner);
        notice = ""; refresh(); return token;
    }

    void finishQualificationRestore(int token, Object previousOwner, GeneratedBoardInstance nextOwner,
            GenerationJob.Outcome outcome) {
        if (nextOwner != null) {
            if (!session.restoredQualification(token, previousOwner, nextOwner))
                throw new IllegalStateException("Private restore belongs to a closed session");
            notice = "Session restored. Transient behavior restarted; retest the board.";
        } else if (!session.failed(token, outcome == GenerationJob.Outcome.CANCELLED,
                "The session could not be restored. The previous board is retained. Resume it or choose another file."))
            throw new IllegalStateException("Private restore failure belongs to a closed session");
        if (nextOwner == null && outcome == GenerationJob.Outcome.CANCELLED)
            resumeQualification(session.token());
        sim.refreshChallengeInteractionState(); refresh(); sim.repaint();
    }

    /** Resume preserved results only from the exact retained controller's passed job. */
    private void resumeQualification(int expected) {
        if (!ownsCurrentBoard()) return;
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        GeneratedCustomerRetestResult result = challenge.getCustomerRetestResult();
        // Return to board from Results is navigation, rather than failed-load recovery.
        boolean completed = session.screen() != PlayerSession.Screen.RESULTS &&
            challenge.isCompleted() && result != null && result.isPassed();
        session.resumeQualification(expected, sim.getGeneratedBoardInstance(), completed);
    }

    /** Captured modal leases invalidate a retained Shop callback on close/reopen. */
    int openView() {
        if (!isBridgeAvailable()) throw new IllegalStateException("This session has closed");
        return ++viewToken;
    }
    void closeView(int token) { if (!released && token == viewToken) viewToken++; }

    String action(int expected, int view, String action, String first, String second, String third) {
        if (!isBridgeAvailable() || !session.accepts(expected)) return "This control belongs to an earlier session.";
        if (session.isQualification() && ("launch".equals(action) || "random".equals(action) ||
                "replay".equals(action) || ("load-session".equals(action) && !qualificationPersistence)))
            return "This qualification board cannot be saved, shared or replaced.";
        try {
            if ("launch".equals(action)) launch(new PlayerLaunchRequest(first, second, third));
            else if ("random".equals(action)) launch(PlayerLaunchRequest.random(first, second, third));
            else if ("replay".equals(action)) launch(PlayerLaunchRequest.parse(first));
            else if ("load-session".equals(action)) {
                if (view != viewToken) return "This file selection belongs to a closed view.";
                PlayerSessionSave saved;
                try { saved = PlayerSessionSave.parse(first); }
                catch (RuntimeException invalid) { return "This session file is incomplete, incompatible or unsupported. The current board is unchanged."; }
                if (session.isQualification()) Rb56QualificationVerifier.restoreSession(sim, this, saved);
                else launch(PlayerLaunchRequest.parse(saved.replay), saved);
            }
            else if ("cancel".equals(action) && session.screen() == PlayerSession.Screen.PREPARING) sim.generationCoordinator.cancel();
            else if ("menu".equals(action)) session.enter(expected, PlayerSession.Screen.MENU);
            else if ("resume".equals(action)) {
                if (session.isQualification()) resumeQualification(expected);
                else session.enter(expected, PlayerSession.Screen.WORKBENCH);
            }
            else if ("retest".equals(action)) retest(expected);
            else if ("end-qualification".equals(action) && session.isQualification()) {
                if (session.screen() != PlayerSession.Screen.RESULTS || !ownsCurrentBoard() ||
                        !sim.getGeneratedChallengeController().isCompleted())
                    return "Finish the customer retest before closing this session.";
                if (!Rb56QualificationVerifier.finishRetainedBench(sim)) return "This qualification view has closed.";
            }
            else if ("meter".equals(action)) {
                if (!ownsCurrentBoard() || session.screen() != PlayerSession.Screen.WORKBENCH)
                    return "The meter belongs to the current workbench.";
                if (!sim.instrumentController.selectPlayerMode(first))
                    return "This meter function is unavailable while the board is busy.";
            }
            else if ("acquire".equals(action)) {
                if (view != viewToken) return "This catalog view has closed.";
                return acquire(first, second);
            } else return "This action is unavailable.";
        } catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
        catch (RuntimeException failure) { return "The action is unavailable while the board is busy or requires isolation."; }
        refresh(); return "";
    }

    private boolean canSaveSession() {
        return (!session.isQualification() || qualificationPersistence) && ownsCurrentBoard() && sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
            session.screen() != PlayerSession.Screen.PREPARING && session.screen() != PlayerSession.Screen.RETEST;
    }

    String saveSession(int expected, int view) {
        JSONObject result = new JSONObject();
        try {
            if (!session.accepts(expected) || view != viewToken || !canSaveSession())
                throw new IllegalStateException("The current session is busy or this view has closed.");
            PlayerSessionSave saved = session.isQualification() ?
                Rb56QualificationVerifier.captureSession(sim, this, GWT.getPermutationStrongName()) :
                PlayerSessionState.capture(sim, session.request(),
                    sim.getGeneratedBoardInstance().getPhysicalBoardRuntime().getSessionHistory().operations(),
                    GWT.getPermutationStrongName());
            put(result, "contents", saved.encode());
            result.put("ok", JSONBoolean.getInstance(true));
        } catch (RuntimeException failure) {
            result.put("ok", JSONBoolean.getInstance(false));
            put(result, "error", "The session could not be saved. Wait for the board to settle and try again; this format supports up to 8192 operations.");
        }
        return result.toString();
    }

    PlayerSessionHistory.Page historyPage(int expected, int view, int offset) {
        if (!isBridgeAvailable() || !session.accepts(expected) || view <= 0 || view != viewToken ||
                session.screen() != PlayerSession.Screen.MENU || !ownsCurrentBoard())
            throw new IllegalStateException("This history view has closed.");
        return sim.getGeneratedBoardInstance().getPhysicalBoardRuntime().getSessionHistory().page(offset);
    }

    private String historyPageJson(int expected, int view, int offset) {
        JSONObject result = new JSONObject();
        try {
            PlayerSessionHistory.Page page = historyPage(expected, view, offset);
            JSONArray entries = new JSONArray();
            for (PlayerSessionSave.Operation operation : page.operations)
                entries.set(entries.size(), new JSONString(historyLabel(operation)));
            result.put("entries", entries);
            result.put("offset", new JSONNumber(page.offset));
            result.put("total", new JSONNumber(page.total));
            result.put("previousOffset", new JSONNumber(page.previousOffset()));
            result.put("nextOffset", new JSONNumber(page.nextOffset()));
            result.put("ok", JSONBoolean.getInstance(true));
        } catch (RuntimeException failure) {
            result.put("ok", JSONBoolean.getInstance(false));
            put(result, "error", "This history page is unavailable. Reopen Session history and try again.");
        }
        return result.toString();
    }

    private static String historyLabel(PlayerSessionSave.Operation operation) {
        String kind = operation.kind;
        if ("REMOVE".equals(kind)) return "Removed a part.";
        if ("INSTALL".equals(kind)) return "Installed a part from the tray.";
        if ("ACQUIRE".equals(kind)) return "Added a part to the tray.";
        if ("CATALOG".equals(kind)) return "Installed a catalog part.";
        if ("LEAD".equals(kind)) return "CONNECTED".equals(operation.result) ? "Reconnected a lead." : "Lifted a lead.";
        if ("GRAPH_REMOVE".equals(kind)) return "Disconnected a component.";
        if ("GRAPH_RESTORE".equals(kind)) return "Reconnected a component.";
        if ("INPUT".equals(kind)) return "Changed a board input.";
        if ("SOURCE".equals(kind)) return "Changed a supply setting.";
        if ("RESET".equals(kind)) return "Reset simulation and service wear.";
        throw new IllegalArgumentException("Unsupported history operation");
    }

    boolean isWorkbenchScreen() { return session.screen() == PlayerSession.Screen.WORKBENCH && ownsCurrentBoard(); }

    private boolean ownsCurrentBoard() {
        return isBridgeAvailable() && session.owner() != null && sim.getGeneratedBoardInstance() == session.owner() &&
            sim.getGeneratedChallengeController() != null && !sim.generationCoordinator.isRunning();
    }
    private String acquire(String categoryId, String catalogId) {
        if (!ownsCurrentBoard() || session.screen() != PlayerSession.Screen.WORKBENCH || !sim.isChallengeInteractionEnabled() ||
                sim.activeMeasurementOverlay || !sim.getBoardPowerController().isElectricallyUnpowered())
            return "Disconnect all board supplies and wait for settling before acquiring parts.";
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        if (owner.getPhysicalBoardRuntime().getPhysicalParts().size() >= PlayerSessionSave.MAX_PARTS)
            return "This session's parts inventory is full. Start a new board to clear it.";
        PlayerShopCatalog.Entry selection = new PlayerShopCatalog(owner).category(categoryId).entry(catalogId);
        String component = selection.acquisitionComponent;
        catalogId = selection.catalogId;
        WorkbenchPartsProvider catalog = owner.getPhysicalBoardRuntime().getWorkbenchPartsProvider(component);
        PhysicalSlotMutationProvider provider = owner.getPhysicalBoardRuntime().getMutationProvider(component);
        if (catalog == null || !(provider instanceof CatalogAcquisitionProvider)) return "Acquisition is unavailable for this catalog.";
        boolean found = false;
        for (WorkbenchCatalogEntry entry : catalog.getCatalogEntries()) if (entry.getId().equals(catalogId)) found = true;
        if (!found) return "Unknown catalog entry.";
        PhysicalPart<?> original = owner.getPhysicalBoardRuntime().getInstalledPart(component);
        PhysicalPart<?> acquired;
        try { acquired = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(catalogId); }
        catch (BoardModificationRejectedException refusal) {
            if ("Disconnect supplies and wait for discharge before acquiring a relay".equals(refusal.getMessage()))
                return "This part cannot be added yet. Disconnect every supply and wait for stored energy to discharge, then try again.";
            throw refusal;
        }
        if (acquired == null || acquired.isInstalled() || !catalog.ownsPart(acquired.getId()) ||
                original != owner.getPhysicalBoardRuntime().getInstalledPart(component))
            throw new IllegalStateException("Catalog acquisition changed its physical slot");
        refresh(); return "Added to the real Parts Tray. Select the loose part on the workbench to install it.";
    }

    private void retest(int expected) {
        if (!ownsCurrentBoard() || !sim.isGeneratedSemanticInteractionEnabled() ||
                sim.getGeneratedChallengeController().isCompleted()) return;
        final Object owner = session.owner();
        int token = session.retest(expected, owner);
        notice = ""; refresh();
        try {
            GeneratedCustomerRetestResult result = sim.performCustomerRetest();
            pendingRetest = new PendingRetest(token, owner, sim.getGeneratedChallengeController(), result);
            // Functional profiles can leave ordinary analysis queued while
            // restoring the player's input. Completion belongs to that settled
            // owner, never to the synchronous return of the profile alone.
            pendingRetest.watchdog.schedule(5000);
            finishRetest(false);
        } catch (RuntimeException failure) {
            if (sim.troubleshootDebug) CirSim.console("Customer retest failed: " + failure);
            clearRetest();
            session.retested(token, owner, false, "The retest could not finish. Check board power and try again.");
        }
    }

    private void clearRetest() {
        if (pendingRetest != null) pendingRetest.watchdog.cancel();
        pendingRetest = null;
    }

    private void finishRetest(boolean expired) {
        PendingRetest pending = pendingRetest;
        if (pending == null) return;
        if (!session.accepts(pending.token) || session.owner() != pending.owner) {
            clearRetest(); return;
        }
        boolean sameResult = sim.getGeneratedBoardInstance() == pending.owner &&
            sim.getGeneratedChallengeController() == pending.challenge &&
            pending.challenge.getCustomerRetestResult() == pending.result;
        if (!expired && sameResult && pending.result.isPassed() && !sim.isGeneratedRuntimeSettled()) return;
        // Revoke before finishJob: its UI refresh must not re-enter completion.
        clearRetest();
        boolean completed = false;
        String message = pending.result.getPlayerMessage();
        try {
            if (expired || !sameResult) message = "The retest could not finish. Check board power and try again.";
            else if (pending.result.isPassed()) {
                completed = pending.challenge.isCompleted() || pending.challenge.finishJob();
                if (!completed) message = "The board is not ready to finish. Check board power and retest.";
            }
        } catch (RuntimeException failure) {
            message = "The retest could not finish. Check board power and try again.";
        }
        session.retested(pending.token, pending.owner, completed, message);
    }

    void refresh() { if (released) return; finishRetest(false); if (bridgeReady) notifyView(bridge); }

    String snapshot(boolean includeCatalog) {
        JSONObject out = new JSONObject();
        out.put("token", new JSONNumber(session.token()));
        put(out, "screen", session.screen().name()); put(out, "message", session.message()); put(out, "notice", notice);
        put(out, "epoch", PlayerLaunchRequest.EPOCH); put(out, "build", GWT.getPermutationStrongName());
        out.put("hasBoard", JSONBoolean.getInstance(!released && session.owner() != null));
        out.put("privateQualification", JSONBoolean.getInstance(session.isQualification()));
        out.put("sessionPersistenceAvailable", JSONBoolean.getInstance(!session.isQualification() || qualificationPersistence));
        out.put("sessionSaveAvailable", JSONBoolean.getInstance(canSaveSession()));
        out.put("sessionHistoryCount", new JSONNumber(ownsCurrentBoard() ? sim.getGeneratedBoardInstance()
            .getPhysicalBoardRuntime().getSessionHistory().size() : 0));
        PlayerLaunchRequest request = session.request();
        if (request != null) { put(out, "replay", request.replay()); put(out, "profile", request.profile.name()); }
        JSONArray families = new JSONArray();
        for (String id : PlayerFamilyCatalog.families()) {
            JSONObject family = new JSONObject(); put(family, "id", id); put(family, "name", PlayerFamilyCatalog.name(id));
            put(family, "profile", PlayerFamilyCatalog.candidateProfile(id).name());
            family.put("procedural", JSONBoolean.getInstance(QuickPlayAdmission.supports(id)));
            families.set(families.size(), family);
        }
        out.put("families", families);
        if (session.screen() == PlayerSession.Screen.PREPARING && sim.generationCoordinator.getJob() != null) {
            GenerationCoordinator generation = sim.generationCoordinator;
            JSONObject progress = new JSONObject();
            put(progress, "label", generation.getProgressLabel());
            progress.put("paused", JSONBoolean.getInstance(generation.isProgressPaused()));
            progress.put("activeElapsedMs", new JSONNumber(generation.getProgressActiveMillis()));
            progress.put("percent", new JSONNumber(generation.getProgressPercent()));
            progress.put("candidate", new JSONNumber(generation.getJob().getCandidateIndex() + 1));
            progress.put("candidates", new JSONNumber(generation.getCandidateCount()));
            progress.put("phase", new JSONNumber(generation.getJob().getStage().ordinal() + 1));
            progress.put("phases", new JSONNumber(6));
            progress.put("units", new JSONNumber(generation.getJob().getStepCount()));
            progress.put("elapsedMs", new JSONNumber(generation.getProgressElapsedMillis()));
            progress.put("lastUpdateAgeMs", new JSONNumber(generation.getProgressAgeMillis()));
            out.put("progress", progress);
        }
        if (ownsCurrentBoard()) {
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            put(out, "complaint", challenge.getComplaintText());
            put(out, "retestInstruction", challenge.getCustomerRetestProfile().getPlayerInstruction());
            out.put("ready", JSONBoolean.getInstance(sim.isGeneratedSemanticInteractionEnabled()));
            out.put("completed", JSONBoolean.getInstance(challenge.isCompleted()));
            out.put("isolated", JSONBoolean.getInstance(sim.getBoardPowerController().isElectricallyUnpowered()));
            if (includeCatalog) {
            JSONArray catalogs = new JSONArray();
            for (PlayerShopCatalog.Category category : new PlayerShopCatalog(sim.getGeneratedBoardInstance()).categories()) {
                JSONObject catalog = new JSONObject(); put(catalog, "id", category.id);
                put(catalog, "title", category.title);
                JSONArray entries = new JSONArray();
                for (PlayerShopCatalog.Entry entry : category.entries()) {
                    JSONObject row = new JSONObject(); put(row, "id", entry.id); put(row, "label", category.label(entry));
                    entries.set(entries.size(), row);
                }
                catalog.put("entries", entries); catalog.put("looseCount", new JSONNumber(category.looseCount()));
                catalogs.set(catalogs.size(), catalog);
            }
            out.put("catalogs", catalogs);
            }
        }
        return out.toString();
    }

    private static void put(JSONObject object, String key, String value) { object.put(key, new JSONString(value == null ? "" : value)); }

    private static native boolean bridgeIsEmpty() /*-{
        return !$wnd.tsjProduct && typeof $wnd.tsjProductTeardown === 'function';
    }-*/;
    private native JavaScriptObject installBridge() /*-{
        var owner = this;
        var bridge = {
            available: $entry(function() { return owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::isBridgeAvailable()(); }),
            snapshot: $entry(function(includeCatalog) { return JSON.parse(owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::snapshot(Z)(includeCatalog !== false)); }),
            historyPage: $entry(function(token, view, offset) {
                if (typeof offset !== 'number' || offset % 1 !== 0 || offset < -1 || offset > 2147483647)
                    return { ok: false, error: 'This history page is unavailable.' };
                return JSON.parse(owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::historyPageJson(III)(token, view, offset));
            }),
            saveSession: $entry(function(token, view) {
                return JSON.parse(owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::saveSession(II)(token, view));
            }),
            action: $entry(function(token, view, name, a, b, c) {
                return owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::action(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(token, view, name, a || '', b || '', c || '');
            }),
            openView: $entry(function() { return owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::openView()(); }),
            closeView: $entry(function(token) { owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::closeView(I)(token); })
        };
        $wnd.tsjProduct = bridge;
        return bridge;
    }-*/;
    private static native boolean removeBridge(JavaScriptObject bridge) /*-{
        if ($wnd.tsjProduct !== bridge) return false;
        if (typeof $wnd.tsjProductTeardown !== 'function' || !$wnd.tsjProductTeardown(bridge)) return false;
        if ($wnd.tsjProduct !== bridge) return false;
        delete $wnd.tsjProduct;
        return typeof $wnd.tsjProduct === 'undefined';
    }-*/;
    private static native void notifyView(JavaScriptObject bridge) /*-{
        if ($wnd.tsjProduct === bridge && $wnd.tsjProductRefresh) $wnd.tsjProductRefresh();
    }-*/;
}
