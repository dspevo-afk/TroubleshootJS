package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/** Native direct-graph exercise of the authored RB100 pilot, never a player admission. */
public final class Rb100ElectricalPilotContractTest {
    private static final long SEED = 9007199254740993L;
    private static final long FAULT_MATRIX_LIMIT_MS = 1200000L;
    private static final double FAULT_REFERENCE_DT = .00005;
    private static final double HEALTHY_TIMESTEP_DELTA_V = .15;
    private static final int EXPECTED_TEMPORAL_BATCH_STEPS = 64;
    private static int assertions;

    private static final ExpectedFault[] EXPECTED_FAULTS = {
        f("RENAC", "RESISTANCE", 1e9, "RAIL12", false, false, 1),
        f("REN", "RESISTANCE", 1e9, "RAIL5", false, false, 1),
        f("RSA", "RESISTANCE", 1e9, "OUT_A", false, false, .5),
        f("RDA", "RESISTANCE", 1e9, "OUT_A", false, false, .5),
        f("KB", "COIL_OPEN", 1e9, "OUT_B", false, false, .5),
        f("RSC", "RESISTANCE", 1e9, "OUT_C", false, false, .5),
        f("RSD", "RESISTANCE", 1e9, "OUT_D", false, false, .5),
        f("RDC", "RESISTANCE", 1e9, "OUT_C", false, false, .5),
        f("RDD", "RESISTANCE", 1e9, "OUT_D", false, false, .5),
        f("KC", "COIL_OPEN", 1e9, "OUT_C", false, false, .5),
        f("KD", "COIL_OPEN", 1e9, "OUT_D", false, false, .5),
        f("RDE", "RESISTANCE", 1e9, "OUT_E", false, false, .5),
        f("RDF", "RESISTANCE", 1e9, "OUT_F", false, false, .5),
        f("RSH_E", "RESISTANCE", 1e9, "OUT_E", false, false, .5),
        f("RSH_F", "RESISTANCE", 1e9, "OUT_F", false, false, .5),
        f("DE", "SHUNT", .1, "OUT_E", false, false, .5),
        f("DF", "SHUNT", .1, "OUT_F", false, false, .5),
        f("CSTORE_E", "SHUNT", .1, "OUT_E", false, false, .5),
        f("CSTORE_F", "SHUNT", .1, "OUT_F", false, false, .5),
        f("C33", "SHUNT", .1, "RAIL3V3", false, false, 1),
        f("CIN33", "SHUNT", .1, "RAIL5", false, false, 1),
        f("RMON", "RESISTANCE", 10, "OUT_C", false, false, .5),
        f("RMON_H", "RESISTANCE", 1e9, "OUT_C", true, true, 9),
        f("RMON_L", "RESISTANCE", 1e9, "OUT_C", true, true, 9),
        f("CMON", "SHUNT", .1, "OUT_C", true, true, 9),
        f("RBAUX", "RESISTANCE", 1e9, "OUT_C", true, true, 9),
        f("RPUAUX", "RESISTANCE", 10, "AUX_GATE", true, true, 1),
        f("QAUX", "SHUNT", .1, "OUT_C", true, true, 9),
        f("CFLT_C", "SHUNT", .1, "OUT_C", false, false, .5),
        f("CFLT_D", "SHUNT", .1, "OUT_D", false, false, .5),
        f("RMON_IN", "RESISTANCE", 1e9, "OUT_C", true, true, 9)
    };

    private Rb100ElectricalPilotContractTest() { }

    public static void main(String[] args) {
        boolean faultMode = args.length == 1 && "--faults".equals(args[0]);
        boolean diagnosticMode = args.length > 0 && "--diagnostic-fault".equals(args[0]);
        if (diagnosticMode && (args.length < 2 || args.length > 3 ||
                args[1] == null || args[1].trim().length() == 0))
            throw new IllegalArgumentException("Expected --diagnostic-fault <known fault id> [0.0001|0.00005]");
        if (args.length != 0 && !faultMode && !diagnosticMode)
            throw new IllegalArgumentException("Expected no arguments, --faults, or --diagnostic-fault <known fault id>");
        Rb100PilotFaults.Fault diagnosticFault = null;
        double diagnosticDt = .0001;
        if (diagnosticMode) diagnosticFault = resolveDiagnosticFault(args[1]);
        if (diagnosticMode && args.length == 3) diagnosticDt = parseDiagnosticDt(args[2]);
        long suiteStarted = System.currentTimeMillis();
        int failures = 0, cases = 0;
        if (diagnosticMode)
            System.out.println("RB100_DIAGNOSTIC_MODE {\"mode\":\"SINGLE_FAULT_BOTH_VARIANTS\"," +
                "\"status\":\"RUNNING\",\"fault\":\"" + json(args[1]) +
                "\",\"variants\":2,\"dt\":" + diagnosticDt +
                ",\"scope\":\"NATIVE_GRAPH_ONLY\"}");
        if (!runPreflightCanaries()) failures++;
        if (diagnosticMode) {
            for (Rb100Plan.Variant variant : variants()) {
                CaseResult result = runCase(variant, diagnosticFault, diagnosticDt, 0);
                cases++; printCase(result); if (!result.passed) failures++;
            }
        } else if (faultMode) {
            long matrixStarted = System.currentTimeMillis();
            long deadline = matrixStarted + FAULT_MATRIX_LIMIT_MS;
            int[] counts = runFaultMatrix(deadline);
            cases += counts[0]; failures += counts[1];
        } else {
            for (Rb100Plan.Variant variant : variants()) {
                double[][] coarse = null, fine = null;
                CaseResult atCoarse = runCase(variant, null, .0001, 0);
                cases++; printCase(atCoarse); if (!atCoarse.passed) failures++;
                coarse = atCoarse.healthyObservations;
                CaseResult atFine = runCase(variant, null, .00005, 0);
                cases++; printCase(atFine); if (!atFine.passed) failures++;
                fine = atFine.healthyObservations;
                if (atCoarse.passed && atFine.passed) {
                    try {
                        compareTimesteps(variant, coarse, fine);
                        System.out.println("RB100_TIMESTEP_COMPARISON " +
                            comparisonJson(variant, coarse, fine, "PASS"));
                    } catch (RuntimeException error) {
                        failures++;
                        System.out.println("RB100_TIMESTEP_COMPARISON " +
                            comparisonFailureJson(variant, error));
                    } catch (Error error) {
                        failures++;
                        System.out.println("RB100_TIMESTEP_COMPARISON " +
                            comparisonFailureJson(variant, error));
                    }
                }
            }
        }
        long elapsed = System.currentTimeMillis() - suiteStarted;
        if (diagnosticMode) {
            System.out.println("DIAGNOSTIC COMPLETE: RB100 selected-fault assertions=" +
                assertions + " cases=" + cases + " expectedCases=2 failures=" + failures +
                " elapsedMs=" + elapsed + " scope=NATIVE_GRAPH_ONLY");
            if (failures != 0)
                throw new AssertionError("RB100 selected-fault diagnostic failures=" + failures);
            return;
        }
        String status = failures == 0 ? "PASS" : "FAIL";
        System.out.println(status + ": RB100 electrical pilot contracts assertions=" +
            assertions + " cases=" + cases + " expectedCases=" +
            (faultMode ? 62 : 4) + " failures=" + failures +
            " elapsedMs=" + elapsed + " faultMatrixHostBoundMs=" +
            (faultMode ? FAULT_MATRIX_LIMIT_MS : 0) + " scope=NATIVE_GRAPH_ONLY");
        if (failures != 0) throw new AssertionError("RB100 electrical pilot contract failures=" + failures);
    }

    private static Rb100PilotFaults.Fault resolveDiagnosticFault(String id) {
        ExpectedFault expected = null;
        for (ExpectedFault row : EXPECTED_FAULTS)
            if (row.id().equals(id)) { expected = row; break; }
        if (expected == null) throw new IllegalArgumentException("Unknown RB100 fault id: " + id);
        Rb100PilotFaults.Fault fault = Rb100PilotFaults.find(id);
        verifyFault(expected, fault);
        return fault;
    }

    private static double parseDiagnosticDt(String value) {
        final double parsed;
        try { parsed = Double.parseDouble(value); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("Diagnostic timestep must be 0.0001 or 0.00005: " + value);
        }
        if (parsed != .0001 && parsed != .00005)
            throw new IllegalArgumentException("Diagnostic timestep must be 0.0001 or 0.00005: " + value);
        return parsed;
    }

    private static int[] runFaultMatrix(long deadline) {
        int executed = 0, failures = 0;
        for (Rb100Plan.Variant variant : variants()) {
            for (ExpectedFault expected : EXPECTED_FAULTS) {
                if (System.currentTimeMillis() >= deadline) {
                    for (Rb100Plan.Variant remainingVariant : variants()) {
                        if (remainingVariant.ordinal() < variant.ordinal()) continue;
                        for (ExpectedFault remaining : EXPECTED_FAULTS) {
                            if (remainingVariant == variant &&
                                    indexOf(remaining) < indexOf(expected)) continue;
                            printNotRun(remainingVariant, remaining);
                            failures++;
                        }
                    }
                    return new int[] {executed, failures};
                }
                Rb100PilotFaults.Fault fault = null;
                Throwable lookupFailure = null;
                try {
                    fault = Rb100PilotFaults.find(expected.id());
                    verifyFault(expected, fault);
                } catch (RuntimeException error) { lookupFailure = error; }
                if (lookupFailure != null) {
                    CaseResult rejected = CaseResult.failure(expected.id(), variant, FAULT_REFERENCE_DT,
                        "FROZEN_POPULATION_LOOKUP", lookupFailure, 0, 0, true, null);
                    printCase(rejected); executed++; failures++;
                    continue;
                }
                CaseResult result = runCase(variant, fault, FAULT_REFERENCE_DT, deadline);
                printCase(result); executed++;
                if (!result.passed) failures++;
            }
        }
        return new int[] {executed, failures};
    }

    private static int indexOf(ExpectedFault fault) {
        for (int i = 0; i < EXPECTED_FAULTS.length; i++)
            if (EXPECTED_FAULTS[i] == fault) return i;
        throw new IllegalArgumentException("Foreign expected fault row");
    }

    private static boolean runPreflightCanaries() {
        boolean passed = true;
        try {
            verifyFrozenPopulation();
            verifyCatalogIsolation();
            System.out.println("RB100_PREFLIGHT {\"status\":\"PASS\",\"population\":31," +
                "\"variants\":2,\"publicCatalog\":false}");
        } catch (RuntimeException error) {
            passed = false;
            System.out.println("RB100_PREFLIGHT " + failureJson("FAIL", "FROZEN_POPULATION", error));
        } catch (Error error) {
            passed = false;
            System.out.println("RB100_PREFLIGHT " + failureJson("FAIL", "FROZEN_POPULATION", error));
        }
        try {
            long cleanupMs = verifyFailureCleanupCanary();
            System.out.println("RB100_CLEANUP_CANARY {\"status\":\"PASS\"," +
                "\"faultLookup\":\"unknown-and-foreign-rejected\"," +
                "\"simulatedFailure\":\"after-first-accepted-step\"," +
                "\"cleanupMs\":" + cleanupMs + "," +
                "\"scope\":\"NATIVE_GRAPH_ONLY\"}");
        } catch (RuntimeException error) {
            passed = false;
            System.out.println("RB100_CLEANUP_CANARY " + failureJson("FAIL", "CLEANUP_CANARY", error));
        } catch (Error error) {
            passed = false;
            System.out.println("RB100_CLEANUP_CANARY " + failureJson("FAIL", "CLEANUP_CANARY", error));
        }
        try {
            verifyLoadDomainCanary();
        } catch (RuntimeException error) {
            passed = false;
            System.out.println("RB100_LOAD_DOMAIN_CANARY " +
                failureJson("FAIL", "ACTUAL_LOAD_DOMAIN_CANARY", error));
        } catch (Error error) {
            passed = false;
            System.out.println("RB100_LOAD_DOMAIN_CANARY " +
                failureJson("FAIL", "ACTUAL_LOAD_DOMAIN_CANARY", error));
        }
        return passed;
    }

