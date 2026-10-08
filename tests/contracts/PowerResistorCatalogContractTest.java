package com.lushprojects.circuitjs1.client;

/** Power replacement ratings must remain visible and distinct from the existing catalog. */
public final class PowerResistorCatalogContractTest {
    private static int assertions;
    public static void main(String[] args) {
        ResistorReplacementCatalog standard = new ResistorReplacementCatalog();
        ResistorReplacementCatalog low = ResistorReplacementCatalog.forSpecification(
            new ResistorNameplate("RLOW", 4700, 5, .22));
        ResistorReplacementCatalog power = ResistorReplacementCatalog.forSpecification(
            new ResistorNameplate("RBULK", 4700, 5, 10));
        check(standard.size() == 73 && power.size() == 73, "bounded E12 population retained");
        check(standard.get("R_CATALOG_330").getNameplate().getRatedWattage() == .22,
            "existing 330-ohm rating is unchanged");
        check(standard.get("R_CATALOG_4700").getNameplate().getRatedWattage() == .25,
            "existing standard rating is unchanged");
        check(standard.get("R_CATALOG_10000000").getNameplate().getNominalResistanceOhms() == 10000000,
            "existing range endpoint remains available");
        check(low.get("R_CATALOG_4700").getCatalogDisplayValue().equals(
            standard.get("R_CATALOG_4700").getCatalogDisplayValue()),
            "ordinary low-power positions retain their catalog labels and IDs");
        ResistorCatalogEntry bleed = power.get("R_CATALOG_4700_W10_0");
        check(bleed.getNameplate().getNominalResistanceOhms() == 4700 &&
            bleed.getNameplate().getRatedWattage() == 10, "replacement has the actual bleed value and rating");
        check(bleed.getCatalogDisplayValue().contains("10 W"), "shop marks the replacement power rating");
        for (ResistorCatalogEntry entry : power.getEntries()) {
            check(entry.getNameplate().getRatedWattage() == 10, "power catalog cannot silently sell a quarter-watt part");
            check(!entry.getId().equals(standard.get("R_CATALOG_" +
                (long)entry.getNameplate().getNominalResistanceOhms()).getId()),
                "different power specifications cannot merge by catalog ID");
        }
        reject(power, "R_CATALOG_4700");
        reject(standard, "R_CATALOG_4700_W10_0");
        check(standard.get("R_CATALOG_4700").getNameplate().getRatedWattage() == .25,
            "constructing a power catalog cannot change another position");
        System.out.println("PASS: power resistor catalog contracts assertions=" + assertions);
    }
    private static void reject(ResistorReplacementCatalog catalog, String id) {
        try { catalog.get(id); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Foreign power-class catalog ID was accepted");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }
}
