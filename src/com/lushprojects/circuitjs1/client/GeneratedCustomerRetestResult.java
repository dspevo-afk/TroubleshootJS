package com.lushprojects.circuitjs1.client;

/** Player-safe result of a family-owned customer retest. */
final class GeneratedCustomerRetestResult {
    private final boolean passed;
    private final String playerMessage;

    GeneratedCustomerRetestResult(boolean passed, String playerMessage) {
        if (playerMessage == null || playerMessage.length() == 0)
            throw new IllegalArgumentException("Customer retest requires player feedback");
        this.passed = passed;
        this.playerMessage = playerMessage;
    }

    boolean isPassed() { return passed; }
    String getPlayerMessage() { return playerMessage; }
}
