package com.lushprojects.circuitjs1.client;

interface GeneratedBoardFamilyState {
    /** Validate captured mutable controls before live installation. */
    void requireOwnedBy(GeneratedBoardInstance instance);

    boolean isFaultedTargetInstalled(GeneratedBoardInstance instance, String componentId);

    GeneratedBoardOperationCatalog getOperationCatalog();

    GeneratedCustomerRetestProfile getCustomerRetestProfile();
}
