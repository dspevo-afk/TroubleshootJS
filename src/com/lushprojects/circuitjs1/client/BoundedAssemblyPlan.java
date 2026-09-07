package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;

/**
 * Immutable, fully resolved plan for the bounded composition.  Resolution is
 * pure: it performs descriptor/provider/wiring checks and declared-port
 * preflight before a mutable runtime or solver object can be allocated.
 */
final class BoundedAssemblyPlan {
    static final String DEVICE_SCHEMA_ID = BoundedAssemblyRequest.INTENT_ID;
    static final int DEVICE_SCHEMA_VERSION = BoundedAssemblyRequest.INTENT_VERSION;

    /*
     * Keep the stable decision vocabulary closed.  The selected owner is
     * resolved by this complete table, rather than by interpreting a string
     * prefix.  That makes an unknown decision fail closed and preserves the
     * exact decision-to-owner contract through replay.
     */
    private static final Map<String, String> FAULT_BLOCK_BY_DECISION =
            faultBlockByDecision();

    private final BoundedAssemblyRequest request;
    private final BlockNamespace namespace;
    private final Map<String, ComposedBlockContribution> blocks;
    private final List<DeviceAdapterContract> deviceAdapters;
    private final boolean controlledIndicator;
    private final Map<String, String> netAliases;
    private final Map<String, String> portNets;
    private final List<String> mergeProvenance;
    private final String faultDecisionKey;
    private final String faultBlockKey;
    private final Map<String, String> decisionOwners;
    private final String semanticSignature;
    private final ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedLoadRecipe;

    private BoundedAssemblyPlan(BoundedAssemblyRequest request,
            BlockNamespace namespace,
            Map<String, ComposedBlockContribution> blocks,
            Map<String, String> netAliases,
            Map<String, String> portNets,
            List<String> mergeProvenance,
            String faultDecisionKey, String faultBlockKey,
            Map<String, String> decisionOwners, String semanticSignature,
            Collection<DeviceAdapterContract> deviceAdapters,
            boolean controlledIndicator,
            ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedLoadRecipe) {
        this.request = request;
        this.namespace = namespace;
        this.blocks = Collections.unmodifiableMap(
                new TreeMap<String, ComposedBlockContribution>(blocks));
        this.netAliases = Collections.unmodifiableMap(
                new TreeMap<String, String>(netAliases));
        this.portNets = Collections.unmodifiableMap(
                new TreeMap<String, String>(portNets));
        this.mergeProvenance = Collections.unmodifiableList(
                new ArrayList<String>(mergeProvenance));
        this.faultDecisionKey = faultDecisionKey;
        this.faultBlockKey = faultBlockKey;
        this.decisionOwners = Collections.unmodifiableMap(
                new TreeMap<String, String>(decisionOwners));
        this.semanticSignature = semanticSignature;
        this.deviceAdapters = Collections.unmodifiableList(
                new ArrayList<DeviceAdapterContract>(deviceAdapters));
        this.controlledIndicator = controlledIndicator;
        this.resolvedLoadRecipe = resolvedLoadRecipe;
    }

    /** Resolve the supplied request using only the typed local registry. */
    static BoundedAssemblyPlan resolve(BoundedAssemblyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Assembly request is required");
        }

        if (isControlledDescriptor(request.getDescriptor()) ||
                isControlledValuesDescriptor(request.getDescriptor())) {
            return resolveControlled(request, null);
        }

        // Keep the declared compatibility gate before provider semantic
        // resolution.  A malformed or insufficient proposal therefore cannot
        // reach any future mutable assembler even if its type IDs look known.
        PortCompatibilityPreflight.Result preflight = preflight(request);
        requireCompatible(preflight);
        validateDescriptor(request.getDescriptor());
        validateConstraints(request.getDescriptor().getConstraints());
        validateWiring(request);

        TreeMap<String, ComposedBlockContribution> contributions =
                resolveContributions(request);
        ArrayList<FunctionalBlockDescriptor> descriptors =
                new ArrayList<FunctionalBlockDescriptor>();
        for (ComposedBlockContribution contribution : contributions.values()) {
            descriptors.add(contribution.getDescriptor());
        }
        BlockNamespace namespace = new BlockNamespace(DEVICE_SCHEMA_ID,
                DEVICE_SCHEMA_VERSION, descriptors);
        NetResolution nets = resolveNets(request, namespace, contributions);

