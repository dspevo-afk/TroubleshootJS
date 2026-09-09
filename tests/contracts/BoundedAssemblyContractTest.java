package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Maintained pure acceptance checks for the current bounded resistive
 * composition. The assertions describe electrical ownership and replay
 * invariants; they deliberately avoid historical IDs, byte goldens, and
 * intermediate assembly counts.
 */
public final class BoundedAssemblyContractTest {
    private static int assertions;
    private static final long[] SEEDS = {
        0L, 1L, 2L, 3L, Long.MIN_VALUE, Long.MAX_VALUE,
        -9007199254740993L, 9007199254740993L
    };

    private BoundedAssemblyContractTest() { }

    public static void main(String[] args) {
        currentDescriptorAndReplay();
        namedStreamIsolation();
        healthyFaultAndCrossNetRelationships();
        qualifiedPhysicalLocusBoundary();
        preflightOutcomesAndRejections();
        constraintsAndRetiredDescriptorRejections();
        System.out.println("PASS: current resistive assembly contracts "
                + assertions + " assertions");
    }

    private static void currentDescriptorAndReplay() {
        for (long seed : SEEDS) {
            ChallengeDescriptor descriptor = BoundedAssemblyRequest.descriptor(seed,
                    GenerationConstraints.unspecified());
            require(descriptor.getSchemaVersion() == ChallengeDescriptor.SCHEMA_VERSION,
                    "current schema is used");
            require(BoundedAssemblyRequest.GENERATOR_ID.equals(
                    descriptor.getGenerator().getId()) &&
                    descriptor.getGenerator().getVersion() ==
                        BoundedAssemblyRequest.GENERATOR_VERSION,
                    "current bounded generator identity is used");
            require(descriptor.getRootSeed() == seed,
                    "signed root seed survives descriptor construction");

            BoundedAssemblyRequest request = BoundedAssemblyRequest.forCanary(
                    descriptor);
            PortCompatibilityPreflight.Result preflight =
                    BoundedAssemblyPlan.preflight(request);
            require(BoundedAssemblyPlan.isCompatible(preflight),
                    "stock canary is electrically compatible");
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
            ComposedBlockContribution source = plan.getBlocks().get("source");
            ComposedBlockContribution load = plan.getBlocks().get("load");
            require(source != null && load != null,
                    "both current blocks resolve");
            require(source.getResistanceOhms() > 0.0 &&
                    load.getResistanceOhms() > 0.0,
                    "resolved resistances are finite and positive");
            require(plan.getBlocks().size() == request.getBlocks().size(),
                    "every declared block has one resolved contribution");
            require(plan.netForPort("source", "OUT").equals(
                    plan.netForPort("load", "IN")),
                    "signal connection is explicit");
            require(plan.netForPort("source", "RETURN").equals(
                    plan.netForPort("load", "RETURN")),
                    "return connection is explicit");
            require(!plan.netFor("source", "SUPPLY").equals(
                    plan.netFor("load", "SUPPLY")),
                    "same local net names do not create a cross-block short");
            require(!plan.getMergeProvenance().isEmpty(),
                    "current plan retains merge provenance");

            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            String sourcePad = plan.idFor("source", EntityKind.PAD, "R1.1");
            String sourceEndpoint = plan.idFor("source", EntityKind.ENDPOINT, "R1_1");
            require(nonEmpty(sourceComponent) && nonEmpty(loadComponent) &&
                    nonEmpty(sourcePad) && nonEmpty(sourceEndpoint),
                    "semantic identity API returns non-empty IDs");
            require(!sourceComponent.equals(loadComponent),
                    "same local component IDs retain distinct owners");
            require(sourceComponent.contains("source") && sourceComponent.contains("R1") &&
                    loadComponent.contains("load") && loadComponent.contains("R1"),
                    "semantic IDs retain block and local ownership");
            require(plan.getDecisionOwners().containsValue(sourceComponent) ||
                    plan.getDecisionOwners().containsValue(loadComponent),
                    "fault decision has a qualified component owner");
            require(plan.getFaultBlockKey() != null &&
                    plan.getFaultDecisionKey() != null &&
                    !plan.getFaultDecisionKey().isEmpty(),
                    "fault decision retains an explicit block owner");
            ComposedBlockContribution selected = "source".equals(
                    plan.getFaultBlockKey()) ? source : load;
            require(selected.getFaultSpec().getKind() ==
                    ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE,
                    "resistive fault is represented by its actual effect");
            require(selected.getFaultSpec().getTargetComponentLocalId().equals(
                    selected.getRepairLocalComponentId()),
                    "resistive fault target is the repair component");
            require(selected.getFaultSpec().getEffectiveResistanceOhms() !=
                    selected.getResistanceOhms(),
                    "resistive fault effect differs from the healthy recipe");

            BoundedAssemblyPlan replay = BoundedAssemblyPlan.resolve(
                    new BoundedAssemblyRequest(descriptor,
                            reversed(request.getBlocks()),
                            reversed(request.getConnections())));
            require(plan.getSemanticSignature().equals(replay.getSemanticSignature()),
                    "declaration order does not change current semantics");
            require(ChallengeDescriptor.parse(descriptor.toCanonical()).equals(
                    descriptor), "full signed seed canonical round trip");

            double healthyCurrent = 5.0 / (source.getResistanceOhms()
                    + load.getResistanceOhms());
            verifyHealthy(source, 5.0, 5.0 - healthyCurrent
                    * source.getResistanceOhms(), healthyCurrent,
                    source.getResistanceOhms());
            verifyHealthy(load, healthyCurrent * load.getResistanceOhms(),
                    0.0, healthyCurrent, load.getResistanceOhms());
            System.out.println("seed=" + Long.toString(seed) +
                    ";source-ohms=" + Double.toString(source.getResistanceOhms())
                    + ";load-ohms=" + Double.toString(load.getResistanceOhms())
                    + ";fault=" + plan.getFaultDecisionKey());
        }
    }

