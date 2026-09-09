package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Parameter;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Value;

/**
 * Pure capture and replay adapter for the bounded assembly versions 1, 2, and 3.
 *
 * <p>This class resolves and verifies the complete data identity before the
 * mutable assembler is entered.  It deliberately does not retain a runtime,
 * solver node, coordinate, or semantic signature.</p>
 */
final class A03RealizationReplay {
    static final int VERSION = 1;
    static final String REPLAY_ID = "bounded-realization-replay";
    static final int REPLAY_VERSION = VERSION;

    private static final String PCB_LAYOUT_ID = "pcb-layout";
    private static final String PCB_GEOMETRY_ID = "pcb-geometry";
    private static final String ROUTING_ID = BoundedAssemblyRequest.GENERATOR_ID;
    private static final String MODEL_ID = BoundedAssemblyRequest.GENERATOR_ID;
    private static final String PACKAGE_ID = BoundedAssemblyRequest.GENERATOR_ID;
    private static final String VALUES_ID = "controlled-led-load-e12";
    private static final String DIAGNOSTIC_ID = "generated-diagnostic-solvability";
    private static final String STREAM_ID = "named-random-streams";
    private A03RealizationReplay() { }

    static RealizationManifest capture(BoundedAssemblyPlan plan) {
        if (plan == null)
            throw new IllegalArgumentException("Assembly plan is required");
        return capture(plan,
                BoundedGeneratedBoardAssembler.describePhysicalChoices(plan));
    }

    static RealizationManifest capture(BoundedAssemblyPlan plan,
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices physicalChoices) {
        if (plan == null || physicalChoices == null)
            throw new IllegalArgumentException("Capture inputs are required");
        ArrayList<RealizationManifest.Choice> choices =
                new ArrayList<RealizationManifest.Choice>();
        ArrayList<RealizationManifest.NetBinding> nets =
                new ArrayList<RealizationManifest.NetBinding>();
        ArrayList<String> targets = new ArrayList<String>();
        BlockNamespace namespace = plan.getNamespace();

        captureNamespace(plan, namespace, choices, targets);
        captureBuses(plan, namespace, nets, targets, choices);
        captureConnections(plan, namespace, choices);
        captureAdapters(plan, namespace, choices);
        captureContributions(plan, namespace, choices);
        captureFaults(plan, namespace, choices);
        capturePhysicalChoices(plan, physicalChoices, namespace, choices, targets);
        captureModelChoices(plan, choices);

        return new RealizationManifest(RealizationManifest.VERSION,
                plan.getRequest().getDescriptor(),
                namespace.getRealizations().values(),
                versionPins(plan, physicalChoices),
                choices, nets, targets, null, null);
    }

    static BoundedAssemblyPlan decodeAndResolve(String encoded) {
        return resolve(RealizationManifest.parse(encoded));
    }

