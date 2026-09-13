package com.lushprojects.circuitjs1.client;

public final class A07ExecutionContractTest {
    public static void main(String[] args) {
        int count = A07ExecutionContractVectors.run();
        long before = System.currentTimeMillis();
        double selected = CircuitSolverExecutor.wallTimeMillis();
        long after = System.currentTimeMillis();
        if (selected < before || selected > after || selected != Math.floor(selected))
            throw new AssertionError("Native solver wall clock did not select the real JVM millisecond clock");
        count++;
        System.out.println("PASS: A07 execution contracts assertions=" + count);
    }
}
