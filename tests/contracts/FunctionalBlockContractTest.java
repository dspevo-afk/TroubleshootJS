package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.lushprojects.circuitjs1.client.BlockContractException.Code;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Component;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Endpoint;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Pad;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Parameter;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Port;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Requirement;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Role;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Value;

/** Literal identity and failure oracles, independent of the production encoder. */
public final class FunctionalBlockContractTest {
    private static int assertions;

    public static void main(String[] args) {
        examples();
        namespaceLiterals();
        orderingAndOptionalBlocks();
        malformedIdsAndVersions();
        duplicateDeclarations();
        relationshipFailures();
        roleAndPortFailures();
        immutability();
        parameterValues();
        System.out.println("PASS: Task44 9 groups, " + assertions + " assertions; no simulator dependency");
    }

    private static void examples() {
        FunctionalBlockDescriptor driver = FunctionalBlockExamples.lowSideDriver("driver");
        FunctionalBlockDescriptor load = FunctionalBlockExamples.indicatorLoad("lamp");
        equal("driver", driver.getInstanceKey());
        equal("lamp", load.getInstanceKey());
        check(driver.getComponents().containsKey("Q1"), "driver declares Q1");
        check(driver.getComponents().containsKey("RPD"), "driver declares pull-down");
        check(load.getComponents().containsKey("R1"), "load declares resistor");
        check(load.getComponents().containsKey("LED1"), "load declares LED");
        for (FunctionalBlockDescriptor block : list(driver, load)) {
            check(!block.getEndpoints().isEmpty() && !block.getPads().isEmpty(), "local relationships present");
            check(!block.getRoles().isEmpty() && !block.getPorts().isEmpty(), "roles and ports present");
            for (Port port : block.getPorts().values()) {
                check(block.getRoles().get(port.getRoleId()).getMembers().contains(port.getAttachment()),
                    "port attachment is a role contribution");
            }
        }
        BlockNamespace namespace = new BlockNamespace("controlled-indicator", 1, list(driver, load));
        equal("tsj-preflight-v1/controlled-indicator@1/driver/component/Q1",
            namespace.idFor("driver", EntityKind.COMPONENT, "Q1"));
        equal("tsj-preflight-v1/controlled-indicator@1/driver/pad/Q1.G",
            namespace.idFor("driver", EntityKind.PAD, "Q1.G"));
        equal("tsj-preflight-v1/controlled-indicator@1/driver/endpoint/Q1.G",
            namespace.idFor("driver", EntityKind.ENDPOINT, "Q1.G"));
    }

    private static void namespaceLiterals() {
        FunctionalBlockDescriptor same = new FunctionalBlockDescriptor("same-type", 7, "one",
            Collections.<Parameter>emptyList(), list(new Component("same", "passive", list("1"))),
            list("same"), list(new Endpoint("same", "same", "1")),
            list(new Pad("same", "same", "same")),
            list(new Role("same", Requirement.REQUIRED, list(ref(EntityKind.NET, "same")))),
            list(new Port("same", "same", ref(EntityKind.NET, "same"))));
        BlockNamespace n = new BlockNamespace("device", 1, list(same));
        equal("tsj-preflight-v1/device@1/one/component/same", n.idFor("one", EntityKind.COMPONENT, "same"));
        equal("tsj-preflight-v1/device@1/one/pad/same", n.idFor("one", EntityKind.PAD, "same"));
        equal("tsj-preflight-v1/device@1/one/net/same", n.idFor("one", EntityKind.NET, "same"));
        equal("tsj-preflight-v1/device@1/one/endpoint/same", n.idFor("one", EntityKind.ENDPOINT, "same"));
        equal("tsj-preflight-v1/device@1/one/port/same", n.idFor("one", EntityKind.PORT, "same"));
        equal("tsj-preflight-v1/device@1/one/role/same", n.idFor("one", EntityKind.ROLE, "same"));
        equal("tsj-preflight-v1/device@2/one/net/same",
            new BlockNamespace("device", 2, list(same)).idFor("one", EntityKind.NET, "same"));
        equal("tsj-preflight-v1/device-two@1/one/net/same",
            new BlockNamespace("device-two", 1, list(same)).idFor("one", EntityKind.NET, "same"));

        Fixture dottedA = new Fixture("a.b");
        dottedA.nets.add("c");
        Fixture dottedB = new Fixture("a");
        dottedB.nets.add("b.c");
        BlockNamespace dotted = new BlockNamespace("d", 1, list(dottedA.build(), dottedB.build()));
        equal("tsj-preflight-v1/d@1/a.b/net/c", dotted.idFor("a.b", EntityKind.NET, "c"));
        equal("tsj-preflight-v1/d@1/a/net/b.c", dotted.idFor("a", EntityKind.NET, "b.c"));
        check(!dotted.idFor("a.b", EntityKind.NET, "c").equals(dotted.idFor("a", EntityKind.NET, "b.c")),
            "accepted delimiter-like local IDs cannot cross segment boundaries");
    }

