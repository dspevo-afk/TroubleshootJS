package com.lushprojects.circuitjs1.client;

/** Keeps component-specific casts outside the type-neutral runtime/inventory storage. */
interface PhysicalPartTypeAdapter<P extends PhysicalPart<?>> {
    P require(PhysicalPart<?> part);
}
