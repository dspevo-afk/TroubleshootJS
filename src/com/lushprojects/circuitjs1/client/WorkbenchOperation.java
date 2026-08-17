package com.lushprojects.circuitjs1.client;

/** Type-neutral workbench action; physical identity remains in the part. */
final class WorkbenchOperation {
    static final String INSTALL = "INSTALL";
    static final String REMOVE = "REMOVE";
    static final String LIFT_LEAD = "LIFT_LEAD";
    static final String RECONNECT_LEAD = "RECONNECT_LEAD";
    static final String RESTORE = "RESTORE";
    static final String CATALOG_INSTALL = "CATALOG_INSTALL";
    static final String INSPECT_LOOSE = "INSPECT_LOOSE";

    private final String id;
    private final PhysicalPart part;
    private final String componentId;
    private final String padId;
    private final String catalogEntryId;

    private WorkbenchOperation(String id, PhysicalPart part, String componentId,
            String padId, String catalogEntryId) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Missing workbench operation ID");
        this.id = id;
        this.part = part;
        this.componentId = componentId;
        this.padId = padId;
        this.catalogEntryId = catalogEntryId;
    }

    static WorkbenchOperation forPart(String id, PhysicalPart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing physical part");
        return new WorkbenchOperation(id, part,
            getBoardComponentId(part), null, null);
    }

    static WorkbenchOperation forPartAtSlot(String id, PhysicalPart part, String componentId) {
        if (part == null || componentId == null || componentId.length() == 0)
            throw new IllegalArgumentException("Missing physical part or target slot");
        return new WorkbenchOperation(id, part, componentId, null, null);
    }

    static WorkbenchOperation forPartLead(String id, PhysicalPart part, String componentId,
            String padId) {
        if (part == null || componentId == null || padId == null)
            throw new IllegalArgumentException("Incomplete physical lead operation");
        return new WorkbenchOperation(id, part, componentId, padId, null);
    }

    static String getBoardComponentId(PhysicalPart part) {
        if (part == null || part.getBoardSlot() == null)
            return null;
        return part.getBoardSlot().getComponentId();
    }

    static WorkbenchOperation forComponent(String id, String componentId) {
        return new WorkbenchOperation(id, null, componentId, null, null);
    }

    static WorkbenchOperation forComponentLead(String id, String componentId, String padId) {
        if (componentId == null || padId == null)
            throw new IllegalArgumentException("Incomplete board lead operation");
        return new WorkbenchOperation(id, null, componentId, padId, null);
    }

    static WorkbenchOperation forCatalog(String componentId, String catalogEntryId) {
        if (catalogEntryId == null || catalogEntryId.length() == 0)
            throw new IllegalArgumentException("Missing catalog entry ID");
        return new WorkbenchOperation(CATALOG_INSTALL, null, componentId, null,
            catalogEntryId);
    }

    String getId() { return id; }
    PhysicalPart getPart() { return part; }
    String getComponentId() { return componentId; }
    String getPadId() { return padId; }
    String getCatalogEntryId() { return catalogEntryId; }
}
