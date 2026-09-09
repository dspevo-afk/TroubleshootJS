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
    static final int VERSION = 1;
    static final String LEGACY_POWER_INPUT_ID = "VIN_INPUT";
    // Frozen choices of the accepted bounded generator versions, not CURRENT defaults.
    static final String CONTROLLED_LED_MODEL = "default-led";
    static final double CONTROLLED_NMOS_THRESHOLD_VOLTS = 1.5;
    static final double CONTROLLED_NMOS_BETA = 10.0;
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
    private final ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedLoadRecipe;
    private final Map<String, String> componentIds;
    private final Map<String, String> padIds;
    private final Map<String, PadBindingSpec> padBindings;
    private final Map<String, PowerInputSpec> powerInputs;
    private final Set<String> requiredConnectionPadIds;

    private ElectricalRealizationSpec(int version,
            Map<String, ProviderDeclaration> providerDeclarations,
            Map<String, ElementDeclaration> elementDeclarations,
            Map<String, PhysicalUnitSpec> physicalUnits,
            Map<String, TerminalMapping> terminalMappings,
            Map<String, DeviceJoinSpec> deviceJoins,
            Map<String, BridgeSpec> bridgeSpecs,
            Map<String, SolverReservation> solverReservations,
            ElectricalUnitPackageMap packageMap,
            ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedLoadRecipe,
            Map<String, String> componentIds, Map<String, String> padIds,
            Map<String, PadBindingSpec> padBindings,
            Map<String, PowerInputSpec> powerInputs,
            Collection<String> requiredConnectionPadIds) {
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
        this.resolvedLoadRecipe = resolvedLoadRecipe;
        this.componentIds = immutable(componentIds);
        this.padIds = immutable(padIds);
        this.padBindings = immutable(padBindings);
        this.powerInputs = immutable(powerInputs);
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
    ControlledIndicatorValueSynthesis.ResolvedRecipe getResolvedLoadRecipe() {
        return resolvedLoadRecipe;
    }

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

        ProviderDeclaration(String ownerKey, String providerId, int providerVersion,
                ComposedBlockContribution contribution, Collection<String> elementIds,
                Collection<String> unitIds, Collection<String> terminalIds,
                Collection<String> joinIds, boolean deviceOwner) {
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

        ElementDeclaration(String ownerKey, String elementId, String kind,
                String componentId, Map<String, Integer> postIndexByTerminal) {
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
        }

        String getOwnerKey() { return ownerKey; }
        String getElementId() { return elementId; }
        String getKind() { return kind; }
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
            ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedLoadRecipe,
            boolean controlled) {
        if (request == null || namespace == null || blocks == null || adapters == null ||
                netAliases == null)
            throw new IllegalArgumentException("Electrical spec inputs are required");

        Builder builder = new Builder(request, namespace, netAliases, controlled,
                resolvedLoadRecipe);
        for (Map.Entry<String, ComposedBlockContribution> entry : blocks.entrySet())
            builder.addContribution(entry.getKey(), entry.getValue());
        builder.addJoins(request.getConnections());
        builder.addDeviceDeclarations(adapters);
        return builder.finish();
    }

    private static final class Builder {
        private final BoundedAssemblyRequest request;
        private final BlockNamespace namespace;
        private final Map<String, String> netAliases;
        private final boolean controlled;
        private final ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedRecipe;
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
        private final TreeMap<String, PhysicalPackage> packages =
                new TreeMap<String, PhysicalPackage>();
        private final TreeMap<String, String> packageOwners =
                new TreeMap<String, String>();
        private final ArrayList<ElectricalUnitPackageMap.Unit> packageUnits =
                new ArrayList<ElectricalUnitPackageMap.Unit>();
        private final TreeMap<String, ComposedBlockContribution> contributionByOwner =
                new TreeMap<String, ComposedBlockContribution>();

        Builder(BoundedAssemblyRequest request, BlockNamespace namespace,
                Map<String, String> netAliases, boolean controlled,
                ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedRecipe) {
            this.request = request;
            this.namespace = namespace;
            this.netAliases = netAliases;
            this.controlled = controlled;
            this.resolvedRecipe = resolvedRecipe;
        }

        void addContribution(String ownerKey, ComposedBlockContribution contribution) {
            if (contribution == null)
                throw new IllegalArgumentException("Missing contribution " + ownerKey);
            contributionByOwner.put(ownerKey, contribution);
            String providerId = contribution.getProviderTypeId();
            int providerVersion = contribution.getProviderVersion();
            ArrayList<String> declaredElements = new ArrayList<String>();
            ArrayList<String> declaredUnits = new ArrayList<String>();
            ArrayList<String> declaredTerminals = new ArrayList<String>();
            for (ComposedBlockContribution.ResistorRecipe recipe :
                    contribution.getResistors().values()) {
                String local = recipe.getComponentLocalId();
                String componentId = namespace.idFor(ownerKey, EntityKind.COMPONENT, local);
                componentIds.put(elementKey(ownerKey, local), componentId);
                addElement(ownerKey, local, "RESISTOR", componentId,
                        map("1", 0, "2", 1));
                declaredElements.add(local);
                boolean declaresSecondary = recipe.isMutable();
                if (declaresSecondary) {
                    addHelper(ownerKey, local + "_SECONDARY", "FAULT_HELPER", componentId,
                            map("1", 0, "2", 1), declaredElements);
                }
                addUnit(ownerKey, local, componentId, packageFor(recipe),
                        Arrays.asList("1", "2"), Arrays.asList("1", "2"));
                declaredUnits.add(local);
                addRecipeTerminals(ownerKey, local, recipe.getFirstPadLocalId(),
                        recipe.getSecondPadLocalId(), componentId, packageFor(recipe),
                        padNet(ownerKey, recipe.getFirstPadLocalId()),
                        padNet(ownerKey, recipe.getSecondPadLocalId()), declaredTerminals);
            }
            for (ComposedBlockContribution.NmosRecipe recipe :
                    contribution.getNmosRecipes().values()) {
                String local = recipe.getComponentLocalId();
                String componentId = namespace.idFor(ownerKey, EntityKind.COMPONENT, local);
                componentIds.put(elementKey(ownerKey, local), componentId);
                addElement(ownerKey, local, "NMOS", componentId,
                        map("G", 0, "D", 2, "S", 1));
                declaredElements.add(local);
                addUnit(ownerKey, local, componentId, PhysicalPackages.TO92_NMOS,
                        Arrays.asList("G", "D", "S"), Arrays.asList("G", "D", "S"));
                declaredUnits.add(local);
                addRecipeTerminals(ownerKey, local, recipe.getGatePadLocalId(),
                        recipe.getDrainPadLocalId(), componentId, PhysicalPackages.TO92_NMOS,
                        padNet(ownerKey, recipe.getGatePadLocalId()),
                        padNet(ownerKey, recipe.getDrainPadLocalId()), declaredTerminals);
                padIds.put(elementKey(ownerKey, recipe.getSourcePadLocalId()),
                        namespace.idFor(ownerKey, EntityKind.PAD, recipe.getSourcePadLocalId()));
                addTerminal(ownerKey, local, recipe.getSourcePadLocalId(), componentId,
                        PhysicalPackages.TO92_NMOS, "S", padNet(ownerKey,
                                recipe.getSourcePadLocalId()), declaredTerminals);
                addPadBindingForLocalPad(ownerKey, local, recipe.getSourcePadLocalId(), componentId,
                         "S", padNet(ownerKey, recipe.getSourcePadLocalId()));
            }
            for (ComposedBlockContribution.LedRecipe recipe :
                    contribution.getLedRecipes().values()) {
                String local = recipe.getComponentLocalId();
                String componentId = namespace.idFor(ownerKey, EntityKind.COMPONENT, local);
                componentIds.put(elementKey(ownerKey, local), componentId);
                addElement(ownerKey, local, "LED", componentId, map("A", 0, "K", 1));
                declaredElements.add(local);
                addUnit(ownerKey, local, componentId, PhysicalPackages.THROUGH_HOLE_LED,
                        Arrays.asList("A", "K"), Arrays.asList("A", "K"));
                declaredUnits.add(local);
                addRecipeTerminals(ownerKey, local, recipe.getAnodePadLocalId(),
                        recipe.getCathodePadLocalId(), componentId,
                        PhysicalPackages.THROUGH_HOLE_LED,
                        padNet(ownerKey, recipe.getAnodePadLocalId()),
                        padNet(ownerKey, recipe.getCathodePadLocalId()), declaredTerminals);
            }
            if (ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(ownerKey)) {
                addHelper(ownerKey, "RG_FAULT_SWITCH", "SWITCH",
                        namespace.idFor(ownerKey, EntityKind.COMPONENT, "RG"),
                        map("1", 0, "2", 1), declaredElements);
                addHelper(ownerKey, "RG_FIRST_ATTACHMENT", "WIRE", null,
                        map("1", 0, "2", 1), declaredElements);
                addHelper(ownerKey, "RG_SECOND_ATTACHMENT", "WIRE", null,
                        map("1", 0, "2", 1), declaredElements);
                addHelper(ownerKey, "GATE_NODE_TRACE", "WIRE", null,
                        map("1", 0, "2", 1), declaredElements);
            } else if (ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(ownerKey)
                    && controlled) {
                addHelper(ownerKey, "RLOAD_FAULT_SWITCH", "SWITCH",
                        namespace.idFor(ownerKey, EntityKind.COMPONENT, "RLOAD"),
                        map("1", 0, "2", 1), declaredElements);
                addHelper(ownerKey, "RLOAD_FIRST_ATTACHMENT", "WIRE", null,
                        map("1", 0, "2", 1), declaredElements);
                addHelper(ownerKey, "RLOAD_SECOND_ATTACHMENT", "WIRE", null,
                        map("1", 0, "2", 1), declaredElements);
                addHelper(ownerKey, "LOAD_NODE_TRACE", "WIRE", null,
                        map("1", 0, "2", 1), declaredElements);
            }
            addReservation(ownerKey, "local", reservationIndex(ownerKey));
            providers.put(ownerKey, new ProviderDeclaration(ownerKey, providerId,
                    providerVersion, contribution, declaredElements, declaredUnits,
                    declaredTerminals, Collections.<String>emptyList(), false));
        }

        private int reservationIndex(String ownerKey) {
            if (ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(ownerKey))
                return 1;
            if (ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(ownerKey))
                return 2;
            if (ResistiveBlockContributions.SOURCE_BLOCK_KEY.equals(ownerKey))
                return 3;
            return 4;
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
                addControlledDeviceElement("LOAD_SUPPLY", "VOLTAGE", map("+", 1, "-", 0), declaredElements);
                addControlledDeviceElement("LOAD_ISOLATION", "SWITCH", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("LOAD_CONNECTOR", "SWITCH", map("1", 0, "2", 1),
                        namespace.idFor(DeviceAdapterContract.POWER_ADAPTER_KEY,
                                EntityKind.COMPONENT, "J1"), declaredElements);
                addControlledDeviceElement("LOAD_INPUT_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("CONTROL_SUPPLY", "VOLTAGE", map("+", 1, "-", 0), declaredElements);
                addControlledDeviceElement("CONTROL_ISOLATION", "SWITCH", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("CONTROL_INPUT_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("CONTROL_COMMAND", "SWITCH", map("1", 0, "2", 1),
                        namespace.idFor(DeviceAdapterContract.CONTROL_ADAPTER_KEY,
                                EntityKind.COMPONENT, "J2"), declaredElements);
                addControlledDeviceElement("CONTROL_BOARD_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("DRAIN_TRACE", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("GROUND", "GROUND", map("1", 0), declaredElements);
                addControlledDeviceElement("LOAD_RETURN", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("CONTROL_RETURN", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("PULLDOWN_RETURN", "WIRE", map("1", 0, "2", 1), declaredElements);
                addControlledDeviceElement("SOURCE_RETURN", "WIRE", map("1", 0, "2", 1), declaredElements);
                addDeviceComponent("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                        "power-adapter", declaredUnits);
                addDeviceComponent("J2", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                        "control-adapter", declaredUnits);
                addControlledBridges();
                addReservation("device", "infrastructure", 0);
            }
            for (DeviceAdapterContract adapter : adapters) {
                if (adapter == null)
                    throw new IllegalArgumentException("Null device adapter");
                String owner = adapter.getKey();
                String local = adapter.getComponentLocalId();
                String componentId = namespace.idFor(owner, EntityKind.COMPONENT, local);
                componentIds.put(elementKey(owner, local), componentId);
                padIds.put(elementKey(owner, local + ".1"),
                        namespace.idFor(owner, EntityKind.PAD, local + ".1"));
                padIds.put(elementKey(owner, local + ".2"),
                        namespace.idFor(owner, EntityKind.PAD, local + ".2"));
                String outputNet = adapterNet(owner, "OUTPUT");
                String returnNet = adapterNet(owner, "RETURN");
                addPadBinding(owner, local, "1", componentId, "1", outputNet);
                addPadBinding(owner, local, "2", componentId, "2", returnNet);
                String adapterBacking = DeviceAdapterContract.POWER_ADAPTER_KEY.equals(owner) ?
                        "LOAD_CONNECTOR" : "CONTROL_COMMAND";
                addDeviceTerminal(owner, local, componentId,
                        PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getId(), adapterBacking,
                        "1", outputNet);
                addDeviceTerminal(owner, local, componentId,
                        PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getId(), adapterBacking,
                        "2", returnNet);
                addPowerInput(adapter.getExternalInputId(),
                        requiredPadId(owner, local + ".1"), requiredPadId(owner, local + ".2"),
                        outputNet, returnNet);
            }
            String deviceProviderId = controlled ? "controlled-device-join" :
                    "resistive-device-join";
            providers.put("device", new ProviderDeclaration("device", deviceProviderId,
                    1, null, declaredElements, declaredUnits, declaredTerminals,
                    new ArrayList<String>(joins.keySet()), true));
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
            ElectricalUnitPackageMap packageMap = new ElectricalUnitPackageMap(
                    ElectricalUnitPackageMap.VERSION, packages, packageOwners,
                    packageUnits);
            return new ElectricalRealizationSpec(VERSION, providers, elements, units,
                    terminals, joins, bridges, reservations, packageMap, resolvedRecipe,
                    componentIds, padIds, padBindings, powerInputs,
                    requiredConnectionPadIds);
        }

        private void addElement(String owner, String local, String kind,
                String componentId, Map<String, Integer> posts) {
            String key = elementKey(owner, local);
            if (elements.put(key, new ElementDeclaration(owner, local, kind,
                    componentId, posts)) != null)
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

        private void addRecipeTerminals(String owner, String local, String firstPad,
                String secondPad, String componentId, PhysicalPackage physicalPackage,
                String firstNet, String secondNet, Collection<String> declared) {
            padIds.put(elementKey(owner, firstPad), namespace.idFor(owner,
                    EntityKind.PAD, firstPad));
            padIds.put(elementKey(owner, secondPad), namespace.idFor(owner,
                    EntityKind.PAD, secondPad));
            String firstTerminal = terminalForPad(local, firstPad, "1");
            String secondTerminal = terminalForPad(local, secondPad, "2");
            addTerminal(owner, local, firstPad, componentId, physicalPackage,
                    firstTerminal, firstNet, declared);
            addTerminal(owner, local, secondPad, componentId, physicalPackage,
                    secondTerminal, secondNet, declared);
            addPadBindingForLocalPad(owner, local, firstPad, componentId, firstTerminal, firstNet);
            addPadBindingForLocalPad(owner, local, secondPad, componentId, secondTerminal, secondNet);
            if (contributionByOwner.get(owner).getResistor(local) != null &&
                    contributionByOwner.get(owner).getResistor(local).isMutable()) {
                requiredConnectionPadIds.add(padIds.get(elementKey(owner, firstPad)));
                requiredConnectionPadIds.add(padIds.get(elementKey(owner, secondPad)));
            }
        }

        private void addTerminal(String owner, String local, String padId,
                String componentId, PhysicalPackage physicalPackage, String terminal,
                String net, Collection<String> declared) {
            ComposedBlockContribution.ResistorRecipe resistor =
                    contributionByOwner.get(owner).getResistor(local);
            EndpointRef componentEndpoint = new EndpointRef(owner, local, terminal);
            if (resistor != null && resistor.isMutable() && "2".equals(terminal))
                componentEndpoint = new EndpointRef(owner, local + "_SECONDARY", "2");
            terminals.put(terminalKey(owner, local, terminal), new TerminalMapping(owner,
                    local, terminal, componentId, physicalPackage.getId(),
                    terminal, net, componentEndpoint));
            // Provider terminal declarations are pad-scoped.  Bare terminal
            // names such as "1" and "2" repeat across components, while
            // the physical pad identity is unique within the provider.
            declared.add(padId);
        }

        private String terminalForPad(String local, String padId, String fallback) {
            if (padId.endsWith(".1") || padId.endsWith("_1") || padId.endsWith(".A"))
                return padId.endsWith(".A") ? "A" : "1";
            if (padId.endsWith(".2") || padId.endsWith("_2") || padId.endsWith(".K"))
                return padId.endsWith(".K") ? "K" : "2";
            if (padId.endsWith(".G")) return "G";
            if (padId.endsWith(".D")) return "D";
            if (padId.endsWith(".S")) return "S";
            return fallback;
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

        private void addControlledBridges() {
            addBridge("LOAD_INPUT_TRACE", "device", "LOAD_CONNECTOR", "2",
                    "load", "RLOAD_FIRST_ATTACHMENT", "1",
                    ControlledIndicatorBlockContributions.POWER_CONNECTION_ID);
            addBridge("CONTROL_BOARD_TRACE", "device", "CONTROL_COMMAND", "2",
                    "driver", "RG_FIRST_ATTACHMENT", "1",
                    ControlledIndicatorBlockContributions.CONTROL_CONNECTION_ID);
            addBridge("DRAIN_TRACE", "load", "LED1", "K",
                    "driver", "Q1", "D",
                    ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID);
            addBridge("PULLDOWN_RETURN", "driver", "RPD", "2",
                    "device", "GROUND", "1",
                    ControlledIndicatorBlockContributions.RETURN_CONNECTION_ID);
            addBridge("SOURCE_RETURN", "driver", "Q1", "S",
                    "device", "GROUND", "1",
                    ControlledIndicatorBlockContributions.RETURN_CONNECTION_ID);
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
            reservations.put(owner + "/" + role,
                    new SolverReservation(owner, role, originX, originY, 1024, 768));
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
