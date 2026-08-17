package com.lushprojects.circuitjs1.client;

import java.util.Random;

/** Creates package geometry without making the generic placer aware of component types. */
interface PcbFootprintProvider {
    PcbFootprint create(BoardComponent component, int x, int y, Random random,
            Rectangle workingOutline);
}
