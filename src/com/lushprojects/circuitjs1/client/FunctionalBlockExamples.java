package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;

/**
 * Small in-memory descriptor examples used by contract tests and design
 * documentation.  They contain declarations only; they do not generate or
 * attach a runtime board.
 */
final class FunctionalBlockExamples {
    private FunctionalBlockExamples() {
    }

    static FunctionalBlockDescriptor lowSideDriver(String instanceKey) {
        return new FunctionalBlockDescriptor(
                "nmos-low-side-driver", 1, instanceKey,
                Arrays.asList(
                        new FunctionalBlockDescriptor.Parameter(
                                "pullDownOhms",
                                FunctionalBlockDescriptor.Value.ofInteger(100000)),
                        new FunctionalBlockDescriptor.Parameter(
                                "thresholdVolts",
                                FunctionalBlockDescriptor.Value.ofDecimal(2.5)),
                        new FunctionalBlockDescriptor.Parameter(
                                "enabled",
                                FunctionalBlockDescriptor.Value.ofBoolean(true)),
                        new FunctionalBlockDescriptor.Parameter(
                                "model",
                                FunctionalBlockDescriptor.Value.ofText("NMOS"))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component(
                                "Q1", "NMOS", Arrays.asList("G", "D", "S")),
                        new FunctionalBlockDescriptor.Component(
                                "RPD", "RESISTOR", Arrays.asList("1", "2"))),
                Arrays.asList("control", "load", "return"),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Endpoint("Q1.G", "Q1", "G"),
                        new FunctionalBlockDescriptor.Endpoint("Q1.D", "Q1", "D"),
                        new FunctionalBlockDescriptor.Endpoint("Q1.S", "Q1", "S"),
                        new FunctionalBlockDescriptor.Endpoint("RPD.1", "RPD", "1"),
                        new FunctionalBlockDescriptor.Endpoint("RPD.2", "RPD", "2")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Pad("Q1.G", "Q1.G", "control"),
                        new FunctionalBlockDescriptor.Pad("Q1.D", "Q1.D", "load"),
                        new FunctionalBlockDescriptor.Pad("Q1.S", "Q1.S", "return"),
                        new FunctionalBlockDescriptor.Pad("RPD.1", "RPD.1", "control"),
                        new FunctionalBlockDescriptor.Pad("RPD.2", "RPD.2", "return")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Role(
                                "control",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        ref(FunctionalBlockDescriptor.EntityKind.NET,
                                                "control"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "Q1.G"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "RPD.1"))),
                        new FunctionalBlockDescriptor.Role(
                                "load",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        ref(FunctionalBlockDescriptor.EntityKind.NET,
                                                "load"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "Q1.D"))),
                        new FunctionalBlockDescriptor.Role(
                                "return",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        ref(FunctionalBlockDescriptor.EntityKind.NET,
                                                "return"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "Q1.S"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "RPD.2"))),
                        new FunctionalBlockDescriptor.Role(
                                "sense",
                                FunctionalBlockDescriptor.Requirement.OPTIONAL,
                                Collections.<FunctionalBlockDescriptor.LocalRef>emptyList())),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port(
                                "control", "control",
                                ref(FunctionalBlockDescriptor.EntityKind.PAD, "Q1.G")),
                        new FunctionalBlockDescriptor.Port(
                                "load", "load",
                                ref(FunctionalBlockDescriptor.EntityKind.PAD, "Q1.D")),
                        new FunctionalBlockDescriptor.Port(
                                "return", "return",
                                ref(FunctionalBlockDescriptor.EntityKind.PAD, "Q1.S"))));
    }

    static FunctionalBlockDescriptor indicatorLoad(String instanceKey) {
        return new FunctionalBlockDescriptor(
                "resistor-led-indicator", 1, instanceKey,
                Arrays.asList(
                        new FunctionalBlockDescriptor.Parameter(
                                "resistanceOhms",
                                FunctionalBlockDescriptor.Value.ofInteger(1000)),
                        new FunctionalBlockDescriptor.Parameter(
                                "forwardVolts",
                                FunctionalBlockDescriptor.Value.ofDecimal(2.0)),
                        new FunctionalBlockDescriptor.Parameter(
                                "enabled",
                                FunctionalBlockDescriptor.Value.ofBoolean(true)),
                        new FunctionalBlockDescriptor.Parameter(
                                "model",
                                FunctionalBlockDescriptor.Value.ofText("LED indicator"))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component(
                                "R1", "RESISTOR", Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component(
                                "LED1", "LED", Arrays.asList("A", "K"))),
                Arrays.asList("supply", "ledNode", "return"),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Endpoint("R1.1", "R1", "1"),
                        new FunctionalBlockDescriptor.Endpoint("R1.2", "R1", "2"),
                        new FunctionalBlockDescriptor.Endpoint("LED1.A", "LED1", "A"),
                        new FunctionalBlockDescriptor.Endpoint("LED1.K", "LED1", "K")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Pad("R1.1", "R1.1", "supply"),
                        new FunctionalBlockDescriptor.Pad("R1.2", "R1.2", "ledNode"),
                        new FunctionalBlockDescriptor.Pad("LED1.A", "LED1.A", "ledNode"),
                        new FunctionalBlockDescriptor.Pad("LED1.K", "LED1.K", "return")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Role(
                                "supply",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        ref(FunctionalBlockDescriptor.EntityKind.NET,
                                                "supply"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "R1.1"))),
                        new FunctionalBlockDescriptor.Role(
                                "ledNode",
                                FunctionalBlockDescriptor.Requirement.OPTIONAL,
                                Arrays.asList(
                                        ref(FunctionalBlockDescriptor.EntityKind.NET,
                                                "ledNode"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "R1.2"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "LED1.A"))),
                        new FunctionalBlockDescriptor.Role(
                                "return",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        ref(FunctionalBlockDescriptor.EntityKind.NET,
                                                "return"),
                                        ref(FunctionalBlockDescriptor.EntityKind.PAD,
                                                "LED1.K"))),
                        new FunctionalBlockDescriptor.Role(
                                "sense",
                                FunctionalBlockDescriptor.Requirement.OPTIONAL,
                                Collections.<FunctionalBlockDescriptor.LocalRef>emptyList())),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port(
                                "supply", "supply",
                                ref(FunctionalBlockDescriptor.EntityKind.PAD, "R1.1")),
                        new FunctionalBlockDescriptor.Port(
                                "return", "return",
                                ref(FunctionalBlockDescriptor.EntityKind.PAD, "LED1.K"))));
    }

    private static FunctionalBlockDescriptor.LocalRef ref(
            FunctionalBlockDescriptor.EntityKind kind, String id) {
        return new FunctionalBlockDescriptor.LocalRef(kind, id);
    }
}
