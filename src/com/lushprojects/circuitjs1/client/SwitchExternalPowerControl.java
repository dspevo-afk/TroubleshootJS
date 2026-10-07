package com.lushprojects.circuitjs1.client;

/** One source command owns all of its actual isolation poles. */
class SwitchExternalPowerControl implements ExternalPowerControl {
    private final SwitchElm[] isolationSwitches;

    SwitchExternalPowerControl(SwitchElm isolationSwitch) {
        this(new SwitchElm[] {isolationSwitch});
    }

    SwitchExternalPowerControl(SwitchElm[] switches) {
        if (switches == null || switches.length == 0)
            throw new IllegalArgumentException("Missing external power isolation switches");
        isolationSwitches = new SwitchElm[switches.length];
        for (int i = 0; i < switches.length; i++)
            isolationSwitches[i] = switches[i];
        for (int i = 0; i < isolationSwitches.length; i++) {
            if (isolationSwitches[i] == null)
                throw new IllegalArgumentException("Missing external power isolation switch");
            for (int j = 0; j < i; j++)
                if (isolationSwitches[i] == isolationSwitches[j])
                    throw new IllegalArgumentException("Duplicate external power isolation switch");
        }
    }

    public void setConnected(boolean connected) {
        for (SwitchElm isolationSwitch : isolationSwitches)
            if ((isolationSwitch.position == 0) != connected)
                isolationSwitch.toggle();
    }

    public boolean isConnected() {
        // A partially closed source must never be reported physically isolated.
        for (SwitchElm isolationSwitch : isolationSwitches)
            if (isolationSwitch.position == 0) return true;
        return false;
    }
}
