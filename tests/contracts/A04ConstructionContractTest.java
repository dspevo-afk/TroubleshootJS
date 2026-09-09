package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Pure/native conformance for the A04 construction boundary.
 *
 * <p>This class deliberately derives its terminal expectations from the
 * electrical declarations (the two resistive port declarations and the
 * controlled driver/load descriptors).  It does not ask the construction
 * context to tell the test what it was supposed to build.</p>
 */
public final class A04ConstructionContractTest {
    private static int assertions;

    private A04ConstructionContractTest() { }

    public static void main(String[] args) {
        try {
            immutableDeclarationCanaries();
            supportedProviderRegistry();
            planSpecAndElectricalCorrespondence();
            packageMapCompletenessAndSharedPackageShape();
            a03ManifestReplayPreservation();
            System.out.println("PASS: A04ConstructionContractTest assertions="
                    + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.println("FAIL: A04ConstructionContractTest after "
                    + assertions + " assertions: " + failure.getMessage());
            System.exit(1);
        }
    }

    /** The small records used by the spec must own every caller collection. */
    private static void immutableDeclarationCanaries() {
        final List<String> elements = new ArrayList<String>(
                Arrays.asList("z", "a"));
        final List<String> units = new ArrayList<String>(
                Arrays.asList("unit-b", "unit-a"));
        final List<String> terminals = new ArrayList<String>(
                Arrays.asList("2", "1"));
        final List<String> joins = new ArrayList<String>(
                Arrays.asList("join-b", "join-a"));
        final ElectricalRealizationSpec.ProviderDeclaration declaration =
                new ElectricalRealizationSpec.ProviderDeclaration("owner",
                        "provider", 1, null, elements, units, terminals,
                        joins, false);
        elements.clear();
        units.clear();
        terminals.clear();
        joins.clear();
        check(declaration.getElementIds().equals(Arrays.asList("a", "z")),
                "provider declaration did not defensively copy element IDs");
        check(declaration.getUnitIds().equals(Arrays.asList("unit-a", "unit-b")),
                "provider declaration did not defensively copy unit IDs");
        check(declaration.getTerminalIds().equals(Arrays.asList("1", "2")),
                "provider declaration did not defensively copy terminal IDs");
        check(declaration.getJoinIds().equals(Arrays.asList("join-a", "join-b")),
                "provider declaration did not defensively copy join IDs");
        expectUnsupported(new Runnable() {
            @Override public void run() {
                declaration.getElementIds().clear();
            }
        }, "provider element declaration view");

        final Map<String, Integer> posts = new HashMap<String, Integer>();
        posts.put("D", Integer.valueOf(2));
        posts.put("S", Integer.valueOf(1));
        posts.put("G", Integer.valueOf(0));
        final ElectricalRealizationSpec.ElementDeclaration element =
                new ElectricalRealizationSpec.ElementDeclaration("owner", "Q1",
                        "NMOS", "component/Q1", posts);
        posts.clear();
        check(element.getPostIndex("G") == 0 && element.getPostIndex("S") == 1
                && element.getPostIndex("D") == 2,
                "element declaration did not retain the independent NMOS post map");
        expectUnsupported(new Runnable() {
            @Override public void run() {
                element.getPostIndexByTerminal().put("X", Integer.valueOf(3));
            }
        }, "element post declaration view");

        final Map<String, String> packageTerminals = new HashMap<String, String>();
        packageTerminals.put("VCC", "VCC");
        packageTerminals.put("GND", "GND");
        final ElectricalRealizationSpec.PhysicalUnitSpec unit =
                new ElectricalRealizationSpec.PhysicalUnitSpec("owner", "unit",
                        "component/U1", "package/U1", packageTerminals);
        packageTerminals.clear();
        check(unit.getPackageTerminalByUnitTerminal().size() == 2,
                "physical unit did not defensively copy its terminal map");
        expectUnsupported(new Runnable() {
            @Override public void run() {
                unit.getPackageTerminalByUnitTerminal().put("X", "X");
            }
        }, "physical unit terminal mapping view");

        final List<String> joinRefs = new ArrayList<String>(Arrays.asList(
                "load/LED1.A", "driver/Q1.D"));
        final ElectricalRealizationSpec.DeviceJoinSpec join =
                new ElectricalRealizationSpec.DeviceJoinSpec("led-drain",
                        joinRefs, "SIGNAL");
        joinRefs.clear();
        check(join.getTerminalRefs().equals(Arrays.asList("driver/Q1.D",
                "load/LED1.A")), "device join did not canonicalize references");
        expectUnsupported(new Runnable() {
            @Override public void run() {
                join.getTerminalRefs().clear();
            }
        }, "device join terminal view");

        expectIllegal(new Runnable() {
            @Override public void run() {
                new ElectricalRealizationSpec.ProviderDeclaration("owner",
                        "provider", 0, null, Arrays.asList("x"),
                        Arrays.asList("u"), Arrays.asList("t"),
                        Collections.<String>emptyList(), false);
            }
        }, "provider version zero");
    }

    /** Registry lookup is a closed versioned boundary, not a permissive map. */
    private static void supportedProviderRegistry() {
        ElectricalConstructionProvider source =
                StandardElectricalConstructionProviders.resistiveSource();
        ElectricalConstructionProvider load =
                StandardElectricalConstructionProviders.resistiveLoad();
        ElectricalConstructionProvider driver =
                StandardElectricalConstructionProviders.controlledDriver();
        ElectricalConstructionProvider controlledLoad =
                StandardElectricalConstructionProviders.controlledLoad();
        check(source != null && load != null && driver != null
                && controlledLoad != null, "standard providers are incomplete");
        check("resistive-source".equals(source.getProviderId())
                && source.getVersion() == 1, "resistive source provider identity");
        check("resistive-load".equals(load.getProviderId())
                && load.getVersion() == 1, "resistive load provider identity");
        check("nmos-low-side-driver".equals(driver.getProviderId())
                && driver.getVersion() == 1, "controlled driver provider identity");
        check(ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(
                controlledLoad.getProviderId())
                && controlledLoad.getVersion() == ControlledIndicatorBlockContributions.LOAD_VERSION,
                "controlled load provider identity");
        check(StandardElectricalConstructionProviders.provider(
                source.getProviderId(), source.getVersion()) == source,
                "source registry lookup did not return the registered provider");
        check(StandardElectricalConstructionProviders.provider(
                load.getProviderId(), load.getVersion()) == load,
                "load registry lookup did not return the registered provider");
        check(StandardElectricalConstructionProviders.provider(
                driver.getProviderId(), driver.getVersion()) == driver,
                "driver registry lookup did not return the registered provider");
        check(StandardElectricalConstructionProviders.provider(
                controlledLoad.getProviderId(), controlledLoad.getVersion())
                == controlledLoad,
                "controlled-load registry lookup did not return the provider");
        expectIllegal(new Runnable() {
            @Override public void run() {
                StandardElectricalConstructionProviders.provider(
                        "unknown-provider", 1);
            }
        }, "unknown provider lookup");
        expectIllegal(new Runnable() {
            @Override public void run() {
                StandardElectricalConstructionProviders.provider(
                        "resistive-source", 99);
            }
        }, "unsupported provider version lookup");
    }

    /** Check the current resistive and controlled plans against independent electrical facts. */
    private static void planSpecAndElectricalCorrespondence() {
        for (int version : new int[] { 1, BoundedAssemblyRequest.GENERATOR_VERSION }) {
            BoundedAssemblyPlan plan = planFor(version, version);
            ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
            check(spec != null && spec.getVersion() == ElectricalRealizationSpec.VERSION,
                    "missing versioned electrical realization spec for v" + version);
            check(!spec.getProviderDeclarations().isEmpty()
                    && !spec.getElementDeclarations().isEmpty()
                    && !spec.getPhysicalUnits().isEmpty()
                    && !spec.getTerminalMappings().isEmpty(),
                    "electrical spec omitted required declaration maps for v" + version);
            immutableMap(spec.getProviderDeclarations(), "provider declarations");
            immutableMap(spec.getElementDeclarations(), "element declarations");
            immutableMap(spec.getPhysicalUnits(), "physical units");
            immutableMap(spec.getTerminalMappings(), "terminal mappings");
            immutableMap(spec.getDeviceJoins(), "device joins");
            immutableMap(spec.getSolverReservations(), "solver reservations");

            if (version == 1)
                assertResistiveDeclarations(plan, spec);
            else
                assertControlledDeclarations(plan, spec, version);

            int expectedPackages = version == 1 ? 3 : 7;
            int expectedUnits = version == 1 ? 3 : 7;
            check(spec.getPackageMap().getPackageCount() == expectedPackages,
                    "unexpected visible package count for v" + version);
            check(spec.getPackageMap().getUnitCount() == expectedUnits,
                    "unexpected physical-unit count for v" + version);
            mapCompleteness(spec, "v" + version);
            reservationAndSupplyCorrespondence(spec, version);

            if (version != 1) {
                ControlledIndicatorValueSynthesis.ResolvedRecipe planRecipe =
                        plan.getResolvedLoadRecipe();
                check(planRecipe != null && spec.getResolvedLoadRecipe() == planRecipe,
                        "current controlled recipe was not passed through by object identity");
                check(plan.getLoad().getResolvedValueRecipe() == planRecipe,
                        "current controlled load contribution does not retain the plan recipe identity");
            } else {
                check(spec.getResolvedLoadRecipe() == null,
                        "resistive spec unexpectedly synthesized a resolved recipe");
            }
        }
    }

    private static void assertResistiveDeclarations(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec) {
        check(spec.getBridgeSpecs().get("SOURCE_FIRST_ATTACHMENT").getSemanticJoinId() == null &&
                "VIN_INPUT".equals(spec.getBridgeSpecs().get("SOURCE_FIRST_ATTACHMENT").getExternalPowerInputId()),
                "source positive supply attachment was mislabeled as a common-return join");
        provider(spec, "source", "resistive-source", 1);
        provider(spec, "load", "resistive-load", 1);
        element(spec, "source", "R1", "RESISTOR", "1", 0, "2", 1);
        element(spec, "load", "R1", "RESISTOR", "1", 0, "2", 1);
        terminal(spec, plan, "source", "R1", "1", "1",
                plan.netFor("source", "SUPPLY"));
        terminal(spec, plan, "source", "R1", "2", "2",
                plan.netFor("source", "OUT"));
        terminal(spec, plan, "load", "R1", "1", "1",
                plan.netFor("load", "SUPPLY"));
        terminal(spec, plan, "load", "R1", "2", "2",
                plan.netFor("load", "RETURN"));
        check(plan.netFor("source", "OUT").equals(
                plan.netFor("load", "SUPPLY")),
                "resistive signal join is not the explicit source-to-load path");
        check(plan.netFor("source", "RETURN").equals(
                plan.netFor("load", "RETURN")),
                "resistive return join is not the explicit common return");
    }

    private static void assertControlledDeclarations(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, int version) {
        provider(spec, "driver", "nmos-low-side-driver", 1);
        provider(spec, "load", "resistor-led-load",
                ControlledIndicatorBlockContributions.LOAD_VERSION);
        element(spec, "driver", "RG", "RESISTOR", "1", 0, "2", 1);
        element(spec, "driver", "RPD", "RESISTOR", "1", 0, "2", 1);
        element(spec, "driver", "Q1", "NMOS", "G", 0, "S", 1, "D", 2);
        element(spec, "load", "RLOAD", "RESISTOR", "1", 0, "2", 1);
        element(spec, "load", "LED1", "LED", "A", 0, "K", 1);

        terminal(spec, plan, "driver", "RG", "1", "1",
                plan.netFor("driver", "CONTROL"));
        terminal(spec, plan, "driver", "RG", "2", "2",
                plan.netFor("driver", "GATE"));
        terminal(spec, plan, "driver", "RPD", "1", "1",
                plan.netFor("driver", "GATE"));
        terminal(spec, plan, "driver", "RPD", "2", "2",
                plan.netFor("driver", "RETURN"));
        terminal(spec, plan, "driver", "Q1", "G", "G",
                plan.netFor("driver", "GATE"));
        terminal(spec, plan, "driver", "Q1", "D", "D",
                plan.netFor("driver", "SWITCHED_SINK"));
        terminal(spec, plan, "driver", "Q1", "S", "S",
                plan.netFor("driver", "RETURN"));

        terminal(spec, plan, "load", "RLOAD", "1", "1",
                plan.netFor("load", "SUPPLY"));
        terminal(spec, plan, "load", "RLOAD", "2", "2",
                plan.netFor("load", "LED_NODE"));
        terminal(spec, plan, "load", "LED1", "A", "A",
                plan.netFor("load", "LED_NODE"));
        terminal(spec, plan, "load", "LED1", "K", "K",
                plan.netFor("load", "SWITCHED_LOAD"));

        check(plan.netFor("load", "SWITCHED_LOAD").equals(
                plan.netFor("driver", "SWITCHED_SINK")),
                "controlled LED cathode is not joined to Q1 drain");
        check(spec.getElementDeclaration("driver", "Q1").getPostIndex("G") == 0
                && spec.getElementDeclaration("driver", "Q1").getPostIndex("S") == 1
                && spec.getElementDeclaration("driver", "Q1").getPostIndex("D") == 2,
                "controlled v" + version + " changed NMOS G/S/D post order");
        check(spec.getElementDeclaration("load", "LED1").getPostIndex("A") == 0
                && spec.getElementDeclaration("load", "LED1").getPostIndex("K") == 1,
                "controlled v" + version + " changed LED A/K polarity");
    }

    private static void provider(ElectricalRealizationSpec spec, String owner,
            String providerId, int version) {
        final ElectricalRealizationSpec.ProviderDeclaration declaration =
                spec.getProviderDeclarations().get(owner);
        check(declaration != null, "missing provider declaration " + owner);
        check(providerId.equals(declaration.getProviderId())
                && version == declaration.getProviderVersion(),
                "provider ownership changed for " + owner);
        check(declaration.getElementIds().size() > 0,
                "provider declaration has no owned elements " + owner);
    }

    private static void element(ElectricalRealizationSpec spec, String owner,
            String id, String kind, String first, int firstPost,
            String second, int secondPost) {
        element(spec, owner, id, kind, first, firstPost, second, secondPost,
                null, -1);
    }

    private static void element(ElectricalRealizationSpec spec, String owner,
            String id, String kind, String first, int firstPost,
            String second, int secondPost, String third, int thirdPost) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(owner, id);
        check(declaration != null, "missing element declaration " + owner + "/" + id);
        check(kind.equals(declaration.getKind()), "element kind changed for "
                + owner + "/" + id);
        check(declaration.getPostIndex(first) == firstPost
                && declaration.getPostIndex(second) == secondPost,
                "element terminal order changed for " + owner + "/" + id);
        if (third != null)
            check(declaration.getPostIndex(third) == thirdPost,
                    "element third terminal order changed for " + owner + "/" + id);
    }

