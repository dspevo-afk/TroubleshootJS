package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, bounded value synthesis for the Task 49 controlled-indicator load.
 *
 * <p>The existing resistor replacement catalog is the only source of nominal
 * values and ratings.  This class evaluates catalog rows against the typed
 * load intent and returns one immutable recipe.  It has no CircuitJS or board
 * dependency; the solver remains the final healthy-behaviour authority.</p>
 */
final class ControlledIndicatorValueSynthesis {
    static final String POLICY_ID = "controlled-led-load-e12@1";
    static final String PACKAGE_ID = "AXIAL_RESISTOR";
    static final String BLOCK_KEY = "load";
    static final int VALUES_REVISION = 1;
    static final String VALUES_KEY = "resistance";
    static final String MODEL_ID = "default-led";

    // These are policy inputs.  Electrical operating facts are read from the
    // request's typed ports and switched relation below; keeping them here
    // would create a second authority for the interface contract.
    static final double TARGET_MINIMUM_CURRENT_AMPS = 0.005;
    static final double LED_MINIMUM_FORWARD_VOLTS = 1.6;
    static final double LED_MAXIMUM_FORWARD_VOLTS = 2.0;
    static final double REQUIRED_TOLERANCE_FRACTION = 0.05;
    static final double POWER_HEADROOM_FACTOR = 2.0;
    static final double SINK_HEADROOM_FACTOR = 1.25;

    private ControlledIndicatorValueSynthesis() { }

    /** Immutable typed design intent for one controlled LED load. */
    static final class Intent {
        private final double sourceMinimumVolts;
        private final double sourceMaximumVolts;
        private final double loadAcceptanceMinimumVolts;
        private final double loadAcceptanceMaximumVolts;
        private final double sinkMinimumVolts;
        private final double sinkMaximumVolts;
        private final double sinkCapacityAmps;
        private final double typedDemandAmps;
        private final double targetMinimumCurrentAmps;
        private final double ledMinimumForwardVolts;
        private final double ledMaximumForwardVolts;
        private final double modelToleranceFraction;
        private final double powerHeadroomFactor;
        private final double sinkHeadroomFactor;
        private final String modelId;
        private final String packageId;

        Intent(double sourceMinimumVolts, double sourceMaximumVolts,
                double loadAcceptanceMinimumVolts,
                double loadAcceptanceMaximumVolts, double sinkMinimumVolts,
                double sinkMaximumVolts, double sinkCapacityAmps,
                double typedDemandAmps, double targetMinimumCurrentAmps,
                double ledMinimumForwardVolts, double ledMaximumForwardVolts,
                double modelToleranceFraction, double powerHeadroomFactor,
                double sinkHeadroomFactor, String modelId, String packageId) {
            requireFinitePositive(sourceMinimumVolts, "sourceMinimumVolts");
            requireFinitePositive(sourceMaximumVolts, "sourceMaximumVolts");
            requireFinitePositive(loadAcceptanceMinimumVolts,
                    "loadAcceptanceMinimumVolts");
            requireFinitePositive(loadAcceptanceMaximumVolts,
                    "loadAcceptanceMaximumVolts");
            requireFinite(sinkMinimumVolts, "sinkMinimumVolts");
            requireFinite(sinkMaximumVolts, "sinkMaximumVolts");
            requireFinitePositive(sinkCapacityAmps, "sinkCapacityAmps");
            requireFinitePositive(typedDemandAmps, "typedDemandAmps");
            requireFinitePositive(targetMinimumCurrentAmps,
                    "targetMinimumCurrentAmps");
            requireFinitePositive(ledMinimumForwardVolts,
                    "ledMinimumForwardVolts");
            requireFinitePositive(ledMaximumForwardVolts,
                    "ledMaximumForwardVolts");
            requireFinitePositive(modelToleranceFraction,
                    "modelToleranceFraction");
            requireFinitePositive(powerHeadroomFactor, "powerHeadroomFactor");
            requireFinitePositive(sinkHeadroomFactor, "sinkHeadroomFactor");
            if (sourceMinimumVolts > sourceMaximumVolts ||
                    loadAcceptanceMinimumVolts > loadAcceptanceMaximumVolts ||
                    sinkMinimumVolts > sinkMaximumVolts ||
                    sinkMinimumVolts < 0.0 || sinkMaximumVolts < 0.0 ||
                    ledMinimumForwardVolts > ledMaximumForwardVolts ||
                    targetMinimumCurrentAmps > typedDemandAmps ||
                    sourceMinimumVolts < loadAcceptanceMinimumVolts ||
                    sourceMaximumVolts > loadAcceptanceMaximumVolts ||
                    modelToleranceFraction >= 1.0 ||
                    sinkHeadroomFactor < 1.0 || powerHeadroomFactor < 1.0)
                throw new IllegalArgumentException("Malformed controlled LED intent");
            this.sourceMinimumVolts = sourceMinimumVolts;
            this.sourceMaximumVolts = sourceMaximumVolts;
            this.loadAcceptanceMinimumVolts = loadAcceptanceMinimumVolts;
            this.loadAcceptanceMaximumVolts = loadAcceptanceMaximumVolts;
            this.sinkMinimumVolts = sinkMinimumVolts;
            this.sinkMaximumVolts = sinkMaximumVolts;
            this.sinkCapacityAmps = sinkCapacityAmps;
            this.typedDemandAmps = typedDemandAmps;
            this.targetMinimumCurrentAmps = targetMinimumCurrentAmps;
            this.ledMinimumForwardVolts = ledMinimumForwardVolts;
            this.ledMaximumForwardVolts = ledMaximumForwardVolts;
            this.modelToleranceFraction = modelToleranceFraction;
            this.powerHeadroomFactor = powerHeadroomFactor;
            this.sinkHeadroomFactor = sinkHeadroomFactor;
            this.modelId = requiredId(modelId, "modelId");
            this.packageId = requiredId(packageId, "packageId");
            if (!MODEL_ID.equals(this.modelId))
                throw new IllegalArgumentException("Unsupported controlled LED model");
            if (!PACKAGE_ID.equals(this.packageId))
                throw new IllegalArgumentException("Unsupported controlled LED package");
        }

