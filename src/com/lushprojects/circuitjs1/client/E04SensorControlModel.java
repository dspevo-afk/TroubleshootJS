package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Bounded solver-backed sensor/control foundation for E04.
 *
 * <p>The original constructor deliberately consumes the declaration at the
 * E04 boundary, rather than naming a regulator implementation.  When a
 * selected E02 {@code RailRegulationContract} is available, the bounded
 * {@link #fromE02Rail(RailRegulationContract, Variant)} adapter is also
 * available.  That path keeps the same generic {@link RailContract} view but
 * installs the selected E02 regulator and finite input/enable sources in the
 * live solver graph.  E04 does not create a generic rail registry.</p>
 *
 * <p>The fixture contains real CircuitJS sources, resistors and a nonlinear
 * decision element.  The source impedances, divider, feedback path and load
 * are therefore part of the solved graph.  The semantic control state is
 * read from solved nodes by {@link DecisionElement}; it is not a render or
 * scenario flag.</p>
 */
public final class E04SensorControlModel {
    /** Two compatible realizations of one bounded sensor/control role. */
    public enum Variant {
        DIRECT_THRESHOLD,
        HYSTERETIC_REGENERATIVE
    }

    /** Player-facing stimulus vocabulary. */
    public enum SensorCondition {
        SENSOR_LOW,
        SENSOR_MID,
        SENSOR_HIGH,
        SENSOR_UNSUPPORTED
    }

    /** Solver-derived output state, including explicit unavailable states. */
    public enum ControlState {
        UNSETTLED,
        LOW,
        HIGH,
        BROWNOUT,
        REFERENCE_LOST,
        UNSUPPORTED
    }

    /**
     * Declarative rail view consumed by the sensor fixture.
     *
     * <p>It is intentionally narrower than a regulator provider: E04 needs
     * the selected output contract and does not own input-side regulation.
     * A future E02 adapter can preserve the E02 provider's identity and
     * diagnostics while exposing this view.</p>
     */
    public interface RailContract {
        String getRailId();
        String getReferenceId();
        double getNominalVoltage();
        double getMinimumOperatingVoltage();
        double getSourceResistanceOhms();
        boolean hasReference();
    }

    /** Small immutable declaration useful to isolated fixtures and tests. */
    public static final class RailDeclaration implements RailContract {
        private final String railId;
        private final String referenceId;
        private final double nominalVoltage;
        private final double minimumOperatingVoltage;
        private final double sourceResistanceOhms;
        private final boolean hasReference;

        public RailDeclaration(String railId, String referenceId,
                double nominalVoltage, double minimumOperatingVoltage,
                double sourceResistanceOhms, boolean hasReference) {
            requireId(railId, "rail id");
            requireId(referenceId, "reference id");
            requireFinitePositive(nominalVoltage, "nominal rail voltage");
            if (nominalVoltage > 24.0)
                throw new IllegalArgumentException("E04 rail is outside the 0-24 V fixture envelope");
            if (!finite(minimumOperatingVoltage) || minimumOperatingVoltage <= 0.0 ||
                    minimumOperatingVoltage > nominalVoltage)
                throw new IllegalArgumentException("Invalid E04 brownout minimum");
            requireFinitePositive(sourceResistanceOhms, "rail source resistance");
            if (sourceResistanceOhms > 1000.0)
                throw new IllegalArgumentException("E04 rail source resistance is outside its bounded envelope");
            this.railId = railId;
            this.referenceId = referenceId;
            this.nominalVoltage = nominalVoltage;
            this.minimumOperatingVoltage = minimumOperatingVoltage;
            this.sourceResistanceOhms = sourceResistanceOhms;
            this.hasReference = hasReference;
        }

        /** Representative low-voltage E04 rail. */
        public static RailDeclaration fiveVolt() {
            return new RailDeclaration("CONTROL_5V", "CONTROL_RETURN", 5.0,
                    3.8, 1.0, true);
        }

        public String getRailId() { return railId; }
        public String getReferenceId() { return referenceId; }
        public double getNominalVoltage() { return nominalVoltage; }
        public double getMinimumOperatingVoltage() { return minimumOperatingVoltage; }
        public double getSourceResistanceOhms() { return sourceResistanceOhms; }
        public boolean hasReference() { return hasReference; }
    }

    /**
     * Bounded electrical values shared by both control variants.
     * Thresholds are offsets from the solved reference node; they are not
     * hidden absolute answers.
     */
    public static final class Configuration {
        public final double sensorSourceResistanceOhms;
        public final double sensorLoadOhms;
        public final double referenceHighOhms;
        public final double referenceLowOhms;
        public final double directThresholdOffsetVolts;
        public final double risingThresholdOffsetVolts;
        public final double fallingThresholdOffsetVolts;
        public final double sensorLowVolts;
        public final double sensorHighVolts;

        public Configuration(double sensorSourceResistanceOhms,
                double sensorLoadOhms, double referenceHighOhms,
                double referenceLowOhms, double directThresholdOffsetVolts,
                double risingThresholdOffsetVolts,
                double fallingThresholdOffsetVolts, double sensorLowVolts,
                double sensorHighVolts) {
            requireFinitePositive(sensorSourceResistanceOhms,
                    "sensor source resistance");
            requireFinitePositive(sensorLoadOhms, "sensor load");
            requireFinitePositive(referenceHighOhms, "reference high resistance");
            requireFinitePositive(referenceLowOhms, "reference low resistance");
            requireFinite(directThresholdOffsetVolts,
                    "direct threshold offset");
            requireFinite(risingThresholdOffsetVolts,
                    "rising threshold offset");
            requireFinite(fallingThresholdOffsetVolts,
                    "falling threshold offset");
            if (risingThresholdOffsetVolts <= fallingThresholdOffsetVolts)
                throw new IllegalArgumentException(
                    "Hysteretic rising threshold must exceed falling threshold");
            requireFinite(sensorLowVolts, "sensor low condition");
            requireFinite(sensorHighVolts, "sensor high condition");
            if (sensorLowVolts < 0.0 || sensorHighVolts <= sensorLowVolts)
                throw new IllegalArgumentException("Invalid sensor condition range");
            if (sensorSourceResistanceOhms > 1e6 || sensorLoadOhms > 1e9 ||
                    referenceHighOhms > 1e9 || referenceLowOhms > 1e9)
                throw new IllegalArgumentException("E04 resistance exceeds bounded fixture range");
            this.sensorSourceResistanceOhms = sensorSourceResistanceOhms;
            this.sensorLoadOhms = sensorLoadOhms;
            this.referenceHighOhms = referenceHighOhms;
            this.referenceLowOhms = referenceLowOhms;
            this.directThresholdOffsetVolts = directThresholdOffsetVolts;
            this.risingThresholdOffsetVolts = risingThresholdOffsetVolts;
            this.fallingThresholdOffsetVolts = fallingThresholdOffsetVolts;
            this.sensorLowVolts = sensorLowVolts;
            this.sensorHighVolts = sensorHighVolts;
        }

        public static Configuration defaults(RailContract rail) {
            if (rail == null)
                throw new IllegalArgumentException("Missing E04 rail contract");
            double nominal = rail.getNominalVoltage();
            return new Configuration(10000.0, 100000.0, 10000.0, 10000.0,
                    0.0, nominal * 0.07, -nominal * 0.07,
                    nominal * 0.20, nominal * 0.65);
        }
    }

    /** Physical/semantic terminal mapping owned by this family model. */
    public static final class TerminalDescriptor {
        private final String id;
        private final String elementId;
        private final int postIndex;
        private final String role;

        TerminalDescriptor(String id, String elementId, int postIndex,
                String role) {
            this.id = id;
            this.elementId = elementId;
            this.postIndex = postIndex;
            this.role = role;
        }

        public String getId() { return id; }
        public String getElementId() { return elementId; }
        public int getPostIndex() { return postIndex; }
        public String getRole() { return role; }
    }

    /**
     * Package-private live solver targets owned by the E04 family.  These are
     * endpoint wrappers around the elements already installed in the fixture;
     * the binding view never creates a second source, load, or fault element.
     */
    static final class SensorControlBindings {
        private final CircuitPostMeasurementEndpoint externalSensorSource;
        private final CircuitPostMeasurementEndpoint externalSensorReturn;
        private final CircuitPostMeasurementEndpoint conditionedSensor;
        private final CircuitPostMeasurementEndpoint reference;
        private final CircuitPostMeasurementEndpoint rail;
        private final CircuitPostMeasurementEndpoint outputDrive;
        private final CircuitPostMeasurementEndpoint loadedOutput;
        private final CircuitPostMeasurementEndpoint returned;
        private final CircuitPostMeasurementEndpoint rawInput;
        private final CircuitPostMeasurementEndpoint rawReturn;
        private final CircuitPostMeasurementEndpoint regulatorInputBoard;
        private final CircuitPostMeasurementEndpoint regulatorOutputBoard;
        private final CircuitPostMeasurementEndpoint regulatorReturnBoard;
        private final CircuitPostMeasurementEndpoint regulatorEnableBoard;
        private final CircuitPostMeasurementEndpoint regulatorInput;
        private final CircuitPostMeasurementEndpoint regulatorOutput;
        private final CircuitPostMeasurementEndpoint regulatorReturn;
        private final CircuitPostMeasurementEndpoint regulatorEnable;
        private final Map<String, PassiveBinding> faultablePassives;

        SensorControlBindings(CircuitPostMeasurementEndpoint externalSensorSource,
                CircuitPostMeasurementEndpoint externalSensorReturn,
                CircuitPostMeasurementEndpoint conditionedSensor,
                CircuitPostMeasurementEndpoint reference,
                CircuitPostMeasurementEndpoint rail,
                CircuitPostMeasurementEndpoint outputDrive,
                CircuitPostMeasurementEndpoint loadedOutput,
                CircuitPostMeasurementEndpoint returned,
                CircuitPostMeasurementEndpoint rawInput,
                CircuitPostMeasurementEndpoint rawReturn,
                CircuitPostMeasurementEndpoint regulatorInputBoard,
                CircuitPostMeasurementEndpoint regulatorOutputBoard,
                CircuitPostMeasurementEndpoint regulatorReturnBoard,
                CircuitPostMeasurementEndpoint regulatorEnableBoard,
                CircuitPostMeasurementEndpoint regulatorInput,
                CircuitPostMeasurementEndpoint regulatorOutput,
                CircuitPostMeasurementEndpoint regulatorReturn,
                CircuitPostMeasurementEndpoint regulatorEnable,
                Map<String, PassiveBinding> faultablePassives) {
            this.externalSensorSource = externalSensorSource;
            this.externalSensorReturn = externalSensorReturn;
            this.conditionedSensor = conditionedSensor;
            this.reference = reference;
            this.rail = rail;
            this.outputDrive = outputDrive;
            this.loadedOutput = loadedOutput;
            this.returned = returned;
            this.rawInput = rawInput;
            this.rawReturn = rawReturn;
            this.regulatorInputBoard = regulatorInputBoard;
            this.regulatorOutputBoard = regulatorOutputBoard;
            this.regulatorReturnBoard = regulatorReturnBoard;
            this.regulatorEnableBoard = regulatorEnableBoard;
            this.regulatorInput = regulatorInput;
            this.regulatorOutput = regulatorOutput;
            this.regulatorReturn = regulatorReturn;
            this.regulatorEnable = regulatorEnable;
            this.faultablePassives = faultablePassives;
        }

        CircuitPostMeasurementEndpoint getExternalSensorSourceEndpoint() {
            return externalSensorSource;
        }

        CircuitPostMeasurementEndpoint getExternalSensorReturnEndpoint() {
            return externalSensorReturn;
        }

        CircuitPostMeasurementEndpoint getExternalSensorInputEndpoint() {
            return externalSensorSource;
        }

        CircuitPostMeasurementEndpoint getSensorSourceConnectorEndpoint() {
            return externalSensorSource;
        }

        CircuitPostMeasurementEndpoint getConditionedSensorEndpoint() {
            return conditionedSensor;
        }

        CircuitPostMeasurementEndpoint getConditionedSensorNodeEndpoint() {
            return conditionedSensor;
        }

        CircuitPostMeasurementEndpoint getReferenceEndpoint() {
            return reference;
        }

        CircuitPostMeasurementEndpoint getReference() { return reference; }

        CircuitPostMeasurementEndpoint getRailEndpoint() { return rail; }

        CircuitPostMeasurementEndpoint getRail() { return rail; }

        /** Decision output before the model-owned output resistance. */
        CircuitPostMeasurementEndpoint getOutputDriveEndpoint() {
            return outputDrive;
        }

        CircuitPostMeasurementEndpoint getLoadedOutputEndpoint() {
            return loadedOutput;
        }

        CircuitPostMeasurementEndpoint getLoadedOutput() { return loadedOutput; }

        CircuitPostMeasurementEndpoint getReturnEndpoint() { return returned; }

        CircuitPostMeasurementEndpoint getReturn() { return returned; }

        /** The external raw source post that powers the selected E02 rail. */
        CircuitPostMeasurementEndpoint getRawInputEndpoint() { return rawInput; }

        /** The external source return post; this is not an invented ideal rail. */
        CircuitPostMeasurementEndpoint getRawReturnEndpoint() { return rawReturn; }

        /** Persistent board/copper endpoints for the detachable U1 leads. */
        CircuitPostMeasurementEndpoint getRegulatorInputBoardEndpoint() {
            return regulatorInputBoard;
        }

        CircuitPostMeasurementEndpoint getRegulatorOutputBoardEndpoint() {
            return regulatorOutputBoard;
        }

        CircuitPostMeasurementEndpoint getRegulatorReturnBoardEndpoint() {
            return regulatorReturnBoard;
        }

        CircuitPostMeasurementEndpoint getRegulatorEnableBoardEndpoint() {
            return regulatorEnableBoard;
        }

        /** Exact selected E02 regulator posts for the physical U1 mapping. */
        CircuitPostMeasurementEndpoint getRegulatorInputEndpoint() {
            return regulatorInput;
        }

        CircuitPostMeasurementEndpoint getRegulatorOutputEndpoint() {
            return regulatorOutput;
        }

        CircuitPostMeasurementEndpoint getRegulatorReturnEndpoint() {
            return regulatorReturn;
        }

        CircuitPostMeasurementEndpoint getRegulatorEnableEndpoint() {
            return regulatorEnable;
        }

        Map<String, PassiveBinding> getFaultablePassives() {
            return faultablePassives;
        }

        Map<String, PassiveBinding> getFaultablePassiveElements() {
            return faultablePassives;
        }

        PassiveBinding getFaultablePassive(String id) {
            return faultablePassives.get(id);
        }
    }

    /** One actual passive element and both of its live measurement posts. */
    static final class PassiveBinding {
        private final String id;
        private final ResistorElm element;
        private final CircuitPostMeasurementEndpoint first;
        private final CircuitPostMeasurementEndpoint second;
        private final SwitchElm faultIsolation;
        private final ResistorSecondaryOpenPath openPath;

        PassiveBinding(String id, ResistorElm element) {
            this(id, element, null, null);
        }

        PassiveBinding(String id, ResistorElm element, SwitchElm faultIsolation,
                ResistorSecondaryOpenPath openPath) {
            this.id = id;
            this.element = element;
            this.first = postEndpoint(element, 0);
            this.second = openPath == null ? postEndpoint(element, 1) :
                openPath.getPublicTerminal();
            this.faultIsolation = faultIsolation;
            this.openPath = openPath;
        }

        String getId() { return id; }
        ResistorElm getElement() { return element; }
        CircuitPostMeasurementEndpoint getFirstEndpoint() { return first; }
        CircuitPostMeasurementEndpoint getSecondEndpoint() { return second; }

        SwitchElm getFaultIsolation() { return faultIsolation; }
        ResistorSecondaryOpenPath getOpenPath() { return openPath; }

        CircuitPostMeasurementEndpoint getEndpoint(int postIndex) {
            if (postIndex == 0) return first;
            if (postIndex == 1) return second;
            throw new IllegalArgumentException("Invalid E04 passive post");
        }
    }

    private static final double OPEN_RESISTANCE = 1e9;
    private static final double REFERENCE_MARGIN_VOLTS = .10;
    private static final double OUTPUT_HEADROOM_VOLTS = .15;
    // Match the bounded E02 Norton update tolerance so the control source
    // does not force needless extra nonlinear trials on every rail settling
    // step while retaining sub-millivolt decision-output accuracy.
    private static final double DECISION_OUTPUT_TOLERANCE = 5e-4;

    private final CirSim sim;
    private final RailContract rail;
    private final Variant variant;
    private final Configuration configuration;
    private final Vector<CircuitElm> elements = new Vector<CircuitElm>();
    private final Map<String, TerminalDescriptor> terminalMap;
    private final SensorControlBindings solverBindings;

    private final E04VariableSourceElm sensorSource;
    private final E04VariableSourceElm railSource;
    private final E02FiniteSourceElm regulatorInputSource;
    private final E02FiniteSourceElm regulatorEnableSource;
    private final AbstractRailRegulatorElm selectedRegulator;
    private final RailRegulationContract regulatorContract;
    private final ResistorElm sensorSourceResistance;
    private final ResistorElm sensorLoad;
    private final ResistorElm railSourceResistance;
    private final ResistorElm railLoad;
    private final ResistorElm referenceHigh;
    private final ResistorElm referenceLow;
    private final ResistorElm outputResistance;
    private final ResistorElm outputLoad;
    private final ResistorElm feedback;
    private final SwitchElm sensorSourceFaultIsolation;
    private final SwitchElm referenceHighFaultIsolation;
    private final SwitchElm outputResistanceFaultIsolation;
    private final ResistorSecondaryOpenPath sensorSourceOpenPath;
    private final ResistorSecondaryOpenPath referenceHighOpenPath;
    private final ResistorSecondaryOpenPath outputResistanceOpenPath;
    private final WireElm sensorSourceFirstLead;
    private final WireElm sensorSourceSecondLead;
    private final WireElm referenceHighFirstLead;
    private final WireElm referenceHighSecondLead;
    private final WireElm outputResistanceFirstLead;
    private final WireElm outputResistanceSecondLead;
    /* Fixture-only leads replaced by separately-owned, detachable U1 leads
       when this model is materialized as a physical board. */
    private WireElm regulatorInputFixtureLead;
    private WireElm regulatorOutputFixtureLead;
    private WireElm regulatorReturnFixtureLead;
    private WireElm regulatorEnableFixtureLead;
    private final DecisionElement decision;
    private final GroundElm ground;
    private final boolean hasFeedback;
    private final Map<String, PassiveBinding> boardPassives;
    private boolean referenceAvailable;
    private boolean powered;
    private boolean disposed;
    private boolean preparedForPhysicalBoard;
    private double requestedRegulatorInputVoltage;
    private boolean requestedRegulatorEnabled;

    public E04SensorControlModel(RailContract rail, Variant variant) {
        this(rail, variant, Configuration.defaults(rail));
    }

    public E04SensorControlModel(RailContract rail, Variant variant,
            Configuration configuration) {
        this(rail, variant, configuration, null, 0.0, true);
    }

    /**
     * Builds E04 around a selected E02 rail implementation.  The adapter is
     * intentionally bounded: E02's output contract is projected into the
     * generic E04 rail view, while the actual E02 regulator remains an
     * element in this model's returned solver graph.
     */
    public static E04SensorControlModel fromE02Rail(
            RailRegulationContract regulator, Variant variant) {
        RailContract adapted = adaptE02Rail(regulator);
        return new E04SensorControlModel(adapted, variant,
                Configuration.defaults(adapted), regulator,
                defaultRegulatorInput(regulator), true);
    }

    /** Uses a caller-owned E04 threshold configuration with the E02 graph. */
    public static E04SensorControlModel fromE02Rail(
            RailRegulationContract regulator, Variant variant,
            Configuration configuration) {
        RailContract adapted = adaptE02Rail(regulator);
        return new E04SensorControlModel(adapted, variant, configuration,
                regulator, defaultRegulatorInput(regulator), true);
    }

    /**
     * Explicit bounded E02 fixture overload for board-owned startup values.
     * Input voltage is the real E02 source voltage; enable is driven through
     * its real enable terminal.  Source changes require the board owner to
     * re-analyze, just like other E02 finite-source mutations.
     */
    public static E04SensorControlModel fromE02Rail(
            RailRegulationContract regulator, Variant variant,
            Configuration configuration, double inputVoltage, boolean enabled) {
        RailContract adapted = adaptE02Rail(regulator);
        return new E04SensorControlModel(adapted, variant, configuration,
                regulator, inputVoltage, enabled);
    }

    /**
     * Projects E02's output contract into the stable E04 rail API.  E02 does
     * not declare a consumer brownout threshold, so the bounded 76% nominal
     * point is explicit here; callers needing a different board threshold can
     * use the overload with an explicit minimum.
     */
    public static RailContract adaptE02Rail(RailRegulationContract regulator) {
        if (regulator == null)
            throw new IllegalArgumentException("Missing E02 rail contract");
        return adaptE02Rail(regulator,
                regulator.getNominalOutputVolts() * .76);
    }

    /** Bounded E02-to-E04 adapter with an explicit consumer brownout point. */
    public static RailContract adaptE02Rail(
            RailRegulationContract regulator, double minimumOperatingVoltage) {
        if (regulator == null)
            throw new IllegalArgumentException("Missing E02 rail contract");
        return new E02RailAdapter(regulator, minimumOperatingVoltage);
    }

    private E04SensorControlModel(RailContract rail, Variant variant,
            Configuration configuration, RailRegulationContract regulator,
            double regulatorInputVoltage, boolean regulatorEnabled) {
        if (rail == null)
            throw new IllegalArgumentException("Missing E04 rail contract");
        if (variant == null)
            throw new IllegalArgumentException("Missing E04 control variant");
        if (configuration == null)
            throw new IllegalArgumentException("Missing E04 control configuration");
        validateRail(rail);
        if (regulator != null)
            validateRegulatorSelection(regulator, regulatorInputVoltage);
        this.sim = CircuitElm.sim;
        if (sim == null)
            throw new IllegalStateException("CircuitJS singleton is not initialized");
        this.rail = rail;
        this.variant = variant;
        this.configuration = configuration;
        this.regulatorContract = regulator;
        this.requestedRegulatorInputVoltage = regulatorInputVoltage;
        this.requestedRegulatorEnabled = regulatorEnabled;

        ground = span(new GroundElm(96, 480), 96, 512);
        Point railNode;
        if (regulator == null) {
            railSource = span(new E04VariableSourceElm(192, 96), 192, 160);
            railSourceResistance = span(new ResistorElm(288, 160), 384, 160);
            regulatorInputSource = null;
            regulatorEnableSource = null;
            selectedRegulator = null;
            railNode = railSourceResistance.getPost(1);
        } else {
            railSource = null;
            railSourceResistance = null;
            regulatorInputSource = span(new E02FiniteSourceElm(192, 64,
                    regulatorInputVoltage, .10), 192, 160);
            regulatorEnableSource = span(new E02FiniteSourceElm(192, 224,
                    regulatorEnabled ? regulator.getEnableHighVolts() + .5 : 0.0,
                    1.0), 192, 128);
            selectedRegulator = regulator.isAveragedSwitching()
                    ? span(new AveragedSwitchingRegulatorElm(400, 160,
                            regulator), 512, 160)
                    : span(new LinearRegulatorElm(400, 160, regulator),
                            512, 160);
            // The physical board must retain a downstream copper anchor.
            // Assign it after railLoad exists rather than using U1's post
            // directly, so removing U1 cannot leave its output hard-wired to
            // the decision/reference network.
            railNode = null;
        }
        railLoad = span(new ResistorElm(576, 160), 576, 256);
        if (regulator != null)
            railNode = railLoad.getPost(0);
        sensorSource = span(new E04VariableSourceElm(192, 288), 192, 352);
        sensorSourceResistance = span(new ResistorElm(288, 352), 384, 352);
        // Keep each support load on the conditioned side of its physical
        // resistor seam.  Sharing its first post with the resistor before
        // the two explicit switches would make a zero-resistance loop once
        // the lead to the decision element is added, and would let a faulted
        // physical resistor keep carrying the support-load current.
        sensorLoad = span(new ResistorElm(576, 384), 576, 480);
        referenceHigh = span(new ResistorElm(480, 160), 480, 256);
        referenceLow = span(new ResistorElm(608, 272), 608, 352);
        decision = new DecisionElement(672, 288, rail, variant, configuration);
        // Keep RFB's first post distinct from the decision output so its
        // physical lead is a real detachable connection, rather than a
        // zero-length visual binding at the same CircuitJS coordinate.
        outputResistance = span(new ResistorElm(832, 288), 928, 288);
        outputLoad = span(new ResistorElm(1056, 288), 1056, 480);
        sensorSourceFaultIsolation = seriesSwitch(sensorSourceResistance);
        referenceHighFaultIsolation = seriesSwitch(referenceHigh);
        outputResistanceFaultIsolation = seriesSwitch(outputResistance);
        sensorSourceOpenPath = ResistorSecondaryOpenPath.create(
            postEndpoint(sensorSourceFaultIsolation, 1));
        referenceHighOpenPath = ResistorSecondaryOpenPath.create(
            postEndpoint(referenceHighFaultIsolation, 1));
        outputResistanceOpenPath = ResistorSecondaryOpenPath.create(
            postEndpoint(outputResistanceFaultIsolation, 1));
        if (variant == Variant.HYSTERETIC_REGENERATIVE) {
            // Its terminals must remain distinct from the pre-switch RFB and
            // RBIAS posts.  Sharing either endpoint would put the physical
            // switches in a zero-resistance loop around the feedback branch.
            feedback = span(new ResistorElm(800, 400), 800, 480);
            hasFeedback = true;
        } else {
            feedback = null;
            hasFeedback = false;
        }

        if (railSourceResistance != null)
            railSourceResistance.setResistance(rail.getSourceResistanceOhms());
        railLoad.setResistance(10000.0);
        sensorSourceResistance.setResistance(configuration.sensorSourceResistanceOhms);
        sensorLoad.setResistance(configuration.sensorLoadOhms);
        referenceHigh.setResistance(configuration.referenceHighOhms);
        referenceLow.setResistance(configuration.referenceLowOhms);
        outputResistance.setResistance(47.0);
        outputLoad.setResistance(10000.0);
        if (feedback != null) feedback.setResistance(22000.0);

        add(ground);
        if (regulator == null) {
            add(railSource); add(railSourceResistance);
        } else {
            add(regulatorInputSource); add(regulatorEnableSource);
            add(selectedRegulator);
        }
        add(railLoad);
        add(sensorSource); add(sensorSourceResistance); add(sensorLoad);
        add(sensorSourceFaultIsolation);
        add(sensorSourceOpenPath.getSimulationElement());
        add(referenceHigh); add(referenceLow); add(decision);
        add(referenceHighFaultIsolation);
        add(referenceHighOpenPath.getSimulationElement());
        add(outputResistance); add(outputLoad);
        add(outputResistanceFaultIsolation);
        add(outputResistanceOpenPath.getSimulationElement());
        if (feedback != null) add(feedback);
        if (regulator == null) {
            wire(ground.getPost(0), railSource.getPost(0));
            wire(railSource.getPost(1), railSourceResistance.getPost(0));
        } else {
            wire(ground.getPost(0), regulatorInputSource.getPost(0));
            wire(ground.getPost(0), regulatorEnableSource.getPost(0));
            regulatorReturnFixtureLead = wire(ground.getPost(0), selectedRegulator.getPost(
                    AbstractRailRegulatorElm.RETURN_POST));
            regulatorInputFixtureLead = wire(regulatorInputSource.getPost(1), selectedRegulator.getPost(
                    AbstractRailRegulatorElm.INPUT_POST));
            regulatorEnableFixtureLead = wire(regulatorEnableSource.getPost(1), selectedRegulator.getPost(
                    AbstractRailRegulatorElm.ENABLE_POST));
            regulatorOutputFixtureLead = wire(selectedRegulator.getPost(
                    AbstractRailRegulatorElm.OUTPUT_POST), railLoad.getPost(0));
        }
        wire(ground.getPost(0), sensorSource.getPost(0));
        wire(ground.getPost(0), railLoad.getPost(1));
        wire(ground.getPost(0), sensorLoad.getPost(1));
        wire(ground.getPost(0), referenceLow.getPost(1));
        wire(ground.getPost(0), decision.getPost(4));
        wire(ground.getPost(0), outputLoad.getPost(1));
        if (regulator == null)
            wire(railNode, railLoad.getPost(0));
        wire(railNode, decision.getPost(2));
        sensorSourceFirstLead = wire(sensorSource.getPost(1),
            sensorSourceResistance.getPost(0));
        sensorSourceSecondLead = wire(sensorSourceOpenPath.getPublicTerminal()
            .getElement().getPost(sensorSourceOpenPath.getPublicTerminal().getPostIndex()),
            decision.getPost(0));
        wire(decision.getPost(0), sensorLoad.getPost(0));
        referenceHighFirstLead = wire(railNode, referenceHigh.getPost(0));
        referenceHighSecondLead = wire(referenceHighOpenPath.getPublicTerminal()
            .getElement().getPost(referenceHighOpenPath.getPublicTerminal().getPostIndex()),
            decision.getPost(1));
        wire(decision.getPost(1), referenceLow.getPost(0));
        outputResistanceFirstLead = wire(decision.getPost(3),
            outputResistance.getPost(0));
        outputResistanceSecondLead = wire(outputResistanceOpenPath.getPublicTerminal()
            .getElement().getPost(outputResistanceOpenPath.getPublicTerminal().getPostIndex()),
            outputLoad.getPost(0));
        if (feedback != null) {
            wire(decision.getPost(3), feedback.getPost(0));
            wire(decision.getPost(0), feedback.getPost(1));
        }

        referenceAvailable = rail.hasReference();
        powered = true;
        setReferenceAvailable(referenceAvailable);
        setSensorCondition(SensorCondition.SENSOR_LOW);
        setRailPowered(true);

        LinkedHashMap<String, TerminalDescriptor> terminals =
                new LinkedHashMap<String, TerminalDescriptor>();
        terminals.put("SENSOR", new TerminalDescriptor("SENSOR", "U_E04_DECISION",
                0, "player-operated conditioned sensor input"));
        terminals.put("REFERENCE", new TerminalDescriptor("REFERENCE", "U_E04_DECISION",
                1, "solver-derived reference input"));
        terminals.put("RAIL", new TerminalDescriptor("RAIL", "U_E04_DECISION",
                2, "regulated control rail"));
        terminals.put("OUTPUT", new TerminalDescriptor("OUTPUT",
                "R_E04_OUTPUT_LOAD", 0, "loaded control decision output"));
        terminals.put("RETURN", new TerminalDescriptor("RETURN", "U_E04_DECISION",
                4, "declared rail return"));
        terminalMap = Collections.unmodifiableMap(terminals);

        LinkedHashMap<String, PassiveBinding> passives =
                new LinkedHashMap<String, PassiveBinding>();
        addPassive(passives, "SENSOR_SOURCE_RESISTANCE", sensorSourceResistance);
        addPassive(passives, "SENSOR_LOAD", sensorLoad);
        addPassive(passives, "RAIL_SOURCE_RESISTANCE", railSourceResistance);
        addPassive(passives, "RAIL_LOAD", railLoad);
        addPassive(passives, "REFERENCE_HIGH", referenceHigh);
        addPassive(passives, "REFERENCE_LOW", referenceLow);
        addPassive(passives, "OUTPUT_RESISTANCE", outputResistance);
        addPassive(passives, "OUTPUT_LOAD", outputLoad);
        addPassive(passives, "FEEDBACK", feedback);
        LinkedHashMap<String, PassiveBinding> physicalPassives =
                new LinkedHashMap<String, PassiveBinding>();
        physicalPassives.put("RBIAS", new PassiveBinding("RBIAS",
            sensorSourceResistance, sensorSourceFaultIsolation, sensorSourceOpenPath));
        physicalPassives.put("RREF", new PassiveBinding("RREF",
            referenceHigh, referenceHighFaultIsolation, referenceHighOpenPath));
        physicalPassives.put("RFB", new PassiveBinding("RFB",
            outputResistance, outputResistanceFaultIsolation, outputResistanceOpenPath));
        boardPassives = Collections.unmodifiableMap(physicalPassives);
        solverBindings = new SensorControlBindings(
                postEndpoint(sensorSource, 1),
                postEndpoint(sensorSource, 0),
                postEndpoint(decision, 0),
                postEndpoint(decision, 1),
                postEndpoint(decision, 2),
                postEndpoint(decision, 3),
                postEndpoint(outputLoad, 0),
                postEndpoint(decision, 4),
                regulator == null ? null : postEndpoint(regulatorInputSource, 1),
                regulator == null ? null : postEndpoint(regulatorInputSource, 0),
                regulator == null ? null : postEndpoint(regulatorInputSource, 1),
                regulator == null ? null : postEndpoint(railLoad, 0),
                regulator == null ? null : postEndpoint(ground, 0),
                regulator == null ? null : postEndpoint(regulatorEnableSource, 1),
                regulator == null ? null : postEndpoint(selectedRegulator,
                    AbstractRailRegulatorElm.INPUT_POST),
                regulator == null ? null : postEndpoint(selectedRegulator,
                    AbstractRailRegulatorElm.OUTPUT_POST),
                regulator == null ? null : postEndpoint(selectedRegulator,
                    AbstractRailRegulatorElm.RETURN_POST),
                regulator == null ? null : postEndpoint(selectedRegulator,
                    AbstractRailRegulatorElm.ENABLE_POST),
                Collections.unmodifiableMap(passives));
    }

    public RailContract getRailContract() { return rail; }
    public Variant getVariant() { return variant; }
    public Configuration getConfiguration() { return configuration; }
    public boolean hasRegenerativeFeedback() { return hasFeedback; }
    public boolean hasE02Regulator() { return regulatorContract != null; }
    public String getE02RegulatorVariantId() {
        return regulatorContract == null ? null : regulatorContract.getVariantId();
    }

    /** Package-private physical integration hook for the U1 board adapter. */
    AbstractRailRegulatorElm getSelectedE02Regulator() {
        return selectedRegulator;
    }

    /** Defensive copy suitable for a PrivateSolverContext or a board owner. */
    public Vector<CircuitElm> getSimulationElements() {
        return new Vector<CircuitElm>(elements);
    }

    public Map<String, TerminalDescriptor> getTerminalMapping() {
        return terminalMap;
    }

    /** Package-private live endpoints for E04 measurement/fault integration. */
    SensorControlBindings getSensorControlBindings() { return solverBindings; }

    /**
     * The three generated board resistors are the model-owned electrical
     * seams, not parallel board-only loads.  Their returned endpoints include
     * the physical secondary-open path used by replacement parts.
     */
    PassiveBinding getBoardOwnedPassive(String componentId) {
        return boardPassives.get(componentId);
    }

    /**
     * Remove the model fixture leads before a physical board generator adds
     * its detachable component leads.  The resistor bodies and series fault /
     * secondary paths remain in the one authoritative solver graph.
     */
    void prepareForPhysicalBoard() {
        if (preparedForPhysicalBoard)
            return;
        removeElement(sensorSourceFirstLead);
        removeElement(sensorSourceSecondLead);
        removeElement(referenceHighFirstLead);
        removeElement(referenceHighSecondLead);
        removeElement(outputResistanceFirstLead);
        removeElement(outputResistanceSecondLead);
        removeElement(regulatorInputFixtureLead);
        removeElement(regulatorOutputFixtureLead);
        removeElement(regulatorReturnFixtureLead);
        removeElement(regulatorEnableFixtureLead);
        preparedForPhysicalBoard = true;
    }

    /** Alias kept package-private for board-family integration code. */
    SensorControlBindings getEndpointBindings() { return solverBindings; }

    /** Solver-named alias for callers that consume only the electrical view. */
    SensorControlBindings getSolverBindings() { return solverBindings; }

    public void setSensorCondition(SensorCondition condition) {
        if (condition == null || condition == SensorCondition.SENSOR_UNSUPPORTED)
            throw new IllegalArgumentException("Unsupported player sensor condition");
        double voltage;
        switch (condition) {
        case SENSOR_LOW:
            voltage = Math.max(0.0, configuration.sensorLowVolts * .55);
            break;
        case SENSOR_MID:
            voltage = (configuration.sensorLowVolts +
                    configuration.sensorHighVolts) * .5;
            break;
        case SENSOR_HIGH:
            voltage = Math.min(rail.getNominalVoltage() * .9,
                    configuration.sensorHighVolts + rail.getNominalVoltage() * .25);
            break;
        default:
            throw new IllegalArgumentException("Unsupported player sensor condition");
        }
        setSensorVoltage(voltage);
    }

    /** Set the real player stimulus source; bounded overrange is observable as UNSUPPORTED. */
    public void setSensorVoltage(double voltage) {
        if (!finite(voltage) || voltage < -rail.getNominalVoltage() * .5 ||
                voltage > rail.getNominalVoltage() * 1.8)
            throw new IllegalArgumentException("Sensor stimulus outside bounded E04 envelope");
        sensorSource.setVoltage(voltage);
    }

    public void setRailPowered(boolean on) {
        powered = on;
        if (regulatorContract == null) {
            railSource.setVoltage(on ? rail.getNominalVoltage() : 0.0);
        } else {
            regulatorInputSource.setVoltage(on ? requestedRegulatorInputVoltage : 0.0);
            regulatorEnableSource.setVoltage(on && requestedRegulatorEnabled
                    ? regulatorContract.getEnableHighVolts() + .5 : 0.0);
        }
    }

    /** Bounded rail adjustment used by brownout and valid headroom canaries. */
    public void setRailVoltage(double voltage) {
        if (!finite(voltage) || voltage < 0.0 ||
                voltage > rail.getNominalVoltage() * 1.25)
            throw new IllegalArgumentException("Rail stimulus outside bounded E04 envelope");
        powered = voltage > 0.0;
        if (regulatorContract == null) {
            railSource.setVoltage(voltage);
        } else if (!powered) {
            regulatorInputSource.setVoltage(0.0);
            regulatorEnableSource.setVoltage(0.0);
        } else {
            // This legacy E04 operation is retained as a bounded input-side
            // request when E02 owns the rail.  The solved output remains the
            // regulator's actual finite result, not an ideal rail source.
            double requestedInput = voltage + regulatorContract.getDropoutVolts();
            if (requestedInput > regulatorContract.getMaximumInputVolts())
                requestedInput = regulatorContract.getMaximumInputVolts();
            setRegulatorInputVoltage(requestedInput);
            setRegulatorEnabled(true);
        }
    }

    /** Set the real E02 finite input source; re-analyze after changing it. */
    public void setRegulatorInputVoltage(double voltage) {
        requireE02Regulator();
        if (!finite(voltage) || voltage < 0.0 ||
                voltage > regulatorContract.getMaximumInputVolts())
            throw new IllegalArgumentException("E02 input outside bounded rail envelope");
        requestedRegulatorInputVoltage = voltage;
        regulatorInputSource.setVoltage(powered ? voltage : 0.0);
    }

    /** Set the real E02 enable source; re-analyze after changing it. */
    public void setRegulatorEnabled(boolean enabled) {
        requireE02Regulator();
        requestedRegulatorEnabled = enabled;
        regulatorEnableSource.setVoltage(powered && enabled
                ? regulatorContract.getEnableHighVolts() + .5 : 0.0);
    }

    public double getRegulatorInputVoltage() {
        requireE02Regulator();
        return selectedRegulator.getInputVoltage();
    }

    public double getRegulatorOutputVoltage() {
        requireE02Regulator();
        return selectedRegulator.getOutputVoltage();
    }

    public boolean isRegulatorEnabled() {
        requireE02Regulator();
        return selectedRegulator.isEnabled();
    }

    public boolean isRegulatorInDropout() {
        requireE02Regulator();
        return selectedRegulator.isInDropout();
    }

    /** Opens the physical reference supply leg; the return leg then proves
     * loss in the solver.  The board owner must re-analyze after this change. */
    public void setReferenceAvailable(boolean available) {
        referenceAvailable = available;
        referenceHigh.setResistance(available ? configuration.referenceHighOhms : OPEN_RESISTANCE);
        referenceLow.setResistance(configuration.referenceLowOhms);
    }

    /** The board owner must re-analyze after changing this passive topology. */
    public void setReferenceDivider(double highOhms, double lowOhms) {
        requireFinitePositive(highOhms, "reference high resistance");
        requireFinitePositive(lowOhms, "reference low resistance");
        if (highOhms > 1e8 || lowOhms > 1e8)
            throw new IllegalArgumentException("Reference divider exceeds bounded E04 range");
        referenceAvailable = true;
        referenceHigh.setResistance(highOhms);
        referenceLow.setResistance(lowOhms);
    }

    /** The board owner must re-analyze after changing this passive load. */
    public void setSensorLoadOhms(double ohms) {
        requireFinitePositive(ohms, "sensor load");
        if (ohms > 1e9) throw new IllegalArgumentException("Sensor load exceeds bounded E04 range");
        sensorLoad.setResistance(ohms);
    }

    /** The board owner must re-analyze after changing this passive load. */
    public void setOutputLoadOhms(double ohms) {
        requireFinitePositive(ohms, "output load");
        if (ohms > 1e9) throw new IllegalArgumentException("Output load exceeds bounded E04 range");
        outputLoad.setResistance(ohms);
    }

    /** The board owner must re-analyze after changing this passive load. */
    public void setRailLoadOhms(double ohms) {
        requireFinitePositive(ohms, "rail load");
        if (ohms > 1e9) throw new IllegalArgumentException("Rail load exceeds bounded E04 range");
        railLoad.setResistance(ohms);
    }

    public SensorCondition getSensorCondition() {
        double voltage = getSensorNodeVoltage();
        if (!finite(voltage) || voltage < 0.0 ||
                voltage > rail.getNominalVoltage() * 1.10)
            return SensorCondition.SENSOR_UNSUPPORTED;
        if (voltage < configuration.sensorLowVolts) return SensorCondition.SENSOR_LOW;
        if (voltage > configuration.sensorHighVolts) return SensorCondition.SENSOR_HIGH;
        return SensorCondition.SENSOR_MID;
    }

    public ControlState getControlState() { return decision.controlState; }
    public boolean isOutputHigh() { return decision.controlState == ControlState.HIGH; }
    public boolean isPowered() { return powered; }
    public boolean isReferenceAvailable() { return referenceAvailable; }
    public double getSensorNodeVoltage() { return decision.getPostVoltage(0) - decision.getPostVoltage(4); }
    public double getReferenceNodeVoltage() { return decision.getPostVoltage(1) - decision.getPostVoltage(4); }
    public double getRailNodeVoltage() { return decision.getPostVoltage(2) - decision.getPostVoltage(4); }
    public double getOutputVoltage() { return outputLoad.getPostVoltage(0) - outputLoad.getPostVoltage(1); }
    public double getOutputCurrent() { return outputLoad.getCurrent(); }
    public double getSensorSourceCurrent() { return sensorSourceResistance.getCurrent(); }
    public double getRailSourceCurrent() {
        return regulatorContract == null ? railSourceResistance.getCurrent() :
                regulatorInputSource.getCurrent();
    }
    public double getReferenceThresholdVoltage() { return getReferenceNodeVoltage() + configuration.directThresholdOffsetVolts; }
    public double getRisingThresholdVoltage() { return getReferenceNodeVoltage() + configuration.risingThresholdOffsetVolts; }
    public double getFallingThresholdVoltage() { return getReferenceNodeVoltage() + configuration.fallingThresholdOffsetVolts; }

    /** Caller must not dispose a fixture that has been installed into a board graph. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (CircuitElm element : elements) element.delete();
    }

    private void add(CircuitElm element) { elements.add(element); }

    private void removeElement(CircuitElm element) {
        if (element != null && elements.remove(element))
            element.delete();
    }

    private static void addPassive(Map<String, PassiveBinding> passives,
            String id, ResistorElm element) {
        if (element != null) passives.put(id, new PassiveBinding(id, element));
    }

    private static CircuitPostMeasurementEndpoint postEndpoint(
            CircuitElm element, int postIndex) {
        if (element == null || postIndex < 0 ||
                postIndex >= element.getPostCount())
            throw new IllegalArgumentException("Invalid E04 solver endpoint");
        return new CircuitPostMeasurementEndpoint(element, postIndex);
    }

    private WireElm wire(Point from, Point to) {
        WireElm wire = new WireElm(from.x, from.y);
        // Preserve custom regulator midpoint posts (which need not be on the
        // editor grid).  drag() rounds its endpoint and can leave a real E02
        // return/enable post electrically floating.
        wire.setPosition(from.x, from.y, to.x, to.y);
        elements.add(wire);
        return wire;
    }

    private SwitchElm seriesSwitch(ResistorElm resistor) {
        if (resistor == null || resistor.getPost(1) == null)
            throw new IllegalArgumentException("Missing E04 resistor series switch");
        Point post = resistor.getPost(1);
        SwitchElm result = new SwitchElm(post.x, post.y);
        result.drag(post.x + 32, post.y);
        return result;
    }

    private <T extends CircuitElm> T span(T element, int x2, int y2) {
        element.drag(x2, y2);
        return element;
    }

    private void requireE02Regulator() {
        if (regulatorContract == null)
            throw new IllegalStateException("E04 model has no selected E02 regulator");
    }

    private static double defaultRegulatorInput(
            RailRegulationContract regulator) {
        if (regulator == null)
            throw new IllegalArgumentException("Missing E02 rail contract");
        double requested = regulator.getNominalOutputVolts() + 2.0;
        return Math.min(regulator.getMaximumInputVolts(), requested);
    }

    private static void validateRegulatorSelection(
            RailRegulationContract regulator, double inputVoltage) {
        if (!finite(inputVoltage) || inputVoltage < 0.0 ||
                inputVoltage > regulator.getMaximumInputVolts())
            throw new IllegalArgumentException("E02 input outside bounded rail envelope");
        if (regulator.getTerminalIds().size() != 4 ||
                regulator.supportsSwitchingWaveform() ||
                regulator.supportsFrequencyMeasurement())
            throw new IllegalArgumentException("Unsupported E02 regulator selection");
    }

    /** Stable E04 view of one selected E02 contract; no solver state is copied. */
    private static final class E02RailAdapter implements RailContract {
        private final RailRegulationContract regulator;
        private final double minimumOperatingVoltage;

        E02RailAdapter(RailRegulationContract regulator,
                double minimumOperatingVoltage) {
            if (regulator == null)
                throw new IllegalArgumentException("Missing E02 rail contract");
            requireId(regulator.getVariantId(), "E02 regulator variant");
            requireId(regulator.getReturnTerminalId(),
                    "E02 regulator return terminal");
            if (regulator.getNominalOutputVolts() > 24.0)
                throw new IllegalArgumentException(
                        "E02 rail is outside the E04 0-24 V envelope");
            if (!finite(minimumOperatingVoltage) ||
                    minimumOperatingVoltage <= 0.0 ||
                    minimumOperatingVoltage > regulator.getNominalOutputVolts())
                throw new IllegalArgumentException(
                        "Invalid E02 consumer brownout minimum");
            validateRegulatorSelection(regulator,
                    Math.min(regulator.getMaximumInputVolts(),
                            regulator.getNominalOutputVolts() + 2.0));
            double sourceResistance = regulator.getOutputResistanceOhms() +
                    regulator.getNominalOutputVolts() /
                    regulator.getMaximumOutputCurrentAmps();
            if (!finite(sourceResistance) || sourceResistance <= 0.0 ||
                    sourceResistance > 1000.0)
                throw new IllegalArgumentException(
                        "E02 output resistance is outside the E04 envelope");
            this.regulator = regulator;
            this.minimumOperatingVoltage = minimumOperatingVoltage;
        }

        public String getRailId() {
            return "E02_" + regulator.getVariantId() + "_OUTPUT";
        }

        public String getReferenceId() { return regulator.getReturnTerminalId(); }

        public double getNominalVoltage() {
            return regulator.getNominalOutputVolts();
        }

        public double getMinimumOperatingVoltage() {
            return minimumOperatingVoltage;
        }

        public double getSourceResistanceOhms() {
            return regulator.getOutputResistanceOhms() +
                    regulator.getNominalOutputVolts() /
                    regulator.getMaximumOutputCurrentAmps();
        }

        /** E04 creates its reference divider from the selected rail output. */
        public boolean hasReference() { return true; }
    }

    private static void validateRail(RailContract rail) {
        requireId(rail.getRailId(), "rail id");
        requireId(rail.getReferenceId(), "reference id");
        requireFinitePositive(rail.getNominalVoltage(), "nominal rail voltage");
        if (rail.getNominalVoltage() > 24.0 ||
                !finite(rail.getMinimumOperatingVoltage()) ||
                rail.getMinimumOperatingVoltage() <= 0.0 ||
                rail.getMinimumOperatingVoltage() > rail.getNominalVoltage())
            throw new IllegalArgumentException("Invalid E04 rail operating envelope");
        requireFinitePositive(rail.getSourceResistanceOhms(), "rail source resistance");
        if (rail.getSourceResistanceOhms() > 1000.0)
            throw new IllegalArgumentException("E04 rail source resistance is outside its bounded envelope");
    }

    private static void requireId(String value, String label) {
        if (value == null || value.length() == 0 || value.indexOf('\n') >= 0 ||
                value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0)
            throw new IllegalArgumentException("Invalid E04 " + label);
    }

    private static void requireFinite(double value, String label) {
        if (!finite(value)) throw new IllegalArgumentException("Invalid E04 " + label);
    }

    private static void requireFinitePositive(double value, String label) {
        if (!finite(value) || value <= 0.0)
            throw new IllegalArgumentException("Invalid E04 " + label);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Variable source used for player stimulus and rail dropout without graph replacement. */
    private static final class E04VariableSourceElm extends VoltageElm {
        private double requestedVoltage;

        E04VariableSourceElm(int x, int y) {
            super(x, y, WF_VAR);
            waveform = WF_VAR;
            // Keep the inherited, audited source payload equal to the real
            // requested value.  GenerationDependencyContext deliberately
            // reads VoltageElm.dump(), so a player/source mutation must never
            // look like the same immutable diagnostic context.
            setVoltage(0.0);
        }

        double getVoltage() { return requestedVoltage; }

        void setVoltage(double voltage) {
            if (!finite(voltage)) throw new IllegalArgumentException("Nonfinite E04 source voltage");
            requestedVoltage = voltage;
            maxVoltage = voltage;
            bias = 0.0;
        }

        void stamp() {
            // Keep the RHS dynamic.  Stamping a fixed value here lets the
            // matrix simplifier eliminate this row, after which a later
            // player source update would address a retired row.
            sim.stampVoltageSource(nodes[0], nodes[1], voltSource);
        }

        void doStep() {
            sim.updateVoltageSource(nodes[0], nodes[1], voltSource, requestedVoltage);
        }
    }

    /**
     * Actual decision model: high-impedance sensor/reference/rail inputs and
     * a bounded output voltage source.  Hysteresis is retained between solver
     * steps and is also backed by a physical output-to-sensor resistor in the
     * regenerative variant.
     */
    /**
     * Solver-backed bounded threshold decision.  Unlike a controller-owned
     * helper, this is a real CircuitJS element, so its declaration has an
     * audited save/load representation.  The retained high/low latch and
     * last solved output are intentionally runtime state, not dump input.
     */
    static final class DecisionElement extends CircuitElm {
        private static final int DUMP_TYPE = 457;
        private final Point[] posts = new Point[5];
        private final Variant variant;
        private final double nominalRailVoltage;
        private final double minimumOperatingVoltage;
        private final double directThresholdOffsetVolts;
        private final double risingThresholdOffsetVolts;
        private final double fallingThresholdOffsetVolts;
        private boolean high;
        private double lastOutput = Double.NaN;
        private ControlState controlState = ControlState.UNSETTLED;

        DecisionElement(int x, int y, RailContract rail, Variant variant,
                Configuration configuration) {
            this(x, y, x + 96, y, 0, rail, variant, configuration);
        }

        private DecisionElement(int xa, int ya, int xb, int yb, int flags,
                RailContract rail, Variant variant, Configuration configuration) {
            super(xa, ya, xb, yb, flags);
            if (rail == null || variant == null || configuration == null)
                throw new IllegalArgumentException("Missing E04 decision declaration");
            nominalRailVoltage = rail.getNominalVoltage();
            minimumOperatingVoltage = rail.getMinimumOperatingVoltage();
            this.variant = variant;
            directThresholdOffsetVolts = configuration.directThresholdOffsetVolts;
            risingThresholdOffsetVolts = configuration.risingThresholdOffsetVolts;
            fallingThresholdOffsetVolts = configuration.fallingThresholdOffsetVolts;
            validateDecisionDeclaration();
            setPoints();
        }

        /** CircuitJS undo/import constructor for the bounded E04 declaration. */
        DecisionElement(int xa, int ya, int xb, int yb, int flags,
                StringTokenizer st) {
            super(xa, ya, xb, yb, flags);
            variant = parseVariant(st);
            nominalRailVoltage = nextDecisionDouble(st, "nominal rail voltage");
            minimumOperatingVoltage = nextDecisionDouble(st, "minimum operating voltage");
            directThresholdOffsetVolts = nextDecisionDouble(st,
                    "direct threshold offset");
            risingThresholdOffsetVolts = nextDecisionDouble(st,
                    "rising threshold offset");
            fallingThresholdOffsetVolts = nextDecisionDouble(st,
                    "falling threshold offset");
            if (st.hasMoreTokens())
                throw new IllegalArgumentException("Unexpected E04 decision dump payload");
            validateDecisionDeclaration();
            setPoints();
        }

        int getDumpType() { return DUMP_TYPE; }

        String dump() {
            return super.dump() + " " + variant.ordinal() + " " +
                nominalRailVoltage + " " + minimumOperatingVoltage + " " +
                directThresholdOffsetVolts + " " + risingThresholdOffsetVolts +
                " " + fallingThresholdOffsetVolts;
        }

        private void validateDecisionDeclaration() {
            if (!finite(nominalRailVoltage) || nominalRailVoltage <= 0.0 ||
                    nominalRailVoltage > 24.0 ||
                    !finite(minimumOperatingVoltage) ||
                    minimumOperatingVoltage <= 0.0 ||
                    minimumOperatingVoltage > nominalRailVoltage ||
                    !finite(directThresholdOffsetVolts) ||
                    !finite(risingThresholdOffsetVolts) ||
                    !finite(fallingThresholdOffsetVolts) ||
                    risingThresholdOffsetVolts <= fallingThresholdOffsetVolts)
                throw new IllegalArgumentException("Invalid E04 decision declaration");
        }

        private static Variant parseVariant(StringTokenizer st) {
            if (st == null || !st.hasMoreTokens())
                throw new IllegalArgumentException("Missing E04 decision variant");
            try {
                int ordinal = Integer.parseInt(st.nextToken());
                Variant[] variants = Variant.values();
                if (ordinal < 0 || ordinal >= variants.length)
                    throw new IllegalArgumentException("Invalid E04 decision variant");
                return variants[ordinal];
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Invalid E04 decision variant");
            }
        }

        private static double nextDecisionDouble(StringTokenizer st, String label) {
            if (st == null || !st.hasMoreTokens())
                throw new IllegalArgumentException("Missing E04 decision " + label);
            try {
                return Double.parseDouble(st.nextToken());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Invalid E04 decision " + label);
            }
        }

        int getPostCount() { return 5; }
        int getVoltageSourceCount() { return 1; }
        boolean nonLinear() { return true; }

        void setPoints() {
            point1 = new Point(x, y);
            point2 = new Point(x2, y2);
            posts[0] = new Point(x, y - 32);
            posts[1] = new Point(x, y - 16);
            posts[2] = new Point(x, y);
            posts[3] = new Point(x2, y2);
            posts[4] = new Point(x, y + 32);
            boundingBox = new Rectangle(Math.min(x, x2), y - 40,
                    Math.abs(x2 - x) + 1, 81);
        }

        Point getPost(int n) { return posts[n]; }

        void stamp() {
            // CircuitJS defines a source value as V(second)-V(first).  Keep
            // the family output positive with respect to its declared
            // return post.
            sim.stampVoltageSource(nodes[4], nodes[3], voltSource);
        }

        void doStep() {
            double returnVoltage = volts[4];
            double sensorVoltage = volts[0] - returnVoltage;
            double referenceVoltage = volts[1] - returnVoltage;
            double railVoltage = volts[2] - returnVoltage;
            ControlState prior = controlState;
            boolean priorHigh = high;

            if (!finite(sensorVoltage) || !finite(referenceVoltage) ||
                    !finite(railVoltage)) {
                high = false;
                controlState = ControlState.UNSUPPORTED;
            } else if (railVoltage < minimumOperatingVoltage) {
                high = false;
                controlState = ControlState.BROWNOUT;
            } else if (referenceVoltage <= REFERENCE_MARGIN_VOLTS ||
                    referenceVoltage >= railVoltage - REFERENCE_MARGIN_VOLTS) {
                high = false;
                controlState = ControlState.REFERENCE_LOST;
            } else if (sensorVoltage < -REFERENCE_MARGIN_VOLTS ||
                    sensorVoltage > nominalRailVoltage * 1.10) {
                high = false;
                controlState = ControlState.UNSUPPORTED;
            } else {
                if (variant == Variant.DIRECT_THRESHOLD) {
                    high = sensorVoltage >= referenceVoltage +
                            directThresholdOffsetVolts;
                } else if (high) {
                    if (sensorVoltage <= referenceVoltage +
                            fallingThresholdOffsetVolts)
                        high = false;
                } else if (sensorVoltage >= referenceVoltage +
                        risingThresholdOffsetVolts) {
                    high = true;
                }
                controlState = high ? ControlState.HIGH : ControlState.LOW;
            }

            double output = high ? Math.max(0.0,
                    railVoltage - OUTPUT_HEADROOM_VOLTS) : 0.0;
            if (!finite(output)) {
                output = 0.0;
                high = false;
                controlState = ControlState.UNSUPPORTED;
            }
            if (prior != controlState || priorHigh != high ||
                    !finite(lastOutput) || Math.abs(lastOutput - output) >
                    DECISION_OUTPUT_TOLERANCE)
                sim.converged = false;
            lastOutput = output;
            sim.updateVoltageSource(nodes[4], nodes[3], voltSource, output);
        }

        double getVoltageDiff() { return volts[3] - volts[4]; }

        // Sensor, reference and rail inputs remain high impedance, while the
        // modeled output source has a real return path.  Advertising only
        // that pair keeps an opened RFB from leaving the live source node
        // falsely unconnected during CircuitJS graph validation.
        boolean getConnection(int n1, int n2) {
            return (n1 == 3 && n2 == 4) || (n1 == 4 && n2 == 3);
        }
        boolean hasGroundConnection(int n) { return n == 4; }
        @Override double getCurrentIntoNode(int n) {
            if (n == 3) return -current;
            if (n == 4) return current;
            return 0.0;
        }
    }
}
