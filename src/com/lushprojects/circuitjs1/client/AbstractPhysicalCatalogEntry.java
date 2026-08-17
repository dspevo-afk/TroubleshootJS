package com.lushprojects.circuitjs1.client;

/** Common immutable identity/metadata boundary for typed catalog entries. */
abstract class AbstractPhysicalCatalogEntry<S extends PhysicalSpecification>
        implements PhysicalCatalogEntry<S> {
    private final String id;
    private final S specification;
    private final PhysicalNameplate playerVisibleNameplate;
    private final PhysicalPartOrientation orientation;

    AbstractPhysicalCatalogEntry(String id, S specification,
            PhysicalNameplate playerVisibleNameplate, PhysicalPartOrientation orientation) {
        if (id == null || id.length() == 0 || specification == null ||
                playerVisibleNameplate == null || orientation == null)
            throw new IllegalArgumentException("Invalid physical catalog entry");
        this.id = id;
        this.specification = specification;
        this.playerVisibleNameplate = playerVisibleNameplate;
        this.orientation = orientation;
    }

    public final String getId() { return id; }
    public final S getSpecification() { return specification; }
    public final PhysicalNameplate getPlayerVisibleNameplate() {
        return playerVisibleNameplate;
    }
    public final PhysicalPartOrientation getOrientation() { return orientation; }
}
