package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure acceptance checks for the current resolved controlled load. This keeps
 * the useful value/equation and seed-isolation coverage without preserving a
 * separate historical provider or generator interpretation.
 */
public final class Task49ValueSynthesisContractTest {
    private static int assertions;
    private static final long[] SEEDS = { 0L, 1L, 2L, 3L,
            Long.MIN_VALUE, Long.MAX_VALUE, 9007199254740993L,
            -9007199254740993L };

    private Task49ValueSynthesisContractTest() { }

    public static void main(String[] args) {
        System.out.println("CURRENT_VALUE_RECEIPT_BEGIN");
        for (long seed : SEEDS) verifySeed(seed);
        System.out.println("CURRENT_VALUE_RECEIPT_END");
        verifyReorderAndReplay();
        verifyCatalogBoundary();
        verifyIntentBoundary();
        verifyEquationNegatives();
        verifyCandidatePermutationAndImmutability();
        verifyNamedValuesIsolation();
        verifyTypedRequestFacts();
        verifyProviderDeclarationBoundary();
        System.out.println("PASS: current value synthesis " + assertions
                + " assertions");
    }

    private static void verifySeed(long seed) {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(seed);
        ChallengeDescriptor descriptor = request.getDescriptor();
        require(descriptor.getRootSeed() == seed &&
                descriptor.getGenerator().getVersion() ==
                    BoundedAssemblyRequest.GENERATOR_VERSION &&
                ChallengeDescriptor.parse(descriptor.toCanonical()).equals(descriptor),
                "current descriptor retains signed seed and canonical round trip");
        require(request.getBlocks().size() == 2 &&
                request.getConnections().size() == 4 &&
                request.getDeviceAdapters().size() == 2,
                "typed controlled composition shape is retained");

        ComposedBlockContribution declaredLoad =
                ControlledIndicatorBlockContributions.load().create("load");
        require(declaredLoad.getProviderVersion() ==
                ControlledIndicatorBlockContributions.LOAD_VERSION &&
                declaredLoad.getResistors().isEmpty() &&
                declaredLoad.getLedRecipes().size() == 1,
                "load declaration carries its typed intent before pure resolution");

        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                plan.getResolvedLoadRecipe();
        require(plan.isControlledIndicator() && recipe != null,
                "current plan resolves one catalog-backed recipe");
        require(containsCandidate(recipe.getCatalogEntryId()),
                "selected recipe belongs to the admissible catalog");
        require(recipe.getCandidate().getCatalogEntry() != null &&
                recipe.getCandidate().getCatalogEntry().getId().equals(
                    recipe.getCatalogEntryId()),
                "candidate exposes its current catalog entry");
        require(recipe.getTolerancePercent() > 0.0 &&
                recipe.getPackageId().equals(ControlledIndicatorValueSynthesis.PACKAGE_ID),
                "recipe retains catalog tolerance and package identity");
        require(recipe.getResistanceMinimumOhms() < recipe.getResistanceOhms() &&
                recipe.getResistanceMaximumOhms() > recipe.getResistanceOhms() &&
                recipe.getMinimumCurrentAmps() >=
                    recipe.getIntent().getTargetMinimumCurrentAmps() &&
                recipe.getMaximumCurrentAmps() <=
                    recipe.getIntent().getTypedDemandAmps() &&
                recipe.getGuardedPowerWatts() > 0.0,
                "recipe receipt satisfies bounded electrical equations");
        ComposedBlockContribution.ResistorRecipe load =
                plan.getLoad().getResistor("RLOAD");
        require(load != null && load.getResolvedRecipe() == recipe &&
                load.getResistanceOhms() == recipe.getResistanceOhms() &&
                load.isMutable(),
                "resolved recipe owns the mutable RLOAD contribution");
        /*
         * Keep one compact, independently checkable observation stream.  The
         * Python oracle consumes only these rows; it does not import the Java
         * resolver or use the plan's candidate list as its expected answer.
         * The descriptor is last because its constraints field contains
         * semicolons.  Replace its line separators with the canonical report
         * separator before writing the row.
         */
        System.out.println("seed=" + Long.toString(seed) +
                ";catalog=" + recipe.getCatalogEntryId() +
                ";resistance=" + Double.toString(recipe.getResistanceOhms()) +
                ";rmin=" + Double.toString(recipe.getResistanceMinimumOhms()) +
                ";rmax=" + Double.toString(recipe.getResistanceMaximumOhms()) +
                ";imin=" + Double.toString(recipe.getMinimumCurrentAmps()) +
                ";imax=" + Double.toString(recipe.getMaximumCurrentAmps()) +
                ";pguard=" + Double.toString(recipe.getGuardedPowerWatts()) +
                ";fault=" + plan.getFaultDecisionKey() +
                ";descriptor=" + descriptor.toCanonical().replace('\n', '|'));
    }

