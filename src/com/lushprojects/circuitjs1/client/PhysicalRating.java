package com.lushprojects.circuitjs1.client;

/** Base type for typed immutable physical ratings. */
abstract class PhysicalRating {
    private final String id;

    PhysicalRating(String id) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Invalid physical rating ID");
        this.id = id;
    }

    String getId() { return id; }
}
