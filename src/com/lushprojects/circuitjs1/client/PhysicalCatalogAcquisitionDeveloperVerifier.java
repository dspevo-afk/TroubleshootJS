package com.lushprojects.circuitjs1.client;

/** Shared developer assertions for production catalog acquisition identity boundaries. */
final class PhysicalCatalogAcquisitionDeveloperVerifier {
    private PhysicalCatalogAcquisitionDeveloperVerifier() { }

    static void verify(PhysicalPart<?> part, PhysicalCatalogEntry<?> entry, String componentId) {
        require(part != null && entry != null && componentId != null,
            "Missing catalog acquisition identity inputs");
        PhysicalSpecification specification = entry.getSpecification();
        PhysicalNameplate expected = entry.getPlayerVisibleNameplate();
        PhysicalNameplate actual = part.getPlayerVisibleNameplate();
        require(part.getSpecification() == specification &&
                !componentId.equals(specification.getSpecificationId()) &&
                !part.getId().equals(specification.getSpecificationId()) &&
                actual != null && actual != expected && actual.getId().equals(part.getId()) &&
                actual.getDisplayName().equals(expected.getDisplayName()) &&
                actual.hasWorkbenchDetail() == expected.hasWorkbenchDetail(),
            "Catalog acquisition discarded specification or nameplate identity: " + part.getId());
        if (expected.hasWorkbenchDetail())
            require(expected.getWorkbenchDetailLabel().equals(actual.getWorkbenchDetailLabel()) &&
                    expected.getWorkbenchDetailValue().equals(actual.getWorkbenchDetailValue()),
                "Catalog acquisition reconstructed visible metadata: " + part.getId());
        require(part.getOrientation() == entry.getOrientation(),
            "Catalog acquisition discarded orientation metadata: " + part.getId());
    }

    static void verifySameSpecification(PhysicalPart<?> first, PhysicalPart<?> second) {
        require(first != null && second != null && first != second &&
                !first.getId().equals(second.getId()) &&
                first.getSpecification() == second.getSpecification(),
            "Repeated catalog acquisition did not create a distinct part with shared spec identity");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
