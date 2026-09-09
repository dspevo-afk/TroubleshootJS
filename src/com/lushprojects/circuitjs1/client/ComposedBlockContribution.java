package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure contribution of one bounded local block.
 *
 * <p>A contribution contains declarations and executable local observation
 * rules only.  It deliberately has no reference to a simulator, a board, a
 * physical runtime, or a fault controller.  The assembler is responsible for
 * translating these declarations into fresh runtime objects.</p>
 */
final class ComposedBlockContribution {
    static final double MAX_OPERATING_CURRENT_AMPS = 0.005;
    static final double MIN_HEALTHY_CURRENT_AMPS = 0.001;
    static final double FAULT_RESISTANCE_OHMS = 100000.0;
    static final double RATED_WATTS = 0.25;

    /** Solver-backed local observations supplied by the runtime owner. */
    interface Observation {
        double voltage(String localEndpointId);
        double current(String localComponentId);
        double resistance(String localComponentId);
    }

    /** Classification from actual measured current, never from a fault ID. */
    enum ObservationResult {
        CONDUCTING,
        LOW_CURRENT,
        INVALID
    }

    /** Immutable local resistor recipe consumed by the private assembler. */
    static final class ResistorRecipe {
        private final String componentLocalId;
        private final String firstEndpointLocalId;
        private final String secondEndpointLocalId;
        private final String firstPadLocalId;
        private final String secondPadLocalId;
        private final double resistanceOhms;
        private final double ratedWatts;
        private final String packageId;
        private final ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedRecipe;
        private final boolean mutable;

        ResistorRecipe(String componentLocalId, String firstEndpointLocalId,
                String secondEndpointLocalId, String firstPadLocalId,
                String secondPadLocalId, double resistanceOhms,
                double ratedWatts, String packageId, boolean mutable) {
            this(componentLocalId, firstEndpointLocalId, secondEndpointLocalId,
                    firstPadLocalId, secondPadLocalId, resistanceOhms,
                    ratedWatts, packageId, null, mutable);
        }

        ResistorRecipe(String componentLocalId, String firstEndpointLocalId,
                String secondEndpointLocalId, String firstPadLocalId,
                String secondPadLocalId,
                ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedRecipe,
                boolean mutable) {
            this(componentLocalId, firstEndpointLocalId, secondEndpointLocalId,
                    firstPadLocalId, secondPadLocalId,
                    required(resolvedRecipe, "resolvedRecipe").getResistanceOhms(),
                    resolvedRecipe.getRatedWatts(), resolvedRecipe.getPackageId(),
                    resolvedRecipe, mutable);
        }

