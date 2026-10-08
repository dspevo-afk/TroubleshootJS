package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** One ordinary CircuitJS optocoupler, including its standard internal diode/CCCS/NPN recipe. */
final class OptocouplerSpecification implements PhysicalSpecification {
    static final OptocouplerSpecification STANDARD = new OptocouplerSpecification();
    private OptocouplerSpecification() { }
    public String getSpecificationId() { return "OPTOCOUPLER_CIRCUITJS_DEFAULT_E06_V1"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String catalogLabel() { return "4-pin optocoupler"; }

    Vector<CircuitElm> createBacking(int x, int y) {
        String previousDiode = DiodeElm.lastModelName, previousTransistor = TransistorElm.lastModelName;
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        boolean complete = false;
        try {
            // Composite node allocation must see the zero-series-resistance default junction initially.
            DiodeElm.lastModelName = "default"; TransistorElm.lastModelName = "default";
            OptocouplerElm opto = new OptocouplerElm(x, y); elements.add(opto); opto.setPoints();
            complete = true; return elements;
        } finally {
            DiodeElm.lastModelName = previousDiode; TransistorElm.lastModelName = previousTransistor;
            if (!complete) for (CircuitElm element : elements) element.delete();
        }
    }

    static void requireBacking(PhysicalSpecification spec, PhysicalPackage pkg, Vector<CircuitElm> elements) {
        if (spec != STANDARD || pkg != PhysicalPackages.OPTOCOUPLER_4 || elements == null ||
                elements.size() != 1 || elements.get(0) == null || elements.get(0).getClass() != OptocouplerElm.class)
            throw new IllegalArgumentException("Optocoupler requires its declared composite backing");
        OptocouplerElm opto = (OptocouplerElm)elements.get(0);
        if (opto.getPostCount() != 4 || opto.compElmList.size() != 3 || opto.diode == null || opto.transistor == null ||
                opto.compElmList.get(0) != opto.diode || !(opto.compElmList.get(1) instanceof CCCSElm) ||
                opto.compElmList.get(2) != opto.transistor ||
                !"default".equals(opto.diode.modelName) || !"default".equals(opto.transistor.modelName) ||
                opto.diode.hasResistance || opto.transistor.pnp != 1 || opto.transistor.beta != 700)
            throw new IllegalArgumentException("Optocoupler standard internal recipe changed");
        if (opto.diode.model != DiodeModel.getDefaultModel() ||
                opto.transistor.model != TransistorModel.getDefaultModel() ||
                opto.transistor.vcrit != TransistorElm.vt * Math.log(TransistorElm.vt /
                    (Math.sqrt(2) * opto.transistor.model.satCur)))
            throw new IllegalArgumentException("Optocoupler actual internal model setup changed");
        ZenerSpecification.requireJunctionSetup(opto.diode);
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++)
            if (opto.getPost(i).equals(opto.getPost(j)))
                throw new IllegalArgumentException("Optocoupler external posts overlap");
    }
}
