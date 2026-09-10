package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;

/** Stable simulation-time events, committed only at accepted step boundaries. */
final class SolverEventQueue {
    interface Action { void fire(double scheduledTime, double acceptedTime); }
    static final class Event {
        final double time;
        final long order;
        final Action action;
        boolean cancelled;
        Event(double time, long order, Action action) {
            this.time = time; this.order = order; this.action = action;
        }
        void cancel() { cancelled = true; }
    }
    private final ArrayList<Event> events = new ArrayList<Event>();
    private final int capacity, dispatchLimit;
    private long nextOrder;
    private double acceptedTime, dispatchTime;
    private boolean dispatching, cancelled, failed;

    SolverEventQueue(int capacity, int dispatchLimit, double initialTime) {
        if (capacity < 1 || dispatchLimit < 1) throw new IllegalArgumentException("Invalid event limits");
        this.capacity = capacity; this.dispatchLimit = dispatchLimit;
        reset(initialTime);
    }
    void reset(double initialTime) {
        if (dispatching || !SolverExecutionBoundary.finite(initialTime))
            throw new IllegalStateException("Invalid event reset boundary");
        events.clear(); nextOrder = 0; acceptedTime = initialTime;
        dispatchTime = initialTime; cancelled = false; failed = false;
    }
    Event schedule(double time, Action action) {
        requireUsable();
        double earliest = dispatching ? dispatchTime : acceptedTime;
        if (action == null || !SolverExecutionBoundary.finite(time) || time < earliest)
            throw new IllegalArgumentException("Event precedes its simulation phase");
        if (events.size() >= capacity || nextOrder == Long.MAX_VALUE)
            throw new SolverExecutionBoundary.Failure(SolverExecutionBoundary.Outcome.WORK_EXHAUSTED,
                "Scheduled-state queue capacity exhausted");
        Event event = new Event(time, nextOrder++, action);
        int index = 0;
        while (index < events.size() && events.get(index).time <= time) index++;
        events.add(index, event);
        return event;
    }
    int dispatchAccepted(double time) {
        requireUsable();
        if (dispatching || !SolverExecutionBoundary.finite(time) || time < acceptedTime)
            throw new IllegalStateException("Invalid accepted-state event boundary");
        dispatching = true;
        int work = 0;
        try {
            while (!events.isEmpty() && events.get(0).time <= time) {
                if (cancelled) break;
                if (++work > dispatchLimit)
                    throw new SolverExecutionBoundary.Failure(SolverExecutionBoundary.Outcome.WORK_EXHAUSTED,
                        "Finite scheduled-state feedback limit exceeded");
                Event event = events.remove(0);
                dispatchTime = event.time;
                if (!event.cancelled) event.action.fire(event.time, time);
            }
            acceptedTime = time;
            return work;
        } catch (RuntimeException failure) {
            failed = true; throw failure;
        } catch (Error failure) {
            failed = true; throw failure;
        } finally { dispatching = false; }
    }
    double nextTime() {
        requireUsable();
        return events.isEmpty() ? Double.POSITIVE_INFINITY : events.get(0).time;
    }
    void cancel() { events.clear(); cancelled = true; }
    private void requireUsable() {
        if (cancelled || failed) throw new IllegalStateException("Scheduled state requires explicit reset");
    }
}
