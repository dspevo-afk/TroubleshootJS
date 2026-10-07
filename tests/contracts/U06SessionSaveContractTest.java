package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Current-format parser and bounded history falsifiers. Runtime reconstruction is a separate gate. */
public final class U06SessionSaveContractTest {
    private static int assertions;
    private interface Attempt { void run(); }

    public static void main(String[] args) {
        verifyFixture();
        verifyOperationsAndCopies();
        verifyInvalidFiles();
        verifyInvalidRecords();
        verifyFuses();
        verifyBounds();
        verifyHistoryPages();
        verifyHistoryPageLeases();
        verifyInventoryPopulation();
        System.out.println("PASS: U06 session save contracts assertions=" + assertions);
    }

    /** Frozen independent wire bytes for a compiled-environment parity consumer. */
    static String crossEnvironmentFixture() {
        return "tsj-session/1\ntsj-session-model/1\nrestart\n" +
            "47:tsj-alpha/4/EASY/LED_INDICATOR/9007199254740993\n" +
            "13:fixture-build\n19:fixture-realization\n13:fixture-state\n" +
            "HISTORY\n1\n5:RESET\n0:\n0:\n0:\n" +
            "SOURCES\n1\n4:MAIN\n9:CONNECTED\n16:3fd0000000000000\n" +
            "STRESS\n1\n2:R1\n16:3fe0000000000000\n16:4010000000000000\n" +
            "7:HEALTHY\n4:NONE\n" +
            "FUSES\n1\n2:F1\n16:3fc0000000000000\n6:INTACT\nEND\n";
    }

    private static PlayerSessionSave fixture() {
        Vector<PlayerSessionSave.Operation> operations = new Vector<PlayerSessionSave.Operation>();
        operations.add(new PlayerSessionSave.Operation("RESET", "", "", ""));
        Vector<PlayerSessionSave.Source> sources = new Vector<PlayerSessionSave.Source>();
        sources.add(new PlayerSessionSave.Source("MAIN", true, .25));
        Vector<PlayerSessionSave.Stress> stress = new Vector<PlayerSessionSave.Stress>();
        stress.add(new PlayerSessionSave.Stress("R1", .5, 4, false, Double.NaN));
        Vector<PlayerSessionSave.Fuse> fuses = new Vector<PlayerSessionSave.Fuse>();
        fuses.add(new PlayerSessionSave.Fuse("F1", .125, false));
        return new PlayerSessionSave("tsj-alpha/4/EASY/LED_INDICATOR/9007199254740993",
            "fixture-build", "fixture-realization", "fixture-state", operations, sources, stress, fuses);
    }

    private static void verifyFixture() {
        PlayerSessionSave save = fixture();
        String text = crossEnvironmentFixture();
        check(text.equals(save.encode()), "independent canonical wire fixture");
        PlayerSessionSave read = PlayerSessionSave.parse(text);
        check(text.equals(read.encode()), "parse/encode retains canonical bytes");
        check(read.replay.equals(save.replay) && read.build.equals("fixture-build") &&
            read.realization.equals("fixture-realization") && read.stateSignature.equals("fixture-state"),
            "declared identity fields survive");
        check(read.getSources().get(0).limitAmps == .25 && read.getSources().get(0).connected &&
            read.getStress().get(0).damage == .5 && read.getStress().get(0).serviceTime == 4 &&
            Double.isNaN(read.getStress().get(0).failureTime), "numeric state survives exact bit encoding");
        check(read.getFuses().size() == 1 && read.getFuses().get(0).partId.equals("F1") &&
            read.getFuses().get(0).heat == .125 && !read.getFuses().get(0).blown,
            "independent fuse heat bits and intact state survive");
        check("0".equals(PlayerSessionSave.number(0)) &&
            "3fd0000000000000".equals(PlayerSessionSave.number(.25)), "numeric bits have independent expectations");
        for (String seed : new String[] {"0", "-1", "9007199254740993", "-9007199254740993",
                "-9223372036854775808", "9223372036854775807"}) {
            PlayerSessionSave exact = new PlayerSessionSave("tsj-alpha/4/EASY/LED_INDICATOR/" + seed,
                "build", "realization\nwith:delimiters", "signature\nHISTORY\n0\nEND\n",
                save.getHistory(), save.getSources(), save.getStress(), save.getFuses());
            PlayerSessionSave restored = PlayerSessionSave.parse(exact.encode());
            check(restored.replay.endsWith("/" + seed) && exact.encode().equals(restored.encode()),
                "exact signed-long seed and framed multiline fields survive");
        }
    }

