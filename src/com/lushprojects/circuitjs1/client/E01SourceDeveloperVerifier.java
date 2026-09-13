package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent load-line and protection expectations exercised by the real solver. */
final class E01SourceDeveloperVerifier {
    private static int assertions;
    private E01SourceDeveloperVerifier() { }
    static String verify(CirSim sim, boolean forced) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("E01 requires an explicit developer route");
        if (forced) throw new AssertionError("e01-explicit-failure-canary");
        assertions = 0;
        long start = System.currentTimeMillis();
        GeneratedBoardInstance player = sim.getGeneratedBoardInstance();
        double nominal, limited, backfeed, shortCurrent;
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            LimitedDcSupplyElm a = supply(elements, 64, 5, .1);
            SwitchElm isolateA = connect(elements, 64, true);
            LimitedDcSupplyElm b = supply(elements, 256, 12, .1);
            SwitchElm isolateB = connect(elements, 256, false);
            ResistorElm load = new ResistorElm(448, 64); load.drag(448, 192); load.setResistance(100);
            elements.add(load); ground(elements, 448, 192);
            SwitchElm shorted = new SwitchElm(448,64); shorted.drag(448,192);
            if (shorted.position == 0) shorted.toggle(); elements.add(shorted);
            proof.install(elements); proof.analyze(); proof.advanceSteps(3);
            nominal = load.getVoltageDiff();
            near(nominal, 5 * 100 / 100.05, 1e-5, "loaded voltage includes real output resistance");
            near(a.getCurrent(), nominal/100, 1e-7, "supply readout satisfies KCL");
            load.setResistance(1); proof.analyze(); proof.advanceSteps(3);
            limited = load.getVoltageDiff();
            near(limited, .1, .00003, "overload collapses actual load voltage");
            near(a.getCurrent(), limited, 1e-7, "limited load current is solver current");
            require(a.isLimiting(), "compliance state from actual branch voltage");
            shorted.toggle(); proof.analyze(); proof.advanceSteps(3);
            shortCurrent = a.getCurrent();
            near(load.getVoltageDiff(), 0, 1e-8, "wire short is electrically zero volts");
            near(shortCurrent, .1, .00003, "short has finite causal source current");
            shorted.toggle(); load.setResistance(1000);
            isolateB.toggle(); proof.analyze(); proof.advanceSteps(3);
            backfeed = load.getVoltageDiff();
            near(backfeed, 12, .002, "higher source supplies shared load");
            require(a.getCurrent() < 0 && Math.abs(a.getCurrent()) < .000045,
                "lower source reverse current follows declared leakage");
            isolateA.toggle(); proof.analyze(); proof.advanceSteps(3);
            near(load.getVoltageDiff(), 12, .002, "one disabled source is not all-sources-off");
            near(a.getCurrent(), 0, 1e-7, "open isolation switch truly disconnects supply");
            isolateB.toggle(); proof.analyze(); proof.advanceSteps(3);
            near(load.getVoltageDiff(), 0, 1e-7, "all sources disconnected discharges resistive load");
        } finally { proof.close(); }
        require(sim.getGeneratedBoardInstance() == player, "source proof restores exact player owner");
        verifyProtection(sim);
        return "{\"schema\":\"e01-source-proof-v1\",\"status\":\"PASS\",\"assertions\":" + assertions +
            ",\"nominalVolts\":"+nominal+",\"limitedVolts\":"+limited+",\"shortAmps\":"+shortCurrent+
            ",\"backfeedVolts\":"+backfeed+",\"elapsedMs\":"+(System.currentTimeMillis()-start)+"}";
    }
    private static void verifyProtection(CirSim sim) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            supply(elements,64,5,.5);
            ProtectionFuseElm fuse = new ProtectionFuseElm(64,64); fuse.drag(256,64); elements.add(fuse);
            ResistorElm load = new ResistorElm(256,64); load.drag(256,192); load.setResistance(1); elements.add(load);
            ground(elements,256,192);
            proof.install(elements); proof.analyze(); proof.advanceFor(.005);
            require(fuse.blown && fuse.heat >= fuse.i2t, "accepted overload energy blows fuse");
            proof.advanceSteps(3);
            require(Math.abs(load.getCurrent()) < 1e-7, "blown fuse changes solved load current");
            double heat = fuse.heat; fuse.reset();
            require(fuse.blown && fuse.heat == heat, "solver reset cannot heal physical fuse");
            proof.analyze(); proof.advanceSteps(3);
            require(Math.abs(load.getCurrent()) < 1e-7, "reset preserves failed electrical state");
            ProtectionFuseElm fresh = new ProtectionFuseElm(0,0);
            for (int i = 0; i < 10; i++) fresh.startIteration();
            require(!fresh.blown && fresh.heat == 0, "trial iterations cannot age a new fuse");
            fresh.delete();
        } finally { proof.close(); }
        proof = PrivateSolverContext.open(sim);
        try {
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            supply(elements,64,24,.5);
            BoundedExternalLoadElm load = new BoundedExternalLoadElm(64,64,50);
            load.drag(64,192); elements.add(load);
            proof.install(elements); proof.analyze(); proof.advanceSteps(2);
            require(load.hasFailed(), "external load overload causes declared physical failure");
            proof.analyze(); proof.advanceSteps(3);
            require(Math.abs(load.getCurrent()) < 1e-7, "failed external load is electrically open");
            load.reset(); require(load.hasFailed(), "reset does not repair failed external load");
        } finally { proof.close(); }
    }
    private static LimitedDcSupplyElm supply(Vector<CircuitElm> e,int x,double v,double a) {
        LimitedDcSupplyElm source = new LimitedDcSupplyElm(x,192); source.drag(x,64); source.configure(v,a);
        e.add(source); ground(e,x,192); return source;
    }
    private static SwitchElm connect(Vector<CircuitElm> e,int x,boolean on) {
        SwitchElm s = new SwitchElm(x,64); s.drag(448,64);
        if ((s.position == 0) != on) s.toggle(); e.add(s); return s;
    }
    private static void ground(Vector<CircuitElm> e,int x,int y) {
        GroundElm g = new GroundElm(x,y); g.drag(x,y+32); e.add(g);
    }
    private static void require(boolean value,String label) {
        assertions++; if (!value) throw new AssertionError("E01: " + label);
    }
    private static void near(double actual,double expected,double tolerance,String label) {
        require(LowVoltageSourceModel.finite(actual) && Math.abs(actual-expected) <= tolerance,
            label+": "+actual+" expected "+expected);
    }
}
