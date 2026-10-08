package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Matched private converter graphs. No installed package or qualified output claim. */
final class E06ConverterFixtures {
    private E06ConverterFixtures() { }

    static Fixture detailed() { return create(false); }
    static Fixture averaged() { return create(true); }

    private static Fixture create(boolean averaged) {
        Fixture f = new Fixture(); boolean complete = false;
        try {
            f.front = E05ElectricalFixtures.rectifierBulk(); f.elements.addAll(f.front.elements);
            removePreload(f);
            f.inPlus = f.front.bulk.getPost(0); f.inMinus = f.front.bulk.getPost(1);
            f.preLPlus = new Point(1248, 160); f.outMinus = new Point(1152, 288);
            if (averaged) {
                f.averaged = new E06AveragedConverterElm(1024, 160, f.contract); f.elements.add(f.averaged);
                wire(f, f.inPlus, f.averaged.getPost(E06ConverterContract.IN_PLUS));
                wire(f, f.inMinus, f.averaged.getPost(E06ConverterContract.IN_MINUS));
                wire(f, f.preLPlus, f.averaged.getPost(E06ConverterContract.PRE_L_PLUS));
                wire(f, f.outMinus, f.averaged.getPost(E06ConverterContract.OUT_MINUS));
            } else {
                f.transformer = new TransformerElm(1024, 160); f.transformer.drag(1152, 288);
                f.transformer.inductance = E06ConverterContract.PRIMARY_HENRIES;
                f.transformer.ratio = E06ConverterContract.SECONDARY_OVER_PRIMARY_TURNS;
                f.transformer.couplingCoef = E06ConverterContract.COUPLING;
                f.transformer.flags |= Inductor.FLAG_BACK_EULER; f.elements.add(f.transformer);
                f.highSwitch = end(new BoundedPowerSwitchElm(864, 144), 944, 144);
                f.lowSwitch = end(new BoundedPowerSwitchElm(864, 304), 944, 304);
                f.elements.add(f.highSwitch); f.elements.add(f.lowSwitch);
                wire(f, f.inPlus, f.highSwitch.getPost(2)); wire(f, f.highSwitch.getPost(1), f.transformer.getPost(0));
                wire(f, f.transformer.getPost(2), f.lowSwitch.getPost(2)); wire(f, f.lowSwitch.getPost(1), f.inMinus);
                f.resetLower = diode(f, f.inMinus, f.transformer.getPost(0));
                f.resetUpper = diode(f, f.transformer.getPost(2), f.inPlus);
                f.forward = diode(f, f.transformer.getPost(1), f.preLPlus);
            }
            f.freewheel = diode(f, f.outMinus, f.preLPlus);
            f.outputInductor = end(new InductorElm(1248, 160), 1376, 160);
            f.outputInductor.inductance = E06ConverterContract.OUTPUT_HENRIES;
            f.outputInductor.flags &= ~Inductor.FLAG_BACK_EULER;
            f.outputInductor.ind.setup(f.outputInductor.inductance, 0, f.outputInductor.flags);
            f.elements.add(f.outputInductor);
            f.outputPlus = new Point(1536, 160);
            f.windingResistance = resistor(f, f.outputInductor.getPost(1), f.outputPlus, E06ConverterContract.WINDING_OHMS);
            Point capTop = new Point(1536, 224);
            f.capacitorEsr = resistor(f, f.outputPlus, capTop, E06ConverterContract.ESR_OHMS);
            f.outputCap = capacitor(f, capTop, new Point(1536, 384), E06ConverterContract.OUTPUT_FARADS);
            wire(f, f.outputCap.getPost(1), f.outMinus);
            f.load = resistor(f, new Point(1664, 160), new Point(1664, 384), E06ConverterContract.LOAD_OHMS);
            wire(f, f.outputPlus, f.load.getPost(0)); wire(f, f.outMinus, f.load.getPost(1));
            f.bleed = resistor(f, new Point(1792, 160), new Point(1792, 384), E06ConverterContract.BLEED_OHMS);
            wire(f, f.outputPlus, f.bleed.getPost(0)); wire(f, f.outMinus, f.bleed.getPost(1));
            if (!averaged) {
                f.pwm = new E06PwmControllerElm(672, 512, f.contract); f.elements.add(f.pwm);
                wire(f, f.inPlus, f.pwm.getPost(E06PwmControllerElm.INPUT));
                wire(f, f.inMinus, f.pwm.getPost(E06PwmControllerElm.RETURN));
                wire(f, f.pwm.getPost(E06PwmControllerElm.HIGH_GATE), f.highSwitch.getPost(0));
                wire(f, f.pwm.getPost(E06PwmControllerElm.HIGH_SOURCE), f.highSwitch.getPost(1));
                wire(f, f.pwm.getPost(E06PwmControllerElm.LOW_GATE), f.lowSwitch.getPost(0));
                wire(f, f.pwm.getPost(E06PwmControllerElm.LOW_SOURCE), f.lowSwitch.getPost(1));
            }
            f.bias = new E06PwmControllerElm.BiasElm(672, 736); f.elements.add(f.bias);
            wire(f, f.inPlus, f.bias.getPost(0)); wire(f, f.inMinus, f.bias.getPost(1));
            f.enablePoint = averaged ? f.averaged.getPost(E06ConverterContract.ENABLE) :
                f.pwm.getPost(E06PwmControllerElm.ENABLE);
            f.feedbackPoint = averaged ? f.averaged.getPost(E06ConverterContract.FEEDBACK) :
                f.pwm.getPost(E06PwmControllerElm.FEEDBACK);
            f.enable = end(new E02FiniteSourceElm(512, 896, 0, 1000), 640, 896); f.elements.add(f.enable);
            wire(f, f.enable.getPost(0), f.inMinus); wire(f, f.enable.getPost(1), f.enablePoint);
            f.opto = optocoupler(); f.opto.setPoints(); f.elements.add(f.opto);
            f.senseResistor = resistor(f, new Point(1472, 480), new Point(1472, 544), E06ConverterContract.SENSE_OHMS);
            wire(f, f.outputPlus, f.senseResistor.getPost(0));
            f.senseZener = end(new ZenerElm(1472, 576), 1472, 544);
            f.senseZener.model = DiodeModel.getModelWithParameters(.805904783, E06ConverterContract.ZENER_VOLTS);
            f.senseZener.modelName = f.senseZener.model.name; f.senseZener.setup(); f.elements.add(f.senseZener);
            wire(f, f.senseZener.getPost(0), f.opto.getPost(0)); wire(f, f.opto.getPost(1), f.outMinus);
            f.feedbackPullup = resistor(f, f.bias.getPost(2), f.feedbackPoint, E06ConverterContract.PULLUP_OHMS);
            f.feedbackComp = capacitor(f, f.feedbackPoint, new Point(768, 816), E06ConverterContract.COMPENSATION_FARADS);
            wire(f, f.feedbackComp.getPost(1), f.inMinus);
            wire(f, f.opto.getPost(2), f.feedbackPoint); wire(f, f.opto.getPost(3), f.inMinus);
            complete = true; return f;
        } finally { if (!complete) for (CircuitElm e : f.elements) e.delete(); }
    }

