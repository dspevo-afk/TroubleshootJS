package com.lushprojects.circuitjs1.client;

/** Tracks a cumulative foreground generation budget across tab visibility changes. */
final class ForegroundGenerationClock {
    private final long started;
    private long lastWall, pausedAt, pausedMillis;
    private boolean paused;
    ForegroundGenerationClock(long wall, boolean paused) {
        started=lastWall=pausedAt=wall; this.paused=paused;
    }
    private void sample(long wall) {
        if(wall < lastWall) throw new IllegalStateException("Generation wall clock regressed");
        lastWall=wall;
    }
    void setPaused(long wall, boolean value) {
        sample(wall);
        if(value == paused) return;
        if(value) pausedAt=wall;
        else {
            long interval=wall-pausedAt;
            if(interval < 0 || pausedMillis > Long.MAX_VALUE-interval)
                throw new IllegalStateException("Generation paused duration overflow");
            pausedMillis+=interval;
        }
        paused=value;
    }
    long nowMillis(long wall) { sample(wall); return (paused ? pausedAt : wall)-pausedMillis; }
    long elapsedMillis(long wall) { return nowMillis(wall)-started; }
    boolean isPaused() { return paused; }
}
