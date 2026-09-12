package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Pure, immutable construction contract owned by one resolved assembly plan.
 *
 * <p>This is deliberately a data model rather than a second resolver.  The
 * contribution objects and (for the value-enabled controlled route) the exact
 * resolved recipe are passed through by identity from the plan.</p>
 */
final class ElectricalRealizationSpec {
    static final int VERSION = 2;
    static final String LEGACY_POWER_INPUT_ID = "VIN_INPUT";
    // Bounded source envelope. Local models are declared by their providers.
    static final double EXTERNAL_SUPPLY_VOLTS = 5.0;

    private final int version;
    private final Map<String, ProviderDeclaration> providerDeclarations;
    private final Map<String, ElementDeclaration> elementDeclarations;
    private final Map<String, PhysicalUnitSpec> physicalUnits;
    private final Map<String, TerminalMapping> terminalMappings;
    private final Map<String, DeviceJoinSpec> deviceJoins;
    private final Map<String, BridgeSpec> bridgeSpecs;
    private final Map<String, SolverReservation> solverReservations;
    private final ElectricalUnitPackageMap packageMap;
    private final Map<String, String> componentIds;
    private final Map<String, String> padIds;
    private final Map<String, PadBindingSpec> padBindings;
    private final Map<String, PowerInputSpec> powerInputs;
    private final Set<String> requiredConnectionPadIds;
    private final Map<String, BoardEndpointSpec> boardEndpoints;

    private ElectricalRealizationSpec(int version,
            Map<String, ProviderDeclaration> providerDeclarations,
            Map<String, ElementDeclaration> elementDeclarations,
            Map<String, PhysicalUnitSpec> physicalUnits,
            Map<String, TerminalMapping> terminalMappings,
            Map<String, DeviceJoinSpec> deviceJoins,
            Map<String, BridgeSpec> bridgeSpecs,
            Map<String, SolverReservation> solverReservations,
            ElectricalUnitPackageMap packageMap,
            Map<String, String> componentIds, Map<String, String> padIds,
            Map<String, PadBindingSpec> padBindings,
            Map<String, PowerInputSpec> powerInputs,
            Collection<String> requiredConnectionPadIds,
            Map<String, BoardEndpointSpec> boardEndpoints) {
        if (version != VERSION)
            throw new IllegalArgumentException("Unsupported electrical spec version");
        if (packageMap == null)
            throw new IllegalArgumentException("Electrical package map is required");
        this.version = version;
        this.providerDeclarations = immutable(providerDeclarations);
        this.elementDeclarations = immutable(elementDeclarations);
        this.physicalUnits = immutable(physicalUnits);
        this.terminalMappings = immutable(terminalMappings);
        this.deviceJoins = immutable(deviceJoins);
        this.bridgeSpecs = immutable(bridgeSpecs);
        this.solverReservations = immutable(solverReservations);
        this.packageMap = packageMap;
        this.componentIds = immutable(componentIds);
        this.padIds = immutable(padIds);
        this.padBindings = immutable(padBindings);
        this.powerInputs = immutable(powerInputs);
        this.boardEndpoints = immutable(boardEndpoints);
        this.requiredConnectionPadIds = immutableSet(requiredConnectionPadIds,
                "required connection pads");
    }

    private static <T> Map<String, T> immutable(Map<String, T> source) {
        if (source == null)
            throw new IllegalArgumentException("Electrical spec map is required");
        return Collections.unmodifiableMap(new TreeMap<String, T>(source));
    }

    int getVersion() { return version; }
    Map<String, ProviderDeclaration> getProviderDeclarations() {
        return providerDeclarations;
    }
    Map<String, ElementDeclaration> getElementDeclarations() {
        return elementDeclarations;
    }
    Map<String, PhysicalUnitSpec> getPhysicalUnits() { return physicalUnits; }
    Map<String, TerminalMapping> getTerminalMappings() { return terminalMappings; }
    Map<String, DeviceJoinSpec> getDeviceJoins() { return deviceJoins; }
    Map<String, BridgeSpec> getBridgeSpecs() { return bridgeSpecs; }
    Map<String, SolverReservation> getSolverReservations() {
        return solverReservations;
    }
    ElectricalUnitPackageMap getPackageMap() { return packageMap; }

    ProviderDeclaration getProviderDeclaration(String ownerKey) {
        return providerDeclarations.get(ownerKey);
    }

    ElementDeclaration getElementDeclaration(String ownerKey, String elementId) {
        return elementDeclarations.get(elementKey(ownerKey, elementId));
    }

    String getComponentId(String ownerKey, String localId) {
        return required(componentIds.get(elementKey(ownerKey, localId)),
                "component " + ownerKey + "/" + localId);
    }

    String getPadId(String ownerKey, String localId) {
        return required(padIds.get(elementKey(ownerKey, localId)),
                "pad " + ownerKey + "/" + localId);
    }

    boolean hasPad(String ownerKey, String localId) {
        return padIds.containsKey(elementKey(ownerKey, localId));
    }

    Map<String, PadBindingSpec> getPadBindings() { return padBindings; }

    PadBindingSpec getPadBinding(String padId) { return padBindings.get(padId); }

    Map<String, PowerInputSpec> getPowerInputs() { return powerInputs; }

    Set<String> getRequiredConnectionPadIds() { return requiredConnectionPadIds; }
    Map<String, BoardEndpointSpec> getBoardEndpoints() { return boardEndpoints; }

    Set<String> getExpectedNetIds() {
        TreeSet<String> result = new TreeSet<String>();
        for (PadBindingSpec pad : padBindings.values())
            result.add(pad.getNetId());
        return Collections.unmodifiableSet(result);
    }

    TerminalMapping getTerminalMapping(String ownerKey, String localId,
            String terminalId) {
        return terminalMappings.get(terminalKey(ownerKey, localId, terminalId));
    }

    static String elementKey(String ownerKey, String elementId) {
        return required(ownerKey, "ownerKey") + "/" + required(elementId, "elementId");
    }

    static String terminalKey(String ownerKey, String localId, String terminalId) {
        return elementKey(ownerKey, localId) + "/" + required(terminalId, "terminalId");
    }