    private static void terminal(ElectricalRealizationSpec spec,
            BoundedAssemblyPlan plan, String owner, String local,
            String terminalId, String packageTerminal, String net) {
        ElectricalRealizationSpec.TerminalMapping mapping =
                spec.getTerminalMapping(owner, local, terminalId);
        check(mapping != null, "missing terminal mapping " + owner + "/" + local
                + "." + terminalId);
        String expectedElement = "2".equals(terminalId) &&
                ("R1".equals(local) || "RG".equals(local) || "RLOAD".equals(local)) ?
                    local + "_SECONDARY" : local;
        ElectricalRealizationSpec.EndpointRef component = mapping.getComponentEndpoint();
        check(owner.equals(component.getOwnerKey()) &&
                expectedElement.equals(component.getElementId()) &&
                terminalId.equals(component.getTerminalId()),
                "component-side terminal representation changed for " + owner + "/" + local);
        check(owner.equals(mapping.getOwnerKey()) && local.equals(mapping.getLocalId())
                && terminalId.equals(mapping.getTerminalId())
                && packageTerminal.equals(mapping.getPackageTerminalId())
                && net.equals(mapping.getNetId()),
                "terminal/package/net correspondence changed for " + owner + "/"
                + local + "." + terminalId);
        check(mapping.getComponentId().equals(spec.getComponentId(owner, local)),
                "terminal escaped its declared component owner");
    }

