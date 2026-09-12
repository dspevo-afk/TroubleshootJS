package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessProvision;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessRequirement;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.ActiveLevel;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Behavior;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Direction;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Domain;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Loading;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Role;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Requirement;

/** Typed registry for the low-side role and its indicator-load fixture. */
final class ControlledIndicatorBlockContributions {
    static final int VERSION = 1;
    static final int NPN_VERSION = 1;
    static final int LOAD_VERSION = 2;
    static final String FAMILY_ID = "COMPOSED_CONTROLLED_INDICATOR";
    static final String DRIVER_TYPE_ID = "nmos-low-side-driver";
    static final String NPN_DRIVER_TYPE_ID = "npn-low-side-driver";
    static final String LOAD_TYPE_ID = "resistor-led-load";
    static final String DRIVER_BLOCK_KEY = "driver";
    static final String LOAD_BLOCK_KEY = "load";
    static final String DRIVER_FAULT_DECISION_KEY = "driver-rg-open";
    static final String LOAD_FAULT_DECISION_KEY = "load-rload-open";
    static final String POWER_CONNECTION_ID = "power-supply";
    static final String CONTROL_CONNECTION_ID = "control-input";
    static final String SWITCHED_CONNECTION_ID = "switched-low-side";
    static final String RETURN_CONNECTION_ID = "common-return";

    static final double RG_OHMS = 1000.0;
    static final double RB_OHMS = 2700.0;
    static final double RPD_OHMS = 100000.0;
    static final double RESISTOR_TOLERANCE_FRACTION = 0.05;
    static final double NMOS_THRESHOLD_VOLTS = 1.5;
    static final double NMOS_BETA = 10.0;
    static final double NPN_BETA = 100.0;
    static final double NPN_FORCED_BETA = 20.0;
    static final double NPN_VBE_MIN_VOLTS = 0.5;
    static final double NPN_VBE_MAX_VOLTS = 0.95;

    /** Worst-case NMOS DC control demand is the pull-down current. */
    static final double NMOS_CONTROL_DEMAND_AMPS =
            5.25 / (RPD_OHMS * (1.0 - RESISTOR_TOLERANCE_FRACTION));
    /** Worst-case NPN high-state source current through RB plus RPD. */
    static final double NPN_CONTROL_DEMAND_AMPS =
            (5.25 - NPN_VBE_MIN_VOLTS) /
                    (RB_OHMS * (1.0 - RESISTOR_TOLERANCE_FRACTION)) +
            NPN_VBE_MIN_VOLTS /
                    (RPD_OHMS * (1.0 - RESISTOR_TOLERANCE_FRACTION));
    /** Minimum NPN base current required by 20 mA at forced beta 20. */
    static final double NPN_REQUIRED_BASE_DRIVE_AMPS =
            (0.020 / NPN_FORCED_BETA) +
            NPN_VBE_MAX_VOLTS /
                    (RPD_OHMS * (1.0 - RESISTOR_TOLERANCE_FRACTION));
    /** Available base current at the declared worst high and VBE limits. */
    static final double NPN_AVAILABLE_BASE_DRIVE_AMPS =
            (4.75 - NPN_VBE_MAX_VOLTS) /
                    (RB_OHMS * (1.0 + RESISTOR_TOLERANCE_FRACTION)) -
            NPN_VBE_MAX_VOLTS /
                    (RPD_OHMS * (1.0 - RESISTOR_TOLERANCE_FRACTION));

    /** Local fault vocabulary kept independent of generated challenge faults. */
    static final class FaultSpec extends ComposedBlockContribution.FaultSpec {
        FaultSpec(Kind kind, String targetComponentLocalId,
                double effectiveResistanceOhms) {
            super(kind, targetComponentLocalId, effectiveResistanceOhms);
        }
        static FaultSpec open(String componentLocalId) {
            return new FaultSpec(Kind.OPEN, componentLocalId,
                    ComposedBlockContribution.FAULT_RESISTANCE_OHMS);
        }
        static FaultSpec incorrectResistance(String componentLocalId,
                double effectiveResistanceOhms) {
            return new FaultSpec(Kind.INCORRECT_RESISTANCE,
                    componentLocalId, effectiveResistanceOhms);
        }
    }

