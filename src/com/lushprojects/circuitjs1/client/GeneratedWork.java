package com.lushprojects.circuitjs1.client;

/** Finite owner-held work; every step finishes its solver operation before yielding. */
abstract class GeneratedWork<T> implements Runnable {
    /** Execute one unit; true means another unit remains. */
    abstract boolean step();
    /** Return the completed value without performing more solver work. */
    abstract T finish();
    abstract void cancel();
    abstract int getWorkUnits();

    public final void run() { complete(this); }

    static <T> T complete(GeneratedWork<T> work) {
        if (work == null) throw new IllegalArgumentException("Missing generated work");
        try {
            while (work.step()) { }
            return work.finish();
        } catch (RuntimeException failure) {
            cancelAfterFailure(work, failure);
            throw failure;
        } catch (Error failure) {
            cancelAfterFailure(work, failure);
            throw failure;
        }
    }

    private static void cancelAfterFailure(GeneratedWork<?> work, Throwable failure) {
        try { work.cancel(); }
        catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }

    static <T> GeneratedWork<T> value(final T value) {
        return new GeneratedWork<T>() {
            private boolean complete, cancelled;
            boolean step() {
                if (cancelled) throw new IllegalStateException("Generated work was cancelled");
                complete = true; return false;
            }
            T finish() {
                if (!complete || cancelled) throw new IllegalStateException("Generated work is incomplete");
                return value;
            }
            void cancel() { if (!complete) cancelled = true; }
            int getWorkUnits() { return 1; }
        };
    }
}