    private static String required(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private static Set<String> immutableSet(Collection<String> values, String field) {
        if (values == null)
            throw new IllegalArgumentException("Missing " + field);
        TreeSet<String> result = new TreeSet<String>();
        for (String value : values)
            if (value == null || value.length() == 0 || !result.add(value))
                throw new IllegalArgumentException("Invalid or duplicate " + field);
        return Collections.unmodifiableSet(result);
    }

    /** Provider-owned declaration and its immutable pure contribution. */
    static final class ProviderDeclaration {
        private final String ownerKey;
        private final String providerId;
        private final int providerVersion;
        private final ComposedBlockContribution contribution;
        private final List<String> elementIds;
        private final List<String> unitIds;
        private final List<String> terminalIds;
        private final List<String> joinIds;
        private final boolean deviceOwner;
        private final Map<String, ElementDeclaration> elements;
        private final Map<String, FunctionalBlockDescriptor.Value> choices;

        ProviderDeclaration(String ownerKey, String providerId, int providerVersion,
                ComposedBlockContribution contribution, Collection<String> elementIds,
                Collection<String> unitIds, Collection<String> terminalIds,
                Collection<String> joinIds, boolean deviceOwner) {
            this(ownerKey, providerId, providerVersion, contribution, elementIds, unitIds,
                    terminalIds, joinIds, deviceOwner, Collections.<String, ElementDeclaration>emptyMap());
        }

        ProviderDeclaration(String ownerKey, String providerId, int providerVersion,
                ComposedBlockContribution contribution, Collection<String> elementIds,
                Collection<String> unitIds, Collection<String> terminalIds,
                Collection<String> joinIds, boolean deviceOwner,
                Map<String, ElementDeclaration> allElements) {
            this(ownerKey, providerId, providerVersion, contribution, elementIds, unitIds,
                    terminalIds, joinIds, deviceOwner, allElements,
                    Collections.<String, FunctionalBlockDescriptor.Value>emptyMap());
        }

        ProviderDeclaration(String ownerKey, String providerId, int providerVersion,
                ComposedBlockContribution contribution, Collection<String> elementIds,
                Collection<String> unitIds, Collection<String> terminalIds,
                Collection<String> joinIds, boolean deviceOwner,
                Map<String, ElementDeclaration> allElements,
                Map<String, FunctionalBlockDescriptor.Value> choices) {
            this.choices = immutable(choices);
            this.ownerKey = required(ownerKey, "provider.ownerKey");
            this.providerId = required(providerId, "provider.providerId");
            if (providerVersion < 1)
                throw new IllegalArgumentException("Invalid provider version");
            this.providerVersion = providerVersion;
            this.contribution = contribution;
            this.elementIds = immutableList(elementIds, "provider.elementIds");
            this.unitIds = immutableList(unitIds, "provider.unitIds");
            this.terminalIds = immutableList(terminalIds, "provider.terminalIds");
            this.joinIds = immutableList(joinIds, "provider.joinIds");
            this.deviceOwner = deviceOwner;
            TreeMap<String, ElementDeclaration> local = new TreeMap<String, ElementDeclaration>();
            for (ElementDeclaration element : allElements.values())
                if (ownerKey.equals(element.getOwnerKey())) local.put(element.getElementId(), element);
            this.elements = Collections.unmodifiableMap(local);
        }

        String getOwnerKey() { return ownerKey; }
        String getProviderId() { return providerId; }
        String getProviderTypeId() { return providerId; }
        int getProviderVersion() { return providerVersion; }
        int getVersion() { return providerVersion; }
        ComposedBlockContribution getContribution() { return contribution; }
        List<String> getElementIds() { return elementIds; }
        List<String> getUnitIds() { return unitIds; }
        List<String> getTerminalIds() { return terminalIds; }
        List<String> getJoinIds() { return joinIds; }
        boolean isDeviceOwner() { return deviceOwner; }
        ElementDeclaration getElement(String id) {
            ElementDeclaration result = elements.get(id);
            if (result == null) throw new IllegalArgumentException("Undeclared provider element " + ownerKey + "/" + id);
            return result;
        }
        Map<String, ElementDeclaration> getElements() { return elements; }
        Map<String, FunctionalBlockDescriptor.Value> getChoices() { return choices; }

        private static List<String> immutableList(Collection<String> values, String field) {
            if (values == null)
                throw new IllegalArgumentException("Missing " + field);
            TreeSet<String> sorted = new TreeSet<String>();
            for (String value : values) {
                if (value == null || value.length() == 0 || !sorted.add(value))
                    throw new IllegalArgumentException("Invalid or duplicate " + field);
            }
            return Collections.unmodifiableList(new ArrayList<String>(sorted));
        }
    }

    /** One actual CircuitJS element declaration, including explicit post roles. */
    static final class ElementDeclaration {
        private final String ownerKey;
        private final String elementId;
        private final String kind;
        private final String componentId;
        private final Map<String, Integer> postIndexByTerminal;
        private final String modelId;
        private final Map<String, Double> parameters;

        ElementDeclaration(String ownerKey, String elementId, String kind,
                String componentId, Map<String, Integer> postIndexByTerminal) {
            this(ownerKey, elementId, kind, componentId, postIndexByTerminal, null,
                    Collections.<String, Double>emptyMap());
        }

        ElementDeclaration(String ownerKey, String elementId, String kind,
                String componentId, Map<String, Integer> postIndexByTerminal,
                String modelId, Map<String, Double> parameters) {
            this.modelId = modelId;
            if (parameters == null) throw new IllegalArgumentException("Element choices are required");
            TreeMap<String, Double> choices = new TreeMap<String, Double>();
            for (Map.Entry<String, Double> entry : parameters.entrySet()) {
                if (entry.getValue() == null || !ComposedBlockContribution.finite(entry.getValue()))
                    throw new IllegalArgumentException("Element choice must be finite");
                choices.put(required(entry.getKey(), "element.parameter"), entry.getValue());
            }
            this.parameters = Collections.unmodifiableMap(choices);
            this.ownerKey = required(ownerKey, "element.ownerKey");
            this.elementId = required(elementId, "element.elementId");
            this.kind = required(kind, "element.kind");
            this.componentId = componentId;
            if (postIndexByTerminal == null || postIndexByTerminal.isEmpty())
                throw new IllegalArgumentException("Element terminals are required");
            TreeMap<String, Integer> posts = new TreeMap<String, Integer>();
            for (Map.Entry<String, Integer> entry : postIndexByTerminal.entrySet()) {
                if (entry.getKey() == null || entry.getKey().length() == 0 ||
                        entry.getValue() == null || entry.getValue().intValue() < 0 ||
                        posts.put(entry.getKey(), entry.getValue()) != null)
                    throw new IllegalArgumentException("Invalid element terminal declaration");
            }
            this.postIndexByTerminal = Collections.unmodifiableMap(posts);
            validatePrimitive();
        }

        // These are CircuitJS primitive categories, independent of device families.
        private void validatePrimitive() {
            Map<String, Integer> expected;
            if ("NMOS".equals(kind)) expected = posts("G", 0, "D", 2, "S", 1);
            else if ("NPN".equals(kind)) expected = posts("B", 0, "C", 1, "E", 2);
            else if ("LED".equals(kind)) expected = posts("A", 0, "K", 1);
            else if ("VOLTAGE".equals(kind)) expected = posts("+", 1, "-", 0);
            else if ("GROUND".equals(kind)) expected = posts("1", 0);
            else if ("RESISTOR".equals(kind) || "WIRE".equals(kind) ||
                    "SWITCH".equals(kind) || "FAULT_HELPER".equals(kind))
                expected = posts("1", 0, "2", 1);
            else throw new IllegalArgumentException("Unknown electrical primitive " + kind);
            if (!expected.equals(postIndexByTerminal))
                throw new IllegalArgumentException("Incorrect CircuitJS terminal mapping for " + kind);
            if ("RESISTOR".equals(kind)) positiveParameter("resistance");
            if ("VOLTAGE".equals(kind)) getParameter("voltage");
            if ("NMOS".equals(kind)) {
                positiveParameter("threshold");
                positiveParameter("beta");
                if (!"NMOS".equals(modelId))
                    throw new IllegalArgumentException("Unsupported NMOS primitive model " + modelId);
            }
            if ("NPN".equals(kind)) positiveParameter("beta");
            if ("NMOS".equals(kind) || "NPN".equals(kind) || "LED".equals(kind))
                required(modelId, "primitive.model");
            else if (modelId != null)
                throw new IllegalArgumentException("Model is not applicable to " + kind);
        }

        private void positiveParameter(String name) {
            if (getParameter(name) <= 0)
                throw new IllegalArgumentException("Invalid primitive parameter " + name);
        }

        String getOwnerKey() { return ownerKey; }
        String getElementId() { return elementId; }
        String getKind() { return kind; }
        String getModelId() { return modelId; }
        Map<String, Double> getParameters() { return parameters; }
        double getParameter(String name) {
            Double value = parameters.get(name);
            if (value == null) throw new IllegalArgumentException("Missing element choice " + name);
            return value.doubleValue();
        }
        String getComponentId() { return componentId; }
        Collection<String> getTerminalIds() { return postIndexByTerminal.keySet(); }
        Map<String, Integer> getPostIndexByTerminal() { return postIndexByTerminal; }
        int getPostIndex(String terminalId) {
            Integer index = postIndexByTerminal.get(terminalId);
            if (index == null)
                throw new IllegalArgumentException("Undeclared element terminal " + terminalId);
            return index.intValue();
        }
    }

    /** Pure physical electrical unit and public package terminal declaration. */
    static final class PhysicalUnitSpec {
        private final String ownerKey;
        private final String unitId;
        private final String componentId;
        private final String packageId;
        private final Map<String, String> packageTerminalByUnitTerminal;

        PhysicalUnitSpec(String ownerKey, String unitId, String componentId,
                String packageId, Map<String, String> packageTerminalByUnitTerminal) {
            this.ownerKey = required(ownerKey, "unit.ownerKey");
            this.unitId = required(unitId, "unit.unitId");
            this.componentId = required(componentId, "unit.componentId");
            this.packageId = required(packageId, "unit.packageId");
            this.packageTerminalByUnitTerminal = immutable(packageTerminalByUnitTerminal);
            if (this.packageTerminalByUnitTerminal.isEmpty())
                throw new IllegalArgumentException("Unit package terminals are required");
        }

        String getOwnerKey() { return ownerKey; }
        String getUnitId() { return unitId; }
        String getComponentId() { return componentId; }
        String getPackageId() { return packageId; }
        Map<String, String> getPackageTerminalByUnitTerminal() {
            return packageTerminalByUnitTerminal;
        }
    }

    /** Mapping from a public logical terminal to package terminal and board net. */
    static final class TerminalMapping {
        private final String ownerKey;
        private final String localId;
        private final String terminalId;
        private final String componentId;
        private final String packageId;
        private final String packageTerminalId;
        private final String netId;
        private final EndpointRef componentEndpoint;

        TerminalMapping(String ownerKey, String localId, String terminalId,
                String componentId, String packageId, String packageTerminalId,
                String netId, EndpointRef componentEndpoint) {
            this.ownerKey = required(ownerKey, "terminal.ownerKey");
            this.localId = required(localId, "terminal.localId");
            this.terminalId = required(terminalId, "terminal.terminalId");
            this.componentId = required(componentId, "terminal.componentId");
            this.packageId = required(packageId, "terminal.packageId");
            this.packageTerminalId = required(packageTerminalId, "terminal.packageTerminalId");
            this.netId = required(netId, "terminal.netId");
            if (componentEndpoint == null)
                throw new IllegalArgumentException("Component terminal representation is required");
            this.componentEndpoint = componentEndpoint;
        }

        String getOwnerKey() { return ownerKey; }
        String getLocalId() { return localId; }
        String getTerminalId() { return terminalId; }
        String getComponentId() { return componentId; }
        String getPackageId() { return packageId; }
        String getPackageTerminalId() { return packageTerminalId; }
        String getNetId() { return netId; }
        EndpointRef getComponentEndpoint() { return componentEndpoint; }
    }

    /** A plan-authorized cross-owner join; no live handle is retained here. */
    static final class DeviceJoinSpec {
        private final String joinId;
        private final List<String> terminalRefs;
        private final String kind;

        DeviceJoinSpec(String joinId, Collection<String> terminalRefs, String kind) {
            this.joinId = required(joinId, "join.joinId");
            this.kind = required(kind, "join.kind");
            if (terminalRefs == null || terminalRefs.size() < 2)
                throw new IllegalArgumentException("A device join needs two terminals");
            TreeSet<String> sorted = new TreeSet<String>();
            for (String ref : terminalRefs)
                if (ref == null || ref.length() == 0 || !sorted.add(ref))
                    throw new IllegalArgumentException("Invalid device join terminal");
            this.terminalRefs = Collections.unmodifiableList(new ArrayList<String>(sorted));
        }

        String getJoinId() { return joinId; }
        List<String> getTerminalRefs() { return terminalRefs; }
        String getKind() { return kind; }
    }

    /** One device-owned conductive bridge with ordered, exact endpoints. */
    static final class BridgeSpec {
        private final String bridgeElementId;
        private final EndpointRef first;
        private final EndpointRef second;
        private final String semanticJoinId;
        private final String externalPowerInputId;

        BridgeSpec(String bridgeElementId, EndpointRef first, EndpointRef second,
                String semanticJoinId, String externalPowerInputId) {
            this.bridgeElementId = required(bridgeElementId, "bridge.elementId");
            if (first == null || second == null)
                throw new IllegalArgumentException("Bridge endpoints are required");
            if (first.equals(second))
                throw new IllegalArgumentException("Bridge endpoints must differ");
            this.first = first;
            this.second = second;
            if ((semanticJoinId == null) == (externalPowerInputId == null))
                throw new IllegalArgumentException("Bridge requires exactly one declared origin");
            this.semanticJoinId = semanticJoinId;
            this.externalPowerInputId = externalPowerInputId;
        }

        String getBridgeElementId() { return bridgeElementId; }
        EndpointRef getFirst() { return first; }
        EndpointRef getSecond() { return second; }
        String getSemanticJoinId() { return semanticJoinId; }
        String getExternalPowerInputId() { return externalPowerInputId; }
    }

    /** Exact owner/element/terminal identity used by a bridge contract. */
    static final class EndpointRef {
        private final String ownerKey;
        private final String elementId;
        private final String terminalId;

        EndpointRef(String ownerKey, String elementId, String terminalId) {
            this.ownerKey = required(ownerKey, "endpoint.ownerKey");
            this.elementId = required(elementId, "endpoint.elementId");
            this.terminalId = required(terminalId, "endpoint.terminalId");
        }

        String getOwnerKey() { return ownerKey; }
        String getElementId() { return elementId; }
        String getTerminalId() { return terminalId; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof EndpointRef)) return false;
            EndpointRef value = (EndpointRef) other;
            return ownerKey.equals(value.ownerKey) && elementId.equals(value.elementId)
                    && terminalId.equals(value.terminalId);
        }