        /**
         * Build the canonical standalone-test intent from the same typed
         * providers used by the production request factory.  This method is
         * deliberately independent of descriptor construction so the
         * provider/default-intent path cannot recurse.
         */
        static Intent controlledIndicator() {
            SwitchedLowSideContract switched =
                    SwitchedLowSideContract.forControlledIndicator();
            ElectricalBlockContract driver = ControlledIndicatorBlockContributions
                    .driver().create("driver").getElectricalContract();
            ElectricalBlockContract load = ControlledIndicatorBlockContributions
                    .load().create("load").getElectricalContract();
            DeviceAdapterContract power = DeviceAdapterContract.power();
            return fromTypedFacts(switched,
                    requirePort(power.getElectricalContract(),
                            DeviceAdapterContract.POWER_OUTPUT_PORT_ID),
                    requirePort(load, "SUPPLY"),
                    requirePort(load, "SWITCHED_LOAD"),
                    requirePort(driver, "SWITCHED_SINK"));
        }

        double getSourceMinimumVolts() { return sourceMinimumVolts; }
        double getSourceMaximumVolts() { return sourceMaximumVolts; }
        double getLoadAcceptanceMinimumVolts() { return loadAcceptanceMinimumVolts; }
        double getLoadAcceptanceMaximumVolts() { return loadAcceptanceMaximumVolts; }
        double getSinkMinimumVolts() { return sinkMinimumVolts; }
        double getSinkMaximumVolts() { return sinkMaximumVolts; }
        double getSinkCapacityAmps() { return sinkCapacityAmps; }
        double getTypedDemandAmps() { return typedDemandAmps; }
        double getTargetMinimumCurrentAmps() { return targetMinimumCurrentAmps; }
        double getLedMinimumForwardVolts() { return ledMinimumForwardVolts; }
        double getLedMaximumForwardVolts() { return ledMaximumForwardVolts; }
        double getModelToleranceFraction() { return modelToleranceFraction; }
        double getRequiredToleranceFraction() { return modelToleranceFraction; }
        double getPowerHeadroomFactor() { return powerHeadroomFactor; }
        double getSinkHeadroomFactor() { return sinkHeadroomFactor; }
        String getModelId() { return modelId; }
        String getPackageId() { return packageId; }
        String getPackageIdentity() { return packageId; }
    }

