package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/** Provider-owned live observation of one low-side driver implementation. */
interface ControlledIndicatorDriverObservation {
    String getProviderId();

    boolean isHealthyOn(ControlledIndicatorChannelObservation context);
    boolean isHealthyOff(ControlledIndicatorChannelObservation context);
    boolean isFaultedOn(ControlledIndicatorChannelObservation context);
    boolean isDriverEnergizedOn(ControlledIndicatorChannelObservation context);
}

/**
 * Runtime view of one resolved channel.  It contains only current solver and
 * physical bindings; the provider receives no mutable board owner during pure
 * contribution resolution.
 */
final class ControlledIndicatorChannelObservation {
    private final GeneratedBoardInstance instance;
    private final BoundedAssemblyPlan plan;
    private final ControlledIndicatorChannel channel;

    ControlledIndicatorChannelObservation(GeneratedBoardInstance instance,
            BoundedAssemblyPlan plan, ControlledIndicatorChannel channel) {
        if (instance == null || plan == null || channel == null)
            throw new IllegalArgumentException("Incomplete channel observation context");
        this.instance = instance;
        this.plan = plan;
        this.channel = channel;
    }

    GeneratedBoardInstance getInstance() { return instance; }
    BoundedAssemblyPlan getPlan() { return plan; }
    ControlledIndicatorChannel getChannel() { return channel; }

    CircuitElm component(String ownerKey, String localId) {
        String id = plan.idFor(ownerKey, EntityKind.COMPONENT, localId);
        return instance.getComponentBindings().getSingleElement(id);
    }

    ResistorElm resistor(String ownerKey, String localId) {
        CircuitElm element = component(ownerKey, localId);
        if (!(element instanceof ResistorElm))
            throw new IllegalStateException("Expected resistor " + ownerKey + "/" + localId);
        return (ResistorElm) element;
    }

    LEDElm led(String ownerKey, String localId) {
        CircuitElm element = component(ownerKey, localId);
        if (!(element instanceof LEDElm))
            throw new IllegalStateException("Expected LED " + ownerKey + "/" + localId);
        return (LEDElm) element;
    }

    double voltage(String ownerKey, String localPadId) {
        String padId = plan.idFor(ownerKey, EntityKind.PAD, localPadId);
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing solver endpoint " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getPostVoltage(post.getPostIndex());
    }

    double adapterVoltage(DeviceAdapterContract adapter) {
        if (adapter == null)
            throw new IllegalArgumentException("Adapter is required");
        String first = null;
        String second = null;
        for (String localPad : adapter.getDescriptor().getPads().keySet()) {
            if (localPad.endsWith(".1")) first = localPad;
            if (localPad.endsWith(".2")) second = localPad;
        }
        if (first == null || second == null)
            throw new IllegalStateException("Adapter has no positive/return pads: " +
                adapter.getKey());
        return voltage(adapter.getKey(), first) - voltage(adapter.getKey(), second);
    }

    double powerVoltage() {
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters())
            if (DeviceAdapterContract.POWER_ADAPTER_KEY.equals(adapter.getKey()))
                return adapterVoltage(adapter);
        throw new IllegalStateException("Controlled plan has no power adapter");
    }

    double controlVoltage() {
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters())
            if (adapter.getKey().equals(channel.getControlAdapterKey()))
                return adapterVoltage(adapter);
        throw new IllegalStateException("Controlled plan has no channel control adapter " +
            channel.getKey());
    }

    double loadCurrent() {
        return Math.abs(resistor(channel.getLoadKey(), "RLOAD").getCurrent());
    }

    double ledCurrent() {
        return Math.abs(led(channel.getLoadKey(), "LED1").getCurrent());
    }

    double loadResistance() {
        return resistor(channel.getLoadKey(), "RLOAD").getResistance();
    }

    double loadRatedWatts() {
        String componentId = plan.idFor(channel.getLoadKey(), EntityKind.COMPONENT, "RLOAD");
        PhysicalPart<?> installed = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        if (installed instanceof PhysicalResistorPart)
            return ((PhysicalResistorPart) installed).getNameplate().getRatedWattage();
        ComposedBlockContribution load = plan.getBlocks().get(channel.getLoadKey());
        if (load != null && load.getResistor("RLOAD") != null)
            return load.getResistor("RLOAD").getRatedWatts();
        return ComposedBlockContribution.RATED_WATTS;
    }

    double supportCurrent() {
        String support = plan.getSupportBlockKey();
        return Math.abs(resistor(support, SupplyPresentBlockContributions.RSUP_COMPONENT_ID)
            .getCurrent());
    }

    double supportLedCurrent() {
        String support = plan.getSupportBlockKey();
        return Math.abs(led(support, SupplyPresentBlockContributions.LED_COMPONENT_ID)
            .getCurrent());
    }

    static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

