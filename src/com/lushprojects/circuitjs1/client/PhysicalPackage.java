package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Random;
import java.util.Vector;

/** Typed package definition shared by placement, routing, and installed parts. */
final class PhysicalPackage {
    /**
     * Variant production is an explicit package declaration.  NONE is used by
     * every ordinary/custom constructor, so a package that reuses a built-in
     * ID cannot inherit that built-in's randomized geometry by accident.
     */
    enum GeometryVariantOwner {
        NONE,
        AXIAL_RESISTOR_SPANS,
        AXIAL_DIODE_SPANS
    }

    private static final int[] RESISTOR_SPANS = new int[] { 220, 240, 260 };
    private static final int[] DIODE_SPANS = new int[] { 230, 250 };

    private final String id;
    private final Vector<String> terminalIds;
    private final Vector<String> internalConnections;
    private final boolean connector;
    private final PhysicalPackageGeometry geometry;
    private final GeometryVariantOwner geometryVariantOwner;

    PhysicalPackage(String id, int terminalCount) {
        this(id, terminalIdsForCount(terminalCount), new Vector<String>(), false, null);
    }

    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections) {
        this(id, terminalIds, internalConnections, false, null);
    }

    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector) {
        this(id, terminalIds, internalConnections, connector, null);
    }

    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector, PhysicalPackageGeometry geometry) {
        this(id, terminalIds, internalConnections, connector, geometry,
            GeometryVariantOwner.NONE);
    }

    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector, PhysicalPackageGeometry geometry,
            GeometryVariantOwner geometryVariantOwner) {
        if (id == null || id.trim().length() == 0 || terminalIds == null || terminalIds.size() < 1 ||
                internalConnections == null || geometryVariantOwner == null)
            throw new IllegalArgumentException("Invalid physical package");
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
        if (geometry == null)
            throw new IllegalArgumentException("Physical package requires authoritative geometry: " +
                id);
        this.geometry = geometry;
        this.geometryVariantOwner = geometryVariantOwner;
        if (!this.geometry.hasTerminalIds(this.terminalIds))
            throw new IllegalArgumentException("Physical package geometry terminal mismatch: " + id);
    }

    /**
     * Explicit generic geometry is reserved for developer/future-package
     * canaries.  Ordinary package declarations must pass authoritative
     * geometry to the regular constructor.
     */
    static PhysicalPackage developerPackageWithGenericGeometry(String id,
            Vector<String> terminalIds, Vector<String> internalConnections, boolean connector) {
        return new PhysicalPackage(id, terminalIds, internalConnections, connector,
            PhysicalPackageGeometry.generic(terminalIds, connector));
    }

    String getId() { return id; }
    int getTerminalCount() { return terminalIds.size(); }
    Vector<String> getTerminalIds() { return new Vector<String>(terminalIds); }
    boolean isConnector() { return connector; }
    PhysicalPackageGeometry getGeometry() { return geometry; }
    GeometryVariantOwner getGeometryVariantOwner() { return geometryVariantOwner; }

    PhysicalPackageGeometry geometryForPlacement(Random random, int x, Rectangle outline) {
        PhysicalPackageGeometry result = geometry;
        if (geometryVariantOwner == GeometryVariantOwner.AXIAL_RESISTOR_SPANS) {
            result = PhysicalPackages.axialResistorVariant(RESISTOR_SPANS[
                nextInt(random, RESISTOR_SPANS.length)]);
        } else if (geometryVariantOwner == GeometryVariantOwner.AXIAL_DIODE_SPANS) {
            result = PhysicalPackages.axialDiodeVariant(DIODE_SPANS[
                nextInt(random, DIODE_SPANS.length)]);
        }
        // Edge mirroring belongs to the declared connector capability, not its ID.
        if (connector && isRightEdge(x, outline))
            result = result.mirroredHorizontally();
        return result;
    }

    boolean acceptsGeometry(PhysicalPackageGeometry candidate) {
        if (candidate == null || !candidate.hasTerminalIds(terminalIds))
            return false;
        if (acceptsUnmirroredGeometry(candidate))
            return true;
        return connector && acceptsUnmirroredGeometry(candidate.mirroredHorizontally());
    }

    /** Package identity is declared by ID, while compatibility includes its definition. */
    boolean isEquivalentTo(PhysicalPackage other) {
        return other != null && id.equals(other.id) && connector == other.connector &&
            terminalIds.equals(other.terminalIds) && internalConnections.equals(
                other.internalConnections) && geometry.isEquivalentTo(other.geometry) &&
            geometryVariantOwner == other.geometryVariantOwner;
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

    private static Vector<String> terminalIdsForCount(int count) {
        if (count < 1)
            throw new IllegalArgumentException("Physical package must have a terminal");
        Vector<String> result = new Vector<String>();
        for (int index = 1; index <= count; index++)
            result.add(String.valueOf(index));
        return result;
    }

    private boolean acceptsUnmirroredGeometry(PhysicalPackageGeometry candidate) {
        if (geometry.isEquivalentTo(candidate))
            return true;
        if (geometryVariantOwner == GeometryVariantOwner.AXIAL_RESISTOR_SPANS) {
            for (int span : RESISTOR_SPANS)
                if (PhysicalPackages.axialResistorVariant(span).isEquivalentTo(candidate))
                    return true;
        } else if (geometryVariantOwner == GeometryVariantOwner.AXIAL_DIODE_SPANS) {
            for (int span : DIODE_SPANS)
                if (PhysicalPackages.axialDiodeVariant(span).isEquivalentTo(candidate))
                    return true;
        }
        return false;
    }

    private static int nextInt(Random random, int bound) {
        return random == null ? 0 : random.nextInt(bound);
    }

    private static boolean isRightEdge(int x, Rectangle outline) {
        return outline != null && x >= outline.x + outline.width / 2;
    }
}
