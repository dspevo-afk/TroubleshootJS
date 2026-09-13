package com.lushprojects.circuitjs1.client;

/** Independent ablations on a fresh copy of the actual generated electrical graph. */
final class Q15SupportChecks {
    private int assertions;
    private final PrivateSolverContext proof;
    private final GeneratedBoardInstance owner;
    private final SwitchElm command;
    private Q15SupportChecks(CirSim sim,long seed) {
        Rb15Plan plan=Rb15Plan.resolve(seed).withRoutedLayout(sim.getGeneratedBoardInstance().getPcbLayout());
        proof=PrivateSolverContext.open(sim);
        try {
            owner=new RelayOutputGenerator().generateResolved(seed,null,plan);
            owner.getFaultBinding().setApplied(false);
            proof.install(owner.getSimulationElements());
            SwitchElm found=null;
            for(CircuitElm element:owner.getSimulationElements())
                if(element instanceof SwitchElm && !(element instanceof WireElm)) found=(SwitchElm)element;
            if(found==null)throw new AssertionError("Missing command switch");command=found;
            proof.analyze();proof.advanceFor(.025);
        } catch(Throwable failure) {proof.close();throw failure;}
    }
    static int verify(CirSim sim,long seed) {
        Q15SupportChecks test=new Q15SupportChecks(sim,seed);
        try {test.run(Rb15Plan.resolve(seed));return test.assertions;}
        finally {test.proof.close();}
    }
    private CircuitElm part(String id) {return owner.getComponentBindings().getSingleElement(id);}
    private void require(boolean value,String message) {assertions++;if(!value)throw new AssertionError("Q15 support: "+message);}
    private void settle() {proof.analyze();proof.advanceFor(.025);}
    private void command(boolean high) {if((command.position==0)!=high)command.toggle();settle();}
    private void run(Rb15Plan plan) {
        ServiceRelayElm relay=(ServiceRelayElm)part("K1");
        ProtectionFuseElm fuse=(ProtectionFuseElm)part("F1");
        CapacitorElm bulk=(CapacitorElm)part("C1");
        ResistorElm bleed=(ResistorElm)part("RBLEED"),input=(ResistorElm)part("RIN"),limiter=(ResistorElm)part("RLED");
        LEDElm led=(LEDElm)part("LED1");
        CircuitElm load=((CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint("J4.1")).getElement();
        require(load.getVoltageDiff()>10 && load.getVoltageDiff()<12,"connector J4 delivers real loaded output");
        require(relay.coilCurrent>.013 && relay.coilCurrent<.018,"driver Q1 energizes actual twelve-volt coil");
        require(led.getCurrent()>.002 && led.getCurrent()<.004,"LED1 is a powered indicator");
        double lit=led.getCurrent();
        require(Math.abs(fuse.getCurrent()-load.getCurrent()-relay.coilCurrent-led.getCurrent()-bleed.getCurrent())<.0001,
            "J1, fuse and protected rail obey independent branch-current sum");
        require(input.getCurrent()>.00002,"J2 and series conditioner carry real command current");
        require(part("RPD").getCurrent()>.000005,"bias resistor provides the driver return path");
        require(part("DREV").getVoltageDiff()>.4 && part("DREV").getVoltageDiff()<1,"series diode has a real forward drop");
        limiter.setResistance(6600);settle();
        require(led.getCurrent()>lit*.4 && led.getCurrent()<lit*.6,"RLED limits actual emitter current");
        limiter.setResistance(3300);settle();
        input.setResistance(1e9);settle();require(load.getVoltageDiff()<.05,"opening RIN removes command drive");
        input.setResistance(1000);settle();
        if(plan.filtered) {
            CapacitorElm filter=(CapacitorElm)part("C2");
            double steady=filter.getVoltageDiff();
            command.toggle();proof.analyze();proof.advanceFor(.000005);double early=filter.getVoltageDiff();
            require(early>steady*.5 && early<steady,"C2 filters the falling command edge: "+early+" / "+steady);
            settle();command(true);filter.setCapacitance(1e-12);
            command.toggle();proof.analyze();proof.advanceFor(.000005);
            require(filter.getVoltageDiff()<early*.5,"removing effective filter capacitance sharpens the edge");
            filter.setCapacitance(1e-8);command(true);
        } else {
            ResistorElm termination=(ResistorElm)part("RBIAS");double v=termination.getVoltageDiff();
            require(termination.getCurrent()>.00002,"RBIAS terminates the input independently of RPD");
            termination.setResistance(1e12);settle();
            require(termination.getVoltageDiff()>v+.005,"termination changes the loaded command voltage");
            termination.setResistance(100000);settle();
        }
        double coilCurrent=relay.coilCurrent;command.toggle();proof.analyze();
        // LOW opens the command source. The RC version first discharges through
        // RPD (100k * 10nF = 1 ms), then releases the inductive coil. Observe the
        // actual flyback event within five time constants, preserving the same
        // independent current threshold as the unfiltered driver.
        boolean flyback=false;
        for(int sample=0;sample<1000 && !flyback;sample++) {
            proof.advanceFor(.000005);flyback=part("D1").getCurrent()>coilCurrent*.5;
        }
        require(flyback,"D1 carries released coil energy after the driver turns off");settle();
        require(load.getVoltageDiff()<.05,"LOW releases the load while the power indicator stays lit");
        ExternalPowerSimulationBinding entry=owner.getExternalPowerBindings().getBinding("COIL_INPUT");
        entry.setConnected(false);proof.analyze();proof.advanceFor(.000005);
        require(bulk.getVoltageDiff()>5,"C1 supplies residual energy after J1 disconnects");
        proof.advanceFor(.025);require(Math.abs(bulk.getVoltageDiff())<.05,"bleeder makes stored energy safe to measure");
        entry.setConnected(true);settle();bleed.setResistance(1e12);
        entry.setConnected(false);settle();
        require(bulk.getVoltageDiff()>.2,"without RBLEED charge remains after the service wait");
        bleed.setResistance(2200);settle();require(Math.abs(bulk.getVoltageDiff())<.05,"restoring bleeder discharges the real capacitor");
        entry.setConnected(true);command(true);
        fuse.blown=true;settle();require(load.getVoltageDiff()<.05 && led.getCurrent()<.00001,"open F1 isolates the powered board");
        fuse.reset();require(fuse.blown,"solver reset cannot heal the fuse");fuse.blown=false;settle();
        command(false);
        // Reverse only this detached fixture's input leads. The bounded normal
        // bench source still supplies +12 V; the board receives reversed polarity.
        LimitedDcSupplyElm source=entry.getLimitedSupply();int x=source.x,y=source.y;
        source.x=source.x2;source.y=source.y2;source.x2=x;source.y2=y;source.setPoints();settle();
        require(Math.abs(bulk.getVoltageDiff())<.05 && Math.abs(led.getCurrent())<.00001,"DREV blocks reversed input polarity");
    }
}