    private static void orderingAndOptionalBlocks() {
        Fixture f = new Fixture("driver");
        FunctionalBlockDescriptor forward = f.build();
        f.reverse();
        FunctionalBlockDescriptor reverse = f.build();
        equal(new ArrayList<String>(forward.getComponents().keySet()), new ArrayList<String>(reverse.getComponents().keySet()));
        equal(forward.getNetIds(), reverse.getNetIds());
        equal(new ArrayList<String>(forward.getEndpoints().keySet()), new ArrayList<String>(reverse.getEndpoints().keySet()));
        equal(new ArrayList<String>(forward.getPorts().keySet()), new ArrayList<String>(reverse.getPorts().keySet()));
        FunctionalBlockDescriptor second = new Fixture("second").build();
        FunctionalBlockDescriptor optional = new Fixture("optional").build();
        List<List<FunctionalBlockDescriptor>> arrangements = list(list(forward, second),
            list(second, reverse), list(optional, second, reverse), list(forward, optional, second),
            list(second, forward, optional), list(forward));
        for (List<FunctionalBlockDescriptor> blocks : arrangements) {
            BlockNamespace n = new BlockNamespace("device", 3, blocks);
            equal("tsj-preflight-v1/device@3/driver/pad/X.1", n.idFor("driver", EntityKind.PAD, "X.1"));
            equal("tsj-preflight-v1/device@3/driver/net/rail", n.idFor("driver", EntityKind.NET, "rail"));
            if (n.getBlocks().containsKey("second")) {
                equal("tsj-preflight-v1/device@3/second/pad/X.1", n.idFor("second", EntityKind.PAD, "X.1"));
            }
        }
        final BlockNamespace n = new BlockNamespace("device", 1, list(forward));
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() { n.idFor("absent", EntityKind.PAD, "X.1"); }});
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() { n.idFor("driver", EntityKind.PAD, "absent"); }});
    }

    private static void malformedIdsAndVersions() {
        String[] malformed = { null, "", ".", "..", " a", "a ", "a/b", "a@1", "a:b", "a|b", "a\\b", "a%2Fb",
            "a\n", "a\t", "\u00e9", "a\u0301", "\ud83d\udd0c", repeat('x', 129) };
        for (final String bad : malformed) {
            expect(Code.INVALID_ID, new Runnable() { public void run() { FunctionalBlockDescriptor.requireId(bad, "test.id"); }});
        }
        equal(repeat('x', 128), FunctionalBlockDescriptor.requireId(repeat('x', 128), "test.id"));
        equal("0_a-b.c", FunctionalBlockDescriptor.requireId("0_a-b.c", "test.id"));
        final Fixture f = new Fixture("ok");
        expect(Code.INVALID_ID, new Runnable() { public void run() { f.instance = "bad/key"; f.build(); }});
        f.instance = "ok";
        expect(Code.INVALID_ID, new Runnable() { public void run() { f.type = "bad@type"; f.build(); }});
        f.type = "fixture";
        expect(Code.INVALID_VERSION, new Runnable() { public void run() { f.version = 0; f.build(); }});
        f.version = 1;
        expect(Code.INVALID_VERSION, new Runnable() { public void run() { new BlockNamespace("device", -1, list(f.build())); }});
        expect(Code.INVALID_ID, new Runnable() { public void run() { new BlockNamespace("device@1", 1, list(f.build())); }});
        expect(Code.MISSING_DECLARATION, new Runnable() { public void run() { new BlockNamespace("device", 1, null); }});
        expect(Code.MISSING_DECLARATION, new Runnable() { public void run() { new BlockNamespace("device", 1, list((FunctionalBlockDescriptor) null)); }});
    }

    private static void duplicateDeclarations() {
        final Fixture f = new Fixture("block");
        final FunctionalBlockDescriptor d = f.build();
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { new BlockNamespace("device", 1, list(d, d)); }});
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.parameters.add(f.parameters.get(0)); f.build(); }});
        f.parameters.remove(f.parameters.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.components.add(f.components.get(0)); f.build(); }});
        f.components.remove(f.components.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.nets.add("rail"); f.build(); }});
        f.nets.remove(f.nets.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.endpoints.add(f.endpoints.get(0)); f.build(); }});
        f.endpoints.remove(f.endpoints.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.pads.add(f.pads.get(0)); f.build(); }});
        f.pads.remove(f.pads.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.roles.add(f.roles.get(0)); f.build(); }});
        f.roles.remove(f.roles.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { f.ports.add(f.ports.get(0)); f.build(); }});
        f.ports.remove(f.ports.size() - 1);
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() { new Component("X", "passive", list("1", "1")); }});
        expect(Code.DUPLICATE_TERMINAL_ENDPOINT, new Runnable() { public void run() {
            f.endpoints.add(new Endpoint("extra", "X", "1")); f.build();
        }});
        f.endpoints.remove(f.endpoints.size() - 1);
        expect(Code.INVALID_ATTACHMENT, new Runnable() { public void run() {
            f.pads.add(new Pad("extra", "X.1", "return")); f.build();
        }});
    }

    private static void relationshipFailures() {
        final Fixture f = new Fixture("block");
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() { f.components.clear(); f.build(); }});
        f.components.add(new Component("X", "passive", list("1", "2")));
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() { f.nets.remove("rail"); f.build(); }});
        f.nets.add("rail");
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() { f.endpoints.set(0, new Endpoint("X.1", "X", "unknown")); f.build(); }});
        f.endpoints.set(0, new Endpoint("X.1", "X", "1"));
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() { f.pads.set(0, new Pad("X.1", "unknown", "rail")); f.build(); }});
        f.pads.set(0, new Pad("X.1", "X.1", "rail"));
        expect(Code.MISSING_DECLARATION, new Runnable() { public void run() {
            f.components.set(0, new Component("X", "passive", list("1", "2", "3"))); f.build();
        }});
        f.components.set(0, new Component("X", "passive", list("1", "2")));
        expect(Code.MISSING_DECLARATION, new Runnable() { public void run() { f.pads.add(null); f.build(); }});
        f.pads.remove(f.pads.size() - 1);
        expect(Code.MISSING_DECLARATION, new Runnable() { public void run() {
            new FunctionalBlockDescriptor("fixture", 1, "block", null, f.components, f.nets, f.endpoints, f.pads, f.roles, f.ports);
        }});

        // Component/terminal pairs must be structural. These two legal pairs
        // have the same naive dot-concatenated spelling, but are different.
        FunctionalBlockDescriptor pairs = new FunctionalBlockDescriptor("pairs", 1, "pairs",
            Collections.<Parameter>emptyList(),
            list(new Component("a.b", "passive", list("c")), new Component("a", "passive", list("b.c"))),
            Collections.<String>emptyList(), list(new Endpoint("e1", "a.b", "c"), new Endpoint("e2", "a", "b.c")),
            Collections.<Pad>emptyList(), Collections.<Role>emptyList(), Collections.<Port>emptyList());
        equal(2, pairs.getEndpoints().size());
    }

    private static void roleAndPortFailures() {
        final Fixture f = new Fixture("block");
        expect(Code.CONTRADICTORY_ROLE, new Runnable() { public void run() {
            f.roles.add(new Role("supply", Requirement.OPTIONAL, Collections.<LocalRef>emptyList())); f.build();
        }});
        Collections.reverse(f.roles);
        expect(Code.CONTRADICTORY_ROLE, new Runnable() { public void run() { f.build(); }});
        Collections.reverse(f.roles);
        f.roles.remove(f.roles.size() - 1);
        expect(Code.INVALID_ROLE, new Runnable() { public void run() {
            f.roles.add(new Role("empty", Requirement.REQUIRED, Collections.<LocalRef>emptyList())); f.build();
        }});
        f.roles.remove(f.roles.size() - 1);
        expect(Code.INVALID_ROLE, new Runnable() { public void run() { new Role("null", null, list(ref(EntityKind.NET, "rail"))); }});
        expect(Code.DUPLICATE_DECLARATION, new Runnable() { public void run() {
            new Role("repeated", Requirement.OPTIONAL, list(ref(EntityKind.NET, "rail"), ref(EntityKind.NET, "rail")));
        }});
        expect(Code.INVALID_ROLE, new Runnable() { public void run() {
            f.roles.add(new Role("cycle", Requirement.OPTIONAL, list(ref(EntityKind.ROLE, "cycle")))); f.build();
        }});
        if (f.roles.size() > 3) f.roles.remove(f.roles.size() - 1);
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() {
            f.roles.add(new Role("dangling", Requirement.OPTIONAL, list(ref(EntityKind.NET, "gone")))); f.build();
        }});
        f.roles.remove(f.roles.size() - 1);
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() {
            f.ports.set(0, new Port("in", "missing", ref(EntityKind.NET, "rail"))); f.build();
        }});
        f.ports.set(0, new Port("in", "supply", ref(EntityKind.NET, "rail")));
        expect(Code.INVALID_ATTACHMENT, new Runnable() { public void run() {
            f.ports.set(0, new Port("in", "supply", ref(EntityKind.COMPONENT, "X"))); f.build();
        }});
        f.ports.set(0, new Port("in", "supply", ref(EntityKind.NET, "rail")));
        expect(Code.INVALID_ATTACHMENT, new Runnable() { public void run() {
            f.ports.set(0, new Port("in", "supply", ref(EntityKind.NET, "return"))); f.build();
        }});
        f.ports.set(0, new Port("in", "supply", ref(EntityKind.NET, "rail")));
        expect(Code.DANGLING_REFERENCE, new Runnable() { public void run() {
            f.ports.set(0, new Port("in", "supply", ref(EntityKind.NET, "gone"))); f.build();
        }});
        // All three legal local attachment kinds are usable without a solver.
        f.roles.set(0, new Role("supply", Requirement.REQUIRED,
            list(ref(EntityKind.NET, "rail"), ref(EntityKind.PAD, "X.1"), ref(EntityKind.ENDPOINT, "X.1"))));
        f.ports.set(0, new Port("in", "supply", ref(EntityKind.PAD, "X.1")));
        equal(EntityKind.PAD, f.build().getPorts().get("in").getAttachment().getKind());
        f.ports.set(0, new Port("in", "supply", ref(EntityKind.ENDPOINT, "X.1")));
        equal(EntityKind.ENDPOINT, f.build().getPorts().get("in").getAttachment().getKind());
    }

    private static void immutability() {
        final Fixture f = new Fixture("block");
        List<String> terminals = list("1", "2");
        f.components.set(0, new Component("X", "passive", terminals));
        List<LocalRef> members = list(ref(EntityKind.NET, "rail"), ref(EntityKind.PAD, "X.1"));
        f.roles.set(0, new Role("supply", Requirement.REQUIRED, members));
        final FunctionalBlockDescriptor d = f.build();
        terminals.clear();
        members.clear();
        f.parameters.clear(); f.components.clear(); f.nets.clear(); f.endpoints.clear();
        f.pads.clear(); f.roles.clear(); f.ports.clear();
        equal(1, d.getParameters().size()); equal(1, d.getComponents().size()); equal(2, d.getNetIds().size());
        equal(2, d.getEndpoints().size()); equal(2, d.getPads().size()); equal(3, d.getRoles().size()); equal(2, d.getPorts().size());
        equal(list("1", "2"), d.getComponents().get("X").getTerminalIds());
        equal(2, d.getRoles().get("supply").getMembers().size());
        unmodifiable(new Runnable() { public void run() { d.getParameters().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getComponents().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getNetIds().add("evil"); }});
        unmodifiable(new Runnable() { public void run() { d.getEndpoints().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getPads().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getRoles().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getPorts().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getComponents().get("X").getTerminalIds().clear(); }});
        unmodifiable(new Runnable() { public void run() { d.getRoles().get("supply").getMembers().clear(); }});
        unmodifiable(new Runnable() { public void run() {
            Map.Entry<String, Parameter> entry = d.getParameters().entrySet().iterator().next();
            entry.setValue(new Parameter("bad", Value.ofInteger(1)));
        }});
        List<FunctionalBlockDescriptor> blocks = list(d);
        final BlockNamespace n = new BlockNamespace("device", 1, blocks);
        blocks.clear();
        unmodifiable(new Runnable() { public void run() { n.getBlocks().clear(); }});
        for (int i = 0; i < 20; i++) {
            equal("tsj-preflight-v1/device@1/block/pad/X.1", n.idFor("block", EntityKind.PAD, "X.1"));
            equal(2, d.getPads().size());
        }
    }

    private static void parameterValues() {
        equal(true, Value.ofBoolean(true).getBoolean());
        equal(Integer.MIN_VALUE, Value.ofInteger(Integer.MIN_VALUE).getInteger());
        equal(Integer.MAX_VALUE, Value.ofInteger(Integer.MAX_VALUE).getInteger());
        equal(2.75, Value.ofDecimal(2.75).getDecimal());
        equal(0L, Double.doubleToLongBits(Value.ofDecimal(-0.0).getDecimal()));
        equal("text \u00e9", Value.ofText("text \u00e9").getText());
        equal("", Value.ofText("").getText());
        equal(Value.Kind.BOOLEAN, Value.ofBoolean(false).getKind());
        equal(Value.Kind.INTEGER, Value.ofInteger(1).getKind());
        equal(Value.Kind.DECIMAL, Value.ofDecimal(1).getKind());
        equal(Value.Kind.TEXT, Value.ofText("x").getKind());
        expect(Code.INVALID_PARAMETER, new Runnable() { public void run() { Value.ofDecimal(Double.NaN); }});
        expect(Code.INVALID_PARAMETER, new Runnable() { public void run() { Value.ofDecimal(Double.POSITIVE_INFINITY); }});
        expect(Code.INVALID_PARAMETER, new Runnable() { public void run() { Value.ofDecimal(Double.NEGATIVE_INFINITY); }});
        expect(Code.INVALID_PARAMETER, new Runnable() { public void run() { Value.ofText(null); }});
        try {
            Value.ofBoolean(true).getDecimal();
            throw new AssertionError("wrong typed accessor accepted");
        } catch (IllegalStateException expected) { assertions++; }
    }

    private static final class Fixture {
        String type = "fixture";
        int version = 1;
        String instance;
        final List<Parameter> parameters = list(new Parameter("resistance", Value.ofInteger(1000)));
        final List<Component> components = list(new Component("X", "passive", list("1", "2")));
        final List<String> nets = list("rail", "return");
        final List<Endpoint> endpoints = list(new Endpoint("X.1", "X", "1"), new Endpoint("X.2", "X", "2"));
        final List<Pad> pads = list(new Pad("X.1", "X.1", "rail"), new Pad("X.2", "X.2", "return"));
        final List<Role> roles = list(new Role("supply", Requirement.REQUIRED,
            list(ref(EntityKind.NET, "rail"), ref(EntityKind.PAD, "X.1"))),
            new Role("return", Requirement.REQUIRED, list(ref(EntityKind.NET, "return"))),
            new Role("spare", Requirement.OPTIONAL, Collections.<LocalRef>emptyList()));
        final List<Port> ports = list(new Port("in", "supply", ref(EntityKind.NET, "rail")),
            new Port("out", "return", ref(EntityKind.NET, "return")));
        Fixture(String key) { instance = key; }
        FunctionalBlockDescriptor build() {
            return new FunctionalBlockDescriptor(type, version, instance, parameters, components, nets, endpoints, pads, roles, ports);
        }
        void reverse() {
            Collections.reverse(parameters); Collections.reverse(components); Collections.reverse(nets);
            Collections.reverse(endpoints); Collections.reverse(pads); Collections.reverse(roles); Collections.reverse(ports);
        }
    }

    private static LocalRef ref(EntityKind kind, String id) { return new LocalRef(kind, id); }
    @SafeVarargs private static <T> List<T> list(T... values) { return new ArrayList<T>(Arrays.asList(values)); }
    private static String repeat(char c, int n) { char[] chars = new char[n]; Arrays.fill(chars, c); return new String(chars); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(expected.equals(actual), "expected " + expected + ", got " + actual); }
    private static void expect(Code code, Runnable action) {
        assertions++;
        try { action.run(); } catch (BlockContractException ex) {
            if (ex.getCode() != code) throw new AssertionError("expected " + code + ", got " + ex.getCode(), ex);
            check(ex.getFieldId() != null && ex.getFieldId().length() > 0, "diagnostic identifies field");
            return;
        }
        throw new AssertionError("expected contract failure " + code);
    }
    private static void unmodifiable(Runnable action) {
        assertions++;
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("mutable collection escaped descriptor");
    }
}
