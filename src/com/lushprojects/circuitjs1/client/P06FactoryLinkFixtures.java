package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Frozen crossing fixture shared by native and compiled P06 checks; never a Quick Play family. */
final class P06FactoryLinkFixtures {
    static final PhysicalPackage TEST_POINT = pointPackage();
    static final SeededPcbLayoutGenerator.AttemptObserver OBSERVER =
        new SeededPcbLayoutGenerator.AttemptObserver() { public void check(int attempt) { } };

    static final class Fixture {
        final TroubleshootBoard board = new TroubleshootBoard("P06_RAISED_CROSSOVER");
        final PcbBoardLayout layout;
        Fixture(boolean raised, int expansion, int shift) {
            this(raised, expansion, shift, false);
        }
        /** Matched all-copper control: same external terminals, one less physical component. */
        Fixture(boolean raised, int expansion, int shift, boolean noLink) {
            if (expansion < 0 || expansion > 80 || shift < -20 || shift > 20)
                throw new IllegalArgumentException("Outside frozen fixture envelope");
            layout = new PcbBoardLayout(900, 650,
                new Rectangle(100 - expansion, 100, 420 + 2 * expansion, 400),
                new Rectangle(650, 120, 200, 400));
            for (String net : noLink ? new String[] {"OVER", "UNDER", "GND"} :
                    new String[] {"LEFT", "RIGHT", "UNDER", "GND"})
                board.addNet(new BoardNet(net));
            PhysicalPackage link = raised ? PhysicalPackages.RAISED_FACTORY_LINK : PhysicalPackages.AXIAL_RESISTOR;
            if (!noLink) add("FL1", link, new String[] {"LEFT", "RIGHT"}, PcbPackagePose.top(200, 270 + shift));
            point("L", noLink ? "OVER" : "LEFT", 110, 300 + shift, PcbRotation.DEG_180);
            point("R", noLink ? "OVER" : "RIGHT", 510, 300 + shift, PcbRotation.DEG_0);
            point("A", "UNDER", 310, 150, PcbRotation.DEG_0);
            point("B", "UNDER", 310, 450, PcbRotation.DEG_0);
            point("G", "GND", 180, 450, PcbRotation.DEG_0);
            Vector<PcbPlacementConstraints.Part> demands = new Vector<PcbPlacementConstraints.Part>();
            for (String id : board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(id, "prototype", "Factory-link fixture",
                    "low-voltage", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP));
            board.addPowerInput(new ExternalBoardPowerInput("LINK_SUPPLY", "L.1", "G.1", noLink ? "OVER" : "LEFT", "GND"));
            board.addPowerInput(new ExternalBoardPowerInput("UNDER_SUPPLY", "A.1", "G.1", "UNDER", "GND"));
            board.validate();
            String[] ids = {"FL1", "L", "R", "A", "B", "G"};
            int[][] labels = {{350,350},{120,235},{475,235},{333,145},{333,440},{200,440}};
            for (int i = noLink ? 1 : 0; i < ids.length; i++)
                layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:" + ids[i], ids[i],
                    new Rectangle(labels[i][0], labels[i][1], 28, 18), 12, true, null));
        }
        private void point(String id, String net, int x, int y, PcbRotation rotation) {
            PcbPackagePose reference = new PcbPackagePose(100, 100, rotation, PcbBoardSide.BOTTOM);
            Point local = TEST_POINT.getGeometry().placedAt(reference).getPadPoint(0);
            add(id, TEST_POINT, new String[] {net},
                new PcbPackagePose(100 + x - local.x, 100 + y - local.y, rotation, PcbBoardSide.BOTTOM));
        }
        private void add(String id, PhysicalPackage pkg, String[] nets, PcbPackagePose pose) {
            BoardComponent component = new BoardComponent(id, pkg.getId(), pkg, id);
            board.addComponent(component);
            Vector<String> terminals = pkg.getTerminalIds();
            for (int i = 0; i < terminals.size(); i++)
                board.addPad(new BoardPad(id + "." + terminals.get(i), id, terminals.get(i), nets[i]));
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose, pkg.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
        void route() { PcbNetRouter.route(layout, board, layout.getBoardOutline(), 0, OBSERVER); }
    }

    private static PhysicalPackage pointPackage() {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        Point pad = new Point(10, 10), body = new Point(24, 10);
        PhysicalPackageGeometry.Lead connected = new PhysicalPackageGeometry.Lead(pad, body,
            new Rectangle(8,8,18,4), new Point(22,10), new Rectangle(20,8,6,4));
        PhysicalPackageGeometry.Lead lifted = new PhysicalPackageGeometry.Lead(new Point(32,10), body,
            new Rectangle(22,8,12,4), new Point(30,10), new Rectangle(28,8,6,4));
        terminals.add(new PhysicalPackageGeometry.Terminal("1", pad, new Rectangle(6,6,8,8),
            pad, new Rectangle(5,5,10,10), connected, lifted, 0,0,0));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(40,20,terminals,
            new Rectangle(22,6,10,8), new Rectangle(20,4,16,12), new Rectangle(0,0,40,20),
            new Rectangle(0,0,40,20), new Rectangle(0,0,40,20));
        Vector<String> names = new Vector<String>(); names.add("1");
        Vector<PhysicalPackage.GeometryVariant> variants = new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        Vector<PcbRotation> rotations = new Vector<PcbRotation>();
        rotations.add(PcbRotation.DEG_0); rotations.add(PcbRotation.DEG_90);
        rotations.add(PcbRotation.DEG_180); rotations.add(PcbRotation.DEG_270);
        Vector<PcbBoardSide> sides = new Vector<PcbBoardSide>();
        sides.add(PcbBoardSide.TOP); sides.add(PcbBoardSide.BOTTOM);
        return new PhysicalPackage("DEV_P06_TEST_POINT", names, new Vector<String>(), false,
            geometry, variants, "DEFAULT", PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT, rotations, sides);
    }
    private P06FactoryLinkFixtures() { }
}