    static BoundedAssemblyPlan resolve(RealizationManifest manifest) {
        if (manifest == null)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD,
                    "manifest", "Manifest is required");
        rejectFutureFields(manifest);
        BoundedAssemblyRequest request = requestFor(manifest.getDescriptor(),
                manifest.getVersionPins());
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        RealizationManifest expected = capture(plan);
        compareVersionPins(expected.getVersionPins(), manifest.getVersionPins());
        compareChoices(expected.getChoices(), manifest.getChoices());
        if (!expected.identityCanonical().equals(manifest.identityCanonical()))
            throw mismatch("identity", "Resolved realization identity differs");
        return plan;
    }

    static BoundedGeneratedBoardAssembler.Result generate(
            RealizationManifest manifest) {
        return BoundedGeneratedBoardAssembler.replay(manifest);
    }
    private static void rejectFutureFields(RealizationManifest manifest) {
        if (manifest.getFutureState() != null)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "future", "Future model state is not supported");
        RealizationManifest.ImportOrigin origin = manifest.getOrigin();
        if (origin != null && origin.getInterpretation() != null)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "origin.interpretation",
                    "Interpretation references are not supported");
    }

    private static BoundedAssemblyRequest requestFor(
            ChallengeDescriptor descriptor,
            List<RealizationManifest.VersionPin> pins) {
        if (descriptor == null)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD,
                    "descriptor", "Descriptor is required");
        if (descriptor.getSchemaVersion() != ChallengeDescriptor.SCHEMA_VERSION)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "descriptor.schemaVersion", "Unsupported descriptor schema");
        String generator = descriptor.getGenerator().getId();
        int version = descriptor.getGenerator().getVersion();
        if (!BoundedAssemblyRequest.GENERATOR_ID.equals(generator))
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_ID,
                    "descriptor.generator", "Unsupported bounded generator");
        if (BoundedAssemblyRequest.INTENT_ID.equals(
                descriptor.getDeviceIntent().getId())) {
            if (descriptor.getDeviceIntent().getVersion()
                    != BoundedAssemblyRequest.INTENT_VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "descriptor.deviceIntent", "Unsupported resistive intent version");
            if (!BoundedAssemblyRequest.PROFILE_ID.equals(
                    descriptor.getDifficultyProfile().getId())
                    || descriptor.getDifficultyProfile().getVersion()
                            != BoundedAssemblyRequest.PROFILE_VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_ID,
                        "descriptor.difficultyProfile", "Unsupported resistive profile");
        } else if (BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                descriptor.getDeviceIntent().getId())) {
            if (descriptor.getDeviceIntent().getVersion()
                    != BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "descriptor.deviceIntent", "Unsupported controlled intent version");
            if (!BoundedAssemblyRequest.CONTROLLED_PROFILE_ID.equals(
                    descriptor.getDifficultyProfile().getId())
                    || descriptor.getDifficultyProfile().getVersion()
                            != BoundedAssemblyRequest.CONTROLLED_PROFILE_VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_ID,
                        "descriptor.difficultyProfile", "Unsupported controlled profile");
        } else {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_ID,
                    "descriptor.deviceIntent", "Unsupported bounded intent");
        }
        if (descriptor.getGeometryVersion().getValue()
                != BoundedAssemblyRequest.GEOMETRY_VERSION)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "descriptor.geometry", "Unsupported bounded geometry version");
        if (descriptor.getConstraints() == null
                || descriptor.getConstraints().getVersion()
                        != GenerationConstraints.VERSION)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "descriptor.constraints", "Unsupported constraint schema");
        validateVersionPins(pins, descriptor);
        if (BoundedAssemblyRequest.GENERATOR_ID.equals(generator)
                && version == BoundedAssemblyRequest.GENERATOR_VERSION
                && BoundedAssemblyRequest.INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId()))
            return BoundedAssemblyRequest.forCanary(descriptor);
        if (BoundedAssemblyRequest.GENERATOR_ID.equals(generator)
                && version == BoundedAssemblyRequest.CONTROLLED_GENERATOR_VERSION
                && BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId()))
            return BoundedAssemblyRequest.forControlledIndicator(descriptor);
        if (BoundedAssemblyRequest.GENERATOR_ID.equals(generator)
                && version == BoundedAssemblyRequest.CONTROLLED_VALUES_GENERATOR_VERSION
                && BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId()))
            return BoundedAssemblyRequest.forControlledIndicatorValues(descriptor);
        throw new ChallengeContractException(
                ChallengeContractException.Code.UNSUPPORTED_VERSION,
                "descriptor.generator",
                "Unsupported bounded assembly generator or intent");
    }

    private static void validateVersionPins(
            List<RealizationManifest.VersionPin> pins,
            ChallengeDescriptor descriptor) {
        if (pins == null
                || pins.size() != RealizationManifest.VersionPin.Concern.values().length)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD,
                    "versions", "All bounded version pins are required");
        int generatorVersion = descriptor.getGenerator().getVersion();
        for (RealizationManifest.VersionPin.Concern concern
                : RealizationManifest.VersionPin.Concern.values()) {
            RealizationManifest.VersionPin found = null;
            for (RealizationManifest.VersionPin pin : pins)
                if (pin.getConcern() == concern) {
                    found = pin;
                    break;
                }
            if (found == null)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "version." + concern.getToken(), "Version pin is required");
            String expectedId;
            int expectedVersion;
            switch (concern) {
            case LAYOUT:
                expectedId = PCB_LAYOUT_ID;
                expectedVersion = SeededPcbLayoutGenerator.LEGACY_VERSION;
                break;
            case ROUTING:
            case MODELS:
            case PACKAGES:
                expectedId = ROUTING_ID;
                expectedVersion = generatorVersion;
                break;
            case VALUES:
                expectedId = generatorVersion
                        == BoundedAssemblyRequest.CONTROLLED_VALUES_GENERATOR_VERSION
                        ? VALUES_ID : ROUTING_ID;
                expectedVersion = generatorVersion
                        == BoundedAssemblyRequest.CONTROLLED_VALUES_GENERATOR_VERSION
                        ? 1 : generatorVersion;
                break;
            case GEOMETRY:
                expectedId = PCB_GEOMETRY_ID;
                expectedVersion = BoundedAssemblyRequest.GEOMETRY_VERSION;
                break;
            case DIAGNOSTIC:
                expectedId = DIAGNOSTIC_ID;
                expectedVersion = GeneratedDiagnosticSolvabilityContract.VERSION;
                break;
            case NAMED_STREAMS:
                expectedId = STREAM_ID;
                expectedVersion = NamedRandomStreams.DERIVATION_VERSION;
                break;
            default:
                throw new IllegalStateException("Unknown version concern");
            }
            if (!expectedId.equals(found.getOwnerId())
                    || expectedVersion != found.getOwnerVersionNumber())
                throw mismatch("version." + concern.getToken(),
                        "Unsupported bounded version pin");
        }
    }

    private static ChallengeContractException mismatch(String field,
            String detail) {
        return new ChallengeContractException(
                ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT,
                field, detail);
    }

    private static void compareVersionPins(
            List<RealizationManifest.VersionPin> expected,
            List<RealizationManifest.VersionPin> actual) {
        for (RealizationManifest.VersionPin.Concern concern
                : RealizationManifest.VersionPin.Concern.values()) {
            RealizationManifest.VersionPin expectedPin = null;
            RealizationManifest.VersionPin actualPin = null;
            for (RealizationManifest.VersionPin pin : expected)
                if (pin.getConcern() == concern) expectedPin = pin;
            for (RealizationManifest.VersionPin pin : actual)
                if (pin.getConcern() == concern) actualPin = pin;
            if (expectedPin == null || actualPin == null
                    || !expectedPin.equals(actualPin))
                throw mismatch("version." + concern.getToken(),
                        "Resolved version pin differs");
        }
    }

    private static void compareChoices(
            List<RealizationManifest.Choice> expected,
            List<RealizationManifest.Choice> actual) {
        for (RealizationManifest.Choice choice : expected) {
            RealizationManifest.Choice found = null;
            for (RealizationManifest.Choice candidate : actual)
                if (choice.getKey().equals(candidate.getKey())) {
                    found = candidate;
                    break;
                }
            if (found == null || !choice.equals(found))
                throw mismatch("choice." + choice.getKey(),
                        "Resolved choice differs");
        }
        for (RealizationManifest.Choice choice : actual) {
            boolean known = false;
            for (RealizationManifest.Choice candidate : expected)
                if (choice.getKey().equals(candidate.getKey())) {
                    known = true;
                    break;
                }
            if (!known)
                throw mismatch("choice." + choice.getKey(),
                        "Unknown resolved choice");
        }
    }
    private static List<RealizationManifest.VersionPin> versionPins(
            BoundedAssemblyPlan plan,
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices physicalChoices) {
        ChallengeDescriptor descriptor = plan.getRequest().getDescriptor();
        int generatorVersion = descriptor.getGenerator().getVersion();
        ArrayList<RealizationManifest.VersionPin> result =
                new ArrayList<RealizationManifest.VersionPin>();
        result.add(pin(RealizationManifest.VersionPin.Concern.LAYOUT,
                PCB_LAYOUT_ID, physicalChoices.getLayoutVersion()));
        result.add(pin(RealizationManifest.VersionPin.Concern.ROUTING,
                ROUTING_ID, generatorVersion));
        if (generatorVersion == BoundedAssemblyRequest.CONTROLLED_VALUES_GENERATOR_VERSION)
            result.add(pin(RealizationManifest.VersionPin.Concern.VALUES,
                    VALUES_ID, 1));
        else
            result.add(pin(RealizationManifest.VersionPin.Concern.VALUES,
                    ROUTING_ID, generatorVersion));
        result.add(pin(RealizationManifest.VersionPin.Concern.MODELS,
                MODEL_ID, generatorVersion));
        result.add(pin(RealizationManifest.VersionPin.Concern.PACKAGES,
                PACKAGE_ID, generatorVersion));
        result.add(pin(RealizationManifest.VersionPin.Concern.GEOMETRY,
                PCB_GEOMETRY_ID, BoundedAssemblyRequest.GEOMETRY_VERSION));
        result.add(pin(RealizationManifest.VersionPin.Concern.DIAGNOSTIC,
                DIAGNOSTIC_ID, GeneratedDiagnosticSolvabilityContract.VERSION));
        result.add(pin(RealizationManifest.VersionPin.Concern.NAMED_STREAMS,
                STREAM_ID, NamedRandomStreams.DERIVATION_VERSION));
        return result;
    }

    private static RealizationManifest.VersionPin pin(
            RealizationManifest.VersionPin.Concern concern,
            String id, int version) {
        return new RealizationManifest.VersionPin(concern,
                new ChallengeDescriptor.VersionedId(id, version));
    }

    private static void captureNamespace(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices,
            List<String> targets) {
        for (Map.Entry<String, FunctionalBlockDescriptor> entry
                : namespace.getBlocks().entrySet()) {
            String block = entry.getKey();
            FunctionalBlockDescriptor descriptor = entry.getValue();
            addTarget(targets, namespace.instanceIdFor(block));
            addToken(choices, "block." + block + ".type",
                    descriptor.getTypeId() + "@" + descriptor.getSchemaVersion());
            addIds(choices, "block." + block + ".components",
                    durableIds(namespace, block, EntityKind.COMPONENT,
                            descriptor.getComponents().keySet(), targets));
            addIds(choices, "block." + block + ".nets",
                    durableIds(namespace, block, EntityKind.NET,
                            descriptor.getNetIds(), targets));
            addIds(choices, "block." + block + ".endpoints",
                    durableIds(namespace, block, EntityKind.ENDPOINT,
                            descriptor.getEndpoints().keySet(), targets));
            addIds(choices, "block." + block + ".pads",
                    durableIds(namespace, block, EntityKind.PAD,
                            descriptor.getPads().keySet(), targets));
            addIds(choices, "block." + block + ".roles",
                    durableIds(namespace, block, EntityKind.ROLE,
                            descriptor.getRoles().keySet(), targets));
            addIds(choices, "block." + block + ".ports",
                    durableIds(namespace, block, EntityKind.PORT,
                            descriptor.getPorts().keySet(), targets));
            captureBlockDetails(namespace, block, descriptor, plan,
                    choices, targets);
        }
        for (BlockRealizationIdentity identity
                : namespace.getRealizations().values()) {
            addTarget(targets, namespace.instanceIdFor(identity.getInstanceKey()));
        }
    }

    private static List<String> durableIds(BlockNamespace namespace,
            String block, EntityKind kind, Collection<String> localIds,
            List<String> targets) {
        ArrayList<String> result = new ArrayList<String>();
        for (String local : localIds) {
            String durable = namespace.durableIdFor(block, kind, local);
            result.add(durable);
            addTarget(targets, durable);
        }
        return result;
    }
    private static void captureBlockDetails(BlockNamespace namespace,
            String block, FunctionalBlockDescriptor descriptor,
            BoundedAssemblyPlan plan,
            List<RealizationManifest.Choice> choices,
            List<String> targets) {
        for (Map.Entry<String, Parameter> entry
                : descriptor.getParameters().entrySet())
            addValue(choices, "block." + block + ".parameter."
                    + entry.getKey(), entry.getValue().getValue());

        for (Map.Entry<String, FunctionalBlockDescriptor.Component> entry
                : descriptor.getComponents().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Component component = entry.getValue();
            addToken(choices, "block." + block + ".component." + local
                    + ".type", component.getTypeId());
            addIds(choices, "block." + block + ".component." + local
                    + ".terminals",
                    terminalIds(namespace, block, component, targets));
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Endpoint> entry
                : descriptor.getEndpoints().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Endpoint endpoint = entry.getValue();
            String durable = namespace.durableIdFor(block,
                    EntityKind.ENDPOINT, local);
            String terminal = namespace.durableTerminalIdFor(block,
                    endpoint.getComponentId(), endpoint.getTerminalId());
            addToken(choices, "block." + block + ".endpoint." + local
                    + ".component", namespace.durableIdFor(block,
                            EntityKind.COMPONENT, endpoint.getComponentId()));
            addToken(choices, "block." + block + ".endpoint." + local
                    + ".terminal", terminal);
            addTarget(targets, durable);
            addTarget(targets, terminal);
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry
                : descriptor.getPads().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Pad pad = entry.getValue();
            addToken(choices, "block." + block + ".pad." + local
                    + ".endpoint", namespace.durableIdFor(block,
                            EntityKind.ENDPOINT, pad.getEndpointId()));
            addToken(choices, "block." + block + ".pad." + local
                    + ".net", plan.durableNetFor(block, pad.getNetId()));
        }
        for (String localNet : descriptor.getNetIds()) {
            addToken(choices, "block." + block + ".net." + localNet
                    + ".conductor", plan.durableNetFor(block, localNet));
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Role> entry
                : descriptor.getRoles().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Role role = entry.getValue();
            addToken(choices, "block." + block + ".role." + local
                    + ".requirement", role.getRequirement().name());
            ArrayList<String> members = new ArrayList<String>();
            for (LocalRef member : role.getMembers()) {
                String durable = namespace.durableIdFor(block,
                        member.getKind(), member.getId());
                members.add(durable);
                addTarget(targets, durable);
            }
            addIds(choices, "block." + block + ".role." + local
                    + ".members", members);
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Port> entry
                : descriptor.getPorts().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Port port = entry.getValue();
            addToken(choices, "block." + block + ".port." + local
                    + ".role", namespace.durableIdFor(block,
                            EntityKind.ROLE, port.getRoleId()));
            addToken(choices, "block." + block + ".port." + local
                    + ".attachment", namespace.durableIdFor(block,
                            port.getAttachment().getKind(),
                            port.getAttachment().getId()));
        }
    }

    private static List<String> terminalIds(BlockNamespace namespace,
            String block, FunctionalBlockDescriptor.Component component,
            List<String> targets) {
        ArrayList<String> result = new ArrayList<String>();
        for (String terminal : component.getTerminalIds()) {
            String durable = namespace.durableTerminalIdFor(block,
                    component.getId(), terminal);
            result.add(durable);
            addTarget(targets, durable);
        }
        return result;
    }
    private static void captureBuses(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.NetBinding> nets,
            List<String> targets,
            List<RealizationManifest.Choice> choices) {
        Map<String, String> localIds = namespace.getDurableLocalNetIds();
        Map<String, String> durable = plan.getDeviceBuses().getDurableNets();
        if (!localIds.keySet().equals(durable.keySet()))
            throw mismatch("nets", "Device buses do not cover namespace nets");
        for (Map.Entry<String, String> entry : localIds.entrySet()) {
            String alias = entry.getValue();
            String conductor = durable.get(entry.getKey());
            if (conductor == null)
                throw mismatch("nets", "Missing durable conductor");
            nets.add(new RealizationManifest.NetBinding(alias, conductor));
            addTarget(targets, alias);
            addTarget(targets, conductor);
        }
        for (Map.Entry<String, List<String>> entry
                : plan.getDeviceBuses().getBusAliases().entrySet()) {
            addTarget(targets, entry.getKey());
            addIds(choices, "bus." + entry.getKey() + ".aliases",
                    entry.getValue());
            for (String alias : entry.getValue())
                addTarget(targets, alias);
        }
        captureBusDeclarations(plan, namespace, choices, targets);
    }

    private static void captureBusDeclarations(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices,
            List<String> targets) {
        Map<String, String> durableLocalNets =
                namespace.getDurableLocalNetIds();
        for (DeviceBusBindings.Declaration declaration
                : plan.getDeviceBuses().getDeclarations()) {
            String semanticKey = declaration.getSemanticKey();
            String base = "bus.declaration." + semanticKey + ".";
            addToken(choices, base + "semantic-key", semanticKey);
            ArrayList<String> anchors = new ArrayList<String>();
            for (String anchor : declaration.getAnchorAliases()) {
                String durableAnchor = durableLocalNets.get(anchor);
                if (durableAnchor == null)
                    throw mismatch("nets.declaration", "Unknown bus anchor");
                anchors.add(durableAnchor);
                addTarget(targets, durableAnchor);
            }
            addIds(choices, base + "anchors", anchors);
            List<String> externalRefs = declaration.getExternalRefs();
            addInteger(choices, base + "external-ref-count",
                    externalRefs.size());
            for (int index = 0; index < externalRefs.size(); index++) {
                String externalRef = externalRefs.get(index);
                addToken(choices, base + "external-ref." + index,
                        externalRef);
            }
            String busId = plan.getDeviceBuses().getBusId(semanticKey);
            addToken(choices, base + "bus-id", busId);
            addTarget(targets, busId);
        }
    }

    private static void captureConnections(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices) {
        for (ElectricalConnection connection
                : plan.getRequest().getConnections()) {
            ArrayList<String> ports = new ArrayList<String>();
            for (ElectricalConnection.PortRef ref : connection.getPorts())
                ports.add(namespace.durableIdFor(ref.getBlockKey(),
                        EntityKind.PORT, ref.getPortId()));
            addToken(choices, "connection." + connection.getId()
                    + ".kind", connection.getKind().name());
            addIds(choices, "connection." + connection.getId() + ".ports",
                    ports);
            captureSwitchedContract(connection, namespace, choices);
        }
    }
    private static void captureSwitchedContract(
            ElectricalConnection connection, BlockNamespace namespace,
            List<RealizationManifest.Choice> choices) {
        SwitchedLowSideContract contract =
                connection.getSwitchedLowSideContract();
        if (contract == null)
            return;
        String base = "connection." + connection.getId() + ".switched.";
        addToken(choices, base + "active-high",
                Boolean.toString(contract.getActiveHigh()));
        addToken(choices, base + "reference-net", contract.getReferenceNetId());
        addToken(choices, base + "isolation", contract.getIsolationId());
        addNumber(choices, base + "sink-capacity",
                contract.getSinkCapacityAmps());
        addNumber(choices, base + "load-demand",
                contract.getLoadDemandAmps());
        addNumber(choices, base + "supply-min",
                contract.getSupplyGuaranteedMinimumVolts());
        addNumber(choices, base + "supply-max",
                contract.getSupplyGuaranteedMaximumVolts());
        addNumber(choices, base + "load-min",
                contract.getLoadAllowedMinimumVolts());
        addNumber(choices, base + "load-max",
                contract.getLoadAllowedMaximumVolts());
        addNumber(choices, base + "on-min",
                contract.getOnClampMinimumVolts());
        addNumber(choices, base + "on-max",
                contract.getOnClampMaximumVolts());
        addNumber(choices, base + "off-min",
                contract.getOffDrainMinimumVolts());
        addNumber(choices, base + "off-max",
                contract.getOffDrainMaximumVolts());
        addNumber(choices, base + "control-low",
                contract.getControlLowMaximumVolts());
        addNumber(choices, base + "control-high",
                contract.getControlHighMinimumVolts());
        addNumber(choices, base + "input-low",
                contract.getInputLowMaximumVolts());
        addNumber(choices, base + "input-high",
                contract.getInputHighMinimumVolts());
        addPortRef(choices, base + "sink-port",
                contract.getSinkPort(), namespace);
        addPortRef(choices, base + "load-port",
                contract.getLoadPort(), namespace);
        addPortRef(choices, base + "supply-port",
                contract.getSupplyPort(), namespace);
        addPortRef(choices, base + "control-port",
                contract.getControlPort(), namespace);
        ArrayList<String> returns = new ArrayList<String>();
        for (ElectricalConnection.PortRef ref : contract.getReturnPorts())
            returns.add(namespace.durableIdFor(ref.getBlockKey(),
                    EntityKind.PORT, ref.getPortId()));
        addIds(choices, base + "return-ports", returns);
    }

    private static void addPortRef(List<RealizationManifest.Choice> choices,
            String key, ElectricalConnection.PortRef ref,
            BlockNamespace namespace) {
        addToken(choices, key, namespace.durableIdFor(ref.getBlockKey(),
                EntityKind.PORT, ref.getPortId()));
    }
    private static void captureAdapters(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices) {
        for (DeviceAdapterContract adapter : plan.getRequest().getDeviceAdapters()) {
            String base = "adapter." + adapter.getKey() + ".";
            addInteger(choices, base + "version", adapter.getVersion());
            addToken(choices, base + "component", namespace.durableIdFor(
                    adapter.getKey(), EntityKind.COMPONENT,
                    adapter.getComponentLocalId()));
            addToken(choices, base + "external-input",
                    adapter.getExternalInputId());
            addToken(choices, base + "output-port", namespace.durableIdFor(
                    adapter.getKey(), EntityKind.PORT,
                    adapter.getOutputPortId()));
            addToken(choices, base + "return-port", namespace.durableIdFor(
                    adapter.getKey(), EntityKind.PORT,
                    adapter.getReturnPortId()));
        }
    }
    private static void captureContributions(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices) {
        for (Map.Entry<String, ComposedBlockContribution> entry
                : plan.getBlocks().entrySet()) {
            String block = entry.getKey();
            ComposedBlockContribution contribution = entry.getValue();
            for (Map.Entry<String, ComposedBlockContribution.ResistorRecipe> recipe
                    : contribution.getResistors().entrySet()) {
                String base = "recipe." + block + "."
                        + recipe.getKey() + ".";
                captureResistorRecipe(namespace, block, recipe.getValue(),
                        base, choices);
            }
            for (Map.Entry<String, ComposedBlockContribution.NmosRecipe> recipe
                    : contribution.getNmosRecipes().entrySet())
                captureNmosRecipe(namespace, block, recipe.getValue(), choices);
            for (Map.Entry<String, ComposedBlockContribution.LedRecipe> recipe
                    : contribution.getLedRecipes().entrySet())
                captureLedRecipe(namespace, block, recipe.getValue(), choices);
            addToken(choices, "recipe." + block + ".provider",
                    contribution.getProviderTypeId() + "@"
                            + contribution.getProviderVersion());
            // Version 1 stores a fault label in the legacy FaultSpec wrapper;
            // its assembler applies incorrect resistance to the repair resistor.
            boolean legacyResistive = plan.getRequest().getDescriptor()
                    .getGenerator().getVersion() == 1;
            addToken(choices, "recipe." + block + ".fault-kind",
                    legacyResistive ? "INCORRECT_RESISTANCE"
                            : contribution.getFaultSpec().getKind().name());
            addToken(choices, "recipe." + block + ".fault-target",
                    namespace.durableIdFor(block, EntityKind.COMPONENT,
                            legacyResistive ? contribution.getRepairLocalComponentId()
                                    : contribution.getFaultSpec().getTargetComponentLocalId()));
            addNumber(choices, "recipe." + block + ".fault-effective",
                    contribution.getFaultSpec().getEffectiveResistanceOhms());
            addToken(choices, "recipe." + block + ".repair",
                    namespace.durableIdFor(block, EntityKind.COMPONENT,
                            contribution.getRepairLocalComponentId()));
            addIds(choices, "recipe." + block + ".inputs",
                    contribution.getInputRequirements());
            addIds(choices, "recipe." + block + ".retests",
                    contribution.getRetestRequirements());
            captureResolvedRecipe(block, contribution.getResolvedValueRecipe(),
                    choices);
        }
    }
    private static void captureResistorRecipe(BlockNamespace namespace,
            String block, ComposedBlockContribution.ResistorRecipe recipe,
            String base, List<RealizationManifest.Choice> choices) {
        addToken(choices, base + "component", namespace.durableIdFor(block,
                EntityKind.COMPONENT, recipe.getComponentLocalId()));
        addToken(choices, base + "endpoint-1", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getFirstEndpointLocalId()));
        addToken(choices, base + "endpoint-2", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getSecondEndpointLocalId()));
        addToken(choices, base + "pad-1", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getFirstPadLocalId()));
        addToken(choices, base + "pad-2", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getSecondPadLocalId()));
        addNumber(choices, base + "resistance", recipe.getResistanceOhms());
        addNumber(choices, base + "rated-watts", recipe.getRatedWatts());
        addNumber(choices, base + "tolerance-percent",
                recipe.getTolerancePercent());
        addToken(choices, base + "mutable",
                Boolean.toString(recipe.isMutable()));
        if (recipe.getCatalogEntryId() != null)
            addToken(choices, base + "catalog", recipe.getCatalogEntryId());
        if (recipe.getPackageId() != null)
            addToken(choices, base + "package", recipe.getPackageId());
    }

    private static void captureNmosRecipe(BlockNamespace namespace, String block,
            ComposedBlockContribution.NmosRecipe recipe,
            List<RealizationManifest.Choice> choices) {
        String base = "recipe." + block + ".nmos."
                + recipe.getComponentLocalId() + ".";
        addToken(choices, base + "component", namespace.durableIdFor(block,
                EntityKind.COMPONENT, recipe.getComponentLocalId()));
        addToken(choices, base + "model", recipe.getModelId());
        addToken(choices, base + "gate-endpoint", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getGateEndpointLocalId()));
        addToken(choices, base + "drain-endpoint", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getDrainEndpointLocalId()));
        addToken(choices, base + "source-endpoint", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getSourceEndpointLocalId()));
        addToken(choices, base + "gate-pad", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getGatePadLocalId()));
        addToken(choices, base + "drain-pad", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getDrainPadLocalId()));
        addToken(choices, base + "source-pad", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getSourcePadLocalId()));
    }
    private static void captureLedRecipe(BlockNamespace namespace, String block,
            ComposedBlockContribution.LedRecipe recipe,
            List<RealizationManifest.Choice> choices) {
        String base = "recipe." + block + ".led."
                + recipe.getComponentLocalId() + ".";
        addToken(choices, base + "component", namespace.durableIdFor(block,
                EntityKind.COMPONENT, recipe.getComponentLocalId()));
        addToken(choices, base + "model", recipe.getModelId());
        addToken(choices, base + "anode-endpoint", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getAnodeEndpointLocalId()));
        addToken(choices, base + "cathode-endpoint", namespace.durableIdFor(block,
                EntityKind.ENDPOINT, recipe.getCathodeEndpointLocalId()));
        addToken(choices, base + "anode-pad", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getAnodePadLocalId()));
        addToken(choices, base + "cathode-pad", namespace.durableIdFor(block,
                EntityKind.PAD, recipe.getCathodePadLocalId()));
    }
    private static void captureFaults(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices) {
        addToken(choices, "fault.decision", plan.getFaultDecisionKey());
        addToken(choices, "fault.block", plan.getFaultBlockKey());
        for (Map.Entry<String, String> owner
                : plan.getDecisionOwners().entrySet()) {
            addToken(choices, "fault.owner." + owner.getKey(),
                    durablePhysicalComponentId(namespace, owner.getValue()));
        }
    }

    private static void captureResolvedRecipe(String block,
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe,
            List<RealizationManifest.Choice> choices) {
        if (recipe == null)
            return;
        String base = "value." + block + ".";
        addToken(choices, base + "policy", recipe.getPolicyId());
        addToken(choices, base + "catalog", recipe.getCatalogEntryId());
        addToken(choices, base + "package", recipe.getPackageId());
        addNumber(choices, base + "nominal-resistance",
                recipe.getNominalResistanceOhms());
        addNumber(choices, base + "tolerance-percent",
                recipe.getTolerancePercent());
        addNumber(choices, base + "rated-watts", recipe.getRatedWatts());
        addNumber(choices, base + "power-headroom",
                recipe.getPowerHeadroomFactor());
        addNumber(choices, base + "sink-headroom",
                recipe.getSinkHeadroomFactor());
        captureIntent(block, recipe.getIntent(), choices);
    }
    private static void captureIntent(String block,
            ControlledIndicatorValueSynthesis.Intent intent,
            List<RealizationManifest.Choice> choices) {
        String base = "value." + block + ".intent.";
        addNumber(choices, base + "source-min", intent.getSourceMinimumVolts());
        addNumber(choices, base + "source-max", intent.getSourceMaximumVolts());
        addNumber(choices, base + "load-min",
                intent.getLoadAcceptanceMinimumVolts());
        addNumber(choices, base + "load-max",
                intent.getLoadAcceptanceMaximumVolts());
        addNumber(choices, base + "sink-min", intent.getSinkMinimumVolts());
        addNumber(choices, base + "sink-max", intent.getSinkMaximumVolts());
        addNumber(choices, base + "sink-capacity", intent.getSinkCapacityAmps());
        addNumber(choices, base + "typed-demand", intent.getTypedDemandAmps());
        addNumber(choices, base + "target-minimum-current",
                intent.getTargetMinimumCurrentAmps());
        addNumber(choices, base + "led-min", intent.getLedMinimumForwardVolts());
        addNumber(choices, base + "led-max", intent.getLedMaximumForwardVolts());
        addNumber(choices, base + "model-tolerance",
                intent.getModelToleranceFraction());
        addNumber(choices, base + "power-headroom",
                intent.getPowerHeadroomFactor());
        addNumber(choices, base + "sink-headroom",
                intent.getSinkHeadroomFactor());
        addToken(choices, base + "model", intent.getModelId());
        addToken(choices, base + "package", intent.getPackageId());
    }
    private static void capturePhysicalChoices(BoundedAssemblyPlan plan,
            BoundedGeneratedBoardAssembler.PlanPhysicalChoices physical,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices,
            List<String> targets) {
        addInteger(choices, "physical.layout-version",
                physical.getLayoutVersion());
        for (Map.Entry<String, PhysicalGeometryRealization> entry
                : physical.getPackages().entrySet()) {
            String durableComponent = durablePhysicalComponentId(namespace,
                    entry.getKey());
            String base = "physical.package." + durableComponent + ".";
            PhysicalGeometryRealization realization = entry.getValue();
            addToken(choices, base + "package",
                    realization.getPhysicalPackage().getId());
            addToken(choices, base + "variant",
                    realization.getGeometryVariantKey());
            addToken(choices, base + "transform",
                    realization.getGeometryTransformKey());
            addInteger(choices, base + "geometry-version",
                    realization.getGeometryContractVersionValue());
            addTarget(targets, durableComponent);
            addDeviceOwnedEnvelopeTargets(namespace, entry.getKey(),
                    durableComponent, realization.getPhysicalGeometry(), targets);
        }
        for (Map.Entry<String, Double> entry
                : physical.getInputVoltages().entrySet()) {
            String durableInput = durablePhysicalInputId(plan, namespace,
                    entry.getKey());
            addNumber(choices, "physical.input." + durableInput
                    + ".voltage", entry.getValue().doubleValue());
            addTarget(targets, durableInput);
        }
    }

    private static void captureModelChoices(BoundedAssemblyPlan plan,
            List<RealizationManifest.Choice> choices) {
        if (!plan.isControlledIndicator())
            return;
        addToken(choices, "model.nmos.id", "NMOS_TRANSISTOR");
        addNumber(choices, "model.nmos.threshold-volts",
                BoundedGeneratedBoardAssembler.CONTROLLED_NMOS_THRESHOLD_VOLTS);
        addNumber(choices, "model.nmos.beta",
                BoundedGeneratedBoardAssembler.CONTROLLED_NMOS_BETA);

        DiodeModel.createModelMap();
        DiodeModel model = DiodeModel.modelMap.get(
                BoundedGeneratedBoardAssembler.CONTROLLED_LED_MODEL);
        if (model == null)
            throw mismatch("model.led", "Missing default-led model");
        String base = "model.led." + BoundedGeneratedBoardAssembler.CONTROLLED_LED_MODEL
                + ".";
        addToken(choices, base + "id", model.name);
        addInteger(choices, base + "flags", model.flags);
        addNumber(choices, base + "saturation-current", model.saturationCurrent);
        addNumber(choices, base + "series-resistance", model.seriesResistance);
        addNumber(choices, base + "emission-coefficient", model.emissionCoefficient);
        addNumber(choices, base + "breakdown-voltage", model.breakdownVoltage);
        addNumber(choices, base + "thermal-voltage", DiodeModel.vt);
        addNumber(choices, base + "vscale", model.vscale);
        addNumber(choices, base + "vdcoef", model.vdcoef);
        addNumber(choices, base + "forward-drop", model.fwdrop);
    }

    /** Map legacy/layout component keys to the explicit variant-owned ID. */
    private static String durablePhysicalComponentId(BlockNamespace namespace,
            String componentId) {
        if (componentId == null || componentId.length() == 0)
            throw mismatch("physical.component", "Missing component identity");
        String match = null;
        for (Map.Entry<String, FunctionalBlockDescriptor> block
                : namespace.getBlocks().entrySet()) {
            for (String local : block.getValue().getComponents().keySet()) {
                String qualified = namespace.idFor(block.getKey(),
                        EntityKind.COMPONENT, local);
                String durable = namespace.durableIdFor(block.getKey(),
                        EntityKind.COMPONENT, local);
                if (componentId.equals(durable))
                    return durable;
                if (componentId.equals(qualified) || componentId.equals(local)) {
                    if (match != null && !match.equals(durable))
                        throw mismatch("physical.component",
                                "Ambiguous legacy component identity");
                    match = durable;
                }
            }
        }
        if (match != null)
            return match;
        // The bounded resistive envelope owns J1 outside the functional
        // contribution namespace. Give it a deterministic device identity.
        if ("J1".equals(componentId))
            return deviceOwnedComponentId(namespace, componentId);
        throw mismatch("physical.component", "Unknown component identity");
    }

    private static String durablePhysicalInputId(BoundedAssemblyPlan plan,
            BlockNamespace namespace, String inputId) {
        if (inputId == null || inputId.length() == 0)
            throw mismatch("physical.input", "Missing power input identity");
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters()) {
            if (inputId.equals(adapter.getExternalInputId()))
                return deviceOwnedInputId(namespace, adapter.getComponentLocalId(),
                        inputId);
        }
        // Bounded v1 has one board-owned J1 input and no adapter descriptor.
        if ("VIN_INPUT".equals(inputId))
            return deviceOwnedInputId(namespace, "J1", inputId);
        throw mismatch("physical.input", "Unknown power input identity");
    }

    private static String deviceOwnedPrefix(BlockNamespace namespace) {
        return "tsj-device-v1/" + namespace.getDeviceSchemaId() + "@"
                + namespace.getDeviceSchemaVersion();
    }

    private static String deviceOwnedComponentId(BlockNamespace namespace,
            String componentId) {
        return deviceOwnedPrefix(namespace) + "/component/" + componentId;
    }

    private static String deviceOwnedInputId(BlockNamespace namespace,
            String componentId, String inputId) {
        return deviceOwnedComponentId(namespace, componentId) + "/input/"
                + inputId;
    }

    private static void addDeviceOwnedEnvelopeTargets(BlockNamespace namespace,
            String originalComponentId, String durableComponent,
            PhysicalPackageGeometry geometry, List<String> targets) {
        if (!durableComponent.startsWith("tsj-device-v1/"))
            return;
        addTarget(targets, durableComponent);
        for (String terminal : geometry.getTerminalIds()) {
            addTarget(targets, durableComponent + "/pad/" + terminal);
            addTarget(targets, durableComponent + "/terminal/" + terminal);
        }
    }
    private static void addValue(List<RealizationManifest.Choice> choices,
            String key, Value value) {
        if (value == null)
            throw new IllegalArgumentException("Missing parameter value " + key);
        switch (value.getKind()) {
        case BOOLEAN:
            addToken(choices, key, Boolean.toString(value.getBoolean()));
            break;
        case INTEGER:
            addInteger(choices, key, value.getInteger());
            break;
        case DECIMAL:
            addNumber(choices, key, value.getDecimal());
            break;
        case TEXT:
            addToken(choices, key, value.getText());
            break;
        default:
            throw new IllegalArgumentException("Unsupported parameter value");
        }
    }

    private static void addToken(List<RealizationManifest.Choice> choices,
            String key, String value) {
        choices.add(RealizationManifest.Choice.token(key, value));
    }

    private static void addInteger(List<RealizationManifest.Choice> choices,
            String key, long value) {
        choices.add(RealizationManifest.Choice.integer(key, value));
    }

    private static void addNumber(List<RealizationManifest.Choice> choices,
            String key, double value) {
        choices.add(RealizationManifest.Choice.number(key, value));
    }

    private static void addIds(List<RealizationManifest.Choice> choices,
            String key, Collection<String> values) {
        choices.add(RealizationManifest.Choice.ids(key, values));
    }

    private static void addTarget(List<String> targets, String value) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing durable target");
        if (!targets.contains(value))
            targets.add(value);
    }
    /**
     * A saved repair/cut reference carries the complete identity that was
     * observed when the action was created.
     */
    static final class SavedActionReference {
        static final int VERSION = 1;
        private final int schemaVersion;
        private final String expectedIdentityCanonical;
        private final String target;

        SavedActionReference(int schemaVersion, String expectedIdentityCanonical,
                String target) {
            ChallengeContractException.positiveVersion(schemaVersion,
                    "saved.schemaVersion");
            if (schemaVersion != VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "saved.schemaVersion", "Unsupported saved action schema");
            if (expectedIdentityCanonical == null
                    || expectedIdentityCanonical.length() == 0)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "saved.identity", "Identity is required");
            RealizationManifest expected = RealizationManifest.parse(expectedIdentityCanonical);
            if (!expected.identityCanonical().equals(expectedIdentityCanonical))
                throw mismatch("saved.identity", "Saved identity must be canonical");
            this.schemaVersion = schemaVersion;
            this.expectedIdentityCanonical = expectedIdentityCanonical;
            this.target = savedTarget(target);
            if (!expected.getTargets().contains(this.target))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "saved.target", "Target is absent from realization");
        }

        static SavedActionReference capture(RealizationManifest manifest,
                String target) {
            if (manifest == null)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "manifest", "Manifest is required");
            return new SavedActionReference(VERSION,
                    manifest.identityCanonical(), target);
        }

        String resolve(RealizationManifest manifest) {
            if (manifest == null)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "manifest", "Manifest is required");
            if (!expectedIdentityCanonical.equals(manifest.identityCanonical()))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT,
                        "saved-action.realization", "STALE_REALIZATION");
            if (!manifest.getTargets().contains(target))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "saved.target", "Target is absent from realization");
            return target;
        }

        int getSchemaVersion() { return schemaVersion; }
        String getExpectedIdentityCanonical() { return expectedIdentityCanonical; }
        String getIdentityCanonical() { return expectedIdentityCanonical; }
        String getTarget() { return target; }

        private static String savedTarget(String value) {
            if (value == null || value.length() == 0 || value.length() > 512)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ID,
                        "saved.target", "Target is missing or too long");
            for (int index = 0; index < value.length(); index++) {
                char c = value.charAt(index);
                if (c < 0x21 || c > 0x7e || c == '|' || c == ','
                        || c == '=') {
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.INVALID_ID,
                            "saved.target", "Target has unsupported characters");
                }
            }
            return value;
        }
    }
}