/** Explicit registry: provider IDs select implementation classes. */
final class ControlledIndicatorDriverObservations {
    private static final ControlledIndicatorDriverObservation NMOS =
        new NmosControlledIndicatorDriverObservation();
    private static final ControlledIndicatorDriverObservation NPN =
        new NpnControlledIndicatorDriverObservation();

    private ControlledIndicatorDriverObservations() { }

    static ControlledIndicatorDriverObservation forProvider(String providerId) {
        if (ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(providerId))
            return NMOS;
        if (ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID.equals(providerId))
            return NPN;
        throw new IllegalArgumentException("Unsupported controlled driver provider " + providerId);
    }
}

/** Actual NMOS low-side observation rules. */
final class NmosControlledIndicatorDriverObservation
        implements ControlledIndicatorDriverObservation {
    public String getProviderId() {
        return ControlledIndicatorBlockContributions.DRIVER_TYPE_ID;
    }

    public boolean isHealthyOn(ControlledIndicatorChannelObservation context) {
        NMosfetElm q = nmos(context);
        double load = context.loadCurrent();
        double led = context.ledCurrent();
        double mosfet = Math.abs(q.getCurrent());
        double supply = context.powerVoltage();
        double control = context.controlVoltage();
        double vgs = q.getPostVoltage(0) - q.getPostVoltage(1);
        double vds = q.getPostVoltage(2) - q.getPostVoltage(1);
        double loadResistance = context.loadResistance();
        double ratedWatts = context.loadRatedWatts();
        double minimumLoad = loadMinimum(context);
        double maximumLoad = loadMaximum(context);
        context.require(ControlledIndicatorChannelObservation.finite(load) &&
                ControlledIndicatorChannelObservation.finite(led) &&
                ControlledIndicatorChannelObservation.finite(mosfet) &&
                ControlledIndicatorChannelObservation.finite(supply),
            "NMOS channel has non-finite solver observation");
        ControlledIndicatorBlockContributions.Provider provider = provider(context);
        return supply >= provider.getSupplyMinimumVolts() &&
            supply <= provider.getSupplyMaximumVolts() &&
            control >= provider.getControlHighMinimumVolts() &&
            load >= minimumLoad && load <= maximumLoad && led >= .005 &&
            Math.abs(load - led) < .0005 && Math.abs(load - mosfet) < .002 &&
            vgs > 3.0 && vds >= 0.0 && vds <= provider.getOnSinkMaximumVolts() &&
            Math.abs(q.getCurrentIntoNode(0)) < 1.0e-9 &&
            loadResistance > 0.0 && ratedWatts > 0.0 &&
            load * load * loadResistance <= ratedWatts + 1.0e-9;
    }

    public boolean isHealthyOff(ControlledIndicatorChannelObservation context) {
        NMosfetElm q = nmos(context);
        ControlledIndicatorBlockContributions.Provider provider = provider(context);
        double supply = context.powerVoltage();
        double control = context.controlVoltage();
        return ControlledIndicatorChannelObservation.finite(supply) &&
            ControlledIndicatorChannelObservation.finite(control) &&
            supply >= provider.getSupplyMinimumVolts() &&
            supply <= provider.getSupplyMaximumVolts() &&
            control <= provider.getControlLowMaximumVolts() &&
            Math.abs(context.loadCurrent()) < provider.getOffLeakageMaximumAmps() &&
            q.getPostVoltage(0) - q.getPostVoltage(1) < .1 &&
            q.getPostVoltage(2) - q.getPostVoltage(1) > supply - 1.0 &&
            Math.abs(q.getCurrentIntoNode(0)) < 1.0e-9;
    }

    public boolean isFaultedOn(ControlledIndicatorChannelObservation context) {
        NMosfetElm q = nmos(context);
        return context.loadCurrent() < 1.0e-6 && context.ledCurrent() < 1.0e-6 &&
            q.getPostVoltage(0) - q.getPostVoltage(1) < .1 &&
            Math.abs(context.resistor(context.getChannel().getDriverKey(), "RG").getCurrent()) < 1.0e-6;
    }

    public boolean isDriverEnergizedOn(ControlledIndicatorChannelObservation context) {
        NMosfetElm q = nmos(context);
        return q.getPostVoltage(0) - q.getPostVoltage(1) > 3.0 &&
            Math.abs(q.getCurrentIntoNode(0)) < 1.0e-9;
    }

    private static NMosfetElm nmos(ControlledIndicatorChannelObservation context) {
        CircuitElm element = context.component(context.getChannel().getDriverKey(), "Q1");
        if (!(element instanceof NMosfetElm))
            throw new IllegalStateException("Selected driver is not an NMOS");
        return (NMosfetElm) element;
    }

    private static double loadMinimum(ControlledIndicatorChannelObservation context) {
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe = loadRecipe(context);
        return recipe.getIntent().getTargetMinimumCurrentAmps();
    }

    private static double loadMaximum(ControlledIndicatorChannelObservation context) {
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe = loadRecipe(context);
        return recipe.getIntent().getTypedDemandAmps();
    }

    private static ControlledIndicatorValueSynthesis.ResolvedRecipe loadRecipe(
            ControlledIndicatorChannelObservation context) {
        ComposedBlockContribution load = context.getPlan().getBlocks()
            .get(context.getChannel().getLoadKey());
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe = load == null ? null :
            load.getResolvedValueRecipe();
        if (recipe == null)
            throw new IllegalStateException("Controlled channel has no resolved load recipe: " +
                context.getChannel().getKey());
        return recipe;
    }

    private static ControlledIndicatorBlockContributions.Provider provider(
            ControlledIndicatorChannelObservation context) {
        ComposedBlockContribution driver = context.getPlan().getBlocks()
            .get(context.getChannel().getDriverKey());
        if (driver == null)
            throw new IllegalStateException("Controlled channel has no driver contribution: " +
                context.getChannel().getKey());
        return ControlledIndicatorBlockContributions.resolve(driver.getProviderTypeId(),
            driver.getProviderVersion());
    }
}