    private static void mapCompleteness(ElectricalRealizationSpec spec,
            String label) {
        final ElectricalUnitPackageMap map = spec.getPackageMap();
        check(map.getPackages().keySet().equals(map.getPackageOwners().keySet()),
                label + ": package owners do not cover exactly package IDs");
        Set<String> covered = new HashSet<String>();
        for (ElectricalUnitPackageMap.Unit unit : map.getUnits().values()) {
            PhysicalPackage physical = map.getPackages().get(unit.getComponentId());
            check(physical != null, label + ": unit has undeclared package");
            check(unit.getOwnerKey().equals(map.getPackageOwners().get(
                    unit.getComponentId())), label + ": unit has foreign package owner");
            check(unit.getTerminalIds().size() == unit
                    .getPackageTerminalByUnitTerminal().size(),
                    label + ": unit declaration is incomplete");
            for (String packageTerminal : unit
                    .getPackageTerminalByUnitTerminal().values()) {
                check(physical.getTerminalIds().contains(packageTerminal),
                        label + ": unit maps to undeclared package terminal");
                covered.add(unit.getComponentId() + "|" + packageTerminal);
            }
        }
        for (Map.Entry<String, PhysicalPackage> entry : map.getPackages().entrySet())
            for (String terminal : entry.getValue().getTerminalIds())
                check(covered.contains(entry.getKey() + "|" + terminal),
                        label + ": package terminal lacks a unit declaration");

        for (ElectricalRealizationSpec.PhysicalUnitSpec unit :
                spec.getPhysicalUnits().values()) {
            check(spec.getPackageMap().getPackages().containsKey(unit.getComponentId()),
                    label + ": spec unit has no package instance");
            check(unit.getPackageTerminalByUnitTerminal().size() > 0,
                    label + ": spec unit has no public terminals");
        }
    }

