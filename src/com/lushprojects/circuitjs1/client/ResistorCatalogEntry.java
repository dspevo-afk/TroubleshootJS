package com.lushprojects.circuitjs1.client;

class ResistorCatalogEntry extends AbstractPhysicalCatalogEntry<ResistorNameplate> {

    ResistorCatalogEntry(String id, double resistanceOhms) {
        this(id, resistanceOhms, ResistorNameplate.DEFAULT_RATED_WATTAGE);
    }

    ResistorCatalogEntry(String id, double resistanceOhms, double ratedWattage) {
        super(id, new ResistorNameplate(id, resistanceOhms, 5, ratedWattage),
            createPlayerNameplate(id, resistanceOhms, ratedWattage), PhysicalPartOrientation.NON_POLARIZED);
    }

    ResistorNameplate getNameplate() { return getSpecification(); }

    String getCatalogDisplayValue() {
        return getNameplate().getDisplayValue() + powerSuffix(getNameplate().getRatedWattage());
    }

    private static PhysicalNameplate createPlayerNameplate(String id, double resistanceOhms,
            double ratedWattage) {
        return new PhysicalNameplate(id, "Physical resistor", "Value",
            format(resistanceOhms) + " Ohm +/-5%" + powerSuffix(ratedWattage));
    }

    private static String powerSuffix(double watts) {
        return watts > ResistorNameplate.DEFAULT_RATED_WATTAGE ? " / " + format(watts) + " W" : "";
    }

    private static String format(double value) {
        if (value == Math.rint(value))
            return String.valueOf((long) value);
        return String.valueOf(value);
    }
}
