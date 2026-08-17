package com.lushprojects.circuitjs1.client;

/** Creates a part only after the owning runtime has selected its stable identity. */
interface PhysicalPartIdentityFactory<P extends PhysicalPart<?>> {
    P create(String physicalPartId);
}
