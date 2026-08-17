package com.lushprojects.circuitjs1.client;

/** Stable origin metadata, separate from catalog specification and physical identity. */
final class PhysicalPartProvenance {
    static final String GENERATED_ORIGINAL = "GENERATED_ORIGINAL";
    static final String CATALOG_ACQUIRED = "CATALOG_ACQUIRED";
    static final String FIXED_GENERATED = "FIXED_GENERATED";
    static final String DEVELOPER_CANARY = "DEVELOPER_CANARY";

    private final String kind;
    private final String sourceId;

    PhysicalPartProvenance(String kind, String sourceId) {
        if (kind == null || kind.length() == 0 || sourceId == null || sourceId.length() == 0)
            throw new IllegalArgumentException("Invalid physical part provenance");
        this.kind = kind;
        this.sourceId = sourceId;
    }

    String getKind() { return kind; }
    String getSourceId() { return sourceId; }
    boolean isOriginal() { return GENERATED_ORIGINAL.equals(kind); }
}
