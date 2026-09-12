package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Developer-only P01 oracle, executed unchanged on the JVM and compiled GWT path. */
final class P01PhysicalPoseChecks {
    private int assertions;
    private int poses;
    private static final int LIMIT = 100000000;
    private static final int[][] MATRIX = {
        {1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}
    };

    static P01PhysicalPoseChecks verify() {
        P01PhysicalPoseChecks checks = new P01PhysicalPoseChecks();
        checks.allDeclaredPoses();
        checks.literalSot23();
        checks.translationAndCopies();
        checks.invalidBoundaries();
        checks.rejectedCompactionIsAtomic();
        return checks;
    }

    int getAssertions() { return assertions; }
    int getPoses() { return poses; }

    // Independent affine oracle. Never calls a production rotation/mount/view helper.
    static Point expectedPoint(Point local, int width, int height, int x, int y,
            PcbRotation rotation, PcbBoardSide mount) {
        int r = rotation.ordinal();
        int u = mount == PcbBoardSide.BOTTOM ? width - local.x : local.x;
        int v = local.y;
        int offsetX = r == 1 ? height : r == 2 ? width : 0;
        int offsetY = r == 2 ? height : r == 3 ? width : 0;
        return new Point(x + MATRIX[r][0] * u + MATRIX[r][1] * v + offsetX,
            y + MATRIX[r][2] * u + MATRIX[r][3] * v + offsetY);
    }

    static Rectangle expectedRectangle(Rectangle local, int width, int height,
            int x, int y, PcbRotation rotation, PcbBoardSide mount) {
        Point a = expectedPoint(new Point(local.x, local.y), width, height, x, y, rotation, mount);
        Point b = expectedPoint(new Point(local.x + local.width, local.y + local.height),
            width, height, x, y, rotation, mount);
        return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y),
            Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private static PhysicalPackage[] packages() {
        return new PhysicalPackage[] { PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_DIODE, PhysicalPackages.THROUGH_HOLE_LED,
            PhysicalPackages.TO92_NPN, PhysicalPackages.TO92_NMOS,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            PhysicalPackages.DEV_SMD_0805, PhysicalPackages.DEV_SMD_SOT23,
            PhysicalPackages.MULTI_TERMINAL, PhysicalPackages.DEV_CANARY_3,
            PhysicalPackages.DEV_CANARY_3_ORDERED, PhysicalPackages.DEV_CANARY_4,
            PhysicalPackages.DEV_CANARY_5, PhysicalPackages.DEV_CANARY_6,
            PhysicalPackages.DEV_CANARY_CONNECTOR_3, PhysicalPackages.DEV_CANARY_CONNECTOR_4,
            PhysicalPackages.DEV_CANARY_CONNECTOR_5, PhysicalPackages.DEV_CANARY_CONNECTOR_6 };
    }

    private static BoardComponent component(PhysicalPackage p) {
        BoardComponent c = new BoardComponent("U1", "P01", p);
        for (String id : p.getTerminalIds()) c.addPadId("U1." + id);
        return c;
    }