    /** Immutable catalog-backed candidate. */
    static final class Candidate {
        private final ResistorCatalogEntry catalogEntry;
        private final String packageId;

        Candidate(ResistorCatalogEntry catalogEntry, String packageId) {
            if (catalogEntry == null)
                throw new IllegalArgumentException("Catalog entry is required");
            this.catalogEntry = catalogEntry;
            this.packageId = requiredId(packageId, "candidate.packageId");
            if (!PACKAGE_ID.equals(this.packageId))
                throw new IllegalArgumentException("Unsupported candidate package");
            ResistorNameplate plate = catalogEntry.getSpecification();
            if (plate == null || !catalogEntry.getId().equals(plate.getComponentId()) ||
                    !finitePositive(plate.getNominalResistanceOhms()) ||
                    !finitePositive(plate.getTolerancePercent()) ||
                    !finitePositive(plate.getRatedWattage()))
                throw new IllegalArgumentException("Forged or malformed catalog metadata");
            ResistorCatalogEntry canonical;
            try {
                canonical = new ResistorReplacementCatalog().get(catalogEntry.getId());
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Nonstandard catalog entry", unknown);
            }
            ResistorNameplate canonicalPlate = canonical.getSpecification();
            if (canonicalPlate.getNominalResistanceOhms() != plate.getNominalResistanceOhms() ||
                    canonicalPlate.getTolerancePercent() != plate.getTolerancePercent() ||
                    canonicalPlate.getRatedWattage() != plate.getRatedWattage())
                throw new IllegalArgumentException("Forged catalog metadata");
        }

        ResistorCatalogEntry getCatalogEntry() { return catalogEntry; }
        ResistorCatalogEntry getEntry() { return catalogEntry; }
        String getId() { return catalogEntry.getId(); }
        String getCatalogEntryId() { return catalogEntry.getId(); }
        double getNominalResistanceOhms() {
            return catalogEntry.getSpecification().getNominalResistanceOhms();
        }
        double getResistanceOhms() { return getNominalResistanceOhms(); }
        double getTolerancePercent() {
            return catalogEntry.getSpecification().getTolerancePercent();
        }
        double getToleranceFraction() { return getTolerancePercent() / 100.0; }
        double getRatedWatts() { return catalogEntry.getSpecification().getRatedWattage(); }
        ResistorNameplate getNameplate() { return catalogEntry.getNameplate(); }
        ResistorNameplate getSpecification() { return catalogEntry.getSpecification(); }
        PhysicalNameplate getPlayerVisibleNameplate() {
            return catalogEntry.getPlayerVisibleNameplate();
        }
        String getPackageId() { return packageId; }
        String getPackageIdentity() { return packageId; }

        @Override public String toString() {
            return getId() + "=" + Double.toString(getNominalResistanceOhms()) +
                    ":" + Double.toString(getTolerancePercent()) + ":" +
                    Double.toString(getRatedWatts()) + ":" + packageId;
        }
    }

    /** One immutable recipe and the pure margin receipt that admitted it. */
    static final class ResolvedRecipe {
        private final Candidate candidate;
        private final Intent intent;
        private final double resistanceMinimumOhms;
        private final double resistanceMaximumOhms;
        private final double minimumCurrentAmps;
        private final double maximumCurrentAmps;
        private final double guardedPowerWatts;

        private ResolvedRecipe(Candidate candidate, Intent intent,
                double resistanceMinimumOhms, double resistanceMaximumOhms,
                double minimumCurrentAmps, double maximumCurrentAmps,
                double guardedPowerWatts) {
            this.candidate = candidate;
            this.intent = intent;
            this.resistanceMinimumOhms = resistanceMinimumOhms;
            this.resistanceMaximumOhms = resistanceMaximumOhms;
            this.minimumCurrentAmps = minimumCurrentAmps;
            this.maximumCurrentAmps = maximumCurrentAmps;
            this.guardedPowerWatts = guardedPowerWatts;
        }