        private ResistorRecipe(String componentLocalId, String firstEndpointLocalId,
                String secondEndpointLocalId, String firstPadLocalId,
                String secondPadLocalId, double resistanceOhms,
                double ratedWatts, String packageId,
                ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedRecipe,
                boolean mutable) {
            this.componentLocalId = FunctionalBlockDescriptor.requireId(
                    componentLocalId, "recipe.componentLocalId");
            this.firstEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    firstEndpointLocalId, "recipe.firstEndpointLocalId");
            this.secondEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    secondEndpointLocalId, "recipe.secondEndpointLocalId");
            this.firstPadLocalId = FunctionalBlockDescriptor.requireId(
                    firstPadLocalId, "recipe.firstPadLocalId");
            this.secondPadLocalId = FunctionalBlockDescriptor.requireId(
                    secondPadLocalId, "recipe.secondPadLocalId");
            requireFinitePositive(resistanceOhms, "recipe.resistanceOhms");
            requireFinitePositive(ratedWatts, "recipe.ratedWatts");
            this.resistanceOhms = resistanceOhms == 0.0 ? 0.0
                    : resistanceOhms;
            this.ratedWatts = ratedWatts == 0.0 ? 0.0 : ratedWatts;
            this.packageId = FunctionalBlockDescriptor.requireId(packageId,
                    "recipe.packageId");
            this.resolvedRecipe = resolvedRecipe;
            this.mutable = mutable;
        }

        String getComponentLocalId() { return componentLocalId; }
        String getFirstEndpointLocalId() { return firstEndpointLocalId; }
        String getSecondEndpointLocalId() { return secondEndpointLocalId; }
        String getFirstPadLocalId() { return firstPadLocalId; }
        String getSecondPadLocalId() { return secondPadLocalId; }
        double getResistanceOhms() {
            return resolvedRecipe == null ? resistanceOhms :
                resolvedRecipe.getResistanceOhms();
        }
        double getRatedWatts() {
            return resolvedRecipe == null ? ratedWatts : resolvedRecipe.getRatedWatts();
        }
        double getTolerancePercent() {
            return resolvedRecipe == null ? 5.0 : resolvedRecipe.getTolerancePercent();
        }
        String getPackageId() {
            return packageId;
        }
        String getCatalogEntryId() {
            return resolvedRecipe == null ? null : resolvedRecipe.getCatalogEntryId();
        }
        ControlledIndicatorValueSynthesis.ResolvedRecipe getResolvedRecipe() {
            return resolvedRecipe;
        }
        boolean isMutable() { return mutable; }
        @Override public String toString() {
            return componentLocalId + "=" + Double.toString(resistanceOhms)
                    + ":" + Double.toString(ratedWatts) + ":" + mutable
                    + "(" + firstEndpointLocalId + "," + secondEndpointLocalId + ")";
        }
    }

    /** Immutable local NMOS recipe.  It describes terminals and the model only. */
    static class NmosRecipe {
        private final String componentLocalId, gateEndpointLocalId,
                drainEndpointLocalId, sourceEndpointLocalId;
        private final String gatePadLocalId, drainPadLocalId, sourcePadLocalId;
        private final String modelId;

        NmosRecipe(String componentLocalId, String gateEndpointLocalId,
                String drainEndpointLocalId, String sourceEndpointLocalId,
                String gatePadLocalId, String drainPadLocalId,
                String sourcePadLocalId, String modelId) {
            this.componentLocalId = FunctionalBlockDescriptor.requireId(
                    componentLocalId, "nmos.componentLocalId");
            this.gateEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    gateEndpointLocalId, "nmos.gateEndpointLocalId");
            this.drainEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    drainEndpointLocalId, "nmos.drainEndpointLocalId");
            this.sourceEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    sourceEndpointLocalId, "nmos.sourceEndpointLocalId");
            this.gatePadLocalId = FunctionalBlockDescriptor.requireId(
                    gatePadLocalId, "nmos.gatePadLocalId");
            this.drainPadLocalId = FunctionalBlockDescriptor.requireId(
                    drainPadLocalId, "nmos.drainPadLocalId");
            this.sourcePadLocalId = FunctionalBlockDescriptor.requireId(
                    sourcePadLocalId, "nmos.sourcePadLocalId");
            this.modelId = FunctionalBlockDescriptor.requireId(modelId,
                    "nmos.modelId");
        }
        String getComponentLocalId() { return componentLocalId; }
        String getGateEndpointLocalId() { return gateEndpointLocalId; }
        String getDrainEndpointLocalId() { return drainEndpointLocalId; }
        String getSourceEndpointLocalId() { return sourceEndpointLocalId; }
        String getGatePadLocalId() { return gatePadLocalId; }
        String getDrainPadLocalId() { return drainPadLocalId; }
        String getSourcePadLocalId() { return sourcePadLocalId; }
        String getModelId() { return modelId; }
        @Override public String toString() {
            return componentLocalId + "=" + modelId + "(" + gateEndpointLocalId
                    + "," + drainEndpointLocalId + "," + sourceEndpointLocalId + ")";
        }
    }

    /** Immutable local LED recipe.  It describes terminals and the model only. */
    static class LedRecipe {
        private final String componentLocalId, anodeEndpointLocalId,
                cathodeEndpointLocalId, anodePadLocalId, cathodePadLocalId;
        private final String modelId;

        LedRecipe(String componentLocalId, String anodeEndpointLocalId,
                String cathodeEndpointLocalId, String anodePadLocalId,
                String cathodePadLocalId, String modelId) {
            this.componentLocalId = FunctionalBlockDescriptor.requireId(
                    componentLocalId, "led.componentLocalId");
            this.anodeEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    anodeEndpointLocalId, "led.anodeEndpointLocalId");
            this.cathodeEndpointLocalId = FunctionalBlockDescriptor.requireId(
                    cathodeEndpointLocalId, "led.cathodeEndpointLocalId");
            this.anodePadLocalId = FunctionalBlockDescriptor.requireId(
                    anodePadLocalId, "led.anodePadLocalId");
            this.cathodePadLocalId = FunctionalBlockDescriptor.requireId(
                    cathodePadLocalId, "led.cathodePadLocalId");
            this.modelId = FunctionalBlockDescriptor.requireId(modelId,
                    "led.modelId");
        }
        String getComponentLocalId() { return componentLocalId; }
        String getAnodeEndpointLocalId() { return anodeEndpointLocalId; }
        String getCathodeEndpointLocalId() { return cathodeEndpointLocalId; }
        String getAnodePadLocalId() { return anodePadLocalId; }
        String getCathodePadLocalId() { return cathodePadLocalId; }
        String getModelId() { return modelId; }
        @Override public String toString() {
            return componentLocalId + "=" + modelId + "(" + anodeEndpointLocalId
                    + "," + cathodeEndpointLocalId + ")";
        }
    }

    /** Local fault declaration; no generated/global fault enum is involved. */
    static class FaultSpec {
        enum Kind { OPEN, INCORRECT_RESISTANCE }
        private final Kind kind;
        private final String targetComponentLocalId;
        private final double effectiveResistanceOhms;

        FaultSpec(Kind kind, String targetComponentLocalId,
                double effectiveResistanceOhms) {
            if (kind == null) throw new IllegalArgumentException("fault kind is required");
            this.kind = kind;
            this.targetComponentLocalId = FunctionalBlockDescriptor.requireId(
                    targetComponentLocalId, "fault.targetComponentLocalId");
            requireFinitePositive(effectiveResistanceOhms,
                    "fault.effectiveResistanceOhms");
            this.effectiveResistanceOhms = effectiveResistanceOhms;
        }
        Kind getKind() { return kind; }
        String getTargetComponentLocalId() { return targetComponentLocalId; }
        double getEffectiveResistanceOhms() { return effectiveResistanceOhms; }
        @Override public String toString() {
            return kind + ":" + targetComponentLocalId + ":"
                    + Double.toString(effectiveResistanceOhms);
        }
    }

    private final String providerTypeId;
    private final int providerVersion;
    private final FunctionalBlockDescriptor descriptor;
    private final ElectricalBlockContract electricalContract;
    private final ResistorRecipe resistorRecipe;
    private final Map<String, ResistorRecipe> resistorRecipes;
    private final Map<String, NmosRecipe> nmosRecipes;
    private final Map<String, LedRecipe> ledRecipes;
    private final FaultSpec faultSpec;
    private final String faultLocalId;
    private final double faultEffectiveOhms;
    private final String repairLocalComponentId;
    private final List<String> inputRequirements;
    private final List<String> retestRequirements;
    private final ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedValueRecipe;

    ComposedBlockContribution(String providerTypeId, int providerVersion,
            FunctionalBlockDescriptor descriptor,
            ElectricalBlockContract electricalContract,
            Map<String, ResistorRecipe> resistorRecipes,
            Collection<NmosRecipe> nmosRecipes, Collection<LedRecipe> ledRecipes,
            FaultSpec faultSpec, String repairLocalComponentId,
            Collection<String> inputRequirements,
            Collection<String> retestRequirements) {
        this(providerTypeId, providerVersion, descriptor, electricalContract,
                resistorRecipes, nmosRecipes, ledRecipes, faultSpec,
                repairLocalComponentId, inputRequirements, retestRequirements,
                null);
    }

    ComposedBlockContribution(String providerTypeId, int providerVersion,
            FunctionalBlockDescriptor descriptor,
            ElectricalBlockContract electricalContract,
            Map<String, ResistorRecipe> resistorRecipes,
            Collection<NmosRecipe> nmosRecipes, Collection<LedRecipe> ledRecipes,
            FaultSpec faultSpec, String repairLocalComponentId,
            Collection<String> inputRequirements,
            Collection<String> retestRequirements,
            ControlledIndicatorValueSynthesis.ResolvedRecipe resolvedValueRecipe) {
        this.providerTypeId = FunctionalBlockDescriptor.requireId(
                providerTypeId, "providerTypeId");
        FunctionalBlockDescriptor.requirePositiveVersion(providerVersion,
                "providerVersion", this.providerTypeId);
        this.providerVersion = providerVersion;
        this.descriptor = required(descriptor, "descriptor");
        this.electricalContract = required(electricalContract,
                "electricalContract");
        if (electricalContract.getDescriptor() != descriptor) {
            throw new IllegalArgumentException(
                    "Electrical contract must retain its local descriptor");
        }
        if (!descriptor.getInstanceKey().equals(
                electricalContract.getDescriptor().getInstanceKey())) {
            throw new IllegalArgumentException("Contribution instance mismatch");
        }
        this.resistorRecipes = immutableResistors(resistorRecipes);
        this.resistorRecipe = this.resistorRecipes.isEmpty() ? null
                : this.resistorRecipes.values().iterator().next();
        this.nmosRecipes = immutableNmos(nmosRecipes);
        this.ledRecipes = immutableLeds(ledRecipes);
        this.faultSpec = required(faultSpec, "faultSpec");
        requireDeclared(descriptor, faultSpec.getTargetComponentLocalId());
        this.faultLocalId = faultSpec.getTargetComponentLocalId();
        this.faultEffectiveOhms = faultSpec.getEffectiveResistanceOhms();
        if (this.resistorRecipes.isEmpty() &&
                this.nmosRecipes.isEmpty() && this.ledRecipes.isEmpty())
            throw new IllegalArgumentException("At least one local recipe is required");
        for (ResistorRecipe recipe : this.resistorRecipes.values())
            requireDeclared(descriptor, recipe.getComponentLocalId());
        for (NmosRecipe recipe : this.nmosRecipes.values())
            requireDeclared(descriptor, recipe.getComponentLocalId());
        for (LedRecipe recipe : this.ledRecipes.values())
            requireDeclared(descriptor, recipe.getComponentLocalId());
        this.repairLocalComponentId = FunctionalBlockDescriptor.requireId(
                repairLocalComponentId, "repairLocalComponentId");
        if (!descriptor.getComponents().containsKey(repairLocalComponentId)) {
            throw new IllegalArgumentException("Repair component is undeclared");
        }
        if (!repairLocalComponentId.equals(faultSpec.getTargetComponentLocalId()))
            throw new IllegalArgumentException("Fault and repair must name the same component");
        this.inputRequirements = immutableRequirements(inputRequirements,
                "inputRequirements");
        this.retestRequirements = immutableRequirements(retestRequirements,
                "retestRequirements");
        this.resolvedValueRecipe = resolvedValueRecipe;
        if (resolvedValueRecipe != null) {
            if (resistorRecipe == null ||
                    !"RLOAD".equals(resistorRecipe.getComponentLocalId()) ||
                    resistorRecipe.getResolvedRecipe() != resolvedValueRecipe)
                throw new IllegalArgumentException(
                        "Resolved value recipe must own the RLOAD recipe");
        }
    }

    String getProviderTypeId() { return providerTypeId; }
    int getProviderVersion() { return providerVersion; }
    FunctionalBlockDescriptor getDescriptor() { return descriptor; }
    ElectricalBlockContract getElectricalContract() { return electricalContract; }
    ResistorRecipe getResistorRecipe() { return resistorRecipe; }

    /** Convenience map retained for assembler code that handles recipes generically. */
    Map<String, ResistorRecipe> getResistors() {
        return resistorRecipes;
    }

    ResistorRecipe getResistor(String localComponentId) {
        return resistorRecipes.get(localComponentId);
    }
    Map<String, NmosRecipe> getNmosRecipes() { return nmosRecipes; }
    Map<String, LedRecipe> getLedRecipes() { return ledRecipes; }
    FaultSpec getFaultSpec() { return faultSpec; }

    String getComponentLocalId() { return resistorRecipe == null ? null : resistorRecipe.getComponentLocalId(); }
    double getResistanceOhms() { return resistorRecipe == null ? 0.0 : resistorRecipe.getResistanceOhms(); }
    double getRatedWatts() { return resistorRecipe == null ? 0.0 : resistorRecipe.getRatedWatts(); }
    String getFaultLocalId() { return faultLocalId; }
    double getFaultEffectiveOhms() { return faultEffectiveOhms; }
    String getRepairLocalComponentId() { return repairLocalComponentId; }
    List<String> getInputRequirements() { return inputRequirements; }
    List<String> getRetestRequirements() { return retestRequirements; }
    ControlledIndicatorValueSynthesis.ResolvedRecipe getResolvedValueRecipe() {
        return resolvedValueRecipe;
    }

    /**
     * Verify a healthy local resistor against actual solver observations.
     * Values are compared by magnitude because CircuitJS current orientation
     * is an implementation detail of the physical endpoint mapping.
     */
    void verifyHealthy(Observation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("Local observation is required");
        }
        ResistorRecipe recipe = primaryRecipe();
        double firstVoltage = observation.voltage(recipe.getFirstEndpointLocalId());
        double secondVoltage = observation.voltage(recipe.getSecondEndpointLocalId());
        double current = observation.current(recipe.getComponentLocalId());
        double resistance = observation.resistance(recipe.getComponentLocalId());
        requireFinite(firstVoltage, recipe.getFirstEndpointLocalId() + " voltage");
        requireFinite(secondVoltage, recipe.getSecondEndpointLocalId() + " voltage");
        requireFinite(current, "R1 current");
        requireFinite(resistance, "R1 resistance");
        if (!approximately(resistance, recipe.getResistanceOhms(),
                resistanceTolerance(recipe.getResistanceOhms()))) {
            throw new IllegalArgumentException("Healthy resistor value mismatch");
        }
        double absoluteCurrent = Math.abs(current);
        if (absoluteCurrent < MIN_HEALTHY_CURRENT_AMPS) {
            throw new IllegalArgumentException("Healthy resistor current is too low");
        }
        if (absoluteCurrent > MAX_OPERATING_CURRENT_AMPS + 1.0e-9) {
            throw new IllegalArgumentException("Healthy resistor current exceeds bound");
        }
        double measuredDrop = Math.abs(firstVoltage - secondVoltage);
        double expectedDrop = absoluteCurrent * recipe.getResistanceOhms();
        if (!approximately(measuredDrop, expectedDrop,
                Math.max(1.0e-5, expectedDrop * 2.0e-3))) {
            throw new IllegalArgumentException("Healthy resistor violates Ohm's law");
        }
    }

    /** Classify the actual measured local current. */
    ObservationResult observe(Observation observation) {
        if (observation == null) {
            return ObservationResult.INVALID;
        }
        ResistorRecipe recipe = primaryRecipe();
        double firstVoltage = observation.voltage(recipe.getFirstEndpointLocalId());
        double secondVoltage = observation.voltage(recipe.getSecondEndpointLocalId());
        double current = observation.current(recipe.getComponentLocalId());
        double resistance = observation.resistance(recipe.getComponentLocalId());
        if (!finite(firstVoltage) || !finite(secondVoltage)
                || !finite(current) || !finite(resistance)
                || resistance <= 0.0) {
            return ObservationResult.INVALID;
        }
        return Math.abs(current) < MIN_HEALTHY_CURRENT_AMPS
                ? ObservationResult.LOW_CURRENT : ObservationResult.CONDUCTING;
    }

    /** Stable semantic fingerprint used by pure replay checks. */
    String semanticSignature() {
        StringBuilder result = new StringBuilder();
        result.append(providerTypeId).append('@').append(providerVersion)
                .append('|').append(descriptorSignature(descriptor))
                .append('|').append(contractSignature(electricalContract))
                .append("|resistors=").append(resistorRecipes)
                .append("|nmos=").append(nmosRecipes)
                .append("|leds=").append(ledRecipes)
                .append("|fault=").append(faultSpec.getKind()).append(':')
                .append(faultLocalId).append(':')
                .append(Double.toString(faultEffectiveOhms))
                .append("|repair=").append(repairLocalComponentId)
                .append("|input=").append(inputRequirements)
                .append("|retest=").append(retestRequirements);
        if (resolvedValueRecipe != null)
            result.append("|resolved=").append(resolvedValueRecipe.semanticSignature());
        return result.toString();
    }

    /** Structural equality helper for request validation; no object identity is used. */
    static boolean sameContract(ElectricalBlockContract first,
            ElectricalBlockContract second) {
        return first != null && second != null
                && contractSignature(first).equals(contractSignature(second));
    }

    static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String descriptorSignature(FunctionalBlockDescriptor value) {
        StringBuilder result = new StringBuilder();
        result.append(value.getTypeId()).append('@').append(value.getSchemaVersion())
                .append('/').append(value.getInstanceKey());
        result.append(";parameters=");
        for (Map.Entry<String, FunctionalBlockDescriptor.Parameter> entry
                : value.getParameters().entrySet()) {
            result.append(entry.getKey()).append(':')
                    .append(valueSignature(entry.getValue().getValue())).append(',');
        }
        result.append(";components=");
        for (Map.Entry<String, FunctionalBlockDescriptor.Component> entry
                : value.getComponents().entrySet()) {
            result.append(entry.getKey()).append(':')
                    .append(entry.getValue().getTypeId()).append('[');
            for (String terminal : entry.getValue().getTerminalIds()) {
                result.append(terminal).append(',');
            }
            result.append(']').append(',');
        }
        result.append(";nets=").append(value.getNetIds());
        result.append(";endpoints=");
        for (Map.Entry<String, FunctionalBlockDescriptor.Endpoint> entry
                : value.getEndpoints().entrySet()) {
            FunctionalBlockDescriptor.Endpoint endpoint = entry.getValue();
            result.append(entry.getKey()).append(':').append(endpoint.getComponentId())
                    .append('.').append(endpoint.getTerminalId()).append(',');
        }
        result.append(";pads=");
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry
                : value.getPads().entrySet()) {
            FunctionalBlockDescriptor.Pad pad = entry.getValue();
            result.append(entry.getKey()).append(':').append(pad.getEndpointId())
                    .append('@').append(pad.getNetId()).append(',');
        }
        result.append(";roles=");
        for (Map.Entry<String, FunctionalBlockDescriptor.Role> entry
                : value.getRoles().entrySet()) {
            FunctionalBlockDescriptor.Role role = entry.getValue();
            result.append(entry.getKey()).append(':')
                    .append(role.getRequirement()).append('[');
            for (FunctionalBlockDescriptor.LocalRef member : role.getMembers()) {
                result.append(member.getKind().getToken()).append('/')
                        .append(member.getId()).append(',');
            }
            result.append(']').append(',');
        }
        result.append(";ports=");
        for (Map.Entry<String, FunctionalBlockDescriptor.Port> entry
                : value.getPorts().entrySet()) {
            FunctionalBlockDescriptor.Port port = entry.getValue();
            result.append(entry.getKey()).append(':').append(port.getRoleId())
                    .append('/').append(port.getAttachment().getKind().getToken())
                    .append('/').append(port.getAttachment().getId()).append(',');
        }
        return result.toString();
    }

    private static String valueSignature(FunctionalBlockDescriptor.Value value) {
        switch (value.getKind()) {
        case BOOLEAN:
            return "b:" + value.getBoolean();
        case INTEGER:
            return "i:" + value.getInteger();
        case DECIMAL:
            return "d:" + Double.toString(value.getDecimal());
        case TEXT:
            return "t:" + value.getText();
        default:
            return value.getKind().toString();
        }
    }

    private static String contractSignature(ElectricalBlockContract value) {
        StringBuilder result = new StringBuilder(descriptorSignature(
                value.getDescriptor()));
        result.append(";electrical=");
        for (Map.Entry<String, ElectricalPortContract> entry
                : value.getPorts().entrySet()) {
            ElectricalPortContract port = entry.getValue();
            result.append(entry.getKey()).append(':')
                    .append(port.getRole()).append('/')
                    .append(port.getDirection()).append('/')
                    .append(port.getBehavior()).append('/')
                    .append(port.getDrive()).append('/')
                    .append(domainSignature(port.getDomain())).append('/')
                    .append(scalarSignature(port.getNominalVoltage())).append('/')
                    .append(rangeSignature(port.getGuaranteedVoltage())).append('/')
                    .append(rangeSignature(port.getAllowedVoltage())).append('/')
                    .append(port.getLoading()).append('/')
                    .append(scalarSignature(port.getCapacityAmps())).append('/')
                    .append(scalarSignature(port.getDemandAmps())).append('/')
                    .append(digitalSignature(port.getDigital())).append('/')
                    .append(port.getMergePolicy()).append('/')
                    .append(port.getAccessRequirement()).append('/')
                    .append(port.getAccessProvision()).append(';');
        }
        result.append(";attachments=");
        for (Map.Entry<String, ElectricalPortContract> entry
                : value.getPorts().entrySet()) {
            result.append(entry.getKey()).append('@')
                    .append(value.getAttachmentKey(entry.getKey())).append(';');
        }
        result.append(";adapters=");
        for (Map.Entry<String, ElectricalBlockContract.Adapter> entry
                : value.getAdapters().entrySet()) {
            ElectricalBlockContract.Adapter adapter = entry.getValue();
            result.append(entry.getKey()).append(':').append(adapter.getKind())
                    .append('/').append(adapter.getInputPortId()).append('/')
                    .append(adapter.getOutputPortId()).append('/')
                    .append(adapter.isIsolated()).append(';');
        }
        return result.toString();
    }

    private static String domainSignature(ElectricalPortContract.Domain value) {
        return value.getReferenceNetId() + '@'
                + (value.getIsolationId() == null ? "~" : value.getIsolationId());
    }

    private static String scalarSignature(ElectricalPortContract.Scalar value) {
        return value.getState().name() + (value.getState()
                == ElectricalPortContract.State.KNOWN
                ? ":" + Double.toString(value.getValue()) : "");
    }

    private static String rangeSignature(ElectricalPortContract.Range value) {
        return value.getState().name() + (value.getState()
                == ElectricalPortContract.State.KNOWN
                ? ":" + Double.toString(value.getMinimum()) + ','
                    + Double.toString(value.getMaximum()) : "");
    }

    private static String digitalSignature(ElectricalPortContract.Digital value) {
        return value.getActiveLevel().name() + '/'
                + scalarSignature(value.getLowMaximum()) + '/'
                + scalarSignature(value.getHighMinimum()) + '/'
                + scalarSignature(value.getInputLowMaximum()) + '/'
                + scalarSignature(value.getInputHighMinimum());
    }

    private static List<String> immutableRequirements(Collection<String> values,
            String field) {
        if (values == null) {
            throw new IllegalArgumentException(field + " are required");
        }
        ArrayList<String> copy = new ArrayList<String>();
        for (String value : values) {
            copy.add(FunctionalBlockDescriptor.requireId(value, field));
        }
        Collections.sort(copy);
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new IllegalArgumentException("Duplicate " + field + " "
                        + copy.get(index));
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private ResistorRecipe primaryRecipe() {
        if (resistorRecipe == null) {
            throw new IllegalStateException("Contribution has no resistor observation");
        }
        return resistorRecipe;
    }

    private static Map<String, ResistorRecipe> immutableResistors(
            Map<String, ResistorRecipe> values) {
        if (values == null) throw new IllegalArgumentException("resistors are required");
        TreeMap<String, ResistorRecipe> copy = new TreeMap<String, ResistorRecipe>();
        for (Map.Entry<String, ResistorRecipe> entry : values.entrySet()) {
            String key = FunctionalBlockDescriptor.requireId(entry.getKey(), "resistors");
            ResistorRecipe value = required(entry.getValue(), "resistor");
            if (!key.equals(value.getComponentLocalId()))
                throw new IllegalArgumentException("Resistor map key mismatch");
            if (copy.put(key, value) != null)
                throw new IllegalArgumentException("Duplicate resistor " + key);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, NmosRecipe> immutableNmos(
            Collection<NmosRecipe> values) {
        if (values == null) throw new IllegalArgumentException("nmos recipes are required");
        TreeMap<String, NmosRecipe> copy = new TreeMap<String, NmosRecipe>();
        for (NmosRecipe value : values) {
            value = required(value, "nmos recipe");
            if (copy.put(value.getComponentLocalId(), value) != null)
                throw new IllegalArgumentException("Duplicate NMOS recipe " + value.getComponentLocalId());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, LedRecipe> immutableLeds(
            Collection<LedRecipe> values) {
        if (values == null) throw new IllegalArgumentException("led recipes are required");
        TreeMap<String, LedRecipe> copy = new TreeMap<String, LedRecipe>();
        for (LedRecipe value : values) {
            value = required(value, "led recipe");
            if (copy.put(value.getComponentLocalId(), value) != null)
                throw new IllegalArgumentException("Duplicate LED recipe " + value.getComponentLocalId());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireDeclared(FunctionalBlockDescriptor descriptor,
            String componentLocalId) {
        if (!descriptor.getComponents().containsKey(componentLocalId))
            throw new IllegalArgumentException("Recipe component is undeclared");
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static void requireFinitePositive(double value, String field) {
        if (!finite(value) || value <= 0.0) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!finite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private static boolean approximately(double first, double second,
            double tolerance) {
        return Math.abs(first - second) <= tolerance;
    }

    private static double resistanceTolerance(double value) {
        return Math.max(1.0e-6, Math.abs(value) * 1.0e-6);
    }
}
