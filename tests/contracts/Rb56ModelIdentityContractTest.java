package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Vector;

/** Actual factory codecs and audited model identity; no solver-state save promise. */
public final class Rb56ModelIdentityContractTest {
    private static int assertions;
    private static final Vector<CircuitElm> owned = new Vector<CircuitElm>();

    public static void main(String[] args) throws Exception {
        CirSim oldSim = CircuitElm.sim, oldSingleton = CirSim.theSim;
        String oldDiode = DiodeElm.lastModelName, oldBjt = TransistorElm.lastModelName;
        String oldZener = ZenerElm.lastZenerModelName;
        try {
            CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
            CircuitElm.sim = sim; CirSim.theSim = sim;
            DiodeElm.lastModelName = TransistorElm.lastModelName = "default";
            ZenerElm.lastZenerModelName = "default-zener";
            converterCodecs();
            inductorIdentity();
            zenerIdentity();
            optocouplerIdentity();
            existingCanonicalBytes();
            System.out.println("PASS: RB56 model-identity contracts assertions=" + assertions);
        } finally {
            for (CircuitElm element : owned) element.delete();
            CircuitElm.sim = oldSim; CirSim.theSim = oldSingleton;
            DiodeElm.lastModelName = oldDiode; TransistorElm.lastModelName = oldBjt;
            ZenerElm.lastZenerModelName = oldZener;
        }
    }

    private static void converterCodecs() throws Exception {
        E06AveragedConverterElm averaged = own(new E06AveragedConverterElm(160, 224, new E06ConverterContract()));
        E06PwmControllerElm.BiasElm bias = own(new E06PwmControllerElm.BiasElm(400, 224));
        for (CircuitElm element : new CircuitElm[] { averaged, bias }) {
            element.x2 += 16; element.y2 += 16; element.flags = 4; element.setPoints();
            String freshDump = element.dump(), freshIdentity = stable(element);
            if (element == bias) require(freshIdentity.indexOf(
                E06PwmControllerElm.BiasElm.CONVERGENCE_POLICY) >= 0,
                "Fixed numerical convergence policy is part of the bias model identity");
            for (int n = 0; n < element.volts.length; n++) element.volts[n] = n * .7;
            element.current = .017;
            if (element == averaged) {
                set(averaged, "active", true); set(averaged, "acceptedActive", true);
                set(averaged, "duty", .25); set(averaged, "acceptedDuty", .25);
                set(averaged, "integral", .02); set(averaged, "acceptedIntegral", .02);
                set(averaged, "activeSeconds", .1); set(averaged, "acceptedActiveSeconds", .1);
                set(averaged, "cycleIndex", 200L); set(averaged, "acceptedCycleIndex", 200L);
                set(averaged, "acceptedInputCurrent", .017); set(averaged, "receiptReady", true);
                set(averaged, "tangentReady", true);
            } else {
                set(bias, "acceptedInput", .01); set(bias, "acceptedOutput", .01);
                set(bias, "appliedTargetGain", .01); set(bias, "tangentReady", true);
            }
            require(freshDump.equals(element.dump()), "Codec excludes controller/accepted solver state");
            require(freshIdentity.equals(stable(element)), "Controller and tangent state are not model identity");
            CircuitElm loaded = own(load(element.dump()));
            require(loaded.getClass() == element.getClass(), "Actual numeric factory resolves the exact element class");
            require(freshDump.equals(loaded.dump()), "Actual factory round trip preserves complete current dump");
            require(freshIdentity.equals(stable(loaded)), "Round trip preserves stable model definition");
            require(loaded.flags == element.flags && loaded.getPostCount() == element.getPostCount(),
                "Factory retains flags and terminal inventory");
            for (int n = 0; n < element.getPostCount(); n++) {
                Point a = element.getPost(n), b = loaded.getPost(n);
                require(a.x == b.x && a.y == b.y, "Factory preserves each terminal coordinate");
                require(loaded.volts[n] == 0, "Load creates fresh terminal solver state");
            }
            if (loaded instanceof E06AveragedConverterElm) {
                E06AveragedConverterElm value = (E06AveragedConverterElm) loaded;
                require(!value.isActive() && value.getDuty() == 0 && value.getIntegral() == 0 &&
                    value.getActiveSeconds() == 0 && ((Long) get(value, "acceptedCycleIndex")) == 0,
                    "U06 reload restarts cycle/controller state");
                require(value.getContract().canonical().equals(averaged.getContract().canonical()),
                    "Factory retains the fixed E06 law");
            } else {
                E06PwmControllerElm.BiasElm value = (E06PwmControllerElm.BiasElm) loaded;
                require(value.getInputCurrent() == 0 && value.getOutputCurrent() == 0,
                    "Bias reload starts with no accepted current");
            }
            element.x += 16;
            require(!freshIdentity.equals(stable(element)), "Terminal connection geometry is a dependency");
            element.x -= 16;
            String kind = element == averaged ? "e06-averaged" : "e06-bias";
            String other = element == averaged ? "e06-bias" : "e06-averaged";
            for (String payload : new String[] { "", "1", "0 " + kind, "2 " + kind, "01 " + kind,
                    "+1 " + kind, "1.0 " + kind, "NaN " + kind, "1 " + other,
                    "1 " + kind + " extra", "1 " + kind + " NaN" })
                rejectedCodec(element.getDumpType(), new StringTokenizer(payload));
            rejectedCodec(element.getDumpType(), null);
        }
        require(averaged.getDumpType() == 458 && bias.getDumpType() == 459,
            "New models use their distinct audited numeric types");
    }

