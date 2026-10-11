package com.lushprojects.circuitjs1.client;

/** Public request and owner-token falsifiers, independent of DOM and CircuitJS settlement. */
public final class U04SessionContractTest {
    private static int assertions;
    private interface Attempt { void run(); }
    public static void main(String[] args) {
        for (String seed : new String[] {"0", "-1", "9007199254740993", "-9223372036854775808", "9223372036854775807"}) {
            PlayerLaunchRequest request = new PlayerLaunchRequest("RB15_CONTROL", seed, "MEDIUM");
            PlayerLaunchRequest replay = PlayerLaunchRequest.parse(request.replay());
            check(replay.seed == Long.parseLong(seed), "exact signed-long replay");
            check(replay.replay().equals(request.replay()), "canonical replay round trip");
            check(replay.generation().canonical().contains("difficulty=MEDIUM@1"), "request reaches admission");
        }
        verifyQ30PlayerRequestAndOwner();
        verifyQualificationSession();
        verifyHeldRb56Admission();
        for (final String invalid : new String[] {"01", "+1", "-0", "1.0", "1e3", " 1", "1 ", "9223372036854775808", "-9223372036854775809", "", "NaN"})
            reject(new Attempt() { public void run() { new PlayerLaunchRequest("LED_INDICATOR", invalid, "EASY"); }});
        for (final String profile : new String[] {"HARD", "PSYCHOTIC", "easy", "", "quick-play"})
            reject(new Attempt() { public void run() { new PlayerLaunchRequest("LED_INDICATOR", "0", profile); }});
        for (final String replay : new String[] {"tsj-alpha/0/EASY/LED_INDICATOR/0", "tsj-alpha/1/EASY/UNKNOWN/0", "tsj-alpha/1/EASY/LED_INDICATOR/0/x", "", "tsj-alpha/1/EASY/LED_INDICATOR/"})
            reject(new Attempt() { public void run() { PlayerLaunchRequest.parse(replay); }});
        PlayerLaunchRequest launch = new PlayerLaunchRequest("LED_INDICATOR", "0", "EASY");
        GenerationRequest composed = new PlayerLaunchRequest("COMPOSED_CONTROLLED_INDICATOR", "0", "MEDIUM").generation();
        check(!composed.isQuickPlay() && composed.requiresExplicitCompletion() && composed.isComposition(),
            "composed product session requires explicit completion without a leaf Quick Play publication");
        check(!launch.generation().isQuickPlay() && launch.generation().requiresExplicitCompletion(),
            "typed product completion is independent of legacy Quick Play selection");
        PlayerSession session = new PlayerSession(); Object a = new Object(), b = new Object();
        int initial = session.token(), pending = session.begin(launch);
        check(session.screen() == PlayerSession.Screen.PREPARING && !session.accepts(initial), "launch revokes menu callback");
        check(!session.prepared(pending, new PlayerLaunchRequest("LED_INDICATOR", "0", "EASY"), a), "same text is not the pending request owner");
        check(!session.enter(pending, PlayerSession.Screen.WORKBENCH), "cannot expose pending graph");
        check(session.prepared(pending, launch, a), "exact pending request admitted");
        check(!session.prepared(pending, launch, b), "late completion cannot replace owner");
        check(session.screen() == PlayerSession.Screen.TICKET && session.owner() == a, "ticket retains exact admitted owner");
        check(session.enter(session.token(), PlayerSession.Screen.WORKBENCH), "accept ticket");
        int test = session.retest(session.token(), a);
        check(!session.enter(session.token(), PlayerSession.Screen.MENU), "settling retest keeps session locked");
        check(!session.retested(test, b, true, "bad"), "foreign result rejected");
        check(session.retested(test, a, false, "unrepaired"), "failed retest returns to workbench");
        int next = session.begin(launch);
        check(session.failed(next, true, "cancelled") && session.owner() == a, "cancel retains previous owner");
        check(!session.retested(test, a, true, "late"), "old retest cannot complete successor session");
        next = session.begin(launch);
        check(session.failed(next, false, "failed") && session.owner() == a, "error retains board");
        check(session.enter(session.token(), PlayerSession.Screen.WORKBENCH), "resume after failure");
        test = session.retest(session.token(), a);
        check(session.retested(test, a, true, "passed") && session.screen() == PlayerSession.Screen.RESULTS, "verified completion reaches results");
        check(!session.retested(test, a, false, "late"), "results are not changed by consumed callback");
        session.enter(session.token(), PlayerSession.Screen.WORKBENCH);
        test = session.retest(session.token(), a);
        session.adopt(b, launch);
        check(!session.retested(test, a, true, "late") && session.owner() == b &&
            session.screen() == PlayerSession.Screen.WORKBENCH, "pending completion cannot finish an adopted successor");
        check(PlayerFamilyCatalog.families().size() == 11 &&
            PlayerFamilyCatalog.families().contains(Rb30Plan.FAMILY_ID),
            "accepted Q30 is present in the normal-player menu");
        check(PlayerFamilyCatalog.registeredFamilies().size() == 12 &&
            PlayerFamilyCatalog.registeredFamilies().contains(Rb56Plan.FAMILY_ID),
            "held RB56 retains its registration without entering the eleven-family player menu");
        verifyGenericShop();
        verifyAllCatalogLabels();
        System.out.println("PASS: U04 session contracts assertions=" + assertions);
    }

