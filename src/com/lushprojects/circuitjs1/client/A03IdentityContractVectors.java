package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * The small, data-only A03 contract corpus shared by the JVM and GWT gates.
 *
 * <p>Every receipt line is derived from the immutable contract objects.  The
 * literal assertions are intentional: they make a change from a transient
 * solver, DSU root, collection position, or drawing coordinate visible in the
 * byte-exact corpus.</p>
 */
public final class A03IdentityContractVectors {
    private static int assertions;

    private A03IdentityContractVectors() {
    }

    /** Return the complete deterministic receipt without allocating a board. */
    public static String run() {
        assertions = 0;
        StringBuilder receipt = new StringBuilder();
        receipt.append("A03_IDENTITY_VECTORS_BEGIN\n");

        NamespaceFixture namespace = namespaceFixture(true, false, false);
        namespaceVectors(namespace, receipt);
        busVectors(namespace, receipt);
        manifestVectors(namespace, receipt);
        seedVectors(namespace, receipt);
        replayVersionVectors(receipt);

        receipt.append("assertions=").append(assertions).append('\n');
        receipt.append("A03_IDENTITY_VECTORS_END\n");
        return receipt.toString();
    }

    public static int getAssertionCount() {
        return assertions;
    }

    private static void namespaceVectors(NamespaceFixture fixture,
            StringBuilder receipt) {
        BlockNamespace namespace = fixture.namespace;
        check(namespace.idFor("u1", EntityKind.ROLE, "control").equals(
                "tsj-realization-v1/a03-device@7/u1/nmos-provider@1/"
                + "nmos-low-side-driver@1/role/control"),
                "role identity changed");
        check(namespace.idFor("u1", EntityKind.PORT, "control").equals(
                "tsj-realization-v1/a03-device@7/u1/nmos-provider@1/"
                + "nmos-low-side-driver@1/port/control"),
                "port identity changed");
        check(namespace.terminalIdFor("u1", "Q1", "G").equals(
                "tsj-realization-v1/a03-device@7/u1/nmos-provider@1/"
                + "nmos-low-side-driver@1/terminal/Q1.G"),
                "terminal identity changed");
        check(namespace.realizationFor("u1").toCanonical().equals(
                "tsj-realization-v1|u1|driver@1|nmos-provider@1|"
                + "nmos-low-side-driver@1"),
                "realization canonical identity changed");

        // Repeated instances remain distinct even when they have the same
        // descriptor type and the same provider/variant.
        check(!namespace.idFor("u1", EntityKind.ROLE, "control").equals(namespace.idFor("u2", EntityKind.ROLE, "control")),
                "repeated block instances collapsed");
        check(!namespace.terminalIdFor("u1", "Q1", "G").equals(
                namespace.terminalIdFor("u2", "Q1", "G")),
                "repeated terminal identities collapsed");
        check(namespace.idFor("u1", EntityKind.ROLE, "control").equals(
                namespace.idFor("u1", EntityKind.ROLE, "control")),
                "role identity was not deterministic");

        NamespaceFixture reordered = namespaceFixture(true, true, false);
        check(namespace.idFor("u1", EntityKind.ROLE, "control").equals(reordered.namespace.idFor("u1", EntityKind.ROLE, "control"))
                && namespace.terminalIdFor("u2", "Q1", "D").equals(
                        reordered.namespace.terminalIdFor("u2", "Q1", "D")),
                "block or realization reorder renamed an existing identity");

        NamespaceFixture withoutUnrelated = namespaceFixture(false, false, false);
        check(namespace.idFor("u1", EntityKind.ROLE, "control").equals(
                withoutUnrelated.namespace.idFor("u1", EntityKind.ROLE, "control"))
                && namespace.idFor("u2", EntityKind.COMPONENT, "Q1").equals(
                        withoutUnrelated.namespace.idFor(
                                "u2", EntityKind.COMPONENT, "Q1")),
                "unrelated block insertion renamed an existing identity");

        final NamespaceFixture alternateProvider = namespaceFixture(true, false, true);
        check(!namespace.idFor("u1", EntityKind.ROLE, "control").equals(
                alternateProvider.namespace.idFor("u1", EntityKind.ROLE, "control"))
                && !namespace.idFor("u1", EntityKind.PORT, "control").equals(
                        alternateProvider.namespace.idFor(
                                "u1", EntityKind.PORT, "control")),
                "provider/variant did not qualify role and port identity");
        check(!namespace.terminalIdFor("u1", "Q1", "G").equals(
                alternateProvider.namespace.terminalIdFor(
                        "u1", "Q2", "GATE"))
                && !namespace.idFor("u1", EntityKind.COMPONENT, "Q1").equals(
                        alternateProvider.namespace.idFor(
                                "u1", EntityKind.COMPONENT, "Q2")),
                "unlike variant internals were falsely assigned one identity");
        expectBlockFailure(new Action() {
            @Override public void run() {
                alternateProvider.namespace.terminalIdFor(
                        "u1", "Q1", "G");
            }
        }, BlockContractException.Code.DANGLING_REFERENCE, "terminalName",
                "missing old variant terminal accepted");
        terminalTupleCanary();

        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                new BlockNamespace("a03-device", 7,
                        Arrays.asList(f.blocks.get(0), f.blocks.get(0)),
                        f.realizations);
            }
        }, "duplicate block instance");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                new BlockNamespace("a03-device", 7, f.blocks,
                        Arrays.asList(f.realizations.get(0), f.realizations.get(0),
                                f.realizations.get(2)));
            }
        }, "duplicate realization instance");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                new BlockNamespace("a03-device", 7, f.blocks,
                        Arrays.asList(f.realizations.get(0), f.realizations.get(1)));
            }
        }, "missing realization");

        // Defensive copies and immutable views are part of the identity
        // boundary, rather than a convenience of the test fixture.
        check(namespace.getRealizations().size() == 3,
                "realizations were not retained");
        expectFailure(new Action() {
            @Override public void run() {
                namespaceFixture(true, false, false).namespace
                        .getRealizations().clear();
            }
        }, "mutable realization view");

        receipt.append("namespace.instance=").append(namespace.idFor("u1", EntityKind.ROLE, "control"))
                .append('\n');
        receipt.append("namespace.component=")
                .append(namespace.idFor("u1", EntityKind.COMPONENT, "Q1"))
                .append('\n');
        receipt.append("namespace.terminal=")
                .append(namespace.terminalIdFor("u1", "Q1", "G"))
                .append('\n');
        receipt.append("case=namespace PASS\n");
    }

    private static void busVectors(NamespaceFixture fixture,
            StringBuilder receipt) {
        DeviceBusBindings buses = fixture.buses;
        String busId = buses.getBusId("shared-load");
        check(busId.equals("tsj-bus-v1/a03-device@7/shared-load"),
                "durable bus identity changed");
        String u1Load = fixture.alias("u1", "load");
        String u2Load = fixture.alias("u2", "load");
        String u3Supply = fixture.alias("u3", "supply");
        Map<String, String> durable = buses.getNetBindings();
        check(busId.equals(durable.get(u2Load))
                && busId.equals(durable.get(u3Supply)),
                "declared electrical aliases did not share the named bus");
        check(durable.get(fixture.alias("u1", "return")).equals(
                fixture.alias("u1", "return")),
                "unbound local net was not retained as a local identity");
        List<String> reverse = buses.getBusAliases().get(busId);
        check(reverse != null && reverse.contains(u2Load)
                && reverse.contains(u3Supply),
                "bus reverse aliases lost durable local aliases");

        // The newly joined u1/load alias is lexically earlier than u2/load.
        NamespaceFixture earlier = namespaceFixture(true, false, false, true);
        String earlierBus = earlier.buses.getBusId("shared-load");
        String originalRepresentative = fixture.aliases.get(u2Load);
        String earlierRepresentative = earlier.aliases.get(
                earlier.alias("u2", "load"));
        check(earlierBus.equals(busId)
                && !earlierRepresentative.equals(originalRepresentative)
                && earlierRepresentative.equals(earlier.alias("u1", "load")),
                "lexically earlier electrical representative renamed the durable bus");
        check(earlier.buses.getNetBindings().get(
                earlier.alias("u1", "load")).equals(earlierBus),
                "earlier alias did not resolve to the named bus");
        check(earlier.buses.getNetBindings().get(
                earlier.alias("u2", "load")).equals(earlierBus),
                "existing alias changed bus membership");

        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                List<DeviceBusBindings.Declaration> declarations = new ArrayList<
                        DeviceBusBindings.Declaration>();
                declarations.add(new DeviceBusBindings.Declaration("shared-load",
                        Arrays.asList(f.alias("u2", "load"),
                                f.alias("u3", "supply")),
                        Arrays.asList("port:LOAD")));
                declarations.add(new DeviceBusBindings.Declaration("shared-load",
                        Arrays.asList(f.alias("u1", "return")),
                        Arrays.asList("port:OTHER")));
                new DeviceBusBindings(f.namespace, declarations, f.aliases);
            }
        }, "duplicate bus semantic key");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                List<DeviceBusBindings.Declaration> declarations = Arrays.asList(
                        new DeviceBusBindings.Declaration("shared-load",
                                Arrays.asList(f.alias("u2", "load"),
                                        f.alias("u3", "supply")),
                                Arrays.asList("port:LOAD")),
                        new DeviceBusBindings.Declaration("other",
                                Arrays.asList(f.alias("u2", "load")),
                                Arrays.asList("port:OTHER")));
                new DeviceBusBindings(f.namespace, declarations, f.aliases);
            }
        }, "duplicate bus anchor");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                List<DeviceBusBindings.Declaration> declarations = Arrays.asList(
                        new DeviceBusBindings.Declaration("shared-load",
                                Arrays.asList(f.alias("u2", "load"),
                                        f.alias("u3", "supply")),
                                Arrays.asList("port:SHARED")),
                        new DeviceBusBindings.Declaration("other",
                                Arrays.asList(f.alias("u3", "ledNode")),
                                Arrays.asList("port:SHARED")));
                new DeviceBusBindings(f.namespace, declarations, f.aliases);
            }
        }, "duplicate external reference");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                TreeMap<String, String> aliases = new TreeMap<String, String>(f.aliases);
                String first = f.alias("u1", "return");
                String second = f.alias("u2", "return");
                aliases.put(second, first);
                new DeviceBusBindings(f.namespace,
                        Collections.<DeviceBusBindings.Declaration>emptyList(), aliases);
            }
        }, "undeclared merged electrical group");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                new DeviceBusBindings(f.namespace,
                        Arrays.asList(new DeviceBusBindings.Declaration("bad",
                                Arrays.asList(f.alias("u2", "load"),
                                        f.alias("u3", "ledNode")),
                                Arrays.asList("port:BAD"))), f.aliases);
            }
        }, "contradictory bus anchors");
        expectFailure(new Action() {
            @Override public void run() {
                NamespaceFixture f = namespaceFixture(true, false, false);
                new DeviceBusBindings(f.namespace,
                        Arrays.asList(new DeviceBusBindings.Declaration("bad",
                                Arrays.asList("u2/load", "u3/supply"),
                                Arrays.asList("port:UNQUALIFIED"))), f.aliases);
            }
        }, "unqualified bus anchor");

        receipt.append("bus.id=").append(busId).append('\n');
        receipt.append("bus.aliases=").append(reverse).append('\n');
        receipt.append("case=bus PASS\n");
    }

    private static void manifestVectors(NamespaceFixture fixture,
            StringBuilder receipt) {
        RealizationManifest manifest = manifest(fixture, 0L);
        String canonical = manifest.toCanonical();
        RealizationManifest parsed = RealizationManifest.parse(canonical);
        check(parsed.toCanonical().equals(canonical),
                "manifest canonical round-trip changed bytes");
        check(parsed.identityCanonical().equals(manifest.identityCanonical()),
                "manifest identity round-trip changed bytes");
        check(canonical.startsWith("tsj-realization/"
                + RealizationManifest.VERSION + "\ndescriptor="),
                "manifest header or descriptor framing changed");
        check(canonical.indexOf("\nversion=") > 0
                && canonical.indexOf("\nchoice=") > 0
                 && canonical.indexOf("\nnet=") > 0
                 && canonical.indexOf("\ntarget=") > 0
                 && canonical.indexOf("\nfuture=") < 0,
                "manifest omitted a required framed field");

        check(RealizationManifest.Choice.number("nominal", 330.0)
                .toCanonical().equals("nominal|number|4074a00000000000"),
                "binary64 number choice is not canonical");
        check(RealizationManifest.Choice.integer("seed", 9007199254740993L)
                .toCanonical().equals("seed|integer|9007199254740993"),
                "exact signed64 choice lost integer precision");
        check(RealizationManifest.Choice.ids("parts", Arrays.asList("z", "a"))
                .toCanonical().equals("parts|ids|a,z"),
                "ID choice ordering is not canonical");
        check(RealizationManifest.Choice.version("layout",
                new ChallengeDescriptor.VersionedId("pcb-layout",
                        SeededPcbLayoutGenerator.CURRENT_VERSION))
                .toCanonical().equals("layout|version|pcb-layout@"
                        + SeededPcbLayoutGenerator.CURRENT_VERSION),
                "version choice is not canonical");

        final String missingVersion = removeFirstRecord(canonical, "version");
        String missingVersionFailure = expectChallengeFailure(new Action() {
            @Override public void run() { RealizationManifest.parse(missingVersion); }
        }, ChallengeContractException.Code.MISSING_FIELD, "versions",
                "missing required version pin");
        final String unknownVersion = canonical.replace(
                "layout|pcb-layout@" + SeededPcbLayoutGenerator.CURRENT_VERSION,
                "layout|pcb-layout@9");
        String unknownVersionFailure = expectChallengeFailure(new Action() {
            @Override public void run() { RealizationManifest.parse(unknownVersion); }
        }, ChallengeContractException.Code.UNSUPPORTED_VERSION, "version.owner",
                "unknown version pin");
        final String duplicateTarget = appendRecord(canonical, "target",
                fixture.namespace.idFor("u1", EntityKind.ROLE, "control"));
        expectFailure(new Action() {
            @Override public void run() { RealizationManifest.parse(duplicateTarget); }
        }, "duplicate target");
        expectFailure(new Action() {
            @Override public void run() {
                RealizationManifest.Choice.parse("n|number|ffffffffffffffff");
            }
        }, "nonfinite number choice");
        final String retiredFuture = appendRecord(canonical, "future", "~");
        expectChallengeFailure(new Action() {
            @Override public void run() { RealizationManifest.parse(retiredFuture); }
        }, ChallengeContractException.Code.UNKNOWN_FIELD, "future",
                "retired future state field");

        // Constructor collections are copied before publication.
        final List<BlockRealizationIdentity> blocks = new ArrayList<
                BlockRealizationIdentity>(fixture.realizations);
        final List<RealizationManifest.VersionPin> versions =
                new ArrayList<RealizationManifest.VersionPin>(pins());
        final List<RealizationManifest.Choice> choices =
                new ArrayList<RealizationManifest.Choice>(choices(0L));
        final List<RealizationManifest.NetBinding> nets =
                new ArrayList<RealizationManifest.NetBinding>(netBindings(fixture));
        final List<String> targets = new ArrayList<String>(targets(fixture));
        final RealizationManifest copied = new RealizationManifest(
                RealizationManifest.VERSION,
                BoundedAssemblyRequest.descriptor(0L), blocks, versions, choices,
                nets, targets);
        blocks.clear();
        versions.clear();
        choices.clear();
        nets.clear();
        targets.clear();
        check(copied.getBlocks().size() == 3 && copied.getVersions().size() == 8
                && copied.getChoices().size() == 4 && copied.getNets().size() == 2,
                "manifest did not defensively copy constructor collections");
        expectFailure(new Action() {
            @Override public void run() { copied.getTargets().clear(); }
        }, "mutable manifest target view");
        expectFailure(new Action() {
            @Override public void run() { copied.getChoices().clear(); }
        }, "mutable manifest choice view");

        receipt.append("manifest.identity=").append(manifest.identityCanonical())
                .append('\n');
        receipt.append("manifest.missing-version=")
                .append(missingVersionFailure).append('\n');
        receipt.append("manifest.unknown-version=")
                .append(unknownVersionFailure).append('\n');
        receipt.append("manifest.canonical=").append(canonical).append('\n');
        receipt.append("case=manifest PASS\n");
    }

    private static void seedVectors(NamespaceFixture fixture,
            StringBuilder receipt) {
        long[] seeds = { 0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE,
                9007199254740993L, -9007199254740993L };
        for (long seed : seeds) {
            RealizationManifest manifest = manifest(fixture, seed);
            RealizationManifest parsed = RealizationManifest.parse(
                    manifest.toCanonical());
            check(parsed.getDescriptor().getRootSeed() == seed,
                    "descriptor seed changed during round-trip: " + seed);
            check(findChoice(parsed.getChoices(), "root-seed").getIntegerValue() == seed,
                    "choice seed changed during round-trip: " + seed);
            check(Long.toString(seed).equals(
                    findChoice(parsed.getChoices(), "root-seed").getCanonicalValue()),
                    "seed canonical decimal changed: " + seed);
        }
        receipt.append("seeds=0,1,-1,9223372036854775807,-9223372036854775808,"
                + "9007199254740993,-9007199254740993\n");
        receipt.append("case=seeds PASS\n");
    }

    private static void replayVersionVectors(StringBuilder receipt) {
        String[] families = { "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR",
                "PARALLEL_DUAL_INDICATOR" };
        long[] seeds = { 0L, Long.MIN_VALUE, Long.MAX_VALUE,
                9007199254740993L };
        for (String family : families) {
            for (long seed : seeds) {
                ChallengeDescriptor descriptor = ChallengeDescriptor.current(family, seed);
                ChallengeDescriptor parsed = ChallengeDescriptor.parse(
                        descriptor.toCanonical());
                check(parsed.getSchemaVersion() == ChallengeDescriptor.SCHEMA_VERSION
                        && parsed.getRootSeed() == seed,
                        "current descriptor schema or seed changed");
                check(parsed.getGenerator().getId().equals("leaf")
                        && parsed.getGenerator().getVersion() == 1,
                        "current leaf generator identity changed");
            }
        }
        receipt.append("current-schema=").append(ChallengeDescriptor.SCHEMA_VERSION)
                .append(" leaf-generator=").append(ChallengeDescriptor.LEAF_GENERATOR_VERSION)
                .append(" geometry=").append(BoundedAssemblyRequest.GEOMETRY_VERSION)
                .append('\n');
        receipt.append("case=replay-version PASS\n");
    }

    /** Component/terminal tuples are length-delimited by ownership, not dots. */
    private static void terminalTupleCanary() {
        FunctionalBlockDescriptor tuple = new FunctionalBlockDescriptor(
                "tuple-block", 1, "tuple", Collections
                        .<FunctionalBlockDescriptor.Parameter>emptyList(),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component("A", "X",
                                Arrays.asList("B.C")),
                        new FunctionalBlockDescriptor.Component("A.B", "X",
                                Arrays.asList("C"))),
                Arrays.asList("n1", "n2"),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Endpoint("e1", "A", "B.C"),
                        new FunctionalBlockDescriptor.Endpoint("e2", "A.B", "C")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Pad("p1", "e1", "n1"),
                        new FunctionalBlockDescriptor.Pad("p2", "e2", "n2")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Role("r1",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(localRef(EntityKind.PAD, "p1"))),
                        new FunctionalBlockDescriptor.Role("r2",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(localRef(EntityKind.PAD, "p2")))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port("p1", "r1",
                                localRef(EntityKind.PAD, "p1")),
                        new FunctionalBlockDescriptor.Port("p2", "r2",
                                localRef(EntityKind.PAD, "p2"))));
        BlockNamespace namespace = new BlockNamespace("tuple-device", 1,
                Arrays.asList(tuple), Arrays.asList(new BlockRealizationIdentity(
                        "tuple", new ChallengeDescriptor.VersionedId("role", 1),
                        new ChallengeDescriptor.VersionedId("provider", 1),
                        new ChallengeDescriptor.VersionedId("tuple-block", 1))));
        String first = namespace.terminalIdFor("tuple", "A", "B.C");
        String second = namespace.terminalIdFor("tuple", "A.B", "C");
        check(!first.equals(second),
                "component/terminal dotted tuple identities collided");
        check(first.equals(namespace.terminalIdFor("tuple", "A", "B.C"))
                && second.equals(namespace.terminalIdFor("tuple", "A.B", "C")),
                "component/terminal tuple identity was construction-order dependent");
    }

    /** Same external contract as the stock driver, with different internals. */
    private static FunctionalBlockDescriptor alternateLowSideDriver(
            String instanceKey) {
        return new FunctionalBlockDescriptor(
                "nmos-low-side-driver-alt", 1, instanceKey,
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
                                "Q2", "NMOS", Arrays.asList("GATE", "DRAIN", "SOURCE")),
                        new FunctionalBlockDescriptor.Component(
                                "RBIAS", "RESISTOR", Arrays.asList("A", "B"))),
                Arrays.asList("control", "load", "return"),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Endpoint(
                                "Q2.GATE", "Q2", "GATE"),
                        new FunctionalBlockDescriptor.Endpoint(
                                "Q2.DRAIN", "Q2", "DRAIN"),
                        new FunctionalBlockDescriptor.Endpoint(
                                "Q2.SOURCE", "Q2", "SOURCE"),
                        new FunctionalBlockDescriptor.Endpoint(
                                "RBIAS.A", "RBIAS", "A"),
                        new FunctionalBlockDescriptor.Endpoint(
                                "RBIAS.B", "RBIAS", "B")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Pad(
                                "Q2.GATE", "Q2.GATE", "control"),
                        new FunctionalBlockDescriptor.Pad(
                                "Q2.DRAIN", "Q2.DRAIN", "load"),
                        new FunctionalBlockDescriptor.Pad(
                                "Q2.SOURCE", "Q2.SOURCE", "return"),
                        new FunctionalBlockDescriptor.Pad(
                                "RBIAS.A", "RBIAS.A", "control"),
                        new FunctionalBlockDescriptor.Pad(
                                "RBIAS.B", "RBIAS.B", "return")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Role(
                                "control",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        localRef(EntityKind.NET, "control"),
                                        localRef(EntityKind.PAD, "Q2.GATE"),
                                        localRef(EntityKind.PAD, "RBIAS.A"))),
                        new FunctionalBlockDescriptor.Role(
                                "load",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        localRef(EntityKind.NET, "load"),
                                        localRef(EntityKind.PAD, "Q2.DRAIN"))),
                        new FunctionalBlockDescriptor.Role(
                                "return",
                                FunctionalBlockDescriptor.Requirement.REQUIRED,
                                Arrays.asList(
                                        localRef(EntityKind.NET, "return"),
                                        localRef(EntityKind.PAD, "Q2.SOURCE"),
                                        localRef(EntityKind.PAD, "RBIAS.B"))),
                        new FunctionalBlockDescriptor.Role(
                                "sense",
                                FunctionalBlockDescriptor.Requirement.OPTIONAL,
                                Collections.<FunctionalBlockDescriptor.LocalRef>emptyList())),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port(
                                "control", "control",
                                localRef(EntityKind.PAD, "Q2.GATE")),
                        new FunctionalBlockDescriptor.Port(
                                "load", "load",
                                localRef(EntityKind.PAD, "Q2.DRAIN")),
                        new FunctionalBlockDescriptor.Port(
                                "return", "return",
                                localRef(EntityKind.PAD, "Q2.SOURCE"))));
    }

    private static FunctionalBlockDescriptor.LocalRef localRef(EntityKind kind,
            String id) {
        return new FunctionalBlockDescriptor.LocalRef(kind, id);
    }

    private static RealizationManifest manifest(NamespaceFixture fixture,
            long seed) {
        return new RealizationManifest(RealizationManifest.VERSION,
                BoundedAssemblyRequest.descriptor(seed),
                new ArrayList<BlockRealizationIdentity>(fixture.realizations),
                pins(), choices(seed), netBindings(fixture), targets(fixture));
    }

    private static List<RealizationManifest.VersionPin> pins() {
        ArrayList<RealizationManifest.VersionPin> result =
                new ArrayList<RealizationManifest.VersionPin>();
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.GEOMETRY,
                new ChallengeDescriptor.VersionedId("pcb-geometry",
                        BoundedAssemblyRequest.GEOMETRY_VERSION)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.LAYOUT,
                new ChallengeDescriptor.VersionedId("pcb-layout",
                        SeededPcbLayoutGenerator.CURRENT_VERSION)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.ROUTING,
                new ChallengeDescriptor.VersionedId("bounded-assembler",
                        BoundedAssemblyRequest.GENERATOR_VERSION)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.VALUES,
                new ChallengeDescriptor.VersionedId(
                        "bounded-assembler",
                        BoundedAssemblyRequest.GENERATOR_VERSION)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.MODELS,
                new ChallengeDescriptor.VersionedId("bounded-assembler",
                        BoundedAssemblyRequest.GENERATOR_VERSION)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.PACKAGES,
                new ChallengeDescriptor.VersionedId("bounded-assembler",
                        BoundedAssemblyRequest.GENERATOR_VERSION)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.DIAGNOSTIC,
                new ChallengeDescriptor.VersionedId(
                        "generated-diagnostic-solvability", 1)));
        result.add(new RealizationManifest.VersionPin(
                RealizationManifest.VersionPin.Concern.NAMED_STREAMS,
                new ChallengeDescriptor.VersionedId("named-random-streams", 1)));
        return result;
    }

    private static List<RealizationManifest.Choice> choices(long seed) {
        ArrayList<RealizationManifest.Choice> result =
                new ArrayList<RealizationManifest.Choice>();
        result.add(RealizationManifest.Choice.ids("blocks",
                Arrays.asList("u2", "u1")));
        result.add(RealizationManifest.Choice.number("resistance", 330.0));
        result.add(RealizationManifest.Choice.token("topology", "series"));
        result.add(RealizationManifest.Choice.integer("root-seed", seed));
        return result;
    }

    private static List<RealizationManifest.NetBinding> netBindings(
            NamespaceFixture fixture) {
        String loadAlias = fixture.alias("u2", "load");
        String returnAlias = fixture.alias("u1", "return");
        return Arrays.asList(new RealizationManifest.NetBinding(loadAlias,
                        fixture.buses.getBusId("shared-load")),
                new RealizationManifest.NetBinding(returnAlias, returnAlias));
    }

    private static List<String> targets(NamespaceFixture fixture) {
        TreeSet<String> result = new TreeSet<String>();
        result.addAll(fixture.namespace.getSemanticNetIds());
        result.add(fixture.buses.getBusId("shared-load"));
        for (Map.Entry<String, FunctionalBlockDescriptor> entry
                : fixture.namespace.getBlocks().entrySet()) {
            String instance = entry.getKey();
            FunctionalBlockDescriptor block = entry.getValue();
            for (String component : block.getComponents().keySet()) {
                result.add(fixture.namespace.idFor(instance,
                        EntityKind.COMPONENT, component));
                for (String terminal : block.getComponents().get(component)
                        .getTerminalIds()) {
                    result.add(fixture.namespace.terminalIdFor(instance,
                            component, terminal));
                }
            }
            for (String pad : block.getPads().keySet())
                result.add(fixture.namespace.idFor(instance,
                        EntityKind.PAD, pad));
            for (String endpoint : block.getEndpoints().keySet())
                result.add(fixture.namespace.idFor(instance,
                        EntityKind.ENDPOINT, endpoint));
            for (String role : block.getRoles().keySet())
                result.add(fixture.namespace.idFor(instance,
                        EntityKind.ROLE, role));
            for (String port : block.getPorts().keySet())
                result.add(fixture.namespace.idFor(instance,
                        EntityKind.PORT, port));
        }
        return new ArrayList<String>(result);
    }

    private static RealizationManifest.Choice findChoice(
            Collection<RealizationManifest.Choice> choices, String key) {
        for (RealizationManifest.Choice choice : choices)
            if (key.equals(choice.getKey())) return choice;
        throw new AssertionError("choice missing: " + key);
    }

    private static NamespaceFixture namespaceFixture(boolean includeExtra,
            boolean reverse, boolean alternateProvider) {
        return namespaceFixture(includeExtra, reverse, alternateProvider, false);
    }

    private static NamespaceFixture namespaceFixture(boolean includeExtra,
            boolean reverse, boolean alternateProvider, boolean earlierAlias) {
        ArrayList<FunctionalBlockDescriptor> blocks =
                new ArrayList<FunctionalBlockDescriptor>();
        FunctionalBlockDescriptor u1 = alternateProvider
                ? alternateLowSideDriver("u1")
                : FunctionalBlockExamples.lowSideDriver("u1");
        FunctionalBlockDescriptor u2 = FunctionalBlockExamples.lowSideDriver("u2");
        FunctionalBlockDescriptor u3 = FunctionalBlockExamples.indicatorLoad("u3");
        blocks.add(u1);
        blocks.add(u2);
        if (includeExtra) blocks.add(u3);

        ChallengeDescriptor.VersionedId provider = new ChallengeDescriptor.VersionedId(
                alternateProvider ? "nmos-provider-alt" : "nmos-provider", 1);
        ArrayList<BlockRealizationIdentity> realizations =
                new ArrayList<BlockRealizationIdentity>();
        realizations.add(new BlockRealizationIdentity("u1",
                new ChallengeDescriptor.VersionedId("driver", 1), provider,
                new ChallengeDescriptor.VersionedId(
                        alternateProvider ? "nmos-low-side-driver-alt"
                                : "nmos-low-side-driver", 1)));
        realizations.add(new BlockRealizationIdentity("u2",
                new ChallengeDescriptor.VersionedId("driver", 1),
                new ChallengeDescriptor.VersionedId("nmos-provider", 1),
                new ChallengeDescriptor.VersionedId("nmos-low-side-driver", 1)));
        if (includeExtra) {
            realizations.add(new BlockRealizationIdentity("u3",
                    new ChallengeDescriptor.VersionedId("indicator", 1),
                    new ChallengeDescriptor.VersionedId("led-provider", 1),
                    new ChallengeDescriptor.VersionedId("resistor-led-indicator", 1)));
        }
        if (reverse) {
            Collections.reverse(blocks);
            Collections.reverse(realizations);
        }
        BlockNamespace namespace = new BlockNamespace("a03-device", 7, blocks,
                realizations);
        TreeMap<String, String> aliases = new TreeMap<String, String>();
        for (String alias : namespace.getSemanticNetIds())
            aliases.put(alias, alias);
        DeviceBusBindings buses = null;
        if (includeExtra) {
            String u1Load = namespace.idFor("u1", EntityKind.NET, "load");
            String u2Load = namespace.idFor("u2", EntityKind.NET, "load");
            String u3Supply = namespace.idFor("u3", EntityKind.NET, "supply");
            if (earlierAlias) {
                // Deliberately change the temporary electrical representative
                // to the newly introduced lexical-first alias. The explicit
                // device-bus owner remains the semantic key below.
                aliases.put(u1Load, u1Load);
                aliases.put(u2Load, u1Load);
                aliases.put(u3Supply, u1Load);
            } else {
                aliases.put(u3Supply, u2Load);
            }
            List<String> anchors = earlierAlias
                    ? Arrays.asList(u1Load, u2Load, u3Supply)
                    : Arrays.asList(u2Load, u3Supply);
            buses = new DeviceBusBindings(namespace,
                    Arrays.asList(new DeviceBusBindings.Declaration("shared-load",
                            anchors, Arrays.asList("port:LOAD"))), aliases);
        }
        return new NamespaceFixture(namespace, blocks, realizations, aliases, buses);
    }

    private static String removeFirstRecord(String value, String key) {
        String[] lines = value.split("\\n", -1);
        StringBuilder result = new StringBuilder();
        boolean removed = false;
        for (String line : lines) {
            if (!removed && line.startsWith(key + "=")) {
                removed = true;
                continue;
            }
            if (result.length() != 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }

    private static String appendRecord(String value, String key, String payload) {
        return value + "\n" + key + "=" + payload.length() + ":" + payload;
    }

    private interface Action {
        void run();
    }

    private static void expectFailure(Action action, String label) {
        try {
            action.run();
            throw new AssertionError("accepted invalid A03 contract: " + label);
        } catch (AssertionError failure) {
            throw failure;
        } catch (RuntimeException expected) {
            assertions++;
        }
    }

    private static String expectChallengeFailure(Action action,
            ChallengeContractException.Code code, String field, String label) {
        try {
            action.run();
            throw new AssertionError("accepted invalid A03 contract: " + label);
        } catch (ChallengeContractException expected) {
            assertions++;
            if (expected.getCode() != code || !field.equals(expected.getFieldId())) {
                throw new AssertionError(label + " wrong failure: "
                        + expected.getCode() + ":" + expected.getFieldId());
            }
            return expected.getCode().name() + ":" + expected.getFieldId();
        }
    }

    private static void expectBlockFailure(Action action,
            BlockContractException.Code code, String field, String label) {
        try {
            action.run();
            throw new AssertionError("accepted invalid A03 contract: " + label);
        } catch (BlockContractException expected) {
            assertions++;
            if (expected.getCode() != code || !field.equals(expected.getFieldId())) {
                throw new AssertionError(label + " wrong failure: "
                        + expected.getCode() + ":" + expected.getFieldId());
            }
        }
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static final class NamespaceFixture {
        final BlockNamespace namespace;
        final List<FunctionalBlockDescriptor> blocks;
        final List<BlockRealizationIdentity> realizations;
        final Map<String, String> aliases;
        final DeviceBusBindings buses;

        NamespaceFixture(BlockNamespace namespace,
                List<FunctionalBlockDescriptor> blocks,
                List<BlockRealizationIdentity> realizations,
                Map<String, String> aliases, DeviceBusBindings buses) {
            this.namespace = namespace;
            this.blocks = Collections.unmodifiableList(
                    new ArrayList<FunctionalBlockDescriptor>(blocks));
            this.realizations = Collections.unmodifiableList(
                    new ArrayList<BlockRealizationIdentity>(realizations));
            this.aliases = Collections.unmodifiableMap(
                    new TreeMap<String, String>(aliases));
            this.buses = buses;
        }

        String alias(String instance, String localNet) {
            return namespace.idFor(instance, EntityKind.NET, localNet);
        }
    }
}