/** Actual NPN low-side observation rules. */
final class NpnControlledIndicatorDriverObservation
        implements ControlledIndicatorDriverObservation {
    public String getProviderId() {
        return ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID;
    }

    public boolean isHealthyOn(ControlledIndicatorChannelObservation context) {
        NTransistorElm q = npn(context);
        double load = context.loadCurrent();
        double resistor = Math.abs(context.resistor(context.getChannel().getLoadKey(),
            "RLOAD").getCurrent());
        double base = Math.abs(q.ib);
        double collector = Math.abs(q.ic);
        double led = context.ledCurrent();
        double supply = context.powerVoltage();
        double control = context.controlVoltage();
        double vbe = q.getPostVoltage(0) - q.getPostVoltage(2);
        double vce = q.getPostVoltage(1) - q.getPostVoltage(2);
        ResistorElm baseResistor = context.resistor(context.getChannel().getDriverKey(), "RB");
        ResistorElm pullDown = context.resistor(context.getChannel().getDriverKey(), "RPD");
        double source = Math.abs(baseResistor.getCurrent());
        double pullDownCurrent = Math.abs(pullDown.getCurrent());
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe = loadRecipe(context);
        ControlledIndicatorValueSynthesis.Intent intent = recipe.getIntent();
        ControlledIndicatorBlockContributions.Provider provider = provider(context);
        double requiredBase = provider.getRequiredBaseDriveAmps();
        double sourceLimit = Math.min(.002, provider.getControlDemandAmps());
        double ratedWatts = context.loadRatedWatts();
        double loadResistance = context.loadResistance();
        double vbeMin = parameter(context, "vbe-min-volts");
        double vbeMax = parameter(context, "vbe-max-volts");
        return finite(load) && finite(resistor) && finite(base) && finite(collector) &&
            finite(led) && finite(supply) && finite(control) && finite(vbe) &&
            finite(vce) && finite(source) && finite(pullDownCurrent) &&
            supply >= provider.getSupplyMinimumVolts() &&
            supply <= provider.getSupplyMaximumVolts() &&
            control >= provider.getControlHighMinimumVolts() &&
            load >= intent.getTargetMinimumCurrentAmps() &&
            load <= intent.getTypedDemandAmps() &&
            resistor >= intent.getTargetMinimumCurrentAmps() &&
            resistor <= intent.getTypedDemandAmps() &&
            base >= requiredBase && collector >= intent.getTargetMinimumCurrentAmps() &&
            led >= intent.getTargetMinimumCurrentAmps() &&
            Math.abs(load - resistor) < .0005 && Math.abs(load - led) < .0005 &&
            Math.abs(load - collector) < .002 &&
            vbe >= vbeMin && vbe <= vbeMax && vce >= 0.0 &&
            vce <= provider.getOnSinkMaximumVolts() &&
            source <= sourceLimit && base + pullDownCurrent <= .002 &&
            Math.abs(source - base - pullDownCurrent) < .0002 &&
            loadResistance > 0.0 && ratedWatts > 0.0 &&
            load * load * loadResistance <= ratedWatts + 1.0e-9;
    }

    public boolean isHealthyOff(ControlledIndicatorChannelObservation context) {
        NTransistorElm q = npn(context);
        ControlledIndicatorBlockContributions.Provider provider = provider(context);
        double supply = context.powerVoltage();
        double control = context.controlVoltage();
        double load = context.loadCurrent();
        return finite(supply) && finite(control) && finite(load) &&
            supply >= provider.getSupplyMinimumVolts() &&
            supply <= provider.getSupplyMaximumVolts() &&
            control <= provider.getControlLowMaximumVolts() &&
            Math.abs(load) < provider.getOffLeakageMaximumAmps() &&
            Math.abs(q.ib) < provider.getOffLeakageMaximumAmps();
    }

    public boolean isFaultedOn(ControlledIndicatorChannelObservation context) {
        NTransistorElm q = npn(context);
        return context.loadCurrent() < .000001 && context.ledCurrent() < .000001 &&
            Math.abs(q.ib) < .000001 &&
            Math.abs(context.resistor(context.getChannel().getDriverKey(), "RB").getCurrent()) < .000001;
    }

    public boolean isDriverEnergizedOn(ControlledIndicatorChannelObservation context) {
        NTransistorElm q = npn(context);
        return q.getPostVoltage(0) - q.getPostVoltage(2) >=
            parameter(context, "vbe-min-volts") &&
            Math.abs(q.ib) >= provider(context).getRequiredBaseDriveAmps();
    }

    private static NTransistorElm npn(ControlledIndicatorChannelObservation context) {
        CircuitElm element = context.component(context.getChannel().getDriverKey(), "Q1");
        if (!(element instanceof NTransistorElm))
            throw new IllegalStateException("Selected driver is not an NPN");
        return (NTransistorElm) element;
    }

    private static ControlledIndicatorValueSynthesis.ResolvedRecipe loadRecipe(
            ControlledIndicatorChannelObservation context) {
        ComposedBlockContribution load = context.getPlan().getBlocks()
            .get(context.getChannel().getLoadKey());
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe = load == null ? null :
            load.getResolvedValueRecipe();
        if (recipe == null)
            throw new IllegalStateException("Controlled channel has no resolved load recipe: " +
                context.getChannel().getKey());
        return recipe;
    }

    private static ControlledIndicatorBlockContributions.Provider provider(
            ControlledIndicatorChannelObservation context) {
        ComposedBlockContribution driver = context.getPlan().getBlocks()
            .get(context.getChannel().getDriverKey());
        if (driver == null)
            throw new IllegalStateException("Controlled channel has no driver contribution: " +
                context.getChannel().getKey());
        return ControlledIndicatorBlockContributions.resolve(driver.getProviderTypeId(),
            driver.getProviderVersion());
    }

    private static double parameter(ControlledIndicatorChannelObservation context,
            String id) {
        ComposedBlockContribution driver = context.getPlan().getBlocks()
            .get(context.getChannel().getDriverKey());
        FunctionalBlockDescriptor.Parameter parameter = driver.getDescriptor()
            .getParameters().get(id);
        if (parameter == null)
            throw new IllegalStateException("Controlled driver omitted parameter " + id);
        FunctionalBlockDescriptor.Value value = parameter.getValue();
        if (value.getKind() == FunctionalBlockDescriptor.Value.Kind.DECIMAL)
            return value.getDecimal();
        if (value.getKind() == FunctionalBlockDescriptor.Value.Kind.INTEGER)
            return value.getInteger();
        throw new IllegalStateException("Controlled driver parameter is not numeric " + id);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
