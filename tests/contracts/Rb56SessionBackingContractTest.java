package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Vector;

/** Developer-owned actual backing/digest restart laws; no normal session capture or load qualification. */
public final class Rb56SessionBackingContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        CirSim prior = CircuitElm.sim, priorSingleton = CirSim.theSim;
        String priorDiode = DiodeElm.lastModelName, priorZener = ZenerElm.lastZenerModelName;
        String priorBjt = TransistorElm.lastModelName;
        int priorFlags = MosfetElm.globalFlags; double priorBeta = MosfetElm.lastBeta;
        Rb56PhysicalOwnerConstruction.Result fixture = null; Throwable failure = null;
        try {
            CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
            CircuitElm.sim = sim; CirSim.theSim = sim;
            fixture = Rb56PhysicalOwnerConstruction.build(Rb56Plan.reference(77), new UnqualifiedBehavior());
            check(fixture.owner.isDeveloperOnlyFaultRoute(), "fixture remains explicitly developer owned");
            for (String id : new String[] {"CBULK", "COUT", "CBIAS", "CFB", "CIN", "C5", "L1", "DZ1", "UFB", "F1", "UAC"}) {
                PhysicalPart<?> part = fixture.runtime.getInstalledPart(id);
                check(PlayerSessionState.hasTypedPowerSessionBinding(Rb56Plan.FAMILY_ID, part), "actual typed part selected");
                for (String family : PlayerFamilyCatalog.registeredFamilies())
                    if (!Rb56Plan.FAMILY_ID.equals(family))
                        check(!PlayerSessionState.hasTypedPowerSessionBinding(family, part),
                            "old normal family does not append any typed model fields");
                String before = definition(part);
                Vector<CircuitElm> backing = part.getElectricalBacking().getCircuitElements();
                for (CircuitElm element : backing) {
                    check(fixture.owner.ownsRuntimeSimulationElement(element), "each typed element belongs to its actual owner");
                    translate(element, 1232, -848);
                }
                check(before.equals(definition(part)), "whole island translation does not change session model identity");
                for (CircuitElm element : backing) translate(element, -1232, 848);
                check(before.equals(definition(part)), "translation reading neither moves nor replaces package backing");
            }
            capacitor((PhysicalCapacitorPart)fixture.runtime.getInstalledPart("COUT"));
            inductor((PhysicalServicePart)fixture.runtime.getInstalledPart("L1"));
            zener((PhysicalServicePart)fixture.runtime.getInstalledPart("DZ1"));
            optocoupler((PhysicalServicePart)fixture.runtime.getInstalledPart("UFB"));
            fuse((PhysicalServicePart)fixture.runtime.getInstalledPart("F1"));
            converter((PhysicalConverterPart)fixture.runtime.getInstalledPart("UAC"));
            System.out.println("PASS: RB56 session backing contracts assertions=" + assertions +
                " scope=DEVELOPER_MODEL_DIGEST_AND_RESTART_ONLY normalSessionLoad=false");
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } catch (Exception error) { failure = error; throw error;
        } finally {
            Throwable cleanup = null;
            try {
                if (fixture != null) {
                    try { fixture.assembly.power.clearForAbortedConstruction(fixture.assembly.board); }
                    catch (Throwable error) { cleanup = error; }
                    for (CircuitElm element : fixture.owner.getSimulationElements()) try { element.delete(); }
                    catch (Throwable error) { if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                }
            } finally {
                DiodeElm.lastModelName = priorDiode; ZenerElm.lastZenerModelName = priorZener;
                TransistorElm.lastModelName = priorBjt; MosfetElm.globalFlags = priorFlags; MosfetElm.lastBeta = priorBeta;
                CircuitElm.sim = prior; CirSim.theSim = priorSingleton;
            }
            System.out.println("RB56_SESSION_BACKING_CLEANUP clean=" + (cleanup == null));
            if (cleanup != null) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw new AssertionError("Session backing fixture cleanup failed", cleanup);
            }
        }
    }

    private static void capacitor(PhysicalCapacitorPart part) {
        CapacitorElm cap = part.getElement(); ResistorElm esr = part.getEsrElement();
        check(esr != null && part.getElectricalBacking().getCircuitElements().size() == 2,
            "actual COUT package owns its ordinary capacitor and ESR");
        String before = definition(part);
        cap.voltdiff = 7.25; cap.current = .02; cap.curSourceValue = .01; cap.compResistance = 123;
        cap.volts[0] = 8; cap.volts[1] = .75;
        check(before.equals(definition(part)), "actual capacitor charge/current/companion state is restart state");
        cap.reset();
        check(cap.voltdiff == part.getSpecification().getInitialVoltage() && cap.current == 0 && cap.curSourceValue == 0,
            "ordinary capacitor reset restarts at its typed initial voltage");
        esr.resistance += .01; changed(part, before, "owned ESR parameter changes digest"); esr.resistance -= .01;
        // Restore from the immutable declared value, avoiding roundoff in the test fixture.
        esr.resistance = part.getSpecification().getEsrOhms();
        cap.flags ^= CapacitorElm.FLAG_BACK_EULER;
        changed(part, before, "capacitor integration changes digest"); cap.flags ^= CapacitorElm.FLAG_BACK_EULER;
        cap.initialVoltage = .125; changed(part, before, "initial voltage changes digest");
        cap.initialVoltage = part.getSpecification().getInitialVoltage();
        translate(esr, 16, 0); changed(part, before, "broken internal series join changes digest"); translate(esr, -16, 0);
        check(before.equals(definition(part)), "restored capacitor recipe has the same model identity");
    }

    private static void inductor(PhysicalServicePart part) {
        InductorElm winding = (InductorElm)part.primary(); String before = definition(part);
        winding.current = winding.ind.current = .002; winding.ind.curSourceValue = .003;
        winding.ind.compResistance = 23; winding.volts[0] = 5; winding.volts[1] = 4;
        check(before.equals(definition(part)), "both actual winding currents and companion state are restart state");
        winding.reset();
        check(winding.current == 0 && winding.ind.current == 0 && winding.ind.curSourceValue == 0,
            "ordinary winding reset clears both real current holders");
        double declared = winding.ind.inductance; winding.ind.inductance *= 1.1;
        changed(part, before, "hidden actual L setup changes digest"); winding.ind.inductance = declared;
        ResistorElm copper = (ResistorElm)part.secondary(); copper.resistance = .6;
        changed(part, before, "owned winding resistance changes digest"); copper.resistance = .5;
        translate(copper, 16, 0); changed(part, before, "broken L/Rw internal join changes digest"); translate(copper, -16, 0);
        check(before.equals(definition(part)), "restored winding has the same model identity");
    }

    private static void zener(PhysicalServicePart part) {
        ZenerElm value = (ZenerElm)part.primary(); String before = definition(part);
        value.current = .003; value.volts[0] = 12; value.volts[1] = 1.3; value.diode.lastvoltdiff = 10.7;
        check(before.equals(definition(part)), "Zener solved and Newton state is excluded");
        double leakage = value.diode.leakage; value.diode.leakage *= 1.1;
        changed(part, before, "actual hidden junction setup changes digest"); value.diode.leakage = leakage;
        value.reset(); check(before.equals(definition(part)), "Zener reset preserves fixed junction law");
    }

    private static void optocoupler(PhysicalServicePart part) {
        OptocouplerElm value = (OptocouplerElm)part.primary(); String before = definition(part);
        CCCSElm transfer = (CCCSElm)value.compElmList.get(1);
        for (CircuitElm element : value.compElmList) {
            element.current = .001;
            for (int p = 0; p < element.volts.length; p++) element.volts[p] = .25 * p;
        }
        value.diode.diode.lastvoltdiff = .65; value.transistor.lastvbe = .5; value.transistor.lastvbc = -.3;
        value.transistor.gmin = 1e-12; transfer.lastCurrents[0] = .002; transfer.exprState.t = .2;
        check(before.equals(definition(part)), "opto internal solved/iteration/expression time state is excluded");
        double beta = value.transistor.beta; value.transistor.beta += 1;
        changed(part, before, "actual opaque BJT recipe changes digest"); value.transistor.beta = beta;
        String expression = transfer.exprString; transfer.setExpr("i * .5");
        changed(part, before, "actual opaque current-transfer recipe changes digest"); transfer.setExpr(expression);
        value.reset(); check(before.equals(definition(part)), "opto reset preserves its audited fixed law");
    }

    private static void fuse(PhysicalServicePart part) {
        ProtectionFuseElm value = (ProtectionFuseElm)part.primary(); String before = definition(part);
        value.heat = value.i2t / 2; value.blown = false;
        PlayerSessionSave.Fuse partial = new PlayerSessionSave.Fuse(part.getId(), value.heat, value.blown);
        value.reset();
        check(value.heat == partial.heat && value.blown == partial.blown,
            "ordinary fuse reset preserves wear keyed to its exact physical identity");
        check(before.equals(definition(part)), "partial wear belongs to existing FUSES records, not model definition");
        value.heat = value.i2t * 1.5; value.blown = true;
        PlayerSessionSave.Fuse blown = new PlayerSessionSave.Fuse(part.getId(), value.heat, value.blown);
        value.reset();
        check(value.heat == blown.heat && value.blown == blown.blown && blown.partId.equals(part.getId()),
            "blown wear survives transient reset with exact physical record ownership");
        check(before.equals(definition(part)), "blown state is excluded from static typed model definition");
        double threshold = value.i2t; value.i2t *= 2;
        changed(part, before, "actual physical fuse threshold changes digest"); value.i2t = threshold;
        value.heat = 0; value.blown = false;
    }

    private static void converter(PhysicalConverterPart part) throws Exception {
        IsolatedConverterModuleModel module = part.getModule(); String before = definition(part);
        check(before.indexOf("e06-averaged") >= 0 && before.indexOf("e06-bias") >= 0 &&
            before.indexOf(module.converter.getContract().canonical()) >= 0 &&
            before.indexOf(E06PwmControllerElm.BiasElm.CONVERGENCE_POLICY) >= 0,
            "actual 458/459 fixed law version and bias convergence policy enter typed identity");
        for (String field : new String[] {"duty", "acceptedDuty", "integral", "acceptedIntegral", "activeSeconds", "acceptedActiveSeconds"})
            set(module.converter, field, Double.valueOf(.2));
        set(module.converter, "active", Boolean.TRUE); set(module.converter, "acceptedActive", Boolean.TRUE);
        set(module.converter, "cycleIndex", Long.valueOf(200)); set(module.converter, "acceptedCycleIndex", Long.valueOf(200));
        set(module.converter, "acceptedInputCurrent", Double.valueOf(.003));
        set(module.bias, "acceptedInput", Double.valueOf(.004)); set(module.bias, "acceptedOutput", Double.valueOf(.004));
        set(module.bias, "appliedTargetGain", Double.valueOf(.01)); set(module.bias, "tangentReady", Boolean.TRUE);
        check(before.equals(definition(part)), "actual E06 accepted controller/tangent/current state is excluded");
        for (CircuitElm element : module.elements()) element.reset();
        check(module.converter.getDuty() == 0 && module.converter.getIntegral() == 0 &&
            module.converter.getActiveSeconds() == 0 && !module.converter.isActive() &&
            module.bias.getInputCurrent() == 0 && module.bias.getOutputCurrent() == 0,
            "actual E06 controller and bias restart without a continuation claim");
        WireElm inputLink = (WireElm)module.elements().get(2);
        translate(inputLink, 16, 0); changed(part, before, "whole opaque package internal input topology enters digest");
        translate(inputLink, -16, 0);
        check(before.equals(definition(part)), "restored complete module has identical static identity");
    }

    private static String definition(PhysicalPart<?> part) { return PlayerSessionState.typedPowerBackingDefinition(part); }
    private static void changed(PhysicalPart<?> part, String previous, String message) { check(!previous.equals(definition(part)), message); }
    private static void translate(CircuitElm element, int x, int y) {
        element.x += x; element.y += y; element.x2 += x; element.y2 += y; element.setPoints();
    }
    private static void set(Object value, String name, Object fieldValue) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); field.set(value, fieldValue);
    }
    private static void check(boolean result, String message) { assertions++; if (!result) throw new AssertionError(message); }
    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { throw new UnsupportedOperationException("No customer qualification in digest contract"); }
        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state) { throw new UnsupportedOperationException("No selected fault in digest contract"); }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL; }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
    }
}
