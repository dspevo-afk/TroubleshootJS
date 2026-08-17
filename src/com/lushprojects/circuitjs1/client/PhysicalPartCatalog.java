package com.lushprojects.circuitjs1.client;

import java.util.Vector;

interface PhysicalPartCatalog<S extends PhysicalSpecification> {
    S getSpecification(String specificationId);
    Vector<S> getSpecifications();
}
