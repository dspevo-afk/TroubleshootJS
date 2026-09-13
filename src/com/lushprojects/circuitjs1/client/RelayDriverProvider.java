package com.lushprojects.circuitjs1.client;

/** Driver-owned electrical construction and physical pin map for a relay channel. */
interface RelayDriverProvider {
    String getId();
    CircuitElm create(int x, int y);
    PhysicalSpecification getSpecification();
    PhysicalPackage getPackage();
    String[] getTerminals();
    int[] getPosts();

    final class Bjt implements RelayDriverProvider {
        public String getId() { return "BJT"; }
        public CircuitElm create(int x, int y) {
            NTransistorElm q = new NTransistorElm(x, y);
            q.drag(x + 80, y); q.beta = 100; return q;
        }
        public PhysicalSpecification getSpecification() { return new NpnSpecification("Q1", 100); }
        public PhysicalPackage getPackage() { return PhysicalPackages.TO92_NPN; }
        public String[] getTerminals() { return new String[] { "B", "C", "E" }; }
        public int[] getPosts() { return new int[] { 0, 1, 2 }; }
    }

    final class Nmos implements RelayDriverProvider {
        public String getId() { return "NMOS"; }
        public CircuitElm create(int x, int y) {
            NMosfetElm q = new NMosfetElm(x, y);
            q.drag(x + 80, y); q.vt = 1.5; q.beta = 5; return q;
        }
        public PhysicalSpecification getSpecification() { return new NmosSpecification("Q1", 1.5, 5); }
        public PhysicalPackage getPackage() { return PhysicalPackages.TO92_NMOS; }
        public String[] getTerminals() { return new String[] { "G", "D", "S" }; }
        public int[] getPosts() { return new int[] { 0, 2, 1 }; }
    }
}
