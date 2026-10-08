package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fixed ordinary CircuitJS junction recipe used by the accepted E06 feedback network. */
final class ZenerSpecification implements PhysicalSpecification {
    static final ZenerSpecification STANDARD = new ZenerSpecification();
    static final double BREAKDOWN_VOLTS = 10.7;
    static final double FORWARD_DROP_VOLTS = .805904783;
    private ZenerSpecification() { }
    public String getSpecificationId() { return "AXIAL_ZENER_10V7_E06_JUNCTION_V1"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String catalogLabel() { return "10.7 V zener diode"; }

    Vector<CircuitElm> createBacking(int x, int y) {
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        boolean complete = false;
        try {
            ZenerElm zener = new ZenerElm(x, y); elements.add(zener);
            zener.x2 = x + 16; zener.y2 = y;
            zener.model = DiodeModel.getModelWithParameters(FORWARD_DROP_VOLTS, BREAKDOWN_VOLTS);
            zener.modelName = zener.model.name; zener.setup(); zener.setPoints();
            complete = true; return elements;
        } finally { if (!complete) for (CircuitElm element : elements) element.delete(); }
    }

    static void requireBacking(PhysicalSpecification spec, PhysicalPackage pkg, Vector<CircuitElm> elements) {
        if (spec != STANDARD || pkg != PhysicalPackages.AXIAL_DIODE || elements == null ||
                elements.size() != 1 || elements.get(0) == null || elements.get(0).getClass() != ZenerElm.class)
            throw new IllegalArgumentException("Zener requires its declared ordinary junction backing");
        ZenerElm zener = (ZenerElm)elements.get(0);
        double leakage = 1 / (Math.exp(FORWARD_DROP_VOLTS / (2 * DiodeModel.vt)) - 1);
        if (zener.model == null || zener.modelName == null || !zener.modelName.equals(zener.model.name) ||
                zener.model.breakdownVoltage != BREAKDOWN_VOLTS || zener.model.seriesResistance != 0 ||
                zener.model.emissionCoefficient != 2 ||
                Math.abs(zener.model.saturationCurrent / leakage - 1) > 1e-8 ||
                zener.getPost(0).equals(zener.getPost(1)))
            throw new IllegalArgumentException("Zener E06 junction recipe changed");
        requireJunctionSetup(zener);
    }

    /** Audit the fixed setup coefficients actually read by the embedded ordinary solver junction. */
    static void requireJunctionSetup(DiodeElm element) {
        DiodeModel model = element.model; Diode junction = element.diode;
        if (model == null || junction == null || element.hasResistance != (model.seriesResistance > 0) ||
                element.diodeEndNode != (element.hasResistance ? 2 : 1))
            throw new IllegalArgumentException("Junction series-node setup differs from its actual model");
        double scale = model.emissionCoefficient * Diode.vt;
        double critical = scale * Math.log(scale / (Math.sqrt(2) * model.saturationCurrent));
        double zcritical = Diode.vt * Math.log(Diode.vt / (Math.sqrt(2) * model.saturationCurrent));
        double offset = model.breakdownVoltage == 0 ? 0 : model.breakdownVoltage -
            Math.log(-(1 - .005 / model.saturationCurrent)) / Diode.vzcoef;
        if (model.vscale != scale || model.vdcoef != 1 / scale ||
                junction.leakage != model.saturationCurrent || junction.zvoltage != model.breakdownVoltage ||
                junction.vscale != scale || junction.vdcoef != 1 / scale ||
                junction.zoffset != offset || junction.vcrit != critical || junction.vzcrit != zcritical)
            throw new IllegalArgumentException("Embedded junction setup differs from its declared actual model");
    }
}