    private static void verifyOperationsAndCopies() {
        Vector<PlayerSessionSave.Operation> history = new Vector<PlayerSessionSave.Operation>();
        String[][] rows = {
            {"REMOVE", "R1", "R1_ORIGINAL", ""},
            {"INSTALL", "R1", "R1_CATALOG_PART_0", ""},
            {"ACQUIRE", "R1", "R_CATALOG_1000", "R1_CATALOG_PART_0"},
            {"CATALOG", "R1", "R_CATALOG_1000", "R1_CATALOG_PART_1"},
            {"LEAD", "R1", "R1.1", "CONNECTED"},
            {"LEAD", "R1", "R1.2", "DISCONNECTED"},
            {"GRAPH_REMOVE", "R1", "", ""},
            {"GRAPH_RESTORE", "R1", "", ""},
            {"INPUT", "CONTROL_INPUT_HIGH", "", ""},
            {"SOURCE", "MAIN", "DISCONNECTED", "0.25"},
            {"SOURCE", "CONTROL", "CONNECTED", "NONE"},
            {"RESET", "", "", ""}
        };
        for (String[] row : rows) history.add(new PlayerSessionSave.Operation(row[0], row[1], row[2], row[3]));
        Vector<PlayerSessionSave.Source> sources = new Vector<PlayerSessionSave.Source>();
        sources.add(new PlayerSessionSave.Source("MAIN", false, .001));
        sources.add(new PlayerSessionSave.Source("CONTROL", true, Double.NaN));
        Vector<PlayerSessionSave.Stress> stress = new Vector<PlayerSessionSave.Stress>();
        stress.add(new PlayerSessionSave.Stress("R1_CATALOG_PART_0", 1.25, 12, true, 9));
        PlayerSessionSave save = create(history, sources, stress);
        String encoded = save.encode();
        history.clear(); sources.clear(); stress.clear();
        save.getHistory().clear(); save.getSources().clear(); save.getStress().clear();
        check(encoded.equals(save.encode()) && save.getHistory().size() == rows.length &&
            save.getSources().size() == 2 && save.getStress().size() == 1,
            "constructor and getters do not expose mutable vector storage");
        PlayerSessionSave restored = PlayerSessionSave.parse(encoded);
        for (int i = 0; i < rows.length; i++) {
            PlayerSessionSave.Operation operation = restored.getHistory().get(i);
            check(operation.kind.equals(rows[i][0]) && operation.owner.equals(rows[i][1]) &&
                operation.argument.equals(rows[i][2]) && operation.result.equals(rows[i][3]),
                "ordered semantic operation survives: " + rows[i][0]);
        }
        check(restored.getStress().get(0).failed && restored.getStress().get(0).failureTime == 9 &&
            restored.getStress().get(0).damage == 1.25 && Double.isNaN(restored.getSources().get(1).limitAmps),
            "failed damage and unlimited-source marker survive");
    }