        String faultDecisionKey = ResistiveBlockContributions
                .chooseFaultDecision(request.getDescriptor().getRootSeed());
        String faultBlockKey = FAULT_BLOCK_BY_DECISION.get(faultDecisionKey);
        if (faultBlockKey == null) {
            throw new IllegalArgumentException("Unknown fault decision key "
                    + faultDecisionKey);
        }
        TreeMap<String, String> owners = new TreeMap<String, String>();
        owners.put(ResistiveBlockContributions.SOURCE_FAULT_DECISION_KEY,
                namespace.idFor(ResistiveBlockContributions.SOURCE_BLOCK_KEY,
                        EntityKind.COMPONENT, "R1"));
        owners.put(ResistiveBlockContributions.LOAD_FAULT_DECISION_KEY,
                namespace.idFor(ResistiveBlockContributions.LOAD_BLOCK_KEY,
                        EntityKind.COMPONENT, "R1"));

        String signature = semanticSignature(request, contributions, nets,
                faultDecisionKey, owners);
        return new BoundedAssemblyPlan(request, namespace, contributions,
                nets.aliases, nets.portNets, nets.provenance, faultDecisionKey,
                faultBlockKey, owners, signature,
                Collections.<DeviceAdapterContract>emptyList(), false, null);
    }

    /** Resolve one of the two normal diagnostic fault targets explicitly. */
    static BoundedAssemblyPlan resolveForDiagnosticFault(
            BoundedAssemblyRequest request, String qualifiedTargetComponentId) {
        if (request == null)
            throw new IllegalArgumentException("Assembly request is required");
        if (!isControlledDescriptor(request.getDescriptor()) &&
                !isControlledValuesDescriptor(request.getDescriptor()))
            throw new IllegalArgumentException(
                    "Diagnostic fault override requires controlled indicator");
        return resolveControlled(request, qualifiedTargetComponentId);
    }

    private static BoundedAssemblyPlan resolveControlled(
            BoundedAssemblyRequest request, String qualifiedTargetComponentId) {
        PortCompatibilityPreflight.Result preflight = preflight(request);
        requireCompatible(preflight);
        validateControlledDescriptor(request.getDescriptor());
        validateControlledConstraints(request.getDescriptor().getConstraints());
        validateControlledWiring(request);

        BlockNamespace namespace = new BlockNamespace(
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION,
                request.getNamespaceDescriptors());
        String driverOwner = namespace.idFor(
                ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY, EntityKind.COMPONENT, "RG");
        String loadOwner = namespace.idFor(
                ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY, EntityKind.COMPONENT, "RLOAD");
        String decision;
        if (qualifiedTargetComponentId == null) {
            decision = ControlledIndicatorBlockContributions.chooseFaultDecision(
                    request.getDescriptor().getRootSeed());
        } else if (driverOwner.equals(qualifiedTargetComponentId)) {
            decision = ControlledIndicatorBlockContributions.DRIVER_FAULT_DECISION_KEY;
        } else if (loadOwner.equals(qualifiedTargetComponentId)) {
            decision = ControlledIndicatorBlockContributions.LOAD_FAULT_DECISION_KEY;
        } else {
            throw new IllegalArgumentException("Unsupported controlled fault target "
                    + qualifiedTargetComponentId);
        }
        TreeMap<String, ComposedBlockContribution> contributions =
                new TreeMap<String, ComposedBlockContribution>();
        ComposedBlockContribution driver = ControlledIndicatorBlockContributions
                .driver().create(ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                        ControlledIndicatorBlockContributions.faultForDecision(
                                ControlledIndicatorBlockContributions.DRIVER_FAULT_DECISION_KEY));
        ControlledIndicatorBlockContributions.FaultSpec loadFault =
                ControlledIndicatorBlockContributions.faultForDecision(
                    ControlledIndicatorBlockContributions.LOAD_FAULT_DECISION_KEY);
        ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedLoadRecipe = null;
        ComposedBlockContribution load;
        if (isControlledValuesDescriptor(request.getDescriptor())) {
            // The immutable recipe is derived from this request's typed
            // interfaces after the preflight above.  The standalone default
            // intent is intentionally not a production input.
            ControlledIndicatorValueSynthesis.Intent intent =
                    ControlledIndicatorValueSynthesis.fromRequest(request);
            resolvedLoadRecipe = ControlledIndicatorValueSynthesis.resolve(
                     request.getDescriptor().getRootSeed(),
                     intent);
            load = ControlledIndicatorBlockContributions.createResolvedValueLoad(
                    ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY,
                    loadFault, resolvedLoadRecipe);
        } else {
            load = ControlledIndicatorBlockContributions.load().create(
                    ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY, loadFault);
        }
        contributions.put(driver.getDescriptor().getInstanceKey(), driver);
        contributions.put(load.getDescriptor().getInstanceKey(), load);
        validateControlledContributions(request, contributions);

        NetResolution nets = resolveNets(request, namespace, contributions);
        String faultBlockKey = ControlledIndicatorBlockContributions.DRIVER_FAULT_DECISION_KEY
                .equals(decision)
                ? ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY
                : ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY;
        TreeMap<String, String> owners = new TreeMap<String, String>();
        owners.put(ControlledIndicatorBlockContributions.DRIVER_FAULT_DECISION_KEY,
                namespace.idFor(ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                        EntityKind.COMPONENT, "RG"));
        owners.put(ControlledIndicatorBlockContributions.LOAD_FAULT_DECISION_KEY,
                namespace.idFor(ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY,
                        EntityKind.COMPONENT, "RLOAD"));
        String signature = semanticSignature(request, contributions, nets,
                decision, owners, request.getDeviceAdapters());
        return new BoundedAssemblyPlan(request, namespace, contributions,
                nets.aliases, nets.portNets, nets.provenance, decision,
                faultBlockKey, owners, signature, request.getDeviceAdapters(), true,
                resolvedLoadRecipe);
    }

    /** Return the declared-policy preflight result without allocating anything. */
    static PortCompatibilityPreflight.Result preflight(
            BoundedAssemblyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Assembly request is required");
        }
        String schema = (isControlledDescriptor(request.getDescriptor()) ||
                isControlledValuesDescriptor(request.getDescriptor()))
                ? BoundedAssemblyRequest.CONTROLLED_INTENT_ID : DEVICE_SCHEMA_ID;
        int version = (isControlledDescriptor(request.getDescriptor()) ||
                isControlledValuesDescriptor(request.getDescriptor()))
                ? BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION : DEVICE_SCHEMA_VERSION;
        return PortCompatibilityPreflight.check(schema, version,
                request.getAllElectricalContracts(),
                request.getConnections());
    }

    static PortCompatibilityPreflight.Result checkPreflight(
            BoundedAssemblyRequest request) {
        return preflight(request);
    }

    /** Fail closed for every result other than the explicit compatible state. */
    static void requireCompatible(PortCompatibilityPreflight.Result result) {
        if (result == null) {
            throw new IllegalArgumentException("Preflight result is required");
        }
        if (result.getDecision() != PortCompatibilityPreflight.Decision.COMPATIBLE) {
            String detail = result.getDiagnostics().isEmpty() ? "none"
                    : result.getDiagnostics().get(0).getCode().name();
            throw new IllegalArgumentException("Assembly preflight "
                    + result.getDecision().name() + ": " + detail);
        }
    }

    static boolean isCompatible(PortCompatibilityPreflight.Result result) {
        return result != null
                && result.getDecision() == PortCompatibilityPreflight.Decision.COMPATIBLE;
    }

    BoundedAssemblyRequest getRequest() { return request; }
    BlockNamespace getNamespace() { return namespace; }
    Map<String, ComposedBlockContribution> getBlocks() { return blocks; }
    List<DeviceAdapterContract> getDeviceAdapters() { return deviceAdapters; }
    boolean isControlledIndicator() { return controlledIndicator; }
    boolean isControlledIndicatorValues() { return resolvedLoadRecipe != null; }
    ControlledIndicatorValueSynthesis.ResolvedRecipe getResolvedLoadRecipe() {
        return resolvedLoadRecipe;
    }
    ComposedBlockContribution getDriver() { return blocks.get("driver"); }
    ComposedBlockContribution getLoad() { return blocks.get("load"); }
    Map<String, String> getNetAliases() { return netAliases; }
    List<String> getMergeProvenance() { return mergeProvenance; }
    String getFaultDecisionKey() { return faultDecisionKey; }
    String getFaultBlockKey() { return faultBlockKey; }
    Map<String, String> getDecisionOwners() { return decisionOwners; }
    String getSemanticSignature() { return semanticSignature; }

    String idFor(String blockKey, EntityKind kind, String localId) {
        return namespace.idFor(blockKey, kind, localId);
    }

    String idFor(ComposedBlockContribution block, EntityKind kind,
            String localId) {
        if (block == null) {
            throw new IllegalArgumentException("Block contribution is required");
        }
        return idFor(block.getDescriptor().getInstanceKey(), kind, localId);
    }

    String qualifiedId(String blockKey, EntityKind kind, String localId) {
        return idFor(blockKey, kind, localId);
    }

    /** Resolve a local net to its canonical merged qualified net ID. */
    String netFor(String blockKey, String localNetId) {
        String qualified = namespace.idFor(blockKey, EntityKind.NET,
                localNetId);
        String result = netAliases.get(qualified);
        if (result == null) {
            throw new IllegalArgumentException("Net was not resolved: " + qualified);
        }
        return result;
    }

    String netFor(ComposedBlockContribution block, String localNetId) {
        if (block == null) {
            throw new IllegalArgumentException("Block contribution is required");
        }
        return netFor(block.getDescriptor().getInstanceKey(), localNetId);
    }

    /** Resolve a local port's declared attachment to its canonical net ID. */
    String netForPort(String blockKey, String portId) {
        String qualified = namespace.idFor(blockKey, EntityKind.PORT, portId);
        String result = portNets.get(qualified);
        if (result == null) {
            throw new IllegalArgumentException("Port net was not resolved: "
                    + qualified);
        }
        return result;
    }

    String netForPort(ComposedBlockContribution block, String portId) {
        if (block == null) {
            throw new IllegalArgumentException("Block contribution is required");
        }
        return netForPort(block.getDescriptor().getInstanceKey(), portId);
    }

    String faultOwnerId(String decisionKey) {
        String owner = decisionOwners.get(decisionKey);
        if (owner == null) {
            throw new IllegalArgumentException("Unknown fault decision "
                    + decisionKey);
        }
        return owner;
    }
    String getFaultOwnerId(String decisionKey) { return faultOwnerId(decisionKey); }

    private static boolean isControlledDescriptor(ChallengeDescriptor descriptor) {
        return descriptor != null
                && descriptor.getGenerator().getId().equals(
                        BoundedAssemblyRequest.GENERATOR_ID)
                && descriptor.getGenerator().getVersion()
                        == BoundedAssemblyRequest.CONTROLLED_GENERATOR_VERSION
                && descriptor.getDeviceIntent().getId().equals(
                        BoundedAssemblyRequest.CONTROLLED_INTENT_ID);
    }

    private static boolean isControlledValuesDescriptor(
            ChallengeDescriptor descriptor) {
        return descriptor != null
                && BoundedAssemblyRequest.GENERATOR_ID.equals(
                        descriptor.getGenerator().getId())
                && descriptor.getGenerator().getVersion()
                        == BoundedAssemblyRequest.CONTROLLED_VALUES_GENERATOR_VERSION
                && BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId());
    }

    private static void validateDescriptor(ChallengeDescriptor descriptor) {
        if (descriptor.getSchemaVersion() != ChallengeDescriptor.SCHEMA_VERSION
                || descriptor.getGenerator().getId().equals(
                        BoundedAssemblyRequest.GENERATOR_ID) == false
                || descriptor.getGenerator().getVersion()
                        != BoundedAssemblyRequest.GENERATOR_VERSION
                || !BoundedAssemblyRequest.INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId())
                || descriptor.getDeviceIntent().getVersion()
                        != BoundedAssemblyRequest.INTENT_VERSION
                || !BoundedAssemblyRequest.PROFILE_ID.equals(
                        descriptor.getDifficultyProfile().getId())
                || descriptor.getDifficultyProfile().getVersion()
                        != BoundedAssemblyRequest.PROFILE_VERSION
                || descriptor.getGeometryVersion().getValue()
                        != BoundedAssemblyRequest.GEOMETRY_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported bounded assembly descriptor identity");
        }
    }

    private static void validateControlledDescriptor(ChallengeDescriptor descriptor) {
        if (descriptor.getSchemaVersion() != ChallengeDescriptor.SCHEMA_VERSION
                || !BoundedAssemblyRequest.GENERATOR_ID.equals(
                        descriptor.getGenerator().getId())
                || (descriptor.getGenerator().getVersion()
                        != BoundedAssemblyRequest.CONTROLLED_GENERATOR_VERSION &&
                    descriptor.getGenerator().getVersion()
                        != BoundedAssemblyRequest.CONTROLLED_VALUES_GENERATOR_VERSION)
                || !BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId())
                || descriptor.getDeviceIntent().getVersion()
                        != BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION
                || !BoundedAssemblyRequest.CONTROLLED_PROFILE_ID.equals(
                        descriptor.getDifficultyProfile().getId())
                || descriptor.getDifficultyProfile().getVersion()
                        != BoundedAssemblyRequest.CONTROLLED_PROFILE_VERSION
                || descriptor.getGeometryVersion().getValue()
                        != BoundedAssemblyRequest.GEOMETRY_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported controlled indicator descriptor identity");
        }
    }

    private static void validateConstraints(GenerationConstraints constraints) {
        if (constraints == null || constraints.getVersion()
                != GenerationConstraints.VERSION) {
            throw new IllegalArgumentException("Unsupported assembly constraints");
        }
        validateSupportedCount(constraints, GenerationConstraints.CountMetric.BLOCKS,
                BoundedAssemblyRequest.SUPPORTED_BLOCK_COUNT);
        validateSupportedCount(constraints,
                GenerationConstraints.CountMetric.COMPONENTS,
                BoundedAssemblyRequest.SUPPORTED_COMPONENT_COUNT);
        validateSupportedCount(constraints, GenerationConstraints.CountMetric.DOMAINS,
                BoundedAssemblyRequest.SUPPORTED_DOMAIN_COUNT);
        for (GenerationConstraints.CountMetric metric
                : GenerationConstraints.CountMetric.values()) {
            if (metric == GenerationConstraints.CountMetric.BLOCKS
                    || metric == GenerationConstraints.CountMetric.COMPONENTS
                    || metric == GenerationConstraints.CountMetric.DOMAINS) {
                continue;
            }
            if (constraints.getCount(metric).isSpecified()) {
                throw new IllegalArgumentException(
                        "Unsupported specified count constraint "
                                + metric.getToken());
            }
        }
        if (constraints.getParallelAmbiguity()
                != GenerationConstraints.Requirement.UNSPECIFIED
                || constraints.getTemporalEvidence()
                        != GenerationConstraints.Requirement.UNSPECIFIED
                || constraints.getAllowedInstruments().isSpecified()) {
            throw new IllegalArgumentException(
                    "Unsupported assembly requirement or instrument constraint");
        }
    }

    private static void validateControlledConstraints(
            GenerationConstraints constraints) {
        if (constraints == null || constraints.getVersion()
                != GenerationConstraints.VERSION)
            throw new IllegalArgumentException("Unsupported controlled constraints");
        validateSupportedCount(constraints, GenerationConstraints.CountMetric.BLOCKS,
                BoundedAssemblyRequest.CONTROLLED_SUPPORTED_BLOCK_COUNT);
        validateSupportedCount(constraints, GenerationConstraints.CountMetric.COMPONENTS,
                BoundedAssemblyRequest.CONTROLLED_SUPPORTED_COMPONENT_COUNT);
        validateSupportedCount(constraints, GenerationConstraints.CountMetric.DOMAINS,
                BoundedAssemblyRequest.CONTROLLED_SUPPORTED_DOMAIN_COUNT);
        for (GenerationConstraints.CountMetric metric
                : GenerationConstraints.CountMetric.values()) {
            if (metric == GenerationConstraints.CountMetric.BLOCKS
                    || metric == GenerationConstraints.CountMetric.COMPONENTS
                    || metric == GenerationConstraints.CountMetric.DOMAINS)
                continue;
            if (constraints.getCount(metric).isSpecified())
                throw new IllegalArgumentException("Unsupported specified count constraint "
                        + metric.getToken());
        }
        if (constraints.getParallelAmbiguity()
                != GenerationConstraints.Requirement.UNSPECIFIED
                || constraints.getTemporalEvidence()
                        != GenerationConstraints.Requirement.UNSPECIFIED
                || constraints.getAllowedInstruments().isSpecified())
            throw new IllegalArgumentException("Unsupported controlled requirement");
    }

    private static void validateSupportedCount(GenerationConstraints constraints,
            GenerationConstraints.CountMetric metric, int expected) {
        GenerationConstraints.CountRange range = constraints.getCount(metric);
        if (range.isSpecified()
                && (range.getMinimum() > expected || range.getMaximum() < expected)) {
            throw new IllegalArgumentException("Constraint " + metric.getToken()
                    + " does not contain " + expected);
        }
    }

    private static TreeMap<String, ComposedBlockContribution> resolveContributions(
            BoundedAssemblyRequest request) {
        if (request.getBlocks().size() != BoundedAssemblyRequest.SUPPORTED_BLOCK_COUNT) {
            throw new IllegalArgumentException("Assembly requires exactly two blocks");
        }
        TreeMap<String, ElectricalBlockContract> declarations =
                new TreeMap<String, ElectricalBlockContract>();
        for (ElectricalBlockContract declaration : request.getBlocks()) {
            String blockKey = declaration.getDescriptor().getInstanceKey();
            if (declarations.put(blockKey, declaration) != null) {
                throw new IllegalArgumentException("Duplicate block " + blockKey);
            }
        }
        if (!declarations.keySet().equals(new TreeSet<String>(Arrays.asList(
                ResistiveBlockContributions.SOURCE_BLOCK_KEY,
                ResistiveBlockContributions.LOAD_BLOCK_KEY)))) {
            throw new IllegalArgumentException(
                    "Assembly requires source and load block declarations");
        }

        TreeMap<String, ComposedBlockContribution> result =
                new TreeMap<String, ComposedBlockContribution>();
        long seed = request.getDescriptor().getRootSeed();
        for (String blockKey : declarations.keySet()) {
            ElectricalBlockContract declaration = declarations.get(blockKey);
            FunctionalBlockDescriptor descriptor = declaration.getDescriptor();
            boolean source = ResistiveBlockContributions.SOURCE_BLOCK_KEY
                    .equals(blockKey);
            String expectedType = source
                    ? ResistiveBlockContributions.SOURCE_TYPE_ID
                    : ResistiveBlockContributions.LOAD_TYPE_ID;
            if (!expectedType.equals(descriptor.getTypeId())
                    || descriptor.getSchemaVersion()
                            != ResistiveBlockContributions.VERSION) {
                throw new IllegalArgumentException("Unsupported block provider "
                        + descriptor.getTypeId() + "@" + descriptor.getSchemaVersion());
            }
            ComposedBlockContribution expected =
                    (source ? ResistiveBlockContributions.source()
                            : ResistiveBlockContributions.load()).create(blockKey,
                            ResistiveBlockContributions.chooseValue(seed,
                                    blockKey));
            if (!ComposedBlockContribution.sameContract(declaration,
                    expected.getElectricalContract())) {
                throw new IllegalArgumentException(
                        "Block declaration does not match its typed provider "
                                + blockKey);
            }
            result.put(blockKey, expected);
        }
        return result;
    }

    private static void validateWiring(BoundedAssemblyRequest request) {
        if (request.getConnections().size() != 2) {
            throw new IllegalArgumentException("Assembly requires two connections");
        }
        TreeMap<String, ElectricalConnection> byId =
                new TreeMap<String, ElectricalConnection>();
        for (ElectricalConnection connection : request.getConnections()) {
            byId.put(connection.getId(), connection);
        }
        ElectricalConnection signal = byId.get(
                ResistiveBlockContributions.SIGNAL_CONNECTION_ID);
        ElectricalConnection returned = byId.get(
                ResistiveBlockContributions.RETURN_CONNECTION_ID);
        if (signal == null || returned == null || byId.size() != 2
                || !portSet(signal).equals(new TreeSet<String>(Arrays.asList(
                        "load/IN", "source/OUT")))
                || !portSet(returned).equals(new TreeSet<String>(Arrays.asList(
                        "load/RETURN", "source/RETURN")))) {
            throw new IllegalArgumentException("Fixed resistive wiring was changed");
        }
    }

    private static void validateControlledWiring(BoundedAssemblyRequest request) {
        if (request.getBlocks().size() != BoundedAssemblyRequest.CONTROLLED_SUPPORTED_BLOCK_COUNT
                || request.getDeviceAdapters().size() != 2
                || request.getConnections().size() != 4)
            throw new IllegalArgumentException("Controlled indicator requires two blocks, two adapters and four connections");
        TreeSet<String> blockKeys = new TreeSet<String>();
        for (ElectricalBlockContract block : request.getBlocks())
            blockKeys.add(block.getDescriptor().getInstanceKey());
        if (!blockKeys.equals(new TreeSet<String>(Arrays.asList(
                ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY))))
            throw new IllegalArgumentException("Controlled block keys were changed");
        TreeSet<String> adapterKeys = new TreeSet<String>();
        for (DeviceAdapterContract adapter : request.getDeviceAdapters())
            adapterKeys.add(adapter.getKey());
        if (!adapterKeys.equals(new TreeSet<String>(Arrays.asList(
                DeviceAdapterContract.POWER_ADAPTER_KEY,
                DeviceAdapterContract.CONTROL_ADAPTER_KEY))))
            throw new IllegalArgumentException("Controlled adapter keys were changed");
        TreeMap<String, ElectricalConnection> byId = new TreeMap<String, ElectricalConnection>();
        for (ElectricalConnection connection : request.getConnections())
            byId.put(connection.getId(), connection);
        if (byId.size() != 4
                || !portSet(byId.get(ControlledIndicatorBlockContributions.POWER_CONNECTION_ID)).equals(
                        new TreeSet<String>(Arrays.asList("load/SUPPLY", "power-adapter/POWER_OUT")))
                || !portSet(byId.get(ControlledIndicatorBlockContributions.CONTROL_CONNECTION_ID)).equals(
                        new TreeSet<String>(Arrays.asList("control-adapter/CONTROL_OUT", "driver/CONTROL")))
                || !portSet(byId.get(ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID)).equals(
                        new TreeSet<String>(Arrays.asList("driver/SWITCHED_SINK", "load/SWITCHED_LOAD")))
                || !portSet(byId.get(ControlledIndicatorBlockContributions.RETURN_CONNECTION_ID)).equals(
                        new TreeSet<String>(Arrays.asList("control-adapter/RETURN", "driver/RETURN",
                                "load/RETURN", "power-adapter/RETURN"))))
            throw new IllegalArgumentException("Controlled indicator wiring was changed");
        ElectricalConnection switched = byId.get(
                ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID);
        if (switched.getKind() != ElectricalConnection.Kind.LOW_SIDE_SWITCHED
                || switched.getSwitchedLowSideContract() == null)
            throw new IllegalArgumentException("Controlled switched relation is required");
    }

    private static void validateControlledContributions(
            BoundedAssemblyRequest request,
            Map<String, ComposedBlockContribution> contributions) {
        for (ElectricalBlockContract declaration : request.getBlocks()) {
            String key = declaration.getDescriptor().getInstanceKey();
            ComposedBlockContribution expected = contributions.get(key);
            if (isControlledValuesDescriptor(request.getDescriptor()) &&
                    ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(key)) {
                expected = ControlledIndicatorBlockContributions.valuesLoad().create(
                        ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY);
            }
            if (expected == null || !ComposedBlockContribution.sameContract(
                    declaration, expected.getElectricalContract()))
                throw new IllegalArgumentException("Block declaration does not match controlled provider " + key);
        }
        DeviceAdapterContract expectedPower = DeviceAdapterContract.power();
        DeviceAdapterContract expectedControl = DeviceAdapterContract.control();
        for (DeviceAdapterContract adapter : request.getDeviceAdapters()) {
            DeviceAdapterContract expected = DeviceAdapterContract.POWER_ADAPTER_KEY
                    .equals(adapter.getKey()) ? expectedPower : expectedControl;
            if (!ComposedBlockContribution.sameContract(adapter.getElectricalContract(),
                    expected.getElectricalContract()))
                throw new IllegalArgumentException("Adapter declaration does not match typed provider "
                        + adapter.getKey());
        }
    }

    private static TreeSet<String> portSet(ElectricalConnection connection) {
        TreeSet<String> result = new TreeSet<String>();
        for (ElectricalConnection.PortRef ref : connection.getPorts()) {
            result.add(ref.getBlockKey() + "/" + ref.getPortId());
        }
        return result;
    }

    private static NetResolution resolveNets(BoundedAssemblyRequest request,
            BlockNamespace namespace,
            Map<String, ComposedBlockContribution> contributions) {
        Union union = new Union();
        TreeMap<String, ElectricalBlockContract> declarations =
                new TreeMap<String, ElectricalBlockContract>();
        for (ComposedBlockContribution contribution : contributions.values()) {
            ElectricalBlockContract contract = contribution.getElectricalContract();
            declarations.put(contribution.getDescriptor().getInstanceKey(), contract);
            for (String localNet : contribution.getDescriptor().getNetIds()) {
                union.add(namespace.idFor(contribution.getDescriptor().getInstanceKey(),
                        EntityKind.NET, localNet));
            }
        }
        for (DeviceAdapterContract adapter : request.getDeviceAdapters()) {
            ElectricalBlockContract contract = adapter.getElectricalContract();
            declarations.put(contract.getDescriptor().getInstanceKey(), contract);
            for (String localNet : contract.getDescriptor().getNetIds()) {
                union.add(namespace.idFor(contract.getDescriptor().getInstanceKey(),
                        EntityKind.NET, localNet));
            }
        }
        for (ElectricalConnection connection : request.getConnections()) {
            String first = null;
            for (ElectricalConnection.PortRef ref : connection.getPorts()) {
                ElectricalBlockContract block = declarations.get(ref.getBlockKey());
                if (block == null || !block.getPorts().containsKey(ref.getPortId())) {
                    throw new IllegalArgumentException("Connection references unknown port");
                }
                String qualifiedNet = qualifiedAttachmentNet(namespace, block,
                        ref.getBlockKey(), ref.getPortId());
                if (first == null) {
                    first = qualifiedNet;
                } else {
                    union.join(first, qualifiedNet);
                }
            }
        }
        TreeMap<String, String> aliases = new TreeMap<String, String>();
        for (String qualifiedNet : union.keys()) {
            aliases.put(qualifiedNet, union.find(qualifiedNet));
        }
        TreeMap<String, String> portNets = new TreeMap<String, String>();
        for (Map.Entry<String, ElectricalBlockContract> entry
                : declarations.entrySet()) {
            String blockKey = entry.getKey();
            for (String portId : entry.getValue().getPorts().keySet()) {
                String qualifiedPort = namespace.idFor(blockKey,
                        EntityKind.PORT, portId);
                String qualifiedNet = qualifiedAttachmentNet(namespace,
                        entry.getValue(), blockKey, portId);
                portNets.put(qualifiedPort, aliases.get(qualifiedNet));
            }
        }
        ArrayList<String> provenance = new ArrayList<String>();
        for (ElectricalConnection connection : request.getConnections()) {
            ArrayList<String> ports = new ArrayList<String>();
            ArrayList<String> nets = new ArrayList<String>();
            for (ElectricalConnection.PortRef ref : connection.getPorts()) {
                ports.add(namespace.idFor(ref.getBlockKey(), EntityKind.PORT,
                        ref.getPortId()));
                ElectricalBlockContract block = declarations.get(ref.getBlockKey());
                nets.add(qualifiedAttachmentNet(namespace, block,
                        ref.getBlockKey(), ref.getPortId()));
            }
            Collections.sort(ports);
            Collections.sort(nets);
            provenance.add("connection=" + connection.getId() + ";ports="
                    + ports + ";nets=" + nets + ";canonical="
                    + aliases.get(nets.get(0)));
        }
        Collections.sort(provenance);
        return new NetResolution(aliases, portNets, provenance);
    }

    private static String qualifiedAttachmentNet(BlockNamespace namespace,
            ElectricalBlockContract block, String blockKey, String portId) {
        FunctionalBlockDescriptor descriptor = block.getDescriptor();
        FunctionalBlockDescriptor.Port port = descriptor.getPorts().get(portId);
        LocalRef attachment = port.getAttachment();
        String localNet;
        if (attachment.getKind() == EntityKind.NET) {
            localNet = attachment.getId();
        } else if (attachment.getKind() == EntityKind.PAD) {
            localNet = descriptor.getPads().get(attachment.getId()).getNetId();
        } else if (attachment.getKind() == EntityKind.ENDPOINT) {
            localNet = null;
            for (FunctionalBlockDescriptor.Pad pad
                    : descriptor.getPads().values()) {
                if (pad.getEndpointId().equals(attachment.getId())) {
                    localNet = pad.getNetId();
                    break;
                }
            }
            if (localNet == null) {
                throw new IllegalArgumentException("Port endpoint has no pad net");
            }
        } else {
            throw new IllegalArgumentException("Port attachment is not conductive");
        }
        return namespace.idFor(blockKey, EntityKind.NET, localNet);
    }

    private static String semanticSignature(BoundedAssemblyRequest request,
            Map<String, ComposedBlockContribution> contributions,
            NetResolution nets, String faultDecisionKey,
            Map<String, String> owners) {
        return semanticSignature(request, contributions, nets, faultDecisionKey,
                owners, Collections.<DeviceAdapterContract>emptyList());
    }

    private static String semanticSignature(BoundedAssemblyRequest request,
            Map<String, ComposedBlockContribution> contributions,
            NetResolution nets, String faultDecisionKey,
            Map<String, String> owners,
            Collection<DeviceAdapterContract> adapters) {
        StringBuilder result = new StringBuilder();
        result.append("tsj-bounded-assembly/1\n")
                .append(request.getDescriptor().toCanonical()).append('\n');
        for (ComposedBlockContribution contribution : contributions.values()) {
            result.append("block=").append(contribution.semanticSignature())
                    .append('\n');
        }
        for (String alias : nets.aliases.keySet()) {
            result.append("net=").append(alias).append("->")
                    .append(nets.aliases.get(alias)).append('\n');
        }
        for (String provenance : nets.provenance) {
            result.append("merge=").append(provenance).append('\n');
        }
        for (DeviceAdapterContract adapter : adapters) {
            result.append("adapter=").append(adapter.getKey()).append('@')
                    .append(adapter.getVersion()).append('|')
                    .append(adapter.getDescriptor().getInstanceKey()).append('|')
                    .append(adapter.getComponentLocalId()).append('|')
                    .append(adapter.getExternalInputId()).append('|')
                    .append(adapter.getOutputPortId()).append('\n');
        }
        result.append("fault=").append(faultDecisionKey).append('\n');
        for (Map.Entry<String, String> owner : owners.entrySet()) {
            result.append("owner=").append(owner.getKey()).append("->")
                    .append(owner.getValue()).append('\n');
        }
        return result.toString();
    }

    private static Map<String, String> faultBlockByDecision() {
        TreeMap<String, String> result = new TreeMap<String, String>();
        result.put(ResistiveBlockContributions.SOURCE_FAULT_DECISION_KEY,
                ResistiveBlockContributions.SOURCE_BLOCK_KEY);
        result.put(ResistiveBlockContributions.LOAD_FAULT_DECISION_KEY,
                ResistiveBlockContributions.LOAD_BLOCK_KEY);
        return Collections.unmodifiableMap(result);
    }

    private static final class NetResolution {
        final Map<String, String> aliases;
        final Map<String, String> portNets;
        final List<String> provenance;

        NetResolution(Map<String, String> aliases,
                Map<String, String> portNets, List<String> provenance) {
            this.aliases = aliases;
            this.portNets = portNets;
            this.provenance = provenance;
        }
    }

    private static final class Union {
        private final TreeMap<String, String> parents =
                new TreeMap<String, String>();

        void add(String value) {
            if (!parents.containsKey(value)) {
                parents.put(value, value);
            }
        }

        String find(String value) {
            String parent = parents.get(value);
            if (parent == null) {
                throw new IllegalArgumentException("Unknown net " + value);
            }
            String root = value;
            while (!parents.get(root).equals(root)) {
                root = parents.get(root);
            }
            while (!value.equals(root)) {
                String next = parents.get(value);
                parents.put(value, root);
                value = next;
            }
            return root;
        }

        void join(String first, String second) {
            String a = find(first);
            String b = find(second);
            if (a.equals(b)) {
                return;
            }
            if (a.compareTo(b) < 0) {
                parents.put(b, a);
            } else {
                parents.put(a, b);
            }
        }

        Collection<String> keys() { return parents.keySet(); }
    }
}
