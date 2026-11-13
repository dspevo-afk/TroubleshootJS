package com.lushprojects.circuitjs1.client;

/** Reads solved post voltages at a solver-time slice boundary; never drives the circuit. */
interface GeneratedTemporalSampler {
    void sample(double solverTimeSeconds);
}