    /** One independent same-package/two-unit canary, including shared rails. */
    private static void packageMapCompletenessAndSharedPackageShape() {
        PhysicalPackage shared = packageOf("A04_SHARED", "P1", "P2", "VCC", "GND");
        String componentId = "board/U1";
        String owner = "provider/multi-unit";
        ElectricalUnitPackageMap.Unit first = new ElectricalUnitPackageMap.Unit(
                owner, "board/U1/unit/analog", componentId,
                Arrays.asList("IN", "OUT", "VCC", "GND"), pairs(
                        "IN", "P1", "OUT", "P2", "VCC", "VCC", "GND", "GND"));
        ElectricalUnitPackageMap.Unit second = new ElectricalUnitPackageMap.Unit(
                owner, "board/U1/unit/indicator", componentId,
                Arrays.asList("A", "K", "VCC", "GND"), pairs(
                        "A", "P2", "K", "P1", "VCC", "VCC", "GND", "GND"));
        Map<String, PhysicalPackage> packages = new HashMap<String, PhysicalPackage>();
        packages.put(componentId, shared);
        Map<String, String> owners = new HashMap<String, String>();
        owners.put(componentId, owner);
        final ElectricalUnitPackageMap map = new ElectricalUnitPackageMap(
                ElectricalUnitPackageMap.VERSION, packages, owners,
                Arrays.asList(second, first));
        check(map.getPackageCount() == 1 && map.getUnitCount() == 2,
                "same-package logical units were counted as separate packages");
        check("VCC".equals(map.getUnits().get(first.getUnitId())
                .getPackageTerminalByUnitTerminal().get("VCC"))
                && "VCC".equals(map.getUnits().get(second.getUnitId())
                .getPackageTerminalByUnitTerminal().get("VCC"))
                && "GND".equals(map.getUnits().get(first.getUnitId())
                .getPackageTerminalByUnitTerminal().get("GND"))
                && "GND".equals(map.getUnits().get(second.getUnitId())
                .getPackageTerminalByUnitTerminal().get("GND")),
                "shared supply pins were not explicit in both unit declarations");
        expectUnsupported(new Runnable() {
            @Override public void run() { map.getUnits().clear(); }
        }, "shared-package map view");
    }

