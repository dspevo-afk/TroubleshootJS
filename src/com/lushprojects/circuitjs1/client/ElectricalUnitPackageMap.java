package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable correspondence between modeled electrical units and physical
 * component instances.  This is a data contract only; it does not allocate
 * CircuitJS elements, merge nets, or own physical geometry.
 */
final class ElectricalUnitPackageMap {
    static final int VERSION = 1;

    /* Qualified realization IDs use punctuation such as '/' and '@'. */
    private static final int MAX_ID_LENGTH = 512;

    private final Map<String, PhysicalPackage> packages;
    private final Map<String, String> packageOwners;
    private final Map<String, Unit> units;

    static final class Unit {
        private final String ownerKey;
        private final String unitId;
        private final String componentId;
        private final List<String> terminalIds;
        private final Map<String, String> packageTerminalByUnitTerminal;

        Unit(String ownerKey, String unitId, String componentId,
                Collection<String> terminalIds,
                Map<String, String> packageTerminalByUnitTerminal) {
            this.ownerKey = requireId(ownerKey, "unit.ownerKey");
            this.unitId = requireId(unitId, "unit.unitId");
            this.componentId = requireId(componentId, "unit.componentId");
            if (terminalIds == null || terminalIds.isEmpty())
                throw invalid("unit.terminalIds", "at least one electrical terminal is required");
            if (packageTerminalByUnitTerminal == null)
                throw invalid("unit.packageTerminalByUnitTerminal",
                    "map is required");

            TreeSet<String> declaredTerminals = new TreeSet<String>();
            for (String terminalId : terminalIds) {
                String validated = requireId(terminalId,
                    "unit.terminalIds");
                if (!declaredTerminals.add(validated))
                    throw invalid("unit.terminalIds", "duplicate terminal: "
                        + validated);
            }

            TreeMap<String, String> terminalMap =
                new TreeMap<String, String>();
            for (Map.Entry<String, String> entry :
                    packageTerminalByUnitTerminal.entrySet()) {
                if (entry == null)
                    throw invalid("unit.packageTerminalByUnitTerminal",
                        "entry is required");
                String unitTerminal = requireId(entry.getKey(),
                    "unit.packageTerminalByUnitTerminal.key");
                String packageTerminal = requireId(entry.getValue(),
                    "unit.packageTerminalByUnitTerminal.value");
                if (terminalMap.put(unitTerminal, packageTerminal) != null)
                    throw invalid("unit.packageTerminalByUnitTerminal",
                        "duplicate terminal: " + unitTerminal);
            }
            if (!declaredTerminals.equals(terminalMap.keySet()))
                throw invalid("unit.packageTerminalByUnitTerminal",
                    "keys must exactly match declared unit terminals");

            this.terminalIds = Collections.unmodifiableList(
                new ArrayList<String>(declaredTerminals));
            this.packageTerminalByUnitTerminal = Collections.unmodifiableMap(
                terminalMap);
        }

        String getOwnerKey() {
            return ownerKey;
        }

        String getUnitId() {
            return unitId;
        }

        String getComponentId() {
            return componentId;
        }

        List<String> getTerminalIds() {
            return terminalIds;
        }

        Map<String, String> getPackageTerminalByUnitTerminal() {
            return packageTerminalByUnitTerminal;
        }
    }