    private static void verifyFrozenPopulation() {
        Vector<Rb100PilotFaults.Fault> actual = Rb100PilotFaults.population();
        require(actual.size() == 31 && EXPECTED_FAULTS.length == 31,
            "frozen independent pilot population remains all 31 members");
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < EXPECTED_FAULTS.length; i++) {
            ExpectedFault expected = EXPECTED_FAULTS[i];
            Rb100PilotFaults.Fault fault = actual.get(i);
            verifyFault(expected, fault);
            require(ids.add(fault.id), "fault identity is unique at canonical order " + (i + 1));
            verifyFault(expected, Rb100PilotFaults.find(expected.id()));
        }
        boolean rejected = false;
        try { Rb100PilotFaults.find("RB100_UNKNOWN_FAULT"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "unknown fault lookup is rejected before graph mutation");
    }

    private static void verifyFault(ExpectedFault expected, Rb100PilotFaults.Fault actual) {
        require(actual != null && expected.owner.equals(actual.owner) &&
                expected.id().equals(actual.id) &&
                expected.kind.equals(actual.kind.name()) &&
                actual.value == expected.value && expected.witness.equals(actual.witness) &&
                actual.overload == expected.overload && actual.above == expected.above &&
                actual.threshold == expected.threshold,
            "independent frozen fault identity, model, context and witness: " + expected.id());
    }

    private static void verifyCatalogIsolation() {
        require(!Rb100Plan.FAMILY_ID.equals(Rb56Plan.FAMILY_ID),
            "RB100 electrical pilot has its own family identity");
        require(!PlayerFamilyCatalog.isRegistered(Rb100Plan.FAMILY_ID) &&
                !PlayerFamilyCatalog.families().contains(Rb100Plan.FAMILY_ID),
            "RB100 pilot remains absent from installed and enabled player catalogs");
    }

