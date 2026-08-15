package com.lushprojects.circuitjs1.client;

interface ProbeTarget {
    boolean isValid();
    boolean isSameTarget(ProbeTarget other);
    Point getMarkerPoint();
    CircuitMeasurementEndpoint getMeasurementEndpoint();
}