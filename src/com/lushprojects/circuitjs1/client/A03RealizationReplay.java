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
 * Pure capture and replay adapter for the current bounded assembly generator.
 *
 * <p>This class resolves and verifies the complete data identity before the
 * mutable assembler is entered.  It deliberately does not retain a runtime,
 * solver node, coordinate, or semantic signature.</p>
 */
final class A03RealizationReplay {
    static final int VERSION = 1;
    static final String REPLAY_ID = "bounded-realization-replay";

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

        return new RealizationManifest(RealizationManifest.VERSION,
                plan.getRequest().getDescriptor(),
                namespace.getRealizations().values(),
                versionPins(plan, physicalChoices),
                choices, nets, targets);
    }

    static BoundedAssemblyPlan decodeAndResolve(String encoded) {
        return resolve(RealizationManifest.parse(encoded));
    }

    static BoundedAssemblyPlan resolve(RealizationManifest manifest) {
        if (manifest == null)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD,
                    "manifest", "Manifest is required");
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
                && version == BoundedAssemblyRequest.GENERATOR_VERSION
                && BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId()))
            return BoundedAssemblyRequest.forControlledIndicator(descriptor);
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
                expectedVersion = SeededPcbLayoutGenerator.CURRENT_VERSION;
                break;
            case ROUTING:
            case MODELS:
            case PACKAGES:
                expectedId = ROUTING_ID;
                expectedVersion = generatorVersion;
                break;
            case VALUES:
                expectedId = BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId())
                        ? VALUES_ID : ROUTING_ID;
                expectedVersion = BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                        descriptor.getDeviceIntent().getId())
                        ? ControlledIndicatorValueSynthesis.VALUES_REVISION
                        : generatorVersion;
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
        if (BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                descriptor.getDeviceIntent().getId()))
            result.add(pin(RealizationManifest.VersionPin.Concern.VALUES,
                    VALUES_ID, ControlledIndicatorValueSynthesis.VALUES_REVISION));
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
    }

    private static List<String> durableIds(BlockNamespace namespace,
            String block, EntityKind kind, Collection<String> localIds,
            List<String> targets) {
        ArrayList<String> result = new ArrayList<String>();
        for (String local : localIds) {
            String semantic = namespace.idFor(block, kind, local);
            result.add(semantic);
            addTarget(targets, semantic);
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
            String semantic = namespace.idFor(block,
                    EntityKind.ENDPOINT, local);
            String terminal = namespace.terminalIdFor(block,
                    endpoint.getComponentId(), endpoint.getTerminalId());
            addToken(choices, "block." + block + ".endpoint." + local
                    + ".component", namespace.idFor(block,
                            EntityKind.COMPONENT, endpoint.getComponentId()));
            addToken(choices, "block." + block + ".endpoint." + local
                    + ".terminal", terminal);
            addTarget(targets, semantic);
            addTarget(targets, terminal);
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry
                : descriptor.getPads().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Pad pad = entry.getValue();
            addToken(choices, "block." + block + ".pad." + local
                    + ".endpoint", namespace.idFor(block,
                            EntityKind.ENDPOINT, pad.getEndpointId()));
            addToken(choices, "block." + block + ".pad." + local
                    + ".net", plan.netFor(block, pad.getNetId()));
        }
        for (String localNet : descriptor.getNetIds()) {
            addToken(choices, "block." + block + ".net." + localNet
                    + ".conductor", plan.netFor(block, localNet));
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Role> entry
                : descriptor.getRoles().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Role role = entry.getValue();
            addToken(choices, "block." + block + ".role." + local
                    + ".requirement", role.getRequirement().name());
            ArrayList<String> members = new ArrayList<String>();
            for (LocalRef member : role.getMembers()) {
                String semantic = namespace.idFor(block,
                        member.getKind(), member.getId());
                members.add(semantic);
                addTarget(targets, semantic);
            }
            addIds(choices, "block." + block + ".role." + local
                    + ".members", members);
        }
        for (Map.Entry<String, FunctionalBlockDescriptor.Port> entry
                : descriptor.getPorts().entrySet()) {
            String local = entry.getKey();
            FunctionalBlockDescriptor.Port port = entry.getValue();
            addToken(choices, "block." + block + ".port." + local
                    + ".role", namespace.idFor(block,
                            EntityKind.ROLE, port.getRoleId()));
            addToken(choices, "block." + block + ".port." + local
                    + ".attachment", namespace.idFor(block,
                            port.getAttachment().getKind(),
                            port.getAttachment().getId()));
        }
    }

    private static List<String> terminalIds(BlockNamespace namespace,
            String block, FunctionalBlockDescriptor.Component component,
            List<String> targets) {
        ArrayList<String> result = new ArrayList<String>();
        for (String terminal : component.getTerminalIds()) {
            String semantic = namespace.terminalIdFor(block,
                    component.getId(), terminal);
            result.add(semantic);
            addTarget(targets, semantic);
        }
        return result;
    }
    private static void captureBuses(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.NetBinding> nets,
            List<String> targets,
            List<RealizationManifest.Choice> choices) {
        Map<String, String> semanticNets = plan.getDeviceBuses().getNetBindings();
        if (!namespace.getSemanticNetIds().equals(semanticNets.keySet()))
            throw mismatch("nets", "Device buses do not cover namespace nets");
        for (Map.Entry<String, String> entry : semanticNets.entrySet()) {
            String alias = entry.getKey();
            String conductor = entry.getValue();
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
        for (DeviceBusBindings.Declaration declaration
                : plan.getDeviceBuses().getDeclarations()) {
            String semanticKey = declaration.getSemanticKey();
            String base = "bus.declaration." + semanticKey + ".";
            addToken(choices, base + "semantic-key", semanticKey);
            ArrayList<String> anchors = new ArrayList<String>();
            for (String anchor : declaration.getAnchorAliases()) {
                if (!namespace.getSemanticNetIds().contains(anchor))
                    throw mismatch("nets.declaration", "Unknown bus anchor");
                anchors.add(anchor);
                addTarget(targets, anchor);
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
                ports.add(namespace.idFor(ref.getBlockKey(),
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
        addInteger(choices, base + "version", SwitchedLowSideContract.VERSION);
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
        addPortRef(choices, base + "driver-control-port",
                contract.getDriverControlPort(), namespace);
        addPortRef(choices, base + "load-supply-port",
                contract.getLoadSupplyPort(), namespace);
        ArrayList<String> returns = new ArrayList<String>();
        for (ElectricalConnection.PortRef ref : contract.getReturnPorts())
            returns.add(namespace.idFor(ref.getBlockKey(),
                    EntityKind.PORT, ref.getPortId()));
        addIds(choices, base + "return-ports", returns);
    }

    private static void addPortRef(List<RealizationManifest.Choice> choices,
            String key, ElectricalConnection.PortRef ref,
            BlockNamespace namespace) {
        addToken(choices, key, namespace.idFor(ref.getBlockKey(),
                EntityKind.PORT, ref.getPortId()));
    }
    private static void captureAdapters(BoundedAssemblyPlan plan,
            BlockNamespace namespace,
            List<RealizationManifest.Choice> choices) {
        for (DeviceAdapterContract adapter : plan.getRequest().getDeviceAdapters()) {
            String base = "adapter." + adapter.getKey() + ".";
            addInteger(choices, base + "version", adapter.getVersion());
            addToken(choices, base + "component", namespace.idFor(
                    adapter.getKey(), EntityKind.COMPONENT,
                    adapter.getComponentLocalId()));
            addToken(choices, base + "external-input",
                    adapter.getExternalInputId());
            addToken(choices, base + "output-port", namespace.idFor(
                    adapter.getKey(), EntityKind.PORT,
                    adapter.getOutputPortId()));
            addToken(choices, base + "return-port", namespace.idFor(
                    adapter.getKey(), EntityKind.PORT,
                    adapter.getReturnPortId()));
        }
    }
    private static void captureContributions(BoundedAssemblyPlan plan,
            BlockNamespace namespace, List<RealizationManifest.Choice> choices) {
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        addInteger(choices, "construction.version", spec.getVersion());
        for (ElectricalRealizationSpec.ProviderDeclaration provider
                : spec.getProviderDeclarations().values()) {
            String base = "recipe." + provider.getOwnerKey() + ".";
            addToken(choices, base + "provider", provider.getProviderId() + "@"
                    + provider.getProviderVersion());
            for (Map.Entry<String, Value> choice : provider.getChoices().entrySet())
                addValue(choices, base + "choice." + choice.getKey(), choice.getValue());
            ComposedBlockContribution contribution = provider.getContribution();
            if (contribution != null) {
                ComposedBlockContribution.FaultSpec fault = contribution.getFaultSpec();
                if (fault != null) {
                    addToken(choices, base + "fault-kind", fault.getKind().name());
                    addToken(choices, base + "fault-target", namespace.idFor(provider.getOwnerKey(),
                            EntityKind.COMPONENT, fault.getTargetComponentLocalId()));
                    addNumber(choices, base + "fault-effective", fault.getEffectiveResistanceOhms());
                    addToken(choices, base + "repair", namespace.idFor(provider.getOwnerKey(),
                            EntityKind.COMPONENT, contribution.getRepairLocalComponentId()));
                }
                addIds(choices, base + "inputs", contribution.getInputRequirements());
                addIds(choices, base + "retests", contribution.getRetestRequirements());
            }
            for (ElectricalRealizationSpec.ElementDeclaration element : provider.getElements().values()) {
                String local = base + "element." + element.getElementId() + ".";
                addToken(choices, local + "kind", element.getKind());
                if (element.getComponentId() != null)
                    addToken(choices, local + "component", element.getComponentId());
                if (element.getModelId() != null)
                    addToken(choices, local + "model", element.getModelId());
                for (Map.Entry<String, Integer> post : element.getPostIndexByTerminal().entrySet())
                    addInteger(choices, local + "post." + post.getKey(), post.getValue());
                for (Map.Entry<String, Double> parameter : element.getParameters().entrySet())
                    addNumber(choices, local + "parameter." + parameter.getKey(), parameter.getValue());
            }
        }
        for (Map.Entry<String, ElectricalRealizationSpec.PhysicalUnitSpec> entry : spec.getPhysicalUnits().entrySet()) {
            ElectricalRealizationSpec.PhysicalUnitSpec unit = entry.getValue();
            String base = "construction.unit." + entry.getKey() + ".";
            addToken(choices, base + "component", unit.getComponentId());
            addToken(choices, base + "package", unit.getPackageId());
            for (Map.Entry<String, String> terminal : unit.getPackageTerminalByUnitTerminal().entrySet())
                addToken(choices, base + "terminal." + terminal.getKey(), terminal.getValue());
        }
        for (Map.Entry<String, ElectricalRealizationSpec.TerminalMapping> entry : spec.getTerminalMappings().entrySet()) {
            String base = "construction.terminal." + entry.getKey() + ".";
            ElectricalRealizationSpec.TerminalMapping terminal = entry.getValue();
            addToken(choices, base + "backing", terminal.getComponentEndpoint().toString());
            addToken(choices, base + "net", terminal.getNetId());
        }
        for (Map.Entry<String, ElectricalRealizationSpec.BoardEndpointSpec> entry : spec.getBoardEndpoints().entrySet()) {
            String base = "construction.pad." + entry.getKey() + ".";
            addToken(choices, base + "backing", entry.getValue().getEndpoint().toString());
            if (entry.getValue().getAttachmentElementId() != null)
                addToken(choices, base + "attachment", entry.getValue().getAttachmentElementId());
        }
        for (ElectricalRealizationSpec.BridgeSpec bridge : spec.getBridgeSpecs().values()) {
            String base = "construction.bridge." + bridge.getBridgeElementId() + ".";
            addToken(choices, base + "first", bridge.getFirst().toString());
            addToken(choices, base + "second", bridge.getSecond().toString());
            if (bridge.getSemanticJoinId() != null)
                addToken(choices, base + "join", bridge.getSemanticJoinId());
            if (bridge.getExternalPowerInputId() != null)
                addToken(choices, base + "input", bridge.getExternalPowerInputId());
        }
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

    /** Accept only the current declared component or device-owned envelope. */
    private static String durablePhysicalComponentId(BlockNamespace namespace,
            String componentId) {
        if (componentId == null || componentId.length() == 0)
            throw mismatch("physical.component", "Missing component identity");
        for (Map.Entry<String, FunctionalBlockDescriptor> block
                : namespace.getBlocks().entrySet()) {
            for (String local : block.getValue().getComponents().keySet()) {
                String semantic = namespace.idFor(block.getKey(),
                        EntityKind.COMPONENT, local);
                if (componentId.equals(semantic)) return componentId;
            }
        }
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
}
