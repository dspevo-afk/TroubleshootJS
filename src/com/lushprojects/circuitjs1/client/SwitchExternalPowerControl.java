package com.lushprojects.circuitjs1.client;

class SwitchExternalPowerControl implements ExternalPowerControl {
    private final SwitchElm isolationSwitch;

    SwitchExternalPowerControl(SwitchElm isolationSwitch) {
        if (isolationSwitch == null)
            throw new IllegalArgumentException("Missing external power isolation switch");
        this.isolationSwitch = isolationSwitch;
    }

    public void setConnected(boolean connected) {
        if (isConnected() != connected)
            isolationSwitch.toggle();
    }

    public boolean isConnected() {
        return isolationSwitch.position == 0;
    }
}