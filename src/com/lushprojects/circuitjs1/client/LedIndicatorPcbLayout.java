package com.lushprojects.circuitjs1.client;

class LedIndicatorPcbLayout {
    static PcbBoardLayout create(TroubleshootBoard board) {
        PcbBoardLayout layout = new PcbBoardLayout(1040, 520,
            new Rectangle(50, 60, 760, 390), new Rectangle(850, 125, 150, 255));

        layout.addComponent(new PcbComponentPlacement("J1", 85, 190, 110, 130));
        layout.addComponent(new PcbComponentPlacement("R1", 330, 185, 230, 70));
        layout.addComponent(new PcbComponentPlacement("LED1", 665, 180, 90, 100));

        layout.addPad(new PcbPadPlacement("J1.1", 140, 225));
        layout.addPad(new PcbPadPlacement("J1.2", 140, 290));
        layout.addPad(new PcbPadPlacement("R1.1", 355, 220));
        layout.addPad(new PcbPadPlacement("R1.2", 535, 220));
        layout.addPad(new PcbPadPlacement("LED1.A", 690, 220));
        layout.addPad(new PcbPadPlacement("LED1.K", 730, 220));

        layout.addTrace(new PcbTraceGeometry("VIN",
            new int[] { 140, 245, 245, 355 }, new int[] { 225, 225, 220, 220 }));
        layout.addTrace(new PcbTraceGeometry("LED_NODE",
            new int[] { 535, 610, 610, 690 }, new int[] { 220, 220, 245, 220 }));
        layout.addTrace(new PcbTraceGeometry("GND",
            new int[] { 730, 770, 770, 260, 260, 140 },
            new int[] { 220, 220, 365, 365, 290, 290 }));
        layout.validateAgainst(board);
        return layout;
    }
}