    private void allDeclaredPoses() {
        for (final PhysicalPackage p : packages()) {
            for (PhysicalPackage.GeometryVariant variant : p.getGeometryVariants()) {
                final PhysicalPackageGeometry g = variant.getGeometry();
                for (final PcbRotation rotation : PcbRotation.values()) {
                    for (final PcbBoardSide side : PcbBoardSide.values()) {
                        final PcbPackagePose pose = new PcbPackagePose(700, 900, rotation, side);
                        if (!p.getAllowedRotations().contains(rotation) ||
                                !p.getAllowedMountingSides().contains(side)) {
                            rejects(new Runnable() { public void run() {
                                PcbComponentPlacement.fromPhysicalGeometry("U1", pose, p, g);
                            }}, "undeclared pose");
                            continue;
                        }
                        poses++;
                        PcbFootprint f = PcbFootprint.fromPhysicalPackage(component(p), pose, g);
                        PcbComponentPlacement placed = f.getPlacement();
                        Rectangle[] local = { g.getNominalBounds(), g.getBodyBounds(),
                            g.getBodyKeepOut(), g.getRoutingCourtyard(), g.getSelectionEnvelope(),
                            g.getDragEnvelope() };
                        Rectangle[] actual = { placed.getBounds(), placed.getBodyBounds(),
                            placed.getKeepOut(), placed.getRoutingCourtyard(),
                            placed.getSelectionEnvelope(), placed.getDragEnvelope() };
                        for (int k = 0; k < local.length; k++)
                            rect(actual[k], local[k], g, pose, "envelope " + k);
                        PhysicalPackageGeometry.Placement surfaces = g.placedAt(pose);
                        for (int i = 0; i < p.getTerminalCount(); i++) {
                            PhysicalPackageGeometry.Terminal t = g.getTerminal(i);
                            String id = p.getTerminalIds().get(i);
                            PcbPadPlacement pad = f.getPad("U1." + id);
                            require(t.getTerminalId().equals(id) && pad != null,
                                "terminal order and stable pad identity");
                            point(placed.getPadPoint(i), t.getPadCenter(), g, pose, "pad");
                            point(placed.getBoardPadProbeCenter(i), t.getBoardPadProbeCenter(),
                                g, pose, "board probe");
                            require(pose.toLocalPoint(placed.getPadPoint(i), g.getWidth(),
                                g.getHeight()).equals(t.getPadCenter()), "inverse pad pose");
                            rect(pad.getPadBounds(), t.getPadBounds(), g, pose, "pad bounds");
                            rect(pad.getProbeBounds(), t.getBoardPadProbeBounds(), g, pose, "probe bounds");
                            int[] m = MATRIX[rotation.ordinal()];
                            int dx = side == PcbBoardSide.BOTTOM ? -t.getEscapeDx() : t.getEscapeDx();
                            int dy = t.getEscapeDy();
                            require(pad.getEscapeDx() == m[0] * dx + m[1] * dy &&
                                pad.getEscapeDy() == m[2] * dx + m[3] * dy &&
                                pad.getEscapeLength() == t.getEscapeLength(), "mount/rotate escape");
                            require(surfaces.getEscapeDx(i) == pad.getEscapeDx() &&
                                surfaces.getEscapeDy(i) == pad.getEscapeDy(), "shared escape projection");
                            require(pad.getMountingSide() == side && pad.getAttachment() ==
                                t.getAttachment(), "attachment and face preserved");
                            for (boolean lifted : new boolean[] { false, true }) {
                                PhysicalPackageGeometry.Lead lead = t.getLead(lifted);
                                point(placed.getLeadEndPoint(i, lifted), lead.getEndPoint(), g, pose, "lead end");
                                point(placed.getLeadBodyPoint(i, lifted), lead.getBodyPoint(), g, pose, "lead body");
                                point(placed.getComponentLeadProbeCenter(i, lifted), lead.getComponentProbeCenter(),
                                    g, pose, "lead probe");
                                rect(placed.getLeadBounds(i, lifted), lead.getBounds(), g, pose, "lead bounds");
                                rect(placed.getComponentLeadProbeBounds(i, lifted), lead.getComponentProbeBounds(),
                                    g, pose, "lead probe bounds");
                            }
                        }
                        String frozen = f.geometryFingerprint();
                        f.getPads().clear(); p.getAllowedRotations().clear(); p.getAllowedMountingSides().clear();
                        placed.getBodyBounds().x = -1; g.getBodyBounds().x = -1;
                        require(frozen.equals(f.geometryFingerprint()), "immutable footprint/catalog getters");
                    }
                }
            }
        }
    }

    private void point(Point actual, Point local, PhysicalPackageGeometry g, PcbPackagePose pose,
            String label) {
        Point expected = expectedPoint(local, g.getWidth(), g.getHeight(), pose.getX(), pose.getY(),
            pose.getRotation(), pose.getMountingSide());
        require(expected.equals(actual), label + " follows independent affine oracle");
        for (PcbBoardSide side : PcbBoardSide.values()) {
            PcbBoardViewTransform view = new PcbBoardViewTransform(new Rectangle(100, 200, 2000, 2000), side);
            Point displayed = view.boardToView(actual);
            require(displayed.equals(new Point(side == PcbBoardSide.TOP ? actual.x : 2200 - actual.x,
                actual.y)), label + " independent view flip");
            require(view.viewToBoard(displayed).equals(actual), label + " inverse view");
        }
    }

