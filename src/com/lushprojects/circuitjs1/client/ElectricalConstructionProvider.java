package com.lushprojects.circuitjs1.client;

/**
 * A small local construction provider.  Providers receive a declaration and a
 * scope limited to that declaration; they never receive a board, runtime, or
 * global CircuitJS element collection.
 */
interface ElectricalConstructionProvider {
    String getProviderId();
    int getVersion();

    ContributionConstructionReceipt construct(
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ElectricalConstructionContext.Scope scope);
}
