package com.lushprojects.circuitjs1.client;

/** Pure JDK contract checks for the immutable temporal dependency envelope. */
public final class A10DependencyContractTest {
    private static int assertions;

    private A10DependencyContractTest() { }

    public static void main(String[] args) {
        framingAndFieldOrder();
        equalValuesCompareByCanonical();
        recipeChangesInvalidate();
        healthyReferenceChangesInvalidate();
        missingInitialStateFailsClosed();
        nonPositiveRecipeFailsClosed();
        scenarioMetadataInvalidates();
        System.out.println("PASS: A10 dependency contracts assertions=" + assertions);
    }

    private static void framingAndFieldOrder() {
        GeneratedTemporalDependency value = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .100, .001, 1.25, 4.25);
        String expected = frame("generated-temporal-dependency-v1") +
            field("behavior.id", "RC_DELAY_TEMPORAL") +
            field("behavior.version", "1") +
            field("initial-state.contract",
                "owner=new-generated-board;graph=fresh;solver.t=0;solver.timeStepCount=0;" +
                "solver.timeStepAccum=0;eventQueue=empty;requiresAnalysis=true") +
            field("endpoint.output", "J2.1") +
            field("endpoint.ground", "J2.2") +
            field("nominal-supply.bits", bits(5)) +
            field("time.player-reselect.bits", bits(.120)) +
            field("time.natural-discharge.bits", bits(1.000)) +
            field("time.early-sample.bits", bits(.100)) +
            field("time.late-sample.bits", bits(.700)) +
            field("time.max-solver-advance.bits", bits(.750)) +
            field("time.live-solver-advance.bits", bits(.005)) +
            field("threshold.residual.bits", bits(.25)) +
            field("threshold.healthy-rise-min.bits", bits(.20)) +
            field("threshold.healthy-late-min.bits", bits(.25)) +
            field("threshold.healthy-early-max.bits", bits(.65)) +
            field("threshold.classification-rise-min.bits", bits(.15)) +
            field("threshold.classification-late-max.bits", bits(.85)) +
            field("threshold.classification-early-difference.bits", bits(.45)) +
            field("threshold.classification-healthy-early-difference.bits", bits(.30)) +
            field("threshold.classification-healthy-late-difference.bits", bits(.15)) +
            field("reference.healthy-residual.bits", bits(.001)) +
            field("reference.healthy-early.bits", bits(1.25)) +
            field("reference.healthy-late.bits", bits(4.25));
        check(value.canonical().equals(expected),
            "temporal canonical uses the independent framed field oracle");
        check(value.canonical().indexOf("sim.t") < 0 &&
                value.canonical().indexOf("voltdiff") < 0 &&
                value.canonical().indexOf("queue") < 0,
            "temporal canonical excludes live solver state");
    }

    private static void equalValuesCompareByCanonical() {
        GeneratedTemporalDependency first = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .100, .001, 1.25, 4.25);
        GeneratedTemporalDependency second = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .100, .001, 1.25, 4.25);
        check(first != second && first.equals(second), "equal temporal values compare equal");
        check(first.hashCode() == second.hashCode(), "equal temporal values share a hash");
    }

    private static void recipeChangesInvalidate() {
        GeneratedTemporalDependency baseline = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .100, .001, 1.25, 4.25);
        GeneratedTemporalDependency changed = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .125, .001, 1.25, 4.25);
        check(!baseline.equals(changed) &&
                !baseline.canonical().equals(changed.canonical()),
            "recipe timing changes invalidate temporal identity");
    }

    private static void healthyReferenceChangesInvalidate() {
        GeneratedTemporalDependency baseline = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .100, .001, 1.25, 4.25);
        GeneratedTemporalDependency changed = value(
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, 5, .7,
            .100, .001, 1.25, 4.50);
        check(!baseline.equals(changed) &&
                !baseline.canonical().equals(changed.canonical()),
            "healthy temporal reference changes invalidate identity");
    }

    private static void missingInitialStateFailsClosed() {
        boolean rejected = false;
        try {
            value(null, 5, .7, .100, .001, 1.25, 4.25);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "missing initial-state contract fails closed");
    }

    private static void nonPositiveRecipeFailsClosed() {
        boolean rejected = false;
        try {
            value(GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1,
                0, .7, .100, .001, 1.25, 4.25);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "non-positive temporal recipe input fails closed");
    }

    private static GeneratedTemporalDependency value(String initialState,
            double nominalSupply, double lateSample, double earlySample,
            double healthyResidual, double healthyEarly, double healthyLate) {
        return new GeneratedTemporalDependency("RC_DELAY_TEMPORAL", 1, initialState,
            "J2.1", "J2.2", nominalSupply, .120, 1.000, earlySample,
            lateSample, .750, .005, .25, .20, .25, .65, .15, .85, .45,
            .30, .15, healthyResidual, healthyEarly, healthyLate);
    }

    private static void scenarioMetadataInvalidates() {
        GeneratedScenarioCompatibility<GeneratedObservedBehavior> compatible =
            new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                public boolean matches(GeneratedBoardInstance instance,
                        BoardModificationController modifications, BoardPowerState state,
                        GeneratedObservedBehavior behavior) { return true; }
            };
        GeneratedScenario<GeneratedObservedBehavior> first = new GeneratedScenario<GeneratedObservedBehavior>(
            "scenario", "complaint", "Check the indicator.", GeneratedObservedBehavior.DARK_INDICATOR, compatible);
        String identity = GenerationDependencyContext.scenarioIdentity(first);
        check(identity.equals(GenerationDependencyContext.scenarioIdentity(first.withComplaintText(
            "Check the indicator."))), "fresh equal scenario metadata has equal dependency identity");
        check(!identity.equals(GenerationDependencyContext.scenarioIdentity(first.withComplaintText(
            "Check the output."))), "changed complaint invalidates scenario dependency");
        GeneratedScenario<GeneratedObservedBehavior> changedBehavior = new GeneratedScenario<GeneratedObservedBehavior>(
            "scenario", "complaint", "Check the indicator.", GeneratedObservedBehavior.ASYMMETRIC_INDICATORS, compatible);
        check(!identity.equals(GenerationDependencyContext.scenarioIdentity(changedBehavior)),
            "changed observed behavior invalidates scenario dependency");
        java.util.Vector<GeneratedScenario<GeneratedObservedBehavior>> entries =
            new java.util.Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        entries.add(first);
        GeneratedScenarioCatalog<GeneratedObservedBehavior> catalog =
            new GeneratedScenarioCatalog<GeneratedObservedBehavior>(entries);
        entries.clear(); catalog.getCandidates().clear();
        check(catalog.getCandidates().size() == 1 && catalog.getCandidates().firstElement() == first,
            "catalog constructor and dependency access preserve immutable membership");
    }

    private static String field(String key, String value) {
        return "F" + frame(key) + frame(value);
    }

    private static String frame(String value) {
        return value.length() + ":" + value;
    }

    private static String bits(double value) {
        return Long.toHexString(Double.doubleToLongBits(value));
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