    private static OptocouplerElm optocoupler() {
        // Composite node allocation must see the fixed zero-series-resistance junction first.
        String previous = DiodeElm.lastModelName, previousTransistor = TransistorElm.lastModelName;
        try {
            DiodeElm.lastModelName = "default"; TransistorElm.lastModelName = "default";
            return new OptocouplerElm(1600, 512);
        } finally {
            DiodeElm.lastModelName = previous; TransistorElm.lastModelName = previousTransistor;
        }
    }
    private static void removePreload(Fixture f) {
        ResistorElm removed = f.front.load;
        for (int i = f.elements.size() - 1; i >= 0; i--) {
            CircuitElm e = f.elements.get(i);
            if (e == removed || e instanceof WireElm &&
                    (same(e.getPost(0), removed.getPost(0)) || same(e.getPost(1), removed.getPost(0)) ||
                     same(e.getPost(0), removed.getPost(1)) || same(e.getPost(1), removed.getPost(1)))) {
                f.elements.remove(i); f.front.elements.remove(e); e.delete();
            }
        }
        f.front.load = null;
    }
    private static boolean same(Point a, Point b) { return a.x == b.x && a.y == b.y; }
    private static void wire(Fixture f, Point a, Point b) {
        if (!same(a, b)) f.elements.add(end(new WireElm(a.x, a.y), b.x, b.y));
    }
    private static DiodeElm diode(Fixture f, Point a, Point b) {
        DiodeElm d = end(new DiodeElm(a.x, a.y), b.x, b.y);
        d.modelName = "default"; d.model = null; d.setup(); f.elements.add(d); return d;
    }
    private static ResistorElm resistor(Fixture f, Point a, Point b, double ohms) {
        ResistorElm r = end(new ResistorElm(a.x, a.y), b.x, b.y);
        r.setResistance(ohms); f.elements.add(r); return r;
    }
    private static CapacitorElm capacitor(Fixture f, Point a, Point b, double farads) {
        CapacitorElm c = end(new CapacitorElm(a.x, a.y), b.x, b.y);
        c.capacitance = farads; c.initialVoltage = 0; c.flags |= CapacitorElm.FLAG_BACK_EULER;
        c.reset(); f.elements.add(c); return c;
    }
    private static <T extends CircuitElm> T end(T e, int x, int y) { e.x2 = x; e.y2 = y; e.setPoints(); return e; }

    /** Native-only finite channel approximation, retaining the real tied-source body diode. */
    private static final class BoundedPowerSwitchElm extends MosfetElm {
        private boolean channelReady, appliedOn;
        private double appliedResistance = E06ConverterContract.POWER_SWITCH_OFF_OHMS;

