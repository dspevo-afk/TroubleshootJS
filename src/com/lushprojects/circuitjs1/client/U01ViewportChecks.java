package com.lushprojects.circuitjs1.client;

/** Independent numeric expectations, also run in the compiled application. */
final class U01ViewportChecks {
    private int assertions;
    static int verify() { U01ViewportChecks checks = new U01ViewportChecks(); checks.run(); return checks.assertions; }
    private void require(boolean value, String message) {
        assertions++; if (!value) throw new IllegalStateException("U01 " + message);
    }
    private void near(double value, double expected, String message) { require(Math.abs(value - expected) < 1e-8, message); }
    private void same(PcbViewport.Transform a, PcbViewport.Transform b) {
        require(a.scale == b.scale && a.x == b.x && a.y == b.y && a.flipped == b.flipped, "exact permanent camera restoration");
    }
    private void run() {
        PcbViewport camera = new PcbViewport(new Rectangle(40, 20, 1000, 500));
        camera.resize(new Rectangle(10, 10, 1040, 540));
        near(camera.current().screenX(40), 30, "literal fitted left");
        near(camera.current().screenY(20), 30, "literal fitted top");
        camera.pan(21.5, -17.25);
        PcbViewport.Transform original = camera.permanent();
        double anchorX = original.boardX(307), anchorY = original.boardY(214);
        camera.zoom(1.5, 307, 214);
        near(camera.current().boardX(307), anchorX, "cursor anchored permanent zoom X");
        near(camera.current().boardY(214), anchorY, "cursor anchored permanent zoom Y");
        PcbViewport.Transform saved = camera.permanent();
        require(camera.inspect(307, 214), "space starts in board");
        near(camera.current().screenX(anchorX), 307, "loupe anchor X");
        near(camera.current().screenY(anchorY), 214, "loupe anchor Y");
        camera.pan(900, -400); same(saved, camera.permanent());
        camera.moveCursor(650, 300); camera.zoom(2, 650, 300); same(saved, camera.permanent());
        near(camera.current().screenX(saved.boardX(650)), 650, "moving cursor anchored");
        for (int reason = 0; reason < 5; reason++) {
            camera.dismiss(); same(saved, camera.current()); require(!camera.isInspecting(), "dismissed");
            camera.inspect(400, 220);
        }
        camera.dismiss(); camera.setFace(PcbBoardSide.BOTTOM);
        near(camera.current().screenX(40), saved.screenX(1040), "literal bottom reflection");
        camera.setFace(PcbBoardSide.TOP); same(saved, camera.current());
        require(!camera.inspect(-1, 30), "tray/outside cannot start loupe");
        for (boolean flipped : new boolean[] { false, true }) {
            PcbViewport.Transform overview = new PcbViewport.Transform(0.1, 0, 0, 100, flipped);
            Rectangle thin = overview.project(new Rectangle(20, 30, 2, 3));
            require(thin.x == (flipped ? 8 : 2) && thin.y == 3 && thin.width == 1 && thin.height == 1,
                "literal subpixel feature retains one raster pixel on either face");
        }
        for (int count : new int[] {15, 30, 56, 100}) {
            PcbViewport large = new PcbViewport(new Rectangle(20, 20, 700 + count * 23, 450 + count * 11));
            large.resize(new Rectangle(0, 0, 900, 700));
            for (PcbBoardSide side : PcbBoardSide.values()) {
                large.setFace(side); large.pan(83, -41); large.zoom(2, 450, 350); large.inspect(470, 380);
                for (int index = 0; index < count; index++) {
                    double bx = 40 + index * 20.25, by = 100 + index * 10.5;
                    PcbViewport.Transform t = large.current();
                    near(t.boardX(t.screenX(bx)), bx, "forward inverse X");
                    near(t.boardY(t.screenY(by)), by, "forward inverse Y");
                    Rectangle rect = t.project(new Rectangle(40 + index * 20, 100, 20, 30));
                    require(rect.width <= Math.ceil(20 * t.scale) + 1, "no enlarged invisible envelope");
                }
                large.dismiss();
            }
        }
        camera.inspect(300, 200); camera.resize(new Rectangle(0, 0, 800, 600));
        require(!camera.isInspecting(), "resize cancels loupe");
        boolean rejected = false;
        try { camera.zoom(Double.NaN, 1, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "invalid zoom rejected");
    }
    private U01ViewportChecks() { }
}
