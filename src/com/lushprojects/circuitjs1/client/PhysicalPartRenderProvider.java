package com.lushprojects.circuitjs1.client;

/** Package-keyed provider for physical body and interaction rendering. */
interface PhysicalPartRenderProvider {
    PhysicalPartRenderer getRenderer(PhysicalPart<?> part);
}
