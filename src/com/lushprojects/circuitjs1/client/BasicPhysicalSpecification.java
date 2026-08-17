package com.lushprojects.circuitjs1.client;

/** Catalog definition for a fixed physical item without typed mutable values. */
final class BasicPhysicalSpecification implements PhysicalSpecification {
    private final String id;

    BasicPhysicalSpecification(String id) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Missing physical specification ID");
        this.id = id;
    }

    public String getSpecificationId() {
        return id;
    }
}