        Candidate getCandidate() { return candidate; }
        Candidate getSelectedCandidate() { return candidate; }
        String getCatalogEntryId() { return candidate.getId(); }
        String getSelectedCatalogEntryId() { return candidate.getId(); }
        double getNominalResistanceOhms() { return candidate.getNominalResistanceOhms(); }
        double getResistanceOhms() { return candidate.getNominalResistanceOhms(); }
        double getTolerancePercent() { return candidate.getTolerancePercent(); }
        double getToleranceFraction() { return candidate.getToleranceFraction(); }
        double getRatedWatts() { return candidate.getRatedWatts(); }
        String getPackageId() { return candidate.getPackageId(); }
        String getPackageIdentity() { return candidate.getPackageId(); }
        ResistorNameplate getNameplate() { return candidate.getNameplate(); }
        PhysicalNameplate getPlayerVisibleNameplate() {
            return candidate.getPlayerVisibleNameplate();
        }
        Intent getIntent() { return intent; }
        String getPolicyId() { return POLICY_ID; }
        double getResistanceMinimumOhms() { return resistanceMinimumOhms; }
        double getResistanceMaximumOhms() { return resistanceMaximumOhms; }
        double getMinimumCurrentAmps() { return minimumCurrentAmps; }
        double getMaximumCurrentAmps() { return maximumCurrentAmps; }
        double getGuardedPowerWatts() { return guardedPowerWatts; }
        double getRequiredPowerWatts() {
            return intent.getPowerHeadroomFactor() * guardedPowerWatts;
        }
        double getPowerRequirementWatts() { return getRequiredPowerWatts(); }
        double getPowerHeadroomFactor() { return intent.getPowerHeadroomFactor(); }
        double getSinkHeadroomFactor() { return intent.getSinkHeadroomFactor(); }

        String semanticSignature() {
            return POLICY_ID + "|" + candidate.toString() + "|Rmin=" +
                    Double.toString(resistanceMinimumOhms) + "|Rmax=" +
                    Double.toString(resistanceMaximumOhms) + "|Imin=" +
                    Double.toString(minimumCurrentAmps) + "|Imax=" +
                    Double.toString(maximumCurrentAmps) + "|Pguard=" +
                    Double.toString(guardedPowerWatts);
        }
    }

    static Intent defaultIntent() { return Intent.controlledIndicator(); }

