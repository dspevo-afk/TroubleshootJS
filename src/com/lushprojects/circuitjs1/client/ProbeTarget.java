package com.lushprojects.circuitjs1.client;

interface ProbeTarget {
    boolean isValid();
    Point getMarkerPoint();
    CircuitMeasurementEndpoint getMeasurementEndpoint();
}