    private static void namedStreamIsolation() {
        for (long seed : SEEDS) {
            NamedRandomStreams streams = new NamedRandomStreams(1, seed,
                    BoundedAssemblyRequest.INTENT_ID,
                    BoundedAssemblyRequest.INTENT_VERSION);
            String source = NamedRandomStreams.select(streams.blockSeed("source",
                    NamedRandomStreams.Concern.VALUES, 1, "resistance"),
                    Arrays.asList("r100", "r220"));
            String load = NamedRandomStreams.select(streams.blockSeed("load",
                    NamedRandomStreams.Concern.VALUES, 1, "resistance"),
                    Arrays.asList("r1000", "r2200"));
            String fault = NamedRandomStreams.select(streams.deviceSeed(
                    NamedRandomStreams.Concern.FAULT, 1, "selected-fault"),
                    Arrays.asList("source-high-resistance",
                            "load-high-resistance"));
            NamedRandomStreams.Stream unrelatedScenario = streams.openDevice(
                    NamedRandomStreams.Concern.SCENARIO, 1, "unrelated");
            NamedRandomStreams.Stream unrelatedBlock = streams.openBlock(
                    "unrelated", NamedRandomStreams.Concern.VALUES, 1,
                    "resistance");
            NamedRandomStreams.Stream unrelatedPlacement = streams.openBlock(
                    "source", NamedRandomStreams.Concern.PLACEMENT, 1,
                    "layout");
            for (int draw = 0; draw < 9; draw++) {
                unrelatedScenario.nextLong();
                unrelatedBlock.nextLong();
                unrelatedPlacement.nextLong();
            }
            require(source.equals(NamedRandomStreams.select(streams.blockSeed(
                    "source", NamedRandomStreams.Concern.VALUES, 1,
                    "resistance"), Arrays.asList("r100", "r220"))),
                    "source values stream is concern-isolated");
            require(load.equals(NamedRandomStreams.select(streams.blockSeed(
                    "load", NamedRandomStreams.Concern.VALUES, 1,
                    "resistance"), Arrays.asList("r1000", "r2200"))),
                    "load values stream is concern-isolated");
            require(fault.equals(NamedRandomStreams.select(streams.deviceSeed(
                    NamedRandomStreams.Concern.FAULT, 1, "selected-fault"),
                    Arrays.asList("source-high-resistance",
                            "load-high-resistance"))),
                    "fault stream is concern-isolated");
            require(source.equals(ResistiveBlockContributions.chooseValueKey(seed,
                    "source")) && load.equals(
                    ResistiveBlockContributions.chooseValueKey(seed, "load")) &&
                    fault.equals(ResistiveBlockContributions.chooseFaultDecision(seed)),
                    "provider selectors agree with the independent stream tuple");
        }
    }

