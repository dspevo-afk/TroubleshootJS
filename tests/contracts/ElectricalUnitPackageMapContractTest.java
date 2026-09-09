package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/** Native contract oracle for the A04 unit-to-physical-package mapping. */
final class ElectricalUnitPackageMapContractTest {
    private static int assertions;

    public static void main(String[] args) {
        try {
            normalResistorMapping();
            sharedPackageMapping();
            rejectsInvalidOwnershipAndMappings();
            rejectsDuplicateAndUnsupportedDeclarations();
            defensiveCopyingAndCanonicalOrdering();
            rejectsInvalidIdsAndNulls();
            System.out.println("PASS: ElectricalUnitPackageMapContractTest assertions="
                + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.println("FAIL: ElectricalUnitPackageMapContractTest after "
                + assertions + " assertions: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void normalResistorMapping() {
        PhysicalPackage resistor = packageOf("AXIAL_RESISTOR", "1", "2");
        String componentId = "tsj-realization-v1/device@1/board/component/R1";
        String ownerKey = "provider/resistive@1";
        ElectricalUnitPackageMap.Unit unit = new ElectricalUnitPackageMap.Unit(
            ownerKey,
            "tsj-realization-v1/device@1/board/unit/R1",
            componentId,
            Arrays.asList("2", "1"),
            pairs("2", "2", "1", "1"));

        Map<String, PhysicalPackage> packages = new HashMap<String, PhysicalPackage>();
        packages.put(componentId, resistor);
        Map<String, String> owners = new HashMap<String, String>();
        owners.put(componentId, ownerKey);
        ElectricalUnitPackageMap map = new ElectricalUnitPackageMap(
            ElectricalUnitPackageMap.VERSION, packages, owners,
            Arrays.asList(unit));

        equal(1, map.getPackageCount(), "one physical component instance");
        equal(1, map.getUnitCount(), "one modeled resistor unit");
        check(map.getPackages().containsKey(componentId),
            "package map is keyed by component instance ID");
        check(!map.getPackages().containsKey(resistor.getId()),
            "package type ID is not the package-map key");
        equal(Arrays.asList("1", "2"), unit.getTerminalIds(),
            "unit terminals are canonicalized");
        equal("1", unit.getPackageTerminalByUnitTerminal().get("1"),
            "resistor terminal one mapping");
        check(unit.getUnitId().indexOf('/') >= 0
                && unit.getUnitId().indexOf('@') >= 0,
            "qualified unit ID punctuation is accepted");
    }

    private static void sharedPackageMapping() {
        PhysicalPackage shared = packageOf("SIX_PIN_SHARED_PACKAGE",
            "P1", "P2", "P3", "P4", "VCC", "GND");
        String componentId = "board/instance@1/component/U1";
        String ownerKey = "provider/mixed-units@1";
        ElectricalUnitPackageMap.Unit resistor =
            new ElectricalUnitPackageMap.Unit(ownerKey,
                "board/instance@1/unit/resistor",
                componentId,
                Arrays.asList("GND", "R2", "VCC", "R1"),
                pairs("R2", "P2", "GND", "GND", "VCC", "VCC",
                    "R1", "P1"));
        ElectricalUnitPackageMap.Unit indicator =
            new ElectricalUnitPackageMap.Unit(ownerKey,
                "board/instance@1/unit/indicator",
                componentId,
                Arrays.asList("CATHODE", "VCC", "ANODE", "GND"),
                pairs("CATHODE", "P4", "GND", "GND", "ANODE", "P3",
                    "VCC", "VCC"));

        Map<String, PhysicalPackage> packages = new HashMap<String, PhysicalPackage>();
        packages.put(componentId, shared);
        Map<String, String> owners = new HashMap<String, String>();
        owners.put(componentId, ownerKey);
        ElectricalUnitPackageMap map = new ElectricalUnitPackageMap(
            ElectricalUnitPackageMap.VERSION, packages, owners,
            Arrays.asList(indicator, resistor));

        equal(1, map.getPackageCount(), "shared package count is one");
        equal(2, map.getUnitCount(), "shared package has two unlike units");
        equal("VCC", map.getUnits().get(
            "board/instance@1/unit/resistor"
        ).getPackageTerminalByUnitTerminal().get("VCC"),
            "resistor explicitly maps shared VCC pin");
        equal("VCC", map.getUnits().get(
            "board/instance@1/unit/indicator"
        ).getPackageTerminalByUnitTerminal().get("VCC"),
            "indicator explicitly maps shared VCC pin");
        equal("GND", map.getUnits().get(
            "board/instance@1/unit/resistor"
        ).getPackageTerminalByUnitTerminal().get("GND"),
            "resistor explicitly maps shared GND pin");
        equal("GND", map.getUnits().get(
            "board/instance@1/unit/indicator"
        ).getPackageTerminalByUnitTerminal().get("GND"),
            "indicator explicitly maps shared GND pin");
    }

    private static void rejectsInvalidOwnershipAndMappings() {
        final PhysicalPackage packageWithFourPins = packageOf("FOUR_PIN", "1", "2", "3", "4");
        final String componentId = "board/component/U1";
        final String ownerKey = "owner/a";
        final ElectricalUnitPackageMap.Unit valid = unit(ownerKey, "unit/valid",
            componentId, "1", "2", "3", "4");

        expectIllegal("missing package declaration", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    packages("board/component/other", packageWithFourPins),
                    owners("board/component/other", ownerKey),
                    Arrays.asList(valid));
            }
        });

        expectIllegal("foreign physical terminal", new Runnable() {
            public void run() {
                ElectricalUnitPackageMap.Unit foreignTerminal =
                    new ElectricalUnitPackageMap.Unit(ownerKey, "unit/foreign",
                        componentId, Arrays.asList("one", "two", "three", "four"),
                        pairs("one", "1", "two", "2", "three", "3",
                            "four", "foreign"));
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    packages(componentId, packageWithFourPins),
                    owners(componentId, ownerKey), Arrays.asList(foreignTerminal));
            }
        });

