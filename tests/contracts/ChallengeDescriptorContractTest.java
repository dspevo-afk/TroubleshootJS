package com.lushprojects.circuitjs1.client;

/** JVM entry point for the pure Task 46 A/B corpus and parity text. */
public final class ChallengeDescriptorContractTest {
    private ChallengeDescriptorContractTest() { }

    public static void main(String[] args) {
        String output = Task46ContractVectors.run();
        String repeated = Task46ContractVectors.run();
        if (!output.equals(repeated)) {
            throw new AssertionError("Task 46 pure corpus is not deterministic across runs");
        }
        System.out.println("TASK46_PARITY_BEGIN");
        System.out.print(output);
        if (!output.endsWith("\n")) System.out.println();
        System.out.println("TASK46_PARITY_END");
        System.out.println("PASS: Task46 " + Task46ContractVectors.getAssertionCount()
            + " assertions, " + Task46ContractVectors.getNegativeCaseCount()
            + " negative cases; pure descriptor/constraints/named-stream corpus");
    }
}
