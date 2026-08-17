package com.lushprojects.circuitjs1.client;

/** Typed interpretation kept outside the generic physical-definition container. */
interface PhysicalDefinitionProvider<S extends PhysicalSpecification> {
    String getProviderId();
    void add(BoardPhysicalSpecifications specifications, S specification);
    S find(BoardPhysicalSpecifications specifications, String componentId);
    S require(BoardPhysicalSpecifications specifications, String componentId);
}
