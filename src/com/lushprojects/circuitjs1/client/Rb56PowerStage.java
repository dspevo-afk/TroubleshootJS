package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Reusable RB56 power graph. Package ownership and board leads are declared by its generator. */
final class Rb56PowerStage {
    static final double PRIMARY_FARADS = .000047, PRIMARY_BLEED_OHMS = 4700, PRIMARY_BLEED_MIN_WATTS = 10;
    static final double ENABLE_PULLUP_OHMS = 10000, BIAS_FARADS = .000001;
    static final double OUTPUT_BLEED_OHMS = 1000, OUTPUT_BLEED_MIN_WATTS = .25;
    static final double MAX_TIME_STEP_SECONDS = E06ConverterContract.MAX_AVERAGED_STEP_SECONDS;
    // The primary bleeder dissipates up to about 6.2 W at the declared sine peak.
    // Its future physical specification/catalog must enforce the 10 W obligation.
    final E05AcInputModel.Input input;
    final ProtectionFuseElm fuse;
    final DiodeElm[] bridge = new DiodeElm[4];
    final CapacitorElm bulk, biasCap, outputCap, feedbackComp;
    final ResistorElm primaryBleed, enablePullup, windingResistance, capacitorEsr;
    final ResistorElm outputBleed, senseResistor, feedbackPullup;
    final IsolatedConverterModuleModel module;
    final WireElm enableLead;
    final WireElm[] moduleLeads = new WireElm[7];
    final DiodeElm freewheel;
    final InductorElm outputInductor;
    final ZenerElm senseZener;
    final OptocouplerElm opto;
    final Point line, returned, inPlus, inMinus, preLPlus, outMinus, outputPlus;
    final Point enablePoint, feedbackPoint, biasPoint;
    private final Vector<CircuitElm> board = new Vector<CircuitElm>();
    private final int originX, originY;

    Rb56PowerStage(int x, int y) {
        originX = x; originY = y;
        line = point(64, 96); returned = point(64, 256);
        inPlus = point(384, 96); inMinus = point(384, 256);
        preLPlus = point(1248, 160); outMinus = point(1152, 288); outputPlus = point(1536, 160);
        enablePoint = point(800, 416); feedbackPoint = point(768, 656); biasPoint = point(1152, 416);
        input = E05AcInputModel.create(x - 256, y + 96, line, returned);
        boolean complete = false;
        try {
            Point fused = point(192, 96);
            fuse = E05AcInputModel.fuse(line, fused); board.add(fuse);
            bridge[0] = diode(fused, inPlus); bridge[1] = diode(returned, inPlus);
            bridge[2] = diode(inMinus, fused); bridge[3] = diode(inMinus, returned);
            bulk = capacitor(inPlus, inMinus, PRIMARY_FARADS);
            primaryBleed = resistor(point(640, 96), point(640, 256), PRIMARY_BLEED_OHMS);
            wire(inPlus, primaryBleed.getPost(0)); wire(inMinus, primaryBleed.getPost(1));

            // The whole bundle remains on its service island. Only these seven leads
            // join its terminals to persistent board anchors; no module relocation.
            module = new IsolatedConverterModuleModel(x + 32768, y + 32768);
            board.addAll(module.elements());
            Point[] anchors = {inPlus, inMinus, preLPlus, outMinus, enablePoint, feedbackPoint, biasPoint};
            for (int n = 0; n < moduleLeads.length; n++)
                moduleLeads[n] = wire(anchors[n], module.terminalPoint(n));
            enableLead = moduleLeads[E06ConverterContract.ENABLE];
            enablePullup = resistor(biasPoint, enablePoint, ENABLE_PULLUP_OHMS);
            biasCap = capacitor(point(1152, 480), point(1152, 640), BIAS_FARADS);
            wire(biasPoint, biasCap.getPost(0)); wire(inMinus, biasCap.getPost(1));

            freewheel = diode(outMinus, preLPlus);
            outputInductor = end(new InductorElm(x + 1248, y + 160), point(1376, 160));
            outputInductor.inductance = E06ConverterContract.OUTPUT_HENRIES;
            outputInductor.flags &= ~Inductor.FLAG_BACK_EULER;
            outputInductor.ind.setup(outputInductor.inductance, 0, outputInductor.flags);
            board.add(outputInductor);
            windingResistance = resistor(outputInductor.getPost(1), outputPlus, E06ConverterContract.WINDING_OHMS);
            Point capTop = point(1536, 224);
            capacitorEsr = resistor(outputPlus, capTop, E06ConverterContract.ESR_OHMS);
            outputCap = capacitor(capTop, point(1536, 384), E06ConverterContract.OUTPUT_FARADS);
            wire(outputCap.getPost(1), outMinus);
            // A real discharge path is required even when every downstream load is absent.
            outputBleed = resistor(point(1792, 160), point(1792, 384), OUTPUT_BLEED_OHMS);
            wire(outputPlus, outputBleed.getPost(0)); wire(outMinus, outputBleed.getPost(1));

            opto = optocoupler(x + 1600, y + 512); opto.setPoints(); board.add(opto);
            senseResistor = resistor(point(1472, 480), point(1472, 544), E06ConverterContract.SENSE_OHMS);
            wire(outputPlus, senseResistor.getPost(0));
            senseZener = end(new ZenerElm(x + 1472, y + 576), point(1472, 544));
            senseZener.model = DiodeModel.getModelWithParameters(.805904783, E06ConverterContract.ZENER_VOLTS);
            senseZener.modelName = senseZener.model.name; senseZener.setup(); board.add(senseZener);
            wire(senseZener.getPost(0), opto.getPost(0)); wire(opto.getPost(1), outMinus);
            Point feedback = feedbackPoint;
            feedbackPullup = resistor(biasPoint, feedback, E06ConverterContract.PULLUP_OHMS);
            feedbackComp = capacitor(feedback, point(768, 816), E06ConverterContract.COMPENSATION_FARADS);
            wire(feedbackComp.getPost(1), inMinus);
            wire(opto.getPost(2), feedback); wire(opto.getPost(3), inMinus);
            requireIsolatedModulePosts();
            complete = true;
        } finally {
            if (!complete) {
                for (CircuitElm element : board) element.delete();
                for (CircuitElm element : input.elements) element.delete();
            }
        }
    }