    private static void healthyFaultAndCrossNetRelationships() {
        ComposedBlockContribution source = ResistiveBlockContributions
                .source().create("source", 100.0);
        source.verifyHealthy(observation(5.0, 4.5, 0.005, 100.0));
        require(source.observe(observation(5.0, 4.5, 0.005, 100.0)) ==
                ComposedBlockContribution.ObservationResult.CONDUCTING,
                "healthy measured resistor conducts");
        require(source.observe(observation(5.0, 4.995, .00005, 100000.0)) ==
                ComposedBlockContribution.ObservationResult.LOW_CURRENT,
                "incorrect resistance produces low current");
        require(source.observe(observation(Double.NaN, 0.0, 0.0, 100.0)) ==
                ComposedBlockContribution.ObservationResult.INVALID,
                "nonfinite observations fail closed");
        require(source.getInputRequirements().equals(
                Collections.singletonList("BOARD_POWER")),
                "power input requirement remains explicit");
        require(source.getRetestRequirements().equals(
                Collections.singletonList("STEADY_DC_POWERED")),
                "retest requirement remains explicit");
        try {
            source.getInputRequirements().add("MUTATION");
            fail("input requirements unexpectedly mutable");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
    }

    private static void qualifiedPhysicalLocusBoundary() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forCanary(0L));
        String owner = plan.idFor("source", EntityKind.COMPONENT, "R1");
        require(GeneratedFaultLocus.componentInternal(owner).getOwnerId().equals(owner),
                "qualified physical owner survives locus construction");
        require(GeneratedFaultLocus.terminalAttachment(owner, "2").getOwnerId()
                .equals(owner), "terminal locus retains component ownership");
        require(GeneratedFaultLocus.traceSegment("TRACE_A").getPathId()
                .equals("TRACE_A"), "trace locus remains local to a path");
        for (String value : Arrays.asList(owner + "/1", "NODE_1", "COORD_1")) {
            boolean rejected = false;
            try { GeneratedFaultLocus.componentInternal(value); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "private or malformed physical identity rejected");
        }
        boolean terminalRejected = false;
        try { GeneratedFaultLocus.terminalAttachment("R1", owner); }
        catch (IllegalArgumentException expected) { terminalRejected = true; }
        require(terminalRejected, "component identity cannot be a terminal ID");
        boolean pathRejected = false;
        try { GeneratedFaultLocus.traceSegment(owner); }
        catch (IllegalArgumentException expected) { pathRejected = true; }
        require(pathRejected, "component identity cannot be a trace path ID");
    }

    private static void preflightOutcomesAndRejections() {
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forCanary(0L);
        require(BoundedAssemblyPlan.preflight(request).getDecision() ==
                PortCompatibilityPreflight.Decision.COMPATIBLE,
                "compatible preflight outcome");
        ElectricalConnection malformed = new ElectricalConnection("signal",
                Arrays.asList(new ElectricalConnection.PortRef("source", "MISSING"),
                        new ElectricalConnection.PortRef("load", "IN")));
        expectDecision(PortCompatibilityPreflight.Decision.MALFORMED,
                BoundedAssemblyPlan.preflight(new BoundedAssemblyRequest(
                        request.getDescriptor(), request.getBlocks(),
                        Arrays.asList(malformed, request.getConnections().get(0)))));
        ElectricalConnection incompatible = new ElectricalConnection("signal",
                Arrays.asList(new ElectricalConnection.PortRef("source", "RETURN"),
                        new ElectricalConnection.PortRef("load", "IN")));
        expectDecision(PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                BoundedAssemblyPlan.preflight(new BoundedAssemblyRequest(
                        request.getDescriptor(), request.getBlocks(),
                        Arrays.asList(incompatible, request.getConnections().get(0)))));

        ElectricalBlockContract source = block(request, "source");
        ElectricalBlockContract load = block(request, "load");
        ElectricalPortContract oldOut = source.getPorts().get("OUT");
        ElectricalPortContract uncertainOut = new ElectricalPortContract("OUT",
                oldOut.getRole(), oldOut.getDirection(), oldOut.getBehavior(),
                oldOut.getDrive(), oldOut.getDomain(), oldOut.getNominalVoltage(),
                oldOut.getGuaranteedVoltage(), oldOut.getAllowedVoltage(),
                oldOut.getLoading(), oldOut.getCapacityAmps(), oldOut.getDemandAmps(),
                oldOut.getDigital(), MergePolicy.UNKNOWN,
                oldOut.getAccessRequirement(), oldOut.getAccessProvision());
        ElectricalBlockContract uncertainSource = new ElectricalBlockContract(
                source.getDescriptor(), Arrays.asList(uncertainOut,
                        source.getPorts().get("RETURN")), source.getAdapters().values());
        expectDecision(PortCompatibilityPreflight.Decision.INSUFFICIENT_INFORMATION,
                BoundedAssemblyPlan.preflight(new BoundedAssemblyRequest(
                        request.getDescriptor(), Arrays.asList(uncertainSource, load),
                        request.getConnections())));
        expectResolveFailure(new BoundedAssemblyRequest(request.getDescriptor(),
                Arrays.asList(uncertainSource, load), request.getConnections()));
        expectResolveFailure(new BoundedAssemblyRequest(request.getDescriptor(),
                request.getBlocks(), Arrays.asList(incompatible,
                        request.getConnections().get(0))));
    }

    private static void constraintsAndRetiredDescriptorRejections() {
        GenerationConstraints badBlocks = new GenerationConstraints(
                GenerationConstraints.VERSION,
                Arrays.asList(new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.BLOCKS,
                        GenerationConstraints.CountRange.exact(1))),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        expectResolveFailure(BoundedAssemblyRequest.forCanary(
                BoundedAssemblyRequest.descriptor(0L, badBlocks)));

        GenerationConstraints unsupportedDimension = new GenerationConstraints(
                GenerationConstraints.VERSION,
                Arrays.asList(new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.DIAGNOSTIC_DEPTH,
                        GenerationConstraints.CountRange.exact(1))),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        expectResolveFailure(BoundedAssemblyRequest.forCanary(
                BoundedAssemblyRequest.descriptor(0L, unsupportedDimension)));

        ChallengeDescriptor current = BoundedAssemblyRequest.descriptor(0L);
        expectRejectedCanonical(current.toCanonical().replace("tsj-challenge/2",
                "tsj-challenge/1"), "retired schema artifact");
        expectRejectedCanonical(current.toCanonical().replace(
                "generator=" + BoundedAssemblyRequest.GENERATOR_ID + "@" +
                    BoundedAssemblyRequest.GENERATOR_VERSION,
                "generator=" + BoundedAssemblyRequest.GENERATOR_ID + "@99"),
                "unknown bounded generator version");
        expectRejectedCanonical(current.toCanonical().replace(
                "device-intent=" + BoundedAssemblyRequest.INTENT_ID + "@" +
                    BoundedAssemblyRequest.INTENT_VERSION,
                "device-intent=unknown@1"), "unknown device intent");
    }

    private static ElectricalBlockContract block(BoundedAssemblyRequest request,
            String key) {
        for (ElectricalBlockContract block : request.getBlocks())
            if (key.equals(block.getDescriptor().getInstanceKey())) return block;
        throw new AssertionError("missing block " + key);
    }

    private static ComposedBlockContribution.Observation observation(
            final double firstVoltage, final double secondVoltage,
            final double current, final double resistance) {
        return new ComposedBlockContribution.Observation() {
            @Override public double voltage(String endpoint) {
                return "R1_1".equals(endpoint) ? firstVoltage : secondVoltage;
            }
            @Override public double current(String component) { return current; }
            @Override public double resistance(String component) { return resistance; }
        };
    }

    private static void verifyHealthy(ComposedBlockContribution contribution,
            final double firstVoltage, final double secondVoltage,
            final double current, final double resistance) {
        contribution.verifyHealthy(observation(firstVoltage, secondVoltage,
                current, resistance));
        assertions++;
    }

    private static <T> List<T> reversed(List<T> values) {
        ArrayList<T> copy = new ArrayList<T>(values);
        Collections.reverse(copy);
        return copy;
    }

    private static void expectDecision(PortCompatibilityPreflight.Decision expected,
            PortCompatibilityPreflight.Result actual) {
        require(actual.getDecision() == expected,
                "expected preflight " + expected + ", got " + actual.getDecision());
    }

    private static void expectResolveFailure(BoundedAssemblyRequest request) {
        try {
            BoundedAssemblyPlan.resolve(request);
            fail("unsupported request unexpectedly resolved");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void expectRejectedCanonical(String canonical, String label) {
        try {
            ChallengeDescriptor parsed = ChallengeDescriptor.parse(canonical);
            BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forCanary(parsed));
            fail(label + " was admitted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