    private static void a03ManifestReplayPreservation() {
        for (int version : new int[] { 1, BoundedAssemblyRequest.GENERATOR_VERSION }) {
            long seed = version;
            BoundedAssemblyPlan plan = planFor(version, seed);
            String canonical = A03RealizationReplay.capture(plan).toCanonical();
            RealizationManifest parsed = RealizationManifest.parse(canonical);
            BoundedAssemblyPlan replay = A03RealizationReplay.resolve(parsed);
            check(canonical.equals(parsed.toCanonical()),
                    "A03 v" + version + " manifest bytes changed after parse");
            check(plan.getSemanticSignature().equals(replay.getSemanticSignature()),
                    "A03 v" + version + " replay changed resolved semantics");
            check(parsed.getDescriptor().getGenerator().getVersion() ==
                    BoundedAssemblyRequest.GENERATOR_VERSION
                    && parsed.getDescriptor().getRootSeed() == seed,
                    "A03 manifest changed current generator version or exact seed for route " + version);
            check(canonical.indexOf("generator=bounded-assembler@" +
                    BoundedAssemblyRequest.GENERATOR_VERSION) >= 0,
                    "A03 manifest omitted the current pinned generator version for route " + version);
        }
    }

    private static BoundedAssemblyPlan planFor(int version, long seed) {
        BoundedAssemblyRequest request;
        if (version == 1)
            request = BoundedAssemblyRequest.forCanary(seed);
        else if (version == BoundedAssemblyRequest.GENERATOR_VERSION)
            request = BoundedAssemblyRequest.forControlledIndicator(seed);
        else
            throw new IllegalArgumentException("Unsupported A04 test version");
        return BoundedAssemblyPlan.resolve(request);
    }