    /** Resolve policy plus the actual typed operating facts in a request. */
    static Intent fromRequest(BoundedAssemblyRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Assembly request is required");
        ElectricalConnection switchedConnection = null;
        for (ElectricalConnection connection : request.getConnections()) {
            if (ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID
                    .equals(connection.getId())) {
                if (switchedConnection != null)
                    throw new IllegalArgumentException("Duplicate switched relation");
                switchedConnection = connection;
            }
        }
        if (switchedConnection == null || switchedConnection.getKind() !=
                ElectricalConnection.Kind.LOW_SIDE_SWITCHED ||
                switchedConnection.getSwitchedLowSideContract() == null)
            throw new IllegalArgumentException("Typed switched relation is required");
        SwitchedLowSideContract switched =
                switchedConnection.getSwitchedLowSideContract();
        requireRef(switched.getSinkPort(), ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                "SWITCHED_SINK");
        requireRef(switched.getLoadPort(), ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY,
                "SWITCHED_LOAD");
        requireRef(switched.getSupplyPort(), DeviceAdapterContract.POWER_ADAPTER_KEY,
                DeviceAdapterContract.POWER_OUTPUT_PORT_ID);
        requireRef(switched.getControlPort(), DeviceAdapterContract.CONTROL_ADAPTER_KEY,
                DeviceAdapterContract.CONTROL_OUTPUT_PORT_ID);
        requireReturnRefs(switched);

        ElectricalBlockContract driver = findContract(request,
                ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY);
        ElectricalBlockContract load = findContract(request,
                ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY);
        ElectricalBlockContract power = findContract(request,
                DeviceAdapterContract.POWER_ADAPTER_KEY);
        if (driver == null || load == null || power == null)
            throw new IllegalArgumentException("Controlled typed interfaces are incomplete");
        ElectricalPortContract sourcePort = requirePort(power,
                DeviceAdapterContract.POWER_OUTPUT_PORT_ID);
        ElectricalPortContract loadSupply = requirePort(load, "SUPPLY");
        ElectricalPortContract loadSwitch = requirePort(load, "SWITCHED_LOAD");
        ElectricalPortContract sink = requirePort(driver, "SWITCHED_SINK");
        requireKnown(sourcePort.getGuaranteedVoltage(), "source guaranteed voltage");
        requireKnown(sourcePort.getAllowedVoltage(), "source allowed voltage");
        requireKnown(loadSupply.getAllowedVoltage(), "load accepted voltage");
        requireKnown(sink.getGuaranteedVoltage(), "sink clamp voltage");
        requireKnown(sink.getCapacityAmps(), "sink capacity");
        requireKnown(loadSupply.getDemandAmps(), "load demand");
        requireKnown(loadSwitch.getDemandAmps(), "switched load demand");

        double sourceMinimum = sourcePort.getGuaranteedVoltage().getMinimum();
        double sourceMaximum = sourcePort.getGuaranteedVoltage().getMaximum();
        double loadMinimum = loadSupply.getAllowedVoltage().getMinimum();
        double loadMaximum = loadSupply.getAllowedVoltage().getMaximum();
        double sinkMinimum = sink.getGuaranteedVoltage().getMinimum();
        double sinkMaximum = sink.getGuaranteedVoltage().getMaximum();
        double sinkCapacity = sink.getCapacityAmps().getValue();
        double typedDemand = loadSupply.getDemandAmps().getValue();
        if (!same(sourceMinimum, switched.getSupplyGuaranteedMinimumVolts()) ||
                !same(sourceMaximum, switched.getSupplyGuaranteedMaximumVolts()) ||
                !same(loadMinimum, switched.getLoadAllowedMinimumVolts()) ||
                !same(loadMaximum, switched.getLoadAllowedMaximumVolts()) ||
                !same(sinkMinimum, switched.getOnClampMinimumVolts()) ||
                !same(sinkMaximum, switched.getOnClampMaximumVolts()) ||
                !same(sinkCapacity, switched.getSinkCapacityAmps()) ||
                !same(typedDemand, switched.getLoadDemandAmps()) ||
                !same(typedDemand, loadSwitch.getDemandAmps().getValue()))
            throw new IllegalArgumentException("Contradictory switched interface facts: " +
                    sourceMinimum + "/" + switched.getSupplyGuaranteedMinimumVolts() + "," +
                    sourceMaximum + "/" + switched.getSupplyGuaranteedMaximumVolts() + "," +
                    loadMinimum + "/" + switched.getLoadAllowedMinimumVolts() + "," +
                    loadMaximum + "/" + switched.getLoadAllowedMaximumVolts() + "," +
                    sinkMinimum + "/" + switched.getOnClampMinimumVolts() + "," +
                    sinkMaximum + "/" + switched.getOnClampMaximumVolts() + "," +
                    sinkCapacity + "/" + switched.getSinkCapacityAmps() + "," +
                    typedDemand + "/" + switched.getLoadDemandAmps() + "," +
                    typedDemand + "/" + loadSwitch.getDemandAmps().getValue());
        return fromTypedFacts(switched, sourcePort, loadSupply, loadSwitch, sink);
    }

    private static Intent fromTypedFacts(SwitchedLowSideContract switched,
            ElectricalPortContract sourcePort, ElectricalPortContract loadSupply,
            ElectricalPortContract loadSwitch, ElectricalPortContract sink) {
        if (switched == null || sourcePort == null || loadSupply == null ||
                loadSwitch == null || sink == null)
            throw new IllegalArgumentException("Controlled typed facts are required");
        requireKnown(sourcePort.getGuaranteedVoltage(), "source guaranteed voltage");
        requireKnown(loadSupply.getAllowedVoltage(), "load accepted voltage");
        requireKnown(sink.getGuaranteedVoltage(), "sink clamp voltage");
        requireKnown(sink.getCapacityAmps(), "sink capacity");
        requireKnown(loadSupply.getDemandAmps(), "load demand");
        requireKnown(loadSwitch.getDemandAmps(), "switched load demand");
        return new Intent(
                sourcePort.getGuaranteedVoltage().getMinimum(),
                sourcePort.getGuaranteedVoltage().getMaximum(),
                loadSupply.getAllowedVoltage().getMinimum(),
                loadSupply.getAllowedVoltage().getMaximum(),
                sink.getGuaranteedVoltage().getMinimum(),
                sink.getGuaranteedVoltage().getMaximum(),
                sink.getCapacityAmps().getValue(),
                loadSupply.getDemandAmps().getValue(),
                TARGET_MINIMUM_CURRENT_AMPS, LED_MINIMUM_FORWARD_VOLTS,
                LED_MAXIMUM_FORWARD_VOLTS, REQUIRED_TOLERANCE_FRACTION,
                POWER_HEADROOM_FACTOR, SINK_HEADROOM_FACTOR, MODEL_ID,
                PACKAGE_ID);
    }