    private static void verifyQualificationSession() {
        final PlayerLaunchRequest launch = new PlayerLaunchRequest("LED_INDICATOR", "0", "EASY");
        final PlayerSession session = new PlayerSession();
        final Object first = new Object(), successor = new Object();
        SessionSnapshot before = new SessionSnapshot(session);
        reject(new Attempt() { public void run() { session.adoptQualification(null); }});
        before.requireUnchanged(session, "missing qualification owner cannot mutate the menu");
        int menuToken = session.token();
        session.adoptQualification(first);
        check(session.isQualification() && session.screen() == PlayerSession.Screen.TICKET &&
            session.owner() == first && session.request() == null && field(session, "pending") == null &&
            !session.accepts(menuToken), "private ticket retains only its exact owner, without a public request or replay");
        before = new SessionSnapshot(session);
        rejectState(new Attempt() { public void run() { session.begin(launch); }});
        rejectState(new Attempt() { public void run() { session.adopt(successor, launch); }});
        rejectState(new Attempt() { public void run() { session.adoptQualification(first); }});
        rejectState(new Attempt() { public void run() { session.adoptQualification(successor); }});
        check(!session.prepared(session.token(), launch, successor) &&
            !session.failed(session.token(), true, "foreign preparation"),
            "qualification did not create a normal pending generation");
        before.requireUnchanged(session, "ordinary launch, adoption and preparation cannot replace the private lease");
        check(!session.enter(menuToken, PlayerSession.Screen.WORKBENCH), "pre-qualification menu token is stale");
        int ticketToken = session.token();
        check(session.enter(ticketToken, PlayerSession.Screen.WORKBENCH) && !session.accepts(ticketToken),
            "accepting the private ticket advances its callback token");
        before = new SessionSnapshot(session);
        rejectState(new Attempt() { public void run() { session.retest(session.token(), successor); }});
        before.requireUnchanged(session, "foreign retest cannot claim the private owner");
        int retest = session.retest(session.token(), first);
        check(session.screen() == PlayerSession.Screen.RETEST && session.isQualification() &&
            session.owner() == first && session.request() == null, "private retest locks the same request-free owner");
        before = new SessionSnapshot(session);
        check(!session.enter(retest, PlayerSession.Screen.MENU) &&
            !session.retested(retest, successor, true, "foreign result"), "private retest refuses navigation and foreign results");
        rejectState(new Attempt() { public void run() { session.adoptQualification(successor); }});
        before.requireUnchanged(session, "blocked retest callbacks preserve the private lease");
        check(session.retested(retest, first, false, "unrepaired") &&
            session.screen() == PlayerSession.Screen.WORKBENCH && session.isQualification() &&
            session.owner() == first && session.request() == null,
            "failed private retest returns its exact owner to workbench without a replay");
        check(!session.retested(retest, first, true, "late"), "consumed failed retest cannot complete the owner");
        retest = session.retest(session.token(), first);
        check(session.retested(retest, first, true, "passed") &&
            session.screen() == PlayerSession.Screen.RESULTS && session.isQualification() &&
            session.owner() == first && session.request() == null,
            "successful private retest reaches results without minting a normal launch");
        check(session.enter(session.token(), PlayerSession.Screen.WORKBENCH), "private results can return to the owned workbench");
        final int closingRetest = session.retest(session.token(), first);
        before = new SessionSnapshot(session);
        rejectState(new Attempt() { public void run() { session.revoke(successor); }});
        rejectState(new Attempt() { public void run() { session.revoke(null); }});
        before.requireUnchanged(session, "wrong-owner revoke leaves every session field and callback token unchanged");
        session.revoke(first);
        check(session.screen() == PlayerSession.Screen.MENU && session.owner() == null &&
            session.request() == null && field(session, "pending") == null && !session.isQualification() &&
            session.message().length() == 0 && !session.accepts(closingRetest),
            "exact-owner revoke retires pending retest and clears the private lease");
        check(!session.retested(closingRetest, first, true, "after close") &&
            !session.enter(closingRetest, PlayerSession.Screen.WORKBENCH) &&
            !session.prepared(closingRetest, launch, first), "closed private callbacks cannot revive the board");
        session.adoptQualification(successor);
        before = new SessionSnapshot(session);
        check(!session.retested(closingRetest, first, true, "old owner"), "late predecessor cannot finish the private successor");
        rejectState(new Attempt() { public void run() { session.revoke(first); }});
        before.requireUnchanged(session, "predecessor cannot revoke the successor lease");
        session.revoke(successor);
        int normal = session.begin(launch);
        check(session.screen() == PlayerSession.Screen.PREPARING && !session.isQualification() &&
            session.owner() == null, "ordinary generation is available after private revocation");
        before = new SessionSnapshot(session);
        rejectState(new Attempt() { public void run() { session.adoptQualification(first); }});
        before.requireUnchanged(session, "qualification cannot replace a pending ordinary generation");
        check(session.prepared(normal, launch, successor) && session.request() == launch &&
            !session.isQualification(), "ordinary prepared ticket preserves its public replay request");
        before = new SessionSnapshot(session);
        rejectState(new Attempt() { public void run() { session.adoptQualification(first); }});
        before.requireUnchanged(session, "qualification cannot replace an existing ordinary owner");
        check(session.enter(session.token(), PlayerSession.Screen.WORKBENCH), "ordinary workbench remains available afterward");
        normal = session.retest(session.token(), successor);
        check(session.retested(normal, successor, true, "normal pass") &&
            session.screen() == PlayerSession.Screen.RESULTS && session.request() == launch &&
            !session.isQualification(), "ordinary completion and replay remain available after private use");
    }

