package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Random;
import java.util.Vector;

/** Typed package definition shared by placement, routing, and installed parts. */
final class PhysicalPackage {
    /** The package, rather than a package ID, owns variant selection policy. */
    enum GeometryVariantSelection {
        FIXED_DEFAULT,
        SEEDED_CATALOG,
        EDGE_ORIENTED
    }

    /** Immutable named realization in a package's finite geometry catalog. */
    static final class GeometryVariant {
        private final String key;
        private final String transformKey;
        private final PhysicalPackageGeometry geometry;

        GeometryVariant(String key, String transformKey, PhysicalPackageGeometry geometry) {
            if (key == null || key.trim().length() == 0 || transformKey == null ||
                    transformKey.trim().length() == 0 || geometry == null)
                throw new IllegalArgumentException("Invalid physical package geometry variant");
            this.key = key;
            this.transformKey = transformKey;
            this.geometry = geometry;
        }

        String getKey() { return key; }
        String getTransformKey() { return transformKey; }
        PhysicalPackageGeometry getGeometry() { return geometry; }

        boolean isEquivalentTo(GeometryVariant other) {
            return other != null && key.equals(other.key) &&
                transformKey.equals(other.transformKey) && geometry.isEquivalentTo(other.geometry);
        }
    }

    private final String id;
    private final Vector<String> terminalIds;
    private final Vector<String> internalConnections;
    private final boolean connector;
    private final PhysicalPackageGeometry geometry;
    private final Vector<GeometryVariant> geometryVariants;
    private final String defaultLooseGeometryVariantKey;
    private final GeometryVariantSelection geometryVariantSelection;
    private final boolean developerGeneric;
    private final PcbGeometryContractVersion geometryContractVersion;

    /** Legacy constructor retained only as a marked developer-generic boundary. */
    PhysicalPackage(String id, int terminalCount) {
        this(id, terminalIdsForCount(terminalCount), new Vector<String>(), false,
            PhysicalPackageGeometry.generic(terminalIdsForCount(terminalCount), false), true);
    }

