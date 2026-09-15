package com.lushprojects.circuitjs1.client;

/** P06 prototype limits, not permission to insert links into normal player boards. */
final class PcbFactoryLinkPolicy {
    static final int MAX_LINKS = 2;
    static final int MAX_CANDIDATES = 32;
    static final int MIN_AREA_PER_LINK = 60000;
    static final int ADDED_ROUTE_COST = 400;
    private PcbFactoryLinkPolicy() { }

    static int validateLayout(PcbBoardLayout layout) {
        int count = 0;
        for (PcbComponentPlacement placement : layout.getComponents())
            if (placement.getPhysicalGeometry().getRaisedCrossover() != null) count++;
        Rectangle outline = layout.getBoardOutline();
        long area = (long)outline.width * outline.height;
        int nonLinks = layout.getComponents().size() - count;
        if (count > MAX_LINKS || count > 1 + nonLinks / 12 ||
                (long)count * MIN_AREA_PER_LINK > area)
            throw new IllegalArgumentException("Sparse factory-link count or density budget exceeded");
        return count;
    }

    static void requireCandidateIndex(int index) {
        if (index < 0 || index >= MAX_CANDIDATES)
            throw new IllegalArgumentException("Factory-link candidate budget exhausted");
    }

    static void validateConstruction(TroubleshootBoard board, PcbBoardLayout layout,
            BoardPhysicalSpecifications specs, GeneratedComponentBindings bindings,
            boolean developerOnly) {
        int count = 0;
        for (String id : board.getComponentIds()) {
            PhysicalPackage physical = board.getComponent(id).getPhysicalPackage();
            boolean raised = false;
            for (PhysicalPackage.GeometryVariant variant : physical.getGeometryVariants())
                raised |= variant.getGeometry().getRaisedCrossover() != null;
            PhysicalSpecification spec = specs.getSpecification(id);
            if (!raised && !(spec instanceof FactoryLinkSpecification)) continue;
            count++;
            if (!developerOnly || layout == null || !raised ||
                    !bindings.hasComponentBinding(id))
                throw new IllegalArgumentException("Factory crossover requires a fresh developer-only construction and proof; normal use awaits P09");
            VectorHelper.require(spec, physical, bindings.getSingleElement(id));
        }
        if (count > 0 && validateLayout(layout) != count)
            throw new IllegalArgumentException("Factory-link layout and electrical declarations disagree");
    }

    private static final class VectorHelper {
        static void require(PhysicalSpecification spec, PhysicalPackage physical, CircuitElm element) {
            java.util.Vector<CircuitElm> elements = new java.util.Vector<CircuitElm>();
            elements.add(element); FactoryLinkSpecification.requireBacking(spec, physical, elements);
        }
    }
}