    ElectricalUnitPackageMap(int version,
            Map<String, PhysicalPackage> packages,
            Map<String, String> packageOwners,
            Collection<Unit> units) {
        if (version != VERSION)
            throw invalid("version", "unsupported version: " + version);
        if (packages == null)
            throw invalid("packages", "map is required");
        if (packageOwners == null)
            throw invalid("packageOwners", "map is required");
        if (units == null)
            throw invalid("units", "collection is required");

        TreeMap<String, PhysicalPackage> packageCopy =
            new TreeMap<String, PhysicalPackage>();
        for (Map.Entry<String, PhysicalPackage> entry : packages.entrySet()) {
            if (entry == null)
                throw invalid("packages", "entry is required");
            String componentId = requireId(entry.getKey(), "packages.key");
            if (entry.getValue() == null)
                throw invalid("packages." + componentId,
                    "physical package is required");
            packageCopy.put(componentId, entry.getValue());
        }

        TreeMap<String, String> ownerCopy = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : packageOwners.entrySet()) {
            if (entry == null)
                throw invalid("packageOwners", "entry is required");
            String componentId = requireId(entry.getKey(),
                "packageOwners.key");
            String ownerKey = requireId(entry.getValue(),
                "packageOwners." + componentId);
            ownerCopy.put(componentId, ownerKey);
        }
        if (!packageCopy.keySet().equals(ownerCopy.keySet()))
            throw invalid("packageOwners", "keys must exactly match packages");

        TreeMap<String, Unit> unitCopy = new TreeMap<String, Unit>();
        for (Unit unit : units) {
            if (unit == null)
                throw invalid("units", "unit is required");
            if (unitCopy.put(unit.getUnitId(), unit) != null)
                throw invalid("units", "duplicate unit ID: "
                    + unit.getUnitId());
        }

        TreeMap<String, Set<String>> coveredPackageTerminals =
            new TreeMap<String, Set<String>>();
        for (Map.Entry<String, PhysicalPackage> entry : packageCopy.entrySet())
            coveredPackageTerminals.put(entry.getKey(),
                new HashSet<String>());

        for (Unit unit : unitCopy.values()) {
            String componentId = unit.getComponentId();
            PhysicalPackage physicalPackage = packageCopy.get(componentId);
            if (physicalPackage == null)
                throw invalid("units." + unit.getUnitId(),
                    "component is not declared: " + componentId);
            String packageOwner = ownerCopy.get(componentId);
            if (!packageOwner.equals(unit.getOwnerKey()))
                throw invalid("units." + unit.getUnitId(),
                    "component has a different package owner");

            Set<String> covered = coveredPackageTerminals.get(componentId);
            for (String packageTerminal :
                    unit.getPackageTerminalByUnitTerminal().values()) {
                if (!physicalPackage.getTerminalIds().contains(packageTerminal))
                    throw invalid("units." + unit.getUnitId(),
                        "mapped physical terminal is not declared: "
                            + packageTerminal);
                covered.add(packageTerminal);
            }
        }

        for (Map.Entry<String, PhysicalPackage> entry : packageCopy.entrySet()) {
            Set<String> covered = coveredPackageTerminals.get(entry.getKey());
            for (String packageTerminal : entry.getValue().getTerminalIds()) {
                if (!covered.contains(packageTerminal))
                    throw invalid("packages." + entry.getKey(),
                        "declared terminal is not covered: "
                            + packageTerminal);
            }
        }

        this.packages = Collections.unmodifiableMap(packageCopy);
        this.packageOwners = Collections.unmodifiableMap(ownerCopy);
        this.units = Collections.unmodifiableMap(unitCopy);
    }

    Map<String, PhysicalPackage> getPackages() {
        return packages;
    }

    Map<String, String> getPackageOwners() {
        return packageOwners;
    }

    Map<String, Unit> getUnits() {
        return units;
    }

    int getPackageCount() {
        return packages.size();
    }

    int getUnitCount() {
        return units.size();
    }

    /**
     * IDs are bounded printable ASCII atoms.  The qualified IDs already used
     * by the realization layer remain legal; only record delimiters are
     * excluded so a future canonical encoding cannot become ambiguous.
     */
    private static String requireId(String value, String field) {
        if (value == null || value.length() == 0
                || value.length() > MAX_ID_LENGTH)
            throw invalid(field, "missing or overlong ASCII ID");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e
                    || character == '|' || character == ','
                    || character == '=')
                throw invalid(field, "ID contains unsupported character");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String field,
            String message) {
        return new IllegalArgumentException("Invalid electrical unit/package map "
            + field + ": " + message);
    }
}
