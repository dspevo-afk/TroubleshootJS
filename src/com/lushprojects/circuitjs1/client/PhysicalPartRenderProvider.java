package com.lushprojects.circuitjs1.client;

/** Package-keyed provider for physical body and interaction rendering. */
interface PhysicalPartRenderProvider {
    /** Select a renderer for an actual supported part; null is not a capability probe. */
    PhysicalPartRenderer getRenderer(PhysicalPart<?> part);
}
