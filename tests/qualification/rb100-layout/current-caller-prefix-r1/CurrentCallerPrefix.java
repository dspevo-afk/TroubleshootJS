package com.lushprojects.circuitjs1.client;

/** Test-only typed caller prefix; observations are not production authority. */
final class CurrentCallerPrefix {
    static long observedStarted;
    static Object observedArguments;
    static Object observedParent;
    static Object observedNativeMeter;
    static Object observedWrapper;
    static int parsedStages;
    static int parentStages;
    static int nativeStages;
    static int wrapperStages;
    static int inputStages;
    static int completionStages;

    private CurrentCallerPrefix() { }

    static void run(final String[] raw) throws Exception {
        final long started = System.nanoTime();
        observedStarted = started;
        final CohortTerminalTelemetryStudy.Arguments args =
                CohortTerminalTelemetryStudy.Arguments.parse(raw);
        observedArguments = args;
        parsedStages++;
        final CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
        observedParent = parent;
        parentStages++;
        final Object rawMeter = parent.meter(started);
        observedNativeMeter = rawMeter;
        nativeStages++;
        final CohortTerminalTelemetryStudy.SetupMeter meter =
                new CohortTerminalTelemetryStudy.SetupMeter(parent, rawMeter);
        observedWrapper = meter;
        wrapperStages++;
        inputStages++;
        final CohortTerminalTelemetryStudy.SetupResult setup =
                CurrentV2InputSetup.readCurrent(meter, started, args.inputContextSha256);
        completionStages++;
        if (setup == null)
            throw new IllegalStateException("PREFIX_UNEXPECTED_NULL_SETUP");
        throw new IllegalStateException("PREFIX_UNEXPECTED_SETUP_SUCCESS");
    }
}
