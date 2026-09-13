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
            check(runtime.getWorkbenchPartsProviders().size() == 4,
                "two channel driver/load catalogs exist in the real composed runtime");
            final PlayerShopCatalog shop = new PlayerShopCatalog(owner);
            check(shop.categories().size() == 1, "four target catalogs become one part-type catalog");
            PlayerShopCatalog.Category resistors = shop.category("RESISTOR");
            check("Resistors".equals(resistors.title) && resistors.entries().size() == 73,
                "one complete E12 resistor catalog without destination duplicates");
            check(resistors.looseCount() == 0, "installed resistors are not counted as loose stock");
            check("330 Ohm +/-5%".equals(resistors.label(find(resistors, "R_CATALOG_330", "SPAN_220"))) &&
                "1000 Ohm +/-5%".equals(resistors.label(find(resistors, "R_CATALOG_1000", "SPAN_220"))) &&
                "10000000 Ohm +/-5%".equals(resistors.label(find(resistors, "R_CATALOG_10000000", "SPAN_220"))),
                "public catalog retains selected specifications across the value range");
            resistors.entries().clear(); shop.categories().clear();
            check(shop.categories().size() == 1 && resistors.entries().size() == 73 &&
                partOrder.equals(runtime.getPartOrder()), "projection does not own or mutate catalog/inventory storage");
            reject(new Attempt() { public void run() { shop.category("RLOAD"); } });
            reject(new Attempt() { public void run() { shop.category("RESISTOR").entry("R_CATALOG_UNKNOWN"); } });
        }
        GeneratedBoardInstance npn = new PlayerLaunchRequest("NPN_LOW_SIDE_SWITCH", "0", "EASY")
            .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
        PlayerShopCatalog.Category resistors = new PlayerShopCatalog(npn).category("RESISTOR");
        check(resistors.entries().size() == 146, "two current lead geometries retain complete purchasable catalogs");
        PlayerShopCatalog.Entry medium = find(resistors, "R_CATALOG_1000", "SPAN_240");
        PlayerShopCatalog.Entry wide = find(resistors, "R_CATALOG_1000", "SPAN_260");
        check(!medium.id.equals(wide.id) && "RLOAD".equals(medium.acquisitionComponent) &&
            "RB".equals(wide.acquisitionComponent), "each public option resolves its real recipe and acquiring geometry");
        check(resistors.label(medium).equals("1000 Ohm +/-5% - Medium lead spacing") &&
            resistors.label(wide).equals("1000 Ohm +/-5% - Wide lead spacing"),
            "physical fit options have public descriptions without destination names or implementation dimensions");
        check(resistors.entry(medium.id) == medium && resistors.entry(wide.id) == wide,
            "public specification handles retain distinct physical acquisition requests");
    }
    private static PlayerShopCatalog.Entry find(PlayerShopCatalog.Category category, String catalogId, String variant) {
        for (PlayerShopCatalog.Entry entry : category.entries())
            if (entry.catalogId.equals(catalogId) && entry.geometry.getVariantKey().equals(variant)) return entry;
        throw new AssertionError("Missing catalog/physical-fit option " + catalogId + "/" + variant);
    }
    private static void reject(Attempt a) { assertions++; try { a.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid public request accepted"); }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