    private static void verifyHeldRb56Admission() {
        check(PlayerFamilyCatalog.isRegistered(Rb56Plan.FAMILY_ID) &&
            !PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID) &&
            !PlayerFamilyCatalog.families().contains(Rb56Plan.FAMILY_ID),
            "RB56 remains registered but held outside normal-player selection");
        reject(new Attempt() { public void run() { PlayerFamilyCatalog.requireNormalPlayerEnabled(Rb56Plan.FAMILY_ID); }});
        reject(new Attempt() { public void run() { new PlayerLaunchRequest(Rb56Plan.FAMILY_ID, "-1022", "HARD"); }});
        reject(new Attempt() { public void run() { new PlayerLaunchRequest(Rb56Plan.FAMILY_ID, "-1022", "MEDIUM").generation(); }});
        reject(new Attempt() { public void run() { PlayerLaunchRequest.random(Rb56Plan.FAMILY_ID, "-1022", "MEDIUM"); }});
    }

    /** Snapshot only session fields; no electrical graph, admission or controller calls. */
    private static final class SessionSnapshot {
        final int token;
        final PlayerSession.Screen screen;
        final Object owner, pending, savedScreen;
        final PlayerLaunchRequest request;
        final String message;
        final boolean qualification;
        SessionSnapshot(PlayerSession session) {
            token = session.token(); screen = session.screen(); owner = session.owner();
            request = session.request(); message = session.message(); qualification = session.isQualification();
            pending = field(session, "pending"); savedScreen = field(session, "savedScreen");
        }
        void requireUnchanged(PlayerSession session, String label) {
            check(token == session.token() && screen == session.screen() && owner == session.owner() &&
                request == session.request() && message.equals(session.message()) && qualification == session.isQualification() &&
                pending == field(session, "pending") && savedScreen == field(session, "savedScreen"), label);
        }
    }
    private static Object field(PlayerSession session, String name) {
        try {
            java.lang.reflect.Field field = PlayerSession.class.getDeclaredField(name);
            field.setAccessible(true); return field.get(session);
        } catch (Exception failure) { throw new AssertionError("Missing session field " + name, failure); }
    }
    private static void rejectState(Attempt attempt) {
        assertions++;
        try { attempt.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Conflicting session operation accepted");
    }

    private static void verifyGenericShop() {
        CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
        CircuitElm.sim = sim;
        for (long seed : new long[] { 0, 3 }) {
            GeneratedBoardInstance owner = new PlayerLaunchRequest(
                "COMPOSED_CONTROLLED_INDICATOR", String.valueOf(seed), "MEDIUM")
                .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            java.util.Vector<String> partOrder = runtime.getPartOrder();
            check(runtime.getWorkbenchPartsProviders().size() == 15,
                "every composed-board position has its physical replacement catalog");
            final PlayerShopCatalog shop = new PlayerShopCatalog(owner);
            java.util.HashSet<String> physicalTypes = new java.util.HashSet<String>();
            for (String id : owner.getBoard().getComponentIds()) physicalTypes.add(owner.getBoard().getComponent(id).getType());
            check(shop.categories().size() == physicalTypes.size(), "catalogs group every physical part type without destination duplicates");
            PlayerShopCatalog.Category resistors = shop.category("RESISTOR");
            check("Resistors".equals(resistors.title) && resistors.entries().size() >= 73 &&
                    resistors.entries().size() % 73 == 0,
                "complete E12 resistor catalogs for every actual procedural lead geometry");
            check(resistors.looseCount() == 0, "installed resistors are not counted as loose stock");
            int resistorOptionCount = resistors.entries().size();
            check(resistors.label(find(resistors, "R_CATALOG_330", "SPAN_220")).startsWith("330 Ohm +/-5%") &&
                resistors.label(find(resistors, "R_CATALOG_1000", "SPAN_220")).startsWith("1000 Ohm +/-5%") &&
                resistors.label(find(resistors, "R_CATALOG_10000000", "SPAN_220")).startsWith("10000000 Ohm +/-5%"),
                "public catalog retains selected specifications across the value range and procedural fit labels");
            resistors.entries().clear(); shop.categories().clear();
            check(shop.categories().size() == physicalTypes.size() &&
                resistors.entries().size() == resistorOptionCount &&
                partOrder.equals(runtime.getPartOrder()), "projection does not own or mutate catalog/inventory storage");
            reject(new Attempt() { public void run() { shop.category("RLOAD"); } });
            reject(new Attempt() { public void run() { shop.category("RESISTOR").entry("R_CATALOG_UNKNOWN"); } });
        }
        GeneratedBoardInstance npn = new PlayerLaunchRequest("NPN_LOW_SIDE_SWITCH", "0", "EASY")
            .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
        PlayerShopCatalog.Category resistors = new PlayerShopCatalog(npn).category("RESISTOR");
        // Normal NPN geometry is now procedural. Inspect the independently owned
        // slot geometry instead of asserting the retired template's 240/260 pair.
        java.util.HashSet<String> variants = new java.util.HashSet<String>();
        java.util.HashSet<String> handles = new java.util.HashSet<String>();
        for (String component : new String[] {"RLOAD", "RB", "RPD"}) {
            PhysicalGeometryRealization expected = npn.getPhysicalBoardRuntime()
                .getSlot(component).getGeometryRealization();
            String variant = expected.getVariantKey();
            variants.add(variant);
            PlayerShopCatalog.Entry entry = find(resistors, "R_CATALOG_1000", variant);
            check(entry.geometry.isEquivalentTo(expected), "catalog preserves actual procedural slot geometry");
            check(npn.getPhysicalBoardRuntime().getSlot(entry.acquisitionComponent)
                    .getGeometryRealization().isEquivalentTo(expected),
                "public option resolves a real compatible acquiring provider");
            check(resistors.entry(entry.id) == entry &&
                    resistors.label(entry).startsWith("1000 Ohm +/-5%") &&
                    !resistors.label(entry).contains("SPAN_") &&
                    !resistors.label(entry).contains(component),
                "physical fit has a stable public handle without implementation coordinates");
            handles.add(entry.id);
        }
        check(variants.size() >= 2 && handles.size() == variants.size() &&
                resistors.entries().size() == 73 * variants.size(),
            "distinct physical fits retain complete E12 catalogs and distinct handles");
    }
    private static void verifyAllCatalogLabels() {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        for (String family : PlayerFamilyCatalog.families()) {
            check(PlayerFamilyCatalog.isNormalPlayerEnabled(family),
                "normal catalog construction requires an enabled family");
            GeneratedBoardInstance owner = constructPlayer(family,
                Rb30Plan.FAMILY_ID.equals(family) ? 13L : 3L);
            PlayerShopCatalog catalog = new PlayerShopCatalog(owner);
            for (PlayerShopCatalog.Category category : catalog.categories()) {
                java.util.HashSet<String> labels = new java.util.HashSet<String>();
                for (PlayerShopCatalog.Entry entry : category.entries()) {
                    String label = category.label(entry);
                    check(label.length() > 0 && labels.add(label), "every supported physical fit has a distinct public catalog label: " + family + "/" + label);
                    check(category.entry(entry.id) == entry, "catalog label does not change acquisition identity");
                }
            }
            if (family.equals("RB15_CONTROL")) {
                PlayerShopCatalog.Category connectors = catalog.category("CONNECTOR");
                java.util.HashSet<String> labels = new java.util.HashSet<String>();
                for (PlayerShopCatalog.Entry entry : connectors.entries()) labels.add(connectors.label(entry));
                check(labels.toString().contains("Standard connector layout") && labels.toString().contains("Mirrored connector layout"),
                    "RB15 Shop supports both actual connector orientations instead of throwing");
            }
        }
    }
    private static void verifyQ30PlayerRequestAndOwner() {
        for (final String seed : new String[] {"9007199254740993",
                "-9007199254740993", "-9223372036854775808", "9223372036854775807"}) {
            PlayerLaunchRequest launch = PlayerLaunchRequest.random(Rb30Plan.FAMILY_ID,
                seed, "MEDIUM");
            GenerationRequest search = launch.generation();
            check(launch.seed == Long.parseLong(seed) && launch.candidateSearch &&
                launch.profile == DifficultyProfile.MEDIUM && search.candidateCount() == 4 &&
                search.getDescriptor().getRootSeed() == Long.parseLong(seed) &&
                search.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                MediumBoardNormalAdmission.IDENTITY.equals(
                    search.getRequiredPhysicalAdmissionIdentity()),
                "Q30 public launch preserves exact seed and admitted MEDIUM capability");

            long acceptedSeed = search.candidate(3).getDescriptor().getRootSeed();
            PlayerLaunchRequest accepted = launch.accepted(acceptedSeed);
            PlayerSession session = new PlayerSession();
            Object owner = new Object();
            int token = session.begin(launch);
            check(session.prepared(token, launch, accepted, owner) &&
                session.request() == accepted && session.owner() == owner,
                "Q30 candidate acceptance retains its exact normal-player owner");

            PlayerLaunchRequest replay = PlayerLaunchRequest.parse(accepted.replay());
            GenerationRequest exact = replay.generation();
            check(!replay.candidateSearch && replay.familyId.equals(Rb30Plan.FAMILY_ID) &&
                replay.seed == acceptedSeed && replay.profile == DifficultyProfile.MEDIUM &&
                replay.replay().equals(accepted.replay()) && exact.candidateCount() == 1 &&
                exact.getDescriptor().getRootSeed() == acceptedSeed &&
                exact.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                MediumBoardNormalAdmission.IDENTITY.equals(
                    exact.getRequiredPhysicalAdmissionIdentity()),
                "Q30 public replay preserves the accepted exact board without retry");
        }
    }
    private static GeneratedBoardInstance constructPlayer(String family, long seed) {
        GenerationRequest request = new PlayerLaunchRequest(family, Long.toString(seed),
            PlayerFamilyCatalog.candidateProfile(family).name()).generation();
        GenerationRequest.Prepared prepared = request.resolve(new GenerationRequest.PlanCache());
        if (!Rb30Plan.FAMILY_ID.equals(family)) return prepared.construct().instance;
        GenerationRequest.ConstructionSession session = prepared.beginConstruction();
        int steps = 0;
        while (!session.advance()) {
            if (++steps > GenerationCoordinator.MAX_JOB_STEPS)
                throw new AssertionError("Normal Q30 physical route exceeded the unchanged step bound");
        }
        return session.result().instance;
    }
    private static PlayerShopCatalog.Entry find(PlayerShopCatalog.Category category, String catalogId, String variant) {
        for (PlayerShopCatalog.Entry entry : category.entries())
            if (entry.catalogId.equals(catalogId) && entry.geometry.getVariantKey().equals(variant)) return entry;
        throw new AssertionError("Missing catalog/physical-fit option " + catalogId + "/" + variant);
    }
    private static void reject(Attempt a) { assertions++; try { a.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid public request accepted"); }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
