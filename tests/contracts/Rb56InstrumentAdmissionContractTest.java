package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Vector;

/** Actual instrument boundary only; no player geometry, fault or diagnostic admission is certified. */
public final class Rb56InstrumentAdmissionContractTest {
    private static final double DT = .00005;
    private static final InstrumentMode RESISTANCE = new InstrumentMode("RESISTANCE");
    private static final InstrumentMode CONTINUITY = new InstrumentMode("CONTINUITY");
    private static final InstrumentMode DIODE = new InstrumentMode("DIODE");
    private static int assertions, activeOrientations, passiveOrientations;
    public static void main(String[] args) throws Exception {
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        String diode = DiodeElm.lastModelName, zener = ZenerElm.lastZenerModelName, transistor = TransistorElm.lastModelName;
        int flags = MosfetElm.globalFlags; double beta = MosfetElm.lastBeta;
        NativeMeterCirSim sim = new NativeMeterCirSim(); Vector<CircuitElm> original = sim.elmList;
        Rb56PhysicalOwnerConstruction.Result f = null; Throwable failure = null;
        String phase = "CONSTRUCTION"; CircuitElm.sim = sim;
        try {
            f = Rb56PhysicalOwnerConstruction.build(Rb56Plan.reference(77), new UnqualifiedBehavior());
            check(f.runtime.getCapability(Rb56InstrumentAdmissionCapability.ID) instanceof Rb56InstrumentAdmissionCapability,
                "actual helper registers instrument policy before owner publication");
            sim.generatedBoardInstance = f.owner; sim.elmList = f.owner.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, f.owner);
            sim.getBoardPowerController().attach(f.owner.getExternalPowerBindings());
            f.runtime.installRegisteredCapabilities(sim, f.owner, sim.boardModificationController, 0); sim.a01MeasurementRunning = true;
            PhysicalResistorPart referenceHigh = (PhysicalResistorPart)f.runtime.getInstalledPart("RREF_H");
            PhysicalResistorPart referenceLow = (PhysicalResistorPart)f.runtime.getInstalledPart("RREF_L");
            CircuitPostMeasurementEndpoint highRail = boardPadEndpoint(f.owner, referenceHigh, 0);
            CircuitPostMeasurementEndpoint highShared = boardPadEndpoint(f.owner, referenceHigh, 1);
            CircuitPostMeasurementEndpoint lowShared = boardPadEndpoint(f.owner, referenceLow, 0);
            CircuitPostMeasurementEndpoint lowReturn = boardPadEndpoint(f.owner, referenceLow, 1);
            check(f.plan.channelCount == 2 && f.plan.referenceArrangement == Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC,
                "fixed physical fixture declares the shared two-channel reference topology");
            check("REF_SHARED".equals(f.owner.getBoard().getPad("RREF_H.2").getNetId()) &&
                f.owner.getBoard().getPad("RREF_H.2").getNetId().equals(
                    f.owner.getBoard().getPad("RREF_L.1").getNetId()),
                "independent board declaration places RREF_H.2 and RREF_L.1 on REF_SHARED");
            phase = "POWERED"; advance(sim, f, .4);
            Quiet powered = new Quiet(sim, f.owner);
            check(!sim.getActiveMeasurementReadiness(lowShared, lowReturn, RESISTANCE).isReady() &&
                Double.isNaN(sim.measureResistance(lowShared, lowReturn, RESISTANCE)),
                "explicit OHM mode remains blocked while the actual RB56 power sources are connected");
            powered.verify(sim, f.owner);
            PhysicalCapacitorPart cap = (PhysicalCapacitorPart)f.runtime.getInstalledPart("CBULK");
            check(Math.abs(cap.getElement().getVoltageDiff()) > .25, "actual powered graph charges bulk storage");
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            f.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED); sim.needAnalyze();
            phase = "CHARGED_OFF"; advance(sim, f, DT);
            CircuitPostMeasurementEndpoint cr = terminal(cap, 0), cb = terminal(cap, 1);
            check(f.runtime.getActiveMeasurementReadiness(cr, cb, BoardPowerState.UNPOWERED, true) == ActiveMeasurementReadiness.DISCHARGE,
                "service-energy API retains fresh solver-observed DISCHARGE independently of injection admission");
            Quiet charged = new Quiet(sim, f.owner);
            check(sim.getActiveMeasurementReadiness(cr, cb) == ActiveMeasurementReadiness.DISCHARGE && Double.isNaN(sim.measureResistance(cr, cb)) &&
                sim.measureDiode(cr, cb) == null &&
                sim.getActiveMeasurementReadiness(cr, cb, RESISTANCE) == ActiveMeasurementReadiness.DISCHARGE &&
                Double.isNaN(sim.measureResistance(cr, cb, RESISTANCE)),
                "charged actual capacitor cannot receive mode-qualified or legacy meter injection"); charged.verify(sim, f.owner);
            PhysicalSlotMutationProvider service = f.runtime.getMutationProvider("UAC");
            check(!service.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null),
                "actual charged OFF module remains unavailable for service");
            phase = "DISCHARGED_OFF"; advance(sim, f, 3);
            PhysicalConverterPart module = (PhysicalConverterPart)f.runtime.getInstalledPart("UAC");
            CircuitPostMeasurementEndpoint input = terminal(module, 0), returned = terminal(module, 1);
            check(f.runtime.getActiveMeasurementReadiness(input, returned, BoardPowerState.UNPOWERED, true).isReady() &&
                sim.getActiveMeasurementReadiness(input, returned) == ActiveMeasurementReadiness.UNSUPPORTED,
                "discharged service readiness does not inherit opaque-module instrument refusal");

            CircuitPostMeasurementEndpoint primaryPad = boardPadEndpoint(f.owner, module, 0);
            CircuitPostMeasurementEndpoint secondaryPad = boardPadEndpoint(f.owner, module, 2);
            String primaryPadId = "UAC." + module.getTerminal(0).getTerminalName();
            String secondaryPadId = "UAC." + module.getTerminal(2).getTerminalName();
            PcbPlacementConstraints placement = f.owner.getBoard().getPlacementConstraints();
            check("PRIMARY".equals(placement.domainForPad(f.owner.getBoard(), primaryPadId)) &&
                "SECONDARY".equals(placement.domainForPad(f.owner.getBoard(), secondaryPadId)),
                "independent terminal-domain declarations identify the UAC board-pad isolation boundary");
            Quiet crossDomain = new Quiet(sim, f.owner);
            check(!sim.getActiveMeasurementReadiness(primaryPad, secondaryPad, RESISTANCE).isReady() &&
                Double.isNaN(sim.measureResistance(primaryPad, secondaryPad, RESISTANCE)),
                "mode-qualified OHM refuses cross-domain UAC board pads before source injection");
            crossDomain.verify(sim, f.owner);

            CircuitPostMeasurementEndpoint primaryResistorPad = boardPadEndpoint(f.owner,
                f.runtime.getInstalledPart("RPRIMARY"), 0);
            Quiet ordinaryCrossDomain = new Quiet(sim, f.owner);
            check(sim.getActiveMeasurementReadiness(primaryResistorPad, lowReturn, RESISTANCE) ==
                    ActiveMeasurementReadiness.ISOLATE_COMPONENT &&
                Double.isNaN(sim.measureResistance(primaryResistorPad, lowReturn, RESISTANCE)),
                "ordinary resistor pads cannot bypass the primary/secondary reference boundary");
            ordinaryCrossDomain.verify(sim, f.owner);

            phase = "INSTALLED_REFERENCE_RESISTORS";
            verifyInstalledBoardResistance(sim, f, referenceHigh, highRail, highShared);
            verifyInstalledBoardResistance(sim, f, referenceLow, lowShared, lowReturn);
            CircuitPostMeasurementEndpoint physicalRed = terminal(referenceLow, 0), physicalBlack = terminal(referenceLow, 1);
            waitForMeasurementReady(sim, f, physicalRed, physicalBlack, RESISTANCE);
            check(sim.getActiveMeasurementReadiness(physicalRed, physicalBlack, RESISTANCE) == ActiveMeasurementReadiness.READY,
                "connected physical part leads resolve their actual installed board references");
            Vector<CircuitElm> physicalGraph = new Vector<CircuitElm>(sim.elmList);
            double physicalOhms = sim.measureResistance(physicalRed, physicalBlack, RESISTANCE);
            check(PowerDomainContract.finite(physicalOhms) && physicalOhms > 0 &&
                sim.elmList.equals(physicalGraph) && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                "physical-lead probing uses the same real network transaction and cleanup as board pads");
            advance(sim, f, DT);
            verifySameNetBoardResistance(sim, f, referenceHigh, 1, referenceLow, 0);
            verifyInstalledMeasurementFailureCleanup(sim, f, referenceLow, lowShared, lowReturn);

            CircuitPostMeasurementEndpoint invalidPost = new CircuitPostMeasurementEndpoint(
                referenceLow.getElement(), referenceLow.getElement().getPostCount());
            Quiet invalid = new Quiet(sim, f.owner);
            check(!sim.getActiveMeasurementReadiness(invalidPost, lowReturn, RESISTANCE).isReady() &&
                Double.isNaN(sim.measureResistance(invalidPost, lowReturn, RESISTANCE)),
                "out-of-range endpoint cannot reach the temporary resistance source");
            invalid.verify(sim, f.owner);

            // A real meter can leave energy in capacitors/coils. Let the live
            // circuit decay before service; never clear or reset those stores.
            int cooldownSteps = 0;
            while (!service.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null) &&
                    cooldownSteps < 100) {
                advance(sim, f, .01); cooldownSteps++;
            }
            System.out.println("RB56_METER_NATURAL_COOLDOWN solverSeconds=" + (cooldownSteps * .01) +
                " powerOff=" + sim.getBoardPowerController().isElectricallyUnpowered());
            check(service.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null) &&
                service.removeInstalledPart(), "real discharged converter removal has no instrument-policy deadlock");
            advance(sim, f, DT); check(!module.isInstalled(), "the exact original module is loose in its inventory");
            phase = "CONVERTER_ACTIVE_PAIRS";
            for (int first = 0; first < 7; first++) for (int second = first + 1; second < 7; second++) {
                denyActive(sim, f.owner, terminal(module, first), terminal(module, second));
                denyActive(sim, f.owner, terminal(module, second), terminal(module, first));
            }
            phase = "CONVERTER_PASSIVE_ISOLATION";
            // Independent model terminal map: five primary pins and two secondary pins.
            for (int primary : new int[] {0, 1, 4, 5, 6}) for (int secondary : new int[] {2, 3}) {
                denyPassive(sim, f.owner, terminal(module, primary), terminal(module, secondary));
                denyPassive(sim, f.owner, terminal(module, secondary), terminal(module, primary));
            }
            phase = "LOOSE_RESISTOR";
            PhysicalResistorPart resistor = (PhysicalResistorPart)f.runtime.getInstalledPart("RPRIMARY");
            check(f.runtime.getMutationProvider("RPRIMARY").removeInstalledPart(), "remove actual discharged 4.7k resistor"); advance(sim, f, DT);
            for (int polarity = 0; polarity < 2; polarity++) {
                CircuitPostMeasurementEndpoint red = terminal(resistor, polarity), black = terminal(resistor, 1 - polarity);
                waitForMeasurementReady(sim, f, red, black, RESISTANCE);
                check(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.READY &&
                    sim.getActiveMeasurementReadiness(red, black, RESISTANCE) == ActiveMeasurementReadiness.READY &&
                    sim.getActiveMeasurementReadiness(red, black, CONTINUITY) == ActiveMeasurementReadiness.READY &&
                    sim.getActiveMeasurementReadiness(red, black, DIODE) == ActiveMeasurementReadiness.READY,
                    "isolated loose resistor supports every real solver-backed meter mode in either polarity");
                Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList); double before = sim.t;
                double ohms = sim.measureResistance(red, black, RESISTANCE);
                check(PowerDomainContract.finite(ohms) && Math.abs(ohms - 4700) < 1 &&
                    Math.abs(Math.abs(sim.getLastResistanceTestCurrentForDeveloperVerification()) - 1.0 / 5700) < 2e-8,
                    "real OHM source returns 4.7k and the independent 1V/(1k+4.7k) current oracle in each polarity");
                check(sim.t > before && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification() && sim.hasElectricallyNeutralResistanceReferenceForDeveloperVerification(),
                    "real OHM source/reference and solver indexes restore after accepted solve");
                GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
                advance(sim, f, DT);

                waitForMeasurementReady(sim, f, red, black, CONTINUITY);
                before = sim.t;
                double continuity = sim.measureResistance(red, black, CONTINUITY);
                check(PowerDomainContract.finite(continuity) && Math.abs(continuity - 4700) < 1 &&
                    Math.abs(Math.abs(sim.getLastResistanceTestCurrentForDeveloperVerification()) - 1.0 / 5700) < 2e-8,
                    "real CONTINUITY source uses the same independent 4.7k network oracle in each polarity");
                check(sim.t > before && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                    "real CONTINUITY transaction restores the exact board solver graph");
                GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
                advance(sim, f, DT);

                waitForMeasurementReady(sim, f, red, black, DIODE);
                before = sim.t;
                DiodeMeasurementResult diodeReading = sim.measureDiode(red, black, DIODE);
                check(diodeReading != null && PowerDomainContract.finite(diodeReading.voltage) &&
                    PowerDomainContract.finite(diodeReading.current) &&
                    Math.abs(diodeReading.voltage - 3.0 * 4700 / 5700) < .005 &&
                    Math.abs(diodeReading.current - 3.0 / 5700) < 2e-6,
                    "real DIODE source returns independent 3V/(1k+4.7k) current and 4.7k divider voltage in each polarity");
                check(sim.t > before && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                    "real DIODE transaction restores the exact board solver graph");
                GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
                advance(sim, f, DT);
            }
            check(activeOrientations == 42 && passiveOrientations == 20, "exact complete independent converter pin-pair census");
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } catch (Exception error) { failure = error; throw error;
        } finally {
            Throwable cleanup = null; boolean disposed = true, overlayClear = !sim.activeMeasurementOverlay;
            try {
                sim.a01MeasurementRunning = false; sim.elmList = original; sim.generatedBoardInstance = null; sim.boardModificationController = null;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); } catch (Throwable error) { cleanup = error; }
                sim.circuitMatrix = null; sim.getBoardPowerController().detach();
                if (f != null) for (CircuitElm element : f.owner.getSimulationElements()) try { element.delete(); }
                catch (Throwable error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
            } finally {
                DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener; TransistorElm.lastModelName = transistor;
                MosfetElm.globalFlags = flags; MosfetElm.lastBeta = beta; CircuitElm.sim = prior; CirSim.theSim = singleton;
            }
            boolean clean = cleanup == null && disposed && overlayClear && original.isEmpty() && sim.elmList == original &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == singleton;
            System.out.println("RB56_INSTRUMENT_CLEANUP {\"phase\":\"" + phase + "\",\"candidateDisposed\":" + disposed + ",\"clean\":" + clean + "}");
            if (failure != null) { if (cleanup != null) failure.addSuppressed(cleanup); if (!clean) failure.addSuppressed(new AssertionError("Instrument fixture cleanup failed")); }
            else if (!clean) throw new AssertionError("Instrument fixture cleanup failed", cleanup);
        }
        System.out.println("PASS: RB56 instrument admission contracts assertions=" + assertions +
            " activePairs=21 activeOrientations=42 passivePairs=10 passiveOrientations=20 realOhm=2 " +
            "installedReferenceTargets=2 sameNet=1 failureStages=2 scope=DEVELOPER_BOUNDARY_ONLY");
    }
    private static void denyActive(NativeMeterCirSim sim, GeneratedBoardInstance owner,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) throws Exception {
        Quiet before = new Quiet(sim, owner);
        check(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.UNSUPPORTED, "opaque module pair explicitly unsupported");
        check(sim.getActiveMeasurementReadiness(red, black, RESISTANCE) == ActiveMeasurementReadiness.UNSUPPORTED &&
            Double.isNaN(sim.measureResistance(red, black, RESISTANCE)) && sim.measureDiode(red, black) == null,
            "opaque module remains unsupported under explicit OHM and diode entry points");
        before.verify(sim, owner); activeOrientations++;
    }
    private static void denyPassive(NativeMeterCirSim sim, GeneratedBoardInstance owner,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) throws Exception {
        Quiet before = new Quiet(sim, owner); MeasurementReferencePolicy.Result reference = sim.assessMeasurementReference(red, black);
        check(reference.getDecision() == MeasurementReferencePolicy.Decision.REJECTED && "ISOLATION_BOUNDARY".equals(reference.getReason()),
            "loose converter cross-domain reference is rejected rather than NOT_APPLICABLE");
        check(sim.measureDcVoltageResult(red, black).getStatus() == VoltageMeasurementResult.Status.REFERENCE_REJECTED &&
            sim.measureAcVoltage(red, black).getStatus() == VoltageMeasurementResult.Status.REFERENCE_REJECTED &&
            sim.observeDifferentialVoltage(red, black) == null, "DC/AC/scope refuse before burden or observation subscription");
        before.verify(sim, owner); passiveOrientations++;
    }
    private static CircuitPostMeasurementEndpoint terminal(PhysicalPart<?> part, int index) {
        return (CircuitPostMeasurementEndpoint)part.getTerminal(index).getEndpoint();
    }
    private static CircuitPostMeasurementEndpoint boardPadEndpoint(GeneratedBoardInstance owner,
            PhysicalPart<?> part, int index) {
        PhysicalPartTerminal physicalTerminal = part.getTerminal(index);
        String padId = part.getId() + "." + physicalTerminal.getTerminalName();
        CircuitMeasurementEndpoint boardEndpoint = owner.getSimulationBindings().getEndpoint(padId);
        GeneratedComponentConnectionBinding connection = owner.getConnectionBindings().get(part.getId(), padId);
        CircuitPostMeasurementEndpoint terminalEndpoint =
            (CircuitPostMeasurementEndpoint)physicalTerminal.getEndpoint();
        check(boardEndpoint instanceof CircuitPostMeasurementEndpoint &&
            GeneratedComponentConnectionBindings.sameEndpoint(
                (CircuitPostMeasurementEndpoint)boardEndpoint, connection.getBoardEndpoint()),
            "board pad resolves its current public probe endpoint " + padId);
        check(GeneratedComponentConnectionBindings.sameEndpoint(
                (CircuitPostMeasurementEndpoint)connection.getComponentEndpoint(), terminalEndpoint),
            "board pad lead resolves the current physical terminal endpoint " + padId);
        return (CircuitPostMeasurementEndpoint)boardEndpoint;
    }
    private static void verifyInstalledBoardResistance(NativeMeterCirSim sim,
            Rb56PhysicalOwnerConstruction.Result f, PhysicalResistorPart part,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) throws Exception {
        waitForMeasurementReady(sim, f, red, black, RESISTANCE);
        String firstPad = part.getId() + "." + part.getTerminal(0).getTerminalName();
        String secondPad = part.getId() + "." + part.getTerminal(1).getTerminalName();
        check(part.isInstalled() && !f.owner.getBoard().getPad(firstPad).getNetId().equals(
                f.owner.getBoard().getPad(secondPad).getNetId()),
            "installed target spans two distinct declared nets " + part.getId());
        ActiveMeasurementReadiness beforeReadiness =
            sim.getActiveMeasurementReadiness(red, black, RESISTANCE);
        check(beforeReadiness == ActiveMeasurementReadiness.READY,
            "settled electrically-off actual board admits explicit OHM for " + part.getId());
        check(sim.getActiveMeasurementReadiness(red, black, CONTINUITY) == ActiveMeasurementReadiness.READY &&
            sim.getActiveMeasurementReadiness(red, black, DIODE) == ActiveMeasurementReadiness.READY,
            "settled electrically-off actual board admits solver-backed CONTINUITY and DIODE for " + part.getId());

        Quiet blocked = new Quiet(sim, f.owner);
        check(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.ISOLATE_COMPONENT &&
            Double.isNaN(sim.measureResistance(red, black)),
            "unspecified OHM remains conservative for installed target " + part.getId());
        blocked.verify(sim, f.owner);

        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        double timeBefore = sim.t;
        double ohms = sim.measureResistance(red, black, RESISTANCE);
        double current = Math.abs(sim.getLastResistanceTestCurrentForDeveloperVerification());
        ActiveMeasurementReadiness afterReadiness =
            sim.getActiveMeasurementReadiness(red, black, RESISTANCE);
        boolean cleanup = !sim.activeMeasurementOverlay &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
            sim.hasElectricallyNeutralResistanceReferenceForDeveloperVerification();
        check(PowerDomainContract.finite(ohms) && ohms > 0 &&
            PowerDomainContract.finite(current) &&
            current >= ResistanceMeasurementStimulus.MINIMUM_TEST_CURRENT,
            "actual installed-network OHM solve returns a finite positive reading/current " + part.getId());
        check(sim.t > timeBefore && sim.elmList.equals(graph) && cleanup,
            "accepted installed-network solve advances real solver time and restores its graph/source " + part.getId());
        GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);

        advance(sim, f, DT);
        waitForMeasurementReady(sim, f, red, black, CONTINUITY);
        timeBefore = sim.t;
        double continuity = sim.measureResistance(red, black, CONTINUITY);
        check(PowerDomainContract.finite(continuity) && continuity > 0 &&
            PowerDomainContract.finite(sim.getLastResistanceTestCurrentForDeveloperVerification()) &&
            Math.abs(sim.getLastResistanceTestCurrentForDeveloperVerification()) >=
                ResistanceMeasurementStimulus.MINIMUM_TEST_CURRENT,
            "actual installed board network yields a finite solver-backed CONTINUITY sample " + part.getId());
        check(sim.t > timeBefore && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "installed CONTINUITY transaction restores the exact solver graph " + part.getId());
        GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);

        advance(sim, f, DT);
        waitForMeasurementReady(sim, f, red, black, DIODE);
        timeBefore = sim.t;
        DiodeMeasurementResult diode = sim.measureDiode(red, black, DIODE);
        check(diode != null && PowerDomainContract.finite(diode.voltage) &&
            PowerDomainContract.finite(diode.current) && diode.current > 0,
            "actual installed board network yields a finite solver-backed DIODE sample " + part.getId());
        check(sim.t > timeBefore && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "installed DIODE transaction restores the exact solver graph " + part.getId());
        GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
        System.out.println("RB56_IN_CIRCUIT_RESISTANCE {\"component\":\"" + part.getId() +
            "\",\"boardPads\":\"" + firstPad + "," + secondPad + "\",\"nominalOhms\":" +
            number(part.getElement().resistance) + ",\"actualOhms\":" + number(ohms) +
            ",\"testCurrentAmps\":" + number(current) + ",\"readinessBefore\":\"" +
            beforeReadiness.name() + "\",\"readinessAfter\":\"" + afterReadiness.name() +
            "\",\"timeBefore\":" + number(timeBefore) + ",\"timeAfter\":" + number(sim.t) +
            ",\"cleanup\":" + cleanup + "}");
        advance(sim, f, DT);
    }
    private static void verifySameNetBoardResistance(NativeMeterCirSim sim,
            Rb56PhysicalOwnerConstruction.Result f, PhysicalPart<?> firstPart, int firstTerminal,
            PhysicalPart<?> secondPart, int secondTerminal) throws Exception {
        String firstPad = firstPart.getId() + "." + firstPart.getTerminal(firstTerminal).getTerminalName();
        String secondPad = secondPart.getId() + "." + secondPart.getTerminal(secondTerminal).getTerminalName();
        check(!firstPart.getId().equals(secondPart.getId()) &&
            f.owner.getBoard().getPad(firstPad).getNetId().equals(f.owner.getBoard().getPad(secondPad).getNetId()),
            "selected probes belong to different physical parts on one declared net");
        CircuitPostMeasurementEndpoint red = boardPadEndpoint(f.owner, firstPart, firstTerminal);
        CircuitPostMeasurementEndpoint black = boardPadEndpoint(f.owner, secondPart, secondTerminal);
        waitForMeasurementReady(sim, f, red, black, RESISTANCE);
        check(sim.getActiveMeasurementReadiness(red, black, RESISTANCE) == ActiveMeasurementReadiness.READY,
            "same-net board pads on different parts are eligible in explicit OHM mode");
        Quiet blocked = new Quiet(sim, f.owner);
        check(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.ISOLATE_COMPONENT &&
            Double.isNaN(sim.measureResistance(red, black)),
            "same-net target remains blocked when the instrument mode is unspecified");
        blocked.verify(sim, f.owner);
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        double timeBefore = sim.t;
        double ohms = sim.measureResistance(red, black, RESISTANCE);
        double current = Math.abs(sim.getLastResistanceTestCurrentForDeveloperVerification());
        boolean cleanup = !sim.activeMeasurementOverlay &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
            sim.hasElectricallyNeutralResistanceReferenceForDeveloperVerification();
        check(PowerDomainContract.finite(ohms) && Math.abs(ohms) < .001 &&
            PowerDomainContract.finite(current) && Math.abs(current - 1.0 / 1000) < 2e-8,
            "same electrical node yields the independent 1V/(1k+0) source-current oracle");
        check(sim.t > timeBefore && sim.elmList.equals(graph) && cleanup,
            "same-net accepted read restores its temporary solver source");
        GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
        System.out.println("RB56_IN_CIRCUIT_RESISTANCE {\"component\":\"same-net-cross-part\",\"boardPads\":\"" +
            firstPad + "," + secondPad + "\",\"actualOhms\":" + number(ohms) +
            ",\"testCurrentAmps\":" + number(current) + "}");
        advance(sim, f, DT);
    }
    private static void verifyInstalledMeasurementFailureCleanup(NativeMeterCirSim sim,
            Rb56PhysicalOwnerConstruction.Result f, PhysicalPart<?> part,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) throws Exception {
        boolean priorTask43P = sim.troubleshootTask43PVerification;
        boolean priorDeveloperVerifier = sim.developerVerifierRunning;
        try {
            sim.troubleshootTask43PVerification = true;
            sim.developerVerifierRunning = true;
            for (CirSim.Task43PMeasurementFailureStage stage : new CirSim.Task43PMeasurementFailureStage[] {
                    CirSim.Task43PMeasurementFailureStage.READER,
                    CirSim.Task43PMeasurementFailureStage.AFTER_STIMULUS_REMOVE }) {
                waitForMeasurementReady(sim, f, red, black, RESISTANCE);
                check(sim.getActiveMeasurementReadiness(red, black, RESISTANCE) == ActiveMeasurementReadiness.READY,
                    "real installed target remains ready before injected cleanup canary " + stage);
                Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
                double timeBefore = sim.t;
                sim.clearTask43PMeasurementFailureForDeveloperVerification();
                sim.armTask43PMeasurementFailureForDeveloperVerification(stage);
                CirSim.Task43PInjectedMeasurementFailure caught = null;
                CirSim.Task43PMeasurementFailureStage observedStage = CirSim.Task43PMeasurementFailureStage.NONE;
                int observedCount = 0;
                try {
                    sim.measureResistance(red, black, RESISTANCE);
                } catch (CirSim.Task43PInjectedMeasurementFailure expected) {
                    caught = expected;
                } finally {
                    observedStage = sim.task43PMeasurementFailureLastInjectedStage;
                    observedCount = sim.task43PMeasurementFailureInjectionCount;
                    sim.clearTask43PMeasurementFailureForDeveloperVerification();
                }
                ActiveMeasurementStimulus last = sim.lastActiveMeasurementStimulus;
                boolean temporaryElementsAbsent = last != null;
                if (last != null) for (CircuitElm element : last.getTemporaryElements())
                    temporaryElementsAbsent &= !sim.elmList.contains(element);
                check(caught != null && caught.getStage() == stage &&
                    observedStage == stage && observedCount == 1,
                    "real temporary-source transaction reached the requested injected failure " + stage);
                boolean cleanup = temporaryElementsAbsent && !sim.activeMeasurementOverlay &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                    sim.getBoardPowerController().isElectricallyUnpowered() && sim.stopMessage == null;
                check(sim.t > timeBefore && sim.elmList.equals(graph) && cleanup,
                    "failed installed-target solve removes stimulus, restores solver and preserves power-off " + stage);
                GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
                System.out.println("RB56_IN_CIRCUIT_FAILURE {\"component\":\"" + part.getId() +
                    "\",\"stage\":\"" + stage.name() + "\",\"timeBefore\":" + number(timeBefore) +
                    ",\"timeAfter\":" + number(sim.t) + ",\"temporaryElementsAbsent\":" +
                    temporaryElementsAbsent + ",\"cleanup\":" + cleanup + "}");
                advance(sim, f, DT);
            }
        } finally {
            sim.clearTask43PMeasurementFailureForDeveloperVerification();
            sim.troubleshootTask43PVerification = priorTask43P;
            sim.developerVerifierRunning = priorDeveloperVerifier;
        }
    }
    private static String number(double value) {
        return PowerDomainContract.finite(value) ? Double.toString(value) : "null";
    }
    /** Let the real unpowered board discharge between independently injected meter transactions. */
    private static double waitForMeasurementReady(NativeMeterCirSim sim,
            Rb56PhysicalOwnerConstruction.Result f, CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black, InstrumentMode mode) {
        check(sim.getBoardPowerController().isElectricallyUnpowered(),
            "natural meter cooldown keeps the board electrically off");
        double start = sim.t;
        ActiveMeasurementReadiness readiness = sim.getActiveMeasurementReadiness(red, black, mode);
        int slices = 0;
        while ((readiness == ActiveMeasurementReadiness.DISCHARGE ||
                readiness == ActiveMeasurementReadiness.WAITING) && slices < 1000) {
            advance(sim, f, .01);
            slices++;
            readiness = sim.getActiveMeasurementReadiness(red, black, mode);
        }
        double elapsed = sim.t - start;
        check(readiness == ActiveMeasurementReadiness.READY,
            "live board naturally recovers to READY for " + mode + " within 10 solver seconds; state=" + readiness);
        if (elapsed > DT) System.out.println("RB56_METER_TRANSACTION_COOLDOWN {\"mode\":\"" +
            mode + "\",\"solverSeconds\":" + number(elapsed) + "}");
        check(sim.getBoardPowerController().isElectricallyUnpowered(),
            "natural meter cooldown does not repower the board");
        return elapsed;
    }
    private static void advance(NativeMeterCirSim sim, Rb56PhysicalOwnerConstruction.Result f, double seconds) {
        if (sim.circuitMatrix == null || sim.analyzeFlag) { sim.solverExecutor.analyze(); sim.analyzeFlag = false; }
        for (int i = 0, n = (int)Math.round(seconds / DT); i < n; i++) sim.solverExecutor.advanceSteps(1);
        f.runtime.observeSimulationTime(sim.t);
        check(sim.stopMessage == null && sim.solverExecutor.isCurrent(sim.solverExecutor.observation(f.owner), f.owner), "actual accepted current-owner sample");
    }
    private static int subscriptions(NativeMeterCirSim sim) throws Exception {
        Field field = SolverTimeObservationService.class.getDeclaredField("subscriptions"); field.setAccessible(true);
        return ((Vector<?>)field.get(sim.solverTimeObservations)).size();
    }
    private static final class Quiet {
        final double time; final Vector<CircuitElm> graph; final Object observation; final int subscriptions;
        Quiet(NativeMeterCirSim sim, GeneratedBoardInstance owner) throws Exception {
            time = sim.t; graph = new Vector<CircuitElm>(sim.elmList); observation = sim.solverExecutor.observation(owner); subscriptions = subscriptions(sim);
        }
        void verify(NativeMeterCirSim sim, GeneratedBoardInstance owner) throws Exception {
            check(sim.t == time && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay && sim.stopMessage == null &&
                sim.solverExecutor.observation(owner) == observation && subscriptions(sim) == subscriptions,
                "denial does not advance solver, append stimulus, open overlay, replace receipt or leak scope subscription");
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { throw new UnsupportedOperationException("Instrument fixture is not customer qualification"); }
        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state) { throw new UnsupportedOperationException("No fault selected"); }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL; }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
    }
    /** Adapt only widgetless native scheduling; keep the production stimulus/reader/restoration transaction. */
    private static final class NativeMeterCirSim extends CirSim {
        NativeMeterCirSim() { gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>(); maxTimeStep = minTimeStep = timeStep = DT; adjustTimeStep = false; }
        @Override void runCircuit(boolean didAnalyze) { solverExecutor.advanceSteps(8); }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm element) { stopMessage = message; circuitMatrix = null; stopElm = element; setSimRunning(false); analyzeFlag = false; }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