    private static long verifyFailureCleanupCanary() {
        NativeContext context = new NativeContext(.0001);
        Rb100ElectricalPilot.Candidate candidate = null;
        GeneratedFaultEffect effect = null;
        Rb100PilotReference reference = null;
        Vector<CircuitElm> installed = null;
        Throwable failure = null;
        boolean injected = false;
        try {
            context.activate();
            long started = System.currentTimeMillis();
            Rb100Plan plan = Rb100Plan.create(SEED,
                Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR);
            candidate = Rb100ElectricalPilot.construct(plan);
            require(System.currentTimeMillis() - started <= Rb100PilotReference.MAX_UNIT_MILLIS,
                "canary construction is within the declared construction unit bound");
            boolean unknownRejected = false;
            try { Rb100PilotFaults.find("RB100_UNKNOWN_FAULT"); }
            catch (IllegalArgumentException expected) { unknownRejected = true; }
            require(unknownRejected, "unknown fault lookup rejects before graph installation");

            Rb100PilotFaults.Fault fault = Rb100PilotFaults.find("RENAC_RESISTANCE");
            CircuitElm backing = candidate.backing.get(fault.owner);
            int backingIndex = candidate.assembly.elements.indexOf(backing);
            require(backingIndex >= 0, "canary starts with the package backing inside its own graph");
            candidate.assembly.elements.remove(backingIndex);
            boolean foreignRejected = false;
            try { Rb100PilotFaults.bind(candidate, fault); }
            catch (IllegalArgumentException expected) { foreignRejected = true; }
            finally { candidate.assembly.elements.add(backingIndex, backing); }
            require(foreignRejected,
                "fault binding rejects an actual package backing detached from its candidate graph");

            verifyCandidate(candidate);
            verifyWireConnectivityCanaries(candidate);
            effect = Rb100PilotFaults.bind(candidate, fault);
            installed = candidate.elements();
            context.install(installed);
            final TestCirSim sim = context.sim;
            final long before = sim.a01AcceptedStepCount;
            reference = new Rb100PilotReference(sim, candidate, fault, effect,
                new NativeStepper(sim, 0, true), .0001, started);
            try {
                reference.advance(); // Actual executor analysis.
                reference.advance(); // Inject after one actual accepted solver step.
            } catch (InjectedSolverFailure expected) {
                injected = sim.a01AcceptedStepCount == before + 1;
            }
            require(injected, "simulated failure occurs just after one accepted solver step");
        } catch (RuntimeException error) { failure = error;
        } catch (Error error) { failure = error;
        } finally {
            Throwable cleanup = context.close(candidate, effect);
            if (failure == null) failure = cleanup;
            else if (cleanup != null) failure.addSuppressed(cleanup);
        }
        if (failure != null) {
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure instanceof Error) throw (Error)failure;
            throw new AssertionError(failure);
        }
        return context.cleanupElapsedMs;
    }

    private static CaseResult runCase(Rb100Plan.Variant variant,
            Rb100PilotFaults.Fault fault, double dt, long hostDeadline) {
        long started = System.currentTimeMillis();
        NativeContext context = new NativeContext(dt);
        Rb100ElectricalPilot.Candidate candidate = null;
        GeneratedFaultEffect effect = null;
        Rb100PilotReference reference = null;
        Vector<CircuitElm> installed = null;
        double[][] observations = null;
        Throwable failure = null;
        String report = null;
        String phase = "PLAN_AND_CONSTRUCTION";
        long operationElapsedMs = 0;
        try {
            context.activate();
            Rb100Plan plan = Rb100Plan.create(SEED, variant);
            candidate = Rb100ElectricalPilot.construct(plan);
            require(System.currentTimeMillis() - started <= Rb100PilotReference.MAX_UNIT_MILLIS,
                "plan and candidate construction fit the declared 5 second unit budget");
            phase = "ACTUAL_PHYSICAL_AND_ELECTRICAL_MAPPING";
            verifyCandidate(candidate);
            double originalFaultValue = Double.NaN;
            if (fault != null) {
                phase = "FAULT_BINDING_BEFORE_INSTALL";
                effect = Rb100PilotFaults.bind(candidate, fault);
                originalFaultValue = mutationValue(candidate, fault, effect);
            }
            printCandidateElementOwners(candidate, variant, dt, fault);
            installed = candidate.elements();
            Vector<CircuitElm> installedContents = new Vector<CircuitElm>(installed);
            context.install(installed);
            phase = "ACTUAL_SOLVER_FAULT_AND_REPAIR_REFERENCE";
            reference = new Rb100PilotReference(context.sim, candidate, fault, effect,
                new NativeStepper(context.sim, hostDeadline, false), dt, started);
            while (!reference.advance()) { }
            phase = "REPAIRED_SIX_CONTEXT_RETEST_AND_REPORT";
            report = reference.report();
            verifyTemporalReferenceReport(report, dt, fault != null);
            verifyFaultLoadSourceDomain(fault, report);
            require(System.currentTimeMillis() - started <= Rb100PilotReference.MAX_CASE_MILLIS,
                "completed reference stays within the per-case 90 second bound");
            require(reference.healthyObservations.length == 6,
                "all six healthy contexts are returned by the actual reference");
            for (int i = 0; i < reference.healthyObservations.length; i++)
                require(reference.healthyObservations[i] != null &&
                    reference.healthyObservations[i].length >= 9,
                    "healthy six-context rail/output observations are complete: " + i);
            observations = copy(reference.healthyObservations);
            require(context.sim.elmList == installed &&
                sameElements(context.sim.elmList, installedContents),
                "repair and retest used the same installed graph and every owned solver element");
            if (fault != null) {
                require(effect != null && !effect.isApplied(),
                    "same live fault is cleared after repaired six-context retest");
                require(mutationValue(candidate, fault, effect) == originalFaultValue,
                    "actual fault backing returned to its original model value");
            }
            require(context.sim.stopMessage == null && context.sim.circuitMatrix != null,
                "actual CircuitJS solver retained a live solved matrix");
        } catch (RuntimeException error) {
            failure = error;
            report = emitDiagnostic(reference);
        } catch (Error error) {
            failure = error;
            report = emitDiagnostic(reference);
        } finally {
            operationElapsedMs = System.currentTimeMillis() - started;
            Throwable cleanup = context.close(candidate, effect);
            if (failure == null) failure = cleanup;
            else if (cleanup != null) failure.addSuppressed(cleanup);
        }
        boolean passed = failure == null;
        return new CaseResult(fault == null ? "HEALTHY" : fault.id,
            variant, dt, passed, report, failure, phase, System.currentTimeMillis() - started,
            operationElapsedMs, context.cleanupElapsedMs, observations, context.cleanupPassed);
    }

    private static double mutationValue(Rb100ElectricalPilot.Candidate candidate,
            Rb100PilotFaults.Fault fault, GeneratedFaultEffect effect) {
        CircuitElm actual = candidate.backing.get(fault.owner);
        if (fault.kind == Rb100PilotFaults.Kind.COIL_OPEN)
            return ((ServiceRelayElm)actual).coilR;
        if (fault.kind == Rb100PilotFaults.Kind.RESISTANCE)
            return ((ResistorElm)actual).getResistance();
        CircuitElm target = effect.getValueMutationTarget();
        require(target instanceof ResistorElm,
            "short reference owns an actual bounded parallel shunt");
        return ((ResistorElm)target).getResistance();
    }

    private static void printCandidateElementOwners(Rb100ElectricalPilot.Candidate candidate,
            Rb100Plan.Variant variant, double dt, Rb100PilotFaults.Fault fault) {
        if (!Boolean.getBoolean("tsj.q100.newtonTrace")) return;
        String caseId = fault == null ? "HEALTHY" : fault.id;
        for (Rb100Plan.Part part : candidate.plan.parts()) {
            if (candidate.assembly.components.hasComponentBinding(part.id)) {
                for (CircuitElm element : candidate.assembly.components.getElements(part.id))
                    printOwnerElement(caseId, variant, dt, part.id, "PRIMARY", candidate, element);
                for (CircuitElm element : candidate.assembly.components.getAuxiliaryElements(part.id))
                    printOwnerElement(caseId, variant, dt, part.id, "AUXILIARY", candidate, element);
            } else {
                CircuitElm element = candidate.backing.get(part.id);
                if (element != null)
                    printOwnerElement(caseId, variant, dt, part.id, "BACKING", candidate, element);
            }
        }
    }

    private static void printOwnerElement(String caseId, Rb100Plan.Variant variant,
            double dt, String owner, String role, Rb100ElectricalPilot.Candidate candidate,
            CircuitElm element) {
        int index = -1;
        for (int i = 0; i < candidate.assembly.elements.size(); i++)
            if (candidate.assembly.elements.get(i) == element) { index = i; break; }
        System.out.println("RB100_OWNER_MAP case=" + caseId + " variant=" + variant.name() +
            " dt=" + dt + " owner=" + owner + " role=" + role + " elmIndex=" + index +
            " class=" + element.getClass().getSimpleName());
    }

    private static void verifyCandidate(Rb100ElectricalPilot.Candidate candidate) {
        Rb100Plan plan = candidate.plan;
        TroubleshootBoard board = candidate.board();
        Vector<CircuitElm> elements = candidate.elements();
        require(Rb100Plan.PACKAGE_COUNT == 100 && plan.physicalPackageCount() == 100 &&
                board.getComponentIds().size() == 100 && candidate.backing.size() == 100,
            "exact 100-package plan, board and actual backing census");
        require(board.getId().equals(Rb100Plan.FAMILY_ID + "_BOARD") &&
                !Rb100Plan.FAMILY_ID.equals(Rb56Plan.FAMILY_ID),
            "actual board identity is RB100 and distinct from the reused RB56 architecture");
        require(elements.size() > board.getComponentIds().size(),
            "solver-only infrastructure and external harness elements are excluded from package count");
        require(candidate.loads.size() == 6,
            "four relay contact loads and both DC harness loads use actual bounded solver models");

        IdentityHashMap<CircuitElm, Boolean> uniqueElements =
            new IdentityHashMap<CircuitElm, Boolean>();
        for (CircuitElm element : elements)
            require(element != null && uniqueElements.put(element, Boolean.TRUE) == null,
                "every owned private solver element is unique in the graph");
        require(candidate.assembly.nets.size() == board.getNetIds().size(),
            "every declared board net has one actual solver graph anchor");
        WireConnectivity wireConnectivity = new WireConnectivity(elements);
        Set<String> netCoordinates = new HashSet<String>();
        for (String netId : board.getNetIds()) {
            Point point = candidate.assembly.nets.get(netId);
            require(point != null && netCoordinates.add(point.x + "," + point.y),
                "distinct actual net anchors cannot silently short: " + netId);
            BoardNet net = board.getNet(netId);
            require(net != null && !net.getPadIds().isEmpty(),
                "each electrical net has physical terminal membership: " + netId);
            for (String padId : net.getPadIds())
                require(netId.equals(board.getPad(padId).getNetId()),
                    "net and pad identity are reciprocal: " + padId);
        }

        Set<String> actualPackageIds = new TreeSet<String>();
        int terminals = 0, externalSourcePackages = 0, grounds = 0;
        for (Rb100Plan.Part part : plan.parts()) {
            require(actualPackageIds.add(part.id), "unique package identity: " + part.id);
            BoardComponent component = board.getComponent(part.id);
            CircuitElm actual = candidate.backing.get(part.id);
            require(component != null && component.getPadIds().size() == part.terminalIds().length &&
                    component.getPhysicalPackage().isEquivalentTo(part.physicalPackage),
                "actual declared package and terminal identity: " + part.id);
            require(actual != null && elements.contains(actual),
                "package backing belongs to the single actual solver graph: " + part.id);
            boolean externalSource = part.kind == Rb100Plan.Kind.SOURCE || "JAC".equals(part.id);
            if (externalSource) {
                externalSourcePackages++;
                require(!candidate.assembly.components.hasComponentBinding(part.id),
                    "external input package is not counted as an installed circuit component: " + part.id);
            } else {
                require(candidate.assembly.components.hasComponentBinding(part.id),
                    "physical package has its actual component binding: " + part.id);
            }
            String[] ids = part.terminalIds(), nets = part.netIds();
            for (int i = 0; i < ids.length; i++) {
                terminals++;
                String padId = part.id + "." + ids[i];
                BoardPad pad = board.getPad(padId);
                CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(padId);
                require(pad != null && part.id.equals(pad.getComponentId()) &&
                        ids[i].equals(pad.getTerminalId()) && nets[i].equals(pad.getNetId()),
                    "declared terminal has its independently named net: " + padId);
                require(endpoint instanceof CircuitPostMeasurementEndpoint,
                    "every terminal maps to an actual CircuitJS post: " + padId);
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint)endpoint;
                require(elements.contains(post.getElement()) && post.getPostIndex() >= 0 &&
                        post.getPostIndex() < post.getElement().getPostCount(),
                    "mapped terminal post belongs to the candidate graph: " + padId);
                Point netPoint = candidate.assembly.nets.get(nets[i]);
                if (externalSource) {
                    require(samePoint(post.getElement().getPost(post.getPostIndex()), netPoint),
                        "external source/header endpoint is the actual declared net boundary: " + padId);
                    require(reachesOnlyDeclaredNet(wireConnectivity, candidate,
                            post.getElement().getPost(post.getPostIndex()), nets[i]),
                        "external source/header endpoint reaches only its declared net: " + padId);
                } else {
                    verifyMappedLead(candidate, wireConnectivity, padId, nets[i], post);
                }
            }
        }
        require(actualPackageIds.size() == 100 && terminals > 100 &&
                externalSourcePackages > 0,
            "all 100 physical packages, their unique terminals and separate external inputs are represented");
        for (CircuitElm element : elements) if (element instanceof GroundElm) {
            grounds++;
            require(samePoint(element.getPost(0), candidate.power.stage.input.source.getPost(0)),
                "only ground is tied to the AC input return");
        }
        require(grounds == 1, "no secondary or invented ground reference exists");
        verifyActualModels(candidate);
        verifyInputAndLoadBoundary(candidate);
    }

    private static void verifyMappedLead(Rb100ElectricalPilot.Candidate candidate,
            WireConnectivity wireConnectivity, String padId, String expectedNetId,
            CircuitPostMeasurementEndpoint simulationEndpoint) {
        GeneratedComponentConnectionBinding binding =
            candidate.assembly.connections.getOrNull(padId);
        require(binding != null && binding.getBoardEndpoint() instanceof CircuitPostMeasurementEndpoint &&
                binding.getComponentEndpoint() instanceof CircuitPostMeasurementEndpoint &&
                binding.getConnectionElement() instanceof WireElm,
            "physical terminal has an actual board, component and lead binding: " + padId);
        CircuitPostMeasurementEndpoint boardEndpoint =
            (CircuitPostMeasurementEndpoint)binding.getBoardEndpoint();
        CircuitPostMeasurementEndpoint componentEndpoint =
            (CircuitPostMeasurementEndpoint)binding.getComponentEndpoint();
        require(boardEndpoint.getElement() == simulationEndpoint.getElement() &&
                boardEndpoint.getPostIndex() == simulationEndpoint.getPostIndex(),
            "player-facing electrical terminal is the board-owned conductive endpoint: " + padId);
        require(candidate.assembly.elements.contains(boardEndpoint.getElement()) &&
                candidate.assembly.elements.contains(componentEndpoint.getElement()) &&
                candidate.assembly.elements.contains(binding.getConnectionElement()),
            "mapped copper, component endpoint and lead are in the same graph: " + padId);
        require(boardEndpoint.getElement() instanceof WireElm,
            "board terminal lands on actual conductive copper: " + padId);
        WireElm copper = (WireElm)boardEndpoint.getElement();
        Point padPoint = copper.getPost(boardEndpoint.getPostIndex());
        Point componentPoint = componentEndpoint.getElement().getPost(componentEndpoint.getPostIndex());
        WireElm lead = (WireElm)binding.getConnectionElement();
        Point leadFirst = lead.getPost(0), leadSecond = lead.getPost(1);
        require(samePoint(leadFirst, padPoint) && samePoint(leadSecond, componentPoint) ||
                samePoint(leadSecond, padPoint) && samePoint(leadFirst, componentPoint),
            "actual component lead connects the declared pad to its solver endpoint: " + padId);
        require(reachesOnlyDeclaredNet(wireConnectivity, candidate, padPoint, expectedNetId),
            "board copper reaches only its declared logical net: " + padId);
        require(reachesOnlyDeclaredNet(wireConnectivity, candidate, componentPoint, expectedNetId),
            "actual component post reaches only its declared logical net through wire posts: " + padId);
    }

    /**
     * CircuitJS wire closure merges posts at equal coordinates and shorts the
     * two endpoints of each WireElm. It does not connect line crossings or
     * travel through a non-wire component body.
     */
    private static final class WireConnectivity {
        private final Map<Point, Set<Point>> adjacency =
            new HashMap<Point, Set<Point>>();
        WireConnectivity(Vector<CircuitElm> elements) {
            for (CircuitElm element : elements) {
                for (int post = 0; post < element.getPostCount(); post++)
                    addPoint(element.getPost(post));
                if (element instanceof WireElm) {
                    Point first = element.getPost(0);
                    Point second = element.getPost(1);
                    adjacency.get(first).add(second);
                    adjacency.get(second).add(first);
                }
            }
        }
        private void addPoint(Point point) {
            if (!adjacency.containsKey(point))
                adjacency.put(new Point(point.x, point.y), new HashSet<Point>());
        }
        Set<Point> reachableFrom(Point start) {
            Set<Point> reached = new HashSet<Point>();
            ArrayDeque<Point> pending = new ArrayDeque<Point>();
            Point origin = new Point(start.x, start.y);
            reached.add(origin);
            pending.add(origin);
            while (!pending.isEmpty()) {
                Point point = pending.removeFirst();
                Set<Point> adjacent = adjacency.get(point);
                if (adjacent == null) continue;
                for (Point next : adjacent) if (reached.add(next)) pending.addLast(next);
            }
            return reached;
        }
        boolean connected(Point first, Point second) {
            return reachableFrom(first).contains(second);
        }
    }

    private static boolean reachesOnlyDeclaredNet(WireConnectivity connectivity,
            Rb100ElectricalPilot.Candidate candidate, Point start, String expectedNetId) {
        Point expected = candidate.assembly.nets.get(expectedNetId);
        if (expected == null) return false;
        Set<Point> reached = connectivity.reachableFrom(start);
        if (!reached.contains(expected)) return false;
        for (Map.Entry<String, Point> net : candidate.assembly.nets.entrySet())
            if (!expectedNetId.equals(net.getKey()) && reached.contains(net.getValue()))
                return false;
        return true;
    }

    /** Negative canaries run on an unpublished candidate graph before installation. */
    private static void verifyWireConnectivityCanaries(
            Rb100ElectricalPilot.Candidate candidate) {
        Rb100Plan.Part part = candidate.plan.part("F1");
        String padId = part.id + "." + part.terminalIds()[0];
        String expectedNetId = part.netIds()[0];
        GeneratedComponentConnectionBinding binding =
            candidate.assembly.connections.getOrNull(padId);
        require(binding != null &&
                binding.getComponentEndpoint() instanceof CircuitPostMeasurementEndpoint &&
                binding.getConnectionElement() instanceof WireElm,
            "F1 canary has its actual component post and package lead");
        CircuitPostMeasurementEndpoint component =
            (CircuitPostMeasurementEndpoint)binding.getComponentEndpoint();
        WireElm lead = (WireElm)binding.getConnectionElement();
        int leadIndex = candidate.assembly.elements.indexOf(lead);
        require(leadIndex >= 0, "F1 package lead belongs to the private uninstalled graph");
        Point componentPoint = component.getElement().getPost(component.getPostIndex());
        Point expectedPoint = candidate.assembly.nets.get(expectedNetId);
        candidate.assembly.elements.remove(leadIndex);
        try {
            WireConnectivity missingLead =
                new WireConnectivity(candidate.assembly.elements);
            require(!missingLead.connected(componentPoint, expectedPoint),
                "removing the actual F1 lead disconnects its solver post from AC_LINE");
        } finally {
            candidate.assembly.elements.add(leadIndex, lead);
        }

        WireConnectivity healthy =
            new WireConnectivity(candidate.assembly.elements);
        require(reachesOnlyDeclaredNet(healthy, candidate, componentPoint, expectedNetId),
            "restored F1 post reaches its declared net without a second net anchor");
        String foreignNetId = null;
        for (Map.Entry<String, Point> net : candidate.assembly.nets.entrySet())
            if (!expectedNetId.equals(net.getKey()) &&
                    !healthy.connected(componentPoint, net.getValue())) {
                foreignNetId = net.getKey();
                break;
            }
        require(foreignNetId != null,
            "private graph provides a distinct declared net for the miswire canary");
        Point foreignPoint = candidate.assembly.nets.get(foreignNetId);
        WireElm miswire = new WireElm(expectedPoint.x, expectedPoint.y);
        miswire.x2 = foreignPoint.x; miswire.y2 = foreignPoint.y; miswire.setPoints();
        candidate.assembly.elements.add(miswire);
        try {
            WireConnectivity shorted =
                new WireConnectivity(candidate.assembly.elements);
            require(!reachesOnlyDeclaredNet(shorted, candidate, componentPoint, expectedNetId),
                "a real WireElm short to another declared net is rejected before installation");
        } finally {
            candidate.assembly.elements.remove(miswire);
            miswire.delete();
        }
    }

    private static void verifyActualModels(Rb100ElectricalPilot.Candidate candidate) {
        Rb100Plan plan = candidate.plan;
        for (String channel : new String[] {"A", "B", "C", "D", "E", "F", "AUX", "INV"}) {
            CircuitElm actual = candidate.backing.get("Q" + channel);
            String model = plan.driver(channel).getId();
            require(("NMOS".equals(model) && actual instanceof NMosfetElm) ||
                    ("BJT".equals(model) && actual instanceof NTransistorElm),
                "selected " + model + " driver is the actual CircuitJS model: " + channel);
        }
        for (Rb100Plan.Part part : plan.tailParts()) {
            CircuitElm actual = candidate.backing.get(part.id);
            if (part.kind == Rb100Plan.Kind.DIODE)
                require(actual instanceof DiodeElm &&
                    "1N4148".equals(((DiodeElm)actual).modelName),
                    "declared flyback diode uses its actual selected model: " + part.id);
            if (part.kind == Rb100Plan.Kind.CAPACITOR)
                require(actual instanceof CapacitorElm &&
                    ((CapacitorElm)actual).getCapacitance() == part.value &&
                    ((CapacitorElm)actual).initialVoltage == 0,
                    "declared zero-energy capacitor uses its actual model: " + part.id);
        }
        verifyCIN33SeriesEsr(candidate);
        AbstractRailRegulatorElm rail5 =
            (AbstractRailRegulatorElm)candidate.backing.get("U1");
        AbstractRailRegulatorElm rail3 =
            (AbstractRailRegulatorElm)candidate.backing.get("U33");
        require(rail5.getContract().getVariantId().equals(
                RailRegulationContract.linear5V().getVariantId()),
            "actual 5 V rail uses the frozen linear E02 regulator");
        RailRegulationContract expected3 = plan.variant ==
            Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING ?
                RailRegulationContract.averagedSwitching3V3() :
                RailRegulationContract.linear3V3();
        require(rail3.getContract().getVariantId().equals(expected3.getVariantId()),
            "actual 3.3 V rail realizes the selected structural alternative");
        require(candidate.decisions.size() == 5 &&
                candidate.decisions.get("MON") instanceof E04SensorControlModel.DecisionElement,
            "four sensor decisions and powered monitor use actual E04 decision models");
    }

    private static void verifyCIN33SeriesEsr(Rb100ElectricalPilot.Candidate candidate) {
        require(Rb100Plan.MODEL_VERSION == 4,
            "independent electrical model epoch is version 4");
        Rb100Plan.Part part = candidate.plan.part("CIN33");
        require(part != null && part.kind == Rb100Plan.Kind.CAPACITOR &&
                part.terminalIds().length == 2 && "+".equals(part.terminalIds()[0]) &&
                "-".equals(part.terminalIds()[1]) &&
                "RAIL12".equals(part.netIds()[0]) && "CTRL_RETURN".equals(part.netIds()[1]),
            "CIN33 is the declared polarized RAIL12-to-CTRL_RETURN input capacitor");
        Rb100Plan.Part regulator = candidate.plan.part("U33");
        require(regulator != null && regulator.kind == Rb100Plan.Kind.REGULATOR &&
                regulator.terminalIds().length == 4 &&
                "INPUT".equals(regulator.terminalIds()[0]) &&
                "OUTPUT".equals(regulator.terminalIds()[1]) &&
                "RETURN".equals(regulator.terminalIds()[2]) &&
                "ENABLE".equals(regulator.terminalIds()[3]) &&
                "RAIL12".equals(regulator.netIds()[0]) &&
                "RAIL3V3".equals(regulator.netIds()[1]) &&
                "CTRL_RETURN".equals(regulator.netIds()[2]) &&
                "RAIL12".equals(regulator.netIds()[3]),
            "U33 input and enable use the independent RAIL12 source boundary");

        CircuitElm actual = candidate.backing.get("CIN33");
        PhysicalSpecification physical = candidate.assembly.specifications.getSpecification("CIN33");
        require(actual instanceof CapacitorElm && actual.getClass() == CapacitorElm.class &&
                candidate.assembly.elements.contains(actual) &&
                physical instanceof CapacitorSpecification,
            "100-package CIN33 primary backing remains the actual owned CapacitorElm");
        CapacitorElm capacitor = (CapacitorElm)actual;
        CapacitorSpecification specification = (CapacitorSpecification)physical;
        require(capacitor.getCapacitance() == 22e-6 && capacitor.initialVoltage == 0 &&
                !capacitor.isTrapezoidal() && specification.hasExplicitModelRecipe() &&
                specification.getCapacitanceFarads() == 22e-6 &&
                specification.getEsrOhms() == 1 &&
                specification.getIntegrationFlags() == CapacitorElm.FLAG_BACK_EULER &&
                specification.getInitialVoltage() == 0,
            "CIN33 typed model retains its zero-energy 22 uF recipe and explicit 1 ohm ESR");

        GeneratedComponentConnectionBinding positive =
            candidate.assembly.connections.getOrNull("CIN33.+");
        GeneratedComponentConnectionBinding negative =
            candidate.assembly.connections.getOrNull("CIN33.-");
        require(positive != null && negative != null &&
                positive.getComponentEndpoint() instanceof CircuitPostMeasurementEndpoint &&
                negative.getComponentEndpoint() instanceof CircuitPostMeasurementEndpoint,
            "CIN33 positive and negative terminals retain actual post bindings");
        CircuitPostMeasurementEndpoint positiveEndpoint =
            (CircuitPostMeasurementEndpoint)positive.getComponentEndpoint();
        CircuitPostMeasurementEndpoint negativeEndpoint =
            (CircuitPostMeasurementEndpoint)negative.getComponentEndpoint();
        require(positiveEndpoint.getElement() instanceof ResistorElm &&
                positiveEndpoint.getPostIndex() == 0 &&
                negativeEndpoint.getElement() == capacitor && negativeEndpoint.getPostIndex() == 1,
            "CIN33 board positive lead reaches ESR R0 and its negative lead reaches capacitor post 1");

        ResistorElm esr = (ResistorElm)positiveEndpoint.getElement();
        Point internalCapPost = capacitor.getPost(0);
        Point negativeCapPost = capacitor.getPost(1);
        Point externalEsrPost = esr.getPost(0);
        Point internalEsrPost = esr.getPost(1);
        require(candidate.assembly.elements.contains(esr) && esr.getResistance() == 1 &&
                samePoint(internalEsrPost, internalCapPost) &&
                !samePoint(externalEsrPost, internalCapPost) &&
                !samePoint(negativeCapPost, internalCapPost) &&
                !samePoint(externalEsrPost, negativeCapPost),
            "one actual owned 1 ohm series resistor shares only its negative-side node with capacitor post 0");

        WireConnectivity connectivity = new WireConnectivity(candidate.assembly.elements);
        require(reachesOnlyDeclaredNet(connectivity, candidate, externalEsrPost, "RAIL12") &&
                reachesOnlyDeclaredNet(connectivity, candidate, negativeCapPost, "CTRL_RETURN"),
            "CIN33 package leads preserve positive and negative declared copper nets");
        for (Map.Entry<String, Point> net : candidate.assembly.nets.entrySet())
            require(!connectivity.connected(internalCapPost, net.getValue()),
                "CIN33 internal ESR junction is not directly wire-shorted to declared net " + net.getKey());

        Set<Point> internalWireNode = connectivity.reachableFrom(internalCapPost);
        int resistorTerminalsAtInternalNode = 0;
        for (CircuitElm element : candidate.assembly.elements)
            if (element instanceof ResistorElm)
                for (int post = 0; post < 2; post++)
                    if (internalWireNode.contains(element.getPost(post))) {
                        resistorTerminalsAtInternalNode++;
                        require(element == esr && post == 1,
                            "CIN33 internal series junction has no second resistor or bypass branch");
                    }
        require(resistorTerminalsAtInternalNode == 1,
            "CIN33 has exactly one auxiliary resistor endpoint at its internal capacitor junction");
    }

    private static void verifyInputAndLoadBoundary(Rb100ElectricalPilot.Candidate candidate) {
        Rb56PowerStage stage = candidate.power.stage;
        verifyModel4LoadTopology(candidate);
        Set<String> expectedInputs = new TreeSet<String>(Arrays.asList(
            "MAINAC", "LOAD12", "SENSOR_A", "SENSOR_B", "SENSOR_C", "SENSOR_D"));
        require(expectedInputs.equals(new TreeSet<String>(
                candidate.board().getPowerInputIds())),
            "only the AC, bounded 12 V load and four customer sensor inputs are exposed");
        require(stage.input.source.waveform == VoltageElm.WF_AC &&
                stage.input.source.frequency == E05AcInputModel.FREQUENCY_HZ &&
                stage.input.source.maxVoltage == E05AcInputModel.PEAK_VOLTS &&
                stage.input.impedance.getResistance() == E05AcInputModel.SERIES_OHMS &&
                !candidate.backing.containsValue(stage.input.impedance) &&
                candidate.assembly.power.getBinding("MAINAC") == stage.input.binding,
            "actual mains source is 120 Vrms/60 Hz with excluded external 22 ohm impedance");
        ExternalPowerSimulationBinding loadInput =
            candidate.assembly.power.getBinding("LOAD12");
        require(loadInput.getLimitedSupply() == candidate.backing.get("JLOAD") &&
                loadInput.getLimitedSupply().maxVoltage == 12 &&
                loadInput.getLimitedSupply().getLimitAmps() == .5,
            "LOAD12 is the actual 12 V/500 mA source at the contact-load boundary");
        for (String channel : new String[] {"A", "B", "C", "D"}) {
            ExternalPowerSimulationBinding input =
                candidate.assembly.power.getBinding("SENSOR_" + channel);
            require(input.getLimitedSupply() == candidate.sensorSources.get(channel) &&
                    input.getLimitedSupply().getLimitAmps() == .25 &&
                    input.getLimitedSupply().maxVoltage == 0,
                "sensor source is the actual bounded 0 V input: " + channel);
        }
        for (String channel : new String[] {"A", "B", "C", "D", "E", "F"}) {
            BoundedExternalLoadElm load = candidate.loads.get(channel);
            require(load != null && load.getResistance() == 180 && !load.hasFailed(),
                "actual external load retains the frozen 180 ohm boundary: " + channel);
        }
        for (String channel : new String[] {"E", "F"}) {
            Rb100Plan.Part header = candidate.plan.part("JO" + channel);
            String[] nets = header.netIds();
            CircuitPostMeasurementEndpoint[] harness =
                candidate.assembly.connections.getConnectorHarness(header.id);
            boolean series = false;
            BoundedExternalLoadElm load = candidate.loads.get(channel);
            CircuitElm inductor = harness != null && harness.length == 2 ?
                harness[1].getElement() : null;
            if (harness != null && harness.length == 2 &&
                    harness[0].getElement() == load &&
                    harness[0].getPostIndex() == 0 &&
                    inductor instanceof InductorElm &&
                    harness[1].getPostIndex() == 1 &&
                    ((InductorElm)inductor).getInductance() ==
                        Rb100Plan.EXTERNAL_LOAD_HENRIES) {
                for (CircuitElm element : candidate.assembly.elements)
                    if (element instanceof WireElm &&
                            ((samePoint(element.getPost(0), load.getPost(1)) &&
                              samePoint(element.getPost(1), inductor.getPost(0))) ||
                             (samePoint(element.getPost(1), load.getPost(1)) &&
                              samePoint(element.getPost(0), inductor.getPost(0)))))
                        series = true;
            }
            WireConnectivity topology = new WireConnectivity(candidate.assembly.elements);
            require("LOAD12".equals(nets[0]) &&
                    ("OUT_" + channel).equals(nets[1]) &&
                    series && reachesOnlyDeclaredNet(topology, candidate, load.getPost(0), "LOAD12") &&
                    reachesOnlyDeclaredNet(topology, candidate,
                        inductor.getPost(1), "OUT_" + channel),
                "actual external 180 ohm/10 mH series load spans LOAD12 and OUT_" +
                    channel + ": " + channel);
        }
    }

    private static void verifyModel4LoadTopology(Rb100ElectricalPilot.Candidate candidate) {
        require(Rb100Plan.MODEL_VERSION == 4 &&
                candidate.board().getNet("LOAD_RETURN") == null &&
                !candidate.assembly.nets.containsKey("LOAD_RETURN") &&
                !candidate.board().getNetIds().contains("LOAD_RETURN"),
            "model 4 has no superseded LOAD_RETURN island or solver anchor");
        verifyModel4BasePreservation(candidate);
        WireConnectivity topology = new WireConnectivity(candidate.elements());
        requireModel4Part(candidate, "JLOAD", Rb100Plan.Kind.SOURCE, "CONNECTOR",
            "EXTERNAL_DC_LOAD_BUS_INPUT", new String[] {"1", "2"},
            new String[] {"LOAD12", "CTRL_RETURN"}, "LOAD12");
        for (String channel : new String[] {"A", "B"})
            requireModel4Part(candidate, "JO" + channel, Rb100Plan.Kind.OUTPUT_HEADER,
                "OUTPUT_HEADER", "EXTERNAL_DC_LOAD_BUS_RELAY_OUTPUT_INTERFACE",
                new String[] {"1", "2"}, new String[] {"OUT_" + channel, "CTRL_RETURN"}, null);
        for (String channel : new String[] {"C", "D"})
            requireModel4Part(candidate, "JO" + channel, Rb100Plan.Kind.OUTPUT_HEADER,
                "OUTPUT_HEADER", "EXTERNAL_DC_LOAD_BUS_RESISTIVE_OUTPUT_INTERFACE",
                new String[] {"1", "2"}, new String[] {"OUT_" + channel, "CTRL_RETURN"}, null);
        for (String channel : new String[] {"E", "F"}) {
            requireModel4Part(candidate, "JO" + channel, Rb100Plan.Kind.OUTPUT_HEADER,
                "OUTPUT_HEADER", "EXTERNAL_DC_LOAD_BUS_R_PLUS_L_OUTPUT_INTERFACE",
                new String[] {"1", "2"}, new String[] {"LOAD12", "OUT_" + channel}, null);
            requireModel4Part(candidate, "D" + channel, Rb100Plan.Kind.DIODE, "DIODE",
                "OUTPUT_FLYBACK", new String[] {"A", "K"},
                new String[] {"OUT_" + channel, "LOAD12"}, null);
            requireModel4Part(candidate, "CSTORE_" + channel, Rb100Plan.Kind.CAPACITOR,
                "CAPACITOR", "OUTPUT_STORAGE_CAPACITOR", new String[] {"1", "2"},
                new String[] {"LOAD12", "OUT_" + channel}, null);

            DiodeElm diode = (DiodeElm)candidate.backing.get("D" + channel);
            CapacitorElm storage = (CapacitorElm)candidate.backing.get("CSTORE_" + channel);
            require(diode != null && storage != null &&
                    reachesOnlyDeclaredNet(topology, candidate, diode.getPost(1), "LOAD12") &&
                    reachesOnlyDeclaredNet(topology, candidate, storage.getPost(0), "LOAD12") &&
                    reachesOnlyDeclaredNet(topology, candidate, storage.getPost(1), "OUT_" + channel),
                "actual " + channel + " flyback cathode and storage capacitor use the LOAD12/OUT bus");
        }

        for (String channel : new String[] {"A", "B", "C", "D"}) {
            String outputNet = "OUT_" + channel;
            ServiceRelayElm relay = candidate.relays.get(channel);
            Rb100Plan.Part relayPart = candidate.plan.part("K" + channel);
            String[] relayTerminals = relayPart.terminalIds(), relayNets = relayPart.netIds();
            GeneratedComponentConnectionBinding commonBinding = candidate.assembly.connections.get(
                "K" + channel, "K" + channel + ".COM");
            GeneratedComponentConnectionBinding openBinding = candidate.assembly.connections.get(
                "K" + channel, "K" + channel + ".NO");
            CircuitPostMeasurementEndpoint commonContact = (CircuitPostMeasurementEndpoint)
                commonBinding.getComponentEndpoint();
            CircuitPostMeasurementEndpoint normallyOpen = (CircuitPostMeasurementEndpoint)
                openBinding.getComponentEndpoint();
            BoundedExternalLoadElm load = candidate.loads.get(channel);
            CircuitPostMeasurementEndpoint[] harness =
                candidate.assembly.connections.getConnectorHarness("JO" + channel);
            require(relay != null && relayTerminals.length == 5 &&
                    "COM".equals(relayTerminals[2]) && "NO".equals(relayTerminals[4]) &&
                    "LOAD12".equals(relayNets[2]) && outputNet.equals(relayNets[4]) &&
                    commonBinding.getBoardEndpoint() == candidate.board().getSimulationBindings()
                        .getEndpoint("K" + channel + ".COM") &&
                    openBinding.getBoardEndpoint() == candidate.board().getSimulationBindings()
                        .getEndpoint("K" + channel + ".NO") &&
                    commonContact.getElement() == relay &&
                    commonContact.getPostIndex() == RelaySpecification.POSTS[2] &&
                    normallyOpen.getElement() == relay &&
                    normallyOpen.getPostIndex() == RelaySpecification.POSTS[4] &&
                    reachesOnlyDeclaredNet(topology, candidate, relay.getPost(RelaySpecification.POSTS[2]), "LOAD12") &&
                    reachesOnlyDeclaredNet(topology, candidate, relay.getPost(RelaySpecification.POSTS[4]), outputNet) &&
                    harness != null && harness.length == 2 &&
                    harness[0].getElement() == load && harness[0].getPostIndex() == 0 &&
                    harness[1].getElement() == load && harness[1].getPostIndex() == 1 &&
                    reachesOnlyDeclaredNet(topology, candidate, load.getPost(0), outputNet) &&
                    reachesOnlyDeclaredNet(topology, candidate, load.getPost(1), "CTRL_RETURN"),
                "actual " + channel + " LOAD12 relay contact and external load return to shared CTRL_RETURN");
        }

        ExternalBoardPowerInput boardInput = candidate.board().getPowerInput("LOAD12");
        ExternalPowerSimulationBinding input = candidate.assembly.power.getBinding("LOAD12");
        LimitedDcSupplyElm supply = input.getLimitedSupply();
        Vector<CircuitElm> sourceBacking = input.getBackingElements();
        require(boardInput != null && "JLOAD.1".equals(boardInput.getPositivePadId()) &&
                "JLOAD.2".equals(boardInput.getReturnPadId()) &&
                "LOAD12".equals(boardInput.getPositiveNetId()) &&
                "CTRL_RETURN".equals(boardInput.getReturnNetId()) &&
                supply == candidate.backing.get("JLOAD") && sourceBacking.size() == 2 &&
                sourceBacking.get(0) == supply && sourceBacking.get(1) instanceof SwitchElm,
            "actual JLOAD external power pads and limited source use the shared load bus/common return");
        SwitchElm isolation = (SwitchElm)sourceBacking.get(1);
        Point loadBus = candidate.assembly.nets.get("LOAD12");
        Point common = candidate.assembly.nets.get("CTRL_RETURN");
        CircuitPostMeasurementEndpoint positivePad = (CircuitPostMeasurementEndpoint)
            candidate.board().getSimulationBindings().getEndpoint("JLOAD.1");
        CircuitPostMeasurementEndpoint returnPad = (CircuitPostMeasurementEndpoint)
            candidate.board().getSimulationBindings().getEndpoint("JLOAD.2");
        require(input.isConnected() && isolation.position == 0 &&
                reachesOnlyDeclaredNet(topology, candidate, supply.getPost(0), "CTRL_RETURN") &&
                samePoint(supply.getPost(1), isolation.getPost(0)) &&
                reachesOnlyDeclaredNet(topology, candidate, isolation.getPost(1), "LOAD12") &&
                reachesOnlyDeclaredNet(topology, candidate,
                    positivePad.getElement().getPost(positivePad.getPostIndex()), "LOAD12") &&
                reachesOnlyDeclaredNet(topology, candidate,
                    returnPad.getElement().getPost(returnPad.getPostIndex()), "CTRL_RETURN") &&
                loadBus != null && common != null,
                "actual JLOAD source return is common and its output flows through the closed isolation pole");
    }

    private static void verifyModel4BasePreservation(Rb100ElectricalPilot.Candidate candidate) {
        Vector<Rb56Plan.Part> baseParts = candidate.plan.base.parts();
        Set<String> allowedReturnOverrides = new HashSet<String>(Arrays.asList(
            "JLOAD", "JOA", "JOB"));
        int overrides = 0;
        require(baseParts.size() == 51,
            "RB100 reuses the exact 51-package two-channel RB56 core");
        for (Rb56Plan.Part base : baseParts) {
            Rb100Plan.Part actual = candidate.plan.part(base.id);
            String[] expectedNets = base.netIds();
            if (allowedReturnOverrides.contains(base.id)) {
                require(expectedNets.length == 2 && "LOAD_RETURN".equals(expectedNets[1]),
                    "frozen RB56 return override originates at the exact legacy pin: " + base.id);
                expectedNets[1] = "CTRL_RETURN";
                overrides++;
            }
            require(actual.kind.name().equals(base.kind.name()) && actual.type.equals(base.type) &&
                    actual.physicalPackage.isEquivalentTo(base.physicalPackage) &&
                    Double.doubleToLongBits(actual.value) == Double.doubleToLongBits(base.value) &&
                    (actual.channel == null ? base.channel == null : actual.channel.equals(base.channel)) &&
                    (actual.inputId == null ? base.inputId == null : actual.inputId.equals(base.inputId)) &&
                    Arrays.equals(actual.terminalIds(), base.terminalIds()) &&
                    Arrays.equals(actual.netIds(), expectedNets),
                "model 4 preserves every RB56 core field except the three declared return pins: " + base.id);
        }
        require(overrides == 3,
            "only JLOAD, JOA and JOB inherit the three declared CTRL_RETURN pin overrides");
    }

    private static void requireModel4Part(Rb100ElectricalPilot.Candidate candidate,
            String id, Rb100Plan.Kind kind, String type, String purpose,
            String[] terminals, String[] nets, String inputId) {
        Rb100Plan.Part part = candidate.plan.part(id);
        require(part.kind == kind && type.equals(part.type) && purpose.equals(part.purpose) &&
                Arrays.equals(terminals, part.terminalIds()) && Arrays.equals(nets, part.netIds()) &&
                (inputId == null ? part.inputId == null : inputId.equals(part.inputId)),
            "independent model 4 declaration for " + id + " has its exact package purpose, pins and nets");
    }

    /** Independently reads all six pre-repair samples for load-bus shunts. */
    private static void verifyFaultLoadSourceDomain(Rb100PilotFaults.Fault fault, String report) {
        if (fault == null || !("DE_SHUNT".equals(fault.id) || "DF_SHUNT".equals(fault.id) ||
                "CSTORE_E_SHUNT".equals(fault.id) || "CSTORE_F_SHUNT".equals(fault.id))) return;
        String[] contexts = {"LOW", "HIGH", "AC_ONLY", "OVERLOAD", "RECOVERED", "LOW_AGAIN"};
        int cursor = report.indexOf("\"observations\":[");
        require(cursor >= 0, "load-bus fault report retains its actual reference observations");
        for (String context : contexts) {
            int sample = report.indexOf("\"phase\":\"FAULT\"", cursor);
            require(sample >= 0 && report.startsWith("\"phase\":\"FAULT\",\"context\":\"" +
                    context + "\"", sample),
                "load-bus short retains ordered pre-repair context " + context);
            int marker = report.indexOf("\"values\":[", sample);
            int valuesStart = marker < 0 ? -1 : marker + "\"values\":[".length();
            int valuesEnd = valuesStart < 0 ? -1 : report.indexOf(']', valuesStart);
            require(valuesStart >= 0 && valuesEnd > valuesStart,
                "load-bus short retains a complete actual sample for " + context);
            String[] values = report.substring(valuesStart, valuesEnd).split(",");
            require(values.length == 14,
                "load-bus short retains all 14 independent reference signals for " + context);
            double rail12 = Double.parseDouble(values[0]);
            double rail5 = Double.parseDouble(values[1]);
            double rail33 = Double.parseDouble(values[2]);
            require(LowVoltageSourceModel.finite(rail12) && LowVoltageSourceModel.finite(rail5) &&
                    LowVoltageSourceModel.finite(rail33) && rail12 >= 10 && rail12 <= 12.5 &&
                    rail5 >= 4.65 && rail5 <= 5.25 && rail33 >= 3.05 && rail33 <= 3.5,
                "actual control rails remain healthy during the " + fault.id + " " + context +
                    " pre-repair sample: " + rail12 + "/" + rail5 + "/" + rail33);
            cursor = valuesEnd + 1;
        }
        require(report.indexOf("\"phase\":\"FAULT\"", cursor) < 0,
            "load-bus fault has exactly six pre-repair contexts before same-graph repair retest");
    }

    private static void verifyLoadDomainCanary() {
        final double dt = .0001;
        final long started = System.currentTimeMillis();
        NativeContext context = new NativeContext(dt);
        Rb100ElectricalPilot.Candidate candidate = null;
        ResistorElm taskLoad = null;
        WireElm positiveLead = null, returnLead = null;
        Vector<CircuitElm> installed = null;
        Throwable failure = null;
        long operationMs = 0;
        int work = 1;
        double limitedBus = Double.NaN, deliveredCurrent = Double.NaN, branchCurrent = Double.NaN;
        double loadedRail12 = Double.NaN, loadedRail5 = Double.NaN, loadedRail33 = Double.NaN;
        double recoveredBus = Double.NaN, recoveredCurrent = Double.NaN;
        double recoveredRail12 = Double.NaN, recoveredRail5 = Double.NaN, recoveredRail33 = Double.NaN;
        long acceptedSteps = 0;
        double simulatedSeconds = 0;
        try {
            context.activate();
            Rb100Plan plan = Rb100Plan.create(SEED,
                Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR);
            candidate = Rb100ElectricalPilot.construct(plan);
            require(System.currentTimeMillis() - started <= Rb100PilotReference.MAX_UNIT_MILLIS,
                "model 4 load-domain candidate construction fits the 5 second unit bound");
            verifyCandidate(candidate);
            require(candidate.board().getComponentIds().size() == 100 &&
                    candidate.backing.size() == 100,
                "task load canary begins from the exact 100-package candidate");

            taskLoad = new ResistorElm(900000, 900000);
            taskLoad.x2 = 900080; taskLoad.y2 = 900000; taskLoad.setPoints();
            taskLoad.setResistance(10);
            candidate.assembly.elements.add(taskLoad);
            positiveLead = candidate.assembly.wire(candidate.assembly.nets.get("LOAD12"),
                taskLoad.getPost(0));
            returnLead = candidate.assembly.wire(taskLoad.getPost(1),
                candidate.assembly.nets.get("CTRL_RETURN"));
            installed = candidate.elements();
            context.install(installed);
            TestCirSim sim = context.sim;
            NativeStepper stepper = new NativeStepper(sim, 0, false);
            work = boundedCanaryAnalyze(stepper, sim, started, work, "loaded");
            work = boundedCanaryAdvance(stepper, sim, dt, .4, started, work, "loaded");
            require(sim.stopMessage == null && sim.circuitMatrix != null,
                "loaded task harness has a live actual CircuitJS matrix");

            LimitedDcSupplyElm supply = candidate.assembly.power.getBinding("LOAD12").getLimitedSupply();
            limitedBus = actualNetVoltage(candidate, "LOAD12");
            deliveredCurrent = supply.getCurrent();
            branchCurrent = taskLoad.getCurrent();
            loadedRail12 = actualNetVoltage(candidate, "RAIL12");
            loadedRail5 = actualNetVoltage(candidate, "RAIL5");
            loadedRail33 = actualNetVoltage(candidate, "RAIL3V3");
            requireFiniteCanary(limitedBus, deliveredCurrent, branchCurrent,
                loadedRail12, loadedRail5, loadedRail33);
            printLoadDomainSample("LIMITED", sim, limitedBus, deliveredCurrent,
                branchCurrent, loadedRail12, loadedRail5, loadedRail33);
            require(limitedBus >= 4.5 && limitedBus <= 5.5,
                "actual LOAD12 bus settles near the 500 mA compliance voltage: " + limitedBus);
            require(deliveredCurrent >= .499 && deliveredCurrent <= .501,
                "LimitedDcSupply current has the positive delivery sign and reaches 0.499-0.501 A: " +
                    deliveredCurrent);
            require(branchCurrent > .49 && branchCurrent <= .51,
                "task 10 ohm resistor draws positive current from LOAD12 to CTRL_RETURN: " + branchCurrent);
            requireCanaryControlRails(loadedRail12, loadedRail5, loadedRail33, "under LOAD12 limit");

            taskLoad.setResistance(1e12);
            sim.needAnalyze();
            work = boundedCanaryAnalyze(stepper, sim, started, work, "recovery");
            work = boundedCanaryAdvance(stepper, sim, dt, .04, started, work, "recovery");
            require(sim.stopMessage == null && sim.circuitMatrix != null,
                "1 teraohm restamp retains a live actual CircuitJS matrix");
            recoveredBus = actualNetVoltage(candidate, "LOAD12");
            recoveredCurrent = supply.getCurrent();
            recoveredRail12 = actualNetVoltage(candidate, "RAIL12");
            recoveredRail5 = actualNetVoltage(candidate, "RAIL5");
            recoveredRail33 = actualNetVoltage(candidate, "RAIL3V3");
            requireFiniteCanary(recoveredBus, recoveredCurrent,
                recoveredRail12, recoveredRail5, recoveredRail33);
            printLoadDomainSample("RECOVERED", sim, recoveredBus, recoveredCurrent,
                taskLoad.getCurrent(), recoveredRail12, recoveredRail5, recoveredRail33);
            require(taskLoad.getResistance() == 1e12 && recoveredBus >= 11.5 && recoveredBus <= 12.5,
                "same task load at 1 teraohm lets actual LOAD12 bus recover to 11.5-12.5 V: " + recoveredBus);
            require(Math.abs(recoveredCurrent) < .01,
                "load-source current returns near zero after the high-resistance restamp: " + recoveredCurrent);
            requireCanaryControlRails(recoveredRail12, recoveredRail5, recoveredRail33,
                "after LOAD12 recovery");

            acceptedSteps = sim.a01AcceptedStepCount;
            simulatedSeconds = sim.t;
            removeIdentity(installed, taskLoad, "temporary load");
            removeIdentity(installed, positiveLead, "temporary positive lead");
            removeIdentity(installed, returnLead, "temporary common-return lead");
        } catch (RuntimeException error) { failure = error;
        } catch (Error error) { failure = error;
        } finally {
            operationMs = System.currentTimeMillis() - started;
            Throwable cleanup = context.close(candidate, null);
            if (failure == null) failure = cleanup;
            else if (cleanup != null) failure.addSuppressed(cleanup);
        }
        if (failure != null) {
            System.out.println("RB100_LOAD_DOMAIN_CANARY {\"status\":\"FAIL\",\"scope\":\"NATIVE_GRAPH_ONLY\"," +
                "\"operationMs\":" + operationMs + ",\"cleanupMs\":" + context.cleanupElapsedMs +
                ",\"cleanupStatus\":\"" + (context.cleanupPassed ? "PASS" : "FAIL") +
                "\",\"failure\":\"" + json(failure.toString()) + "\"}");
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure instanceof Error) throw (Error)failure;
            throw new AssertionError(failure);
        }
        require(context.cleanupPassed,
            "model 4 load-domain candidate, temporary resistor, and wires dispose exactly once");
        System.out.println("RB100_LOAD_DOMAIN_CANARY {\"status\":\"PASS\",\"scope\":\"NATIVE_GRAPH_ONLY\"," +
            "\"variant\":\"SHARED_HYSTERETIC_LINEAR\",\"dt\":" + dt +
            ",\"packages\":100,\"temporaryBoardPackages\":0,\"temporaryGraphElements\":3," +
            "\"taskLoadOhms\":10,\"limitedBusV\":" + limitedBus +
            ",\"positiveSupplyCurrentA\":" + deliveredCurrent +
            ",\"loadBranchCurrentA\":" + branchCurrent +
            ",\"loadedRailsV\":[" + loadedRail12 + "," + loadedRail5 + "," + loadedRail33 +
            "],\"recoveryLoadOhms\":1.0E12,\"recoveredBusV\":" + recoveredBus +
            ",\"recoverySupplyCurrentA\":" + recoveredCurrent +
            ",\"recoveredRailsV\":[" + recoveredRail12 + "," + recoveredRail5 + "," +
            recoveredRail33 + "],\"acceptedSteps\":" + acceptedSteps +
            ",\"simulatedSeconds\":" + simulatedSeconds + ",\"work\":" + work +
            ",\"operationMs\":" + operationMs + ",\"cleanupMs\":" + context.cleanupElapsedMs +
            ",\"cleanupStatus\":\"PASS\"}");
    }

    private static int boundedCanaryAnalyze(NativeStepper stepper, TestCirSim sim,
            long started, int work, String phase) {
        long unitStarted = System.currentTimeMillis();
        stepper.analyze();
        int nextWork = work + 1;
        require(sim.stopMessage == null && sim.circuitMatrix != null,
            "actual " + phase + " compliance canary analysis completes");
        require(nextWork <= Rb100PilotReference.MAX_WORK,
            "actual load-domain canary remains within the 640 work-unit bound");
        require(System.currentTimeMillis() - unitStarted <= Rb100PilotReference.MAX_UNIT_MILLIS &&
                System.currentTimeMillis() - started <= Rb100PilotReference.MAX_CASE_MILLIS,
            "actual " + phase + " analysis stays within the 5 second unit and 90 second case bounds");
        return nextWork;
    }

    private static int boundedCanaryAdvance(NativeStepper stepper, TestCirSim sim,
            double dt, double seconds, long started, int work, String phase) {
        int remaining = (int)Math.round(seconds / dt);
        while (remaining > 0) {
            int count = Math.min(Rb100PilotReference.BATCH_STEPS, remaining);
            long unitStarted = System.currentTimeMillis();
            long acceptedBefore = sim.a01AcceptedStepCount;
            double timeBefore = sim.t;
            stepper.advance(count);
            int nextWork = work + 1;
            require(sim.a01AcceptedStepCount == acceptedBefore + count &&
                    Math.abs((sim.t - timeBefore) - count * dt) < 1e-8,
                "actual " + phase + " compliance canary accepted step/time correspondence");
            require(nextWork <= Rb100PilotReference.MAX_WORK,
                "actual load-domain canary remains within the 640 work-unit bound");
            require(System.currentTimeMillis() - unitStarted <= Rb100PilotReference.MAX_UNIT_MILLIS &&
                    System.currentTimeMillis() - started <= Rb100PilotReference.MAX_CASE_MILLIS,
                "actual " + phase + " batch stays within the 5 second unit and 90 second case bounds");
            work = nextWork;
            remaining -= count;
        }
        return work;
    }

    private static double actualNetVoltage(Rb100ElectricalPilot.Candidate candidate, String netId) {
        return actualNetAbsoluteVoltage(candidate, netId) -
            actualNetAbsoluteVoltage(candidate, "CTRL_RETURN");
    }

    private static double actualNetAbsoluteVoltage(Rb100ElectricalPilot.Candidate candidate,
            String netId) {
        for (Rb100Plan.Part part : candidate.plan.parts()) {
            String[] nets = part.netIds(), terminals = part.terminalIds();
            for (int i = 0; i < nets.length; i++) if (netId.equals(nets[i])) {
                CircuitMeasurementEndpoint raw = candidate.board().getSimulationBindings()
                    .getEndpoint(part.id + "." + terminals[i]);
                require(raw instanceof CircuitPostMeasurementEndpoint,
                    "actual load-domain measurement endpoint for " + netId);
                CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)raw;
                return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
            }
        }
        throw new AssertionError("Missing actual board net measurement endpoint: " + netId);
    }

    private static void requireFiniteCanary(double... values) {
        for (double value : values)
            require(LowVoltageSourceModel.finite(value), "finite actual load-domain observation");
    }

    private static void requireCanaryControlRails(double rail12, double rail5,
            double rail33, String phase) {
        require(rail12 >= 10 && rail12 <= 12.5 && rail5 >= 4.65 && rail5 <= 5.25 &&
                rail33 >= 3.05 && rail33 <= 3.5,
            "actual converted 12 V/5 V/3.3 V control rails remain healthy " + phase +
                ": " + rail12 + "/" + rail5 + "/" + rail33);
    }

    private static void printLoadDomainSample(String phase, TestCirSim sim,
            double bus, double sourceCurrent, double loadCurrent,
            double rail12, double rail5, double rail33) {
        System.out.println("RB100_LOAD_DOMAIN_SAMPLE {\"phase\":\"" + phase +
            "\",\"time\":" + sim.t + ",\"busV\":" + bus +
            ",\"sourceCurrentA\":" + sourceCurrent + ",\"loadCurrentA\":" + loadCurrent +
            ",\"controlRailsV\":[" + rail12 + "," + rail5 + "," + rail33 + "]}");
    }

    private static void removeIdentity(Vector<CircuitElm> elements, CircuitElm target, String label) {
        int found = -1, count = 0;
        for (int i = 0; i < elements.size(); i++) if (elements.get(i) == target) {
            found = i; count++;
        }
        require(count == 1, "temporary load-domain element exists once before removal: " + label);
        elements.remove(found);
    }

    private static void compareTimesteps(Rb100Plan.Variant variant,
            double[][] coarse, double[][] fine) {
        require(coarse != null && fine != null && coarse.length == 6 && fine.length == 6,
            "both timestep runs include the same six healthy contexts");
        for (int context = 0; context < 6; context++) {
            require(coarse[context] != null && fine[context] != null &&
                    coarse[context].length >= 9 && fine[context].length >= 9,
                "matched healthy observation row " + context);
            for (int signal = 0; signal < 9; signal++) {
                double difference = Math.abs(coarse[context][signal] - fine[context][signal]);
                require(LowVoltageSourceModel.finite(difference),
                    "finite matched timestep delta");
                require(difference <= HEALTHY_TIMESTEP_DELTA_V,
                    "healthy timestep sensitivity <= 0.15 V at " +
                    Rb100PilotReference.CONTEXTS[context] + ":" + signalName(signal) +
                    " delta=" + difference);
            }
        }
    }

    private static void verifyTemporalReferenceReport(String report, double dt, boolean faulted) {
        require(report != null &&
                report.contains("\"protocol\":\"q100-electrical-reference-v3\"") &&
                report.contains("\"modelVersion\":4"),
            "complete actual reference report identifies protocol v3 and frozen model version 4");
        require(report.contains("\"advanceMode\":\"TEMPORAL_BATCH\"") &&
                report.contains("\"operationLimitMs\":5000") &&
                report.contains("\"trialBoundaryLimitMs\":500") &&
                report.contains("\"maxBatchSteps\":" + EXPECTED_TEMPORAL_BATCH_STEPS) &&
                report.contains("\"fixedStepVerified\":true"),
            "reference reports its bounded temporal-batch and fixed-step contract");
        long expectedAcceptedSteps = Math.round((faulted ? 1.2 : .6) / dt);
        long expectedBatchesPerPhase = batchesFor(.4, dt) + 5 * batchesFor(.04, dt);
        long expectedBatches = expectedBatchesPerPhase * (faulted ? 2 : 1);
        require(reportLong(report, "acceptedSteps") == expectedAcceptedSteps,
            "reference reports all expected accepted steps for " + (faulted ? "fault plus repair" : "healthy") +
                " contexts: " + expectedAcceptedSteps);
        require(reportLong(report, "stepBatches") == expectedBatches,
            "reference reports the ceil-per-context temporal batch count: " + expectedBatches);
        require(reportLong(report, "maxBatchAcceptedSteps") == EXPECTED_TEMPORAL_BATCH_STEPS,
            "reference batches never exceed the independent 64-step maximum");
        require(Math.abs(reportDouble(report, "simulatedSeconds") - (faulted ? 1.2 : .6)) < 1e-8,
            "reference completes every declared healthy/fault and repair duration");
    }

    private static long batchesFor(double seconds, double dt) {
        long steps = Math.round(seconds / dt);
        return (steps + EXPECTED_TEMPORAL_BATCH_STEPS - 1) / EXPECTED_TEMPORAL_BATCH_STEPS;
    }

    private static long reportLong(String report, String field) {
        return Long.parseLong(reportValue(report, field));
    }

    private static double reportDouble(String report, String field) {
        return Double.parseDouble(reportValue(report, field));
    }

    private static String reportValue(String report, String field) {
        String marker = "\"" + field + "\":";
        int start = report.indexOf(marker);
        require(start >= 0, "complete reference report contains " + field);
        start += marker.length();
        int end = start;
        while (end < report.length() && ",}".indexOf(report.charAt(end)) < 0) end++;
        require(end > start, "complete reference report has a value for " + field);
        return report.substring(start, end);
    }

    private static String signalName(int signal) {
        String[] names = {"RAIL12", "RAIL5", "RAIL3V3", "OUT_A", "OUT_B",
            "OUT_C", "OUT_D", "OUT_E", "OUT_F"};
        return names[signal];
    }

    private static String comparisonJson(Rb100Plan.Variant variant,
            double[][] coarse, double[][] fine, String status) {
        double maximum = 0; int context = 0, signal = 0;
        for (int c = 0; c < 6; c++) for (int s = 0; s < 9; s++) {
            double delta = Math.abs(coarse[c][s] - fine[c][s]);
            if (delta > maximum) { maximum = delta; context = c; signal = s; }
        }
        return "{\"status\":\"" + status + "\",\"variant\":\"" + variant.name() +
            "\",\"contexts\":6,\"signals\":9,\"limitV\":0.15,\"maxDeltaV\":" +
            maximum + ",\"maxAt\":\"" + Rb100PilotReference.CONTEXTS[context] +
            ":" + signalName(signal) + "\"}";
    }

    private static String comparisonFailureJson(Rb100Plan.Variant variant, Throwable error) {
        return "{\"status\":\"FAIL\",\"variant\":\"" + variant.name() +
            "\",\"failure\":\"" + json(error.toString()) + "\"}";
    }

    private static void printCase(CaseResult result) {
        if (result.passed && result.report != null) {
            System.out.println("RB100_ELECTRICAL_PILOT " + result.report);
            System.out.println("RB100_ELECTRICAL_CLEANUP " +
                "{\"case\":\"" + result.id + "\",\"variant\":\"" +
                result.variant.name() + "\",\"dt\":" + result.dt +
                ",\"cleanupStatus\":\"PASS\",\"rawGraphRestored\":true," +
                "\"candidateDisposedExactlyOnce\":true,\"nativeStateRestored\":true," +
                "\"operationMs\":" + result.operationElapsedMs +
                ",\"cleanupMs\":" + result.cleanupMs + "}");
        } else {
            String partial = result.report == null ? "null" : result.report;
            System.out.println("RB100_ELECTRICAL_PILOT {\"status\":\"FAIL\",\"case\":\"" +
                json(result.id) + "\",\"variant\":\"" + result.variant.name() +
                "\",\"dt\":" + result.dt + ",\"elapsedMs\":" + result.elapsedMs +
                ",\"phase\":\"" + result.phase + "\",\"operationMs\":" +
                result.operationElapsedMs + ",\"cleanupMs\":" + result.cleanupMs +
                ",\"cleanupStatus\":\"" + (result.cleanupPassed ? "PASS" : "FAIL") + "\"" +
                ",\"failure\":\"" + json(String.valueOf(result.failure)) +
                "\",\"partialReport\":" + partial + "}");
        }
    }

    private static void printNotRun(Rb100Plan.Variant variant, ExpectedFault fault) {
        System.out.println("RB100_ELECTRICAL_PILOT {\"status\":\"NOT_RUN_HOST_BOUND\"," +
            "\"case\":\"" + fault.id() + "\",\"variant\":\"" + variant.name() +
            "\",\"dt\":" + FAULT_REFERENCE_DT + ",\"matrixLimitMs\":1200000}");
    }

    private static String failureJson(String status, String phase, Throwable error) {
        return "{\"status\":\"" + status + "\",\"phase\":\"" + phase +
            "\",\"failure\":\"" + json(error.toString()) + "\"}";
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch == '\n') out.append("\\n");
            else if (ch == '\r') out.append("\\r");
            else if (ch == '\t') out.append("\\t");
            else if (ch < 32) out.append('?');
            else out.append(ch);
        }
        return out.toString();
    }

    private static Rb100Plan.Variant[] variants() {
        return new Rb100Plan.Variant[] {
            Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR,
            Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING
        };
    }

    private static double[][] copy(double[][] source) {
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++)
            result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }

    private static ExpectedFault f(String owner, String kind, double value,
            String witness, boolean overload, boolean above, double threshold) {
        return new ExpectedFault(owner, kind, value, witness, overload, above, threshold);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError("RB100 electrical pilot: " + message);
    }

    private static boolean samePoint(Point first, Point second) {
        return first != null && second != null && first.x == second.x && first.y == second.y;
    }

    private static boolean sameElements(Vector<CircuitElm> first, Vector<CircuitElm> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) if (first.get(i) != second.get(i)) return false;
        return true;
    }

    private static final class ExpectedFault {
        final String owner, kind, witness;
        final double value, threshold;
        final boolean overload, above;
        ExpectedFault(String owner, String kind, double value, String witness,
                boolean overload, boolean above, double threshold) {
            this.owner = owner; this.kind = kind; this.value = value; this.witness = witness;
            this.overload = overload; this.above = above; this.threshold = threshold;
        }
        String id() { return owner + "_" + kind; }
    }

    private static final class CaseResult {
        final String id;
        final Rb100Plan.Variant variant;
        final double dt;
        final boolean passed;
        final String report;
        final Throwable failure;
        final String phase;
        final long elapsedMs, operationElapsedMs, cleanupMs;
        final double[][] healthyObservations;
        CaseResult(String id, Rb100Plan.Variant variant, double dt, boolean passed,
                String report, Throwable failure, String phase, long elapsedMs,
                long operationElapsedMs, long cleanupMs,
                double[][] healthyObservations, boolean cleanupPassed) {
            this.id = id; this.variant = variant; this.dt = dt;
            this.passed = passed && cleanupPassed; this.report = report;
            this.failure = failure; this.phase = phase;
            this.elapsedMs = elapsedMs; this.operationElapsedMs = operationElapsedMs;
            this.cleanupMs = cleanupMs; this.cleanupPassed = cleanupPassed;
            this.healthyObservations = healthyObservations;
        }
        final boolean cleanupPassed;
        static CaseResult failure(String id, Rb100Plan.Variant variant, double dt,
                String phase, Throwable failure, long elapsedMs, long cleanupMs,
                boolean cleanup, double[][] observations) {
            return new CaseResult(id, variant, dt, false,
                "{\"phase\":\"" + phase + "\"}", failure, phase, elapsedMs,
                elapsedMs, cleanupMs, observations, cleanup);
        }
    }

    private static final class NativeStepper implements Rb100PilotReference.Stepper {
        final TestCirSim sim;
        final long deadline;
        final boolean failAfterFirst;
        boolean injected;
        NativeStepper(TestCirSim sim, long deadline, boolean failAfterFirst) {
            this.sim = sim; this.deadline = deadline; this.failAfterFirst = failAfterFirst;
        }
        public void analyze() {
            checkDeadline();
            sim.solverExecutor.analyze();
        }
        public void advance(int steps) {
            checkDeadline();
            if (!failAfterFirst) {
                sim.solverExecutor.advanceFor(steps * sim.maxTimeStep);
                checkDeadline();
                return;
            }
            long startCount = sim.a01AcceptedStepCount;
            for (int i = 0; i < steps; i++) {
                sim.solverExecutor.advanceSteps(1);
                if (!injected && sim.a01AcceptedStepCount > startCount) {
                    injected = true;
                    throw new InjectedSolverFailure();
                }
            }
        }
        private void checkDeadline() {
            if (deadline > 0 && System.currentTimeMillis() >= deadline)
                throw new HostBoundReached();
        }
    }

    private static final class InjectedSolverFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        InjectedSolverFailure() { super("intentional native cleanup canary"); }
    }
    private static final class HostBoundReached extends RuntimeException {
        private static final long serialVersionUID = 1L;
        HostBoundReached() { super("1200 second native fault matrix host bound reached"); }
    }

    private static String emitDiagnostic(Rb100PilotReference reference) {
        if (reference == null) return null;
        try {
            String diagnostic = reference.diagnosticReport();
            System.out.println("RB100_PILOT_CASE_FAILURE " + diagnostic);
            return diagnostic;
        } catch (RuntimeException diagnosticFailure) {
            String diagnostic = failureJson("FAIL", "DIAGNOSTIC_REPORT", diagnosticFailure);
            System.out.println("RB100_PILOT_CASE_FAILURE " + diagnostic);
            return diagnostic;
        } catch (Error diagnosticFailure) {
            String diagnostic = failureJson("FAIL", "DIAGNOSTIC_REPORT", diagnosticFailure);
            System.out.println("RB100_PILOT_CASE_FAILURE " + diagnostic);
            return diagnostic;
        }
    }

    private static final class NativeContext {
        final CirSim priorCircuitSim = CircuitElm.sim;
        final CirSim priorSingleton = CirSim.theSim;
        final SimSnapshot priorCircuitSnapshot = new SimSnapshot(priorCircuitSim);
        final SimSnapshot priorSingletonSnapshot =
            priorSingleton == priorCircuitSim ? null : new SimSnapshot(priorSingleton);
        final ModelDefaults defaults = new ModelDefaults();
        final TestCirSim sim;
        final Vector<CircuitElm> rawGraph;
        final Scope[] scopes;
        final int scopeCount;
        final int[] scopeColumns;
        long cleanupElapsedMs;
        boolean cleanupPassed;
        NativeContext(double dt) {
            sim = new TestCirSim(dt);
            rawGraph = sim.elmList;
            scopes = sim.scopes;
            scopeCount = sim.scopeCount;
            scopeColumns = sim.scopeColCount;
        }
        void activate() { CircuitElm.sim = sim; CirSim.theSim = sim; }
        void install(Vector<CircuitElm> graph) {
            if (graph == null || graph.isEmpty()) throw new IllegalArgumentException("Empty native graph");
            sim.elmList = graph;
            sim.a01MeasurementRunning = true;
        }
        Throwable close(Rb100ElectricalPilot.Candidate candidate, GeneratedFaultEffect effect) {
            long cleanupStarted = System.currentTimeMillis();
            Throwable failure = null;
            boolean exactDisposal = true;
            activate();
            if (effect != null) try { effect.setApplied(false); }
            catch (RuntimeException error) { failure = error; }
            catch (Error error) { failure = error; }
            Vector<CircuitElm> owned = candidate == null ? new Vector<CircuitElm>() :
                new Vector<CircuitElm>(candidate.assembly.elements);
            IdentityHashMap<CircuitElm, Boolean> unique = new IdentityHashMap<CircuitElm, Boolean>();
            for (CircuitElm element : owned)
                if (unique.put(element, Boolean.TRUE) != null) exactDisposal = false;
            sim.a01MeasurementRunning = false;
            sim.elmList = rawGraph;
            try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
            catch (RuntimeException error) { failure = append(failure, error); }
            catch (Error error) { failure = append(failure, error); }
            sim.circuitMatrix = null;
            if (candidate != null) {
                try {
                    candidate.dispose();
                    candidate.dispose();
                } catch (RuntimeException error) { failure = append(failure, error); exactDisposal = false; }
                catch (Error error) { failure = append(failure, error); exactDisposal = false; }
                for (CircuitElm element : owned)
                    if (sim.deleted.get(element) == null || sim.deleted.get(element).intValue() != 1)
                        exactDisposal = false;
                if (!candidate.assembly.elements.isEmpty()) exactDisposal = false;
            }
            sim.elmList = rawGraph;
            sim.scopes = scopes;
            sim.scopeCount = scopeCount;
            sim.scopeColCount = scopeColumns;
            boolean privateStateRestored = sim.elmList == rawGraph && rawGraph.isEmpty() &&
                sim.scopes == scopes && sim.scopeCount == scopeCount &&
                sim.scopeColCount == scopeColumns && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable();
            if (!privateStateRestored || !exactDisposal)
                failure = append(failure, new AssertionError("private CircuitJS graph or exact disposal was not restored"));
            if (System.currentTimeMillis() - cleanupStarted > 120000)
                failure = append(failure, new AssertionError("native cleanup exceeded its 120 second bound"));
            defaults.restore();
            CircuitElm.sim = priorCircuitSim;
            CirSim.theSim = priorSingleton;
            if ((priorCircuitSnapshot != null && !priorCircuitSnapshot.matches()) ||
                    (priorSingletonSnapshot != null && !priorSingletonSnapshot.matches()) ||
                    !defaults.matchesRestored() || CircuitElm.sim != priorCircuitSim ||
                    CirSim.theSim != priorSingleton)
                failure = append(failure, new AssertionError("native singleton, graph, scopes or model defaults changed"));
            cleanupElapsedMs = System.currentTimeMillis() - cleanupStarted;
            cleanupPassed = failure == null;
            return failure;
        }
        private static Throwable append(Throwable prior, Throwable next) {
            if (prior == null) return next;
            prior.addSuppressed(next);
            return prior;
        }
    }

    private static final class SimSnapshot {
        final CirSim sim;
        final Vector<CircuitElm> graph;
        final Vector<CircuitElm> graphElements;
        final Scope[] scopes;
        final Scope[] scopeElements;
        final int scopeCount;
        final int[] scopeColumns;
        final int[] scopeColumnValues;
        SimSnapshot(CirSim sim) {
            this.sim = sim;
            if (sim == null) {
                graph = null; graphElements = null; scopes = null; scopeElements = null;
                scopeCount = 0; scopeColumns = null; scopeColumnValues = null;
            } else {
                graph = sim.elmList;
                graphElements = graph == null ? null : new Vector<CircuitElm>(graph);
                scopes = sim.scopes;
                scopeElements = scopes == null ? null : scopes.clone();
                scopeCount = sim.scopeCount;
                scopeColumns = sim.scopeColCount;
                scopeColumnValues = scopeColumns == null ? null : scopeColumns.clone();
            }
        }
        boolean matches() {
            return sim == null || (sim.elmList == graph &&
                sameElements(sim.elmList, graphElements) && sim.scopes == scopes &&
                Arrays.equals(sim.scopes, scopeElements) && sim.scopeCount == scopeCount &&
                sim.scopeColCount == scopeColumns && Arrays.equals(scopeColumns, scopeColumnValues));
        }
    }

    private static final class ModelDefaults {
        final String diode = DiodeElm.lastModelName;
        final String zener = ZenerElm.lastZenerModelName;
        final String transistor = TransistorElm.lastModelName;
        final String customLogic = CustomLogicElm.lastModelName;
        final String customComposite = CustomCompositeElm.lastModelName;
        final String led = LEDElm.lastLEDModelName;
        final double mosfetBeta = MosfetElm.lastBeta;
        final int groundSymbol = GroundElm.lastSymbolType;
        void restore() {
            DiodeElm.lastModelName = diode;
            ZenerElm.lastZenerModelName = zener;
            TransistorElm.lastModelName = transistor;
            CustomLogicElm.lastModelName = customLogic;
            CustomCompositeElm.lastModelName = customComposite;
            LEDElm.lastLEDModelName = led;
            MosfetElm.lastBeta = mosfetBeta;
            GroundElm.lastSymbolType = groundSymbol;
        }
        boolean matchesRestored() {
            return diode.equals(DiodeElm.lastModelName) &&
                zener.equals(ZenerElm.lastZenerModelName) &&
                transistor.equals(TransistorElm.lastModelName) &&
                customLogic.equals(CustomLogicElm.lastModelName) &&
                customComposite.equals(CustomCompositeElm.lastModelName) &&
                led.equals(LEDElm.lastLEDModelName) &&
                Double.doubleToLongBits(mosfetBeta) ==
                    Double.doubleToLongBits(MosfetElm.lastBeta) &&
                groundSymbol == GroundElm.lastSymbolType;
        }
    }

    private static final class TestCirSim extends CirSim {
        final IdentityHashMap<CircuitElm, Integer> deleted =
            new IdentityHashMap<CircuitElm, Integer>();
        TestCirSim(double dt) {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            scopes = new Scope[20]; scopeColCount = new int[20]; scopeCount = 0;
            maxTimeStep = minTimeStep = timeStep = dt; adjustTimeStep = false;
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message; circuitMatrix = null; stopElm = element;
            setSimRunning(false); analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
        @Override void deleteSliders(CircuitElm element) {
            Integer count = deleted.get(element);
            deleted.put(element, count == null ? 1 : count + 1);
            super.deleteSliders(element);
        }
    }
}