    private void rect(Rectangle actual, Rectangle local, PhysicalPackageGeometry g, PcbPackagePose pose,
            String label) {
        require(actual.equals(expectedRectangle(local, g.getWidth(), g.getHeight(), pose.getX(),
            pose.getY(), pose.getRotation(), pose.getMountingSide())), label + " transformed");
        PcbBoardViewTransform view = new PcbBoardViewTransform(new Rectangle(100, 200, 2000, 2000),
            PcbBoardSide.BOTTOM);
        Rectangle shown = view.boardToView(actual);
        require(shown.equals(new Rectangle(2200 - actual.x - actual.width, actual.y,
            actual.width, actual.height)) && view.viewToBoard(shown).equals(actual), label + " view round trip");
    }

    private void literalSot23() {
        int[][][] top = { {{720,972},{780,972},{750,918}}, {{718,920},{718,980},{772,950}},
            {{780,918},{720,918},{750,972}}, {{772,980},{772,920},{718,950}} };
        int[][][] bottom = { {{780,972},{720,972},{750,918}}, {{718,980},{718,920},{772,950}},
            {{720,918},{780,918},{750,972}}, {{772,920},{772,980},{718,950}} };
        PhysicalPackage p = PhysicalPackages.DEV_SMD_SOT23;
        for (PcbRotation r : PcbRotation.values()) {
            for (PcbBoardSide side : PcbBoardSide.values()) {
                PcbComponentPlacement f = PcbComponentPlacement.fromPhysicalGeometry("Q1",
                    new PcbPackagePose(700,900,r,side), p, p.getGeometry());
                int[][] expected = side == PcbBoardSide.TOP ? top[r.ordinal()] : bottom[r.ordinal()];
                for (int i=0; i<3; i++) {
                    require(f.getPadPoint(i).equals(new Point(expected[i][0],expected[i][1])),
                        "literal SOT-23 pad " + i + " " + r + " " + side);
                    require(p.getGeometry().getTerminal(i).getAttachment() ==
                        PcbTerminalAttachment.SURFACE_PAD, "SOT-23 has no invented barrel");
                }
                Point a=f.getPadPoint(0), b=f.getPadPoint(1), c=f.getPadPoint(2);
                long area=((long)b.x-a.x)*(c.y-a.y)-((long)b.y-a.y)*(c.x-a.x);
                require(area == (side == PcbBoardSide.TOP ? -3240 : 3240), "mount handedness");
            }
        }
        for (PhysicalPackageGeometry.Terminal t : PhysicalPackages.DEV_SMD_0805.getGeometry().getTerminals())
            require(t.getAttachment() == PcbTerminalAttachment.SURFACE_PAD, "0805 surface attachment");
    }