    interface Provider extends LowSideRoleFamily.Provider {
        String getTypeId();
        int getVersion();
        String getRoleId();
        boolean isCompatible(LowSideRoleFamily.Envelope envelope);
        boolean isLowSide();
        double getSupplyMinimumVolts();
        double getSupplyMaximumVolts();
        double getControlHighMinimumVolts();
        double getControlLowMaximumVolts();
        double getControlDemandAmps();
        double getRequiredBaseDriveAmps();
        double getSinkCapacityAmps();
        double getLoadDemandAmps();
        double getOnSinkMaximumVolts();
        double getOffSinkMaximumVolts();
        double getOffLeakageMaximumAmps();
        String getModelId();
        ComposedBlockContribution create(String blockKey);
        ComposedBlockContribution create(String blockKey, FaultSpec fault);
    }

    private static final Provider DRIVER = new ProviderImpl(NmosDriverProfile.standard());
    private static final Provider NPN_DRIVER = new ProviderImpl(Kind.NPN_DRIVER);
    private static final Provider LOAD = new ProviderImpl(Kind.LOAD);

    private ControlledIndicatorBlockContributions() { }

    static Provider driver() { return DRIVER; }
    static Provider nmosDriver() { return DRIVER; }
    static Provider nmosDriver(NmosDriverProfile profile) {
        return createNmosDriver(profile);
    }
    static Provider createNmosDriver(NmosDriverProfile profile) {
        if (profile == null)
            throw new IllegalArgumentException("NMOS driver profile is required");
        return profile == NmosDriverProfile.standard() ? DRIVER : new ProviderImpl(profile);
    }
    static Provider npnDriver() { return NPN_DRIVER; }
    static Provider load() { return LOAD; }

    static ComposedBlockContribution createResolvedValueLoad(String blockKey,
            FaultSpec fault, ControlledIndicatorValueSynthesis.ResolvedRecipe recipe) {
        return ((ProviderImpl) LOAD).createResolved(blockKey, fault, recipe);
    }

    static ComposedBlockContribution createResolvedValueLoad(String blockKey,
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe) {
        return ((ProviderImpl) LOAD).createResolved(blockKey, null, recipe);
    }

    static Provider resolve(String typeId, int version) {
        return ConstructionProviderRegistry.standard().get(typeId, version)
            .requireControlledContribution();
    }

    static String componentId(BlockNamespace namespace, String blockKey,
            String localComponentId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(blockKey, EntityKind.COMPONENT, localComponentId);
    }

    static String padId(BlockNamespace namespace, String blockKey,
            String localPadId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(blockKey, EntityKind.PAD, localPadId);
    }

