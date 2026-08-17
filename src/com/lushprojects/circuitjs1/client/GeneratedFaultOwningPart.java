package com.lushprojects.circuitjs1.client;

/** Identifies the physical part that owns a selected generated fault binding. */
interface GeneratedFaultOwningPart {
    boolean ownsGeneratedFault(GeneratedFaultBinding binding);
}
