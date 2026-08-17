package com.lushprojects.circuitjs1.client;

interface PhysicalPartFactory<S extends PhysicalSpecification, P extends PhysicalPart<S>> {
    P create(String physicalPartId, S specification, PhysicalNameplate nameplate,
            PhysicalPartProvenance provenance);
}