    /** The complete device identity authority, including its declared adapters. */
    static BlockNamespace namespace() {
        return BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(0L))
                .getNamespace();
    }

    static String chooseFaultDecision(long rootSeed) {
        NamedRandomStreams streams = new NamedRandomStreams(1, rootSeed,
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION);
        return NamedRandomStreams.select(streams.deviceSeed(
                NamedRandomStreams.Concern.FAULT, 1, "selected-fault"),
                Arrays.asList(DRIVER_FAULT_DECISION_KEY,
                        LOAD_FAULT_DECISION_KEY));
    }

    static FaultSpec faultForDecision(String decisionKey) {
        if (DRIVER_FAULT_DECISION_KEY.equals(decisionKey)) return FaultSpec.open("RG");
        if (LOAD_FAULT_DECISION_KEY.equals(decisionKey)) return FaultSpec.open("RLOAD");
        throw new IllegalArgumentException("Unknown controlled fault decision " + decisionKey);
    }

    private enum Kind { NMOS_DRIVER, NPN_DRIVER, LOAD }

    private static final class ProviderImpl implements Provider,
            LowSideRoleFamily.Provider {
        private final Kind kind;
        private final NmosDriverProfile nmosProfile;

        ProviderImpl(Kind kind) {
            if (kind == null || kind == Kind.NMOS_DRIVER)
                throw new IllegalArgumentException("NMOS providers require a profile");
            this.kind = kind;
            this.nmosProfile = null;
        }

        ProviderImpl(NmosDriverProfile profile) {
            if (profile == null)
                throw new IllegalArgumentException("NMOS driver profile is required");
            this.kind = Kind.NMOS_DRIVER;
            this.nmosProfile = profile;
        }

        private boolean isDriver() { return kind != Kind.LOAD; }
        private boolean isNpn() { return kind == Kind.NPN_DRIVER; }
        private boolean isNmos() { return kind == Kind.NMOS_DRIVER; }
        @Override public String getTypeId() {
            if (kind == Kind.NPN_DRIVER) return NPN_DRIVER_TYPE_ID;
            return isNmos() ? nmosProfile.getProviderId() : LOAD_TYPE_ID;
        }
        @Override public int getVersion() {
            if (kind == Kind.NPN_DRIVER) return NPN_VERSION;
            return isNmos() ? nmosProfile.getVersion() : LOAD_VERSION;
        }
        @Override public String getRoleId() {
            return isDriver() ? LowSideRoleFamily.ROLE_ID : "indicator-load";
        }
        @Override public boolean isCompatible(LowSideRoleFamily.Envelope envelope) {
            if (envelope == null || !isDriver() || !envelope.isLowSide()) return false;
            if (envelope.getSupplyMinimumVolts() < getSupplyMinimumVolts() ||
                    envelope.getSupplyMaximumVolts() > getSupplyMaximumVolts() ||
                    envelope.getControlHighMinimumVolts() < getControlHighMinimumVolts() ||
                    envelope.getControlLowMaximumVolts() > getControlLowMaximumVolts() ||
                    envelope.getControlCapacityAmps() < getControlDemandAmps() ||
                    envelope.getSinkCapacityAmps() < getSinkCapacityAmps() ||
                    envelope.getLoadDemandAmps() > getLoadDemandAmps()) return false;
            return !isNpn() || NPN_AVAILABLE_BASE_DRIVE_AMPS >=
                    NPN_REQUIRED_BASE_DRIVE_AMPS;
        }
        @Override public boolean isLowSide() { return isDriver(); }
        @Override public double getSupplyMinimumVolts() {
            return LowSideRoleFamily.SUPPLY_MINIMUM_VOLTS;
        }
        @Override public double getSupplyMaximumVolts() {
            return LowSideRoleFamily.SUPPLY_MAXIMUM_VOLTS;
        }
        @Override public double getControlHighMinimumVolts() {
            return LowSideRoleFamily.CONTROL_HIGH_MINIMUM_VOLTS;
        }
        @Override public double getControlLowMaximumVolts() {
            return LowSideRoleFamily.CONTROL_LOW_MAXIMUM_VOLTS;
        }
        @Override public double getControlDemandAmps() {
            if (!isDriver()) return 0.0;
            return isNpn() ? NPN_CONTROL_DEMAND_AMPS : nmosProfile.getControlDemandAmps();
        }
        @Override public double getRequiredBaseDriveAmps() {
            return isNpn() ? NPN_REQUIRED_BASE_DRIVE_AMPS : 0.0;
        }
        @Override public double getSinkCapacityAmps() {
            return isDriver() ? LowSideRoleFamily.SINK_CAPACITY_AMPS : 0.0;
        }
        @Override public double getLoadDemandAmps() {
            return isDriver() ? LowSideRoleFamily.LOAD_DEMAND_AMPS : 0.0;
        }
        @Override public double getOnSinkMaximumVolts() {
            return isDriver() ? LowSideRoleFamily.ON_SINK_MAXIMUM_VOLTS : 0.0;
        }
        @Override public double getOffSinkMaximumVolts() {
            return isDriver() ? LowSideRoleFamily.OFF_SINK_MAXIMUM_VOLTS : 0.0;
        }
        @Override public double getOffLeakageMaximumAmps() {
            return isDriver() ? LowSideRoleFamily.OFF_LEAKAGE_MAXIMUM_AMPS : 0.0;
        }
        @Override public String getModelId() {
            if (kind == Kind.NPN_DRIVER) return "NPN";
            if (isNmos()) return nmosProfile.getModelId();
            return ControlledIndicatorValueSynthesis.MODEL_ID;
        }

        @Override public ComposedBlockContribution create(String blockKey) {
            if (!isDriver()) return createIntent(blockKey, FaultSpec.open("RLOAD"));
            return create(blockKey, FaultSpec.open(isNpn() ? "RB" :
                nmosProfile.getControlResistorId()));
        }

        @Override public ComposedBlockContribution create(String blockKey,
                FaultSpec fault) {
            if (!isDriver())
                throw new IllegalArgumentException(
                        "Controlled load requires a resolved catalog recipe");
            FunctionalBlockDescriptor.requireId(blockKey, "driver.instanceKey");
            if (fault == null) throw new IllegalArgumentException("Fault is required");
            String expectedTarget = isNpn() ? "RB" : nmosProfile.getControlResistorId();
            if (!expectedTarget.equals(fault.getTargetComponentLocalId()))
                throw new IllegalArgumentException("Controlled fault target must be "
                        + expectedTarget);
            FunctionalBlockDescriptor descriptor = driverDescriptor(blockKey, isNpn(),
                nmosProfile);
            ElectricalBlockContract electrical = driverElectrical(descriptor, isNpn(),
                nmosProfile);
            TreeMap<String, ComposedBlockContribution.ResistorRecipe> resistors =
                    new TreeMap<String, ComposedBlockContribution.ResistorRecipe>();
            Collection<ComposedBlockContribution.NmosRecipe> nmos =
                    Collections.<ComposedBlockContribution.NmosRecipe>emptyList();
            Collection<ComposedBlockContribution.LedRecipe> leds =
                    Collections.<ComposedBlockContribution.LedRecipe>emptyList();
            if (isNpn()) {
                resistors.put("RB", new ComposedBlockContribution.ResistorRecipe(
                        "RB", "RB_1", "RB_2", "RB.1", "RB.2", RB_OHMS,
                        ComposedBlockContribution.RATED_WATTS,
                        PhysicalPackages.AXIAL_RESISTOR.getId(), true));
            } else {
                String controlResistor = nmosProfile.getControlResistorId();
                resistors.put(controlResistor, new ComposedBlockContribution.ResistorRecipe(
                        controlResistor, controlResistor + "_1", controlResistor + "_2",
                        controlResistor + ".1", controlResistor + ".2",
                        nmosProfile.getGateResistanceOhms(),
                        ComposedBlockContribution.RATED_WATTS,
                        PhysicalPackages.AXIAL_RESISTOR.getId(), true));
            }
            resistors.put("RPD", new ComposedBlockContribution.ResistorRecipe(
                    "RPD", "RPD_1", "RPD_2", "RPD.1", "RPD.2",
                    isNpn() ? RPD_OHMS : nmosProfile.getPullDownOhms(),
                    ComposedBlockContribution.RATED_WATTS,
                    PhysicalPackages.AXIAL_RESISTOR.getId(), false));
            if (!isNpn()) {
                nmos = Arrays.asList(new ComposedBlockContribution.NmosRecipe(
                        "Q1", "Q1_G", "Q1_D", "Q1_S", "Q1.G", "Q1.D",
                        "Q1.S", nmosProfile.getModelId()));
            }
            return new ComposedBlockContribution(getTypeId(), getVersion(), descriptor,
                    electrical, resistors, nmos, leds, fault, fault.getTargetComponentLocalId(),
                    Arrays.asList("BOARD_POWER", "CONTROL_INPUT"),
                    Arrays.asList("STEADY_DC_POWERED", "CUSTOMER_RETEST"));
        }

        ComposedBlockContribution createIntent(String blockKey, FaultSpec fault) {
            if (isDriver())
                throw new IllegalArgumentException("Only the controlled load has an unresolved intent");
            FunctionalBlockDescriptor.requireId(blockKey, "load.instanceKey");
            if (fault == null ||
                    !"RLOAD".equals(fault.getTargetComponentLocalId()))
                throw new IllegalArgumentException("Invalid unresolved controlled load");
            FunctionalBlockDescriptor descriptor = loadDescriptor(blockKey);
            ElectricalBlockContract electrical = loadElectrical(descriptor);
            Collection<ComposedBlockContribution.LedRecipe> leds = Arrays.asList(
                new ComposedBlockContribution.LedRecipe(
                    "LED1", "LED1_A", "LED1_K", "LED1.A", "LED1.K", "LED"));
            return new ComposedBlockContribution(getTypeId(), getVersion(),
                    descriptor, electrical,
                    Collections.<String, ComposedBlockContribution.ResistorRecipe>emptyMap(),
                    Collections.<ComposedBlockContribution.NmosRecipe>emptyList(), leds,
                    fault, fault.getTargetComponentLocalId(),
                    Arrays.asList("BOARD_POWER", "CONTROL_INPUT"),
                    Arrays.asList("STEADY_DC_POWERED", "CUSTOMER_RETEST"));
        }

        ComposedBlockContribution createResolved(String blockKey, FaultSpec fault,
                ControlledIndicatorValueSynthesis.ResolvedRecipe resolved) {
            if (isDriver() || resolved == null)
                throw new IllegalArgumentException("Only the controlled load has a resolved value");
            FunctionalBlockDescriptor.requireId(blockKey, "load.instanceKey");
            if (fault != null && !"RLOAD".equals(fault.getTargetComponentLocalId()))
                throw new IllegalArgumentException("Invalid resolved controlled load");
            FunctionalBlockDescriptor descriptor = loadDescriptor(blockKey);
            ElectricalBlockContract electrical = loadElectrical(descriptor);
            TreeMap<String, ComposedBlockContribution.ResistorRecipe> resistors =
                    new TreeMap<String, ComposedBlockContribution.ResistorRecipe>();
            resistors.put("RLOAD", new ComposedBlockContribution.ResistorRecipe(
                    "RLOAD", "RLOAD_1", "RLOAD_2", "RLOAD.1", "RLOAD.2",
                    resolved, true));
            Collection<ComposedBlockContribution.LedRecipe> leds = Arrays.asList(
                new ComposedBlockContribution.LedRecipe(
                    "LED1", "LED1_A", "LED1_K", "LED1.A", "LED1.K", "LED"));
            return new ComposedBlockContribution(getTypeId(), getVersion(),
                    descriptor, electrical, resistors,
                    Collections.<ComposedBlockContribution.NmosRecipe>emptyList(), leds,
                    fault, fault == null ? null : fault.getTargetComponentLocalId(),
                    Arrays.asList("BOARD_POWER", "CONTROL_INPUT"),
                    Arrays.asList("STEADY_DC_POWERED", "CUSTOMER_RETEST"), resolved);
        }
    }

    private static FunctionalBlockDescriptor driverDescriptor(String key,
            boolean npn, NmosDriverProfile nmosProfile) {
        if (!npn && nmosProfile == null)
            throw new IllegalArgumentException("NMOS driver profile is required");
        String typeId = npn ? NPN_DRIVER_TYPE_ID : nmosProfile.getProviderId();
        int version = npn ? NPN_VERSION : nmosProfile.getVersion();
        String resistor = npn ? "RB" : nmosProfile.getControlResistorId();
        String resistorFirst = resistor + "_1";
        String resistorSecond = resistor + "_2";
        String resistorPadFirst = resistor + ".1";
        String resistorPadSecond = resistor + ".2";
        String controlNode = npn ? "BASE" : nmosProfile.getControlNodeId();
        String transistorType = npn ? "NPN" : nmosProfile.getPrimitiveType();
        String transistorFirst = npn ? "B" : nmosProfile.getControlTerminal();
        String transistorSecond = npn ? "C" : nmosProfile.getSwitchedTerminal();
        String transistorThird = npn ? "E" : nmosProfile.getReturnTerminal();
        String transistorEndpointFirst = "Q1_" + transistorFirst;
        String transistorEndpointSecond = "Q1_" + transistorSecond;
        String transistorEndpointThird = "Q1_" + transistorThird;
        String transistorPadFirst = "Q1." + transistorFirst;
        String transistorPadSecond = "Q1." + transistorSecond;
        String transistorPadThird = "Q1." + transistorThird;
        ArrayList<FunctionalBlockDescriptor.Parameter> parameters =
                new ArrayList<FunctionalBlockDescriptor.Parameter>();
        parameters.add(parameter("functional-role", LowSideRoleFamily.ROLE_ID));
        parameters.add(parameter(npn ? "base-resistance-ohms" :
                "gate-resistance-ohms", npn ? RB_OHMS :
                nmosProfile.getGateResistanceOhms()));
        parameters.add(parameter("pull-down-ohms", npn ? RPD_OHMS :
                nmosProfile.getPullDownOhms()));
        parameters.add(parameter("model", npn ? transistorType : nmosProfile.getModelId()));
        if (npn)
            parameters.add(parameter("beta", 100));
        else if (nmosProfile == NmosDriverProfile.standard())
            parameters.add(parameter("beta", 10));
        else
            parameters.add(parameter("beta", nmosProfile.getBeta()));
        if (npn) {
            parameters.add(parameter("forced-beta", NPN_FORCED_BETA));
            parameters.add(parameter("vbe-min-volts", NPN_VBE_MIN_VOLTS));
            parameters.add(parameter("vbe-max-volts", NPN_VBE_MAX_VOLTS));
            parameters.add(parameter("required-base-drive-amps",
                    NPN_REQUIRED_BASE_DRIVE_AMPS));
            parameters.add(parameter("available-base-drive-amps",
                    NPN_AVAILABLE_BASE_DRIVE_AMPS));
        } else {
            parameters.add(parameter("threshold-volts", nmosProfile.getThresholdVolts()));
        }
        parameters.add(parameter("control-demand-amps",
                npn ? NPN_CONTROL_DEMAND_AMPS : nmosProfile.getControlDemandAmps()));
        parameters.add(parameter("sink-capacity-amps",
                LowSideRoleFamily.SINK_CAPACITY_AMPS));
        parameters.add(parameter("load-demand-amps",
                LowSideRoleFamily.LOAD_DEMAND_AMPS));
        parameters.add(parameter("on-clamp-max-volts",
                LowSideRoleFamily.ON_SINK_MAXIMUM_VOLTS));
        parameters.add(parameter("off-leakage-max-amps",
                LowSideRoleFamily.OFF_LEAKAGE_MAXIMUM_AMPS));
        return new FunctionalBlockDescriptor(typeId, version, key, parameters,
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component(resistor, "RESISTOR",
                                Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component("RPD", "RESISTOR",
                                Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component("Q1", transistorType,
                                Arrays.asList(transistorFirst, transistorSecond,
                                        transistorThird))),
                Arrays.asList("CONTROL", controlNode, "SWITCHED_SINK", "RETURN"),
                Arrays.asList(
                        endpoint(resistorFirst, resistor, "1"),
                        endpoint(resistorSecond, resistor, "2"),
                        endpoint("RPD_1", "RPD", "1"),
                        endpoint("RPD_2", "RPD", "2"),
                        endpoint(transistorEndpointFirst, "Q1", transistorFirst),
                        endpoint(transistorEndpointSecond, "Q1", transistorSecond),
                        endpoint(transistorEndpointThird, "Q1", transistorThird)),
                Arrays.asList(
                        pad(resistorPadFirst, resistorFirst, "CONTROL"),
                        pad(resistorPadSecond, resistorSecond, controlNode),
                        pad("RPD.1", "RPD_1", controlNode),
                        pad("RPD.2", "RPD_2", "RETURN"),
                        pad(transistorPadFirst, transistorEndpointFirst, controlNode),
                        pad(transistorPadSecond, transistorEndpointSecond, "SWITCHED_SINK"),
                        pad(transistorPadThird, transistorEndpointThird, "RETURN")),
                Arrays.asList(
                        role("control", "CONTROL", resistorPadFirst),
                        new FunctionalBlockDescriptor.Role(npn ? "base" : "gate",
                                Requirement.REQUIRED, Arrays.asList(
                                        ref(EntityKind.NET, controlNode),
                                        ref(EntityKind.PAD, resistorPadSecond),
                                        ref(EntityKind.PAD, "RPD.1"),
                                        ref(EntityKind.PAD, transistorPadFirst))),
                        role("switched", "SWITCHED_SINK", transistorPadSecond),
                        new FunctionalBlockDescriptor.Role("return", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "RETURN"),
                                        ref(EntityKind.PAD, "RPD.2"),
                                        ref(EntityKind.PAD, transistorPadThird)))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port("CONTROL", "control",
                                ref(EntityKind.PAD, resistorPadFirst)),
                        new FunctionalBlockDescriptor.Port("SWITCHED_SINK", "switched",
                                ref(EntityKind.PAD, transistorPadSecond)),
                        new FunctionalBlockDescriptor.Port("RETURN", "return",
                                ref(EntityKind.NET, "RETURN"))));
    }

    private static FunctionalBlockDescriptor loadDescriptor(String key) {
        // The provider declares local policy only. Supply/sink facts are read
        // from the actual request when the device resolves its recipe.
        List<FunctionalBlockDescriptor.Parameter> parameters = Arrays.asList(
                parameter("functional-role", "indicator-load"),
                parameter("policy", ControlledIndicatorValueSynthesis.POLICY_ID),
                parameter("target-min-current-amps",
                        ControlledIndicatorValueSynthesis.TARGET_MINIMUM_CURRENT_AMPS),
                parameter("led-vf-min-volts", ControlledIndicatorValueSynthesis.LED_MINIMUM_FORWARD_VOLTS),
                parameter("led-vf-max-volts", ControlledIndicatorValueSynthesis.LED_MAXIMUM_FORWARD_VOLTS),
                parameter("tolerance-fraction", ControlledIndicatorValueSynthesis.REQUIRED_TOLERANCE_FRACTION),
                parameter("power-headroom-factor", ControlledIndicatorValueSynthesis.POWER_HEADROOM_FACTOR),
                parameter("sink-headroom-factor", ControlledIndicatorValueSynthesis.SINK_HEADROOM_FACTOR),
                parameter("model", ControlledIndicatorValueSynthesis.MODEL_ID),
                parameter("package", ControlledIndicatorValueSynthesis.PACKAGE_ID));
        return new FunctionalBlockDescriptor(LOAD_TYPE_ID, LOAD_VERSION, key,
                parameters,
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component("RLOAD", "RESISTOR", Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component("LED1", "LED", Arrays.asList("A", "K"))),
                Arrays.asList("SUPPLY", "LED_NODE", "SWITCHED_LOAD", "RETURN"),
                Arrays.asList(endpoint("RLOAD_1", "RLOAD", "1"), endpoint("RLOAD_2", "RLOAD", "2"),
                        endpoint("LED1_A", "LED1", "A"), endpoint("LED1_K", "LED1", "K")),
                Arrays.asList(pad("RLOAD.1", "RLOAD_1", "SUPPLY"), pad("RLOAD.2", "RLOAD_2", "LED_NODE"),
                        pad("LED1.A", "LED1_A", "LED_NODE"), pad("LED1.K", "LED1_K", "SWITCHED_LOAD")),
                Arrays.asList(
                        role("supply", "SUPPLY", "RLOAD.1"),
                        new FunctionalBlockDescriptor.Role("ledNode", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "LED_NODE"), ref(EntityKind.PAD, "RLOAD.2"),
                                        ref(EntityKind.PAD, "LED1.A"))),
                        role("switched", "SWITCHED_LOAD", "LED1.K"),
                        new FunctionalBlockDescriptor.Role("return", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "RETURN")))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port("SUPPLY", "supply", ref(EntityKind.PAD, "RLOAD.1")),
                        new FunctionalBlockDescriptor.Port("SWITCHED_LOAD", "switched", ref(EntityKind.PAD, "LED1.K")),
                        new FunctionalBlockDescriptor.Port("RETURN", "return", ref(EntityKind.NET, "RETURN"))));
    }

    private static ElectricalBlockContract driverElectrical(
            FunctionalBlockDescriptor descriptor, boolean npn,
            NmosDriverProfile nmosProfile) {
        if (!npn && nmosProfile == null)
            throw new IllegalArgumentException("NMOS driver profile is required");
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract control = new ElectricalPortContract("CONTROL", Role.CONTROL,
                Direction.INPUT, Behavior.SINK, Drive.NONE, domain, Scalar.known(5.0),
                Range.known(0.0, 5.0), Range.known(0.0, 5.0), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(npn ? NPN_CONTROL_DEMAND_AMPS :
                        nmosProfile.getControlDemandAmps()),
                new ElectricalPortContract.Digital(ActiveLevel.HIGH,
                        Scalar.known(LowSideRoleFamily.CONTROL_LOW_MAXIMUM_VOLTS),
                        Scalar.known(LowSideRoleFamily.CONTROL_HIGH_MINIMUM_VOLTS),
                        Scalar.known(LowSideRoleFamily.CONTROL_LOW_MAXIMUM_VOLTS),
                        Scalar.known(LowSideRoleFamily.CONTROL_HIGH_MINIMUM_VOLTS)),
                MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        ElectricalPortContract switched = new ElectricalPortContract("SWITCHED_SINK", Role.LOAD,
                Direction.OUTPUT, Behavior.SINK, Drive.OPEN_DRAIN, domain, Scalar.known(0.0),
                Range.known(0.0, 0.8), Range.known(0.0, 5.5), Loading.NONE,
                Scalar.known(0.020), Scalar.notApplicable(),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        return contract(descriptor, Arrays.asList(control, switched, returned(domain)));
    }

    private static ElectricalBlockContract loadElectrical(FunctionalBlockDescriptor descriptor) {
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract supply = new ElectricalPortContract("SUPPLY", Role.LOAD,
                Direction.INPUT, Behavior.SINK, Drive.NONE, domain, Scalar.known(5.0),
                Range.known(4.5, 5.5), Range.known(4.5, 5.5), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(0.016),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        ElectricalPortContract switched = new ElectricalPortContract("SWITCHED_LOAD", Role.LOAD,
                Direction.INPUT, Behavior.SINK, Drive.NONE, domain, Scalar.known(0.0),
                Range.known(0.0, 5.5), Range.known(0.0, 5.5), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(0.016),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        return contract(descriptor, Arrays.asList(supply, switched, returned(domain)));
    }

    private static ElectricalBlockContract contract(FunctionalBlockDescriptor descriptor,
            List<ElectricalPortContract> ports) {
        return new ElectricalBlockContract(descriptor, ports,
                Collections.<ElectricalBlockContract.Adapter>emptyList());
    }
    private static ElectricalPortContract returned(Domain domain) {
        return new ElectricalPortContract("RETURN", Role.RETURN, Direction.BIDIRECTIONAL,
                Behavior.PASSIVE, Drive.NONE, domain, Scalar.notApplicable(),
                Range.notApplicable(), Range.notApplicable(), Loading.NONE,
                Scalar.notApplicable(), Scalar.notApplicable(),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
    }
    private static FunctionalBlockDescriptor.Parameter parameter(String id, int value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofInteger(value));
    }
    private static FunctionalBlockDescriptor.Parameter parameter(String id, double value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofDecimal(value));
    }
    private static FunctionalBlockDescriptor.Parameter parameter(String id, String value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofText(value));
    }
    private static FunctionalBlockDescriptor.Endpoint endpoint(String id, String component, String terminal) {
        return new FunctionalBlockDescriptor.Endpoint(id, component, terminal);
    }
    private static FunctionalBlockDescriptor.Pad pad(String id, String endpoint, String net) {
        return new FunctionalBlockDescriptor.Pad(id, endpoint, net);
    }
    private static FunctionalBlockDescriptor.Role role(String id, String net, String pad) {
        return new FunctionalBlockDescriptor.Role(id, Requirement.REQUIRED,
                Arrays.asList(ref(EntityKind.NET, net), ref(EntityKind.PAD, pad)));
    }
    private static LocalRef ref(EntityKind kind, String id) {
        return new LocalRef(kind, id);
    }
}