    private static void inductorIdentity() throws Exception {
        InductorElm value = own(new InductorElm(32, 48)); value.x2 = 96; value.setPoints();
        value.setInductance(.02);
        String initial = stable(value), initialDump = value.dump();
        value.current = value.ind.current = .4; value.ind.curSourceValue = .3;
        value.ind.compResistance = 800; value.volts[0] = 11; value.volts[1] = 10;
        require(!initialDump.equals(value.dump()), "Inductor raw dump really contains live current");
        require(initial.equals(stable(value)), "Inductor current, voltage and companion state are transient");
        value.setInductance(.021);
        require(!initial.equals(stable(value)), "Inductance changes model identity");
        value.setInductance(.02); value.flags |= Inductor.FLAG_BACK_EULER;
        value.ind.setup(value.inductance, value.current, value.flags);
        require(!initial.equals(stable(value)), "Integration method changes model identity");
        value.flags = 0; value.ind.setup(value.inductance, value.current, value.flags);
        value.ind.inductance = .022;
        require(!initial.equals(stable(value)), "Actual hidden inductor setup cannot bypass identity");
        value.ind.inductance = value.inductance; value.x2 += 16;
        require(!initial.equals(stable(value)), "Inductor connection changes model identity");
    }

    private static void zenerIdentity() throws Exception {
        ZenerElm value = own(new ZenerElm(32, 96)); value.x2 = 96; value.setPoints();
        String initial = stable(value), dump = value.dump();
        value.volts[0] = 12; value.current = .01; value.diode.lastvoltdiff = 10;
        require(initial.equals(stable(value)), "Junction trial voltage and accepted current are transient");
        DiodeModel original = value.model;
        DiodeModel copy = new DiodeModel(original); copy.name = original.name; value.model = copy;
        for (String field : new String[] { "saturationCurrent", "seriesResistance", "emissionCoefficient", "breakdownVoltage" }) {
            Field parameter = DiodeModel.class.getDeclaredField(field); parameter.setAccessible(true);
            double before = parameter.getDouble(copy); parameter.setDouble(copy, before * 1.01 + 1e-12);
            require(dump.equals(value.dump()), "Same-name Zener parameter mutation is absent from its raw dump");
            require(!initial.equals(stable(value)), "Every physical diode-model parameter is retained: " + field);
            parameter.setDouble(copy, before);
        }
        copy.dumped = true; stable(value);
        require(copy.dumped, "Identity reading preserves model dumped flag");
        value.model = original; value.diode.leakage *= 1.01;
        require(!initial.equals(stable(value)), "Actual junction setup mutation cannot hide behind a model name");
        value.diode.setup(original); value.modelName = "other-junction";
        require(!initial.equals(stable(value)), "Junction model selection changes identity");
    }