    /** Legacy constructor retained only as a marked developer-generic boundary. */
    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections) {
        this(id, terminalIds, internalConnections, false,
            PhysicalPackageGeometry.generic(terminalIds, false), true);
    }

    /** Legacy constructor retained only as a marked developer-generic boundary. */
    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector) {
        this(id, terminalIds, internalConnections, connector,
            PhysicalPackageGeometry.generic(terminalIds, connector), true);
    }

    /** Authoritative production constructor; geometry may not be null or generic. */
    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector, PhysicalPackageGeometry geometry) {
        this(id, terminalIds, internalConnections, connector, geometry, false);
    }

    private PhysicalPackage(String id, Vector<String> terminalIds,
            Vector<String> internalConnections, boolean connector,
            PhysicalPackageGeometry geometry, boolean developerGeneric) {
        this(id, terminalIds, internalConnections, connector, geometry,
            singletonVariants(geometry, developerGeneric),
            developerGeneric ? "DEVELOPER_DEFAULT" : "DEFAULT",
            GeometryVariantSelection.FIXED_DEFAULT, developerGeneric);
    }

    /** Authoritative package constructor with an explicit finite variant catalog. */
    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector, PhysicalPackageGeometry geometry,
            Vector<GeometryVariant> geometryVariants, String defaultLooseGeometryVariantKey,
            GeometryVariantSelection geometryVariantSelection) {
        this(id, terminalIds, internalConnections, connector, geometry, geometryVariants,
            defaultLooseGeometryVariantKey, geometryVariantSelection, false);
    }

    private PhysicalPackage(String id, Vector<String> terminalIds,
            Vector<String> internalConnections, boolean connector,
            PhysicalPackageGeometry geometry, Vector<GeometryVariant> geometryVariants,
            String defaultLooseGeometryVariantKey,
            GeometryVariantSelection geometryVariantSelection, boolean developerGeneric) {
        if (id == null || id.trim().length() == 0 || terminalIds == null ||
                terminalIds.size() < 1 || internalConnections == null || geometry == null ||
                geometryVariants == null || geometryVariants.size() < 1 ||
                defaultLooseGeometryVariantKey == null ||
                defaultLooseGeometryVariantKey.trim().length() == 0 ||
                geometryVariantSelection == null)
            throw new IllegalArgumentException("Invalid physical package");
        if (geometry.isDeveloperGeneric() != developerGeneric)
            throw new IllegalArgumentException("Production package geometry must be authoritative: " +
                id);

        this.id = id;
        this.terminalIds = new Vector<String>(terminalIds);
        this.connector = connector;
        for (int index = 0; index < this.terminalIds.size(); index++) {
            String terminalId = this.terminalIds.get(index);
            if (terminalId == null || terminalId.trim().length() == 0)
                throw new IllegalArgumentException("Invalid physical package terminal");
            for (int previous = 0; previous < index; previous++)
                if (terminalId.equals(this.terminalIds.get(previous)))
                    throw new IllegalArgumentException("Duplicate physical package terminal: " +
                        terminalId);
        }

        Vector<String> normalizedConnections = new Vector<String>();
        for (String connection : internalConnections) {
            if (connection == null)
                throw new IllegalArgumentException("Invalid physical package connectivity");
            int separator = connection.indexOf('=');
            if (separator <= 0 || separator != connection.lastIndexOf('=') ||
                    separator == connection.length() - 1)
                throw new IllegalArgumentException("Invalid physical package connectivity: " +
                    connection);
            String first = connection.substring(0, separator);
            String second = connection.substring(separator + 1);
            if (first.equals(second) || !this.terminalIds.contains(first) ||
                    !this.terminalIds.contains(second))
                throw new IllegalArgumentException("Physical package connectivity references " +
                    "invalid terminals: " + connection);
            String normalized = first.compareTo(second) < 0 ? first + "=" + second :
                second + "=" + first;
            if (normalizedConnections.contains(normalized))
                throw new IllegalArgumentException("Duplicate physical package connectivity: " +
                    connection);
            normalizedConnections.add(normalized);
        }
        Collections.sort(normalizedConnections);
        this.internalConnections = normalizedConnections;

        this.geometryVariants = new Vector<GeometryVariant>();
        GeometryVariant defaultLoose = null;
        for (GeometryVariant variant : geometryVariants) {
            if (variant == null || this.geometryVariantsContainsKey(variant.getKey()))
                throw new IllegalArgumentException("Duplicate physical package geometry variant: " +
                    id);
            PhysicalPackageGeometry variantGeometry = variant.getGeometry();
            if (variantGeometry.isDeveloperGeneric() != developerGeneric ||
                    !variantGeometry.hasTerminalIds(this.terminalIds) ||
                    !variantGeometry.getGeometryContractVersion().equals(
                        geometry.getGeometryContractVersion()))
                throw new IllegalArgumentException("Physical package variant geometry mismatch: " +
                    id);
            this.geometryVariants.add(variant);
            if (defaultLooseGeometryVariantKey.equals(variant.getKey()))
                defaultLoose = variant;
        }
        if (defaultLoose == null || defaultLoose.getGeometry() != geometry)
            throw new IllegalArgumentException("Physical package default geometry is not canonical: " +
                id);
        boolean foundGeometry = false;
        for (GeometryVariant variant : this.geometryVariants)
            if (variant.getGeometry() == geometry)
                foundGeometry = true;
        if (!foundGeometry)
            throw new IllegalArgumentException("Physical package geometry is undeclared: " + id);

        this.geometry = geometry;
        this.defaultLooseGeometryVariantKey = defaultLooseGeometryVariantKey;
        this.geometryVariantSelection = geometryVariantSelection;
        this.developerGeneric = developerGeneric;
        this.geometryContractVersion = geometry.getGeometryContractVersion();
    }

    /** Explicit generic geometry is reserved for developer/future-package canaries. */
    static PhysicalPackage developerPackageWithGenericGeometry(String id,
            Vector<String> terminalIds, Vector<String> internalConnections, boolean connector) {
        PhysicalPackageGeometry geometry = PhysicalPackageGeometry.generic(terminalIds, connector);
        Vector<GeometryVariant> variants = new Vector<GeometryVariant>();
        variants.add(new GeometryVariant(connector ? "DEFAULT" : "DEVELOPER_DEFAULT",
            connector ? "IDENTITY" : "DEVELOPER_GENERIC", geometry));
        if (connector)
            variants.add(new GeometryVariant("DEFAULT_MIRRORED_X", "DEVELOPER_MIRROR_X",
                geometry.mirroredHorizontally()));
        return new PhysicalPackage(id, terminalIds, internalConnections, connector, geometry,
            variants, connector ? "DEFAULT" : "DEVELOPER_DEFAULT",
            connector ? GeometryVariantSelection.EDGE_ORIENTED :
            GeometryVariantSelection.FIXED_DEFAULT, true);
    }

    /**
     * Explicit compatibility adapter for the old generic placement factory. It
     * is deliberately impossible to use with production geometry.
     */
    static PhysicalPackage developerProjectionForGeometry(String componentId,
            PhysicalPackageGeometry geometry) {
        if (componentId == null || componentId.trim().length() == 0 || geometry == null ||
                !geometry.isDeveloperGeneric())
            throw new IllegalArgumentException("Only marked developer geometry may be projected");
        Vector<String> terminals = geometry.getTerminalIds();
        Vector<String> connections = new Vector<String>();
        Vector<GeometryVariant> catalog = singletonVariants(geometry, true);
        catalog.set(0, new GeometryVariant("DEVELOPER_PROJECTION", "DEVELOPER_GENERIC",
            geometry));
        return new PhysicalPackage("DEVELOPER_PROJECTION_" + componentId, terminals, connections,
            false, geometry, catalog, "DEVELOPER_PROJECTION",
            GeometryVariantSelection.FIXED_DEFAULT, true);
    }

    String getId() { return id; }
    int getTerminalCount() { return terminalIds.size(); }
    Vector<String> getTerminalIds() { return new Vector<String>(terminalIds); }
    boolean isConnector() { return connector; }

    /** Package-declared default loose projection; retained under the legacy name. */
    PhysicalPackageGeometry getGeometry() { return geometry; }
    PhysicalPackageGeometry getDefaultLooseGeometry() { return geometry; }
    String getDefaultLooseGeometryVariantKey() { return defaultLooseGeometryVariantKey; }
    GeometryVariantSelection getGeometryVariantSelection() { return geometryVariantSelection; }
    boolean isDeveloperGeneric() { return developerGeneric; }
    PcbGeometryContractVersion getGeometryContractVersion() { return geometryContractVersion; }
    int getGeometryContractVersionValue() { return geometryContractVersion.getValue(); }

    Vector<GeometryVariant> getGeometryVariants() {
        return new Vector<GeometryVariant>(geometryVariants);
    }

    GeometryVariant getGeometryVariant(String key) {
        if (key == null)
            return null;
        for (GeometryVariant variant : geometryVariants)
            if (key.equals(variant.getKey()))
                return variant;
        return null;
    }

    String getGeometryVariantKey(PhysicalPackageGeometry candidate) {
        GeometryVariant variant = findCanonicalVariant(candidate);
        return variant == null ? null : variant.getKey();
    }

    String getGeometryVariantTransformKey(PhysicalPackageGeometry candidate) {
        GeometryVariant variant = findCanonicalVariant(candidate);
        return variant == null ? null : variant.getTransformKey();
    }

    PhysicalPackageGeometry geometryForPlacement(Random random, int x, Rectangle outline) {
        GeometryVariant selected = geometryVariants.get(0);
        if (geometryVariantSelection == GeometryVariantSelection.SEEDED_CATALOG)
            selected = geometryVariants.get(nextInt(random, geometryVariants.size()));
        else if (geometryVariantSelection == GeometryVariantSelection.EDGE_ORIENTED &&
                isRightEdge(x, outline)) {
            for (GeometryVariant variant : geometryVariants)
                if ("MIRROR_X".equals(variant.getTransformKey()) ||
                        "DEVELOPER_MIRROR_X".equals(variant.getTransformKey())) {
                    selected = variant;
                    break;
                }
        }
        return selected.getGeometry();
    }

    /** Only canonical catalog objects are accepted; structural equality is insufficient. */
    boolean acceptsGeometry(PhysicalPackageGeometry candidate) {
        return findCanonicalVariant(candidate) != null;
    }

    /** Package identity is structural for registry comparison; placement uses object identity. */
    boolean isEquivalentTo(PhysicalPackage other) {
        if (other == null || !id.equals(other.id) || connector != other.connector ||
                !terminalIds.equals(other.terminalIds) ||
                !internalConnections.equals(other.internalConnections) ||
                !defaultLooseGeometryVariantKey.equals(other.defaultLooseGeometryVariantKey) ||
                geometryVariantSelection != other.geometryVariantSelection ||
                developerGeneric != other.developerGeneric ||
                !geometryContractVersion.equals(other.geometryContractVersion) ||
                geometryVariants.size() != other.geometryVariants.size())
            return false;
        for (int index = 0; index < geometryVariants.size(); index++)
            if (!geometryVariants.get(index).isEquivalentTo(other.geometryVariants.get(index)))
                return false;
        return geometry.isEquivalentTo(other.geometry);
    }

    /** Returns true only for a declared package-internal connection. */
    boolean isInternallyConnected(String firstTerminal, String secondTerminal) {
        if (firstTerminal == null || secondTerminal == null || firstTerminal.equals(secondTerminal) ||
                !terminalIds.contains(firstTerminal) || !terminalIds.contains(secondTerminal))
            return false;
        String forward = firstTerminal + "=" + secondTerminal;
        String reverse = secondTerminal + "=" + firstTerminal;
        return internalConnections.contains(forward) || internalConnections.contains(reverse);
    }

    private boolean geometryVariantsContainsKey(String key) {
        for (GeometryVariant variant : geometryVariants)
            if (variant.getKey().equals(key))
                return true;
        return false;
    }

    private GeometryVariant findCanonicalVariant(PhysicalPackageGeometry candidate) {
        if (candidate == null)
            return null;
        for (GeometryVariant variant : geometryVariants)
            if (variant.getGeometry() == candidate)
                return variant;
        return null;
    }

    private static Vector<GeometryVariant> singletonVariants(PhysicalPackageGeometry geometry,
            boolean developerGeneric) {
        Vector<GeometryVariant> result = new Vector<GeometryVariant>();
        result.add(new GeometryVariant(developerGeneric ? "DEVELOPER_DEFAULT" : "DEFAULT",
            developerGeneric ? "DEVELOPER_GENERIC" : "IDENTITY", geometry));
        return result;
    }

    private static Vector<String> terminalIdsForCount(int count) {
        if (count < 1)
            throw new IllegalArgumentException("Physical package must have a terminal");
        Vector<String> result = new Vector<String>();
        for (int index = 1; index <= count; index++)
            result.add(String.valueOf(index));
        return result;
    }

    private static int nextInt(Random random, int bound) {
        return random == null ? 0 : random.nextInt(bound);
    }

    private static boolean isRightEdge(int x, Rectangle outline) {
        if (outline == null)
            return false;
        long midpoint = (long) outline.x + outline.width / 2L;
        return (long) x >= midpoint;
    }
}
