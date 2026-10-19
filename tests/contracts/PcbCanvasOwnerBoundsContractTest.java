package com.lushprojects.circuitjs1.client;

/** Independent literal contracts for the virtual workbench canvas owner. */
public final class PcbCanvasOwnerBoundsContractTest {
    private static int assertions;

    private PcbCanvasOwnerBoundsContractTest() { }

    public static void main(String[] args) {
        ownerCase("exact-fit", 700, 620, new Rectangle(100, 120, 600, 500),
            new Rectangle(15, 20, 80, 70), 700, 620);
        ownerCase("over-wide", 800, 600, new Rectangle(75, 90, 825, 210),
            new Rectangle(20, 20, 100, 100), 900, 600);
        ownerCase("over-tall", 800, 600, new Rectangle(80, 40, 400, 700),
            new Rectangle(650, 20, 100, 100), 800, 740);
        ownerCase("both-axes", 500, 400, new Rectangle(125, 80, 500, 500),
            new Rectangle(10, 10, 100, 80), 625, 580);
        ownerCase("positive-origin-minimal-extent", 250, 220,
            new Rectangle(175, 155, 180, 130), new Rectangle(20, 20, 90, 90), 355, 285);
        ownerCase("no-shrink", 1200, 1000, new Rectangle(20, 30, 200, 150),
            new Rectangle(800, 100, 300, 500), 1200, 1000);
        sealedOwnerIsUnchanged();
        System.out.println("PASS: PCB canvas owner bounds contracts assertions=" + assertions);
    }

    private static void ownerCase(String name, int width, int height, Rectangle outline,
            Rectangle tray, int expectedWidth, int expectedHeight) {
        PcbBoardLayout layout = new PcbBoardLayout(width, height, outline, tray);
        Rectangle outlineBefore = layout.getBoardOutline();
        Rectangle trayBefore = layout.getPartsTray();
        layout.ensureCanvasContainsBoard();
        check(layout.getWidth() == expectedWidth && layout.getHeight() == expectedHeight,
            name + " exact canvas extent");
        check(same(layout.getBoardOutline(), outlineBefore), name + " outline unchanged");
        check(same(layout.getPartsTray(), trayBefore), name + " tray unchanged");
    }

    private static void sealedOwnerIsUnchanged() {
        PcbBoardLayout layout = new PcbBoardLayout(300, 200,
            new Rectangle(150, 100, 200, 150), new Rectangle(10, 10, 60, 60));
        Rectangle outlineBefore = layout.getBoardOutline();
        Rectangle trayBefore = layout.getPartsTray();
        layout.seal();
        IllegalStateException failure = null;
        try { layout.ensureCanvasContainsBoard(); }
        catch (IllegalStateException expected) { failure = expected; }
        check(failure != null && "Pristine PCB realization is sealed".equals(failure.getMessage()),
            "sealed owner rejects expansion");
        check(layout.isSealed(), "sealed state remains set");
        check(layout.getWidth() == 300 && layout.getHeight() == 200,
            "sealed canvas dimensions remain unchanged");
        check(same(layout.getBoardOutline(), outlineBefore), "sealed outline unchanged");
        check(same(layout.getPartsTray(), trayBefore), "sealed tray unchanged");
    }

    private static boolean same(Rectangle first, Rectangle second) {
        return first.x == second.x && first.y == second.y &&
            first.width == second.width && first.height == second.height;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