    private void translationAndCopies() {
        final PhysicalPackage p = PhysicalPackages.DEV_SMD_0805;
        final PhysicalPackageGeometry g = p.getGeometry();
        for (PcbRotation rotation : PcbRotation.values()) {
            for (PcbBoardSide side : PcbBoardSide.values()) {
                PcbFootprint f = PcbFootprint.fromPhysicalPackage(component(p),
                    new PcbPackagePose(-75000000,-75000000,rotation,side), g);
                String frozen = f.geometryFingerprint();
                PcbFootprint translated = f.translated(75000000,75000000);
                require(translated.getPlacement().getX() == 75000000 &&
                    translated.getPlacement().getY() == 75000000, "large legal displacement");
                require(translated.translated(-75000000,-75000000).geometryFingerprint().equals(frozen),
                    "complete cross-origin footprint round trip");
                require(f.geometryFingerprint().equals(frozen), "translation preserves original snapshot");
            }
        }
        int[] xs={10,50}, ys={20,20};
        PcbTraceGeometry trace=new PcbTraceGeometry("N","U1.1","U1.2",xs,ys);
        Rectangle outline=new Rectangle(0,0,400,300), tray=new Rectangle(420,0,100,300);
        PcbBoardLayout layout=new PcbBoardLayout(540,320,outline,tray);
        layout.addTrace(trace);
        String frozen=layout.geometryFingerprint();
        xs[0]=90; ys[0]=90; trace.getXPoints()[0]=90; trace.getYPoints()[0]=90;
        layout.getTraces().clear(); outline.x=90; tray.y=90;
        layout.getBoardOutline().x=90; layout.getPartsTray().y=90;
        require(layout.geometryFingerprint().equals(frozen), "arrays/envelopes cannot alter frozen fingerprint");
        final PcbFootprint f=PcbFootprint.fromPhysicalPackage(component(p),100,100,g);
        final Vector<PcbPadPlacement> swapped=f.getPads();
        PcbPadPlacement first=swapped.get(0); swapped.set(0,swapped.get(1)); swapped.set(1,first);
        rejects(new Runnable() { public void run() { new PcbFootprint(f.getPlacement(),swapped); }},
            "terminal-order permutation");
        final Vector<PcbPadPlacement> stale=f.getPads();
        PcbPadPlacement pad=stale.get(0);
        stale.set(0,new PcbPadPlacement(pad.getPadId(),pad.getX()+1,pad.getY(),pad.getEscapeDx(),
            pad.getEscapeDy(),pad.getEscapeLength(),pad.getPadBounds(),pad.getProbeBounds(),
            pad.getAttachment(),pad.getMountingSide()));
        rejects(new Runnable() { public void run() { new PcbFootprint(f.getPlacement(),stale); }},
            "pad/probe projection disagreement");
        Rectangle body=g.getBodyBounds(); body.x++;
        final PhysicalPackageGeometry changed=new PhysicalPackageGeometry(g.getWidth(),g.getHeight(),
            g.getTerminals(),body,g.getBodyKeepOut(),g.getRoutingCourtyard(),g.getSelectionEnvelope(),
            g.getDragEnvelope(),g.getGeometryContractVersion());
        rejects(new Runnable() { public void run() {
            PcbComponentPlacement.fromPhysicalGeometry("U1",100,100,p,changed);
        }}, "undeclared nominal shape with unchanged version");
    }