    private static ElectricalBlockContract findContract(BoundedAssemblyRequest request,
            String key) {
        for (ElectricalBlockContract contract : request.getAllElectricalContracts())
            if (key.equals(contract.getDescriptor().getInstanceKey())) return contract;
        return null;
    }

    private static ElectricalPortContract requirePort(ElectricalBlockContract contract,
            String id) {
        if (contract == null || contract.getPorts().get(id) == null)
            throw new IllegalArgumentException("Missing typed port " + id);
        return contract.getPorts().get(id);
    }

    private static void requireRef(ElectricalConnection.PortRef ref,
            String block, String port) {
        if (ref == null || !block.equals(ref.getBlockKey()) ||
                !port.equals(ref.getPortId()))
            throw new IllegalArgumentException("Switched relation references an unexpected port");
    }

    private static void requireReturnRefs(SwitchedLowSideContract switched) {
        List<String> expected = new ArrayList<String>();
        expected.add(ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY + "/RETURN");
        expected.add(ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY + "/RETURN");
        expected.add(DeviceAdapterContract.POWER_ADAPTER_KEY + "/" + DeviceAdapterContract.RETURN_PORT_ID);
        expected.add(DeviceAdapterContract.CONTROL_ADAPTER_KEY + "/" + DeviceAdapterContract.RETURN_PORT_ID);
        ArrayList<String> actual = new ArrayList<String>();
        for (ElectricalConnection.PortRef ref : switched.getReturnPorts())
            actual.add(ref.getBlockKey() + "/" + ref.getPortId());
        Collections.sort(expected);
        Collections.sort(actual);
        if (!expected.equals(actual))
            throw new IllegalArgumentException("Switched relation return references are incomplete");
    }

    private static void requireKnown(ElectricalPortContract.Range range, String field) {
        if (range == null || range.getState() != ElectricalPortContract.State.KNOWN)
            throw new IllegalArgumentException("Unknown " + field);
    }

    private static void requireKnown(ElectricalPortContract.Scalar scalar, String field) {
        if (scalar == null || scalar.getState() != ElectricalPortContract.State.KNOWN)
            throw new IllegalArgumentException("Unknown " + field);
    }

