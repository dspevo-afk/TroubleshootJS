package com.lushprojects.circuitjs1.client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.TreeMap;
import java.util.Vector;

/** Frozen A01 inventories with explicit physical-only packages; never solver-backed content claims. */
final class P03StructuralFixtures {
    static final class Fixture {
        final TroubleshootBoard board;
        final TreeMap<String,String> regions = new TreeMap<String,String>();
        final TreeMap<String,String> domains = new TreeMap<String,String>();
        final PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        Fixture(String id) { board = new TroubleshootBoard(id); }
    }
    static Fixture read(String id) throws Exception {
        Fixture result = new Fixture(id);
        BufferedReader file = new BufferedReader(new FileReader("tests/benchmarks/p03-components.tsv"));
        TreeMap<String,PhysicalPackage> packages = new TreeMap<String,PhysicalPackage>();
        try {
            String line;
            while ((line = file.readLine()) != null) {
                String[] fields = line.split("\\t"); if (!id.equals(fields[0])) continue;
                String shape = fields[4]; PhysicalPackage physical = packages.get(shape);
                if (physical == null) {
                    physical = shape(shape, Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), Integer.parseInt(fields[7]));
                    packages.put(shape, physical);
                    result.registry.register(physical, new PcbFootprintProvider() {
                        public PcbFootprint create(BoardComponent component, int x, int y, java.util.Random random, Rectangle outline) {
                            return PcbFootprint.fromPhysicalPackage(component, x, y);
                        }
                    });
                }
                String componentId=fields[1], domain=fields[3];
                result.board.addComponent(new BoardComponent(componentId, "STRUCTURAL", physical, "U"+(result.regions.size()+1)));
                result.regions.put(componentId, fields[2]); result.domains.put(componentId, domain);
                for (int index=0; index<physical.getTerminalCount(); index++) {
                    String net=domain+"/"+(index%2), terminal=physical.getTerminalIds().get(index);
                    if (result.board.getNet(net)==null) result.board.addNet(new BoardNet(net));
                    result.board.addPad(new BoardPad(componentId+"."+terminal,componentId,terminal,net));
                }
            }
        } finally { file.close(); }
        result.board.validate(); return result;
    }
    static PhysicalPackage shape(String name, int width, int height, int count) {
        Vector<String> ids=new Vector<String>(); Vector<PhysicalPackageGeometry.Terminal> terminals=new Vector<PhysicalPackageGeometry.Terminal>();
        boolean connector=name.equals("connector");
        for (int index=0; index<count; index++) {
            String id=""+(index+1); ids.add(id);
            boolean left=connector || index%2==0;
            int py=30+(connector?index:index/2)*30, px=left?20:width-20;
            Point pad=new Point(px,py), body=new Point(left?45:width-45,py);
            PhysicalPackageGeometry.Lead lead=new PhysicalPackageGeometry.Lead(pad,body,
                new Rectangle(Math.min(px,body.x)-3,py-3,Math.abs(px-body.x)+6,6),body,new Rectangle(body.x-4,py-4,8,8));
            PhysicalPackageGeometry.Lead lifted=new PhysicalPackageGeometry.Lead(body,body,
                new Rectangle(body.x-3,py-3,6,6),body,new Rectangle(body.x-4,py-4,8,8));
            terminals.add(new PhysicalPackageGeometry.Terminal(id,pad,new Rectangle(px-10,py-10,20,20),pad,
                new Rectangle(px-12,py-12,24,24),lead,lifted,left?-1:1,0,40));
        }
        PhysicalPackageGeometry geometry=new PhysicalPackageGeometry(width,height,terminals,
            new Rectangle(35,15,width-70,height-30),new Rectangle(30,10,width-60,height-20),
            new Rectangle(0,0,width,height),new Rectangle(-20,-20,width+40,height+40),new Rectangle(-25,-25,width+50,height+50));
        return new PhysicalPackage("P03_"+name,ids,new Vector<String>(),connector,geometry);
    }
}