    private void invalidBoundaries() {
        for (final int bad : new int[] { LIMIT+1, -LIMIT-1, Integer.MAX_VALUE, Integer.MIN_VALUE }) {
            rejects(new Runnable() { public void run() {
                new PcbTraceGeometry("N",new int[]{bad,0},new int[]{0,0});
            }}, "trace x boundary");
            rejects(new Runnable() { public void run() {
                new PcbTraceGeometry("N",new int[]{0,0},new int[]{0,bad});
            }}, "trace y boundary");
            rejects(new Runnable() { public void run() {
                new PcbPackagePose(bad,0,PcbRotation.DEG_0,PcbBoardSide.TOP);
            }}, "pose boundary");
            rejects(new Runnable() { public void run() {
                new PcbPadPlacement("P",bad,0,0,0,0,new Rectangle(bad,0,1,1),new Rectangle(bad,0,1,1));
            }}, "pad boundary");
            for (final PcbBoardSide side : PcbBoardSide.values()) {
                final PcbBoardViewTransform view=new PcbBoardViewTransform(new Rectangle(0,0,100,100),side);
                rejects(new Runnable() { public void run() { view.boardToView(new Point(bad,0)); }}, "view x input");
                rejects(new Runnable() { public void run() { view.viewToBoard(new Point(0,bad)); }}, "view y input");
                rejects(new Runnable() { public void run() {
                    view.boardToView(new Rectangle(bad,0,1,1));
                }}, "view rectangle input");
            }
        }
        final Rectangle overflow=new Rectangle(LIMIT-5,0,10,10);
        rejects(new Runnable() { public void run() {
            new PcbBoardViewTransform(overflow,PcbBoardSide.TOP);
        }}, "outline extremum");
        rejects(new Runnable() { public void run() {
            new PcbBoardLayout(100,100,overflow,new Rectangle(0,0,1,1));
        }}, "layout outline extremum");
        rejects(new Runnable() { public void run() {
            new PcbSilkscreenLabel("L","L",overflow,10,false,null);
        }}, "silkscreen extremum");
        rejects(new Runnable() { public void run() {
            new PcbPadPlacement("P",LIMIT-4,1,0,0,0,overflow,overflow);
        }}, "pad envelope extremum");
        rejects(new Runnable() { public void run() {
            new PcbPadPlacement("P",0,0,Integer.MIN_VALUE,0,1,new Rectangle(-1,-1,2,2),new Rectangle(-1,-1,2,2));
        }}, "overflowing direction");
        rejects(new Runnable() { public void run() {
            PcbPackagePose.top(0,0).toBoardDirection(Integer.MIN_VALUE,0);
        }}, "mount direction overflow");
        rejects(new Runnable() { public void run() {
            new PcbPadPlacement("P",LIMIT-20,0,1,0,30,new Rectangle(LIMIT-21,-1,2,2),
                new Rectangle(LIMIT-21,-1,2,2));
        }}, "escape tip boundary");
        rejects(new Runnable() { public void run() {
            PhysicalPackages.DEV_SMD_0805.getGeometry().placedAt(LIMIT-5,0);
        }}, "entire pose bounded at construction");
        rejects(new Runnable() { public void run() {
            PcbPackagePose.top(0,0).toLocalPoint(new Point(Integer.MAX_VALUE,0),100,100);
        }}, "inverse pose input");
        rejects(new Runnable() { public void run() {
            PcbPackagePose.top(0,0).toBoardPoint(new Point(Integer.MIN_VALUE,0),100,100);
        }}, "forward pose result");
        rejects(new Runnable() { public void run() {
            new PcbBoardViewTransform(new Rectangle(LIMIT-10,0,10,10),PcbBoardSide.BOTTOM)
                .boardToView(new Rectangle(LIMIT-15,0,10,10));
        }}, "mirrored rectangle far edge");
        PcbBoardViewTransform full=new PcbBoardViewTransform(new Rectangle(-LIMIT,-LIMIT,2*LIMIT,2*LIMIT),
            PcbBoardSide.BOTTOM);
        require(full.boardToView(new Point(-LIMIT,LIMIT)).equals(new Point(LIMIT,LIMIT)), "inclusive coordinate endpoints");
        require(PcbCoordinateSystem.boardDisplacement(LIMIT,-LIMIT)==2*LIMIT, "displacement spans full domain");
        final int[] manyX=new int[25], manyY=new int[25];
        for (int i=0;i<manyX.length;i++) manyX[i]=(i%2==0 ? -90000000 : 90000000);
        rejects(new Runnable() { public void run() {
            new PcbBoardLayout(100,100,new Rectangle(0,0,50,50),new Rectangle(60,0,10,10))
                .getTraceLength(new PcbTraceGeometry("N",manyX,manyY));
        }}, "trace-length accumulation overflow");
    }

    private void rejectedCompactionIsAtomic() {
        final PcbBoardLayout layout=new PcbBoardLayout(600,500,new Rectangle(0,0,400,400),
            new Rectangle(420,0,100,400));
        PcbFootprint f=PcbFootprint.fromPhysicalPackage(component(PhysicalPackages.DEV_SMD_0805),100,100);
        layout.addComponent(f.getPlacement());
        for (PcbPadPlacement p : f.getPads()) layout.addPad(p);
        String before=layout.geometryFingerprint();
        rejects(new Runnable() { public void run() { layout.compactToContent(LIMIT-1000,100,600); }},
            "compaction final outline boundary");
        require(before.equals(layout.geometryFingerprint()), "failed compaction leaves all geometry unchanged");
    }

    private void rejects(Runnable operation,String description) {
        boolean rejected=false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected=true; }
        require(rejected, "reject " + description);
    }

    private void require(boolean condition,String description) {
        assertions++;
        if (!condition) throw new IllegalStateException("P01: " + description);
    }
}
