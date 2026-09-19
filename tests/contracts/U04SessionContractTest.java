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
        check(PlayerFamilyCatalog.families().size() == 9, "all current normal families represented");
        verifyGenericShop();
        verifyAllCatalogLabels();
        System.out.println("PASS: U04 session contracts assertions=" + assertions);
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
            GeneratedBoardInstance owner = new PlayerLaunchRequest(family, "3",
                PlayerFamilyCatalog.candidateProfile(family).name()).generation()
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
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
    private static PlayerShopCatalog.Entry find(PlayerShopCatalog.Category category, String catalogId, String variant) {
        for (PlayerShopCatalog.Entry entry : category.entries())
            if (entry.catalogId.equals(catalogId) && entry.geometry.getVariantKey().equals(variant)) return entry;
        throw new AssertionError("Missing catalog/physical-fit option " + catalogId + "/" + variant);
    }
    private static void reject(Attempt a) { assertions++; try { a.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid public request accepted"); }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
