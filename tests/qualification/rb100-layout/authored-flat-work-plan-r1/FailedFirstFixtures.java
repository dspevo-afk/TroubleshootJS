package com.lushprojects.circuitjs1.client;

/** Compile-only type carrier. Fixture production is outside this component. */
final class FailedFirstFixtures {
    private FailedFirstFixtures() {
        throw new UnsupportedOperationException("FAILED_FIRST_FIXTURES_DENIED");
    }
    static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        Fixture() {
            throw new UnsupportedOperationException("FAILED_FIRST_FIXTURES_DENIED");
        }
    }
    static Fixture open() {
        throw new UnsupportedOperationException("FAILED_FIRST_FIXTURES_DENIED");
    }
    static Fixture blocked() {
        throw new UnsupportedOperationException("FAILED_FIRST_FIXTURES_DENIED");
    }
}