        @Override public int hashCode() {
            return (ownerKey + "\n" + elementId + "\n" + terminalId).hashCode();
        }

        @Override public String toString() {
            return ownerKey + "/" + elementId + "." + terminalId;
        }
    }

    /** Frozen expected physical board pad identity and resolved net. */
    static final class PadBindingSpec {
        private final String padId;
        private final String ownerKey;
        private final String localId;
        private final String terminalId;
        private final String componentId;
        private final String netId;

        PadBindingSpec(String padId, String ownerKey, String localId,
                String terminalId, String componentId, String netId) {
            this.padId = required(padId, "padBinding.padId");
            this.ownerKey = required(ownerKey, "padBinding.ownerKey");
            this.localId = required(localId, "padBinding.localId");
            this.terminalId = required(terminalId, "padBinding.terminalId");
            this.componentId = required(componentId, "padBinding.componentId");
            this.netId = required(netId, "padBinding.netId");
        }

        String getPadId() { return padId; }
        String getOwnerKey() { return ownerKey; }
        String getLocalId() { return localId; }
        String getTerminalId() { return terminalId; }
        String getComponentId() { return componentId; }
        String getNetId() { return netId; }
    }

    /** Frozen external power identity expected on the candidate board. */
    static final class PowerInputSpec {
        private final String inputId;
        private final String positivePadId;
        private final String returnPadId;
        private final String positiveNetId;
        private final String returnNetId;

        PowerInputSpec(String inputId, String positivePadId, String returnPadId,
                String positiveNetId, String returnNetId) {
            this.inputId = required(inputId, "power.inputId");
            this.positivePadId = required(positivePadId, "power.positivePadId");
            this.returnPadId = required(returnPadId, "power.returnPadId");
            this.positiveNetId = required(positiveNetId, "power.positiveNetId");
            this.returnNetId = required(returnNetId, "power.returnNetId");
        }

        String getInputId() { return inputId; }
        String getPositivePadId() { return positivePadId; }
        String getReturnPadId() { return returnPadId; }
        String getPositiveNetId() { return positiveNetId; }
        String getReturnNetId() { return returnNetId; }
    }

    static final class BoardEndpointSpec {
        private final String ownerKey, localPadId;
        private final EndpointRef endpoint;
        private final String attachmentElementId;
        BoardEndpointSpec(String ownerKey, String localPadId, EndpointRef endpoint,
                String attachmentElementId) {
            this.ownerKey = required(ownerKey, "boardEndpoint.owner");
            this.localPadId = required(localPadId, "boardEndpoint.pad");
            if (endpoint == null) throw new IllegalArgumentException("Board endpoint is required");
            if (attachmentElementId != null && ownerKey.equals(endpoint.getOwnerKey()) &&
                    attachmentElementId.equals(endpoint.getElementId()))
                throw new IllegalArgumentException("Board endpoint cannot be its detachable attachment");
            this.endpoint = endpoint;
            this.attachmentElementId = attachmentElementId;
        }
        String getOwnerKey() { return ownerKey; }
        String getLocalPadId() { return localPadId; }
        EndpointRef getEndpoint() { return endpoint; }
        String getAttachmentElementId() { return attachmentElementId; }
    }

    /** Bounded private solver witness reservation, independent of PCB geometry. */
    static final class SolverReservation {
        private final String ownerKey;
        private final String role;
        private final int originX;
        private final int originY;
        private final int width;
        private final int height;

        SolverReservation(String ownerKey, String role, int originX, int originY,
                int width, int height) {
            this.ownerKey = required(ownerKey, "reservation.ownerKey");
            this.role = required(role, "reservation.role");
            if (originX < 0 || originY < 0 || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid bounded solver reservation");
            long right = (long) originX + (long) width;
            long bottom = (long) originY + (long) height;
            if (right > Integer.MAX_VALUE || bottom > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Solver reservation overflow");
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }

        String getOwnerKey() { return ownerKey; }
        String getRole() { return role; }
        int getOriginX() { return originX; }
        int getOriginY() { return originY; }
        int getWidth() { return width; }
        int getHeight() { return height; }
    }

    /** Build the spec from already resolved plan contributions and declarations. */
    static ElectricalRealizationSpec fromResolved(BoundedAssemblyRequest request,
            BlockNamespace namespace, Map<String, ComposedBlockContribution> blocks,
            Collection<DeviceAdapterContract> adapters, Map<String, String> netAliases,
            boolean controlled) {
        if (request == null || namespace == null || blocks == null || adapters == null ||
                netAliases == null)
            throw new IllegalArgumentException("Electrical spec inputs are required");

        Builder builder = new Builder(request, namespace, netAliases, controlled);
        for (Map.Entry<String, ComposedBlockContribution> entry : new TreeMap<String, ComposedBlockContribution>(blocks).entrySet())
            builder.addContribution(entry.getKey(), entry.getValue());
        builder.addJoins(request.getConnections());
        builder.addDeviceDeclarations(adapters);
        return builder.finish();
    }

    /** Restricted pure declaration scope; local names acquire exactly one owner. */
    static final class ContributionBuilder {
        private final Builder parent;
        private final String owner;
        private final ComposedBlockContribution contribution;
        private final List<String> elementIds = new ArrayList<String>();
        private final List<String> unitIds = new ArrayList<String>();
        private final List<String> terminalIds = new ArrayList<String>();
        private final Map<String, FunctionalBlockDescriptor.Value> choices =
                new TreeMap<String, FunctionalBlockDescriptor.Value>();
        private boolean finished;

        private ContributionBuilder(Builder parent, String owner,
                ComposedBlockContribution contribution) {
            this.parent = parent;
            this.owner = owner;
            this.contribution = contribution;
        }

        void resistor(ComposedBlockContribution.ResistorRecipe recipe) {
            open();
            if (recipe == null) throw new IllegalArgumentException("Resistor recipe is required");
            String local = recipe.getComponentLocalId();
            choice(local + ".rated-watts", FunctionalBlockDescriptor.Value.ofDecimal(recipe.getRatedWatts()));
            choice(local + ".tolerance-percent", FunctionalBlockDescriptor.Value.ofDecimal(recipe.getTolerancePercent()));
            choice(local + ".mutable", FunctionalBlockDescriptor.Value.ofBoolean(recipe.isMutable()));
            if (recipe.getCatalogEntryId() != null)
                choice(local + ".catalog", FunctionalBlockDescriptor.Value.ofText(recipe.getCatalogEntryId()));
            component(local, "RESISTOR", parent.packageFor(recipe), null,
                    numbers("resistance", recipe.getResistanceOhms()), posts("1", 0, "2", 1));
            if (recipe.isMutable())
                helper(local + "_SECONDARY", "FAULT_HELPER", local, posts("1", 0, "2", 1));
        }

        void choice(String id, FunctionalBlockDescriptor.Value value) {
            open();
            if (value == null || choices.put(required(id, "local.choice"), value) != null)
                throw new IllegalArgumentException("Missing or duplicate local choice");
        }

        void component(String local, String kind, PhysicalPackage physicalPackage,
                String model, Map<String, Double> parameters, Map<String, Integer> posts) {
            open();
            FunctionalBlockDescriptor descriptor = contribution.getDescriptor();
            FunctionalBlockDescriptor.Component component = descriptor.getComponents().get(local);
            if (component == null || physicalPackage == null ||
                    !new TreeSet<String>(component.getTerminalIds()).equals(posts.keySet()) ||
                    !new TreeSet<String>(physicalPackage.getTerminalIds()).equals(posts.keySet()) ||
                    unitIds.contains(local))
                throw new IllegalArgumentException("Component/package/terminal declaration mismatch " + owner + "/" + local);
            if (!kind.equals(component.getTypeId()))
                throw new IllegalArgumentException("Primitive does not match public component category");
            String componentId = parent.namespace.idFor(owner, EntityKind.COMPONENT, local);
            parent.componentIds.put(elementKey(owner, local), componentId);
            parent.addElement(owner, local, kind, componentId, posts, model, parameters);
            elementIds.add(local);
            List<String> names = new ArrayList<String>(posts.keySet());
            parent.addUnit(owner, local, componentId, physicalPackage, names, names);
            unitIds.add(local);
            TreeSet<String> mapped = new TreeSet<String>();
            for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry : descriptor.getPads().entrySet()) {
                FunctionalBlockDescriptor.Pad pad = entry.getValue();
                FunctionalBlockDescriptor.Endpoint endpoint = descriptor.getEndpoints().get(pad.getEndpointId());
                if (!local.equals(endpoint.getComponentId())) continue;
                String terminal = endpoint.getTerminalId();
                if (!mapped.add(terminal))
                    throw new IllegalArgumentException("Duplicate physical terminal pad");
                String padLocal = entry.getKey();
                String padId = parent.namespace.idFor(owner, EntityKind.PAD, padLocal);
                parent.padIds.put(elementKey(owner, padLocal), padId);
                String net = parent.padNet(owner, padLocal);
                ComposedBlockContribution.ResistorRecipe resistor = contribution.getResistor(local);
                boolean detachable = resistor != null && resistor.isMutable();
                EndpointRef componentEndpoint = new EndpointRef(owner,
                        detachable && "2".equals(terminal) ? local + "_SECONDARY" : local, terminal);
                parent.terminals.put(terminalKey(owner, local, terminal), new TerminalMapping(
                        owner, local, terminal, componentId, physicalPackage.getId(), terminal, net, componentEndpoint));
                parent.addPadBindingForLocalPad(owner, local, padLocal, componentId, terminal, net);
                if (detachable) parent.requiredConnectionPadIds.add(padId);
                terminalIds.add(padLocal);
            }
            if (!mapped.equals(posts.keySet()))
                throw new IllegalArgumentException("Component physical pads are incomplete " + owner + "/" + local);
        }

        void helper(String local, String kind, String componentLocal,
                Map<String, Integer> posts) {
            open();
            String componentId = componentLocal == null ? null :
                    parent.componentIds.get(elementKey(owner, componentLocal));
            if (componentLocal != null && componentId == null)
                throw new IllegalArgumentException("Helper component is undeclared");
            parent.addElement(owner, local, kind, componentId, posts);
            elementIds.add(local);
        }

        void boardPad(String localPad, String element, String terminal, String attachment) {
            open();
            String padId = parent.requiredPadId(owner, localPad);
            if (parent.boardEndpoints.put(padId, new BoardEndpointSpec(owner, localPad,
                    new EndpointRef(owner, element, terminal), attachment)) != null)
                throw new IllegalArgumentException("Duplicate board endpoint " + padId);
        }

        private void open() {
            if (finished) throw new IllegalStateException("Local declaration scope is closed");
        }

        private void finish() {
            open();
            if (!new TreeSet<String>(unitIds).equals(contribution.getDescriptor().getComponents().keySet()) ||
                    !new TreeSet<String>(terminalIds).equals(contribution.getDescriptor().getPads().keySet()) ||
                    elementIds.size() > 32)
                throw new IllegalArgumentException("Local declaration is incomplete or exceeds its bounded envelope");
            finished = true;
        }
    }

    static Map<String, Integer> posts(Object... entries) {
        TreeMap<String, Integer> result = new TreeMap<String, Integer>();
        for (int i = 0; i < entries.length; i += 2)
            if (result.put((String) entries[i], (Integer) entries[i + 1]) != null)
                throw new IllegalArgumentException("Duplicate terminal post");
        return result;
    }

    static Map<String, Double> numbers(Object... entries) {
        TreeMap<String, Double> result = new TreeMap<String, Double>();
        for (int i = 0; i < entries.length; i += 2)
            if (result.put((String) entries[i], Double.valueOf(((Number) entries[i + 1]).doubleValue())) != null)
                throw new IllegalArgumentException("Duplicate element parameter");
        return result;
    }

    private static final class Builder {
        private final BoundedAssemblyRequest request;
        private final BlockNamespace namespace;
        private final Map<String, String> netAliases;
        private final boolean controlled;
        private final TreeMap<String, ProviderDeclaration> providers =
                new TreeMap<String, ProviderDeclaration>();
        private final TreeMap<String, ElementDeclaration> elements =
                new TreeMap<String, ElementDeclaration>();
        private final TreeMap<String, PhysicalUnitSpec> units =
                new TreeMap<String, PhysicalUnitSpec>();
        private final TreeMap<String, TerminalMapping> terminals =
                new TreeMap<String, TerminalMapping>();
        private final TreeMap<String, DeviceJoinSpec> joins =
                new TreeMap<String, DeviceJoinSpec>();
        private final TreeMap<String, BridgeSpec> bridges =
                new TreeMap<String, BridgeSpec>();
        private final TreeMap<String, SolverReservation> reservations =
                new TreeMap<String, SolverReservation>();
        private final TreeMap<String, String> componentIds = new TreeMap<String, String>();
        private final TreeMap<String, String> padIds = new TreeMap<String, String>();
        private final TreeMap<String, PadBindingSpec> padBindings =
                new TreeMap<String, PadBindingSpec>();
        private final TreeMap<String, PowerInputSpec> powerInputs =
                new TreeMap<String, PowerInputSpec>();
        private final TreeSet<String> requiredConnectionPadIds = new TreeSet<String>();
        private final TreeMap<String, BoardEndpointSpec> boardEndpoints =
                new TreeMap<String, BoardEndpointSpec>();
        private final TreeMap<String, PhysicalPackage> packages =
                new TreeMap<String, PhysicalPackage>();
        private final TreeMap<String, String> packageOwners =
                new TreeMap<String, String>();
        private final ArrayList<ElectricalUnitPackageMap.Unit> packageUnits =
                new ArrayList<ElectricalUnitPackageMap.Unit>();
        private final TreeMap<String, ComposedBlockContribution> contributionByOwner =
                new TreeMap<String, ComposedBlockContribution>();

        Builder(BoundedAssemblyRequest request, BlockNamespace namespace,
                Map<String, String> netAliases, boolean controlled) {
            this.request = request;
            this.namespace = namespace;
            this.netAliases = netAliases;
            this.controlled = controlled;
        }

        void addContribution(String ownerKey, ComposedBlockContribution contribution) {
            if (contribution == null || !ownerKey.equals(contribution.getDescriptor().getInstanceKey()) ||
                    contributionByOwner.put(ownerKey, contribution) != null)
                throw new IllegalArgumentException("Missing or duplicate contribution " + ownerKey);
            ElectricalConstructionProvider provider = StandardElectricalConstructionProviders.provider(
                    contribution.getProviderTypeId(), contribution.getProviderVersion());
            ContributionBuilder local = new ContributionBuilder(this, ownerKey, contribution);
            provider.declare(local, contribution);
            local.finish();
            // These are private solver witnesses, not durable identities. Canonical
            // packing uses one bounded reservation for every actual declared owner.
            addReservation(ownerKey, "local", providers.size() + 1);
            providers.put(ownerKey, new ProviderDeclaration(ownerKey, provider.getProviderId(),
                    provider.getVersion(), contribution, local.elementIds, local.unitIds,
                    local.terminalIds, Collections.<String>emptyList(), false, elements, local.choices));
        }

        void addDeviceDeclarations(Collection<DeviceAdapterContract> adapters) {
            ArrayList<String> declaredElements = new ArrayList<String>();
            ArrayList<String> declaredUnits = new ArrayList<String>();
            ArrayList<String> declaredTerminals = new ArrayList<String>();
            if (!controlled) {
                addDeviceElement("SUPPLY", "VOLTAGE", map("+", 1, "-", 0), declaredElements);
                addDeviceElement("ISOLATION", "SWITCH", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("CONNECTOR", "SWITCH", map("1", 0, "2", 1),
                        "J1", declaredElements);
                addDeviceElement("SUPPLY_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("SOURCE_FIRST_ATTACHMENT", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("SOURCE_SECOND_ATTACHMENT", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("OUTPUT_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("LOAD_FIRST_ATTACHMENT", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("LOAD_SECOND_ATTACHMENT", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("RETURN_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceElement("GROUND", "GROUND", map("1", 0), declaredElements);
                addDeviceElement("RETURN_BOTTOM", "WIRE", map("1", 0, "2", 1), declaredElements);
                /* J1 is a device-owned component.  Its stable component ID is
                 * still the public J1 identity, but the package/unit owner is
                 * the device provider, matching the terminal mappings and the
                 * physical declaration owner. */
                addDeviceComponent("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                        "device", declaredUnits);
                addPadBinding("device", "J1", "1", "J1", "1",
                        contributionNet("source", "SUPPLY"));
                addPadBinding("device", "J1", "2", "J1", "2",
                        contributionNet("load", "RETURN"));
                addDeviceTerminal("device", "J1", "J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getId(),
                        "CONNECTOR", "1", contributionNet("source", "SUPPLY"));
                addDeviceTerminal("device", "J1", "J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getId(),
                        "CONNECTOR", "2", contributionNet("load", "RETURN"));
                addResistiveBridges();
                addPowerInput(LEGACY_POWER_INPUT_ID,
                        requiredPadId("device", "J1.1"), requiredPadId("device", "J1.2"),
                        contributionNet("source", "SUPPLY"), contributionNet("load", "RETURN"));
                addReservation("device", "infrastructure", 0);
            } else {
                addDeviceElement("GROUND", "GROUND", map("1", 0), declaredElements);
                for (DeviceAdapterContract adapter : adapters) {
                    String owner = adapter.getKey();
                    String local = adapter.getComponentLocalId();
                    String componentId = namespace.idFor(owner, EntityKind.COMPONENT, local);
                    addDeviceComponent(local, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, owner, declaredUnits);
                    addDeviceElement(owner + ".SUPPLY", "VOLTAGE", map("+", 1, "-", 0), declaredElements);
                    addDeviceElement(owner + ".ISOLATION", "SWITCH", map("1", 0, "2", 1), declaredElements);
                    addDeviceElement(owner + ".CONNECTOR", "SWITCH", map("1", 0, "2", 1), componentId, declaredElements);
                    addDeviceElement(owner + ".RETURN", "WIRE", map("1", 0, "2", 1), declaredElements);
                    componentIds.put(elementKey(owner, local), componentId);
                    String outputNet = adapterNet(owner, "OUTPUT");
                    String returnNet = adapterNet(owner, "RETURN");
                    for (String terminal : Arrays.asList("1", "2")) {
                        String localPad = local + "." + terminal;
                        String padId = namespace.idFor(owner, EntityKind.PAD, localPad);
                        padIds.put(elementKey(owner, localPad), padId);
                        String net = "1".equals(terminal) ? outputNet : returnNet;
                        EndpointRef endpoint = "1".equals(terminal) ?
                                new EndpointRef("device", owner + ".CONNECTOR", "2") :
                                new EndpointRef("device", "GROUND", "1");
                        addPadBinding(owner, local, terminal, componentId, terminal, net);
                        terminals.put(terminalKey(owner, local, terminal), new TerminalMapping(owner,
                                local, terminal, componentId, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getId(),
                                terminal, net, endpoint));
                        boardEndpoints.put(padId, new BoardEndpointSpec(owner, localPad, endpoint, null));
                    }
                    addPowerInput(adapter.getExternalInputId(), requiredPadId(owner, local + ".1"),
                            requiredPadId(owner, local + ".2"), outputNet, returnNet);
                }
                addControlledBridges(declaredElements);
                addReservation("device", "infrastructure", 0);
            }
            String deviceProviderId = controlled ? "controlled-device-join" :
                    "resistive-device-join";
            providers.put("device", new ProviderDeclaration("device", deviceProviderId,
                    1, null, declaredElements, declaredUnits, declaredTerminals,
                    new ArrayList<String>(joins.keySet()), true, elements));
        }

        private void addDeviceComponent(String local, PhysicalPackage physicalPackage,
                String owner, Collection<String> declaredUnits) {
            String componentId = "J1".equals(local) && !controlled ? "J1" :
                    namespace.idFor(owner, EntityKind.COMPONENT, local);
            componentIds.put(elementKey("device", local), componentId);
            componentIds.put(elementKey(owner, local), componentId);
            packages.put(componentId, physicalPackage);
            packageOwners.put(componentId, owner);
            Map<String, String> mapping = map("1", "1", "2", "2");
            packageUnits.add(new ElectricalUnitPackageMap.Unit(owner,
                    owner + "/" + local,
                    componentId, Arrays.asList("1", "2"), mapping));
            units.put("device/" + local, new PhysicalUnitSpec(owner, local,
                    componentId, physicalPackage.getId(), mapping));
            declaredUnits.add(local);
            if ("J1".equals(local) && !controlled) {
                padIds.put(elementKey("device", "J1.1"), "J1.1");
                padIds.put(elementKey("device", "J1.2"), "J1.2");
            }
        }

        private void addDeviceElement(String local, String kind,
                Map<String, Integer> posts, Collection<String> declared) {
            addDeviceElement(local, kind, posts, null, declared);
        }

        private void addDeviceElement(String local, String kind,
                Map<String, Integer> posts, String componentId,
                Collection<String> declared) {
            addElement("device", local, kind, componentId, posts);
            declared.add(local);
        }

        private void addControlledDeviceElement(String local, String kind,
                Map<String, Integer> posts, Collection<String> declared) {
            addControlledDeviceElement(local, kind, posts, null, declared);
        }

        private void addControlledDeviceElement(String local, String kind,
                Map<String, Integer> posts, String componentId,
                Collection<String> declared) {
            addElement("device", local, kind, componentId, posts);
            declared.add(local);
        }

        void addJoins(Collection<ElectricalConnection> connections) {
            if (connections == null)
                throw new IllegalArgumentException("Missing electrical connections");
            for (ElectricalConnection connection : connections) {
                if (connection == null)
                    throw new IllegalArgumentException("Null electrical connection");
                ArrayList<String> refs = new ArrayList<String>();
                for (ElectricalConnection.PortRef ref : connection.getPorts())
                    refs.add(ref.getBlockKey() + "/" + ref.getPortId());
                if (joins.put(connection.getId(), new DeviceJoinSpec(connection.getId(), refs,
                        connection.getKind().name())) != null)
                    throw new IllegalArgumentException("Duplicate device join " +
                            connection.getId());
            }
        }

        ElectricalRealizationSpec finish() {
            // All public package terminals are represented by the same frozen
            // map consumed by layout/runtime code.  A missing package or owner
            // therefore fails while the plan is still pure.
            if (controlled && !boardEndpoints.keySet().equals(padBindings.keySet()))
                throw new IllegalArgumentException("Provider board endpoints do not cover the physical pads");
            for (Map.Entry<String, BoardEndpointSpec> entry : boardEndpoints.entrySet()) {
                BoardEndpointSpec board = entry.getValue();
                EndpointRef endpoint = board.getEndpoint();
                ElementDeclaration backing = elements.get(elementKey(endpoint.getOwnerKey(), endpoint.getElementId()));
                if (backing == null || !backing.getTerminalIds().contains(endpoint.getTerminalId()))
                    throw new IllegalArgumentException("Provider declared a missing board endpoint");
                boolean detachable = requiredConnectionPadIds.contains(entry.getKey());
                if (detachable != (board.getAttachmentElementId() != null))
                    throw new IllegalArgumentException("Detachable pad/attachment mismatch");
                if (detachable) {
                    ElementDeclaration wire = elements.get(elementKey(board.getOwnerKey(), board.getAttachmentElementId()));
                    if (wire == null || !"WIRE".equals(wire.getKind()))
                        throw new IllegalArgumentException("Missing provider attachment backing");
                    if (padBindings.get(entry.getKey()).getComponentId().equals(backing.getComponentId()))
                        throw new IllegalArgumentException("Board endpoint cannot belong to the removable component");
                }
            }
            ElectricalUnitPackageMap packageMap = new ElectricalUnitPackageMap(
                    ElectricalUnitPackageMap.VERSION, packages, packageOwners,
                    packageUnits);
            return new ElectricalRealizationSpec(VERSION, providers, elements, units,
                    terminals, joins, bridges, reservations, packageMap,
                    componentIds, padIds, padBindings, powerInputs,
                    requiredConnectionPadIds, boardEndpoints);
        }

        private void addElement(String owner, String local, String kind,
                String componentId, Map<String, Integer> posts) {
            addElement(owner, local, kind, componentId, posts, null,
                    "VOLTAGE".equals(kind) ? numbers("voltage", EXTERNAL_SUPPLY_VOLTS) :
                    Collections.<String, Double>emptyMap());
        }

        private void addElement(String owner, String local, String kind,
                String componentId, Map<String, Integer> posts, String model,
                Map<String, Double> parameters) {
            String key = elementKey(owner, local);
            if (elements.put(key, new ElementDeclaration(owner, local, kind,
                    componentId, posts, model, parameters)) != null)
                throw new IllegalArgumentException("Duplicate electrical element " + key);
        }

        private String requiredPadId(String ownerKey, String localId) {
            String result = padIds.get(elementKey(ownerKey, localId));
            if (result == null)
                throw new IllegalStateException("Missing declared pad " + ownerKey + "/" + localId);
            return result;
        }

        private void addHelper(String owner, String local, String kind,
                String componentId, Map<String, Integer> posts, Collection<String> declared) {
            addElement(owner, local, kind, componentId, posts);
            declared.add(local);
        }

        private void addUnit(String owner, String local, String componentId,
                PhysicalPackage physicalPackage, List<String> unitTerminals,
                List<String> packageTerminals) {
            if (physicalPackage == null)
                throw new IllegalArgumentException("Missing physical package");
            Map<String, String> mapping = new TreeMap<String, String>();
            for (int i = 0; i < unitTerminals.size(); i++)
                mapping.put(unitTerminals.get(i), packageTerminals.get(i));
            String key = owner + "/" + local;
            units.put(key, new PhysicalUnitSpec(owner, local, componentId,
                    physicalPackage.getId(), mapping));
            packages.put(componentId, physicalPackage);
            packageOwners.put(componentId, owner);
            packageUnits.add(new ElectricalUnitPackageMap.Unit(owner,
                    owner + "/" + local,
                    componentId, unitTerminals, mapping));
        }

        private String padNet(String owner, String padId) {
            ComposedBlockContribution contribution = contributionByOwner.get(owner);
            FunctionalBlockDescriptor descriptor = contribution == null ? null :
                    contribution.getDescriptor();
            // Contributions are added in lexical owner order, so use the
            // resolved contribution stored by the temporary owner map below.
            if (descriptor == null) {
                throw new IllegalStateException("Contribution descriptor unavailable for " + owner);
            }
            FunctionalBlockDescriptor.Pad pad = descriptor.getPads().get(padId);
            if (pad == null)
                throw new IllegalArgumentException("Unknown contribution pad " + owner + "/" + padId);
            String qualified = namespace.idFor(owner, EntityKind.NET, pad.getNetId());
            String net = netAliases.get(qualified);
            if (net == null)
                throw new IllegalArgumentException("Unresolved contribution net " + qualified);
            return net;
        }

        private String contributionNet(String owner, String localNet) {
            String qualified = namespace.idFor(owner, EntityKind.NET, localNet);
            String net = netAliases.get(qualified);
            if (net == null)
                throw new IllegalArgumentException("Unresolved contribution net " + qualified);
            return net;
        }

        private String adapterNet(String owner, String localNet) {
            String qualified = namespace.idFor(owner, EntityKind.NET, localNet);
            String result = netAliases.get(qualified);
            if (result == null)
                throw new IllegalArgumentException("Unresolved adapter net " + qualified);
            return result;
        }

        private void addPadBinding(String owner, String local, String terminalId,
                String componentId, String terminal, String netId) {
             String localPad = local + "." + terminalId;
             addPadBindingForLocalPad(owner, local, localPad, componentId, terminal, netId);
        }

        private void addPadBindingForLocalPad(String owner, String local,
                String localPad, String componentId, String terminal, String netId) {
             String padId = padIds.get(elementKey(owner, localPad));
             if (padId == null)
                 throw new IllegalArgumentException("Pad ID was not declared: " +
                        owner + "/" + localPad);
            PadBindingSpec value = new PadBindingSpec(padId, owner, local, terminal,
                    componentId, netId);
            if (padBindings.put(padId, value) != null)
                throw new IllegalArgumentException("Duplicate pad binding " + padId);
        }

        private void addDeviceTerminal(String owner, String local, String componentId,
                String packageId, String backingElement, String terminal, String netId) {
            String key = terminalKey(owner, local, terminal);
            if (terminals.put(key, new TerminalMapping(owner, local, terminal,
                    componentId, packageId, terminal, netId,
                    new EndpointRef("device", backingElement, terminal))) != null)
                throw new IllegalArgumentException("Duplicate device terminal mapping " + key);
        }

        private void addPowerInput(String inputId, String positivePadId,
                String returnPadId, String positiveNetId, String returnNetId) {
            PowerInputSpec value = new PowerInputSpec(inputId, positivePadId,
                    returnPadId, positiveNetId, returnNetId);
            if (powerInputs.put(inputId, value) != null)
                throw new IllegalArgumentException("Duplicate power input " + inputId);
        }

        private void addResistiveBridges() {
            addBridge("SOURCE_FIRST_ATTACHMENT", "device", "SUPPLY_TRACE", "2",
                    "source", "R1", "1", null, "VIN_INPUT");
            addBridge("SOURCE_SECOND_ATTACHMENT", "source", "R1_SECONDARY", "2",
                    "device", "OUTPUT_TRACE", "1", ResistiveBlockContributions.SIGNAL_CONNECTION_ID);
            addBridge("LOAD_FIRST_ATTACHMENT", "device", "OUTPUT_TRACE", "2",
                    "load", "R1", "1", ResistiveBlockContributions.SIGNAL_CONNECTION_ID);
            addBridge("LOAD_SECOND_ATTACHMENT", "load", "R1_SECONDARY", "2",
                    "device", "RETURN_TRACE", "1", ResistiveBlockContributions.RETURN_CONNECTION_ID);
        }

        private void addControlledBridges(Collection<String> declaredElements) {
            for (ElectricalConnection connection : request.getConnections()) {
                EndpointRef first = null;
                for (ElectricalConnection.PortRef ref : connection.getPorts()) {
                    EndpointRef endpoint = portEndpoint(ref);
                    if (endpoint == null) continue; // A logical reference port need not expose a physical pad.
                    if (first == null) first = endpoint;
                    else if (!first.equals(endpoint)) {
                        String bridge = connection.getId() + "." + ref.getBlockKey();
                        addDeviceElement(bridge, "WIRE", map("1", 0, "2", 1), declaredElements);
                        addBridge(bridge, first.getOwnerKey(), first.getElementId(), first.getTerminalId(),
                                endpoint.getOwnerKey(), endpoint.getElementId(), endpoint.getTerminalId(), connection.getId());
                    }
                }
            }
        }

        private EndpointRef portEndpoint(ElectricalConnection.PortRef ref) {
            ElectricalBlockContract contract = null;
            for (ElectricalBlockContract candidate : request.getAllElectricalContracts())
                if (candidate.getDescriptor().getInstanceKey().equals(ref.getBlockKey())) contract = candidate;
            if (contract == null) throw new IllegalArgumentException("Unknown device join owner");
            FunctionalBlockDescriptor descriptor = contract.getDescriptor();
            FunctionalBlockDescriptor.Port port = descriptor.getPorts().get(ref.getPortId());
            if (port == null) throw new IllegalArgumentException("Unknown device join port");
            FunctionalBlockDescriptor.LocalRef attachment = port.getAttachment();
            for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry : descriptor.getPads().entrySet()) {
                FunctionalBlockDescriptor.Pad pad = entry.getValue();
                boolean matches = attachment.getKind() == EntityKind.PAD ? attachment.getId().equals(entry.getKey()) :
                        attachment.getKind() == EntityKind.NET ? attachment.getId().equals(pad.getNetId()) :
                        attachment.getKind() == EntityKind.ENDPOINT && attachment.getId().equals(pad.getEndpointId());
                if (!matches) continue;
                String padId = requiredPadId(ref.getBlockKey(), entry.getKey());
                BoardEndpointSpec backing = boardEndpoints.get(padId);
                if (backing == null) throw new IllegalArgumentException("Local provider omitted persistent pad backing");
                return backing.getEndpoint();
            }
            if (attachment.getKind() != EntityKind.NET)
                throw new IllegalArgumentException("Device join does not identify a physical endpoint");
            return null;
        }

        private void addBridge(String bridgeId, String firstOwner, String firstElement,
                String firstTerminal, String secondOwner, String secondElement,
                String secondTerminal, String semanticJoinId) {
            addBridge(bridgeId, firstOwner, firstElement, firstTerminal,
                    secondOwner, secondElement, secondTerminal, semanticJoinId, null);
        }

        private void addBridge(String bridgeId, String firstOwner, String firstElement,
                String firstTerminal, String secondOwner, String secondElement,
                String secondTerminal, String semanticJoinId, String externalPowerInputId) {
            if (!"WIRE".equals(elements.get(elementKey("device", bridgeId)).getKind()))
                throw new IllegalArgumentException("Bridge is not a device wire: " + bridgeId);
            BridgeSpec value = new BridgeSpec(bridgeId,
                    new EndpointRef(firstOwner, firstElement, firstTerminal),
                    new EndpointRef(secondOwner, secondElement, secondTerminal),
                    semanticJoinId, externalPowerInputId);
            if (bridges.put(bridgeId, value) != null)
                throw new IllegalArgumentException("Duplicate bridge " + bridgeId);
        }

        private PhysicalPackage packageFor(ComposedBlockContribution.ResistorRecipe recipe) {
            if (PhysicalPackages.AXIAL_RESISTOR.getId().equals(recipe.getPackageId()))
                return PhysicalPackages.AXIAL_RESISTOR;
            throw new IllegalArgumentException("Unsupported electrical package " +
                    recipe.getPackageId());
        }

        private void addReservation(String owner, String role, int index) {
            int originX = 64 + index * 1200;
            int originY = 64;
            if (index < 0 || index > 16 || reservations.put(owner + "/" + role,
                    new SolverReservation(owner, role, originX, originY, 1024, 768)) != null)
                throw new IllegalArgumentException("Duplicate or excessive solver reservation");
        }

        private static Map<String, Integer> map(String first, int firstIndex,
                String second, int secondIndex) {
            TreeMap<String, Integer> result = new TreeMap<String, Integer>();
            result.put(first, Integer.valueOf(firstIndex));
            result.put(second, Integer.valueOf(secondIndex));
            return result;
        }

        private static Map<String, Integer> map(String first, int firstIndex) {
            TreeMap<String, Integer> result = new TreeMap<String, Integer>();
            result.put(first, Integer.valueOf(firstIndex));
            return result;
        }

        private static Map<String, Integer> map(String first, int firstIndex,
                String second, int secondIndex, String third, int thirdIndex) {
            TreeMap<String, Integer> result = new TreeMap<String, Integer>();
            result.put(first, Integer.valueOf(firstIndex));
            result.put(second, Integer.valueOf(secondIndex));
            result.put(third, Integer.valueOf(thirdIndex));
            return result;
        }

        private static Map<String, String> map(String first, String firstValue,
                String second, String secondValue) {
            TreeMap<String, String> result = new TreeMap<String, String>();
            result.put(first, firstValue);
            result.put(second, secondValue);
            return result;
        }
    }
}
