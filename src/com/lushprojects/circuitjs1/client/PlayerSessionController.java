package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.GWT;
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
    private String notice = "";
    private boolean bridgeReady;
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
        installBridge(); bridgeReady = true;
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
                launch(new PlayerLaunchRequest(selected.getFamilyId(), Long.toString(selected.getSeed()),
                    PlayerFamilyCatalog.candidateProfile(selected.getFamilyId()).name()));
            } else if (query.getValue("tsjFixture") != null) notice = "Developer fixtures require the separate developer mode.";
        } catch (RuntimeException failure) { notice = "This replay is unsupported. Check its epoch, family, profile and exact seed."; }
        refresh();
    }

    private void launch(final PlayerLaunchRequest request) {
        if (!sim.isGeneratedRuntimeSettled() || sim.generationCoordinator.isRunning())
            throw new IllegalStateException("The current board is busy");
        final int token = session.begin(request);
        notice = ""; refresh();
        try {
            sim.generationCoordinator.start(request.generation(), new GenerationCoordinator.Completion() {
                public void complete(GenerationJob job, GeneratedBoardInstance published) {
                    if (job.getOutcome() == GenerationJob.Outcome.PASS) {
                        session.prepared(token, request, published);
                    } else session.failed(token, job.getOutcome() == GenerationJob.Outcome.CANCELLED,
                        job.getOutcome() == GenerationJob.Outcome.CANCELLED ? "Board preparation cancelled." :
                        "This exact board could not be prepared for this difficulty. Try another seed or board. " +
                        "Replay: " + request.replay());
                    sim.refreshChallengeInteractionState(); refresh(); sim.repaint();
                }
            }, true);
        } catch (RuntimeException failure) {
            session.failed(token, false, "Board preparation could not start. The previous board is retained.");
            refresh();
        }
    }

    /** Captured modal leases invalidate a retained Shop callback on close/reopen. */
    int openView() { return ++viewToken; }
    void closeView(int token) { if (token == viewToken) viewToken++; }

    String action(int expected, int view, String action, String first, String second, String third) {
        if (!session.accepts(expected)) return "This control belongs to an earlier session.";
        try {
            if ("launch".equals(action)) launch(new PlayerLaunchRequest(first, second, third));
            else if ("random".equals(action)) launch(PlayerLaunchRequest.random(first, second, third));
            else if ("replay".equals(action)) launch(PlayerLaunchRequest.parse(first));
            else if ("cancel".equals(action) && session.screen() == PlayerSession.Screen.PREPARING) sim.generationCoordinator.cancel();
            else if ("menu".equals(action)) session.enter(expected, PlayerSession.Screen.MENU);
            else if ("resume".equals(action)) session.enter(expected, PlayerSession.Screen.WORKBENCH);
            else if ("retest".equals(action)) retest(expected);
            else if ("acquire".equals(action)) {
                if (view != viewToken) return "This catalog view has closed.";
                return acquire(first, second);
            } else return "This action is unavailable.";
        } catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
        catch (RuntimeException failure) { return "The action is unavailable while the board is busy or requires isolation."; }
        refresh(); return "";
    }

    private boolean ownsCurrentBoard() {
        return session.owner() != null && sim.getGeneratedBoardInstance() == session.owner() &&
            sim.getGeneratedChallengeController() != null && !sim.generationCoordinator.isRunning();
    }
    private String acquire(String categoryId, String catalogId) {
        if (!ownsCurrentBoard() || session.screen() != PlayerSession.Screen.WORKBENCH || !sim.isChallengeInteractionEnabled() ||
                sim.activeMeasurementOverlay || !sim.getBoardPowerController().isElectricallyUnpowered())
            return "Disconnect all board supplies and wait for settling before acquiring parts.";
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        if (owner.getPhysicalBoardRuntime().getPhysicalParts().size() >= 96)
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
        PhysicalPart<?> acquired = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(catalogId);
        if (acquired == null || acquired.isInstalled() || !catalog.ownsPart(acquired.getId()) ||
                original != owner.getPhysicalBoardRuntime().getInstalledPart(component))
            throw new IllegalStateException("Catalog acquisition changed its physical slot");
        refresh(); return "Added to the real Parts Tray. Select the loose part on the workbench to install it.";
    }

    private void retest(int expected) {
        if (!ownsCurrentBoard() || !sim.isGeneratedSemanticInteractionEnabled() ||
                sim.getGeneratedChallengeController().isCompleted()) return;
        final Object owner = session.owner();
        int token = session.retest(expected, owner); refresh();
        try {
            GeneratedCustomerRetestResult result = sim.performCustomerRetest();
            pendingRetest = new PendingRetest(token, owner, sim.getGeneratedChallengeController(), result);
            // Functional profiles can leave ordinary analysis queued while
            // restoring the player's input. Completion belongs to that settled
            // owner, never to the synchronous return of the profile alone.
            pendingRetest.watchdog.schedule(5000);
            finishRetest(false);
        } catch (RuntimeException failure) {
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

    void refresh() { finishRetest(false); if (bridgeReady) notifyView(); }

    String snapshot() {
        JSONObject out = new JSONObject();
        out.put("token", new JSONNumber(session.token()));
        put(out, "screen", session.screen().name()); put(out, "message", session.message()); put(out, "notice", notice);
        put(out, "epoch", PlayerLaunchRequest.EPOCH); put(out, "build", GWT.getPermutationStrongName());
        out.put("hasBoard", JSONBoolean.getInstance(session.owner() != null));
        PlayerLaunchRequest request = session.request();
        if (request != null) { put(out, "replay", request.replay()); put(out, "profile", request.profile.name()); }
        JSONArray families = new JSONArray();
        for (String id : PlayerFamilyCatalog.families()) {
            JSONObject family = new JSONObject(); put(family, "id", id); put(family, "name", PlayerFamilyCatalog.name(id));
            put(family, "profile", PlayerFamilyCatalog.candidateProfile(id).name()); families.set(families.size(), family);
        }
        out.put("families", families);
        if (ownsCurrentBoard()) {
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            put(out, "complaint", challenge.getComplaintText());
            put(out, "retestInstruction", challenge.getCustomerRetestProfile().getPlayerInstruction());
            out.put("ready", JSONBoolean.getInstance(sim.isGeneratedSemanticInteractionEnabled()));
            out.put("completed", JSONBoolean.getInstance(challenge.isCompleted()));
            out.put("isolated", JSONBoolean.getInstance(sim.getBoardPowerController().isElectricallyUnpowered()));
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
        return out.toString();
    }

    private static void put(JSONObject object, String key, String value) { object.put(key, new JSONString(value == null ? "" : value)); }

    private native void installBridge() /*-{
        var owner = this;
        $wnd.tsjProduct = {
            snapshot: $entry(function() { return JSON.parse(owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::snapshot()()); }),
            action: $entry(function(token, view, name, a, b, c) {
                return owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::action(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(token, view, name, a || '', b || '', c || '');
            }),
            openView: $entry(function() { return owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::openView()(); }),
            closeView: $entry(function(token) { owner.@com.lushprojects.circuitjs1.client.PlayerSessionController::closeView(I)(token); })
        };
    }-*/;
    private static native void notifyView() /*-{
        if ($wnd.tsjProductRefresh) $wnd.tsjProductRefresh();
    }-*/;
}
