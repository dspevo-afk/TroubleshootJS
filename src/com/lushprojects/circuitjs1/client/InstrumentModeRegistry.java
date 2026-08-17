package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Runtime registry/discovery seam for instrument providers. */
final class InstrumentModeRegistry {
    private final HashMap<String, InstrumentModeStrategy> strategies =
        new HashMap<String, InstrumentModeStrategy>();
    private final Vector<InstrumentModeStrategy> orderedStrategies =
        new Vector<InstrumentModeStrategy>();

    InstrumentModeRegistry(InstrumentModeProvider... bootstrapProviders) {
        if (bootstrapProviders == null)
            throw new IllegalArgumentException("Missing instrument mode bootstrap providers");
        for (int i = 0; i < bootstrapProviders.length; i++)
            registerProduction(bootstrapProviders[i]);
    }

    InstrumentModeStrategy get(String id) {
        InstrumentModeStrategy strategy = strategies.get(id);
        if (strategy == null)
            throw new IllegalStateException("No instrument mode strategy: " + id);
        return strategy;
    }

    InstrumentModeStrategy get(InstrumentMode mode) {
        if (mode == null)
            throw new IllegalArgumentException("Missing instrument mode");
        return get(mode.getId());
    }

    Vector<InstrumentModeStrategy> getAll() {
        Vector<InstrumentModeStrategy> copy = new Vector<InstrumentModeStrategy>();
        for (int i = 0; i < orderedStrategies.size(); i++)
            copy.add(orderedStrategies.elementAt(i));
        return copy;
    }

    Vector<InstrumentModeStrategy> getPlayerVisibleModes() {
        Vector<InstrumentModeStrategy> visible = new Vector<InstrumentModeStrategy>();
        for (int i = 0; i < orderedStrategies.size(); i++) {
            InstrumentModeStrategy strategy = orderedStrategies.elementAt(i);
            if (strategy.isPlayerVisible())
                visible.add(strategy);
        }
        return visible;
    }

    void registerProduction(InstrumentModeProvider provider) {
        validate(provider);
        if (provider.getId().startsWith("DEVELOPER_"))
            throw new IllegalArgumentException("Developer instrument mode is not production-safe");
        registerValidated(provider);
    }

    void register(InstrumentModeStrategy strategy) {
        validate(strategy);
        if (!(strategy instanceof InstrumentModeProvider))
            throw new IllegalArgumentException("Instrument mode is not a production provider");
        registerProduction((InstrumentModeProvider) strategy);
    }

    void registerDeveloperOnly(InstrumentModeStrategy strategy) {
        validate(strategy);
        if (strategy.isPlayerVisible() || !strategy.getId().startsWith("DEVELOPER_"))
            throw new IllegalArgumentException("Invalid developer instrument mode strategy");
        registerValidated(strategy);
    }

    private void registerValidated(InstrumentModeStrategy strategy) {
        if (strategies.containsKey(strategy.getId()))
            throw new IllegalArgumentException("Duplicate instrument mode strategy: " +
                strategy.getId());
        strategies.put(strategy.getId(), strategy);
        orderedStrategies.add(strategy);
    }

    private static void validate(InstrumentModeStrategy strategy) {
        if (strategy == null || strategy.getMode() == null || strategy.getId() == null ||
                strategy.getId().length() == 0 || strategy.getLabel() == null ||
                strategy.getInitialDisplay() == null || strategy.getProbeRequirements() == null ||
                strategy.getPowerPolicy() == null || strategy.getState() == null)
            throw new IllegalArgumentException("Invalid instrument mode strategy");
        if (!strategy.getId().equals(strategy.getMode().getId()))
            throw new IllegalArgumentException("Instrument mode identity does not match provider ID");
    }
}