        expectIllegal("conflicting package owner", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    packages(componentId, packageWithFourPins),
                    owners(componentId, "owner/other"), Arrays.asList(valid));
            }
        });

        expectIllegal("missing package terminal coverage", new Runnable() {
            public void run() {
                ElectricalUnitPackageMap.Unit incomplete =
                    new ElectricalUnitPackageMap.Unit(ownerKey, "unit/incomplete",
                        componentId, Arrays.asList("one", "two"),
                        pairs("one", "1", "two", "2"));
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    packages(componentId, packageWithFourPins),
                    owners(componentId, ownerKey), Arrays.asList(incomplete));
            }
        });

        expectIllegal("package and owner keysets differ", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    packages(componentId, packageWithFourPins),
                    new HashMap<String, String>(), Arrays.asList(valid));
            }
        });

        expectIllegal("unit terminal map has a missing key", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap.Unit(ownerKey, "unit/missing-map-key",
                    componentId, Arrays.asList("one", "two"),
                    pairs("one", "1"));
            }
        });
    }

    private static void rejectsDuplicateAndUnsupportedDeclarations() {
        final PhysicalPackage resistor = packageOf("RESISTOR", "1", "2");
        final String componentId = "component/R1";
        final String ownerKey = "owner/resistor";
        final ElectricalUnitPackageMap.Unit first = unit(ownerKey, "unit/R1",
            componentId, "1", "2");
        final ElectricalUnitPackageMap.Unit second = unit(ownerKey, "unit/R1",
            componentId, "1", "2");

        expectIllegal("duplicate unit ID", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    packages(componentId, resistor), owners(componentId, ownerKey),
                    Arrays.asList(first, second));
            }
        });

        expectIllegal("duplicate declared terminal", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap.Unit(ownerKey, "unit/duplicate-terminal",
                    componentId, Arrays.asList("1", "1"),
                    pairs("1", "1"));
            }
        });

        expectIllegal("empty phantom electrical unit", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap.Unit(ownerKey, "unit/phantom",
                    componentId, new ArrayList<String>(),
                    new HashMap<String, String>());
            }
        });

        expectIllegal("unsupported version", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(2, packages(componentId, resistor),
                    owners(componentId, ownerKey), Arrays.asList(first));
            }
        });
    }

    private static void defensiveCopyingAndCanonicalOrdering() {
        PhysicalPackage resistor = packageOf("RESISTOR", "1", "2");
        String componentId = "component/R1";
        String ownerKey = "owner/resistor";
        List<String> terminals = new ArrayList<String>(Arrays.asList("2", "1"));
        Map<String, String> terminalMap = pairs("2", "2", "1", "1");
        ElectricalUnitPackageMap.Unit unit = new ElectricalUnitPackageMap.Unit(
            ownerKey, "unit/R1", componentId, terminals, terminalMap);
        Map<String, PhysicalPackage> packages = packages(componentId, resistor);
        Map<String, String> owners = owners(componentId, ownerKey);
        final ElectricalUnitPackageMap map = new ElectricalUnitPackageMap(
            ElectricalUnitPackageMap.VERSION, packages, owners,
            Arrays.asList(unit));

        terminals.clear();
        terminalMap.clear();
        packages.clear();
        owners.clear();
        equal(Arrays.asList("1", "2"), unit.getTerminalIds(),
            "unit copied terminal collection");
        equal(1, map.getPackageCount(), "map copied package collection");
        equal(1, map.getUnitCount(), "map copied unit collection");
        equal(Arrays.asList("1", "2"), map.getUnits().get("unit/R1").getTerminalIds(),
            "map retains immutable unit copy");

        expectUnsupported("package output is immutable", new Runnable() {
            public void run() {
                map.getPackages().clear();
            }
        });
        expectUnsupported("owner output is immutable", new Runnable() {
            public void run() {
                map.getPackageOwners().clear();
            }
        });
        expectUnsupported("unit output is immutable", new Runnable() {
            public void run() {
                map.getUnits().clear();
            }
        });
        expectUnsupported("terminal list output is immutable", new Runnable() {
            public void run() {
                map.getUnits().get("unit/R1").getTerminalIds().add("3");
            }
        });
        expectUnsupported("terminal mapping output is immutable", new Runnable() {
            public void run() {
                map.getUnits().get("unit/R1").getPackageTerminalByUnitTerminal()
                    .put("3", "3");
            }
        });
    }

    private static void rejectsInvalidIdsAndNulls() {
        expectIllegal("null unit owner", new Runnable() {
            public void run() {
                unit(null, "unit/one", "component/one", "1", "2");
            }
        });
        expectIllegal("unicode unit ID", new Runnable() {
            public void run() {
                unit("owner/one", "unit/é", "component/one", "1", "2");
            }
        });
        expectIllegal("record delimiter in component ID", new Runnable() {
            public void run() {
                unit("owner/one", "unit/one", "component|one", "1", "2");
            }
        });
        expectIllegal("null outer packages", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    null, new HashMap<String, String>(),
                    new ArrayList<ElectricalUnitPackageMap.Unit>());
            }
        });
        expectIllegal("null unit item", new Runnable() {
            public void run() {
                new ElectricalUnitPackageMap(ElectricalUnitPackageMap.VERSION,
                    new HashMap<String, PhysicalPackage>(),
                    new HashMap<String, String>(),
                    Arrays.asList((ElectricalUnitPackageMap.Unit) null));
            }
        });
    }

    private static ElectricalUnitPackageMap.Unit unit(String ownerKey,
            String unitId, String componentId, String... terminalIds) {
        Map<String, String> mapping = new HashMap<String, String>();
        for (int index = 0; index < terminalIds.length; index++)
            mapping.put(terminalIds[index], String.valueOf(index + 1));
        return new ElectricalUnitPackageMap.Unit(ownerKey, unitId, componentId,
            Arrays.asList(terminalIds), mapping);
    }

    private static PhysicalPackage packageOf(String id, String... terminalIds) {
        Vector<String> terminals = new Vector<String>();
        terminals.addAll(Arrays.asList(terminalIds));
        return PhysicalPackage.developerPackageWithGenericGeometry(id, terminals,
                new Vector<String>(), false);
    }

    private static Map<String, PhysicalPackage> packages(String componentId,
            PhysicalPackage physicalPackage) {
        Map<String, PhysicalPackage> result =
            new HashMap<String, PhysicalPackage>();
        result.put(componentId, physicalPackage);
        return result;
    }

    private static Map<String, String> owners(String componentId,
            String ownerKey) {
        Map<String, String> result = new HashMap<String, String>();
        result.put(componentId, ownerKey);
        return result;
    }

    private static Map<String, String> pairs(String... values) {
        if ((values.length & 1) != 0)
            throw new IllegalArgumentException("pairs require key/value pairs");
        Map<String, String> result = new HashMap<String, String>();
        for (int index = 0; index < values.length; index += 2)
            result.put(values[index], values[index + 1]);
        return result;
    }

    private static void expectIllegal(String description, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + description);
    }

    private static void expectUnsupported(String description, Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException: " + description);
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition)
            throw new AssertionError(description);
    }

    private static void equal(Object expected, Object actual, String description) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(description + "; expected " + expected
                + " but was " + actual);
    }
}
