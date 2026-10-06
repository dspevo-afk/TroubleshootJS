package com.lushprojects.circuitjs1.client;

interface GeneratedBoardFamilyState {
    /** Validate captured mutable controls before live installation. */
    void requireOwnedBy(GeneratedBoardInstance instance);

    boolean isFaultedTargetInstalled(GeneratedBoardInstance instance, String componentId);

    /** Current input commands, independently read from the family owner. */
    String getSessionInputSignature();

    GeneratedBoardOperationCatalog getOperationCatalog();

    GeneratedCustomerRetestProfile getCustomerRetestProfile();
}