    private static void verifyInvalidFiles() {
        final String valid = crossEnvironmentFixture();
        for (int i = 0; i < valid.length(); i++) {
            final String truncated = valid.substring(0, i);
            reject(new Attempt() { public void run() { PlayerSessionSave.parse(truncated); } },
                "every truncated prefix rejects");
        }
        for (final String broken : new String[] {
                valid + "x", valid + "END\n", valid.replace("tsj-session/1", "tsj-session/0"),
                valid.replace("tsj-session-model/1", "tsj-session-model/2"),
                valid.replace("restart\n", "exact\n"), valid.replace("tsj-alpha/4", "tsj-alpha/3"),
                valid.replace("47:", "047:"), valid.replace("47:", "-1:"),
                valid.replace("47:", "2147483648:"), valid.replace("47:", "48:"),
                valid.replace("HISTORY\n1\n", "HISTORY\n01\n"),
                valid.replace("HISTORY\n1\n", "HISTORY\n8193\n"),
                valid.replace("SOURCES\n1\n", "SOURCES\n97\n"),
                valid.replace("STRESS\n1\n", "STRESS\n97\n"),
                valid.replace("5:RESET", "5:PROOF"), valid.replace("5:RESET", "5:NODES"),
                valid.replace("5:RESET\n0:\n", "5:RESET\n1:x\n"),
                valid.replace("9:CONNECTED", "9:connected"),
                valid.replace("16:3fd0000000000000", "16:7ff0000000000000"),
                valid.replace("16:3fd0000000000000", "16:7ff8000000000000"),
                valid.replace("16:3fd0000000000000", "16:bfd0000000000000"),
                valid.replace("16:3fd0000000000000", "17:03fd0000000000000"),
                valid.replace("16:3fd0000000000000", "16:3FD0000000000000"),
                valid.replace("16:3fd0000000000000", "1:0"),
                valid.replace("16:3fe0000000000000", "16:8000000000000000"),
                valid.replace("7:HEALTHY", "6:FAILED"),
                valid.replace("7:HEALTHY\n4:NONE", "7:HEALTHY\n1:0"),
                valid.replace("STRESS\n1\n", "SOURCES\n0\nSTRESS\n1\n") })
            reject(new Attempt() { public void run() { PlayerSessionSave.parse(broken); } },
                "malformed, unsupported or contradictory file rejects");
        final String sourceRow = "4:MAIN\n9:CONNECTED\n16:3fd0000000000000\n";
        final String duplicateSource = valid.replace("SOURCES\n1\n" + sourceRow,
            "SOURCES\n2\n" + sourceRow + sourceRow);
        reject(new Attempt() { public void run() { PlayerSessionSave.parse(duplicateSource); } }, "duplicate source rejects");
        final String stressRow = "2:R1\n16:3fe0000000000000\n16:4010000000000000\n7:HEALTHY\n4:NONE\n";
        final String duplicateStress = valid.replace("STRESS\n1\n" + stressRow,
            "STRESS\n2\n" + stressRow + stressRow);
        reject(new Attempt() { public void run() { PlayerSessionSave.parse(duplicateStress); } }, "duplicate stress owner rejects");
    }

