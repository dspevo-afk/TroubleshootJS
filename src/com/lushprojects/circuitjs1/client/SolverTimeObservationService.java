package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.Operation;
import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.Outcome;

/**
 * Publishes bounded differential samples only from completed, accepted solver
 * operations.  A subscriber never observes a trial, a failed operation, or a
 * screen repaint.  Graph/owner revisions clear every window before a new
 * operation may contribute samples.
 */
final class SolverTimeObservationService {
    /** Largest bounded window retained by a single instrument subscription. */
    static final int MAX_CAPACITY = 8192;
    static final int DEFAULT_CAPACITY = 2048;

    static final class Subscription {
        private final CircuitPostMeasurementEndpoint red;
        private final CircuitPostMeasurementEndpoint black;
        private final boolean collectDuringActiveMeasurement;
        private final SolverTimeWindow committed;
        private final SolverTimeWindow staging;
        private Object owner;
        private Object graph;
        private Operation stagedOperation;
        /* Changes whenever the service retires an observation epoch.  A
         * consumer with dynamically resolved BoardPad endpoints must rebind
         * rather than keep an old element/post cursor after mutation. */
        private long generation;
        private boolean closed;

        private Subscription(CircuitPostMeasurementEndpoint red,
                CircuitPostMeasurementEndpoint black, int capacity,
                boolean collectDuringActiveMeasurement, long generation) {
            if (red == null || black == null)
                throw new IllegalArgumentException("Differential observation endpoints are required");
            this.red = red;
            this.black = black;
            this.collectDuringActiveMeasurement = collectDuringActiveMeasurement;
            this.generation = generation;
            committed = new SolverTimeWindow(capacity);
            staging = new SolverTimeWindow(capacity);
        }

        SolverTimeSample[] snapshot() {
            return committed.snapshot();
        }

        SolverTimeWindow snapshotWindow() {
            SolverTimeWindow result = new SolverTimeWindow(committed.getCapacity());
            SolverTimeSample[] samples = committed.snapshot();
            for (int i = 0; i < samples.length; i++)
                result.append(samples[i]);
            return result;
        }

        int getSampleCount() {
            return committed.getSampleCount();
        }

        long getGeneration() {
            return generation;
        }

        void clear() {
            committed.clear();
            staging.clear();
            stagedOperation = null;
            owner = null;
            graph = null;
        }

        void clear(long currentGeneration) {
            clear();
            generation = currentGeneration;
        }

        void close() {
            clear();
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }

    private final CirSim sim;
    private final Vector<Subscription> subscriptions = new Vector<Subscription>();
    private long generation;

    SolverTimeObservationService(CirSim sim) {
        if (sim == null)
            throw new IllegalArgumentException("Solver-time observation needs a simulation");
        this.sim = sim;
    }

    Subscription subscribe(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black, int capacity,
            boolean collectDuringActiveMeasurement) {
        if (capacity < 2 || capacity > MAX_CAPACITY)
            throw new IllegalArgumentException("Invalid bounded solver-time observation capacity");
        Subscription subscription = new Subscription(red, black, capacity,
            collectDuringActiveMeasurement, generation);
        subscriptions.add(subscription);
        return subscription;
    }

    void unsubscribe(Subscription subscription) {
        if (subscription == null)
            return;
        subscription.close();
        subscriptions.remove(subscription);
    }

    /** Retire every published and staged sample before topology/source ownership changes. */
    void invalidate() {
        generation++;
        for (int i = subscriptions.size() - 1; i >= 0; i--) {
            Subscription subscription = subscriptions.elementAt(i);
            if (subscription.isClosed()) {
                subscriptions.removeElementAt(i);
                continue;
            }
            subscription.clear(generation);
        }
    }

    /** Capture an accepted state into an operation-private staging window. */
    void accepted(Operation operation) {
        if (operation == null)
            return;
        for (int i = 0; i < subscriptions.size(); i++) {
            Subscription subscription = subscriptions.elementAt(i);
            if (!eligible(subscription, operation))
                continue;
            if (subscription.stagedOperation != operation) {
                subscription.staging.clear();
                subscription.stagedOperation = operation;
            }
            double value = differentialVoltage(subscription);
            if (finite(value))
                subscription.staging.append(operation.lastAcceptedTime, value);
        }
    }

    /** Publish a complete operation atomically; all other outcomes are discarded. */
    void finished(Operation operation, Outcome outcome) {
        if (operation == null)
            return;
        for (int i = 0; i < subscriptions.size(); i++) {
            Subscription subscription = subscriptions.elementAt(i);
            if (subscription.closed)
                continue;
            /* An operation can fail or be cancelled before its first accepted
             * step.  In that case there is no staging identity to match, but
             * the old committed waveform is still stale: the solver boundary
             * has rejected the current observation epoch. */
            if (outcome != Outcome.COMPLETE && matches(subscription, operation)) {
                subscription.committed.clear();
                if (subscription.stagedOperation == operation) {
                    subscription.staging.clear();
                    subscription.stagedOperation = null;
                }
                continue;
            }
            if (subscription.stagedOperation != operation)
                continue;
            if (outcome == Outcome.COMPLETE && matches(subscription, operation)) {
                SolverTimeSample[] samples = subscription.staging.snapshot();
                for (int j = 0; j < samples.length; j++)
                    subscription.committed.append(samples[j]);
            } else {
                // A rejected/failed operation may have disturbed companion
                // state. Never retain an older waveform as if it were current.
                subscription.committed.clear();
            }
            subscription.staging.clear();
            subscription.stagedOperation = null;
        }
    }

    private boolean eligible(Subscription subscription, Operation operation) {
        if (subscription == null || subscription.closed ||
                (sim.activeMeasurementOverlay && !subscription.collectDuringActiveMeasurement))
            return false;
        if (!matches(subscription, operation)) {
            if (subscription.stagedOperation != null)
                subscription.clear();
            if (!currentEndpoints(subscription))
                return false;
            subscription.owner = operation.owner;
            subscription.graph = operation.graph;
        }
        return currentEndpoints(subscription);
    }

    private boolean matches(Subscription subscription, Operation operation) {
        return subscription.owner == operation.owner && subscription.graph == operation.graph &&
            operation.graph == sim.elmList;
    }

    private boolean currentEndpoints(Subscription subscription) {
        return sim.elmList != null &&
            sim.elmList.contains(subscription.red.getElement()) &&
            sim.elmList.contains(subscription.black.getElement()) &&
            validPost(subscription.red) && validPost(subscription.black);
    }

    private static boolean validPost(CircuitPostMeasurementEndpoint endpoint) {
        return endpoint.getElement() != null && endpoint.getPostIndex() >= 0 &&
            endpoint.getPostIndex() < endpoint.getElement().getPostCount();
    }

    private static double differentialVoltage(Subscription subscription) {
        try {
            return subscription.red.getElement().getPostVoltage(subscription.red.getPostIndex()) -
                subscription.black.getElement().getPostVoltage(subscription.black.getPostIndex());
        } catch (RuntimeException ignored) {
            // A stale physical projection is discarded, never converted into a reading.
            return Double.NaN;
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