    /** Fresh vectors retain the actual owned elements, never a second electrical graph. */
    Vector<CircuitElm> boardElements() { return new Vector<CircuitElm>(board); }
    Vector<CircuitElm> elements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>(input.elements);
        result.addAll(board); return result;
    }

    /** Internal posts may meet only their own backing and the declared lead post1. */
    private void requireIsolatedModulePosts() {
        Vector<CircuitElm> internal = module.elements();
        Vector<CircuitElm> other = elements();
        for (CircuitElm owned : internal) for (int a = 0; a < owned.getPostCount(); a++) {
            Point at = owned.getPost(a);
            for (CircuitElm e : other) {
                if (internal.contains(e)) continue;
                boolean lead = false;
                for (WireElm candidate : moduleLeads) if (candidate == e) lead = true;
                for (int b = 0; b < e.getPostCount(); b++) {
                    if (lead && b == 1) continue;
                    Point external = e.getPost(b);
                    if (at.x == external.x && at.y == external.y)
                        throw new IllegalArgumentException("Module service post collides with board/source backing");
                }
            }
        }
    }
    private Point point(int x, int y) { return new Point(originX + x, originY + y); }
    private WireElm wire(Point from, Point to) {
        WireElm result = end(new WireElm(from.x, from.y), to); board.add(result); return result;
    }
    private ResistorElm resistor(Point from, Point to, double ohms) {
        ResistorElm result = end(new ResistorElm(from.x, from.y), to);
        result.setResistance(ohms); board.add(result); return result;
    }
    private DiodeElm diode(Point from, Point to) {
        DiodeElm result = end(new DiodeElm(from.x, from.y), to);
        result.modelName = "default"; result.model = null; result.setup(); board.add(result); return result;
    }
    private CapacitorElm capacitor(Point from, Point to, double farads) {
        CapacitorElm result = end(new CapacitorElm(from.x, from.y), to);
        result.capacitance = farads; result.initialVoltage = 0;
        result.flags |= CapacitorElm.FLAG_BACK_EULER; result.reset(); board.add(result); return result;
    }
    private static OptocouplerElm optocoupler(int x, int y) {
        String diode = DiodeElm.lastModelName, transistor = TransistorElm.lastModelName;
        try {
            DiodeElm.lastModelName = "default"; TransistorElm.lastModelName = "default";
            return new OptocouplerElm(x, y);
        } finally { DiodeElm.lastModelName = diode; TransistorElm.lastModelName = transistor; }
    }
    private static <T extends CircuitElm> T end(T element, Point to) {
        element.x2 = to.x; element.y2 = to.y; element.setPoints(); return element;
    }
}