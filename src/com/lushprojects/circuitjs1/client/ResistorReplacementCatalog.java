package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class ResistorReplacementCatalog {
    private static final int[] E12_MANTISSAS = { 10, 12, 15, 18, 22, 27, 33, 39, 47, 56, 68, 82 };
    private final HashMap<String, ResistorCatalogEntry> entries =
        new HashMap<String, ResistorCatalogEntry>();
    private final Vector<String> order = new Vector<String>();

    ResistorReplacementCatalog() {
        for (int decade = 0; decade <= 6; decade++) {
            for (int mantissa : E12_MANTISSAS) {
                double value = mantissa * Math.pow(10, decade);
                if (value > 10000000)
                    continue;
                add(value);
            }
        }
    }

    private void add(double resistanceOhms) {
        double ratedWattage = ResistorNameplate.DEFAULT_RATED_WATTAGE;
        if (resistanceOhms == 330)
            ratedWattage = .22;
        String id = "R_CATALOG_" + (long) resistanceOhms;
        if (entries.containsKey(id))
            throw new IllegalArgumentException("Duplicate catalog value: " + resistanceOhms);
        entries.put(id, new ResistorCatalogEntry(id, resistanceOhms, ratedWattage));
        order.add(id);
    }

    ResistorCatalogEntry get(String id) {
        ResistorCatalogEntry entry = entries.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown resistor catalog entry: " + id);
        return entry;
    }

    Vector<ResistorCatalogEntry> getEntries() {
        Vector<ResistorCatalogEntry> result = new Vector<ResistorCatalogEntry>();
        for (String id : order)
            result.add(entries.get(id));
        return result;
    }

    int size() { return order.size(); }
}
