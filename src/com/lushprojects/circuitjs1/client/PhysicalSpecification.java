package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable typed catalog data for a physical part. */
interface PhysicalSpecification {
    String getSpecificationId();
    /** Hidden technical ratings carried by a specification, when applicable. */
    Vector<PhysicalRating> getRatings();
}
