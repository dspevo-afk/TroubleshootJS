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
    private final DeviceBusBindings deviceBuses;
    private final ElectricalRealizationSpec electricalRealizationSpec;

    private BoundedAssemblyPlan(BoundedAssemblyRequest request,
            BlockNamespace namespace,
            Map<String, ComposedBlockContribution> blocks,
            Map<String, String> netAliases,
            Map<String, String> portNets,
            List<String> mergeProvenance,
            String faultDecisionKey, String faultBlockKey,
            Map<String, String> decisionOwners, String semanticSignature,
            Collection<DeviceAdapterContract> deviceAdapters,
            boolean controlledIndicator, DeviceBusBindings deviceBuses) {
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
        if (deviceBuses == null) {
            throw new IllegalArgumentException("Device bus bindings are required");
        }
        this.deviceBuses = deviceBuses;
        this.electricalRealizationSpec = ElectricalRealizationSpec.fromResolved(
                request, namespace, this.blocks, this.deviceAdapters, this.netAliases,
                controlledIndicator);
    }

    /** Resolve the supplied request using only the typed local registry. */
    static BoundedAssemblyPlan resolve(BoundedAssemblyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Assembly request is required");
        }

        validateCurrentGenerator(request.getDescriptor());
        if (isControlledDescriptor(request.getDescriptor())) {
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
                DEVICE_SCHEMA_VERSION, descriptors,
                realizationIdentities(contributions.values(),
                        Collections.<DeviceAdapterContract>emptyList()));
        NetResolution nets = resolveNets(request, namespace, contributions);
        DeviceBusBindings deviceBuses = deviceBusBindings(request, namespace,
                contributions, nets, false);
        nets = bindDeviceNets(nets, deviceBuses);

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
                Collections.<DeviceAdapterContract>emptyList(), false,
                deviceBuses);
    }

    /** Resolve one of the current physically serviceable diagnostic targets explicitly. */
    static BoundedAssemblyPlan resolveForDiagnosticFault(
            BoundedAssemblyRequest request, String qualifiedTargetComponentId) {
        if (request == null)
            throw new IllegalArgumentException("Assembly request is required");
        validateCurrentGenerator(request.getDescriptor());
        if (!isControlledDescriptor(request.getDescriptor()))
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

        TreeMap<String, ComposedBlockContribution> contributions = new TreeMap<String, ComposedBlockContribution>();
        for (ControlledIndicatorChannel channel : BoundedAssemblyRequest.controlledChannels()) {
            ElectricalBlockContract declaredDriver = requestBlock(request, channel.getDriverKey());
            ComposedBlockContribution driver = LowSideRoleFamily.resolve(
                    declaredDriver.getDescriptor().getTypeId(), declaredDriver.getDescriptor().getSchemaVersion())
                    .create(channel.getDriverKey());
            SwitchedLowSideContract switched = requestConnection(request, channel.getSwitchedJoinId())
                    .getSwitchedLowSideContract();
            ControlledIndicatorValueSynthesis.Intent intent = ControlledIndicatorValueSynthesis.fromRequest(request, switched);
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe = ControlledIndicatorValueSynthesis.resolve(
                    request.getDescriptor().getRootSeed(), channel.getLoadKey(), intent);
            ComposedBlockContribution load = ControlledIndicatorBlockContributions.createResolvedValueLoad(
                    channel.getLoadKey(), ControlledIndicatorBlockContributions.FaultSpec.open("RLOAD"), recipe);
            contributions.put(channel.getDriverKey(), driver);
            contributions.put(channel.getLoadKey(), load);
        }
        contributions.put(BoundedAssemblyRequest.SUPPORT_BLOCK_KEY,
                SupplyPresentBlockContributions.create(BoundedAssemblyRequest.SUPPORT_BLOCK_KEY));
        BlockNamespace namespace = new BlockNamespace(
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION,
                request.getNamespaceDescriptors(),
                realizationIdentities(contributions.values(), request.getDeviceAdapters()));
        TreeMap<String, String> owners = new TreeMap<String, String>();
        TreeMap<String, String> ownerBlocks = new TreeMap<String, String>();
        for (ComposedBlockContribution contribution : contributions.values()) {
            ComposedBlockContribution.FaultSpec fault = contribution.getFaultSpec();
            if (fault == null) continue;
            String owner = contribution.getDescriptor().getInstanceKey();
            String decisionKey = owner + "-" + fault.getTargetComponentLocalId() + "-" + fault.getKind().name();
            owners.put(decisionKey, namespace.idFor(owner, EntityKind.COMPONENT, fault.getTargetComponentLocalId()));
            ownerBlocks.put(decisionKey, owner);
        }
        if (owners.isEmpty()) throw new IllegalArgumentException("No serviceable fault candidates");
        String decision = null;
        if (qualifiedTargetComponentId != null) {
            for (Map.Entry<String, String> entry : owners.entrySet())
                if (entry.getValue().equals(qualifiedTargetComponentId)) decision = entry.getKey();
            if (decision == null) throw new IllegalArgumentException("Unsupported composed fault target");
        } else {
            NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
                    request.getDescriptor().getRootSeed(), BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                    BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION);
            decision = NamedRandomStreams.select(streams.deviceSeed(NamedRandomStreams.Concern.FAULT,
                    1, "selected-fault"), owners.keySet());
        }
        NetResolution nets = resolveNets(request, namespace, contributions);
        DeviceBusBindings deviceBuses = deviceBusBindings(request, namespace, contributions, nets, true);
        nets = bindDeviceNets(nets, deviceBuses);
        String signature = semanticSignature(request, contributions, nets, decision, owners, request.getDeviceAdapters());
        return new BoundedAssemblyPlan(request, namespace, contributions,
                nets.aliases, nets.portNets, nets.provenance, decision,
                ownerBlocks.get(decision), owners, signature, request.getDeviceAdapters(), true,
                deviceBuses);
    }

    /** Return the declared-policy preflight result without allocating anything. */
    static PortCompatibilityPreflight.Result preflight(
            BoundedAssemblyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Assembly request is required");
        }
        String schema = isControlledDescriptor(request.getDescriptor())
                ? BoundedAssemblyRequest.CONTROLLED_INTENT_ID : DEVICE_SCHEMA_ID;
        int version = isControlledDescriptor(request.getDescriptor())
                ? BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION : DEVICE_SCHEMA_VERSION;
        return PortCompatibilityPreflight.check(schema, version,
                request.getAllElectricalContracts(),
                request.getConnections());
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
    List<ControlledIndicatorChannel> getChannels() {
        return controlledIndicator ? BoundedAssemblyRequest.controlledChannels() :
                Collections.<ControlledIndicatorChannel>emptyList();
    }
    String getSupportBlockKey() {
        if (!controlledIndicator) throw new IllegalStateException("This device has no support contribution");
        return BoundedAssemblyRequest.SUPPORT_BLOCK_KEY;
    }
    Map<String, String> getNetAliases() { return netAliases; }
    List<String> getMergeProvenance() { return mergeProvenance; }
    String getFaultDecisionKey() { return faultDecisionKey; }
    String getFaultBlockKey() { return faultBlockKey; }
    Map<String, String> getDecisionOwners() { return decisionOwners; }
    String getSemanticSignature() { return semanticSignature; }
    DeviceBusBindings getDeviceBuses() { return deviceBuses; }
    ElectricalRealizationSpec getElectricalRealizationSpec() {
        return electricalRealizationSpec;
    }

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

    /** Resolve a local net to its declared device bus or semantic local net. */
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

    private static boolean isControlledDescriptor(ChallengeDescriptor descriptor) {
        return descriptor != null
                && descriptor.getGenerator().getId().equals(
                        BoundedAssemblyRequest.GENERATOR_ID)
                && descriptor.getGenerator().getVersion()
                        == BoundedAssemblyRequest.GENERATOR_VERSION
                && descriptor.getDeviceIntent().getId().equals(
                        BoundedAssemblyRequest.CONTROLLED_INTENT_ID);
    }

    private static void validateCurrentGenerator(ChallengeDescriptor descriptor) {
        if (descriptor == null
                || descriptor.getSchemaVersion() != ChallengeDescriptor.SCHEMA_VERSION
                || !BoundedAssemblyRequest.GENERATOR_ID.equals(descriptor.getGenerator().getId())
                || descriptor.getGenerator().getVersion() != BoundedAssemblyRequest.GENERATOR_VERSION)
            throw new IllegalArgumentException("Unsupported or retired bounded generator");
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
                || descriptor.getGenerator().getVersion()
                        != BoundedAssemblyRequest.GENERATOR_VERSION
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
        BoundedAssemblyRequest expected = BoundedAssemblyRequest.forControlledIndicator(request.getDescriptor());
        if (request.getBlocks().size() != expected.getBlocks().size() ||
                request.getDeviceAdapters().size() != expected.getDeviceAdapters().size() ||
                request.getConnections().size() != expected.getConnections().size())
            throw new IllegalArgumentException("Controlled device population changed");
        for (int i = 0; i < expected.getBlocks().size(); i++)
            if (!ComposedBlockContribution.sameContract(request.getBlocks().get(i), expected.getBlocks().get(i)))
                throw new IllegalArgumentException("Block differs from the selected compatible provider");
        for (int i = 0; i < expected.getDeviceAdapters().size(); i++)
            if (!ComposedBlockContribution.sameContract(request.getDeviceAdapters().get(i).getElectricalContract(),
                    expected.getDeviceAdapters().get(i).getElectricalContract()))
                throw new IllegalArgumentException("Device source declaration changed");
        for (int i = 0; i < expected.getConnections().size(); i++) {
            ElectricalConnection actual = request.getConnections().get(i);
            ElectricalConnection wanted = expected.getConnections().get(i);
            if (!actual.getId().equals(wanted.getId()) || actual.getKind() != wanted.getKind() ||
                    !portSet(actual).equals(portSet(wanted)) ||
                    !switchedSignature(actual.getSwitchedLowSideContract()).equals(
                            switchedSignature(wanted.getSwitchedLowSideContract())))
                throw new IllegalArgumentException("Undeclared or mismatched device join");
        }
    }

    private static String switchedSignature(SwitchedLowSideContract value) {
        if (value == null) return "none";
        TreeSet<String> returns = new TreeSet<String>();
        for (ElectricalConnection.PortRef ref : value.getReturnPorts()) returns.add(ref.key());
        return value.getSinkPort().key() + "|" + value.getLoadPort().key() + "|" +
                value.getSupplyPort().key() + "|" + value.getControlPort().key() + "|" +
                value.getDriverControlPort().key() + "|" + value.getLoadSupplyPort().key() + "|" + returns + "|" +
                value.getReferenceNetId() + "|" + value.getIsolationId() + "|" + value.isActiveHigh() + "|" +
                Double.toString(value.getSinkCapacityAmps()) + "|" + Double.toString(value.getLoadDemandAmps());
    }

    private static ElectricalBlockContract requestBlock(BoundedAssemblyRequest request, String key) {
        for (ElectricalBlockContract block : request.getAllElectricalContracts())
            if (block.getDescriptor().getInstanceKey().equals(key)) return block;
        throw new IllegalArgumentException("Missing block " + key);
    }

    private static ElectricalConnection requestConnection(BoundedAssemblyRequest request, String id) {
        for (ElectricalConnection connection : request.getConnections())
            if (connection.getId().equals(id)) return connection;
        throw new IllegalArgumentException("Missing device join " + id);
    }

    private static TreeSet<String> portSet(ElectricalConnection connection) {
        TreeSet<String> result = new TreeSet<String>();
        for (ElectricalConnection.PortRef ref : connection.getPorts()) {
            result.add(ref.getBlockKey() + "/" + ref.getPortId());
        }
        return result;
    }

    private static List<BlockRealizationIdentity> realizationIdentities(
            Collection<ComposedBlockContribution> contributions,
            Collection<DeviceAdapterContract> adapters) {
        if (contributions == null || adapters == null) {
            throw new IllegalArgumentException(
                    "Realization sources are required");
        }
        ArrayList<BlockRealizationIdentity> result =
                new ArrayList<BlockRealizationIdentity>();
        for (ComposedBlockContribution contribution : contributions) {
            if (contribution == null) {
                throw new IllegalArgumentException(
                        "Contribution is required for realization");
            }
            FunctionalBlockDescriptor descriptor =
                    contribution.getDescriptor();
            result.add(new BlockRealizationIdentity(
                    descriptor.getInstanceKey(),
                    new ChallengeDescriptor.VersionedId(
                            contribution.getRoleId(), 1),
                    new ChallengeDescriptor.VersionedId(
                            contribution.getProviderTypeId(),
                            contribution.getProviderVersion()),
                    new ChallengeDescriptor.VersionedId(
                            descriptor.getTypeId(),
                            descriptor.getSchemaVersion())));
        }
        for (DeviceAdapterContract adapter : adapters) {
            if (adapter == null) {
                throw new IllegalArgumentException(
                        "Adapter is required for realization");
            }
            FunctionalBlockDescriptor descriptor = adapter.getDescriptor();
            result.add(new BlockRealizationIdentity(
                    descriptor.getInstanceKey(),
                    new ChallengeDescriptor.VersionedId(
                            adapter.getRoleId(), 1),
                    new ChallengeDescriptor.VersionedId(
                            descriptor.getTypeId(), descriptor.getSchemaVersion()),
                    new ChallengeDescriptor.VersionedId(
                            descriptor.getTypeId(), descriptor.getSchemaVersion())));
        }
        return result;
    }

    private static DeviceBusBindings deviceBusBindings(
            BoundedAssemblyRequest request, BlockNamespace namespace,
            Map<String, ComposedBlockContribution> contributions,
            NetResolution nets, boolean controlled) {
        if (request == null || namespace == null || contributions == null
                || nets == null) {
            throw new IllegalArgumentException(
                    "Device bus binding inputs are required");
        }
        return new DeviceBusBindings(namespace,
                deviceBusDeclarations(request, namespace, controlled), nets.aliases);
    }

    private static Collection<DeviceBusBindings.Declaration>
            deviceBusDeclarations(BoundedAssemblyRequest request, BlockNamespace namespace, boolean controlled) {
        ArrayList<DeviceBusBindings.Declaration> result =
                new ArrayList<DeviceBusBindings.Declaration>();
        if (!controlled) {
            result.add(new DeviceBusBindings.Declaration("power-supply",
                    Arrays.asList(net(namespace, "source", "SUPPLY")),
                    Collections.singletonList("VIN_INPUT/positive")));
            result.add(new DeviceBusBindings.Declaration("signal",
                    Arrays.asList(net(namespace, "source", "OUT"),
                            net(namespace, "load", "SUPPLY")),
                    Collections.<String>emptyList()));
            result.add(new DeviceBusBindings.Declaration("power-return",
                    Arrays.asList(net(namespace, "source", "RETURN"),
                            net(namespace, "load", "RETURN")),
                    Collections.singletonList("VIN_INPUT/return")));
        } else {
            for (ElectricalConnection connection : request.getConnections()) {
                ArrayList<String> anchors = new ArrayList<String>();
                ArrayList<String> external = new ArrayList<String>();
                for (ElectricalConnection.PortRef ref : connection.getPorts()) {
                    ElectricalBlockContract contract = requestBlock(request, ref.getBlockKey());
                    anchors.add(qualifiedAttachmentNet(namespace, contract, ref.getBlockKey(), ref.getPortId()));
                    for (DeviceAdapterContract adapter : request.getDeviceAdapters())
                        if (adapter.getKey().equals(ref.getBlockKey()))
                            external.add(adapter.getExternalInputId() +
                                    (adapter.getReturnPortId().equals(ref.getPortId()) ? "/return" : "/positive"));
                }
                result.add(new DeviceBusBindings.Declaration(connection.getId(), anchors, external));
            }
        }
        return result;
    }

    private static String net(BlockNamespace namespace, String blockKey,
            String localNetId) {
        return namespace.idFor(blockKey, EntityKind.NET, localNetId);
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
                    + ports + ";nets=" + nets);
        }
        Collections.sort(provenance);
        return new NetResolution(aliases, portNets, provenance);
    }

    private static NetResolution bindDeviceNets(NetResolution electrical,
            DeviceBusBindings buses) {
        TreeMap<String, String> ports = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : electrical.portNets.entrySet())
            ports.put(entry.getKey(), buses.netFor(entry.getValue()));
        return new NetResolution(buses.getNetBindings(), ports, electrical.provenance);
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
        result.append("tsj-bounded-assembly/2\n")
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
