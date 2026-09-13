package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Read-only shopping projection over the current runtime's real catalogs and inventory. */
final class PlayerShopCatalog {
    static final class Entry {
        final String id;
        final String catalogId;
        final String label;
        final String acquisitionComponent;
        final PhysicalGeometryRealization geometry;

        Entry(String publicId, WorkbenchCatalogEntry entry, String component,
                PhysicalGeometryRealization geometry) {
            id = publicId;
            catalogId = entry.getId();
            label = entry.getDisplayName();
            acquisitionComponent = component;
            this.geometry = geometry;
        }
    }

    static final class Category {
        final String id;
        final String title;
        private final Vector<Entry> entries = new Vector<Entry>();
        private final HashSet<String> looseIds = new HashSet<String>();

        Category(String type) {
            id = type;
            title = titleFor(type);
        }

        Vector<Entry> entries() { return new Vector<Entry>(entries); }
        int looseCount() { return looseIds.size(); }

        String label(Entry selected) {
            for (Entry entry : entries)
                if (entry != selected && entry.catalogId.equals(selected.catalogId))
                    return selected.label + " - " + fitLabel(selected.geometry);
            return selected.label;
        }

        Entry entry(String entryId) {
            for (Entry entry : entries) if (entry.id.equals(entryId)) return entry;
            throw new IllegalArgumentException("Unknown part specification.");
        }

        void add(WorkbenchPartsProvider provider) {
            if (!(provider instanceof PhysicalBoardInstallationProvider.Scoped))
                throw new IllegalStateException("Catalog lacks a physical acquisition declaration.");
            PhysicalGeometryRealization geometry = ((PhysicalBoardInstallationProvider.Scoped)provider)
                .getMutationSlot().getPhysicalSlot().getGeometryRealization();
            if (geometry == null) throw new IllegalStateException("Catalog has no physical package geometry.");
            for (WorkbenchCatalogEntry row : provider.getCatalogEntries()) {
                Entry existing = null;
                for (Entry entry : entries)
                    if (entry.catalogId.equals(row.getId()) && entry.geometry.isEquivalentTo(geometry)) existing = entry;
                if (existing == null) entries.add(new Entry("spec-" + (entries.size() + 1),
                    row, provider.getComponentId(), geometry));
                else if (!existing.label.equals(row.getDisplayName()))
                    throw new IllegalArgumentException("Conflicting part catalog specification.");
            }
            for (PhysicalPart<?> part : provider.getLooseParts()) {
                if (part.isInstalled() || !provider.ownsPart(part.getId()))
                    throw new IllegalStateException("Invalid loose inventory projection.");
                looseIds.add(part.getId());
            }
        }
    }

    private final Vector<Category> categories = new Vector<Category>();

    PlayerShopCatalog(GeneratedBoardInstance owner) {
        if (owner == null) throw new IllegalArgumentException("Missing shopping board.");
        for (WorkbenchPartsProvider provider : owner.getPhysicalBoardRuntime().getWorkbenchPartsProviders()) {
            String type = owner.getBoard().getComponent(provider.getComponentId()).getType();
            Category category = null;
            for (Category existing : categories) if (existing.id.equals(type)) category = existing;
            if (category == null) {
                category = new Category(type);
                categories.add(category);
            }
            category.add(provider);
        }
    }

    Vector<Category> categories() { return new Vector<Category>(categories); }

    Category category(String id) {
        for (Category category : categories) if (category.id.equals(id)) return category;
        throw new IllegalArgumentException("Unknown part type.");
    }

    private static String titleFor(String type) {
        if ("RESISTOR".equals(type)) return "Resistors";
        if ("CAPACITOR".equals(type)) return "Capacitors";
        if ("DIODE".equals(type)) return "Diodes";
        if ("LED".equals(type)) return "LEDs";
        if ("NPN_TRANSISTOR".equals(type)) return "NPN transistors";
        if ("NMOS_TRANSISTOR".equals(type)) return "N-channel MOSFETs";
        if ("RELAY".equals(type)) return "Relays";
        return type + " parts";
    }

    private static String fitLabel(PhysicalGeometryRealization geometry) {
        PhysicalPackage physicalPackage = geometry.getPhysicalPackage();
        if (physicalPackage.isEquivalentTo(PhysicalPackages.AXIAL_RESISTOR)) {
            if ("SPAN_220".equals(geometry.getVariantKey())) return "Narrow lead spacing";
            if ("SPAN_240".equals(geometry.getVariantKey())) return "Medium lead spacing";
            if ("SPAN_260".equals(geometry.getVariantKey())) return "Wide lead spacing";
        }
        if (physicalPackage.isEquivalentTo(PhysicalPackages.AXIAL_DIODE)) {
            if ("SPAN_230".equals(geometry.getVariantKey())) return "Narrow lead spacing";
            if ("SPAN_250".equals(geometry.getVariantKey())) return "Wide lead spacing";
        }
        throw new IllegalStateException("Catalog package options lack distinct public fit labels.");
    }
}