    private static void optocouplerIdentity() throws Exception {
        OptocouplerElm value = own(new OptocouplerElm(160, 384)); value.x2 = 256; value.setPoints();
        String initial = stable(value), dump = value.dump();
        CCCSElm transfer = (CCCSElm) value.compElmList.get(1);
        for (CircuitElm element : value.compElmList) {
            for (int n = 0; n < element.volts.length; n++) element.volts[n] = .3 * n;
            element.current = .001;
        }
        value.diode.diode.lastvoltdiff = .6;
        value.transistor.lastvbe = .5; value.transistor.lastvbc = -.2; value.transistor.gmin = 1e-12;
        transfer.lastCurrents[0] = .002; transfer.exprState.values[0] = .001;
        transfer.exprState.t = .2; transfer.pins[1].current = .003;
        require(initial.equals(stable(value)), "Optocoupler Newton/cycle/current history is transient");
        value.transistor.beta += 1;
        hiddenMutation(value, dump, initial, "BJT beta"); value.transistor.beta -= 1;
        TransistorModel original = value.transistor.model;
        TransistorModel copy = new TransistorModel(original); copy.name = original.name; value.transistor.model = copy;
        for (String field : new String[] { "satCur", "invRollOffF", "BEleakCur", "leakBEemissionCoeff",
                "invRollOffR", "BCleakCur", "leakBCemissionCoeff", "emissionCoeffF", "emissionCoeffR",
                "invEarlyVoltF", "invEarlyVoltR", "betaR" }) {
            Field parameter = TransistorModel.class.getDeclaredField(field); parameter.setAccessible(true);
            double before = parameter.getDouble(copy); parameter.setDouble(copy, before * 1.01 + 1e-12);
            hiddenMutation(value, dump, initial, "BJT parameter " + field); parameter.setDouble(copy, before);
        }
        value.transistor.model = original;
        value.diode.diode.leakage *= 1.01;
        hiddenMutation(value, dump, initial, "actual diode setup"); value.diode.diode.setup(value.diode.model);
        String expression = transfer.exprString; transfer.setExpr("i * .5");
        hiddenMutation(value, dump, initial, "CCCS transfer expression"); transfer.setExpr(expression);
        int type = transfer.expr.type; transfer.expr.type = Expr.E_VAL;
        hiddenMutation(value, dump, initial, "actual expression tree"); transfer.expr.type = type;
        Collections.swap(value.compNodeList, 0, 1);
        hiddenMutation(value, dump, initial, "external/local topology"); Collections.swap(value.compNodeList, 0, 1);
        Collections.swap(value.compNodeList, 4, 5);
        require(initial.equals(stable(value)), "Internal node enumeration order is not a connection change");
        Collections.swap(value.compNodeList, 4, 5);
        CircuitNode node = value.compNodeList.get(2); Collections.reverse(node.links);
        require(initial.equals(stable(value)), "Link enumeration order is not a connection change");
        Collections.reverse(node.links);
        int number = node.links.get(0).num; node.links.get(0).num = 99;
        rejectedIdentity(value, "Foreign/malformed local link fails closed"); node.links.get(0).num = number;
        CircuitElm diode = value.compElmList.get(0); value.compElmList.set(0, value.transistor);
        rejectedIdentity(value, "Different actual optocoupler internals fail closed"); value.compElmList.set(0, diode);
        require(initial.equals(stable(value)), "Restored actual internals recover the original definition");
    }

    private static void existingCanonicalBytes() throws Exception {
        require("tsj-generation-dependencies-v16".equals(GenerationDependencyContext.INTERPRETATION_EPOCH) &&
            "circuitjs-source-load-model-inputs-no-transient-dump-v6".equals(GenerationDependencyContext.CIRCUIT_DUMP_EPOCH),
            "Additive unsupported-type policies preserve existing Q30 epochs");
        ResistorElm resistor = own(new ResistorElm(16, 32)); resistor.x2 = 80; resistor.resistance = 100;
        CapacitorElm capacitor = own(new CapacitorElm(16, 64)); capacitor.x2 = 80;
        capacitor.capacitance = .001; capacitor.voltdiff = 8; capacitor.initialVoltage = .002;
        require("r 16 32 80 32 0 100.0".equals(stable(resistor)), "Existing resistor canonical bytes unchanged");
        require("c 16 64 80 64 0 0.001 0.002".equals(stable(capacitor)), "Existing capacitor canonical bytes unchanged");
        Vector<CircuitElm> values = new Vector<CircuitElm>(); values.add(resistor); values.add(capacitor);
        Method method = GenerationDependencyContext.class.getDeclaredMethod("captureElementRecords", Vector.class);
        method.setAccessible(true);
        Vector<String> expected = new Vector<String>();
        expected.add("N;" + frame("r 16 32 80 32 0 100.0"));
        expected.add("N;" + frame("c 16 64 80 64 0 0.001 0.002")); Collections.sort(expected);
        require(expected.equals(method.invoke(null, values)), "Existing batch canonical framing unchanged");
    }

    private static CircuitElm load(String dump) {
        StringTokenizer tokens = new StringTokenizer(dump);
        int type = Integer.parseInt(tokens.nextToken());
        int x = Integer.parseInt(tokens.nextToken()), y = Integer.parseInt(tokens.nextToken());
        int x2 = Integer.parseInt(tokens.nextToken()), y2 = Integer.parseInt(tokens.nextToken());
        int flags = Integer.parseInt(tokens.nextToken());
        return CirSim.createCe(type, x, y, x2, y2, flags, tokens);
    }
    private static void rejectedCodec(int type, StringTokenizer tokens) {
        try { CirSim.createCe(type, 32, 48, 192, 48, 0, tokens); throw new AssertionError("Malformed codec accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static String stable(CircuitElm element) throws Exception {
        Method method = GenerationDependencyContext.class.getDeclaredMethod("stableElementDump", CircuitElm.class);
        method.setAccessible(true);
        try { return (String) method.invoke(null, element); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw failure;
        }
    }
    private static void rejectedIdentity(CircuitElm element, String message) throws Exception {
        try { stable(element); throw new AssertionError(message); }
        catch (IllegalStateException expected) { assertions++; }
    }
    private static void hiddenMutation(OptocouplerElm element, String raw, String identity, String kind) throws Exception {
        require(raw.equals(element.dump()), "407 raw dump hides " + kind);
        require(!identity.equals(stable(element)), "Actual 407 identity retains " + kind);
    }
    private static <T extends CircuitElm> T own(T element) { owned.add(element); return element; }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static String frame(String value) { return "V" + value.length() + ":" + value + ";"; }
    private static void require(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