    private static void verifyReorderAndReplay() {
        BoundedAssemblyRequest original =
                BoundedAssemblyRequest.forControlledIndicator(
                        9007199254740993L);
        ArrayList<ElectricalBlockContract> blocks =
                new ArrayList<ElectricalBlockContract>(original.getBlocks());
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(original.getConnections());
        ArrayList<DeviceAdapterContract> adapters =
                new ArrayList<DeviceAdapterContract>(original.getDeviceAdapters());
        Collections.reverse(blocks);
        Collections.reverse(connections);
        Collections.reverse(adapters);
        BoundedAssemblyPlan first = BoundedAssemblyPlan.resolve(original);
        BoundedAssemblyPlan reordered = BoundedAssemblyPlan.resolve(
                new BoundedAssemblyRequest(original.getDescriptor(), blocks,
                        connections, adapters));
        BoundedAssemblyPlan replay = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(
                        ChallengeDescriptor.parse(original.getDescriptor()
                                .toCanonical())));
        require(first.getSemanticSignature().equals(reordered.getSemanticSignature()) &&
                first.getSemanticSignature().equals(replay.getSemanticSignature()),
                "reordered and canonical replay preserve current semantics");
        require(first.getResolvedLoadRecipe().getCatalogEntryId().equals(
                reordered.getResolvedLoadRecipe().getCatalogEntryId()) &&
                first.getResolvedLoadRecipe().getCatalogEntryId().equals(
                replay.getResolvedLoadRecipe().getCatalogEntryId()),
                "reordered and replayed requests preserve selected value");
    }

    private static void verifyCatalogBoundary() {
        ControlledIndicatorValueSynthesis.Intent intent =
                ControlledIndicatorValueSynthesis.defaultIntent();
        List<ResistorCatalogEntry> entries = new ArrayList<ResistorCatalogEntry>();
        entries.add(new ResistorCatalogEntry("R_CATALOG_270", 270.0));
        entries.add(new ResistorCatalogEntry("R_CATALOG_330", 330.0, .22));
        entries.add(new ResistorCatalogEntry("R_CATALOG_270", 270.0));
        boolean duplicateRejected = false;
        try {
            ControlledIndicatorValueSynthesis.validCandidates(entries, intent);
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        require(duplicateRejected, "duplicate catalog IDs are rejected");

        entries = new ArrayList<ResistorCatalogEntry>();
        entries.add(new ResistorCatalogEntry("R_CATALOG_270", 270.0, .99));
        entries.add(new ResistorCatalogEntry("R_CATALOG_UNKNOWN", 330.0));
        require(ControlledIndicatorValueSynthesis.validCandidates(entries, intent)
                .isEmpty(), "forged catalog metadata is not admitted");

        List<ControlledIndicatorValueSynthesis.Candidate> valid =
                ControlledIndicatorValueSynthesis.validCandidates(intent);
        require(valid.size() >= 2 &&
                valid.get(0).getId().compareTo(valid.get(1).getId()) < 0,
                "valid candidate pool is sorted by canonical catalog ID");
    }

    private static void verifyIntentBoundary() {
        final ControlledIndicatorValueSynthesis.Intent baseline =
                ControlledIndicatorValueSynthesis.defaultIntent();
        boolean rejected = false;
        try {
            new ControlledIndicatorValueSynthesis.Intent(
                    baseline.getSourceMinimumVolts(), baseline.getSourceMaximumVolts(),
                    baseline.getLoadAcceptanceMinimumVolts(),
                    baseline.getLoadAcceptanceMaximumVolts(), baseline.getSinkMinimumVolts(),
                    baseline.getSinkMaximumVolts(), baseline.getSinkCapacityAmps(),
                    baseline.getTypedDemandAmps(), baseline.getTypedDemandAmps() + .001,
                    baseline.getLedMinimumForwardVolts(), baseline.getLedMaximumForwardVolts(),
                    baseline.getModelToleranceFraction(), baseline.getPowerHeadroomFactor(),
                    baseline.getSinkHeadroomFactor(), baseline.getModelId(),
                    baseline.getPackageId());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "typed demand below target is rejected");
        rejected = false;
        try {
            ControlledIndicatorValueSynthesis.resolve(0L, null);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "missing controlled intent is rejected");
        final double[] invalidTolerances = { Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 0.0, -0.01, 1.0, 1.01 };
        for (final double tolerance : invalidTolerances) {
            expectRejected(new Rejection() {
                public void run() {
                    new ControlledIndicatorValueSynthesis.Intent(
                            baseline.getSourceMinimumVolts(), baseline.getSourceMaximumVolts(),
                            baseline.getLoadAcceptanceMinimumVolts(),
                            baseline.getLoadAcceptanceMaximumVolts(),
                            baseline.getSinkMinimumVolts(), baseline.getSinkMaximumVolts(),
                            baseline.getSinkCapacityAmps(), baseline.getTypedDemandAmps(),
                            baseline.getTargetMinimumCurrentAmps(),
                            baseline.getLedMinimumForwardVolts(),
                            baseline.getLedMaximumForwardVolts(), tolerance,
                            baseline.getPowerHeadroomFactor(),
                            baseline.getSinkHeadroomFactor(), baseline.getModelId(),
                            baseline.getPackageId());
                }
            }, "invalid tolerance input");
        }
        expectRejected(new Rejection() {
            public void run() {
                new ResistorCatalogEntry("R_BAD_NAN", Double.NaN);
            }
        }, "nonfinite catalog resistance input");
        expectRejected(new Rejection() {
            public void run() {
                new ResistorCatalogEntry("R_BAD_INF", Double.POSITIVE_INFINITY);
            }
        }, "infinite catalog resistance input");
    }

    private static void verifyEquationNegatives() {
        ControlledIndicatorValueSynthesis.Intent baseline =
                ControlledIndicatorValueSynthesis.defaultIntent();
        ControlledIndicatorValueSynthesis.Intent currentFloor =
                new ControlledIndicatorValueSynthesis.Intent(4.75, 5.25, 4.5, 5.5,
                        0.0, 0.8, 0.020, 0.006, 0.005, 1.6, 2.0, 0.05,
                        2.0, 1.25, baseline.getModelId(), baseline.getPackageId());
        require(ControlledIndicatorValueSynthesis.validCandidates(currentFloor).isEmpty(),
                "current-floor equation rejects every catalog value");
        ControlledIndicatorValueSynthesis.Intent currentCeiling =
                new ControlledIndicatorValueSynthesis.Intent(4.75, 5.25, 4.5, 5.5,
                        0.0, 0.8, 0.020, 0.010, 0.005, 1.6, 2.0, 0.05,
                        2.0, 1.25, baseline.getModelId(), baseline.getPackageId());
        require(ControlledIndicatorValueSynthesis.validCandidates(currentCeiling).isEmpty(),
                "current-ceiling equation rejects every catalog value");
        ControlledIndicatorValueSynthesis.Intent powerMargin =
                new ControlledIndicatorValueSynthesis.Intent(4.75, 5.25, 4.5, 5.5,
                        0.0, 0.8, 0.020, 0.016, 0.005, 1.6, 2.0, 0.05,
                        3.0, 1.25, baseline.getModelId(), baseline.getPackageId());
        require(ControlledIndicatorValueSynthesis.validCandidates(powerMargin).isEmpty(),
                "power-headroom equation rejects every catalog value");
        expectRejected(new Rejection() {
            public void run() {
                ControlledIndicatorValueSynthesis.resolve(0L,
                        new ControlledIndicatorValueSynthesis.Intent(4.75, 5.25,
                            4.5, 5.5, 0.0, 0.8, 0.020, 0.010, 0.005,
                            1.6, 2.0, 0.05, 2.0, 1.25, "default-led",
                            "AXIAL_RESISTOR"));
            }
        }, "empty candidate pool fails before selection");
    }

    private static void verifyCandidatePermutationAndImmutability() {
        ControlledIndicatorValueSynthesis.Intent intent =
                ControlledIndicatorValueSynthesis.defaultIntent();
        ArrayList<ResistorCatalogEntry> canonical = new ArrayList<ResistorCatalogEntry>(
                new ResistorReplacementCatalog().getEntries());
        ArrayList<ResistorCatalogEntry> reversed =
                new ArrayList<ResistorCatalogEntry>(canonical);
        Collections.reverse(reversed);
        for (long seed : SEEDS) {
            ControlledIndicatorValueSynthesis.ResolvedRecipe first =
                    ControlledIndicatorValueSynthesis.resolve(seed, canonical, intent);
            ControlledIndicatorValueSynthesis.ResolvedRecipe second =
                    ControlledIndicatorValueSynthesis.resolve(seed, reversed, intent);
            require(first.getCatalogEntryId().equals(second.getCatalogEntryId()),
                    "candidate permutation changed selection");
        }
        List<ControlledIndicatorValueSynthesis.Candidate> valid =
                ControlledIndicatorValueSynthesis.validCandidates(intent);
        boolean immutable = false;
        try {
            valid.add(valid.get(0));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        require(immutable, "candidate collection is immutable");
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                ControlledIndicatorValueSynthesis.resolve(2L, intent);
        double resistance = recipe.getResistanceOhms();
        canonical.clear();
        require(recipe.getResistanceOhms() == resistance &&
                recipe.getCandidate().getCatalogEntry() != null &&
                recipe.getCandidate().getCatalogEntry().getId().equals(
                    recipe.getCatalogEntryId()) &&
                recipe.getIntent().getSourceMinimumVolts() ==
                    intent.getSourceMinimumVolts(),
                "resolved candidate and intent remain immutable");
        require(ControlledIndicatorValueSynthesis.VALUES_REVISION == 1 &&
                ControlledIndicatorValueSynthesis.BLOCK_KEY.equals("load") &&
                ControlledIndicatorValueSynthesis.VALUES_KEY.equals("resistance"),
                "current value stream tuple remains named and versioned");
    }

    private static void verifyNamedValuesIsolation() {
        ControlledIndicatorValueSynthesis.Intent intent =
                ControlledIndicatorValueSynthesis.defaultIntent();
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(2L);
        String intentId = request.getDescriptor().getDeviceIntent().getId();
        int intentVersion = request.getDescriptor().getDeviceIntent().getVersion();
        List<ControlledIndicatorValueSynthesis.Candidate> candidates =
                ControlledIndicatorValueSynthesis.validCandidates(intent);
        ArrayList<String> ids = new ArrayList<String>();
        for (ControlledIndicatorValueSynthesis.Candidate candidate : candidates)
            ids.add(candidate.getId());
        NamedRandomStreams streams = new NamedRandomStreams(1, 2L, intentId,
                intentVersion);
        long loadSeedRevision1 = streams.blockSeed("load",
                NamedRandomStreams.Concern.VALUES,
                ControlledIndicatorValueSynthesis.VALUES_REVISION,
                ControlledIndicatorValueSynthesis.VALUES_KEY);
        long loadSeedRevision2 = streams.blockSeed("load",
                NamedRandomStreams.Concern.VALUES, 2,
                ControlledIndicatorValueSynthesis.VALUES_KEY);
        String selectedBefore = NamedRandomStreams.select(loadSeedRevision1, ids);
        String selectedRevision2 = NamedRandomStreams.select(loadSeedRevision2, ids);
        require(loadSeedRevision1 != loadSeedRevision2 &&
                ids.contains(selectedRevision2) &&
                selectedBefore.equals(ControlledIndicatorValueSynthesis.resolve(2L, intent)
                    .getCatalogEntryId()),
                "load values stream remains independently seeded");
        String unrelatedBefore = unrelatedConcernFingerprint(streams);
        streams.openBlock("load", NamedRandomStreams.Concern.VALUES, 2,
                ControlledIndicatorValueSynthesis.VALUES_KEY).nextLong();
        streams.blockSeed("optional-support", NamedRandomStreams.Concern.VALUES,
                ControlledIndicatorValueSynthesis.VALUES_REVISION, "resistance");
        String unrelatedAfter = unrelatedConcernFingerprint(streams);
        require(unrelatedBefore.equals(unrelatedAfter),
                "load value stream insertion does not shift unrelated concerns");
        require(ControlledIndicatorValueSynthesis.POLICY_ID.equals(
                ControlledIndicatorValueSynthesis.resolve(2L, intent).getPolicyId()),
                "current selection retains its policy revision");
    }

    private static String unrelatedConcernFingerprint(NamedRandomStreams streams) {
        NamedRandomStreams.Concern[] concerns = {
                NamedRandomStreams.Concern.FAULT,
                NamedRandomStreams.Concern.SCENARIO,
                NamedRandomStreams.Concern.PLACEMENT,
                NamedRandomStreams.Concern.ROUTING,
                NamedRandomStreams.Concern.PRESENTATION };
        String[] keys = { "selected-fault", "scenario-choice", "geometry",
                "copper", "nameplate" };
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < concerns.length; index++) {
            NamedRandomStreams.Stream stream = streams.openDevice(concerns[index],
                    1, keys[index]);
            result.append(stream.nextLong()).append(':').append(stream.nextLong())
                    .append(';');
        }
        return result.toString();
    }

    private static void verifyTypedRequestFacts() {
        final BoundedAssemblyRequest original =
                BoundedAssemblyRequest.forControlledIndicator(2L);
        require(ControlledIndicatorValueSynthesis.fromRequest(original)
                .getSinkMaximumVolts() == 0.8,
                "production resolver consumes typed sink clamp");

        ElectricalBlockContract driver = findBlock(original, "driver");
        ElectricalPortContract switched = driver.getPorts().get("SWITCHED_SINK");
        ElectricalPortContract malformedSink = copyPort(switched,
                ElectricalPortContract.Range.known(0.0, 0.7),
                switched.getAllowedVoltage());
        final BoundedAssemblyRequest changedSink = replaceBlock(original,
                copyBlockWithPort(driver, malformedSink));
        expectRejected(new Rejection() {
            public void run() { ControlledIndicatorValueSynthesis.fromRequest(changedSink); }
        }, "contradictory typed sink facts");

        ElectricalBlockContract load = findBlock(original, "load");
        ElectricalPortContract supply = load.getPorts().get("SUPPLY");
        ElectricalPortContract unknownDemand = copyPort(supply,
                supply.getGuaranteedVoltage(), supply.getAllowedVoltage(),
                ElectricalPortContract.Scalar.unknown());
        final BoundedAssemblyRequest changedDemand = replaceBlock(original,
                copyBlockWithPort(load, unknownDemand));
        expectRejected(new Rejection() {
            public void run() { ControlledIndicatorValueSynthesis.fromRequest(changedDemand); }
        }, "unknown typed load demand");

        ElectricalConnection switchedConnection = findConnection(original,
                ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID);
        SwitchedLowSideContract contradictory = new SwitchedLowSideContract(
                switchedConnection.getSwitchedLowSideContract().getSinkPort(),
                switchedConnection.getSwitchedLowSideContract().getLoadPort(),
                switchedConnection.getSwitchedLowSideContract().getSupplyPort(),
                switchedConnection.getSwitchedLowSideContract().getControlPort(),
                switchedConnection.getSwitchedLowSideContract().getReturnPorts(),
                "RETURN", "shared-return", true, 0.019, 0.016);
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(original.getConnections());
        connections.remove(switchedConnection);
        connections.add(new ElectricalConnection(switchedConnection.getId(),
                switchedConnection.getPorts(), contradictory));
        final BoundedAssemblyRequest changedRelation = new BoundedAssemblyRequest(
                original.getDescriptor(), original.getBlocks(), connections,
                original.getDeviceAdapters());
        expectRejected(new Rejection() {
            public void run() { ControlledIndicatorValueSynthesis.fromRequest(changedRelation); }
        }, "contradictory switched relation capacity");
    }

    private static void verifyProviderDeclarationBoundary() {
        expectRejected(new Rejection() {
            public void run() {
                ControlledIndicatorBlockContributions.resolve(
                        ControlledIndicatorBlockContributions.LOAD_TYPE_ID, 99);
            }
        }, "unknown controlled provider version");
        expectRejected(new Rejection() {
            public void run() {
                ControlledIndicatorBlockContributions.resolve(
                        ControlledIndicatorBlockContributions.DRIVER_TYPE_ID,
                        ControlledIndicatorBlockContributions.LOAD_VERSION);
            }
        }, "mismatched provider/version declaration");
        expectRejected(new Rejection() {
            public void run() {
                ControlledIndicatorBlockContributions.resolve(
                        ControlledIndicatorBlockContributions.LOAD_TYPE_ID,
                        ControlledIndicatorBlockContributions.VERSION);
            }
        }, "load cannot use the driver provider version");
    }

    private static boolean containsCandidate(String id) {
        if (id == null) return false;
        for (ControlledIndicatorValueSynthesis.Candidate candidate :
                ControlledIndicatorValueSynthesis.validCandidates(
                        ControlledIndicatorValueSynthesis.defaultIntent())) {
            if (id.equals(candidate.getId())) return true;
        }
        return false;
    }

    private static ElectricalBlockContract findBlock(BoundedAssemblyRequest request,
            String key) {
        for (ElectricalBlockContract block : request.getBlocks())
            if (key.equals(block.getDescriptor().getInstanceKey())) return block;
        throw new AssertionError("missing block " + key);
    }

    private static ElectricalConnection findConnection(BoundedAssemblyRequest request,
            String id) {
        for (ElectricalConnection connection : request.getConnections())
            if (id.equals(connection.getId())) return connection;
        throw new AssertionError("missing connection " + id);
    }

    private static BoundedAssemblyRequest replaceBlock(BoundedAssemblyRequest request,
            ElectricalBlockContract replacement) {
        ArrayList<ElectricalBlockContract> blocks =
                new ArrayList<ElectricalBlockContract>(request.getBlocks());
        for (int index = 0; index < blocks.size(); index++)
            if (blocks.get(index).getDescriptor().getInstanceKey().equals(
                    replacement.getDescriptor().getInstanceKey())) {
                blocks.set(index, replacement);
                return new BoundedAssemblyRequest(request.getDescriptor(), blocks,
                        request.getConnections(), request.getDeviceAdapters());
            }
        throw new AssertionError("replacement block was not declared");
    }

    private static ElectricalBlockContract copyBlockWithPort(
            ElectricalBlockContract source, ElectricalPortContract replacement) {
        ArrayList<ElectricalPortContract> ports =
                new ArrayList<ElectricalPortContract>(source.getPorts().values());
        for (int index = 0; index < ports.size(); index++)
            if (ports.get(index).getId().equals(replacement.getId()))
                ports.set(index, replacement);
        return new ElectricalBlockContract(source.getDescriptor(), ports,
                source.getAdapters().values());
    }

    private static ElectricalPortContract copyPort(ElectricalPortContract source,
            ElectricalPortContract.Range guaranteed,
            ElectricalPortContract.Range allowed) {
        return copyPort(source, guaranteed, allowed, source.getDemandAmps());
    }

    private static ElectricalPortContract copyPort(ElectricalPortContract source,
            ElectricalPortContract.Range guaranteed,
            ElectricalPortContract.Range allowed,
            ElectricalPortContract.Scalar demand) {
        return new ElectricalPortContract(source.getId(), source.getRole(),
                source.getDirection(), source.getBehavior(), source.getDrive(),
                source.getDomain(), source.getNominalVoltage(), guaranteed, allowed,
                source.getLoading(), source.getCapacityAmps(), demand,
                source.getDigital(), source.getMergePolicy(),
                source.getAccessRequirement(), source.getAccessProvision());
    }

    private interface Rejection { void run(); }

    private static void expectRejected(Rejection action, String label) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, label + " was admitted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
