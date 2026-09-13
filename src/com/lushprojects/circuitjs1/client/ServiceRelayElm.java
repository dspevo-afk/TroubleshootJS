package com.lushprojects.circuitjs1.client;

/** One inherited RL coil, mechanically coupled but electrically isolated SPDT contacts. */
final class ServiceRelayElm extends RelayElm {
    boolean contactOpen;
    private double ncCurrent, noCurrent;
    ServiceRelayElm(int x,int y) { super(x,y); }
    ServiceRelayElm(int x,int y,int x2,int y2,int flags,StringTokenizer st) {
        super(x,y,x2,y2,flags,st);
        String state = st.nextToken();
        if (!"true".equals(state) && !"false".equals(state))
            throw new IllegalArgumentException("Invalid relay contact state");
        contactOpen = "true".equals(state);
        if (poleCount != 1) throw new IllegalArgumentException("Only one relay pole is qualified");
        if (!LowVoltageSourceModel.finite(coilCurrent) || inductance != .2 || r_on != .2 || r_off != 1e9 ||
                (coilR != 125 && coilR != 720 && coilR != 1e9) ||
                (onCurrent != .65*5/125 && onCurrent != .65*12/720))
            throw new IllegalArgumentException("Unsupported relay model parameters");
    }
    int getDumpType() { return 452; }
    String dump() { return super.dump() + " " + contactOpen; }
    void doStep() {
        ind.doStep(volts[nCoil1]-volts[nCoil3]);
        sim.stampResistor(nodes[0],nodes[1],i_position==0?r_on:r_off);
        sim.stampResistor(nodes[0],nodes[2],noResistance());
    }
    private double noResistance() { return !contactOpen && i_position==1?r_on:r_off; }
    void calculateCurrent() {
        coilCurrent = ind.calculateCurrent(volts[nCoil1]-volts[nCoil3]);
        ncCurrent = (volts[0]-volts[1])/(i_position==0?r_on:r_off);
        noCurrent = (volts[0]-volts[2])/noResistance();
        switchCurrent[0] = ncCurrent + noCurrent;
    }
    double getCurrentIntoNode(int n) {
        switch(n) {
        case 0: return -ncCurrent-noCurrent;
        case 1: return ncCurrent;
        case 2: return noCurrent;
        case 3: return -coilCurrent;
        case 4: return coilCurrent;
        default: return 0;
        }
    }
}