        BoundedPowerSwitchElm(int x, int y) {
            super(x, y, false); vt = E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS;
            beta = .5; flags = FLAG_BODY_DIODE;
        }
        boolean showBulk() { return true; }
        boolean drawDigital() { return false; }
        boolean doBodyDiode() { return true; }
        void setPoints() { super.setPoints(); flags &= ~FLAGS_GLOBAL; flags |= FLAG_BODY_DIODE; }
        void reset() {
            super.reset(); channelReady = false; appliedOn = false;
            appliedResistance = E06ConverterContract.POWER_SWITCH_OFF_OHMS;
            ids = gm = 0; mode = 0;
        }
        void stamp() { super.stamp(); channelReady = false; }

        void calculate(boolean finished) {
            for (int i = 0; i < 3; i++) E06ConverterContract.requireFinite(volts[i]);
            double vgs = volts[0] - volts[1]; E06ConverterContract.requireFinite(vgs);
            boolean on = vgs >= E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS;
            if (!finished) {
                if (!channelReady || on != appliedOn) sim.converged = false;
                appliedOn = on; channelReady = true;
                appliedResistance = on ? E06ConverterContract.POWER_SWITCH_ON_OHMS :
                    E06ConverterContract.POWER_SWITCH_OFF_OHMS;
                sim.stampResistor(nodes[1], nodes[2], appliedResistance);
            } else if (!channelReady || on != appliedOn) {
                throw new IllegalStateException("E06 accepted switch differs from stamped channel");
            }
            lastv0 = volts[0]; lastv1 = volts[1]; lastv2 = volts[2];
            double vds = volts[2] - volts[1]; E06ConverterContract.requireFinite(vds);
            ids = vds / appliedResistance; gm = 0; mode = appliedOn ? 1 : 0;
            double channelPower = vds * ids;
            if (!E06ConverterContract.finite(ids) || !E06ConverterContract.finite(channelPower) || channelPower < 0)
                throw new IllegalStateException("E06 nonfinite or nonpassive switch channel");

            double bodyToSource = volts[bodyTerminal] - volts[1];
            double bodyToDrain = volts[bodyTerminal] - volts[2];
            E06ConverterContract.requireFinite(bodyToSource); E06ConverterContract.requireFinite(bodyToDrain);
            if (!finished) {
                diodeB1.doStep(bodyToSource); diodeB2.doStep(bodyToDrain);
            }
            diodeCurrent1 = diodeB1.calculateCurrent(bodyToSource);
            diodeCurrent2 = diodeB2.calculateCurrent(bodyToDrain);
            double bodyPower = bodyToSource * diodeCurrent1 + bodyToDrain * diodeCurrent2;
            if (finished && (!E06ConverterContract.finite(diodeCurrent1) || !E06ConverterContract.finite(diodeCurrent2) ||
                    !E06ConverterContract.finite(bodyPower) || bodyPower < 0))
                throw new IllegalStateException("E06 nonfinite or nonpassive switch body diode");
        }
    }

    static final class Fixture {
        final E06ConverterContract contract = new E06ConverterContract();
        final Vector<CircuitElm> elements = new Vector<CircuitElm>();
        E05ElectricalFixtures.Fixture front;
        E06PwmControllerElm pwm;
        E06AveragedConverterElm averaged;
        E06PwmControllerElm.BiasElm bias;
        E02FiniteSourceElm enable;
        TransformerElm transformer;
        MosfetElm highSwitch, lowSwitch;
        DiodeElm resetLower, resetUpper, forward, freewheel;
        InductorElm outputInductor;
        ResistorElm windingResistance, capacitorEsr, load, bleed, senseResistor, feedbackPullup;
        CapacitorElm outputCap, feedbackComp;
        OptocouplerElm opto;
        ZenerElm senseZener;
        Point inPlus, inMinus, preLPlus, outMinus, outputPlus, enablePoint, feedbackPoint;
        String getParameterSummary() {
            return contract.canonical() + ";actual-source-peak=" + front.input.source.maxVoltage +
                ";actual-source-hz=" + front.input.source.frequency + ";actual-source-R=" + front.input.impedance.resistance +
                ";actual-bulk-C=" + front.bulk.capacitance + ";actual-bulk-bleed-R=" + front.bleed.resistance +
                (averaged != null ? ";actual-stage=E06AveragedConverterElm;switch-waveforms=unsupported" :
                ";actual-transformer=" + transformer.inductance + "/" + transformer.ratio + "/" + transformer.couplingCoef +
                ";actual-transformer-flags=" + transformer.flags + ";actual-Q-flags=" + highSwitch.flags + "/" + lowSwitch.flags +
                ";actual-Q-channel=BoundedPowerSwitchElm;actual-Q-Ron=" + E06ConverterContract.POWER_SWITCH_ON_OHMS +
                ";actual-Q-Roff=" + E06ConverterContract.POWER_SWITCH_OFF_OHMS ) +
                ";actual-L-flags=" + outputInductor.flags + ";actual-C-flags=" + outputCap.flags + "/" + feedbackComp.flags +
                ";actual-opto-model=" + opto.diode.modelName + ";actual-opto-BJT-model=" + opto.transistor.modelName +
                ";actual-zener-model=" + senseZener.modelName;
        }
    }
}
