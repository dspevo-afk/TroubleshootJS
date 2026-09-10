package com.lushprojects.circuitjs1.client;

/**
 * A small local construction provider.  Providers receive a declaration and a
 * scope limited to that declaration; they never receive a board, runtime, or
 * global CircuitJS element collection.
 */
interface ElectricalConstructionProvider {
    String getProviderId();
    int getVersion();

    /** Local declarations are resolved once before any mutable allocation. */
    void declare(ElectricalRealizationSpec.ContributionBuilder builder,
            ComposedBlockContribution contribution);

    ContributionConstructionReceipt construct(
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ElectricalConstructionContext.Scope scope);
}
