package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Q30 input recipe and functional observations on the current CircuitJS graph. */
final class Rb30Behavior implements GeneratedBoardFamilyState,
        GeneratedChallengeBehaviorContract, GeneratedTemporalBehavior,
        GeneratedLiveTemporalSimulation {
    static final String SENSORS_LOW = "SENSORS_LOW";
    static final String SENSORS_A_ONLY = "SENSORS_A_ONLY";
    static final String SENSORS_B_ONLY = "SENSORS_B_ONLY";
    static final String SENSORS_HIGH = "SENSORS_HIGH";
    static final double SAMPLE_SECONDS = .030;
    static final double SOLVER_MAX_STEP_SECONDS = 5e-6;
    static final double SOLVER_MIN_STEP_SECONDS = 50e-12;
    static final double LIVE_POWERED_SECONDS = .0001;
    static final double LIVE_ISOLATED_SECONDS = .005;
    private final TroubleshootBoard board;
    private final GeneratedExternalPowerBindings power;
    private final String[] channels;
    private final int allInputMask;
    private final int profileWorkUnits;
    private final int retestWorkUnits;
    private final GeneratedBoardOperationCatalog operations = new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retest;
    private int input;
    private GeneratedObservedBehavior observed;

    Rb30Behavior(Rb30Generator.Candidate candidate) {
        board = candidate.board();
        power = candidate.assembly.power;
        channels = candidate.plan.channels();
        allInputMask = (1 << channels.length) - 1;
        profileWorkUnits = (1 << channels.length) + 1;
        retestWorkUnits = profileWorkUnits;
        input = allInputMask;
        if (channels.length == 1) {
            addInput(SENSORS_LOW, "Set sensor A LOW", 0);
            addInput(SENSORS_HIGH, "Set sensor A HIGH", 1);
        } else {
            addInput(SENSORS_LOW, "Set both sensors LOW", 0);
            addInput(SENSORS_A_ONLY, "Set sensor A HIGH, B LOW", 1);
            addInput(SENSORS_B_ONLY, "Set sensor A LOW, B HIGH", 2);
            addInput(SENSORS_HIGH, "Set both sensors HIGH", 3);
        }
        retest = new GeneratedCustomerRetestProfile("RB30_CUSTOMER_RETEST",
            channels.length == 1 ?
                "Check that the external load follows sensor A at LOW and HIGH." :
                "Check that each external load follows its own sensor, including both loads together.",
            channels.length == 1 ?
                "Connect the 12 V main and isolated load supplies and sensor A." :
                "Connect the 12 V main and isolated load supplies and both sensor inputs.",
            channels.length == 1 ? "LOW, HIGH; restore the previous input." :
                "Both LOW, A only, B only, both HIGH; restore the previous inputs.",
            channels.length == 1 ?
                "Observe output A across its two-terminal load connector." :
                "Observe each output across its own two-terminal load connector.",
            "Allow 30 ms of CircuitJS time after each input change.",
            channels.length == 1 ?
                "Low-voltage DC, one 180 ohm external load; all serviced leads reconnected." :
                "Low-voltage DC, two 180 ohm external loads; all serviced leads reconnected.",
            new GeneratedCustomerRetestProfile.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    return GeneratedWork.complete(beginCustomerRetest(sim, owner));
                }
            });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Retest Customer", new GeneratedBoardOperation.ResumableExecutor() {
                GeneratedWork<GeneratedCustomerRetestResult> begin(CirSim sim,
                        GeneratedBoardInstance owner) {
                    return beginCustomerRetest(sim, owner);
                }
                int getWorkUnits(GeneratedBoardInstance owner) { return retestWorkUnits; }
            }));
    }

    private void addInput(String id, String label, final int value) {
        operations.add(new GeneratedBoardOperation(id, label, new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                setInputs(sim, owner, value);
                return null;
            }
        }));
    }

    public void requireOwnedBy(GeneratedBoardInstance owner) {
        if (owner == null || owner.getBoard() != board || owner.getExternalPowerBindings() != power ||
                owner.getFamilyState() != this || owner.getTemporalBehavior() != this)
            throw new IllegalArgumentException("Foreign Q30 behavior owner");
        for (String channel : channels)
            for (CircuitElm element : power.getBinding("SENSOR_" + channel).getBackingElements())
                if (!owner.getSimulationElements().contains(element))
                    throw new IllegalArgumentException("Foreign Q30 sensor source");
    }

    private void requireCurrent(CirSim sim, GeneratedBoardInstance owner) {
        requireOwnedBy(owner);
        if (sim == null || sim.getGeneratedBoardInstance() != owner || sim.activeMeasurementOverlay)
            throw new IllegalStateException("Q30 operation lost its live owner");
    }

    void setInputs(CirSim sim, GeneratedBoardInstance owner, int value) {
        requireCurrent(sim, owner);
        if (value < 0 || value > allInputMask)
            throw new IllegalArgumentException("Unsupported Q30 inputs");
        for (int index = 0; index < channels.length; index++) {
            LimitedDcSupplyElm source = power.getBinding(
                "SENSOR_" + channels[index]).getLimitedSupply();
            source.configure((value & (1 << index)) != 0 ? 5 : 0,
                source.getLimitAmps());
        }
        input = value;
        // Restamp the real finite sources; this never connects a source or repowers the board.
        sim.advanceGeneratedTemporalProfile(SAMPLE_SECONDS);
        requireCurrent(sim, owner);
    }

    int getInputs() { return input; }

    static double voltage(GeneratedBoardInstance owner, String positive, String negative) {
        return postVoltage(owner, positive) - postVoltage(owner, negative);
    }

    private static double postVoltage(GeneratedBoardInstance owner, String pad) {
        CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings().getEndpoint(pad);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing Q30 observation endpoint " + pad);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        if (!owner.getSimulationElements().contains(post.getElement()))
            throw new IllegalStateException("Stale Q30 observation endpoint " + pad);
        double value = post.getElement().getPostVoltage(post.getPostIndex());
        if (!PowerDomainContract.finite(value))
            throw new IllegalStateException("Non-finite Q30 observation " + pad);
        return value;
    }

    boolean healthy(GeneratedBoardInstance owner, int condition) {
        requireOwnedBy(owner);
        double rail = voltage(owner, "U1.OUTPUT", "U1.RETURN");
        if (rail < 4.75 || rail > 5.25) return false;
        for (int index = 0; index < channels.length; index++) {
            String channel = channels[index];
            double output = voltage(owner, "JO" + channel + ".1",
                "JO" + channel + ".2");
            if (!outputMatches(output, (condition & (1 << index)) != 0))
                return false;
        }
        return true;
    }

    private static boolean outputMatches(double volts, boolean on) {
        return on ? volts >= 10.8 && volts <= 12.6 : Math.abs(volts) <= .05;
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance owner, String id) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(owner, id);
    }
    public String getSessionInputSignature() {
        StringBuilder out = new StringBuilder("RB30_INPUTS@1:");
        for (int index = 0; index < channels.length; index++) {
            double expected = (input & (1 << index)) != 0 ? 5 : 0;
            LimitedDcSupplyElm source = power.getBinding("SENSOR_" + channels[index]).getLimitedSupply();
            if (source == null || source.maxVoltage != expected)
                throw new IllegalStateException("Sensor command disagrees with its source");
            out.append(channels[index].length()).append(':').append(channels[index])
                .append(expected == 0 ? ":LOW;" : ":HIGH;");
        }
        return out.toString();
    }

    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retest; }
    // Preserve the powered cadence on this larger graph. Once every actual
    // source switch is isolated, advance the same transient in bounded 5 ms
    // slices so its high-resistance storage tails can discharge in live play.
    // This changes scheduling only; voltages, timesteps and readiness still
    // come from CircuitJS. Fault-isolated capacitors can remain charged.
    public double getLiveSolverAdvanceSeconds() {
        return power.areAllDisconnected() ? LIVE_ISOLATED_SECONDS : LIVE_POWERED_SECONDS;
    }
    public int getProfileWorkUnits() { return profileWorkUnits; }

    public GeneratedTemporalDependency getDependency(GeneratedBoardInstance owner) {
        requireOwnedBy(owner);
        TreeMap<String, String> values = new TreeMap<String, String>();
        values.put("recipe", channels.length == 1 ?
            "LOW-HIGH-restore-inputs" : "LOW-A_ONLY-B_ONLY-HIGH-restore-inputs");
        values.put("fault-preparation", channels.length == 1 ?
            "healthy-two-conditions-then-LOW-apply-fault-then-HIGH" :
            "healthy-four-conditions-then-LOW-apply-fault-then-HIGH");
        values.put("sample-seconds", Double.toString(SAMPLE_SECONDS));
        values.put("live-powered-seconds", Double.toString(LIVE_POWERED_SECONDS));
        values.put("live-isolated-seconds", Double.toString(LIVE_ISOLATED_SECONDS));
        values.put("qualification-solver", "CircuitJS-adaptive");
        values.put("qualification-maximum-step-seconds", Double.toString(SOLVER_MAX_STEP_SECONDS));
        values.put("qualification-minimum-step-seconds", Double.toString(SOLVER_MIN_STEP_SECONDS));
        values.put("profile-work-units", Integer.toString(profileWorkUnits));
        values.put("customer-retest-work-units", Integer.toString(retestWorkUnits));
        values.put("active-channels", joinChannels());
        values.put("condition-count", Integer.toString(1 << channels.length));
        values.put("rail-range-volts", "4.75..5.25");
        values.put("on-range-volts", "10.8..12.6");
        values.put("off-maximum-volts", "0.05");
        StringBuilder outputs = new StringBuilder();
        for (String channel : channels) {
            if (outputs.length() > 0) outputs.append(';');
            outputs.append("JO").append(channel).append(".1-JO")
                .append(channel).append(".2");
        }
        values.put("outputs", outputs.toString());
        values.put("topology", owner.getTopologyVariantId());
        values.put("physical-policy", MediumBoardPhysicalPolicy.identity());
        values.put("seed", Long.toString(owner.getSeed()));
        return new GeneratedTemporalDependency("RB30_CHANNEL_FUNCTION", 3,
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1,
            "JOA.1", "JOA.2", values);
    }

    private String joinChannels() {
        StringBuilder result = new StringBuilder();
        for (String channel : channels) {
            if (result.length() > 0) result.append(',');
            result.append(channel);
        }
        return result.toString();
    }

    public GeneratedWork<GeneratedRepairStatus> beginProfile(final CirSim sim,
            final GeneratedBoardInstance owner, final Profile profile) {
        return new ProfileWork(sim, owner, profile);
    }

    public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance owner) {
        GeneratedRepairStatus status = GeneratedWork.complete(
            beginProfile(sim, owner, Profile.HEALTHY));
        if (status != GeneratedRepairStatus.CORRECTLY_RESTORED)
            throw new IllegalStateException("Healthy Q30 profile did not complete");
    }
    public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance owner) {
        GeneratedWork.complete(beginProfile(sim, owner, Profile.FAULTED));
    }

    private GeneratedWork<GeneratedCustomerRetestResult> beginCustomerRetest(
            final CirSim sim, final GeneratedBoardInstance owner) {
        if (sim == null || owner == null || sim.getGeneratedBoardInstance() != owner ||
                sim.getBoardPowerController() == null ||
                !GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, owner))
            return failedCustomerRetestWork();
        final GeneratedWork<GeneratedRepairStatus> profile =
            beginProfile(sim, owner, Profile.REPAIR);
        return new GeneratedWork<GeneratedCustomerRetestResult>() {
            private boolean cancelled;
            private boolean complete;
            private GeneratedCustomerRetestResult result;
            boolean step() {
                if (cancelled) throw new IllegalStateException("Q30 customer retest cancelled");
                return profile.step();
            }
            GeneratedCustomerRetestResult finish() {
                if (cancelled) throw new IllegalStateException("Q30 customer retest cancelled");
                if (complete) return result;
                GeneratedRepairStatus status = profile.finish();
                result = status == GeneratedRepairStatus.CORRECTLY_RESTORED ?
                    GeneratedCustomerRetestSupport.success() : GeneratedCustomerRetestSupport.failure();
                complete = true;
                return result;
            }
            void cancel() {
                if (cancelled || complete) return;
                profile.cancel();
                cancelled = true;
            }
            int getWorkUnits() { return retestWorkUnits; }
        };
    }

    private GeneratedWork<GeneratedCustomerRetestResult> failedCustomerRetestWork() {
        return new GeneratedWork<GeneratedCustomerRetestResult>() {
            private boolean complete, cancelled;
            boolean step() {
                if (cancelled) throw new IllegalStateException("Q30 customer retest cancelled");
                complete = true;
                return false;
            }
            GeneratedCustomerRetestResult finish() {
                if (cancelled || !complete) throw new IllegalStateException("Q30 customer retest incomplete");
                return GeneratedCustomerRetestSupport.failure();
            }
            void cancel() { if (!complete) cancelled = true; }
            int getWorkUnits() { return retestWorkUnits; }
        };
    }

    /** One guarded unit performs at most one CircuitJS advance of SAMPLE_SECONDS. */
    private final class ProfileWork extends GeneratedWork<GeneratedRepairStatus> {
        private final CirSim ownerSim;
        private final GeneratedBoardInstance owner;
        private final Profile profile;
        private final Object graph;
        private final Vector<CircuitElm> graphElements;
        private final GeneratedChallengeController challenge;
        private final GeneratedBoardFamilyState familyState;
        private final GeneratedDiagnosticProvider provider;
        private final GeneratedTemporalBehavior temporal;
        private final GeneratedFaultBinding faultBinding;
        private final GeneratedChallengeDefinition definition;
        private final TroubleshootBoard ownerBoard;
        private final BoardSimulationBindings simulationBindings;
        private final PhysicalBoardRuntime runtime;
        private final Object mutationReceipt;
        private final BoardPowerController powerController;
        private final GeneratedExternalPowerBindings powerBindings;
        private final BoardModificationController modifications;
        private final GeneratedExternalPowerBindings.ControlObservation controlObservation;
        private final GeneratedExternalPowerBindings.SavedControls savedControls;
        private final Vector<String> powerInputIds;
        private final ExternalPowerSimulationBinding[] powerInputBindings;
        private final boolean[] powerInputInitiallyControlled, powerInputInitiallyConnected;
        private final long[] powerInputRevisions;
        private final LimitedDcSupplyElm[] powerInputSupplies;
        private final double[] powerInputLimits;
        private final BoardPowerState powerState;
        private final boolean physicalState;
        private final LimitedDcSupplyElm[] sensorSources;
        private final double[] sensorLimits;
        private final double expectedMaximumStep, expectedMinimumStep;
        private final boolean expectedAdaptiveStep;
        private final int priorInput;
        private int expectedInput;
        private final double[] expectedSensorVoltages;
        private int phase;
        private boolean passed = true;
        private boolean blocked;
        private boolean complete, cancelled;
        private GeneratedObservedBehavior localObserved;
        private GeneratedRepairStatus result;
        private final StringBuilder failures = new StringBuilder();

        ProfileWork(CirSim sim, GeneratedBoardInstance instance, Profile profile) {
            if (profile == null) throw new IllegalArgumentException("Missing Q30 profile");
            Rb30Behavior.this.requireCurrent(sim, instance);
            if (profile == Profile.HEALTHY && input != allInputMask)
                throw new IllegalStateException("Healthy Q30 proof requires the fresh HIGH input state");
            ownerSim = sim;
            owner = instance;
            this.profile = profile;
            graph = sim.elmList;
            graphElements = new Vector<CircuitElm>(sim.elmList);
            challenge = sim.getGeneratedChallengeController();
            familyState = instance.getFamilyState();
            provider = instance.getDiagnosticProvider();
            temporal = instance.getTemporalBehavior();
            faultBinding = instance.getFaultBinding();
            definition = instance.getChallengeDefinition();
            ownerBoard = instance.getBoard();
            simulationBindings = instance.getSimulationBindings();
            runtime = instance.getPhysicalBoardRuntime();
            powerController = sim.getBoardPowerController();
            powerBindings = instance.getExternalPowerBindings();
            modifications = sim.getBoardModificationController();
            if (powerController == null || powerBindings == null || modifications == null ||
                    runtime == null || powerController.getBindingsForDeveloperVerification() != powerBindings ||
                    modifications.getInstanceForRuntimeValidation() != instance || runtime.isMutationInProgress())
                throw new IllegalStateException("Q30 profile has incomplete or busy owner state");
            mutationReceipt = runtime.getLastMutationReceipt();
            powerState = powerController.getState();
            physicalState = modifications.isFullyRestored();
            controlObservation = powerBindings.observeControls();
            savedControls = powerBindings.saveControls();
            powerInputIds = new Vector<String>(ownerBoard.getPowerInputIds());
            powerInputBindings = new ExternalPowerSimulationBinding[powerInputIds.size()];
            powerInputInitiallyControlled = new boolean[powerInputIds.size()];
            powerInputInitiallyConnected = new boolean[powerInputIds.size()];
            powerInputRevisions = new long[powerInputIds.size()];
            powerInputSupplies = new LimitedDcSupplyElm[powerInputIds.size()];
            powerInputLimits = new double[powerInputIds.size()];
            for (int i = 0; i < powerInputIds.size(); i++) {
                ExternalPowerSimulationBinding binding = powerBindings.getBinding(powerInputIds.get(i));
                powerInputBindings[i] = binding;
                powerInputInitiallyControlled[i] = binding.hasControl();
                powerInputInitiallyConnected[i] = binding.isConnected();
                powerInputRevisions[i] = binding.getConnectionRevision();
                powerInputSupplies[i] = binding.getLimitedSupply();
                powerInputLimits[i] = powerInputSupplies[i] == null ? Double.NaN :
                    powerInputSupplies[i].getLimitAmps();
            }
            sensorSources = new LimitedDcSupplyElm[channels.length];
            sensorLimits = new double[channels.length];
            expectedSensorVoltages = new double[channels.length];
            for (int index = 0; index < channels.length; index++) {
                LimitedDcSupplyElm source = powerBindings.getBinding(
                    "SENSOR_" + channels[index]).getLimitedSupply();
                if (source == null || !instance.getSimulationElements().contains(source))
                    throw new IllegalStateException("Q30 profile lost its sensor source owner");
                sensorSources[index] = source;
                sensorLimits[index] = source.getLimitAmps();
            }
            priorInput = input;
            expectedInput = input;
            for (int index = 0; index < channels.length; index++) {
                expectedSensorVoltages[index] = sensorSources[index].maxVoltage;
                if (expectedSensorVoltages[index] !=
                        ((input & (1 << index)) != 0 ? 5 : 0))
                    throw new IllegalStateException(
                        "Q30 input state disagrees with its live sources");
            }
            if (profile == Profile.HEALTHY) {
                // Apply the same declared CircuitJS recipe before the first healthy sample.
                sim.timeStep = sim.maxTimeStep = SOLVER_MAX_STEP_SECONDS;
                sim.minTimeStep = SOLVER_MIN_STEP_SECONDS;
                sim.adjustTimeStep = true;
            }
            expectedMaximumStep = sim.maxTimeStep;
            expectedMinimumStep = sim.minTimeStep;
            expectedAdaptiveStep = sim.adjustTimeStep;
            blocked = profile == Profile.REPAIR &&
                !GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, instance);
            requireCurrent("begin");
        }

        boolean step() {
            if (cancelled) throw new IllegalStateException("Q30 profile cancelled");
            if (complete || phase >= profileWorkUnits) return false;
            requireCurrent("unit " + phase + " before");
            if (!blocked) {
                if (profile == Profile.HEALTHY) {
                    if (phase < allInputMask + 1) observeCondition(phase);
                    else if (passed) applyInput(0);
                } else if (profile == Profile.FAULTED) {
                    if (phase == 0) {
                        applyInput(allInputMask);
                        localObserved = healthy(owner, allInputMask) ? null :
                            GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING;
                    }
                } else if (phase < allInputMask + 1) {
                    observeCondition(phase);
                } else if (input != priorInput) {
                    applyInput(priorInput);
                }
            }
            phase++;
            requireCurrent("unit " + (phase - 1) + " after");
            return phase < profileWorkUnits;
        }

        private void observeCondition(int condition) {
            applyInput(condition);
            boolean matched = healthy(owner, condition);
            passed &= matched;
            if (!matched && profile == Profile.HEALTHY) appendFailureSnapshot(condition);
        }

        private void appendFailureSnapshot(int condition) {
            failures.append(" input=").append(condition).append(" inputName=")
                .append(inputName(condition));
            appendVoltage(" rail_U1_OUTPUT_to_U1_RETURN_V", owner,
                "U1.OUTPUT", "U1.RETURN");
            appendVoltage(" regulatorInput_U1_INPUT_to_J1_2_V", owner,
                "U1.INPUT", "J1.2");
            appendVoltage(" regulatorReturn_U1_RETURN_to_J1_2_V", owner,
                "U1.RETURN", "J1.2");
            appendVoltage(" main12V_J1_1_to_J1_2_V", owner, "J1.1", "J1.2");
            for (String channel : channels)
                appendChannelFailureSnapshot(channel);
        }

        private void appendChannelFailureSnapshot(String channel) {
            String upper = channel.toUpperCase();
            String sourceId = "SENSOR_" + upper;
            String inputConnector = "JS" + upper;
            String u2 = "U2" + upper;
            String q = "Q" + upper;
            String rDrive = "RD" + upper;
            String relayId = "K" + upper;

            failures.append(" channel=").append(upper);
            appendVoltage(" sensorSourceV", owner, inputConnector + ".1",
                inputConnector + ".2");
            failures.append(" sensorCommandV=")
                .append(sourceCommand(owner, sourceId));
            failures.append(" sensorSourceCurrentA=")
                .append(installedPartCurrent(owner, inputConnector));
            appendVoltage(" sensorNodeV", owner, u2 + ".SENSOR", u2 + ".RETURN");
            appendVoltage(" referenceNodeV", owner, u2 + ".REFERENCE", u2 + ".RETURN");
            appendVoltage(" controllerRailV", owner, u2 + ".RAIL", u2 + ".RETURN");
            double command = safeVoltage(owner, u2 + ".OUTPUT", u2 + ".RETURN");
            failures.append(" commandNodeV=").append(number(command))
                .append(" decisionOutputState=").append(decisionOutputState(command))
                .append(" decisionOutputCurrentA=")
                .append(decisionOutputCurrent(owner, u2));
            String referenceHigh = owner.getBoard().getComponent("RREF_H") != null ?
                "RREF_H" : "RREF_H" + upper;
            String referenceLow = owner.getBoard().getComponent("RREF_L") != null ?
                "RREF_L" : "RREF_L" + upper;
            failures.append(" referencePullupCurrentA=")
                .append(installedPartCurrent(owner, referenceHigh))
                .append(" referencePulldownCurrentA=")
                .append(installedPartCurrent(owner, referenceLow));
            failures.append(" driveResistorCurrentA=")
                .append(installedPartCurrent(owner, rDrive));
            appendDriverTerminals(channel, q);
            appendVoltage(" coilV", owner, relayId + ".A1", relayId + ".A2");
            failures.append(" coilCurrentA=").append(relayCurrent(owner, relayId))
                .append(" relayContactOpen=").append(relayContactOpen(owner, relayId))
                .append(" relayPosition=").append(relayPosition(owner, relayId));
        }

        private void appendDriverTerminals(String channel, String componentId) {
            PhysicalPart<?> part = owner.getPhysicalBoardRuntime()
                .getInstalledPart(componentId);
            if (part == null) {
                failures.append(" driver=unavailable");
                return;
            }
            Vector<String> terminals = part.getPackage().getTerminalIds();
            failures.append(" driverCurrentA=").append(installedPartCurrent(owner, componentId));
            for (String terminal : terminals)
                appendVoltage(" driver" + terminal + "V", owner,
                    componentId + "." + terminal, "J1.2");
        }

        private String inputName(int value) {
            if (value == 0) return "LOW";
            if (value == allInputMask) return "HIGH";
            if (value == 1) return "A_ONLY";
            if (value == 2) return "B_ONLY";
            return "UNKNOWN";
        }

        private void appendVoltage(StringBuilder target, String label,
                GeneratedBoardInstance owner, String positive, String negative) {
            target.append(label).append('=').append(number(safeVoltage(owner,
                positive, negative)));
        }

        private void appendVoltage(String label, GeneratedBoardInstance instance,
                String positive, String negative) {
            appendVoltage(failures, label, instance, positive, negative);
        }

        private double safeVoltage(GeneratedBoardInstance owner,
                String positive, String negative) {
            try { return voltage(owner, positive, negative); }
            catch (RuntimeException unavailable) { return Double.NaN; }
        }

        private String sourceCommand(GeneratedBoardInstance owner, String inputId) {
            try {
                LimitedDcSupplyElm source = owner.getExternalPowerBindings()
                    .getBinding(inputId).getLimitedSupply();
                return source == null ? "unavailable" : number(source.maxVoltage);
            } catch (RuntimeException unavailable) { return "unavailable"; }
        }

        private String installedPartCurrent(GeneratedBoardInstance owner,
                String componentId) {
            try {
                PhysicalPart<?> part = owner.getPhysicalBoardRuntime()
                    .getInstalledPart(componentId);
                if (part == null || part.getElectricalBacking() == null)
                    return "unavailable";
                Vector<CircuitElm> elements = part.getElectricalBacking().getCircuitElements();
                if (elements.isEmpty()) return "unavailable";
                return number(elements.firstElement().getCurrent());
            } catch (RuntimeException unavailable) { return "unavailable"; }
        }

        private String decisionOutputCurrent(GeneratedBoardInstance owner,
                String componentId) {
            try {
                PhysicalPart<?> part = owner.getPhysicalBoardRuntime()
                    .getInstalledPart(componentId);
                if (!(part instanceof E04DecisionControlPart)) return "unavailable";
                return number(((E04DecisionControlPart) part).getElement()
                    .getCurrentIntoNode(3));
            } catch (RuntimeException unavailable) { return "unavailable"; }
        }

        private String decisionOutputState(double outputVolts) {
            if (!PowerDomainContract.finite(outputVolts)) return "UNAVAILABLE";
            if (Math.abs(outputVolts) <= .05) return "LOW";
            if (outputVolts >= 1.0) return "HIGH";
            return "INTERMEDIATE";
        }

        private String relayCurrent(GeneratedBoardInstance owner, String componentId) {
            try {
                PhysicalPart<?> part = owner.getPhysicalBoardRuntime()
                    .getInstalledPart(componentId);
                if (!(part instanceof PhysicalRelayPart)) return "unavailable";
                return number(((PhysicalRelayPart) part).getElement().coilCurrent);
            } catch (RuntimeException unavailable) { return "unavailable"; }
        }

        private String relayContactOpen(GeneratedBoardInstance owner, String componentId) {
            try {
                PhysicalPart<?> part = owner.getPhysicalBoardRuntime()
                    .getInstalledPart(componentId);
                if (!(part instanceof PhysicalRelayPart)) return "unavailable";
                return Boolean.toString(((PhysicalRelayPart) part).getElement().contactOpen);
            } catch (RuntimeException unavailable) { return "unavailable"; }
        }

        private String relayPosition(GeneratedBoardInstance owner, String componentId) {
            try {
                PhysicalPart<?> part = owner.getPhysicalBoardRuntime()
                    .getInstalledPart(componentId);
                if (!(part instanceof PhysicalRelayPart)) return "unavailable";
                return Integer.toString(((PhysicalRelayPart) part).getElement().i_position);
            } catch (RuntimeException unavailable) { return "unavailable"; }
        }

        private String number(double value) {
            return PowerDomainContract.finite(value) ? Double.toString(value) : "NaN";
        }

        private void applyInput(int value) {
            if (value < 0 || value > allInputMask)
                throw new IllegalArgumentException("Unsupported Q30 input state");
            requireCurrent("input " + value + " before");
            expectedInput = value;
            for (int index = 0; index < channels.length; index++) {
                expectedSensorVoltages[index] =
                    (value & (1 << index)) != 0 ? 5 : 0;
                sensorSources[index].configure(expectedSensorVoltages[index],
                    sensorLimits[index]);
            }
            input = value;
            ownerSim.advanceGeneratedTemporalProfile(SAMPLE_SECONDS);
            requireCurrent("input " + value + " after");
        }

        GeneratedRepairStatus finish() {
            if (cancelled || phase < profileWorkUnits)
                throw new IllegalStateException("Q30 profile is incomplete");
            requireCurrent("finish");
            if (profile == Profile.HEALTHY) {
                if (!passed)
                    throw new IllegalStateException("Healthy Q30 failed " +
                        (1 << channels.length) + " input conditions:" + failures);
                result = GeneratedRepairStatus.CORRECTLY_RESTORED;
            } else if (profile == Profile.FAULTED) {
                observed = localObserved;
                result = GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            } else {
                result = !blocked && passed ? GeneratedRepairStatus.CORRECTLY_RESTORED :
                    GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            }
            complete = true;
            return result;
        }

        void cancel() {
            if (cancelled || complete) return;
            Throwable cleanupFailure = null;
            if (input != priorInput) {
                if (powerController.getState() == BoardPowerState.UNPOWERED) {
                    if (canRestorePriorInput()) restoreInputCommandWhileUnpowered();
                } else if (canRestorePriorInput()) {
                    try { applyInput(priorInput); }
                    catch (Throwable failure) { cleanupFailure = failure; }
                }
            }
            cancelled = true;
            if (cleanupFailure instanceof Error) throw (Error) cleanupFailure;
            if (cleanupFailure instanceof RuntimeException) throw (RuntimeException) cleanupFailure;
            if (cleanupFailure != null)
                throw new IllegalStateException("Q30 prior-input cleanup failed", cleanupFailure);
        }

        int getWorkUnits() { return profileWorkUnits; }

        private void requireCurrent(String stage) {
            if (!isCurrentOwnerIdentity() || powerController.getState() != powerState ||
                    modifications.isFullyRestored() != physicalState ||
                    !controlObservation.isCurrent() || !savedControls.matches() ||
                    input != expectedInput || !sensorCommandsAreCurrent() ||
                    !solverRecipeIsCurrent())
                throw new IllegalStateException("Q30 profile lost owner or controls at " + stage);
        }

        private boolean solverRecipeIsCurrent() {
            return ownerSim.maxTimeStep == expectedMaximumStep &&
                ownerSim.minTimeStep == expectedMinimumStep &&
                ownerSim.adjustTimeStep == expectedAdaptiveStep;
        }

        private boolean canRestorePriorInput() {
            try {
                if (!isCurrentOwnerIdentity() || modifications.isFullyRestored() != physicalState ||
                        !sensorCommandsAreCurrent() || !solverRecipeIsCurrent()) return false;
                BoardPowerState currentPower = powerController.getState();
                if (currentPower == BoardPowerState.UNPOWERED)
                    return powerController.isElectricallyUnpowered() &&
                        (powerState == BoardPowerState.UNPOWERED ? controlsAreUnchanged() :
                            isExactSinglePowerOffTransition());
                return currentPower == powerState && controlsAreUnchanged();
            } catch (Throwable ignored) { return false; }
        }

        private boolean controlsAreUnchanged() {
            return controlObservation.isCurrent() && savedControls.matches();
        }

        /**
         * A cancellation may restore only its own input source setpoints after
         * the user's single exact board-power disconnect transition. Keep this
         * exception narrow: all original external bindings must remain
         * identical, every input must have been connected, and each connection
         * revision must reflect exactly that one transition.
         */
        private boolean isExactSinglePowerOffTransition() {
            if (powerState != BoardPowerState.POWERED ||
                    powerController.getState() != BoardPowerState.UNPOWERED ||
                    !powerController.isElectricallyUnpowered() || !powerBindings.areAllDisconnected() ||
                    powerInputIds.isEmpty()) return false;
            for (int i = 0; i < powerInputIds.size(); i++) {
                ExternalPowerSimulationBinding binding = powerBindings.getBinding(powerInputIds.get(i));
                if (binding != powerInputBindings[i] || !powerInputInitiallyControlled[i] ||
                        !powerInputInitiallyConnected[i] || !binding.hasControl() || binding.isConnected() ||
                        powerInputRevisions[i] == Long.MAX_VALUE ||
                        binding.getConnectionRevision() != powerInputRevisions[i] + 1 ||
                        binding.getLimitedSupply() != powerInputSupplies[i]) return false;
                LimitedDcSupplyElm supply = powerInputSupplies[i];
                if (supply != null && supply.getLimitAmps() != powerInputLimits[i]) return false;
                if (supply == null && !Double.isNaN(powerInputLimits[i])) return false;
            }
            return true;
        }

        private boolean sensorCommandsAreCurrent() {
            if (input != expectedInput) return false;
            for (int index = 0; index < channels.length; index++)
                if (sensorSources[index].maxVoltage != expectedSensorVoltages[index] ||
                        sensorSources[index].getLimitAmps() != sensorLimits[index] ||
                        powerBindings.getBinding("SENSOR_" + channels[index])
                            .getLimitedSupply() != sensorSources[index])
                    return false;
            return true;
        }

        /** No solver or external-power command is touched while the board is off. */
        private void restoreInputCommandWhileUnpowered() {
            for (int index = 0; index < channels.length; index++) {
                double volts = (priorInput & (1 << index)) != 0 ? 5 : 0;
                sensorSources[index].maxVoltage = volts;
                expectedSensorVoltages[index] = volts;
            }
            input = priorInput;
            expectedInput = priorInput;
        }

        private boolean isCurrentOwnerIdentity() {
            return CircuitElm.sim == ownerSim && ownerSim.getGeneratedBoardInstance() == owner &&
                ownerSim.elmList == graph && graphElementsUnchanged() &&
                ownerSim.getGeneratedChallengeController() == challenge &&
                ownerSim.getBoardPowerController() == powerController &&
                ownerSim.getBoardModificationController() == modifications &&
                ownerSim.getBoardModificationController().getInstanceForRuntimeValidation() == owner &&
                ownerSim.getBoardPowerController().getBindingsForDeveloperVerification() == powerBindings &&
                owner.getBoard() == ownerBoard && owner.getSimulationBindings() == simulationBindings &&
                owner.getExternalPowerBindings() == powerBindings && owner.getPhysicalBoardRuntime() == runtime &&
                runtime.getLastMutationReceipt() == mutationReceipt && !runtime.isMutationInProgress() &&
                owner.getFamilyState() == familyState && owner.getDiagnosticProvider() == provider &&
                owner.getTemporalBehavior() == temporal && owner.getFaultBinding() == faultBinding &&
                owner.getChallengeDefinition() == definition && !ownerSim.activeMeasurementOverlay;
        }

        private boolean graphElementsUnchanged() {
            if (ownerSim.elmList.size() != graphElements.size()) return false;
            for (int i = 0; i < graphElements.size(); i++)
                if (ownerSim.elmList.get(i) != graphElements.get(i)) return false;
            return true;
        }
    }
    public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) {
        if (state != BoardPowerState.POWERED || !healthy(owner, input))
            throw new IllegalStateException("Q30 healthy function absent");
    }
    public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController modifications,
            BoardPowerState state) {
        requireOwnedBy(owner);
        if (state != BoardPowerState.POWERED || observed == null ||
                healthy(owner, allInputMask))
            throw new IllegalStateException("Q30 fault has no measured symptom");
    }
    public void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state) {
        verifyFaulted(owner, modifications, state);
    }
    public GeneratedObservedBehavior getObservedBehavior() { return observed; }
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state, boolean overlay) {
        return !overlay && state == BoardPowerState.POWERED && modifications.isFullyRestored() &&
            healthy(owner, input) ? GeneratedRepairStatus.CORRECTLY_RESTORED :
            GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
    }
    public GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state, boolean overlay) {
        // Live status is observational, as in E03. Only an explicit customer
        // retest or temporal profile may drive the four-condition input recipe.
        return getRepairStatus(owner, modifications, state, overlay);
    }
    public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state, boolean overlay) {
        return getRepairStatus(owner, modifications, state, overlay) == GeneratedRepairStatus.CORRECTLY_RESTORED;
    }
    GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> result =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        result.add(new GeneratedScenario<GeneratedObservedBehavior>("RB30_OUTPUT_NOT_TRACKING",
            "OUTPUT_NOT_TRACKING", "One or both loads fail to follow their sensor inputs. Check each channel separately and together.",
            GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                public boolean matches(GeneratedBoardInstance owner, BoardModificationController modifications,
                        BoardPowerState state, GeneratedObservedBehavior expected) {
                    return state == BoardPowerState.POWERED && observed == expected;
                }
            }));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(result, this);
    }
}
