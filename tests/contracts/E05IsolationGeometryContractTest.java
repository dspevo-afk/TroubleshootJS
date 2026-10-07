package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Vector;

/** Independent drawing-unit isolation oracles; no electrical or certification claim. */
public final class E05IsolationGeometryContractTest {
    private static int checks;
    private static final PhysicalPackage TRANSFORMER = PhysicalPackages.E05_ISOLATION_TRANSFORMER_4;
    private static final String[] TERMINALS = { "P1", "P2", "S1", "S2" };
    private static final String[] NETS = { "P_A", "P_B", "S_A", "S_B" };
    private static final SeededPcbLayoutGenerator.AttemptObserver OBSERVER =
        new SeededPcbLayoutGenerator.AttemptObserver() { public void check(int attempt) { } };

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        Fixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board; this.layout = layout;
        }
    }

    public static void main(String[] args) throws Exception {
        immutableDeclarationsAndTerminalOwnership();
        invalidTerminalDomainsAndConnections();
        bodySpanAndCanonicalPackageAdmission();
        bothFaceCopperAndLandNegatives();
        platedAndNonPlatedDrillNegatives();
        singleFaceRoutingAndFinalPublicationGate();
        canonicalOwnersBindDeclarations();
        defaultsAndProductionWhitelistStayFixed();
        System.out.println("PASS: E05 isolation geometry contracts assertions=" + checks);
    }

    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void reject(String expectedMessage, Runnable action) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException failure) {
            if (failure.getMessage() != null && failure.getMessage().contains(expectedMessage)) return;
            throw new AssertionError("Wrong declaration rejection for " + expectedMessage, failure);
        }
        catch (IllegalStateException failure) {
            if (failure.getMessage() != null && failure.getMessage().contains(expectedMessage)) return;
            throw new AssertionError("Wrong geometry rejection for " + expectedMessage, failure);
        }
        throw new AssertionError("Expected rejection: " + expectedMessage);
    }

    private static Vector<String> strings(String... values) {
        Vector<String> result = new Vector<String>();
        for (String value : values) result.add(value);
        return result;
    }

    private static Vector<PcbPlacementConstraints.TerminalDomain> domains(String primary, String secondary) {
        Vector<PcbPlacementConstraints.TerminalDomain> result = new Vector<PcbPlacementConstraints.TerminalDomain>();
        result.add(new PcbPlacementConstraints.TerminalDomain("P1", primary));
        result.add(new PcbPlacementConstraints.TerminalDomain("P2", primary));
        result.add(new PcbPlacementConstraints.TerminalDomain("S1", secondary));
        result.add(new PcbPlacementConstraints.TerminalDomain("S2", secondary));
        return result;
    }

    private static TroubleshootBoard board(PhysicalPackage physical, boolean connectors, boolean joinedReturns) {
        TroubleshootBoard board = new TroubleshootBoard("E05_ISOLATION_GEOMETRY");
        for (String net : NETS) board.addNet(new BoardNet(net));
        BoardComponent transformer = new BoardComponent("T1", "TRANSFORMER", physical);
        board.addComponent(transformer);
        for (int index = 0; index < TERMINALS.length; index++)
            board.addPad(new BoardPad("T1." + TERMINALS[index], "T1", TERMINALS[index],
                joinedReturns && index == 3 ? "P_B" : NETS[index]));
        if (connectors) {
            for (int side = 0; side < 2; side++) {
                String id = side == 0 ? "JPRI" : "JSEC";
                board.addComponent(new BoardComponent(id, "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
                board.addPad(new BoardPad(id + ".1", id, "1", NETS[side * 2]));
                board.addPad(new BoardPad(id + ".2", id, "2", NETS[side * 2 + 1]));
            }
        }
        board.validate();
        return board;
    }

    private static PcbPlacementConstraints constraints(TroubleshootBoard board,
            Vector<PcbPlacementConstraints.TerminalDomain> domains, String primary, String secondary,
            boolean withBarrier) {
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("T1", "power", "Power", primary,
            PcbPlacementConstraints.Anchor.NONE, 20, domains));
        if (board.getComponent("JPRI") != null) {
            parts.add(new PcbPlacementConstraints.Part("JPRI", "input", "Input", primary,
                PcbPlacementConstraints.Anchor.NONE, 20));
            parts.add(new PcbPlacementConstraints.Part("JSEC", "output", "Output", secondary,
                PcbPlacementConstraints.Anchor.NONE, 20));
        }
        Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
        if (withBarrier) barriers.add(new PcbPlacementConstraints.Barrier(primary, secondary, 120));
        return new PcbPlacementConstraints(parts, barriers, PcbCopperLayer.BOTTOM);
    }

    private static PcbBoardLayout blankLayout() {
        return new PcbBoardLayout(1400, 650, new Rectangle(100, 100, 1000, 400),
            new Rectangle(1180, 120, 180, 400));
    }

    private static void place(PcbBoardLayout layout, BoardComponent component, int x, int y) {
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, x, y);
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static Fixture fixture(PhysicalPackage physical,
            Vector<PcbPlacementConstraints.TerminalDomain> domains, String primary, String secondary,
            boolean connectors) {
        TroubleshootBoard board = board(physical, connectors, false);
        board.setPlacementConstraints(constraints(board, domains, primary, secondary, true));
        PcbBoardLayout layout = blankLayout();
        place(layout, board.getComponent("T1"), 400, 200);
        if (connectors) {
            place(layout, board.getComponent("JPRI"), 200, 250);
            place(layout, board.getComponent("JSEC"), 900, 250);
        }
        return new Fixture(board, layout);
    }

    private static Fixture fixture(boolean connectors) {
        return fixture(TRANSFORMER, domains("PRIMARY", "SECONDARY"), "PRIMARY", "SECONDARY", connectors);
    }

    private static void immutableDeclarationsAndTerminalOwnership() {
        Vector<PcbPlacementConstraints.TerminalDomain> supplied = domains("PRIMARY", "SECONDARY");
        PcbPlacementConstraints.Part part = new PcbPlacementConstraints.Part("T1", "power", "Power", "PRIMARY",
            PcbPlacementConstraints.Anchor.NONE, 20, supplied);
        supplied.clear();
        supplied.add(new PcbPlacementConstraints.TerminalDomain("P1", "FORGED"));
        part.getTerminalDomains().clear();
        check("PRIMARY".equals(part.terminalDomain("P1")) && "SECONDARY".equals(part.terminalDomain("S2")),
            "caller mutation cannot retarget retained terminal domains");
        check(part.getTerminalDomains().size() == 4, "declarations retain all four terminals");

        Fixture fixture = fixture(false);
        check(TRANSFORMER.getTerminalIds().equals(strings("P1", "P2", "S1", "S2")),
            "canonical terminal order is P1,P2,S1,S2");
        int[][] expected = { {430,260}, {430,350}, {790,260}, {790,350} };
        for (int index = 0; index < TERMINALS.length; index++) {
            String id = "T1." + TERMINALS[index];
            PcbPadPlacement pad = fixture.layout.getPad(id);
            check(pad.getX() == expected[index][0] && pad.getY() == expected[index][1],
                "independent package terminal center " + id);
            check((index < 2 ? "PRIMARY" : "SECONDARY").equals(
                fixture.board.getPlacementConstraints().domainForPad(fixture.board, id)),
                "each pad consumes its terminal domain " + id);
            check(fixture.board.getPad(id).getNetId().equals(NETS[index]), "terminal net identity " + id);
        }
        PcbTwoLayerRules rules = new PcbTwoLayerRules(fixture.board, fixture.layout);
        check(rules.permits("P_A", new Rectangle(500,450,10,10)), "PRIMARY copper stays left of 550");
        check(!rules.permits("P_A", new Rectangle(551,450,10,10)), "independent 550..670 corridor excludes PRIMARY");
        check(rules.permits("S_A", new Rectangle(700,450,10,10)), "SECONDARY copper stays right of 670");
        check(!rules.permits("S_A", new Rectangle(650,450,10,10)), "corridor excludes SECONDARY");
        rules.validate(fixture.layout);
        fixture.layout.validateRoutingGeometry(fixture.board);
        checks += 2;
    }

    private static void invalidTerminalDomainsAndConnections() {
        reject("Duplicate terminal", new Runnable() { public void run() {
            Vector<PcbPlacementConstraints.TerminalDomain> values = domains("PRIMARY", "SECONDARY");
            values.add(new PcbPlacementConstraints.TerminalDomain("P1", "PRIMARY"));
            new PcbPlacementConstraints.Part("T1", "power", "Power", "PRIMARY",
                PcbPlacementConstraints.Anchor.NONE, 20, values);
        }});
        reject("Incomplete terminal", new Runnable() { public void run() {
            Vector<PcbPlacementConstraints.TerminalDomain> values = domains("PRIMARY", "SECONDARY"); values.remove(0);
            fixture(TRANSFORMER, values, "PRIMARY", "SECONDARY", false);
        }});
        reject("Foreign terminal", new Runnable() { public void run() {
            Vector<PcbPlacementConstraints.TerminalDomain> values = domains("PRIMARY", "SECONDARY");
            values.set(0, new PcbPlacementConstraints.TerminalDomain("FOREIGN", "PRIMARY"));
            fixture(TRANSFORMER, values, "PRIMARY", "SECONDARY", false);
        }});
        reject("conflicting domains", new Runnable() { public void run() {
            Vector<PcbPlacementConstraints.TerminalDomain> values = domains("PRIMARY", "SECONDARY");
            values.set(1, new PcbPlacementConstraints.TerminalDomain("P2", "SECONDARY"));
            fixture(TRANSFORMER, values, "PRIMARY", "SECONDARY", false);
        }});
        reject("Mixed-domain package", new Runnable() { public void run() {
            Vector<PcbPlacementConstraints.TerminalDomain> values = domains("PRIMARY", "SECONDARY");
            values.set(3, new PcbPlacementConstraints.TerminalDomain("S2", "THIRD"));
            fixture(TRANSFORMER, values, "PRIMARY", "SECONDARY", false);
        }});
        reject("requires two terminal domains", new Runnable() { public void run() {
            fixture(TRANSFORMER, domains("PRIMARY", "PRIMARY"), "PRIMARY", "SECONDARY", false);
        }});
        reject("Mixed-domain package", new Runnable() { public void run() {
            TroubleshootBoard board = board(TRANSFORMER, false, false);
            board.setPlacementConstraints(constraints(board, domains("PRIMARY", "SECONDARY"), "PRIMARY", "SECONDARY", false));
        }});
        reject("conductive net crosses", new Runnable() { public void run() {
            TroubleshootBoard board = board(TRANSFORMER, false, true);
            board.setPlacementConstraints(constraints(board, domains("PRIMARY", "SECONDARY"), "PRIMARY", "SECONDARY", true));
        }});
        reject("Package connectivity crosses", new Runnable() { public void run() {
            PhysicalPackage shorted = new PhysicalPackage("SHORTED_TRANSFORMER", strings(TERMINALS),
                strings("P1=S1"), false, TRANSFORMER.getGeometry());
            fixture(shorted, domains("PRIMARY", "SECONDARY"), "PRIMARY", "SECONDARY", false);
        }});
    }

    private static PhysicalPackage changedBody(int firstWidth, int secondX, Rectangle span) {
        PhysicalPackageGeometry original = TRANSFORMER.getGeometry();
        PhysicalPackageGeometry changed = new PhysicalPackageGeometry(original.getWidth(), original.getHeight(),
            original.getTerminals(), original.getBodyBounds(), original.getBodyKeepOut(), original.getRoutingCourtyard(),
            original.getSelectionEnvelope(), original.getDragEnvelope()).withIsolationBody(
                new PhysicalPackageGeometry.IsolationBody(strings("P1", "P2"), strings("S1", "S2"),
                    new Rectangle(10,10,firstWidth,200), new Rectangle(secondX,10,410-secondX,200), span));
        return new PhysicalPackage(TRANSFORMER.getId(), strings(TERMINALS), new Vector<String>(), false, changed);
    }

    private static void bodySpanAndCanonicalPackageAdmission() {
        Vector<String> primary = strings("P1", "P2"), secondary = strings("S1", "S2");
        Rectangle first = new Rectangle(10,10,105,200), second = new Rectangle(305,10,105,200);
        Rectangle span = new Rectangle(90,25,240,165);
        PhysicalPackageGeometry.IsolationBody declaration = new PhysicalPackageGeometry.IsolationBody(primary,secondary,first,second,span);
        String canonical = declaration.canonical();
        primary.clear(); secondary.clear(); first.x = 0; second.width = 1; span.y = 0;
        check(canonical.equals(declaration.canonical()), "terminal groups and body rectangles are immutable");
        reject("omits terminal metal", new Runnable() { public void run() {
            changedBody(70,305,new Rectangle(90,25,240,165));
        }});
        final Fixture incompleteSpan = fixture(changedBody(105,305,new Rectangle(90,26,240,164)),
            domains("PRIMARY", "SECONDARY"), "PRIMARY", "SECONDARY", false);
        reject("outside its declared insulating span", new Runnable() { public void run() {
            new PcbTwoLayerRules(incompleteSpan.board,incompleteSpan.layout).validate(incompleteSpan.layout);
        }});
        reject("Foreign PCB footprint geometry", new Runnable() { public void run() {
            BoardComponent component = new BoardComponent("T1", "TRANSFORMER", TRANSFORMER);
            PcbFootprint.fromPhysicalPackage(component,400,200,changedBody(105,305,new Rectangle(90,25,240,165)).getGeometry());
        }});
        reject("Conflicting PCB package definition", new Runnable() { public void run() {
            StandardPcbFootprintProviders.createRegistry().getProvider(changedBody(100,305,new Rectangle(90,25,240,165)));
        }});
        check(StandardPcbFootprintProviders.createRegistry().getProvider(TRANSFORMER) != null,
            "concrete transformer has its package-backed footprint provider");
        check(StandardPhysicalPartRenderProviders.createRegistry().hasProvider(TRANSFORMER),
            "concrete transformer has an installed/loose rendering provider");
    }

    private static PcbTraceGeometry crossing(String id, PcbCopperLayer layer) {
        return new PcbTraceGeometry(id,"P_A",null,null,layer,PcbCopperAccess.Exposure.EXPOSED,
            new int[] {500,700},new int[] {450,450});
    }

    private static void bothFaceCopperAndLandNegatives() {
        for (final PcbCopperLayer layer : PcbCopperLayer.values()) {
            final Fixture fixture = fixture(false);
            fixture.layout.addTrace(crossing("crossing-" + layer,layer));
            reject("copper crosses a physical domain barrier", new Runnable() { public void run() {
                fixture.layout.validateRoutingGeometry(fixture.board);
            }});
            final Fixture edge = fixture(false);
            edge.layout.addTrace(new PcbTraceGeometry("stroke-edge-" + layer,"P_A",null,null,layer,
                PcbCopperAccess.Exposure.EXPOSED,new int[] {547,547},new int[] {440,460}));
            reject("copper crosses a physical domain barrier", new Runnable() { public void run() {
                new PcbTwoLayerRules(edge.board,edge.layout).validate(edge.layout);
            }});
        }
        final Fixture wrongSide = fixture(false);
        wrongSide.layout.addTrace(new PcbTraceGeometry("reappeared-primary","P_A",null,null,PcbCopperLayer.BOTTOM,
            PcbCopperAccess.Exposure.EXPOSED,new int[] {720,760},new int[] {450,450}));
        reject("copper crosses a physical domain barrier", new Runnable() { public void run() {
            new PcbTwoLayerRules(wrongSide.board,wrongSide.layout).validate(wrongSide.layout);
        }});
        final Fixture healthy = fixture(false);
        final PcbBoardLayout altered = blankLayout();
        for (PcbComponentPlacement part : healthy.layout.getComponents()) altered.addComponent(part);
        for (PcbPadPlacement pad : healthy.layout.getPads()) {
            if ("T1.P1".equals(pad.getPadId()))
                altered.addPad(new PcbPadPlacement(pad.getPadId(),pad.getX(),pad.getY(),pad.getEscapeDx(),pad.getEscapeDy(),
                    pad.getEscapeLength(),new Rectangle(417,247,137,26),new Rectangle(415,245,141,30)));
            else altered.addPad(pad);
        }
        reject("Component land crosses", new Runnable() { public void run() {
            new PcbTwoLayerRules(healthy.board,healthy.layout).validate(altered);
        }});
        check(healthy.board.getPlacementConstraints().domainForPad(healthy.board,"T1.P1").equals("PRIMARY"),
            "bad land did not alter the logical endpoint or domain");
        final PcbBoardLayout staleLeadPlacement = blankLayout();
        staleLeadPlacement.addComponent(healthy.layout.getComponent("T1").translatedBy(100,0));
        for (PcbPadPlacement pad : healthy.layout.getPads()) staleLeadPlacement.addPad(pad);
        reject("Component lead crosses", new Runnable() { public void run() {
            new PcbTwoLayerRules(healthy.board,healthy.layout).validate(staleLeadPlacement);
        }});
    }

    private static PcbBoardHole hole(String id, PcbBoardHole.Kind kind, int x, int y, int land) {
        return new PcbBoardHole(id,kind,kind == PcbBoardHole.Kind.NON_PLATED ? null : "P_A",x,y,
            kind == PcbBoardHole.Kind.NON_PLATED ? land : 2,land,
            kind == PcbBoardHole.Kind.NON_PLATED ? null : PcbCopperLayer.TOP,
            kind == PcbBoardHole.Kind.NON_PLATED ? null : PcbCopperLayer.BOTTOM,PcbCopperAccess.Exposure.EXPOSED);
    }

    private static void platedAndNonPlatedDrillNegatives() {
        for (final PcbBoardHole.Kind kind : PcbBoardHole.Kind.values()) {
            final Fixture middle = fixture(false);
            middle.layout.addHole(hole("corridor-" + kind,kind,610,450,8));
            reject("Drill or land crosses", new Runnable() { public void run() {
                middle.layout.validateRoutingGeometry(middle.board);
            }});
            final Fixture landEdge = fixture(false);
            landEdge.layout.addHole(hole("edge-" + kind,kind,544,450,8));
            reject("Drill or land crosses", new Runnable() { public void run() {
                new PcbTwoLayerRules(landEdge.board,landEdge.layout).validate(landEdge.layout);
            }});
        }
        final Fixture underBody = fixture(false);
        underBody.layout.addHole(hole("npth-under-body",PcbBoardHole.Kind.NON_PLATED,520,300,4));
        reject("drills through a package courtyard", new Runnable() { public void run() {
            underBody.layout.validateRoutingGeometry(underBody.board);
        }});
        Fixture legal = fixture(false);
        legal.layout.addHole(hole("legal-npth",PcbBoardHole.Kind.NON_PLATED,150,450,4));
        legal.layout.validateRoutingGeometry(legal.board);
        check(legal.layout.getHoleCount() == 1 && legal.layout.getHoles().firstElement().netId == null,
            "legal developer NPTH has no invented electrical endpoint");
    }

    private static void independentCorridorOracle(PcbBoardLayout layout, int dx) {
        check(layout.getTraces().size() == 4, "both winding pairs have actual routed copper");
        for (PcbTraceGeometry trace : layout.getTraces()) {
            check(trace.getLayer() == PcbCopperLayer.BOTTOM, "single-face positive uses the actual BOTTOM router");
            check(trace.getStartPadId() != null && trace.getEndPadId() != null, "routed copper has physical terminal endpoints");
            int[] x = trace.getXPoints();
            // Fixed trace half-width 4: expectations do not call the production stroke helper.
            for (int value : x)
                check(trace.getNetId().startsWith("P_") ? value + 4 <= 550 + dx : value - 4 >= 670 + dx,
                    "independent corridor bound for " + trace.getNetId());
        }
    }

    private static void singleFaceRoutingAndFinalPublicationGate() {
        Fixture routed = fixture(true);
        PcbNetRouter.route(routed.layout,routed.board,routed.layout.getBoardOutline(),0,OBSERVER);
        independentCorridorOracle(routed.layout,0);
        routed.layout.validateRoutingGeometry(routed.board);
        checks++;
        for (final PcbCopperLayer layer : PcbCopperLayer.values()) {
            Fixture empty = fixture(true);
            PcbNetRouter.Router router = new PcbNetRouter.Router(empty.layout,empty.board,
                empty.layout.getBoardOutline(),0,OBSERVER,layer);
            check(!router.permitsLayerStep(540,450,550,450,"P_A",empty.layout.getPad("T1.P1"),empty.layout.getPad("JPRI.1")),
                "search excludes full-stroke corridor contact on " + layer);
            check(router.permitsLayerStep(500,450,510,450,"P_A",empty.layout.getPad("T1.P1"),empty.layout.getPad("JPRI.1")),
                "same search accepts a legal move on " + layer);
        }
        final Fixture finalGate = fixture(true);
        PcbNetRouter.route(finalGate.layout,finalGate.board,finalGate.layout.getBoardOutline(),0,OBSERVER);
        finalGate.layout.addTrace(crossing("injected-final-crossing",PcbCopperLayer.BOTTOM));
        reject("copper crosses a physical domain barrier", new Runnable() { public void run() {
            finalGate.layout.validateRoutingGeometry(finalGate.board);
        }});
        String carrier = routed.layout.getComponent("T1").getGeometryRealization().fingerprint();
        int oldX = routed.layout.getPad("T1.P1").getX();
        routed.layout.compactToContent(120,110,60);
        int dx = routed.layout.getPad("T1.P1").getX() - oldX;
        independentCorridorOracle(routed.layout,dx);
        routed.layout.validateRoutingGeometry(routed.board);
        check(carrier.equals(routed.layout.getComponent("T1").getGeometryRealization().fingerprint()),
            "compaction preserves physical carrier identity and both terminal domains");
        check("PRIMARY".equals(routed.board.getPlacementConstraints().domainForPad(routed.board,"T1.P2")) &&
            "SECONDARY".equals(routed.board.getPlacementConstraints().domainForPad(routed.board,"T1.S2")),
            "compaction cannot join returns or retarget pads");
    }

    private static String generationBoard(TroubleshootBoard board) throws Exception {
        Method method = GenerationDependencyContext.class.getDeclaredMethod("appendBoard",StringBuilder.class,
            TroubleshootBoard.class,BoardPhysicalSpecifications.class);
        method.setAccessible(true);
        StringBuilder out = new StringBuilder();
        method.invoke(null,out,board,new BoardPhysicalSpecifications());
        return out.toString();
    }

    private static String generationPackage(PhysicalPackage physical) throws Exception {
        Method method = GenerationDependencyContext.class.getDeclaredMethod("appendPackage",StringBuilder.class,
            String.class,PhysicalPackage.class);
        method.setAccessible(true);
        StringBuilder out = new StringBuilder(); method.invoke(null,out,"test.package",physical);
        return out.toString();
    }

    private static String admissionBoard(TroubleshootBoard board) throws Exception {
        Method method = MediumBoardNormalAdmission.class.getDeclaredMethod("boardDeclarationFingerprint",TroubleshootBoard.class);
        method.setAccessible(true); return (String)method.invoke(null,board);
    }

    private static void canonicalOwnersBindDeclarations() throws Exception {
        Fixture baseline = fixture(false);
        Vector<PcbPlacementConstraints.TerminalDomain> reversed = domains("PRIMARY", "SECONDARY");
        Collections.reverse(reversed);
        Fixture reordered = fixture(TRANSFORMER,reversed,"PRIMARY","SECONDARY",false);
        check(generationBoard(baseline.board).equals(generationBoard(reordered.board)),
            "generation canonical domains do not depend on declaration insertion order");
        check(admissionBoard(baseline.board).equals(admissionBoard(reordered.board)),
            "normal admission canonical domains do not depend on insertion order");
        Fixture exchanged = fixture(TRANSFORMER,domains("SECONDARY", "PRIMARY"),"PRIMARY","SECONDARY",false);
        check(!generationBoard(baseline.board).equals(generationBoard(exchanged.board)),
            "terminal-domain changes alone invalidate generation identity with fixed part domains/barriers");
        check(!admissionBoard(baseline.board).equals(admissionBoard(exchanged.board)),
            "terminal-domain changes alone invalidate normal admission identity with fixed part domains/barriers");
        Fixture renamed = fixture(TRANSFORMER,domains("PRIMARY|P", "SECONDARY|S"),"PRIMARY|P","SECONDARY|S",false);
        check(!generationBoard(baseline.board).equals(generationBoard(renamed.board)),
            "generation identity binds exact terminal domains");
        check(!admissionBoard(baseline.board).equals(admissionBoard(renamed.board)),
            "normal admission identity binds exact terminal domains");
        for (PhysicalPackage changed : new PhysicalPackage[] {
                changedBody(100,305,new Rectangle(90,25,240,165)),
                changedBody(105,310,new Rectangle(90,25,240,165)),
                changedBody(105,305,new Rectangle(90,26,240,164)) }) {
            Fixture altered = fixture(changed,domains("PRIMARY", "SECONDARY"),"PRIMARY","SECONDARY",false);
            check(!TRANSFORMER.isEquivalentTo(changed), "same ID/dimensions cannot erase a changed body declaration");
            check(!generationPackage(TRANSFORMER).equals(generationPackage(changed)), "generation binds body declaration");
            check(!admissionBoard(baseline.board).equals(admissionBoard(altered.board)), "normal admission binds body declaration");
            check(!baseline.layout.getComponent("T1").geometryFingerprint().equals(altered.layout.getComponent("T1").geometryFingerprint()),
                "placement binds its selected body geometry");
            check(!baseline.layout.getComponent("T1").getGeometryRealization().fingerprint().equals(
                altered.layout.getComponent("T1").getGeometryRealization().fingerprint()), "carrier binds its body geometry");
        }
    }

    private static void defaultsAndProductionWhitelistStayFixed() throws Exception {
        PcbPlacementConstraints.Part legacy = new PcbPlacementConstraints.Part("R1","circuit","Circuit","board",
            PcbPlacementConstraints.Anchor.NONE,20);
        check(legacy.getTerminalDomains().isEmpty() && "board".equals(legacy.terminalDomain("1")) &&
            "board".equals(legacy.terminalDomain("2")), "legacy declarations retain one domain and no new identity fields");
        PcbComponentPlacement resistor = PcbComponentPlacement.fromPhysicalGeometry("R1",100,100,
            PhysicalPackages.AXIAL_RESISTOR,PhysicalPackages.AXIAL_RESISTOR.getGeometry());
        check("AXIAL_RESISTOR|variant=SPAN_220|transform=IDENTITY|version=3|geometry=220x70".equals(
            resistor.getGeometryRealization().fingerprint()), "frozen pre-E05 physical carrier identity");
        check(!resistor.geometryFingerprint().contains("isolationBody="), "legacy placement emits no new body field");
        check(!generationPackage(PhysicalPackages.AXIAL_RESISTOR).contains("isolation-body"),
            "legacy package emits no new generation dependency body field");
        check("THT_SINGLE_FACE@1".equals(new PcbPlacementConstraints(new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>()).getPhysicalPolicyIdentity()), "default policy identity is unchanged");
        check("MEDIUM_BOARD@1".equals(MediumBoardPhysicalPolicy.identity()), "medium policy identity is unchanged");
        Method whitelist = PcbTwoLayerRules.class.getDeclaredMethod("isSupportedProductionPackage",PhysicalPackage.class);
        whitelist.setAccessible(true);
        check(!((Boolean)whitelist.invoke(null,TRANSFORMER)).booleanValue(), "E05 developer fixture cannot expand medium production package admission");
        check(((Boolean)whitelist.invoke(null,PhysicalPackages.AXIAL_RESISTOR)).booleanValue() &&
            ((Boolean)whitelist.invoke(null,PhysicalPackages.E04_DECISION_CONTROL_5)).booleanValue(),
            "existing concrete production packages remain in the whitelist");
    }
}
