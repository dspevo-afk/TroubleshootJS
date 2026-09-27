package com.lushprojects.circuitjs1.client;

/** Native contracts for bounded, exact-owner physical service preparation. */
public final class DiagnosticServicePreparationContractTest {
    private static int assertions;
    private static final double UNIT_SECONDS = .050;

    private DiagnosticServicePreparationContractTest() { }

    public static void main(String[] args) {
        assertions = 0;
        GeneratedDiagnosticServicePreparation.Policy policy =
            new GeneratedDiagnosticServicePreparation.Policy(5, UNIT_SECONDS);
        verifyImmutablePolicy(policy);
        verifyAlreadyReadyConsumesBoundWithoutAdvancing(policy);
        verifyDelayedReadinessUsesRealBoundedAdvances(policy);
        verifyNeverReadyFailsAtBound(policy);
        verifyAvailabilityExpiresBeforeDispatch(policy);
        verifyIncompleteAndStaleCursorsNeverAdvanceOnCancel(policy);
        System.out.println("PASS: diagnostic service preparation contracts " +
            assertions + " assertions policy=" + policy.canonical());
    }

    private static void verifyImmutablePolicy(
            GeneratedDiagnosticServicePreparation.Policy policy) {
        check(policy.getWorkUnits() == 5 &&
                policy.getMaximumAdvanceSeconds() == UNIT_SECONDS,
            "policy exposes its fixed five-unit, 50-ms cap");
        check(policy.canonical().equals(
                "action=REMOVE;units=5;maximumAdvanceSeconds=0.05;readiness=exact-current-owner"),
            "policy canonical includes operation, unit duration, count, and owner-bound readiness");
        rejects(new Action() {
            public void run() {
                new GeneratedDiagnosticServicePreparation.Policy(0, UNIT_SECONDS);
            }
        }, "empty work bound rejected");
        rejects(new Action() {
            public void run() {
                new GeneratedDiagnosticServicePreparation.Policy(5, Double.NaN);
            }
        }, "nonfinite time bound rejected");
        rejects(new Action() {
            public void run() {
                new GeneratedDiagnosticServicePreparation.Policy(5, 1.01);
            }
        }, "unbounded per-unit advance rejected");
    }

    private static void verifyAlreadyReadyConsumesBoundWithoutAdvancing(
            GeneratedDiagnosticServicePreparation.Policy policy) {
        FakeHost host = new FakeHost(0);
        GeneratedDiagnosticServicePreparation.Cursor cursor =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, host);
        while (cursor.step()) { }
        cursor.finish();
        check(cursor.getCompletedUnits() == 5 && host.advances == 0 &&
                host.availabilityChecks == 6,
            "available REMOVE consumes all declared units with zero solver advances");
    }

    private static void verifyDelayedReadinessUsesRealBoundedAdvances(
            GeneratedDiagnosticServicePreparation.Policy policy) {
        FakeHost host = new FakeHost(2);
        GeneratedDiagnosticServicePreparation.Cursor cursor =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, host);
        while (cursor.step()) { }
        cursor.finish();
        check(host.advances == 2 && host.advancedSeconds == 2 * UNIT_SECONDS,
            "unavailable target advances once per bounded unit until readiness");
        check(cursor.getCompletedUnits() == 5 && host.availabilityChecks == 8,
            "readiness is rechecked while remaining declared units drain without time advance");
    }

    private static void verifyNeverReadyFailsAtBound(
            GeneratedDiagnosticServicePreparation.Policy policy) {
        FakeHost host = new FakeHost(Integer.MAX_VALUE);
        GeneratedDiagnosticServicePreparation.Cursor cursor =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, host);
        boolean rejected = false;
        try {
            while (cursor.step()) { }
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage() != null &&
                expected.getMessage().contains("within the declared service bound");
        }
        check(rejected && cursor.getCompletedUnits() == 5 && host.advances == 5 &&
                host.advancedSeconds == 5 * UNIT_SECONDS,
            "permanently unavailable REMOVE fails after, and never exceeds, five advances");
    }

    private static void verifyAvailabilityExpiresBeforeDispatch(
            GeneratedDiagnosticServicePreparation.Policy policy) {
        FakeHost host = new FakeHost(0);
        GeneratedDiagnosticServicePreparation.Cursor cursor =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, host);
        while (cursor.step()) { }
        host.forceUnavailable = true;
        boolean rejected = false;
        try { cursor.finish(); }
        catch (IllegalStateException expected) {
            rejected = expected.getMessage() != null &&
                expected.getMessage().contains("readiness expired before service dispatch");
        }
        check(rejected && host.advances == 0,
            "final dispatch gate rechecks actual readiness without substituting elapsed time");
        host.forceUnavailable = false;
        cursor.cancel();
    }

    private static void verifyIncompleteAndStaleCursorsNeverAdvanceOnCancel(
            GeneratedDiagnosticServicePreparation.Policy policy) {
        final FakeHost incompleteHost = new FakeHost(0);
        final GeneratedDiagnosticServicePreparation.Cursor incomplete =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, incompleteHost);
        rejects(new Action() {
            public void run() { incomplete.finish(); }
        }, "incomplete service cursor cannot pass its dispatch gate");
        incomplete.cancel();
        check(incompleteHost.advances == 0,
            "cancelling an incomplete current cursor never advances");

        FakeHost stale = new FakeHost(Integer.MAX_VALUE);
        final GeneratedDiagnosticServicePreparation.Cursor staleCursor =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, stale);
        stale.stale = true;
        boolean rejected = false;
        try {
            staleCursor.step();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected && stale.advances == 0,
            "stale exact owner aborts before an advance");
        staleCursor.cancel();
        check(stale.advances == 0,
            "cancelling a stale read-only cursor needs no host restoration or solver work");
        rejects(new Action() {
            public void run() { staleCursor.step(); }
        }, "closed stale cursor cannot resume even after ownership changes");
        stale.stale = false;
        rejects(new Action() {
            public void run() { staleCursor.step(); }
        }, "restoring host state cannot resurrect a cancelled cursor");

        FakeHost current = new FakeHost(Integer.MAX_VALUE);
        final GeneratedDiagnosticServicePreparation.Cursor cancelled =
            new GeneratedDiagnosticServicePreparation.Cursor(policy, current);
        cancelled.cancel();
        check(current.advances == 0,
            "cancellation before service work never advances or powers the owner");
        rejects(new Action() {
            public void run() { cancelled.step(); }
        }, "closed service cursor cannot resume after cancellation");
    }

    private static final class FakeHost implements GeneratedDiagnosticServicePreparation.Host {
        final int advancesBeforeReady;
        int advances;
        int availabilityChecks;
        double advancedSeconds;
        boolean stale;
        boolean forceUnavailable;

        FakeHost(int advancesBeforeReady) {
            this.advancesBeforeReady = advancesBeforeReady;
        }

        public void checkCurrent() {
            if (stale) throw new IllegalStateException("stale service owner");
        }

        public boolean isAvailable() {
            availabilityChecks++;
            return !forceUnavailable && advances >= advancesBeforeReady;
        }

        public void advance(double seconds) {
            checkCurrent();
            if (seconds != UNIT_SECONDS)
                throw new AssertionError("Unexpected service advance duration: " + seconds);
            advances++;
            advancedSeconds += seconds;
        }
    }

    private interface Action { void run(); }

    private static void rejects(Action action, String message) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition)
            throw new AssertionError("Diagnostic service preparation: " + message);
    }
}
