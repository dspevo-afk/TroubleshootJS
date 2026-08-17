package com.lushprojects.circuitjs1.client;

/** Supplies the two selection values owned by the Quick Play session seam. */
interface QuickPlayRandomSource {
    long nextLong();
}
