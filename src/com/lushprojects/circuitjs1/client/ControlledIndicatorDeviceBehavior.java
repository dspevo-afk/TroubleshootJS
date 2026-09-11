package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Device-owned behavior for the repeated low-side indicator composition.
 *
 * <p>The behavior owns the aggregate device contract. Each channel delegates
 * transistor-specific solver assertions to the provider selected by the
 * resolved contribution. A channel key is the only identity used here, so
 * the same implementation can be instantiated repeatedly without sharing
 * command state, physical owners, or solver endpoints.</p>
 */
final class ControlledIndicatorDeviceBehavior
        implements GeneratedChallengeBehaviorContract, GeneratedDiagnosticExecutionProvider {
    static final String FAMILY_ID = ControlledIndicatorBlockContributions.FAMILY_ID;
    static final String TOPOLOGY_VARIANT_ID = "LOW_SIDE_INDICATOR_CHANNELS";
    static final String LOAD_POWER_INPUT_ID = "LOAD_VIN_INPUT";
    static final double SUPPLY_VOLTAGE = 5.0;

    private static final double OFF_CURRENT = .000001;
    private static final double UNPOWERED_CURRENT = .000001;

    private final BoundedAssemblyPlan plan;
    private final ConstructionReceipt constructionReceipt;
    private final Map<String, ElectricalConstructionContext.ControlCommandHandle> commands;
    private final Map<String, ControlledIndicatorDriverObservation> driverProviders;

    ControlledIndicatorDeviceBehavior(BoundedAssemblyPlan plan,
            ConstructionReceipt constructionReceipt) {
        if (plan == null || !plan.isControlledIndicator())
            throw new IllegalArgumentException("Controlled indicator plan is required");
        if (constructionReceipt == null || constructionReceipt.isAborted())
            throw new IllegalArgumentException("Controlled indicator construction receipt is required");
        this.plan = plan;
        this.constructionReceipt = constructionReceipt;
        TreeMap<String, ElectricalConstructionContext.ControlCommandHandle> commandMap =
                new TreeMap<String, ElectricalConstructionContext.ControlCommandHandle>();
        TreeMap<String, ControlledIndicatorDriverObservation> providerMap =
                new TreeMap<String, ControlledIndicatorDriverObservation>();
        DeviceJoinReceipt deviceReceipt = constructionReceipt.getDeviceReceipt();
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            commandMap.put(channel.getKey(), deviceReceipt.getCommand(channel.getControlJoinId()));
            ComposedBlockContribution driver = plan.getBlocks().get(channel.getDriverKey());
            if (driver == null)
                throw new IllegalArgumentException("Missing driver contribution " + channel.getDriverKey());
            providerMap.put(channel.getKey(), ControlledIndicatorDriverObservations
                .forProvider(driver.getProviderTypeId()));
        }
        if (commandMap.size() != plan.getChannels().size())
            throw new IllegalArgumentException("Controlled channel command population changed");
        this.commands = Collections.unmodifiableMap(commandMap);
        this.driverProviders = Collections.unmodifiableMap(providerMap);
        // Fail closed while the immutable plan is still being installed if a
        // serviceable resistor has no complete, unique player isolation pair.
        getIsolationPairs();
    }

    BoundedAssemblyPlan getPlan() { return plan; }
    public ConstructionReceipt getConstructionReceipt() { return constructionReceipt; }
    public BoundedAssemblyPlan getAssemblyPlan() { return plan; }

    @Override
    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return GeneratedDiagnosticPlanCatalog.forAssembly(plan);
    }

    @Override
    public void verifyHealthy(GeneratedBoardInstance instance, BoardPowerState powerState) {
        if (instance == null || powerState == null)
            throw new IllegalArgumentException("Missing controlled healthy context");
        if (powerState == BoardPowerState.UNPOWERED) {
            for (ControlledIndicatorChannel channel : plan.getChannels()) {
                ControlledIndicatorChannelObservation context = context(instance, channel);
                requireNearZero(context.loadCurrent(), "Unpowered load current is not zero");
                requireNearZero(context.ledCurrent(), "Unpowered LED current is not zero");
            }
            ControlledIndicatorChannelObservation support =
                context(instance, plan.getChannels().get(0));
            requireNearZero(support.supportCurrent(),
                "Unpowered support current is not zero");
            requireNearZero(support.supportLedCurrent(),
                "Unpowered support LED current is not zero");
            return;
        }
        CirSim sim = CircuitElm.sim;
        if (sim == null)
            throw new IllegalStateException("Healthy controlled proof has no active simulator");
        Map<String, Boolean> prior = channelStates();
        sim.beginObservationalValidation();
        try {
            require(verifyAllHealthyPatterns(sim, instance),
                "Controlled indicator does not solve to independent channel behavior");
        } finally {
            try { restoreChannelStates(sim, prior); }
            finally { sim.endObservationalValidation(); }
        }
    }

    void verifyHealthyOff(GeneratedBoardInstance instance) { requireHealthyOff(instance); }
    void verifyHealthyOn(GeneratedBoardInstance instance) { requireHealthyOn(instance); }

    @Override
    public void verifyFaulted(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (instance == null || powerState == null || modifications == null)
            throw new IllegalArgumentException("Missing controlled fault context");
        if (powerState == BoardPowerState.UNPOWERED) {
            for (ControlledIndicatorChannel channel : plan.getChannels())
                requireNearZero(context(instance, channel).loadCurrent(),
                    "Unpowered controlled load current is not zero during fault verification");
            return;
        }
        if (!modifications.isFullyRestored())
            throw new IllegalStateException("Fault proof requires untouched physical owners");
        CirSim sim = CircuitElm.sim;
        Map<String, Boolean> prior = channelStates();
        if (sim != null) sim.beginObservationalValidation();
        try {
            setAllChannels(sim, true);
            requireFaultedOnSymptom(instance);
        } finally {
            try { restoreChannelStates(sim, prior); }
            finally { if (sim != null) sim.endObservationalValidation(); }
        }
    }

    @Override
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        if (instance == null || powerState != BoardPowerState.POWERED ||
                activeMeasurementOverlay || modifications == null ||
                !modifications.isFullyRestored())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        CirSim sim = CircuitElm.sim;
        Map<String, Boolean> prior = channelStates();
        if (sim != null) sim.beginObservationalValidation();
        try {
            if (sim == null || !verifyAllHealthyPatterns(sim, instance))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            return GeneratedRepairStatus.CORRECTLY_RESTORED;
        } finally {
            try { restoreChannelStates(sim, prior); }
            finally { if (sim != null) sim.endObservationalValidation(); }
        }
    }

    @Override
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState,
                activeMeasurementOverlay) == GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    GeneratedBoardFamilyState createFamilyState() { return new FamilyState(this); }

    GeneratedScenarioCatalog<GeneratedObservedBehavior> createScenarioCatalog() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> scenarios =
                new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        scenarios.add(new GeneratedScenario<GeneratedObservedBehavior>(
                "CONTROLLED_INDICATOR_DARK", "CONTROLLED_LOAD_DOES_NOT_SWITCH_ON",
                "One of the controlled indicators does not turn on.",
                GeneratedObservedBehavior.NMOS_LOAD_NOT_SWITCHING,
                new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                    public boolean matches(GeneratedBoardInstance instance,
                            BoardModificationController modifications,
                            BoardPowerState powerState,
                            GeneratedObservedBehavior behavior) {
                        return powerState == BoardPowerState.POWERED && instance != null &&
                            hasCommandedOnChannel() && hasFaultedOutput(instance);
                    }
                }, new GeneratedScenarioPresentation<GeneratedObservedBehavior>() {
                    public void present(CirSim sim, GeneratedBoardInstance instance,
                            GeneratedObservedBehavior behavior) {
                        if (behavior != GeneratedObservedBehavior.NMOS_LOAD_NOT_SWITCHING)
                            throw new IllegalStateException("Unsupported controlled complaint");
                        setAllChannels(sim, true);
                    }
                }));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(scenarios, this);
    }

    /** Aggregate query for device validation. */
    boolean isCommandedOn() { return areAllChannelsHigh(); }
    boolean isHealthyOn(GeneratedBoardInstance instance) { return healthyOn(instance); }
    boolean isHealthyOff(GeneratedBoardInstance instance) { return healthyOff(instance); }

    private void requireHealthyOn(GeneratedBoardInstance instance) {
        require(healthyOn(instance), "Controlled indicator does not solve to ON behavior");
    }

    private void requireHealthyOff(GeneratedBoardInstance instance) {
        require(healthyOff(instance), "Controlled indicator does not solve to OFF behavior");
    }

    private boolean healthyOn(GeneratedBoardInstance instance) {
        if (!isHealthySupport(instance)) return false;
        for (ControlledIndicatorChannel channel : plan.getChannels())
            if (!driverProviders.get(channel.getKey()).isHealthyOn(context(instance, channel)))
                return false;
        return true;
    }

    private boolean healthyOff(GeneratedBoardInstance instance) {
        if (!isHealthySupport(instance)) return false;
        for (ControlledIndicatorChannel channel : plan.getChannels())
            if (!driverProviders.get(channel.getKey()).isHealthyOff(context(instance, channel)))
                return false;
        return true;
    }

    /**
     * Prove the aggregate device with all-off, all-on, and one-channel-on
     * patterns.  The latter patterns are required because a shorted control
     * path can make simultaneous all-on/all-off checks look healthy.
     */
    private boolean verifyAllHealthyPatterns(CirSim sim, GeneratedBoardInstance instance) {
        applyPattern(sim, null, false);
        if (!healthyPattern(instance, null, false)) return false;
        applyPattern(sim, null, true);
        if (!healthyPattern(instance, null, true)) return false;
        for (ControlledIndicatorChannel selected : plan.getChannels()) {
            applyPattern(sim, selected, false);
            if (!healthyPattern(instance, selected, false)) return false;
        }
        return true;
    }

    private void applyPattern(CirSim sim, ControlledIndicatorChannel selected,
            boolean allHigh) {
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            boolean high = allHigh || (selected != null &&
                selected.getKey().equals(channel.getKey()));
            ElectricalConstructionContext.ControlCommandHandle command =
                commands.get(channel.getKey());
            if (command == null) throw new IllegalStateException(
                "Missing channel command " + channel.getKey());
            if (command.isHigh() != high) command.setHigh(high);
        }
        settle(sim);
    }

    private boolean healthyPattern(GeneratedBoardInstance instance,
            ControlledIndicatorChannel selected, boolean allHigh) {
        try {
            if (!isHealthySupport(instance)) return false;
            for (ControlledIndicatorChannel channel : plan.getChannels()) {
                boolean high = allHigh || (selected != null &&
                    selected.getKey().equals(channel.getKey()));
                ControlledIndicatorDriverObservation provider =
                    driverProviders.get(channel.getKey());
                ControlledIndicatorChannelObservation observation =
                    context(instance, channel);
                if (high ? !provider.isHealthyOn(observation) :
                        !provider.isHealthyOff(observation)) return false;
            }
            return true;
        } catch (IllegalStateException invalidObservation) {
            // A disconnected or incomplete support/runtime binding is a failed
            // repair observation.  The admission verifier wraps healthy proofs
            // with the required diagnostic instead of leaking this exception.
            return false;
        }
    }

    private void requireFaultedOnSymptom(GeneratedBoardInstance instance) {
        GeneratedFaultLocus locus = instance.getFaultLocus();
        if (locus == null || locus.getComponentId() == null)
            throw new IllegalStateException("Controlled fault has no component locus");
        boolean matched = false;
        String faultedChannelKey = null;
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            String driverKey = channel.getDriverKey();
            String loadKey = channel.getLoadKey();
            ControlledIndicatorChannelObservation context = context(instance, channel);
            ComposedBlockContribution driver = plan.getBlocks().get(driverKey);
            String driverFault = driver.getFaultSpec() == null ? null :
                driver.getFaultSpec().getTargetComponentLocalId();
            String driverId = driverFault == null ? null :
                plan.idFor(driverKey, EntityKind.COMPONENT, driverFault);
            String loadId = plan.idFor(loadKey, EntityKind.COMPONENT, "RLOAD");
            if (locus.getComponentId().equals(driverId)) {
                matched = true;
                faultedChannelKey = channel.getKey();
                require(driverProviders.get(channel.getKey()).isFaultedOn(context),
                    "Open driver resistor did not leave the selected channel off");
            } else if (locus.getComponentId().equals(loadId)) {
                matched = true;
                faultedChannelKey = channel.getKey();
                require(context.loadCurrent() < OFF_CURRENT && context.ledCurrent() < OFF_CURRENT,
                    "Open load resistor still conducts on command");
                require(driverProviders.get(channel.getKey()).isDriverEnergizedOn(context),
                    "Open load resistor lost the commanded driver state");
            }
        }
        require(matched, "Fault owner is outside the controlled channel population");
        require(isHealthySupport(instance), "Healthy support contribution is not operating");
        CirSim sim = CircuitElm.sim;
        require(sim != null, "Controlled fault proof has no active simulator");
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            if (channel.getKey().equals(faultedChannelKey)) continue;
            ControlledIndicatorChannelObservation observation = context(instance, channel);
            require(driverProviders.get(channel.getKey()).isHealthyOn(observation),
                "Unaffected controlled channel did not remain healthy on command");
            setChannel(sim, channel, false);
            require(driverProviders.get(channel.getKey()).isHealthyOff(context(instance, channel)),
                "Unaffected controlled channel did not turn off independently");
            setChannel(sim, channel, true);
        }
    }

    private boolean hasFaultedOutput(GeneratedBoardInstance instance) {
        GeneratedFaultLocus locus = instance.getFaultLocus();
        if (locus == null || locus.getComponentId() == null) return false;
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            if (locus.getComponentId().equals(plan.idFor(channel.getLoadKey(),
                    EntityKind.COMPONENT, "RLOAD"))) {
                ControlledIndicatorChannelObservation context = context(instance, channel);
                return context.loadCurrent() < OFF_CURRENT && context.ledCurrent() < OFF_CURRENT;
            }
            ComposedBlockContribution driver = plan.getBlocks().get(channel.getDriverKey());
            String local = driver.getFaultSpec() == null ? null :
                driver.getFaultSpec().getTargetComponentLocalId();
            if (local != null && locus.getComponentId().equals(plan.idFor(
                    channel.getDriverKey(), EntityKind.COMPONENT, local))) {
                ControlledIndicatorChannelObservation context = context(instance, channel);
                return context.loadCurrent() < OFF_CURRENT && context.ledCurrent() < OFF_CURRENT;
            }
        }
        return false;
    }

    private ControlledIndicatorChannelObservation context(GeneratedBoardInstance instance,
            ControlledIndicatorChannel channel) {
        return new ControlledIndicatorChannelObservation(instance, plan, channel);
    }

    private void setChannel(CirSim sim, ControlledIndicatorChannel channel, boolean high) {
        ElectricalConstructionContext.ControlCommandHandle command = commands.get(channel.getKey());
        if (command == null) throw new IllegalStateException("Missing channel command " + channel.getKey());
        if (command.isHigh() != high) command.setHigh(high);
        settle(sim);
    }

    void setAllChannels(CirSim sim, boolean high) {
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            ElectricalConstructionContext.ControlCommandHandle command = commands.get(channel.getKey());
            if (command == null) throw new IllegalStateException("Missing channel command " + channel.getKey());
            if (command.isHigh() != high) command.setHigh(high);
        }
        settle(sim);
    }

    private void settle(CirSim sim) {
        if (sim != null) {
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.runCircuit(true);
            sim.runCircuit(true);
        }
    }

    private Map<String, Boolean> channelStates() {
        TreeMap<String, Boolean> result = new TreeMap<String, Boolean>();
        for (ControlledIndicatorChannel channel : plan.getChannels())
            result.put(channel.getKey(), Boolean.valueOf(commands.get(channel.getKey()).isHigh()));
        return result;
    }

    private void restoreChannelStates(CirSim sim, Map<String, Boolean> prior) {
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            Boolean value = prior.get(channel.getKey());
            if (value == null) throw new IllegalStateException("Missing prior channel state");
            commands.get(channel.getKey()).setHigh(value.booleanValue());
        }
        settle(sim);
    }

    private boolean areAllChannelsHigh() {
        for (ControlledIndicatorChannel channel : plan.getChannels())
            if (!commands.get(channel.getKey()).isHigh()) return false;
        return true;
    }

    private boolean hasCommandedOnChannel() {
        for (ControlledIndicatorChannel channel : plan.getChannels())
            if (commands.get(channel.getKey()).isHigh()) return true;
        return false;
    }

    @Override
    public String[][] getIsolationPairs() {
        TreeMap<String, String[]> pairsByTarget = new TreeMap<String, String[]>();
        TreeSet<String> uniquePairs = new TreeSet<String>();
        for (Map.Entry<String, String> owner : plan.getDecisionOwners().entrySet()) {
            String targetId = owner.getValue();
            if (targetId == null || pairsByTarget.containsKey(targetId))
                throw new IllegalStateException("Duplicate declared serviceable resistor target " +
                    targetId);
            String ownerKey = null;
            ComposedBlockContribution.ResistorRecipe recipe = null;
            for (Map.Entry<String, ComposedBlockContribution> block : plan.getBlocks().entrySet()) {
                ComposedBlockContribution contribution = block.getValue();
                if (contribution == null) throw new IllegalStateException(
                    "Null contribution in controlled diagnostic plan");
                for (Map.Entry<String, ComposedBlockContribution.ResistorRecipe> resistor :
                        contribution.getResistors().entrySet()) {
                    String candidate = plan.idFor(block.getKey(), EntityKind.COMPONENT,
                        resistor.getKey());
                    if (!targetId.equals(candidate)) continue;
                    if (recipe != null) throw new IllegalStateException(
                        "Serviceable resistor target resolves more than once: " + targetId);
                    ownerKey = block.getKey();
                    recipe = resistor.getValue();
                }
            }
            if (ownerKey == null || recipe == null || !recipe.isMutable())
                throw new IllegalStateException("Declared fault owner is not a mutable resistor: " +
                    targetId);
            FunctionalBlockDescriptor descriptor = plan.getBlocks().get(ownerKey).getDescriptor();
            if (!descriptor.getComponents().containsKey(recipe.getComponentLocalId()) ||
                    !descriptor.getEndpoints().containsKey(recipe.getFirstEndpointLocalId()) ||
                    !descriptor.getEndpoints().containsKey(recipe.getSecondEndpointLocalId()) ||
                    !descriptor.getPads().containsKey(recipe.getFirstPadLocalId()) ||
                    !descriptor.getPads().containsKey(recipe.getSecondPadLocalId()))
                throw new IllegalStateException("Serviceable resistor endpoints/pads are incomplete: " +
                    targetId);
            String first = plan.idFor(ownerKey, EntityKind.PAD, recipe.getFirstPadLocalId());
            String second = plan.idFor(ownerKey, EntityKind.PAD, recipe.getSecondPadLocalId());
            if (first.equals(second)) throw new IllegalStateException(
                "Serviceable resistor isolation pads are identical: " + targetId);
            String low = first.compareTo(second) < 0 ? first : second;
            String high = first.compareTo(second) < 0 ? second : first;
            if (!uniquePairs.add(low + "|" + high))
                throw new IllegalStateException("Duplicate serviceable resistor isolation pair: " +
                    first + "/" + second);
            pairsByTarget.put(targetId, new String[] { first, second });
        }
        if (pairsByTarget.size() != plan.getDecisionOwners().size())
            throw new IllegalStateException("Controlled isolation coverage does not match declared " +
                "serviceable resistor targets");
        ArrayList<String[]> pairs = new ArrayList<String[]>();
        for (Map.Entry<String, String> owner : plan.getDecisionOwners().entrySet()) {
            String[] pair = pairsByTarget.get(owner.getValue());
            if (pair == null) throw new IllegalStateException(
                "Missing isolation pair for declared serviceable resistor " + owner.getValue());
            pairs.add(pair);
        }
        return pairs.toArray(new String[pairs.size()][]);
    }

    @Override
    public String getProbeLabel(String qualifiedPadId) {
        if (qualifiedPadId == null) throw new IllegalArgumentException("Probe ID is required");
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            if (qualifiedPadId.startsWith(prefix(channel.getDriverKey())))
                return "CHANNEL_" + channel.getLabel() + "_DRIVER_" + suffix(qualifiedPadId);
            if (qualifiedPadId.startsWith(prefix(channel.getLoadKey())))
                return "CHANNEL_" + channel.getLabel() + "_LOAD_" + suffix(qualifiedPadId);
        }
        if (qualifiedPadId.startsWith(prefix(plan.getSupportBlockKey())))
            return "SUPPORT_" + suffix(qualifiedPadId);
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters()) {
            if (qualifiedPadId.startsWith(prefix(adapter.getKey())))
                return (adapter.isControl() ? "CONTROL_" : "POWER_") + suffix(qualifiedPadId);
        }
        throw new IllegalArgumentException("Unknown controlled probe target " + qualifiedPadId);
    }

    private String prefix(String ownerKey) {
        String local = null;
        ComposedBlockContribution contribution = plan.getBlocks().get(ownerKey);
        if (contribution != null && !contribution.getDescriptor().getPads().isEmpty())
            local = contribution.getDescriptor().getPads().keySet().iterator().next();
        if (local == null) {
            for (DeviceAdapterContract adapter : plan.getDeviceAdapters())
                if (adapter.getKey().equals(ownerKey) &&
                        !adapter.getDescriptor().getPads().isEmpty()) {
                    local = adapter.getDescriptor().getPads().keySet().iterator().next();
                    break;
                }
        }
        if (local == null) throw new IllegalArgumentException("Owner has no public pads " + ownerKey);
        String qualified = plan.getNamespace().idFor(ownerKey, EntityKind.PAD, local);
        return qualified.substring(0, qualified.lastIndexOf('/') + 1);
    }

    private String suffix(String qualifiedPadId) {
        int slash = qualifiedPadId.lastIndexOf('/');
        String local = slash < 0 ? qualifiedPadId : qualifiedPadId.substring(slash + 1);
        return local.replace('.', '_').replace('-', '_').toUpperCase();
    }

    @Override
    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if (instance == null || componentId == null)
            throw new IllegalArgumentException("Replacement context is incomplete");
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            ComposedBlockContribution load = plan.getBlocks().get(channel.getLoadKey());
            String loadId = plan.idFor(channel.getLoadKey(), EntityKind.COMPONENT, "RLOAD");
            if (componentId.equals(loadId) && load.getResolvedValueRecipe() != null &&
                    load.getResolvedValueRecipe().getCatalogEntryId() != null)
                return load.getResolvedValueRecipe().getCatalogEntryId();
        }
        PhysicalSpecification specification = instance.getPhysicalSpecifications()
            .getSpecification(componentId);
        if (specification instanceof ResistorNameplate)
            return "R_CATALOG_" + (long)((ResistorNameplate) specification)
                .getNominalResistanceOhms();
        throw new IllegalStateException("No provider-owned replacement catalog for " + componentId);
    }

    @Override
    public boolean isHealthySupport(GeneratedBoardInstance instance) {
        if (instance == null || plan.getChannels().isEmpty()) return false;
        try {
            // The support indicator is deliberately a shared SUPPLY/RETURN
            // branch.  Read it once through a channel-owned observation; the
            // wiring contract proves that all channels use that same branch.
            ControlledIndicatorChannelObservation context =
                context(instance, plan.getChannels().get(0));
            double power = context.powerVoltage();
            double current = context.supportCurrent();
            double led = context.supportLedCurrent();
            return ControlledIndicatorChannelObservation.finite(power) &&
                power >= 4.5 && power <= 5.5 &&
                ControlledIndicatorChannelObservation.finite(current) &&
                ControlledIndicatorChannelObservation.finite(led) &&
                current >= SupplyPresentBlockContributions.HEALTHY_CURRENT_MIN_AMPS &&
                current <= SupplyPresentBlockContributions.HEALTHY_CURRENT_MAX_AMPS &&
                Math.abs(current - led) < .0005;
        } catch (IllegalStateException disconnectedSupport) {
            // A failed/disconnected support branch is a failed customer
            // retest observation, not an uncontrolled verifier exception.
            return false;
        }
    }

    @Override
    public void collectDcSamples(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticPlan diagnosticPlan, Vector<GeneratedDiagnosticSample> samples,
            GeneratedDiagnosticExecutionTrace.Builder trace,
            GeneratedDiagnosticSampleSink sink) {
        if (diagnosticPlan == null || samples == null || trace == null || sink == null)
            throw new IllegalArgumentException("Incomplete controlled diagnostic sample context");
        setAllChannels(sim, false);
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            String highId = channel.getHighOperationId();
            instance.invokeOperation(highId, sim);
            GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
                "controlled-signature-" + highId);
            trace.recordInputPowerTransition(highId);
            trace.recordTemporalWaitSample(channel.getSampleId(true), 0.0);
            appendSamples(sim, instance, diagnosticPlan, samples, trace, sink, highId + "_DC_");
            String lowId = channel.getLowOperationId();
            instance.invokeOperation(lowId, sim);
            GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
                "controlled-signature-" + lowId);
            trace.recordInputPowerTransition(lowId);
            trace.recordTemporalWaitSample(channel.getSampleId(false), 0.0);
            appendSamples(sim, instance, diagnosticPlan, samples, trace, sink, lowId + "_DC_");
        }
    }

    private void appendSamples(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticPlan diagnosticPlan, Vector<GeneratedDiagnosticSample> samples,
            GeneratedDiagnosticExecutionTrace.Builder trace, GeneratedDiagnosticSampleSink sink,
            String prefix) {
        for (String targetId : diagnosticPlan.getProbeTargetIds()) {
            if (targetId.equals(diagnosticPlan.getReferenceTargetId())) continue;
            sink.addSample(samples, prefix + getProbeLabel(targetId), sink.measureDc(
                sim, instance, targetId, diagnosticPlan.getReferenceTargetId(), trace));
        }
    }

    private static void requireNearZero(double value, String message) {
        require(ControlledIndicatorChannelObservation.finite(value) &&
            Math.abs(value) <= UNPOWERED_CURRENT, message + ": " + value);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Device-owned per-channel controls plus the semantic customer retest. */
    private static final class FamilyState implements GeneratedBoardFamilyState {
        private final ControlledIndicatorDeviceBehavior behavior;
        private final GeneratedBoardOperationCatalog operations =
            new GeneratedBoardOperationCatalog();
        private final GeneratedCustomerRetestProfile retestProfile;

        FamilyState(final ControlledIndicatorDeviceBehavior behavior) {
            this.behavior = behavior;
            for (final ControlledIndicatorChannel channel : behavior.plan.getChannels()) {
                operations.add(new GeneratedBoardOperation(channel.getHighOperationId(),
                    "Channel " + channel.getLabel() + " HIGH",
                    new GeneratedBoardOperation.Executor() {
                        public GeneratedCustomerRetestResult execute(CirSim sim,
                                GeneratedBoardInstance instance) {
                            behavior.setChannel(sim, channel, true); return null;
                        }
                    }));
                operations.add(new GeneratedBoardOperation(channel.getLowOperationId(),
                    "Channel " + channel.getLabel() + " LOW",
                    new GeneratedBoardOperation.Executor() {
                        public GeneratedCustomerRetestResult execute(CirSim sim,
                                GeneratedBoardInstance instance) {
                            behavior.setChannel(sim, channel, false); return null;
                        }
                    }));
            }
            retestProfile = new GeneratedCustomerRetestProfile(
                "CONTROLLED_INDICATOR_CUSTOMER_RETEST",
                "Set each channel HIGH and LOW and verify every indicator responds.",
                "The board supply and both control inputs remain connected during the check.",
                "Each external channel input is set HIGH, then LOW.",
                "Each channel driver, load, resistor current, LED current, and supply support.",
                "One HIGH/LOW repetition per channel after CircuitJS settles each command.",
                "All three external inputs and the supply indicator remain connected; physical parts are unchanged.",
                new GeneratedCustomerRetestProfile.Executor() {
                    public GeneratedCustomerRetestResult execute(CirSim sim,
                            GeneratedBoardInstance instance) { return retest(sim, instance); }
                });
            operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
                "Retest Customer", new GeneratedBoardOperation.Executor() {
                    public GeneratedCustomerRetestResult execute(CirSim sim,
                            GeneratedBoardInstance instance) { return retestProfile.execute(sim, instance); }
                }));
        }

        private GeneratedCustomerRetestResult retest(CirSim sim, GeneratedBoardInstance instance) {
            if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, instance))
                return GeneratedCustomerRetestSupport.failure();
            Map<String, Boolean> prior = behavior.channelStates();
            BoardPowerState priorPower = sim.getBoardPowerController().getState();
            boolean priorPhysical = sim.getBoardModificationController().isFullyRestored();
            try {
                return behavior.verifyAllHealthyPatterns(sim, instance) ?
                    GeneratedCustomerRetestSupport.success() :
                    GeneratedCustomerRetestSupport.failure();
            } finally {
                try { behavior.restoreChannelStates(sim, prior); }
                finally {
                    GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
                    if (priorPhysical != sim.getBoardModificationController().isFullyRestored())
                        throw new IllegalStateException("Customer retest changed physical board state");
                }
            }
        }

        public void requireOwnedBy(GeneratedBoardInstance instance) {
            if (behavior != instance.getBehaviorContract())
                throw new IllegalArgumentException("Fresh family state captures a foreign behavior owner");
        }

        public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance, String componentId) {
            return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
        }
        public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
        public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
    }

}