    private static boolean same(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    /** Resolve exactly one candidate from the catalog for the supplied seed. */
    static ResolvedRecipe resolve(long seed) {
        return resolve(seed, defaultIntent());
    }

    static ResolvedRecipe resolve(long seed, Intent intent) {
        return resolve(seed, new ResistorReplacementCatalog().getEntries(), intent);
    }

    static ResolvedRecipe resolve(long seed,
            Collection<ResistorCatalogEntry> entries, Intent intent) {
        Intent validated = requireIntent(intent);
        List<Candidate> valid = validCandidates(entries, validated);
        if (valid.isEmpty())
            throw new IllegalArgumentException("Controlled LED intent has no valid catalog candidate");
        ArrayList<String> ids = new ArrayList<String>();
        for (Candidate candidate : valid) ids.add(candidate.getId());
        NamedRandomStreams streams = new NamedRandomStreams(1, seed,
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION);
        String selectedId = NamedRandomStreams.select(streams.blockSeed(BLOCK_KEY,
                NamedRandomStreams.Concern.VALUES, VALUES_REVISION, VALUES_KEY), ids);
        for (Candidate candidate : valid)
            if (selectedId.equals(candidate.getId()))
                return admit(candidate, validated);
        throw new IllegalStateException("Selected catalog candidate disappeared");
    }

    static List<Candidate> validCandidates(Intent intent) {
        return validCandidates(new ResistorReplacementCatalog().getEntries(), intent);
    }

    /** Evaluate a caller-supplied catalog collection without trusting its order. */
    static List<Candidate> validCandidates(Collection<ResistorCatalogEntry> entries,
            Intent intent) {
        Intent validated = requireIntent(intent);
        if (entries == null)
            throw new IllegalArgumentException("Catalog entries are required");
        ArrayList<ResistorCatalogEntry> source = new ArrayList<ResistorCatalogEntry>();
        for (ResistorCatalogEntry entry : entries) {
            if (entry == null) throw new IllegalArgumentException("Catalog entry is required");
            source.add(entry);
        }
        Collections.sort(source, new Comparator<ResistorCatalogEntry>() {
            @Override public int compare(ResistorCatalogEntry first,
                    ResistorCatalogEntry second) {
                return first.getId().compareTo(second.getId());
            }
        });
        ArrayList<Candidate> result = new ArrayList<Candidate>();
        String previousId = null;
        for (ResistorCatalogEntry entry : source) {
            String id = entry.getId();
            if (id == null || id.equals(previousId))
                throw new IllegalArgumentException("Duplicate or malformed catalog ID");
            previousId = id;
            Candidate candidate;
            try {
                candidate = new Candidate(entry, validated.getPackageId());
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            if (isAdmitted(candidate, validated)) result.add(candidate);
        }
        return Collections.unmodifiableList(result);
    }

    /** Validate one candidate against the frozen pure equations. */
    static boolean isAdmitted(Candidate candidate, Intent intent) {
        if (candidate == null || intent == null) return false;
        if (!PACKAGE_ID.equals(candidate.getPackageId()) ||
                !PACKAGE_ID.equals(intent.getPackageId())) return false;
        double tolerance = candidate.getToleranceFraction();
        if (!finitePositive(tolerance) ||
                Math.abs(tolerance - intent.getModelToleranceFraction()) > 1.0e-12)
            return false;
        double resistance = candidate.getNominalResistanceOhms();
        if (!finitePositive(resistance) || !finitePositive(candidate.getRatedWatts()))
            return false;
        double rmin = resistance * (1.0 - tolerance);
        double rmax = resistance * (1.0 + tolerance);
        double imin = (intent.getSourceMinimumVolts() -
                intent.getLedMaximumForwardVolts() - intent.getSinkMaximumVolts()) / rmax;
        double imax = (intent.getSourceMaximumVolts() -
                intent.getLedMinimumForwardVolts() - intent.getSinkMinimumVolts()) / rmin;
        double pguard = intent.getSourceMaximumVolts() * intent.getSourceMaximumVolts() / rmin;
        return finitePositive(rmin) && finitePositive(rmax) && finitePositive(imin) &&
                finitePositive(imax) && finitePositive(pguard) &&
                imin >= intent.getTargetMinimumCurrentAmps() &&
                imax <= intent.getTypedDemandAmps() &&
                intent.getSinkHeadroomFactor() * imax <= intent.getSinkCapacityAmps() &&
                intent.getPowerHeadroomFactor() * pguard <= candidate.getRatedWatts();
    }

    static ResolvedRecipe admit(Candidate candidate, Intent intent) {
        Intent validated = requireIntent(intent);
        if (!isAdmitted(candidate, validated))
            throw new IllegalArgumentException("Candidate does not satisfy controlled LED intent");
        double resistance = candidate.getNominalResistanceOhms();
        double tolerance = candidate.getToleranceFraction();
        double rmin = resistance * (1.0 - tolerance);
        double rmax = resistance * (1.0 + tolerance);
        double imin = (validated.getSourceMinimumVolts() -
                validated.getLedMaximumForwardVolts() - validated.getSinkMaximumVolts()) / rmax;
        double imax = (validated.getSourceMaximumVolts() -
                validated.getLedMinimumForwardVolts() - validated.getSinkMinimumVolts()) / rmin;
        double pguard = validated.getSourceMaximumVolts() * validated.getSourceMaximumVolts() / rmin;
        return new ResolvedRecipe(candidate, validated, rmin, rmax, imin, imax, pguard);
    }

    private static Intent requireIntent(Intent intent) {
        if (intent == null) throw new IllegalArgumentException("Controlled LED intent is required");
        return intent;
    }

    private static String requiredId(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private static void requireFinite(double value, String field) {
        if (!finite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    private static void requireFinitePositive(double value, String field) {
        if (!finitePositive(value))
            throw new IllegalArgumentException(field + " must be finite and positive");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean finitePositive(double value) {
        return finite(value) && value > 0.0;
    }
}
