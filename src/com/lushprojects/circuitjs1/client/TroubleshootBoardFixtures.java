package com.lushprojects.circuitjs1.client;

class TroubleshootBoardFixtures {
    static TroubleshootBoard createLedIndicatorBoard() {
        TroubleshootBoard board = new TroubleshootBoard("LED_INDICATOR");
        board.addNet(new BoardNet("VIN"));
        board.addNet(new BoardNet("LED_NODE"));
        board.addNet(new BoardNet("GND"));

        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("R1", "RESISTOR"));
        board.addComponent(new BoardComponent("LED1", "LED"));

        board.addPad(new BoardPad("J1.1", "J1", "1", "VIN"));
        board.addPad(new BoardPad("J1.2", "J1", "2", "GND"));
        board.addPad(new BoardPad("R1.1", "R1", "1", "VIN"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "LED_NODE"));
        board.addPad(new BoardPad("LED1.A", "LED1", "A", "LED_NODE"));
        board.addPad(new BoardPad("LED1.K", "LED1", "K", "GND"));

        board.addPowerInput(new ExternalBoardPowerInput(
            "VIN_INPUT", "J1.1", "J1.2", "VIN", "GND"));
        board.validate();
        return board;
    }
}
