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
        verifyPanLimits();
    }
    private void verifyPanLimits() {
        Rectangle board = new Rectangle(40, 20, 800, 400);
        Rectangle bench = board.union(new Rectangle(900, 20, 200, 400));
        Rectangle view = new Rectangle(0, 0, 1200, 700);
        PcbViewport camera = new PcbViewport(board); camera.setWorkbenchBounds(bench); camera.resize(view);
        for (PcbBoardSide side : PcbBoardSide.values()) for (int dx : new int[] {-100000, 100000})
            for (int dy : new int[] {-100000, 100000}) {
                camera.setFace(side); camera.fitBoard(); camera.pan(dx, dy);
                Rectangle projected = camera.permanent().project(board);
                require(overlap(projected, view) + 3 * projected.width >= .5 * projected.width * projected.height,
                    "overview pan retains half the physical board area, including corner drags");
                camera.zoom(3, 600, 350); camera.pan(dx, dy);
                // The PCB flips within its outline; the tray remains on the right.
                // Project their combined bench envelope without reflecting furniture.
                PcbViewport.Transform t = camera.permanent();
                projected = new PcbViewport.Transform(t.scale, t.x, t.y, 0, false).project(bench);
                require(overlap(projected, view) + 3 * view.width >= .5 * view.width * view.height,
                    "close pan retains board or adjacent tray in half the view");
            }
    }
    private double overlap(Rectangle a, Rectangle b) {
        return Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x)) *
            (double)Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y));
    }
    private U01ViewportChecks() { }
}