    private static PhysicalPackage packageOf(String id, String... terminalIds) {
        Vector<String> terminals = new Vector<String>();
        terminals.addAll(Arrays.asList(terminalIds));
        return PhysicalPackage.developerPackageWithGenericGeometry(id, terminals,
                new Vector<String>(), false);
    }

    private static Map<String, String> pairs(String... values) {
        if ((values.length & 1) != 0)
            throw new IllegalArgumentException("pairs require key/value pairs");
        Map<String, String> result = new HashMap<String, String>();
        for (int index = 0; index < values.length; index += 2)
            result.put(values[index], values[index + 1]);
        return result;
    }

    private static void immutableMap(final Map<?, ?> value, String label) {
        expectUnsupported(new Runnable() {
            @Override public void run() {
                ((Map<Object, Object>) value).clear();
            }
        }, label);
    }

    private static void expectIllegal(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + label);
    }

    private static void expectUnsupported(Runnable action, String label) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException: " + label);
    }

    private static void reservationAndSupplyCorrespondence(ElectricalRealizationSpec spec,
            int version) {
        for (ElectricalRealizationSpec.ProviderDeclaration provider :
                spec.getProviderDeclarations().values()) {
            int count = 0;
            for (ElectricalRealizationSpec.SolverReservation reservation :
                    spec.getSolverReservations().values())
                if (provider.getOwnerKey().equals(reservation.getOwnerKey())) count++;
            check(count == 1, "provider has no unique private coordinate reservation");
        }
        for (ElectricalRealizationSpec.SolverReservation first :
                spec.getSolverReservations().values())
            for (ElectricalRealizationSpec.SolverReservation second :
                    spec.getSolverReservations().values()) {
                if (first.getOwnerKey().equals(second.getOwnerKey())) continue;
                boolean disjoint = (long) first.getOriginX() + first.getWidth() <= second.getOriginX()
                    || (long) second.getOriginX() + second.getWidth() <= first.getOriginX()
                    || (long) first.getOriginY() + first.getHeight() <= second.getOriginY()
                    || (long) second.getOriginY() + second.getHeight() <= first.getOriginY();
                check(disjoint, "private coordinate reservations overlap across owners");
            }
        for (String source : version == 1 ? new String[] { "SUPPLY" } :
                new String[] { "LOAD_SUPPLY", "CONTROL_SUPPLY" }) {
            ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration("device", source);
            check(declaration != null && declaration.getPostIndex("+") == 1 &&
                declaration.getPostIndex("-") == 0,
                "voltage-source polarity disagrees with CircuitJS V(post1)-V(post0)");
        }
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition)
            throw new AssertionError(label);
    }
}
