package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Real selected solver canaries, independent of generation/diagnostic pass classifications. */
final class E03RelayDeveloperVerifier {
    private static int assertions;
    interface Completion { void finished(String report, Throwable failure); }
    static void start(final CirSim sim, final boolean forced, final Completion completion) {
        final GeneratedBoardInstance player=sim.getGeneratedBoardInstance();
        new com.google.gwt.user.client.Timer() {
            int stage, mutationAssertions;
            String raw;
            final long started=System.currentTimeMillis();
            public void run() {
                try {
                    if(sim.getGeneratedBoardInstance()!=player)
                        throw new IllegalStateException("E03 verification player owner changed");
                    if(stage==0) raw=verifyRaw(sim,forced);
                    else mutationAssertions+=E03RelayMutationChecks.verify(sim,stage-1);
                    if(++stage<=6) {schedule(0);return;}
                    completion.finished(raw+",\"mutationAssertions\":"+mutationAssertions+
                        ",\"playerCases\":6,\"elapsedMs\":"+(System.currentTimeMillis()-started)+"}",null);
                } catch(Throwable failure) {completion.finished(null,failure);}
            }
        }.schedule(0);
    }
    private static String verifyRaw(CirSim sim, boolean forced) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("E03 requires an explicit developer route");
        if (forced) throw new AssertionError("e03-explicit-failure-canary");
        assertions = 0; long start = System.currentTimeMillis();
        GeneratedBoardInstance player = sim.getGeneratedBoardInstance();
        StringBuilder cases = new StringBuilder();
        for (RelayDriverProvider driver : new RelayDriverProvider[] { new RelayDriverProvider.Bjt(), new RelayDriverProvider.Nmos() }) {
            CirSim.console("E03 raw relay: " + driver.getId());
            PrivateSolverContext proof = PrivateSolverContext.open(sim);
            try {
                Fixture f = new Fixture(driver); proof.install(f.elements); proof.analyze(); proof.advanceFor(.025);
                double on = f.load.getVoltageDiff(), current = f.relay.coilCurrent;
                near(on, 12*180/180.25, .01, "loaded contact voltage " + driver.getId());
                require(current > .037 && current < .041, "actual driver energizes 125 Ohm coil");
                require(f.relay.i_position == 1, "energized relay selects NO");
                require(!f.relay.getConnection(0,3) && !f.relay.getConnection(1,4), "coil/contact galvanic separation");
                int[] expected = {3,4,0,1,2};
                for(int i=0;i<5;i++) require(RelaySpecification.POSTS[i] == expected[i], "independent five-pin map");
                f.command.toggle(); proof.analyze(); proof.advanceFor(.000025);
                double flyback = f.diode.getCurrent();
                require(flyback > .025 && flyback < .041, "coil energy flows through real flyback diode: " + flyback);
                double coilV = f.relay.volts[3] - f.relay.volts[4];
                require(coilV < -.3 && coilV > -1.2, "flyback voltage has physical diode polarity: " + coilV);
                require(f.relay.coilCurrent > 0 && f.relay.coilCurrent < current, "inductive current decays continuously");
                proof.advanceFor(.025);
                require(Math.abs(f.relay.coilCurrent) < 1e-6 && f.relay.i_position == 0, "deenergized coil releases NC");
                double off = f.load.getVoltageDiff();
                require(off > 1e-7 && off < .00001, "real off-contact leakage reaches load: " + off);
                near(f.relay.getCurrentIntoNode(2), f.load.getCurrent(), 1e-9, "off-contact current participates in KCL");
                f.command.toggle(); f.relay.coilR = 1e9; proof.analyze(); proof.advanceFor(.025);
                require(Math.abs(f.relay.coilCurrent) < 1e-7 && f.load.getVoltageDiff() < .001,
                    "coil-open fault prevents pickup");
                require(f.relay.volts[3]-f.relay.volts[4] > 4.8, "coil-open retains applied coil voltage");
                f.relay.coilR = 125; f.relay.contactOpen = true; proof.analyze(); proof.advanceFor(.025);
                require(f.relay.coilCurrent > .037 && f.relay.i_position == 1 && f.load.getVoltageDiff() < .001,
                    "contact-open leaves energized coil distinguishable");
                f.relay.contactOpen = false; f.drive.setResistance(1e9); proof.analyze(); proof.advanceFor(.025);
                require(f.load.getVoltageDiff() < .001 && Math.abs(f.relay.coilCurrent) < 1e-6,
                    "driver open prevents pickup");
                require(Math.abs(f.relay.volts[3]-f.relay.volts[4]) < .1, "driver-open lacks coil differential");
                f.drive.setResistance(1000); f.relay.coilR = 720; f.relay.onCurrent = .65*12/720;
                proof.analyze(); proof.advanceFor(.025);
                require(f.load.getVoltageDiff() < .001 && f.relay.coilCurrent > .006,
                    "wrong 12 V catalog coil cannot pick up on 5 V");
                f.relay.coilR = 125; f.relay.onCurrent = .65*5/125; proof.analyze(); proof.advanceFor(.025);
                near(f.load.getVoltageDiff(), on, 1e-6, "correct replacement restores electrical output");
                if(cases.length()>0)cases.append(',');
                cases.append("{\"driver\":\"").append(driver.getId()).append("\",\"onVolts\":").append(on)
                    .append(",\"offVolts\":").append(off).append(",\"coilAmps\":").append(current)
                    .append(",\"flybackAmps\":").append(flyback).append('}');
            } finally { proof.close(); }
            require(sim.getGeneratedBoardInstance() == player, "exact player restored after private relay proof");
        }
        return "{\"schema\":\"e03-relay-proof-v1\",\"status\":\"PASS\",\"assertions\":"+assertions+
            ",\"cases\":["+cases+"],\"rawElapsedMs\":"+(System.currentTimeMillis()-start);
    }
    private static final class Fixture {
        final Vector<CircuitElm> elements = new Vector<CircuitElm>();
        final ServiceRelayElm relay = new RelaySpecification(5).create(800,300);
        final ResistorElm drive = new ResistorElm(500,100);
        final DiodeElm diode = new DiodeElm(500,500);
        final SwitchElm command = new SwitchElm(200,100);
        final ResistorElm load = new ResistorElm(1200,100);
        Fixture(RelayDriverProvider driver) {
            CircuitElm q=driver.create(600,300); elements.add(q); elements.add(relay);
            LimitedDcSupplyElm control = new LimitedDcSupplyElm(100,200); control.drag(100,100); control.configure(5,.25); elements.add(control);
            GroundElm g = new GroundElm(100,200); g.drag(100,240); elements.add(g);
            LimitedDcSupplyElm output = new LimitedDcSupplyElm(1400,200); output.drag(1400,100); output.configure(12,.25); elements.add(output);
            command.drag(300,100); elements.add(command); wire(control.getPost(1),command.getPost(0));
            drive.drag(560,100); drive.setResistance(1000); elements.add(drive);
            wire(command.getPost(1),drive.getPost(0)); wire(drive.getPost(1),q.getPost(0));
            ResistorElm pull=new ResistorElm(600,600); pull.drag(600,700); pull.setResistance(100000); elements.add(pull);
            wire(q.getPost(0),pull.getPost(0)); wire(pull.getPost(1),control.getPost(0));
            int collector = driver instanceof RelayDriverProvider.Bjt ? 1 : 2;
            int emitter = driver instanceof RelayDriverProvider.Bjt ? 2 : 1;
            wire(q.getPost(emitter),control.getPost(0)); wire(q.getPost(collector),relay.getPost(4));
            wire(control.getPost(1),relay.getPost(3));
            diode.drag(560,500); diode.modelName="1N4148"; diode.setup(); elements.add(diode);
            wire(diode.getPost(0),relay.getPost(4)); wire(diode.getPost(1),relay.getPost(3));
            load.drag(1200,200); load.setResistance(180); elements.add(load);
            wire(output.getPost(1),relay.getPost(0)); wire(relay.getPost(2),load.getPost(0)); wire(load.getPost(1),output.getPost(0));
        }
        void wire(Point from,Point to) { WireElm e=new WireElm(from.x,from.y); e.x2=to.x; e.y2=to.y; e.setPoints(); elements.add(e); }
    }
    private static void require(boolean value,String message) { assertions++; if(!value)throw new AssertionError("E03: "+message); }
    private static void near(double actual,double expected,double error,String message) {
        require(PowerDomainContract.finite(actual)&&Math.abs(actual-expected)<=error,message+": "+actual);
    }
}
