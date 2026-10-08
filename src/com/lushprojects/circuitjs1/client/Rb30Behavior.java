package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Finite Q30/RB56 input recipes; one guarded profile owner and current CircuitJS graph. */
final class Rb30Behavior implements GeneratedBoardFamilyState,
        GeneratedChallengeBehaviorContract, GeneratedTemporalBehavior,
        GeneratedLiveTemporalSimulation {
    static final String SENSORS_LOW = "SENSORS_LOW";
    static final String SENSORS_A_ONLY = "SENSORS_A_ONLY";
    static final String SENSORS_B_ONLY = "SENSORS_B_ONLY";
    static final String SENSORS_HIGH = "SENSORS_HIGH";
    static final double SAMPLE_SECONDS = .030;
    static final double SOLVER_MAX_STEP_SECONDS = 5e-6;
    static final double RB56_MAX_STEP_SECONDS = E06ConverterContract.MAX_AVERAGED_STEP_SECONDS;
    static final double SOLVER_MIN_STEP_SECONDS = 50e-12;
    static final double LIVE_POWERED_SECONDS = .0001;
    static final double LIVE_ISOLATED_SECONDS = .005;
    private double maximumStepSeconds() {
        return recipe == Recipe.RB56 ? RB56_MAX_STEP_SECONDS : SOLVER_MAX_STEP_SECONDS;
    }
    private enum Recipe { RB30, RB56 }
    private static final int RB56_STARTUP_UNITS = 8;
    private static final double RB56_STARTUP_UNIT_SECONDS = .050;
    private final Recipe recipe;
    private final int initialInput;
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
        this(candidate.board(), candidate.assembly.power, candidate.plan.channels(), Recipe.RB30);
    }

    static Rb30Behavior forRb56(TroubleshootBoard board,
            GeneratedExternalPowerBindings power, String[] channels) {
        return new Rb30Behavior(board, power, channels, Recipe.RB56);
    }

    private Rb30Behavior(TroubleshootBoard board, GeneratedExternalPowerBindings power,
            String[] channels, Recipe recipe) {
        if (board == null || power == null || channels == null ||
                channels.length < 1 || channels.length > 2 || !"A".equals(channels[0]) ||
                channels.length == 2 && !"B".equals(channels[1]) ||
                power.getBoardForRuntimeValidation() != board ||
                recipe == Recipe.RB56 && !(Rb56Plan.FAMILY_ID + "_BOARD").equals(board.getId()))
            throw new IllegalArgumentException("Missing or foreign channel behavior construction");
        this.recipe = recipe;
        this.board = board;
        this.power = power;
        this.channels = new String[channels.length];
        System.arraycopy(channels, 0, this.channels, 0, channels.length);
        allInputMask = (1 << channels.length) - 1;
        profileWorkUnits = (1 << channels.length) + 1 + startupUnits();
        retestWorkUnits = profileWorkUnits;
        initialInput = recipe == Recipe.RB56 ? 0 : allInputMask;
        input = initialInput;
        if (channels.length == 1) {
            addInput(SENSORS_LOW, "Set sensor A LOW", 0);
            addInput(SENSORS_HIGH, "Set sensor A HIGH", 1);
        } else {
            addInput(SENSORS_LOW, "Set both sensors LOW", 0);
            addInput(SENSORS_A_ONLY, "Set sensor A HIGH, B LOW", 1);
            addInput(SENSORS_B_ONLY, "Set sensor A LOW, B HIGH", 2);
            addInput(SENSORS_HIGH, "Set both sensors HIGH", 3);
        }
        retest = new GeneratedCustomerRetestProfile(recipe == Recipe.RB56 ?
            "RB56_CUSTOMER_RETEST" : "RB30_CUSTOMER_RETEST",
            channels.length == 1 ?
                "Check that the external load follows sensor A at LOW and HIGH." :
                "Check that each external load follows its own sensor, including both loads together.",
            recipe == Recipe.RB56 ?
                (channels.length == 1 ?
                    "Connect the AC input, isolated load supply and sensor A." :
                    "Connect the AC input, isolated load supply and both sensor inputs.") :
                (channels.length == 1 ?
                    "Connect the 12 V main and isolated load supplies and sensor A." :
                    "Connect the 12 V main and isolated load supplies and both sensor inputs."),
            channels.length == 1 ? "LOW, HIGH; restore the previous input." :
                "Both LOW, A only, B only, both HIGH; restore the previous inputs.",
            channels.length == 1 ?
                "Observe output A across its two-terminal load connector." :
                "Observe each output across its own two-terminal load connector.",
            recipe == Recipe.RB56 ?
                "Allow 400 ms for the power rails, then 30 ms after each input change." :
                "Allow 30 ms of CircuitJS time after each input change.",
            recipe == Recipe.RB56 ?
                "Simulated AC input and isolated low-voltage load supply; all serviced leads reconnected." :
                (channels.length == 1 ?
                    "Low-voltage DC, one 180 ohm external load; all serviced leads reconnected." :
                    "Low-voltage DC, two 180 ohm external loads; all serviced leads reconnected."),
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

    private int startupUnits() { return recipe == Recipe.RB56 ? RB56_STARTUP_UNITS : 0; }
    private String controlReturnPad() { return recipe == Recipe.RB56 ? "U1.RETURN" : "J1.2"; }

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
        if (recipe == Recipe.RB56 && !Rb56Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()))
            throw new IllegalArgumentException("Foreign RB56 behavior family");
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
        if (recipe == Recipe.RB56) {
            try { return healthyRb56(owner, condition); }
            catch (RuntimeException unavailable) { return false; }
        }
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

    /** Live status is provisional; explicit functional profiles additionally require accepted windows. */
    private boolean healthyRb56(GeneratedBoardInstance owner, int condition) {
        if (!currentRb56PartsSupported(owner)) return false;
        double rail12 = voltage(owner, "COUT.+", "COUT.-");
        double rail5 = voltage(owner, "U1.OUTPUT", "U1.RETURN");
        if (rail12 < 10.64 || rail12 > 11.76 || rail5 < 4.75 || rail5 > 5.25) return false;
        for (int index = 0; index < channels.length; index++)
            if (!outputMatches(harnessVoltage(owner, "JO" + channels[index]),
                    (condition & (1 << index)) != 0)) return false;
        return true;
    }

    private boolean currentRb56PartsSupported(GeneratedBoardInstance owner) {
        PhysicalPart<?> converter = currentInstalled(owner, "UAC");
        PhysicalPart<?> regulator = currentInstalled(owner, "U1");
        if (!(converter instanceof PhysicalConverterPart) || !(regulator instanceof PhysicalRegulatorPart) ||
                !(((PhysicalRegulatorPart)regulator).getElement() instanceof LinearRegulatorElm)) return false;
        PhysicalConverterPart module = (PhysicalConverterPart)converter;
        module.getSpecification().requireModel(module.getModule());
        for (int index = 0; index < channels.length; index++) {
            String channel = channels[index];
            PhysicalPart<?> decision = currentInstalled(owner, "U2" + channel);
            PhysicalPart<?> relay = currentInstalled(owner, "K" + channel);
            PhysicalPart<?> driver = currentInstalled(owner, "Q" + channel);
            PhysicalPart<?> connector = currentInstalled(owner, "JO" + channel);
            if (!(decision instanceof E04DecisionControlPart) || !(relay instanceof PhysicalRelayPart) ||
                    !(driver instanceof PhysicalNpnPart) && !(driver instanceof PhysicalNmosPart) ||
                    !(connector instanceof PhysicalServicePart) || !((PhysicalServicePart)connector).isConnector())
                return false;
            currentHarness(owner, "JO" + channel);
        }
        return true;
    }

    /** Resolve every sample from the present slot/backing/terminal bindings, never original model maps. */
    private static PhysicalPart<?> currentInstalled(GeneratedBoardInstance owner, String id) {
        CirSim sim = CircuitElm.sim;
        if (sim == null || sim.getGeneratedBoardInstance() != owner || sim.elmList == null) return null;
        Vector<CircuitElm> live = sim.elmList;
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        PhysicalPart<?> part = runtime.getInstalledPart(id);
        PhysicalBoardSlot slot = runtime.getSlot(id);
        if (part == null || slot == null || slot.getInstalledPart() != part || part.getBoardSlot() != slot ||
                !part.isInstalled() || runtime.getPart(part.getId()) != part ||
                !runtime.isPartOwnedByRegisteredProvider(part)) return null;
        Vector<CircuitElm> backing = part.getElectricalBacking().getCircuitElements();
        Vector<CircuitElm> bound = owner.getComponentBindings().getElements(id);
        bound.addAll(owner.getComponentBindings().getAuxiliaryElements(id));
        if (backing.isEmpty() || !bound.equals(backing)) return null;
        for (CircuitElm element : backing)
            if (!owner.ownsRuntimeSimulationElement(element) || !live.contains(element)) return null;
        if (owner.getConnectionBindings().getForComponent(id).size() != part.getTerminalCount()) return null;
        for (PhysicalPartTerminal terminal : part.getTerminals()) {
            GeneratedComponentConnectionBinding binding = owner.getConnectionBindings().get(id,
                id + "." + terminal.getTerminalName());
            if (!GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), terminal.getEndpoint()) ||
                    !GeneratedComponentConnectionBindings.sameEndpoint(binding.getBoardEndpoint(),
                        owner.getSimulationBindings().getEndpoint(binding.getPadId())) ||
                    !live.contains(binding.getConnectionElement())) return null;
        }
        return part;
    }

    /** The customer load remains outside the replaceable header; its cable endpoints are the output. */
    private static CircuitPostMeasurementEndpoint[] currentHarness(GeneratedBoardInstance owner, String id) {
        CirSim sim = CircuitElm.sim;
        CircuitPostMeasurementEndpoint[] ends = owner.getConnectionBindings().getConnectorHarness(id);
        if (sim == null || sim.getGeneratedBoardInstance() != owner || sim.elmList == null || ends == null || ends.length != 2 || ends[0].getElement() != ends[1].getElement() ||
                !(ends[0].getElement() instanceof BoundedExternalLoadElm) ||
                ends[0].getPostIndex() != 0 || ends[1].getPostIndex() != 1 ||
                !owner.ownsRuntimeSimulationElement(ends[0].getElement()) ||
                !sim.elmList.contains(ends[0].getElement()))
            throw new IllegalStateException("Missing current external load harness: " + id);
        return ends;
    }

    private static double harnessVoltage(GeneratedBoardInstance owner, String id) {
        CircuitPostMeasurementEndpoint[] ends = currentHarness(owner, id);
        double value = ends[0].getElement().getPostVoltage(0) - ends[1].getElement().getPostVoltage(1);
        if (!PowerDomainContract.finite(value)) throw new IllegalStateException("Non-finite external load voltage");
        return value;
    }

    /** Statistics of accepted values over the last 10 ms, weighted by actual step duration. */
    private static final class Rb56ObservedWindow {
        private static final double SECONDS = .010, HALF_SECONDS = .005, TIME_EPSILON = 1e-12;
        private double duration, firstDuration, secondDuration, sum, firstSum, secondSum;
        private double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        static Rb56ObservedWindow read(SolverTimeSample[] samples, double end) {
            double start = end - SECONDS, middle = end - HALF_SECONDS;
            if (samples == null || samples.length < 3 || !PowerDomainContract.finite(end) ||
                    samples[0].getTime() > start + TIME_EPSILON ||
                    Math.abs(samples[samples.length - 1].getTime() - end) > TIME_EPSILON)
                throw new IllegalStateException("RB56 accepted observation window has insufficient duration");
            Rb56ObservedWindow result = new Rb56ObservedWindow();
            for (int index = 1; index < samples.length; index++) {
                double previous = samples[index - 1].getTime(), current = samples[index].getTime();
                if (current <= start || previous >= end) continue;
                double gap = current - previous;
                if (!PowerDomainContract.finite(gap) || gap <= 0 || gap > RB56_MAX_STEP_SECONDS + TIME_EPSILON)
                    throw new IllegalStateException("RB56 accepted observation window exceeds its step gap");
                double from = Math.max(start, previous), to = Math.min(end, current);
                double value = samples[index].getValue();
                double weight = to - from;
                // Right-endpoint duration weighting equals the frozen equal-step sample mean.
                result.duration += weight; result.sum += value * weight;
                result.min = Math.min(result.min, value); result.max = Math.max(result.max, value);
                double firstWeight = Math.max(0, Math.min(to, middle) - from);
                double secondWeight = weight - firstWeight;
                result.firstDuration += firstWeight; result.firstSum += value * firstWeight;
                result.secondDuration += secondWeight; result.secondSum += value * secondWeight;
            }
            if (Math.abs(result.duration - SECONDS) > TIME_EPSILON ||
                    Math.abs(result.firstDuration - HALF_SECONDS) > TIME_EPSILON ||
                    Math.abs(result.secondDuration - HALF_SECONDS) > TIME_EPSILON ||
                    !PowerDomainContract.finite(result.min) || !PowerDomainContract.finite(result.max))
                throw new IllegalStateException("RB56 accepted observation window is incomplete");
            return result;
        }
        double mean() { return sum / duration; }
        double ripple() { return (max - min) / Math.abs(mean()); }
        double drift() { return Math.abs(firstSum / firstDuration - secondSum / secondDuration) / Math.abs(mean()); }
    }

    private static boolean outputMatches(double volts, boolean on) {
        return on ? volts >= 10.8 && volts <= 12.6 : Math.abs(volts) <= .05;
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance owner, String id) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(owner, id);
    }
    public String getSessionInputSignature() {
        StringBuilder out = new StringBuilder(recipe == Recipe.RB56 ? "RB56_INPUTS@1:" : "RB30_INPUTS@1:");
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
        values.put("fault-preparation", recipe == Recipe.RB56 ?
            (channels.length == 1 ?
                "healthy-two-conditions-then-LOW-apply-fault-settle-0.4s-then-HIGH" :
                "healthy-four-conditions-then-LOW-apply-fault-settle-0.4s-then-HIGH") :
            (channels.length == 1 ?
                "healthy-two-conditions-then-LOW-apply-fault-then-HIGH" :
                "healthy-four-conditions-then-LOW-apply-fault-then-HIGH"));
        values.put("sample-seconds", Double.toString(SAMPLE_SECONDS));
        values.put("live-powered-seconds", Double.toString(LIVE_POWERED_SECONDS));
        values.put("live-isolated-seconds", Double.toString(LIVE_ISOLATED_SECONDS));
        values.put("qualification-solver", "CircuitJS-adaptive");
        values.put("qualification-maximum-step-seconds", Double.toString(maximumStepSeconds()));
        values.put("qualification-minimum-step-seconds", Double.toString(SOLVER_MIN_STEP_SECONDS));
        values.put("profile-work-units", Integer.toString(profileWorkUnits));
        values.put("customer-retest-work-units", Integer.toString(retestWorkUnits));
        values.put("active-channels", joinChannels());
        values.put("condition-count", Integer.toString(1 << channels.length));
        values.put("rail-range-volts", "4.75..5.25");
        if (recipe == Recipe.RB56) {
            values.put("fresh-input", "LOW");
            values.put("startup-units", Integer.toString(RB56_STARTUP_UNITS));
            values.put("startup-unit-seconds", Double.toString(RB56_STARTUP_UNIT_SECONDS));
            values.put("startup-total-seconds", "0.4");
            values.put("startup-profiles", "HEALTHY,FAULTED,REPAIR");
            values.put("rail12-target", "COUT.+-COUT.-");
            values.put("rail12-range-volts", "10.64..11.76");
            values.put("rail5-target", "U1.OUTPUT-U1.RETURN");
            values.put("functional-observation-window-seconds", "0.01");
            values.put("functional-observation-window", "completed-accepted-steps;right-endpoint-time-weighted;5ms-halves");
            values.put("functional-observation-capacity", Integer.toString(SolverTimeObservationService.MAX_CAPACITY));
            values.put("rail12-ripple-maximum", "0.05");
            values.put("rail12-half-drift-maximum", "0.02");
            values.put("rail5-and-loads", "accepted-window-minimum-maximum");
            values.put("outputs-owner", "current-connector-external-load-harness");
            values.put("required-current-parts", "UAC,U1,U2-channel,K-channel,Q-channel,JO-channel");
        }
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
        return new GeneratedTemporalDependency(recipe == Recipe.RB56 ? "RB56_CHANNEL_FUNCTION" : "RB30_CHANNEL_FUNCTION",
            recipe == Recipe.RB56 ? 1 : 3,
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

    /** One guarded unit performs one bounded family startup or 30 ms sensor advance at most. */
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
            if (profile == Profile.HEALTHY && input != initialInput)
                throw new IllegalStateException(recipe == Recipe.RB56 ?
                    "Healthy RB56 proof requires the fresh LOW input state" :
                    "Healthy Q30 proof requires the fresh HIGH input state");
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
                sim.timeStep = sim.maxTimeStep = maximumStepSeconds();
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
                int conditionPhase = phase - startupUnits();
                if (conditionPhase < 0) {
                    // One answer-blind settling recipe covers cold startup, post-fault
                    // storage decay and repaired cold rails without inspecting the fault.
                    ownerSim.advanceGeneratedTemporalProfile(RB56_STARTUP_UNIT_SECONDS);
                    requireCurrent("startup " + phase + " after");
                } else if (profile == Profile.HEALTHY) {
                    if (conditionPhase < allInputMask + 1) observeCondition(conditionPhase);
                    else if (passed) applyInput(0);
                } else if (profile == Profile.FAULTED) {
                    if (conditionPhase == 0) {
                        if (recipe == Recipe.RB56) {
                            Boolean matched = observeRb56Input(allInputMask);
                            localObserved = matched == null || matched.booleanValue() ? null :
                                GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING;
                        } else {
                            applyInput(allInputMask);
                            localObserved = healthy(owner, allInputMask) ? null :
                                GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING;
                        }
                    }
                } else if (conditionPhase < allInputMask + 1) {
                    observeCondition(conditionPhase);
                } else if (input != priorInput) {
                    applyInput(priorInput);
                }
            }
            phase++;
            requireCurrent("unit " + (phase - 1) + " after");
            return phase < profileWorkUnits;
        }

        private void observeCondition(int condition) {
            if (recipe == Recipe.RB56) {
                Boolean observed = observeRb56Input(condition);
                boolean matched = Boolean.TRUE.equals(observed);
                passed = passed && matched;
                if (!matched && profile == Profile.HEALTHY) appendFailureSnapshot(condition);
                return;
            }
            applyInput(condition);
            boolean matched = healthy(owner, condition);
            passed &= matched;
            if (!matched && profile == Profile.HEALTHY) appendFailureSnapshot(condition);
        }

        /** One existing 30 ms advance; no retained subscriptions or extra solver work. */
        private Boolean observeRb56Input(int condition) {
            requireCurrent("RB56 observation before");
            if (!rb56PartsAvailable()) return null;
            SolverTimeObservationService.Subscription[] samples =
                new SolverTimeObservationService.Subscription[2 + channels.length];
            try {
                samples[0] = observeEndpoints(observationEndpoint("COUT.+"), observationEndpoint("COUT.-"));
                samples[1] = observeEndpoints(observationEndpoint("U1.OUTPUT"), observationEndpoint("U1.RETURN"));
                for (int index = 0; index < channels.length; index++) {
                    CircuitPostMeasurementEndpoint[] ends = currentHarness(owner, "JO" + channels[index]);
                    samples[2 + index] = observeEndpoints(ends[0], ends[1]);
                }
                applyInput(condition);
                requireCurrent("RB56 observation after");
                if (!rb56PartsAvailable()) return null;
                double end = ownerSim.t;
                Rb56ObservedWindow rail12 = Rb56ObservedWindow.read(samples[0].snapshot(), end);
                Rb56ObservedWindow rail5 = Rb56ObservedWindow.read(samples[1].snapshot(), end);
                boolean matched = rail12.mean() >= 10.64 && rail12.mean() <= 11.76 &&
                    rail12.ripple() <= .05 && rail12.drift() <= .02 &&
                    rail5.min >= 4.75 && rail5.max <= 5.25;
                for (int index = 0; index < channels.length; index++) {
                    Rb56ObservedWindow load = Rb56ObservedWindow.read(samples[2 + index].snapshot(), end);
                    boolean on = (condition & (1 << index)) != 0;
                    matched = matched && (on ? load.min >= 10.8 && load.max <= 12.6 :
                        Math.max(Math.abs(load.min), Math.abs(load.max)) <= .05);
                }
                return Boolean.valueOf(matched);
            } finally {
                // Failed analysis/advance/coverage has no waveform to publish as a symptom.
                for (SolverTimeObservationService.Subscription subscription : samples)
                    ownerSim.solverTimeObservations.unsubscribe(subscription);
            }
        }

        private boolean rb56PartsAvailable() {
            try { return currentRb56PartsSupported(owner); }
            catch (RuntimeException unsupportedOwnerOrModel) { return false; }
        }

        private CircuitPostMeasurementEndpoint observationEndpoint(String padId) {
            CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings().getEndpoint(padId);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Missing RB56 observation endpoint: " + padId);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint)endpoint;
            if (!owner.ownsRuntimeSimulationElement(post.getElement()) || !ownerSim.elmList.contains(post.getElement()))
                throw new IllegalStateException("Stale RB56 observation endpoint: " + padId);
            return post;
        }

        private SolverTimeObservationService.Subscription observeEndpoints(CircuitPostMeasurementEndpoint red,
                CircuitPostMeasurementEndpoint black) {
            return ownerSim.solverTimeObservations.subscribe(red, black,
                SolverTimeObservationService.MAX_CAPACITY, false);
        }

        private void appendFailureSnapshot(int condition) {
            failures.append(" input=").append(condition).append(" inputName=")
                .append(inputName(condition));
            appendVoltage(" rail_U1_OUTPUT_to_U1_RETURN_V", owner,
                "U1.OUTPUT", "U1.RETURN");
            if (recipe == Recipe.RB56) {
                appendVoltage(" regulatorInput_U1_INPUT_to_U1_RETURN_V", owner, "U1.INPUT", "U1.RETURN");
                appendVoltage(" converted12V_COUT_positive_to_negative_V", owner, "COUT.+", "COUT.-");
                appendVoltage(" primaryBulk_CBULK_positive_to_negative_V", owner, "CBULK.+", "CBULK.-");
            } else {
                appendVoltage(" regulatorInput_U1_INPUT_to_J1_2_V", owner,
                    "U1.INPUT", "J1.2");
                appendVoltage(" regulatorReturn_U1_RETURN_to_J1_2_V", owner,
                    "U1.RETURN", "J1.2");
                appendVoltage(" main12V_J1_1_to_J1_2_V", owner, "J1.1", "J1.2");
            }
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
                    componentId + "." + terminal, controlReturnPad());
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
        result.add(new GeneratedScenario<GeneratedObservedBehavior>(recipe == Recipe.RB56 ?
            "RB56_OUTPUT_NOT_TRACKING" : "RB30_OUTPUT_NOT_TRACKING",
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
