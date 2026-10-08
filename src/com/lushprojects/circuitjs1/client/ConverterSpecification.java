package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Static declaration of the selected E06 opaque module, without a safety certification. */
final class ConverterSpecification implements PhysicalSpecification {
    private static final String[] TERMINALS = {"IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"};
    private final String modelDeclaration = new E06ConverterContract().canonical();

    public String getSpecificationId() { return "ISOLATED_CONVERTER_E06_V3"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String label() { return "Isolated converter / 10 V bias / EN / FB"; }

    void requireModel(IsolatedConverterModuleModel module) {
        if (module == null || module.converter == null || module.bias == null ||
                !modelDeclaration.equals(module.converter.getContract().canonical()))
            throw new IllegalArgumentException("Converter backing does not match its static declaration");
        Vector<CircuitElm> elements = module.elements();
        if (elements.size() != 4 || elements.get(0) != module.converter ||
                elements.get(1) != module.bias || !(elements.get(2) instanceof WireElm) ||
                !(elements.get(3) instanceof WireElm))
            throw new IllegalArgumentException("Converter requires its bias and two internal input wires");
        for (int i = 0; i < TERMINALS.length; i++)
            if (!TERMINALS[i].equals(module.terminalId(i)))
                throw new IllegalArgumentException("Converter terminal declaration changed");
        for (int i = 0; i < 2; i++) {
            CircuitElm wire = elements.get(i + 2);
            if (!wire.getPost(0).equals(module.converter.getPost(i)) ||
                    !wire.getPost(1).equals(module.bias.getPost(i)))
                throw new IllegalArgumentException("Converter internal input wiring changed");
        }
    }

    static String terminalId(int index) {
        if (index < 0 || index >= TERMINALS.length)
            throw new IllegalArgumentException("Invalid converter terminal");
        return TERMINALS[index];
    }
    static Vector<String> terminalIds() {
        Vector<String> result = new Vector<String>();
        for (String id : TERMINALS) result.add(id);
        return result;
    }
    static int terminalIndex(String id) {
        for (int i = 0; i < TERMINALS.length; i++) if (TERMINALS[i].equals(id)) return i;
        throw new IllegalArgumentException("Unknown converter terminal: " + id);
    }
}
