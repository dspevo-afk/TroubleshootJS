package com.lushprojects.circuitjs1.client;

/** Small direct construction canary, not a Q30 admission or solver proof. */
public final class Rb30GeneratorPilot {
    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        if (args.length > 0 && "nmos".equals(args[0])) {
            verifyNmosHealth();
            return;
        }
        boolean direct = false, shared = false, bjt = false, nmos = false;
        long[] seeds = { 0L, 1L, 2L, 3L, 4L, 5L,
            Long.MIN_VALUE, Long.MAX_VALUE };
        for (long seed : seeds) {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan);
            Rb30TopologyValidator.require(candidate);
            if (candidate.board().getComponentIds().size() != 33)
                throw new AssertionError("Reference package count changed");
            if (candidate.board().getPowerInputIds().size() != 4)
                throw new AssertionError("Missing physical input domains");
            if (candidate.serviceA.getActiveDecisionElement() !=
                    candidate.decisionA ||
                    candidate.serviceB.getActiveDecisionElement() !=
                    candidate.decisionB ||
                    candidate.runtime.getCapability("RB30_RESISTOR_SERVICE_REN") == null ||
                    candidate.runtime.getCapability("RB30_RESISTOR_SERVICE_RSA") == null ||
                    candidate.runtime.getCapability("RB30_RESISTOR_SERVICE_RDA") == null ||
                    candidate.runtime.getCapability("RB30_DIODE_SERVICE_DREV") == null ||
                    candidate.runtime.getCapability(
                        Rb30RelayService.CAPABILITY_PREFIX + "KA") == null ||
                    candidate.runtime.getCapability(
                        Rb30RelayService.CAPABILITY_PREFIX + "KB") == null)
                throw new AssertionError("Missing selected-fault service owner");
            if (seed == 0L) {
                ResistorElm unexplained = new ResistorElm(50000, 400);
                unexplained.drag(50080, 400);
                candidate.elements().add(unexplained);
                try {
                    Rb30TopologyValidator.require(candidate);
                    throw new AssertionError("Unexplained element admitted");
                } catch (IllegalStateException expected) {
                    if (!expected.getMessage().contains("unexplained CircuitJS element"))
                        throw expected;
                } finally {
                    candidate.elements().remove(unexplained);
                }
                Point net = candidate.assembly.nets.get("RAIL5");
                WireElm ghost = new WireElm(net.x, net.y);
                ghost.drag(60000, 400);
                candidate.elements().add(ghost);
                try {
                    Rb30TopologyValidator.require(candidate);
                    throw new AssertionError("Ghost branch admitted");
                } catch (IllegalStateException expected) {
                    if (!expected.getMessage().contains("unexplained CircuitJS element"))
                        throw expected;
                } finally {
                    candidate.elements().remove(ghost);
                }
            }
            if (plan.sharedHystereticReference) shared = true;
            else direct = true;
            if (plan.driverABjt || plan.driverBBjt) bjt = true;
            if (!plan.driverABjt || !plan.driverBBjt) nmos = true;
            System.out.println("PASS RB30 seed=" + seed + " topology=" +
                plan.topology() + " fault=" + plan.selectedFault + " parts=" +
                candidate.board().getComponentIds().size() + " elements=" +
                candidate.elements().size());
        }
        if (!direct || !shared || !bjt || !nmos)
            throw new AssertionError("Seed canary missed a structural choice");
        Rb30Generator.Candidate electrical = new Rb30Generator().construct(
            Rb30Plan.resolve(0));
        sim.elmList = electrical.elements();
        sim.adjustables = new java.util.Vector<Adjustable>();
        sim.maxTimeStep = 1e-4;
        sim.minTimeStep = 1e-7;
        sim.adjustTimeStep = true;
        electrical.assembly.power.setConnected(true);
        sim.analyzeCircuit();
        sim.solverExecutor.advanceFor(.03);
        double rail = electrical.regulator.getPostVoltage(1) -
            electrical.regulator.getPostVoltage(2);
        double raw = ((LimitedDcSupplyElm) electrical.backing.get("J1"))
            .getOutputVoltage();
        double input = electrical.regulator.getPostVoltage(0) -
            electrical.regulator.getPostVoltage(2);
        double enable = electrical.regulator.getPostVoltage(3) -
            electrical.regulator.getPostVoltage(2);
        CircuitElm fuse = electrical.backing.get("F1");
        CircuitElm diode = electrical.backing.get("DREV");
        System.out.println("RB30 entry J1=" + electrical.backing.get("J1").getPostVoltage(0) +
            "/" + electrical.backing.get("J1").getPostVoltage(1) +
            " F1=" + fuse.getPostVoltage(0) + "/" + fuse.getPostVoltage(1) +
            " DREV=" + diode.getPostVoltage(0) + "/" + diode.getPostVoltage(1));
        for (String pad : new String[] { "DREV.K", "U1.INPUT", "U1.RETURN" }) {
            CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
                electrical.board().getSimulationBindings().getEndpoint(pad);
            System.out.println("RB30 pad " + pad + " board=" +
                endpoint.getElement().getPostVoltage(endpoint.getPostIndex()) +
                " component=" + electrical.assembly.connections
                    .get(electrical.board().getPad(pad).getComponentId(), pad)
                    .getComponentEndpoint());
        }
        double a = ((BoundedExternalLoadElm) electrical.backing.get("JOA"))
            .getPostVoltage(0) -
            ((BoundedExternalLoadElm) electrical.backing.get("JOA"))
            .getPostVoltage(1);
        double b = ((BoundedExternalLoadElm) electrical.backing.get("JOB"))
            .getPostVoltage(0) -
            ((BoundedExternalLoadElm) electrical.backing.get("JOB"))
            .getPostVoltage(1);
        System.out.println("RB30 solver pilot raw=" + raw + " input=" +
            input + " enable=" + enable + " rail=" + rail +
            " outA=" + a + " outB=" + b);
        if (!(rail > 4.5 && rail < 5.1 && a > 8 && b > 8))
            throw new AssertionError("Q30 healthy high/high solver state");
        verifyFaults();
    }

    private static void verifyFaults() {
        String[] required = { "SENSOR_A_OPEN", "DREV_OPEN", "REN_OPEN",
            "DRIVE_A_OPEN", "RELAY_B_COIL_OPEN" };
        for (String faultId : required) {
            long selectedSeed = Long.MIN_VALUE;
            for (long seed = 0; seed < 1024; seed++) {
                Rb30Plan plan = Rb30Plan.resolve(seed);
                if (plan.selectedFault.equals(faultId) && plan.driverABjt &&
                        plan.driverBBjt && !plan.sharedHystereticReference) {
                    selectedSeed = seed;
                    break;
                }
            }
            if (selectedSeed == Long.MIN_VALUE)
                throw new AssertionError("No Q30 fault seed: " + faultId);
            System.out.println("RB30 fault pilot begin=" + faultId +
                " seed=" + selectedSeed + " topology=" +
                Rb30Plan.resolve(selectedSeed).topology());
            CirSim sim = new CirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = 1e-4;
            sim.minTimeStep = 1e-7;
            sim.adjustTimeStep = true;
            CircuitElm.sim = sim;
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(
                Rb30Plan.resolve(selectedSeed));
            sim.elmList = candidate.elements();
            sim.adjustables = new java.util.Vector<Adjustable>();
            candidate.assembly.power.setConnected(true);
            sim.analyzeCircuit();
            sim.solverExecutor.advanceFor(.03);
            double beforeA = output(candidate, "A");
            double beforeB = output(candidate, "B");
            if (!(beforeA > 8 && beforeB > 8))
                throw new AssertionError("Q30 healthy prior state " + faultId +
                    " " + beforeA + "/" + beforeB);
            String target = candidate.selectedFault.getFault().getTargetComponentId();
            PhysicalPart<?> part = candidate.runtime.getSlot(target)
                .getInstalledPart();
            if (!(part instanceof GeneratedFaultOwningPart) ||
                    !((GeneratedFaultOwningPart) part).ownsGeneratedFault(
                        candidate.selectedFault))
                throw new AssertionError("Fault not owned by physical original " + faultId);
            candidate.assembly.power.setConnected(false);
            for (CircuitElm element : candidate.elements()) element.reset();
            candidate.selectedFault.setApplied(true);
            candidate.assembly.power.setConnected(true);
            sim.analyzeCircuit();
            sim.solverExecutor.advanceFor(.03);
            double faultA = output(candidate, "A");
            double faultB = output(candidate, "B");
            double faultRail = candidate.regulator.getPostVoltage(1) -
                candidate.regulator.getPostVoltage(2);
            System.out.println("RB30 fault readout=" + faultId + " A=" +
                faultA + " B=" + faultB + " rail=" + faultRail);
            if (faultId.equals("DREV_OPEN") || faultId.equals("REN_OPEN")) {
                if (!(faultRail < 1 && faultA < 1 && faultB < 1))
                    throw new AssertionError("Q30 rail fault symptom " + faultId);
            } else if (faultId.equals("RELAY_B_COIL_OPEN")) {
                if (!(faultA > 8 && faultB < 1))
                    throw new AssertionError("Q30 B-only symptom");
            } else if (!(faultA < 1 && faultB > 8)) {
                throw new AssertionError("Q30 A-only symptom " + faultId);
            }
            candidate.assembly.power.setConnected(false);
            for (CircuitElm element : candidate.elements()) element.reset();
            candidate.selectedFault.setApplied(false);
            candidate.assembly.power.setConnected(true);
            sim.analyzeCircuit();
            sim.solverExecutor.advanceFor(.03);
            double repairedA = output(candidate, "A");
            double repairedB = output(candidate, "B");
            if (!(repairedA > 8 && repairedB > 8))
                throw new AssertionError("Q30 restored state " + faultId);
            System.out.println("PASS RB30 solver fault=" + faultId +
                " seed=" + selectedSeed + " healthy=" + beforeA + "/" +
                beforeB + " fault=" + faultA + "/" + faultB +
                " rail=" + faultRail + " restored=" + repairedA + "/" +
                repairedB);
        }
    }

    private static double output(Rb30Generator.Candidate candidate,
            String channel) {
        BoundedExternalLoadElm load = (BoundedExternalLoadElm)
            candidate.backing.get("JO" + channel);
        return load.getPostVoltage(0) - load.getPostVoltage(1);
    }

    private static void verifyNmosHealth() {
        int failures = 0;
        for (long seed : new long[] { 1, 2, 3, 4, 5 }) {
            CirSim sim = new CirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = 1e-4;
            sim.minTimeStep = 1e-7;
            sim.adjustTimeStep = true;
            CircuitElm.sim = sim;
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(
                Rb30Plan.resolve(seed));
            sim.elmList = candidate.elements();
            sim.adjustables = new java.util.Vector<Adjustable>();
            candidate.assembly.power.setConnected(true);
            sim.analyzeCircuit();
            try {
                sim.solverExecutor.advanceFor(5e-5);
                String observed = candidate.plan.driverABjt ? "B" : "A";
                CircuitElm q = candidate.backing.get("Q" + observed);
                CircuitElm d = candidate.backing.get("D" + observed);
                System.out.println("RB30 NMOS early seed=" + seed +
                    " t=" + sim.t + " Vin=" +
                    candidate.regulator.getInputVoltage() + " V5=" +
                    candidate.regulator.getOutputVoltage() +
                    " Qposts=" + q.getPostVoltage(0) + "/" +
                    q.getPostVoltage(1) + "/" + q.getPostVoltage(2) +
                    " D=" + d.getPostVoltage(0) + "/" +
                    d.getPostVoltage(1));
                sim.solverExecutor.advanceFor(.03 - 5e-5);
                System.out.println("RB30 NMOS seed=" + seed + " rail=" +
                    candidate.regulator.getOutputVoltage() + " A=" +
                    output(candidate, "A") + " B=" + output(candidate, "B"));
            } catch (RuntimeException failure) {
                failures++;
                System.out.println("FAIL RB30 NMOS seed=" + seed + " time=" +
                    sim.t + " regulatorInput=" +
                    candidate.regulator.getInputVoltage() + " reason=" +
                    failure.getClass().getSimpleName() + ": " +
                    failure.getMessage());
            }
        }
        if (failures > 0)
            throw new AssertionError("Unqualified Q30 NMOS healthy variant(s): " +
                failures);
    }
}
