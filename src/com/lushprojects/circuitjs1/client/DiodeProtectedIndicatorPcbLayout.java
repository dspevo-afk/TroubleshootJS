package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorPcbLayout {
    static PcbBoardLayout create(TroubleshootBoard board) {
        PcbBoardLayout layout = new PcbBoardLayout(1040, 520,
            new Rectangle(50, 60, 760, 390), new Rectangle(850, 125, 150, 255));
        layout.addComponent(new PcbComponentPlacement("J1", 75, 190, 100, 130));
        layout.addComponent(new PcbComponentPlacement("D1", 225, 185, 210, 70));
        layout.addComponent(new PcbComponentPlacement("R1", 450, 185, 170, 70));
        layout.addComponent(new PcbComponentPlacement("LED1", 665, 180, 90, 100));

        layout.addPad(new PcbPadPlacement("J1.1", 125, 225));
        layout.addPad(new PcbPadPlacement("J1.2", 125, 290));
        layout.addPad(new PcbPadPlacement("D1.A", 250, 220));
        layout.addPad(new PcbPadPlacement("D1.K", 410, 220));
        layout.addPad(new PcbPadPlacement("R1.1", 475, 220));
        layout.addPad(new PcbPadPlacement("R1.2", 595, 220));
        layout.addPad(new PcbPadPlacement("LED1.A", 690, 220));
        layout.addPad(new PcbPadPlacement("LED1.K", 730, 220));

        layout.addTrace(new PcbTraceGeometry("VIN",
            new int[] { 125, 190, 190, 250 }, new int[] { 225, 225, 250, 220 }));
        layout.addTrace(new PcbTraceGeometry("DIODE_OUT",
            new int[] { 410, 440, 440, 475 }, new int[] { 220, 220, 245, 220 }));
        layout.addTrace(new PcbTraceGeometry("LED_NODE",
            new int[] { 595, 640, 640, 690 }, new int[] { 220, 220, 245, 220 }));
        layout.addTrace(new PcbTraceGeometry("GND",
            new int[] { 730, 770, 770, 250, 250, 125 },
            new int[] { 220, 220, 365, 365, 290, 290 }));
        layout.validateAgainst(board);
        return layout;
    }
}
