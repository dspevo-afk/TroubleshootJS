package com.lushprojects.circuitjs1.client;

/** Stable semantic operation identities; these are deliberately not solver IDs. */
final class GeneratedBoardOperationIds {
    static final String CONTROL_INPUT_HIGH = "CONTROL_INPUT_HIGH";
    static final String CONTROL_INPUT_LOW = "CONTROL_INPUT_LOW";
    static final String SENSOR_CONDITION_LOW = "SENSOR_CONDITION_LOW";
    static final String SENSOR_CONDITION_MID = "SENSOR_CONDITION_MID";
    static final String SENSOR_CONDITION_HIGH = "SENSOR_CONDITION_HIGH";
    // Compatibility aliases for callers that describe the same public
    // operation as a sensor input rather than a sensor condition.
    static final String SENSOR_INPUT_LOW = SENSOR_CONDITION_LOW;
    static final String SENSOR_INPUT_MID = SENSOR_CONDITION_MID;
    static final String SENSOR_INPUT_HIGH = SENSOR_CONDITION_HIGH;
    static final String CUSTOMER_RETEST = "CUSTOMER_RETEST";

    private GeneratedBoardOperationIds() { }
}