    private static void verifyInvalidRecords() {
        for (final String[] row : new String[][] {
                {"REMOVE", "R1", "", ""}, {"INSTALL", "R1", "P1", "extra"},
                {"ACQUIRE", "R1", "CATALOG", ""}, {"CATALOG", "", "CATALOG", "P1"},
                {"LEAD", "R1", "R1.1", "OFF"}, {"GRAPH_REMOVE", "R1", "P1", ""},
                {"GRAPH_RESTORE", "R1", "", "P1"}, {"INPUT", "CONTROL_NODE_1", "", ""},
                {"SOURCE", "MAIN", "CONNECTED", "Infinity"}, {"SOURCE", "MAIN", "CONNECTED", "NaN"},
                {"SOURCE", "MAIN", "CONNECTED", "0"}, {"SOURCE", "MAIN", "CONNECTED", "-0.25"},
                {"SOURCE", "MAIN", "CONNECTED", "1e309"}, {"SOURCE", "MAIN", "CONNECTED", "1e-999"},
                {"SOURCE", "MAIN", "CONNECTED", "1000.0"}, {"SOURCE", "MAIN", "CONNECTED", "0.0001"},
                {"SOURCE", "MAIN", "CONNECTED", " 0.25"}, {"SOURCE", "MAIN", "CONNECTED", "0x1p0"},
                {"SOURCE", "MAIN", "CONNECTED", "0.25f"}, {"RESET", "R1", "", ""},
                {"UNDO", "", "", ""}, {"REMOVE", "R 1", "P1", ""},
                {"REMOVE", "R1\nP1", "P1", ""}, {"REMOVE", "R1", null, ""} })
            reject(new Attempt() { public void run() {
                new PlayerSessionSave.Operation(row[0], row[1], row[2], row[3]);
            } }, "invalid operation contract rejects");
        for (final double invalid : new double[] {0, -0.0, -.25, .0001, 1000, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            reject(new Attempt() { public void run() { new PlayerSessionSave.Source("MAIN", true, invalid); } },
                "invalid source limit rejects");
        for (final double invalid : new double[] {-1, -0.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            reject(new Attempt() { public void run() { new PlayerSessionSave.Stress("R1", invalid, 1, false, Double.NaN); } },
                "invalid damage rejects");
            reject(new Attempt() { public void run() { new PlayerSessionSave.Stress("R1", 0, invalid, false, Double.NaN); } },
                "invalid service time rejects");
        }
        reject(new Attempt() { public void run() { new PlayerSessionSave.Stress("R1", 1, 3, false, Double.NaN); } }, "unfailed excess damage rejects");
        reject(new Attempt() { public void run() { new PlayerSessionSave.Stress("R1", .5, 3, true, 1); } }, "failed insufficient damage rejects");
        reject(new Attempt() { public void run() { new PlayerSessionSave.Stress("R1", 1, 3, true, 4); } }, "future failure rejects");
        reject(new Attempt() { public void run() { new PlayerSessionSave.Stress("R1", 1, 3, true, Double.NaN); } }, "failed absent time rejects");
        reject(new Attempt() { public void run() { PlayerSessionSave.parse(null); } }, "null file rejects");
    }

    private static void verifyFuses() {
        final String valid = crossEnvironmentFixture();
        final String row = "2:F1\n16:3fc0000000000000\n6:INTACT\n";
        for (final String broken : new String[] {
                valid.replace("FUSES\n1\n" + row, ""),
                valid.replace("FUSES\n1\n", "FUSES\n0\n"),
                valid.replace("FUSES\n1\n", "FUSES\n01\n"),
                valid.replace("FUSES\n1\n", "FUSES\n97\n"),
                valid.replace("FUSES\n1\n", "FUSES\n2147483648\n"),
                valid.replace("FUSES\n1\n" + row, "FUSES\n2\n" + row + row),
                valid.replace("FUSES\n1\n" + row, "FUSES\n1\n2:F1\n"),
                valid.replace("FUSES\n1\n" + row, "FUSES\n1\n2:F1\n16:3fc0000000000000\n"),
                valid.replace("16:3fc0000000000000", "16:7ff0000000000000"),
                valid.replace("16:3fc0000000000000", "16:7ff8000000000000"),
                valid.replace("16:3fc0000000000000", "16:bfc0000000000000"),
                valid.replace("16:3fc0000000000000", "16:8000000000000000"),
                valid.replace("16:3fc0000000000000", "17:13fc0000000000000"),
                valid.replace("16:3fc0000000000000", "4:NONE"),
                valid.replace("6:INTACT", "6:FAILED"),
                valid.replace("6:INTACT", "6:intact") })
            reject(new Attempt() { public void run() { PlayerSessionSave.parse(broken); } },
                "missing, malformed, duplicate or nonfinite fuse state rejects");
        for (final double invalid : new double[] {-1, -0.0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            reject(new Attempt() { public void run() { new PlayerSessionSave.Fuse("F1", invalid, false); } },
                "invalid fuse heat rejects at construction");
        final Vector<PlayerSessionSave.Fuse> fuses = new Vector<PlayerSessionSave.Fuse>();
        for (int i = 0; i < PlayerSessionSave.MAX_PARTS; i++)
            fuses.add(new PlayerSessionSave.Fuse("FUSE_" + i, i == 0 ? 0 : .125, i % 2 == 1));
        PlayerSessionSave save = create(new Vector<PlayerSessionSave.Operation>(),
            new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>(), fuses);
        String encoded = save.encode();
        check(PlayerSessionSave.parse(encoded).getFuses().size() == PlayerSessionSave.MAX_PARTS &&
            PlayerSessionSave.parse(encoded).getFuses().get(1).blown &&
            PlayerSessionSave.parse(encoded).getFuses().get(0).heat == 0,
            "maximum fuse count, zero heat and blown state round trip");
        fuses.clear(); save.getFuses().clear();
        check(encoded.equals(save.encode()) && save.getFuses().size() == PlayerSessionSave.MAX_PARTS,
            "fuse vectors are copied on input and output");
        fuses.addAll(save.getFuses()); fuses.add(new PlayerSessionSave.Fuse("ONE_TOO_MANY", 0, false));
        reject(new Attempt() { public void run() {
            create(new Vector<PlayerSessionSave.Operation>(), new Vector<PlayerSessionSave.Source>(),
                new Vector<PlayerSessionSave.Stress>(), fuses);
        } }, "excessive fuse count rejects");
        fuses.clear(); fuses.add(new PlayerSessionSave.Fuse("F1", 0, false)); fuses.add(fuses.get(0));
        reject(new Attempt() { public void run() {
            create(new Vector<PlayerSessionSave.Operation>(), new Vector<PlayerSessionSave.Source>(),
                new Vector<PlayerSessionSave.Stress>(), fuses);
        } }, "duplicate fuse constructor identity rejects");
        fuses.clear(); fuses.add(null);
        reject(new Attempt() { public void run() {
            create(new Vector<PlayerSessionSave.Operation>(), new Vector<PlayerSessionSave.Source>(),
                new Vector<PlayerSessionSave.Stress>(), fuses);
        } }, "missing fuse constructor entry rejects");
        PlayerSessionSave empty = create(new Vector<PlayerSessionSave.Operation>(),
            new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>());
        check(empty.encode().endsWith("FUSES\n0\nEND\n") &&
            PlayerSessionSave.parse(empty.encode()).getFuses().isEmpty(), "empty fuse section remains explicit");
    }

    private static void verifyBounds() {
        final Vector<PlayerSessionSave.Operation> operations = new Vector<PlayerSessionSave.Operation>();
        for (int i = 0; i < PlayerSessionSave.MAX_OPERATIONS; i++)
            operations.add(new PlayerSessionSave.Operation("RESET", "", "", ""));
        PlayerSessionSave bound = create(operations, new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>());
        check(PlayerSessionSave.parse(bound.encode()).getHistory().size() == PlayerSessionSave.MAX_OPERATIONS,
            "maximum history count is supported");
        operations.add(new PlayerSessionSave.Operation("RESET", "", "", ""));
        reject(new Attempt() { public void run() { create(operations, new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>()); } },
            "excessive history rejects");
        final Vector<PlayerSessionSave.Source> sources = new Vector<PlayerSessionSave.Source>();
        final Vector<PlayerSessionSave.Stress> stress = new Vector<PlayerSessionSave.Stress>();
        for (int i = 0; i < PlayerSessionSave.MAX_PARTS; i++) {
            sources.add(new PlayerSessionSave.Source("SOURCE_" + i, false, Double.NaN));
            stress.add(new PlayerSessionSave.Stress("PART_" + i, 0, 0, false, Double.NaN));
        }
        check(PlayerSessionSave.parse(create(new Vector<PlayerSessionSave.Operation>(), sources, stress).encode())
            .getStress().size() == PlayerSessionSave.MAX_PARTS, "maximum part/source count is supported");
        stress.add(new PlayerSessionSave.Stress("ONE_TOO_MANY", 0, 0, false, Double.NaN));
        reject(new Attempt() { public void run() { create(new Vector<PlayerSessionSave.Operation>(), sources, stress); } }, "excessive stress count rejects");
        stress.remove(stress.size() - 1);
        sources.add(new PlayerSessionSave.Source("ONE_TOO_MANY", false, Double.NaN));
        reject(new Attempt() { public void run() { create(new Vector<PlayerSessionSave.Operation>(), sources, stress); } }, "excessive source count rejects");
        final String tooLarge = repeated('x', PlayerSessionSave.MAX_CHARACTERS + 1);
        reject(new Attempt() { public void run() { PlayerSessionSave.parse(tooLarge); } }, "artifact size rejects before parsing");
        final String canonical = repeated('x', PlayerSessionSave.MAX_CANONICAL_TEXT);
        reject(new Attempt() { public void run() {
            new PlayerSessionSave(fixture().replay, "build", canonical, canonical,
                new Vector<PlayerSessionSave.Operation>(), new Vector<PlayerSessionSave.Source>(),
                new Vector<PlayerSessionSave.Stress>(), new Vector<PlayerSessionSave.Fuse>());
        } }, "aggregate size rejects even when individual fields fit");
        final String longId = repeated('x', PlayerSessionSave.MAX_IDENTIFIER + 1);
        reject(new Attempt() { public void run() { new PlayerSessionSave.Operation("GRAPH_REMOVE", longId, "", ""); } }, "identifier size rejects");
        final Vector<PlayerSessionSave.Operation> missing = new Vector<PlayerSessionSave.Operation>(); missing.add(null);
        reject(new Attempt() { public void run() { create(missing, new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>()); } }, "null history entry rejects");
    }

    private static void verifyHistoryPages() {
        // Total, latest offset and latest population are independent boundary expectations.
        for (int[] boundary : new int[][] {{0, 0, 0}, {1, 0, 1}, {49, 0, 49}, {50, 0, 50},
                {51, 50, 1}, {99, 50, 49}, {100, 50, 50}, {101, 100, 1},
                {8192, 8150, 42}, {8193, 8150, 43}}) {
            int total = boundary[0];
            final PlayerSessionHistory history = new PlayerSessionHistory();
            Vector<PlayerSessionSave.Operation> original = new Vector<PlayerSessionSave.Operation>();
            for (int i = 0; i < total; i++)
                original.add(new PlayerSessionSave.Operation("GRAPH_REMOVE", "PART_" + i, "", ""));
            history.restore(original);
            original.clear();
            check(history.size() == total, "history count is independent of restored vector storage");
            int latest = boundary[1];
            PlayerSessionHistory.Page page = history.page(-1);
            check(page.offset == latest && page.operations.size() == boundary[2] &&
                page.total == total && page.nextOffset() == -1,
                "latest page uses independently expected boundary for " + total);
            int seen = 0;
            for (int offset = 0; offset <= latest; offset += 50) {
                page = history.page(offset);
                int expectedCount = Math.min(50, total - offset);
                check(page.offset == offset && page.total == total && page.operations.size() == expectedCount,
                    "fixed chronological page contains at most 50 entries");
                check(page.previousOffset() == (offset == 0 ? -1 : offset - 50) &&
                    page.nextOffset() == (offset < latest ? offset + 50 : -1), "navigation has exact boundaries");
                for (int i = 0; i < expectedCount; i++) {
                    check(page.operations.get(i).owner.equals("PART_" + seen), "all entries are ordered, once each");
                    seen++;
                }
                page.operations.clear();
            }
            check(seen == total && history.size() == total && history.operations().size() == total,
                "paging never truncates or mutates the complete journal, including above save cap");
            Vector<PlayerSessionSave.Operation> journal = history.operations();
            for (int i = 0; i < total; i++)
                check(journal.get(i).owner.equals("PART_" + i), "complete save journal retains original order");
            if (total <= 8192) {
                Vector<PlayerSessionSave.Operation> complete = history.operations();
                PlayerSessionSave saved = create(complete, new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>());
                String bytes = saved.encode();
                history.page(-1).operations.clear(); history.operations().clear();
                PlayerSessionSave afterPaging = create(history.operations(), new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>());
                check(bytes.equals(afterPaging.encode()) && PlayerSessionSave.parse(bytes).getHistory().size() == total,
                    "bounded presentation preserves complete exact save bytes");
            } else {
                reject(new Attempt() { public void run() {
                    create(history.operations(), new Vector<PlayerSessionSave.Source>(), new Vector<PlayerSessionSave.Stress>());
                } }, "above-cap live history still rejects saving instead of silently truncating");
            }
            for (final int invalid : new int[] {-2, 1, latest + 50, Integer.MAX_VALUE})
                reject(new Attempt() { public void run() { history.page(invalid); } }, "invalid page offset rejects");
        }
    }

    private static void verifyHistoryPageLeases() {
        CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
        CircuitElm.sim = sim;
        sim.generationCoordinator = new GenerationCoordinator(sim);
        PlayerLaunchRequest request = new PlayerLaunchRequest("LED_INDICATOR", "0", "EASY");
        GeneratedBoardInstance owner = request.generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
        installHistoryOwner(sim, owner);
        final PlayerSessionController controller = new PlayerSessionController(sim);
        controller.session.adopt(owner, request);
        controller.session.enter(controller.session.token(), PlayerSession.Screen.MENU);
        final int token = controller.session.token();
        final int view = controller.openView();
        PlayerSessionHistory history = owner.getPhysicalBoardRuntime().getSessionHistory();
        history.start(); history.record(sim, "RESET", "", "", "");
        check(controller.historyPage(token, view, -1).operations.size() == 1, "live menu lease reads current history");
        controller.closeView(view);
        rejectClosedHistory(controller, token, view, "closed-view callback rejects");
        int reopened = controller.openView();
        rejectClosedHistory(controller, token, view, "reopening cannot revive the old callback");
        check(controller.historyPage(token, reopened, 0).total == 1, "new view reads full retained history");
        controller.session.enter(token, PlayerSession.Screen.WORKBENCH);
        rejectClosedHistory(controller, token, reopened, "old session token rejects after leaving menu");
        rejectClosedHistory(controller, controller.session.token(), reopened, "history is unavailable outside menu");
        controller.session.enter(controller.session.token(), PlayerSession.Screen.MENU);
        int current = controller.session.token();
        sim.generatedBoardInstance = null;
        rejectClosedHistory(controller, current, reopened, "displaced board owner rejects");
        sim.generatedBoardInstance = owner;
        GeneratedBoardInstance successor = request.generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
        installHistoryOwner(sim, successor);
        controller.session.adopt(successor, request);
        controller.session.enter(controller.session.token(), PlayerSession.Screen.MENU);
        rejectClosedHistory(controller, current, reopened, "old callback cannot read successor history");
        check(history.size() == 1 && controller.historyPage(controller.session.token(), controller.openView(), -1).total == 0,
            "successor history is independent and predecessor journal remains intact");
    }

    /** Native fixture uses the real capability installation before challenge validation. */
    private static void installHistoryOwner(CirSim sim, GeneratedBoardInstance owner) {
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = owner;
        sim.boardModificationController = new BoardModificationController(sim, owner);
        owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner,
            sim.boardModificationController, 0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        sim.generatedChallengeController = new GeneratedChallengeController(sim, owner);
    }

    private static void rejectClosedHistory(PlayerSessionController controller, int token, int view, String message) {
        assertions++;
        try { controller.historyPage(token, view, -1); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }

    private static void verifyInventoryPopulation() {
        final Vector<PlayerSessionSave.Operation> operations = new Vector<PlayerSessionSave.Operation>();
        for (int i = 0; i < 93; i++) {
            operations.add(new PlayerSessionSave.Operation(i % 2 == 0 ? "ACQUIRE" : "CATALOG",
                "R1", "R_CATALOG_1000", "STOCK_" + i));
            operations.add(new PlayerSessionSave.Operation("RESET", "", "", ""));
            operations.add(new PlayerSessionSave.Operation("REMOVE", "R1", "STOCK_" + i, ""));
        }
        PlayerSessionState.validateInventoryPopulation(3, operations);
        check(operations.size() == 279, "three originals plus 93 acquisitions support 96 retained parts");
        operations.add(new PlayerSessionSave.Operation("ACQUIRE", "R1", "R_CATALOG_1000", "STOCK_93"));
        reject(new Attempt() { public void run() { PlayerSessionState.validateInventoryPopulation(3, operations); } },
            "97 retained parts reject despite intervening reset and removal operations");
        operations.clear();
        PlayerSessionState.validateInventoryPopulation(96, operations);
        check(operations.isEmpty(), "96 original parts need no acquisitions");
        reject(new Attempt() { public void run() { PlayerSessionState.validateInventoryPopulation(97, operations); } },
            "97 original parts reject");
        reject(new Attempt() { public void run() { PlayerSessionState.validateInventoryPopulation(-1, operations); } },
            "negative original count rejects");
    }

    private static PlayerSessionSave create(Vector<PlayerSessionSave.Operation> history,
            Vector<PlayerSessionSave.Source> sources, Vector<PlayerSessionSave.Stress> stress) {
        return create(history, sources, stress, new Vector<PlayerSessionSave.Fuse>());
    }
    private static PlayerSessionSave create(Vector<PlayerSessionSave.Operation> history,
            Vector<PlayerSessionSave.Source> sources, Vector<PlayerSessionSave.Stress> stress,
            Vector<PlayerSessionSave.Fuse> fuses) {
        return new PlayerSessionSave("tsj-alpha/4/EASY/LED_INDICATOR/0", "build", "realization", "state", history, sources, stress, fuses);
    }
    private static String repeated(char character, int count) {
        char[] chars = new char[count]; java.util.Arrays.fill(chars, character); return new String(chars);
    }
    private static void reject(Attempt attempt, String message) {
        assertions++;
        try { attempt.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
