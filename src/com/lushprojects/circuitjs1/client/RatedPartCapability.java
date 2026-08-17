package com.lushprojects.circuitjs1.client;

final class RatedPartCapability implements PhysicalPartCapability {
    private final PhysicalRating rating;
    private final WorkbenchCapabilityMetadata metadata =
        new WorkbenchCapabilityMetadata("RATED", "Rated", "READ_RATING");

    RatedPartCapability(PhysicalRating rating) {
        if (rating == null)
            throw new IllegalArgumentException("Missing physical rating");
        this.rating = rating;
    }

    public WorkbenchCapabilityMetadata getMetadata() { return metadata; }
    public String getOperationLabel(WorkbenchOperation operation) { return "Inspect rating"; }
    public boolean supports(WorkbenchOperation operation) {
        return operation != null && operation.getPart() != null &&
            "READ_RATING".equals(operation.getId());
    }
    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation);
    }
    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return isAvailable(operation, context);
    }
    PhysicalRating getRating() { return rating; }
}
