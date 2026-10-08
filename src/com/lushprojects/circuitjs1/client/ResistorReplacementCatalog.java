package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class ResistorReplacementCatalog implements PhysicalPartCatalog<ResistorCatalogEntry> {
    private static final int[] E12_MANTISSAS = { 10, 12, 15, 18, 22, 27, 33, 39, 47, 56, 68, 82 };
    private final HashMap<String, ResistorCatalogEntry> entries =
        new HashMap<String, ResistorCatalogEntry>();
    private final Vector<String> order = new Vector<String>();

    ResistorReplacementCatalog() { this(ResistorNameplate.DEFAULT_RATED_WATTAGE); }

    /** A power-resistor position advertises its actual replacement rating. */
    static ResistorReplacementCatalog forSpecification(ResistorNameplate specification) {
        if (specification == null) throw new IllegalArgumentException("Missing resistor specification");
        return new ResistorReplacementCatalog(Math.max(ResistorNameplate.DEFAULT_RATED_WATTAGE,
            specification.getRatedWattage()));
    }

    private ResistorReplacementCatalog(double ratedWattage) {
        if (Double.isNaN(ratedWattage) || Double.isInfinite(ratedWattage) || ratedWattage <= 0)
            throw new IllegalArgumentException("Invalid catalog power rating");
        for (int decade = 0; decade <= 6; decade++) {
            for (int mantissa : E12_MANTISSAS) {
                double value = mantissa * Math.pow(10, decade);
                if (value > 10000000)
                    continue;
                add(value, ratedWattage);
            }
        }
    }

    private void add(double resistanceOhms, double ratedWattage) {
        boolean standard = ratedWattage == ResistorNameplate.DEFAULT_RATED_WATTAGE;
        if (standard && resistanceOhms == 330) ratedWattage = .22;
        String id = "R_CATALOG_" + (long) resistanceOhms +
            (standard ? "" : "_W" + Double.toString(ratedWattage).replace('.', '_'));
        if (entries.containsKey(id))
            throw new IllegalArgumentException("Duplicate catalog value: " + resistanceOhms);
        entries.put(id, new ResistorCatalogEntry(id, resistanceOhms, ratedWattage));
        order.add(id);
    }

    public ResistorCatalogEntry get(String id) {
        ResistorCatalogEntry entry = entries.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown resistor catalog entry: " + id);
        return entry;
    }

    public Vector<ResistorCatalogEntry> getEntries() {
        Vector<ResistorCatalogEntry> result = new Vector<ResistorCatalogEntry>();
        for (String id : order)
            result.add(entries.get(id));
        return result;
    }

    int size() { return order.size(); }
}
