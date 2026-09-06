package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Independent Task 45 oracle.  This is a small source-7 main rather than a
 * JUnit test so that it can be compiled directly against the package-local
 * contract types and the pinned JDK 8 toolchain.
 */
final class ElectricalPortContractTest {
    private static final String DEVICE = "device";
    private static final String ISO_MAIN = "iso-main";
    private static int assertions;

    public static void main(String[] args) {
        try {
            scalarAndRangeValidation();
            descriptorValidationAndImmutability();
            connectionValidationAndImmutability();
            namespaceAndTask44IdentityRegression();
            positiveRailAndPassiveCases();
            voltageContainmentCases();
            referenceAndMergeCases();
            driverAndDirectionCases();
            digitalCases();
            loadingAndAccessibilityCases();
            malformedProposalCases();
            determinismAndPurityCases();
            adapterContractCases();
            legacyInputAdapterCases();
            legacyRoleValidationCases();
            lowSideSwitchAdapterCases();
            System.out.println("PASS: Task45 " + assertions + " assertions");
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.println("FAIL: Task45 after " + assertions + " assertions: "
                + failure.getMessage());
            System.exit(1);
        }
    }

    private static void scalarAndRangeValidation() {
        expectElectrical(ElectricalContractException.Code.INVALID_SCALAR,
            new Runnable() { public void run() {
                ElectricalPortContract.Scalar.known(Double.NaN);
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_SCALAR,
            new Runnable() { public void run() {
                ElectricalPortContract.Scalar.known(Double.POSITIVE_INFINITY);
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_SCALAR,
            new Runnable() { public void run() {
                ElectricalPortContract.Scalar.known(Double.NEGATIVE_INFINITY);
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_RANGE,
            new Runnable() { public void run() {
                ElectricalPortContract.Range.known(Double.NaN, 1.0);
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_RANGE,
            new Runnable() { public void run() {
                ElectricalPortContract.Range.known(0.0, Double.POSITIVE_INFINITY);
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_RANGE,
            new Runnable() { public void run() {
                ElectricalPortContract.Range.known(5.0, 4.0);
            }});
        expectElectrical(ElectricalContractException.Code.MISSING_FIELD,
            new Runnable() { public void run() {
                ElectricalPortContract.Domain.known("gnd", null);
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_THRESHOLD,
            new Runnable() { public void run() {
                new ElectricalPortContract.Digital(
                    ElectricalPortContract.ActiveLevel.HIGH,
                    ElectricalPortContract.Scalar.known(3.5),
                    ElectricalPortContract.Scalar.known(3.5),
                    ElectricalPortContract.Scalar.notApplicable(),
                    ElectricalPortContract.Scalar.notApplicable());
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_THRESHOLD,
            new Runnable() { public void run() {
                new ElectricalPortContract.Digital(
                    ElectricalPortContract.ActiveLevel.HIGH,
                    ElectricalPortContract.Scalar.notApplicable(),
                    ElectricalPortContract.Scalar.notApplicable(),
                    ElectricalPortContract.Scalar.known(4.0),
                    ElectricalPortContract.Scalar.known(3.0));
            }});
        expectElectrical(ElectricalContractException.Code.CONTRADICTORY_FIELD,
            new Runnable() { public void run() {
                new ElectricalPortContract.Digital(
                    ElectricalPortContract.ActiveLevel.NOT_APPLICABLE,
                    ElectricalPortContract.Scalar.known(0.4),
                    ElectricalPortContract.Scalar.notApplicable(),
                    ElectricalPortContract.Scalar.notApplicable(),
                    ElectricalPortContract.Scalar.notApplicable());
            }});

        final ElectricalPortContract.Scalar known = ElectricalPortContract.Scalar.known(-0.0);
        equal(ElectricalPortContract.State.KNOWN, known.getState());
        equal(0L, Double.doubleToLongBits(known.getValue()));
        final ElectricalPortContract.Range zero = ElectricalPortContract.Range.known(-0.0, 0.0);
        equal(0L, Double.doubleToLongBits(zero.getMinimum()));
        equal(0L, Double.doubleToLongBits(zero.getMaximum()));
        equal(ElectricalPortContract.State.UNKNOWN,
            ElectricalPortContract.Scalar.unknown().getState());
        equal(ElectricalPortContract.State.NOT_APPLICABLE,
            ElectricalPortContract.Range.notApplicable().getState());
    }

    private static void descriptorValidationAndImmutability() {
        final PortSpec source = sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0,
            1.0, ElectricalPortContract.Range.known(4.75, 5.25));
        PortSpec reference = returnPort("return", "gnd", ISO_MAIN,
            ElectricalPortContract.MergePolicy.ALLOW);
        List<PortSpec> specs = list(source, reference);
        FunctionalBlockDescriptor descriptor = descriptor("immutable", specs);
        List<ElectricalPortContract> ports = new ArrayList<ElectricalPortContract>();
        ports.add(source.port);
        ports.add(reference.port);
        final ElectricalBlockContract block = new ElectricalBlockContract(descriptor, ports,
            Collections.<ElectricalBlockContract.Adapter>emptyList());

        specs.clear();
        ports.clear();
        equal(2, block.getPorts().size());
        equal(ElectricalPortContract.Role.RAIL, block.getPorts().get("out").getRole());
        equal("net/signal", block.getAttachmentKey("out"));
        equal("immutable/gnd", block.getReferenceKey("out"));
        unmodifiable(new Runnable() { public void run() {
            block.getPorts().clear();
        }});
        unmodifiable(new Runnable() { public void run() {
            block.getAdapters().clear();
        }});

        final FunctionalBlockDescriptor missingPortDescriptor = descriptor("missing-port",
            list(source));
        expectElectrical(ElectricalContractException.Code.MISSING_FIELD,
            new Runnable() { public void run() {
                new ElectricalBlockContract(missingPortDescriptor,
                    Collections.<ElectricalPortContract>emptyList(),
                    Collections.<ElectricalBlockContract.Adapter>emptyList());
            }});

        final List<ElectricalPortContract> duplicatePorts = list(source.port, source.port);
        expectElectrical(ElectricalContractException.Code.DUPLICATE_DECLARATION,
            new Runnable() { public void run() {
                new ElectricalBlockContract(descriptor("duplicate-port", list(source)),
                    duplicatePorts, Collections.<ElectricalBlockContract.Adapter>emptyList());
            }});

        final PortSpec badReturn = new PortSpec("return", ElectricalPortContract.Role.RETURN,
            ElectricalPortContract.Direction.BIDIRECTIONAL,
            ElectricalPortContract.Behavior.PASSIVE, ElectricalPortContract.Drive.NONE,
            "signal", "gnd", ISO_MAIN, naScalar(), naRange(), naRange(),
            ElectricalPortContract.Loading.NONE, naScalar(), naScalar(), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
        expectElectrical(ElectricalContractException.Code.INVALID_REFERENCE,
            new Runnable() { public void run() {
                new ElectricalBlockContract(descriptor("bad-return", list(source, badReturn)),
                    list(source.port, badReturn.port),
                    Collections.<ElectricalBlockContract.Adapter>emptyList());
            }});

        expectElectrical(ElectricalContractException.Code.INVALID_SCALAR,
            new Runnable() { public void run() {
                sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0,
                    -1.0, ElectricalPortContract.Range.known(4.75, 5.25));
            }});
    }

    private static void connectionValidationAndImmutability() {
        ElectricalConnection.PortRef first = new ElectricalConnection.PortRef("a", "out");
        ElectricalConnection.PortRef second = new ElectricalConnection.PortRef("b", "in");
        List<ElectricalConnection.PortRef> mutable = list(second, first);
        final ElectricalConnection connection = new ElectricalConnection("wire", mutable);
        mutable.clear();
        equal(2, connection.getPorts().size());
        equal("a", connection.getPorts().get(0).getBlockKey());
        equal("b", connection.getPorts().get(1).getBlockKey());
        unmodifiable(new Runnable() { public void run() {
            connection.getPorts().clear();
        }});
        final ElectricalConnection.PortRef duplicate = new ElectricalConnection.PortRef("a", "out");
        expectElectrical(ElectricalContractException.Code.DUPLICATE_DECLARATION,
            new Runnable() { public void run() {
                new ElectricalConnection("duplicate", list(duplicate, duplicate,
                    new ElectricalConnection.PortRef("b", "in")));
            }});
        expectElectrical(ElectricalContractException.Code.INVALID_CONNECTION,
            new Runnable() { public void run() {
                new ElectricalConnection("one", list(new ElectricalConnection.PortRef("a", "out")));
            }});
        expectElectrical(ElectricalContractException.Code.MISSING_FIELD,
            new Runnable() { public void run() {
                new ElectricalConnection("null-ports", null);
            }});
    }

    private static void namespaceAndTask44IdentityRegression() {
        FunctionalBlockDescriptor descriptor = descriptor("identity",
            list(sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
                returnPort("return", "gnd", ISO_MAIN,
                    ElectricalPortContract.MergePolicy.ALLOW)));
        FunctionalBlockDescriptor second = descriptor("other",
            list(sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
                returnPort("return", "gnd", ISO_MAIN,
                    ElectricalPortContract.MergePolicy.ALLOW)));
        BlockNamespace namespace = new BlockNamespace(DEVICE, 1, list(descriptor, second));
        equal("tsj-block-v1/device@1/identity/port/out",
            namespace.idFor("identity", FunctionalBlockDescriptor.EntityKind.PORT, "out"));
        equal("tsj-block-v1/device@1/identity/net/signal",
            namespace.idFor("identity", FunctionalBlockDescriptor.EntityKind.NET, "signal"));
        equal("tsj-block-v1/device@1/other/port/out",
            namespace.idFor("other", FunctionalBlockDescriptor.EntityKind.PORT, "out"));
        equal("tsj-block-v1/device@1/identity/port/out",
            namespace.idFor("identity", FunctionalBlockDescriptor.EntityKind.PORT, "out"));
    }

    private static void positiveRailAndPassiveCases() {
        ElectricalBlockContract source = block("source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract load = block("load",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        PortCompatibilityPreflight.Result result = check(list(source, load),
            connection("signal", ref("source", "out"), ref("load", "in")),
            connection("reference", ref("source", "return"), ref("load", "return")));
        expectDecision("COMPATIBLE", result);
        equal(0, result.getDiagnostics().size());

        ElectricalBlockContract passive = block("passive",
            passivePort("node", "signal", "gnd", ISO_MAIN, 0.0, 5.5),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(source, passive),
            connection("passive-signal", ref("source", "out"), ref("passive", "node")),
            connection("passive-reference", ref("source", "return"), ref("passive", "return")));
        expectDecision("COMPATIBLE", result);

        ElectricalBlockContract resistive = block("resistive",
            sourceRailWithDrive("out", ElectricalPortContract.Drive.RESISTIVE_SOURCE,
                "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(resistive, load),
            connection("resistive-signal", ref("resistive", "out"), ref("load", "in")),
            connection("resistive-reference", ref("resistive", "return"), ref("load", "return")));
        expectDecision("COMPATIBLE", result);
    }

    private static void voltageContainmentCases() {
        ElectricalBlockContract source = block("source-v",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract overlapping = block("overlap-v",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 5.0, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        PortCompatibilityPreflight.Result result = check(list(source, overlapping),
            connection("v-overlap", ref("source-v", "out"), ref("overlap-v", "in")),
            connection("v-overlap-ref", ref("source-v", "return"), ref("overlap-v", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "VOLTAGE_RANGE_NOT_CONTAINED", "v-overlap",
            fullPort("source-v", "out"), fullPort("overlap-v", "in"));

        ElectricalBlockContract outside = block("outside-v",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 5.5, 6.0, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(source, outside),
            connection("v-outside", ref("source-v", "out"), ref("outside-v", "in")),
            connection("v-outside-ref", ref("source-v", "return"), ref("outside-v", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "VOLTAGE_RANGE_NOT_CONTAINED", "v-outside",
            fullPort("source-v", "out"), fullPort("outside-v", "in"));
    }

    private static void referenceAndMergeCases() {
        ElectricalBlockContract source = block("ref-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract load = block("ref-load",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));

        PortCompatibilityPreflight.Result result = check(list(source, load),
            connection("unjoined-signal", ref("ref-source", "out"), ref("ref-load", "in")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "REFERENCE_MISMATCH", "unjoined-signal",
            fullPort("ref-source", "out"), fullPort("ref-load", "in"));

        result = check(list(source, load),
            connection("joined-signal", ref("ref-source", "out"), ref("ref-load", "in")),
            connection("joined-return", ref("ref-source", "return"), ref("ref-load", "return")));
        expectDecision("COMPATIBLE", result);

        ElectricalBlockContract unknownSource = block("unknown-ref-source",
            sourceRail("out", "signal", "gnd", null, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", null,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract unknownLoad = block("unknown-ref-load",
            receiverLoad("in", "signal", "gnd", null, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", null,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unknownSource, unknownLoad),
            connection("unknown-return", ref("unknown-ref-source", "return"),
                ref("unknown-ref-load", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNKNOWN_ISOLATION", "unknown-return",
            fullPort("unknown-ref-source", "return"), fullPort("unknown-ref-load", "return"));

        result = check(list(unknownSource, unknownLoad),
            connection("unknown-signal", ref("unknown-ref-source", "out"),
                ref("unknown-ref-load", "in")),
            connection("unknown-signal-return", ref("unknown-ref-source", "return"),
                ref("unknown-ref-load", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "REFERENCE_UNPROVEN", "unknown-signal",
            fullPort("unknown-ref-source", "out"), fullPort("unknown-ref-load", "in"));

        ElectricalBlockContract sameBlockReturns = block("same-return-block",
            returnPort("return-a", "gnd", null, ElectricalPortContract.MergePolicy.ALLOW),
            returnPort("return-b", "gnd", null, ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(sameBlockReturns),
            connection("same-local-return", ref("same-return-block", "return-a"),
                ref("same-return-block", "return-b")));
        expectDecision("COMPATIBLE", result);

        ElectricalBlockContract forbiddenSource = block("forbidden-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.FORBID));
        result = check(list(forbiddenSource, load),
            connection("forbidden-return", ref("forbidden-source", "return"),
                ref("ref-load", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "MERGE_FORBIDDEN", "forbidden-return",
            fullPort("forbidden-source", "return"));

        ElectricalBlockContract unknownMergeSource = block("unknown-merge-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.UNKNOWN));
        result = check(list(unknownMergeSource, load),
            connection("unknown-merge", ref("unknown-merge-source", "return"),
                ref("ref-load", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNKNOWN_MERGE_POLICY", "unknown-merge",
            fullPort("unknown-merge-source", "return"));

        ElectricalBlockContract isolated = block("isolated-load",
            receiverLoad("in", "signal", "gnd", "iso-other", 4.5, 5.5, 0.2),
            returnPort("return", "gnd", "iso-other",
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(source, isolated),
            connection("isolated-return", ref("ref-source", "return"),
                ref("isolated-load", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "ISOLATION_MERGE_FORBIDDEN", "isolated-return",
            fullPort("ref-source", "return"), fullPort("isolated-load", "return"));

        result = check(list(source, load),
            connection("mixed", ref("ref-source", "out"), ref("ref-source", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "RETURN_SIGNAL_MIX", "mixed",
            fullPort("ref-source", "out"), fullPort("ref-source", "return"));
    }

    private static void driverAndDirectionCases() {
        ElectricalBlockContract source = block("driver-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract load = block("driver-load",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));

        ElectricalBlockContract equalSource = block("equal-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        PortCompatibilityPreflight.Result result = check(list(source, equalSource, load),
            connection("equal-drivers", ref("driver-source", "out"),
                ref("equal-source", "out"), ref("driver-load", "in")),
            connection("equal-returns", ref("driver-source", "return"),
                ref("equal-source", "return"), ref("driver-load", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "CONFLICTING_DRIVERS", "equal-drivers",
            fullPort("driver-source", "out"), fullPort("equal-source", "out"));

        ElectricalBlockContract sink = block("open-drain",
            sinkPort("out", ElectricalPortContract.Role.RAIL, "signal", "gnd", ISO_MAIN),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract railLoad = block("rail-load",
            receiverRail("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(sink, railLoad),
            connection("open-drain-rail", ref("open-drain", "out"),
                ref("rail-load", "in")),
            connection("open-drain-ref", ref("open-drain", "return"),
                ref("rail-load", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "INVALID_DIRECTION_DRIVE", "open-drain-rail",
            fullPort("open-drain", "out"), fullPort("rail-load", "in"));

        ElectricalBlockContract unknownDrive = block("unknown-drive",
            sourceRailWithDrive("out", ElectricalPortContract.Drive.UNKNOWN,
                "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unknownDrive, load),
            connection("unknown-drive-signal", ref("unknown-drive", "out"),
                ref("driver-load", "in")),
            connection("unknown-drive-ref", ref("unknown-drive", "return"),
                ref("driver-load", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNSUPPORTED_DRIVE", "unknown-drive-signal",
            fullPort("unknown-drive", "out"));

        ElectricalBlockContract openDrainControl = block("open-drain-control",
            sinkPort("out", ElectricalPortContract.Role.CONTROL, "signal", "gnd", ISO_MAIN),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract openDrainInput = block("open-drain-input",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 0.0, 5.0, 0.1),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(openDrainControl, openDrainInput),
            connection("open-drain-pullup", ref("open-drain-control", "out"),
                ref("open-drain-input", "in")),
            connection("open-drain-pullup-ref", ref("open-drain-control", "return"),
                ref("open-drain-input", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNSUPPORTED_DRIVE", "open-drain-pullup",
            fullPort("open-drain-control", "out"));

        ElectricalBlockContract noSource = block("no-source",
            receiverLoad("first", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            receiverLoad("second", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2));
        result = check(list(noSource),
            connection("missing-source", ref("no-source", "first"),
                ref("no-source", "second")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "NO_DECLARED_SOURCE", "missing-source",
            fullPort("no-source", "first"), fullPort("no-source", "second"));

        ElectricalBlockContract unsupportedSource = block("unsupported-source",
            controlSource("out", "signal", "gnd", ISO_MAIN, 5.0),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract analogLoad = block("analog-load",
            receiverLoadRole("in", ElectricalPortContract.Role.ANALOG,
                "signal", "gnd", ISO_MAIN, 0.0, 5.0, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unsupportedSource, analogLoad),
            connection("role-pair", ref("unsupported-source", "out"),
                ref("analog-load", "in")),
            connection("role-pair-ref", ref("unsupported-source", "return"),
                ref("analog-load", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNSUPPORTED_ROLE_PAIR", "role-pair",
            fullPort("unsupported-source", "out"), fullPort("analog-load", "in"));
    }

    private static void digitalCases() {
        ElectricalBlockContract source = block("digital-source",
            digitalSource("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.ActiveLevel.HIGH, 0.4, 4.4),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract receiver = block("digital-receiver",
            digitalReceiver("in", "signal", "gnd", ISO_MAIN, 0.0, 5.0,
                ElectricalPortContract.ActiveLevel.HIGH, 1.5, 3.5),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        PortCompatibilityPreflight.Result result = check(list(source, receiver),
            connection("digital-ok", ref("digital-source", "out"),
                ref("digital-receiver", "in")),
            connection("digital-ok-ref", ref("digital-source", "return"),
                ref("digital-receiver", "return")));
        expectDecision("COMPATIBLE", result);

        ElectricalBlockContract lowSource = block("low-digital-source",
            digitalSource("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.ActiveLevel.HIGH, 2.0, 4.0),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(lowSource, receiver),
            connection("digital-level", ref("low-digital-source", "out"),
                ref("digital-receiver", "in")),
            connection("digital-level-ref", ref("low-digital-source", "return"),
                ref("digital-receiver", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "DIGITAL_LEVEL_MISMATCH", "digital-level",
            fullPort("low-digital-source", "out"), fullPort("digital-receiver", "in"));

        ElectricalBlockContract lowActiveReceiver = block("low-active-receiver",
            digitalReceiver("in", "signal", "gnd", ISO_MAIN, 0.0, 5.0,
                ElectricalPortContract.ActiveLevel.LOW, 1.5, 3.5),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(source, lowActiveReceiver),
            connection("active-level", ref("digital-source", "out"),
                ref("low-active-receiver", "in")),
            connection("active-level-ref", ref("digital-source", "return"),
                ref("low-active-receiver", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "ACTIVE_LEVEL_MISMATCH", "active-level",
            fullPort("digital-source", "out"), fullPort("low-active-receiver", "in"));

        ElectricalBlockContract unknown = block("unknown-digital-source",
            unknownDigitalSource("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unknown, receiver),
            connection("digital-unknown", ref("unknown-digital-source", "out"),
                ref("digital-receiver", "in")),
            connection("digital-unknown-ref", ref("unknown-digital-source", "return"),
                ref("digital-receiver", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "REQUIRED_INFORMATION_MISSING", "digital-unknown",
            fullPort("unknown-digital-source", "out"), fullPort("digital-receiver", "in"));

        ElectricalBlockContract naRequired = block("na-digital-source",
            digitalSourceWithRange("out", "signal", "gnd", ISO_MAIN, 5.0,
                ElectricalPortContract.Range.notApplicable(),
                ElectricalPortContract.ActiveLevel.HIGH, 0.4, 4.4),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(naRequired, receiver),
            connection("digital-na", ref("na-digital-source", "out"),
                ref("digital-receiver", "in")),
            connection("digital-na-ref", ref("na-digital-source", "return"),
                ref("digital-receiver", "return")));
        expectDecision("MALFORMED", result);
        expectDiagnostic(result, "REQUIRED_FIELD_NOT_APPLICABLE", "digital-na",
            fullPort("na-digital-source", "out"));
    }

    private static void loadingAndAccessibilityCases() {
        ElectricalBlockContract source = block("capacity-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 0.3,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract loadA = block("capacity-a",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract loadB = block("capacity-b",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        PortCompatibilityPreflight.Result result = check(list(source, loadA, loadB),
            connection("capacity-sum", ref("capacity-source", "out"),
                ref("capacity-a", "in"), ref("capacity-b", "in")),
            connection("capacity-return", ref("capacity-source", "return"),
                ref("capacity-a", "return"), ref("capacity-b", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "LOAD_CAPACITY_EXCEEDED", "capacity-sum",
            fullPort("capacity-source", "out"), fullPort("capacity-a", "in"),
            fullPort("capacity-b", "in"));

        ElectricalBlockContract unknownLoading = block("unknown-loading",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract loadUnknown = block("load-unknown",
            receiverLoadUnknown("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unknownLoading, loadUnknown),
            connection("load-unknown", ref("unknown-loading", "out"),
                ref("load-unknown", "in")),
            connection("load-unknown-ref", ref("unknown-loading", "return"),
                ref("load-unknown", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNKNOWN_LOADING", "load-unknown",
            fullPort("load-unknown", "in"));

        ElectricalBlockContract inaccessible = block("inaccessible",
            receiverLoadWithAccess("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5,
                0.2, ElectricalPortContract.AccessRequirement.CONNECTABLE,
                ElectricalPortContract.AccessProvision.INACCESSIBLE),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unknownLoading, inaccessible),
            connection("access-bad", ref("unknown-loading", "out"),
                ref("inaccessible", "in")),
            connection("access-bad-ref", ref("unknown-loading", "return"),
                ref("inaccessible", "return")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "ACCESSIBILITY_UNMET", "access-bad",
            fullPort("inaccessible", "in"));

        ElectricalBlockContract accessUnknown = block("access-unknown",
            receiverLoadWithAccess("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5,
                0.2, ElectricalPortContract.AccessRequirement.CONNECTABLE,
                ElectricalPortContract.AccessProvision.UNKNOWN),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(unknownLoading, accessUnknown),
            connection("access-unknown", ref("unknown-loading", "out"),
                ref("access-unknown", "in")),
            connection("access-unknown-ref", ref("unknown-loading", "return"),
                ref("access-unknown", "return")));
        expectDecision("INSUFFICIENT_INFORMATION", result);
        expectDiagnostic(result, "UNKNOWN_ACCESSIBILITY", "access-unknown",
            fullPort("access-unknown", "in"));
    }

    private static void malformedProposalCases() {
        ElectricalBlockContract source = block("known-source",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract load = block("known-load",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        PortCompatibilityPreflight.Result result = check(list(source, load),
            connection("unknown-port", ref("known-source", "out"),
                ref("known-load", "missing")));
        expectDecision("MALFORMED", result);
        expectDiagnostic(result, "UNKNOWN_PORT", "unknown-port",
            fullPort("known-load", "missing"));

        result = check(list(source, load),
            connection("unknown-block", ref("missing-block", "out"),
                ref("known-load", "in")));
        expectDecision("MALFORMED", result);
        expectDiagnostic(result, "UNKNOWN_PORT", "unknown-block",
            fullPort("missing-block", "out"));

        List<ElectricalBlockContract> malformedBlocks = list(source, null);
        result = PortCompatibilityPreflight.check(DEVICE, 1, malformedBlocks,
            Collections.<ElectricalConnection>emptyList());
        expectDecision("MALFORMED", result);
        expectReason(result, "MALFORMED_DESCRIPTOR");
    }

    private static void determinismAndPurityCases() {
        ElectricalBlockContract source = block("det-source",
            sourceRail("a", "signal", "gnd", ISO_MAIN, 5.0, 0.3,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            sourceRailWithDrive("b", ElectricalPortContract.Drive.STIFF_VOLTAGE,
                "signal", "gnd", ISO_MAIN, 5.0, 0.3,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract loadA = block("det-load-a",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract loadB = block("det-load-b",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalConnection signal = connection("det-signal", ref("det-source", "a"),
            ref("det-source", "b"), ref("det-load-a", "in"), ref("det-load-b", "in"));
        ElectricalConnection returns = connection("det-returns", ref("det-source", "return"),
            ref("det-load-a", "return"), ref("det-load-b", "return"));
        List<ElectricalBlockContract> blocks = list(source, loadA, loadB);
        List<ElectricalConnection> connections = list(signal, returns);
        final PortCompatibilityPreflight.Result first = PortCompatibilityPreflight.check(DEVICE, 1,
            blocks, connections);
        String firstSignature = resultSignature(first);
        PortCompatibilityPreflight.Result second = PortCompatibilityPreflight.check(DEVICE, 1,
            list(loadB, source, loadA), list(returns, signal));
        equal(firstSignature, resultSignature(second));
        equal(firstSignature, resultSignature(PortCompatibilityPreflight.check(DEVICE, 1,
            blocks, connections)));
        expectDecision("INCOMPATIBLE", first);
        expectDiagnostic(first, "CONFLICTING_DRIVERS", "det-signal",
            fullPort("det-source", "a"), fullPort("det-source", "b"));
        blocks.clear();
        connections.clear();
        equal(firstSignature, resultSignature(first));
        unmodifiable(new Runnable() { public void run() {
            first.getDiagnostics().clear();
        }});

        final List<PortSpec> aliasSpecs = list(
            sourceRail("one", "same-net", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            sourceRailWithDrive("two", ElectricalPortContract.Drive.STIFF_VOLTAGE,
                "same-net", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)));
        ElectricalBlockContract aliasBlock = block("local-alias", aliasSpecs);
        ElectricalBlockContract aliasLoad = block("local-alias-load",
            receiverLoad("in", "load-net", "gnd", ISO_MAIN, 4.5, 5.5, 0.2));
        PortCompatibilityPreflight.Result aliasResult = check(list(aliasBlock, aliasLoad),
            connection("hidden-alias", ref("local-alias", "one"),
                ref("local-alias-load", "in")));
        expectDecision("INCOMPATIBLE", aliasResult);
        expectDiagnostic(aliasResult, "CONFLICTING_DRIVERS", "hidden-alias",
            fullPort("local-alias", "one"), fullPort("local-alias", "two"));

        ElectricalBlockContract splitA = block("split-a",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract splitB = block("split-b",
            sourceRail("out", "signal", "gnd", ISO_MAIN, 5.0, 1.0,
                ElectricalPortContract.Range.known(4.75, 5.25)),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract splitLoad = block("split-load",
            receiverLoad("in", "signal", "gnd", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "gnd", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        aliasResult = check(list(splitA, splitB, splitLoad),
            connection("split-a", ref("split-a", "out"), ref("split-load", "in")),
            connection("split-b", ref("split-b", "out"), ref("split-load", "in")),
            connection("split-a-ref", ref("split-a", "return"), ref("split-load", "return")),
            connection("split-b-ref", ref("split-b", "return"), ref("split-load", "return")));
        expectDecision("INCOMPATIBLE", aliasResult);
        expectDiagnostic(aliasResult, "CONFLICTING_DRIVERS", "split-a",
            fullPort("split-a", "out"), fullPort("split-b", "out"));
    }

    private static void adapterContractCases() {
        ElectricalBlockContract source12 = block("adapter-source",
            sourceRail("out", "signal", "ref-a", ISO_MAIN, 12.0, 2.0,
                ElectricalPortContract.Range.known(11.0, 13.0)),
            returnPort("return", "ref-a", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract load5 = block("adapter-load",
            receiverLoad("in", "signal", "ref-b", ISO_MAIN, 4.5, 5.5, 0.2),
            returnPort("return", "ref-b", ISO_MAIN,
                ElectricalPortContract.MergePolicy.ALLOW));

        for (ElectricalBlockContract.AdapterKind kind : new ElectricalBlockContract.AdapterKind[] {
                ElectricalBlockContract.AdapterKind.REGULATOR,
                ElectricalBlockContract.AdapterKind.DIVIDER,
                ElectricalBlockContract.AdapterKind.LEVEL_SHIFTER,
                ElectricalBlockContract.AdapterKind.RELAY,
                ElectricalBlockContract.AdapterKind.ISOLATION_BARRIER }) {
            boolean isolated = kind == ElectricalBlockContract.AdapterKind.RELAY
                || kind == ElectricalBlockContract.AdapterKind.ISOLATION_BARRIER;
            ElectricalBlockContract adapter = adapterBlock("adapter-" + kind.name().toLowerCase(),
                kind, isolated);
            equal(1, adapter.getAdapters().size());
            equal(kind, adapter.getAdapters().get("a").getKind());
            equal(isolated, adapter.getAdapters().get("a").isIsolated());
            equal("net/in-net", adapter.getAttachmentKey("in"));
            equal("net/out-net", adapter.getAttachmentKey("out"));
        }

        ElectricalBlockContract regulator = adapterBlock("regulator",
            ElectricalBlockContract.AdapterKind.REGULATOR, false);
        PortCompatibilityPreflight.Result result = check(list(source12, regulator, load5),
            connection("regulator-in", ref("adapter-source", "out"), ref("regulator", "in")),
            connection("regulator-in-ref", ref("adapter-source", "return"),
                ref("regulator", "in-return")),
            connection("regulator-out", ref("regulator", "out"), ref("adapter-load", "in")),
            connection("regulator-out-ref", ref("regulator", "out-return"),
                ref("adapter-load", "return")));
        expectDecision("COMPATIBLE", result);

        result = check(list(regulator),
            connection("regulator-bypass", ref("regulator", "in"), ref("regulator", "out")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "ADAPTER_BYPASS", "regulator-bypass",
            fullPort("regulator", "in"), fullPort("regulator", "out"));

        ElectricalBlockContract isolated = adapterBlock("isolated-shifter",
            ElectricalBlockContract.AdapterKind.LEVEL_SHIFTER, true);
        ElectricalBlockContract isolatedSource = block("isolated-source",
            sourceRail("out", "signal", "in-ref", "iso-in", 12.0, 2.0,
                ElectricalPortContract.Range.known(11.0, 13.0)),
            returnPort("return", "in-ref", "iso-in",
                ElectricalPortContract.MergePolicy.ALLOW));
        ElectricalBlockContract isolatedLoad = block("isolated-load-5",
            receiverLoad("in", "signal", "out-ref", "iso-out", 4.5, 5.5, 0.2),
            returnPort("return", "out-ref", "iso-out",
                ElectricalPortContract.MergePolicy.ALLOW));
        result = check(list(isolatedSource, isolated, isolatedLoad),
            connection("isolated-side-in", ref("isolated-source", "out"),
                ref("isolated-shifter", "in")),
            connection("isolated-side-in-ref", ref("isolated-source", "return"),
                ref("isolated-shifter", "in-return")),
            connection("isolated-side-out", ref("isolated-shifter", "out"),
                ref("isolated-load-5", "in")),
            connection("isolated-side-out-ref", ref("isolated-shifter", "out-return"),
                ref("isolated-load-5", "return")));
        expectDecision("COMPATIBLE", result);

        result = check(list(isolated),
            connection("isolated-bypass", ref("isolated-shifter", "in"),
                ref("isolated-shifter", "out")));
        expectDecision("INCOMPATIBLE", result);
        expectDiagnostic(result, "ADAPTER_BYPASS", "isolated-bypass",
            fullPort("isolated-shifter", "in"), fullPort("isolated-shifter", "out"));
    }

    private static void legacyInputAdapterCases() {
        List<ExternalBoardPowerInput> inputs = list(
            new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2", "VIN", "GND"),
            new ExternalBoardPowerInput("AUX_INPUT", "J2.1", "J2.2", "AUX", "GND"));
        List<PowerInputNameplate> nameplates = list(
            new PowerInputNameplate("VIN_INPUT", 12.0));
        List<BoardPad> pads = list(
            new BoardPad("J1.1", "J1", "1", "VIN"),
            new BoardPad("J1.2", "J1", "2", "GND"),
            new BoardPad("J2.1", "J2", "1", "AUX"),
            new BoardPad("J2.2", "J2", "2", "GND"));
        Map<String, ElectricalPortContract.Role> roles = new HashMap<String, ElectricalPortContract.Role>();
        roles.put("VIN_INPUT", ElectricalPortContract.Role.RAIL);
        roles.put("AUX_INPUT", ElectricalPortContract.Role.CONTROL);
        final ElectricalBlockContract block = LegacyInputPortMetadata.adapt("legacy", inputs,
            nameplates, pads, roles);
        equal("legacy-external-inputs", block.getDescriptor().getTypeId());
        equal(1, block.getDescriptor().getSchemaVersion());
        equal("legacy", block.getDescriptor().getInstanceKey());
        equal(4, block.getDescriptor().getPads().size());
        equal("J1", block.getDescriptor().getEndpoints().get("J1.1").getComponentId());
        equal("1", block.getDescriptor().getEndpoints().get("J1.1").getTerminalId());
        equal("VIN", block.getDescriptor().getPads().get("J1.1").getNetId());
        equal("GND", block.getDescriptor().getPads().get("J1.2").getNetId());
        equal("GND", block.getPorts().get("VIN_INPUT").getDomain().getReferenceNetId());
        equal("net/VIN", block.getAttachmentKey("VIN_INPUT"));
        equal("net/GND", block.getAttachmentKey("VIN_INPUT.return"));
        equal(ElectricalPortContract.Role.RAIL, block.getPorts().get("VIN_INPUT").getRole());
        equal(ElectricalPortContract.Role.CONTROL, block.getPorts().get("AUX_INPUT").getRole());
        equal(ElectricalPortContract.State.KNOWN,
            block.getPorts().get("VIN_INPUT").getNominalVoltage().getState());
        equal(12.0, block.getPorts().get("VIN_INPUT").getNominalVoltage().getValue());
        equal(ElectricalPortContract.State.UNKNOWN,
            block.getPorts().get("AUX_INPUT").getNominalVoltage().getState());
        equal(ElectricalPortContract.State.NOT_APPLICABLE,
            block.getPorts().get("VIN_INPUT.return").getGuaranteedVoltage().getState());
        equal(ElectricalPortContract.State.UNKNOWN,
            block.getPorts().get("VIN_INPUT").getAllowedVoltage().getState());
        equal(ElectricalPortContract.State.UNKNOWN,
            block.getPorts().get("VIN_INPUT").getDemandAmps().getState());
        equal(ElectricalPortContract.MergePolicy.UNKNOWN,
            block.getPorts().get("VIN_INPUT").getMergePolicy());
        equal(null, block.getPorts().get("VIN_INPUT").getDomain().getIsolationId());
        equal(ElectricalPortContract.AccessRequirement.CONNECTABLE,
            block.getPorts().get("VIN_INPUT").getAccessRequirement());
        equal(ElectricalPortContract.AccessProvision.UNKNOWN,
            block.getPorts().get("VIN_INPUT").getAccessProvision());
        equal(ElectricalPortContract.ActiveLevel.UNKNOWN,
            block.getPorts().get("AUX_INPUT").getDigital().getActiveLevel());
        equal(ElectricalPortContract.ActiveLevel.NOT_APPLICABLE,
            block.getPorts().get("VIN_INPUT").getDigital().getActiveLevel());

        inputs.clear();
        nameplates.clear();
        pads.clear();
        roles.clear();
        equal(4, block.getPorts().size());
        unmodifiable(new Runnable() { public void run() {
            block.getPorts().clear();
        }});

        final List<ExternalBoardPowerInput> missingPadInput = list(
            new ExternalBoardPowerInput("BAD", "X.1", "X.2", "X", "GND"));
        expectElectricalAny(new Runnable() { public void run() {
            LegacyInputPortMetadata.adapt("bad", missingPadInput,
                Collections.<PowerInputNameplate>emptyList(),
                Collections.<BoardPad>emptyList(),
                singletonRole("BAD", ElectricalPortContract.Role.RAIL));
        }});
    }

    private static void lowSideSwitchAdapterCases() {
        final List<ExternalBoardPowerInput> inputs = list(
            new ExternalBoardPowerInput("LOAD_VIN_INPUT", "J1.1", "J1.2",
                "LOAD_SUPPLY", "GND"),
            new ExternalBoardPowerInput("CONTROL_VIN_INPUT", "J2.1", "J2.2",
                "CONTROL_INPUT", "GND"));
        final List<PowerInputNameplate> nameplates = list(
            new PowerInputNameplate("LOAD_VIN_INPUT", 12.0),
            new PowerInputNameplate("CONTROL_VIN_INPUT", 5.0));
        List<BoardPad> pads = list(
            new BoardPad("J1.1", "J1", "1", "LOAD_SUPPLY"),
            new BoardPad("J1.2", "J1", "2", "GND"),
            new BoardPad("J2.1", "J2", "1", "CONTROL_INPUT"),
            new BoardPad("J2.2", "J2", "2", "GND"));
        ElectricalBlockContract block = LowSideSwitchInputMetadata.adapt("switch", inputs,
            nameplates, pads);
        equal(4, block.getPorts().size());
        equal(ElectricalPortContract.Role.RAIL,
            block.getPorts().get("LOAD_VIN_INPUT").getRole());
        equal(ElectricalPortContract.Role.CONTROL,
            block.getPorts().get("CONTROL_VIN_INPUT").getRole());
        equal(ElectricalPortContract.Role.RETURN,
            block.getPorts().get("LOAD_VIN_INPUT.return").getRole());
        equal(ElectricalPortContract.Role.RETURN,
            block.getPorts().get("CONTROL_VIN_INPUT.return").getRole());
        equal("J1", block.getDescriptor().getEndpoints().get("J1.1").getComponentId());
        equal("1", block.getDescriptor().getEndpoints().get("J1.1").getTerminalId());
        equal("LOAD_SUPPLY", block.getDescriptor().getPads().get("J1.1").getNetId());
        equal("CONTROL_INPUT", block.getDescriptor().getPads().get("J2.1").getNetId());
        equal(12.0, block.getPorts().get("LOAD_VIN_INPUT").getNominalVoltage().getValue());
        equal(5.0, block.getPorts().get("CONTROL_VIN_INPUT").getNominalVoltage().getValue());
        equal("GND", block.getPorts().get("LOAD_VIN_INPUT").getDomain().getReferenceNetId());
        equal("GND", block.getPorts().get("CONTROL_VIN_INPUT").getDomain().getReferenceNetId());
        equal(ElectricalPortContract.State.UNKNOWN,
            block.getPorts().get("LOAD_VIN_INPUT").getAllowedVoltage().getState());
        equal(ElectricalPortContract.State.UNKNOWN,
            block.getPorts().get("CONTROL_VIN_INPUT").getAllowedVoltage().getState());

        final List<ExternalBoardPowerInput> wrongInputNames = list(
            new ExternalBoardPowerInput("LOAD_VIN_INPUT", "J1.1", "J1.2",
                "LOAD_SUPPLY", "GND"),
            new ExternalBoardPowerInput("OTHER", "J2.1", "J2.2",
                "CONTROL_INPUT", "GND"));
        expectElectricalAny(new Runnable() { public void run() {
            LowSideSwitchInputMetadata.adapt("bad-switch", wrongInputNames,
                Collections.<PowerInputNameplate>emptyList(),
                Collections.<BoardPad>emptyList());
        }});

        final List<BoardPad> wrongPadMapping = list(
            new BoardPad("J1.1", "J1", "1", "WRONG"),
            new BoardPad("J1.2", "J1", "2", "GND"),
            new BoardPad("J2.1", "J2", "1", "CONTROL_INPUT"),
            new BoardPad("J2.2", "J2", "2", "GND"));
        expectElectricalAny(new Runnable() { public void run() {
            LowSideSwitchInputMetadata.adapt("bad-pad", inputs, nameplates, wrongPadMapping);
        }});
    }

    private static void legacyRoleValidationCases() {
        final List<ExternalBoardPowerInput> inputs = list(
            new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2", "VIN", "GND"));
        final List<BoardPad> pads = list(
            new BoardPad("J1.1", "J1", "1", "VIN"),
            new BoardPad("J1.2", "J1", "2", "GND"));
        final List<PowerInputNameplate> noNameplates =
            Collections.<PowerInputNameplate>emptyList();

        final Map<String, ElectricalPortContract.Role> nullKey =
            new HashMap<String, ElectricalPortContract.Role>();
        nullKey.put(null, ElectricalPortContract.Role.RAIL);
        expectElectricalField(ElectricalContractException.Code.INVALID_ID, "input.role.id",
            new Runnable() { public void run() {
                LegacyInputPortMetadata.adapt("role-null-key", inputs, noNameplates,
                    pads, nullKey);
            }});

        final Map<String, ElectricalPortContract.Role> nullValue =
            new HashMap<String, ElectricalPortContract.Role>();
        nullValue.put("VIN_INPUT", null);
        expectElectricalField(ElectricalContractException.Code.MISSING_FIELD, "input.role",
            new Runnable() { public void run() {
                LegacyInputPortMetadata.adapt("role-null-value", inputs, noNameplates,
                    pads, nullValue);
            }});

        final Map<String, ElectricalPortContract.Role> wrongRole =
            singletonRole("VIN_INPUT", ElectricalPortContract.Role.PASSIVE);
        expectElectricalField(ElectricalContractException.Code.CONTRADICTORY_FIELD, "input.role",
            new Runnable() { public void run() {
                LegacyInputPortMetadata.adapt("role-wrong", inputs, noNameplates,
                    pads, wrongRole);
            }});
    }

    private static FunctionalBlockDescriptor descriptor(String instance, List<PortSpec> specs) {
        ArrayList<String> nets = new ArrayList<String>();
        ArrayList<FunctionalBlockDescriptor.Role> roles = new ArrayList<FunctionalBlockDescriptor.Role>();
        ArrayList<FunctionalBlockDescriptor.Port> ports = new ArrayList<FunctionalBlockDescriptor.Port>();
        Set<String> seenRoles = new HashSet<String>();
        for (PortSpec spec : specs) {
            if (!nets.contains(spec.attachmentNet)) nets.add(spec.attachmentNet);
            if (!nets.contains(spec.referenceNet)) nets.add(spec.referenceNet);
            String roleId = "role_" + spec.id;
            if (seenRoles.add(roleId)) {
                roles.add(new FunctionalBlockDescriptor.Role(roleId,
                    FunctionalBlockDescriptor.Requirement.REQUIRED,
                    list(new FunctionalBlockDescriptor.LocalRef(
                        FunctionalBlockDescriptor.EntityKind.NET, spec.attachmentNet))));
            }
            ports.add(new FunctionalBlockDescriptor.Port(spec.id, roleId,
                new FunctionalBlockDescriptor.LocalRef(
                    FunctionalBlockDescriptor.EntityKind.NET, spec.attachmentNet)));
        }
        return new FunctionalBlockDescriptor("task45-fixture", 1, instance,
            Collections.<FunctionalBlockDescriptor.Parameter>emptyList(),
            Collections.<FunctionalBlockDescriptor.Component>emptyList(), nets,
            Collections.<FunctionalBlockDescriptor.Endpoint>emptyList(),
            Collections.<FunctionalBlockDescriptor.Pad>emptyList(), roles, ports);
    }

    private static ElectricalBlockContract block(String instance, PortSpec... specs) {
        return block(instance, list(specs));
    }

    private static ElectricalBlockContract block(String instance, List<PortSpec> specs) {
        FunctionalBlockDescriptor descriptor = descriptor(instance, specs);
        ArrayList<ElectricalPortContract> ports = new ArrayList<ElectricalPortContract>();
        for (PortSpec spec : specs) ports.add(spec.port);
        return new ElectricalBlockContract(descriptor, ports,
            Collections.<ElectricalBlockContract.Adapter>emptyList());
    }

    private static ElectricalBlockContract adapterBlock(String instance,
            ElectricalBlockContract.AdapterKind kind, boolean isolated) {
        String firstRef = isolated ? "ref-in" : "ref";
        String secondRef = isolated ? "ref-out" : "ref";
        String firstIso = isolated ? "iso-in" : ISO_MAIN;
        String secondIso = isolated ? "iso-out" : ISO_MAIN;
        PortSpec input = new PortSpec("in", ElectricalPortContract.Role.RAIL,
            ElectricalPortContract.Direction.INPUT, ElectricalPortContract.Behavior.SINK,
            ElectricalPortContract.Drive.NONE, "in-net", firstRef, firstIso,
            ElectricalPortContract.Scalar.known(12.0),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Range.known(11.0, 13.0),
            ElectricalPortContract.Loading.BOUNDED_CURRENT,
            ElectricalPortContract.Scalar.notApplicable(),
            ElectricalPortContract.Scalar.known(2.0), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
        PortSpec output = new PortSpec("out", ElectricalPortContract.Role.RAIL,
            ElectricalPortContract.Direction.OUTPUT, ElectricalPortContract.Behavior.SOURCE,
            ElectricalPortContract.Drive.STIFF_VOLTAGE, "out-net", secondRef, secondIso,
            ElectricalPortContract.Scalar.known(5.0),
            ElectricalPortContract.Range.known(4.75, 5.25),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE,
            ElectricalPortContract.Scalar.known(1.0),
            ElectricalPortContract.Scalar.notApplicable(), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
        PortSpec inReturn = returnPort("in-return", firstRef, firstIso,
            ElectricalPortContract.MergePolicy.ALLOW);
        PortSpec outReturn = returnPort("out-return", secondRef, secondIso,
            ElectricalPortContract.MergePolicy.ALLOW);
        FunctionalBlockDescriptor descriptor = descriptor(instance,
            list(input, output, inReturn, outReturn));
        return new ElectricalBlockContract(descriptor,
            list(input.port, output.port, inReturn.port, outReturn.port),
            list(new ElectricalBlockContract.Adapter("a", kind, "in", "out", isolated)));
    }

    private static PortSpec sourceRail(String id, String attachment, String reference,
            String isolation, double nominal, double capacity,
            ElectricalPortContract.Range guaranteed) {
        return sourceRailWithDrive(id, ElectricalPortContract.Drive.STIFF_VOLTAGE,
            attachment, reference, isolation, nominal, capacity, guaranteed);
    }

    private static PortSpec sourceRailWithDrive(String id, ElectricalPortContract.Drive drive,
            String attachment, String reference, String isolation, double nominal,
            double capacity, ElectricalPortContract.Range guaranteed) {
        return new PortSpec(id, ElectricalPortContract.Role.RAIL,
            ElectricalPortContract.Direction.OUTPUT,
            ElectricalPortContract.Behavior.SOURCE, drive, attachment, reference, isolation,
            ElectricalPortContract.Scalar.known(nominal), guaranteed,
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE,
            ElectricalPortContract.Scalar.known(capacity),
            ElectricalPortContract.Scalar.notApplicable(), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec receiverLoad(String id, String attachment, String reference,
            String isolation, double minimum, double maximum, double demand) {
        return receiverLoadRole(id, ElectricalPortContract.Role.LOAD, attachment, reference,
            isolation, minimum, maximum, demand);
    }

    private static PortSpec receiverLoadRole(String id, ElectricalPortContract.Role role,
            String attachment, String reference, String isolation, double minimum,
            double maximum, double demand) {
        return new PortSpec(id, role, ElectricalPortContract.Direction.INPUT,
            ElectricalPortContract.Behavior.PASSIVE, ElectricalPortContract.Drive.NONE,
            attachment, reference, isolation, ElectricalPortContract.Scalar.unknown(),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Range.known(minimum, maximum),
            ElectricalPortContract.Loading.BOUNDED_CURRENT,
            ElectricalPortContract.Scalar.notApplicable(),
            ElectricalPortContract.Scalar.known(demand), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec receiverLoadUnknown(String id, String attachment, String reference,
            String isolation, double minimum, double maximum) {
        return new PortSpec(id, ElectricalPortContract.Role.LOAD,
            ElectricalPortContract.Direction.INPUT, ElectricalPortContract.Behavior.PASSIVE,
            ElectricalPortContract.Drive.NONE, attachment, reference, isolation,
            ElectricalPortContract.Scalar.unknown(), ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Range.known(minimum, maximum),
            ElectricalPortContract.Loading.UNKNOWN,
            ElectricalPortContract.Scalar.notApplicable(),
            ElectricalPortContract.Scalar.unknown(), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec receiverLoadWithAccess(String id, String attachment, String reference,
            String isolation, double minimum, double maximum, double demand,
            ElectricalPortContract.AccessRequirement requirement,
            ElectricalPortContract.AccessProvision provision) {
        PortSpec result = receiverLoad(id, attachment, reference, isolation, minimum, maximum, demand);
        return result.withAccess(requirement, provision);
    }

    private static PortSpec receiverRail(String id, String attachment, String reference,
            String isolation, double minimum, double maximum) {
        return new PortSpec(id, ElectricalPortContract.Role.RAIL,
            ElectricalPortContract.Direction.INPUT, ElectricalPortContract.Behavior.PASSIVE,
            ElectricalPortContract.Drive.NONE, attachment, reference, isolation,
            ElectricalPortContract.Scalar.unknown(), ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Range.known(minimum, maximum),
            ElectricalPortContract.Loading.BOUNDED_CURRENT,
            ElectricalPortContract.Scalar.notApplicable(), ElectricalPortContract.Scalar.known(0.1),
            naDigital(), ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec passivePort(String id, String attachment, String reference,
            String isolation, double minimum, double maximum) {
        return new PortSpec(id, ElectricalPortContract.Role.PASSIVE,
            ElectricalPortContract.Direction.BIDIRECTIONAL,
            ElectricalPortContract.Behavior.PASSIVE, ElectricalPortContract.Drive.NONE,
            attachment, reference, isolation, ElectricalPortContract.Scalar.unknown(),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Range.known(minimum, maximum),
            ElectricalPortContract.Loading.BOUNDED_CURRENT,
            ElectricalPortContract.Scalar.notApplicable(), ElectricalPortContract.Scalar.known(0.1),
            naDigital(), ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec sinkPort(String id, ElectricalPortContract.Role role,
            String attachment, String reference, String isolation) {
        return new PortSpec(id, role, ElectricalPortContract.Direction.OUTPUT,
            ElectricalPortContract.Behavior.SINK, ElectricalPortContract.Drive.OPEN_DRAIN,
            attachment, reference, isolation, ElectricalPortContract.Scalar.unknown(),
            ElectricalPortContract.Range.notApplicable(), ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE, ElectricalPortContract.Scalar.notApplicable(),
            ElectricalPortContract.Scalar.notApplicable(), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec controlSource(String id, String attachment, String reference,
            String isolation, double nominal) {
        return new PortSpec(id, ElectricalPortContract.Role.CONTROL,
            ElectricalPortContract.Direction.OUTPUT, ElectricalPortContract.Behavior.SOURCE,
            ElectricalPortContract.Drive.PUSH_PULL, attachment, reference, isolation,
            ElectricalPortContract.Scalar.known(nominal),
            ElectricalPortContract.Range.known(0.0, nominal),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE,
            ElectricalPortContract.Scalar.known(1.0),
            ElectricalPortContract.Scalar.notApplicable(), naDigital(),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec digitalSource(String id, String attachment, String reference,
            String isolation, double nominal, double capacity,
            ElectricalPortContract.ActiveLevel active, double lowMaximum, double highMinimum) {
        return new PortSpec(id, ElectricalPortContract.Role.CONTROL,
            ElectricalPortContract.Direction.OUTPUT, ElectricalPortContract.Behavior.SOURCE,
            ElectricalPortContract.Drive.PUSH_PULL, attachment, reference, isolation,
            ElectricalPortContract.Scalar.known(nominal),
            ElectricalPortContract.Range.known(0.0, nominal),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE,
            ElectricalPortContract.Scalar.known(capacity),
            ElectricalPortContract.Scalar.notApplicable(),
            new ElectricalPortContract.Digital(active,
                ElectricalPortContract.Scalar.known(lowMaximum),
                ElectricalPortContract.Scalar.known(highMinimum),
                ElectricalPortContract.Scalar.notApplicable(),
                ElectricalPortContract.Scalar.notApplicable()),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec digitalSourceWithRange(String id, String attachment, String reference,
            String isolation, double nominal, ElectricalPortContract.Range guaranteed,
            ElectricalPortContract.ActiveLevel active, double lowMaximum, double highMinimum) {
        return new PortSpec(id, ElectricalPortContract.Role.CONTROL,
            ElectricalPortContract.Direction.OUTPUT, ElectricalPortContract.Behavior.SOURCE,
            ElectricalPortContract.Drive.PUSH_PULL, attachment, reference, isolation,
            guaranteed.getState() == ElectricalPortContract.State.KNOWN
                ? ElectricalPortContract.Scalar.known(nominal)
                : ElectricalPortContract.Scalar.notApplicable(), guaranteed,
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE,
            ElectricalPortContract.Scalar.known(1.0),
            ElectricalPortContract.Scalar.notApplicable(),
            new ElectricalPortContract.Digital(active,
                ElectricalPortContract.Scalar.known(lowMaximum),
                ElectricalPortContract.Scalar.known(highMinimum),
                ElectricalPortContract.Scalar.notApplicable(),
                ElectricalPortContract.Scalar.notApplicable()),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec digitalReceiver(String id, String attachment, String reference,
            String isolation, double minimum, double maximum,
            ElectricalPortContract.ActiveLevel active, double inputLowMaximum,
            double inputHighMinimum) {
        return new PortSpec(id, ElectricalPortContract.Role.CONTROL,
            ElectricalPortContract.Direction.INPUT, ElectricalPortContract.Behavior.PASSIVE,
            ElectricalPortContract.Drive.NONE, attachment, reference, isolation,
            ElectricalPortContract.Scalar.unknown(), ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Range.known(minimum, maximum),
            ElectricalPortContract.Loading.BOUNDED_CURRENT,
            ElectricalPortContract.Scalar.notApplicable(), ElectricalPortContract.Scalar.known(0.1),
            new ElectricalPortContract.Digital(active,
                ElectricalPortContract.Scalar.notApplicable(),
                ElectricalPortContract.Scalar.notApplicable(),
                ElectricalPortContract.Scalar.known(inputLowMaximum),
                ElectricalPortContract.Scalar.known(inputHighMinimum)),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec unknownDigitalSource(String id, String attachment, String reference,
            String isolation, double nominal, double capacity) {
        return new PortSpec(id, ElectricalPortContract.Role.CONTROL,
            ElectricalPortContract.Direction.OUTPUT, ElectricalPortContract.Behavior.SOURCE,
            ElectricalPortContract.Drive.PUSH_PULL, attachment, reference, isolation,
            ElectricalPortContract.Scalar.known(nominal),
            ElectricalPortContract.Range.known(0.0, nominal),
            ElectricalPortContract.Range.notApplicable(),
            ElectricalPortContract.Loading.NONE,
            ElectricalPortContract.Scalar.known(capacity),
            ElectricalPortContract.Scalar.notApplicable(),
            new ElectricalPortContract.Digital(ElectricalPortContract.ActiveLevel.UNKNOWN,
                ElectricalPortContract.Scalar.unknown(), ElectricalPortContract.Scalar.unknown(),
                ElectricalPortContract.Scalar.notApplicable(),
                ElectricalPortContract.Scalar.notApplicable()),
            ElectricalPortContract.MergePolicy.ALLOW,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static PortSpec returnPort(String id, String reference, String isolation,
            ElectricalPortContract.MergePolicy mergePolicy) {
        return new PortSpec(id, ElectricalPortContract.Role.RETURN,
            ElectricalPortContract.Direction.BIDIRECTIONAL,
            ElectricalPortContract.Behavior.PASSIVE, ElectricalPortContract.Drive.NONE,
            reference, reference, isolation, naScalar(), naRange(), naRange(),
            ElectricalPortContract.Loading.NONE, naScalar(), naScalar(), naDigital(), mergePolicy,
            ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static ElectricalConnection connection(String id,
            ElectricalConnection.PortRef... refs) {
        return new ElectricalConnection(id, list(refs));
    }

    private static ElectricalConnection.PortRef ref(String block, String port) {
        return new ElectricalConnection.PortRef(block, port);
    }

    private static String fullPort(String block, String port) {
        return "tsj-block-v1/device@1/" + block + "/port/" + port;
    }

    private static PortSpec naAccess(PortSpec spec) {
        return spec.withAccess(ElectricalPortContract.AccessRequirement.NONE,
            ElectricalPortContract.AccessProvision.NOT_APPLICABLE);
    }

    private static ElectricalPortContract.Scalar naScalar() {
        return ElectricalPortContract.Scalar.notApplicable();
    }

    private static ElectricalPortContract.Range naRange() {
        return ElectricalPortContract.Range.notApplicable();
    }

    private static ElectricalPortContract.Digital naDigital() {
        return ElectricalPortContract.Digital.notApplicable();
    }

    private static Map<String, ElectricalPortContract.Role> singletonRole(String id,
            ElectricalPortContract.Role role) {
        Map<String, ElectricalPortContract.Role> result =
            new HashMap<String, ElectricalPortContract.Role>();
        result.put(id, role);
        return result;
    }

    private static PortCompatibilityPreflight.Result check(
            List<ElectricalBlockContract> blocks, ElectricalConnection... connections) {
        return PortCompatibilityPreflight.check(DEVICE, 1, blocks, list(connections));
    }

    private static void expectDecision(String expected,
            PortCompatibilityPreflight.Result result) {
        if (!expected.equals(result.getDecision().name()))
            throw new AssertionError("expected " + expected + ", got "
                + resultSignature(result));
        assertions++;
    }

    private static void expectReason(PortCompatibilityPreflight.Result result,
            String code) {
        for (Object diagnostic : result.getDiagnostics()) {
            if (diagnosticCode(diagnostic).equals(code)) return;
        }
        throw new AssertionError("expected diagnostic code " + code + " in "
            + resultSignature(result));
    }

    private static void expectDiagnostic(PortCompatibilityPreflight.Result result,
            String code, String connectionId, String... relevantPorts) {
        for (Object diagnostic : result.getDiagnostics()) {
            if (!diagnosticCode(diagnostic).equals(code)) continue;
            if (!connectionId.equals(diagnosticConnectionId(diagnostic))) continue;
            List<String> actualPorts = diagnosticPortIds(diagnostic);
            boolean allPresent = true;
            for (String expectedPort : relevantPorts) {
                if (!actualPorts.contains(expectedPort)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) return;
        }
        throw new AssertionError("expected " + code + " connection=" + connectionId
            + " ports=" + Arrays.asList(relevantPorts) + " in "
            + resultSignature(result));
    }

    /*
     * Diagnostic is intentionally read through its fixed public contract by
     * direct getters.  The Object parameter keeps this test source-compatible
     * with either Result.Diagnostic or PortCompatibilityPreflight.Diagnostic
     * nesting while still checking every literal field used by the oracle.
     */
    private static String diagnosticCode(Object diagnostic) {
        return ((PortCompatibilityPreflight.Diagnostic) diagnostic).getCode().name();
    }

    private static String diagnosticConnectionId(Object diagnostic) {
        List<String> ids = ((PortCompatibilityPreflight.Diagnostic) diagnostic).getConnectionIds();
        return ids.isEmpty() ? "" : ids.get(0);
    }

    private static List<String> diagnosticPortIds(Object diagnostic) {
        return ((PortCompatibilityPreflight.Diagnostic) diagnostic).getPortIds();
    }

    private static String resultSignature(PortCompatibilityPreflight.Result result) {
        StringBuilder signature = new StringBuilder(result.getDecision().name());
        for (Object diagnostic : result.getDiagnostics()) {
            PortCompatibilityPreflight.Diagnostic typed =
                (PortCompatibilityPreflight.Diagnostic) diagnostic;
            signature.append('|').append(typed.getDecision().name())
                .append(':').append(typed.getCode().name())
                .append(':').append(typed.getConnectionIds())
                .append(':').append(typed.getPortIds())
                .append(':').append(typed.getFieldId());
        }
        return signature.toString();
    }

    private static void expectElectrical(ElectricalContractException.Code code,
            Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (ElectricalContractException expected) {
            equal(code, expected.getCode());
            check(expected.getFieldId() != null && expected.getFieldId().length() > 0,
                "typed electrical failure identifies field");
            return;
        }
        throw new AssertionError("expected electrical contract failure " + code);
    }

    private static void expectElectricalAny(Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (ElectricalContractException expected) {
            check(expected.getCode() != null, "typed adapter failure has code");
            check(expected.getFieldId() != null && expected.getFieldId().length() > 0,
                "typed adapter failure identifies field");
            return;
        }
        throw new AssertionError("expected typed electrical contract failure");
    }

    private static void expectElectricalField(ElectricalContractException.Code code,
            String field, Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (ElectricalContractException expected) {
            equal(code, expected.getCode());
            equal(field, expected.getFieldId());
            if (expected.getEntityId() != null)
                check(expected.getEntityId().length() > 0,
                    "typed failure entity is nonempty when supplied");
            return;
        }
        throw new AssertionError("expected electrical contract failure " + code
            + " field=" + field);
    }

    private static void unmodifiable(Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("mutable collection escaped contract");
    }

    private static void equal(Object expected, Object actual) {
        check(expected == null ? actual == null : expected.equals(actual),
            "expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        return new ArrayList<T>(Arrays.asList(values));
    }

    private static final class PortSpec {
        final String id;
        final ElectricalPortContract.Role role;
        final ElectricalPortContract.Direction direction;
        final ElectricalPortContract.Behavior behavior;
        final ElectricalPortContract.Drive drive;
        final String attachmentNet;
        final String referenceNet;
        final String isolation;
        final ElectricalPortContract.Scalar nominal;
        final ElectricalPortContract.Range guaranteed;
        final ElectricalPortContract.Range allowed;
        final ElectricalPortContract.Loading loading;
        final ElectricalPortContract.Scalar capacity;
        final ElectricalPortContract.Scalar demand;
        final ElectricalPortContract.Digital digital;
        final ElectricalPortContract.MergePolicy mergePolicy;
        final ElectricalPortContract.AccessRequirement accessRequirement;
        final ElectricalPortContract.AccessProvision accessProvision;
        final ElectricalPortContract port;

        PortSpec(String id, ElectricalPortContract.Role role,
                ElectricalPortContract.Direction direction,
                ElectricalPortContract.Behavior behavior,
                ElectricalPortContract.Drive drive, String attachmentNet,
                String referenceNet, String isolation,
                ElectricalPortContract.Scalar nominal,
                ElectricalPortContract.Range guaranteed,
                ElectricalPortContract.Range allowed,
                ElectricalPortContract.Loading loading,
                ElectricalPortContract.Scalar capacity,
                ElectricalPortContract.Scalar demand,
                ElectricalPortContract.Digital digital,
                ElectricalPortContract.MergePolicy mergePolicy,
                ElectricalPortContract.AccessRequirement accessRequirement,
                ElectricalPortContract.AccessProvision accessProvision) {
            this.id = id;
            this.role = role;
            this.direction = direction;
            this.behavior = behavior;
            this.drive = drive;
            this.attachmentNet = attachmentNet;
            this.referenceNet = referenceNet;
            this.isolation = isolation;
            this.nominal = nominal;
            this.guaranteed = guaranteed;
            this.allowed = allowed;
            this.loading = loading;
            this.capacity = capacity;
            this.demand = demand;
            this.digital = digital;
            this.mergePolicy = mergePolicy;
            this.accessRequirement = accessRequirement;
            this.accessProvision = accessProvision;
            this.port = new ElectricalPortContract(id, role, direction, behavior, drive,
                isolation == null ? ElectricalPortContract.Domain.unknownIsolation(referenceNet)
                    : ElectricalPortContract.Domain.known(referenceNet, isolation),
                nominal, guaranteed, allowed, loading, capacity, demand, digital,
                mergePolicy, accessRequirement, accessProvision);
        }

        PortSpec withAccess(ElectricalPortContract.AccessRequirement requirement,
                ElectricalPortContract.AccessProvision provision) {
            return new PortSpec(id, role, direction, behavior, drive, attachmentNet,
                referenceNet, isolation, nominal, guaranteed, allowed, loading, capacity,
                demand, digital, mergePolicy, requirement, provision);
        }
    }
}
