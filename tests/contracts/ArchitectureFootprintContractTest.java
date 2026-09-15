package com.lushprojects.circuitjs1.client;

/** Current generic-package escape and multi-terminal routing regressions. */
public final class ArchitectureFootprintContractTest {
    public static void main(String[] args) throws Exception {
        int assertions = 0;
        for (int pins = 3; pins <= 6; pins++) {
            PhysicalPackage pkg = PhysicalPackages.developerConnectorForCount(pins);
            for (PhysicalPackage.GeometryVariant variant : pkg.getGeometryVariants()) {
                PhysicalPackageGeometry geometry = variant.getGeometry();
                Rectangle court = geometry.getRoutingCourtyard();
                for (PhysicalPackageGeometry.Terminal terminal : geometry.getTerminals()) {
                    Point pad = terminal.getPadCenter();
                    int x = pad.x + terminal.getEscapeDx() * (terminal.getEscapeLength() + 20);
                    int y = pad.y + terminal.getEscapeDy() * (terminal.getEscapeLength() + 20);
                    if (court.contains(x, y))
                        throw new AssertionError("Declared escape terminates inside its own body courtyard");
                    assertions++;
                }
            }
        }
        java.lang.reflect.Method canaries = ArchitectureDeveloperVerifier.class
            .getDeclaredMethod("verifyFootprintCanaries", CirSim.class);
        canaries.setAccessible(true);
        canaries.invoke(null, new Object[] { null });
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        sim.elmList = new java.util.Vector<CircuitElm>();
        PhysicalFoundationDeveloperVerifier.verify(sim);
        if (!sim.elmList.isEmpty()) throw new AssertionError("Foundation canary leaked backing elements");
        assertions += verifyCopperNegatives();
        assertions += verifyInstalledFixture(sim);
        java.lang.reflect.Method family = PcbLayoutDeveloperVerifier.class.getDeclaredMethod("verifyFamily", String.class);
        family.setAccessible(true); int failures = 0;
        for (String id : new String[] { "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR",
                "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH" }) {
            try { family.invoke(null, id); }
            catch (java.lang.reflect.InvocationTargetException failure) {
                failures++; System.err.println("ARCHITECTURE_LAYOUT_FAILURE " + id + ": " + failure.getCause());
            }
        }
        if (failures != 0) throw new AssertionError("Current layout families failed: " + failures);
        System.out.println("PASS: architecture footprint contracts assertions=" + assertions
            + " routedPackages=4 deterministicRepeats=4 familyRegressions=6");
    }

    private static int verifyInstalledFixture(CirSim sim) throws Exception {
        Class<?> type=Class.forName("com.lushprojects.circuitjs1.client.PhysicalPartRenderDeveloperVerifier$InstalledRenderNegativeFixture");
        java.lang.reflect.Method create=type.getDeclaredMethod("create",CirSim.class); create.setAccessible(true);
        Object fixture=create.invoke(null,sim);
        java.lang.reflect.Field ownerField=type.getDeclaredField("instance"), partField=type.getDeclaredField("part"), idField=type.getDeclaredField("componentId");
        ownerField.setAccessible(true);partField.setAccessible(true);idField.setAccessible(true);
        GeneratedBoardInstance owner=(GeneratedBoardInstance)ownerField.get(fixture);
        PhysicalPart<?> part=(PhysicalPart<?>)partField.get(fixture);String id=(String)idField.get(fixture);
        if (!(part instanceof PhysicalResistorPart) || owner.getPhysicalBoardRuntime().getInstalledPart(id)!=part ||
                owner.getPhysicalBoardRuntime().getWorkbenchPartsProvider(id)==null || owner.getPcbLayout()==null ||
                !(owner.getComponentBindings().getSingleElement(id) instanceof ResistorElm))
            throw new AssertionError("Installed negative fixture lost its canonical typed service owner");
        owner.getPhysicalBoardRuntime().validate();owner.getPcbLayout().validateAgainst(owner.getBoard());
        return 5;
    }

    private static int verifyCopperNegatives() throws Exception {
        java.lang.reflect.Method make = PhysicalFoundationDeveloperVerifier.class.getDeclaredMethod(
            "createBoard", String.class, int.class, PhysicalPackage.class);
        make.setAccessible(true);
        TroubleshootBoard board = (TroubleshootBoard) make.invoke(null, "U_CANARY_4", 4, PhysicalPackages.DEV_CANARY_4);
        PcbBoardLayout layout = new SeededPcbLayoutGenerator(StandardPcbFootprintProviders.createRegistry()).generate(board, 6152);
        java.util.Vector<PcbTraceGeometry> healthy = layout.getTraces();
        boolean branch = false;
        for (PcbTraceGeometry trace : healthy) if (trace.getEndPadId() == null) branch = true;
        if (!branch) throw new AssertionError("Missing actual trunk-branch regression fixture");
        ArchitectureDeveloperVerifier.verifyRasterConnectivity(layout, board);
        java.util.Vector<PcbTraceGeometry> changed = new java.util.Vector<PcbTraceGeometry>(healthy);
        changed.remove(0); layout.replaceTraces(changed); expectCopperFailure(layout, board);
        changed = new java.util.Vector<PcbTraceGeometry>(healthy);
        PcbPadPlacement a = layout.getPad("U_CANARY_4.1"), b = layout.getPad("U_CANARY_4.3");
        int[] x = a.getX() == b.getX() || a.getY() == b.getY() ? new int[] {a.getX(),b.getX()} : new int[] {a.getX(),b.getX(),b.getX()};
        int[] y = a.getX() == b.getX() || a.getY() == b.getY() ? new int[] {a.getY(),b.getY()} : new int[] {a.getY(),a.getY(),b.getY()};
        changed.add(new PcbTraceGeometry("false-net-label", x, y));
        layout.replaceTraces(changed); expectCopperFailure(layout, board);
        layout.replaceTraces(healthy); ArchitectureDeveloperVerifier.verifyRasterConnectivity(layout, board);
        return 5;
    }
    private static void expectCopperFailure(PcbBoardLayout layout, TroubleshootBoard board) {
        try { ArchitectureDeveloperVerifier.verifyRasterConnectivity(layout, board); }
        catch (IllegalStateException expected) {
            if (expected.getMessage().startsWith("independent copper raster disagrees")) return;
            throw expected;
        }
        throw new AssertionError("Independent raster accepted broken or shorted copper");
    }
}
