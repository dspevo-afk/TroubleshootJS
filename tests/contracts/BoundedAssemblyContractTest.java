package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessProvision;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessRequirement;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Behavior;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Direction;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Loading;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Role;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/** Focused pure acceptance matrix for the Task 47 request/provider/plan seam. */
public final class BoundedAssemblyContractTest {
    private static int assertions;
    private static final long[] SEEDS = {
        0L, 1L, 2L, 3L, Long.MIN_VALUE, Long.MAX_VALUE,
        -9007199254740993L, 9007199254740993L
    };

    private BoundedAssemblyContractTest() { }

    public static void main(String[] args) {
        receiptAndCanonicalReplay();
        namedStreamIsolation();
        localRulesUseObservations();
        qualifiedPhysicalLocusBoundary();
        preflightOutcomesAndRejections();
        constraintsAndDescriptorRejections();
        System.out.println("PASS: Task47 pure assembly contracts " + assertions
                + " assertions");
    }

    private static void receiptAndCanonicalReplay() {
        System.out.println("TASK47_ASSEMBLY_RECEIPT_BEGIN");
        for (long seed : SEEDS) {
            ChallengeDescriptor descriptor = BoundedAssemblyRequest.descriptor(seed,
                    GenerationConstraints.unspecified());
            BoundedAssemblyRequest request = BoundedAssemblyRequest.forCanary(
                    descriptor);
            PortCompatibilityPreflight.Result preflight =
                    BoundedAssemblyPlan.preflight(request);
            require(BoundedAssemblyPlan.isCompatible(preflight),
                    "stock canary must be compatible");
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
            ComposedBlockContribution source = plan.getBlocks().get("source");
            ComposedBlockContribution load = plan.getBlocks().get("load");
            require(source != null && load != null, "both blocks resolved");
            require((source.getResistanceOhms() == 100.0
                    || source.getResistanceOhms() == 220.0)
                    && (load.getResistanceOhms() == 1000.0
                    || load.getResistanceOhms() == 2200.0),
                    "stock values are supported");
            require(plan.getBlocks().size() == 2, "two resolved contributions");
            require(plan.getNetAliases().size() == 5, "all local nets retained");
            require(!plan.netFor("source", "SUPPLY").equals(
                    plan.netFor("load", "SUPPLY")),
                    "same local SUPPLY names retain distinct nodes");
            require(plan.netForPort("source", "OUT").equals(
                    plan.netForPort("load", "IN")), "signal join is explicit");
            require(plan.netForPort("source", "RETURN").equals(
                    plan.netForPort("load", "RETURN")),
                    "return join is explicit");
            require(plan.getMergeProvenance().size() == 2,
                    "two explicit merge records");
            require(plan.idFor("source", EntityKind.COMPONENT, "R1").equals(
                    "tsj-block-v1/resistive-coupling@1/source/component/R1"),
                    "source component namespace");
            require(plan.idFor("load", EntityKind.PAD, "R1.1").equals(
                    "tsj-block-v1/resistive-coupling@1/load/pad/R1.1"),
                    "load pad namespace");
            require(plan.idFor("source", EntityKind.ENDPOINT, "R1_1").equals(
                    "tsj-block-v1/resistive-coupling@1/source/endpoint/R1_1"),
                    "source endpoint namespace");
            require(plan.idFor("source", EntityKind.NET, "SUPPLY").equals(
                    "tsj-block-v1/resistive-coupling@1/source/net/SUPPLY"),
                    "source net namespace");
            require(plan.idFor("source", EntityKind.ROLE, "rail").equals(
                    "tsj-block-v1/resistive-coupling@1/source/role/rail"),
                    "source role namespace");
            require(plan.idFor("source", EntityKind.PORT, "OUT").equals(
                    "tsj-block-v1/resistive-coupling@1/source/port/OUT"),
                    "source port namespace");
            String sourceSupply = plan.idFor("source", EntityKind.NET, "SUPPLY");
            String loadSupply = plan.idFor("load", EntityKind.NET, "SUPPLY");
            String sourceReturn = plan.idFor("source", EntityKind.NET, "RETURN");
            String loadReturn = plan.idFor("load", EntityKind.NET, "RETURN");
            require(sourceSupply.equals(plan.getNetAliases().get(sourceSupply)) &&
                    loadSupply.equals(plan.getNetAliases().get(loadSupply)) &&
                    !plan.getNetAliases().get(sourceSupply).equals(
                        plan.getNetAliases().get(loadSupply)),
                    "same-labelled SUPPLY nets remain unmerged aliases");
            require(plan.netFor("source", "RETURN").equals(
                    plan.getNetAliases().get(sourceReturn)) &&
                    plan.netFor("load", "RETURN").equals(
                    plan.getNetAliases().get(loadReturn)) &&
                    !sourceReturn.equals(loadReturn),
                    "both explicit RETURN references retain provenance");
            require(plan.getDecisionOwners().get("source-high-resistance")
                    .equals(plan.idFor("source", EntityKind.COMPONENT, "R1")),
                    "source fault owner is qualified");
            require(plan.getDecisionOwners().get("load-high-resistance")
                    .equals(plan.idFor("load", EntityKind.COMPONENT, "R1")),
                    "load fault owner is qualified");
            require(plan.getFaultBlockKey().equals("source")
                    || plan.getFaultBlockKey().equals("load"),
                    "selected fault has a block owner");
            require(plan.getFaultDecisionKey().equals(
                    plan.getFaultBlockKey() + "-high-resistance"),
                    "fault decision and owner agree");

            ArrayList<ElectricalBlockContract> reversedBlocks =
                    new ArrayList<ElectricalBlockContract>(request.getBlocks());
            Collections.reverse(reversedBlocks);
            ArrayList<ElectricalConnection> reversedConnections =
                    new ArrayList<ElectricalConnection>(request.getConnections());
            Collections.reverse(reversedConnections);
            BoundedAssemblyPlan replay = BoundedAssemblyPlan.resolve(
                    new BoundedAssemblyRequest(descriptor, reversedBlocks,
                            reversedConnections));
            require(plan.getSemanticSignature().equals(
                    replay.getSemanticSignature()),
                    "reversed declaration order replays the same semantics");
            require(ChallengeDescriptor.parse(descriptor.toCanonical()).equals(
                    descriptor), "full signed seed canonical round trip");

            double healthyCurrent = 5.0 / (source.getResistanceOhms()
                    + load.getResistanceOhms());
            verifyHealthy(source, 5.0, 5.0 - healthyCurrent
                    * source.getResistanceOhms(), healthyCurrent,
                    source.getResistanceOhms());
            verifyHealthy(load, healthyCurrent * load.getResistanceOhms(),
                    0.0, healthyCurrent, load.getResistanceOhms());
            System.out.println("seed=" + Long.toString(seed) + ";source-ohms="
                    + Double.toString(source.getResistanceOhms())
                    + ";load-ohms=" + Double.toString(load.getResistanceOhms())
                    + ";fault=" + plan.getFaultDecisionKey()
                    + ";descriptor=" + descriptor.toCanonical().replace('\n', '|'));
        }
        System.out.println("TASK47_ASSEMBLY_RECEIPT_END");
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
                    "source VALUES stream changed after unrelated draws");
            require(load.equals(NamedRandomStreams.select(streams.blockSeed(
                    "load", NamedRandomStreams.Concern.VALUES, 1,
                    "resistance"), Arrays.asList("r1000", "r2200"))),
                    "load VALUES stream changed after unrelated draws");
            require(fault.equals(NamedRandomStreams.select(streams.deviceSeed(
                    NamedRandomStreams.Concern.FAULT, 1, "selected-fault"),
                    Arrays.asList("source-high-resistance",
                            "load-high-resistance"))),
                    "FAULT stream changed after unrelated draws");
            require(source.equals(ResistiveBlockContributions.chooseValueKey(seed,
                    "source")) && load.equals(
                    ResistiveBlockContributions.chooseValueKey(seed, "load")) &&
                    fault.equals(ResistiveBlockContributions.chooseFaultDecision(seed)),
                    "provider selectors diverged from named streams");
        }
        assertions++;
    }

    private static void localRulesUseObservations() {
        ComposedBlockContribution source = ResistiveBlockContributions
                .createSource("source", 100.0);
        final double[] values = { 5.0, 4.5, 0.005, 100.0 };
        ComposedBlockContribution.Observation healthy = observation(values);
        source.verifyHealthy(healthy);
        require(source.observe(healthy)
                == ComposedBlockContribution.ObservationResult.CONDUCTING,
                "healthy observation conducts");
        final double[] low = { 5.0, 4.995, .00005, 100000.0 };
        require(source.observe(observation(low))
                == ComposedBlockContribution.ObservationResult.LOW_CURRENT,
                "high resistance observation is low current");
        final double[] invalid = { Double.NaN, 0.0, 0.0, 100.0 };
        require(source.observe(observation(invalid))
                == ComposedBlockContribution.ObservationResult.INVALID,
                "nonfinite observation is invalid");
        require(source.getInputRequirements().equals(
                Collections.singletonList("BOARD_POWER")),
                "input requirements are immutable and explicit");
        require(source.getRetestRequirements().equals(
                Collections.singletonList("STEADY_DC_POWERED")),
                "retest requirements are immutable and explicit");
        try {
            source.getInputRequirements().add("MUTATION");
            fail("input requirements unexpectedly mutable");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
    }

    private static void qualifiedPhysicalLocusBoundary() {
        final String owner = "tsj-block-v1/resistive-coupling@1/source/component/R1";
        require(GeneratedFaultLocus.componentInternal(owner).getOwnerId().equals(owner),
                "qualified physical fault owner retains every namespace byte");
        require(GeneratedFaultLocus.terminalAttachment(owner, "2").getOwnerId().equals(owner),
                "qualified terminal locus retains its component owner");
        require(GeneratedFaultLocus.componentInternal("R1").getOwnerId().equals("R1") &&
                GeneratedFaultLocus.terminalAttachment("C1", "+").getTerminalId().equals("+") &&
                GeneratedFaultLocus.traceSegment("TRACE_A").getPathId().equals("TRACE_A"),
                "legacy component, terminal and trace forms remain supported");
        for (String value : Arrays.asList("source/R1", "R1@1",
                owner.replace("tsj-block-v1", "tsj-block-v2"),
                owner.replace("@1/", "@0/"), owner.replace("@1/", "@01/"),
                owner.replace("@1/", "@2147483648/"),
                owner.replace("/component/", "/endpoint/"), owner + "/1",
                owner.replace("/source/", "//"), owner.replace("/R1", "/NODE_1"),
                "NODE_1", "COORD_1", "INDEX_1", "UUID_1", "SWITCH_1")) {
            boolean rejected = false;
            try { GeneratedFaultLocus.componentInternal(value); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "invalid or private physical identity accepted: " + value);
        }
        boolean terminalRejected = false;
        try { GeneratedFaultLocus.terminalAttachment("R1", owner); }
        catch (IllegalArgumentException expected) { terminalRejected = true; }
        require(terminalRejected, "qualified component ID accepted as a terminal ID");
        boolean pathRejected = false;
        try { GeneratedFaultLocus.traceSegment(owner); }
        catch (IllegalArgumentException expected) { pathRejected = true; }
        require(pathRejected, "qualified component ID accepted as a trace path ID");
    }

    private static ComposedBlockContribution.Observation observation(
            final double[] values) {
        return new ComposedBlockContribution.Observation() {
            @Override
            public double voltage(String endpoint) {
                return "R1_1".equals(endpoint) ? values[0] : values[1];
            }

            @Override
            public double current(String component) { return values[2]; }

            @Override
            public double resistance(String component) { return values[3]; }
        };
    }

    private static void verifyHealthy(ComposedBlockContribution contribution,
            final double firstVoltage, final double secondVoltage,
            final double current, final double resistance) {
        contribution.verifyHealthy(new ComposedBlockContribution.Observation() {
            @Override
            public double voltage(String endpoint) {
                return "R1_1".equals(endpoint) ? firstVoltage : secondVoltage;
            }

            @Override
            public double current(String component) { return current; }

            @Override
            public double resistance(String component) { return resistance; }
        });
        assertions++;
    }

    private static void preflightOutcomesAndRejections() {
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forCanary(0L);
        require(BoundedAssemblyPlan.preflight(request).getDecision()
                == PortCompatibilityPreflight.Decision.COMPATIBLE,
                "compatible preflight outcome");

        List<ElectricalBlockContract> blocks = request.getBlocks();
        ElectricalConnection malformed = new ElectricalConnection("signal",
                Arrays.asList(new ElectricalConnection.PortRef("source", "MISSING"),
                        new ElectricalConnection.PortRef("load", "IN")));
        expectDecision(PortCompatibilityPreflight.Decision.MALFORMED,
                BoundedAssemblyPlan.preflight(new BoundedAssemblyRequest(
                        request.getDescriptor(), blocks,
                        Arrays.asList(malformed, request.getConnections().get(0)))));
        ElectricalConnection incompatible = new ElectricalConnection("signal",
                Arrays.asList(new ElectricalConnection.PortRef("source", "RETURN"),
                        new ElectricalConnection.PortRef("load", "IN")));
        expectDecision(PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                BoundedAssemblyPlan.preflight(new BoundedAssemblyRequest(
                        request.getDescriptor(), blocks,
                        Arrays.asList(incompatible, request.getConnections().get(0)))));

        ElectricalBlockContract source = blocks.get(1).getDescriptor()
                .getInstanceKey().equals("source") ? blocks.get(1) : blocks.get(0);
        ElectricalBlockContract load = blocks.get(1).getDescriptor()
                .getInstanceKey().equals("load") ? blocks.get(1) : blocks.get(0);
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
        List<ElectricalBlockContract> uncertainBlocks = sourceFirst(uncertainSource,
                load);
        expectDecision(PortCompatibilityPreflight.Decision.INSUFFICIENT_INFORMATION,
                BoundedAssemblyPlan.preflight(new BoundedAssemblyRequest(
                        request.getDescriptor(), uncertainBlocks,
                        request.getConnections())));
        try {
            BoundedAssemblyPlan.resolve(new BoundedAssemblyRequest(
                    request.getDescriptor(), uncertainBlocks, request.getConnections()));
            fail("incompatible preflight unexpectedly resolved");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
        try {
            BoundedAssemblyPlan.resolve(new BoundedAssemblyRequest(
                    request.getDescriptor(), blocks,
                    Arrays.asList(incompatible, request.getConnections().get(0))));
            fail("changed wiring unexpectedly resolved");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static List<ElectricalBlockContract> sourceFirst(
            ElectricalBlockContract source, ElectricalBlockContract load) {
        return Arrays.asList(source, load);
    }

    private static void constraintsAndDescriptorRejections() {
        GenerationConstraints badBlocks = new GenerationConstraints(
                GenerationConstraints.VERSION,
                Arrays.asList(new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.BLOCKS,
                        GenerationConstraints.CountRange.exact(1))),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        expectResolveFailure(BoundedAssemblyRequest.descriptor(0L, badBlocks));

        GenerationConstraints unsupportedDimension = new GenerationConstraints(
                GenerationConstraints.VERSION,
                Arrays.asList(new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.DIAGNOSTIC_DEPTH,
                        GenerationConstraints.CountRange.exact(1))),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        expectResolveFailure(BoundedAssemblyRequest.descriptor(0L,
                unsupportedDimension));

        GenerationConstraints requiredTemporal = new GenerationConstraints(
                GenerationConstraints.VERSION, Collections.<GenerationConstraints.CountRequest>emptyList(),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.REQUIRED,
                GenerationConstraints.AllowedInstruments.unspecified());
        expectResolveFailure(BoundedAssemblyRequest.descriptor(0L,
                requiredTemporal));

        ChallengeDescriptor unsupported = new ChallengeDescriptor(
                ChallengeDescriptor.SCHEMA_VERSION, 0L,
                new ChallengeDescriptor.VersionedId("legacy-leaf", 1),
                new ChallengeDescriptor.VersionedId(BoundedAssemblyRequest.INTENT_ID, 1),
                new ChallengeDescriptor.VersionedId(BoundedAssemblyRequest.PROFILE_ID, 1),
                new PcbGeometryContractVersion(3),
                GenerationConstraints.unspecified());
        expectResolveFailure(unsupported);
    }

    private static void expectResolveFailure(ChallengeDescriptor descriptor) {
        try {
            BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forCanary(descriptor));
            fail("unsupported request unexpectedly resolved");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void expectDecision(PortCompatibilityPreflight.Decision expected,
            PortCompatibilityPreflight.Result actual) {
        require(actual.getDecision() == expected,
                "expected preflight " + expected + ", got "
                        + actual.getDecision());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
