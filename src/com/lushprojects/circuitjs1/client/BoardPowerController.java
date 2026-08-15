package com.lushprojects.circuitjs1.client;

class BoardPowerController {
    private BoardPowerState state = BoardPowerState.POWERED;

    BoardPowerState getState() {
        return state;
    }

    void setState(BoardPowerState state) {
        this.state = state;
    }

    boolean isUnpowered() {
        return state == BoardPowerState.UNPOWERED;
    }
}
