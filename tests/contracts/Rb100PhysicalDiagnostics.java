package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;

/** Current native observations and focused test-only placement diagnostics for RB100. */
public final class Rb100PhysicalDiagnostics {
    private static final long SEED = 9007199254740993L;
    private static final long PLACEMENT_DIAGNOSTIC_SEED = -6587958367974952701L;
    private static final long ROUTING_DIAGNOSTIC_ROUTING_SEED = 8445301674744508279L;
    private static final long CASE_LIMIT_NANOS = 90000000000L;
    private static final long UNIT_LIMIT_NANOS = 5000000000L;
    private static final int WORK_LIMIT = 640;
    private static final long CLEANUP_LIMIT_MILLIS = 120000L;
    private static final int EXPECTED_COMPONENTS = 100;
    private static final int MAX_PLACEMENTS = 80;
    private static final int MAX_ROUTE_ATTEMPTS = 800;
    private static final int MAX_ROUTE_EXPANSIONS = 160000000;
    private static final int MAX_ACCESS_CELLS = 700000;
    private static int assertions;

    private Rb100PhysicalDiagnostics() { }

    static void runPlacementDiagnosticContracts() {
        long suiteStarted = System.nanoTime();
        int beforeAssertions = assertions;
        require(SeededPcbLayoutGenerator.CURRENT_VERSION == 26 &&
                "tsj-generation-dependencies-v28".equals(
                    GenerationDependencyContext.INTERPRETATION_EPOCH) &&
                Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                Rb100Placement.INTENT_VERSION == 9,
            "placement diagnostics identify current layout26/dependency-v28/plan1/model4/intent9 inputs");
        PlacementDiagnosticCase[] cases = new PlacementDiagnosticCase[] {
            runStructuralPlacementDiagnostic(), runCorridorWitnessPlacementDiagnostic()
        };
        int failures = 0;
        for (PlacementDiagnosticCase result : cases) {
            System.out.println("PCB_PLACEMENT_DIAGNOSTIC " + result.toJson());
            if (!"PASS".equals(result.status)) failures++;
        }
        long suiteMillis = nanosToMillis(System.nanoTime() - suiteStarted);
        if (cases.length != 2) failures++;
        if (failures != 0) {
            System.out.println("PCB_PLACEMENT_DIAGNOSTIC_SUMMARY {\"status\":\"FAIL\",\"cases\":" +
                cases.length + ",\"expectedCases\":2,\"failures\":" + failures +
                ",\"assertions\":" + (assertions - beforeAssertions) +
                ",\"suiteMs\":" + suiteMillis +
                ",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION + "}");
            throw new AssertionError("PCB placement diagnostics failures=" + failures);
        }
        System.out.println("PASS: PCB placement diagnostics contracts {\"status\":\"PASS\",\"cases\":2," +
            "\"fixtures\":[\"MEDIUM_GENERIC_EDGE_REGION_20_PART\",\"MEDIUM_ISOLATION_CORRIDOR_WITNESS_20_PART\"]," +
            "\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":\"" + GenerationDependencyContext.INTERPRETATION_EPOCH +
            "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
            ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"suiteMs\":" + suiteMillis +
            ",\"scope\":\"CURRENT_PLANNER_DIAGNOSTIC_CONTROLS_ONLY\"}");
    }

    static void runP07PolicyDiagnosticContracts() {
        long suiteStarted = System.nanoTime();
        int beforeAssertions = assertions;
        final String runId = UUID.randomUUID().toString();
        int failures = 0;
        P07PolicyDiagnosticCase[] results = new P07PolicyDiagnosticCase[2];
        Rb100Plan.Variant[] variants = variants();
        for (int index = 0; index < variants.length; index++) {
            P07PolicyDiagnosticCase result = runP07PolicyDiagnosticCase(variants[index], runId);
            results[index] = result;
            System.out.println("RB100_P07_POLICY_DIAGNOSTIC " + result.toJson());
            if (!"PASS".equals(result.status)) failures++;
        }
        RoutingInputMetadata summaryMetadata = null;
        if (results[0] != null && results[1] != null &&
                results[0].inputMetadata != null && results[1].inputMetadata != null &&
                results[0].inputMetadata.sameAs(results[1].inputMetadata)) {
            summaryMetadata = results[0].inputMetadata;
        } else {
            failures++;
        }
        long suiteMillis = nanosToMillis(System.nanoTime() - suiteStarted);
        if (results.length != 2 || failures != 0) {
            System.out.println("RB100_P07_POLICY_DIAGNOSTIC_SUMMARY {\"status\":\"FAIL\",\"cases\":" +
                results.length + ",\"expectedCases\":2,\"failures\":" + failures +
                ",\"protocolVersion\":2,\"runId\":\"" + runId + "\",\"suiteMs\":" +
                suiteMillis + ",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" +
                jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
                routingInputMetadataFields(summaryMetadata) +
                ",\"scope\":\"OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY\"," +
                "\"layoutQualification\":false}");
            throw new AssertionError("RB100 P07 policy diagnostic failures=" + failures);
        }
        System.out.println("PASS: RB100 P07 policy diagnostic contracts {\"status\":\"PASS\",\"cases\":2," +
            "\"authoredVariants\":2,\"variants\":[\"SHARED_HYSTERETIC_LINEAR\"," +
            "\"SEPARATE_DIRECT_SWITCHING\"],\"protocolVersion\":2,\"runId\":\"" + runId +
            "\",\"seed\":\"" + SEED + "\",\"placementSeed\":\"" +
            PLACEMENT_DIAGNOSTIC_SEED + "\",\"routingSeed\":\"" +
            ROUTING_DIAGNOSTIC_ROUTING_SEED + "\",\"layoutVersion\":" +
            SeededPcbLayoutGenerator.CURRENT_VERSION + ",\"dependencyEpoch\":\"" +
            jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) +
            "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
            ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            routingInputMetadataFields(summaryMetadata) +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"suiteMs\":" + suiteMillis +
            ",\"scope\":\"OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY\"," +
            "\"layoutQualification\":false}");
    }

    /** Actual-generator NULL/PROBE pair plus bounded actual-branch graph, allocation and LOAD12 capture. */
    static void runQualityZeroViaGraphContracts() {
        long suiteStarted=System.nanoTime();
        int beforeAssertions=assertions;
        final String runId=UUID.randomUUID().toString();
        final String nullOperationId="QUALITY_NULL";
        final String probeOperationId="QUALITY_PROBE";
        Rb100Plan.Variant variant=Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING;
        require(SeededPcbLayoutGenerator.CURRENT_VERSION==26 &&
            "tsj-generation-dependencies-v28".equals(
                GenerationDependencyContext.INTERPRETATION_EPOCH) &&
            Rb100Plan.PLAN_VERSION==1 && Rb100Plan.MODEL_VERSION==4 &&
            Rb100Placement.INTENT_VERSION==9,
            "quality scalar diagnostic identifies current layout26/dependency-v28/plan1/model4/intent9 inputs");
        require(CASE_LIMIT_NANOS==90000000000L && WORK_LIMIT==640 &&
            UNIT_LIMIT_NANOS==5000000000L && CLEANUP_LIMIT_MILLIS==120000L &&
            QualityScalarProbe.MAX_MANUAL_STEPS_PER_UNIT==32768,
            "quality scalar diagnostic retains the existing case/work/unit/cleanup budgets");

        P07PolicyDiagnosticCase baseline=runP07PolicyDiagnosticCase(variant,runId,null,null,
            "NULL",nullOperationId,null,null,true);
        if(!"PASS".equals(baseline.status) || baseline.policyResult==null ||
                baseline.policyResult.getLayout()!=null || baseline.qualitySubsetMarkerJson==null) {
            System.out.println("RB100_QUALITY_SCALAR_OPERATION {\"protocolVersion\":1,\"sourceRevision\":18,\"runId\":\""+
                jsonFull(runId)+"\",\"operationId\":\""+nullOperationId+
                "\",\"variant\":\""+variant.name()+"\",\"mode\":\"NULL\",\"result\":"+
                baseline.toJson()+"}");
            throw new AssertionError("RB100 quality scalar null baseline did not complete without a layout");
        }

        QualityScalarProbe probe=new QualityScalarProbe(runId,probeOperationId,variant);
        P07PolicyDiagnosticCase observed=runP07PolicyDiagnosticCase(variant,runId,null,baseline,
            "PROBE",probeOperationId,null,probe,true);
        if(probe.controlsJson==null) {
            if(baseline.qualitySubsetMarkerJson!=null)
                System.out.println(baseline.qualitySubsetMarkerJson);
            System.out.println(qualityScalarOperationJson(baseline,nullOperationId,"NULL"));
            if(observed.qualitySubsetMarkerJson!=null)
                System.out.println(observed.qualitySubsetMarkerJson);
            System.out.println(qualityScalarOperationJson(observed,probeOperationId,"PROBE"));
            throw new AssertionError("RB100 quality scalar actual Publisher controls were not completed");
        }
        System.out.println(probe.controlsJson);
        System.out.println(baseline.qualitySubsetMarkerJson);
        System.out.println(qualityScalarOperationJson(baseline,nullOperationId,"NULL"));
        System.out.println(observed.qualitySubsetMarkerJson==null ?
            "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS {\"protocol\":\"rb100-physical-subsets-v1\",\"runId\":\""+
                jsonFull(runId)+"\",\"variant\":\""+variant.name()+
                "\",\"status\":\"MISSING\",\"independentTwoLayerAccess\":false,\"layoutQualification\":false}" :
            observed.qualitySubsetMarkerJson);
        System.out.println(qualityScalarOperationJson(observed,probeOperationId,"PROBE"));

        probe.requireTerminalOperation(observed);
        boolean pairPassed=qualityScalarPairPassed(baseline,observed,variant);
        String event=probe.eventJson(observed);
        System.out.println(event);
        System.out.println(qualityScalarPairJson(baseline,observed,variant,runId,
            nullOperationId,probeOperationId,pairPassed,probe));
        require(pairPassed && "PASS".equals(baseline.status) && "PASS".equals(observed.status),
            "quality scalar pair preserves the canonical null/probe generator result");
        String pathEvent=probe.pathEventJson(observed,pairPassed);
        String pathSummary=qualityPathSummaryJson(runId,probe.pathSnapshot);

        long suiteMillis=nanosToMillis(System.nanoTime()-suiteStarted);
        require(suiteMillis<=480000L,
            "quality scalar two-operation suite remains within its frozen480s ceiling");
        System.out.println("PASS: RB100 quality scalar contracts {\"protocolVersion\":1,\"sourceRevision\":18,"+
            "\"status\":\"PASS\",\"operations\":2,\"nullOperations\":1,\"probeOperations\":1,"+
            "\"pairs\":1,\"confirmedScalarEvents\":1,\"variant\":\""+variant.name()+
            "\",\"runId\":\""+jsonFull(runId)+"\",\"rootSeed\":\""+SEED+
            "\",\"placementSeed\":\""+PLACEMENT_DIAGNOSTIC_SEED+
            "\",\"routingSeed\":\""+ROUTING_DIAGNOSTIC_ROUTING_SEED+
            "\",\"layoutVersion\":"+SeededPcbLayoutGenerator.CURRENT_VERSION+
            ",\"dependencyEpoch\":\""+jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)+
            "\",\"planVersion\":"+Rb100Plan.PLAN_VERSION+
            ",\"modelVersion\":"+Rb100Plan.MODEL_VERSION+
            ",\"physicalIntentVersion\":"+Rb100Placement.INTENT_VERSION+
            ",\"assertions\":"+(assertions-beforeAssertions)+",\"suiteMs\":"+suiteMillis+
            ",\"suiteLimitMs\":480000,\"manualDriverMillis\":"+probe.manualDriverMillis()+
            ",\"manualSliceSteps\":"+probe.manualSliceSteps+
            ",\"routeMillisStatus\":\"INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE\","+
            "\"feasibility\":\"UNKNOWN\",\"layoutQualification\":false,"+
            "\"scope\":\"OBSERVATIONAL_ACTUAL_GENERATOR_QUALITY_SCALAR_ONLY\"}");
        String graphEvent=probe.zeroViaGraphEventJson(observed,pairPassed);
        String graphSummary=probe.zeroViaGraphSummaryJson(observed,pairPassed);
        System.out.println(pathEvent);
        System.out.println(pathSummary);
        System.out.println(graphEvent);
        System.out.println(graphSummary);
        String allocationEvent=probe.prefixAllocationEventJson(observed,pairPassed);
        String allocationSummary=probe.prefixAllocationSummaryJson(observed,pairPassed);
        System.out.println(allocationEvent);
        System.out.println(allocationSummary);
        String load12Event=probe.load12EventJson(observed,pairPassed);
        String load12Summary=probe.load12SummaryJson(observed,pairPassed);
        System.out.println(load12Event);
        System.out.println(load12Summary);
    }

    /** Actual-generator null/capture pairs for the opt-in fixed-prefix graph seam. */
    static void runFixedPrefixCaptureContracts() {
        runFixedPrefixCaptureContracts(false);
    }

    /** Actual-generator completed NO_PATH null/capture pairs for the two frozen targets. */
    static void runFixedPrefixCaptureContracts(boolean completedNoPath) {
        long suiteStarted=System.nanoTime();
        int beforeAssertions=assertions;
        final String runId=UUID.randomUUID().toString();
        Rb100Plan.Variant[] authored=variants();
        P07PolicyDiagnosticCase[][] results=new P07PolicyDiagnosticCase[2][2];
        int failures=0;
        if(completedNoPath) runCompletedNoPathSelectorControls(authored);
        String[] operationIds={"SHARED_NULL","SHARED_CAPTURE","SEPARATE_NULL","SEPARATE_CAPTURE"};
        for(int variantIndex=0;variantIndex<authored.length;variantIndex++) {
            Rb100Plan.Variant variant=authored[variantIndex];
            FixedPrefixNoPathTarget target=completedNoPath?
                fixedPrefixNoPathTarget(variant):null;
            P07PolicyDiagnosticCase baseline=runP07PolicyDiagnosticCase(
                variant,runId,null,null,"NULL",operationIds[variantIndex*2],target);
            results[variantIndex][0]=baseline;
            System.out.println("RB100_FIXED_PREFIX_CAPTURE_OPERATION "+
                fixedPrefixOperationJson(baseline,"NULL",operationIds[variantIndex*2]));
            if(!"PASS".equals(baseline.status)) failures++;
            if(target!=null) {
                require("PASS".equals(baseline.status),
                    "completed NO_PATH capture stops before selection when its null operation is incomplete");
                requireCompletedNoPathOrderingReceipt(baseline,target);
            }

            FixedPrefixCaptureCollector capture=new FixedPrefixCaptureCollector(
                variant,runId,operationIds[variantIndex*2+1],target);
            P07PolicyDiagnosticCase observed=runP07PolicyDiagnosticCase(
                variant,runId,capture,baseline,"CAPTURE",operationIds[variantIndex*2+1],target);
            results[variantIndex][1]=observed;
            System.out.println("RB100_FIXED_PREFIX_CAPTURE_OPERATION "+
                fixedPrefixOperationJson(observed,"CAPTURE",operationIds[variantIndex*2+1]));
            if(!"PASS".equals(observed.status)) failures++;
            String pair=fixedPrefixPairJson(baseline,observed,variant,runId,
                operationIds[variantIndex*2],operationIds[variantIndex*2+1]);
            System.out.println("RB100_FIXED_PREFIX_CAPTURE_PAIR "+pair);
            if(!fixedPrefixPairPassed(baseline,observed,variant)) failures++;
        }
        long suiteMillis=nanosToMillis(System.nanoTime()-suiteStarted);
        if(suiteMillis>900000L) failures++;
        if(failures!=0 || authored.length!=2) {
            System.out.println("RB100_FIXED_PREFIX_CAPTURE_SUMMARY {\"status\":\"FAIL\",\"operations\":4,"+
                "\"expectedOperations\":4,\"failures\":"+failures+",\"runId\":\""+
                jsonFull(runId)+"\",\"suiteMs\":"+suiteMillis+
                ",\"layoutQualification\":false}");
            throw new AssertionError("RB100 fixed-prefix capture failures="+failures);
        }
        System.out.println("PASS: RB100 fixed-prefix capture contracts {\"status\":\"PASS\",\"operations\":4,"+
            "\"nullOperations\":2,\"captureOperations\":2,\"pairs\":2,\"graphs\":2,"+
            "\"authoredVariants\":2,\"variants\":[\"SHARED_HYSTERETIC_LINEAR\",\"SEPARATE_DIRECT_SWITCHING\"],"+
            (completedNoPath?"\"captureSelection\":\"COMPLETED_NO_PATH\",":"")+
            "\"runId\":\""+jsonFull(runId)+"\",\"rootSeed\":\""+SEED+
            "\",\"placementSeed\":\""+PLACEMENT_DIAGNOSTIC_SEED+"\",\"routingSeed\":\""+
            ROUTING_DIAGNOSTIC_ROUTING_SEED+"\",\"layoutVersion\":"+
            SeededPcbLayoutGenerator.CURRENT_VERSION+",\"dependencyEpoch\":\""+
            jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)+"\",\"planVersion\":"+
            Rb100Plan.PLAN_VERSION+",\"modelVersion\":"+Rb100Plan.MODEL_VERSION+
            ",\"physicalIntentVersion\":"+Rb100Placement.INTENT_VERSION+",\"assertions\":"+
            (assertions-beforeAssertions)+",\"suiteMs\":"+suiteMillis+
            ",\"suiteLimitMs\":900000"+
            ",\"scope\":\"OBSERVATIONAL_ACTUAL_GENERATOR_FIXED_PREFIX_CAPTURE_ONLY\",\"layoutQualification\":false}");
    }

    static void runP07BranchDiagnosticContracts() {
        long suiteStarted = System.nanoTime();
        int beforeAssertions = assertions;
        final String runId = UUID.randomUUID().toString();
        int failures = 0;
        BranchDiagnosticCase[] results = new BranchDiagnosticCase[2];
        Rb100Plan.Variant[] variants = variants();
        for (int index = 0; index < variants.length; index++) {
            BranchDiagnosticCase result = runAuthoredBranchDiagnostic(variants[index], runId);
            results[index] = result;
            System.out.println("RB100_P07_BRANCH_DIAGNOSTIC " + result.toJson());
            if (!"PASS".equals(result.status)) failures++;
        }
        RoutingInputMetadata summaryMetadata = null;
        if (results.length == 2 && results[0] != null && results[1] != null &&
                results[0].inputMetadata != null && results[1].inputMetadata != null &&
                results[0].inputMetadata.sameAs(results[1].inputMetadata)) {
            summaryMetadata = results[0].inputMetadata;
        } else {
            failures++;
        }
        long suiteMillis = nanosToMillis(System.nanoTime() - suiteStarted);
        if (results.length != 2 || failures != 0) {
            System.out.println("RB100_P07_BRANCH_DIAGNOSTIC_SUMMARY {\"status\":\"FAIL\",\"cases\":" +
                results.length + ",\"expectedCases\":2,\"failures\":" + failures +
                ",\"protocolVersion\":2,\"runId\":\"" + jsonFull(runId) +
                "\",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" +
                jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
                routingInputMetadataFields(summaryMetadata) +
                ",\"scope\":\"OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY\"," +
                "\"layoutQualification\":false}");
            throw new AssertionError("RB100 P07 branch diagnostic failures=" + failures);
        }
        String census = "[{\"variant\":\"" + jsonFull(results[0].subject) +
            "\",\"branchCount\":" + results[0].branches.size() +
            ",\"expansions\":" + results[0].routeResult.expansions +
            ",\"outcome\":\"" + jsonFull(results[0].routeResult.outcome) + "\"}," +
            "{\"variant\":\"" + jsonFull(results[1].subject) +
            "\",\"branchCount\":" + results[1].branches.size() +
            ",\"expansions\":" + results[1].routeResult.expansions +
            ",\"outcome\":\"" + jsonFull(results[1].routeResult.outcome) + "\"}]";
        System.out.println("PASS: RB100 P07 branch diagnostic contracts {\"status\":\"PASS\",\"cases\":2," +
            "\"authoredVariants\":2,\"variants\":[\"SHARED_HYSTERETIC_LINEAR\"," +
            "\"SEPARATE_DIRECT_SWITCHING\"],\"protocolVersion\":2,\"runId\":\"" +
            jsonFull(runId) + "\",\"seed\":\"" + SEED + "\",\"placementSeed\":\"" +
            PLACEMENT_DIAGNOSTIC_SEED + "\",\"routingSeed\":\"" +
            ROUTING_DIAGNOSTIC_ROUTING_SEED + "\",\"fixedPlacementAttempt\":4," +
            "\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":\"" +
            jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) + "\",\"planVersion\":" +
            Rb100Plan.PLAN_VERSION + ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            routingInputMetadataFields(summaryMetadata) + ",\"variantCensus\":" + census +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"suiteMs\":" + suiteMillis +
            ",\"scope\":\"OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY\"," +
            "\"layoutQualification\":false}");
    }

    private static P07PolicyDiagnosticCase runP07PolicyDiagnosticCase(
            final Rb100Plan.Variant variant, String runId) {
        return runP07PolicyDiagnosticCase(variant,runId,null,null,"LEGACY",null,null);
    }

    private static P07PolicyDiagnosticCase runP07PolicyDiagnosticCase(
            final Rb100Plan.Variant variant,String runId,
            final FixedPrefixCaptureCollector captureCollector,
            final P07PolicyDiagnosticCase expectedNull,final String mode,final String operationId,
            final FixedPrefixNoPathTarget noPathTarget) {
        return runP07PolicyDiagnosticCase(variant,runId,captureCollector,expectedNull,mode,
            operationId,noPathTarget,null,false);
    }

    private static P07PolicyDiagnosticCase runP07PolicyDiagnosticCase(
            final Rb100Plan.Variant variant,String runId,
            final FixedPrefixCaptureCollector captureCollector,
            final P07PolicyDiagnosticCase expectedNull,final String mode,final String operationId,
            final FixedPrefixNoPathTarget noPathTarget,
            final QualityScalarProbe qualityProbe,final boolean qualityScalarOperation) {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final P07PolicyDiagnosticCase result = new P07PolicyDiagnosticCase(
            variant.name(), SEED, runId);
        result.fixedPrefixMode=mode;
        result.fixedPrefixOperationId=operationId;
        result.fixedPrefixCollector=captureCollector;
        result.qualityScalarProbe=qualityProbe;
        result.qualityScalarOperation=qualityScalarOperation;
        final NativeContext[] context = new NativeContext[1];
        final Rb100Plan[] plan = new Rb100Plan[1];
        final Rb100ElectricalPilot.Candidate[] candidate =
            new Rb100ElectricalPilot.Candidate[1];
        final BoardIdentity[] identity = new BoardIdentity[1];
        final SeededPcbLayoutGenerator[] generator = new SeededPcbLayoutGenerator[1];
        final SeededPcbLayoutGenerator.Session[] session =
            new SeededPcbLayoutGenerator.Session[1];
        final MediumBoardPhysicalPolicy.Result[] policyResult =
            new MediumBoardPhysicalPolicy.Result[1];
        final PcbRoutingRejectedException[] terminalRejection =
            new PcbRoutingRejectedException[1];
        final boolean[] terminal = new boolean[] { false };
        try {
            result.phase = "CURRENT_SOURCE_IDENTITY";
            unit(budget, "p07-source-identity", new UnitAction() {
                public void run() {
                    require(SeededPcbLayoutGenerator.CURRENT_VERSION == 26 &&
                        "tsj-generation-dependencies-v28".equals(
                            GenerationDependencyContext.INTERPRETATION_EPOCH) &&
                        Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                        Rb100Placement.INTENT_VERSION == 9,
                        "P07 observation identifies layout26/dependency-v28/plan1/model4/intent9");
                    require(MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES == 6 &&
                        MediumBoardPhysicalPolicy.ROUTING_CANDIDATES == 3 &&
                        PcbLayerRoutingPrototype.MAX_EXPANSIONS == 1000000 &&
                        PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS == 100000 &&
                        PcbLayerRoutingPrototype.MAX_CELLS == 250000 &&
                        PcbLayerRoutingPrototype.ORDERINGS == 3 &&
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias == 48,
                        "P07 observation retains the existing placement, routing, branch, grid, ordering and via caps");
                    require(MAX_PLACEMENTS == 80 && MAX_ROUTE_ATTEMPTS == 800 &&
                        MAX_ROUTE_EXPANSIONS == 160000000 && MAX_ACCESS_CELLS == 700000 &&
                        CASE_LIMIT_NANOS == 90000000000L && WORK_LIMIT == 640 &&
                        UNIT_LIMIT_NANOS == 5000000000L && CLEANUP_LIMIT_MILLIS == 120000L,
                        "P07 observation retains existing aggregate and per-case resource ceilings");
                }
            });
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "p07-native-context", new UnitAction() {
                public void run() {
                    context[0] = new NativeContext();
                    context[0].activate();
                }
            });
            result.phase = "CANDIDATE_CONSTRUCTION";
            unit(budget, "p07-authored-candidate-construction", new UnitAction() {
                public void run() {
                    plan[0] = Rb100Plan.create(SEED, variant);
                    candidate[0] = Rb100ElectricalPilot.construct(plan[0]);
                }
            });
            identity[0] = valueUnit(budget, "p07-authored-board-identity",
                new UnitValue<BoardIdentity>() {
                    public BoardIdentity run() {
                        result.components = candidate[0].board().getComponentIds().size();
                        result.pads = candidate[0].board().getPadIds().size();
                        result.nets = candidate[0].board().getNetIds().size();
                        require(result.components == EXPECTED_COMPONENTS && result.pads == 246 &&
                            (result.nets == 59 || result.nets == 60),
                            "P07 observation uses the actual authored 100-component, 246-pad, 59-or-60-net board");
                        BoardIdentity captured=BoardIdentity.capture(candidate[0]);
                        if(noPathTarget!=null) {
                            result.boardNetIds=new TreeSet<String>(candidate[0].board().getNetIds());
                            require(result.boardNetIds.size()==result.nets &&
                                result.boardNetIds.equals(captured.nets),
                                "fixed-prefix net census starts from the unique authoritative authored board net IDs");
                        }
                        return captured;
                    }
                });

            result.phase = "PROCEDURAL_DECLARATION";
            unit(budget, "p07-procedural-layout-declaration", new UnitAction() {
                public void run() { ProceduralPcbLayout.declare(candidate[0].board()); }
            });
            unit(budget, "p07-post-declaration-input-identity", new UnitAction() {
                public void run() {
                    identity[0].requireUnchanged(candidate[0]);
                    result.inputMetadata = RoutingInputMetadata.capture(
                        candidate[0].board().getPlacementConstraints());
                    result.inputMetadata.requireCurrentP07Treatment();
                    require(MediumBoardPhysicalPolicy.selected(
                            candidate[0].board().getPlacementConstraints()),
                        "actual procedural board selects the existing MEDIUM_BOARD policy");
                }
            });
            final long[] namedSeeds = valueUnit(budget, "p07-named-layout-seeds",
                new UnitValue<long[]>() {
                    public long[] run() {
                        return new long[] {
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.PLACEMENT),
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.ROUTING)
                        };
                    }
                });
            result.placementSeed = namedSeeds[0];
            result.routingSeed = namedSeeds[1];
            unit(budget, "p07-assert-named-seeds", new UnitAction() {
                public void run() {
                    require(result.placementSeed == PLACEMENT_DIAGNOSTIC_SEED &&
                        result.routingSeed == ROUTING_DIAGNOSTIC_ROUTING_SEED,
                        "P07 session uses the exact frozen signed placement and routing seeds");
                }
            });

            final SeededPcbLayoutGenerator.AttemptObserver observer =
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) {
                        result.observerChecks++;
                        budget.checkpoint();
                        if(qualityProbe!=null)
                            qualityProbe.guardNoUnexpectedReady(session[0]);
                    }
                };
            result.phase = "MEDIUM_SESSION_BEGIN";
            unit(budget, "p07-generator-and-session-begin", new UnitAction() {
                public void run() {
                    generator[0] = new SeededPcbLayoutGenerator(
                        StandardPcbFootprintProviders.createRegistry(), observer);
                    PcbLayerRoutingPrototype.FixedPrefixGraphObserver graphObserver=
                        captureCollector!=null?captureCollector:qualityProbe;
                    session[0] = graphObserver == null ?
                        generator[0].begin(candidate[0].board(),result.placementSeed,result.routingSeed) :
                        generator[0].beginWithFixedPrefixObservation(candidate[0].board(),
                            result.placementSeed,result.routingSeed,graphObserver);
                    if(qualityProbe!=null) qualityProbe.armReadyGuard();
                }
            });

            result.phase = "MEDIUM_SESSION_ADVANCE";
            while (!terminal[0]) {
                if(qualityProbe!=null && qualityProbe.shouldManuallyAdvance(session[0])) {
                    final String unitName=qualityProbe.nextManualUnitName();
                    valueUnit(budget,unitName,
                        new UnitValue<Object>() {
                            public Object run() {
                                qualityProbe.advanceManualGroup(session[0],budget);
                                return null;
                            }
                        });
                    continue;
                }
                result.advanceCalls++;
                try {
                    terminal[0] = valueUnit(budget,
                        "p07-session-advance-" + result.advanceCalls,
                        new UnitValue<Boolean>() {
                            public Boolean run() {
                                return Boolean.valueOf(session[0].advance());
                            }
                        }).booleanValue();
                    if(qualityProbe!=null)
                        qualityProbe.observeGeneratorAdvance(session[0]);
                    if (terminal[0]) result.sessionTerminalStatus = "ROUTED";
                } catch (PcbRoutingRejectedException expectedExhaustion) {
                    terminalRejection[0] = expectedExhaustion;
                    terminal[0] = true;
                    result.sessionTerminalStatus = "EXHAUSTED";
                    if(qualityProbe!=null)
                        qualityProbe.observeGeneratorAdvance(session[0]);
                }
            }
            result.phase = "TERMINAL_POLICY_RESULT_CAPTURE";
            valueUnit(budget, "p07-terminal-policy-result-capture", new UnitValue<Object>() {
                public Object run() {
                    require(result.advanceCalls > 0 && result.observerChecks > 0,
                        "P07 policy observation advances the real session under budget checkpoints");
                    policyResult[0] = session[0].mediumResult();
                    require(policyResult[0] != null && policyResult[0].getStatistics() != null,
                        "terminal medium session exposes its actual immutable result receipt");
                    result.policyResult = policyResult[0];
                    result.sessionRejection = terminalRejection[0];
                    if (policyResult[0].accepted()) {
                        require(terminalRejection[0] == null &&
                            "ROUTED".equals(result.sessionTerminalStatus) &&
                            "SUCCESS".equals(policyResult[0].getStatistics().outcome) &&
                            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                                policyResult[0].getStatistics().selectedRoutePolicy) &&
                            policyResult[0].getLayout() != null &&
                            session[0].result() == policyResult[0].getLayout(),
                            "completed medium session returns its selected routed layout and success receipt");
                    } else {
                        require(terminalRejection[0] != null &&
                            "EXHAUSTED".equals(result.sessionTerminalStatus) &&
                            terminalRejection[0].isExhausted() &&
                            terminalRejection[0].getSeed() == result.placementSeed &&
                            "REJECTED".equals(policyResult[0].getStatistics().outcome) &&
                            policyResult[0].getFailure() != null,
                            "expected exhausted rejection carries the installed terminal rejected-policy receipt");
                    }
                    verifyP07PolicyStatistics(policyResult[0]);
                    identity[0].requireUnchanged(candidate[0]);
                    if(captureCollector!=null &&
                            captureCollector.completedNoPathTarget!=null) {
                        require(result.boardNetIds!=null &&
                            result.boardNetIds.equals(new TreeSet<String>(
                                candidate[0].board().getNetIds())) &&
                            result.boardNetIds.size()==result.nets,
                            "completed NO_PATH census verifies the pre-generation net IDs remain unchanged after generation");
                    }
                    require(CircuitElm.sim == context[0].sim &&
                        CirSim.theSim == context[0].sim && context[0].sim.elmList == context[0].rawGraph &&
                        context[0].rawGraph.isEmpty(),
                        "P07 geometry session leaves the authored CircuitJS elements uninstalled");
                    result.solverGraphIdentityStatus = "PASS";
                    result.boardIdentityStatus = "PASS";
                    result.captureLayoutOutput();
                    if(qualityProbe!=null)
                        qualityProbe.confirmTerminalOperation(result,session[0],generator[0],
                            policyResult[0]);
                    if(captureCollector==null) {
                        if(noPathTarget==null && "NULL".equals(mode))
                            requireExpectedFixedPrefixPlacement(variant,policyResult[0].getStatistics());
                        else if(noPathTarget!=null)
                            requireCompletedNoPathOrderingReceipt(result,noPathTarget);
                    } else {
                        require(expectedNull!=null && "PASS".equals(expectedNull.status) &&
                            expectedNull.policyResult!=null && expectedNull.policyResult.getStatistics()!=null,
                            "capture run has its terminal null-observer actual-generator baseline");
                        if(noPathTarget==null)
                            requireExpectedFixedPrefixPlacement(variant,policyResult[0].getStatistics());
                        else {
                            requireCompletedNoPathOrderingReceipt(expectedNull,noPathTarget);
                            requireCompletedNoPathOrderingReceipt(result,noPathTarget);
                        }
                        requireFixedPrefixPairParity(expectedNull,result);
                        captureCollector.requireComplete(result);
                    }
                    return null;
                }
            });
            if(policyResult[0].accepted()) {
                result.phase="AUTHORED_INDEPENDENT_PHYSICAL_SUBSETS";
                unit(budget,"p07-authored-independent-physical-subsets",new UnitAction() {
                    public void run() {
                        P07OrderingCandidatesContractTest.verifyAuthored100PhysicalSubsets(
                            candidate[0].board(),policyResult[0].getLayout());
                        identity[0].requireUnchanged(candidate[0]);
                    }
                });
            }
            result.qualitySubsetMarkerJson="RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS {\"protocol\":\"rb100-physical-subsets-v1\",\"runId\":\""+jsonFull(runId)+
                "\",\"variant\":\""+variant.name()+"\",\"status\":\""+
                (policyResult[0].accepted()?"PASS_GEOMETRY_CLEARANCE_GRAPH_RASTER_ONLY":
                    "NOT_RUN_NO_LAYOUT")+"\",\"independentTwoLayerAccess\":false,"+
                "\"layoutQualification\":false}";
            if(!qualityScalarOperation) System.out.println(result.qualitySubsetMarkerJson);
            if(captureCollector!=null) {
                result.phase="FIXED_PREFIX_GRAPH_SERIALIZATION";
                valueUnit(budget,"p07-fixed-prefix-graph-serialization-and-emission",
                    new UnitValue<Object>() {
                        public Object run() {
                            long serializationStarted=System.nanoTime();
                            FixedPrefixGraphEmission emission=serializeFixedPrefixGraph(
                                captureCollector,variant);
                            result.fixedPrefixGraphCaseId=emission.caseId;
                            result.fixedPrefixGraphSha256=emission.sha256;
                            result.fixedPrefixGraphBytes=emission.bytes;
                            result.fixedPrefixSerializationMillis=nanosToMillis(
                                System.nanoTime()-serializationStarted);
                            System.out.println("P07_FIXED_PREFIX_GRAPH_DATA "+emission.payload);
                            System.out.println("P07_FIXED_PREFIX_CAPTURE_FACT "+
                                fixedPrefixCaptureFactJson(result,captureCollector,
                                    emission,result.fixedPrefixSerializationMillis,
                                    operationId,mode));
                            if(captureCollector.completedNoPathTarget!=null) {
                                require(result.boardNetIds!=null &&
                                    result.boardNetIds.size()==result.nets,
                                    "completed NO_PATH census has the pre-generation authoritative net-ID set");
                                System.out.println("RB100_FIXED_PREFIX_NET_CENSUS "+
                                    fixedPrefixNetCensusJson(result,emission,operationId));
                            }
                            return null;
                        }
                    });
            }
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }

        result.phase = "FINAL_REPORT_PREPARATION";
        try {
            unit(budget, "p07-final-report-preparation", new UnitAction() {
                public void run() { result.prepareFacts(); }
            });
        } catch (RuntimeException failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        } catch (Error failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        }
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], candidate[0]);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed &&
            result.policyResult != null && result.inputMetadata != null &&
            result.boardIdentityStatus.equals("PASS") &&
            result.solverGraphIdentityStatus.equals("PASS") &&
            ("ROUTED".equals(result.sessionTerminalStatus) ||
                "EXHAUSTED".equals(result.sessionTerminalStatus)) &&
            result.operationMillis <= nanosToMillis(CASE_LIMIT_NANOS) &&
            result.workUnits <= WORK_LIMIT && result.maxUnitMillis <=
                nanosToMillis(UNIT_LIMIT_NANOS) ? "PASS" : "FAIL";
        return result;
    }

    private static void requireExpectedFixedPrefixPlacement(Rb100Plan.Variant variant,
            MediumBoardPhysicalPolicy.Statistics statistics) {
        int[] expected=expectedFixedPrefixEligibleAttempts(variant);
        require(statistics!=null && statistics.routeEligiblePlacementAttempts.size()==expected.length,
            "actual generator request retains the frozen post-priority eligible placement census");
        for(int i=0;i<expected.length;i++)
            require(statistics.routeEligiblePlacementAttempts.get(i).intValue()==expected[i],
                "actual generator keeps the exact post-priority eligible placement order");
        require(firstP07RequestAttempt(statistics)==4,
            "actual first required-P07 request selects eligible placement attempt4 without a forced selector");
    }

    private static FixedPrefixNoPathTarget fixedPrefixNoPathTarget(Rb100Plan.Variant variant) {
        if(variant==Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR)
            return new FixedPrefixNoPathTarget(2,2,0,"F_DRAIN");
        if(variant==Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING)
            return new FixedPrefixNoPathTarget(0,0,4,"A_COIL_LOW");
        throw new IllegalArgumentException("Unknown authored fixed-prefix NO_PATH variant");
    }

    /** Cheap primitive controls exercise the exact selector predicate before either capture. */
    private static void runCompletedNoPathSelectorControls(Rb100Plan.Variant[] authored) {
        require(authored!=null && authored.length==2,
            "completed NO_PATH selector controls cover the two authored variants");
        for(Rb100Plan.Variant variant:authored) {
            FixedPrefixNoPathTarget target=fixedPrefixNoPathTarget(variant);
            require(matchesCompletedNoPathTarget(target,target.requestIndex,target.eligibleIndex,
                target.placementAttempt,target.netId,"NO_PATH"),
                "completed NO_PATH selector accepts the exact frozen request, placement and net");
            require(!matchesCompletedNoPathTarget(target,target.requestIndex+1,target.eligibleIndex,
                target.placementAttempt,target.netId,"NO_PATH"),
                "completed NO_PATH selector ignores another request");
            require(!matchesCompletedNoPathTarget(target,target.requestIndex,target.eligibleIndex,
                target.placementAttempt,"OTHER_NET","NO_PATH"),
                "completed NO_PATH selector ignores a different branch net");
            require(!matchesCompletedNoPathTarget(target,target.requestIndex,target.eligibleIndex,
                target.placementAttempt,target.netId,"TOTAL_SEARCH_LIMIT"),
                "completed NO_PATH selector rejects non-NO_PATH terminal branches");
            require(!matchesCompletedNoPathTarget(target,target.requestIndex,target.eligibleIndex,
                target.placementAttempt,target.netId,"FOUND"),
                "completed NO_PATH selector rejects found branches");
            require(shouldSelectFirstCompletedNoPathTarget(target,target.requestIndex,
                target.eligibleIndex,target.placementAttempt,target.netId,"NO_PATH",false) &&
                !shouldSelectFirstCompletedNoPathTarget(target,target.requestIndex,
                    target.eligibleIndex,target.placementAttempt,target.netId,"NO_PATH",true),
                "completed NO_PATH selector selects only the first matching lineage");
            boolean wrongPlacementRejected=false;
            try {
                matchesCompletedNoPathTarget(target,target.requestIndex,target.eligibleIndex+1,
                    target.placementAttempt+1,target.netId,"NO_PATH");
            } catch(IllegalArgumentException expected) {
                wrongPlacementRejected=true;
            }
            require(wrongPlacementRejected,
                "completed NO_PATH selector fails closed on target-request placement mismatch");
        }
    }

    private static boolean matchesCompletedNoPathTarget(FixedPrefixNoPathTarget target,
            int requestIndex,int eligibleIndex,int placementAttempt,String netId,
            String terminalReason) {
        if(requestIndex!=target.requestIndex) return false;
        if(eligibleIndex!=target.eligibleIndex || placementAttempt!=target.placementAttempt)
            throw new IllegalArgumentException(
                "Completed NO_PATH target request selected a different placement");
        return target.netId.equals(netId) && "NO_PATH".equals(terminalReason);
    }

    private static boolean shouldSelectFirstCompletedNoPathTarget(
            FixedPrefixNoPathTarget target,int requestIndex,int eligibleIndex,
            int placementAttempt,String netId,String terminalReason,boolean alreadySelected) {
        return !alreadySelected && matchesCompletedNoPathTarget(target,requestIndex,
            eligibleIndex,placementAttempt,netId,terminalReason);
    }

    /** Bind capture selection to the exact completed request receipt from its null run. */
    private static void requireCompletedNoPathOrderingReceipt(P07PolicyDiagnosticCase result,
            FixedPrefixNoPathTarget target) {
        MediumBoardPhysicalPolicy.Statistics statistics=statistics(result);
        require(result!=null && result.policyResult!=null && statistics!=null &&
            result.boardNetIds!=null && result.boardNetIds.contains(target.netId) &&
            statistics.orderingSearchReceipts!=null && statistics.routeOutcomes!=null &&
            target.requestIndex<statistics.orderingSearchReceipts.size() &&
            target.requestIndex<statistics.routeOutcomes.size(),
            "completed NO_PATH target has an exact request-indexed null/capture receipt");
        MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt=
            statistics.orderingSearchReceipts.get(target.requestIndex);
        String expectedOutcome=MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER+"@"+
            target.placementAttempt+"=REJECTED:"+
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER+":"+target.netId+":NO_PATH";
        require(receipt!=null && receipt.attempt==target.placementAttempt &&
            receipt.comparisonComplete &&
            receipt.completedOrderings==PcbLayerRoutingPrototype.ORDERINGS &&
            (target.netId+":NO_PATH").equals(receipt.termination) &&
            expectedOutcome.equals(statistics.routeOutcomes.get(target.requestIndex)) &&
            statistics.routeEligiblePlacementAttempts.size()>target.eligibleIndex &&
            statistics.routeEligiblePlacementAttempts.get(target.eligibleIndex).intValue()==
                target.placementAttempt && target.requestIndex==target.eligibleIndex,
            "completed NO_PATH target binds its exact eligible placement and completed null ordering receipt, never a total-search-limit receipt");
    }

    private static int[] expectedFixedPrefixEligibleAttempts(Rb100Plan.Variant variant) {
        if(variant==Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR)
            return new int[]{4,2,0,1,3};
        if(variant==Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING)
            return new int[]{4,2,3,0,1};
        throw new IllegalArgumentException("Unknown authored fixed-prefix variant");
    }

    private static int firstP07RequestAttempt(MediumBoardPhysicalPolicy.Statistics statistics) {
        if(statistics==null) return -1;
        String prefix=MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER+"@";
        for(String outcome:statistics.routeOutcomes) if(outcome.startsWith(prefix)) {
            int end=outcome.indexOf('=',prefix.length());
            if(end<=prefix.length()) return -1;
            try { return Integer.parseInt(outcome.substring(prefix.length(),end)); }
            catch(NumberFormatException malformed) { return -1; }
        }
        return -1;
    }

    private static void requireFixedPrefixPairParity(P07PolicyDiagnosticCase baseline,
            P07PolicyDiagnosticCase observed) {
        require(baseline!=null && observed!=null && baseline.policyResult!=null &&
            observed.policyResult!=null && baseline.policyResult.getStatistics()!=null &&
            observed.policyResult.getStatistics()!=null,
            "null/capture pair has complete actual generator results");
        MediumBoardPhysicalPolicy.Statistics first=baseline.policyResult.getStatistics();
        MediumBoardPhysicalPolicy.Statistics second=observed.policyResult.getStatistics();
        require(first.toCanonical().equals(second.toCanonical()),
            "fixed-prefix observation preserves the full deterministic medium policy receipt");
        require(baseline.inputMetadata!=null &&
            baseline.inputMetadata.sameAs(observed.inputMetadata),
            "null/capture pair preserves the actual medium placement and routing inputs");
        require(baseline.sessionTerminalStatus.equals(observed.sessionTerminalStatus) &&
            baseline.layoutOutputJson.equals(observed.layoutOutputJson),
            "fixed-prefix observation preserves terminal session status and exact layout output");
    }

    private static boolean fixedPrefixPairPassed(P07PolicyDiagnosticCase baseline,
            P07PolicyDiagnosticCase observed,Rb100Plan.Variant variant) {
        if(baseline==null || observed==null || baseline.policyResult==null ||
                observed.policyResult==null || baseline.policyResult.getStatistics()==null ||
                observed.policyResult.getStatistics()==null) return false;
        MediumBoardPhysicalPolicy.Statistics first=baseline.policyResult.getStatistics();
        MediumBoardPhysicalPolicy.Statistics second=observed.policyResult.getStatistics();
        return "PASS".equals(baseline.status) && "PASS".equals(observed.status) &&
            baseline.inputMetadata!=null && baseline.inputMetadata.sameAs(observed.inputMetadata) &&
            sameIntVector(first.routeEligiblePlacementAttempts,second.routeEligiblePlacementAttempts) &&
            firstP07RequestAttempt(first)==firstP07RequestAttempt(second) &&
            first.routeOutcomes.equals(second.routeOutcomes) &&
            primaryRoutingOrders(first)==primaryRoutingOrders(second) &&
            primaryRoutingExpansions(first)==primaryRoutingExpansions(second) &&
            first.toCanonical().equals(second.toCanonical()) &&
            baseline.sessionTerminalStatus.equals(observed.sessionTerminalStatus) &&
            baseline.layoutOutputJson.equals(observed.layoutOutputJson) &&
            sameExpectedEligible(first.routeEligiblePlacementAttempts,variant);
    }

    private static String qualityScalarOperationJson(P07PolicyDiagnosticCase result,
            String operationId,String mode) {
        return "RB100_QUALITY_SCALAR_OPERATION {\"protocolVersion\":1,\"sourceRevision\":18,"+
            "\"runId\":\""+jsonFull(result.runId)+"\",\"operationId\":\""+
            jsonFull(operationId)+"\",\"variant\":\""+jsonFull(result.subject)+
            "\",\"mode\":\""+jsonFull(mode)+"\",\"result\":"+result.toJson()+"}";
    }

    private static boolean qualityScalarPairPassed(P07PolicyDiagnosticCase baseline,
            P07PolicyDiagnosticCase observed,Rb100Plan.Variant variant) {
        if(!fixedPrefixPairPassed(baseline,observed,variant) ||
                baseline.policyResult.getLayout()!=null || observed.policyResult.getLayout()!=null)
            return false;
        MediumBoardPhysicalPolicy.Statistics first=baseline.policyResult.getStatistics();
        MediumBoardPhysicalPolicy.Statistics second=observed.policyResult.getStatistics();
        return first!=null && second!=null &&
            first.routeEligiblePlacementAttempts.equals(second.routeEligiblePlacementAttempts) &&
            firstP07RequestAttempt(first)==4 && firstP07RequestAttempt(first)==firstP07RequestAttempt(second) &&
            first.routeOutcomes.equals(second.routeOutcomes) &&
            primaryRoutingOrders(first)==primaryRoutingOrders(second) &&
            primaryRoutingExpansions(first)==primaryRoutingExpansions(second) &&
            first.toCanonical().equals(second.toCanonical()) &&
            baseline.components==observed.components && baseline.pads==observed.pads &&
            baseline.nets==observed.nets && "PASS".equals(baseline.boardIdentityStatus) &&
            "PASS".equals(observed.boardIdentityStatus) &&
            "PASS".equals(baseline.solverGraphIdentityStatus) &&
            "PASS".equals(observed.solverGraphIdentityStatus) &&
            baseline.sessionTerminalStatus.equals(observed.sessionTerminalStatus) &&
            baseline.layoutOutputJson.equals(observed.layoutOutputJson);
    }

    private static String qualityScalarPairJson(P07PolicyDiagnosticCase baseline,
            P07PolicyDiagnosticCase observed,Rb100Plan.Variant variant,String runId,
            String nullOperationId,String probeOperationId,boolean passed,
            QualityScalarProbe probe) {
        MediumBoardPhysicalPolicy.Statistics first=statistics(baseline);
        MediumBoardPhysicalPolicy.Statistics second=statistics(observed);
        boolean eligible=first!=null && second!=null &&
            first.routeEligiblePlacementAttempts.equals(second.routeEligiblePlacementAttempts);
        boolean firstRequest=first!=null && second!=null && firstP07RequestAttempt(first)==4 &&
            firstP07RequestAttempt(first)==firstP07RequestAttempt(second);
        boolean outcomes=first!=null && second!=null && first.routeOutcomes.equals(second.routeOutcomes);
        boolean orders=first!=null && second!=null &&
            primaryRoutingOrders(first)==primaryRoutingOrders(second);
        boolean expansions=first!=null && second!=null &&
            primaryRoutingExpansions(first)==primaryRoutingExpansions(second);
        boolean canonical=first!=null && second!=null && first.toCanonical().equals(second.toCanonical());
        boolean board=baseline!=null && observed!=null && baseline.components==observed.components &&
            baseline.pads==observed.pads && baseline.nets==observed.nets &&
            "PASS".equals(baseline.boardIdentityStatus) && "PASS".equals(observed.boardIdentityStatus) &&
            baseline.inputMetadata!=null && baseline.inputMetadata.sameAs(observed.inputMetadata);
        boolean solver=baseline!=null && observed!=null &&
            "PASS".equals(baseline.solverGraphIdentityStatus) &&
            "PASS".equals(observed.solverGraphIdentityStatus);
        boolean terminal=baseline!=null && observed!=null &&
            baseline.sessionTerminalStatus.equals(observed.sessionTerminalStatus);
        boolean layout=baseline!=null && observed!=null &&
            baseline.layoutOutputJson.equals(observed.layoutOutputJson);
        boolean absent=baseline!=null && observed!=null && baseline.policyResult!=null &&
            observed.policyResult!=null && baseline.policyResult.getLayout()==null &&
            observed.policyResult.getLayout()==null && baseline.layoutOutputJson.contains("\"layoutReturned\":false") &&
            observed.layoutOutputJson.contains("\"layoutReturned\":false");
        return "RB100_QUALITY_SCALAR_PAIR {\"protocolVersion\":1,\"sourceRevision\":18,"+
            "\"runId\":\""+jsonFull(runId)+"\",\"variant\":\""+variant.name()+
            "\",\"nullOperationId\":\""+nullOperationId+"\",\"probeOperationId\":\""+
            probeOperationId+"\",\"rootSeed\":\""+SEED+"\",\"placementSeed\":\""+
            PLACEMENT_DIAGNOSTIC_SEED+"\",\"routingSeed\":\""+ROUTING_DIAGNOSTIC_ROUTING_SEED+
            "\",\"layoutVersion\":"+SeededPcbLayoutGenerator.CURRENT_VERSION+
            ",\"dependencyEpoch\":\""+jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)+
            "\",\"planVersion\":"+Rb100Plan.PLAN_VERSION+",\"modelVersion\":"+
            Rb100Plan.MODEL_VERSION+",\"physicalIntentVersion\":"+Rb100Placement.INTENT_VERSION+
            ",\"status\":\""+(passed?"PASS":"FAIL")+"\",\"routeEligiblePlacementAttemptsEqual\":"+
            eligible+",\"firstP07RequestAttemptEqual\":"+firstRequest+
            ",\"routeOutcomesEqual\":"+outcomes+",\"primaryRoutingOrdersEqual\":"+orders+
            ",\"primaryRoutingExpansionsEqual\":"+expansions+",\"policyCanonicalEqual\":"+canonical+
            ",\"boardIdentityEqual\":"+board+",\"solverIdentityEqual\":"+solver+
            ",\"terminalStatusEqual\":"+terminal+",\"layoutOutputEqual\":"+layout+
            ",\"layoutAbsent\":"+absent+
",\"routeMillisStatus\":\"INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE\","+
            "\"layoutQualification\":false}";
    }

    private static String fixedPrefixPairJson(P07PolicyDiagnosticCase baseline,
            P07PolicyDiagnosticCase observed,Rb100Plan.Variant variant,String runId,
            String nullOperationId,String captureOperationId) {
        MediumBoardPhysicalPolicy.Statistics first=statistics(baseline);
        MediumBoardPhysicalPolicy.Statistics second=statistics(observed);
        boolean eligible=first!=null && second!=null && sameIntVector(
            first.routeEligiblePlacementAttempts,second.routeEligiblePlacementAttempts);
        boolean firstRequest=first!=null && second!=null &&
            firstP07RequestAttempt(first)==firstP07RequestAttempt(second);
        boolean outcomes=first!=null && second!=null && first.routeOutcomes.equals(second.routeOutcomes);
        boolean orders=first!=null && second!=null && primaryRoutingOrders(first)==primaryRoutingOrders(second);
        boolean expansions=first!=null && second!=null &&
            primaryRoutingExpansions(first)==primaryRoutingExpansions(second);
        boolean canonicalAndTerminal=first!=null && second!=null &&
            first.toCanonical().equals(second.toCanonical()) &&
            baseline!=null && observed!=null && baseline.inputMetadata!=null &&
            baseline.inputMetadata.sameAs(observed.inputMetadata) &&
            baseline.sessionTerminalStatus.equals(observed.sessionTerminalStatus);
        boolean layout=baseline!=null && observed!=null &&
            baseline.layoutOutputJson.equals(observed.layoutOutputJson);
        boolean pass=fixedPrefixPairPassed(baseline,observed,variant);
        return "{\"protocolVersion\":1,\"runId\":\""+jsonFull(runId)+
            "\",\"variant\":\""+variant.name()+"\",\"nullOperationId\":\""+
            nullOperationId+"\",\"captureOperationId\":\""+captureOperationId+
            "\",\"eligiblePlacementAttemptsEqual\":"+eligible+
            ",\"firstP07RequestAttemptEqual\":"+firstRequest+
            ",\"routeOutcomesEqual\":"+outcomes+
            ",\"primaryRoutingOrdersEqual\":"+orders+
            ",\"primaryRoutingExpansionsEqual\":"+expansions+
            ",\"policyCanonicalAndTerminalEqual\":"+canonicalAndTerminal+
            ",\"layoutOutputEqual\":"+layout+
            ",\"status\":\""+(pass?"PASS":"FAIL")+"\",\"parityStatus\":\""+
            (pass?"PASS":"FAIL")+"\",\"scope\":\"OBSERVATIONAL_NULL_CAPTURE_PARITY_ONLY\","+
            "\"layoutQualification\":false}";
    }

    private static MediumBoardPhysicalPolicy.Statistics statistics(P07PolicyDiagnosticCase result) {
        return result==null || result.policyResult==null?null:result.policyResult.getStatistics();
    }

    private static int primaryRoutingOrders(MediumBoardPhysicalPolicy.Statistics statistics) {
        return statistics.routingOrders-(statistics.packingProbe==null?0:statistics.packingProbe.orderings);
    }

    private static int primaryRoutingExpansions(MediumBoardPhysicalPolicy.Statistics statistics) {
        return statistics.routingExpansions-(statistics.packingProbe==null?0:statistics.packingProbe.expansions);
    }

    private static boolean sameExpectedEligible(Vector<Integer> attempts,Rb100Plan.Variant variant) {
        int[] expected=expectedFixedPrefixEligibleAttempts(variant);
        if(attempts==null || attempts.size()!=expected.length) return false;
        for(int i=0;i<expected.length;i++) if(attempts.get(i).intValue()!=expected[i]) return false;
        return true;
    }

    private static boolean sameIntVector(Vector<Integer> first,Vector<Integer> second) {
        return first!=null && first.equals(second);
    }

    private static String fixedPrefixOperationJson(P07PolicyDiagnosticCase result,
            String mode,String operationId) {
        MediumBoardPhysicalPolicy.Statistics statistics=result.policyResult==null?
            null:result.policyResult.getStatistics();
        String canonical=statistics==null?"":statistics.toCanonical();
        return "{\"protocolVersion\":1,\"runId\":\""+jsonFull(result.runId)+
            "\",\"operationId\":\""+jsonFull(operationId)+"\",\"variant\":\""+
            jsonFull(result.subject)+"\",\"mode\":\""+mode+"\",\"status\":\""+
            result.status+"\",\"terminalStatus\":\""+jsonFull(result.sessionTerminalStatus)+
            "\",\"policyOutcome\":"+(statistics==null?"null":"\""+jsonFull(statistics.outcome)+"\"")+
            ",\"selectedRoutePolicy\":"+(statistics==null?"null":nullableString(statistics.selectedRoutePolicy))+
            ",\"policySelectedPlacementAttempt\":"+(statistics==null?-1:statistics.selectedPlacementAttempt)+
            ",\"firstP07RequestAttempt\":"+firstP07RequestAttempt(statistics)+
            ",\"routeEligiblePlacementAttempts\":"+integerVectorJson(statistics==null?
                new Vector<Integer>():statistics.routeEligiblePlacementAttempts)+
            ",\"routeEligibilitySkips\":"+stringVectorJson(statistics==null?
                new Vector<String>():statistics.routeEligibilitySkips)+
            ",\"routeOutcomes\":"+stringVectorJson(statistics==null?
                new Vector<String>():statistics.routeOutcomes)+
            ",\"primaryRoutingOrders\":"+(statistics==null?0:primaryRoutingOrders(statistics))+
            ",\"primaryRoutingExpansions\":"+(statistics==null?0:primaryRoutingExpansions(statistics))+
            ",\"policyCanonicalLength\":"+canonical.length()+
            ",\"policyCanonicalSha256\":\""+sha256Hex(canonical)+"\",\"policyCanonical\":\""+
            jsonFull(canonical)+"\",\"layoutOutput\":"+result.layoutOutputJson+
            ",\"components\":"+result.components+",\"pads\":"+result.pads+
            ",\"nets\":"+result.nets+",\"rootSeed\":\""+result.seed+
            "\",\"placementSeed\":\""+result.placementSeed+"\",\"routingSeed\":\""+
            result.routingSeed+"\",\"layoutVersion\":"+SeededPcbLayoutGenerator.CURRENT_VERSION+
            ",\"dependencyEpoch\":\""+jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)+
            "\",\"planVersion\":"+Rb100Plan.PLAN_VERSION+",\"modelVersion\":"+
            Rb100Plan.MODEL_VERSION+",\"physicalIntentVersion\":"+Rb100Placement.INTENT_VERSION+
            ",\"operationMs\":"+result.operationMillis+",\"cleanupMs\":"+result.cleanupMillis+
            ",\"cleanupStatus\":\""+(result.cleanupPassed?"PASS":"FAIL")+
            "\",\"workUnits\":"+result.workUnits+",\"maxUnitMs\":"+result.maxUnitMillis+
            ",\"failurePhase\":\""+jsonFull(result.failurePhase)+"\",\"failureType\":\""+
            jsonFull(typedFailure(result.failure))+"\",\"cleanupFailureType\":\""+
            jsonFull(typedFailure(result.cleanupFailure))+"\",\"scope\":\"OBSERVATIONAL_ACTUAL_GENERATOR_FIXED_PREFIX_CAPTURE_ONLY\","+
            "\"layoutQualification\":false}";
    }

    private static String fixedPrefixCaptureFactJson(P07PolicyDiagnosticCase result,
            FixedPrefixCaptureCollector collector,FixedPrefixGraphEmission emission,
            long serializationElapsedMillis,String operationId,String mode) {
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph=collector.snapshot;
        PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage=collector.selected;
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=lineage.context();
        if(collector.completedNoPathTarget!=null) {
            require(result.boardNetIds!=null && result.boardNetIds.size()==result.nets,
                "completed NO_PATH census uses the sorted unique board-net snapshot");
        }
        int[] eligible=context.eligiblePlacementAttempts();
        int[] holeCounts=lineage.prefixHoleCountsByKind();
        StringBuilder holes=new StringBuilder("[");
        for(int i=0;i<lineage.prefixHoleCount();i++) {
            if(i>0) holes.append(',');
            PcbLayerRoutingPrototype.FixedPrefixHole hole=lineage.prefixHole(i);
            holes.append("{\"id\":\"").append(jsonFull(hole.id())).append("\",\"netId\":")
                .append(hole.netId()==null?"null":"\""+jsonFull(hole.netId())+"\"")
                .append(",\"kind\":\"").append(hole.kind().name())
                .append("\",\"x\":").append(hole.x()).append(",\"y\":").append(hole.y())
                .append(",\"drillRadius\":").append(hole.drillRadius())
                .append(",\"landRadius\":").append(hole.landRadius()).append(",\"exposure\":")
                .append(hole.exposure()==null?"null":"\""+hole.exposure().name()+"\"").append('}');
        }
        holes.append(']');
        int goalsTop=0,goalsBottom=0;
        for(int cell=0;cell<graph.cellCount();cell++) {
            if(graph.goalAt(PcbCopperLayer.TOP.ordinal(),cell)) goalsTop++;
            if(graph.goalAt(PcbCopperLayer.BOTTOM.ordinal(),cell)) goalsBottom++;
        }
        String raw=context.rawPlacementFingerprint();
        String prefix=lineage.prefixGeometryFingerprint();
        return "{\"protocolVersion\":1,\"runId\":\""+jsonFull(result.runId)+
            "\",\"operationId\":\""+jsonFull(operationId)+"\",\"mode\":\""+mode+
            "\",\"variant\":\""+jsonFull(result.subject)+"\",\"rootSeed\":\""+result.seed+
            "\",\"placementSeed\":\""+context.layoutSeed()+"\",\"routingSeed\":\""+
            context.routingSeed()+"\",\"layoutVersion\":"+SeededPcbLayoutGenerator.CURRENT_VERSION+
            ",\"dependencyEpoch\":\""+jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)+
            "\",\"planVersion\":"+Rb100Plan.PLAN_VERSION+",\"modelVersion\":"+
            Rb100Plan.MODEL_VERSION+",\"physicalIntentVersion\":"+Rb100Placement.INTENT_VERSION+
            ",\"components\":"+result.components+",\"pads\":"+result.pads+
            ",\"nets\":"+result.nets+",\"routeEligiblePlacementAttempts\":"+
            integerArrayJson(eligible)+",\"selectedPlacementIndex\":"+context.selectedPlacementIndex()+
            ",\"selectedPlacementAttempt\":"+context.selectedPlacementAttempt()+
            ",\"routeRequestIndex\":"+context.routeRequestIndex()+
            ",\"rawPlacementFingerprint\":\""+jsonFull(raw)+"\",\"rawPlacementFingerprintLength\":"+
            raw.length()+",\"rawPlacementFingerprintSha256\":\""+sha256Hex(raw)+
            "\",\"graphCaseId\":\""+jsonFull(emission.caseId)+"\",\"graphSha256\":\""+
            emission.sha256+"\",\"graphBytes\":"+emission.bytes+
            ",\"gridWidth\":"+graph.gridWidth()+",\"gridHeight\":"+graph.gridHeight()+
            ",\"gridOriginX\":"+graph.originX()+",\"gridOriginY\":"+graph.originY()+
            ",\"gridPitch\":"+graph.gridPitch()+",\"cellCount\":"+graph.cellCount()+
            ",\"primaryLayer\":"+graph.primaryLayer()+",\"startCell\":"+graph.startCell()+
            ",\"initialFacesMask\":"+graph.initialFacesMask()+",\"pass\":"+lineage.pass()+
            ",\"branchOrdinal\":"+lineage.branchOrdinal()+",\"netId\":\""+
            jsonFull(lineage.netId())+"\",\"startPadId\":\""+jsonFull(lineage.startPadId())+
            "\",\"rootPadId\":\""+jsonFull(lineage.rootPadId())+"\",\"startX\":"+
            lineage.startX()+",\"startY\":"+lineage.startY()+",\"rootX\":"+
            lineage.rootX()+",\"rootY\":"+lineage.rootY()+",\"reachedPadIds\":"+
            stringArrayJson(lineage.reachedPadIds())+",\"reachedPthPadIds\":"+
            stringArrayJson(lineage.reachedPthPadIds())+",\"terminalReason\":\""+
            jsonFull(lineage.terminalReason())+"\",\"prefixGeometryFingerprint\":\""+
            jsonFull(prefix)+"\",\"prefixGeometryFingerprintLength\":"+prefix.length()+
            ",\"prefixGeometryFingerprintSha256\":\""+sha256Hex(prefix)+
            "\",\"prefixHoleCount\":"+lineage.prefixHoleCount()+",\"prefixViaCount\":"+
            lineage.prefixViaCount()+",\"prefixHoleCountsByKind\":{\"VIA\":"+
            holeCounts[PcbBoardHole.Kind.VIA.ordinal()]+",\"PLATED\":"+
            holeCounts[PcbBoardHole.Kind.PLATED.ordinal()]+",\"NON_PLATED\":"+
            holeCounts[PcbBoardHole.Kind.NON_PLATED.ordinal()]+"},\"prefixHoles\":"+holes+
            ",\"branchExpanded\":"+lineage.branchExpanded()+",\"totalRouteExpansions\":"+
            lineage.totalRouteExpansions()+",\"goalsTop\":"+goalsTop+",\"goalsBottom\":"+
            goalsBottom+",\"captureWorkUnits\":"+graph.captureWorkUnits()+",\"copyEntries\":"+
            graph.copyEntries()+",\"goalProbes\":"+graph.goalProbes()+",\"ordinaryEdgeProbes\":"+
            graph.ordinaryEdgeProbes()+",\"viaProbes\":"+graph.viaProbes()+
            ",\"comparisonEntries\":"+graph.comparisonEntries()+
            ",\"captureActiveElapsedMillis\":"+graph.captureActiveElapsedMillis()+
            ",\"serializationElapsedMillis\":"+serializationElapsedMillis+
            ",\"scope\":\"ACTUAL_GENERATOR_FIXED_PREFIX_OBSERVATION_ONLY\",\"reachabilityClaim\":false,"+
            "\"layoutQualification\":false}";
    }

    private static String fixedPrefixNetCensusJson(P07PolicyDiagnosticCase result,
            FixedPrefixGraphEmission emission,String operationId) {
        require(result!=null && emission!=null && result.boardNetIds!=null &&
            result.boardNetIds.size()==result.nets,
            "fixed-prefix net census binds the actual pre-generation board identity");
        return "{\"protocolVersion\":1,\"runId\":\""+jsonFull(result.runId)+
            "\",\"operationId\":\""+jsonFull(operationId)+
            "\",\"variant\":\""+jsonFull(result.subject)+
            "\",\"graphCaseId\":\""+jsonFull(emission.caseId)+
            "\",\"graphSha256\":\""+emission.sha256+
            "\",\"netIds\":"+stringArrayJson(result.boardNetIds.toArray(new String[0]))+"}";
    }

    private static FixedPrefixGraphEmission serializeFixedPrefixGraph(
            FixedPrefixCaptureCollector collector,Rb100Plan.Variant variant) {
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph=collector.snapshot;
        int cells=graph.cellCount();
        String caseId;
        if(collector.completedNoPathTarget!=null) {
            require(collector.completedNoPathTarget.requestIndex==
                graph.context().routeRequestIndex(),
                "completed NO_PATH graph case ID binds its selected request");
            caseId=variant==Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR?
                "RB100_SHARED_HYSTERETIC_LINEAR_NO_PATH_REQ2":
                "RB100_SEPARATE_DIRECT_SWITCHING_NO_PATH_REQ0";
        } else {
            caseId=variant==Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR?
                "RB100_SHARED_HYSTERETIC_LINEAR_REQ0":"RB100_SEPARATE_DIRECT_SWITCHING_REQ0";
        }
        StringBuilder payload=new StringBuilder(Math.min(8388608,64+cells*12));
        payload.append("{\"caseId\":\"").append(caseId)
            .append("\",\"goals\":[");
        for(int layer=0;layer<2;layer++) for(int cell=0;cell<cells;cell++) {
            if(layer!=0 || cell!=0) payload.append(',');
            payload.append(graph.goalAt(layer,cell));
        }
        payload.append("],\"height\":").append(graph.gridHeight())
            .append(",\"model\":\"DIRECTED_TWO_FACE_HISTORY_RELAXED\",\"primary\":")
            .append(graph.primaryLayer()).append(",\"protocol\":\"p07-fixed-prefix-graph-v1\",\"starts\":[");
        boolean first=true;
        for(int layer=0;layer<2;layer++) if((graph.initialFacesMask()&(1<<layer))!=0) {
            if(!first) payload.append(',');
            payload.append(layer*cells+graph.startCell()); first=false;
        }
        payload.append("],\"steps\":[");
        for(int layer=0;layer<2;layer++) for(int cell=0;cell<cells;cell++) {
            if(layer!=0 || cell!=0) payload.append(',');
            payload.append(layer==PcbCopperLayer.TOP.ordinal()?graph.topStepMaskAt(cell):
                graph.bottomStepMaskAt(cell));
        }
        payload.append("],\"vias\":[");
        for(int cell=0;cell<cells;cell++) {
            if(cell>0) payload.append(',');
            payload.append(graph.viaAllowedAt(cell));
        }
        payload.append("],\"width\":").append(graph.gridWidth()).append('}');
        String canonical=payload.toString();
        int bytes=canonical.getBytes(StandardCharsets.UTF_8).length;
        require(bytes>0 && bytes<=8*1024*1024,
            "each canonical fixed-prefix graph is independently bounded to8MiB");
        return new FixedPrefixGraphEmission(caseId,canonical,sha256Hex(canonical),bytes);
    }

    private static String integerArrayJson(int[] values) {
        StringBuilder json=new StringBuilder("[");
        for(int i=0;i<values.length;i++) { if(i>0) json.append(','); json.append(values[i]); }
        return json.append(']').toString();
    }

    private static String stringArrayJson(String[] values) {
        StringBuilder json=new StringBuilder("[");
        for(int i=0;i<values.length;i++) {
            if(i>0) json.append(',');
            json.append(values[i]==null?"null":"\""+jsonFull(values[i])+"\"");
        }
        return json.append(']').toString();
    }

    private static String integerVectorJson(Vector<Integer> values) {
        StringBuilder json=new StringBuilder("[");
        for(int i=0;i<values.size();i++) { if(i>0) json.append(','); json.append(values.get(i)); }
        return json.append(']').toString();
    }

    private static String stringVectorJson(Vector<String> values) {
        StringBuilder json=new StringBuilder("[");
        for(int i=0;i<values.size();i++) {
            if(i>0) json.append(','); json.append('"').append(jsonFull(values.get(i))).append('"');
        }
        return json.append(']').toString();
    }

    private static void verifyP07PolicyStatistics(MediumBoardPhysicalPolicy.Result result) {
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        require("MEDIUM_BOARD@1".equals(statistics.policyIdentity) &&
            statistics.placementCandidates == MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES &&
            statistics.placementCandidates <= MAX_PLACEMENTS &&
            statistics.placementRejections >= 0 && statistics.placementEvaluations > 0 &&
            statistics.placementRejections + statistics.rankedPlacementAttempts.size() ==
                statistics.placementCandidates &&
            statistics.rankedPlacementAttempts.size() == statistics.placementScores.size() &&
            statistics.rankedPlacementAttempts.size() <= statistics.placementCandidates,
            "medium policy receipt preserves actual ranked placement candidates and scores");
        require(statistics.oneFaceAttempts == 0 && statistics.oneFaceSuccesses == 0 &&
            statistics.twoLayerAttempts == statistics.routeAttempts &&
            statistics.twoLayerAttempts >= 1 &&
            statistics.twoLayerAttempts <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            statistics.routeAttempts <= MAX_ROUTE_ATTEMPTS &&
            statistics.twoLayerSuccesses <= statistics.twoLayerAttempts,
            "requiresTwoLayerRouting skips all P05 attempts and retains bounded P07 counts");
        verifyP07GridEligibility(statistics);
        int p05Outcomes = 0, p07Outcomes = 0, p07Successes = 0;
        Vector<Integer> attemptedP07 = new Vector<Integer>();
        for (String outcome : statistics.routeOutcomes) {
            if (outcome.startsWith(MediumBoardPhysicalPolicy.P05_ONE_FACE + "@")) p05Outcomes++;
            if (outcome.startsWith(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@")) {
                int routeIndex = p07Outcomes++;
                String prefix = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@";
                int attemptEnd = outcome.indexOf('=', prefix.length());
                require(attemptEnd > prefix.length(),
                    "each P07 outcome includes an exact placement-attempt receipt");
                int actualAttempt = Integer.parseInt(
                    outcome.substring(prefix.length(), attemptEnd));
                require(statistics.routeEligiblePlacementAttempts.contains(Integer.valueOf(actualAttempt)) &&
                    !attemptedP07.contains(Integer.valueOf(actualAttempt)),
                    "actual P07 router calls use unique eligible placements");
                if (p07Successes == 0)
                    require(routeIndex < statistics.routeEligiblePlacementAttempts.size() &&
                        actualAttempt == statistics.routeEligiblePlacementAttempts.get(routeIndex).intValue(),
                        "P07 calls before the first success follow eligible priority");
                attemptedP07.add(Integer.valueOf(actualAttempt));
                if ("SUCCESS".equals(outcome.substring(attemptEnd + 1))) p07Successes++;
            }
        }
        require(p05Outcomes == statistics.oneFaceAttempts && p05Outcomes == 0 &&
            p07Outcomes == statistics.twoLayerAttempts &&
            p07Successes == statistics.twoLayerSuccesses &&
            statistics.routeOutcomes.size() == statistics.routeAttempts &&
            statistics.routeOutcomes.size() <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            statistics.routingOrders >= 0 && statistics.routingExpansions >= 0 &&
            statistics.selectedPlacementAttempt >= -1 &&
            (statistics.selectedPlacementAttempt < 0 ||
                statistics.selectedPlacementAttempt < statistics.placementCandidates),
            "route outcomes and selected-attempt metadata agree with actual P05/P07 policy counts");
        PcbPackingProbeReceipt probe = statistics.packingProbe;
        int probeOrders = probe == null ? 0 : probe.orderings;
        int probeExpansions = probe == null ? 0 : probe.expansions;
        if (probe != null)
            require(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(probe.routePolicy) &&
                probe.attempts <= 1 && probe.orderings <= PcbLayerRoutingPrototype.ORDERINGS &&
                probe.expansions <= PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                "optional connected-pocket probe is separately bounded on the actual P07 policy");
        require(statistics.routingOrders >= probeOrders &&
            statistics.routingExpansions >= probeExpansions &&
            statistics.routingExpansions <= MAX_ROUTE_EXPANSIONS &&
            statistics.routingOrders - probeOrders <=
                MediumBoardPhysicalPolicy.ROUTING_CANDIDATES * PcbLayerRoutingPrototype.ORDERINGS &&
            statistics.routingExpansions - probeExpansions <=
                MediumBoardPhysicalPolicy.ROUTING_CANDIDATES *
                    PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            "main P07 routing work remains within three requests after subtracting the separate probe");
    }

    private static void verifyP07GridEligibility(MediumBoardPhysicalPolicy.Statistics statistics) {
        Vector<Integer> eligible = statistics.routeEligiblePlacementAttempts;
        Vector<String> skips = statistics.routeEligibilitySkips;
        Vector<Integer> ranked = statistics.rankedPlacementAttempts;
        Vector<Integer> seen = new Vector<Integer>();
        require(eligible.size() + skips.size() <= ranked.size() &&
            eligible.size() <= statistics.placementCandidates &&
            !eligible.isEmpty(),
            "P07 receipt separates nonempty eligible candidates from pre-route grid skips");
        for (Integer attempt : eligible) {
            require(attempt != null && ranked.contains(attempt) && !seen.contains(attempt),
                "each post-priority eligible attempt is a unique member of the ranked placement receipt");
            seen.add(attempt);
        }
        for (String skip : skips) {
            String[] fields = skip.split(";", -1);
            require(fields.length == 5 && fields[0].startsWith("attempt=") &&
                fields[1].startsWith("width=") && fields[2].startsWith("height=") &&
                fields[3].startsWith("cells=") && "reason=GRID_LIMIT".equals(fields[4]),
                "P07 grid skip uses the frozen exact field order and GRID_LIMIT reason");
            int attempt = Integer.parseInt(fields[0].substring("attempt=".length()));
            int width = Integer.parseInt(fields[1].substring("width=".length()));
            int height = Integer.parseInt(fields[2].substring("height=".length()));
            long cells = Long.parseLong(fields[3].substring("cells=".length()));
            require(attempt >= 0 && attempt < statistics.placementCandidates &&
                ranked.contains(Integer.valueOf(attempt)) &&
                !seen.contains(Integer.valueOf(attempt)) &&
                cells == (long) width * height &&
                !(width >= 1 && height >= 1 && cells <= PcbLayerRoutingPrototype.MAX_CELLS),
                "P07 grid skip is a unique ranked attempt whose reported grid fails the production admission bounds");
            seen.add(Integer.valueOf(attempt));
        }
        String canonical = statistics.toCanonical();
        require(canonical.contains(";routeEligiblePlacementAttempts=" + eligible) &&
            canonical.contains(";routeEligibilitySkips=" + skips),
            "canonical policy receipt preserves both copied P07 eligibility vectors");
    }

    private static BranchDiagnosticCase runAuthoredBranchDiagnostic(
            final Rb100Plan.Variant variant, String runId) {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final BranchDiagnosticCase result = new BranchDiagnosticCase(variant.name(), SEED, runId);
        final NativeContext[] context = new NativeContext[1];
        final Rb100Plan[] plan = new Rb100Plan[1];
        final Rb100ElectricalPilot.Candidate[] candidate =
            new Rb100ElectricalPilot.Candidate[1];
        final BoardIdentity[] identity = new BoardIdentity[1];
        final PcbPlacementPlanner[] planner = new PcbPlacementPlanner[1];
        final PcbPlacementPlanner.Plan[] placement = new PcbPlacementPlanner.Plan[1];
        final PcbBoardLayout[] inputLayout = new PcbBoardLayout[1];
        final PcbLayerRoutingPrototype.Session[] session =
            new PcbLayerRoutingPrototype.Session[1];
        final PcbLayerRoutingPrototype.Result[] routeResult =
            new PcbLayerRoutingPrototype.Result[1];
        try {
            result.phase = "CURRENT_SOURCE_IDENTITY";
            unit(budget, "p07-branch-source-identity", new UnitAction() {
                public void run() {
                    require(SeededPcbLayoutGenerator.CURRENT_VERSION == 26 &&
                        "tsj-generation-dependencies-v28".equals(
                            GenerationDependencyContext.INTERPRETATION_EPOCH) &&
                        Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                        Rb100Placement.INTENT_VERSION == 9,
                        "fixed branch observation identifies layout26/dependency-v28/plan1/model4/intent9");
                    require(PcbLayerRoutingPrototype.MAX_EXPANSIONS == 1000000 &&
                        PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS == 100000 &&
                        PcbLayerRoutingPrototype.MAX_CELLS == 250000 &&
                        PcbLayerRoutingPrototype.ORDERINGS == 3 &&
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias == 48,
                        "fixed branch observation keeps the existing P07 expansion, grid, ordering and via limits");
                }
            });
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "p07-branch-native-context", new UnitAction() {
                public void run() {
                    context[0] = new NativeContext();
                    context[0].activate();
                }
            });
            result.phase = "CANDIDATE_CONSTRUCTION";
            unit(budget, "p07-branch-authored-candidate-construction", new UnitAction() {
                public void run() {
                    plan[0] = Rb100Plan.create(SEED, variant);
                    candidate[0] = Rb100ElectricalPilot.construct(plan[0]);
                }
            });
            identity[0] = valueUnit(budget, "p07-branch-authored-board-identity",
                new UnitValue<BoardIdentity>() {
                    public BoardIdentity run() {
                        result.components = candidate[0].board().getComponentIds().size();
                        result.pads = candidate[0].board().getPadIds().size();
                        result.nets = candidate[0].board().getNetIds().size();
                        require(result.components == EXPECTED_COMPONENTS &&
                            result.pads == 246 && (result.nets == 59 || result.nets == 60),
                            "branch observation uses the exact authored 100-component, 246-pad, 59-or-60-net candidate");
                        return BoardIdentity.capture(candidate[0]);
                    }
                });

            result.phase = "PROCEDURAL_DECLARATION";
            unit(budget, "p07-branch-procedural-declaration", new UnitAction() {
                public void run() { ProceduralPcbLayout.declare(candidate[0].board()); }
            });
            unit(budget, "p07-branch-post-declaration-input-identity", new UnitAction() {
                public void run() {
                    identity[0].requireUnchanged(candidate[0]);
                    result.inputMetadata = RoutingInputMetadata.capture(
                        candidate[0].board().getPlacementConstraints());
                    result.inputMetadata.requireCurrentP07Treatment();
                    require(MediumBoardPhysicalPolicy.selected(
                        candidate[0].board().getPlacementConstraints()),
                        "actual procedural board retains its current medium physical-policy interpretation");
                }
            });
            final long[] namedSeeds = valueUnit(budget, "p07-branch-named-seeds",
                new UnitValue<long[]>() {
                    public long[] run() {
                        return new long[] {
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.PLACEMENT),
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.ROUTING)
                        };
                    }
                });
            result.placementSeed = namedSeeds[0];
            result.routingSeed = namedSeeds[1];
            unit(budget, "p07-branch-assert-named-seeds", new UnitAction() {
                public void run() {
                    require(result.placementSeed == PLACEMENT_DIAGNOSTIC_SEED &&
                        result.routingSeed == ROUTING_DIAGNOSTIC_ROUTING_SEED,
                        "fixed branch observation preserves the exact root and named concern streams");
                }
            });

            result.phase = "FOOTPRINT_REGISTRY";
            unit(budget, "p07-branch-footprint-registry", new UnitAction() {
                public void run() {
                    planner[0] = new PcbPlacementPlanner(
                        StandardPcbFootprintProviders.createRegistry());
                }
            });
            result.phase = "FIXED_PLACEMENT_ATTEMPT_4";
            valueUnit(budget, "p07-branch-fixed-placement-attempt-4",
                new UnitValue<Object>() {
                    public Object run() {
                        placement[0] = planner[0].plan(candidate[0].board(),
                            candidate[0].board().getPlacementConstraints(),
                            result.placementSeed, 4);
                        return null;
                    }
                });
            result.phase = "FIXED_PLACEMENT_MATERIALIZATION";
            unit(budget, "p07-branch-fixed-placement-materialization", new UnitAction() {
                public void run() {
                    inputLayout[0] = placement[0].materialize();
                    result.placementFingerprint = inputLayout[0].geometryFingerprint();
                    require(result.placementFingerprint != null &&
                        result.placementFingerprint.length() > 0 &&
                        result.placementFingerprint.length() <= 1024 * 1024,
                        "fixed4 placement has a complete input fingerprint within the 1MiB receipt bound");
                    require(inputLayout[0].getTraces().isEmpty() &&
                        inputLayout[0].getHoles().isEmpty(),
                        "fixed4 materialization is the undrilled and unrouted actual placement");
                    require(PcbLayerRoutingPrototype.gridAdmission(
                            inputLayout[0].getBoardOutline()).eligible,
                        "fixed4 branch observation requires the actual preflight-eligible placement");
                    result.maximumBranchEvents = 3L * (result.pads - result.nets);
                    require(result.maximumBranchEvents > 0,
                        "authored board provides a positive three-pass branch event bound");
                }
            });
            unit(budget, "p07-branch-pre-session-board-identity", new UnitAction() {
                public void run() { identity[0].requireUnchanged(candidate[0]); }
            });

            final SeededPcbLayoutGenerator.AttemptObserver observer =
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int pass) {
                        result.observerChecks++;
                        if (pass < 0 || pass >= PcbLayerRoutingPrototype.ORDERINGS)
                            throw new AssertionError("P07 observer reported a pass outside the three-ordering bound");
                        budget.checkpoint();
                    }
                };
            final PcbLayerRoutingPrototype.Diagnostics diagnostics =
                new PcbLayerRoutingPrototype.Diagnostics() {
                    public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                        budget.checkpoint();
                        if (snapshot == null)
                            throw new AssertionError("P07 emitted a null terminal branch snapshot");
                        if (result.branches.size() >= result.maximumBranchEvents)
                            throw new AssertionError("P07 branch snapshot stream exceeded its finite pad/net bound");
                        result.branches.add(snapshot);
                    }
                };
            result.phase = "FIXED_P07_SESSION_BEGIN";
            unit(budget, "p07-branch-session-begin", new UnitAction() {
                public void run() {
                    session[0] = PcbLayerRoutingPrototype.begin(candidate[0].board(),
                        inputLayout[0], PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                        observer, PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias, diagnostics);
                }
            });

            result.phase = "FIXED_P07_SESSION_ADVANCE";
            boolean terminal = false;
            while (!terminal) {
                final int call = ++result.advanceCalls;
                terminal = valueUnit(budget, "p07-branch-session-advance-" + call,
                    new UnitValue<Boolean>() {
                        public Boolean run() {
                            // Keep one counted diagnostic unit on the production router quantum.
                            return Boolean.valueOf(session[0].advanceSlice());
                        }
                    }).booleanValue();
            }
            result.phase = "TERMINAL_P07_RESULT_CAPTURE";
            valueUnit(budget, "p07-branch-terminal-result-capture", new UnitValue<Object>() {
                public Object run() {
                    routeResult[0] = session[0].result();
                    result.routeResult = routeResult[0];
                    require(result.advanceCalls > 0 && result.observerChecks > 0,
                        "fixed P07 session advanced under actual per-unit budget checkpoints");
                    require(routeResult[0] != null && routeResult[0].outcome != null &&
                        routeResult[0].expansions >= 0 && routeResult[0].expansions <=
                            PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
                        routeResult[0].orderings >= 0 && routeResult[0].orderings <=
                            PcbLayerRoutingPrototype.ORDERINGS && routeResult[0].vias >= 0 &&
                        routeResult[0].vias <= PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias &&
                        routeResult[0].links >= 0,
                        "actual fixed P07 result reports bounded outcome and counters");
                    require(routeResult[0].accepted() == (routeResult[0].layout != null),
                        "P07 accepted outcome agrees with presence of its actual layout");
                    require(routeResult[0].accepted() == "SUCCESS".equals(routeResult[0].outcome),
                        "P07 success outcome agrees with acceptance; rejected outcomes remain observations");
                    verifyBranchHistory(candidate[0].board(), inputLayout[0],
                        result.branches, result.maximumBranchEvents, routeResult[0]);
                    identity[0].requireUnchanged(candidate[0]);
                    require(result.placementFingerprint.equals(inputLayout[0].geometryFingerprint()) &&
                        inputLayout[0].getTraces().isEmpty() && inputLayout[0].getHoles().isEmpty(),
                        "P07 session preserves the exact fixed placement input and publishes no copper into it");
                    require(CircuitElm.sim == context[0].sim && CirSim.theSim == context[0].sim &&
                        context[0].sim.elmList == context[0].rawGraph && context[0].rawGraph.isEmpty(),
                        "P07 branch observation leaves the candidate uninstalled in the empty active solver graph");
                    result.boardIdentityStatus = "PASS";
                    result.solverGraphIdentityStatus = "PASS";
                    result.captureLayoutOutput();
                    return null;
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }

        result.phase = "FINAL_REPORT_PREPARATION";
        try {
            unit(budget, "p07-branch-final-report-preparation", new UnitAction() {
                public void run() { result.prepareFacts(); }
            });
        } catch (RuntimeException failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        } catch (Error failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        }
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], candidate[0]);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed &&
            result.routeResult != null && result.inputMetadata != null &&
            "PASS".equals(result.boardIdentityStatus) &&
            "PASS".equals(result.solverGraphIdentityStatus) &&
            result.reportFactsJson != null && result.operationMillis <=
                nanosToMillis(CASE_LIMIT_NANOS) && result.workUnits <= WORK_LIMIT &&
            result.maxUnitMillis <= nanosToMillis(UNIT_LIMIT_NANOS) ? "PASS" : "FAIL";
        return result;
    }

    private static void verifyBranchHistory(TroubleshootBoard board, PcbBoardLayout placement,
            Vector<PcbLayerRoutingPrototype.BranchSnapshot> branches, long maximumEvents,
            PcbLayerRoutingPrototype.Result routeResult) {
        require(!branches.isEmpty() && branches.size() <= maximumEvents,
            "complete terminal branch census stays within three times (pads minus nets)");
        HashSet<String> uniqueBranches = new HashSet<String>();
        long totalExpanded = 0;
        int priorPass = -1;
        int priorPrefixPass = -1;
        int priorPrefixViaCount = -1;
        int expectedGridWidth = (placement.getBoardOutline().width - 2 * 10) / 10 + 1;
        int expectedGridHeight = (placement.getBoardOutline().height - 2 * 10) / 10 + 1;
        require(PcbLayerRoutingPrototype.GRID == 10 && expectedGridWidth > 0 &&
            expectedGridHeight > 0 && (long)expectedGridWidth * expectedGridHeight <=
                PcbLayerRoutingPrototype.MAX_CELLS,
            "branch grid census matches the frozen ten-unit P07 grid and existing cell ceiling");
        for (PcbLayerRoutingPrototype.BranchSnapshot branch : branches) {
            require(branch.pass >= 0 && branch.pass < PcbLayerRoutingPrototype.ORDERINGS &&
                branch.pass >= priorPass,
                "branch snapshots preserve actual pass order within the three-pass cap");
            priorPass = branch.pass;
            BoardNet net = board.getNet(branch.netId);
            BoardPad startPad = board.getPad(branch.startPadId);
            BoardPad rootPad = board.getPad(branch.rootPadId);
            PcbPadPlacement start = placement.getPad(branch.startPadId);
            PcbPadPlacement root = placement.getPad(branch.rootPadId);
            require(net != null && startPad != null && rootPad != null && start != null && root != null &&
                branch.netId.equals(startPad.getNetId()) && branch.netId.equals(rootPad.getNetId()) &&
                branch.startX == start.getX() && branch.startY == start.getY() &&
                branch.rootX == root.getX() && branch.rootY == root.getY(),
                "branch identity and endpoint coordinates name actual same-net board pads");
            int netPadCount = net.getPadIds().size();
            require(branch.prefixViaCount >= 0 && branch.prefixViaCount <=
                    PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias &&
                branch.reachedPadCount >= 1 && branch.reachedPadCount <= netPadCount,
                "prefix-via and reached-terminal snapshots stay within actual route limits and net membership");
            if (branch.pass != priorPrefixPass) {
                priorPrefixPass = branch.pass;
                priorPrefixViaCount = branch.prefixViaCount;
            } else {
                require(branch.prefixViaCount >= priorPrefixViaCount,
                    "actual published via prefix never decreases within an ordering pass");
                priorPrefixViaCount = branch.prefixViaCount;
            }
            require(uniqueBranches.add(branch.pass + "|" + branch.netId.length() + ":" +
                branch.netId + "|" + branch.startPadId.length() + ":" + branch.startPadId +
                "|" + branch.rootPadId.length() + ":" + branch.rootPadId),
                "each actual pass/net/start/root branch emits once");
            long cells = (long) branch.gridWidth * branch.gridHeight;
            require(branch.gridWidth == expectedGridWidth && branch.gridHeight == expectedGridHeight &&
                branch.goalsTop >= branch.reachedPadCount &&
                branch.goalsBottom >= branch.reachedPadCount &&
                branch.goalsTop <= cells && branch.goalsBottom <= cells,
                "fuller-policy goals include every reached PTH face within the fixed grid");
            require(branch.initialManhattanDistance >= 0 &&
                branch.initialManhattanDistance % 10 == 0 &&
                branch.minimumExploredManhattanDistance >= -1 &&
                (branch.minimumExploredManhattanDistance < 0 ||
                    branch.minimumExploredManhattanDistance % 10 == 0) &&
                (branch.branchExpanded == 0 ? branch.minimumExploredManhattanDistance == -1 :
                    branch.minimumExploredManhattanDistance >= 0 &&
                        branch.minimumExploredManhattanDistance <= branch.initialManhattanDistance),
                "Manhattan distance fields retain ten-unit route-grid semantics and zero-work sentinel");
            require(branch.branchExpanded >= 0 &&
                branch.branchExpanded <= PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS &&
                branch.expandedTop >= 0 && branch.expandedBottom >= 0 &&
                branch.expandedTop + branch.expandedBottom == branch.branchExpanded &&
                branch.totalExpanded >= totalExpanded &&
                branch.totalExpanded == totalExpanded + branch.branchExpanded &&
                branch.totalExpanded <= PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
                branch.frontierCount >= 0 && branch.bestStateCount >= 0,
                "per-face branch work and cumulative expansion census conserve actual non-stale search states");
            String terminalReason = String.valueOf(branch.terminalReason);
            require("FOUND".equals(terminalReason) || "NO_PATH".equals(terminalReason) ||
                "TOTAL_SEARCH_LIMIT".equals(terminalReason) ||
                "BRANCH_SEARCH_LIMIT".equals(terminalReason),
                "branch snapshot records a real terminal search reason");
            totalExpanded += branch.branchExpanded;
        }
        require(totalExpanded == routeResult.expansions &&
            branches.lastElement().totalExpanded == routeResult.expansions,
            "complete terminal branch events account for the final P07 result expansion total");
        if (routeResult.accepted() || "QUALITY_LIMIT".equals(routeResult.outcome)) {
            int completedBranches = 0;
            for (PcbLayerRoutingPrototype.BranchSnapshot branch : branches)
                if (branch.pass == routeResult.orderings - 1) {
                    require("FOUND".equals(branch.terminalReason),
                        "a completed ordering contains only found search branches");
                    completedBranches++;
                }
            require(completedBranches == board.getPadIds().size() - board.getNetIds().size(),
                "a completed authored ordering covers every non-root net terminal");
        }
    }

    private static String routingInputMetadataFields(RoutingInputMetadata metadata) {
        if (metadata == null)
            return ",\"physicalPolicyId\":null,\"physicalPolicyVersion\":null," +
                "\"placementDensityVersion\":null,\"routingLayer\":null," +
                "\"traceWidthProfile\":null,\"placementPackingVersion\":null," +
                "\"requiresTwoLayerRouting\":null";
        return ",\"physicalPolicyId\":\"" + jsonFull(metadata.physicalPolicyId) +
            "\",\"physicalPolicyVersion\":" + metadata.physicalPolicyVersion +
            ",\"placementDensityVersion\":" + metadata.placementDensityVersion +
            ",\"routingLayer\":\"" + jsonFull(metadata.routingLayer) +
            "\",\"traceWidthProfile\":\"" + jsonFull(metadata.traceWidthProfile) +
            "\",\"placementPackingVersion\":" + metadata.placementPackingVersion +
            ",\"requiresTwoLayerRouting\":" + metadata.requiresTwoLayerRouting;
    }

    static void runCorridorOrientationContracts() {
        long started = System.nanoTime();
        int beforeAssertions = assertions;
        require(SeededPcbLayoutGenerator.CURRENT_VERSION == 26 &&
                "tsj-generation-dependencies-v28".equals(
                    GenerationDependencyContext.INTERPRETATION_EPOCH),
            "orientation contract identifies layout26/dependency-v28 interpretation");
        require(Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                Rb100Placement.INTENT_VERSION == 9,
            "orientation contract identifies current plan1/model4/physical-intent9");

        PcbPlacementConstraints.Barrier barrier =
            new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 80);
        PcbPlacementConstraints constraints = corridorFixtureConstraints(barrier);
        PcbFootprint uac = corridorFixtureFootprint("UAC",
            PhysicalPackages.ISOLATED_CONVERTER_7, PcbRotation.DEG_0);
        PcbFootprint ufb = corridorFixtureFootprint("UFB",
            PhysicalPackages.OPTOCOUPLER_4, PcbRotation.DEG_180);
        Vector<PcbFootprint> authoredSelected = new Vector<PcbFootprint>();
        authoredSelected.add(uac);
        authoredSelected.add(ufb);

        TreeMap<String, Rectangle> uacCourtyards =
            constraints.domainCourtyards(uac.getPlacement());
        require(sameRectangle(uacCourtyards.get("PRIMARY"),
                new Rectangle(10, 10, 105, 310)) &&
            sameRectangle(uacCourtyards.get("SECONDARY"),
                new Rectangle(305, 10, 105, 310)),
            "actual selected UAC geometry retains the literal primary-left courtyard pair");
        int uacMask = PcbPlacementPlanner.separatingOrientationMask(
            new Rectangle(10, 10, 105, 310), new Rectangle(305, 10, 105, 310), 80);
        require(uacMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "literal UAC courtyards independently require primary-left-secondary");

        TreeMap<String, Rectangle> ufbCourtyards =
            constraints.domainCourtyards(ufb.getPlacement());
        require(sameRectangle(ufbCourtyards.get("PRIMARY"),
                new Rectangle(10, 10, 105, 180)) &&
            sameRectangle(ufbCourtyards.get("SECONDARY"),
                new Rectangle(225, 10, 105, 180)),
            "actual rotated UFB geometry maps its primary courtyard left of secondary");
        int ufbMask = PcbPlacementPlanner.separatingOrientationMask(
            ufbCourtyards.get("PRIMARY"), ufbCourtyards.get("SECONDARY"), 80);
        require(ufbMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "rotated UFB is compatible with the authored primary-left corridor");
        int authoredMask = PcbPlacementPlanner.supportedIsolationOrientationMask(
            constraints, barrier, authoredSelected);
        require(authoredMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "one shared requirement intersects all selected UAC/UFB isolation bodies");

        Rectangle prefixPrimary = new Rectangle(94, 2784, 594, 142);
        Rectangle prefixSecondary = new Rectangle(55, 75, 12331, 2591);
        int prefixMask = PcbPlacementPlanner.separatingOrientationMask(
            prefixPrimary, prefixSecondary, 80);
        require(prefixMask == PcbPlacementPlanner.CORRIDOR_SECOND_ABOVE_FIRST,
            "recorded prefix supports only secondary-above-primary, meaning primary-below-secondary");
        require((prefixMask & authoredMask) == 0,
            "production orientation mask refuses the recorded prefix despite its vertical gap");

        int exactClearance = PcbPlacementPlanner.separatingOrientationMask(
            new Rectangle(0, 0, 100, 100), new Rectangle(180, 0, 100, 100), 80);
        int oneShort = PcbPlacementPlanner.separatingOrientationMask(
            new Rectangle(0, 0, 100, 100), new Rectangle(179, 0, 100, 100), 80);
        require(exactClearance == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "X-lane separation passes at exactly the declared 80-unit clearance");
        require(oneShort == 0,
            "X-lane separation rejects at a 79-unit gap without another separating axis");

        PhysicalPackage rotatableConverter = rotatableIsolationFixturePackage(
            PhysicalPackages.ISOLATED_CONVERTER_7);
        PcbFootprint horizontal = corridorFixtureFootprint("MIXED_H",
            rotatableConverter, PcbRotation.DEG_0);
        PcbFootprint vertical = corridorFixtureFootprint("MIXED_V",
            rotatableConverter, PcbRotation.DEG_90);
        PcbPlacementConstraints incompatibleConstraints =
            corridorFixtureConstraints(barrier, "MIXED_H", "MIXED_V");
        TreeMap<String, Rectangle> horizontalCourtyards =
            incompatibleConstraints.domainCourtyards(horizontal.getPlacement());
        TreeMap<String, Rectangle> verticalCourtyards =
            incompatibleConstraints.domainCourtyards(vertical.getPlacement());
        int horizontalMask = PcbPlacementPlanner.separatingOrientationMask(
            horizontalCourtyards.get("PRIMARY"), horizontalCourtyards.get("SECONDARY"), 80);
        int verticalMask = PcbPlacementPlanner.separatingOrientationMask(
            verticalCourtyards.get("PRIMARY"), verticalCourtyards.get("SECONDARY"), 80);
        require(horizontalMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND &&
            verticalMask == PcbPlacementPlanner.CORRIDOR_FIRST_ABOVE_SECOND,
            "each individually legal mixed-body pose contributes its own fixed orientation");
        Vector<PcbFootprint> incompatibleSelected = new Vector<PcbFootprint>();
        incompatibleSelected.add(horizontal);
        incompatibleSelected.add(vertical);
        int emptyMask = PcbPlacementPlanner.supportedIsolationOrientationMask(
            incompatibleConstraints, barrier, incompatibleSelected);
        require(emptyMask == 0,
            "incompatible individually legal selected mixed poses have an empty common mask");
        boolean typedRejection = false;
        try {
            PcbPlacementPlanner.requireNonEmptyIsolationOrientationMask(emptyMask);
        } catch (PcbPlacementPlanner.Rejected rejected) {
            typedRejection = "MIXED_CORRIDOR_ORIENTATION".equals(rejected.reason);
        }
        require(typedRejection,
            "empty selected-body intersection maps to the typed mixed-corridor rejection");
        require(PcbPlacementPlanner.requireNonEmptyIsolationOrientationMask(authoredMask) ==
                authoredMask,
            "nonempty selected-body orientation masks pass unchanged");

        BoardComponent ordinaryComponent = new BoardComponent("ORDINARY", "LED",
            PhysicalPackages.THROUGH_HOLE_LED);
        for (String terminal : PhysicalPackages.THROUGH_HOLE_LED.getTerminalIds())
            ordinaryComponent.addPadId("ORDINARY." + terminal);
        PcbFootprint ordinary = PcbFootprint.fromPhysicalPackage(ordinaryComponent, 0, 0);
        Vector<PcbFootprint> ordinarySelected = new Vector<PcbFootprint>();
        ordinarySelected.add(ordinary);
        Vector<PcbPlacementConstraints.Part> ordinaryParts =
            new Vector<PcbPlacementConstraints.Part>();
        ordinaryParts.add(new PcbPlacementConstraints.Part("ORDINARY", "circuit", "Circuit",
            "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20));
        PcbPlacementConstraints ordinaryConstraints = new PcbPlacementConstraints(
            ordinaryParts, singletonBarrier(barrier));
        require(PcbPlacementPlanner.supportedIsolationOrientationMask(
                ordinaryConstraints, barrier, ordinarySelected) ==
                PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS,
            "ordinary nonmixed packages impose no new corridor orientation");

        Vector<PcbFootprint> noDomains = new Vector<PcbFootprint>();
        require(PcbPlacementPlanner.currentSeparatingOrientations(constraints, barrier,
                null, noDomains, -1, false) ==
                PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS,
            "incomplete current aggregates preserve the existing permissive missing-domain rule");
        require(PcbPlacementPlanner.currentSeparatingOrientations(constraints, barrier,
                null, noDomains, -1, true) == 0,
            "complete current aggregates reject when either barrier domain is missing");
        Vector<PcbFootprint> oneDomain = new Vector<PcbFootprint>();
        oneDomain.add(ordinary);
        require(PcbPlacementPlanner.currentSeparatingOrientations(ordinaryConstraints,
                barrier, null, oneDomain, -1, false) ==
                PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS &&
            PcbPlacementPlanner.currentSeparatingOrientations(ordinaryConstraints,
                barrier, null, oneDomain, -1, true) == 0,
            "single-domain prefix keeps incomplete/complete missing-domain semantics distinct");

        System.out.println("PASS: PCB corridor orientation contracts {\"status\":\"PASS\",\"cases\":5," +
            "\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":\"" + GenerationDependencyContext.INTERPRETATION_EPOCH +
            "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
            ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"elapsedMs\":" + nanosToMillis(System.nanoTime() - started) +
            ",\"scope\":\"PLANNER_ORIENTATION_GEOMETRY_ONLY\"}");
    }

    private static PcbPlacementConstraints corridorFixtureConstraints(
            PcbPlacementConstraints.Barrier barrier, String... componentIds) {
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        if (componentIds == null || componentIds.length == 0)
            componentIds = new String[] { "UAC", "UFB" };
        for (String componentId : componentIds) {
            String[] primary, secondary;
            if (componentId.equals("UFB")) {
                primary = new String[] { "C", "E" };
                secondary = new String[] { "A", "K" };
            } else if (componentId.equals("UAC") || componentId.startsWith("MIXED_")) {
                primary = new String[] { "IN+", "IN-", "EN", "FB", "BIAS" };
                secondary = new String[] { "PRE_L+", "OUT-" };
            } else {
                parts.add(new PcbPlacementConstraints.Part(componentId, "circuit", "Circuit",
                    "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20));
                continue;
            }
            Vector<PcbPlacementConstraints.TerminalDomain> declarations =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            for (String terminal : primary)
                declarations.add(new PcbPlacementConstraints.TerminalDomain(terminal, "PRIMARY"));
            for (String terminal : secondary)
                declarations.add(new PcbPlacementConstraints.TerminalDomain(terminal, "SECONDARY"));
            parts.add(new PcbPlacementConstraints.Part(componentId, "circuit", "Circuit",
                "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20, declarations));
        }
        return new PcbPlacementConstraints(parts, singletonBarrier(barrier));
    }

    private static Vector<PcbPlacementConstraints.Barrier> singletonBarrier(
            PcbPlacementConstraints.Barrier barrier) {
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(barrier);
        return barriers;
    }

    private static PcbFootprint corridorFixtureFootprint(String componentId,
            PhysicalPackage physicalPackage, PcbRotation rotation) {
        BoardComponent component = new BoardComponent(componentId, "CORRIDOR_FIXTURE",
            physicalPackage);
        for (String terminal : physicalPackage.getTerminalIds())
            component.addPadId(componentId + "." + terminal);
        return PcbFootprint.fromPhysicalPackage(component,
            new PcbPackagePose(0, 0, rotation, PcbBoardSide.TOP),
            physicalPackage.getGeometry());
    }

    private static PhysicalPackage rotatableIsolationFixturePackage(PhysicalPackage source) {
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY",
            source.getGeometry()));
        Vector<PcbRotation> rotations = new Vector<PcbRotation>();
        rotations.add(PcbRotation.DEG_0);
        rotations.add(PcbRotation.DEG_90);
        Vector<PcbBoardSide> sides = new Vector<PcbBoardSide>();
        sides.add(PcbBoardSide.TOP);
        return new PhysicalPackage("TEST_ROTATABLE_ISOLATION_FIXTURE",
            source.getTerminalIds(), new Vector<String>(), false, source.getGeometry(),
            variants, "DEFAULT", PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT,
            rotations, sides);
    }

    private static boolean sameRectangle(Rectangle actual, Rectangle expected) {
        return actual != null && expected != null && actual.x == expected.x &&
            actual.y == expected.y && actual.width == expected.width &&
            actual.height == expected.height;
    }

    private static PlacementDiagnosticCase runStructuralPlacementDiagnostic() {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final PlacementDiagnosticCase result = new PlacementDiagnosticCase(
            "MEDIUM_GENERIC_EDGE_REGION_20_PART", "STRUCTURAL_CONTROL", 0L, 0);
        final NativeContext[] context = new NativeContext[1];
        final TroubleshootBoard[] board = new TroubleshootBoard[1];
        final PcbPlacementPlanner[] planner = new PcbPlacementPlanner[1];
        final PcbPlacementPlanner.Plan[] nullPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Plan[] observedPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Plan[] retryPlan = new PcbPlacementPlanner.Plan[1];
        final DiagnosticCounts[] counts = new DiagnosticCounts[1];
        final DiagnosticCounts[] abortCounts = new DiagnosticCounts[1];
        final DiagnosticCallbackAbort sentinel = new DiagnosticCallbackAbort();
        try {
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "native-context", new UnitAction() {
                public void run() { context[0] = new NativeContext(); context[0].activate(); }
            });
            result.phase = "EXISTING_MEDIUM_STRUCTURAL_FIXTURE";
            board[0] = valueUnit(budget, "existing-medium-20-part-fixture",
                new UnitValue<TroubleshootBoard>() {
                    public TroubleshootBoard run() {
                        return MediumBoardFloorplanningContractTest.genericTwentyPartBoard();
                    }
                });
            unit(budget, "structural-candidate-counts", new UnitAction() {
                public void run() {
                    result.components = board[0].getComponentIds().size();
                    result.pads = board[0].getPadIds().size();
                    result.nets = board[0].getNetIds().size();
                    require(result.components == 20,
                        "positive instrumentation control is the existing generic 20-part medium fixture");
                }
            });
            unit(budget, "structural-footprint-registry", new UnitAction() {
                public void run() {
                    planner[0] = new PcbPlacementPlanner(
                        StandardPcbFootprintProviders.createRegistry());
                }
            });

            result.phase = "STRUCTURAL_NULL_SINK_CONTROL";
            nullPlan[0] = valueUnit(budget, "structural-null-sink-placement",
                new UnitValue<PcbPlacementPlanner.Plan>() {
                    public PcbPlacementPlanner.Plan run() {
                        return planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, null);
                    }
                });
            result.nullOutcome = outcomeName(nullPlan[0], null);
            result.nullGeometry = valueUnit(budget, "structural-null-geometry-fingerprint",
                new UnitValue<String>() {
                    public String run() { return nullPlan[0].materialize().geometryFingerprint(); }
                });

            counts[0] = new DiagnosticCounts(budget, null);
            result.phase = "STRUCTURAL_OBSERVED_SINK_CONTROL";
            observedPlan[0] = valueUnit(budget, "structural-observed-sink-placement",
                new UnitValue<PcbPlacementPlanner.Plan>() {
                    public PcbPlacementPlanner.Plan run() {
                        return planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, counts[0]);
                    }
                });
            result.observedOutcome = outcomeName(observedPlan[0], null);
            result.observedGeometry = valueUnit(budget,
                "structural-observed-geometry-fingerprint", new UnitValue<String>() {
                    public String run() { return observedPlan[0].materialize().geometryFingerprint(); }
                });

            abortCounts[0] = new DiagnosticCounts(budget, sentinel);
            result.phase = "CALLBACK_EXCEPTION_PROPAGATION";
            unit(budget, "sentinel-callback-abort", new UnitAction() {
                public void run() {
                    Throwable propagated = null;
                    try {
                        planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, abortCounts[0]);
                    } catch (RuntimeException failure) {
                        propagated = failure;
                    }
                    result.callbackExceptionPropagated = propagated == sentinel;
                    require(result.callbackExceptionPropagated,
                        "selected medium placement propagates the exact sink callback exception");
                    require(abortCounts[0].fitCallbacks == 1,
                        "sentinel abort occurs at the first local fit callback");
                    require(abortCounts[0].snapshotCalls == 0,
                        "sentinel abort propagates before a placement-failure snapshot");
                }
            });
            result.phase = "NULL_SINK_RETRY_AFTER_CALLBACK_ABORT";
            retryPlan[0] = valueUnit(budget, "null-sink-retry-after-callback-abort",
                new UnitValue<PcbPlacementPlanner.Plan>() {
                    public PcbPlacementPlanner.Plan run() {
                        return planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, null);
                    }
                });
            result.retryOutcome = outcomeName(retryPlan[0], null);
            result.retryGeometry = valueUnit(budget, "retry-geometry-fingerprint",
                new UnitValue<String>() {
                    public String run() { return retryPlan[0].materialize().geometryFingerprint(); }
                });
            result.phase = "CONTROL_PARITY_AND_REPORT";
            unit(budget, "structural-control-parity", new UnitAction() {
                public void run() {
                    require(nullPlan[0] != null && observedPlan[0] != null && retryPlan[0] != null,
                        "existing medium control placement succeeds with null, observed and post-abort null sinks");
                    require("ACCEPTED".equals(result.nullOutcome) &&
                        result.nullOutcome.equals(result.observedOutcome),
                        "positive control has the same accepted typed outcome with and without observation");
                    require(result.nullGeometry.equals(result.observedGeometry) &&
                        result.nullGeometry.equals(result.retryGeometry),
                        "null/observed and post-abort null placements have exact canonical pose parity");
                    require(result.nullGeometry.startsWith("layout=26|"),
                        "generic20 control has the current layout epoch prefix");
                    require("d0ca6b24/41787".equals(key("layout=20|" +
                            result.nullGeometry.substring("layout=26|".length()))),
                        "generic20 geometry retains its recorded key with only the epoch masked");
                    require(counts[0].snapshotCalls == 0 && counts[0].totalOutcomes() > 0,
                        "accepted positive control reports fit outcomes without a failure snapshot");
                    require(counts[0].totalOutcomes() == observedPlan[0].evaluations,
                        "positive control reports one classified outcome per planner evaluation");
                    require(counts[0].get(PcbPlacementPlanner.FitOutcome.ACCEPTED) > 0,
                        "accepted positive control reports at least one accepted fit");
                    require(result.callbackExceptionPropagated,
                        "callback abort propagation remains established before retry");
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }
        preparePlacementDiagnosticReport(result, budget, new UnitAction() {
            public void run() {
                if (counts[0] != null) {
                    result.fitCounts = counts[0].copyCounts();
                    result.firstFitCount = counts[0].totalOutcomes();
                }
                if (abortCounts[0] != null)
                    result.callbackAbortCallbacks = abortCounts[0].fitCallbacks;
                result.prepareFacts();
            }
        });
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], null);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed && result.reportFactsJson != null &&
            result.operationMillis <= nanosToMillis(CASE_LIMIT_NANOS) &&
            result.workUnits <= WORK_LIMIT && result.maxUnitMillis <= nanosToMillis(UNIT_LIMIT_NANOS) ?
            "PASS" : "FAIL";
        return result;
    }

    private static PlacementDiagnosticCase runCorridorWitnessPlacementDiagnostic() {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final PlacementDiagnosticCase result = new PlacementDiagnosticCase(
            "MEDIUM_ISOLATION_CORRIDOR_WITNESS_20_PART", "RIGHT_ANCHORED_PRIMARY", 0L, 0);
        final NativeContext[] context = new NativeContext[1];
        final TroubleshootBoard[] board = new TroubleshootBoard[1];
        final PlacementFixtureIdentity[] identity = new PlacementFixtureIdentity[1];
        final PcbPlacementPlanner[] planner = new PcbPlacementPlanner[1];
        final PcbPlacementPlanner.Plan[] nullPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Plan[] observedPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Rejected[] nullRejected =
            new PcbPlacementPlanner.Rejected[1];
        final PcbPlacementPlanner.Rejected[] observedRejected =
            new PcbPlacementPlanner.Rejected[1];
        final DiagnosticCounts[] counts = new DiagnosticCounts[1];
        final DiagnosticCounts[] abortCounts = new DiagnosticCounts[1];
        final DiagnosticCallbackAbort sentinel = new DiagnosticCallbackAbort();
        try {
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "native-context", new UnitAction() {
                public void run() { context[0] = new NativeContext(); context[0].activate(); }
            });
            result.phase = "DETERMINISTIC_CORRIDOR_FIXTURE";
            board[0] = valueUnit(budget, "build-20-part-corridor-fixture",
                new UnitValue<TroubleshootBoard>() {
                    public TroubleshootBoard run() { return corridorWitnessBoard(); }
                });
            identity[0] = valueUnit(budget, "capture-corridor-fixture-identity",
                new UnitValue<PlacementFixtureIdentity>() {
                    public PlacementFixtureIdentity run() {
                        return PlacementFixtureIdentity.capture(board[0]);
                    }
                });
            unit(budget, "corridor-fixture-input-contract", new UnitAction() {
                public void run() {
                    result.components = board[0].getComponentIds().size();
                    result.pads = board[0].getPadIds().size();
                    result.nets = board[0].getNetIds().size();
                    require(result.components == 20 &&
                        board[0].getComponent("UAC") != null &&
                        board[0].getComponent("JRIGHT") != null,
                        "non-authored fixture is exactly the planner's 20-part medium threshold with the intended real packages");
                    PcbPlacementConstraints constraints = board[0].getPlacementConstraints();
                    String[] expectedIsolationTerminals = new String[] {
                        "IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"
                    };
                    require(PhysicalPackages.ISOLATED_CONVERTER_7.getTerminalIds().equals(
                            Arrays.asList(expectedIsolationTerminals)),
                        "fixture uses the selected converter's exact seven literal terminal IDs");
                    require("MEDIUM_BOARD".equals(constraints.getPhysicalPolicyId()) &&
                        constraints.getPhysicalPolicyVersion() == 1 &&
                        constraints.placementDensityVersion == 14 &&
                        constraints.requiresTwoLayerRouting &&
                        "ROLE_TRACE@1".equals(constraints.getTraceWidthProfile().getIdentity()) &&
                        constraints.placementPackingVersion == 4,
                        "fixture exercises the real current medium planner input identity");
                    PcbPlacementConstraints.Part right = constraints.get("JRIGHT");
                    PcbPlacementConstraints.Part isolation = constraints.get("UAC");
                    require(right != null && right.anchor == PcbPlacementConstraints.Anchor.RIGHT &&
                        isolation != null && isolation.anchor == PcbPlacementConstraints.Anchor.NONE &&
                        isolation.getTerminalDomains().size() == 7 &&
                        "SECONDARY".equals(constraints.get("R02").domainId) &&
                        "SECONDARY".equals(constraints.get("R03").domainId) &&
                        "SECONDARY".equals(constraints.domainForPad(board[0], "R02.1")) &&
                        "SECONDARY".equals(constraints.domainForPad(board[0], "UAC.OUT-")) &&
                        "SECONDARY".equals(constraints.domainForPad(board[0], "R03.1")) &&
                        constraints.getBarriers().size() == 1 &&
                        constraints.getBarriers().get(0).separates("PRIMARY", "SECONDARY") &&
                        constraints.getBarriers().get(0).clearance == 80,
                        "actual selected isolation package and fixed right connector declare the single 80-unit primary/secondary barrier");
                    String[] expectedDomains = new String[] {
                        "PRIMARY", "PRIMARY", "SECONDARY", "SECONDARY",
                        "PRIMARY", "PRIMARY", "PRIMARY"
                    };
                    for (int index = 0; index < expectedIsolationTerminals.length; index++) {
                        String terminal = expectedIsolationTerminals[index];
                        String resistor = corridorResistorId(index);
                        require(expectedDomains[index].equals(
                                constraints.domainForPad(board[0], "UAC." + terminal)) &&
                            expectedDomains[index].equals(
                                constraints.domainForPad(board[0], resistor + ".1")) &&
                            expectedDomains[index].equals(constraints.get(resistor).domainId),
                            "each of the seven converter links stays within its exact terminal-domain assignment: " + terminal);
                    }
                }
            });
            unit(budget, "corridor-footprint-registry", new UnitAction() {
                public void run() {
                    planner[0] = new PcbPlacementPlanner(
                        StandardPcbFootprintProviders.createRegistry());
                }
            });

            result.phase = "CORRIDOR_NULL_SINK_PLACEMENT";
            valueUnit(budget, "corridor-null-sink-planner", new UnitValue<Object>() {
                public Object run() {
                    try {
                        nullPlan[0] = planner[0].plan(board[0],
                            board[0].getPlacementConstraints(), 0L, 0, null);
                    } catch (PcbPlacementPlanner.Rejected rejected) {
                        nullRejected[0] = rejected;
                    }
                    return null;
                }
            });
            result.nullOutcome = outcomeName(nullPlan[0], nullRejected[0]);

            counts[0] = new DiagnosticCounts(budget, null);
            result.phase = "CORRIDOR_OBSERVED_SINK_PLACEMENT";
            valueUnit(budget, "corridor-observed-sink-planner", new UnitValue<Object>() {
                public Object run() {
                    try {
                        observedPlan[0] = planner[0].plan(board[0],
                            board[0].getPlacementConstraints(), 0L, 0, counts[0]);
                    } catch (PcbPlacementPlanner.Rejected rejected) {
                        observedRejected[0] = rejected;
                    }
                    return null;
                }
            });
            result.observedOutcome = outcomeName(observedPlan[0], observedRejected[0]);
            result.snapshot = counts[0].snapshot;
            result.phase = "INDEPENDENT_FAILURE_SNAPSHOT_AND_WITNESS_CHECKS";
            unit(budget, "corridor-parity-and-immutable-witness", new UnitAction() {
                public void run() {
                    require(nullPlan[0] == null && observedPlan[0] == null &&
                        nullRejected[0] != null && observedRejected[0] != null &&
                        "MEDIUM_COMPONENT_PACKING".equals(nullRejected[0].reason) &&
                        nullRejected[0].reason.equals(observedRejected[0].reason) &&
                        result.nullOutcome.equals(result.observedOutcome),
                        "null and observed sinks return the same concrete medium component rejection");
                    require(counts[0].snapshotCalls == 1 && result.snapshot != null &&
                        result.snapshot.getComponentId().equals("UAC") &&
                        result.snapshot.getPlacedCount() == 1,
                        "actual first failed medium item is UAC after only the right-anchored connector was placed");
                    verifyFailureSnapshot(board[0], result.snapshot,
                        counts[0].totalOutcomes());
                    verifyLiteralCorridorWitness(result.snapshot);
                    identity[0].requireUnchanged(board[0]);
                }
            });
            final String immutableWitnessBeforeAbort = result.snapshotJson();
            abortCounts[0] = new DiagnosticCounts(budget, sentinel);
            result.phase = "CALLBACK_ABORT_AND_NULL_SINK_RETRY";
            unit(budget, "sentinel-callback-abort-and-retry", new UnitAction() {
                public void run() {
                    Throwable propagated = null;
                    try {
                        planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, abortCounts[0]);
                    } catch (RuntimeException failure) {
                        propagated = failure;
                    }
                    result.callbackExceptionPropagated = propagated == sentinel;
                    require(result.callbackExceptionPropagated &&
                        abortCounts[0].fitCallbacks == 1 && abortCounts[0].snapshotCalls == 0,
                        "real planner propagates the exact first callback exception before publishing a failure snapshot");
                    PcbPlacementPlanner.Rejected retryRejected = null;
                    try {
                        planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, null);
                    } catch (PcbPlacementPlanner.Rejected rejected) {
                        retryRejected = rejected;
                    }
                    result.retryOutcome = outcomeName(null, retryRejected);
                    require(retryRejected != null && nullRejected[0].reason.equals(retryRejected.reason) &&
                        result.retryOutcome.equals(result.nullOutcome),
                        "null-sink retry after callback abort reaches the same deterministic typed rejection");
                    require(counts[0].snapshot == result.snapshot && counts[0].snapshotCalls == 1 &&
                        immutableWitnessBeforeAbort.equals(result.snapshotJson()),
                        "the retained immutable witness is unchanged by an aborted observation and later planner retry");
                    identity[0].requireUnchanged(board[0]);
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }
        preparePlacementDiagnosticReport(result, budget, new UnitAction() {
            public void run() {
                if (counts[0] != null) {
                    result.fitCounts = counts[0].copyCounts();
                    result.firstFitCount = counts[0].totalOutcomes();
                }
                if (abortCounts[0] != null)
                    result.callbackAbortCallbacks = abortCounts[0].fitCallbacks;
                result.prepareFacts();
            }
        });
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], null);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed && result.reportFactsJson != null &&
            result.operationMillis <= nanosToMillis(CASE_LIMIT_NANOS) &&
            result.workUnits <= WORK_LIMIT &&
            result.maxUnitMillis <= nanosToMillis(UNIT_LIMIT_NANOS) ? "PASS" : "FAIL";
        return result;
    }

    private static TroubleshootBoard corridorWitnessBoard() {
        TroubleshootBoard board = new TroubleshootBoard("MEDIUM_ISOLATION_CORRIDOR_WITNESS_20_PART");
        String[] isolationTerminals = PhysicalPackages.ISOLATED_CONVERTER_7.getTerminalIds()
            .toArray(new String[0]);
        board.addComponent(new BoardComponent("UAC", "ISOLATED_CONVERTER",
            PhysicalPackages.ISOLATED_CONVERTER_7));
        board.addComponent(new BoardComponent("JRIGHT", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        for (int index = 0; index < 18; index++)
            board.addComponent(new BoardComponent(corridorResistorId(index),
                "RESISTOR", PhysicalPackages.AXIAL_RESISTOR));

        TreeMap<String, String> linkedResistorDomains = new TreeMap<String, String>();
        for (int index = 0; index < isolationTerminals.length; index++) {
            String resistor = corridorResistorId(index);
            String domain = "PRE_L+".equals(isolationTerminals[index]) ||
                "OUT-".equals(isolationTerminals[index]) ? "SECONDARY" : "PRIMARY";
            linkedResistorDomains.put(resistor, domain);
            String sharedNet = "UAC_LINK_" + index;
            String otherNet = resistor + "_OTHER";
            board.addNet(new BoardNet(sharedNet));
            board.addNet(new BoardNet(otherNet));
            board.addPad(new BoardPad("UAC." + isolationTerminals[index], "UAC",
                isolationTerminals[index], sharedNet));
            board.addPad(new BoardPad(resistor + ".1", resistor, "1", sharedNet));
            board.addPad(new BoardPad(resistor + ".2", resistor, "2", otherNet));
        }
        for (int index = isolationTerminals.length; index < 18; index++) {
            String resistor = corridorResistorId(index);
            String firstNet = resistor + "_1", secondNet = resistor + "_2";
            board.addNet(new BoardNet(firstNet));
            board.addNet(new BoardNet(secondNet));
            board.addPad(new BoardPad(resistor + ".1", resistor, "1", firstNet));
            board.addPad(new BoardPad(resistor + ".2", resistor, "2", secondNet));
        }
        for (String terminal : PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getTerminalIds()) {
            String net = "JRIGHT_" + terminal;
            board.addNet(new BoardNet(net));
            board.addPad(new BoardPad("JRIGHT." + terminal, "JRIGHT", terminal, net));
        }

        Vector<PcbPlacementConstraints.Part> demands =
            new Vector<PcbPlacementConstraints.Part>();
        for (String componentId : board.getComponentIds()) {
            PcbPlacementConstraints.Anchor anchor = "JRIGHT".equals(componentId) ?
                PcbPlacementConstraints.Anchor.RIGHT : PcbPlacementConstraints.Anchor.NONE;
            Vector<PcbPlacementConstraints.TerminalDomain> terminalDomains =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            if ("UAC".equals(componentId)) {
                for (String terminal : isolationTerminals) {
                    String domain = "PRE_L+".equals(terminal) || "OUT-".equals(terminal) ?
                        "SECONDARY" : "PRIMARY";
                    terminalDomains.add(new PcbPlacementConstraints.TerminalDomain(
                        terminal, domain));
                }
            }
            String domain = linkedResistorDomains.containsKey(componentId) ?
                linkedResistorDomains.get(componentId) : "PRIMARY";
            demands.add(new PcbPlacementConstraints.Part(componentId, "fixture",
                "Circuit", domain, anchor, 20, terminalDomains));
        }
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 80));
        board.setPlacementConstraints(new PcbPlacementConstraints(demands, barriers,
            PcbCopperLayer.BOTTOM, "MEDIUM_BOARD", 1, 14, true,
            PcbTraceWidthProfile.ROLE_WIDTHS, 4));
        board.validate();
        return board;
    }

    private static String corridorResistorId(int index) {
        return index < 10 ? "R0" + index : "R" + index;
    }

    private static void verifyLiteralCorridorWitness(
            PcbPlacementPlanner.PlacementFailureSnapshot snapshot) {
        PcbPlacementPlanner.CorridorWitnessSnapshot witness =
            snapshot.getFirstGlobalCorridorWitness();
        require(witness != null && "UAC".equals(witness.getTrialComponentId()) &&
            "ISOLATED_CONVERTER_7".equals(witness.getTrialGeometryId()) &&
            "PRIMARY".equals(witness.getBarrierFirstDomain()) &&
            "SECONDARY".equals(witness.getBarrierSecondDomain()) &&
            witness.getBarrierClearance() == 80 && "DEG_0".equals(witness.getTrialRotation()) &&
            "TOP".equals(witness.getTrialSide()) &&
            snapshot.getFirstRequiredOrientationPruneWitness() == null,
            "actual selected UAC footprint emits a global-corridor witness with no separating-axis prune");
        PcbPlacementPlanner.DomainBoundsSnapshot primary = witness.getTrialFirstCourtyard();
        PcbPlacementPlanner.DomainBoundsSnapshot secondary = witness.getTrialSecondCourtyard();
        require(primary != null && primary.isPresent() && secondary != null && secondary.isPresent() &&
            primary.getX() == witness.getTrialX() + 10 &&
            primary.getY() == witness.getTrialY() + 10 &&
            primary.getWidth() == 105 && primary.getHeight() == 310 &&
            secondary.getX() == witness.getTrialX() + 305 &&
            secondary.getY() == witness.getTrialY() + 10 &&
            secondary.getWidth() == 105 && secondary.getHeight() == 310,
            "literal converter package courtyards retain their authored x/y offsets and 105x310 extents");
        PcbPlacementPlanner.DomainBoundsSnapshot beforePrimary =
            witness.getPreTrialFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot beforeSecondary =
            witness.getPreTrialSecondDomainBounds();
        require(beforePrimary != null && beforePrimary.isPresent() &&
            "JRIGHT".equals(beforePrimary.getMinXComponentId()) &&
            "JRIGHT".equals(beforePrimary.getMaxXComponentId()) &&
            "JRIGHT".equals(beforePrimary.getMinYComponentId()) &&
            "JRIGHT".equals(beforePrimary.getMaxYComponentId()) &&
            beforeSecondary != null && !beforeSecondary.isPresent(),
            "pre-trial corridor bounds are independently attributable to the only placed right connector");
        require(witness.getAppliedAllowedOrientationMask() ==
                PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND &&
            witness.getUnconstrainedSeparatingOrientationMask() == 0,
            "fixed UAC pose requires primary-left-secondary and the right connector prefix admits no separating axis");

        PcbPlacementPlanner.DomainBoundsSnapshot afterPrimary =
            witness.getPostUnionFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot afterSecondary =
            witness.getPostUnionSecondDomainBounds();
        long trialPrimaryX = (long)witness.getTrialX() + 10;
        long trialPrimaryY = (long)witness.getTrialY() + 10;
        long trialSecondaryX = (long)witness.getTrialX() + 305;
        long trialSecondaryY = (long)witness.getTrialY() + 10;
        long primaryLeft = Math.min((long)beforePrimary.getX(), trialPrimaryX);
        long primaryTop = Math.min((long)beforePrimary.getY(), trialPrimaryY);
        long primaryRight = Math.max((long)beforePrimary.getX() + beforePrimary.getWidth(),
            trialPrimaryX + 105);
        long primaryBottom = Math.max((long)beforePrimary.getY() + beforePrimary.getHeight(),
            trialPrimaryY + 310);
        long firstToSecondX = primaryRight + 80 - trialSecondaryX;
        long secondToFirstX = trialSecondaryX + 105 + 80 - primaryLeft;
        long firstToSecondY = primaryBottom + 80 - trialSecondaryY;
        long secondToFirstY = trialSecondaryY + 310 + 80 - primaryTop;
        require(afterPrimary != null && afterPrimary.isPresent() &&
            afterPrimary.getX() == primaryLeft && afterPrimary.getY() == primaryTop &&
            afterPrimary.getWidth() == primaryRight - primaryLeft &&
            afterPrimary.getHeight() == primaryBottom - primaryTop &&
            afterSecondary != null && afterSecondary.isPresent() &&
            afterSecondary.getX() == trialSecondaryX &&
            afterSecondary.getY() == trialSecondaryY &&
            afterSecondary.getWidth() == 105 && afterSecondary.getHeight() == 310,
            "post-union bounds independently equal the right connector plus literal UAC primary and the sole UAC secondary courtyard");
        require(firstToSecondX > 0 && secondToFirstX > 0 &&
            firstToSecondY > 0 && secondToFirstY > 0 &&
            witness.getFirstToSecondXMargin() == firstToSecondX &&
            witness.getSecondToFirstXMargin() == secondToFirstX &&
            witness.getFirstToSecondYMargin() == firstToSecondY &&
            witness.getSecondToFirstYMargin() == secondToFirstY,
            "all four literal-courtyard clearance margins are independently positive and equal the immutable global witness");
    }

    private static void preparePlacementDiagnosticReport(final PlacementDiagnosticCase result,
            Budget budget, final UnitAction prepare) {
        result.phase = "FINAL_REPORT_PREPARATION";
        try {
            unit(budget, "final-report-preparation", new UnitAction() {
                public void run() { prepare.run(); }
            });
        } catch (RuntimeException failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        } catch (Error failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        }
    }

    private static void cleanupDiagnosticCase(PlacementDiagnosticCase result,
            NativeContext context, Rb100ElectricalPilot.Candidate candidate) {
        if (context == null) {
            result.cleanupPassed = false;
            result.failure = append(result.failure,
                new AssertionError("native context was not created; owner cleanup was unavailable"));
            return;
        }
        long cleanupStarted = System.nanoTime();
        try {
            context.close(candidate);
        } catch (RuntimeException cleanupFailure) {
            if (result.failurePhase == null) result.failurePhase = "CLEANUP";
            result.cleanupFailure = cleanupFailure;
            result.failure = append(result.failure, cleanupFailure);
        } catch (Error cleanupFailure) {
            if (result.failurePhase == null) result.failurePhase = "CLEANUP";
            result.cleanupFailure = cleanupFailure;
            result.failure = append(result.failure, cleanupFailure);
        }
        result.cleanupMillis = nanosToMillis(System.nanoTime() - cleanupStarted);
        result.cleanupPassed = result.cleanupFailure == null &&
            result.cleanupMillis <= CLEANUP_LIMIT_MILLIS;
        if (!result.cleanupPassed && result.cleanupFailure == null) {
            if (result.failurePhase == null) result.failurePhase = "CLEANUP";
            result.failure = append(result.failure,
                new AssertionError("native owner cleanup exceeded the 120 second bound"));
        }
    }

    private static void verifyFailureSnapshot(TroubleshootBoard board,
            PcbPlacementPlanner.PlacementFailureSnapshot snapshot, long observedOutcomes) {
        require(snapshot.getComponentId() != null && board.getComponent(snapshot.getComponentId()) != null,
            "failure snapshot names an actual component in the deterministic planner fixture");
        PcbPlacementConstraints.Part demand = board.getPlacementConstraints().get(snapshot.getComponentId());
        require(demand != null && demand.domainId.equals(snapshot.getDomainId()) &&
            demand.regionId.equals(snapshot.getRegionId()),
            "failure snapshot preserves the selected component's declared domain and region");
        require(snapshot.getOutlineWidth() > 0 && snapshot.getOutlineHeight() > 0 &&
            snapshot.getGridMinOriginX() <= snapshot.getGridMaxOriginX() &&
            snapshot.getGridMinOriginY() <= snapshot.getGridMaxOriginY(),
            "failure snapshot contains finite outline and ordered search bounds");
        require(snapshot.getPlacedCount() >= 0 &&
            snapshot.getPlacedCount() < board.getComponentIds().size(),
            "failure snapshot records the already-placed count before the rejected component");
        long classified = snapshot.getOutlineRejectCount() +
            snapshot.getAccessEnvelopeOverlapCount() + snapshot.getInterDomainBarrierCount() +
            snapshot.getGlobalCorridorCount() + snapshot.getAcceptedCount();
        require(snapshot.getEvaluationCount() > 0 && classified == snapshot.getEvaluationCount() &&
            snapshot.getAcceptedCount() == 0,
            "failed component's first-fit cause counts exactly cover its evaluations with no accepted pose");
        require(snapshot.getPlanEvaluationCount() >= snapshot.getEvaluationCount(),
            "failure snapshot distinguishes cumulative planner evaluations from failed-item evaluations");
        require(snapshot.getPlanEvaluationCount() == observedOutcomes,
            "observed first-fit callbacks equal the cumulative planner evaluation count");
        PcbPlacementPlanner.CorridorWitnessSnapshot witness =
            snapshot.getFirstGlobalCorridorWitness();
        if (witness != null)
            verifyCorridorWitness(board, witness, false);
        PcbPlacementPlanner.CorridorWitnessSnapshot pruneWitness =
            snapshot.getFirstRequiredOrientationPruneWitness();
        if (pruneWitness != null)
            verifyCorridorWitness(board, pruneWitness, true);
    }

    private static void verifyCorridorWitness(TroubleshootBoard board,
            PcbPlacementPlanner.CorridorWitnessSnapshot witness,
            boolean requiredOrientationPrune) {
        require(board.getComponent(witness.getTrialComponentId()) != null,
            "global-corridor witness names an actual selected fixture trial");
        Vector<PcbPlacementConstraints.Barrier> barriers =
            board.getPlacementConstraints().getBarriers();
        boolean declaredBarrier = false;
        for (PcbPlacementConstraints.Barrier barrier : barriers)
            if (barrier.firstDomain.equals(witness.getBarrierFirstDomain()) &&
                    barrier.secondDomain.equals(witness.getBarrierSecondDomain()) &&
                    barrier.clearance == witness.getBarrierClearance())
                declaredBarrier = true;
        require(declaredBarrier,
            "witness barrier identifiers match the actual fixture declaration");

        PhysicalPackage physical = board.getComponent(witness.getTrialComponentId())
            .getPhysicalPackage();
        PcbRotation rotation = PcbRotation.valueOf(witness.getTrialRotation());
        PcbBoardSide side = PcbBoardSide.valueOf(witness.getTrialSide());
        PcbPackagePose pose = new PcbPackagePose(witness.getTrialX(), witness.getTrialY(),
            rotation, side);
        PhysicalPackage.GeometryVariant variant =
            physical.getGeometryVariant(witness.getTrialGeometryVariantKey());
        require(physical.getId().equals(witness.getTrialGeometryId()) && variant != null &&
            variant.getTransformKey().equals(witness.getTrialGeometryTransformKey()) &&
            physical.supportsPose(pose),
            "witness keeps an actual package variant, transform and supported pose");
        TreeMap<String, Rectangle> actualTrialCourtyards =
            board.getPlacementConstraints().domainCourtyards(
                PcbComponentPlacement.fromPhysicalGeometry(witness.getTrialComponentId(),
                    pose, physical, variant.getGeometry()));

        PcbPlacementPlanner.DomainBoundsSnapshot trialFirst = witness.getTrialFirstCourtyard();
        PcbPlacementPlanner.DomainBoundsSnapshot trialSecond = witness.getTrialSecondCourtyard();
        PcbPlacementPlanner.DomainBoundsSnapshot preFirst = witness.getPreTrialFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot preSecond = witness.getPreTrialSecondDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot postFirst = witness.getPostUnionFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot postSecond = witness.getPostUnionSecondDomainBounds();
        requireDomainBounds(trialFirst, witness.getBarrierFirstDomain(), board, true);
        requireDomainBounds(trialSecond, witness.getBarrierSecondDomain(), board, true);
        requireDomainBounds(preFirst, witness.getBarrierFirstDomain(), board, false);
        requireDomainBounds(preSecond, witness.getBarrierSecondDomain(), board, false);
        requireDomainBounds(postFirst, witness.getBarrierFirstDomain(), board, false);
        requireDomainBounds(postSecond, witness.getBarrierSecondDomain(), board, false);
        requireTrialCourtyardContributors(trialFirst, witness.getTrialComponentId());
        requireTrialCourtyardContributors(trialSecond, witness.getTrialComponentId());
        requireBoundsMatchRectangle(trialFirst,
            actualTrialCourtyards.get(witness.getBarrierFirstDomain()),
            "first actual trial courtyard");
        requireBoundsMatchRectangle(trialSecond,
            actualTrialCourtyards.get(witness.getBarrierSecondDomain()),
            "second actual trial courtyard");
        requireNoTrialContributor(preFirst, witness.getTrialComponentId());
        requireNoTrialContributor(preSecond, witness.getTrialComponentId());

        verifyUnionBounds(preFirst, trialFirst, postFirst, "first domain");
        verifyUnionBounds(preSecond, trialSecond, postSecond, "second domain");
        verifyUnionExtrema(preFirst, trialFirst, postFirst, "first domain");
        verifyUnionExtrema(preSecond, trialSecond, postSecond, "second domain");

        long firstToSecondX = (long)postFirst.getX() + postFirst.getWidth() +
            witness.getBarrierClearance() - postSecond.getX();
        long secondToFirstX = (long)postSecond.getX() + postSecond.getWidth() +
            witness.getBarrierClearance() - postFirst.getX();
        long firstToSecondY = (long)postFirst.getY() + postFirst.getHeight() +
            witness.getBarrierClearance() - postSecond.getY();
        long secondToFirstY = (long)postSecond.getY() + postSecond.getHeight() +
            witness.getBarrierClearance() - postFirst.getY();
        require(witness.getFirstToSecondXMargin() == firstToSecondX &&
            witness.getSecondToFirstXMargin() == secondToFirstX &&
            witness.getFirstToSecondYMargin() == firstToSecondY &&
            witness.getSecondToFirstYMargin() == secondToFirstY,
            "witness's four signed clearance margins match its post-union domain bounds");
        int allowed = witness.getAppliedAllowedOrientationMask();
        int unconstrained = witness.getUnconstrainedSeparatingOrientationMask();
        require((allowed & ~PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS) == 0 &&
            (unconstrained & ~PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS) == 0,
            "witness orientation masks contain only declared separating orientations");
        if (requiredOrientationPrune)
            require(unconstrained != 0 && (unconstrained & allowed) == 0 &&
                allowed == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
                "orientation-prune witness records a separable prefix excluded by applied allowed orientations");
        else
            require(unconstrained == 0,
                "first global-corridor witness records a prefix with no unconstrained separating orientation");
    }

    private static void requireDomainBounds(PcbPlacementPlanner.DomainBoundsSnapshot bounds,
            String expectedDomain, TroubleshootBoard board, boolean trialCourtyard) {
        require(bounds != null && expectedDomain.equals(bounds.getDomainId()),
            "corridor witness identifies bounds for " + expectedDomain);
        if (!bounds.isPresent()) {
            require(bounds.getX() == 0 && bounds.getY() == 0 && bounds.getWidth() == 0 &&
                bounds.getHeight() == 0 && bounds.getMinXComponentId() == null &&
                bounds.getMaxXComponentId() == null && bounds.getMinYComponentId() == null &&
                bounds.getMaxYComponentId() == null,
                "missing domain bounds use the immutable empty representation for " + expectedDomain);
            return;
        }
        require(bounds.getWidth() > 0 && bounds.getHeight() > 0,
            "corridor witness contains positive present bounds for " + expectedDomain);
        TreeSet<String> actualComponents = new TreeSet<String>(board.getComponentIds());
        require(actualComponents.contains(bounds.getMinXComponentId()) &&
            actualComponents.contains(bounds.getMaxXComponentId()) &&
            actualComponents.contains(bounds.getMinYComponentId()) &&
            actualComponents.contains(bounds.getMaxYComponentId()),
            "corridor bounds extrema refer to actual plan component IDs for " + expectedDomain);
        if (trialCourtyard)
            require(bounds.getMinXComponentId().equals(bounds.getMaxXComponentId()) &&
                bounds.getMinXComponentId().equals(bounds.getMinYComponentId()) &&
                bounds.getMinXComponentId().equals(bounds.getMaxYComponentId()),
                "trial courtyard extrema all belong to its one physical component");
    }

    private static void requireBoundsMatchRectangle(
            PcbPlacementPlanner.DomainBoundsSnapshot bounds, Rectangle expected, String label) {
        if (expected == null) {
            require(!bounds.isPresent(),
                label + " is absent when the selected package has no such domain");
            return;
        }
        require(bounds.isPresent() && bounds.getX() == expected.x &&
            bounds.getY() == expected.y && bounds.getWidth() == expected.width &&
            bounds.getHeight() == expected.height,
            label + " matches the selected package geometry at the recorded pose");
    }

    private static void requireTrialCourtyardContributors(
            PcbPlacementPlanner.DomainBoundsSnapshot bounds, String trialComponentId) {
        if (!bounds.isPresent()) return;
        require(trialComponentId.equals(bounds.getMinXComponentId()) &&
            trialComponentId.equals(bounds.getMaxXComponentId()) &&
            trialComponentId.equals(bounds.getMinYComponentId()) &&
            trialComponentId.equals(bounds.getMaxYComponentId()),
            "trial courtyard reports its actual component at every boundary");
    }

    private static void requireNoTrialContributor(
            PcbPlacementPlanner.DomainBoundsSnapshot bounds, String trialComponentId) {
        require(!trialComponentId.equals(bounds.getMinXComponentId()) &&
            !trialComponentId.equals(bounds.getMaxXComponentId()) &&
            !trialComponentId.equals(bounds.getMinYComponentId()) &&
            !trialComponentId.equals(bounds.getMaxYComponentId()),
            "pre-trial aggregate excludes the unplaced failed component");
    }

    private static void verifyUnionBounds(PcbPlacementPlanner.DomainBoundsSnapshot before,
            PcbPlacementPlanner.DomainBoundsSnapshot trial,
            PcbPlacementPlanner.DomainBoundsSnapshot after, String domain) {
        if (!before.isPresent() && !trial.isPresent()) {
            require(!after.isPresent(),
                "post-union remains absent when both inputs are absent for " + domain);
            return;
        }
        PcbPlacementPlanner.DomainBoundsSnapshot only = before.isPresent() ? before : trial;
        if (!before.isPresent() || !trial.isPresent()) {
            require(after.isPresent() && after.getX() == only.getX() &&
                after.getY() == only.getY() && after.getWidth() == only.getWidth() &&
                after.getHeight() == only.getHeight(),
                "post-union preserves the one present input for " + domain);
            return;
        }
        long minX = Math.min((long)before.getX(), trial.getX());
        long minY = Math.min((long)before.getY(), trial.getY());
        long maxX = Math.max((long)before.getX() + before.getWidth(),
            (long)trial.getX() + trial.getWidth());
        long maxY = Math.max((long)before.getY() + before.getHeight(),
            (long)trial.getY() + trial.getHeight());
        require(after.getX() == minX && after.getY() == minY &&
            after.getWidth() == maxX - minX && after.getHeight() == maxY - minY,
            "post-union bounds equal the pre-trial aggregate union trial courtyard for " + domain);
    }

    private static void verifyUnionExtrema(PcbPlacementPlanner.DomainBoundsSnapshot before,
            PcbPlacementPlanner.DomainBoundsSnapshot trial,
            PcbPlacementPlanner.DomainBoundsSnapshot after, String domain) {
        if (!before.isPresent() || !trial.isPresent()) {
            if (before.isPresent() || trial.isPresent()) {
                PcbPlacementPlanner.DomainBoundsSnapshot only = before.isPresent() ? before : trial;
                require(after.getMinXComponentId().equals(only.getMinXComponentId()) &&
                    after.getMaxXComponentId().equals(only.getMaxXComponentId()) &&
                    after.getMinYComponentId().equals(only.getMinYComponentId()) &&
                    after.getMaxYComponentId().equals(only.getMaxYComponentId()),
                    "post-union preserves the one present input's extrema for " + domain);
            }
            return;
        }
        verifyUnionExtremum(before.getX(), before.getMinXComponentId(),
            trial.getX(), trial.getMinXComponentId(), after.getX(), after.getMinXComponentId(),
            true, domain + " minX");
        verifyUnionExtremum((long)before.getX() + before.getWidth(),
            before.getMaxXComponentId(), (long)trial.getX() + trial.getWidth(),
            trial.getMaxXComponentId(), (long)after.getX() + after.getWidth(),
            after.getMaxXComponentId(), false, domain + " maxX");
        verifyUnionExtremum(before.getY(), before.getMinYComponentId(),
            trial.getY(), trial.getMinYComponentId(), after.getY(), after.getMinYComponentId(),
            true, domain + " minY");
        verifyUnionExtremum((long)before.getY() + before.getHeight(),
            before.getMaxYComponentId(), (long)trial.getY() + trial.getHeight(),
            trial.getMaxYComponentId(), (long)after.getY() + after.getHeight(),
            after.getMaxYComponentId(), false, domain + " maxY");
    }

    private static void verifyUnionExtremum(long beforeValue, String beforeComponent,
            long trialValue, String trialComponent, long afterValue, String afterComponent,
            boolean minimum, String label) {
        long expected = minimum ? Math.min(beforeValue, trialValue) :
            Math.max(beforeValue, trialValue);
        String expectedComponent;
        if (beforeValue == trialValue)
            expectedComponent = beforeComponent.equals(trialComponent) ? beforeComponent : null;
        else if (minimum ? trialValue < beforeValue : trialValue > beforeValue)
            expectedComponent = trialComponent;
        else expectedComponent = beforeComponent;
        boolean contributorMatches = expectedComponent == null ?
            afterComponent.equals(beforeComponent) || afterComponent.equals(trialComponent) :
            expectedComponent.equals(afterComponent);
        require(afterValue == expected && contributorMatches,
            "post-union extremum retains a valid source component for " + label);
    }

    private static String outcomeName(PcbPlacementPlanner.Plan plan,
            PcbPlacementPlanner.Rejected rejection) {
        if (plan != null) return "ACCEPTED";
        return rejection == null ? "NO_PLACEMENT_RESULT" : typedFailure(rejection);
    }

    private static String padRecord(BoardPad pad) {
        return pad.getComponentId() + "|" + pad.getTerminalId() + "|" + pad.getNetId();
    }

    private static boolean containsIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        for (CircuitElm element : elements) if (element == target) return true;
        return false;
    }

    private static Rb100Plan.Variant[] variants() {
        return new Rb100Plan.Variant[] {
            Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR,
            Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING
        };
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError("RB100 physical diagnostics: " + message);
    }

    private static long nanosToMillis(long nanos) { return nanos / 1000000L; }

    private static String key(String canonical) {
        return Integer.toHexString(canonical.hashCode()) + "/" + canonical.length();
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && i < 400; i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch == '\n') out.append("\\n");
            else if (ch == '\r') out.append("\\r");
            else if (ch == '\t') out.append("\\t");
            else if (ch < 32) out.append('?');
            else out.append(ch);
        }
        if (value.length() > 400) out.append("...");
        return out.toString();
    }

    /** Complete JSON escaping for bounded routing decisions and geometry fingerprints. */
    private static String jsonFull(String value) {
        if (value == null) return "";
        final char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch == '\n') out.append("\\n");
            else if (ch == '\r') out.append("\\r");
            else if (ch == '\t') out.append("\\t");
            else if (ch < 32) {
                out.append("\\u").append(hex[(ch >>> 12) & 0xf])
                    .append(hex[(ch >>> 8) & 0xf]).append(hex[(ch >>> 4) & 0xf])
                    .append(hex[ch & 0xf]);
            } else out.append(ch);
        }
        return out.toString();
    }

    private static String nullableString(String value) {
        return value==null?"null":"\""+jsonFull(value)+"\"";
    }

    private static Throwable append(Throwable prior, Throwable next) {
        if (prior == null) return next;
        prior.addSuppressed(next);
        return prior;
    }

    private static String typedFailure(Throwable failure) {
        if (failure == null) return "none";
        if (failure instanceof BudgetExceeded)
            return "BUDGET_" + ((BudgetExceeded)failure).reason;
        if (failure instanceof PcbRoutingRejectedException) {
            PcbRoutingRejectedException rejected = (PcbRoutingRejectedException)failure;
            return "PCB_" + rejected.getKind() + (rejected.isExhausted() ? "_EXHAUSTED" : "_REJECTED");
        }
        if (failure instanceof PcbPlacementPlanner.Rejected)
            return "PLACEMENT_" + ((PcbPlacementPlanner.Rejected)failure).reason;
        if (failure instanceof PcbNetRouter.Rejected)
            return "ROUTER_" + ((PcbNetRouter.Rejected)failure).reason;
        if (failure instanceof SupportedEnvelope.Rejected)
            return "ENVELOPE_" + ((SupportedEnvelope.Rejected)failure).reason;
        return failure.getClass().getSimpleName();
    }

    private interface UnitAction { void run(); }
    private interface UnitValue<T> { T run(); }

    private static void unit(Budget budget, String name, UnitAction action) {
        budget.beginUnit(name);
        Throwable thrown = null;
        try { action.run(); }
        catch (RuntimeException failure) { thrown = failure; throw failure; }
        catch (Error failure) { thrown = failure; throw failure; }
        finally { budget.endUnit(thrown); }
    }

    private static <T> T valueUnit(Budget budget, String name, UnitValue<T> action) {
        budget.beginUnit(name);
        Throwable thrown = null;
        try { return action.run(); }
        catch (RuntimeException failure) { thrown = failure; throw failure; }
        catch (Error failure) { thrown = failure; throw failure; }
        finally { budget.endUnit(thrown); }
    }

    private static final class Budget {
        final long started;
        int workUnits;
        long maxUnitNanos;
        long unitStarted;
        String unitName;
        Budget(long started) { this.started = started; }
        void beginUnit(String name) {
            checkpoint();
            if (workUnits >= WORK_LIMIT) throw new BudgetExceeded("WORK_LIMIT", name);
            workUnits++;
            unitName = name;
            unitStarted = System.nanoTime();
        }
        void checkpoint() {
            long now = System.nanoTime();
            if (now - started > CASE_LIMIT_NANOS)
                throw new BudgetExceeded("CASE_DEADLINE", unitName);
            if (unitName != null && now - unitStarted > UNIT_LIMIT_NANOS)
                throw new BudgetExceeded("UNIT_DEADLINE", unitName);
        }
        void endUnit(Throwable primary) {
            long elapsed = System.nanoTime() - unitStarted;
            if (elapsed > maxUnitNanos) maxUnitNanos = elapsed;
            String ended = unitName;
            unitName = null;
            try {
                if (elapsed > UNIT_LIMIT_NANOS)
                    throw new BudgetExceeded("UNIT_DEADLINE", ended);
                if (System.nanoTime() - started > CASE_LIMIT_NANOS)
                    throw new BudgetExceeded("CASE_DEADLINE", ended);
            } catch (RuntimeException budgetFailure) {
                if (primary != null) primary.addSuppressed(budgetFailure);
                else throw budgetFailure;
            }
        }
        long maxUnitMillis() { return nanosToMillis(maxUnitNanos); }
    }

    private static final class BudgetExceeded extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String reason;
        BudgetExceeded(String reason, String unit) {
            super(reason + (unit == null ? "" : " in " + unit));
            this.reason = reason;
        }
    }

    private static final class FixedPrefixGraphEmission {
        final String caseId,payload,sha256;
        final int bytes;
        FixedPrefixGraphEmission(String caseId,String payload,String sha256,int bytes) {
            this.caseId=caseId; this.payload=payload; this.sha256=sha256; this.bytes=bytes;
        }
    }

    private static final class FixedPrefixNoPathTarget {
        final int requestIndex,eligibleIndex,placementAttempt;
        final String netId;
        FixedPrefixNoPathTarget(int requestIndex,int eligibleIndex,int placementAttempt,
                String netId) {
            if(requestIndex<0 || eligibleIndex<0 || placementAttempt<0 || netId==null ||
                    netId.length()==0 || requestIndex!=eligibleIndex)
                throw new IllegalArgumentException("Invalid fixed-prefix completed NO_PATH target");
            this.requestIndex=requestIndex; this.eligibleIndex=eligibleIndex;
            this.placementAttempt=placementAttempt; this.netId=netId;
        }
    }

    private static final class FixedPrefixCaptureCollector
            implements PcbLayerRoutingPrototype.FixedPrefixGraphObserver {
        final Rb100Plan.Variant variant;
        final String runId,operationId;
        final FixedPrefixNoPathTarget completedNoPathTarget;
        int requestZeroSelectionCalls,targetRequestSelectionCalls,
            targetNoPathLineageCalls,graphCallbacks;
        PcbLayerRoutingPrototype.FixedPrefixBranchLineage selected;
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot;
        FixedPrefixCaptureCollector(Rb100Plan.Variant variant,String runId,String operationId,
                FixedPrefixNoPathTarget completedNoPathTarget) {
            this.variant=variant; this.runId=runId; this.operationId=operationId;
            this.completedNoPathTarget=completedNoPathTarget;
        }
        public boolean select(PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage) {
            require(lineage!=null && lineage.context()!=null,
                "generator capture selector receives complete actual failed-branch lineage");
            PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=lineage.context();
            if(completedNoPathTarget==null) {
                if(context.routeRequestIndex()!=0) return false;
                requestZeroSelectionCalls++;
                require(context.layoutSeed()==PLACEMENT_DIAGNOSTIC_SEED &&
                    context.routingSeed()==ROUTING_DIAGNOSTIC_ROUTING_SEED &&
                    context.selectedPlacementAttempt()==4 &&
                    context.selectedPlacementIndex()==0 &&
                    sameExpectedEligible(toIntegerVector(context.eligiblePlacementAttempts()),variant),
                    "capture source lineage binds exact authored signed seeds, post-priority candidates and actual request0 attempt4");
                require(!"FOUND".equals(lineage.terminalReason()),
                    "fixed-prefix capture selects only a failed terminal branch");
            } else {
                if(context.routeRequestIndex()!=completedNoPathTarget.requestIndex) return false;
                targetRequestSelectionCalls++;
                require(context.layoutSeed()==PLACEMENT_DIAGNOSTIC_SEED &&
                    context.routingSeed()==ROUTING_DIAGNOSTIC_ROUTING_SEED &&
                    context.selectedPlacementAttempt()==completedNoPathTarget.placementAttempt &&
                    context.selectedPlacementIndex()==completedNoPathTarget.eligibleIndex &&
                    sameExpectedEligible(toIntegerVector(context.eligiblePlacementAttempts()),variant),
                    "completed NO_PATH lineage binds exact request, authored seeds, eligible order and target placement");
                boolean matches=matchesCompletedNoPathTarget(completedNoPathTarget,
                    context.routeRequestIndex(),context.selectedPlacementIndex(),
                    context.selectedPlacementAttempt(),lineage.netId(),lineage.terminalReason());
                if(matches) targetNoPathLineageCalls++;
                if(!shouldSelectFirstCompletedNoPathTarget(completedNoPathTarget,
                        context.routeRequestIndex(),context.selectedPlacementIndex(),
                        context.selectedPlacementAttempt(),lineage.netId(),lineage.terminalReason(),
                        selected!=null)) return false;
            }
            if(completedNoPathTarget==null && selected!=null) return false;
            selected=lineage;
            return true;
        }
        public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph) {
            graphCallbacks++;
            require(graphCallbacks==1 && graph!=null && selected!=null && graph.lineage()==selected &&
                graph.context()==selected.context(),
                "completed graph retains the exact selected immutable request lineage");
            snapshot=graph;
        }
        void requireComplete(P07PolicyDiagnosticCase result) {
            if(completedNoPathTarget==null) {
                require(requestZeroSelectionCalls==1 && graphCallbacks==1 && selected!=null && snapshot!=null,
                    "one actual request0 failed branch is selected once and emits one complete graph");
            } else {
                require(targetRequestSelectionCalls>0 && targetNoPathLineageCalls>0 &&
                    graphCallbacks==1 && selected!=null && snapshot!=null &&
                    selected.context().routeRequestIndex()==completedNoPathTarget.requestIndex &&
                    selected.context().selectedPlacementIndex()==completedNoPathTarget.eligibleIndex &&
                    selected.context().selectedPlacementAttempt()==completedNoPathTarget.placementAttempt &&
                    completedNoPathTarget.netId.equals(selected.netId()) &&
                    "NO_PATH".equals(selected.terminalReason()),
                    "first matching completed NO_PATH lineage for the exact request, placement and net emits one graph callback");
            }
            require(snapshot.lineage()==selected && snapshot.context()==selected.context() &&
                snapshot.captureWorkUnits()>0 && snapshot.copyEntries()>0 &&
                snapshot.goalProbes()>0 && snapshot.ordinaryEdgeProbes()>0 &&
                snapshot.viaProbes()==snapshot.cellCount() &&
                snapshot.comparisonEntries()>0 && snapshot.captureActiveElapsedMillis()>=0,
                "captured graph reports separately bounded copy, predicate-scan and comparison work");
            int cells=snapshot.cellCount();
            require(snapshot.gridWidth()>0 && snapshot.gridHeight()>0 && cells>0 &&
                cells<=PcbLayerRoutingPrototype.MAX_CELLS &&
                (long)snapshot.gridWidth()*snapshot.gridHeight()==cells &&
                snapshot.gridPitch()==PcbLayerRoutingPrototype.GRID &&
                snapshot.primaryLayer()==PcbCopperLayer.BOTTOM.ordinal() &&
                snapshot.initialFacesMask()==3 && snapshot.startCell()>=0 &&
                snapshot.startCell()<cells,
                "captured graph preserves the admitted bounded FULLER two-face routing grid");
            int startCell=fixedPrefixCell(snapshot,selected.startX(),selected.startY());
            int rootCell=fixedPrefixCell(snapshot,selected.rootX(),selected.rootY());
            boolean rootReached=false;
            for(String reached:selected.reachedPadIds())
                if(selected.rootPadId().equals(reached)) rootReached=true;
            for(String additional:selected.reachedPthPadIds()) {
                boolean inReached=false;
                for(String reached:selected.reachedPadIds())
                    if(additional.equals(reached)) inReached=true;
                require(inReached && !selected.rootPadId().equals(additional),
                    "additional PTH endpoints are reached terminals distinct from the separately bound root");
            }
            require(snapshot.startCell()==startCell &&
                startCell!=rootCell && rootReached &&
                snapshot.goalAt(PcbCopperLayer.TOP.ordinal(),rootCell) &&
                snapshot.goalAt(PcbCopperLayer.BOTTOM.ordinal(),rootCell),
                "graph start and both root-face goals are bound to the copied lineage coordinates");
            long expectedEdges=4L*((long)(snapshot.gridWidth()-1)*snapshot.gridHeight()+
                (long)snapshot.gridWidth()*(snapshot.gridHeight()-1));
            require(snapshot.goalProbes()==2*cells &&
                snapshot.ordinaryEdgeProbes()==expectedEdges,
                "graph predicate census covers every in-bounds directed edge and both goal faces");
            require(selected.branchExpanded()>=0 &&
                selected.branchExpanded()<=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS &&
                selected.totalRouteExpansions()>=selected.branchExpanded() &&
                selected.totalRouteExpansions()<=PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                "captured terminal counters remain within the unchanged production P07 request limits");
            int[] counts=selected.prefixHoleCountsByKind();
            int countTotal=0;
            for(int count:counts) {
                require(count>=0,"typed fixed-prefix hole count is nonnegative");
                countTotal+=count;
            }
            require(counts.length==PcbBoardHole.Kind.values().length &&
                countTotal==selected.prefixHoleCount() &&
                counts[PcbBoardHole.Kind.VIA.ordinal()]==selected.prefixViaCount() &&
                selected.prefixViaCount()<=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,
                "typed prefix inventory covers every hole and counts only actual VIA holes toward the via cap");
            HashSet<String> holeIds=new HashSet<String>();
            int[] copiedCounts=new int[PcbBoardHole.Kind.values().length];
            for(int index=0;index<selected.prefixHoleCount();index++) {
                PcbLayerRoutingPrototype.FixedPrefixHole hole=selected.prefixHole(index);
                require(hole!=null && hole.id()!=null && !hole.id().isEmpty() &&
                    hole.kind()!=null && holeIds.add(hole.id()),
                    "typed prefix hole rows retain unique immutable identities and valid kinds");
                copiedCounts[hole.kind().ordinal()]++;
            }
            for(int index=0;index<counts.length;index++)
                require(copiedCounts[index]==counts[index],
                    "typed prefix row inventory matches its independent per-kind counts");
            require(selected.pass()>=0 && selected.pass()<PcbLayerRoutingPrototype.ORDERINGS &&
                selected.branchOrdinal()>=1 && !"FOUND".equals(selected.terminalReason()) &&
                (completedNoPathTarget==null ? selected.context().routeRequestIndex()==0 :
                    selected.context().routeRequestIndex()==completedNoPathTarget.requestIndex) &&
                result.policyResult!=null,
                "captured lineage identifies the actual selected request's failed branch and never a found route");
        }
        private static Vector<Integer> toIntegerVector(int[] values) {
            Vector<Integer> result=new Vector<Integer>();
            for(int value:values) result.add(Integer.valueOf(value));
            return result;
        }
    }

    private static final class QualityScalarProbe
            implements PcbLayerRoutingPrototype.FixedPrefixGraphObserver {
        private static final int MAX_MANUAL_STEPS_PER_UNIT=32768;
        final String runId,operationId;
        final Rb100Plan.Variant variant;
        int selectCalls,graphCallbacks,manualSliceSteps,manualGroups;
        int graphAdvanceCalls;
        long manualDriverNanos;
        String controlsJson,qualityRejectionMessage,generatorLastFailureType,
            generatorLastFailureMessage;
        SeededPcbLayoutGenerator.Session targetGeneratorSession;
        Object routeSession,fixedPrefixContext,targetSearch,targetPublisher,
            targetBranchSearch,targetBranch;
        Object targetStartPad,targetRootPad,targetEndNode;
        QualityScalarSnapshot snapshot;
        QualityPathSnapshot pathSnapshot;
        ZeroViaGraphBinding graphBinding;
        P07ZeroViaGraphDiagnostics.Cursor graphCursor;
        String graphDiagnosticJson;
        int graphOrdinaryQueries=-1,graphCompletedLastWork=-1;
        int lastGraphBudgetUnit;
        P07PrefixAllocationDiagnostics.Cursor prefixAllocationCursor;
        PrefixAllocationBinding prefixAllocationBinding;
        String prefixAllocationDiagnosticJson;
        int prefixAllocationAdvanceCalls,prefixAllocationCompletedLastWork;
        int prefixAllocationTotalHoles=-1,lastPrefixAllocationBudgetUnit;
        P07AcceptedBranchCaptureDiagnostics.SeedSnapshot load12Seeds;
        P07AcceptedBranchCaptureDiagnostics.Context load12Context;
        P07AcceptedBranchCaptureDiagnostics.PreCursor load12PreCursor;
        P07AcceptedBranchCaptureDiagnostics.PreResult load12PreResult;
        P07AcceptedBranchCaptureDiagnostics.PostCursor load12PostCursor;
        P07AcceptedBranchCaptureDiagnostics.PostResult load12PostResult;
        Load12BranchBinding load12Binding;
        Object load12Search,load12Branch,load12Publisher,load12ReversedIdentity;
        String load12PreJson,load12PostJson;
        String[] load12PreHoleIds,load12PreHoleFingerprints,
            load12PostHoleIds,load12PostHoleFingerprints,load12CreatedHoleIds;
        int load12PathNodeCount=-1,load12TransitionCount=-1,load12PrefixHoleCount=-1,
            load12PostHoleCount=-1,load12CreatedHoleCount=-1,load12CreatedTraceCount=-1;
        int load12PreAdvanceCalls,load12PostAdvanceCalls;
        int lastLoad12PreBudgetUnit,lastLoad12PostBudgetUnit;
        int targetAccountingBefore=-1,targetAccountingAfter=-1;
        boolean targetCaptured,qualityRejected,targetRequestAccounted,
            terminalConfirmed,readyGuardRequested,armedReadyGuard,targetBranchHandoffVerified;
        boolean load12BranchCaptured,load12BranchHandoffVerified,
            load12PreStarted,load12PreComplete,load12PostStarted,load12PostComplete,
            load12CaptureReleased;

        QualityScalarProbe(String runId,String operationId,Rb100Plan.Variant variant) {
            this.runId=runId; this.operationId=operationId; this.variant=variant;
        }

        public boolean select(PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage) {
            selectCalls++;
            if(lineage==null || lineage.context()==null)
                throw new AssertionError("quality scalar observer received incomplete actual failed-branch lineage");
            return false;
        }

        public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph) {
            graphCallbacks++;
            throw new AssertionError("quality scalar observer must never materialize a graph");
        }

        boolean shouldManuallyAdvance(SeededPcbLayoutGenerator.Session generatorSession) {
            return !qualityRejected && generatorSession!=null &&
                P07ScalarReflection.read(generatorSession,"mediumTwoLayerRoute")!=null;
        }

        String nextManualUnitName() {
            if(load12PreStarted && !load12PreComplete)
                return "p07-quality-load12-pre-advance-"+(load12PreAdvanceCalls+1);
            if(load12PostStarted && !load12PostComplete)
                return "p07-quality-load12-post-advance-"+(load12PostAdvanceCalls+1);
            if(prefixAllocationCursor!=null && !prefixAllocationCursor.isComplete())
                return "p07-quality-prefix-allocation-advance-"+
                    (prefixAllocationAdvanceCalls+1);
            return graphCursor!=null && !graphCursor.isComplete() ?
                "p07-quality-zero-via-graph-advance-"+(graphAdvanceCalls+1) :
                "p07-quality-scalar-single-step-group-"+(manualGroups+1);
        }

        void armReadyGuard() { readyGuardRequested=true; }

        void guardNoUnexpectedReady(SeededPcbLayoutGenerator.Session generatorSession) {
            if(!readyGuardRequested || generatorSession==null) return;
            Object active=P07ScalarReflection.read(generatorSession,"mediumTwoLayerRoute");
            if(active==null) return;
            armedReadyGuard=true;
            Object ready=P07ScalarReflection.read(active,"readyOrdering");
            if(ready!=null)
                throw new AssertionError(
                    "armed actual-generator observer rejects unexpected ROUTE_READY before parent integration");
        }

        void advanceManualGroup(SeededPcbLayoutGenerator.Session generatorSession,Budget budget) {
            require(generatorSession!=null && !qualityRejected,
                "manual single-step driver owns the live generator session before the target rejection");
            Object active=P07ScalarReflection.read(generatorSession,"mediumTwoLayerRoute");
            require(active!=null,"manual single-step driver observes the actual retained P07 owner");
            if(load12PreStarted && !load12PreComplete) {
                require(active==routeSession && generatorSession==targetGeneratorSession,
                    "pending LOAD12 pre-capture retains the same generator and P07 owner");
                long preGroupStarted=System.nanoTime();
                try {
                    advanceLoad12PreCapture(budget);
                } finally {
                    manualDriverNanos+=Math.max(0L,System.nanoTime()-preGroupStarted);
                }
                manualGroups++;
                return;
            }
            if(load12PostStarted && !load12PostComplete) {
                require(active==routeSession && generatorSession==targetGeneratorSession,
                    "pending LOAD12 post-capture retains the same generator and P07 owner");
                long postGroupStarted=System.nanoTime();
                try {
                    advanceLoad12PostCapture(budget);
                } finally {
                    manualDriverNanos+=Math.max(0L,System.nanoTime()-postGroupStarted);
                }
                manualGroups++;
                return;
            }
            if(prefixAllocationCursor!=null && !prefixAllocationCursor.isComplete()) {
                require(active==routeSession &&
                    P07ScalarReflection.read(routeSession,"fixedPrefixContext")==fixedPrefixContext,
                    "pending prefix allocation work retains the same generator owner and fixed-prefix context");
                long allocationGroupStarted=System.nanoTime();
                try {
                    advancePrefixAllocation(budget);
                } finally {
                    manualDriverNanos+=Math.max(0L,System.nanoTime()-allocationGroupStarted);
                }
                manualGroups++;
                return;
            }
            if(graphCursor!=null && !graphCursor.isComplete()) {
                require(active==routeSession &&
                    P07ScalarReflection.read(routeSession,"fixedPrefixContext")==fixedPrefixContext,
                    "pending zero-via graph work retains the same generator owner and fixed-prefix context");
                long graphGroupStarted=System.nanoTime();
                try {
                    advanceZeroViaGraph(budget);
                } finally {
                    manualDriverNanos+=Math.max(0L,System.nanoTime()-graphGroupStarted);
                }
                manualGroups++;
                return;
            }
            if(routeSession==null) {
                routeSession=active;
                targetGeneratorSession=generatorSession;
                fixedPrefixContext=P07ScalarReflection.read(routeSession,"fixedPrefixContext");
                require(fixedPrefixContext instanceof PcbLayerRoutingPrototype.FixedPrefixCaptureContext,
                    "probe observer installs the actual generator-owned fixed-prefix context");
                verifyTargetContext((PcbLayerRoutingPrototype.FixedPrefixCaptureContext)fixedPrefixContext);
                targetAccountingBefore=P07ScalarReflection.intField(generatorSession,
                    "mediumTwoLayerAttempts");
                require(targetAccountingBefore==0,
                    "target request starts before the generator accounts its retained P07 stream");
                require(P07ScalarReflection.intField(routeSession,"pass")==0,
                    "first ordinary generator quantum has not crossed the target ordering before manual stepping begins");
                Object initialSearch=P07ScalarReflection.read(routeSession,"search");
                require(initialSearch==null || P07ScalarReflection.intField(initialSearch,"pass")==0,
                    "first ordinary generator quantum has not skipped the pass1 scalar observation point");
            } else {
                require(active==routeSession && generatorSession==targetGeneratorSession,
                    "manual single-step driver cannot switch to a successor P07 owner");
            }
            require(P07ScalarReflection.read(routeSession,"fixedPrefixContext")==fixedPrefixContext,
                "manual P07 work group retains its actual fixed-prefix request owner");
            long manualGroupStarted=System.nanoTime();
            int steps=0;
            try {
                while(steps<MAX_MANUAL_STEPS_PER_UNIT && !qualityRejected) {
                    budget.checkpoint();
                    Object search=P07ScalarReflection.read(routeSession,"search");
                    if(search!=null && controlsJson==null)
                        controlsJson=runQualityScalarPublisherControls(search,runId);
                    Object load12BranchBeforeSlice=retainLoad12BranchBeforeSlice(search);
                    verifyLoad12ProgressBeforeSlice(search);
                    Object branchBeforeSlice=retainTargetBranchBeforeSlice(search);
                    Object publisherBeforeSlice=search==null?null:
                        P07ScalarReflection.read(search,"publisher");
                    PcbLayerRoutingPrototype.OrderingProgress progress=
                        ((PcbLayerRoutingPrototype.Session)routeSession).advanceOrderingSlice(1);
                    manualSliceSteps++;
                    steps++;
                    if(search!=null && targetBranch!=null &&
                            branchBeforeSlice==targetBranch && publisherBeforeSlice==null &&
                            search==targetBranchSearch) {
                        Object branchAfterSlice=P07ScalarReflection.read(search,"branch");
                        Object publisherAfterSlice=P07ScalarReflection.read(search,"publisher");
                        if(branchAfterSlice==null && publisherAfterSlice!=null) {
                            require(P07ScalarReflection.read(publisherAfterSlice,"end") ==
                                    P07ScalarReflection.read(targetBranch,"found") &&
                                P07ScalarReflection.read(publisherAfterSlice,"endTerminal") ==
                                    P07ScalarReflection.read(targetBranch,"foundTerminal"),
                                "installed Publisher endpoints are the found Node and terminal from the retained actual BranchSearch");
                            targetBranchHandoffVerified=true;
                        }
                    }
                    if(search!=null && load12BranchCaptured &&
                            load12BranchBeforeSlice==load12Branch &&
                            publisherBeforeSlice==null && search==load12Search) {
                        Object branchAfterSlice=P07ScalarReflection.read(search,"branch");
                        Object publisherAfterSlice=P07ScalarReflection.read(search,"publisher");
                        if(branchAfterSlice==null && publisherAfterSlice!=null) {
                            require(P07ScalarReflection.read(publisherAfterSlice,"end")==
                                    P07ScalarReflection.read(load12Branch,"found") &&
                                P07ScalarReflection.read(publisherAfterSlice,"endTerminal")==
                                    P07ScalarReflection.read(load12Branch,"foundTerminal"),
                                "LOAD12 Publisher receives the exact actual found Node and end terminal");
                            load12Publisher=publisherAfterSlice;
                            load12BranchHandoffVerified=true;
                        }
                    }
                    require(progress!=PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY,
                        "manual P07 stepping must stop before any retained route can be integrated");
                    inspectActualTargetAfterSlice(progress);
                    verifyLoad12ProgressAfterSlice();
                    if(load12PreStarted && !load12PreComplete) break;
                    if(load12PostStarted && !load12PostComplete) break;
                    if(graphCursor!=null && !graphCursor.isComplete()) break;
                    if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE && !qualityRejected)
                        throw new AssertionError("target quality rejection was not observed before P07 completion");
                }
            } finally {
                manualDriverNanos+=Math.max(0L,System.nanoTime()-manualGroupStarted);
            }
            manualGroups++;
        }

        private Object retainLoad12BranchBeforeSlice(Object search) {
            if(search==null || load12CaptureReleased) return null;
            if(load12BranchCaptured) {
                require(search==load12Search,
                    "LOAD12 capture cannot follow a successor Search");
                Object branch=P07ScalarReflection.read(search,"branch");
                return branch==load12Branch ? branch : null;
            }
            if(P07ScalarReflection.intField(search,"pass")!=1) return null;
            Object branch=P07ScalarReflection.read(search,"branch");
            if(branch==null) return null;
            int ordinal=P07ScalarReflection.intField(branch,"branchOrdinal");
            if(ordinal<73) return null;
            require(ordinal==73 &&
                "LOAD12".equals(P07ScalarReflection.stringField(branch,"net")),
                "the first pass1 branch at ordinal73 is the approved LOAD12 target");
            captureLoad12BranchBeforeFirstWork(search,branch);
            return branch;
        }

        private void captureLoad12BranchBeforeFirstWork(Object search,Object branch) {
            require(!load12BranchCaptured && routeSession!=null &&
                targetGeneratorSession!=null &&
                P07ScalarReflection.read(targetGeneratorSession,"mediumTwoLayerRoute")==routeSession &&
                P07ScalarReflection.read(routeSession,"search")==search &&
                P07ScalarReflection.read(search,"branch")==branch &&
                P07ScalarReflection.read(search,"publisher")==null,
                "LOAD12/73 source is captured before its first BranchSearch work");
            Object netsObject=P07ScalarReflection.read(search,"nets");
            require(netsObject instanceof Vector,
                "LOAD12 Search retains its actual source-order net vector");
            Vector<?> nets=(Vector<?>)netsObject;
            require(nets.size()>0 && nets.size()<=256,
                "LOAD12 Search net vector stays inside its frozen metadata bound");
            String[] netOrder=new String[nets.size()];
            HashSet<String> uniqueNets=new HashSet<String>();
            for(int index=0;index<nets.size();index++) {
                Object value=nets.get(index);
                require(value instanceof String,
                    "LOAD12 Search net order contains only source String values");
                String net=(String)value;
                require(net.length()>0 && net.length()<=4096 &&
                    uniqueNets.add(net) && net.equals(PcbConductorGraph.requireId(net)),
                    "LOAD12 Search net order is unique and contains bounded valid identities");
                netOrder[index]=net;
            }
            int searchNetIndex=P07ScalarReflection.intField(search,"netIndex");
            int currentNetPosition=searchNetIndex-1;
            String currentNet=P07ScalarReflection.stringField(search,"currentNet");
            int branchOrdinal=P07ScalarReflection.intField(branch,"branchOrdinal");
            int searchBranches=P07ScalarReflection.intField(search,"branches");
            int pass=P07ScalarReflection.intField(search,"pass");
            PcbLayerRoutingPrototype.Policy policy=(PcbLayerRoutingPrototype.Policy)
                P07ScalarReflection.read(search,"policy");
            int primaryLayer=P07ScalarReflection.intField(search,"primary");
            int maximumVias=P07ScalarReflection.intField(search,"maximumVias");
            PcbBoardLayout layout=(PcbBoardLayout)P07ScalarReflection.read(search,"layout");
            PcbBoardLayout template=(PcbBoardLayout)P07ScalarReflection.read(routeSession,"template");
            PcbBoardHole[] prefixHoles=(PcbBoardHole[])
                P07ScalarReflection.read(branch,"holes");
            PcbPadPlacement start=(PcbPadPlacement)P07ScalarReflection.read(branch,"start");
            PcbPadPlacement root=(PcbPadPlacement)P07ScalarReflection.read(branch,"root");
            Object queue=P07ScalarReflection.read(branch,"queue");
            require(currentNetPosition>=0 && currentNetPosition<netOrder.length &&
                currentNet!=null && "LOAD12".equals(currentNet) &&
                currentNet.equals(netOrder[currentNetPosition]) &&
                branchOrdinal==73 && searchBranches==branchOrdinal && pass==1 &&
                policy==PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER &&
                primaryLayer==PcbCopperLayer.BOTTOM.ordinal() &&
                maximumVias==48 && policy.transitions==4 && layout!=null &&
                template!=null && template.getHoleCount()==0 &&
                template.getHoles().isEmpty() && start!=null && root!=null &&
                P07ScalarReflection.read(search,"currentRoot")==root &&
                prefixHoles!=null && prefixHoles.length<=maximumVias &&
                layout.getHoleCount()==prefixHoles.length &&
                P07ScalarReflection.intField(branch,"expanded")==0 &&
                P07ScalarReflection.intField(queue,"size")==2 &&
                P07ScalarReflection.read(branch,"found")==null,
                "actual LOAD12/73 captures ordered net, FULLER policy, empty template, and two unconsumed source seeds");
            Vector<PcbBoardHole> layoutHoles=layout.getHoles();
            require(layoutHoles.size()==prefixHoles.length,
                "LOAD12 branch prefix array and live layout have the same initial hole count");
            for(int index=0;index<prefixHoles.length;index++)
                require(prefixHoles[index]!=null && prefixHoles[index]==layoutHoles.get(index),
                    "LOAD12 source prefix retains its original hole identity at index "+index);
            P07AcceptedBranchCaptureDiagnostics.SeedSnapshot seeds=
                P07AcceptedBranchCaptureDiagnostics.captureInitialSeeds(search,branch);
            require(seeds!=null && seeds.nodeCount()==2,
                "actual LOAD12 branch retains both initial seed Node identities before queue advancement");
            load12Search=search;
            load12Branch=branch;
            load12Seeds=seeds;
            load12Binding=new Load12BranchBinding(search,branch,nets,netOrder,
                searchNetIndex,currentNet,branchOrdinal,searchBranches,pass,policy,
                primaryLayer,maximumVias,layout,template,prefixHoles,start,root);
            load12BranchCaptured=true;
            verifyLoad12BranchSourceBeforePublication();
        }

        private void verifyLoad12SearchMetadata() {
            Load12BranchBinding binding=load12Binding;
            require(binding!=null && load12Search==binding.search &&
                load12Branch==binding.branch &&
                P07ScalarReflection.read(binding.search,"nets")==binding.netVectorIdentity &&
                P07ScalarReflection.intField(binding.search,"netIndex")==binding.searchNetIndex &&
                P07ScalarReflection.stringField(binding.search,"currentNet")
                    .equals(binding.currentNet) &&
                P07ScalarReflection.intField(binding.search,"branches")==binding.searchBranches &&
                binding.searchBranches==binding.branchOrdinal &&
                P07ScalarReflection.intField(binding.branch,"branchOrdinal")==binding.branchOrdinal &&
                binding.currentNet.equals(P07ScalarReflection.stringField(binding.branch,"net")) &&
                P07ScalarReflection.intField(binding.search,"pass")==binding.pass &&
                P07ScalarReflection.read(binding.search,"policy")==binding.policy &&
                P07ScalarReflection.intField(binding.search,"primary")==binding.primaryLayer &&
                P07ScalarReflection.intField(binding.search,"maximumVias")==binding.maximumVias &&
                P07ScalarReflection.read(binding.search,"layout")==binding.layout &&
                P07ScalarReflection.read(routeSession,"template")==binding.template &&
                binding.template.getHoleCount()==0 && binding.template.getHoles().isEmpty(),
                "LOAD12 source Search, net position, branch ordinal, policy, layout and template remain exact");
            Vector<?> nets=(Vector<?>)binding.netVectorIdentity;
            require(nets.size()==binding.netOrder.length &&
                binding.currentNetPosition==binding.searchNetIndex-1 &&
                binding.netOrder[binding.currentNetPosition].equals(binding.currentNet),
                "LOAD12 source net vector retains its actual next-fetch/current-net relationship");
            for(int index=0;index<binding.netOrder.length;index++)
                require(binding.netOrder[index].equals(nets.get(index)),
                    "LOAD12 actual net order remains unchanged at index "+index);
        }

        private void verifyLoad12BranchSourceBeforePublication() {
            Load12BranchBinding binding=load12Binding;
            verifyLoad12SearchMetadata();
            require(P07ScalarReflection.read(routeSession,"search")==binding.search &&
                P07ScalarReflection.read(binding.search,"branch")==binding.branch &&
                P07ScalarReflection.read(binding.search,"publisher")==null &&
                P07ScalarReflection.read(binding.branch,"start")==binding.start &&
                P07ScalarReflection.read(binding.branch,"root")==binding.root &&
                binding.prefixHoles.length==binding.layout.getHoleCount() &&
                binding.prefixGeometryFingerprint.equals(
                    binding.layout.geometryFingerprint()),
                "LOAD12 prefix geometry and retained unworked branch remain unchanged before publication");
            verifyLoad12BranchHoleArray();
            Vector<PcbBoardHole> layoutHoles=binding.layout.getHoles();
            require(layoutHoles.size()==binding.prefixHoles.length,
                "LOAD12 pre-publication layout preserves every original prefix hole");
            for(int index=0;index<binding.prefixHoles.length;index++) {
                PcbBoardHole hole=binding.prefixHoles[index];
                require(hole==layoutHoles.get(index) &&
                    hole.id.equals(binding.prefixHoleIds[index]) &&
                    hole.fingerprint().equals(binding.prefixHoleFingerprints[index]),
                    "LOAD12 pre-publication prefix hole identity and fingerprint remain unchanged at index "+index);
            }
        }

        private void verifyLoad12ProgressBeforeSlice(Object search) {
            if(!load12BranchCaptured || load12CaptureReleased) return;
            require(search==load12Search &&
                P07ScalarReflection.read(routeSession,"search")==load12Search,
                "manual P07 stepping remains on the retained LOAD12 Search");
            require(P07ScalarReflection.intField(load12Search,"pass")==1 &&
                "LOAD12".equals(P07ScalarReflection.stringField(load12Search,"currentNet")) &&
                P07ScalarReflection.intField(load12Search,"branches")==73 &&
                P07ScalarReflection.intField(load12Branch,"branchOrdinal")==73,
                "manual P07 stepping retains LOAD12 net and branch73 until the post capture");
            Object branch=P07ScalarReflection.read(load12Search,"branch");
            Object publisher=P07ScalarReflection.read(load12Search,"publisher");
            if(!load12PreComplete) {
                require((branch==load12Branch && publisher==null) ||
                    (load12BranchHandoffVerified && branch==null &&
                        publisher==load12Publisher),
                    "LOAD12 branch either remains in its search or its found Publisher is retained");
            } else if(!load12PostStarted) {
                require(load12BranchHandoffVerified && branch==null &&
                    publisher==load12Publisher && load12PreResult!=null &&
                    P07ScalarReflection.read(publisher,"reversed")==load12ReversedIdentity,
                    "normal LOAD12 Publisher resumes only from its completed pre capture");
                Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
                require(reversed.size()==load12PreResult.pathNodeCount() &&
                    P07ScalarReflection.intField(publisher,"qualityIndex")==reversed.size(),
                    "LOAD12 Publisher retains the measured actual Node path before publication steps");
            } else {
                throw new AssertionError("LOAD12 post cursor must own the branch before another P07 slice");
            }
        }

        private void inspectLoad12AfterSlice(Object search) {
            if(!load12BranchCaptured || load12CaptureReleased || search!=load12Search)
                return;
            Object branch=P07ScalarReflection.read(search,"branch");
            Object publisher=P07ScalarReflection.read(search,"publisher");
            if(!load12PreStarted && branch==null && publisher==load12Publisher &&
                    load12BranchHandoffVerified &&
                    P07ScalarReflection.intField(publisher,"stage")==1) {
                Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
                if(reversed==null ||
                        P07ScalarReflection.intField(publisher,"qualityIndex")!=reversed.size())
                    return;
                verifyLoad12SearchMetadata();
                require(reversed.size()>1 &&
                    P07ScalarReflection.intField(publisher,"qualityIndex")==reversed.size() &&
                    P07ScalarReflection.read(publisher,"end")==
                        P07ScalarReflection.read(load12Branch,"found") &&
                    P07ScalarReflection.read(publisher,"endTerminal")==
                        P07ScalarReflection.read(load12Branch,"foundTerminal"),
                    "LOAD12 pre-capture starts at its actual found Publisher stage1 quality boundary");
                load12ReversedIdentity=reversed;
                load12Context=new P07AcceptedBranchCaptureDiagnostics.Context(
                    routeSession,load12Search,load12Branch,load12Publisher,load12Seeds);
                load12PreStarted=true;
                verifyLoad12PreBoundary();
                return;
            }
            if(load12PreComplete && !load12PostStarted && branch==null &&
                    publisher==null && load12Publisher!=null &&
                    P07ScalarReflection.intField(load12Publisher,"stage")==6) {
                verifyLoad12SearchMetadata();
                require(load12BranchHandoffVerified && load12Publisher!=null &&
                    P07ScalarReflection.intField(load12Publisher,"stage")==6,
                    "LOAD12 post-capture starts only after the retained Publisher reaches stage6");
                verifyLoad12PostBoundary();
                load12PostStarted=true;
                return;
            }
        }

        private void verifyLoad12TemplateAndSearchOwner() {
            Load12BranchBinding binding=load12Binding;
            require(binding!=null && routeSession!=null && targetGeneratorSession!=null &&
                P07ScalarReflection.read(targetGeneratorSession,"mediumTwoLayerRoute")==routeSession &&
                P07ScalarReflection.read(routeSession,"search")==load12Search &&
                P07ScalarReflection.read(load12Search,"owner")==routeSession,
                "LOAD12 cursor remains attached to the original generator, route and Search owners");
            require(fixedPrefixContext instanceof
                    PcbLayerRoutingPrototype.FixedPrefixCaptureContext &&
                P07ScalarReflection.read(routeSession,"fixedPrefixContext")==fixedPrefixContext,
                "LOAD12 capture retains the actual macro fixed-prefix request context");
            verifyTargetContext((PcbLayerRoutingPrototype.FixedPrefixCaptureContext)
                fixedPrefixContext);
            verifyLoad12SearchMetadata();
            require(P07ScalarReflection.read(routeSession,"template")==binding.template &&
                binding.template.getHoleCount()==0 && binding.template.getHoles().isEmpty() &&
                P07ScalarReflection.read(load12Search,"layout")==binding.layout,
                "LOAD12 cursor preserves the actual empty incoming template and source layout identities");
        }

        private void verifyLoad12PrefixHoleBindings(boolean allowLayoutReorder) {
            Load12BranchBinding binding=load12Binding;
            require(binding!=null && binding.prefixHoles.length==binding.prefixHoleIds.length &&
                binding.prefixHoles.length==binding.prefixHoleFingerprints.length,
                "LOAD12 source prefix retains its captured hole identity and fingerprint inventory");
            Vector<PcbBoardHole> layoutHoles=binding.layout.getHoles();
            require(layoutHoles.size()>=binding.prefixHoles.length,
                "LOAD12 layout retains every source prefix hole after actual publication");
            HashMap<String,PcbBoardHole> byId=new HashMap<String,PcbBoardHole>();
            for(PcbBoardHole hole:layoutHoles)
                require(hole!=null && byId.put(hole.id,hole)==null,
                    "LOAD12 live layout retains unique canonical hole IDs");
            for(int index=0;index<binding.prefixHoles.length;index++) {
                PcbBoardHole original=binding.prefixHoles[index];
                PcbBoardHole live=allowLayoutReorder?byId.get(binding.prefixHoleIds[index]):
                    layoutHoles.get(index);
                require(original!=null && live==original &&
                    original.id.equals(binding.prefixHoleIds[index]) &&
                    original.fingerprint().equals(binding.prefixHoleFingerprints[index]),
                    "LOAD12 prefix hole object, ID and raw fingerprint remain unchanged for "+
                        binding.prefixHoleIds[index]);
            }
        }

        private void verifyLoad12PreBoundary() {
            verifyLoad12TemplateAndSearchOwner();
            require(load12PreStarted && load12BranchHandoffVerified &&
                P07ScalarReflection.read(load12Search,"branch")==null &&
                P07ScalarReflection.read(load12Search,"publisher")==load12Publisher &&
                P07ScalarReflection.intField(load12Publisher,"stage")==1 &&
                P07ScalarReflection.read(load12Publisher,"reversed")==load12ReversedIdentity,
                "LOAD12 pre cursor retains the same found Publisher at stage1");
            Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(load12Publisher,"reversed");
            require(reversed!=null && reversed.size()>1 &&
                P07ScalarReflection.intField(load12Publisher,"qualityIndex")==reversed.size() &&
                P07ScalarReflection.read(load12Publisher,"end")==
                    P07ScalarReflection.read(load12Branch,"found") &&
                P07ScalarReflection.read(load12Publisher,"endTerminal")==
                    P07ScalarReflection.read(load12Branch,"foundTerminal") &&
                P07ScalarReflection.read(load12Branch,"start")==load12Binding.start &&
                P07ScalarReflection.read(load12Branch,"root")==load12Binding.root &&
                load12Binding.prefixGeometryFingerprint.equals(
                    load12Binding.layout.geometryFingerprint()),
                "LOAD12 pre boundary retains actual path, endpoints, and unchanged prefix geometry");
            verifyLoad12BranchHoleArray();
            verifyLoad12PrefixHoleBindings(false);
        }

        private int reachedStartCount() {
            Object reachedObject=P07ScalarReflection.read(load12Search,"reachedPadIds");
            require(reachedObject instanceof Vector,
                "LOAD12 source retains the actual reached-pad vector at publication handoff");
            Vector<?> reached=(Vector<?>)reachedObject;
            int count=0;
            for(Object value:reached) {
                require(value instanceof String,
                    "LOAD12 reached-pad entries retain actual String identities");
                if(load12Binding.start.getPadId().equals(value)) count++;
            }
            return count;
        }

        private void verifyLoad12PostBoundary() {
            verifyLoad12TemplateAndSearchOwner();
            require(load12PreComplete && load12PreResult!=null &&
                P07ScalarReflection.read(load12Search,"branch")==null &&
                P07ScalarReflection.read(load12Search,"publisher")==null &&
                P07ScalarReflection.intField(load12Publisher,"stage")==6 &&
                reachedStartCount()==1,
                "LOAD12 post boundary follows completed publication and one reached-start handoff");
            verifyLoad12BranchHoleArray();
            verifyLoad12PrefixHoleBindings(true);
        }

        private void verifyLoad12BranchHoleArray() {
            Load12BranchBinding binding=load12Binding;
            Object branchHolesObject=P07ScalarReflection.read(binding.branch,"holes");
            require(branchHolesObject==binding.prefixHoleArrayIdentity &&
                branchHolesObject instanceof PcbBoardHole[],
                "LOAD12 BranchSearch retains the exact captured source hole-array object");
            PcbBoardHole[] branchHoles=(PcbBoardHole[])branchHolesObject;
            require(branchHoles.length==binding.prefixHoles.length &&
                binding.prefixHoleArrayIdentity.length==binding.prefixHoles.length,
                "LOAD12 BranchSearch source hole array retains the captured prefix length");
            for(int index=0;index<branchHoles.length;index++) {
                PcbBoardHole sourceHole=branchHoles[index];
                PcbBoardHole snapshotHole=binding.prefixHoles[index];
                require(sourceHole==snapshotHole && sourceHole!=null &&
                    sourceHole.id.equals(binding.prefixHoleIds[index]) &&
                    sourceHole.fingerprint().equals(binding.prefixHoleFingerprints[index]),
                    "LOAD12 BranchSearch source hole array retains each snapshotted object, ID and fingerprint at index "+index);
            }
        }

        private void verifyLoad12ProgressAfterSlice() {
            if(!load12BranchCaptured || load12CaptureReleased) return;
            if(load12PostStarted) {
                verifyLoad12PostBoundary();
            } else if(load12PreStarted && !load12PreComplete) {
                verifyLoad12PreBoundary();
            }
        }

        private void advanceLoad12PreCapture(Budget budget) {
            require(load12PreStarted && !load12PreComplete &&
                load12Context!=null && load12PreAdvanceCalls<640 &&
                load12PreAdvanceCalls+load12PostAdvanceCalls<640,
                "pending LOAD12 pre cursor has its one retained actual source boundary");
            require(budget!=null && budget.unitName!=null &&
                budget.unitName.startsWith("p07-quality-load12-pre-advance-") &&
                budget.workUnits>lastLoad12PreBudgetUnit,
                "each LOAD12 pre-cursor advance owns a fresh charged outer value unit");
            int workUnit=budget.workUnits,callsBefore=load12PreAdvanceCalls;
            budget.checkpoint();
            verifyLoad12PreBoundary();
            if(load12PreCursor==null)
                load12PreCursor=P07AcceptedBranchCaptureDiagnostics.beginPre(load12Context);
            require(load12PreCursor!=null && !load12PreCursor.isComplete(),
                "LOAD12 pre cursor begins pending in its separately charged value unit");
            boolean complete=load12PreCursor.advance(MAX_MANUAL_STEPS_PER_UNIT);
            load12PreAdvanceCalls++;
            int lastWork=load12PreCursor.lastWork();
            require(complete==load12PreCursor.isComplete() &&
                load12PreAdvanceCalls==callsBefore+1 && budget.workUnits==workUnit &&
                lastWork>0 &&
                lastWork<=MAX_MANUAL_STEPS_PER_UNIT,
                "one LOAD12 pre-cursor advance remains within its bounded source quantum");
            budget.checkpoint();
            verifyLoad12PreBoundary();
            if(load12PreCursor.isComplete()) captureCompletedLoad12Pre();
            if(load12PreCursor.isComplete()) verifyLoad12PreBoundary();
            budget.checkpoint();
            lastLoad12PreBudgetUnit=workUnit;
        }

        private void captureCompletedLoad12Pre() {
            require(load12PreCursor!=null && load12PreCursor.isComplete() &&
                load12PreResult==null && load12PreJson==null,
                "LOAD12 pre result is materialized once after its bounded cursor completes");
            P07AcceptedBranchCaptureDiagnostics.PreResult result=load12PreCursor.result();
            String body=result.toJson();
            require(result.pathNodeCount()>=2 && result.pathNodeCount()<=4096 &&
                result.transitionCount()>=0 && result.transitionCount()<=4 &&
                result.prefixHoleCount()==load12Binding.prefixHoles.length &&
                result.branchOrdinal()==73 && "LOAD12".equals(result.netId()) &&
                body!=null && body.equals(result.toJson()),
                "LOAD12 pre result exposes the actual bounded path and captured prefix inventory");
            String seedJson=load12Seeds.toJson();
            require(seedJson!=null && seedJson.contains("\"faceMask\":3") &&
                body.contains("\"seeds\":"+seedJson),
                "LOAD12 pre result embeds the exact two-face seed snapshot captured before branch work");
            String[] ids=new String[result.prefixHoleCount()];
            String[] fingerprints=new String[result.prefixHoleCount()];
            HashMap<String,String> expected=new HashMap<String,String>();
            for(int index=0;index<load12Binding.prefixHoleIds.length;index++)
                require(expected.put(load12Binding.prefixHoleIds[index],
                    load12Binding.prefixHoleFingerprints[index])==null,
                    "LOAD12 retained source prefix IDs are unique");
            HashSet<String> observedIds=new HashSet<String>();
            for(int index=0;index<ids.length;index++) {
                ids[index]=result.prefixHoleIdAt(index);
                fingerprints[index]=result.prefixHoleFingerprintAt(index);
                require(ids[index]!=null && fingerprints[index]!=null &&
                    observedIds.add(ids[index]) && fingerprints[index].equals(expected.get(ids[index])),
                    "LOAD12 helper prefix rows preserve source hole ID and raw fingerprint");
            }
            require(observedIds.size()==expected.size(),
                "LOAD12 helper prefix inventory accounts for every captured source hole");
            load12PathNodeCount=result.pathNodeCount();
            load12TransitionCount=result.transitionCount();
            load12PrefixHoleCount=result.prefixHoleCount();
            load12PreHoleIds=ids;
            load12PreHoleFingerprints=fingerprints;
            load12PreJson=body;
            load12PreResult=result;
            load12PreComplete=true;
        }

        private void advanceLoad12PostCapture(Budget budget) {
            require(load12PostStarted && !load12PostComplete &&
                load12Context!=null && load12PreResult!=null &&
                load12PreAdvanceCalls+load12PostAdvanceCalls<640,
                "pending LOAD12 post cursor follows the same completed pre result and source context");
            require(budget!=null && budget.unitName!=null &&
                budget.unitName.startsWith("p07-quality-load12-post-advance-") &&
                budget.workUnits>lastLoad12PostBudgetUnit,
                "each LOAD12 post-cursor advance owns a fresh charged outer value unit");
            int workUnit=budget.workUnits,callsBefore=load12PostAdvanceCalls;
            budget.checkpoint();
            verifyLoad12PostBoundary();
            if(load12PostCursor==null)
                load12PostCursor=P07AcceptedBranchCaptureDiagnostics.beginPost(
                    load12Context,load12PreResult);
            require(load12PostCursor!=null && !load12PostCursor.isComplete(),
                "LOAD12 post cursor begins pending in its separately charged value unit");
            boolean complete=load12PostCursor.advance(MAX_MANUAL_STEPS_PER_UNIT);
            load12PostAdvanceCalls++;
            int lastWork=load12PostCursor.lastWork();
            require(complete==load12PostCursor.isComplete() &&
                load12PostAdvanceCalls==callsBefore+1 && budget.workUnits==workUnit &&
                lastWork>0 &&
                lastWork<=MAX_MANUAL_STEPS_PER_UNIT,
                "one LOAD12 post-cursor advance remains within its bounded source quantum");
            budget.checkpoint();
            verifyLoad12PostBoundary();
            if(load12PostCursor.isComplete()) captureCompletedLoad12Post();
            budget.checkpoint();
            lastLoad12PostBudgetUnit=workUnit;
        }

        private void captureCompletedLoad12Post() {
            require(load12PostCursor!=null && load12PostCursor.isComplete() &&
                load12PostResult==null && load12PostJson==null,
                "LOAD12 post result is materialized once after its bounded cursor completes");
            P07AcceptedBranchCaptureDiagnostics.PostResult result=load12PostCursor.result();
            String body=result.toJson();
            require(load12PreResult!=null &&
                result.postHoleCount()>=load12PreResult.prefixHoleCount() &&
                result.postHoleCount()<=48 && result.createdHoleCount()>=0 &&
                result.createdHoleCount()<=result.postHoleCount() &&
                result.createdTraceCount()>=0 && result.createdTraceCount()<=100000 &&
                body!=null && body.equals(result.toJson()),
                "LOAD12 post result exposes actual bounded publication counts and immutable JSON");
            String[] ids=new String[result.postHoleCount()];
            String[] fingerprints=new String[result.postHoleCount()];
            HashMap<String,String> preById=new HashMap<String,String>();
            for(int index=0;index<load12PreHoleIds.length;index++)
                require(preById.put(load12PreHoleIds[index],
                    load12PreHoleFingerprints[index])==null,
                    "LOAD12 pre inventory retains unique IDs during post correlation");
            HashMap<String,String> postById=new HashMap<String,String>();
            for(int index=0;index<ids.length;index++) {
                ids[index]=result.postHoleIdAt(index);
                fingerprints[index]=result.postHoleFingerprintAt(index);
                require(ids[index]!=null && fingerprints[index]!=null &&
                    postById.put(ids[index],fingerprints[index])==null,
                    "LOAD12 post inventory retains unique actual hole IDs and fingerprints");
                String preFingerprint=preById.get(ids[index]);
                if(preFingerprint!=null)
                    require(preFingerprint.equals(fingerprints[index]),
                        "LOAD12 publication preserves raw fingerprints for all earlier same-net hole owners");
            }
            for(String id:load12PreHoleIds)
                require(postById.containsKey(id) &&
                    preById.get(id).equals(postById.get(id)),
                    "LOAD12 post inventory retains every original prefix hole by ID and fingerprint");
            String[] created=new String[result.createdHoleCount()];
            HashSet<String> createdSet=new HashSet<String>();
            for(int index=0;index<created.length;index++) {
                created[index]=result.createdHoleIdAt(index);
                require(created[index]!=null && !preById.containsKey(created[index]) &&
                    postById.containsKey(created[index]) && createdSet.add(created[index]),
                    "LOAD12 created-hole IDs are the actual post-minus-pre identity set");
            }
            require(createdSet.size()==result.postHoleCount()-preById.size(),
                "LOAD12 created-hole accounting equals the actual post-minus-pre hole set");
            load12PostHoleIds=ids;
            load12PostHoleFingerprints=fingerprints;
            load12CreatedHoleIds=created;
            load12PostHoleCount=result.postHoleCount();
            load12CreatedHoleCount=result.createdHoleCount();
            load12CreatedTraceCount=result.createdTraceCount();
            load12PostJson=body;
            load12PostResult=result;
            load12PostComplete=true;
            verifyLoad12PostBoundary();
            releaseLoad12CaptureSources();
        }

        private void releaseLoad12CaptureSources() {
            require(load12PostComplete && load12PreComplete && load12PostJson!=null &&
                load12PreJson!=null && load12PostHoleIds!=null &&
                load12PostHoleFingerprints!=null,
                "LOAD12 source release occurs only after both complete immutable result bodies are saved");
            load12PreCursor=null;
            load12PostCursor=null;
            load12Context=null;
            load12Seeds=null;
            load12PreResult=null;
            load12PostResult=null;
            load12Search=null;
            load12Branch=null;
            load12Publisher=null;
            load12ReversedIdentity=null;
            load12Binding=null;
            load12CaptureReleased=true;
        }

        private void verifyLoad12CorroboratesFinalDPrefix() {
            require(load12CaptureReleased && load12PreHoleIds!=null &&
                load12PostHoleIds!=null && prefixAllocationBinding!=null &&
                prefixAllocationBinding.prefixHoles.length==48,
                "D_SENSE final prefix capture cross-checks the completed earlier LOAD12 inventory");
            PrefixAllocationBinding d=prefixAllocationBinding;
            HashMap<String,String> dById=new HashMap<String,String>();
            for(int index=0;index<d.holeIds.length;index++)
                require(dById.put(d.holeIds[index],d.holeFingerprints[index])==null,
                    "D_SENSE final prefix rows retain unique hole identities");
            for(int index=0;index<load12PreHoleIds.length;index++)
                require(load12PreHoleFingerprints[index].equals(
                    dById.get(load12PreHoleIds[index])),
                    "LOAD12 pre-hole ID and raw fingerprint are retained in the later D_SENSE prefix");
            for(int index=0;index<load12PostHoleIds.length;index++)
                require(load12PostHoleFingerprints[index].equals(
                    dById.get(load12PostHoleIds[index])),
                    "LOAD12 post-hole ID and raw fingerprint are retained in the later D_SENSE prefix");
        }

        private Object retainTargetBranchBeforeSlice(Object search) {
            if(search==null || P07ScalarReflection.intField(search,"pass")!=1) return null;
            Object branch=P07ScalarReflection.read(search,"branch");
            if(targetBranch==null) {
                if(branch==null || !"D_SENSE".equals(P07ScalarReflection.stringField(branch,"net")))
                    return null;
                require(P07ScalarReflection.read(search,"publisher")==null,
                    "retained actual D_SENSE BranchSearch precedes its Publisher installation");
                targetBranchSearch=search;
                targetBranch=branch;
            }
            if(search==targetBranchSearch && branch!=null) {
                require(branch==targetBranch,
                    "actual D_SENSE branch identity remains the same through its pre-Publisher search slices");
                return branch;
            }
            return null;
        }

        private void beginZeroViaGraph(Object search,Object publisher) {
            require(targetBranchSearch==search && targetBranch!=null && targetBranchHandoffVerified &&
                P07ScalarReflection.read(search,"branch")==null &&
                P07ScalarReflection.read(search,"publisher")==publisher,
                "stage1 Publisher follows the exact BranchSearch retained before its installing P07 slice");
            Object found=P07ScalarReflection.read(targetBranch,"found");
            PcbPadPlacement foundTerminal=(PcbPadPlacement)
                P07ScalarReflection.read(targetBranch,"foundTerminal");
            PcbPadPlacement start=(PcbPadPlacement)P07ScalarReflection.read(targetBranch,"start");
            PcbPadPlacement root=(PcbPadPlacement)P07ScalarReflection.read(targetBranch,"root");
            PcbPadPlacement[] reached=(PcbPadPlacement[])
                P07ScalarReflection.read(targetBranch,"reachedTerminals");
            boolean[] goals=(boolean[])P07ScalarReflection.read(targetBranch,"goal");
            String netId=P07ScalarReflection.stringField(targetBranch,"net");
            PcbBoardLayout layout=(PcbBoardLayout)P07ScalarReflection.read(search,"layout");
            PcbNetRouter.Router[] faces=(PcbNetRouter.Router[])
                P07ScalarReflection.read(search,"faces");
            PcbLayerRoutingPrototype.Policy policy=(PcbLayerRoutingPrototype.Policy)
                P07ScalarReflection.read(search,"policy");
            int primaryLayer=P07ScalarReflection.intField(search,"primary");
            int maximumVias=P07ScalarReflection.intField(search,"maximumVias");
            PcbBoardHole[] prefixHoles=(PcbBoardHole[])
                P07ScalarReflection.read(targetBranch,"holes");
            int width=P07ScalarReflection.intField(search,"width");
            int height=P07ScalarReflection.intField(search,"height");
            int cells=P07ScalarReflection.intField(search,"cells");
            int originX=P07ScalarReflection.intField(search,"minX");
            int originY=P07ScalarReflection.intField(search,"minY");
            int pitch=PcbLayerRoutingPrototype.GRID;
            int endCell=P07ScalarReflection.intField(found,"cell");
            int endLayer=P07ScalarReflection.intField(found,"layer");
            int initialLayerMask=0;
            for(int layer=0;layer<2;layer++)
                if(layer==primaryLayer || policy==PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER)
                    initialLayerMask|=1<<layer;
            require(pathSnapshot!=null && pathSnapshot.nodes.length==66,
                "approved actual Publisher path retains its exact66 captured nodes before query setup");
            QualityPathNodeSnapshot firstPathNode=pathSnapshot.nodes[0];
            QualityPathNodeSnapshot lastPathNode=pathSnapshot.nodes[pathSnapshot.nodes.length-1];
            PcbPadPlacement publisherTerminal=(PcbPadPlacement)
                P07ScalarReflection.read(publisher,"endTerminal");
            require(snapshot.pass==1 && "D_SENSE".equals(snapshot.netId) &&
                start!=null && root!=null && "U2D.SENSOR".equals(start.getPadId()) &&
                "CFLT_D.1".equals(root.getPadId()) &&
                snapshot.pathNodeCount==66 && snapshot.planarLength==650 &&
                snapshot.directDistance==210 && snapshot.bends==6 && snapshot.transitionCount==0 &&
                endCell==93086 && firstPathNode.cost==2 && lastPathNode.cost==992,
                "zero-via graph binds the approved separate U2D.SENSOR-to-CFLT_D.1 D_SENSE path 66/650/210/6/0 ending at cell93086 with costs2/992");
            require(targetPublisher==publisher && targetEndNode==found &&
                P07ScalarReflection.read(publisher,"end")==found && publisherTerminal==foundTerminal &&
                start==targetStartPad && root==targetRootPad && start!=null && root!=null &&
                netId!=null && netId.equals(snapshot.netId) &&
                netId.equals(P07ScalarReflection.stringField(search,"currentNet")) &&
                P07ScalarReflection.read(search,"currentRoot")==root &&
                netId.equals(P07ScalarReflection.stringField(publisher,"net")) &&
                P07ScalarReflection.read(publisher,"start")==start &&
                P07ScalarReflection.read(publisher,"root")==root &&
                width>0 && height>0 && (long)width*height==cells &&
                cells==snapshot.gridWidth*snapshot.gridHeight &&
                width==snapshot.gridWidth && height==snapshot.gridHeight &&
                originX==snapshot.gridOriginX && originY==snapshot.gridOriginY &&
                pitch==snapshot.gridPitch && faces!=null && faces.length==2 &&
                faces[PcbCopperLayer.TOP.ordinal()]!=null &&
                faces[PcbCopperLayer.BOTTOM.ordinal()]!=null &&
                policy==PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER &&
                primaryLayer==PcbCopperLayer.BOTTOM.ordinal() && initialLayerMask==3 &&
                maximumVias>=0 && maximumVias<=policy.vias && prefixHoles!=null &&
                layout.getHoleCount()==prefixHoles.length && reached!=null && goals!=null &&
                goals.length==2*cells && endCell==pathSnapshot.nodes[pathSnapshot.nodes.length-1].cell &&
                endLayer==snapshot.endLayer && endLayer==PcbCopperLayer.TOP.ordinal() &&
                P07ScalarReflection.intField(publisher,"stage")==1 &&
                P07ScalarReflection.intField(publisher,"qualityIndex")==66,
                "actual BranchSearch endpoints, complete two-face graph authorities, policy, grid and quiescent Publisher match the captured path");
            PcbBoardHole[] layoutHoles=layout.getHoles().toArray(new PcbBoardHole[0]);
            require(layoutHoles.length==prefixHoles.length,
                "actual BranchSearch retains the complete original fixed-prefix hole count");
            for(int index=0;index<prefixHoles.length;index++)
                require(prefixHoles[index]!=null && prefixHoles[index]==layoutHoles[index],
                    "actual BranchSearch fixed-prefix hole identity remains bound at index "+index);
            require(initialLayerMask==3,
                "FULLER actual policy seeds both primary and secondary faces without copper filtering");
            require(goals[endLayer*cells+endCell],
                "actual Publisher end cell and face are true in the retained BranchSearch goal mask");
            String[] reachedIds=new String[reached.length];
            String previousId=null;
            for(int index=0;index<reached.length;index++) {
                PcbPadPlacement pad=reached[index];
                require(pad!=null && !root.getPadId().equals(pad.getPadId()) &&
                    (previousId==null || previousId.compareTo(pad.getPadId())<0),
                    "actual reached PTH terminals retain unique canonical source order outside the root");
                reachedIds[index]=pad.getPadId();
                previousId=pad.getPadId();
            }
            if(foundTerminal!=null) {
                boolean terminalAuthority=foundTerminal==root;
                for(PcbPadPlacement pad:reached) if(pad==foundTerminal) terminalAuthority=true;
                require(terminalAuthority,
                    "actual found terminal is the Publisher endpoint and an original root or reached PTH identity");
            }
            int[][] path=new int[pathSnapshot.nodes.length][3];
            for(int index=0;index<pathSnapshot.nodes.length;index++) {
                QualityPathNodeSnapshot row=pathSnapshot.nodes[index];
                require(row.layer==endLayer,
                    "the captured actual path stays on its checked TOP Router face without adding transitions");
                path[index][0]=row.x;
                path[index][1]=row.y;
                path[index][2]=row.layer;
            }
            graphBinding=new ZeroViaGraphBinding(search,targetBranch,publisher,found,foundTerminal,
                goals,reached,faces,layout,start,root,netId,
                P07ScalarReflection.intField(search,"pass"),width,height,cells,originX,originY,
                pitch,endLayer,endCell,policy,primaryLayer,initialLayerMask,maximumVias,
                prefixHoles,reachedIds);
            P07ZeroViaGraphDiagnostics.Context context=new P07ZeroViaGraphDiagnostics.Context(
                layout,faces,netId,start,root,reached,goals,width,height,originX,originY,pitch,
                initialLayerMask,path);
            graphCursor=P07ZeroViaGraphDiagnostics.begin(context);
            require(graphCursor!=null && !graphCursor.isComplete(),
                "actual finite two-face zero-via graph cursor begins pending and resumable");
            beginPrefixAllocation(search,prefixHoles,layout);
            verifyGraphBindingUnchanged(false);
            verifyPrefixAllocationBindingUnchanged(false);
        }

        private void beginPrefixAllocation(Object search,PcbBoardHole[] prefixHoles,
                PcbBoardLayout layout) {
            require(prefixAllocationCursor==null && prefixAllocationBinding==null &&
                search==targetSearch && targetBranchSearch==search && targetBranch!=null &&
                prefixHoles!=null && layout!=null,
                "actual allocation cursor begins once on the retained same-owner D_SENSE branch");
            Object netsObject=P07ScalarReflection.read(search,"nets");
            require(netsObject instanceof Vector,
                "actual Search retains its source-ordered routing-net vector");
            Vector<?> nets=(Vector<?>)netsObject;
            require(nets.size()>0 && nets.size()<=256,
                "actual routing-net vector stays within the frozen allocation identity bound");
            String[] netOrder=new String[nets.size()];
            HashSet<String> uniqueNets=new HashSet<String>();
            for(int index=0;index<nets.size();index++) {
                Object value=nets.get(index);
                require(value instanceof String,
                    "actual Search net vector contains only source String values");
                String net=(String)value;
                require(net.length()>0 && net.length()<=4096 &&
                    uniqueNets.add(net) && net.equals(PcbConductorGraph.requireId(net)),
                    "actual source-order routing nets are unique, bounded, and valid identities");
                netOrder[index]=net;
            }
            int searchNetIndex=P07ScalarReflection.intField(search,"netIndex");
            int currentNetPosition=searchNetIndex-1;
            String currentNet=P07ScalarReflection.stringField(search,"currentNet");
            int currentBranchOrdinal=P07ScalarReflection.intField(targetBranch,"branchOrdinal");
            int searchBranchCount=P07ScalarReflection.intField(search,"branches");
            int pass=P07ScalarReflection.intField(search,"pass");
            int maximumVias=P07ScalarReflection.intField(search,"maximumVias");
            Object templateObject=P07ScalarReflection.read(routeSession,"template");
            require(currentNetPosition>=0 && currentNetPosition<netOrder.length &&
                currentNet!=null && currentNet.equals(netOrder[currentNetPosition]) &&
                currentNet.equals(snapshot.netId) && currentBranchOrdinal>0 &&
                searchBranchCount==currentBranchOrdinal && pass==1 && maximumVias==48 &&
                prefixHoles.length==48 && layout.getHoleCount()==48 &&
                templateObject instanceof PcbBoardLayout &&
                ((PcbBoardLayout)templateObject).getHoleCount()==0 &&
                ((PcbBoardLayout)templateObject).getHoles().isEmpty(),
                "allocation binds current actual net position/branch and empty Session template to its 48-hole prefix");
            prefixAllocationBinding=new PrefixAllocationBinding(search,nets,netOrder,
                searchNetIndex,currentNet,currentBranchOrdinal,pass,maximumVias,
                targetBranch,prefixHoles,layout,(PcbBoardLayout)templateObject);
            P07PrefixAllocationDiagnostics.Context context=
                new P07PrefixAllocationDiagnostics.Context(layout,prefixHoles,netOrder,
                    searchNetIndex,currentNet,currentBranchOrdinal,pass,maximumVias);
            prefixAllocationCursor=P07PrefixAllocationDiagnostics.begin(context);
            require(prefixAllocationCursor!=null && !prefixAllocationCursor.isComplete(),
                "actual prefix allocation diagnostic begins pending before the ordinary graph cursor resumes");
            verifyPrefixAllocationBindingUnchanged(false);
            verifyLoad12CorroboratesFinalDPrefix();
        }

        private void verifyPrefixAllocationBindingUnchanged(boolean ownerAdvanced) {
            PrefixAllocationBinding binding=prefixAllocationBinding;
            require(binding!=null && prefixAllocationCursor!=null &&
                targetSearch==binding.search && targetBranch==binding.branch &&
                targetBranchSearch==binding.search,
                "actual Search and retained BranchSearch identities remain bound to prefix allocation");
            Object activeSearch=P07ScalarReflection.read(routeSession,"search");
            require(ownerAdvanced ? activeSearch==null : activeSearch==binding.search,
                "same P07 owner remains quiescent until the captured branch is rejected");
            Object sourceNets=P07ScalarReflection.read(binding.search,"nets");
            require(sourceNets==binding.netVectorIdentity && sourceNets instanceof Vector &&
                P07ScalarReflection.intField(binding.search,"netIndex")==binding.searchNetIndex &&
                binding.currentNet.equals(
                    P07ScalarReflection.stringField(binding.search,"currentNet")) &&
                P07ScalarReflection.intField(binding.search,"branches")==
                    binding.currentBranchOrdinal &&
                P07ScalarReflection.intField(binding.branch,"branchOrdinal")==
                    binding.currentBranchOrdinal &&
                binding.currentNet.equals(
                    P07ScalarReflection.stringField(binding.branch,"net")) &&
                P07ScalarReflection.intField(binding.search,"pass")==binding.pass &&
                P07ScalarReflection.intField(binding.search,"maximumVias")==
                    binding.maximumVias &&
                P07ScalarReflection.read(binding.search,"layout")==binding.layout &&
                P07ScalarReflection.read(binding.search,"publisher")==targetPublisher &&
                P07ScalarReflection.read(binding.branch,"holes")==binding.prefixHoles &&
                P07ScalarReflection.read(routeSession,"template")==binding.template &&
                binding.templateHoleCount==0 && binding.template.getHoleCount()==0 &&
                binding.template.getHoles().isEmpty() &&
                binding.layout.getHoleCount()==binding.prefixHoles.length &&
                binding.prefixHoles.length==48 &&
                P07ScalarReflection.intField(binding.search,"maximumVias")==48,
                "source net vector/cursor, branch ordinal, actual layout, Publisher, and empty template stay unchanged");
            Vector<?> nets=(Vector<?>)sourceNets;
            require(nets.size()==binding.netValues.length &&
                binding.currentNetPosition==binding.searchNetIndex-1 &&
                binding.currentNetPosition>=0 &&
                binding.currentNetPosition<binding.netValues.length &&
                binding.netValues[binding.currentNetPosition].equals(binding.currentNet),
                "actual net-vector length and next-fetch current position remain exact");
            for(int index=0;index<binding.netValues.length;index++)
                require(binding.netValues[index].equals(nets.get(index)),
                    "actual Search net-order value remains unchanged at index "+index);
            Vector<PcbBoardHole> layoutHoles=binding.layout.getHoles();
            require(layoutHoles.size()==binding.prefixHoles.length &&
                binding.prefixGeometryFingerprint.equals(
                    binding.layout.geometryFingerprint()),
                "actual layout prefix count/order geometry fingerprint remain unchanged");
            for(int index=0;index<binding.prefixHoles.length;index++) {
                PcbBoardHole hole=binding.prefixHoles[index];
                require(hole!=null && hole==layoutHoles.get(index) &&
                    hole.id.equals(binding.holeIds[index]) &&
                    hole.fingerprint().equals(binding.holeFingerprints[index]),
                    "original prefix hole identity, ID and fingerprint remain unchanged at index "+index);
            }
            if(graphCursor!=null && graphCursor.isComplete()) {
                require(sha256Hex(binding.prefixGeometryFingerprint).equals(
                    graphCursor.result().prefixGeometrySha256),
                    "allocation layout fingerprint remains the completed graph's exact geometry authority");
            }
        }

        private void advancePrefixAllocation(Budget budget) {
            require(prefixAllocationCursor!=null && !prefixAllocationCursor.isComplete() &&
                prefixAllocationBinding!=null && !qualityRejected,
                "pending allocation cursor owns the actual quiescent Publisher before P07 resumes");
            require(budget!=null && budget.unitName!=null &&
                budget.unitName.startsWith("p07-quality-prefix-allocation-advance-") &&
                budget.workUnits>lastPrefixAllocationBudgetUnit,
                "each allocation cursor advance receives a newly charged existing outer work unit");
            int workUnit=budget.workUnits;
            int callsBefore=prefixAllocationAdvanceCalls;
            budget.checkpoint();
            verifyQualityPathSourceUnchanged(routeSession,targetSearch,targetPublisher,
                fixedPrefixContext,targetStartPad,targetRootPad,targetEndNode,pathSnapshot);
            verifyGraphBindingUnchanged(false);
            verifyPrefixAllocationBindingUnchanged(false);
            boolean advanceComplete=prefixAllocationCursor.advance(MAX_MANUAL_STEPS_PER_UNIT);
            prefixAllocationAdvanceCalls++;
            int lastWork=prefixAllocationCursor.lastWork();
            require(advanceComplete==prefixAllocationCursor.isComplete() &&
                prefixAllocationAdvanceCalls==callsBefore+1 && budget.workUnits==workUnit &&
                lastWork>0 && lastWork<=MAX_MANUAL_STEPS_PER_UNIT,
                "one resumable allocation cursor advance completes within its single bounded work unit");
            budget.checkpoint();
            verifyQualityPathSourceUnchanged(routeSession,targetSearch,targetPublisher,
                fixedPrefixContext,targetStartPad,targetRootPad,targetEndNode,pathSnapshot);
            verifyGraphBindingUnchanged(false);
            verifyPrefixAllocationBindingUnchanged(false);
            if(prefixAllocationCursor.isComplete())
                captureCompletedPrefixAllocationCursor(lastWork);
            budget.checkpoint();
            lastPrefixAllocationBudgetUnit=workUnit;
        }

        private void captureCompletedPrefixAllocationCursor(int lastWork) {
            require(prefixAllocationCursor!=null && prefixAllocationCursor.isComplete() &&
                prefixAllocationDiagnosticJson==null && lastWork>0 &&
                lastWork<=MAX_MANUAL_STEPS_PER_UNIT,
                "completed prefix allocation cursor has one bounded final work result");
            String diagnostic=prefixAllocationCursor.bodyJson();
            String repeatedDiagnostic=prefixAllocationCursor.bodyJson();
            int totalHoles=prefixAllocationCursor.result().totalHoles;
            require(diagnostic!=null && diagnostic.equals(repeatedDiagnostic) &&
                totalHoles==prefixAllocationBinding.prefixHoles.length &&
                prefixAllocationCursor.isComplete() &&
                prefixAllocationCursor.lastWork()==lastWork,
                "completed allocation body and immutable total-hole result remain stable across reads");
            prefixAllocationDiagnosticJson=diagnostic;
            prefixAllocationTotalHoles=totalHoles;
            prefixAllocationCompletedLastWork=lastWork;
        }

        private void verifyGraphBindingUnchanged(boolean ownerAdvanced) {
            ZeroViaGraphBinding binding=graphBinding;
            require(binding!=null && targetBranch==binding.branch &&
                targetBranchSearch==binding.search && targetSearch==binding.search &&
                targetPublisher==binding.publisher && targetEndNode==binding.endNode,
                "retained actual BranchSearch, Search, Publisher and end Node object identities remain bound");
            Object activeSearch=P07ScalarReflection.read(routeSession,"search");
            require(ownerAdvanced ? activeSearch==null : activeSearch==binding.search,
                "P07 owner remains stopped at the captured Search until cursor completion, then advances only to its actual rejection");
            PcbNetRouter.Router[] faces=(PcbNetRouter.Router[])
                P07ScalarReflection.read(binding.search,"faces");
            PcbPadPlacement[] reached=(PcbPadPlacement[])
                P07ScalarReflection.read(binding.branch,"reachedTerminals");
            boolean[] goals=(boolean[])P07ScalarReflection.read(binding.branch,"goal");
            int derivedInitialLayerMask=0;
            for(int layer=0;layer<2;layer++)
                if(layer==binding.primaryLayer ||
                        binding.policy==PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER)
                    derivedInitialLayerMask|=1<<layer;
            require(P07ScalarReflection.read(binding.search,"owner")==routeSession &&
                P07ScalarReflection.read(binding.search,"branch")==null &&
                P07ScalarReflection.read(binding.search,"publisher")==binding.publisher &&
                P07ScalarReflection.read(binding.search,"layout")==binding.layout &&
                P07ScalarReflection.intField(binding.search,"pass")==binding.pass &&
                P07ScalarReflection.read(binding.search,"policy")==binding.policy &&
                P07ScalarReflection.intField(binding.search,"primary")==binding.primaryLayer &&
                P07ScalarReflection.intField(binding.search,"maximumVias")==binding.maximumVias &&
                binding.netId.equals(P07ScalarReflection.stringField(binding.search,"currentNet")) &&
                P07ScalarReflection.read(binding.search,"currentRoot")==binding.root &&
                faces==binding.facesIdentity && faces.length==2 &&
                faces[0]==binding.faceValues[0] && faces[1]==binding.faceValues[1] &&
                binding.policy==PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER &&
                binding.primaryLayer==PcbCopperLayer.BOTTOM.ordinal() &&
                binding.initialLayerMask==derivedInitialLayerMask && derivedInitialLayerMask==3 &&
                P07ScalarReflection.read(binding.branch,"found")==binding.endNode &&
                P07ScalarReflection.read(binding.branch,"foundTerminal")==binding.foundTerminal &&
                P07ScalarReflection.read(binding.branch,"goal")==binding.goalIdentity &&
                Arrays.equals(binding.goalValues,goals) &&
                P07ScalarReflection.read(binding.branch,"reachedTerminals")==binding.reachedIdentity &&
                reached.length==binding.reachedValues.length &&
                P07ScalarReflection.read(binding.branch,"holes")==binding.holesIdentity &&
                ((PcbBoardHole[])P07ScalarReflection.read(binding.branch,"holes")).length==
                    binding.prefixHoles.length &&
                P07ScalarReflection.read(binding.branch,"start")==binding.start &&
                P07ScalarReflection.read(binding.branch,"root")==binding.root &&
                P07ScalarReflection.read(binding.publisher,"end")==binding.endNode &&
                P07ScalarReflection.read(binding.publisher,"endTerminal")==binding.foundTerminal &&
                P07ScalarReflection.read(binding.publisher,"start")==binding.start &&
                P07ScalarReflection.read(binding.publisher,"root")==binding.root &&
                binding.netId.equals(P07ScalarReflection.stringField(binding.publisher,"net")) &&
                P07ScalarReflection.intField(binding.publisher,"stage")==1 &&
                P07ScalarReflection.intField(binding.publisher,"qualityIndex")==66,
                "original branch goal, two-face router, policy, hole cap and endpoint authorities stay unchanged and quiescent");
            for(int index=0;index<reached.length;index++)
                require(reached[index]==binding.reachedValues[index] &&
                    binding.reachedPadIds[index].equals(reached[index].getPadId()),
                    "actual reached PTH identity and canonical pad ID remain unchanged at index "+index);
            PcbBoardHole[] branchHoles=(PcbBoardHole[])
                P07ScalarReflection.read(binding.branch,"holes");
            PcbBoardHole[] layoutHoles=binding.layout.getHoles().toArray(new PcbBoardHole[0]);
            require(branchHoles.length==binding.prefixHoleCount &&
                layoutHoles.length==binding.prefixHoleCount,
                "actual prefix hole count stays bound to both BranchSearch and layout");
            for(int index=0;index<branchHoles.length;index++)
                require(branchHoles[index]==binding.prefixHoles[index] &&
                    branchHoles[index]==layoutHoles[index],
                    "actual prefix hole identity remains unchanged at index "+index);
            int endCell=P07ScalarReflection.intField(binding.endNode,"cell");
            int endLayer=P07ScalarReflection.intField(binding.endNode,"layer");
            require(endCell==binding.endCell && endLayer==binding.endLayer &&
                binding.goalValues[endLayer*binding.cells+endCell],
                "actual found end cell and layer remain a true original goal");
        }

        private void advanceZeroViaGraph(Budget budget) {
            require(graphCursor!=null && !graphCursor.isComplete() &&
                graphBinding!=null && !qualityRejected,
                "pending zero-via graph owns the actual quiescent Publisher before P07 resumes");
            require(budget!=null && budget.unitName!=null &&
                budget.unitName.startsWith("p07-quality-zero-via-graph-advance-") &&
                budget.workUnits>lastGraphBudgetUnit,
                "each pending cursor advance receives one newly charged existing outer work unit");
            int workUnit=budget.workUnits;
            int callsBefore=graphAdvanceCalls;
            budget.checkpoint();
            verifyQualityPathSourceUnchanged(routeSession,targetSearch,targetPublisher,
                fixedPrefixContext,targetStartPad,targetRootPad,targetEndNode,pathSnapshot);
            verifyGraphBindingUnchanged(false);
            verifyPrefixAllocationBindingUnchanged(false);
            boolean advanceComplete=graphCursor.advance(MAX_MANUAL_STEPS_PER_UNIT);
            graphAdvanceCalls++;
            int lastWork=graphCursor.lastWork();
            require(advanceComplete==graphCursor.isComplete() &&
                graphAdvanceCalls==callsBefore+1 && budget.workUnits==workUnit &&
                lastWork>0 && lastWork<=MAX_MANUAL_STEPS_PER_UNIT,
                "one resumable cursor advance completes within its single existing bounded work unit");
            budget.checkpoint();
            verifyQualityPathSourceUnchanged(routeSession,targetSearch,targetPublisher,
                fixedPrefixContext,targetStartPad,targetRootPad,targetEndNode,pathSnapshot);
            verifyGraphBindingUnchanged(false);
            verifyPrefixAllocationBindingUnchanged(false);
            if(graphCursor.isComplete()) captureCompletedGraphCursor(lastWork);
            budget.checkpoint();
            lastGraphBudgetUnit=workUnit;
        }

        private void captureCompletedGraphCursor(int lastWork) {
            require(graphCursor!=null && graphCursor.isComplete() &&
                graphDiagnosticJson==null && lastWork>0 &&
                lastWork<=MAX_MANUAL_STEPS_PER_UNIT,
                "completed zero-via graph cursor has a single bounded final work result");
            String diagnostic=graphCursor.bodyJson();
            int ordinaryQueries=graphCursor.result().ordinaryQueries;
            String repeatedDiagnostic=graphCursor.bodyJson();
            int repeatedCount=graphCursor.result().ordinaryQueries;
            require(diagnostic!=null && diagnostic.equals(repeatedDiagnostic) &&
                ordinaryQueries>=0 && repeatedCount==ordinaryQueries &&
                graphCursor.isComplete() && graphCursor.lastWork()==lastWork,
                "completed cursor body and immutable structured result remain stable across reads");
            graphDiagnosticJson=diagnostic;
            graphOrdinaryQueries=ordinaryQueries;
            graphCompletedLastWork=lastWork;
        }

        private void verifyCompletedGraphCursor() {
            require(graphCursor!=null && graphCursor.isComplete() &&
                graphDiagnosticJson!=null && graphOrdinaryQueries>=0 &&
                graphCursor.result().ordinaryQueries==graphOrdinaryQueries &&
                graphCursor.lastWork()==graphCompletedLastWork,
                "completed zero-via graph cursor retains its terminal body, query count and last-work state");
        }

        private void verifyTargetContext(
                PcbLayerRoutingPrototype.FixedPrefixCaptureContext context) {
            require(variant==Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING &&
                context.layoutSeed()==PLACEMENT_DIAGNOSTIC_SEED &&
                context.routingSeed()==ROUTING_DIAGNOSTIC_ROUTING_SEED &&
                context.routeRequestIndex()==0 && context.selectedPlacementIndex()==0 &&
                context.selectedPlacementAttempt()==4 &&
                Arrays.equals(context.eligiblePlacementAttempts(),new int[]{4,2,3,0,1}) &&
                context.rawPlacementFingerprint()!=null &&
                context.rawPlacementFingerprint().length()>0 &&
                context.rawPlacementFingerprint().length()<=1024*1024,
                "actual quality probe context binds exact seeds, request0, eligible index0, attempt4 and canonical placement order");
        }

        private void inspectActualTargetAfterSlice(
                PcbLayerRoutingPrototype.OrderingProgress progress) {
            Object search=P07ScalarReflection.read(routeSession,"search");
            inspectLoad12AfterSlice(search);
            if(!targetCaptured && search!=null) {
                int pass=P07ScalarReflection.intField(search,"pass");
                Object publisher=P07ScalarReflection.read(search,"publisher");
                if(pass==1 && publisher!=null && "D_SENSE".equals(
                        P07ScalarReflection.stringField(publisher,"net"))) {
                    int stage=P07ScalarReflection.intField(publisher,"stage");
                    Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
                    int qualityIndex=P07ScalarReflection.intField(publisher,"qualityIndex");
                    if(stage==1 && reversed!=null && qualityIndex==reversed.size()) {
                        snapshot=captureActualQualityScalars(search,publisher,
                            (PcbLayerRoutingPrototype.FixedPrefixCaptureContext)fixedPrefixContext);
                        require(snapshot.pathNodeCount>1 && snapshot.planarLength>0,
                            "actual D_SENSE target is a nonempty found path, not a zero-length route");
                        require(snapshot.rejectDirectZero || snapshot.rejectLengthOverThreeDirect ||
                            snapshot.rejectBendsOver16,
                            "actual D_SENSE Publisher state must explain the existing branch-quality rejection");
                        targetSearch=search;
                        targetPublisher=publisher;
                        targetStartPad=P07ScalarReflection.read(publisher,"start");
                        targetRootPad=P07ScalarReflection.read(publisher,"root");
                        targetEndNode=P07ScalarReflection.read(publisher,"end");
                        pathSnapshot=captureActualQualityPath(routeSession,search,publisher,
                            (PcbLayerRoutingPrototype.FixedPrefixCaptureContext)fixedPrefixContext,
                            snapshot);
                        targetCaptured=true;
                        beginZeroViaGraph(search,publisher);
                    }
                }
            }
            if(targetCaptured && !qualityRejected) {
                int ownerPass=P07ScalarReflection.intField(routeSession,"pass");
                Object currentSearch=P07ScalarReflection.read(routeSession,"search");
                if(ownerPass==2 && currentSearch==null) {
                    String reason=P07ScalarReflection.stringField(routeSession,"reason");
                    require("D_SENSE:BRANCH_QUALITY_LIMIT".equals(reason),
                        "same P07 owner records the subsequent actual D_SENSE branch-quality rejection");
                    PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
                        (PcbLayerRoutingPrototype.FixedPrefixCaptureContext)fixedPrefixContext;
                    require(P07ScalarReflection.read(targetSearch,"publisher")==targetPublisher &&
                        P07ScalarReflection.read(targetSearch,"owner")==routeSession &&
                        P07ScalarReflection.read(routeSession,"fixedPrefixContext")==context &&
                        P07ScalarReflection.intField(targetSearch,"pass")==snapshot.pass &&
                        snapshot.pass==1 && snapshot.netId.equals(
                            P07ScalarReflection.stringField(targetSearch,"currentNet")) &&
                        P07ScalarReflection.read(targetPublisher,"start")==targetStartPad &&
                        P07ScalarReflection.read(targetPublisher,"root")==targetRootPad &&
                        P07ScalarReflection.read(targetPublisher,"end")==targetEndNode &&
                        snapshot.startPadId.equals(((PcbPadPlacement)targetStartPad).getPadId()) &&
                        snapshot.rootPadId.equals(((PcbPadPlacement)targetRootPad).getPadId()),
                        "actual rejection retains the same request/pass/net/start/root and Publisher object identity");
                    require(graphCursor!=null && graphCursor.isComplete() &&
                        graphAdvanceCalls>0,
                        "actual branch-quality rejection resumes only after the bounded query cursor completes");
                    verifyQualityPathSourceUnchanged(routeSession,targetSearch,targetPublisher,
                        fixedPrefixContext,targetStartPad,targetRootPad,targetEndNode,pathSnapshot);
                    verifyGraphBindingUnchanged(true);
                    verifyPrefixAllocationBindingUnchanged(true);
                    qualityRejectionMessage=reason;
                    qualityRejected=true;
                } else {
                    Object currentPublisher=currentSearch==null?null:
                        P07ScalarReflection.read(currentSearch,"publisher");
                    require(currentPublisher==targetPublisher,
                        "captured Publisher must be rejected by its actual guard before any successor branch or owner");
                    require(progress==PcbLayerRoutingPrototype.OrderingProgress.WORKING,
                        "actual quality rejection stays an internally consumed P07 failure, not a retained route");
                }
            }
        }

        void observeGeneratorAdvance(SeededPcbLayoutGenerator.Session generatorSession) {
            if(!qualityRejected || targetRequestAccounted) return;
            Object active=P07ScalarReflection.read(generatorSession,"mediumTwoLayerRoute");
            int accounted=P07ScalarReflection.intField(generatorSession,"mediumTwoLayerAttempts");
            if(active==routeSession) return;
            if(accounted==targetAccountingBefore+1) {
                targetAccountingAfter=accounted;
                targetRequestAccounted=true;
                verifyPrefixAllocationBindingUnchanged(true);
            } else if(accounted>targetAccountingBefore+1) {
                throw new AssertionError("target P07 request accounting advanced more than once before receipt");
            }
        }

        void confirmTerminalOperation(P07PolicyDiagnosticCase result,
                SeededPcbLayoutGenerator.Session generatorSession,
                SeededPcbLayoutGenerator generator,MediumBoardPhysicalPolicy.Result policyResult) {
            require(targetCaptured && qualityRejected && snapshot!=null &&
                routeSession!=null && fixedPrefixContext!=null && armedReadyGuard,
                "terminal scalar record has its exact actual target context and rejection");
            require(graphCursor!=null && graphCursor.isComplete() &&
                graphAdvanceCalls>0 && graphBinding!=null,
                "terminal zero-via graph diagnostic has completed all resumable work under the existing driver");
            require(prefixAllocationCursor!=null && prefixAllocationCursor.isComplete() &&
                prefixAllocationAdvanceCalls>0 && prefixAllocationDiagnosticJson!=null &&
                prefixAllocationTotalHoles==48,
                "terminal allocation diagnostic completes before the actual branch rejection resumes");
            require(targetRequestAccounted && targetAccountingAfter==targetAccountingBefore+1,
                "ordinary generator advancement accounts the target P07 request exactly once");
            require(result!=null && "EXHAUSTED".equals(result.sessionTerminalStatus) &&
                policyResult!=null && !policyResult.accepted() && policyResult.getLayout()==null &&
                result.layoutOutputJson.contains("\"layoutReturned\":false") &&
                result.sessionRejection!=null && result.sessionRejection.isExhausted(),
                "actual generator operation ends exhausted with no selected or returned layout");
            require(generatorSession!=null && generator!=null,
                "final rejection record remains owned by the actual generator session");
            Object lastFailure=P07ScalarReflection.read(generatorSession,"lastFailure");
            require(lastFailure instanceof PcbRoutingRejectedException,
                "actual generator lastFailure retains its typed PcbRoutingRejectedException owner");
            generatorLastFailureType=lastFailure.getClass().getName();
            generatorLastFailureMessage=((Throwable)lastFailure).getMessage();
            require(generatorLastFailureMessage!=null && generatorLastFailureMessage.length()>0,
                "actual generator lastFailure retains its source rejection message");
            require(graphCallbacks==0,
                "nonselecting quality observer never invokes graph materialization callback");
            requirePathSnapshotUnchanged();
            terminalConfirmed=true;
        }

        void requireTerminalOperation(P07PolicyDiagnosticCase result) {
            require(terminalConfirmed && targetCaptured && qualityRejected &&
                targetRequestAccounted && result!=null && result.cleanupPassed &&
                "PASS".equals(result.status) &&
                "EXHAUSTED".equals(result.sessionTerminalStatus) &&
                result.policyResult!=null && result.policyResult.getLayout()==null &&
                graphCallbacks==0,
                "quality scalar event is eligible only after successful cleanup and confirmed no-layout rejection");
            requirePathSnapshotUnchanged();
        }

        void requirePathSnapshotUnchanged() {
            require(pathSnapshot!=null && targetSearch!=null && targetPublisher!=null &&
                targetStartPad!=null && targetRootPad!=null && targetEndNode!=null,
                "actual Publisher path snapshot retains its same-operation source identities");
            verifyQualityPathSourceUnchanged(routeSession,targetSearch,targetPublisher,
                fixedPrefixContext,targetStartPad,targetRootPad,targetEndNode,pathSnapshot);
            verifyGraphBindingUnchanged(qualityRejected);
            verifyPrefixAllocationBindingUnchanged(qualityRejected);
            verifyCompletedGraphCursor();
            verifyCompletedPrefixAllocationCursor();
        }

        long manualDriverMillis() { return nanosToMillis(manualDriverNanos); }

        String eventJson(P07PolicyDiagnosticCase result) {
            requireTerminalOperation(result);
            PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
                (PcbLayerRoutingPrototype.FixedPrefixCaptureContext)fixedPrefixContext;
            return "RB100_QUALITY_SCALAR_EVENT {\"protocolVersion\":1,\"sourceRevision\":18,"+
                "\"runId\":\""+jsonFull(runId)+"\",\"operationId\":\""+jsonFull(operationId)+
                "\",\"variant\":\""+variant.name()+"\",\"mode\":\"PROBE\",\"rootSeed\":\""+
                SEED+"\",\"placementSeed\":\""+PLACEMENT_DIAGNOSTIC_SEED+
                "\",\"routingSeed\":\""+ROUTING_DIAGNOSTIC_ROUTING_SEED+
                "\",\"layoutVersion\":"+SeededPcbLayoutGenerator.CURRENT_VERSION+
                ",\"dependencyEpoch\":\""+jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)+
                "\",\"planVersion\":"+Rb100Plan.PLAN_VERSION+
                ",\"modelVersion\":"+Rb100Plan.MODEL_VERSION+
                ",\"physicalIntentVersion\":"+Rb100Placement.INTENT_VERSION+
                ",\"routeRequestIndex\":"+context.routeRequestIndex()
                +",\"selectedPlacementIndex\":"+context.selectedPlacementIndex()
                +",\"selectedPlacementAttempt\":"+context.selectedPlacementAttempt()
                +",\"eligiblePlacementAttempts\":"+integerArrayJson(context.eligiblePlacementAttempts())
                +",\"placementFingerprintLength\":"+context.rawPlacementFingerprint().length()
                +",\"placementFingerprintSha256\":\""+sha256Hex(context.rawPlacementFingerprint())+"\""
                +",\"pass\":"+snapshot.pass+",\"netId\":\""+jsonFull(snapshot.netId)+"\""
                +",\"startPadId\":\""+jsonFull(snapshot.startPadId)+"\",\"rootPadId\":\""+
                jsonFull(snapshot.rootPadId)+"\",\"endPadId\":null"
                +",\"startX\":"+snapshot.startX+",\"startY\":"+snapshot.startY
                +",\"endX\":"+snapshot.endX+",\"endY\":"+snapshot.endY
                +",\"endLayer\":"+snapshot.endLayer+",\"primaryLayer\":"+snapshot.primaryLayer
                +",\"firstLayer\":"+snapshot.firstLayer+",\"gridWidth\":"+snapshot.gridWidth
                +",\"gridHeight\":"+snapshot.gridHeight+",\"gridOriginX\":"+snapshot.gridOriginX
                +",\"gridOriginY\":"+snapshot.gridOriginY+",\"gridPitch\":"+snapshot.gridPitch
                +",\"pathNodeCount\":"+snapshot.pathNodeCount+",\"transitionCount\":"+
                snapshot.transitionCount+",\"planarLength\":"+snapshot.planarLength
                +",\"directDistance\":"+snapshot.directDistance+",\"bends\":"+snapshot.bends
                +",\"rejectDirectZero\":"+snapshot.rejectDirectZero
                +",\"rejectLengthOverThreeDirect\":"+snapshot.rejectLengthOverThreeDirect
                +",\"rejectBendsOver16\":"+snapshot.rejectBendsOver16
                +",\"qualityRejected\":true,\"qualityRejectionOwner\":\"PcbLayerRoutingPrototype.Session#reason\""
                +",\"qualityRejectionMessage\":\""+jsonFull(qualityRejectionMessage)+"\""
                +",\"generatorLastFailureSource\":\"SeededPcbLayoutGenerator.Session#lastFailure\""
                +",\"generatorLastFailureType\":\""+jsonFull(generatorLastFailureType)+"\""
                +",\"generatorLastFailureMessage\":\""+jsonFull(generatorLastFailureMessage)+"\""
                +",\"routeMillisStatus\":\"INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE\""
                +",\"manualDriverMillis\":"+manualDriverMillis()+",\"manualSliceSteps\":"+
                manualSliceSteps+",\"acceptanceFlags\":{\"accepted\":false,\"routed\":false,\"playable\":false}"
                +",\"feasibility\":\"UNKNOWN\",\"layoutQualification\":false,\"graphCallbacks\":"+
                graphCallbacks+"}";
        }

        String pathEventJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireTerminalOperation(result);
            require(pairPassed && "QUALITY_PROBE".equals(operationId),
                "quality path event requires the confirmed same-run QUALITY_PROBE scalar pair");
            requirePathSnapshotUnchanged();
            StringBuilder nodes=new StringBuilder("[");
            for(int index=0;index<pathSnapshot.nodes.length;index++) {
                if(index>0) nodes.append(',');
                nodes.append(pathSnapshot.nodes[index].toJson());
            }
            nodes.append(']');
            return "RB100_QUALITY_PATH_EVENT {\"protocolVersion\":1,\"sourceRevision\":18,"+
                "\"runId\":\""+jsonFull(runId)+"\",\"operationId\":\""+
                jsonFull(operationId)+"\",\"order\":\"START_TO_END\",\"captureStage\":"+
                pathSnapshot.captureStage+",\"captureQualityIndex\":"+
                pathSnapshot.captureQualityIndex+",\"parentIdentityVerified\":true,"+
                "\"nodeIdentityUnique\":true,\"nodes\":"+nodes+
                ",\"scope\":\"ACTUAL_PUBLISHER_PATH_ONLY\",\"feasibility\":\"UNKNOWN\","+
                "\"legalShortcutProof\":false,\"layoutQualification\":false}";
        }

        String zeroViaGraphEventJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireTerminalOperation(result);
            require(pairPassed && "QUALITY_PROBE".equals(operationId) && graphBinding!=null &&
                graphCursor!=null && graphCursor.isComplete() && graphAdvanceCalls>0 &&
                graphDiagnosticJson!=null,
                "zero-via graph event follows same-run scalar parity, terminal rejection and every cursor unit");
            require(qualityRejected && targetRequestAccounted && terminalConfirmed,
                "zero-via graph event follows the same actual quality rejection and terminal cleanup");
            verifyGraphBindingUnchanged(true);
            ZeroViaGraphBinding binding=graphBinding;
            String foundTerminal=binding.foundTerminal==null ? "null" :
                "\""+jsonFull(((PcbPadPlacement)binding.foundTerminal).getPadId())+"\"";
            StringBuilder reached=new StringBuilder("[");
            for(int index=0;index<binding.reachedPadIds.length;index++) {
                if(index>0) reached.append(',');
                reached.append('"').append(jsonFull(binding.reachedPadIds[index])).append('"');
            }
            reached.append(']');
            return "RB100_ZERO_VIA_GRAPH_EVENT {\"protocolVersion\":1,\"sourceRevision\":18,"+
                "\"runId\":\""+jsonFull(runId)+"\",\"operationId\":\"QUALITY_PROBE\","+
                "\"scope\":\"ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY\",\"binding\":{"+
                "\"branchIdentityVerified\":true,\"sourcePublisherUnchanged\":true,"+
                "\"pass\":"+binding.pass+",\"netId\":\""+jsonFull(binding.netId)+"\","+
                "\"startPadId\":\""+jsonFull(binding.start.getPadId())+"\","+
                "\"rootPadId\":\""+jsonFull(binding.root.getPadId())+"\","+
                "\"reachedPthPadIds\":"+reached+",\"foundTerminalPadId\":"+foundTerminal+","+
                "\"endCell\":"+binding.endCell+",\"endLayer\":"+binding.endLayer+","+
                "\"goalMaskLength\":"+binding.goalValues.length+",\"policy\":\""+
                binding.policy.name()+"\",\"primaryLayer\":"+binding.primaryLayer+
                ",\"initialLayerMask\":"+binding.initialLayerMask+",\"maximumVias\":"+
                binding.maximumVias+",\"prefixHoleCount\":"+binding.prefixHoleCount+
                "},\"diagnostic\":"+graphDiagnosticJson+
                ",\"feasibility\":\"UNKNOWN\",\"legalRouteProof\":false,"+
                "\"layoutQualification\":false,\"routeAdoption\":false}";
        }

        String zeroViaGraphSummaryJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireTerminalOperation(result);
            require(pairPassed && "QUALITY_PROBE".equals(operationId) && graphBinding!=null &&
                graphCursor!=null && graphCursor.isComplete(),
                "zero-via graph summary follows the parity-checked completed actual branch diagnostic");
            verifyGraphBindingUnchanged(true);
            int ordinaryQueries=graphOrdinaryQueries;
            require(ordinaryQueries>=0 && ordinaryQueries==graphCursor.result().ordinaryQueries,
                "helper structured result exposes its final ordinary edge query count");
            return "PASS: RB100 quality zero-via graph contracts {\"protocolVersion\":1,\"sourceRevision\":18,"+
                "\"status\":\"PASS\",\"runId\":\""+jsonFull(runId)+
                "\",\"operationId\":\"QUALITY_PROBE\",\"events\":1,"+
                "\"scope\":\"ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY\",\"ordinaryQueries\":"+
                ordinaryQueries+",\"feasibility\":\"UNKNOWN\",\"legalRouteProof\":false,"+
                "\"layoutQualification\":false,\"routeAdoption\":false}";
        }

        private void verifyCompletedPrefixAllocationCursor() {
            require(prefixAllocationCursor!=null && prefixAllocationCursor.isComplete() &&
                prefixAllocationBinding!=null && prefixAllocationDiagnosticJson!=null &&
                prefixAllocationAdvanceCalls>0 && prefixAllocationCompletedLastWork>0 &&
                prefixAllocationCursor.result().totalHoles==prefixAllocationTotalHoles &&
                prefixAllocationTotalHoles==prefixAllocationBinding.prefixHoles.length &&
                prefixAllocationCursor.lastWork()==prefixAllocationCompletedLastWork &&
                prefixAllocationDiagnosticJson.equals(prefixAllocationCursor.bodyJson()),
                "completed allocation cursor retains its immutable body, hole total and terminal work state");
            require(graphCursor!=null && graphCursor.isComplete() &&
                sha256Hex(prefixAllocationBinding.prefixGeometryFingerprint).equals(
                    graphCursor.result().prefixGeometrySha256),
                "allocation prefix fingerprint equals the same actual graph geometry fingerprint");
        }

        String prefixAllocationEventJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireTerminalOperation(result);
            require(pairPassed && "QUALITY_PROBE".equals(operationId) &&
                qualityRejected && targetRequestAccounted && terminalConfirmed &&
                result.cleanupPassed && prefixAllocationBinding!=null,
                "prefix allocation event follows scalar parity, same-owner rejection/accounting and cleanup");
            verifyGraphBindingUnchanged(true);
            verifyPrefixAllocationBindingUnchanged(true);
            verifyCompletedPrefixAllocationCursor();
            PrefixAllocationBinding binding=prefixAllocationBinding;
            require(binding.pass==1 && binding.maximumVias==48 &&
                binding.prefixHoles.length==48 && binding.templateHoleCount==0 &&
                binding.template.getHoleCount()==0 &&
                binding.searchNetIndex==binding.currentNetPosition+1 &&
                binding.netValues[binding.currentNetPosition].equals(binding.currentNet) &&
                binding.currentNet.equals(graphBinding.netId) &&
                binding.currentBranchOrdinal>0,
                "allocation event binds the exact current source net, branch, empty template and 48-hole prefix");
            return "RB100_PREFIX_ALLOCATION_EVENT {\"protocolVersion\":1,\"sourceRevision\":18,"+
                "\"runId\":\""+jsonFull(runId)+"\",\"operationId\":\"QUALITY_PROBE\","+
                "\"scope\":\"ACTUAL_FIXED_PREFIX_CREATION_ALLOCATION_ONLY\",\"binding\":{"+
                "\"branchIdentityVerified\":true,\"sourcePublisherUnchanged\":true,"+
                "\"pass\":"+binding.pass+",\"netId\":\""+jsonFull(binding.currentNet)+"\","+
                "\"startPadId\":\""+jsonFull(graphBinding.start.getPadId())+"\","+
                "\"rootPadId\":\""+jsonFull(graphBinding.root.getPadId())+"\","+
                "\"prefixHoleCount\":"+binding.prefixHoles.length+
                ",\"maximumVias\":"+binding.maximumVias+
                ",\"templateHoleCount\":0,\"searchNetIndex\":"+binding.searchNetIndex+
                ",\"currentNetPosition\":"+binding.currentNetPosition+
                ",\"currentBranchOrdinal\":"+binding.currentBranchOrdinal+
                ",\"netOrder\":"+stringArrayJson(binding.netValues)+"},\"diagnostic\":"+
                prefixAllocationDiagnosticJson+",\"chronologyProof\":false,"+
                "\"routeAdoption\":false,\"layoutQualification\":false}";
        }

        String prefixAllocationSummaryJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireTerminalOperation(result);
            require(pairPassed && "QUALITY_PROBE".equals(operationId) &&
                qualityRejected && targetRequestAccounted && terminalConfirmed &&
                result.cleanupPassed,
                "prefix allocation summary follows the actual rejected and cleaned-up scalar operation");
            verifyCompletedPrefixAllocationCursor();
            return "PASS: RB100 prefix allocation contracts {\"protocolVersion\":1,"+
                "\"sourceRevision\":18,\"status\":\"PASS\",\"runId\":\""+
                jsonFull(runId)+"\",\"operationId\":\"QUALITY_PROBE\",\"events\":1,"+
                "\"scope\":\"ACTUAL_FIXED_PREFIX_CREATION_ALLOCATION_ONLY\",\"totalHoles\":"+
                prefixAllocationTotalHoles+",\"chronologyProof\":false,\"routeAdoption\":false,"+
                "\"layoutQualification\":false}";
        }

        private void requireLoad12CaptureComplete(P07PolicyDiagnosticCase result,
                boolean pairPassed) {
            requireTerminalOperation(result);
            require(pairPassed && "QUALITY_PROBE".equals(operationId) &&
                qualityRejected && targetRequestAccounted && terminalConfirmed &&
                result.cleanupPassed && "PASS".equals(result.status) &&
                runId.equals(result.runId) && result.qualityScalarProbe==this &&
                result.qualityScalarOperation &&
                "QUALITY_PROBE".equals(result.fixedPrefixOperationId) &&
                controlsJson!=null && graphCallbacks==0 &&
                load12BranchCaptured && load12BranchHandoffVerified &&
                load12PreStarted && load12PreComplete && load12PostStarted &&
                load12PostComplete && load12CaptureReleased && load12PreJson!=null &&
                load12PostJson!=null && load12PreAdvanceCalls>0 &&
                load12PostAdvanceCalls>0 &&
                load12PreAdvanceCalls+load12PostAdvanceCalls<=640 &&
                load12PreHoleIds!=null && load12PreHoleFingerprints!=null &&
                load12PostHoleIds!=null && load12PostHoleFingerprints!=null &&
                load12CreatedHoleIds!=null && prefixAllocationBinding!=null,
                "LOAD12 event follows the complete same-run accepted branch capture and clean terminal rejection");
            require(load12Search==null && load12Branch==null && load12Publisher==null &&
                load12ReversedIdentity==null && load12Seeds==null && load12Context==null &&
                load12Binding==null && load12PreCursor==null && load12PostCursor==null &&
                load12PreResult==null && load12PostResult==null,
                "completed LOAD12 capture releases all live source and cursor references before successor routing");
            require(load12PathNodeCount>=2 && load12PathNodeCount<=4096 &&
                load12TransitionCount>=0 && load12TransitionCount<=4 &&
                load12PrefixHoleCount==load12PreHoleIds.length &&
                load12PostHoleCount==load12PostHoleIds.length &&
                load12PostHoleCount<=48 &&
                load12CreatedHoleCount==load12CreatedHoleIds.length &&
                load12CreatedTraceCount>=0 && load12CreatedTraceCount<=100000,
                "LOAD12 retained result metrics match actual copied identity inventories and frozen caps");
            require(load12PreJson.contains("\"faceMask\":3") &&
                load12PreJson.contains("\"startCopperFaceMask\":3") &&
                load12PreJson.contains("\"rootCopperFaceMask\":3") &&
                load12PreJson.contains("\"policy\":\"FULLER_TWO_LAYER\"") &&
                load12PreJson.contains("\"maximumVias\":48") &&
                load12PreJson.contains("\"maximumTransitions\":4"),
                "LOAD12 result retains actual dual-face seeds, copper, FULLER policy and unchanged limits");
            require(prefixAllocationBinding.prefixHoles.length==48 &&
                prefixAllocationBinding.pass==1 &&
                "D_SENSE".equals(prefixAllocationBinding.currentNet) &&
                prefixAllocationBinding.currentBranchOrdinal>0,
                "LOAD12 event follows the later actual immutable D_SENSE prefix controls");
            verifyLoad12CorroboratesFinalDPrefix();
            verifyGraphBindingUnchanged(true);
            verifyPrefixAllocationBindingUnchanged(true);
            verifyCompletedGraphCursor();
            verifyCompletedPrefixAllocationCursor();
        }

        private void verifyLoad12EventHeader() {
            PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
                (PcbLayerRoutingPrototype.FixedPrefixCaptureContext)fixedPrefixContext;
            require(context!=null && runId!=null && !runId.isEmpty() &&
                "QUALITY_PROBE".equals(operationId) &&
                variant==Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING &&
                SEED==9007199254740993L &&
                PLACEMENT_DIAGNOSTIC_SEED==-6587958367974952701L &&
                ROUTING_DIAGNOSTIC_ROUTING_SEED==8445301674744508279L &&
                context.layoutSeed()==PLACEMENT_DIAGNOSTIC_SEED &&
                context.routingSeed()==ROUTING_DIAGNOSTIC_ROUTING_SEED &&
                context.routeRequestIndex()==0 && context.selectedPlacementIndex()==0 &&
                context.selectedPlacementAttempt()==4,
                "LOAD12 event header is bound to the exact approved macro run, operation, seeds and placement");
            verifyTargetContext(context);
        }

        String load12EventJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireLoad12CaptureComplete(result,pairPassed);
            verifyLoad12EventHeader();
            return "RB100_LOAD12_BRANCH_EVENT {\"protocolVersion\":1,\"sourceRevision\":18,"+
                "\"runId\":\""+jsonFull(runId)+"\",\"operationId\":\"QUALITY_PROBE\","+
                "\"scope\":\"ACTUAL_LOAD12_BRANCH_ENTRY_PATH_AND_PUBLICATION_ONLY\","+
                "\"rootSeed\":\"9007199254740993\",\"placementSeed\":\"-6587958367974952701\","+
                "\"routingSeed\":\"8445301674744508279\",\"routeRequestIndex\":0,"+
                "\"selectedPlacementIndex\":0,\"selectedPlacementAttempt\":4,"+
                "\"status\":\"PASS\",\"claims\":{\"avoidability\":\"UNKNOWN\","+
                "\"chronologyProof\":false,\"routeAdoption\":false,"+
                "\"layoutQualification\":false,\"playable100\":0},\"pre\":"+
                load12PreJson+",\"post\":"+load12PostJson+"}";
        }

        String load12SummaryJson(P07PolicyDiagnosticCase result,boolean pairPassed) {
            requireLoad12CaptureComplete(result,pairPassed);
            verifyLoad12EventHeader();
            return "PASS: RB100 LOAD12 branch contracts {\"protocolVersion\":1,"+
                "\"sourceRevision\":18,\"status\":\"PASS\",\"runId\":\""+
                jsonFull(runId)+"\",\"operationId\":\"QUALITY_PROBE\",\"events\":1,"+
                "\"scope\":\"ACTUAL_LOAD12_BRANCH_ENTRY_PATH_AND_PUBLICATION_ONLY\","+
                "\"rootSeed\":\"9007199254740993\",\"placementSeed\":\"-6587958367974952701\","+
                "\"routingSeed\":\"8445301674744508279\",\"routeRequestIndex\":0,"+
                "\"selectedPlacementIndex\":0,\"selectedPlacementAttempt\":4,"+
                "\"pathNodes\":"+load12PathNodeCount+",\"transitions\":"+
                load12TransitionCount+",\"createdHoles\":"+load12CreatedHoleCount+","+
                "\"claims\":{\"avoidability\":\"UNKNOWN\",\"chronologyProof\":false,"+
                "\"routeAdoption\":false,\"layoutQualification\":false,\"playable100\":0}}";
        }
    }

    private static final class QualityScalarSnapshot {
        final int pass,startX,startY,endX,endY,endLayer,primaryLayer,firstLayer,
            gridWidth,gridHeight,gridOriginX,gridOriginY,gridPitch,pathNodeCount,
            transitionCount,planarLength,directDistance,bends;
        final String netId,startPadId,rootPadId;
        final boolean rejectDirectZero,rejectLengthOverThreeDirect,rejectBendsOver16;
        QualityScalarSnapshot(int pass,String netId,String startPadId,String rootPadId,
                int startX,int startY,int endX,int endY,int endLayer,int primaryLayer,
                int firstLayer,int gridWidth,int gridHeight,int gridOriginX,int gridOriginY,
                int gridPitch,int pathNodeCount,int transitionCount,int planarLength,
                int directDistance,int bends) {
            this.pass=pass; this.netId=netId; this.startPadId=startPadId; this.rootPadId=rootPadId;
            this.startX=startX; this.startY=startY; this.endX=endX; this.endY=endY;
            this.endLayer=endLayer; this.primaryLayer=primaryLayer; this.firstLayer=firstLayer;
            this.gridWidth=gridWidth; this.gridHeight=gridHeight; this.gridOriginX=gridOriginX;
            this.gridOriginY=gridOriginY; this.gridPitch=gridPitch; this.pathNodeCount=pathNodeCount;
            this.transitionCount=transitionCount; this.planarLength=planarLength;
            this.directDistance=directDistance; this.bends=bends;
            rejectDirectZero=directDistance==0;
            rejectLengthOverThreeDirect=(long)planarLength>3L*directDistance;
            rejectBendsOver16=bends>16;
        }
    }

    private static final class QualityPathNodeSnapshot {
        final int index,parentIndex,cell,x,y,layer,direction,transitions,cost,heuristic,serial,key;
        QualityPathNodeSnapshot(int index,int parentIndex,int cell,int x,int y,int layer,
                int direction,int transitions,int cost,int heuristic,int serial,int key) {
            this.index=index; this.parentIndex=parentIndex; this.cell=cell; this.x=x; this.y=y;
            this.layer=layer; this.direction=direction; this.transitions=transitions;
            this.cost=cost; this.heuristic=heuristic; this.serial=serial; this.key=key;
        }
        String toJson() {
            return "{\"index\":"+index+",\"parentIndex\":"+parentIndex+
                ",\"cell\":"+cell+",\"x\":"+x+",\"y\":"+y+
                ",\"layer\":"+layer+",\"direction\":"+direction+
                ",\"transitions\":"+transitions+",\"cost\":"+cost+
                ",\"heuristic\":"+heuristic+",\"serial\":"+serial+
                ",\"key\":"+key+"}";
        }
    }

    private static final class QualityPathSnapshot {
        final int captureStage,captureQualityIndex;
        final Object sourceReversedIdentity;
        final Object[] sourceNodeIdentities;
        final QualityPathNodeSnapshot[] nodes;
        QualityPathSnapshot(int captureStage,int captureQualityIndex,
                Object sourceReversedIdentity,Object[] sourceNodeIdentities,
                QualityPathNodeSnapshot[] nodes) {
            this.captureStage=captureStage; this.captureQualityIndex=captureQualityIndex;
            this.sourceReversedIdentity=sourceReversedIdentity;
            this.sourceNodeIdentities=sourceNodeIdentities; this.nodes=nodes;
        }
    }

    private static final class ZeroViaGraphBinding {
        final Object search,branch,publisher,endNode,foundTerminal,goalIdentity,
            reachedIdentity,facesIdentity,holesIdentity;
        final PcbNetRouter.Router[] faceValues;
        final PcbBoardLayout layout;
        final PcbPadPlacement start,root;
        final PcbPadPlacement[] reachedValues;
        final PcbBoardHole[] prefixHoles;
        final boolean[] goalValues;
        final String netId;
        final String[] reachedPadIds;
        final PcbLayerRoutingPrototype.Policy policy;
        final int pass,width,height,cells,originX,originY,pitch,endCell,endLayer,
            primaryLayer,initialLayerMask,maximumVias,prefixHoleCount;
        ZeroViaGraphBinding(Object search,Object branch,Object publisher,Object endNode,
                PcbPadPlacement foundTerminal,boolean[] goalIdentity,
                PcbPadPlacement[] reachedIdentity,Object facesIdentity,
                PcbBoardLayout layout,PcbPadPlacement start,
                PcbPadPlacement root,String netId,int pass,int width,int height,int cells,
                int originX,int originY,int pitch,int endLayer,int endCell,
                PcbLayerRoutingPrototype.Policy policy,int primaryLayer,int initialLayerMask,
                int maximumVias,PcbBoardHole[] prefixHoles,String[] reachedPadIds) {
            this.search=search; this.branch=branch; this.publisher=publisher; this.endNode=endNode;
            this.foundTerminal=foundTerminal; this.goalIdentity=goalIdentity;
            this.reachedIdentity=reachedIdentity; this.facesIdentity=facesIdentity;
            this.faceValues=((PcbNetRouter.Router[])facesIdentity).clone();
            this.holesIdentity=P07ScalarReflection.read(branch,"holes");
            this.layout=layout; this.start=start; this.root=root;
            this.reachedValues=reachedIdentity.clone(); this.goalValues=goalIdentity.clone();
            this.prefixHoles=prefixHoles.clone();
            this.netId=netId; this.reachedPadIds=reachedPadIds.clone();
            this.pass=pass; this.width=width; this.height=height; this.cells=cells;
            this.originX=originX; this.originY=originY; this.pitch=pitch;
            this.endCell=endCell; this.endLayer=endLayer;
            this.policy=policy; this.primaryLayer=primaryLayer;
            this.initialLayerMask=initialLayerMask; this.maximumVias=maximumVias;
            this.prefixHoleCount=prefixHoles.length;
        }
    }

    private static final class PrefixAllocationBinding {
        final Object search,netVectorIdentity,branch;
        final String[] netValues;
        final int searchNetIndex,currentNetPosition,currentBranchOrdinal,pass,
            maximumVias,templateHoleCount;
        final String currentNet;
        final PcbBoardHole[] prefixHoles;
        final String[] holeIds,holeFingerprints;
        final PcbBoardLayout layout,template;
        final String prefixGeometryFingerprint;
        PrefixAllocationBinding(Object search,Vector<?> netVector,String[] netValues,
                int searchNetIndex,String currentNet,int currentBranchOrdinal,int pass,
                int maximumVias,Object branch,PcbBoardHole[] prefixHoles,
                PcbBoardLayout layout,PcbBoardLayout template) {
            this.search=search;
            this.netVectorIdentity=netVector;
            this.netValues=netValues.clone();
            this.searchNetIndex=searchNetIndex;
            this.currentNetPosition=searchNetIndex-1;
            this.currentNet=currentNet;
            this.currentBranchOrdinal=currentBranchOrdinal;
            this.pass=pass;
            this.maximumVias=maximumVias;
            this.branch=branch;
            this.prefixHoles=prefixHoles;
            this.layout=layout;
            this.template=template;
            this.templateHoleCount=template.getHoleCount();
            this.prefixGeometryFingerprint=layout.geometryFingerprint();
            this.holeIds=new String[prefixHoles.length];
            this.holeFingerprints=new String[prefixHoles.length];
            for(int index=0;index<prefixHoles.length;index++) {
                PcbBoardHole hole=prefixHoles[index];
                this.holeIds[index]=hole.id;
                this.holeFingerprints[index]=hole.fingerprint();
            }
        }
    }

    private static final class Load12BranchBinding {
        final Object search,netVectorIdentity,branch;
        final String[] netOrder,prefixHoleIds,prefixHoleFingerprints;
        final int searchNetIndex,currentNetPosition,branchOrdinal,searchBranches,
            pass,primaryLayer,maximumVias;
        final String currentNet,prefixGeometryFingerprint;
        final PcbLayerRoutingPrototype.Policy policy;
        final PcbBoardLayout layout,template;
        final PcbBoardHole[] prefixHoleArrayIdentity;
        final PcbBoardHole[] prefixHoles;
        final PcbPadPlacement start,root;
        Load12BranchBinding(Object search,Object branch,Vector<?> netVector,
                String[] netOrder,int searchNetIndex,String currentNet,int branchOrdinal,
                int searchBranches,int pass,PcbLayerRoutingPrototype.Policy policy,
                int primaryLayer,int maximumVias,PcbBoardLayout layout,
                PcbBoardLayout template,PcbBoardHole[] prefixHoles,
                PcbPadPlacement start,PcbPadPlacement root) {
            this.search=search;
            this.branch=branch;
            this.netVectorIdentity=netVector;
            this.netOrder=netOrder.clone();
            this.searchNetIndex=searchNetIndex;
            this.currentNetPosition=searchNetIndex-1;
            this.currentNet=currentNet;
            this.branchOrdinal=branchOrdinal;
            this.searchBranches=searchBranches;
            this.pass=pass;
            this.policy=policy;
            this.primaryLayer=primaryLayer;
            this.maximumVias=maximumVias;
            this.layout=layout;
            this.template=template;
            this.prefixHoleArrayIdentity=prefixHoles;
            this.prefixHoles=prefixHoles.clone();
            this.start=start;
            this.root=root;
            this.prefixGeometryFingerprint=layout.geometryFingerprint();
            this.prefixHoleIds=new String[prefixHoles.length];
            this.prefixHoleFingerprints=new String[prefixHoles.length];
            for(int index=0;index<prefixHoles.length;index++) {
                this.prefixHoleIds[index]=prefixHoles[index].id;
                this.prefixHoleFingerprints[index]=prefixHoles[index].fingerprint();
            }
        }
    }

    private static final class P07ScalarReflection {
        private static final Map<String,Field> FIELDS=new HashMap<String,Field>();
        private static final Map<String,Method> METHODS=new HashMap<String,Method>();
        private static Constructor<?> nodeConstructor,publisherConstructor;
        private static Class<?> nodeClass,publisherClass;

        static Object read(Object target,String name) {
            if(target==null) throw new AssertionError("Cannot read "+name+" from a null actual owner");
            String key=target.getClass().getName()+"#"+name;
            Field field=FIELDS.get(key);
            if(field==null) {
                Class<?> current=target.getClass();
                while(current!=null && field==null) {
                    try { field=current.getDeclaredField(name); }
                    catch(NoSuchFieldException absent) { current=current.getSuperclass(); }
                }
                if(field==null) throw new AssertionError("Missing reflected actual field "+key);
                field.setAccessible(true);
                FIELDS.put(key,field);
            }
            try { return field.get(target); }
            catch(IllegalAccessException failure) {
                throw new AssertionError("Cannot read actual field "+key,failure);
            }
        }

        static int intField(Object target,String name) {
            Object value=read(target,name);
            if(!(value instanceof Integer)) throw new AssertionError("Actual field is not int: "+name);
            return ((Integer)value).intValue();
        }

        static String stringField(Object target,String name) {
            Object value=read(target,name);
            if(value!=null && !(value instanceof String))
                throw new AssertionError("Actual field is not String: "+name);
            return (String)value;
        }

        static Object invoke(Object target,String name,Class<?>[] parameterTypes,Object... args) {
            if(target==null) throw new AssertionError("Cannot invoke "+name+" on a null actual owner");
            StringBuilder signature=new StringBuilder(target.getClass().getName()).append('#').append(name);
            for(Class<?> parameter:parameterTypes) signature.append(':').append(parameter.getName());
            String key=signature.toString();
            Method method=METHODS.get(key);
            if(method==null) {
                try { method=target.getClass().getDeclaredMethod(name,parameterTypes); }
                catch(NoSuchMethodException failure) {
                    throw new AssertionError("Missing reflected actual method "+key,failure);
                }
                method.setAccessible(true);
                METHODS.put(key,method);
            }
            try { return method.invoke(target,args); }
            catch(InvocationTargetException failure) { throw propagate(failure.getCause()); }
            catch(ReflectiveOperationException failure) {
                throw new AssertionError("Cannot invoke actual method "+key,failure);
            }
        }

        static int searchCoordinate(Object search,String method,int cell) {
            return ((Integer)invoke(search,method,new Class<?>[]{Integer.TYPE},Integer.valueOf(cell))).intValue();
        }

        static int searchCell(Object search,PcbPadPlacement pad) {
            return ((Integer)invoke(search,"cell",new Class<?>[]{PcbPadPlacement.class},pad)).intValue();
        }

        static Object newNode(Object search,int cell,int layer,int direction,int transitions,
                int serial,Object previous) {
            ensureConstructors(search);
            try {
                int cells=intField(search,"cells");
                return nodeConstructor.newInstance(Integer.valueOf(cell),Integer.valueOf(layer),
                    Integer.valueOf(direction),Integer.valueOf(transitions),Integer.valueOf(serial*10),
                    Integer.valueOf(0),Integer.valueOf(serial),Integer.valueOf(cells),previous);
            } catch(InvocationTargetException failure) { throw propagate(failure.getCause()); }
            catch(ReflectiveOperationException failure) {
                throw new AssertionError("Cannot create isolated synthetic P07 node",failure);
            }
        }

        static Object newPublisher(Object search,String net,PcbPadPlacement start,
                PcbPadPlacement root,Object end,PcbPadPlacement endTerminal) {
            ensureConstructors(search);
            try { return publisherConstructor.newInstance(search,net,start,root,end,endTerminal); }
            catch(InvocationTargetException failure) { throw propagate(failure.getCause()); }
            catch(ReflectiveOperationException failure) {
                throw new AssertionError("Cannot create isolated synthetic P07 Publisher",failure);
            }
        }

        static void advancePublisher(Object publisher) {
            invoke(publisher,"advanceOne",new Class<?>[0]);
        }

        private static void ensureConstructors(Object search) {
            if(nodeConstructor!=null && publisherConstructor!=null) return;
            try {
                for(Class<?> nested:PcbLayerRoutingPrototype.class.getDeclaredClasses())
                    if("Node".equals(nested.getSimpleName())) nodeClass=nested;
                for(Class<?> nested:search.getClass().getDeclaredClasses())
                    if("Publisher".equals(nested.getSimpleName())) publisherClass=nested;
                if(nodeClass==null || publisherClass==null)
                    throw new AssertionError("Actual private P07 Node/Publisher classes are missing");
                nodeConstructor=nodeClass.getDeclaredConstructor(Integer.TYPE,Integer.TYPE,
                    Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,nodeClass);
                publisherConstructor=publisherClass.getDeclaredConstructor(search.getClass(),
                    String.class,PcbPadPlacement.class,PcbPadPlacement.class,nodeClass,
                    PcbPadPlacement.class);
                nodeConstructor.setAccessible(true);
                publisherConstructor.setAccessible(true);
            } catch(ReflectiveOperationException failure) {
                throw new AssertionError("Actual P07 scalar constructor contract changed",failure);
            }
        }

        private static RuntimeException propagate(Throwable failure) {
            if(failure instanceof RuntimeException) return (RuntimeException)failure;
            if(failure instanceof Error) throw (Error)failure;
            return new IllegalStateException("Reflected P07 operation failed",failure);
        }
    }

    private static QualityScalarSnapshot captureActualQualityScalars(Object search,Object publisher,
            PcbLayerRoutingPrototype.FixedPrefixCaptureContext context) {
        Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
        require(reversed!=null && !reversed.isEmpty(),
            "actual quiescent Publisher retains its complete ordered path nodes");
        Object firstNode=reversed.get(reversed.size()-1);
        Object endNode=P07ScalarReflection.read(publisher,"end");
        PcbPadPlacement start=(PcbPadPlacement)P07ScalarReflection.read(publisher,"start");
        PcbPadPlacement root=(PcbPadPlacement)P07ScalarReflection.read(publisher,"root");
        require(start!=null && root!=null && endNode!=null,
            "actual Publisher binds the starting pad, original tree root and found goal node");
        int startCell=P07ScalarReflection.searchCell(search,start);
        int startX=start.getX(),startY=start.getY();
        require(P07ScalarReflection.searchCoordinate(search,"x",startCell)==startX &&
            P07ScalarReflection.searchCoordinate(search,"y",startCell)==startY,
            "actual Publisher start pad maps to the exact Search grid cell without rounding");
        int endCell=P07ScalarReflection.intField(endNode,"cell");
        int endX=P07ScalarReflection.searchCoordinate(search,"x",endCell);
        int endY=P07ScalarReflection.searchCoordinate(search,"y",endCell);
        int direct=Math.abs(startX-endX)+Math.abs(startY-endY);
        int length=P07ScalarReflection.intField(publisher,"length");
        int bends=P07ScalarReflection.intField(publisher,"bends");
        int transitions=P07ScalarReflection.intField(endNode,"transitions");
        int layerChanges=0;
        for(int index=reversed.size()-1;index>0;index--) {
            int before=P07ScalarReflection.intField(reversed.get(index),"layer");
            int after=P07ScalarReflection.intField(reversed.get(index-1),"layer");
            if(before!=after) layerChanges++;
        }
        int gridWidth=P07ScalarReflection.intField(search,"width");
        int gridHeight=P07ScalarReflection.intField(search,"height");
        int gridOriginX=P07ScalarReflection.intField(search,"minX");
        int gridOriginY=P07ScalarReflection.intField(search,"minY");
        int gridPitch=PcbLayerRoutingPrototype.GRID;
        int firstLayer=P07ScalarReflection.intField(firstNode,"layer");
        int endLayer=P07ScalarReflection.intField(endNode,"layer");
        int primaryLayer=P07ScalarReflection.intField(search,"primary");
        require(transitions==layerChanges && transitions>=0 && transitions<reversed.size(),
            "actual end-node transition count conserves every actual path layer change");
        require(length==(reversed.size()-1-transitions)*gridPitch,
            "actual Publisher planar length conserves same-layer path edges at GRID10");
        require(firstLayer>=0 && firstLayer<2 &&
            PcbCopperAccess.hasCopper(start,PcbCopperLayer.values()[firstLayer]) &&
            direct>=0 && gridWidth>0 && gridHeight>0,
            "actual reversed path starts on a copper face available at the actual start pad and remains grid bounded");
        require(context.rawPlacementFingerprint()!=null &&
            context.rawPlacementFingerprint().length()>0,
            "actual metric observation remains bound to the actual request placement fingerprint");
        return new QualityScalarSnapshot(P07ScalarReflection.intField(search,"pass"),
            P07ScalarReflection.stringField(publisher,"net"),start.getPadId(),root.getPadId(),
            startX,startY,endX,endY,endLayer,primaryLayer,firstLayer,gridWidth,gridHeight,
            gridOriginX,gridOriginY,gridPitch,reversed.size(),transitions,length,direct,bends);
    }

    private static QualityPathSnapshot captureActualQualityPath(Object owner,Object search,
            Object publisher,PcbLayerRoutingPrototype.FixedPrefixCaptureContext context,
            QualityScalarSnapshot scalar) {
        Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
        int captureStage=P07ScalarReflection.intField(publisher,"stage");
        int captureQualityIndex=P07ScalarReflection.intField(publisher,"qualityIndex");
        int width=P07ScalarReflection.intField(search,"width");
        int height=P07ScalarReflection.intField(search,"height");
        int cells=P07ScalarReflection.intField(search,"cells");
        int originX=P07ScalarReflection.intField(search,"minX");
        int originY=P07ScalarReflection.intField(search,"minY");
        int pitch=PcbLayerRoutingPrototype.GRID;
        PcbPadPlacement start=(PcbPadPlacement)P07ScalarReflection.read(publisher,"start");
        PcbPadPlacement root=(PcbPadPlacement)P07ScalarReflection.read(publisher,"root");
        Object endNode=P07ScalarReflection.read(publisher,"end");
        require(reversed!=null && captureStage==1 && captureQualityIndex==reversed.size() &&
            reversed.size()==66 && scalar.pathNodeCount==66 && width>0 && height>0 &&
            (long)width*height==cells && cells<=PcbLayerRoutingPrototype.MAX_CELLS,
            "actual quiescent D_SENSE Publisher path is bounded at the frozen66-node stage1 quality index");
        require(owner!=null && search!=null && publisher!=null && context!=null &&
            P07ScalarReflection.read(search,"owner")==owner &&
            P07ScalarReflection.read(search,"publisher")==publisher &&
            P07ScalarReflection.read(owner,"search")==search &&
            P07ScalarReflection.read(owner,"fixedPrefixContext")==context &&
            start!=null && root!=null && endNode!=null &&
            start.getPadId().equals(scalar.startPadId) && root.getPadId().equals(scalar.rootPadId),
            "captured path remains attached to the same actual Search, Publisher, pads and request context");

        Object[] orderedIdentities=new Object[reversed.size()];
        QualityPathNodeSnapshot[] nodes=new QualityPathNodeSnapshot[reversed.size()];
        IdentityHashMap<Object,Boolean> unique=new IdentityHashMap<Object,Boolean>();
        int startCell=P07ScalarReflection.searchCell(search,start);
        int startX=start.getX(),startY=start.getY();
        int endCell=P07ScalarReflection.intField(endNode,"cell");
        int endX=P07ScalarReflection.searchCoordinate(search,"x",endCell);
        int endY=P07ScalarReflection.searchCoordinate(search,"y",endCell);
        int endLayer=P07ScalarReflection.intField(endNode,"layer");
        int computedLength=0,computedTransitions=0,computedBends=0,lastDirection=4;
        for(int index=0;index<reversed.size();index++) {
            Object node=reversed.get(reversed.size()-1-index);
            require(node!=null && unique.put(node,Boolean.TRUE)==null,
                "actual reversed Publisher chain contains unique non-null Node objects");
            orderedIdentities[index]=node;
            Object expectedParent=index==0?null:orderedIdentities[index-1];
            require(P07ScalarReflection.read(node,"previous")==expectedParent,
                "actual Node.previous object identity matches its start-to-end parent index");
            int cell=P07ScalarReflection.intField(node,"cell");
            int x=P07ScalarReflection.searchCoordinate(search,"x",cell);
            int y=P07ScalarReflection.searchCoordinate(search,"y",cell);
            int layer=P07ScalarReflection.intField(node,"layer");
            int direction=P07ScalarReflection.intField(node,"direction");
            int transitions=P07ScalarReflection.intField(node,"transitions");
            int cost=P07ScalarReflection.intField(node,"cost");
            int heuristic=P07ScalarReflection.intField(node,"heuristic");
            int serial=P07ScalarReflection.intField(node,"serial");
            int key=P07ScalarReflection.intField(node,"key");
            long expectedKey=(((long)transitions*2L+layer)*5L+direction)*cells+cell;
            require(cell>=0 && cell<cells && x==originX+(cell%width)*pitch &&
                y==originY+(cell/width)*pitch && x==P07ScalarReflection.searchCoordinate(search,"x",cell) &&
                y==P07ScalarReflection.searchCoordinate(search,"y",cell) && layer>=0 && layer<2 &&
                direction>=0 && direction<=4 && expectedKey==key,
                "every captured actual Node coordinate and state key maps exactly to the Search grid");
            if(index==0) {
                require(P07ScalarReflection.read(node,"previous")==null && cell==startCell &&
                    x==startX && y==startY,
                    "first actual path Node is the start-pad grid state with no parent object");
            } else {
                QualityPathNodeSnapshot previous=nodes[index-1];
                if(previous.layer!=layer) {
                    computedTransitions++;
                    lastDirection=4;
                } else {
                    computedLength+=pitch;
                    if(lastDirection!=4 && direction!=lastDirection) computedBends++;
                    lastDirection=direction;
                }
                require(transitions==computedTransitions,
                    "each actual path Node carries the accumulated physical layer-transition count");
            }
            nodes[index]=new QualityPathNodeSnapshot(index,index-1,cell,x,y,layer,direction,
                transitions,cost,heuristic,serial,key);
        }
        require(orderedIdentities[0]!=null && orderedIdentities[orderedIdentities.length-1]==endNode &&
            nodes[nodes.length-1].cell==endCell && nodes[nodes.length-1].x==endX &&
            nodes[nodes.length-1].y==endY && nodes[nodes.length-1].layer==endLayer &&
            endX==scalar.endX && endY==scalar.endY && endLayer==scalar.endLayer &&
            computedTransitions==scalar.transitionCount &&
            computedLength==scalar.planarLength && computedBends==scalar.bends,
            "last actual path Node retains the exact Publisher end object and captured scalar geometry");
        QualityPathSnapshot snapshot=new QualityPathSnapshot(captureStage,captureQualityIndex,
            reversed,orderedIdentities,nodes);
        verifyQualityPathSourceUnchanged(owner,search,publisher,context,start,root,endNode,snapshot);
        return snapshot;
    }

    private static void verifyQualityPathSourceUnchanged(Object owner,Object search,Object publisher,
            Object context,Object start,Object root,Object endNode,QualityPathSnapshot snapshot) {
        require(owner!=null && search!=null && publisher!=null && context!=null && snapshot!=null &&
            P07ScalarReflection.read(search,"owner")==owner &&
            P07ScalarReflection.read(search,"publisher")==publisher &&
            P07ScalarReflection.read(owner,"fixedPrefixContext")==context &&
            P07ScalarReflection.read(publisher,"start")==start &&
            P07ScalarReflection.read(publisher,"root")==root &&
            P07ScalarReflection.read(publisher,"end")==endNode,
            "same-owner rejection retains the exact actual Search, Publisher, endpoints and context");
        Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
        require(reversed==snapshot.sourceReversedIdentity && reversed!=null &&
            reversed.size()==snapshot.nodes.length && snapshot.sourceNodeIdentities.length==snapshot.nodes.length &&
            P07ScalarReflection.intField(publisher,"stage")==snapshot.captureStage &&
            P07ScalarReflection.intField(publisher,"qualityIndex")==snapshot.captureQualityIndex,
            "actual Publisher vector and quiescent capture counters remain unchanged through rejection");
        for(int index=0;index<snapshot.nodes.length;index++) {
            QualityPathNodeSnapshot row=snapshot.nodes[index];
            Object node=snapshot.sourceNodeIdentities[index];
            require(reversed.get(reversed.size()-1-index)==node && node!=null &&
                P07ScalarReflection.intField(node,"cell")==row.cell &&
                P07ScalarReflection.intField(node,"layer")==row.layer &&
                P07ScalarReflection.intField(node,"direction")==row.direction &&
                P07ScalarReflection.intField(node,"transitions")==row.transitions &&
                P07ScalarReflection.intField(node,"cost")==row.cost &&
                P07ScalarReflection.intField(node,"heuristic")==row.heuristic &&
                P07ScalarReflection.intField(node,"serial")==row.serial &&
                P07ScalarReflection.intField(node,"key")==row.key &&
                P07ScalarReflection.read(node,"previous")==
                    (index==0?null:snapshot.sourceNodeIdentities[index-1]),
                "primitive path snapshot matches each unchanged actual Publisher Node and parent identity");
        }
    }

    private static String qualityPathSummaryJson(String runId,QualityPathSnapshot snapshot) {
        require(runId!=null && snapshot!=null && snapshot.nodes.length==66 &&
            snapshot.captureStage==1 && snapshot.captureQualityIndex==66,
            "quality path summary binds the single frozen stage1 66-node capture");
        return "PASS: RB100 quality path contracts {\"protocolVersion\":1,\"sourceRevision\":18,"+
            "\"status\":\"PASS\",\"runId\":\""+jsonFull(runId)+
            "\",\"operationId\":\"QUALITY_PROBE\",\"events\":1,\"nodes\":66,"+
            "\"scope\":\"ACTUAL_PUBLISHER_PATH_ONLY\",\"feasibility\":\"UNKNOWN\","+
            "\"legalShortcutProof\":false,\"layoutQualification\":false}";
    }

    private static String runQualityScalarPublisherControls(Object search,String runId) {
        int beforeAssertions=assertions;
        int width=P07ScalarReflection.intField(search,"width");
        int height=P07ScalarReflection.intField(search,"height");
        PcbBoardLayout layout=(PcbBoardLayout)P07ScalarReflection.read(search,"layout");
        require(width>20 && height>20 && layout!=null,
            "literal Publisher controls use the actual admitted P07 Search grid and private layout");
        PcbPadPlacement anchor=null;
        int anchorCell=-1;
        for(PcbPadPlacement pad:layout.getPads()) {
            int cell=P07ScalarReflection.searchCell(search,pad);
            int gx=cell%width,gy=cell/width;
            if(gx>=10 && gx+10<width && gy>=10 && gy+10<height) {
                anchor=pad; anchorCell=cell; break;
            }
        }
        require(anchor!=null,"literal scalar controls find an actual placed pad with ten-cell grid clearance");
        ArrayList<QualityControlResult> rows=new ArrayList<QualityControlResult>();
        rows.add(runQualityControl(search,anchor,anchorCell,"direct_zero_rejected","",
            0,0,0,1,0,1,true));
        rows.add(runQualityControl(search,anchor,anchorCell,"length_over_3x_rejected","N3,E2,S3",
            80,20,2,9,0,1,true));
        rows.add(runQualityControl(search,anchor,anchorCell,"bends_over_16_rejected",
            "(E1,N1)x9",180,180,17,19,0,1,true));
        rows.add(runQualityControl(search,anchor,anchorCell,"exact_3x_16_accepted",
            "E6,N1,W6,N1,E1,N1,W1,N1,E1,N1,W1,N1,E1,N1,W1,N1,E1",
            270,90,16,28,0,2,false));
        rows.add(runQualityControl(search,anchor,anchorCell,"via_direction_reset","E1,V1,N1,E1",
            30,30,1,5,1,2,false));
        StringBuilder checks=new StringBuilder("[");
        for(int index=0;index<rows.size();index++) {
            if(index>0) checks.append(',');
            QualityControlResult row=rows.get(index);
            checks.append("{\"name\":\"").append(row.name).append("\",\"status\":\"PASS\"")
                .append(",\"planarLength\":").append(row.planarLength)
                .append(",\"directDistance\":").append(row.directDistance)
                .append(",\"bends\":").append(row.bends)
                .append(",\"pathNodeCount\":").append(row.pathNodeCount)
                .append(",\"transitionCount\":").append(row.transitionCount)
                .append(",\"publisherStage\":").append(row.publisherStage).append('}');
        }
        checks.append(']');
        return "RB100_QUALITY_SCALAR_CONTROLS {\"protocolVersion\":1,\"sourceRevision\":18,"+
            "\"runId\":\""+jsonFull(runId)+"\",\"status\":\"PASS\","+
            "\"fixture\":\"SYNTHETIC_PUBLISHER_METRICS_ONLY\",\"checks\":"+checks+
            ",\"assertions\":"+(assertions-beforeAssertions)+",\"layoutQualification\":false}";
    }

    private static QualityControlResult runQualityControl(Object search,PcbPadPlacement anchor,
            int anchorCell,String name,String specification,int expectedLength,int expectedDirect,
            int expectedBends,int expectedNodes,int expectedTransitions,int expectedStage,
            boolean expectRejected) {
        char[] steps=expandQualityControl(specification);
        int width=P07ScalarReflection.intField(search,"width");
        int height=P07ScalarReflection.intField(search,"height");
        int gx=anchorCell%width,gy=anchorCell/width;
        ArrayList<Integer> cells=new ArrayList<Integer>();
        ArrayList<Integer> layers=new ArrayList<Integer>();
        ArrayList<Integer> directions=new ArrayList<Integer>();
        ArrayList<Integer> transitions=new ArrayList<Integer>();
        int layer=P07ScalarReflection.intField(search,"primary"),transitionCount=0;
        cells.add(Integer.valueOf(anchorCell)); layers.add(Integer.valueOf(layer));
        directions.add(Integer.valueOf(4)); transitions.add(Integer.valueOf(0));
        for(char step:steps) {
            if(step=='V') { layer=1-layer; transitionCount++; }
            else if(step=='E') gx++;
            else if(step=='W') gx--;
            else if(step=='N') gy--;
            else if(step=='S') gy++;
            else throw new AssertionError("Unknown literal scalar-control direction");
            require(gx>=0 && gx<width && gy>=0 && gy<height,
                "literal synthetic Publisher chain remains inside its actual Search grid");
            cells.add(Integer.valueOf(gy*width+gx));
            layers.add(Integer.valueOf(layer));
            directions.add(Integer.valueOf(step=='E'?0:step=='N'?1:step=='W'?2:step=='S'?3:4));
            transitions.add(Integer.valueOf(transitionCount));
        }
        Object previous=null;
        for(int index=0;index<cells.size();index++)
            previous=P07ScalarReflection.newNode(search,cells.get(index).intValue(),
                layers.get(index).intValue(),directions.get(index).intValue(),
                transitions.get(index).intValue(),index,previous);
        PcbPadPlacement root=anchor;
        Object publisher=P07ScalarReflection.newPublisher(search,"CONTROL_"+name,
            anchor,root,previous,null);
        boolean rejected=false;
        String rejection=null;
        for(int work=0;work<steps.length+cells.size()+8;work++) {
            int stage=P07ScalarReflection.intField(publisher,"stage");
            if(stage==2) break;
            try { P07ScalarReflection.advancePublisher(publisher); }
            catch(RuntimeException failure) {
                if("Exhausted".equals(failure.getClass().getSimpleName()) &&
                        failure.getMessage()!=null && failure.getMessage().endsWith(":BRANCH_QUALITY_LIMIT")) {
                    rejected=true; rejection=failure.getMessage(); break;
                }
                throw failure;
            }
        }
        Vector<?> reversed=(Vector<?>)P07ScalarReflection.read(publisher,"reversed");
        Object end=P07ScalarReflection.read(publisher,"end");
        int length=P07ScalarReflection.intField(publisher,"length");
        int bends=P07ScalarReflection.intField(publisher,"bends");
        int direct=Math.abs(anchor.getX()-P07ScalarReflection.searchCoordinate(search,"x",
            P07ScalarReflection.intField(end,"cell")))+
            Math.abs(anchor.getY()-P07ScalarReflection.searchCoordinate(search,"y",
                P07ScalarReflection.intField(end,"cell")));
        int endTransitions=P07ScalarReflection.intField(end,"transitions");
        int stage=P07ScalarReflection.intField(publisher,"stage");
        require(rejected==expectRejected && length==expectedLength && direct==expectedDirect &&
            bends==expectedBends && reversed.size()==expectedNodes &&
            endTransitions==expectedTransitions && stage==expectedStage,
            "actual Publisher control matches its frozen literal scalar/readiness row: "+name+
            (rejection==null?"":" "+rejection));
        if(expectRejected) require(stage==1 && rejected,
            "literal rejected Publisher control stops at its pre-publication quality guard");
        else require(!rejected && stage==2,
            "literal accepted Publisher control proves readiness only and never publishes geometry");
        return new QualityControlResult(name,length,direct,bends,reversed.size(),
            endTransitions,stage);
    }

    private static char[] expandQualityControl(String specification) {
        if(specification==null || specification.length()==0) return new char[0];
        ArrayList<Character> result=new ArrayList<Character>();
        if("(E1,N1)x9".equals(specification)) {
            for(int repeat=0;repeat<9;repeat++) { result.add(Character.valueOf('E')); result.add(Character.valueOf('N')); }
        } else {
            String[] tokens=specification.split(",");
            for(String token:tokens) {
                if(token.length()<2) throw new AssertionError("Malformed literal scalar-control segment");
                char direction=token.charAt(0);
                int count=Integer.parseInt(token.substring(1));
                for(int index=0;index<count;index++) result.add(Character.valueOf(direction));
            }
        }
        char[] values=new char[result.size()];
        for(int index=0;index<values.length;index++) values[index]=result.get(index).charValue();
        return values;
    }

    private static final class QualityControlResult {
        final String name;
        final int planarLength,directDistance,bends,pathNodeCount,transitionCount,publisherStage;
        QualityControlResult(String name,int planarLength,int directDistance,int bends,
                int pathNodeCount,int transitionCount,int publisherStage) {
            this.name=name; this.planarLength=planarLength; this.directDistance=directDistance;
            this.bends=bends; this.pathNodeCount=pathNodeCount;
            this.transitionCount=transitionCount; this.publisherStage=publisherStage;
        }
    }

    private static int fixedPrefixCell(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph,
            int x,int y) {
        int gx=(x-graph.originX())/graph.gridPitch();
        int gy=(y-graph.originY())/graph.gridPitch();
        require(gx>=0 && gx<graph.gridWidth() && gy>=0 && gy<graph.gridHeight(),
            "captured lineage coordinates map into the copied graph rectangle");
        return gy*graph.gridWidth()+gx;
    }

    private static class PlacementDiagnosticCase {
        final String fixture, subject;
        final long seed;
        final int candidate;
        String phase = "START", status = "FAIL";
        String nullOutcome = "NOT_RUN", observedOutcome = "NOT_RUN", retryOutcome = "NOT_RUN";
        String nullGeometry, observedGeometry, retryGeometry;
        String failurePhase, reportFactsJson;
        Throwable failure, cleanupFailure;
        PcbPlacementPlanner.PlacementFailureSnapshot snapshot;
        EnumMap<PcbPlacementPlanner.FitOutcome, Long> fitCounts =
            new EnumMap<PcbPlacementPlanner.FitOutcome, Long>(PcbPlacementPlanner.FitOutcome.class);
        long firstFitCount, operationMillis, cleanupMillis, maxUnitMillis;
        int workUnits, components, pads, nets, callbackAbortCallbacks;
        boolean cleanupPassed, callbackExceptionPropagated;
        PlacementDiagnosticCase(String fixture, String subject, long seed, int candidate) {
            this.fixture = fixture; this.subject = subject;
            this.seed = seed; this.candidate = candidate;
            for (PcbPlacementPlanner.FitOutcome outcome : PcbPlacementPlanner.FitOutcome.values())
                fitCounts.put(outcome, 0L);
        }
        String toJson() {
            return "{\"status\":\"" + status + "\",\"facts\":" +
                (reportFactsJson == null ? "null" : reportFactsJson) +
                ",\"phase\":\"" + json(phase) +
                "\",\"operationMs\":" + operationMillis + ",\"cleanupMs\":" + cleanupMillis +
                ",\"cleanupStatus\":\"" + (cleanupPassed ? "PASS" : "FAIL") +
                "\",\"workUnits\":" + workUnits + ",\"maxUnitMs\":" + maxUnitMillis +
                ",\"failurePhase\":\"" + json(failurePhase) +
                "\",\"failureType\":\"" + json(typedFailure(failure)) +
                "\",\"failure\":\"" + json(failure == null ? "" : failure.toString()) +
                "\",\"cleanupFailureType\":\"" + json(typedFailure(cleanupFailure)) +
                "\",\"cleanupFailure\":\"" +
                json(cleanupFailure == null ? "" : cleanupFailure.toString()) +
                "\",\"scope\":\"CURRENT_PLACEMENT_DIAGNOSTICS_ONLY\"}";
        }
        void prepareFacts() {
            reportFactsJson = "{\"fixture\":\"" + json(fixture) + "\",\"subject\":\"" +
                json(subject) + "\",\"seed\":\"" + seed + "\",\"candidate\":" + candidate +
                ",\"components\":" + components + ",\"pads\":" + pads + ",\"nets\":" + nets +
                ",\"nullOutcome\":\"" + json(nullOutcome) +
                "\",\"retryOutcome\":\"" + json(retryOutcome) +
                "\",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" +
                json(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
                ",\"observedOutcome\":\"" + json(observedOutcome) +
                "\",\"nullGeometryKey\":\"" + geometryKey(nullGeometry) +
                "\",\"observedGeometryKey\":\"" + geometryKey(observedGeometry) +
                "\",\"retryGeometryKey\":\"" + geometryKey(retryGeometry) +
                "\",\"firstFitOutcomeCount\":" + firstFitCount +
                ",\"firstFitOutcomes\":" + fitCountsJson() +
                ",\"failureSnapshot\":" + snapshotJson() +
                ",\"callbackExceptionPropagated\":" + callbackExceptionPropagated +
                ",\"callbackAbortCallbacks\":" + callbackAbortCallbacks +
                "}";
        }
        private String geometryKey(String geometry) {
            return geometry == null ? "NOT_AVAILABLE" : json(key(geometry));
        }
        private String fitCountsJson() {
            return "{\"outline\":" + count(PcbPlacementPlanner.FitOutcome.OUTLINE) +
                ",\"accessEnvelopeOverlap\":" +
                count(PcbPlacementPlanner.FitOutcome.ACCESS_ENVELOPE_OVERLAP) +
                ",\"interDomainBarrier\":" +
                count(PcbPlacementPlanner.FitOutcome.INTER_DOMAIN_BARRIER) +
                ",\"globalCorridor\":" +
                count(PcbPlacementPlanner.FitOutcome.GLOBAL_CORRIDOR) +
                ",\"accepted\":" + count(PcbPlacementPlanner.FitOutcome.ACCEPTED) + "}";
        }
        private long count(PcbPlacementPlanner.FitOutcome outcome) {
            Long value = fitCounts.get(outcome);
            return value == null ? 0 : value.longValue();
        }
        private String snapshotJson() {
            if (snapshot == null) return "null";
            return "{\"componentId\":\"" + json(snapshot.getComponentId()) +
                "\",\"domainId\":\"" + json(snapshot.getDomainId()) +
                "\",\"regionId\":\"" + json(snapshot.getRegionId()) +
                "\",\"outline\":{\"x\":" + snapshot.getOutlineX() +
                ",\"y\":" + snapshot.getOutlineY() +
                ",\"width\":" + snapshot.getOutlineWidth() +
                ",\"height\":" + snapshot.getOutlineHeight() + "},\"gridBounds\":{\"minOriginX\":" +
                snapshot.getGridMinOriginX() + ",\"maxOriginX\":" + snapshot.getGridMaxOriginX() +
                ",\"minOriginY\":" + snapshot.getGridMinOriginY() +
                ",\"maxOriginY\":" + snapshot.getGridMaxOriginY() +
                "},\"placedCount\":" + snapshot.getPlacedCount() +
                ",\"evaluationCount\":" + snapshot.getEvaluationCount() +
                ",\"planEvaluationCount\":" + snapshot.getPlanEvaluationCount() +
                ",\"firstFitCauseCounts\":{\"outline\":" + snapshot.getOutlineRejectCount() +
                ",\"accessEnvelopeOverlap\":" + snapshot.getAccessEnvelopeOverlapCount() +
                ",\"interDomainBarrier\":" + snapshot.getInterDomainBarrierCount() +
                ",\"globalCorridor\":" + snapshot.getGlobalCorridorCount() +
                ",\"accepted\":" + snapshot.getAcceptedCount() +
                "},\"firstGlobalCorridorWitness\":" +
                corridorWitnessJson(snapshot.getFirstGlobalCorridorWitness()) +
                ",\"firstRequiredOrientationPruneWitness\":" +
                corridorWitnessJson(snapshot.getFirstRequiredOrientationPruneWitness()) + "}";
        }
        private String corridorWitnessJson(
                PcbPlacementPlanner.CorridorWitnessSnapshot witness) {
            if (witness == null) return "null";
            return "{\"barrier\":{\"firstDomain\":\"" + json(witness.getBarrierFirstDomain()) +
                "\",\"secondDomain\":\"" + json(witness.getBarrierSecondDomain()) +
                "\",\"clearance\":" + witness.getBarrierClearance() +
                "},\"trial\":{\"componentId\":\"" + json(witness.getTrialComponentId()) +
                "\",\"geometryId\":\"" + json(witness.getTrialGeometryId()) +
                "\",\"geometryVariantKey\":\"" + json(witness.getTrialGeometryVariantKey()) +
                "\",\"geometryTransformKey\":\"" + json(witness.getTrialGeometryTransformKey()) +
                "\",\"x\":" + witness.getTrialX() + ",\"y\":" + witness.getTrialY() +
                ",\"rotation\":\"" + json(witness.getTrialRotation()) +
                "\",\"side\":\"" + json(witness.getTrialSide()) + "\"}," +
                "\"trialDomainCourtyards\":{\"first\":" +
                domainBoundsJson(witness.getTrialFirstCourtyard()) + ",\"second\":" +
                domainBoundsJson(witness.getTrialSecondCourtyard()) +
                "},\"preTrialAggregateBounds\":{\"first\":" +
                domainBoundsJson(witness.getPreTrialFirstDomainBounds()) + ",\"second\":" +
                domainBoundsJson(witness.getPreTrialSecondDomainBounds()) +
                "},\"postUnionAggregateBounds\":{\"first\":" +
                domainBoundsJson(witness.getPostUnionFirstDomainBounds()) + ",\"second\":" +
                domainBoundsJson(witness.getPostUnionSecondDomainBounds()) +
                "},\"signedMargins\":{\"meaning\":\"positive=clearance-shortfall; zero-or-negative=satisfies-orientation\"," +
                "\"firstToSecondXMargin\":" + witness.getFirstToSecondXMargin() +
                ",\"secondToFirstXMargin\":" + witness.getSecondToFirstXMargin() +
                ",\"firstToSecondYMargin\":" + witness.getFirstToSecondYMargin() +
                ",\"secondToFirstYMargin\":" + witness.getSecondToFirstYMargin() +
                "},\"orientationMasks\":{\"appliedAllowed\":" +
                witness.getAppliedAllowedOrientationMask() +
                ",\"appliedAllowedNames\":" + orientationMaskNamesJson(
                    witness.getAppliedAllowedOrientationMask()) +
                ",\"unconstrainedSeparating\":" +
                witness.getUnconstrainedSeparatingOrientationMask() +
                ",\"unconstrainedSeparatingNames\":" + orientationMaskNamesJson(
                    witness.getUnconstrainedSeparatingOrientationMask()) + "}}";
        }
        private String orientationMaskNamesJson(int mask) {
            StringBuilder names = new StringBuilder("[");
            String[] labels = new String[] { "firstLeftSecond", "secondLeftFirst",
                "firstAboveSecond", "secondAboveFirst" };
            int[] bits = new int[] { PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
                PcbPlacementPlanner.CORRIDOR_SECOND_LEFT_FIRST,
                PcbPlacementPlanner.CORRIDOR_FIRST_ABOVE_SECOND,
                PcbPlacementPlanner.CORRIDOR_SECOND_ABOVE_FIRST };
            boolean comma = false;
            for (int index = 0; index < bits.length; index++)
                if ((mask & bits[index]) != 0) {
                    if (comma) names.append(',');
                    names.append('"').append(labels[index]).append('"');
                    comma = true;
                }
            return names.append(']').toString();
        }
        private String domainBoundsJson(PcbPlacementPlanner.DomainBoundsSnapshot bounds) {
            if (bounds == null) return "null";
            return "{\"domainId\":\"" + json(bounds.getDomainId()) +
                "\",\"present\":" + bounds.isPresent() +
                ",\"x\":" + bounds.getX() + ",\"y\":" + bounds.getY() +
                ",\"width\":" + bounds.getWidth() + ",\"height\":" + bounds.getHeight() +
                ",\"extremaComponents\":{\"minX\":\"" + json(bounds.getMinXComponentId()) +
                "\",\"maxX\":\"" + json(bounds.getMaxXComponentId()) +
                "\",\"minY\":\"" + json(bounds.getMinYComponentId()) +
                "\",\"maxY\":\"" + json(bounds.getMaxYComponentId()) + "\"}}";
        }
    }

    private static final class RoutingInputMetadata {
        final String physicalPolicyId, routingLayer, traceWidthProfile;
        final int physicalPolicyVersion, placementDensityVersion, placementPackingVersion;
        final boolean requiresTwoLayerRouting;

        private RoutingInputMetadata(PcbPlacementConstraints constraints) {
            physicalPolicyId = constraints.getPhysicalPolicyId();
            physicalPolicyVersion = constraints.getPhysicalPolicyVersion();
            placementDensityVersion = constraints.placementDensityVersion;
            routingLayer = constraints.routingLayer.name();
            traceWidthProfile = constraints.getTraceWidthProfile().getIdentity();
            placementPackingVersion = constraints.getPlacementPackingVersion();
            requiresTwoLayerRouting = constraints.requiresTwoLayerRouting;
        }

        static RoutingInputMetadata capture(PcbPlacementConstraints constraints) {
            if (constraints == null)
                throw new IllegalArgumentException("Missing actual placement constraints");
            return new RoutingInputMetadata(constraints);
        }

        void requireCurrentP07Treatment() {
            require("MEDIUM_BOARD".equals(physicalPolicyId) &&
                physicalPolicyVersion == 1 && placementDensityVersion == 14 &&
                "BOTTOM".equals(routingLayer) &&
                "ROLE_TRACE@1".equals(traceWidthProfile) &&
                placementPackingVersion == 4 && requiresTwoLayerRouting,
                "actual intent9 input selects MEDIUM_BOARD@1/P07 with ROLE_TRACE@1 at density14 and packing4");
        }

        boolean sameAs(RoutingInputMetadata other) {
            return other != null && physicalPolicyId.equals(other.physicalPolicyId) &&
                physicalPolicyVersion == other.physicalPolicyVersion &&
                placementDensityVersion == other.placementDensityVersion &&
                routingLayer.equals(other.routingLayer) &&
                traceWidthProfile.equals(other.traceWidthProfile) &&
                placementPackingVersion == other.placementPackingVersion &&
                requiresTwoLayerRouting == other.requiresTwoLayerRouting;
        }
    }

    private static final class P07PolicyDiagnosticCase extends PlacementDiagnosticCase {
        final String runId;
        long placementSeed, routingSeed;
        int advanceCalls, observerChecks;
        String sessionTerminalStatus = "NOT_REACHED";
        String boardIdentityStatus = "NOT_REACHED";
        String solverGraphIdentityStatus = "NOT_REACHED";
        RoutingInputMetadata inputMetadata;
        TreeSet<String> boardNetIds;
        PcbRoutingRejectedException sessionRejection;
        MediumBoardPhysicalPolicy.Result policyResult;
        String layoutOutputJson = "null";
        String fixedPrefixMode,fixedPrefixOperationId,fixedPrefixGraphCaseId,fixedPrefixGraphSha256;
        String qualitySubsetMarkerJson;
        int fixedPrefixGraphBytes;
        long fixedPrefixSerializationMillis;
        FixedPrefixCaptureCollector fixedPrefixCollector;
        QualityScalarProbe qualityScalarProbe;
        boolean qualityScalarOperation;

        P07PolicyDiagnosticCase(String variant, long seed, String runId) {
            super("AUTHORED_RB100", variant, seed, -1);
            this.runId = runId;
        }

        void captureLayoutOutput() {
            if (policyResult == null) {
                layoutOutputJson = "null";
                return;
            }
            PcbBoardLayout layout = policyResult.getLayout();
            if (layout == null) {
                layoutOutputJson = "{\"layoutReturned\":false,\"zeroOutput\":true,\"fingerprint\":null," +
                    "\"outline\":null,\"rawSegments\":0,\"topTraceCount\":0," +
                    "\"bottomTraceCount\":0,\"totalTraceCount\":0,\"holeCount\":0," +
                    "\"viaCount\":0,\"platedHoleCount\":0,\"nonPlatedHoleCount\":0}";
                return;
            }
            String fingerprint = layout.geometryFingerprint();
            require(fingerprint != null && fingerprint.length() > 0 &&
                fingerprint.length() <= 1024 * 1024,
                "selected P07 output has a complete canonical geometry fingerprint within1MiB");
            Rectangle outline = layout.getBoardOutline();
            Vector<PcbTraceGeometry> traces = layout.getTraces();
            int topTraces = 0, bottomTraces = 0;
            for (PcbTraceGeometry trace : traces) {
                if (trace.getLayer() == PcbCopperLayer.TOP) topTraces++;
                else if (trace.getLayer() == PcbCopperLayer.BOTTOM) bottomTraces++;
                else throw new AssertionError("P07 output contains an unknown copper face");
            }
            Vector<PcbBoardHole> holes = layout.getHoles();
            int vias = 0, plated = 0, nonPlated = 0;
            for (PcbBoardHole hole : holes) {
                if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
                else if (hole.kind == PcbBoardHole.Kind.PLATED) plated++;
                else if (hole.kind == PcbBoardHole.Kind.NON_PLATED) nonPlated++;
                else throw new AssertionError("P07 output contains an unknown hole kind");
            }
            require(topTraces + bottomTraces == traces.size() &&
                holes.size() == layout.getHoleCount() &&
                vias <= PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,
                "selected P07 output face and drill counts cover the actual geometry objects");
            layoutOutputJson = "{\"layoutReturned\":true,\"zeroOutput\":false,\"fingerprint\":\"" +
                jsonFull(fingerprint) + "\",\"fingerprintLength\":" + fingerprint.length() +
                ",\"fingerprintJavaHash\":\"" + Integer.toHexString(fingerprint.hashCode()) +
                "\",\"outline\":{\"x\":" + outline.x + ",\"y\":" + outline.y +
                ",\"width\":" + outline.width + ",\"height\":" + outline.height +
                "},\"rawSegments\":" + layout.getRawRoutingSegments() +
                ",\"topTraceCount\":" + topTraces + ",\"bottomTraceCount\":" + bottomTraces +
                ",\"totalTraceCount\":" + traces.size() + ",\"holeCount\":" + holes.size() +
                ",\"viaCount\":" + vias + ",\"platedHoleCount\":" + plated +
                ",\"nonPlatedHoleCount\":" + nonPlated + "}";
        }

        void prepareFacts() {
            StringBuilder facts = new StringBuilder("{\"protocolVersion\":2,\"runId\":\"");
            facts.append(jsonFull(runId)).append("\",\"fixture\":\"AUTHORED_RB100\"")
                .append(",\"variant\":\"").append(jsonFull(subject)).append("\"")
                .append(",\"layoutVersion\":").append(SeededPcbLayoutGenerator.CURRENT_VERSION)
                .append(",\"dependencyEpoch\":\"")
                .append(jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)).append("\"")
                .append(",\"planVersion\":").append(Rb100Plan.PLAN_VERSION)
                .append(",\"modelVersion\":").append(Rb100Plan.MODEL_VERSION)
                .append(",\"physicalIntentVersion\":").append(Rb100Placement.INTENT_VERSION)
                .append(routingInputMetadataFields(inputMetadata))
                .append(",\"seed\":\"").append(seed)
                .append("\",\"placementSeed\":\"").append(placementSeed)
                .append("\",\"routingSeed\":\"").append(routingSeed).append("\"")
                .append(",\"components\":").append(components)
                .append(",\"pads\":").append(pads)
                .append(",\"nets\":").append(nets)
                .append(",\"boardIdentityStatus\":\"").append(boardIdentityStatus).append("\"")
                .append(",\"solverGraphIdentityStatus\":\"")
                .append(solverGraphIdentityStatus).append("\"")
                .append(",\"sessionTerminalStatus\":\"")
                .append(jsonFull(sessionTerminalStatus)).append("\"")
                .append(",\"sessionException\":").append(sessionExceptionJson(sessionRejection))
                .append(",\"advanceCalls\":").append(advanceCalls)
                .append(",\"observerChecks\":").append(observerChecks)
                .append(",\"limits\":").append(limitsJson())
                .append(",\"primaryRoutingOrders\":")
                .append(primaryRoutingOrdersJson(policyResult))
                .append(",\"primaryRoutingExpansions\":")
                .append(primaryRoutingExpansionsJson(policyResult))
                .append(",\"policyFailure\":");
            String policyFailure = policyResult == null ? null : policyResult.getFailure();
            facts.append(policyFailure == null ? "null" : "\"" + jsonFull(policyFailure) + "\"")
                .append(",\"policyStatistics\":").append(policyStatisticsJson(policyResult))
                .append(",\"packingProbe\":").append(packingProbeJson(policyResult))
                .append(",\"layoutOutput\":").append(layoutOutputJson)
                .append(",\"layoutQualification\":false}");
            reportFactsJson = facts.toString();
        }

        @Override String toJson() {
            return "{\"protocolVersion\":2,\"runId\":\"" + jsonFull(runId) +
                "\",\"status\":\"" + status + "\",\"phase\":\"" +
                jsonFull(phase) + "\",\"operationMs\":" + operationMillis +
                ",\"cleanupMs\":" + cleanupMillis + ",\"cleanupStatus\":\"" +
                (cleanupPassed ? "PASS" : "FAIL") + "\",\"workUnits\":" + workUnits +
                ",\"maxUnitMs\":" + maxUnitMillis + ",\"failurePhase\":\"" +
                jsonFull(failurePhase) + "\",\"failureType\":\"" +
                jsonFull(typedFailure(failure)) + "\",\"failure\":\"" +
                jsonFull(failure == null ? "" : failure.toString()) +
                "\",\"cleanupFailureType\":\"" + jsonFull(typedFailure(cleanupFailure)) +
                "\",\"cleanupFailure\":\"" + jsonFull(cleanupFailure == null ? "" :
                    cleanupFailure.toString()) + "\",\"scope\":\"OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY\"," +
                "\"layoutQualification\":false,\"reportFacts\":" +
                (reportFactsJson == null ? "null" : reportFactsJson) + "}";
        }

        private static String sessionExceptionJson(PcbRoutingRejectedException rejection) {
            if (rejection == null) return "null";
            return "{\"type\":\"PcbRoutingRejectedException\",\"kind\":\"" +
                rejection.getKind().name() + "\",\"attemptIndex\":" +
                rejection.getAttemptIndex() + ",\"attemptCount\":" +
                rejection.getAttemptCount() + ",\"seed\":\"" + rejection.getSeed() +
                "\",\"exhausted\":" + rejection.isExhausted() + ",\"message\":\"" +
                jsonFull(rejection.getMessage()) + "\"}";
        }

        private static String limitsJson() {
            return "{\"placementCandidates\":" + MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES +
                ",\"routeCandidates\":" + MediumBoardPhysicalPolicy.ROUTING_CANDIDATES +
                ",\"p07Expansions\":" + PcbLayerRoutingPrototype.MAX_EXPANSIONS +
                ",\"p07BranchExpansions\":" + PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS +
                ",\"p07GridCells\":" + PcbLayerRoutingPrototype.MAX_CELLS +
                ",\"p07Orderings\":" + PcbLayerRoutingPrototype.ORDERINGS +
                ",\"p07Vias\":" + PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias +
                ",\"outerPlacements\":" + MAX_PLACEMENTS +
                ",\"outerRouteAttempts\":" + MAX_ROUTE_ATTEMPTS +
                ",\"outerRouteExpansions\":" + MAX_ROUTE_EXPANSIONS +
                ",\"outerAccessCells\":" + MAX_ACCESS_CELLS +
                ",\"operationMs\":" + nanosToMillis(CASE_LIMIT_NANOS) +
                ",\"workUnits\":" + WORK_LIMIT +
                ",\"unitMs\":" + nanosToMillis(UNIT_LIMIT_NANOS) +
                ",\"cleanupMs\":" + CLEANUP_LIMIT_MILLIS + "}";
        }

        private static String policyStatisticsJson(MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null) return "null";
            MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
            String canonical = statistics.toCanonical();
            return "{\"policyIdentity\":\"" + jsonFull(statistics.policyIdentity) +
                "\",\"outcome\":\"" + jsonFull(statistics.outcome) +
                "\",\"selectedRoutePolicy\":" + nullableString(statistics.selectedRoutePolicy) +
                ",\"placementCandidates\":" + statistics.placementCandidates +
                ",\"placementRejections\":" + statistics.placementRejections +
                ",\"placementEvaluations\":" + statistics.placementEvaluations +
                ",\"routeAttempts\":" + statistics.routeAttempts +
                ",\"oneFaceAttempts\":" + statistics.oneFaceAttempts +
                ",\"oneFaceSuccesses\":" + statistics.oneFaceSuccesses +
                ",\"twoLayerAttempts\":" + statistics.twoLayerAttempts +
                ",\"twoLayerSuccesses\":" + statistics.twoLayerSuccesses +
                ",\"selectedPlacementAttempt\":" + statistics.selectedPlacementAttempt +
                ",\"rankedPlacementCount\":" + statistics.rankedPlacementAttempts.size() +
                ",\"rankedPlacementAttempts\":" + integerVectorJson(
                    statistics.rankedPlacementAttempts) +
                ",\"placementScoreCount\":" + statistics.placementScores.size() +
                ",\"placementScores\":" + stringVectorJson(statistics.placementScores) +
                ",\"routeOutcomesCount\":" + statistics.routeOutcomes.size() +
                ",\"routeOutcomes\":" + stringVectorJson(statistics.routeOutcomes) +
                ",\"orderingSearchReceiptsCount\":" + statistics.orderingSearchReceipts.size() +
                ",\"orderingSearchReceipts\":" + orderingSearchReceiptsJson(statistics.orderingSearchReceipts) +
                ",\"routeEligiblePlacementAttemptCount\":" +
                    statistics.routeEligiblePlacementAttempts.size() +
                ",\"routeEligiblePlacementAttempts\":" + integerVectorJson(
                    statistics.routeEligiblePlacementAttempts) +
                ",\"routeEligibilitySkipCount\":" + statistics.routeEligibilitySkips.size() +
                ",\"routeEligibilitySkips\":" +
                    stringVectorJson(statistics.routeEligibilitySkips) +
                ",\"routingOrders\":" + statistics.routingOrders +
                ",\"routingExpansions\":" + statistics.routingExpansions +
                ",\"selectedPlacementScore\":" + statistics.selectedPlacementScore +
                ",\"selectedRouteQualityMilli\":" + statistics.selectedRouteQualityMilli +
                ",\"canonicalLength\":" + canonical.length() +
                ",\"canonicalJavaHash\":\"" + Integer.toHexString(canonical.hashCode()) +
                "\",\"canonical\":\"" + jsonFull(canonical) + "\"}";
        }

        private static String orderingSearchReceiptsJson(
                Vector<MediumBoardPhysicalPolicy.OrderingSearchReceipt> receipts) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < receipts.size(); index++) {
                if (index > 0) json.append(',');
                MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt = receipts.get(index);
                json.append("{\"attempt\":").append(receipt.attempt)
                    .append(",\"startedOrderings\":").append(receipt.startedOrderings)
                    .append(",\"completedOrderings\":").append(receipt.completedOrderings)
                    .append(",\"yieldedOrderings\":").append(receipt.yieldedOrderings)
                    .append(",\"finalizedOrderings\":").append(receipt.finalizedOrderings)
                    .append(",\"selectedOrdering\":").append(receipt.selectedOrdering)
                    .append(",\"expansions\":").append(receipt.expansions)
                    .append(",\"guidanceStarted\":").append(receipt.guidanceStarted)
                    .append(",\"comparisonComplete\":").append(receipt.comparisonComplete)
                    .append(",\"termination\":\"").append(jsonFull(receipt.termination)).append("\"}");
            }
            return json.append(']').toString();
        }

        private static String primaryRoutingOrdersJson(
                MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null) return "null";
            MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
            PcbPackingProbeReceipt probe = statistics.packingProbe;
            return Integer.toString(statistics.routingOrders -
                (probe == null ? 0 : probe.orderings));
        }

        private static String primaryRoutingExpansionsJson(
                MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null) return "null";
            MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
            PcbPackingProbeReceipt probe = statistics.packingProbe;
            return Integer.toString(statistics.routingExpansions -
                (probe == null ? 0 : probe.expansions));
        }

        private static String packingProbeJson(MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null ||
                    result.getStatistics().packingProbe == null) return "null";
            PcbPackingProbeReceipt probe = result.getStatistics().packingProbe;
            String canonical = probe.toCanonical();
            return "{\"attempts\":" + probe.attempts + ",\"orderings\":" + probe.orderings +
                ",\"expansions\":" + probe.expansions + ",\"millis\":" + probe.millis +
                ",\"routePolicy\":\"" + jsonFull(probe.routePolicy) +
                "\",\"disposition\":\"" + jsonFull(probe.disposition) +
                "\",\"baselineGeometry\":\"" + jsonFull(probe.baselineGeometry) +
                "\",\"selectedGeometry\":\"" + jsonFull(probe.selectedGeometry) +
                "\",\"metricEvidence\":" + metricEvidenceJson(probe.metricEvidence) +
                ",\"canonicalLength\":" + canonical.length() +
                ",\"canonicalJavaHash\":\"" + Integer.toHexString(canonical.hashCode()) +
                "\",\"canonical\":\"" + jsonFull(canonical) + "\"}";
        }

        private static String metricEvidenceJson(PcbPackingProbeReceipt.MetricEvidence evidence) {
            if (evidence == null) return "null";
            return "{\"state\":\"" + evidence.state.name() + "\",\"baseline\":" +
                packingMetricsJson(evidence.baseline) + ",\"candidate\":" +
                packingMetricsJson(evidence.candidate) + ",\"unavailableReason\":" +
                nullableString(evidence.unavailableReason) + "}";
        }

        private static String packingMetricsJson(PcbPackingMetrics metrics) {
            if (metrics == null) return "null";
            return "{\"area\":" + metrics.area + ",\"copper\":" + metrics.copper +
                ",\"vias\":" + metrics.vias + ",\"protectedVoid\":" + metrics.protectedVoid + "}";
        }

        private static String nullableString(String value) {
            return value == null ? "null" : "\"" + jsonFull(value) + "\"";
        }

        private static String integerVectorJson(Vector<Integer> values) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < values.size(); index++) {
                if (index != 0) json.append(',');
                json.append(values.get(index).intValue());
            }
            return json.append(']').toString();
        }

        private static String stringVectorJson(Vector<String> values) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < values.size(); index++) {
                if (index != 0) json.append(',');
                json.append('"').append(jsonFull(values.get(index))).append('"');
            }
            return json.append(']').toString();
        }
    }

    private static final class BranchDiagnosticCase extends PlacementDiagnosticCase {
        final String runId;
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> branches =
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        long placementSeed, routingSeed, maximumBranchEvents;
        int advanceCalls, observerChecks;
        String boardIdentityStatus = "NOT_REACHED";
        String solverGraphIdentityStatus = "NOT_REACHED";
        String placementFingerprint;
        String layoutOutputJson = "null";
        RoutingInputMetadata inputMetadata;
        PcbLayerRoutingPrototype.Result routeResult;

        BranchDiagnosticCase(String variant, long seed, String runId) {
            super("AUTHORED_RB100", variant, seed, 4);
            this.runId = runId;
        }

        void captureLayoutOutput() {
            if (routeResult == null || routeResult.layout == null) {
                layoutOutputJson = "{\"layoutReturned\":false,\"zeroOutput\":true,\"fingerprint\":null," +
                    "\"outline\":null,\"rawSegments\":0,\"topTraceCount\":0,\"bottomTraceCount\":0," +
                    "\"totalTraceCount\":0,\"holeCount\":0,\"viaCount\":0," +
                    "\"platedHoleCount\":0,\"nonPlatedHoleCount\":0}";
                return;
            }
            PcbBoardLayout layout = routeResult.layout;
            String fingerprint = layout.geometryFingerprint();
            require(fingerprint != null && fingerprint.length() > 0 &&
                fingerprint.length() <= 1024 * 1024,
                "routed P07 output fingerprint stays within the 1MiB digest limit");
            int top = 0, bottom = 0;
            for (PcbTraceGeometry trace : layout.getTraces()) {
                if (trace.getLayer() == PcbCopperLayer.TOP) top++;
                else if (trace.getLayer() == PcbCopperLayer.BOTTOM) bottom++;
                else throw new AssertionError("P07 output contains an unknown layer");
            }
            int vias = 0, plated = 0, nonPlated = 0;
            Vector<PcbBoardHole> holes = layout.getHoles();
            for (PcbBoardHole hole : holes) {
                if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
                else if (hole.kind == PcbBoardHole.Kind.PLATED) plated++;
                else if (hole.kind == PcbBoardHole.Kind.NON_PLATED) nonPlated++;
                else throw new AssertionError("P07 output contains an unknown hole kind");
            }
            Rectangle outline = layout.getBoardOutline();
            layoutOutputJson = "{\"layoutReturned\":true,\"zeroOutput\":false,\"fingerprint\":" +
                fingerprintJson(fingerprint) + ",\"outline\":{\"x\":" + outline.x +
                ",\"y\":" + outline.y + ",\"width\":" + outline.width +
                ",\"height\":" + outline.height + "},\"rawSegments\":" +
                layout.getRawRoutingSegments() + ",\"topTraceCount\":" + top +
                ",\"bottomTraceCount\":" + bottom + ",\"totalTraceCount\":" +
                layout.getTraces().size() + ",\"holeCount\":" + holes.size() +
                ",\"viaCount\":" + vias + ",\"platedHoleCount\":" + plated +
                ",\"nonPlatedHoleCount\":" + nonPlated + "}";
        }

        void prepareFacts() {
            StringBuilder facts = new StringBuilder("{\"protocolVersion\":2,\"runId\":\"");
            facts.append(jsonFull(runId)).append("\",\"fixture\":\"AUTHORED_RB100\"")
                .append(",\"variant\":\"").append(jsonFull(subject)).append("\"")
                .append(",\"seed\":\"").append(seed).append("\"")
                .append(",\"placementSeed\":\"").append(placementSeed).append("\"")
                .append(",\"routingSeed\":\"").append(routingSeed).append("\"")
                .append(",\"candidate\":4,\"fixedPlacementAttempt\":4")
                .append(",\"layoutVersion\":").append(SeededPcbLayoutGenerator.CURRENT_VERSION)
                .append(",\"dependencyEpoch\":\"")
                .append(jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)).append("\"")
                .append(",\"planVersion\":").append(Rb100Plan.PLAN_VERSION)
                .append(",\"modelVersion\":").append(Rb100Plan.MODEL_VERSION)
                .append(",\"physicalIntentVersion\":").append(Rb100Placement.INTENT_VERSION)
                .append(routingInputMetadataFields(inputMetadata))
                .append(",\"components\":").append(components)
                .append(",\"pads\":").append(pads)
                .append(",\"nets\":").append(nets)
                .append(",\"boardIdentityStatus\":\"").append(boardIdentityStatus).append("\"")
                .append(",\"solverGraphIdentityStatus\":\"")
                .append(solverGraphIdentityStatus).append("\"")
                .append(",\"advanceCalls\":").append(advanceCalls)
                .append(",\"observerChecks\":").append(observerChecks)
                .append(",\"limits\":{\"p07Expansions\":")
                .append(PcbLayerRoutingPrototype.MAX_EXPANSIONS)
                .append(",\"p07BranchExpansions\":")
                .append(PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS)
                .append(",\"p07GridCells\":").append(PcbLayerRoutingPrototype.MAX_CELLS)
                .append(",\"p07Orderings\":").append(PcbLayerRoutingPrototype.ORDERINGS)
                .append(",\"p07Vias\":")
                .append(PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias)
                .append(",\"operationMs\":").append(nanosToMillis(CASE_LIMIT_NANOS))
                .append(",\"workUnits\":").append(WORK_LIMIT)
                .append(",\"unitMs\":").append(nanosToMillis(UNIT_LIMIT_NANOS))
                .append(",\"cleanupMs\":").append(CLEANUP_LIMIT_MILLIS).append("}")
                .append(",\"placementFingerprint\":")
                .append(placementFingerprint == null ? "null" : fingerprintJson(placementFingerprint))
                .append(",\"branchCount\":").append(branches.size())
                .append(",\"branches\":").append(branchesJson())
                .append(",\"routeResult\":").append(routeResultJson())
                .append(",\"layoutOutput\":").append(layoutOutputJson)
                .append(",\"layoutQualification\":false}");
            reportFactsJson = facts.toString();
            require(reportFactsJson.length() <= 4 * 1024 * 1024,
                "complete bounded branch report fits the 4MiB native receipt limit");
        }

        private String fingerprintJson(String fingerprint) {
            return "{\"length\":" + fingerprint.length() +
                ",\"javaHash\":\"" + Integer.toHexString(fingerprint.hashCode()) +
                "\",\"sha256\":\"" + sha256Hex(fingerprint) + "\"}";
        }

        private String branchesJson() {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < branches.size(); index++) {
                if (index != 0) json.append(',');
                PcbLayerRoutingPrototype.BranchSnapshot branch = branches.get(index);
                json.append("{\"pass\":").append(branch.pass)
                    .append(",\"netId\":\"").append(jsonFull(branch.netId)).append("\"")
                    .append(",\"startPadId\":\"").append(jsonFull(branch.startPadId)).append("\"")
                    .append(",\"rootPadId\":\"").append(jsonFull(branch.rootPadId)).append("\"")
                    .append(",\"startX\":").append(branch.startX)
                    .append(",\"startY\":").append(branch.startY)
                    .append(",\"rootX\":").append(branch.rootX)
                    .append(",\"rootY\":").append(branch.rootY)
                    .append(",\"gridWidth\":").append(branch.gridWidth)
                    .append(",\"gridHeight\":").append(branch.gridHeight)
                    .append(",\"goalsTop\":").append(branch.goalsTop)
                    .append(",\"goalsBottom\":").append(branch.goalsBottom)
                    .append(",\"prefixViaCount\":").append(branch.prefixViaCount)
                    .append(",\"reachedPadCount\":").append(branch.reachedPadCount)
                    .append(",\"initialManhattanDistance\":")
                    .append(branch.initialManhattanDistance)
                    .append(",\"minimumExploredManhattanDistance\":")
                    .append(branch.minimumExploredManhattanDistance)
                    .append(",\"branchExpanded\":").append(branch.branchExpanded)
                    .append(",\"totalExpanded\":").append(branch.totalExpanded)
                    .append(",\"expandedTop\":").append(branch.expandedTop)
                    .append(",\"expandedBottom\":").append(branch.expandedBottom)
                    .append(",\"frontierCount\":").append(branch.frontierCount)
                    .append(",\"bestStateCount\":").append(branch.bestStateCount)
                    .append(",\"terminalReason\":\"")
                    .append(jsonFull(String.valueOf(branch.terminalReason))).append("\"}");
            }
            return json.append(']').toString();
        }

        private String routeResultJson() {
            if (routeResult == null) return "null";
            return "{\"accepted\":" + routeResult.accepted() +
                ",\"outcome\":\"" + jsonFull(routeResult.outcome) +
                "\",\"expansions\":" + routeResult.expansions +
                ",\"orderings\":" + routeResult.orderings +
                ",\"vias\":" + routeResult.vias + ",\"links\":" + routeResult.links + "}";
        }

        @Override String toJson() {
            return "{\"protocolVersion\":2,\"runId\":\"" + jsonFull(runId) +
                "\",\"status\":\"" + status + "\",\"phase\":\"" + jsonFull(phase) +
                "\",\"operationMs\":" + operationMillis + ",\"cleanupMs\":" + cleanupMillis +
                ",\"cleanupStatus\":\"" + (cleanupPassed ? "PASS" : "FAIL") +
                "\",\"workUnits\":" + workUnits + ",\"maxUnitMs\":" + maxUnitMillis +
                ",\"failurePhase\":\"" + jsonFull(failurePhase) + "\",\"failureType\":\"" +
                jsonFull(typedFailure(failure)) + "\",\"failure\":\"" +
                jsonFull(failure == null ? "" : failure.toString()) +
                "\",\"cleanupFailureType\":\"" + jsonFull(typedFailure(cleanupFailure)) +
                "\",\"cleanupFailure\":\"" + jsonFull(cleanupFailure == null ? "" :
                    cleanupFailure.toString()) + "\",\"scope\":\"OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY\"," +
                "\"layoutQualification\":false,\"reportFacts\":" +
                (reportFactsJson == null ? "null" : reportFactsJson) + "}";
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int unsigned = item & 0xff;
                if (unsigned < 16) hex.append('0');
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("JDK is missing SHA-256", impossible);
        }
    }

    private static final class DiagnosticCounts implements PcbPlacementPlanner.DiagnosticSink {
        final Budget budget;
        final RuntimeException abort;
        final EnumMap<PcbPlacementPlanner.FitOutcome, Long> counts =
            new EnumMap<PcbPlacementPlanner.FitOutcome, Long>(PcbPlacementPlanner.FitOutcome.class);
        int fitCallbacks, snapshotCalls;
        PcbPlacementPlanner.PlacementFailureSnapshot snapshot;
        DiagnosticCounts(Budget budget, RuntimeException abort) {
            this.budget = budget; this.abort = abort;
            for (PcbPlacementPlanner.FitOutcome outcome : PcbPlacementPlanner.FitOutcome.values())
                counts.put(outcome, 0L);
        }
        public void onFitOutcome(PcbPlacementPlanner.FitOutcome outcome) {
            budget.checkpoint();
            if (outcome == null) throw new AssertionError("planner emitted a null fit outcome");
            Long prior = counts.get(outcome);
            counts.put(outcome, Long.valueOf((prior == null ? 0 : prior.longValue()) + 1));
            fitCallbacks++;
            if (abort != null) throw abort;
        }
        public void onPlacementFailure(PcbPlacementPlanner.PlacementFailureSnapshot failure) {
            budget.checkpoint();
            snapshotCalls++;
            if (snapshotCalls != 1 || failure == null)
                throw new AssertionError("planner emitted duplicate or null failure snapshot");
            snapshot = failure;
        }
        long get(PcbPlacementPlanner.FitOutcome outcome) {
            Long value = counts.get(outcome);
            return value == null ? 0 : value.longValue();
        }
        long totalOutcomes() {
            long total = 0;
            for (Long value : counts.values()) total += value.longValue();
            return total;
        }
        EnumMap<PcbPlacementPlanner.FitOutcome, Long> copyCounts() {
            return new EnumMap<PcbPlacementPlanner.FitOutcome, Long>(counts);
        }
    }

    private static final class PlacementFixtureIdentity {
        final TreeMap<String, BoardComponent> components = new TreeMap<String, BoardComponent>();
        final TreeMap<String, String> pads = new TreeMap<String, String>();
        final TreeSet<String> nets = new TreeSet<String>();
        final PcbPlacementConstraints constraints;

        private PlacementFixtureIdentity(PcbPlacementConstraints constraints) {
            this.constraints = constraints;
        }

        static PlacementFixtureIdentity capture(TroubleshootBoard board) {
            PcbPlacementConstraints constraints = board.getPlacementConstraints();
            PlacementFixtureIdentity identity = new PlacementFixtureIdentity(constraints);
            for (String componentId : board.getComponentIds()) {
                BoardComponent component = board.getComponent(componentId);
                require(component != null, "fixture component exists before planner calls: " + componentId);
                identity.components.put(componentId, component);
            }
            for (String padId : board.getPadIds()) {
                BoardPad pad = board.getPad(padId);
                require(pad != null, "fixture pad exists before planner calls: " + padId);
                identity.pads.put(padId, padRecord(pad));
            }
            identity.nets.addAll(board.getNetIds());
            return identity;
        }

        void requireUnchanged(TroubleshootBoard board) {
            require(constraints == board.getPlacementConstraints() &&
                components.keySet().equals(new TreeSet<String>(board.getComponentIds())) &&
                pads.keySet().equals(new TreeSet<String>(board.getPadIds())) &&
                nets.equals(new TreeSet<String>(board.getNetIds())),
                "real planner observation retains fixture component, pad, net and constraint identities");
            for (Map.Entry<String, BoardComponent> entry : components.entrySet())
                require(board.getComponent(entry.getKey()) == entry.getValue(),
                    "planner preserves actual component object identity: " + entry.getKey());
            for (Map.Entry<String, String> entry : pads.entrySet())
                require(entry.getValue().equals(padRecord(board.getPad(entry.getKey()))),
                    "planner preserves actual pad terminal/net mapping: " + entry.getKey());
        }
    }

    private static final class DiagnosticCallbackAbort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        DiagnosticCallbackAbort() { super("intentional local placement diagnostic callback abort"); }
    }

    private static final class BoardIdentity {
        final TreeMap<String, PhysicalPackage> components = new TreeMap<String, PhysicalPackage>();
        final TreeMap<String, String> pads = new TreeMap<String, String>();
        final TreeMap<String, EndpointIdentity> endpoints = new TreeMap<String, EndpointIdentity>();
        final TreeSet<String> nets = new TreeSet<String>();
        private BoardIdentity() { }
        static BoardIdentity capture(Rb100ElectricalPilot.Candidate candidate) {
            Rb100Plan plan = candidate.plan;
            TroubleshootBoard board = candidate.board();
            require(CircuitElm.sim != null && CirSim.theSim == CircuitElm.sim &&
                CircuitElm.sim.elmList != null && CircuitElm.sim.elmList.isEmpty(),
                "native candidate is not installed in any solver graph");
            BoardIdentity identity = new BoardIdentity();
            TreeSet<String> expectedComponents = new TreeSet<String>();
            TreeSet<String> expectedPads = new TreeSet<String>();
            TreeSet<String> expectedNets = new TreeSet<String>();
            for (Rb100Plan.Part part : plan.parts()) {
                BoardComponent component = board.getComponent(part.id);
                require(component != null && component.getPhysicalPackage() == part.physicalPackage,
                    "authored package identity is present before physical declaration: " + part.id);
                expectedComponents.add(part.id);
                identity.components.put(part.id, part.physicalPackage);
                String[] terminals = part.terminalIds();
                String[] nets = part.netIds();
                for (String net : nets) expectedNets.add(net);
                require(terminals.length == nets.length && component.getPadIds().size() == terminals.length,
                    "authored package pads equal its plan terminal count: " + part.id);
                for (int i = 0; i < terminals.length; i++) {
                    String padId = part.id + "." + terminals[i];
                    BoardPad pad = board.getPad(padId);
                    require(pad != null && pad.getComponentId().equals(part.id) &&
                        pad.getTerminalId().equals(terminals[i]) && pad.getNetId().equals(nets[i]),
                        "plan-derived terminal and net identity exists before layout: " + padId);
                    expectedPads.add(padId);
                    identity.pads.put(padId, padRecord(pad));
                    CircuitMeasurementEndpoint endpoint =
                        board.getSimulationBindings().getEndpoint(padId);
                    require(endpoint instanceof CircuitPostMeasurementEndpoint,
                        "actual board terminal has a CircuitJS post endpoint: " + padId);
                    CircuitPostMeasurementEndpoint post =
                        (CircuitPostMeasurementEndpoint)endpoint;
                    require(containsIdentity(candidate.assembly.elements, post.getElement()) &&
                        post.getPostIndex() >= 0 &&
                        post.getPostIndex() < post.getElement().getPostCount() &&
                        post.getElement().getPost(post.getPostIndex()) != null,
                        "actual CircuitJS element/post belongs to the unpublished candidate: " + padId);
                    identity.endpoints.put(padId, new EndpointIdentity(post.getElement(),
                        post.getPostIndex(), pad.getNetId(), pad.getTerminalId()));
                }
            }
            require(expectedComponents.equals(new TreeSet<String>(board.getComponentIds())),
                "plan-derived component identity set equals actual candidate board");
            require(expectedPads.equals(new TreeSet<String>(board.getPadIds())),
                "plan-derived complete pad identity set equals actual candidate board");
            TreeSet<String> actualNets = new TreeSet<String>(board.getNetIds());
            require(expectedNets.equals(actualNets),
                "unique plan-derived net identity set equals actual candidate board");
            identity.nets.addAll(actualNets);
            return identity;
        }
        void requireUnchanged(Rb100ElectricalPilot.Candidate candidate) {
            TroubleshootBoard board = candidate.board();
            require(components.keySet().equals(new TreeSet<String>(board.getComponentIds())) &&
                pads.keySet().equals(new TreeSet<String>(board.getPadIds())) &&
                nets.equals(new TreeSet<String>(board.getNetIds())) &&
                endpoints.keySet().equals(pads.keySet()),
                "component, pad, net and solver-endpoint identity sets remain unchanged through layout");
            for (Map.Entry<String, PhysicalPackage> entry : components.entrySet())
                require(board.getComponent(entry.getKey()).getPhysicalPackage() == entry.getValue(),
                    "exact package object remains unchanged through layout: " + entry.getKey());
            for (String padId : pads.keySet())
                require(pads.get(padId).equals(padRecord(board.getPad(padId))),
                    "terminal and net mapping remains unchanged through layout: " + padId);
            for (String padId : endpoints.keySet()) {
                CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(padId);
                require(endpoint instanceof CircuitPostMeasurementEndpoint,
                    "CircuitJS endpoint remains a physical post for " + padId);
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint)endpoint;
                EndpointIdentity expected = endpoints.get(padId);
                require(post.getElement() == expected.element &&
                    post.getPostIndex() == expected.postIndex &&
                    containsIdentity(candidate.assembly.elements, post.getElement()) &&
                    post.getPostIndex() >= 0 && post.getPostIndex() < post.getElement().getPostCount() &&
                    post.getElement().getPost(post.getPostIndex()) != null &&
                    expected.netId.equals(board.getPad(padId).getNetId()) &&
                    expected.terminalId.equals(board.getPad(padId).getTerminalId()),
                    "underlying CircuitJS element/post and terminal/net stay exact for " + padId);
            }
        }
    }

    private static final class EndpointIdentity {
        final CircuitElm element;
        final int postIndex;
        final String netId, terminalId;
        EndpointIdentity(CircuitElm element, int postIndex, String netId, String terminalId) {
            this.element = element; this.postIndex = postIndex;
            this.netId = netId; this.terminalId = terminalId;
        }
    }

    private static final class NativeContext {
        final CirSim priorCircuitSim = CircuitElm.sim;
        final CirSim priorSingleton = CirSim.theSim;
        final SimSnapshot priorCircuitSnapshot = new SimSnapshot(priorCircuitSim);
        final SimSnapshot priorSingletonSnapshot =
            priorSingleton == priorCircuitSim ? null : new SimSnapshot(priorSingleton);
        final ModelDefaults defaults = new ModelDefaults();
        final TestCirSim sim = new TestCirSim();
        final Vector<CircuitElm> rawGraph = sim.elmList;
        void activate() { CircuitElm.sim = sim; CirSim.theSim = sim; }
        void close(Rb100ElectricalPilot.Candidate candidate) {
            boolean exactDisposal = true;
            Throwable failure = null;
            try {
                activate();
                if (candidate != null) {
                    Vector<CircuitElm> owned = new Vector<CircuitElm>(candidate.assembly.elements);
                    IdentityHashMap<CircuitElm, Boolean> unique =
                        new IdentityHashMap<CircuitElm, Boolean>();
                    for (CircuitElm element : owned)
                        if (unique.put(element, Boolean.TRUE) != null) exactDisposal = false;
                    for (CircuitElm element : owned)
                        if (sim.elmList.contains(element))
                            throw new AssertionError("unpublished RB100 candidate entered a solver graph");
                    candidate.dispose();
                    candidate.dispose();
                    for (CircuitElm element : owned)
                        if (sim.deleted.get(element) == null || sim.deleted.get(element).intValue() != 1)
                            exactDisposal = false;
                    if (!candidate.assembly.elements.isEmpty()) exactDisposal = false;
                }
                for (Integer count : sim.deleted.values())
                    if (count.intValue() != 1) exactDisposal = false;
                if (sim.elmList != rawGraph || !rawGraph.isEmpty() || !exactDisposal)
                    throw new AssertionError("private candidate graph was not disposed exactly once");
            } catch (RuntimeException cleanupError) {
                failure = cleanupError;
            } catch (Error cleanupError) {
                failure = cleanupError;
            } finally {
                try {
                    defaults.restore();
                    CircuitElm.sim = priorCircuitSim;
                    CirSim.theSim = priorSingleton;
                    boolean restored = (priorCircuitSnapshot == null || priorCircuitSnapshot.matches()) &&
                        (priorSingletonSnapshot == null || priorSingletonSnapshot.matches()) &&
                        defaults.matchesRestored() && CircuitElm.sim == priorCircuitSim &&
                        CirSim.theSim == priorSingleton;
                    if (!restored)
                        failure = append(failure,
                            new AssertionError("prior native singletons, graph, scopes or defaults changed"));
                } catch (RuntimeException restoreError) {
                    failure = append(failure, restoreError);
                } catch (Error restoreError) {
                    failure = append(failure, restoreError);
                }
            }
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure instanceof Error) throw (Error)failure;
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
            if (sim == null) return true;
            if (sim.elmList != graph || sim.scopeCount != scopeCount ||
                    sim.scopeColCount != scopeColumns || !Arrays.equals(sim.scopes, scopeElements) ||
                    !Arrays.equals(scopeColumns, scopeColumnValues)) return false;
            if (graph == null) return graphElements == null;
            if (graph.size() != graphElements.size()) return false;
            for (int i = 0; i < graph.size(); i++) if (graph.get(i) != graphElements.get(i)) return false;
            return true;
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
            return same(diode, DiodeElm.lastModelName) && same(zener, ZenerElm.lastZenerModelName) &&
                same(transistor, TransistorElm.lastModelName) &&
                same(customLogic, CustomLogicElm.lastModelName) &&
                same(customComposite, CustomCompositeElm.lastModelName) &&
                same(led, LEDElm.lastLEDModelName) &&
                Double.doubleToLongBits(mosfetBeta) == Double.doubleToLongBits(MosfetElm.lastBeta) &&
                groundSymbol == GroundElm.lastSymbolType;
        }
        private static boolean same(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    private static final class TestCirSim extends CirSim {
        final IdentityHashMap<CircuitElm, Integer> deleted =
            new IdentityHashMap<CircuitElm, Integer>();
        TestCirSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            scopes = new Scope[20]; scopeColCount = new int[20]; scopeCount = 0;
            maxTimeStep = minTimeStep = timeStep = .0001; adjustTimeStep = false;
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
